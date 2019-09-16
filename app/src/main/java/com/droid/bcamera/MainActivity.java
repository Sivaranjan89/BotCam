package com.droid.bcamera;

import androidx.appcompat.app.AppCompatActivity;

import android.graphics.SurfaceTexture;
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraCaptureSession;
import android.hardware.camera2.CameraDevice;
import android.hardware.camera2.CaptureFailure;
import android.hardware.camera2.CaptureRequest;
import android.hardware.camera2.TotalCaptureResult;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;

import com.droid.botcam.BotCam;
import com.droid.botcam.BotCamera;

import java.io.FileNotFoundException;

public class MainActivity extends AppCompatActivity {

    private BotCam camera;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        camera = findViewById(R.id.camera);

        Button button = findViewById(R.id.capture);
        button.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                try {
                    camera.takePicture();
                } catch (FileNotFoundException e) {
                    e.printStackTrace();
                }
            }
        });
        

        /*final BotCamera camera = findViewById(R.id.camera);
        camera.setFolderName("BotCam");
        //camera.allowAutoSave(false);
        camera.setBotCameraListener(new BotCamera.BotCameraListener() {
            @Override
            public void onCameraOpened(CameraDevice cameraDevice) {

            }

            @Override
            public void onCameraDisconnected(CameraDevice cameraDevice) {

            }

            @Override
            public void onCameraOpenError(CameraDevice cameraDevice, int i) {

            }

            @Override
            public void onSurfaceTextureAvailable(SurfaceTexture surfaceTexture, int i, int i1) {

            }

            @Override
            public boolean onSurfaceTextureDestroyed(SurfaceTexture surfaceTexture) {
                return false;
            }

            @Override
            public void onCameraConfigured(CameraCaptureSession captureSession) {

            }

            @Override
            public void onCameraConfiguredForCapture(CameraCaptureSession captureSession) {

            }

            @Override
            public void onCaptureStarted(CameraCaptureSession session, CaptureRequest request, long timestamp, long frameNumber) {

            }

            @Override
            public void onCaptureFailed(CameraCaptureSession session, CaptureRequest request, CaptureFailure failure) {

            }

            @Override
            public void onCaptureCompleted(CameraCaptureSession session, CaptureRequest request, TotalCaptureResult result) {

            }

            @Override
            public void getRawImage(byte[] image) {

            }
        });

        */
    }
}
