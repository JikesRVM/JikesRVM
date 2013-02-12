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

import org.jikesrvm.ArchitectureSpecificOpt.PhysicalRegisterSet;
import org.jikesrvm.compilers.opt.ir.operand.RegisterOperand;

/**
 * Represents a symbolic or physical register.
 * Registers are shared among all Operands -- for a given register
 * pool, there is only one instance of an Register with each number.
 *
 * @see RegisterOperand
 * @see org.jikesrvm.ArchitectureSpecificOpt.RegisterPool
 */
public final class Register {

  /**
   * Index number relative to register pool.
   */
  public final int number;

  /**
   * Encoding of register properties & scratch bits
   */
  private int flags;

  private static final int LOCAL = 0x00001;  /* local variable */
  private static final int SPAN_BASIC_BLOCK = 0x00002;  /* live on a basic block boundary */
  private static final int SSA = 0x00004;  /* only one assignment to this register */
  private static final int SEEN_USE = 0x00008;  /* seen use */
  private static final int PHYSICAL = 0x00010;  /* physical (real) register - not symbolic */

  /*  register type  for both physical and symbolic */
  private static final int TYPE_SHIFT = 6;        /* # bits to shift */
  private static final int ADDRESS = 0x00040;  /* address */
  private static final int INTEGER = 0x00080;  /* integer */
  private static final int FLOAT = 0x00100;  /* floating-point single precision */
  private static final int DOUBLE = 0x00200;  /* floating-point double precision */
  private static final int CONDITION = 0x00400;  /* condition: PPC,x86*/
  private static final int LONG = 0x00800;  /* long (two ints)*/
  private static final int VALIDATION = 0x01000;  /* validation pseudo-register */

  /* this for physical register only */
  private static final int VOLATILE = 0x02000;
  private static final int NON_VOLATILE = 0x04000;

  /* used with live analysis */
  private static final int EXCLUDE_LIVEANAL = 0x08000; /* reg is excluded from live analysis */

  /* used by the register allocator */
  private static final int SPILLED = 0x10000; /* spilled into a memory location */
  private static final int TOUCHED = 0x20000; /* register touched */
  private static final int ALLOCATED = 0x40000; /* allocated to some register */
  private static final int PINNED = 0x80000; /* pinned, unavailable for allocation */

  /* derived constants to be exported */
  private static final int TYPE_MASK = (ADDRESS | INTEGER | FLOAT | DOUBLE | CONDITION | LONG | VALIDATION);
  public static final int ADDRESS_TYPE = ADDRESS >>> TYPE_SHIFT;
  public static final int INTEGER_TYPE = INTEGER >>> TYPE_SHIFT;
  public static final int FLOAT_TYPE = FLOAT >>> TYPE_SHIFT;
  public static final int DOUBLE_TYPE = DOUBLE >>> TYPE_SHIFT;
  public static final int CONDITION_TYPE = CONDITION >>> TYPE_SHIFT;
  public static final int LONG_TYPE = LONG >>> TYPE_SHIFT;
  public static final int VALIDATION_TYPE = VALIDATION >>> TYPE_SHIFT;

  public boolean isTemp() { return (flags & LOCAL) == 0; }

  public boolean isLocal() { return (flags & LOCAL) != 0; }

  public boolean spansBasicBlock() { return (flags & SPAN_BASIC_BLOCK) != 0; }

  public boolean isSSA() { return (flags & SSA) != 0; }

  public boolean seenUse() { return (flags & SEEN_USE) != 0; }

  public boolean isPhysical() { return (flags & PHYSICAL) != 0; }

  public boolean isSymbolic() { return (flags & PHYSICAL) == 0; }

  public boolean isAddress() { return (flags & ADDRESS) != 0; }

  public boolean isInteger() { return (flags & INTEGER) != 0; }

  public boolean isLong() { return (flags & LONG) != 0; }

  public boolean isNatural() { return (flags & (INTEGER | LONG | ADDRESS)) != 0; }

  public boolean isFloat() { return (flags & FLOAT) != 0; }

  public boolean isDouble() { return (flags & DOUBLE) != 0; }

  public boolean isFloatingPoint() { return (flags & (FLOAT | DOUBLE)) != 0; }

  public boolean isCondition() { return (flags & CONDITION) != 0; }

  public boolean isValidation() { return (flags & VALIDATION) != 0; }

  public boolean isExcludedLiveA() { return (flags & EXCLUDE_LIVEANAL) != 0; }

  public int getType() { return (flags & TYPE_MASK) >>> TYPE_SHIFT; }

  public boolean isVolatile() { return (flags & VOLATILE) != 0; }

  public boolean isNonVolatile() { return (flags & NON_VOLATILE) != 0; }

  public void setLocal() { flags |= LOCAL; }

  public void setSpansBasicBlock() { flags |= SPAN_BASIC_BLOCK; }

  public void setSSA() { flags |= SSA; }

  public void setSeenUse() { flags |= SEEN_USE; }

  public void setPhysical() { flags |= PHYSICAL; }

  public void setAddress() { flags |= ADDRESS; }

  public void setInteger() { flags |= INTEGER; }

  public void setFloat() { flags |= FLOAT; }

  public void setDouble() { flags |= DOUBLE; }

  public void setLong() { flags |= LONG; }

  public void setCondition() { flags = (flags & ~TYPE_MASK) | CONDITION; }

  public void setValidation() { flags |= VALIDATION; }

  public void setExcludedLiveA() { flags |= EXCLUDE_LIVEANAL; }

  public void setVolatile() { flags |= VOLATILE; }

  public void setNonVolatile() { flags |= NON_VOLATILE; }

  public void putSSA(boolean a) {
    if (a) {
      setSSA();
    } else {
      clearSSA();
    }
  }

  public void putSpansBasicBlock(boolean a) {
    if (a) {
      setSpansBasicBlock();
    } else {
      clearSpansBasicBlock();
    }
  }

  public void clearLocal() { flags &= ~LOCAL; }

  public void clearSpansBasicBlock() { flags &= ~SPAN_BASIC_BLOCK; }

  public void clearSSA() { flags &= ~SSA; }

  public void clearSeenUse() { flags &= ~SEEN_USE; }

  public void clearPhysical() { flags &= ~PHYSICAL; }

  public void clearAddress() { flags &= ~ADDRESS; }

  public void clearInteger() { flags &= ~INTEGER; }

  public void clearFloat() { flags &= ~FLOAT; }

  public void clearDouble() { flags &= ~DOUBLE; }

  public void clearLong() { flags &= ~LONG; }

  public void clearCondition() { flags &= ~CONDITION; }

  public void clearType() { flags &= ~TYPE_MASK; }

  public void clearValidation() { flags &= ~VALIDATION; }

  public Object scratchObject;

  /**
   * Used in dependence graph construction.
   */
  public void setdNode(org.jikesrvm.compilers.opt.depgraph.DepGraphNode a) {
    scratchObject = a;
  }

  public org.jikesrvm.compilers.opt.depgraph.DepGraphNode dNode() {
    return (org.jikesrvm.compilers.opt.depgraph.DepGraphNode) scratchObject;
  }

  /**
   * Used to store register lists.
   * Computed on demand by IR.computeDU().
   */
  public RegisterOperand defList, useList;

  /**
   * This accessor is only valid when register lists are valid
   */
  public Instruction getFirstDef() {
    if (defList == null) {
      return null;
    } else {
      return defList.instruction;
    }
  }

  /**
   * The number of uses; used by flow-insensitive optimizations
   */
  public int useCount;

  /**
   * A field optimizations can use as they choose
   */
  public int scratch;

  public Register(int Number) {
    number = Number;
  }

  public int getNumber() {
    int start = PhysicalRegisterSet.getSize();
    return number - start;
  }

  /**
   * Returns the string representation of this register.
   */
  @Override
  public String toString() {
    if (isPhysical()) {
      return PhysicalRegisterSet.getName(number);
    }

    // Set s to descriptive letter for register type
    String s = isLocal() ? "l" : "t";
    s = s + getNumber() + (spansBasicBlock() ? "p" : "") + (isSSA() ? "s" : "") + typeName();
    return s;
  }

  public String typeName() {
    String s = "";
    if (isCondition()) s += "c";
    if (isAddress()) s += "a";
    if (isInteger()) s += "i";
    if (isDouble()) s += "d";
    if (isFloat()) s += "f";
    if (isLong()) s += "l";
    if (isValidation()) s += "v";
    if (s == null) s = "_";
    return s;
  }

  /* used by the register allocator */
  public Register mapsToRegister;

  public void clearAllocationFlags() {
    flags &= ~(PINNED | TOUCHED | ALLOCATED | SPILLED);
  }

  public void pinRegister() {
    flags |= PINNED | TOUCHED;
  }

  public void reserveRegister() {
    flags |= PINNED;
  }

  public void touchRegister() {
    flags |= TOUCHED;
  }

  public void allocateRegister() {
    flags = (flags & ~SPILLED) | (ALLOCATED | TOUCHED);
  }

  public void allocateRegister(Register reg) {
    flags = (flags & ~SPILLED) | (ALLOCATED | TOUCHED);
    mapsToRegister = reg;
  }

  public void allocateToRegister(Register reg) {
    this.allocateRegister(reg);
    reg.allocateRegister(this);
  }

  public void deallocateRegister() {
    flags &= ~ALLOCATED;
    mapsToRegister = null;
  }

  public void freeRegister() {
    deallocateRegister();
    Register symbReg = mapsToRegister;
    if (symbReg != null) {
      symbReg.clearSpill();
    }
  }

  public void spillRegister() {
    flags = (flags & ~ALLOCATED) | SPILLED;
  }

  public void clearSpill() {
    flags &= ~SPILLED;
  }

  public void unpinRegister() {
    flags &= ~PINNED;
  }

  public boolean isTouched() {
    return (flags & TOUCHED) != 0;
  }

  public boolean isAllocated() {
    return (flags & ALLOCATED) != 0;
  }

  public boolean isSpilled() {
    return (flags & SPILLED) != 0;
  }

  public boolean isPinned() {
    return (flags & PINNED) != 0;
  }

  public boolean isAvailable() {
    return (flags & (ALLOCATED | PINNED)) == 0;
  }

  public Register getRegisterAllocated() {
    return mapsToRegister;
  }

  public int getSpillAllocated() {
    return scratch;
  }

  @Override
  public int hashCode() {
    return number;
  }

  /* inlined behavior of DoublyLinkedListElement */ Register next, prev;

  public Register getNext() { return next; }

  void setNext(Register e) { next = e; }

  public Register getPrev() { return prev; }

  public void linkWithNext(Register Next) {
    next = Next;
    Next.prev = this;
  }

  void append(Register l) {
    next = l;
    l.prev = this;
  }

  Register remove() {
    Register Prev = prev, Next = next;
    if (Prev != null) Prev.next = Next;
    if (Next != null) Next.prev = Prev;
    return Next;
  }
  /* end of inlined behavior */
}
