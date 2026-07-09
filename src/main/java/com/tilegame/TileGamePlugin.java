package com.tilegame;

import com.google.gson.Gson;
import com.google.gson.JsonParseException;
import com.google.inject.Provides;
import java.awt.BasicStroke;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.Graphics2D;
import java.awt.GridLayout;
import java.awt.Polygon;
import java.awt.RenderingHints;
import java.awt.Toolkit;
import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.StringSelection;
import java.awt.datatransfer.UnsupportedFlavorException;
import java.awt.event.MouseEvent;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.net.URI;
import java.util.ArrayList;
import java.util.ArrayDeque;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import javax.inject.Inject;
import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.SourceDataLine;
import javax.swing.BorderFactory;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.SwingUtilities;
import net.runelite.api.ChatMessageType;
import net.runelite.api.Client;
import net.runelite.api.CollisionData;
import net.runelite.api.CollisionDataFlag;
import net.runelite.api.Friend;
import net.runelite.api.FriendContainer;
import net.runelite.api.Perspective;
import net.runelite.api.Player;
import net.runelite.api.Scene;
import net.runelite.api.Tile;
import net.runelite.api.WorldView;
import net.runelite.api.coords.LocalPoint;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.events.GameTick;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.input.MouseAdapter;
import net.runelite.client.input.MouseManager;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.ui.ClientToolbar;
import net.runelite.client.ui.NavigationButton;
import net.runelite.client.ui.overlay.OverlayManager;
import net.runelite.client.util.Text;

@PluginDescriptor(
        name = "Tile Game",
        description = "Paint true tiles, build tile groups, and race tile-completion levels.",
        tags = {"tile", "true tile", "movement", "practice", "game"}
)
public class TileGamePlugin extends Plugin
{
    static final String TILE_OVERLAY_NAME = "tile-game-overlay";

    private static final String CONFIG_GROUP = "tilegame";
    private static final String CONFIG_SAVE_KEY = "savedData";
    private static final int COUNTDOWN_START_TICKS = 3;
    private static final int OVERHEAD_MESSAGE_CYCLES = 300;
    private static final int SEQUENCE_HARD_BONUS_TICKS = 3;
    private static final double SEQUENCE_HARD_BONUS_SPAWN_CHANCE = 0.10;
    private static final double DANGER_TILE_SPAWN_CHANCE = 0.25;
    private static final int DANGER_TILE_SPAWN_RADIUS = 10;
    private static final int DANGER_TILE_BATCH_SIZE = 3;
    private static final int DANGER_TILE_MAX_ACTIVE = 9;
    private static final int DANGER_TILE_MIN_SAFE_TILES = 4;
    private static final double DIRECTIONAL_TILE_SPAWN_CHANCE = 0.10;
    private static final int DIRECTIONAL_TILE_MAX_ACTIVE = 2;
    private static final int DANGER_TILE_COUNTDOWN_TICKS = 2;
    private static final int DANGER_TILE_ACTIVE_TICKS = 5;
    private static final URI MULTIPLAYER_SERVER_URI = URI.create("ws://159.203.9.18:8765");

    @Inject
    private Client client;

    @Inject
    private ConfigManager configManager;

    @Inject
    private TileGameConfig config;

    @Inject
    private ClientThread clientThread;

    @Inject
    private OverlayManager overlayManager;

    @Inject
    private MouseManager mouseManager;

    @Inject
    private ClientToolbar clientToolbar;

    @Inject
    private TileGameOverlay tileGameOverlay;

    @Inject
    private Gson gson;

    private TileGameMultiplayerClient multiplayerClient;
    private final Map<String, Set<WorldPoint>> groups = new HashMap<>();
    private final Map<String, ArrayList<TileGameResult>> highscores = new HashMap<>();

    private final Set<WorldPoint> selectedTiles = new HashSet<>();
    private final Set<String> viewedGroups = new HashSet<>();

    private final Map<WorldPoint, Color> runColoredTiles = new HashMap<>();
    private final Map<WorldPoint, Color> paintedTiles = new HashMap<>();
    private final Set<WorldPoint> activeLevelGroupTiles = new HashSet<>();
    private final Set<WorldPoint> visitedThisRun = new HashSet<>();

    private final Map<String, Map<WorldPoint, TileModifier>> tileModifiers = new HashMap<>();
    private final Map<WorldPoint, Integer> disabledTileTimers = new HashMap<>();
    private WorldPoint resetDisabledTile = null;
    private WorldPoint resetTile = null;
    private WorldPoint lastDisablerClearRequestTile = null;
    private final Set<WorldPoint> usedBonusTiles = new HashSet<>();
    private TileModifier activeModifierTool = null;

    private final Set<WorldPoint> sequenceOrder = new HashSet<>();
    private int currentSequenceNumber = 0;
    private final Set<WorldPoint> validSequenceTiles = new HashSet<>();
    private final Map<WorldPoint, Integer> sequenceHardBonusTiles = new HashMap<>();
    private final Map<WorldPoint, Integer> sequenceHardBonusTimers = new HashMap<>();

    private TileGameMode mode = TileGameMode.IDLE;
    private String activeGroupName = "";
    private boolean pendingSequenceMode = false;
    private boolean pendingSequenceModeDirty = false;

    private boolean pendingAddDisablers = false;
    private boolean pendingAddDisablersDirty = false;

    private boolean pendingDangerTiles = false;
    private boolean pendingDangerTilesDirty = false;

    private boolean pendingDirectionalTiles = false;
    private boolean pendingDirectionalTilesDirty = false;

        private boolean pendingHardMode = false;
        private boolean pendingHardModeDirty = false;
        private boolean isHardMode = false;
        private boolean isSequenceMode = false;
        private boolean isAddDisablersMode = false;
        private boolean isDangerTilesMode = false;
        private boolean isDirectionalTilesMode = false;
        private int disablerCountdown = 0;
        private int sequenceShrinkDelay = 0;
        private int nonSeqDisablerTimer = 3;
        private final LinkedHashMap<WorldPoint, Integer> sequenceTileTimers = new LinkedHashMap<>();
        private final Map<WorldPoint, Integer> powerUpTiles = new HashMap<>();
        private final Map<WorldPoint, Integer> powerUpTimers = new HashMap<>();
        private final Map<WorldPoint, Integer> dangerTileCountdowns = new HashMap<>();
        private final Map<WorldPoint, Integer> dangerTileActiveTimers = new HashMap<>();
        private final Map<WorldPoint, String> directionalTileDirections = new HashMap<>();

    private int totalRunTicks = 0;
    private int lastProcessedTick = -1;
    private int countdownTicksRemaining = 0;
    private int lastRunTicks = 0;

    private WorldPoint lastTrueTile = null;
    private TileGamePanel panel;
    private NavigationButton navButton;
    private boolean paintMode = false;
    private final List<TileGameMultiplayerMessage> pendingMultiplayerInvites = new CopyOnWriteArrayList<>();
    private final Set<String> multiplayerPlayers = ConcurrentHashMap.newKeySet();
    private volatile String multiplayerRoomId = "";
    private volatile String multiplayerLevelName = "";
    private volatile boolean multiplayerHost = false;
    private volatile boolean multiplayerActive = false;
    private volatile String multiplayerSummaryLevelName = "";
    private volatile String multiplayerSummaryWinner = "";
    private volatile String multiplayerSummaryWinnerTimeLabel = "";
    private boolean multiplayerSequenceMode = false;
    private boolean multiplayerAddDisablers = false;
    private boolean multiplayerDangerTiles = false;
    private boolean multiplayerDirectionalTiles = false;
    private boolean multiplayerHardMode = false;
    private int lastMultiplayerRegisterAttemptTick = -1000;

    @Provides
    TileGameConfig provideConfig(ConfigManager configManager)
    {
        return configManager.getConfig(TileGameConfig.class);
    }

    @Override
    protected void startUp()
    {
        overlayManager.add(tileGameOverlay);
        loadPersistentData();
        multiplayerClient = new TileGameMultiplayerClient(
                MULTIPLAYER_SERVER_URI,
                gson,
                this::handleMultiplayerMessage,
                this::say
        );

        panel = new TileGamePanel(this);
        panel.showGroups(groups);

        navButton = NavigationButton.builder()
                .tooltip("Tile Game")
                .icon(createPanelIcon())
                .priority(6)
                .panel(panel)
                .build();

        SwingUtilities.invokeLater(() -> clientToolbar.addNavigation(navButton));

        mouseManager.registerMouseListener(chooseMouseListener);

        say("Tile Game loaded. Use the side-panel buttons to create, view, play, edit, import, and export levels.");
    }

    @Override
    protected void shutDown()
    {
        savePersistentData();

        mouseManager.unregisterMouseListener(chooseMouseListener);

        overlayManager.remove(tileGameOverlay);

        if (navButton != null)
        {
            SwingUtilities.invokeLater(() -> clientToolbar.removeNavigation(navButton));
            navButton = null;
        }

        panel = null;
        if (multiplayerClient != null)
        {
            multiplayerClient.close();
            multiplayerClient = null;
        }

        groups.clear();
        highscores.clear();
        selectedTiles.clear();
        viewedGroups.clear();
        tileModifiers.clear();
        disabledTileTimers.clear();
        sequenceHardBonusTiles.clear();
        sequenceHardBonusTimers.clear();
        dangerTileCountdowns.clear();
        dangerTileActiveTimers.clear();
                activeModifierTool = null;
        resetRunState();
        pendingMultiplayerInvites.clear();
        multiplayerPlayers.clear();
        multiplayerRoomId = "";
        multiplayerLevelName = "";
        multiplayerHost = false;
        multiplayerActive = false;
        clearMultiplayerModifierSnapshot();

        mode = TileGameMode.IDLE;
    }

    void startNewLevelSelection()
    {
        hideViewedGroups();
        if (isPaintMode())
        {
            clearPaintedTiles();
        }
        resetRunState();
        selectedTiles.clear();
        activeGroupName = "";
        pendingSequenceMode = false;

        activeModifierTool = null;

        mode = TileGameMode.CHOOSE;
        updatePanel();

        showOverhead("Choose this level's tiles, then press save.");
    }

    void saveSelectedLevel(String rawGroupName)
    {
        String groupDisplayName = rawGroupName == null ? "" : rawGroupName.trim();
        String groupName = mode == TileGameMode.EDIT ? activeGroupName : normalizeGroupName(groupDisplayName);

        if (groupName.isEmpty())
        {
            showHelpOverhead("Give this level a name before saving.");
            return;
        }

        if (selectedTiles.isEmpty())
        {
            showHelpOverhead("Click at least one tile before saving " + groupDisplayName + ".");
            return;
        }

        Set<WorldPoint> saved = new HashSet<>(selectedTiles);
        groups.put(groupName, saved);
        activeGroupName = groupName;
        // Move any modifiers from "unsaved" to the final group name
        Map<WorldPoint, TileModifier> unsavedMods = tileModifiers.remove(normalizeGroupName("unsaved"));
        if (unsavedMods != null && !unsavedMods.isEmpty())
        {
            tileModifiers.put(groupName, unsavedMods);
        }

        savePersistentData();

        selectedTiles.clear();
        mode = TileGameMode.IDLE;
        updatePanel();
        updatePanelLists();

        showOverhead("Saved level " + groupName + "!");
    }

    void importLevelFromClipboard()
    {
        String json;
        try
        {
            json = readClipboardText();
        }
        catch (UnsupportedFlavorException | IOException ex)
        {
            showHelpOverhead("Clipboard does not contain readable Tile Game JSON.");
            return;
        }

        if (json == null || json.trim().isEmpty())
        {
            showHelpOverhead("Clipboard is empty. Copy exported level JSON, then press Import.");
            return;
        }

        TileGameLevelExport levelExport;
        try
        {
            levelExport = gson.fromJson(json, TileGameLevelExport.class);
        }
        catch (JsonParseException ex)
        {
            showHelpOverhead("Clipboard does not contain valid Tile Game JSON.");
            return;
        }

        if (!isValidLevelExport(levelExport))
        {
            showHelpOverhead("Clipboard JSON is missing level name or tiles.");
            return;
        }

        hideViewedGroups();
        String groupName = normalizeGroupName(levelExport.name);
        groups.put(groupName, toWorldPoints(levelExport.tiles));
        highscores.remove(groupName);
        activeGroupName = groupName;
        savePersistentData();
        updatePanel();
        updatePanelLists();
        showOverhead("Imported " + groupName + " from " + safeCreator(levelExport.creator) + ".");
    }

    void editGroup(String rawGroupName)
    {
        String groupName = normalizeGroupName(rawGroupName);

        if (groupName.isEmpty())
        {
            showHelpOverhead("Pick a level with its edit button.");
            return;
        }

        Set<WorldPoint> groupTiles = groups.get(groupName);

        if (groupTiles == null)
        {
            showHelpOverhead("No level named " + groupName + ".");
            return;
        }

        hideViewedGroups();
        resetRunState();
        selectedTiles.clear();
        selectedTiles.addAll(groupTiles);
        activeGroupName = groupName;
        activeModifierTool = null;
        mode = TileGameMode.EDIT;
        updatePanel();
        updatePanelLists();

        showOverhead("Editing " + groupName + ". Left-click tiles to add or remove them.");
    }

    void stopCurrentGame()
    {
        int coloredCount = runColoredTiles.size();
        int selectedCount = selectedTiles.size();

        leaveMultiplayerLobby();
        hideViewedGroups();
        resetRunState();
        selectedTiles.clear();
        mode = TileGameMode.IDLE;
        updatePanel();

        say("Stopped current game and cleared " + coloredCount + " run tiles and " + selectedCount + " selected tiles.");
    }

    void togglePaintMode()
    {
        paintMode = !paintMode;

        if (paintMode)
        {
            showOverhead("Paint mode ON: walk to paint tiles.");
        }
        else
        {
            showOverhead("Paint mode OFF.");
        }

        updatePanel();
    }

    boolean isPaintMode()
    {
        return paintMode;
    }

    Map<WorldPoint, Color> getPaintedTiles()
    {
        return paintedTiles;
    }

    void clearPaintedTiles()
    {
        paintedTiles.clear();
        updatePanel();
    }

    void showMultiplayerInviteDialog(Component parent)
    {
        if (groups.isEmpty())
        {
            showHelpOverhead("Create or import a level before sending multiplayer invites.");
            return;
        }

        clientThread.invokeLater(() ->
        {
            List<String> onlineFriends = onlineFriendNames();
            SwingUtilities.invokeLater(() -> showMultiplayerInviteDialog(parent, onlineFriends));
        });
    }

    private void showMultiplayerInviteDialog(Component parent, List<String> onlineFriends)
    {
        if (onlineFriends.isEmpty())
        {
            JOptionPane.showMessageDialog(parent, "No online friends found.", "Tile Game Multiplayer", JOptionPane.INFORMATION_MESSAGE);
            return;
        }

        ArrayList<String> groupNames = new ArrayList<>(groups.keySet());
        groupNames.sort(Comparator.naturalOrder());

        JPanel content = new JPanel(new BorderLayout(0, 10));
        JPanel friendsPanel = new JPanel(new GridLayout(0, 1, 0, 4));
        ArrayList<JCheckBox> friendBoxes = new ArrayList<>();
        for (String friendName : onlineFriends)
        {
            JCheckBox box = new JCheckBox(friendName);
            friendBoxes.add(box);
            friendsPanel.add(box);
        }

        JComboBox<String> levelSelector = new JComboBox<>(groupNames.toArray(new String[0]));
        content.add(new JLabel("Invite online friends:"), BorderLayout.NORTH);
        content.add(new JScrollPane(friendsPanel), BorderLayout.CENTER);
        content.add(levelSelector, BorderLayout.SOUTH);
        content.setPreferredSize(new Dimension(320, 280));

        int result = JOptionPane.showConfirmDialog(parent, content, "Tile Game Multiplayer", JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE);
        if (result != JOptionPane.OK_OPTION)
        {
            return;
        }

        ArrayList<String> invitedPlayers = new ArrayList<>();
        for (JCheckBox box : friendBoxes)
        {
            if (box.isSelected())
            {
                invitedPlayers.add(box.getText());
            }
        }

        Object selectedLevel = levelSelector.getSelectedItem();
        if (invitedPlayers.isEmpty() || !(selectedLevel instanceof String))
        {
            showHelpOverhead("Choose at least one friend and one level for multiplayer.");
            return;
        }

        clientThread.invokeLater(() -> sendMultiplayerInvite((String) selectedLevel, invitedPlayers));
    }

    void showPendingMultiplayerInvite(TileGameMultiplayerMessage invite, Component parent)
    {
        if (invite == null || invite.level == null)
        {
            return;
        }

        String levelName = normalizeGroupName(invite.levelName == null ? invite.level.name : invite.levelName);
        String message = "Host: " + safeCreator(invite.host) + "\nLevel: " + levelName;
        int result = JOptionPane.showConfirmDialog(parent, message, "Tile Game Invite Pending", JOptionPane.YES_NO_OPTION, JOptionPane.PLAIN_MESSAGE);
        if (result != JOptionPane.YES_OPTION)
        {
            declineMultiplayerInvite(invite);
            return;
        }

        if (groups.containsKey(levelName))
        {
            int overwrite = JOptionPane.showConfirmDialog(
                    parent,
                    "A level named '" + levelName + "' already exists. Accepting this invite will overwrite it.",
                    "Overwrite Tile Game Level?",
                    JOptionPane.YES_NO_OPTION,
                    JOptionPane.WARNING_MESSAGE
            );
            if (overwrite != JOptionPane.YES_OPTION)
            {
                return;
            }
        }

        clientThread.invokeLater(() -> acceptMultiplayerInvite(invite));
    }

    List<TileGameMultiplayerMessage> getPendingMultiplayerInvites()
    {
        return new ArrayList<>(pendingMultiplayerInvites);
    }

    boolean canStartMultiplayerGame()
    {
        return multiplayerHost && !multiplayerRoomId.isEmpty() && !multiplayerActive;
    }

    String getMultiplayerStatusLabel()
    {
        if (multiplayerRoomId.isEmpty())
        {
            return "Not in multiplayer";
        }

        String role = multiplayerHost ? "Hosting" : "Joined";
        return role + " " + multiplayerLevelName + " (" + multiplayerPlayers.size() + " players)";
    }

    void startMultiplayerGame()
    {
        clientThread.invokeLater(() ->
        {
            if (!canStartMultiplayerGame())
            {
                showHelpOverhead("Create a multiplayer lobby before starting.");
                return;
            }

            TileGameMultiplayerMessage message = new TileGameMultiplayerMessage();
            message.type = "start";
            message.roomId = multiplayerRoomId;
            message.host = currentPlayerName();
            message.level = createMultiplayerLevelSnapshot(multiplayerLevelName);
            setMultiplayerModifierSnapshot(message.level);
            clearMultiplayerSummary();
            sendMultiplayerMessage(message);
            multiplayerActive = true;
            playGroup(multiplayerLevelName);
            });
    }

    private List<String> onlineFriendNames()
    {
        ArrayList<String> names = new ArrayList<>();
        FriendContainer friendContainer = client.getFriendContainer();
        if (friendContainer == null || friendContainer.getMembers() == null)
        {
            return names;
        }

        for (Friend friend : friendContainer.getMembers())
        {
            String friendName = friend == null ? null : cleanPlayerName(friend.getName());
            if (friendName != null && friend.getWorld() > 0)
            {
                names.add(friendName);
            }
        }

        names.sort(String.CASE_INSENSITIVE_ORDER);
        return names;
    }

    private void sendMultiplayerInvite(String rawGroupName, List<String> invitedPlayers)
    {
        String groupName = normalizeGroupName(rawGroupName);
        if (!groups.containsKey(groupName))
        {
            showHelpOverhead("That level no longer exists.");
            return;
        }

        multiplayerRoomId = UUID.randomUUID().toString();
        multiplayerLevelName = groupName;
        multiplayerHost = true;
        multiplayerActive = false;
        clearMultiplayerSummary();
        setMultiplayerModifierSnapshot(pendingSequenceMode, pendingAddDisablers, pendingDangerTiles, pendingDirectionalTiles, pendingHardMode);
        multiplayerPlayers.clear();
        multiplayerPlayers.add(currentPlayerName());

        TileGameMultiplayerMessage message = new TileGameMultiplayerMessage();
        message.type = "invite";
        message.roomId = multiplayerRoomId;
        message.host = currentPlayerName();
        message.levelName = groupName;
        message.invitedPlayers = new ArrayList<>(invitedPlayers);
        message.level = createMultiplayerLevelSnapshot(groupName);
        sendMultiplayerMessage(message);
        updatePanel();
        showOverhead("Sent Tile Game multiplayer invite.");
    }

    private void acceptMultiplayerInvite(TileGameMultiplayerMessage invite)
    {
        if (invite == null || invite.level == null || invite.roomId == null)
        {
            return;
        }

        importMultiplayerLevel(invite.level);
        pendingMultiplayerInvites.removeIf(existing -> invite.roomId.equals(existing.roomId));
        multiplayerRoomId = invite.roomId;
        multiplayerLevelName = normalizeGroupName(invite.levelName == null ? invite.level.name : invite.levelName);
        multiplayerHost = false;
        multiplayerActive = false;
        clearMultiplayerSummary();
        setMultiplayerModifierSnapshot(invite.level);
        multiplayerPlayers.clear();
        if (invite.host != null)
        {
            multiplayerPlayers.add(invite.host);
        }
        multiplayerPlayers.add(currentPlayerName());

        TileGameMultiplayerMessage message = new TileGameMultiplayerMessage();
        message.type = "join";
        message.roomId = invite.roomId;
        message.player = currentPlayerName();
        sendMultiplayerMessage(message);
        updatePanel();
        updatePanelLists();
        showOverhead("Joined " + safeCreator(invite.host) + "'s Tile Game lobby.");
    }

    private void declineMultiplayerInvite(TileGameMultiplayerMessage invite)
    {
        if (invite == null || invite.roomId == null)
        {
            return;
        }

        clientThread.invokeLater(() ->
        {
            pendingMultiplayerInvites.removeIf(existing -> invite.roomId.equals(existing.roomId));
            TileGameMultiplayerMessage message = new TileGameMultiplayerMessage();
            message.type = "decline";
            message.roomId = invite.roomId;
            message.player = currentPlayerName();
            sendMultiplayerMessage(message);
            updatePanel();
        });
    }

    private void sendMultiplayerMessage(TileGameMultiplayerMessage message)
    {
        if (multiplayerClient == null)
        {
            showHelpOverhead("Tile Game multiplayer is not ready yet.");
            return;
        }

        multiplayerClient.send(currentPlayerName(), message);
    }

    private void handleMultiplayerMessage(TileGameMultiplayerMessage message)
    {
        if (message == null || message.type == null)
        {
            return;
        }

        switch (message.type)
        {
            case "invite":
                handleMultiplayerInvite(message);
                break;
            case "player_joined":
                handleMultiplayerPlayerJoined(message);
                break;
            case "player_declined":
                handleMultiplayerPlayerDeclined(message);
                break;
            case "player_left":
                handleMultiplayerPlayerLeft(message);
                break;
            case "start":
                clientThread.invokeLater(() -> handleMultiplayerStart(message));
                break;
            case "state":
                clientThread.invokeLater(() -> handleMultiplayerState(message));
                break;
            case "clear_disablers":
                clientThread.invokeLater(() -> handleMultiplayerClearDisablers(message));
                break;
            case "game_over":
                clientThread.invokeLater(() -> handleMultiplayerGameOver(message));
                break;
            case "lobby_closed":
                clientThread.invokeLater(() -> handleMultiplayerLobbyClosed(message));
                break;
            case "error":
                if (message.roomId != null)
                {
                    pendingMultiplayerInvites.removeIf(existing -> message.roomId.equals(existing.roomId));
                    updatePanel();
                }
                say(message.error == null ? "Tile Game multiplayer server error." : message.error);
                break;
            case "invite_sent":
                say("Tile Game invite sent. Delivered: " + message.deliveredPlayers + ", queued: " + message.queuedPlayers + ".");
                break;
            default:
                break;
        }
    }

    private void handleMultiplayerInvite(TileGameMultiplayerMessage message)
    {
        if (message.roomId == null || message.level == null)
        {
            return;
        }

        pendingMultiplayerInvites.removeIf(existing -> message.roomId.equals(existing.roomId));
        pendingMultiplayerInvites.add(message);
        updatePanel();
        showOverhead("Tile Game invite pending from " + safeCreator(message.host) + ".");
    }

    private void handleMultiplayerPlayerJoined(TileGameMultiplayerMessage message)
    {
        if (!multiplayerRoomId.equals(message.roomId))
        {
            return;
        }

        if (message.players != null)
        {
            multiplayerPlayers.clear();
            multiplayerPlayers.addAll(message.players);
        }
        if (message.player != null)
        {
            multiplayerPlayers.add(message.player);
            if (multiplayerHost)
            {
                showOverhead(message.player + " joined the Tile Game lobby.");
            }
        }
        updatePanel();
    }

    private void handleMultiplayerPlayerDeclined(TileGameMultiplayerMessage message)
    {
        if (multiplayerHost && multiplayerRoomId.equals(message.roomId) && message.player != null)
        {
            showOverhead(message.player + " declined the Tile Game invite.");
        }
    }

    private void handleMultiplayerPlayerLeft(TileGameMultiplayerMessage message)
    {
        if (!multiplayerRoomId.equals(message.roomId))
        {
            return;
        }

        if (message.players != null)
        {
            multiplayerPlayers.clear();
            multiplayerPlayers.addAll(message.players);
        }
        else if (message.player != null)
        {
            multiplayerPlayers.remove(message.player);
        }

        updatePanel();
        if (message.player != null)
        {
            showOverhead(message.player + " left the Tile Game lobby.");
        }
    }

    private void handleMultiplayerStart(TileGameMultiplayerMessage message)
    {
        if (multiplayerHost || !multiplayerRoomId.equals(message.roomId))
        {
            return;
        }

        multiplayerActive = true;
        clearMultiplayerSummary();
        if (message.level != null)
        {
            setMultiplayerModifierSnapshot(message.level);
            importMultiplayerLevel(message.level);
        }
        playGroup(multiplayerLevelName);
    }

    private void handleMultiplayerState(TileGameMultiplayerMessage message)
    {
        if (multiplayerHost || !multiplayerActive || !multiplayerRoomId.equals(message.roomId) || message.state == null)
        {
            return;
        }

        applyMultiplayerState(message.state);
        updatePanel();
    }

    private void handleMultiplayerClearDisablers(TileGameMultiplayerMessage message)
    {
        // Host-side: a participant stepped on the clear (blue) tile — clear disablers for everyone
        if (!multiplayerHost || !multiplayerActive || !multiplayerRoomId.equals(message.roomId))
        {
            return;
        }

        WorldPoint clearedTile = fromMultiplayerTile(message.tile);
        if (clearedTile == null || resetTile == null || !resetTile.equals(clearedTile) || disabledTileTimers.isEmpty())
        {
            return;
        }

        clearDisablersFromReset(clearedTile);
        broadcastMultiplayerStateIfHost();
        updatePanel();
    }

    private void handleMultiplayerGameOver(TileGameMultiplayerMessage message)
    {
        if (!multiplayerRoomId.equals(message.roomId))
        {
            return;
        }

        String levelName = normalizeGroupName(message.levelName == null ? multiplayerLevelName : message.levelName);
        multiplayerSummaryLevelName = levelName;
        multiplayerSummaryWinner = safeCreator(message.winner);
        multiplayerSummaryWinnerTimeLabel = message.totalTicks > 0 ? formatTicks(message.totalTicks) : "-";
        activeGroupName = levelName;
        multiplayerActive = false;
        mode = TileGameMode.DONE;
        resetRunState();
        multiplayerRoomId = "";
        multiplayerLevelName = "";
        multiplayerHost = false;
        clearMultiplayerModifierSnapshot();
        multiplayerPlayers.clear();
        updatePanel();
        String reason = message.reason == null || message.reason.trim().isEmpty() ? "" : " " + message.reason;
        showOverhead("Multiplayer game over! Winner: " + safeCreator(message.winner) + "." + reason);
    }

    private void handleMultiplayerLobbyClosed(TileGameMultiplayerMessage message)
    {
        if (message.roomId != null)
        {
            pendingMultiplayerInvites.removeIf(existing -> message.roomId.equals(existing.roomId));
        }

        if (!multiplayerRoomId.equals(message.roomId))
        {
            updatePanel();
            return;
        }

        multiplayerActive = false;
        multiplayerRoomId = "";
        multiplayerLevelName = "";
        multiplayerHost = false;
        clearMultiplayerModifierSnapshot();
        multiplayerPlayers.clear();
        updatePanel();
        showOverhead("Tile Game lobby closed.");
    }

    void closeMultiplayerLobby()
    {
        leaveMultiplayerLobby();
    }

    void leaveMultiplayerLobby()
    {
        if (multiplayerRoomId.isEmpty())
        {
            return;
        }

        TileGameMultiplayerMessage message = new TileGameMultiplayerMessage();
        message.type = multiplayerHost && !multiplayerActive ? "close" : "leave";
        message.roomId = multiplayerRoomId;
        message.player = currentPlayerName();
        sendMultiplayerMessage(message);
        multiplayerRoomId = "";
        multiplayerLevelName = "";
        multiplayerHost = false;
        multiplayerActive = false;
        clearMultiplayerModifierSnapshot();
        multiplayerPlayers.clear();
        updatePanel();
    }

    boolean isMultiplayerHost()
    {
        return multiplayerHost;
    }

    boolean isInMultiplayerLobby()
    {
        return !multiplayerRoomId.isEmpty();
    }

    boolean isMultiplayerActive()
    {
        return multiplayerActive;
    }

    String getDisplayedMultiplayerLevelLabel()
    {
        return multiplayerSummaryLevelName.isEmpty() ? activeGroupName : multiplayerSummaryLevelName;
    }

    String getDisplayedMultiplayerWinnerLabel()
    {
        return multiplayerSummaryLevelName.isEmpty() ? "" : multiplayerSummaryWinner;
    }

    String getDisplayedMultiplayerWinnerTimeLabel()
    {
        return multiplayerSummaryLevelName.isEmpty() ? getLastRunTimeLabel() : multiplayerSummaryWinnerTimeLabel;
    }

        void setActiveModifierTool(TileModifier modifier)
        {
            activeModifierTool = modifier;
            updatePanel();
        }

        void clearActiveModifierTool()
        {
            activeModifierTool = null;
            updatePanel();
        }

        TileModifier getActiveModifierTool()
        {
            return activeModifierTool;
        }

        Map<WorldPoint, TileModifier> getModifiersForActiveGroup()
        {
            String groupName = activeGroupName.isEmpty() ? normalizeGroupName("unsaved") : normalizeGroupName(activeGroupName);
            return tileModifiers.getOrDefault(groupName, Map.of());
        }

        Map<WorldPoint, TileModifier> getModifiersForGroup(String groupName)
        {
            return tileModifiers.getOrDefault(normalizeGroupName(groupName), Map.of());
        }

        Map<WorldPoint, Integer> getDisabledTileTimers()
        {
            return disabledTileTimers;
        }

        Map<WorldPoint, WorldPoint> getResetTiles()
        {
                    if (resetTile == null || disabledTileTimers.isEmpty())
            {
                        return Map.of();
            }

                    Map<WorldPoint, WorldPoint> result = new HashMap<>();
                    for (WorldPoint disabled : disabledTileTimers.keySet())
                    {
                        result.put(disabled, resetTile);
                    }
                    return result;
                }

        Set<WorldPoint> getUsedBonusTiles()
        {
            return usedBonusTiles;
        }

    void restartCurrentGame()
    {
        String groupName = activeGroupName;

        if (groupName == null || groupName.isEmpty() || !groups.containsKey(groupName))
        {
            showHelpOverhead("No active level to restart. Use the play button on a level.");
            return;
        }

        // Clear used bonus markers so bonuses are available again when restarting
        usedBonusTiles.clear();

        playGroup(groupName);
    }

    void trashCurrentLevelSelection()
    {
        hideViewedGroups();
        selectedTiles.clear();
        tileModifiers.remove(normalizeGroupName("unsaved"));
        activeModifierTool = null;
        resetRunState();
        mode = TileGameMode.IDLE;
        updatePanel();
        updatePanelLists();
        showOverhead("Trashed current level selection.");
    }

    void playGroup(String rawGroupName)
    {
        String groupName = normalizeGroupName(rawGroupName);

        if (groupName.isEmpty())
        {
            showHelpOverhead("Pick a level with its play button.");
            return;
        }

        Set<WorldPoint> groupTiles = groups.get(groupName);

        if (groupTiles == null || groupTiles.isEmpty())
        {
            showHelpOverhead("No playable level named " + groupName + ". Create one with the + button.");
            return;
        }

       Set<WorldPoint> tilesForRun = new HashSet<>(groupTiles);
       clientThread.invokeLater(() ->
        {
           hideViewedGroups();
           if (isPaintMode())
           {
               clearPaintedTiles();
           }
           resetRunState();
           selectedTiles.clear();

           boolean multiplayerParticipant = isMultiplayerParticipant();
           boolean hardModeEnabled = multiplayerParticipant ? multiplayerHardMode : pendingHardMode;
           boolean sequenceEnabled = multiplayerParticipant ? multiplayerSequenceMode : pendingSequenceMode;
           boolean addDisablersEnabled = hardModeEnabled || (multiplayerParticipant ? multiplayerAddDisablers : pendingAddDisablers);
           boolean dangerTilesEnabled = hardModeEnabled || (multiplayerParticipant ? multiplayerDangerTiles : pendingDangerTiles);
           boolean directionalTilesEnabled = hardModeEnabled || (multiplayerParticipant ? multiplayerDirectionalTiles : pendingDirectionalTiles);

                 activeLevelGroupTiles.addAll(tilesForRun);
                 activeGroupName = groupName;
                  pendingSequenceMode = sequenceEnabled;
                  pendingSequenceModeDirty = false;

                  pendingAddDisablers = addDisablersEnabled;
                  pendingAddDisablersDirty = false;

                  pendingDangerTiles = dangerTilesEnabled;
                  pendingDangerTilesDirty = false;

                  pendingDirectionalTiles = directionalTilesEnabled;
                  pendingDirectionalTilesDirty = false;

                  pendingHardMode = hardModeEnabled;
                  pendingHardModeDirty = false;
                  isHardMode = hardModeEnabled;
                  isSequenceMode = sequenceEnabled;
                  isAddDisablersMode = addDisablersEnabled;
                  isDangerTilesMode = dangerTilesEnabled;
                  isDirectionalTilesMode = directionalTilesEnabled;

           generateSequenceOrder(tilesForRun, sequenceEnabled);

           countdownTicksRemaining = COUNTDOWN_START_TICKS;
           mode = TileGameMode.COUNTDOWN;
           updatePanel();

           showOverhead(String.valueOf(countdownTicksRemaining));
       });
    }

    private void generateSequenceOrder(Set<WorldPoint> groupTiles, boolean sequenceEnabled)
    {
        sequenceOrder.clear();
        validSequenceTiles.clear();
        currentSequenceNumber = 0;

        if (sequenceEnabled)
        {
            sequenceOrder.addAll(groupTiles);
            selectNextValidTiles();
        }
    }

    private void selectNextValidTiles()
    {
        currentSequenceNumber++;
        validSequenceTiles.clear();
        sequenceTileTimers.clear();

        List<WorldPoint> candidates = new ArrayList<>(sequenceOrder);
        candidates.removeAll(runColoredTiles.keySet());
        candidates.removeIf(this::isDangerTileOccupied);
        candidates.removeIf(this::isDirectionalTileOccupied);

        WorldPoint currentTile = getCurrentPlayerTile();
        WorldView worldView = client.getTopLevelWorldView();
        boolean canScorePathing = currentTile != null
                && worldView != null
                && worldView.getScene() != null
                && worldView.getCollisionMaps() != null;
        if (!canScorePathing)
        {
            selectRandomSequenceTiles(candidates);
            sequenceShrinkDelay = 1;
            return;
        }

        Map<WorldPoint, Integer> stepCounts = new HashMap<>();
        for (WorldPoint tile : candidates)
        {
            int steps = getWalkStepCount(currentTile, tile);
            if (steps != Integer.MAX_VALUE)
            {
                stepCounts.put(tile, steps);
            }
        }

        List<WorldPoint> orderedSelections = new ArrayList<>();
        int previousThreshold = 0;
        int[] thresholds = new int[] { 2, 4, 6, 8 };
        java.util.Random random = new java.util.Random(System.nanoTime());
        List<WorldPoint> available = new ArrayList<>(candidates);

        for (int threshold : thresholds)
        {
            if (available.isEmpty())
            {
                break;
            }

            List<WorldPoint> bandCandidates = new ArrayList<>();
            for (WorldPoint tile : available)
            {
                Integer steps = stepCounts.get(tile);
                if (steps == null || steps <= previousThreshold || steps > threshold)
                {
                    continue;
                }

                bandCandidates.add(tile);
            }

            List<WorldPoint> pool = bandCandidates.isEmpty() ? available : bandCandidates;
            WorldPoint tile = pool.get(random.nextInt(pool.size()));
            orderedSelections.add(tile);
            available.remove(tile);

            previousThreshold = threshold;
        }

        if (orderedSelections.size() < 4)
        {
            List<WorldPoint> topUpCandidates = new ArrayList<>(sequenceOrder);
            topUpCandidates.removeAll(runColoredTiles.keySet());
            topUpCandidates.removeIf(this::isDangerTileOccupied);
            topUpCandidates.removeIf(this::isDirectionalTileOccupied);
            topUpCandidates.removeAll(orderedSelections);

            while (orderedSelections.size() < 4 && !topUpCandidates.isEmpty())
            {
                WorldPoint tile = topUpCandidates.remove(random.nextInt(topUpCandidates.size()));
                orderedSelections.add(tile);
            }
        }

        for (int i = 0; i < orderedSelections.size(); i++)
        {
            WorldPoint tile = orderedSelections.get(i);
            validSequenceTiles.add(tile);
            sequenceTileTimers.put(tile, i + 1);
        }

        sequenceShrinkDelay = 1;
    }

    private void selectRandomSequenceTiles(List<WorldPoint> candidates)
    {
        List<WorldPoint> nonDirectionalCandidates = new ArrayList<>(candidates);
        nonDirectionalCandidates.removeIf(this::isDirectionalTileOccupied);
        Collections.shuffle(nonDirectionalCandidates, new java.util.Random(System.nanoTime()));
        int count = Math.min(4, nonDirectionalCandidates.size());
        for (int i = 0; i < count; i++)
        {
            WorldPoint tile = nonDirectionalCandidates.get(i);
            validSequenceTiles.add(tile);
            sequenceTileTimers.put(tile, i + 1);
        }
    }

    private void processSequenceHardBonusTimers()
    {
        if (sequenceHardBonusTimers.isEmpty())
        {
            return;
        }

        List<WorldPoint> expired = new ArrayList<>();
        for (Map.Entry<WorldPoint, Integer> entry : sequenceHardBonusTimers.entrySet())
        {
            int remaining = entry.getValue() - 1;
            if (remaining <= 0)
            {
                expired.add(entry.getKey());
            }
            else
            {
                entry.setValue(remaining);
            }
        }

        for (WorldPoint tile : expired)
        {
            sequenceHardBonusTimers.remove(tile);
            sequenceHardBonusTiles.remove(tile);
        }
    }

    private void clearSequenceHardBonusTiles()
    {
        sequenceHardBonusTiles.clear();
        sequenceHardBonusTimers.clear();
    }

    private void maybeSpawnSequenceHardBonusTile(WorldPoint currentTile)
    {
        if (!isHardMode || !isSequenceModeEnabled() || !sequenceHardBonusTiles.isEmpty())
        {
            return;
        }

        if (new java.util.Random().nextDouble() >= SEQUENCE_HARD_BONUS_SPAWN_CHANCE)
        {
            return;
        }

        List<WorldPoint> candidates = new ArrayList<>();
        Map<WorldPoint, TileModifier> mods = tileModifiers.get(normalizeGroupName(activeGroupName));
        for (WorldPoint tile : activeLevelGroupTiles)
        {
            if (tile.equals(currentTile)
                    || runColoredTiles.containsKey(tile)
                    || disabledTileTimers.containsKey(tile)
                    || sequenceHardBonusTiles.containsKey(tile)
                    || isDangerTileOccupied(tile)
                    || isDirectionalTileOccupied(tile)
                    || validSequenceTiles.contains(tile)
                    || (mods != null && mods.containsKey(tile)))
            {
                continue;
            }

            int dist = Math.abs(tile.getX() - currentTile.getX()) + Math.abs(tile.getY() - currentTile.getY());
            if (dist > 4)
            {
                continue;
            }

            candidates.add(tile);
        }

        if (candidates.isEmpty())
        {
            return;
        }

        WorldPoint target = candidates.get(new java.util.Random().nextInt(candidates.size()));
        sequenceHardBonusTiles.put(target, 2 + new java.util.Random().nextInt(3));
        sequenceHardBonusTimers.put(target, SEQUENCE_HARD_BONUS_TICKS);
    }

    private void claimSequenceTile(WorldPoint tile)
    {
        if (tile == null || runColoredTiles.containsKey(tile))
        {
            return;
        }

        runColoredTiles.put(tile, randomColor());
        playPaintSound();

        if (isHardMode)
        {
            disablerCountdown = 0;
        }

        if (isSequenceModeEnabled())
        {
            if (isMultiplayerParticipant())
            {
                validSequenceTiles.remove(tile);
                sequenceTileTimers.remove(tile);
            }
            else
            {
                selectNextValidTiles();
            }
        }
    }

    private void applySequenceHardBonus(WorldPoint currentTile)
    {
        Integer bonusCount = sequenceHardBonusTiles.remove(currentTile);
        if (bonusCount == null)
        {
            return;
        }

        sequenceHardBonusTimers.remove(currentTile);
        claimSequenceTile(currentTile);
        showOverhead("Bonus Tiles!");

        List<WorldPoint> candidates = new ArrayList<>();
        Map<WorldPoint, TileModifier> mods = tileModifiers.get(normalizeGroupName(activeGroupName));
        for (WorldPoint tile : activeLevelGroupTiles)
        {
            if (tile.equals(currentTile)
                    || runColoredTiles.containsKey(tile)
                    || disabledTileTimers.containsKey(tile)
                    || sequenceHardBonusTiles.containsKey(tile)
                    || isDirectionalTileOccupied(tile)
                    || (mods != null && mods.containsKey(tile)))
            {
                continue;
            }

            candidates.add(tile);
        }

        Collections.shuffle(candidates, new java.util.Random(System.nanoTime()));
        int toColor = Math.min(bonusCount, candidates.size());
        for (int i = 0; i < toColor; i++)
        {
            claimSequenceTile(candidates.get(i));
        }
    }

    private void processDangerTiles(WorldPoint currentTile)
    {
        if (!isDangerTilesEnabled() || activeLevelGroupTiles.isEmpty())
        {
            return;
        }

        boolean currentTileWasActive = dangerTileActiveTimers.containsKey(currentTile);

        List<WorldPoint> expiredDangerTiles = new ArrayList<>();
        for (Map.Entry<WorldPoint, Integer> entry : dangerTileActiveTimers.entrySet())
        {
            int remaining = entry.getValue() - 1;
            if (remaining <= 0)
            {
                expiredDangerTiles.add(entry.getKey());
            }
            else
            {
                entry.setValue(remaining);
            }
        }
        for (WorldPoint tile : expiredDangerTiles)
        {
            dangerTileActiveTimers.remove(tile);
        }

        List<WorldPoint> activatedDangerTiles = new ArrayList<>();
        for (Map.Entry<WorldPoint, Integer> entry : dangerTileCountdowns.entrySet())
        {
            int remaining = entry.getValue() - 1;
            if (remaining <= 0)
            {
                activatedDangerTiles.add(entry.getKey());
            }
            else
            {
                entry.setValue(remaining);
            }
        }
        for (WorldPoint tile : activatedDangerTiles)
        {
            dangerTileCountdowns.remove(tile);
            dangerTileActiveTimers.put(tile, DANGER_TILE_ACTIVE_TICKS);
        }

        if (currentTileWasActive || dangerTileActiveTimers.containsKey(currentTile))
        {
            applyDangerTilePenalty(currentTile);
            if (getDangerSpawnCandidates(currentTile).isEmpty())
            {
                removeDangerTileToMakeRoom();
            }
        }

        int activeCount = dangerTileCountdowns.size() + dangerTileActiveTimers.size();
        List<WorldPoint> candidates = getDangerSpawnCandidates(currentTile);
        if (activeCount < DANGER_TILE_MAX_ACTIVE
                && candidates.size() >= DANGER_TILE_MIN_SAFE_TILES
                && new java.util.Random().nextDouble() < DANGER_TILE_SPAWN_CHANCE)
        {
            Collections.shuffle(candidates, new java.util.Random(System.nanoTime()));
            int spawnCount = Math.min(DANGER_TILE_BATCH_SIZE, Math.min(DANGER_TILE_MAX_ACTIVE - activeCount, candidates.size()));
            for (int i = 0; i < spawnCount; i++)
            {
                dangerTileCountdowns.put(candidates.get(i), DANGER_TILE_COUNTDOWN_TICKS);
            }
        }
    }

    private boolean processDirectionalTiles(WorldPoint currentTile)
    {
        if (!isDirectionalTilesEnabled() || activeLevelGroupTiles.isEmpty())
        {
            return false;
        }

        boolean tileHasDirectionalLabel = currentTile != null && directionalTileDirections.containsKey(currentTile);
        if (tileHasDirectionalLabel && lastTrueTile != null)
        {
            String direction = directionalTileDirections.get(currentTile);
            if (isCorrectDirectionalEntry(currentTile, lastTrueTile, direction))
            {
                directionalTileDirections.remove(currentTile);
                if (currentTile != null && !runColoredTiles.containsKey(currentTile))
                {
                    if (!isSequenceModeEnabled() || validSequenceTiles.contains(currentTile))
                    {
                        claimSequenceTile(currentTile);
                    }
                    else
                    {
                        runColoredTiles.put(currentTile, randomColor());
                        playPaintSound();
                    }
                }
            }
        }

        spawnDirectionalTile(currentTile);
        return tileHasDirectionalLabel;
    }

    private boolean processSyncedDirectionalTile(WorldPoint currentTile)
    {
        if (!isDirectionalTilesEnabled() || activeLevelGroupTiles.isEmpty())
        {
            return false;
        }

        boolean tileHasDirectionalLabel = currentTile != null && directionalTileDirections.containsKey(currentTile);
        if (tileHasDirectionalLabel && lastTrueTile != null)
        {
            String direction = directionalTileDirections.get(currentTile);
            if (isCorrectDirectionalEntry(currentTile, lastTrueTile, direction) && !runColoredTiles.containsKey(currentTile))
            {
                if (!isSequenceModeEnabled() || validSequenceTiles.contains(currentTile))
                {
                    claimSequenceTile(currentTile);
                }
                else
                {
                    runColoredTiles.put(currentTile, randomColor());
                    playPaintSound();
                }
            }
        }

        return tileHasDirectionalLabel;
    }

    private void processSyncedDangerTile(WorldPoint currentTile)
    {
        if (currentTile != null && dangerTileActiveTimers.containsKey(currentTile))
        {
            applyDangerTilePenalty(currentTile);
        }
    }

    private void spawnDirectionalTile(WorldPoint currentTile)
    {
        if (currentTile == null
                || directionalTileDirections.size() >= DIRECTIONAL_TILE_MAX_ACTIVE
                || new java.util.Random().nextDouble() >= DIRECTIONAL_TILE_SPAWN_CHANCE)
        {
            return;
        }

        List<WorldPoint> candidates = new ArrayList<>();
        Map<WorldPoint, TileModifier> mods = tileModifiers.get(normalizeGroupName(activeGroupName));
        for (WorldPoint tile : activeLevelGroupTiles)
        {
            if (tile.equals(currentTile)
                    || runColoredTiles.containsKey(tile)
                    || disabledTileTimers.containsKey(tile)
                    || dangerTileCountdowns.containsKey(tile)
                    || dangerTileActiveTimers.containsKey(tile)
                    || sequenceHardBonusTiles.containsKey(tile)
                    || validSequenceTiles.contains(tile)
                    || directionalTileDirections.containsKey(tile)
                    || powerUpTiles.containsKey(tile)
                    || (mods != null && mods.containsKey(tile))
                    || (resetTile != null && resetTile.equals(tile)))
            {
                continue;
            }

            candidates.add(tile);
        }

        if (candidates.isEmpty())
        {
            return;
        }

        List<DirectionalSpawnOption> options = new ArrayList<>();
        for (WorldPoint target : candidates)
        {
            options.addAll(getDirectionalSpawnOptions(target));
        }

        if (options.isEmpty())
        {
            return;
        }

        DirectionalSpawnOption option = options.get(new java.util.Random().nextInt(options.size()));
        directionalTileDirections.put(option.target, option.direction);
    }

    private boolean isDirectionalTileOccupied(WorldPoint tile)
    {
        return directionalTileDirections.containsKey(tile);
    }

    private List<DirectionalSpawnOption> getDirectionalSpawnOptions(WorldPoint target)
    {
        List<DirectionalSpawnOption> options = new ArrayList<>();
        if (target == null || !isWalkableAndVisible(target))
        {
            return options;
        }

        int x = target.getX();
        int y = target.getY();
        int plane = target.getPlane();

        addDirectionalOption(options, target, new WorldPoint(x, y + 1, plane), "N");
        addDirectionalOption(options, target, new WorldPoint(x + 1, y + 1, plane), "NE");
        addDirectionalOption(options, target, new WorldPoint(x + 1, y, plane), "E");
        addDirectionalOption(options, target, new WorldPoint(x + 1, y - 1, plane), "SE");
        addDirectionalOption(options, target, new WorldPoint(x, y - 1, plane), "S");
        addDirectionalOption(options, target, new WorldPoint(x - 1, y - 1, plane), "SW");
        addDirectionalOption(options, target, new WorldPoint(x - 1, y, plane), "W");
        addDirectionalOption(options, target, new WorldPoint(x - 1, y + 1, plane), "NW");

        return options;
    }

    private void addDirectionalOption(List<DirectionalSpawnOption> options, WorldPoint target, WorldPoint source, String direction)
    {
        if (target == null || source == null || !isWalkableAndVisible(source))
        {
            return;
        }

        if (canStepBetween(source, target))
        {
            options.add(new DirectionalSpawnOption(target, direction));
        }
    }

    private boolean canStepBetween(WorldPoint source, WorldPoint target)
    {
        if (source == null || target == null || source.getPlane() != target.getPlane())
        {
            return false;
        }

        WorldView worldView = client.getTopLevelWorldView();
        if (worldView == null)
        {
            return false;
        }

        LocalPoint sourceLocal = LocalPoint.fromWorld(worldView, source);
        LocalPoint targetLocal = LocalPoint.fromWorld(worldView, target);
        if (sourceLocal == null || targetLocal == null)
        {
            return false;
        }

        CollisionData[] collisionMaps = worldView.getCollisionMaps();
        if (collisionMaps == null)
        {
            return false;
        }

        int plane = source.getPlane();
        if (plane < 0 || plane >= collisionMaps.length || collisionMaps[plane] == null)
        {
            return false;
        }

        int[][] flags = collisionMaps[plane].getFlags();
        if (!isTileWithinBounds(flags, sourceLocal.getSceneX(), sourceLocal.getSceneY())
                || !isTileWithinBounds(flags, targetLocal.getSceneX(), targetLocal.getSceneY()))
        {
            return false;
        }

        return canStep(flags, sourceLocal.getSceneX(), sourceLocal.getSceneY(), targetLocal.getSceneX(), targetLocal.getSceneY());
    }

    private String directionalLabelFor(WorldPoint tile, WorldPoint previousTile)
    {
        if (tile == null || previousTile == null)
        {
            return null;
        }

        int dx = previousTile.getX() - tile.getX();
        int dy = previousTile.getY() - tile.getY();

        if (dx == -1 && dy == 0)
        {
            return "W";
        }
        if (dx == 1 && dy == 0)
        {
            return "E";
        }
        if (dx == 0 && dy == 1)
        {
            return "N";
        }
        if (dx == 0 && dy == -1)
        {
            return "S";
        }
        if (dx == -1 && dy == 1)
        {
            return "NW";
        }
        if (dx == 1 && dy == 1)
        {
            return "NE";
        }
        if (dx == -1 && dy == -1)
        {
            return "SW";
        }
        if (dx == 1 && dy == -1)
        {
            return "SE";
        }

        return null;
    }

    private static final class DirectionalSpawnOption
    {
        private final WorldPoint target;
        private final String direction;

        private DirectionalSpawnOption(WorldPoint target, String direction)
        {
            this.target = target;
            this.direction = direction;
        }
    }

    private boolean isCorrectDirectionalEntry(WorldPoint tile, WorldPoint previousTile, String expectedDirection)
    {
        if (tile == null || previousTile == null || expectedDirection == null)
        {
            return false;
        }

        int dx = previousTile.getX() - tile.getX();
        int dy = previousTile.getY() - tile.getY();
        if (!isDirectionalApproachVector(expectedDirection, dx, dy))
        {
            return false;
        }

        if (Math.max(Math.abs(dx), Math.abs(dy)) <= 1)
        {
            return expectedDirection.equals(directionalLabelFor(tile, previousTile));
        }

        WorldPoint intermediate = new WorldPoint(
                tile.getX() + Integer.signum(dx),
                tile.getY() + Integer.signum(dy),
                tile.getPlane()
        );

        if (!isWalkableAndVisible(intermediate))
        {
            return false;
        }

        return canStepBetween(previousTile, intermediate) && canStepBetween(intermediate, tile);
    }

    private boolean isDirectionalApproachVector(String expectedDirection, int dx, int dy)
    {
        switch (expectedDirection)
        {
            case "N":
                return dx == 0 && (dy == 1 || dy == 2);
            case "NE":
                return (dx == 1 || dx == 2) && (dy == 1 || dy == 2) && Math.abs(dx) == Math.abs(dy);
            case "E":
                return (dx == 1 || dx == 2) && dy == 0;
            case "SE":
                return (dx == 1 || dx == 2) && (dy == -1 || dy == -2) && Math.abs(dx) == Math.abs(dy);
            case "S":
                return dx == 0 && (dy == -1 || dy == -2);
            case "SW":
                return (dx == -1 || dx == -2) && (dy == -1 || dy == -2) && Math.abs(dx) == Math.abs(dy);
            case "W":
                return (dx == -1 || dx == -2) && dy == 0;
            case "NW":
                return (dx == -1 || dx == -2) && (dy == 1 || dy == 2) && Math.abs(dx) == Math.abs(dy);
            default:
                return false;
        }
    }

    private void applyDangerTilePenalty(WorldPoint currentTile)
    {
        List<WorldPoint> coloredTiles = new ArrayList<>(runColoredTiles.keySet());
        if (coloredTiles.isEmpty())
        {
            return;
        }

        if (coloredTiles.size() > 1)
        {
            coloredTiles.remove(currentTile);
        }

        if (coloredTiles.isEmpty())
        {
            coloredTiles.addAll(runColoredTiles.keySet());
        }

        if (coloredTiles.isEmpty())
        {
            return;
        }

        WorldPoint tileToUncolor = coloredTiles.get(new java.util.Random().nextInt(coloredTiles.size()));
        runColoredTiles.remove(tileToUncolor);
    }

    void deleteGroup(String rawGroupName)
    {
        String groupName = normalizeGroupName(rawGroupName);

        if (groupName.isEmpty())
        {
            showHelpOverhead("Pick a level with its delete button.");
            return;
        }

        if (!groups.containsKey(groupName))
        {
            showHelpOverhead("No level named " + groupName + ".");
            return;
        }

        hideViewedGroups();
        groups.remove(groupName);
        highscores.remove(groupName);
                tileModifiers.remove(groupName);

        if (groupName.equals(activeGroupName))
        {
            resetRunState();
            selectedTiles.clear();
            mode = TileGameMode.IDLE;
        }

        savePersistentData();
        updatePanel();
        updatePanelLists();
        showOverhead("Deleted level " + groupName + ".");
    }

    void toggleViewGroup(String rawGroupName)
    {
        String groupName = normalizeGroupName(rawGroupName);

        if (groupName.isEmpty() || !groups.containsKey(groupName))
        {
            showHelpOverhead("That level no longer exists.");
            return;
        }

        if (viewedGroups.contains(groupName))
        {
            viewedGroups.remove(groupName);
            showOverhead("Hid " + groupName + ".");
        }
        else
        {
            hideViewedGroups();
            viewedGroups.add(groupName);
            showOverhead("Viewing " + groupName + " in black.");
        }

        updatePanelLists();
    }

    private final MouseAdapter chooseMouseListener = new MouseAdapter()
    {
        @Override
        public MouseEvent mousePressed(MouseEvent event)
        {
            if (mode != TileGameMode.CHOOSE)
            {
                if (mode != TileGameMode.EDIT)
                {
                    return event;
                }
            }

            if (event.getButton() != MouseEvent.BUTTON1)
            {
                return event;
            }

            // Hold Ctrl to walk to the clicked tile instead of marking it.
            if (event.isControlDown())
            {
                return event;
            }

            WorldPoint clickedTile = getTileAtCanvasPoint(event.getPoint());

            if (clickedTile == null)
            {
                showHelpOverhead("No tile found under click.");
                return null;
            }

            // If a modifier tool is active, apply or toggle the modifier on the clicked tile.
            if (activeModifierTool != null)
            {
                String groupName = normalizeGroupName(activeGroupName.isEmpty() ? "unsaved" : activeGroupName);
                Map<WorldPoint, TileModifier> mods = tileModifiers.computeIfAbsent(groupName, k -> new java.util.LinkedHashMap<>());

                TileModifier existing = mods.get(clickedTile);

                // Prevent placing a DISABLE modifier on a tile that's currently colored in a run
                if (activeModifierTool == TileModifier.DISABLE && runColoredTiles.containsKey(clickedTile))
                {
                    showHelpOverhead("Cannot apply DISABLE to a currently colored tile.");
                    return null;
                }

                if (existing == activeModifierTool)
                {
                    // Toggle off — remove the modifier
                    mods.remove(clickedTile);
                    say("Removed " + activeModifierTool + " from tile.");
                }
                else
                {
                    mods.put(clickedTile, activeModifierTool);
                    say("Applied " + activeModifierTool + " to tile.");
                }

                saveEditedGroup();
                updatePanel();
                return null;
            }

            if (selectedTiles.contains(clickedTile))
            {
                selectedTiles.remove(clickedTile);
                saveEditedGroup();
                updatePanel();
                say("Deselected tile. Selected tiles: " + selectedTiles.size());
            }
            else
            {
                selectedTiles.add(clickedTile);
                saveEditedGroup();
                updatePanel();
                say("Selected tile. Selected tiles: " + selectedTiles.size());
            }

            return null;
        }
    };

    @Subscribe
    public void onGameTick(GameTick event)
    {
        registerMultiplayerPlayerIfReady();
        processPaintTick();
        processCountdownTick();
        if (!isMultiplayerParticipant())
        {
            processDisableTick();
        }
        processGameTick();
    }

    private void registerMultiplayerPlayerIfReady()
    {
        if (multiplayerClient == null)
        {
            return;
        }

        String playerName = currentPlayerNameOrNull();
        if (playerName == null || multiplayerClient.isRegisteredAs(playerName))
        {
            return;
        }

        int currentTick = client.getTickCount();
        if (currentTick - lastMultiplayerRegisterAttemptTick < 50)
        {
            return;
        }

        lastMultiplayerRegisterAttemptTick = currentTick;
        multiplayerClient.ensureRegistered(playerName);
    }

    private void processPaintTick()
    {
        if (!paintMode)
        {
            return;
        }

        if (client.getLocalPlayer() == null)
        {
            return;
        }

        WorldPoint currentTile = client.getLocalPlayer().getWorldLocation();

        if (currentTile == null)
        {
            return;
        }

        if (!paintedTiles.containsKey(currentTile))
        {
            paintedTiles.put(currentTile, randomColor());
            updatePanel();
        }
    }

    private void processCountdownTick()
    {
        if (mode != TileGameMode.COUNTDOWN)
        {
            return;
        }

        countdownTicksRemaining--;

        if (countdownTicksRemaining > 0)
        {
            showOverhead(String.valueOf(countdownTicksRemaining));
            updatePanel();
            broadcastMultiplayerStateIfHost();
            return;
        }

        mode = TileGameMode.LEVEL;
                if (isHardMode && !isSequenceModeEnabled())
                {
                    nonSeqDisablerTimer = 3;
                }
                lastProcessedTick = client.getTickCount();
        updatePanel();
        showOverhead("GO!");
        broadcastMultiplayerStateIfHost();
    }

    private boolean isWalkableAndVisible(WorldPoint candidate)
    {
        if (candidate == null)
        {
            return false;
        }

        WorldView worldView = client.getTopLevelWorldView();
        if (worldView == null)
        {
            return false;
        }

        LocalPoint lp = LocalPoint.fromWorld(worldView, candidate);
        if (lp == null)
        {
            return false;
        }

        // Must have a canvas polygon (visible / in line of sight)
        if (Perspective.getCanvasTilePoly(client, lp) == null)
        {
            return false;
        }

        Scene scene = worldView.getScene();
        if (scene == null)
        {
            return true; // if no scene info, fall back to visible check
        }

        Tile[][][] tiles = scene.getTiles();
        int plane = candidate.getPlane();
        if (tiles == null || plane < 0 || plane >= tiles.length || tiles[plane] == null)
        {
            return false;
        }

        int sceneX = lp.getSceneX();
        int sceneY = lp.getSceneY();
        if (sceneX < 0 || sceneX >= tiles[plane].length || tiles[plane][sceneX] == null ||
                sceneY < 0 || sceneY >= tiles[plane][sceneX].length)
        {
            return false;
        }

        Tile tile = tiles[plane][sceneX][sceneY];
        if (tile == null)
        {
            return false;
        }

        CollisionData[] collisionMaps = worldView.getCollisionMaps();
        if (collisionMaps == null || plane < 0 || plane >= collisionMaps.length || collisionMaps[plane] == null)
        {
            return true;
        }

        int[][] flags = collisionMaps[plane].getFlags();
        if (flags == null || sceneX >= flags.length || flags[sceneX] == null || sceneY >= flags[sceneX].length)
        {
            return true;
        }

        int movementBlockMask = CollisionDataFlag.BLOCK_MOVEMENT_FULL |
                CollisionDataFlag.BLOCK_MOVEMENT_OBJECT |
                CollisionDataFlag.BLOCK_MOVEMENT_FLOOR |
                CollisionDataFlag.BLOCK_MOVEMENT_FLOOR_DECORATION;

        return (flags[sceneX][sceneY] & movementBlockMask) == 0;
    }

    private WorldPoint getCurrentPlayerTile()
    {
        Player localPlayer = client.getLocalPlayer();
        if (localPlayer == null)
        {
            return null;
        }

        return localPlayer.getWorldLocation();
    }

    private boolean isDangerTileOccupied(WorldPoint tile)
    {
        return tile != null && (dangerTileCountdowns.containsKey(tile) || dangerTileActiveTimers.containsKey(tile));
    }

    private List<WorldPoint> getDangerSpawnCandidates(WorldPoint currentTile)
    {
        List<WorldPoint> candidates = new ArrayList<>();
        Map<WorldPoint, TileModifier> mods = tileModifiers.get(normalizeGroupName(activeGroupName));

        for (WorldPoint tile : activeLevelGroupTiles)
        {
            if (runColoredTiles.containsKey(tile)
                    || disabledTileTimers.containsKey(tile)
                    || dangerTileCountdowns.containsKey(tile)
                    || dangerTileActiveTimers.containsKey(tile)
                    || validSequenceTiles.contains(tile)
                    || sequenceHardBonusTiles.containsKey(tile)
                    || directionalTileDirections.containsKey(tile)
                    || (resetTile != null && resetTile.equals(tile))
                    || (mods != null && mods.containsKey(tile)))
            {
                continue;
            }

            int dist = Math.abs(tile.getX() - currentTile.getX()) + Math.abs(tile.getY() - currentTile.getY());
            if (dist <= DANGER_TILE_SPAWN_RADIUS)
            {
                candidates.add(tile);
            }
        }

        return candidates;
    }

    private void removeDangerTileToMakeRoom()
    {
        List<WorldPoint> dangerTiles = new ArrayList<>(dangerTileCountdowns.keySet());
        dangerTiles.addAll(dangerTileActiveTimers.keySet());

        if (dangerTiles.isEmpty())
        {
            return;
        }

        WorldPoint tile = dangerTiles.get(new java.util.Random().nextInt(dangerTiles.size()));
        dangerTileCountdowns.remove(tile);
        dangerTileActiveTimers.remove(tile);
    }

    private int getWalkStepCount(WorldPoint from, WorldPoint to)
    {
        if (from == null || to == null || from.getPlane() != to.getPlane())
        {
            return Integer.MAX_VALUE;
        }

        WorldView worldView = client.getTopLevelWorldView();
        if (worldView == null)
        {
            return Integer.MAX_VALUE;
        }

        LocalPoint fromLocal = LocalPoint.fromWorld(worldView, from);
        LocalPoint toLocal = LocalPoint.fromWorld(worldView, to);
        if (fromLocal == null || toLocal == null)
        {
            return Integer.MAX_VALUE;
        }

        Scene scene = worldView.getScene();
        CollisionData[] collisionMaps = worldView.getCollisionMaps();
        if (scene == null || collisionMaps == null)
        {
            return Integer.MAX_VALUE;
        }

        int plane = from.getPlane();
        if (plane < 0 || plane >= collisionMaps.length || collisionMaps[plane] == null)
        {
            return Integer.MAX_VALUE;
        }

        int[][] flags = collisionMaps[plane].getFlags();
        if (flags == null)
        {
            return Integer.MAX_VALUE;
        }

        int startX = fromLocal.getSceneX();
        int startY = fromLocal.getSceneY();
        int targetX = toLocal.getSceneX();
        int targetY = toLocal.getSceneY();

        if (!isTileWithinBounds(flags, startX, startY) || !isTileWithinBounds(flags, targetX, targetY))
        {
            return Integer.MAX_VALUE;
        }

        Deque<int[]> queue = new ArrayDeque<>();
        Map<Long, Integer> distances = new HashMap<>();
        long startKey = encodeScenePoint(startX, startY);
        queue.add(new int[] { startX, startY });
        distances.put(startKey, 0);

        while (!queue.isEmpty())
        {
            int[] current = queue.removeFirst();
            int x = current[0];
            int y = current[1];
            int distance = distances.get(encodeScenePoint(x, y));

            if (x == targetX && y == targetY)
            {
                return distance;
            }

            for (int dx = -1; dx <= 1; dx++)
            {
                for (int dy = -1; dy <= 1; dy++)
                {
                    if (dx == 0 && dy == 0)
                    {
                        continue;
                    }

                    int nextX = x + dx;
                    int nextY = y + dy;
                    if (!isTileWithinBounds(flags, nextX, nextY))
                    {
                        continue;
                    }

                    long nextKey = encodeScenePoint(nextX, nextY);
                    if (distances.containsKey(nextKey))
                    {
                        continue;
                    }

                    if (!canStep(flags, x, y, nextX, nextY))
                    {
                        continue;
                    }

                    distances.put(nextKey, distance + 1);
                    queue.addLast(new int[] { nextX, nextY });
                }
            }
        }

        return Integer.MAX_VALUE;
    }

    private boolean canStep(int[][] flags, int x, int y, int nextX, int nextY)
    {
        int currentFlags = flags[x][y];
        int nextFlags = flags[nextX][nextY];
        int dx = nextX - x;
        int dy = nextY - y;

        if (!isTileWalkable(flags, nextX, nextY))
        {
            return false;
        }

        if (dx == 1 && dy == 0)
        {
            return (currentFlags & CollisionDataFlag.BLOCK_MOVEMENT_EAST) == 0
                    && (nextFlags & CollisionDataFlag.BLOCK_MOVEMENT_WEST) == 0;
        }
        if (dx == -1 && dy == 0)
        {
            return (currentFlags & CollisionDataFlag.BLOCK_MOVEMENT_WEST) == 0
                    && (nextFlags & CollisionDataFlag.BLOCK_MOVEMENT_EAST) == 0;
        }
        if (dx == 0 && dy == 1)
        {
            return (currentFlags & CollisionDataFlag.BLOCK_MOVEMENT_NORTH) == 0
                    && (nextFlags & CollisionDataFlag.BLOCK_MOVEMENT_SOUTH) == 0;
        }
        if (dx == 0 && dy == -1)
        {
            return (currentFlags & CollisionDataFlag.BLOCK_MOVEMENT_SOUTH) == 0
                    && (nextFlags & CollisionDataFlag.BLOCK_MOVEMENT_NORTH) == 0;
        }
        if (dx == 1 && dy == 1)
        {
            return (currentFlags & (CollisionDataFlag.BLOCK_MOVEMENT_NORTH | CollisionDataFlag.BLOCK_MOVEMENT_EAST | CollisionDataFlag.BLOCK_MOVEMENT_NORTH_EAST)) == 0
                    && (nextFlags & (CollisionDataFlag.BLOCK_MOVEMENT_SOUTH | CollisionDataFlag.BLOCK_MOVEMENT_WEST | CollisionDataFlag.BLOCK_MOVEMENT_SOUTH_WEST)) == 0;
        }
        if (dx == -1 && dy == 1)
        {
            return (currentFlags & (CollisionDataFlag.BLOCK_MOVEMENT_NORTH | CollisionDataFlag.BLOCK_MOVEMENT_WEST | CollisionDataFlag.BLOCK_MOVEMENT_NORTH_WEST)) == 0
                    && (nextFlags & (CollisionDataFlag.BLOCK_MOVEMENT_SOUTH | CollisionDataFlag.BLOCK_MOVEMENT_EAST | CollisionDataFlag.BLOCK_MOVEMENT_SOUTH_EAST)) == 0;
        }
        if (dx == 1 && dy == -1)
        {
            return (currentFlags & (CollisionDataFlag.BLOCK_MOVEMENT_SOUTH | CollisionDataFlag.BLOCK_MOVEMENT_EAST | CollisionDataFlag.BLOCK_MOVEMENT_SOUTH_EAST)) == 0
                    && (nextFlags & (CollisionDataFlag.BLOCK_MOVEMENT_NORTH | CollisionDataFlag.BLOCK_MOVEMENT_WEST | CollisionDataFlag.BLOCK_MOVEMENT_NORTH_WEST)) == 0;
        }
        if (dx == -1 && dy == -1)
        {
            return (currentFlags & (CollisionDataFlag.BLOCK_MOVEMENT_SOUTH | CollisionDataFlag.BLOCK_MOVEMENT_WEST | CollisionDataFlag.BLOCK_MOVEMENT_SOUTH_WEST)) == 0
                    && (nextFlags & (CollisionDataFlag.BLOCK_MOVEMENT_NORTH | CollisionDataFlag.BLOCK_MOVEMENT_EAST | CollisionDataFlag.BLOCK_MOVEMENT_NORTH_EAST)) == 0;
        }

        return false;
    }

    private boolean isTileWalkable(int[][] flags, int x, int y)
    {
        if (!isTileWithinBounds(flags, x, y))
        {
            return false;
        }

        return (flags[x][y] & (CollisionDataFlag.BLOCK_MOVEMENT_FULL
                | CollisionDataFlag.BLOCK_MOVEMENT_OBJECT
                | CollisionDataFlag.BLOCK_MOVEMENT_FLOOR
                | CollisionDataFlag.BLOCK_MOVEMENT_FLOOR_DECORATION)) == 0;
    }

    private boolean isTileWithinBounds(int[][] flags, int x, int y)
    {
        return x >= 0 && x < flags.length && flags[x] != null && y >= 0 && y < flags[x].length;
    }

    private long encodeScenePoint(int x, int y)
    {
        return (((long) x) << 32) | (y & 0xffffffffL);
    }

    private boolean hasLineOfSight(WorldPoint from, WorldPoint to)
    {
        WorldView worldView = client.getTopLevelWorldView();
        if (worldView == null)
        {
            return false;
        }

        LocalPoint fromLocal = LocalPoint.fromWorld(worldView, from);
        LocalPoint toLocal = LocalPoint.fromWorld(worldView, to);
        if (fromLocal == null || toLocal == null)
        {
            return false;
        }

        CollisionData[] collisionMaps = worldView.getCollisionMaps();
        int plane = from.getPlane();
        if (to.getPlane() != plane || collisionMaps == null || plane < 0 || plane >= collisionMaps.length || collisionMaps[plane] == null)
        {
            return false;
        }

        int[][] flags = collisionMaps[plane].getFlags();
        if (flags == null)
        {
            return true;
        }

        int x0 = fromLocal.getSceneX();
        int y0 = fromLocal.getSceneY();
        int x1 = toLocal.getSceneX();
        int y1 = toLocal.getSceneY();
        int dx = Math.abs(x1 - x0);
        int dy = Math.abs(y1 - y0);
        int sx = x0 < x1 ? 1 : -1;
        int sy = y0 < y1 ? 1 : -1;
        int err = dx - dy;

        int x = x0;
        int y = y0;
        while (true)
        {
            if (isLineOfSightBlocked(flags, x, y))
            {
                return false;
            }

            if (x == x1 && y == y1)
            {
                return true;
            }

            int e2 = 2 * err;
            int nextX = x;
            int nextY = y;
            if (e2 > -dy)
            {
                err -= dy;
                nextX += sx;
            }
            if (e2 < dx)
            {
                err += dx;
                nextY += sy;
            }

            if (isLineOfSightBlockedBetween(flags, x, y, nextX, nextY))
            {
                return false;
            }

            x = nextX;
            y = nextY;
        }
    }

    private boolean isLineOfSightBlocked(int[][] flags, int x, int y)
    {
        if (x < 0 || x >= flags.length || flags[x] == null || y < 0 || y >= flags[x].length)
        {
            return true;
        }

        return (flags[x][y] & CollisionDataFlag.BLOCK_LINE_OF_SIGHT_FULL) != 0;
    }

    private boolean isLineOfSightBlockedBetween(int[][] flags, int x, int y, int nextX, int nextY)
    {
        if (isLineOfSightBlocked(flags, nextX, nextY))
        {
            return true;
        }

        int currentFlags = flags[x][y];
        int nextFlags = flags[nextX][nextY];

        if (nextX > x && ((currentFlags & CollisionDataFlag.BLOCK_LINE_OF_SIGHT_EAST) != 0 ||
                (nextFlags & CollisionDataFlag.BLOCK_LINE_OF_SIGHT_WEST) != 0))
        {
            return true;
        }
        if (nextX < x && ((currentFlags & CollisionDataFlag.BLOCK_LINE_OF_SIGHT_WEST) != 0 ||
                (nextFlags & CollisionDataFlag.BLOCK_LINE_OF_SIGHT_EAST) != 0))
        {
            return true;
        }
        if (nextY > y && ((currentFlags & CollisionDataFlag.BLOCK_LINE_OF_SIGHT_NORTH) != 0 ||
                (nextFlags & CollisionDataFlag.BLOCK_LINE_OF_SIGHT_SOUTH) != 0))
        {
            return true;
        }
        if (nextY < y && ((currentFlags & CollisionDataFlag.BLOCK_LINE_OF_SIGHT_SOUTH) != 0 ||
                (nextFlags & CollisionDataFlag.BLOCK_LINE_OF_SIGHT_NORTH) != 0))
        {
            return true;
        }

        return false;
    }

    private WorldPoint findWalkableResetCandidate(WorldPoint center, int maxRadius)
    {
        java.util.Random random = new java.util.Random();
        List<WorldPoint> candidates = new ArrayList<>();

        for (int r = 1; r <= maxRadius; r++)
        {
            for (int dx = -r; dx <= r; dx++)
            {
                int dy = r - Math.abs(dx);
                int[] dys = new int[] { dy, -dy };
                for (int ddy : dys)
                {
                    WorldPoint candidate = new WorldPoint(center.getX() + dx, center.getY() + ddy, center.getPlane());
                    if (candidate.equals(center))
                    {
                        continue;
                    }
                    if (runColoredTiles.containsKey(candidate) || disabledTileTimers.containsKey(candidate))
                    {
                        continue;
                    }
                    if (dangerTileCountdowns.containsKey(candidate) || dangerTileActiveTimers.containsKey(candidate))
                    {
                        continue;
                    }
                    if (!isWalkableAndVisible(candidate))
                    {
                        continue;
                    }
                    if (!hasLineOfSight(center, candidate))
                    {
                        continue;
                    }
                    candidates.add(candidate);
                }
            }
            if (!candidates.isEmpty())
            {
                return candidates.get(random.nextInt(candidates.size()));
            }
        }

        return null;
    }

    private void processDisableTick()
    {
            // Handles disabling tiles when Add Disablers is enabled.
            if (mode != TileGameMode.LEVEL)
            {
                return;
            }

            if (!isAddDisablersEnabled())
            {
                return;
            }

            if (client.getLocalPlayer() == null)
            {
                return;
            }

            WorldPoint currentTile = client.getLocalPlayer().getWorldLocation();
            if (currentTile == null)
            {
                return;
            }

            // Hard mode: disablers are temporary — re-enable after countdown
                        if (isHardMode)
                        {
                            if (isSequenceModeEnabled())
                            {
                                // --- Sequence mode: existing countdown-based logic ---
                                if (disablerCountdown > 0)
                                {
                                    disablerCountdown--;
                                    if (disablerCountdown <= 0)
                                    {
                                    // Re-enable disablers and spawn new clear tile
                                        for (WorldPoint tile : validSequenceTiles)
                                        {
                                            if (!runColoredTiles.containsKey(tile))
                                            {
                                                disabledTileTimers.put(tile, 1);
                                            }
                                        }

                                        if (!disabledTileTimers.isEmpty())
                                        {
                                            resetTile = findWalkableResetCandidate(currentTile, 4);
                                            // Clear the countdown — it will be set again when clear tile is stepped on
                                            disablerCountdown = 0;
                                        }
                                    }
                                    return;
                                }

                                // If there are active disablers and a clear tile, check if player stepped on it
                                if (!disabledTileTimers.isEmpty())
                                {
                                    if (resetTile != null && resetTile.equals(currentTile))
                                    {
                                        // Player stepped on clear tile — temporarily clear disablers and start countdown
                                        clearDisablersFromReset(currentTile);
                                        return;
                                    }

                                    // Clear tile exists but player hasn't stepped on it yet — ensure one exists
                                    if (resetTile == null)
                                    {
                                        resetTile = findWalkableResetCandidate(currentTile, 4);
                                    }

                                    return;
                                }

                                // No active disablers — spawn at 10% chance per tick (all valid tiles)
                                if (new java.util.Random().nextDouble() < 0.10)
                                {
                                    for (WorldPoint tile : validSequenceTiles)
                                    {
                                        if (!runColoredTiles.containsKey(tile) && !disabledTileTimers.containsKey(tile))
                                        {
                                            disabledTileTimers.put(tile, 1);
                                        }
                                    }

                                    if (!disabledTileTimers.isEmpty())
                                    {
                                        resetTile = findWalkableResetCandidate(currentTile, 4);
                                    }
                                }

                                return;
                            }
                            else
                            {
                                // --- Non-sequence hard mode: timed disabler spawning ---

                                // Tick down the spawn timer
                                nonSeqDisablerTimer--;
                                if (nonSeqDisablerTimer <= 0)
                                {
                                    // Spawn a disabler on a random uncolored, non-disabled tile
                                    List<WorldPoint> uncolored = new ArrayList<>();
                                    for (WorldPoint tile : activeLevelGroupTiles)
                                    {
                                        if (!runColoredTiles.containsKey(tile) && !disabledTileTimers.containsKey(tile))
                                        {
                                            uncolored.add(tile);
                                        }
                                    }
                                    if (!uncolored.isEmpty())
                                    {
                                        disabledTileTimers.put(uncolored.get(new java.util.Random().nextInt(uncolored.size())), 1);
                                    }

                                    if (!disabledTileTimers.isEmpty() && resetTile == null)
                                    {
                                        resetTile = findWalkableResetCandidate(currentTile, 4);
                                    }

                                    nonSeqDisablerTimer = 2;
                                }

                                // Check if player stepped on the clear tile
                                if (!disabledTileTimers.isEmpty() && resetTile != null && resetTile.equals(currentTile))
                                {
                                    clearDisablersFromReset(currentTile);
                                    return;
                                }
                                else if (!disabledTileTimers.isEmpty() && resetTile == null)
                                {
                                    resetTile = findWalkableResetCandidate(currentTile, 4);
                                }

                                return;
                            }
                        }

            // --- Non-hard mode behavior below ---
            if (!disabledTileTimers.isEmpty() && resetTile == null)
            {
                WorldPoint resetPoint = findWalkableResetCandidate(currentTile, 4);
                if (resetPoint != null)
                {
                    resetDisabledTile = disabledTileTimers.keySet().iterator().next();
                    resetTile = resetPoint;
                }
            }

            java.util.Random random = new java.util.Random();
            // ~15% chance per tick to spawn a new disabled tile
            if (random.nextDouble() >= 0.15)
            {
                return;
            }

            // If in sequence mode, disable all valid next tiles
                    if (isSequenceModeEnabled())
                    {
                        // Do not allow more than one disabler event active at a time
                        if (!disabledTileTimers.isEmpty())
                        {
                            if (resetTile == null)
                            {
                                WorldPoint resetPoint = findWalkableResetCandidate(currentTile, 4);
                                if (resetPoint != null)
                                {
                                    resetTile = resetPoint;
                                }
                            }

                            return;
                        }

                        // Disable all valid next tiles that aren't already colored or disabled
                        for (WorldPoint tile : validSequenceTiles)
                        {
                            if (!runColoredTiles.containsKey(tile) && !disabledTileTimers.containsKey(tile))
                            {
                                disabledTileTimers.put(tile, 1);
                            }
                        }

                        if (!disabledTileTimers.isEmpty())
                        {
                            // The reset tile lets the player re-enable all disabled tiles at once
                            resetTile = findWalkableResetCandidate(currentTile, 4);
                        }

                        return;
                    }

            // Otherwise (not sequence mode or falling back), disable a random nearby tile within Manhattan distance 3 of the player
            List<WorldPoint> candidates = new ArrayList<>();
            for (WorldPoint t : activeLevelGroupTiles)
            {
                if (runColoredTiles.containsKey(t) || disabledTileTimers.containsKey(t) || t.equals(currentTile))
                {
                    continue;
                }

                int dist = Math.abs(t.getX() - currentTile.getX()) + Math.abs(t.getY() - currentTile.getY());
                if (dist <= 3 && isWalkableAndVisible(t))
                {
                    candidates.add(t);
                }
            }

            // Fallback: if no visible/walkable candidates, allow nearby tiles ignoring visibility (but still not colored/disabled)
            if (candidates.isEmpty())
            {
                for (WorldPoint t : activeLevelGroupTiles)
                {
                    if (runColoredTiles.containsKey(t) || disabledTileTimers.containsKey(t) || t.equals(currentTile))
                    {
                        continue;
                    }

                    int dist = Math.abs(t.getX() - currentTile.getX()) + Math.abs(t.getY() - currentTile.getY());
                    if (dist <= 3)
                    {
                        candidates.add(t);
                    }
                }
            }

            if (!candidates.isEmpty())
            {
                WorldPoint tile = candidates.get(random.nextInt(candidates.size()));
                disabledTileTimers.put(tile, 1);

                if (resetDisabledTile == null)
                {
                    WorldPoint resetPoint = findWalkableResetCandidate(currentTile, 4);
                    if (resetPoint != null)
                    {
                        resetDisabledTile = tile;
                        resetTile = resetPoint;
                    }
                }
            }
        }

    // Clears active disablers as if the reset (blue) tile at fromTile was stepped on,
    // applying the mode-appropriate follow-up timers.
    private void clearDisablersFromReset(WorldPoint fromTile)
    {
        disabledTileTimers.clear();
        resetDisabledTile = null;
        resetTile = null;

        if (!isHardMode)
        {
            return;
        }

        if (isSequenceModeEnabled())
        {
            sequenceShrinkDelay = 1;

            // Calculate ticks to reach closest valid tile from the reset tile
            int minDist = Integer.MAX_VALUE;
            for (WorldPoint tile : validSequenceTiles)
            {
                if (!runColoredTiles.containsKey(tile))
                {
                    int dist = Math.abs(tile.getX() - fromTile.getX()) + Math.abs(tile.getY() - fromTile.getY());
                    if (dist < minDist)
                    {
                        minDist = dist;
                    }
                }
            }

            if (minDist == Integer.MAX_VALUE)
            {
                minDist = 1;
            }
            disablerCountdown = minDist + 1;
        }
        else
        {
            nonSeqDisablerTimer = 3;
        }
    }

    // Participant-side: ask the host to clear the disablers after stepping on the reset tile
    private void requestMultiplayerDisablerClear(WorldPoint tile)
    {
        if (disabledTileTimers.isEmpty() || tile.equals(lastDisablerClearRequestTile))
        {
            return;
        }

        lastDisablerClearRequestTile = tile;
        TileGameMultiplayerMessage message = new TileGameMultiplayerMessage();
        message.type = "clear_disablers";
        message.roomId = multiplayerRoomId;
        message.player = currentPlayerName();
        message.tile = toMultiplayerTile(tile);
        sendMultiplayerMessage(message);
    }

    void processGameTick()
    {
        if (mode != TileGameMode.LEVEL)
        {
            return;
        }

        if (client.getLocalPlayer() == null)
        {
            return;
        }

        WorldPoint currentTile = client.getLocalPlayer().getWorldLocation();

        if (currentTile == null)
        {
            return;
        }

        int currentTick = client.getTickCount();

        if (currentTick == lastProcessedTick)
        {
            return;
        }

        lastProcessedTick = currentTick;
        totalRunTicks++;

        boolean multiplayerParticipant = isMultiplayerParticipant();

        if (!multiplayerParticipant && isHardMode && isSequenceModeEnabled())
        {
            processSequenceHardBonusTimers();
        }

        boolean tileIsInLevelGroup = activeLevelGroupTiles.contains(currentTile);
        boolean tileAlreadyColored = runColoredTiles.containsKey(currentTile);
        boolean tileDisabled = disabledTileTimers.containsKey(currentTile);

        String groupName = normalizeGroupName(activeGroupName);
        Map<WorldPoint, TileModifier> mods = tileModifiers.get(groupName);
        boolean isBonusTile = mods != null && mods.get(currentTile) == TileModifier.BONUS && !usedBonusTiles.contains(currentTile);
        boolean isSequenceHardBonusTile = isHardMode && isSequenceModeEnabled() && sequenceHardBonusTiles.containsKey(currentTile);
        boolean directionalTileActive = multiplayerParticipant
                ? processSyncedDirectionalTile(currentTile)
                : processDirectionalTiles(currentTile);

        // If the player stepped on the reset tile, re-enable all currently-disabled tiles
                // (In hard mode this is handled by processDisableTick for the countdown mechanic)
                if (resetTile != null && resetTile.equals(currentTile))
                {
                    if (multiplayerParticipant)
                    {
                        // Participants ask the host to clear disablers for everyone
                        requestMultiplayerDisablerClear(currentTile);
                    }
                    else if (!isHardMode)
                    {
                        disabledTileTimers.clear();
                        resetDisabledTile = null;
                        resetTile = null;
                    }
                }

        if (!tileAlreadyColored && !tileDisabled && !directionalTileActive)
        {
            // Bonus tile: can be activated even if not in level group
            if (isBonusTile)
            {
                // Only color the tile the player stepped on if it is part of the active level group.
                if (tileIsInLevelGroup)
                {
                    claimSequenceTile(currentTile);
                }

                // Auto-paint a single unpainted, non-disabled level tile (bonus effect), then deactivate this bonus tile
                    WorldPoint chosen = null;

                    if (isSequenceModeEnabled())
                    {
                        // Prefer a tile from the valid set if available
                        for (WorldPoint tile : validSequenceTiles)
                        {
                            if (!runColoredTiles.containsKey(tile) && !tile.equals(currentTile))
                            {
                                chosen = tile;
                                break;
                            }
                        }
                    }

                    if (chosen == null)
                    {
                        List<WorldPoint> candidates = new ArrayList<>();
                        for (WorldPoint tile : activeLevelGroupTiles)
                        {
                            if (!runColoredTiles.containsKey(tile)
                                    && !disabledTileTimers.containsKey(tile)
                                    && !tile.equals(currentTile)
                                    && !isDirectionalTileOccupied(tile))
                            {
                                candidates.add(tile);
                            }
                        }

                        if (!candidates.isEmpty())
                        {
                            chosen = candidates.get(new java.util.Random().nextInt(candidates.size()));
                        }
                    }

                    if (chosen != null)
                    {
                        claimSequenceTile(chosen);
                    }

                    // Mark this bonus tile as used for this run so it no longer fires and its border disappears
                    usedBonusTiles.add(currentTile);
                }
            // Regular level tile
            else if (isSequenceHardBonusTile)
            {
                applySequenceHardBonus(currentTile);
            }
            else if (tileIsInLevelGroup && (!isSequenceModeEnabled() || validSequenceTiles.contains(currentTile)))
            {
                claimSequenceTile(currentTile);

                // Non-sequence hard mode: if this tile had a power-up, color extra tiles
                if (isHardMode && !isSequenceModeEnabled() && powerUpTiles.containsKey(currentTile))
                {
                    int extra = powerUpTiles.remove(currentTile) - 1;
                    powerUpTimers.remove(currentTile);
                    if (extra > 0)
                    {
                        List<WorldPoint> uncolored = new ArrayList<>();
                        for (WorldPoint t : activeLevelGroupTiles)
                        {
                            if (!runColoredTiles.containsKey(t) && !t.equals(currentTile))
                            {
                                uncolored.add(t);
                            }
                        }
                        Collections.shuffle(uncolored, new java.util.Random(System.nanoTime()));
                        int toColor = Math.min(extra, uncolored.size());
                        for (int i = 0; i < toColor; i++)
                        {
                            claimSequenceTile(uncolored.get(i));
                        }
                    }
                }
            }
        }

        if (multiplayerParticipant)
        {
            processSyncedDangerTile(currentTile);
        }
        else
        {
            processDangerTiles(currentTile);
        }

        visitedThisRun.add(currentTile);
        lastTrueTile = currentTile;
        updatePanel();

        // Hard mode tick effects
        if (!multiplayerParticipant && isHardMode)
        {
            if (isSequenceModeEnabled())
            {
                        // Pause shrinking while disablers are active
                        if (disabledTileTimers.isEmpty())
                {
                            // Wait one tick after refreshing before shrinking starts
                            if (sequenceShrinkDelay > 0)
                            {
                                sequenceShrinkDelay--;
                            }
                            else if (!validSequenceTiles.isEmpty())
                            {
                                            // Decrement all timers and remove expired tiles
                                            List<WorldPoint> expired = new ArrayList<>();
                                            for (Map.Entry<WorldPoint, Integer> entry : sequenceTileTimers.entrySet())
                                            {
                                                int remaining = entry.getValue() - 1;
                                                entry.setValue(remaining);
                                                if (remaining <= 0)
                                                {
                                                    expired.add(entry.getKey());
                                                }
                                            }
                                            for (WorldPoint tile : expired)
                                            {
                                                validSequenceTiles.remove(tile);
                                                sequenceTileTimers.remove(tile);
                                            }
                                            if (validSequenceTiles.isEmpty())
                                            {
                                                selectNextValidTiles();
                                            }
                                        }
                                    }
                                }
            else
            {
                // Count down power-up timers; expired ones get removed
                List<WorldPoint> expired = new ArrayList<>();
                for (Map.Entry<WorldPoint, Integer> entry : powerUpTimers.entrySet())
                {
                    int remaining = entry.getValue() - 1;
                    if (remaining <= 0)
                    {
                        expired.add(entry.getKey());
                    }
                    else
                    {
                        entry.setValue(remaining);
                    }
                }
                for (WorldPoint wp : expired)
                {
                    powerUpTiles.remove(wp);
                    powerUpTimers.remove(wp);
                }

                // 25% chance per tick to spawn a power-up on a random uncolored tile within 4 tiles
                if (new java.util.Random().nextDouble() < 0.25)
                {
                    List<WorldPoint> candidates = new ArrayList<>();
                    for (WorldPoint t : activeLevelGroupTiles)
                    {
                        if (!runColoredTiles.containsKey(t) && !powerUpTiles.containsKey(t))
                        {
                            int dist = Math.abs(t.getX() - currentTile.getX()) + Math.abs(t.getY() - currentTile.getY());
                            if (dist <= 4)
                            {
                                candidates.add(t);
                            }
                        }
                    }
                    if (!candidates.isEmpty())
                    {
                        WorldPoint target = candidates.get(new java.util.Random().nextInt(candidates.size()));
                        int number = 2 + new java.util.Random().nextInt(3); // 2-4
                        powerUpTiles.put(target, number);
                        powerUpTimers.put(target, 3);
                    }
                }
            }
        }

        if (mode == TileGameMode.LEVEL
                && !activeLevelGroupTiles.isEmpty()
                && !runColoredTiles.keySet().containsAll(activeLevelGroupTiles))
        {
            if (!multiplayerParticipant)
            {
                maybeSpawnSequenceHardBonusTile(currentTile);
            }
        }

        if (mode == TileGameMode.LEVEL && !activeLevelGroupTiles.isEmpty() && runColoredTiles.keySet().containsAll(activeLevelGroupTiles))
        {
            completeLevel();
            return;
        }

        broadcastMultiplayerStateIfHost();
    }

    private boolean isNextSequenceTile(WorldPoint tile)
    {
        return validSequenceTiles.contains(tile);
    }

    private void completeLevel()
    {
        TileGameResult.ScoreMode scoreMode = isHardMode
                ? (isSequenceModeEnabled() ? TileGameResult.ScoreMode.SEQUENCE_HARDMODE : TileGameResult.ScoreMode.NON_SEQUENCE_HARDMODE)
                : TileGameResult.ScoreMode.NORMAL;
        TileGameResult result = new TileGameResult(totalRunTicks, scoreMode);
        // remember last run time for display in the panel
        lastRunTicks = totalRunTicks;
        lastRunTicks = totalRunTicks;

        ArrayList<TileGameResult> board = highscores.computeIfAbsent(activeGroupName, k -> new ArrayList<>());
        board.add(result);
        board.sort(scoreboardComparator());

        mode = TileGameMode.DONE;
        savePersistentData();
        updatePanel();

        if (multiplayerActive && !multiplayerRoomId.isEmpty())
        {
            TileGameMultiplayerMessage message = new TileGameMultiplayerMessage();
            message.type = "finish";
            message.roomId = multiplayerRoomId;
            message.player = currentPlayerName();
            message.totalTicks = totalRunTicks;
            sendMultiplayerMessage(message);
        }

        // Clear colored and active tiles when the game finishes
        runColoredTiles.clear();
        activeLevelGroupTiles.clear();
        visitedThisRun.clear();
        directionalTileDirections.clear();

        showOverhead("Level complete!");
    }

    private void resetRunState()
    {
        runColoredTiles.clear();
        activeLevelGroupTiles.clear();
        visitedThisRun.clear();
        disabledTileTimers.clear();
        resetDisabledTile = null;
        resetTile = null;
        lastDisablerClearRequestTile = null;
        usedBonusTiles.clear();
        dangerTileCountdowns.clear();
        dangerTileActiveTimers.clear();
        directionalTileDirections.clear();
                sequenceOrder.clear();
        validSequenceTiles.clear();
        sequenceHardBonusTiles.clear();
        sequenceHardBonusTimers.clear();
                sequenceTileTimers.clear();
                currentSequenceNumber = 0;
                isHardMode = false;
                isSequenceMode = false;
                isAddDisablersMode = false;
                isDangerTilesMode = false;
                isDirectionalTilesMode = false;
                disablerCountdown = 0;
                sequenceShrinkDelay = 0;
                                nonSeqDisablerTimer = 3;
                powerUpTiles.clear();
                powerUpTimers.clear();
                totalRunTicks = 0;
                lastProcessedTick = -1;
                countdownTicksRemaining = 0;
                lastTrueTile = null;
                activeGroupName = "";
                updatePanel();
        }

    private void hideViewedGroups()
    {
        if (viewedGroups.isEmpty())
        {
            return;
        }

        viewedGroups.clear();
        updatePanelLists();
    }

    private WorldPoint getTileAtCanvasPoint(java.awt.Point mousePoint)
    {
        WorldView worldView = client.getTopLevelWorldView();
        if (worldView == null)
        {
            return null;
        }

        Scene scene = worldView.getScene();

        if (scene == null)
        {
            return null;
        }

        Tile[][][] tiles = scene.getTiles();
        int plane = worldView.getPlane();

        if (tiles == null || plane < 0 || plane >= tiles.length || tiles[plane] == null)
        {
            return null;
        }

        for (int x = 0; x < tiles[plane].length; x++)
        {
            if (tiles[plane][x] == null)
            {
                continue;
            }

            for (int y = 0; y < tiles[plane][x].length; y++)
            {
                Tile tile = tiles[plane][x][y];

                if (tile == null)
                {
                    continue;
                }

                LocalPoint localPoint = tile.getLocalLocation();

                if (localPoint == null)
                {
                    continue;
                }

                Polygon poly = Perspective.getCanvasTilePoly(client, localPoint);

                if (poly != null && poly.contains(mousePoint))
                {
                    return WorldPoint.fromLocal(client, localPoint);
                }
            }
        }

        return null;
    }

    private Color randomColor()
    {
        if (config.paintMode() == TileGameConfig.PaintMode.STATIC)
        {
            return config.defaultColor();
        }

        return new Color(
                (float) Math.random(),
                (float) Math.random(),
                (float) Math.random(),
                1.0f
        );
    }

    private void playPaintSound()
    {
        // Sound removed per user request.
    }

    private String normalizeGroupName(String raw)
    {
        if (raw == null)
        {
            return "";
        }

        return raw.trim().toLowerCase(Locale.ROOT);
    }

    private void say(String message)
    {
        clientThread.invokeLater(() ->
                client.addChatMessage(
                        ChatMessageType.GAMEMESSAGE,
                        "",
                        message,
                        null,
                        false
                )
        );
    }

    private void showHelpOverhead(String message)
    {
        showOverhead(message + " Open Help for details.");
    }

    private void showOverhead(String message)
    {
        clientThread.invokeLater(() ->
        {
            Player player = client.getLocalPlayer();

            if (player == null)
            {
                client.addChatMessage(ChatMessageType.GAMEMESSAGE, "", message, null, false);
                return;
            }

            player.setOverheadText(message);
            player.setOverheadCycle(OVERHEAD_MESSAGE_CYCLES);
        });
    }

    private void updatePanel()
    {
        if (panel != null)
        {
            panel.refresh();
        }
    }

    private void saveEditedGroup()
    {
        if (mode == TileGameMode.EDIT && !activeGroupName.isEmpty())
        {
            groups.put(activeGroupName, new HashSet<>(selectedTiles));
            if (panel != null)
            {
                panel.refresh();
                panel.showGroups(groups);
            }
        }

        String groupName = activeGroupName.isEmpty() ? normalizeGroupName("unsaved") : normalizeGroupName(activeGroupName);
        Map<WorldPoint, TileModifier> mods = tileModifiers.get(groupName);

        if (mods == null || mods.isEmpty())
        {
            tileModifiers.remove(groupName);
        }

        // Persist modifier changes immediately so they survive client restarts
        savePersistentData();
        updatePanel();
        updatePanelLists();
    }

    void showHowToPlay()
    {
        SwingUtilities.invokeLater(() ->
        {
            JTextArea text = new JTextArea(
                    "Tile Game is a tiny tile-racing trainer with a silly hat and a clipboard.\n\n" +
                            "How a run works:\n" +
                            "Pick a saved level, press play, wait for 3-2-1-GO, then run over every black target tile. A tile turns colorful when you claim it. Try to claim a new target tile every tick without doubling back.\n\n" +
                            "Grades:\n" +
                            "Every completed run gets S, A, B, C, or D. The score starts at 100, loses points for the percentage of ticks where you did not claim a new target tile, and loses much more for revisiting tiles. Revisits are intentionally spicy: each revisit costs 2.5x as much as the same percentage of missed ticks.\n\n" +
                            "Top buttons:\n" +
                            "Help opens this guide. Export copies a selected level as JSON. Scores shows every recorded completion for each level, including mode and score. Import reads level JSON from your clipboard. Multiplayer opens an invite menu for online friends, sends the selected level to the local websocket server, and shows queued Invite Pending buttons for incoming games.\n\n" +
                            "Current Game card:\n" +
                            "Mode shows whether you are idle, choosing tiles, editing, counting down, running, or finished. Group is the active level. Ticks is elapsed game ticks in the current run. Missed counts ticks without a new target tile. Revisits counts doubled-back tiles. Grade previews the current run grade once a run has started. Levels is your saved level count. The restart button reruns the active level; stop clears the current run or selection.\n\n" +
                            "Levels card:\n" +
                            "+ starts a new level selection. While choosing or editing, left-click tiles to add/remove them, then press save to name or update the level. Hold Ctrl while left-clicking to walk to a tile instead of marking it. Each level row has view/hide, play, edit, and delete buttons. The Sequence Mode checkbox marks 4 valid tiles using 2/4/6/8-step walk-distance bands — closer tiles get shorter timers, then color one to reveal 4 new options. In sequenced hardmode, a pink BONUS tile can briefly appear on an unmarked tile within 4 tiles of the player; step on it to color extra tiles and advance the sequence. Directional Tiles can spawn green direction labels on unmarked tiles; step onto them from the marked side to clear them. Danger Tiles can spawn yellow countdown tiles in batches of 3 within 10 tiles of the player, while leaving at least 4 safe tiles; when one reaches 0 and is stepped on, it uncolors a random colored tile. View paints every tile in that level black so you can inspect the route before running it."
            );
            text.setEditable(false);
            text.setLineWrap(true);
            text.setWrapStyleWord(true);
            text.setOpaque(false);
            text.setForeground(Color.WHITE);
            text.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));

            JPanel panel = new JPanel(new java.awt.BorderLayout());
            panel.setBackground(new Color(36, 30, 64));
            panel.setBorder(BorderFactory.createLineBorder(new Color(255, 208, 79), 2));
            JScrollPane scrollPane = new JScrollPane(text);
            scrollPane.setPreferredSize(new Dimension(520, 360));
            panel.add(scrollPane, java.awt.BorderLayout.CENTER);

            JOptionPane.showMessageDialog(null, panel, "Tile Game: How to Play", JOptionPane.PLAIN_MESSAGE);
        });
    }

    private TileGameMultiplayerLevel createMultiplayerLevelSnapshot(String groupName)
    {
        String normalized = normalizeGroupName(groupName);
        TileGameMultiplayerLevel level = new TileGameMultiplayerLevel();
        level.name = normalized;
        level.creator = currentPlayerName();
        level.sequenceMode = pendingSequenceMode;
        level.addDisablers = pendingHardMode || pendingAddDisablers;
        level.dangerTiles = pendingHardMode || pendingDangerTiles;
        level.directionalTiles = pendingHardMode || pendingDirectionalTiles;
        level.hardMode = pendingHardMode;
        level.tiles = toMultiplayerTiles(groups.getOrDefault(normalized, Collections.emptySet()));
        level.modifiers = toMultiplayerModifierTiles(tileModifiers.getOrDefault(normalized, Collections.emptyMap()));
        return level;
    }

    private void importMultiplayerLevel(TileGameMultiplayerLevel level)
    {
        if (level == null || level.name == null || level.tiles == null || level.tiles.isEmpty())
        {
            showHelpOverhead("The multiplayer invite did not contain a playable level.");
            return;
        }

        String groupName = normalizeGroupName(level.name);
        hideViewedGroups();
        groups.put(groupName, fromMultiplayerTiles(level.tiles));
        highscores.remove(groupName);
        pendingSequenceMode = level.sequenceMode;
        pendingAddDisablers = level.addDisablers || level.hardMode;
        pendingDangerTiles = level.dangerTiles || level.hardMode;
        pendingDirectionalTiles = level.directionalTiles || level.hardMode;
        pendingHardMode = level.hardMode;
        tileModifiers.put(groupName, fromMultiplayerModifierTiles(level.modifiers));
        activeGroupName = groupName;
        savePersistentData();
    }

    private void setMultiplayerModifierSnapshot(TileGameMultiplayerLevel level)
    {
        if (level == null)
        {
            clearMultiplayerModifierSnapshot();
            return;
        }

        setMultiplayerModifierSnapshot(
                level.sequenceMode,
                level.addDisablers,
                level.dangerTiles,
                level.directionalTiles,
                level.hardMode
        );
    }

    private void setMultiplayerModifierSnapshot(
            boolean sequenceMode,
            boolean addDisablers,
            boolean dangerTiles,
            boolean directionalTiles,
            boolean hardMode)
    {
        multiplayerHardMode = hardMode;
        multiplayerSequenceMode = sequenceMode;
        multiplayerAddDisablers = hardMode || addDisablers;
        multiplayerDangerTiles = hardMode || dangerTiles;
        multiplayerDirectionalTiles = hardMode || directionalTiles;
    }

    private void clearMultiplayerModifierSnapshot()
    {
        multiplayerSequenceMode = false;
        multiplayerAddDisablers = false;
        multiplayerDangerTiles = false;
        multiplayerDirectionalTiles = false;
        multiplayerHardMode = false;
    }

    private void clearMultiplayerSummary()
    {
        multiplayerSummaryLevelName = "";
        multiplayerSummaryWinner = "";
        multiplayerSummaryWinnerTimeLabel = "";
    }

    private TileGameMultiplayerState createMultiplayerStateSnapshot()
    {
        TileGameMultiplayerState state = new TileGameMultiplayerState();
        state.mode = mode.name();
        state.sequenceModeEnabled = isSequenceMode;
        state.addDisablersEnabled = isAddDisablersMode;
        state.dangerTilesEnabled = isDangerTilesMode;
        state.directionalTilesEnabled = isDirectionalTilesMode;
        state.hardModeEnabled = isHardMode;
        state.countdownTicksRemaining = countdownTicksRemaining;
        state.totalRunTicks = totalRunTicks;
        state.currentSequenceNumber = currentSequenceNumber;
        state.disablerCountdown = disablerCountdown;
        state.sequenceShrinkDelay = sequenceShrinkDelay;
        state.nonSeqDisablerTimer = nonSeqDisablerTimer;
        state.activeLevelTiles = toMultiplayerTiles(activeLevelGroupTiles);
        state.validSequenceTiles = toMultiplayerTiles(validSequenceTiles);
        state.sequenceTileTimers = toTimedTiles(sequenceTileTimers);
        state.sequenceHardBonusTiles = toTimedTiles(sequenceHardBonusTiles);
        state.sequenceHardBonusTimers = toTimedTiles(sequenceHardBonusTimers);
        state.disabledTileTimers = toTimedTiles(disabledTileTimers);
        state.resetTile = toMultiplayerTile(resetTile);
        state.dangerTileCountdowns = toTimedTiles(dangerTileCountdowns);
        state.dangerTileActiveTimers = toTimedTiles(dangerTileActiveTimers);
        state.directionalTiles = toDirectionalTiles(directionalTileDirections);
        state.powerUpTiles = toTimedTiles(powerUpTiles);
        state.powerUpTimers = toTimedTiles(powerUpTimers);
        return state;
    }

    private void applyMultiplayerState(TileGameMultiplayerState state)
    {
        try
        {
            mode = TileGameMode.valueOf(state.mode);
        }
        catch (IllegalArgumentException | NullPointerException ex)
        {
            mode = TileGameMode.LEVEL;
        }

        countdownTicksRemaining = state.countdownTicksRemaining;
        totalRunTicks = state.totalRunTicks;
        isHardMode = state.hardModeEnabled;
        isSequenceMode = state.sequenceModeEnabled;
        isAddDisablersMode = state.hardModeEnabled || state.addDisablersEnabled;
        isDangerTilesMode = state.hardModeEnabled || state.dangerTilesEnabled;
        isDirectionalTilesMode = state.hardModeEnabled || state.directionalTilesEnabled;
        setMultiplayerModifierSnapshot(isSequenceMode, isAddDisablersMode, isDangerTilesMode, isDirectionalTilesMode, isHardMode);
        currentSequenceNumber = state.currentSequenceNumber;
        disablerCountdown = state.disablerCountdown;
        sequenceShrinkDelay = state.sequenceShrinkDelay;
        nonSeqDisablerTimer = state.nonSeqDisablerTimer;
        replaceSet(activeLevelGroupTiles, fromMultiplayerTiles(state.activeLevelTiles));
        replaceSet(validSequenceTiles, fromMultiplayerTiles(state.validSequenceTiles));
        replaceTimedMap(sequenceTileTimers, state.sequenceTileTimers);
        replaceTimedMap(sequenceHardBonusTiles, state.sequenceHardBonusTiles);
        replaceTimedMap(sequenceHardBonusTimers, state.sequenceHardBonusTimers);
        replaceTimedMap(disabledTileTimers, state.disabledTileTimers);
        resetTile = fromMultiplayerTile(state.resetTile);
        resetDisabledTile = null;
        if (resetTile == null || !resetTile.equals(lastDisablerClearRequestTile))
        {
            // A new (or no) reset tile is active — allow a fresh clear request
            lastDisablerClearRequestTile = null;
        }
        replaceTimedMap(dangerTileCountdowns, state.dangerTileCountdowns);
        replaceTimedMap(dangerTileActiveTimers, state.dangerTileActiveTimers);
        replaceDirectionalMap(directionalTileDirections, state.directionalTiles);
        replaceTimedMap(powerUpTiles, state.powerUpTiles);
        replaceTimedMap(powerUpTimers, state.powerUpTimers);
    }

    private void broadcastMultiplayerStateIfHost()
    {
        if (!multiplayerHost || !multiplayerActive || multiplayerRoomId.isEmpty())
        {
            return;
        }

        TileGameMultiplayerMessage message = new TileGameMultiplayerMessage();
        message.type = "state";
        message.roomId = multiplayerRoomId;
        message.host = currentPlayerName();
        message.state = createMultiplayerStateSnapshot();
        sendMultiplayerMessage(message);
    }

    private boolean isMultiplayerParticipant()
    {
        return multiplayerActive && !multiplayerHost && !multiplayerRoomId.isEmpty();
    }

    private List<TileGameMultiplayerTile> toMultiplayerTiles(Set<WorldPoint> tiles)
    {
        ArrayList<TileGameMultiplayerTile> result = new ArrayList<>();
        if (tiles == null)
        {
            return result;
        }

        tiles.stream()
                .sorted(Comparator.comparingInt(WorldPoint::getX)
                        .thenComparingInt(WorldPoint::getY)
                        .thenComparingInt(WorldPoint::getPlane))
                .forEach(tile -> result.add(toMultiplayerTile(tile)));
        return result;
    }

    private List<TileGameMultiplayerTile> toMultiplayerModifierTiles(Map<WorldPoint, TileModifier> modifiers)
    {
        ArrayList<TileGameMultiplayerTile> result = new ArrayList<>();
        if (modifiers == null)
        {
            return result;
        }

        modifiers.entrySet().stream()
                .sorted(Comparator.comparing(entry -> entry.getKey(), Comparator.comparingInt(WorldPoint::getX)
                        .thenComparingInt(WorldPoint::getY)
                        .thenComparingInt(WorldPoint::getPlane)))
                .forEach(entry ->
                {
                    TileGameMultiplayerTile tile = toMultiplayerTile(entry.getKey());
                    tile.modifier = entry.getValue().name();
                    result.add(tile);
                });
        return result;
    }

    private TileGameMultiplayerTile toMultiplayerTile(WorldPoint point)
    {
        if (point == null)
        {
            return null;
        }

        TileGameMultiplayerTile tile = new TileGameMultiplayerTile();
        tile.x = point.getX();
        tile.y = point.getY();
        tile.plane = point.getPlane();
        return tile;
    }

    private List<TileGameMultiplayerTimedTile> toTimedTiles(Map<WorldPoint, Integer> tiles)
    {
        ArrayList<TileGameMultiplayerTimedTile> result = new ArrayList<>();
        if (tiles == null)
        {
            return result;
        }

        tiles.entrySet().stream()
                .sorted(Comparator.comparing(entry -> entry.getKey(), Comparator.comparingInt(WorldPoint::getX)
                        .thenComparingInt(WorldPoint::getY)
                        .thenComparingInt(WorldPoint::getPlane)))
                .forEach(entry ->
                {
                    TileGameMultiplayerTimedTile tile = new TileGameMultiplayerTimedTile();
                    tile.x = entry.getKey().getX();
                    tile.y = entry.getKey().getY();
                    tile.plane = entry.getKey().getPlane();
                    tile.value = entry.getValue();
                    result.add(tile);
                });
        return result;
    }

    private List<TileGameMultiplayerDirectionalTile> toDirectionalTiles(Map<WorldPoint, String> tiles)
    {
        ArrayList<TileGameMultiplayerDirectionalTile> result = new ArrayList<>();
        if (tiles == null)
        {
            return result;
        }

        tiles.entrySet().stream()
                .sorted(Comparator.comparing(entry -> entry.getKey(), Comparator.comparingInt(WorldPoint::getX)
                        .thenComparingInt(WorldPoint::getY)
                        .thenComparingInt(WorldPoint::getPlane)))
                .forEach(entry ->
                {
                    TileGameMultiplayerDirectionalTile tile = new TileGameMultiplayerDirectionalTile();
                    tile.x = entry.getKey().getX();
                    tile.y = entry.getKey().getY();
                    tile.plane = entry.getKey().getPlane();
                    tile.direction = entry.getValue();
                    result.add(tile);
                });
        return result;
    }

    private Set<WorldPoint> fromMultiplayerTiles(List<? extends TileGameMultiplayerTile> tiles)
    {
        Set<WorldPoint> result = new HashSet<>();
        if (tiles == null)
        {
            return result;
        }

        for (TileGameMultiplayerTile tile : tiles)
        {
            WorldPoint point = fromMultiplayerTile(tile);
            if (point != null)
            {
                result.add(point);
            }
        }

        return result;
    }

    private WorldPoint fromMultiplayerTile(TileGameMultiplayerTile tile)
    {
        if (tile == null)
        {
            return null;
        }

        return new WorldPoint(tile.x, tile.y, tile.plane);
    }

    private Map<WorldPoint, TileModifier> fromMultiplayerModifierTiles(List<TileGameMultiplayerTile> tiles)
    {
        Map<WorldPoint, TileModifier> result = new LinkedHashMap<>();
        if (tiles == null)
        {
            return result;
        }

        for (TileGameMultiplayerTile tile : tiles)
        {
            WorldPoint point = fromMultiplayerTile(tile);
            if (point == null || tile.modifier == null)
            {
                continue;
            }

            try
            {
                result.put(point, TileModifier.valueOf(tile.modifier));
            }
            catch (IllegalArgumentException ex)
            {
                say("Tile Game multiplayer ignored an unknown tile modifier: " + tile.modifier);
            }
        }

        return result;
    }

    private void replaceSet(Set<WorldPoint> target, Set<WorldPoint> source)
    {
        target.clear();
        target.addAll(source);
    }

    private void replaceTimedMap(Map<WorldPoint, Integer> target, List<TileGameMultiplayerTimedTile> source)
    {
        target.clear();
        if (source == null)
        {
            return;
        }

        for (TileGameMultiplayerTimedTile tile : source)
        {
            WorldPoint point = fromMultiplayerTile(tile);
            if (point != null)
            {
                target.put(point, tile.value);
            }
        }
    }

    private void replaceDirectionalMap(Map<WorldPoint, String> target, List<TileGameMultiplayerDirectionalTile> source)
    {
        target.clear();
        if (source == null)
        {
            return;
        }

        for (TileGameMultiplayerDirectionalTile tile : source)
        {
            WorldPoint point = fromMultiplayerTile(tile);
            if (point != null && tile.direction != null)
            {
                target.put(point, tile.direction);
            }
        }
    }

    void showExportLevelPicker()
    {
        if (groups.isEmpty())
        {
            showHelpOverhead("No levels to export. Create one with the + button first.");
            return;
        }

        SwingUtilities.invokeLater(() ->
        {
            ArrayList<String> groupNames = new ArrayList<>(groups.keySet());
            groupNames.sort(Comparator.naturalOrder());

            String defaultGroup = normalizeGroupName(activeGroupName);
            if (!groups.containsKey(defaultGroup))
            {
                defaultGroup = groupNames.get(0);
            }

            Object selected = JOptionPane.showInputDialog(
                    null,
                    "Choose the level to export:",
                    "Export Tile Game Level",
                    JOptionPane.PLAIN_MESSAGE,
                    null,
                    groupNames.toArray(),
                    defaultGroup
            );

            if (selected instanceof String)
            {
                clientThread.invokeLater(() -> exportGroupToClipboard((String) selected));
            }
        });
    }

    private void exportGroupToClipboard(String groupName)
    {
        if (groupName.isEmpty() || !groups.containsKey(groupName))
        {
            showHelpOverhead("That level no longer exists. Check the Levels card.");
            return;
        }

        hideViewedGroups();
        activeGroupName = groupName;

        TileGameLevelExport levelExport = new TileGameLevelExport();
        levelExport.name = groupName;
        levelExport.creator = currentPlayerName();
        levelExport.tiles = toTileDtos(groups.get(groupName));

        String json = gson.toJson(levelExport);
        Toolkit.getDefaultToolkit().getSystemClipboard().setContents(new StringSelection(json), null);
        showOverhead("Exported " + groupName + " JSON to clipboard!");
    }

    private void loadPersistentData()
    {
        String json = configManager.getConfiguration(CONFIG_GROUP, CONFIG_SAVE_KEY);

        if (json == null || json.isEmpty())
        {
            return;
        }

        TileGameSaveData saveData;
        try
        {
            saveData = gson.fromJson(json, TileGameSaveData.class);
        }
        catch (JsonParseException ex)
        {
            say("Tile Game saved data could not be loaded.");
            return;
        }

        groups.clear();
        highscores.clear();
        tileModifiers.clear();
        pendingSequenceMode = false;
        pendingSequenceModeDirty = false;
        pendingAddDisablers = false;
        pendingAddDisablersDirty = false;
        pendingDangerTiles = false;
        pendingDangerTilesDirty = false;
        pendingDirectionalTiles = false;
        pendingDirectionalTilesDirty = false;
        pendingHardMode = false;
        pendingHardModeDirty = false;

        if (saveData.groups != null)
        {
            for (Map.Entry<String, List<TileGameTileDto>> entry : saveData.groups.entrySet())
            {
                String name = normalizeGroupName(entry.getKey());
                if (name == null || name.isEmpty())
                {
                    continue;
                }
                groups.put(name, toWorldPoints(entry.getValue()));
            }
        }

        if (saveData.highscores != null)
        {
            for (Map.Entry<String, List<TileGameResultDto>> entry : saveData.highscores.entrySet())
            {
                ArrayList<TileGameResult> results = new ArrayList<>();

                for (TileGameResultDto result : entry.getValue())
                {
                    results.add(new TileGameResult(result.totalTicks, TileGameResult.ScoreMode.fromSerializedValue(result.scoreMode)));
                }

                results.sort(scoreboardComparator());
                highscores.put(normalizeGroupName(entry.getKey()), results);
            }
        }

        if (saveData.tileModifiers != null)
        {
            for (Map.Entry<String, List<TileGameTileDto>> entry : saveData.tileModifiers.entrySet())
            {
                Map<WorldPoint, TileModifier> mods = toModifierMap(entry.getValue());
                if (!mods.isEmpty())
                {
                    tileModifiers.put(normalizeGroupName(entry.getKey()), mods);
                }
            }
        }
    }

    private void savePersistentData()
    {
        TileGameSaveData saveData = new TileGameSaveData();
        saveData.groups = new LinkedHashMap<>();
        saveData.highscores = new LinkedHashMap<>();
        saveData.tileModifiers = new LinkedHashMap<>();

        groups.entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .forEach(entry -> saveData.groups.put(normalizeGroupName(entry.getKey()), toTileDtos(entry.getValue())));

        highscores.entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .forEach(entry -> saveData.highscores.put(normalizeGroupName(entry.getKey()), toResultDtos(entry.getValue())));

        tileModifiers.entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .forEach(entry -> saveData.tileModifiers.put(normalizeGroupName(entry.getKey()), toModifierDtos(entry.getValue())));

        configManager.setConfiguration(CONFIG_GROUP, CONFIG_SAVE_KEY, gson.toJson(saveData));
    }

    private void updatePanelLists()
    {
        if (panel != null)
        {
            panel.showGroups(groups);
        }
    }

    Map<String, Set<WorldPoint>> getGroups()
    {
        return groups;
    }

    Map<String, ArrayList<TileGameResult>> getHighscores()
    {
        return highscores;
    }

    private Comparator<TileGameResult> scoreboardComparator()
    {
        return Comparator.comparingInt(result -> result.totalTicks);
    }

    private List<TileGameTileDto> toTileDtos(Set<WorldPoint> tiles)
    {
        ArrayList<TileGameTileDto> tileDtos = new ArrayList<>();

        tiles.stream()
                .sorted(Comparator.comparingInt(WorldPoint::getX)
                        .thenComparingInt(WorldPoint::getY)
                        .thenComparingInt(WorldPoint::getPlane))
                .forEach(tile ->
                {
                    TileGameTileDto tileDto = new TileGameTileDto();
                    tileDto.x = tile.getX();
                    tileDto.y = tile.getY();
                    tileDto.plane = tile.getPlane();
                    tileDtos.add(tileDto);
                });

        return tileDtos;
    }

    private List<TileGameTileDto> toModifierDtos(Map<WorldPoint, TileModifier> modifiers)
    {
        if (modifiers == null || modifiers.isEmpty())
        {
            return new ArrayList<>();
        }

        ArrayList<TileGameTileDto> tileDtos = new ArrayList<>();
        modifiers.entrySet().stream()
                .sorted(Comparator.comparing(entry -> entry.getKey(), Comparator.comparingInt(WorldPoint::getX)
                        .thenComparingInt(WorldPoint::getY)
                        .thenComparingInt(WorldPoint::getPlane)))
                .forEach(entry ->
                {
                    TileGameTileDto tileDto = new TileGameTileDto();
                    tileDto.x = entry.getKey().getX();
                    tileDto.y = entry.getKey().getY();
                    tileDto.plane = entry.getKey().getPlane();
                    tileDto.modifier = entry.getValue().name();
                    tileDtos.add(tileDto);
                });

        return tileDtos;
    }

    private Set<WorldPoint> toWorldPoints(List<TileGameTileDto> tileDtos)
    {
        Set<WorldPoint> tiles = new HashSet<>();

        for (TileGameTileDto tileDto : tileDtos)
        {
            tiles.add(new WorldPoint(tileDto.x, tileDto.y, tileDto.plane));
        }

        return tiles;
    }

        private Map<WorldPoint, TileModifier> toModifierMap(List<TileGameTileDto> tileDtos)
        {
            Map<WorldPoint, TileModifier> mods = new LinkedHashMap<>();

            if (tileDtos != null)
            {
                for (TileGameTileDto tileDto : tileDtos)
                {
                    if (tileDto.modifier != null && !tileDto.modifier.isEmpty())
                    {
                        try
                        {
                            WorldPoint point = new WorldPoint(tileDto.x, tileDto.y, tileDto.plane);
                            mods.put(point, TileModifier.valueOf(tileDto.modifier));
                        }
                        catch (IllegalArgumentException ignored)
                        {
                            // Ignore unknown modifier values
                        }
                    }
                }
            }

            return mods;
        }

    private List<TileGameResultDto> toResultDtos(List<TileGameResult> results)
    {
        ArrayList<TileGameResultDto> resultDtos = new ArrayList<>();

        for (TileGameResult result : results)
        {
            TileGameResultDto resultDto = new TileGameResultDto();
            resultDto.totalTicks = result.totalTicks;
            resultDto.scoreMode = result.getScoreMode().name();
            resultDtos.add(resultDto);
        }

        return resultDtos;
    }

    private String readClipboardText() throws UnsupportedFlavorException, IOException
    {
        return (String) Toolkit.getDefaultToolkit().getSystemClipboard().getData(DataFlavor.stringFlavor);
    }

    private boolean isValidLevelExport(TileGameLevelExport levelExport)
    {
        return levelExport != null
                && levelExport.name != null
                && !levelExport.name.trim().isEmpty()
                && levelExport.tiles != null
                && !levelExport.tiles.isEmpty();
    }

    private String currentPlayerName()
    {
        String playerName = currentPlayerNameOrNull();
        return playerName == null ? "unknown" : playerName;
    }

    private String currentPlayerNameOrNull()
    {
        Player player = client.getLocalPlayer();

        if (player == null || player.getName() == null || player.getName().isEmpty())
        {
            return null;
        }

        return cleanPlayerName(player.getName());
    }

    private String cleanPlayerName(String name)
    {
        if (name == null)
        {
            return null;
        }

        String cleaned = Text.removeTags(name)
                .replace('\u00A0', ' ')
                .trim();
        return cleaned.isEmpty() ? null : cleaned;
    }

    private String safeCreator(String creator)
    {
        return creator == null || creator.trim().isEmpty() ? "unknown" : creator;
    }

    private BufferedImage createPanelIcon()
    {
        BufferedImage image = new BufferedImage(16, 16, BufferedImage.TYPE_INT_ARGB);
        Graphics2D graphics = image.createGraphics();

        try
        {
            graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            graphics.setColor(new Color(55, 180, 255));
            graphics.fillRoundRect(1, 1, 14, 14, 4, 4);
            graphics.setColor(Color.WHITE);
            graphics.setStroke(new BasicStroke(2));
            graphics.drawLine(4, 5, 12, 5);
            graphics.drawLine(4, 8, 10, 8);
            graphics.drawLine(4, 11, 12, 11);
        }
        finally
        {
            graphics.dispose();
        }

        return image;
    }

    TileGameMode getMode()
    {
        return mode;
    }

    String getActiveGroupName()
    {
        return activeGroupName;
    }

    Set<WorldPoint> getSelectedTiles()
    {
        return selectedTiles;
    }

    Map<WorldPoint, Color> getRunColoredTiles()
    {
        return runColoredTiles;
    }

    Set<WorldPoint> getSequenceOrder()
    {
        return validSequenceTiles;
    }

    int getCurrentSequenceNumber()
    {
            return currentSequenceNumber;
    }

        Map<WorldPoint, Integer> getSequenceTileTimers()
        {
            return sequenceTileTimers;
        }

        Map<WorldPoint, Integer> getSequenceHardBonusTiles()
        {
            return sequenceHardBonusTiles;
        }

        Map<WorldPoint, Integer> getPowerUpTiles()
        {
            return powerUpTiles;
        }

        Map<WorldPoint, Integer> getDangerTileCountdowns()
        {
            return dangerTileCountdowns;
        }

        Map<WorldPoint, Integer> getDangerTileActiveTimers()
        {
            return dangerTileActiveTimers;
        }

        Map<WorldPoint, String> getDirectionalTileDirections()
        {
            return directionalTileDirections;
        }

        boolean isHardMode()
        {
            return isHardMode;
        }

        int getDisablerCountdown()
        {
            return disablerCountdown;
        }

    Set<WorldPoint> getActiveLevelGroupTiles()
    {
        return activeLevelGroupTiles;
    }

    Set<WorldPoint> getViewedLevelTiles()
    {
        Set<WorldPoint> viewedTiles = new HashSet<>();

        for (String groupName : viewedGroups)
        {
            Set<WorldPoint> groupTiles = groups.get(groupName);

            if (groupTiles != null)
            {
                viewedTiles.addAll(groupTiles);
            }
        }

        return viewedTiles;
    }

    Map<WorldPoint, TileModifier> getModifiersForViewedGroups()
    {
        Map<WorldPoint, TileModifier> combined = new LinkedHashMap<>();

        for (String groupName : viewedGroups)
        {
            Map<WorldPoint, TileModifier> mods = tileModifiers.get(normalizeGroupName(groupName));
            if (mods != null && !mods.isEmpty())
            {
                combined.putAll(mods);
            }
        }

        return combined;
    }

    String getLastRunTimeLabel()
    {
        if (lastRunTicks <= 0)
        {
            return "-";
        }

        return formatTicks(lastRunTicks);
    }

    private String formatTicks(int ticks)
    {
        return String.format("%.1fs", ticks * 0.6);
    }

    boolean isViewingGroup(String groupName)
    {
        return viewedGroups.contains(normalizeGroupName(groupName));
    }

    boolean isSequenceModeEnabled()
    {
        if (mode == TileGameMode.COUNTDOWN || mode == TileGameMode.LEVEL)
        {
            return isSequenceMode;
        }

        return pendingSequenceMode;
    }

    void setSequenceModeEnabled(boolean enabled)
    {
        pendingSequenceMode = enabled;
        pendingSequenceModeDirty = true;
        if (!enabled)
        {
            clearSequenceHardBonusTiles();
        }
        updatePanel();
    }

    boolean isAddDisablersEnabled()
    {
        if (isHardModeEnabled())
        {
            return true;
        }

        if (mode == TileGameMode.COUNTDOWN || mode == TileGameMode.LEVEL)
        {
            return isAddDisablersMode;
        }

        return pendingAddDisablers;
    }

    void setAddDisablersEnabled(boolean enabled)
    {
        if (isHardModeEnabled() && !enabled)
        {
            updatePanel();
            return;
        }

        pendingAddDisablers = enabled;
        pendingAddDisablersDirty = true;
        updatePanel();
    }

    boolean isDangerTilesEnabled()
    {
        if (isHardModeEnabled())
        {
            return true;
        }

        if (mode == TileGameMode.COUNTDOWN || mode == TileGameMode.LEVEL)
        {
            return isDangerTilesMode;
        }

        return pendingDangerTiles;
    }

    void setDangerTilesEnabled(boolean enabled)
    {
        if (isHardModeEnabled() && !enabled)
        {
            updatePanel();
            return;
        }

        pendingDangerTiles = enabled;
        pendingDangerTilesDirty = true;
        updatePanel();
    }

    boolean isDirectionalTilesEnabled()
    {
        if (isHardModeEnabled())
        {
            return true;
        }

        if (mode == TileGameMode.COUNTDOWN || mode == TileGameMode.LEVEL)
        {
            return isDirectionalTilesMode;
        }

        return pendingDirectionalTiles;
    }

    void setDirectionalTilesEnabled(boolean enabled)
    {
        if (isHardModeEnabled() && !enabled)
        {
            updatePanel();
            return;
        }

        pendingDirectionalTiles = enabled;
        pendingDirectionalTilesDirty = true;
        updatePanel();
    }

            boolean isHardModeEnabled()
            {
                if (mode == TileGameMode.COUNTDOWN || mode == TileGameMode.LEVEL)
                {
                    return isHardMode;
                }

                return pendingHardMode;
            }

            void setHardModeEnabled(boolean enabled)
            {
                pendingHardMode = enabled;
                pendingHardModeDirty = true;
                if (enabled)
                {
                    pendingDangerTiles = true;
                    pendingDangerTilesDirty = true;
                    pendingDirectionalTiles = true;
                    pendingDirectionalTilesDirty = true;
                }
                if (!enabled)
                {
                    clearSequenceHardBonusTiles();
                }
                updatePanel();
            }

            boolean canEditSequenceMode()
    {
        // Always allow editing the sequence mode so the checkbox in the Levels card
        // remains clickable even after stopping a run or when no group is active.
        return true;
    }

    // Grade/score/miss/revisit tracking removed per configuration

    int getTotalRunTicks()
    {
        return totalRunTicks;
    }

    int getGroupCount()
    {
        return groups.size();
    }

    private static final class TileGameSaveData
    {
        private Map<String, List<TileGameTileDto>> groups;
        private Map<String, List<TileGameResultDto>> highscores;
        private Map<String, List<TileGameTileDto>> tileModifiers;
    }

    private static final class TileGameLevelExport
    {
        private String name;
        private String creator;
        private List<TileGameTileDto> tiles;
    }

    private static final class TileGameTileDto
    {
        private int x;
        private int y;
        private int plane;
        private String modifier;
    }

    private static final class TileGameResultDto
    {
        private int totalTicks;
        private String scoreMode;
    }
}