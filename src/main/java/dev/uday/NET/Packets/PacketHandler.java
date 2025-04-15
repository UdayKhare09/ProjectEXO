package dev.uday.NET.Packets;

import dev.uday.AI.AIHandler;
import dev.uday.NET.Server;

import java.util.Arrays;
import java.util.UUID;

public class PacketHandler {
    public static void handlePacket(byte[] packet, UUID sender) {
        byte packetType = packet[0];
        byte[] packetData = Arrays.copyOfRange(packet, 1, packet.length);
        System.out.println("Received packet of type " + packetType);
        switch (packetType) {
            //Handle message
            case 1:
                System.out.println("Handling message");
                MessageHandler.handleMessage(packetData, sender);
                break;
            //Handle image
            case 3:
                System.out.println("Handling image");
                ImageHandler.handleImagePacket(packetData, sender);
                break;
            //Handle AI prompts
            case 9:
                System.out.println("Handling AI prompt");
                AIHandler.handleAIPacket(packetData, sender);
                break;
            case 10:
                handleHeartbeat(packetData, sender);
        }
    }

    private static void handleHeartbeat(byte[] packet, UUID sender) {
        byte packetType = packet[0];
        if (packetType == 1) {
            Server.currentClients.get(sender).lastHeartbeatTime = System.currentTimeMillis();
        } else {
            System.out.println("Unknown ping packet type: " + packetType);
        }
    }
}
