Overview of com.tileracer classes

TileGameConfig.java
- Lightweight Runelite Config interface for the plugin. Holds @ConfigGroup("tilegame") so plugin configuration can be stored/retrieved by the framework.

TileGameOverlay.java
- Draws painted/selected/viewed/run tiles onto the game scene. Builds masks for NPCs to avoid overpainting hulls and renders tiles with provided colors. Responsible for visual overlay layering and rendering logic.

TileGameMode.java
- Simple enum representing plugin state: IDLE, CHOOSE, EDIT, COUNTDOWN, LEVEL, DONE.

TileGameResult.java
- Small value object holding a run duration in ticks and formatting helper (formattedSeconds).

TileGamePanel.java
- Swing UI for the plugin side panel. Presents current game state, level list, highscore dialog, and action controls (play, edit, view, import, export, paint). Updates view based on TileGamePlugin model and forwards UI actions back to the plugin. Contains UI helpers and a CollapsibleCard inner class.

TileGamePlugin.java
- Core plugin logic and state machine. Manages groups (saved levels), highscores, selected/viewed/painted/run tiles, and run lifecycle (countdown, level ticks, completion). Handles persistence (save/load JSON), clipboard import/export, mouse interactions for selecting tiles, paint-mode logic, overlay registration, navigation button and panel lifecycle (startUp/shutDown), and utility DTO classes used for serialization (TileGameSaveData, TileGameLevelExport, TileGameTileDto, TileGameResultDto).

Notes
- Most business logic and state lives in TileGamePlugin; TileGamePanel and TileGameOverlay are the presentation layers (UI & rendering). DTO/inner classes in TileGamePlugin are used only for persistence/clipboard export and are private to the plugin implementation.
