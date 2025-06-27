// === FINAL ChatClientGUI.java (with robust roomName parsing + active close button) ===
import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.io.*;
import java.net.*;

public class ChatClientGUI extends JFrame {
    private JTextArea chatArea;
    private JTextField messageField;
    private JButton sendButton;
    private JButton connectButton;
    private JButton createRoomButton;
    private JButton closeRoomButton;
    private JTextField nameField;
    private JTextField serverField;
    private JTextField portField;
    private DefaultListModel<String> roomListModel;
    private JList<String> roomList;
    private JLabel roomInfoLabel;
    private JLabel roomTitleLabel;

    private Socket socket;
    private DataInputStream in;
    private DataOutputStream out;
    private String clientName;
    private String currentRoom = "";
    private boolean connected = false;

    public ChatClientGUI() {
        initGUI();
    }

    private void initGUI() {
        setTitle("Messenger Style Chat Client");
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setLayout(new BorderLayout());

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

        roomListModel = new DefaultListModel<>();
        roomList = new JList<>(roomListModel);
        roomList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        JScrollPane roomScroll = new JScrollPane(roomList);
        roomScroll.setPreferredSize(new Dimension(200, 0));

        createRoomButton = new JButton("+ Buat Room");
        createRoomButton.setEnabled(false);
        createRoomButton.addActionListener(e -> showRoomInputDialog());

        closeRoomButton = new JButton("Tutup Room");
        closeRoomButton.setEnabled(false);
        closeRoomButton.addActionListener(e -> closeCurrentRoom());

        roomInfoLabel = new JLabel("Klik dua kali untuk join room. Klik satu kali untuk menyeleksi.");

        JPanel roomPanel = new JPanel(new BorderLayout());
        JPanel roomButtonPanel = new JPanel(new GridLayout(2, 1));
        roomButtonPanel.add(closeRoomButton);
        roomButtonPanel.add(createRoomButton);
        roomPanel.add(roomInfoLabel, BorderLayout.NORTH);
        roomPanel.add(roomScroll, BorderLayout.CENTER);
        roomPanel.add(roomButtonPanel, BorderLayout.SOUTH);

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

        roomList.addListSelectionListener(e -> {
            if (!e.getValueIsAdjusting()) {
                String selected = roomList.getSelectedValue();
                if (selected != null && selected.contains("(owner)")) {
                    String selectedRoom = extractRoomName(selected);
                    if (selectedRoom.equals(currentRoom)) {
                        closeRoomButton.setEnabled(true);
                        return;
                    }
                }
                closeRoomButton.setEnabled(false);
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
                requestUserList();
            }
        });

        JPanel centerPanel = new JPanel(new BorderLayout());
        centerPanel.add(roomTitleLabel, BorderLayout.NORTH);
        centerPanel.add(scroll, BorderLayout.CENTER);
        add(centerPanel, BorderLayout.CENTER);

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

        addWindowListener(new WindowAdapter() {
            public void windowClosing(WindowEvent e) {
                disconnect();
            }
        });

        pack();
        setLocationRelativeTo(null);
        setVisible(true);
    }

    private String extractRoomName(String label) {
        // Ambil nama room sebelum tanda ' (' pertama
        int idx = label.indexOf(" (");
        return idx >= 0 ? label.substring(0, idx).trim() : label.trim();
    }

    private void closeCurrentRoom() {
        String selected = roomList.getSelectedValue();
        if (selected != null) {
            String roomName = extractRoomName(selected);
            try {
                out.writeUTF("DELETE_ROOM:" + roomName);
                closeRoomButton.setEnabled(false);
            } catch (IOException e) {
                showMessage("Gagal menutup room.");
            }
        }
    }

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

    private void updateRoomList() {
        try {
            out.writeUTF("_list");
        } catch (IOException e) {
            showMessage("Gagal minta daftar room.");
        }
    }

    private void joinRoom(String roomName) {
        try {
            out.writeUTF(roomName);
            messageField.setEnabled(true);
            sendButton.setEnabled(true);
            chatArea.setText("");
            currentRoom = roomName;
            roomTitleLabel.setText("# " + roomName + " (klik untuk lihat anggota)");
        } catch (IOException e) {
            showMessage("Gagal join room.");
        }
    }

    private void requestUserList() {
        if (out != null && !currentRoom.isEmpty()) {
            try {
                out.writeUTF("_users");
            } catch (IOException e) {
                showMessage("Gagal minta daftar user.");
            }
        }
    }

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
                    });
                } else if (msg.startsWith("USERS:")) {
                    String users = msg.substring(6);
                    JOptionPane.showMessageDialog(this, users.isEmpty() ? "Belum ada user." : users, "Pengguna di room", JOptionPane.INFORMATION_MESSAGE);
                } else if (msg.startsWith("ROOM_CLOSED:")) {
                    String closedRoom = msg.substring(13);
                    if (currentRoom.equals(closedRoom)) {
                        currentRoom = "";
                        messageField.setEnabled(false);
                        sendButton.setEnabled(false);
                        closeRoomButton.setEnabled(false);
                        roomTitleLabel.setText("");
                        showMessage("Room " + closedRoom + " telah ditutup oleh pemilik.");
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
        roomTitleLabel.setText("");
    }

    private void showMessage(String msg) {
        SwingUtilities.invokeLater(() -> {
            chatArea.append(msg + "\n");
            chatArea.setCaretPosition(chatArea.getDocument().getLength());
        });
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(ChatClientGUI::new);
    }
}
