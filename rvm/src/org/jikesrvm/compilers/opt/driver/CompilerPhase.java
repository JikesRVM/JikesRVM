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

import java.lang.reflect.Constructor;

import org.jikesrvm.VM;
import org.jikesrvm.compilers.opt.OptOptions;
import org.jikesrvm.compilers.opt.OptimizingCompilerException;
import org.jikesrvm.compilers.opt.ir.IR;

/**
 * Compiler phases all extend this abstract class.
 * All compiler phases must provide implementations of
 * two abstract methods:
 * <ul>
 *  <li> getName:  return a String that is the name of the phase
 *  <li> perform:  actually do the work of the phase
 * </ul>
 *
 * <p> By default, a new instance of the phase is created each time
 * shouldPerform is called.  This instance is discarded as soon
 * as shouldPerform completes. Therefore, it is allowable
 * (and is suggested when necessary) for subclasses
 * to use their instance fields to hold per-compilation state.
 * To be more concrete, the pattern of use is:
 * <pre>
 *  newExecution(ir).performPhase(ir).
 * </pre>
 * @see OptimizationPlanAtomicElement#perform
 *
 * <p> NOTE: compiler phases that do not need to use instance
 * fields to hold per-compilation state may override
 * <code> newExecution() </code> to return this.  Doing so may lead to
 * memory leaks and concurrent access problems, so this should be done
 * with great care!
 */
public abstract class CompilerPhase {

  /**
   * The plan element that contains this phase.
   * Only useful if the phase wants to gather additional statistics
   * for a measure compilation report.
   */
  protected OptimizationPlanAtomicElement container;

  /**
   * Arguments to constructor that copies this phase
   */
  private final Object[] initargs;

  /**
   * Constructor
   */
  public CompilerPhase() {
    initargs = null;
  }

  /**
   * Constructor
   *
   * @param initargs arguments used when constructing copies of this phase
   */
  public CompilerPhase(Object[] initargs) {
    this.initargs = initargs;
  }

  /**
   * @return a String which is the name of the phase.
   */
  public abstract String getName();

  /**
   * This is the method that actually does the work of the phase.
   *
   * @param ir the IR on which to apply the phase
   */
  public abstract void perform(IR ir);

  /**
   * This method determines if the phase should be run, based on the
   * Options object it is passed.
   * By default, phases are always performed.
   * Subclasses should override this method if they only want
   * to be performed conditionally.
   *
   * @param options the compiler options for the compilation
   * @return true if the phase should be performed
   */
  public boolean shouldPerform(OptOptions options) {
    return true;
  }

  /**
   * Returns true if the phase wants the IR dumped before and/or after it runs.
   * By default, printing is not enabled.
   * Subclasses should override this method if they want to provide IR dumping.
   *
   * @param options the compiler options for the compilation
   * @param before true when invoked before perform, false otherwise.
   * @return true if the IR should be printed, false otherwise.
   */
  public boolean printingEnabled(OptOptions options, boolean before) {
    return false;
  }

  /**
   * Called when printing a measure compilation report to enable a phase
   * to report additional phase-specific statistics.
   */
  public void reportAdditionalStats() {}

  /**
   * This method is called immediately before performPhase. Phases
   * that do not need to create a new instance for each execution may
   * override this method to return this, but this must be done
   * carefully! Classes that don't override this method need to
   * override getClassConstructor.
   *
   * @param ir the IR that is about to be passed to performPhase
   * @return an opt compiler phase on which performPhase may be invoked.
   */
  public CompilerPhase newExecution(IR ir) {
    Constructor<CompilerPhase> cons = getClassConstructor();
    if (cons != null) {
      try {
        return (CompilerPhase) cons.newInstance(initargs);
      } catch (Exception e) {
        throw new Error("Failed to create phase " + this.getClass() + " with constructor " + cons, e);
      }
    } else {
      throw new Error("Error, no constructor found in phase " +
                      this.getClass() +
                      " make sure a public constructor is declared");
    }
  }

  /**
   * Get a constructor object for this compiler phase
   *
   * @return exception/null as this phase can't be created
   */
  public Constructor<CompilerPhase> getClassConstructor() {
    OptimizingCompilerException.UNREACHABLE();
    return null;
  }

  /**
   * Given the name of a compiler phase return the default (no
   * argument) constructor for it.
   */
  protected static Constructor<CompilerPhase> getCompilerPhaseConstructor(Class<? extends CompilerPhase> klass) {
    return getCompilerPhaseConstructor(klass, null);
  }

  /**
   * Given the name of a compiler phase return the default (no
   * argument) constructor for it.
   */
  protected static Constructor<CompilerPhase> getCompilerPhaseConstructor(Class<? extends CompilerPhase> phaseType,
                                                                              Class<?>[] initTypes) {
    try {
      @SuppressWarnings("unchecked") // We are explicitly breaking type safety
          Constructor<CompilerPhase> constructor =
          (Constructor<CompilerPhase>) phaseType.getConstructor(initTypes);
      return constructor;
    } catch (NoSuchMethodException e) {
      throw new Error("Constructor not found in " + phaseType.getName() + " compiler phase", e);
    }
  }

  /**
   * Set the containing optimization plan element for this phase
   */
  public final void setContainer(OptimizationPlanAtomicElement atomEl) {
    container = atomEl;
  }

  /**
   * Runs a phase by calling perform on the supplied IR surrounded by
   * printing/messaging/debugging glue.
   * @param ir the IR object on which to do the work of the phase.
   */
  public final void performPhase(IR ir) {
    if (printingEnabled(ir.options, true)) {
      if (!ir.options.hasMETHOD_TO_PRINT() || ir.options.fuzzyMatchMETHOD_TO_PRINT(ir.method.toString())) {
        // only print above certain opt level.
        //if (ir.options.getOptLevel() >= ir.options.IR_PRINT_LEVEL) {
        dumpIR(ir, "Before " + getName());
        //}
      }
    }
    if (ir.options.PRINT_PHASES) VM.sysWrite(getName() + " (" + ir.method.toString()+ ")");

    perform(ir);                // DOIT!!

    if (ir.options.PRINT_PHASES) VM.sysWrite(" done\n");
    if (ir.options.PRINT_ALL_IR || printingEnabled(ir.options, false)) {
      if (!ir.options.hasMETHOD_TO_PRINT() || ir.options.fuzzyMatchMETHOD_TO_PRINT(ir.method.toString())) {
        // only print when above certain opt level
        if (ir.options.getOptLevel() >= ir.options.PRINT_IR_LEVEL) {
          dumpIR(ir, "After " + getName());
        }
      }
    }

    if (IR.PARANOID) verify(ir);
  }

  /**
   * Prints the IR, optionally including the CFG
   *
   * @param ir the IR to print
   * @param tag a String to use in the start/end message of the IR dump
   */
  public static void dumpIR(IR ir, String tag) {
    dumpIR(ir, tag, false);
  }

  /**
   * Prints the IR, optionally including the CFG
   *
   * @param ir the IR to print
   * @param forceCFG should the CFG be printed, independent of the value of ir.options.PRINT_CFG?
   * @param tag a String to use in the start/end message of the IR dump
   */
  public static void dumpIR(IR ir, String tag, boolean forceCFG) {
    System.out.println("********* START OF IR DUMP  " + tag + "   FOR " + ir.method);
    ir.printInstructions();
    if (forceCFG || ir.options.PRINT_CFG) {
      ir.cfg.printDepthFirst();
    }
    System.out.println("*********   END OF IR DUMP  " + tag + "   FOR " + ir.method);
  }

  /**
   * Verify the IR.
   * Written as a non-final virtual method to allow late stages in the
   * compilation pipeline (eg ConvertMIR2MC) to skip verification.
   *
   * @param ir the IR to verify
   */
  public void verify(IR ir) {
    ir.verify(getName(), true);
  }
}
