package com.example.waypoints;

import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.Identifier;
import net.minecraft.util.FormattedCharSequence;

import java.awt.AlphaComposite;
import java.awt.Color;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Shared, antialiased text renderer for the custom waypoint UI.
 * Each string is rasterized as one high-quality text surface instead of
 * blitting individual character tiles (which caused visible tracking gaps).
 */
final class WaypointUiText {
    static final int LINE_HEIGHT = 14;
    // Keep glyphs at 4x source density all the way to the GPU. Do not downsample
    // them to 1x on the CPU: doing so throws away the high-DPI edge information
    // before Minecraft scales the GUI to the physical display.
    private static final int OVERSAMPLE = 4;
    private static final int MAX_CACHE_ENTRIES = 256;
    private static final AtomicInteger TEXTURE_SEQUENCE = new AtomicInteger();
    private static final Map<TextKey, TextTexture> TEXTURES = new LinkedHashMap<>(128, 0.75f, true);
    private static final Map<WidthKey, Integer> WIDTHS = new LinkedHashMap<>(256, 0.75f, true);

    private WaypointUiText() {}

    static MutableComponent component(String text) {
        return Component.literal(text == null ? "" : text);
    }

    static int width(Font font, String text) {
        return textWidth(text, false, 12);
    }

    static int compactWidth(Font font, String text) {
        return textWidth(text, false, 11);
    }

    static void draw(GuiGraphicsExtractor g, Font font, String text, int x, int y, int color, boolean shadow) {
        drawText(g, text, x, y, color, shadow, false, 12);
    }

    static void draw(GuiGraphicsExtractor g, Font font, FormattedCharSequence text, int x, int y, int color, boolean shadow) {
        // Rich vanilla description lines keep Minecraft's own styled renderer.
        g.text(font, text, x, y, color, shadow);
    }

    static void drawCompact(GuiGraphicsExtractor g, Font font, String text, int x, int y, int color) {
        drawText(g, text, x, y, color, false, false, 11);
    }

    static void drawStrong(GuiGraphicsExtractor g, Font font, String text, int x, int y, int color) {
        drawText(g, text, x, y, color, false, true, 13);
    }

    static void drawTitle(GuiGraphicsExtractor g, Font font, String text, int x, int y, int color) {
        drawText(g, text, x, y, color, false, true, 18);
    }

    /** The native EditBox owns keyboard input; its value is painted with the same smooth renderer. */
    static void drawInput(GuiGraphicsExtractor g, Font font, EditBox box, int x, int y, int maxWidth) {
        if (box == null || maxWidth <= 0) return;
        String value = box.getValue();
        int cursor = Math.max(0, Math.min(value.length(), box.getCursorPosition()));
        int start = 0;
        while (start < cursor && textWidth(value.substring(start), false, 12) > maxWidth) start++;
        String shown = value.substring(start);
        int shownCursor = Math.max(0, cursor - start);
        if (textWidth(shown, false, 12) > maxWidth) {
            int end = shown.length();
            while (end > shownCursor && textWidth(shown.substring(0, end), false, 12) > maxWidth) end--;
            shown = shown.substring(0, Math.max(shownCursor, end));
        }
        drawText(g, shown, x, y, 0xFFF2F4F5, false, false, 12);
        if (box.isFocused() && ((System.currentTimeMillis() / 500L) & 1L) == 0L) {
            int caretX = x + textWidth(shown.substring(0, Math.min(shownCursor, shown.length())), false, 12);
            g.fill(caretX, y - 1, caretX + 1, y + 13, 0xFFF2F4F5);
        }
    }

    private static void drawText(GuiGraphicsExtractor g, String text, int x, int y, int color,
                                  boolean shadow, boolean bold, int size) {
        if (text == null || text.isEmpty()) return;
        if (shadow) drawRun(g, text, x + 1, y + 1, 0x88000000, bold, size);
        drawRun(g, text, x, y, color, bold, size);
    }

    private static void drawRun(GuiGraphicsExtractor g, String text, int x, int y, int color, boolean bold, int size) {
        TextTexture run = texture(text, color, bold, size);
        if (run == null) return;
        g.blit(run.id(), x, y, x + run.width(), y + run.height(), 0.0f, 1.0f, 0.0f, 1.0f);
    }

    private static int textWidth(String text, boolean bold, int size) {
        if (text == null || text.isEmpty()) return 0;
        WidthKey key = new WidthKey(text, bold, size);
        synchronized (WIDTHS) {
            Integer known = WIDTHS.get(key);
            if (known != null) return known;
        }
        java.awt.Font awt = new java.awt.Font("SansSerif", bold ? java.awt.Font.BOLD : java.awt.Font.PLAIN, size);
        BufferedImage probe = new BufferedImage(1, 1, BufferedImage.TYPE_INT_ARGB);
        Graphics2D graphics = probe.createGraphics();
        graphics.setFont(awt);
        FontMetrics metrics = graphics.getFontMetrics();
        int measured = Math.max(1, metrics.stringWidth(text) + 2);
        graphics.dispose();
        synchronized (WIDTHS) {
            if (WIDTHS.size() >= MAX_CACHE_ENTRIES * 2) WIDTHS.clear();
            WIDTHS.put(key, measured);
        }
        return measured;
    }

    private static TextTexture texture(String text, int color, boolean bold, int size) {
        TextKey key = new TextKey(text, color, bold, size);
        synchronized (TEXTURES) {
            TextTexture cached = TEXTURES.get(key);
            if (cached != null) return cached;
        }

        int scale = OVERSAMPLE;
        java.awt.Font awt = new java.awt.Font("SansSerif", bold ? java.awt.Font.BOLD : java.awt.Font.PLAIN, size * scale);
        BufferedImage probe = new BufferedImage(1, 1, BufferedImage.TYPE_INT_ARGB);
        Graphics2D pg = probe.createGraphics();
        pg.setFont(awt);
        FontMetrics metrics = pg.getFontMetrics();
        int logicalWidth = Math.max(1, (int)Math.ceil(metrics.stringWidth(text) / (double)scale) + 2);
        int logicalHeight = Math.max(size + 3, (int)Math.ceil(metrics.getHeight() / (double)scale) + 2);
        int textureWidth = logicalWidth * scale;
        int textureHeight = logicalHeight * scale;
        int baseline = scale + metrics.getAscent();
        pg.dispose();

        // Rasterize once at 4x logical GUI resolution and keep those pixels in
        // the GPU texture. GPU linear filtering performs the final display-scale
        // reduction; CPU downsampling to a 1x image made the old text look soft.
        BufferedImage high = new BufferedImage(textureWidth, textureHeight, BufferedImage.TYPE_INT_ARGB);
        Graphics2D hg = high.createGraphics();
        hg.setComposite(AlphaComposite.Src);
        hg.setFont(awt);
        hg.setColor(new Color(color, true));
        hg.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        hg.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        hg.setRenderingHint(RenderingHints.KEY_FRACTIONALMETRICS, RenderingHints.VALUE_FRACTIONALMETRICS_ON);
        hg.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
        hg.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);
        hg.drawString(text, scale, baseline);
        hg.dispose();

        int sequence = TEXTURE_SEQUENCE.incrementAndGet();
        Identifier id = Identifier.fromNamespaceAndPath("simplewaypoints", "ui_text_run_" + sequence);
        DynamicTexture dynamic = new DynamicTexture("simple-waypoints-ui-text", textureWidth, textureHeight, true);
        NativeImage pixels = dynamic.getPixels();
        for (int py = 0; py < textureHeight; py++) {
            for (int px = 0; px < textureWidth; px++) pixels.setPixel(px, py, high.getRGB(px, py));
        }
        dynamic.upload();
        // The 4x source texture is drawn into logical GUI coordinates. Linear
        // minification keeps curves smooth without a second CPU-side blur pass.
        dynamic.setFilter(true, false);
        Minecraft.getInstance().getTextureManager().register(id, dynamic);
        TextTexture created = new TextTexture(id, logicalWidth, logicalHeight);
        synchronized (TEXTURES) {
            TEXTURES.put(key, created);
            while (TEXTURES.size() > MAX_CACHE_ENTRIES) {
                TextKey oldest = TEXTURES.keySet().iterator().next();
                TextTexture removed = TEXTURES.remove(oldest);
                Minecraft.getInstance().getTextureManager().release(removed.id());
            }
        }
        return created;
    }

    private record WidthKey(String text, boolean bold, int size) {}
    private record TextKey(String text, int color, boolean bold, int size) {}
    private record TextTexture(Identifier id, int width, int height) {}
}
