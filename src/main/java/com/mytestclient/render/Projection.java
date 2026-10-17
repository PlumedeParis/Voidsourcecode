package com.mytestclient.render;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.Camera;
import net.minecraft.util.math.Vec3d;
import org.joml.Matrix4f;

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

        // view space (x right, y up, z backwards), then the frame's bobbing/hurt tilt on top
        double[][] basis = {{rx, ry, rz}, {ux, uy, uz}, {-fx, -fy, -fz}};
        double[][] b = {
                {bob.m00(), bob.m10(), bob.m20(), bob.m30()},
                {bob.m01(), bob.m11(), bob.m21(), bob.m31()},
                {bob.m02(), bob.m12(), bob.m22(), bob.m32()}};
        double[][] m = new double[3][4];
        boolean ok = true;
        for (int i = 0; i < 3; i++) {
            for (int c = 0; c < 3; c++) m[i][c] = b[i][0] * basis[0][c] + b[i][1] * basis[1][c] + b[i][2] * basis[2][c];
            m[i][3] = b[i][3];
            for (double v : m[i]) if (!Double.isFinite(v)) ok = false;
        }
        if (!ok) { // never trust a broken matrix: plain camera basis
            m = new double[][]{{rx, ry, rz, 0}, {ux, uy, uz, 0}, {-fx, -fy, -fz, 0}};
        }
        m00 = m[0][0]; m01 = m[0][1]; m02 = m[0][2]; m03 = m[0][3];
        m10 = m[1][0]; m11 = m[1][1]; m12 = m[1][2]; m13 = m[1][3];
        m20 = m[2][0]; m21 = m[2][1]; m22 = m[2][2]; m23 = m[2][3];

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

    private static boolean inHand;

    public static void setBob(Matrix4f m) {
        if (!inHand) bob.set(m);
    }

    /** The hand is rendered with its own bobbing pass: ignore it. */
    public static void setInHand(boolean value) {
        inHand = value;
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
