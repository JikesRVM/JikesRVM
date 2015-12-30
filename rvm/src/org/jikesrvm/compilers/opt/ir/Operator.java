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

/* NOTE: this is a mechanically generated file, see class JavaDoc
    for details */

package org.jikesrvm.compilers.opt.ir;

import org.jikesrvm.VM;

/**
 * An Operator represents the operator of an {@link Instruction}.
 * For each operator in the IR, we create exactly one Operator instance
 * to represent it. These instances are all stored in static fields
 * of {@link Operators}. Since only one instance is created for each
 * semantic operator, they can be compared using <code>==</code>.
 *
 * @see Operators
 * @see Instruction
 */
public abstract class Operator {


  /*
   * The following are used to encode operator traits in OperatorList.dat.
   * Had to make a few of them public (yuck) to let us get at them
   * from InstructionFormat.java.
   */
  /** operator has no interesting traits */
  public static final int none         = 0x00000000;
  /** operator is a simple move operation from one "register" to another */
  protected static final int move         = 0x00000001;
  /** operator is an intraprocedural branch of some form */
  protected static final int branch       = 0x00000002;
  /** operator is some kind of call (interprocedural branch) */
  protected static final int call         = 0x00000004;
  /** modifer for branches/calls */
  protected static final int conditional  = 0x00000008;
  /** modifier for branches/calls, mostly on MIR */
  protected static final int indirect     = 0x00000010;
  /** an explicit load of a value from memory */
  protected static final int load         = 0x00000020;
  /** operator is modeled as a load by memory system, mostly on MIR */
  protected static final int memAsLoad    = 0x00000040;
  /** an explicit store of a value to memory */
  protected static final int store        = 0x00000080;
  /** operator is modeled as a store by memory system, mostly on MIR */
  protected static final int memAsStore   = 0x00000100;
  /** is an exception throw */
  protected static final int ethrow       = 0x00000200;
  /** an immediate PEI (null_check, int_zero_check, but _not_ call); */
  protected static final int immedPEI     = 0x00000400;
  /** operator is some kind of compare (val,val)-&gt; cond */
  protected static final int compare      = 0x00000800;
  /** an explicit memory allocation */
  protected static final int alloc        = 0x00001000;
  /** a return instruction (interprocedural branch) */
  protected static final int ret          = 0x00002000;
  /** operator has a variable number of uses */
  public static final int varUses      = 0x00004000;
  /** operator has a variable number of defs */
  public static final int varDefs      = 0x00008000;
  /**
   * operator is a potential thread switch point for some reason
   * other than being a call/immedPEI
   */
  protected static final int tsp          = 0x00010000;
  /** operator is an acquire (monitorenter/lock) HIR only */
  protected static final int acquire      = 0x00020000;
  /** operator is a relase (monitorexit/unlock) HIR only */
  protected static final int release      = 0x00040000;
  /** operator either directly or indirectly may casue dynamic linking */
  protected static final int dynLink      = 0x00080000;
  /** operator is a yield point */
  protected static final int yieldPoint   = 0x00100000;
  /** operator pops floating-point stack after performing defs */
  protected static final int fpPop        = 0x00200000;
  /** operator pushs floating-point stack before performing defs */
  protected static final int fpPush       = 0x00400000;
  /** operator is commutative */
  protected static final int commutative  = 0x00800000;

  /**
   * The operators opcode.
   * This value serves as a unique id suitable for use in switches
   */
  final char opcode;

  /**
   * Encoding of the operator's InstructionFormat.
   * This field is only meant to be directly referenced
   * from the mechanically generated InstructionFormat
   * classes defined in the instructionFormats package.
   * {@link Instruction} contains an explanation
   * of the role of InstructionFormats in the IR.
   */
  public final byte format;

  /**
   * encoding of operator traits (characteristics)
   */
  private final int traits;

  /**
   * How many operands of the operator are (pure) defs?
   */
  private final int numberDefs;

  /**
   * How many operands of the operator are both defs and uses?
   * Only non-zero on IA32, 390.
   */
  private final int numberDefUses;

  /**
   * How many operands of the operator are pure uses?
   * Only contains a valid value for non-variableLength operators
   */
  private final int numberUses;

  /**
   * Physical registers that are implicitly defined by the operator.
   */
  public final int implicitDefs;

  /**
   * Physical registers that are implicitly used by the operator.
   */
  public final int implicitUses;

  protected Operator(char opcode, byte format, int traits,
                       int numDefs, int numDefUses, int numUses,
                       int iDefs, int iUses) {
    this.opcode       = opcode;
    this.format       = format;
    this.traits       = traits;
    this.numberDefs   = numDefs;
    this.numberDefUses = numDefUses;
    this.numberUses   = numUses;
    this.implicitDefs = iDefs;
    this.implicitUses = iUses;
  }

  public static Operator lookupOpcode(int opcode) {
    if (VM.BuildForIA32) {
      return org.jikesrvm.compilers.opt.ir.ia32.ArchOperator.lookupOpcode(opcode);
    } else {
      if (VM.VerifyAssertions) VM._assert(VM.BuildForPowerPC);
      return org.jikesrvm.compilers.opt.ir.ppc.ArchOperator.lookupOpcode(opcode);
    }
  }

  public final char getOpcode() {
    return opcode;
  }

  /**
   * Returns the string representation of this operator.
   *
   * @return the name of the operator
   */
  @Override
  public String toString() {
    if (VM.BuildForIA32) {
      return org.jikesrvm.compilers.opt.ir.ia32.ArchOperatorNames.toString(this);
    } else {
      if (VM.VerifyAssertions) VM._assert(VM.BuildForPowerPC);
      return org.jikesrvm.compilers.opt.ir.ppc.ArchOperatorNames.toString(this);
    }
  }

  /**
   * Returns the number of operands that are defs.
   * By convention, operands are ordered in instructions
   * such that all defs are first, followed by all
   * combined defs/uses, followed by all pure uses.
   *
   * @return number of operands that are pure defs
   */
  public int getNumberOfPureDefs() {
    if (VM.VerifyAssertions) VM._assert(!hasVarDefs());
    return numberDefs;
  }

  /**
   * Returns the number of operands that are pure defs
   * and are not in the variable-length part of the operand list.
   * By convention, operands are ordered in instructions
   * such that all defs are first, followed by all
   * combined defs/uses, followed by all pure uses.
   *
   * @return how many non-variable operands are pure defs
   */
  public int getNumberOfFixedPureDefs() {
    return numberDefs;
  }

  /**
   * Returns the number of operands that are pure uses
   * and are not in the variable-length part of the operand list.
   * By convention, operands are ordered in instructions
   * such that all defs are first, followed by all
   * combined defs/uses, followed by all pure uses.
   *
   * @return how many non-variable operands are pure uses
   */
  public int getNumberOfFixedPureUses() {
    return numberUses;
  }

  /**
   * Returns the number of operands that are defs
   * and uses.
   * By convention, operands are ordered in instructions
   * such that all defs are first, followed by all
   * combined defs/uses, followed by all pure uses.
   *
   * @return number of operands that are combined defs and uses
   */
  public int getNumberOfDefUses() {
    return numberDefUses;
  }

  /**
   * Returns the number of operands that are pure uses.
   * By convention, operands are ordered in instructions
   * such that all defs are first, followed by all
   * combined defs/uses, followed by all pure uses.
   *
   * @return number of operands that are pure uses
   */
  public int getNumberOfPureUses() {
    return numberUses;
  }

  /**
   * Returns the number of operands that are defs
   * (either pure defs or combined def/uses).
   * By convention, operands are ordered in instructions
   * such that all defs are first, followed by all
   * combined defs/uses, followed by all pure uses.
   *
   * @return number of operands that are defs
   */
  public int getNumberOfDefs() {
    if (VM.VerifyAssertions) VM._assert(!hasVarDefs());
    return numberDefs + numberDefUses;
  }

  /**
   * Returns the number of operands that are uses
   * (either combined def/uses or pure uses).
   * By convention, operands are ordered in instructions
   * such that all defs are first, followed by all
   * combined defs/uses, followed by all pure uses.
   *
   * @return how many operands are uses
   */
  public int getNumberOfUses() {
    if (VM.VerifyAssertions) VM._assert(!hasVarUses());
    return numberDefUses + numberUses;
  }

  /**
   * Returns the number of operands that are pure uses
   * and are not in the variable-length part of the operand list.
   * By convention, operands are ordered in instructions
   * such that all defs are first, followed by all
   * combined defs/uses, followed by all pure uses.
   *
   * @return how many non-variable operands are pure uses
   */
  public int getNumberOfPureFixedUses() {
    return numberUses;
  }

  /**
   * Returns the number of operands that are uses
   * (either combined use/defs or pure uses)
   * and are not in the variable-length part of the operand list.
   * By convention, operands are ordered in instructions
   * such that all defs are first, followed by all
   * combined defs/uses, followed by all pure uses.
   *
   * @return number of non-variable operands are uses
   */
  public int getNumberOfFixedUses() {
    return numberDefUses + numberUses;
  }

  /**
   * Returns the number of physical registers that are
   * implicitly defined by this operator.
   *
   * @return number of implicit defs
   */
  public int getNumberOfImplicitDefs() {
    return Integer.bitCount(implicitDefs);
  }

  /**
   * Returns the number of physical registers that are
   * implicitly used by this operator.
   *
   * @return number of implicit uses
   */
  public int getNumberOfImplicitUses() {
    return Integer.bitCount(implicitUses);
  }

  /**
   * Does the operator represent a simple move (the value is unchanged)
   * from one "register" location to another "register" location?
   *
   * @return <code>true</code> if the operator is a simple move
   *         or <code>false</code> if it is not.
   */
  public boolean isMove() {
    return (traits & move) != 0;
  }

  /**
   * Is the operator an intraprocedural branch?
   *
   * @return <code>true</code> if the operator is am
   *         intraprocedural branch or <code>false</code> if it is not.
   */
  public boolean isBranch() {
    return (traits & branch) != 0;
  }

  /**
   * Is the operator a conditional intraprocedural branch?
   *
   * @return <code>true</code> if the operator is a conditoonal
   *         intraprocedural branch or <code>false</code> if it is not.
   */
  public boolean isConditionalBranch() {
    return (traits & (branch | conditional)) == (branch | conditional);
  }

  /**
   * Is the operator an unconditional intraprocedural branch?
   * We consider various forms of switches to be unconditional
   * intraprocedural branches, even though they are multi-way branches
   * and we may not no exactly which target will be taken.
   * This turns out to be the right thing to do, since some
   * arm of the switch will always be taken (unlike conditional branches).
   *
   * @return <code>true</code> if the operator is an unconditional
   *         intraprocedural branch or <code>false</code> if it is not.
   */
  public boolean isUnconditionalBranch() {
    return (traits & (branch | conditional)) == branch;
  }

  /**
   * Is the operator a direct intraprocedural branch?
   * In the HIR and LIR we consider switches to be direct branches,
   * because their targets are known precisely.
   *
   * @return <code>true</code> if the operator is a direct
   *         intraprocedural branch or <code>false</code> if it is not.
   */
  public boolean isDirectBranch() {
    return (traits & (branch | indirect)) == branch;
  }

  /**
   * Is the operator an indirect intraprocedural branch?
   *
   * @return <code>true</code> if the operator is an indirect
   *         interprocedural branch or <code>false</code> if it is not.
   */
  public boolean isIndirectBranch() {
    return (traits & (branch | indirect)) == (branch | indirect);
  }

  /**
   * Is the operator a call (one kind of interprocedural branch)?
   *
   * @return <code>true</code> if the operator is a call
   *         or <code>false</code> if it is not.
   */
  public boolean isCall() {
    return (traits & call) != 0;
  }

  /**
   * Is the operator a conditional call?
   * We only allow conditional calls in the MIR, since they
   * tend to only be directly implementable on some architecutres.
   *
   * @return <code>true</code> if the operator is a
   *         conditional call or <code>false</code> if it is not.
   */
  public boolean isConditionalCall() {
    return (traits & (call | conditional)) == (call | conditional);
  }

  /**
   * Is the operator an unconditional call?
   * Really only an interesting question in the MIR, since
   * it is by definition true for all HIR and LIR calls.
   *
   * @return <code>true</code> if the operator is an unconditional
   *         call or <code>false</code> if it is not.
   */
  public boolean isUnconditionalCall() {
    return (traits & (call | conditional)) == call;
  }

  /**
   * Is the operator a direct call?
   * Only interesting on the MIR.  In the HIR and LIR we pretend that
   * all calls are "direct" even though most of them aren't.
   *
   * @return <code>true</code> if the operator is a direct call
   *         or <code>false</code> if it is not.
   */
  public boolean isDirectCall() {
    return (traits & (call | indirect)) == call;
  }

  /**
   * Is the operator an indirect call?
   * Only interesting on the MIR.  In the HIR and LIR we pretend that
   * all calls are "direct" even though most of them aren't.
   *
   * @return <code>true</code> if the operator is an indirect call
   *         or <code>false</code> if it is not.
   */
  public boolean isIndirectCall() {
    return (traits & (call | indirect)) == (call | indirect);
  }

  /**
   * Is the operator an explicit load of a finite set of values from
   * a finite set of memory locations (load, load multiple, _not_ call)?
   *
   * @return <code>true</code> if the operator is an explicit load
   *         or <code>false</code> if it is not.
   */
  public boolean isExplicitLoad() {
    return (traits & load) != 0;
  }

  /**
   * Should the operator be treated as a load from some unknown location(s)
   * for the purposes of scheduling and/or modeling the memory subsystem?
   *
   * @return <code>true</code> if the operator is an implicit load
   *         or <code>false</code> if it is not.
   */
  public boolean isImplicitLoad() {
    return (traits & (load | memAsLoad | call)) != 0;
  }

  /**
   * Is the operator an explicit store of a finite set of values to
   * a finite set of memory locations (store, store multiple, _not_ call)?
   *
   * @return <code>true</code> if the operator is an explicit store
   *         or <code>false</code> if it is not.
   */
  public boolean isExplicitStore() {
    return (traits & store) != 0;
  }

  /**
   * Should the operator be treated as a store to some unknown location(s)
   * for the purposes of scheduling and/or modeling the memory subsystem?
   *
   * @return <code>true</code> if the operator is an implicit store
   *         or <code>false</code> if it is not.
   */
  public boolean isImplicitStore() {
    return (traits & (store | memAsStore | call)) != 0;
  }

  /**
   * Is the operator a throw of a Java exception?
   *
   * @return <code>true</code> if the operator is a throw
   *         or <code>false</code> if it is not.
   */
  public boolean isThrow() {
    return (traits & ethrow) != 0;
  }

  /**
   * Is the operator a PEI (Potentially Excepting Instruction)?
   *
   * @return <code>true</code> if the operator is a PEI
   *         or <code>false</code> if it is not.
   */
  public boolean isPEI() {
    return (traits & (ethrow | immedPEI)) != 0;
  }

  /**
   * Is the operator a potential GC point?
   *
   * @return <code>true</code> if the operator is a potential
   *         GC point or <code>false</code> if it is not.
   */
  public boolean isGCPoint() {
    return isPEI() || ((traits & (alloc | tsp)) != 0);
  }

  /**
   * is the operator a potential thread switch point?
   *
   * @return <code>true</code> if the operator is a potential
   *         threadswitch point or <code>false</code> if it is not.
   */
  public boolean isTSPoint() {
    return isGCPoint();
  }

  /**
   * Is the operator a compare (val,val) =&gt; condition?
   *
   * @return <code>true</code> if the operator is a compare
   *         or <code>false</code> if it is not.
   */
  public boolean isCompare() {
    return (traits & compare) != 0;
  }

  /**
   * Is the operator an actual memory allocation instruction
   * (NEW, NEWARRAY, etc)?
   *
   * @return <code>true</code> if the operator is an allocation
   *         or <code>false</code> if it is not.
   */
  public boolean isAllocation() {
    return (traits & alloc) != 0;
  }

  /**
   * Is the operator a return (interprocedural branch)?
   *
   * @return <code>true</code> if the operator is a return
   *         or <code>false</code> if it is not.
   */
  public boolean isReturn() {
    return (traits & ret) != 0;
  }

  /**
   * Can the operator have a variable number of uses?
   *
   * @return <code>true</code> if the operator has a variable number
   *         of uses or <code>false</code> if it does not.
   */
  public boolean hasVarUses() {
    return (traits & varUses) != 0;
  }

  /**
   * Can the operator have a variable number of uses?
   *
   * @return <code>true</code> if the operator has a variable number
   *         of uses or <code>false</code> if it does not.
   */
  public boolean hasVarDefs() {
    return (traits & varDefs) != 0;
  }

  /**
   * Can the operator have a variable number of uses or defs?
   *
   * @return <code>true</code> if the operator has a variable number
   *         of uses or defs or <code>false</code> if it does not.
   */
  public boolean hasVarUsesOrDefs() {
    return (traits & (varUses | varDefs)) != 0;
  }

  /**
   * Is the operator an acquire (monitorenter/lock)?
   *
   * @return <code>true</code> if the operator is an acquire
   *         or <code>false</code> if it is not.
   */
  public boolean isAcquire() {
    return (traits & acquire) != 0;
  }

  /**
   * Is the operator a release (monitorexit/unlock)?
   *
   * @return <code>true</code> if the operator is a release
   *         or <code>false</code> if it is not.
   */
  public boolean isRelease() {
    return (traits & release) != 0;
  }

  /**
   * Could the operator either directly or indirectly
   * cause dynamic class loading?
   *
   * @return <code>true</code> if the operator is a dynamic linking point
   *         or <code>false</code> if it is not.
   */
  public boolean isDynamicLinkingPoint() {
    return (traits & dynLink) != 0;
  }

  /**
   * Is the operator a yield point?
   *
   * @return <code>true</code> if the operator is a yield point
   *         or <code>false</code> if it is not.
   */
  public boolean isYieldPoint() {
    return (traits & yieldPoint) != 0;
  }

  /**
   * Does the operator pop the floating-point stack?
   *
   * @return <code>true</code> if the operator pops the floating-point
   * stack.
   *         or <code>false</code> if not.
   */
  public boolean isFpPop() {
    return (traits & fpPop) != 0;
  }

  /**
   * Does the operator push on the floating-point stack?
   *
   * @return <code>true</code> if the operator pushes on the floating-point
   * stack.
   *         or <code>false</code> if not.
   */
  public boolean isFpPush() {
    return (traits & fpPush) != 0;
  }

  /**
   * Is the operator commutative?
   *
   * @return <code>true</code> if the operator is commutative.
   *         or <code>false</code> if not.
   */
  public boolean isCommutative() {
    return (traits & commutative) != 0;
  }

  /**
   * @return whether this is a operator a call to a routine that will save volatile
   *  registers
   */
  public boolean isCallSaveVolatile() {
    if (VM.BuildForIA32) {
      return this == org.jikesrvm.compilers.opt.ir.ia32.ArchOperators.CALL_SAVE_VOLATILE;
    } else {
      if (VM.VerifyAssertions) VM._assert(VM.BuildForPowerPC);
      return this == org.jikesrvm.compilers.opt.ir.ppc.ArchOperators.CALL_SAVE_VOLATILE;
    }
  }

  /** @return is this the IA32 ADVISE_ESP operator? */
  public boolean isAdviseESP() {
    return VM.BuildForIA32 &&
      this == org.jikesrvm.compilers.opt.ir.ia32.ArchOperators.ADVISE_ESP;
  }

  /** @return is this the IA32 FNINIT operator? */
  public boolean isFNInit() {
    return VM.BuildForIA32 &&
      this == org.jikesrvm.compilers.opt.ir.ia32.ArchOperators.IA32_FNINIT;
  }

  /** @return is this the IA32 FCLEAR operator? */
  public boolean isFClear() {
    return VM.BuildForIA32 &&
      this == org.jikesrvm.compilers.opt.ir.ia32.ArchOperators.IA32_FCLEAR;
  }

  /**
   * @return Instruction template used by the assembler to
   * generate binary code. Only valid on MIR operators.
   */
  public abstract int instTemplate();
}
