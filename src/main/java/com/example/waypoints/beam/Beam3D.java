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
                    labelCandidates.add(new LabelState(waypoint, playerDistance, distanceSquared));
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
                for (LabelState label : labelStates) {
                    submitDepthTestedLabel(poseStack, collector, cameraState, mc.font, label);
                }
            }
        } catch (Throwable t) {
            fail(t);
        }
    }

    /**
     * Submit the label as actual world-space text with NORMAL depth testing.
     * The scene depth buffer now decides visibility independently for every pixel:
     * a skeleton, dropped item, player or block in front of the label covers only
     * the fragments it occupies. There is no raycast, entity-distance heuristic,
     * frame delay, or screen-space text drawn over the whole scene.
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
        int nameWidth = font.width(name);
        int distanceWidth = font.width(distanceLine);
        float halfWidth = Math.max(nameWidth, distanceWidth) * 0.5f + 5.0f;

        // Keep the text readable at ordinary ranges while still behaving like a
        // physical world marker. Size grows gently with distance, then stops growing.
        float sizeMode = switch (WaypointsMod.markerSizeMode) {
            case 0 -> 0.060f;
            case 2 -> 0.082f;
            default -> 0.071f;
        };
        float distanceBoost = (float) Math.max(1.0, Math.min(1.30, label.playerDistance() / 32.0));
        float scale = sizeMode * distanceBoost;

        poseStack.pushPose();
        poseStack.translate(
                waypoint.x() + 0.5 - cameraPos.x,
                waypoint.y() + 1.85 - cameraPos.y,
                waypoint.z() + 0.5 - cameraPos.z
        );
        // This is the same billboard orientation convention used by vanilla name tags.
        poseStack.mulPose(new Matrix4f().rotation(camera.orientation));
        poseStack.scale(-scale, -scale, scale);

        // Keep a low-opacity backing visible even when an entity crosses the
        // waypoint, so the nameplate does not disappear as one solid unit.
        // SEE_THROUGH is used ONLY for this subtle background; its low alpha
        // lets the mob/item remain clearly visible beneath it. The glyphs below
        // still use NORMAL depth testing and are naturally masked by foreground
        // entities and blocks on a per-pixel basis.
        collector.submitTextBackground(
                poseStack,
                -halfWidth,
                -12.0f,
                halfWidth,
                11.0f,
                0x55070709,
                Font.DisplayMode.SEE_THROUGH,
                FULL_BRIGHT
        );
        collector.submitText(
                poseStack,
                -nameWidth * 0.5f,
                -9.0f,
                Component.literal(name).getVisualOrderText(),
                false,
                Font.DisplayMode.NORMAL,
                FULL_BRIGHT,
                0xFFF0F2F4,
                0,
                0
        );
        collector.submitText(
                poseStack,
                -distanceWidth * 0.5f,
                1.5f,
                Component.literal(distanceLine).getVisualOrderText(),
                false,
                Font.DisplayMode.NORMAL,
                FULL_BRIGHT,
                0xFFD0D6DC,
                0,
                0
        );
        poseStack.popPose();
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

    private record LabelState(Waypoint waypoint, double playerDistance, double distanceSquared) {
    }
}
