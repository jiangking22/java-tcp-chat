import java.io.BufferedReader;
import java.io.Console;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.Socket;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.Scanner;

/**
 * Console client with separate threads for keyboard input and network reception.
 */
public class ChatClient {
    public static void main(String[] args) throws IOException {
        String host = args.length > 0 ? args[0] : "127.0.0.1";
        int port = args.length > 1 ? Integer.parseInt(args[1]) : 5000;

        try (Socket socket = new Socket(host, port)) {
            System.out.println("Connected to server at " + host + ":" + port);

            BufferedReader in = new BufferedReader(
                    new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8));
            PrintWriter out = ChatProtocol.writer(socket.getOutputStream());

            Thread keyboard = new Thread(() -> sendInput(socket, out), "chat-keyboard");
            keyboard.setDaemon(true);
            keyboard.start();

            String serverLine;
            while ((serverLine = in.readLine()) != null) {
                System.out.println(serverLine);
            }
        }
        System.out.println("Client shut down.");
    }

    private static void sendInput(Socket socket, PrintWriter out) {
        Console console = System.console();
        Charset inputCharset = console == null ? StandardCharsets.UTF_8 : console.charset();
        try (Scanner scanner = new Scanner(System.in, inputCharset)) {
            while (scanner.hasNextLine()) {
                String userInput = scanner.nextLine();
                out.println(userInput);
                if (userInput.equalsIgnoreCase("quit")) {
                    break;
                }
            }
            socket.shutdownOutput();
        } catch (IOException e) {
            // The server can close the socket while the keyboard thread is still reading.
            if (!socket.isClosed()) {
                System.err.println("Could not finish sending input: " + e.getMessage());
            }
        }
    }
}
