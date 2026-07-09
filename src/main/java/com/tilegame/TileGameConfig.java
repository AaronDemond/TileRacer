package com.tilegame;

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
}