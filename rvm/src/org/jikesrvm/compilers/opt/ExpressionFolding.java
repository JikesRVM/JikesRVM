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
package org.jikesrvm.compilers.opt;

import static org.jikesrvm.compilers.opt.ir.Operators.*;

import java.util.Enumeration;
import java.util.HashSet;
import java.util.Iterator;

import org.jikesrvm.VM;
import org.jikesrvm.classloader.TypeReference;
import org.jikesrvm.compilers.opt.ir.Binary;
import org.jikesrvm.compilers.opt.ir.BooleanCmp;
import org.jikesrvm.compilers.opt.ir.BoundsCheck;
import org.jikesrvm.compilers.opt.ir.CondMove;
import org.jikesrvm.compilers.opt.ir.GuardedBinary;
import org.jikesrvm.compilers.opt.ir.GuardedUnary;
import org.jikesrvm.compilers.opt.ir.IR;
import org.jikesrvm.compilers.opt.ir.IRTools;
import org.jikesrvm.compilers.opt.ir.IfCmp;
import org.jikesrvm.compilers.opt.ir.IfCmp2;
import org.jikesrvm.compilers.opt.ir.InstanceOf;
import org.jikesrvm.compilers.opt.ir.Instruction;
import org.jikesrvm.compilers.opt.ir.Move;
import org.jikesrvm.compilers.opt.ir.New;
import org.jikesrvm.compilers.opt.ir.NewArray;
import org.jikesrvm.compilers.opt.ir.NullCheck;
import org.jikesrvm.compilers.opt.ir.Register;
import org.jikesrvm.compilers.opt.ir.Unary;
import org.jikesrvm.compilers.opt.ir.ZeroCheck;
import org.jikesrvm.compilers.opt.ir.operand.AddressConstantOperand;
import org.jikesrvm.compilers.opt.ir.operand.BranchOperand;
import org.jikesrvm.compilers.opt.ir.operand.BranchProfileOperand;
import org.jikesrvm.compilers.opt.ir.operand.ConditionOperand;
import org.jikesrvm.compilers.opt.ir.operand.DoubleConstantOperand;
import org.jikesrvm.compilers.opt.ir.operand.FloatConstantOperand;
import org.jikesrvm.compilers.opt.ir.operand.IntConstantOperand;
import org.jikesrvm.compilers.opt.ir.operand.LongConstantOperand;
import org.jikesrvm.compilers.opt.ir.operand.NullConstantOperand;
import org.jikesrvm.compilers.opt.ir.operand.ObjectConstantOperand;
import org.jikesrvm.compilers.opt.ir.operand.Operand;
import org.jikesrvm.compilers.opt.ir.operand.RegisterOperand;
import org.jikesrvm.compilers.opt.ir.operand.TrueGuardOperand;
import org.jikesrvm.runtime.Magic;
import org.jikesrvm.runtime.RuntimeEntrypoints;
import org.vmmagic.unboxed.Address;
import org.vmmagic.unboxed.Word;

/**
 * This class simplifies expressions globally, if in SSA form, or locally within
 * a basic block if not.
 */
public class ExpressionFolding extends IRTools {
  /**
   * Only fold operations when the result of the 1st operation becomes dead
   * after folding
   * TODO: doesn't apply to local folding
   */
  private static final boolean RESTRICT_TO_DEAD_EXPRESSIONS = false;

  /**
   * Fold across uninterruptible regions
   */
  private static final boolean FOLD_OVER_UNINTERRUPTIBLE = false;
  /**
   * Fold operations on ints
   */
  private static final boolean FOLD_INTS = true;
  /**
   * Fold operations on word like things
   */
  private static final boolean FOLD_REFS = true;
  /**
   * Fold operations on longs
   */
  private static final boolean FOLD_LONGS = true;
  /**
   * Fold operations on floats
   */
  private static final boolean FOLD_FLOATS = true;
  /**
   * Fold operations on doubles
   */
  private static final boolean FOLD_DOUBLES = true;

  /**
   * Fold binary SUB operations
   */
  private static final boolean FOLD_SUBS = true;

  /**
   * Fold binary ADD operations
   */
  private static final boolean FOLD_ADDS = true;

  /**
   * Fold binary multiply operations
   */
  private static final boolean FOLD_MULTS = true;

  /**
   * Fold binary divide operations
   */
  private static final boolean FOLD_DIVS = true;

  /**
   * Fold binary shift left operations
   */
  private static final boolean FOLD_SHIFTLS = true;

  /**
   * Fold binary shift right operations
   */
  private static final boolean FOLD_SHIFTRS = true;

  /**
   * Fold binary CMP operations
   */
  private static final boolean FOLD_CMPS = true;

  /**
   * Fold binary XOR operations
   */
  private static final boolean FOLD_XORS = true;

  /**
   * Fold binary OR operations
   */
  private static final boolean FOLD_ORS = true;

  /**
   * Fold binary AND operations
   */
  private static final boolean FOLD_ANDS = true;

  /**
   * Fold unary NEG operations
   */
  private static final boolean FOLD_NEGS = true;

  /**
   * Fold unary NOT operations
   */
  private static final boolean FOLD_NOTS = true;

  /**
   * Fold operations that create a constant on the LHS? This may produce less
   * optimal code on 2 address architectures where the constant would need
   * loading into a register prior to the operation.
   */
  private static final boolean FOLD_CONSTANTS_TO_LHS = true;

  /**
   * Fold IFCMP operations
   */
  private static final boolean FOLD_IFCMPS = true;

  /**
   * Fold COND_MOVE operations
   */
  private static final boolean FOLD_CONDMOVES = true;

  /**
   * Fold xxx_2xxx where the precision is increased then decreased achieving a
   * nop effect
   */
  private static final boolean FOLD_2CONVERSION = true;

  /**
   * Fold ZeroCheck where we're testing whether a value is 0 or not
   */
  private static final boolean FOLD_CHECKS = true;

  /**
   * Print out debug information
   */
  private static final boolean VERBOSE = false;

  /**
   * Perform expression folding on individual basic blocks
   */
  public static boolean performLocal(IR ir) {
    Instruction outer = ir.cfg.entry().firstRealInstruction();
    if (VERBOSE) {
      System.out.println("Start of expression folding for: " + ir.method.toString());
    }
    boolean didSomething = false;
    // Outer loop: walk over every instruction of basic block looking for candidates
    while (outer != null) {
      Register outerDef = isCandidateExpression(outer, false);
      if (outerDef != null) {
        if (VERBOSE) {
          System.out.println("Found outer candidate of: " + outer.toString());
        }
        Instruction inner = outer.nextInstructionInCodeOrder();
        // Inner loop: walk over instructions in block, after outer instruction,
        // checking whether inner and outer could be folded together.
        // if not check for a dependency that means there are potential hazards
        // to stop the search
        loop_over_inner_instructions:
        while ((inner != null) && (inner.operator() != BBEND) && !inner.isGCPoint()) {
          if (!FOLD_OVER_UNINTERRUPTIBLE &&
              ((inner.operator() == UNINT_BEGIN) || (inner.operator() == UNINT_END))) {
            break loop_over_inner_instructions;
          }
          Register innerDef = isCandidateExpression(inner, false);
          // 1. check for true dependence (does inner use outer's def?)
          if (innerDef != null) {
            if (VERBOSE) {
              System.out.println("Found inner candidate of: " + inner.toString());
            }
            RegisterOperand use = getUseFromCandidate(inner);
            if ((use != null) && (use.getRegister() == outerDef)) {
              // Optimization case
              Instruction newInner;
              try {
                if (VERBOSE) {
                  System.out.println("Trying to fold:" + outer.toString());
                  System.out.println("          with:" + inner.toString());
                }
                newInner = transform(inner, outer);
              } catch (OptimizingCompilerException e) {
                OptimizingCompilerException newE = new OptimizingCompilerException("Error transforming " + outer + " ; " + inner);
                newE.initCause(e);
                throw newE;
              }
              if (newInner != null) {
                if (VERBOSE) {
                  System.out.println("Replacing:" + inner.toString());
                  System.out.println("     with:" + newInner.toString());
                }
                DefUse.replaceInstructionAndUpdateDU(inner, CPOS(inner,newInner));
                inner = newInner;
                didSomething = true;
              }
            }
          }
          // 2. check for output dependence (does inner kill outer's def?)
          if (innerDef == outerDef) {
            if (VERBOSE) {
              System.out.println("Stopping search as innerDef == outerDef " + innerDef.toString());
            }
            break loop_over_inner_instructions;
          }
          if (innerDef == null) {
            Enumeration<Operand> defs = inner.getDefs();
            while(defs.hasMoreElements()) {
              Operand def = defs.nextElement();
              if (def.isRegister()) {
                Register defReg = def.asRegister().getRegister();
                if (defReg == outerDef) {
                  if (VERBOSE) {
                    System.out.println("Stopping search as innerDef == outerDef " + defReg.toString());
                  }
                  break loop_over_inner_instructions;
                }
              }
            }
          }
          // 3. check for anti dependence (do we define something that outer uses?)
          if (innerDef != null) {
            Enumeration<Operand> uses = outer.getUses();
            while(uses.hasMoreElements()) {
              Operand use = uses.nextElement();
              if (use.isRegister() && (use.asRegister().getRegister() == innerDef)) {
                if (VERBOSE) {
                  System.out.println("Stopping search as anti-dependence " + use.toString());
                }
                break loop_over_inner_instructions;
              }
            }
          } else {
            Enumeration<Operand> defs = inner.getDefs();
            while(defs.hasMoreElements()) {
              Operand def = defs.nextElement();
              if (def.isRegister()) {
                Enumeration<Operand> uses = outer.getUses();
                while(uses.hasMoreElements()) {
                  Operand use = uses.nextElement();
                  if (use.similar(def)) {
                    if (VERBOSE) {
                      System.out.println("Stopping search as anti-dependence " + use.toString());
                    }
                    break loop_over_inner_instructions;
                  }
                }
              }
            }
          }
          inner = inner.nextInstructionInCodeOrder();
        } // loop over inner instructions
      }
      outer = outer.nextInstructionInCodeOrder();
    } // loop over outer instructions
    return didSomething;
  }

  /**
   * Get the register that's used by the candidate instruction
   * @param s the instruction
   * @return register used by candidate or {@code null} if this isn't a candidate
   */
  private static RegisterOperand getUseFromCandidate(Instruction s) {
    if (Binary.conforms(s)) {
      return Binary.getVal1(s).asRegister();
    } else if (GuardedBinary.conforms(s)) {
      return GuardedBinary.getVal1(s).asRegister();
    } else if (Unary.conforms(s)) {
      return Unary.getVal(s).asRegister();
    } else if (GuardedUnary.conforms(s)) {
      return GuardedUnary.getVal(s).asRegister();
    } else if (BooleanCmp.conforms(s)) {
      return BooleanCmp.getVal1(s).asRegister();
    } else if (IfCmp.conforms(s)) {
      return IfCmp.getVal1(s).asRegister();
    } else if (IfCmp2.conforms(s)) {
      return IfCmp2.getVal1(s).asRegister();
    } else if (CondMove.conforms(s)) {
      return CondMove.getVal1(s).asRegister();
    } else if (ZeroCheck.conforms(s)) {
      return ZeroCheck.getValue(s).asRegister();
    } else if (BoundsCheck.conforms(s)) {
      return BoundsCheck.getRef(s).asRegister();
    } else if (NullCheck.conforms(s)) {
      return NullCheck.getRef(s).asRegister();
    } else if (InstanceOf.conforms(s)) {
      return InstanceOf.getRef(s).asRegister();
    } else if (NewArray.conforms(s) || New.conforms(s)) {
      return null;
    } else {
      OptimizingCompilerException.UNREACHABLE();
      return null;
    }
  }

  /**
   * Get the register that's defined by the candidate instruction
   * @param first is this the first instruction?
   * @param s the instruction
   * @return register used by candidate or {@code null} if this isn't a candidate
   */
  private static RegisterOperand getDefFromCandidate(Instruction s, boolean first) {
    if (Binary.conforms(s)) {
      return Binary.getResult(s);
    } else if (GuardedBinary.conforms(s)) {
      return GuardedBinary.getResult(s);
    } else if (Unary.conforms(s)) {
      return Unary.getResult(s);
    } else if (GuardedUnary.conforms(s)) {
      return GuardedUnary.getResult(s);
    } else if (BooleanCmp.conforms(s)) {
      return BooleanCmp.getResult(s);
    } else if (IfCmp.conforms(s)) {
      if (first) {
        return null;
      } else {
        return IfCmp.getGuardResult(s);
      }
    } else if (IfCmp2.conforms(s)) {
      if (first) {
        return null;
      } else {
        return IfCmp2.getGuardResult(s);
      }
    } else if (CondMove.conforms(s)) {
      if (first) {
        return null;
      } else {
        return CondMove.getResult(s);
      }
    } else if (ZeroCheck.conforms(s)) {
      return ZeroCheck.getGuardResult(s);
    } else if (BoundsCheck.conforms(s)) {
      return BoundsCheck.getGuardResult(s);
    } else if (NullCheck.conforms(s)) {
      return NullCheck.getGuardResult(s);
    } else if (InstanceOf.conforms(s)) {
      return InstanceOf.getResult(s);
    } else if (NewArray.conforms(s)) {
      if (first) {
        return NewArray.getResult(s).asRegister();
      } else {
        return null;
      }
    } else if (New.conforms(s)) {
      if (first) {
        return New.getResult(s).asRegister();
      } else {
        return null;
      }
    } else {
      OptimizingCompilerException.UNREACHABLE();
      return null;
    }
  }
  /**
   * Perform the transformation.
   *
   * If we have, in SSA form,
   *
   * <pre>
   *    x = a op1 c1
   *    y = x op2 c2
   * </pre>
   *
   * where c1 and c2 are constants, replace the def of y by
   *
   * <pre>
   * y = a op1 (c1 op3 c2)
   * </pre>
   *
   * Where op1, op2 and op3 are add, subtract, multiply, and, or, xor and
   * compare. Repeatedly apply transformation until all expressions are folded.
   *
   * <p>
   * PRECONDITIONS: SSA form, register lists computed
   *
   * @param ir
   *          the governing IR
   */
  public static void perform(IR ir) {
    // Create a set of potential computations to fold.
    HashSet<Register> candidates = new HashSet<Register>(20);

    for (Enumeration<Instruction> e = ir.forwardInstrEnumerator(); e.hasMoreElements();) {
      Instruction s = e.nextElement();
      // Check if s is a candidate for expression folding
      Register r = isCandidateExpression(s, true);
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

      iterate_over_candidates:
      for (Iterator<Register> it = candidates.iterator(); it.hasNext();) {
        Register r = it.next();
        Instruction s = r.getFirstDef();
        Operand val1 = getUseFromCandidate(s);
        if (val1 == null) continue; // operator that's not being optimized
        if (VERBOSE) {
          System.out.println("Found candidate instruction: " + s.toString());
        }
        Instruction def = val1.asRegister().getRegister().getFirstDef();
        // filter out moves to get the real defining instruction
        while (Move.conforms(def)) {
          Operand op = Move.getVal(def);
          if (op.isRegister()) {
            def = op.asRegister().getRegister().getFirstDef();
          } else {
            // The non-constant operand of the candidate expression is the
            // result of moving a constant. Remove as a candidate and leave
            // for constant propagation and simplification.
            it.remove();
            continue iterate_over_candidates;
          }
        }
        if (candidates.contains(val1.asRegister().getRegister())) {
          if (VERBOSE) {
            System.out.println(" Found candidate definition: " + def.toString());
          }
          // check if the defining instruction has not mutated yet
          if (isCandidateExpression(def, true) == null) {
            if (VERBOSE) {
              System.out.println(" Ignoring definition that is no longer a candidate");
            }
            continue;
          }

          Instruction newS = transform(s, def);
          if (newS != null) {
            if (VERBOSE) {
              System.out.println(" Replacing: " + s.toString() + "\n with:" + newS.toString());
            }
            // check if this expression is still an optimisation candidate
            if (isCandidateExpression(newS, true) == null) {
              it.remove();
            }
            DefUse.replaceInstructionAndUpdateDU(s, CPOS(s,newS));
            didSomething = true;
          }
        }
      }
    }
  }

  /**
   * Prune the candidate set; restrict candidates to only allow transformations
   * that result in dead code to be eliminated
   */
  private static void pruneCandidates(HashSet<Register> candidates) {
    for (Iterator<Register> i = candidates.iterator(); i.hasNext();) {
      Register r = i.next();
      Instruction s = r.getFirstDef();
      Operand val1 = getUseFromCandidate(s);
      if (val1 == null) continue; // operator that's not being optimized

      if (VM.VerifyAssertions) {
        boolean isRegister = val1.isRegister();
        if (!isRegister) {
          String msg = "Error with val1 of " + s;
          VM._assert(VM.NOT_REACHED, msg);
        }
      }

      Register v1 = val1.asRegister().getRegister();
      if (candidates.contains(v1)) {
        Enumeration<RegisterOperand> uses = DefUse.uses(v1);
        while (uses.hasMoreElements()) {
          RegisterOperand op = uses.nextElement();
          Instruction u = op.instruction;
          if ((isCandidateExpression(u, true) == null) && !Move.conforms(u)) {
            i.remove();
            break;
          }
        }
      }
    }
  }

  /**
   * Perform the transformation on the instruction
   *
   * @param s
   *          the instruction to transform of the form y = x op c1
   * @param def
   *          the definition of x, the defining instruction is of the form x = a
   *          op c2
   * @return the new instruction to replace s;
   */
  private static Instruction transform(Instruction s, Instruction def) {
    // x = a op1 c1  <-- def
    // y = x op2 c2  <-- s
    final RegisterOperand a = getUseFromCandidate(def);
    final RegisterOperand x = getDefFromCandidate(def, true);
    if (x == null) {
      return null;
    }
    final RegisterOperand y = getDefFromCandidate(s, false);
    if (y == null) {
      return null;
    }

    if (VM.VerifyAssertions) {
      RegisterOperand x2;
      x2 = getUseFromCandidate(s);
      boolean similar = x.similar(x2);
      if (!similar) {
        String msg = "x not similar to x2 " + x + " : " + x2;
        VM._assert(VM.NOT_REACHED, msg);
      }
    }

    switch (s.operator.opcode) {
      // Foldable operators
      case INT_ADD_opcode: {
        if (FOLD_INTS && FOLD_ADDS) {
          int c2 = getIntValue(Binary.getVal2(s));
          if (def.operator == INT_ADD) {
            int c1 = getIntValue(Binary.getVal2(def));
            // x = a + c1; y = x + c2
            return Binary.create(INT_ADD, y.copyRO(), a.copyRO(), IC(c1 + c2));
          } else if (def.operator == INT_SUB) {
            int c1 = getIntValue(Binary.getVal2(def));
            // x = a - c1; y = x + c2
            return Binary.create(INT_ADD, y.copyRO(), a.copyRO(), IC(c2 - c1));
          } else if (def.operator == INT_NEG && FOLD_CONSTANTS_TO_LHS) {
            // x = -a; y = x + c2;
            return Binary.create(INT_SUB, y.copyRO(), IC(c2), a.copyRO());
          }
        }
        return null;
      }
      case REF_ADD_opcode: {
        if (FOLD_REFS && FOLD_ADDS) {
          Address c2 = getAddressValue(Binary.getVal2(s));
          if (def.operator == REF_ADD) {
            Address c1 = getAddressValue(Binary.getVal2(def));
            // x = a + c1; y = x + c2
            return Binary.create(REF_ADD, y.copyRO(), a.copyRO(), AC(c1.toWord().plus(c2.toWord()).toAddress()));
          } else if (def.operator == REF_SUB) {
            Address c1 = getAddressValue(Binary.getVal2(def));
            // x = a - c1; y = x + c2
            return Binary.create(REF_ADD, y.copyRO(), a.copyRO(), AC(c2.toWord().minus(c1.toWord()).toAddress()));
          } else if (def.operator == REF_NEG && FOLD_CONSTANTS_TO_LHS) {
            // x = -a; y = x + c2;
            return Binary.create(REF_SUB, y.copyRO(), AC(c2), a.copyRO());
          }
        }
        return null;
      }
      case LONG_ADD_opcode: {
        if (FOLD_LONGS && FOLD_ADDS) {
          long c2 = getLongValue(Binary.getVal2(s));
          if (def.operator == LONG_ADD) {
            long c1 = getLongValue(Binary.getVal2(def));
            // x = a + c1; y = x + c2
            return Binary.create(LONG_ADD, y.copyRO(), a.copyRO(), LC(c1 + c2));
          } else if (def.operator == LONG_SUB) {
            long c1 = getLongValue(Binary.getVal2(def));
            // x = a - c1; y = x + c2
            return Binary.create(LONG_ADD, y.copyRO(), a.copyRO(), LC(c2 - c1));
          } else if (def.operator == LONG_NEG && FOLD_CONSTANTS_TO_LHS) {
            // x = -a; y = x + c2;
            return Binary.create(LONG_SUB, y.copyRO(), LC(c2), a.copyRO());
          }
        }
        return null;
      }
      case FLOAT_ADD_opcode: {
        if (FOLD_FLOATS && FOLD_ADDS) {
          float c2 = getFloatValue(Binary.getVal2(s));
          if (def.operator == FLOAT_ADD) {
            float c1 = getFloatValue(Binary.getVal2(def));
            // x = a + c1; y = x + c2
            return Binary.create(FLOAT_ADD, y.copyRO(), a.copyRO(), FC(c1 + c2));
          } else if (def.operator == FLOAT_SUB) {
            float c1 = getFloatValue(Binary.getVal2(def));
            // x = a - c1; y = x + c2
            return Binary.create(FLOAT_ADD, y.copyRO(), a.copyRO(), FC(c2 - c1));
          } else if (def.operator == FLOAT_NEG && FOLD_CONSTANTS_TO_LHS) {
            // x = -a; y = x + c2;
            return Binary.create(FLOAT_SUB, y.copyRO(), FC(c2), a.copyRO());
          }
        }
        return null;
      }
      case DOUBLE_ADD_opcode: {
        if (FOLD_DOUBLES && FOLD_ADDS) {
          double c2 = getDoubleValue(Binary.getVal2(s));
          if (def.operator == DOUBLE_ADD) {
            double c1 = getDoubleValue(Binary.getVal2(def));
            // x = a + c1; y = x + c2
            return Binary.create(DOUBLE_ADD, y.copyRO(), a.copyRO(), DC(c1 + c2));
          } else if (def.operator == DOUBLE_SUB) {
            double c1 = getDoubleValue(Binary.getVal2(def));
            // x = a - c1; y = x + c2
            return Binary.create(DOUBLE_ADD, y.copyRO(), a.copyRO(), DC(c2 - c1));
          } else if (def.operator == DOUBLE_NEG && FOLD_CONSTANTS_TO_LHS) {
            // x = -a; y = x + c2;
            return Binary.create(DOUBLE_SUB, y.copyRO(), DC(c2), a.copyRO());
          }
        }
        return null;
      }
      case INT_SUB_opcode: {
        if (FOLD_INTS && FOLD_SUBS) {
          int c2 = getIntValue(Binary.getVal2(s));
          if (def.operator == INT_ADD) {
            int c1 = getIntValue(Binary.getVal2(def));
            // x = a + c1; y = x - c2
            return Binary.create(INT_ADD, y.copyRO(), a.copyRO(), IC(c1 - c2));
          } else if (def.operator == INT_SUB) {
            int c1 = getIntValue(Binary.getVal2(def));
            // x = a - c1; y = x - c2
            return Binary.create(INT_ADD, y.copyRO(), a.copyRO(), IC(-c1 - c2));
          } else if (def.operator == INT_NEG && FOLD_CONSTANTS_TO_LHS) {
            // x = -a; y = x - c2;
            return Binary.create(INT_SUB, y.copyRO(), IC(-c2), a.copyRO());
          }
        }
        return null;
      }
      case REF_SUB_opcode: {
        if (FOLD_REFS && FOLD_SUBS) {
          Address c2 = getAddressValue(Binary.getVal2(s));
          if (def.operator == REF_ADD) {
            Address c1 = getAddressValue(Binary.getVal2(def));
            // x = a + c1; y = x - c2
            return Binary.create(REF_ADD, y.copyRO(), a.copyRO(), AC(c1.toWord().minus(c2.toWord()).toAddress()));
          } else if (def.operator == REF_SUB) {
            Address c1 = getAddressValue(Binary.getVal2(def));
            // x = a - c1; y = x - c2
            return Binary.create(REF_ADD,
                                 y.copyRO(),
                                 a.copyRO(),
                                 AC(Word.zero().minus(c1.toWord()).minus(c2.toWord()).toAddress()));
          } else if (def.operator == REF_NEG && FOLD_CONSTANTS_TO_LHS) {
            // x = -a; y = x - c2;
            return Binary.create(REF_SUB, y.copyRO(), AC(Word.zero().minus(c2.toWord()).toAddress()), a.copyRO());
          }
        }
        return null;
      }
      case LONG_SUB_opcode: {
        if (FOLD_LONGS && FOLD_SUBS) {
          long c2 = getLongValue(Binary.getVal2(s));
          if (def.operator == LONG_ADD) {
            long c1 = getLongValue(Binary.getVal2(def));
            // x = a + c1; y = x - c2
            return Binary.create(LONG_ADD, y.copyRO(), a.copyRO(), LC(c1 - c2));
          } else if (def.operator == LONG_SUB) {
            long c1 = getLongValue(Binary.getVal2(def));
            // x = a - c1; y = x - c2
            return Binary.create(LONG_ADD, y.copyRO(), a.copyRO(), LC(-c1 - c2));
          } else if (def.operator == LONG_NEG && FOLD_CONSTANTS_TO_LHS) {
            // x = -a; y = x - c2;
            return Binary.create(LONG_SUB, y.copyRO(), LC(-c2), a.copyRO());
          }
        }
        return null;
      }
      case FLOAT_SUB_opcode: {
        if (FOLD_FLOATS && FOLD_SUBS) {
          float c2 = getFloatValue(Binary.getVal2(s));
          if (def.operator == FLOAT_ADD) {
            float c1 = getFloatValue(Binary.getVal2(def));
            // x = a + c1; y = x - c2
            return Binary.create(FLOAT_ADD, y.copyRO(), a.copyRO(), FC(c1 - c2));
          } else if (def.operator == FLOAT_SUB) {
            float c1 = getFloatValue(Binary.getVal2(def));
            // x = a - c1; y = x - c2
            return Binary.create(FLOAT_ADD, y.copyRO(), a.copyRO(), FC(-c1 - c2));
          } else if (def.operator == FLOAT_NEG && FOLD_CONSTANTS_TO_LHS) {
            // x = -a; y = x - c2;
            return Binary.create(FLOAT_SUB, y.copyRO(), FC(-c2), a.copyRO());
          }
        }
        return null;
      }
      case DOUBLE_SUB_opcode: {
        if (FOLD_DOUBLES && FOLD_SUBS) {
          double c2 = getDoubleValue(Binary.getVal2(s));
          if (def.operator == FLOAT_ADD) {
            double c1 = getDoubleValue(Binary.getVal2(def));
            // x = a + c1; y = x - c2
            return Binary.create(DOUBLE_ADD, y.copyRO(), a.copyRO(), DC(c1 - c2));
          } else if (def.operator == DOUBLE_SUB) {
            double c1 = getDoubleValue(Binary.getVal2(def));
            // x = a - c1; y = x + c2
            return Binary.create(DOUBLE_ADD, y.copyRO(), a.copyRO(), DC(-c1 - c2));
          } else if (def.operator == DOUBLE_NEG && FOLD_CONSTANTS_TO_LHS) {
            // x = -a; y = x - c2;
            return Binary.create(DOUBLE_SUB, y.copyRO(), DC(-c2), a.copyRO());
          }
        }
        return null;
      }
      case INT_MUL_opcode: {
        if (FOLD_INTS && FOLD_MULTS) {
          int c2 = getIntValue(Binary.getVal2(s));
          if (def.operator == INT_MUL) {
            int c1 = getIntValue(Binary.getVal2(def));
            // x = a * c1; y = x * c2
            return Binary.create(INT_MUL, y.copyRO(), a.copyRO(), IC(c1 * c2));
          } else if (def.operator == INT_NEG) {
            // x = -a; y = x * c2;
            return Binary.create(INT_MUL, y.copyRO(), a.copyRO(), IC(-c2));
          }
        }
        return null;
      }
      case LONG_MUL_opcode: {
        if (FOLD_LONGS && FOLD_MULTS) {
          long c2 = getLongValue(Binary.getVal2(s));
          if (def.operator == LONG_MUL) {
            long c1 = getLongValue(Binary.getVal2(def));
            // x = a * c1; y = x * c2
            return Binary.create(LONG_MUL, y.copyRO(), a.copyRO(), LC(c1 * c2));
          } else if (def.operator == LONG_NEG) {
            // x = -a; y = x * c2;
            return Binary.create(LONG_MUL, y.copyRO(), a.copyRO(), LC(-c2));
          }
        }
        return null;
      }
      case FLOAT_MUL_opcode: {
        if (FOLD_FLOATS && FOLD_MULTS) {
          float c2 = getFloatValue(Binary.getVal2(s));
          if (def.operator == FLOAT_MUL) {
            float c1 = getFloatValue(Binary.getVal2(def));
            // x = a * c1; y = x * c2
            return Binary.create(FLOAT_MUL, y.copyRO(), a.copyRO(), FC(c1 * c2));
          } else if (def.operator == FLOAT_NEG) {
            // x = -a; y = x * c2;
            return Binary.create(FLOAT_MUL, y.copyRO(), a.copyRO(), FC(-c2));
          }
        }
        return null;
      }
      case DOUBLE_MUL_opcode: {
        if (FOLD_DOUBLES && FOLD_MULTS) {
          double c2 = getDoubleValue(Binary.getVal2(s));
          if (def.operator == DOUBLE_MUL) {
            double c1 = getDoubleValue(Binary.getVal2(def));
            // x = a * c1; y = x * c2
            return Binary.create(DOUBLE_MUL, y.copyRO(), a.copyRO(), DC(c1 * c2));
          } else if (def.operator == DOUBLE_NEG) {
            // x = -a; y = x * c2;
            return Binary.create(DOUBLE_MUL, y.copyRO(), a.copyRO(), DC(-c2));
          }
        }
        return null;
      }
      case INT_DIV_opcode: {
        if (FOLD_INTS && FOLD_DIVS) {
          int c2 = getIntValue(GuardedBinary.getVal2(s));
          if (def.operator == INT_DIV) {
            int c1 = getIntValue(GuardedBinary.getVal2(def));
            Operand guard = GuardedBinary.getGuard(def);
            // x = a / c1; y = x / c2
            return GuardedBinary.create(INT_DIV, y.copyRO(), a.copyRO(), IC(c1 * c2), guard);
          } else if (def.operator == INT_NEG) {
            Operand guard = GuardedBinary.getGuard(s);
            // x = -a; y = x / c2;
            return GuardedBinary.create(INT_DIV, y.copyRO(), a.copyRO(), IC(-c2), guard);
          }
        }
        return null;
      }
      case LONG_DIV_opcode: {
        if (FOLD_LONGS && FOLD_DIVS) {
          long c2 = getLongValue(GuardedBinary.getVal2(s));
          if (def.operator == LONG_DIV) {
            long c1 = getLongValue(GuardedBinary.getVal2(def));
            Operand guard = GuardedBinary.getGuard(def);
            // x = a / c1; y = x / c2
            return GuardedBinary.create(LONG_DIV, y.copyRO(), a.copyRO(), LC(c1 * c2), guard);
          } else if (def.operator == LONG_NEG) {
            Operand guard = GuardedBinary.getGuard(s);
            // x = -a; y = x / c2;
            return GuardedBinary.create(LONG_DIV, y.copyRO(), a.copyRO(), LC(-c2), guard);
          }
        }
        return null;
      }
      case FLOAT_DIV_opcode: {
        if (FOLD_FLOATS && FOLD_DIVS) {
          float c2 = getFloatValue(Binary.getVal2(s));
          if (def.operator == FLOAT_DIV) {
            float c1 = getFloatValue(Binary.getVal2(def));
            // x = a / c1; y = x / c2
            return Binary.create(FLOAT_DIV, y.copyRO(), a.copyRO(), FC(c1 * c2));
          } else if (def.operator == FLOAT_NEG) {
            // x = -a; y = x / c2;
            return Binary.create(FLOAT_DIV, y.copyRO(), a.copyRO(), FC(-c2));
          }
        }
        return null;
      }
      case DOUBLE_DIV_opcode: {
        if (FOLD_DOUBLES && FOLD_DIVS) {
          double c2 = getDoubleValue(Binary.getVal2(s));
          if (def.operator == DOUBLE_DIV) {
            double c1 = getDoubleValue(Binary.getVal2(def));
            // x = a / c1; y = x / c2
            return Binary.create(DOUBLE_DIV, y.copyRO(), a.copyRO(), DC(c1 * c2));
          } else if (def.operator == DOUBLE_NEG) {
            // x = -a; y = x / c2;
            return Binary.create(DOUBLE_DIV, y.copyRO(), a.copyRO(), DC(-c2));
          }
        }
        return null;
      }
      case INT_SHL_opcode: {
        if (FOLD_INTS && FOLD_SHIFTLS) {
          int c2 = getIntValue(Binary.getVal2(s));
          if (def.operator == INT_SHL) {
            int c1 = getIntValue(Binary.getVal2(def));
            // x = a << c1; y = x << c2
            return Binary.create(INT_SHL, y.copyRO(), a.copyRO(), IC(c1 + c2));
          } else if ((def.operator == INT_SHR) || (def.operator == INT_USHR)) {
            int c1 = getIntValue(Binary.getVal2(def));
            if (c1 == c2) {
              // x = a >> c1; y = x << c1
              return Binary.create(INT_AND, y.copyRO(), a.copyRO(), IC(-1 << c1));
            }
          } else if (def.operator == INT_AND) {
            int c1 = getIntValue(Binary.getVal2(def));
            // x = a & c1; y = << c2
            if ((c1 << c2) == (-1 << c2)) {
              // the first mask is redundant
              return Binary.create(INT_SHL, y.copyRO(), a.copyRO(), IC(c2));
            }
          } else if ((def.operator == INT_OR)||(def.operator == INT_XOR)) {
            int c1 = getIntValue(Binary.getVal2(def));
            // x = a | c1; y = << c2
            if ((c1 << c2) == 0) {
              // the first mask is redundant
              return Binary.create(INT_SHL, y.copyRO(), a.copyRO(), IC(c2));
            }
          }
        }
        return null;
      }
      case REF_SHL_opcode: {
        if (FOLD_REFS && FOLD_SHIFTLS) {
          int c2 = getIntValue(Binary.getVal2(s));
          if (def.operator == REF_SHL) {
            int c1 = getIntValue(Binary.getVal2(def));
            // x = a << c1; y = x << c2
            return Binary.create(REF_SHL, y.copyRO(), a.copyRO(), IC(c1 + c2));
          } else if ((def.operator == REF_SHR) || (def.operator == REF_USHR)) {
            int c1 = getIntValue(Binary.getVal2(def));
            if (c1 == c2) {
              // x = a >> c1; y = x << c1
              return Binary.create(REF_AND,
                                   y.copyRO(),
                                   a.copyRO(),
                                   AC(Word.zero().minus(Word.one()).lsh(c1).toAddress()));
            }
          } else if (def.operator == REF_AND) {
            Address c1 = getAddressValue(Binary.getVal2(def));
            // x = a & c1; y = x << c2
            if (c1.toWord().lsh(c2).EQ(Word.fromIntSignExtend(-1).lsh(c2))) {
              // the first mask is redundant
              return Binary.create(REF_SHL, y.copyRO(), a.copyRO(), IC(c2));
            }
          } else if ((def.operator == REF_OR)||(def.operator == REF_XOR)) {
            Address c1 = getAddressValue(Binary.getVal2(def));
            // x = a | c1; y = x << c2
            if (c1.toWord().lsh(c2).EQ(Word.zero())) {
              // the first mask is redundant
              return Binary.create(REF_SHL, y.copyRO(), a.copyRO(), IC(c2));
            }
          }
        }
        return null;
      }
      case LONG_SHL_opcode: {
        if (FOLD_LONGS && FOLD_SHIFTLS) {
          int c2 = getIntValue(Binary.getVal2(s));
          if (def.operator == LONG_SHL) {
            int c1 = getIntValue(Binary.getVal2(def));
            // x = a << c1; y = x << c2
            return Binary.create(LONG_SHL, y.copyRO(), a.copyRO(), IC(c1 + c2));
          } else if ((def.operator == LONG_SHR) || (def.operator == LONG_USHR)) {
            int c1 = getIntValue(Binary.getVal2(def));
            if (c1 == c2) {
              // x = a >> c1; y = x << c1
              return Binary.create(LONG_AND, y.copyRO(), a.copyRO(), LC(-1L << c1));
            }
          } else if (def.operator == LONG_AND) {
            long c1 = getLongValue(Binary.getVal2(def));
            // x = a & c1; y = << c2
            if ((c1 << c2) == (-1L << c2)) {
              // the first mask is redundant
              return Binary.create(LONG_SHL, y.copyRO(), a.copyRO(), IC(c2));
            }
          } else if ((def.operator == LONG_OR)||(def.operator == LONG_XOR)) {
            long c1 = getLongValue(Binary.getVal2(def));
            // x = a | c1; y = << c2
            if ((c1 << c2) == 0L) {
              // the first mask is redundant
              return Binary.create(LONG_SHL, y.copyRO(), a.copyRO(), IC(c2));
            }
          }
        }
        return null;
      }
      case INT_SHR_opcode: {
        if (FOLD_INTS && FOLD_SHIFTRS) {
          int c2 = getIntValue(Binary.getVal2(s));
          if (def.operator == INT_SHR) {
            int c1 = getIntValue(Binary.getVal2(def));
            // x = a >> c1; y = x >> c2
            return Binary.create(INT_SHR, y.copyRO(), a.copyRO(), IC(c1 + c2));
          } else if (def.operator == INT_SHL) {
            int c1 = getIntValue(Binary.getVal2(def));
            if (c1 == c2) {
              if (c1 == 24) {
                // x = a << 24; y = x >> 24
                return Unary.create(INT_2BYTE, y.copyRO(), a.copyRO());
              } else if (c1 == 16) {
                // x = a << 16; y = x >> 16
                return Unary.create(INT_2SHORT, y.copyRO(), a.copyRO());
              }
            }
          } else if (def.operator == INT_AND) {
            int c1 = getIntValue(Binary.getVal2(def));
            // x = a & c1; y = >> c2
            if ((c1 >> c2) == -1) {
              // the first mask is redundant
              return Binary.create(INT_SHR, y.copyRO(), a.copyRO(), IC(c2));
            }
          } else if ((def.operator == INT_OR)||(def.operator == INT_XOR)) {
            int c1 = getIntValue(Binary.getVal2(def));
            // x = a | c1; y = >> c2
            if ((c1 >>> c2) == 0) {
              // the first mask is redundant
              return Binary.create(INT_SHR, y.copyRO(), a.copyRO(), IC(c2));
            }
          }
        }
        return null;
      }
      case REF_SHR_opcode: {
        if (FOLD_REFS && FOLD_SHIFTRS) {
          int c2 = getIntValue(Binary.getVal2(s));
          if (def.operator == REF_SHR) {
            int c1 = getIntValue(Binary.getVal2(def));
            // x = a >> c1; y = x >> c2
            return Binary.create(REF_SHR, y.copyRO(), a.copyRO(), IC(c1 + c2));
          } else if (def.operator == REF_AND) {
            Address c1 = getAddressValue(Binary.getVal2(def));
            // x = a & c1; y = x >> c2
            if (c1.toWord().rsha(c2).EQ(Word.zero().minus(Word.one()))) {
              // the first mask is redundant
              return Binary.create(REF_SHR, y.copyRO(), a.copyRO(), IC(c2));
            }
          } else if ((def.operator == REF_OR)||(def.operator == REF_XOR)) {
            Address c1 = getAddressValue(Binary.getVal2(def));
            // x = a | c1; y = x >> c2
            if (c1.toWord().rshl(c2).EQ(Word.zero())) {
              // the first mask is redundant
              return Binary.create(REF_SHR, y.copyRO(), a.copyRO(), IC(c2));
            }
          }
        }
        return null;
      }
      case LONG_SHR_opcode: {
        if (FOLD_LONGS && FOLD_SHIFTRS) {
          int c2 = getIntValue(Binary.getVal2(s));
          if (def.operator == LONG_SHR) {
            int c1 = getIntValue(Binary.getVal2(def));
            // x = a >> c1; y = x >> c2
            return Binary.create(LONG_SHR, y.copyRO(), a.copyRO(), IC(c1 + c2));
          } else if (def.operator == LONG_AND) {
            long c1 = getLongValue(Binary.getVal2(def));
            // x = a & c1; y = >> c2
            if ((c1 >> c2) == -1L) {
              // the first mask is redundant
              return Binary.create(LONG_SHR, y.copyRO(), a.copyRO(), IC(c2));
            }
          } else if ((def.operator == LONG_OR)||(def.operator == LONG_XOR)) {
            long c1 = getLongValue(Binary.getVal2(def));
            // x = a & c1; y = >> c2
            if ((c1 >>> c2) == 0L) {
              // the first mask is redundant
              return Binary.create(LONG_SHR, y.copyRO(), a.copyRO(), IC(c2));
            }
          }
        }
        return null;
      }
      case INT_USHR_opcode: {
        if (FOLD_INTS && FOLD_SHIFTRS) {
          int c2 = getIntValue(Binary.getVal2(s));
          if (def.operator == INT_USHR) {
            int c1 = getIntValue(Binary.getVal2(def));
            // x = a >>> c1; y = x >>> c2
            return Binary.create(INT_USHR, y.copyRO(), a.copyRO(), IC(c1 + c2));
          } else if (def.operator == INT_SHL) {
            int c1 = getIntValue(Binary.getVal2(def));
            if (c1 == c2) {
              // x = a << c1; y = x >>> c1
              return Binary.create(INT_AND, y.copyRO(), a.copyRO(), IC(-1 >>> c1));
            }
          } else if (def.operator == INT_AND) {
            int c1 = getIntValue(Binary.getVal2(def));
            // x = a & c1; y = >>> c2
            if ((c1 >> c2) == -1L) {
              // the first mask is redundant
              return Binary.create(INT_USHR, y.copyRO(), a.copyRO(), IC(c2));
            }
          } else if ((def.operator == INT_OR)||(def.operator == INT_XOR)) {
            int c1 = getIntValue(Binary.getVal2(def));
            // x = a | c1; y = >>> c2
            if ((c1 >>> c2) == 0) {
              // the first mask is redundant
              return Binary.create(INT_USHR, y.copyRO(), a.copyRO(), IC(c2));
            }
          }
        }
        return null;
      }
      case REF_USHR_opcode: {
        if (FOLD_REFS && FOLD_SHIFTRS) {
          int c2 = getIntValue(Binary.getVal2(s));
          if (def.operator == REF_USHR) {
            int c1 = getIntValue(Binary.getVal2(def));
            // x = a >>> c1; y = x >>> c2
            return Binary.create(REF_USHR, y.copyRO(), a.copyRO(), IC(c1 + c2));
          } else if (def.operator == REF_SHL) {
            int c1 = getIntValue(Binary.getVal2(def));
            if (c1 == c2) {
              // x = a << c1; y = x >>> c1
              return Binary.create(REF_AND,
                                   y.copyRO(),
                                   a.copyRO(),
                                   AC(Word.zero().minus(Word.one()).rshl(c1).toAddress()));
            }
          } else if (def.operator == REF_AND) { //IAN!!!
            Address c1 = getAddressValue(Binary.getVal2(def));
            // x = a & c1; y = x >>> c2
            if (c1.toWord().rsha(c2).EQ(Word.zero().minus(Word.one()))) {
              // the first mask is redundant
              return Binary.create(REF_USHR, y.copyRO(), a.copyRO(), IC(c2));
            }
          } else if (false) { //(def.operator == REF_OR)||(def.operator == REF_XOR)) {
            Address c1 = getAddressValue(Binary.getVal2(def));
            // x = a | c1; y = x >>> c2
            if (c1.toWord().rshl(c2).EQ(Word.zero())) {
              // the first mask is redundant
              return Binary.create(REF_USHR, y.copyRO(), a.copyRO(), IC(c2));
            }
          }
        }
        return null;
      }
      case LONG_USHR_opcode: {
        if (FOLD_LONGS && FOLD_SHIFTRS) {
          int c2 = getIntValue(Binary.getVal2(s));
          if (def.operator == LONG_USHR) {
            int c1 = getIntValue(Binary.getVal2(def));
            // x = a >>> c1; y = x >>> c2
            return Binary.create(LONG_USHR, y.copyRO(), a.copyRO(), IC(c1 + c2));
          } else if (def.operator == LONG_SHL) {
            int c1 = getIntValue(Binary.getVal2(def));
            if (c1 == c2) {
              // x = a << c1; y = x >>> c1
              return Binary.create(LONG_AND, y.copyRO(), a.copyRO(), LC(-1L >>> c1));
            }
          } else if (def.operator == LONG_AND) {
            long c1 = getLongValue(Binary.getVal2(def));
            // x = a & c1; y = >>> c2
            if ((c1 >> c2) == -1L) {
              // the first mask is redundant
              return Binary.create(LONG_USHR, y.copyRO(), a.copyRO(), IC(c2));
            }
          } else if ((def.operator == LONG_OR)||(def.operator == LONG_XOR)) {
            long c1 = getLongValue(Binary.getVal2(def));
            // x = a & c1; y = >>> c2
            if ((c1 >>> c2) == 0L) {
              // the first mask is redundant
              return Binary.create(LONG_USHR, y.copyRO(), a.copyRO(), IC(c2));
            }
          }
        }
        return null;
      }
      case INT_AND_opcode: {
        if (FOLD_INTS && FOLD_ANDS) {
          int c2 = getIntValue(Binary.getVal2(s));
          if (def.operator == INT_AND) {
            int c1 = getIntValue(Binary.getVal2(def));
            // x = a & c1; y = x & c2
            return Binary.create(INT_AND, y.copyRO(), a.copyRO(), IC(c1 & c2));
          } else if (def.operator == INT_OR) {
            int c1 = getIntValue(Binary.getVal2(def));
            // x = a | c1; y = x & c2
            if ((c1 & c2) == 0) {
              return Binary.create(INT_AND, y.copyRO(), a.copyRO(), IC(c2));
            }
          } else if (def.operator == INT_XOR) {
            int c1 = getIntValue(Binary.getVal2(def));
            // x = a ^ c1; y = x & c2
            if ((c1 & c2) == 0) {
              return Binary.create(INT_AND, y.copyRO(), a.copyRO(), IC(c2));
            }
          } else if (def.operator == INT_SHR) {
            int c1 = getIntValue(Binary.getVal2(def));
            // x = a >> c1; y = x & c2
            if ((-1 >>> c1) == c2) {
              // turn arithmetic shifts into logical shifts if possible
              return Binary.create(INT_USHR, y.copyRO(), a.copyRO(), IC(c1));
            }
          } else if (def.operator == INT_SHL) {
            int c1 = getIntValue(Binary.getVal2(def));
            // x = a << c1; y = x & c2
            if (((-1 << c1) & c2) == (-1 << c1)) {
              // does the mask zero bits already cleared by the shift?
              return Binary.create(INT_SHL, y.copyRO(), a.copyRO(), IC(c1));
            }
          } else if (def.operator == INT_USHR) {
            int c1 = getIntValue(Binary.getVal2(def));
            // x = a >>> c1; y = x & c2
            if (((-1 >>> c1) & c2) == (-1 >>> c1)) {
              // does the mask zero bits already cleared by the shift?
              return Binary.create(INT_USHR, y.copyRO(), a.copyRO(), IC(c1));
            }
          }
        }
        return null;
      }
      case REF_AND_opcode: {
        if (FOLD_REFS && FOLD_ANDS) {
          Address c2 = getAddressValue(Binary.getVal2(s));
          if (def.operator == REF_AND) {
            Address c1 = getAddressValue(Binary.getVal2(def));
            // x = a & c1; y = x & c2
            return Binary.create(REF_AND, y.copyRO(), a.copyRO(), AC(c1.toWord().and(c2.toWord()).toAddress()));
          } else if (def.operator == REF_OR) {
            Address c1 = getAddressValue(Binary.getVal2(def));
            // x = a | c1; y = x & c2
            if (c1.toWord().and(c2.toWord()).EQ(Word.zero())) {
              return Binary.create(REF_AND, y.copyRO(), a.copyRO(), AC(c2));
            }
          } else if (def.operator == REF_XOR) {
            Address c1 = getAddressValue(Binary.getVal2(def));
            // x = a ^ c1; y = x & c2
            if (c1.toWord().and(c2.toWord()).EQ(Word.zero())) {
              return Binary.create(REF_AND, y.copyRO(), a.copyRO(), AC(c2));
            }
          } else if (def.operator == REF_SHR) {
            int c1 = getIntValue(Binary.getVal2(def));
            // x = a >> c1; y = x & c2
            if (Word.zero().minus(Word.one()).rshl(c1).toAddress().EQ(c2)) {
              // turn arithmetic shifts into logical ones if possible
              return Binary.create(REF_USHR, y.copyRO(), a.copyRO(), IC(c1));
            }
          } else if (def.operator == REF_SHL) {
            int c1 = getIntValue(Binary.getVal2(def));
            // x = a << c1; y = x & c2
            if (Word.zero().minus(Word.one()).lsh(c1).and(c2.toWord()).EQ(Word.zero().minus(Word.one()).lsh(c1))) {
              // does the mask zero bits already cleared by the shift?
              return Binary.create(REF_SHL, y.copyRO(), a.copyRO(), IC(c1));
            }
          } else if (def.operator == REF_USHR) {
            int c1 = getIntValue(Binary.getVal2(def));
            // x = a >>> c1; y = x & c2
            if (Word.zero().minus(Word.one()).rshl(c1).and(c2.toWord()).EQ(Word.zero().minus(Word.one()).rshl(c1))) {
              // does the mask zero bits already cleared by the shift?
              return Binary.create(REF_USHR, y.copyRO(), a.copyRO(), IC(c1));
            }
          }
        }
        return null;
      }
      case LONG_AND_opcode: {
        if (FOLD_LONGS && FOLD_ANDS) {
          long c2 = getLongValue(Binary.getVal2(s));
          if (def.operator == LONG_AND) {
            long c1 = getLongValue(Binary.getVal2(def));
            // x = a & c1; y = x & c2
            return Binary.create(LONG_AND, y.copyRO(), a.copyRO(), LC(c1 & c2));
          } else if (def.operator == LONG_OR) {
            long c1 = getLongValue(Binary.getVal2(def));
            // x = a | c1; y = x & c2
            if ((c1 & c2) == 0) {
              return Binary.create(LONG_AND, y.copyRO(), a.copyRO(), LC(c2));
            }
          } else if (def.operator == LONG_XOR) {
            long c1 = getLongValue(Binary.getVal2(def));
            // x = a ^ c1; y = x & c2
            if ((c1 & c2) == 0) {
              return Binary.create(LONG_AND, y.copyRO(), a.copyRO(), LC(c2));
            }
          } else if (def.operator == LONG_SHR) {
            int c1 = getIntValue(Binary.getVal2(def));
            // x = a >> c1; y = x & c2
            if ((-1L >>> c1) == c2) {
              // turn arithmetic shifts into logical ones if possible
              return Binary.create(LONG_USHR, y.copyRO(), a.copyRO(), IC(c1));
            }
          } else if (def.operator == LONG_SHL) {
            int c1 = getIntValue(Binary.getVal2(def));
            // x = a << c1; y = x & c2
            if (((-1L << c1) & c2) == (-1L << c1)) {
              // does the mask zero bits already cleared by the shift?
              return Binary.create(LONG_SHL, y.copyRO(), a.copyRO(), IC(c1));
            }
          } else if (def.operator == LONG_USHR) {
            int c1 = getIntValue(Binary.getVal2(def));
            // x = a >>> c1; y = x & c2
            if (((-1L >>> c1) & c2) == (-1L >>> c1)) {
              // does the mask zero bits already cleared by the shift?
              return Binary.create(LONG_USHR, y.copyRO(), a.copyRO(), IC(c1));
            }
          }
        }
        return null;
      }
      case INT_OR_opcode: {
        if (FOLD_INTS && FOLD_ORS) {
          int c2 = getIntValue(Binary.getVal2(s));
          if (def.operator == INT_OR) {
            int c1 = getIntValue(Binary.getVal2(def));
            // x = a | c1; y = x | c2
            return Binary.create(INT_OR, y.copyRO(), a.copyRO(), IC(c1 | c2));
          } else if (def.operator == INT_AND) {
            int c1 = getIntValue(Binary.getVal2(def));
            // x = a & c1; y = x | c2
            if ((~c1 | c2) == c2) {
              return Binary.create(INT_OR, y.copyRO(), a.copyRO(), IC(c2));
            }
          } else if (def.operator == INT_XOR) {
            int c1 = getIntValue(Binary.getVal2(def));
            // x = a ^ c1; y = x | c2
            if ((c1 | c2) == c2) {
              return Binary.create(INT_OR, y.copyRO(), a.copyRO(), IC(c2));
            }
          }
        }
        return null;
      }
      case REF_OR_opcode: {
        if (FOLD_REFS && FOLD_ORS) {
          Address c2 = getAddressValue(Binary.getVal2(s));
          if (def.operator == REF_OR) {
            Address c1 = getAddressValue(Binary.getVal2(def));
            // x = a | c1; y = x | c2
            return Binary.create(REF_OR, y.copyRO(), a.copyRO(), AC(c1.toWord().or(c2.toWord()).toAddress()));
          } else if (def.operator == REF_AND) {
            Address c1 = getAddressValue(Binary.getVal2(def));
            // x = a & c1; y = x | c2
            if (c1.toWord().not().or(c2.toWord()).EQ(c2.toWord())) {
              return Binary.create(REF_OR, y.copyRO(), a.copyRO(), AC(c2));
            }
          } else if (def.operator == REF_XOR) {
            Address c1 = getAddressValue(Binary.getVal2(def));
            // x = a ^ c1; y = x | c2
            if (c1.toWord().or(c2.toWord()).EQ(c2.toWord())) {
              return Binary.create(REF_OR, y.copyRO(), a.copyRO(), AC(c2));
            }
          }
        }
        return null;
      }
      case LONG_OR_opcode: {
        if (FOLD_LONGS && FOLD_ORS) {
          long c2 = getLongValue(Binary.getVal2(s));
          if (def.operator == LONG_OR) {
            long c1 = getLongValue(Binary.getVal2(def));
            // x = a | c1; y = x | c2
            return Binary.create(LONG_OR, y.copyRO(), a.copyRO(), LC(c1 | c2));
          } else if (def.operator == LONG_AND) {
            long c1 = getLongValue(Binary.getVal2(def));
            // x = a & c1; y = x | c2
            if ((~c1 | c2) == c2) {
              return Binary.create(LONG_OR, y.copyRO(), a.copyRO(), LC(c2));
            }
          } else if (def.operator == LONG_XOR) {
            long c1 = getLongValue(Binary.getVal2(def));
            // x = a ^ c1; y = x | c2
            if ((c1 | c2) == c2) {
              return Binary.create(LONG_OR, y.copyRO(), a.copyRO(), LC(c2));
            }
          }
        }
        return null;
      }
      case INT_XOR_opcode: {
        if (FOLD_INTS && FOLD_XORS) {
          int c2 = getIntValue(Binary.getVal2(s));
          if (def.operator == INT_XOR) {
            int c1 = getIntValue(Binary.getVal2(def));
            // x = a ^ c1; y = x ^ c2
            return Binary.create(INT_XOR, y.copyRO(), a.copyRO(), IC(c1 ^ c2));
          } else if (def.operator == INT_NOT) {
            // x = ~a; y = x ^ c2
            return Binary.create(INT_XOR, y.copyRO(), a.copyRO(), IC(~c2));
          } else if (def.operator == BOOLEAN_NOT) {
            // x = !a; y = x ^ c2
            return Binary.create(INT_XOR, y.copyRO(), a.copyRO(), IC(c2 ^ 1));
          }
        }
        return null;
      }
      case REF_XOR_opcode: {
        if (FOLD_REFS && FOLD_XORS) {
          Address c2 = getAddressValue(Binary.getVal2(s));
          if (def.operator == REF_XOR) {
            Address c1 = getAddressValue(Binary.getVal2(def));
            // x = a ^ c1; y = x ^ c2
            return Binary.create(REF_XOR, y.copyRO(), a.copyRO(), AC(c1.toWord().xor(c2.toWord()).toAddress()));
          } else if (def.operator == REF_NOT) {
            // x = ~a; y = x ^ c2
            return Binary.create(REF_XOR, y.copyRO(), a.copyRO(), AC(c2.toWord().not().toAddress()));
          }
        }
        return null;
      }
      case LONG_XOR_opcode: {
        if (FOLD_LONGS && FOLD_XORS) {
          long c2 = getLongValue(Binary.getVal2(s));
          if (def.operator == LONG_XOR) {
            long c1 = getLongValue(Binary.getVal2(def));
            // x = a ^ c1; y = x ^ c2
            return Binary.create(LONG_XOR, y.copyRO(), a.copyRO(), LC(c1 ^ c2));
          } else if (def.operator == LONG_NOT) {
            // x = ~a; y = x ^ c2
            return Binary.create(LONG_XOR, y.copyRO(), a.copyRO(), LC(~c2));
          }
        }
        return null;
      }
      case LONG_CMP_opcode: {
        if (FOLD_LONGS && FOLD_CMPS) {
          long c2 = getLongValue(Binary.getVal2(s));
          if (def.operator == LONG_NEG) {
            // x = -a; y = x cmp c2
            return Binary.create(LONG_CMP, y.copyRO(), LC(-c2), a.copyRO());
          }
        }
        return null;
      }
      case FLOAT_CMPL_opcode:
      case FLOAT_CMPG_opcode: {
        if (FOLD_FLOATS && FOLD_CMPS) {
          float c2 = getFloatValue(Binary.getVal2(s));
          if (def.operator == FLOAT_ADD) {
            float c1 = getFloatValue(Binary.getVal2(def));
            // x = a + c1; y = x cmp c2
            return Binary.create(s.operator, y.copyRO(), a.copyRO(), FC(c2 - c1));
          } else if (def.operator == FLOAT_SUB) {
            float c1 = getFloatValue(Binary.getVal2(def));
            // x = a - c1; y = x cmp c2
            return Binary.create(s.operator, y.copyRO(), a.copyRO(), FC(c1 + c2));
          } else if (def.operator == FLOAT_NEG) {
            // x = -a; y = x cmp c2
            return Binary.create(s.operator, y.copyRO(), FC(-c2), a.copyRO());
          }
        }
        return null;
      }
      case DOUBLE_CMPL_opcode:
      case DOUBLE_CMPG_opcode: {
        if (FOLD_DOUBLES && FOLD_CMPS) {
          double c2 = getDoubleValue(Binary.getVal2(s));
          if (def.operator == DOUBLE_ADD) {
            double c1 = getDoubleValue(Binary.getVal2(def));
            // x = a + c1; y = x cmp c2
            return Binary.create(s.operator, y.copyRO(), a.copyRO(), DC(c2 - c1));
          } else if (def.operator == DOUBLE_SUB) {
            double c1 = getDoubleValue(Binary.getVal2(def));
            // x = a - c1; y = x cmp c2
            return Binary.create(s.operator, y.copyRO(), a.copyRO(), DC(c1 + c2));
          } else if (def.operator == DOUBLE_NEG) {
            // x = -a; y = x cmp c2
            return Binary.create(s.operator, y.copyRO(), DC(-c2), a.copyRO());
          }
        }
        return null;
      }
      case BOOLEAN_CMP_INT_opcode: {
        if (FOLD_INTS && FOLD_CMPS) {
          int c2 = getIntValue(BooleanCmp.getVal2(s));
          ConditionOperand cond = (ConditionOperand) BooleanCmp.getCond(s).copy();
          BranchProfileOperand prof = (BranchProfileOperand) BooleanCmp.getBranchProfile(s).copy();
          if (cond.isEQUAL() || cond.isNOT_EQUAL()) {
            if (def.operator == INT_ADD) {
              int c1 = getIntValue(Binary.getVal2(def));
              // x = a + c1; y = x cmp c2
              return BooleanCmp.create(BOOLEAN_CMP_INT, y.copyRO(), a.copyRO(), IC(c2 - c1), cond, prof);
            } else if (def.operator == INT_SUB) {
              int c1 = getIntValue(Binary.getVal2(def));
              // x = a - c1; y = x cmp c2
              return BooleanCmp.create(BOOLEAN_CMP_INT, y.copyRO(), a.copyRO(), IC(c1 + c2), cond, prof);
            } else if (def.operator == INT_NEG) {
                // x = -a; y = x cmp c2
                return BooleanCmp.create(BOOLEAN_CMP_INT, y.copyRO(), a.copyRO(), IC(-c2), cond.flipOperands(), prof);
            } else if (def.operator == BOOLEAN_CMP_INT) {
              int c1 = getIntValue(BooleanCmp.getVal2(def));
              ConditionOperand cond2 = BooleanCmp.getCond(def).copy().asCondition();
              // x = a cmp c1 ? true : false; y = x cmp c2 ? true : false
              if ((cond.isEQUAL() && c2 == 1)||
                  (cond.isNOT_EQUAL() && c2 == 0)) {
                // Fold away redundancy boolean_cmp
                return BooleanCmp.create(BOOLEAN_CMP_INT, y.copyRO(), a.copyRO(), IC(c1), cond2, prof);
              } else if ((cond.isEQUAL() && c2 == 0)||
                  (cond.isNOT_EQUAL() && c2 == 1)) {
                // Fold away redundancy boolean_cmp
                return BooleanCmp.create(BOOLEAN_CMP_INT, y.copyRO(), a.copyRO(), IC(c1), cond2.flipCode(), prof);
              }
            } else if (def.operator == BOOLEAN_CMP_LONG) {
              long c1 = getLongValue(BooleanCmp.getVal2(def));
              ConditionOperand cond2 = BooleanCmp.getCond(def).copy().asCondition();
              // x = a cmp c1 ? true : false; y = x cmp c2 ? true : false
              if ((cond.isEQUAL() && c2 == 1)||
                  (cond.isNOT_EQUAL() && c2 == 0)) {
                // Fold away redundancy boolean_cmp
                return BooleanCmp.create(BOOLEAN_CMP_LONG, y.copyRO(), a.copyRO(), LC(c1), cond2, prof);
              } else if ((cond.isEQUAL() && c2 == 0)||
                  (cond.isNOT_EQUAL() && c2 == 1)) {
                // Fold away redundancy boolean_cmp
                return BooleanCmp.create(BOOLEAN_CMP_LONG, y.copyRO(), a.copyRO(), LC(c1), cond2.flipCode(), prof);
              }
            } else if (def.operator == BOOLEAN_CMP_ADDR) {
              Address c1 = getAddressValue(BooleanCmp.getVal2(def));
              ConditionOperand cond2 = BooleanCmp.getCond(def).copy().asCondition();
              // x = a cmp c1 ? true : false; y = x cmp c2 ? true : false
              if ((cond.isEQUAL() && c2 == 1)||
                  (cond.isNOT_EQUAL() && c2 == 0)) {
                // Fold away redundancy boolean_cmp
                return BooleanCmp.create(BOOLEAN_CMP_ADDR, y.copyRO(), a.copyRO(), AC(c1), cond2, prof);
              } else if ((cond.isEQUAL() && c2 == 0)||
                  (cond.isNOT_EQUAL() && c2 == 1)) {
                // Fold away redundancy boolean_cmp
                return BooleanCmp.create(BOOLEAN_CMP_ADDR, y.copyRO(), a.copyRO(), AC(c1), cond2.flipCode(), prof);
              }
            } else if (def.operator == BOOLEAN_CMP_FLOAT) {
              float c1 = getFloatValue(BooleanCmp.getVal2(def));
              ConditionOperand cond2 = BooleanCmp.getCond(def).copy().asCondition();
              // x = a cmp c1 ? true : false; y = x cmp c2 ? true : false
              if ((cond.isEQUAL() && c2 == 1)||
                  (cond.isNOT_EQUAL() && c2 == 0)) {
                // Fold away redundancy boolean_cmp
                return BooleanCmp.create(BOOLEAN_CMP_FLOAT, y.copyRO(), a.copyRO(), FC(c1), cond2, prof);
              } else if ((cond.isEQUAL() && c2 == 0)||
                  (cond.isNOT_EQUAL() && c2 == 1)) {
                // Fold away redundancy boolean_cmp
                return BooleanCmp.create(BOOLEAN_CMP_FLOAT, y.copyRO(), a.copyRO(), FC(c1), cond2.flipCode(), prof);
              }
            } else if (def.operator == BOOLEAN_CMP_DOUBLE) {
              double c1 = getDoubleValue(BooleanCmp.getVal2(def));
              ConditionOperand cond2 = BooleanCmp.getCond(def).copy().asCondition();
              // x = a cmp c1 ? true : false; y = x cmp c2 ? true : false
              if ((cond.isEQUAL() && c2 == 1)||
                  (cond.isNOT_EQUAL() && c2 == 0)) {
                // Fold away redundancy boolean_cmp
                return BooleanCmp.create(BOOLEAN_CMP_DOUBLE, y.copyRO(), a.copyRO(), DC(c1), cond2, prof);
              } else if ((cond.isEQUAL() && c2 == 0)||
                  (cond.isNOT_EQUAL() && c2 == 1)) {
                // Fold away redundancy boolean_cmp
                return BooleanCmp.create(BOOLEAN_CMP_DOUBLE, y.copyRO(), a.copyRO(), DC(c1), cond2.flipCode(), prof);
              }
            } else if (def.operator == LONG_CMP) {
              long c1 = getLongValue(Binary.getVal2(def));
              // x = a lcmp c1; y = y = x cmp c2 ? true : false
              if (cond.isEQUAL() && c2 == 0) {
                return BooleanCmp.create(BOOLEAN_CMP_LONG, y.copyRO(), a.copyRO(), LC(c1),
                    ConditionOperand.EQUAL(), prof);
              } else if (cond.isNOT_EQUAL() && c2 == 0) {
                return BooleanCmp.create(BOOLEAN_CMP_LONG, y.copyRO(), a.copyRO(), LC(c1),
                    ConditionOperand.NOT_EQUAL(), prof);
              } else if ((cond.isEQUAL() && c2 == 1)||(cond.isGREATER() && c2 == 0)){
                return BooleanCmp.create(BOOLEAN_CMP_LONG, y.copyRO(), a.copyRO(), LC(c1),
                    ConditionOperand.GREATER(), prof);
              } else if (cond.isGREATER_EQUAL() && c2 == 0){
                return BooleanCmp.create(BOOLEAN_CMP_LONG, y.copyRO(), a.copyRO(), LC(c1),
                    ConditionOperand.GREATER_EQUAL(), prof);
              } else if ((cond.isEQUAL() && c2 == -1)||(cond.isLESS() && c2 == 0)) {
                return BooleanCmp.create(BOOLEAN_CMP_LONG, y.copyRO(), a.copyRO(), LC(c1),
                    ConditionOperand.LESS(), prof);
              } else if (cond.isLESS_EQUAL() && c2 == 0) {
                return BooleanCmp.create(BOOLEAN_CMP_LONG, y.copyRO(), a.copyRO(), LC(c1),
                    ConditionOperand.LESS_EQUAL(), prof);
              }
            }
          }
        }
        return null;
      }
      case BOOLEAN_CMP_LONG_opcode: {
        if (FOLD_LONGS && FOLD_CMPS) {
          long c2 = getLongValue(BooleanCmp.getVal2(s));
          ConditionOperand cond = (ConditionOperand) BooleanCmp.getCond(s).copy();
          BranchProfileOperand prof = (BranchProfileOperand) BooleanCmp.getBranchProfile(s).copy();
          if (cond.isEQUAL() || cond.isNOT_EQUAL()) {
            if (def.operator == LONG_ADD) {
              long c1 = getLongValue(Binary.getVal2(def));
              // x = a + c1; y = x cmp c2
              return BooleanCmp.create(BOOLEAN_CMP_LONG, y.copyRO(), a.copyRO(), LC(c2 - c1), cond, prof);
            } else if (def.operator == LONG_SUB) {
              long c1 = getLongValue(Binary.getVal2(def));
              // x = a - c1; y = x cmp c2
              return BooleanCmp.create(BOOLEAN_CMP_LONG, y.copyRO(), a.copyRO(), LC(c1 + c2), cond, prof);
            } else if (def.operator == LONG_NEG) {
              // x = -a; y = x cmp c2
              return BooleanCmp.create(BOOLEAN_CMP_INT, y.copyRO(), a.copyRO(), LC(-c2), cond.flipOperands(), prof);
            }
          }
        }
        return null;
      }
      case BOOLEAN_CMP_ADDR_opcode: {
        if (FOLD_REFS && FOLD_CMPS) {
          Address c2 = getAddressValue(BooleanCmp.getVal2(s));
          ConditionOperand cond = (ConditionOperand) BooleanCmp.getCond(s).copy();
          BranchProfileOperand prof = (BranchProfileOperand) BooleanCmp.getBranchProfile(s).copy();
          if (cond.isEQUAL() || cond.isNOT_EQUAL()) {
            if (def.operator == REF_ADD) {
              Address c1 = getAddressValue(Binary.getVal2(def));
              // x = a + c1; y = x cmp c2
              return BooleanCmp.create(BOOLEAN_CMP_ADDR,
                  y.copyRO(),
                  a.copyRO(),
                  AC(c2.toWord().minus(c1.toWord()).toAddress()),
                  cond,
                  prof);
            } else if (def.operator == REF_SUB) {
              Address c1 = getAddressValue(Binary.getVal2(def));
              // x = a - c1; y = x cmp c2
              return BooleanCmp.create(BOOLEAN_CMP_ADDR,
                  y.copyRO(),
                  a.copyRO(),
                  AC(c1.toWord().plus(c2.toWord()).toAddress()),
                  cond,
                  prof);
            } else if (def.operator == REF_NEG) {
              // x = -a; y = x cmp c2
              return BooleanCmp.create(BOOLEAN_CMP_ADDR,
                                       y.copyRO(),
                                       a.copyRO(),
                                       AC(Word.zero().minus(c2.toWord()).toAddress()),
                                       cond.flipOperands(),
                                       prof);
            }
          }
        }
        return null;
      }
      case INT_IFCMP_opcode: {
        if (FOLD_INTS && FOLD_IFCMPS) {
          int c2 = getIntValue(IfCmp.getVal2(s));
          ConditionOperand cond = (ConditionOperand) IfCmp.getCond(s).copy();
          BranchOperand target = (BranchOperand) IfCmp.getTarget(s).copy();
          BranchProfileOperand prof = (BranchProfileOperand) IfCmp.getBranchProfile(s).copy();
          if (cond.isEQUAL() || cond.isNOT_EQUAL()) {
            if (def.operator == INT_ADD) {
              int c1 = getIntValue(Binary.getVal2(def));
              // x = a + c1; y = x cmp c2
              return IfCmp.create(INT_IFCMP, y.copyRO(), a.copyRO(), IC(c2 - c1), cond, target, prof);
            } else if (def.operator == INT_SUB) {
              int c1 = getIntValue(Binary.getVal2(def));
              // x = a - c1; y = x cmp c2
              return IfCmp.create(INT_IFCMP, y.copyRO(), a.copyRO(), IC(c1 + c2), cond, target, prof);
            } else if (def.operator == INT_NEG) {
                // x = -a; y = x cmp c2
                return IfCmp.create(INT_IFCMP, y.copyRO(), a.copyRO(), IC(-c2), cond.flipOperands(), target, prof);
            } else if (def.operator == BOOLEAN_CMP_INT) {
              int c1 = getIntValue(BooleanCmp.getVal2(def));
              ConditionOperand cond2 = BooleanCmp.getCond(def).copy().asCondition();
              // x = a cmp<cond2> c1 ? true : false; y = x cmp<cond> c2
              if ((cond.isEQUAL() && c2 == 1)||
                  (cond.isNOT_EQUAL() && c2 == 0)) {
                // Fold away redundant boolean_cmp
                // x = a cmp<cond2> c1; y = x == 1  ==> y = a cmp<cond2> c1
                return IfCmp.create(INT_IFCMP, y.copyRO(), a.copyRO(), IC(c1), cond2, target, prof);
              } else if ((cond.isEQUAL() && c2 == 0)||
                  (cond.isNOT_EQUAL() && c2 == 1)) {
                // Fold away redundant boolean_cmp
                // x = a cmp<cond2> c1; y = x == 0  ==> y = a cmp<!cond2> c1
                return IfCmp.create(INT_IFCMP, y.copyRO(), a.copyRO(), IC(c1), cond2.flipCode(), target, prof);
              }
            } else if (def.operator == BOOLEAN_CMP_LONG) {
              long c1 = getLongValue(BooleanCmp.getVal2(def));
              ConditionOperand cond2 = BooleanCmp.getCond(def).copy().asCondition();
              // x = a cmp c1 ? true : false; y = x cmp c2
              if ((cond.isEQUAL() && c2 == 1)||
                  (cond.isNOT_EQUAL() && c2 == 0)) {
                // Fold away redundant boolean_cmp
                return IfCmp.create(LONG_IFCMP, y.copyRO(), a.copyRO(), LC(c1), cond2, target, prof);
              } else if ((cond.isEQUAL() && c2 == 0)||
                  (cond.isNOT_EQUAL() && c2 == 1)) {
                // Fold away redundant boolean_cmp
                return IfCmp.create(LONG_IFCMP, y.copyRO(), a.copyRO(), LC(c1), cond2.flipCode(), target, prof);
              }
            } else if (def.operator == BOOLEAN_CMP_ADDR) {
              Address c1 = getAddressValue(BooleanCmp.getVal2(def));
              ConditionOperand cond2 = BooleanCmp.getCond(def).copy().asCondition();
              // x = a cmp c1 ? true : false; y = x cmp c2
              if ((cond.isEQUAL() && c2 == 1)||
                (cond.isNOT_EQUAL() && c2 == 0)) {
                // Fold away redundant boolean_cmp
                return IfCmp.create(REF_IFCMP, y.copyRO(), a.copyRO(), AC(c1), cond2, target, prof);
              } else if ((cond.isEQUAL() && c2 == 0)||
                (cond.isNOT_EQUAL() && c2 == 1)) {
                // Fold away redundant boolean_cmp
                return IfCmp.create(REF_IFCMP, y.copyRO(), a.copyRO(), AC(c1), cond2.flipCode(), target, prof);
              }
            } else if (def.operator == BOOLEAN_CMP_FLOAT) {
              float c1 = getFloatValue(BooleanCmp.getVal2(def));
              ConditionOperand cond2 = BooleanCmp.getCond(def).copy().asCondition();
              // x = a cmp c1 ? true : false; y = x cmp c2
              if ((cond.isEQUAL() && c2 == 1)||
                  (cond.isNOT_EQUAL() && c2 == 0)) {
                // Fold away redundant boolean_cmp
                return IfCmp.create(FLOAT_IFCMP, y.copyRO(), a.copyRO(), FC(c1), cond2, target, prof);
              } else if ((cond.isEQUAL() && c2 == 0)||
                  (cond.isNOT_EQUAL() && c2 == 1)) {
                // Fold away redundant boolean_cmp
                return IfCmp.create(FLOAT_IFCMP, y.copyRO(), a.copyRO(), FC(c1), cond2.flipCode(), target, prof);
              }
            } else if (def.operator == BOOLEAN_CMP_DOUBLE) {
              double c1 = getDoubleValue(BooleanCmp.getVal2(def));
              ConditionOperand cond2 = BooleanCmp.getCond(def).copy().asCondition();
              // x = a cmp c1 ? true : false; y = x cmp c2
              if ((cond.isEQUAL() && c2 == 1)||
                  (cond.isNOT_EQUAL() && c2 == 0)) {
                // Fold away redundant boolean_cmp
                return IfCmp.create(DOUBLE_IFCMP, y.copyRO(), a.copyRO(), DC(c1), cond2, target, prof);
              } else if ((cond.isEQUAL() && c2 == 0)||
                  (cond.isNOT_EQUAL() && c2 == 1)) {
                // Fold away redundant boolean_cmp
                return IfCmp.create(DOUBLE_IFCMP, y.copyRO(), a.copyRO(), DC(c1), cond2.flipCode(), target, prof);
              }
            } else if (def.operator == LONG_CMP) {
              long c1 = getLongValue(Binary.getVal2(def));
              // x = a lcmp c1; y = y = x cmp c2
              if (cond.isEQUAL() && c2 == 0) {
                return IfCmp.create(LONG_IFCMP, y.copyRO(), a.copyRO(), LC(c1),
                    ConditionOperand.EQUAL(), target, prof);
              } else if (cond.isNOT_EQUAL() && c2 == 0) {
                return IfCmp.create(LONG_IFCMP, y.copyRO(), a.copyRO(), LC(c1),
                    ConditionOperand.NOT_EQUAL(), target, prof);
              } else if ((cond.isEQUAL() && c2 == 1)||(cond.isGREATER() && c2 == 0)){
                return IfCmp.create(LONG_IFCMP, y.copyRO(), a.copyRO(), LC(c1),
                    ConditionOperand.GREATER(), target, prof);
              } else if (cond.isGREATER_EQUAL() && c2 == 0){
                return IfCmp.create(LONG_IFCMP, y.copyRO(), a.copyRO(), LC(c1),
                    ConditionOperand.GREATER_EQUAL(), target, prof);
              } else if ((cond.isEQUAL() && c2 == -1)||(cond.isLESS() && c2 == 0)) {
                return IfCmp.create(LONG_IFCMP, y.copyRO(), a.copyRO(), LC(c1),
                    ConditionOperand.LESS(), target, prof);
              } else if (cond.isLESS_EQUAL() && c2 == 0) {
                return IfCmp.create(LONG_IFCMP, y.copyRO(), a.copyRO(), LC(c1),
                    ConditionOperand.LESS_EQUAL(), target, prof);
              }
            }
          }
        }
        return null;
      }
      case LONG_IFCMP_opcode: {
        if (FOLD_LONGS && FOLD_IFCMPS) {
          long c2 = getLongValue(IfCmp.getVal2(s));
          ConditionOperand cond = (ConditionOperand) IfCmp.getCond(s).copy();
          BranchOperand target = (BranchOperand) IfCmp.getTarget(s).copy();
          BranchProfileOperand prof = (BranchProfileOperand) IfCmp.getBranchProfile(s).copy();
          if (cond.isEQUAL() || cond.isNOT_EQUAL()) {
            if (def.operator == LONG_ADD) {
              long c1 = getLongValue(Binary.getVal2(def));
              // x = a + c1; y = x cmp c2
              return IfCmp.create(LONG_IFCMP, y.copyRO(), a.copyRO(), LC(c2 - c1), cond, target, prof);
            } else if (def.operator == LONG_SUB) {
              long c1 = getLongValue(Binary.getVal2(def));
              // x = a - c1; y = x cmp c2
              return IfCmp.create(LONG_IFCMP, y.copyRO(), a.copyRO(), LC(c1 + c2), cond, target, prof);
            } else if (def.operator == LONG_NEG) {
              // x = -a; y = x cmp c2
              return IfCmp.create(LONG_IFCMP, y.copyRO(), a.copyRO(), LC(-c2), cond.flipOperands(), target, prof);
            }
          }
        }
        return null;
      }
      case FLOAT_IFCMP_opcode: {
        if (FOLD_FLOATS && FOLD_IFCMPS) {
          float c2 = getFloatValue(IfCmp.getVal2(s));
          ConditionOperand cond = (ConditionOperand) IfCmp.getCond(s).copy();
          BranchOperand target = (BranchOperand) IfCmp.getTarget(s).copy();
          BranchProfileOperand prof = (BranchProfileOperand) IfCmp.getBranchProfile(s).copy();
          if (def.operator == FLOAT_ADD) {
            float c1 = getFloatValue(Binary.getVal2(def));
            // x = a + c1; y = x cmp c2
            return IfCmp.create(FLOAT_IFCMP, y.copyRO(), a.copyRO(), FC(c2 - c1), cond, target, prof);
          } else if (def.operator == FLOAT_SUB) {
            float c1 = getFloatValue(Binary.getVal2(def));
            // x = a - c1; y = x cmp c2
            return IfCmp.create(FLOAT_IFCMP, y.copyRO(), a.copyRO(), FC(c1 + c2), cond, target, prof);
          } else if (def.operator == FLOAT_NEG) {
            // x = -a; y = x cmp c2
            return IfCmp.create(FLOAT_IFCMP, y.copyRO(), a.copyRO(), FC(-c2), cond.flipOperands(), target, prof);
          }
        }
        return null;
      }
      case DOUBLE_IFCMP_opcode: {
        if (FOLD_DOUBLES && FOLD_IFCMPS) {
          double c2 = getDoubleValue(IfCmp.getVal2(s));
          ConditionOperand cond = (ConditionOperand) IfCmp.getCond(s).copy();
          BranchOperand target = (BranchOperand) IfCmp.getTarget(s).copy();
          BranchProfileOperand prof = (BranchProfileOperand) IfCmp.getBranchProfile(s).copy();
          if (def.operator == DOUBLE_ADD) {
            double c1 = getDoubleValue(Binary.getVal2(def));
            // x = a + c1; y = x cmp c2
            return IfCmp.create(DOUBLE_IFCMP, y.copyRO(), a.copyRO(), DC(c2 - c1), cond, target, prof);
          } else if (def.operator == DOUBLE_SUB) {
            double c1 = getDoubleValue(Binary.getVal2(def));
            // x = a - c1; y = x cmp c2
            return IfCmp.create(DOUBLE_IFCMP, y.copyRO(), a.copyRO(), DC(c1 + c2), cond, target, prof);
          } else if (def.operator == DOUBLE_NEG) {
            // x = -a; y = x cmp c2
            return IfCmp.create(DOUBLE_IFCMP, y.copyRO(), a.copyRO(), DC(-c2), cond.flipOperands(), target, prof);
          }
        }
        return null;
      }
      case REF_IFCMP_opcode: {
        if (FOLD_REFS && FOLD_IFCMPS) {
          Address c2 = getAddressValue(IfCmp.getVal2(s));
          ConditionOperand cond = (ConditionOperand) IfCmp.getCond(s).copy();
          BranchOperand target = (BranchOperand) IfCmp.getTarget(s).copy();
          BranchProfileOperand prof = (BranchProfileOperand) IfCmp.getBranchProfile(s).copy();
          if (cond.isEQUAL() || cond.isNOT_EQUAL()) {
            if ((def.operator == NEW || def.operator == NEWARRAY) && c2.EQ(Address.zero())) {
              // x = new ... ; y = x cmp null
              return IfCmp.create(REF_IFCMP,
                  y.copyRO(),
                  AC(Address.zero()),
                  AC(Address.zero()),
                  cond.flipCode(),
                  target,
                  prof);
            } else if (def.operator == REF_ADD) {
              Address c1 = getAddressValue(Binary.getVal2(def));
              // x = a + c1; y = x cmp c2
              return IfCmp.create(REF_IFCMP,
                                  y.copyRO(),
                                  a.copyRO(),
                                  AC(c2.toWord().minus(c1.toWord()).toAddress()),
                                  cond,
                                  target,
                                  prof);
            } else if (def.operator == REF_SUB) {
              Address c1 = getAddressValue(Binary.getVal2(def));
              // x = a - c1; y = x cmp c2
              return IfCmp.create(REF_IFCMP,
                                  y.copyRO(),
                                  a.copyRO(),
                                  AC(c1.toWord().plus(c2.toWord()).toAddress()),
                                  cond,
                                  target,
                                  prof);
            } else if (def.operator == REF_NEG) {
              // x = -a; y = x cmp c2
              return IfCmp.create(REF_IFCMP,
                                  y.copyRO(),
                                  a.copyRO(),
                                  AC(Word.zero().minus(c2.toWord()).toAddress()),
                                  cond.flipOperands(),
                                  target,
                                  prof);
            }
          }
        }
        return null;
      }
      case INT_IFCMP2_opcode: {
        if (FOLD_INTS && FOLD_IFCMPS) {
          int c2 = getIntValue(IfCmp2.getVal2(s));
          ConditionOperand cond1 = (ConditionOperand) IfCmp2.getCond1(s).copy();
          ConditionOperand cond2 = (ConditionOperand) IfCmp2.getCond2(s).copy();
          BranchOperand target1 = (BranchOperand) IfCmp2.getTarget1(s).copy();
          BranchOperand target2 = (BranchOperand) IfCmp2.getTarget2(s).copy();
          BranchProfileOperand prof1 = (BranchProfileOperand) IfCmp2.getBranchProfile1(s).copy();
          BranchProfileOperand prof2 = (BranchProfileOperand) IfCmp2.getBranchProfile2(s).copy();
          if ((cond1.isEQUAL() || cond1.isNOT_EQUAL())&&(cond2.isEQUAL() || cond2.isNOT_EQUAL())) {
            if (def.operator == INT_ADD) {
              int c1 = getIntValue(Binary.getVal2(def));
              // x = a + c1; y = x cmp c2
              return IfCmp2.create(INT_IFCMP2,
                                   y.copyRO(),
                                   a.copyRO(),
                                   IC(c2 - c1),
                                   cond1,
                                   target1,
                                   prof1,
                                   cond2,
                                   target2,
                                   prof2);
            } else if (def.operator == INT_SUB) {
              int c1 = getIntValue(Binary.getVal2(def));
              // x = a - c1; y = x cmp c2
              return IfCmp2.create(INT_IFCMP2,
                                   y.copyRO(),
                                   a.copyRO(),
                                   IC(c1 + c2),
                                   cond1,
                                   target1,
                                   prof1,
                                   cond2,
                                   target2,
                                   prof2);
            } else if (def.operator == INT_NEG) {
              // x = -a; y = x cmp c2
              return IfCmp2.create(INT_IFCMP2,
                                   y.copyRO(),
                                   a.copyRO(),
                                   IC(-c2),
                                   cond1.flipOperands(),
                                   target1,
                                   prof1,
                                   cond2.flipOperands(),
                                   target2,
                                   prof2);
            }
          }
        }
        return null;
      }

      case INT_COND_MOVE_opcode:
      case LONG_COND_MOVE_opcode:
      case REF_COND_MOVE_opcode:
      case FLOAT_COND_MOVE_opcode:
      case DOUBLE_COND_MOVE_opcode:
      case GUARD_COND_MOVE_opcode: {
        if (FOLD_INTS && FOLD_CONDMOVES) {
          Operand trueValue = CondMove.getTrueValue(s);
          Operand falseValue = CondMove.getFalseValue(s);
          ConditionOperand cond = (ConditionOperand) CondMove.getCond(s).copy();
          boolean isEqualityTest = cond.isEQUAL() || cond.isNOT_EQUAL();
          switch (def.operator.opcode) {
            case INT_ADD_opcode:
              if (isEqualityTest) {
                int c1 = getIntValue(Binary.getVal2(def));
                int c2 = getIntValue(CondMove.getVal2(s));
                // x = a + c1; y = x cmp c2 ? trueValue : falseValue
                return CondMove.create(s.operator(), y.copyRO(), a.copyRO(), IC(c2 - c1), cond, trueValue, falseValue);
              }
              break;
            case LONG_ADD_opcode:
              if (isEqualityTest) {
                long c1 = getLongValue(Binary.getVal2(def));
                long c2 = getLongValue(CondMove.getVal2(s));
                // x = a + c1; y = x cmp c2 ? trueValue : falseValue
                return CondMove.create(s.operator(), y.copyRO(), a.copyRO(), LC(c2 - c1), cond, trueValue, falseValue);
              }
              break;
            case REF_ADD_opcode:
              if (isEqualityTest) {
                Address c1 = getAddressValue(Binary.getVal2(def));
                Address c2 = getAddressValue(CondMove.getVal2(s));
                // x = a + c1; y = x cmp c2 ? trueValue : falseValue
                return CondMove.create(s.operator(),
                    y.copyRO(),
                    a.copyRO(),
                    AC(c2.toWord().minus(c1.toWord()).toAddress()),
                    cond,
                    trueValue,
                    falseValue);
              }
              break;
            case FLOAT_ADD_opcode: {
              float c1 = getFloatValue(Binary.getVal2(def));
              float c2 = getFloatValue(CondMove.getVal2(s));
              // x = a + c1; y = x cmp c2 ? trueValue : falseValue
              return CondMove.create(s.operator(), y.copyRO(), a.copyRO(), FC(c2 - c1), cond, trueValue, falseValue);
            }
            case DOUBLE_ADD_opcode: {
              double c1 = getDoubleValue(Binary.getVal2(def));
              double c2 = getDoubleValue(CondMove.getVal2(s));
              // x = a + c1; y = x cmp c2 ? trueValue : falseValue
              return CondMove.create(s.operator(), y.copyRO(), a.copyRO(), DC(c2 - c1), cond, trueValue, falseValue);
            }
            case INT_SUB_opcode:
              if (isEqualityTest) {
                int c1 = getIntValue(Binary.getVal2(def));
                int c2 = getIntValue(CondMove.getVal2(s));
                // x = a - c1; y = x cmp c2 ? trueValue : falseValue
                return CondMove.create(s.operator(), y.copyRO(), a.copyRO(), IC(c1 + c2), cond, trueValue, falseValue);
              }
              break;
            case LONG_SUB_opcode:
              if (isEqualityTest) {

                long c1 = getLongValue(Binary.getVal2(def));
                long c2 = getLongValue(CondMove.getVal2(s));
                // x = a - c1; y = x cmp c2 ? trueValue : falseValue
                return CondMove.create(s.operator(), y.copyRO(), a.copyRO(), LC(c1 + c2), cond, trueValue, falseValue);
              }
              break;
            case REF_SUB_opcode:
              if (isEqualityTest) {
                Address c1 = getAddressValue(Binary.getVal2(def));
                Address c2 = getAddressValue(CondMove.getVal2(s));
                // x = a - c1; y = x cmp c2 ? trueValue : falseValue
                return CondMove.create(s.operator(),
                    y.copyRO(),
                    a.copyRO(),
                    AC(c1.toWord().plus(c2.toWord()).toAddress()),
                    cond,
                    trueValue,
                    falseValue);
              }
              break;
            case FLOAT_SUB_opcode: {
              float c1 = getFloatValue(Binary.getVal2(def));
              float c2 = getFloatValue(CondMove.getVal2(s));
              // x = a - c1; y = x cmp c2 ? trueValue : falseValue
              return CondMove.create(s.operator(), y.copyRO(), a.copyRO(), FC(c1 + c2), cond, trueValue, falseValue);
            }
            case DOUBLE_SUB_opcode: {
              double c1 = getDoubleValue(Binary.getVal2(def));
              double c2 = getDoubleValue(CondMove.getVal2(s));
              // x = a - c1; y = x cmp c2 ? trueValue : falseValue
              return CondMove.create(s.operator(), y.copyRO(), a.copyRO(), DC(c1 + c2), cond, trueValue, falseValue);
            }
            case INT_NEG_opcode:
              if (isEqualityTest) {
                int c2 = getIntValue(CondMove.getVal2(s));
                // x = -a; y = x cmp c2 ? trueValue : falseValue
                return CondMove.create(s.operator(),
                    y.copyRO(),
                    a.copyRO(),
                    IC(-c2),
                    cond.flipOperands(),
                    trueValue,
                    falseValue);
              }
              break;
            case LONG_NEG_opcode:
              if (isEqualityTest) {
                long c2 = getLongValue(CondMove.getVal2(s));
                // x = -a; y = x cmp c2 ? trueValue : falseValue
                return CondMove.create(s.operator(),
                    y.copyRO(),
                    a.copyRO(),
                    LC(-c2),
                    cond.flipOperands(),
                    trueValue,
                    falseValue);
              }
              break;
            case REF_NEG_opcode:
              if (isEqualityTest) {
                Address c2 = getAddressValue(CondMove.getVal2(s));
                // x = -a; y = x cmp c2 ? trueValue : falseValue
                return CondMove.create(s.operator(),
                    y.copyRO(),
                    a.copyRO(),
                    AC(Word.zero().minus(c2.toWord()).toAddress()),
                    cond.flipOperands(),
                    trueValue,
                    falseValue);
              }
              break;
            case FLOAT_NEG_opcode: {
              float c2 = getFloatValue(CondMove.getVal2(s));
              // x = -a; y = x cmp c2 ? trueValue : falseValue
              return CondMove.create(s.operator(),
                                     y.copyRO(),
                                     a.copyRO(),
                                     FC(-c2),
                                     cond.flipOperands(),
                                     trueValue,
                                     falseValue);
            }
            case DOUBLE_NEG_opcode: {
              double c2 = getDoubleValue(CondMove.getVal2(s));
              // x = -a; y = x cmp c2 ? trueValue : falseValue
              return CondMove.create(s.operator(),
                                     y.copyRO(),
                                     a.copyRO(),
                                     DC(-c2),
                                     cond.flipOperands(),
                                     trueValue,
                                     falseValue);
            }
            case BOOLEAN_CMP_INT_opcode: {
              int c1 = getIntValue(BooleanCmp.getVal2(def));
              int c2 = getIntValue(CondMove.getVal2(s));
              // x = a cmp c1 ? true : false; y = x cmp c2 ? trueValue : falseValue
              if ((cond.isEQUAL() && c2 == 1)||
                  (cond.isNOT_EQUAL() && c2 == 0)) {
                return CondMove.create(s.operator(),
                    y.copyRO(),
                    a.copyRO(),
                    IC(c1),
                    BooleanCmp.getCond(def).copy().asCondition(),
                    trueValue,
                    falseValue);
              } else if ((cond.isEQUAL() && c2 == 0)||
                  (cond.isNOT_EQUAL() && c2 == 1)) {
                return CondMove.create(s.operator(),
                    y.copyRO(),
                    a.copyRO(),
                    IC(c1),
                    BooleanCmp.getCond(def).copy().asCondition().flipCode(),
                    trueValue,
                    falseValue);
              }
              break;
            }
            case BOOLEAN_CMP_ADDR_opcode: {
              Address c1 = getAddressValue(BooleanCmp.getVal2(def));
              int c2 = getIntValue(CondMove.getVal2(s));
              // x = a cmp c1 ? true : false; y = x cmp c2 ? trueValue : falseValue
              if ((cond.isEQUAL() && c2 == 1)||
                  (cond.isNOT_EQUAL() && c2 == 0)) {
                return CondMove.create(s.operator(),
                    y.copyRO(),
                    a.copyRO(),
                    AC(c1),
                    BooleanCmp.getCond(def).copy().asCondition(),
                    trueValue,
                    falseValue);
              } else if ((cond.isEQUAL() && c2 == 0)||
                  (cond.isNOT_EQUAL() && c2 == 1)) {
                return CondMove.create(s.operator(),
                    y.copyRO(),
                    a.copyRO(),
                    AC(c1),
                    BooleanCmp.getCond(def).flipCode(),
                    trueValue,
                    falseValue);
              }
              break;
            }
            case BOOLEAN_CMP_LONG_opcode: {
              long c1 = getLongValue(BooleanCmp.getVal2(def));
              int c2 = getIntValue(CondMove.getVal2(s));
              // x = a cmp c1 ? true : false; y = x cmp c2 ? trueValue : falseValue
              if ((cond.isEQUAL() && c2 == 1)||
                  (cond.isNOT_EQUAL() && c2 == 0)) {
                return CondMove.create(s.operator(),
                    y.copyRO(),
                    a.copyRO(),
                    LC(c1),
                    BooleanCmp.getCond(def).copy().asCondition(),
                    trueValue,
                    falseValue);
              } else if ((cond.isEQUAL() && c2 == 0)||
                  (cond.isNOT_EQUAL() && c2 == 1)) {
                return CondMove.create(s.operator(),
                    y.copyRO(),
                    a.copyRO(),
                    LC(c1),
                    BooleanCmp.getCond(def).copy().asCondition().flipCode(),
                    trueValue,
                    falseValue);
              } else {
                return null;
              }
            }
            case BOOLEAN_CMP_DOUBLE_opcode: {
              double c1 = getDoubleValue(BooleanCmp.getVal2(def));
              int c2 = getIntValue(CondMove.getVal2(s));
              // x = a cmp c1 ? true : false; y = x cmp c2 ? trueValue : falseValue
              if ((cond.isEQUAL() && c2 == 1)||
                  (cond.isNOT_EQUAL() && c2 == 0)) {
                return CondMove.create(s.operator(),
                    y.copyRO(),
                    a.copyRO(),
                    DC(c1),
                    BooleanCmp.getCond(def).copy().asCondition(),
                    trueValue,
                    falseValue);
              } else if ((cond.isEQUAL() && c2 == 0)||
                  (cond.isNOT_EQUAL() && c2 == 1)) {
                return CondMove.create(s.operator(),
                    y.copyRO(),
                    a.copyRO(),
                    DC(c1),
                    BooleanCmp.getCond(def).copy().asCondition().flipCode(),
                    trueValue,
                    falseValue);
              }
              break;
            }
            case BOOLEAN_CMP_FLOAT_opcode: {
              float c1 = getFloatValue(BooleanCmp.getVal2(def));
              int c2 = getIntValue(CondMove.getVal2(s));
              // x = a cmp c1 ? true : false; y = x cmp c2 ? trueValue : falseValue
              if ((cond.isEQUAL() && c2 == 1)||
                  (cond.isNOT_EQUAL() && c2 == 0)) {
                return CondMove.create(s.operator(),
                    y.copyRO(),
                    a.copyRO(),
                    FC(c1),
                    BooleanCmp.getCond(def).copy().asCondition(),
                    trueValue,
                    falseValue);
              } else if ((cond.isEQUAL() && c2 == 0)||
                  (cond.isNOT_EQUAL() && c2 == 1)) {
                return CondMove.create(s.operator(),
                    y.copyRO(),
                    a.copyRO(),
                    FC(c1),
                    BooleanCmp.getCond(def).copy().asCondition().flipCode(),
                    trueValue,
                    falseValue);
              }
              break;
            }
            case LONG_CMP_opcode: {
              long c1 = getLongValue(Binary.getVal2(def));
              int c2 = getIntValue(CondMove.getVal2(s));
              // x = a lcmp c1; y = y = x cmp c2 ? trueValue : falseValue
              if (cond.isEQUAL() && c2 == 0) {
                return CondMove.create(s.operator(),
                    y.copyRO(),
                    a.copyRO(),
                    LC(c1),
                    ConditionOperand.EQUAL(),
                    trueValue,
                    falseValue);
              } else if (cond.isNOT_EQUAL() && c2 == 0) {
                return CondMove.create(s.operator(),
                    y.copyRO(),
                    a.copyRO(),
                    LC(c1),
                    ConditionOperand.NOT_EQUAL(),
                    trueValue,
                    falseValue);
              } else if ((cond.isEQUAL() && c2 == 1)||(cond.isGREATER() && c2 == 0)){
                return CondMove.create(s.operator(),
                    y.copyRO(),
                    a.copyRO(),
                    LC(c1),
                    ConditionOperand.GREATER(),
                    trueValue,
                    falseValue);
              } else if (cond.isGREATER_EQUAL() && c2 == 0){
                return CondMove.create(s.operator(),
                    y.copyRO(),
                    a.copyRO(),
                    LC(c1),
                    ConditionOperand.GREATER_EQUAL(),
                    trueValue,
                    falseValue);
              } else if ((cond.isEQUAL() && c2 == -1)||(cond.isLESS() && c2 == 0)) {
                return CondMove.create(s.operator(),
                    y.copyRO(),
                    a.copyRO(),
                    LC(c1),
                    ConditionOperand.LESS(),
                    trueValue,
                    falseValue);
              } else if (cond.isLESS_EQUAL() && c2 == 0) {
                return CondMove.create(s.operator(),
                    y.copyRO(),
                    a.copyRO(),
                    LC(c1),
                    ConditionOperand.LESS_EQUAL(),
                    trueValue,
                    falseValue);
              }
              break;
            }
            default:
          }
        }
        return null;
      }

      case INT_NEG_opcode: {
        if (FOLD_INTS && FOLD_NEGS) {
          if (def.operator == INT_NEG) {
            // x = -z; y = -x;
            return Move.create(INT_MOVE, y.copyRO(), Unary.getVal(def).copy());
          } else if (def.operator == INT_MUL) {
            int c1 = getIntValue(Binary.getVal2(def));
            // x = a * c1; y = -x;
            return Binary.create(INT_MUL, y.copyRO(), a.copyRO(), IC(-c1));
          } else if (def.operator == INT_DIV) {
            int c1 = getIntValue(GuardedBinary.getVal2(def));
            Operand guard = GuardedBinary.getGuard(def);
            // x = a / c1; y = -x;
            return GuardedBinary.create(INT_DIV, y.copyRO(), a.copyRO(), IC(-c1), guard.copy());
          } else if (FOLD_CONSTANTS_TO_LHS && (def.operator == INT_ADD)) {
            int c1 = getIntValue(Binary.getVal2(def));
            // x = a + c1; y = -x;
            return Binary.create(INT_SUB, y.copyRO(), IC(-c1), a.copyRO());
          } else if (FOLD_CONSTANTS_TO_LHS && (def.operator == INT_SUB)) {
            int c1 = getIntValue(Binary.getVal2(def));
            // x = a - c1; y = -x;
            return Binary.create(INT_SUB, y.copyRO(), IC(c1), a.copyRO());
          }
        }
        return null;
      }

      case REF_NEG_opcode: {
        if (FOLD_REFS && FOLD_NEGS) {
          if (def.operator == REF_NEG) {
            // x = -z; y = -x;
            return Move.create(REF_MOVE, y.copyRO(), Unary.getVal(def).copy());
          } else if (FOLD_CONSTANTS_TO_LHS && (def.operator == REF_ADD)) {
            Address c1 = getAddressValue(Binary.getVal2(def));
            // x = a + c1; y = -x;
            return Binary.create(REF_SUB, y.copyRO(), AC(Word.zero().minus(c1.toWord()).toAddress()), a.copyRO());
          } else if (FOLD_CONSTANTS_TO_LHS && (def.operator == REF_SUB)) {
            Address c1 = getAddressValue(Binary.getVal2(def));
            // x = a - c1; y = -x;
            return Binary.create(REF_SUB, y.copyRO(), AC(c1), a.copyRO());
          }
        }
        return null;
      }

      case LONG_NEG_opcode: {
        if (FOLD_LONGS && FOLD_NEGS) {
          if (def.operator == LONG_NEG) {
            // x = -z; y = -x;
            return Move.create(LONG_MOVE, y.copyRO(), Unary.getVal(def).copy());
          } else if (def.operator == LONG_MUL) {
            long c1 = getLongValue(Binary.getVal2(def));
            // x = a * c1; y = -x;
            return Binary.create(LONG_MUL, y.copyRO(), a.copyRO(), LC(-c1));
          } else if (def.operator == LONG_DIV) {
            long c1 = getLongValue(GuardedBinary.getVal2(def));
            Operand guard = GuardedBinary.getGuard(def);
            // x = a / c1; y = -x;
            return GuardedBinary.create(LONG_DIV, y.copyRO(), a.copyRO(), LC(-c1), guard.copy());
          } else if (FOLD_CONSTANTS_TO_LHS && (def.operator == LONG_ADD)) {
            long c1 = getLongValue(Binary.getVal2(def));
            // x = a + c1; y = -x;
            return Binary.create(LONG_SUB, y.copyRO(), LC(-c1), a.copyRO());
          } else if (FOLD_CONSTANTS_TO_LHS && (def.operator == LONG_SUB)) {
            long c1 = getLongValue(Binary.getVal2(def));
            // x = a - c1; y = -x;
            return Binary.create(LONG_SUB, y.copyRO(), LC(c1), a.copyRO());
          }
        }
        return null;
      }

      case FLOAT_NEG_opcode: {
        if (FOLD_FLOATS && FOLD_NEGS) {
          if (def.operator == FLOAT_NEG) {
            // x = -z; y = -x;
            return Move.create(FLOAT_MOVE, y.copyRO(), Unary.getVal(def).copy());
          } else if (def.operator == FLOAT_MUL) {
            float c1 = getFloatValue(Binary.getVal2(def));
            // x = a * c1; y = -x;
            return Binary.create(FLOAT_MUL, y.copyRO(), a.copyRO(), FC(-c1));
          } else if (def.operator == FLOAT_DIV) {
            float c1 = getFloatValue(Binary.getVal2(def));
            // x = a / c1; y = -x;
            return Binary.create(FLOAT_DIV, y.copyRO(), a.copyRO(), FC(-c1));
          } else if (FOLD_CONSTANTS_TO_LHS && (def.operator == FLOAT_ADD)) {
            float c1 = getFloatValue(Binary.getVal2(def));
            // x = a + c1; y = -x;
            return Binary.create(FLOAT_SUB, y.copyRO(), FC(-c1), a.copyRO());
          } else if (FOLD_CONSTANTS_TO_LHS && (def.operator == FLOAT_SUB)) {
            float c1 = getFloatValue(Binary.getVal2(def));
            // x = a - c1; y = -x;
            return Binary.create(FLOAT_SUB, y.copyRO(), FC(c1), a.copyRO());
          }
        }
        return null;
      }

      case DOUBLE_NEG_opcode: {
        if (FOLD_DOUBLES && FOLD_NEGS) {
          if (def.operator == DOUBLE_NEG) {
            // x = -z; y = -x;
            return Move.create(DOUBLE_MOVE, y.copyRO(), Unary.getVal(def).copy());
          } else if (def.operator == DOUBLE_MUL) {
            double c1 = getDoubleValue(Binary.getVal2(def));
            // x = a * c1; y = -x;
            return Binary.create(DOUBLE_MUL, y.copyRO(), a.copyRO(), DC(-c1));
          } else if (def.operator == DOUBLE_DIV) {
            double c1 = getDoubleValue(Binary.getVal2(def));
            // x = a / c1; y = -x;
            return Binary.create(DOUBLE_DIV, y.copyRO(), a.copyRO(), DC(-c1));
          } else if (FOLD_CONSTANTS_TO_LHS && (def.operator == DOUBLE_ADD)) {
            double c1 = getDoubleValue(Binary.getVal2(def));
            // x = a + c1; y = -x;
            return Binary.create(DOUBLE_SUB, y.copyRO(), DC(-c1), a.copyRO());
          } else if (FOLD_CONSTANTS_TO_LHS && (def.operator == DOUBLE_SUB)) {
            double c1 = getDoubleValue(Binary.getVal2(def));
            // x = a - c1; y = -x;
            return Binary.create(DOUBLE_SUB, y.copyRO(), DC(c1), a.copyRO());
          }
        }
        return null;
      }

      case BOOLEAN_NOT_opcode: {
        if (FOLD_INTS && FOLD_NOTS) {
          if (def.operator == BOOLEAN_NOT) {
            // x = 1 ^ a; y = 1 ^ x;
            return Move.create(INT_MOVE, y.copyRO(), Unary.getVal(def).copy());
          } else if (BooleanCmp.conforms(def)) {
            // x = a cmp b; y = !x
            return BooleanCmp.create(def.operator,
                                     y.copyRO(),
                                     BooleanCmp.getVal1(def).copy(),
                                     BooleanCmp.getVal2(def).copy(),
                                     ((ConditionOperand) BooleanCmp.getCond(def).copy()).flipCode(),
                                     ((BranchProfileOperand) BooleanCmp.getBranchProfile(def).copy()));
          }
        }
        return null;
      }

      case INT_NOT_opcode: {
        if (FOLD_INTS && FOLD_NOTS) {
          if (def.operator == INT_NOT) {
            // x = -1 ^ a; y = -1 ^ x;
            return Move.create(INT_MOVE, y.copyRO(), a.copy());
          }
        }
        return null;
      }

      case REF_NOT_opcode: {
        if (FOLD_REFS && FOLD_NOTS) {
          if (def.operator == REF_NOT) {
            // x = -1 ^ a; y = -1 ^ x;
            return Move.create(REF_MOVE, y.copyRO(), a.copy());
          }
        }
        return null;
      }

      case LONG_NOT_opcode: {
        if (FOLD_LONGS && FOLD_NOTS) {
          if (def.operator == LONG_NOT) {
            // x = -1 ^ a; y = -1 ^ x;
            return Move.create(LONG_MOVE, y.copyRO(), a.copy());
          }
        }
        return null;
      }

      case INT_2BYTE_opcode: {
        if (FOLD_INTS && FOLD_2CONVERSION) {
          if ((def.operator == INT_2BYTE) || (def.operator == INT_2SHORT)) {
            // x = (short)a; y = (byte)x;
            return Unary.create(INT_2BYTE, y.copyRO(), a.copy());
          } else if (def.operator == INT_2USHORT) {
            // x = (char)a; y = (byte)x;
            return Binary.create(INT_AND, y.copyRO(), a.copy(), IC(0xFF));
          }
        }
        return null;
      }
      case INT_2SHORT_opcode: {
        if (FOLD_INTS && FOLD_2CONVERSION) {
          if (def.operator == INT_2BYTE) {
            // x = (byte)a; y = (short)x;
            return Unary.create(INT_2BYTE, y.copyRO(), a.copy());
          } else if (def.operator == INT_2SHORT) {
            // x = (short)a; y = (short)x;
            return Unary.create(INT_2SHORT, y.copyRO(), a.copy());
          } else if (def.operator == INT_2USHORT) {
            // x = (char)a; y = (short)x;
            return Unary.create(INT_2USHORT, y.copyRO(), a.copy());
          }
        }
        return null;
      }
      case INT_2USHORT_opcode: {
        if (FOLD_INTS && FOLD_2CONVERSION) {
          if ((def.operator == INT_2SHORT) || (def.operator == INT_2USHORT)) {
            // x = (short)a; y = (char)x;
            return Unary.create(INT_2USHORT, y.copyRO(), a.copy());
          }
        }
        return null;
      }

      case LONG_2INT_opcode: {
        if (FOLD_LONGS && FOLD_2CONVERSION) {
          if (def.operator == INT_2LONG) {
            // x = (long)a; y = (int)x;
            return Move.create(INT_MOVE, y.copyRO(), a.copy());
          }
        }
        return null;
      }
      case INT_2LONG_opcode:
        // unused
        return null;

      case DOUBLE_2FLOAT_opcode: {
        if (FOLD_DOUBLES && FOLD_2CONVERSION) {
          if (def.operator == FLOAT_2DOUBLE) {
            // x = (double)a; y = (float)x;
            return Move.create(FLOAT_MOVE, y.copyRO(), a.copy());
          }
        }
        return null;
      }

      case FLOAT_2DOUBLE_opcode:
        // unused
        return null;
      case INT_ZERO_CHECK_opcode: {
        if (FOLD_INTS && FOLD_CHECKS) {
          if (def.operator == INT_NEG) {
            // x = -z; y = zerocheck x;
            return ZeroCheck.create(INT_ZERO_CHECK, y.copyRO(), Unary.getVal(def).copy());
          }
        }
        return null;
      }
      case LONG_ZERO_CHECK_opcode: {
        if (FOLD_INTS && FOLD_CHECKS) {
          if (def.operator == INT_NEG) {
            // x = -z; y = zerocheck x;
            return ZeroCheck.create(INT_ZERO_CHECK, y.copyRO(), Unary.getVal(def).copy());
          }
        }
        return null;
      }
      case NEWARRAY_opcode:
        // unused
        return null;
      case BOUNDS_CHECK_opcode: {
        if (FOLD_CHECKS) {
          if (def.operator == NEWARRAY) {
            // x = newarray xxx[c1]; y = boundscheck x, c2;
            int c1 = getIntValue(NewArray.getSize(def));
            int c2 = getIntValue(BoundsCheck.getIndex(s));
            if (c2 >= 0 && c2 < c1) {
              return Move.create(GUARD_MOVE, y.copyRO(), BoundsCheck.getGuard(def).copy());
            }
          }
        }
        return null;
      }
      case NULL_CHECK_opcode: {
        if (FOLD_CHECKS) {
          if (def.operator == NEWARRAY || def.operator == NEW) {
            // x = new xxx; y = nullcheck x;
            return Move.create(GUARD_MOVE, y.copyRO(), new TrueGuardOperand());
          }
        }
        return null;
      }
      case INSTANCEOF_opcode: {
        if (FOLD_CHECKS) {
          TypeReference newType;
          if (def.operator == NEW) {
            // x = new xxx; y = instanceof x, zzz;
            newType = New.getType(def).getTypeRef();
          } else if (def.operator == NEWARRAY) {
            // x = newarray xxx; y = instanceof x, zzz;
            newType = NewArray.getType(def).getTypeRef();
          } else {
            return null;
          }
          TypeReference instanceofType = InstanceOf.getType(s).getTypeRef();
          if (newType == instanceofType) {
            return Move.create(INT_MOVE, y.copyRO(), IC(1));
          } else {
            return Move.create(INT_MOVE, y.copyRO(), IC(RuntimeEntrypoints.isAssignableWith(instanceofType.resolve(), newType.resolve()) ? 1 : 0));
          }
        }
        return null;
      }
      case ARRAYLENGTH_opcode: {
        if (FOLD_CHECKS) {
          if (def.operator() == NEWARRAY) {
            // x = newarray xxx[c1]; y = arraylength x;
            return Move.create(INT_MOVE, y.copyRO(), NewArray.getSize(def).copy());
          }
        }
        return null;
      }
      default:
        OptimizingCompilerException.UNREACHABLE();
        return null;
    }
  }

  /**
   * Does instruction s compute a register r = candidate expression?
   *
   * @param s the instruction
   * @param ssa are we in SSA form?
   * @return the computed register, or {@code null}
   */
  private static Register isCandidateExpression(Instruction s, boolean ssa) {

    switch (s.operator.opcode) {
      // Foldable operators
      case BOOLEAN_NOT_opcode:
      case INT_NOT_opcode:
      case REF_NOT_opcode:
      case LONG_NOT_opcode:

      case INT_NEG_opcode:
      case REF_NEG_opcode:
      case LONG_NEG_opcode:
      case FLOAT_NEG_opcode:
      case DOUBLE_NEG_opcode:

      case INT_2BYTE_opcode:
      case INT_2SHORT_opcode:
      case INT_2USHORT_opcode:
      case INT_2LONG_opcode:
      case LONG_2INT_opcode:
      case FLOAT_2DOUBLE_opcode:
      case DOUBLE_2FLOAT_opcode: {
        Operand val1 = Unary.getVal(s);
        // if val1 is constant too, this should've been constant folded
        // beforehand. Give up.
        if (val1.isConstant()) {
          return null;
        }
        Register result = Unary.getResult(s).asRegister().getRegister();
        if (ssa) {
          return result;
        } else if (val1.asRegister().getRegister() != result) {
          return result;
        } else {
          return null;
        }
      }

      case ARRAYLENGTH_opcode: {
        Operand val1 = GuardedUnary.getVal(s);
        // if val1 is constant too, this should've been constant folded
        // beforehand. Give up.
        if (val1.isConstant()) {
          return null;
        }
        Register result = GuardedUnary.getResult(s).asRegister().getRegister();
        // don't worry about the input and output bring the same as their types differ
        return result;
      }

      case INT_ADD_opcode:
      case REF_ADD_opcode:
      case LONG_ADD_opcode:
      case FLOAT_ADD_opcode:
      case DOUBLE_ADD_opcode:

      case INT_SUB_opcode:
      case REF_SUB_opcode:
      case LONG_SUB_opcode:
      case FLOAT_SUB_opcode:
      case DOUBLE_SUB_opcode:

      case INT_MUL_opcode:
      case LONG_MUL_opcode:
      case FLOAT_MUL_opcode:
      case DOUBLE_MUL_opcode:

      case FLOAT_DIV_opcode:
      case DOUBLE_DIV_opcode:

      case INT_SHL_opcode:
      case REF_SHL_opcode:
      case LONG_SHL_opcode:

      case INT_SHR_opcode:
      case REF_SHR_opcode:
      case LONG_SHR_opcode:

      case INT_USHR_opcode:
      case REF_USHR_opcode:
      case LONG_USHR_opcode:

      case INT_AND_opcode:
      case REF_AND_opcode:
      case LONG_AND_opcode:

      case INT_OR_opcode:
      case REF_OR_opcode:
      case LONG_OR_opcode:

      case INT_XOR_opcode:
      case REF_XOR_opcode:
      case LONG_XOR_opcode:

      case LONG_CMP_opcode:
      case FLOAT_CMPL_opcode:
      case DOUBLE_CMPL_opcode:
      case FLOAT_CMPG_opcode:
      case DOUBLE_CMPG_opcode: {

        Operand val2 = Binary.getVal2(s);
        if (!val2.isObjectConstant() && !val2.isTIBConstant()) {
          if (val2.isConstant()) {
            Operand val1 = Binary.getVal1(s);
            // if val1 is constant too, this should've been constant folded
            // beforehand. Give up.
            if (val1.isConstant()) {
              return null;
            }

            Register result = Binary.getResult(s).asRegister().getRegister();
            if (ssa) {
              return result;
            } else if (val1.asRegister().getRegister() != result) {
              return result;
            } else {
              return null;
            }
          } else {
            if (VM.VerifyAssertions) {
              VM._assert(val2.isRegister());
            }

            Operand val1 = Binary.getVal1(s);
            if (s.operator.isCommutative() && val1.isConstant() && !val1.isMovableObjectConstant() && !val1.isTIBConstant()) {
              Binary.setVal1(s, Binary.getClearVal2(s));
              Binary.setVal2(s, val1);
              Register result = Binary.getResult(s).asRegister().getRegister();
              if (ssa) {
                return result;
              } else if (val2.asRegister().getRegister() != result) {
                return result;
              } else {
                return null;
              }
            }
          }
        }
        return null;
      }

      case INT_DIV_opcode:
      case LONG_DIV_opcode: {
        Operand val2 = GuardedBinary.getVal2(s);
        if (val2.isConstant()) {
          Operand val1 = GuardedBinary.getVal1(s);
          // if val1 is constant too, this should've been constant folded
          // beforehand. Give up.
          if (val1.isConstant()) {
            return null;
          }
          Register result = GuardedBinary.getResult(s).asRegister().getRegister();
          if (ssa) {
            return result;
          } else if (val1.asRegister().getRegister() != result) {
            return result;
          }
        }
        return null;
      }

      case BOOLEAN_CMP_INT_opcode:
      case BOOLEAN_CMP_LONG_opcode:
      case BOOLEAN_CMP_ADDR_opcode: {
        Operand val2 = BooleanCmp.getVal2(s);
        if (val2.isConstant() && !val2.isMovableObjectConstant() && !val2.isTIBConstant()) {
          Operand val1 = BooleanCmp.getVal1(s);
          // if val1 is constant too, this should've been constant folded
          // beforehand. Give up.
          if (val1.isConstant()) {
            return null;
          }
          Register result = BooleanCmp.getResult(s).asRegister().getRegister();
          if (ssa) {
            return result;
          } else if (val1.asRegister().getRegister() != result) {
            return result;
          }
        } else if (val2.isRegister()) {
          Operand val1 = BooleanCmp.getVal1(s);
          if (val1.isConstant() && !val1.isMovableObjectConstant() && !val1.isTIBConstant()) {
            BooleanCmp.setVal1(s, BooleanCmp.getClearVal2(s));
            BooleanCmp.setVal2(s, val1);
            BooleanCmp.getCond(s).flipOperands();
            Register result = BooleanCmp.getResult(s).asRegister().getRegister();
            if (ssa) {
              return result;
            } else if (val2.asRegister().getRegister() != result) {
              return result;
            }
          }
        }
        return null;
      }
      case INT_IFCMP_opcode:
      case LONG_IFCMP_opcode:
      case FLOAT_IFCMP_opcode:
      case DOUBLE_IFCMP_opcode:
      case REF_IFCMP_opcode: {
        Operand val2 = IfCmp.getVal2(s);
        if (!val2.isObjectConstant() && !val2.isTIBConstant()) {
          if (val2.isConstant()) {
            Operand val1 = IfCmp.getVal1(s);
            // if val1 is constant too, this should've been constant folded
            // beforehand. Give up.
            if (val1.isConstant()) {
              return null;
            }

            Register result = IfCmp.getGuardResult(s).asRegister().getRegister();
            if (ssa) {
              return result;
            } else if (val1.asRegister().getRegister() != result) {
              return result;
            }
          } else {
            if (VM.VerifyAssertions) {
              VM._assert(val2.isRegister());
            }
            Operand val1 = IfCmp.getVal1(s);
            if (val1.isConstant() && !val1.isMovableObjectConstant() && !val1.isTIBConstant()) {
              IfCmp.setVal1(s, IfCmp.getClearVal2(s));
              IfCmp.setVal2(s, val1);
              IfCmp.getCond(s).flipOperands();
              Register result = IfCmp.getGuardResult(s).asRegister().getRegister();
              if (ssa) {
                return result;
              } else if (val2.asRegister().getRegister() != result) {
                return result;
              }
            }
          }
        }
        return null;
      }
      case INT_IFCMP2_opcode: {
        Operand val2 = IfCmp2.getVal2(s);
        if (!val2.isObjectConstant() && !val2.isTIBConstant()) {
          if (val2.isConstant()) {
            Operand val1 = IfCmp2.getVal1(s);
            // if val1 is constant too, this should've been constant folded
            // beforehand. Give up.
            if (val1.isConstant()) {
              return null;
            }

            Register result = IfCmp2.getGuardResult(s).asRegister().getRegister();
            if (ssa) {
              return result;
            } else if (val1.asRegister().getRegister() != result) {
              return result;
            }
          } else {
            if (VM.VerifyAssertions) {
              VM._assert(val2.isRegister());
            }
            Operand val1 = IfCmp2.getVal1(s);
            if (val1.isConstant() && !val1.isMovableObjectConstant() && !val1.isTIBConstant()) {
              IfCmp2.setVal1(s, IfCmp2.getClearVal2(s));
              IfCmp2.setVal2(s, val1);
              IfCmp2.getCond1(s).flipOperands();
              IfCmp2.getCond2(s).flipOperands();
              Register result = IfCmp2.getGuardResult(s).asRegister().getRegister();
              if (ssa) {
                return result;
              } else if (val2.asRegister().getRegister() != result) {
                return result;
              }
            }
          }
        }
        return null;
      }
      case INT_COND_MOVE_opcode:
      case LONG_COND_MOVE_opcode:
      case REF_COND_MOVE_opcode:
      case FLOAT_COND_MOVE_opcode:
      case DOUBLE_COND_MOVE_opcode:
      case GUARD_COND_MOVE_opcode: {
        Operand val2 = CondMove.getVal2(s);
        if (!val2.isObjectConstant()) {
          if (val2.isConstant()) {
            Operand val1 = CondMove.getVal1(s);
            // if val1 is constant too, this should've been constant folded
            // beforehand. Give up.
            if (val1.isConstant()) {
              return null;
            }
            Register result = CondMove.getResult(s).asRegister().getRegister();
            if (ssa) {
              return result;
            } else if (val1.asRegister().getRegister() != result) {
              return result;
            }
          } else {
            if (VM.VerifyAssertions) {
              VM._assert(val2.isRegister());
            }
            Operand val1 = CondMove.getVal1(s);
            if (val1.isConstant() && !val1.isMovableObjectConstant()) {
              CondMove.setVal1(s, CondMove.getClearVal2(s));
              CondMove.setVal2(s, val1);
              CondMove.getCond(s).flipOperands();
              Register result = CondMove.getResult(s).asRegister().getRegister();
              if (ssa) {
                return result;
              } else if (val2.asRegister().getRegister() != result) {
                return result;
              }
            }
          }
        }
        return null;
      }
      case BOUNDS_CHECK_opcode: {
        Operand ref = BoundsCheck.getRef(s);
        Operand index = BoundsCheck.getIndex(s);
        if (index.isConstant()) {
          if (ref.isConstant()) {
            // this should have been constant folded. Give up.
            return null;
          }
          // don't worry about the input and output bring the same as their types differ
          return BoundsCheck.getGuardResult(s).asRegister().getRegister();
        }
        return null;
      }
      case NULL_CHECK_opcode: {
        Operand ref = NullCheck.getRef(s);
        if (ref.isConstant()) {
          // this should have been constant folded. Give up.
          return null;
        }
        // don't worry about the input and output bring the same as their types differ
        return NullCheck.getGuardResult(s).asRegister().getRegister();
      }
      case INSTANCEOF_opcode: {
        Operand ref = InstanceOf.getRef(s);
        if (ref.isConstant()) {
          // this should have been constant folded. Give up.
          return null;
        }
        // don't worry about the input and output bring the same as their types differ
        return InstanceOf.getResult(s).getRegister();
      }
      case NEWARRAY_opcode: {
        Operand size = NewArray.getSize(s);
        if (size.isConstant()) {
          // don't worry about the input and output bring the same as their types differ
          return NewArray.getResult(s).getRegister();
        }
        return null;
      }
      case NEW_opcode: {
        return New.getResult(s).getRegister();
      }
      case INT_ZERO_CHECK_opcode:
      case LONG_ZERO_CHECK_opcode:  {
        Operand val1 = ZeroCheck.getValue(s);
        // if val1 is constant, this should've been constant folded
        // beforehand. Give up.
        if (val1.isConstant()) {
          return null;
        }
        // don't worry about the input and output bring the same as their types differ
        return ZeroCheck.getGuardResult(s).asRegister().getRegister();
      }
      default:
        // Operator can't be folded
        return null;
    }
  }

  private static int getIntValue(Operand op) {
    if (op instanceof IntConstantOperand) {
      return op.asIntConstant().value;
    }
    if (VM.BuildFor32Addr) {
      return getAddressValue(op).toInt();
    }
    throw new OptimizingCompilerException(
        "Cannot getIntValue from this operand " + op +
        " of instruction " + op.instruction);
  }

  private static long getLongValue(Operand op) {
    if (op instanceof LongConstantOperand)
      return op.asLongConstant().value;
    if (VM.BuildFor64Addr) {
      return getAddressValue(op).toLong();
    }
    throw new OptimizingCompilerException(
        "Cannot getLongValue from this operand " + op +
        " of instruction " + op.instruction);
  }

  private static float getFloatValue(Operand op) {
    if (op instanceof FloatConstantOperand)
      return op.asFloatConstant().value;
    throw new OptimizingCompilerException(
        "Cannot getFloatValue from this operand " + op +
        " of instruction " + op.instruction);
  }

  private static double getDoubleValue(Operand op) {
    if (op instanceof DoubleConstantOperand)
      return op.asDoubleConstant().value;
    throw new OptimizingCompilerException(
        "Cannot getDoubleValue from this operand " + op +
        " of instruction " + op.instruction);
  }

  private static Address getAddressValue(Operand op) {
    if (op instanceof NullConstantOperand) {
      return Address.zero();
    }
    if (op instanceof AddressConstantOperand) {
      return op.asAddressConstant().value;
    }
    if (op instanceof IntConstantOperand) {
      return Address.fromIntSignExtend(op.asIntConstant().value);
    }
    if (VM.BuildFor64Addr && op instanceof LongConstantOperand) {
      return Address.fromLong(op.asLongConstant().value);
    }
    if (op instanceof ObjectConstantOperand) {
      if (VM.VerifyAssertions) VM._assert(!op.isMovableObjectConstant());
      return Magic.objectAsAddress(op.asObjectConstant().value);
    }
    throw new OptimizingCompilerException(
        "Cannot getAddressValue from this operand " + op +
        " of instruction " + op.instruction);
  }
}
