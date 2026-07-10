# Introduction


TileRacer is a plugin where you are challenged to color all the tiles in various configurations as fast as you can by stepping on them. There are many mechanics and modifiers that make this more difficult than it sounds. You can play by yourself or compete against friends in real time. Anyone can create and share new configurations, and your client tracks your highscores as you improve.

Note: The server configuration can be found on the same Github profile as the plugin source code. No data about you is stored.

# Requqirements and limitations.

- You must have either the GPU plugin enabled or the 117 HD plugin enabled. Without it, the plugin does not allow you to do anything.
- If the camera ever breaks (rotating the screen feels off) click the yellow "Reset Camera" button in the game state card. This will be fixed perminately in a later release.

# Configuring a Level for Playu

- Click the "New" button in the levels panel.
- Click on tiles to mark them as part of the circuit. Marked tiles will appear black.
    - Click on marked tiles again to unmark them.
    - Hold Ctrl to move your character.
    - Click the "Bonus" button to place a special tile that rewards the player with multiple colored tiles. Bonus tiles are not required to finish the game.
- Click Save to save the layout, or Delete to discard it. After saving, each level has these four options next to it:
    - "Eye" lets you preview the level.
    - "Play" starts a game on that level.
    - "Pencil" allows you to make changes to a level.
    - "X" removes a level.

# Playing a Game

There are two ways to play: solo and with friends. To play alone, configure your mechanics using the checkboxes in the levels card, then click the Play button next to the level you want to play.

To play with friends, one person, the player hosting the match, clicks Multiplayer and chooses the level and all friends who will receive an invite to the game. Players will then see an "Invite Pending" button appear in the multiplayer panel. They can accept or decline. Once the host is satisfied that all participants have joined, the host clicks "Start Multiplayer" and a live, synced game begins between all players. All mechanics and modifiers will act the same for every player, so nothing is left up to RNG. The game ends when someone colors all the tiles, at which point the winner is broadcast to all players.

If at any point you want to leave a game, click the Stop button in the game state card. If the host ends the game, it stops for everybody.

# Game Mechanics

There are a number of mechanics you can enable before starting a match.

- Sequence Mode
    - This mode forces you to choose between a small number of tiles that disappear quickly. Valid tiles have a number on them that counts down every tick. When it reaches 0, that tile will no longer be a valid move. When you play, you must prioritize coloring tiles quickly, but you also have to be careful not to try to reach one you cannot get to in time.
- Disablers
    - Disablers are special modifiers that randomly appear, indicated by a red border. They prevent you from coloring the tile they spawn on. Disablers must be cleared by stepping on the blue-bordered tile that appears.
- Danger Tiles
    - Danger tiles are first indicated by a small countdown in yellow text. When the countdown reaches zero, the whole tile turns yellow. Once it is fully yellow, the danger tile is active. For every tick you are on a danger tile, one of your previously colored tiles will become uncolored, and you will have to color it again.
- Directional Tiles
    - Directional tiles are indicated by green text representing a direction, such as NW for north-west. These tiles can only be colored by walking onto them from that direction.
- Hard Mode
    - Hard Mode automatically enables Disablers, Danger Tiles, and Directional Tiles. Enabling Hard Mode also puts your score in a different category, separate from normal runs.

# Other Plugin Functions

- Export
    - Export copies data to your system clipboard that defines a level you choose. If you send this data to another user, they can import your level.
- Import
    - Import reads data from the clipboard, and if it is a valid level design, the level will be added to your plugin.
- Paint
    - Paint allows you to freely paint tiles as you walk around the game. There are no scores or modifiers with this, just colorful tile vomit.
- Clear
    - Clear removes any tiles you have painted with the Paint command.
- Scores
    - Shows the highscores.
- Help
    - Shows this help text.