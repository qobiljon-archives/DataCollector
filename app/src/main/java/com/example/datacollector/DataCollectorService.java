package com.example.datacollector;

import android.Manifest;
import android.app.AlarmManager;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.location.Location;
import android.os.BatteryManager;
import android.os.Binder;
import android.os.IBinder;
import android.os.Looper;
import android.os.SystemClock;
import android.util.Log;
import android.util.Pair;

import androidx.core.app.ActivityCompat;
import androidx.core.app.NotificationCompat;

import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationCallback;
import com.google.android.gms.location.LocationRequest;
import com.google.android.gms.location.LocationResult;
import com.google.android.gms.location.LocationServices;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.Locale;
import java.util.concurrent.ExecutionException;

import static com.example.datacollector.MainActivity.PACKAGE_NAME;
import static com.example.datacollector.MainActivity.TAG;

public class DataCollectorService extends Service {

    @Override
    public IBinder onBind(Intent intent) {
        return mBinder;
    }

    @Override
    public void onCreate() {
        super.onCreate();

        this.foregroundNotificationId = (int) (System.currentTimeMillis() % 10000);

        this.locationDataFile = new File(getFilesDir(), getString(R.string.location_file_name));
        this.locationDataSubmitUrl = getString(R.string.post_format, getString(R.string.api_submit_location_data));

        this.btScanDataFile = new File(getFilesDir(), getString(R.string.bt_scan_file_name));
        this.btScanDataSubmitUrl = getString(R.string.post_format, getString(R.string.api_submit_bt_scan_data));

        this.batteryLevelDataFile = new File(getFilesDir(), getString(R.string.battery_level_file_name));
        this.batteryLevelDataSubmitUrl = getString(R.string.post_format, getString(R.string.api_battery_level_data));

        this.fusedLocationClient = LocationServices.getFusedLocationProviderClient(getApplicationContext());
        this.locationCallback = new LocationCallback() {
            @Override
            public void onLocationResult(LocationResult locationResult) {
                if (locationResult == null)
                    return;
                try {
                    for (Location location : locationResult.getLocations()) {
                        storeLocationData(
                                location.getTime(),
                                location.getLatitude(),
                                location.getLongitude(),
                                location.getAltitude()
                        );
                    }
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        };

        // listen for bluetooth broadcasts
        this.bluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
        BroadcastReceiver bluetoothBroadcastReceiver = new BroadcastReceiver() {
            public void onReceive(Context context, Intent intent) {
                String action = intent.getAction();
                if (BluetoothDevice.ACTION_FOUND.equals(action)) {
                    BluetoothDevice device = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);
                    try {
                        storeBtScanData(
                                System.currentTimeMillis(),
                                device.getAddress(),
                                device.getBondState() == BluetoothDevice.BOND_BONDED
                        );
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                }
            }
        };
        IntentFilter filter = new IntentFilter(BluetoothDevice.ACTION_FOUND);
        registerReceiver(bluetoothBroadcastReceiver, filter);

        // listen for battery level broadcasts
        BroadcastReceiver batteryLevelBroadcast = new BroadcastReceiver() {
            public void onReceive(Context context, Intent intent) {
                String action = intent.getAction();
                if (Intent.ACTION_BATTERY_CHANGED.equals(action)) {
                    try {
                        int status = intent.getIntExtra(BatteryManager.EXTRA_STATUS, -1);
                        boolean isCharging = status == BatteryManager.BATTERY_STATUS_CHARGING || status == BatteryManager.BATTERY_STATUS_FULL;

                        int level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1);
                        int scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, -1);
                        float batteryPercentage = level * 100 / (float) scale;

                        storeBatterLevelData(
                                System.currentTimeMillis(),
                                batteryPercentage,
                                isCharging
                        );
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                }
            }
        };
        filter = new IntentFilter(Intent.ACTION_BATTERY_CHANGED);
        registerReceiver(batteryLevelBroadcast, filter);

        Log.e(TAG, "DataCollectorService created");
    }

    @Override
    public void onDestroy() {
        super.onDestroy();

        this.fusedLocationClient.removeLocationUpdates(locationCallback);

        Log.e(TAG, "DataCollectorService terminated");

        onTaskRemoved(null);
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        startForeground(foregroundNotificationId, getForegroundNotification());


        // start tracking location
        if (fusedLocationClient != null) {
            if (ActivityCompat.checkSelfPermission(getApplicationContext(), Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED && ActivityCompat.checkSelfPermission(getApplicationContext(), Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
                LocationRequest locationRequest = new LocationRequest();
                locationRequest.setPriority(LocationRequest.PRIORITY_HIGH_ACCURACY);
                locationRequest.setFastestInterval(GPS_FASTEST_INTERVAL_MS);
                locationRequest.setInterval(GPS_SLOWEST_INTERVAL_MS);
                fusedLocationClient.requestLocationUpdates(
                        locationRequest,
                        new LocationCallback() {
                            private Location previouslyStoredLocation;

                            @Override
                            public void onLocationResult(LocationResult locationResult) {
                                if (locationResult == null)
                                    return;

                                try {
                                    for (Location location : locationResult.getLocations()) {
                                        if (previouslyStoredLocation != null && previouslyStoredLocation.equals(location))
                                            continue;

                                        storeLocationData(
                                                location.getTime(),
                                                location.getLatitude(),
                                                location.getLongitude(),
                                                location.getAltitude()
                                        );
                                        //Log.e("LOCATION UPDATE", String.format(Locale.getDefault(), "(ts, lat, lon, alt)=(%d, %f, %f, %f)", location.getTime(), latitude, longitude, altitude));

                                        if (previouslyStoredLocation == null)
                                            previouslyStoredLocation = location;
                                    }
                                } catch (IOException e) {
                                    e.printStackTrace();
                                }
                            }
                        },
                        Looper.myLooper()
                );

                Log.e(TAG, "DataCollectorService started");
            } else {
                Log.e(TAG, "DataCollectorService failed to start");
            }
        }

        // start bluetooth scans
        Tools.addBackgroundTask(new Runnable() {
            @Override
            public void run() {
                while (!Thread.interrupted()) {
                    bluetoothAdapter.startDiscovery();

                    try {
                        Thread.sleep(BT_SCAN_INTERVAL_MS);
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }
                }
            }
        });

        // start uploading files
        Tools.addBackgroundTask(new Runnable() {
            @Override
            public void run() {
                while (!Thread.interrupted()) {
                    try {
                        if (!Tools.isNetworkUnavailable())
                            uploadCollectedData(getApplicationContext());
                    } catch (IOException | InterruptedException | ExecutionException e) {
                        e.printStackTrace();
                    }

                    try {
                        Thread.sleep(DATA_UPLOAD_INTERVAL_MS);
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }
                }
            }
        });

        return super.onStartCommand(intent, flags, startId);
    }

    @Override
    public void onTaskRemoved(Intent rootIntent) {
        Log.e(TAG, "DataCollectorService task removed, restarting service...");
        Intent intent = new Intent(getApplicationContext(), DataCollectorService.class);
        intent.setPackage(PACKAGE_NAME);
        PendingIntent restartServicePendingIntent = PendingIntent.getService(getApplicationContext(), 1, intent, PendingIntent.FLAG_ONE_SHOT);

        AlarmManager alarmService = (AlarmManager) getApplicationContext().getSystemService(Context.ALARM_SERVICE);
        alarmService.set(AlarmManager.ELAPSED_REALTIME, SystemClock.elapsedRealtime() + 100, restartServicePendingIntent);
    }


    // region Constants
    private final IBinder mBinder = new LocalBinder();

    private static final int GPS_FASTEST_INTERVAL_MS = 10000; // 10000 = 10 seconds
    private static final int GPS_SLOWEST_INTERVAL_MS = 180000; // 180000 = 3 minutes

    private static final int BT_SCAN_INTERVAL_MS = 180000; // 180000 = 3 minutes

    private static final int DATA_UPLOAD_INTERVAL_MS = 900000; // 900000 = 15 minutes
    // endregion

    // region Variables
    private File locationDataFile;
    private File btScanDataFile;
    private File batteryLevelDataFile;
    private String locationDataSubmitUrl;
    private String btScanDataSubmitUrl;
    private String batteryLevelDataSubmitUrl;

    private FusedLocationProviderClient fusedLocationClient;
    private LocationCallback locationCallback;
    private BluetoothAdapter bluetoothAdapter;

    private Notification foregroundNotification;
    private int foregroundNotificationId;
    private String foregroundNotificationChannelName;
    private String foregroundNotificationChannelId;
    private String foregroundNotificationChannelDescription;
    // endregion


    synchronized void storeLocationData(long timestamp, double latitude, double longitude, double altitude) throws IOException {
        // Log.e(TAG, "storeLocationData: called");
        FileWriter writer = new FileWriter(this.locationDataFile, true);
        writer.write(String.format(
                Locale.getDefault(),
                "%d %f %f %f\n",
                timestamp,
                latitude,
                longitude,
                altitude
        ));
        writer.close();
    }

    synchronized void storeBtScanData(long timestamp, String address, boolean bound) throws IOException {
        // Log.e(TAG, "storeBtScanData: called");
        FileWriter writer = new FileWriter(this.btScanDataFile, true);
        writer.write(String.format(
                Locale.getDefault(),
                "%d %s %b\n",
                timestamp,
                address,
                bound
        ));
        writer.close();
    }

    synchronized void storeBatterLevelData(long timestamp, float batteryPercentage, boolean isCharging) throws IOException {
        // Log.e(TAG, "storeBtScanData: called");
        FileWriter writer = new FileWriter(this.batteryLevelDataFile, true);
        writer.write(String.format(
                Locale.getDefault(),
                "%d %f %b\n",
                timestamp,
                batteryPercentage,
                isCharging
        ));
        writer.close();
    }

    synchronized void uploadCollectedData(Context context) throws IOException, InterruptedException, ExecutionException {
        if (this.locationDataFile == null || this.locationDataSubmitUrl == null || this.btScanDataFile == null || this.btScanDataSubmitUrl == null || this.batteryLevelDataFile == null || this.batteryLevelDataSubmitUrl == null)
            return;

        int result = Tools.post(context, R.string.api_submit_location_data, new Pair<>("location_data", Tools.readCharacterFile(this.locationDataFile)));
        if (result == 0 && locationDataFile.delete())
            //noinspection ResultOfMethodCallIgnored
            locationDataFile.createNewFile();

        result = Tools.post(context, R.string.api_submit_bt_scan_data, new Pair<>("bt_scan_data", Tools.readCharacterFile(this.btScanDataFile)));
        if (result == 0 && btScanDataFile.delete())
            //noinspection ResultOfMethodCallIgnored
            btScanDataFile.createNewFile();

        result = Tools.post(context, R.string.api_battery_level_data, new Pair<>("battery_level_data", Tools.readCharacterFile(this.batteryLevelDataFile)));
        if (result == 0 && batteryLevelDataFile.delete())
            //noinspection ResultOfMethodCallIgnored
            batteryLevelDataFile.createNewFile();
    }


    public Notification getForegroundNotification() {
        if (foregroundNotification == null) {
            foregroundNotification = new NotificationCompat.Builder(getApplicationContext(), getForegroundNotificationChannelId())
                    .setSmallIcon(R.mipmap.ic_launcher_round)
                    .setPriority(NotificationCompat.PRIORITY_HIGH)
                    .setSound(null)
                    .build();
        }
        return foregroundNotification;
    }

    public String getForegroundNotificationChannelName() {
        if (foregroundNotificationChannelName == null) {
            foregroundNotificationChannelName = getString(R.string.app_name);
        }
        return foregroundNotificationChannelName;
    }

    public String getForegroundNotificationChannelDescription() {
        if (foregroundNotificationChannelDescription == null) {
            foregroundNotificationChannelDescription = getString(R.string.app_name);
        }
        return foregroundNotificationChannelDescription;
    }

    public String getForegroundNotificationChannelId() {
        if (foregroundNotificationChannelId == null) {
            foregroundNotificationChannelId = "ForegroundServiceSample.NotificationChannel";

            NotificationManager manager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
            // Not exists so we create it at first time
            if (manager.getNotificationChannel(foregroundNotificationChannelId) == null) {
                NotificationChannel nc = new NotificationChannel(
                        getForegroundNotificationChannelId(),
                        getForegroundNotificationChannelName(),
                        NotificationManager.IMPORTANCE_MIN
                );
                // Discrete notification setup
                manager.createNotificationChannel(nc);
                nc.setDescription(getForegroundNotificationChannelDescription());
                nc.setLockscreenVisibility(NotificationCompat.VISIBILITY_PRIVATE);
                nc.setVibrationPattern(null);
                nc.setSound(null, null);
                nc.setShowBadge(false);
            }
        }
        return foregroundNotificationChannelId;
    }


    private class LocalBinder extends Binder {
        //DataCollectorService getService() {
        //    return DataCollectorService.this;
        //}
    }
}
