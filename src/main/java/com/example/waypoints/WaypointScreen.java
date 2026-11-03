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

/**
 * Simple Waypoints' main workspace.
 *
 * The screen combines the four approved design directions into one interface:
 * - a navigation rail and waypoint directory;
 * - a dedicated selected-waypoint inspector (the compact preview concept);
 * - a search field and dimension filters;
 * - the separate full waypoint editor and matching in-world name labels.
 *
 * Rendering is static: there are no per-frame texture builds, animation timers,
 * or thousands of individual child widgets. Visible list rows are a fixed set
 * of hit targets and the displayed data is cached when the screen opens.
 */
public final class WaypointScreen extends Screen {
    private static final String[] FILTER_LABELS = {"HERE", "OVERWORLD", "NETHER", "END", "ALL"};

    // Midnight blue / cyan / violet theme from the approved concepts.
    private static final int BACKDROP = 0xD0060A18;
    private static final int FRAME = 0xFF364B83;
    private static final int PANEL = 0xF20B1428;
    private static final int RAIL = 0xFF0C1830;
    private static final int CARD = 0xFF142441;
    private static final int CARD_HOVER = 0xFF1E355C;
    private static final int EDGE = 0xFF304A78;
    private static final int TEXT = 0xFFF2F6FF;
    private static final int MUTED = 0xFFA7B9DA;
    private static final int QUIET = 0xFF7085AE;
    private static final int CYAN = 0xFF48E6F2;
    private static final int CYAN_DARK = 0xFF123E5A;
    private static final int VIOLET = 0xFFB477FF;
    private static final int VIOLET_DARK = 0xFF38245E;
    private static final int DANGER = 0xFFFF6377;

    private final int requestedPage;
    private int page;
    private String searchQuery;
    private int left, top, right, bottom;
    private int panelWidth, panelHeight;
    private int railWidth;
    private int listX, listRight, listWidth;
    private int detailX, detailRight;
    private int rowsPerPage, rowHeight, listTop, footerY;
    private boolean fullLayout;
    private boolean detailLayout;
    private List<Waypoint> entries = List.of();
    private List<Waypoint> filtered = List.of();
    private Waypoint selectedWaypoint;
    private EditBox searchBox;
    private String playerWorldId = "unknown";
    private String playerDimension = "unknown";

    public WaypointScreen(int page) {
        this(page, "");
    }

    private WaypointScreen(int page, String search) {
        super(Component.literal("Simple Waypoints"));
        this.requestedPage = page;
        this.page = page;
        this.searchQuery = search == null ? "" : search;
    }

    static void openMenu() { open(0); }

    static void open(int page) {
        Minecraft.getInstance().gui.setScreen(new WaypointScreen(page));
    }

    private List<Waypoint> filteredEntries() {
        return filtered;
    }

    /** Rebuild the search result only when the screen opens, the query changes, or a point is removed. */
    private void refreshFilteredEntries() {
        String q = searchQuery == null ? "" : searchQuery.trim().toLowerCase(Locale.ROOT);
        if (q.isEmpty()) {
            filtered = entries;
            return;
        }
        List<Waypoint> result = new ArrayList<>();
        for (Waypoint w : entries) {
            String haystack = (w.name() + " " + w.dimension() + " " + w.x() + " " + w.y() + " " + w.z())
                    .toLowerCase(Locale.ROOT);
            if (haystack.contains(q)) result.add(w);
        }
        filtered = List.copyOf(result);
    }

    private int pages() {
        return Math.max(1, (filteredEntries().size() + rowsPerPage - 1) / rowsPerPage);
    }

    private int currentPage() {
        return Math.max(0, Math.min(page, pages() - 1));
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
        // Quiet corner-light accents, kept static to avoid UI animation overhead.
        g.fill(0, 0, width, 1, 0x223D9DFF);
        g.fill(0, height - 1, width, height, 0x222DDAE8);
    }

    @Override
    protected void init() {
        panelWidth = Math.min(760, Math.max(300, width - 18));
        panelHeight = Math.min(420, Math.max(220, height - 16));
        left = (width - panelWidth) / 2;
        top = (height - panelHeight) / 2;
        right = left + panelWidth;
        bottom = top + panelHeight;

        fullLayout = panelWidth >= 710 && panelHeight >= 398;
        detailLayout = panelWidth >= 590 && panelHeight >= 340;
        railWidth = fullLayout ? 142 : 0;
        listX = fullLayout ? left + railWidth + 14 : left + 14;
        detailRight = right - 14;
        detailX = detailLayout ? detailRight - (fullLayout ? 220 : 190) : detailRight;
        listRight = detailLayout ? detailX - 12 : detailRight;
        listWidth = listRight - listX;
        rowHeight = fullLayout ? 49 : detailLayout ? 45 : 42;
        listTop = top + (fullLayout ? 105 : detailLayout ? 126 : 126);
        footerY = bottom - 34;
        rowsPerPage = Math.max(1, Math.min(5, (footerY - listTop - 10) / rowHeight));

        entries = WaypointsMod.menuWaypoints();
        refreshFilteredEntries();
        playerWorldId = WaypointsMod.currentWorldId();
        playerDimension = WaypointsMod.currentDimension();
        List<Waypoint> matches = filteredEntries();
        page = Math.max(0, Math.min(requestedPage, Math.max(0, (matches.size() - 1) / rowsPerPage)));
        if (selectedWaypoint == null || !matches.contains(selectedWaypoint)) {
            selectedWaypoint = matches.isEmpty() ? null : matches.get(0);
        }

        // Close control and create action.
        hit(right - 30, top + 12, 18, 18, this::onClose);
        if (fullLayout) {
            hit(left + 14, top + 66, railWidth - 28, 30,
                    () -> Minecraft.getInstance().gui.setScreen(new WaypointEditScreen(null, currentPage())));
            addRailFilterTargets();
            hit(left + 14, bottom - 62, railWidth - 28, 22,
                    () -> WaypointsMod.showWaypointBeams = !WaypointsMod.showWaypointBeams);
            hit(left + 14, bottom - 34, railWidth - 28, 22,
                    () -> WaypointsMod.showWaypointLabels = !WaypointsMod.showWaypointLabels);
        } else {
            addCompactFilterTargets();
            int createX = right - 100;
            hit(createX, top + 13, 84, 23,
                    () -> Minecraft.getInstance().gui.setScreen(new WaypointEditScreen(null, currentPage())));
            hit(left + 14, top + 70, 82, 20,
                    () -> WaypointsMod.showWaypointBeams = !WaypointsMod.showWaypointBeams);
            hit(left + 102, top + 70, 88, 20,
                    () -> WaypointsMod.showWaypointLabels = !WaypointsMod.showWaypointLabels);
        }

        int searchY = fullLayout ? top + 48 : top + 99;
        searchBox = new EditBox(font, listX + 1, searchY + 1, Math.max(48, listWidth - 2), 19,
                Component.literal("Search waypoints"));
        searchBox.setMaxLength(48);
        searchBox.setValue(searchQuery);
        searchBox.setBordered(false);
        searchBox.setResponder(value -> {
            searchQuery = value;
            page = 0;
            refreshFilteredEntries();
            List<Waypoint> now = filteredEntries();
            if (selectedWaypoint == null || !now.contains(selectedWaypoint)) {
                selectedWaypoint = now.isEmpty() ? null : now.get(0);
            }
        });
        addRenderableWidget(searchBox);

        // A fixed number of row hit targets. The current filtered result is looked up
        // when clicked, so typing in search never leaves stale row-to-waypoint mappings.
        for (int slot = 0; slot < rowsPerPage; slot++) {
            final int visibleSlot = slot;
            int y = rowY(slot);
            if (detailLayout) {
                hit(listX, y, listWidth, rowHeight - 4, () -> selectVisibleRow(visibleSlot));
            } else {
                hit(listX, y, Math.max(20, listWidth - 84), rowHeight - 4,
                        () -> selectVisibleRow(visibleSlot));
                hit(listRight - 76, y + 8, 34, 22, () -> editVisibleRow(visibleSlot));
                hit(listRight - 36, y + 8, 26, 22, () -> deleteVisibleRow(visibleSlot));
            }
        }

        if (detailLayout) {
            int actionY = bottom - 72;
            hit(detailX + 10, actionY, (detailRight - detailX - 26) / 2, 25, () -> {
                if (selectedWaypoint != null) {
                    Minecraft.getInstance().gui.setScreen(new WaypointEditScreen(selectedWaypoint, currentPage()));
                }
            });
            hit(detailX + 16 + (detailRight - detailX - 26) / 2, actionY,
                    (detailRight - detailX - 26) / 2, 25, () -> {
                        if (selectedWaypoint != null) {
                            WaypointsMod.remove(selectedWaypoint);
                            selectedWaypoint = null;
                            entries = WaypointsMod.menuWaypoints();
                            refreshFilteredEntries();
                            List<Waypoint> now = filteredEntries();
                            selectedWaypoint = now.isEmpty() ? null : now.get(0);
                        }
                    });
        }

        int navX = listX;
        hit(navX, footerY, 27, 22, () -> {
            if (currentPage() > 0) {
                Minecraft.getInstance().gui.setScreen(new WaypointScreen(currentPage() - 1, searchQuery));
            }
        });
        hit(navX + 33, footerY, 27, 22, () -> {
            if (currentPage() < pages() - 1) {
                Minecraft.getInstance().gui.setScreen(new WaypointScreen(currentPage() + 1, searchQuery));
            }
        });
        hit(right - 78, footerY, 64, 22, this::onClose);
    }

    private void addRailFilterTargets() {
        for (int i = 0; i < FILTER_LABELS.length; i++) {
            final int mode = i;
            int y = top + 112 + i * 27;
            hit(left + 12, y, railWidth - 24, 23, () -> {
                WaypointsMod.menuDimensionMode = mode;
                Minecraft.getInstance().gui.setScreen(new WaypointScreen(0, searchQuery));
            });
        }
    }

    private void addCompactFilterTargets() {
        int x = left + 12;
        int y = top + 45;
        int[] widths = {48, 78, 54, 42, 42};
        for (int i = 0; i < FILTER_LABELS.length; i++) {
            final int mode = i;
            hit(x, y, widths[i], 21, () -> {
                WaypointsMod.menuDimensionMode = mode;
                Minecraft.getInstance().gui.setScreen(new WaypointScreen(0, searchQuery));
            });
            x += widths[i] + 4;
        }
    }

    private Waypoint visibleWaypoint(int slot) {
        List<Waypoint> matches = filteredEntries();
        int index = currentPage() * rowsPerPage + slot;
        return index >= 0 && index < matches.size() ? matches.get(index) : null;
    }

    private void selectVisibleRow(int slot) {
        Waypoint waypoint = visibleWaypoint(slot);
        if (waypoint != null) selectedWaypoint = waypoint;
    }

    private void editVisibleRow(int slot) {
        Waypoint waypoint = visibleWaypoint(slot);
        if (waypoint != null) Minecraft.getInstance().gui.setScreen(
                new WaypointEditScreen(waypoint, currentPage()));
    }

    private void deleteVisibleRow(int slot) {
        Waypoint waypoint = visibleWaypoint(slot);
        if (waypoint == null) return;
        WaypointsMod.remove(waypoint);
        entries = WaypointsMod.menuWaypoints();
        refreshFilteredEntries();
        List<Waypoint> now = filteredEntries();
        selectedWaypoint = now.isEmpty() ? null : now.get(Math.min(slot, now.size() - 1));
    }

    private void hit(int x, int y, int w, int h, Runnable action) {
        addRenderableWidget(Button.builder(Component.literal(""), button -> action.run())
                .bounds(x, y, w, h).build());
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float delta) {
        super.extractRenderState(g, mouseX, mouseY, delta);

        rounded(g, left - 1, top - 1, right + 1, bottom + 1, FRAME, 15);
        rounded(g, left, top, right, bottom, PANEL, 14);

        if (fullLayout) {
            rounded(g, left + 1, top + 1, left + railWidth, bottom - 1, RAIL, 13);
            g.fill(left + railWidth, top + 16, left + railWidth + 1, bottom - 16, 0x55465F91);
            drawNavigationRail(g, mouseX, mouseY);
        } else {
            drawCompactHeader(g, mouseX, mouseY);
        }

        drawListHeader(g, mouseX, mouseY);
        drawSearchField(g, mouseX, mouseY);
        drawRows(g, mouseX, mouseY);
        if (detailLayout) drawInspector(g, mouseX, mouseY);
        drawFooter(g, mouseX, mouseY);
    }

    private void drawNavigationRail(GuiGraphicsExtractor g, int mx, int my) {
        int iconX = left + 15;
        int iconY = top + 17;
        rounded(g, iconX, iconY, iconX + 30, iconY + 30, VIOLET_DARK, 9);
        g.fill(iconX + 8, iconY + 6, iconX + 22, iconY + 8, CYAN);
        g.fill(iconX + 8, iconY + 11, iconX + 19, iconY + 13, CYAN);
        g.fill(iconX + 8, iconY + 16, iconX + 22, iconY + 18, CYAN);
        g.text(font, "SIMPLE WAYPOINTS", left + 52, top + 17, TEXT, true);
        g.text(font, "MARK  /  EXPLORE", left + 52, top + 30, MUTED, false);

        actionButton(g, mx, my, left + 14, top + 66, railWidth - 28, 30,
                "+  CREATE WAYPOINT", true, false);
        g.text(font, "DESTINATION SCOPE", left + 15, top + 104, QUIET, false);

        int active = selectedFilter();
        for (int i = 0; i < FILTER_LABELS.length; i++) {
            int y = top + 112 + i * 27;
            boolean selected = active == i;
            boolean hover = inside(mx, my, left + 12, y, railWidth - 24, 23);
            rounded(g, left + 12, y, left + railWidth - 12, y + 23,
                    selected ? VIOLET_DARK : hover ? CARD_HOVER : RAIL, 7);
            if (selected) g.fill(left + 12, y + 5, left + 15, y + 18, CYAN);
            g.text(font, FILTER_LABELS[i], left + 23, y + 8, selected ? TEXT : MUTED, selected);
        }

        g.fill(left + 14, bottom - 70, left + railWidth - 14, bottom - 69, 0x55465F91);
        railToggle(g, mx, my, left + 14, bottom - 62, railWidth - 28, 22,
                "BEACON BEAM", WaypointsMod.showWaypointBeams, CYAN);
        railToggle(g, mx, my, left + 14, bottom - 34, railWidth - 28, 22,
                "NAME LABELS", WaypointsMod.showWaypointLabels, VIOLET);
    }

    private void drawCompactHeader(GuiGraphicsExtractor g, int mx, int my) {
        g.text(font, "SIMPLE WAYPOINTS", left + 15, top + 14, TEXT, true);
        g.text(font, "MARK  /  EXPLORE", left + 15, top + 28, MUTED, false);
        actionButton(g, mx, my, right - 100, top + 13, 84, 23, "+ NEW", true, false);
        int x = left + 12;
        int y = top + 45;
        int[] widths = {48, 78, 54, 42, 42};
        for (int i = 0; i < FILTER_LABELS.length; i++) {
            boolean active = selectedFilter() == i;
            boolean hover = inside(mx, my, x, y, widths[i], 21);
            rounded(g, x, y, x + widths[i], y + 21,
                    active ? VIOLET_DARK : hover ? CARD_HOVER : CARD, 6);
            g.text(font, FILTER_LABELS[i], x + (widths[i] - font.width(FILTER_LABELS[i])) / 2,
                    y + 7, active ? TEXT : MUTED, active);
            x += widths[i] + 4;
        }
        smallToggle(g, mx, my, left + 14, top + 70, 82, "BEAM", WaypointsMod.showWaypointBeams, CYAN);
        smallToggle(g, mx, my, left + 102, top + 70, 88, "NAMES", WaypointsMod.showWaypointLabels, VIOLET);
    }

    private void drawListHeader(GuiGraphicsExtractor g, int mx, int my) {
        if (!fullLayout) return;
        int y = top + 18;
        String heading = fullLayout ? "Waypoint directory" : "Your waypoints";
        g.text(font, heading, listX, y, TEXT, true);
        String count = filteredEntries().size() + " locations";
        g.text(font, count, listRight - font.width(count), y + 1, MUTED, false);
    }

    private void drawSearchField(GuiGraphicsExtractor g, int mx, int my) {
        int searchY = fullLayout ? top + 48 : top + 99;
        boolean focused = searchBox != null && searchBox.isFocused();
        rounded(g, listX, searchY, listRight, searchY + 23, focused ? 0xFF3A6C9B : EDGE, 7);
        rounded(g, listX + 1, searchY + 1, listRight - 1, searchY + 22, CARD, 6);
        // Tiny magnifier drawn from fills so it renders consistently with Minecraft's font set.
        g.fill(listX + 9, searchY + 7, listX + 15, searchY + 8, CYAN);
        g.fill(listX + 9, searchY + 12, listX + 15, searchY + 13, CYAN);
        g.fill(listX + 9, searchY + 8, listX + 10, searchY + 12, CYAN);
        g.fill(listX + 14, searchY + 8, listX + 15, searchY + 12, CYAN);
        g.fill(listX + 14, searchY + 12, listX + 17, searchY + 15, CYAN);
        String query = searchBox == null ? "" : searchBox.getValue();
        if (query.isEmpty() && !focused) {
            g.text(font, "Search by name or coordinates", listX + 24, searchY + 7, QUIET, false);
        } else if (searchBox != null) {
            g.text(font, query, listX + 24, searchY + 7, TEXT, false);
            if (focused && (System.currentTimeMillis() / 500) % 2 == 0) {
                int caret = listX + 24 + font.width(query);
                g.fill(caret, searchY + 5, caret + 1, searchY + 18, TEXT);
            }
        }
    }

    private void drawRows(GuiGraphicsExtractor g, int mx, int my) {
        List<Waypoint> matches = filteredEntries();
        int start = currentPage() * rowsPerPage;
        int end = Math.min(matches.size(), start + rowsPerPage);
        if (matches.isEmpty()) {
            int y = listTop + 6;
            int h = Math.min(82, footerY - y - 8);
            rounded(g, listX, y, listRight, y + h, CARD, 10);
            rounded(g, listX + 13, y + 14, listX + 43, y + 44, VIOLET_DARK, 9);
            g.text(font, "+", listX + 23, y + 24, CYAN, true);
            g.text(font, searchQuery.isBlank() ? "No waypoints yet" : "No matching waypoints",
                    listX + 53, y + 16, TEXT, true);
            g.text(font, searchQuery.isBlank() ? "Create your first beacon marker." : "Try another name or coordinate.",
                    listX + 53, y + 33, MUTED, false);
            return;
        }

        for (int i = start; i < end; i++) {
            Waypoint waypoint = matches.get(i);
            int slot = i - start;
            int y = rowY(slot);
            int h = rowHeight - 4;
            boolean hover = inside(mx, my, listX, y, listWidth, h);
            boolean selected = waypoint.equals(selectedWaypoint);
            int color = 0xFF000000 | WaypointsMod.resolveColor(waypoint);
            rounded(g, listX, y, listRight, y + h,
                    selected ? 0xFF1D3151 : hover ? CARD_HOVER : CARD, 8);
            if (selected) {
                g.fill(listX, y + 7, listX + 2, y + h - 7, CYAN);
                rounded(g, listX + 9, y + 9, listX + 27, y + h - 9, color, 6);
            } else {
                rounded(g, listX + 9, y + 9, listX + 27, y + h - 9, color, 6);
            }
            int tx = listX + 37;
            String name = fitText(waypoint.name(), Math.max(80, listWidth - 140));
            g.text(font, name, tx, y + 8, TEXT, true);
            String meta = dimensionName(waypoint.dimension()) + "  ·  " + waypoint.x() + ", " + waypoint.y() + ", " + waypoint.z();
            g.text(font, fitText(meta, Math.max(70, listWidth - 145)), tx, y + 24, MUTED, false);
            if (detailLayout) {
                g.text(font, selected ? "SELECTED" : "OPEN", listRight - 43, y + 8,
                        selected ? CYAN : QUIET, false);
            } else {
                actionButton(g, mx, my, listRight - 76, y + 8, 34, 22, "EDIT", false, false);
                actionButton(g, mx, my, listRight - 36, y + 8, 26, 22, "×", false, true);
            }
        }
    }

    private void drawInspector(GuiGraphicsExtractor g, int mx, int my) {
        int y = fullLayout ? top + 50 : top + 112;
        int h = bottom - y - 47;
        rounded(g, detailX, y, detailRight, y + h, 0xFF101E38, 11);
        g.text(font, "DESTINATION PREVIEW", detailX + 12, y + 12, QUIET, false);
        if (selectedWaypoint == null) {
            g.text(font, "Select a waypoint", detailX + 12, y + 42, TEXT, true);
            g.text(font, "Its details appear here.", detailX + 12, y + 58, MUTED, false);
            return;
        }

        Waypoint w = selectedWaypoint;
        int rgb = 0xFF000000 | WaypointsMod.resolveColor(w);
        if (fullLayout) {
            rounded(g, detailX + 12, y + 34, detailX + 52, y + 74, 0xFF1B3156, 10);
            rounded(g, detailX + 23, y + 42, detailX + 41, y + 66, rgb, 8);
            g.text(font, fitText(w.name(), detailRight - detailX - 26), detailX + 12, y + 84, TEXT, true);
            g.text(font, dimensionName(w.dimension()), detailX + 12, y + 100, CYAN, false);
            g.fill(detailX + 12, y + 120, detailRight - 12, y + 121, 0x55465F91);
            g.text(font, "COORDINATES", detailX + 12, y + 132, QUIET, false);
            drawInfoRow(g, "X", String.valueOf(w.x()), detailX + 12, y + 148);
            drawInfoRow(g, "Y", String.valueOf(w.y()), detailX + 12, y + 169);
            drawInfoRow(g, "Z", String.valueOf(w.z()), detailX + 12, y + 190);
            g.text(font, "DISTANCE", detailX + 12, y + 220, QUIET, false);
            g.text(font, distanceTo(w), detailX + 12, y + 235, TEXT, true);
            g.text(font, expiryText(w), detailX + 12, y + 254, MUTED, false);
        } else {
            rounded(g, detailX + 12, y + 34, detailX + 42, y + 64, 0xFF1B3156, 8);
            rounded(g, detailX + 21, y + 41, detailX + 33, y + 57, rgb, 6);
            g.text(font, fitText(w.name(), detailRight - detailX - 60), detailX + 50, y + 43, TEXT, true);
            g.text(font, dimensionName(w.dimension()), detailX + 50, y + 57, CYAN, false);
            g.fill(detailX + 12, y + 77, detailRight - 12, y + 78, 0x55465F91);
            g.text(font, "COORDINATES", detailX + 12, y + 88, QUIET, false);
            String xyz = w.x() + "   " + w.y() + "   " + w.z();
            g.text(font, fitText(xyz, detailRight - detailX - 24), detailX + 12, y + 103, TEXT, false);
            g.text(font, "DISTANCE", detailX + 12, y + 130, QUIET, false);
            g.text(font, distanceTo(w), detailX + 12, y + 145, TEXT, true);
        }

        int actionY = bottom - 72;
        int half = (detailRight - detailX - 26) / 2;
        actionButton(g, mx, my, detailX + 10, actionY, half, 25, "EDIT", true, false);
        actionButton(g, mx, my, detailX + 16 + half, actionY, half, 25, "DELETE", false, true);
    }

    private String expiryText(Waypoint w) {
        if (w.expiresAt() <= 0) return "PERMANENT MARKER";
        long remaining = Math.max(0L, (w.expiresAt() - System.currentTimeMillis()) / 1000L);
        return "EXPIRES IN " + remaining + "s";
    }

    private void drawInfoRow(GuiGraphicsExtractor g, String label, String value, int x, int y) {
        rounded(g, x, y, x + 20, y + 16, 0xFF1D3151, 5);
        g.text(font, label, x + 7, y + 4, CYAN, true);
        g.text(font, value, x + 29, y + 4, TEXT, false);
        g.fill(x + 29, y + 17, detailRight - 12, y + 18, 0x223C5A8B);
    }

    private String distanceTo(Waypoint w) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null) return "—";
        String waypointDimension = WaypointsMod.normalizeDimension(w.dimension());
        if (!java.util.Objects.equals(w.worldId(), playerWorldId)
                || (!"all".equals(waypointDimension)
                && !waypointDimension.equals(playerDimension))) {
            return "Other dimension";
        }
        double dx = w.x() + 0.5 - mc.player.getX();
        double dy = w.y() + 0.5 - mc.player.getY();
        double dz = w.z() + 0.5 - mc.player.getZ();
        double d = Math.sqrt(dx * dx + dy * dy + dz * dz);
        if (d >= 1000.0) return String.format(Locale.ROOT, "%.1f km", d / 1000.0);
        return Math.round(d) + " m";
    }

    private String dimensionName(String dimension) {
        String d = WaypointsMod.normalizeDimension(dimension);
        if (d.equals("minecraft:overworld")) return "OVERWORLD";
        if (d.equals("minecraft:the_nether")) return "NETHER";
        if (d.equals("minecraft:the_end")) return "THE END";
        if (d.equals("all")) return "ALL DIMENSIONS";
        return "CUSTOM DIMENSION";
    }

    private void drawFooter(GuiGraphicsExtractor g, int mx, int my) {
        int startX = listX;
        g.fill(startX, footerY - 8, detailLayout ? detailX - 12 : listRight, footerY - 7, 0x55465F91);
        actionButton(g, mx, my, startX, footerY, 27, 22, "‹", false, false);
        actionButton(g, mx, my, startX + 33, footerY, 27, 22, "›", false, false);
        String pageText = (currentPage() + 1) + " / " + pages();
        int labelX = startX + 69;
        g.text(font, pageText, labelX, footerY + 7, MUTED, false);
        if (detailLayout) {
            String total = filteredEntries().size() + " / " + entries.size() + " shown";
            int x = detailX + 10;
            g.text(font, total, x, footerY + 7, MUTED, false);
        }
        actionButton(g, mx, my, right - 78, footerY, 64, 22, "CLOSE", false, false);
    }

    private void railToggle(GuiGraphicsExtractor g, int mx, int my, int x, int y, int w,
                            String label, boolean enabled, int accent) {
        boolean hover = inside(mx, my, x, y, w, 22);
        rounded(g, x, y, x + w, y + 22, hover ? CARD_HOVER : RAIL, 6);
        g.fill(x + 8, y + 7, x + 11, y + 15, enabled ? accent : QUIET);
        g.text(font, label, x + 17, y + 7, enabled ? TEXT : MUTED, false);
        int sx = x + w - 25;
        rounded(g, sx, y + 5, sx + 17, y + 17, enabled ? VIOLET_DARK : 0xFF26354F, 6);
        rounded(g, enabled ? sx + 9 : sx + 2, y + 7,
                (enabled ? sx + 9 : sx + 2) + 7, y + 15, enabled ? CYAN : QUIET, 4);
    }

    private void smallToggle(GuiGraphicsExtractor g, int mx, int my, int x, int y, int w,
                             String label, boolean enabled, int accent) {
        boolean hover = inside(mx, my, x, y, w, 20);
        rounded(g, x, y, x + w, y + 20, hover ? CARD_HOVER : CARD, 6);
        g.fill(x + 7, y + 7, x + 10, y + 14, enabled ? accent : QUIET);
        g.text(font, label, x + 15, y + 7, enabled ? TEXT : MUTED, false);
        String state = enabled ? "ON" : "OFF";
        g.text(font, state, x + w - font.width(state) - 6, y + 7, enabled ? accent : QUIET, false);
    }

    private void actionButton(GuiGraphicsExtractor g, int mx, int my, int x, int y,
                              int w, int h, String label, boolean primary, boolean danger) {
        boolean hover = inside(mx, my, x, y, w, h);
        int fill = primary ? (hover ? 0xFF70F5FA : CYAN)
                : danger ? (hover ? 0xFF5A263F : 0xFF341F35)
                : (hover ? CARD_HOVER : CARD);
        int edge = primary ? 0xFF8DFAFF : danger ? 0xFF9A425E : EDGE;
        rounded(g, x, y, x + w, y + h, edge, Math.min(8, h / 2));
        rounded(g, x + 1, y + 1, x + w - 1, y + h - 1, fill, Math.max(2, Math.min(7, h / 2 - 1)));
        int textColor = primary ? 0xFF081A2B : danger ? DANGER : TEXT;
        int tw = font.width(label);
        g.text(font, label, x + (w - tw) / 2, y + (h - 8) / 2, textColor, primary);
    }

    private String fitText(String value, int maxWidth) {
        if (value == null) return "";
        if (font.width(value) <= maxWidth) return value;
        String clipped = value;
        while (clipped.length() > 2 && font.width(clipped + "…") > maxWidth) {
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
