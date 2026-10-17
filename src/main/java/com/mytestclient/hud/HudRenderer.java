package com.mytestclient.hud;

import com.mytestclient.MyTestClient;
import com.mytestclient.event.ClientEvents;
import com.mytestclient.module.Module;
import com.mytestclient.module.Modules;
import com.mytestclient.render.Overlay;
import com.mytestclient.render.Projection;
import com.mytestclient.util.Notifications;
import com.mytestclient.util.Navigation;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.render.RenderTickCounter;
import net.minecraft.util.Identifier;

/**
 * Single rendering hook of the client, registered as a Fabric HUD element. Each frame it draws
 * (1) world overlays of enabled modules, (2) enabled HUD elements, (3) notifications.
 */
public final class HudRenderer {
    private HudRenderer() {
    }

    public static void register() {
        HudElementRegistry.addLast(Identifier.of(MyTestClient.MOD_ID, "overlay"), HudRenderer::render);
    }

    private static void render(DrawContext ctx, RenderTickCounter counter) {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.player == null || mc.world == null || mc.options.hudHidden) return;
        float tickDelta = counter.getTickProgress(true);

        Projection.update();
        Overlay overlay = new Overlay(ctx, tickDelta);
        for (Module module : Modules.all()) {
            if (!module.isEnabled()) continue;
            ClientEvents.safely(module, "overlay", () -> module.onRenderOverlay(overlay));
            overlay.flush();
        }

        Navigation.render(overlay);
        com.mytestclient.util.ElytraAutopilot.render(overlay);
        com.mytestclient.module.movement.PathfinderModule pf = com.mytestclient.module.Modules.get(com.mytestclient.module.movement.PathfinderModule.class);
        if (pf == null || pf.showPath.get()) com.mytestclient.pathing.Travel.render(overlay);
        overlay.flush();

        for (Module module : Modules.all()) {
            if (module instanceof HudModule hud && hud.isEnabled()) {
                ClientEvents.safely(hud, "hud", () -> hud.renderHud(ctx, tickDelta));
            }
        }

        Notifications.render(ctx);
    }
}
