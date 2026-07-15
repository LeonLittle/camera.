package com.leonlittle.t10smonitor;

import android.media.MediaCodecInfo;
import android.media.MediaCodecList;
import android.util.Log;

final class CodecDiagnostics {
    private static final String TAG = "T10sCodec";

    private CodecDiagnostics() { }

    static void logAvcEncoders() {
        int count = MediaCodecList.getCodecCount();
        boolean found = false;
        for (int index = 0; index < count; index++) {
            MediaCodecInfo codec = MediaCodecList.getCodecInfoAt(index);
            if (!codec.isEncoder()) continue;
            for (String type : codec.getSupportedTypes()) {
                if (!"video/avc".equalsIgnoreCase(type)) continue;
                found = true;
                MediaCodecInfo.CodecCapabilities capabilities = codec.getCapabilitiesForType(type);
                StringBuilder colors = new StringBuilder();
                for (int color : capabilities.colorFormats) {
                    if (colors.length() > 0) colors.append(',');
                    colors.append(color);
                }
                Log.i(TAG, "H264 encoder=" + codec.getName() + ", colorFormats=" + colors);
            }
        }
        if (!found) Log.w(TAG, "No H264 encoder reported by MediaCodecList");
    }
}
