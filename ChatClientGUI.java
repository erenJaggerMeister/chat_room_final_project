import javax.swing.*;
import javax.swing.UIManager;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.io.*;
import java.net.*;

public class ChatClientGUI extends JFrame {
    private JTextArea chatArea;
    private JTextField messageField;
    private JButton sendButton;
    private JButton connectButton;
    private JTextField nameField;
    private JTextField serverField;
    private JTextField portField;
    
    private Socket socket;
    private DataInputStream in;
    private DataOutputStream out;
    private String clientName;
    private boolean connected = false;
    
    public ChatClientGUI() {
        initializeGUI();
    }
    
    private void initializeGUI() {
        setTitle("Chat Client");
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setLayout(new BorderLayout());
        
        // Connection panel
        JPanel connectionPanel = new JPanel(new FlowLayout());
        connectionPanel.add(new JLabel("Name:"));
        nameField = new JTextField(10);
        connectionPanel.add(nameField);
        
        connectionPanel.add(new JLabel("Server:"));
        serverField = new JTextField("127.0.0.1", 10);
        connectionPanel.add(serverField);
        
        connectionPanel.add(new JLabel("Port:"));
        portField = new JTextField("3355", 5);
        connectionPanel.add(portField);
        
        connectButton = new JButton("Connect");
        connectButton.addActionListener(e -> connectToServer());
        connectionPanel.add(connectButton);
        
        add(connectionPanel, BorderLayout.NORTH);
        
        // Chat area
        chatArea = new JTextArea(20, 50);
        chatArea.setEditable(false);
        chatArea.setBackground(Color.WHITE);
        chatArea.setFont(new Font("Arial", Font.PLAIN, 12));
        JScrollPane scrollPane = new JScrollPane(chatArea);
        scrollPane.setVerticalScrollBarPolicy(JScrollPane.VERTICAL_SCROLLBAR_ALWAYS);
        add(scrollPane, BorderLayout.CENTER);
        
        // Message input panel
        JPanel messagePanel = new JPanel(new BorderLayout());
        messageField = new JTextField();
        messageField.setEnabled(false);
        messageField.addActionListener(e -> sendMessage());
        
        sendButton = new JButton("Send");
        sendButton.setEnabled(false);
        sendButton.addActionListener(e -> sendMessage());
        
        messagePanel.add(messageField, BorderLayout.CENTER);
        messagePanel.add(sendButton, BorderLayout.EAST);
        add(messagePanel, BorderLayout.SOUTH);
        
        // Window close handler
        addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent e) {
                disconnect();
                System.exit(0);
            }
        });
        
        pack();
        setLocationRelativeTo(null);
        setVisible(true);
    }
    
    private void connectToServer() {
        String name = nameField.getText().trim();
        String server = serverField.getText().trim();
        String portText = portField.getText().trim();
        
        if (name.isEmpty()) {
            JOptionPane.showMessageDialog(this, "Please enter your name!");
            return;
        }
        
        try {
            int port = Integer.parseInt(portText);
            socket = new Socket(server, port);
            in = new DataInputStream(socket.getInputStream());
            out = new DataOutputStream(socket.getOutputStream());
            
            // Read welcome message and send name
            String welcomeMessage = in.readUTF();
            appendToChatArea("Server: " + welcomeMessage);
            out.writeUTF(name);
            clientName = name;
            
            connected = true;
            
            // Enable/disable UI components
            connectButton.setEnabled(false);
            nameField.setEnabled(false);
            serverField.setEnabled(false);
            portField.setEnabled(false);
            messageField.setEnabled(true);
            sendButton.setEnabled(true);
            messageField.requestFocus();
            
            appendToChatArea("Connected to server as: " + clientName);
            
            // Start thread for receiving messages
            Thread readThread = new Thread(this::receiveMessages);
            readThread.setDaemon(true);
            readThread.start();
            
        } catch (NumberFormatException e) {
            JOptionPane.showMessageDialog(this, "Invalid port number!");
        } catch (IOException e) {
            JOptionPane.showMessageDialog(this, "Could not connect to server: " + e.getMessage());
        }
    }
    
    private void receiveMessages() {
        try {
            while (connected && socket != null && !socket.isClosed()) {
                String message = in.readUTF();
                SwingUtilities.invokeLater(() -> appendToChatArea(message));
            }
        } catch (IOException e) {
            if (connected) {
                SwingUtilities.invokeLater(() -> {
                    appendToChatArea("Disconnected from server.");
                    disconnect();
                });
            }
        }
    }
    
    private void sendMessage() {
        String message = messageField.getText().trim();
        if (message.isEmpty() || !connected) {
            return;
        }
        
        try {
            out.writeUTF(message);
            messageField.setText("");
            
            if (message.equalsIgnoreCase("exit")) {
                disconnect();
            } else {
                appendToChatArea("Me : "+message);
            }
        } catch (IOException e) {
            appendToChatArea("Error sending message: " + e.getMessage());
            disconnect();
        }
    }
    
    private void disconnect() {
        connected = false;
        try {
            if (out != null) {
                out.writeUTF("exit");
            }
            if (socket != null) {
                socket.close();
            }
        } catch (IOException e) {
            // Ignore errors during disconnect
        }
        
        // Reset UI
        connectButton.setEnabled(true);
        nameField.setEnabled(true);
        serverField.setEnabled(true);
        portField.setEnabled(true);
        messageField.setEnabled(false);
        sendButton.setEnabled(false);
        messageField.setText("");
        
        appendToChatArea("Disconnected from server.");
    }
    
    private void appendToChatArea(String message) {
        SwingUtilities.invokeLater(() -> {
            chatArea.append(message + "\n");
            chatArea.setCaretPosition(chatArea.getDocument().getLength());
        });
    }
    
    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            new ChatClientGUI();
        });
    }
}