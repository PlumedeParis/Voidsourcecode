package com.voidclient.mixin;

import com.voidclient.VoidClient;
import com.voidclient.gui.Theme;
import com.voidclient.render.Render2D;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.TitleScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** A small VoidClient badge on the title screen (name, version, how to open the menu). */
@Mixin(TitleScreen.class)
public abstract class TitleScreenMixin {
    @Inject(method = "render", at = @At("TAIL"), require = 0)
    private void voidclient$badge(DrawContext ctx, int mouseX, int mouseY, float delta, CallbackInfo ci) {
        try {
            var tr = MinecraftClient.getInstance().textRenderer;
            String version = VoidClient.VERSION, hint = "Right Shift in game: menu";
            int w = Math.max(tr.getWidth("VOID client " + version), tr.getWidth(hint)) + 14;
            Render2D.roundedRect(ctx, 4, 4, w, 28, 6, 0xC00A0A12);
            Render2D.roundedOutline(ctx, 4, 4, w, 28, 6, Theme.BORDER);
            Render2D.hGradient(ctx, 10, 4, w - 12, 1, Theme.accent(), Theme.accent2());
            Render2D.gradientText(ctx, tr, "VOID", 11, 9, Theme.accent(), Theme.accent2());
            ctx.drawText(tr, "client " + version, 11 + tr.getWidth("VOID "), 9, Theme.TEXT, false);
            ctx.drawText(tr, hint, 11, 20, Theme.TEXT_MUTED, false);
        } catch (Throwable ignored) {
            // never break the title screen
        }
    }
}
