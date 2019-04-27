package com.liuzhenlin.floatingmenu;

import android.view.View;

import androidx.annotation.NonNull;

public class MenuItem {

    private String text;
    private int iconResId = View.NO_ID;

    public MenuItem() {
    }

    public MenuItem(String text) {
        this.text = text;
    }

    public MenuItem(String text, int iconResId) {
        this.text = text;
        this.iconResId = iconResId;
    }

    public String getText() {
        return text;
    }

    public void setText(String item) {
        this.text = item;
    }

    public int getIconResId() {
        return iconResId;
    }

    public void setIconResId(int itemResId) {
        this.iconResId = itemResId;
    }

    @NonNull
    @Override
    public String toString() {
        return "MenuItem{" +
                "text='" + text + '\'' +
                '}';
    }
}
