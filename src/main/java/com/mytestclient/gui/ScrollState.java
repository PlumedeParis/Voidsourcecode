package com.mytestclient.gui;

/** Smooth scrolling for a list area. */
public final class ScrollState {
    private double target, current;
    private int max;

    public void setBounds(int contentHeight, int viewHeight) {
        max = Math.max(0, contentHeight - viewHeight);
        target = Math.max(0, Math.min(max, target));
    }

    public void scroll(double amount) {
        target = Math.max(0, Math.min(max, target - amount * 24));
    }

    public int update(float delta) {
        current += (target - current) * Math.min(1f, delta * 16f);
        if (Math.abs(target - current) < 0.5) current = target;
        return (int) Math.round(current);
    }

    public void reset() {
        target = current = 0;
    }
}
