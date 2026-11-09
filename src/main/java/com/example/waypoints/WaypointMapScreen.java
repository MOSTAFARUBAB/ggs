package com.example.waypoints;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;

import java.util.List;

/** Dedicated map workspace with waypoint markers and the shared neon sidebar. */
public final class WaypointMapScreen extends Screen {
    private static final int BG = 0xFF0B0C0E;
    private static final int PANEL = 0xFF131719;
    private static final int EDGE = 0xFF29312B;
    private static final int TEXT = 0xFFF2F5F5;
    private static final int MUTED = 0xFFA3ABB0;
    private int railWidth = 176;
    private static final Identifier MAP_TEXTURE = Identifier.fromNamespaceAndPath("simplewaypoints", "textures/gui/surface_map_base.png");
    private List<Waypoint> waypoints = List.of();
    private int mapX, mapY, mapW, mapH;
    private int zoom = 100;
    private boolean showContours = true;

    public WaypointMapScreen() { super(Component.literal("Waypoint Map")); }

    @Override protected void init() {
        railWidth = width >= 760 ? 176 : 54;
        waypoints = WaypointsMod.menuWaypoints();
    }

    @Override public void extractBackground(GuiGraphicsExtractor g, int mx, int my, float delta) {
        g.fill(0, 0, width, height, BG);
    }

    @Override public void extractRenderState(GuiGraphicsExtractor g, int mx, int my, float delta) {
        drawSidebar(g, mx, my);
        int x = railWidth + (railWidth > 70 ? 24 : 12);
        WaypointUiText.drawTitle(g, font, "Surface Map", x, 28, TEXT);
        mapX = x;
        mapY = 60;
        mapW = Math.max(120, width - mapX - 16);
        mapH = Math.max(100, height - mapY - 14);
        drawMap(g);
        drawMapTools(g, mx, my);
        super.extractRenderState(g, mx, my, delta);
    }

    private void drawMap(GuiGraphicsExtractor g) {
        int top = mapY, bottom = mapY + mapH;
        rounded(g, mapX, top, mapX + mapW, bottom, 0xFF13251B, 11);
        border(g, mapX, top, mapX + mapW, bottom, EDGE);
        // Textured overhead terrain replaces the former empty green schematic
        // with hand-drawn sine-wave rivers and sparse rectangles. Waypoint pins
        // below remain dynamic and are positioned from the actual saved data.
        int innerX = mapX + 1, innerY = top + 1;
        int innerW = mapW - 2, innerH = mapH - 2;
        // Keep the terrain, markers, labels and legend clipped to the map panel.
        g.enableScissor(innerX, innerY, mapX + mapW - 1, bottom - 1);
        float sourceAspect = 16.0f / 9.0f;
        float targetAspect = innerW / (float)Math.max(1, innerH);
        float u0 = 0.0f, u1 = 1.0f, v0 = 0.0f, v1 = 1.0f;
        if (targetAspect > sourceAspect) {
            float visible = sourceAspect / targetAspect;
            v0 = (1.0f - visible) * 0.5f;
            v1 = 1.0f - v0;
        } else if (targetAspect < sourceAspect) {
            float visible = targetAspect / sourceAspect;
            u0 = (1.0f - visible) * 0.5f;
            u1 = 1.0f - u0;
        }
        float zoomFactor = Math.max(0.4f, Math.min(2.0f, zoom / 100.0f));
        int drawX = innerX, drawY = innerY, drawW = innerW, drawH = innerH;
        if (zoomFactor < 1.0f) {
            drawW = Math.max(1, Math.round(innerW * zoomFactor));
            drawH = Math.max(1, Math.round(innerH * zoomFactor));
            drawX += (innerW - drawW) / 2;
            drawY += (innerH - drawH) / 2;
        } else if (zoomFactor > 1.0f) {
            float uCenter = (u0 + u1) * 0.5f, vCenter = (v0 + v1) * 0.5f;
            float uHalf = (u1 - u0) / (2.0f * zoomFactor);
            float vHalf = (v1 - v0) / (2.0f * zoomFactor);
            u0 = uCenter - uHalf; u1 = uCenter + uHalf;
            v0 = vCenter - vHalf; v1 = vCenter + vHalf;
        }
        g.blit(MAP_TEXTURE, drawX, drawY, drawX + drawW, drawY + drawH, u0, u1, v0, v1);
        g.fill(mapX + 1, top + 1, mapX + mapW - 1, bottom - 1, 0x16040A08);
        if (showContours) drawContourOverlay(g, top, bottom);
        if (!waypoints.isEmpty()) {
            int minX = waypoints.stream().mapToInt(Waypoint::x).min().orElse(0);
            int maxX = waypoints.stream().mapToInt(Waypoint::x).max().orElse(0);
            int minZ = waypoints.stream().mapToInt(Waypoint::z).min().orElse(0);
            int maxZ = waypoints.stream().mapToInt(Waypoint::z).max().orElse(0);
            int rangeX = Math.max(1, maxX - minX), rangeZ = Math.max(1, maxZ - minZ);
            for (Waypoint w : waypoints) {
                double factor = zoom / 100.0;
                int baseX = mapX + 36 + (int)((w.x() - minX) * (mapW - 72.0) / rangeX);
                int baseY = top + 36 + (int)((w.z() - minZ) * (mapH - 72.0) / rangeZ);
                int px = mapX + mapW / 2 + (int)((baseX - (mapX + mapW / 2)) * factor);
                int py = top + mapH / 2 + (int)((baseY - (top + mapH / 2)) * factor);
                px = Math.max(mapX + 24, Math.min(mapX + mapW - 24, px));
                py = Math.max(top + 24, Math.min(bottom - 24, py));
                int color = 0xFF000000 | WaypointsMod.resolveColor(w);
                g.fill(px - 2, py + 9, px + 3, py + 17, 0x99000000);
                rounded(g, px - 13, py - 13, px + 14, py + 14, 0xFF0D160F, 12);
                border(g, px - 13, py - 13, px + 14, py + 14, color);
                g.fill(px - 2, py - 6, px + 3, py + 1, color);
                g.fill(px - 6, py - 2, px + 7, py + 4, color);
                if (mapW > 420) {
                    String label = w.name().length() > 16 ? w.name().substring(0, 15) + "…" : w.name();
                    int labelWidth = Math.min(mapW - 36, WaypointUiText.width(font, label) + 12);
                    int labelHeight = 22;
                    int minLabelX = mapX + 8, maxLabelX = mapX + mapW - labelWidth - 8;
                    int minLabelY = top + 8, maxLabelY = bottom - labelHeight - 8;
                    int compassLeft = mapX + mapW - 71, compassRight = mapX + mapW - 15;
                    int compassTop = top + 15, compassBottom = top + 71;
                    int legendW = Math.max(100, Math.min(240, mapW - 28));
                    int legendCount = Math.min(7, waypoints.size());
                    int legendLeft = mapX + 14, legendRight = legendLeft + legendW;
                    int legendTop = bottom - (34 + legendCount * 22) - 14, legendBottom = bottom - 14;
                    int toolsLeft = mapX + 12, toolsRight = mapX + 40;
                    int toolsTop = top + 12, toolsBottom = top + 110;

                    int[][] candidates = {
                            {px + 17, py - labelHeight / 2},
                            {px - labelWidth - 17, py - labelHeight / 2},
                            {px - labelWidth / 2, py - labelHeight - 16},
                            {px - labelWidth / 2, py + 16}
                    };
                    int labelX = -1, labelY = -1;
                    for (int[] candidate : candidates) {
                        int candidateX = Math.max(minLabelX, Math.min(maxLabelX, candidate[0]));
                        int candidateY = Math.max(minLabelY, Math.min(maxLabelY, candidate[1]));
                        boolean overlapsCompass = rectsOverlap(candidateX, candidateY, labelWidth, labelHeight,
                                compassLeft, compassTop, compassRight - compassLeft, compassBottom - compassTop);
                        boolean overlapsLegend = rectsOverlap(candidateX, candidateY, labelWidth, labelHeight,
                                legendLeft, legendTop, legendRight - legendLeft, legendBottom - legendTop);
                        boolean overlapsTools = rectsOverlap(candidateX, candidateY, labelWidth, labelHeight,
                                toolsLeft, toolsTop, toolsRight - toolsLeft, toolsBottom - toolsTop);
                        boolean overlapsMarker = rectsOverlap(candidateX, candidateY, labelWidth, labelHeight,
                                px - 14, py - 14, 28, 28);
                        if (!overlapsCompass && !overlapsLegend && !overlapsTools && !overlapsMarker) {
                            labelX = candidateX;
                            labelY = candidateY;
                            break;
                        }
                    }
                    if (labelX >= 0) {
                        rounded(g, labelX, labelY, labelX + labelWidth, labelY + labelHeight, 0xE8090C0A, 5);
                        WaypointUiText.draw(g, font, label, labelX + 6, labelY + 5, TEXT, false);
                    }
                }
            }
        }
        // Legend panel.
        int legendW = Math.max(100, Math.min(240, mapW - 28));
        int count = Math.min(7, waypoints.size());
        int lx = mapX + 14, ly = bottom - (34 + count * 22) - 14;
        rounded(g, lx, ly, lx + legendW, ly + 34 + count * 22, 0xE8090C0A, 8);
        WaypointUiText.draw(g, font, "WAYPOINTS  ·  " + waypoints.size(), lx + 11, ly + 10, WaypointsMod.uiAccentColor, true);
        for (int i = 0; i < count; i++) {
            Waypoint w = waypoints.get(i);
            int yy = ly + 35 + i * 22;
            g.fill(lx + 11, yy + 3, lx + 19, yy + 11, 0xFF000000 | WaypointsMod.resolveColor(w));
            String label = w.name();
            int maxLabelWidth = Math.max(48, legendW - 44);
            while (!label.isEmpty() && WaypointUiText.width(font, label) > maxLabelWidth) {
                label = label.substring(0, label.length() - 1);
            }
            if (!label.equals(w.name()) && !label.isEmpty()) label = label.substring(0, Math.max(0, label.length() - 1)) + "…";
            WaypointUiText.draw(g, font, label, lx + 27, yy + 2, TEXT, false);
        }
        g.disableScissor();
    }

    private static boolean rectsOverlap(int ax, int ay, int aw, int ah,
                                        int bx, int by, int bw, int bh) {
        return ax < bx + bw && ax + aw > bx && ay < by + bh && ay + ah > by;
    }

    private void drawContourOverlay(GuiGraphicsExtractor g, int top, int bottom) {
        // Thin, low-contrast contour traces; they are decorative map layers, not world data.
        int left = mapX + 8, right = mapX + mapW - 8;
        int usableHeight = Math.max(1, bottom - top - 32);
        for (int row = 0; row < 7; row++) {
            double phase = row * 0.83;
            int baseY = top + 18 + row * usableHeight / 6;
            for (int x = left; x < right; x += 5) {
                int offset = (int)Math.round(Math.sin((x - left) * 0.012 + phase) * 7.0);
                int y = baseY + offset;
                if (y > top + 8 && y < bottom - 8) g.fill(x, y, Math.min(x + 5, right), y + 1, 0x335FE18A);
            }
        }
    }

    private void drawMapTools(GuiGraphicsExtractor g, int mx, int my) {
        int x = mapX + 12, y = mapY + 12;
        mapTool(g, x, y, 28, 28, "+", zoom < 200, mx, my);
        mapTool(g, x, y + 35, 28, 28, "−", zoom > 40, mx, my);
        mapTool(g, x, y + 70, 28, 28, "≋", showContours, mx, my);
        if (mapW >= 230 && mapH >= 150) {
            int cx = mapX + mapW - 43, cy = mapY + 43;
            rounded(g, cx - 28, cy - 28, cx + 28, cy + 28, 0xE90C1010, 28);
            border(g, cx - 28, cy - 28, cx + 28, cy + 28, 0xFFBBC5C2);
            WaypointUiText.draw(g, font, "N", cx - 3, cy - 23, TEXT, true);
            WaypointUiText.draw(g, font, "S", cx - 3, cy + 13, MUTED, false);
            WaypointUiText.draw(g, font, "W", cx - 22, cy - 4, MUTED, false);
            WaypointUiText.draw(g, font, "E", cx + 15, cy - 4, MUTED, false);
            g.fill(cx - 2, cy - 18, cx + 3, cy + 18, 0xFFEEF2EE);
            g.fill(cx - 15, cy - 2, cx + 16, cy + 3, 0xFFEEF2EE);
            g.fill(cx - 2, cy - 18, cx + 3, cy - 2, WaypointsMod.uiAccentColor);
        }
    }

    private void mapTool(GuiGraphicsExtractor g, int x, int y, int w, int h, String label,
                         boolean enabled, int mx, int my) {
        boolean hover = mx >= x && mx < x + w && my >= y && my < y + h;
        rounded(g, x, y, x + w, y + h, hover ? 0xFF26312A : 0xE90C1010, 6);
        border(g, x, y, x + w, y + h, enabled ? 0xFF637E5C : EDGE);
        WaypointUiText.draw(g, font, label, x + (w - WaypointUiText.width(font, label)) / 2, y + 8,
                enabled ? WaypointsMod.uiAccentColor : MUTED, true);
    }

    private void drawSidebar(GuiGraphicsExtractor g, int mx, int my) {
        g.fill(0, 0, railWidth, height, 0xFF101113);
        g.fill(railWidth - 1, 0, railWidth, height, EDGE);
        if (railWidth > 70) {
            drawBrandMark(g, 18, 20);
            WaypointUiText.draw(g, font, "Simple", 69, 22, TEXT, true);
            WaypointUiText.draw(g, font, "Waypoints", 69, 36, TEXT, true);
            nav(g, mx, my, 10, 112, "Waypoints", false);
            nav(g, mx, my, 10, 157, "Map", true);
            nav(g, mx, my, 10, 202, "Settings", false);
            nav(g, mx, my, 10, 247, "About", false);
            g.fill(14, height - 96, 42, height - 77, 0xFF79502E);
            g.fill(14, height - 99, 42, height - 92, 0xFF4F9C35);
            WaypointUiText.draw(g, font, "v1.4.9", 54, height - 91, WaypointsMod.uiAccentColor, true);
        } else {
            drawBrandMark(g, 5, 18);
            nav(g, mx, my, 8, 112, "Waypoints", false);
            nav(g, mx, my, 8, 156, "Map", true);
            nav(g, mx, my, 8, 200, "Settings", false);
            nav(g, mx, my, 8, 244, "About", false);
            WaypointUiText.draw(g, font, "1.4.9", 5, height - 32, WaypointsMod.uiAccentColor, true);
        }
    }

    private void drawBrandMark(GuiGraphicsExtractor g, int x, int y) {
        rounded(g, x + 7, y, x + 28, y + 27, WaypointsMod.uiAccentColor, 11);
        g.fill(x + 9, y + 20, x + 26, y + 30, WaypointsMod.uiAccentColor);
        g.fill(x + 12, y + 28, x + 23, y + 36, WaypointsMod.uiAccentColor);
        g.fill(x + 16, y + 34, x + 20, y + 42, WaypointsMod.uiAccentColor);
        rounded(g, x + 13, y + 6, x + 23, y + 17, 0xFF101113, 6);
    }

    private void nav(GuiGraphicsExtractor g, int mx, int my, int x, int y, String label, boolean active) {
        int w = railWidth > 70 ? railWidth - 20 : railWidth - 16;
        boolean hover = mx >= x && mx <= x + w && my >= y && my <= y + 36;
        if (active || hover) rounded(g, x, y, x + w, y + 36, active ? WaypointsMod.uiAccentColor : PANEL, 7);
        int c = active ? 0xFF101410 : MUTED;
        if (label.equals("Waypoints")) drawPinGlyph(g, x + 12, y + 10, c);
        else if (label.equals("Map")) drawCubeGlyph(g, x + 11, y + 10, c);
        else if (label.equals("Settings")) drawSlidersGlyph(g, x + 10, y + 10, c);
        else drawInfoGlyph(g, x + 10, y + 10, c);
        if (railWidth > 70) WaypointUiText.draw(g, font, label, x + 38, y + 11, active ? 0xFF101410 : MUTED, active);
    }

    private void drawPinGlyph(GuiGraphicsExtractor g, int x, int y, int c) {
        g.fill(x + 4, y, x + 8, y + 2, c); g.fill(x + 2, y + 2, x + 10, y + 6, c);
        g.fill(x + 3, y + 6, x + 9, y + 9, c); g.fill(x + 5, y + 9, x + 7, y + 12, c);
        g.fill(x + 4, y + 3, x + 8, y + 6, 0xFF101113);
    }
    private void drawCubeGlyph(GuiGraphicsExtractor g, int x, int y, int c) {
        g.fill(x + 5, y, x + 6, y + 1, c); g.fill(x + 2, y + 2, x + 9, y + 3, c);
        g.fill(x, y + 4, x + 2, y + 6, c); g.fill(x + 9, y + 4, x + 11, y + 6, c);
        g.fill(x + 2, y + 7, x + 9, y + 8, c); g.fill(x + 5, y + 3, x + 6, y + 8, c);
    }
    private void drawSlidersGlyph(GuiGraphicsExtractor g, int x, int y, int c) {
        g.fill(x, y + 2, x + 13, y + 3, c); g.fill(x, y + 7, x + 13, y + 8, c);
        g.fill(x, y + 12, x + 13, y + 13, c); g.fill(x + 3, y, x + 6, y + 5, c); g.fill(x + 8, y + 5, x + 11, y + 10, c);
    }
    private void drawInfoGlyph(GuiGraphicsExtractor g, int x, int y, int c) {
        g.fill(x + 2, y, x + 11, y + 1, c); g.fill(x, y + 2, x + 1, y + 11, c);
        g.fill(x + 12, y + 2, x + 13, y + 11, c); g.fill(x + 2, y + 12, x + 11, y + 13, c);
        g.fill(x + 6, y + 3, x + 7, y + 4, c); g.fill(x + 6, y + 6, x + 7, y + 10, c);
    }

    private static void border(GuiGraphicsExtractor g, int x, int y, int x2, int y2, int color) {
        g.fill(x, y, x2, y + 1, color); g.fill(x, y2 - 1, x2, y2, color);
        g.fill(x, y, x + 1, y2, color); g.fill(x2 - 1, y, x2, y2, color);
    }
    private static void rounded(GuiGraphicsExtractor g, int x, int y, int x2, int y2, int color, int radius) {
        int w=x2-x, h=y2-y; if(w<=0||h<=0)return; int r=Math.min(WaypointsMod.smoothUiEdges?Math.max(radius,8):Math.min(radius,3),Math.min(w/2,h/2));
        g.fill(x+r,y,x2-r,y2,color); g.fill(x,y+r,x2,y2-r,color);
        for(int row=0;row<r;row++){int inset=(int)Math.ceil(r-Math.sqrt(Math.max(0,r*r-Math.pow(r-row-.5,2))));g.fill(x+inset,y+row,x2-inset,y+row+1,color);g.fill(x+inset,y2-row-1,x2-inset,y2-row,color);}
    }

    @Override public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        if (event.button() == 1) {
            int toolX = mapX + 12;
            int toolY = mapY + 12;
            if (event.x() >= toolX && event.x() < toolX + 28 && event.y() >= toolY && event.y() < toolY + 28) {
                zoom = Math.min(200, zoom + 20);
                return true;
            }
            if (event.x() >= toolX && event.x() < toolX + 28 && event.y() >= toolY + 35 && event.y() < toolY + 63) {
                zoom = Math.max(40, zoom - 20);
                return true;
            }
            if (event.x() >= toolX && event.x() < toolX + 28 && event.y() >= toolY + 70 && event.y() < toolY + 98) {
                showContours = !showContours;
                return true;
            }
            if (event.x() <= railWidth) {
                if (event.y() >= 112 && event.y() < 148) { Minecraft.getInstance().gui.setScreen(new WaypointScreen(0)); return true; }
                if (event.y() >= 202 && event.y() < 238) { Minecraft.getInstance().gui.setScreen(new WaypointSettingsScreen()); return true; }
                if (event.y() >= 247 && event.y() < 283) { Minecraft.getInstance().gui.setScreen(new WaypointAboutScreen()); return true; }
            }
        }
        return super.mouseClicked(event, doubleClick);
    }
}
