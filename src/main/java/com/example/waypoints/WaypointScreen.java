package com.example.waypoints;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.List;

/**
 * Waypoint directory. This is a deliberately different layout from the old
 * centered stack: a narrow navigation rail controls scope and visibility,
 * while the larger workspace contains the saved-location list.
 *
 * All decoration is a small, fixed number of fills/text calls. No per-frame
 * texture generation, animation loop, or per-row widget tree is used.
 */
public final class WaypointScreen extends Screen {
    private static final int[] FILTER_WIDTHS = {40, 70, 50, 34, 34};
    private static final String[] FILTER_LABELS = {"HERE", "OVERWORLD", "NETHER", "END", "ALL"};
    private static final String[] DIM_LABELS = {"Current world", "Overworld", "Nether", "The End", "All dimensions"};

    // A warm graphite / brass / ice palette, intentionally unlike the previous cyan-violet skin.
    private static final int BACKDROP = 0xC9070B11;
    private static final int FRAME = 0xFF34404A;
    private static final int PANEL = 0xF20D141D;
    private static final int RAIL = 0xFF111B25;
    private static final int CARD = 0xFF18232E;
    private static final int CARD_HOVER = 0xFF22313D;
    private static final int EDGE = 0xFF30414D;
    private static final int TEXT = 0xFFF1F0EA;
    private static final int MUTED = 0xFF9AA8B2;
    private static final int QUIET = 0xFF657681;
    private static final int AMBER = 0xFFFFB86B;
    private static final int AMBER_DARK = 0xFF47301D;
    private static final int ICE = 0xFF8BD6DD;
    private static final int DANGER = 0xFFEF7F80;

    private final int requestedPage;
    private int left;
    private int top;
    private int right;
    private int bottom;
    private int panelWidth;
    private int panelHeight;
    private int railWidth;
    private int contentX;
    private int contentRight;
    private int contentWidth;
    private int rowsPerPage;
    private int rowHeight;
    private int listTop;
    private int footerY;
    private boolean wideLayout;
    private List<Waypoint> entries = List.of();

    public WaypointScreen(int page) {
        super(Component.literal("Waypoint Directory"));
        this.requestedPage = page;
    }

    static void openMenu() { open(0); }

    static void open(int page) {
        Minecraft.getInstance().gui.setScreen(new WaypointScreen(page));
    }

    private int pages() {
        return Math.max(1, (entries.size() + rowsPerPage - 1) / rowsPerPage);
    }

    private int currentPage() {
        return Math.max(0, Math.min(requestedPage, pages() - 1));
    }

    private int selectedFilter() {
        return Math.max(0, Math.min(4, WaypointsMod.menuDimensionMode));
    }

    private int rowY(int indexOnPage) {
        return listTop + indexOnPage * rowHeight;
    }

    public void extractBlurredBackground(GuiGraphicsExtractor g) { }

    @Override
    public void extractBackground(GuiGraphicsExtractor g, int mouseX, int mouseY, float delta) {
        g.fill(0, 0, width, height, BACKDROP);
        // A restrained pair of guide lines adds structure without animating or adding noise.
        g.fill(width / 2 - 1, 0, width / 2 + 1, height, 0x10000000);
        g.fill(0, height / 2, width, height / 2 + 1, 0x10000000);
    }

    @Override
    protected void init() {
        panelWidth = Math.min(560, Math.max(300, width - 20));
        panelHeight = Math.min(334, Math.max(220, height - 18));
        left = (width - panelWidth) / 2;
        top = (height - panelHeight) / 2;
        right = left + panelWidth;
        bottom = top + panelHeight;
        wideLayout = panelWidth >= 500 && panelHeight >= 300;
        railWidth = wideLayout ? 150 : 0;
        contentX = wideLayout ? left + railWidth + 18 : left + 14;
        contentRight = right - 14;
        contentWidth = contentRight - contentX;
        rowHeight = wideLayout ? 45 : 39;
        rowsPerPage = wideLayout ? 4 : Math.max(1, Math.min(4, (panelHeight - 150) / rowHeight));
        listTop = top + (wideLayout ? 82 : 113);
        footerY = bottom - 34;

        // Read the scoped list once when opening the screen. Idle frames only render this snapshot.
        entries = WaypointsMod.menuWaypoints();
        int pageNow = currentPage();
        int start = pageNow * rowsPerPage;
        int end = Math.min(entries.size(), start + rowsPerPage);
        hit(right - 28, top + 12, 16, 16, this::onClose);

        if (wideLayout) {
            hit(left + 14, top + 70, railWidth - 28, 31,
                    () -> Minecraft.getInstance().gui.setScreen(new WaypointEditScreen(null, pageNow)));
            for (int i = 0; i < FILTER_LABELS.length; i++) {
                final int mode = i;
                int y = top + 116 + i * 25;
                hit(left + 12, y, railWidth - 24, 22, () -> {
                    WaypointsMod.menuDimensionMode = mode;
                    open(0);
                });
            }
            hit(left + 14, bottom - 58, railWidth - 28, 22,
                    () -> WaypointsMod.showWaypointBeams = !WaypointsMod.showWaypointBeams);
            hit(left + 14, bottom - 32, railWidth - 28, 22,
                    () -> WaypointsMod.showWaypointLabels = !WaypointsMod.showWaypointLabels);
        } else {
            int filterX = left + 12;
            int filterY = top + 48;
            for (int i = 0; i < FILTER_LABELS.length; i++) {
                final int mode = i;
                int w = FILTER_WIDTHS[i];
                int x = filterX;
                hit(x, filterY, w, 21, () -> {
                    WaypointsMod.menuDimensionMode = mode;
                    open(0);
                });
                filterX += w + 4;
            }
            hit(contentRight - 110, top + 15, 94, 24,
                    () -> Minecraft.getInstance().gui.setScreen(new WaypointEditScreen(null, pageNow)));
            hit(left + 14, top + 77, 96, 21,
                    () -> WaypointsMod.showWaypointBeams = !WaypointsMod.showWaypointBeams);
            hit(left + 116, top + 77, 106, 21,
                    () -> WaypointsMod.showWaypointLabels = !WaypointsMod.showWaypointLabels);
        }

        for (int i = start; i < end; i++) {
            final Waypoint waypoint = entries.get(i);
            int y = rowY(i - start);
            int editX = contentRight - 76;
            int deleteX = contentRight - 34;
            hit(editX, y + (rowHeight - 23) / 2, 34, 23,
                    () -> Minecraft.getInstance().gui.setScreen(new WaypointEditScreen(waypoint, pageNow)));
            hit(deleteX, y + (rowHeight - 23) / 2, 24, 23, () -> {
                WaypointsMod.remove(waypoint);
                open(pageNow);
            });
        }

        int navX = wideLayout ? contentX : left + 14;
        hit(navX, footerY, 27, 22, () -> { if (pageNow > 0) open(pageNow - 1); });
        hit(navX + 33, footerY, 27, 22, () -> { if (pageNow < pages() - 1) open(pageNow + 1); });
        hit(right - 78, footerY, 64, 22, this::onClose);
    }

    private void hit(int x, int y, int w, int h, Runnable action) {
        addRenderableWidget(Button.builder(Component.literal(""), button -> action.run())
                .bounds(x, y, w, h).build());
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float delta) {
        super.extractRenderState(g, mouseX, mouseY, delta);

        // Outer frame and split navigation rail.
        rounded(g, left - 1, top - 1, right + 1, bottom + 1, FRAME, 13);
        rounded(g, left, top, right, bottom, PANEL, 12);
        if (wideLayout) {
            rounded(g, left + 1, top + 1, left + railWidth, bottom - 1, RAIL, 11);
            g.fill(left + railWidth, top + 14, left + railWidth + 1, bottom - 14, 0x55374752);
            drawRail(g, mouseX, mouseY);
            drawWorkspaceHeader(g, mouseX, mouseY, true);
        } else {
            drawWorkspaceHeader(g, mouseX, mouseY, false);
            drawCompactFilters(g, mouseX, mouseY);
        }

        drawRows(g, mouseX, mouseY);
        drawFooter(g, mouseX, mouseY);
    }

    private void drawRail(GuiGraphicsExtractor g, int mx, int my) {
        int markX = left + 15;
        int markY = top + 17;
        rounded(g, markX, markY, markX + 29, markY + 29, AMBER_DARK, 8);
        g.fill(markX + 8, markY + 7, markX + 21, markY + 9, AMBER);
        g.fill(markX + 8, markY + 12, markX + 18, markY + 14, AMBER);
        g.fill(markX + 8, markY + 17, markX + 15, markY + 19, AMBER);
        g.text(font, "WAYPOINTS", left + 51, top + 18, TEXT, true);
        g.text(font, "FIELD ATLAS", left + 51, top + 30, MUTED, false);

        railButton(g, mx, my, left + 14, top + 70, railWidth - 28, 31,
                "+   CREATE POINT", true, false);
        g.text(font, "DESTINATION SCOPE", left + 15, top + 104, QUIET, false);

        int selected = selectedFilter();
        for (int i = 0; i < FILTER_LABELS.length; i++) {
            int y = top + 116 + i * 25;
            boolean active = selected == i;
            boolean hover = inside(mx, my, left + 12, y, railWidth - 24, 22);
            rounded(g, left + 12, y, left + railWidth - 12, y + 22,
                    active ? AMBER_DARK : (hover ? CARD_HOVER : RAIL), 5);
            if (active) g.fill(left + 12, y + 5, left + 15, y + 17, AMBER);
            g.text(font, FILTER_LABELS[i], left + 23, y + 7,
                    active ? TEXT : MUTED, active);
        }

        g.fill(left + 14, bottom - 64, left + railWidth - 14, bottom - 63, 0x55374752);
        railToggle(g, mx, my, left + 14, bottom - 58, railWidth - 28, 22,
                "BEACON BEAM", WaypointsMod.showWaypointBeams, AMBER);
        railToggle(g, mx, my, left + 14, bottom - 32, railWidth - 28, 22,
                "NAME LABELS", WaypointsMod.showWaypointLabels, ICE);
    }

    private void drawWorkspaceHeader(GuiGraphicsExtractor g, int mx, int my, boolean wide) {
        int headingX = contentX;
        String title = "Saved locations";
        g.text(font, wide ? "NAVIGATION / PERSONAL MAP" : "FIELD ATLAS / WAYPOINTS",
                headingX, top + 15, QUIET, false);
        g.text(font, title, headingX, top + 29, TEXT, true);
        String activeScope = DIM_LABELS[selectedFilter()];
        g.text(font, activeScope.toUpperCase(java.util.Locale.ROOT), headingX, top + 45, AMBER, false);

        if (wide) {
            String count = entries.size() + (entries.size() == 1 ? " saved point" : " saved points");
            g.text(font, count, right - 46 - font.width(count), top + 23, MUTED, false);
        } else {
            int addX = contentRight - 110;
            drawActionButton(g, mx, my, addX, top + 15, 94, 24, "+ NEW", true, false);
        }
        int closeX = right - 28;
        rounded(g, closeX, top + 12, closeX + 16, top + 28,
                inside(mx, my, closeX, top + 12, 16, 16) ? CARD_HOVER : CARD, 5);
        g.text(font, "X", closeX + 5, top + 16, MUTED, true);
    }

    private void drawCompactFilters(GuiGraphicsExtractor g, int mx, int my) {
        int x = left + 12;
        int y = top + 48;
        for (int i = 0; i < FILTER_LABELS.length; i++) {
            int w = FILTER_WIDTHS[i];
            boolean active = selectedFilter() == i;
            boolean hover = inside(mx, my, x, y, w, 21);
            rounded(g, x, y, x + w, y + 21,
                    active ? AMBER_DARK : (hover ? CARD_HOVER : CARD), 5);
            if (active) g.fill(x + 5, y + 5, x + 7, y + 16, AMBER);
            g.text(font, FILTER_LABELS[i], x + (active ? 10 : (w - font.width(FILTER_LABELS[i])) / 2), y + 7,
                    active ? TEXT : MUTED, active);
            x += w + 4;
        }
        compactToggle(g, mx, my, left + 14, top + 77, 96,
                "BEAM", WaypointsMod.showWaypointBeams, AMBER);
        compactToggle(g, mx, my, left + 116, top + 77, 106,
                "LABELS", WaypointsMod.showWaypointLabels, ICE);
    }

    private void drawRows(GuiGraphicsExtractor g, int mx, int my) {
        int page = currentPage();
        int start = page * rowsPerPage;
        int end = Math.min(entries.size(), start + rowsPerPage);
        if (entries.isEmpty()) {
            int y = listTop + 5;
            int emptyBottom = Math.min(y + rowHeight * 2 + 12, footerY - 9);
            rounded(g, contentX, y, contentRight, emptyBottom, CARD, 9);
            rounded(g, contentX + 14, y + 12, contentX + 44, y + 42, AMBER_DARK, 8);
            g.text(font, "+", contentX + 25, y + 22, AMBER, true);
            g.text(font, "No saved locations", contentX + 56, y + 16, TEXT, true);
            if (emptyBottom - y > 42) {
                g.text(font, "Create a point and its beacon appears in your world.",
                        contentX + 56, y + 31, MUTED, false);
            }
            return;
        }

        for (int i = start; i < end; i++) {
            Waypoint waypoint = entries.get(i);
            int y = rowY(i - start);
            int rowH = rowHeight - 4;
            boolean hover = inside(mx, my, contentX, y, contentWidth, rowH);
            int color = 0xFF000000 | WaypointsMod.resolveColor(waypoint);
            rounded(g, contentX, y, contentRight, y + rowH,
                    hover ? CARD_HOVER : CARD, 7);
            rounded(g, contentX + 7, y + 7, contentX + 11, y + rowH - 7, color, 2);
            rounded(g, contentX + 16, y + 8, contentX + 35, y + rowH - 8,
                    0xFF26313A, 5);
            g.fill(contentX + 24, y + 11, contentX + 26, y + rowH - 11, color);

            String name = fitText(waypoint.name(), 132);
            g.text(font, name, contentX + 43, y + 7, TEXT, true);
            String coords = "X " + waypoint.x() + "   Y " + waypoint.y() + "   Z " + waypoint.z();
            g.text(font, fitText(coords, 130), contentX + 43, y + 22, MUTED, false);

            String dim = dimensionName(waypoint.dimension());
            int dimW = font.width(dim) + 12;
            int dimX = Math.max(contentX + 190, contentRight - 166 - dimW);
            rounded(g, dimX, y + 10, dimX + dimW, y + 27, 0xFF202C35, 5);
            g.text(font, dim, dimX + 6, y + 15, QUIET, false);

            drawActionButton(g, mx, my, contentRight - 76, y + (rowH - 23) / 2,
                    34, 23, "EDIT", false, false);
            drawActionButton(g, mx, my, contentRight - 34, y + (rowH - 23) / 2,
                    24, 23, "X", false, true);
        }
    }

    private String dimensionName(String dimension) {
        String d = WaypointsMod.normalizeDimension(dimension);
        if (d.equals("minecraft:overworld")) return "OVERWORLD";
        if (d.equals("minecraft:the_nether")) return "NETHER";
        if (d.equals("minecraft:the_end")) return "THE END";
        return "ALL";
    }

    private void drawFooter(GuiGraphicsExtractor g, int mx, int my) {
        if (wideLayout) g.fill(contentX, footerY - 8, contentRight, footerY - 7, 0x55374752);
        else g.fill(left + 14, footerY - 8, right - 14, footerY - 7, 0x55374752);

        int navX = wideLayout ? contentX : left + 14;
        drawActionButton(g, mx, my, navX, footerY, 27, 22, "<", false, false);
        drawActionButton(g, mx, my, navX + 33, footerY, 27, 22, ">", false, false);
        String pageText = (currentPage() + 1) + " / " + pages();
        int centerX = (navX + contentRight) / 2;
        g.text(font, pageText, centerX - font.width(pageText) / 2, footerY + 7, MUTED, false);
        drawActionButton(g, mx, my, right - 78, footerY, 64, 22, "CLOSE", false, false);
    }

    private void railToggle(GuiGraphicsExtractor g, int mx, int my, int x, int y, int w,
                            String label, boolean enabled, int accent) {
        boolean hover = inside(mx, my, x, y, w, 22);
        rounded(g, x, y, x + w, y + 22, hover ? CARD_HOVER : RAIL, 5);
        g.fill(x + 8, y + 7, x + 11, y + 15, enabled ? accent : QUIET);
        g.text(font, label, x + 17, y + 7, enabled ? TEXT : MUTED, false);
        int sw = x + w - 26;
        rounded(g, sw, y + 5, sw + 18, y + 17, enabled ? AMBER_DARK : 0xFF27323B, 6);
        int knobX = enabled ? sw + 10 : sw + 2;
        rounded(g, knobX, y + 7, knobX + 8, y + 15, enabled ? AMBER : QUIET, 4);
    }

    private void compactToggle(GuiGraphicsExtractor g, int mx, int my, int x, int y, int w,
                               String label, boolean enabled, int accent) {
        boolean hover = inside(mx, my, x, y, w, 21);
        rounded(g, x, y, x + w, y + 21, hover ? CARD_HOVER : CARD, 5);
        g.fill(x + 7, y + 7, x + 10, y + 14, enabled ? accent : QUIET);
        g.text(font, label, x + 15, y + 7, enabled ? TEXT : MUTED, false);
        String state = enabled ? "ON" : "OFF";
        g.text(font, state, x + w - font.width(state) - 7, y + 7, enabled ? accent : QUIET, false);
    }

    private void railButton(GuiGraphicsExtractor g, int mx, int my, int x, int y, int w, int h,
                            String label, boolean primary, boolean danger) {
        drawActionButton(g, mx, my, x, y, w, h, label, primary, danger);
    }

    private void drawActionButton(GuiGraphicsExtractor g, int mx, int my, int x, int y,
                                  int w, int h, String label, boolean primary, boolean danger) {
        boolean hover = inside(mx, my, x, y, w, h);
        int fill = primary ? (hover ? 0xFFFFC987 : AMBER)
                : danger ? (hover ? 0xFF593034 : 0xFF352329)
                : (hover ? CARD_HOVER : CARD);
        int textColor = primary ? 0xFF21150B : danger ? DANGER : TEXT;
        rounded(g, x, y, x + w, y + h, fill, Math.min(7, h / 2));
        if (primary) {
            g.fill(x + 8, y + 6, x + 10, y + h - 6, 0x553D2716);
        } else if (!danger) {
            g.fill(x + 6, y + 1, x + w - 6, y + 2, 0x3020303A);
        }
        int textWidth = font.width(label);
        g.text(font, label, x + (w - textWidth) / 2, y + (h - 8) / 2, textColor, primary);
    }

    private String fitText(String value, int maxWidth) {
        if (font.width(value) <= maxWidth) return value;
        String clipped = value;
        while (clipped.length() > 3 && font.width(clipped + "…") > maxWidth) {
            clipped = clipped.substring(0, clipped.length() - 1);
        }
        return clipped + "…";
    }

    private static void rounded(GuiGraphicsExtractor g, int x1, int y1, int x2, int y2,
                                int color, int radiusWanted) {
        int w = x2 - x1;
        int h = y2 - y1;
        if (w <= 0 || h <= 0) return;
        int r = Math.min(radiusWanted, Math.min(w / 2, h / 2));
        if (r <= 0) { g.fill(x1, y1, x2, y2, color); return; }
        g.fill(x1 + r, y1, x2 - r, y2, color);
        g.fill(x1, y1 + r, x2, y2 - r, color);
        for (int row = 0; row < r; row++) {
            double dy = r - row - 0.5;
            int inset = (int) Math.ceil(r - Math.sqrt(Math.max(0.0, r * r - dy * dy)));
            g.fill(x1 + inset, y1 + row, x2 - inset, y1 + row + 1, color);
            g.fill(x1 + inset, y2 - row - 1, x2 - inset, y2 - row, color);
        }
    }

    private static boolean inside(int mx, int my, int x, int y, int w, int h) {
        return mx >= x && mx < x + w && my >= y && my < y + h;
    }
}
