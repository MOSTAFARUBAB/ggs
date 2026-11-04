package com.example.waypoints;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Full workspace dashboard replacing the former compact modal waypoint menu. */
public final class WaypointScreen extends Screen {
    private static final String[] FILTER_LABELS = {"All dimensions", "Overworld", "The Nether", "The End"};
    private static final int BG = 0xFF090A0C;
    private static final int RAIL = 0xFF101214;
    private static final int PANEL = 0xFF131619;
    private static final int PANEL_ALT = 0xFF0F1214;
    private static final int EDGE = 0xFF30373B;
    private static final int TEXT = 0xFFF2F5F5;
    private static final int MUTED = 0xFF9BA4A8;
    private static final int QUIET = 0xFF687278;
    private static final int ACCENT = 0xFFB7F36B;
    private static final int RED = 0xFFFF777F;

    private final int requestedPage;
    private int page;
    private String searchQuery;
    private int railWidth, contentX, contentRight, contentWidth;
    private int toolbarY, filtersY, gridY, footerY, columns, rows, cardWidth, cardHeight, pageSize;
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

    static void openMenu() { open(0); }
    static void open(int page) { Minecraft.getInstance().gui.setScreen(new WaypointScreen(page)); }

    @Override
    public void extractBackground(GuiGraphicsExtractor g, int mouseX, int mouseY, float delta) {
        g.fill(0, 0, width, height, BG);
    }

    @Override
    protected void init() {
        clearWidgets();
        railWidth = width >= 800 ? 150 : 54;
        contentX = railWidth + 24;
        contentRight = width - 24;
        contentWidth = Math.max(220, contentRight - contentX);
        int topBar = 38;
        int headingY = 58;
        int statsY = 99;
        toolbarY = Math.max(148, Math.min(height - 230, 188));
        filtersY = toolbarY + 34;
        gridY = filtersY + 28;
        footerY = height - 27;
        columns = listLayout ? 1 : contentWidth >= 620 ? 3 : contentWidth >= 390 ? 2 : 1;
        rows = Math.max(1, Math.min(2, (footerY - gridY - 12) / 98));
        cardWidth = (contentWidth - (columns - 1) * 12) / columns;
        cardHeight = Math.max(82, (footerY - gridY - 12 * (rows - 1) - 12) / rows);
        pageSize = columns * rows;

        entries = new ArrayList<>(WaypointsMod.menuWaypoints());
        if (sortByName) entries.sort((a, b) -> a.name().compareToIgnoreCase(b.name()));
        refreshFiltered();
        page = Math.max(0, Math.min(requestedPage, Math.max(0, (filtered.size() - 1) / pageSize)));

        // Sidebar navigation.
        if (railWidth > 70) {
            button(10, 140, railWidth - 20, 32, () -> Minecraft.getInstance().gui.setScreen(new WaypointScreen(0)));
            button(10, 179, railWidth - 20, 32, () -> Minecraft.getInstance().gui.setScreen(new WaypointSettingsScreen()));
        } else {
            button(8, 104, 38, 32, () -> Minecraft.getInstance().gui.setScreen(new WaypointScreen(0)));
            button(8, 150, 38, 32, () -> Minecraft.getInstance().gui.setScreen(new WaypointSettingsScreen()));
        }
        // Create action and data transfer controls.
        button(contentRight - 126, headingY - 1, 126, 29,
                () -> Minecraft.getInstance().gui.setScreen(new WaypointEditScreen(null, currentPage())));
        button(contentRight - 190, 164, 86, 24, () -> showTransferMessage(WaypointsMod.importWaypointsFromFile(), true));
        button(contentRight - 98, 164, 86, 24, () -> showTransferMessage(WaypointsMod.exportWaypointsToFile(), false));
        searchBox = new EditBox(font, contentX + 1, toolbarY + 1,
                Math.max(70, contentWidth - 190), 22, Component.literal("Search waypoints"));
        searchBox.setMaxLength(48);
        searchBox.setValue(searchQuery);
        searchBox.setBordered(false);
        searchBox.setResponder(value -> {
            searchQuery = value;
            page = 0;
            refreshFiltered();
            // Card hit targets resolve the current filtered slot at click time, so
            // they do not need to be recreated for every character typed.
        });
        addRenderableWidget(searchBox);
        button(contentRight - 178, toolbarY, 116, 25, () -> {
            sortByName = !sortByName;
            rebuildDashboard();
        });
        button(contentRight - 54, toolbarY, 54, 25, () -> {
            listLayout = !listLayout;
            rebuildDashboard();
        });

        // Dimension filters: current, Overworld, Nether, End, All.
        int filterX = contentX;
        int[] modes = {4, 1, 2, 3};
        int[] widths = {106, 86, 86, 72};
        for (int i = 0; i < modes.length; i++) {
            final int mode = modes[i];
            button(filterX, filtersY, widths[i], 21, () -> {
                WaypointsMod.menuDimensionMode = mode;
                page = 0;
                rebuildDashboard();
            });
            filterX += widths[i] + 7;
        }
        button(contentRight - 70, filtersY, 70, 21, () -> {
            WaypointsMod.menuDimensionMode = 0;
            page = 0;
            rebuildDashboard();
        });
        rebuildCardTargets();
        // Pagination buttons.
        button(contentRight - 106, footerY, 30, 22, () -> changePage(-1));
        button(contentRight - 34, footerY, 30, 22, () -> changePage(1));
    }

    private void rebuildDashboard() {
        Minecraft.getInstance().gui.setScreen(new WaypointScreen(currentPage(), searchQuery));
    }

    private void rebuildCardTargets() {
        // One target set per visible slot. The slot is resolved at click time, so
        // search edits never leave stale buttons pointing to older waypoints.
        for (int slot = 0; slot < pageSize; slot++) {
            final int visibleSlot = slot;
            int col = slot % columns;
            int row = slot / columns;
            int x = contentX + col * (cardWidth + 12);
            int y = gridY + row * (cardHeight + 12);
            button(x, y, Math.max(1, cardWidth - 54), cardHeight, () -> editSlot(visibleSlot));
            button(x + cardWidth - 50, y + 8, 20, 20, () -> editSlot(visibleSlot));
            button(x + cardWidth - 27, y + 8, 20, 20, () -> deleteSlot(visibleSlot));
        }
    }

    private Waypoint visibleSlot(int slot) {
        int index = currentPage() * pageSize + slot;
        return index >= 0 && index < filtered.size() ? filtered.get(index) : null;
    }

    private void editSlot(int slot) {
        Waypoint w = visibleSlot(slot);
        if (w != null) Minecraft.getInstance().gui.setScreen(new WaypointEditScreen(w, currentPage()));
    }

    private void deleteSlot(int slot) {
        Waypoint w = visibleSlot(slot);
        if (w == null) return;
        WaypointsMod.remove(w);
        Minecraft.getInstance().gui.setScreen(new WaypointScreen(currentPage(), searchQuery));
    }

    private void refreshFiltered() {
        String q = searchQuery == null ? "" : searchQuery.trim().toLowerCase(Locale.ROOT);
        if (q.isEmpty()) filtered = List.copyOf(entries);
        else {
            List<Waypoint> result = new ArrayList<>();
            for (Waypoint w : entries) {
                String value = (w.name() + " " + w.dimension() + " " + w.x() + " " + w.y() + " " + w.z()).toLowerCase(Locale.ROOT);
                if (value.contains(q)) result.add(w);
            }
            filtered = List.copyOf(result);
        }
    }

    private int currentPage() { return Math.max(0, Math.min(page, Math.max(0, (filtered.size() - 1) / Math.max(1, pageSize)))); }
    private int pageCount() { return Math.max(1, (filtered.size() + Math.max(1, pageSize) - 1) / Math.max(1, pageSize)); }
    private void changePage(int delta) {
        int next = Math.max(0, Math.min(pageCount() - 1, currentPage() + delta));
        if (next != page) Minecraft.getInstance().gui.setScreen(new WaypointScreen(next, searchQuery));
    }

    private void showTransferMessage(String message, boolean refresh) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player != null) mc.gui.chatListener().handleSystemMessage(Component.literal(message), false);
        if (refresh) Minecraft.getInstance().gui.setScreen(new WaypointScreen(currentPage(), searchQuery));
    }

    private void button(int x, int y, int w, int h, Runnable action) {
        addRenderableWidget(Button.builder(Component.literal(""), b -> action.run()).bounds(x, y, w, h).build());
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor g, int mx, int my, float delta) {
        super.extractRenderState(g, mx, my, delta);
        drawSidebar(g, mx, my);
        g.fill(railWidth, 0, width, 39, 0xFF0C0E10);
        g.fill(railWidth, 38, width, 39, EDGE);
        g.text(font, "◇  " + worldTitle(), railWidth + 26, 15, MUTED, false);
        g.text(font, "/", railWidth + 140, 15, QUIET, false);
        g.text(font, "Waypoints", railWidth + 154, 15, TEXT, false);
        g.fill(width - 118, 17, width - 113, 22, ACCENT);
        g.text(font, "Local workspace", width - 106, 15, MUTED, false);

        g.text(font, "Every place, a waypoint.", contentX, 57, TEXT, true);
        g.text(font, "Keep your world organized. Never lose a place worth returning to.", contentX, 75, MUTED, false);
        primaryButton(g, mx, my, contentRight - 126, 56, 126, 29, "+  Create waypoint");

        int statGap = 12;
        int statW = (contentWidth - 2 * statGap) / 3;
        statCard(g, contentX, 99, statW, 62, "Total waypoints", String.valueOf(entries.size()), "Across this world", "⌖");
        statCard(g, contentX + statW + statGap, 99, statW, 62, "Dimensions explored", String.valueOf(countDimensions()), dimensionsText(), "◇");
        Waypoint last = entries.isEmpty() ? null : entries.get(entries.size() - 1);
        statCard(g, contentX + (statW + statGap) * 2, 99, statW, 62, "Last added",
                last == null ? "—" : fit(last.name(), Math.max(40, statW - 18)), last == null ? "No waypoints yet" : "Saved in this world", "◷");

        g.text(font, "Your waypoints", contentX, 170, TEXT, true);
        g.text(font, String.valueOf(filtered.size()), contentX + font.width("Your waypoints") + 10, 171, MUTED, false);
        actionButton(g, mx, my, contentRight - 190, 164, 86, 24, "Import", false);
        actionButton(g, mx, my, contentRight - 98, 164, 86, 24, "Export", false);
        drawSearch(g, mx, my);
        drawFilters(g, mx, my);
        drawCards(g, mx, my);
        drawFooter(g, mx, my);
    }

    private void drawSidebar(GuiGraphicsExtractor g, int mx, int my) {
        g.fill(0, 0, railWidth, height, RAIL);
        g.fill(railWidth - 1, 0, railWidth, height, EDGE);
        rounded(g, 14, 15, 43, 44, ACCENT, 8);
        g.text(font, "➤", 24, 26, 0xFF101410, true);
        if (railWidth > 70) {
            g.text(font, "Waypoint", 51, 19, TEXT, false);
            g.text(font, "M A N A G E R", 51, 31, MUTED, false);
            rounded(g, 14, 62, railWidth - 14, 105, PANEL, 8);
            g.text(font, worldTitle(), 24, 73, TEXT, true);
            g.fill(24, 91, 28, 95, ACCENT);
            g.text(font, "Java Edition · 26.3", 34, 89, MUTED, false);
            g.text(font, "WORKSPACE", 16, 125, QUIET, false);
            navItem(g, mx, my, 10, 140, railWidth - 20, "◎", "Waypoints", true);
            navItem(g, mx, my, 10, 179, railWidth - 20, "☷", "Settings", false);
            rounded(g, 14, height - 109, railWidth - 14, height - 18, PANEL_ALT, 8);
            g.text(font, "Your world. Your waypoints.", 23, height - 94, TEXT, false);
            g.text(font, "Stored locally, ready for", 23, height - 76, MUTED, false);
            g.text(font, "your next adventure.", 23, height - 63, MUTED, false);
            g.fill(23, height - 43, 28, height - 38, ACCENT);
            g.text(font, "All changes saved", 34, height - 44, MUTED, false);
        } else {
            navItem(g, mx, my, 8, 104, 38, "◎", "", true);
            navItem(g, mx, my, 8, 150, 38, "☷", "", false);
        }
    }

    private void navItem(GuiGraphicsExtractor g, int mx, int my, int x, int y, int w, String icon, String label, boolean active) {
        boolean hover = inside(mx, my, x, y, w, 32);
        if (active || hover) rounded(g, x, y, x + w, y + 32, active ? 0xFF202629 : PANEL, 7);
        g.text(font, icon, x + 10, y + 11, active ? ACCENT : MUTED, false);
        if (!label.isEmpty()) g.text(font, label, x + 30, y + 11, active ? TEXT : MUTED, false);
    }

    private void statCard(GuiGraphicsExtractor g, int x, int y, int w, int h, String label, String value, String note, String icon) {
        rounded(g, x, y, x + w, y + h, PANEL, 9);
        border(g, x, y, x + w, y + h, EDGE);
        g.text(font, label, x + 12, y + 10, MUTED, false);
        g.text(font, fit(value, w - 24), x + 12, y + 27, TEXT, true);
        g.text(font, fit(note, w - 24), x + 12, y + h - 14, QUIET, false);
        g.text(font, icon, x + w - 24, y + 10, MUTED, false);
    }

    private void drawSearch(GuiGraphicsExtractor g, int mx, int my) {
        int y = toolbarY;
        int w = contentWidth - 190;
        rounded(g, contentX, y, contentX + w, y + 27, PANEL_ALT, 7);
        border(g, contentX, y, contentX + w, y + 27, EDGE);
        g.text(font, "⌕", contentX + 10, y + 9, MUTED, false);
        if (searchBox != null && searchBox.getValue().isEmpty() && !searchBox.isFocused()) {
            g.text(font, "Search by name or coordinates…", contentX + 27, y + 10, QUIET, false);
        } else if (searchBox != null) {
            String value = searchBox.getValue();
            g.text(font, value, contentX + 27, y + 10, TEXT, false);
            if (searchBox.isFocused() && (System.currentTimeMillis() / 500) % 2 == 0) {
                int caret = contentX + 27 + font.width(value);
                g.fill(caret, y + 6, caret + 1, y + 20, TEXT);
            }
        }
        if (sortByName) actionButton(g, mx, my, contentRight - 178, y, 116, 27, "A–Z", false);
        else actionButton(g, mx, my, contentRight - 178, y, 116, 27, "Recently added", false);
        actionButton(g, mx, my, contentRight - 54, y, 54, 27, listLayout ? "Grid" : "List", false);
    }

    private void drawFilters(GuiGraphicsExtractor g, int mx, int my) {
        int x = contentX;
        int y = filtersY;
        int[] modes = {4, 1, 2, 3};
        int[] widths = {106, 86, 86, 72};
        String[] labels = {"All dimensions", "Overworld", "The Nether", "The End"};
        for (int i = 0; i < modes.length; i++) {
            boolean selected = WaypointsMod.menuDimensionMode == modes[i];
            pill(g, x, y, widths[i], 21, labels[i], selected, mx, my);
            x += widths[i] + 7;
        }
        pill(g, contentRight - 70, y, 70, 21, "Current", WaypointsMod.menuDimensionMode == 0, mx, my);
    }

    private void drawCards(GuiGraphicsExtractor g, int mx, int my) {
        int start = currentPage() * pageSize;
        int end = Math.min(filtered.size(), start + pageSize);
        if (filtered.isEmpty()) {
            rounded(g, contentX, gridY, contentRight, gridY + 88, PANEL, 10);
            g.text(font, searchQuery.isBlank() ? "No waypoints yet" : "No matching waypoints", contentX + 18, gridY + 23, TEXT, true);
            g.text(font, "Create a waypoint to save a place in this world.", contentX + 18, gridY + 43, MUTED, false);
            return;
        }
        for (int i = start; i < end; i++) {
            Waypoint w = filtered.get(i);
            int slot = i - start;
            int col = slot % columns;
            int row = slot / columns;
            int x = contentX + col * (cardWidth + 12);
            int y = gridY + row * (cardHeight + 12);
            boolean hover = inside(mx, my, x, y, cardWidth, cardHeight);
            rounded(g, x, y, x + cardWidth, y + cardHeight, hover ? 0xFF1A2023 : PANEL, 9);
            border(g, x, y, x + cardWidth, y + cardHeight, EDGE);
            int color = 0xFF000000 | WaypointsMod.resolveColor(w);
            rounded(g, x + 12, y + 13, x + 36, y + 37, 0xFF252B2F, 7);
            rounded(g, x + 20, y + 18, x + 28, y + 32, color, 4);
            g.text(font, fit(w.name(), Math.max(45, cardWidth - 100)), x + 44, y + 12, TEXT, true);
            String sub = dimension(w.dimension()) + " · " + (w.expiresAt() > 0 ? "Temporary" : "Permanent");
            g.text(font, fit(sub, cardWidth - 60), x + 44, y + 26, MUTED, false);
            int coordsY = y + 42;
            int coordW = (cardWidth - 42) / 3;
            drawCoordinate(g, x + 12, coordsY, coordW, "X", Integer.toString(w.x()));
            drawCoordinate(g, x + 18 + coordW, coordsY, coordW, "Y", Integer.toString(w.y()));
            drawCoordinate(g, x + 24 + coordW * 2, coordsY, coordW, "Z", Integer.toString(w.z()));
            int metaY = y + cardHeight - 15;
            g.fill(x + 12, metaY + 4, x + 16, metaY + 8, color);
            g.text(font, dimension(w.dimension()), x + 22, metaY + 1, MUTED, false);
            g.text(font, "✎", x + cardWidth - 44, metaY, MUTED, false);
            g.text(font, "×", x + cardWidth - 21, metaY, MUTED, false);
        }
    }

    private void drawCoordinate(GuiGraphicsExtractor g, int x, int y, int w, String label, String value) {
        rounded(g, x, y, x + w, y + 28, PANEL_ALT, 5);
        g.text(font, label, x + 7, y + 4, QUIET, false);
        g.text(font, fit(value, w - 14), x + 7, y + 15, TEXT, false);
    }

    private void drawFooter(GuiGraphicsExtractor g, int mx, int my) {
        int start = currentPage() * pageSize;
        int shown = filtered.isEmpty() ? 0 : Math.min(pageSize, filtered.size() - start);
        g.text(font, "Showing " + (filtered.isEmpty() ? 0 : start + 1) + "–" + (start + shown) + " of " + filtered.size() + " waypoints",
                contentX, footerY + 7, MUTED, false);
        actionButton(g, mx, my, contentRight - 106, footerY, 30, 22, "‹", false);
        g.text(font, (currentPage() + 1) + " / " + pageCount(), contentRight - 68, footerY + 7, TEXT, false);
        actionButton(g, mx, my, contentRight - 34, footerY, 30, 22, "›", false);
    }

    private int countDimensions() {
        boolean over = false, nether = false, end = false;
        for (Waypoint w : entries) {
            String d = WaypointsMod.normalizeDimension(w.dimension());
            over |= d.equals("minecraft:overworld");
            nether |= d.equals("minecraft:the_nether");
            end |= d.equals("minecraft:the_end");
        }
        return (over ? 1 : 0) + (nether ? 1 : 0) + (end ? 1 : 0);
    }
    private String dimensionsText() {
        List<String> names = new ArrayList<>();
        for (Waypoint w : entries) {
            String d = dimension(w.dimension());
            if (!names.contains(d)) names.add(d);
        }
        return names.isEmpty() ? "No dimensions yet" : String.join(", ", names);
    }
    private String worldTitle() {
        Minecraft mc = Minecraft.getInstance();
        try {
            if (mc.getCurrentServer() != null) return mc.getCurrentServer().name;
            if (mc.getSingleplayerServer() != null) return mc.getSingleplayerServer().getWorldData().getLevelName();
        } catch (Throwable ignored) { }
        return "Survival Realm";
    }
    private String dimension(String raw) {
        String d = WaypointsMod.normalizeDimension(raw);
        if (d.endsWith(":the_nether")) return "The Nether";
        if (d.endsWith(":the_end")) return "The End";
        if (d.endsWith(":overworld")) return "Overworld";
        return "Custom dimension";
    }
    private String fit(String value, int maxWidth) {
        if (value == null) return "";
        String s = value;
        while (!s.isEmpty() && font.width(s) > maxWidth) s = s.substring(0, s.length() - 1);
        return s.equals(value) ? s : s + "…";
    }
    private void pill(GuiGraphicsExtractor g, int x, int y, int w, int h, String label, boolean selected, int mx, int my) {
        boolean hover = inside(mx, my, x, y, w, h);
        rounded(g, x, y, x + w, y + h, selected ? 0xFF26321F : hover ? PANEL : PANEL_ALT, 6);
        if (selected) border(g, x, y, x + w, y + h, 0xFF789D4C);
        g.text(font, label, x + (w - font.width(label)) / 2, y + 7, selected ? ACCENT : MUTED, selected);
    }
    private void actionButton(GuiGraphicsExtractor g, int mx, int my, int x, int y, int w, int h, String label, boolean primary) {
        boolean hover = inside(mx, my, x, y, w, h);
        rounded(g, x, y, x + w, y + h, primary ? (hover ? 0xFFC9FF83 : ACCENT) : (hover ? 0xFF242B2F : PANEL), 6);
        if (!primary) border(g, x, y, x + w, y + h, EDGE);
        int color = primary ? 0xFF12170C : TEXT;
        g.text(font, label, x + (w - font.width(label)) / 2, y + (h - 8) / 2, color, false);
    }
    private void primaryButton(GuiGraphicsExtractor g, int mx, int my, int x, int y, int w, int h, String label) {
        actionButton(g, mx, my, x, y, w, h, label, true);
    }
    private static void border(GuiGraphicsExtractor g, int x, int y, int x2, int y2, int color) {
        g.fill(x, y, x2, y + 1, color); g.fill(x, y2 - 1, x2, y2, color);
        g.fill(x, y, x + 1, y2, color); g.fill(x2 - 1, y, x2, y2, color);
    }
    private static void rounded(GuiGraphicsExtractor g, int x, int y, int x2, int y2, int color, int radius) {
        int w = x2 - x, h = y2 - y; if (w <= 0 || h <= 0) return;
        int r = Math.min(radius, Math.min(w / 2, h / 2));
        g.fill(x + r, y, x2 - r, y2, color); g.fill(x, y + r, x2, y2 - r, color);
        for (int row = 0; row < r; row++) {
            int inset = (int) Math.ceil(r - Math.sqrt(Math.max(0, r * r - Math.pow(r - row - .5, 2))));
            g.fill(x + inset, y + row, x2 - inset, y + row + 1, color);
            g.fill(x + inset, y2 - row - 1, x2 - inset, y2 - row, color);
        }
    }
    private static boolean inside(int mx, int my, int x, int y, int w, int h) {
        return mx >= x && mx < x + w && my >= y && my < y + h;
    }
}
