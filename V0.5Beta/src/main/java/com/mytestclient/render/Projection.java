package com.mytestclient.render;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.Camera;
import net.minecraft.util.math.Vec3d;

/**
 * Projects world coordinates onto the screen using the camera position, rotation and FOV.
 * <p>
 * Overlays (ESP boxes, waypoints, chunk borders...) are drawn in the HUD pass with these
 * projected coordinates instead of hooking the world renderer. This keeps them independent of
 * Minecraft's frequently changing rendering internals. The real FOV (sprinting, zoom, effects) is
 * used; only view bobbing is not compensated, so turn it off for pixel-perfect overlays.
 */
public final class Projection {
    public static final double NEAR = 0.05;

    private static double camX, camY, camZ;
    private static double fx, fy, fz;
    private static double rx, ry, rz;
    private static double ux, uy, uz;
    private static double focal;
    private static double width, height, guiScale;
    /** Actual world FOV of the last rendered frame (sprint, zoom...), recorded by GameRendererMixin. */
    private static float worldFov = -1;

    private Projection() {
    }

    /** Captures the camera state. Call once per frame before projecting. */
    public static void update() {
        MinecraftClient mc = MinecraftClient.getInstance();
        Camera camera = mc.gameRenderer.getCamera();
        Vec3d pos = camera.getCameraPos();
        camX = pos.x;
        camY = pos.y;
        camZ = pos.z;

        double yaw = Math.toRadians(camera.getYaw());
        double pitch = Math.toRadians(camera.getPitch());
        // forward
        fx = -Math.sin(yaw) * Math.cos(pitch);
        fy = -Math.sin(pitch);
        fz = Math.cos(yaw) * Math.cos(pitch);
        // right (horizontal)
        rx = -Math.cos(yaw);
        ry = 0;
        rz = -Math.sin(yaw);
        // up = right x forward
        ux = ry * fz - rz * fy;
        uy = rz * fx - rx * fz;
        uz = rx * fy - ry * fx;

        guiScale = mc.getWindow().getScaleFactor();
        width = mc.getWindow().getFramebufferWidth() / guiScale;
        height = mc.getWindow().getFramebufferHeight() / guiScale;
        double fov = worldFov > 0 ? worldFov : mc.options.getFov().getValue();
        focal = (height / 2.0) / Math.tan(Math.toRadians(fov) / 2.0);
    }

    public static void setWorldFov(float fov) {
        worldFov = fov;
    }

    /** World position to camera space {right, up, forward}. */
    public static double[] toCamera(double x, double y, double z) {
        double dx = x - camX, dy = y - camY, dz = z - camZ;
        return new double[]{
                dx * rx + dy * ry + dz * rz,
                dx * ux + dy * uy + dz * uz,
                dx * fx + dy * fy + dz * fz
        };
    }

    /** Camera space point (must be in front of the camera) to GUI coordinates. */
    public static double[] toScreen(double[] cam) {
        return new double[]{
                width / 2.0 + cam[0] / cam[2] * focal,
                height / 2.0 - cam[1] / cam[2] * focal
        };
    }

    /** World position to GUI coordinates, or null if behind the camera. */
    public static double[] project(double x, double y, double z) {
        double[] cam = toCamera(x, y, z);
        return cam[2] < NEAR ? null : toScreen(cam);
    }

    public static double distanceTo(double x, double y, double z) {
        double dx = x - camX, dy = y - camY, dz = z - camZ;
        return Math.sqrt(dx * dx + dy * dy + dz * dz);
    }

    public static double width() {
        return width;
    }

    public static double height() {
        return height;
    }

    public static double guiScale() {
        return guiScale;
    }

    public static double camX() {
        return camX;
    }

    public static double camY() {
        return camY;
    }

    public static double camZ() {
        return camZ;
    }
}
