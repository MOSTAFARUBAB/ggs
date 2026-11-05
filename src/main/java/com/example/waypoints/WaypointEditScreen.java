package com.example.waypoints;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
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
 * Text inputs remain native Minecraft edit boxes; custom controls use separate
 * hit targets so vanilla button backgrounds never cover the designed interface.
 */
public class WaypointEditScreen extends Screen {
    // ---- theme ----
    private static final int PANEL = 0xF0080808;
    private static final int PANEL_ALT = 0xFF0F1214;
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
    private static final int PICKER_MAX_HEIGHT = 126;
    private static final int PICKER_TEXTURE_WIDTH = 256;
    private static final int PICKER_TEXTURE_HEIGHT = 128;
    private static final Identifier PICKER_TEXTURE_ID = Identifier.fromNamespaceAndPath(
            "simplewaypoints", "gui/waypoint_color_picker");
    private static DynamicTexture pickerTexture;
    private static float pickerTextureHue = Float.NaN;
    private static long lastPickerUploadNanos = 0L;

    // The hue strip is static, so generate it once and draw it as one texture.
    private static final int HUE_DISPLAY_HEIGHT = 8;
    private static final int HUE_TEXTURE_HEIGHT = 8;
    private static final int HUE_TEXTURE_WIDTH = 256;
    private static final Identifier HUE_TEXTURE_ID = Identifier.fromNamespaceAndPath(
            "simplewaypoints", "gui/waypoint_hue_strip");
    private static DynamicTexture hueTexture;

    // ---- dimensions ----
    private static final String[] DIM_LABELS = {"Overworld", "Nether", "The End"};
    private static final String[] DIM_IDS = {"minecraft:overworld", "minecraft:the_nether", "minecraft:the_end"};

    /** Recently saved colors (kept until you quit the game). */
    private static final List<Integer> RECENT = new ArrayList<>();

    private record HitTarget(int x, int y, int width, int height, Runnable action) {
        boolean contains(double px, double py) {
            return px >= x && px < x + width && py >= y && py < y + height;
        }
    }
    private final List<HitTarget> hitTargets = new ArrayList<>();

    private final Waypoint original; // null = creating a new one
    private final int returnPage;

    // starting values (kept so the screen survives being re-initialised, e.g. on window resize)
    private final String initName;
    private final String initX;
    private final String initY;
    private final String initZ;
    private final String keepDimension;

    private EditBox nameBox;
    private EditBox xBox;
    private EditBox yBox;
    private EditBox zBox;
    private EditBox hexBox;

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
    private boolean dimensionMenuOpen;
    private int dimMode;            // 0 Overworld, 1 Nether, 2 End, -1 = keep the original (modded) dimension
    private String lastHex = "";
    private String error = "";

    private int cx;
    private int oy;
    private int cardTop;
    private int locationCardHeight;
    private int visibilityY;
    private int visibilityHeight;
    private int colorCardHeight;
    private int actionY;
    private int breadcrumbY;
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
    private int locationOffset(int base) { return Math.round(base * (locationCardHeight / 308.0f)); }
    private int nameFieldY() { return cardTop + locationOffset(70); }
    private int coordinateFieldsY() { return cardTop + locationOffset(156); }
    private int dimensionFieldY() { return cardTop + locationOffset(243); }
    private int pickerX() { return colorX + 16; }
    private int pickerY() { return oy + 57; }
    private int pickerHeight() { return Math.max(108, Math.min(PICKER_MAX_HEIGHT, Math.round(height * 0.1375f))); }
    private int pickerWidth() { return Math.max(170, colorWidth - 32); }
    private int hueX() { return pickerX(); }
    private int hueY() { return pickerY() + pickerHeight() + 12; }
    private int hexRowY() { return hueY() + HUE_DISPLAY_HEIGHT + 14; }
    private int chromaY() { return hexRowY() + Math.round(42 + (height - 784) * 0.0484f); }
    private int recentLabelY() { return chromaY() + Math.round(59 + (height - 784) * 0.0323f); }
    private int recentColorsY() { return recentLabelY() + 18; }
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
        g.fill(0, 0, this.width, this.height, 0xFF090A0C);
    }

    // ------------------------------------------------------------------ layout / widgets
    @Override
    protected void init() {
        clearWidgets();
        hitTargets.clear();
        railWidth = width >= 800 ? Math.max(150, Math.min(190, Math.round(width * 0.16f))) : 0;
        int sideInset = width >= 800 ? 32 : 18;
        contentLeft = railWidth > 0 ? railWidth + sideInset : sideInset;
        contentRight = width - sideInset;
        int available = Math.max(500, contentRight - contentLeft);
        colorWidth = Math.max(250, Math.min(330, available / 3));
        formWidth = Math.max(300, available - colorWidth - 18);
        colorX = contentLeft + formWidth + 18;
        coordinateWidth = Math.max(72, (formWidth - 52) / 3);

        cardTop = Math.max(140, Math.round(height * 0.215f));
        oy = cardTop - 10;
        breadcrumbY = Math.round(height * 0.10f);
        locationCardHeight = Math.max(292, Math.min(344, Math.round(308 + (height - 784) * 0.266f)));
        visibilityY = cardTop + locationCardHeight + 14;
        visibilityHeight = 152;
        colorCardHeight = Math.max(330, Math.round(340 + (height - 784) * 0.298f));
        actionY = original == null ? height - 66 : height - 188;

        int left = formLeft();
        int nameY = nameFieldY();
        int coordsY = coordinateFieldsY();
        int dimY = dimensionFieldY();
        int nameWidth = formWidth - 36;
        int coordGap = 8;

        if (railWidth > 0) {
            int navY = 153;
            click(10, navY, railWidth - 20, 32,
                    () -> Minecraft.getInstance().gui.setScreen(new WaypointScreen(returnPage < 0 ? 0 : returnPage)));
            click(10, navY + 41, railWidth - 20, 32,
                    () -> Minecraft.getInstance().gui.setScreen(new WaypointSettingsScreen()));
        }
        // The breadcrumb is the only Back hit area; it never overlaps the page title.
        click(contentLeft, breadcrumbY - 4, 126, 18, this::back);

        nameBox = makeBox(nameBox, left, nameY, nameWidth, 34, initName, 32, "Waypoint name");
        xBox = makeBox(xBox, left, coordsY, coordinateWidth, 34, initX, 9, "X");
        yBox = makeBox(yBox, left + coordinateWidth + coordGap, coordsY, coordinateWidth, 34, initY, 9, "Y");
        zBox = makeBox(zBox, left + (coordinateWidth + coordGap) * 2, coordsY, coordinateWidth, 34, initZ, 9, "Z");

        click(left, dimY, nameWidth, 34, () -> dimensionMenuOpen = !dimensionMenuOpen);

        // Color picker interactions use a wide horizontal saturation/value field and hue strip.
        click(pickerX(), pickerY(), pickerWidth(), pickerHeight(), () -> { });
        click(hueX(), hueY(), pickerWidth(), HUE_DISPLAY_HEIGHT + 4, () -> { });
        click(colorX + colorWidth - 56, recentLabelY() - 1, 42, 16, this::resetColor);
        click(previewX(), chromaY(), colorWidth - 32, 46, () -> chroma = !chroma);
        for (int i = 0; i < RECENT.size(); i++) {
            final int idx = i;
            click(previewX() + 1 + i * 25, recentColorsY(), 21, 21, () -> pickRecent(idx));
        }
        int rowY = hexRowY();
        hexBox = makeBox(hexBox, previewX() + 84, rowY + 3, Math.max(72, colorWidth - 128), 28,
                String.format(Locale.ROOT, "%06X", currentRgb()), 7, "HEX");
        lastHex = hexBox.getValue();

        // Per-waypoint visibility controls.
        click(left + formWidth - 62, visibilityY + 55, 26, 15, () -> labelEnabled = !labelEnabled);
        click(left + formWidth - 62, visibilityY + 100, 26, 15, () -> beamEnabled = !beamEnabled);

        refreshPickerTexture();
        ensureHueTexture();

        int formRight = contentLeft + formWidth;
        if (original == null) {
            click(formRight - 180, actionY, 62, 31, this::back);
            click(formRight - 118, actionY, 118, 31, this::save);
        } else {
            click(formRight - 236, actionY, 112, 31, this::back);
            click(formRight - 116, actionY, 116, 31, this::save);
            int deleteY = height - 137;
            click(formRight - 96, deleteY + 16, 78, 34, () -> {
                WaypointsMod.remove(original);
                back();
            });
        }
    }

    private void click(int x, int y, int w, int h, Runnable action) {
        // Keep hit testing separate from rendering so no default Minecraft button
        // background can cover the custom controls.
        hitTargets.add(new HitTarget(x, y, w, h, action));
    }

    private EditBox makeBox(EditBox old, int x, int y, int w, int h, String initial, int maxLength, String label) {
        EditBox box = new EditBox(this.font, x, y, w, h, Component.literal(label));
        box.setMaxLength(maxLength);
        box.setValue(old != null ? old.getValue() : initial);
        box.setBordered(false);
        box.setTextColor(TEXT);
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
            int pickerW = pickerWidth();
            int pickerH = pickerHeight();

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
            int hueY = hueY();
            int hueW = pickerWidth();
            if (mouseX >= hueX && mouseX < hueX + hueW
                    && mouseY >= hueY && mouseY < hueY + HUE_DISPLAY_HEIGHT + 4) {
                draggingHuePicker = true;
                draggingSvPicker = false;
                this.setDragging(true);
                pickerPointerX = mouseX;
                pickerPointerY = mouseY;
                updateHuePicker(pickerPointerX);
                return true;
            }
        }

        if (event.button() == 1) {
            if (dimensionMenuOpen) {
                int left = formLeft();
                int optionWidth = formWidth - 36;
                if (inside(event.x(), event.y(), left, dimensionFieldY(), optionWidth, 34)) {
                    dimensionMenuOpen = false;
                    return true;
                }
                int top = dimensionFieldY() + locationOffset(36);
                int optionHeight = locationOffset(24);
                for (int i = 0; i < DIM_LABELS.length; i++) {
                    int optionY = top + i * optionHeight;
                    if (inside(event.x(), event.y(), left, optionY, optionWidth, optionHeight)) {
                        dimMode = i;
                        dimensionMenuOpen = false;
                        return true;
                    }
                }
                dimensionMenuOpen = false;
            }
            for (int i = hitTargets.size() - 1; i >= 0; i--) {
                HitTarget target = hitTargets.get(i);
                if (target.contains(event.x(), event.y())) {
                    target.action().run();
                    return true;
                }
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
        else if (draggingHuePicker) updateHuePicker(mouseX);
    }

    @Override
    public boolean mouseReleased(MouseButtonEvent event) {
        if (draggingSvPicker || draggingHuePicker) {
            // Apply the exact release coordinate before ending the drag. The
            // picker capture itself is initiated only by a left-button press.
            if (draggingSvPicker) updateSvPicker(event.x(), event.y());
            else updateHuePicker(event.x());
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

        sat = clamp01((float) ((mouseX - gx) / (pickerWidth() - 1.0)));
        val = 1.0f - clamp01((float) ((mouseY - gy) / (pickerHeight() - 1.0)));
        chroma = false;
        syncHex();
    }

    private void updateHuePicker(double mouseX) {
        int gx = hueX();
        int hueW = pickerWidth();

        hue = clamp01((float) ((mouseX - gx) / (hueW - 1.0)));
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
        for (int x = 0; x < HUE_TEXTURE_WIDTH; x++) {
            int rgb = 0xFF000000 | hsvToRgb(
                    x / (float) (HUE_TEXTURE_WIDTH - 1), 1.0f, 1.0f);
            for (int y = 0; y < HUE_TEXTURE_HEIGHT; y++) pixels.setPixel(x, y, rgb);
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

        int color = chroma ? WaypointsMod.CHROMA : currentRgb();
        // Keep the legacy JSON marker field for backwards compatibility; it is not rendered.
        String marker = original != null ? original.safeMarkerText() : "W";

        // Keep an existing temporary waypoint's expiry intact; newly created waypoints are permanent.
        long expires = original != null ? original.expiresAt() : 0L;
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

        String curHex = hexBox.getValue();
        if (!curHex.equals(lastHex)) {
            lastHex = curHex;
            applyHex(curHex);
        }

        int left = formLeft();
        int preview = previewX();
        int pickerTop = pickerY();
        int nameY = nameFieldY();
        int coordsY = coordinateFieldsY();
        int dimY = dimensionFieldY();
        int nameWidth = formWidth - 36;
        int formRight = contentLeft + formWidth;

        drawSidebar(g, mouseX, mouseY);
        int topBarHeight = Math.max(44, Math.round(height * 0.06f));
        g.fill(railWidth, 0, width, topBarHeight, 0xFF0C0E10);
        g.fill(railWidth, topBarHeight - 1, width, topBarHeight, CARD_BORDER);
        g.text(font, "◇  " + fitText(worldTitle(), 112) + "   /   Waypoints   /   " + (original == null ? "New waypoint" : "Edit waypoint"),
                railWidth + 24, 16, MUTED, false);
        g.fill(width - 132, 18, width - 127, 23, ACCENT);
        g.text(font, "Local workspace", width - 120, 16, MUTED, false);
        g.text(font, "←  " + (returnPage < 0 ? "Back to game" : "All waypoints"), contentLeft, breadcrumbY, MUTED, false);
        int titleY = Math.round(height * 0.137f);
        g.text(font, original == null ? "Mark somewhere new." : "Edit waypoint", contentLeft, titleY, TEXT, true);
        g.text(font, original == null ? "Save a location now. Find your way back whenever you need it." : "Fine-tune the details. Keep your favorite place within reach.",
                contentLeft, titleY + 35, MUTED, false);
        if (original != null) {
            rounded(g, formRight - 88, titleY + 10, formRight, titleY + 34, 0xFF182019, 6);
            g.text(font, "• Unsaved changes", formRight - 81, titleY + 18, ACCENT, false);
        }

        // Left: location card and per-waypoint visibility, matching the reference layout.
        card(g, contentLeft, cardTop, formWidth, locationCardHeight);
        g.text(font, "⌖  Location details", contentLeft + 18, cardTop + 21, TEXT, true);
        g.text(font, "Waypoint name", left, cardTop + locationOffset(54), TEXT, false);
        drawBox(g, nameBox, left, nameY, nameWidth, 34, "Waypoint name");
        g.text(font, "A memorable name for a memorable place.", left, nameY + locationOffset(42), QUIET, false);

        int cw = coordinateWidth;
        int gap = 8;
        g.text(font, "X", left, coordsY - 16, TEXT, false);
        g.text(font, "Y", left + cw + gap, coordsY - 16, TEXT, false);
        g.text(font, "Z", left + (cw + gap) * 2, coordsY - 16, TEXT, false);
        drawBox(g, xBox, left, coordsY, cw, 34, "X");
        drawBox(g, yBox, left + cw + gap, coordsY, cw, 34, "Y");
        drawBox(g, zBox, left + (cw + gap) * 2, coordsY, cw, 34, "Z");
        g.text(font, "ⓘ  Use your in-game coordinates. Press F3 to find them.", left, coordsY + locationOffset(46), QUIET, false);

        g.text(font, "Dimension", left, dimY - locationOffset(15), TEXT, false);
        rounded(g, left, dimY, left + nameWidth, dimY + 34, PANEL_ALT, 6);
        border(g, left, dimY, left + nameWidth, dimY + 34, CARD_BORDER);
        String selectedDimension = dimMode >= 0 ? DIM_LABELS[dimMode] : dimensionName(keepDimension);
        g.text(font, fitText(selectedDimension, nameWidth - 42), left + 11, dimY + 13, TEXT, false);
        g.text(font, "⌄", left + nameWidth - 19, dimY + 12, MUTED, false);
        g.text(font, "Only visible when you're in this dimension.", left, dimY + locationOffset(44), QUIET, false);
        card(g, contentLeft, visibilityY, formWidth, visibilityHeight);
        g.text(font, "Make it visible", contentLeft + 18, visibilityY + 20, TEXT, true);
        settingLine(g, mouseX, left, visibilityY + 54, formWidth - 36,
                "Show waypoint label", "Display the name and distance in your world.", labelEnabled);
        settingLine(g, mouseX, left, visibilityY + 99, formWidth - 36,
                "Show beacon beam", "A vertical beam to guide you from a distance.", beamEnabled);
        // Pop over the following card, as a native dropdown would, instead of
        // being clipped by the visibility panel when the dimension menu is open.
        if (dimensionMenuOpen) {
            int menuY = dimY + locationOffset(36);
            int optionHeight = locationOffset(24);
            rounded(g, left, menuY, left + nameWidth, menuY + DIM_LABELS.length * optionHeight, 0xFF080A0B, 7);
            border(g, left, menuY, left + nameWidth, menuY + DIM_LABELS.length * optionHeight, CARD_BORDER);
            for (int i = 0; i < DIM_LABELS.length; i++) {
                int optionY = menuY + i * optionHeight;
                if (i == dimMode) rounded(g, left + 2, optionY + 1, left + nameWidth - 2, optionY + optionHeight - 1, 0xFF202B1B, 5);
                g.text(font, DIM_LABELS[i], left + 11, optionY + (optionHeight - 8) / 2, i == dimMode ? ACCENT : TEXT, false);
            }
        }

        // Right: color workstation with a broad square picker and horizontal hue strip.
        card(g, colorX, cardTop, colorWidth, colorCardHeight);
        int previewRgb = chroma
                ? WaypointsMod.resolveColor(new Waypoint("", 0, 0, 0, WaypointsMod.CHROMA, "", 0L, "", "W"))
                : currentRgb();
        g.text(font, "Waypoint color", colorX + 16, cardTop + 21, TEXT, true);
        g.fill(colorX + colorWidth - 31, cardTop + 24, colorX + colorWidth - 26, cardTop + 29, ACCENT);
        g.text(font, chroma ? "Chroma" : "Custom", colorX + colorWidth - font.width(chroma ? "Chroma" : "Custom") - 18,
                cardTop + 21, MUTED, false);
        refreshPickerTexture();
        int pickerW = pickerWidth();
        g.blit(PICKER_TEXTURE_ID, pickerX(), pickerTop, pickerX() + pickerW, pickerTop + pickerHeight(),
                0.0f, 1.0f, 0.0f, 1.0f);
        border(g, pickerX(), pickerTop, pickerX() + pickerW, pickerTop + pickerHeight(), CARD_BORDER);
        int markX = pickerX() + Math.round(sat * (pickerW - 1));
        int markY = pickerTop + Math.round((1f - val) * (pickerHeight() - 1));
        ring(g, markX, markY);
        ensureHueTexture();
        g.blit(HUE_TEXTURE_ID, hueX(), hueY(), hueX() + pickerW, hueY() + HUE_DISPLAY_HEIGHT,
                0.0f, 1.0f, 0.0f, 1.0f);
        int hueMarkerX = hueX() + Math.round(hue * (pickerW - 1));
        g.fill(hueMarkerX - 1, hueY() - 2, hueMarkerX + 2, hueY() + HUE_DISPLAY_HEIGHT + 2, 0xFFFFFFFF);

        int rowY = hexRowY();
        rounded(g, preview, rowY, preview + 32, rowY + 34, 0xFF000000 | previewRgb, 6);
        drawBox(g, hexBox, preview + 42, rowY + 3, Math.max(100, colorWidth - 74), 28, "HEX");
        g.text(font, "HEX", preview + 50, rowY + 12, QUIET, false);
        g.text(font, "✎", colorX + colorWidth - 30, rowY + 12, MUTED, false);

        int chromaRow = chromaY();
        rounded(g, preview, chromaRow, preview + colorWidth - 32, chromaRow + 46, PANEL_ALT, 7);
        g.text(font, "✧  Chroma mode", preview + 11, chromaRow + 8, TEXT, false);
        toggle(g, preview + colorWidth - 59, chromaRow + 8, chroma);
        g.text(font, "Cycle through the spectrum", preview + 11, chromaRow + 25, QUIET, false);

        int recentY = recentLabelY();
        g.text(font, "Recent colors", preview, recentY, MUTED, false);
        String resetLabel = "Reset";
        g.text(font, resetLabel, colorX + colorWidth - 16 - font.width(resetLabel), recentY, MUTED, false);
        for (int i = 0; i < RECENT.size(); i++) {
            int sx = preview + 1 + i * 25;
            rrect(g, sx, recentColorsY(), sx + 21, recentColorsY() + 21, 0xFF000000 | RECENT.get(i));
            if (!chroma && RECENT.get(i) == currentRgb()) border(g, sx - 1, recentColorsY() - 1, sx + 22, recentColorsY() + 22, TEXT);
        }

        // Surface preview: sampled from loaded top-surface chunks, not underground blocks.
        int previewPanelY = cardTop + colorCardHeight + 46;
        g.text(font, "IN-GAME PREVIEW", colorX, previewPanelY - 18, QUIET, false);
        int mapCenterX = parseCoordinate(xBox, Minecraft.getInstance().player == null ? 0 : Minecraft.getInstance().player.blockPosition().getX());
        int mapCenterZ = parseCoordinate(zBox, Minecraft.getInstance().player == null ? 0 : Minecraft.getInstance().player.blockPosition().getZ());
        String mapDimension = dimMode >= 0 && dimMode < DIM_IDS.length ? DIM_IDS[dimMode] : keepDimension;
        int mapHeight = Math.max(96, Math.min(190, height - previewPanelY - 62));
        int mapCenterY = parseCoordinate(yBox, Minecraft.getInstance().player == null ? 64 : Minecraft.getInstance().player.blockPosition().getY());
        String previewName = nameBox.getValue().isBlank() ? "Waypoint" : nameBox.getValue().trim();
        String previewDetails = mapCenterX + ", " + mapCenterY + ", " + mapCenterZ;
        SurfaceMapPreview.draw(g, font, colorX, previewPanelY, colorWidth, mapHeight,
                mapCenterX, mapCenterZ, mapDimension, 0xFF000000 | previewRgb,
                previewName, previewDetails);
        g.text(font, "A clear marker, right where you need it.", colorX, previewPanelY + mapHeight + 8, QUIET, false);

        int formActionY = actionY;
        if (original == null) {
            g.text(font, "◈  This waypoint is saved to " + fitText(worldTitle(), 126) + " and stored on this device.",
                    contentLeft + 12, visibilityY + visibilityHeight + 38, MUTED, false);
            actionButton(g, mouseX, mouseY, formRight - 180, formActionY, 62, 31, "Cancel", false, false);
            actionButton(g, mouseX, mouseY, formRight - 118, formActionY, 118, 31, "+  Create waypoint", true, false);
        } else {
            actionButton(g, mouseX, mouseY, formRight - 236, formActionY, 112, 31, "Discard changes", false, false);
            actionButton(g, mouseX, mouseY, formRight - 116, formActionY, 116, 31, "✓  Save changes", true, false);
            int deleteY = height - 137;
            card(g, contentLeft, deleteY, formWidth, 66);
            g.text(font, "Delete waypoint", contentLeft + 16, deleteY + 17, 0xFFFF777F, false);
            g.text(font, "Permanently remove this waypoint. This can't be undone.", contentLeft + 16, deleteY + 39, MUTED, false);
            actionButton(g, mouseX, mouseY, formRight - 96, deleteY + 16, 78, 34, "Delete", false, true);
        }
        if (!error.isEmpty()) g.text(font, error, contentLeft, actionY - 18, 0xFFFF8585, false);

        // Native EditBoxes are rendered last so each field's text/caret appears once over its shell.
        super.extractRenderState(g, mouseX, mouseY, delta);
    }

    private String fitText(String value, int maxWidth) {
        if (value == null) return "";
        String result = value;
        while (!result.isEmpty() && font.width(result) > maxWidth) result = result.substring(0, result.length() - 1);
        return result.equals(value) ? result : result + "…";
    }

    private String dimensionName(String raw) {
        String dimension = WaypointsMod.normalizeDimension(raw);
        if (dimension.endsWith(":the_nether")) return "Nether";
        if (dimension.endsWith(":the_end")) return "The End";
        if (dimension.endsWith(":overworld")) return "Overworld";
        return "Current dimension";
    }

    private void drawSidebar(GuiGraphicsExtractor g, int mx, int my) {
        if (railWidth <= 0) return;
        g.fill(0, 0, railWidth, height, 0xFF101214);
        g.fill(railWidth - 1, 0, railWidth, height, CARD_BORDER);
        rrect(g, 14, 15, 43, 44, 0xFFB7F36B);
        g.text(font, "W", 23, 25, 0xFF101410, true);
        if (railWidth > 70) {
            g.text(font, "Waypoint", 51, 19, TEXT, false);
            g.text(font, "M A N A G E R", 51, 31, MUTED, false);
            rrect(g, 14, 63, railWidth - 14, 108, CARD);
            g.text(font, fitText(worldTitle(), railWidth - 48), 24, 74, TEXT, true);
            g.fill(24, 94, 28, 98, ACCENT);
            g.text(font, "Java Edition · 26.3", 34, 92, MUTED, false);
            g.text(font, "WORKSPACE", 16, 135, QUIET, false);
            nav(g, mx, my, 10, 153, railWidth - 20, "◎", "Waypoints", true);
            nav(g, mx, my, 10, 194, railWidth - 20, "☷", "Settings", false);
            rrect(g, 14, height - 109, railWidth - 14, height - 18, PANEL_ALT);
            g.text(font, "Your world. Your waypoints.", 23, height - 94, TEXT, false);
            g.text(font, "Stored locally, ready for", 23, height - 76, MUTED, false);
            g.text(font, "your next adventure.", 23, height - 63, MUTED, false);
            g.fill(23, height - 43, 28, height - 38, ACCENT);
            g.text(font, "All changes saved", 34, height - 44, MUTED, false);
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

    private int parseCoordinate(EditBox box, int fallback) {
        try { return Integer.parseInt(box.getValue().trim()); }
        catch (NumberFormatException ignored) { return fallback; }
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
        rrect(g, x, y, x + w, y + h, focused ? 0xFF536044 : CARD_BORDER);
        rrect(g, x + 1, y + 1, x + w - 1, y + h - 1, 0xFF0E1011);
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

    private static boolean inside(double mx, double my, int x, int y, int w, int h) {
        return mx >= x && mx < x + w && my >= y && my < y + h;
    }
}
