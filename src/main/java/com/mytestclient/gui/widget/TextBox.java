package com.mytestclient.gui.widget;

import com.mytestclient.gui.Theme;
import com.mytestclient.gui.Ui;
import com.mytestclient.render.Render2D;
import net.minecraft.client.MinecraftClient;
import org.lwjgl.glfw.GLFW;

import java.util.function.Consumer;

/** Single-line text input. Click to focus; supports backspace, Ctrl+Backspace, Ctrl+V and Enter. */
public class TextBox {
    private final String placeholder;
    private final int maxLength;
    private String text = "";
    private Consumer<String> onChange = s -> {
    };
    private Consumer<String> onSubmit = s -> {
    };

    public TextBox(String placeholder, int maxLength) {
        this.placeholder = placeholder;
        this.maxLength = maxLength;
    }

    public TextBox onChange(Consumer<String> onChange) {
        this.onChange = onChange;
        return this;
    }

    public TextBox onSubmit(Consumer<String> onSubmit) {
        this.onSubmit = onSubmit;
        return this;
    }

    public String getText() {
        return text;
    }

    public void setText(String text) {
        this.text = text.length() > maxLength ? text.substring(0, maxLength) : text;
    }

    public void render(Ui ui, int x, int y, int w, int h) {
        boolean focused = ui.focused == this;
        int border = focused ? Theme.accent() : (ui.hover(x, y, w, h) ? 0xFF3A4050 : Theme.BORDER);
        Render2D.roundedRectBordered(ui.ctx, x, y, w, h, 4, Theme.FIELD, border);

        int ty = y + (h - 8) / 2;
        int maxW = w - 10;
        if (text.isEmpty() && !focused) {
            ui.text(ui.ellipsize(placeholder, maxW), x + 5, ty, Theme.TEXT_MUTED);
        } else {
            String visible = text;
            while (!visible.isEmpty() && ui.width(visible) > maxW) visible = visible.substring(1);
            ui.text(visible, x + 5, ty, Theme.TEXT);
            if (focused && (System.currentTimeMillis() / 500) % 2 == 0) {
                int cx = x + 5 + ui.width(visible);
                ui.ctx.fill(cx, ty - 1, cx + 1, ty + 9, Theme.TEXT);
            }
        }
        ui.click(x, y, w, h, (b, mx, my) -> {
            ui.focused = this;
            if (b == 1) {
                setText("");
                onChange.accept(text);
            }
        });
    }

    /** @return true if the key was consumed */
    public boolean keyPressed(int key, int modifiers) {
        boolean ctrl = (modifiers & (GLFW.GLFW_MOD_CONTROL | GLFW.GLFW_MOD_SUPER)) != 0;
        switch (key) {
            case GLFW.GLFW_KEY_BACKSPACE -> {
                if (text.isEmpty()) return true;
                if (ctrl) {
                    String trimmed = text.stripTrailing();
                    int cut = trimmed.lastIndexOf(' ');
                    text = cut < 0 ? "" : trimmed.substring(0, cut + 1);
                } else {
                    text = text.substring(0, text.offsetByCodePoints(text.length(), -1));
                }
                onChange.accept(text);
                return true;
            }
            case GLFW.GLFW_KEY_ENTER, GLFW.GLFW_KEY_KP_ENTER -> {
                onSubmit.accept(text);
                return true;
            }
            case GLFW.GLFW_KEY_V -> {
                if (ctrl) {
                    String clip = MinecraftClient.getInstance().keyboard.getClipboard();
                    if (clip != null) {
                        clip = clip.replaceAll("[\\r\\n\\t]", " ");
                        setText(text + clip);
                        onChange.accept(text);
                    }
                }
                return true;
            }
            default -> {
                return true; // swallow everything else so module keybinds don't fire while typing
            }
        }
    }

    public boolean charTyped(int codepoint) {
        if (codepoint < 32 || codepoint == 127 || text.length() >= maxLength) return true;
        text += new String(Character.toChars(codepoint));
        onChange.accept(text);
        return true;
    }
}
