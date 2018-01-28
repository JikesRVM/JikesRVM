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
package org.jikesrvm.compilers.opt.escape;

import static org.jikesrvm.compilers.opt.ir.Operators.*;
import static org.jikesrvm.compilers.opt.ir.ia32.ArchOperators.PREFETCH_opcode;
import static org.jikesrvm.compilers.opt.ir.ppc.ArchOperators.DCBST_opcode;
import static org.jikesrvm.compilers.opt.ir.ppc.ArchOperators.DCBTST_opcode;
import static org.jikesrvm.compilers.opt.ir.ppc.ArchOperators.DCBT_opcode;
import static org.jikesrvm.compilers.opt.ir.ppc.ArchOperators.DCBZL_opcode;
import static org.jikesrvm.compilers.opt.ir.ppc.ArchOperators.DCBZ_opcode;
import static org.jikesrvm.compilers.opt.ir.ppc.ArchOperators.ICBI_opcode;

import java.util.ArrayList;
import java.util.Enumeration;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Set;

import org.jikesrvm.VM;
import org.jikesrvm.classloader.NormalMethod;
import org.jikesrvm.classloader.RVMMethod;
import org.jikesrvm.compilers.opt.DefUse;
import org.jikesrvm.compilers.opt.MagicNotImplementedException;
import org.jikesrvm.compilers.opt.OptOptions;
import org.jikesrvm.compilers.opt.OptimizingCompilerException;
import org.jikesrvm.compilers.opt.Simple;
import org.jikesrvm.compilers.opt.bc2ir.ConvertBCtoHIR;
import org.jikesrvm.compilers.opt.driver.CompilationPlan;
import org.jikesrvm.compilers.opt.driver.CompilerPhase;
import org.jikesrvm.compilers.opt.driver.OptimizationPlanCompositeElement;
import org.jikesrvm.compilers.opt.driver.OptimizationPlanElement;
import org.jikesrvm.compilers.opt.driver.OptimizingCompiler;
import org.jikesrvm.compilers.opt.ir.AStore;
import org.jikesrvm.compilers.opt.ir.Call;
import org.jikesrvm.compilers.opt.ir.IR;
import org.jikesrvm.compilers.opt.ir.Instruction;
import org.jikesrvm.compilers.opt.ir.PutField;
import org.jikesrvm.compilers.opt.ir.PutStatic;
import org.jikesrvm.compilers.opt.ir.Register;
import org.jikesrvm.compilers.opt.ir.ResultCarrier;
import org.jikesrvm.compilers.opt.ir.Return;
import org.jikesrvm.compilers.opt.ir.Store;
import org.jikesrvm.compilers.opt.ir.operand.MethodOperand;
import org.jikesrvm.compilers.opt.ir.operand.Operand;
import org.jikesrvm.compilers.opt.ir.operand.RegisterOperand;

/**
 * Simple flow-insensitive intra-procedural escape analysis. Information
 * about other procedures is only used when examining call instructions.
 * <p>
 * NOTE: The analysis is tailored to the optimizations that currently make
 * use of it and <em>NOT</em> suited for general tasks that rely on escape
 * analysis. In particular, the analysis may incorrectly classify uses as
 * thread-local or method-local. This does not cause problems for the implemented
 * optimizations because those will only be performed when the definition of
 * the object in question is contained in the method that's being compiled.
 * <p>
 * TODO list:
 * <ul>
 *   <li>This would be more effective if formulated as a data-flow problem,
 *    and solved with iteration.</li>
 *   <li>Implement a more powerful analysis that it suitable for general use and
 *    add suitable references</li>
 *   <li>Write a testsuite that demonstrates the capabilities of the analysis</li>
 * </ul>
 *
 */
class SimpleEscape extends CompilerPhase {
  /**
   * Return this instance of this phase. This phase contains no
   * per-compilation instance fields.
   * @param ir not used
   * @return this
   */
  @Override
  public CompilerPhase newExecution(IR ir) {
    return this;
  }

  @Override
  public final boolean shouldPerform(OptOptions options) {
    return options.ESCAPE_SIMPLE_IPA;
  }

  @Override
  public final String getName() {
    return "Simple Escape Analysis";
  }

  @Override
  public final boolean printingEnabled(OptOptions options, boolean before) {
    return false;
  }

  @Override
  public void perform(IR ir) {
    SimpleEscape analyzer = new SimpleEscape();
    analyzer.simpleEscapeAnalysis(ir);
  }

  /**
   * Performs the escape analysis for a method.
   *
   * <p> Side effect: updates method summary database to hold
   *                escape analysis result for parameters
   *
   * @param ir IR for the target method
   * @return an object holding the result of the analysis
   */
  public FI_EscapeSummary simpleEscapeAnalysis(IR ir) {
    final boolean DEBUG = false;
    if (DEBUG) {
      VM.sysWriteln("ENTER Simple Escape Analysis " + ir.method);
    }
    if (DEBUG) {
      ir.printInstructions();
    }
    // create a method summary object for this method
    RVMMethod m = ir.method;
    MethodSummary summ = SummaryDatabase.findOrCreateMethodSummary(m);
    summ.setInProgress(true);
    FI_EscapeSummary result = new FI_EscapeSummary();
    // set up register lists, SSA flags
    DefUse.computeDU(ir);
    DefUse.recomputeSSA(ir);
    // pass through registers, and mark escape information
    for (Register reg = ir.regpool.getFirstSymbolicRegister(); reg != null; reg = reg.getNext()) {
      // skip the following types of registers:
      if (reg.isFloatingPoint()) {
        continue;
      }
      if (reg.isInteger()) {
        continue;
      }
      if (reg.isLong()) {
        continue;
      }
      if (reg.isCondition()) {
        continue;
      }
      if (reg.isValidation()) {
        continue;
      }
      if (reg.isPhysical()) {
        continue;
      }
      if (!reg.isSSA()) {
        continue;
      }
      AnalysisResult escapes = checkAllAppearances(reg, ir);
      if (escapes.threadLocal) {
        result.setThreadLocal(reg, true);
      }
      if (escapes.methodLocal) {
        result.setMethodLocal(reg, true);
      }
    }
    // update the method summary database to note whether
    // parameters may escape
    int numParam = 0;
    for (Enumeration<Operand> e = ir.getParameters(); e.hasMoreElements(); numParam++) {
      Register p = ((RegisterOperand) e.nextElement()).getRegister();
      if (result.isThreadLocal(p)) {
        summ.setParameterMayEscapeThread(numParam, false);
      } else {
        summ.setParameterMayEscapeThread(numParam, true);
      }
    }

    // update the method summary to note whether the return value
    // may escape
    boolean foundEscapingReturn = false;
    for (Iterator<Operand> itr = iterateReturnValues(ir); itr.hasNext();) {
      Operand op = itr.next();
      if (op == null) {
        continue;
      }
      if (op.isRegister()) {
        Register r = op.asRegister().getRegister();
        if (!result.isThreadLocal(r)) {
          foundEscapingReturn = true;
        }
      }
    }
    if (!foundEscapingReturn) {
      summ.setResultMayEscapeThread(false);
    }
    // record that we're done with analysis
    summ.setInProgress(false);
    if (DEBUG) {
      VM.sysWriteln("LEAVE Simple Escape Analysis " + ir.method);
    }
    return result;
  }

  /**
   * This member represents the directions to the optimizing compiler to
   * perform escape analysis on a method, but do <em> not </em> generate
   * code.
   */
  private static final OptimizationPlanElement escapePlan = initEscapePlan();

  /**
   * Checks all appearances of a register, to see if any instruction in
   * this method causes the object pointed to by the register to escape
   * this thread and/or method.
   *
   * @param reg the register to check
   * @param ir the governing IR
   * @return {@code true} if it may escape this thread,
   *  {@code false} otherwise
   */
  private static AnalysisResult checkAllAppearances(Register reg, IR ir) {
    return new AnalysisResult(!checkIfUseEscapesThread(reg, ir, null),
        !checkIfUseEscapesMethod(reg, ir, null));
  }
  private static boolean checkIfUseEscapesThread(Register reg, IR ir, Set<Register> visited) {
    for (RegisterOperand use = reg.useList; use != null; use = use.getNext()) {

      assertThatTypeIsNotNull(ir, use);

      // if the type is primitive, just say it escapes
      // TODO: handle this more cleanly
      if (use.getType().isPrimitiveType()) {
        return true;
      }
      if (checkEscapesThread(use, ir, visited)) {
        return true;
      }
    }
    for (RegisterOperand def = reg.defList; def != null; def = def.getNext()) {

      assertThatTypeIsNotNull(ir, def);

      // if the type is primitive, just say it escapes
      // TODO: handle this more cleanly
      if (def.getType() == null || def.getType().isPrimitiveType()) {
        return true;
      }
      if (checkEscapesThread(def, ir, visited)) {
        return true;
      }
    }
    return false;
  }
  private static boolean checkIfUseEscapesMethod(Register reg, IR ir, Set<Register> visited) {
    for (RegisterOperand use = reg.useList; use != null; use = use.getNext()) {
      assertThatTypeIsNotNull(ir, use);

      // if the type is primitive, just say it escapes
      // TODO: handle this more cleanly
      if (use.getType().isPrimitiveType()) {
        return true;
      }
      if (checkEscapesMethod(use, ir, visited)) {
        return true;
      }
    }
    for (RegisterOperand def = reg.defList; def != null; def = def.getNext()) {
      assertThatTypeIsNotNull(ir, def);

      // if the type is primitive, just say it escapes
      // TODO: handle this more cleanly
      if (def.getType() == null || def.getType().isPrimitiveType()) {
        return true;
      }
      if (checkEscapesMethod(def, ir, visited)) {
        return true;
      }
    }
    return false;
  }

  private static void assertThatTypeIsNotNull(IR ir, RegisterOperand useOrDef) {
    if (VM.VerifyAssertions && useOrDef.getType() == null) {
      ir.printInstructions();
      String msg = "type of " + useOrDef + " is null";
      VM._assert(VM.NOT_REACHED, msg);
    }
  }

  /**
   * Checks a single use, to see if this use may cause the object
   * referenced to escape from this thread.
   *
   * @param use the use to check
   * @param ir the governing IR
   * @param visited visited registers
   * @return {@code true} if it may escape, {@code false} otherwise
   */
  private static boolean checkEscapesThread(RegisterOperand use, IR ir, Set<Register> visited) {
    Instruction inst = use.instruction;
    switch (inst.getOpcode()) {
      case INT_ASTORE_opcode:
      case LONG_ASTORE_opcode:
      case FLOAT_ASTORE_opcode:
      case DOUBLE_ASTORE_opcode:
      case BYTE_ASTORE_opcode:
      case SHORT_ASTORE_opcode:
      case REF_ASTORE_opcode:
        // as long as we don't store this operand elsewhere, all
        // is OK
        Operand value = AStore.getValue(inst);
        return value == use;
      case GETFIELD_opcode:
      case GETSTATIC_opcode:
      case INT_ALOAD_opcode:
      case LONG_ALOAD_opcode:
      case FLOAT_ALOAD_opcode:
      case DOUBLE_ALOAD_opcode:
      case BYTE_ALOAD_opcode:
      case UBYTE_ALOAD_opcode:
      case BYTE_LOAD_opcode:
      case UBYTE_LOAD_opcode:
      case SHORT_ALOAD_opcode:
      case USHORT_ALOAD_opcode:
      case SHORT_LOAD_opcode:
      case USHORT_LOAD_opcode:
      case REF_ALOAD_opcode:
      case INT_LOAD_opcode:
      case LONG_LOAD_opcode:
      case FLOAT_LOAD_opcode:
      case DOUBLE_LOAD_opcode:
      case REF_LOAD_opcode:
        // all is OK, unless we load this register from memory
        Operand result = ResultCarrier.getResult(inst);
        return result == use;
      case PUTFIELD_opcode:
        // as long as we don't store this operand elsewhere, all
        // is OK. TODO: add more smarts.
        value = PutField.getValue(inst);
        return value == use;
      case PUTSTATIC_opcode:
        // as long as we don't store this operand elsewhere, all
        // is OK. TODO: add more smarts.
        value = PutStatic.getValue(inst);
        return value == use;
      case BYTE_STORE_opcode:
      case SHORT_STORE_opcode:
      case REF_STORE_opcode:
      case INT_STORE_opcode:
      case LONG_STORE_opcode:
      case FLOAT_STORE_opcode:
      case DOUBLE_STORE_opcode:
        // as long as we don't store this operand elsewhere, all
        // is OK. TODO: add more smarts.
        value = Store.getValue(inst);
        return value == use;
        // the following instructions never cause an object to
        // escape
      case BOUNDS_CHECK_opcode:
      case MONITORENTER_opcode:
      case MONITOREXIT_opcode:
      case NULL_CHECK_opcode:
      case ARRAYLENGTH_opcode:
      case REF_IFCMP_opcode:
      case INT_IFCMP_opcode:
      case IG_PATCH_POINT_opcode:
      case IG_CLASS_TEST_opcode:
      case IG_METHOD_TEST_opcode:
      case BOOLEAN_CMP_INT_opcode:
      case BOOLEAN_CMP_ADDR_opcode:
      case OBJARRAY_STORE_CHECK_opcode:
      case OBJARRAY_STORE_CHECK_NOTNULL_opcode:
      case GET_OBJ_TIB_opcode:
      case GET_TYPE_FROM_TIB_opcode:
      case NEW_opcode:
      case NEWARRAY_opcode:
      case NEWOBJMULTIARRAY_opcode:
      case NEW_UNRESOLVED_opcode:
      case NEWARRAY_UNRESOLVED_opcode:
      case INSTANCEOF_opcode:
      case INSTANCEOF_NOTNULL_opcode:
      case INSTANCEOF_UNRESOLVED_opcode:
      case MUST_IMPLEMENT_INTERFACE_opcode:
      case GET_CAUGHT_EXCEPTION_opcode:
      case IR_PROLOGUE_opcode:
        return false;
      case RETURN_opcode:
        // a return instruction might cause an object to escape,
        // but not a parameter (whose escape properties are determined
        // by caller)
        return !ir.isParameter(use);
      case CALL_opcode:
        MethodOperand mop = Call.getMethod(inst);
        if (mop == null) {
          return true;
        }
        if (!mop.hasPreciseTarget()) {
          // if we're not sure of the dynamic target, give up
          return true;
        }
        // pure methods don't let object escape
        if (mop.getTarget().isPure()) {
          return false;
        }
        // Assume non-annotated native methods let object escape
        if (mop.getTarget().isNative()) {
          return true;
        }
        // try to get a method summary for the called method
        MethodSummary summ = getMethodSummaryIfAvailable(mop.getTarget(), ir.options);
        if (summ == null) {
          // couldn't get one. assume the object escapes
          return true;
        }
        // if use is result of the call...
        if (use == Call.getResult(inst)) {
          return summ.resultMayEscapeThread();
        }
        // use is a parameter to the call.  Find out which one.
        int p = getParameterIndex(use, inst);
        return summ.parameterMayEscapeThread(p);
      case CHECKCAST_opcode:
      case CHECKCAST_NOTNULL_opcode:
      case CHECKCAST_UNRESOLVED_opcode:
      case REF_MOVE_opcode: {
        Register copy = ResultCarrier.getResult(inst).getRegister();
        if (!copy.isSSA()) {
          return true;
        } else {
          if (visited == null) {
            visited = new HashSet<Register>();
          }
          visited.add(use.getRegister());
          if (visited.contains(copy)) {
            return false;
          } else {
            return checkIfUseEscapesThread(copy, ir, visited);
          }
        }
      }
      case ATHROW_opcode:
      case PREPARE_INT_opcode:
      case PREPARE_ADDR_opcode:
      case PREPARE_LONG_opcode:
      case ATTEMPT_LONG_opcode:
      case ATTEMPT_INT_opcode:
      case ATTEMPT_ADDR_opcode:
      case INT_MOVE_opcode:
      case INT_ADD_opcode:
      case REF_ADD_opcode:
      case INT_MUL_opcode:
      case INT_DIV_opcode:
      case INT_REM_opcode:
      case INT_NEG_opcode:
      case INT_ZERO_CHECK_opcode:
      case INT_OR_opcode:
      case INT_AND_opcode:
      case INT_XOR_opcode:
      case REF_OR_opcode:
      case REF_AND_opcode:
      case REF_XOR_opcode:
      case INT_SUB_opcode:
      case REF_SUB_opcode:
      case INT_SHL_opcode:
      case INT_SHR_opcode:
      case INT_USHR_opcode:
      case SYSCALL_opcode:
      case REF_SHL_opcode:
      case REF_SHR_opcode:
      case REF_USHR_opcode:
      case SET_CAUGHT_EXCEPTION_opcode:
      case PHI_opcode:
      case INT_2LONG_opcode:
      case REF_COND_MOVE_opcode:
      case INT_COND_MOVE_opcode:
      case INT_2ADDRSigExt_opcode:
      case INT_2ADDRZerExt_opcode:
      case ADDR_2INT_opcode:
      case ADDR_2LONG_opcode:
      case LONG_OR_opcode:
      case LONG_AND_opcode:
      case LONG_XOR_opcode:
      case LONG_SUB_opcode:
      case LONG_SHL_opcode:
      case LONG_ADD_opcode:
      case LONG_SHR_opcode:
      case LONG_USHR_opcode:
      case LONG_NEG_opcode:
      case LONG_MOVE_opcode:
      case LONG_2ADDR_opcode:
        // we don't currently analyze these instructions,
        // so conservatively assume everything escapes
        // TODO: add more smarts
      case YIELDPOINT_OSR_opcode:
        // on stack replacement really a part of the current method, but
        // we do not know exactly, so be conservative
        return true;
      default:
        if (VM.BuildForPowerPC) {
          switch (inst.getOpcode()) {
            case DCBST_opcode:
            case DCBT_opcode:
            case DCBTST_opcode:
            case DCBZ_opcode:
            case DCBZL_opcode:
            case ICBI_opcode:
              return false;
            }
        } else {
          switch (inst.getOpcode()) {
            case PREFETCH_opcode:
              return false;
          }
        }
        throw new OptimizingCompilerException("SimpleEscapge: Unexpected " + inst);
    }
  }

  /**
   * Checks a single use, to see if this use may cause the object
   * referenced to escape from this method.
   *
   * @param use the use to check
   * @param ir the governing IR
   * @param visited visited registers
   * @return {@code true} if it may escape, {@code false} otherwise
   */
  private static boolean checkEscapesMethod(RegisterOperand use, IR ir, Set<Register> visited) {
    Instruction inst = use.instruction;
    try {
      switch (inst.getOpcode()) {
      case INT_ASTORE_opcode:
      case LONG_ASTORE_opcode:
      case FLOAT_ASTORE_opcode:
      case DOUBLE_ASTORE_opcode:
      case BYTE_ASTORE_opcode:
      case SHORT_ASTORE_opcode:
      case REF_ASTORE_opcode:
        // as long as we don't store this operand elsewhere, all
        // is OK
        Operand value = AStore.getValue(inst);
        return value == use;
      case GETFIELD_opcode:
      case GETSTATIC_opcode:
      case INT_ALOAD_opcode:
      case LONG_ALOAD_opcode:
      case FLOAT_ALOAD_opcode:
      case DOUBLE_ALOAD_opcode:
      case BYTE_ALOAD_opcode:
      case UBYTE_ALOAD_opcode:
      case BYTE_LOAD_opcode:
      case UBYTE_LOAD_opcode:
      case USHORT_ALOAD_opcode:
      case SHORT_ALOAD_opcode:
      case USHORT_LOAD_opcode:
      case SHORT_LOAD_opcode:
      case REF_ALOAD_opcode:
      case INT_LOAD_opcode:
      case LONG_LOAD_opcode:
      case FLOAT_LOAD_opcode:
      case DOUBLE_LOAD_opcode:
      case REF_LOAD_opcode:
        // all is OK, unless we load this register from memory
        Operand result = ResultCarrier.getResult(inst);
        return result == use;
      case PUTFIELD_opcode:
        // as long as we don't store this operand elsewhere, all
        // is OK. TODO: add more smarts.
        value = PutField.getValue(inst);
        return value == use;
      case PUTSTATIC_opcode:
        // as long as we don't store this operand elsewhere, all
        // is OK. TODO: add more smarts.
        value = PutStatic.getValue(inst);
        return value == use;
      case BYTE_STORE_opcode:
      case SHORT_STORE_opcode:
      case REF_STORE_opcode:
      case INT_STORE_opcode:
      case LONG_STORE_opcode:
      case FLOAT_STORE_opcode:
      case DOUBLE_STORE_opcode:
        // as long as we don't store this operand elsewhere, all
        // is OK. TODO: add more smarts.
        value = Store.getValue(inst);
        return value == use;
        // the following instructions never cause an object to
        // escape
      case BOUNDS_CHECK_opcode:
      case MONITORENTER_opcode:
      case MONITOREXIT_opcode:
      case NULL_CHECK_opcode:
      case ARRAYLENGTH_opcode:
      case REF_IFCMP_opcode:
      case INT_IFCMP_opcode:
      case IG_PATCH_POINT_opcode:
      case IG_CLASS_TEST_opcode:
      case IG_METHOD_TEST_opcode:
      case BOOLEAN_CMP_INT_opcode:
      case BOOLEAN_CMP_ADDR_opcode:
      case OBJARRAY_STORE_CHECK_opcode:
      case OBJARRAY_STORE_CHECK_NOTNULL_opcode:
      case GET_OBJ_TIB_opcode:
      case GET_TYPE_FROM_TIB_opcode:
      case NEW_opcode:
      case NEWARRAY_opcode:
      case NEWOBJMULTIARRAY_opcode:
      case NEW_UNRESOLVED_opcode:
      case NEWARRAY_UNRESOLVED_opcode:
      case INSTANCEOF_opcode:
      case INSTANCEOF_NOTNULL_opcode:
      case INSTANCEOF_UNRESOLVED_opcode:
      case MUST_IMPLEMENT_INTERFACE_opcode:
      case GET_CAUGHT_EXCEPTION_opcode:
      case IR_PROLOGUE_opcode:
        return false;
      case RETURN_opcode:
        // a return instruction causes an object to escape this method.
        return true;
      case CALL_opcode: {
        // A call instruction causes an object to escape this method.
        // However, when the target of the call is a no-argument Throwable
        // constructor, we know the constructor would only fill in the stack trace.
        // If the stack trace is unnecessary, we can replace the call to the Throwable
        // constructor.
        MethodOperand mop = Call.getMethod(inst);
        if (mop != null && mop.hasPreciseTarget()) {
          RVMMethod target = mop.getTarget();
          boolean isThrowableConstructor = target.getDeclaringClass().isJavaLangThrowableType() &&
              target.isObjectInitializer();
          boolean isNoArgThrowableConstructor = isThrowableConstructor &&
              target.getParameterTypes().length == 0;
          if (isNoArgThrowableConstructor) {
            return false;
          }
        }
        return true;
      }
      case CHECKCAST_opcode:
      case CHECKCAST_NOTNULL_opcode:
      case CHECKCAST_UNRESOLVED_opcode:
      case REF_MOVE_opcode: {
        if (visited == null) {
          visited = new HashSet<Register>();
        }
        Register copy = ResultCarrier.getResult(inst).getRegister();
        if (!copy.isSSA()) {
          return true;
        } else {
          visited.add(use.getRegister());
          if (visited.contains(copy)) {
            return false;
          } else {
            boolean result2 = checkIfUseEscapesMethod(copy, ir, visited);
            return result2;
          }
        }
      }
      case ATHROW_opcode:
      case PREPARE_INT_opcode:
      case PREPARE_ADDR_opcode:
      case ATTEMPT_INT_opcode:
      case ATTEMPT_ADDR_opcode:
      case PREPARE_LONG_opcode:
      case ATTEMPT_LONG_opcode:
      case INT_MOVE_opcode:
      case INT_ADD_opcode:
      case REF_ADD_opcode:
      case INT_MUL_opcode:
      case INT_DIV_opcode:
      case INT_REM_opcode:
      case INT_NEG_opcode:
      case INT_ZERO_CHECK_opcode:
      case INT_OR_opcode:
      case INT_AND_opcode:
      case INT_XOR_opcode:
      case REF_OR_opcode:
      case REF_AND_opcode:
      case REF_XOR_opcode:
      case INT_SUB_opcode:
      case REF_SUB_opcode:
      case INT_SHL_opcode:
      case INT_SHR_opcode:
      case INT_USHR_opcode:
      case SYSCALL_opcode:
      case REF_SHL_opcode:
      case REF_SHR_opcode:
      case REF_USHR_opcode:
      case SET_CAUGHT_EXCEPTION_opcode:
      case PHI_opcode:
      case INT_2LONG_opcode:
      case REF_COND_MOVE_opcode:
      case INT_COND_MOVE_opcode:
      case INT_2ADDRSigExt_opcode:
      case INT_2ADDRZerExt_opcode:
      case ADDR_2INT_opcode:
      case ADDR_2LONG_opcode:
      case LONG_OR_opcode:
      case LONG_AND_opcode:
      case LONG_XOR_opcode:
      case LONG_SUB_opcode:
      case LONG_SHL_opcode:
      case LONG_ADD_opcode:
      case LONG_SHR_opcode:
      case LONG_USHR_opcode:
      case LONG_NEG_opcode:
      case LONG_MOVE_opcode:
      case LONG_2ADDR_opcode:
      case YIELDPOINT_OSR_opcode:
        // we don't currently analyze these instructions,
        // so conservatively assume everything escapes
        // TODO: add more smarts
        return true;
      default:
        if (VM.BuildForPowerPC) {
          switch(inst.getOpcode()) {
            case DCBST_opcode:
            case DCBT_opcode:
            case DCBTST_opcode:
            case DCBZ_opcode:
            case DCBZL_opcode:
            case ICBI_opcode:
              return false;
            }
        } else {
          switch(inst.getOpcode()) {
            case PREFETCH_opcode:
              return false;
          }
        }
        throw new OptimizingCompilerException("SimpleEscapge: Unexpected " + inst);
      }
    } catch (Exception e) {
      OptimizingCompilerException oe = new OptimizingCompilerException("Error handling use (" + use + ") of: " + inst);
      oe.initCause(e);
      throw oe;
    }
  }

  /**
   * Which parameter to a call instruction corresponds to op?
   * <p> PRECONDITION: Call.conforms(s)
   *
   * @param op the operand whose parameter is sought
   * @param s the call instruction
   * @return the index in the instruction for the parameter that matches
   *  the operand
   */
  private static int getParameterIndex(Operand op, Instruction s) {
    for (int i = 0; i < Call.getNumberOfParams(s); i++) {
      Operand p = Call.getParam(s, i);
      if (p == op) {
        return i;
      }
    }
    throw new OptimizingCompilerException("Parameter not found" + op + s);
  }

  /**
   * Returns a method summary if present.
   * <p>
   * In the special case of enabled eager method summary computation,
   * this method will perform escape analysis for the requested method,
   * which will create the method summary as a side effect.
   *
   * @param m the method whose summary is sought
   * @param options options to determine whether to create a summary
   *  if it does not exist
   * @return a method summary or {@code null}.
   */
  private static MethodSummary getMethodSummaryIfAvailable(RVMMethod m, OptOptions options) {
    MethodSummary summ = SummaryDatabase.findMethodSummary(m);
    if (summ == null) {
      if (options.ESCAPE_SIMPLE_IPA) {
        performSimpleEscapeAnalysis(m, options);
        summ = SummaryDatabase.findMethodSummary(m);
      }
      return summ;
    } else {
      return summ;
    }
  }

  private static void performSimpleEscapeAnalysis(RVMMethod m, OptOptions options) {
    if (!options.ESCAPE_SIMPLE_IPA) {
      return;
    }
    // do not perform for unloaded methods
    MethodSummary summ = SummaryDatabase.findMethodSummary(m);
    if (summ != null) {
      // do not attempt to perform escape analysis recursively
      if (summ.inProgress()) {
        return;
      }
    }
    CompilationPlan plan = new CompilationPlan((NormalMethod) m, escapePlan, null, options);
    plan.analyzeOnly = true;
    try {
      OptimizingCompiler.compile(plan);
    } catch (MagicNotImplementedException e) {
      summ.setInProgress(false); // summary stays at bottom
    }
  }

  private static OptimizationPlanElement initEscapePlan() {
    return OptimizationPlanCompositeElement.compose("Escape Analysis",
                                                        new Object[]{new ConvertBCtoHIR(),
                                                                     new Simple(1, true, true, false, false),
                                                                     new SimpleEscape()});
  }

  /**
   * TODO: Move this utility elsewhere
   *
   * @param ir the IR to search for the return values
   * @return an iterator over the operands that serve as return values
   * in an IR
   */
  private static Iterator<Operand> iterateReturnValues(IR ir) {
    ArrayList<Operand> returnValues = new ArrayList<Operand>();
    for (Enumeration<Instruction> e = ir.forwardInstrEnumerator(); e.hasMoreElements();) {
      Instruction s = e.nextElement();
      if (Return.conforms(s)) {
        returnValues.add(Return.getVal(s));
      }
    }
    return returnValues.iterator();
  }

  /**
   * Utility class used to hold the result of the escape analysis.
   */
  private static final class AnalysisResult {
    /**
     * Was the result "the register must point to thread-local objects"?
     */
    final boolean threadLocal;
    /**
     * Was the result "the register must point to method-local objects"?
     */
    final boolean methodLocal;

    AnalysisResult(boolean tl, boolean ml) {
      threadLocal = tl;
      methodLocal = ml;
    }
  }
}
