package com.leonlittle.t10smonitor;

import android.app.Service;
import android.content.Intent;
import android.os.IBinder;
import android.os.PowerManager;
import android.util.Log;

import java.io.File;

public final class MonitorService extends Service {
    private static final String TAG = "T10sMonitor";
    private static final long RETENTION_MS = 24L * 60L * 60L * 1000L;
    private PowerManager.WakeLock wakeLock;
    private CameraController camera;
    private MjpegServer server;

    @Override
    public void onCreate() {
        super.onCreate();
        PowerManager manager = (PowerManager) getSystemService(POWER_SERVICE);
        wakeLock = manager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "T10sMonitor:camera");
        wakeLock.acquire();

        File externalDirectory = getExternalFilesDir(null);
        File recordingDirectory = new File(
                externalDirectory != null ? externalDirectory : getFilesDir(),
                "recordings");
        if (!recordingDirectory.exists()) recordingDirectory.mkdirs();
        // The hardware-validation build reserves half of available space and always
        // removes files older than 24 hours before starting the camera.
        long maxBytes = Math.max(512L * 1024L * 1024L, recordingDirectory.getUsableSpace() / 2L);
        StorageRetention.prune(recordingDirectory, System.currentTimeMillis(), RETENTION_MS, maxBytes);

        server = new MjpegServer(8080);
        server.start();
        CodecDiagnostics.logAvcEncoders();
        camera = new CameraController(server::publish);
        try {
            camera.start();
            Log.i(TAG, "Camera service started");
        } catch (RuntimeException error) {
            Log.e(TAG, "Unable to start camera", error);
            stopSelf();
        }
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        return START_STICKY;
    }

    @Override
    public void onDestroy() {
        if (camera != null) camera.close();
        if (server != null) server.close();
        if (wakeLock != null && wakeLock.isHeld()) wakeLock.release();
        super.onDestroy();
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
}
