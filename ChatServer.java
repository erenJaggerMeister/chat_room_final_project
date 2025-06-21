import java.io.*;
import java.net.*;
import java.util.*;

public class ChatServer {
    private static final int PORT = 3355;
    private static Set<ClientHandler> clients = Collections.synchronizedSet(new HashSet<>());

    public static void main(String[] args) throws IOException {
        ServerSocket serverSocket = new ServerSocket(PORT);
        System.out.println("Server started on port " + PORT);

        while (true) {
            Socket clientSocket = serverSocket.accept();
            ClientHandler client = new ClientHandler(clientSocket);
            clients.add(client);
            client.start();
        }
    }

    public static void broadcast(String message, ClientHandler sender) {
        System.out.println("Broadcast from " + sender.getClientName() + ": " + message);
        synchronized (clients) {
            for (ClientHandler client : clients) {
                if (client != sender) {
                    client.sendMessage(sender.getClientName() + ": " + message);
                }
            }
        }
    }

    static class ClientHandler extends Thread {
        private Socket socket;
        private DataInputStream in;
        private DataOutputStream out;
        private String clientName;

        public ClientHandler(Socket socket) {
            this.socket = socket;
        }

        public String getClientName() {
            return clientName;
        }

        public void sendMessage(String message) {
            try {
                out.writeUTF(message);
            } catch (IOException e) {
                System.out.println("Error sending message to " + clientName);
            }
        }

        public void run() {
            try {
                in = new DataInputStream(socket.getInputStream());
                out = new DataOutputStream(socket.getOutputStream());

                out.writeUTF("Enter your name:");
                clientName = in.readUTF();
                System.out.println(clientName + " joined the chat.");
                broadcast("joined the chat.", this);

                while (true) {
                    String message = in.readUTF();
                    if (message.equalsIgnoreCase("exit")) {
                        break;
                    }
                    System.out.println(clientName + ": " + message);
                    broadcast(message, this);
                }

                System.out.println(clientName + " left the chat.");
                broadcast("left the chat.", this);
                clients.remove(this);
                socket.close();
            } catch (IOException e) {
                System.out.println(clientName + " disconnected unexpectedly.");
                clients.remove(this);
            }
        }
    }
}
