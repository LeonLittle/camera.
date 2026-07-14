import com.leonlittle.t10smonitor.StorageRetention;

import java.io.File;
import java.io.FileOutputStream;
import java.nio.file.Files;

public final class StorageRetentionTest {
    public static void main(String[] args) throws Exception {
        File directory = Files.createTempDirectory("retention-test").toFile();
        long now = 2_000_000_000L;
        File expired = create(directory, "expired.mp4", 20, now - 1000);
        File oldest = create(directory, "oldest.mp4", 60, now - 100);
        File newest = create(directory, "newest.mp4", 60, now - 10);

        StorageRetention.prune(directory, now, 500, 60);
        if (expired.exists()) throw new AssertionError("Expired file was retained");
        if (oldest.exists()) throw new AssertionError("Oldest file was retained over quota");
        if (!newest.exists()) throw new AssertionError("Newest file was removed");
        System.out.println("StorageRetentionTest passed");
    }

    private static File create(File directory, String name, int bytes, long modified) throws Exception {
        File file = new File(directory, name);
        try (FileOutputStream output = new FileOutputStream(file)) {
            output.write(new byte[bytes]);
        }
        if (!file.setLastModified(modified)) throw new AssertionError("Cannot set mtime");
        return file;
    }
}
