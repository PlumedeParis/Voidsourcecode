package com.mytestclient.gui;

import net.minecraft.client.gui.Click;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.input.CharInput;
import net.minecraft.client.input.KeyInput;
import net.minecraft.text.Text;

/**
 * Adapts Minecraft's input events (which change shape between versions — 1.21.9 introduced
 * {@code Click}/{@code KeyInput}/{@code CharInput}) to simple methods. If a future version
 * changes them again, only this class needs updating.
 */
public abstract class BaseScreen extends Screen {
    protected BaseScreen(Text title) {
        super(title);
    }

    protected boolean onMouseClick(double mouseX, double mouseY, int button) {
        return false;
    }

    protected void onMouseRelease(double mouseX, double mouseY, int button) {
    }

    protected void onMouseDrag(double mouseX, double mouseY, int button) {
    }

    protected boolean onScroll(double mouseX, double mouseY, double amount) {
        return false;
    }

    protected boolean onKey(int key, int scancode, int modifiers) {
        return false;
    }

    protected boolean onChar(int codepoint) {
        return false;
    }

    @Override
    public boolean mouseClicked(Click click, boolean doubled) {
        if (onMouseClick(click.x(), click.y(), click.button())) return true;
        return super.mouseClicked(click, doubled);
    }

    @Override
    public boolean mouseReleased(Click click) {
        onMouseRelease(click.x(), click.y(), click.button());
        return super.mouseReleased(click);
    }

    @Override
    public boolean mouseDragged(Click click, double offsetX, double offsetY) {
        onMouseDrag(click.x(), click.y(), click.button());
        return super.mouseDragged(click, offsetX, offsetY);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        if (onScroll(mouseX, mouseY, verticalAmount)) return true;
        return super.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount);
    }

    @Override
    public boolean keyPressed(KeyInput input) {
        if (onKey(input.key(), input.scancode(), input.modifiers())) return true;
        return super.keyPressed(input);
    }

    @Override
    public boolean charTyped(CharInput input) {
        if (onChar(input.codepoint())) return true;
        return super.charTyped(input);
    }

    @Override
    public boolean shouldPause() {
        return false;
    }

    /** Plain dim instead of the vanilla blur. */
    @Override
    public void renderBackground(DrawContext context, int mouseX, int mouseY, float delta) {
        context.fill(0, 0, width, height, 0x80000000);
    }
}
