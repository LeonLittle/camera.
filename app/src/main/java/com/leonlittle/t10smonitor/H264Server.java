package com.leonlittle.t10smonitor;

import android.media.MediaCodec;
import android.util.Log;

import java.io.BufferedOutputStream;
import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

final class H264Server implements AutoCloseable {
    private static final String TAG = "T10sH264Server";
    private static final byte[] HTTP_HEADER = ("HTTP/1.1 200 OK\r\n"
            + "Content-Type: video/h264\r\nCache-Control: no-store\r\n"
            + "Connection: close\r\n\r\n").getBytes(StandardCharsets.US_ASCII);

    private final int port;
    private final ExecutorService executor = Executors.newCachedThreadPool();
    private final CopyOnWriteArrayList<Client> clients = new CopyOnWriteArrayList<>();
    private volatile boolean running;
    private volatile byte[] codecConfig;
    private ServerSocket server;

    H264Server(int port) { this.port = port; }

    void start() {
        running = true;
        executor.execute(() -> {
            try {
                server = new ServerSocket(port);
                while (running) {
                    Client client = new Client(server.accept());
                    clients.add(client);
                    executor.execute(client);
                }
            } catch (IOException error) {
                if (running) Log.e(TAG, "H264 server stopped", error);
            }
        });
    }

    void publish(byte[] encoded, int flags) {
        if ((flags & MediaCodec.BUFFER_FLAG_CODEC_CONFIG) != 0) codecConfig = encoded.clone();
        for (Client client : clients) client.offer(encoded);
    }

    private final class Client implements Runnable {
        private final Socket socket;
        private final ArrayBlockingQueue<byte[]> queue = new ArrayBlockingQueue<>(30);

        Client(Socket socket) { this.socket = socket; }

        void offer(byte[] data) {
            if (!queue.offer(data)) {
                queue.poll();
                queue.offer(data);
            }
        }

        @Override public void run() {
            try (Socket client = socket;
                 BufferedOutputStream output = new BufferedOutputStream(client.getOutputStream())) {
                client.setTcpNoDelay(true);
                client.setSendBufferSize(64 * 1024);
                output.write(HTTP_HEADER);
                byte[] config = codecConfig;
                if (config != null) output.write(config);
                output.flush();
                while (running && !client.isClosed()) {
                    byte[] frame = queue.take();
                    output.write(frame);
                    output.flush();
                }
            } catch (InterruptedException ignored) {
                Thread.currentThread().interrupt();
            } catch (IOException ignored) {
                // Player disconnects are expected.
            } finally {
                clients.remove(this);
            }
        }
    }

    @Override public void close() {
        running = false;
        if (server != null) try { server.close(); } catch (IOException ignored) { }
        for (Client client : clients) try { client.socket.close(); } catch (IOException ignored) { }
        clients.clear();
        executor.shutdownNow();
    }
}
