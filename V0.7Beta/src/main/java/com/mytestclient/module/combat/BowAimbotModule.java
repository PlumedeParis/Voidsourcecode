package com.mytestclient.module.combat;

import com.mytestclient.module.Category;
import com.mytestclient.module.Module;
import com.mytestclient.module.Risk;
import com.mytestclient.module.setting.BooleanSetting;
import com.mytestclient.module.setting.NumberSetting;
import com.mytestclient.render.Overlay;
import com.mytestclient.util.CombatUtil;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.item.BowItem;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;

/**
 * Bow Aimbot: while you draw a bow (or a trident), aims at the target closest to your crosshair,
 * accounting for arrow drop and where the target is moving. ⚠ DETECTABLE: automated aim.
 */
public class BowAimbotModule extends Module {
    private final BooleanSetting players = add(new BooleanSetting("Players", "Aim at players (friends excluded).", true));
    private final BooleanSetting hostiles = add(new BooleanSetting("Monsters", "Aim at hostile mobs.", true));
    private final BooleanSetting animals = add(new BooleanSetting("Animals", "Aim at passive mobs.", false));
    private final NumberSetting range = add(new NumberSetting("Range", "Maximum distance.", 60, 10, 120, 5));
    private final NumberSetting fov = add(new NumberSetting("FOV", "Only targets within this angle of your crosshair.", 60, 10, 180, 5));
    private final BooleanSetting predict = add(new BooleanSetting("Predict", "Lead moving targets.", true));

    private LivingEntity target;

    public BowAimbotModule() {
        super("Bow Aimbot", "Aims your bow for you, with arrow drop and target movement.", Category.COMBAT);
        setRisk(Risk.DETECTABLE, "Automated aim; perfect long-range shots are easy to flag.");
    }

    @Override
    public void onTick() {
        target = null;
        ItemStack using = mc.player.getActiveItem();
        if (!mc.player.isUsingItem() || !(using.isOf(Items.BOW) || using.isOf(Items.TRIDENT))) return;
        double speed = using.isOf(Items.BOW) ? Math.max(0.1, BowItem.getPullProgress(mc.player.getItemUseTime())) * 3.0 : 2.5;

        Vec3d eyes = mc.player.getEyePos(), look = mc.player.getRotationVector();
        double best = Math.cos(Math.toRadians(fov.get() / 2));
        for (Entity e : mc.world.getEntities()) {
            if (!CombatUtil.isTarget(e, players.get(), hostiles.get(), animals.get())) continue;
            if (mc.player.squaredDistanceTo(e) > range.get() * range.get() || !CombatUtil.canSee(e)) continue;
            double dot = CombatUtil.body(e).subtract(eyes).normalize().dotProduct(look);
            if (dot > best) {
                best = dot;
                target = (LivingEntity) e;
            }
        }
        if (target == null) return;

        Vec3d aim = CombatUtil.body(target);
        double ticks = aim.distanceTo(eyes) / speed;
        if (predict.get()) {
            Vec3d v = target.getVelocity();
            aim = aim.add(v.x * ticks, 0, v.z * ticks);
        }
        double dx = aim.x - eyes.x, dz = aim.z - eyes.z, dy = aim.y - eyes.y;
        double horizontal = Math.sqrt(dx * dx + dz * dz);
        // ballistic angle for speed v and gravity g (drag ignored, a small error at long range)
        double g = 0.05, v2 = speed * speed;
        double root = v2 * v2 - g * (g * horizontal * horizontal + 2 * dy * v2);
        double pitch = root < 0 ? Math.atan2(dy, horizontal) : Math.atan((v2 - Math.sqrt(root)) / (g * horizontal));
        mc.player.setYaw((float) Math.toDegrees(Math.atan2(dz, dx)) - 90f);
        mc.player.setPitch(MathHelper.clamp((float) -Math.toDegrees(pitch), -90f, 90f));
    }

    @Override
    public void onRenderOverlay(Overlay o) {
        if (target == null) return;
        Vec3d p = target.getLerpedPos(o.tickDelta());
        o.box(target.getBoundingBox().offset(p.x - target.getX(), p.y - target.getY(), p.z - target.getZ()), 0xFFF59E0B);
    }
}
