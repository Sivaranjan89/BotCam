package com.droid.bcamera;

import android.Manifest;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.view.View;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;

import com.droid.botcam.BotCamera;
import com.droid.botcam.DroidFunctions;

public class MainActivity extends AppCompatActivity {

    BotCamera botCamera;

    boolean isFront = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        botCamera = BotCamera.newInstance();

        findViewById(R.id.takePic).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                botCamera.takePicture(null);
            }
        });

        findViewById(R.id.switchCamera).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                if (isFront) {
                    botCamera.useRearCamera();
                    isFront = false;
                } else {
                    botCamera.useFrontCamera();
                    isFront = true;
                }
            }
        });

        if (hasAccess()) {
            getSupportFragmentManager().beginTransaction()
                    .replace(R.id.cameraPreview, botCamera)
                    .commit();
        }
    }

    private boolean hasAccess() {
        boolean granted = true;
        if (ContextCompat.checkSelfPermission(MainActivity.this, Manifest.permission.WRITE_EXTERNAL_STORAGE)
                != PackageManager.PERMISSION_GRANTED) {
            DroidFunctions.requestStoragePermission(MainActivity.this, 2);
            granted = false;
        }

        if (ContextCompat.checkSelfPermission(MainActivity.this, Manifest.permission.CAMERA)
                != PackageManager.PERMISSION_GRANTED) {
            DroidFunctions.requestCameraPermission(MainActivity.this, 1);
            granted = false;
        }

        return granted;
    }
}
