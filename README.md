# JavaFX Media Player

A media player with a video view, playlist, and keyboard controls.

## What you need installed
1. **JDK 17 or newer** (https://adoptium.net)
2. **Apache Maven** (https://maven.apache.org/download.cgi), or just use the VS Code Maven extension
3. VS Code extension: **Extension Pack for Java**

## Run it
1. VS Code -> File -> Open Folder -> select this `media-player` folder
2. Wait for the Java extension to finish importing the Maven project (bottom-right status)
3. Either:
   - open `src/main/java/com/mediaplayer/Launcher.java` and click **Run** above `main`, or
   - open a terminal in this folder and run: `mvn javafx:run`

## Add your videos
Put your files in the `media/` folder, then click **Add Files** (the dialog opens in that folder).
You can also drag and drop files onto the window.

Supported formats: MP4 (H.264 video + AAC audio), M4V, MP3, WAV, AAC, M4A, AIFF, FLV.
If a video does not play, re-export it as MP4 with H.264 + AAC.

## Controls

| Key | Action |
|---|---|
| Space | Play / pause toggle |
| S | Stop |
| N | Next item in playlist |
| P | Previous item in playlist |
| Up / Down | Volume up / down |
| M | Mute / unmute |
| Left / Right | Seek 5 seconds (extra) |
| F or double-click video | Fullscreen (extra) |
| Delete | Remove selected playlist item |

## Playlist
- **Add Files** opens a FileChooser (multi-select)
- **Remove** removes the selected item (also the Delete key)
- Clicking an item in the list plays it straight away
- When a track ends, the next one starts automatically

## Project layout
```
media-player/
  pom.xml
  media/                         <- put your videos here
  src/main/java/com/mediaplayer/
    Main.java                    <- the whole player
    Launcher.java                <- entry point for the VS Code Run button
  src/main/resources/style.css   <- dark theme
```
