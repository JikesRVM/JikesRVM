/*
 * (C) Copyright IBM Corp. 2001
 */
//$Id$

import instructionFormats.*;
import java.util.Enumeration;
import java.util.HashSet;
import java.util.HashMap;
import java.util.Iterator;

/**
 * This class simplifies expressions in SSA form.
 *
 * @author Stephen Fink
 */
class OPT_ExpressionFolding implements OPT_Operators {
  static final boolean DEBUG = false;

  /** 
   * Perform the transformation.
   *
   * If we have, in SSA form,
   * <pre>
   *    x = a + c1
   *    y = x + c2
   * </pre>
   * where c1 and c2 are constants, replace the def of y by
   * <pre>
   * y = a + (c1+c2)
   * </pre>
   * Perform a similar transformation for subtraction.
   *
   * <p> PRECONDITIONS: SSA form, register lists computed
   *                    
   * @param ir the governing IR
   */
  final public static void perform (OPT_IR ir) {

    // Create a set of potential computations to fold.
    HashSet candidates = new HashSet(20);

    for (Enumeration e = ir.forwardInstrEnumerator(); e.hasMoreElements();) {
      OPT_Instruction s = (OPT_Instruction)e.nextElement();
      // Check if s is a fixed-point add/subtract instruction with 
      // a constant second operand
      OPT_Register r = isCandidateExpression(s);
      if (r != null) {
        candidates.add(r);
      }
    }

    boolean didSomething = true;
    while (didSomething) {
      didSomething = false;
      for (Iterator i = candidates.iterator(); i.hasNext(); ) {
        OPT_Register r = (OPT_Register)i.next();
        OPT_Instruction s = r.getFirstDef();
        OPT_Operand val1 = Binary.getVal1(s);
        if (VM.VerifyAssertions) VM.assert(val1.isRegister());
        if (candidates.contains(val1.asRegister().register)) {
          OPT_Instruction def = val1.asRegister().register.getFirstDef();
          OPT_Operand def1 = Binary.getVal1(def);
          if (VM.VerifyAssertions) VM.assert(def1.isRegister());
          OPT_Operand def2 = Binary.getVal2(def);
          if (VM.VerifyAssertions) VM.assert(def2.isConstant());

          OPT_Instruction newS = transform(s,def);
          s.insertAfter(newS);
          OPT_DefUse.updateDUForNewInstruction(newS);
          OPT_DefUse.removeInstructionAndUpdateDU(s);
          didSomething = true;
        }
      }
    }
  }      

  /**
   * Perform the transfomation on the instruction s = A +/- c
   * where def is the definition of A.
   *
   * @return the new instruction to replace s;
   */
  private static OPT_Instruction transform(OPT_Instruction s, 
                                           OPT_Instruction def) {
    if (s.operator == INT_ADD || s.operator == INT_SUB) {
      return transformForInt(s,def);
    } else {
      return transformForLong(s,def);
    }
  }
  /**
   * Perform the transfomation on the instruction s = A +/- c
   * where def is the definition of A.
   * @return the new instruction to replace s;
   */
  private static OPT_Instruction transformForInt(OPT_Instruction s, 
                                                 OPT_Instruction def) {
    // s is y = A + c
    OPT_RegisterOperand y = Binary.getResult(s);
    OPT_RegisterOperand A = Binary.getVal1(s).asRegister();
    int c = Binary.getVal2(s).asIntConstant().value;
    if (s.operator == INT_SUB) c = -c;

    // A = B + d
    OPT_RegisterOperand B = Binary.getVal1(def).asRegister();
    int d = Binary.getVal2(def).asIntConstant().value;
    if (def.operator == INT_SUB) d = -d;

    // rewrite so y = B + (c+d)  
    OPT_IntConstantOperand val2 = new OPT_IntConstantOperand(c+d);
    return Binary.create(INT_ADD,y.copyRO(),B.copy(),val2);
  }

  /**
   * Perform the transfomation on the instruction s = A +/- c
   * where def is the definition of A.
   * @return the new instruction to replace s;
   */
  private static OPT_Instruction transformForLong(OPT_Instruction s, 
                                                  OPT_Instruction def) {
    // s is y = A + c
    OPT_RegisterOperand y = Binary.getResult(s);
    OPT_RegisterOperand A = Binary.getVal1(s).asRegister();
    long c = Binary.getVal2(s).asLongConstant().value;
    if (s.operator == LONG_SUB) c = -c;

    // A = B + d
    OPT_RegisterOperand B = Binary.getVal1(def).asRegister();
    long d = Binary.getVal2(def).asLongConstant().value;
    if (def.operator == LONG_SUB) d = -d;

    // rewrite so y = B + (c+d)  
    OPT_LongConstantOperand val2 = new OPT_LongConstantOperand(c+d);
    return Binary.create(LONG_ADD,y.copyRO(),B.copy(),val2);
  }

  /**
   * Does instruction s compute a register r = candidate expression?
   *
   * @return the computed register, or null 
   */
  private static OPT_Register isCandidateExpression(OPT_Instruction s) {
    if (s.operator == INT_ADD || s.operator == LONG_ADD ||
        s.operator == INT_SUB || s.operator == LONG_SUB ) {
      OPT_Operand val2 = Binary.getVal2(s);
      if (val2.isConstant()) {
        return Binary.getResult(s).asRegister().register;
      }
    }
    return null;
  }
}
