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
 * Object containing scheduling information
 * Used by the scheduler
 */
final class SchedulingInfo {
  int alt;
  int time;
  int etime;
  int cp;

  /**
   * For internal use only. Clients should
   * invoke {@link #createInfo(Instruction)}.
   */
  private SchedulingInfo() {
    alt = -1;
    time = -1;
    etime = -1;
    cp = -1;
  }

  /**
   * Initializes scheduling information for instruction.
   *
   * @param i instruction
   */
  public static void createInfo(Instruction i) {
    i.scratchObject = new SchedulingInfo();
  }

  /**
   * Removes scheduling information from instruction.
   *
   * @param i instruction
   */
  public static void removeInfo(Instruction i) {
    i.scratchObject = null;
  }

  /**
   * Returns scheduling information for instruction.
   *
   * @param i instruction
   * @return scheduling info for instruction
   */
  public static SchedulingInfo getInfo(Instruction i) {
    return (SchedulingInfo) i.scratchObject;
  }

  /**
   * Adds scheduling information to instruction.
   *
   * @param i instruction
   * @param alt scheduling alternative
   * @param time scheduling time
   */
  public static void setInfo(Instruction i, int alt, int time) {
    SchedulingInfo si = getInfo(i);
    si.alt = alt;
    si.time = time;
  }

  /**
   * Clears scheduling information of instruction.
   *
   * @param i instruction
   */
  public static void resetInfo(Instruction i) {
    setInfo(i, -1, -1);
  }

  /**
   * Checks whether instruction is scheduled.
   *
   * @param i instruction
   * @return true if instruction is scheduled, false otherwise
   */
  public static boolean isScheduled(Instruction i) {
    return getInfo(i).alt != -1;
  }

  /**
   * Returns scheduling alternative for instruction.
   *
   * @param i instruction
   * @return scheduling alternative for instruction
   */
  public static int getAlt(Instruction i) {
    return getInfo(i).alt;
  }

  /**
   * Returns scheduling time for instruction.
   *
   * @param i instruction
   * @return scheduling time for instruction
   */
  public static int getTime(Instruction i) {
    return getInfo(i).time;
  }

  /**
   * Returns earliest scheduling time for instruction.
   *
   * @param i instruction
   * @return earliest scheduling time for instruction
   */
  public static int getEarliestTime(Instruction i) {
    return getInfo(i).etime;
  }

  /**
   * Sets earliest scheduling time for instruction.
   *
   * @param i instruction
   * @param etime earliest scheduling time for instruction
   */
  public static void setEarliestTime(Instruction i, int etime) {
    getInfo(i).etime = etime;
  }

  /**
   * Returns critical path length for instruction.
   *
   * @param i instruction
   * @return critical path length for instruction
   */
  public static int getCriticalPath(Instruction i) {
    return getInfo(i).cp;
  }

  /**
   * Sets critical path length for instruction.
   *
   * @param i instruction
   * @param cp critical path length for instruction
   */
  public static void setCriticalPath(Instruction i, int cp) {
    getInfo(i).cp = cp;
  }

  /**
   * Returns a string representation of scheduling info.
   *
   * @return string representation of scheduling info
   */
  @Override
  public String toString() {
    return "time=" + time + "; alt=" + alt + "; eTime=" + etime + "; cp=" + cp;
  }
}
