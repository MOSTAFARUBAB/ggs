package com.example.waypoints;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public final class WaypointScreen extends Screen {
    private static final int BG = 0xFF0B0C0E;
    private static final int RAIL = 0xFF101113;
    private static final int PANEL = 0xFF131518;
    private static final int PANEL_ALT = 0xFF0F1113;
    private static final int EDGE = 0xFF292D31;
    private static final int TEXT = 0xFFF2F5F5;
    private static final int MUTED = 0xFF9BA4A8;
    private static final int QUIET = 0xFF687278;
    private static final int ACCENT = 0xFFB7F36B;

    private final int requestedPage;
    private int page;
    private String searchQuery;
    private int railWidth, contentX, contentRight, contentWidth;
    private int headingY, statsY, statsHeight, sectionY, toolbarY, filtersY, gridY, footerY;
    private int columns, rows, cardWidth, cardHeight, pageSize;

    private record HitTarget(int x, int y, int width, int height, Runnable action) {
        boolean contains(double px, double py) {
            return px >= x && px < x + width && py >= y && py < y + height;
        }
    }

    private final List<HitTarget> hitTargets = new ArrayList<>();
    private final List<HitTarget> cardHitTargets = new ArrayList<>();
    private final List<HitTarget> paginationTargets = new ArrayList<>();
    private List<Waypoint> entries = List.of();
    private List<Waypoint> filtered = List.of();
    private EditBox searchBox;
    private static boolean sortByName;
    private static boolean listLayout;

    public WaypointScreen(int page) { this(page, ""); }

    private WaypointScreen(int page, String search) {
        super(Component.literal("Waypoint Manager"));
        this.requestedPage = page;
        this.page = page;
        this.searchQuery = search == null ? "" : search;
    }

    static void open(int page) { Minecraft.getInstance().gui.setScreen(new WaypointScreen(page)); }
    static void openMenu() { open(0); }

    @Override
    public void extractBackground(GuiGraphicsExtractor g, int mouseX, int mouseY, float delta) {
        g.fill(0, 0, width, height, BG);
    }

    @Override
    protected void init() {
        clearWidgets();
        hitTargets.clear();
        cardHitTargets.clear();
        paginationTargets.clear();
        railWidth = 190;
        int leftInset = 35;
        int rightInset = 35;
        contentX = railWidth + leftInset;
        contentRight = width - rightInset;
        contentWidth = Math.max(220, contentRight - contentX);
        headingY = 58;
        statsY = 110;
        statsHeight = 85;
        sectionY = statsY + statsHeight + 32;
        toolbarY = sectionY + 36;
        filtersY = toolbarY + 50;
        gridY = filtersY + 43;
        footerY = height - 75;
        columns = listLayout ? 1 : 3;
        rows = 2;
        cardWidth = (contentWidth - (columns - 1) * 15) / columns;
        cardHeight = 115;
        pageSize = columns * rows;

        entries = new ArrayList<>(WaypointsMod.menuWaypoints());
        if (sortByName) entries.sort((a, b) -> a.name().compareToIgnoreCase(b.name()));
        refreshFiltered();
        page = Math.max(0, Math.min(requestedPage, Math.max(0, (filtered.size() - 1) / pageSize)));

        // Sidebar navigation buttons
        button(20, 153, 150, 32, () -> Minecraft.getInstance().gui.setScreen(new WaypointScreen(0)));
        button(20, 194, 150, 32, () -> Minecraft.getInstance().gui.setScreen(new WaypointSettingsScreen()));

        // Create waypoint button (+ Create waypoint in top right)
        button(contentRight - 150, headingY + 2, 150, 38,
                () -> Minecraft.getInstance().gui.setScreen(new WaypointEditScreen(null, page)));

        int searchWidth = Math.max(70, contentWidth - 280);
        searchBox = new EditBox(font, contentX + 32, toolbarY + 9, searchWidth, 20, Component.literal("Search"));
        searchBox.setMaxLength(64);
        searchBox.setValue(searchQuery);
        searchBox.setBordered(false);
        searchBox.setTextColor(0x00000000);
        searchBox.setResponder(value -> {
            searchQuery = value;
            page = 0;
            refreshFiltered();
            rebuildCardTargets();
            rebuildPaginationTargets();
        });
        addRenderableWidget(searchBox);

        // Sort & Layout buttons
        button(contentRight - 210, toolbarY, 120, 38, () -> {
            sortByName = !sortByName;
            rebuildDashboard();
        });
        button(contentRight - 78, toolbarY, 78, 38, () -> {
            listLayout = !listLayout;
            rebuildDashboard();
        });

        // Dimension filters
        int filterX = contentX;
        int[] modes = {4, 1, 2, 3};
        int[] widths = {120, 95, 95, 80};
        for (int i = 0; i < modes.length; i++) {
            final int mode = modes[i];
            button(filterX, filtersY, widths[i], 32, () -> {
                WaypointsMod.menuDimensionMode = mode;
                page = 0;
                rebuildDashboard();
            });
            filterX += widths[i] + 8;
        }

        rebuildCardTargets();
        rebuildPaginationTargets();
    }

    private void rebuildDashboard() {
        Minecraft.getInstance().gui.setScreen(new WaypointScreen(page, searchQuery));
    }

    private void rebuildCardTargets() {
        int visibleCount = Math.max(0, Math.min(pageSize, filtered.size() - page * pageSize));
        cardHitTargets.clear();
        for (int slot = 0; slot < visibleCount; slot++) {
            final int visibleSlot = slot;
            int col = slot % columns;
            int row = slot / columns;
            int x = contentX + col * (cardWidth + 15);
            int y = gridY + row * (cardHeight + 15);
            cardHitTargets.add(new HitTarget(x, y, cardWidth, cardHeight, () -> editSlot(visibleSlot)));
        }
    }

    private void rebuildPaginationTargets() {
        paginationTargets.clear();
        if (pageCount() <= 1) return;
        int totalWidth = 140;
        int x = contentRight - totalWidth;
        paginationTargets.add(new HitTarget(x, footerY, 28, 28, () -> changePage(-1)));
        paginationTargets.add(new HitTarget(x + 112, footerY, 28, 28, () -> changePage(1)));
    }

    private Waypoint visibleSlot(int slot) {
        int index = page * pageSize + slot;
        return index >= 0 && index < filtered.size() ? filtered.get(index) : null;
    }

    private void editSlot(int slot) {
        Waypoint w = visibleSlot(slot);
        if (w != null) Minecraft.getInstance().gui.setScreen(new WaypointEditScreen(w, page));
    }

    private void refreshFiltered() {
        String q = searchQuery == null ? "" : searchQuery.trim().toLowerCase(Locale.ROOT);
        String current = WaypointsMod.normalizeDimension(WaypointsMod.currentDimension());
        List<Waypoint> result = new ArrayList<>();
        for (Waypoint w : entries) {
            String dimension = WaypointsMod.normalizeDimension(w.dimension());
            boolean dimensionMatches = switch (WaypointsMod.menuDimensionMode) {
                case 0 -> dimension.equals(current);
                case 1 -> dimension.equals("minecraft:overworld");
                case 2 -> dimension.equals("minecraft:the_nether");
                case 3 -> dimension.equals("minecraft:the_end");
                default -> true;
            };
            if (!dimensionMatches) continue;
            String value = (w.name() + " " + w.dimension() + " " + w.x() + " " + w.y() + " " + w.z()).toLowerCase(Locale.ROOT);
            if (q.isEmpty() || value.contains(q)) result.add(w);
        }
        filtered = List.copyOf(result);
    }

    private int pageCount() { return Math.max(1, (filtered.size() + pageSize - 1) / pageSize); }
    private void changePage(int delta) {
        int next = Math.max(0, Math.min(pageCount() - 1, page + delta));
        if (next != page) Minecraft.getInstance().gui.setScreen(new WaypointScreen(next, searchQuery));
    }

    private void button(int x, int y, int w, int h, Runnable action) {
        hitTargets.add(new HitTarget(x, y, w, h, action));
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        if (event.button() == 1) {
            for (var t : hitTargets) if (t.contains(event.x(), event.y())) { t.action().run(); return true; }
            for (var t : paginationTargets) if (t.contains(event.x(), event.y())) { t.action().run(); return true; }
            for (var t : cardHitTargets) if (t.contains(event.x(), event.y())) { t.action().run(); return true; }
        }
        return super.mouseClicked(event, doubleClick);
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor g, int mx, int my, float delta) {
        drawSidebar(g, mx, my);
        g.fill(railWidth, 0, width, 48, BG);
        g.fill(railWidth, 47, width, 48, EDGE);
        WaypointUiText.draw(g, font, worldTitle() + "  /  Waypoints", contentX, 17, MUTED, false);

        g.fill(width - 155, 18, width - 150, 23, ACCENT);
        WaypointUiText.draw(g, font, "Local workspace", width - 142, 16, MUTED, false);
        border(g, width - 42, 14, width - 30, 26, QUIET);
        WaypointUiText.draw(g, font, "?", width - 38, 16, MUTED, false);

        WaypointUiText.drawTitle(g, font, "Every place, a waypoint.", contentX, headingY, TEXT);
        WaypointUiText.draw(g, font, "Keep your world organized. Never lose a place worth returning to.", contentX, headingY + 30, MUTED, false);
        actionButton(g, mx, my, contentRight - 150, headingY + 2, 150, 38, "+ Create waypoint", true);

        // Stats cards
        int statW = (contentWidth - 30) / 3;
        statCard(g, contentX, statsY, statW, statsHeight, "Total waypoints", String.valueOf(entries.size()), "Across your entire world");
        statCard(g, contentX + statW + 15, statsY, statW, statsHeight, "Dimensions explored", "3", "Overworld, Nether & End");
        Waypoint last = entries.isEmpty() ? null : entries.get(entries.size() - 1);
        statCard(g, contentX + (statW + 15) * 2, statsY, statW, statsHeight, "Last added", last == null ? "-" : last.name(), "Today, 10:42 AM");

        WaypointUiText.draw(g, font, "Your waypoints", contentX, sectionY, TEXT, false);
        rounded(g, contentX + 120, sectionY - 2, contentX + 152, sectionY + 16, PANEL_ALT, 6);
        WaypointUiText.draw(g, font, String.valueOf(filtered.size()), contentX + 128, sectionY + 2, MUTED, false);

        // Search & Filter Toolbar
        rounded(g, contentX, toolbarY, contentX + Math.max(70, contentWidth - 280), toolbarY + 38, PANEL_ALT, 8);
        border(g, contentX, toolbarY, contentX + Math.max(70, contentWidth - 280), toolbarY + 38, EDGE);
        if (searchBox != null && searchBox.getValue().isEmpty() && !searchBox.isFocused()) {
            WaypointUiText.draw(g, font, "Search by name or coordinates...", contentX + 32, toolbarY + 12, QUIET, false);
        }
        actionButton(g, mx, my, contentRight - 210, toolbarY, 120, 38, sortByName ? "A-Z" : "Recently added", false);
        actionButton(g, mx, my, contentRight - 78, toolbarY, 78, 38, listLayout ? "List" : "Grid", false);

        int fx = contentX;
        String[] dims = {"All dimensions", "Overworld", "The Nether", "The End"};
        int[] dw = {120, 95, 95, 80};
        for (int i = 0; i < dims.length; i++) {
            boolean active = WaypointsMod.menuDimensionMode == (i == 0 ? 4 : i);
            rounded(g, fx, filtersY, fx + dw[i], filtersY + 32, active ? 0xFF26321F : PANEL_ALT, 6);
            if (active) border(g, fx, filtersY, fx + dw[i], filtersY + 32, 0xFF789D4C);
            WaypointUiText.draw(g, font, dims[i], fx + 14, filtersY + 10, active ? ACCENT : MUTED, false);
            fx += dw[i] + 8;
        }

        // Cards grid
        int start = page * pageSize;
        int end = Math.min(filtered.size(), start + pageSize);
        for (int i = start; i < end; i++) {
            Waypoint w = filtered.get(i);
            int slot = i - start;
            int col = slot % columns;
            int row = slot / columns;
            int cx = contentX + col * (cardWidth + 15);
            int cy = gridY + row * (cardHeight + 15);
            rounded(g, cx, cy, cx + cardWidth, cy + cardHeight, PANEL, 10);
            border(g, cx, cy, cx + cardWidth, cy + cardHeight, EDGE);
            rounded(g, cx + 16, cy + 16, cx + 44, cy + 44, 0xFF000000 | WaypointsMod.resolveColor(w), 8);
            WaypointUiText.draw(g, font, w.name().substring(0, 1).toUpperCase(), cx + 27, cy + 24, TEXT, false);
            WaypointUiText.draw(g, font, w.name(), cx + 54, cy + 16, TEXT, false);
            WaypointUiText.draw(g, font, w.dimension(), cx + 54, cy + 32, MUTED, false);

            int cw = (cardWidth - 48) / 3;
            coordBox(g, cx + 16, cy + 60, cw, "X", String.valueOf(w.x()));
            coordBox(g, cx + 24 + cw, cy + 60, cw, "Y", String.valueOf(w.y()));
            coordBox(g, cx + 32 + cw * 2, cy + 60, cw, "Z", String.valueOf(w.z()));
        }

        super.extractRenderState(g, mx, my, delta);
        WaypointUiText.drawInput(g, font, searchBox, contentX + 32, toolbarY + 11, searchBox.getWidth() - 10);
    }

    private void drawSidebar(GuiGraphicsExtractor g, int mx, int my) {
        g.fill(0, 0, railWidth, height, RAIL);
        g.fill(railWidth - 1, 0, railWidth, height, EDGE);
        rounded(g, 20, 15, 47, 42, ACCENT, 7);
        WaypointUiText.draw(g, font, "Waypoint", 56, 19, TEXT, false);
        WaypointUiText.draw(g, font, "M A N A G E R", 56, 31, MUTED, false);

        rounded(g, 15, 65, railWidth - 15, 115, PANEL, 8);
        WaypointUiText.draw(g, font, worldTitle(), 25, 76, TEXT, false);
        g.fill(25, 98, 29, 102, ACCENT);
        WaypointUiText.draw(g, font, "Java Edition | 26.3", 35, 96, MUTED, false);

        WaypointUiText.draw(g, font, "WORKSPACE", 15, 144, QUIET, false);
        navItem(g, mx, my, 15, 153, railWidth - 30, "Waypoints", true);
        navItem(g, mx, my, 15, 194, railWidth - 30, "Settings", false);

        int footerBottom = height - 70;
        int footerTop = footerBottom - 82;
        rounded(g, 15, footerTop, railWidth - 15, footerBottom, PANEL_ALT, 8);
        WaypointUiText.draw(g, font, "Your world. Your waypoints.", 25, footerTop + 12, TEXT, false);
        WaypointUiText.draw(g, font, "Stored locally, ready for", 25, footerTop + 28, MUTED, false);
        WaypointUiText.draw(g, font, "your next adventure.", 25, footerTop + 42, MUTED, false);
        g.fill(25, footerBottom - 18, 29, footerBottom - 14, ACCENT);
        WaypointUiText.draw(g, font, "All changes saved", 35, footerBottom - 20, MUTED, false);

        int profileTop = footerBottom + 10;
        rounded(g, 15, profileTop, railWidth - 15, profileTop + 40, 0xFF1B1E21, 8);
        WaypointUiText.draw(g, font, "AL", 25, profileTop + 12, TEXT, false);
        WaypointUiText.draw(g, font, "Alex", 56, profileTop + 8, TEXT, false);
        WaypointUiText.draw(g, font, "Explorer", 56, profileTop + 22, MUTED, false);
    }

    private void statCard(GuiGraphicsExtractor g, int x, int y, int w, int h, String label, String value, String note) {
        rounded(g, x, y, x + w, y + h, PANEL, 10);
        border(g, x, y, x + w, y + h, EDGE);
        WaypointUiText.draw(g, font, label, x + 16, y + 14, MUTED, false);
        WaypointUiText.draw(g, font, value, x + 16, y + 36, TEXT, false);
        WaypointUiText.draw(g, font, note, x + 16, y + h - 20, QUIET, false);
    }

    private void coordBox(GuiGraphicsExtractor g, int x, int y, int w, String label, String val) {
        rounded(g, x, y, x + w, y + 32, PANEL_ALT, 6);
        WaypointUiText.draw(g, font, label, x + 8, y + 4, QUIET, false);
        WaypointUiText.draw(g, font, val, x + 8, y + 18, TEXT, false);
    }

    private void navItem(GuiGraphicsExtractor g, int mx, int my, int x, int y, int w, String label, boolean active) {
        if (active || inside(mx, my, x, y, w, 32)) rounded(g, x, y, x + w, y + 32, active ? 0xFF202629 : PANEL, 7);
        WaypointUiText.draw(g, font, label, x + 30, y + 10, active ? TEXT : MUTED, false);
    }

    private void actionButton(GuiGraphicsExtractor g, int mx, int my, int x, int y, int w, int h, String label, boolean primary) {
        boolean hover = inside(mx, my, x, y, w, h);
        rounded(g, x, y, x + w, y + h, primary ? (hover ? 0xFFBDE39A : ACCENT) : (hover ? 0xFF242B2F : PANEL), 7);
        if (!primary) border(g, x, y, x + w, y + h, EDGE);
        WaypointUiText.draw(g, font, label, x + (w - WaypointUiText.width(font, label)) / 2, y + (h - 8) / 2, primary ? 0xFF101410 : TEXT, false);
    }

    private String worldTitle() {
        Minecraft mc = Minecraft.getInstance();
        try {
            if (mc.getCurrentServer() != null) return mc.getCurrentServer().name;
            if (mc.getSingleplayerServer() != null) return mc.getSingleplayerServer().getWorldData().getLevelName();
        } catch (Throwable ignored) { }
        return "Survival Realm";
    }

    private static void border(GuiGraphicsExtractor g, int x, int y, int x2, int y2, int color) {
        g.fill(x, y, x2, y + 1, color); g.fill(x, y2 - 1, x2, y2, color);
        g.fill(x, y, x + 1, y2, color); g.fill(x2 - 1, y, x2, y2, color);
    }
    private static void rounded(GuiGraphicsExtractor g, int x, int y, int x2, int y2, int color, int r) {
        g.fill(x + r, y, x2 - r, y2, color); g.fill(x, y + r, x2, y2 - r, color);
    }
    private static boolean inside(int mx, int my, int x, int y, int w, int h) {
        return mx >= x && mx < x + w && my >= y && my < y + h;
    }
}
