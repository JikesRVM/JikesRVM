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
package org.jikesrvm.adaptive.controller;

import org.jikesrvm.adaptive.recompilation.CompilerDNA;
import org.jikesrvm.classloader.NormalMethod;
import org.jikesrvm.compilers.common.CompiledMethod;

/**
 * Represents the recompilation choice of simply recompiling the
 * method in question at a particular opt-level.  The cost is the
 * expected compilation time at that level, and the benefit is the
 * execution improvement of executing at that level.
 */
class RecompileOptChoice extends RecompilationChoice {

  /**
   * The opt level associated with this recompilation choice
   */
  private int thisChoiceOptLevel;

  /**
   * The "compiler" (see CompilerDNA) that is associated with this choice
   */
  private int thisChoiceCompiler;

  /**
   * Constructor
   * @param level the opt level associated with this choice
   */
  RecompileOptChoice(int level) {
    thisChoiceOptLevel = level;
    thisChoiceCompiler = CompilerDNA.getCompilerConstant(level);
  }

  @Override
  double getCost(NormalMethod meth) {
    return CompilerDNA.estimateCompileTime(getCompiler(), meth);
  }

  @Override
  double getFutureExecutionTime(int prevCompiler, double futureTimeForMethod) {
    double rtFactor = CompilerDNA.getBenefitRatio(prevCompiler, getCompiler());
    return futureTimeForMethod / rtFactor;
  }

  /**
   * {@inheritDoc}
   * In this case, simply create a plan to recompile at level {@link #thisChoiceOptLevel}.
   */
  @Override
  ControllerPlan makeControllerPlan(CompiledMethod cmpMethod, int prevCompiler, double prevTimeForMethod,
                                       double bestActionTime, double expectedCompilationTime) {
    double speedup = CompilerDNA.getBenefitRatio(prevCompiler, getCompiler());
    double priority = prevTimeForMethod - bestActionTime;
    return Controller.recompilationStrategy.
        createControllerPlan(cmpMethod.getMethod(),
                             thisChoiceOptLevel,
                             null,
                             cmpMethod.getId(),
                             speedup,
                             expectedCompilationTime,
                             priority);
  }

  /**
   * How should this choice be displayed?
   */
  @Override
  public String toString() {
    return "O" + getOptLevel();
  }

  /**
   * Which opt-level is associated with this choice?
   * @return the opt-level for this choice
   */
  int getOptLevel() {
    return thisChoiceOptLevel;
  }

  /**
   * Which "compiler" is associated with this choice?
   * @return the integer representing the compiler for this choice
   * @see CompilerDNA#getCompilerConstant(int)
   */
  int getCompiler() {
    return thisChoiceCompiler;
  }
}
