package com.example.waypoints;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.renderer.texture.DynamicTexture;
import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.resources.Identifier;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Create / edit one waypoint in the Field Atlas editor: a wide location form above
 * a color workstation, live preview, dimension scope, and expiry controls.
 *
 * How it works: normal Minecraft buttons and text boxes sit invisibly in the right places and handle all
 * clicking and typing; everything you actually SEE is drawn on top of them in extractRenderState.
 */
public class WaypointEditScreen extends Screen {
    // ---- theme ----
    private static final int PANEL = 0xF0080808;
    private static final int CARD = 0xFF121212;
    private static final int CARD_HOVER = 0xFF202020;
    private static final int CARD_BORDER = 0xFF383838;
    private static final int TEXT = 0xFFF2F2F2;
    private static final int MUTED = 0xFFBDBDBD;
    private static final int QUIET = 0xFF858585;
    private static final int CYAN = 0xFFE0E0E0;
    private static final int CYAN_HOVER = 0xFFFFFFFF;
    private static final int CYAN_DARK = 0xFF303030;
    private static final int ACCENT = 0xFFB7F36B;

    // ---- color picker geometry ----
    // GUI-space dimensions stay compact; the backing texture is 2x resolution
    // so the gradient stays smooth on scaled/high-DPI displays.
    private static final int PICKER_WIDTH = 198;
    private static final int PICKER_HEIGHT = 100;
    private static final int PICKER_TEXTURE_WIDTH = 256;
    private static final int PICKER_TEXTURE_HEIGHT = 128;
    private static final Identifier PICKER_TEXTURE_ID = Identifier.fromNamespaceAndPath(
            "simplewaypoints", "gui/waypoint_color_picker");
    private static DynamicTexture pickerTexture;
    private static float pickerTextureHue = Float.NaN;
    private static long lastPickerUploadNanos = 0L;

    // The hue strip is static, so generate it once and draw it as one texture.
    private static final int HUE_DISPLAY_HEIGHT = 100;
    private static final int HUE_TEXTURE_HEIGHT = 256;
    private static final int HUE_TEXTURE_WIDTH = 8;
    private static final Identifier HUE_TEXTURE_ID = Identifier.fromNamespaceAndPath(
            "simplewaypoints", "gui/waypoint_hue_strip");
    private static DynamicTexture hueTexture;

    // ---- dimensions ----
    private static final String[] DIM_LABELS = {"Overworld", "Nether", "End", "All dimensions"};
    private static final String[] DIM_IDS = {"minecraft:overworld", "minecraft:the_nether", "minecraft:the_end", "all"};
    private static final int[] DIM_W = {70, 56, 44, 118};

    /** Recently saved colors (kept until you quit the game). */
    private static final List<Integer> RECENT = new ArrayList<>();

    private final Waypoint original; // null = creating a new one
    private final int returnPage;

    // starting values (kept so the screen survives being re-initialised, e.g. on window resize)
    private final String initName;
    private final String initX;
    private final String initY;
    private final String initZ;
    private final String initTimer;
    private final String keepDimension;

    private EditBox nameBox;
    private EditBox xBox;
    private EditBox yBox;
    private EditBox zBox;
    private EditBox hexBox;
    private EditBox timerBox;

    private float hue = 0f;
    private float sat = 1f;
    private float val = 1f;
    private boolean draggingSvPicker = false;
    private boolean draggingHuePicker = false;
    // Track the selection while the cursor moves; GUI-scaled absolute coordinates
    // are used so high-DPI window scaling cannot cause drag drift.
    private double pickerPointerX = 0.0;
    private double pickerPointerY = 0.0;
    private boolean chroma = false;
    private int dimMode;            // 0 Overworld, 1 Nether, 2 End, 3 All, -1 = keep the original (modded) dimension
    private String lastHex = "";
    private String error = "";

    private int cx;
    private int oy;
    private int railWidth;
    private int contentLeft;
    private int contentRight;
    private int formWidth;
    private int colorX;
    private int colorWidth;
    private int coordinateWidth;
    private boolean labelEnabled = true;
    private boolean beamEnabled = true;

    // Full-page editor geometry: location form on the left, color workstation on the right.
    private int formLeft() { return contentLeft + 18; }
    private int pickerX() { return colorX + 16; }
    private int pickerY() { return oy + 40; }
    private int hueX() { return pickerX() + PICKER_WIDTH + 8; }
    private int previewX() { return colorX + 16; }

    public WaypointEditScreen(Waypoint original, int returnPage) {
        super(Component.literal(original == null ? "New Waypoint" : "Edit Waypoint"));
        this.original = original;
        this.returnPage = returnPage;

        if (original != null) {
            labelEnabled = original.isLabelEnabled();
            beamEnabled = original.isBeamEnabled();
            initName = original.name();
            initX = String.valueOf(original.x());
            initY = String.valueOf(original.y());
            initZ = String.valueOf(original.z());
            initTimer = original.expiresAt() > 0
                    ? String.valueOf(Math.max(1, (original.expiresAt() - System.currentTimeMillis()) / 1000))
                    : "0";
            keepDimension = original.dimension();
            if (original.color() == WaypointsMod.CHROMA) {
                chroma = true;
            } else {
                setFromRgb(original.color() & 0xFFFFFF);
            }
        } else {
            Minecraft mc = Minecraft.getInstance();
            BlockPos p = mc.player != null ? mc.player.blockPosition() : BlockPos.ZERO;
            initName = WaypointsMod.nextWaypointName();
            initX = String.valueOf(p.getX());
            initY = String.valueOf(p.getY());
            initZ = String.valueOf(p.getZ());
            initTimer = "0";
            keepDimension = WaypointsMod.currentDimension();
        }

        String normalized = WaypointsMod.normalizeDimension(keepDimension);
        dimMode = -1;
        for (int i = 0; i < DIM_IDS.length; i++) {
            if (DIM_IDS[i].equals(normalized)) dimMode = i;
        }
    }

    /** Opened by the B key: a blank "New Waypoint" menu, already filled with your position and dimension. */
    static void openNew() {
        Minecraft.getInstance().gui.setScreen(new WaypointEditScreen(null, -1));
    }

    /** Plain dark overlay instead of Minecraft's blurred world, so the menu looks sharp and clear. */
    public void extractBlurredBackground(GuiGraphicsExtractor g) {
    }

    public void extractBackground(GuiGraphicsExtractor g, int mouseX, int mouseY, float delta) {
        g.fill(0, 0, this.width, this.height, 0xE6000000);
    }

    // ------------------------------------------------------------------ layout / widgets
    @Override
    protected void init() {
        clearWidgets();
        railWidth = width >= 800 ? 150 : 0;
        contentLeft = railWidth > 0 ? railWidth + 24 : 20;
        contentRight = width - 24;
        int available = Math.max(500, contentRight - contentLeft);
        colorWidth = Math.max(250, Math.min(286, available / 3));
        formWidth = Math.max(300, available - colorWidth - 16);
        colorX = contentLeft + formWidth + 16;
        coordinateWidth = Math.max(72, (formWidth - 52) / 3);
        cx = contentRight;
        oy = 94;
        int left = formLeft();
        int preview = previewX();
        int pickerTop = pickerY();
        int nameY = oy + 44;
        int coordsY = oy + 94;
        int dimY = oy + 145;
        int expiryY = oy + 190;
        int nameWidth = formWidth - 36;
        int coordGap = 8;

        if (railWidth > 0) {
            click(railWidth + 10, 140, railWidth - 20, 32,
                    () -> Minecraft.getInstance().gui.setScreen(new WaypointScreen(returnPage < 0 ? 0 : returnPage)));
            click(railWidth + 10, 179, railWidth - 20, 32,
                    () -> Minecraft.getInstance().gui.setScreen(new WaypointSettingsScreen()));
        }
        // Visible breadcrumb is the actual Back action (do not place an invisible
        // hit target over the subtitle or title).
        click(contentLeft, 41, 104, 16, this::back);

        nameBox = makeBox(nameBox, left, nameY, nameWidth, 23, initName, 32, "Waypoint name");
        xBox = makeBox(xBox, left, coordsY, coordinateWidth, 24, initX, 9, "X");
        yBox = makeBox(yBox, left + coordinateWidth + coordGap, coordsY, coordinateWidth, 24, initY, 9, "Y");
        zBox = makeBox(zBox, left + (coordinateWidth + coordGap) * 2, coordsY, coordinateWidth, 24, initZ, 9, "Z");

        int dx = left;
        int[] dimWidths = {70, 56, 44, Math.max(105, Math.min(118, nameWidth - 190))};
        for (int i = 0; i < DIM_LABELS.length; i++) {
            final int mode = i;
            click(dx, dimY, dimWidths[i], 22, () -> dimMode = mode);
            dx += dimWidths[i] + 5;
        }

        // Color picker: full-resolution saturation/value field plus hue strip.
        click(preview, pickerTop + 146, colorWidth - 32, 22, () -> chroma = !chroma);
        click(colorX + colorWidth - 58, pickerTop + 174, 42, 18, this::resetColor);
        for (int i = 0; i < RECENT.size(); i++) {
            final int idx = i;
            click(preview + 16 + i * 22, pickerTop + 190, 18, 18, () -> pickRecent(idx));
        }
        hexBox = makeBox(hexBox, preview + 42, pickerTop + 118, Math.max(76, colorWidth - 132), 22,
                String.format(Locale.ROOT, "%06X", currentRgb()), 7, "HEX");
        lastHex = hexBox.getValue();
        timerBox = makeBox(timerBox, left + 72, expiryY - 3, 82, 22, initTimer, 9, "Seconds");

        // Per-waypoint visibility, the same two switches shown in the reference editor.
        int visibilityY = oy + 252;
        // Align hit targets exactly with the two rendered toggle switches.
        click(left + formWidth - 62, visibilityY + 29, 26, 15, () -> labelEnabled = !labelEnabled);
        click(left + formWidth - 62, visibilityY + 61, 26, 15, () -> beamEnabled = !beamEnabled);

        refreshPickerTexture();
        ensureHueTexture();
        int actionY = height - 30;
        click(contentRight - 220, actionY, 94, 24, this::back);
        click(contentRight - 116, actionY, 116, 24, this::save);
        if (original != null) {
            click(contentLeft, actionY, 112, 24, () -> {
                WaypointsMod.remove(original);
                back();
            });
        }
    }

    private void click(int x, int y, int w, int h, Runnable action) {
        this.addRenderableWidget(Button.builder(Component.literal(""), btn -> action.run())
                .bounds(x, y, w, h).build());
    }

    private EditBox makeBox(EditBox old, int x, int y, int w, int h, String initial, int maxLength, String label) {
        EditBox box = new EditBox(this.font, x, y, w, h, Component.literal(label));
        box.setMaxLength(maxLength);
        box.setValue(old != null ? old.getValue() : initial);
        this.addRenderableWidget(box);
        return box;
    }

    private void back() {
        if (returnPage < 0) {
            this.onClose(); // opened with B: go straight back to the game
        } else {
            WaypointScreen.open(returnPage);
        }
    }

    // SDL3 left mouse button is 1 in Minecraft 26.3.
    // Keep a drag capture so the picker follows the cursor continuously, even
    // after the pointer moves beyond the edge of the color square or hue strip.
    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        if (event.button() == 1) {
            double mouseX = event.x();
            double mouseY = event.y();

            int gx = pickerX();
            int gy = pickerY();
            int pickerW = PICKER_WIDTH;
            int pickerH = PICKER_HEIGHT;

            if (mouseX >= gx && mouseX < gx + pickerW
                    && mouseY >= gy && mouseY < gy + pickerH) {
                draggingSvPicker = true;
                draggingHuePicker = false;
                this.setDragging(true);
                pickerPointerX = mouseX;
                pickerPointerY = mouseY;
                updateSvPicker(pickerPointerX, pickerPointerY);
                return true;
            }

            int hueX = hueX();
            int hueY = pickerY();
            int hueH = HUE_DISPLAY_HEIGHT;
            if (mouseX >= hueX && mouseX < hueX + 10
                    && mouseY >= hueY && mouseY < hueY + hueH) {
                draggingHuePicker = true;
                draggingSvPicker = false;
                this.setDragging(true);
                pickerPointerX = mouseX;
                pickerPointerY = mouseY;
                updateHuePicker(pickerPointerY);
                return true;
            }
        }

        return super.mouseClicked(event, doubleClick);
    }

    @Override
    public boolean mouseDragged(MouseButtonEvent event, double dragX, double dragY) {
        if (draggingSvPicker || draggingHuePicker) {
            // Prefer the live cursor position maintained by Minecraft's SDL-backed
            // MouseHandler. Its scaled coordinates are the same coordinate space
            // used by Screen widgets, including non-default GUI scales.
            updatePickerFromLiveMouse();
            return true;
        }
        return super.mouseDragged(event, dragX, dragY);
    }

    @Override
    public void mouseMoved(double mouseX, double mouseY) {
        if (draggingSvPicker || draggingHuePicker) {
            // Keep event-driven updates for immediate response, but use the exact
            // same coordinate conversion as vanilla GUI elements.
            updatePickerFromLiveMouse();
        }
        super.mouseMoved(mouseX, mouseY);
    }

    /** Read the current pointer directly from Minecraft instead of relying on a
     * possibly coalesced MouseButtonEvent. This keeps the picker marker attached
     * to the cursor while dragging quickly or when Windows sends sparse events.
     */
    private void updatePickerFromLiveMouse() {
        Minecraft mc = Minecraft.getInstance();
        double mouseX = mc.mouseHandler.getScaledXPos(mc.getWindow());
        double mouseY = mc.mouseHandler.getScaledYPos(mc.getWindow());
        pickerPointerX = mouseX;
        pickerPointerY = mouseY;
        if (draggingSvPicker) updateSvPicker(mouseX, mouseY);
        else if (draggingHuePicker) updateHuePicker(mouseY);
    }

    @Override
    public boolean mouseReleased(MouseButtonEvent event) {
        if (draggingSvPicker || draggingHuePicker) {
            // Apply the exact release coordinate before ending the drag. The
            // picker capture itself is initiated only by a left-button press.
            if (draggingSvPicker) updateSvPicker(event.x(), event.y());
            else updateHuePicker(event.y());
            draggingSvPicker = false;
            draggingHuePicker = false;
            this.setDragging(false);
            return true;
        }
        return super.mouseReleased(event);
    }

    private void updateSvPicker(double mouseX, double mouseY) {
        int gx = pickerX();
        int gy = pickerY();

        sat = clamp01((float) ((mouseX - gx) / (PICKER_WIDTH - 1.0)));
        val = 1.0f - clamp01((float) ((mouseY - gy) / (PICKER_HEIGHT - 1.0)));
        chroma = false;
        syncHex();
    }

    private void updateHuePicker(double mouseY) {
        int gy = pickerY();
        int hueH = HUE_DISPLAY_HEIGHT;

        hue = clamp01((float) ((mouseY - gy) / (hueH - 1.0)));
        chroma = false;
        syncHex();
    }

    private static float clamp01(float value) {
        return Math.max(0.0f, Math.min(1.0f, value));
    }

    /**
     * Builds a 2x-resolution saturation/value square once for the current hue and uploads
     * it only when the hue changes. The old implementation queued 8,192 fill calls every frame,
     * which made the edit screen noticeably laggy.
     */
    private void refreshPickerTexture() {
        if (pickerTexture == null) {
            pickerTexture = new DynamicTexture("simple-waypoints-color-picker",
                    PICKER_TEXTURE_WIDTH, PICKER_TEXTURE_HEIGHT, true);
            Minecraft.getInstance().getTextureManager().register(PICKER_TEXTURE_ID, pickerTexture);
            pickerTextureHue = Float.NaN;
        }

        if (!Float.isNaN(pickerTextureHue) && Math.abs(pickerTextureHue - hue) < 0.0001f) {
            return;
        }

        // When dragging the hue strip, cap texture uploads at roughly 60 per second.
        // The selection hex/preview still follows the cursor every input event.
        long now = System.nanoTime();
        if (!Float.isNaN(pickerTextureHue)
                && now - lastPickerUploadNanos < 16_000_000L) {
            return;
        }

        NativeImage pixels = pickerTexture.getPixels();
        for (int y = 0; y < PICKER_TEXTURE_HEIGHT; y++) {
            float brightness = 1.0f - y / (float) (PICKER_TEXTURE_HEIGHT - 1);
            for (int x = 0; x < PICKER_TEXTURE_WIDTH; x++) {
                float saturation = x / (float) (PICKER_TEXTURE_WIDTH - 1);
                pixels.setPixel(x, y, 0xFF000000 | hsvToRgb(hue, saturation, brightness));
            }
        }
        pickerTexture.upload();
        pickerTextureHue = hue;
        lastPickerUploadNanos = now;
    }

    /** Creates the vertical hue spectrum once and reuses it for every menu frame. */
    private static void ensureHueTexture() {
        if (hueTexture != null) return;

        hueTexture = new DynamicTexture("simple-waypoints-hue-strip",
                HUE_TEXTURE_WIDTH, HUE_TEXTURE_HEIGHT, true);
        Minecraft.getInstance().getTextureManager().register(HUE_TEXTURE_ID, hueTexture);

        NativeImage pixels = hueTexture.getPixels();
        for (int y = 0; y < HUE_TEXTURE_HEIGHT; y++) {
            int rgb = 0xFF000000 | hsvToRgb(
                    y / (float) (HUE_TEXTURE_HEIGHT - 1), 1.0f, 1.0f);
            for (int x = 0; x < HUE_TEXTURE_WIDTH; x++) {
                pixels.setPixel(x, y, rgb);
            }
        }
        hueTexture.upload();
    }

    // ------------------------------------------------------------------ color logic

    private void pickRecent(int idx) {
        if (idx < 0 || idx >= RECENT.size()) return;
        setFromRgb(RECENT.get(idx));
        chroma = false;
        syncHex();
    }

    private void resetColor() {
        hue = 0f;
        sat = 1f;
        val = 1f;
        chroma = false;
        syncHex();
    }

    private void syncHex() {
        if (hexBox == null) return;
        String hex = String.format(Locale.ROOT, "%06X", currentRgb());
        if (!hex.equals(lastHex)) {
            hexBox.setValue(hex);
            lastHex = hex;
        }
    }

    private void applyHex(String text) {
        String t = text.trim();
        if (t.startsWith("#")) t = t.substring(1);
        if (t.length() != 6) return;
        try {
            setFromRgb(Integer.parseInt(t, 16));
            chroma = false;
        } catch (NumberFormatException ignored) {
        }
    }

    private int currentRgb() {
        return hsvToRgb(hue, sat, val);
    }

    private void setFromRgb(int rgb) {
        float r = ((rgb >> 16) & 0xFF) / 255f;
        float g = ((rgb >> 8) & 0xFF) / 255f;
        float b = (rgb & 0xFF) / 255f;
        float max = Math.max(r, Math.max(g, b));
        float min = Math.min(r, Math.min(g, b));
        float d = max - min;
        float h;
        if (d == 0f) h = 0f;
        else if (max == r) h = (((g - b) / d) % 6f) / 6f;
        else if (max == g) h = (((b - r) / d) + 2f) / 6f;
        else h = (((r - g) / d) + 4f) / 6f;
        if (h < 0f) h += 1f;
        hue = h;
        sat = max == 0f ? 0f : d / max;
        val = max;
    }

    private static int hsvToRgb(float h, float s, float v) {
        float hh = (h - (float) Math.floor(h)) * 6f;
        int i = (int) hh;
        float f = hh - i;
        float p = v * (1f - s);
        float q = v * (1f - s * f);
        float t = v * (1f - s * (1f - f));
        float r;
        float g;
        float b;
        switch (i % 6) {
            case 0: r = v; g = t; b = p; break;
            case 1: r = q; g = v; b = p; break;
            case 2: r = p; g = v; b = t; break;
            case 3: r = p; g = q; b = v; break;
            case 4: r = t; g = p; b = v; break;
            default: r = v; g = p; b = q; break;
        }
        return (Math.round(r * 255f) << 16) | (Math.round(g * 255f) << 8) | Math.round(b * 255f);
    }

    // ------------------------------------------------------------------ saving
    private void save() {
        String name = nameBox.getValue().trim();
        if (name.isEmpty()) {
            error = "Please type a name.";
            return;
        }

        int x;
        int y;
        int z;
        try {
            x = Integer.parseInt(xBox.getValue().trim());
            y = Integer.parseInt(yBox.getValue().trim());
            z = Integer.parseInt(zBox.getValue().trim());
        } catch (NumberFormatException e) {
            error = "X, Y and Z must be whole numbers.";
            return;
        }

        int secs;
        try {
            String t = timerBox.getValue().trim();
            secs = t.isEmpty() ? 0 : Integer.parseInt(t);
        } catch (NumberFormatException e) {
            error = "Timer must be a number of seconds (0 = forever).";
            return;
        }

        int color = chroma ? WaypointsMod.CHROMA : currentRgb();
        // Keep the legacy JSON marker field for backwards compatibility; it is not rendered.
        String marker = original != null ? original.safeMarkerText() : "W";

        long expires = secs > 0 ? System.currentTimeMillis() + secs * 1000L : 0L;
        String dim = dimMode >= 0 ? DIM_IDS[dimMode] : keepDimension;
        String worldId = original != null ? original.worldId() : WaypointsMod.currentWorldId();

        WaypointsMod.upsert(original, new Waypoint(name, x, y, z, color, dim, expires, worldId, marker, labelEnabled, beamEnabled));

        if (!chroma) {
            Integer rgb = color;
            RECENT.remove(rgb);
            RECENT.add(0, rgb);
            while (RECENT.size() > 6) RECENT.remove(RECENT.size() - 1);
        }
        back();
    }

    // ------------------------------------------------------------------ drawing
    @Override
    public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float delta) {
        if (draggingSvPicker || draggingHuePicker) updatePickerFromLiveMouse();
        super.extractRenderState(g, mouseX, mouseY, delta);

        String curHex = hexBox.getValue();
        if (!curHex.equals(lastHex)) {
            lastHex = curHex;
            applyHex(curHex);
        }

        int left = formLeft();
        int preview = previewX();
        int pickerTop = pickerY();
        int nameY = oy + 44;
        int coordsY = oy + 94;
        int dimY = oy + 145;
        int expiryY = oy + 190;
        int nameWidth = formWidth - 36;

        drawSidebar(g, mouseX, mouseY);
        g.fill(railWidth, 0, width, 39, 0xFF0C0E10);
        g.fill(railWidth, 38, width, 39, CARD_BORDER);
        g.text(font, "◇  " + worldTitle() + "   /   Waypoints   /   " + (original == null ? "New waypoint" : "Edit waypoint"), railWidth + 24, 15, MUTED, false);
        g.text(font, returnPage < 0 ? "←  Back to game" : "←  All waypoints", contentLeft, 43, MUTED, false);
        g.text(font, original == null ? "Mark somewhere new." : "Edit waypoint", contentLeft, 57, TEXT, true);
        g.text(font, "Save a location now. Find your way back whenever you need it.", contentLeft, 76, MUTED, false);

        // Location details card.
        card(g, contentLeft, oy + 10, formWidth, 230);
        g.text(font, "⌖  Location details", contentLeft + 18, oy + 23, TEXT, true);
        g.text(font, "Waypoint name", left, oy + 34, MUTED, false);
        drawBox(g, nameBox, left, nameY, nameWidth, 23, "Waypoint name");
        g.text(font, "A memorable name for a memorable place.", left, nameY + 29, QUIET, false);
        int cw = coordinateWidth;
        int gap = 8;
        g.text(font, "X", left, coordsY - 12, TEXT, false);
        g.text(font, "Y", left + cw + gap, coordsY - 12, TEXT, false);
        g.text(font, "Z", left + (cw + gap) * 2, coordsY - 12, TEXT, false);
        drawBox(g, xBox, left, coordsY, cw, 24, "X");
        drawBox(g, yBox, left + cw + gap, coordsY, cw, 24, "Y");
        drawBox(g, zBox, left + (cw + gap) * 2, coordsY, cw, 24, "Z");
        g.text(font, "ⓘ  Use your in-game coordinates. Press F3 to find them.", left, coordsY + 34, QUIET, false);
        g.text(font, "Dimension", left, dimY - 13, TEXT, false);
        int dx = left;
        int[] dimWidths = {70, 56, 44, Math.max(105, Math.min(118, nameWidth - 190))};
        for (int i = 0; i < DIM_LABELS.length; i++) {
            boolean selected = dimMode == i;
            int dw = dimWidths[i];
            rrect(g, dx, dimY, dx + dw, dimY + 22, selected ? ACCENT : CARD_BORDER);
            rrect(g, dx + 1, dimY + 1, dx + dw - 1, dimY + 21, selected ? 0xFF25321D : CARD);
            String label = DIM_LABELS[i];
            g.text(font, label, dx + (dw - font.width(label)) / 2, dimY + 7, selected ? ACCENT : MUTED, selected);
            dx += dw + 5;
        }
        g.text(font, "Expiry", left, expiryY + 3, TEXT, false);
        drawBox(g, timerBox, left + 72, expiryY - 3, 82, 22, "Seconds");
        g.text(font, "seconds  ·  0 means never", left + 163, expiryY + 4, MUTED, false);

        // Individual label / beam visibility card.
        int visibilityY = oy + 252;
        card(g, contentLeft, visibilityY, formWidth, 91);
        g.text(font, "Make it visible", contentLeft + 18, visibilityY + 12, TEXT, true);
        settingLine(g, mouseX, left, visibilityY + 28, formWidth - 36,
                "Show waypoint label", "Display the name and distance in your world.", labelEnabled);
        settingLine(g, mouseX, left, visibilityY + 60, formWidth - 36,
                "Show beacon beam", "A vertical beam to guide you from a distance.", beamEnabled);

        // Color workspace.
        card(g, colorX, oy + 10, colorWidth, 242);
        g.text(font, "Waypoint color", colorX + 16, oy + 23, TEXT, true);
        int previewRgb = chroma
                ? WaypointsMod.resolveColor(new Waypoint("", 0, 0, 0, WaypointsMod.CHROMA, "", 0L, "", "W"))
                : currentRgb();
        g.text(font, chroma ? "Chroma" : "Custom", colorX + colorWidth - font.width(chroma ? "Chroma" : "Custom") - 16, oy + 23, ACCENT, false);
        refreshPickerTexture();
        g.blit(PICKER_TEXTURE_ID, pickerX(), pickerTop,
                pickerX() + PICKER_WIDTH, pickerTop + PICKER_HEIGHT,
                0.0f, 1.0f, 0.0f, 1.0f);
        border(g, pickerX(), pickerTop, pickerX() + PICKER_WIDTH, pickerTop + PICKER_HEIGHT, CARD_BORDER);
        int markX = pickerX() + Math.round(sat * (PICKER_WIDTH - 1));
        int markY = pickerTop + Math.round((1f - val) * (PICKER_HEIGHT - 1));
        ring(g, markX, markY);
        ensureHueTexture();
        g.blit(HUE_TEXTURE_ID, hueX(), pickerTop, hueX() + 10, pickerTop + HUE_DISPLAY_HEIGHT,
                0.0f, 1.0f, 0.0f, 1.0f);
        int hueY = pickerTop + Math.round(hue * (HUE_DISPLAY_HEIGHT - 1));
        g.fill(hueX() - 2, hueY - 1, hueX() + 12, hueY + 1, 0xFFFFFFFF);
        int rowY = pickerTop + 118;
        rrect(g, preview, rowY, preview + 30, rowY + 24, 0xFF000000 | previewRgb);
        drawBox(g, hexBox, preview + 42, rowY, Math.max(76, colorWidth - 132), 22, "HEX");
        g.text(font, "HEX", preview + 42, rowY + 25, QUIET, false);
        int chromaY = pickerTop + 146;
        rounded(g, preview, chromaY, preview + colorWidth - 32, chromaY + 22, PANEL_ALT, 6);
        g.text(font, "✧  Chroma mode", preview + 10, chromaY + 7, TEXT, false);
        toggle(g, preview + colorWidth - 58, chromaY + 4, chroma);
        g.text(font, "Cycle through the spectrum", preview + 10, chromaY + 13, QUIET, false);
        g.text(font, "Recent colors", preview, pickerTop + 176, MUTED, false);
        String resetLabel = "Reset";
        g.text(font, resetLabel, colorX + colorWidth - 16 - font.width(resetLabel), pickerTop + 176, MUTED, false);
        for (int i = 0; i < RECENT.size(); i++) {
            int sx = preview + 16 + i * 22;
            rrect(g, sx, pickerTop + 190, sx + 18, pickerTop + 208, 0xFF000000 | RECENT.get(i));
            if (!chroma && RECENT.get(i) == currentRgb()) border(g, sx - 1, pickerTop + 189, sx + 19, pickerTop + 209, TEXT);
        }
        // Small in-game preview directly beneath the color workspace.
        int previewPanelY = oy + 258;
        card(g, colorX, previewPanelY, colorWidth, Math.max(110, Math.min(186, height - previewPanelY - 30)));
        g.text(font, "IN-GAME PREVIEW", colorX + 14, previewPanelY + 11, QUIET, false);
        int mid = colorX + colorWidth / 2;
        g.fill(mid, previewPanelY + 24, mid + 1, previewPanelY + 62, 0xFF607F3E);
        rrect(g, mid - 12, previewPanelY + 30, mid + 12, previewPanelY + 54, 0xFF24331D);
        border(g, mid - 12, previewPanelY + 30, mid + 12, previewPanelY + 54, 0xFF000000 | previewRgb);
        g.text(font, "⌖", mid - 4, previewPanelY + 38, ACCENT, false);
        int previewW = Math.min(colorWidth - 24, Math.max(100, font.width(nameBox.getValue()) + 22));
        rounded(g, mid - previewW / 2, previewPanelY + 64, mid + previewW / 2, previewPanelY + 86, PANEL_ALT, 5);
        String previewName = fitPreview(nameBox.getValue(), previewW - 16);
        g.text(font, previewName, mid - font.width(previewName) / 2, previewPanelY + 70, ACCENT, false);

        int actionY = height - 30;
        actionButton(g, mouseX, mouseY, contentRight - 220, actionY, 94, 24, "Discard changes", false, false);
        actionButton(g, mouseX, mouseY, contentRight - 116, actionY, 116, 24,
                original == null ? "+  Create waypoint" : "✓  Save changes", true, false);
        if (original != null) actionButton(g, mouseX, mouseY, contentLeft, actionY, 112, 24, "Delete waypoint", false, true);
        if (!error.isEmpty()) g.text(font, error, contentLeft, height - 51, 0xFFFF8585, false);
    }

    private void drawSidebar(GuiGraphicsExtractor g, int mx, int my) {
        if (railWidth <= 0) return;
        g.fill(0, 0, railWidth, height, 0xFF101214);
        g.fill(railWidth - 1, 0, railWidth, height, CARD_BORDER);
        rrect(g, 14, 15, 43, 44, 0xFFB7F36B);
        g.text(font, "➤", 24, 26, 0xFF101410, true);
        if (railWidth > 70) {
            g.text(font, "Waypoint", 51, 19, TEXT, false);
            g.text(font, "M A N A G E R", 51, 31, MUTED, false);
            rrect(g, 14, 62, railWidth - 14, 105, CARD);
            g.text(font, worldTitle(), 24, 73, TEXT, true);
            g.fill(24, 91, 28, 95, ACCENT);
            g.text(font, "Java Edition · 26.3", 34, 89, MUTED, false);
            g.text(font, "WORKSPACE", 16, 125, QUIET, false);
            nav(g, mx, my, 10, 140, railWidth - 20, "◎", "Waypoints", false);
            nav(g, mx, my, 10, 179, railWidth - 20, "☷", "Settings", false);
        } else {
            nav(g, mx, my, 8, 104, 38, "◎", "", false);
            nav(g, mx, my, 8, 150, 38, "☷", "", false);
        }
    }

    private void nav(GuiGraphicsExtractor g, int mx, int my, int x, int y, int w, String icon, String label, boolean active) {
        if (active || inside(mx, my, x, y, w, 32)) rrect(g, x, y, x + w, y + 32, 0xFF202629);
        g.text(font, icon, x + 10, y + 11, active ? ACCENT : MUTED, false);
        if (!label.isEmpty()) g.text(font, label, x + 30, y + 11, active ? TEXT : MUTED, false);
    }

    private void card(GuiGraphicsExtractor g, int x, int y, int w, int h) {
        rrect(g, x, y, x + w, y + h, CARD);
        g.fill(x, y, x + w, y + 1, CARD_BORDER); g.fill(x, y + h - 1, x + w, y + h, CARD_BORDER);
        g.fill(x, y, x + 1, y + h, CARD_BORDER); g.fill(x + w - 1, y, x + w, y + h, CARD_BORDER);
    }

    private void settingLine(GuiGraphicsExtractor g, int mx, int x, int y, int w,
                             String title, String description, boolean enabled) {
        g.text(font, title, x, y, TEXT, false);
        g.text(font, description, x, y + 12, MUTED, false);
        toggle(g, x + w - 26, y + 1, enabled);
    }

    private void toggle(GuiGraphicsExtractor g, int x, int y, boolean enabled) {
        rrect(g, x, y, x + 26, y + 15, enabled ? 0xFF45652A : 0xFF3B4247);
        rrect(g, enabled ? x + 13 : x + 2, y + 2, enabled ? x + 24 : x + 13, y + 13,
                enabled ? ACCENT : 0xFF9BA4A8);
    }

    private void actionButton(GuiGraphicsExtractor g, int mx, int my, int x, int y, int w, int h,
                              String label, boolean primary, boolean danger) {
        boolean hover = inside(mx, my, x, y, w, h);
        int fill = primary ? (hover ? 0xFFC9FF83 : ACCENT)
                : danger ? (hover ? 0xFF4D2225 : 0xFF281719)
                : (hover ? 0xFF242B2F : CARD);
        rrect(g, x, y, x + w, y + h, fill);
        if (!primary) {
            g.fill(x, y, x + w, y + 1, danger ? 0xFF85464A : CARD_BORDER);
            g.fill(x, y + h - 1, x + w, y + h, danger ? 0xFF85464A : CARD_BORDER);
        }
        int color = primary ? 0xFF101410 : danger ? 0xFFFF8C92 : TEXT;
        g.text(font, label, x + (w - font.width(label)) / 2, y + (h - 8) / 2, color, false);
    }

    private String fitPreview(String value, int max) {
        if (value == null) return "Waypoint";
        String s = value;
        while (!s.isEmpty() && font.width(s) > max) s = s.substring(0, s.length() - 1);
        return s.isEmpty() ? "Waypoint" : s.equals(value) ? s : s + "…";
    }

    private String worldTitle() {
        Minecraft mc = Minecraft.getInstance();
        try {
            if (mc.getCurrentServer() != null) return mc.getCurrentServer().name;
            if (mc.getSingleplayerServer() != null) return mc.getSingleplayerServer().getWorldData().getLevelName();
        } catch (Throwable ignored) { }
        return "Survival Realm";
    }

    private void drawBox(GuiGraphicsExtractor g, EditBox box, int x, int y, int w, int h, String placeholder) {
        boolean focused = box.isFocused();
        rrect(g, x, y, x + w, y + h, focused ? CYAN : CARD_BORDER);
        rrect(g, x + 1, y + 1, x + w - 1, y + h - 1, CARD);
        String v = box.getValue();
        int ty = y + (h - 8) / 2;
        if (v.isEmpty() && !focused) {
            g.text(this.font, placeholder, x + 8, ty, MUTED, false);
        } else {
            g.text(this.font, v, x + 8, ty, TEXT, false);
            if (focused && (System.currentTimeMillis() / 500) % 2 == 0) {
                int caretX = x + 8 + this.font.width(v);
                g.fill(caretX, y + 3, caretX + 1, y + h - 3, TEXT);
            }
        }
    }

    private static void border(GuiGraphicsExtractor g, int x, int y, int x2, int y2, int color) {
        g.fill(x, y, x2, y + 1, color);
        g.fill(x, y2 - 1, x2, y2, color);
        g.fill(x, y, x + 1, y2, color);
        g.fill(x2 - 1, y, x2, y2, color);
    }

    private static void rounded(GuiGraphicsExtractor g, int x, int y, int x2, int y2, int color, int radius) {
        int w = x2 - x;
        int h = y2 - y;
        if (w <= 0 || h <= 0) return;
        int r = Math.min(radius, Math.min(w / 2, h / 2));
        g.fill(x + r, y, x2 - r, y2, color);
        g.fill(x, y + r, x2, y2 - r, color);
        for (int row = 0; row < r; row++) {
            int inset = (int) Math.ceil(r - Math.sqrt(Math.max(0.0, r * r - Math.pow(r - row - 0.5, 2))));
            g.fill(x + inset, y + row, x2 - inset, y + row + 1, color);
            g.fill(x + inset, y2 - row - 1, x2 - inset, y2 - row, color);
        }
    }

    /** Efficient rounded rectangle: draw the body once and rasterize only the corner rows. */
    private static void rrect(GuiGraphicsExtractor g, int x1, int y1, int x2, int y2, int color) {
        int width = x2 - x1;
        int height = y2 - y1;
        if (width <= 0 || height <= 0) return;

        int radius = Math.min(7, Math.min(width / 2, height / 2));
        if (radius <= 0) {
            g.fill(x1, y1, x2, y2, color);
            return;
        }

        // Central body: two rectangles instead of one draw call per vertical pixel.
        g.fill(x1 + radius, y1, x2 - radius, y2, color);
        g.fill(x1, y1 + radius, x2, y2 - radius, color);

        // Only the rounded top and bottom corner rows need separate fills.
        for (int row = 0; row < radius; row++) {
            double dy = radius - row - 0.5;
            int inset = (int) Math.ceil(radius - Math.sqrt(Math.max(0.0, radius * radius - dy * dy)));
            g.fill(x1 + inset, y1 + row, x2 - inset, y1 + row + 1, color);
            g.fill(x1 + inset, y2 - row - 1, x2 - inset, y2 - row, color);
        }
    }

    /** Small white ring used as the picker marker. */
    private static void ring(GuiGraphicsExtractor g, int x, int y) {
        g.fill(x - 3, y - 3, x + 4, y - 2, 0xFF000000);
        g.fill(x - 3, y + 3, x + 4, y + 4, 0xFF000000);
        g.fill(x - 3, y - 2, x - 2, y + 3, 0xFF000000);
        g.fill(x + 3, y - 2, x + 4, y + 3, 0xFF000000);
        g.fill(x - 2, y - 2, x + 3, y - 1, 0xFFFFFFFF);
        g.fill(x - 2, y + 2, x + 3, y + 3, 0xFFFFFFFF);
        g.fill(x - 2, y - 1, x - 1, y + 2, 0xFFFFFFFF);
        g.fill(x + 2, y - 1, x + 3, y + 2, 0xFFFFFFFF);
    }

    private static boolean inside(int mx, int my, int x, int y, int w, int h) {
        return mx >= x && mx < x + w && my >= y && my < y + h;
    }
}
