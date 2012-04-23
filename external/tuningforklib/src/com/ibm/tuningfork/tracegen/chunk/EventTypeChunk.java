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

import org.vmmagic.pragma.Interruptible;
import org.vmmagic.pragma.Uninterruptible;

import com.ibm.tuningfork.tracegen.types.EventAttribute;
import com.ibm.tuningfork.tracegen.types.EventType;

@Uninterruptible
public class EventTypeChunk extends Chunk {

    public static final int EVENT_TYPE_ID = 4;
    public static final int EVENT_TYPE_OFFSET = Chunk.DATA_OFFSET;
    public static final int EVENT_DATA_OFFSET = EVENT_TYPE_OFFSET + 4;
    private int numberOfEventTypes = 0;

    public EventTypeChunk() {
	super(EVENT_TYPE_ID);
	seek(EVENT_DATA_OFFSET);
    }

    public boolean hasData() {
	return numberOfEventTypes > 0;
    }

    @Interruptible
    public boolean add(EventType et) {
      boolean success = false;
      int savedPosition = getPosition();
      try {
        if (!addInt(et.getIndex())) return false;
        if (!addStringInternal(getChars(et.getName()))) return false;
        if (!addStringInternal(getChars(et.getDescription()))) return false;
        if (!addInt(et.getNumberOfInts())) return false;
        if (!addInt(et.getNumberOfLongs())) return false;
        if (!addInt(et.getNumberOfDoubles())) return false;
        if (!addInt(et.getNumberOfStrings())) return false;
        for (int i = 0; i < et.getNumberOfAttributes(); i++) {
          EventAttribute ea = et.getAttribute(i);
          if (!addStringInternal(getChars(ea.getName()))) return false;
          if (!addStringInternal(getChars(ea.getDescription()))) return false;
        }
        success = true;
        numberOfEventTypes++;
        return true;
      } finally {
        if (!success) {
          seek(savedPosition);
        }
      }
    }

    @Override
    public void close() {
	putIntAt(EVENT_TYPE_OFFSET, numberOfEventTypes);
	numberOfEventTypes = 0;
	super.close();
    }

    public void reset() {
	resetImpl();
	numberOfEventTypes = 0;
	seek(EVENT_DATA_OFFSET);
    }

}
