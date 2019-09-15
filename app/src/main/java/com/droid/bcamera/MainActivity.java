package com.droid.bcamera;

import androidx.appcompat.app.AppCompatActivity;

import android.hardware.camera2.CameraAccessException;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;

import com.droid.botcam.BotCamera;

public class MainActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        final BotCamera camera = findViewById(R.id.camera);
        camera.setFolderName("BotCam");
        camera.allowAutoSave(false);
        camera.onReceivingRawImage(new BotCamera.ReceiveRawImageCallback() {
            @Override
            public void getRawImage(byte[] image) {
                byte[] sada = image;
            }
        });

        Button button = findViewById(R.id.capture);
        button.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                try {
                    camera.takePicture();
                } catch (CameraAccessException e) {
                    e.printStackTrace();
                }
            }
        });
    }
}
