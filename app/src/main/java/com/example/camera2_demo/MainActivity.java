package com.example.camera2_demo;

import android.Manifest;
import android.content.pm.PackageManager;
import android.graphics.Matrix;
import android.graphics.RectF;
import android.graphics.SurfaceTexture;
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraCaptureSession;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraDevice;
import android.hardware.camera2.CameraManager;
import android.hardware.camera2.CaptureRequest;
import android.hardware.camera2.params.StreamConfigurationMap;
import android.os.Bundle;
import android.os.Handler;
import android.os.HandlerThread;
import android.util.Size;
import android.view.Surface;
import android.view.TextureView;
import android.widget.ImageView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import butterknife.BindView;
import butterknife.ButterKnife;
import butterknife.OnClick;
import pub.devrel.easypermissions.AfterPermissionGranted;
import pub.devrel.easypermissions.EasyPermissions;

public class MainActivity extends AppCompatActivity {
    private static final int ALL_PERMISSION = 100;
    @BindView(R.id.preview_texture)
    TextureView previewTexture;
    @BindView(R.id.convert_img)
    ImageView convertImg;

    private String mCameraId;
    private Size mPreviewSize;
    private HandlerThread mCameraThread;
    private Handler mCameraHandler;
    private CameraDevice mCameraDevice;
    private CaptureRequest.Builder mCaptureRequestBuilder;
    private CaptureRequest mCaptureRequest;
    private CameraCaptureSession mCameraCaptureSession;

    /**
     * 前后置摄像头
     */
    public static final String CAMERA_FRONT = "1";
    public static final String CAMERA_BACK = "0";


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        ButterKnife.bind(this);
        //获取相机权限
        methodRequiresPermisson();
    }

    @Override
    protected void onResume() {
        super.onResume();
        startCameraThread();
        if (!previewTexture.isAvailable()) {
            previewTexture.setSurfaceTextureListener(textureListener);
        } else {
            startPreview();
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (mCameraDevice != null) {
            mCameraDevice.close();
        }
        if (mCameraCaptureSession != null) {
            mCameraCaptureSession = null;
        }

    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        EasyPermissions.onRequestPermissionsResult(requestCode, permissions, grantResults, this);
    }


    @AfterPermissionGranted(ALL_PERMISSION)
    private void methodRequiresPermisson() {
        String[] perms = {Manifest.permission.CAMERA, Manifest.permission.RECORD_AUDIO, Manifest.permission.READ_EXTERNAL_STORAGE, Manifest.permission.WRITE_EXTERNAL_STORAGE};
        if (EasyPermissions.hasPermissions(this,perms)) {
            Toast.makeText(this, "授权成功", Toast.LENGTH_SHORT).show();
        } else {
            EasyPermissions.requestPermissions(this, "请授权允许打开相机权限",
                    ALL_PERMISSION, perms);
        }
    }


    private void startCameraThread() {
        mCameraThread = new HandlerThread("CameraTextureViewThread");
        mCameraThread.start();
        mCameraHandler = new Handler(mCameraThread.getLooper());
    }

    TextureView.SurfaceTextureListener textureListener = new TextureView.SurfaceTextureListener() {
        @Override
        public void onSurfaceTextureAvailable(SurfaceTexture surfaceTexture, int width, int height) {
            setupCamera(width, height);
            transformImage(width, height);
            openCamera();
        }

        @Override
        public void onSurfaceTextureSizeChanged(SurfaceTexture surfaceTexture, int width, int height) {

        }

        @Override
        public boolean onSurfaceTextureDestroyed(SurfaceTexture surfaceTexture) {
            return false;
        }

        @Override
        public void onSurfaceTextureUpdated(SurfaceTexture surfaceTexture) {

        }
    };

    @OnClick(R.id.convert_img)
    public void onViewClicked() {
        switchCamera();
    }

    private void switchCamera() {
        if (mCameraId.equals(CAMERA_FRONT)) {
            mCameraId = CAMERA_BACK;
            mCameraDevice.close();
            reopenCamera();
        } else if (mCameraId.equals(CAMERA_BACK)) {
            mCameraId = CAMERA_FRONT;
            mCameraDevice.close();
            reopenCamera();
        }
    }

    private void reopenCamera() {
        if (!previewTexture.isAvailable()) {
            openCamera();
        } else {
            previewTexture.setSurfaceTextureListener(textureListener);
        }
    }


    private void setupCamera(int width, int height) {
        CameraManager cameraManager = (CameraManager) getSystemService(CAMERA_SERVICE);
        try {
            for (String cameraId : cameraManager.getCameraIdList()) {
                CameraCharacteristics cameraCharacteristics = cameraManager.getCameraCharacteristics(cameraId);
                //默认打开前置摄像机
                Integer facing = cameraCharacteristics.get(CameraCharacteristics.LENS_FACING);

                if (facing != null && facing == CameraCharacteristics.LENS_FACING_BACK)
                    continue;

                //获取图片输出的尺寸
                StreamConfigurationMap map = cameraCharacteristics.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP);
                assert map != null;

                //摄像头支持的预览Size数组
                mPreviewSize = getOptimalSize(map.getOutputSizes(SurfaceTexture.class), width, height);
                mCameraId = cameraId;
            }
        } catch (CameraAccessException e) {
            e.printStackTrace();
        }
    }


    private Size getOptimalSize(Size[] sizeMap, int width, int height) {
        List<Size> sizeList = new ArrayList<>();
        for (Size option : sizeMap) {
            if (width > height) {
                if (option.getWidth() > width && option.getHeight() > height) {
                    sizeList.add(option);
                }
            } else {
                if (option.getWidth() > height && option.getHeight() > width) {
                    sizeList.add(option);
                }
            }
        }
        if (sizeList.size() > 0) {
            return Collections.min(sizeList, (lhs, rhs) -> Long.signum(lhs.getWidth() * lhs.getHeight() - rhs.getHeight() * rhs.getWidth()));
        }
        return sizeMap[0];
    }


    private void openCamera() {
        CameraManager cameraManager = (CameraManager) getSystemService(CAMERA_SERVICE);
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            // TODO: Consider calling
            //    ActivityCompat#requestPermissions
            // here to request the missing permissions, and then overriding
            //   public void onRequestPermissionsResult(int requestCode, String[] permissions,
            //                                          int[] grantResults)
            // to handle the case where the user grants the permission. See the documentation
            // for ActivityCompat#requestPermissions for more details.
            return;
        }
            try {
                cameraManager.openCamera(mCameraId, mStateCallback, mCameraHandler);
            } catch (Exception e) {
                e.printStackTrace();
            }
        }

        private CameraDevice.StateCallback mStateCallback = new CameraDevice.StateCallback() {
            @Override
            public void onOpened(@NonNull CameraDevice cameraDevice) {
                mCameraDevice = cameraDevice;
                startPreview();
            }

            @Override
            public void onDisconnected(@NonNull CameraDevice cameraDevice) {
                if (mCameraDevice != null) {
                    cameraDevice.close();
                    cameraDevice.close();
                    mCameraDevice = null;
                }
            }

            @Override
            public void onError(@NonNull CameraDevice cameraDevice, int i) {
                if (mCameraDevice != null) {
                    mCameraDevice.close();
                    cameraDevice.close();
                    mCameraDevice = null;
                }
            }
        };

        private void startPreview () {
            SurfaceTexture mSurfaceTexture = previewTexture.getSurfaceTexture();
            mSurfaceTexture.setDefaultBufferSize(mPreviewSize.getWidth(), mPreviewSize.getHeight());
            Surface previewSurface = new Surface(mSurfaceTexture);

            try {
                mCaptureRequestBuilder = mCameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW);
                mCaptureRequestBuilder.addTarget(previewSurface);
                mCameraDevice.createCaptureSession(Arrays.asList(previewSurface), new CameraCaptureSession.StateCallback() {
                    @Override
                    public void onConfigured(@NonNull CameraCaptureSession cameraCaptureSession) {
                        mCaptureRequest = mCaptureRequestBuilder.build();
                        mCameraCaptureSession = cameraCaptureSession;

                        try {
                            mCameraCaptureSession.setRepeatingRequest(mCaptureRequest, null, mCameraHandler);
                        } catch (CameraAccessException e) {
                            e.printStackTrace();
                        }
                    }

                    @Override
                    public void onConfigureFailed(@NonNull CameraCaptureSession cameraCaptureSession) {
                    }
                }, mCameraHandler);
            } catch (CameraAccessException e) {
                e.printStackTrace();
            }
        }

        /**
         * 旋转角度
         *
         * @param width
         * @param height
         */
        private void transformImage ( int width, int height){
            if (mPreviewSize == null || previewTexture == null) {
                return;
            }
            Matrix matrix = new Matrix();
            int rotation = getWindowManager().getDefaultDisplay().getRotation();
            RectF textureRectF = new RectF(0, 0, width, height);
            RectF previewRectF = new RectF(0, 0, mPreviewSize.getHeight(), mPreviewSize.getWidth());
            float centerX = textureRectF.centerX();
            float centery = textureRectF.centerY();

            if (rotation == Surface.ROTATION_0 || rotation == Surface.ROTATION_270) {
            } else if (rotation == Surface.ROTATION_90 || rotation == Surface.ROTATION_270) {
                previewRectF.offset(centerX - previewRectF.centerX(), centery - previewRectF.centerY());
                matrix.setRectToRect(textureRectF, previewRectF, Matrix.ScaleToFit.FILL);
                float scale = Math.max((float) width / mPreviewSize.getWidth(), (float) height / mPreviewSize.getHeight());

                matrix.postScale(scale, scale, centerX, centery);
                matrix.postRotate(90 * (rotation - 2), centerX, centery);
                previewTexture.setTransform(matrix);

            }
        }

    }
