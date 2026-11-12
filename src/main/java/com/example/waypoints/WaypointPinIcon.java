package com.example.waypoints;

import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.renderpearl.api.textures.FilterMode;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.Identifier;

import java.awt.AlphaComposite;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Path2D;
import java.awt.geom.RoundRectangle2D;
import java.awt.image.BufferedImage;
import java.util.LinkedHashMap;
import java.util.Map;

/** High-resolution, antialiased waypoint pin tile matching the supplied dashboard reference. */
final class WaypointPinIcon {
    private static final int AA = 5;
    private static final int MAX_COLORS = 48;
    private static final Map<Key, Identifier> CACHE = new LinkedHashMap<>(32, .75f, true);

    private WaypointPinIcon() {}

    static void draw(GuiGraphicsExtractor g, int x, int y, int size, int argb) {
        int rgb = argb & 0x00FFFFFF;
        int guiScale;
        try { guiScale = Math.max(1, Math.min(8, Minecraft.getInstance().getWindow().getGuiScale())); }
        catch (Throwable ignored) { guiScale = 1; }
        Key key = new Key(size, rgb, guiScale);
        Identifier id;
        synchronized (CACHE) {
            id = CACHE.get(key);
            if (id == null) {
                id = create(key);
                CACHE.put(key, id);
                while (CACHE.size() > MAX_COLORS) {
                    var it = CACHE.entrySet().iterator();
                    var old = it.next();
                    it.remove();
                    Minecraft.getInstance().getTextureManager().release(old.getValue());
                }
            }
        }
        g.blit(id, x, y, x + size, y + size, 0f, 1f, 0f, 1f);
    }

    private static Identifier create(Key key) {
        int factor = AA * key.guiScale;
        int highSize = key.size * factor;
        BufferedImage high = new BufferedImage(highSize, highSize, BufferedImage.TYPE_INT_ARGB);
        Graphics2D gg = high.createGraphics();
        gg.setComposite(AlphaComposite.Src);
        gg.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        gg.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
        gg.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);
        gg.scale(factor, factor);

        // Subtle color-tinted tile and fine border.
        Color accent = new Color(key.rgb);
        gg.setColor(new Color(27, 37, 30, 255));
        gg.fillRoundRect(0, 0, key.size, key.size, 8, 8);
        gg.setColor(new Color(accent.getRed(), accent.getGreen(), accent.getBlue(), 58));
        gg.setStroke(new BasicStroke(.65f));
        gg.draw(new RoundRectangle2D.Float(.4f, .4f, key.size - .8f, key.size - .8f, 7f, 7f));

        // Classic map-pin outline: rounded head, tapered lower point, and a clear center hole.
        Path2D.Float pin = new Path2D.Float();
        pin.moveTo(14.0f, 23.0f);
        pin.curveTo(11.1f, 20.0f, 7.0f, 15.6f, 7.0f, 11.2f);
        pin.curveTo(7.0f, 7.4f, 10.0f, 5.0f, 14.0f, 5.0f);
        pin.curveTo(18.0f, 5.0f, 21.0f, 7.4f, 21.0f, 11.2f);
        pin.curveTo(21.0f, 15.6f, 16.9f, 20.0f, 14.0f, 23.0f);
        pin.closePath();
        gg.setColor(accent);
        gg.setStroke(new BasicStroke(1.50f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        gg.draw(pin);
        gg.setStroke(new BasicStroke(1.3f));
        gg.draw(new Ellipse2D.Float(11.3f, 8.5f, 5.4f, 5.4f));
        gg.dispose();

        int pixelSize = key.size * key.guiScale;
        BufferedImage output = new BufferedImage(pixelSize, pixelSize, BufferedImage.TYPE_INT_ARGB);
        Graphics2D down = output.createGraphics();
        down.setComposite(AlphaComposite.Src);
        down.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
        down.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
        down.setRenderingHint(RenderingHints.KEY_ALPHA_INTERPOLATION, RenderingHints.VALUE_ALPHA_INTERPOLATION_QUALITY);
        down.drawImage(high, 0, 0, pixelSize, pixelSize, null);
        down.dispose();
        high.flush();

        DynamicTexture texture = new PinTexture("waypoint-pin", pixelSize, pixelSize);
        NativeImage image = texture.getPixels();
        for (int py = 0; py < pixelSize; py++)
            for (int px = 0; px < pixelSize; px++)
                image.setPixel(px, py, output.getRGB(px, py));
        output.flush();
        texture.upload();
        Identifier id = Identifier.fromNamespaceAndPath("simplewaypoints", "waypoint_pin_" + key.size + "_" + Integer.toHexString(key.rgb) + "_" + key.guiScale);
        Minecraft.getInstance().getTextureManager().register(id, texture);
        return id;
    }

    private static final class PinTexture extends DynamicTexture {
        private PinTexture(String name, int width, int height) {
            super(name, width, height, true);
            this.sampler = RenderSystem.getSamplerCache().getClampToEdge(FilterMode.NEAREST);
        }
    }

    private record Key(int size, int rgb, int guiScale) {}
}
