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
package org.jikesrvm.osr;

import org.jikesrvm.ArchitectureSpecific.BaselineCompilerImpl;
import org.jikesrvm.VM;
import org.jikesrvm.adaptive.controller.ControllerMemory;
import org.jikesrvm.adaptive.controller.ControllerPlan;
import org.jikesrvm.classloader.ExceptionHandlerMap;
import org.jikesrvm.classloader.NormalMethod;
import org.jikesrvm.compilers.common.CompiledMethod;
import org.jikesrvm.compilers.common.RuntimeCompiler;
import org.jikesrvm.compilers.opt.OptOptions;
import org.jikesrvm.compilers.opt.driver.CompilationPlan;
import org.jikesrvm.compilers.opt.driver.OptimizationPlanElement;

/**
 * SpecialCompiler is a wrapper for compiling specialized byte code.
 * It accepts an instance of ExecutionState, generates the specialized
 * byte code, and compiles it to machine code instructions.
 */
public class SpecialCompiler {

  /**
   * recompile an execution state
   * @param state a list of execution states
   * @param invalidate Is this an invalidation?
   * @return the compiled method for the root state
   */
  public static CompiledMethod recompileState(ExecutionState state, boolean invalidate) {

    // compile from callee to caller
    CompiledMethod newCM = null;
    do {
      if (!invalidate) {
        newCM = optCompile(state);
      } else {
        newCM = baselineCompile(state);
      }

      if (VM.TraceOnStackReplacement) {
        VM.sysWriteln("new CMID 0x" + Integer.toHexString(newCM.getId()) + "(" + newCM.getId() + ") for " + newCM.getMethod());
      }

      if (state.callerState == null) break;
      state = state.callerState;
      // set callee_cmid of the caller
      state.callee_cmid = newCM.getId();

    } while (true);

    return newCM;
  }

  /* Compiles the method with the baseline compiler.
  * 1. generate  prologue (PSEUDO_bytecode) from the state.
  * 2. make up new byte code with prologue.
  * 3. set method's bytecode to the specilizaed byte code.
  * 4. call BaselineCompilerImpl.compile,
  *    the 'compile' method is customized to process pseudo instructions,
  *    and it will reset the byte code to the original one, and adjust
  *    the map from bytecode to the generated machine code. then the
  *    reference map can be generated corrected relying on the original
  *    bytecode.
  * NOTE: this is different from optCompile which resets the
  *    bytecode after compilation. I believe this minimizes the
  *    work to change both compilers.
  */
  public static CompiledMethod baselineCompile(ExecutionState state) {
    NormalMethod method = state.getMethod();

    if (VM.TraceOnStackReplacement) {VM.sysWriteln("BASE : starts compiling " + method); }

    /* generate prologue bytes */
    byte[] prologue = state.generatePrologue();

    if (VM.TraceOnStackReplacement) {VM.sysWriteln("prologue length " + prologue.length);}

    // the compiler will call setForOsrSpecialization after generating the reference map
    /* set a flag for specialization, compiler will see it, and
     * know how to do it properly.
     */
    method.setForOsrSpecialization(prologue, state.getMaxStackHeight());

    /* for baseline compilation, we do not adjust the exception table and line table
    * because the compiler will generate maps after compilation.
    * Any necessary adjustment should be made during the compilation
    */
    CompiledMethod newCompiledMethod = BaselineCompilerImpl.compile(method);

    // compiled method was already set by BaselineCompilerImpl.compile
    // the call here does nothing
//    method.finalizeOsrSpecialization();

    // mark the method is a specialized one
    newCompiledMethod.setSpecialForOSR();

    if (VM.TraceOnStackReplacement) {
//        ((BaselineCompiledMethod)newCompiledMethod).printCodeMapEntries();
      VM.sysWriteln("BASE : done, CMID 0x" +
                    Integer.toHexString(newCompiledMethod.getId()) +
                    "(" + newCompiledMethod.getId() + ") JTOC offset " +
                    VM.addressAsHexString(newCompiledMethod.getOsrJTOCoffset().toWord().toAddress()));
    }

    return newCompiledMethod;
  }

  /**
   *     1. generate prologue PSEUDO_bytecode from the state.
   *     2. make new bytecodes with prologue.
   *     3. set method's bytecode to specialized one.
   *     4. adjust exception map, line number map.
   *     5. compile the method.
   *     6. restore bytecode, exception, linenumber map to the original one.
   */
  public static CompiledMethod optCompile(ExecutionState state) {

    NormalMethod method = state.getMethod();
    if (VM.TraceOnStackReplacement) { VM.sysWriteln("OPT : starts compiling " + method); }

    ControllerPlan latestPlan = ControllerMemory.findLatestPlan(method);

    OptOptions _options = null;
    if (latestPlan != null) {
      _options = latestPlan.getCompPlan().options.dup();
    } else {
      // no previous compilation plan, a long run loop promoted from baseline.
      // this only happens when testing, not in real code
      _options = new OptOptions();
      _options.setOptLevel(0);
    }
    // disable OSR points in specialized method
    _options.OSR_GUARDED_INLINING = false;

    CompilationPlan compPlan =
        new CompilationPlan(method,
                                (OptimizationPlanElement[]) RuntimeCompiler.optimizationPlan,
                                null,
                                _options);

    // it is also necessary to recompile the current method
    // without OSR.

    /* generate prologue bytes */
    byte[] prologue = state.generatePrologue();
    int prosize = prologue.length;

    method.setForOsrSpecialization(prologue, state.getMaxStackHeight());

    int[] oldStartPCs = null;
    int[] oldEndPCs = null;
    int[] oldHandlerPCs = null;

    /* adjust exception table. */
    {
      // if (VM.TraceOnStackReplacement) { VM.sysWrite("OPT adjust exception table.\n"); }

      ExceptionHandlerMap exceptionHandlerMap = method.getExceptionHandlerMap();

      if (exceptionHandlerMap != null) {

        oldStartPCs = exceptionHandlerMap.getStartPC();
        oldEndPCs = exceptionHandlerMap.getEndPC();
        oldHandlerPCs = exceptionHandlerMap.getHandlerPC();

        int n = oldStartPCs.length;

        int[] newStartPCs = new int[n];
        System.arraycopy(oldStartPCs, 0, newStartPCs, 0, n);
        exceptionHandlerMap.setStartPC(newStartPCs);

        int[] newEndPCs = new int[n];
        System.arraycopy(oldEndPCs, 0, newEndPCs, 0, n);
        exceptionHandlerMap.setEndPC(newEndPCs);

        int[] newHandlerPCs = new int[n];
        System.arraycopy(oldHandlerPCs, 0, newHandlerPCs, 0, n);
        exceptionHandlerMap.setHandlerPC(newHandlerPCs);

        for (int i = 0; i < n; i++) {
          newStartPCs[i] += prosize;
          newEndPCs[i] += prosize;
          newHandlerPCs[i] += prosize;
        }
      }
    }

    CompiledMethod newCompiledMethod = RuntimeCompiler.recompileWithOptOnStackSpecialization(compPlan);

    // restore original bytecode, exception table, and line number table
    method.finalizeOsrSpecialization();

    {
      ExceptionHandlerMap exceptionHandlerMap = method.getExceptionHandlerMap();

      if (exceptionHandlerMap != null) {
        exceptionHandlerMap.setStartPC(oldStartPCs);
        exceptionHandlerMap.setEndPC(oldEndPCs);
        exceptionHandlerMap.setHandlerPC(oldHandlerPCs);
      }
    }

    // compilation failed because compilation is in progress,
    // reverse back to the baseline
    if (newCompiledMethod == null) {
      if (VM.TraceOnStackReplacement) {
        VM.sysWriteln("OPT : fialed, because compilation in progress, " + "fall back to baseline");
      }
      return baselineCompile(state);
    }

    // mark the method is a specialized one
    newCompiledMethod.setSpecialForOSR();

    if (VM.TraceOnStackReplacement) VM.sysWriteln("OPT : done\n");

    return newCompiledMethod;
  }
}
