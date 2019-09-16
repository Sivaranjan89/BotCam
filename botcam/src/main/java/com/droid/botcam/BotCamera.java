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
import android.os.Handler;
import android.os.HandlerThread;
import android.util.AttributeSet;
import android.util.Size;
import android.util.SparseIntArray;
import android.view.Surface;
import android.view.TextureView;
import android.widget.Toast;

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

    private Context mContext;

    private CameraDevice.StateCallback callback;
    private TextureView.SurfaceTextureListener surfaceTextureListener;
    private CaptureRequest.Builder captureRequestBuilder;
    private CameraCaptureSession cameraCaptureSession;

    //Interfaces
    private BotCameraListener botCameraListener;

    //Camera Parameters
    private CameraDevice cameraDevice;
    private String cameraId;
    private Size imageDimensions;
    private int selectedCamera = 0;
    private boolean autoSave = true;

    private File imageDirectory;
    private File folder;

    private static final SparseIntArray ORIENTATIONS = new SparseIntArray();
    static {
        ORIENTATIONS.append(Surface.ROTATION_0, 90);
        ORIENTATIONS.append(Surface.ROTATION_90, 0);
        ORIENTATIONS.append(Surface.ROTATION_180, 270);
        ORIENTATIONS.append(Surface.ROTATION_270, 180);
    }

    private HandlerThread mBackgroundThread;
    private Handler mBackgroundHandler;

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
        cameraListeners();
    }

    private void cameraListeners() {
        callback = new CameraDevice.StateCallback() {
            @Override
            public void onOpened(@NonNull CameraDevice device) {
                cameraDevice = device;
                if (botCameraListener != null) {
                    botCameraListener.onCameraOpened(device);
                }

                try {
                    startBackgroundThread();
                    showCameraPreview();
                } catch (CameraAccessException e) {
                    e.printStackTrace();
                }
            }

            @Override
            public void onDisconnected(@NonNull CameraDevice cameraDevice) {
                if (botCameraListener != null) {
                    botCameraListener.onCameraDisconnected(cameraDevice);
                } else {
                    cameraDevice.close();
                    stopBackgroundThread();
                }
            }

            @Override
            public void onError(@NonNull CameraDevice cameraDevice, int i) {
                if (botCameraListener != null) {
                    botCameraListener.onCameraOpenError(cameraDevice, i);
                } else {
                    cameraDevice.close();
                    stopBackgroundThread();
                }
            }
        };


        surfaceTextureListener = new TextureView.SurfaceTextureListener() {
            @Override
            public void onSurfaceTextureAvailable(SurfaceTexture surfaceTexture, int i, int i1) {
                if (botCameraListener != null) {
                    botCameraListener.onSurfaceTextureAvailable(surfaceTexture, i, i1);
                }

                try {
                    openCamera();
                } catch (CameraAccessException e) {
                    e.printStackTrace();
                }
            }

            @Override
            public void onSurfaceTextureSizeChanged(SurfaceTexture surfaceTexture, int i, int i1) {

            }

            @Override
            public boolean onSurfaceTextureDestroyed(SurfaceTexture surfaceTexture) {
                if (botCameraListener != null) {
                    botCameraListener.onSurfaceTextureDestroyed(surfaceTexture);
                }
                return false;
            }

            @Override
            public void onSurfaceTextureUpdated(SurfaceTexture surfaceTexture) {

            }
        };

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
                if (botCameraListener != null) {
                    botCameraListener.onCameraConfigured(captureSession);
                }

                try {
                    updateCameraPreview();
                } catch (CameraAccessException e) {
                    e.printStackTrace();
                }
            }

            @Override
            public void onConfigureFailed(@NonNull CameraCaptureSession captureSession) {

            }
        }, mBackgroundHandler);
    }

    private void updateCameraPreview() throws CameraAccessException {
        if (cameraDevice == null) {
            return;
        }

        captureRequestBuilder.set(CaptureRequest.CONTROL_AF_MODE, CameraMetadata.CONTROL_AF_MODE_CONTINUOUS_PICTURE);

        cameraCaptureSession.setRepeatingRequest(captureRequestBuilder.build(), null, mBackgroundHandler);
    }

    private void openCamera() throws CameraAccessException {
        //Close Camera if already opened
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

        cameraManager.openCamera(cameraId, callback, mBackgroundHandler);
    }

    private void takePicture() throws CameraAccessException {
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
        //Lock Focus
        builder.set(CaptureRequest.CONTROL_AF_TRIGGER, CameraMetadata.CONTROL_AF_TRIGGER_START);

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
                    if (botCameraListener != null) {
                        botCameraListener.getRawImage(bytes);
                    }
                    if (imageDirectory != null && autoSave) {
                        saveImageInDirectory(bytes);
                    }
                } catch (FileNotFoundException e) {
                    e.printStackTrace();
                    Toast.makeText(mContext, "Please create Directory by calling botcamera.setFolderName(String name)",
                            Toast.LENGTH_LONG).show();
                } catch (IOException e) {
                    e.printStackTrace();
                } finally {
                    if (image != null) {
                        image.close();
                    }
                }
            }
        };

        imageReader.setOnImageAvailableListener(readerListener, mBackgroundHandler);

        final CameraCaptureSession.CaptureCallback captureListener = new CameraCaptureSession.CaptureCallback() {
            @Override
            public void onCaptureStarted(@NonNull CameraCaptureSession session, @NonNull CaptureRequest request, long timestamp, long frameNumber) {
                super.onCaptureStarted(session, request, timestamp, frameNumber);
                if (botCameraListener != null) {
                    botCameraListener.onCaptureStarted(session, request, timestamp, frameNumber);
                }
            }

            @Override
            public void onCaptureProgressed(@NonNull CameraCaptureSession session, @NonNull CaptureRequest request, @NonNull CaptureResult partialResult) {
                super.onCaptureProgressed(session, request, partialResult);
            }

            @Override
            public void onCaptureCompleted(@NonNull CameraCaptureSession session, @NonNull CaptureRequest request, @NonNull TotalCaptureResult result) {
                super.onCaptureCompleted(session, request, result);
                if (botCameraListener != null) {
                    botCameraListener.onCaptureCompleted(session, request, result);
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
                if (botCameraListener != null) {
                    botCameraListener.onCaptureFailed(session, request, failure);
                }
            }

            @Override
            public void onCaptureSequenceCompleted(@NonNull CameraCaptureSession session, int sequenceId, long frameNumber) {
                super.onCaptureSequenceCompleted(session, sequenceId, frameNumber);

            }

            @Override
            public void onCaptureSequenceAborted(@NonNull CameraCaptureSession session, int sequenceId) {
                super.onCaptureSequenceAborted(session, sequenceId);

            }

            @Override
            public void onCaptureBufferLost(@NonNull CameraCaptureSession session, @NonNull CaptureRequest request, @NonNull Surface target, long frameNumber) {
                super.onCaptureBufferLost(session, request, target, frameNumber);

            }
        };

        cameraDevice.createCaptureSession(surfaces, new CameraCaptureSession.StateCallback() {
            @Override
            public void onConfigured(@NonNull CameraCaptureSession captureSession) {
                cameraCaptureSession = captureSession;
                if (botCameraListener != null) {
                    botCameraListener.onCameraConfiguredForCapture(captureSession);
                }

                try {
                    captureSession.capture(builder.build(), captureListener, mBackgroundHandler);
                } catch (CameraAccessException e) {
                    e.printStackTrace();
                }
            }

            @Override
            public void onConfigureFailed(@NonNull CameraCaptureSession captureSession) {

            }
        }, mBackgroundHandler);
    }

    private void saveImageInDirectory(byte[] bytes) throws IOException {
        OutputStream outputStream = new FileOutputStream(imageDirectory);
        outputStream.write(bytes);
        outputStream.close();
    }

    private void startBackgroundThread() {
        mBackgroundThread = new HandlerThread("Camera Background");
        mBackgroundThread.start();
        mBackgroundHandler = new Handler(mBackgroundThread.getLooper());
    }

    private void stopBackgroundThread() {
        mBackgroundThread.quitSafely();
        try {
            mBackgroundThread.join();
            mBackgroundThread = null;
            mBackgroundHandler = null;
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }

    public interface BotCameraListener {
        void onCameraOpened(CameraDevice cameraDevice);
        void onCameraDisconnected(CameraDevice cameraDevice);
        void onCameraOpenError(CameraDevice cameraDevice, int i);
        void onSurfaceTextureAvailable(SurfaceTexture surfaceTexture, int i, int i1);
        boolean onSurfaceTextureDestroyed(SurfaceTexture surfaceTexture);
        void onCameraConfigured(CameraCaptureSession captureSession);
        void onCameraConfiguredForCapture(CameraCaptureSession captureSession);
        void onCaptureStarted(CameraCaptureSession session, CaptureRequest request, long timestamp, long frameNumber);
        void onCaptureFailed(CameraCaptureSession session, CaptureRequest request, CaptureFailure failure);
        void onCaptureCompleted(CameraCaptureSession session, CaptureRequest request, TotalCaptureResult result);
        void getRawImage(byte[] image);
    }


    //Setters and Getters
    public void setBotCameraListener(BotCameraListener botCameraListener) {
        this.botCameraListener = botCameraListener;
    }

    public BotCameraListener getBotCameraListener() {
        return botCameraListener;
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

    public void snap() throws CameraAccessException {
        takePicture();
    }

    public void stopCamera() {
        if (cameraDevice != null) {
            cameraDevice.close();
        }
    }

    public void startCamera() throws CameraAccessException {
        openCamera();
    }

    public void restartCamera() throws CameraAccessException {
        openCamera();
    }
}
