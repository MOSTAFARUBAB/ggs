package com.example.waypoints;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

/** Waypoint dashboard matching the supplied Waypoint Manager dashboard reference. */
public final class WaypointScreen extends Screen {
    private record Hit(int x, int y, int w, int h, Runnable action) {
        boolean contains(double px, double py) { return WaypointUiChrome.inside(px, py, x, y, w, h); }
    }

    private final List<Hit> hits = new ArrayList<>();
    private final int requestedPage;
    private int rail, top, left, right, contentW;
    private int titleY, statsY, statsH, sectionY, toolbarY, filtersY, gridY, footerY;
    private int cardW, cardH, cols = 3, pageSize = 6, page;
    private EditBox search;
    private String query;
    private static int dimensionFilter; // 0 all, 1 overworld, 2 nether, 3 end
    private static boolean sortName;
    private static boolean listView;
    private List<Waypoint> all = List.of();
    private List<Waypoint> filtered = List.of();
    private String lastAddedName = "—";

    public WaypointScreen(int page) { this(page, ""); }
    private WaypointScreen(int page, String query) {
        super(Component.literal("Waypoints"));
        this.requestedPage = page;
        this.query = query == null ? "" : query;
    }

    static void openMenu() { open(0); }
    static void open(int page) { Minecraft.getInstance().gui.setScreen(new WaypointScreen(page)); }

    public void extractBlurredBackground(GuiGraphicsExtractor g) { }
    @Override public void extractBackground(GuiGraphicsExtractor g, int mx, int my, float delta) {
        g.fill(0, 0, width, height, WaypointUiChrome.BG);
    }

    @Override protected void init() {
        clearWidgets(); hits.clear();
        rail = WaypointUiChrome.railWidth(width, height);
        top = WaypointUiChrome.topHeight(height);
        left = rail + 33;
        right = width - 30;
        contentW = Math.max(360, right - left);
        titleY = top + 32;
        statsY = titleY + 72;
        statsH = Math.max(80, Math.min(100, height / 8));
        sectionY = statsY + statsH + 28;
        toolbarY = sectionY + 37;
        filtersY = toolbarY + 51;
        gridY = filtersY + 43;
        footerY = height - 104;
        cols = listView ? 1 : (contentW >= 720 ? 3 : contentW >= 490 ? 2 : 1);
        int rows = listView ? 6 : 2;
        cardW = (contentW - (cols - 1) * 12) / cols;
        cardH = listView
                ? Math.max(30, (footerY - gridY - 6 * (rows - 1)) / rows)
                : Math.max(96, Math.min(150, (footerY - gridY - 12 * (rows - 1)) / rows));
        pageSize = listView ? 6 : cols * rows;

        all = new ArrayList<>(WaypointsMod.menuWaypointsAllDimensions());
        lastAddedName = all.isEmpty() ? "—" : all.get(all.size() - 1).name();
        if (sortName) all.sort(Comparator.comparing(Waypoint::name, String.CASE_INSENSITIVE_ORDER));
        refreshFiltered();
        page = Math.max(0, Math.min(requestedPage, Math.max(0, (filtered.size() - 1) / Math.max(1, pageSize))));

        int ny = WaypointUiChrome.navY(height);
        hits.add(new Hit(20, ny, rail - 40, 36, () -> open(0)));
        hits.add(new Hit(20, ny + 43, rail - 40, 36, () -> Minecraft.getInstance().gui.setScreen(new WaypointSettingsScreen())));
        hits.add(new Hit(right - 130, titleY - 2, 130, 36, () -> Minecraft.getInstance().gui.setScreen(new WaypointEditScreen(null, currentPage()))));

        hits.add(new Hit(left, filtersY, 100, 29, () -> setDimension(0)));
        int chipX = left + 108;
        for (int mode = 1; mode <= 3; mode++) {
            final int m = mode;
            int chipW = mode == 1 ? 104 : mode == 2 ? 99 : 88;
            hits.add(new Hit(chipX, filtersY, chipW, 29, () -> setDimension(m)));
            chipX += chipW + 8;
        }

        int importW = 72, exportW = 76;
        hits.add(new Hit(right - importW - exportW - 10, sectionY - 3, importW, 30,
                () -> report(WaypointsMod.importWaypointsFromFile())));
        hits.add(new Hit(right - exportW, sectionY - 3, exportW, 30,
                () -> report(WaypointsMod.exportWaypointsToFile())));

        int searchW = Math.max(230, contentW - 206);
        search = new EditBox(font, left + 10, toolbarY + 8, searchW - 20, 20, Component.literal("Search by name or coordinates..."));
        search.setMaxLength(64); search.setValue(query); search.setBordered(false); search.setTextColor(0x00000000);
        search.setResponder(v -> { query = v; page = 0; refreshFiltered(); });
        addRenderableWidget(search);
        hits.add(new Hit(left + searchW + 8, toolbarY, 128, 36, () -> {
            sortName = !sortName;
            Minecraft.getInstance().gui.setScreen(new WaypointScreen(0, query));
        }));
        hits.add(new Hit(right - 62, toolbarY, 62, 36, () -> {
            listView = !listView;
            Minecraft.getInstance().gui.setScreen(new WaypointScreen(0, query));
        }));

        // Fixed slot targets remain valid while the search box changes the filtered list.
        for (int slot = 0; slot < pageSize; slot++) {
            int col = listView ? 0 : slot % cols, row = listView ? slot : slot / cols;
            int x = left + col * (cardW + 12), y = gridY + row * (cardH + (listView ? 6 : 12));
            final int visibleSlot = slot;
            hits.add(new Hit(x, y, cardW, cardH, () -> editVisibleSlot(visibleSlot)));
            int actionTop = y + cardH - (cardH < 130 && !listView ? 20 : 28);
            int actionHeight = cardH < 130 && !listView ? 18 : 22;
            hits.add(new Hit(x + cardW - 61, actionTop, 24, actionHeight, () -> copyVisibleSlot(visibleSlot)));
            hits.add(new Hit(x + cardW - 34, actionTop, 24, actionHeight, () -> editVisibleSlot(visibleSlot)));
        }
        int pagerX = right - 164;
        hits.add(new Hit(pagerX, footerY + 6, 24, 24, () -> { if (pageCount() > 1) changePage(-1); }));
        hits.add(new Hit(pagerX + 138, footerY + 6, 24, 24, () -> { if (pageCount() > 1) changePage(1); }));
        for (int i = 0; i < 4; i++) {
            final int slot = i;
            hits.add(new Hit(pagerX + 34 + i * 25, footerY + 6, 24, 24, () -> {
                int targetPage = pagerStart() + slot;
                if (targetPage < pageCount())
                    Minecraft.getInstance().gui.setScreen(new WaypointScreen(targetPage, query));
            }));
        }
    }

    private void setDimension(int mode) { dimensionFilter = mode; page = 0; Minecraft.getInstance().gui.setScreen(new WaypointScreen(0, query)); }
    private void refreshFiltered() {
        String q = query == null ? "" : query.trim().toLowerCase(Locale.ROOT);
        List<Waypoint> out = new ArrayList<>();
        for (Waypoint w : all) {
            String d = WaypointsMod.normalizeDimension(w.dimension());
            boolean match = dimensionFilter == 0 || dimensionFilter == 1 && d.endsWith(":overworld")
                    || dimensionFilter == 2 && d.endsWith(":the_nether") || dimensionFilter == 3 && d.endsWith(":the_end");
            if (!match) continue;
            String text = (w.name() + " " + w.dimension() + " " + w.x() + " " + w.y() + " " + w.z()).toLowerCase(Locale.ROOT);
            if (q.isEmpty() || text.contains(q)) out.add(w);
        }
        filtered = List.copyOf(out);
    }
    private int currentPage() { return Math.max(0, Math.min(page, Math.max(0, (filtered.size() - 1) / Math.max(1, pageSize)))); }
    private int pageCount() { return Math.max(1, (filtered.size() + Math.max(1, pageSize) - 1) / Math.max(1, pageSize)); }
    private int pagerStart() { return Math.max(0, Math.min(currentPage() - 1, Math.max(0, pageCount() - 4))); }
    private void changePage(int d) { Minecraft.getInstance().gui.setScreen(new WaypointScreen(Math.max(0, Math.min(pageCount() - 1, currentPage() + d)), query)); }
    private void edit(Waypoint w) { Minecraft.getInstance().gui.setScreen(new WaypointEditScreen(w, currentPage())); }
    private void editVisibleSlot(int slot) {
        int index = currentPage() * pageSize + slot;
        if (index >= 0 && index < filtered.size()) edit(filtered.get(index));
    }
    private void copyVisibleSlot(int slot) {
        int index = currentPage() * pageSize + slot;
        if (index < 0 || index >= filtered.size()) return;
        Waypoint w = filtered.get(index);
        Minecraft.getInstance().keyboardHandler.setClipboard(w.x() + ", " + w.y() + ", " + w.z());
        report("Copied coordinates: " + w.x() + ", " + w.y() + ", " + w.z());
    }
    private void report(String s) { Minecraft.getInstance().gui.chatListener().handleSystemMessage(Component.literal(s), false); }

    @Override public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        if (event.button() == 1) {
            for (int i = hits.size() - 1; i >= 0; i--) if (hits.get(i).contains(event.x(), event.y())) { hits.get(i).action().run(); return true; }
        }
        return super.mouseClicked(event, doubleClick);
    }

    @Override public void extractRenderState(GuiGraphicsExtractor g, int mx, int my, float delta) {
        WaypointUiChrome.drawFrame(g, font, width, height, mx, my, false, "Waypoints");
        WaypointUiText.drawHeading(g, font, "Every place, a waypoint.", left, titleY, WaypointUiChrome.TEXT);
        WaypointUiText.draw(g, font, "Keep your world organized. Never lose a place worth returning to.", left, titleY + 30, WaypointUiChrome.MUTED, false);
        WaypointUiChrome.button(g, font, mx, my, right - 130, titleY - 2, 130, 36, "+  Create waypoint", true, false);

        int gap = 12, statW = (contentW - gap * 2) / 3;
        drawStat(g, left, statsY, statW, statsH, "Total waypoints", Integer.toString(all.size()), "Across your entire world", "◎");
        long dimensions = all.stream().map(w -> WaypointsMod.normalizeDimension(w.dimension())).distinct().count();
        drawStat(g, left + statW + gap, statsY, statW, statsH, "Dimensions explored", Long.toString(dimensions), "Overworld, Nether & End", "◈");
        drawStat(g, left + (statW + gap) * 2, statsY, statW, statsH, "Last added", WaypointUiChrome.fit(font, lastAddedName, statW - 30), "Saved in this world", "◷");

        WaypointUiText.drawHeading(g, font, "Your waypoints", left, sectionY + 3, WaypointUiChrome.TEXT);
        smallBadge(g, left + WaypointUiText.width(font, "Your waypoints") + 12, sectionY + 2, Integer.toString(all.size()));
        WaypointUiChrome.button(g, font, mx, my, right - 164, sectionY - 3, 72, 30, "↓  Import", false, false);
        WaypointUiChrome.button(g, font, mx, my, right - 82, sectionY - 3, 76, 30, "↑  Export", false, false);

        // Search, sort and grid/list controls.
        int searchW = Math.max(230, contentW - 206);
        drawSearch(g, left, toolbarY, searchW, mx, my);
        if (query.isBlank()) WaypointUiText.drawCompact(g, font, "Search by name or coordinates...", left + 40, toolbarY + 13, WaypointUiChrome.QUIET);
        WaypointUiChrome.button(g, font, mx, my, left + searchW + 8, toolbarY, 128, 36,
                sortName ? "↕  Name" : "↕  Recently added", false, false);
        WaypointUiChrome.button(g, font, mx, my, right - 62, toolbarY, 62, 36, listView ? "☷" : "▦", false, false);

        int x = left;
        drawFilter(g, x, filtersY, 100, "All dimensions", dimensionFilter == 0, countDimension(0)); x += 108;
        drawFilter(g, x, filtersY, 104, "●  Overworld", dimensionFilter == 1, countDimension(1)); x += 112;
        drawFilter(g, x, filtersY, 99, "●  The Nether", dimensionFilter == 2, countDimension(2)); x += 107;
        drawFilter(g, x, filtersY, 88, "●  The End", dimensionFilter == 3, countDimension(3));

        drawCards(g, mx, my);
        drawFooter(g, mx, my);
        super.extractRenderState(g, mx, my, delta);
        if (search != null) WaypointUiText.drawInput(g, font, search, left + 40, toolbarY + 11, searchW - 55);
    }

    private int countDimension(int mode) {
        if (mode == 0) return all.size();
        return (int)all.stream().filter(w -> {
            String d = WaypointsMod.normalizeDimension(w.dimension());
            return mode == 1 && d.endsWith(":overworld") || mode == 2 && d.endsWith(":the_nether") || mode == 3 && d.endsWith(":the_end");
        }).count();
    }

    private void drawStat(GuiGraphicsExtractor g, int x, int y, int w, int h, String label, String value, String sub, String icon) {
        WaypointUiChrome.panel(g, x, y, w, h);
        WaypointUiText.drawCompact(g, font, label, x + 16, y + 17, WaypointUiChrome.MUTED);
        WaypointUiText.drawHeading(g, font, value, x + 16, y + 39, WaypointUiChrome.TEXT);
        WaypointUiText.drawCompact(g, font, sub, x + 16, y + h - 22, WaypointUiChrome.QUIET);
        WaypointUiText.draw(g, font, icon, x + w - 27, y + 15, WaypointUiChrome.MUTED, false);
    }

    private void smallBadge(GuiGraphicsExtractor g, int x, int y, String text) {
        WaypointUiChrome.round(g, x, y, x + WaypointUiText.width(font, text) + 13, y + 18, 0xFF1D2225, 5);
        WaypointUiText.drawCompact(g, font, text, x + 6, y + 4, WaypointUiChrome.MUTED);
    }

    private void drawSearch(GuiGraphicsExtractor g, int x, int y, int w, int mx, int my) {
        WaypointUiChrome.round(g, x, y, x + w, y + 36, WaypointUiChrome.FIELD, 7);
        WaypointUiChrome.border(g, x, y, x + w, y + 36, WaypointUiChrome.EDGE);
        WaypointUiText.draw(g, font, "⌕", x + 13, y + 9, WaypointUiChrome.MUTED, false);
    }

    private void drawFilter(GuiGraphicsExtractor g, int x, int y, int w, String label, boolean active, int count) {
        int bg = active ? 0xFF1E2427 : WaypointUiChrome.BG;
        WaypointUiChrome.round(g, x, y, x + w, y + 29, bg, 6);
        if (active) WaypointUiChrome.border(g, x, y, x + w, y + 29, WaypointUiChrome.EDGE);
        int textX = x + 9;
        if (label.startsWith("●")) {
            int dot = label.contains("Overworld") ? WaypointUiChrome.GREEN
                    : label.contains("Nether") ? 0xFFFFB65C : 0xFFA98AFF;
            g.fill(x + 10, y + 12, x + 14, y + 16, dot);
            textX = x + 19;
            label = label.substring(1).trim();
        }
        WaypointUiText.drawCompact(g, font, label, textX, y + 8, active ? WaypointUiChrome.TEXT : WaypointUiChrome.MUTED);
        String c = Integer.toString(count);
        WaypointUiText.drawCompact(g, font, c, x + w - WaypointUiText.compactWidth(font, c) - 8, y + 8, WaypointUiChrome.QUIET);
    }

    private void drawCards(GuiGraphicsExtractor g, int mx, int my) {
        if (filtered.isEmpty()) {
            WaypointUiChrome.panel(g, left, gridY, contentW, 120);
            WaypointUiText.draw(g, font, query.isEmpty() ? "No waypoints yet" : "No matching waypoints", left + 20, gridY + 24, WaypointUiChrome.TEXT, false);
            WaypointUiText.draw(g, font, "Create a waypoint to save a place in this world.", left + 20, gridY + 49, WaypointUiChrome.MUTED, false);
            return;
        }
        int start = currentPage() * pageSize, end = Math.min(filtered.size(), start + pageSize);
        for (int i = start; i < end; i++) {
            Waypoint w = filtered.get(i);
            int slot = i - start, col = listView ? 0 : slot % cols, row = listView ? slot : slot / cols;
            int x = left + col * (cardW + 12), y = gridY + row * (cardH + (listView ? 6 : 12));
            WaypointUiChrome.panel(g, x, y, cardW, cardH);
            int color = WaypointsMod.resolveColor(w);
            WaypointUiChrome.round(g, x + 13, y + Math.max(7, (cardH - 26) / 2), x + 39, y + Math.max(33, (cardH - 26) / 2 + 26), 0xFF202A22, 7);
            WaypointUiChrome.round(g, x + 22, y + Math.max(13, (cardH - 26) / 2 + 6), x + 30, y + Math.max(25, (cardH - 26) / 2 + 18), color, 5);
            boolean compactCard = !listView && cardH < 130;
            WaypointUiText.draw(g, font, WaypointUiChrome.fit(font, w.name(), listView ? 180 : cardW - 104), x + 49,
                    y + (listView || compactCard ? 8 : 17), WaypointUiChrome.TEXT, true);
            String desc = dimension(w.dimension()) + " · Saved in this world";
            if (!listView || cardH >= 42)
                WaypointUiText.drawCompact(g, font, desc, x + 49, y + (listView ? 26 : compactCard ? 25 : 34), WaypointUiChrome.MUTED);
            if (listView) {
                String coords = "X  " + format(w.x()) + "     Y  " + format(w.y()) + "     Z  " + format(w.z());
                WaypointUiText.drawCompact(g, font, coords, x + Math.max(235, cardW - 250), y + 17, WaypointUiChrome.TEXT);
                WaypointUiText.draw(g, font, "↗", x + cardW - 30, y + 14, WaypointUiChrome.MUTED, false);
            } else {
                WaypointUiText.draw(g, font, "···", x + cardW - 29, y + 18, WaypointUiChrome.MUTED, false);
                int gap = 7, tileW = (cardW - 36 - gap * 2) / 3;
                int tileY = y + (compactCard ? 43 : 58);
                int tileH = compactCard ? 32 : 44;
                drawCoordinate(g, x + 16, tileY, tileW, tileH, "X", format(w.x()));
                drawCoordinate(g, x + 16 + tileW + gap, tileY, tileW, tileH, "Y", format(w.y()));
                drawCoordinate(g, x + 16 + (tileW + gap) * 2, tileY, tileW, tileH, "Z", format(w.z()));
                int bottom = y + cardH - (compactCard ? 20 : 23);
                g.fill(x + 18, bottom + 7, x + 22, bottom + 11, color);
                WaypointUiText.drawCompact(g, font, dimension(w.dimension()), x + 29, bottom + 4, WaypointUiChrome.MUTED);
                WaypointUiText.draw(g, font, "▢", x + cardW - 61, bottom + 3, WaypointUiChrome.MUTED, false);
                WaypointUiText.draw(g, font, "↗", x + cardW - 31, bottom + 3, WaypointUiChrome.MUTED, false);
            }
        }
    }

    private void drawCoordinate(GuiGraphicsExtractor g, int x, int y, int w, int h, String axis, String value) {
        WaypointUiChrome.round(g, x, y, x + w, y + h, WaypointUiChrome.FIELD, 5);
        WaypointUiText.drawCompact(g, font, axis, x + 9, y + (h <= 32 ? 4 : 7), WaypointUiChrome.QUIET);
        WaypointUiText.draw(g, font, value, x + 9, y + (h <= 32 ? 17 : 23), WaypointUiChrome.TEXT, false);
    }

    private String format(int value) { return String.format(Locale.ROOT, "%,d", value); }
    private String dimension(String raw) {
        String d = WaypointsMod.normalizeDimension(raw);
        if (d.endsWith(":the_nether")) return "The Nether";
        if (d.endsWith(":the_end")) return "The End";
        if (d.endsWith(":overworld")) return "Overworld";
        return raw == null || raw.isBlank() ? "Unknown dimension" : raw;
    }

    private void drawFooter(GuiGraphicsExtractor g, int mx, int my) {
        int start = currentPage() * pageSize;
        WaypointUiText.drawCompact(g, font, "Showing " + (filtered.isEmpty() ? 0 : start + 1) + "–" + Math.min(filtered.size(), start + pageSize)
                + " of " + filtered.size() + " waypoints", left, footerY + 12, WaypointUiChrome.QUIET);
        WaypointUiText.drawCompact(g, font, "⌨  A little less wandering. A lot more exploring.", left, height - 39, WaypointUiChrome.QUIET);
        if (pageCount() > 1) {
            int pagerX = right - 164;
            WaypointUiChrome.button(g, font, mx, my, pagerX, footerY + 6, 24, 24, "‹", false, false);
            int startPage = pagerStart();
            for (int slot = 0; slot < Math.min(4, pageCount()); slot++) {
                int p = startPage + slot;
                int px = pagerX + 34 + slot * 25;
                boolean active = p == currentPage();
                if (active) WaypointUiChrome.round(g, px, footerY + 6, px + 24, footerY + 30, 0xFF1D2225, 5);
                WaypointUiText.drawCompact(g, font, Integer.toString(p + 1), px + 9, footerY + 14, active ? WaypointUiChrome.TEXT : WaypointUiChrome.MUTED);
            }
            WaypointUiChrome.button(g, font, mx, my, pagerX + 138, footerY + 6, 24, 24, "›", false, false);
        }
        String signature = WaypointUiChrome.fit(font, "WAYPOINT MANAGER  /  v1.5.0", 150);
        WaypointUiText.drawCompact(g, font, signature, right - 150, height - 39, WaypointUiChrome.QUIET);
    }
}
