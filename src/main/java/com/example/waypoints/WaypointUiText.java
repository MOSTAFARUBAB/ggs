package com.example.waypoints;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.util.FormattedCharSequence;

/** Clean standard text renderer for all Waypoint Manager screens. */
final class WaypointUiText {
    private static final int TEXT = 0xFFF2F4F5;
    static final int LINE_HEIGHT = 10;

    private WaypointUiText() {}

    static MutableComponent component(String text) {
        return Component.literal(text == null ? "" : text);
    }

    static int width(Font font, String text) {
        return font.width(text == null ? "" : text);
    }

    static void draw(GuiGraphicsExtractor g, Font font, String text, int x, int y, int color, boolean shadow) {
        if (text == null || text.isEmpty()) return;
        g.text(font, text, x, y, color, shadow);
    }

    static void draw(GuiGraphicsExtractor g, Font font, FormattedCharSequence text, int x, int y, int color, boolean shadow) {
        if (text == null) return;
        g.text(font, text, x, y, color, shadow);
    }

    static int compactWidth(Font font, String text) {
        return font.width(text == null ? "" : text);
    }

    static void drawCompact(GuiGraphicsExtractor g, Font font, String text, int x, int y, int color) {
        draw(g, font, text, x, y, color, false);
    }

    static void drawStrong(GuiGraphicsExtractor g, Font font, String text, int x, int y, int color) {
        draw(g, font, text, x, y, color, false);
    }

    static void drawTitle(GuiGraphicsExtractor g, Font font, String text, int x, int y, int color) {
        if (text == null || text.isEmpty()) return;
        g.pose().pushMatrix();
        g.pose().translate(x, y);
        g.pose().scale(1.5f, 1.5f);
        g.text(font, text, 0, 0, color, false);
        g.pose().popMatrix();
    }

    static void drawInput(GuiGraphicsExtractor g, Font font, EditBox box, int x, int y, int maxWidth) {
        if (box == null || maxWidth <= 0) return;
        String value = box.getValue();
        int cursor = Math.max(0, Math.min(value.length(), box.getCursorPosition()));
        int start = 0;
        while (start < cursor && width(font, value.substring(start)) > maxWidth) start++;
        String shown = value.substring(start);
        int shownCursor = Math.max(0, cursor - start);
        if (width(font, shown) > maxWidth) {
            int end = shown.length();
            while (end > shownCursor && width(font, shown.substring(0, end)) > maxWidth) end--;
            shown = shown.substring(0, Math.max(shownCursor, end));
        }
        draw(g, font, shown, x, y, TEXT, false);
        if (box.isFocused() && ((System.currentTimeMillis() / 500L) & 1L) == 0L) {
            int caretX = x + width(font, shown.substring(0, Math.min(shownCursor, shown.length())));
            g.fill(caretX, y - 1, caretX + 1, y + 10, TEXT);
        }
    }
}
