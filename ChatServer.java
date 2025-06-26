import java.io.*;
import java.net.*;
import java.util.*;

public class ChatServer {
    private static final int PORT = 3355;
    private static Map<String, ChatRoom> rooms = Collections.synchronizedMap(new HashMap<String, ChatRoom>());

    public static void main(String[] args) throws IOException {
        ServerSocket serverSocket = new ServerSocket(PORT);
        System.out.println("Server started on port " + PORT);

        while (true) {
            Socket clientSocket = serverSocket.accept();
            ClientHandler clientHandler = new ClientHandler(clientSocket);
            clientHandler.start();
        }
    }

    static class ChatRoom {
        String name;
        String owner;
        Set<ClientHandler> users = Collections.synchronizedSet(new HashSet<ClientHandler>());

        public ChatRoom(String name, String owner) {
            this.name = name;
            this.owner = owner;
        }

        public void broadcast(String message, ClientHandler sender) {
            synchronized (users) {
                for (ClientHandler user : users) {
                    user.sendMessage("[" + name + "] " + sender.clientName + ": " + message);
                }
            }
        }

        public void sendSystemMessage(String message) {
            synchronized (users) {
                for (ClientHandler user : users) {
                    user.sendMessage("[" + name + "] SYSTEM: " + message);
                }
            }
        }
    }

    static class ClientHandler extends Thread {
        private Socket socket;
        private DataInputStream in;
        private DataOutputStream out;
        private ChatRoom currentRoom = null;
        public String clientName;

        public ClientHandler(Socket socket) {
            this.socket = socket;
        }

        public void sendMessage(String message) {
            try {
                out.writeUTF(message);
            } catch (IOException e) {
                System.out.println("Gagal kirim pesan ke " + clientName);
            }
        }

        public void run() {
            try {
                in = new DataInputStream(socket.getInputStream());
                out = new DataOutputStream(socket.getOutputStream());

                out.writeUTF("Masukkan nama kamu:");
                clientName = in.readUTF();
                System.out.println(clientName + " joined the chat.");

                while (true) {
                    String req = in.readUTF();

                    if (req.equals("_list")) {
                        sendRoomList();
                        continue;
                    }

                    if (req.startsWith("NEW:")) {
                        String newRoom = req.substring(4).trim();
                        if (!rooms.containsKey(newRoom)) {
                            ChatRoom room = new ChatRoom(newRoom, clientName);
                            rooms.put(newRoom, room);
                            System.out.println(clientName + " created room: " + newRoom);
                        }
                        broadcastRoomListToAll();
                        sendRoomList();
                        continue;
                    }

                    if (rooms.containsKey(req)) {
                        joinRoom(req);
                        break;
                    } else {
                        out.writeUTF("Room tidak ditemukan.");
                        sendRoomList();
                    }
                }

                while (true) {
                    String message = in.readUTF();

                    if (message.equalsIgnoreCase("exit")) {
                        break;
                    }

                    if (message.equals("_list")) {
                        sendRoomList();
                        continue;
                    }

                    if (message.startsWith("NEW:")) {
                        String newRoom = message.substring(4).trim();
                        if (!rooms.containsKey(newRoom)) {
                            ChatRoom room = new ChatRoom(newRoom, clientName);
                            rooms.put(newRoom, room);
                            System.out.println(clientName + " created room: " + newRoom);
                        }
                        sendRoomList();
                        broadcastRoomListToAll();
                        continue;
                    }

                    if (rooms.containsKey(message)) {
                        if (!message.equals(currentRoom.name)) {
                            leaveCurrentRoom();
                            joinRoom(message);
                        }
                        continue;
                    }

                    System.out.println(clientName + ": " + message);
                    System.out.println("Broadcast from " + clientName + ": " + message);
                    currentRoom.broadcast(message, this);
                }

                leaveCurrentRoom();
                socket.close();
            } catch (IOException e) {
                System.out.println(clientName + " disconnected unexpectedly.");
                leaveCurrentRoom();
            }
        }

        private void leaveCurrentRoom() {
            if (currentRoom != null) {
                currentRoom.users.remove(this);
                currentRoom.sendSystemMessage(clientName + " left the room.");
                System.out.println(clientName + " left the chat.");
                System.out.println("Broadcast from " + clientName + ": left the chat.");
                broadcastRoomListToAll();
                currentRoom = null;
            }
        }

        private void joinRoom(String roomName) {
            currentRoom = rooms.get(roomName);
            currentRoom.users.add(this);
            currentRoom.sendSystemMessage(clientName + " joined the room.");
            broadcastRoomListToAll();
        }

        private void sendRoomList() {
            StringBuilder sb = new StringBuilder();
            synchronized (rooms) {
                for (Map.Entry<String, ChatRoom> entry : rooms.entrySet()) {
                    sb.append(entry.getKey())
                      .append(" (")
                      .append(entry.getValue().users.size())
                      .append(" users),");
                }
            }
            try {
                out.writeUTF("ROOMS:" + sb.toString());
            } catch (IOException e) {
                System.out.println("Gagal kirim daftar room ke " + clientName);
            }
        }
    }

    private static void broadcastRoomListToAll() {
        StringBuilder sb = new StringBuilder();
        synchronized (rooms) {
            for (Map.Entry<String, ChatRoom> entry : rooms.entrySet()) {
                sb.append(entry.getKey())
                  .append(" (")
                  .append(entry.getValue().users.size())
                  .append(" users),");
            }
        }
        String roomList = "ROOMS:" + sb.toString();
        synchronized (rooms) {
            for (ChatRoom room : rooms.values()) {
                for (ClientHandler user : room.users) {
                    user.sendMessage(roomList);
                }
            }
        }
    }
}
