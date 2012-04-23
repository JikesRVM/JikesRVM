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
package org.jikesrvm.compilers.opt.instrsched;

import org.jikesrvm.compilers.opt.ir.Instruction;

/**
 * Resource usage map representation
 * Used by the scheduler to accomodate resource patterns
 *
 * @see OperatorClass
 * @see org.jikesrvm.compilers.opt.ir.Operator
 */
final class ResourceMap {
  private static final int VERBOSE = 0;

  private static void debug(String s) {
    System.out.println(s);
  }

  private static String toBinaryPad32(int value) {
    String s = Integer.toBinaryString(value);
    return String.format("%032s", s);
  }

  /** GROWABLE Resource Usage map. */
  private int[] rumap;
  /** Current size of the RU map. */
  private int size;

  /** Grows the RU map to a given size. For internal use only. */
  private void grow(int s) {
    if (VERBOSE >= 2) {
      debug("Growing from " + size + " to " + s);
    }
    if (size >= s) {
      return;
    }
    int len = rumap.length;
    if (len < s) {
      for (; len < s; len <<= 1) ;
      int[] t = new int[len];
      for (int i = 0; i < rumap.length; i++) {
        t[i] = rumap[i];
      }
      for (int i = rumap.length; i < len; i++) {
        t[i] = OperatorClass.NONE;
      }
      rumap = t;
    }
    size = s;
  }

  /**
   * Creates new resource map.
   */
  public ResourceMap() {
    this(4);
  }

  /**
   * Creates new resource map with desired initial length.
   *
   * @param length desired initial length of the resource map
   */
  public ResourceMap(int length) {
    rumap = new int[length];
    size = 0;
    for (int i = 0; i < length; i++) {
      rumap[i] = OperatorClass.NONE;
    }
  }

  /**
   * Reserves resources for given instruction at given time.
   *
   * @param i instruction
   * @param time time to schedule
   * @return true if succeeded, false if there was a conflict
   * @see #unschedule(Instruction)
   */
  public boolean schedule(Instruction i, int time) {
    if (SchedulingInfo.isScheduled(i)) {
      throw new InternalError("Already scheduled");
    }
    OperatorClass opc = i.operator().getOpClass();
    if (VERBOSE >= 2) {
      debug("Op Class=" + opc);
    }
    for (int alt = 0; alt < opc.masks.length; alt++) {
      int[] ru = opc.masks[alt];
      if (schedule(ru, time)) {
        SchedulingInfo.setInfo(i, alt, time);
        return true;
      }
    }
    return false;
  }

  /**
   * Frees resources for given instruction.
   *
   * @param i instruction
   * @see #schedule(Instruction,int)
   */
  public void unschedule(Instruction i) {
    if (!SchedulingInfo.isScheduled(i)) {
      throw new InternalError("Not scheduled");
    }
    OperatorClass opc = i.operator().getOpClass();
    int[] ru = opc.masks[SchedulingInfo.getAlt(i)];
    unschedule(ru, SchedulingInfo.getTime(i));
    SchedulingInfo.resetInfo(i);
  }

  /**
   * Returns a string representation of the resource map.
   *
   * @return a string representation of the resource map
   */
  @Override
  public String toString() {
    StringBuilder sb = new StringBuilder();
    for (int i = 0; i < size; i++) {
      sb.append(toBinaryPad32(rumap[i])).append("\n");
    }
    return sb.toString();
  }

  // Binds resources for given resource usage pattern at given time.
  // Returns false if there is a resource conflict.
  // For internal use only.
  private boolean schedule(int[] usage, int time) {
    grow(time + usage.length);
    if (VERBOSE >= 1) {
      debug("Pattern (" + usage.length + ")");
      for (int anUsage : usage) debug("   " + toBinaryPad32(anUsage));
      debug("");
    }
    for (int i = 0; i < usage.length; i++) {
      if ((usage[i] & rumap[time + i]) != 0) {
        return false;
      }
    }
    for (int i = 0; i < usage.length; i++) {
      rumap[time + i] |= usage[i];
    }
    return true;
  }

  // Unbinds resources for given resource usage pattern at given time.
  // For internal use only.
  private void unschedule(int[] usage, int time) {
    for (int i = 0; i < usage.length; i++) {
      rumap[time + i] &= ~usage[i];
    }
  }
}



