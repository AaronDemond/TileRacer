package com.tilegame;

import java.util.List;

final class TileGameMultiplayerMessage
{
    String type;
    String player;
    String host;
    String roomId;
    String levelName;
    String winner;
    String reason;
    String error;
    int totalTicks;
    int sequenceNumber;
    int tick;
    List<String> invitedPlayers;
    List<String> deliveredPlayers;
    List<String> queuedPlayers;
    List<String> players;
    TileGameMultiplayerTile tile;
    TileGameMultiplayerLevel level;
    TileGameMultiplayerState state;
}

final class TileGameMultiplayerLevel
{
    String name;
    String creator;
    boolean sequenceMode;
    boolean addDisablers;
    boolean dangerTiles;
    boolean directionalTiles;
    boolean hardMode;
    List<TileGameMultiplayerTile> tiles;
    List<TileGameMultiplayerTile> modifiers;
}

final class TileGameMultiplayerState
{
    String mode;
    boolean sequenceModeEnabled;
    boolean sequenceSharedModeEnabled;
    boolean addDisablersEnabled;
    boolean dangerTilesEnabled;
    boolean directionalTilesEnabled;
    boolean hardModeEnabled;
    int countdownTicksRemaining;
    int totalRunTicks;
    int currentSequenceNumber;
    int sequenceShrinkDelay;
    int sequenceShrinkDelaySetTick;
    List<TileGameMultiplayerTile> activeLevelTiles;
    List<TileGameMultiplayerTile> runColoredTiles;
    TileGameMultiplayerTile position;
    List<TileGameMultiplayerTile> validSequenceTiles;
    List<TileGameMultiplayerTimedTile> sequenceTileTimers;
    List<TileGameMultiplayerTimedTile> sequenceHardBonusTiles;
    List<TileGameMultiplayerTimedTile> sequenceHardBonusTimers;
    List<TileGameMultiplayerTimedTile> disabledTileTimers;
    TileGameMultiplayerTile resetTile;
    List<TileGameMultiplayerTimedTile> dangerTileCountdowns;
    List<TileGameMultiplayerTimedTile> dangerTileActiveTimers;
    List<TileGameMultiplayerDirectionalTile> directionalTiles;
    List<TileGameMultiplayerTimedTile> powerUpTiles;
    List<TileGameMultiplayerTimedTile> powerUpTimers;
}

class TileGameMultiplayerTile
{
    int x;
    int y;
    int plane;
    String modifier;
}

final class TileGameMultiplayerTimedTile extends TileGameMultiplayerTile
{
    int value;
}

final class TileGameMultiplayerDirectionalTile extends TileGameMultiplayerTile
{
    String direction;
}
