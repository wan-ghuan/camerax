package com.wangxb.camerax.app;

import android.Manifest;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Bundle;
import android.util.Log;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.camera.core.CameraSelector;
import androidx.camera.core.ImageAnalysis;
import androidx.camera.core.ImageCapture;
import androidx.camera.core.ImageCaptureException;
import androidx.camera.core.ImageProxy;
import androidx.camera.core.Preview;
import androidx.camera.lifecycle.ProcessCameraProvider;
import androidx.camera.view.PreviewView;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.google.common.util.concurrent.ListenableFuture;

import java.io.File;
import java.nio.ByteBuffer;
import java.text.SimpleDateFormat;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MainActivity extends AppCompatActivity {
    private static final String[] REQUIRED_PERMISSIONS = new String[] { Manifest.permission.CAMERA };
    private static final int REQUEST_CODE_PERMISSIONS = 10;
    private static final String TAG = "CameraXBasic";
    private static final String FILENAME_FORMAT = "yyyy-MM-dd-HH-mm-ss-SSS";
    private ImageCapture mImageCapture;
    private ExecutorService mWorkExecutor;
    private File mOutputDirectory;
    private PreviewView mPreviewView;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        if (permissionsGranted()) {
            startCamera();
        } else {
            ActivityCompat.requestPermissions(
                    this, REQUIRED_PERMISSIONS, REQUEST_CODE_PERMISSIONS);
        }

        mPreviewView = findViewById(R.id.viewFinder);
        findViewById(R.id.camera_capture_button).setOnClickListener(view -> takePhoto());

        mOutputDirectory = getOutputDirectory();
        mWorkExecutor = Executors.newSingleThreadExecutor();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        mWorkExecutor.shutdown();
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQUEST_CODE_PERMISSIONS) {
            if (permissionsGranted()) {
                startCamera();
            } else {
                Toast.makeText(this,
                        "Permissions not granted by the user.",
                        Toast.LENGTH_SHORT).show();
                finish();
            }
        }
    }

    private File getOutputDirectory() {
        File[] dirs = getExternalMediaDirs();
        if (dirs == null || dirs[0] == null) {
            return getFilesDir();
        } else {
            return dirs[0];
        }
    }

    private void takePhoto() {
        // Get a stable reference of the modifiable image capture use case
        if (mImageCapture == null) {
            return;
        }

        // Create time-stamped output file to hold the image
        File photoFile = new File(mOutputDirectory, new SimpleDateFormat(FILENAME_FORMAT, Locale.US).format(System.currentTimeMillis()) + ".jpg");

        // Create output options object which contains file + metadata
        ImageCapture.OutputFileOptions outputOptions = new ImageCapture.OutputFileOptions.Builder(photoFile).build();

        // Set up image capture listener, which is triggered after photo has
        // been taken
        mImageCapture.takePicture(outputOptions, ContextCompat.getMainExecutor(this), new ImageCapture.OnImageSavedCallback() {
            @Override
            public void onImageSaved(@NonNull ImageCapture.OutputFileResults outputFileResults) {
                Uri savedUri = Uri.fromFile(photoFile);
                String msg = "Photo capture succeeded: " + savedUri.toString();
                Toast.makeText(getBaseContext(), msg, Toast.LENGTH_SHORT).show();
                Log.i(TAG, msg);
            }

            @Override
            public void onError(@NonNull ImageCaptureException exception) {
                Log.e(TAG, "Photo capture failed: " + exception.getMessage(), exception);

            }
        });
    }

    private void startCamera() {
        ListenableFuture<ProcessCameraProvider> cameraProviderFuture = ProcessCameraProvider.getInstance(this);

        cameraProviderFuture.addListener(() -> {
            try {
                // Used to bind the lifecycle of cameras to the lifecycle owner
                ProcessCameraProvider cameraProvider = cameraProviderFuture.get();

                // Preview
                Preview preview = new Preview.Builder()
                        .build();
                preview.setSurfaceProvider(mPreviewView.getSurfaceProvider());

                mImageCapture = new ImageCapture.Builder()
                        .build();

                ImageAnalysis imageAnalysis = new ImageAnalysis.Builder()
                        .build();
                imageAnalysis.setAnalyzer(mWorkExecutor, new LuminosityAnalyzer(luminosity -> Log.d(TAG, "Average luminosity: " + luminosity)));

                // Select back camera as a default
                CameraSelector cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA;

                // Unbind use cases before rebinding
                cameraProvider.unbindAll();

                // Bind use cases to camera
                cameraProvider.bindToLifecycle(MainActivity.this, cameraSelector, preview, mImageCapture, imageAnalysis);

            } catch(Exception exc) {
                Log.e(TAG, "Use case binding failed", exc);
            }
        }, ContextCompat.getMainExecutor(this));
    }

    private boolean permissionsGranted() {
        return ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED;
    }


    private static class LuminosityAnalyzer implements ImageAnalysis.Analyzer {
        LuminosityListener mLuminosityListener;

        public LuminosityAnalyzer(LuminosityListener mLuminosityListener) {
            this.mLuminosityListener = mLuminosityListener;
        }

        @Override
        public void analyze(@NonNull ImageProxy image) {
            ByteBuffer buffer = image.getPlanes()[0].getBuffer();
            byte[] data = toByteArray(buffer);

            int total = 0;
            for (byte i: data) {
                 total += Byte.toUnsignedInt(i);
            }

            mLuminosityListener.onLuminosity(total/data.length);

            image.close();
        }

        private byte[] toByteArray(ByteBuffer byteBuffer)  {
            byteBuffer.rewind();    // Rewind the buffer to zero
            byte[] data = new byte[byteBuffer.remaining()];
            byteBuffer.get(data);   // Copy the buffer into a byte array
            return data; // Return the byte array
        }
    }

    private interface LuminosityListener {
        void onLuminosity(int luminosity);
    }
}
