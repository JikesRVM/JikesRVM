/*
 * (C) Copyright IBM Corp 2002
 */
//$Id$

package com.ibm.JikesRVM.OSR;

import com.ibm.JikesRVM.*;
/**
 * An iterator over an encoded OSR map.
 * It is a bit odd to used now. 
 *     while (it.hasMore()) {
 *       it.getKind();
 *       it.getNumber();
 *       it.getMethodId();
 *       it.getBcIndex();
 *       ....
 *
 *       it.moveToNext();
 *     }
 * 
 * @author Feng Qian
 */

public class OSR_MapIterator implements OSR_Constants{
  private int curidx;
  private int[] maps;  
  private int curmid;
  private int curmpc;

  private boolean moreMethId = false;
  private boolean moreElemnt = false;

  public OSR_MapIterator(int[] mapcode, int index) {
    // skip over the map of registers which are references.
    this.curidx = index + 1;
    this.maps   = mapcode;

    if ((mapcode[index] & NEXT_BIT) != 0) {
      this.moreMethId = true;
      moveToNextMethodId();
    }
  }

  public boolean hasMore() {
    return this.moreElemnt;
  }

  /* after finishing iteration of one method, move to the next, 
   * it if is empty, move further.
   */
  private void moveToNextMethodId() {
//    VM.sysWriteln("move to next method id "+this.curidx);

    this.curmid = maps[curidx] & ~NEXT_BIT;
    this.moreMethId = (maps[curidx] & NEXT_BIT) != 0;
    
    this.curidx ++;
    this.curmpc = maps[curidx] & ~NEXT_BIT;
    this.moreElemnt = (maps[curidx] & NEXT_BIT) != 0;

    this.curidx ++;

    // if this method id entry is empty, skip to the next
    if (!hasMoreElements() && hasMoreMethodId()) {
      moveToNextMethodId();
    }
  }

  /* has next method id to iterate? */
  private boolean hasMoreMethodId() {
    return this.moreMethId;
  }

  /* has next element of this method id to iterate? */
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

    this.moreElemnt    = (maps[curidx] & NEXT_BIT) != 0;
    this.curidx += 2;
    if (!hasMoreElements() && hasMoreMethodId()) {
      moveToNextMethodId();
    }
  }

  /* for the current element, provide a list of queries. */

  /* what kind. */
  public int getKind() {
    return (maps[curidx] & KIND_MASK) >> KIND_SHIFT;
  }

  /* type code. */
  public int getTypeCode() {
    return (maps[curidx] & TCODE_MASK) >> TCODE_SHIFT;
  }

  /* number */
  public int getNumber() {
    return (maps[curidx] & NUM_MASK) >> NUM_SHIFT;
  }

  /* value type */
  public int getValueType() {
    return (maps[curidx] & VTYPE_MASK) >> VTYPE_SHIFT;
  }

  /* value */
  public int getValue() {
    return (maps[curidx+1]);
  }

  /* current mid */
  public int getMethodId() {
    return this.curmid;
  }

  /* current pc */
  public int getBcIndex() {
    return this.curmpc;
  }
}
