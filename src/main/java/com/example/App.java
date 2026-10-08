package com.example;

import java.io.File;
import java.io.InputStream;
import java.util.List;

import javafx.application.Application;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.control.Slider;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.media.Media;
import javafx.scene.media.MediaPlayer;
import javafx.scene.media.MediaView;
import javafx.scene.shape.Rectangle;
import javafx.stage.FileChooser;
import javafx.stage.Stage;
import javafx.util.Duration;

public class App extends Application {

    // --- Private Fields ---
    private MediaPlayer mediaPlayer;
    private MediaView mediaView;

    private final ObservableList<File> playlist = FXCollections.observableArrayList();
    private final ListView<File> playlistView = new ListView<>(playlist);
    private int currentTrackIndex = -1;

    private Slider volumeSlider;
    private Slider timeSlider;
    private Label currentTimeLabel;
    private Label totalTimeLabel;
    private boolean isUserSeeking = false;
    private double previousVolume = 0.5;

    private static final int BARS_COUNT = 32;
    private final Rectangle[] visualizerBars = new Rectangle[BARS_COUNT];
    private HBox visualizerContainer;

    @Override
    public void start(Stage stage) {
        BorderPane root = new BorderPane();
        root.getStyleClass().add("root-pane");

        root.setCenter(createCenterPane());
        root.setBottom(createControlBar(stage));
        root.setRight(createPlaylistSidebar(stage));

        Scene scene = new Scene(root, 950, 600);
        
        String cssPath = getClass().getResource("/style.css") != null 
                ? getClass().getResource("/style.css").toExternalForm()
                : new File("style.css").toURI().toString();
        scene.getStylesheets().add(cssPath);

        setupKeyboardShortcuts(scene);

        stage.setTitle("Media Player");
        stage.setScene(scene);
        stage.show();
    }

    public static void main(String[] args) {
        launch(args);
    }

    // --- UI Layout Methods ---

    private StackPane createCenterPane() {
        mediaView = new MediaView();
        mediaView.setPreserveRatio(true);

        visualizerContainer = new HBox(4);
        visualizerContainer.setAlignment(Pos.CENTER);
        visualizerContainer.getStyleClass().add("visualizer-pane");

        for (int i = 0; i < BARS_COUNT; i++) {
            Rectangle bar = new Rectangle(8, 2);
            bar.getStyleClass().add("visualizer-bar");
            visualizerBars[i] = bar;
            visualizerContainer.getChildren().add(bar);
        }

        StackPane centerPane = new StackPane(mediaView, visualizerContainer);
        centerPane.setAlignment(Pos.CENTER);
        centerPane.getStyleClass().add("media-container");

        centerPane.widthProperty().addListener((obs, oldVal, newVal) -> 
            mediaView.setFitWidth(newVal.doubleValue() - 20)
        );
        centerPane.heightProperty().addListener((obs, oldVal, newVal) -> 
            mediaView.setFitHeight(newVal.doubleValue() - 20)
        );

        return centerPane;
    }

    private VBox createControlBar(Stage stage) {
        // Playback Buttons 
        Button prevBtn = createIconButton("/image/previous.png");
        Button playBtn = createIconButton("/image/play.png");
        Button stopBtn = createIconButton("/image/stop.png");
        Button nextBtn = createIconButton("/image/next.png");

        prevBtn.setOnAction(e -> playPreviousTrack());
        playBtn.setOnAction(e -> togglePlayPause());
        stopBtn.setOnAction(e -> stopMedia());
        nextBtn.setOnAction(e -> playNextTrack());

        // Time / Seek Slider & Labels (Centered above icons)
        currentTimeLabel = new Label("00:00");
        totalTimeLabel = new Label("00:00");
        currentTimeLabel.getStyleClass().add("time-label");
        totalTimeLabel.getStyleClass().add("time-label");

        timeSlider = new Slider(0, 100, 0);
        timeSlider.setFocusTraversable(false);
        HBox.setHgrow(timeSlider, Priority.ALWAYS);

        timeSlider.valueProperty().addListener((obs, oldVal, newVal) -> {
            if (timeSlider.isValueChanging() && mediaPlayer != null) {
                Duration seekTarget = mediaPlayer.getTotalDuration().multiply(newVal.doubleValue() / 100.0);
                mediaPlayer.seek(seekTarget);
            }
        });

        timeSlider.setOnMousePressed(e -> isUserSeeking = true);
        timeSlider.setOnMouseReleased(e -> {
            isUserSeeking = false;
            if (mediaPlayer != null) {
                Duration seekTarget = mediaPlayer.getTotalDuration().multiply(timeSlider.getValue() / 100.0);
                mediaPlayer.seek(seekTarget);
            }
        });

        HBox timeRow = new HBox(10, currentTimeLabel, timeSlider, totalTimeLabel);
        timeRow.setAlignment(Pos.CENTER);
        timeRow.setMaxWidth(Double.MAX_VALUE);

        // Volume Controls
        Label volumeLabel = new Label("Vol:");
        volumeLabel.getStyleClass().add("volume-label");

        volumeSlider = new Slider(0, 1, 0.5);
        volumeSlider.setPrefWidth(90);
        volumeSlider.setFocusTraversable(false);
        volumeSlider.valueProperty().addListener((obs, oldVal, newVal) -> {
            if (mediaPlayer != null) mediaPlayer.setVolume(newVal.doubleValue());
        });

        HBox buttonsAndVolume = new HBox(15, prevBtn, playBtn, stopBtn, nextBtn, volumeLabel, volumeSlider);
        buttonsAndVolume.setAlignment(Pos.CENTER);

        VBox controlBar = new VBox(10, timeRow, buttonsAndVolume);
        controlBar.setAlignment(Pos.CENTER);
        controlBar.setPadding(new Insets(12, 20, 12, 20));
        controlBar.getStyleClass().add("control-bar");

        return controlBar;
    }

    private VBox createPlaylistSidebar(Stage stage) {
        Label sidebarHeader = new Label("PLAYLIST");
        sidebarHeader.getStyleClass().add("sidebar-header");

        playlistView.setCellFactory(param -> new ListCell<File>() {
            @Override
            protected void updateItem(File item, boolean empty) {
                super.updateItem(item, empty);
                setText(empty || item == null ? null : (getIndex() + 1) + ".  " + item.getName());
            }
        });

        playlistView.getSelectionModel().selectedIndexProperty().addListener((obs, oldIdx, newIdx) -> {
            if (newIdx != null && newIdx.intValue() >= 0 && newIdx.intValue() < playlist.size()) {
                if (newIdx.intValue() != currentTrackIndex) playMediaAtIndex(newIdx.intValue());
            }
        });

        Button addButton = new Button("+ Add Media");
        addButton.setMaxWidth(Double.MAX_VALUE);
        addButton.getStyleClass().add("btn-primary");

        Button removeButton = new Button("- Remove");
        removeButton.setMaxWidth(Double.MAX_VALUE);
        removeButton.getStyleClass().add("btn-danger");

        FileChooser fileChooser = new FileChooser();
        fileChooser.getExtensionFilters().add(
            new FileChooser.ExtensionFilter("Media Files", "*.mp3", "*.mp4", "*.m4a", "*.wav", "*.aac")
        );

        addButton.setOnAction(e -> {
            List<File> selectedFiles = fileChooser.showOpenMultipleDialog(stage);
            if (selectedFiles != null && !selectedFiles.isEmpty()) {
                playlist.addAll(selectedFiles);
                if (currentTrackIndex == -1) playMediaAtIndex(0);
            }
        });

        removeButton.setOnAction(e -> {
            int selectedIdx = playlistView.getSelectionModel().getSelectedIndex();
            if (selectedIdx >= 0) {
                playlist.remove(selectedIdx);
                if (playlist.isEmpty()) {
                    stopAndDisposePlayer();
                    currentTrackIndex = -1;
                } else if (selectedIdx == currentTrackIndex) {
                    playMediaAtIndex(Math.min(selectedIdx, playlist.size() - 1));
                }
            }
        });

        HBox playlistActions = new HBox(8, addButton, removeButton);
        HBox.setHgrow(addButton, Priority.ALWAYS);
        HBox.setHgrow(removeButton, Priority.ALWAYS);

        VBox sidebar = new VBox(10, sidebarHeader, playlistView, playlistActions);
        VBox.setVgrow(playlistView, Priority.ALWAYS);
        sidebar.setPadding(new Insets(12));
        sidebar.setPrefWidth(240);
        sidebar.getStyleClass().add("sidebar");

        return sidebar;
    }

    // --- Media Playback & Visualizer Logic ---

    private void playMediaAtIndex(int index) {
        if (index < 0 || index >= playlist.size()) return;

        stopAndDisposePlayer();
        currentTrackIndex = index;
        playlistView.getSelectionModel().select(currentTrackIndex);

        File file = playlist.get(currentTrackIndex);
        mediaPlayer = new MediaPlayer(new Media(file.toURI().toString()));
        mediaView.setMediaPlayer(mediaPlayer);
        mediaPlayer.setVolume(volumeSlider.getValue());

        mediaPlayer.setOnReady(() -> {
            totalTimeLabel.setText(formatDuration(mediaPlayer.getTotalDuration()));
        });

        mediaPlayer.currentTimeProperty().addListener((obs, oldTime, newTime) -> {
            if (!isUserSeeking && mediaPlayer.getTotalDuration() != null && !mediaPlayer.getTotalDuration().isUnknown()) {
                double progress = (newTime.toMillis() / mediaPlayer.getTotalDuration().toMillis()) * 100.0;
                timeSlider.setValue(progress);
                currentTimeLabel.setText(formatDuration(newTime));
            }
        });

        boolean isAudioOnly = !file.getName().toLowerCase().endsWith(".mp4");
        visualizerContainer.setVisible(isAudioOnly);

        if (isAudioOnly) {
            mediaPlayer.setAudioSpectrumNumBands(BARS_COUNT);
            mediaPlayer.setAudioSpectrumInterval(0.03);
            mediaPlayer.setAudioSpectrumThreshold(-60);

            mediaPlayer.setAudioSpectrumListener((timestamp, duration, magnitudes, phases) -> {
                Platform.runLater(() -> {
                    for (int i = 0; i < BARS_COUNT && i < magnitudes.length; i++) {
                        double mag = magnitudes[i] - mediaPlayer.getAudioSpectrumThreshold();
                        visualizerBars[i].setHeight(Math.max(2, mag * 3.0));
                    }
                });
            });
        }

        mediaPlayer.play();
        mediaPlayer.setOnEndOfMedia(this::playNextTrack);
    }

    private String formatDuration(Duration duration) {
        if (duration == null || duration.isUnknown() || duration.isIndefinite()) return "00:00";
        int millis = (int) duration.toMillis();
        int seconds = (millis / 1000) % 60;
        int minutes = (millis / (1000 * 60)) % 60;
        int hours = millis / (1000 * 60 * 60);
        if (hours > 0) {
            return String.format("%d:%02d:%02d", hours, minutes, seconds);
        } else {
            return String.format("%02d:%02d", minutes, seconds);
        }
    }

    private void togglePlayPause() {
        if (mediaPlayer == null) return;
        if (mediaPlayer.getStatus() == MediaPlayer.Status.PLAYING) mediaPlayer.pause();
        else mediaPlayer.play();
    }

    private void stopMedia() {
        if (mediaPlayer != null) {
            mediaPlayer.stop();
            resetVisualizerBars();
            timeSlider.setValue(0);
            currentTimeLabel.setText("00:00");
        }
    }

    private void playNextTrack() {
        if (!playlist.isEmpty()) playMediaAtIndex((currentTrackIndex + 1) % playlist.size());
    }

    private void playPreviousTrack() {
        if (!playlist.isEmpty()) playMediaAtIndex((currentTrackIndex - 1 + playlist.size()) % playlist.size());
    }

    private void toggleMute() {
        if (volumeSlider.getValue() > 0) {
            previousVolume = volumeSlider.getValue();
            volumeSlider.setValue(0);
            if (mediaPlayer != null) mediaPlayer.setVolume(0);
        } else {
            double restoredVol = previousVolume > 0 ? previousVolume : 0.5;
            volumeSlider.setValue(restoredVol);
            if (mediaPlayer != null) mediaPlayer.setVolume(restoredVol);
        }
    }

    // --- Cleanup & Reset Methods ---
    private void stopAndDisposePlayer() {
        if (mediaPlayer != null) {
            mediaPlayer.stop();
            mediaPlayer.dispose();
            mediaPlayer = null;
        }
        resetVisualizerBars();
        timeSlider.setValue(0);
        currentTimeLabel.setText("00:00");
        totalTimeLabel.setText("00:00");
    }

    private void resetVisualizerBars() {
        for (Rectangle bar : visualizerBars) bar.setHeight(2);
    }

    // --- Helpers & Shortcuts ---

    private Button createIconButton(String resourcePath) {
        Button button = new Button();
        button.setFocusTraversable(false);
        button.getStyleClass().add("icon-button");

        InputStream imageStream = getClass().getResourceAsStream(resourcePath);
        if (imageStream != null) {
            ImageView iconView = new ImageView(new Image(imageStream));
            iconView.setFitWidth(22);
            iconView.setFitHeight(22);
            button.setGraphic(iconView);
        }
        return button;
    }

    private void setupKeyboardShortcuts(Scene scene) {
        scene.addEventFilter(KeyEvent.KEY_PRESSED, event -> {
            if (event.getCode() == KeyCode.SPACE) { togglePlayPause(); event.consume(); }
            else if (event.getCode() == KeyCode.S) { stopMedia(); event.consume(); }
            else if (event.getCode() == KeyCode.N) { playNextTrack(); event.consume(); }
            else if (event.getCode() == KeyCode.P) { playPreviousTrack(); event.consume(); }
            else if (event.getCode() == KeyCode.UP) {
                volumeSlider.setValue(Math.min(1.0, volumeSlider.getValue() + 0.05)); event.consume();
            }
            else if (event.getCode() == KeyCode.DOWN) {
                volumeSlider.setValue(Math.max(0.0, volumeSlider.getValue() - 0.05)); event.consume();
            }
            else if (event.getCode() == KeyCode.M) { toggleMute(); event.consume(); }
        });
    }
}