/*
 * SPDX-FileCopyrightText: 2026 TenkeiTech
 * SPDX-License-Identifier: GPL-3.0-only
 */
package com.tenkeitech.patternupload;

/** Client-only bridge implemented by the pattern-terminal GUI mixin. */
public interface PatternUploadScreenBridge {

    boolean tenkeitech$isTerminalOverlayOpen();

    void tenkeitech$tickPatternUpload();

    void tenkeitech$setPatternUploadCategory(String category, String label);

    void tenkeitech$handleTerminalOverlayClick(int mouseX, int mouseY, int mouseButton);

    void tenkeitech$tickNeeSettings();

    void tenkeitech$closeNeeSettings();

    void tenkeitech$acceptNeeSettingsReply(com.tenkeitech.universalwand.UniversalWandNetwork.NeeSettingsReply reply);

    void tenkeitech$acceptPatternUploadPage(PatternUploadNetwork.TargetsPageMessage message);

    void tenkeitech$acceptPatternUploadResult(PatternUploadNetwork.UploadResultMessage message);
}
