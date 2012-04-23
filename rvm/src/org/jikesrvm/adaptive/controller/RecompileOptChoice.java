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
   */
  RecompileOptChoice(int level) {
    thisChoiceOptLevel = level;
    thisChoiceCompiler = CompilerDNA.getCompilerConstant(level);
  }

  /**
   * What is the cost of executing this plan?
   *
   * @param meth The method being considered for recompilation.
   * @return The expected cost of exeuting this recompilation choice
   */
  @Override
  double getCost(NormalMethod meth) {
    return CompilerDNA.estimateCompileTime(getCompiler(), meth);
  }

  /**
   * What is the benefit of executing this plan, given the estimated
   * future time for the method if nothing changes?
   *
   * @param prevCompiler The previous compiler
   * @param futureTimeForMethod The expected future execution time of
   *        the method if left running with the previous compiler.
   * @return The expected future execution time if this choice were selected
   */
  @Override
  double getFutureExecutionTime(int prevCompiler, double futureTimeForMethod) {
    double rtFactor = CompilerDNA.getBenefitRatio(prevCompiler, getCompiler());
    return futureTimeForMethod / rtFactor;
  }

  /**
   * Return a controller plan that will start this recompilation
   * choice in action.  In this case, simply create a plan to
   * recompile at level "optLevel"
   *
   * @param cmpMethod The method in question
   * @param prevCompiler The previous compiler
   * @param prevTimeForMethod The estimated future time had nothing been done
   * @param bestActionTime The estimated total time implementing this choice
   * @param expectedCompilationTime The expected time for recompiling
   * @return The controller plan implementing this recompilation choice
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
   */
  int getOptLevel() {
    return thisChoiceOptLevel;
  }

  /**
   * Which "compiler" (@see CompilerDNA) is associated with this choice?
   */
  int getCompiler() {
    return thisChoiceCompiler;
  }
}
