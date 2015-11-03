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
package org.jikesrvm.compilers.opt.ir.operand.ia32;

import static org.jikesrvm.compilers.common.assembler.ia32.AssemblerConstants.CONDITION;
import static org.jikesrvm.compilers.common.assembler.ia32.AssemblerConstants.EQ;
import static org.jikesrvm.compilers.common.assembler.ia32.AssemblerConstants.GE;
import static org.jikesrvm.compilers.common.assembler.ia32.AssemblerConstants.GT;
import static org.jikesrvm.compilers.common.assembler.ia32.AssemblerConstants.LE;
import static org.jikesrvm.compilers.common.assembler.ia32.AssemblerConstants.LGE;
import static org.jikesrvm.compilers.common.assembler.ia32.AssemblerConstants.LGT;
import static org.jikesrvm.compilers.common.assembler.ia32.AssemblerConstants.LLE;
import static org.jikesrvm.compilers.common.assembler.ia32.AssemblerConstants.LLT;
import static org.jikesrvm.compilers.common.assembler.ia32.AssemblerConstants.LT;
import static org.jikesrvm.compilers.common.assembler.ia32.AssemblerConstants.NE;
import static org.jikesrvm.compilers.common.assembler.ia32.AssemblerConstants.NO;
import static org.jikesrvm.compilers.common.assembler.ia32.AssemblerConstants.NS;
import static org.jikesrvm.compilers.common.assembler.ia32.AssemblerConstants.O;
import static org.jikesrvm.compilers.common.assembler.ia32.AssemblerConstants.PE;
import static org.jikesrvm.compilers.common.assembler.ia32.AssemblerConstants.PO;
import static org.jikesrvm.compilers.common.assembler.ia32.AssemblerConstants.S;

import org.jikesrvm.compilers.opt.OptimizingCompilerException;
import org.jikesrvm.compilers.opt.ir.operand.ConditionOperand;
import org.jikesrvm.compilers.opt.ir.operand.Operand;

/**
 * An IA32 condition operand
 */
public final class IA32ConditionOperand extends Operand {

  /**
   * Value of this operand (one of the ConditionCode constants operands
   * defined in AssemblerConstants)
   */
  public byte value;

  @Override
  public Operand copy() {
    return new IA32ConditionOperand(value);
  }

  @Override
  public boolean similar(Operand op) {
    return (op instanceof IA32ConditionOperand) && ((IA32ConditionOperand) op).value == value;
  }

  /**
   * flip the direction of the condition
   * @return this, mutated to flip value
   */
  public IA32ConditionOperand flipCode() {
    switch (value) {
      case O:   value =  NO; break;
      case NO:  value =   O; break;
      case LLT: value = LGE; break;
      case LGE: value = LLT; break;
      case EQ:  value =  NE; break;
      case NE:  value =  EQ; break;
      case LLE: value = LGT; break;
      case LGT: value = LLE; break;
      case S:   value =  NS; break;
      case NS:  value =   S; break;
      case PE:  value =  PO; break;
      case PO:  value =  PE; break;
      case LT:  value =  GE; break;
      case GE:  value =  LT; break;
      case LE:  value =  GT; break;
      case GT:  value =  LE; break;
      default:
        OptimizingCompilerException.UNREACHABLE();
    }
    return this;
  }

  /**
   * change the condition when operands are flipped
   * @return this mutated to change value
   */
  public IA32ConditionOperand flipOperands() {
    switch (value) {
      case LLT: value = LGT; break;
      case LGE: value = LLE; break;
      case LLE: value = LGE; break;
      case LGT: value = LLT; break;
      case LT:  value =  GT; break;
      case GE:  value =  LE; break;
      case LE:  value =  GE; break;
      case GT:  value =  LT; break;
      default:
        OptimizingCompilerException.TODO();
    }
    return this;
  }

  /**
   * Constructs the IA32 Condition Operand that corresponds to the
   * argument ConditionOperand.
   *
   * @param c the template
   */
  public IA32ConditionOperand(ConditionOperand c) {
    translate(c);
  }

  public static IA32ConditionOperand EQ() {
    return new IA32ConditionOperand(EQ);
  }

  public static IA32ConditionOperand NE() {
    return new IA32ConditionOperand(NE);
  }

  public static IA32ConditionOperand LT() {
    return new IA32ConditionOperand(LT);
  }

  public static IA32ConditionOperand LE() {
    return new IA32ConditionOperand(LE);
  }

  public static IA32ConditionOperand GT() {
    return new IA32ConditionOperand(GT);
  }

  public static IA32ConditionOperand GE() {
    return new IA32ConditionOperand(GE);
  }

  public static IA32ConditionOperand O() {
    return new IA32ConditionOperand(O);
  }

  public static IA32ConditionOperand NO() {
    return new IA32ConditionOperand(NO);
  }

  public static IA32ConditionOperand LGT() {
    return new IA32ConditionOperand(LGT);
  }

  public static IA32ConditionOperand LLT() {
    return new IA32ConditionOperand(LLT);
  }

  public static IA32ConditionOperand LGE() {
    return new IA32ConditionOperand(LGE);
  }

  public static IA32ConditionOperand LLE() {
    return new IA32ConditionOperand(LLE);
  }

  public static IA32ConditionOperand PE() {
    return new IA32ConditionOperand(PE);
  }

  public static IA32ConditionOperand PO() {
    return new IA32ConditionOperand(PO);
  }

  private IA32ConditionOperand(byte c) {
    value = c;
  }

  // translate from ConditionOperand: used during LIR => MIR translation
  private void translate(ConditionOperand c) {
    switch (c.value) {
      case ConditionOperand.EQUAL:
        value = EQ;
        break;
      case ConditionOperand.NOT_EQUAL:
        value = NE;
        break;
      case ConditionOperand.LESS:
        value = LT;
        break;
      case ConditionOperand.LESS_EQUAL:
        value = LE;
        break;
      case ConditionOperand.GREATER:
        value = GT;
        break;
      case ConditionOperand.GREATER_EQUAL:
        value = GE;
        break;
      case ConditionOperand.HIGHER:
        value = LGT;
        break;
      case ConditionOperand.LOWER:
      case ConditionOperand.CARRY_FROM_ADD:
      case ConditionOperand.BORROW_FROM_SUB:
      case ConditionOperand.BORROW_FROM_RSUB:
      case ConditionOperand.BIT_TEST:
      case ConditionOperand.RBIT_TEST:
        value = LLT;
        break;
      case ConditionOperand.HIGHER_EQUAL:
      case ConditionOperand.NO_CARRY_FROM_ADD:
      case ConditionOperand.NO_BORROW_FROM_SUB:
      case ConditionOperand.NO_BORROW_FROM_RSUB:
      case ConditionOperand.NO_BIT_TEST:
      case ConditionOperand.NO_RBIT_TEST:
        value = LGE;
        break;
      case ConditionOperand.LOWER_EQUAL:
        value = LLE;
        break;
      case ConditionOperand.OVERFLOW_FROM_ADD:
      case ConditionOperand.OVERFLOW_FROM_SUB:
      case ConditionOperand.OVERFLOW_FROM_RSUB:
      case ConditionOperand.OVERFLOW_FROM_MUL:
        value =   O;
        break;
      case ConditionOperand.NO_OVERFLOW_FROM_ADD:
      case ConditionOperand.NO_OVERFLOW_FROM_SUB:
      case ConditionOperand.NO_OVERFLOW_FROM_RSUB:
      case ConditionOperand.NO_OVERFLOW_FROM_MUL:
        value =  NO;
        break;
      case ConditionOperand.CMPL_EQUAL:
      case ConditionOperand.CMPL_GREATER:
      case ConditionOperand.CMPG_LESS:
      case ConditionOperand.CMPL_GREATER_EQUAL:
      case ConditionOperand.CMPG_LESS_EQUAL:
      case ConditionOperand.CMPL_NOT_EQUAL:
      case ConditionOperand.CMPL_LESS:
      case ConditionOperand.CMPG_GREATER_EQUAL:
      case ConditionOperand.CMPG_GREATER:
      case ConditionOperand.CMPL_LESS_EQUAL:
        throw new Error("IA32ConditionOperand.translate: Complex operand can't be directly translated " + c);
      default:
        OptimizingCompilerException.UNREACHABLE();
    }
  }

  // Returns the string representation of this operand.
  @Override
  public String toString() {
    return CONDITION[value];
  }

}
