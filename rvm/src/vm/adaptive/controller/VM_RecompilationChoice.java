/*
 * (C) Copyright IBM Corp. 2001
 */
//$Id$
package com.ibm.JikesRVM.adaptive;

import com.ibm.JikesRVM.VM_CompiledMethod;
import com.ibm.JikesRVM.classloader.VM_NormalMethod;

/**
 * A recompilation choice represents an action (or a set of actions)
 * that can be considered by the controller's analytic model.
 *
 * @author Matthew Arnold
 */
abstract class VM_RecompilationChoice {

  //--- Interface ---

  /**
   * What is the cost of selecting this recompilation choice
   *
   * @param meth The method being considered for recompilation.
   * @return The expected cost of exeuting this recompilation choice
   */
  abstract double getCost(VM_NormalMethod meth);

  /**
   * What is the benefit of executing this recompilation choice, given
   * the estimated future time for the method if nothing changes?  
   *
   * @param prevCompiler The previous compiler 
   * @param futureExecutionTime The expected future execution time of
   *        the method if left running with the previous compiler.
   * @return The expected future execution time if this choice were selected 
   */
  abstract double getFutureExecutionTime(int prevCompiler, 
                                         double futureExecutionTime);

  /**
   * Return a controller plan that will start this recompilation choice 
   * in action
   *
   * @param cmpMethod The method in question
   * @param prevCompiler The previous compiler
   * @param prevTimeFormethod The estimated future time had nothing been done
   * @param bestActionTime The estimated total time implementing this choice
   * @param bestCost The estimated compilation cost implementing this choice
   * @return The controller plan implementing this recompilation choice
   */
  abstract VM_ControllerPlan makeControllerPlan(VM_CompiledMethod cmpMethod,
                                                int prevCompiler,
                                                double prevTimeFormethod,
                                                double bestActionTime,
                                                double bestCost);

}







