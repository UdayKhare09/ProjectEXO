package dev.uday.NET;

import dev.uday.Client;
import dev.uday.Clients;
import dev.uday.NET.Packets.PacketHandler;
import org.slf4j.Logger;

import javax.crypto.*;
import java.io.*;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.security.*;
import java.security.spec.InvalidKeySpecException;
import java.security.spec.X509EncodedKeySpec;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

public class Server {
    private ServerSocket serverSocket;
    public static int PORT = 2005;
    public static ConcurrentHashMap<UUID, Client> currentClients = Clients.currentClients;
    private static KeyPair keyPair;
    public static String ip;
    public static String serverName = "SoloLeveler";
    Logger logger;

    // Heartbeat executor
    private static final ScheduledExecutorService heartbeatExecutor = new ScheduledThreadPoolExecutor(1);
    // Timeout after 15 seconds of no response (3 missed heartbeats)
    private static final long HEARTBEAT_TIMEOUT_MS = 10000;

    public Server(int port) {
        if (port != 0) {
            PORT = port;
        }
        try {
            logger = org.slf4j.LoggerFactory.getLogger(Server.class);
            serverSocket = new ServerSocket(PORT);
            ip = InetAddress.getLocalHost().getHostAddress();
            logger.info("Server started on port: {}", PORT);
            keyPair = generateKeyPair();

            startHeartbeatMonitor();
        } catch (IOException | NoSuchAlgorithmException e) {
            logger.error(e.getMessage());
        }
    }

    public static void closeExecutor() {
        try {
            heartbeatExecutor.shutdown();
            if (!heartbeatExecutor.awaitTermination(60, TimeUnit.SECONDS)) {
                heartbeatExecutor.shutdownNow();
            }
        } catch (InterruptedException e) {
            heartbeatExecutor.shutdownNow();
        }
    }

    private void startHeartbeatMonitor() {
        heartbeatExecutor.scheduleAtFixedRate(() -> {
            if (currentClients.isEmpty()) {
                return;
            }

            // Current time to compare against
            long currentTime = System.currentTimeMillis();

            // Check each client for timeout
            currentClients.forEach((uuid, client) -> {
                if (currentTime - client.lastHeartbeatTime > HEARTBEAT_TIMEOUT_MS) {
                    logger.info("Client {} ({}): No heartbeat, disconnecting", client.username, client.IP);
                    if (client.clientHandler != null) {
                        client.clientHandler.closeClientSocket();
                    } else {
                        currentClients.remove(uuid);
                    }
                } else {
                    // Send heartbeat to client
                    if (client.clientHandler != null) {
                        client.clientHandler.sendHeartbeat();
                    }
                }
            });
        }, 5, 5, TimeUnit.SECONDS);  // Run every 5 seconds
    }

    public static UUID getUUIDFromUsername(String recipientUsername) {
        for (Client client : currentClients.values()) {
            if (client.username.equals(recipientUsername)) {
                return client.uuid;
            }
        }
        return null;
    }

    public void start() {
        try {
            while (true) {
                Socket clientSocket = serverSocket.accept();
                logger.info("Client connected: {}", clientSocket.getInetAddress());
                UUID uuid = generateUniqueUUID();
                Client client = new Client(clientSocket, uuid);
                // Set initial heartbeat time
                client.lastHeartbeatTime = System.currentTimeMillis();
                currentClients.put(uuid, client);
                logger.info(uuid.toString());

                ClientHandler clientHandler = new ClientHandler(clientSocket, keyPair.getPublic(), uuid);
                currentClients.get(uuid).clientHandler = clientHandler;
                clientHandler.start();
            }
        } catch (IOException | NoSuchPaddingException | NoSuchAlgorithmException e) {
            logger.error(e.getMessage());
        } finally {
            stop();
        }
    }

    private UUID generateUniqueUUID() {
        UUID uuid;
        do {
            uuid = UUID.randomUUID();
        } while (currentClients.containsKey(uuid));
        return uuid;
    }

    private KeyPair generateKeyPair() throws NoSuchAlgorithmException {
        KeyPairGenerator keyGen = KeyPairGenerator.getInstance("RSA");
        keyGen.initialize(2048);
        return keyGen.generateKeyPair();
    }

    public void stop() {
        try {
            if (serverSocket != null) {
                serverSocket.close();
            }
        } catch (IOException e) {
            logger.error(e.getMessage());
        }
    }

    public static void broadcast(byte[] bytes) {
        for (Client client : currentClients.values()) {
            client.clientHandler.sendPacket(bytes);
        }
    }

    private static void broadcastCurrentClients() {
        if (currentClients.isEmpty()) {
            return;
        }
        StringBuilder sb = new StringBuilder();
        for (Client client : currentClients.values()) {
            sb.append(client.username).append(",");
        }
        String message = sb.toString();
        byte messageType = 0;
        byte[] messageBytes = message.getBytes();
        byte[] bytes = new byte[messageBytes.length + 1];
        bytes[0] = messageType;
        System.arraycopy(messageBytes, 0, bytes, 1, messageBytes.length);
        broadcast(bytes);
    }

    public static class ClientHandler extends Thread {
        private final Socket clientSocket;
        private final PublicKey publicKey;
        public final UUID uuid;
        private final Cipher cipher;
        private DataInputStream inputStream;
        private DataOutputStream outputStream;
        public String username;
        public String password;
        private Logger logger;
        private final AtomicBoolean isRunning = new AtomicBoolean(true);

        public ClientHandler(Socket socket, PublicKey publicKey, UUID uuid) throws NoSuchPaddingException, NoSuchAlgorithmException {
            this.clientSocket = socket;
            this.publicKey = publicKey;
            this.uuid = uuid;
            logger = org.slf4j.LoggerFactory.getLogger(ClientHandler.class);
            try {
                inputStream = new DataInputStream(clientSocket.getInputStream());
                outputStream = new DataOutputStream(clientSocket.getOutputStream());
            } catch (IOException e) {
                logger.error(e.getMessage());
            }
            cipher = Cipher.getInstance("RSA");
        }

        @Override
        public void run() {
            try {
                keyExchange();
                auth();
                broadcastCurrentClients();
                startReceivingPacket();
            } catch (IOException | InvalidKeySpecException | NoSuchAlgorithmException | IllegalBlockSizeException |
                     BadPaddingException | InvalidKeyException e) {
                logger.error(e.getMessage());
            } finally {
                closeClientSocket();
            }
        }

        private void auth() throws IOException, InvalidKeyException, IllegalBlockSizeException, BadPaddingException {
            username = decryptData(inputStream);
            password = decryptData(inputStream);

            if (Clients.registeredClients.containsKey(username)) {
                if (isClientLoggedIn(username)) {
                    outputStream.writeInt(3);
                    closeClientSocket();
                } else if (Clients.registeredClients.get(username).equals(password)) {
                    outputStream.writeInt(1);
                    currentClients.get(uuid).username = username;
                    logger.info("User {} logged in", username);
                } else {
                    outputStream.writeInt(2);
                    closeClientSocket();
                }
            } else {
                outputStream.writeInt(0);
                closeClientSocket();
            }
        }

        private String decryptData(DataInputStream inputStream) throws IOException, InvalidKeyException, IllegalBlockSizeException, BadPaddingException {
            int length = inputStream.readInt();
            byte[] dataBytes = new byte[length];
            inputStream.readFully(dataBytes);
            cipher.init(Cipher.DECRYPT_MODE, keyPair.getPrivate());
            return new String(cipher.doFinal(dataBytes));
        }

        private void keyExchange() throws IOException, InvalidKeySpecException, NoSuchAlgorithmException {
            byte[] publicKeyBytes = publicKey.getEncoded();
            outputStream.writeInt(publicKeyBytes.length);
            outputStream.write(publicKeyBytes);
            outputStream.flush();

            int length = inputStream.readInt();
            byte[] receivedClientPublicKeyBytes = new byte[length];
            inputStream.readFully(receivedClientPublicKeyBytes);

            currentClients.get(uuid).publicKey = KeyFactory.getInstance("RSA")
                    .generatePublic(new X509EncodedKeySpec(receivedClientPublicKeyBytes));
        }

        public void sendPacket(byte[] bytes) {
            try {
                if (!isRunning.get()) return;
                int chunkSize = 240;
                int totalChunks = (int) Math.ceil((double) bytes.length / chunkSize);

                cipher.init(Cipher.ENCRYPT_MODE, currentClients.get(uuid).publicKey);
                byte[] header = ("SIZE:" + bytes.length + ";CHUNKS:" + totalChunks).getBytes();
                byte[] encryptedHeader = cipher.doFinal(header);
                outputStream.writeInt(encryptedHeader.length);
                outputStream.write(encryptedHeader);
                outputStream.flush();

                for (int i = 0; i < totalChunks; i++) {
                    int start = i * chunkSize;
                    int end = Math.min(bytes.length, start + chunkSize);
                    byte[] chunk = new byte[end - start];
                    System.arraycopy(bytes, start, chunk, 0, end - start);

                    cipher.init(Cipher.ENCRYPT_MODE, currentClients.get(uuid).publicKey);
                    byte[] encryptedChunk = cipher.doFinal(chunk);
                    outputStream.writeInt(encryptedChunk.length);
                    outputStream.write(encryptedChunk);
                    outputStream.flush();
                }
            } catch (InvalidKeyException | IllegalBlockSizeException | BadPaddingException | IOException e) {
                logger.error(e.getMessage());
            }
        }

        public void startReceivingPacket() {
            try {
                while (isRunning.get()) {
                    byte[] decryptedHeader = receiveAndDecryptData();
                    String header = new String(decryptedHeader);
                    int totalSize = Integer.parseInt(header.split(";")[0].split(":")[1]);
                    int totalChunks = Integer.parseInt(header.split(";")[1].split(":")[1]);

                    ByteArrayOutputStream completePacket = new ByteArrayOutputStream();
                    for (int i = 0; i < totalChunks; i++) {
                        byte[] decryptedChunk = receiveAndDecryptData();
                        completePacket.write(decryptedChunk);
                    }

                    byte[] decryptedBytes = completePacket.toByteArray();
                    PacketHandler.handlePacket(decryptedBytes, uuid);
                }
            } catch (IOException | InvalidKeyException | IllegalBlockSizeException | BadPaddingException e) {
                logger.error(e.getMessage());
            } finally {
                closeClientSocket();
            }
        }

        private byte[] receiveAndDecryptData() throws IOException, InvalidKeyException, IllegalBlockSizeException, BadPaddingException {
            int length = inputStream.readInt();
            byte[] dataBytes = new byte[length];
            inputStream.readFully(dataBytes);
            cipher.init(Cipher.DECRYPT_MODE, keyPair.getPrivate());
            return cipher.doFinal(dataBytes);
        }

        private void closeClientSocket() {
            try {
                clientSocket.close();
                currentClients.remove(uuid);
                broadcastCurrentClients();
                this.interrupt();
            } catch (IOException e) {
                logger.error(e.getMessage());
            }
        }

        private boolean isClientLoggedIn(String username) {
            return currentClients.values().stream().anyMatch(client -> client.username.equals(username));
        }

        public void sendHeartbeat() {
            if (!isRunning.get()) return;
            byte[] packetType= {10,1};
            sendPacket(packetType);
        }
    }

    Thread broadcastThread = new Thread(() -> {
        while (true) {
            broadcastCurrentClients();
            try {
                Thread.sleep(1000);
            } catch (InterruptedException e) {
                logger.error(e.getMessage());
            }
        }
    });
}