/*
 * (C) Copyright IBM Corp. 2001
 */
//$Id$
package com.ibm.JikesRVM.opt;

import com.ibm.JikesRVM.*;
import com.ibm.JikesRVM.classloader.*;

/**
 * Instances of this class represent decisions to inline.
 *
 * @author Stephen Fink
 */
public final class OPT_InlineDecision {

  /** 
   * Return a decision NOT to inline.
   * 
   * @param target the method that is not being inlined.
   * @param reason a rationale for not inlining
   * @return a decision NOT to inline
   */
  public static OPT_InlineDecision NO(VM_Method target, String reason) {
    VM_Method[] targets = new VM_Method[1];
    targets[0] = target;
    return new OPT_InlineDecision(targets, null, DECIDE_NO, reason);
  }

  /**
   * Return a decision NOT to inline.
   * 
   * @param reason a rationale for not inlining
   * @return a decision NOT to inline
   */
  public static OPT_InlineDecision NO(String reason) {
    return new OPT_InlineDecision(null, null, DECIDE_NO, reason);
  }

  /** 
   * Return a decision to inline without a guard.
   * @param target the method to inline
   * @param reason a rationale for inlining
   * @return a decision YES to inline
   */
  public static OPT_InlineDecision YES(VM_Method target, String reason) {
    VM_Method[] targets = new VM_Method[1];
    targets[0] = target;
    return new OPT_InlineDecision(targets, null, DECIDE_YES, reason);
  }

  /** 
   * Return a decision YES to do a guarded inline.
   * 
   * @param target the method to inline
   * @param guard  the type of guard to use
   * @param reason a rationale for inlining
   * @return a decision YES to inline, but it is not always safe. 
   */
  public static OPT_InlineDecision guardedYES(VM_Method target, 
                                              byte guard, 
                                              String reason) {
    VM_Method[] targets = new VM_Method[1];
    byte[] guards = new byte[1];
    targets[0] = target;
    guards[0] = guard;
    return new OPT_InlineDecision(targets, guards, GUARDED_YES, reason);
  }

  /** 
   * Return a decision YES to do a guarded inline.
   * 
   * @param target the method to inline
   * @param guard  the type of guard to use
   * @param reason a rationale for inlining
   * @return a decision YES to inline, but it is not always safe. 
   */
  public static OPT_InlineDecision guardedYES(VM_Method[] targets, 
                                              byte[] guards,
                                              String reason) {
    return new OPT_InlineDecision(targets, guards, GUARDED_YES, reason);
  }

  /**
   * Is this inline decision a YES?
   */
  public boolean isYES () {
    return !isNO();
  }

  /**
   * Is this inline decision a NO?
   */
  public boolean isNO () {
    return (code == DECIDE_NO);
  }

  /**
   * Does this inline site need a guard?
   */
  public boolean needsGuard () {
    return (code == GUARDED_YES);
  }

  /**
   * Return the methods to inline according to this decision.
   */
  public VM_Method[] getTargets () {
    return targets;
  }

  /**
   * Return the guards to use according to this decision.
   */
  public byte[] getGuards () {
    return guards;
  }

  /**
   * Return the number methods to inline.
   */
  public int getNumberOfTargets () {
    if (targets == null)
      return 0;
    return targets.length;
  }

  /**
   * Should the test-failed block be replaced with an OSR point?
   */
  //-#if RVM_WITH_OSR
  private boolean testFailedOSR = false;
  public void setOSRTestFailed() { testFailedOSR = true; }
  public boolean OSRTestFailed() { return testFailedOSR; }
  //-#endif
  
  /**
   * Symbolic constant coding internal state.
   */
  private static final short DECIDE_NO = 0;
  /**
   * Symbolic constant coding internal state.
   */
  private static final short DECIDE_YES = 1;
  /**
   * Symbolic constant coding internal state.
   */
  private static final short GUARDED_YES = 2;
  /**
   * Rationale for this decision
   */
  private String rationale;
  /**
   * Holds characterization of this decision.
   */
  private short code;
  /**
   * The set of methods to inline.
   */
  private VM_Method[] targets;
  /**
   * The set of guards to use
   * (only valid when code == GUARDED_YES)
   */
  private byte[] guards;


  /** 
   * @param target the methods to inline
   * @param code the decision code
   * @param reason a string rationale
   */
  private OPT_InlineDecision (VM_Method[] targets, byte[] guards,
                              short code, String reason) {
    this.targets = targets;
    this.guards = guards;
    this.code = code;
    this.rationale = reason;
  }

  /** 
   * @param code the decision code
   * @param reason a string rationale
   */
  private OPT_InlineDecision (short code, String reason) {
    this.code = code;
    this.rationale = reason;
  }

  public String toString () {
    String s = null;
    if (code == DECIDE_NO)
      s = "DECIDE_NO"; 
    else if (code == DECIDE_YES)
      s = "DECIDE_YES"; 
    else if (code == GUARDED_YES)
      s = "GUARDED_YES";
    //-#if RVM_WITH_OSR
    if (testFailedOSR)
      s += "(OSR off-branch)";
    //-#endif
    s += ":" + rationale;
    if (targets != null) {
      for (int i = 0; i < targets.length; i++) {
        s += " " + targets[i];
        if (guards != null) {
          switch (guards[i]) {
          case OPT_Options.IG_METHOD_TEST:
            s += " (method test)";
            break;
          case OPT_Options.IG_CLASS_TEST:
            s += " (class test)";
            break;
          case OPT_Options.IG_CODE_PATCH:
            s += " (code patch)";
            break;
          }
        }
      }
    }
    return s;
  }
}



