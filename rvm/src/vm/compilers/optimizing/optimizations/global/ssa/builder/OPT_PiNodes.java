/*
 * (C) Copyright IBM Corp. 2001
 */
//$Id$
package com.ibm.JikesRVM.opt;
import com.ibm.JikesRVM.*;

import  java.util.*;
import  java.math.*;
import com.ibm.JikesRVM.opt.ir.*;

/**
 * @author Rastislav Bodik
 * @author Stephen Fink
 * @author Julian Dolby
 *
 * This pass inserts PI nodes (Effectively copies)
 * on branch edges, to introduce new names for analysis
 */
public final class OPT_PiNodes extends OPT_CompilerPhase
    implements OPT_Operators, OPT_Constants {
   
  /**
   * Should we insert PI nodes for array references after bounds-checks
   * and null-checks?  TODO: if this is false, then null-check elimination
   * will be ineffective.  TODO: prove that null-check elimination is
   * sound before turning this on again.
   */
  final static boolean CHECK_REF_PI = false;

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
   * @param options controlling compiler options
   */
  public final boolean shouldPerform(OPT_Options options) {
    return options.GLOBAL_BOUNDS_CHECK || typeChecks;
  };

  /**
   * A String representation of this phase
   * @return a string representation
   */
  public final String getName() {
    return  "Pi Nodes " + insertion;
  }

  /**
   * Should we print the IR either before or after this phase?
   * @param options controlling compiler options
   * @param before control for the query
   */
  public final boolean printingEnabled(OPT_Options options, boolean before) {
    return  false;
  }

  /**
   * Create the phase.
   *
   * @param insert If true, we insert PI nodes,  If false, we remove them.
   */
  OPT_PiNodes(boolean insert) {
    this.insertion = insert;
    this.typeChecks = false;
  }

  /**
   * Create the phase.
   *
   * @param insert If true, we insert PI nodes,  If false, we remove them.
   * @param typeChecks If true, we insert PI nodes only for type checks.
   */
  OPT_PiNodes(boolean insert, boolean typeChecks) {
    this.insertion = insert;
    this.typeChecks = typeChecks;
  }

  /**
   * Perform the transformation.
   * @param ir the IR to optimize 
   */
  public void perform(OPT_IR ir) {
    if (insertion) {
      if (!typeChecks) {
          insertPiIfNodes(ir);
          insertPiBcNodes(ir);
          insertPiNullCheckNodes(ir);
      } else
          insertPiCheckCastNodes(ir);
      // invalidate SSA state
      ir.actualSSAOptions = null;
    } 
    else {
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
  private void insertPiIfNodes(OPT_IR ir) {
    for (OPT_InstructionEnumeration e = ir.forwardInstrEnumerator(); 
        e.hasMoreElements(); /* nothing */
    ) {
      OPT_Instruction instr = e.next
      /*Element*/
      ();
      // TODO: what other compareops generate useful assertions?
      if (IfCmp.conforms(instr) || InlineGuard.conforms(instr)) {

        OPT_BasicBlock thisbb = instr.getBasicBlock();
        // only handle the "normal" case
        if (thisbb.getNumberOfNormalOut() != 2)
          continue;
        // insert new basic blocks on each edge out of thisbb
        OPT_BasicBlockEnumeration outBB = thisbb.getNormalOut();
        OPT_BasicBlock out1 = outBB.next();
        OPT_BasicBlock new1 = OPT_IRTools.makeBlockOnEdge(thisbb, out1, ir);
        OPT_BasicBlock out2 = outBB.next();
        OPT_BasicBlock new2 = OPT_IRTools.makeBlockOnEdge(thisbb, out2, ir);

        // For these types of IfCmp's, the Pi Node is not actually
        // needed yet.  For now the only functionality needed is the
        // blocks made on the outgoing edges.
        if (InlineGuard.conforms(instr)) continue;

        OPT_RegisterOperand ifGuard = (OPT_RegisterOperand)
            IfCmp.getGuardResult(instr);

        if (VM.VerifyAssertions)
          VM._assert(ifGuard != null);
        // get compared variables
        OPT_Operand a = IfCmp.getVal1(instr);
        OPT_Operand b = IfCmp.getVal2(instr);
        // determine which block is "taken" on the branch
        OPT_BasicBlock takenBlock = 
            IfCmp.getTarget(instr).target.getBasicBlock();
        boolean new1IsTaken = false;
        if (takenBlock == new1) {
          new1IsTaken = true;
        }

        // insert the PI-node instructions for a and b
        if (a.isRegister() && !a.asRegister().register.isPhysical() && 
            (a.asRegister().register.isInteger() || a.asRegister().register.isAddress())) {
          // insert pi-nodes only for variables, not constants
          OPT_Instruction s = GuardedUnary.create(
              PI, (OPT_RegisterOperand)a.copy(), 
              a.copy(), null);
          OPT_RegisterOperand sGuard = (OPT_RegisterOperand)ifGuard.copy();
          if (new1IsTaken)
            sGuard.setTaken(); 
          else 
            sGuard.setNotTaken();
          GuardedUnary.setGuard(s, sGuard);
          new1.prependInstruction(s);
          s = s.copyWithoutLinks();
          sGuard = (OPT_RegisterOperand)ifGuard.copy();
          if (new1IsTaken)
            sGuard.setNotTaken(); 
          else 
            sGuard.setTaken();
          GuardedUnary.setGuard(s, sGuard);
          new2.prependInstruction(s);
        }
        if (b.isRegister() && !b.asRegister().register.isPhysical() && 
            (b.asRegister().register.isInteger() || b.asRegister().register.isAddress())) {
          OPT_Instruction s = GuardedUnary.create(
              PI, (OPT_RegisterOperand)b.copy(), 
              b.copy(), null);
          OPT_RegisterOperand sGuard = (OPT_RegisterOperand)ifGuard.copy();
          if (new1IsTaken)
            sGuard.setTaken(); 
          else 
            sGuard.setNotTaken();
          GuardedUnary.setGuard(s, sGuard);
          new1.prependInstruction(s);
          s = s.copyWithoutLinks();
          sGuard = (OPT_RegisterOperand)ifGuard.copy();
          if (new1IsTaken)
            sGuard.setNotTaken(); 
          else 
            sGuard.setTaken();
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
  private void insertPiBcNodes(OPT_IR ir) {
    OPT_Instruction nextInst = null;
    // for each instruction in the IR
    for (OPT_Instruction instr = ir.firstInstructionInCodeOrder(); instr
        != null; instr = nextInst) {
      // can't use iterator, since we modify instruction stream
      nextInst = instr.nextInstructionInCodeOrder();
      if (BoundsCheck.conforms(instr)) {
        // Create a pi node for the index.
        OPT_Operand index = BoundsCheck.getIndex(instr);
        // create the instruction and insert it
        if (index.isRegister() && !index.asRegister().register.isPhysical()) {
          OPT_Instruction s = GuardedUnary.create(PI, 
                                           (OPT_RegisterOperand)index.copy(), 
                                           index.copy(), null);
          OPT_RegisterOperand sGuard = (OPT_RegisterOperand)
                                       BoundsCheck.getGuardResult(instr).copy();
          sGuard.setBoundsCheck();
          GuardedUnary.setGuard(s, sGuard);
          instr.insertAfter(s);
        }
        if (CHECK_REF_PI) {
          // Create a pi node for the array.
          OPT_Operand array = BoundsCheck.getRef(instr);
          // create the instruction and insert it
          if (array.isRegister() && !array.asRegister().register.isPhysical()) {
            OPT_Instruction s = GuardedUnary.create(PI, 
                                                    (OPT_RegisterOperand)array.copy(), 
                                                    array.copy(), null);
            OPT_RegisterOperand sGuard = (OPT_RegisterOperand)
              BoundsCheck.getGuardResult(instr).copy();
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
  private void insertPiNullCheckNodes(OPT_IR ir) {
    if (!CHECK_REF_PI) return;
    OPT_Instruction nextInst = null;
    // for each instruction in the IR
    for (OPT_Instruction instr = ir.firstInstructionInCodeOrder(); instr
        != null; instr = nextInst) {
      // can't use iterator, since we modify instruction stream
      nextInst = instr.nextInstructionInCodeOrder();
      if (NullCheck.conforms(instr)) {
        // get compared variables
        OPT_Operand obj = NullCheck.getRef(instr);
        // create the instruction and insert it
        if (obj.isRegister()) {
          OPT_RegisterOperand lval = (OPT_RegisterOperand)obj.copy();
          OPT_Instruction s = GuardedUnary.create(PI, lval, obj.copy(), 
                                                  null);
          OPT_RegisterOperand sGuard = (OPT_RegisterOperand)
                              NullCheck.getGuardResult(instr).copy();
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
  private void insertPiCheckCastNodes(OPT_IR ir) {
    OPT_Instruction nextInst = null;
    // for each instruction in the IR
    for (OPT_Instruction instr = ir.firstInstructionInCodeOrder(); instr
        != null; instr = nextInst) {
      // can't use iterator, since we modify instruction stream
      nextInst = instr.nextInstructionInCodeOrder();
      if (TypeCheck.conforms(instr)) {
        // get compared variables
        OPT_Operand obj = TypeCheck.getRef(instr);
        // create the instruction and insert it
        if (obj.isRegister()) {
          OPT_RegisterOperand lval = (OPT_RegisterOperand)obj.copy();
          lval.type = TypeCheck.getType(instr).getTypeRef();
          lval.clearDeclaredType();
          OPT_Instruction s = GuardedUnary.create(PI, lval, obj.copy(), null);
          s.position = instr.position;
          s.bcIndex = instr.bcIndex;
          OPT_Operand iGuard = TypeCheck.getGuard(instr);
          if (iGuard != null) {
              OPT_Operand sGuard = iGuard.copy();
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
  static void cleanUp(OPT_IR ir) {
    for (OPT_InstructionEnumeration e = ir.forwardInstrEnumerator(); 
        e.hasMoreElements();) {
      OPT_Instruction s = e.next();
      if (s.operator == PI) {
        OPT_RegisterOperand result = GuardedUnary.getResult(s);
        OPT_Operator mv = OPT_IRTools.getMoveOp(result.type);
        OPT_Operand val = GuardedUnary.getVal(s);
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
  public static OPT_Instruction getGenerator(OPT_Instruction def) {
    if (def.operator != PI) throw new OPT_OptimizingCompilerException(
                                      "Not a PI Node!");
    OPT_Operand g = GuardedUnary.getGuard(def);
    OPT_Instruction link = g.asRegister().register.defList.instruction;
    return link;
  }
  /**
   * Is an instruction a Pi node linked to the <em>not taken</em> edge of
   * a conditional branch instruction?
   */
  public static boolean isNotTakenPi(OPT_Instruction def) {
    if (def.operator != PI)
      return false;
    OPT_Operand g = GuardedUnary.getGuard(def);
    if (g.asRegister().isNotTaken()) 
      return true;
    else
      return false;
  }
  /**
   * Is an instruction a Pi node linked to the <em>taken</em> edge of
   * a conditional branch instruction?
   */
  public static boolean isTakenPi(OPT_Instruction def) {
    if (def.operator != PI)
      return false;
    OPT_Operand g = GuardedUnary.getGuard(def);
    if (g.asRegister().isTaken()) 
      return true;
    else
      return false;
  }
  /**
   * Is an instruction a Pi node linked to a bounds-check?
   */
  public static boolean isBoundsCheckPi(OPT_Instruction def) {
    if (def.operator != PI)
      return false;
    OPT_Operand g = GuardedUnary.getGuard(def);
    if (g.asRegister().isBoundsCheck()) 
      return true;
    else
      return false;
  }
  /**
   * Is an instruction a Pi node linked to a null-check?
   */
  public static boolean isNullCheckPi(OPT_Instruction def) {
    if (def.operator != PI)
      return false;
    OPT_Operand g = GuardedUnary.getGuard(def);
    if (g.asRegister().isNullCheck()) 
      return true;
    else
      return false;
  }
}
