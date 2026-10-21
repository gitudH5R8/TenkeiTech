/*
 * SPDX-FileCopyrightText: 2026 TenkeiTech
 * SPDX-License-Identifier: GPL-3.0-only
 */
package com.tenkeitech.patternupload;

/** Screen-relative layout in scaled GUI pixels, independent of the hidden AE terminal. */
final class PatternUploadLayout {

    final Box search, previous, next, reload, categoryInput, mapping, cancel, add, delete, showButton, autoUpload;
    final Box[] rows = new Box[6];
    final int statusY, pageY;

    PatternUploadLayout(int width, int height) {
        int listWidth = Math.min(240, width - 16);
        int top = Math.max(8, Math.min(height / 2 - 95, height - 260));
        int navY = height - 76;
        int rowTop = top + 25;
        statusY = navY - 11;
        // The final row needs no trailing gap: its height is at most rowStep - 3.
        int rowStep = Math.min(25, (statusY - rowTop + 3) / rows.length);
        search = new Box((width - listWidth) / 2, top, listWidth, 18);
        for (int row = 0; row < rows.length; row++)
            rows[row] = new Box(search.x, rowTop + row * rowStep, listWidth, Math.min(20, rowStep - 3));
        previous = new Box(width / 2 - 60, navY, 20, 20);
        next = new Box(width / 2 + 40, navY, 20, 20);
        pageY = navY + 6;
        int footerWidth = Math.min(460, width - 10);
        int buttonWidth = Math.min(80, (footerWidth - 80) / 4);
        int inputWidth = footerWidth - buttonWidth * 4 - 20;
        int bottom = height - 22;
        reload = new Box((width - footerWidth) / 2, bottom, buttonWidth, 20);
        mapping = new Box(reload.right() + 5, bottom, inputWidth, 20);
        cancel = new Box(mapping.right() + 5, bottom, buttonWidth, 20);
        add = new Box(cancel.right() + 5, bottom, buttonWidth, 20);
        delete = new Box(add.right() + 5, bottom, buttonWidth, 20);
        categoryInput = new Box(reload.x, bottom - 25, add.x - reload.x - 5, 20);
        showButton = new Box(add.x, bottom - 25, buttonWidth, 20);
        autoUpload = new Box(delete.x, bottom - 25, buttonWidth, 20);
    }

    static final class Box {

        final int x, y, w, h;

        Box(int x, int y, int w, int h) {
            this.x = x;
            this.y = y;
            this.w = w;
            this.h = h;
        }

        int right() {
            return x + w;
        }

        boolean contains(int mouseX, int mouseY) {
            return mouseX >= x && mouseX < right() && mouseY >= y && mouseY < y + h;
        }
    }
}
