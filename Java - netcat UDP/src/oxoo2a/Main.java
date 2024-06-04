package oxoo2a;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.util.HashMap;
import java.util.Map;

public class Main {

    private static void fatal(String comment) {
        System.out.println(comment);
        System.exit(-1);
    }

    private static final int packetSize = 4096;
    private static String name;
    private static int port;
    private static Map<String, Contact> contacts = new HashMap<>();

    public static void main(String[] args) throws IOException {
        if (args.length < 3) {
            fatal("Usage: \"<netcat> -l <name> <port>\" or \"netcat <name> <ip> <port>\"");
        }
        name = args[1];
        port = Integer.parseInt(args[args.length - 1]);

        if (args[0].equalsIgnoreCase("-l")) {
            listenAndTalk(port);
        } else {
            String otherHost = args[args.length - 2];
            connectAndTalk(otherHost, port);
        }
    }

    private static void listenAndTalk(int port) throws IOException {
        DatagramSocket socket = new DatagramSocket(port);
        byte[] buffer = new byte[packetSize];
        String line;

        while (true) {
            DatagramPacket packet = new DatagramPacket(buffer, buffer.length);
            socket.receive(packet);
            line = new String(buffer, 0, packet.getLength(), "UTF-8");
            processReceivedMessage(line, packet.getAddress(), packet.getPort());
        }
    }

    private static void connectAndTalk(String otherHost, int otherPort) throws IOException {
        InetAddress otherAddress = InetAddress.getByName(otherHost);
        DatagramSocket socket = new DatagramSocket();
        byte[] buffer = new byte[packetSize];
        String line;

        // Send registration message
        String registrationMessage = String.format("REG: %s %s %d", name, InetAddress.getLocalHost().getHostAddress(), port);
        buffer = registrationMessage.getBytes("UTF-8");
        DatagramPacket registrationPacket = new DatagramPacket(buffer, buffer.length, otherAddress, otherPort);
        socket.send(registrationPacket);

        while (true) {
            line = readString();
            if (line.startsWith("send")) {
                String[] parts = line.split(" ", 3);
                if (parts.length < 3) {
                    System.out.println("Invalid command. Use: send <name> <message>");
                    continue;
                }
                String contactName = parts[1];
                String message = parts[2];

                Contact contact = contacts.get(contactName);
                if (contact == null) {
                    System.out.println("No such contact: " + contactName);
                    continue;
                }

                buffer = message.getBytes("UTF-8");
                DatagramPacket messagePacket = new DatagramPacket(buffer, buffer.length, contact.address, contact.port);
                socket.send(messagePacket);
            }
        }
    }

    private static void processReceivedMessage(String message, InetAddress address, int port) {
        if (message.startsWith("REG:")) {
            String[] parts = message.substring(4).split(" ");
            if (parts.length != 3) {
                System.out.println("Invalid registration message: " + message);
                return;
            }
            String contactName = parts[0];
            String ipAddress = parts[1];
            int contactPort = Integer.parseInt(parts[2]);

            contacts.put(contactName, new Contact(contactName, ipAddress, contactPort, address));
            System.out.println("Registered new contact: " + contactName + " at " + ipAddress + ":" + contactPort);
        } else {
            System.out.println("Received message: " + message);
        }
    }

    private static String readString() {
        BufferedReader br = null;
        boolean again = false;
        String input = null;
        do {
            try {
                if (br == null) {
                    br = new BufferedReader(new InputStreamReader(System.in));
                }
                input = br.readLine();
            } catch (Exception e) {
                System.out.printf("Exception: %s\n", e.getMessage());
                again = true;
            }
        } while (again);
        return input;
    }

    static class Contact {
        String name;
        String ipAddress;
        int port;
        InetAddress address;

        Contact(String name, String ipAddress, int port, InetAddress address) {
            this.name = name;
            this.ipAddress = ipAddress;
            this.port = port;
            this.address = address;
        }
    }
}
