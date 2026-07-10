package com.tileracer;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Container;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.GridLayout;
import java.awt.Insets;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import javax.swing.AbstractButton;
import javax.swing.BorderFactory;
import javax.swing.DefaultListCellRenderer;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.JTextArea;
import javax.swing.SwingUtilities;
import javax.swing.table.DefaultTableModel;
import net.runelite.client.ui.PluginPanel;

class TileGamePanel extends PluginPanel
{
    private static final Color PANEL_BACKGROUND = new Color(23, 18, 42);
    private static final Color SECTION_BACKGROUND = new Color(36, 30, 64);
    private static final Color ROW_BACKGROUND = new Color(45, 38, 82);
    private static final Color HEADER_BACKGROUND = new Color(82, 56, 151);
    private static final Color ACTION_BACKGROUND = new Color(36, 160, 132);
    private static final Color ACTION_BORDER = new Color(178, 255, 235);
    private static final Color CYAN = new Color(86, 226, 255);
    private static final Color GOLD = new Color(255, 208, 79);
    private static final Color TEXT_COLOR = Color.WHITE;
    private static final Color MUTED_TEXT_COLOR = new Color(202, 190, 245);

    private final TileGamePlugin plugin;
    private final JLabel modeValue = valueLabel();
    private final JLabel groupValue = valueLabel();
    private final JLabel winnerValue = valueLabel();
    private final JLabel timeValue = valueLabel();
    private final JPanel levelsPanel = new JPanel();
    private final JPanel levelCreationPanel = new JPanel();
    private final JPanel multiplayerPanel = new JPanel();
    private JCheckBox sequenceCheckbox;
    private JCheckBox addDisablersCheckbox;
    private JCheckBox dangerTilesCheckbox;
    private JCheckBox directionalTilesCheckbox;
        private JCheckBox hardModeCheckbox;
    private JButton paintButton;
    private JButton clearButton;
        private JButton restartButton;
        private JButton stopButton;
        private final Map<String, List<JButton>> levelActionButtons = new HashMap<>();

    TileGamePanel(TileGamePlugin plugin)
    {
        super();
        this.plugin = plugin;

        getScrollPane().setVerticalScrollBarPolicy(JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED);
        getScrollPane().getVerticalScrollBar().setUnitIncrement(16);

        setLayout(new BorderLayout(0, 10));
        setBackground(PANEL_BACKGROUND);
        setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));

        JPanel content = new JPanel();
        content.setLayout(new GridBagLayout());
        content.setBackground(PANEL_BACKGROUND);

        levelsPanel.setLayout(new BoxLayout(levelsPanel, BoxLayout.Y_AXIS));
        levelsPanel.setBackground(SECTION_BACKGROUND);
        levelCreationPanel.setBackground(SECTION_BACKGROUND);
        multiplayerPanel.setBackground(SECTION_BACKGROUND);

        addFullWidth(content, card(
                "Controls",
                actionPanel(),
                null,
                plugin.isControlsCollapsed(),
                plugin::setControlsCollapsed
        ), 0);
        addFullWidth(content, card(
                "Game State",
                currentGamePanel(),
                null,
                plugin.isGameStateCollapsed(),
                plugin::setGameStateCollapsed
        ), 1);
        addFullWidth(content, card(
                "Multiplayer",
                multiplayerStatusPanel(),
                null,
                plugin.isMultiplayerCollapsed(),
                plugin::setMultiplayerCollapsed
        ), 2);
        addFullWidth(content, card(
                "Levels",
                levelsCardPanel(),
                null,
                plugin.isLevelsCollapsed(),
                plugin::setLevelsCollapsed
        ), 3);

        add(content, BorderLayout.CENTER);
        refresh();
    }

    void refresh()
    {
        SwingUtilities.invokeLater(() ->
        {
            setInteractiveComponentsEnabled(this, true);
            modeValue.setText(plugin.getMode().name());
            groupValue.setText(blankToDash(plugin.getDisplayedMultiplayerLevelLabel()));
            winnerValue.setText(blankToDash(plugin.getDisplayedMultiplayerWinnerLabel()));
            timeValue.setText(blankToDash(plugin.getDisplayedMultiplayerWinnerTimeLabel()));
            if (sequenceCheckbox != null)
            {
                sequenceCheckbox.setSelected(plugin.isSequenceModeEnabled());
                sequenceCheckbox.setEnabled(plugin.canEditSequenceMode());
            }
            if (addDisablersCheckbox != null)
            {
                addDisablersCheckbox.setSelected(plugin.isAddDisablersEnabled());
                addDisablersCheckbox.setEnabled(plugin.canEditSequenceMode() && !plugin.isHardModeEnabled());
            }
            if (dangerTilesCheckbox != null)
            {
                dangerTilesCheckbox.setSelected(plugin.isDangerTilesEnabled());
                dangerTilesCheckbox.setEnabled(plugin.canEditSequenceMode() && !plugin.isHardModeEnabled());
            }
            if (directionalTilesCheckbox != null)
            {
                directionalTilesCheckbox.setSelected(plugin.isDirectionalTilesEnabled());
                directionalTilesCheckbox.setEnabled(plugin.canEditSequenceMode() && !plugin.isHardModeEnabled());
            }
            if (hardModeCheckbox != null)
            {
                hardModeCheckbox.setSelected(plugin.isHardModeEnabled());
                hardModeCheckbox.setEnabled(plugin.canEditSequenceMode());
            }
            updateLevelCreationControls();
            if (paintButton != null)
            {
                paintButton.setText(plugin.isPaintMode() ? "Painting" : "Paint");
                paintButton.setEnabled(plugin.canUsePaintButton());
            }
            if (clearButton != null)
            {
                clearButton.setEnabled(!plugin.getPaintedTiles().isEmpty());
            }
            if (restartButton != null)
            {
                restartButton.setEnabled(plugin.canReplayCurrentGame());
            }
            if (stopButton != null)
            {
                stopButton.setEnabled(true);
            }
            updateLevelActionButtons();
            updateMultiplayerControls();
            if (!plugin.isRendererReady())
            {
                setInteractiveComponentsEnabled(this, false);
            }
        });
    }

    void showGroups(Map<String, ?> groups)
    {
        SwingUtilities.invokeLater(() ->
        {
            levelsPanel.removeAll();
            levelActionButtons.clear();

            if (groups.isEmpty())
            {
                levelsPanel.add(emptyLevelsMessage());
                if (!plugin.isRendererReady())
                {
                    setInteractiveComponentsEnabled(this, false);
                }
                levelsPanel.revalidate();
                levelsPanel.repaint();
                return;
            }

            groups.keySet().stream()
                    .sorted()
                    .forEach(groupName -> levelsPanel.add(levelRow(groupName)));

            if (!plugin.isRendererReady())
            {
                setInteractiveComponentsEnabled(this, false);
            }
            levelsPanel.revalidate();
            levelsPanel.repaint();
        });
    }

    void showHighscoresDialog()
    {
        SwingUtilities.invokeLater(() ->
        {
            if (plugin.getGroups().isEmpty())
            {
                JOptionPane.showMessageDialog(this, "No levels exist yet. Create one with the + button.", "Highscores", JOptionPane.INFORMATION_MESSAGE);
                return;
            }

            JPanel panel = new JPanel(new BorderLayout(0, 10));
            panel.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));

            JComboBox<String> levelSelector = new JComboBox<>(sortedGroupNames());
            JComboBox<TileGameResult.ScoreMode> scoreModeSelector = new JComboBox<>(new TileGameResult.ScoreMode[]{
                    TileGameResult.ScoreMode.NORMAL,
                    TileGameResult.ScoreMode.NON_SEQUENCE_HARDMODE,
                    TileGameResult.ScoreMode.SEQUENCE_HARDMODE
            });
            scoreModeSelector.setRenderer(new DefaultListCellRenderer()
            {
                @Override
                public Component getListCellRendererComponent(javax.swing.JList<?> list, Object value, int index, boolean isSelected, boolean cellHasFocus)
                {
                    super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);
                    if (value instanceof TileGameResult.ScoreMode)
                    {
                        TileGameResult.ScoreMode scoreMode = (TileGameResult.ScoreMode) value;
                        setText(scoreMode == TileGameResult.ScoreMode.NORMAL ? "Normal mode"
                                : scoreMode == TileGameResult.ScoreMode.NON_SEQUENCE_HARDMODE ? "Non Sequenced Hard Mode"
                                : "Sequenced Hard Mode");
                    }
                    return this;
                }
            });
            JLabel modeLabel = new JLabel("Showing normal mode scores");

            JScrollPane tableScroll = new JScrollPane(highscoreTable((String) levelSelector.getSelectedItem(), TileGameResult.ScoreMode.NORMAL));
            tableScroll.setPreferredSize(new Dimension(720, 180));

            Runnable refreshScores = () ->
            {
                String groupName = (String) levelSelector.getSelectedItem();
                TileGameResult.ScoreMode scoreMode = (TileGameResult.ScoreMode) scoreModeSelector.getSelectedItem();
                tableScroll.setViewportView(highscoreTable(groupName, scoreMode));
                modeLabel.setText("Showing " + scoreMode.displayName() + " scores");
            };

            levelSelector.addActionListener(event -> refreshScores.run());
            scoreModeSelector.addActionListener(event -> refreshScores.run());

            JPanel selectorPanel = new JPanel(new BorderLayout(0, 6));
            selectorPanel.add(levelSelector, BorderLayout.NORTH);

            JPanel modePanel = new JPanel(new BorderLayout(8, 0));
            modePanel.add(modeLabel, BorderLayout.WEST);
            modePanel.add(scoreModeSelector, BorderLayout.EAST);
            selectorPanel.add(modePanel, BorderLayout.SOUTH);

            panel.add(selectorPanel, BorderLayout.NORTH);
            panel.add(tableScroll, BorderLayout.CENTER);
            refreshScores.run();

            JOptionPane optionPane = new JOptionPane(panel, JOptionPane.PLAIN_MESSAGE, JOptionPane.DEFAULT_OPTION);
            JDialog dialog = optionPane.createDialog(null, "Tile Racer Highscores");
            dialog.setPreferredSize(new Dimension(780, 360));
            dialog.pack();
            dialog.setVisible(true);
        });
    }

    private JPanel statsPanel()
    {
        JPanel panel = new JPanel(new GridLayout(0, 2, 8, 5));
        panel.setBackground(SECTION_BACKGROUND);
        panel.setAlignmentX(Component.LEFT_ALIGNMENT);

            addRow(panel, "Mode", modeValue);
            addRow(panel, "Level", groupValue);
            addRow(panel, "Winner", winnerValue);
            addRow(panel, "Time", timeValue);

        return panel;
    }

    private void addFullWidth(JPanel parent, Component component, int row)
    {
        GridBagConstraints constraints = new GridBagConstraints();
        constraints.gridx = 0;
        constraints.gridy = row;
        constraints.weightx = 1;
        constraints.fill = GridBagConstraints.HORIZONTAL;
        constraints.anchor = GridBagConstraints.NORTHWEST;
        constraints.insets = new Insets(0, 0, 8, 0);
        parent.add(component, constraints);
    }

    private JPanel currentGamePanel()
    {
        JPanel panel = new JPanel(new BorderLayout(0, 8));
        panel.setBackground(SECTION_BACKGROUND);
        panel.setAlignmentX(Component.LEFT_ALIGNMENT);
        panel.add(statsPanel(), BorderLayout.CENTER);
        panel.add(currentGameActions(), BorderLayout.SOUTH);
        return panel;
    }

    private JPanel currentGameActions()
    {
        JPanel panel = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 0));
        panel.setBackground(SECTION_BACKGROUND);
        restartButton = iconButton("↻", "Restart current level", plugin::restartCurrentGame);
        stopButton = iconButton("■", "Stop current game", plugin::stopCurrentGame);
        panel.add(restartButton);
        panel.add(stopButton);
        return panel;
    }

    private JPanel levelsCardPanel()
    {
        JPanel panel = new JPanel(new BorderLayout(0, 8));
        panel.setBackground(SECTION_BACKGROUND);
        panel.setAlignmentX(Component.LEFT_ALIGNMENT);

        JPanel centerPanel = new JPanel(new BorderLayout(0, 8));
        centerPanel.setBackground(SECTION_BACKGROUND);
        centerPanel.add(levelsPanel, BorderLayout.CENTER);

        sequenceCheckbox = new JCheckBox("Sequence Mode");
        sequenceCheckbox.setSelected(plugin.isSequenceModeEnabled());
        sequenceCheckbox.setForeground(TEXT_COLOR);
        sequenceCheckbox.setBackground(SECTION_BACKGROUND);
        sequenceCheckbox.setFont(sequenceCheckbox.getFont().deriveFont(Font.PLAIN, 11f));
        sequenceCheckbox.setFocusPainted(false);
        sequenceCheckbox.setToolTipText("When enabled, 4 valid tiles are chosen from 2/4/6/8-step walk-distance bands. In multiplayer, shared unmarked tiles are preferred first, then each client falls back to local unmarked tiles. Color a tile to get 4 new options. Must color all tiles to complete the level.");
        sequenceCheckbox.setEnabled(plugin.canEditSequenceMode());
        sequenceCheckbox.addActionListener(event -> plugin.setSequenceModeEnabled(sequenceCheckbox.isSelected()));

        addDisablersCheckbox = new JCheckBox("Add Disablers");
        addDisablersCheckbox.setSelected(plugin.isAddDisablersEnabled());
        addDisablersCheckbox.setForeground(TEXT_COLOR);
        addDisablersCheckbox.setBackground(SECTION_BACKGROUND);
        addDisablersCheckbox.setFont(addDisablersCheckbox.getFont().deriveFont(Font.PLAIN, 11f));
        addDisablersCheckbox.setFocusPainted(false);
        addDisablersCheckbox.setToolTipText("When enabled, the level will spawn disabled tiles during play.");
        addDisablersCheckbox.setEnabled(plugin.canEditSequenceMode() && !plugin.isHardModeEnabled());
        addDisablersCheckbox.addActionListener(event -> plugin.setAddDisablersEnabled(addDisablersCheckbox.isSelected()));

        dangerTilesCheckbox = new JCheckBox("Danger Tiles");
        dangerTilesCheckbox.setSelected(plugin.isDangerTilesEnabled());
        dangerTilesCheckbox.setForeground(TEXT_COLOR);
        dangerTilesCheckbox.setBackground(SECTION_BACKGROUND);
        dangerTilesCheckbox.setFont(dangerTilesCheckbox.getFont().deriveFont(Font.PLAIN, 11f));
        dangerTilesCheckbox.setFocusPainted(false);
        dangerTilesCheckbox.setToolTipText("Spawns yellow countdown tiles in batches of 3 within 10 tiles of you, while leaving at least 4 safe tiles and capping at 9 active tiles. Hard Mode forces this on.");
        dangerTilesCheckbox.setEnabled(plugin.canEditSequenceMode() && !plugin.isHardModeEnabled());
        dangerTilesCheckbox.addActionListener(event -> plugin.setDangerTilesEnabled(dangerTilesCheckbox.isSelected()));

        directionalTilesCheckbox = new JCheckBox("Directional Tiles");
        directionalTilesCheckbox.setSelected(plugin.isDirectionalTilesEnabled());
        directionalTilesCheckbox.setForeground(TEXT_COLOR);
        directionalTilesCheckbox.setBackground(SECTION_BACKGROUND);
        directionalTilesCheckbox.setFont(directionalTilesCheckbox.getFont().deriveFont(Font.PLAIN, 11f));
        directionalTilesCheckbox.setFocusPainted(false);
        directionalTilesCheckbox.setToolTipText("Spawns green direction labels on unmarked tiles. Up to 2 can be active at once, and you must step onto the tile from the marked side. Hard Mode forces this on.");
        directionalTilesCheckbox.setEnabled(plugin.canEditSequenceMode() && !plugin.isHardModeEnabled());
        directionalTilesCheckbox.addActionListener(event -> plugin.setDirectionalTilesEnabled(directionalTilesCheckbox.isSelected()));

        hardModeCheckbox = new JCheckBox("Hard Mode");
        hardModeCheckbox.setSelected(plugin.isHardModeEnabled());
        hardModeCheckbox.setForeground(TEXT_COLOR);
        hardModeCheckbox.setBackground(SECTION_BACKGROUND);
        hardModeCheckbox.setFont(hardModeCheckbox.getFont().deriveFont(Font.PLAIN, 11f));
        hardModeCheckbox.setFocusPainted(false);
        hardModeCheckbox.setToolTipText("When enabled, disablers are temporary with a countdown, Directional Tiles and Danger Tiles are forced on, in sequence mode valid tiles shrink, and in normal mode power-up tiles appear.");
        hardModeCheckbox.setEnabled(plugin.canEditSequenceMode());
        hardModeCheckbox.addActionListener(event -> plugin.setHardModeEnabled(hardModeCheckbox.isSelected()));

        JPanel modRow = new JPanel(new GridLayout(1, 2, 4, 0));
        modRow.setBackground(SECTION_BACKGROUND);
        JPanel inner = new JPanel(new GridLayout(5, 1, 4, 0));
        inner.setBackground(SECTION_BACKGROUND);
        inner.add(sequenceCheckbox);
        inner.add(addDisablersCheckbox);
        inner.add(dangerTilesCheckbox);
        inner.add(directionalTilesCheckbox);
        inner.add(hardModeCheckbox);
        modRow.add(inner);

        centerPanel.add(modRow, BorderLayout.SOUTH);
        panel.add(centerPanel, BorderLayout.CENTER);
        panel.add(levelCreationPanel, BorderLayout.SOUTH);
        updateLevelCreationControls();
        return panel;
    }

    private JPanel actionPanel()
    {
        JPanel outer = new JPanel(new GridLayout(3, 1, 0, 6));
        outer.setBackground(PANEL_BACKGROUND);
        outer.setBorder(BorderFactory.createEmptyBorder(0, 0, 8, 0));
        outer.setMaximumSize(new Dimension(Integer.MAX_VALUE, 128));

        JPanel topRow = new JPanel(new GridLayout(1, 3, 5, 0));
        topRow.setBackground(PANEL_BACKGROUND);

        paintButton = actionButton("Paint");
        paintButton.addActionListener(event -> plugin.togglePaintMode());
        JButton highscoresButton = actionButton("Scores");
        highscoresButton.addActionListener(event -> showHighscoresDialog());
        JButton helpButton = actionButton("Help");
        helpButton.addActionListener(event -> plugin.showHowToPlay());

        topRow.add(paintButton);
        topRow.add(highscoresButton);
        topRow.add(helpButton);

        JPanel bottomRow = new JPanel(new GridLayout(1, 3, 5, 0));
        bottomRow.setBackground(PANEL_BACKGROUND);

        clearButton = actionButton("Clear");
        clearButton.addActionListener(event -> plugin.clearPaintedTiles());

        JButton importButton = actionButton("Import");
        importButton.addActionListener(event -> plugin.importLevelFromClipboard());
        JButton exportButton = actionButton("Export");
        exportButton.addActionListener(event -> plugin.showExportLevelPicker());

        bottomRow.add(importButton);
        bottomRow.add(exportButton);
        bottomRow.add(clearButton);

        JPanel resetRow = new JPanel(new GridLayout(1, 1, 5, 0));
        resetRow.setBackground(PANEL_BACKGROUND);
        JButton resetViewButton = actionButton("Fix Camera");
        resetViewButton.setBackground(new Color(255, 64, 64));
        resetViewButton.setForeground(Color.WHITE);
        resetViewButton.setFont(resetViewButton.getFont().deriveFont(Font.BOLD, 13f));
        resetViewButton.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(new Color(160, 0, 0), 2),
                BorderFactory.createEmptyBorder(5, 4, 5, 4)
        ));
        resetViewButton.addActionListener(event -> plugin.hardSceneReset("manual panel reset"));
        resetRow.add(resetViewButton);

        outer.add(topRow);
        outer.add(bottomRow);
        outer.add(resetRow);

        return outer;
    }

    private JPanel multiplayerStatusPanel()
    {
        multiplayerPanel.setLayout(new BoxLayout(multiplayerPanel, BoxLayout.Y_AXIS));
        multiplayerPanel.setBorder(BorderFactory.createEmptyBorder(0, 0, 0, 0));
        updateMultiplayerControls();
        return multiplayerPanel;
    }

    private void updateMultiplayerControls()
    {
        multiplayerPanel.removeAll();
        multiplayerPanel.setLayout(new BoxLayout(multiplayerPanel, BoxLayout.Y_AXIS));
        multiplayerPanel.setBackground(SECTION_BACKGROUND);

        if (!plugin.isInMultiplayerLobby())
        {
            JButton startMultiplayerButton = actionButton("Start Multiplayer");
            startMultiplayerButton.setBackground(new Color(72, 220, 121));
            startMultiplayerButton.setBorder(BorderFactory.createCompoundBorder(
                    BorderFactory.createLineBorder(new Color(31, 140, 63), 2),
                    BorderFactory.createEmptyBorder(5, 4, 5, 4)
            ));
            startMultiplayerButton.addActionListener(event -> plugin.showMultiplayerInviteDialog(this));
            startMultiplayerButton.setAlignmentX(Component.LEFT_ALIGNMENT);
            multiplayerPanel.add(startMultiplayerButton);
            multiplayerPanel.add(javax.swing.Box.createVerticalStrut(8));
        }

        JLabel status = new JLabel(plugin.getMultiplayerStatusLabel());
        status.setForeground(MUTED_TEXT_COLOR);
        status.setFont(status.getFont().deriveFont(Font.BOLD, 11f));
        status.setAlignmentX(Component.LEFT_ALIGNMENT);
        multiplayerPanel.add(status);
        multiplayerPanel.add(javax.swing.Box.createVerticalStrut(8));

        if (plugin.isInMultiplayerLobby())
        {
            String levelName = plugin.getMultiplayerLevelName();
            boolean canPreview = levelName != null && !levelName.isEmpty() && plugin.getGroups().containsKey(levelName);
            JButton previewButton = iconButton(
                    plugin.isViewingGroup(levelName) ? "Hide Preview" : "Preview",
                    "Show or hide the multiplayer level preview",
                    () -> plugin.toggleViewGroup(levelName)
            );
            previewButton.setEnabled(canPreview);
            previewButton.setAlignmentX(Component.LEFT_ALIGNMENT);
            multiplayerPanel.add(previewButton);
            multiplayerPanel.add(javax.swing.Box.createVerticalStrut(8));
        }

        if (plugin.isInMultiplayerLobby() && !plugin.isMultiplayerActive())
        {
            JPanel lobbyActions = new JPanel(new GridLayout(1, plugin.isMultiplayerHost() ? 2 : 1, 5, 0));
            lobbyActions.setBackground(SECTION_BACKGROUND);
            lobbyActions.setAlignmentX(Component.LEFT_ALIGNMENT);

            if (plugin.canStartMultiplayerGame())
            {
                lobbyActions.add(iconButton("Start Multiplayer", "Start the hosted multiplayer game", plugin::startMultiplayerGame));
            }

            if (plugin.isMultiplayerHost())
            {
                lobbyActions.add(iconButton("Close Lobby", "Close the hosted multiplayer lobby for everyone", plugin::closeMultiplayerLobby));
            }
            else
            {
                lobbyActions.add(iconButton("Leave Lobby", "Leave the current multiplayer lobby", plugin::leaveMultiplayerLobby));
            }

            multiplayerPanel.add(lobbyActions);
            multiplayerPanel.add(javax.swing.Box.createVerticalStrut(8));
        }

        List<TileGameMultiplayerMessage> invites = plugin.getPendingMultiplayerInvites();
        if (!invites.isEmpty())
        {
            JPanel invitePanel = new JPanel();
            invitePanel.setLayout(new BoxLayout(invitePanel, BoxLayout.Y_AXIS));
            invitePanel.setBackground(SECTION_BACKGROUND);
            invitePanel.setAlignmentX(Component.LEFT_ALIGNMENT);

            for (int i = 0; i < invites.size(); i++)
            {
                TileGameMultiplayerMessage invite = invites.get(i);
                String host = invite.host == null || invite.host.trim().isEmpty() ? "unknown" : invite.host.trim();
                JButton inviteButton = iconButton(
                        "Invite Pending [" + host + "]",
                        "View invite from " + host,
                        () -> plugin.showPendingMultiplayerInvite(invite, this)
                );
                inviteButton.setAlignmentX(Component.LEFT_ALIGNMENT);
                invitePanel.add(inviteButton);
                if (i < invites.size() - 1)
                {
                    invitePanel.add(javax.swing.Box.createVerticalStrut(6));
                }
            }

            multiplayerPanel.add(invitePanel);
        }

        multiplayerPanel.revalidate();
        multiplayerPanel.repaint();
    }

    private JButton actionButton(String text)
    {
        JButton button = new JButton(text);
        button.setBackground(ACTION_BACKGROUND);
        button.setForeground(Color.BLACK);
        button.setFont(button.getFont().deriveFont(Font.BOLD, 12f));
        button.setFocusPainted(false);
        button.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(ACTION_BORDER, 1),
                BorderFactory.createEmptyBorder(5, 4, 5, 4)
        ));
        return button;
    }

    private CollapsibleCard card(String title, Component component, String helpText)
    {
        CollapsibleCard card = new CollapsibleCard(title, component, helpText, false, null);
        card.setAlignmentX(Component.LEFT_ALIGNMENT);
        card.setMaximumSize(new Dimension(Integer.MAX_VALUE, Integer.MAX_VALUE));
        return card;
    }

    private CollapsibleCard card(String title, Component component, String helpText, boolean initiallyCollapsed, Consumer<Boolean> collapseListener)
    {
        CollapsibleCard card = new CollapsibleCard(title, component, helpText, initiallyCollapsed, collapseListener);
        card.setAlignmentX(Component.LEFT_ALIGNMENT);
        card.setMaximumSize(new Dimension(Integer.MAX_VALUE, Integer.MAX_VALUE));
        return card;
    }

    private Component emptyLevelsMessage()
    {
        JTextArea textArea = textArea("No levels saved yet. Press +, click tiles, then save your first route.");
        textArea.setMaximumSize(new Dimension(Integer.MAX_VALUE, 48));
        return textArea;
    }

    private void updateLevelCreationControls()
    {
        levelCreationPanel.removeAll();
        levelCreationPanel.setLayout(new BoxLayout(levelCreationPanel, BoxLayout.Y_AXIS));

        if (plugin.getMode() == TileGameMode.CHOOSE || plugin.getMode() == TileGameMode.EDIT)
        {
            JPanel actionRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 0));
            actionRow.setBackground(SECTION_BACKGROUND);
            JButton saveButton = iconButton("💾 Save", "Save selected tiles as a level", this::saveCurrentSelection);
            JButton trashButton = iconButton("🗑 Delete", "Trash current level selection", plugin::trashCurrentLevelSelection);
            boolean controlsEnabled = plugin.canUseLevelPanelControls();
            saveButton.setEnabled(controlsEnabled);
            trashButton.setEnabled(controlsEnabled);
            actionRow.add(saveButton);
            actionRow.add(trashButton);

            TileModifier activeTool = plugin.getActiveModifierTool();
            JPanel modifierRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 2));
            modifierRow.setBackground(SECTION_BACKGROUND);
            JButton bonusButton = modifierToolButton("★ Bonus", TileModifier.BONUS, activeTool);
            bonusButton.setEnabled(controlsEnabled);
            modifierRow.add(bonusButton);

            levelCreationPanel.add(actionRow);
            levelCreationPanel.add(modifierRow);
        }
        else
        {
            JPanel actionRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 0));
            actionRow.setBackground(SECTION_BACKGROUND);
            JButton newButton = iconButton("+ New", "Create a new level", plugin::startNewLevelSelection);
            newButton.setEnabled(plugin.canUseLevelPanelControls());
            actionRow.add(newButton);
            levelCreationPanel.add(actionRow);
        }

        levelCreationPanel.revalidate();
        levelCreationPanel.repaint();
    }

        private JButton modifierToolButton(String text, TileModifier modifier, TileModifier activeTool)
        {
            JButton button = new JButton(text);
            boolean isActive = activeTool == modifier;
            button.setToolTipText(
                    modifier == TileModifier.BONUS
                            ? (isActive ? "Bonus tool active — click tiles to toggle bonus (auto-paints random tile on step)"
                                         : "Click to use Bonus tool — apply bonus modifier to tiles")
                            : (isActive ? "Disable tool active — click tiles to toggle disable (randomly blocks painting)"
                                        : "Click to use Disable tool — apply disable modifier to tiles")
                        );
            button.setBackground(isActive ? new Color(46, 180, 152) : ACTION_BACKGROUND);
            button.setForeground(Color.BLACK);
            button.setFont(button.getFont().deriveFont(Font.BOLD, 10f));
            button.setFocusPainted(false);
            button.setBorder(BorderFactory.createCompoundBorder(
                    BorderFactory.createLineBorder(isActive ? Color.GREEN : ACTION_BORDER, 2),
                    BorderFactory.createEmptyBorder(2, 5, 2, 5)
            ));
            button.addActionListener(event ->
            {
                if (isActive)
                {
                    plugin.clearActiveModifierTool();
                }
                else
                {
                    plugin.setActiveModifierTool(modifier);
                }
            });
            return button;
        }

    private void saveCurrentSelection()
    {
        if (plugin.getMode() == TileGameMode.EDIT)
        {
            plugin.saveSelectedLevel(plugin.getActiveGroupName());
            return;
        }

        String name = JOptionPane.showInputDialog(this, "Name this level:", "Save Tile Racer Level", JOptionPane.PLAIN_MESSAGE);
        if (name != null && !name.trim().isEmpty())
        {
            plugin.saveSelectedLevel(name);
        }
    }

    private JPanel levelRow(String groupName)
    {
        JPanel row = new JPanel(new BorderLayout(8, 0));
        row.setBackground(ROW_BACKGROUND);
        row.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(0, 0, 1, 0, HEADER_BACKGROUND),
                BorderFactory.createEmptyBorder(6, 8, 6, 6)
        ));
        row.setMaximumSize(new Dimension(Integer.MAX_VALUE, 42));
        row.setAlignmentX(Component.LEFT_ALIGNMENT);

        JLabel label = new JLabel("✦ " + groupName);
        label.setForeground(GOLD);
        label.setFont(label.getFont().deriveFont(Font.BOLD | Font.ITALIC, 13f));

        JPanel actions = new JPanel(new GridLayout(1, 4, 4, 0));
        actions.setOpaque(false);
        JButton viewButton = iconButton(
                plugin.isViewingGroup(groupName) ? "◌" : "👁",
                (plugin.isViewingGroup(groupName) ? "Hide " : "View ") + groupName,
                () -> plugin.toggleViewGroup(groupName)
        );
        JButton playButton = iconButton("▶", "Play " + groupName, () -> plugin.playGroup(groupName));
        JButton editButton = iconButton("✎", "Edit " + groupName, () -> plugin.editGroup(groupName));
        JButton deleteButton = iconButton("×", "Delete " + groupName, () -> {
            int res = JOptionPane.showConfirmDialog(TileGamePanel.this,
                    "Delete level '" + groupName + "'? This cannot be undone.",
                    "Confirm delete",
                    JOptionPane.YES_NO_OPTION,
                    JOptionPane.WARNING_MESSAGE);

            if (res == JOptionPane.YES_OPTION)
            {
                plugin.deleteGroup(groupName);
            }
        });

        boolean controlsEnabled = plugin.canUseLevelPanelControls();
        viewButton.setEnabled(controlsEnabled);
        playButton.setEnabled(controlsEnabled);
        editButton.setEnabled(controlsEnabled);
        deleteButton.setEnabled(controlsEnabled);

        actions.add(viewButton);
        actions.add(playButton);
        actions.add(editButton);
        actions.add(deleteButton);

        levelActionButtons.put(groupName, List.of(viewButton, playButton, editButton, deleteButton));

        row.add(label, BorderLayout.CENTER);
        row.add(actions, BorderLayout.EAST);
        return row;
    }

    private void updateLevelActionButtons()
    {
        boolean enabled = plugin.canUseLevelPanelControls();
        levelActionButtons.values().forEach(buttons ->
        {
            for (JButton button : buttons)
            {
                button.setEnabled(enabled);
            }
        });
    }

    private JButton iconButton(String text, String tooltip, Runnable action)
    {
        JButton button = new JButton(text);
        button.setToolTipText(tooltip);
        button.setBackground(ACTION_BACKGROUND);
        button.setForeground(Color.BLACK);
        button.setFont(button.getFont().deriveFont(Font.BOLD, 11f));
        button.setFocusPainted(false);
        button.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(ACTION_BORDER, 1),
                BorderFactory.createEmptyBorder(2, 5, 2, 5)
        ));
        button.addActionListener(event -> action.run());
        return button;
    }

    private void addRow(JPanel panel, String label, JLabel value)
    {
        JLabel labelComponent = new JLabel(label);
        labelComponent.setForeground(MUTED_TEXT_COLOR);

        panel.add(labelComponent);
        panel.add(value);
    }

    private static JLabel valueLabel()
    {
        JLabel label = new JLabel("-");
        label.setForeground(TEXT_COLOR);
        label.setFont(label.getFont().deriveFont(Font.BOLD));
        return label;
    }

    private static JTextArea textArea(String text)
    {
        JTextArea textArea = new JTextArea(text);
        textArea.setEditable(false);
        textArea.setLineWrap(true);
        textArea.setWrapStyleWord(true);
        textArea.setBackground(SECTION_BACKGROUND);
        textArea.setForeground(TEXT_COLOR);
        textArea.setBorder(BorderFactory.createEmptyBorder());
        return textArea;
    }

    private JTable highscoreTable(String groupName, TileGameResult.ScoreMode scoreMode)
    {
        String[] columns = {"Rank", "Time"};
        DefaultTableModel model = new DefaultTableModel(columns, 0)
        {
            @Override
            public boolean isCellEditable(int row, int column)
            {
                return false;
            }
        };

        ArrayList<TileGameResult> results = new ArrayList<>();
        Iterable<TileGameResult> savedResults = plugin.getHighscores().get(groupName);
        if (savedResults != null)
        {
            savedResults.forEach(result ->
            {
                if (result.getScoreMode() == scoreMode)
                {
                    results.add(result);
                }
            });
        }
        results.sort(Comparator.comparingInt(result -> result.totalTicks));

        int limit = results.size();
        for (int i = 0; i < limit; i++)
        {
            TileGameResult result = results.get(i);
            model.addRow(new Object[]{
                    i + 1,
                    result.formattedSeconds(),
            });
        }

        if (limit == 0)
        {
            model.addRow(new Object[]{"-", "No " + scoreMode.displayName() + " completions yet"});
        }

        JTable table = new JTable(model);
        table.setFillsViewportHeight(true);
        table.setRowHeight(24);
        table.getTableHeader().setReorderingAllowed(false);
        return table;
    }

    private String[] sortedGroupNames()
    {
        return plugin.getGroups().keySet().stream()
                .sorted()
                .toArray(String[]::new);
    }

    private String blankToDash(String value)
    {
        return value == null || value.isEmpty() ? "-" : value;
    }

    private void setInteractiveComponentsEnabled(Component component, boolean enabled)
    {
        if (component instanceof AbstractButton)
        {
            component.setEnabled(enabled);
        }
        else if (component instanceof JComboBox)
        {
            component.setEnabled(enabled);
        }

        if (component instanceof Container)
        {
            for (Component child : ((Container) component).getComponents())
            {
                setInteractiveComponentsEnabled(child, enabled);
            }
        }
    }

    private void showCardHelp(String title, String helpText)
    {
        JTextArea textArea = textArea(helpText);
        textArea.setBackground(ROW_BACKGROUND);
        JScrollPane scrollPane = new JScrollPane(textArea);
        scrollPane.setPreferredSize(new Dimension(520, 260));
        JOptionPane.showMessageDialog(this, scrollPane, title + " Help", JOptionPane.PLAIN_MESSAGE);
    }

    private final class CollapsibleCard extends JPanel
    {
        private final String title;
        private final JButton toggleButton;
        private final JPanel body;
        private final Consumer<Boolean> collapseListener;
        private boolean collapsed;

        private CollapsibleCard(String title, Component component, String helpText, boolean initiallyCollapsed, Consumer<Boolean> collapseListener)
        {
            super(new BorderLayout(0, 6));
            this.title = title;
            this.collapseListener = collapseListener;
            setBackground(SECTION_BACKGROUND);
            setAlignmentX(Component.LEFT_ALIGNMENT);
            setMaximumSize(new Dimension(Integer.MAX_VALUE, Integer.MAX_VALUE));
            setBorder(BorderFactory.createCompoundBorder(
                    BorderFactory.createLineBorder(CYAN, 1),
                    BorderFactory.createEmptyBorder(8, 8, 8, 8)
            ));

            collapsed = initiallyCollapsed;
            toggleButton = new JButton((collapsed ? "> " : "v ") + title);
            toggleButton.setHorizontalAlignment(JButton.LEFT);
            toggleButton.setBackground(HEADER_BACKGROUND);
            toggleButton.setForeground(GOLD);
            toggleButton.setFocusPainted(false);
            toggleButton.setFont(toggleButton.getFont().deriveFont(Font.BOLD, 13f));
            toggleButton.addActionListener(event -> toggle());

            body = new JPanel(new BorderLayout(0, 6));
            body.setBackground(SECTION_BACKGROUND);
            body.add(component, BorderLayout.CENTER);

            // Only show a help button when help text is provided
            if (helpText != null && !helpText.trim().isEmpty())
            {
                JButton helpButton = new JButton("?");
                helpButton.setBackground(GOLD);
                helpButton.setForeground(Color.BLACK);
                helpButton.setFocusPainted(false);
                helpButton.setFont(helpButton.getFont().deriveFont(Font.BOLD, 11f));
                helpButton.addActionListener(event -> showCardHelp(title, helpText));

                JPanel helpRow = new JPanel(new BorderLayout());
                helpRow.setBackground(SECTION_BACKGROUND);
                helpRow.add(helpButton, BorderLayout.EAST);
                body.add(helpRow, BorderLayout.SOUTH);
            }

            body.setVisible(!collapsed);
            add(toggleButton, BorderLayout.NORTH);
            add(body, BorderLayout.CENTER);
        }

        private void toggle()
        {
            collapsed = !collapsed;
            body.setVisible(!collapsed);
            toggleButton.setText((collapsed ? "> " : "v ") + title);
            if (collapseListener != null)
            {
                collapseListener.accept(collapsed);
            }
            revalidate();
            repaint();
        }
    }
}
