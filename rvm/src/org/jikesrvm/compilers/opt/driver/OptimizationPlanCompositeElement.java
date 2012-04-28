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
package org.jikesrvm.compilers.opt.driver;

import org.jikesrvm.VM;
import org.jikesrvm.compilers.opt.OptOptions;
import org.jikesrvm.compilers.opt.OptimizingCompilerException;
import org.jikesrvm.compilers.opt.ir.IR;

/**
 * An element in the opt compiler's optimzation plan
 * that aggregates together other OptimizationPlan elements.
 *
 * NOTE: Instances of subclasses of this class are
 *       held in OptimizationPlanner.masterPlan
 *       and thus represent global state.
 *       It is therefore incorrect for any per-compilation
 *       state to be stored in an instance field of
 *       one of these objects.
 */
public class OptimizationPlanCompositeElement extends OptimizationPlanElement {
  /**
   * Name of this element.
   */
  private final String myName;
  /**
   * Ordered list of elements that together comprise this element.
   */
  private final OptimizationPlanElement[] myElements;

  /**
   * Compose together the argument elements into a composite element
   * of an optimization plan.
   *
   * @param   n     The name for this phase
   * @param   e     The elements to compose
   */
  public OptimizationPlanCompositeElement(String n, OptimizationPlanElement[] e) {
    myName = n;
    myElements = e;
  }

  /**
   * Compose together the argument elements into a composite element
   * of an optimization plan.
   *
   * @param   n     The name for this phase
   * @param   e     The elements to compose
   */
  public OptimizationPlanCompositeElement(String n, Object[] e) {
    myName = n;
    myElements = new OptimizationPlanElement[e.length];
    for (int i = 0; i < e.length; i++) {
      if (e[i] instanceof OptimizationPlanElement) {
        myElements[i] = (OptimizationPlanElement) (e[i]);
      } else if (e[i] instanceof CompilerPhase) {
        myElements[i] = new OptimizationPlanAtomicElement((CompilerPhase) e[i]);
      } else {
        throw new OptimizingCompilerException("Unsupported plan element " + e[i]);
      }
    }
  }

  @Override
  public void initializeForMeasureCompilation() {
    // initialize each composite object
    for (OptimizationPlanElement myElement : myElements) {
      myElement.initializeForMeasureCompilation();
    }
  }

  /**
   * Compose together the argument elements into a composite element
   * of an optimization plan.
   *
   * @param name The name associated with this composite.
   * @param elems An Object[] of CompilerPhases or
   *              OptimizationPlanElements to be composed
   * @return an OptimizationPlanCompositeElement that
   *         represents the composition.
   */
  public static OptimizationPlanCompositeElement compose(String name, Object[] elems) {
    return new OptimizationPlanCompositeElement(name, elems);
  }

  @Override
  public boolean shouldPerform(OptOptions options) {
    for (OptimizationPlanElement myElement : myElements) {
      if (myElement.shouldPerform(options)) {
        return true;
      }
    }
    return false;
  }

  /**
   * Returns true if the phase wants the IR dumped before and/or after it runs.
   * By default, printing is not enabled.
   * Subclasses should overide this method if they want to provide IR dumping.
   *
   * @param options the compiler options for the compilation
   * @param before true when invoked before perform, false otherwise.
   * @return true if the IR should be printed, false otherwise.
   */
  public boolean printingEnabled(OptOptions options, boolean before) {
    return false;
  }

  @Override
  public final void perform(IR ir) {
    if (printingEnabled(ir.options, true)) {
      if (!ir.options.hasMETHOD_TO_PRINT() || ir.options.fuzzyMatchMETHOD_TO_PRINT(ir.method.toString())) {
        CompilerPhase.dumpIR(ir, "Before " + getName());
      }
    }

    for (OptimizationPlanElement myElement : myElements) {
      if (myElement.shouldPerform(ir.options)) {
        myElement.perform(ir);
      }
    }

    if (printingEnabled(ir.options, false)) {
      if (!ir.options.hasMETHOD_TO_PRINT() || ir.options.fuzzyMatchMETHOD_TO_PRINT(ir.method.toString())) {
        CompilerPhase.dumpIR(ir, "After " + getName());
      }
    }
  }

  @Override
  public String getName() {
    return myName;
  }

  @Override
  public final void reportStats(int indent, int timeCol, double totalTime) {
    double myTime = elapsedTime();
    if (myTime < 0.000001) {
      return;
    }
    // (1) Print header.
    int curCol = 0;
    for (curCol = 0; curCol < indent; curCol++) {
      VM.sysWrite(" ");
    }
    int myNamePtr = 0;
    while (curCol < timeCol && myNamePtr < myName.length()) {
      VM.sysWrite(myName.charAt(myNamePtr));
      myNamePtr++;
      curCol++;
    }
    VM.sysWrite("\n");
    // (2) print elements
    for (OptimizationPlanElement myElement : myElements) {
      myElement.reportStats(indent + 4, timeCol, totalTime);
    }
    // (3) print total
    curCol = 0;
    for (curCol = 0; curCol < indent + 4; curCol++) {
      VM.sysWrite(" ");
    }
    VM.sysWrite("TOTAL ");
    curCol += 6;
    while (curCol < timeCol) {
      VM.sysWrite(" ");
      curCol++;
    }
    prettyPrintTime(myTime, totalTime);
    VM.sysWriteln();
  }

  @Override
  public double elapsedTime() {
    double total = 0.0;
    for (OptimizationPlanElement myElement : myElements) {
      total += myElement.elapsedTime();
    }
    return total;
  }
}
