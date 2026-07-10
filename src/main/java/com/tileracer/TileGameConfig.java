package com.tileracer;

import java.awt.Color;
import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigItem;

@ConfigGroup("tilegame")
public interface TileGameConfig extends Config
{
	enum PaintMode
	{
		RANDOM,
		STATIC
	}

	@ConfigItem(
		keyName = "paintMode",
		name = "Paint Mode",
		description = "Random: each tile gets a unique color. Static: all colored tiles use the Default Color.",
		position = 0
	)
	default PaintMode paintMode()
	{
		return PaintMode.RANDOM;
	}

	@ConfigItem(
		keyName = "defaultColor",
		name = "Default Color",
		description = "Color used for all tiles when Paint Mode is set to Static.",
		position = 1
	)
	default Color defaultColor()
	{
		return Color.BLACK;
	}

	@ConfigItem(
		keyName = "controlsCollapsed",
		name = "Collapse Controls",
		description = "Remember whether the Controls section is collapsed.",
		position = 2
	)
	default boolean controlsCollapsed()
	{
		return false;
	}

	@ConfigItem(
		keyName = "multiplayerCollapsed",
		name = "Collapse Multiplayer",
		description = "Remember whether the Multiplayer section is collapsed.",
		position = 3
	)
	default boolean multiplayerCollapsed()
	{
		return false;
	}

	@ConfigItem(
		keyName = "gameStateCollapsed",
		name = "Collapse Game State",
		description = "Remember whether the Game State section is collapsed.",
		position = 4
	)
	default boolean gameStateCollapsed()
	{
		return false;
	}

	@ConfigItem(
		keyName = "levelsCollapsed",
		name = "Collapse Levels",
		description = "Remember whether the Levels section is collapsed.",
		position = 5
	)
	default boolean levelsCollapsed()
	{
		return false;
	}
}