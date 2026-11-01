package com.mytestclient.module.combat;

import com.mytestclient.module.Category;
import com.mytestclient.module.Module;
import com.mytestclient.module.Risk;
import com.mytestclient.module.setting.BooleanSetting;
import com.mytestclient.module.setting.ListSetting;
import com.mytestclient.module.setting.ModeSetting;
import com.mytestclient.module.setting.NumberSetting;
import com.mytestclient.render.Overlay;
import com.mytestclient.util.EntityKind;
import com.mytestclient.util.Friends;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.RaycastContext;

/**
 * Kill Aura: automatically attacks monsters/players next to you and equips your best sword
 * (or axe) from the hotbar when it attacks.
 * <p>
 * Targets are chosen either by category (players / hostiles / animals) or from a picker menu of
 * entity types. Friends ({@code \friend add}) are never attacked. Attacks wait for the vanilla
 * attack cooldown by default, so every hit does full damage.
 * <p>
 * ⚠ DETECTABLE: automated attacks are the most-detected cheat; most servers ban it.
 */
public class KillAuraModule extends Module {
    private final ModeSetting targetMode = add(new ModeSetting("Targets", "Categories: use the toggles below. List: pick exact entity types.", "Categories", "Categories", "List"));
    private final BooleanSetting players = add(new BooleanSetting("Players", "Attack players (friends excluded).", true).visibleWhen(() -> targetMode.is("Categories")));
    private final BooleanSetting hostiles = add(new BooleanSetting("Monsters", "Attack hostile mobs.", true).visibleWhen(() -> targetMode.is("Categories")));
    private final BooleanSetting animals = add(new BooleanSetting("Animals", "Attack passive mobs.", false).visibleWhen(() -> targetMode.is("Categories")));
    private final ListSetting entityList = add(new ListSetting("Entity List", "Entity types to attack.", ListSetting.Kind.ENTITY,
            "minecraft:zombie", "minecraft:skeleton", "minecraft:creeper", "minecraft:spider").visibleWhen(() -> targetMode.is("List")));
    private final NumberSetting range = add(new NumberSetting("Range", "Attack distance in blocks (vanilla reach is 3).", 3.5, 1, 6, 0.1));
    private final ModeSetting priority = add(new ModeSetting("Priority", "Which target to hit first.", "Closest", "Closest", "Lowest Health", "Crosshair"));
    private final BooleanSetting waitCooldown = add(new BooleanSetting("Wait Cooldown", "Wait for the attack cooldown so hits do full damage.", true));
    private final NumberSetting cps = add(new NumberSetting("Clicks Per Second", "Attack rate when not waiting for the cooldown.", 8, 1, 20, 1)
            .visibleWhen(() -> !waitCooldown.get()));
    private final BooleanSetting autoWeapon = add(new BooleanSetting("Equip Weapon", "Switch to your best sword (or axe) when attacking.", true));
    private final BooleanSetting switchBack = add(new BooleanSetting("Switch Back", "Return to your previous slot when there is nothing to attack.", true));
    private final BooleanSetting throughWalls = add(new BooleanSetting("Through Walls", "Also attack targets you cannot see.", false));
    private final BooleanSetting rotate = add(new BooleanSetting("Rotate", "Look at the target when attacking.", true));
    private final BooleanSetting showTarget = add(new BooleanSetting("Show Target", "Outline the current target.", true));

    private LivingEntity target;
    private int previousSlot = -1;
    private int idleTicks;
    private double clickAcc;

    public KillAuraModule() {
        super("Kill Aura", "Automatically attacks chosen monsters/players near you and equips your sword.", Category.COMBAT);
        setRisk(Risk.DETECTABLE, "Automated attacks are the most detected cheat; banned on almost every server.");
    }

    @Override
    protected void onDisable() {
        target = null;
        restoreSlot();
    }

    @Override
    public void onWorldLeave() {
        target = null;
        previousSlot = -1;
        idleTicks = 0;
    }

    @Override
    public void onTick() {
        target = findTarget();
        if (target == null) {
            if (++idleTicks > 20) restoreSlot();
            return;
        }
        idleTicks = 0;

        if (autoWeapon.get()) equipWeapon();
        if (waitCooldown.get()) {
            if (mc.player.getAttackCooldownProgress(0.5f) < 1f) return;
        } else {
            clickAcc += cps.get() / 20.0;
            if (clickAcc < 1) return;
            clickAcc -= 1;
        }
        if (com.mytestclient.util.CombatUtil.holdForCritical()) return;
        if (rotate.get()) lookAt(target);
        mc.interactionManager.attackEntity(mc.player, target);
        mc.player.swingHand(Hand.MAIN_HAND);
    }

    private boolean isValid(Entity entity) {
        if (!(entity instanceof LivingEntity living) || entity == mc.player || !living.isAlive() || living.getHealth() <= 0) return false;
        if (entity instanceof PlayerEntity p && (p.isCreative() || p.isSpectator() || Friends.isFriend(p.getName().getString()))) return false;
        if (targetMode.is("List")) return entityList.contains(Registries.ENTITY_TYPE.getId(entity.getType()).toString());
        return switch (EntityKind.of(entity)) {
            case PLAYER -> players.get();
            case HOSTILE -> hostiles.get();
            case PASSIVE -> animals.get();
            default -> false;
        };
    }

    private LivingEntity findTarget() {
        LivingEntity best = null;
        double bestScore = Double.MAX_VALUE;
        double maxSq = range.get() * range.get();
        Vec3d eyes = mc.player.getEyePos();
        Vec3d look = mc.player.getRotationVector();
        for (Entity entity : mc.world.getOtherEntities(mc.player, mc.player.getBoundingBox().expand(range.get() + 1))) {
            double d = mc.player.squaredDistanceTo(entity);
            if (d > maxSq || !isValid(entity)) continue;
            if (!throughWalls.get() && !canSee(entity)) continue;
            LivingEntity living = (LivingEntity) entity;
            double score = switch (priority.get()) {
                case "Lowest Health" -> living.getHealth();
                case "Crosshair" -> {
                    Vec3d to = new Vec3d(entity.getX() - eyes.x, entity.getEyeY() - eyes.y, entity.getZ() - eyes.z).normalize();
                    yield 1 - to.dotProduct(look);
                }
                default -> d;
            };
            if (score < bestScore) {
                bestScore = score;
                best = living;
            }
        }
        return best;
    }

    private boolean canSee(Entity entity) {
        Vec3d from = mc.player.getEyePos();
        Vec3d to = new Vec3d(entity.getX(), entity.getEyeY(), entity.getZ());
        HitResult hit = mc.world.raycast(new RaycastContext(from, to, RaycastContext.ShapeType.COLLIDER, RaycastContext.FluidHandling.NONE, mc.player));
        return hit.getType() == HitResult.Type.MISS;
    }

    // ------------------------------------------------------------------ weapon

    private void equipWeapon() {
        int best = -1, bestScore = 0;
        for (int i = 0; i < 9; i++) {
            int score = weaponScore(mc.player.getInventory().getStack(i));
            if (score > bestScore) {
                bestScore = score;
                best = i;
            }
        }
        int current = mc.player.getInventory().getSelectedSlot();
        if (best < 0 || best == current) return;
        if (previousSlot < 0) previousSlot = current;
        mc.player.getInventory().setSelectedSlot(best);
    }

    /** Swords first (by material), then axes. */
    private static int weaponScore(ItemStack stack) {
        if (stack.isEmpty()) return 0;
        String path = Registries.ITEM.getId(stack.getItem()).getPath();
        int base;
        if (path.endsWith("_sword")) base = 100;
        else if (path.endsWith("_axe")) base = 50;
        else return 0;
        int tier = path.startsWith("netherite") ? 7 : path.startsWith("diamond") ? 6 : path.startsWith("iron") ? 5
                : path.startsWith("copper") ? 4 : path.startsWith("stone") ? 3 : path.startsWith("golden") ? 2 : 1;
        return base + tier;
    }

    private void restoreSlot() {
        if (previousSlot >= 0 && switchBack.get() && mc.player != null) mc.player.getInventory().setSelectedSlot(previousSlot);
        previousSlot = -1;
    }

    private void lookAt(Entity entity) {
        double dx = entity.getX() - mc.player.getX();
        double dy = (entity.getY() + entity.getHeight() * 0.5) - mc.player.getEyeY();
        double dz = entity.getZ() - mc.player.getZ();
        mc.player.setYaw((float) Math.toDegrees(Math.atan2(dz, dx)) - 90f);
        mc.player.setPitch(MathHelper.clamp((float) -Math.toDegrees(Math.atan2(dy, Math.sqrt(dx * dx + dz * dz))), -90f, 90f));
    }

    @Override
    public void onRenderOverlay(Overlay o) {
        if (!showTarget.get() || target == null) return;
        Vec3d p = target.getLerpedPos(o.tickDelta());
        o.box(target.getBoundingBox().offset(p.x - target.getX(), p.y - target.getY(), p.z - target.getZ()), 0xFFEF4444);
    }
}
