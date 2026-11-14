package com.voidclient.gui.component;

import com.voidclient.gui.Ui;

/** A GUI row that edits one setting. Renders at (x, y) with the given width and returns its height. */
public interface SettingComponent {
    int render(Ui ui, int x, int y, int width);
}
