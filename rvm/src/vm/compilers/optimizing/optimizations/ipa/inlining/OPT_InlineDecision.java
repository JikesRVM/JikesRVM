/*
 * (C) Copyright IBM Corp. 2001
 */
//$Id$

/**
 * Instances of this class represent decisions to inline.
 *
 * @author Stephen Fink
 */
final class OPT_InlineDecision {

  /** 
   * Return a decision NOT to inline
   * @param reason a rationale for not inlining
   * @return a decision NOT to inline
   */
  public static OPT_InlineDecision NO (VM_Method target, String reason) {
    VM_Method targets[] = new VM_Method[1];
    targets[0] = target;
    return  new OPT_InlineDecision(targets, DECIDE_NO, reason);
  }

  /**
   * Return a decision NOT to inline
   * @param reason a rationale for not inlining
   * @return a decision NOT to inline
   */
  public static OPT_InlineDecision NO (String reason) {
    return  new OPT_InlineDecision(null, DECIDE_NO, reason);
  }

  /** 
   * Return a decision YES to inline.  This decision is safe
   * under all situations.
   * @param target the method to inline
   * @param reason a rationale for inlining
   * @return a decision YES to inline
   */
  public static OPT_InlineDecision YES (VM_Method target, String reason) {
    VM_Method targets[] = new VM_Method[1];
    targets[0] = target;
    return  new OPT_InlineDecision(targets, DECIDE_YES, reason);
  }

  /** 
   * Return a decision YES to inline, but it is not always safe. 
   * In this case, the calling code must likely insert a guard
   * to protect the inlined code
   * @param target the method to inline
   * @param reason a rationale for inlining
   * @return a decision YES to inline, but it is not always safe. 
   */
  public static OPT_InlineDecision unsafeYES (VM_Method target, String reason) {
    VM_Method targets[] = new VM_Method[1];
    targets[0] = target;
    return  new OPT_InlineDecision(targets, UNSAFE_YES, reason);
  }

  /** 
   * Return a decision YES to inline, but it is not always safe. 
   * In this case, the calling code must likely insert a guard
   * to protect the inlined code
   * @param target the method to inline
   * @param reason a rationale for inlining
   * @return a decision YES to inline, but it is not always safe. 
   */
  public static OPT_InlineDecision unsafeYES (
      VM_Method[] targets, String reason) {
    return  new OPT_InlineDecision(targets, UNSAFE_YES, reason);
  }

  /**
   * Is this inline decision a YES?
   */
  public boolean isYES () {
    return  !isNO();
  }

  /**
   * Is this inline decision a NO?
   */
  public boolean isNO () {
    return  (code == DECIDE_NO);
  }

  /**
   * Does this inline site need a guard, due to possibly mispredicted
   * virtual invocation?
   */
  public boolean needsGuard () {
    return  (code == UNSAFE_YES);
  }

  /**
   * Return the methods to inline according to this decision.
   */
  public VM_Method[] getTargets () {
    return  targets;
  }

  /**
   * Return the number methods to inline.
   */
  public int getNumberOfTargets () {
    if (targets == null)
      return  0;
    return  targets.length;
  }

  /**
   * Mark this decision as SAFE (needs no guard)
   */
  public void setSafe () {
    if (code == UNSAFE_YES)
      code = DECIDE_YES;
  }

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
  private static final short UNSAFE_YES = 2;
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
  private VM_Method targets[];

  /** 
   * @param target the methods to inline
   * @param code the decision code
   * @param reason a string rationale
   */
  private OPT_InlineDecision (VM_Method targets[], short code, String reason) {
    this.targets = targets;
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
    else if (code == UNSAFE_YES)
      s = "UNSAFE_YES";
    s += ":" + rationale;
    if (targets != null) {
      for (int i = 0; i < targets.length; i++)
        s += " " + targets[i];
    }
    return  s;
  }
}



