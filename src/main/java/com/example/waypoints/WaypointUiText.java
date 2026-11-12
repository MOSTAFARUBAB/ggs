package com.example.waypoints;

import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.renderpearl.api.textures.FilterMode;
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
    static final int LINE_HEIGHT = 12;
    // Rasterize at 4x, then high-quality downsample ONCE to the exact GUI-pixel
    // dimensions. The final texture is drawn 1:1, so the GPU never has to blur
    // an oversized glyph texture to fit the text line.
    private static final int OVERSAMPLE = 4;
    private static final int MAX_CACHE_ENTRIES = 512;
    private static final AtomicInteger TEXTURE_SEQUENCE = new AtomicInteger();
    private static final Map<TextKey, TextTexture> TEXTURES = new LinkedHashMap<>(128, 0.75f, true);
    private static final Map<WidthKey, Integer> WIDTHS = new LinkedHashMap<>(256, 0.75f, true);

    private WaypointUiText() {}

    /** Creates a linearly sampled texture for continuous color gradients. */
    static DynamicTexture createSmoothTexture(String label, int width, int height, boolean zero) {
        return new SmoothDynamicTexture(label, width, height, zero, FilterMode.LINEAR);
    }

    static MutableComponent component(String text) {
        return Component.literal(text == null ? "" : text);
    }

    static int width(Font font, String text) {
        return textWidth(text, false, 9);
    }

    static int compactWidth(Font font, String text) {
        return textWidth(text, false, 8);
    }

    static int headingWidth(Font font, String text) {
        return textWidth(text, true, 14);
    }

    static void draw(GuiGraphicsExtractor g, Font font, String text, int x, int y, int color, boolean shadow) {
        drawText(g, text, x, y, color, shadow, false, 9);
    }

    static void draw(GuiGraphicsExtractor g, Font font, FormattedCharSequence text, int x, int y, int color, boolean shadow) {
        // Rich vanilla description lines keep Minecraft's own styled renderer.
        g.text(font, text, x, y, color, shadow);
    }

    static void drawCompact(GuiGraphicsExtractor g, Font font, String text, int x, int y, int color) {
        drawText(g, text, x, y, color, false, false, 8);
    }

    static void drawStrong(GuiGraphicsExtractor g, Font font, String text, int x, int y, int color) {
        drawText(g, text, x, y, color, false, true, 9);
    }

    static void drawTitle(GuiGraphicsExtractor g, Font font, String text, int x, int y, int color) {
        drawText(g, text, x, y, color, false, true, 11);
    }

    static void drawHeading(GuiGraphicsExtractor g, Font font, String text, int x, int y, int color) {
        drawText(g, text, x, y, color, false, true, 14);
    }

    static void drawSubheading(GuiGraphicsExtractor g, Font font, String text, int x, int y, int color) {
        drawText(g, text, x, y, color, false, true, 11);
    }

    /** Draw text at an explicit logical pixel size (used by live waypoint labels). */
    static void drawAtSize(GuiGraphicsExtractor g, Font font, String text, int x, int y, int color, int size) {
        drawText(g, text, x, y, color, false, false, Math.max(7, Math.min(18, size)));
    }

    static int widthAtSize(Font font, String text, int size) {
        return textWidth(text, false, Math.max(7, Math.min(18, size)));
    }

    /** The native EditBox owns keyboard input; its value is painted with the same smooth renderer. */
    static void drawInput(GuiGraphicsExtractor g, Font font, EditBox box, int x, int y, int maxWidth) {
        if (box == null || maxWidth <= 0) return;
        String value = box.getValue();
        int cursor = Math.max(0, Math.min(value.length(), box.getCursorPosition()));
        int start = 0;
        while (start < cursor && textWidth(value.substring(start), false, 9) > maxWidth) start++;
        String shown = value.substring(start);
        int shownCursor = Math.max(0, cursor - start);
        if (textWidth(shown, false, 9) > maxWidth) {
            int end = shown.length();
            while (end > shownCursor && textWidth(shown.substring(0, end), false, 9) > maxWidth) end--;
            shown = shown.substring(0, Math.max(shownCursor, end));
        }
        drawText(g, shown, x, y, 0xFFF2F4F5, false, false, 9);
        if (box.isFocused() && ((System.currentTimeMillis() / 500L) & 1L) == 0L) {
            int caretX = x + Math.max(0, textWidth(shown.substring(0, Math.min(shownCursor, shown.length())), false, 9) - 7);
            g.fill(caretX, y - 1, caretX + 1, y + 11, 0xFFF2F4F5);
        }
    }

    private static void drawText(GuiGraphicsExtractor g, String text, int x, int y, int color,
                                  boolean shadow, boolean bold, int size) {
        if (text == null || text.isEmpty()) return;
        // UI copy sits on opaque dark surfaces; a second offset glyph pass made
        // the letter edges look doubled/soft. Use one clean antialiased pass.
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
        int measured = Math.max(1, metrics.stringWidth(text) + 7);
        graphics.dispose();
        synchronized (WIDTHS) {
            if (WIDTHS.size() >= MAX_CACHE_ENTRIES * 2) WIDTHS.clear();
            WIDTHS.put(key, measured);
        }
        return measured;
    }

    private static int guiScale() {
        try {
            return Math.max(1, Math.min(8, Minecraft.getInstance().getWindow().getGuiScale()));
        } catch (Throwable ignored) {
            return 1;
        }
    }

    private static TextTexture texture(String text, int color, boolean bold, int size) {
        TextKey key = new TextKey(text, color, bold, size, guiScale());
        synchronized (TEXTURES) {
            TextTexture cached = TEXTURES.get(key);
            if (cached != null) return cached;
        }

        int guiScale = guiScale();
        int scale = OVERSAMPLE * guiScale;
        int style = bold ? java.awt.Font.BOLD : java.awt.Font.PLAIN;
        java.awt.Font highFont = new java.awt.Font(java.awt.Font.SANS_SERIF, style, size * scale);

        // Measure with the exact high-resolution font that will be rasterized.
        // Measuring a separate 1x font can differ by several pixels after font
        // hinting, which clipped the final letters in long labels.
        BufferedImage probe = new BufferedImage(1, 1, BufferedImage.TYPE_INT_ARGB);
        Graphics2D measure = probe.createGraphics();
        measure.setFont(highFont);
        measure.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        FontMetrics highMetricsForMeasure = measure.getFontMetrics();
        int logicalWidth = Math.max(1, (int)Math.ceil(highMetricsForMeasure.stringWidth(text) / (double) scale) + 6);
        int logicalHeight = Math.max(size + 4, (int)Math.ceil(highMetricsForMeasure.getHeight() / (double) scale) + 3);
        measure.dispose();

        // Match framebuffer density exactly. One extra logical pixel around the
        // glyphs prevents descenders and italic fallbacks from touching texture edges.
        int pixelWidth = logicalWidth * guiScale;
        int pixelHeight = logicalHeight * guiScale;
        int textureWidth = pixelWidth * OVERSAMPLE;
        int textureHeight = pixelHeight * OVERSAMPLE;
        BufferedImage high = new BufferedImage(textureWidth, textureHeight, BufferedImage.TYPE_INT_ARGB);
        Graphics2D hg = high.createGraphics();
        hg.setComposite(AlphaComposite.Src);
        hg.setFont(highFont);
        hg.setColor(new Color(color, true));
        hg.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        hg.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        hg.setRenderingHint(RenderingHints.KEY_FRACTIONALMETRICS, RenderingHints.VALUE_FRACTIONALMETRICS_ON);
        hg.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
        hg.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);
        FontMetrics highMetrics = hg.getFontMetrics();
        hg.drawString(text, scale, scale + highMetrics.getAscent());
        hg.dispose();

        // Convert the supersampled source to the exact framebuffer-pixel size
        // once, using bicubic antialiasing. The result is then sampled 1:1 by the
        // GPU at the current GUI scale: no magnification blur or pixel doubling.
        BufferedImage finalImage = new BufferedImage(pixelWidth, pixelHeight, BufferedImage.TYPE_INT_ARGB);
        Graphics2D downsample = finalImage.createGraphics();
        downsample.setComposite(AlphaComposite.Src);
        downsample.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
        downsample.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
        downsample.setRenderingHint(RenderingHints.KEY_ALPHA_INTERPOLATION, RenderingHints.VALUE_ALPHA_INTERPOLATION_QUALITY);
        downsample.drawImage(high, 0, 0, pixelWidth, pixelHeight, null);
        downsample.dispose();
        high.flush();

        int sequence = TEXTURE_SEQUENCE.incrementAndGet();
        Identifier id = Identifier.fromNamespaceAndPath("simplewaypoints", "ui_text_run_" + sequence);
        DynamicTexture dynamic = new SmoothDynamicTexture("simple-waypoints-ui-text", pixelWidth, pixelHeight, true, FilterMode.NEAREST);
        NativeImage pixels = dynamic.getPixels();
        for (int py = 0; py < pixelHeight; py++) {
            for (int px = 0; px < pixelWidth; px++) {
                pixels.setPixel(px, py, finalImage.getRGB(px, py));
            }
        }
        finalImage.flush();
        dynamic.upload();
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

    private static final class SmoothDynamicTexture extends DynamicTexture {
        private SmoothDynamicTexture(String label, int width, int height, boolean zero, FilterMode filter) {
            super(label, width, height, zero);
            this.sampler = RenderSystem.getSamplerCache().getClampToEdge(filter);
        }
    }

    private record WidthKey(String text, boolean bold, int size) {}
    private record TextKey(String text, int color, boolean bold, int size, int guiScale) {}
    private record TextTexture(Identifier id, int width, int height) {}
}
