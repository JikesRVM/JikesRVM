/*
 * (C) Copyright IBM Corp. 2001
 */
//$Id$

import instructionFormats.*;

/**
  * This class holds miscellaneous information regarding the state of
  * a compilation
  *
  * @author Stephen Fink
  * @modified Dave Grove
  */
final class OPT_CompilationState {

  /*
   * Interface 
   */

  /**
   * @param call the call instruction being considered for inlining.
   * @param mcSizeEstimate, current guess on total size of final machinecode
   * @param computedTarget the caller has deduced FOR CERTAIN that the
   *	    current CALL instruction resolves to this target. (null if
   *	    not certain.)
   * @param isExtant is the receiver of a virtual call an extant object?
   * @param options controlling compiler options
   * @@param cm compiled method of the IR object being compiled
   */
  public OPT_CompilationState(OPT_Instruction call,
			      int mcSizeEstimate, 
			      VM_Method computedTarget, 
			      boolean isExtant,
			      OPT_Options options,
			      VM_CompiledMethod cm) {
    this.call = call;
    this.mcSizeEstimate = mcSizeEstimate;
    this.computedTarget = computedTarget;
    this.isExtant = isExtant;
    this.options = options;
    this.cm = cm;
  }

  /**
   * Does this state represent an invokeinterface call?
   * 
   * @return <code>true</code> if it is an interface call
   *         or <code>false</code> if it is not.
   */
   boolean isInvokeInterface() {
     return Call.getMethod(call).isInterface();
   }

  /** 
   * Return the depth of inlining so far.
   */
  public int getInlineDepth() {
    return call.position.getInlineDepth();
  }

  /**
   * Return the call instruction being considered for inlining
   */
  public OPT_Instruction getCallInstruction() {
    return call;
  }

  /**
   * Obtain the target method from the compilation state.
   * If a computed target is present, use it.
   */
  public VM_Method obtainTarget() {
    if (computedTarget == null) 
      return Call.getMethod(call).method;
    else
      return computedTarget;
  }

  /** 
   * Return the controlling compiler options
   */
  public OPT_Options getOptions() {
    return options;
  }

  /** 
   * Return whether or not the receiving object is extant
   */
  public boolean getIsExtant() {
    return isExtant;
  }

  /** 
   * Return the number of bytecodes compiled so far.
   */
  public int getMCSizeEstimate() {
    return mcSizeEstimate;
  }

  /** 
   * Return the root method of the compilation 
   */
  public VM_Method getRootMethod() {
    return call.position.getRootMethod();
  }

  /** 
   * Return the method being compiled
   */
  public VM_Method getMethod() {
    return call.position.getMethod();
  }

  /** 
   * Return the current bytecode index
   */
  public int getBytecodeIndex() {
    return call.bcIndex;
  }

  /** 
   * Return the inlining sequence
   */
  public OPT_InlineSequence getSequence() {
    return call.position;
  }

  /** 
   * Return the computed target
   */
  public VM_Method getComputedTarget() {
    return computedTarget;
  }
  
  /**
   * Return the compiled method
   */
  public VM_CompiledMethod getCompiledMethod() {
    return cm;
  }

  /*
   * Implementation 
   */
  private OPT_Instruction call;
  private int mcSizeEstimate;
  private VM_Method computedTarget;
  private boolean isExtant;
  private OPT_Options options;
  private VM_CompiledMethod cm;
}
