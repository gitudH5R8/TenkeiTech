/*
 * SPDX-FileCopyrightText: 2026 TenkeiTech
 * SPDX-License-Identifier: GPL-3.0-only
 */
package com.tenkeitech.autoextract;

/** Access to this open interface's display state and server-validated cancellation action. */
public interface AutoExtractionContainer {

    AutoExtractionView tenkeitech$extractionView();

    void tenkeitech$cancelExtraction();
}
