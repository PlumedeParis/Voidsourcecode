package com.mytestclient.module.visuals;

import com.mytestclient.module.Category;
import com.mytestclient.module.Module;
import com.mytestclient.module.Modules;
import com.mytestclient.module.Risk;
import com.mytestclient.module.movement.FreecamModule;
import com.mytestclient.module.setting.BooleanSetting;
import com.mytestclient.module.setting.ColorSetting;
import com.mytestclient.module.setting.ListSetting;
import com.mytestclient.module.setting.ModeSetting;
import com.mytestclient.module.setting.NumberSetting;
import com.mytestclient.render.Colors;
import com.mytestclient.render.Overlay;
import com.mytestclient.render.Projection;
import com.mytestclient.render.Render2D;
import com.mytestclient.util.EntityKind;
import com.mytestclient.util.Friends;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.registry.Registries;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;

/**
 * ESP: highlights entities through walls.
 * <ul>
 *     <li>Glow: vanilla outline (as with the Glowing effect).</li>
 *     <li>2D: a clean screen-space frame with a health bar.</li>
 *     <li>3D: wireframe bounding box.</li>
 * </ul>
 * Targets are chosen by category and/or from a picker menu of entity types (no typing).
 * Friends get their own colour. Optional tracers and labels.
 * <p>
 * ⚠ CLIENT_SIDE: only draws data your client already has, so servers cannot see it, but it is
 * an information advantage most multiplayer servers' rules forbid.
 */
public class EspModule extends Module {
    private final ModeSetting mode = add(new ModeSetting("Style", "Glow: vanilla outline. 2D: frame + health bar. 3D: wireframe box.", "2D", "Glow", "2D", "3D", "Glow + 2D"));
    private final NumberSetting range = add(new NumberSetting("Range", "Maximum distance in blocks.", 96, 8, 512, 1));
    private final BooleanSetting players = add(new BooleanSetting("Players", "Highlight other players.", true));
    private final BooleanSetting hostiles = add(new BooleanSetting("Hostiles", "Highlight hostile mobs.", true));
    private final BooleanSetting passives = add(new BooleanSetting("Passives", "Highlight animals and other peaceful mobs.", false));
    private final BooleanSetting items = add(new BooleanSetting("Items", "Highlight dropped items.", false));
    private final BooleanSetting useList = add(new BooleanSetting("Extra Entities", "Also highlight the entity types picked in the list below.", false));
    private final ListSetting extra = add(new ListSetting("Entity List", "Extra entity types to highlight.", ListSetting.Kind.ENTITY,
            "minecraft:villager", "minecraft:end_crystal").visibleWhen(useList::get));
    private final BooleanSetting healthBar = add(new BooleanSetting("Health Bar", "Health bar next to 2D frames.", true));
    private final BooleanSetting tracers = add(new BooleanSetting("Tracers", "Draw a line from your crosshair to each target.", false));
    private final BooleanSetting labels = add(new BooleanSetting("Labels", "Show name, health and distance above targets.", true));
    private final ColorSetting playerColor = add(new ColorSetting("Player Color", "Colour for players.", 0xFF6C8CFF));
    private final ColorSetting friendColor = add(new ColorSetting("Friend Color", "Colour for friends (\\friend add).", 0xFF22D3EE));
    private final ColorSetting hostileColor = add(new ColorSetting("Hostile Color", "Colour for hostile mobs.", 0xFFEF4444));
    private final ColorSetting passiveColor = add(new ColorSetting("Passive Color", "Colour for passive mobs.", 0xFF4ADE80));
    private final ColorSetting itemColor = add(new ColorSetting("Item Color", "Colour for items.", 0xFFFACC15));
    private final ColorSetting extraColor = add(new ColorSetting("List Color", "Colour for entities from the list.", 0xFFF472B6));

    public EspModule() {
        super("ESP", "See entities through walls: outlines, 2D frames with health, tracers and labels. Pick entity types from a menu.", Category.VISUALS);
        setRisk(Risk.CLIENT_SIDE, "Visual only — servers cannot see it — but it is an information advantage that most servers' rules forbid.");
    }

    /** Colour for the glow outline (used by the mixins), or null if the entity is not highlighted. */
    public Integer glowColor(Entity entity) {
        if (!mode.get().contains("Glow")) return null;
        return colorFor(entity);
    }

    private Integer colorFor(Entity entity) {
        if (mc.player == null) return null;
        if (entity == mc.player && !Modules.active(FreecamModule.class)) return null;
        if (mc.player.squaredDistanceTo(entity) > range.get() * range.get()) return null;
        if (useList.get() && extra.contains(Registries.ENTITY_TYPE.getId(entity.getType()).toString())) return extraColor.get();
        return switch (EntityKind.of(entity)) {
            case PLAYER -> !players.get() ? null : Friends.isFriend(entity.getName().getString()) ? friendColor.get() : playerColor.get();
            case HOSTILE -> hostiles.get() ? hostileColor.get() : null;
            case PASSIVE -> passives.get() ? passiveColor.get() : null;
            case ITEM -> items.get() ? itemColor.get() : null;
            case OTHER -> null;
        };
    }

    @Override
    public void onRenderOverlay(Overlay overlay) {
        float td = overlay.tickDelta();
        boolean frames = mode.get().contains("2D");
        for (Entity entity : mc.world.getEntities()) {
            Integer color = colorFor(entity);
            if (color == null) continue;
            Vec3d pos = entity.getLerpedPos(td);
            Box box = entity.getBoundingBox().offset(pos.x - entity.getX(), pos.y - entity.getY(), pos.z - entity.getZ());
            if (mode.is("3D")) overlay.box(box, color);
            if (tracers.get()) overlay.tracer(pos.x, pos.y + entity.getHeight() / 2, pos.z, color);
            if (frames) {
                overlay.flush();
                drawFrame(overlay.ctx(), entity, box, color);
            } else if (labels.get()) {
                overlay.label(pos.x, box.maxY + 0.3, pos.z, label(entity), color);
            }
        }
    }

    /** 2D frame: the screen rectangle around the projected bounding box, with corners and health bar. */
    private void drawFrame(DrawContext ctx, Entity entity, Box b, int color) {
        double minX = Double.MAX_VALUE, minY = Double.MAX_VALUE, maxX = -Double.MAX_VALUE, maxY = -Double.MAX_VALUE;
        for (int i = 0; i < 8; i++) {
            double[] p = Projection.project((i & 1) == 0 ? b.minX : b.maxX, (i & 2) == 0 ? b.minY : b.maxY, (i & 4) == 0 ? b.minZ : b.maxZ);
            if (p == null) return;
            minX = Math.min(minX, p[0]);
            minY = Math.min(minY, p[1]);
            maxX = Math.max(maxX, p[0]);
            maxY = Math.max(maxY, p[1]);
        }
        int x1 = (int) minX, y1 = (int) minY, x2 = (int) maxX, y2 = (int) maxY;
        if (x2 - x1 < 2 || y2 - y1 < 2) return;
        int w = x2 - x1, h = y2 - y1;
        int c = color | 0xFF000000;
        // dark outline then coloured corners
        Render2D.outline(ctx, x1 - 1, y1 - 1, w + 2, h + 2, 0x80000000);
        int cl = Math.max(3, Math.min(w, h) / 4);
        ctx.fill(x1, y1, x1 + cl, y1 + 1, c);
        ctx.fill(x1, y1, x1 + 1, y1 + cl, c);
        ctx.fill(x2 - cl, y1, x2, y1 + 1, c);
        ctx.fill(x2 - 1, y1, x2, y1 + cl, c);
        ctx.fill(x1, y2 - 1, x1 + cl, y2, c);
        ctx.fill(x1, y2 - cl, x1 + 1, y2, c);
        ctx.fill(x2 - cl, y2 - 1, x2, y2, c);
        ctx.fill(x2 - 1, y2 - cl, x2, y2, c);

        if (healthBar.get() && entity instanceof LivingEntity living && living.getMaxHealth() > 0) {
            float frac = Math.max(0, Math.min(1, living.getHealth() / living.getMaxHealth()));
            ctx.fill(x1 - 4, y1 - 1, x1 - 2, y2 + 1, 0xA0000000);
            int filled = Math.round(h * frac);
            ctx.fill(x1 - 4, y2 - filled, x1 - 2, y2, Colors.health(frac));
        }
        if (labels.get()) {
            String text = label(entity);
            int tw = mc.textRenderer.getWidth(text);
            int tx = x1 + w / 2 - tw / 2, ty = y1 - 11;
            Render2D.roundedRect(ctx, tx - 2, ty - 1, tw + 4, 10, 2, 0x90101014);
            ctx.drawText(mc.textRenderer, text, tx, ty, c, false);
        }
    }

    private String label(Entity entity) {
        StringBuilder sb = new StringBuilder(entity.getName().getString());
        if (entity instanceof LivingEntity living) sb.append(' ').append(Math.round(living.getHealth())).append('❤');
        sb.append(' ').append(Math.round(Math.sqrt(mc.player.squaredDistanceTo(entity)))).append('m');
        if (entity instanceof PlayerEntity p && Friends.isFriend(p.getName().getString())) sb.append(" ★");
        return sb.toString();
    }
}
