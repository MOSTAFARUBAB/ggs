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
 * Create / edit form matching the supplied Waypoint Manager reference: location
 * details and visibility controls on the left, color workstation and preview on the right.
 *
 * Text inputs remain native Minecraft edit boxes; custom controls use separate
 * hit targets so vanilla button backgrounds never cover the designed interface.
 */
public class WaypointEditScreen extends Screen {
    // ---- theme ----

    // ---- color picker geometry ----
    // GUI-space dimensions stay compact; the backing texture is 2x resolution
    // so the gradient stays smooth on scaled/high-DPI displays.
    private static final int PICKER_TEXTURE_WIDTH = 1024;
    private static final int PICKER_TEXTURE_HEIGHT = 512;
    private static final Identifier PICKER_TEXTURE_ID = Identifier.fromNamespaceAndPath(
            "simplewaypoints", "gui/waypoint_color_picker");
    private static DynamicTexture pickerTexture;
    private static float pickerTextureHue = Float.NaN;
    private static long lastPickerUploadNanos = 0L;

    // The hue strip is static, so generate it once and draw it as one texture.
    private static final int HUE_DISPLAY_HEIGHT = 8;
    private static final int HUE_TEXTURE_HEIGHT = 32;
    private static final int HUE_TEXTURE_WIDTH = 1024;
    private static final Identifier HUE_TEXTURE_ID = Identifier.fromNamespaceAndPath(
            "simplewaypoints", "gui/waypoint_hue_strip");
    private static DynamicTexture hueTexture;

    // ---- dimensions ----
    private static final String[] DIM_LABELS = {"Overworld", "Nether", "The End"};
    private static final String[] DIM_IDS = {"minecraft:overworld", "minecraft:the_nether", "minecraft:the_end"};

    /** Recently saved colors (kept until you quit the game). */
    private static final List<Integer> RECENT = new ArrayList<>(List.of(0xB7F36B, 0x59B7FF, 0xA98AFF, 0xFFB65C, 0x55D6B5, 0xF17A91, 0xE48BCB));

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
    private String keepDimension;

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
    private int selectedIconIndex = 0;
    private static final String[] ICON_GLYPHS = {"H", "★", "K", "W", "T", "D", "+"};
    private static final String[] ICON_MARKERS = {"H", "S", "K", "W", "T", "D", "M"};
    private boolean dimensionMenuOpen;
    private int dimMode;            // 0 Overworld, 1 Nether, 2 End, -1 = keep the original (modded) dimension
    private String lastHex = "";
    private String error = "";

    private int cx;
    private int oy;
    private int scrollOffset, maxScroll;
    private int cardTop;
    private int locationCardHeight;
    private int visibilityY;
    private int visibilityHeight;
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
    /** Scale the form as one unit on short GUI viewports; Minecraft's global GUI scale is left untouched. */
    private float layoutScale() { return Math.max(0.72f, Math.min(1.0f, height / 720.0f)); }
    private int ui(int value) { return Math.max(1, Math.round(value * layoutScale())); }
    private float formFactor() { return Math.max(0f, Math.min(1f, (height - 784) / 126f)); }
    private int formOffset(int compact, int expanded) { return ui(Math.round(compact + (expanded - compact) * formFactor())); }
    private int nameFieldY() { return cardTop + formOffset(66, 72); }
    private int coordinateFieldsY() { return cardTop + formOffset(148, 162); }
    private int dimensionFieldY() { return cardTop + formOffset(229, 250); }
    private int pickerX() { return colorX + ui(18); }
    private float colorScale() { return layoutScale(); }
    private int pickerY() { return cardTop + ui(48); }
    private int pickerHeight() { return ui(125); }
    private int pickerWidth() { return Math.max(90, colorWidth - ui(40)); }
    private int hueX() { return pickerX(); }
    private int hueY() { return pickerY() + pickerHeight() + ui(12); }
    private int hueDisplayHeight() { return ui(HUE_DISPLAY_HEIGHT); }
    private int hexRowY() { return cardTop + ui(207); }
    private int chromaY() { return cardTop + ui(254); }
    private int recentLabelY() { return cardTop + ui(315); }
    private int recentColorsY() { return cardTop + ui(334); }
    private int previewX() { return colorX; }
    private int presetX(int index) { return colorX + ui(18) + index * ui(30); }
    private int presetY(int index) { return recentColorsY(); }
    private int hexInputX() { return colorX + ui(58); }
    private int hexInputWidth() { return Math.max(72, colorWidth - ui(114)); }
    private int eyeX() { return colorX + colorWidth - ui(40); }
    private int colorCardHeight() { return ui(365); }
    private int previewPanelY() { return cardTop + colorCardHeight() + ui(20); }
    private int previewPanelHeight() { return ui(150); }
    private int contentViewTop() { return WaypointUiChrome.topHeight(height) + ui(78); }
    private int contentViewBottom() { return height - ui(8); }

    public WaypointEditScreen(Waypoint original, int returnPage) {
        super(Component.literal(original == null ? "New Waypoint" : "Edit Waypoint"));
        this.original = original;
        this.returnPage = returnPage;

        if (original != null) {
            labelEnabled = original.isLabelEnabled();
            beamEnabled = original.isBeamEnabled();
            boolean matchedIcon = false;
            for (int i = 0; i < ICON_MARKERS.length; i++) {
                if (ICON_MARKERS[i].equalsIgnoreCase(original.safeMarkerText())) {
                    selectedIconIndex = i;
                    matchedIcon = true;
                    break;
                }
            }
            if (!matchedIcon && "W".equalsIgnoreCase(original.safeMarkerText())) selectedIconIndex = 3;
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
            setFromRgb(0xB7F36B);
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
        g.fill(0, 0, this.width, this.height, 0xFF0B0C0E);
    }

    // ------------------------------------------------------------------ layout / widgets
    @Override
    protected void init() {
        clearWidgets();
        hitTargets.clear();
        railWidth = WaypointUiChrome.railWidth(width, height);
        int top = WaypointUiChrome.topHeight(height);
        contentLeft = railWidth + (height >= 880 ? 28 : 25);
        contentRight = width - 26;
        int available = Math.max(420, contentRight - contentLeft);
        formWidth = Math.max(300, Math.round(available * 0.64f));
        colorX = contentLeft + formWidth + 18;
        colorWidth = Math.max(230, contentRight - colorX);
        if (colorX + colorWidth > contentRight) colorWidth = contentRight - colorX;
        coordinateWidth = Math.max(70, (formWidth - 56) / 3);
        breadcrumbY = top + 30;
        int baseCardTop = Math.round(height * 0.185f + 26f);
        cardTop = baseCardTop; // previewPanelHeight() uses cardTop for short viewports.
        locationCardHeight = ui(284);
        visibilityHeight = ui(140);
        int baseVisibilityY = baseCardTop + locationCardHeight + ui(12);
        int actionGap = original == null ? ui(26) : ui(12);
        int baseActionY = baseVisibilityY + visibilityHeight + actionGap;
        // The create flow is laid out to fit the available height. Only the optional
        // delete section on an edit screen may require a small wheel scroll.
        int basePreviewBottom = baseCardTop + colorCardHeight() + ui(20) + previewPanelHeight();
        if (height >= 650) basePreviewBottom += ui(18);
        int baseDeleteBottom = original == null ? baseActionY + ui(36)
                : baseActionY + ui(36) + ui(12) + ui(52);
        int contentBottom = Math.max(basePreviewBottom, baseDeleteBottom);
        maxScroll = Math.max(0, contentBottom - contentViewBottom());
        scrollOffset = Math.max(0, Math.min(scrollOffset, maxScroll));
        cardTop = baseCardTop - scrollOffset;
        visibilityY = cardTop + locationCardHeight + ui(12);
        actionY = visibilityY + visibilityHeight + actionGap;
        int left = formLeft();
        int nameWidth = formWidth - 36;
        int coordGap = 10;

        int ny = WaypointUiChrome.navY(height);
        hitTargets.add(new HitTarget(20, ny, railWidth - 40, 36,
                () -> Minecraft.getInstance().gui.setScreen(new WaypointScreen(returnPage < 0 ? 0 : returnPage))));
        hitTargets.add(new HitTarget(20, ny + 43, railWidth - 40, 36,
                () -> Minecraft.getInstance().gui.setScreen(new WaypointSettingsScreen())));
        hitTargets.add(new HitTarget(contentLeft, breadcrumbY - 4, 120, 24, this::back));

        nameBox = makeBox(nameBox, left + 4, nameFieldY() + ui(7), nameWidth - 8, 22, initName, 32, "Waypoint name");
        xBox = makeBox(xBox, left + 4, coordinateFieldsY() + ui(7), coordinateWidth - 8, 22, initX, 9, "X");
        yBox = makeBox(yBox, left + coordinateWidth + coordGap + 4, coordinateFieldsY() + ui(7), coordinateWidth - 8, 22, initY, 9, "Y");
        zBox = makeBox(zBox, left + (coordinateWidth + coordGap) * 2 + 4, coordinateFieldsY() + ui(7), coordinateWidth - 8, 22, initZ, 9, "Z");
        hexBox = makeBox(hexBox, hexInputX() + 4, hexRowY() + ui(7), hexInputWidth() - 8, 22,
                String.format(Locale.ROOT, "#%06X", currentRgb()), 7, "HEX");
        lastHex = hexBox.getValue();

        click(left, dimensionFieldY(), nameWidth, ui(36), this::toggleDimensionMenu);
        int labelToggleX = contentLeft + formWidth - ui(50);
        click(labelToggleX, visibilityY + ui(60), ui(34), ui(22), () -> labelEnabled = !labelEnabled);
        click(labelToggleX, visibilityY + ui(107), ui(34), ui(22), () -> beamEnabled = !beamEnabled);
        click(colorX + ui(18), chromaY(), colorWidth - ui(36), ui(43), () -> chroma = !chroma);
        click(eyeX(), hexRowY(), ui(34), ui(36), this::pickBlockColor);
        for (int i = 0; i < RECENT.size(); i++) {
            final int index = i;
            click(presetX(i) - ui(2), presetY(i) - ui(2), ui(25), ui(25), () -> pickRecent(index));
        }
        int formRight = contentLeft + formWidth;
        if (original == null) {
            click(formRight - 118, actionY, 118, ui(36), this::save);
            click(formRight - 180, actionY, 58, ui(36), this::back);
        } else {
            click(formRight - 116, actionY, 116, ui(36), this::save);
            click(formRight - 236, actionY, 122, ui(36), this::back);
        }
        if (original != null) click(contentLeft + formWidth - 96, actionY + ui(58), 80, ui(31), this::deleteWaypoint);

        refreshPickerTexture();
        ensureHueTexture();
        updateHexBoxPosition();
    }

    private void toggleDimensionMenu() {
        dimensionMenuOpen = !dimensionMenuOpen;
        updateHexBoxPosition();
    }

    private void updateHexBoxPosition() {
        if (nameBox != null) nameBox.setY(nameFieldY() + ui(7));
        if (xBox != null) xBox.setY(coordinateFieldsY() + ui(7));
        if (yBox != null) yBox.setY(coordinateFieldsY() + ui(7));
        if (zBox != null) zBox.setY(coordinateFieldsY() + ui(7));
        if (hexBox != null) hexBox.setY(hexRowY() + ui(7));
    }

    private void click(int x, int y, int w, int h, Runnable action) {
        hitTargets.add(new HitTarget(x, y, w, h, action));
    }
    private EditBox makeBox(EditBox old, int x, int y, int w, int h, String initial, int maxLength, String label) {
        EditBox box = new EditBox(this.font, x, y, w, h, Component.literal(label));
        box.setMaxLength(maxLength);
        box.setValue(old != null ? old.getValue() : initial);
        box.setBordered(false);
        box.setTextColor(0x00000000);
        int viewTop = contentViewTop();
        int viewBottom = contentViewBottom();
        if (y >= viewTop && y + h <= viewBottom) this.addRenderableWidget(box);
        return box;
    }

    private void useCurrentPosition() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;
        BlockPos pos = mc.player.blockPosition();
        xBox.setValue(Integer.toString(pos.getX()));
        yBox.setValue(Integer.toString(pos.getY()));
        zBox.setValue(Integer.toString(pos.getZ()));
        dimMode = -1;
        keepDimension = WaypointsMod.currentDimension();
        error = "Current position added.";
    }

    private void useLookedAtBlock() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.hitResult == null || mc.hitResult.getType() != net.minecraft.world.phys.HitResult.Type.BLOCK) {
            error = "Look at a block first, then choose this button.";
            return;
        }
        BlockPos pos = ((net.minecraft.world.phys.BlockHitResult) mc.hitResult).getBlockPos();
        xBox.setValue(Integer.toString(pos.getX()));
        yBox.setValue(Integer.toString(pos.getY()));
        zBox.setValue(Integer.toString(pos.getZ()));
        dimMode = -1;
        keepDimension = WaypointsMod.currentDimension();
        error = "Block position added.";
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
        if (event.button() == 1 && dimensionMenuOpen) {
            int left = formLeft();
            int optionW = formWidth - ui(36);
            int menuY = dimensionFieldY() + ui(37);
            int optionH = ui(28);
            int viewTop = contentViewTop();
            int viewBottom = contentViewBottom();
            if (event.y() >= viewTop && event.y() <= viewBottom
                    && inside(event.x(), event.y(), left, menuY, optionW, optionH * DIM_LABELS.length)) {
                int selected = Math.max(0, Math.min(DIM_LABELS.length - 1, ((int)event.y() - menuY) / optionH));
                dimMode = selected;
                dimensionMenuOpen = false;
                return true;
            }
            dimensionMenuOpen = false;
            // Consume outside clicks while closing the popup. This prevents a click
            // from passing through to the color controls underneath it.
            return true;
        }
        if (event.button() == 1) {
            double mx = event.x(), my = event.y();
            if (inside(mx, my, pickerX(), pickerY(), pickerWidth(), pickerHeight())) {
                draggingSvPicker = true; draggingHuePicker = false; setDragging(true);
                pickerPointerX = mx; pickerPointerY = my; updateSvPicker(mx, my); return true;
            }
            if (inside(mx, my, hueX(), hueY() - ui(3), pickerWidth(), hueDisplayHeight() + ui(6))) {
                draggingHuePicker = true; draggingSvPicker = false; setDragging(true);
                pickerPointerX = mx; pickerPointerY = my; updateHuePicker(mx); return true;
            }
            int viewTop = contentViewTop();
            int viewBottom = contentViewBottom();
            for (int i = hitTargets.size() - 1; i >= 0; i--) {
                HitTarget t = hitTargets.get(i);
                boolean sidebar = t.x() < railWidth;
                boolean breadcrumb = t.y() < viewTop;
                if ((sidebar || breadcrumb || (my >= viewTop && my <= viewBottom)) && t.contains(mx, my)) {
                    t.action().run();
                    return true;
                }
            }
            if (inside(mx, my, colorX, cardTop, colorWidth, colorCardHeight())) return true;
        }
        return super.mouseClicked(event, doubleClick);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (dimensionMenuOpen) { dimensionMenuOpen = false; return true; }
        int viewTop = WaypointUiChrome.topHeight(height) + ui(52);
        int viewBottom = contentViewBottom();
        if (scrollY == 0 || mouseX < railWidth || mouseX > width - 8 || mouseY < viewTop || mouseY > viewBottom)
            return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
        if (maxScroll > 0) {
            int next = Math.max(0, Math.min(maxScroll, scrollOffset - (int)Math.round(scrollY * 64.0)));
            if (next != scrollOffset) {
                scrollOffset = next;
                dimensionMenuOpen = false;
                init(); // Rebuild native input widgets at their new scrolled positions.
            }
        }
        return true;
    }

    private boolean contentVisible(int y, int h) {
        int viewTop = contentViewTop();
        int viewBottom = contentViewBottom();
        return y >= viewTop && y + h <= viewBottom;
    }

    private void unfocusIfOutside(EditBox box) {
        // Native EditBoxes are only registered while they intersect the visible content viewport.
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
     * Builds a high-density saturation/value square once for the current hue and uploads
     * it only when the hue changes. The old implementation queued 8,192 fill calls every frame,
     * which made the edit screen noticeably laggy.
     */
    private void refreshPickerTexture() {
        if (pickerTexture == null) {
            pickerTexture = WaypointUiText.createSmoothTexture("simple-waypoints-color-picker",
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

        hueTexture = WaypointUiText.createSmoothTexture("simple-waypoints-hue-strip",
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
        String hex = String.format(Locale.ROOT, "#%06X", currentRgb());
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
    private void pickBlockColor() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.hitResult == null
                || mc.hitResult.getType() != net.minecraft.world.phys.HitResult.Type.BLOCK) {
            error = "Look at a block to sample its map color.";
            return;
        }
        BlockPos pos = ((net.minecraft.world.phys.BlockHitResult) mc.hitResult).getBlockPos();
        int sampled = mc.level.getBlockState(pos).getMapColor(mc.level, pos).col;
        setFromRgb(sampled);
        chroma = false;
        syncHex();
        error = "Block color sampled.";
    }

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
        String marker = ICON_MARKERS[Math.max(0, Math.min(ICON_MARKERS.length - 1, selectedIconIndex))];

        // Keep an existing temporary waypoint's expiry intact; newly created waypoints are permanent.
        long expires = original != null ? original.expiresAt() : 0L;
        String dim = dimMode >= 0 ? DIM_IDS[dimMode] : keepDimension;
        String worldId = original != null ? original.worldId() : WaypointsMod.currentWorldId();

        WaypointsMod.upsert(original, new Waypoint(name, x, y, z, color, dim, expires, worldId, marker, labelEnabled, beamEnabled));

        if (!chroma) {
            Integer rgb = color;
            RECENT.remove(rgb);
            RECENT.add(0, rgb);
            while (RECENT.size() > 7) RECENT.remove(RECENT.size() - 1);
        }
        back();
    }


    private void deleteWaypoint() {
        if (original != null) {
            WaypointsMod.remove(original);
            Minecraft.getInstance().gui.setScreen(new WaypointScreen(Math.max(0, returnPage)));
        }
    }

    private String colorName(int rgb) {
        int[][] named = {{0xB7F36B,0},{0x59B7FF,1},{0xA98AFF,2},{0xFFB65C,3},{0x55D6B5,4},{0xF17A91,5},{0xE48BCB,6}};
        String[] names = {"Lime","Blue","Violet","Orange","Mint","Rose","Pink"};
        int best = 0, dist = Integer.MAX_VALUE;
        for (int i=0;i<named.length;i++) { int v=named[i][0]; int d=Math.abs(((rgb>>16)&255)-((v>>16)&255))+Math.abs(((rgb>>8)&255)-((v>>8)&255))+Math.abs((rgb&255)-(v&255)); if(d<dist){dist=d;best=i;} }
        return names[best];
    }

    // ------------------------------------------------------------------ drawing
    @Override
    public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float delta) {
        if (draggingSvPicker || draggingHuePicker) updatePickerFromLiveMouse();
        String curHex = hexBox.getValue();
        if (!curHex.equals(lastHex)) { lastHex = curHex; applyHex(curHex); }

        WaypointUiChrome.drawFrame(g, font, width, height, mouseX, mouseY, false, "Waypoints");
        int left = contentLeft;
        int top = WaypointUiChrome.topHeight(height);
        WaypointUiText.drawCompact(g, font, "‹   All waypoints   /   " + (original == null ? "New waypoint" : original.name()),
                contentLeft, top + formOffset(31, 37), WaypointUiChrome.MUTED);
        String heading = original == null ? "Mark somewhere new." : "Edit waypoint";
        String subtitle = original == null ? "Save a location now. Find your way back whenever you need it."
                : "Fine-tune the details. Keep your favorite place within reach.";
        WaypointUiText.drawHeading(g, font, heading, contentLeft, top + formOffset(62, 72), WaypointUiChrome.TEXT);
        WaypointUiText.draw(g, font, subtitle, contentLeft, top + formOffset(94, 107), WaypointUiChrome.MUTED, false);
        if (original != null) {
            WaypointUiChrome.round(g, contentRight - 104, top + 80, contentRight, top + 104, 0xFF1B2415, 6);
            WaypointUiText.drawCompact(g, font, "•  Unsaved changes", contentRight - 96, top + 87, WaypointUiChrome.GREEN);
        }

        int viewTop = contentViewTop();
        int viewBottom = contentViewBottom();
        g.enableScissor(contentLeft - 2, viewTop, width - 12, viewBottom);
        int l = formLeft();
        int nameW = formWidth - 36;
        int coordGap = 10;
        int cw = (nameW - coordGap * 2) / 3;
        // Location details panel.
        WaypointUiChrome.panel(g, contentLeft, cardTop, formWidth, locationCardHeight);
        WaypointUiText.draw(g, font, "⌖  Location details", l, cardTop + ui(22), WaypointUiChrome.TEXT, true);
        WaypointUiText.drawCompact(g, font, "Waypoint name", l, cardTop + formOffset(55, 61), WaypointUiChrome.TEXT);
        drawField(g, nameBox, l, nameFieldY(), nameW, ui(36));
        WaypointUiText.drawCompact(g, font, "A memorable name for a memorable place.", l, cardTop + formOffset(112, 120), WaypointUiChrome.QUIET);
        WaypointUiText.drawCompact(g, font, "X", l, cardTop + formOffset(140, 156), WaypointUiChrome.TEXT);
        WaypointUiText.drawCompact(g, font, "Y", l + cw + coordGap, cardTop + formOffset(140, 156), WaypointUiChrome.TEXT);
        WaypointUiText.drawCompact(g, font, "Z", l + (cw + coordGap) * 2, cardTop + formOffset(140, 156), WaypointUiChrome.TEXT);
        drawField(g, xBox, l, coordinateFieldsY(), cw, ui(36));
        drawField(g, yBox, l + cw + coordGap, coordinateFieldsY(), cw, ui(36));
        drawField(g, zBox, l + (cw + coordGap) * 2, coordinateFieldsY(), cw, ui(36));
        WaypointUiText.drawCompact(g, font, "ⓘ  Use your in-game coordinates. Press F3 to find them.", l, cardTop + formOffset(200, 220), WaypointUiChrome.QUIET);
        WaypointUiText.drawCompact(g, font, "Dimension", l, cardTop + formOffset(230, 251), WaypointUiChrome.TEXT);
        WaypointUiChrome.field(g, l, dimensionFieldY(), nameW, ui(36), dimensionMenuOpen);
        WaypointUiChrome.round(g, l + ui(12), dimensionFieldY() + ui(14), l + ui(19), dimensionFieldY() + ui(21),
                dimensionIndicatorColor(dimMode), ui(4));
        WaypointUiText.draw(g, font, dimensionName(dimMode >= 0 ? DIM_IDS[dimMode] : keepDimension), l + ui(27), dimensionFieldY() + ui(12), WaypointUiChrome.TEXT, false);
        WaypointUiText.draw(g, font, "⌄", l + nameW - ui(22), dimensionFieldY() + ui(11), WaypointUiChrome.MUTED, false);
        WaypointUiText.drawCompact(g, font, "Only visible when you're in this dimension.", l, dimensionFieldY() + formOffset(40, 43), WaypointUiChrome.QUIET);

        // Visibility card.
        WaypointUiChrome.panel(g, contentLeft, visibilityY, formWidth, visibilityHeight);
        WaypointUiText.draw(g, font, "Make it visible", l, visibilityY + ui(21), WaypointUiChrome.TEXT, true);
        WaypointUiText.draw(g, font, "Show waypoint label", l, visibilityY + ui(56), WaypointUiChrome.TEXT, false);
        WaypointUiText.drawCompact(g, font, "Display the name and distance in your world.", l, visibilityY + ui(76), WaypointUiChrome.MUTED);
        drawScaledToggle(g, contentLeft + formWidth - ui(44), visibilityY + ui(60), labelEnabled);
        WaypointUiText.draw(g, font, "Show beacon beam", l, visibilityY + ui(103), WaypointUiChrome.TEXT, false);
        WaypointUiText.drawCompact(g, font, "A vertical beam to guide you from a distance.", l, visibilityY + ui(123), WaypointUiChrome.MUTED);
        drawScaledToggle(g, contentLeft + formWidth - ui(44), visibilityY + ui(107), beamEnabled);

        // Waypoint color workstation.
        WaypointUiChrome.panel(g, colorX, cardTop, colorWidth, colorCardHeight());
        WaypointUiText.draw(g, font, "Waypoint color", colorX + ui(18), cardTop + ui(22), WaypointUiChrome.TEXT, false);
        String colorName = chroma ? "Chroma" : colorName(currentRgb());
        g.fill(colorX + colorWidth - ui(64), cardTop + ui(25), colorX + colorWidth - ui(59), cardTop + ui(30), currentRgb() | 0xFF000000);
        WaypointUiText.drawCompact(g, font, colorName, colorX + colorWidth - ui(54), cardTop + ui(22), WaypointUiChrome.MUTED);
        refreshPickerTexture();
        g.blit(PICKER_TEXTURE_ID, pickerX(), pickerY(), pickerX() + pickerWidth(), pickerY() + pickerHeight(), 0f, 1f, 0f, 1f);
        WaypointUiChrome.border(g, pickerX(), pickerY(), pickerX() + pickerWidth(), pickerY() + pickerHeight(), WaypointUiChrome.EDGE);
        int markX = pickerX() + Math.round(sat * (pickerWidth() - 1));
        int markY = pickerY() + Math.round((1f - val) * (pickerHeight() - 1));
        ring(g, markX, markY);
        ensureHueTexture();
        g.blit(HUE_TEXTURE_ID, hueX(), hueY(), hueX() + pickerWidth(), hueY() + hueDisplayHeight(), 0f, 1f, 0f, 1f);
        int hueMark = hueX() + Math.round(hue * (pickerWidth() - 1));
        g.fill(hueMark - 1, hueY() - ui(2), hueMark + ui(2), hueY() + hueDisplayHeight() + ui(2), 0xFFFFFFFF);

        // Hex input, eye dropper and chroma mode.
        WaypointUiChrome.round(g, colorX + ui(18), hexRowY(), hexInputX() - ui(8), hexRowY() + ui(36), currentRgb() | 0xFF000000, ui(6));
        WaypointUiChrome.field(g, hexInputX(), hexRowY(), hexInputWidth(), ui(36), hexBox.isFocused());
        WaypointUiText.drawCompact(g, font, "HEX", hexInputX() + ui(10), hexRowY() + ui(12), WaypointUiChrome.QUIET);
        WaypointUiChrome.round(g, eyeX(), hexRowY(), eyeX() + ui(28), hexRowY() + ui(36), WaypointUiChrome.FIELD, ui(6));
        WaypointUiChrome.border(g, eyeX(), hexRowY(), eyeX() + ui(28), hexRowY() + ui(36), WaypointUiChrome.EDGE);
        drawDropper(g, eyeX() + ui(8), hexRowY() + ui(12), WaypointUiChrome.MUTED);
        WaypointUiChrome.round(g, colorX + ui(18), chromaY(), colorX + colorWidth - ui(18), chromaY() + ui(43),
                chroma ? 0xFF222D1A : 0xFF191D20, 7);
        WaypointUiChrome.border(g, colorX + ui(18), chromaY(), colorX + colorWidth - ui(18), chromaY() + ui(43),
                chroma ? WaypointUiChrome.GREEN : WaypointUiChrome.EDGE);
        WaypointUiText.draw(g, font, "✧", colorX + ui(30), chromaY() + ui(13), WaypointUiChrome.MUTED, false);
        WaypointUiText.draw(g, font, "Chroma mode", colorX + ui(52), chromaY() + ui(7), WaypointUiChrome.TEXT, false);
        WaypointUiText.drawCompact(g, font, "Cycle through the spectrum", colorX + ui(52), chromaY() + ui(23), WaypointUiChrome.MUTED);
        drawScaledToggle(g, colorX + colorWidth - ui(42), chromaY() + ui(13), chroma);
        WaypointUiText.drawCompact(g, font, "Recent colors", colorX + ui(18), recentLabelY(), WaypointUiChrome.MUTED);
        for (int i = 0; i < RECENT.size(); i++) {
            int sx = presetX(i), sy = presetY(i);
            WaypointUiChrome.round(g, sx, sy, sx + ui(23), sy + ui(23), RECENT.get(i) | 0xFF000000, 5);
            if (!chroma && currentRgb() == (RECENT.get(i) & 0xFFFFFF)) WaypointUiChrome.border(g, sx - ui(2), sy - ui(2), sx + ui(25), sy + ui(25), 0xFFFFFFFF);
        }

        // In-game preview panel with the thin topographic line pattern from the reference.
        int previewY = previewPanelY();
        WaypointUiText.drawCompact(g, font, "IN-GAME PREVIEW", colorX, cardTop + colorCardHeight() + ui(12), WaypointUiChrome.QUIET);
        drawPreview(g, colorX, previewY, colorWidth, previewPanelHeight(), currentRgb(), beamEnabled, labelEnabled);
        if (height >= 650) {
            WaypointUiText.drawCompact(g, font, "A clear marker, right where you need it.", colorX, previewY + previewPanelHeight() + ui(10), WaypointUiChrome.QUIET);
        }

        // Actions and destructive operation.
        int formRight = contentLeft + formWidth;
        if (original == null) {
            WaypointUiChrome.button(g, font, mouseX, mouseY, formRight - 180, actionY, 58, ui(36), "Cancel", false, false);
            WaypointUiChrome.button(g, font, mouseX, mouseY, formRight - 118, actionY, 118, ui(36), "+  Create waypoint", true, false);
        } else {
            WaypointUiChrome.button(g, font, mouseX, mouseY, formRight - 236, actionY, 122, ui(36), "Discard changes", false, false);
            WaypointUiChrome.button(g, font, mouseX, mouseY, formRight - 116, actionY, 116, ui(36), "✓  Save changes", true, false);
        }
        if (original == null) {
            WaypointUiText.drawCompact(g, font, "♧  This waypoint is saved to " + WaypointUiChrome.fit(font, WaypointUiChrome.worldName(), 90)
                    + " and stored on this device. Your coordinates stay yours.", contentLeft + 2, visibilityY + visibilityHeight + ui(12), WaypointUiChrome.QUIET);
        }
        if (original != null) {
            int dy = actionY + ui(48);
            int dh = ui(52);
            int buttonY = dy + ui(10);
            WaypointUiChrome.panel(g, contentLeft, dy, formWidth, dh);
            WaypointUiText.draw(g, font, "Delete waypoint", contentLeft + ui(16), dy + ui(10), WaypointUiChrome.RED, false);
            WaypointUiText.drawCompact(g, font, "Permanently remove " + WaypointUiChrome.fit(font, original.name(), 150) + ". This can't be undone.",
                    contentLeft + ui(16), dy + ui(32), WaypointUiChrome.MUTED);
            WaypointUiChrome.button(g, font, mouseX, mouseY, contentLeft + formWidth - 96, buttonY, 80, ui(31), "▣  Delete", false, true);
        }
        if (!error.isEmpty()) WaypointUiText.drawCompact(g, font, error, contentLeft, actionY - 17, WaypointUiChrome.RED);
        g.disableScissor();
        super.extractRenderState(g, mouseX, mouseY, delta);
        if (contentVisible(nameFieldY() + ui(12), ui(16))) WaypointUiText.drawInput(g, font, nameBox, l + ui(12), nameFieldY() + ui(12), nameW - ui(24));
        if (contentVisible(coordinateFieldsY() + ui(12), ui(16))) {
            WaypointUiText.drawInput(g, font, xBox, l + ui(12), coordinateFieldsY() + ui(12), cw - ui(24));
            WaypointUiText.drawInput(g, font, yBox, l + cw + coordGap + ui(12), coordinateFieldsY() + ui(12), cw - ui(24));
            WaypointUiText.drawInput(g, font, zBox, l + (cw + coordGap) * 2 + ui(12), coordinateFieldsY() + ui(12), cw - ui(24));
        }
        if (contentVisible(hexRowY() + ui(12), ui(16))) WaypointUiText.drawInput(g, font, hexBox, hexInputX() + ui(48), hexRowY() + ui(12), Math.max(24, hexInputWidth() - ui(60)));

        if (dimensionMenuOpen && contentVisible(dimensionFieldY(), ui(36))) {
            g.enableScissor(contentLeft - 2, viewTop, width - 12, viewBottom);
            int menuY = dimensionFieldY() + ui(37);
            int optH = ui(28);
            int optW = formWidth - ui(36);
            WaypointUiChrome.round(g, l, menuY, l + optW, menuY + optH * 3, 0xFF0A0C0D, ui(7));
            WaypointUiChrome.border(g, l, menuY, l + optW, menuY + optH * 3, WaypointUiChrome.EDGE);
            for (int i = 0; i < 3; i++) {
                if (inside(mouseX, mouseY, l, menuY + i * optH, optW, optH))
                    WaypointUiChrome.round(g, l + 2, menuY + i * optH + ui(1), l + optW - ui(2), menuY + (i + 1) * optH - ui(1), 0xFF202629, 5);
                WaypointUiChrome.round(g, l + ui(12), menuY + i * optH + ui(10),
                        l + ui(19), menuY + i * optH + ui(17), dimensionIndicatorColor(i), ui(4));
                WaypointUiText.draw(g, font, DIM_LABELS[i], l + ui(27), menuY + i * optH + ui(8),
                        i == dimMode ? WaypointUiChrome.GREEN : WaypointUiChrome.TEXT, false);
                if (i == dimMode) WaypointUiText.draw(g, font, "✓", l + optW - ui(24), menuY + i * optH + ui(7), WaypointUiChrome.GREEN, false);
            }
            g.disableScissor();
        }
    }

    private void drawScaledToggle(GuiGraphicsExtractor g, int x, int y, boolean on) {
        int w = ui(26), h = ui(14), pad = ui(2), knob = ui(10);
        WaypointUiChrome.round(g, x, y, x + w, y + h, on ? WaypointUiChrome.GREEN : 0xFF3B4146, Math.max(4, h / 2));
        int knobX = on ? x + w - pad - knob : x + pad;
        WaypointUiChrome.round(g, knobX, y + pad, knobX + knob, y + h - pad,
                on ? 0xFF15200E : 0xFF9AA0A4, Math.max(3, (h - pad * 2) / 2));
    }

    private void drawField(GuiGraphicsExtractor g, EditBox box, int x, int y, int w, int h) {
        WaypointUiChrome.field(g, x, y, w, h, box != null && box.isFocused());
    }

    private void drawPreview(GuiGraphicsExtractor g, int x, int y, int w, int h, int color, boolean showBeacon, boolean showLabel) {
        WaypointUiChrome.panel(g, x, y, w, h);
        // The caller already clips the complete editor workspace. Do not start and
        // then disable a nested scissor here; GuiGraphicsExtractor does not restore
        // the outer clip when disableScissor() is called.
        WaypointUiChrome.drawContour(g, x + 1, y + 1, w - 2, h - 2, false);
        int cx = x + w / 2, markerY = y + h / 2 - ui(10);
        if (showBeacon) g.fill(cx, y + 1, cx + 1, markerY, color | 0xFF000000);
        int markerHalfW = ui(17), markerTop = ui(15), markerBottom = ui(19);
        WaypointUiChrome.round(g, cx - markerHalfW, markerY - markerTop, cx + markerHalfW, markerY + markerBottom, 0xFF121716, ui(9));
        WaypointUiChrome.border(g, cx - markerHalfW, markerY - markerTop, cx + markerHalfW, markerY + markerBottom, color | 0xFF000000);
        WaypointUiText.draw(g, font, "⌖", cx - ui(6), markerY - ui(5), color | 0xFF000000, false);
        if (showLabel) {
            int labelHalfW = Math.min(ui(61), Math.max(32, (w - ui(20)) / 2));
            WaypointUiChrome.round(g, cx - labelHalfW, markerY + ui(22), cx + labelHalfW, markerY + ui(57), 0xF0080A0B, ui(6));
            int labelSize = WaypointsMod.markerFontSize();
            String label = fitPreviewLabel(nameBox.getValue(), labelHalfW * 2 - ui(12), labelSize);
            int labelWidth = WaypointUiText.widthAtSize(font, label, labelSize);
            int labelY = markerY + ui(28) + (labelSize >= 12 ? ui(1) : 0);
            WaypointUiText.drawAtSize(g, font, label, cx - labelWidth / 2, labelY, color | 0xFF000000, labelSize);
            int px = parseCoordinate(xBox, 0), py = parseCoordinate(yBox, 0), pz = parseCoordinate(zBox, 0);
            Minecraft mc = Minecraft.getInstance();
            long blocks = mc.player == null ? 0L : Math.round(Math.sqrt(
                    Math.pow(mc.player.getX() - (px + 0.5), 2) + Math.pow(mc.player.getY() - py, 2)
                            + Math.pow(mc.player.getZ() - (pz + 0.5), 2)));
            String xyz = px + ", " + py + ", " + pz + " · " + blocks + "m";
            int metaSize = Math.max(8, labelSize - 1);
            String shown = fitPreviewLabel(xyz, w - ui(24), metaSize);
            WaypointUiText.drawAtSize(g, font, shown, cx - WaypointUiText.widthAtSize(font, shown, metaSize) / 2,
                    markerY + ui(43), WaypointUiChrome.MUTED, metaSize);
        }
    }

    private void drawDropper(GuiGraphicsExtractor g, int x, int y, int color) {
        // Small eyedropper silhouette, drawn from crisp one-pixel strokes.
        g.fill(x + 5, y, x + 8, y + 3, color);
        g.fill(x + 3, y + 2, x + 6, y + 5, color);
        g.fill(x + 1, y + 4, x + 4, y + 7, color);
        g.fill(x, y + 7, x + 2, y + 9, color);
        g.fill(x - 1, y + 9, x + 1, y + 11, color);
    }

    private int dimensionIndicatorColor(int mode) {
        if (mode < 0) {
            String raw = WaypointsMod.normalizeDimension(keepDimension);
            if (raw.endsWith(":overworld")) mode = 0;
            else if (raw.endsWith(":the_nether")) mode = 1;
            else if (raw.endsWith(":the_end")) mode = 2;
        }
        return switch (mode) {
            case 0 -> 0xFFB7F36B; // Overworld
            case 1 -> 0xFFFFB65C; // Nether
            case 2 -> 0xFFA98AFF; // End
            default -> WaypointUiChrome.MUTED;
        };
    }

    private String fitPreviewLabel(String raw, int maxWidth, int size) {
        String original = raw == null || raw.isBlank() ? "Waypoint" : raw.trim();
        String value = original;
        while (value.length() > 1 && WaypointUiText.widthAtSize(font, value, size) > maxWidth) {
            value = value.substring(0, value.length() - 1);
        }
        return value.equals(original) ? value : value + "…";
    }

    private String dimensionName(String raw) {
        String dimension = WaypointsMod.normalizeDimension(raw);
        if (dimension.endsWith(":the_nether")) return "Nether";
        if (dimension.endsWith(":the_end")) return "The End";
        if (dimension.endsWith(":overworld")) return "Overworld";
        return "Current dimension";
    }

    private int parseCoordinate(EditBox box, int fallback) {
        try { return Integer.parseInt(box.getValue().trim()); }
        catch (NumberFormatException ignored) { return fallback; }
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
