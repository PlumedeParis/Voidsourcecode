package com.mytestclient.render;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.Camera;
import net.minecraft.util.math.Vec3d;
import org.joml.Matrix4f;
import org.joml.Quaternionf;

/**
 * Projects world coordinates onto the screen using the camera position, rotation and FOV.
 * <p>
 * Overlays (ESP boxes, waypoints, chunk borders...) are drawn in the HUD pass with these
 * projected coordinates instead of hooking the world renderer. This keeps them independent of
 * Minecraft's frequently changing rendering internals. The real FOV (sprinting, zoom, effects),
 * the camera's own rotation and the view-bobbing / hurt-tilt matrix of the frame (captured by
 * GameRendererMixin) are used, so overlays stay glued to the world even while walking.
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
    /** View bobbing + hurt tilt of the current frame (view space), recorded by GameRendererMixin. */
    private static final Matrix4f bob = new Matrix4f();
    private static final Matrix4f view = new Matrix4f();
    private static final Quaternionf rot = new Quaternionf();
    /** world-relative -> view space (x right, y up, z backwards), row-major copy for fast double math. */
    private static double m00, m01, m02, m03, m10, m11, m12, m13, m20, m21, m22, m23;

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

        // exact view transform: bob/tilt * camera rotation (same as the world renderer)
        rot.set(camera.getRotation()).conjugate();
        view.set(bob).rotate(rot);
        m00 = view.m00(); m01 = view.m10(); m02 = view.m20(); m03 = view.m30();
        m10 = view.m01(); m11 = view.m11(); m12 = view.m21(); m13 = view.m31();
        m20 = view.m02(); m21 = view.m12(); m22 = view.m22(); m23 = view.m32();

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

    /** Start of the world render: no bobbing until bobView/tiltViewWhenHurt report one. */
    public static void resetBob() {
        bob.identity();
    }

    public static void setBob(Matrix4f m) {
        bob.set(m);
    }

    /** World position to camera space {right, up, forward}. */
    public static double[] toCamera(double x, double y, double z) {
        double dx = x - camX, dy = y - camY, dz = z - camZ;
        return new double[]{
                m00 * dx + m01 * dy + m02 * dz + m03,
                m10 * dx + m11 * dy + m12 * dz + m13,
                -(m20 * dx + m21 * dy + m22 * dz + m23)
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
