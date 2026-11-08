package com.example.waypoints;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;

public final class WaypointEditScreen extends Screen {
    private static final int PANEL = 0xFF131518;
    private static final int PANEL_ALT = 0xFF0F1113;
    private static final int EDGE = 0xFF292D31;
    private static final int TEXT = 0xFFF2F4F5;
    private static final int MUTED = 0xFFA3ABB0;
    private static final int QUIET = 0xFF727B82;
    private static final int ACCENT = 0xFFB7F36B;

    private final Waypoint original;
    private final int returnPage;
    private EditBox nameBox, xBox, yBox, zBox, hexBox;
    private boolean labelEnabled = true, beamEnabled = true;
    private int railWidth = 190, contentLeft, contentRight, formWidth, colorWidth, colorX;
    private int cardTop = 140;

    private record HitTarget(int x, int y, int width, int height, Runnable action) {
        boolean contains(double px, double py) { return px >= x && px < x + width && py >= y && py < y + height; }
    }
    private final List<HitTarget> hitTargets = new ArrayList<>();

    public WaypointEditScreen(Waypoint original, int returnPage) {
        super(Component.literal(original == null ? "New Waypoint" : "Edit Waypoint"));
        this.original = original;
        this.returnPage = returnPage;
    }

    static void openNew() {
        Minecraft.getInstance().gui.setScreen(new WaypointEditScreen(null, -1));
    }

    @Override
    public void extractBackground(GuiGraphicsExtractor g, int mouseX, int mouseY, float delta) {
        g.fill(0, 0, width, height, 0xFF0B0C0E);
    }

    @Override
    protected void init() {
        clearWidgets();
        hitTargets.clear();
        contentLeft = railWidth + 35;
        contentRight = width - 35;
        int available = contentRight - contentLeft;
        colorWidth = 320;
        formWidth = available - colorWidth - 20;
        colorX = contentLeft + formWidth + 20;
        int coordW = (formWidth - 52) / 3;

        // Navigation back
        click(contentLeft, 85, 120, 20, this::onClose);

        Minecraft mc = Minecraft.getInstance();
        BlockPos p = mc.player != null ? mc.player.blockPosition() : BlockPos.ZERO;
        String initName = original != null ? original.name() : "Waypoint1";
        String initX = original != null ? String.valueOf(original.x()) : String.valueOf(p.getX());
        String initY = original != null ? String.valueOf(original.y()) : String.valueOf(p.getY());
        String initZ = original != null ? String.valueOf(original.z()) : String.valueOf(p.getZ());

        nameBox = makeBox(contentLeft + 16, cardTop + 85, formWidth - 32, 20, initName);
        xBox = makeBox(contentLeft + 16, cardTop + 175, coordW - 16, 20, initX);
        yBox = makeBox(contentLeft + 32 + coordW, cardTop + 175, coordW - 16, 20, initY);
        zBox = makeBox(contentLeft + 48 + coordW * 2, cardTop + 175, coordW - 16, 20, initZ);
        hexBox = makeBox(colorX + 60, cardTop + 245, colorWidth - 80, 20, "#B7F36B");

        // Toggles & buttons
        click(contentLeft + formWidth - 50, cardTop + 360, 32, 20, () -> labelEnabled = !labelEnabled);
        click(contentLeft + formWidth - 50, cardTop + 410, 32, 20, () -> beamEnabled = !beamEnabled);

        int actionY = height - 60;
        click(contentRight - 120, actionY, 120, 38, this::save);
        click(contentRight - 240, actionY, 110, 38, this::onClose);
    }

    private void click(int x, int y, int w, int h, Runnable action) {
        hitTargets.add(new HitTarget(x, y, w, h, action));
    }

    private EditBox makeBox(int x, int y, int w, int h, String val) {
        EditBox box = new EditBox(font, x, y, w, h, Component.literal(""));
        box.setMaxLength(64);
        box.setValue(val);
        box.setBordered(false);
        box.setTextColor(0x00000000);
        addRenderableWidget(box);
        return box;
    }

    private void save() {
        String name = nameBox.getValue().trim();
        if (name.isEmpty()) return;
        int x = Integer.parseInt(xBox.getValue().trim());
        int y = Integer.parseInt(yBox.getValue().trim());
        int z = Integer.parseInt(zBox.getValue().trim());
        WaypointsMod.upsert(original, new Waypoint(name, x, y, z, 0xB7F36B, "minecraft:overworld", 0L, WaypointsMod.currentWorldId(), "W", labelEnabled, beamEnabled));
        WaypointScreen.open(returnPage >= 0 ? returnPage : 0);
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        if (event.button() == 1) {
            for (var t : hitTargets) {
                if (t.contains(event.x(), event.y())) { t.action().run(); return true; }
            }
        }
        return super.mouseClicked(event, doubleClick);
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor g, int mx, int my, float delta) {
        drawSidebar(g, mx, my);
        g.fill(railWidth, 0, width, 48, 0xFF0B0C0E);
        g.fill(railWidth, 47, width, 48, EDGE);
        WaypointUiText.draw(g, font, "Survival Realm  /  Waypoints", contentLeft, 17, MUTED, false);
        
        if (original != null) {
            rounded(g, contentRight - 130, 14, contentRight, 38, 0xFF151B14, 6);
            WaypointUiText.draw(g, font, "Unsaved changes", contentRight - 114, 19, ACCENT, false);
        }

        WaypointUiText.draw(g, font, "<  All waypoints", contentLeft, 85, MUTED, false);
        WaypointUiText.drawTitle(g, font, original == null ? "Mark somewhere new." : "Edit waypoint", contentLeft, 115, TEXT);
        WaypointUiText.draw(g, font, "Fine-tune the details. Keep your favorite place within reach.", contentLeft, 145, MUTED, false);

        // Location card
        rounded(g, contentLeft, cardTop, contentLeft + formWidth, cardTop + 300, PANEL, 10);
        border(g, contentLeft, cardTop, contentLeft + formWidth, cardTop + 300, EDGE);
        WaypointUiText.draw(g, font, "Location details", contentLeft + 18, cardTop + 18, TEXT, false);
        WaypointUiText.draw(g, font, "Waypoint name", contentLeft + 16, cardTop + 60, TEXT, false);
        drawBox(g, contentLeft + 16, cardTop + 80, formWidth - 32, 32);

        int coordW = (formWidth - 52) / 3;
        WaypointUiText.draw(g, font, "X", contentLeft + 16, cardTop + 150, TEXT, false);
        WaypointUiText.draw(g, font, "Y", contentLeft + 32 + coordW, cardTop + 150, TEXT, false);
        WaypointUiText.draw(g, font, "Z", contentLeft + 48 + coordW * 2, cardTop + 150, TEXT, false);
        drawBox(g, contentLeft + 16, cardTop + 170, coordW, 32);
        drawBox(g, contentLeft + 32 + coordW, cardTop + 170, coordW, 32);
        drawBox(g, contentLeft + 48 + coordW * 2, cardTop + 170, coordW, 32);

        // Color workstation card
        rounded(g, colorX, cardTop, colorX + colorWidth, cardTop + 450, PANEL, 10);
        border(g, colorX, cardTop, colorX + colorWidth, cardTop + 450, EDGE);
        WaypointUiText.draw(g, font, "Waypoint color", colorX + 16, cardTop + 18, TEXT, false);
        WaypointUiText.draw(g, font, "Lime", colorX + colorWidth - 60, cardTop + 18, MUTED, false);
        rounded(g, colorX + 16, cardTop + 50, colorX + colorWidth - 16, cardTop + 200, 0xFF88CC44, 8);
        drawBox(g, colorX + 16, cardTop + 238, colorWidth - 32, 32);
        WaypointUiText.draw(g, font, "HEX", colorX + 28, cardTop + 248, QUIET, false);

        // Action buttons
        int actionY = height - 60;
        actionButton(g, mx, my, contentRight - 120, actionY, 120, 38, "Save changes", true);
        actionButton(g, mx, my, contentRight - 240, actionY, 110, 38, "Discard changes", false);

        super.extractRenderState(g, mx, my, delta);
        WaypointUiText.drawInput(g, font, nameBox, contentLeft + 24, cardTop + 89, formWidth - 48);
        WaypointUiText.drawInput(g, font, xBox, contentLeft + 24, cardTop + 179, coordW - 24);
        WaypointUiText.drawInput(g, font, yBox, contentLeft + 40 + coordW, cardTop + 179, coordW - 24);
        WaypointUiText.drawInput(g, font, zBox, contentLeft + 56 + coordW * 2, cardTop + 179, coordW - 24);
        WaypointUiText.drawInput(g, font, hexBox, colorX + 68, cardTop + 252, colorWidth - 96);
    }

    private void drawSidebar(GuiGraphicsExtractor g, int mx, int my) {
        g.fill(0, 0, railWidth, height, 0xFF101113);
        g.fill(railWidth - 1, 0, railWidth, height, EDGE);
        rounded(g, 20, 15, 47, 42, ACCENT, 7);
        WaypointUiText.draw(g, font, "Waypoint", 56, 19, TEXT, false);
        WaypointUiText.draw(g, font, "M A N A G E R", 56, 31, MUTED, false);

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

    private void drawBox(GuiGraphicsExtractor g, int x, int y, int w, int h) {
        rounded(g, x, y, x + w, y + h, 0xFF08090A, 6);
        border(g, x, y, x + w, y + h, EDGE);
    }

    private void actionButton(GuiGraphicsExtractor g, int mx, int my, int x, int y, int w, int h, String label, boolean primary) {
        boolean hover = inside(mx, my, x, y, w, h);
        rounded(g, x, y, x + w, y + h, primary ? (hover ? 0xFFBDE39A : ACCENT) : (hover ? 0xFF242B2F : PANEL), 7);
        if (!primary) border(g, x, y, x + w, y + h, EDGE);
        WaypointUiText.draw(g, font, label, x + (w - WaypointUiText.width(font, label)) / 2, y + (h - 8) / 2, primary ? 0xFF101410 : TEXT, false);
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
