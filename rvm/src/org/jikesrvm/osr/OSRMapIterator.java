/*
 *  This file is part of the Jikes RVM project (http://jikesrvm.org).
 *
 *  This file is licensed to You under the Eclipse Public License (EPL);
 *  You may not use this file except in compliance with the License. You
 *  may obtain a copy of the License at
 *
 *      http://www.opensource.org/licenses/eclipse-1.0.php
 *
 *  See the COPYRIGHT.txt file distributed with this work for information
 *  regarding copyright ownership.
 */
package org.jikesrvm.osr;

import static org.jikesrvm.osr.OSRConstants.KIND_MASK;
import static org.jikesrvm.osr.OSRConstants.KIND_SHIFT;
import static org.jikesrvm.osr.OSRConstants.NEXT_BIT;
import static org.jikesrvm.osr.OSRConstants.NUM_MASK;
import static org.jikesrvm.osr.OSRConstants.NUM_SHIFT;
import static org.jikesrvm.osr.OSRConstants.TCODE_MASK;
import static org.jikesrvm.osr.OSRConstants.TCODE_SHIFT;
import static org.jikesrvm.osr.OSRConstants.VTYPE_MASK;
import static org.jikesrvm.osr.OSRConstants.VTYPE_SHIFT;

import org.jikesrvm.VM;

/**
 * An iterator over an encoded OSR map.
 * It is a bit odd to used now.
 * <pre>
 *     while (it.hasMore()) {
 *       it.getKind();
 *       it.getNumber();
 *       it.getMethodId();
 *       it.getBcIndex();
 *       ....
 *
 *       it.moveToNext();
 *     }
 *</pre>
 */

public class OSRMapIterator {
  private int curidx;
  private final int[] maps;
  private int curmid;
  private int curmpc;

  private boolean moreMethId = false;
  private boolean moreElemnt = false;

  public OSRMapIterator(int[] mapcode, int index) {
    // skip over the map of registers which are references.
    this.curidx = index + 1;
    this.maps = mapcode;

    if ((mapcode[index] & NEXT_BIT) != 0) {
      this.moreMethId = true;
      moveToNextMethodId();
    }
  }

  public boolean hasMore() {
    return this.moreElemnt;
  }

  /**
   * after finishing iteration of one method, move to the next,
   * it if is empty, move further.
   */
  private void moveToNextMethodId() {
//    VM.sysWriteln("move to next method id "+this.curidx);

    this.curmid = maps[curidx] & ~NEXT_BIT;
    this.moreMethId = (maps[curidx] & NEXT_BIT) != 0;

    this.curidx++;
    this.curmpc = maps[curidx] & ~NEXT_BIT;
    this.moreElemnt = (maps[curidx] & NEXT_BIT) != 0;

    this.curidx++;

    // if this method id entry is empty, skip to the next
    if (!hasMoreElements() && hasMoreMethodId()) {
      moveToNextMethodId();
    }
  }

  /** @return whether another method id is available */
  private boolean hasMoreMethodId() {
    return this.moreMethId;
  }

  /** @return whether there's more elements for the current method id */
  private boolean hasMoreElements() {
    return this.moreElemnt;
  }

  /**
   * Moves the index to the next element, update more first because
   * we use last element's bit to indicate whether this element is
   * available.
   */
  public void moveToNext() {
    if (VM.VerifyAssertions) VM._assert(this.hasMore());

    this.moreElemnt = (maps[curidx] & NEXT_BIT) != 0;
    this.curidx += 2;
    if (!hasMoreElements() && hasMoreMethodId()) {
      moveToNextMethodId();
    }
  }

  /* for the current element, provide a list of queries. */

  public boolean getKind() {
    return (maps[curidx] & KIND_MASK) >> KIND_SHIFT != 0;
  }

  public byte getTypeCode() {
    return (byte)((maps[curidx] & TCODE_MASK) >> TCODE_SHIFT);
  }

  public char getNumber() {
    return (char)((maps[curidx] & NUM_MASK) >> NUM_SHIFT);
  }

  public byte getValueType() {
    return (byte)((maps[curidx] & VTYPE_MASK) >> VTYPE_SHIFT);
  }

  public int getValue() {
    return maps[curidx + 1];
  }

  public int getMethodId() {
    return this.curmid;
  }

  /** @return the current program counter */
  public int getBcIndex() {
    return this.curmpc;
  }
}
