package com.leonlittle.t10smonitor;

import android.Manifest;
import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.widget.Button;
import android.widget.TextView;

public final class MainActivity extends Activity {
    private static final int REQUEST_PERMISSIONS = 100;
    private TextView status;

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        setContentView(R.layout.activity_main);
        status = findViewById(R.id.status);
        Button start = findViewById(R.id.start);
        start.setOnClickListener(view -> ensurePermissionsAndStart());
        ensurePermissionsAndStart();
    }

    private void ensurePermissionsAndStart() {
        String[] permissions = {
                Manifest.permission.CAMERA,
                Manifest.permission.RECORD_AUDIO,
                Manifest.permission.WRITE_EXTERNAL_STORAGE
        };
        for (String permission : permissions) {
            if (checkSelfPermission(permission) != PackageManager.PERMISSION_GRANTED) {
                requestPermissions(permissions, REQUEST_PERMISSIONS);
                status.setText("请授予摄像头、麦克风和存储权限");
                return;
            }
        }
        startService(new Intent(this, MonitorService.class));
        String ip = NetworkUtils.findWifiIpv4Address();
        String address = ip == null
                ? "Wi-Fi连接后将显示地址"
                : "网页回退：http://" + ip + ":8080/\n"
                + "H.264测试：http://" + ip + ":8081/live.h264";
        status.setText("监控服务已启动（H.264兼容测试版 v0.6）\n" + address);
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] results) {
        super.onRequestPermissionsResult(requestCode, permissions, results);
        if (requestCode == REQUEST_PERMISSIONS) {
            ensurePermissionsAndStart();
        }
    }
}
