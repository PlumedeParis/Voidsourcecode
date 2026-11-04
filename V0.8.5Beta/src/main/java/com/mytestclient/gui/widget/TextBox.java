package com.mytestclient.gui.widget;

import com.mytestclient.gui.Theme;
import com.mytestclient.gui.Ui;
import com.mytestclient.render.Colors;
import com.mytestclient.render.Render2D;
import net.minecraft.client.MinecraftClient;
import org.lwjgl.glfw.GLFW;

import java.util.function.Consumer;

/**
 * Single-line text input used by every GUI of the client.
 * <ul>
 *     <li>Click to focus and place the caret, right click to clear.</li>
 *     <li>Left/Right (Ctrl: by word), Home/End, Backspace/Delete (Ctrl: by word).</li>
 *     <li>Ctrl+A selects everything (typing replaces it), Ctrl+C/X/V copy, cut and paste.</li>
 *     <li>Enter submits. The text scrolls so the caret always stays visible.</li>
 * </ul>
 */
public class TextBox {
    private final String placeholder;
    private final int maxLength;
    private String text = "";
    private int cursor;
    private boolean allSelected;
    private int scroll;
    private long lastInput;
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
        String clean = text.length() > maxLength ? text.substring(0, maxLength) : text;
        if (!clean.equals(this.text)) {
            this.text = clean;
            cursor = clean.length();
            allSelected = false;
        }
        cursor = Math.min(cursor, this.text.length());
    }

    /** Selects the whole text (e.g. when a field opens for editing). */
    public void selectAll() {
        allSelected = !text.isEmpty();
        cursor = text.length();
    }

    public void render(Ui ui, int x, int y, int w, int h) {
        boolean focused = ui.focused == this;
        if (!focused) allSelected = false;
        boolean hover = ui.hover(x, y, w, h);
        float ft = ui.anim(this, focused ? 1f : hover ? 0.45f : 0f, 16f);
        int border = Colors.lerp(Theme.BORDER, focused ? Theme.accent() : Theme.BORDER_HOVER, focused ? ft : ft / 0.45f);
        Render2D.roundedRectBordered(ui.ctx, x, y, w, h, 4, Theme.FIELD, border);

        int pad = 5;
        int ty = y + (h - 8) / 2 + 1;
        int innerW = w - pad * 2;
        if (text.isEmpty() && !focused) {
            ui.text(ui.ellipsize(placeholder, innerW), x + pad, ty, Theme.TEXT_MUTED);
        } else {
            // keep the caret inside the visible part
            int caretPx = ui.width(text.substring(0, cursor));
            if (caretPx - scroll > innerW - 1) scroll = caretPx - innerW + 1;
            if (caretPx - scroll < 0) scroll = caretPx;
            scroll = Math.max(0, Math.min(scroll, Math.max(0, ui.width(text) - innerW + 1)));

            ui.ctx.enableScissor(x + 2, y + 1, x + w - 2, y + h - 1);
            if (allSelected) {
                int sw = Math.min(innerW, ui.width(text) - scroll);
                Render2D.rect(ui.ctx, x + pad, ty - 1, sw, 10, Colors.withAlpha(Theme.accent(), 0x70));
            }
            ui.text(text, x + pad - scroll, ty, Theme.TEXT);
            boolean blinkOn = (System.currentTimeMillis() - lastInput) % 1000 < 550;
            if (focused && !allSelected && blinkOn) {
                int cx = x + pad + caretPx - scroll;
                ui.ctx.fill(cx, ty - 1, cx + 1, ty + 9, Theme.accent());
            }
            ui.ctx.disableScissor(); // DrawContext keeps a scissor stack, so the GUI's own clip area is restored
        }
        ui.click(x, y, w, h, (b, mx, my) -> {
            ui.focused = this;
            lastInput = System.currentTimeMillis();
            allSelected = false;
            if (b == 1) {
                setText("");
                onChange.accept(text);
                return;
            }
            cursor = indexAt(ui, (int) mx - (x + pad) + scroll);
        });
    }

    private int indexAt(Ui ui, int px) {
        if (px <= 0) return 0;
        for (int i = 1; i <= text.length(); i++) {
            int before = ui.width(text.substring(0, i - 1)), after = ui.width(text.substring(0, i));
            if (px < (before + after) / 2) return i - 1;
        }
        return text.length();
    }

    private void changed() {
        lastInput = System.currentTimeMillis();
        onChange.accept(text);
    }

    private void insert(String s) {
        if (allSelected) {
            text = "";
            cursor = 0;
            allSelected = false;
        }
        int room = maxLength - text.length();
        if (room <= 0) return;
        if (s.length() > room) s = s.substring(0, room);
        text = text.substring(0, cursor) + s + text.substring(cursor);
        cursor += s.length();
        changed();
    }

    private int wordLeft() {
        int i = cursor;
        while (i > 0 && text.charAt(i - 1) == ' ') i--;
        while (i > 0 && text.charAt(i - 1) != ' ') i--;
        return i;
    }

    private int wordRight() {
        int i = cursor;
        while (i < text.length() && text.charAt(i) == ' ') i++;
        while (i < text.length() && text.charAt(i) != ' ') i++;
        return i;
    }

    /** @return true if the key was consumed */
    public boolean keyPressed(int key, int modifiers) {
        boolean ctrl = (modifiers & (GLFW.GLFW_MOD_CONTROL | GLFW.GLFW_MOD_SUPER)) != 0;
        lastInput = System.currentTimeMillis();
        cursor = Math.min(cursor, text.length());
        switch (key) {
            case GLFW.GLFW_KEY_BACKSPACE, GLFW.GLFW_KEY_DELETE -> {
                if (allSelected) {
                    text = "";
                    cursor = 0;
                    allSelected = false;
                    changed();
                    return true;
                }
                boolean back = key == GLFW.GLFW_KEY_BACKSPACE;
                int from = back ? (ctrl ? wordLeft() : Math.max(0, cursor - 1)) : cursor;
                int to = back ? cursor : (ctrl ? wordRight() : Math.min(text.length(), cursor + 1));
                if (from == to) return true;
                text = text.substring(0, from) + text.substring(to);
                cursor = from;
                changed();
                return true;
            }
            case GLFW.GLFW_KEY_LEFT -> {
                cursor = allSelected ? 0 : ctrl ? wordLeft() : Math.max(0, cursor - 1);
                allSelected = false;
                return true;
            }
            case GLFW.GLFW_KEY_RIGHT -> {
                cursor = allSelected ? text.length() : ctrl ? wordRight() : Math.min(text.length(), cursor + 1);
                allSelected = false;
                return true;
            }
            case GLFW.GLFW_KEY_HOME -> {
                cursor = 0;
                allSelected = false;
                return true;
            }
            case GLFW.GLFW_KEY_END -> {
                cursor = text.length();
                allSelected = false;
                return true;
            }
            case GLFW.GLFW_KEY_ENTER, GLFW.GLFW_KEY_KP_ENTER -> {
                allSelected = false;
                onSubmit.accept(text);
                return true;
            }
            case GLFW.GLFW_KEY_A -> {
                if (ctrl) selectAll();
                return true;
            }
            case GLFW.GLFW_KEY_C, GLFW.GLFW_KEY_X -> {
                if (ctrl && !text.isEmpty()) {
                    MinecraftClient.getInstance().keyboard.setClipboard(text);
                    if (key == GLFW.GLFW_KEY_X) {
                        text = "";
                        cursor = 0;
                        allSelected = false;
                        changed();
                    }
                }
                return true;
            }
            case GLFW.GLFW_KEY_V -> {
                if (ctrl) {
                    String clip = MinecraftClient.getInstance().keyboard.getClipboard();
                    if (clip != null && !clip.isEmpty()) insert(clip.replaceAll("[\\r\\n\\t]", " "));
                }
                return true;
            }
            default -> {
                return true; // swallow everything else so module keybinds don't fire while typing
            }
        }
    }

    public boolean charTyped(int codepoint) {
        if (codepoint < 32 || codepoint == 127) return true;
        insert(new String(Character.toChars(codepoint)));
        return true;
    }
}
