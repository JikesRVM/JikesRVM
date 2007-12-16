/*
 *  This file is part of the Jikes RVM project (http://jikesrvm.org).
 *
 *  This file is licensed to You under the Common Public License (CPL);
 *  You may not use this file except in compliance with the License. You
 *  may obtain a copy of the License at
 *
 *      http://www.opensource.org/licenses/cpl1.0.php
 *
 *  See the COPYRIGHT.txt file distributed with this work for information
 *  regarding copyright ownership.
 */
package org.jikesrvm.compilers.opt.ir;

import org.jikesrvm.classloader.VM_Method;
import org.jikesrvm.classloader.VM_NormalMethod;
import org.jikesrvm.compilers.common.VM_CompiledMethod;
import org.jikesrvm.compilers.opt.OptOptions;

/**
 * This class holds miscellaneous information regarding the state of
 * a compilation
 */
public final class CompilationState {

  /*
   * Implementation
   */
  private final Instruction call;
  private final boolean isExtant;
  private final OptOptions options;
  private final VM_CompiledMethod cm;

  /*
   * Interface
   */

  /**
   * @param call the call instruction being considered for inlining.
   * @param isExtant is the receiver of a virtual call an extant object?
   * @param options controlling compiler options
   * @param cm compiled method of the IR object being compiled
   */
  public CompilationState(Instruction call, boolean isExtant, OptOptions options, VM_CompiledMethod cm) {
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
  public Instruction getCallInstruction() {
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
  public OptOptions getOptions() {
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
  public InlineSequence getSequence() {
    return call.position;
  }

  /**
   * Return the compiled method
   */
  public VM_CompiledMethod getCompiledMethod() {
    return cm;
  }
}
