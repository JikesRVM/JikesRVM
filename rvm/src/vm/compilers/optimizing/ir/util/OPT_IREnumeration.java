/*
 * (C) Copyright IBM Corp. 2001
 */
//$Id$
package com.ibm.JikesRVM.opt.ir;

/**
 * This class is not meant to be instantiated.
 * It simply serves as a place to collect the implementation of
 * primitive IR enumerations.
 * None of these functions are meant to be called directly from
 * anywhere except OPT_IR, OPT_Instruction, and OPT_BasicBlock.
 * General clients should use the higher level interfaces provided
 * by those classes
 *
 * @author Dave Grove
 */
public abstract class OPT_IREnumeration {

  /**
   * Forward intra basic block instruction enumerations from 
   * from start...last inclusive.
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
  public static final OPT_InstructionEnumeration forwardIntraBlockIE(final OPT_Instruction start,
                                                                     final OPT_Instruction end) {
    return new OPT_InstructionEnumeration() {
      private OPT_Instruction current = start;
      private OPT_Instruction last = end;
      public final boolean hasMoreElements() { return current != null; }
      public final Object nextElement() { return next(); }
      public final OPT_Instruction next() {
        OPT_Instruction res = current;
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
   * from start...last inclusive.
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
  public static final OPT_InstructionEnumeration reverseIntraBlockIE(final OPT_Instruction start,
                                                                     final OPT_Instruction end) {
    return new OPT_InstructionEnumeration() {
      private OPT_Instruction current = start;
      private OPT_Instruction last = end;
      public final boolean hasMoreElements() { return current != null; }
      public final Object nextElement() { return next(); }
      public final OPT_Instruction next() {
        OPT_Instruction res = current;
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
  public static final OPT_InstructionEnumeration forwardGlobalIE(final OPT_IR ir) {
    return new OPT_InstructionEnumeration() {
      private OPT_Instruction current = ir.firstInstructionInCodeOrder();
      public final boolean hasMoreElements() { return current != null; }
      public final Object nextElement() { return next(); }
      public final OPT_Instruction next() {
        try {
          OPT_Instruction res = current;
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
  public static final OPT_InstructionEnumeration reverseGlobalIE(final OPT_IR ir) {
    return new OPT_InstructionEnumeration() {
      private OPT_Instruction current = ir.lastInstructionInCodeOrder();
      public final boolean hasMoreElements() { return current != null; }
      public final Object nextElement() { return next(); }
      public final OPT_Instruction next() {
        try {
          OPT_Instruction res = current;
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
  public static final OPT_BasicBlockEnumeration forwardBE(final OPT_IR ir) {
    return new OPT_BasicBlockEnumeration() {
      private OPT_BasicBlock current = ir.firstBasicBlockInCodeOrder();
      public final boolean hasMoreElements() { return current != null; }
      public final Object nextElement() { return next(); }
      public final OPT_BasicBlock next() {
        try {
          OPT_BasicBlock res = current;
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
  public static final OPT_BasicBlockEnumeration reverseBE(final OPT_IR ir) {
    return new OPT_BasicBlockEnumeration() {
      private OPT_BasicBlock current = ir.lastBasicBlockInCodeOrder();
      public final boolean hasMoreElements() { return current != null; }
      public final Object nextElement() { return next(); }
      public final OPT_BasicBlock next() {
        try {
          OPT_BasicBlock res = current;
          current = current.prevBasicBlockInCodeOrder();
          return res;
        } catch (NullPointerException e) {
          fail("forwardBE");
          return null; // placate jikes
        }
      }
    };
  }
  
  private static final void fail(String msg) throws java.util.NoSuchElementException, 
                                                    com.ibm.JikesRVM.VM_PragmaNoInline {
    throw new java.util.NoSuchElementException(msg);
  }
}

