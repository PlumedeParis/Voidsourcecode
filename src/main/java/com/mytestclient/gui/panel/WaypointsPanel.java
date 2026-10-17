package com.mytestclient.gui.panel;

import com.mytestclient.MyTestClient;
import com.mytestclient.gui.ScrollState;
import com.mytestclient.gui.Theme;
import com.mytestclient.gui.Ui;
import com.mytestclient.gui.Widgets;
import com.mytestclient.gui.widget.TextBox;
import com.mytestclient.module.world.QuickWaypointModule;
import com.mytestclient.render.Render2D;
import com.mytestclient.waypoint.Waypoint;
import com.mytestclient.waypoint.WaypointManager;
import net.minecraft.client.MinecraftClient;

import java.util.ArrayList;
import java.util.List;

/** Waypoints page: add at your position, list, show/hide, recolour and delete. */
public class WaypointsPanel {
    private final TextBox name = new TextBox("Waypoint name", 32);
    private final ScrollState scroll = new ScrollState();
    private boolean currentWorldOnly = true;
    private int listX, listY, listW, listH;

    public WaypointsPanel() {
        name.onSubmit(s -> add());
    }

    private void add() {
        if (MinecraftClient.getInstance().player == null) return;
        QuickWaypointModule.addHere(name.getText());
        name.setText("");
    }

    public void render(Ui ui, int x, int y, int w, int h) {
        MinecraftClient mc = MinecraftClient.getInstance();
        WaypointManager manager = MyTestClient.get().waypoints();
        boolean inWorld = mc.player != null;

        ui.text("Waypoints", x, y, Theme.accent());
        String hint = inWorld ? WaypointManager.currentWorldKey() : "Join a world to add waypoints";
        ui.text(ui.ellipsize(hint, w - 70), x + w - Math.min(ui.width(hint), w - 70), y, Theme.TEXT_MUTED);
        int cy = y + 14;

        int buttonsW = 150;
        name.render(ui, x, cy, w - buttonsW - 4, 16);
        if (inWorld) Widgets.button(ui, x + w - buttonsW, cy, 70, 16, "Add here", true, this::add);
        Widgets.button(ui, x + w - 76, cy, 76, 16, "Clear deaths", () -> manager.removeIf(wp -> wp.death));
        cy += 22;

        Widgets.toggle(ui, x, cy + 1, currentWorldOnly, this);
        ui.text("This world only", x + Widgets.SWITCH_W + 6, cy + 2, Theme.TEXT_DIM);
        ui.click(x, cy, Widgets.SWITCH_W + 90, 13, (b, mx, my) -> currentWorldOnly = !currentWorldOnly);
        cy += 18;

        List<Waypoint> list = currentWorldOnly ? manager.forCurrentWorld() : new ArrayList<>(manager.all());
        listX = x;
        listY = cy;
        listW = w;
        listH = y + h - cy;
        int rowH = 24;
        scroll.setBounds(list.size() * rowH, listH);
        int offset = scroll.update(ui.delta());

        ui.clip(listX, listY, listX + listW, listY + listH);
        if (list.isEmpty()) {
            ui.text("No waypoints yet. Use 'Add here' or bind Quick Waypoint.", x + 4, cy + 6, Theme.TEXT_MUTED);
        }
        String dim = WaypointManager.currentDimension();
        int ry = cy - offset;
        for (Waypoint wp : list) {
            if (ry + rowH >= listY && ry <= listY + listH) renderRow(ui, mc, manager, wp, x, ry, w - 6, rowH - 3, dim);
            ry += rowH;
        }
        ui.unclip();
        Widgets.scrollbar(ui, x + w - 3, listY, listH, list.size() * rowH, offset);
    }

    private void renderRow(Ui ui, MinecraftClient mc, WaypointManager manager, Waypoint wp, int x, int y, int w, int h, String dim) {
        Render2D.roundedRect(ui.ctx, x, y, w, h, 4, ui.hover(x, y, w, h) ? Theme.CARD_HOVER : Theme.CARD);

        // colour dot: click to cycle the palette
        Render2D.circle(ui.ctx, x + 10, y + h / 2, 4, wp.color);
        ui.click(x + 4, y + 2, 12, h - 4, (b, mx, my) -> {
            wp.color = nextColor(wp.color);
            manager.save();
        });
        if (ui.hover(x + 4, y + 2, 12, h - 4)) ui.tooltip("Click to change colour");

        String coords = wp.x + " " + wp.y + " " + wp.z;
        String dimShort = wp.dimension.substring(wp.dimension.indexOf(':') + 1).replace("the_", "");
        String nameText = (wp.death ? "☠ " : "") + wp.name;
        ui.text(ui.ellipsize(nameText, w - 150), x + 20, y + 3, wp.visible ? Theme.TEXT : Theme.TEXT_MUTED);
        ui.text(ui.ellipsize(coords + "  " + dimShort, w - 150), x + 20, y + 12, Theme.TEXT_MUTED);

        if (mc.player != null && wp.dimension.equals(dim) && wp.world.equals(WaypointManager.currentWorldKey())) {
            String dist = String.format("%.0fm", Math.sqrt(mc.player.squaredDistanceTo(wp.x + 0.5, wp.y, wp.z + 0.5)));
            ui.text(dist, x + w - 92 - ui.width(dist), y + (h - 8) / 2, Theme.TEXT_DIM);
        }

        Widgets.button(ui, x + w - 84, y + 3, 44, h - 6, wp.visible ? "Hide" : "Show", () -> {
            wp.visible = !wp.visible;
            manager.save();
        });
        Widgets.button(ui, x + w - 36, y + 3, 32, h - 6, "✕", () -> manager.remove(wp));
    }

    private static int nextColor(int color) {
        int[] palette = WaypointManager.PALETTE;
        for (int i = 0; i < palette.length; i++) if (palette[i] == color) return palette[(i + 1) % palette.length];
        return palette[0];
    }

    public boolean scroll(double mouseX, double mouseY, double amount) {
        if (mouseX >= listX && mouseX < listX + listW && mouseY >= listY && mouseY < listY + listH) {
            scroll.scroll(amount);
            return true;
        }
        return false;
    }
}
