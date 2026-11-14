package com.voidclient.util;

import net.minecraft.util.math.BlockPos;

/**
 * A straight lane through the block grid in one of 8 directions (4 axes, 4 diagonals), used
 * by Tunnel Miner and Highway Builder. Positions are described by how far {@code along} the
 * lane they are and how far to the side ({@code lateral}), both in whole steps.
 */
public final class Lane {
    public static final String[] NAMES = {"+Z (South)", "-X +Z", "-X (West)", "-X -Z", "-Z (North)", "+X -Z", "+X (East)", "+X +Z"};
    private static final int[] DX = {0, -1, -1, -1, 0, 1, 1, 1}, DZ = {1, 1, 0, -1, -1, -1, 0, 1};

    public final int index, dx, dz, originX, originZ;

    public Lane(int index, BlockPos origin) {
        this.index = Math.floorMod(index, 8);
        this.dx = DX[this.index];
        this.dz = DZ[this.index];
        this.originX = origin.getX();
        this.originZ = origin.getZ();
    }

    /** Lane in the direction closest to a yaw. */
    public static Lane fromYaw(float yaw, BlockPos origin, boolean allowDiagonal) {
        int step = allowDiagonal ? 45 : 90;
        int i = Math.floorMod(Math.round(yaw / step) * step / 45, 8);
        return new Lane(i, origin);
    }

    public boolean diagonal() {
        return dx != 0 && dz != 0;
    }

    public String name() {
        return NAMES[index];
    }

    public float yaw() {
        return (float) Math.toDegrees(Math.atan2(-dx, dz));
    }

    /** Steps forward from the origin (diagonal steps count double: x+z). */
    public int along(int x, int z) {
        return (x - originX) * dx + (z - originZ) * dz;
    }

    /** Steps to the side of the centre line (0 = on it). */
    public int lateral(int x, int z) {
        return -(x - originX) * dz + (z - originZ) * dx;
    }

    /** Largest |lateral| still inside a lane that is {@code width} blocks wide. */
    public int half(int width) {
        return diagonal() ? (int) Math.floor(width * 0.7071) : (width - 1) / 2;
    }

    /** Along-steps that correspond to {@code blocks} blocks of distance. */
    public int steps(int blocks) {
        return diagonal() ? blocks * 2 : blocks;
    }

    /** Signed distance (blocks) of a point from the centre line. */
    public double offset(double x, double z) {
        double len = diagonal() ? Math.sqrt(2) : 1;
        return (-(x - (originX + 0.5)) * dz + (z - (originZ + 0.5)) * dx) / len;
    }
}
