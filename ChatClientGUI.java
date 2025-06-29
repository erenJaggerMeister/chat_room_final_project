import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.io.*;
import java.net.*;

// Deklarasi class utama turunan dari JFrame
public class ChatClientGUI extends JFrame {
    private JTextArea chatArea;
    private JTextField messageField;
    private JButton sendButton;
    private JButton connectButton;
    private JButton createRoomButton;
    private JButton closeRoomButton;
    private JButton kickUserButton;// author: Marcellius ;; date add : 28/06/2025
    private JTextField nameField;
    private JTextField serverField;
    private JTextField portField;
    private DefaultListModel<String> roomListModel;
    private JList<String> roomList;
    private JLabel roomInfoLabel;
    private JLabel roomTitleLabel;

    // Variabel jaringan dan status koneksi
    private Socket socket;
    private DataInputStream in;
    private DataOutputStream out;
    private String clientName;
    private String currentRoom = "";
    private boolean connected = false;
    private boolean isRoomOwner = false;// author: Marcellius ;; date add : 28/06/2025

    public ChatClientGUI() {
        initGUI();
    }

    private void initGUI() {
        setTitle("Messenger Style Chat Client");
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setLayout(new BorderLayout());

        // Panel login untuk memasukkan nama, IP server, dan port
        JPanel loginPanel = new JPanel(new FlowLayout());
        loginPanel.add(new JLabel("Nama:"));
        nameField = new JTextField(10);
        loginPanel.add(nameField);
        loginPanel.add(new JLabel("Server:"));
        serverField = new JTextField("127.0.0.1", 10);
        loginPanel.add(serverField);
        loginPanel.add(new JLabel("Port:"));
        portField = new JTextField("3355", 5);
        loginPanel.add(portField);

        connectButton = new JButton("Connect");
        connectButton.addActionListener(e -> connectToServer());
        loginPanel.add(connectButton);
        add(loginPanel, BorderLayout.NORTH);

        // Komponen daftar room
        roomListModel = new DefaultListModel<>();
        roomList = new JList<>(roomListModel);
        roomList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        JScrollPane roomScroll = new JScrollPane(roomList);
        roomScroll.setPreferredSize(new Dimension(200, 0));

        // Tombol room: buat, tutup, dan kick user
        createRoomButton = new JButton("+ Buat Room");
        createRoomButton.setEnabled(false);
        createRoomButton.addActionListener(e -> showRoomInputDialog());

        closeRoomButton = new JButton("Tutup Room");
        closeRoomButton.setEnabled(false);
        closeRoomButton.addActionListener(e -> closeCurrentRoom());

        kickUserButton = new JButton("Kick User");// author: Marcellius ;; date add : 28/06/2025
        kickUserButton.setEnabled(false);// author: Marcellius ;; date add : 28/06/2025
        kickUserButton.addActionListener(e -> showKickUserDialog());// author: Marcellius ;; date add : 28/06/2025

        roomInfoLabel = new JLabel("Klik dua kali untuk join room. Klik satu kali untuk menyeleksi.");

        // Panel kiri untuk daftar room dan tombol-tombolnya
        JPanel roomPanel = new JPanel(new BorderLayout());
        JPanel roomButtonPanel = new JPanel(new GridLayout(3, 1, 5, 5));
        roomButtonPanel.add(createRoomButton);
        roomButtonPanel.add(closeRoomButton);
        roomButtonPanel.add(kickUserButton);
        roomPanel.add(roomInfoLabel, BorderLayout.NORTH);
        roomPanel.add(roomScroll, BorderLayout.CENTER);
        roomPanel.add(roomButtonPanel, BorderLayout.SOUTH);

        // Klik dua kali untuk join ke room
        roomList.addMouseListener(new MouseAdapter() {
            public void mouseClicked(MouseEvent e) {
                if (e.getClickCount() == 2) {
                    String selectedRoom = roomList.getSelectedValue();
                    if (selectedRoom != null) {
                        String roomName = extractRoomName(selectedRoom);
                        joinRoom(roomName);
                    }
                }
            }
        });

        // Aktifkan tombol tutup/kick jika user adalah owner dari room yang diseleksi
        roomList.addListSelectionListener(e -> {
            if (!e.getValueIsAdjusting()) {
                String selected = roomList.getSelectedValue();
                if (selected != null && selected.contains("(owner)")) {
                    String selectedRoom = extractRoomName(selected);
                    if (selectedRoom.equals(currentRoom)) {
                        closeRoomButton.setEnabled(true);
                        kickUserButton.setEnabled(true);
                        return;
                    }
                }
                closeRoomButton.setEnabled(false);
                kickUserButton.setEnabled(false);
            }
        });

        add(roomPanel, BorderLayout.WEST);

        chatArea = new JTextArea(20, 50);
        chatArea.setEditable(false);
        JScrollPane scroll = new JScrollPane(chatArea);

        roomTitleLabel = new JLabel(" ");
        roomTitleLabel.setForeground(Color.BLUE.darker());
        roomTitleLabel.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        roomTitleLabel.setToolTipText("Klik untuk melihat siapa saja di room ini");
        roomTitleLabel.addMouseListener(new MouseAdapter() {
            public void mouseClicked(MouseEvent e) {
                requestUserList(); // Minta daftar user saat ini di room
            }
        });

        JPanel centerPanel = new JPanel(new BorderLayout());
        centerPanel.add(roomTitleLabel, BorderLayout.NORTH);
        centerPanel.add(scroll, BorderLayout.CENTER);
        add(centerPanel, BorderLayout.CENTER);

        // Panel bawah untuk input pesan dan tombol kirim
        JPanel bottomPanel = new JPanel(new BorderLayout());
        messageField = new JTextField();
        messageField.setEnabled(false);
        messageField.addActionListener(e -> sendMessage());
        bottomPanel.add(messageField, BorderLayout.CENTER);

        sendButton = new JButton("Send");
        sendButton.setEnabled(false);
        sendButton.addActionListener(e -> sendMessage());
        bottomPanel.add(sendButton, BorderLayout.EAST);

        add(bottomPanel, BorderLayout.SOUTH);

        // Jika jendela ditutup, lakukan disconnect
        addWindowListener(new WindowAdapter() {
            public void windowClosing(WindowEvent e) {
                disconnect();
            }
        });

        pack();
        setLocationRelativeTo(null);
        setVisible(true);
    }

    // Ekstrak nama room sebelum tanda ' (' jika ada
    private String extractRoomName(String label) {
        int idx = label.indexOf(" (");
        return idx >= 0 ? label.substring(0, idx).trim() : label.trim();
    }

    // Kirim perintah untuk menutup room saat ini
    private void closeCurrentRoom() {
        String selected = roomList.getSelectedValue();
        if (selected != null) {
            String roomName = extractRoomName(selected);
            try {
                out.writeUTF("DELETE_ROOM:" + roomName);
                closeRoomButton.setEnabled(false);
                kickUserButton.setEnabled(false);
            } catch (IOException e) {
                showMessage("Gagal menutup room.");
            }
        }
    }

     // Tampilkan dialog input user yang ingin di-kick dari room
    private void showKickUserDialog() {
        if (!isRoomOwner || currentRoom.isEmpty()) {
            showMessage("Anda bukan pemilik room ini.");
            return;
        }

        // First, request user list to show in selection dialog
        try {
            // Create a simple input dialog for now
            String targetUser = JOptionPane.showInputDialog(
                this, 
                "Masukkan nama user yang ingin dikeluarkan:", 
                "Kick User", 
                JOptionPane.QUESTION_MESSAGE
            );
            
            if (targetUser != null && !targetUser.trim().isEmpty()) {
                targetUser = targetUser.trim();
                if (targetUser.equals(clientName)) {
                    JOptionPane.showMessageDialog(this, "Anda tidak bisa mengeluarkan diri sendiri!", "Error", JOptionPane.ERROR_MESSAGE);
                    return;
                }
                
                int confirm = JOptionPane.showConfirmDialog(
                    this,
                    "Apakah Anda yakin ingin mengeluarkan user '" + targetUser + "' dari room?",
                    "Konfirmasi Kick User",
                    JOptionPane.YES_NO_OPTION,
                    JOptionPane.WARNING_MESSAGE
                );
                
                if (confirm == JOptionPane.YES_OPTION) {
                    out.writeUTF("KICK_USER:" + targetUser);
                }
            }
        } catch (IOException e) {
            showMessage("Gagal mengeluarkan user.");
        }
    }

    // Lakukan koneksi ke server dan setup awal
    private void connectToServer() {
        String name = nameField.getText().trim();
        String server = serverField.getText().trim();
        int port = Integer.parseInt(portField.getText().trim());

        if (name.isEmpty()) return;

        try {
            socket = new Socket(server, port);
            in = new DataInputStream(socket.getInputStream());
            out = new DataOutputStream(socket.getOutputStream());

            String prompt = in.readUTF();
            out.writeUTF(name);
            clientName = name;
            connected = true;

            connectButton.setEnabled(false);
            nameField.setEnabled(false);
            serverField.setEnabled(false);
            portField.setEnabled(false);
            createRoomButton.setEnabled(true);

            Thread t = new Thread(this::readMessages);
            t.setDaemon(true);
            t.start();

            updateRoomList();

        } catch (Exception e) {
            showMessage("Gagal koneksi: " + e.getMessage());
        }
    }

    // Minta daftar room ke server
    private void updateRoomList() {
        try {
            out.writeUTF("_list");
        } catch (IOException e) {
            showMessage("Gagal minta daftar room.");
        }
    }

    // Kirim perintah join ke server, update GUI sesuai room
    private void joinRoom(String roomName) {
        try {
            out.writeUTF(roomName);
            messageField.setEnabled(true);
            sendButton.setEnabled(true);
            chatArea.setText("");
            currentRoom = roomName;
            roomTitleLabel.setText("# " + roomName + " (klik untuk lihat anggota)");
            
            // Update room owner status
            updateRoomOwnerStatus();
            
        } catch (IOException e) {
            showMessage("Gagal join room.");
        }
    }

    // Cek apakah client adalah pemilik room saat ini
    private void updateRoomOwnerStatus() {
        isRoomOwner = false;
        for (int i = 0; i < roomListModel.size(); i++) {
            String roomEntry = roomListModel.getElementAt(i);
            String roomName = extractRoomName(roomEntry);
            if (roomName.equals(currentRoom) && roomEntry.contains("(owner)")) {
                isRoomOwner = true;
                break;
            }
        }
        
        // Update button states
        if (isRoomOwner && !currentRoom.isEmpty()) {
            closeRoomButton.setEnabled(true);
            kickUserButton.setEnabled(true);// author: Marcellius ;; date add : 28/06/2025
        } else {
            closeRoomButton.setEnabled(false);
            kickUserButton.setEnabled(false);// author: Marcellius ;; date add : 28/06/2025
        }
    }

    // Kirim permintaan daftar user ke server
    private void requestUserList() {
        if (out != null && !currentRoom.isEmpty()) {
            try {
                out.writeUTF("_users");
            } catch (IOException e) {
                showMessage("Gagal minta daftar user.");
            }
        }
    }

    // Tampilkan input dialog untuk nama room baru
    private void showRoomInputDialog() {
        String namaRoom = JOptionPane.showInputDialog(this, "Masukkan nama room baru:");
        if (namaRoom != null && !namaRoom.trim().isEmpty()) {
            try {
                out.writeUTF("NEW:" + namaRoom.trim());
                updateRoomList();
            } catch (IOException e) {
                showMessage("Gagal buat room.");
            }
        }
    }

    // Thread untuk membaca pesan dari server
    private void readMessages() {
        try {
            while (connected && socket != null && !socket.isClosed()) {
                String msg = in.readUTF();
                if (msg.startsWith("ROOMS:")) {
                    String raw = msg.substring(6).trim();
                    SwingUtilities.invokeLater(() -> {
                        roomListModel.clear();
                        if (raw.isEmpty()) {
                            roomInfoLabel.setText("Belum ada room tersedia.");
                        } else {
                            String[] rooms = raw.split(",");
                            for (String room : rooms) {
                                if (!room.trim().isEmpty()) {
                                    roomListModel.addElement(room.trim());
                                }
                            }
                            roomInfoLabel.setText("Klik dua kali untuk join room. Klik satu kali untuk menyeleksi.");
                        }
                        updateRoomOwnerStatus();
                    });
                } else if (msg.startsWith("USERS:")) {
                    String users = msg.substring(6);
                    JOptionPane.showMessageDialog(this, users.isEmpty() ? "Belum ada user." : users, "Pengguna di room", JOptionPane.INFORMATION_MESSAGE);
                } else if (msg.startsWith("ROOM_CLOSED:")) { // author: Marcellius ;; date add : 28/06/2025
                    String closedRoom = msg.substring(13);
                    if (currentRoom.equals(closedRoom)) {
                        currentRoom = "";
                        messageField.setEnabled(false);
                        sendButton.setEnabled(false);
                        closeRoomButton.setEnabled(false);
                        kickUserButton.setEnabled(false);
                        roomTitleLabel.setText("");
                        isRoomOwner = false;
                        showMessage("Room " + closedRoom + " telah ditutup oleh pemilik.");
                    }
                } else if (msg.startsWith("KICKED_FROM_ROOM:")) {
                    String roomName = msg.substring(17);
                    if (currentRoom.equals(roomName)) {
                        currentRoom = "";
                        messageField.setEnabled(false);
                        sendButton.setEnabled(false);
                        closeRoomButton.setEnabled(false);
                        kickUserButton.setEnabled(false);
                        roomTitleLabel.setText("");
                        isRoomOwner = false;
                        chatArea.setText("");
                    }
                } else {
                    String display = msg;
                    if (msg.contains(": ") && msg.contains("[")) {
                        int endIdx = msg.indexOf("]");
                        int nameIdx = msg.indexOf(":", endIdx);
                        if (endIdx > 0 && nameIdx > endIdx) {
                            String sender = msg.substring(endIdx + 2, nameIdx).trim();
                            if (sender.equals(clientName)) {
                                display = msg.substring(0, endIdx + 2) + "YOU" + msg.substring(nameIdx);
                            }
                        }
                    }
                    showMessage(display);
                }
            }
        } catch (IOException e) {
            showMessage("Terputus dari server.");
            disconnect();
        }
    }

    // Kirim pesan ke server
    private void sendMessage() {
        String message = messageField.getText().trim();
        if (!message.isEmpty() && !message.startsWith("NEW:")) {
            try {
                out.writeUTF(message);
                messageField.setText("");
                if (message.equalsIgnoreCase("exit")) {
                    disconnect();
                }
            } catch (IOException e) {
                showMessage("Gagal kirim pesan.");
                disconnect();
            }
        }
    }

    // Putus koneksi, reset GUI ke kondisi awal
    private void disconnect() {
        connected = false;
        try {
            if (out != null) out.writeUTF("exit");
            if (socket != null) socket.close();
        } catch (IOException ignored) {}

        connectButton.setEnabled(true);
        nameField.setEnabled(true);
        serverField.setEnabled(true);
        portField.setEnabled(true);
        messageField.setEnabled(false);
        sendButton.setEnabled(false);
        createRoomButton.setEnabled(false);
        closeRoomButton.setEnabled(false);
        kickUserButton.setEnabled(false);// author: Marcellius ;; date add : 28/06/2025
        roomTitleLabel.setText("");
        isRoomOwner = false;
    }

    // Tampilkan pesan ke chat area
    private void showMessage(String msg) {
        SwingUtilities.invokeLater(() -> {
            chatArea.append(msg + "\n");
            chatArea.setCaretPosition(chatArea.getDocument().getLength());
        });
    }

    // Fungsi main: jalankan aplikasi
    public static void main(String[] args) {
        SwingUtilities.invokeLater(ChatClientGUI::new);
    }
}