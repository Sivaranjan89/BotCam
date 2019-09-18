# BotCam
Create your own custom camera with Botcam. Botcam uses camera2 api
Available Functions,
1) Rear camera capture
2) Front camera capture
3) Flash support
4) Save the resulting image in raw byte[] or bitmap

# How to Install Plugin
Add the below in your root build.gradle(project) at the end of repositories:<br />

~~~
allprojects {
		repositories {
			...
			maven { url 'https://jitpack.io' }
		}
	}
~~~
<br />
Add the dependency in build.gradle(module) : <br />
~~~
dependencies {
	        implementation 'com.github.Sivaranjan89:botcam:1.0'
	}
~~~

# Invoke Botcam Fragment (Make sure you have the required permissions before invoking this)
~~~

getSupportFragmentManager().beginTransaction()
                    .replace(R.id.cameraPreview, botCamera)
                    .commit();
                    
~~~

# Configure Botcam
~~~

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

~~~

# Other Functions
<ol>
<li>takePicture() -> Clicks the Picture</li>
<li>useFrontCamera() -> Uses the front camera</li>
<li>useRearCamera() -> Uses the Back camera</li>
<li>setFlashEnabled() -> Decide whether to use flash or not</li>
<li>setZoomEnabled() -> Decide whether to use zoom functionality</li>
<li>restartCamera() -> Restarts the camera</li>
<li>convertRawImageToBitmap(byte[] image) -> Use this method to transform your raw image to Bitmap</li>
<li>saveImageToDirectory(folderName, image, imageName) -> if folderName and imagename are given null, it will save with a default name under "BotCam/Images" directory. Image can be bitmap or byte[]</li>
<li>restartCamera() -> Restarts the camera</li>
</ol>
