package com.leonlittle.t10smonitor;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

public final class BootReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
        if (Intent.ACTION_BOOT_COMPLETED.equals(intent.getAction())) {
            Intent service = new Intent(context, MonitorService.class);
            service.putExtra(MonitorService.EXTRA_SHOW_UI_AFTER_BOOT, true);
            context.startService(service);
        }
    }
}
