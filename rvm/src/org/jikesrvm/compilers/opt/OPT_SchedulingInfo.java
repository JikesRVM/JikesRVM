/*
 * This file is part of Jikes RVM (http://jikesrvm.sourceforge.net).
 * The Jikes RVM project is distributed under the Common Public License (CPL).
 * A copy of the license is included in the distribution, and is also
 * available at http://www.opensource.org/licenses/cpl1.0.php
 *
 * (C) Copyright IBM Corp. 2001
 */
package org.jikesrvm.compilers.opt;

import org.jikesrvm.compilers.opt.ir.*;
/**
 * Object containing scheduling information
 * Used by the scheduler
 *
 * @author Igor Pechtchanski
 */
final class OPT_SchedulingInfo {
  int alt;
  int time;
  int etime;
  int cp;

  // Creates new scheduling info object.
  // For internal use only.
  // Other classes should invoke createInfo().
  private OPT_SchedulingInfo () {
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
  public static void createInfo (OPT_Instruction i) {
    i.scratchObject = new OPT_SchedulingInfo();
  }

  /**
   * Removes scheduling information from instruction.
   *
   * @param i instruction
   */
  public static void removeInfo (OPT_Instruction i) {
    i.scratchObject = null;
  }

  /**
   * Returns scheduling information for instruction.
   *
   * @param i instruction
   * @return scheduling info for instruction
   */
  public static OPT_SchedulingInfo getInfo (OPT_Instruction i) {
    return  (OPT_SchedulingInfo)i.scratchObject;
  }

  /**
   * Adds scheduling information to instruction.
   *
   * @param i instruction
   * @param alt scheduling alternative
   * @param time scheduling time
   */
  public static void setInfo (OPT_Instruction i, int alt, int time) {
    OPT_SchedulingInfo si = getInfo(i);
    si.alt = alt;
    si.time = time;
  }

  /**
   * Clears scheduling information of instruction.
   *
   * @param i instruction
   */
  public static void resetInfo (OPT_Instruction i) {
    setInfo(i, -1, -1);
  }

  /**
   * Checks whether instruction is scheduled.
   *
   * @param i instruction
   * @return true if instruction is scheduled, false otherwise
   */
  public static boolean isScheduled (OPT_Instruction i) {
    return  getInfo(i).alt != -1;
  }

  /**
   * Returns scheduling alternative for instruction.
   *
   * @param i instruction
   * @return scheduling alternative for instruction
   */
  public static int getAlt (OPT_Instruction i) {
    return  getInfo(i).alt;
  }

  /**
   * Returns scheduling time for instruction.
   *
   * @param i instruction
   * @return scheduling time for instruction
   */
  public static int getTime (OPT_Instruction i) {
    return  getInfo(i).time;
  }

  /**
   * Returns earliest scheduling time for instruction.
   *
   * @param i instruction
   * @return earliest scheduling time for instruction
   */
  public static int getEarliestTime (OPT_Instruction i) {
    return  getInfo(i).etime;
  }

  /**
   * Sets earliest scheduling time for instruction.
   *
   * @param i instruction
   * @param etime earliest scheduling time for instruction
   */
  public static void setEarliestTime (OPT_Instruction i, int etime) {
    getInfo(i).etime = etime;
  }

  /**
   * Returns critical path length for instruction.
   *
   * @param i instruction
   * @return critical path length for instruction
   */
  public static int getCriticalPath (OPT_Instruction i) {
    return  getInfo(i).cp;
  }

  /**
   * Sets critical path length for instruction.
   *
   * @param i instruction
   * @param cp critical path length for instruction
   */
  public static void setCriticalPath (OPT_Instruction i, int cp) {
    getInfo(i).cp = cp;
  }

  /**
   * Returns a string representation of scheduling info.
   *
   * @return string representation of scheduling info
   */
  public String toString () {
    return  "time=" + time + "; alt=" + alt + "; eTime=" + etime + "; cp="
        + cp;
  }
}



