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
    private static Map<String, String> predefinedAnswers = new HashMap<>();

    static {
        predefinedAnswers.put("Was ist deine MAC-Adresse?", "Die MAC-Adresse ist geheim.");
        predefinedAnswers.put("Sind Kartoffeln eine richtige Mahlzeit?", "Ja, Kartoffeln sind eine nahrhafte Mahlzeit.");
    }

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
                    System.out.println("Unknown contact.");
                    continue;
                }
                String sendMessage = "SEND: " + name + " " + contactName + " " + message;
                buffer = sendMessage.getBytes("UTF-8");
                DatagramPacket sendPacket = new DatagramPacket(buffer, buffer.length, contact.address, contact.port);
                socket.send(sendPacket);
            } else if (line.startsWith("broadcast")) {
                String message = line.substring(10);
                for (Contact contact : contacts.values()) {
                    String sendMessage = "BROADCAST: " + name + " " + message;
                    buffer = sendMessage.getBytes("UTF-8");
                    DatagramPacket sendPacket = new DatagramPacket(buffer, buffer.length, contact.address, contact.port);
                    socket.send(sendPacket);
                }
            } else if (line.equals("list")) {
                listContacts();
            } else if (line.startsWith("query")) {
                String question = line.substring(6);
                String answer = predefinedAnswers.getOrDefault(question, "Ich weiß es nicht.");
                System.out.println("RESPONSE: " + answer);
            } else if (line.equalsIgnoreCase("stop")) {
                break;
            } else {
                System.out.println("Unknown command.");
            }
        }

        socket.close();
    }

    private static void processReceivedMessage(String message, InetAddress address, int port) {
        if (message.startsWith("REG: ")) {
            String[] parts = message.substring(5).split(" ");
            if (parts.length == 3) {
                String contactName = parts[0];
                String contactIp = parts[1];
                int contactPort = Integer.parseInt(parts[2]);
                contacts.put(contactName, new Contact(contactName, contactIp, contactPort, address, port));
                System.out.println(contactName + " has joined.");
            }
        } else if (message.startsWith("SEND: ")) {
            String[] parts = message.split(" ", 4);
            if (parts.length == 4) {
                String sender = parts[1];
                String receiver = parts[2];
                String msg = parts[3];
                System.out.println("From " + sender + ": " + msg);
            }
        } else if (message.startsWith("BROADCAST: ")) {
            String[] parts = message.split(" ", 3);
            if (parts.length == 3) {
                String sender = parts[1];
                String msg = parts[2];
                System.out.println("From " + sender + " (broadcast): " + msg);
            }
        } else if (message.startsWith("LIST: ")) {
            listContacts();
        } else {
            System.out.println("Unknown message: " + message);
        }
    }

    private static void listContacts() {
        System.out.println("Known contacts:");
        for (String contactName : contacts.keySet()) {
            System.out.println(contactName);
        }
    }

    private static String readString() throws IOException {
        BufferedReader br = new BufferedReader(new InputStreamReader(System.in));
        return br.readLine();
    }

    private static class Contact {
        String name;
        String ip;
        int port;
        InetAddress address;
        int originalPort;

        Contact(String name, String ip, int port, InetAddress address, int originalPort) {
            this.name = name;
            this.ip = ip;
            this.port = port;
            this.address = address;
            this.originalPort = originalPort;
        }
    }
}
