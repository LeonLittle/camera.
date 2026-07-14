package com.leonlittle.t10smonitor;

import android.util.Log;

import java.io.BufferedOutputStream;
import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicReference;

final class MjpegServer implements AutoCloseable {
    private static final String TAG = "T10sMjpeg";
    private static final byte[] HEADER = ("HTTP/1.1 200 OK\r\n"
            + "Cache-Control: no-store\r\n"
            + "Connection: close\r\n"
            + "Content-Type: multipart/x-mixed-replace; boundary=frame\r\n\r\n")
            .getBytes(StandardCharsets.US_ASCII);
    private final int port;
    private final AtomicReference<byte[]> latest = new AtomicReference<>();
    private final ExecutorService clients = Executors.newCachedThreadPool();
    private volatile boolean running;
    private ServerSocket server;

    MjpegServer(int port) {
        this.port = port;
    }

    void publish(byte[] jpeg) {
        latest.set(jpeg);
    }

    void start() {
        running = true;
        clients.execute(() -> {
            try {
                server = new ServerSocket(port);
                while (running) {
                    Socket socket = server.accept();
                    clients.execute(() -> serve(socket));
                }
            } catch (IOException error) {
                if (running) Log.e(TAG, "Server stopped unexpectedly", error);
            }
        });
    }

    private void serve(Socket socket) {
        try (Socket client = socket;
             BufferedOutputStream output = new BufferedOutputStream(client.getOutputStream())) {
            client.setTcpNoDelay(true);
            output.write(HEADER);
            byte[] previous = null;
            while (running && !client.isClosed()) {
                byte[] frame = latest.get();
                if (frame == null || frame == previous) {
                    Thread.sleep(25L);
                    continue;
                }
                previous = frame;
                output.write(("--frame\r\nContent-Type: image/jpeg\r\nContent-Length: "
                        + frame.length + "\r\n\r\n").getBytes(StandardCharsets.US_ASCII));
                output.write(frame);
                output.write("\r\n".getBytes(StandardCharsets.US_ASCII));
                output.flush();
            }
        } catch (InterruptedException ignored) {
            Thread.currentThread().interrupt();
        } catch (IOException ignored) {
            // A disconnected viewer is normal and must not stop the camera service.
        }
    }

    @Override
    public void close() {
        running = false;
        if (server != null) try { server.close(); } catch (IOException ignored) { }
        clients.shutdownNow();
    }
}
