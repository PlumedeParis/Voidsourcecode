package com.mytestclient.gui;

import com.mytestclient.gui.widget.TextBox;
import com.mytestclient.program.BlockTypes;
import com.mytestclient.program.BlockTypes.Category;
import com.mytestclient.program.BlockTypes.Def;
import com.mytestclient.program.BlockTypes.Kind;
import com.mytestclient.program.BlockTypes.Param;
import com.mytestclient.program.BlockTypes.Shape;
import com.mytestclient.program.ProgBlock;
import com.mytestclient.program.Program;
import com.mytestclient.program.ProgramManager;
import com.mytestclient.program.ProgramRunner;
import com.mytestclient.render.Colors;
import com.mytestclient.render.Render2D;
import com.mytestclient.util.KeyNames;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.text.Text;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Player Programming editor, Scratch style: categories and a block palette on the left, the
 * script on the right under a "when [key] pressed" hat. Drag blocks from the palette into the
 * script, drag them around (C-blocks take blocks inside), drop them on the palette to delete,
 * click fields to edit them, right-click a block to duplicate/delete it.
 */
public class ProgramEditorScreen extends BaseScreen {
    private static final int TOP = 30, CATS_W = 62, PALETTE_W = 190;
    private static final int BH = 20, INDENT = 14, C_BOTTOM = 12, ELSE_H = 16, EMPTY_H = 14, FIELD_H = 13;

    private static int programIndex;
    private static Category category = BlockTypes.MOTION;

    private final Screen parent;
    private final Ui ui = new Ui();
    private final ScrollState paletteScroll = new ScrollState();
    private int scrollX, scrollY;

    // drag & drop
    private record Slot(List<ProgBlock> list, int index, int x, int y) {
    }

    private final List<Slot> slots = new ArrayList<>();
    private ProgBlock dragging;
    private List<ProgBlock> dragFrom;
    private int dragFromIndex, grabDX, grabDY;
    private ProgBlock pending;
    private List<ProgBlock> pendingList;
    private int pendingIndex;
    private double pressX, pressY;
    private boolean pendingFromPalette;

    // field editing / popups
    private ProgBlock editBlock;
    private String editKey;
    private Kind editKind;
    private int editX, editY, editW;
    private final TextBox fieldBox = new TextBox("", 120);
    private final TextBox renameBox = new TextBox("Program name", 40);
    private boolean renaming, listeningKey, confirmDelete;
    private List<String> popupOptions;
    private java.util.function.Consumer<String> popupAction;
    private int popupX, popupY, popupScroll;
    private ProgBlock menuBlock;
    private List<ProgBlock> menuList;
    private int menuX, menuY;

    private int wsX, wsY, wsW, wsH;

    public ProgramEditorScreen(Screen parent) {
        super(Text.literal("Player Programming"));
        this.parent = parent;
        fieldBox.onSubmit(s -> commitField());
        renameBox.onSubmit(s -> commitRename());
    }

    private Program program() {
        List<Program> list = ProgramManager.programs();
        if (list.isEmpty()) list.add(new Program("My program"));
        programIndex = Math.floorMod(programIndex, list.size());
        return list.get(programIndex);
    }

    // ------------------------------------------------------------------ labels

    private record Token(String text, String key) {
    }

    private static final Map<String, List<Token>> TOKENS = new HashMap<>();

    private static List<Token> tokens(String label, String prefix) {
        return TOKENS.computeIfAbsent(prefix + label, k -> {
            List<Token> out = new ArrayList<>();
            int i = 0;
            while (i < label.length()) {
                int a = label.indexOf('{', i);
                if (a < 0) {
                    out.add(new Token(label.substring(i), null));
                    break;
                }
                if (a > i) out.add(new Token(label.substring(i, a), null));
                int b = label.indexOf('}', a);
                out.add(new Token(null, prefix + label.substring(a + 1, b)));
                i = b + 1;
            }
            return out;
        });
    }

    private Param param(ProgBlock b, String key) {
        if (key.startsWith("c.")) {
            BlockTypes.CondDef c = BlockTypes.cond(b.arg("cond"));
            return c == null ? null : c.params().get(key.substring(2));
        }
        Def d = BlockTypes.get(b.type);
        return d == null ? null : d.params().get(key);
    }

    private String fieldText(ProgBlock b, String key, Kind kind) {
        String v = b.arg(key);
        if (kind == Kind.CHOICE || kind == Kind.MODULE || kind == Kind.PROGRAM || kind == Kind.VAR) v = (v.isEmpty() ? "…" : v) + " ▾";
        if (v.isEmpty()) v = " ";
        return ui.ellipsize(v, 110);
    }

    private int fieldWidth(ProgBlock b, String key, Kind kind) {
        if (kind == Kind.COND) return condWidth(b);
        return Math.max(14, ui.width(fieldText(b, key, kind)) + 8);
    }

    private int condWidth(ProgBlock b) {
        BlockTypes.CondDef c = BlockTypes.cond(b.arg("cond"));
        int w = 10 + ui.width(b.arg("not").equals("true") ? "not" : "◇") + 4;
        if (c == null) return w + ui.width("?") + 10;
        for (Token t : tokens(c.label(), "c.")) {
            if (t.text() != null) w += ui.width(t.text());
            else {
                Param p = param(b, t.key());
                w += (p == null ? 14 : fieldWidth(b, t.key(), p.kind())) + 2;
            }
        }
        return w + ui.width(" ▾") + 8;
    }

    private int headerWidth(ProgBlock b) {
        Def d = BlockTypes.get(b.type);
        int w = 16;
        for (Token t : tokens(d.label(), "")) {
            if (t.text() != null) w += ui.width(t.text());
            else {
                Param p = param(b, t.key());
                w += (p == null ? 14 : fieldWidth(b, t.key(), p.kind())) + 2;
            }
        }
        return Math.max(w, 60);
    }

    // ------------------------------------------------------------------ block drawing

    /** Draws a block (and what's inside). {@code live}: in the script (registers clicks/slots). @return height */
    private int drawBlock(ProgBlock b, int x, int y, boolean live, List<ProgBlock> parent, int index) {
        DrawContext ctx = ui.ctx;
        Def d = BlockTypes.get(b.type);
        int color = d.category().color(), dark = Colors.shade(color, -0.25f);
        int w = headerWidth(b);
        boolean c = d.shape() == Shape.C || d.shape() == Shape.IF_ELSE;

        // body first (so the header draws over the joins)
        int h = BH;
        int bodyTop = y + BH, bodyEnd = bodyTop, elseEnd = 0;
        if (c) {
            bodyEnd = drawList(b.body, x + INDENT, bodyTop, live, true);
            if (d.shape() == Shape.IF_ELSE) {
                int elseTop = bodyEnd + ELSE_H;
                elseEnd = drawList(b.elseBody, x + INDENT, elseTop, live, true);
                h = elseEnd - y + C_BOTTOM;
            } else h = bodyEnd - y + C_BOTTOM;
        }

        // shape
        Render2D.roundedRect(ctx, x, y, w, BH, 4, color);
        ctx.fill(x + 1, y + BH - 1, x + w - 1, y + BH, dark);
        if (d.shape() != Shape.CAP && !c) ctx.fill(x + 10, y + BH, x + 22, y + BH + 2, color); // tab under the block
        if (c) {
            ctx.fill(x, y + BH - 2, x + INDENT, (d.shape() == Shape.IF_ELSE ? elseEnd : bodyEnd), color);
            if (d.shape() == Shape.IF_ELSE) {
                Render2D.roundedRect(ctx, x, bodyEnd, Math.max(60, w / 2), ELSE_H, 3, color);
                ctx.drawText(ui.tr, "else", x + 8, bodyEnd + 4, 0xFFFFFFFF, true);
            }
            int bottom = y + h - C_BOTTOM;
            Render2D.roundedRect(ctx, x, bottom, Math.max(60, w / 2), C_BOTTOM, 3, color);
            ctx.fill(x + 1, bottom + C_BOTTOM - 1, x + Math.max(60, w / 2) - 1, bottom + C_BOTTOM, dark);
            if (d.shape() != Shape.CAP && !b.type.equals("forever")) ctx.fill(x + 10, y + h, x + 22, y + h + 2, color);
        }
        if (d.shape() == Shape.CAP) ctx.fill(x + 1, y + BH - 2, x + w - 1, y + BH, dark);

        // running highlight
        ProgramRunner runner = ProgramManager.runner(program());
        if (live && runner != null && runner.currentBlock() == b) {
            Render2D.glow(ctx, x, y, w, BH, 4, 4, 0xFFFFE066, 1f);
            Render2D.roundedOutline(ctx, x - 1, y - 1, w + 2, BH + 2, 5, 0xFFFFE066);
        }

        // header click: drag / menu
        if (live) {
            ui.click(x, y, w, BH, (btn, mx, my) -> {
                if (btn == 1) {
                    menuBlock = b;
                    menuList = parent;
                    menuX = (int) mx;
                    menuY = (int) my;
                    return;
                }
                pending = b;
                pendingList = parent;
                pendingIndex = parent.indexOf(b);
                pendingFromPalette = false;
                pressX = mx;
                pressY = my;
                grabDX = (int) mx - x;
                grabDY = (int) my - y;
            });
            if (ui.hover(x, y, w, BH) && dragging == null && popupOptions == null) ui.tooltip(d.help());
        }

        // label + fields
        int tx = x + 8;
        for (Token t : tokens(d.label(), "")) {
            if (t.text() != null) {
                ctx.drawText(ui.tr, t.text(), tx, y + 6, 0xFFFFFFFF, true);
                tx += ui.width(t.text());
            } else {
                Param p = param(b, t.key());
                if (p == null) continue;
                tx += drawField(b, t.key(), p.kind(), tx, y + (BH - FIELD_H) / 2, color, live) + 2;
            }
        }
        return h;
    }

    /** One field. @return width */
    private int drawField(ProgBlock b, String key, Kind kind, int x, int y, int blockColor, boolean live) {
        DrawContext ctx = ui.ctx;
        if (kind == Kind.COND) return drawCondition(b, x, y - 1, live);
        int w = fieldWidth(b, key, kind);
        boolean dropdown = kind == Kind.CHOICE || kind == Kind.MODULE || kind == Kind.PROGRAM || kind == Kind.VAR;
        boolean editing = live && editBlock == b && key.equals(editKey);
        int bg = dropdown ? Colors.shade(blockColor, -0.3f) : 0xFFFFFFFF;
        int fg = dropdown ? 0xFFFFFFFF : 0xFF575E75;
        if (live && ui.hover(x, y, w, FIELD_H)) bg = dropdown ? Colors.shade(blockColor, -0.4f) : 0xFFEFF1FA;
        Render2D.roundedRect(ctx, x, y, w, FIELD_H, kind == Kind.NUMBER || kind == Kind.COORD ? FIELD_H / 2 : 3, bg);
        if (!editing) ctx.drawText(ui.tr, fieldText(b, key, kind), x + 4, y + 3, fg, false);
        if (live) {
            ui.click(x, y, w, FIELD_H, (btn, mx, my) -> openField(b, key, kind, x, y, w));
            if (ui.hover(x, y, w, FIELD_H) && popupOptions == null) {
                String hint = switch (kind) {
                    case COORD -> "Number, ~ (your position) or ~5 (relative). {var:name.x} works too.";
                    case ITEM -> "Item id or part of it (e.g. diamond, orange_shulker_box). 'any' = every item.";
                    case BLOCK -> "Block id or part of it (e.g. chest, orange_shulker_box, diamond).";
                    case ENTITY -> "player, monster, animal, any, or an id (zombie, cow...).";
                    case TEXT -> "Text. {x} {y} {z} {health} {hunger} {name} {var:name} are replaced.";
                    default -> null;
                };
                if (hint != null) ui.tooltip(hint);
            }
        }
        return w;
    }

    private int drawCondition(ProgBlock b, int x, int y, boolean live) {
        DrawContext ctx = ui.ctx;
        int w = condWidth(b), h = FIELD_H + 2;
        int color = BlockTypes.COND_COLOR;
        // hexagon-ish: pointed ends
        Render2D.rect(ctx, x + 4, y, w - 8, h, color);
        for (int i = 0; i < 4; i++) {
            int inset = 4 - i;
            ctx.fill(x + inset - 1, y + i * 2, x + 4, y + h - i * 2, color);
            ctx.fill(x + w - 4, y + i * 2, x + w - inset + 1, y + h - i * 2, color);
        }
        int cx = x + 6;
        // "not" chip
        boolean not = b.arg("not").equals("true");
        String nt = not ? "not" : "◇";
        int nw = ui.width(nt) + 4;
        Render2D.roundedRect(ctx, cx, y + 2, nw, h - 4, 3, not ? 0xFFE6505F : Colors.shade(color, -0.25f));
        ctx.drawText(ui.tr, nt, cx + 2, y + 4, 0xFFFFFFFF, false);
        if (live) {
            ui.click(cx, y + 2, nw, h - 4, (btn, mx, my) -> b.args.put("not", not ? "false" : "true"));
            if (ui.hover(cx, y + 2, nw, h - 4)) ui.tooltip("Click: invert the condition (not).");
        }
        cx += nw + 4;
        BlockTypes.CondDef c = BlockTypes.cond(b.arg("cond"));
        if (c != null) {
            for (Token t : tokens(c.label(), "c.")) {
                if (t.text() != null) {
                    ctx.drawText(ui.tr, t.text(), cx, y + 4, 0xFFFFFFFF, true);
                    cx += ui.width(t.text());
                } else {
                    Param p = param(b, t.key());
                    if (p == null) continue;
                    cx += drawField(b, t.key(), p.kind(), cx, y + 1, color, live) + 2;
                }
            }
        }
        ctx.drawText(ui.tr, " ▾", cx, y + 4, 0xFFFFFFFF, true);
        if (live) {
            ui.click(cx, y, ui.width(" ▾") + 2, h, (btn, mx, my) -> openPopup(BlockTypes.conditionIds(), (int) mx, y + h + 2, id -> BlockTypes.applyConditionDefaults(b, id), true));
            if (ui.hover(cx, y, ui.width(" ▾") + 2, h)) ui.tooltip("Pick the condition.");
        }
        return w;
    }

    /** Draws a list of blocks, registering drop slots. @return bottom y */
    private int drawList(List<ProgBlock> list, int x, int y, boolean live, boolean inner) {
        if (live) slots.add(new Slot(list, 0, x, y));
        if (list.isEmpty()) return y + (inner ? EMPTY_H : 0);
        int cy = y;
        for (int i = 0; i < list.size(); i++) {
            ProgBlock b = list.get(i);
            cy += drawBlock(b, x, cy, live, list, i);
            if (live) slots.add(new Slot(list, i + 1, x, cy));
        }
        return cy;
    }

    // ------------------------------------------------------------------ frame

    @Override
    public void renderBackground(DrawContext ctx, int mouseX, int mouseY, float delta) {
        ctx.fill(0, 0, width, height, 0xFF0B0B12);
    }

    @Override
    public void render(DrawContext ctx, int mouseX, int mouseY, float delta) {
        super.render(ctx, mouseX, mouseY, delta);
        ui.beginFrame(ctx, textRenderer, mouseX, mouseY, width, height);
        slots.clear();
        Program prog = program();
        wsX = CATS_W + PALETTE_W;
        wsY = TOP;
        wsW = width - wsX;
        wsH = height - TOP;

        drawWorkspace(ctx, prog);
        drawCategories(ctx);
        drawPalette(ctx);
        drawTopBar(ctx, prog);

        // dragged block on top
        if (dragging != null) {
            Slot target = dropSlot(mouseX, mouseY);
            if (target != null && mouseX > wsX) {
                Render2D.glow(ctx, target.x(), target.y() - 1, 120, 3, 1, 3, 0xFFFFFFFF, 0.8f);
                Render2D.roundedRect(ctx, target.x(), target.y() - 1, 120, 3, 1, 0xFFFFFFFF);
            }
            if (mouseX < wsX) {
                ctx.fill(0, TOP, wsX, height, 0x50E6505F);
                Render2D.outline(ctx, 2, TOP + 2, wsX - 4, height - TOP - 4, 0xC0E6505F);
                String t = "✘  Drop here to delete";
                ui.text(t, (wsX - ui.width(t)) / 2, height / 2 - 4, 0xFFFFFFFF);
            }
            Render2D.shadow(ctx, mouseX - grabDX, mouseY - grabDY + 2, headerWidth(dragging), BH, 4, 6, 0x90);
            drawBlock(dragging, mouseX - grabDX, mouseY - grabDY, false, null, 0);
        }
        drawPopups(ctx);
        ui.endFrame();
    }

    private void drawTopBar(DrawContext ctx, Program prog) {
        ctx.fill(0, 0, width, TOP, 0xF20F0F18);
        Render2D.hGradient(ctx, 0, TOP - 1, width, 1, Theme.accent(), Theme.accent2());
        List<Program> list = ProgramManager.programs();
        String keyLabel = listeningKey ? "Press a key..." : "Key: " + (prog.key > 0 ? KeyNames.name(prog.key) : "none");
        // left part needs ~360px + the title, right part the key chip, Run and Close
        int rightW = 8 + 44 + 60 + ui.width(keyLabel) + 18;
        int leftFixed = 10 + 18 + 2 + 22 + 44 + 58 + 48 + 8;
        String title = "PLAYER PROGRAMMING";
        boolean showTitle = leftFixed + 130 + ui.width(title) + 14 + rightW <= width;
        int nameW = Math.max(70, Math.min(130, width - rightW - leftFixed - (showTitle ? ui.width(title) + 14 : 0)));
        int x = 10;
        if (showTitle) {
            Render2D.gradientText(ctx, textRenderer, title, x, 11, Theme.accent(), Theme.accent2());
            x += ui.width(title) + 14;
        }

        Widgets.button(ui, x, 7, 16, 16, "◀", () -> {
            programIndex--;
            scrollX = scrollY = 0;
        });
        x += 18;
        if (renaming) {
            renameBox.render(ui, x, 7, nameW, 16);
        } else {
            boolean nameHover = ui.hover(x, 7, nameW, 16);
            Render2D.roundedRectBordered(ctx, x, 7, nameW, 16, 4, Theme.FIELD, nameHover ? Theme.BORDER_HOVER : Theme.BORDER);
            String counter = (programIndex + 1) + "/" + list.size();
            ui.text(ui.ellipsize(prog.name, nameW - 16 - ui.width(counter)), x + 5, 11, 0xFFFFFFFF);
            ui.text(counter, x + nameW - 5 - ui.width(counter), 11, Theme.TEXT_MUTED);
            ui.click(x, 7, nameW, 16, (b, mx, my) -> {
                renaming = true;
                renameBox.setText(prog.name);
                renameBox.selectAll();
                ui.focused = renameBox;
            });
            if (ui.hover(x, 7, nameW, 16)) ui.tooltip("Click to rename. " + (programIndex + 1) + " / " + list.size());
        }
        x += nameW + 2;
        Widgets.button(ui, x, 7, 16, 16, "▶", () -> {
            programIndex++;
            scrollX = scrollY = 0;
        });
        x += 22;
        Widgets.button(ui, x, 7, 40, 16, "+ New", () -> {
            list.add(new Program(uniqueName("Program " + (list.size() + 1))));
            programIndex = list.size() - 1;
            scrollX = scrollY = 0;
            ProgramManager.save();
        });
        x += 44;
        Widgets.button(ui, x, 7, 54, 16, "Duplicate", () -> {
            list.add(prog.copy(uniqueName(prog.name + " copy")));
            programIndex = list.size() - 1;
            ProgramManager.save();
        });
        x += 58;
        if (ui.hover(x, 7, 48, 16)) ui.tooltip(confirmDelete ? "Click again to delete this program." : "Delete this program (asks to confirm).");
        Widgets.button(ui, x, 7, 48, 16, confirmDelete ? "Sure?" : "Delete", () -> {
            if (!confirmDelete) {
                confirmDelete = true;
                return;
            }
            confirmDelete = false;
            ProgramManager.stop(prog.name, false);
            list.remove(prog);
            programIndex--;
            ProgramManager.save();
        });

        // right side: key, run/stop, close
        int rx = width - 8;
        rx -= 44;
        Widgets.button(ui, rx, 7, 44, 16, "Close", this::close);
        boolean running = ProgramManager.isRunning(prog.name);
        rx -= 60;
        int runX = rx;
        boolean hover = ui.hover(runX, 7, 56, 16);
        Render2D.roundedRect(ctx, runX, 7, 56, 16, 4, running ? (hover ? 0xFFF05A6A : 0xFFE6505F) : (hover ? 0xFF2FD39C : 0xFF0FBD8C));
        String rt = running ? "■ Stop" : "▶ Run";
        ui.text(rt, runX + (56 - ui.width(rt)) / 2, 11, 0xFFFFFFFF);
        ui.click(runX, 7, 56, 16, (b, mx, my) -> {
            ProgramManager.save();
            ProgramManager.toggle(prog);
        });
        String key = keyLabel;
        int kw = ui.width(key) + 12;
        rx -= kw + 6;
        Render2D.roundedRectBordered(ctx, rx, 7, kw, 16, 4, Theme.FIELD, listeningKey ? Theme.accent() : Theme.BORDER);
        ui.text(key, rx + 6, 11, listeningKey ? Theme.accent() : Theme.TEXT);
        ui.click(rx, 7, kw, 16, (b, mx, my) -> listeningKey = true);
        if (ui.hover(rx, 7, kw, 16)) ui.tooltip("Key that starts the program, and stops it when pressed again. Delete clears it.");
    }

    private String uniqueName(String base) {
        String name = base;
        int i = 2;
        while (ProgramManager.find(name) != null) name = base + " " + i++;
        return name;
    }

    private void drawCategories(DrawContext ctx) {
        ctx.fill(0, TOP, CATS_W, height, 0xFF0D0D16);
        int n = BlockTypes.CATEGORIES.size();
        // full tiles (icon + name) when there is room, otherwise icon-only tiles with a tooltip
        int step = Math.min(37, (height - TOP - 8) / n);
        boolean withNames = step >= 32;
        int tile = step - 3;
        int y = TOP + 6;
        for (Category c : BlockTypes.CATEGORIES) {
            boolean sel = c == category;
            boolean hover = ui.hover(0, y, CATS_W, tile);
            float t = ui.anim("pcat:" + c.name(), sel ? 1f : hover ? 0.5f : 0f, 14f);
            if (t > 0.01f) Render2D.roundedRect(ctx, 3, y, CATS_W - 6, tile, 6, Colors.lerp(0x10FFFFFF, Colors.withAlpha(c.color(), 0x40), sel ? t : 0));
            if (sel) Render2D.roundedRect(ctx, 3, y + 4, 2, tile - 8, 1, c.color());
            int r = withNames ? 8 : Math.max(5, Math.min(8, tile / 2 - 2));
            int iy = withNames ? y + 11 : y + tile / 2;
            Render2D.circle(ctx, CATS_W / 2, iy, r, c.color());
            ui.text(c.icon(), CATS_W / 2 - ui.width(c.icon()) / 2, iy - 4, 0xFFFFFFFF);
            if (withNames) {
                String name = ui.ellipsize(c.name(), CATS_W - 4);
                ui.text(name, (CATS_W - ui.width(name)) / 2, y + 23, sel ? 0xFFFFFFFF : hover ? Theme.TEXT : Theme.TEXT_DIM);
            } else if (hover) ui.tooltip(c.name());
            ui.click(0, y, CATS_W, tile, (b, mx, my) -> {
                category = c;
                paletteScroll.reset();
            });
            y += step;
        }
    }

    private void drawPalette(DrawContext ctx) {
        int x0 = CATS_W, y0 = TOP;
        ctx.fill(x0, y0, x0 + PALETTE_W, height, 0xFF11111B);
        ctx.fill(x0 + PALETTE_W - 1, y0, x0 + PALETTE_W, height, Theme.SEPARATOR);
        ui.text(category.name().toUpperCase(), x0 + 10, y0 + 8, category.color());
        List<Def> defs = BlockTypes.of(category);
        int offset = paletteScroll.update(ui.delta());
        ui.clip(x0, y0 + 20, x0 + PALETTE_W - 1, height);
        int y = y0 + 24 - offset;
        for (Def d : defs) {
            ProgBlock sample = sampleOf(d.id());
            int h = drawBlock(sample, x0 + 8, y, false, null, 0);
            int w = Math.min(PALETTE_W - 12, headerWidth(sample));
            ui.click(x0 + 8, y, w, BH, (b, mx, my) -> {
                pending = ProgBlock.create(d.id());
                pendingList = null;
                pendingFromPalette = true;
                pressX = mx;
                pressY = my;
                grabDX = 10;
                grabDY = 10;
            });
            if (ui.hover(x0 + 8, y, w, BH) && dragging == null) ui.tooltip(d.help(), "Drag into the script →");
            y += h + 8;
        }
        int content = y - (y0 + 24 - offset);
        ui.unclip();
        paletteScroll.setBounds(content, height - y0 - 24);
    }

    private final Map<String, ProgBlock> samples = new HashMap<>();

    private ProgBlock sampleOf(String id) {
        return samples.computeIfAbsent(id, ProgBlock::create);
    }

    private void drawWorkspace(DrawContext ctx, Program prog) {
        ctx.fill(wsX, wsY, width, height, 0xFF14141F);
        // dotted grid
        int step = 20;
        int ox = Math.floorMod(-scrollX, step), oy = Math.floorMod(-scrollY, step);
        for (int x = wsX + ox; x < width; x += step) {
            for (int y = wsY + oy; y < height; y += step) ctx.fill(x, y, x + 1, y + 1, 0x22FFFFFF);
        }
        // pan by dragging the empty workspace
        ui.click(wsX, wsY, wsW, wsH, (b, mx, my) -> {
            int sx = scrollX, sy = scrollY;
            ui.drag = (dx, dy) -> {
                scrollX = sx - (int) (dx - mx);
                scrollY = Math.max(0, sy - (int) (dy - my));
            };
        });
        ui.clip(wsX, wsY, width, height);
        int x = wsX + 24 - scrollX, y = wsY + 40 - scrollY;
        // hat block
        int hatW = 170;
        int color = BlockTypes.CONTROL.color();
        Render2D.roundedRect(ctx, x, y - 26, hatW, 26, 10, color);
        ctx.fill(x, y - 10, x + hatW, y, color);
        ctx.fill(x + 10, y, x + 22, y + 2, color);
        String key = prog.key > 0 ? KeyNames.name(prog.key) : "?";
        ctx.drawText(textRenderer, "when key", x + 8, y - 15, 0xFFFFFFFF, true);
        int kx = x + 8 + ui.width("when key ") + 2;
        Render2D.roundedRect(ctx, kx, y - 18, ui.width(key) + 10, FIELD_H, 3, Colors.shade(color, -0.3f));
        ctx.drawText(textRenderer, key, kx + 5, y - 15, 0xFFFFFFFF, false);
        ui.click(kx, y - 18, ui.width(key) + 10, FIELD_H, (b, mx, my) -> listeningKey = true);
        ctx.drawText(textRenderer, "is pressed", kx + ui.width(key) + 14, y - 15, 0xFFFFFFFF, true);
        ProgramRunner r = ProgramManager.runner(prog);
        if (r != null && !r.isFinished()) Render2D.roundedOutline(ctx, x - 1, y - 27, hatW + 2, 28, 10, 0xFFFFE066);

        int bottom = drawList(prog.blocks, x, y, true, false);
        if (prog.blocks.isEmpty()) {
            ui.text("Drag blocks here from the left ←", x + 4, y + 8, Theme.TEXT_MUTED);
        }
        ui.unclip();
        // keep scroll in range
        int maxScroll = Math.max(0, bottom + scrollY - (height - 60));
        if (scrollY > maxScroll) scrollY = maxScroll;
    }

    private void drawPopups(DrawContext ctx) {
        // field text editor (+ suggestions)
        if (editBlock != null && editKind != null) {
            int w = Math.max(editW, 90);
            Render2D.shadow(ctx, editX, editY, w, FIELD_H + 2, 3, 4, 0xA0);
            fieldBox.render(ui, editX, editY, w, FIELD_H + 2);
            if (editKind == Kind.ITEM || editKind == Kind.BLOCK || editKind == Kind.ENTITY) {
                List<String> sug = BlockTypes.suggestions(editKind, fieldBox.getText());
                int sy = editY + FIELD_H + 4;
                int sw = 160;
                int n = Math.min(10, sug.size());
                if (n > 0) {
                    Render2D.roundedRect(ctx, editX, sy, sw, n * 11 + 4, 4, 0xF0101018);
                    for (int i = 0; i < n; i++) {
                        String s = sug.get(i);
                        int ry = sy + 2 + i * 11;
                        if (ui.hover(editX, ry, sw, 11)) Render2D.rect(ctx, editX + 2, ry, sw - 4, 11, Colors.withAlpha(Theme.accent(), 0x50));
                        ui.text(ui.ellipsize(s, sw - 8), editX + 4, ry + 2, Theme.TEXT);
                        ui.click(editX, ry, sw, 11, (b, mx, my) -> {
                            fieldBox.setText(s);
                            commitField();
                        });
                    }
                }
            }
        }
        // dropdown
        if (popupOptions != null) {
            int n = Math.min(12, popupOptions.size());
            int pw = 40;
            for (String o : popupOptions) pw = Math.max(pw, ui.width(label(o)) + 16);
            pw = Math.min(pw, 240);
            int px = Math.min(popupX, width - pw - 4), py = Math.min(popupY, height - n * 12 - 6);
            Render2D.shadow(ctx, px, py, pw, n * 12 + 4, 5, 6, 0xB0);
            Render2D.roundedRect(ctx, px, py, pw, n * 12 + 4, 5, 0xF5121220);
            Render2D.roundedOutline(ctx, px, py, pw, n * 12 + 4, 5, Theme.BORDER);
            popupScroll = Math.max(0, Math.min(popupScroll, popupOptions.size() - n));
            ui.scrollArea(px, py, pw, n * 12 + 4, a -> popupScroll = Math.max(0, Math.min(popupOptions.size() - n, popupScroll - (int) Math.signum(a))));
            ui.click(px, py, pw, n * 12 + 4, (b, mx, my) -> {
            });
            if (popupOptions.size() > n) {
                Widgets.scrollbar(ui, px + pw - 4, py + 2, n * 12, popupOptions.size() * 12, popupScroll * 12);
            }
            for (int i = 0; i < n; i++) {
                String o = popupOptions.get(i + popupScroll);
                int ry = py + 2 + i * 12;
                if (ui.hover(px, ry, pw, 12)) Render2D.rect(ctx, px + 2, ry, pw - 4, 12, Colors.withAlpha(Theme.accent(), 0x50));
                ui.text(ui.ellipsize(label(o), pw - 10), px + 6, ry + 2, Theme.TEXT);
                ui.click(px, ry, pw, 12, (b, mx, my) -> {
                    var action = popupAction;
                    popupOptions = null;
                    action.accept(o);
                });
            }
        }
        // context menu
        if (menuBlock != null) {
            int mw = 86, mh = 30;
            menuX = Math.min(menuX, width - mw - 4);
            menuY = Math.min(menuY, height - mh - 4);
            Render2D.shadow(ctx, menuX, menuY, mw, mh, 5, 6, 0xB0);
            Render2D.roundedRect(ctx, menuX, menuY, mw, mh, 5, 0xF5121220);
            Render2D.roundedOutline(ctx, menuX, menuY, mw, mh, 5, Theme.BORDER);
            String[] items = {"+  Duplicate", "✘  Delete"};
            for (int i = 0; i < 2; i++) {
                int ry = menuY + 2 + i * 13;
                boolean hov = ui.hover(menuX, ry, mw, 13);
                if (hov) Render2D.roundedRect(ctx, menuX + 2, ry, mw - 4, 13, 3, Colors.withAlpha(i == 1 ? 0xFFE6505F : Theme.accent(), 0x50));
                ui.text(items[i], menuX + 6, ry + 3, i == 1 && hov ? 0xFFFFFFFF : Theme.TEXT);
                int idx = i;
                ui.click(menuX, ry, mw, 13, (b, mx, my) -> {
                    int pos = menuList.indexOf(menuBlock);
                    if (idx == 0) menuList.add(pos + 1, menuBlock.copy());
                    else menuList.remove(menuBlock);
                    menuBlock = null;
                    ProgramManager.save();
                });
            }
        }
    }

    /** Condition ids are shown with their label. */
    private String label(String option) {
        BlockTypes.CondDef c = BlockTypes.cond(option);
        if (c != null && popupConditions) return c.label().replaceAll("\\{[^}]+}", "_");
        return option;
    }

    private boolean popupConditions;

    private void openPopup(List<String> options, int x, int y, java.util.function.Consumer<String> action, boolean conditions) {
        commitField();
        popupOptions = options;
        popupAction = action;
        popupX = x;
        popupY = y;
        popupScroll = 0;
        popupConditions = conditions;
    }

    private void openField(ProgBlock b, String key, Kind kind, int x, int y, int w) {
        commitField();
        switch (kind) {
            case CHOICE -> openPopup(param(b, key).options(), x, y + FIELD_H + 2, v -> b.args.put(key, v), false);
            case MODULE -> openPopup(BlockTypes.moduleNames(), x, y + FIELD_H + 2, v -> b.args.put(key, v), false);
            case PROGRAM -> {
                List<String> names = new ArrayList<>();
                for (Program p : ProgramManager.programs()) names.add(p.name);
                openPopup(names, x, y + FIELD_H + 2, v -> b.args.put(key, v), false);
            }
            default -> {
                editBlock = b;
                editKey = key;
                editKind = kind;
                editX = x;
                editY = y - 1;
                editW = w;
                fieldBox.setText(b.arg(key));
                fieldBox.selectAll(); // typing replaces the old value, arrows keep it
                ui.focused = fieldBox;
            }
        }
    }

    private void commitField() {
        if (editBlock != null && editKey != null) {
            String v = fieldBox.getText();
            if (editKind == Kind.NUMBER) v = v.replace(',', '.').replaceAll("[^0-9.\\-{}:a-zA-Z_]", "");
            editBlock.args.put(editKey, v);
            ProgramManager.save();
        }
        editBlock = null;
        editKey = null;
        editKind = null;
        if (ui.focused == fieldBox) ui.focused = null;
    }

    private void commitRename() {
        String n = renameBox.getText().trim();
        Program prog = program();
        if (!n.isEmpty() && (ProgramManager.find(n) == null || ProgramManager.find(n) == prog)) {
            ProgramManager.stop(prog.name, false);
            prog.name = n;
            ProgramManager.save();
        }
        renaming = false;
        if (ui.focused == renameBox) ui.focused = null;
    }

    // ------------------------------------------------------------------ drag & drop

    private Slot dropSlot(double mx, double my) {
        Slot best = null;
        double bestScore = 18;
        for (Slot s : slots) {
            if (mx < s.x() - 12) continue;
            double dy = Math.abs(my - grabDY + 2 - s.y());
            double score = dy + Math.abs(mx - grabDX - s.x()) * 0.08;
            if (dy < 16 && score < bestScore) {
                bestScore = score;
                best = s;
            }
        }
        if (best == null && mx > wsX) {
            // nothing close: append to the end of the script
            for (Slot s : slots) if (s.list() == program().blocks && s.index() == program().blocks.size()) best = s;
        }
        return best;
    }

    private void beginDrag() {
        dragging = pending;
        dragFrom = pendingList;
        dragFromIndex = pendingIndex;
        if (dragFrom != null) dragFrom.remove(dragging);
        pending = null;
    }

    private void endDrag(double mx, double my) {
        ProgBlock b = dragging;
        dragging = null;
        if (mx < wsX) {
            ProgramManager.save(); // dropped on the palette: deleted
            return;
        }
        Slot s = dropSlot(mx, my);
        if (s != null) {
            s.list().add(Math.min(s.index(), s.list().size()), b);
        } else if (dragFrom != null) {
            dragFrom.add(Math.min(dragFromIndex, dragFrom.size()), b);
        }
        ProgramManager.save();
    }

    // ------------------------------------------------------------------ input

    @Override
    protected boolean onMouseClick(double mouseX, double mouseY, int button) {
        if (listeningKey) {
            listeningKey = false;
            return true;
        }
        confirmDelete = confirmDelete && mouseY < TOP;
        boolean hadPopup = popupOptions != null || menuBlock != null;
        boolean wasEditing = editBlock != null || renaming;
        ProgBlock oldMenu = menuBlock;
        var oldOptions = popupOptions;
        ui.dispatchClick(mouseX, mouseY, button);
        // clicking outside a popup closes it
        if (hadPopup && popupOptions == oldOptions && menuBlock == oldMenu) {
            popupOptions = null;
            menuBlock = null;
        }
        if (wasEditing && ui.focused == null) {
            if (editBlock != null) commitField();
            if (renaming) commitRename();
        }
        return true;
    }

    @Override
    protected void onMouseRelease(double mouseX, double mouseY, int button) {
        ui.drag = null;
        if (dragging != null) endDrag(mouseX, mouseY);
        pending = null;
    }

    @Override
    protected void onMouseDrag(double mouseX, double mouseY, int button) {
        if (pending != null && dragging == null && Math.hypot(mouseX - pressX, mouseY - pressY) > 3) beginDrag();
        if (dragging != null) return;
        if (ui.drag != null) ui.drag.onDrag(mouseX, mouseY);
    }

    @Override
    protected boolean onScroll(double mouseX, double mouseY, double amount) {
        if (ui.dispatchScroll(mouseX, mouseY, amount)) return true;
        if (mouseX < wsX && mouseX > CATS_W) paletteScroll.scroll(amount);
        else if (mouseX >= wsX) scrollY = Math.max(0, scrollY - (int) (amount * 24));
        return true;
    }

    @Override
    protected boolean onKey(int key, int scancode, int modifiers) {
        if (listeningKey) {
            listeningKey = false;
            if (key == GLFW.GLFW_KEY_ESCAPE) return true;
            program().key = key == GLFW.GLFW_KEY_DELETE || key == GLFW.GLFW_KEY_BACKSPACE ? 0 : key;
            ProgramManager.save();
            return true;
        }
        if (ui.focused != null) {
            if (key == GLFW.GLFW_KEY_ESCAPE) {
                if (ui.focused == fieldBox) commitField();
                else commitRename();
                return true;
            }
            return ui.focused.keyPressed(key, modifiers);
        }
        if (key == GLFW.GLFW_KEY_ESCAPE && (popupOptions != null || menuBlock != null)) {
            popupOptions = null;
            menuBlock = null;
            return true;
        }
        return false;
    }

    @Override
    protected boolean onChar(int codepoint) {
        return ui.focused != null && ui.focused.charTyped(codepoint);
    }

    @Override
    public void close() {
        commitField();
        ProgramManager.save();
        client.setScreen(parent);
    }
}
