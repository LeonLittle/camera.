package com.leonlittle.t10smonitor;

import java.io.File;
import java.util.Arrays;
import java.util.Comparator;

public final class StorageRetention {
    private StorageRetention() { }

    public static void prune(File directory, long now, long maxAgeMs, long maxBytes) {
        if (!directory.exists() && !directory.mkdirs()) return;
        File[] files = directory.listFiles(File::isFile);
        if (files == null) return;

        for (File file : files) {
            if (now - file.lastModified() > maxAgeMs) file.delete();
        }

        files = directory.listFiles(File::isFile);
        if (files == null) return;
        Arrays.sort(files, Comparator.comparingLong(File::lastModified));
        long total = 0L;
        for (File file : files) total += file.length();
        for (File file : files) {
            if (total <= maxBytes) break;
            long length = file.length();
            if (file.delete()) total -= length;
        }
    }
}
