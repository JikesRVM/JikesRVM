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

import java.lang.reflect.Constructor;
import java.util.ArrayList;
import org.jikesrvm.VM;
import org.jikesrvm.compilers.opt.driver.CompilerPhase;
import org.jikesrvm.compilers.opt.ir.Binary;
import org.jikesrvm.compilers.opt.ir.BoundsCheck;
import org.jikesrvm.compilers.opt.ir.Call;
import org.jikesrvm.compilers.opt.ir.GetField;
import org.jikesrvm.compilers.opt.ir.GetStatic;
import org.jikesrvm.compilers.opt.ir.GuardResultCarrier;
import org.jikesrvm.compilers.opt.ir.GuardedBinary;
import org.jikesrvm.compilers.opt.ir.GuardedUnary;
import org.jikesrvm.compilers.opt.ir.InstanceOf;
import org.jikesrvm.compilers.opt.ir.LocationCarrier;
import org.jikesrvm.compilers.opt.ir.Move;
import org.jikesrvm.compilers.opt.ir.NullCheck;
import org.jikesrvm.compilers.opt.ir.BasicBlock;
import org.jikesrvm.compilers.opt.ir.IR;
import org.jikesrvm.compilers.opt.ir.IRTools;
import org.jikesrvm.compilers.opt.ir.Instruction;
import org.jikesrvm.compilers.opt.ir.InstructionFormat;
import org.jikesrvm.compilers.opt.ir.OperandEnumeration;
import org.jikesrvm.compilers.opt.ir.Operator;
import static org.jikesrvm.compilers.opt.ir.Operators.BOUNDS_CHECK_opcode;
import static org.jikesrvm.compilers.opt.ir.Operators.GUARD_MOVE;
import static org.jikesrvm.compilers.opt.ir.Operators.INT_ZERO_CHECK_opcode;
import static org.jikesrvm.compilers.opt.ir.Operators.LONG_ZERO_CHECK_opcode;
import static org.jikesrvm.compilers.opt.ir.Operators.MONITORENTER_opcode;
import static org.jikesrvm.compilers.opt.ir.Operators.MONITOREXIT_opcode;
import static org.jikesrvm.compilers.opt.ir.Operators.NULL_CHECK_opcode;
import static org.jikesrvm.compilers.opt.ir.Operators.READ_CEILING_opcode;
import static org.jikesrvm.compilers.opt.ir.Operators.REF_MOVE;
import static org.jikesrvm.compilers.opt.ir.Operators.TRAP_IF_opcode;
import static org.jikesrvm.compilers.opt.ir.Operators.WRITE_FLOOR_opcode;
import org.jikesrvm.compilers.opt.ir.Register;
import org.jikesrvm.compilers.opt.ir.PutField;
import org.jikesrvm.compilers.opt.ir.PutStatic;
import org.jikesrvm.compilers.opt.ir.ResultCarrier;
import org.jikesrvm.compilers.opt.ir.TrapIf;
import org.jikesrvm.compilers.opt.ir.TypeCheck;
import org.jikesrvm.compilers.opt.ir.Unary;
import org.jikesrvm.compilers.opt.ir.ZeroCheck;
import org.jikesrvm.compilers.opt.ir.operand.IntConstantOperand;
import org.jikesrvm.compilers.opt.ir.operand.LocationOperand;
import org.jikesrvm.compilers.opt.ir.operand.Operand;
import org.jikesrvm.compilers.opt.ir.operand.RegisterOperand;
import org.jikesrvm.compilers.opt.ir.operand.TrapCodeOperand;

/**
 * Perform local common-subexpression elimination for a factored basic
 * block.
 * <ul>
 *   <li> Note: this module also performs scalar replacement of loads
 *   <li> Note: this module also performs elimination of redundant
 *         nullchecks, boundchecks, and zero checks.
 * </ul>
 * Algorithm: Muchnick pp.379-385
 */
public class LocalCSE extends CompilerPhase {
  private final boolean isHIR;

  /**
   * Constructor
   */
  public LocalCSE(boolean isHIR) {
    super(new Object[]{isHIR});
    this.isHIR = isHIR;
  }

  /**
   * Constructor for this compiler phase
   */
  private static final Constructor<CompilerPhase> constructor =
      getCompilerPhaseConstructor(LocalCSE.class, new Class[]{Boolean.TYPE});

  /**
   * Get a constructor object for this compiler phase
   * @return compiler phase constructor
   */
  @Override
  public Constructor<CompilerPhase> getClassConstructor() {
    return constructor;
  }

  @Override
  public final void reportAdditionalStats() {
    VM.sysWrite("  ");
    VM.sysWrite(container.counter1 / container.counter2 * 100, 2);
    VM.sysWrite("% Infrequent BBs");
  }

  @Override
  public final boolean shouldPerform(OptOptions options) {
    return options.LOCAL_CSE;
  }

  @Override
  public final String getName() {
    return "Local CSE";
  }

  /**
   * Perform Local CSE for a method.
   *
   * @param ir the IR to optimize
   */
  @Override
  public final void perform(IR ir) {
    // iterate over each basic block
    for (BasicBlock bb = ir.firstBasicBlockInCodeOrder(); bb != null; bb = bb.nextBasicBlockInCodeOrder()) {
      if (bb.isEmpty()) continue;
      container.counter2++;
      if (bb.getInfrequent()) {
        container.counter1++;
        if (ir.options.FREQ_FOCUS_EFFORT) continue;
      }
      if (isHIR) {
        optimizeBasicBlockHIR(ir, bb);
      } else {
        optimizeBasicBlockLIR(ir, bb);
      }
    }
  }

  /**
   * Perform Local CSE for a basic block in HIR.
   *
   * @param ir the method's ir
   * @param bb the basic block
   */
  private void optimizeBasicBlockHIR(IR ir, BasicBlock bb) {
    AvExCache cache = new AvExCache(ir.options, true);
    // iterate over all instructions in the basic block
    for (Instruction inst = bb.firstRealInstruction(),
        sentinel = bb.lastInstruction(),
        nextInstr = null; inst != sentinel; inst = nextInstr) {
      nextInstr = inst.nextInstructionInCodeOrder(); // cache before we
      // mutate prev/next links
      // 1. try and replace this instruction according to
      // available expressions in the cache, and update cache
      // accordingly.
      if (isLoadInstruction(inst)) {
        loadHelper(ir, cache, inst);
      } else if (isStoreInstruction(inst)) {
        storeHelper(cache, inst);
      } else if (isExpression(inst)) {
        expressionHelper(ir, cache, inst);
      } else if (isCheck(inst)) {
        checkHelper(ir, cache, inst);
      } else if (isTypeCheck(inst)) {
        typeCheckHelper(ir, cache, inst);
      }

      // 2. update the cache according to which expressions this
      // instruction kills
      cache.eliminate(inst);
      // Non-pure CALL instructions and synchronizations KILL all memory locations!
      if (inst.isNonPureCall()) {
        cache.invalidateAllLoads();
      } else if (isSynchronizing(inst) || inst.isDynamicLinkingPoint()) {
        cache.invalidateAllLoads();
      }
    }
  }

  /**
   * Perform Local CSE for a basic block in LIR.
   *
   * @param ir the method's ir
   * @param bb the basic block
   */
  private void optimizeBasicBlockLIR(IR ir, BasicBlock bb) {
    AvExCache cache = new AvExCache(ir.options, false);
    // iterate over all instructions in the basic block
    for (Instruction inst = bb.firstRealInstruction(),
        sentinel = bb.lastInstruction(),
        nextInstr = null; inst != sentinel; inst = nextInstr) {
      nextInstr = inst.nextInstructionInCodeOrder(); // cache before we
      // mutate prev/next links
      // 1. try and replace this instruction according to
      // available expressions in the cache, and update cache
      // accordingly.
      if (isExpression(inst)) {
        expressionHelper(ir, cache, inst);
      } else if (isCheck(inst)) {
        checkHelper(ir, cache, inst);
      }

      // 2. update the cache according to which expressions this
      // instruction kills
      cache.eliminate(inst);
    }
  }

  /**
   * Is a given instruction a CSE-able load?
   */
  public static boolean isLoadInstruction(Instruction s) {
    return GetField.conforms(s) || GetStatic.conforms(s);
  }

  /**
   * Is a given instruction a CSE-able store?
   */
  public static boolean isStoreInstruction(Instruction s) {
    return PutField.conforms(s) || PutStatic.conforms(s);
  }

  /**
   * Does the instruction compute some expression?
   *
   * @param inst the instruction in question
   * @return true or false, as appropriate
   */
  private boolean isExpression(Instruction inst) {
    if (inst.isDynamicLinkingPoint()) return false;
    switch (inst.operator.format) {
      case InstructionFormat.Unary_format:
      case InstructionFormat.GuardedUnary_format:
      case InstructionFormat.Binary_format:
      case InstructionFormat.GuardedBinary_format:
      case InstructionFormat.InstanceOf_format:
        return true;
      case InstructionFormat.Call_format:
        return inst.isPureCall();
      default:
        return false;
    }
  }

  /**
   * Is the given instruction a check instruction?
   *
   * @param inst the instruction in question
   * @return true or false, as appropriate
   */
  private boolean isCheck(Instruction inst) {
    switch (inst.getOpcode()) {
      case NULL_CHECK_opcode:
      case BOUNDS_CHECK_opcode:
      case INT_ZERO_CHECK_opcode:
      case LONG_ZERO_CHECK_opcode:
        return true;
      case TRAP_IF_opcode:
        TrapCodeOperand tc = TrapIf.getTCode(inst);
        return tc.isNullPtr() || tc.isArrayBounds() || tc.isDivByZero();
      default:
        return false;
    }
  }

  private boolean isTypeCheck(Instruction inst) {
    return TypeCheck.conforms(inst);
  }

  /**
   * Process a load instruction
   *
   * @param ir the containing IR object.
   * @param cache the cache of available expressions
   * @param inst the instruction begin processed
   */
  private void loadHelper(IR ir, AvExCache cache, Instruction inst) {
    LocationOperand loc = LocationCarrier.getLocation(inst);
    if (loc.mayBeVolatile()) return; // don't optimize volatile fields

    // look up the expression in the cache
    AvailableExpression ae = cache.find(inst);
    if (ae != null) {
      RegisterOperand dest = ResultCarrier.getClearResult(inst);
      if (ae.tmp == null) {
        // (1) generate a new temporary, and store in the AE cache
        RegisterOperand newRes = ir.regpool.makeTemp(dest.getType());
        ae.tmp = newRes.getRegister();
        // (2) get the CSE value into newRes
        if (ae.isLoad()) {
          // the first appearance was a load.
          // Modify the first load to assign its result to a new temporary
          // and then insert a move from the new temporary to the old result
          // after the mutated first load.
          RegisterOperand res = ResultCarrier.getClearResult(ae.inst);
          ResultCarrier.setResult(ae.inst, newRes);
          ae.inst.insertAfter(Move.create(getMoveOp(res), res, newRes.copyD2U()));
        } else {
          // the first appearance was a store.
          // Insert a move that assigns the value to newRes before
          // the store instruction.
          Operand value;
          if (PutStatic.conforms(ae.inst)) {
            value = PutStatic.getValue(ae.inst);
          } else {
            value = PutField.getValue(ae.inst);
          }
          ae.inst.insertBefore(Move.create(getMoveOp(newRes), newRes, value.copy()));
        }
        // (3) replace second load with a move from the new temporary
        Move.mutate(inst, getMoveOp(dest), dest, newRes.copyD2U());
      } else {
        // already have a temp. replace the load with a move
        RegisterOperand newRes = new RegisterOperand(ae.tmp, dest.getType());
        Move.mutate(inst, getMoveOp(dest), dest, newRes);
      }
    } else {
      // did not find a match: insert new entry in cache
      cache.insert(inst);
    }
  }

  /**
   * Process a store instruction
   *
   * @param cache the cache of available expressions
   * @param inst the instruction begin processed
   */
  private void storeHelper(AvExCache cache, Instruction inst) {
    LocationOperand loc = LocationCarrier.getLocation(inst);
    if (loc.mayBeVolatile()) return; // don't optimize volatile fields

    // look up the expression in the cache
    AvailableExpression ae = cache.find(inst);
    if (ae == null) {
      // did not find a match: insert new entry in cache
      cache.insert(inst);
    }
  }

  /**
   * Process a unary or binary expression.
   *
   * @param ir the containing IR object
   * @param cache the cache of available expressions
   * @param inst the instruction begin processed
   */
  private void expressionHelper(IR ir, AvExCache cache, Instruction inst) {
    // look up the expression in the cache
    AvailableExpression ae = cache.find(inst);
    if (ae != null) {
      RegisterOperand dest = ResultCarrier.getClearResult(inst);
      if (ae.tmp == null) {
        // (1) generate a new temporary, and store in the AE cache
        RegisterOperand newRes = ir.regpool.makeTemp(dest.getType());
        ae.tmp = newRes.getRegister();
        // (2) Modify ae.inst to assign its result to the new temporary
        // and then insert a move from the new temporary to the old result
        // of ae.inst after ae.inst.
        RegisterOperand res = ResultCarrier.getClearResult(ae.inst);
        ResultCarrier.setResult(ae.inst, newRes);
        ae.inst.insertAfter(Move.create(getMoveOp(res), res, newRes.copyD2U()));
        // (3) replace inst with a move from the new temporary
        Move.mutate(inst, getMoveOp(dest), dest, newRes.copyD2U());
      } else {
        // already have a temp. replace inst with a move
        RegisterOperand newRes = new RegisterOperand(ae.tmp, dest.getType());
        Move.mutate(inst, getMoveOp(dest), dest, newRes);
      }
    } else {
      // did not find a match: insert new entry in cache
      cache.insert(inst);
    }
  }

  /**
   * Process a check instruction
   *
   * @param cache the cache of available expressions
   * @param inst the instruction begin processed
   */
  private void checkHelper(IR ir, AvExCache cache, Instruction inst) {
    // look up the check in the cache
    AvailableExpression ae = cache.find(inst);
    if (ae != null) {
      RegisterOperand dest = GuardResultCarrier.getClearGuardResult(inst);
      if (ae.tmp == null) {
        // generate a new temporary, and store in the AE cache
        RegisterOperand newRes = ir.regpool.makeTemp(dest.getType());
        ae.tmp = newRes.getRegister();
        // (2) Modify ae.inst to assign its guard result to the new temporary
        // and then insert a guard move from the new temporary to the
        // old guard result of ae.inst after ae.inst.
        RegisterOperand res = GuardResultCarrier.getClearGuardResult(ae.inst);
        GuardResultCarrier.setGuardResult(ae.inst, newRes);
        ae.inst.insertAfter(Move.create(GUARD_MOVE, res, newRes.copyD2U()));
        // (3) replace inst with a move from the new temporary
        Move.mutate(inst, GUARD_MOVE, dest, newRes.copyD2U());
      } else {
        // already have a temp. replace inst with a guard move
        RegisterOperand newRes = new RegisterOperand(ae.tmp, dest.getType());
        Move.mutate(inst, GUARD_MOVE, dest, newRes);
      }
    } else {
      // did not find a match: insert new entry in cache
      cache.insert(inst);
    }
  }

  /**
   * Process a type check instruction
   *
   * @param ir     Unused
   * @param cache  The cache of available expressions.
   * @param inst   The instruction being processed
   */
  private static void typeCheckHelper(IR ir, AvExCache cache, Instruction inst) {
    // look up the check in the cache
    AvailableExpression ae = cache.find(inst);
    if (ae != null) {
      // it's a duplicate; blow it away.
      Move.mutate(inst, REF_MOVE, TypeCheck.getClearResult(inst), TypeCheck.getClearRef(inst));
    } else {
      // did not find a match: insert new entry in cache
      cache.insert(inst);
    }
  }

  private static Operator getMoveOp(RegisterOperand r) {
    return IRTools.getMoveOp(r.getType());
  }

  /**
   * Is this a synchronizing instruction?
   *
   * @param inst the instruction in question
   */
  private static boolean isSynchronizing(Instruction inst) {
    switch (inst.getOpcode()) {
      case MONITORENTER_opcode:
      case MONITOREXIT_opcode:
      case READ_CEILING_opcode:
      case WRITE_FLOOR_opcode:
        return true;
      default:
        return false;
    }
  }

  /**
   * Implements a cache of Available Expressions
   */
  protected static final class AvExCache {
    /** Implementation of the cache */
    private final ArrayList<AvailableExpression> cache = new ArrayList<AvailableExpression>(3);

    private final OptOptions options;
    private final boolean doMemory;

    AvExCache(OptOptions opts, boolean doMem) {
      options = opts;
      doMemory = doMem;
    }

    /**
     * Find and return a matching available expression.
     *
     * @param inst the instruction to match
     * @return the matching AE if found, null otherwise
     */
    public AvailableExpression find(Instruction inst) {
      Operator opr = inst.operator();
      Operand[] ops = null;
      LocationOperand location = null;
      switch (inst.operator.format) {
        case InstructionFormat.GetField_format:
          if (VM.VerifyAssertions) VM._assert(doMemory);
          ops = new Operand[]{GetField.getRef(inst)};
          location = GetField.getLocation(inst);
          break;
        case InstructionFormat.GetStatic_format:
          if (VM.VerifyAssertions) VM._assert(doMemory);
          location = GetStatic.getLocation(inst);
          break;
        case InstructionFormat.PutField_format:
          if (VM.VerifyAssertions) VM._assert(doMemory);
          ops = new Operand[]{PutField.getRef(inst)};
          location = PutField.getLocation(inst);
          break;
        case InstructionFormat.PutStatic_format:
          if (VM.VerifyAssertions) VM._assert(doMemory);
          location = PutStatic.getLocation(inst);
          break;
        case InstructionFormat.Unary_format:
          ops = new Operand[]{Unary.getVal(inst)};
          break;
        case InstructionFormat.GuardedUnary_format:
          ops = new Operand[]{GuardedUnary.getVal(inst)};
          break;
        case InstructionFormat.Binary_format:
          ops = new Operand[]{Binary.getVal1(inst), Binary.getVal2(inst)};
          break;
        case InstructionFormat.GuardedBinary_format:
          ops = new Operand[]{GuardedBinary.getVal1(inst), GuardedBinary.getVal2(inst)};
          break;
        case InstructionFormat.Move_format:
          ops = new Operand[]{Move.getVal(inst)};
          break;
        case InstructionFormat.NullCheck_format:
          ops = new Operand[]{NullCheck.getRef(inst)};
          break;
        case InstructionFormat.ZeroCheck_format:
          ops = new Operand[]{ZeroCheck.getValue(inst)};
          break;
        case InstructionFormat.BoundsCheck_format:
          ops = new Operand[]{BoundsCheck.getRef(inst), BoundsCheck.getIndex(inst)};
          break;
        case InstructionFormat.TrapIf_format:
          ops = new Operand[]{TrapIf.getVal1(inst), TrapIf.getVal2(inst), TrapIf.getTCode(inst)};
          break;
        case InstructionFormat.TypeCheck_format:
          ops = new Operand[]{TypeCheck.getRef(inst), TypeCheck.getType(inst)};
          break;
        case InstructionFormat.InstanceOf_format:
          ops = new Operand[]{InstanceOf.getRef(inst), InstanceOf.getType(inst)};
          break;
        case InstructionFormat.Call_format:
          int numParams = Call.getNumberOfParams(inst);
          ops = new Operand[numParams+2];
          ops[0] = Call.getAddress(inst);
          ops[1] = Call.getMethod(inst);
          for (int i=0; i < numParams; i++) {
            ops[i+2] = Call.getParam(inst, i);
          }
          break;
        default:
          throw new OptimizingCompilerException("Unsupported type " + inst);
      }

      AvailableExpression ae = new AvailableExpression(inst, opr, ops, location, null);
      int index = cache.indexOf(ae);
      if (index == -1) {
        return null;
      }
      return cache.get(index);
    }

    /**
     * Insert a new available expression in the cache
     *
     * @param inst the instruction that defines the AE
     */
    public void insert(Instruction inst) {
      Operator opr = inst.operator();
      Operand[] ops = null;
      LocationOperand location = null;

      switch (inst.operator.format) {
        case InstructionFormat.GetField_format:
          if (VM.VerifyAssertions) VM._assert(doMemory);
          ops = new Operand[]{GetField.getRef(inst)};
          location = GetField.getLocation(inst);
          break;
        case InstructionFormat.GetStatic_format:
          if (VM.VerifyAssertions) VM._assert(doMemory);
          location = GetStatic.getLocation(inst);
          break;
        case InstructionFormat.PutField_format:
          if (VM.VerifyAssertions) VM._assert(doMemory);
          ops = new Operand[]{PutField.getRef(inst)};
          location = PutField.getLocation(inst);
          break;
        case InstructionFormat.PutStatic_format:
          if (VM.VerifyAssertions) VM._assert(doMemory);
          location = PutStatic.getLocation(inst);
          break;
        case InstructionFormat.Unary_format:
          ops = new Operand[]{Unary.getVal(inst)};
          break;
        case InstructionFormat.GuardedUnary_format:
          ops = new Operand[]{GuardedUnary.getVal(inst)};
          break;
        case InstructionFormat.Binary_format:
          ops = new Operand[]{Binary.getVal1(inst), Binary.getVal2(inst)};
          break;
        case InstructionFormat.GuardedBinary_format:
          ops = new Operand[]{GuardedBinary.getVal1(inst), GuardedBinary.getVal2(inst)};
          break;
        case InstructionFormat.Move_format:
          ops = new Operand[]{Move.getVal(inst)};
          break;
        case InstructionFormat.NullCheck_format:
          ops = new Operand[]{NullCheck.getRef(inst)};
          break;
        case InstructionFormat.ZeroCheck_format:
          ops = new Operand[]{ZeroCheck.getValue(inst)};
          break;
        case InstructionFormat.BoundsCheck_format:
          ops = new Operand[]{BoundsCheck.getRef(inst), BoundsCheck.getIndex(inst)};
          break;
        case InstructionFormat.TrapIf_format:
          ops = new Operand[]{TrapIf.getVal1(inst), TrapIf.getVal2(inst), TrapIf.getTCode(inst)};
          break;
        case InstructionFormat.TypeCheck_format:
          ops = new Operand[]{TypeCheck.getRef(inst), TypeCheck.getType(inst)};
          break;
        case InstructionFormat.InstanceOf_format:
          ops = new Operand[]{InstanceOf.getRef(inst), InstanceOf.getType(inst)};
          break;
        case InstructionFormat.Call_format:
          int numParams = Call.getNumberOfParams(inst);
          ops = new Operand[numParams+2];
          ops[0] = Call.getAddress(inst);
          ops[1] = Call.getMethod(inst);
          for (int i=0; i < numParams; i++) {
            ops[i+2] = Call.getParam(inst, i);
          }
          break;
        default:
          throw new OptimizingCompilerException("Unsupported type " + inst);
      }

      AvailableExpression ae = new AvailableExpression(inst, opr, ops, location, null);
      cache.add(ae);
    }

    /**
     * Eliminate all AE tuples that contain a given operand
     *
     * @param op the operand in question
     */
    private void eliminate(RegisterOperand op) {
      int i = 0;
      loop_over_expressions:
      while (i < cache.size()) {
        AvailableExpression ae = cache.get(i);
        if (ae.ops != null) {
          for (Operand opx : ae.ops) {
            if (opx instanceof RegisterOperand && ((RegisterOperand) opx).getRegister() == op.getRegister()) {
              cache.remove(i);
              continue loop_over_expressions; // don't increment i, since we removed
            }
          }
        }
        i++;
      }
    }

    /**
     * Eliminate all AE tuples that are killed by a given instruction
     *
     * @param s the store instruction
     */
    public void eliminate(Instruction s) {
      int i = 0;
      // first kill all registers that this instruction defs
      for (OperandEnumeration defs = s.getDefs(); defs.hasMoreElements();) {
        // first KILL any registers this instruction DEFS
        Operand def = defs.next();
        if (def instanceof RegisterOperand) {
          eliminate((RegisterOperand) def);
        }
      }
      if (doMemory) {
        // eliminate all memory locations killed by stores
        if (LocalCSE.isStoreInstruction(s) || (options.READS_KILL && LocalCSE.isLoadInstruction(s))) {
          // sLocation holds the location killed by this instruction
          LocationOperand sLocation = LocationCarrier.getLocation(s);
          // walk through the cache and invalidate any killed locations
          while (i < cache.size()) {
            AvailableExpression ae = cache.get(i);
            if (ae.inst != s) {   // a store instruction doesn't kill itself
              boolean killIt = false;
              if (ae.isLoadOrStore()) {
                if ((sLocation == null) && (ae.location == null)) {
                  // !TODO: is this too conservative??
                  killIt = true;
                } else if ((sLocation != null) && (ae.location != null)) {
                  killIt = LocationOperand.mayBeAliased(sLocation, ae.location);
                }
              }
              if (killIt) {
                cache.remove(i);
                continue;         // don't increment i, since we removed
              }
            }
            i++;
          }
        }
      }
    }

    /**
     * Eliminate all AE tuples that cache ANY memory location.
     */
    public void invalidateAllLoads() {
      if (!doMemory) return;
      int i = 0;
      while (i < cache.size()) {
        AvailableExpression ae = cache.get(i);
        if (ae.isLoadOrStore()) {
          cache.remove(i);
          continue;               // don't increment i, since we removed
        }
        i++;
      }
    }
  }

  /**
   * A tuple to record an Available Expression
   */
  private static final class AvailableExpression {
    /**
     * the instruction which makes this expression available
     */
    final Instruction inst;
    /**
     * the operator of the expression
     */
    final Operator opr;
    /**
     * operands
     */
    final Operand[] ops;
    /**
     * location operand for memory (load/store) expressions
     */
    final LocationOperand location;
    /**
     * temporary register holding the result of the available
     * expression
     */
    Register tmp;

    /**
     * @param i the instruction which makes this expression available
     * @param op the operator of the expression
     * @param ops the operands
     * @param loc location operand for memory (load/store) expressions
     * @param t temporary register holding the result of the available
     * expression
     */
    AvailableExpression(Instruction i, Operator op, Operand[] ops,
                        LocationOperand loc, Register t) {
      this.inst = i;
      this.opr = op;
      this.ops = ops;
      this.location = loc;
      this.tmp = t;
    }

    /**
     * Two AEs are "equal" iff
     *  <ul>
     *   <li> for unary, binary and ternary expressions:
     *     the operator and the operands match
     *    <li> for loads and stores: if the 2 operands and the location match
     *  </ul>
     */
    @Override
    public boolean equals(Object o) {
      if (!(o instanceof AvailableExpression)) {
        return false;
      }
      AvailableExpression ae = (AvailableExpression) o;
      if (isLoadOrStore()) {
        if (!ae.isLoadOrStore()) {
          return false;
        }
        boolean result = LocationOperand.mayBeAliased(location, ae.location);
        if (ops == null || ae.ops == null){
          return result && ops == ae.ops;
        }
        result = result && ops[0].similar(ae.ops[0]);
        if (ops.length > 1) {
          result = result && ops[1].similar(ae.ops[1]);
        } else {
          /* ops[1] isn't present, so ae.ops[1] must also not be present */
          if (ae.ops.length > 1) {
            return false;
          }
        }
        return result;
      } else if (isBoundsCheck()) {
        // Augment equality with BC(ref,C1) ==> BC(ref,C2)
        // when C1>0, C2>=0, and C1>C2
        if (!opr.equals(ae.opr)) {
          return false;
        }
        if (!ops[0].similar(ae.ops[0])) {
          return false;
        }
        if (ops[1].similar(ae.ops[1])) {
          return true;
        }
        if (ops[1] instanceof IntConstantOperand && ae.ops[1] instanceof IntConstantOperand) {
          int C1 = ((IntConstantOperand) ops[1]).value;
          int C2 = ((IntConstantOperand) ae.ops[1]).value;
          return C1 > 0 && C2 >= 0 && C1 > C2;
        } else {
          return false;
        }
      } else {
        if (!opr.equals(ae.opr)) {
          return false;
        }
        if (ops.length != ae.ops.length) {
          return false;
        } else {
          if (ops.length == 2) {
            return (ops[0].similar(ae.ops[0]) && ops[1].similar(ae.ops[1])) ||
              (isCommutative() && ops[0].similar(ae.ops[1]) && ops[1].similar(ae.ops[0]));
          } else {
            for (int i=0; i < ops.length; i++) {
              if (!ops[i].similar(ae.ops[i])) {
                return false;
              }
            }
            return true;
          }
        }
      }
    }
    /**
     * Unused hashcode method
     */
    @Override
    public int hashCode() {
      return opr.hashCode();
    }

    /**
     * Does this expression represent the result of a load or store?
     */
    public boolean isLoadOrStore() {
      return GetField.conforms(opr) || GetStatic.conforms(opr) || PutField.conforms(opr) || PutStatic.conforms(opr);
    }

    /**
     * Does this expression represent the result of a load?
     */
    public boolean isLoad() {
      return GetField.conforms(opr) || GetStatic.conforms(opr);
    }

    /**
     * Does this expression represent the result of a store?
     */
    public boolean isStore() {
      return PutField.conforms(opr) || PutStatic.conforms(opr);
    }

    /**
     * Does this expression represent the result of a bounds check?
     */
    private boolean isBoundsCheck() {
      return BoundsCheck.conforms(opr) || (TrapIf.conforms(opr) && ((TrapCodeOperand) ops[2]).isArrayBounds());
    }

    /**
     * Is this expression commutative?
     */
    private boolean isCommutative() {
      return opr.isCommutative();
    }
  }
}
