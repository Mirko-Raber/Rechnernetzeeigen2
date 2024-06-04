package oxoo2a;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.HashMap;
import java.util.Map;

public class Main {

    private static void fatal ( String comment ) {
        System.out.println(comment);
        System.exit(-1);
    }

    public static void main(String[] args) throws IOException {
        if (args.length != 2)
            fatal("Usage: \"<netcat> -l <port>\" or \"netcat <ip> <port>\"");
        int port = Integer.parseInt(args[1]);
        if (args[0].equalsIgnoreCase("-l"))
            Server(port);
        else
            Client(args[0], port);
    }

    // ************************************************************************
    // Server
    // ************************************************************************
    private static Map<String, ClientHandler> clients = new HashMap<>();

    private static void Server (int port) throws IOException {
        ServerSocket serverSocket = new ServerSocket(port);
        System.out.println("Server started on port " + port);
        while (true) {
            Socket clientSocket = serverSocket.accept();
            new Thread(() -> {
                try {
                    serveClient(clientSocket);
                } catch (IOException e) {
                    System.out.println("Error serving client: " + e.getMessage());
                }
            }).start();
        }
    }

    private static void serveClient(Socket clientSocket) throws IOException {
        BufferedReader reader = new BufferedReader(new InputStreamReader(clientSocket.getInputStream()));
        PrintWriter writer = new PrintWriter(clientSocket.getOutputStream(), true);

        // Registration
        writer.println("Enter your name: ");
        String name = reader.readLine();
        synchronized (clients) {
            if (clients.containsKey(name)) {
                writer.println("Name already taken. Disconnecting...");
                clientSocket.close();
                return;
            } else {
                clients.put(name, new ClientHandler(name, clientSocket, reader, writer));
                System.out.println(name + " has joined.");
            }
        }

        // Communication
        String message;
        while ((message = reader.readLine()) != null) {
            if (message.startsWith("send ")) {
                String[] parts = message.split(" ", 3);
                if (parts.length < 3) {
                    writer.println("Invalid command. Use: send <name> <message>");
                    continue;
                }
                String targetName = parts[1];
                String msg = parts[2];
                sendMessage(name, targetName, msg);
            } else if (message.equalsIgnoreCase("stop")) {
                break;
            } else {
                writer.println("Unknown command.");
            }
        }

        // Cleanup
        synchronized (clients) {
            clients.remove(name);
            System.out.println(name + " has left.");
        }
        clientSocket.close();
    }

    private static void sendMessage(String from, String to, String message) {
        synchronized (clients) {
            ClientHandler handler = clients.get(to);
            if (handler != null) {
                handler.sendMessage("From " + from + ": " + message);
            } else {
                ClientHandler sender = clients.get(from);
                if (sender != null) {
                    sender.sendMessage(to + " is not available.");
                }
            }
        }
    }

    // ************************************************************************
    // ClientHandler
    // ************************************************************************
    private static class ClientHandler {
        private String name;
        private Socket socket;
        private BufferedReader reader;
        private PrintWriter writer;

        public ClientHandler(String name, Socket socket, BufferedReader reader, PrintWriter writer) {
            this.name = name;
            this.socket = socket;
            this.reader = reader;
            this.writer = writer;
        }

        public void sendMessage(String message) {
            writer.println(message);
        }
    }

    // ************************************************************************
    // Client
    // ************************************************************************
    private static void Client(String serverHost, int serverPort) throws IOException {
        InetAddress serverAddress = InetAddress.getByName(serverHost);
        Socket serverSocket = new Socket(serverAddress, serverPort);
        PrintWriter writer = new PrintWriter(serverSocket.getOutputStream(), true);
        BufferedReader reader = new BufferedReader(new InputStreamReader(serverSocket.getInputStream()));
        BufferedReader consoleReader = new BufferedReader(new InputStreamReader(System.in));

        // Registration
        String serverMessage = reader.readLine();
        System.out.println(serverMessage);
        String name = consoleReader.readLine();
        writer.println(name);

        // Communication
        new Thread(() -> {
            String serverResponse;
            try {
                while ((serverResponse = reader.readLine()) != null) {
                    System.out.println(serverResponse);
                }
            } catch (IOException e) {
                System.out.println("Connection closed.");
            }
        }).start();

        String userInput;
        while ((userInput = consoleReader.readLine()) != null) {
            writer.println(userInput);
            if (userInput.equalsIgnoreCase("stop")) break;
        }

        serverSocket.close();
    }
}
