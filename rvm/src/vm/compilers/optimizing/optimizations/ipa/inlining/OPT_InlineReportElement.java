/*
 * (C) Copyright IBM Corp. 2001
 */
//$Id$

/**
 * OPT_InlineReportElement.java
 *
 * This class represents a single inlining decision instance.
 * It consists of:
 * <ul>
 * <li> a lot of instance data members to hold properties
 * <li> a lot of accessor methods to set the data members
 * <li> a toString method
 * </ul>
 *
 * @author Anne Mulhern
 *
 * @see OPT_InlineReport
 */
class OPT_InlineReportElement {
  // These values set in setValues method
  /*
   * The caller method, obtained from the OPT_CompilationState object
   * @see #setValues
   */
  private VM_Method caller;
  /*
   * The callee method, obtained from the OPT_CompilationState object
   * @see #setValues
   */
  private VM_Method callee;
  /**
   * does the callee contain a pragma inline directive?
   * This information contained in the method summary, so summary must exist.
   *
   * @see OPT_InlineTools#hasInlinePragma
   */
  private boolean hasInlinePragma;
  /**
   * does the callee contain a pragma no inline directive?
   * This information contained in the method summary, so summary must exist.
   *
   * @see OPT_InlineTools#hasNoInlinePragma
   */
  private boolean hasNoInlinePragma;
  /**
   * what is the inlined size estimate for the callee?
   * This information contained in the method summary, so summary must exist.
   *
   * @see OPT_InlineTools#inlinedSizeEstimate
   */
  private int inlinedSizeEstimate;
  /**
   * Does this call need a guard according to OPT_InlineTools.needsGuard()
   * @see OPT_InlineTools#needsGuard
   */
  private boolean needsGuard;
  /**
   * Is the call potentially recursive?
   */
  private boolean isRecursive;
  /**
   * Is the callee final?
   */
  private boolean isFinal;
  /**
   * Is the callee's declaring class final
   */
  private boolean declaringClassFinal;
  /**
   * Is the callee private?
   */
  private boolean isPrivate;
  /**
   * Is the callee a constructor?
   */
  private boolean isObjectInitializer;
  /**
   * Is the callee static?
   */
  private boolean isStatic;
  /**
   * does the target need a dynamic link?
   *
   * @see VM_Member#needsDynamicLink
   */
  private boolean needsDynamicLink;
  /**
   * is the callee abstract?
   */
  private boolean isAbstract;
  /**
   * is the callee native?
   */
  private boolean isNative;
  /**
   * The instruction corresponding to this call site
   */
  private OPT_Instruction instruction;
  /**
   * max size to always inline
   */
  private int maxAlwaysInlineSize;
  /**
   * computedTarget held by state
   */
  private VM_Method computedTarget;
  /**
   * inlineSequence for callee
   */
  private OPT_InlineSequence sequence;
  /**
   * max size to inline
   */
  private int maxTargetSize;
  /**
   * What is the inline depth for inlining this call?
   *
   * @see OPT_CompilationState#getInlineDepth
   */
  private int inlineDepth;
  /**
   * max depth to inline 
   */
  private int maxInlineDepth;

  /**
   * sets values that can be extraced from the state, caller, and callee
   * 
   * @param state contains lots of static information about callee
   * @param callee 
   *
   * @see OPT_CompilationState
   */
  public void setValues (OPT_CompilationState state) {
    caller = state.getMethod();
    callee = state.obtainTarget();
    OPT_Options opts = state.getOptions();
    if (callee != null) {
      if (OPT_InlineTools.legalToInline(caller, callee)) {
        hasInlinePragma = OPT_InlineTools.hasInlinePragma(callee, state);
        hasNoInlinePragma = OPT_InlineTools.hasNoInlinePragma(callee, 
            state);
        inlinedSizeEstimate = OPT_InlineTools.inlinedSizeEstimate(callee, 
            state);
      }
      if (callee.getDeclaringClass().isLoaded()) {
        needsGuard = OPT_InlineTools.needsGuard(callee);
        isRecursive = state.getSequence().containsMethod(callee);
        isFinal = callee.isFinal();
        declaringClassFinal = callee.getDeclaringClass().isFinal();
        isPrivate = callee.isPrivate();
        isObjectInitializer = callee.isObjectInitializer();
        isStatic = callee.isStatic();
        needsDynamicLink = OPT_ClassLoaderProxy.needsDynamicLink(callee,caller.getDeclaringClass());
        isAbstract = callee.isAbstract();
        isNative = callee.isNative();
      }
    }
    instruction = state.getCallInstruction();
    maxAlwaysInlineSize = opts.IC_MAX_ALWAYS_INLINE_TARGET_SIZE;
    computedTarget = state.getComputedTarget();
    sequence = state.getSequence();
    maxTargetSize = opts.IC_MAX_TARGET_SIZE;
    inlineDepth = state.getInlineDepth();
    maxInlineDepth = opts.IC_MAX_INLINE_DEPTH;
  }

  /**
   * constructor
   */
  public OPT_InlineReportElement () {
    isPreciseType = false;
    inliningDeferred = false;
    unimplementedMagic = false;
    instructionNull = false;
    isMagic = false;
  }
  /**
   * The type of this instruction, whether
   *    invokevirtual
   *    invokestatic
   *    invokespecial
   *    invokeinterface
   */
  private int callType;

  /*
   * set call type of instruction
   *
   * @param type should be OPT_InlineReport static final field
   */
  public void setCallType (int type) {
    callType = type;
  }
  /*
   * is the type of the receiver expression known precisely at compile time
   */
  private boolean isPreciseType;

  /*
   * the type of the receiver expression is known precisely at compile time
   */
  public void isPreciseType () {
    isPreciseType = true;
  }
  /*
   * Target obtained from bytecodes
   */
  private VM_Method compileTimeTarget;
  /**
   * target method class unresolved
   */
  private boolean isUnresolved;

  /*
   * checks if the class of the callee is unresolved
   *
   * @see #isUnresolved
   * @see #compileTimeTarget
   */
  public void classUnresolved (boolean val, VM_Method meth) {
    isUnresolved = val;
    compileTimeTarget = meth;
  }
  /**
   * The inline decision object returned by the inlining oracle or other
   * inlining mechanism
   */
  private OPT_InlineDecision decision;

  /*
   * sets decision
   */
  public void setDecision (OPT_InlineDecision d) {
    decision = d;
  }
  /**
   * inlining deferred until after semantic expansion
   */
  private boolean inliningDeferred;

  /**
   * called is inlining deferred until after semantic expansion
   */
  public void inliningDeferred () {
    inliningDeferred = true;
  }
  /*
   * magic unimplemented for the callee method
   */
  private boolean unimplementedMagic;

  /*
   * called if magic unimplemented for the callee method
   */
  public void unimplementedMagic (VM_Method method) {
    unimplementedMagic = true;
    magicMethod = method;
  }
  /*
   * is the call instruction extracted from the opcodes null?
   */
  private boolean instructionNull;

  /*
   * sets instructionNull
   */
  public void instructionNull () {
    instructionNull = true;
  }
  /*
   * The magic method
   */
  private VM_Method magicMethod;
  /*
   * generate Magic has generated all the required instructions
   */
  private boolean isMagic;

  /*
   * called if generate magic has generated all necessary instructions
   */
  public void isMagic (VM_Method method) {
    magicMethod = method;
    isMagic = true;
  }

  /**
   * determines if this method is so small that it should always be inlined
   * regardlesss of other size constraints
   *
   * @return true if the method should always be inlined, otherwise false
   *
   * @see OPT_GenericInlineOracle#shouldInline
   */
  private boolean isAlwaysInlineSize () {
    return  inlinedSizeEstimate < maxAlwaysInlineSize;
  }

  /*
   * determines if the method is small enough that it may be inlined
   *
   * @return true if the method may be inlined, otherwise false
   *
   * @see OPT_StaticInlineOracle#shouldInlineInternal
   */
  private boolean isInlineableSize () {
    return  inlinedSizeEstimate <= maxTargetSize;
  }

  /*
   * determines if the inlining depth is allowable
   *
   * @return true if the depth is not too deep, otherwise false
   *
   * @see OPT_StaticInlineOracle#shouldInlineInternal
   */
  private boolean isInlineableDepht () {
    return  inlineDepth <= maxInlineDepth;
  }

  /*
   * Returns a String representation of the object
   *
   * @return A string which represents the object
   *
   * Note: would like to make this configurable at a later time
   */
  public String toString () {
    StringBuffer res = new StringBuffer();
    if (decision != null) {
      if (decision.isYES()) {
        if (decision.needsGuard()) {
          res.append("Guarded inline ");
        } 
        else {
          res.append("Inline ");
        }
        res.append(callee.toString() + " into " + caller.toString() + 
            " ");
        res.append("at " + instruction.bcIndex + " ");
        res.append("because " + decision.toString());
      } 
      else {
        if (callee == null) {
          res.append("No callee to inline.");
        } 
        else {
          res.append("Don't inline ");
          res.append(callee.toString() + " into " + caller.toString() + 
              " ");
          res.append("at " + instruction.bcIndex + " ");
          res.append("because " + decision.toString());
        }
      }
    } 
    else {
      res.append("Decision is null because ");
      if (isUnresolved)
        res.append("the class of " + compileTimeTarget + " is unresolved."); 
      else if (instructionNull)
        res.append("the call site was a null instruction."); 
      else if (isMagic)
        res.append(magicMethod + " is a magic method."); 
      else if (unimplementedMagic)
        res.append(magicMethod + "has an unimplemented magic."); 
      else if (callType == OPT_InlineReport.INVOKE_INTERFACE)
        res.append("receiver expression has interface type."); 
      else 
        res.append("of an unknown reason.");
    }
    res.append("\n");
    //Type of instruction
    res.append("The instruction type was " + 
        OPT_InlineReport.getInstructionType(callType));
    res.append("\n");
    //Computed target
    res.append("Computed target was ");
    if (computedTarget != null) {
      res.append("set to " + computedTarget.toString() + " ");
      res.append("by ");
      if (isPreciseType) {
        res.append("checking precise type of receiver expression.\n");
      } 
      else {
        res.append("an unknown entity.\n");
      }
    } 
    else {
      res.append("not set.\n");
    }
    return  res.toString();
  }
}



