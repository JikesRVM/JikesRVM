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
package org.jikesrvm.compilers.opt.ssa;

import static org.jikesrvm.compilers.opt.ir.Operators.PI;

import java.lang.reflect.Constructor;
import java.util.Enumeration;

import org.jikesrvm.VM;
import org.jikesrvm.compilers.opt.OptOptions;
import org.jikesrvm.compilers.opt.OptimizingCompilerException;
import org.jikesrvm.compilers.opt.driver.CompilerPhase;
import org.jikesrvm.compilers.opt.ir.BasicBlock;
import org.jikesrvm.compilers.opt.ir.BoundsCheck;
import org.jikesrvm.compilers.opt.ir.GuardedUnary;
import org.jikesrvm.compilers.opt.ir.IR;
import org.jikesrvm.compilers.opt.ir.IRTools;
import org.jikesrvm.compilers.opt.ir.IfCmp;
import org.jikesrvm.compilers.opt.ir.InlineGuard;
import org.jikesrvm.compilers.opt.ir.Instruction;
import org.jikesrvm.compilers.opt.ir.Move;
import org.jikesrvm.compilers.opt.ir.NullCheck;
import org.jikesrvm.compilers.opt.ir.Operator;
import org.jikesrvm.compilers.opt.ir.TypeCheck;
import org.jikesrvm.compilers.opt.ir.operand.Operand;
import org.jikesrvm.compilers.opt.ir.operand.RegisterOperand;

/**
 * This pass inserts PI nodes (Effectively copies)
 * on branch edges, to introduce new names for analysis
 */
public final class PiNodes extends CompilerPhase {

  /**
   * Should we insert PI nodes for array references after bounds-checks
   * and null-checks?
   * <p>TODO: if this is false, then null-check elimination
   * will be ineffective.
   * <p>TODO: prove that null-check elimination is
   * sound before turning this on again.
   */
  static final boolean CHECK_REF_PI = false;

  /**
   * Should we insert (true) or delete (false) PI nodes?
   */
  final boolean insertion;

  /**
   * Are we adding pi nodes for type checks only?  This is for GNOSYS
   * analysis right now.
   */
  final boolean typeChecks;

  /**
   * Should this phase be performed?
   * Only perform this when we are doing an SSA-based optimization
   * that can benefit from PI nodes.
   */
  @Override
  public boolean shouldPerform(OptOptions options) {
    return options.SSA_GLOBAL_BOUNDS_CHECK || typeChecks;
  }

  /**
   * Constructor for this compiler phase
   */
  private static final Constructor<CompilerPhase> constructor =
      getCompilerPhaseConstructor(PiNodes.class, new Class[]{Boolean.TYPE, Boolean.TYPE});

  /**
   * Get a constructor object for this compiler phase
   * @return compiler phase constructor
   */
  @Override
  public Constructor<CompilerPhase> getClassConstructor() {
    return constructor;
  }

  /**
   * A String representation of this phase
   * @return a string representation
   */
  @Override
  public String getName() {
    return "Pi Nodes " + insertion;
  }

  /**
   * Should we print the IR either before or after this phase?
   * @param options controlling compiler options
   * @param before control for the query
   */
  @Override
  public boolean printingEnabled(OptOptions options, boolean before) {
    return false;
  }

  /**
   * Create the phase.
   *
   * @param insert If true, we insert PI nodes,  If false, we remove them.
   */
  public PiNodes(boolean insert) {
    this.insertion = insert;
    this.typeChecks = false;
  }

  /**
   * Create the phase.
   *
   * @param insert If true, we insert PI nodes,  If false, we remove them.
   * @param typeChecks If true, we insert PI nodes only for type checks.
   */
  public PiNodes(boolean insert, boolean typeChecks) {
    super(new Object[]{insert, typeChecks});
    this.insertion = insert;
    this.typeChecks = typeChecks;
  }

  @Override
  public void perform(IR ir) {
    if (insertion) {
      if (!typeChecks) {
        insertPiIfNodes(ir);
        insertPiBcNodes(ir);
        insertPiNullCheckNodes(ir);
      } else {
        insertPiCheckCastNodes(ir);
      }
      // invalidate SSA state
      ir.actualSSAOptions = null;
    } else {
      cleanUp(ir);
    }
  }

  /**
   *  Insert PI nodes corresponding to compare operations.
   *  Pi-nodes are represented as dummy assignments with a single
   *  argument inserted along each outedge of the conditional.
   *
   *  @param ir the governing IR
   */
  private void insertPiIfNodes(IR ir) {
    Enumeration<Instruction> e = ir.forwardInstrEnumerator();
    while(e.hasMoreElements()) {
      Instruction instr = e.nextElement();
      // TODO: what other compareops generate useful assertions?
      if (IfCmp.conforms(instr) || InlineGuard.conforms(instr)) {

        BasicBlock thisbb = instr.getBasicBlock();
        // only handle the "normal" case
        if (thisbb.getNumberOfNormalOut() != 2) {
          continue;
        }
        // insert new basic blocks on each edge out of thisbb
        Enumeration<BasicBlock> outBB = thisbb.getNormalOut();
        BasicBlock out1 = outBB.nextElement();
        BasicBlock new1 = IRTools.makeBlockOnEdge(thisbb, out1, ir);
        BasicBlock out2 = outBB.nextElement();
        BasicBlock new2 = IRTools.makeBlockOnEdge(thisbb, out2, ir);

        // For these types of IfCmp's, the Pi Node is not actually
        // needed yet.  For now the only functionality needed is the
        // blocks made on the outgoing edges.
        if (InlineGuard.conforms(instr)) continue;

        RegisterOperand ifGuard = IfCmp.getGuardResult(instr);

        if (VM.VerifyAssertions) {
          VM._assert(ifGuard != null);
        }
        // get compared variables
        Operand a = IfCmp.getVal1(instr);
        Operand b = IfCmp.getVal2(instr);
        // determine which block is "taken" on the branch
        BasicBlock takenBlock = IfCmp.getTarget(instr).target.getBasicBlock();
        boolean new1IsTaken = false;
        if (takenBlock == new1) {
          new1IsTaken = true;
        }

        // insert the PI-node instructions for a and b
        if (a.isRegister() &&
            !a.asRegister().getRegister().isPhysical() &&
            (a.asRegister().getRegister().isInteger() || a.asRegister().getRegister().isAddress())) {
          // insert pi-nodes only for variables, not constants
          Instruction s = GuardedUnary.create(PI, (RegisterOperand) a.copy(), a.copy(), null);
          RegisterOperand sGuard = (RegisterOperand) ifGuard.copy();
          if (new1IsTaken) {
            sGuard.setTaken();
          } else {
            sGuard.setNotTaken();
          }
          GuardedUnary.setGuard(s, sGuard);
          new1.prependInstruction(s);
          s = s.copyWithoutLinks();
          sGuard = (RegisterOperand) ifGuard.copy();
          if (new1IsTaken) {
            sGuard.setNotTaken();
          } else {
            sGuard.setTaken();
          }
          GuardedUnary.setGuard(s, sGuard);
          new2.prependInstruction(s);
        }
        if (b.isRegister() &&
            !b.asRegister().getRegister().isPhysical() &&
            (b.asRegister().getRegister().isInteger() || b.asRegister().getRegister().isAddress())) {
          Instruction s = GuardedUnary.create(PI, (RegisterOperand) b.copy(), b.copy(), null);
          RegisterOperand sGuard = (RegisterOperand) ifGuard.copy();
          if (new1IsTaken) {
            sGuard.setTaken();
          } else {
            sGuard.setNotTaken();
          }
          GuardedUnary.setGuard(s, sGuard);
          new1.prependInstruction(s);
          s = s.copyWithoutLinks();
          sGuard = (RegisterOperand) ifGuard.copy();
          if (new1IsTaken) {
            sGuard.setNotTaken();
          } else {
            sGuard.setTaken();
          }
          GuardedUnary.setGuard(s, sGuard);
          new2.prependInstruction(s);
        }
      }
    }
  }

  /**
   * Insert Pi nodes for boundchecks.
   *
   * <p>Each boundcheck Arr, Index will be followed by
   * <pre> PI Index, Index </pre>
   *
   * @param ir the governing IR
   */
  private void insertPiBcNodes(IR ir) {
    Instruction nextInst = null;
    // for each instruction in the IR
    for (Instruction instr = ir.firstInstructionInCodeOrder(); instr != null; instr = nextInst) {
      // can't use iterator, since we modify instruction stream
      nextInst = instr.nextInstructionInCodeOrder();
      if (BoundsCheck.conforms(instr)) {
        // Create a pi node for the index.
        Operand index = BoundsCheck.getIndex(instr);
        // create the instruction and insert it
        if (index.isRegister() && !index.asRegister().getRegister().isPhysical()) {
          Instruction s = GuardedUnary.create(PI, (RegisterOperand) index.copy(), index.copy(), null);
          RegisterOperand sGuard = (RegisterOperand) BoundsCheck.getGuardResult(instr).copy();
          sGuard.setBoundsCheck();
          GuardedUnary.setGuard(s, sGuard);
          instr.insertAfter(s);
        }
        if (CHECK_REF_PI) {
          // Create a pi node for the array.
          Operand array = BoundsCheck.getRef(instr);
          // create the instruction and insert it
          if (array.isRegister() && !array.asRegister().getRegister().isPhysical()) {
            Instruction s = GuardedUnary.create(PI, (RegisterOperand) array.copy(), array.copy(), null);
            RegisterOperand sGuard = (RegisterOperand) BoundsCheck.getGuardResult(instr).copy();
            sGuard.setBoundsCheck();
            GuardedUnary.setGuard(s, sGuard);
            instr.insertAfter(s);
          }
        }
      }
    }
  }

  /**
   * Insert Pi nodes for null check operations.
   *
   * <p>Each checkcast obj will be followed by
   * <pre> PI obj, obj </pre>
   *
   * @param ir the governing IR
   */
  private void insertPiNullCheckNodes(IR ir) {
    if (!CHECK_REF_PI) return;
    Instruction nextInst = null;
    // for each instruction in the IR
    for (Instruction instr = ir.firstInstructionInCodeOrder(); instr != null; instr = nextInst) {
      // can't use iterator, since we modify instruction stream
      nextInst = instr.nextInstructionInCodeOrder();
      if (NullCheck.conforms(instr)) {
        // get compared variables
        Operand obj = NullCheck.getRef(instr);
        // create the instruction and insert it
        if (obj.isRegister()) {
          RegisterOperand lval = (RegisterOperand) obj.copy();
          Instruction s = GuardedUnary.create(PI, lval, obj.copy(), null);
          RegisterOperand sGuard = (RegisterOperand) NullCheck.getGuardResult(instr).copy();
          sGuard.setNullCheck();
          GuardedUnary.setGuard(s, sGuard);
          instr.insertAfter(s);
        }
      }
    }
  }

  /**
   * Insert Pi nodes for checkcast operations.
   *
   * <p>Each checkcast obj will be followed by
   * <pre> ref_move obj, obj </pre>
   *
   * @param ir the governing IR
   */
  private void insertPiCheckCastNodes(IR ir) {
    Instruction nextInst = null;
    // for each instruction in the IR
    for (Instruction instr = ir.firstInstructionInCodeOrder(); instr != null; instr = nextInst) {
      // can't use iterator, since we modify instruction stream
      nextInst = instr.nextInstructionInCodeOrder();
      if (TypeCheck.conforms(instr)) {
        // get compared variables
        Operand obj = TypeCheck.getRef(instr);
        // create the instruction and insert it
        if (obj.isRegister()) {
          RegisterOperand lval = (RegisterOperand) obj.copy();
          lval.clearDeclaredType();
          if (lval.getType().isLoaded() && lval.getType().isClassType() && lval.getType().peekType().asClass().isFinal()) {
            lval.setPreciseType(TypeCheck.getType(instr).getTypeRef());
          } else {
            lval.clearPreciseType();
            lval.setType(TypeCheck.getType(instr).getTypeRef());
          }
          Instruction s = GuardedUnary.create(PI, lval, obj.copy(), null);
          s.position = instr.position;
          s.bcIndex = instr.bcIndex;
          Operand iGuard = TypeCheck.getGuard(instr);
          if (iGuard != null) {
            Operand sGuard = iGuard.copy();
            GuardedUnary.setGuard(s, sGuard);
          }
          instr.insertAfter(s);
        }
      }
    }
  }

  /**
   * Change all PI nodes to INT_MOVE instructions
   * <p> Side effect: invalidates SSA state
   *
   * @param ir the governing IR
   */
  static void cleanUp(IR ir) {
    for (Enumeration<Instruction> e = ir.forwardInstrEnumerator(); e.hasMoreElements();) {
      Instruction s = e.nextElement();
      if (s.operator == PI) {
        RegisterOperand result = GuardedUnary.getResult(s);
        Operator mv = IRTools.getMoveOp(result.getType());
        Operand val = GuardedUnary.getVal(s);
        Move.mutate(s, mv, result, val);
      }
    }
    // invalidate SSA state
    ir.actualSSAOptions = null;
  }

  /**
   * Get the instruction a Pi node is linked to.
   * <strong>PRECONDITION: </strong> register lists computed and valid.
   */
  public static Instruction getGenerator(Instruction def) {
    if (def.operator != PI) {
      throw new OptimizingCompilerException("Not a PI Node!");
    }
    Operand g = GuardedUnary.getGuard(def);
    Instruction link = g.asRegister().getRegister().defList.instruction;
    return link;
  }

  /**
   * Is an instruction a Pi node linked to the <em>not taken</em> edge of
   * a conditional branch instruction?
   */
  public static boolean isNotTakenPi(Instruction def) {
    if (def.operator != PI) {
      return false;
    }
    Operand g = GuardedUnary.getGuard(def);
    return g.asRegister().isNotTaken();
  }

  /**
   * Is an instruction a Pi node linked to the <em>taken</em> edge of
   * a conditional branch instruction?
   */
  public static boolean isTakenPi(Instruction def) {
    if (def.operator != PI) {
      return false;
    }
    Operand g = GuardedUnary.getGuard(def);
    return g.asRegister().isTaken();
  }

  /**
   * Is an instruction a Pi node linked to a bounds-check?
   */
  public static boolean isBoundsCheckPi(Instruction def) {
    if (def.operator != PI) {
      return false;
    }
    Operand g = GuardedUnary.getGuard(def);
    return g.asRegister().isBoundsCheck();
  }

  /**
   * Is an instruction a Pi node linked to a null-check?
   */
  public static boolean isNullCheckPi(Instruction def) {
    if (def.operator != PI) {
      return false;
    }
    Operand g = GuardedUnary.getGuard(def);
    return g.asRegister().isNullCheck();
  }
}
