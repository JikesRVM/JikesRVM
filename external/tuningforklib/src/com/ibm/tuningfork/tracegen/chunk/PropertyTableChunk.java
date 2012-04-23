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
public class PropertyTableChunk extends Chunk {

    public static final int PROPERTY_TABLE_ID = 1;
    public static final int PROPERTY_COUNT_OFFSET = Chunk.DATA_OFFSET;
    public static final int PROPERTY_DATA_OFFSET = PROPERTY_COUNT_OFFSET + 4;
    private int numberOfProperties = 0;

    public PropertyTableChunk() {
	super(PROPERTY_TABLE_ID);
	seek(PROPERTY_DATA_OFFSET);
    }

    public boolean add(String prop, String val) {
	int guess = JikesRVMSupport.getStringLength(prop) + JikesRVMSupport.getStringLength(val);
	if (!hasRoom(guess)) {
	    return false;
	}
	int savedPosition = getPosition();
	if (!addString(prop)) {
	    seek(savedPosition);
	    return false;
	}
	if (!addString(val)) {
	    seek(savedPosition);
	    return false;
	}
	numberOfProperties++;
	return true;
    }

    @Override
    public void close() {
	putIntAt(PROPERTY_COUNT_OFFSET, numberOfProperties);
	numberOfProperties = 0;
	super.close();
    }

    public boolean hasData() {
	return numberOfProperties > 0;
    }

    public void reset() {
 	resetImpl();
        numberOfProperties = 0;
        seek(PROPERTY_DATA_OFFSET);
    }
}
