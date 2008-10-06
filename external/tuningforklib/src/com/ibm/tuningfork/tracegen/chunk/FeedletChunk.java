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

@Uninterruptible
public class FeedletChunk extends Chunk {

    public static final int FEEDLET_TYPE_ID = 2;
    public static final int FEEDLET_COUNT_OFFSET = Chunk.DATA_OFFSET;
    public static final int FEEDLET_DATA_OFFSET = FEEDLET_COUNT_OFFSET + 4;
    public static final int FEEDLET_ADD_OPERATION = 1;
    public static final int FEEDLET_REMOVE_OPERATION = 2;
    public static final int FEEDLET_DESCRIBE_OPERATION = 3;
    public static final String NAME_PROPERTY = "name";
    public static final String DECSRIPTION_PROPERTY = "description";

    private int feedletOperations = 0;

    public FeedletChunk() {
	super(FEEDLET_TYPE_ID);
	seek(FEEDLET_DATA_OFFSET);
    }

    public boolean hasData() {
	return feedletOperations > 0;
    }

    @Interruptible
    public boolean add(int feedletIndex, String name, String description) {
      int savedPosition = getPosition();
      int savedOperationCount = feedletOperations;
      boolean success = false;
      try {
	if (!addInt(FEEDLET_ADD_OPERATION)) return false;
	if (!addInt(feedletIndex)) return false;
	feedletOperations++;
	if (!addProperty(feedletIndex, NAME_PROPERTY, name)) return false;
	if (!addProperty(feedletIndex, DECSRIPTION_PROPERTY, description)) return false;
	success = true;
	return true;
      } finally {
        if (!success) {
          seek(savedPosition);
          feedletOperations = savedOperationCount;
        }
      }
    }

    public boolean remove(int feedletIndex) {
      if (!hasRoom(ENCODING_SPACE_INT*2)) return false;
      addIntUnchecked(FEEDLET_REMOVE_OPERATION);
      addIntUnchecked(feedletIndex);
      feedletOperations++;
      return true;
    }

    @Interruptible
    public boolean addProperty(int feedletIndex, String key, String val) {
      int savedPosition = getPosition();
      int savedOperationCount = feedletOperations;
      boolean success = false;
      try {
	if (!addInt(FEEDLET_DESCRIBE_OPERATION)) return false;
	if (!addInt(feedletIndex)) return false;
	if (!addStringInternal(getChars(key))) return false;
	if (!addStringInternal(getChars(val))) return false;
	feedletOperations++;
	success = true;
	return true;
      } finally {
        if (!success) {
          seek(savedPosition);
          feedletOperations = savedOperationCount;
        }
      }
    }

    public void close() {
	putIntAt(FEEDLET_COUNT_OFFSET, feedletOperations);
	feedletOperations = 0;
	super.close();
    }

    public void reset() {
	resetImpl();
	feedletOperations = 0;
        seek(FEEDLET_DATA_OFFSET);
    }
}
