package com.virtuoworks.cordova-plugin-canvas-camera;

import android.app.Activity;
import android.util.Log;
import android.util.Size;
import java.util.List;
import android.util.SparseIntArray;
import android.content.Context;
import android.content.res.Configuration;

import android.content.pm.PackageManager;
import android.hardware.Camera;
import android.graphics.SurfaceTexture;
import android.graphics.YuvImage;
import android.graphics.Rect;
import java.io.ByteArrayOutputStream;
import android.view.TextureView;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.view.Surface;
import android.graphics.Matrix;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;

import org.apache.cordova.CallbackContext;
import org.apache.cordova.CordovaPlugin;
import org.apache.cordova.PluginResult;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;

public class CanvasCamera extends CordovaPlugin
{
    private final static String TAG = "CanvasCamera";
    private final static String kLensOrientationKey = "cameraPosition";
    private final static String kFpsKey = "fps";
    private final static String kWidthKey = "width";
    private final static String kHeightKey = "height";

    private Activity mActivity;
    private CallbackContext mCallbackContext;
    private TextureView mTextureView = null;
    private Camera mCamera;
    private int mCameraId = 0;
    private int mPreviewWidth = 0;
    private int mPreviewHeight = 0;
    private int mFileId = 0;
    private int mPreviewFormat;

    private int mLensOrientation;
    private int mFps;
    private int mWidth;
    private int mHeight;
    private int mRotation = 0;

    @Override
    public void onResume(boolean multitasking) {
        if (mTextureView != null) {
            initPreviewSurface();
        }
    }

    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        setCurrentRotation();
    }

    @Override
    public boolean execute(String action, final JSONArray args, final CallbackContext callbackContext) throws JSONException
    {
        mActivity = this.cordova.getActivity();

        if ("startCapture".equals(action)) {
            mActivity.runOnUiThread(new Runnable() {
                public void run() {
                    startCapture(args, callbackContext);
                }
            });
            return true;
        } else if ("stopCapture".equals(action)) {
            mActivity.runOnUiThread(new Runnable() {
                public void run() {
                    stopCapture(args, callbackContext);
                }
            });
            return true;
        } else if ("setFlashMode".equals(action)) {
            mActivity.runOnUiThread(new Runnable() {
                public void run() {
                    setFlashMode(args, callbackContext);
                }
            });
            return true;
        } else if ("setCameraPosition".equals(action)) {
            mActivity.runOnUiThread(new Runnable() {
                public void run() {
                    setCameraPosition(args, callbackContext);
                }
            });
            return true;
        }

        return false;
    }

    private void startCapture(JSONArray args, CallbackContext callbackContext)
    {
        String cameraPosition = "";

        // init parameters - default values
        mFps = 30;
        mWidth = 352;
        mHeight = 288;
        mLensOrientation = Camera.CameraInfo.CAMERA_FACING_BACK;

        // parse options
        try {
            JSONObject jsonData = args.getJSONObject(0);
            getOptions(jsonData);
        } catch(Exception e) {
            Log.e(TAG, "Parsing options error: " + e.getMessage());
        }

        if (checkCameraHardware(mActivity)) {
            mCallbackContext = callbackContext;
            PluginResult result = new PluginResult(PluginResult.Status.NO_RESULT);
            result.setKeepCallback(true);
            callbackContext.sendPluginResult(result);

            initPreviewSurface();
        } else {
            Log.e(TAG, "No camera detected");
        }
    }

    private void stopCapture(JSONArray args, CallbackContext callbackContext)
    {
        try {
            ((ViewGroup)mTextureView.getParent()).removeView(mTextureView);
            mTextureView = null;
        } catch (Exception e) {
            Log.e(TAG, "Camera can't be stopped: " + e.getMessage());
        }
    }

    private void setFlashMode(JSONArray args, CallbackContext callbackContext)
    {
        try {
            String flashMode;
            boolean isFlashModeOn = args.getBoolean(0);

            if (isFlashModeOn) {
                flashMode = Camera.Parameters.FLASH_MODE_TORCH;
            }
            else {
                flashMode = Camera.Parameters.FLASH_MODE_OFF;
            }

            if (mCamera != null) {
                Camera.Parameters params = mCamera.getParameters();
                params.setFlashMode(flashMode);
                mCamera.setParameters(params);
            }

            callbackContext.success();
        }
        catch (Exception e){
            callbackContext.error("Failed to set flash mode");
        }
    }

    private void setCameraPosition(JSONArray args, CallbackContext callbackContext)
    {
        try {
            String cameraPosition = args.getString(0);

            if (cameraPosition.equals("front")) {
                mLensOrientation = Camera.CameraInfo.CAMERA_FACING_FRONT;
            }
            else {
                mLensOrientation = Camera.CameraInfo.CAMERA_FACING_BACK;
            }

            if (mCamera != null) {
                stopCamera();
                initPreviewSurface();

                callbackContext.success();
            }
            else {
                callbackContext.error("Failed to switch camera");
            }
        }
        catch (Exception e){
            callbackContext.error("Failed to switch camera");
        }
    }

    private void initPreviewSurface() {
        mTextureView = new TextureView(mActivity);
        mTextureView.setSurfaceTextureListener(mSurfaceTextureListener);
        addViewToLayout(mTextureView);
    }

    private TextureView.SurfaceTextureListener mSurfaceTextureListener = new TextureView.SurfaceTextureListener() {
        public void onSurfaceTextureAvailable(SurfaceTexture surface, int width, int height) {
            mCamera = getCameraInstance();
            setCurrentRotation();

            try {
                setPreviewParameters(mCamera);

                mCamera.setPreviewTexture(surface);
                mCamera.setPreviewCallback(mCameraPreviewCallback);
                mCamera.setErrorCallback(mCameraErrorCallback);

                mCamera.startPreview();
            } catch (Exception e) {
                Log.e(TAG, "Failed to init preview: " + e.getLocalizedMessage());
            }

            mTextureView.setVisibility(View.INVISIBLE);
            mTextureView.setAlpha(0);
        }

        public void onSurfaceTextureSizeChanged(SurfaceTexture surface, int width, int height) {
            // Ignored, Camera does all the work for us
        }

        public boolean onSurfaceTextureDestroyed(SurfaceTexture surface) {
            stopCamera();
            return true;
        }

        public void onSurfaceTextureUpdated(SurfaceTexture surface) {
            // Invoked every time there's a new Camera preview frame
        }
    };

    private Camera.PreviewCallback mCameraPreviewCallback = new Camera.PreviewCallback() {
        @Override
        public void onPreviewFrame(byte[] data, Camera camera) {
            data = convertImageToJpeg(data);
            // data = rotateImage(data, mRotation);

            File file = getImageFile();
            saveImage(data, file);
            // data = null;
            // System.gc();

            PluginResult result = new PluginResult(PluginResult.Status.OK, file.getPath());
            result.setKeepCallback(true);
            mCallbackContext.sendPluginResult(result);
        }
    };

    private Camera.ErrorCallback mCameraErrorCallback = new Camera.ErrorCallback() {
        @Override
        public void onError(int error, Camera camera) {
            Log.e(TAG, "on camera error: " + error);

            try {
                stopCamera();
                initPreviewSurface();
            } catch(Exception e) {
                Log.e(TAG, "something happened while stopping camera: " + e.getMessage());
            }
        }
    };

    private void setCurrentRotation() {
        if (mCamera != null) {
            Camera.CameraInfo cameraInfo = new Camera.CameraInfo();
            Camera.getCameraInfo(mCameraId, cameraInfo);

            int surfaceRotation = mActivity.getWindowManager().getDefaultDisplay().getRotation();

            int correction = cameraInfo.orientation - 90;

            if (cameraInfo.facing == Camera.CameraInfo.CAMERA_FACING_FRONT) {
                switch (surfaceRotation) {
                    case Surface.ROTATION_90: correction += 180; break;
                    case Surface.ROTATION_270: correction += 180; break;
                }
            }

            mRotation = correction;
        }
    }

    private void setPreviewParameters(Camera camera) {
        Camera.Parameters params = camera.getParameters();

        Camera.Size previewSize = getOptimalPictureSize(params);
        mPreviewWidth = previewSize.width;
        mPreviewHeight = previewSize.height;
        params.setPreviewSize(mPreviewWidth, mPreviewHeight);

        int[] frameRate = getOptimalFramerate(params);
        mFps = (int)frameRate[0];
        params.setPreviewFpsRange((int)frameRate[0], (int)frameRate[0]);

        String focusMode = getOptimalFocusMode(params);
        params.setFocusMode(focusMode);

        camera.setParameters(params);

        mPreviewFormat = params.getPreviewFormat();
    }


    private int[] getOptimalFramerate(Camera.Parameters params) {
        List<int[]> supportedRanges = params.getSupportedPreviewFpsRange();

        int[] optimalFpsRange = new int[] {30, 30};

        for (int[] range : supportedRanges) {
            optimalFpsRange = range;
            if ((int)range[0] >= (mFps * 1000)) {
               break;
            }
        }
        return optimalFpsRange;
    }

    private String getOptimalFocusMode(Camera.Parameters params) {
        String result;
        List<String> focusModes = params.getSupportedFocusModes();

        if (focusModes.contains(Camera.Parameters.FOCUS_MODE_CONTINUOUS_VIDEO)) {
            result = Camera.Parameters.FOCUS_MODE_CONTINUOUS_VIDEO;
        } else if (focusModes.contains(Camera.Parameters.FOCUS_MODE_AUTO)) {
            result = Camera.Parameters.FOCUS_MODE_AUTO;
        } else {
            result = params.getSupportedFocusModes().get(0);
        }

        return result;
    }

    private Camera.Size getOptimalPictureSize(Camera.Parameters params) {
        Camera.Size bigEnough = params.getSupportedPictureSizes().get(0);

        for (Camera.Size size : params.getSupportedPictureSizes()) {
            if (size.width >= mWidth && size.height >= mHeight
                && size.width < bigEnough.width
                && size.height < bigEnough.height
            ) {
                bigEnough = size;
            }
        }

        return bigEnough;
    }

    private boolean checkCameraHardware(Context context) {
        if (context.getPackageManager().hasSystemFeature(PackageManager.FEATURE_CAMERA)){
            return true;
        } else {
            return false;
        }
    }

    private Camera getCameraInstance() {
        Camera camera = null;

        try {
            Camera.CameraInfo cameraInfo = new Camera.CameraInfo();
            int cameraCount = Camera.getNumberOfCameras();
            int cameraId;

            for (cameraId = 0; cameraId < cameraCount; cameraId++) {
                Camera.getCameraInfo(cameraId, cameraInfo);
                if (cameraInfo.facing == mLensOrientation) {
                    try {
                        mCameraId = cameraId;
                        camera = Camera.open(cameraId);
                        break;
                    } catch (RuntimeException e) {
                        Log.e(TAG, "Camera failed to open: " + e.getMessage());
                    }
                }
            }
        } catch (Exception e){
            Log.e(TAG, "Camera is not available: " + e.getMessage());
        }

        return camera;
    }

    private void stopCamera() {
        if (mCamera != null) {
            mCamera.stopPreview();
            mCamera.setPreviewCallback(null);
            mCamera.release();
            mCamera = null;
            mCameraId = 0;
        }
    }

    private void addViewToLayout(View view) {
        WindowManager mW = (WindowManager) mActivity.getSystemService(Context.WINDOW_SERVICE);
		int screenWidth = mW.getDefaultDisplay().getWidth();
		int screenHeight = mW.getDefaultDisplay().getHeight();

        mActivity.addContentView(view, new ViewGroup.LayoutParams(screenWidth, screenHeight));
    }

    private File getImageFile() {
        File dir = mActivity.getExternalFilesDir(null);

        mFileId++;
        if (mFileId > 10) {
            File prevFile = new File(dir, (mFileId - 10) + ".jpg");
            prevFile.delete();
        }

        return new File(dir, mFileId + ".jpg");
    }

    public void saveImage(byte[] bytes, File file) {
        FileOutputStream output = null;
        try {
            output = new FileOutputStream(file);
            output.write(bytes);
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            if (null != output) {
                try {
                    output.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }
    }

    private byte[] convertImageToJpeg(byte[] data) {

        YuvImage yuvImage = new YuvImage(data, mPreviewFormat, mPreviewWidth, mPreviewHeight, null);
        Rect rect = new Rect(0, 0, mPreviewWidth, mPreviewHeight);
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        yuvImage.compressToJpeg(rect, 80, outputStream);
        return outputStream.toByteArray();
    }

    private byte[] rotateImage(byte[] data, int angle) {
        Bitmap bitmap = BitmapFactory.decodeByteArray(data, 0, data.length);
        ByteArrayOutputStream rotatedStream = new ByteArrayOutputStream();

        Matrix matrix = new Matrix();
        matrix.postRotate(angle);

        bitmap = Bitmap.createBitmap(bitmap, 0, 0, mPreviewWidth, mPreviewHeight, matrix, false);

        bitmap.compress(Bitmap.CompressFormat.JPEG, 80, rotatedStream);
        return rotatedStream.toByteArray();
    }

    private void getOptions(JSONObject jsonData) throws Exception
    {
        if (jsonData == null) {
            return;
        }

        // lens orientation
        if (jsonData.has(kLensOrientationKey)) {
            String orientation = jsonData.getString(kLensOrientationKey);
            if (orientation.equals("front")) {
                mLensOrientation = Camera.CameraInfo.CAMERA_FACING_FRONT;
            } else {
                mLensOrientation = Camera.CameraInfo.CAMERA_FACING_BACK;
            }
        }

        // fps
        if (jsonData.has(kFpsKey)) {
            mFps = jsonData.getInt(kFpsKey);
        }

        // width
        /*if (jsonData.has(kWidthKey)) {
            mWidth = jsonData.getInt(kWidthKey);
        }*/

        // height
        /*if (jsonData.has(kHeightKey)) {
            mHeight = jsonData.getInt(kHeightKey);
        }*/
    }

}
