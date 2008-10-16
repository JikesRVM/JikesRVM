/*
 * This file is part of the Tuning Fork Visualization Platform
 *  (http://sourceforge.net/projects/tuningforkvp)
 *
 * Copyright (c) 2005 - 2008 IBM Corporation.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     IBM Corporation - initial API and implementation
 */

package com.ibm.tuningfork.tracegen.chunk;

import org.vmmagic.pragma.Uninterruptible;

@Uninterruptible
public class FeedHeaderChunk extends RawChunk {

    private static final int MAGIC_WORD_1 = 0xcafefeed;
    private static final int MAGIC_WORD_2 = 0x2bad4dfb;
    private static final int MAJOR_VERSION = 1;
    private static final int MINOR_VERSION = 4;
    private static final int DEFUNCT_FIELD = 0;

    public FeedHeaderChunk() {
	super(5 * ENCODING_SPACE_INT);
	addInt(MAGIC_WORD_1);
	addInt(MAGIC_WORD_2);
	addInt(MAJOR_VERSION);
	addInt(MINOR_VERSION);
	addInt(DEFUNCT_FIELD);
	close();
    }

}
