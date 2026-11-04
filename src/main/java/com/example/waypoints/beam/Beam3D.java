package com.example.waypoints.beam;

import com.example.waypoints.Waypoint;
import com.example.waypoints.WaypointsMod;
import com.mojang.blaze3d.vertex.PoseStack;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelExtractionContext;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelExtractionEvents;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.blockentity.BeaconRenderer;
import net.minecraft.client.gui.Font;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;

import java.util.ArrayList;
import java.util.List;

/**
 * Renders waypoint beams through Minecraft's native BeaconRenderer submit path.
 *
 * Each beam is centered at the waypoint's X/Z and runs through the full world
 * column. It uses the vanilla beacon texture, animation, inner/outer radii and
 * 2048-block segment height. Beam range, width scaling, and animation timing
 * follow BeaconRenderer's Minecraft 26.3 implementation.
 */
public final class Beam3D {
    private static final int MAX_RENDERED_BEAMS = 12;
    private static volatile List<BeamState> beams = List.of();
    private static volatile List<LabelState> labels = List.of();
    private static volatile boolean failed = false;
    private static final int MAX_RENDERED_LABELS = 16;
    private static final int FULL_BRIGHT = 0x00F000F0;
    private static final int LABEL_BACKGROUND = 0x66000000;

    private Beam3D() {
    }

    public static void init() {
        LevelExtractionEvents.END_EXTRACTION.register(Beam3D::extract);
        LevelRenderEvents.COLLECT_SUBMITS.register(Beam3D::renderAndDraw);
        WaypointsMod.beam3dActive = true;
    }

    private static void fail(Throwable t) {
        if (!failed) {
            failed = true;
            WaypointsMod.beam3dActive = false;
            System.err.println("[Simple Waypoints] Native beacon beam renderer failed:");
            t.printStackTrace();
        }
        beams = List.of();
        labels = List.of();
    }

    private static void extract(LevelExtractionContext context) {
        if (failed) return;

        try {
            Minecraft mc = Minecraft.getInstance();
            if (mc.level == null || mc.player == null) {
                beams = List.of();
                labels = List.of();
                return;
            }

            boolean renderBeams = WaypointsMod.showWaypointBeams;
            boolean renderLabels = WaypointsMod.showWaypointLabels;
            if (!renderBeams && !renderLabels) {
                beams = List.of();
                labels = List.of();
                return;
            }

            Vec3 cameraPosition = mc.player.getEyePosition();
            double[] camera = WaypointsMod.cameraState(mc);
            if (camera != null) {
                cameraPosition = new Vec3(camera[0], camera[1], camera[2]);
            }
            Vec3 playerPosition = mc.player.position();

            List<BeamState> beamCandidates = new ArrayList<>();
            List<LabelState> labelCandidates = new ArrayList<>();
            double maxRenderDistance = mc.options.getEffectiveRenderDistance() * 16.0;
            double maxRenderDistanceSquared = maxRenderDistance * maxRenderDistance;

            for (Waypoint waypoint : WaypointsMod.currentContextWaypoints()) {
                double wx = waypoint.x() + 0.5;
                double wy = waypoint.y() + 0.5;
                double wz = waypoint.z() + 0.5;
                double dx = wx - cameraPosition.x;
                double dy = wy - cameraPosition.y;
                double dz = wz - cameraPosition.z;
                double distanceSquared = dx * dx + dy * dy + dz * dz;
                double horizontalDistanceSquared = dx * dx + dz * dz;
                if (distanceSquared > maxRenderDistanceSquared) continue;

                if (renderLabels) {
                    double playerDistance = Math.sqrt(playerPosition.distanceToSqr(wx, wy, wz));
                    // Is terrain between you and the label? Then the label is drawn through it (so you can
                    // find your way back); with a clear view it stays depth-tested like a vanilla name tag.
                    boolean blocked = isBlockedByTerrain(mc, cameraPosition, new Vec3(wx, waypoint.y() + 1.85, wz));
                    labelCandidates.add(new LabelState(waypoint, playerDistance, distanceSquared, blocked));
                }

                if (renderBeams && horizontalDistanceSquared <= maxRenderDistanceSquared) {
                    double horizontalDistance = Math.sqrt(horizontalDistanceSquared);
                    // Match BeaconRenderer.extract(): scoped players use normal width;
                    // otherwise the beam widens past 96 horizontal blocks.
                    float radiusScale = mc.player.isScoping()
                            ? 1.0f
                            : (float) Math.max(1.0, horizontalDistance / 96.0);
                    int color = 0xFF000000 | (WaypointsMod.resolveColor(waypoint) & 0x00FFFFFF);
                    beamCandidates.add(new BeamState(
                            waypoint.x(),
                            waypoint.z(),
                            color,
                            radiusScale,
                            horizontalDistanceSquared
                    ));
                }
            }

            beamCandidates.sort(java.util.Comparator.comparingDouble(BeamState::distanceSquared));
            if (beamCandidates.size() > MAX_RENDERED_BEAMS) {
                beamCandidates.subList(MAX_RENDERED_BEAMS, beamCandidates.size()).clear();
            }
            labelCandidates.sort(java.util.Comparator.comparingDouble(LabelState::distanceSquared));
            if (labelCandidates.size() > MAX_RENDERED_LABELS) {
                labelCandidates.subList(MAX_RENDERED_LABELS, labelCandidates.size()).clear();
            }
            beams = List.copyOf(beamCandidates);
            labels = List.copyOf(labelCandidates);
        } catch (Throwable t) {
            fail(t);
        }
    }

    /** True when a solid block lies between the two points (checked every frame, no delay). */
    private static boolean isBlockedByTerrain(Minecraft mc, Vec3 from, Vec3 to) {
        try {
            var hit = mc.level.clip(new ClipContext(from, to, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, mc.player));
            return hit.getType() != HitResult.Type.MISS;
        } catch (Throwable t) {
            return false;
        }
    }

    private static void renderAndDraw(LevelRenderContext context) {
        if (failed) return;

        try {
            List<BeamState> beamStates = beams;
            List<LabelState> labelStates = labels;
            if (beamStates.isEmpty() && labelStates.isEmpty()) return;

            var cameraState = context.levelState().cameraRenderState;
            Vec3 camera = cameraState.pos;
            if (camera == null) return;

            Minecraft mc = Minecraft.getInstance();
            if (mc.level == null) return;

            PoseStack poseStack = context.poseStack();
            var collector = context.submitNodeCollector();
            int minBuildY = mc.level.getMinY();

            if (WaypointsMod.showWaypointBeams) {
                // Copy BeaconRenderer.extract() timing exactly. Vanilla wraps at 40 ticks
                // and adds the current partial tick; passing a huge game-time float loses
                // precision in the rotating core and scrolling texture.
                float animationTime = Math.floorMod(mc.level.getGameTime(), 40L)
                        + mc.getDeltaTracker().getGameTimeDeltaPartialTick(false);

                for (BeamState beam : beamStates) {
                    poseStack.pushPose();
                    // BeaconRenderer itself adds +0.5 X/Z to center the column.
                    poseStack.translate(
                            beam.x() - camera.x,
                            minBuildY - camera.y,
                            beam.z() - camera.z
                    );
                    BeaconRenderer.submitBeaconBeam(
                            poseStack,
                            collector,
                            BeaconRenderer.BEAM_LOCATION,
                            1.0f,
                            animationTime,
                            0,
                            BeaconRenderer.MAX_RENDER_Y,
                            beam.rgb(),
                            BeaconRenderer.SOLID_BEAM_RADIUS * beam.radiusScale(),
                            BeaconRenderer.BEAM_GLOW_RADIUS * beam.radiusScale()
                    );
                    poseStack.popPose();
                }
            }

            if (WaypointsMod.showWaypointLabels) {
                if (!loggedLabels && !labelStates.isEmpty()) {
                    loggedLabels = true;
                    System.out.println("[Simple Waypoints] submitting " + labelStates.size() + " world-space label(s)");
                }
                for (LabelState label : labelStates) {
                    submitDepthTestedLabel(poseStack, collector, cameraState, mc.font, label);
                }
            }
        } catch (Throwable t) {
            fail(t);
        }
    }

    private static boolean loggedLabels = false;

    /**
     * World-space label, drawn like a vanilla name tag.
     *  - Clear view: everything is depth-tested, so mobs, items, players and blocks in front hide it.
     *  - Terrain in the way: it is drawn through the terrain, so it can always guide you back.
     *  - Size: small when you are close, growing with distance so it stays big and readable far away.
     *  - Outline: a border in the same color as the waypoint's beam (rainbow follows the beam).
     */
    private static void submitDepthTestedLabel(PoseStack poseStack,
                                                net.minecraft.client.renderer.SubmitNodeCollector collector,
                                                net.minecraft.client.renderer.state.level.CameraRenderState camera,
                                                Font font,
                                                LabelState label) {
        Waypoint waypoint = label.waypoint();
        Vec3 cameraPos = camera.pos;
        if (cameraPos == null || camera.orientation == null) return;

        String name = fitLabelName(font, waypoint.name());
        String distanceLine = formatDistance(label.playerDistance()) + " away";
        int target = Math.max(font.width(name), font.width(distanceLine)) + 8;
        String nameLine = padToWidth(font, name, target);
        String distLine = padToWidth(font, distanceLine, target);
        int nameWidth = font.width(nameLine);
        int distWidth = font.width(distLine);
        int plateWidth = Math.max(nameWidth, distWidth);

        double lx = waypoint.x() + 0.5;
        double ly = waypoint.y() + 1.85;
        double lz = waypoint.z() + 0.5;
        // The beam runs through the middle of the waypoint, so move the label toward you so it is
        // in front of its own beam instead of half-hidden inside it.
        double toX = cameraPos.x - lx;
        double toZ = cameraPos.z - lz;
        double len = Math.sqrt(toX * toX + toZ * toZ);
        if (len > 0.001) {
            double push = Math.min(0.55, len * 0.5);
            lx += toX / len * push;
            lz += toZ / len * push;
        }

        // Size on screen: small up close, big far away (smooth in between).
        double dx = lx - cameraPos.x;
        double dy = ly - cameraPos.y;
        double dz = lz - cameraPos.z;
        double distance = Math.max(0.5, Math.sqrt(dx * dx + dy * dy + dz * dz));
        Minecraft mc = Minecraft.getInstance();
        double screenH = Math.max(1.0, mc.getWindow().getHeight());
        double tanHalfFov = Math.tan(Math.toRadians(mc.options.fov().get()) / 2.0);
        double t = Math.max(0.0, Math.min(1.0, (distance - 6.0) / 54.0));
        t = t * t * (3.0 - 2.0 * t);
        double nearPx = 13.0;   // height of one text line on screen when you are close
        double farPx = 34.0;    // ... and when you are far away (big, so it helps when you are lost)
        double sizeMul = switch (WaypointsMod.markerSizeMode) { case 0 -> 0.8; case 2 -> 1.25; default -> 1.0; };
        double linePx = (nearPx + (farPx - nearPx) * t) * sizeMul;
        float scale = (float) ((linePx / 9.0) * (2.0 * distance * tanHalfFov) / screenH);

        Font.DisplayMode mode = label.blocked() ? Font.DisplayMode.SEE_THROUGH : Font.DisplayMode.NORMAL;
        int outline = 0xFF000000 | (WaypointsMod.resolveColor(waypoint) & 0x00FFFFFF);

        var nameSeq = Component.literal(nameLine).getVisualOrderText();
        var distSeq = Component.literal(distLine).getVisualOrderText();

        // Plate edges in text space: two 9px strips from y=-11 to y=7.
        float half = plateWidth * 0.5f + 1.0f;
        final float top = -11.0f;
        final float bottom = 7.0f;
        final float th = 1.0f;   // outline thickness in font pixels

        // Submitted facing both ways; text that faces away from you is culled, so only one side shows.
        for (int side = 0; side < 2; side++) {
            float xScale = side == 0 ? scale : -scale;
            poseStack.pushPose();
            poseStack.translate(dx, dy, dz);
            poseStack.mulPose(new Matrix4f().rotation(camera.orientation));
            poseStack.scale(xScale, -scale, scale);

            // outline (same color as the beam)
            collector.submitTextBackground(poseStack, -half - th, top - th, half + th, top, outline, mode, FULL_BRIGHT);
            collector.submitTextBackground(poseStack, -half - th, bottom, half + th, bottom + th, outline, mode, FULL_BRIGHT);
            collector.submitTextBackground(poseStack, -half - th, top, -half, bottom, outline, mode, FULL_BRIGHT);
            collector.submitTextBackground(poseStack, half, top, half + th, bottom, outline, mode, FULL_BRIGHT);

            collector.submitText(poseStack, -nameWidth * 0.5f, -10.0f, nameSeq, false, mode,
                    FULL_BRIGHT, 0xFFFFFFFF, LABEL_BACKGROUND, 0);
            collector.submitText(poseStack, -distWidth * 0.5f, -1.0f, distSeq, false, mode,
                    FULL_BRIGHT, 0xFFD0D6DC, LABEL_BACKGROUND, 0);
            poseStack.popPose();
        }
    }

    /** Adds equal spaces on both sides until the text is about {@code target} pixels wide. */
    private static String padToWidth(Font font, String text, int target) {
        int space = Math.max(1, font.width(" "));
        StringBuilder sb = new StringBuilder(text);
        while (font.width(sb.toString()) + 2 * space <= target) {
            sb.insert(0, ' ').append(' ');
        }
        return sb.toString();
    }

    private static String fitLabelName(Font font, String rawName) {
        String name = rawName == null || rawName.isBlank() ? "Waypoint" : rawName.trim();
        final int maxWidth = 160;
        if (font.width(name) <= maxWidth) return name;
        while (name.length() > 1 && font.width(name + "…") > maxWidth) {
            name = name.substring(0, name.length() - 1);
        }
        return name + "…";
    }

    private static String formatDistance(double distance) {
        if (distance < 10.0) return (Math.round(distance * 10.0) / 10.0) + " m";
        if (distance < 1000.0) return Math.round(distance) + " m";
        if (distance < 10_000.0) return (Math.round(distance / 100.0) / 10.0) + " km";
        return Math.round(distance / 1000.0) + " km";
    }

    private record BeamState(double x, double z, int rgb, float radiusScale, double distanceSquared) {
    }

    private record LabelState(Waypoint waypoint, double playerDistance, double distanceSquared, boolean blocked) {
    }
}
