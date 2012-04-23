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
package org.mmtk.harness.lang.compiler;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.mmtk.harness.lang.Declaration;
import org.mmtk.harness.lang.Env;
import org.mmtk.harness.lang.ast.NormalMethod;
import org.mmtk.harness.lang.pcode.PseudoOp;
import org.mmtk.harness.lang.pcode.ResolvableOp;
import org.mmtk.harness.lang.runtime.PcodeInterpreter;
import org.mmtk.harness.lang.runtime.StackFrame;
import org.mmtk.harness.scheduler.Schedulable;

/**
 * A method, compiled into pseudo-ops.
 */
public class CompiledMethod implements Schedulable {

  /** The name of the method */
  private final String name;

  /** The list of instructions */
  private final List<PseudoOp> contents = new ArrayList<PseudoOp>();

  /** The variable declarations */
  private final List<Declaration> decls;

  /** The number of temporaries */
  private int nTemps;

  /**
   * Create an (empty) virtual method for the given parsed method.
   * @param method
   */
  public CompiledMethod(NormalMethod method) {
    this.name = method.getName();
    this.decls = method.getDecls();
  }

  /**
   * Return the array of instructions corresponding to this method
   * @return
   */
  public PseudoOp[] getCodeArray() {
    return contents.toArray(new PseudoOp[0]);
  }

  /**
   * Append new instructions
   * @param elements
   */
  public void append(PseudoOp...elements) {
    contents.addAll(Arrays.asList(elements));
  }

  /**
   * Get the index of the current instruction, eg as a branch target.
   * @return
   */
  public int currentIndex() {
    return contents.size();
  }

  /**
   * Return the name of this method
   * @return
   */
  public String getName() {
    return name;
  }

  /**
   * Set the # temporaries required to execute
   * this method
   * @param nTemps
   */
  public void setTemps(int nTemps) {
    this.nTemps = nTemps;
  }

  /**
   * Resolve all the method references in this method's instructions,
   * by replacing proxy references with the real compiled method.
   */
  public void resolveMethodReferences() {
    for (PseudoOp op : contents) {
      if (op instanceof ResolvableOp) {
        ((ResolvableOp)op).resolve();
      }
    }
  }

  /**
   * Format the p-code for this method
   */
  @Override
  public String toString() {
    StringBuilder buf = new StringBuilder();
    int i=0;
    for (PseudoOp op : contents) {
      buf.append(String.format("%4d : %s%n",i++,op));
    }
    return buf.toString();
  }

  /**
   * Resolve this method.  Since this is a compiled method, not a
   * proxy, we return ourselves.
   * @return
   */
  public CompiledMethod resolve() {
    return this;
  }

  /**
   * A CompiledMethod is always resolved.
   * @return
   */
  public boolean isResolved() {
    return true;
  }

  /**
   * Create a new stack frame for the execution of this method.
   * @return
   */
  public StackFrame formatStackFrame() {
    return new StackFrame(decls,nTemps);
  }

  @Override
  public void execute(Env env) {
    new PcodeInterpreter(env,this).exec();
  }
}
