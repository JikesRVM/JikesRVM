/*
 * (C) Copyright IBM Corp. 2001
 */
//$Id$
package com.ibm.JikesRVM.opt.ir;

import com.ibm.JikesRVM.*;
import com.ibm.JikesRVM.classloader.*;
import com.ibm.JikesRVM.opt.OPT_Options;

/**
  * This class holds miscellaneous information regarding the state of
  * a compilation
  *
  * @author Stephen Fink
  * @modified Dave Grove
  */
public final class OPT_CompilationState {

  /*
   * Interface 
   */

  /**
   * @param call the call instruction being considered for inlining.
   * @param isExtant is the receiver of a virtual call an extant object?
   * @param options controlling compiler options
   * @param cm compiled method of the IR object being compiled
   */
  public OPT_CompilationState(OPT_Instruction call,
			      boolean isExtant,
                              OPT_Options options,
			      VM_CompiledMethod cm) {
    this.call = call;
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
  public boolean isInvokeInterface() {
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
    return Call.getMethod(call).getTarget();
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
   * Return whether or not the target is precise (ie needs no guard)
   */
  public boolean getHasPreciseTarget() {
    return Call.getMethod(call).hasPreciseTarget();
  }

  /** 
   * Return the root method of the compilation 
   */
  public VM_NormalMethod getRootMethod() {
    return call.position.getRootMethod();
  }

  /** 
   * Return the method being compiled
   */
  public VM_NormalMethod getMethod() {
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
   * Return the compiled method
   */
  public VM_CompiledMethod getCompiledMethod() {
    return cm;
  }

  /*
   * Implementation 
   */
  private OPT_Instruction call;
  private boolean isExtant;
  private OPT_Options options;
  private VM_CompiledMethod cm;
}
