package com.example.waypoints;

import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.gui.Font;

/**
 * Live top-down surface map used by the editor and settings previews.
 * It reads only chunks which are already present in the client chunk cache,
 * then samples the WORLD_SURFACE heightmap (not cave/underground blocks).
 */
final class SurfaceMapPreview {
    private static final int MAP_WIDTH = 128;
    private static final int MAP_HEIGHT = 64;
    private static final Identifier MAP_TEXTURE_ID = Identifier.fromNamespaceAndPath(
            "simplewaypoints", "gui/surface_map_preview");

    private static DynamicTexture texture;
    private static int lastCenterX = Integer.MIN_VALUE;
    private static int lastCenterZ = Integer.MIN_VALUE;
    private static String lastDimension = "";
    private static long lastRefreshMillis;
    private static int loadedSamples;
    private static String status = "Map data unavailable";

    private SurfaceMapPreview() { }

    static void draw(GuiGraphicsExtractor g, Font font, int x, int y, int width, int height,
                     int centerX, int centerZ, String requestedDimension, int markerColor) {
        rounded(g, x, y, x + width, y + height, 0xFF111518, 9);
        border(g, x, y, x + width, y + height, 0xFF30383C);
        g.text(font, "SURFACE MAP · TOP DOWN", x + 12, y + 9, 0xFF9BA4A8, false);

        refreshIfNeeded(centerX, centerZ, requestedDimension);

        int mapAreaWidth = Math.max(1, width - 20);
        int mapAreaHeight = Math.max(1, height - 42);
        int drawWidth = Math.min(mapAreaWidth, mapAreaHeight * 2);
        int drawHeight = Math.max(1, drawWidth / 2);
        int mapX = x + (width - drawWidth) / 2;
        int mapY = y + 24 + Math.max(0, (mapAreaHeight - drawHeight) / 2);

        g.fill(mapX - 1, mapY - 1, mapX + drawWidth + 1, mapY + drawHeight + 1, 0xFF050708);
        if (texture != null) {
            g.blit(MAP_TEXTURE_ID, mapX, mapY, mapX + drawWidth, mapY + drawHeight,
                    0.0f, 1.0f, 0.0f, 1.0f);
        }
        border(g, mapX, mapY, mapX + drawWidth, mapY + drawHeight, 0xFF4A555A);

        // Subtle 16-block chunk grid helps orient the top-down surface image.
        for (int i = 1; i < 8; i++) {
            int gx = mapX + drawWidth * i / 8;
            g.fill(gx, mapY, gx + 1, mapY + drawHeight, 0x26394345);
        }
        for (int i = 1; i < 4; i++) {
            int gy = mapY + drawHeight * i / 4;
            g.fill(mapX, gy, mapX + drawWidth, gy + 1, 0x26394345);
        }

        // A fixed center marker identifies the waypoint's X/Z location on the map.
        int midX = mapX + drawWidth / 2;
        int midY = mapY + drawHeight / 2;
        g.fill(midX - 5, midY - 1, midX + 6, midY + 2, 0xE6000000);
        g.fill(midX - 1, midY - 5, midX + 2, midY + 6, 0xE6000000);
        g.fill(midX - 3, midY - 1, midX + 4, midY + 2, markerColor);
        g.fill(midX - 1, midY - 3, midX + 2, midY + 4, markerColor);
        g.fill(midX, midY, midX + 1, midY + 1, 0xFFFFFFFF);

        String footer = status;
        while (!footer.isEmpty() && font.width(footer) > width - 22) {
            footer = footer.substring(0, footer.length() - 1);
        }
        if (!footer.equals(status) && !footer.isEmpty()) footer += "…";
        g.text(font, footer, x + 11, y + height - 13, 0xFF899398, false);
    }

    private static void refreshIfNeeded(int centerX, int centerZ, String requestedDimension) {
        Minecraft mc = Minecraft.getInstance();
        String dimension = WaypointsMod.normalizeDimension(requestedDimension);
        long now = System.currentTimeMillis();
        boolean changed = centerX != lastCenterX || centerZ != lastCenterZ || !dimension.equals(lastDimension);
        if (!changed && now - lastRefreshMillis < 900L) return;

        ensureTexture(mc);
        lastCenterX = centerX;
        lastCenterZ = centerZ;
        lastDimension = dimension;
        lastRefreshMillis = now;
        loadedSamples = 0;

        NativeImage image = texture.getPixels();
        fillUnknown(image);
        ClientLevel level = mc.level;
        if (level == null) {
            status = "Enter a world to build the surface map";
            texture.upload();
            return;
        }

        String currentDimension = WaypointsMod.normalizeDimension(level.dimension().location().toString());
        if (!dimension.equals("unknown") && !dimension.equals("all") && !dimension.equals(currentDimension)) {
            status = "Switch to this dimension to load its surface";
            texture.upload();
            return;
        }

        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        int previousHeight;
        for (int py = 0; py < MAP_HEIGHT; py++) {
            previousHeight = Integer.MIN_VALUE;
            int blockZ = centerZ + py - MAP_HEIGHT / 2;
            for (int px = 0; px < MAP_WIDTH; px++) {
                int blockX = centerX + px - MAP_WIDTH / 2;
                // Never request or generate chunks from a menu preview. Only read client chunks already loaded.
                if (!level.getChunkSource().hasChunk(blockX >> 4, blockZ >> 4)) {
                    continue;
                }

                int surfaceY;
                try {
                    surfaceY = level.getHeight(Heightmap.Types.WORLD_SURFACE, blockX, blockZ) - 1;
                } catch (Throwable ignored) {
                    continue;
                }
                if (surfaceY < level.getMinY() || surfaceY >= level.getMaxY()) continue;

                pos.set(blockX, surfaceY, blockZ);
                BlockState state = level.getBlockState(pos);
                if (state.isAir()) continue;
                MapColor mapColor = state.getMapColor(level, pos);
                int rgb = mapColor == null ? 0x20272B : mapColor.col & 0xFFFFFF;
                if (rgb == 0) rgb = 0x20272B;

                // Light topographic edges slightly so rooflines and cliffs read clearly.
                float shade = 1.0f;
                if (previousHeight != Integer.MIN_VALUE) {
                    int rise = surfaceY - previousHeight;
                    if (rise > 1) shade = 1.13f;
                    else if (rise < -1) shade = 0.86f;
                }
                previousHeight = surfaceY;
                int r = Math.min(255, Math.round(((rgb >> 16) & 255) * shade));
                int green = Math.min(255, Math.round(((rgb >> 8) & 255) * shade));
                int b = Math.min(255, Math.round((rgb & 255) * shade));
                image.setPixel(px, py, 0xFF000000 | (r << 16) | (green << 8) | b);
                loadedSamples++;
            }
        }

        int total = MAP_WIDTH * MAP_HEIGHT;
        int coverage = (int) Math.round(loadedSamples * 100.0 / total);
        status = loadedSamples == 0
                ? "Visit this area to load its surface map"
                : "Top surface · " + coverage + "% of map loaded";
        texture.upload();
    }

    private static void ensureTexture(Minecraft mc) {
        if (texture != null) return;
        texture = new DynamicTexture("simple-waypoints-surface-map", MAP_WIDTH, MAP_HEIGHT, true);
        mc.getTextureManager().register(MAP_TEXTURE_ID, texture);
        fillUnknown(texture.getPixels());
        texture.upload();
    }

    private static void fillUnknown(NativeImage image) {
        for (int y = 0; y < MAP_HEIGHT; y++) {
            for (int x = 0; x < MAP_WIDTH; x++) {
                int shade = ((x / 8 + y / 8) & 1) == 0 ? 0xFF1A2023 : 0xFF14191C;
                image.setPixel(x, y, shade);
            }
        }
    }

    private static void border(GuiGraphicsExtractor g, int x, int y, int x2, int y2, int color) {
        g.fill(x, y, x2, y + 1, color);
        g.fill(x, y2 - 1, x2, y2, color);
        g.fill(x, y, x + 1, y2, color);
        g.fill(x2 - 1, y, x2, y2, color);
    }

    private static void rounded(GuiGraphicsExtractor g, int x, int y, int x2, int y2, int color, int radius) {
        int width = x2 - x;
        int height = y2 - y;
        if (width <= 0 || height <= 0) return;
        int r = Math.min(radius, Math.min(width / 2, height / 2));
        g.fill(x + r, y, x2 - r, y2, color);
        g.fill(x, y + r, x2, y2 - r, color);
        for (int row = 0; row < r; row++) {
            int inset = (int) Math.ceil(r - Math.sqrt(Math.max(0.0, r * r - Math.pow(r - row - 0.5, 2))));
            g.fill(x + inset, y + row, x2 - inset, y + row + 1, color);
            g.fill(x + inset, y2 - row - 1, x2 - inset, y2 - row, color);
        }
    }
}
