package com.mytestclient.util;

import com.mytestclient.module.Modules;
import com.mytestclient.module.combat.AutoWeaponModule;
import com.mytestclient.module.combat.CriticalsModule;
import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.RaycastContext;

/** Helpers shared by the combat modules (targets, weapons, aiming, block placement). */
public final class CombatUtil {
    private static final MinecraftClient mc = MinecraftClient.getInstance();

    private CombatUtil() {
    }

    /** Alive living entity of a wanted kind; never yourself, creative/spectator players or friends. */
    public static boolean isTarget(Entity entity, boolean players, boolean hostiles, boolean animals) {
        if (!(entity instanceof LivingEntity living) || entity == mc.player || !living.isAlive() || living.getHealth() <= 0) return false;
        if (entity instanceof PlayerEntity p && (p.isCreative() || p.isSpectator() || Friends.isFriend(p.getName().getString()))) return false;
        return switch (EntityKind.of(entity)) {
            case PLAYER -> players;
            case HOSTILE -> hostiles;
            case PASSIVE -> animals;
            default -> false;
        };
    }

    /** Closest wanted target within range (optionally only visible ones), or null. */
    public static LivingEntity closestTarget(double range, boolean players, boolean hostiles, boolean animals, boolean needSight) {
        LivingEntity best = null;
        double bestD = range * range;
        for (Entity e : mc.world.getEntities()) {
            if (!isTarget(e, players, hostiles, animals)) continue;
            double d = mc.player.squaredDistanceTo(e);
            if (d > bestD || (needSight && !canSee(e))) continue;
            bestD = d;
            best = (LivingEntity) e;
        }
        return best;
    }

    public static boolean canSee(Entity entity) {
        Vec3d from = mc.player.getEyePos();
        Vec3d to = new Vec3d(entity.getX(), entity.getEyeY(), entity.getZ());
        return mc.world.raycast(new RaycastContext(from, to, RaycastContext.ShapeType.COLLIDER, RaycastContext.FluidHandling.NONE, mc.player)).getType() == HitResult.Type.MISS;
    }

    /** Swords first (by material), then axes; 0 = not a weapon. */
    public static int weaponScore(ItemStack stack) {
        if (stack.isEmpty()) return 0;
        String path = Registries.ITEM.getId(stack.getItem()).getPath();
        int base;
        if (path.endsWith("_sword")) base = 100;
        else if (path.endsWith("_axe")) base = 50;
        else if (path.equals("mace") || path.equals("trident")) base = 40;
        else return 0;
        return base + tier(path);
    }

    /** Material tier from an item id: netherite 7 ... wood/leather 1. */
    public static int tier(String path) {
        return path.startsWith("netherite") ? 7 : path.startsWith("diamond") ? 6 : path.startsWith("iron") ? 5
                : path.startsWith("copper") || path.startsWith("chainmail") || path.startsWith("turtle") ? 4
                : path.startsWith("stone") ? 3 : path.startsWith("golden") ? 2 : 1;
    }

    /** Best weapon slot in the hotbar, or -1. */
    public static int bestWeaponSlot() {
        int best = -1, bestScore = 0;
        for (int i = 0; i < 9; i++) {
            int score = weaponScore(mc.player.getInventory().getStack(i));
            if (score > bestScore) {
                bestScore = score;
                best = i;
            }
        }
        return best;
    }

    /** Points the player's view at {@code target}. */
    public static void lookAt(Vec3d target) {
        double dx = target.x - mc.player.getX(), dy = target.y - mc.player.getEyeY(), dz = target.z - mc.player.getZ();
        mc.player.setYaw((float) Math.toDegrees(Math.atan2(dz, dx)) - 90f);
        mc.player.setPitch(MathHelper.clamp((float) -Math.toDegrees(Math.atan2(dy, Math.sqrt(dx * dx + dz * dz))), -90f, 90f));
    }

    /** Centre of an entity's body. */
    public static Vec3d body(Entity e) {
        return new Vec3d(e.getX(), e.getY() + e.getHeight() * 0.5, e.getZ());
    }

    /** Called (by a mixin) right before any attack: Auto Weapon and Criticals hook in here. */
    public static void beforeAttack(Entity target) {
        if (mc.player == null) return;
        AutoWeaponModule weapon = Modules.get(AutoWeaponModule.class);
        if (weapon != null && weapon.isEnabled()) weapon.beforeAttack(target);
        CriticalsModule crits = Modules.get(CriticalsModule.class);
        if (crits != null && crits.isEnabled()) crits.beforeAttack(target);
    }

    /** True while Criticals (Jump mode) wants automated attacks to wait for the falling part of a jump. */
    public static boolean holdForCritical() {
        CriticalsModule crits = Modules.get(CriticalsModule.class);
        return crits != null && crits.isEnabled() && crits.holdAttack();
    }

    /** Places a block at {@code pos} from the given hotbar slot by clicking a solid neighbour face. */
    public static boolean place(BlockPos pos, int slot, boolean rotate) {
        if (!mc.world.getBlockState(pos).isReplaceable()) return false;
        for (Direction dir : Direction.values()) {
            BlockPos neighbour = pos.offset(dir);
            if (mc.world.getBlockState(neighbour).isReplaceable()) continue;
            Direction face = dir.getOpposite();
            Vec3d hitVec = new Vec3d(neighbour.getX() + 0.5 + face.getOffsetX() * 0.5,
                    neighbour.getY() + 0.5 + face.getOffsetY() * 0.5, neighbour.getZ() + 0.5 + face.getOffsetZ() * 0.5);
            if (mc.player.getEyePos().squaredDistanceTo(hitVec) > 4.5 * 4.5) continue;
            int previous = mc.player.getInventory().getSelectedSlot();
            mc.player.getInventory().setSelectedSlot(slot);
            if (rotate) lookAt(hitVec);
            mc.interactionManager.interactBlock(mc.player, net.minecraft.util.Hand.MAIN_HAND, new BlockHitResult(hitVec, face, neighbour, false));
            mc.player.swingHand(net.minecraft.util.Hand.MAIN_HAND);
            mc.player.getInventory().setSelectedSlot(previous);
            return true;
        }
        return false;
    }
}
