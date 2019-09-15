package com.droid.botcam;

import android.Manifest;
import android.app.Activity;
import android.content.Context;
import android.content.pm.PackageManager;
import android.graphics.ImageFormat;
import android.graphics.SurfaceTexture;
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraCaptureSession;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraDevice;
import android.hardware.camera2.CameraManager;
import android.hardware.camera2.CameraMetadata;
import android.hardware.camera2.CaptureFailure;
import android.hardware.camera2.CaptureRequest;
import android.hardware.camera2.CaptureResult;
import android.hardware.camera2.TotalCaptureResult;
import android.hardware.camera2.params.StreamConfigurationMap;
import android.media.Image;
import android.media.ImageReader;
import android.util.AttributeSet;
import android.util.Size;
import android.util.SparseIntArray;
import android.view.LayoutInflater;
import android.view.Surface;
import android.view.TextureView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class BotCamera extends TextureView {

    private static final int REQUEST_CAMERA_PERMISSION = 1;
    private static final int REQUEST_EXTERNALSTORAGE_PERMISSION = 2;
    private Context mContext;

    private CameraDevice.StateCallback callback;
    private TextureView.SurfaceTextureListener surfaceTextureListener;
    private CameraDevice cameraDevice;

    private CameraStateCallback cameraStateCallback;
    private SurfaceTextureListener textureListener;
    private CaptureSessionCallback sessionCallback;
    private CaptureListener cameraCaptureListener;
    private ReceiveRawImageCallback receiveRawImageCallback;

    private String cameraId;

    private Size imageDimensions;

    private CaptureRequest.Builder captureRequestBuilder;

    private CameraCaptureSession cameraCaptureSession;

    private static int REAR_CAMERA = 0;
    private static int FRONT_CAMERA = 1;
    private int selectedCamera = 0;

    private File imageDirectory;
    private File folder;

    private boolean autoSave = true;

    private static final SparseIntArray ORIENTATIONS = new SparseIntArray();
    static {
        ORIENTATIONS.append(Surface.ROTATION_0, 90);
        ORIENTATIONS.append(Surface.ROTATION_90, 0);
        ORIENTATIONS.append(Surface.ROTATION_180, 270);
        ORIENTATIONS.append(Surface.ROTATION_270, 180);
    }



    public BotCamera(Context context) {
        super(context);
        mContext = context;
        init();
    }

    public BotCamera(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        mContext = context;
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
                    showCameraPreview();
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

        setSurfaceTextureListener(null);
        setSurfaceTextureListener(surfaceTextureListener);
    }

    private void showCameraPreview() throws CameraAccessException {
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

        captureRequestBuilder.set(CaptureRequest.CONTROL_MODE, CameraMetadata.CONTROL_AE_MODE_ON_AUTO_FLASH_REDEYE);

        cameraCaptureSession.setRepeatingRequest(captureRequestBuilder.build(), null, null);
    }

    public void openCamera() throws CameraAccessException {
        if (cameraDevice != null) {
            cameraDevice.close();
        }
        //Check for permission
        if (ContextCompat.checkSelfPermission(mContext, Manifest.permission.CAMERA)
                != PackageManager.PERMISSION_GRANTED) {
            return;
        }

        CameraManager cameraManager = (CameraManager)mContext.getSystemService(Context.CAMERA_SERVICE);
        cameraId = cameraManager.getCameraIdList()[selectedCamera];

        CameraCharacteristics cameraCharacteristics = cameraManager.getCameraCharacteristics(cameraId);

        StreamConfigurationMap configurationMap = cameraCharacteristics.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP);

        imageDimensions = configurationMap.getOutputSizes(SurfaceTexture.class)[0];

        cameraManager.openCamera(cameraId, callback, null);
    }

    public void takePicture() throws CameraAccessException {
        CameraManager cameraManager = (CameraManager)mContext.getSystemService(Context.CAMERA_SERVICE);
        CameraCharacteristics cameraCharacteristics = cameraManager.getCameraCharacteristics(cameraDevice.getId());

        Size[] imageSizes = cameraCharacteristics.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP).getOutputSizes(ImageFormat.JPEG);

        int imageWidth = 1080;
        int imageHeight = 1920;

        if (imageSizes != null && imageSizes.length > 0) {
            imageWidth = imageSizes[0].getWidth();
            imageHeight = imageSizes[0].getHeight();
        }

        ImageReader imageReader = ImageReader.newInstance(imageWidth, imageHeight, ImageFormat.JPEG, 1);
        List<Surface> surfaces = new ArrayList<>(2);
        surfaces.add(imageReader.getSurface());
        surfaces.add(new Surface(this.getSurfaceTexture()));

        final CaptureRequest.Builder builder = cameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_STILL_CAPTURE);
        builder.addTarget(imageReader.getSurface());
        builder.set(CaptureRequest.CONTROL_MODE, CameraMetadata.CONTROL_MODE_AUTO);

        int deviceRotation = ((Activity)mContext).getWindowManager().getDefaultDisplay().getRotation();
        builder.set(CaptureRequest.JPEG_ORIENTATION, ORIENTATIONS.get(deviceRotation));

        Long ts = System.currentTimeMillis() / 1000;
        String timeStamp = ts.toString();

        if (folder != null) {
            imageDirectory = new File(folder, timeStamp + ".jpg");
        }

        ImageReader.OnImageAvailableListener readerListener = new ImageReader.OnImageAvailableListener() {
            @Override
            public void onImageAvailable(ImageReader imageReader) {
                Image image = imageReader.acquireLatestImage();
                ByteBuffer buffer = image.getPlanes()[0].getBuffer();
                byte[] bytes = new byte[buffer.capacity()];
                buffer.get(bytes);
                try {
                    if (imageDirectory != null && autoSave) {
                        processImage(bytes);
                    } else {
                        if (receiveRawImageCallback != null) {
                            receiveRawImageCallback.getRawImage(bytes);
                        }
                    }
                } catch (FileNotFoundException e) {
                    e.printStackTrace();
                } catch (IOException e) {
                    e.printStackTrace();
                } finally {
                    if (image != null) {
                        image.close();
                    }
                }
            }
        };

        imageReader.setOnImageAvailableListener(readerListener, null);

        final CameraCaptureSession.CaptureCallback captureListener = new CameraCaptureSession.CaptureCallback() {
            @Override
            public void onCaptureStarted(@NonNull CameraCaptureSession session, @NonNull CaptureRequest request, long timestamp, long frameNumber) {
                super.onCaptureStarted(session, request, timestamp, frameNumber);
                if (cameraCaptureListener != null) {
                    cameraCaptureListener.onCaptureStarted(session, request, timestamp, frameNumber);
                }
            }

            @Override
            public void onCaptureProgressed(@NonNull CameraCaptureSession session, @NonNull CaptureRequest request, @NonNull CaptureResult partialResult) {
                super.onCaptureProgressed(session, request, partialResult);
                if (cameraCaptureListener != null) {
                    cameraCaptureListener.onCaptureProgressed(session, request, partialResult);
                }
            }

            @Override
            public void onCaptureCompleted(@NonNull CameraCaptureSession session, @NonNull CaptureRequest request, @NonNull TotalCaptureResult result) {
                super.onCaptureCompleted(session, request, result);
                if (cameraCaptureListener != null) {
                    cameraCaptureListener.onCaptureCompleted(session, request, result);
                }
                try {
                    openCamera();
                } catch (CameraAccessException e) {
                    e.printStackTrace();
                }
            }

            @Override
            public void onCaptureFailed(@NonNull CameraCaptureSession session, @NonNull CaptureRequest request, @NonNull CaptureFailure failure) {
                super.onCaptureFailed(session, request, failure);
                if (cameraCaptureListener != null) {
                    cameraCaptureListener.onCaptureFailed(session, request, failure);
                }
            }

            @Override
            public void onCaptureSequenceCompleted(@NonNull CameraCaptureSession session, int sequenceId, long frameNumber) {
                super.onCaptureSequenceCompleted(session, sequenceId, frameNumber);
                if (cameraCaptureListener != null) {
                    cameraCaptureListener.onCaptureSequenceCompleted(session, sequenceId, frameNumber);
                }
            }

            @Override
            public void onCaptureSequenceAborted(@NonNull CameraCaptureSession session, int sequenceId) {
                super.onCaptureSequenceAborted(session, sequenceId);
                if (cameraCaptureListener != null) {
                    cameraCaptureListener.onCaptureSequenceAborted(session, sequenceId);
                }
            }

            @Override
            public void onCaptureBufferLost(@NonNull CameraCaptureSession session, @NonNull CaptureRequest request, @NonNull Surface target, long frameNumber) {
                super.onCaptureBufferLost(session, request, target, frameNumber);
                if (cameraCaptureListener != null) {
                    cameraCaptureListener.onCaptureBufferLost(session, request, target, frameNumber);
                }
            }
        };

        cameraDevice.createCaptureSession(surfaces, new CameraCaptureSession.StateCallback() {
            @Override
            public void onConfigured(@NonNull CameraCaptureSession captureSession) {
                cameraCaptureSession = captureSession;
                if (sessionCallback != null) {
                    sessionCallback.onConfigured(captureSession);
                }

                try {
                    captureSession.capture(builder.build(), captureListener, null);
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

    private void processImage(byte[] bytes) throws IOException {
        OutputStream outputStream = new FileOutputStream(imageDirectory);
        outputStream.write(bytes);
        outputStream.close();
    }

    public interface CameraStateCallback {
        void onOpened(CameraDevice cameraDevice);
        void onDisconnected(CameraDevice cameraDevice);
        void onError(CameraDevice cameraDevice, int i);
    }

    public interface SurfaceTextureListener {
        void onSurfaceTextureAvailable(SurfaceTexture surfaceTexture, int i, int i1);
        void onSurfaceTextureSizeChanged(SurfaceTexture surfaceTexture, int i, int i1);
        boolean onSurfaceTextureDestroyed(SurfaceTexture surfaceTexture);
        void onSurfaceTextureUpdated(SurfaceTexture surfaceTexture);
    }

    public interface CaptureSessionCallback {
        void onConfigured(CameraCaptureSession captureSession);
        void onConfigureFailed(CameraCaptureSession cameraCaptureSession);
    }

    public interface CaptureListener {
        void onCaptureStarted(CameraCaptureSession session, CaptureRequest request, long timestamp, long frameNumber);
        void onCaptureProgressed(CameraCaptureSession session, CaptureRequest request, CaptureResult partialResult);
        void onCaptureCompleted(CameraCaptureSession session, CaptureRequest request, TotalCaptureResult result);
        void onCaptureFailed(CameraCaptureSession session, CaptureRequest request, CaptureFailure failure);
        void onCaptureSequenceCompleted(CameraCaptureSession session, int sequenceId, long frameNumber);
        void onCaptureSequenceAborted(CameraCaptureSession session, int sequenceId);
        void onCaptureBufferLost(CameraCaptureSession session, CaptureRequest request, Surface target, long frameNumber);
    }

    public interface ReceiveRawImageCallback {
        void getRawImage(byte[] image);
    }


    //Setters and Getters
    public void setBotCamCaptureListener(CaptureListener cameraCaptureListener) {
        this.cameraCaptureListener = cameraCaptureListener;
    }

    public CaptureListener getBotCamCaptureListener() {
        return cameraCaptureListener;
    }

    public void setBotCamCaptureSession(CaptureSessionCallback cameraCaptureSession) {
        this.sessionCallback = cameraCaptureSession;
    }

    public CaptureSessionCallback getBotCamCaptureSession() {
        return sessionCallback;
    }

    public void setBotCamSurfaceTextureListener(SurfaceTextureListener surfaceTextureListener) {
        this.textureListener = surfaceTextureListener;
    }

    public SurfaceTextureListener getBotCamSurfaceTextureListener() {
        return this.textureListener;
    }

    public void setBotCamStateCallback(CameraStateCallback cameraStateCallback) {
        this.cameraStateCallback = cameraStateCallback;
    }

    public CameraStateCallback getBotCamStateCallback() {
        return cameraStateCallback;
    }

    public void setFolderName(String folderName) {
        if (ContextCompat.checkSelfPermission(mContext, Manifest.permission.WRITE_EXTERNAL_STORAGE)
                != PackageManager.PERMISSION_GRANTED) {
            return;
        }

        folder = DroidFunctions.createFolderInExternalStorage(folderName);
    }

    public void allowAutoSave(boolean save) {
        this.autoSave = save;
    }

    public void useFrontCamera() throws CameraAccessException {
        selectedCamera = 1;
        openCamera();
    }

    public void useRearCamera() throws CameraAccessException {
        selectedCamera = 0;
        openCamera();
    }

    public void onReceivingRawImage(ReceiveRawImageCallback receiveRawImageCallback){
        this.receiveRawImageCallback = receiveRawImageCallback;
    }

    public ReceiveRawImageCallback getReceivedRawImageCallback() {
        return this.receiveRawImageCallback;
    }
}
