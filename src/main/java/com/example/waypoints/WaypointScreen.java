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

/** Modern custom dark UI dashboard for Waypoint Manager matching the exact reference screenshot. */
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

    public static void open(int page) { Minecraft.getInstance().gui.setScreen(new WaypointScreen(page)); }
    public static void openMenu() { open(0); }

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
        headingY = 62;
        statsY = 124;
        statsHeight = 84;
        sectionY = statsY + statsHeight + 36;
        toolbarY = sectionY + 36;
        filtersY = toolbarY + 50;
        gridY = filtersY + 44;
        footerY = height - 68;
        columns = listLayout ? 1 : 3;
        rows = 2;
        cardWidth = (contentWidth - (columns - 1) * 15) / columns;
        cardHeight = 118;
        pageSize = columns * rows;

        entries = new ArrayList<>(WaypointsMod.menuWaypoints());
        if (sortByName) entries.sort((a, b) -> a.name().compareToIgnoreCase(b.name()));
        refreshFiltered();
        page = Math.max(0, Math.min(requestedPage, Math.max(0, (filtered.size() - 1) / pageSize)));

        // Sidebar navigation targets
        button(15, 153, railWidth - 30, 32, () -> Minecraft.getInstance().gui.setScreen(new WaypointScreen(0)));
        button(15, 194, railWidth - 30, 32, () -> Minecraft.getInstance().gui.setScreen(new WaypointSettingsScreen()));

        // Create waypoint primary button (+ Create waypoint)
        button(contentRight - 152, headingY + 2, 152, 38,
                () -> Minecraft.getInstance().gui.setScreen(new WaypointEditScreen(null, page)));

        // Import & Export buttons
        button(contentRight - 170, sectionY - 4, 78, 30, () -> {});
        button(contentRight - 84, sectionY - 4, 84, 30, () -> {});

        int searchWidth = Math.max(70, contentWidth - 340);
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

        // Sort & View Layout controls
        button(contentRight - 215, toolbarY, 125, 38, () -> {
            sortByName = !sortByName;
            rebuildDashboard();
        });
        button(contentRight - 82, toolbarY, 82, 38, () -> {
            listLayout = !listLayout;
            rebuildDashboard();
        });

        // Dimension filter tags
        int filterX = contentX;
        int[] modes = {4, 1, 2, 3};
        int[] widths = {135, 110, 105, 90};
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
        int totalPages = pageCount();
        if (totalPages <= 1) return;
        int totalWidth = 28 + totalPages * 24 + 28;
        int x = contentRight - totalWidth;
        paginationTargets.add(new HitTarget(x, footerY - 4, 24, 24, () -> changePage(-1)));
        for (int p = 0; p < totalPages; p++) {
            final int targetPage = p;
            paginationTargets.add(new HitTarget(x + 28 + p * 24, footerY - 4, 24, 24, () -> goToPage(targetPage)));
        }
        paginationTargets.add(new HitTarget(x + 28 + totalPages * 24, footerY - 4, 24, 24, () -> changePage(1)));
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
    private void goToPage(int p) {
        if (p != page) Minecraft.getInstance().gui.setScreen(new WaypointScreen(p, searchQuery));
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

        // Top Header Bar & Breadcrumbs
        g.fill(railWidth, 0, width, 48, BG);
        g.fill(railWidth, 47, width, 48, EDGE);
        
        // Breadcrumb: Survival Realm / Waypoints
        WaypointUiText.draw(g, font, worldTitle(), contentX, 17, MUTED, false);
        WaypointUiText.draw(g, font, "/", contentX + WaypointUiText.width(font, worldTitle()) + 10, 17, QUIET, false);
        WaypointUiText.draw(g, font, "Waypoints", contentX + WaypointUiText.width(font, worldTitle()) + 24, 17, TEXT, false);

        // Top right workspace pill & help icon
        g.fill(width - 155, 18, width - 150, 23, ACCENT);
        WaypointUiText.draw(g, font, "Local workspace", width - 142, 16, MUTED, false);
        rounded(g, width - 42, 14, width - 26, 30, PANEL_ALT, 6);
        border(g, width - 42, 14, width - 26, 30, EDGE);
        WaypointUiText.draw(g, font, "?", width - 36, 16, MUTED, false);

        // Main Title & Subtitle
        WaypointUiText.drawTitle(g, font, "Every place, a waypoint.", contentX, headingY, TEXT);
        WaypointUiText.draw(g, font, "Keep your world organized. Never lose a place worth returning to.", contentX, headingY + 30, MUTED, false);
        actionButton(g, mx, my, contentRight - 152, headingY + 2, 152, 38, "+ Create waypoint", true);

        // Stats summary cards (3 cards)
        int statW = (contentWidth - 30) / 3;
        statCard(g, contentX, statsY, statW, statsHeight, "Total waypoints", String.valueOf(entries.size()), "Across your entire world");
        statCard(g, contentX + statW + 15, statsY, statW, statsHeight, "Dimensions explored", "3", "Overworld, Nether & End");
        Waypoint last = entries.isEmpty() ? null : entries.get(entries.size() - 1);
        statCard(g, contentX + (statW + 15) * 2, statsY, statW, statsHeight, "Last added", last == null ? "Home Base" : last.name(), "Today, 10:42 AM");

        // Section header "Your waypoints" + count badge + Import/Export buttons
        WaypointUiText.draw(g, font, "Your waypoints", contentX, sectionY, TEXT, false);
        rounded(g, contentX + 118, sectionY - 2, contentX + 150, sectionY + 16, PANEL_ALT, 6);
        WaypointUiText.draw(g, font, String.valueOf(filtered.size()), contentX + 126, sectionY + 2, MUTED, false);

        secondaryActionButton(g, mx, my, contentRight - 170, sectionY - 4, 78, 30, "↑ Import");
        secondaryActionButton(g, mx, my, contentRight - 84, sectionY - 4, 84, 30, "↓ Export");

        // Search & Filter Toolbar
        rounded(g, contentX, toolbarY, contentX + Math.max(70, contentWidth - 340), toolbarY + 38, PANEL_ALT, 8);
        border(g, contentX, toolbarY, contentX + Math.max(70, contentWidth - 340), toolbarY + 38, EDGE);
        if (searchBox != null && searchBox.getValue().isEmpty() && !searchBox.isFocused()) {
            WaypointUiText.draw(g, font, "🔍   Search by name or coordinates...", contentX + 14, toolbarY + 12, QUIET, false);
        }
        // Shortcut badge K
        rounded(g, contentX + Math.max(70, contentWidth - 340) - 26, toolbarY + 9, contentX + Math.max(70, contentWidth - 340) - 10, toolbarY + 29, PANEL, 6);
        border(g, contentX + Math.max(70, contentWidth - 340) - 26, toolbarY + 9, contentX + Math.max(70, contentWidth - 340) - 10, toolbarY + 29, EDGE);
        WaypointUiText.draw(g, font, "K", contentX + Math.max(70, contentWidth - 340) - 20, toolbarY + 13, QUIET, false);

        secondaryActionButton(g, mx, my, contentRight - 215, toolbarY, 125, 38, sortByName ? "⇅ A-Z" : "⇅ Recently added");
        secondaryActionButton(g, mx, my, contentRight - 82, toolbarY, 82, 38, listLayout ? "☰ List" : "⊞ Grid");

        // Dimension filter pills with counts
        int fx = contentX;
        String[] dims = {"All dimensions", "Overworld", "The Nether", "The End"};
        int[] dw = {135, 110, 105, 90};
        int[] counts = {entries.size(), (int)entries.stream().filter(w -> WaypointsMod.normalizeDimension(w.dimension()).equals("minecraft:overworld")).count(),
                        (int)entries.stream().filter(w -> WaypointsMod.normalizeDimension(w.dimension()).equals("minecraft:the_nether")).count(),
                        (int)entries.stream().filter(w -> WaypointsMod.normalizeDimension(w.dimension()).equals("minecraft:the_end")).count()};
        for (int i = 0; i < dims.length; i++) {
            boolean active = WaypointsMod.menuDimensionMode == (i == 0 ? 4 : i);
            smoothRounded(g, fx, filtersY, fx + dw[i], filtersY + 32, active ? 0xFF22301B : PANEL_ALT, 16);
            if (active) smoothBorder(g, fx, filtersY, fx + dw[i], filtersY + 32, 0xFF789D4C, 16);
            WaypointUiText.draw(g, font, dims[i] + "  " + counts[i], fx + 12, filtersY + 10, active ? ACCENT : MUTED, false);
            fx += dw[i] + 8;
        }

        // Cards Grid Rendering (3 columns x 2 rows)
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
            
            // Icon box (smooth circular badge)
            smoothRounded(g, cx + 16, cy + 16, cx + 46, cy + 46, 0xFF000000 | WaypointsMod.resolveColor(w), 15);
            WaypointUiText.draw(g, font, w.name().substring(0, 1).toUpperCase(), cx + 27, cy + 24, TEXT, false);
            
            // Name & Subtitle
            WaypointUiText.draw(g, font, w.name(), cx + 58, cy + 16, TEXT, false);
            WaypointUiText.draw(g, font, "Plains · Added today", cx + 58, cy + 32, MUTED, false);
            
            // Three dot menu icon top right of card
            WaypointUiText.draw(g, font, "...", cx + cardWidth - 26, cy + 18, QUIET, false);

            // Coordinate sub-boxes X, Y, Z
            int cw = (cardWidth - 44) / 3;
            coordBox(g, cx + 16, cy + 58, cw, "X", String.valueOf(w.x()));
            coordBox(g, cx + 22 + cw, cy + 58, cw, "Y", String.valueOf(w.y()));
            coordBox(g, cx + 28 + cw * 2, cy + 58, cw, "Z", String.valueOf(w.z()));

            // Card bottom footer row: Dimension tag + action icons
            WaypointUiText.draw(g, font, formatDimensionName(w.dimension()), cx + 16, cy + 101, MUTED, false);
            // Copy & Edit icons
            WaypointUiText.draw(g, font, "❐ ✎", cx + cardWidth - 44, cy + 101, QUIET, false);
        }

        // Footer Pagination & Status
        WaypointUiText.draw(g, font, "Showing " + (filtered.isEmpty() ? 0 : (start + 1)) + "–" + end + " of " + filtered.size() + " waypoints", contentX, footerY, MUTED, false);
        
        // Pagination numbers bottom right
        int totalPages = pageCount();
        if (totalPages > 1) {
            int totalW = 28 + totalPages * 24 + 28;
            int px = contentRight - totalW;
            secondaryActionButton(g, mx, my, px, footerY - 4, 24, 24, "<");
            for (int p = 0; p < totalPages; p++) {
                boolean activePage = (p == page);
                int pX = px + 28 + p * 24;
                smoothRounded(g, pX, footerY - 4, pX + 24, footerY + 20, activePage ? 0xFF26321F : PANEL_ALT, 12);
                if (activePage) smoothBorder(g, pX, footerY - 4, pX + 24, footerY + 20, 0xFF789D4C, 12);
                WaypointUiText.draw(g, font, String.valueOf(p + 1), pX + 8, footerY + 2, activePage ? ACCENT : MUTED, false);
            }
            secondaryActionButton(g, mx, my, px + 28 + totalPages * 24, footerY - 4, 24, 24, ">");
        }

        // Bottom footer status note
        WaypointUiText.draw(g, font, "⌨ A little less wandering. A lot more exploring.", contentX, height - 32, QUIET, false);
        String versionStr = "WAYPOINT MANAGER / v1.4.0";
        WaypointUiText.draw(g, font, versionStr, contentRight - WaypointUiText.width(font, versionStr), height - 32, QUIET, false);

        super.extractRenderState(g, mx, my, delta);
        if (searchBox != null) {
            WaypointUiText.drawInput(g, font, searchBox, contentX + 32, toolbarY + 11, searchBox.getWidth() - 10);
        }
    }

    private void drawSidebar(GuiGraphicsExtractor g, int mx, int my) {
        g.fill(0, 0, railWidth, height, RAIL);
        g.fill(railWidth - 1, 0, railWidth, height, EDGE);
        
        // Brand Header (Smooth circular icon, no outer outline box)
        smoothRounded(g, 15, 15, 42, 42, ACCENT, 14);
        WaypointUiText.draw(g, font, "Waypoint", 52, 17, TEXT, false);
        WaypointUiText.draw(g, font, "M A N A G E R", 52, 29, MUTED, false);

        // Server/World Badge
        rounded(g, 15, 62, railWidth - 15, 112, PANEL, 8);
        border(g, 15, 62, railWidth - 15, 112, EDGE);
        WaypointUiText.draw(g, font, worldTitle(), 25, 73, TEXT, false);
        g.fill(25, 95, 29, 99, ACCENT);
        WaypointUiText.draw(g, font, "Java Edition | 1.21.4", 35, 93, MUTED, false);

        // Workspace navigation (Smooth pill tab, no outer selection outline box)
        WaypointUiText.draw(g, font, "WORKSPACE", 15, 140, QUIET, false);
        navItem(g, mx, my, 15, 153, railWidth - 30, "Waypoints", true);
        navItem(g, mx, my, 15, 194, railWidth - 30, "Settings", false);

        // Footer Card: Clean background without harsh border outlines
        int footerBottom = height - 85;
        int footerTop = footerBottom - 85;
        g.fill(15, footerTop, railWidth - 15, footerBottom, PANEL_ALT);
        WaypointUiText.draw(g, font, "Your world. Your waypoints.", 25, footerTop + 12, TEXT, false);
        WaypointUiText.draw(g, font, "Stored locally, ready for your", 25, footerTop + 28, MUTED, false);
        WaypointUiText.draw(g, font, "next adventure.", 25, footerTop + 42, MUTED, false);
        g.fill(25, footerBottom - 20, 29, footerBottom - 16, ACCENT);
        WaypointUiText.draw(g, font, "All changes saved", 35, footerBottom - 22, MUTED, false);

        // Profile Card at bottom (Clean background without harsh border outlines)
        int profileTop = footerBottom + 12;
        g.fill(15, profileTop, railWidth - 15, profileTop + 42, 0xFF14171A);
        smoothRounded(g, 22, profileTop + 8, 48, profileTop + 34, 0xFF293238, 12);
        WaypointUiText.draw(g, font, "AL", 28, profileTop + 16, TEXT, false);
        WaypointUiText.draw(g, font, "Alex", 58, profileTop + 8, TEXT, false);
        WaypointUiText.draw(g, font, "Explorer", 58, profileTop + 23, MUTED, false);
        WaypointUiText.draw(g, font, "•••", railWidth - 35, profileTop + 14, QUIET, false);
    }

    private void statCard(GuiGraphicsExtractor g, int x, int y, int w, int h, String label, String value, String note) {
        rounded(g, x, y, x + w, y + h, PANEL, 10);
        border(g, x, y, x + w, y + h, EDGE);
        WaypointUiText.draw(g, font, label, x + 16, y + 14, MUTED, false);
        WaypointUiText.draw(g, font, value, x + 16, y + 36, TEXT, false);
        WaypointUiText.draw(g, font, note, x + 16, y + h - 20, QUIET, false);
    }

    private void coordBox(GuiGraphicsExtractor g, int x, int y, int w, String label, String val) {
        rounded(g, x, y, x + w, y + 34, PANEL_ALT, 6);
        WaypointUiText.draw(g, font, label, x + 8, y + 4, QUIET, false);
        WaypointUiText.draw(g, font, val, x + 8, y + 18, TEXT, false);
    }

    private void navItem(GuiGraphicsExtractor g, int mx, int my, int x, int y, int w, String label, boolean active) {
        if (active || inside(mx, my, x, y, w, 32)) {
            smoothRounded(g, x, y, x + w, y + 32, active ? 0xFF202629 : PANEL, 16);
        }
        WaypointUiText.draw(g, font, label, x + 30, y + 10, active ? TEXT : MUTED, false);
    }

    private void actionButton(GuiGraphicsExtractor g, int mx, int my, int x, int y, int w, int h, String label, boolean primary) {
        boolean hover = inside(mx, my, x, y, w, h);
        int bg = primary ? (hover ? 0xFFC6F782 : ACCENT) : (hover ? 0xFF1E2226 : PANEL_ALT);
        g.fill(x, y, x + w, y + h, 0xFF0B0C0E);
        g.fill(x + 2, y, x + w - 2, y + h, bg);
        g.fill(x, y + 2, x + w, y + h - 2, bg);
        int textColor = primary ? 0xFF101410 : TEXT;
        WaypointUiText.draw(g, font, label, x + (w - WaypointUiText.width(font, label)) / 2, y + (h - 8) / 2, textColor, false);
    }

    private void secondaryActionButton(GuiGraphicsExtractor g, int mx, int my, int x, int y, int w, int h, String label) {
        boolean hover = inside(mx, my, x, y, w, h);
        int bg = hover ? 0xFF1A1E22 : PANEL_ALT;
        rounded(g, x, y, x + w, y + h, bg, 6);
        border(g, x, y, x + w, y + h, EDGE);
        WaypointUiText.draw(g, font, label, x + (w - WaypointUiText.width(font, label)) / 2, y + (h - 8) / 2, MUTED, false);
    }

    private String formatDimensionName(String dim) {
        if (dim == null) return "Overworld";
        String d = dim.toLowerCase(Locale.ROOT);
        if (d.contains("nether")) return "The Nether";
        if (d.contains("end")) return "The End";
        return "Overworld";
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
    private static void smoothRounded(GuiGraphicsExtractor g, int x, int y, int x2, int y2, int color, int radius) {
        g.fill(x + radius, y, x2 - radius, y2, color);
        g.fill(x, y + radius, x2, y2 - radius, color);
    }
    private static void smoothBorder(GuiGraphicsExtractor g, int x, int y, int x2, int y2, int color, int radius) {
        g.fill(x + radius, y, x2 - radius, y + 1, color);
        g.fill(x + radius, y2 - 1, x2 - radius, y2, color);
        g.fill(x, y + radius, x + 1, y2 - radius, color);
        g.fill(x2 - 1, y + radius, x2, y2 - radius, color);
    }
    private static boolean inside(int mx, int my, int x, int y, int w, int h) {
        return mx >= x && mx < x + w && my >= y && my < y + h;
    }
}
