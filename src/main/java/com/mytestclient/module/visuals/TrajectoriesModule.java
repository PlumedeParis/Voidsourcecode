package com.mytestclient.module.visuals;

import com.mytestclient.module.Category;
import com.mytestclient.module.Module;
import com.mytestclient.module.setting.ColorSetting;
import com.mytestclient.render.Overlay;
import net.minecraft.entity.Entity;
import net.minecraft.item.BowItem;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.RaycastContext;

import java.util.List;

/**
 * Trajectories: shows where your bow, crossbow, trident, ender pearl, snowball, egg or splash
 * potion will land — the flight curve plus a marker on the block or entity it hits. Uses the
 * vanilla projectile physics (speed, gravity, drag). Purely visual, no warning.
 */
public class TrajectoriesModule extends Module {
    private final ColorSetting color = add(new ColorSetting("Color", "Colour of the flight path.", 0xFFFACC15));
    private final ColorSetting hitColor = add(new ColorSetting("Hit Color", "Colour when the path hits an entity.", 0xFFEF4444));

    private record Physics(double speed, double gravity, double drag, float pitchOffset) {
    }

    public TrajectoriesModule() {
        super("Trajectories", "Shows where arrows, tridents, pearls, snowballs and potions will land.", Category.VISUALS);
    }

    private Physics physicsFor(ItemStack stack) {
        if (stack.isOf(Items.BOW)) {
            float pull = mc.player.isUsingItem() ? BowItem.getPullProgress(mc.player.getItemUseTime()) : 1f;
            if (pull < 0.1f) return null;
            return new Physics(pull * 3.0, 0.05, 0.99, 0);
        }
        if (stack.isOf(Items.CROSSBOW)) return new Physics(3.15, 0.05, 0.99, 0);
        if (stack.isOf(Items.TRIDENT)) return new Physics(2.5, 0.05, 0.99, 0);
        if (stack.isOf(Items.ENDER_PEARL) || stack.isOf(Items.SNOWBALL) || stack.isOf(Items.EGG)) return new Physics(1.5, 0.03, 0.99, 0);
        if (stack.isOf(Items.SPLASH_POTION) || stack.isOf(Items.LINGERING_POTION)) return new Physics(0.5, 0.05, 0.99, -20);
        if (stack.isOf(Items.EXPERIENCE_BOTTLE)) return new Physics(0.7, 0.07, 0.99, -20);
        return null;
    }

    @Override
    public void onRenderOverlay(Overlay o) {
        Physics physics = physicsFor(mc.player.getMainHandStack());
        if (physics == null) physics = physicsFor(mc.player.getOffHandStack());
        if (physics == null) return;

        float td = o.tickDelta();
        Vec3d base = mc.player.getLerpedPos(td);
        Vec3d pos = new Vec3d(base.x, base.y + mc.player.getStandingEyeHeight() - 0.1, base.z);
        double yaw = Math.toRadians(mc.player.getYaw(td));
        double pitch = Math.toRadians(mc.player.getPitch(td) + physics.pitchOffset);
        Vec3d vel = new Vec3d(-Math.sin(yaw) * Math.cos(pitch), -Math.sin(pitch), Math.cos(yaw) * Math.cos(pitch)).multiply(physics.speed);

        int c = color.get();
        for (int step = 0; step < 300; step++) {
            Vec3d next = pos.add(vel);
            // entity hit
            List<Entity> entities = mc.world.getOtherEntities(mc.player, new Box(pos, next).expand(0.3), Entity::canHit);
            for (Entity e : entities) {
                if (e.getBoundingBox().expand(0.3).raycast(pos, next).isPresent()) {
                    o.line(pos.x, pos.y, pos.z, next.x, next.y, next.z, hitColor.get());
                    o.box(e.getBoundingBox(), hitColor.get());
                    return;
                }
            }
            // block hit
            BlockHitResult hit = mc.world.raycast(new RaycastContext(pos, next, RaycastContext.ShapeType.COLLIDER, RaycastContext.FluidHandling.NONE, mc.player));
            if (hit.getType() != HitResult.Type.MISS) {
                Vec3d h = hit.getPos();
                o.line(pos.x, pos.y, pos.z, h.x, h.y, h.z, c);
                o.box(new Box(h.x - 0.15, h.y - 0.15, h.z - 0.15, h.x + 0.15, h.y + 0.15, h.z + 0.15), c);
                return;
            }
            o.line(pos.x, pos.y, pos.z, next.x, next.y, next.z, c);
            pos = next;
            double drag = mc.world.getBlockState(net.minecraft.util.math.BlockPos.ofFloored(pos)).getFluidState().isEmpty() ? physics.drag : 0.6;
            vel = vel.multiply(drag).add(0, -physics.gravity, 0);
            if (pos.y < mc.world.getBottomY() - 16) return;
        }
    }
}
