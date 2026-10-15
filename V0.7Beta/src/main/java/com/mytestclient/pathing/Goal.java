package com.mytestclient.pathing;

import net.minecraft.util.math.BlockPos;

/** Where the pathfinder should go. Positions are the player's feet block. */
public interface Goal {
    boolean isDone(BlockPos feet);

    /** Estimated remaining cost (blocks). */
    double heuristic(BlockPos feet);

    /** Stand on exactly this block (feet). */
    static Goal block(BlockPos target) {
        return new Goal() {
            public boolean isDone(BlockPos f) {
                return f.equals(target);
            }

            public double heuristic(BlockPos f) {
                return Math.sqrt(f.getSquaredDistance(target));
            }

            public String toString() {
                return target.toShortString();
            }
        };
    }

    /** Within {@code radius} blocks of a position. */
    static Goal near(BlockPos target, double radius) {
        return new Goal() {
            public boolean isDone(BlockPos f) {
                return f.getSquaredDistance(target) <= radius * radius;
            }

            public double heuristic(BlockPos f) {
                return Math.max(0, Math.sqrt(f.getSquaredDistance(target)) - radius);
            }

            public String toString() {
                return target.toShortString();
            }
        };
    }

    /** Any height at this x/z. */
    static Goal xz(int x, int z) {
        return new Goal() {
            public boolean isDone(BlockPos f) {
                return Math.abs(f.getX() - x) <= 1 && Math.abs(f.getZ() - z) <= 1;
            }

            public double heuristic(BlockPos f) {
                return Math.hypot(f.getX() - x, f.getZ() - z);
            }

            public String toString() {
                return x + " " + z;
            }
        };
    }

    /** Close enough to reach (break/open) a block: eyes within 4 blocks of its centre. */
    static Goal reach(BlockPos block) {
        return new Goal() {
            double eyeDist(BlockPos f) {
                double dx = f.getX() - block.getX(), dy = f.getY() + 1.62 - (block.getY() + 0.5), dz = f.getZ() - block.getZ();
                return Math.sqrt(dx * dx + dy * dy + dz * dz);
            }

            public boolean isDone(BlockPos f) {
                return eyeDist(f) <= 4.0 && !f.equals(block) && !f.up().equals(block);
            }

            public double heuristic(BlockPos f) {
                return Math.max(0, eyeDist(f) - 3.5);
            }

            public String toString() {
                return block.toShortString();
            }
        };
    }
}
