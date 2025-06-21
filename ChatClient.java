import java.io.*;
import java.net.*;

public class ChatClient {
    public static void main(String[] args) {
        String serverName = "127.0.0.1";
        int port = 3355;

        try {
            Socket socket = new Socket(serverName, port);
            DataInputStream in = new DataInputStream(socket.getInputStream());
            DataOutputStream out = new DataOutputStream(socket.getOutputStream());
            BufferedReader keyboard = new BufferedReader(new InputStreamReader(System.in));

            // Read welcome message and enter name
            System.out.println(in.readUTF());
            String name = keyboard.readLine();
            out.writeUTF(name);

            // Thread for receiving messages
            Thread readThread = new Thread(() -> {
                try {
                    while (true) {
                        String response = in.readUTF();
                        System.out.println(response);
                    }
                } catch (IOException e) {
                    System.out.println("Disconnected from server.");
                }
            });
            readThread.start();

            // Main thread for sending messages
            while (true) {
                String message = keyboard.readLine();
                out.writeUTF(message);
                if (message.equalsIgnoreCase("exit")) {
                    socket.close();
                    break;
                } else {
                    System.out.println("me : "+message);
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
