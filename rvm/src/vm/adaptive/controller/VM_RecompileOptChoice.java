/*
 * (C) Copyright IBM Corp. 2001
 */
//$Id$
package com.ibm.JikesRVM.adaptive;

import com.ibm.JikesRVM.VM;
import com.ibm.JikesRVM.VM_CompiledMethod;
import com.ibm.JikesRVM.classloader.VM_NormalMethod;

/**
 * Represents the recompilation choice of simply recompiling the
 * method in question at a particular opt-level.  The cost is the
 * expected compilation time at that level, and the benefit is the
 * execution improvement of executing at that level.
 *
 * @author Matthew Arnold 
 */
class VM_RecompileOptChoice extends VM_RecompilationChoice {

  /** 
   * The opt level associated with this recompilation choice 
   */ 
  private int thisChoiceOptLevel;

  /** 
   * The "compiler" (see VM_CompilerDNA) that is associated with this choice 
   */
  private int thisChoiceCompiler;

  /**
   * Constructor
   */
  VM_RecompileOptChoice(int level) {
    thisChoiceOptLevel = level;
    thisChoiceCompiler = VM_CompilerDNA.getCompilerConstant(level); 
  }

  /**
   * What is the cost of executing this plan?
   *
   * @param meth The method being considered for recompilation.
   * @return The expected cost of exeuting this recompilation choice
   */
  double getCost(VM_NormalMethod meth) {
    return VM_CompilerDNA.estimateCompileTime(getCompiler(), meth);
  }


  /**
   * What is the benefit of executing this plan, given the estimated
   * future time for the method if nothing changes?
   *
   * @param prevCompiler The previous compiler 
   * @param futureExecutionTime The expected future execution time of
   *        the method if left running with the previous compiler.
   * @return The expected future execution time if this choice were selected 
   */
  double getFutureExecutionTime(int prevCompiler, 
                                double futureTimeForMethod) {
    double rtFactor = 
      VM_CompilerDNA.getBenefitRatio(prevCompiler, getCompiler());
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
  VM_ControllerPlan makeControllerPlan(VM_CompiledMethod cmpMethod,
                                       int prevCompiler, 
                                       double prevTimeForMethod,
                                       double bestActionTime,
                                       double expectedCompilationTime) {
    double speedup = 
      VM_CompilerDNA.getBenefitRatio(prevCompiler, getCompiler());
    double priority = prevTimeForMethod - bestActionTime;
    return VM_Controller.recompilationStrategy.
      createControllerPlan(cmpMethod.getMethod(), thisChoiceOptLevel, 
                           null, cmpMethod.getId(), speedup, 
                           expectedCompilationTime,
                           priority);
  }

  /**
   * How should this choice be displayed?
   */
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
   * Which "compiler" (@see VM_CompilerDNA) is associated with this choice?
   */
  int getCompiler() {
    return thisChoiceCompiler;
  }
}
