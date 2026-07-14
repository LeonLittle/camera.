package com.leonlittle.t10smonitor;

import android.graphics.ImageFormat;
import android.graphics.Rect;
import android.graphics.SurfaceTexture;
import android.graphics.YuvImage;
import android.hardware.Camera;
import android.util.Log;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.List;

@SuppressWarnings("deprecation")
final class CameraController implements AutoCloseable {
    interface FrameSink { void accept(byte[] jpeg); }

    private static final String TAG = "T10sCamera";
    private final FrameSink sink;
    private Camera camera;
    private SurfaceTexture dummyTexture;
    private long lastFrameAt;

    CameraController(FrameSink sink) {
        this.sink = sink;
    }

    void start() {
        int id = findBackCamera();
        camera = Camera.open(id);
        Camera.Parameters parameters = camera.getParameters();
        Camera.Size selected = choosePreviewSize(parameters.getSupportedPreviewSizes());
        parameters.setPreviewSize(selected.width, selected.height);
        parameters.setPreviewFormat(ImageFormat.NV21);
        camera.setParameters(parameters);
        Log.i(TAG, "Camera " + id + " preview " + selected.width + "x" + selected.height);

        dummyTexture = new SurfaceTexture(17);
        try {
            camera.setPreviewTexture(dummyTexture);
        } catch (IOException error) {
            throw new IllegalStateException("Cannot attach preview texture", error);
        }
        camera.setPreviewCallback((data, source) -> publishAtLimitedRate(data, source));
        camera.startPreview();
    }

    private void publishAtLimitedRate(byte[] nv21, Camera source) {
        long now = System.currentTimeMillis();
        if (now - lastFrameAt < 200L) return; // 5 fps for the first network test.
        lastFrameAt = now;
        Camera.Size size = source.getParameters().getPreviewSize();
        YuvImage image = new YuvImage(nv21, ImageFormat.NV21, size.width, size.height, null);
        ByteArrayOutputStream output = new ByteArrayOutputStream(256 * 1024);
        if (image.compressToJpeg(new Rect(0, 0, size.width, size.height), 70, output)) {
            sink.accept(output.toByteArray());
        }
    }

    private static int findBackCamera() {
        Camera.CameraInfo info = new Camera.CameraInfo();
        for (int id = 0; id < Camera.getNumberOfCameras(); id++) {
            Camera.getCameraInfo(id, info);
            if (info.facing == Camera.CameraInfo.CAMERA_FACING_BACK) return id;
        }
        if (Camera.getNumberOfCameras() == 0) throw new IllegalStateException("No camera detected");
        return 0;
    }

    private static Camera.Size choosePreviewSize(List<Camera.Size> sizes) {
        Camera.Size best = sizes.get(0);
        long targetPixels = 1280L * 720L;
        for (Camera.Size size : sizes) {
            if (Math.abs((long) size.width * size.height - targetPixels)
                    < Math.abs((long) best.width * best.height - targetPixels)) {
                best = size;
            }
        }
        return best;
    }

    @Override
    public void close() {
        if (camera != null) {
            camera.setPreviewCallback(null);
            camera.stopPreview();
            camera.release();
            camera = null;
        }
        if (dummyTexture != null) {
            dummyTexture.release();
            dummyTexture = null;
        }
    }
}
