package com.leonlittle.t10smonitor;

import android.media.MediaCodec;
import android.media.MediaCodecInfo;
import android.media.MediaFormat;
import android.util.Log;

import java.nio.ByteBuffer;
import java.io.ByteArrayOutputStream;

final class H264Encoder implements AutoCloseable {
    private static final String TAG = "T10sH264";
    private static final String CODEC_NAME = "OMX.MTK.VIDEO.ENCODER.AVC";
    private static final int BIT_RATE = 600_000;
    private static final int FRAME_RATE = 10;

    private final int width;
    private final int height;
    private final CameraController.EncodedFrameSink sink;
    private MediaCodec codec;
    private int colorFormat;
    private long lastFrameUs;

    H264Encoder(int width, int height, CameraController.EncodedFrameSink sink) {
        if (width > 720 || height > 480 || width % 16 != 0 || height % 16 != 0) {
            throw new IllegalArgumentException("MTK AVC requires <=720x480 and 16-pixel alignment: "
                    + width + "x" + height);
        }
        this.width = width;
        this.height = height;
        this.sink = sink;
    }

    void start() {
        try {
            codec = MediaCodec.createByCodecName(CODEC_NAME);
            MediaCodecInfo.CodecCapabilities capabilities = codec.getCodecInfo()
                    .getCapabilitiesForType(MediaFormat.MIMETYPE_VIDEO_AVC);
            colorFormat = chooseColorFormat(capabilities.colorFormats);
            MediaFormat format = MediaFormat.createVideoFormat(
                    MediaFormat.MIMETYPE_VIDEO_AVC, width, height);
            format.setInteger(MediaFormat.KEY_COLOR_FORMAT, colorFormat);
            format.setInteger(MediaFormat.KEY_BIT_RATE, BIT_RATE);
            format.setInteger(MediaFormat.KEY_FRAME_RATE, FRAME_RATE);
            format.setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, 1);
            codec.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE);
            codec.start();
            Log.i(TAG, "Started " + CODEC_NAME + " " + width + "x" + height
                    + " color=" + colorFormat + " bitrate=" + BIT_RATE);
        } catch (Exception error) {
            close();
            throw new IllegalStateException("Cannot start MTK AVC encoder", error);
        }
    }

    void offer(byte[] nv21, long presentationTimeUs) {
        if (codec == null || presentationTimeUs - lastFrameUs < 100_000L) return;
        lastFrameUs = presentationTimeUs;
        try {
            int inputIndex = codec.dequeueInputBuffer(0L);
            if (inputIndex >= 0) {
                ByteBuffer input = codec.getInputBuffer(inputIndex);
                if (input != null) {
                    input.clear();
                    writeYuv420(input, nv21);
                    codec.queueInputBuffer(inputIndex, 0, width * height * 3 / 2,
                            presentationTimeUs, 0);
                }
            }
            drain();
        } catch (RuntimeException error) {
            Log.e(TAG, "Dropping frame after encoder error", error);
        }
    }

    private void drain() {
        MediaCodec.BufferInfo info = new MediaCodec.BufferInfo();
        while (true) {
            int outputIndex = codec.dequeueOutputBuffer(info, 0L);
            if (outputIndex == MediaCodec.INFO_TRY_AGAIN_LATER) return;
            if (outputIndex == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                MediaFormat outputFormat = codec.getOutputFormat();
                Log.i(TAG, "Output format=" + outputFormat);
                publishCodecConfig(outputFormat);
                continue;
            }
            if (outputIndex < 0) continue;
            ByteBuffer output = codec.getOutputBuffer(outputIndex);
            if (output != null && info.size > 0) {
                output.position(info.offset);
                output.limit(info.offset + info.size);
                byte[] encoded = new byte[info.size];
                output.get(encoded);
                sink.accept(toAnnexB(encoded), info.flags);
            }
            codec.releaseOutputBuffer(outputIndex, false);
        }
    }

    private void publishCodecConfig(MediaFormat format) {
        ByteArrayOutputStream combined = new ByteArrayOutputStream(128);
        appendCodecBuffer(combined, format, "csd-0");
        appendCodecBuffer(combined, format, "csd-1");
        if (combined.size() > 0) {
            sink.accept(combined.toByteArray(), MediaCodec.BUFFER_FLAG_CODEC_CONFIG);
        }
    }

    private static void appendCodecBuffer(ByteArrayOutputStream target,
                                          MediaFormat format, String key) {
        if (!format.containsKey(key)) return;
        ByteBuffer source = format.getByteBuffer(key);
        if (source == null) return;
        ByteBuffer copy = source.duplicate();
        byte[] data = new byte[copy.remaining()];
        copy.get(data);
        byte[] annexB = toAnnexB(data);
        target.write(annexB, 0, annexB.length);
    }

    private static byte[] toAnnexB(byte[] data) {
        if (data.length < 4 || isStartCode(data, 0)) return data;
        ByteArrayOutputStream converted = new ByteArrayOutputStream(data.length + 16);
        int offset = 0;
        while (offset + 4 <= data.length) {
            int length = ((data[offset] & 0xff) << 24)
                    | ((data[offset + 1] & 0xff) << 16)
                    | ((data[offset + 2] & 0xff) << 8)
                    | (data[offset + 3] & 0xff);
            offset += 4;
            if (length <= 0 || offset + length > data.length) return data;
            converted.write(0);
            converted.write(0);
            converted.write(0);
            converted.write(1);
            converted.write(data, offset, length);
            offset += length;
        }
        return offset == data.length ? converted.toByteArray() : data;
    }

    private static boolean isStartCode(byte[] data, int offset) {
        return data.length - offset >= 4 && data[offset] == 0 && data[offset + 1] == 0
                && (data[offset + 2] == 1
                || (data[offset + 2] == 0 && data[offset + 3] == 1));
    }

    private void writeYuv420(ByteBuffer target, byte[] nv21) {
        int ySize = width * height;
        target.put(nv21, 0, ySize);
        if (colorFormat == MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420SemiPlanar) {
            for (int offset = ySize; offset < ySize + ySize / 2; offset += 2) {
                target.put(nv21[offset + 1]);
                target.put(nv21[offset]);
            }
        } else {
            for (int offset = ySize + 1; offset < ySize + ySize / 2; offset += 2) {
                target.put(nv21[offset]);
            }
            for (int offset = ySize; offset < ySize + ySize / 2; offset += 2) {
                target.put(nv21[offset]);
            }
        }
    }

    private static int chooseColorFormat(int[] formats) {
        for (int format : formats) {
            if (format == MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420SemiPlanar) return format;
        }
        for (int format : formats) {
            if (format == MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420Planar
                    || format == MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420Flexible) {
                return format;
            }
        }
        throw new IllegalStateException("MTK AVC exposes no byte-buffer YUV420 input");
    }

    @Override
    public void close() {
        if (codec != null) {
            try { codec.stop(); } catch (RuntimeException ignored) { }
            codec.release();
            codec = null;
        }
    }
}
