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
 * Create / edit one waypoint with a dark Feather-style layout: name, X/Y/Z, dimension buttons
 * (Overworld / Nether / End / All dimensions), a color picker (saturation-brightness grid, hue strip,
 * hex box, Chroma toggle, Reset, recent colors), and timer.
 *
 * How it works: normal Minecraft buttons and text boxes sit invisibly in the right places and handle all
 * clicking and typing; everything you actually SEE is drawn on top of them in extractRenderState.
 */
public class WaypointEditScreen extends Screen {
    // ---- theme ----
    private static final int PANEL = 0xF20A1120;
    private static final int CARD = 0xFF121E30;
    private static final int CARD_HOVER = 0xFF1B2B42;
    private static final int CARD_BORDER = 0xFF2A4058;
    private static final int TEXT = 0xFFF0F6FF;
    private static final int MUTED = 0xFF93A8C1;
    private static final int ORANGE = 0xFF62E6C4;
    private static final int ORANGE_HOVER = 0xFF8AF0D8;
    private static final int ORANGE_DARK = 0xFF153A3B;

    // ---- color picker geometry ----
    // GUI-space dimensions stay compact; the backing texture is 2x resolution
    // so the gradient stays smooth on scaled/high-DPI displays.
    private static final int PICKER_WIDTH = 128;
    private static final int PICKER_HEIGHT = 64;
    private static final int PICKER_TEXTURE_WIDTH = 256;
    private static final int PICKER_TEXTURE_HEIGHT = 128;
    private static final Identifier PICKER_TEXTURE_ID = Identifier.fromNamespaceAndPath(
            "simplewaypoints", "gui/waypoint_color_picker");
    private static DynamicTexture pickerTexture;
    private static float pickerTextureHue = Float.NaN;
    private static long lastPickerUploadNanos = 0L;

    // The hue strip is static, so generate it once and draw it as one texture.
    private static final int HUE_DISPLAY_HEIGHT = 128;
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

    public WaypointEditScreen(Waypoint original, int returnPage) {
        super(Component.literal(original == null ? "New Waypoint" : "Edit Waypoint"));
        this.original = original;
        this.returnPage = returnPage;

        if (original != null) {
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
        g.fill(0, 0, this.width, this.height, 0xB0000000);
    }

    // ------------------------------------------------------------------ layout / widgets
    @Override
    protected void init() {
        cx = this.width / 2;
        oy = Math.max(2, (this.height - 256) / 2);

        // close (X) and go back
        click(cx + 138, oy + 2, 20, 14, this::back);
        click(cx - 150, oy + 22, 62, 16, this::back);

        nameBox = makeBox(nameBox, cx - 150, oy + 44, 300, 18, initName, 32, "Name");
        xBox = makeBox(xBox, cx - 150, oy + 66, 96, 18, initX, 9, "X");
        yBox = makeBox(yBox, cx - 48, oy + 66, 96, 18, initY, 9, "Y");
        zBox = makeBox(zBox, cx + 54, oy + 66, 96, 18, initZ, 9, "Z");

        // dimension buttons
        int dx = cx - 150;
        for (int i = 0; i < DIM_LABELS.length; i++) {
            final int mode = i;
            click(dx, oy + 88, DIM_W[i], 18, () -> dimMode = mode);
            dx += DIM_W[i] + 4;
        }

        // Color picker input is handled directly in mouseClicked (no hundreds of hidden widgets).
        click(cx + 28, oy + 109, 60, 13, () -> chroma = !chroma);
        click(cx + 90, oy + 109, 60, 13, this::resetColor);
        for (int i = 0; i < RECENT.size(); i++) {
            final int idx = i;
            click(cx + 2 + i * 16, oy + 181, 14, 10, () -> pickRecent(idx));
        }

        hexBox = makeBox(hexBox, cx + 2, oy + 152, 100, 16, String.format(Locale.ROOT, "%06X", currentRgb()), 7, "HEX");
        lastHex = hexBox.getValue();

        timerBox = makeBox(timerBox, cx - 100, oy + 198, 56, 16, initTimer, 6, "0");
        refreshPickerTexture();
        ensureHueTexture();

        // save / delete
        int saveWidth = original != null ? 216 : 300;
        click(cx - 150, oy + 220, saveWidth, 20, this::save);
        if (original != null) {
            click(cx + 72, oy + 220, 78, 20, () -> {
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

            int gx = cx - 150;
            int gy = oy + 124;
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

            int hueX = cx - 16;
            int hueY = oy + 124;
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
        int gx = cx - 150;
        int gy = oy + 124;

        sat = clamp01((float) ((mouseX - gx) / (PICKER_WIDTH - 1.0)));
        val = 1.0f - clamp01((float) ((mouseY - gy) / (PICKER_HEIGHT - 1.0)));
        chroma = false;
        syncHex();
    }

    private void updateHuePicker(double mouseY) {
        int gy = oy + 124;
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

        WaypointsMod.upsert(original, new Waypoint(name, x, y, z, color, dim, expires, worldId, marker));

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
        // Sample Minecraft's actual pointer position every frame while a picker
        // drag is captured. This is the same scaled coordinate source used by
        // vanilla chat and widgets, and avoids a stale marker on fast mouse moves.
        if (draggingSvPicker || draggingHuePicker) {
            // Do not use MouseHandler.isLeftPressed() here: Minecraft 26.3 only
            // updates that convenience flag when no screen is open. The screen's
            // mouseReleased callback ends this captured drag instead.
            updatePickerFromLiveMouse();
        }

        super.extractRenderState(g, mouseX, mouseY, delta);

        // Typing a hex code updates the selected hue and the cached gradient texture.
        String curHex = hexBox.getValue();
        if (!curHex.equals(lastHex)) {
            lastHex = curHex;
            applyHex(curHex);
        }

        // panel (covers the plain widgets underneath)
        rrect(g, cx - 158, oy + 16, cx + 158, oy + 250, PANEL);

        // title + close
        g.text(this.font, "WAYPOINT STATION  /  CONFIGURE", cx - 158, oy + 4, MUTED, false);
        g.text(this.font, original == null ? "CREATE WAYPOINT" : "EDIT WAYPOINT", cx - 158, oy + 16, TEXT, true);
        boolean closeHover = inside(mouseX, mouseY, cx + 138, oy + 2, 20, 14);
        rrect(g, cx + 138, oy + 2, cx + 158, oy + 16, closeHover ? ORANGE_HOVER : ORANGE);
        rrect(g, cx + 139, oy + 3, cx + 157, oy + 15, 0xFF1A1208);
        g.text(this.font, "x", cx + 148 - this.font.width("x") / 2, oy + 5, ORANGE, false);

        // go back
        boolean backHover = inside(mouseX, mouseY, cx - 150, oy + 22, 62, 16);
        rrect(g, cx - 150, oy + 22, cx - 88, oy + 38, backHover ? CARD_HOVER : CARD);
        String backLabel = returnPage < 0 ? "< Cancel" : "< Go back";
        g.text(this.font, backLabel, cx - 119 - this.font.width(backLabel) / 2, oy + 26, MUTED, false);

        // text boxes
        drawBox(g, nameBox, cx - 150, oy + 44, 300, 18, "Name");
        drawBox(g, xBox, cx - 150, oy + 66, 96, 18, "X");
        drawBox(g, yBox, cx - 48, oy + 66, 96, 18, "Y");
        drawBox(g, zBox, cx + 54, oy + 66, 96, 18, "Z");
        g.text(this.font, "X", cx - 150 + 96 - 10, oy + 71, MUTED, false);
        g.text(this.font, "Y", cx - 48 + 96 - 10, oy + 71, MUTED, false);
        g.text(this.font, "Z", cx + 54 + 96 - 10, oy + 71, MUTED, false);

        // dimension buttons
        int dx = cx - 150;
        for (int i = 0; i < DIM_LABELS.length; i++) {
            boolean selected = dimMode == i;
            boolean hover = inside(mouseX, mouseY, dx, oy + 88, DIM_W[i], 18);
            rrect(g, dx, oy + 88, dx + DIM_W[i], oy + 106, selected ? ORANGE : CARD_BORDER);
            rrect(g, dx + 1, oy + 89, dx + DIM_W[i] - 1, oy + 105, selected ? ORANGE_DARK : (hover ? CARD_HOVER : CARD));
            int tw = this.font.width(DIM_LABELS[i]);
            g.text(this.font, DIM_LABELS[i], dx + (DIM_W[i] - tw) / 2, oy + 93, selected ? TEXT : MUTED, false);
            dx += DIM_W[i] + 4;
        }

        // color header, chroma toggle, reset
        g.text(this.font, "COLOR", cx - 150, oy + 112, TEXT, true);
        g.text(this.font, "Chroma", cx + 28, oy + 112, MUTED, false);
        rrect(g, cx + 66, oy + 110, cx + 88, oy + 120, chroma ? ORANGE : 0xFF39414B);
        int knobX = chroma ? cx + 78 : cx + 67;
        g.fill(knobX, oy + 111, knobX + 9, oy + 119, 0xFFF3F4F6);
        boolean resetHover = inside(mouseX, mouseY, cx + 90, oy + 109, 60, 13);
        rrect(g, cx + 90, oy + 109, cx + 150, oy + 122, resetHover ? CARD_HOVER : CARD);
        g.text(this.font, "Reset color", cx + 120 - this.font.width("Reset color") / 2, oy + 112, MUTED, false);

        // Smooth 256x128 saturation/brightness texture displayed at 128x64 GUI units.
        // It is one draw call and regenerates only when hue changes.
        int gx = cx - 150;
        int gy = oy + 124;
        refreshPickerTexture();
        g.blit(PICKER_TEXTURE_ID, gx, gy, gx + PICKER_WIDTH, gy + PICKER_HEIGHT,
                0.0f, 1.0f, 0.0f, 1.0f);
        int markX = gx + Math.round(sat * (PICKER_WIDTH - 1));
        int markY = gy + Math.round((1f - val) * (PICKER_HEIGHT - 1));
        ring(g, markX, markY);

        // One cached texture draw instead of 128 individual fill operations.
        ensureHueTexture();
        int hueHeight = HUE_DISPLAY_HEIGHT;
        g.blit(HUE_TEXTURE_ID, cx - 16, gy, cx - 6, gy + hueHeight,
                0.0f, 1.0f, 0.0f, 1.0f);
        int hueY = gy + Math.round(hue * (hueHeight - 1));
        g.fill(cx - 18, hueY - 1, cx - 4, hueY + 1, 0xFFFFFFFF);

        // selected color preview + hex
        g.text(this.font, "Selected color", cx + 2, oy + 124, MUTED, false);
        int previewRgb = chroma
                ? WaypointsMod.resolveColor(new Waypoint("", 0, 0, 0, WaypointsMod.CHROMA, "", 0L, "", "W"))
                : currentRgb();
        rrect(g, cx + 2, oy + 134, cx + 102, oy + 148, 0xFF000000 | previewRgb);
        drawBox(g, hexBox, cx + 2, oy + 152, 100, 16, "HEX");

        // recent colors
        g.text(this.font, "Recent colors", cx + 2, oy + 172, MUTED, false);
        for (int i = 0; i < RECENT.size(); i++) {
            rrect(g, cx + 2 + i * 16, oy + 181, cx + 16 + i * 16, oy + 191, 0xFF000000 | RECENT.get(i));
        }

        // timer
        g.text(this.font, "Timer (s)", cx - 150, oy + 202, MUTED, false);
        drawBox(g, timerBox, cx - 100, oy + 198, 56, 16, "0");
        g.text(this.font, "0 = forever", cx - 30, oy + 202, MUTED, false);

        // save / delete
        int saveWidth = original != null ? 216 : 300;
        boolean saveHover = inside(mouseX, mouseY, cx - 150, oy + 220, saveWidth, 20);
        rrect(g, cx - 150, oy + 220, cx - 150 + saveWidth, oy + 240, saveHover ? ORANGE_HOVER : ORANGE);
        String saveLabel = original == null ? "Create Waypoint" : "Save Waypoint";
        g.text(this.font, saveLabel, cx - 150 + saveWidth / 2 - this.font.width(saveLabel) / 2, oy + 226, 0xFFFFFFFF, true);
        if (original != null) {
            boolean delHover = inside(mouseX, mouseY, cx + 72, oy + 220, 78, 20);
            rrect(g, cx + 72, oy + 220, cx + 150, oy + 240, delHover ? 0xFF9B2C2C : 0xFF7F1D1D);
            g.text(this.font, "Delete", cx + 111 - this.font.width("Delete") / 2, oy + 226, 0xFFFEE2E2, false);
        }

        if (!error.isEmpty()) {
            g.text(this.font, error, cx - 150, oy + 242, 0xFFFF6B6B, false);
        }
    }

    private void drawBox(GuiGraphicsExtractor g, EditBox box, int x, int y, int w, int h, String placeholder) {
        boolean focused = box.isFocused();
        rrect(g, x, y, x + w, y + h, focused ? ORANGE : CARD_BORDER);
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
