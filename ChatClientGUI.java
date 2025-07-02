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
    private JButton attachButton;


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
        setMinimumSize(new Dimension(800, 600));

        // Dark theme colors
        Color primaryColor = new Color(255, 213, 79);    // soft yellow
        Color backgroundColor = new Color(18, 18, 18);   // very dark gray
        Color panelColor = new Color(30, 30, 30);        // dark gray panels
        Color textColor = new Color(238, 238, 238);      // light gray text

        // Main panel with dark background
        JPanel mainPanel = new JPanel(new BorderLayout()) {
            @Override
            protected void paintComponent(Graphics g) {
                super.paintComponent(g);
                g.setColor(backgroundColor);
                g.fillRect(0, 0, getWidth(), getHeight());
            }
        };

        // Panel login untuk memasukkan nama, IP server, dan port
        JPanel loginPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 10));
        loginPanel.setBackground(panelColor);

        JLabel nameLabel = new JLabel("Name:");
        nameLabel.setForeground(textColor);
        nameField = new JTextField(15);
        styleTextField(nameField, panelColor, primaryColor, textColor);

        JLabel serverLabel = new JLabel("Server:");
        serverLabel.setForeground(textColor);
        serverField = new JTextField("127.0.0.1", 10);
        styleTextField(serverField, panelColor, primaryColor, textColor);

        JLabel portLabel = new JLabel("Port:");
        portLabel.setForeground(textColor);
        portField = new JTextField("3355", 5);
        styleTextField(portField, panelColor, primaryColor, textColor);

        connectButton = new JButton("Connect");
        styleButton(connectButton, panelColor, primaryColor);
        connectButton.addActionListener(e -> connectToServer());

        loginPanel.add(nameLabel);
        loginPanel.add(nameField);
        loginPanel.add(Box.createHorizontalStrut(10));
        loginPanel.add(serverLabel);
        loginPanel.add(serverField);
        loginPanel.add(Box.createHorizontalStrut(10));
        loginPanel.add(portLabel);
        loginPanel.add(portField);
        loginPanel.add(Box.createHorizontalStrut(10));
        loginPanel.add(connectButton);

        mainPanel.add(loginPanel, BorderLayout.NORTH);

        // Komponen daftar room
        roomListModel = new DefaultListModel<>();
        roomList = new JList<>(roomListModel);
        roomList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        roomList.setBackground(panelColor);
        roomList.setForeground(textColor);
        roomList.setFont(new Font("Segoe UI", Font.PLAIN, 18)); // +50% font size
        roomList.setCellRenderer(new DefaultListCellRenderer() {
            @Override
            public Component getListCellRendererComponent(JList<?> list, Object value, int index, boolean isSelected, boolean cellHasFocus) {
                JLabel label = (JLabel) super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);
                if (value.toString().contains("(owner)")) {
                    label.setForeground(primaryColor);
                } else {
                    label.setForeground(textColor);
                }
                label.setBackground(isSelected ? backgroundColor.darker() : panelColor);
                return label;
            }
        });

        JScrollPane roomScroll = new JScrollPane(roomList);
        roomScroll.setBorder(BorderFactory.createTitledBorder(
            BorderFactory.createLineBorder(primaryColor, 1), 
            "Available Rooms"
        ));
        roomScroll.setPreferredSize(new Dimension(220, 0));
        roomScroll.getViewport().setBackground(panelColor);

        // Tombol room: buat, tutup, dan kick user
        createRoomButton = new JButton("Create Room");
        styleButton(createRoomButton, panelColor, primaryColor);
        createRoomButton.setEnabled(false);
        createRoomButton.addActionListener(e -> showRoomInputDialog());

        closeRoomButton = new JButton("Close Room");
        styleButton(closeRoomButton, panelColor, primaryColor);
        closeRoomButton.setEnabled(false);
        closeRoomButton.addActionListener(e -> closeCurrentRoom());

        kickUserButton = new JButton("Kick User");
        styleButton(kickUserButton, panelColor, primaryColor);
        kickUserButton.setEnabled(false);
        kickUserButton.addActionListener(e -> showKickUserDialog());

        roomInfoLabel = new JLabel("Double-click to join room. Single-click to select.");
        roomInfoLabel.setForeground(textColor);

        JPanel roomButtonPanel = new JPanel(new GridLayout(3, 1, 5, 5));
        roomButtonPanel.setBackground(panelColor);
        roomButtonPanel.add(createRoomButton);
        roomButtonPanel.add(closeRoomButton);
        roomButtonPanel.add(kickUserButton);

        JPanel roomPanel = new JPanel(new BorderLayout());
        roomPanel.setBackground(panelColor);
        roomPanel.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));
        roomPanel.add(roomInfoLabel, BorderLayout.NORTH);
        roomPanel.add(roomScroll, BorderLayout.CENTER);
        roomPanel.add(roomButtonPanel, BorderLayout.SOUTH);

        mainPanel.add(roomPanel, BorderLayout.WEST);

        // Chat area
        chatArea = new JTextArea();
        chatArea.setEditable(false);
        chatArea.setLineWrap(true);
        chatArea.setWrapStyleWord(true);
        chatArea.setFont(new Font("Segoe UI", Font.PLAIN, 14));
        chatArea.setBackground(panelColor);
        chatArea.setForeground(textColor);

        JScrollPane scroll = new JScrollPane(chatArea);
        scroll.setBorder(BorderFactory.createTitledBorder(
            BorderFactory.createLineBorder(primaryColor, 1), 
            "Chat Messages"
        ));
        scroll.getViewport().setBackground(panelColor);

        roomTitleLabel = new JLabel(" ");
        roomTitleLabel.setForeground(primaryColor);
        roomTitleLabel.setFont(new Font("Segoe UI", Font.BOLD, 16));
        roomTitleLabel.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        roomTitleLabel.setToolTipText("Click to see who's in this room");
        roomTitleLabel.addMouseListener(new MouseAdapter() {
            public void mouseClicked(MouseEvent e) {
                requestUserList();
            }
        });

        JPanel centerPanel = new JPanel(new BorderLayout());
        centerPanel.setBackground(panelColor);
        centerPanel.add(roomTitleLabel, BorderLayout.NORTH);
        centerPanel.add(scroll, BorderLayout.CENTER);

        mainPanel.add(centerPanel, BorderLayout.CENTER);

        // Message input panel
        JPanel bottomPanel = new JPanel(new BorderLayout());
        bottomPanel.setBackground(panelColor);

        messageField = new JTextField();
        messageField.setEnabled(false);
        styleTextField(messageField, panelColor, primaryColor, textColor);
        messageField.addActionListener(e -> sendMessage());
        bottomPanel.add(messageField, BorderLayout.CENTER);

        sendButton = new JButton("Send");
        styleButton(sendButton, panelColor, primaryColor);
        sendButton.setEnabled(false);
        sendButton.addActionListener(e -> sendMessage());
        bottomPanel.add(sendButton, BorderLayout.EAST);

        attachButton = new JButton("Attach");
        styleButton(attachButton, panelColor, primaryColor);
        attachButton.setEnabled(false);
        attachButton.addActionListener(e -> attachFile());
        bottomPanel.add(attachButton, BorderLayout.WEST);


        mainPanel.add(bottomPanel, BorderLayout.SOUTH);

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

        addWindowListener(new WindowAdapter() {
            public void windowClosing(WindowEvent e) {
                disconnect();
            }
        });

        add(mainPanel);
        pack();
        setLocationRelativeTo(null);
        setVisible(true);
    }

    // Helper styling functions
    private void styleButton(JButton button, Color bgColor, Color fgColor) {
        button.setBackground(bgColor);
        button.setForeground(fgColor);
        button.setFocusPainted(false);
        button.setFont(new Font("Segoe UI", Font.BOLD, 13));
        button.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createLineBorder(fgColor, 1),
            BorderFactory.createEmptyBorder(8, 20, 8, 20)
        ));
        button.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        button.setOpaque(true);

        button.addMouseListener(new MouseAdapter() {
            public void mouseEntered(MouseEvent e) {
                button.setBackground(bgColor.darker());
            }
            public void mouseExited(MouseEvent e) {
                button.setBackground(bgColor);
            }
        });
    }

    private void styleTextField(JTextField field, Color bgColor, Color borderColor, Color textColor) {
        field.setBackground(bgColor);
        field.setForeground(textColor);
        field.setCaretColor(textColor);
        field.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createLineBorder(borderColor, 1),
            BorderFactory.createEmptyBorder(8, 8, 8, 8)
        ));
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
            attachButton.setEnabled(true);
            chatArea.setText("");
            currentRoom = roomName;
            roomTitleLabel.setText("# " + roomName + " (click to see members)");
            
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

    private void attachFile() {
        JFileChooser chooser = new JFileChooser();
        int option = chooser.showOpenDialog(this);
        if (option == JFileChooser.APPROVE_OPTION) {
            File file = chooser.getSelectedFile();
            try {
                FileInputStream fis = new FileInputStream(file);
                ByteArrayOutputStream buffer = new ByteArrayOutputStream();
                byte[] temp = new byte[4096];
                int bytesRead;
                while ((bytesRead = fis.read(temp)) != -1) {
                    buffer.write(temp, 0, bytesRead);
                }
                fis.close();
                byte[] fileData = buffer.toByteArray();
    
                String header = "FILE:" + file.getName() + ":" + fileData.length;
                out.writeUTF(header);
                out.writeInt(fileData.length);
                out.write(fileData);
                out.flush();
                showMessage("You sent file '" + file.getName() + "'.");
            } catch (IOException e) {
                showMessage("Failed to send file: " + e.getMessage());
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
                        attachButton.setEnabled(false);
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
                } else if (msg.startsWith("FILE:")) {
                    handleIncomingFile(msg);
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

    private void handleIncomingFile(String header) throws IOException {
        String[] parts = header.split(":");
        if (parts.length < 4) return; // e.g., FILE:filename:size:sender
        String fileName = parts[1];
        int size = Integer.parseInt(parts[2]);
        String sender = parts[3];
    
        byte[] data = new byte[size];
        in.readFully(data);
    
        // Auto-save to Downloads
        File downloadDir = new File(System.getProperty("user.home"), "Downloads");
        if (!downloadDir.exists()) downloadDir.mkdirs();
        File savedFile = new File(downloadDir, fileName);
    
        try (FileOutputStream fos = new FileOutputStream(savedFile)) {
            fos.write(data);
        }
    
        showMessage("Received file '" + fileName + "' from " + sender +
                    ". Saved automatically to: " + savedFile.getAbsolutePath());
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
        SwingUtilities.invokeLater(() -> {
            try {
                UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
            } catch (Exception e) {
                e.printStackTrace();
            }
            new ChatClientGUI();
        });
    }
}