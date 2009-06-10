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

import org.jikesrvm.classloader.NormalMethod;
import org.jikesrvm.compilers.common.CompiledMethod;

/**
 * A recompilation choice represents an action (or a set of actions)
 * that can be considered by the controller's analytic model.
 */
abstract class RecompilationChoice {

  //--- Interface ---

  /**
   * What is the cost of selecting this recompilation choice
   *
   * @param meth The method being considered for recompilation.
   * @return The expected cost of exeuting this recompilation choice
   */
  abstract double getCost(NormalMethod meth);

  /**
   * What is the benefit of executing this recompilation choice, given
   * the estimated future time for the method if nothing changes?
   *
   * @param prevCompiler The previous compiler
   * @param futureExecutionTime The expected future execution time of
   *        the method if left running with the previous compiler.
   * @return The expected future execution time if this choice were selected
   */
  abstract double getFutureExecutionTime(int prevCompiler, double futureExecutionTime);

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
  abstract ControllerPlan makeControllerPlan(CompiledMethod cmpMethod, int prevCompiler, double prevTimeFormethod,
                                                double bestActionTime, double bestCost);

}







