package com.example.datacollector;

import android.Manifest;
import android.content.Intent;
import android.os.Bundle;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;

public class MainActivity extends AppCompatActivity {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        MainActivity.TAG = "MyDataCollector";
        MainActivity.PACKAGE_NAME = this.getPackageName();

        Tools.initData(this);

        if (Tools.isLocationPermissionDenied(this)) {
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION}, GPS_PERMISSION_REQUEST_CODE);
        } else {
            stopService(new Intent(this, DataCollectorService.class));
            startForegroundService(new Intent(this, DataCollectorService.class));
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
    // endregion
}
