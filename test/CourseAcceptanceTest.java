import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.net.ConnectException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.concurrent.TimeUnit;

public final class CourseAcceptanceTest {
    private static final String LOOPBACK = "127.0.0.1";
    private static final String ROOM = "course-main";
    private static final String TEST_PASSWORD = "course-test-password";
    private static final int TIMEOUT_MILLIS = 5000;
    private static final String FRAME_ERROR = "Error: frame exceeds 4096 UTF-8 bytes.";
    private static final String INVALID_UTF8_ERROR = "Error: invalid UTF-8 frame.";
    private static final String BARE_CR_ERROR = "Error: unexpected carriage return in frame.";

    public static void main(String[] args) throws Exception {
        int port;
        try (ServerSocket reservation = new ServerSocket(0, 1, InetAddress.getByName(LOOPBACK))) {
            port = reservation.getLocalPort();
        }

        Process server = startServer(port);
        try (Peer alice = awaitServer(server, port);
                Peer bob = new Peer(port);
                Peer carol = new Peer(port)) {
            alice.expectLine("Enter your username:");
            alice.sendLine("two words");
            alice.expectLine("Username must be one word. Try again.");
            alice.register("alice");
            bob.register("bob");
            carol.register("carol");
            verifyRoomIsolation(alice, bob, carol);
            verifyFrameLimit(alice, bob, carol, port);
            System.out.println("Course acceptance scenarios passed.");
        } finally {
            server.destroy();
            if (!server.waitFor(2, TimeUnit.SECONDS)) {
                server.destroyForcibly();
                if (!server.waitFor(2, TimeUnit.SECONDS)) {
                    throw new IllegalStateException("Could not stop the test server");
                }
            }
        }
    }

    private static Process startServer(int port) throws IOException {
        String java = Path.of(System.getProperty("java.home"), "bin", "java").toString();
        ProcessBuilder launch = new ProcessBuilder(
                java, "-cp", System.getProperty("java.class.path"), "ChatServer");
        launch.environment().put("CHAT_HOST", LOOPBACK);
        launch.environment().put("CHAT_PORT", Integer.toString(port));
        launch.environment().put("METRICS_PORT", "0");
        launch.environment().put("CHAT_LOG_MESSAGES", "false");
        return launch.inheritIO().start();
    }

    private static Peer awaitServer(Process server, int port) throws IOException, InterruptedException {
        long deadline = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(TIMEOUT_MILLIS);
        while (System.nanoTime() < deadline) {
            if (!server.isAlive()) {
                throw new IllegalStateException("Test server exited with status " + server.exitValue());
            }
            try {
                return new Peer(port);
            } catch (ConnectException e) {
                Thread.sleep(25);
            }
        }
        throw new IllegalStateException("Test server did not start on port " + port);
    }

    private static void verifyRoomIsolation(Peer alice, Peer bob, Peer carol) throws IOException {
        alice.join(ROOM);
        bob.join(ROOM);
        carol.join("course-side");

        String isolated = "你好，只有同房间能收到 🌟";
        alice.sendLine(isolated);
        bob.expectLine("alice: " + isolated);
        // The sender processes this command after completing the previous broadcast.
        alice.sendLine("/join " + ROOM);
        alice.expectLine("You are already in room: " + ROOM);

        carol.join(ROOM, "alice: " + isolated);
        String shared = "切换房间后可以收到消息 🎉";
        alice.sendLine(shared);
        bob.expectLine("alice: " + shared);
        carol.expectLineBeforeLeak("alice: " + shared, "alice: " + isolated);
        System.out.println("PASS: Unicode delivery, room isolation and room switching.");
    }

    private static void verifyFrameLimit(Peer alice, Peer bob, Peer carol, int port) throws IOException {
        String maxFrame = "汉".repeat(1364) + "🐱";
        String oversizedFrame = maxFrame + "X";
        if (maxFrame.getBytes(StandardCharsets.UTF_8).length != 4096
                || oversizedFrame.getBytes(StandardCharsets.UTF_8).length != 4097) {
            throw new AssertionError("Incorrect UTF-8 boundary fixtures");
        }

        alice.send(maxFrame + "\r\n");
        bob.expectLine("alice: " + maxFrame);
        carol.expectLine("alice: " + maxFrame);

        carol.send(oversizedFrame);
        carol.expectLine(FRAME_ERROR);
        carol.expectEof();
        alice.expectLine("carol has left the chat");
        alice.sendLine("超长帧被拒绝后继续聊天");
        bob.expectLineBeforeLeak("alice: 超长帧被拒绝后继续聊天", "carol: " + oversizedFrame);

        try (Peer reconnected = new Peer(port)) {
            reconnected.login("CAROL");
            reconnected.join(ROOM);
            reconnected.sendLine("重新连接成功");
            alice.expectLineIgnoringCase("CAROL: 重新连接成功");

            reconnected.sendBytes(new byte[] {(byte) 0xC3, '(', '\n'});
            reconnected.expectLine(INVALID_UTF8_ERROR);
            reconnected.expectEof();
        }
        try (Peer invalidDelimiter = new Peer(port)) {
            invalidDelimiter.expectLine("Enter your username:");
            invalidDelimiter.send("bare\rCR\n");
            invalidDelimiter.expectLine(BARE_CR_ERROR);
            invalidDelimiter.expectEof();
        }
        System.out.println("PASS: 4096-byte CRLF frame, oversized frame rejection and cleanup.");
        System.out.println("PASS: Case-insensitive reconnect, malformed UTF-8 and bare CR rejection.");
    }

    private static final class Peer implements AutoCloseable {
        private final Socket socket;
        private final BufferedReader input;
        private final BufferedWriter output;

        private Peer(int port) throws IOException {
            socket = new Socket();
            try {
                socket.connect(new InetSocketAddress(LOOPBACK, port), 250);
                input = new BufferedReader(new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8));
                output = new BufferedWriter(new OutputStreamWriter(socket.getOutputStream(), StandardCharsets.UTF_8));
            } catch (IOException failure) {
                try {
                    socket.close();
                } catch (IOException closeFailure) {
                    failure.addSuppressed(closeFailure);
                }
                throw failure;
            }
        }

        private void register(String username) throws IOException {
            expectLine("Enter your username:");
            sendLine(username);
            expectLine("New username -- set a password to register:");
            sendLine(TEST_PASSWORD);
            expectLine("Account created. Welcome, " + username + "!");
        }

        private void login(String username) throws IOException {
            expectLine("Enter your username:");
            sendLine(username);
            expectLine("Enter password:");
            sendLine(TEST_PASSWORD);
            expectLineIgnoringCase("Login successful. Welcome back, " + username + "!");
        }

        private void join(String room) throws IOException {
            join(room, null);
        }

        private void join(String room, String forbidden) throws IOException {
            sendLine("/join " + room);
            expectLineBeforeLeak("You are now in room: " + room, forbidden);
        }

        private void sendLine(String line) throws IOException {
            send(line + "\n");
        }

        private void send(String data) throws IOException {
            output.write(data);
            output.flush();
        }

        private void sendBytes(byte[] data) throws IOException {
            socket.getOutputStream().write(data);
        }

        private void expectLine(String expected) throws IOException {
            expectLineBeforeLeak(expected, null);
        }

        private void expectLineIgnoringCase(String expected) throws IOException {
            expectLine(expected, null, true);
        }

        private void expectLineBeforeLeak(String expected, String forbidden) throws IOException {
            expectLine(expected, forbidden, false);
        }

        private void expectLine(String expected, String forbidden, boolean ignoreCase) throws IOException {
            long deadline = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(TIMEOUT_MILLIS);
            while (System.nanoTime() < deadline) {
                long remaining = TimeUnit.NANOSECONDS.toMillis(deadline - System.nanoTime());
                socket.setSoTimeout((int) Math.max(1, remaining));
                String line;
                try {
                    line = input.readLine();
                } catch (SocketTimeoutException e) {
                    throw new AssertionError("Timed out waiting for " + preview(expected), e);
                }
                if (line == null) {
                    throw new AssertionError("Connection closed before " + preview(expected));
                }
                if (line.equals(forbidden)) {
                    throw new AssertionError("Unexpected message received: " + preview(forbidden));
                }
                boolean matches = ignoreCase ? line.equalsIgnoreCase(expected) : line.equals(expected);
                if (matches) {
                    return;
                }
            }
            throw new AssertionError("Timed out waiting for " + preview(expected));
        }

        private void expectEof() throws IOException {
            socket.setSoTimeout(TIMEOUT_MILLIS);
            String unexpected = input.readLine();
            if (unexpected != null) {
                throw new AssertionError("Expected connection to close, received " + preview(unexpected));
            }
        }

        @Override
        public void close() throws IOException {
            socket.close();
        }
    }

    private static String preview(String line) {
        return line.length() <= 120 ? line : line.substring(0, 120) + "...";
    }
}
