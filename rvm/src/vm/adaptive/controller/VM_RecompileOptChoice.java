/*
 * (C) Copyright IBM Corp. 2001
 */
//$Id$
package com.ibm.JikesRVM;

/**
 * VM_RecompileOptChoice 
 *
 * Represents the recompilation choice of simply recompiling the
 * method in question at a particular opt-level.  The cost is the
 * expected compilation time at that level, and the benefit is the
 * execution improvement of executing at that level.
 *
 *
 * @author Matthew Arnold */

class VM_RecompileOptChoice extends VM_RecompilationChoice {

  /**
   * Constructor
   */
  VM_RecompileOptChoice(int level) {
    this.thisChoiceOptLevel = level;
    this.thisChoiceCompiler = 
      VM_CompilerDNA.getCompilerConstant(level); 
  }

  /**
   * What is the cost of executing this plan?
   *
   * @param prevCompiler The previous compiler 
   * @param prevCompileTime The compile time when compiled with the
   *        previous compiler
   * @return The expected cost of exeuting this recompilation choice
   */
  double getCost(int prevCompiler, double prevCompileTime) {

    double compileTimeFactor = 
      VM_CompilerDNA.getCompileTimeRatio(prevCompiler, getCompiler());

    return prevCompileTime * compileTimeFactor   // compile time
      + VM_Controller.options.FIXED_RECOMPILATION_OVERHEAD;   // fixed cost "brake" 
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
      VM_CompilerDNA.getBenefitRatio(prevCompiler, 
				     getCompiler());
    
    return futureTimeForMethod / rtFactor; // future execution time

  }

  /**
   * Return a controller plan that will start this recompilation
   * choice in action.  In this case, simply create a plan to
   * recompile at level "optLevel"
   *
   * @param cmpMethod The method in question
   * @param prevCompiler The previous compiler
   * @param prevTimeFormethod The estimated future time had nothing been done
   * @param bestActionTime The estimated total time implementing this choice
   * @return The controller plan implementing this recompilation choice
   */
  VM_ControllerPlan makeControllerPlan(VM_CompiledMethod cmpMethod,
				       int prevCompiler, 
				       double prevTimeForMethod,
				       double bestActionTime) {
    double speedup = 
      VM_CompilerDNA.getBenefitRatio(prevCompiler, getCompiler());
    double priority = prevTimeForMethod - bestActionTime;
    return VM_Controller.recompilationStrategy.
      createControllerPlan(cmpMethod.getMethod(), thisChoiceOptLevel, 
			   null, cmpMethod.getId(), speedup, priority);

    
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

  //----  Implementation -----

  /** The opt level associated with this recompilation choice */ 
  private int thisChoiceOptLevel;

  /** The "compiler" (see VM_CompilerDNA) that is associated with this choice */
  private int thisChoiceCompiler;
}
