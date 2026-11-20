/* SPDX-License-Identifier: GPL-3.0-or-later
 * Copyright (C) 2026 Embrith contributors
 *
 * BookSource: Interface for a book as a list of chunks (chapters); the reader shows one chunk at a time. */
package com.embrith.library;

import java.io.IOException;

/** A book as a list of chunks (a text file is cut into chapters, an epub has one chunk per chapter); the reader shows one chunk at a time. */
interface BookSource {
    int count();

    /** The text of chunk i (may be styled text). */
    CharSequence chunk(int i) throws IOException;

    /** The size of chunk i, only used to turn "chunk and place in it" into a percentage. */
    long weight(int i);

    /** The name of chunk i for the table of contents; null or empty when it has none. */
    String chapterTitle(int i);

    /** The title of the book; null when unknown (the reader then uses the file name). */
    String bookTitle();

    /** The author; null when unknown. */
    String author();

    void close();
}
