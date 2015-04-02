package com.keith.canvascameraplugin;

import android.app.Activity;
import android.util.Log;
import android.util.Size;
import android.util.SparseIntArray;
import android.os.Handler;
import android.os.HandlerThread;
import android.content.Context;
import android.media.Image;
import android.media.ImageReader;
import android.graphics.ImageFormat;
import android.view.Surface;
import android.hardware.camera2.CameraCaptureSession;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraDevice;
import android.hardware.camera2.CameraManager;
import android.hardware.camera2.CameraMetadata;
import android.hardware.camera2.CaptureRequest;
import android.hardware.camera2.CaptureResult;
import android.hardware.camera2.TotalCaptureResult;
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.params.StreamConfigurationMap;

import org.apache.cordova.CallbackContext;
import org.apache.cordova.CordovaPlugin;
import org.apache.cordova.PluginResult;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.Comparator;
import java.util.List;
import java.util.Arrays;
import java.util.ArrayList;
import java.util.Collections;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;

public class CanvasCamera extends CordovaPlugin
{
    private final static String TAG = "CanvasCamera";

    private Activity mActivity;
    private CallbackContext mCallbackContext;

    private CameraManager mCameraManager;
    private CameraCaptureSession mCameraSession;
    private CameraDevice mCamera;
    private String mCameraId;
    private ImageReader mReader = null;
    private int mFileId = 0;
    private HandlerThread mBackgroundThread;
    private Handler mBackgroundHandler;
    private SimpleImageListener mListener = null;

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
        }
        else if ("stopCapture".equals(action)) {
            mActivity.runOnUiThread(new Runnable() {
                public void run() {
                    stopCapture(args, callbackContext);
                }
            });
            return true;
        }
        else if ("setFlashMode".equals(action)) {
            mActivity.runOnUiThread(new Runnable() {
                public void run() {
                    setFlashMode(args, callbackContext);
                }
            });
            return true;
        }
        else if ("setCameraPosition".equals(action)) {
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
        mCameraManager = (CameraManager) mActivity.getSystemService(Context.CAMERA_SERVICE);
        Log.d(TAG, "camera manager obtained");

        try {
            mCameraId = getCameraIdForOrientation(CameraCharacteristics.LENS_FACING_BACK);
            startBackgroundThread();
            mCameraManager.openCamera(mCameraId, cameraStateCallback, null);

            Log.d(TAG, "capture started");
        } catch (CameraAccessException e) {
            e.printStackTrace();
        }

        mCallbackContext = callbackContext;
        PluginResult result = new PluginResult(PluginResult.Status.NO_RESULT);
        result.setKeepCallback(true);
        callbackContext.sendPluginResult(result);
    }

    private void stopCapture(JSONArray args, CallbackContext callbackContext)
    {
        try {
            stopBackgroundThread();
            mCameraSession.close();
            mCamera.close();

            Log.d(TAG, "capture stopped");
            callbackContext.success();
        }
        catch (Exception e){
            callbackContext.error("Failed to stop capture");
        }
    }

    private void setFlashMode(JSONArray args, CallbackContext callbackContext)
    {
        try {
            int flashMode;

            boolean isFlashModeOn = args.getBoolean(0);

            if (isFlashModeOn) {
                flashMode = CameraMetadata.FLASH_MODE_TORCH;
            }
            else {
                flashMode = CameraMetadata.FLASH_MODE_OFF;
            }

            CaptureRequest request = prepareCaptureRequest(flashMode);
            mCameraSession.setRepeatingRequest(request, null, null);

            Log.d(TAG, "flash mode changed");
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

            Log.d(TAG, "camera is gonna be switched to " + cameraPosition);

            int lensOrientation = CameraCharacteristics.LENS_FACING_BACK;

            if (cameraPosition.equals("front")) {
                lensOrientation = CameraCharacteristics.LENS_FACING_FRONT;
            }

            mCameraId = getCameraIdForOrientation(lensOrientation);

            if (mCameraId != null) {
                stopBackgroundThread();
                mCameraSession.close();
                mCamera.close();

                startBackgroundThread();
                mCameraManager.openCamera(mCameraId, cameraStateCallback, null);

                Log.d(TAG, "camera switched");
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

    private CameraDevice.StateCallback cameraStateCallback = new CameraDevice.StateCallback() {

		@Override
		public void onOpened(CameraDevice camera) {
			Log.d(TAG, "camera opened");

            mCamera = camera;


            try {
                CameraCharacteristics characteristics
                    = mCameraManager.getCameraCharacteristics(mCameraId);
                StreamConfigurationMap map = characteristics.get(
                    CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP);

                Size[] outputSizes = map.getOutputSizes(ImageFormat.JPEG);
                Size previewSize = chooseOptimalSize(outputSizes, 352, 288);

                Log.d(TAG, "Creating ImageReader with size " + previewSize.toString() + " for camera " + mCameraId);

                mReader = ImageReader.newInstance(previewSize.getWidth(), previewSize.getHeight(),
                    ImageFormat.JPEG, /*maxImages*/2);

                mListener  = new SimpleImageListener();

                mReader.setOnImageAvailableListener(mListener, mBackgroundHandler);

                camera.createCaptureSession(Arrays.asList(mReader.getSurface()),
                    new CameraCaptureSession.StateCallback() {

                        @Override
                        public void onConfigured(CameraCaptureSession cameraCaptureSession) {
                            mCameraSession = cameraCaptureSession;
                            try {
                                CaptureRequest request = prepareCaptureRequest(CameraMetadata.FLASH_MODE_OFF);
                                mCameraSession.setRepeatingRequest(request, null, null);

                                Log.d(TAG, "Set repeating capture request");
                            } catch (CameraAccessException e) {
                                e.printStackTrace();
                            }
                        }

                        @Override
                        public void onConfigureFailed(CameraCaptureSession cameraCaptureSession) {
                            Log.e(TAG, "onConfigureFailed");
                        }
                    }, null);

            } catch (CameraAccessException e) {
                e.printStackTrace();
            }
		}

		@Override
		public void onError(CameraDevice camera, int error) {
			// TODO Auto-generated method stub
			Log.e(TAG, "onError");

		}

		@Override
		public void onDisconnected(CameraDevice camera) {
			// TODO Auto-generated method stub
			Log.e(TAG, "onDisconnected");

		}
	};

    private CaptureRequest prepareCaptureRequest(int flashMode) {
        List<Surface> outputSurfaces = new ArrayList<Surface>(1);
        Surface surface = mReader.getSurface();

        outputSurfaces.add(surface);

        try {
            CaptureRequest.Builder captureBuilder =
                mCamera.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW);

            captureBuilder.addTarget(surface);
            captureBuilder.set(CaptureRequest.COLOR_CORRECTION_MODE,
                                CaptureRequest.COLOR_CORRECTION_MODE_FAST);

            int rotation = mActivity.getWindowManager().getDefaultDisplay().getRotation();

            CameraCharacteristics characteristics = mCameraManager.getCameraCharacteristics(mCameraId);
            int orientation = characteristics.get(CameraCharacteristics.LENS_FACING);
            if(orientation == CameraCharacteristics.LENS_FACING_FRONT) {
                rotation = 180;
            }
            else {
                rotation = 0;
            }

            Log.d(TAG, "Rotation: " + rotation);
            captureBuilder.set(CaptureRequest.JPEG_ORIENTATION, rotation);

            captureBuilder.set(CaptureRequest.FLASH_MODE, flashMode);

            return captureBuilder.build();

        } catch (CameraAccessException e) {
            e.printStackTrace();
            return null;
        }
    }

    private String getCameraIdForOrientation(int cameraFacingOrientation) {
        try {
            String[] cameraIdList = mCameraManager.getCameraIdList();

            for(final String cameraId : cameraIdList) {
                CameraCharacteristics characteristics = mCameraManager.getCameraCharacteristics(cameraId);
                int orientation = characteristics.get(CameraCharacteristics.LENS_FACING);
                if(orientation == cameraFacingOrientation) {
                    return cameraId;
                }
            }

            if (cameraIdList.length > 0) {
                return cameraIdList[0];
            }
        } catch (CameraAccessException e) {
            e.printStackTrace();
            return null;
        }

        return null;
    }

    private void startBackgroundThread() {
        mBackgroundThread = new HandlerThread("CameraBackground");
        mBackgroundThread.start();
        mBackgroundHandler = new Handler(mBackgroundThread.getLooper());
        Log.d(TAG, "Background thread started");
    }

    private void stopBackgroundThread() {
        mBackgroundThread.quitSafely();
        try {
            mBackgroundThread.join();
            mBackgroundThread = null;
            mBackgroundHandler = null;

            Log.d(TAG, "Background thread stopped");
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }

    private class SimpleImageListener implements ImageReader.OnImageAvailableListener {
        @Override
        public void onImageAvailable(ImageReader reader) {
            Image img = reader.acquireLatestImage();
            if (img != null) {
                Log.d(TAG, "New image available! With width: " + img.getWidth());

                mFileId++;

                File file = new File(mActivity.getExternalFilesDir(null), mFileId + ".jpg");

                if (mFileId > 10)
                {
                    File prevFile = new File(mActivity.getExternalFilesDir(null), (mFileId - 10) + ".jpg");
                    prevFile.delete();
                }

                saveImage(img, file);

                PluginResult result = new PluginResult(PluginResult.Status.OK,
                            file.getPath());

                result.setKeepCallback(true);
                mCallbackContext.sendPluginResult(result);
            }
        }
    }


    public void saveImage(Image image, File file) {
        ByteBuffer buffer = image.getPlanes()[0].getBuffer();
        byte[] bytes = new byte[buffer.remaining()];
        buffer.get(bytes);
        FileOutputStream output = null;
        try {
            output = new FileOutputStream(file);
            output.write(bytes);
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            image.close();
            if (null != output) {
                try {
                    output.close();
                    Log.d(TAG, "file saved!");
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }
    }

    private static Size chooseOptimalSize(Size[] choices, int width, int height) {
        Size bigEnough = choices[0];
        for (Size option : choices) {
            if (option.getWidth() >= width && option.getHeight() >= height
                && option.getWidth() < bigEnough.getWidth()
                && option.getHeight() < bigEnough.getHeight()
            ) {
                bigEnough = option;
            }
        }

        return bigEnough;
    }

}
