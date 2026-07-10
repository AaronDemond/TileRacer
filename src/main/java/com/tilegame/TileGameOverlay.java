package com.tilegame;

import java.awt.AlphaComposite;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Composite;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.Polygon;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.Shape;
import java.awt.Stroke;
import java.awt.geom.Area;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import javax.inject.Inject;
import net.runelite.api.Actor;
import net.runelite.api.Client;
import net.runelite.api.Model;
import net.runelite.api.NPC;
import net.runelite.api.Perspective;
import net.runelite.api.Player;
import net.runelite.api.WorldView;
import net.runelite.api.coords.LocalPoint;
import net.runelite.api.coords.WorldPoint;
import net.runelite.client.ui.overlay.Overlay;
import net.runelite.client.ui.overlay.OverlayLayer;
import net.runelite.client.ui.overlay.OverlayPosition;

public class TileGameOverlay extends Overlay
{
    private final Client client;
    private final TileGamePlugin plugin;

    @Inject
    TileGameOverlay(Client client, TileGamePlugin plugin)
    {
        this.client = client;
        this.plugin = plugin;

        setPosition(OverlayPosition.DYNAMIC);
        setLayer(OverlayLayer.ABOVE_SCENE);
    }

    @Override
    public String getName()
    {
        return TileGamePlugin.TILE_OVERLAY_NAME;
    }

    @Override
    public Dimension render(Graphics2D graphics)
    {
        if (!plugin.isRendererReady())
        {
            return null;
        }

        List<Area> npcMasks = buildNpcMasks();

        for (WorldPoint worldPoint : plugin.getViewedLevelTiles())
        {
            drawTile(graphics, npcMasks, worldPoint, Color.BLACK);
        }

        // When viewing a level, also render its modifiers so they are visible during view mode
        Map<WorldPoint, TileModifier> viewedModifiers = plugin.getModifiersForViewedGroups();
        if (viewedModifiers != null && !viewedModifiers.isEmpty())
        {
            for (Map.Entry<WorldPoint, TileModifier> entry : viewedModifiers.entrySet())
            {
                Color borderColor;
                switch (entry.getValue())
                {
                    case BONUS: borderColor = Color.GREEN; break;
                    case DISABLE: borderColor = Color.RED; break;
                    default: borderColor = Color.WHITE; break;
                }
                drawTileBorder(graphics, npcMasks, entry.getKey(), borderColor);
            }
        }

        if (plugin.getMode() == TileGameMode.COUNTDOWN || plugin.getMode() == TileGameMode.LEVEL)
        {
            for (WorldPoint worldPoint : plugin.getActiveLevelGroupTiles())
            {
                if (!plugin.getRunColoredTiles().containsKey(worldPoint))
                {
                    drawTile(graphics, npcMasks, worldPoint, Color.BLACK);
                }
            }

                                    Set<WorldPoint> validSeqTiles = plugin.getSequenceOrder();
                                    Map<WorldPoint, Integer> bonusTiles = plugin.getSequenceHardBonusTiles();
                                    if (!validSeqTiles.isEmpty())
                                    {
                                        Map<WorldPoint, Integer> timers = plugin.getSequenceTileTimers();
                                        int seqNum = plugin.getCurrentSequenceNumber();
                                        for (WorldPoint wp : validSeqTiles)
                                        {
                                            if (!plugin.getRunColoredTiles().containsKey(wp) && !bonusTiles.containsKey(wp))
                                            {
                                               int label = timers.getOrDefault(wp, seqNum);
                                               drawSequenceNumber(graphics, wp, Math.max(1, label));
                                            }
                                        }
                                    }

            if (plugin.isHardMode() && plugin.isSequenceModeEnabled())
            {
                for (Map.Entry<WorldPoint, Integer> entry : bonusTiles.entrySet())
                {
                    if (!plugin.getRunColoredTiles().containsKey(entry.getKey()))
                    {
                        drawNumber(graphics, entry.getKey(), entry.getValue(), new Color(255, 105, 180));
                    }
                }
            }
        }

        for (WorldPoint worldPoint : plugin.getSelectedTiles())
        {
            drawTile(graphics, npcMasks, worldPoint, Color.BLACK);
        }


        // Draw reset tile borders (blue)
        for (Map.Entry<WorldPoint, WorldPoint> entry : plugin.getResetTiles().entrySet())
        {
            drawTileBorder(graphics, npcMasks, entry.getValue(), Color.BLUE);
        }

        for (Map.Entry<WorldPoint, Color> entry : plugin.getPaintedTiles().entrySet())
        {
            drawTile(graphics, npcMasks, entry.getKey(), entry.getValue());
        }

        for (Map.Entry<WorldPoint, Color> entry : plugin.getRunColoredTiles().entrySet())
        {
            drawTile(graphics, npcMasks, entry.getKey(), entry.getValue());
        }

        Map<WorldPoint, Integer> dangerCountdowns = plugin.getDangerTileCountdowns();
        Map<WorldPoint, Integer> dangerActiveTimers = plugin.getDangerTileActiveTimers();
        if ((!dangerCountdowns.isEmpty() || !dangerActiveTimers.isEmpty()) && plugin.getMode() == TileGameMode.LEVEL)
        {
            for (Map.Entry<WorldPoint, Integer> entry : dangerCountdowns.entrySet())
            {
                drawNumber(graphics, entry.getKey(), entry.getValue(), new Color(255, 235, 59));
            }

            for (Map.Entry<WorldPoint, Integer> entry : dangerActiveTimers.entrySet())
            {
                drawTile(graphics, npcMasks, entry.getKey(), new Color(255, 235, 59));
                drawText(graphics, entry.getKey(), "Danger", Color.BLACK);
            }
        }

        Map<WorldPoint, String> directionalTiles = plugin.getDirectionalTileDirections();
        if (!directionalTiles.isEmpty() && plugin.getMode() == TileGameMode.LEVEL)
        {
            for (Map.Entry<WorldPoint, String> entry : directionalTiles.entrySet())
            {
                drawText(graphics, entry.getKey(), entry.getValue(), Color.GREEN);
            }
        }

        // Render modifier borders after tile fills so borders remain visible on top
        Map<WorldPoint, TileModifier> activeModifiers = plugin.getModifiersForActiveGroup();
        Map<WorldPoint, Integer> disabledTimers = plugin.getDisabledTileTimers();

        if (plugin.getMode() == TileGameMode.CHOOSE || plugin.getMode() == TileGameMode.EDIT)
        {
            // Show all modifier borders during editing
            for (Map.Entry<WorldPoint, TileModifier> entry : activeModifiers.entrySet())
            {
                Color borderColor;
                switch (entry.getValue())
                {
                    case BONUS: borderColor = Color.GREEN; break;
                    case DISABLE: borderColor = Color.RED; break;
                    default: borderColor = Color.WHITE; break;
                }
                drawTileBorder(graphics, npcMasks, entry.getKey(), borderColor);
            }
        }
        else if (plugin.getMode() == TileGameMode.COUNTDOWN || plugin.getMode() == TileGameMode.LEVEL)
        {
            // Show bonus borders on unpainted BONUS tiles, and always show red borders for any disabled modifiers
            for (Map.Entry<WorldPoint, TileModifier> entry : activeModifiers.entrySet())
            {
                if (entry.getValue() == TileModifier.BONUS)
                {
                    // Don't show bonus border if the bonus has already been used this run or if painted
                    if (!plugin.getUsedBonusTiles().contains(entry.getKey()) && !plugin.getRunColoredTiles().containsKey(entry.getKey()))
                    {
                        drawTileBorder(graphics, npcMasks, entry.getKey(), Color.GREEN);
                    }
                }
                else if (entry.getValue() == TileModifier.DISABLE)
                {
                    // Always draw red border for DISABLE modifiers regardless of painted state
                    drawTileBorder(graphics, npcMasks, entry.getKey(), Color.RED);
                }
            }

            // Also draw red borders for tiles that are currently disabled by runtime (spawned disablers)
            for (WorldPoint disabled : disabledTimers.keySet())
            {
                drawTileBorder(graphics, npcMasks, disabled, Color.RED);
            }

            // Draw power-up tile numbers in non-sequence hard mode
            if (plugin.isHardMode() && !plugin.isSequenceModeEnabled())
            {
                for (Map.Entry<WorldPoint, Integer> entry : plugin.getPowerUpTiles().entrySet())
                {
                    if (!plugin.getRunColoredTiles().containsKey(entry.getKey()))
                    {
                        drawSequenceNumber(graphics, entry.getKey(), entry.getValue());
                    }
                }
            }
        }

        // Draw reset tile borders (blue)
        for (Map.Entry<WorldPoint, WorldPoint> entry : plugin.getResetTiles().entrySet())
        {
            drawTileBorder(graphics, npcMasks, entry.getValue(), Color.BLUE);
        }

        clearPlayers(graphics);
        return null;
    }

    private List<Area> buildNpcMasks()
    {
        List<Area> actorMasks = new ArrayList<>();
        WorldView worldView = client.getTopLevelWorldView();
        if (worldView == null)
        {
            return actorMasks;
        }

        for (NPC npc : worldView.npcs())
        {
            if (npc == null)
            {
                continue;
            }

            Shape hull = npc.getConvexHull();

            if (hull != null)
            {
                actorMasks.add(new Area(hull));
            }
        }

        return actorMasks;
    }

    private void clearPlayers(Graphics2D graphics)
    {
        WorldView worldView = client.getTopLevelWorldView();
        if (worldView == null)
        {
            return;
        }

        for (Player player : worldView.players())
        {
            clearPlayer(graphics, player);
        }
    }

    private void clearPlayer(Graphics2D graphics, Player player)
    {
        if (player == null)
        {
            return;
        }

        LocalPoint localLocation = player.getLocalLocation();
        WorldView worldView = client.getTopLevelWorldView();
        if (localLocation == null || worldView == null)
        {
            return;
        }

        int localZ = Perspective.getFootprintTileHeight(
                client,
                localLocation,
                worldView.getPlane(),
                player.getFootprintSize()
        ) - player.getAnimationHeightOffset();
        clearActor(graphics, player, localZ);
    }

    private void clearActor(Graphics2D graphics, Actor actor, int localZ)
    {
        if (actor == null)
        {
            return;
        }

        Model model = actor.getModel();
        LocalPoint localLocation = actor.getLocalLocation();
        WorldView actorWorldView = actor.getWorldView();
        if (model == null || localLocation == null || actorWorldView == null)
        {
            return;
        }

        int vertexCount = model.getVerticesCount();
        if (vertexCount <= 0)
        {
            return;
        }

        float[] x3d = model.getVerticesX();
        float[] y3d = model.getVerticesY();
        float[] z3d = model.getVerticesZ();
        if (x3d == null || y3d == null || z3d == null)
        {
            return;
        }

        int[] x2d = new int[vertexCount];
        int[] y2d = new int[vertexCount];

        Perspective.modelToCanvas(
                client,
                actorWorldView,
                vertexCount,
                localLocation.getX(),
                localLocation.getY(),
                localZ,
                actor.getCurrentOrientation(),
                x3d,
                z3d,
                y3d,
                x2d,
                y2d
        );

        int clipX1 = client.getViewportXOffset();
        int clipY1 = client.getViewportYOffset();
        int clipX2 = clipX1 + client.getViewportWidth();
        int clipY2 = clipY1 + client.getViewportHeight();

        boolean anyVisible = false;
        for (int i = 0; i < vertexCount; i++)
        {
            int x = x2d[i];
            int y = y2d[i];
            boolean visibleX = x >= clipX1 && x < clipX2;
            boolean visibleY = y >= clipY1 && y < clipY2;
            anyVisible |= visibleX && visibleY;
        }

        if (!anyVisible)
        {
            return;
        }

        int triangleCount = model.getFaceCount();
        int[] tx = model.getFaceIndices1();
        int[] ty = model.getFaceIndices2();
        int[] tz = model.getFaceIndices3();
        if (triangleCount <= 0 || tx == null || ty == null || tz == null)
        {
            return;
        }

        byte[] triangleTransparencies = model.getFaceTransparencies();
        Object originalAntialiasing = graphics.getRenderingHint(RenderingHints.KEY_ANTIALIASING);
        Composite originalComposite = graphics.getComposite();

        graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_OFF);
        graphics.setComposite(AlphaComposite.Clear);
        graphics.setColor(Color.WHITE);

        for (int i = 0; i < triangleCount; i++)
        {
            if (getTriangleDirection(
                    x2d[tx[i]], y2d[tx[i]],
                    x2d[ty[i]], y2d[ty[i]],
                    x2d[tz[i]], y2d[tz[i]]) >= 0)
            {
                continue;
            }

            if (triangleTransparencies == null || (triangleTransparencies[i] & 255) < 254)
            {
                graphics.fill(new Polygon(
                        new int[]{x2d[tx[i]], x2d[ty[i]], x2d[tz[i]]},
                        new int[]{y2d[tx[i]], y2d[ty[i]], y2d[tz[i]]},
                        3
                ));
            }
        }

        graphics.setComposite(originalComposite);
        graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING, originalAntialiasing);
    }

    private int getTriangleDirection(int x1, int y1, int x2, int y2, int x3, int y3)
    {
        int x4 = x2 - x1;
        int y4 = y2 - y1;
        int x5 = x3 - x1;
        int y5 = y3 - y1;
        return x4 * y5 - y4 * x5;
    }

    private void drawTile(Graphics2D graphics, List<Area> npcMasks, WorldPoint worldPoint, Color color)
    {
        LocalPoint localPoint = LocalPoint.fromWorld(client, worldPoint);

        if (localPoint == null)
        {
            return;
        }

        Polygon poly = Perspective.getCanvasTilePoly(client, localPoint);

        if (poly == null)
        {
            return;
        }

        Area tileArea = new Area(poly);

        for (Area npcMask : npcMasks)
        {
            tileArea.subtract(npcMask);
        }

        graphics.setColor(color);
        graphics.fill(tileArea);
    }

    private void drawSequenceNumber(Graphics2D graphics, WorldPoint worldPoint, int sequenceNumber)
    {
        drawNumber(graphics, worldPoint, sequenceNumber, Color.WHITE);
    }

    private void drawNumber(Graphics2D graphics, WorldPoint worldPoint, int sequenceNumber, Color color)
    {
        LocalPoint localPoint = LocalPoint.fromWorld(client, worldPoint);

        if (localPoint == null)
        {
            return;
        }

        Polygon poly = Perspective.getCanvasTilePoly(client, localPoint);

        if (poly == null)
        {
            return;
        }

        Rectangle bounds = poly.getBounds();
        String text = String.valueOf(sequenceNumber);

        graphics.setFont(new Font("SansSerif", Font.BOLD, 11));
        int textWidth = graphics.getFontMetrics().stringWidth(text);
        int textHeight = graphics.getFontMetrics().getAscent();

        int centerX = bounds.x + (bounds.width - textWidth) / 2;
        int centerY = bounds.y + (bounds.height + textHeight) / 2;

        // Draw the number centered on the tile
        graphics.setColor(color);
        graphics.drawString(text, centerX, centerY);
    }

    private void drawText(Graphics2D graphics, WorldPoint worldPoint, String text, Color color)
    {
        LocalPoint localPoint = LocalPoint.fromWorld(client, worldPoint);

        if (localPoint == null)
        {
            return;
        }

        Polygon poly = Perspective.getCanvasTilePoly(client, localPoint);

        if (poly == null)
        {
            return;
        }

        Rectangle bounds = poly.getBounds();
        graphics.setFont(new Font("SansSerif", Font.BOLD, 11));
        int textWidth = graphics.getFontMetrics().stringWidth(text);
        int textHeight = graphics.getFontMetrics().getAscent();

        int centerX = bounds.x + (bounds.width - textWidth) / 2;
        int centerY = bounds.y + (bounds.height + textHeight) / 2;

        graphics.setColor(color);
        graphics.drawString(text, centerX, centerY);
    }

    private void drawTileBorder(Graphics2D graphics, List<Area> npcMasks, WorldPoint worldPoint, Color borderColor)
    {
        LocalPoint localPoint = LocalPoint.fromWorld(client, worldPoint);

        if (localPoint == null)
        {
            return;
        }

        Polygon poly = Perspective.getCanvasTilePoly(client, localPoint);

        if (poly == null)
        {
            return;
        }

        Area tileArea = new Area(poly);

        for (Area npcMask : npcMasks)
        {
            tileArea.subtract(npcMask);
        }

        Stroke originalStroke = graphics.getStroke();
        graphics.setStroke(new BasicStroke(3));
        graphics.setColor(borderColor);
        graphics.draw(tileArea);
        graphics.setStroke(originalStroke);
    }

    private void drawDisabledCountdown(Graphics2D graphics, WorldPoint worldPoint, int remainingTicks)
    {
        LocalPoint localPoint = LocalPoint.fromWorld(client, worldPoint);

        if (localPoint == null)
        {
            return;
        }

        Polygon poly = Perspective.getCanvasTilePoly(client, localPoint);

        if (poly == null)
        {
            return;
        }

        Rectangle bounds = poly.getBounds();
        String text = String.valueOf(remainingTicks);

        graphics.setFont(new Font("SansSerif", Font.BOLD, 11));
        int textWidth = graphics.getFontMetrics().stringWidth(text);
        int textHeight = graphics.getFontMetrics().getAscent();

        int centerX = bounds.x + (bounds.width - textWidth) / 2;
        int centerY = bounds.y + (bounds.height + textHeight) / 2;

        graphics.setColor(Color.RED);
        graphics.drawString(text, centerX, centerY);
    }
}