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
package org.jikesrvm.compilers.opt.ir;

import java.util.Enumeration;
import java.util.Iterator;
import org.jikesrvm.ArchitectureSpecificOpt;
import org.jikesrvm.ArchitectureSpecificOpt.PhysicalDefUse;
import org.jikesrvm.classloader.TypeReference;
import org.jikesrvm.compilers.opt.ir.operand.HeapOperand;
import org.jikesrvm.compilers.opt.ir.operand.Operand;
import org.jikesrvm.compilers.opt.ir.operand.RegisterOperand;

import static org.jikesrvm.compilers.opt.ir.Operators.PHI;
import org.vmmagic.pragma.NoInline;

/**
 * This class is not meant to be instantiated.<p>
 * It simply serves as a place to collect the implementation of
 * primitive IR enumerations.<p>
 * None of these functions are meant to be called directly from
 * anywhere except IR, Instruction, and BasicBlock.<p>
 * General clients should use the higher level interfaces provided
 * by those classes.
 */
public abstract class IREnumeration {

  /**
   * Forward intra basic block instruction enumerations from
   * from start...last inclusive.<p>
   *
   * NB: start and last _must_ be in the same basic block
   *     and must be in the proper relative order.
   *     This code does _not_ check this invariant, and will
   *     simply fail by eventually thowing a NoSuchElementException
   *     if it is not met. Caller's must be sure the invariants are met.
   *
   * @param start the instruction to start with
   * @param end   the instruction to end with
   * @return an enumeration of the instructions from start to end
   */
  public static Enumeration<Instruction> forwardIntraBlockIE(final Instruction start, final Instruction end) {
    return new Enumeration<Instruction>() {
      private Instruction current = start;
      private final Instruction last = end;

      @Override
      public boolean hasMoreElements() { return current != null; }

      @Override
      public Instruction nextElement() {
        Instruction res = current;
        if (current == last) {
          current = null;
        } else {
          try {
            current = current.getNext();
          } catch (NullPointerException e) {
            fail("forwardIntraBlockIE");
          }
        }
        return res;
      }
    };
  }

  /**
   * Reverse intra basic block instruction enumerations from
   * from start...last inclusive.<p>
   *
   * NB: start and last _must_ be in the same basic block
   *     and must be in the proper relative order.
   *     This code does _not_ check this invariant, and will
   *     simply fail by eventually thowing a NoSuchElementException
   *     if it is not met. Caller's must be sure the invariants are met.
   *
   * @param start the instruction to start with
   * @param end   the instruction to end with
   * @return an enumeration of the instructions from start to end
   */
  public static Enumeration<Instruction> reverseIntraBlockIE(final Instruction start, final Instruction end) {
    return new Enumeration<Instruction>() {
      private Instruction current = start;
      private final Instruction last = end;

      @Override
      public boolean hasMoreElements() { return current != null; }

      @Override
      public Instruction nextElement() {
        Instruction res = current;
        if (current == last) {
          current = null;
        } else {
          try {
            current = current.getPrev();
          } catch (NullPointerException e) {
            fail("reverseIntraBlockIE");
          }
        }
        return res;
      }
    };
  }

  /**
   * A forward enumeration of all the instructions in the IR.
   *
   * @param ir the IR to walk over
   * @return a forward enumeration of the insturctions in ir
   */
  public static Enumeration<Instruction> forwardGlobalIE(final IR ir) {
    return new Enumeration<Instruction>() {
      private Instruction current = ir.firstInstructionInCodeOrder();

      @Override
      public boolean hasMoreElements() { return current != null; }

      @Override
      public Instruction nextElement() {
        try {
          Instruction res = current;
          current = current.nextInstructionInCodeOrder();
          return res;
        } catch (NullPointerException e) {
          fail("forwardGlobalIR");
          return null; // placate jikes
        }
      }
    };
  }

  /**
   * A reverse enumeration of all the instructions in the IR.
   *
   * @param ir the IR to walk over
   * @return a forward enumeration of the insturctions in ir
   */
  public static Enumeration<Instruction> reverseGlobalIE(final IR ir) {
    return new Enumeration<Instruction>() {
      private Instruction current = ir.lastInstructionInCodeOrder();

      @Override
      public boolean hasMoreElements() { return current != null; }

      @Override
      public Instruction nextElement() {
        try {
          Instruction res = current;
          current = current.prevInstructionInCodeOrder();
          return res;
        } catch (NullPointerException e) {
          fail("forwardGlobalIR");
          return null; // placate jikes
        }
      }
    };
  }

  /**
   * A forward enumeration of all the basic blocks in the IR.
   *
   * @param ir the IR to walk over
   * @return a forward enumeration of the basic blocks in ir
   */
  public static Enumeration<BasicBlock> forwardBE(final IR ir) {
    return new Enumeration<BasicBlock>() {
      private BasicBlock current = ir.firstBasicBlockInCodeOrder();

      @Override
      public boolean hasMoreElements() { return current != null; }

      @Override
      public BasicBlock nextElement() {
        try {
          BasicBlock res = current;
          current = current.nextBasicBlockInCodeOrder();
          return res;
        } catch (NullPointerException e) {
          fail("forwardBE");
          return null; // placate jikes
        }
      }
    };
  }

  /**
   * A reverse enumeration of all the basic blocks in the IR.
   *
   * @param ir the IR to walk over
   * @return a reverse enumeration of the basic blocks in ir
   */
  public static Enumeration<BasicBlock> reverseBE(final IR ir) {
    return new Enumeration<BasicBlock>() {
      private BasicBlock current = ir.lastBasicBlockInCodeOrder();

      @Override
      public boolean hasMoreElements() { return current != null; }

      @Override
      public BasicBlock nextElement() {
        try {
          BasicBlock res = current;
          current = current.prevBasicBlockInCodeOrder();
          return res;
        } catch (NullPointerException e) {
          fail("forwardBE");
          return null; // placate jikes
        }
      }
    };
  }

  /**
   * This class implements an enumeration of instructions over
   * all instructions for a basic block. This enumeration includes
   * explicit instructions in the IR and implicit phi instructions for
   * heap variables, which are stored only in this lookaside
   * structure.
   * @see org.jikesrvm.compilers.opt.ssa.SSADictionary
   */
  public static final class AllInstructionsEnum implements Enumeration<Instruction> {
    /**
     * An enumeration of the explicit instructions in the IR for a
     * basic block
     */
    private final Enumeration<Instruction> explicitInstructions;

    /**
     * An enumeration of the implicit instructions in the IR for a
     * basic block.  These instructions appear only in the SSA
     * dictionary lookaside structure.
     */
    private final Iterator<Instruction> implicitInstructions;

    /**
     * The label instruction for the basic block - the label is
     * special as we want it to appear in the enumeration before the
     * implicit SSA instructions
     */
    private Instruction labelInstruction;

    /**
     * Construct an enumeration for all instructions, both implicit and
     * explicit in the IR, for a given basic block
     *
     * @param block the basic block whose instructions this enumerates
     */
    public AllInstructionsEnum(IR ir, BasicBlock block) {
      explicitInstructions = block.forwardInstrEnumerator();
      if (ir.inSSAForm()) {
        implicitInstructions = ir.HIRInfo.dictionary.getHeapPhiInstructions(block);
      } else {
        implicitInstructions = null;
      }
      labelInstruction = explicitInstructions.nextElement();
    }

    /**
     * Are there more elements in the enumeration?
     *
     * @return {@code true} or {@code false}
     */
    @Override
    public boolean hasMoreElements() {
      return (((implicitInstructions != null) && implicitInstructions.hasNext()) ||
              explicitInstructions.hasMoreElements());
    }

    @Override
    public Instruction nextElement() {
      if (labelInstruction != null) {
        Instruction temp = labelInstruction;
        labelInstruction = null;
        return temp;
      } else if ((implicitInstructions != null) && implicitInstructions.hasNext()) {
        return implicitInstructions.next();
      } else {
        return explicitInstructions.nextElement();
      }
    }
  }

  /**
   * This class implements an {@link Enumeration} of {@link Operand}s. It used
   * for holding the definitions of a particular instruction and being
   * used as an enumeration for iterating over. It differs from other
   * enumerations of Operand as it iterates over both implicit
   * and explicit operands.
   * @see org.jikesrvm.compilers.opt.ssa.SSADictionary
   */
  public static final class AllDefsEnum implements Enumeration<Operand> {
    /**
     * Enumeration of non-heap operands defined by the instruction
     */
    private final Enumeration<Operand> instructionOperands;
    /**
     * Array of heap operands defined by the instruction
     */
    private final HeapOperand<?>[] heapOperands;
    /**
     * Current heap operand we're upto for the enumeration
     */
    private int curHeapOperand;
    /**
     * Implicit definitions from the operator
     */
    private final ArchitectureSpecificOpt.PhysicalDefUse.PDUEnumeration implicitDefs;
    /**
     * Defining instruction
     */
    private final Instruction instr;

    /**
     * Construct/initialize object
     *
     * @param ir    Containing IR
     * @param instr Instruction to get definitions for
     */
    public AllDefsEnum(IR ir, Instruction instr) {
      this.instr = instr;
      instructionOperands = instr.getDefs();
      if (instr.operator().getNumberOfImplicitDefs() > 0) {
        implicitDefs = ArchitectureSpecificOpt.PhysicalDefUse.enumerate(instr.operator().implicitDefs, ir);
      } else {
        implicitDefs = null;
      }
      if (ir.inSSAForm() && (instr.operator != PHI)) {
        // Phi instructions store the heap SSA in the actual
        // instruction
        heapOperands = ir.HIRInfo.dictionary.getHeapDefs(instr);
      } else {
        heapOperands = null;
      }
    }

    /**
     * Are there any more elements in the enumeration
     */
    @Override
    public boolean hasMoreElements() {
      return ((instructionOperands.hasMoreElements()) ||
              ((heapOperands != null) && (curHeapOperand < heapOperands.length)) ||
              ((implicitDefs != null) && (implicitDefs.hasMoreElements())));
    }

    @Override
    public Operand nextElement() {
      if (instructionOperands.hasMoreElements()) {
        return instructionOperands.nextElement();
      } else {
        if ((implicitDefs != null) && implicitDefs.hasMoreElements()) {
          RegisterOperand rop = new RegisterOperand(implicitDefs.nextElement(), TypeReference.Int);
          rop.instruction = instr;
          return rop;
        } else {
          if (curHeapOperand >= heapOperands.length) {
            fail("Regular and heap operands exhausted");
          }
          HeapOperand<?> result = heapOperands[curHeapOperand];
          curHeapOperand++;
          return result;
        }
      }
    }
  }

  /**
   * This class implements an {@link Enumeration} of {@link Operand}. It used
   * for holding the uses of a particular instruction and being used
   * as an enumeration for iterating over. It differs from other
   * enumerations of Operand as it iterates over both implicit
   * and explicit operands.
   * @see org.jikesrvm.compilers.opt.ssa.SSADictionary
   */
  public static final class AllUsesEnum implements Enumeration<Operand> {
    /**
     * Enumeration of non-heap operands defined by the instruction
     */
    private final Enumeration<Operand> instructionOperands;
    /**
     * Array of heap operands defined by the instruction
     */
    private final HeapOperand<?>[] heapOperands;
    /**
     * Current heap operand we're upto for the enumeration
     */
    private int curHeapOperand;
    /**
     * Implicit uses from the operator
     */
    private final PhysicalDefUse.PDUEnumeration implicitUses;
    /**
     * Defining instruction
     */
    private final Instruction instr;

    /**
     * Construct/initialize object
     *
     * @param ir    Containing IR
     * @param instr Instruction to get uses for
     */
    public AllUsesEnum(IR ir, Instruction instr) {
      this.instr = instr;
      instructionOperands = instr.getUses();
      if (instr.operator().getNumberOfImplicitUses() > 0) {
        implicitUses = PhysicalDefUse.enumerate(instr.operator().implicitUses, ir);
      } else {
        implicitUses = null;
      }
      if (ir.inSSAForm() && (instr.operator != PHI)) {
        // Phi instructions store the heap SSA in the actual
        // instruction
        heapOperands = ir.HIRInfo.dictionary.getHeapUses(instr);
      } else {
        heapOperands = null;
      }
    }

    /**
     * Are there any more elements in the enumeration
     */
    @Override
    public boolean hasMoreElements() {
      return ((instructionOperands.hasMoreElements()) ||
              ((heapOperands != null) && (curHeapOperand < heapOperands.length)) ||
              ((implicitUses != null) && (implicitUses.hasMoreElements())));
    }

    @Override
    public Operand nextElement() {
      if (instructionOperands.hasMoreElements()) {
        return instructionOperands.nextElement();
      } else {
        if ((implicitUses != null) && implicitUses.hasMoreElements()) {
          RegisterOperand rop = new RegisterOperand(implicitUses.nextElement(), TypeReference.Int);
          rop.instruction = instr;
          return rop;
        } else {
          if (curHeapOperand >= heapOperands.length) {
            fail("Regular and heap operands exhausted");
          }
          HeapOperand<?> result = heapOperands[curHeapOperand];
          curHeapOperand++;
          return result;
        }
      }
    }
  }

  @NoInline
  private static void fail(String msg) throws java.util.NoSuchElementException {
    throw new java.util.NoSuchElementException(msg);
  }
}
