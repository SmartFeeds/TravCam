package com.github.travcam;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.Application;
import android.content.Context;
import android.graphics.ImageFormat;
import android.graphics.Matrix;
import android.graphics.Point;
import android.graphics.RectF;
import android.graphics.SurfaceTexture;
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraCaptureSession;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraDevice;
import android.hardware.camera2.CameraManager;
import android.hardware.camera2.CameraMetadata;
import android.hardware.camera2.CaptureFailure;
import android.hardware.camera2.CaptureRequest;
import android.hardware.camera2.TotalCaptureResult;
import android.hardware.camera2.params.StreamConfigurationMap;
import android.media.CamcorderProfile;
import android.media.ImageReader;
import android.media.MediaPlayer;
import android.media.MediaRecorder;
import android.os.CountDownTimer;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Looper;
import android.util.Log;
import android.util.Size;
import android.util.SparseIntArray;
import android.view.OrientationEventListener;
import android.view.Surface;
import android.view.TextureView;
import android.widget.Toast;
import androidx.annotation.NonNull;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

/**
 * Using android.hardware.camera2 API
 * Handles all camera capturing, threading, sessions and media rendering needs
 * Only requires a SurfaceView View to be used to launch and attach camera on
 *
 *
 * To open camera on a Textureview call the main method {@link #init(TextureView, Context, CameraHandlerListener)}
 * This will attach a callbacks {@link android.view.TextureView.SurfaceTextureListener}
 * for {@link TextureView}
 * Once Textureview is ready, background threads will start and Camera will be initialized
 *
 *
 * For threading we're using 2 thread handlers ({@link #mMainHandler} && {@link #mChildHandler})
 * with 1 thread pool {@link #mBackgroundThread}
 */
@SuppressWarnings("FieldCanBeLocal")
public class TravCam {
    private final static String TAG = "CameraHandlerLogs";

    /** Host Context **/
    @SuppressLint("StaticFieldLeak")
    private static Context context;

    /** Actual hardware camera device **/
    private static CameraDevice mCamera;

    /** Holding TextureView which used to preview Camera **/
    @SuppressLint("StaticFieldLeak")
    private static TextureView mTextureView;

    /** Holds characteristics for the assigned CameraID camera **/
    private static CameraCharacteristics mCameraCharacteristics;

    /** Tracking Camera state **/
    private static CameraState mCameraState = CameraState.STATE_PREVIEW;

    /**
     * Two different threads assigned and used in a ThreadPool
     * mChildHandler used for rendering Captured medias
     * mMainHandler used for holding the current Camera session
     * THREAD_POOL_NAME is the name of the used ThreadPool
     * **/
    private static HandlerThread mBackgroundThread;
    private static Handler mChildHandler, mMainHandler;
    private final static String THREAD_POOL_NAME = "CameraHandler";

    /** Current CameraID received from {@link CameraCharacteristics} **/
    private static String mCameraID;

    /** Used for retrieving Captured images **/
    private static ImageReader mImageReader;

    /**
     * To create and configure the current capturing
     * Used for default camera preview session
     * and Capturing image session
     * **/
    private static CaptureRequest.Builder mPreviewBuilder;

    /**
     * To create and configure video recordings
     * Used aside with {@link #mVideoRecordingSession} and {@link #mMediaRecorder}
     */
    private static CaptureRequest.Builder mVideoRecordingBuilder;

    /**
     * Camera capturing session
     * Initialized on default camera preview session and
     * on Image capturing session
     * **/
    private static CameraCaptureSession mPreviewSession;

    /**
     * Video camera capturing session
     * Initialized once, on video capturing starts
     * Used separated {@link CameraCaptureSession} for videos to set custom targets-
     * and avoid camera preview session from freezing when capturing videos
     */
    private static CameraCaptureSession mVideoRecordingSession;

    /** Camera capture sound **/
    private static MediaPlayer mCaptureSound;

    /** Sensor Orientations **/
    private static Integer mSensorOrientation, mImageOrientation;
    private final static int SENSOR_ORIENTATION_DEFAULT_DEGREES = 90;
    private final static int SENSOR_ORIENTATION_INVERSE_DEGREES = 270;

    /** Defining Default orientations **/
    private final static SparseIntArray DEFAULT_ORIENTATIONS = new SparseIntArray();
    static {
        DEFAULT_ORIENTATIONS.append(Surface.ROTATION_0, 90);
        DEFAULT_ORIENTATIONS.append(Surface.ROTATION_90, 0);
        DEFAULT_ORIENTATIONS.append(Surface.ROTATION_180, 270);
        DEFAULT_ORIENTATIONS.append(Surface.ROTATION_270, 180);
    }

    /** Defining Inverse orientations **/
    private final static SparseIntArray INVERSE_ORIENTATIONS = new SparseIntArray();
    static {
        INVERSE_ORIENTATIONS.append(Surface.ROTATION_0, 270);
        INVERSE_ORIENTATIONS.append(Surface.ROTATION_90, 180);
        INVERSE_ORIENTATIONS.append(Surface.ROTATION_180, 90);
        INVERSE_ORIENTATIONS.append(Surface.ROTATION_270, 0);
    }

    /** To listen for sensor orientation changes **/
    private static OrientationEventListener mOrientationEventListener;

    /** Main CameraHandler class callbacks **/
    private static CameraHandlerListener mCameraHandlerListener;

    /**
     * Custom class {@link TravManager}
     * Used to save captured media files to ExternalStorage
     * **/
    @SuppressLint("StaticFieldLeak")
    private static TravManager mFileManager;

    /** Sizes for previewing and video recording **/
    private static Size mPreviewSize, mVideoSize;

    /**
     * Max preview width that is guaranteed by Camera2 API
     */
    private final static int MAX_PREVIEW_WIDTH = 1920;

    /**
     * Max preview height that is guaranteed by Camera2 API
     */
    private final static int MAX_PREVIEW_HEIGHT = 1080;

    /** Using MediaRecorder for recording videos **/
    private static MediaRecorder mMediaRecorder;

    /** Created file to be used for writing captured video data **/
    private static File mLastCapturedVideoFile;

    /** Created file to be used for writing captured image data **/
    private static File mLastCapturedImageFile;

    /** Video recording max length limitation **/
    private final static int MAX_VIDEO_RECORDING_TIME = 30000; // 30 SECONDS

    /**
     * Using CountDownTimer to run for {@link #MAX_VIDEO_RECORDING_TIME}
     * This will detect the currently recording video length, and automatically
     * stop video recording when reaches max length limits
     * **/
    private static CountDownTimer mVideoRecordingTimer;



    // ===========================================================================================
    // =============================== Main Camera Configurations ================================
    // ===========================================================================================
    /**
     * Main initializer method
     * @param textureView  main surface for previewing camera
     * @param ctx          must be Activity context
     * @param listener     to inform host with callbacks
     */
    public static void init(@NonNull TextureView textureView, @NonNull Context ctx, @NonNull CameraHandlerListener listener){
        // Init Context
        context = ctx;

        // Texture view
        mTextureView = textureView;

        // Init Callbacks listener
        mCameraHandlerListener = listener;

        // Init FileManager
        mFileManager = new TravManager(context);

        // Orientation listener
        mOrientationEventListener = new OrientationEventListener(ctx) {
            @Override
            public void onOrientationChanged(int orientation) {
                if (orientation >= 315 || orientation < 45){
                    mImageOrientation = Surface.ROTATION_90;
                }else if (orientation < 135){
                    mImageOrientation = Surface.ROTATION_180;
                }else if (orientation < 225){
                    mImageOrientation = Surface.ROTATION_270;
                }else {
                    mImageOrientation = Surface.ROTATION_0;
                }
            }
        };

        // Enable orientation listener
        if(mOrientationEventListener.canDetectOrientation()) mOrientationEventListener.enable();

        // Callbacks from TextureView are the main initializers for
        // starting background threads and opening camera
        // This is safer to make sure that camera will run on valid surface
        mTextureView.setSurfaceTextureListener(new TextureView.SurfaceTextureListener() {
            @Override
            public void onSurfaceTextureAvailable(@NonNull SurfaceTexture surface, int width, int height) {
                startBackgroundThread();

                try{
                    mPreviewSize = new Size(width, height);
                    configureTransform(width, height);
                    initCamera(width, height);
                }catch (CameraAccessException e){
                    Log.d(TAG, "openCamera exception: "+e.getMessage());
                }
            }

            @Override
            public void onSurfaceTextureSizeChanged(@NonNull SurfaceTexture surface, int width, int height) {
                configureTransform(width, height);
            }

            @Override
            public boolean onSurfaceTextureDestroyed(@NonNull SurfaceTexture surface) {
                closeCamera();
                return true;
            }

            @Override
            public void onSurfaceTextureUpdated(@NonNull SurfaceTexture surface) {

            }
        });
    }

    /**
     * This method requests to open the hardware camera, and won't show an actual camera preview on screen
     * Using {@link CameraManager} to request openCamera
     * Could throw CameraAccessException when calling openCamera method
     * Contains callbacks from {@link CameraDevice.StateCallback}
     */
    @SuppressLint("MissingPermission")
    private static void initCamera(int width, int height) throws CameraAccessException{
        final CameraManager mCameraManager = (CameraManager) context.getSystemService(Context.CAMERA_SERVICE);

        // Getting cameraID
        mCameraID = mCameraManager.getCameraIdList()[0];

        // Configure Sizes and rotations
        configureSizesAndRotations(mCameraManager, width, height);

        // Init Media Recorder
        mMediaRecorder = new MediaRecorder();

        // Open Camera
        mCameraManager.openCamera(mCameraID, new CameraDevice.StateCallback() {
            @Override
            public void onOpened(@NonNull CameraDevice camera) {
                mCamera = camera;

                try {
                    startCameraPreview();
                } catch (CameraAccessException e) {
                    e.printStackTrace();
                }
            }

            @Override
            public void onDisconnected(@NonNull CameraDevice camera) {
                closeCamera();
            }

            @Override
            public void onError(@NonNull CameraDevice camera, int error) {
                closeCamera();
                mCameraHandlerListener.onCloseCameraError();
            }
        }, mMainHandler);
    }

    /**
     * Request close for all running services
     * Stops current background thread
     */
    public static void closeCamera(){
        if(null != mPreviewSession){
            mPreviewSession.close();
            mPreviewSession = null;
        }

        if(null != mCamera){
            mCamera.close();
            mCamera = null;
        }

        if(null != mImageReader){
            mImageReader.close();
            mImageReader = null;
        }

        stopBackgroundThread();
    }

    /**
     * Must be called on host's lifecycle onPause method
     * Pauses current threads
     */
    public static void onPause(){
        stopBackgroundThread();
    }

    /**
     * Must be called on host's lifecycle onResume method
     * Resume previous sessions
     */
    public static void onResume(){
        try {
            if(mCamera != null && mTextureView.isAvailable())
                initCamera(mTextureView.getWidth(), mTextureView.getHeight());
        } catch (CameraAccessException e) {
            e.printStackTrace();
        }
    }

    /**
     * Starts actual camera preview on screen.
     * {@link #initCamera(int, int)} needs to be called in advance
     */
    private static void startCameraPreview() throws CameraAccessException{
        if(mCamera == null || !mTextureView.isAvailable()) throw new RuntimeException("Can't preview camera on a non-valid CameraDevice!");

        // Close old preview session
        closeCameraPreviewSession();

        // Create capture request
        mPreviewBuilder = mCamera.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW);

        // Setting buffer size for current texture
        SurfaceTexture texture = mTextureView.getSurfaceTexture();
        assert texture != null;
        texture.setDefaultBufferSize(mPreviewSize.getWidth(), mPreviewSize.getHeight());

        // Add surface
        Surface previewSurface = new Surface(texture);
        mPreviewBuilder.addTarget(previewSurface);

        // Create capture session
        mCamera.createCaptureSession(Collections.singletonList(previewSurface), new CameraCaptureSession.StateCallback() {
            @Override
            public void onConfigured(@NonNull CameraCaptureSession session) {
                mPreviewSession = session;
                updateCameraPreview();
            }

            @Override
            public void onConfigureFailed(@NonNull CameraCaptureSession session) {

            }
        }, mMainHandler);
    }

    /**
     * Update the camera preview. {@link #startCameraPreview()} needs to be called in advance.
     */
    private static void updateCameraPreview(){
        if(mCamera == null) return;

        try{
            // Setup capture request builder
            mPreviewBuilder.set(CaptureRequest.CONTROL_MODE, CameraMetadata.CONTROL_MODE_AUTO);

            // Turn on Auto-focus if supported
            if(isAutoFocusSupported()){
                mPreviewBuilder.set(CaptureRequest.CONTROL_AF_MODE, CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE);
            }else{
                mPreviewBuilder.set(CaptureRequest.CONTROL_AF_MODE, CaptureRequest.CONTROL_AF_MODE_OFF);
            }

            // Restarting background threads
            stopBackgroundThread();
            startBackgroundThread();

            // Start preview session
            mPreviewSession.setRepeatingRequest(mPreviewBuilder.build(), null, mMainHandler);
        }catch (CameraAccessException e){
            e.printStackTrace();
        }
    }

    /**
     * Closes camera preview session
     */
    private static void closeCameraPreviewSession(){
        if(mPreviewSession != null){
            mPreviewSession.close();
            mPreviewSession = null;
        }
    }

    /**
     * There's a problem that many front-facing cameras have a fixed focus distance.
     * So after the autofocus trigger in lockFocus() the autofocus state (CONTROL_AF_STATE)
     * remains INACTIVE and the autofocus trigger does nothing.
     * So in order to make it work you need to check whether autofocus is supported or not.
     * In here we're checking if there's no actual auto-focus mode
     * or if there's an available auto-focus mode but it is off
     */
    private static boolean isAutoFocusSupported(){
        int[] afAvailableModes = mCameraCharacteristics.get(CameraCharacteristics.CONTROL_AE_AVAILABLE_MODES);
        return !(
                afAvailableModes.length == 0
                        ||
                        (afAvailableModes.length == 1 && afAvailableModes[0] == CameraMetadata.CONTROL_AF_MODE_OFF)
        );
    }

    /**
     * If AUTO_FOCUS is not supported then we need to manually lock focus using lockFocus() method
     * after locking focus and capturing image, unlockFocus() method must be called to free the
     * focus
     */
    private static void unlockFocus(){
        try{
            // Inform Camera to UnLock Focus
            mPreviewBuilder.set(CaptureRequest.CONTROL_AF_TRIGGER, CameraMetadata.CONTROL_AF_TRIGGER_CANCEL);

            // Set repeating request
            mPreviewSession.setRepeatingRequest(mPreviewBuilder.build(), mCameraCaptureSessionCaptureCallbacks, mChildHandler);
            mCameraState = CameraState.STATE_FOCUS_UNLOCKED;
        }catch (CameraAccessException e){
            Log.d(TAG, "unlockFocus() Exception: "+e.getMessage());
        }
    }

    /**
     * In this sample, we choose a video size with 3x4 aspect ratio. Also, we don't use sizes
     * larger than 1080p, since MediaRecorder cannot handle such a high-resolution video.
     *
     * @param choices The list of available sizes
     * @return        The video size
     */
    private static Size chooseVideoSize(Size[] choices) {
        for (Size size : choices) {
            if (size.getWidth() == size.getHeight() * 4 / 3 && size.getWidth() <= 1080) {
                return size;
            }
        }
        Log.e(TAG, "Couldn't find any suitable video size");
        return choices[choices.length - 1];
    }

    /**
     * Given {@code choices} of {@code Size}s supported by a camera, choose the smallest one that
     * is at least as large as the respective texture view size, and that is at most as large as the
     * respective max size, and whose aspect ratio matches with the specified value. If such size
     * doesn't exist, choose the largest one that is at most as large as the respective max size,
     * and whose aspect ratio matches with the specified value.
     *
     * @param choices           The list of sizes that the camera supports for the intended output
     *                          class
     * @param textureViewWidth  The width of the texture view relative to sensor coordinate
     * @param textureViewHeight The height of the texture view relative to sensor coordinate
     * @param maxWidth          The maximum width that can be chosen
     * @param maxHeight         The maximum height that can be chosen
     * @param aspectRatio       The aspect ratio
     * @return The optimal {@code Size}, or an arbitrary one if none were big enough
     */
    private static Size chooseOptimalSize(Size[] choices, int textureViewWidth, int textureViewHeight, int maxWidth, int maxHeight, Size aspectRatio) {
        // Collect the supported resolutions that are at least as big as the preview Surface
        List<Size> bigEnough = new ArrayList<>();
        // Collect the supported resolutions that are smaller than the preview Surface
        List<Size> notBigEnough = new ArrayList<>();
        int w = aspectRatio.getWidth();
        int h = aspectRatio.getHeight();
        for (Size option : choices) {
            if (option.getWidth() <= maxWidth && option.getHeight() <= maxHeight &&
                    option.getHeight() == option.getWidth() * h / w) {
                if (option.getWidth() >= textureViewWidth &&
                        option.getHeight() >= textureViewHeight) {
                    bigEnough.add(option);
                } else {
                    notBigEnough.add(option);
                }
            }
        }

        // Pick the smallest of those big enough. If there is no one big enough, pick the
        // largest of those not big enough.
        if (bigEnough.size() > 0) {
            return Collections.min(bigEnough, new CompareSizesByArea());
        } else if (notBigEnough.size() > 0) {
            return Collections.max(notBigEnough, new CompareSizesByArea());
        } else {
            Log.e(TAG, "Couldn't find any suitable preview size");
            return choices[0];
        }
    }

    /**
     * Configures the necessary {@link android.graphics.Matrix} transformation to `mTextureView`.
     * This method should not to be called until the camera preview size is determined in
     * openCamera, or until the size of `mTextureView` is fixed.
     *
     * @param viewWidth  The width of `mTextureView`
     * @param viewHeight The height of `mTextureView`
     */
    private static void configureTransform(int viewWidth, int viewHeight) {
        Activity activity = (Activity) context;
        if (null == mTextureView || null == mPreviewSize || null == activity) {
            Log.d(TAG, "configureTransform: closing...");
            return;
        }
        Log.d(TAG, "configureTransform: adding...");
        int rotation = activity.getWindowManager().getDefaultDisplay().getRotation();
        Matrix matrix = new Matrix();
        RectF viewRect = new RectF(0, 0, viewWidth, viewHeight);
        RectF bufferRect = new RectF(0, 0, mPreviewSize.getHeight(), mPreviewSize.getWidth());
        float centerX = viewRect.centerX();
        float centerY = viewRect.centerY();
        if (Surface.ROTATION_90 == rotation || Surface.ROTATION_270 == rotation) {
            bufferRect.offset(centerX - bufferRect.centerX(), centerY - bufferRect.centerY());
            matrix.setRectToRect(viewRect, bufferRect, Matrix.ScaleToFit.FILL);
            float scale = Math.max(
                    (float) viewHeight / mPreviewSize.getHeight(),
                    (float) viewWidth / mPreviewSize.getWidth());
            matrix.postScale(scale, scale, centerX, centerY);
            matrix.postRotate(90 * (rotation - 2), centerX, centerY);
        }
        mTextureView.setTransform(matrix);
    }

    /**
     * Using {@link CameraCharacteristics} and {@link StreamConfigurationMap} to receive output
     * configurations. Sets width and height for {@link #mImageReader}.
     * Compares sensor rotation with display view rotation to decide to swap dimensions.
     *
     * @param mCameraManager         To init {@link #mCameraCharacteristics}
     * @param width                  TextureView width
     * @param height                 TextureView height
     * @throws CameraAccessException When accessing {@link CameraCharacteristics}
     */
    private static void configureSizesAndRotations(CameraManager mCameraManager, int width, int height) throws CameraAccessException{
        // Choose the sizes for camera preview and video recording
        mCameraCharacteristics = mCameraManager.getCameraCharacteristics(mCameraID);
        StreamConfigurationMap configMap = mCameraCharacteristics.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP);

        if(configMap == null) throw new RuntimeException("Cannot get available preview/video size!");

        // For still capture images we use the largest available size
        Size largest = Collections.max(Arrays.asList(configMap.getOutputSizes(ImageFormat.JPEG)), new CompareSizesByArea());

        // Find out if we need to swap dimension to get the preview size relative to sensor coordinate.
        int rotation = ((Activity)context).getWindowManager().getDefaultDisplay().getRotation();
        mSensorOrientation = mCameraCharacteristics.get(CameraCharacteristics.SENSOR_ORIENTATION);
        boolean swappedDimensions = false;
        switch (rotation){
            case Surface.ROTATION_0:
            case Surface.ROTATION_180:
                if(mSensorOrientation == 90 || mSensorOrientation == 270) swappedDimensions = true;
                break;

            case Surface.ROTATION_90:
            case Surface.ROTATION_270:
                if(mSensorOrientation == 0 || mSensorOrientation == 180) swappedDimensions = true;
                break;
        }

        Point displaySize = new Point();
        ((Activity) context).getWindowManager().getDefaultDisplay().getSize(displaySize);
        int rotatedPreviewWidth = swappedDimensions ? height : width;
        int rotatedPreviewHeight = swappedDimensions ? width : height;
        int maxPreviewWidth = swappedDimensions ? displaySize.y : displaySize.x;
        int maxPreviewHeight = swappedDimensions ? displaySize.x : displaySize.y;

        if(maxPreviewWidth > MAX_PREVIEW_WIDTH) maxPreviewWidth = MAX_PREVIEW_WIDTH;
        if(maxPreviewHeight > MAX_PREVIEW_HEIGHT) maxPreviewHeight = MAX_PREVIEW_HEIGHT;

        mVideoSize = chooseVideoSize(configMap.getOutputSizes(MediaRecorder.class));
        mPreviewSize = chooseOptimalSize(configMap.getOutputSizes(SurfaceTexture.class), rotatedPreviewWidth, rotatedPreviewHeight, maxPreviewWidth, maxPreviewHeight, largest);
    }

    /**
     * Used to revert rotated captured images to portrait mode
     * Used by {@link TravManager}
     * @param orientation  Current orientation
     * @return             Rotation to apply to return to portrait
     */
    public static int revertOrientationToPortrait(int orientation){
        switch (orientation){
            case 0: return 90;
            case 90: return 0;
            case 180: return 270;
            case 270:
            default:
                return 180;
        }
    }

    /**
     * @return Device sensor orientation
     */
    public static int getSensorOrientation(){
        return DEFAULT_ORIENTATIONS.get(mImageOrientation);
    }





    // ===========================================================================================
    // =================================== Managing Threads ======================================
    // ===========================================================================================
    /** Starts background thread **/
    private static void startBackgroundThread(){
        mBackgroundThread = new HandlerThread(THREAD_POOL_NAME);
        mBackgroundThread.start();
        mChildHandler = new Handler(mBackgroundThread.getLooper());
        mMainHandler = new Handler(Looper.getMainLooper());
    }

    /** Stops background thread **/
    private static void stopBackgroundThread(){
        if(mBackgroundThread != null){
            mBackgroundThread.quitSafely();
            try{
                mBackgroundThread.join();
                mBackgroundThread = null;
                mChildHandler = null;
                mMainHandler = null;
            }catch (InterruptedException e){
                e.printStackTrace();
            }
        }
    }






    // ===========================================================================================
    // =================================== Capturing Images ======================================
    // ===========================================================================================
    /**
     * Called on mSurfaceHolderCallbacks on the surfaceCreated method
     * Used to initialized ImageReader
     */
    private static void initImageReader(){
        // Init image reader width largest available size
        mImageReader = ImageReader.newInstance(mPreviewSize.getWidth(), mPreviewSize.getHeight(), ImageFormat.JPEG, 1);

        // Adding callbacks to ImageReader
        mImageReader.setOnImageAvailableListener(reader -> {
            // Change camera state
            mCameraState = CameraState.STATE_IMAGE_CAPTURED;

            // This callback returns an image when CameraCaptureSession completes capture.
            // Call to save captured image
            mFileManager.listenForCallbacks(new TravManager.ScopedFileManagerCallbacks() {
                @Override
                public void onFileCreated(File file) {
                    // Store captured file
                    mLastCapturedImageFile = file;

                    // Inform listener
                    mCameraHandlerListener.onImageCapture(file);
                }

                @Override
                public void onError(String message) {
                    Log.d(TAG, "FileManager Error: "+message);
                }
            }).storeCapturedImage(reader.acquireNextImage(), TravManager.ScopedFileType.IMAGE);
        }, mMainHandler);
    }

    /**
     * Requests camera image capture
     * Checks if AuthFocus is supported on device, otherwise will manually lock_focus
     */
    public static void captureImage(){
        if(mCamera == null || !mTextureView.isAvailable() || mPreviewSize == null) return;

        // Close previous session
        closeCameraPreviewSession();
        try{
            initImageReader();

            mCameraState = CameraState.STATE_CAPTURING_IMAGE;

            // Updating CaptureRequest builder
            mPreviewBuilder = mCamera.createCaptureRequest(CameraDevice.TEMPLATE_STILL_CAPTURE);

            // Turn on flash
            mPreviewBuilder.set(CaptureRequest.CONTROL_AE_MODE, CaptureRequest.CONTROL_AE_MODE_ON_AUTO_FLASH);

            // Orientation
            final int rotation = ((Activity) context).getWindowManager().getDefaultDisplay().getRotation();
            mPreviewBuilder.set(CaptureRequest.JPEG_ORIENTATION, rotation);

            // Adding targeted surfaces
            List<Surface> surfaces = new ArrayList<>();
            surfaces.add(mImageReader.getSurface());

            // Updating target of captured request
            mPreviewBuilder.addTarget(mImageReader.getSurface());

            // Start capture session
            mCamera.createCaptureSession(surfaces, new CameraCaptureSession.StateCallback() {
                @Override
                public void onConfigured(@NonNull CameraCaptureSession session) {
                    mPreviewSession = session;

                    // Checking if device supports AutoFocus
                    // Otherwise focus will be manually locked then unlocked after capturing
                    try{
                        if(isAutoFocusSupported()){
                            mPreviewSession.capture(mPreviewBuilder.build(),
                                    mCameraCaptureSessionCaptureCallbacks,
                                    mChildHandler);
                        }else{
                            lockFocusAndCapture();
                        }
                    }catch (CameraAccessException e){
                        e.printStackTrace();
                    }
                }

                @Override
                public void onConfigureFailed(@NonNull CameraCaptureSession session) {

                }
            }, mChildHandler);
        }catch (CameraAccessException e){
            e.printStackTrace();
        }
    }

    /**
     * Assign media raw file path
     * Image capture sound
     * @param rawFilePath  Resources raw file
     */
    public static void assignImageCaptureSound(int rawFilePath){
        mCaptureSound = MediaPlayer.create(context, rawFilePath);
    }

    /**
     * If AUTO_FOCUS is not supported then we need to manually lock focus before capturing
     * This will require to trigger unlockFocus() method too after done with capturing
     */
    private static void lockFocusAndCapture(){
        try{
            // Update camera state
            mCameraState = CameraState.STATE_FOCUS_LOCKED;

            // Inform Camera to Lock Focus
            mPreviewBuilder.set(CaptureRequest.CONTROL_AF_TRIGGER, CameraMetadata.CONTROL_AF_TRIGGER_START);

            // Capture
            mPreviewSession.capture(mPreviewBuilder.build(), mCameraCaptureSessionCaptureCallbacks, mChildHandler);

            // Unlock focus
            unlockFocus();
        }catch (CameraAccessException e){
            Log.d(TAG, "lockFocusAndCapture() Exception: "+e.getMessage());
        }
    }

    /**
     * Called from host to delete last captured image file if no longer used
     */
    public static void dismissCapturedImageFile(){
        if(mLastCapturedImageFile != null){
            if(mFileManager.deleteFile(mLastCapturedImageFile)){
                mLastCapturedImageFile = null;
            }
        }
    }







    // ===========================================================================================
    // =================================== Capturing Videos ======================================
    // ===========================================================================================
    /**
     * Changes {@link #mCameraState}
     * Initializes {@link #mVideoRecordingBuilder} for camera video captures
     * Links to {@link #mVideoRecordingSession} for custom camera session
     * Starts {@link #mMediaRecorder} to start video recording
     */
    public static void startVideoRecording(){
        if(mCamera == null || !mTextureView.isAvailable() || mPreviewSize == null) return;

        try {
            // Check if ready to record video
            if(mMediaRecorder == null || mLastCapturedVideoFile == null) return;

            // Change camera state
            mCameraState = CameraState.STATE_RECORDING_VIDEO;

            // Changing template
            mVideoRecordingBuilder = mCamera.createCaptureRequest(CameraDevice.TEMPLATE_RECORD);

            // Adding targeted surfaces
            List<Surface> surfaces = new ArrayList<>();

            // Setup surface for MediaRecorder
            surfaces.add(mMediaRecorder.getSurface());
            mVideoRecordingBuilder.addTarget(mMediaRecorder.getSurface());

            surfaces.add(new Surface(mTextureView.getSurfaceTexture()));
            mVideoRecordingBuilder.addTarget(new Surface(mTextureView.getSurfaceTexture()));

            // Start capture session
            mCamera.createCaptureSession(surfaces, new CameraCaptureSession.StateCallback() {
                @Override
                public void onConfigured(@NonNull CameraCaptureSession session) {
                    mVideoRecordingSession = session;

                    try{
                        // Start preview session
                        mVideoRecordingSession.setRepeatingRequest(mVideoRecordingBuilder.build(), mCameraCaptureSessionCaptureCallbacks, mChildHandler);

                        mVideoRecordingSession.capture(mVideoRecordingBuilder.build(),
                                mCameraCaptureSessionCaptureCallbacks,
                                mChildHandler);

                        // Start recording
                        mMediaRecorder.start();

                        /*
                         ** Start a CountDownTimer to detect video recording length
                         ** Since CountDownTimer runs on background thread, and it'll touch UI thread
                         ** when finish, we need to let it run on UI thread
                         */
                        ((Activity)context).runOnUiThread(() -> {
                            // First cancel previous CountDownTimer if exists
                            if(mVideoRecordingTimer != null) mVideoRecordingTimer.cancel();

                            // Start new timer
                            mVideoRecordingTimer = new CountDownTimer(MAX_VIDEO_RECORDING_TIME, 1000) {
                                @Override
                                public void onTick(long millisUntilFinished) {
                                    mCameraHandlerListener.onVideoRecordingLengthTicks(MAX_VIDEO_RECORDING_TIME, MAX_VIDEO_RECORDING_TIME - millisUntilFinished);
                                }

                                @Override
                                public void onFinish() {
                                    // Stop video recording when it reaches the max length limits
                                    if(mCameraState == CameraState.STATE_RECORDING_VIDEO) stopVideoRecording();
                                }
                            }.start();
                        });
                    }catch (CameraAccessException e){
                        e.printStackTrace();
                        Toast.makeText(context, "Exception: "+e.getMessage(), Toast.LENGTH_SHORT).show();
                    }
                }

                @Override
                public void onConfigureFailed(@NonNull CameraCaptureSession session) {

                }
            }, mChildHandler);
        } catch (CameraAccessException e) {
            e.printStackTrace();
            Toast.makeText(context, "Exception: "+e.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }

    /**
     * Changes {@link #mCameraState}
     * Stops and resets {@link #mMediaRecorder}
     * Inform host with callbacks using {@link #mCameraHandlerListener}
     * Restarts camera preview session {@link #startCameraPreview()}
     */
    public static void stopVideoRecording(){
        // Update camera state
        mCameraState = CameraState.STATE_VIDEO_RECORDED;

        // Stop MediaRecorder
        mMediaRecorder.stop();
        mMediaRecorder.reset();

        // Stop video recording timer
        if(mVideoRecordingTimer != null) mVideoRecordingTimer.cancel();

        // Host callback
        mCameraHandlerListener.onVideoRecordingEnds(mLastCapturedVideoFile);

        // Start default preview
        try {
            mVideoRecordingSession.stopRepeating();
            startCameraPreview();
        } catch (CameraAccessException e) {
            e.printStackTrace();
        }
    }

    /**
     * State checker for host to ensure that video recording is currently running
     * @return value will be based on the current {@link #mCameraState} value
     */
    public static boolean isVideoRecordingRunning(){
        return mCameraState == CameraState.STATE_RECORDING_VIDEO;
    }

    /**
     * Applies all needed configurations for MediaRecorder
     * Creates a temp video file to be used for storing captured video data
     */
    @SuppressLint("NewApi")
    public static void setUpMediaRecorder() {
        final Activity activity = (Activity) context;
        if(activity == null) return;

        // MediaRecorder sources
        mMediaRecorder.setAudioSource(MediaRecorder.AudioSource.MIC);
        mMediaRecorder.setVideoSource(MediaRecorder.VideoSource.SURFACE);
        mMediaRecorder.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4);

        // Get video file
        mFileManager.listenForCallbacks(new TravManager.ScopedFileManagerCallbacks() {
            @Override
            public void onFileCreated(File file) {
                mLastCapturedVideoFile = file;

                // Using CamcorderProfile for default video formats
                final CamcorderProfile camcorderProfile = CamcorderProfile.get(CamcorderProfile.QUALITY_720P);

                // Output file
                mMediaRecorder.setOutputFile(mLastCapturedVideoFile);

                // Video formats
                mMediaRecorder.setVideoEncoder(MediaRecorder.VideoEncoder.DEFAULT);
                mMediaRecorder.setVideoEncodingBitRate(10000000);
                mMediaRecorder.setVideoFrameRate(24);
                mMediaRecorder.setVideoSize(mVideoSize.getWidth(), mVideoSize.getHeight());

                // Audio formats
                mMediaRecorder.setAudioEncoder(MediaRecorder.AudioEncoder.AAC);
                mMediaRecorder.setAudioEncodingBitRate(camcorderProfile.audioBitRate);
                mMediaRecorder.setAudioSamplingRate(camcorderProfile.audioSampleRate);

                // Orientations
                int rotation = activity.getWindowManager().getDefaultDisplay().getRotation();
                switch (mSensorOrientation) {
                    case SENSOR_ORIENTATION_DEFAULT_DEGREES:
                        mMediaRecorder.setOrientationHint(DEFAULT_ORIENTATIONS.get(rotation));
                        break;
                    case SENSOR_ORIENTATION_INVERSE_DEGREES:
                        mMediaRecorder.setOrientationHint(INVERSE_ORIENTATIONS.get(rotation));
                        break;
                }

                // Prepare
                try {
                    mMediaRecorder.prepare();
                    startVideoRecording();
                } catch (IOException e) {
                    Log.d(TAG, "MediaRecorder prepare exception: "+e.getMessage());
                }
            }

            @Override
            public void onError(String message) {
                Log.d(TAG, "Video error: "+message);
            }
        }).generateVideoFile();
    }

    /**
     * Called from host to delete last captured video file if no longer used
     */
    public static void dismissCapturedVideoFile(){
        if(mLastCapturedVideoFile != null){
            if(mFileManager.deleteFile(mLastCapturedVideoFile)){
                mLastCapturedVideoFile = null;
            }
        }
    }








    // ===========================================================================================
    // ===================================== Common Uses =========================================
    // ===========================================================================================
    /**
     * Used in capture() method to get Camera capture callbacks
     * This callback manages captured sessions. Whenever a focus or a still picture is requested from a user,
     * CameraCaptureSession returns callbacks through this callback
     */
    private final static CameraCaptureSession.CaptureCallback mCameraCaptureSessionCaptureCallbacks = new CameraCaptureSession.CaptureCallback() {
        @Override
        public void onCaptureStarted(@NonNull CameraCaptureSession session, @NonNull CaptureRequest request, long timestamp, long frameNumber) {
            super.onCaptureStarted(session, request, timestamp, frameNumber);

            switch (mCameraState){
                case STATE_CAPTURING_IMAGE:
                    if(mCaptureSound != null) mCaptureSound.start();
                    break;

                case STATE_RECORDING_VIDEO:
                    mCameraHandlerListener.onVideoRecordingStarts();
                    break;
            }
        }

        @Override
        public void onCaptureCompleted(@NonNull CameraCaptureSession session, @NonNull CaptureRequest request, @NonNull TotalCaptureResult result) {
            super.onCaptureCompleted(session, request, result);

            try{
                switch (mCameraState){
                    case STATE_CAPTURING_IMAGE:
                    case STATE_IMAGE_CAPTURED:
                        mPreviewSession.stopRepeating();
                        startCameraPreview();
                        break;
                }
            }catch (CameraAccessException e){
                e.printStackTrace();
            }
        }

        @Override
        public void onCaptureFailed(@NonNull CameraCaptureSession session, @NonNull CaptureRequest request, @NonNull CaptureFailure failure) {
            super.onCaptureFailed(session, request, failure);

        }
    };

    /**
     * Camera States Enum
     */
    private enum CameraState{
        STATE_PREVIEW,
        STATE_FOCUS_LOCKED,
        STATE_FOCUS_UNLOCKED,
        STATE_CAPTURING_IMAGE,
        STATE_IMAGE_CAPTURED,
        STATE_RECORDING_VIDEO,
        STATE_VIDEO_RECORDED
    }

    /**
     * Main callbacks listener to inform listening hosts
     */
    public interface CameraHandlerListener{
        void onImageCapture(@NonNull File imageFile);
        void onVideoRecordingStarts();
        void onVideoRecordingEnds(@NonNull File videoFile);
        void onVideoRecordingLengthTicks(long maxProgress, long currentProgress);
        void onCloseCameraError();
    }

    /**
     * Compares two {@code Size}s based on their areas.
     */
    static class CompareSizesByArea implements Comparator<Size> {
        @Override
        public int compare(Size lhs, Size rhs) {
            // We cast here to ensure the multiplications won't overflow
            return Long.signum((long) lhs.getWidth() * lhs.getHeight() -
                    (long) rhs.getWidth() * rhs.getHeight());
        }
    }
}