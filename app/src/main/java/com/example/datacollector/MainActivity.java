package com.example.datacollector;

import android.Manifest;
import android.annotation.SuppressLint;
import android.content.Intent;
import android.os.Bundle;
import android.os.PowerManager;
import android.util.Log;
import android.view.View;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;

import org.json.JSONException;
import org.json.JSONObject;

import java.net.MalformedURLException;
import java.util.Locale;
import java.util.concurrent.ExecutionException;

public class MainActivity extends AppCompatActivity {
    @SuppressLint("WakelockTimeout")
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        this.logTextView = findViewById(R.id.logTextView);
        this.logScrollView = findViewById(R.id.logScrollView);

        MainActivity.TAG = "MyDataCollector";
        MainActivity.PACKAGE_NAME = this.getPackageName();

        Tools.initData(this);
        PowerManager powerManager = (PowerManager) getSystemService(POWER_SERVICE);
        PowerManager.WakeLock wakeLock = powerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, TAG);
        wakeLock.acquire();

        if (Tools.isLocationPermissionDenied(this)) {
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION}, GPS_PERMISSION_REQUEST_CODE);
        } else {
            stopService(new Intent(this, DataCollectorService.class));
            startForegroundService(new Intent(this, DataCollectorService.class));
            log("Data collection service started");
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == GPS_PERMISSION_REQUEST_CODE) {
            if (Tools.isLocationPermissionDenied(this)) {
                Toast.makeText(this, "location permission denied", Toast.LENGTH_SHORT).show();
                finish();
            } else {
                stopService(new Intent(this, DataCollectorService.class));
                startForegroundService(new Intent(this, DataCollectorService.class));
            }
        }
    }


    // region Constants
    final int GPS_PERMISSION_REQUEST_CODE = 1;
    // endregion

    // region Variables
    static String TAG;
    static String PACKAGE_NAME;

    private TextView logTextView;
    private ScrollView logScrollView;
    // endregion


    private synchronized void log(final String text) {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                logTextView.append(text + System.lineSeparator());
                logScrollView.fullScroll(View.FOCUS_DOWN);
            }
        });
    }

    public void getDataSubmissionProgress(View view) {
        if (Tools.isConnectedToInternet()) {
            Tools.addBackgroundTask(new Runnable() {
                @Override
                public void run() {
                    try {
                        String result = Tools.post(MainActivity.this, R.string.api_get_data_submission_progress);
                        JSONObject jsonObject = new JSONObject(result);

                        log(String.format(
                                Locale.getDefault(),
                                "Locations = %d (%d min ago)",
                                jsonObject.getInt("location_samples"),
                                (System.currentTimeMillis() - jsonObject.getLong("last_location")) / 60000
                        ));
                        log(String.format(
                                Locale.getDefault(),
                                "Bluetooth = %d (%d min ago)",
                                jsonObject.getInt("bt_scan_samples"),
                                (System.currentTimeMillis() - jsonObject.getLong("last_bt_scan")) / 60000
                        ));
                        log(String.format(
                                Locale.getDefault(),
                                "Battery   = %d (%d min ago)",
                                jsonObject.getInt("battery_level_samples"),
                                (System.currentTimeMillis() - jsonObject.getLong("last_battery_level")) / 60000
                        ));
                        log("");
                    } catch (MalformedURLException | ExecutionException | InterruptedException | JSONException e) {
                        log("error: " + e.getMessage());
                        Log.e(TAG, "Failed, reason: " + e.getMessage());
                    }
                }
            });
        } else
            log("No network connection...");
    }

    public void getLofCalculationProgress(View view) {
        if (Tools.isConnectedToInternet()) {
            Tools.addBackgroundTask(new Runnable() {
                @Override
                public void run() {
                    try {
                        String result = Tools.post(MainActivity.this, R.string.api_get_lof_calculation_progress);
                        JSONObject jsonObject = new JSONObject(result);

                        log(String.format(
                                Locale.getDefault(),
                                "%d LOFs calculated (%.3f%% done)",
                                jsonObject.getInt("lof_count"),
                                jsonObject.getDouble("lof_progress") * 100
                        ));
                        log("");
                    } catch (MalformedURLException | ExecutionException | InterruptedException | JSONException e) {
                        log("error: " + e.getMessage());
                        Log.e(TAG, "Failed, reason: " + e.getMessage());
                    }
                }
            });
        } else
            log("No network connection...");
    }

    public void startDataCollectionService(View view) {
        startForegroundService(new Intent(this, DataCollectorService.class));
        log("Data collection service started");
    }

    public void stopDataCollectionService(View view) {
        stopService(new Intent(this, DataCollectorService.class));
        Tools.shutdownBackgroundTasks();
        log("Data collection service stopped");
    }

    public synchronized void clearLogs(View view) {
        logTextView.setText("");
    }
}
