/*
 * (C) Copyright IBM Corp. 2001
 */
//$Id$
package com.ibm.JikesRVM.opt;

import com.ibm.JikesRVM.*;
import com.ibm.JikesRVM.classloader.*;

import  java.util.*;
import com.ibm.JikesRVM.opt.ir.*;

/**
 * Simple flow-insensitive escape analysis
 *
 * <p> TODO: This would be more effective if formulated as a data-flow
 *       problem, and solved with iteration
 *
 * @author Stephen Fink
 *
 */
class OPT_SimpleEscape extends OPT_CompilerPhase
    implements OPT_Operators {
  private final static boolean DEBUG = false;

  public final boolean shouldPerform (OPT_Options options) {
    return  options.SIMPLE_ESCAPE_IPA;
  }

  public final String getName () {
    return  "Simple Escape Analysis";
  }

  public final boolean printingEnabled (OPT_Options options, boolean before) {
    return  false;
  }

  public void perform (OPT_IR ir) {
    OPT_SimpleEscape analyzer = new OPT_SimpleEscape();
    analyzer.simpleEscapeAnalysis(ir);
  }

  /** 
   * Perform the escape analysis for a method. Returns an
   * object holding the result of the analysis
   *
   * <p> Side effect: updates method summary database to hold
   *                escape analysis result for parameters
   * 
   * @param ir IR for the target method
   */
  public OPT_FI_EscapeSummary simpleEscapeAnalysis (OPT_IR ir) {
    if (DEBUG)
      VM.sysWrite("ENTER Simple Escape Analysis " + ir.method + "\n");
    if (DEBUG)
      ir.printInstructions();
    // create a method summary object for this method
    VM_Method m = ir.method;
    OPT_MethodSummary summ = OPT_SummaryDatabase.findOrCreateMethodSummary(m);
    summ.setInProgress(true);
    OPT_FI_EscapeSummary result = new OPT_FI_EscapeSummary();
    // set up register lists, SSA flags
    OPT_DefUse.computeDU(ir);
    OPT_DefUse.recomputeSSA(ir);
    // pass through registers, and mark escape information
    for (OPT_Register reg = ir.regpool.getFirstSymbolicRegister(); 
        reg != null; reg = reg.getNext()) {
      // skip the following types of registers:
      if (reg.isFloat())
        continue;
      if (reg.isDouble())
        continue;
      if (reg.isFloatingPoint())
        continue;
      if (reg.isLong())
        continue;
      if (reg.isCondition())
        continue;
      if (reg.isValidation())
        continue;
      if (reg.isPhysical())
        continue;
      if (!reg.isSSA())
        continue;
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
    for (OPT_OperandEnumeration e = ir.getParameters();
         e.hasMoreElements();
         numParam++) {
      OPT_Register p = ((OPT_RegisterOperand)e.next()).register;
      if (result.isThreadLocal(p)) {
        summ.setParameterMayEscapeThread(numParam, false);
      } 
      else {
        summ.setParameterMayEscapeThread(numParam, true);
      }
    }
      
    // update the method summary to note whether the return value
    // may escape
    boolean foundEscapingReturn = false;
    for (Enumeration e = enumerateReturnValues(ir); e.hasMoreElements();) {
      OPT_Operand op = (OPT_Operand)e.nextElement();
      if (op == null)
        continue;
      if (op.isRegister()) {
        OPT_Register r = op.asRegister().register;
        if (!result.isThreadLocal(r)) {
          foundEscapingReturn = true;
        }
      }
    }
    if (!foundEscapingReturn)
      summ.setResultMayEscapeThread(false);
    // record that we're done with analysis
    summ.setInProgress(false);
    if (DEBUG)
      VM.sysWrite("LEAVE Simple Escape Analysis " + ir.method + "\n");
    return  result;
  }

  /**
   * This member represents the directions to the optimizing compiler to
   * perform escape analysis on a method, but do <em> not </em> generate
   * code.
   */
  private static OPT_OptimizationPlanElement escapePlan = initEscapePlan();

  /** 
   * Check all appearances of a register, to see if any object pointed
   * to by this register may escape this thread and/or method.
   *
   * @param reg the register to check
   * @param ir the governing IR
   * @return true if it may escape this thread, false otherwise
   */
  private AnalysisResult checkAllAppearances (OPT_Register reg, OPT_IR ir) {
    AnalysisResult result = new AnalysisResult();
    result.threadLocal = true;
    result.methodLocal = true;
    for (OPT_RegisterOperand use = reg.useList; use != null; 
        use = (OPT_RegisterOperand)use.getNext()) {

      if (VM.VerifyAssertions && use.type == null) {
          ir.printInstructions();
          VM._assert(false, "type of " + use + " is null");
      }

      // if the type is primitive, just say it escapes
      // TODO: handle this more cleanly
      if (use.type.isPrimitiveType()) {
        result.threadLocal = false;
        result.methodLocal = false;
        break;
      }
      if (checkEscapesThread(use, ir)) {
        result.threadLocal = false;
      }
      if (checkEscapesMethod(use, ir)) {
        result.methodLocal = false;
      }
    }
    for (OPT_RegisterOperand def = reg.defList; def != null; 
        def = (OPT_RegisterOperand)def.getNext()) {

      if (VM.VerifyAssertions && def.type == null) {
          ir.printInstructions();
          VM._assert(false, "type of " + def + " is null");
      }

      // if the type is primitive, just say it escapes
      // TODO: handle this more cleanly
      if (def.type == null || def.type.isPrimitiveType()) {
        result.threadLocal = false;
        result.methodLocal = false;
        break;
      }
      if (checkEscapesThread(def, ir)) {
        result.threadLocal = false;
      }
      if (checkEscapesMethod(def, ir)) {
        result.methodLocal = false;
      }
    }
    return  result;
  }

  /** 
   * Check a single use, to see if this use may cause the object
   * referenced to escape from this thread.
   *
   * @param use the use to check
   * @param ir the governing IR
   * @return true if it may escape, false otherwise
   */
  private static boolean checkEscapesThread (OPT_RegisterOperand use, 
      OPT_IR ir) {
    OPT_Instruction inst = use.instruction;
    OPT_Register reg = use.register;
    switch (inst.getOpcode()) {
      case INT_ASTORE_opcode:case LONG_ASTORE_opcode:
      case FLOAT_ASTORE_opcode:case DOUBLE_ASTORE_opcode:
      case BYTE_ASTORE_opcode:case SHORT_ASTORE_opcode:
      case REF_ASTORE_opcode:
        // as long as we don't store this operand elsewhere, all
        // is OK
        OPT_Operand value = AStore.getValue(inst);
        if (value != use) {
          return  false;
        } 
        else {
          return  true;
        }
      case GETFIELD_opcode:case GETSTATIC_opcode:
      case INT_ALOAD_opcode:case LONG_ALOAD_opcode:
      case FLOAT_ALOAD_opcode:case DOUBLE_ALOAD_opcode:
      case BYTE_ALOAD_opcode:case UBYTE_ALOAD_opcode:
      case BYTE_LOAD_opcode:case UBYTE_LOAD_opcode:
      case SHORT_ALOAD_opcode:case USHORT_ALOAD_opcode:
      case SHORT_LOAD_opcode:case USHORT_LOAD_opcode:
      case REF_ALOAD_opcode:
      case INT_LOAD_opcode: case LONG_LOAD_opcode:
      case DOUBLE_LOAD_opcode: case REF_LOAD_opcode:
        // all is OK, unless we load this register from memory
        OPT_Operand result = ResultCarrier.getResult(inst);
        if (result != use) {
          return  false;
        } 
        else {
          return  true;
        }
      case PUTFIELD_opcode:
        // as long as we don't store this operand elsewhere, all
        // is OK. TODO: add more smarts.
        value = PutField.getValue(inst);
        if (value != use) {
          return  false;
        } 
        else {
          return  true;
        }
      case PUTSTATIC_opcode:
        // as long as we don't store this operand elsewhere, all
        // is OK. TODO: add more smarts.
        value = PutStatic.getValue(inst);
        if (value != use) {
          return  false;
        } 
        else {
          return  true;
        }
      case BYTE_STORE_opcode: case SHORT_STORE_opcode: case REF_STORE_opcode:
      case INT_STORE_opcode:  case LONG_STORE_opcode: case DOUBLE_STORE_opcode:
        // as long as we don't store this operand elsewhere, all
        // is OK. TODO: add more smarts.
        value = Store.getValue(inst);
        if (value != use) {
          return  false;
        } 
        else {
          return  true;
        }
        // the following instructions never cause an object to
        // escape
      case BOUNDS_CHECK_opcode:case MONITORENTER_opcode:
      case MONITOREXIT_opcode:case NULL_CHECK_opcode:
      case ARRAYLENGTH_opcode:case REF_IFCMP_opcode:
      case INT_IFCMP_opcode:case IG_PATCH_POINT_opcode:
      case IG_CLASS_TEST_opcode:case IG_METHOD_TEST_opcode:
      case BOOLEAN_CMP_opcode:case OBJARRAY_STORE_CHECK_opcode:
      case OBJARRAY_STORE_CHECK_NOTNULL_opcode:
      case GET_OBJ_TIB_opcode:
      case GET_TYPE_FROM_TIB_opcode:case NEW_opcode:case NEWARRAY_opcode:
      case NEWOBJMULTIARRAY_opcode:case NEW_UNRESOLVED_opcode: case NEWARRAY_UNRESOLVED_opcode:
      case INSTANCEOF_opcode:case INSTANCEOF_NOTNULL_opcode:
      case INSTANCEOF_UNRESOLVED_opcode:
      case CHECKCAST_opcode: case MUST_IMPLEMENT_INTERFACE_opcode:
      case CHECKCAST_NOTNULL_opcode: case CHECKCAST_UNRESOLVED_opcode:
      case GET_CAUGHT_EXCEPTION_opcode:
      case IR_PROLOGUE_opcode: 
        return  false;
      case RETURN_opcode:
        // a return instruction might cause an object to escape,
        // but not a parameter (whose escape properties are determined
        // by caller)
        if (ir.isParameter(use))
          return  false; 
        else {
          return  true;
        }
      case CALL_opcode:
        OPT_MethodOperand mop = Call.getMethod(inst);
        if (mop == null)
          return  true;
        if (!mop.hasPreciseTarget()) {
          // if we're not sure of the dynamic target, give up
          return true;
        }
        // try to get a method summary for the called method
        OPT_MethodSummary summ = findOrCreateMethodSummary(mop.getTarget(), ir.options);
        if (summ == null) {
          // couldn't get one. assume the object escapes
          return  true;
        }
        // if use is result of the call...
        if (use == Call.getResult(inst)) {
          if (summ.resultMayEscapeThread()) {
            return  true;
          } 
          else {
            return  false;
          }
        }
        // use is a parameter to the call.  Find out which one.
        int p = getParameterIndex(use, inst);
        if (summ.parameterMayEscapeThread(p)) {
          // the call may make the parameter escape
          return  true;
        } 
        else {
          return  false;
        }
      case REF_MOVE_opcode:case ATHROW_opcode:case PREPARE_opcode:
      case ATTEMPT_opcode: 
      case INT_MOVE_opcode:case INT_ADD_opcode: 
      case INT_MUL_opcode: case INT_DIV_opcode: case INT_REM_opcode:
      case INT_NEG_opcode: case INT_ZERO_CHECK_opcode:                 
      case INT_OR_opcode: case INT_AND_opcode: case INT_XOR_opcode:
      case INT_SUB_opcode:case INT_SHL_opcode:
      case INT_SHR_opcode:case INT_USHR_opcode:case SYSCALL_opcode:
      case GET_CLASS_OBJECT_opcode:case SET_CAUGHT_EXCEPTION_opcode:
      case PHI_opcode: case INT_2LONG_opcode:
      case REF_COND_MOVE_opcode: case INT_COND_MOVE_opcode:
//-#if RVM_FOR_IA32
      case GET_JTOC_opcode: case GET_CURRENT_PROCESSOR_opcode:
//-#endif
        // we don't currently analyze these instructions,
        // so conservatively assume everything escapes
        // TODO: add more smarts
//-#if RVM_WITH_OSR
      case YIELDPOINT_OSR_opcode:
        // on stack replacement really a part of the current method, but
        // we do not know exactly, so be conservative
//-#endif
        return  true;
      default:
        throw  new OPT_OptimizingCompilerException("OPT_SimpleEscape: Unexpected " 
                                                   + inst);
    }
  }

  /** 
   * Check a single use, to see if this use may cause the object
   * referenced to escape from this method.
   *
   * @param use the use to check
   * @param ir the governing IR
   * @return true if it may escape, false otherwise
   */
  private static boolean checkEscapesMethod (OPT_RegisterOperand use, 
      OPT_IR ir) {
    OPT_Instruction inst = use.instruction;
    OPT_Register reg = use.register;
    switch (inst.getOpcode()) {
      case INT_ASTORE_opcode:case LONG_ASTORE_opcode:
      case FLOAT_ASTORE_opcode:case DOUBLE_ASTORE_opcode:
      case BYTE_ASTORE_opcode:case SHORT_ASTORE_opcode:
      case REF_ASTORE_opcode:
        // as long as we don't store this operand elsewhere, all
        // is OK
        OPT_Operand value = AStore.getValue(inst);
        if (value != use) {
          return  false;
        } 
        else {
          return  true;
        }
      case GETFIELD_opcode:case GETSTATIC_opcode:
      case INT_ALOAD_opcode:case LONG_ALOAD_opcode:case FLOAT_ALOAD_opcode:
      case DOUBLE_ALOAD_opcode:case BYTE_ALOAD_opcode:case UBYTE_ALOAD_opcode:
      case BYTE_LOAD_opcode:case UBYTE_LOAD_opcode:
      case USHORT_ALOAD_opcode:case SHORT_ALOAD_opcode:
      case USHORT_LOAD_opcode:case SHORT_LOAD_opcode:case REF_ALOAD_opcode:
      case INT_LOAD_opcode:case LONG_LOAD_opcode:case DOUBLE_LOAD_opcode:case REF_LOAD_opcode:
        // all is OK, unless we load this register from memory
        OPT_Operand result = ResultCarrier.getResult(inst);
        if (result != use) {
          return  false;
        } 
        else {
          return  true;
        }
      case PUTFIELD_opcode:
        // as long as we don't store this operand elsewhere, all
        // is OK. TODO: add more smarts.
        value = PutField.getValue(inst);
        if (value != use) {
          return  false;
        } 
        else {
          return  true;
        }
      case PUTSTATIC_opcode:
        // as long as we don't store this operand elsewhere, all
        // is OK. TODO: add more smarts.
        value = PutStatic.getValue(inst);
        if (value != use) {
          return  false;
        } 
        else {
          return  true;
        }
      case BYTE_STORE_opcode:case SHORT_STORE_opcode: case REF_STORE_opcode:
      case INT_STORE_opcode:case LONG_STORE_opcode: case DOUBLE_STORE_opcode:
        // as long as we don't store this operand elsewhere, all
        // is OK. TODO: add more smarts.
        value = Store.getValue(inst);
        if (value != use) {
          return  false;
        } 
        else {
          return  true;
        }
        // the following instructions never cause an object to
        // escape
      case BOUNDS_CHECK_opcode:case MONITORENTER_opcode:
      case MONITOREXIT_opcode:case NULL_CHECK_opcode:case ARRAYLENGTH_opcode:
      case REF_IFCMP_opcode:case INT_IFCMP_opcode:case IG_PATCH_POINT_opcode:
      case IG_CLASS_TEST_opcode:case IG_METHOD_TEST_opcode:
      case BOOLEAN_CMP_opcode:
      case OBJARRAY_STORE_CHECK_opcode:
      case OBJARRAY_STORE_CHECK_NOTNULL_opcode:
      case GET_OBJ_TIB_opcode:case GET_TYPE_FROM_TIB_opcode:case NEW_opcode:
      case NEWARRAY_opcode:case NEWOBJMULTIARRAY_opcode:
      case NEW_UNRESOLVED_opcode: case NEWARRAY_UNRESOLVED_opcode:
      case INSTANCEOF_opcode:case INSTANCEOF_NOTNULL_opcode:
      case INSTANCEOF_UNRESOLVED_opcode:
      case CHECKCAST_opcode: case MUST_IMPLEMENT_INTERFACE_opcode:
      case CHECKCAST_NOTNULL_opcode: case CHECKCAST_UNRESOLVED_opcode:
      case GET_CAUGHT_EXCEPTION_opcode:
      case IR_PROLOGUE_opcode: 
        return  false;
      case RETURN_opcode:
        // a return instruction causes an object to escape this method.
        return  true;
      case CALL_opcode:
        // a call instruction causes an object to escape this method.
        return  true;
      case REF_MOVE_opcode:case ATHROW_opcode:case PREPARE_opcode:
      case ATTEMPT_opcode: 
      case INT_MOVE_opcode:case INT_ADD_opcode: 
      case INT_MUL_opcode: case INT_DIV_opcode: case INT_REM_opcode:
      case INT_NEG_opcode: case INT_ZERO_CHECK_opcode:                 
      case INT_OR_opcode: case INT_AND_opcode: case INT_XOR_opcode:
      case INT_SUB_opcode:case INT_SHL_opcode:
      case INT_SHR_opcode:case INT_USHR_opcode:case SYSCALL_opcode:
      case GET_CLASS_OBJECT_opcode:case SET_CAUGHT_EXCEPTION_opcode:
      case PHI_opcode: case INT_2LONG_opcode:
      case REF_COND_MOVE_opcode: case INT_COND_MOVE_opcode:
//-#if RVM_FOR_IA32
      case GET_JTOC_opcode: case GET_CURRENT_PROCESSOR_opcode:
//-#endif
//-#if RVM_WITH_OSR
      case YIELDPOINT_OSR_opcode:
//-#endif
        // we don't currently analyze these instructions,
        // so conservatively assume everything escapes
        // TODO: add more smarts
        return  true;
      default:
        throw  new OPT_OptimizingCompilerException("OPT_SimpleEscapge: Unexpected " + inst);
    }
  }

  /**
   * Which parameter to a call instruction corresponds to op?
   * <p> PRECONDITION: Call.conforms(s)
   */
  private static int getParameterIndex (OPT_Operand op, OPT_Instruction s) {
    for (int i = 0; i < Call.getNumberOfParams(s); i++) {
      OPT_Operand p = Call.getParam(s, i);
      if (p == op)
        return  i;
    }
    throw  new OPT_OptimizingCompilerException("Parameter not found" + 
        op + s);
  }

  /**
   * If a method summary exists for a method, get it.
   * Else, iff SIMPLE_ESCAPE_IPA, 
   *   perform escape analysis, which will create the method
   *    summary as a side effect, and return the summary
   */
  private static OPT_MethodSummary findOrCreateMethodSummary (VM_Method m, 
      OPT_Options options) {
    OPT_MethodSummary summ = OPT_SummaryDatabase.findMethodSummary(m);
    if (summ == null) {
      if (options.SIMPLE_ESCAPE_IPA) {
        performSimpleEscapeAnalysis(m, options);
        summ = OPT_SummaryDatabase.findMethodSummary(m);
      }
      return  summ;
    } 
    else {
      return  summ;
    }
  }

  /**
   * Perform the simple escape analysis for a method.
   */
  private static void performSimpleEscapeAnalysis (VM_Method m, 
                                                   OPT_Options options) {
    if (!options.SIMPLE_ESCAPE_IPA)
      return;
    // do not perform for unloaded methods
    VM_Class c = m.getDeclaringClass();
    OPT_MethodSummary summ = OPT_SummaryDatabase.findMethodSummary(m);
    if (summ != null) {
      // do not attempt to perform escape analysis recursively
      if (summ.inProgress())
        return;
    }
    OPT_CompilationPlan plan = 
      new OPT_CompilationPlan((VM_NormalMethod)m, escapePlan, 
                              null, options);
    plan.analyzeOnly = true;
    try {
      OPT_Compiler.compile(plan);
    } catch (OPT_MagicNotImplementedException e) {
      summ.setInProgress(false); // summary stays at bottom
    }
  }

  /**
   * Static initializer: set up the compilation plan for
   * simple escape analysis of a method.
   */
  private static OPT_OptimizationPlanElement initEscapePlan () {
    return  OPT_OptimizationPlanCompositeElement.compose("Escape Analysis", 
        new Object[] {
      new OPT_ConvertBCtoHIR(), new OPT_Simple(true, true), 
      new OPT_SimpleEscape()
    });
  }

  /**
   * Return an enumeration of the operands that serve as return
   * values in an IR
   *
   * <p> TODO: Move this utility elsewhere
   */
  private static Enumeration enumerateReturnValues (OPT_IR ir) {
    Vector v = new Vector();
    for (OPT_InstructionEnumeration e = ir.forwardInstrEnumerator(); 
        e.hasMoreElements();) {
      OPT_Instruction s = e.next();
      if (Return.conforms(s)) {
        v.addElement(Return.getVal(s));
      }
    }
    return  v.elements();
  }

  /**
   * Utility class used to hold the result of the escape analysis.
   */
  private class AnalysisResult {
    /**
     * Was the result "the register must point to thread-local objects"?
     */
    boolean threadLocal;
    /**
     * Was the result "the register must point to method-local objects"?
     */
    boolean methodLocal;
  }
}



