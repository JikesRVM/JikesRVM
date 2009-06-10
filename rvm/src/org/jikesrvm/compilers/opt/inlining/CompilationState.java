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
package org.jikesrvm.compilers.opt.inlining;

import org.jikesrvm.classloader.RVMMethod;
import org.jikesrvm.classloader.NormalMethod;
import org.jikesrvm.compilers.common.CompiledMethod;
import org.jikesrvm.compilers.opt.OptOptions;
import org.jikesrvm.compilers.opt.ir.Call;
import org.jikesrvm.compilers.opt.ir.Instruction;

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
  private final CompiledMethod cm;
  private final int realBCI;

  /*
   * Interface
   */

  /**
   * @param call the call instruction being considered for inlining.
   * @param isExtant is the receiver of a virtual call an extant object?
   * @param options controlling compiler options
   * @param cm compiled method of the IR object being compiled
   * @param realBCI the real bytecode index of the call instruction, not adjusted because of OSR
   */
  public CompilationState(Instruction call, boolean isExtant, OptOptions options, CompiledMethod cm, int realBCI) {
    this.call = call;
    this.isExtant = isExtant;
    this.options = options;
    this.cm = cm;
    this.realBCI = realBCI;
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
  public RVMMethod obtainTarget() {
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
  public NormalMethod getRootMethod() {
    return call.position.getRootMethod();
  }

  /**
   * Return the method being compiled
   */
  public NormalMethod getMethod() {
    return call.position.getMethod();
  }

  /**
   * Return the real bytecode index associated with this call
   */
  public int getRealBytecodeIndex() {
    return realBCI;
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
  public CompiledMethod getCompiledMethod() {
    return cm;
  }
}
