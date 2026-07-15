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
    private static final long FRAME_INTERVAL_MS = 100L;
    private static final int JPEG_QUALITY = 80;
    private static final int TARGET_WIDTH = 1920;
    private static final int TARGET_HEIGHT = 1080;
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
        int[] fpsRange = chooseFpsRange(parameters.getSupportedPreviewFpsRange());
        if (fpsRange != null) parameters.setPreviewFpsRange(fpsRange[0], fpsRange[1]);
        camera.setParameters(parameters);
        Log.i(TAG, "Camera " + id + " preview " + selected.width + "x" + selected.height
                + ", JPEG quality=" + JPEG_QUALITY
                + (fpsRange == null ? "" : ", HAL fps=" + fpsRange[0] / 1000f + "-" + fpsRange[1] / 1000f));

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
        if (now - lastFrameAt < FRAME_INTERVAL_MS) return;
        lastFrameAt = now;
        Camera.Size size = source.getParameters().getPreviewSize();
        YuvImage image = new YuvImage(nv21, ImageFormat.NV21, size.width, size.height, null);
        ByteArrayOutputStream output = new ByteArrayOutputStream(512 * 1024);
        if (image.compressToJpeg(new Rect(0, 0, size.width, size.height), JPEG_QUALITY, output)) {
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
        double bestScore = score(best);
        for (Camera.Size size : sizes) {
            double score = score(size);
            if (score < bestScore) {
                best = size;
                bestScore = score;
            }
        }
        return best;
    }

    private static double score(Camera.Size size) {
        long targetPixels = (long) TARGET_WIDTH * TARGET_HEIGHT;
        long pixels = (long) size.width * size.height;
        double pixelDifference = Math.abs(pixels - targetPixels) / (double) targetPixels;
        double targetRatio = TARGET_WIDTH / (double) TARGET_HEIGHT;
        double ratioDifference = Math.abs(size.width / (double) size.height - targetRatio);
        // Prefer 16:9 strongly, so a 4:3 mode with similar pixels is not selected.
        return pixelDifference + ratioDifference * 3.0;
    }

    private static int[] chooseFpsRange(List<int[]> ranges) {
        if (ranges == null || ranges.isEmpty()) return null;
        int[] best = ranges.get(0);
        int target = 15_000;
        long bestScore = Long.MAX_VALUE;
        for (int[] range : ranges) {
            long containsPenalty = range[0] <= target && range[1] >= target ? 0 : 1_000_000L;
            long score = containsPenalty + Math.abs(range[1] - target) + Math.abs(range[0] - target) / 2L;
            if (score < bestScore) {
                best = range;
                bestScore = score;
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
