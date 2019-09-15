package com.droid.botcam;

import android.Manifest;
import android.app.Activity;
import android.content.Context;
import android.content.pm.PackageManager;
import android.graphics.SurfaceTexture;
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraCaptureSession;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraDevice;
import android.hardware.camera2.CameraManager;
import android.hardware.camera2.CameraMetadata;
import android.hardware.camera2.CaptureRequest;
import android.hardware.camera2.params.StreamConfigurationMap;
import android.os.Handler;
import android.util.AttributeSet;
import android.util.Size;
import android.view.LayoutInflater;
import android.view.Surface;
import android.view.TextureView;
import android.view.View;
import android.widget.RelativeLayout;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import java.util.Arrays;

public class BotCamera extends TextureView {

    private static final int REQUEST_CAMERA_PERMISSION = 1;
    private Context mContext;
    private View cameraLayout;
    private LayoutInflater inflater;

    private CameraDevice.StateCallback callback;
    private TextureView.SurfaceTextureListener surfaceTextureListener;
    private CameraDevice cameraDevice;

    private CameraStateCallback cameraStateCallback;
    private SurfaceTextureListener textureListener;
    private CaptureSessionCallback sessionCallback;

    private String cameraId;

    private Size imageDimensions;

    private CaptureRequest.Builder captureRequestBuilder;

    private CameraCaptureSession cameraCaptureSession;
    private Handler backgroundHandler;

    public BotCamera(Context context) {
        super(context);
        mContext = context;
        inflater = (LayoutInflater)mContext.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
        cameraLayout = inflater.inflate(R.layout.botcamera, null);
        init();
    }

    public BotCamera(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        mContext = context;
        inflater = (LayoutInflater)mContext.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
        cameraLayout = inflater.inflate(R.layout.botcamera, null);
        init();
    }

    private void init() {
        cameraLifecycle();
    }

    private void cameraLifecycle() {
        callback = new CameraDevice.StateCallback() {
            @Override
            public void onOpened(@NonNull CameraDevice device) {
                cameraDevice = device;
                if (cameraStateCallback != null) {
                    cameraStateCallback.onOpened(device);
                }

                try {
                    showCameraPreview(DroidFunctions.getScreenWidth(), DroidFunctions.getScreenHeight());
                } catch (CameraAccessException e) {
                    e.printStackTrace();
                }
            }

            @Override
            public void onDisconnected(@NonNull CameraDevice cameraDevice) {
                if (cameraStateCallback != null) {
                    cameraStateCallback.onDisconnected(cameraDevice);
                } else {
                    cameraDevice.close();
                }
            }

            @Override
            public void onError(@NonNull CameraDevice cameraDevice, int i) {
                if (cameraStateCallback != null) {
                    cameraStateCallback.onError(cameraDevice, i);
                } else {
                    cameraDevice.close();
                }
            }
        };


        surfaceTextureListener = new TextureView.SurfaceTextureListener() {
            @Override
            public void onSurfaceTextureAvailable(SurfaceTexture surfaceTexture, int i, int i1) {
                if (textureListener != null) {
                    textureListener.onSurfaceTextureAvailable(surfaceTexture, i, i1);
                }

                try {
                    openCamera();
                } catch (CameraAccessException e) {
                    e.printStackTrace();
                }
            }

            @Override
            public void onSurfaceTextureSizeChanged(SurfaceTexture surfaceTexture, int i, int i1) {
                if (textureListener != null) {
                    textureListener.onSurfaceTextureSizeChanged(surfaceTexture, i, i1);
                }
            }

            @Override
            public boolean onSurfaceTextureDestroyed(SurfaceTexture surfaceTexture) {
                if (textureListener != null) {
                    textureListener.onSurfaceTextureDestroyed(surfaceTexture);
                }
                return false;
            }

            @Override
            public void onSurfaceTextureUpdated(SurfaceTexture surfaceTexture) {
                if (textureListener != null) {
                    textureListener.onSurfaceTextureUpdated(surfaceTexture);
                }
            }
        };

        setSurfaceTextureListener(surfaceTextureListener);
    }

    private void showCameraPreview(int screenWidth, int screenHeight) throws CameraAccessException {
        SurfaceTexture surfaceTexture = getSurfaceTexture();
        surfaceTexture.setDefaultBufferSize(imageDimensions.getWidth(), imageDimensions.getHeight());
        Surface surface = new Surface(surfaceTexture);

        captureRequestBuilder = cameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW);
        captureRequestBuilder.addTarget(surface);

        cameraDevice.createCaptureSession(Arrays.asList(surface), new CameraCaptureSession.StateCallback() {
            @Override
            public void onConfigured(@NonNull CameraCaptureSession captureSession) {
                cameraCaptureSession = captureSession;
                if (sessionCallback != null) {
                    sessionCallback.onConfigured(captureSession);
                }

                try {
                    updateCameraPreview();
                } catch (CameraAccessException e) {
                    e.printStackTrace();
                }
            }

            @Override
            public void onConfigureFailed(@NonNull CameraCaptureSession captureSession) {
                if (sessionCallback != null) {
                    sessionCallback.onConfigureFailed(captureSession);
                }
            }
        }, null);
    }

    private void updateCameraPreview() throws CameraAccessException {
        if (cameraDevice == null) {
            return;
        }

        captureRequestBuilder.set(CaptureRequest.CONTROL_MODE, CameraMetadata.CONTROL_MODE_AUTO);

        cameraCaptureSession.setRepeatingRequest(captureRequestBuilder.build(), null, backgroundHandler);
    }

    public void openCamera() throws CameraAccessException {
        //Check for permission and request if not granted
        if (ContextCompat.checkSelfPermission(mContext, Manifest.permission.CAMERA)
                != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions((Activity) mContext, new String[]{Manifest.permission.CAMERA},
                    REQUEST_CAMERA_PERMISSION);
            return;
        }

        CameraManager cameraManager = (CameraManager)mContext.getSystemService(Context.CAMERA_SERVICE);
        cameraId = cameraManager.getCameraIdList()[0];

        CameraCharacteristics cameraCharacteristics = cameraManager.getCameraCharacteristics(cameraId);

        StreamConfigurationMap configurationMap = cameraCharacteristics.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP);

        imageDimensions = configurationMap.getOutputSizes(SurfaceTexture.class)[0];

        cameraManager.openCamera(cameraId, callback, null);
    }

    private interface CameraStateCallback {
        void onOpened(CameraDevice cameraDevice);
        void onDisconnected(CameraDevice cameraDevice);
        void onError(CameraDevice cameraDevice, int i);
    }

    private interface SurfaceTextureListener {
        void onSurfaceTextureAvailable(SurfaceTexture surfaceTexture, int i, int i1);
        void onSurfaceTextureSizeChanged(SurfaceTexture surfaceTexture, int i, int i1);
        boolean onSurfaceTextureDestroyed(SurfaceTexture surfaceTexture);
        void onSurfaceTextureUpdated(SurfaceTexture surfaceTexture);
    }

    private interface CaptureSessionCallback {
        void onConfigured(CameraCaptureSession captureSession);
        void onConfigureFailed(CameraCaptureSession cameraCaptureSession);
    }
}
