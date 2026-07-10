package com;

import com.tileracer.TileGamePlugin;
import net.runelite.client.RuneLite;
import net.runelite.client.externalplugins.ExternalPluginManager;

public class TileRacerPluginTest
{
    public static void main(String[] args) throws Exception
    {
        ExternalPluginManager.loadBuiltin(
                TileGamePlugin.class
        );
        RuneLite.main(args);
    }
}