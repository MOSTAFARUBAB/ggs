package com.example.waypoints;

import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.renderpearl.api.textures.FilterMode;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.Identifier;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

/** Supersampled rounded UI primitives. Every shape is rasterized once and cached. */
final class WaypointUiShapes {
    private static final int AA = 6;
    private static final int MAX_ENTRIES = 384;
    private static final long MAX_BYTES = 48L * 1024L * 1024L;
    private static long cachedBytes;
    private static final AtomicInteger SEQUENCE = new AtomicInteger();
    private static final Map<Key, Entry> CACHE = new LinkedHashMap<>(128, 0.75f, true);

    private WaypointUiShapes() {}

    static void draw(GuiGraphicsExtractor g, int x, int y, int width, int height,
                     int fill, int radius, int stroke, int strokeWidth) {
        if (width <= 0 || height <= 0) return;
        int scale = guiScale();
        Key key = new Key(width, height, fill, Math.max(0, radius), stroke, Math.max(0, strokeWidth), scale);
        Entry entry;
        synchronized (CACHE) {
            entry = CACHE.get(key);
            if (entry == null) {
                entry = create(key);
                if (entry != null) {
                    CACHE.put(key, entry);
                    cachedBytes += entry.bytes;
                    evict();
                }
            }
        }
        if (entry == null) {
            fallback(g, x, y, width, height, fill, radius, stroke, strokeWidth);
            return;
        }
        g.blit(entry.id, x, y, x + width, y + height, 0.0f, 1.0f, 0.0f, 1.0f);
    }

    private static Entry create(Key key) {
        int pixelW = key.width * key.guiScale;
        int pixelH = key.height * key.guiScale;
        long pixels = (long) pixelW * pixelH;
        // Very large full-screen surfaces are cheaper as solid fills; controls,
        // cards, fields and pills remain antialiased textures.
        if (pixels <= 0 || pixels > 3_000_000L) return null;
        int factor = AA * key.guiScale;
        int highW = key.width * factor;
        int highH = key.height * factor;
        BufferedImage high = new BufferedImage(highW, highH, BufferedImage.TYPE_INT_ARGB);
        Graphics2D gg = high.createGraphics();
        gg.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        gg.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
        gg.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);
        int r = Math.min(key.radius * factor, Math.min(highW, highH) / 2);
        if ((key.fill >>> 24) != 0) {
            gg.setColor(new Color(key.fill, true));
            gg.fillRoundRect(0, 0, highW, highH, r * 2, r * 2);
        }
        if ((key.stroke >>> 24) != 0 && key.strokeWidth > 0) {
            float sw = key.strokeWidth * factor;
            gg.setColor(new Color(key.stroke, true));
            gg.setStroke(new BasicStroke(sw, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            float inset = sw / 2.0f;
            gg.drawRoundRect(Math.round(inset), Math.round(inset),
                    Math.max(1, highW - Math.round(sw)), Math.max(1, highH - Math.round(sw)),
                    Math.max(0, r * 2 - Math.round(sw)), Math.max(0, r * 2 - Math.round(sw)));
        }
        gg.dispose();

        BufferedImage output = new BufferedImage(pixelW, pixelH, BufferedImage.TYPE_INT_ARGB);
        Graphics2D down = output.createGraphics();
        down.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
        down.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
        down.setRenderingHint(RenderingHints.KEY_ALPHA_INTERPOLATION, RenderingHints.VALUE_ALPHA_INTERPOLATION_QUALITY);
        down.drawImage(high, 0, 0, pixelW, pixelH, null);
        down.dispose();
        high.flush();

        DynamicTexture texture = new SmoothShapeTexture("waypoint-ui-shape", pixelW, pixelH);
        NativeImage nativeImage = texture.getPixels();
        for (int py = 0; py < pixelH; py++) {
            for (int px = 0; px < pixelW; px++) nativeImage.setPixel(px, py, output.getRGB(px, py));
        }
        output.flush();
        texture.upload();
        Identifier id = Identifier.fromNamespaceAndPath("simplewaypoints", "ui_shape_" + SEQUENCE.incrementAndGet());
        Minecraft.getInstance().getTextureManager().register(id, texture);
        return new Entry(id, pixels * 4L);
    }

    private static void evict() {
        while (CACHE.size() > MAX_ENTRIES || cachedBytes > MAX_BYTES) {
            var iterator = CACHE.entrySet().iterator();
            if (!iterator.hasNext()) break;
            Entry removed = iterator.next().getValue();
            iterator.remove();
            cachedBytes -= removed.bytes;
            Minecraft.getInstance().getTextureManager().release(removed.id);
        }
    }

    private static int guiScale() {
        try { return Math.max(1, Math.min(8, Minecraft.getInstance().getWindow().getGuiScale())); }
        catch (Throwable ignored) { return 1; }
    }

    private static void fallback(GuiGraphicsExtractor g, int x, int y, int w, int h,
                                 int fill, int radius, int stroke, int strokeWidth) {
        int r = Math.max(0, Math.min(radius, Math.min(w / 2, h / 2)));
        if ((fill >>> 24) != 0) {
            g.fill(x + r, y, x + w - r, y + h, fill);
            g.fill(x, y + r, x + w, y + h - r, fill);
        }
        if ((stroke >>> 24) != 0 && strokeWidth > 0) {
            g.fill(x, y, x + w, y + strokeWidth, stroke);
            g.fill(x, y + h - strokeWidth, x + w, y + h, stroke);
            g.fill(x, y, x + strokeWidth, y + h, stroke);
            g.fill(x + w - strokeWidth, y, x + w, y + h, stroke);
        }
    }

    private static final class SmoothShapeTexture extends DynamicTexture {
        private SmoothShapeTexture(String label, int width, int height) {
            super(label, width, height, true);
            this.sampler = RenderSystem.getSamplerCache().getClampToEdge(FilterMode.NEAREST);
        }
    }

    private record Key(int width, int height, int fill, int radius, int stroke, int strokeWidth, int guiScale) {}
    private record Entry(Identifier id, long bytes) {}
}
