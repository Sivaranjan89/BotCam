package com.droid.bcamera;

import android.Manifest;
import android.content.pm.PackageManager;
import android.graphics.SurfaceTexture;
import android.hardware.camera2.CameraCaptureSession;
import android.hardware.camera2.CameraDevice;
import android.hardware.camera2.CaptureFailure;
import android.hardware.camera2.CaptureRequest;
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

        setBotCameraConfiguration();

        findViewById(R.id.takePic).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                botCamera.takePicture();
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

    private void setBotCameraConfiguration() {
        botCamera.setFlashEnabled(false);
        botCamera.setZoomEnabled(true);
        botCamera.useRearCamera();

        botCamera.setOnImageCaptured(new BotCamera.OnImageCaptured() {
            @Override
            public void onImageCaptured(byte[] image) {
                botCamera.saveImageToDirectory(null, image, null);
            }

            @Override
            public void onImageCaptureFailed(CameraCaptureSession session, CaptureRequest request, CaptureFailure failure) {

            }
        });

        botCamera.setOnCameraStarted(new BotCamera.OnCameraStarted() {
            @Override
            public void onCameraOpened(CameraDevice cameraDevice) {

            }

            @Override
            public void onCameraDisconected(CameraDevice cameraDevice) {

            }

            @Override
            public void onCameraError(CameraDevice cameraDevice, int error) {

            }
        });

        botCamera.setOnSurfaceCreated(new BotCamera.OnSurfaceCreated() {
            @Override
            public void onSurfaceAvailable(SurfaceTexture texture, int width, int height) {

            }

            @Override
            public void onSurfaceDestroyed(SurfaceTexture texture) {

            }
        });
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
