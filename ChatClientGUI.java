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
    private JTextField nameField;
    private JTextField serverField;
    private JTextField portField;
    private DefaultListModel<String> roomListModel;
    private JList<String> roomList;
    private JLabel roomInfoLabel;

    private Socket socket;
    private DataInputStream in;
    private DataOutputStream out;
    private String clientName;
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

        roomInfoLabel = new JLabel("Klik dua kali untuk join room. Klik dua kali room lain untuk pindah.");

        JPanel roomPanel = new JPanel(new BorderLayout());
        roomPanel.add(roomInfoLabel, BorderLayout.NORTH);
        roomPanel.add(roomScroll, BorderLayout.CENTER);
        roomPanel.add(createRoomButton, BorderLayout.SOUTH);

        roomList.addMouseListener(new MouseAdapter() {
            public void mouseClicked(MouseEvent e) {
                if (e.getClickCount() == 2) {
                    String selectedRoom = roomList.getSelectedValue();
                    if (selectedRoom != null && selectedRoom.contains("(")) {
                        joinRoom(selectedRoom.split(" \\(")[0]);
                    }
                }
            }
        });

        add(roomPanel, BorderLayout.WEST);

        chatArea = new JTextArea(20, 50);
        chatArea.setEditable(false);
        JScrollPane scroll = new JScrollPane(chatArea);
        add(scroll, BorderLayout.CENTER);

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

    private void connectToServer() {
        String name = nameField.getText().trim();
        String server = serverField.getText().trim();
        int port = Integer.parseInt(portField.getText().trim());

        if (name.isEmpty()) {
            return;
        }

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
        } catch (IOException e) {
            showMessage("Gagal join room.");
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
                    SwingUtilities.invokeLater(() -> {
                        roomListModel.clear();
                        String raw = msg.substring(6).trim();
                        if (raw.isEmpty()) {
                            roomInfoLabel.setText("Belum ada room tersedia.");
                        } else {
                            String[] rooms = raw.split(",");
                            for (String room : rooms) {
                                if (!room.trim().isEmpty()) {
                                    roomListModel.addElement(room.trim());
                                }
                            }
                            roomInfoLabel.setText("Klik dua kali untuk join room. Klik dua kali room lain untuk pindah.");
                        }
                    });
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
