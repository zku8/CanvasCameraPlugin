package com.virtuoworks.cordovaplugincanvascamera;

import android.app.Activity;
import android.util.Log;

import java.util.List;

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

public class CanvasCamera extends CordovaPlugin {
  private final static String TAG = "CanvasCamera";

  private final static String kFpsKey = "fps";
  private final static String kWidthKey = "width";
  private final static String kHeightKey = "height";
  private final static String kLensOrientationKey = "cameraPosition";
  private final static String kHasThumbnailKey = "hasThumbnail";
  private final static String kThumbnailRatioKey = "thumbnailRatio";

  private Activity mActivity;
  private TextureView mTextureView = null;

  private Camera mCamera;
  private int mPreviewFormat;
  private CallbackContext mCallbackContext;

  private int mFileId = 0;
  private int mCameraId = 0;

  private int mOrientation = 0;
  private int mPreviewWidth = 0;
  private int mPreviewHeight = 0;

  private int mFps;
  private int mWidth;
  private int mHeight;
  private int mLensOrientation;
  private boolean mHasThumbnail;
  private double mThumbnailRatio;

  @Override
  public void onResume(boolean multitasking) {
    if (mTextureView != null) {
      initPreviewSurface();
    }
  }

  @Override
  public void onConfigurationChanged(Configuration newConfig) {
    setCurrentOrientation();
  }

  @Override
  public boolean execute(String action, final JSONArray args, final CallbackContext callbackContext) throws JSONException {
    mActivity = this.cordova.getActivity();

    if ("startCapture".equals(action)) {
      Log.d(TAG, "Starting startCapture thread...");
      mActivity.runOnUiThread(new Runnable() {
        public void run() {
          startCapture(args, callbackContext);
        }
      });
      return true;
    } else if ("stopCapture".equals(action)) {
      Log.d(TAG, "Starting stopCapture thread...");
      mActivity.runOnUiThread(new Runnable() {
        public void run() {
          stopCapture(args, callbackContext);
        }
      });
      return true;
    } else if ("setFlashMode".equals(action)) {
      Log.d(TAG, "Starting setFlashMode thread...");
      mActivity.runOnUiThread(new Runnable() {
        public void run() {
          setFlashMode(args, callbackContext);
        }
      });
      return true;
    } else if ("setCameraPosition".equals(action)) {
      Log.d(TAG, "Starting setCameraPosition thread...");
      mActivity.runOnUiThread(new Runnable() {
        public void run() {
          setCameraPosition(args, callbackContext);
        }
      });
      return true;
    }

    return false;
  }

  private void startCapture(JSONArray args, CallbackContext callbackContext) {
    // init parameters - default values
    mFps = 30;
    mWidth = 352;
    mHeight = 288;
    mHasThumbnail = false;
    mThumbnailRatio = 1 / 6;
    mLensOrientation = Camera.CameraInfo.CAMERA_FACING_BACK;

    // parse options
    try {
      JSONObject jsonData = args.getJSONObject(0);
      getOptions(jsonData);
    } catch (Exception e) {
      Log.e(TAG, "Options parsing error: " + e.getMessage());
    }

    if (checkCameraHardware(mActivity)) {
      mCallbackContext = callbackContext;
      PluginResult result = new PluginResult(PluginResult.Status.NO_RESULT);
      result.setKeepCallback(true);
      callbackContext.sendPluginResult(result);
      Log.d(TAG, "Initializing preview surface...");
      initPreviewSurface();
    } else {
      Log.e(TAG, "No camera detected");
    }
  }

  private void stopCapture(JSONArray args, CallbackContext callbackContext) {
    try {
      ((ViewGroup) mTextureView.getParent()).removeView(mTextureView);
      mTextureView = null;
      callbackContext.success(args);
    } catch (Exception e) {
      callbackContext.error("Couldn't stop camera");
      Log.e(TAG, "Couldn't stop camera : " + e.getMessage());
    }
  }

  private void setFlashMode(JSONArray args, CallbackContext callbackContext) {
    try {
      String flashMode;
      boolean isFlashModeOn = args.getBoolean(0);

      if (isFlashModeOn) {
        flashMode = Camera.Parameters.FLASH_MODE_TORCH;
      } else {
        flashMode = Camera.Parameters.FLASH_MODE_OFF;
      }

      if (mCamera != null) {
        Camera.Parameters params = mCamera.getParameters();
        params.setFlashMode(flashMode);
        mCamera.setParameters(params);
      }

      callbackContext.success();
    } catch (Exception e) {
      callbackContext.error("Failed to set flash mode");
    }
  }

  private void setCameraPosition(JSONArray args, CallbackContext callbackContext) {
    try {
      String cameraPosition = args.getString(0);

      if (cameraPosition.equals("front")) {
        mLensOrientation = Camera.CameraInfo.CAMERA_FACING_FRONT;
      } else {
        mLensOrientation = Camera.CameraInfo.CAMERA_FACING_BACK;
      }

      if (mCamera != null) {
        stopCamera();
        initPreviewSurface();
        callbackContext.success();
      } else {
        callbackContext.error("Failed to switch camera");
      }
    } catch (Exception e) {
      callbackContext.error("Failed to switch camera");
    }
  }

  private void initPreviewSurface() {
    mTextureView = new TextureView(mActivity);
    mTextureView.setSurfaceTextureListener(mSurfaceTextureListener);
    addViewToLayout(mTextureView);
  }

  private final TextureView.SurfaceTextureListener mSurfaceTextureListener = new TextureView.SurfaceTextureListener() {
    public void onSurfaceTextureAvailable(SurfaceTexture surface, int width, int height) {
      mCamera = getCameraInstance();

      try {
        setPreviewParameters(mCamera);

        mCamera.setPreviewTexture(surface);
        mCamera.setErrorCallback(mCameraErrorCallback);
        mCamera.setPreviewCallback(mCameraPreviewCallback);

        setCurrentOrientation();
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

  private final Camera.PreviewCallback mCameraPreviewCallback = new Camera.PreviewCallback() {
    @Override
    public void onPreviewFrame(final byte[] data, Camera camera) {

      new Thread(new Runnable() {
        public void run() {
          JSONObject imageFiles = new JSONObject();

          byte[] dataFull;
          dataFull = convertImageToJpeg(data, mPreviewWidth, mPreviewHeight);
          dataFull = rotateImage(dataFull, mOrientation, mPreviewWidth, mPreviewHeight);

          File file = getImageFile("f");
          saveImage(dataFull, file);
          try {
            imageFiles.accumulate("fullsize", file.getPath());
          } catch (JSONException e) {
            Log.e(TAG, "Cannot add fullsize " + e.getMessage());
          }

          if (mHasThumbnail) {
            byte[] dataThumb = resizeImage(dataFull);
            file = getImageFile("t");
            saveImage(dataThumb, file);
            try {
              imageFiles.accumulate("thumbnail", file.getPath());
            } catch (JSONException e) {
              Log.e(TAG, "Cannot add thumbnail " + e.getMessage());
            }
          }

          PluginResult result = new PluginResult(PluginResult.Status.OK, imageFiles);
          result.setKeepCallback(true);
          mCallbackContext.sendPluginResult(result);
        }
      }).start();
    }
  };

  private final Camera.ErrorCallback mCameraErrorCallback = new Camera.ErrorCallback() {
    @Override
    public void onError(int error, Camera camera) {
      Log.e(TAG, "on camera error: " + error);

      try {
        stopCamera();
        initPreviewSurface();
      } catch (Exception e) {
        Log.e(TAG, "something happened while stopping camera: " + e.getMessage());
      }
    }
  };

  private void setCurrentOrientation() {
    mOrientation = getDisplayOrientation();
  }

  private int getDisplayOrientation() {
    int result = 0;
    if (mCamera != null) {
      Camera.CameraInfo cameraInfo = new Camera.CameraInfo();
      Camera.getCameraInfo(mCameraId, cameraInfo);
      int rotation = mActivity.getWindowManager().getDefaultDisplay().getRotation();

      int degrees = 0;
      switch (rotation) {
        case Surface.ROTATION_0:
          degrees = 0;
          break;
        case Surface.ROTATION_90:
          degrees = 90;
          break;
        case Surface.ROTATION_180:
          degrees = 180;
          break;
        case Surface.ROTATION_270:
          degrees = 270;
          break;
      }

      if (cameraInfo.facing == Camera.CameraInfo.CAMERA_FACING_FRONT) {
        result = (cameraInfo.orientation + degrees) % 360;
        result = (360 - result) % 360;  // compensate the mirror
      } else {  // back-facing
        result = (cameraInfo.orientation - degrees + 360) % 360;
      }
    }
    return result;
  }

  private void setPreviewParameters(Camera camera) {
    Camera.Parameters params = camera.getParameters();

    Camera.Size previewSize = getOptimalPictureSize(params);
    mPreviewWidth = previewSize.width;
    mPreviewHeight = previewSize.height;
    params.setPreviewSize(mPreviewWidth, mPreviewHeight);

    int[] frameRate = getOptimalFrameRate(params);
    mFps = frameRate[0];
    params.setPreviewFpsRange(frameRate[0], frameRate[0]);

    String focusMode = getOptimalFocusMode(params);
    params.setFocusMode(focusMode);

    camera.setParameters(params);

    mPreviewFormat = params.getPreviewFormat();
  }


  private int[] getOptimalFrameRate(Camera.Parameters params) {
    List<int[]> supportedRanges = params.getSupportedPreviewFpsRange();

    int[] optimalFpsRange = new int[]{30, 30};

    for (int[] range : supportedRanges) {
      optimalFpsRange = range;
      if (range[0] >= (mFps * 1000)) {
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
      if (size.width >= mWidth &&
          size.height >= mHeight &&
          size.width < bigEnough.width &&
          size.height < bigEnough.height
        ) {
        bigEnough = size;
      }
    }

    return bigEnough;
  }

  private boolean checkCameraHardware(Context context) {
    return context.getPackageManager().hasSystemFeature(PackageManager.FEATURE_CAMERA);
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
          Log.d(TAG, "Trying to open camera : " + cameraId);
          try {
            mCameraId = cameraId;
            camera = Camera.open(cameraId);
            break;
          } catch (RuntimeException e) {
            Log.e(TAG, "Unable to open camera : " + e.getMessage());
          }
        }
      }
    } catch (Exception e) {
      Log.e(TAG, "No available camera : " + e.getMessage());
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

  private File getImageFile(String prefix) {
    File dir = mActivity.getExternalFilesDir(null);

    mFileId++;
    boolean deleted;
    if (mFileId > 10) {
      File prevFile = new File(dir, prefix + (mFileId - 10) + ".jpg");
      deleted = prevFile.delete();
    }

    return new File(dir, prefix + mFileId + ".jpg");
  }

  private void saveImage(byte[] bytes, File file) {
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

  private byte[] convertImageToJpeg(byte[] data, int width, int height) {
    YuvImage yuvImage = new YuvImage(data, mPreviewFormat, width, height, null);
    Rect rect = new Rect(0, 0, width, height);
    ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
    yuvImage.compressToJpeg(rect, 80, outputStream);
    return outputStream.toByteArray();
  }

  private byte[] rotateImage(byte[] data, int angle, int width, int height) {
    Bitmap bitmap = BitmapFactory.decodeByteArray(data, 0, data.length);
    ByteArrayOutputStream rotatedStream = new ByteArrayOutputStream();

    //Log.d(TAG, "Rotating output image by " + angle + "deg");
    final Matrix matrix = new Matrix();

    if (mLensOrientation == Camera.CameraInfo.CAMERA_FACING_FRONT) {
      //Log.d(TAG, "Mirror y axis");
      matrix.preScale(-1.0f, 1.0f);
    }

    matrix.postRotate(angle);

    bitmap = Bitmap.createBitmap(bitmap, 0, 0, width, height, matrix, false);

    bitmap.compress(Bitmap.CompressFormat.JPEG, 80, rotatedStream);
    return rotatedStream.toByteArray();
  }

  private byte[] resizeImage(byte[] data) {
    Bitmap bitmap = BitmapFactory.decodeByteArray(data, 0, data.length);
    int targetWidth = (int)(bitmap.getWidth() * mThumbnailRatio);
    int targetHeight = (int)(bitmap.getHeight() * mThumbnailRatio);

    if (targetWidth > 0 && targetHeight > 0) {
        bitmap = Bitmap.createScaledBitmap(bitmap, targetWidth, targetHeight, true);
        ByteArrayOutputStream resizedStream = new ByteArrayOutputStream();
        bitmap.compress(Bitmap.CompressFormat.JPEG, 100, resizedStream);
        return resizedStream.toByteArray();
    } else {
        return data;
    }
  }

  private void getOptions(JSONObject jsonData) throws Exception {
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
    if (jsonData.has(kWidthKey)) {
      mWidth = jsonData.getInt(kWidthKey);
    }

    // height
    if (jsonData.has(kHeightKey)) {
      mHeight = jsonData.getInt(kHeightKey);
    }

    // hasThumbnail
    if (jsonData.has(kHasThumbnailKey)) {
      mHasThumbnail = jsonData.getBoolean(kHasThumbnailKey);
    }

    // thumbnailRatio
    if (jsonData.has(kThumbnailRatioKey)) {
       mThumbnailRatio = jsonData.getDouble(kThumbnailRatioKey);
    }

  }

}
