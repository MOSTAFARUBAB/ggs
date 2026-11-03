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
import net.minecraft.world.phys.Vec3;

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
    private static final int MAX_RENDERED_BEAMS = 24;
    private static volatile List<BeamState> beams = List.of();
    private static volatile boolean failed = false;

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
    }

    private static void extract(LevelExtractionContext context) {
        if (failed) return;

        try {
            Minecraft mc = Minecraft.getInstance();
            if (mc.level == null || mc.player == null || !WaypointsMod.showWaypointBeams) {
                beams = List.of();
                return;
            }

            Vec3 cameraPosition = mc.player.getEyePosition();
            double[] camera = WaypointsMod.cameraState(mc);
            if (camera != null) {
                cameraPosition = new Vec3(camera[0], camera[1], camera[2]);
            }

            List<BeamState> out = new ArrayList<>();
            // Match BeaconRenderer.getViewDistance()/shouldRender(): distance is
            // the effective render distance in chunks converted to blocks.
            double maxBeamDistance = mc.options.getEffectiveRenderDistance() * 16.0;
            double maxBeamDistanceSquared = maxBeamDistance * maxBeamDistance;
            for (Waypoint waypoint : WaypointsMod.currentContextWaypoints()) {
                if (out.size() >= MAX_RENDERED_BEAMS) break;

                // BeaconRenderer expects the pose stack at the block origin; it adds
                // its own +0.5 X/Z translation internally to center the beam.
                double dx = waypoint.x() + 0.5 - cameraPosition.x;
                double dz = waypoint.z() + 0.5 - cameraPosition.z;
                double horizontalDistanceSquared = dx * dx + dz * dz;

                // Do not hide a waypoint when the player gets close to it. The previous
                // 0.5-block exclusion and HUD fallback made the beam vanish as the player
                // approached or walked around its base.
                if (horizontalDistanceSquared > maxBeamDistanceSquared) continue;

                double horizontalDistance = Math.sqrt(horizontalDistanceSquared);

                // Match BeaconRenderer.extract() exactly: scoped players use normal
                // width; otherwise the beam widens past 96 horizontal blocks.
                float radiusScale = mc.player.isScoping()
                        ? 1.0f
                        : (float) Math.max(1.0, horizontalDistance / 96.0);
                int color = 0xFF000000 | (WaypointsMod.resolveColor(waypoint) & 0x00FFFFFF);

                // Store the block origin, not its center. submitBeaconBeam() centers
                // itself by translating +0.5 X/Z just like the real beacon renderer.
                out.add(new BeamState(
                        waypoint.x(),
                        waypoint.z(),
                        color,
                        radiusScale
                ));
            }
            beams = List.copyOf(out);
        } catch (Throwable t) {
            fail(t);
        }
    }

    private static void renderAndDraw(LevelRenderContext context) {
        if (failed) return;

        try {
            List<BeamState> states = beams;
            if (states.isEmpty()) return;

            Vec3 camera = context.levelState().cameraRenderState.pos;
            if (camera == null) return;

            Minecraft mc = Minecraft.getInstance();
            if (mc.level == null) return;

            PoseStack poseStack = context.poseStack();
            var collector = context.submitNodeCollector();
            int minBuildY = mc.level.getMinY();

            // Copy BeaconRenderer.extract() timing exactly. Vanilla wraps at 40 ticks
            // and adds the current partial tick; passing a huge game-time float loses
            // precision in the rotating core and scrolling texture.
            float animationTime = Math.floorMod(mc.level.getGameTime(), 40L)
                    + mc.getDeltaTracker().getGameTimeDeltaPartialTick(false);

            for (BeamState beam : states) {
                poseStack.pushPose();
                // Keep the requested full-world column, but place the pose at the
                // block origin. BeaconRenderer itself adds its exact vanilla center
                // offset (+0.5 X/Z), rotates the inner core, and submits the translucent
                // outer shell with ARGB alpha 32.
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
        } catch (Throwable t) {
            fail(t);
        }
    }

    private record BeamState(double x, double z, int rgb, float radiusScale) {
    }
}
