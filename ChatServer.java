import java.io.*;
import java.net.*;
import java.util.*;

public class ChatServer {
    private static final int PORT = 3355;

    // Menyimpan semua chat room yang aktif (key = nama room)
    private static Map<String, ChatRoom> rooms = Collections.synchronizedMap(new HashMap<>());

    // Menyimpan semua klien aktif untuk broadcast daftar room
    private static Set<ClientHandler> allClients = Collections.synchronizedSet(new HashSet<>());

    // Entry point server
    public static void main(String[] args) throws IOException {
        ServerSocket serverSocket = new ServerSocket(PORT);
        System.out.println("Server started on port " + PORT);

        while (true) {
            Socket clientSocket = serverSocket.accept();
            ClientHandler clientHandler = new ClientHandler(clientSocket);
            clientHandler.start(); // Handle client secara paralel
        }
    }

    // Kelas ChatRoom: merepresentasikan 1 ruang obrolan
    static class ChatRoom {
        String name;                     // Nama room
        String owner;                    // Pemilik room
        Set<ClientHandler> users = Collections.synchronizedSet(new HashSet<>()); // Anggota room

        public ChatRoom(String name, String owner) {
            this.name = name;
            this.owner = owner;
        }

        // Kirim pesan dari pengirim ke semua anggota room
        public void broadcast(String message, ClientHandler sender) {
            synchronized (users) {
                for (ClientHandler user : users) {
                    user.sendMessage("[" + name + "] " + sender.clientName + ": " + message);
                }
            }
        }

        // Kirim pesan sistem (tanpa pengirim spesifik)
        public void sendSystemMessage(String message) {
            synchronized (users) {
                for (ClientHandler user : users) {
                    user.sendMessage("[" + name + "] SYSTEM: " + message);
                }
            }
        }

        // Dapatkan daftar user dalam room sebagai string
        public String getUserList() {
            StringBuilder sb = new StringBuilder();
            synchronized (users) {
                for (ClientHandler user : users) {
                    sb.append(user.clientName).append(", ");
                }
            }
            if (sb.length() > 0) sb.setLength(sb.length() - 2); // Hapus koma terakhir
            return sb.toString();
        }

        /**
         * 
         * @author Marcellius
         * @since 28/06/2025
         * @param targetUser
         * @param kickerName
         * @return
         * Kick user dari room (hanya owner yang bisa)
         */
        public boolean kickUser(String targetUser, String kickerName) {
            if (!owner.equals(kickerName)) {
                return false; // Bukan owner
            }

            synchronized (users) {
                ClientHandler targetHandler = null;
                for (ClientHandler user : users) {
                    if (user.clientName.equals(targetUser)) {
                        targetHandler = user;
                        break;
                    }
                }

                if (targetHandler != null && !targetHandler.clientName.equals(owner)) {
                    // Kick user
                    users.remove(targetHandler);
                    targetHandler.currentRoom = null;
                    
                    // Notify kicked user
                    targetHandler.sendMessage("KICKED_FROM_ROOM:" + name);
                    targetHandler.sendMessage("SYSTEM: Anda telah dikeluarkan dari room \"" + name + "\" oleh " + kickerName);
                    
                    // Notify other users in room
                    sendSystemMessage(targetUser + " telah dikeluarkan dari room oleh " + kickerName);
                    
                    System.out.println(kickerName + " kicked " + targetUser + " from room: " + name);
                    return true;
                }
            }
            return false; // User tidak ditemukan atau owner mencoba kick dirinya sendiri
        }
    }

    // Kelas ClientHandler: menangani komunikasi dengan satu klien
    static class ClientHandler extends Thread {
        private Socket socket;
        private DataInputStream in;
        private DataOutputStream out;
        private ChatRoom currentRoom = null; // Room yang sedang diikuti
        public String clientName;

        public ClientHandler(Socket socket) {
            this.socket = socket;
        }

        // Kirim pesan ke klien
        public void sendMessage(String message) {
            try {
                out.writeUTF(message);
            } catch (IOException e) {
                System.out.println("Gagal kirim pesan ke " + clientName);
            }
        }

        // Proses utama klien setelah terkoneksi
        public void run() {
            try {
                allClients.add(this);
                in = new DataInputStream(socket.getInputStream());
                out = new DataOutputStream(socket.getOutputStream());

                out.writeUTF("Masukkan nama kamu:");
                clientName = in.readUTF();
                System.out.println(clientName + " joined the chat.");

                // Loop awal: user pilih/join/buat room
                while (true) {
                    String req = in.readUTF();

                    if (req.equals("_list")) {
                        sendRoomList();
                        continue;
                    }

                    if (req.startsWith("NEW:")) {
                        String newRoom = req.substring("NEW:".length()).trim();
                        if (!rooms.containsKey(newRoom)) {
                            ChatRoom room = new ChatRoom(newRoom, clientName);
                            rooms.put(newRoom, room);
                            System.out.println(clientName + " created room: " + newRoom);
                        }
                        sendRoomList();
                        broadcastRoomListToAll();
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

                // Loop setelah masuk room
                while (true) {
                    String message = in.readUTF();

                    if (message.equalsIgnoreCase("exit")) break;

                    if (message.equals("_list")) {
                        sendRoomList();
                        continue;
                    }

                    if (message.equals("_users")) {
                        if (currentRoom != null) {
                            String users = currentRoom.getUserList();
                            out.writeUTF("USERS:" + users);
                        }
                        continue;
                    }

                    if (message.startsWith("NEW:")) {
                        String newRoom = message.substring("NEW:".length()).trim();
                        if (!rooms.containsKey(newRoom)) {
                            ChatRoom room = new ChatRoom(newRoom, clientName);
                            rooms.put(newRoom, room);
                            System.out.println(clientName + " created room: " + newRoom);
                        }
                        sendRoomList();
                        broadcastRoomListToAll();
                        continue;
                    }

                    if (message.startsWith("DELETE_ROOM:")) {
                        String roomToDelete = message.substring("DELETE_ROOM:".length()).trim();
                        ChatRoom room = rooms.get(roomToDelete);

                        // Validasi pemilik
                        if (room != null && room.owner != null && room.owner.equalsIgnoreCase(clientName)) {
                            synchronized (rooms) {
                                rooms.remove(roomToDelete);
                                String notif = "Room \"" + roomToDelete + "\" telah ditutup oleh " + clientName + ".";
                                System.out.println(clientName + " CLOSED room: " + roomToDelete);
                                for (ClientHandler user : room.users) {
                                    user.sendMessage("ROOM_CLOSED:" + roomToDelete);
                                    user.sendMessage("SYSTEM: " + notif);
                                    user.currentRoom = null;
                                }
                                room.users.clear();
                            }
                            broadcastRoomListToAll();
                        } else {
                            out.writeUTF("Kamu bukan pemilik room ini.");
                        }
                        continue;
                    }

                    // author: Marcellius ;; date add : 28/06/2025
                    if (message.startsWith("KICK_USER:")) {
                        String targetUser = message.substring("KICK_USER:".length()).trim();
                        if (currentRoom != null) {
                            if (currentRoom.kickUser(targetUser, clientName)) {
                                out.writeUTF("SYSTEM: " + targetUser + " telah dikeluarkan dari room.");
                                broadcastRoomListToAll();
                            } else {
                                if (targetUser.equals(clientName)) {
                                    out.writeUTF("SYSTEM: Anda tidak bisa mengeluarkan diri sendiri.");
                                } else if (!currentRoom.owner.equals(clientName)) {
                                    out.writeUTF("SYSTEM: Hanya pemilik room yang bisa mengeluarkan user.");
                                } else {
                                    out.writeUTF("SYSTEM: User " + targetUser + " tidak ditemukan di room ini.");
                                }
                            }
                        }
                        continue;
                    }

                    // Ganti room
                    if (rooms.containsKey(message)) {
                        if (currentRoom == null || !message.equals(currentRoom.name)) {
                            leaveCurrentRoom();
                            joinRoom(message);
                        }
                        continue;
                    }

                    // Broadcast pesan biasa ke room
                    if (currentRoom != null) {
                        currentRoom.broadcast(message, this);
                    } else {
                        sendRoomList();
                    }
                }

                leaveCurrentRoom();
                socket.close();
            } catch (IOException e) {
                System.out.println(clientName + " disconnected unexpectedly.");
                leaveCurrentRoom();
            } finally {
                allClients.remove(this);
            }
        }

        // Keluar dari room aktif
        private void leaveCurrentRoom() {
            if (currentRoom != null) {
                currentRoom.users.remove(this);
                currentRoom.sendSystemMessage(clientName + " left the room.");
                System.out.println(clientName + " left room: " + currentRoom.name);
                broadcastRoomListToAll();
                currentRoom = null;
            }
        }

        // Masuk ke room
        private void joinRoom(String roomName) {
            currentRoom = rooms.get(roomName);
            currentRoom.users.add(this);
            currentRoom.sendSystemMessage(clientName + " joined the room.");
            System.out.println(clientName + " joined room: " + roomName);
            broadcastRoomListToAll();
        }

        // Kirim daftar room yang tersedia ke klien ini
        private void sendRoomList() {
            StringBuilder sb = new StringBuilder();
            synchronized (rooms) {
                for (Map.Entry<String, ChatRoom> entry : rooms.entrySet()) {
                    sb.append(entry.getKey())
                      .append(" (")
                      .append(entry.getValue().users.size())
                      .append(" users)");
                    if (entry.getValue().owner.equals(clientName)) {
                        sb.append(" (owner)");
                    }
                    sb.append(",");
                }
            }
            try {
                out.writeUTF("ROOMS:" + sb.toString());
            } catch (IOException e) {
                System.out.println("Gagal kirim daftar room ke " + clientName);
            }
        }
    }

    // Kirim ulang daftar room ke semua klien aktif
    private static void broadcastRoomListToAll() {
        synchronized (allClients) {
            for (ClientHandler user : allClients) {
                user.sendRoomList();
            }
        }
    }
}