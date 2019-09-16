package com.droid.botcam;

import android.Manifest;
import android.app.Activity;
import android.content.Context;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
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
import android.view.View;
import android.view.ViewGroup;
import android.widget.RelativeLayout;

import androidx.annotation.NonNull;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.Semaphore;

public class BotCam extends RelativeLayout {
    private static final int STATE_PREVIEW = 0;
    private static final int STATE_WAITING_LOCK = 1;
    private static final int STATE_WAITING_PRECAPTURE = 2;
    private static final int STATE_WAITING_NON_PRECAPTURE = 3;
    private static final int STATE_PICTURE_TAKEN = 4;
    private int mState;

    Context mContext;

    TextureView cameraView;

    //Camera Parameters
    private CameraDevice cameraDevice;
    private String cameraId;
    private int cameraFacing = CameraCharacteristics.LENS_FACING_BACK;
    private CameraManager cameraManager;
    private Size imageDimensions;
    private Size[] imageSizes;

    private HandlerThread backgroundThread;
    private Handler backgroundHandler;

    //Listeners
    private TextureView.SurfaceTextureListener surfaceTextureListener;
    private CameraDevice.StateCallback stateCallback;
    private CaptureRequest.Builder captureRequestBuilder;
    private CaptureRequest captureRequest;
    private CameraCaptureSession cameraCaptureSession;
    CameraCaptureSession.CaptureCallback captureCallback;

    private String folderName = "BotCamera";
    private File folder;
    private File imageDirectory;

    private static final SparseIntArray ORIENTATIONS=new SparseIntArray();
    static {
        ORIENTATIONS.append(Surface.ROTATION_0, 90);
        ORIENTATIONS.append(Surface.ROTATION_90, 0);
        ORIENTATIONS.append(Surface.ROTATION_180, 270);
        ORIENTATIONS.append(Surface.ROTATION_270, 180);
    }

    private Semaphore mCameraOpenCloseLock = new Semaphore(1);
    private ImageReader.OnImageAvailableListener imageAvailableListener;


    public BotCam(Context context) {
        super(context);
        mContext = context;
        init();
    }

    public BotCam(Context context, AttributeSet attrs) {
        super(context, attrs);
        mContext = context;
        init();
    }

    private void init() {
        this.removeAllViews();

        cameraView = new TextureView(mContext);
        cameraView.setLayoutParams(new LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        this.addView(cameraView);

        createStateCallback();
        createSurfaceTextureListener();
        createCaptureCallback();
        createImageReaderCallback();
        createFolderInExternalStorage();
        cameraView.setSurfaceTextureListener(surfaceTextureListener);
    }

    private void createImageReaderCallback() {
        imageAvailableListener = new ImageReader.OnImageAvailableListener() {
            @Override
            public void onImageAvailable(ImageReader reader) {
                Image image = null;
                try {
                    image = reader.acquireLatestImage();
                    ByteBuffer buffer = image.getPlanes()[0].getBuffer();
                    byte[] bytes = new byte[buffer.capacity()];
                    buffer.get(bytes);

                    if (folder != null) {
                        Long ts = System.currentTimeMillis() / 1000;
                        String timeStamp = ts.toString();
                        imageDirectory = new File(folder, timeStamp + ".jpg");
                    }

                    if (imageDirectory != null) {
                        save(bytes);
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                }
                finally {
                    if(image!=null)
                        image.close();
                }
            }
        };
    }

    private void createCaptureCallback() {
        captureCallback = new CameraCaptureSession.CaptureCallback() {

            private void process(CaptureResult result) {
                switch (mState) {
                    case STATE_PREVIEW: {
                        //Do NOthing
                        break;
                    }
                    case STATE_WAITING_LOCK: {
                        Integer afState = result.get(CaptureResult.CONTROL_AF_STATE);
                        if (afState == null) {
                            captureStillPicture();
                        } else if (CaptureResult.CONTROL_AF_STATE_FOCUSED_LOCKED == afState ||
                                CaptureResult.CONTROL_AF_STATE_NOT_FOCUSED_LOCKED == afState) {
                            Integer aeState = result.get(CaptureResult.CONTROL_AE_STATE);
                            if (aeState == null || aeState == CaptureResult.CONTROL_AE_STATE_CONVERGED) {
                                mState = STATE_PICTURE_TAKEN;
                                captureStillPicture();
                            } else {
                                runPrecaptureSequence();
                            }
                        }
                        break;
                    }
                    case STATE_WAITING_PRECAPTURE: {
                        Integer aeState = result.get(CaptureResult.CONTROL_AE_STATE);
                        if (aeState == null || aeState == CaptureResult.CONTROL_AE_STATE_PRECAPTURE || aeState == CaptureRequest.CONTROL_AE_STATE_FLASH_REQUIRED) {
                            mState = STATE_WAITING_NON_PRECAPTURE;
                        }
                        break;
                    }
                    case STATE_WAITING_NON_PRECAPTURE: {
                        Integer aeState = result.get(CaptureResult.CONTROL_AE_STATE);
                        if (aeState == null || aeState != CaptureResult.CONTROL_AE_STATE_PRECAPTURE) {
                            mState = STATE_PICTURE_TAKEN;
                            captureStillPicture();
                        }
                        break;
                    }
                }
            }

            @Override
            public void onCaptureStarted(CameraCaptureSession session, CaptureRequest request, long timestamp, long frameNumber) {
                super.onCaptureStarted(session, request, timestamp, frameNumber);
            }

            @Override
            public void onCaptureCompleted(CameraCaptureSession session, CaptureRequest request, TotalCaptureResult result) {
                super.onCaptureCompleted(session, request, result);
                process(result);
            }

            @Override
            public void onCaptureProgressed(@NonNull CameraCaptureSession session, @NonNull CaptureRequest request, @NonNull CaptureResult partialResult) {
                super.onCaptureProgressed(session, request, partialResult);
                process(partialResult);
            }

            @Override
            public void onCaptureFailed(@NonNull CameraCaptureSession session, @NonNull CaptureRequest request, @NonNull CaptureFailure failure) {
                super.onCaptureFailed(session, request, failure);
            }
        };
    }

    private void createStateCallback() {
        stateCallback = new CameraDevice.StateCallback() {
            @Override
            public void onOpened(CameraDevice device) {
                mCameraOpenCloseLock.release();
                cameraDevice = device;
                createPreview();
            }

            @Override
            public void onDisconnected(CameraDevice device) {
                mCameraOpenCloseLock.release();
                device.close();
                cameraDevice = null;
            }

            @Override
            public void onError(CameraDevice device, int error) {
                mCameraOpenCloseLock.release();
                device.close();
                cameraDevice = null;
            }
        };
    }

    private void createSurfaceTextureListener() {
        surfaceTextureListener = new TextureView.SurfaceTextureListener() {
            @Override
            public void onSurfaceTextureAvailable(SurfaceTexture surfaceTexture, int width, int height) {
                setUpCamera();
                openCamera();
            }

            @Override
            public void onSurfaceTextureSizeChanged(SurfaceTexture surfaceTexture, int width, int height) {
            }

            @Override
            public boolean onSurfaceTextureDestroyed(SurfaceTexture surfaceTexture) {
                return true;
            }

            @Override
            public void onSurfaceTextureUpdated(SurfaceTexture surfaceTexture) {
            }
        };
    }

    private void createPreview() {
        try {
            SurfaceTexture surfaceTexture = cameraView.getSurfaceTexture();
            surfaceTexture.setDefaultBufferSize(imageDimensions.getWidth(), imageDimensions.getHeight());

            Surface previewSurface = new Surface(surfaceTexture);

            captureRequestBuilder = cameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW);
            captureRequestBuilder.addTarget(previewSurface);

            cameraDevice.createCaptureSession(Collections.singletonList(previewSurface),
                    new CameraCaptureSession.StateCallback() {
                        @Override
                        public void onConfigured(CameraCaptureSession captureSession) {
                            if (cameraDevice == null) {
                                return;
                            }

                            try {
                                cameraCaptureSession = captureSession;

                                captureRequestBuilder.set(CaptureRequest.CONTROL_AF_MODE, CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE);

                                captureRequest = captureRequestBuilder.build();
                                cameraCaptureSession.setRepeatingRequest(captureRequest,
                                        captureCallback, backgroundHandler);
                            } catch (CameraAccessException e) {
                                e.printStackTrace();
                            }
                        }

                        @Override
                        public void onConfigureFailed(CameraCaptureSession cameraCaptureSession) {
                        }
                    }, backgroundHandler);
        } catch (CameraAccessException e) {
            e.printStackTrace();
        }
    }

    private void setUpCamera() {
        try {
            cameraManager = (CameraManager)mContext.getSystemService(Context.CAMERA_SERVICE);

            for (String cameraId : cameraManager.getCameraIdList()) {
                CameraCharacteristics cameraCharacteristics =
                        cameraManager.getCameraCharacteristics(cameraId);
                if (cameraCharacteristics.get(CameraCharacteristics.LENS_FACING) ==
                        this.cameraFacing) {
                    StreamConfigurationMap streamConfigurationMap = cameraCharacteristics.get(
                            CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP);
                    imageDimensions = streamConfigurationMap.getOutputSizes(SurfaceTexture.class)[0];
                    this.cameraId = cameraId;
                }
            }
        } catch (CameraAccessException e) {
            e.printStackTrace();
        }
    }

    private void openCamera() {
        try {
            if (ActivityCompat.checkSelfPermission(mContext, android.Manifest.permission.CAMERA)
                    == PackageManager.PERMISSION_GRANTED) {
                cameraManager.openCamera(cameraId, stateCallback, backgroundHandler);
            }
        } catch (CameraAccessException e) {
            e.printStackTrace();
        }
    }

    public void closeCamera() throws InterruptedException {
        try {
            mCameraOpenCloseLock.acquire();
            if (null != cameraCaptureSession) {
                cameraCaptureSession.close();
                cameraCaptureSession = null;
            }
            if (null != cameraDevice) {
                cameraDevice.close();
                cameraDevice = null;
            }
        } catch (InterruptedException e) {
            throw new RuntimeException("Interrupted while trying to lock camera closing.", e);
        } finally {
            mCameraOpenCloseLock.release();
            stopBackgroundThread();
        }
    }

    private void openBackgroundThread() {
        backgroundThread = new HandlerThread("camera_background_thread");
        backgroundThread.start();
        backgroundHandler = new Handler(backgroundThread.getLooper());
    }

    private void stopBackgroundThread() throws InterruptedException {
        backgroundThread.quitSafely();
        backgroundThread.join();
        backgroundThread = null;
        backgroundHandler = null;
    }

    private void createFolderInExternalStorage() {
        if (ContextCompat.checkSelfPermission(mContext, Manifest.permission.WRITE_EXTERNAL_STORAGE)
                != PackageManager.PERMISSION_GRANTED) {
            return;
        }

        folder = DroidFunctions.createFolderInExternalStorage(folderName);
    }

    private void runPrecaptureSequence() {
        try {
            // This is how to tell the camera to trigger.
            captureRequestBuilder.set(CaptureRequest.CONTROL_AE_PRECAPTURE_TRIGGER, CaptureRequest.CONTROL_AE_PRECAPTURE_TRIGGER_START);

            // Tell captureCallback to wait for the precapture sequence to be set.
            mState = STATE_WAITING_PRECAPTURE;
            cameraCaptureSession.capture(captureRequestBuilder.build(), captureCallback, backgroundHandler);
        } catch (CameraAccessException e) {
            e.printStackTrace();
        }
    }

    private void captureStillPicture() {
        try {
            CameraCharacteristics cameraCharacteristics = cameraManager.getCameraCharacteristics(cameraDevice.getId());
            if(cameraCharacteristics!=null)
            {
                imageSizes = cameraCharacteristics.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP).getOutputSizes(ImageFormat.JPEG);
            }

            int width = 640, height = 480;
            if(imageSizes!=null && imageSizes.length>0)
            {
                width = imageSizes[0].getWidth();
                height = imageSizes[0].getHeight();
            }

            //Getting the Suface ready for Capture
            ImageReader imageReader = ImageReader.newInstance(width, height, ImageFormat.JPEG,1);
            imageReader.setOnImageAvailableListener(imageAvailableListener, backgroundHandler);

            // This is the CaptureRequest.Builder that we use to take a picture.
            final CaptureRequest.Builder captureBuilder = cameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_STILL_CAPTURE);
            captureBuilder.addTarget(imageReader.getSurface());

            // Use the same AE and AF modes as the preview.
            captureBuilder.set(CaptureRequest.CONTROL_AF_MODE, CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE);

            // Orientation
            int rotation = cameraView.getDisplay().getRotation();
            captureBuilder.set(CaptureRequest.JPEG_ORIENTATION, ORIENTATIONS.get(rotation));

            final CameraCaptureSession.CaptureCallback CaptureCallback = new CameraCaptureSession.CaptureCallback() {
                @Override
                public void onCaptureCompleted(@NonNull CameraCaptureSession session, @NonNull CaptureRequest request, @NonNull TotalCaptureResult result) {
                    try {
                        // Reset the auto-focus trigger
                        captureRequestBuilder.set(CaptureRequest.CONTROL_AF_TRIGGER, CameraMetadata.CONTROL_AF_TRIGGER_CANCEL);
                        cameraCaptureSession.capture(captureRequestBuilder.build(), captureCallback, backgroundHandler);
                        // After this, the camera will go back to the normal state of preview.
                        mState = STATE_PREVIEW;
                        cameraCaptureSession.setRepeatingRequest(captureRequestBuilder.build(), captureCallback, backgroundHandler);

                        cameraDevice.close();
                        openCamera();
                    } catch (CameraAccessException e) {
                        e.printStackTrace();
                    }
                }
            };

            List<Surface> surfaces = new ArrayList<>(2);
            surfaces.add(imageReader.getSurface());
            surfaces.add(new Surface(cameraView.getSurfaceTexture()));
            cameraDevice.createCaptureSession(surfaces, new CameraCaptureSession.StateCallback() {
                @Override
                public void onConfigured(@NonNull CameraCaptureSession captureSession) {
                    cameraCaptureSession = captureSession;
                    try {
                        cameraCaptureSession.stopRepeating();
                        cameraCaptureSession.capture(captureBuilder.build(), CaptureCallback, null);
                    } catch (CameraAccessException e) {
                        e.printStackTrace();
                    }
                }

                @Override
                public void onConfigureFailed(@NonNull CameraCaptureSession captureSession) {

                }
            }, backgroundHandler);
        } catch (CameraAccessException e) {
            e.printStackTrace();
        }
    }

    private void save(byte[] bytes) throws IOException {
        OutputStream outputStream = new FileOutputStream(imageDirectory);
        outputStream.write(bytes);
        outputStream.close();
    }

    private void lockFocus() {
        try {
            mState = STATE_WAITING_LOCK;
            captureRequestBuilder.set(CaptureRequest.CONTROL_AF_TRIGGER, CaptureRequest.CONTROL_AF_TRIGGER_START);
            captureRequestBuilder.set(CaptureRequest.JPEG_QUALITY, (byte) 100);

            cameraCaptureSession.capture(captureRequestBuilder.build(), captureCallback, backgroundHandler);
        } catch (CameraAccessException e) {
            e.printStackTrace();
        }
    }

    private void unlockFocus() {
        try {
            mState = STATE_PREVIEW;
            captureRequestBuilder.set(CaptureRequest.CONTROL_AF_TRIGGER, CaptureRequest.CONTROL_AF_TRIGGER_CANCEL);

            cameraCaptureSession.capture(captureRequestBuilder.build(), captureCallback, backgroundHandler);
        } catch (CameraAccessException e) {
            e.printStackTrace();
        }
    }

    public void snap() {
        try {
            // This is how to tell the camera to lock focus.
            captureRequestBuilder.set(CaptureRequest.CONTROL_AF_TRIGGER, CameraMetadata.CONTROL_AF_TRIGGER_START);
            // Tell captureCallback to wait for the lock.
            mState = STATE_WAITING_LOCK;
            cameraCaptureSession.capture(captureRequestBuilder.build(), captureCallback, backgroundHandler);
        } catch (CameraAccessException e) {
            e.printStackTrace();
        }
    }

    public void takePicture() throws FileNotFoundException {
        final Bitmap bmp = cameraView.getBitmap();

        if (folder != null) {
            Long ts = System.currentTimeMillis() / 1000;
            String timeStamp = ts.toString();
            imageDirectory = new File(folder, timeStamp + ".jpg");
        }

        if (imageDirectory != null) {
            final FileOutputStream outputStream = new FileOutputStream(imageDirectory);
            new Handler().post(new Runnable() {
                @Override
                public void run() {
                    bmp.compress(Bitmap.CompressFormat.PNG, 100, outputStream);
                }
            });
        }
    }

    public void useFrontCamera() throws CameraAccessException {
        cameraFacing = CameraCharacteristics.LENS_FACING_FRONT;
        init();
    }

    public void useRearCamera() throws CameraAccessException {
        cameraFacing = CameraCharacteristics.LENS_FACING_BACK;
        init();
    }

    public void setFolderName(String folderName) {
        this.folderName = folderName;
        init();
    }



    //LifeCycle
    @Override
    protected void onVisibilityChanged(View changedView, int visibility) {
        super.onVisibilityChanged(changedView, visibility);
        if (visibility == View.VISIBLE) {
            //onResume
            openBackgroundThread();
            if (cameraView.isAvailable()) {
                setUpCamera();
                openCamera();
            } else {
                cameraView.setSurfaceTextureListener(surfaceTextureListener);
            }
        } else {
            // onPause
            cameraDevice.close();
            try {
                stopBackgroundThread();
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        //Create
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        //Destroy
    }
}
