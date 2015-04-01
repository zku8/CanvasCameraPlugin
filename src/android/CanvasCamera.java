package com.keith.canvascameraplugin;

import android.app.Activity;
import android.util.Log;
import android.util.Size;
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
    private final static String TAG = "SimpleCamera";

    private Activity mActivity;
    private CallbackContext mCallbackContext;

    private CameraManager mCameraManager;
    private CameraCaptureSession mCameraSession;
    private CameraDevice mCamera;
    private String mCameraId;
    private ImageReader mReader = null;
    private HandlerThread mBackgroundThread;
    private Handler mBackgroundHandler;
    private SimpleImageListener mListener = null;

    @Override
    public boolean execute(String action, JSONArray args, CallbackContext callbackContext) throws JSONException
    {
        if ("startCapture".equals(action)) {
            if (args.length() > 0)
                startCapture(args, callbackContext);

            return true;
        }
        else if ("stopCapture".equals(action)) {
            stopCapture(args, callbackContext);
            return true;
        }

        return false;
    }

    private void startCapture(JSONArray args, CallbackContext callbackContext)
    {
        mActivity = this.cordova.getActivity();

        mCameraManager = (CameraManager) mActivity.getSystemService(Context.CAMERA_SERVICE);
        Log.d(TAG, "camera manager obtained");

        try {
            mCameraId = mCameraManager.getCameraIdList()[0];
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
            mCamera.close();

            Log.d(TAG, "capture stopped");
            callbackContext.success();
        }
        catch (Exception e){
            callbackContext.error("Failed to stop capture");
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
                Size previewSize = outputSizes[outputSizes.length - 1];

                Log.d(TAG, "Creating ImageReader with size " + previewSize.toString() + " for camera " + mCameraId);

                mReader = ImageReader.newInstance(previewSize.getWidth(), previewSize.getHeight(),
                    ImageFormat.JPEG, /*maxImages*/10);

                mListener  = new SimpleImageListener();

                mReader.setOnImageAvailableListener(mListener, mBackgroundHandler);

                camera.createCaptureSession(Arrays.asList(mReader.getSurface()),
                    new CameraCaptureSession.StateCallback() {

                        @Override
                        public void onConfigured(CameraCaptureSession cameraCaptureSession) {
                            mCameraSession = cameraCaptureSession;
                            try {
                                CaptureRequest request = prepareCaptureRequest(ImageFormat.JPEG);

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

        private CaptureRequest prepareCaptureRequest(int format) {
            List<Surface> outputSurfaces = new ArrayList<Surface>(1);
            Surface surface = mReader.getSurface();

            outputSurfaces.add(surface);

            try {
                CaptureRequest.Builder captureBuilder =
                    mCamera.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW);

                captureBuilder.addTarget(surface);
                return captureBuilder.build();

            } catch (CameraAccessException e) {
                e.printStackTrace();
                return null;
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
        private int mFileId = 0;

        @Override
        public void onImageAvailable(ImageReader reader) {
            Image img = reader.acquireLatestImage();
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

}
