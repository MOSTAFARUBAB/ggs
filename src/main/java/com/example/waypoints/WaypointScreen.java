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
    private int headingY, statsY, statsHeight, sectionY, transferY;
    private int toolbarY, filtersY, gridY, footerY, columns, rows, cardWidth, cardHeight, pageSize;
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

    static void openMenu() { open(0); }
    static void open(int page) { Minecraft.getInstance().gui.setScreen(new WaypointScreen(page)); }

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
        railWidth = width >= 800 ? Math.max(150, Math.min(190, Math.round(width * 0.16f))) : 54;
        int sideInset = width >= 800 ? 32 : 12;
        contentX = railWidth + sideInset;
        contentRight = width - sideInset;
        contentWidth = Math.max(220, contentRight - contentX);
        headingY = Math.max(58, Math.round(height * 0.098f));
        statsY = Math.max(110, Math.round(height * 0.184f));
        statsHeight = Math.max(80, Math.round(height * 0.118f));
        sectionY = Math.max(statsY + statsHeight + 20, Math.round(height * 0.337f));
        transferY = sectionY - 7;
        toolbarY = Math.max(sectionY + 36, Math.round(height * 0.385f));
        filtersY = toolbarY + 50;
        gridY = filtersY + 43;
        footerY = height - 92;
        columns = listLayout ? 1 : contentWidth >= 620 ? 3 : contentWidth >= 390 ? 2 : 1;
        rows = Math.max(1, Math.min(2, (footerY - gridY - 12) / 120));
        cardWidth = (contentWidth - (columns - 1) * 12) / columns;
        cardHeight = Math.max(96, (footerY - gridY - 12 * (rows - 1) - 12) / rows);
        pageSize = columns * rows;

        entries = new ArrayList<>(WaypointsMod.menuWaypoints());
        if (sortByName) entries.sort((a, b) -> a.name().compareToIgnoreCase(b.name()));
        refreshFiltered();
        page = Math.max(0, Math.min(requestedPage, Math.max(0, (filtered.size() - 1) / pageSize)));

        // Sidebar navigation.
        int navY = railWidth > 70 ? 153 : 104;
        if (railWidth > 70) {
            button(10, navY, railWidth - 20, 32, () -> Minecraft.getInstance().gui.setScreen(new WaypointScreen(0)));
            button(10, navY + 41, railWidth - 20, 32, () -> Minecraft.getInstance().gui.setScreen(new WaypointSettingsScreen()));
        } else {
            button(8, navY, 38, 32, () -> Minecraft.getInstance().gui.setScreen(new WaypointScreen(0)));
            button(8, navY + 46, 38, 32, () -> Minecraft.getInstance().gui.setScreen(new WaypointSettingsScreen()));
        }
        // Create action and data transfer controls.
        button(contentRight - 132, headingY + 2, 132, 34,
                () -> Minecraft.getInstance().gui.setScreen(new WaypointEditScreen(null, currentPage())));
        button(contentRight - 190, transferY, 86, 27, () -> showTransferMessage(WaypointsMod.importWaypointsFromFile(), true));
        button(contentRight - 98, transferY, 86, 27, () -> showTransferMessage(WaypointsMod.exportWaypointsToFile(), false));
        int searchWidth = Math.max(70, contentWidth - 220);
        searchBox = new EditBox(font, contentX + 30, toolbarY + 7,
                Math.max(40, searchWidth - 42), 21, Component.literal("Search waypoints"));
        searchBox.setMaxLength(48);
        searchBox.setValue(searchQuery);
        searchBox.setBordered(false);
        searchBox.setTextColor(0xFFF2F5F5);
        searchBox.setResponder(value -> {
            searchQuery = value;
            page = 0;
            refreshFiltered();
            rebuildCardTargets();
            rebuildPaginationTargets();
        });
        addRenderableWidget(searchBox);
        button(contentRight - 190, toolbarY, 120, 35, () -> {
            sortByName = !sortByName;
            rebuildDashboard();
        });
        button(contentRight - 62, toolbarY, 62, 35, () -> {
            listLayout = !listLayout;
            rebuildDashboard();
        });

        // Dimension filters: current, Overworld, Nether, End, All.
        int filterX = contentX;
        int[] modes = {4, 1, 2, 3};
        int[] widths = {106, 86, 86, 72};
        for (int i = 0; i < modes.length; i++) {
            final int mode = modes[i];
            button(filterX, filtersY, widths[i], 27, () -> {
                WaypointsMod.menuDimensionMode = mode;
                page = 0;
                rebuildDashboard();
            });
            filterX += widths[i] + 7;
        }
        button(contentRight - 78, filtersY, 78, 24, () -> {
            WaypointsMod.menuDimensionMode = 0;
            page = 0;
            rebuildDashboard();
        });
        rebuildCardTargets();
        rebuildPaginationTargets();
    }

    private void rebuildPaginationTargets() {
        paginationTargets.clear();
        if (pageCount() <= 1) return;
        int visiblePages = Math.min(width >= 800 ? 5 : 3, pageCount());
        int startPage = Math.max(0, Math.min(currentPage() - visiblePages / 2, pageCount() - visiblePages));
        int pageButton = 24;
        int gap = 4;
        int totalWidth = pageButton + 8 + visiblePages * pageButton + Math.max(0, visiblePages - 1) * gap + 8 + pageButton;
        int x = contentRight - totalWidth;
        int y = footerY;
        paginationTargets.add(new HitTarget(x, y, pageButton, 24, () -> changePage(-1)));
        int pagesX = x + pageButton + 8;
        for (int i = 0; i < visiblePages; i++) {
            final int pageIndex = startPage + i;
            paginationTargets.add(new HitTarget(pagesX + i * (pageButton + gap), y, pageButton, 24,
                    () -> {
                        if (pageIndex != currentPage()) {
                            Minecraft.getInstance().gui.setScreen(new WaypointScreen(pageIndex, searchQuery));
                        }
                    }));
        }
        int nextX = pagesX + visiblePages * (pageButton + gap) - gap + 8;
        paginationTargets.add(new HitTarget(nextX, y, pageButton, 24, () -> changePage(1)));
    }

    private void rebuildDashboard() {
        Minecraft.getInstance().gui.setScreen(new WaypointScreen(currentPage(), searchQuery));
    }

    private void rebuildCardTargets() {
        // One target set per visible slot. The slot is resolved at click time, so
        // search edits never leave stale buttons pointing to older waypoints.
        int visibleCount = Math.max(0, Math.min(pageSize, filtered.size() - currentPage() * pageSize));
        cardHitTargets.clear();
        for (int slot = 0; slot < visibleCount; slot++) {
            final int visibleSlot = slot;
            int col = slot % columns;
            int row = slot / columns;
            int x = contentX + col * (cardWidth + 12);
            int y = gridY + row * (cardHeight + 12);
            cardHitTargets.add(new HitTarget(x, y, Math.max(1, cardWidth - 54), cardHeight, () -> editSlot(visibleSlot)));
            cardHitTargets.add(new HitTarget(x + cardWidth - 50, y + 8, 20, 20, () -> editSlot(visibleSlot)));
            cardHitTargets.add(new HitTarget(x + cardWidth - 27, y + 8, 20, 20, () -> deleteSlot(visibleSlot)));
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
        String current = WaypointsMod.normalizeDimension(WaypointsMod.currentDimension());
        List<Waypoint> result = new ArrayList<>();
        for (Waypoint w : entries) {
            String dimension = WaypointsMod.normalizeDimension(w.dimension());
            boolean dimensionMatches = switch (WaypointsMod.menuDimensionMode) {
                case 0 -> dimension.equals(current);
                case 1 -> dimension.equals("minecraft:overworld");
                case 2 -> dimension.equals("minecraft:the_nether");
                case 3 -> dimension.equals("minecraft:the_end");
                default -> true; // 4 = all dimensions
            };
            if (!dimensionMatches) continue;
            String value = (w.name() + " " + w.dimension() + " " + w.x() + " " + w.y() + " " + w.z()).toLowerCase(Locale.ROOT);
            if (q.isEmpty() || value.contains(q)) result.add(w);
        }
        filtered = List.copyOf(result);
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
        // Pure hit target: the custom interface is drawn by extractRenderState.
        // A vanilla Button here would paint a grey rectangle over the custom card.
        hitTargets.add(new HitTarget(x, y, w, h, action));
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        if (event.button() == 1) {
            for (int i = hitTargets.size() - 1; i >= 0; i--) {
                HitTarget target = hitTargets.get(i);
                if (target.contains(event.x(), event.y())) {
                    target.action().run();
                    return true;
                }
            }
            for (int i = paginationTargets.size() - 1; i >= 0; i--) {
                HitTarget target = paginationTargets.get(i);
                if (target.contains(event.x(), event.y())) {
                    target.action().run();
                    return true;
                }
            }
            for (int i = cardHitTargets.size() - 1; i >= 0; i--) {
                HitTarget target = cardHitTargets.get(i);
                if (target.contains(event.x(), event.y())) {
                    target.action().run();
                    return true;
                }
            }
        }
        return super.mouseClicked(event, doubleClick);
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor g, int mx, int my, float delta) {
        drawSidebar(g, mx, my);
        g.fill(railWidth, 0, width, 44, 0xFF0C0E10);
        g.fill(railWidth, 43, width, 44, EDGE);
        String worldLabel = fit(worldTitle(), 112);
        g.text(font, "◇  " + worldLabel, railWidth + 26, 16, MUTED, false);
        int crumbX = railWidth + 30 + font.width(worldLabel) + 12;
        g.text(font, "/", crumbX, 16, QUIET, false);
        g.text(font, "Waypoints", crumbX + 14, 16, TEXT, false);
        g.fill(width - 132, 18, width - 127, 23, ACCENT);
        g.text(font, "Local workspace", width - 120, 16, MUTED, false);

        g.text(font, "Every place, a waypoint.", contentX, headingY, TEXT, true);
        g.text(font, "Keep your world organized. Never lose a place worth returning to.", contentX, headingY + 35, MUTED, false);
        primaryButton(g, mx, my, contentRight - 132, headingY + 2, 132, 34, "+  Create waypoint");

        int statGap = 12;
        int statW = (contentWidth - 2 * statGap) / 3;
        statCard(g, contentX, statsY, statW, statsHeight, "Total waypoints", String.valueOf(entries.size()), "Across this world", "⌖");
        statCard(g, contentX + statW + statGap, statsY, statW, statsHeight, "Dimensions explored", String.valueOf(countDimensions()), dimensionsText(), "◇");
        Waypoint last = entries.isEmpty() ? null : entries.get(entries.size() - 1);
        statCard(g, contentX + (statW + statGap) * 2, statsY, statW, statsHeight, "Last added",
                last == null ? "—" : fit(last.name(), Math.max(40, statW - 18)), last == null ? "No waypoints yet" : "Saved in this world", "◷");

        g.text(font, "Your waypoints", contentX, sectionY, TEXT, true);
        rounded(g, contentX + font.width("Your waypoints") + 9, sectionY - 1,
                contentX + font.width("Your waypoints") + 34, sectionY + 15, PANEL_ALT, 5);
        g.text(font, String.valueOf(filtered.size()), contentX + font.width("Your waypoints") + 16, sectionY + 3, MUTED, false);
        actionButton(g, mx, my, contentRight - 190, transferY, 86, 27, "Import", false);
        actionButton(g, mx, my, contentRight - 98, transferY, 86, 27, "Export", false);
        drawSearch(g, mx, my);
        drawFilters(g, mx, my);
        drawCards(g, mx, my);
        drawFooter(g, mx, my);
        // Render native text input last, over the custom matte input shell.
        super.extractRenderState(g, mx, my, delta);
    }

    private void drawSidebar(GuiGraphicsExtractor g, int mx, int my) {
        g.fill(0, 0, railWidth, height, RAIL);
        g.fill(railWidth - 1, 0, railWidth, height, EDGE);
        rounded(g, 14, 15, 43, 44, ACCENT, 8);
        g.text(font, "W", 23, 25, 0xFF101410, true);
        if (railWidth > 70) {
            g.text(font, "Waypoint", 51, 19, TEXT, false);
            g.text(font, "M A N A G E R", 51, 31, MUTED, false);
            rounded(g, 14, 63, railWidth - 14, 108, PANEL, 8);
            g.text(font, fit(worldTitle(), railWidth - 48), 24, 74, TEXT, true);
            g.fill(24, 94, 28, 98, ACCENT);
            g.text(font, "Java Edition · 26.3", 34, 92, MUTED, false);
            g.text(font, "WORKSPACE", 16, 135, QUIET, false);
            navItem(g, mx, my, 10, 153, railWidth - 20, "◎", "Waypoints", true);
            navItem(g, mx, my, 10, 194, railWidth - 20, "☷", "Settings", false);
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
        g.text(font, label, x + 12, y + 9, MUTED, false);
        g.text(font, fit(value, w - 24), x + 12, y + 25, TEXT, true);
        g.text(font, fit(note, w - 24), x + 12, y + h - 14, QUIET, false);
        g.text(font, icon, x + w - 24, y + 10, MUTED, false);
    }

    private void drawSearch(GuiGraphicsExtractor g, int mx, int my) {
        int y = toolbarY;
        int w = Math.max(60, contentWidth - 220);
        rounded(g, contentX, y, contentX + w, y + 35, PANEL_ALT, 7);
        border(g, contentX, y, contentX + w, y + 35, EDGE);
        g.text(font, "⌕", contentX + 11, y + 13, MUTED, false);
        if (searchBox != null && searchBox.getValue().isEmpty() && !searchBox.isFocused()) {
            g.text(font, "Search by name or coordinates…", contentX + 31, y + 14, QUIET, false);
        }
        if (sortByName) actionButton(g, mx, my, contentRight - 190, y, 120, 35, "A–Z", false);
        else actionButton(g, mx, my, contentRight - 190, y, 120, 35, "Recently added", false);
        actionButton(g, mx, my, contentRight - 62, y, 62, 35, listLayout ? "Grid" : "List", false);
    }

    private void drawFilters(GuiGraphicsExtractor g, int mx, int my) {
        int x = contentX;
        int y = filtersY;
        int[] modes = {4, 1, 2, 3};
        int[] widths = {106, 86, 86, 72};
        String[] labels = {"All dimensions", "Overworld", "The Nether", "The End"};
        for (int i = 0; i < modes.length; i++) {
            boolean selected = WaypointsMod.menuDimensionMode == modes[i];
            pill(g, x, y, widths[i], 27, labels[i], selected, mx, my);
            x += widths[i] + 7;
        }
        pill(g, contentRight - 78, y, 78, 24, "Current", WaypointsMod.menuDimensionMode == 0, mx, my);
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
            // The waypoint's own color is the avatar fill; its first letter is the mark.
            rounded(g, x + 12, y + 13, x + 36, y + 37, color, 7);
            String initial = firstInitial(w.name());
            int initialColor = contrastText(color);
            g.text(font, initial, x + 24 - font.width(initial) / 2, y + 20, initialColor, true);
            g.text(font, fit(w.name(), Math.max(45, cardWidth - 100)), x + 44, y + 14, TEXT, true);
            String sub = dimension(w.dimension()) + " · " + (w.expiresAt() > 0 ? "Temporary" : "Permanent");
            g.text(font, fit(sub, cardWidth - 60), x + 44, y + 29, MUTED, false);
            int coordsY = y + 58;
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

    private String firstInitial(String name) {
        if (name == null || name.isBlank()) return "?";
        return name.strip().substring(0, 1).toUpperCase(Locale.ROOT);
    }

    private int contrastText(int argb) {
        int r = (argb >> 16) & 255, g = (argb >> 8) & 255, b = argb & 255;
        double luminance = 0.2126 * r + 0.7152 * g + 0.0722 * b;
        return luminance > 145 ? 0xFF101410 : 0xFFFFFFFF;
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
        if (pageCount() > 1) {
            int visiblePages = Math.min(width >= 800 ? 5 : 3, pageCount());
            int startPage = Math.max(0, Math.min(currentPage() - visiblePages / 2, pageCount() - visiblePages));
            int pageButton = 24;
            int gap = 4;
            int totalWidth = pageButton + 8 + visiblePages * pageButton + Math.max(0, visiblePages - 1) * gap + 8 + pageButton;
            int pagerX = contentRight - totalWidth;
            actionButton(g, mx, my, pagerX, footerY, pageButton, 24, "‹", false);
            int pagesX = pagerX + pageButton + 8;
            for (int i = 0; i < visiblePages; i++) {
                int pageIndex = startPage + i;
                int px = pagesX + i * (pageButton + gap);
                boolean active = pageIndex == currentPage();
                rounded(g, px, footerY, px + pageButton, footerY + 24,
                        active ? 0xFF202629 : inside(mx, my, px, footerY, pageButton, 24) ? PANEL : PANEL_ALT, 5);
                if (active) border(g, px, footerY, px + pageButton, footerY + 24, EDGE);
                String pageText = Integer.toString(pageIndex + 1);
                g.text(font, pageText, px + (pageButton - font.width(pageText)) / 2, footerY + 8,
                        active ? TEXT : MUTED, active);
            }
            int nextX = pagesX + visiblePages * (pageButton + gap) - gap + 8;
            actionButton(g, mx, my, nextX, footerY, pageButton, 24, "›", false);
        }
        g.text(font, "▦   A little less wandering. A lot more exploring.", contentX, height - 25, QUIET, false);
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
        g.text(font, label, x + (w - font.width(label)) / 2, y + (h - 8) / 2, selected ? ACCENT : MUTED, selected);
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
