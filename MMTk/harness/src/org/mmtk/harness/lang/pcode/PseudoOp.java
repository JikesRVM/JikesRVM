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
package org.mmtk.harness.lang.pcode;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

import org.mmtk.harness.lang.Env;
import org.mmtk.harness.lang.ast.AST;
import org.mmtk.harness.lang.compiler.Register;
import org.mmtk.harness.lang.runtime.StackFrame;
import org.mmtk.harness.lang.runtime.Value;

/**
 * Base class for the Pcode operators executed by the interpreter
 */
public abstract class PseudoOp {
  private final AST source;
  protected final int arity;
  protected final boolean hasResult;
  private final int resultTemp;
  protected final String name;
  private final Set<Integer> gcMap = new HashSet<Integer>();

  /**
   * Internal constructor - all constructors ultimately call this.
   *
   * @param source
   * @param arity
   * @param name
   * @param hasResult
   * @param resultTemp
   */
  private PseudoOp(AST source, int arity, String name, boolean hasResult, int resultTemp) {
    this.arity = arity;
    this.hasResult = hasResult;
    this.resultTemp = resultTemp;
    this.name = name;
    this.source = source;
    assert (!hasResult) || resultTemp >= 0 : resultTemp;
  }

  /**
   * Constructor for a P-op with a result.
   * @param source
   * @param arity
   * @param name
   * @param resultTemp
   */
  public PseudoOp(AST source, int arity, String name, Register resultTemp) {
    this(source,arity,name,resultTemp != Register.NULL,resultTemp.getIndex());
  }

  /**
   * Construtor for a p-op with no result.
   * @param source
   * @param arity
   * @param name
   */
  public PseudoOp(AST source, int arity, String name) {
    this(source,arity,name,false,-1);
  }

  public abstract void exec(Env env);

  public boolean hasResult() {
    return hasResult;
  }

  public int getResult() {
    if (!hasResult) {
      throw new RuntimeException("Attempted to get a result from a non-result-producing operation");
    }
    return resultTemp;
  }

  public void setResult(StackFrame frame, Value result) {
    frame.set(getResult(), result);
  }

  public Value getResultValue(StackFrame frame) {
    return frame.get(getResult());
  }

  @Override
  public String toString() {
    if (hasResult) {
      return String.format("[%s] %s <- %s", formatGcMap(), Register.nameOf(resultTemp), name);
    }
    return String.format("[%s] %s",formatGcMap(), name);
  }

  public String formatGcMap() {
    StringBuilder result = new StringBuilder();
    boolean first = true;
    for (int i : gcMap) {
      if (!first) {
        result.append(",");
      }
      first = false;
      result.append(i);
    }
    return result.toString();
  }

  /*
   * Instruction types
   */

  /** Is this a branch-like instruction */
  public boolean affectsControlFlow() {
    return false;
  }

  public boolean mayTriggerGc() {
    return isAlloc() || isCall();
  }

  public boolean isBranch() {
    return false;
  }

  public boolean isAlloc() {
    return false;
  }

  public boolean isTaken(Env env) {
    return true;
  }

  public boolean isCall() {
    return false;
  }

  /** Is this a return instruction */
  public boolean isReturn() {
    return false;
  }

  public int getBranchTarget() {
    throw new RuntimeException("Attempt to get a branch target from a non-branch instruction");
  }

  /*
   * GC map handling
   */
  public void setTraced(int slot) {
    gcMap.add(slot);
  }

  public void setUnTraced(int slot) {
    gcMap.remove(slot);
  }

  public void setGcMap(Set<Register> live) {
    for (Register reg : live) {
      gcMap.add(reg.getIndex());
    }
  }

  public Set<Integer> getGcMap() {
    return Collections.unmodifiableSet(gcMap);
  }

  /*
   * Error handling - link back to source line
   */

  /**
   * The source code line leading to this instruction
   */
  public int getLine() {
    return source.getLine();
  }

  /**
   * The source code column leading to this instruction
   */
  public int getColumn() {
    return source.getColumn();
  }

  /**
   * The source location of this instruction (for error messages)
   */
  public String getSourceLocation() {
    return getSourceLocation("");
  }

  /**
   * The source location of this instruction (for error messages)
   */
  public String getSourceLocation(String prefix) {
    return source.sourceLocation(prefix);
  }
}
