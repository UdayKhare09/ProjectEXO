package dev.uday;

import dev.uday.NET.Server;

import java.net.Socket;
import java.security.PublicKey;
import java.util.UUID;

public class Client {
    public Socket clientSocket;
    public String IP;
    public String username;
    public UUID uuid;
    public PublicKey publicKey;
    public Server.ClientHandler clientHandler;
    public long lastHeartbeatTime;

    public Client(Socket clientSocket, UUID uuid) {
        this.clientSocket = clientSocket;
        IP = clientSocket.getInetAddress().toString();
        this.uuid = uuid;
        this.username = "Unknown";
    }
}
