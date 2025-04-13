package dev.uday;

import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class Clients {
    public static ConcurrentHashMap<UUID, Client> currentClients = new ConcurrentHashMap<>();
    public static HashMap<String, String> registeredClients = new HashMap<>();

    private static final String USER_HOME = System.getProperty("user.home");
    private static final String CONFIG_DIR = USER_HOME + File.separator + ".exo" +
            File.separator + "server";
    private static final String KNOWN_USERS_FILE = CONFIG_DIR + File.separator + "known.users.exo";

    static {
        loadRegisteredUsers();
    }

    public static void loadRegisteredUsers() {
        try {
            // Create directory structure if it doesn't exist
            Files.createDirectories(Paths.get(CONFIG_DIR));

            File knownFile = new File(KNOWN_USERS_FILE);
            if (!knownFile.exists()) {
                // Create the file with default users
                try (PrintWriter writer = new PrintWriter(new FileWriter(knownFile))) {
                    writer.println("uday:Uday");
                    writer.println("admin:admin");
                    writer.println("test:test");
                }
            }

            // Load users from file
            registeredClients.clear();
            try (BufferedReader reader = new BufferedReader(new FileReader(knownFile))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    if (line.trim().isEmpty()) continue;
                    String[] parts = line.split(":", 2);
                    if (parts.length == 2) {
                        registeredClients.put(parts[0], parts[1]);
                    }
                }
            }

            System.out.println("Loaded " + registeredClients.size() + " registered users");
        } catch (IOException e) {
            System.err.println("Error loading registered users: " + e.getMessage());
        }
    }

    public static boolean saveRegisteredUser(String username, String password) {
        try {
            // Add to in-memory map
            registeredClients.put(username, password);

            // Append to file
            try (PrintWriter writer = new PrintWriter(new FileWriter(KNOWN_USERS_FILE, true))) {
                writer.println(username + ":" + password);
            }

            return true;
        } catch (IOException e) {
            System.err.println("Error saving registered user: " + e.getMessage());
            return false;
        }
    }
}