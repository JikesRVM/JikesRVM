/*
 * (C) Copyright IBM Corp. 2001
 */
//$Id$
package com.ibm.JikesRVM.adaptive;

import com.ibm.JikesRVM.VM;
import com.ibm.JikesRVM.VM_Constants;
import com.ibm.JikesRVM.classloader.VM_Method;
import com.ibm.JikesRVM.VM_CompiledMethod;
import com.ibm.JikesRVM.VM_CompiledMethods;
import java.util.*;
import java.io.PrintStream;

/**
 *  This class records decisions taken by the controller.  It will remember
 *  controller plans, which contain compilation plans and other goodies,
 *  and allows searching for previous decisions
 *
 *  @author Michael Hind
 *  @author Stephen Fink
 */
public final class VM_ControllerMemory implements VM_Constants {

  /**
   *  This is a hashtable of controller plans indexed by VM_Method.  
   *  Each method can have a list of such plans associated with.
   */
  private static final HashMap table = new HashMap();

  /**
   * Number of times controller is awoken and did nothing.
   */
  private static int didNothing = 0;

  /**
   * Number of times controller is awoken
   */
  private static int awoken = 0;

  // counters for chosen opt levels
  private static int numMethodsConsidered               = 0;
  private static int numMethodsScheduledForRecomp       = 0;
  private static int numBase                            = 0;
  private static int numOpt0                            = 0;
  private static int numOpt1                            = 0;
  private static int numOpt2                            = 0;
  private static int numOpt3                            = 0;
  private static int numOpt4                            = 0;

  static int getNumAwoken()                    { return awoken; }
  static int getNumDidNothing()                { return didNothing; }
  static int getNumMethodsConsidered()         { return numMethodsConsidered; }
  static int getNumMethodsScheduledForRecomp() 
    { return numMethodsScheduledForRecomp; }
  static int getNumBase()                       { return numBase; }
  static int getNumOpt0()                       { return numOpt0; }
  static int getNumOpt1()                       { return numOpt1; }
  static int getNumOpt2()                       { return numOpt2; }
  static int getNumOpt3()                       { return numOpt3; }
  static int getNumOpt4()                       { return numOpt4; }

  static void incrementNumAwoken()              { awoken++; }
  static void incrementNumDidNothing()          { didNothing++; }
  static void incrementNumMethodsConsidered()   { numMethodsConsidered++; }
  static void incrementNumMethodsScheduledForRecomp()  
    { numMethodsScheduledForRecomp++; }
  public static void incrementNumBase()                { numBase++; }
  static void incrementNumOpt0()                { numOpt0++; }
  static void incrementNumOpt1()                { numOpt1++; }
  static void incrementNumOpt2()                { numOpt2++; }
  static void incrementNumOpt3()                { numOpt3++; }
  static void incrementNumOpt4()                { numOpt4++; }

  /**
   *  Inserts a controller plan keyed on the underlying method
   *
   *  @param plan the controller plan to insert
   */
  static synchronized void insert(VM_ControllerPlan plan) {

    if (VM.LogAOSEvents) {
      VM_Method method = plan.getCompPlan().getMethod();
      numMethodsScheduledForRecomp++;
      int optLevel = plan.getCompPlan().options.getOptLevel();
      switch (optLevel) {
      case 0:  numOpt0++; break;
      case 1:  numOpt1++; break;
      case 2:  numOpt2++; break;
      case 3:  numOpt3++; break;
      case 4:  numOpt4++; break; 
      default:
        if (VM.VerifyAssertions) VM._assert(NOT_REACHED, "Unknown Opt Level");
      }
    }

    // first check to see if there is a plan list for this method
    LinkedList planList = findPlan(plan.getCompPlan().method);

    if (planList == null) {
      // create a plan list, with the single element being this plan
      planList = new LinkedList();

      // no synch needed here because the planList is not in the table yet
      planList.addLast(plan);

      // insert in the hashtable using the method as the hash value
      table.put(plan.getCompPlan().method, planList);
    } else {
      // add the current plan to the end of the list
      synchronized(planList) {
        planList.addLast(plan);
      }
    }

    // tell the plan what list it is on
    plan.setPlanList(planList);
  }

  /**
   * Looks for a controller plan for the passed method
   *
   * @param VM_Method the method to look for
   * @return the list of controller plans for this method if one exists, 
   *         otherwise, null
   */
  private static synchronized LinkedList findPlan(VM_Method method) {
    return (LinkedList)table.get(method);
  }

  /**
   *  Find the plan for the compiled method that is passed
   *  @param cmpMethod the compiled method of interest
   *  @return the matching plan or null if none exists.
   */
  public static synchronized VM_ControllerPlan findMatchingPlan(VM_CompiledMethod cmpMethod) {
    VM_Method method = cmpMethod.getMethod();

    LinkedList planList = findPlan(method);
    if (planList == null) {
      return null;
    } else{
      // iterate over the planList until we get to this item
      boolean found = false;
      VM_ControllerPlan curPlan = null;
      synchronized(planList) {
        ListIterator iter = planList.listIterator();
        while (iter.hasNext()) {
          curPlan = (VM_ControllerPlan) iter.next();

          // exit when we find ourselves
          if (curPlan.getCMID() == cmpMethod.getId()) {
            found = true;
            break;
          }
        } // more to process
      }

      if (!found) {
        // there was a plan for this method, but not for this compiled method
        curPlan = null;
      }

      return curPlan;
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
  static synchronized boolean shouldConsiderForInitialRecompilation(VM_Method method) {
    LinkedList planList = findPlan(method);
    if (planList == null) {
      return true;
    } else {
      // iterate over the planList until we find a plan whose status is
      // inprogress, completed, 
      synchronized(planList) {
        boolean found = false;
        VM_ControllerPlan curPlan = null;
        ListIterator iter = planList.listIterator();
        while (iter.hasNext()) {
          curPlan = (VM_ControllerPlan) iter.next();

          // exit when we find ourselves
          byte status = curPlan.getStatus();
          if (status == VM_ControllerPlan.COMPLETED || 
              status == VM_ControllerPlan.IN_PROGRESS || 
              status == VM_ControllerPlan.ABORTED_COMPILATION_ERROR || 
              status == VM_ControllerPlan.OUTDATED) {
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
  static synchronized boolean planWithStatus(VM_Method method, byte status) {
    LinkedList planList = findPlan(method);
    if (planList != null) {
      // iterate over the planList until we find a plan with status 'status'
      synchronized(planList) {
        ListIterator iter = planList.listIterator();
        while (iter.hasNext()) {
          VM_ControllerPlan curPlan = (VM_ControllerPlan) iter.next();
          if (curPlan.getStatus() == status) {
            return true;
          }
        }
      }
    }
    return false;
  }

  //-#if RVM_WITH_OSR
  /**
   * Return true iff there is a plan to transition from Base to Opt for a
   * given CMID.
   */
  public static synchronized boolean requestedOSR(int cmid) {
    VM_CompiledMethod cm = VM_CompiledMethods.getCompiledMethod(cmid);
    
    // make sure that the cm in question is baseline-compiled
    if (cm.getCompilerType() != VM_CompiledMethod.BASELINE) return false; 

    // OK; now check for an OSR plan 
    VM_Method m = cm.getMethod();
    if (m == null) return false;
    return planWithStatus(m,VM_ControllerPlan.OSR_BASE_2_OPT);
  }
  //-#endif

  /**
   * Return true if there is a completed plan with the given opt level for 
   * the given method
   *
   * @param method the method of interest
   * @param optLevel the opt level of interest
   * @return whether or not there is completed plan with that level 
   *             for the method
   */
  static synchronized boolean completedPlanWithOptLevel(VM_Method method, int optLevel) {
    LinkedList planList = findPlan(method);
    if (planList != null) {
      // iterate over the planList until we find a completed plan with the
      // opt level passed
      synchronized(planList) {
        ListIterator iter = planList.listIterator();
        while (iter.hasNext()) {
          VM_ControllerPlan curPlan = (VM_ControllerPlan) iter.next();
          if (curPlan.getStatus() == VM_ControllerPlan.COMPLETED &&
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
   * @param VM_Method the method to look for
   * @return the last controller plan for this method if it exists, 
   *         otherwise, null
   */
  public static synchronized VM_ControllerPlan findLatestPlan(VM_Method method) {
    LinkedList planList = findPlan(method);
    if (planList == null) {
      return null;
    } else {
      return (VM_ControllerPlan) planList.getLast();
    }
  }

  /**
   * This method summarizes the recompilation actions taken for all methods
   * in this object and produces a report to the passed PrintStream.
   * @param log the stream to print to
   */
  static synchronized void printFinalMethodStats(PrintStream log) {
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
    int summaryArray[] = new int[MAX_BIT_PATTERN+1];
    int totalRecompsAtLevel2 = 0;

    // traverse table and give a summary of all actions that have occurred
    for (Iterator it = table.keySet().iterator(); it.hasNext();) {
      VM_Method meth = (VM_Method) it.next();
      LinkedList planList = (LinkedList) table.get(meth);

      int bitPattern = 0;
      int recompsAtLevel2 = 0;
      VM_ControllerPlan plan = null;
        
      ListIterator iter = planList.listIterator();
      while (iter.hasNext()) {
        plan = (VM_ControllerPlan) iter.next();

        // only process plans that were completed or completed and outdated 
        // by subsequent plans for this method
        byte status = plan.getStatus();
        if (status == VM_ControllerPlan.COMPLETED || 
            status == VM_ControllerPlan.OUTDATED) {
            int optLevel = plan.getCompPlan().options.getOptLevel();

            // check for recomps at level 2
            if (optLevel == 2 && bitIsSet(bitPattern, 2)) {
              recompsAtLevel2++;
            }

            bitPattern = setBitPattern(bitPattern, optLevel);
        } // if
      } // while
      
      if (VM_Controller.options.LOGGING_LEVEL >= 2) {
        log.println("Method: "+ plan.getCompPlan().getMethod() 
                    +", bitPattern: "+ bitPattern
                    +", recompsAtLevel2: "+ recompsAtLevel2);
      }

      summaryArray[bitPattern]++;
      totalRecompsAtLevel2 = totalRecompsAtLevel2 + recompsAtLevel2;
    }

    // Print the summary
    int totalUniqueMethods = 0;
    for (int i=1; i<=MAX_BIT_PATTERN; i++) {
      log.print("    Base");
      for (int optLevel=0;  optLevel <=2; optLevel++) {
        if (bitIsSet(i, optLevel)) {
          log.print(" -> "+ optLevel);
        }
      }
      log.println(": "+ summaryArray[i]);
      totalUniqueMethods = totalUniqueMethods + summaryArray[i];
    }
    log.println("  Num recompilations At level 2: "+ totalRecompsAtLevel2);
    log.println("  Num unique methods recompiled: "+ totalUniqueMethods +"\n");
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

