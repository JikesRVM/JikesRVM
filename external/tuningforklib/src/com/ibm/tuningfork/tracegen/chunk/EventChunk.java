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
import org.vmmagic.pragma.Untraced;

import com.ibm.tuningfork.tracegen.types.EventType;

@Uninterruptible
public final class EventChunk extends Chunk {

  public static final int EVENT_TYPE_ID = 5;
  public static final int FEEDLET_ID_OFFSET = Chunk.DATA_OFFSET;
  public static final int SEQUENCE_NUMBER_OFFSET = Chunk.DATA_OFFSET + 4;
  public static final int EVENT_DATA_OFFSET = Chunk.DATA_OFFSET + 8;
  protected final static int DEFAULT_EVENT_CHUNK_SIZE = 16 * 1024;

  /* Only for use by EventChunkQueue */
  @Untraced
  public EventChunk next = null;

  public EventChunk() {
    super(EVENT_TYPE_ID, DEFAULT_EVENT_CHUNK_SIZE);
    seek(EVENT_DATA_OFFSET);
  }

  public void reset(int feedletIndex, int sequenceNumber) {
    super.resetImpl();
    seek(EVENT_DATA_OFFSET);
    putIntAt(FEEDLET_ID_OFFSET, feedletIndex);
    putIntAt(SEQUENCE_NUMBER_OFFSET, sequenceNumber);
  }

  public boolean addEvent(long timeStamp, EventType et) {
    int required = ENCODING_SPACE_LONG + ENCODING_SPACE_INT;
    if (!hasRoom(required)) {
      return false;
    }
    addLongUnchecked(timeStamp);
    addIntUnchecked(et.getIndex());
    return true;
  }

  public boolean addEvent(long timeStamp, EventType et, int v) {
    int required = ENCODING_SPACE_LONG + ENCODING_SPACE_INT
    + ENCODING_SPACE_INT;
    if (!hasRoom(required)) {
      return false;
    }
    addLongUnchecked(timeStamp);
    addIntUnchecked(et.getIndex());
    addIntUnchecked(v);
    return true;
  }

  public boolean addEvent(long timeStamp, EventType et, int v1, int v2) {
    int required = ENCODING_SPACE_LONG + ENCODING_SPACE_INT + 2*ENCODING_SPACE_INT;
    if (!hasRoom(required)) {
      return false;
    }
    addLongUnchecked(timeStamp);
    addIntUnchecked(et.getIndex());
    addIntUnchecked(v1);
    addIntUnchecked(v2);
    return true;
  }

  public boolean addEvent(long timeStamp, EventType et, int v1, int v2, int v3) {
    int required = ENCODING_SPACE_LONG + ENCODING_SPACE_INT + 3*ENCODING_SPACE_INT;
    if (!hasRoom(required)) {
      return false;
    }
    addLongUnchecked(timeStamp);
    addIntUnchecked(et.getIndex());
    addIntUnchecked(v1);
    addIntUnchecked(v2);
    addIntUnchecked(v3);
    return true;
  }

  public boolean addEvent(long timeStamp, EventType et, int v1, int v2, int v3, int v4) {
    int required = ENCODING_SPACE_LONG + ENCODING_SPACE_INT + 4*ENCODING_SPACE_INT;
    if (!hasRoom(required)) {
      return false;
    }
    addLongUnchecked(timeStamp);
    addIntUnchecked(et.getIndex());
    addIntUnchecked(v1);
    addIntUnchecked(v2);
    addIntUnchecked(v3);
    addIntUnchecked(v4);
    return true;
  }

  public boolean addEvent(long timeStamp, EventType et, long v) {
    int required = ENCODING_SPACE_LONG + ENCODING_SPACE_INT + ENCODING_SPACE_LONG;
    if (!hasRoom(required)) {
      return false;
    }
    addLongUnchecked(timeStamp);
    addIntUnchecked(et.getIndex());
    addLongUnchecked(v);
    return true;
  }

  public boolean addEvent(long timeStamp, EventType et, double v) {
    int required = ENCODING_SPACE_LONG + ENCODING_SPACE_INT + ENCODING_SPACE_DOUBLE;
    if (!hasRoom(required)) {
      return false;
    }
    addLongUnchecked(timeStamp);
    addIntUnchecked(et.getIndex());
    addDoubleUnchecked(v);
    return true;
  }

  public boolean addEvent(long timeStamp, EventType et, String v) {
    int guess = ENCODING_SPACE_LONG + ENCODING_SPACE_INT + JikesRVMSupport.getStringLength(v);
    if (!hasRoom(guess)) {
      return false;
    }
    int savedCursor = getPosition();
    addLongUnchecked(timeStamp);
    addIntUnchecked(et.getIndex());
    if (!addString(v)) {
      seek(savedCursor);
      return false;
    }
    return true;
  }

  public boolean addEvent(long timeStamp, EventType et, int iv, double dv) {
    int required = ENCODING_SPACE_LONG + 2 * ENCODING_SPACE_INT + ENCODING_SPACE_DOUBLE;
    if (!hasRoom(required)) {
      return false;
    }
    addLongUnchecked(timeStamp);
    addIntUnchecked(et.getIndex());
    addIntUnchecked(iv);
    addDoubleUnchecked(dv);
    return true;
  }

  public boolean addEvent(long timeStamp, EventType et, int iv1, int iv2, double dv) {
    int required = ENCODING_SPACE_LONG + 3 * ENCODING_SPACE_INT + ENCODING_SPACE_DOUBLE;
    if (!hasRoom(required)) {
      return false;
    }
    addLongUnchecked(timeStamp);
    addIntUnchecked(et.getIndex());
    addIntUnchecked(iv1);
    addIntUnchecked(iv2);
    addDoubleUnchecked(dv);
    return true;
  }

  public boolean addEvent(long timeStamp, EventType et, double dv, String sv) {
    int guess = ENCODING_SPACE_LONG + ENCODING_SPACE_DOUBLE + ENCODING_SPACE_INT + JikesRVMSupport.getStringLength(sv);
    if (!hasRoom(guess)) {
      return false;
    }
    int savedCursor = getPosition();
    addLongUnchecked(timeStamp);
    addIntUnchecked(et.getIndex());
    addDoubleUnchecked(dv);
    if (!addString(sv)) {
      seek(savedCursor);
      return false;
    }
    return true;
  }

  public boolean addEvent(long timeStamp, EventType et, int[] idata,
                          long[] ldata, double[] ddata, String[] sdata) {
    int ilen = (idata == null) ? 0 : idata.length;
    int llen = (ldata == null) ? 0 : ldata.length;
    int dlen = (ddata == null) ? 0 : ddata.length;
    int slen = (sdata == null) ? 0 : sdata.length;
    int guess = ENCODING_SPACE_LONG + ENCODING_SPACE_INT + ilen * ENCODING_SPACE_INT +
                llen * ENCODING_SPACE_LONG + dlen * ENCODING_SPACE_DOUBLE;
    if (!hasRoom(guess)) {
      return false;
    }
    int savedPosition = getPosition();
    addLongUnchecked(timeStamp);
    addIntUnchecked(et.getIndex());
    for (int i = 0; i < ilen; i++) {
      addIntUnchecked(idata[i]);
    }
    for (int i = 0; i < llen; i++) {
      addLongUnchecked(ldata[i]);
    }
    for (int i = 0; i < dlen; i++) {
      addDoubleUnchecked(ddata[i]);
    }
    for (int i = 0; i < slen; i++) {
      if (!addString(sdata[i])) {
        seek(savedPosition);
        return false;
      }
    }
    return true;
  }

}
