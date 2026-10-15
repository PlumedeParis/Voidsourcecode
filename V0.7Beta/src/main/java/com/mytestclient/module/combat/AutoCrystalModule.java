package com.mytestclient.module.combat;

import com.mytestclient.gui.Theme;
import com.mytestclient.module.Category;
import com.mytestclient.module.Module;
import com.mytestclient.module.Risk;
import com.mytestclient.module.setting.BooleanSetting;
import com.mytestclient.module.setting.NumberSetting;
import com.mytestclient.render.Overlay;
import com.mytestclient.render.Projection;
import com.mytestclient.util.EntityKind;
import com.mytestclient.util.Friends;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.decoration.EndCrystalEntity;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.item.Item;
import net.minecraft.item.Items;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.RaycastContext;

/**
 * Auto Crystal: defends you by placing end crystals next to an attacker and detonating them,
 * while refusing anything that would hurt you more than "Max Self Damage".
 * <p>
 * Damage model (close to vanilla): explosion power 6, distance falloff, <b>exposure</b>
 * (raycasts — blocks between you and the crystal protect you), <b>armour + toughness</b> and the
 * Resistance effect. Without exposure and armour every position looks lethal, which is why the
 * first version never did anything.
 * <p>
 * If there is no obsidian/bedrock near the target, it can place obsidian from your hotbar first.
 * A status line under the crosshair tells you what it is doing or why it waits.
 * <p>
 * ⚠ DETECTABLE: automated placements and attacks. Anti-cheats detect this and most servers ban
 * combat automation. Intended for singleplayer/testing or servers that allow it.
 */
public class AutoCrystalModule extends Module {
    private final NumberSetting targetRange = add(new NumberSetting("Target Range", "Look for attackers within this distance.", 10, 3, 16, 0.5));
    private final NumberSetting placeRange = add(new NumberSetting("Place Range", "Maximum distance to place crystals/obsidian.", 4.5, 1, 6, 0.1));
    private final NumberSetting breakRange = add(new NumberSetting("Break Range", "Maximum distance to hit crystals.", 4.5, 1, 6, 0.1));
    private final NumberSetting delay = add(new NumberSetting("Action Delay", "Ticks between actions.", 1, 0, 20, 1));
    private final NumberSetting minDamage = add(new NumberSetting("Min Target Damage", "Only act if the target takes at least this much (after armour).", 4, 0, 36, 0.5));
    private final NumberSetting maxSelfDamage = add(new NumberSetting("Max Self Dmg", "Never place/break if you would take more than this (after armour).", 8, 0, 36, 0.5));
    private final BooleanSetting antiSuicide = add(new BooleanSetting("Anti Suicide", "Never act if the estimated self damage would kill you.", true));
    private final BooleanSetting players = add(new BooleanSetting("Target Players", "Defend against players (friends are ignored).", true));
    private final BooleanSetting hostiles = add(new BooleanSetting("Target Hostiles", "Defend against hostile mobs.", true));
    private final BooleanSetting place = add(new BooleanSetting("Place", "Place crystals.", true));
    private final BooleanSetting breakCrystals = add(new BooleanSetting("Break", "Hit crystals.", true));
    private final BooleanSetting placeObsidian = add(new BooleanSetting("Place Obsidian", "Place obsidian next to the target when there is nowhere to put a crystal.", true));
    private final BooleanSetting autoSwitch = add(new BooleanSetting("Auto Switch", "Switch to crystals/obsidian in your hotbar.", true));
    private final BooleanSetting rotate = add(new BooleanSetting("Rotate", "Look at the crystal/position before acting.", true));
    private final BooleanSetting showTarget = add(new BooleanSetting("Show Target", "Outline the target and placement, and show a status line.", true));

    /** Max exposure raycast batches per tick, keeps the module cheap even with many candidates. */
    private static final int EXPOSURE_BUDGET = 40;

    private LivingEntity target;
    private BlockPos lastPlace;
    private String status = "";
    private int timer;
    private int exposureBudget;

    public AutoCrystalModule() {
        super("Auto Crystal", "Defend yourself with end crystals: places obsidian/crystals near attackers and detonates them.", Category.COMBAT);
        setRisk(Risk.DETECTABLE, "Sends automated placements and attacks. Anti-cheats detect it and most servers ban combat automation.");
    }

    @Override
    protected void onDisable() {
        target = null;
        lastPlace = null;
        status = "";
    }

    @Override
    public void onTick() {
        exposureBudget = EXPOSURE_BUDGET;
        target = findTarget();
        if (target == null) {
            status = "No target in range";
            return;
        }
        if (timer > 0) {
            timer--;
            return;
        }
        if (breakCrystals.get() && tryBreak()) {
            timer = delay.getInt();
            return;
        }
        if (place.get() && tryPlace()) timer = delay.getInt();
    }

    // ------------------------------------------------------------------ targeting

    private LivingEntity findTarget() {
        LivingEntity best = null;
        double bestDist = targetRange.get() * targetRange.get();
        for (Entity entity : mc.world.getEntities()) {
            if (!(entity instanceof LivingEntity living) || entity == mc.player || !living.isAlive()) continue;
            EntityKind kind = EntityKind.of(entity);
            if (kind == EntityKind.PLAYER) {
                if (!players.get() || Friends.isFriend(entity.getName().getString())) continue;
            } else if (!(kind == EntityKind.HOSTILE && hostiles.get())) continue;
            double d = mc.player.squaredDistanceTo(entity);
            if (d < bestDist) {
                bestDist = d;
                best = living;
            }
        }
        return best;
    }

    // ------------------------------------------------------------------ breaking

    private boolean tryBreak() {
        EndCrystalEntity best = null;
        double bestScore = Double.NEGATIVE_INFINITY;
        for (Entity entity : mc.world.getEntities()) {
            if (!(entity instanceof EndCrystalEntity crystal)) continue;
            if (mc.player.squaredDistanceTo(crystal) > breakRange.get() * breakRange.get()) continue;
            Vec3d pos = new Vec3d(crystal.getX(), crystal.getY(), crystal.getZ());
            double self = damage(pos, mc.player, true);
            if (!selfSafe(self)) continue;
            double dmg = damage(pos, target, true);
            if (dmg < minDamage.get()) continue;
            if (dmg - self > bestScore) {
                best = crystal;
                bestScore = dmg - self;
            }
        }
        if (best == null) return false;
        if (rotate.get()) lookAt(best.getX(), best.getY() + 0.5, best.getZ());
        mc.interactionManager.attackEntity(mc.player, best);
        mc.player.swingHand(Hand.MAIN_HAND);
        status = "Breaking crystal";
        return true;
    }

    // ------------------------------------------------------------------ placing

    private boolean tryPlace() {
        Hand hand = handFor(Items.END_CRYSTAL);
        if (hand == null) {
            status = "No end crystals in your hotbar";
            return false;
        }

        double range = placeRange.get();
        int r = (int) Math.ceil(range);
        BlockPos origin = mc.player.getBlockPos();
        Vec3d eyes = mc.player.getEyePos();

        BlockPos best = null;
        double bestScore = Double.NEGATIVE_INFINITY;
        boolean anyBase = false, anySafe = false;
        for (BlockPos pos : BlockPos.iterate(origin.add(-r, -r, -r), origin.add(r, r, r))) {
            if (eyes.squaredDistanceTo(pos.getX() + 0.5, pos.getY() + 1, pos.getZ() + 0.5) > range * range) continue;
            if (!isCrystalBase(pos)) continue;
            anyBase = true;
            Vec3d crystal = new Vec3d(pos.getX() + 0.5, pos.getY() + 1, pos.getZ() + 0.5);
            // cheap upper bound first (no exposure), real damage only for promising spots
            double upper = damage(crystal, target, false);
            if (upper < minDamage.get() || upper <= bestScore) continue;
            double self = damage(crystal, mc.player, true);
            if (!selfSafe(self)) continue;
            anySafe = true;
            double dmg = damage(crystal, target, true);
            if (dmg < minDamage.get() || dmg - self <= bestScore) continue;
            if (!spaceFree(pos)) continue;
            bestScore = dmg - self;
            best = pos.toImmutable();
        }

        if (best == null) {
            if (!anyBase) {
                if (placeObsidian.get() && tryPlaceObsidian()) return true;
                status = placeObsidian.get() ? "No obsidian nearby and none in your hotbar" : "No obsidian/bedrock near the target";
            } else if (!anySafe) {
                status = "Every spot would hurt you too much (raise Max Self Dmg)";
            } else {
                status = "Target takes too little damage (lower Min Target Damage)";
            }
            return false;
        }

        if (!select(hand, Items.END_CRYSTAL)) return false;
        Vec3d hitVec = new Vec3d(best.getX() + 0.5, best.getY() + 1, best.getZ() + 0.5);
        if (rotate.get()) lookAt(hitVec.x, hitVec.y, hitVec.z);
        mc.interactionManager.interactBlock(mc.player, hand, new BlockHitResult(hitVec, Direction.UP, best, false));
        mc.player.swingHand(hand);
        lastPlace = best;
        status = String.format("Placed crystal (~%.1f dmg)", bestScore);
        return true;
    }

    /** Places obsidian at the target's feet level, next to it, so a crystal can go on top. */
    private boolean tryPlaceObsidian() {
        Hand hand = handFor(Items.OBSIDIAN);
        if (hand == null) return false;
        BlockPos feet = target.getBlockPos();
        BlockPos best = null;
        double bestDist = Double.MAX_VALUE;
        for (Direction dir : Direction.Type.HORIZONTAL) {
            BlockPos pos = feet.offset(dir).down();
            if (!mc.world.getBlockState(pos).isReplaceable()) continue;
            if (!mc.world.getBlockState(pos.up()).isAir()) continue;
            if (mc.world.getBlockState(pos.down()).isReplaceable()) continue; // needs support below
            double d = mc.player.getEyePos().squaredDistanceTo(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5);
            if (d > placeRange.get() * placeRange.get() || d >= bestDist) continue;
            Box space = new Box(pos.getX(), pos.getY(), pos.getZ(), pos.getX() + 1, pos.getY() + 1, pos.getZ() + 1);
            if (!mc.world.getOtherEntities(null, space).isEmpty()) continue;
            best = pos;
            bestDist = d;
        }
        if (best == null || !select(hand, Items.OBSIDIAN)) return false;
        BlockPos support = best.down();
        Vec3d hitVec = new Vec3d(support.getX() + 0.5, support.getY() + 1, support.getZ() + 0.5);
        if (rotate.get()) lookAt(hitVec.x, hitVec.y, hitVec.z);
        mc.interactionManager.interactBlock(mc.player, hand, new BlockHitResult(hitVec, Direction.UP, support, false));
        mc.player.swingHand(hand);
        status = "Placed obsidian";
        return true;
    }

    private Hand handFor(Item item) {
        if (mc.player.getOffHandStack().isOf(item)) return Hand.OFF_HAND;
        if (mc.player.getMainHandStack().isOf(item)) return Hand.MAIN_HAND;
        return autoSwitch.get() && hotbarSlot(item) >= 0 ? Hand.MAIN_HAND : null;
    }

    private boolean select(Hand hand, Item item) {
        if (hand == Hand.OFF_HAND || mc.player.getMainHandStack().isOf(item)) return true;
        int slot = hotbarSlot(item);
        if (slot < 0) return false;
        mc.player.getInventory().setSelectedSlot(slot);
        return true;
    }

    private int hotbarSlot(Item item) {
        for (int i = 0; i < 9; i++) if (mc.player.getInventory().getStack(i).isOf(item)) return i;
        return -1;
    }

    private boolean isCrystalBase(BlockPos pos) {
        BlockState base = mc.world.getBlockState(pos);
        if (!base.isOf(Blocks.OBSIDIAN) && !base.isOf(Blocks.BEDROCK)) return false;
        return mc.world.getBlockState(pos.up()).isAir();
    }

    private boolean spaceFree(BlockPos pos) {
        BlockPos above = pos.up();
        Box space = new Box(above.getX(), above.getY(), above.getZ(), above.getX() + 1, above.getY() + 2, above.getZ() + 1);
        return mc.world.getOtherEntities(null, space).isEmpty();
    }

    private boolean selfSafe(double selfDamage) {
        if (selfDamage > maxSelfDamage.get()) return false;
        return !antiSuicide.get() || selfDamage < mc.player.getHealth() + mc.player.getAbsorptionAmount() - 1;
    }

    // ------------------------------------------------------------------ damage model

    /**
     * Estimated end crystal damage to {@code entity} after armour.
     *
     * @param withExposure trace rays to account for blocks in the way (expensive, budgeted)
     */
    private double damage(Vec3d explosion, LivingEntity entity, boolean withExposure) {
        double power = 6.0;
        double dist = Math.sqrt(entity.squaredDistanceTo(explosion.x, explosion.y, explosion.z)) / (power * 2);
        if (dist > 1) return 0;
        double exposure = withExposure ? exposure(explosion, entity.getBoundingBox()) : 1.0;
        double impact = (1 - dist) * exposure;
        double dmg = (impact * impact + impact) / 2.0 * 7.0 * power * 2 + 1;

        // armour and toughness (vanilla formula)
        double armor = entity.getArmor();
        double toughness = entity.getAttributeValue(EntityAttributes.ARMOR_TOUGHNESS);
        double reduction = MathHelper.clamp(armor - dmg / (2 + toughness / 4), armor * 0.2, 20) / 25;
        dmg *= 1 - reduction;

        StatusEffectInstance resistance = entity.getStatusEffect(StatusEffects.RESISTANCE);
        if (resistance != null) dmg *= Math.max(0, 1 - 0.2 * (resistance.getAmplifier() + 1));
        return Math.max(0, dmg);
    }

    /** Fraction of sample points of the box visible from the explosion (like vanilla). */
    private double exposure(Vec3d source, Box box) {
        if (exposureBudget-- <= 0) return 1.0; // over budget: assume worst case
        int hits = 0, total = 0;
        for (double fx = 0; fx <= 1; fx += 0.5) {
            for (double fy = 0; fy <= 1; fy += 0.5) {
                for (double fz = 0; fz <= 1; fz += 0.5) {
                    Vec3d point = new Vec3d(
                            MathHelper.lerp(fx, box.minX, box.maxX),
                            MathHelper.lerp(fy, box.minY, box.maxY),
                            MathHelper.lerp(fz, box.minZ, box.maxZ));
                    total++;
                    HitResult hit = mc.world.raycast(new RaycastContext(point, source,
                            RaycastContext.ShapeType.COLLIDER, RaycastContext.FluidHandling.NONE, mc.player));
                    if (hit.getType() == HitResult.Type.MISS) hits++;
                }
            }
        }
        return total == 0 ? 0 : (double) hits / total;
    }

    private void lookAt(double x, double y, double z) {
        double dx = x - mc.player.getX(), dy = y - mc.player.getEyeY(), dz = z - mc.player.getZ();
        double horizontal = Math.sqrt(dx * dx + dz * dz);
        mc.player.setYaw((float) Math.toDegrees(Math.atan2(dz, dx)) - 90f);
        mc.player.setPitch(MathHelper.clamp((float) -Math.toDegrees(Math.atan2(dy, horizontal)), -90f, 90f));
    }

    // ------------------------------------------------------------------ rendering

    @Override
    public void onRenderOverlay(Overlay o) {
        if (!showTarget.get()) return;
        if (target != null) {
            Vec3d p = target.getLerpedPos(o.tickDelta());
            o.box(target.getBoundingBox().offset(p.x - target.getX(), p.y - target.getY(), p.z - target.getZ()), 0xFFEF4444);
        }
        if (lastPlace != null) {
            o.box(new Box(lastPlace.getX(), lastPlace.getY(), lastPlace.getZ(), lastPlace.getX() + 1, lastPlace.getY() + 1, lastPlace.getZ() + 1), 0xFFA855F7);
        }
        if (!status.isEmpty()) {
            o.flush();
            String text = "Auto Crystal: " + status;
            int w = mc.textRenderer.getWidth(text);
            int x = (int) (Projection.width() / 2 - w / 2.0), y = (int) (Projection.height() / 2 + 14);
            o.ctx().drawText(mc.textRenderer, text, x, y, target == null ? Theme.TEXT_DIM : 0xFFFFC857, true);
        }
    }
}
