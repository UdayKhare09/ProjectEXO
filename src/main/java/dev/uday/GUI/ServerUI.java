package dev.uday.GUI;

import dev.uday.AI.Ollama;
import dev.uday.Client;
import dev.uday.Clients;
import dev.uday.NET.Server;
import io.github.ollama4j.models.response.Model;
import javafx.animation.Animation;
import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.geometry.Insets;
import javafx.scene.control.*;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.util.Duration;

import java.net.URL;
import java.util.List;
import java.util.Map;
import java.util.ResourceBundle;
import java.util.concurrent.atomic.AtomicInteger;

import static dev.uday.AI.Ollama.api;

public class ServerUI implements Initializable {
    @FXML private Label timeElapsedLabel;
    @FXML private ListView<String> onlineUsersListView;
    @FXML private ListView<String> availableModelsListView;
    @FXML private ListView<String> registeredUsersListView;
    @FXML private Label serverInfoLabel;
    @FXML private Button registerUserButton;
    @FXML private Button switchModelButton;
    @FXML private Button refreshRegisteredUsersButton;
    @FXML private Label currentModelLabel;

    private final ObservableList<String> onlineUsersList = FXCollections.observableArrayList();
    private final ObservableList<String> availableModelsList = FXCollections.observableArrayList();
    private final ObservableList<String> registeredUsersList = FXCollections.observableArrayList();
    private Timeline timeUpdater;
    private final AtomicInteger secondsElapsed = new AtomicInteger(0);

    @Override
    public void initialize(URL url, ResourceBundle resourceBundle) {
        // Initialize UI components
        onlineUsersListView.setItems(onlineUsersList);
        availableModelsListView.setItems(availableModelsList);
        registeredUsersListView.setItems(registeredUsersList);

        // Set server info
        serverInfoLabel.setText("IP: "+Server.ip+" | Port: " + Server.PORT);

        // Set current model info
        updateCurrentModelLabel();

        // Start time elapsed counter
        startTimeCounter();

        // Start periodic updates
        startPeriodicUpdates();

        // Load AI models
        loadAvailableModels();

        // Load registered users
        updateRegisteredUsersList();

        // Register button action
        registerUserButton.setOnAction(event -> showRegisterUserDialog());

        // Switch model button action
        switchModelButton.setOnAction(event -> switchModel());

        // Refresh registered users button action
        refreshRegisteredUsersButton.setOnAction(event -> updateRegisteredUsersList());
    }

    private void updateCurrentModelLabel() {
        currentModelLabel.setText("Current Model: " + Ollama.model);
    }

    private void startTimeCounter() {
        secondsElapsed.set(0);
        timeUpdater = new Timeline(new KeyFrame(Duration.seconds(1), event -> {
            secondsElapsed.incrementAndGet();
            updateTimeElapsedLabel();
        }));
        timeUpdater.setCycleCount(Animation.INDEFINITE);
        timeUpdater.play();
    }

    private void updateTimeElapsedLabel() {
        int seconds = secondsElapsed.get();
        int hours = seconds / 3600;
        int minutes = (seconds % 3600) / 60;
        int secs = seconds % 60;
        timeElapsedLabel.setText(String.format("Time Elapsed: %02d:%02d:%02d", hours, minutes, secs));
    }

    private void startPeriodicUpdates() {
        Timeline updateTimer = new Timeline(new KeyFrame(Duration.seconds(2), event -> {
            updateOnlineUsersList();
        }));
        updateTimer.setCycleCount(Animation.INDEFINITE);
        updateTimer.play();
    }

    private void updateOnlineUsersList() {
        Platform.runLater(() -> {
            onlineUsersList.clear();
            for (Client client : Clients.currentClients.values()) {
                onlineUsersList.add(client.username + " (" + client.IP + ")");
            }
        });
    }

    private void updateRegisteredUsersList() {
        Platform.runLater(() -> {
            registeredUsersList.clear();
            for (Map.Entry<String, String> entry : Clients.registeredClients.entrySet()) {
                registeredUsersList.add(entry.getKey());
            }
        });
    }

    private void loadAvailableModels() {
        new Thread(() -> {
            while (true){
                try {
                    List<Model> models = api.listModels();
                    Platform.runLater(() -> {
                        availableModelsList.clear();
                        for (Model model : models) {
                            availableModelsList.add(model.getName());
                        }
                        if (availableModelsList.isEmpty()) {
                            availableModelsList.add("No models found");
                        }
                    });
                } catch (Exception e) {
                    Platform.runLater(() -> {
                        availableModelsList.add("Error loading models");
                        availableModelsList.add("Make sure Ollama is running");
                    });
                }
                try {
                    Thread.sleep(5000);
                } catch (InterruptedException e) {
                    throw new RuntimeException(e);
                }
            }
        }).start();
    }

    private void switchModel() {
        String selectedModel = availableModelsListView.getSelectionModel().getSelectedItem();
        if (selectedModel == null || selectedModel.equals("No models found") ||
                selectedModel.equals("Error loading models") ||
                selectedModel.equals("Make sure Ollama is running")) {
            showAlert("Selection Error", "Please select a valid model from the list.");
            return;
        }

        // Set the new model
        Ollama.model = selectedModel;

        // Update the current model label
        updateCurrentModelLabel();

        showAlert("Model Changed", "AI model changed to: " + selectedModel);
    }

    private void showRegisterUserDialog() {
        Dialog<String[]> dialog = new Dialog<>();
        dialog.setTitle("Register New User");
        dialog.setHeaderText("Enter credentials for the new user");

        // Set the button types
        ButtonType registerButtonType = new ButtonType("Register", ButtonBar.ButtonData.OK_DONE);
        dialog.getDialogPane().getButtonTypes().addAll(registerButtonType, ButtonType.CANCEL);

        // Create the username and password fields
        TextField usernameField = new TextField();
        usernameField.setPromptText("Username");
        PasswordField passwordField = new PasswordField();
        passwordField.setPromptText("Password");

        // Create layout
        VBox content = new VBox(10);
        content.setPadding(new Insets(20, 150, 10, 10));

        HBox usernameBox = new HBox(10);
        usernameBox.getChildren().addAll(new Label("Username:"), usernameField);

        HBox passwordBox = new HBox(10);
        passwordBox.getChildren().addAll(new Label("Password:"), passwordField);

        content.getChildren().addAll(usernameBox, passwordBox);
        dialog.getDialogPane().setContent(content);

        // Request focus on the username field by default
        Platform.runLater(usernameField::requestFocus);

        // Convert the result to username/password when the register button is clicked
        dialog.setResultConverter(dialogButton -> {
            if (dialogButton == registerButtonType) {
                return new String[]{usernameField.getText(), passwordField.getText()};
            }
            return null;
        });

        dialog.showAndWait().ifPresent(this::registerNewUser);
    }

    private void registerNewUser(String[] credentials) {
        String username = credentials[0];
        String password = credentials[1];

        // Validation
        if (username.isEmpty() || password.isEmpty()) {
            showAlert("Registration Error", "Username and password cannot be empty.");
            return;
        }

        if (Clients.registeredClients.containsKey(username)) {
            showAlert("Registration Error", "Username already exists.");
            return;
        }

        // Save to file and add to registered clients
        boolean saveSuccess = Clients.saveRegisteredUser(username, password);

        if (saveSuccess) {
            updateRegisteredUsersList(); // Refresh the list
            showAlert("Registration Success", "User " + username + " has been registered successfully.\n" +
                    "Saved to ~/.exo/server/known.txt");
        } else {
            showAlert("Registration Warning", "User " + username + " added to memory but could not be saved to file.");
        }
    }

    private void showAlert(String title, String content) {
        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(content);
        alert.showAndWait();
    }
}