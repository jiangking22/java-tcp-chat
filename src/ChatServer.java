import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * TCP chat with one handler thread per connection and a local HTTP metrics endpoint.
 */
public class ChatServer {
    private static final String LOOPBACK = "127.0.0.1";

    // Shared list of all connected clients' handlers.
    // CopyOnWriteArrayList is thread-safe for this "many threads read/write
    // occasionally" pattern -- safe to iterate even while another thread
    // adds/removes an entry, without needing manual synchronized blocks.
    private static final List<ClientHandler> clients = new CopyOnWriteArrayList<>();

    private static volatile boolean accepting = false;

    public static void main(String[] args) throws IOException {
        String host = System.getenv().getOrDefault("CHAT_HOST", LOOPBACK).trim();
        if (host.isEmpty()) {
            host = LOOPBACK;
        }
        run(host, envPort("CHAT_PORT", 5000), envPort("METRICS_PORT", 9100));
    }

    /** Runs the server until the process exits. Separate from main() so tests can pick ports. */
    public static void run(int port, int metricsPort) throws IOException {
        run(LOOPBACK, port, metricsPort);
    }

    private static void run(String host, int port, int metricsPort) throws IOException {
        try (ServerSocket serverSocket = new ServerSocket()) {
            serverSocket.bind(new InetSocketAddress(host, port));
            MetricsServer metrics = new MetricsServer(metricsPort, () -> accepting);
            try {
                metrics.start();
                System.out.println("Metrics on http://" + LOOPBACK + ":" + metrics.port() + "/metrics");
                System.out.println("Chat server listening on port " + serverSocket.getLocalPort()
                        + " (" + host + ").");
                accepting = true;

                while (true) {
                    Socket clientSocket = serverSocket.accept();
                    Metrics.CONNECTIONS_ACCEPTED.inc();
                    ClientHandler handler = new ClientHandler(clientSocket, clients);
                    clients.add(handler);
                    new Thread(handler, "chat-client-" + clientSocket.getPort()).start();
                }
            } finally {
                accepting = false;
                metrics.stop();
            }
        }
    }

    private static int envPort(String name, int fallback) {
        String v = System.getenv(name);
        if (v == null || v.isBlank()) return fallback;
        try {
            int port = Integer.parseInt(v.trim());
            if (port >= 0 && port <= 65535) {
                return port;
            }
        } catch (NumberFormatException e) {
            // Use the same message for out-of-range ports and non-numeric values.
        }
        throw new IllegalArgumentException(name + " must be an integer from 0 to 65535");
    }
}

