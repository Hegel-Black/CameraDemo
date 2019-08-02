package com.hegel.camerademo;

import android.app.Activity;
import android.content.Intent;
import android.hardware.Camera;
import android.media.CamcorderProfile;
import android.media.MediaRecorder;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.util.Log;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.TextView;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;

public class MainActivity extends Activity implements View.OnClickListener {
    private static final String TAG = "Hegel-MainActivity";
    private FrameLayout mPreviewArea;
    private CameraPreview mPreview;
    private Camera mCamera;
    private MediaRecorder mediaRecorder;
    private boolean isRecording = false;
    private File recordingFile;
    private int currentRotation = 90;
    private int currentCameraId = 0;
    private int maxCameraCount = 0;

    private Button btn1, btn2, btn3, btn4;
    private TextView pathText;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Log.i(TAG, "onCreate()");
//        requestWindowFeature(Window.FEATURE_NO_TITLE);
//        getWindow().addFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN);
        setContentView(R.layout.main_page);
        mPreview = new CameraPreview(this);
        mPreviewArea = findViewById(R.id.preview_area);
        mPreviewArea.addView(mPreview);

        btn1 = findViewById(R.id.btn1);
        btn1.setOnClickListener(this);
        btn2 = findViewById(R.id.btn2);
        btn2.setOnClickListener(this);
        btn3 = findViewById(R.id.btn3);
        btn3.setOnClickListener(this);
        btn4 = findViewById(R.id.btn4);
        btn4.setOnClickListener(this);

        pathText = findViewById(R.id.pathText);

        maxCameraCount = Camera.getNumberOfCameras();
        Log.d(TAG, "maxCameraCount = " + maxCameraCount);
    }

    @Override
    protected void onResume() {
        super.onResume();
        Log.i(TAG, "onResume()");
        // Create an instance of Camera
        if (safeCameraOpen(currentCameraId)) {
            rotateCamera(currentRotation);
            mPreview.setCamera(mCamera);
            // 防止息屏后再进入时，预览停止
            try {
                mCamera.setPreviewDisplay(mPreview.getHolder());
                mCamera.startPreview();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        Log.i(TAG, "onPause()");
        mCamera.stopPreview();
        releaseMediaRecorder();
        releaseCameraAndPreview();
    }

    @Override
    protected void onStop() {
        super.onStop();
        Log.i(TAG, "onStop()");
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        Log.d(TAG, "onDestroy()");
    }

    @Override
    public void onClick(View v) {
        switch (v.getId()) {
            case R.id.btn1:
                takePicture();
                break;
            case R.id.btn2:
                switchCamera(getNextCamera());
                break;
            case R.id.btn3:
                rotateCamera(getNextRotation());
                break;
            case R.id.btn4:
                if (isRecording) {
                    mediaRecorder.stop();
                    releaseMediaRecorder();
                    btn4.setText("Record");
                    isRecording = false;
                    pathText.setText(recordingFile.getAbsolutePath());
                    notifyMediaScanner(recordingFile);
                } else {
                    if (prepareVideoRecorder()) {
                        mediaRecorder.start();
                        btn4.setText("Stop");
                        isRecording = true;
                    }
                }
                break;
        }
    }

    private boolean prepareVideoRecorder() {
        mCamera.unlock();
        mediaRecorder = new MediaRecorder();
        mediaRecorder.setCamera(mCamera);
        mediaRecorder.setAudioSource(MediaRecorder.AudioSource.CAMCORDER);
        mediaRecorder.setVideoSource(MediaRecorder.VideoSource.CAMERA);
        mediaRecorder.setProfile(CamcorderProfile.get(currentCameraId, CamcorderProfile.QUALITY_HIGH));
        Log.d(TAG, "video output path: " + getOutputVideoFile().getAbsolutePath());
        recordingFile = getOutputVideoFile();
        mediaRecorder.setOutputFile(recordingFile.getAbsolutePath());
        mediaRecorder.setPreviewDisplay(mPreview.getHolder().getSurface());
        try {
            mediaRecorder.prepare();
        } catch (IOException e) {
            Log.e(TAG, "IOException MediaRecorder.prepare(): " + e.getMessage());
            releaseMediaRecorder();
            return false;
        } catch (IllegalStateException e) {
            Log.e(TAG, "IllegalStateException MediaRecorder.prepare(): " + e.getMessage());
            releaseMediaRecorder();
            return false;
        }
        return true;
    }

    private void takePicture() {
        mCamera.takePicture(shutterCallback, null, jpegCallback);
    }

    private File getOutputPictureFile() {
        File pictureDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES);
        Date date = new Date(System.currentTimeMillis());
        SimpleDateFormat formatter = new SimpleDateFormat("yyyy-MM-dd_HH-mm-ss");
        String time = formatter.format(date);
        String pictureName = "Picture_" + time + ".jpeg";
        File picture = new File(pictureDir, pictureName);
        return picture;
    }

    private File getOutputVideoFile() {
        File videoDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MOVIES);
        Date date = new Date(System.currentTimeMillis());
        SimpleDateFormat formatter = new SimpleDateFormat("yyyy-MM-dd_HH-mm-ss");
        String time = formatter.format(date);
        String pictureName = "Video_" + time + ".mp4";
        File video = new File(videoDir, pictureName);
        return video;
    }

    private Camera.PictureCallback jpegCallback = new Camera.PictureCallback() {

        @Override
        public void onPictureTaken(byte[] data, Camera camera) {
            File pictureFile = getOutputPictureFile();
            if (pictureFile == null) {
                Log.d(TAG, "Error creating media file, check storage permissions");
                return;
            } else {
                Log.i(TAG, "picture name is " + pictureFile.getAbsolutePath());
            }

            try {
                FileOutputStream fos = new FileOutputStream(pictureFile);
                fos.write(data);
                fos.close();
            } catch (FileNotFoundException e) {
                Log.d(TAG, "File not found: " + e.getMessage());
            } catch (IOException e) {
                Log.d(TAG, "Error accessing file: " + e.getMessage());
            }

            mCamera.startPreview();
            pathText.setText(pictureFile.getAbsolutePath());
            notifyMediaScanner(pictureFile);
        }
    };

    private void notifyMediaScanner(File mediaFile) {
        Intent intent = new Intent(Intent.ACTION_MEDIA_SCANNER_SCAN_FILE);
        intent.setData(Uri.fromFile(mediaFile));
        sendBroadcast(intent);
    }

    private Camera.ShutterCallback shutterCallback = new Camera.ShutterCallback() {

        @Override
        public void onShutter() {
            Log.i(TAG, "onShutter: will take jpeg");
        }
    };

    /**
     * A safe way to get an instance of the Camera object.
     */

    private boolean safeCameraOpen(int id) {
        boolean qOpened = false;

        try {
            releaseCameraAndPreview();
            mCamera = Camera.open(id);
            qOpened = (mCamera != null);
        } catch (Exception e) {
            Log.e(TAG, "failed to open Camera");
            e.printStackTrace();
        }

        return qOpened;
    }

    private void releaseCameraAndPreview() {
        mPreview.setCamera(null);
        if (mCamera != null) {
            mCamera.release();
            mCamera = null;
        }
    }

    private void releaseMediaRecorder() {
        if (mediaRecorder != null) {
            mediaRecorder.reset();   // clear recorder configuration
            mediaRecorder.release(); // release the recorder object
            mediaRecorder = null;
            mCamera.lock();           // lock camera for later use
        }
    }

    private void rotateCamera(int rotation) {
        if (mCamera != null) {
            mCamera.setDisplayOrientation(rotation);
        }
    }

    private int getNextRotation() {
        if (currentRotation == 270) {
            currentRotation = 0;
        } else {
            currentRotation += 90;
        }
        return currentRotation;
    }

    private int getNextCamera() {
        if (currentCameraId == maxCameraCount - 1) {
            currentCameraId = 0;
        } else {
            currentCameraId++;
        }
        return currentCameraId;
    }

    private void switchCamera(int cameraId) {
        Log.d(TAG, "switchCamera: cameraId = " + cameraId);
        mCamera.stopPreview();
        releaseCameraAndPreview();
        if (safeCameraOpen(cameraId)) {
//            printCameraSize("PreviewSizes", mCamera.getParameters().getSupportedPreviewSizes());
//            printCameraSize("PictureSizes", mCamera.getParameters().getSupportedPictureSizes());
//            printPreviewFpsRange("PreviewFpsRange", mCamera.getParameters().getSupportedPreviewFpsRange());
            rotateCamera(currentRotation);
            setCustomSize();
            mPreview.setCamera(mCamera);
            try {
                mCamera.setPreviewDisplay(mPreview.getHolder());
                mCamera.startPreview();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    private void setCustomSize() {
        if (mCamera != null) {
            Camera.Parameters para = mCamera.getParameters();
            para.setPreviewSize(720, 720);
            para.setPictureSize(1280, 720);
            mCamera.setParameters(para);
        }
    }

    private void printCameraSize(String type, List<Camera.Size> previewSizes) {
        Log.d(TAG, type);
        for (Camera.Size size : previewSizes) {
            Log.d(TAG, "w = " + size.width + ", h = " + size.height);
        }
    }

    private void printPreviewFpsRange(String type, List<int[]> previewFpsRange) {
        Log.d(TAG, type);
        for (int[] item : previewFpsRange) {
            Log.d(TAG, "min = " + item[0] + ", max = " + item[1]);
        }
    }

}
