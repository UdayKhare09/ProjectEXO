package dev.uday.GUI;

import atlantafx.base.theme.CupertinoDark;
import com.pixelduke.window.ThemeWindowManagerFactory;
import com.pixelduke.window.Win10ThemeWindowManager;
import com.pixelduke.window.Win11ThemeWindowManager;
import dev.uday.NET.Server;
import dev.uday.NET.ServerBroadcasting;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.fxml.FXMLLoader;
import javafx.geometry.Rectangle2D;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.image.Image;
import javafx.scene.layout.StackPane;
import javafx.scene.media.Media;
import javafx.scene.media.MediaPlayer;
import javafx.scene.media.MediaView;
import javafx.stage.Screen;
import javafx.stage.Stage;
import javafx.stage.StageStyle;

import java.net.URL;
import java.util.Objects;

public class ServerApp extends Application {
    private Stage mainStage;

    @Override
    public void start(Stage stage) {
        showSplashScreen();
    }

    @Override
    public void stop() {
        System.exit(0);
    }

    private void showSplashScreen() {
        try {
            System.out.println("Starting splash screen...");

            // Create a new stage for the splash screen
            Stage splashStage = new Stage();
            splashStage.initStyle(StageStyle.UNDECORATED);

            try {
                Image icon = new Image(Objects.requireNonNull(getClass().getClassLoader().getResourceAsStream("icons/logo.png")));
                splashStage.getIcons().add(icon);
            } catch (Exception e) {
                System.err.println("Could not load splash screen icon: " + e.getMessage());
            }

            // Check if video exists before trying to load it
            URL videoUrl = getClass().getClassLoader().getResource("splash.mp4");
            if (videoUrl == null) {
                System.err.println("Splash video not found, skipping to config dialog");
                splashStage.close();
                Platform.runLater(this::showConfigDialog);
                return;
            }

            System.out.println("Loading splash video from: " + videoUrl);

            // Load the splash video
            Media media = new Media(videoUrl.toExternalForm());
            MediaPlayer mediaPlayer = new MediaPlayer(media);
            MediaView mediaView = new MediaView(mediaPlayer);

            // Create a container for the video
            StackPane root = new StackPane();
            root.getChildren().add(mediaView);

            // Set preferred size for the video
            mediaView.setFitWidth(852);
            mediaView.setFitHeight(480);

            // Create scene and display
            Scene scene = new Scene(root, 852, 480);
            splashStage.setScene(scene);

            // Center the splash screen
            Rectangle2D screenBounds = Screen.getPrimary().getVisualBounds();
            splashStage.setX((screenBounds.getWidth() - 852) / 2);
            splashStage.setY((screenBounds.getHeight() - 480) / 2);

            mediaPlayer.setOnReady(() -> {
                System.out.println("Media player ready, duration: " + media.getDuration());
                // Show the splash screen only when media is ready
                splashStage.show();
                splashStage.setAlwaysOnTop(true);
                mediaPlayer.play();
            });

            // When video ends, show the config dialog and close the splash screen
            mediaPlayer.setOnEndOfMedia(() -> {
                System.out.println("Video ended, showing config dialog");
                mediaPlayer.dispose();
                Platform.runLater(() -> {
                    splashStage.close();
                    showConfigDialog();
                });
            });

            // If something goes wrong with the video
            mediaPlayer.setOnError(() -> {
                System.err.println("Error playing splash video: " + mediaPlayer.getError());
                mediaPlayer.dispose();
                Platform.runLater(() -> {
                    splashStage.close();
                    showConfigDialog();
                });
            });

        } catch (Exception e) {
            System.err.println("Error showing splash screen: " + e.getMessage());
            e.printStackTrace();
            Platform.runLater(this::showConfigDialog);
        }
    }

    private void showConfigDialog() {
        System.out.println("Showing configuration dialog...");
        // Create and show the configuration dialog
        ServerConfigDialog.showConfigDialog();

        System.out.println("Config dialog completed: " + ServerConfigDialog.isConfigCompleted());

        if (ServerConfigDialog.isConfigCompleted()) {
            // Update server settings with user's configuration
            Server.PORT = ServerConfigDialog.getPort();
            Server.serverName = ServerConfigDialog.getServerName();
            System.out.println("Server configuration set: " + Server.serverName + " on port " + Server.PORT);

            // Start server in a background thread AFTER configuration
            Thread serverThread = new Thread(() -> {
                Server server = new Server(Server.PORT);
                server.start();
            });
            serverThread.setDaemon(true);
            serverThread.start();

            // Start broadcasting thread AFTER configuration
            Thread broadcastingThread = new Thread(new ServerBroadcasting());
            broadcastingThread.setDaemon(true);
            broadcastingThread.start();

            // Now show the main UI
            Platform.runLater(this::showMainUI);
        } else {
            // User cancelled, exit application
            System.out.println("User cancelled configuration, exiting");
            Platform.exit();
        }
    }

    private void showMainUI() {
        try {
            System.out.println("Showing main UI");

            // Set the theme
            Application.setUserAgentStylesheet(new CupertinoDark().getUserAgentStylesheet());

            // Load FXML
            FXMLLoader loader = new FXMLLoader(getClass().getClassLoader().getResource("ServerUI.fxml"));
            Parent root = loader.load();

            // Create scene
            Scene scene = new Scene(root, 800, 600);

            // Create and configure main stage
            mainStage = new Stage();
            mainStage.setTitle("ProjectEXO Server - " + Server.serverName);

            // Set application icon
            try {
                Image icon = new Image(Objects.requireNonNull(getClass().getClassLoader().getResourceAsStream("icons/logo.png")));
                mainStage.getIcons().add(icon);
            } catch (Exception e) {
                System.err.println("Could not load application icon: " + e.getMessage());
            }

            mainStage.setScene(scene);
            mainStage.show();

            if (System.getProperty("os.name").equalsIgnoreCase("windows 11")) {
                try {
                    Win11ThemeWindowManager manager =
                            (Win11ThemeWindowManager) ThemeWindowManagerFactory.create();
                    manager.setWindowCornerPreference(mainStage, Win11ThemeWindowManager.CornerPreference.ROUND);
                    manager.setDarkModeForWindowFrame(mainStage, true);
                } catch (Exception e) {
                    System.err.println("Error setting window theme: " + e.getMessage());
                }
            } else if (System.getProperty("os.name").equalsIgnoreCase("windows 10")) {
                try {
                    Win10ThemeWindowManager manager =
                            (Win10ThemeWindowManager) ThemeWindowManagerFactory.create();
                    manager.setDarkModeForWindowFrame(mainStage, true);
                } catch (Exception e) {
                    System.err.println("Error setting window theme: " + e.getMessage());
                }
            } else {
                System.out.println("Not running on Windows 10 or 11, skipping theme manager setup");
            }

        } catch (Exception e) {
            System.err.println("Error starting main UI: " + e.getMessage());
            e.printStackTrace();
        }
    }

    public static void startServerApp() {
        launch();
    }
}