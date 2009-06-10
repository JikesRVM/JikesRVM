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
package org.jikesrvm.adaptive.controller;

import java.io.PrintStream;
import java.util.LinkedList;
import org.jikesrvm.VM;
import org.jikesrvm.Constants;
import org.jikesrvm.classloader.RVMMethod;
import org.jikesrvm.compilers.common.CompiledMethod;
import org.jikesrvm.compilers.common.CompiledMethods;
import org.jikesrvm.util.ImmutableEntryHashMapRVM;

/**
 *  This class records decisions taken by the controller.  It will remember
 *  controller plans, which contain compilation plans and other goodies,
 *  and allows searching for previous decisions
 */
public final class ControllerMemory implements Constants {

  /**
   *  This is a hashtable of controller plans indexed by RVMMethod.
   *  Each method can have a list of such plans associated with.
   */
  private static final ImmutableEntryHashMapRVM<RVMMethod, LinkedList<ControllerPlan>> table =
      new ImmutableEntryHashMapRVM<RVMMethod, LinkedList<ControllerPlan>>();

  /**
   * Number of times controller is awoken and did nothing.
   */
  private static int didNothing = 0;

  /**
   * Number of times controller is awoken
   */
  private static int awoken = 0;

  // counters for chosen opt levels
  private static int numMethodsConsidered = 0;
  private static int numMethodsScheduledForRecomp = 0;
  private static int numBase = 0;
  private static int numOpt0 = 0;
  private static int numOpt1 = 0;
  private static int numOpt2 = 0;
  private static int numOpt3 = 0;
  private static int numOpt4 = 0;

  public static int getNumAwoken() { return awoken; }

  public static int getNumDidNothing() { return didNothing; }

  public static int getNumMethodsConsidered() { return numMethodsConsidered; }

  public static int getNumMethodsScheduledForRecomp() { return numMethodsScheduledForRecomp; }

  public static int getNumBase() { return numBase; }

  public static int getNumOpt0() { return numOpt0; }

  public static int getNumOpt1() { return numOpt1; }

  public static int getNumOpt2() { return numOpt2; }

  public static int getNumOpt3() { return numOpt3; }

  static int getNumOpt4() { return numOpt4; }

  static void incrementNumAwoken() { awoken++; }

  static void incrementNumDidNothing() { didNothing++; }

  static void incrementNumMethodsConsidered() { numMethodsConsidered++; }

  static void incrementNumMethodsScheduledForRecomp() { numMethodsScheduledForRecomp++; }

  public static void incrementNumBase() { numBase++; }

  static void incrementNumOpt0() { numOpt0++; }

  static void incrementNumOpt1() { numOpt1++; }

  static void incrementNumOpt2() { numOpt2++; }

  static void incrementNumOpt3() { numOpt3++; }

  static void incrementNumOpt4() { numOpt4++; }

  /**
   *  Inserts a controller plan keyed on the underlying method
   *
   *  @param plan the controller plan to insert
   */
  static synchronized void insert(ControllerPlan plan) {

    numMethodsScheduledForRecomp++;
    int optLevel = plan.getCompPlan().options.getOptLevel();
    switch (optLevel) {
      case 0:
        numOpt0++;
        break;
      case 1:
        numOpt1++;
        break;
      case 2:
        numOpt2++;
        break;
      case 3:
        numOpt3++;
        break;
      case 4:
        numOpt4++;
        break;
      default:
        if (VM.VerifyAssertions) VM._assert(NOT_REACHED, "Unknown Opt Level");
    }

    // first check to see if there is a plan list for this method
    LinkedList<ControllerPlan> planList = findPlan(plan.getCompPlan().method);

    if (planList == null) {
      // create a plan list, with the single element being this plan
      planList = new LinkedList<ControllerPlan>();

      // no synch needed here because the planList is not in the table yet
      planList.addLast(plan);

      // insert in the hashtable using the method as the hash value
      table.put(plan.getCompPlan().method, planList);
    } else {
      // add the current plan to the end of the list
      synchronized (planList) {
        planList.addLast(plan);
      }
    }

    // tell the plan what list it is on
    plan.setPlanList(planList);
  }

  /**
   * Looks for a controller plan for the passed method
   *
   * @param method   The method to look for
   * @return the list of controller plans for this method if one exists,
   *         otherwise, null
   */
  @SuppressWarnings("unchecked")
  // until HashMapRVM becomes generic
  private static synchronized LinkedList<ControllerPlan> findPlan(RVMMethod method) {
    return table.get(method);
  }

  /**
   *  Find the plan for the compiled method that is passed
   *  @param cmpMethod the compiled method of interest
   *  @return the matching plan or null if none exists.
   */
  public static synchronized ControllerPlan findMatchingPlan(CompiledMethod cmpMethod) {
    RVMMethod method = cmpMethod.getMethod();

    LinkedList<ControllerPlan> planList = findPlan(method);
    if (planList == null) {
      return null;
    } else {
      // iterate over the planList until we get to this item
      synchronized (planList) {
        for (ControllerPlan plan : planList) {
          // exit when we find ourselves
          if (plan.getCMID() == cmpMethod.getId()) {
            return plan;
          }
        } // more to process
      }
      return null;
    }
  }

  /**
   *  Determine if the passed method should be considered as a candidate
   *  for _initial_ AOS recompilation.
   *  A method should not be reconsider for initial AOS recompilation if
   *  a plan already exists for the method whose status is IN_PROGRESS,
   *  COMPLETED, OUTDATED, or ABORTED because of compilation error.
   *
   *  @param method the method of interest
   *  @return whether the method should be considered or not
   */
  static synchronized boolean shouldConsiderForInitialRecompilation(RVMMethod method) {
    LinkedList<ControllerPlan> planList = findPlan(method);
    if (planList == null) {
      return true;
    } else {
      // iterate over the planList until we find a plan whose status is
      // inprogress, completed,
      synchronized (planList) {
        for (ControllerPlan curPlan : planList) {
          // exit when we find ourselves
          byte status = curPlan.getStatus();
          if (status == ControllerPlan.COMPLETED ||
              status == ControllerPlan.IN_PROGRESS ||
              status == ControllerPlan.ABORTED_COMPILATION_ERROR ||
              status == ControllerPlan.OUTDATED) {
            return false;
          }
        }
      }
      return true;  // we didn't find any, so return true
    }
  }

  /**
   * Return true if there is a plan with the given status for the given method
   *
   * @param method the method of interest
   * @param status the status of interest
   * @return whether or not there is plan with that status for the method
   */
  static synchronized boolean planWithStatus(RVMMethod method, byte status) {
    LinkedList<ControllerPlan> planList = findPlan(method);
    if (planList != null) {
      // iterate over the planList until we find a plan with status 'status'
      synchronized (planList) {
        for (ControllerPlan curPlan : planList) {
          if (curPlan.getStatus() == status) {
            return true;
          }
        }
      }
    }
    return false;
  }

  /**
   * Return true iff there is a plan to transition from Base to Opt for a
   * given CMID.
   */
  public static synchronized boolean requestedOSR(int cmid) {
    CompiledMethod cm = CompiledMethods.getCompiledMethod(cmid);

    // make sure that the cm in question is baseline-compiled
    if (cm.getCompilerType() != CompiledMethod.BASELINE) return false;

    // OK; now check for an OSR plan
    RVMMethod m = cm.getMethod();
    if (m == null) return false;
    return planWithStatus(m, ControllerPlan.OSR_BASE_2_OPT);
  }

  /**
   * Return true if there is a completed plan with the given opt level for
   * the given method
   *
   * @param method the method of interest
   * @param optLevel the opt level of interest
   * @return whether or not there is completed plan with that level
   *             for the method
   */
  static synchronized boolean completedPlanWithOptLevel(RVMMethod method, int optLevel) {
    LinkedList<ControllerPlan> planList = findPlan(method);
    if (planList != null) {
      // iterate over the planList until we find a completed plan with the
      // opt level passed
      synchronized (planList) {
        for (ControllerPlan curPlan : planList) {
          if (curPlan.getStatus() == ControllerPlan.COMPLETED &&
              curPlan.getCompPlan().options.getOptLevel() == optLevel) {
            return true;
          }
        }
      }
    }
    return false;
  }

  /**
   * Looks for the last controller plan for the passed method
   *
   * @param  method   The method to look for
   * @return The last controller plan for this method if it exists,
   *         otherwise, null
   */
  public static synchronized ControllerPlan findLatestPlan(RVMMethod method) {
    LinkedList<ControllerPlan> planList = findPlan(method);
    if (planList == null) {
      return null;
    } else {
      return planList.getLast();
    }
  }

  /**
   * This method summarizes the recompilation actions taken for all methods
   * in this object and produces a report to the passed PrintStream.
   * @param log the stream to print to
   */
  public static synchronized void printFinalMethodStats(PrintStream log) {
    // We will traverse the hash table and for each method record its status as
    // one of the following
    //    B -> 0 -> 1 -> 2
    //    B -> 0 -> 1
    //    B -> 0
    //    B      -> 1 -> 2
    //    B -> 0      -> 2
    //    B           -> 2
    //    B      -> 1
    //
    //  We encode these possibilities by turning on 1 of three bits for 0, 1, 2
    //  Also, for all methods that eventually get to level 2, they can be
    //  recompiled an arbitrary amount of times.  We record this in in a counter.

    final int MAX_BIT_PATTERN = 7;
    int[] summaryArray = new int[MAX_BIT_PATTERN + 1];
    int[] recompsAtLevel2Array = new int[MAX_BIT_PATTERN + 1];
    int totalRecompsAtLevel2 = 0;

    // traverse table and give a summary of all actions that have occurred
    for (RVMMethod meth : table.keys()) {
      LinkedList<ControllerPlan> planList = table.get(meth);

      int bitPattern = 0;
      int recompsAtLevel2 = 0;

      for (ControllerPlan plan : planList) {

        // only process plans that were completed or completed and outdated
        // by subsequent plans for this method
        byte status = plan.getStatus();
        if (status == ControllerPlan.COMPLETED || status == ControllerPlan.OUTDATED) {
          int optLevel = plan.getCompPlan().options.getOptLevel();

          // check for recomps at level 2
          if (optLevel == 2 && bitIsSet(bitPattern, 2)) {
            recompsAtLevel2++;
          }

          bitPattern = setBitPattern(bitPattern, optLevel);
        } // if
      } // while

      if (Controller.options.LOGGING_LEVEL >= 2) {
        log.println("Method: " + meth + ", bitPattern: " + bitPattern + ", recompsAtLevel2: " + recompsAtLevel2);
      }

      summaryArray[bitPattern]++;
      // track level 2 recomps per pattern
      recompsAtLevel2Array[bitPattern] += recompsAtLevel2;
    }

    // Print the summary
    int totalUniqueMethods = 0;
    for (int i = 1; i <= MAX_BIT_PATTERN; i++) {
      log.print("    Base");
      for (int optLevel = 0; optLevel <= 2; optLevel++) {
        if (bitIsSet(i, optLevel)) {
          log.print(" -> " + optLevel);
        }
      }
      log.print(": " + summaryArray[i]);
      // print any level 2 recomps for this pattern
      if (recompsAtLevel2Array[i] > 0) {
        totalRecompsAtLevel2 += recompsAtLevel2Array[i];
        log.println(" (" + recompsAtLevel2Array[i] + " opt level 2 recomps)");
      } else {
        log.println();
      }
      totalUniqueMethods = totalUniqueMethods + summaryArray[i];
    }
    log.println("  Num recompilations At level 2: " + totalRecompsAtLevel2);
    log.println("  Num unique methods recompiled: " + totalUniqueMethods + "\n");
  }

  /**
   *  set the optLevel bit in the passed bitPattern and return the result
   *  @param bitPattern
   *  @param optLevel
   */
  static int setBitPattern(int bitPattern, int optLevel) {
    int newPattern = 1;
    newPattern = newPattern << optLevel;
    return newPattern | bitPattern;
  }

  /**
   * check if the bit position defined by the 2nd parm is set in the first parm
   * @param bitPattern
   * @param optLevel
   * @return whether the passed bit is set
   */
  static boolean bitIsSet(int bitPattern, int optLevel) {
    int newPattern = 1;
    newPattern = newPattern << optLevel;
    return (newPattern & bitPattern) > 0;
  }

  static synchronized String asString() {
    return table.toString();
  }
}

