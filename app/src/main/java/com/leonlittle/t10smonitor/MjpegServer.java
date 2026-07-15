package com.leonlittle.t10smonitor;

import android.util.Log;

import java.io.BufferedOutputStream;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicReference;

final class MjpegServer implements AutoCloseable {
    private static final String TAG = "T10sMjpeg";
    private static final byte[] HEADER = ("HTTP/1.1 200 OK\r\n"
            + "Cache-Control: no-store, no-cache, must-revalidate, max-age=0\r\n"
            + "Pragma: no-cache\r\n"
            + "Expires: 0\r\n"
            + "X-Accel-Buffering: no\r\n"
            + "Connection: close\r\n"
            + "Content-Type: multipart/x-mixed-replace; boundary=frame\r\n\r\n")
            .getBytes(StandardCharsets.US_ASCII);
    private static final byte[] VIEWER = ("<!doctype html><html><head>"
            + "<meta name=viewport content='width=device-width,initial-scale=1'>"
            + "<title>T10s 家庭监控</title><style>html,body{margin:0;background:#000;height:100%;}"
            + "img{display:block;width:100%;height:100%;object-fit:contain;}</style></head>"
            + "<body><img id=v alt='正在连接摄像头'><script>"
            + "const v=document.getElementById('v');"
            + "function next(delay){setTimeout(()=>{v.src='/snapshot.jpg?t='+Date.now()},delay)}"
            + "v.onload=()=>next(60);v.onerror=()=>next(500);next(0);"
            + "</script></body></html>").getBytes(StandardCharsets.UTF_8);
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
        try (Socket client = socket) {
            client.setTcpNoDelay(true);
            client.setSendBufferSize(64 * 1024);
            BufferedReader input = new BufferedReader(new InputStreamReader(
                    client.getInputStream(), StandardCharsets.US_ASCII));
            String request = input.readLine();
            if (request == null) return;
            String path = request.split(" ").length > 1 ? request.split(" ")[1] : "/";
            // Consume the complete request before replying. Closing a socket with unread
            // request headers can reset the connection and make browsers back off for seconds.
            String headerLine;
            while ((headerLine = input.readLine()) != null && !headerLine.isEmpty()) { }
            BufferedOutputStream output = new BufferedOutputStream(client.getOutputStream());
            if (path.startsWith("/snapshot.jpg")) {
                serveSnapshot(output);
            } else if (path.startsWith("/live.mjpg")) {
                serveMjpeg(client, output);
            } else {
                serveViewer(output);
            }
        } catch (InterruptedException ignored) {
            Thread.currentThread().interrupt();
        } catch (IOException ignored) {
            // A disconnected viewer is normal and must not stop the camera service.
        }
    }

    private void serveViewer(BufferedOutputStream output) throws IOException {
        output.write(("HTTP/1.1 200 OK\r\nContent-Type: text/html; charset=utf-8\r\n"
                + "Cache-Control: no-store\r\nConnection: close\r\nContent-Length: "
                + VIEWER.length + "\r\n\r\n").getBytes(StandardCharsets.US_ASCII));
        output.write(VIEWER);
        output.flush();
    }

    private void serveSnapshot(BufferedOutputStream output) throws IOException, InterruptedException {
        byte[] frame = latest.get();
        for (int attempt = 0; frame == null && attempt < 40; attempt++) {
            Thread.sleep(25L);
            frame = latest.get();
        }
        if (frame == null) {
            output.write("HTTP/1.1 503 Service Unavailable\r\nConnection: close\r\n\r\n"
                    .getBytes(StandardCharsets.US_ASCII));
            output.flush();
            return;
        }
        output.write(("HTTP/1.1 200 OK\r\nContent-Type: image/jpeg\r\nContent-Length: "
                + frame.length + "\r\nCache-Control: no-store, no-cache, max-age=0\r\n"
                + "Connection: close\r\n\r\n").getBytes(StandardCharsets.US_ASCII));
        output.write(frame);
        output.flush();
    }

    private void serveMjpeg(Socket client, BufferedOutputStream output)
            throws IOException, InterruptedException {
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
    }

    @Override
    public void close() {
        running = false;
        if (server != null) try { server.close(); } catch (IOException ignored) { }
        clients.shutdownNow();
    }
}
