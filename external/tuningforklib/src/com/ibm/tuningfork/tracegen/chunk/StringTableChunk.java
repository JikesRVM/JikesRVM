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
public class StringTableChunk extends Chunk {

    public static final int STRING_TABLE_ID = 6;
    public static final int STRING_COUNT_OFFSET = Chunk.DATA_OFFSET;
    public static final int STRING_DATA_OFFSET = STRING_COUNT_OFFSET + 4;
    private int numberOfStrings = 0;

    public StringTableChunk() {
	super(STRING_TABLE_ID);
	seek(STRING_DATA_OFFSET);
    }

    public boolean add(int index, String val) {
	int guess = ENCODING_SPACE_INT + JikesRVMSupport.getStringLength(val);
	if (!hasRoom(guess)) {
	    return false;
	}
	int savedPosition = getPosition();
	addInt(index);
	if (!addString(val)) {
	    seek(savedPosition);
	    return false;
	}
	numberOfStrings++;
	return true;
    }

    @Override
    public void close() {
	putIntAt(STRING_COUNT_OFFSET, numberOfStrings);
	numberOfStrings = 0;
	super.close();
    }

    public boolean hasData() {
	return numberOfStrings > 0;
    }

    public void reset() {
	resetImpl();
	numberOfStrings = 0;
        seek(STRING_DATA_OFFSET);
    }
}
