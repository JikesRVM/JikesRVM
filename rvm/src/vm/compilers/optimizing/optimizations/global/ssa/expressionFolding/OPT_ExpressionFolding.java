/*
 * (C) Copyright IBM Corp. 2001
 */
//$Id$
package com.ibm.JikesRVM.opt;
import com.ibm.JikesRVM.*;

import com.ibm.JikesRVM.opt.ir.*;
import java.util.Enumeration;
import java.util.HashSet;
import java.util.HashMap;
import java.util.Iterator;
import org.vmmagic.unboxed.Address;

/**
 * This class simplifies expressions in SSA form.
 *
 * @author Stephen Fink
 */
class OPT_ExpressionFolding implements OPT_Operators {
  static final boolean DEBUG = false;

  static final boolean RESTRICT_TO_DEAD_EXPRESSIONS = true;

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
  final public static void perform(OPT_IR ir) {
    
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

    if (RESTRICT_TO_DEAD_EXPRESSIONS) {
      pruneCandidates(candidates);
    }

    boolean didSomething = true;
    while (didSomething) {
      didSomething = false;
      for (Iterator i = candidates.iterator(); i.hasNext(); ) {
        OPT_Register r = (OPT_Register)i.next();
        OPT_Instruction s = r.getFirstDef();
        OPT_Operand val1 = Binary.getVal1(s);
        if (VM.VerifyAssertions) { 
          if (!val1.isRegister()) 
            VM.sysWrite("Expression folding trouble AAA" + s);
          VM._assert(val1.isRegister());
        }
        if (candidates.contains(val1.asRegister().register)) {
          OPT_Instruction def = val1.asRegister().register.getFirstDef();
          OPT_Operand def1 = Binary.getVal1(def);
          if (VM.VerifyAssertions) {
            if (!def1.isRegister()) 
              VM.sysWrite("Expression folding trouble BBB" + def);
            VM._assert(def1.isRegister());
          }
          OPT_Operand def2 = Binary.getVal2(def);
          if (VM.VerifyAssertions) {
            if (!def2.isConstant()) 
              VM.sysWrite("Expression folding trouble CCC" + def);
            VM._assert(def2.isConstant());
          }

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
   * Prune the candidate set; restrict candidates to only allow
   * transformations that result in dead code to be eliminated
   */
  private static void pruneCandidates(HashSet candidates) {
    for (Iterator i = candidates.iterator(); i.hasNext(); ) {
      OPT_Register r = (OPT_Register)i.next();
      OPT_Instruction s = r.getFirstDef();
      OPT_Operand val1 = Binary.getVal1(s);
      OPT_Register v1 = val1.asRegister().register;
      if (candidates.contains(v1)) {
        for (Enumeration uses = OPT_DefUse.uses(v1); uses.hasMoreElements();) {
          OPT_RegisterOperand op = (OPT_RegisterOperand)uses.nextElement();
          OPT_Instruction u = op.instruction;
          if (isCandidateExpression(u) == null) {
            i.remove();
            break;
          }
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
    } else if (s.operator == REF_ADD || s.operator == REF_SUB) {
      return transformForWord(s,def);
    } else {
      return transformForLong(s,def);
    }
  }

  private static int getIntValue(OPT_Operand op) {
    if (op instanceof OPT_NullConstantOperand) //is this still necessary? 
      return 0;
    if (op instanceof OPT_IntConstantOperand)
      return op.asIntConstant().value;
    throw new OPT_OptimizingCompilerException("Cannot getIntValue from this operand " + op);
  }

  private static Address getAddressValue(OPT_Operand op) {
    if (op instanceof OPT_NullConstantOperand) 
      return Address.zero();
    if (op instanceof OPT_AddressConstantOperand)
      return op.asAddressConstant().value; 
    if (op instanceof OPT_IntConstantOperand)
      return Address.fromIntSignExtend(op.asIntConstant().value);
    //-#if RVM_FOR_64_ADDR 
    if (op instanceof OPT_LongConstantOperand)
      return Address.fromLong(op.asLongConstant().value);
    //-#endif
    throw new OPT_OptimizingCompilerException("Cannot getWordValue from this operand " + op);
  }

  private static OPT_AddressConstantOperand addConstantValues(boolean neg1, OPT_Operand op1, boolean neg2, OPT_Operand op2) {
    Address a = getAddressValue(op1);
    if (neg1) a = Address.zero().sub(a.toWord().toOffset()); //negate op1
    if (neg2) a = a.sub(getAddressValue(op2).toWord().toOffset()); //sub op2
    else a = a.add(getAddressValue(op2).toWord().toOffset()); //add op2
    return new OPT_AddressConstantOperand(a);
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
    int c = getIntValue(Binary.getVal2(s));
    if (s.operator == INT_SUB) c = -c;

    // A = B + d
    OPT_RegisterOperand B = Binary.getVal1(def).asRegister();
    int d = getIntValue(Binary.getVal2(def));
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
   * Perform the transfomation on the instruction s = A +/- c
   * where def is the definition of A.
   * @return the new instruction to replace s;
   */
  private static OPT_Instruction transformForWord(OPT_Instruction s, 
                                                  OPT_Instruction def) {
    // s is y = A + c
    OPT_RegisterOperand y = Binary.getResult(s);
    OPT_RegisterOperand A = Binary.getVal1(s).asRegister();

    // A = B + d
    OPT_RegisterOperand B = Binary.getVal1(def).asRegister();

    // rewrite so y = B + (c+d)  
    OPT_AddressConstantOperand val2 = addConstantValues(s.operator == REF_SUB, Binary.getVal2(s), def.operator == REF_SUB, Binary.getVal2(def)); 
    return Binary.create(REF_ADD,y.copyRO(),B.copy(),val2);
  }

  /**
   * Does instruction s compute a register r = candidate expression?
   *
   * @return the computed register, or null 
   */
  private static OPT_Register isCandidateExpression(OPT_Instruction s) {
    if (s.operator == INT_ADD || s.operator == LONG_ADD ||
        s.operator == REF_ADD || s.operator == REF_SUB || 
        s.operator == INT_SUB || s.operator == LONG_SUB ) {
      OPT_Operand val2 = Binary.getVal2(s);
      if (val2.isConstant()) {
        OPT_Operand val1 = Binary.getVal1(s);
        // if val1 is constant too, this should've been constant folded
        // beforehand.  Give up.
        if (val1.isConstant()) return null;

        return Binary.getResult(s).asRegister().register;
      }
    }
    return null;
  }
}
