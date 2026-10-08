package com.mediaplayer;

import javafx.application.Application;
import javafx.application.Platform;
import javafx.beans.property.DoubleProperty;
import javafx.beans.property.SimpleDoubleProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.control.Slider;
import javafx.scene.input.DragEvent;
import javafx.scene.input.KeyEvent;
import javafx.scene.input.MouseButton;
import javafx.scene.input.TransferMode;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.media.Media;
import javafx.scene.media.MediaPlayer;
import javafx.scene.media.MediaView;
import javafx.stage.FileChooser;
import javafx.stage.Stage;
import javafx.util.Duration;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * BIIM3210 - Assignment 2: Media Player in JavaFX
 *
 * Part A: MediaView + Play / Pause / Stop buttons
 * Part B: Keyboard controls (Space, S, N, P, Up, Down, M)
 * Part C: ListView playlist with FileChooser add / remove, auto-update on selection
 */
public class Main extends Application {

    /** File types JavaFX Media can decode on all platforms. */
    private static final List<String> FILE_PATTERNS = List.of(
            "*.mp4", "*.m4v", "*.mp3", "*.wav", "*.aac", "*.m4a", "*.aif", "*.aiff", "*.flv");
    private static final Set<String> EXTENSIONS = Set.of(
            "mp4", "m4v", "mp3", "wav", "aac", "m4a", "aif", "aiff", "flv");

    private static final double VOLUME_STEP = 0.05;
    private static final double SEEK_STEP_SECONDS = 5;

    // ---- model / state ----
    private final ObservableList<File> playlist = FXCollections.observableArrayList();
    private final DoubleProperty volume = new SimpleDoubleProperty(0.7);
    private MediaPlayer player;
    private int currentIndex = -1;
    private boolean muted = false;
    private boolean suppressSelection = false;

    // ---- UI ----
    private Stage stage;
    private final ListView<File> listView = new ListView<>(playlist);
    private final MediaView mediaView = new MediaView();
    private final Label placeholder = new Label();
    private final Label nowPlayingLabel = new Label("Nothing loaded");
    private final Label statusLabel = new Label("Stopped");
    private final Label timeLabel = new Label("0:00");
    private final Label durationLabel = new Label("0:00");
    private final Label volumeLabel = new Label();
    private final Slider seekSlider = new Slider(0, 1, 0);
    private final Slider volumeSlider = new Slider(0, 1, 0.7);
    private final Button muteButton = new Button("Mute");

    // =====================================================================
    //  Start-up
    // =====================================================================

    @Override
    public void start(Stage primaryStage) {
        this.stage = primaryStage;

        BorderPane root = new BorderPane();
        root.setTop(buildTopBar());
        root.setCenter(buildVideoArea());
        root.setRight(buildPlaylistPanel());
        root.setBottom(buildControls());

        Scene scene = new Scene(root, 1100, 680);
        scene.getStylesheets().add(getClass().getResource("/style.css").toExternalForm());

        installKeyboardControls(scene);
        installDragAndDrop(scene);

        primaryStage.setTitle("Media Player");
        primaryStage.setMinWidth(820);
        primaryStage.setMinHeight(520);
        primaryStage.setScene(scene);
        primaryStage.show();

        showPlaceholder("Add a video or audio file to begin\n(Add Files button, or drag and drop)");
        updateVolumeLabel();
    }

    @Override
    public void stop() {
        disposePlayer();
    }

    // =====================================================================
    //  UI construction
    // =====================================================================

    private Region buildTopBar() {
        Label title = new Label("Media Player");
        title.getStyleClass().add("app-title");

        nowPlayingLabel.getStyleClass().add("now-playing-label");
        nowPlayingLabel.setMaxWidth(Double.MAX_VALUE);
        HBox.setHgrow(nowPlayingLabel, Priority.ALWAYS);

        statusLabel.getStyleClass().add("status-pill");

        HBox bar = new HBox(16, title, nowPlayingLabel, statusLabel);
        bar.setAlignment(Pos.CENTER_LEFT);
        bar.getStyleClass().add("top-bar");
        return bar;
    }

    private Region buildVideoArea() {
        // The MediaView always fills the pane while keeping the video's aspect ratio.
        mediaView.setPreserveRatio(true);
        mediaView.setVisible(false);

        placeholder.getStyleClass().add("placeholder-label");
        placeholder.setWrapText(true);

        StackPane pane = new StackPane(placeholder, mediaView);
        pane.getStyleClass().add("video-pane");
        pane.setMinSize(0, 0);
        pane.setPrefSize(800, 450);
        mediaView.fitWidthProperty().bind(pane.widthProperty());
        mediaView.fitHeightProperty().bind(pane.heightProperty());

        // Double-click the video to toggle fullscreen.
        pane.setOnMouseClicked(e -> {
            if (e.getButton() == MouseButton.PRIMARY && e.getClickCount() == 2) {
                toggleFullscreen();
            }
        });
        return pane;
    }

    private Region buildPlaylistPanel() {
        Label header = new Label("PLAYLIST");
        header.getStyleClass().add("section-title");

        listView.setPlaceholder(new Label("Playlist is empty.\nClick \"Add Files\" or drop files here."));
        listView.setFocusTraversable(false);
        VBox.setVgrow(listView, Priority.ALWAYS);

        // Show "1. filename" and highlight the item that is currently loaded.
        listView.setCellFactory(lv -> new ListCell<>() {
            @Override
            protected void updateItem(File file, boolean empty) {
                super.updateItem(file, empty);
                getStyleClass().remove("now-playing");
                if (empty || file == null) {
                    setText(null);
                    return;
                }
                setText((getIndex() + 1) + ".  " + file.getName());
                if (getIndex() == currentIndex) {
                    getStyleClass().add("now-playing");
                }
            }
        });

        // Part C: whenever a new item is selected, the player updates automatically.
        listView.getSelectionModel().selectedIndexProperty().addListener((obs, oldV, newV) -> {
            if (suppressSelection) {
                return;
            }
            int i = newV.intValue();
            if (i >= 0 && i != currentIndex) {
                playIndex(i);
            }
        });

        Button addButton = new Button("Add Files");
        addButton.getStyleClass().add("primary");
        addButton.setOnAction(e -> addFilesWithChooser());
        addButton.setFocusTraversable(false);

        Button removeButton = new Button("Remove");
        removeButton.getStyleClass().add("danger");
        removeButton.setOnAction(e -> removeSelected());
        removeButton.setFocusTraversable(false);

        HBox buttons = new HBox(8, addButton, removeButton);

        VBox panel = new VBox(10, header, listView, buttons);
        panel.getStyleClass().add("side-panel");
        panel.setPrefWidth(300);
        panel.setMinWidth(240);
        return panel;
    }

    private Region buildControls() {
        // --- seek row ---
        timeLabel.getStyleClass().add("time-label");
        durationLabel.getStyleClass().add("time-label");
        seekSlider.setFocusTraversable(false);
        HBox.setHgrow(seekSlider, Priority.ALWAYS);
        // Jump to the chosen position when the user releases the mouse.
        seekSlider.setOnMouseReleased(e -> {
            if (player != null) {
                player.seek(Duration.seconds(seekSlider.getValue()));
            }
        });
        HBox seekRow = new HBox(10, timeLabel, seekSlider, durationLabel);
        seekRow.setAlignment(Pos.CENTER);

        // --- transport buttons (Part A) ---
        Button prevButton = makeButton("Prev", this::previous);
        Button playButton = makeButton("Play", this::play);
        playButton.getStyleClass().add("primary");
        Button pauseButton = makeButton("Pause", this::pause);
        Button stopButton = makeButton("Stop", this::stopPlayback);
        Button nextButton = makeButton("Next", this::next);

        // --- volume ---
        muteButton.setOnAction(e -> toggleMute());
        muteButton.setFocusTraversable(false);
        volumeSlider.valueProperty().bindBidirectional(volume);
        volumeSlider.setPrefWidth(140);
        volumeSlider.setFocusTraversable(false);
        volumeLabel.getStyleClass().add("time-label");
        volumeLabel.setMinWidth(40);
        volume.addListener((obs, o, n) -> updateVolumeLabel());

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        HBox buttonRow = new HBox(8, prevButton, playButton, pauseButton, stopButton, nextButton,
                spacer, muteButton, volumeSlider, volumeLabel);
        buttonRow.setAlignment(Pos.CENTER_LEFT);

        Label hint = new Label("Space: play/pause   S: stop   N: next   P: previous   "
                + "Up/Down: volume   M: mute   Left/Right: seek 5s   F: fullscreen   Delete: remove item");
        hint.getStyleClass().add("hint-label");

        VBox controls = new VBox(10, seekRow, buttonRow, hint);
        controls.getStyleClass().add("controls");
        controls.setPadding(new Insets(12, 18, 10, 18));
        return controls;
    }

    private Button makeButton(String text, Runnable action) {
        Button b = new Button(text);
        b.setOnAction(e -> action.run());
        b.setFocusTraversable(false);
        return b;
    }

    // =====================================================================
    //  Part B - keyboard controls
    // =====================================================================

    private void installKeyboardControls(Scene scene) {
        // A filter runs before any focused control sees the key, so Space never
        // "clicks" a button and the arrow keys never move the list selection.
        scene.addEventFilter(KeyEvent.KEY_PRESSED, e -> {
            switch (e.getCode()) {
                case SPACE -> togglePlayPause();
                case S -> stopPlayback();
                case N -> next();
                case P -> previous();
                case UP -> changeVolume(VOLUME_STEP);
                case DOWN -> changeVolume(-VOLUME_STEP);
                case M -> toggleMute();
                case RIGHT -> seekBy(SEEK_STEP_SECONDS);
                case LEFT -> seekBy(-SEEK_STEP_SECONDS);
                case F -> toggleFullscreen();
                case DELETE -> removeSelected();
                default -> {
                    return;
                }
            }
            e.consume();
        });
    }

    // =====================================================================
    //  Playback
    // =====================================================================

    /** Loads and starts the playlist item at the given index. */
    private void playIndex(int index) {
        if (index < 0 || index >= playlist.size()) {
            return;
        }
        disposePlayer();
        currentIndex = index;
        selectInList(index);
        listView.refresh();

        File file = playlist.get(index);
        nowPlayingLabel.setText(file.getName());
        showPlaceholder("Loading " + file.getName() + " ...");

        boolean[] errorReported = {false};
        try {
            Media media = new Media(file.toURI().toString());
            MediaPlayer mp = new MediaPlayer(media);
            player = mp;

            mp.volumeProperty().bind(volume);
            mp.setMute(muted);
            mediaView.setMediaPlayer(mp);

            mp.setOnReady(() -> {
                Duration total = mp.getTotalDuration();
                if (total != null && !total.isUnknown() && !total.isIndefinite()) {
                    seekSlider.setMax(Math.max(1, total.toSeconds()));
                    durationLabel.setText(formatTime(total));
                }
                boolean hasVideo = media.getWidth() > 0 && media.getHeight() > 0;
                if (hasVideo) {
                    mediaView.setVisible(true);
                    placeholder.setVisible(false);
                } else {
                    // Audio-only file: no picture, so show its name instead.
                    showPlaceholder("Now playing audio\n" + file.getName());
                }
            });

            mp.currentTimeProperty().addListener((obs, oldT, newT) -> {
                timeLabel.setText(formatTime(newT));
                if (!seekSlider.isPressed()) {
                    seekSlider.setValue(newT.toSeconds());
                }
            });

            mp.setOnPlaying(() -> statusLabel.setText("Playing"));
            mp.setOnPaused(() -> statusLabel.setText("Paused"));
            mp.setOnStopped(() -> statusLabel.setText("Stopped"));

            // Move on to the next playlist item when this one finishes.
            mp.setOnEndOfMedia(() -> {
                if (currentIndex < playlist.size() - 1) {
                    next();
                } else {
                    stopPlayback();
                }
            });

            mp.setOnError(() -> reportError(file, mp.getError(), errorReported));
            media.setOnError(() -> reportError(file, media.getError(), errorReported));

            mp.play();
        } catch (Exception ex) {
            reportError(file, ex, errorReported);
        }
    }

    private void play() {
        if (player == null) {
            if (!playlist.isEmpty()) {
                int selected = listView.getSelectionModel().getSelectedIndex();
                playIndex(Math.max(0, selected));
            }
            return;
        }
        player.play();
    }

    private void pause() {
        if (player != null) {
            player.pause();
        }
    }

    private void togglePlayPause() {
        if (player != null && player.getStatus() == MediaPlayer.Status.PLAYING) {
            pause();
        } else {
            play();
        }
    }

    private void stopPlayback() {
        if (player != null) {
            player.stop();
        }
    }

    private void next() {
        if (playlist.isEmpty()) {
            return;
        }
        int i = currentIndex < 0 ? 0 : (currentIndex + 1) % playlist.size();
        playIndex(i);
    }

    private void previous() {
        if (playlist.isEmpty()) {
            return;
        }
        int i = currentIndex < 0 ? 0 : (currentIndex - 1 + playlist.size()) % playlist.size();
        playIndex(i);
    }

    private void changeVolume(double delta) {
        double v = Math.max(0, Math.min(1, volume.get() + delta));
        volume.set(v);
        if (delta > 0 && muted) {
            toggleMute();
        }
    }

    private void toggleMute() {
        muted = !muted;
        if (player != null) {
            player.setMute(muted);
        }
        muteButton.setText(muted ? "Unmute" : "Mute");
        updateVolumeLabel();
    }

    private void seekBy(double seconds) {
        if (player == null) {
            return;
        }
        Duration total = player.getTotalDuration();
        double target = player.getCurrentTime().toSeconds() + seconds;
        double max = (total == null || total.isUnknown()) ? target : total.toSeconds();
        player.seek(Duration.seconds(Math.max(0, Math.min(max, target))));
    }

    private void toggleFullscreen() {
        stage.setFullScreen(!stage.isFullScreen());
    }

    /** Releases the current MediaPlayer and resets the time display. */
    private void disposePlayer() {
        if (player != null) {
            player.volumeProperty().unbind();
            player.dispose();
            player = null;
        }
        mediaView.setMediaPlayer(null);
        mediaView.setVisible(false);
        seekSlider.setValue(0);
        seekSlider.setMax(1);
        timeLabel.setText("0:00");
        durationLabel.setText("0:00");
        statusLabel.setText("Stopped");
    }

    // =====================================================================
    //  Part C - playlist management
    // =====================================================================

    private void addFilesWithChooser() {
        FileChooser chooser = new FileChooser();
        chooser.setTitle("Add media files");
        chooser.getExtensionFilters().addAll(
                new FileChooser.ExtensionFilter("Audio and video", FILE_PATTERNS),
                new FileChooser.ExtensionFilter("All files", "*.*"));

        // Start in the project's media/ folder when it exists.
        File mediaDir = new File("media");
        if (mediaDir.isDirectory()) {
            chooser.setInitialDirectory(mediaDir);
        }

        List<File> chosen = chooser.showOpenMultipleDialog(stage);
        addFiles(chosen);
    }

    private void addFiles(List<File> files) {
        if (files == null || files.isEmpty()) {
            return;
        }
        int firstNew = playlist.size();
        List<File> toAdd = new ArrayList<>();
        for (File f : files) {
            if (f != null && f.isFile() && !playlist.contains(f) && !toAdd.contains(f)) {
                toAdd.add(f);
            }
        }
        if (toAdd.isEmpty()) {
            return;
        }
        boolean nothingLoaded = (currentIndex < 0);

        suppressSelection = true;
        playlist.addAll(toAdd);
        suppressSelection = false;

        // If the player was idle, start with the first file that was just added.
        if (nothingLoaded) {
            playIndex(firstNew);
        } else {
            selectInList(currentIndex);
        }
    }

    private void removeSelected() {
        int selected = listView.getSelectionModel().getSelectedIndex();
        if (selected < 0) {
            return;
        }
        boolean wasCurrent = (selected == currentIndex);

        suppressSelection = true;
        playlist.remove(selected);
        suppressSelection = false;

        if (wasCurrent) {
            disposePlayer();
            currentIndex = -1;
            if (playlist.isEmpty()) {
                nowPlayingLabel.setText("Nothing loaded");
                showPlaceholder("Add a video or audio file to begin\n(Add Files button, or drag and drop)");
                listView.refresh();
            } else {
                // Continue with whatever slid into the removed item's place.
                playIndex(Math.min(selected, playlist.size() - 1));
            }
        } else {
            if (selected < currentIndex) {
                currentIndex--;
            }
            selectInList(currentIndex);
            listView.refresh();
        }
    }

    /** Selects an item in the list without triggering the "play on select" listener. */
    private void selectInList(int index) {
        suppressSelection = true;
        if (index >= 0 && index < playlist.size()) {
            listView.getSelectionModel().select(index);
            listView.scrollTo(index);
        } else {
            listView.getSelectionModel().clearSelection();
        }
        suppressSelection = false;
    }

    private void installDragAndDrop(Scene scene) {
        scene.setOnDragOver((DragEvent e) -> {
            if (e.getDragboard().hasFiles()) {
                e.acceptTransferModes(TransferMode.COPY);
            }
            e.consume();
        });
        scene.setOnDragDropped((DragEvent e) -> {
            List<File> dropped = new ArrayList<>();
            if (e.getDragboard().hasFiles()) {
                for (File f : e.getDragboard().getFiles()) {
                    if (hasSupportedExtension(f)) {
                        dropped.add(f);
                    }
                }
            }
            addFiles(dropped);
            e.setDropCompleted(!dropped.isEmpty());
            e.consume();
        });
    }

    // =====================================================================
    //  Helpers
    // =====================================================================

    private void showPlaceholder(String text) {
        placeholder.setText(text);
        placeholder.setVisible(true);
        mediaView.setVisible(false);
    }

    private void updateVolumeLabel() {
        volumeLabel.setText(muted ? "Muted" : Math.round(volume.get() * 100) + "%");
    }

    private void reportError(File file, Exception error, boolean[] alreadyReported) {
        if (alreadyReported[0]) {
            return;
        }
        alreadyReported[0] = true;
        String detail = (error == null || error.getMessage() == null) ? "Unknown error" : error.getMessage();
        Platform.runLater(() -> {
            statusLabel.setText("Error");
            showPlaceholder("Could not play\n" + file.getName());
            Alert alert = new Alert(Alert.AlertType.ERROR);
            alert.setTitle("Playback error");
            alert.setHeaderText("Could not play " + file.getName());
            alert.setContentText(detail + "\n\nTip: use MP4 (H.264 video + AAC audio), MP3 or WAV files.");
            alert.initOwner(stage);
            alert.show();
        });
    }

    private static boolean hasSupportedExtension(File f) {
        String name = f.getName().toLowerCase(Locale.ROOT);
        int dot = name.lastIndexOf('.');
        return dot >= 0 && EXTENSIONS.contains(name.substring(dot + 1));
    }

    private static String formatTime(Duration d) {
        if (d == null || d.isUnknown() || d.isIndefinite()) {
            return "0:00";
        }
        int total = (int) Math.floor(d.toSeconds());
        int h = total / 3600;
        int m = (total % 3600) / 60;
        int s = total % 60;
        return h > 0 ? String.format("%d:%02d:%02d", h, m, s) : String.format("%d:%02d", m, s);
    }

    public static void main(String[] args) {
        launch(args);
    }
}
