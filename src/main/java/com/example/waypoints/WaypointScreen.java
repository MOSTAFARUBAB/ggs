package com.example.waypoints;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.List;

/**
 * Main waypoint browser. The visual layer is intentionally made from a small,
 * fixed set of fills and text calls: no per-frame texture creation, animation
 * timers, or per-row widget trees. Invisible vanilla buttons only provide the
 * input hit targets; the theme is rendered once by this screen.
 */
public final class WaypointScreen extends Screen {
    private static final int PER_PAGE = 5;
    private static final int ROW_H = 27;

    // Northstar / deep-ocean theme shared with the edit screen.
    private static final int PANEL = 0xF20A1120;
    private static final int PANEL_EDGE = 0xFF26364B;
    private static final int CARD = 0xFF121E30;
    private static final int CARD_HOVER = 0xFF1B2B42;
    private static final int CARD_BORDER = 0xFF2A4058;
    private static final int TEXT = 0xFFF0F6FF;
    private static final int MUTED = 0xFF93A8C1;
    private static final int ACCENT = 0xFF62E6C4;
    private static final int ACCENT_DARK = 0xFF153A3B;
    private static final int VIOLET = 0xFF9A8CFF;
    private static final int DANGER = 0xFFFF718A;

    private static final String[] DIM_LABELS = {"Overworld", "Nether", "End", "All"};
    private static final int[] DIM_W = {60, 46, 34, 82};

    private final int page;
    private int left;
    private int right;
    private int cx;
    private int oy;
    private int selectedTabCached = -1;
    private List<Waypoint> entries = List.of();

    public WaypointScreen(int page) {
        super(Component.literal("Waypoint Station"));
        this.page = page;
    }

    static void openMenu() { open(0); }

    static void open(int page) {
        Minecraft.getInstance().gui.setScreen(new WaypointScreen(page));
    }

    private int pages() {
        return Math.max(1, (entries.size() + PER_PAGE - 1) / PER_PAGE);
    }

    private int currentPage() { return Math.max(0, Math.min(page, pages() - 1)); }

    private int selectedTab() {
        int mode = WaypointsMod.menuDimensionMode;
        if (mode >= 1 && mode <= 4) return mode - 1;
        return switch (WaypointsMod.currentDimension()) {
            case "minecraft:overworld" -> 0;
            case "minecraft:the_nether" -> 1;
            case "minecraft:the_end" -> 2;
            default -> -1;
        };
    }

    public void extractBlurredBackground(GuiGraphicsExtractor g) { }

    public void extractBackground(GuiGraphicsExtractor g, int mouseX, int mouseY, float delta) {
        g.fill(0, 0, width, height, 0xB8070D17);
    }

    @Override
    protected void init() {
        int panelWidth = Math.min(372, Math.max(272, width - 16));
        left = (width - panelWidth) / 2;
        right = left + panelWidth;
        cx = (left + right) / 2;
        oy = Math.max(4, (height - 300) / 2);

        // Resolve the active scope once when the screen opens. The old UI rebuilt
        // this stream and reflected over the server/save identity on every frame.
        entries = WaypointsMod.menuWaypoints();
        selectedTabCached = selectedTab();
        int pageNow = currentPage();
        int start = pageNow * PER_PAGE;
        int end = Math.min(entries.size(), start + PER_PAGE);

        hit(right - 34, oy + 4, 22, 20, this::onClose);
        hit(left + 16, oy + 42, 136, 24,
                () -> Minecraft.getInstance().gui.setScreen(new WaypointEditScreen(null, pageNow)));

        int dx = left + 16;
        for (int i = 0; i < DIM_LABELS.length; i++) {
            final int mode = i + 1;
            final int tabX = dx;
            hit(tabX, oy + 73, DIM_W[i], 20, () -> {
                WaypointsMod.menuDimensionMode = mode;
                open(0);
            });
            dx += DIM_W[i] + 5;
        }

        // Toggle values in place. Do not reopen/rebuild the screen on every click.
        hit(left + 16, oy + 99, 108, 20,
                () -> WaypointsMod.showWaypointBeams = !WaypointsMod.showWaypointBeams);
        hit(left + 132, oy + 99, 108, 20,
                () -> WaypointsMod.showWaypointLabels = !WaypointsMod.showWaypointLabels);

        for (int i = start; i < end; i++) {
            final Waypoint waypoint = entries.get(i);
            int y = oy + 126 + (i - start) * ROW_H;
            hit(right - 96, y + 4, 40, 19,
                    () -> Minecraft.getInstance().gui.setScreen(new WaypointEditScreen(waypoint, pageNow)));
            hit(right - 50, y + 4, 34, 19, () -> {
                WaypointsMod.remove(waypoint);
                open(pageNow);
            });
        }

        hit(left + 16, oy + 267, 34, 20, () -> { if (pageNow > 0) open(pageNow - 1); });
        hit(left + 56, oy + 267, 34, 20, () -> { if (pageNow < pages() - 1) open(pageNow + 1); });
        hit(right - 70, oy + 267, 54, 20, this::onClose);
    }

    private void hit(int x, int y, int w, int h, Runnable action) {
        addRenderableWidget(Button.builder(Component.literal(""), button -> action.run())
                .bounds(x, y, w, h).build());
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float delta) {
        super.extractRenderState(g, mouseX, mouseY, delta);

        // Cached until the screen is reopened after an edit, delete, or filter change.
        List<Waypoint> all = entries;
        int pageNow = currentPage();
        int start = pageNow * PER_PAGE;
        int end = Math.min(all.size(), start + PER_PAGE);

        // One quiet outer frame and one panel. This is deliberately static at idle.
        rounded(g, left - 1, oy + 15, right + 1, oy + 296, PANEL_EDGE, 12);
        rounded(g, left, oy + 16, right, oy + 295, PANEL, 11);
        g.fill(left + 16, oy + 36, right - 16, oy + 37, 0x552D4560);

        // Header.
        g.text(this.font, "FIELD NOTES  /  SIMPLE WAYPOINTS", left + 17, oy + 4, MUTED, false);
        g.text(this.font, "WAYPOINT STATION", left + 17, oy + 17, TEXT, true);
        g.text(this.font, "J  ·  NAVIGATE", right - 105, oy + 21, 0xFF7189A5, false);
        button(g, mouseX, mouseY, right - 34, oy + 4, 22, 20, "×", false, true);

        // Primary action.
        button(g, mouseX, mouseY, left + 16, oy + 42, 136, 24, "+  ADD WAYPOINT", true, false);
        String countText = all.size() + (all.size() == 1 ? "  saved point" : "  saved points");
        g.text(this.font, countText, right - 16 - this.font.width(countText), oy + 50, MUTED, false);

        // Dimension selector.
        g.text(this.font, "SHOWING", left + 17, oy + 66, 0xFF7189A5, false);
        int selected = selectedTabCached;
        int dx = left + 16;
        for (int i = 0; i < DIM_LABELS.length; i++) {
            boolean isSelected = selected == i;
            boolean hover = inside(mouseX, mouseY, dx, oy + 73, DIM_W[i], 20);
            rounded(g, dx, oy + 73, dx + DIM_W[i], oy + 93,
                    isSelected ? ACCENT_DARK : (hover ? CARD_HOVER : CARD), 6);
            if (isSelected) g.fill(dx + 8, oy + 78, dx + 11, oy + 88, ACCENT);
            int tw = this.font.width(DIM_LABELS[i]);
            g.text(this.font, DIM_LABELS[i], dx + (DIM_W[i] - tw) / 2 + (isSelected ? 2 : 0),
                    oy + 79, isSelected ? TEXT : MUTED, false);
            dx += DIM_W[i] + 5;
        }

        // Live render switches. They update in place; no screen reconstruction.
        toggle(g, mouseX, mouseY, left + 16, oy + 99, 108,
                "BEAM", WaypointsMod.showWaypointBeams, ACCENT);
        toggle(g, mouseX, mouseY, left + 132, oy + 99, 108,
                "NAME TAGS", WaypointsMod.showWaypointLabels, VIOLET);

        // Waypoint cards.
        if (all.isEmpty()) {
            rounded(g, left + 16, oy + 126, right - 16, oy + 253, CARD, 9);
            g.text(this.font, "Nothing marked here yet.", left + 29, oy + 167, TEXT, true);
            g.text(this.font, "Add a waypoint to place a beacon in this world.",
                    left + 29, oy + 183, MUTED, false);
        } else {
            for (int i = start; i < end; i++) {
                Waypoint waypoint = all.get(i);
                int y = oy + 126 + (i - start) * ROW_H;
                boolean rowHover = inside(mouseX, mouseY, left + 16, y, right - left - 32, 24);
                rounded(g, left + 16, y, right - 16, y + 24,
                        rowHover ? CARD_HOVER : CARD, 7);
                rounded(g, left + 22, y + 7, left + 30, y + 17,
                        0xFF000000 | WaypointsMod.resolveColor(waypoint), 3);
                g.text(this.font, waypoint.name(), left + 38, y + 3, TEXT, false);
                String coords = waypoint.x() + "  /  " + waypoint.y() + "  /  " + waypoint.z();
                g.text(this.font, coords, left + 38, y + 13, MUTED, false);

                button(g, mouseX, mouseY, right - 96, y + 4, 40, 19, "EDIT", false, false);
                button(g, mouseX, mouseY, right - 50, y + 4, 34, 19, "×", false, true);
            }
        }

        // Footer / paging.
        button(g, mouseX, mouseY, left + 16, oy + 267, 34, 20, "‹", false, false);
        button(g, mouseX, mouseY, left + 56, oy + 267, 34, 20, "›", false, false);
        String pageLabel = "PAGE  " + (pageNow + 1) + "  /  " + pages();
        g.text(this.font, pageLabel, cx - this.font.width(pageLabel) / 2, oy + 274, MUTED, false);
        button(g, mouseX, mouseY, right - 70, oy + 267, 54, 20, "DONE", false, false);
    }

    private void toggle(GuiGraphicsExtractor g, int mx, int my, int x, int y,
                        int w, String label, boolean on, int accent) {
        boolean hover = inside(mx, my, x, y, w, 20);
        rounded(g, x, y, x + w, y + 20, on ? 0xFF1B3440 : (hover ? CARD_HOVER : CARD), 6);
        g.fill(x + 8, y + 8, x + 12, y + 12, on ? accent : 0xFF53647A);
        g.text(this.font, label, x + 18, y + 6, on ? TEXT : MUTED, true);
        String state = on ? "ON" : "OFF";
        g.text(this.font, state, x + w - 26, y + 6, on ? accent : MUTED, false);
    }

    private void button(GuiGraphicsExtractor g, int mx, int my, int x, int y, int w, int h,
                        String label, boolean primary, boolean danger) {
        boolean hover = inside(mx, my, x, y, w, h);
        int bg = primary ? (hover ? 0xFF8AF0D8 : ACCENT)
                : danger ? (hover ? 0xFF633044 : 0xFF392334)
                : (hover ? CARD_HOVER : CARD);
        int fg = primary ? 0xFF07151A : danger ? DANGER : TEXT;
        rounded(g, x, y, x + w, y + h, bg, Math.min(7, h / 2));
        if (!primary && !danger) {
            g.fill(x + 1, y + 1, x + w - 1, y + 2, 0x332C4965);
        }
        int tw = this.font.width(label);
        g.text(this.font, label, x + (w - tw) / 2, y + (h - 8) / 2, fg, primary);
    }

    /** Rounded panel composed of a body and a small, bounded number of corner rows. */
    private static void rounded(GuiGraphicsExtractor g, int x1, int y1, int x2, int y2,
                                int color, int requestedRadius) {
        int width = x2 - x1;
        int height = y2 - y1;
        if (width <= 0 || height <= 0) return;
        int radius = Math.min(requestedRadius, Math.min(width / 2, height / 2));
        if (radius <= 0) { g.fill(x1, y1, x2, y2, color); return; }
        g.fill(x1 + radius, y1, x2 - radius, y2, color);
        g.fill(x1, y1 + radius, x2, y2 - radius, color);
        for (int row = 0; row < radius; row++) {
            double dy = radius - row - 0.5;
            int inset = (int) Math.ceil(radius - Math.sqrt(Math.max(0.0, radius * radius - dy * dy)));
            g.fill(x1 + inset, y1 + row, x2 - inset, y1 + row + 1, color);
            g.fill(x1 + inset, y2 - row - 1, x2 - inset, y2 - row, color);
        }
    }

    private static boolean inside(int mx, int my, int x, int y, int w, int h) {
        return mx >= x && mx < x + w && my >= y && my < y + h;
    }
}
