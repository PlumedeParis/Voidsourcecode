package com.mytestclient.gui.panel;

import com.mytestclient.MyTestClient;
import com.mytestclient.config.ProfileManager;
import com.mytestclient.gui.ScrollState;
import com.mytestclient.gui.Theme;
import com.mytestclient.gui.Ui;
import com.mytestclient.gui.Widgets;
import com.mytestclient.gui.widget.TextBox;
import com.mytestclient.module.Module;
import com.mytestclient.module.Modules;
import com.mytestclient.module.Risk;
import com.mytestclient.render.Render2D;
import com.mytestclient.util.Notifications;

import java.util.List;

/** Profiles page: save the current setup as a preset, load, overwrite or delete presets. */
public class ProfilesPanel {
    private final TextBox name = new TextBox("Profile name", 32);
    private final ScrollState scroll = new ScrollState();
    private List<String> cached;
    private String pendingDelete;
    private long pendingDeleteTime;
    private int listX, listY, listW, listH;

    public ProfilesPanel() {
        name.onSubmit(s -> save());
    }

    private ProfileManager profiles() {
        return MyTestClient.get().profiles();
    }

    private void refresh() {
        cached = profiles().list();
    }

    private void save() {
        String clean = ProfileManager.sanitize(name.getText());
        if (clean.isEmpty()) {
            Notifications.warn("Enter a profile name first");
            return;
        }
        if (profiles().save(clean)) Notifications.info("Saved profile '" + clean + "'");
        name.setText("");
        refresh();
    }

    public void render(Ui ui, int x, int y, int w, int h) {
        if (cached == null) refresh();
        String active = profiles().getActive();

        ui.text("Profiles", x, y, Theme.accent());
        String sub = active.isEmpty() ? "No profile loaded" : "Active: " + active;
        ui.text(sub, x + w - ui.width(sub), y, Theme.TEXT_MUTED);
        int cy = y + 14;

        name.render(ui, x, cy, w - 74, 16);
        Widgets.button(ui, x + w - 70, cy, 70, 16, "Save", true, this::save);
        cy += 22;

        Widgets.button(ui, x, cy, (w - 4) / 2, 16, "Disable all ⚠ modules", () -> {
            int n = 0;
            for (Module m : Modules.all()) {
                if (m.isEnabled() && m.getRisk() != Risk.NONE) {
                    m.setEnabledQuietly(false);
                    n++;
                }
            }
            Notifications.info("Disabled " + n + " module(s) with warnings");
        });
        Widgets.button(ui, x + (w - 4) / 2 + 4, cy, (w - 4) / 2, 16, "Reload list", this::refresh);
        cy += 22;

        listX = x;
        listY = cy;
        listW = w;
        listH = y + h - cy;
        int rowH = 22;
        scroll.setBounds(cached.size() * rowH, listH);
        int offset = scroll.update(ui.delta());

        ui.clip(listX, listY, listX + listW, listY + listH);
        if (cached.isEmpty()) {
            ui.text("No profiles yet. Type a name and press Save.", x + 4, cy + 6, Theme.TEXT_MUTED);
        }
        int ry = cy - offset;
        for (String profile : cached) {
            if (ry + rowH >= listY && ry <= listY + listH) renderRow(ui, profile, profile.equals(active), x, ry, w - 6, rowH - 3);
            ry += rowH;
        }
        ui.unclip();
        Widgets.scrollbar(ui, x + w - 3, listY, listH, cached.size() * rowH, offset);
    }

    private void renderRow(Ui ui, String profile, boolean active, int x, int y, int w, int h) {
        Render2D.roundedRect(ui.ctx, x, y, w, h, 4, ui.hover(x, y, w, h) ? Theme.CARD_HOVER : Theme.CARD);
        if (active) Render2D.rect(ui.ctx, x, y + 3, 2, h - 6, Theme.accent());
        ui.text(ui.ellipsize(profile, w - 190), x + 8, y + (h - 8) / 2, active ? Theme.accent() : Theme.TEXT);

        int bx = x + w - 4;
        boolean confirming = profile.equals(pendingDelete) && System.currentTimeMillis() - pendingDeleteTime < 3000;
        bx -= 56;
        Widgets.button(ui, bx, y + 3, 54, h - 6, confirming ? "Confirm?" : "Delete", () -> {
            if (confirming) {
                profiles().delete(profile);
                pendingDelete = null;
                Notifications.info("Deleted profile '" + profile + "'");
                refresh();
            } else {
                pendingDelete = profile;
                pendingDeleteTime = System.currentTimeMillis();
            }
        });
        bx -= 62;
        Widgets.button(ui, bx, y + 3, 60, h - 6, "Overwrite", () -> {
            profiles().save(profile);
            Notifications.info("Overwrote profile '" + profile + "'");
        });
        bx -= 50;
        Widgets.button(ui, bx, y + 3, 48, h - 6, "Load", true, () -> {
            if (profiles().load(profile)) Notifications.info("Loaded profile '" + profile + "'");
            else Notifications.warn("Could not load '" + profile + "'");
        });
    }

    public boolean scroll(double mouseX, double mouseY, double amount) {
        if (mouseX >= listX && mouseX < listX + listW && mouseY >= listY && mouseY < listY + listH) {
            scroll.scroll(amount);
            return true;
        }
        return false;
    }
}
