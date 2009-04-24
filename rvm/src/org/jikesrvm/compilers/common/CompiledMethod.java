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
package org.jikesrvm.compilers.common;

import org.jikesrvm.ArchitectureSpecific;
import org.jikesrvm.ArchitectureSpecific.CodeArray;
import org.jikesrvm.VM;
import org.jikesrvm.SizeConstants;
import org.jikesrvm.classloader.RVMMethod;
import org.jikesrvm.classloader.RVMType;
import org.jikesrvm.runtime.DynamicLink;
import org.jikesrvm.runtime.ExceptionDeliverer;
import org.jikesrvm.runtime.Magic;
import org.jikesrvm.runtime.StackBrowser;
import org.jikesrvm.runtime.Statics;
import org.jikesrvm.scheduler.RVMThread;
import org.vmmagic.pragma.Interruptible;
import org.vmmagic.pragma.Uninterruptible;
import org.vmmagic.pragma.Unpreemptible;
import org.vmmagic.unboxed.Address;
import org.vmmagic.unboxed.Offset;
import org.vmmagic.unboxed.Word;

/**
 * A method that has been compiled into machine code by one of our compilers.
 * We implement SynchronizedObject because we need to synchronize
 * on the CompiledMethod object as part of the invalidation protocol.
 */
public abstract class CompiledMethod implements SizeConstants {

  /*
   * constants for compiler types
   */
  public static final int TRAP = 0; // no code: special trap handling stackframe
  public static final int BASELINE = 1; // baseline code
  public static final int OPT = 3; // opt code
  public static final int JNI = 4; // java to Native C transition frame
  public static final int NUM_COMPILER_TYPES = 4;

  /*
   * constants for flags
   */
  private static final byte COMPILED = 0x08;
  private static final byte INVALID = 0x04;
  private static final byte OBSOLETE = 0x02;
  private static final byte ACTIVE_ON_STACK = 0x01;
  /** flags the compiled method as outdated, needs OSR */
  private static final byte OUTDATED = 0x10;
  /**
   * Has the method sample data for this compiled method been reset?
   */
  private static final byte SAMPLES_RESET = 0x20;
  private static final byte SPECIAL_FOR_OSR = 0x40;
  /** Has bridge from native annotation, NB this makes the flags byte negative */
  private static final byte BRIDGE_FROM_NATIVE = (byte)0x80;
  static {
    if (VM.VerifyAssertions) VM._assert(BRIDGE_FROM_NATIVE < 0);
  }

  /** Flags bit field */
  private byte flags;

  /**
   * The compiled method id of this compiled method (index into CompiledMethods)
   */
  protected final int cmid;

  /**
   * The RVMMethod that was compiled
   */
  public final RVMMethod method;

  /**
   * The compiled machine code for said method.
   */
  protected CodeArray instructions;

  /**
   * the offset of instructions in JTOC, for osr-special compiled
   * method only. all osr-ed method is treated like static.
   * TODO: OSR redesign: put in subclass?  Stick somewhere else?
   *       Don't want to waste space for this on every compiled
   *       method.
   */
  protected int osrJTOCoffset = 0;

  /**
   * The time in milliseconds taken to compile the method.
   */
  protected float compilationTime;

  public void setSamplesReset() {
    flags |= SAMPLES_RESET;
  }

  public boolean getSamplesReset() {
    return (flags & SAMPLES_RESET) != 0;
  }

  public void setSpecialForOSR() {
    flags |= SPECIAL_FOR_OSR;
    // set jtoc
    this.osrJTOCoffset = Statics.allocateReferenceSlot(false).toInt();
    Statics.setSlotContents(this.getOsrJTOCoffset(), this.instructions);
  }

  public boolean isSpecialForOSR() {
    return (flags & SPECIAL_FOR_OSR) != 0;
  }

  public final Offset getOsrJTOCoffset() {
    if (VM.VerifyAssertions) VM._assert(isSpecialForOSR());
    return Offset.fromIntSignExtend(this.osrJTOCoffset);
  }

  /**
   * Set the cmid and method fields
   */
  public CompiledMethod(int id, RVMMethod m) {
    cmid = id;
    method = m;
    if (m != null && m.getDeclaringClass().hasBridgeFromNativeAnnotation()) {
      flags = BRIDGE_FROM_NATIVE;
    }
  }

  /**
   * Return the compiled method id for this compiled method
   */
  @Uninterruptible
  public final int getId() {
    return cmid;
  }

  /**
   * Return the RVMMethod associated with this compiled method
   */
  @Uninterruptible
  public final RVMMethod getMethod() {
    return method;
  }

  /**
   * Does this method have a bridge from native annotation, important when
   * walking the stack
   */
  @Uninterruptible
  public final boolean hasBridgeFromNativeAnnotation() {
    return flags < 0;
  }

  /**
   * @return the CodeArray to jump to to invoke this method (ie,
   *         code_array[0] contains the first instruction of the method's prologue).
   */
  @Uninterruptible
  public final CodeArray getEntryCodeArray() {
    if (VM.VerifyAssertions) VM._assert((flags & COMPILED) != 0);
    return instructions;
  }

  /**
   * @return the number of machine instructions for compiled method;
   *         may be an overestimate if we have adding padding to machine code.
   */
  @Uninterruptible
  public final int numberOfInstructions() {
    if (VM.VerifyAssertions) VM._assert((flags & COMPILED) != 0);
    return instructions.length();
  }

  /**
   * Return the offset in bytes of the given Address from the start
   * of the machine code array.
   * @param ip a Address (should be an interior pointer to instructions)
   * @return offset of addr from start of instructions in bytes
   */
  @Uninterruptible
  public final Offset getInstructionOffset(Address ip) {
    return getInstructionOffset(ip, true);
  }

  /**
   * Return the offset in bytes of the given Address from the start
   * of the machine code array.
   * @param ip a Address (should be an interior pointer to instructions)
   * @param dieOnFailure if ip is invalid should we kill the VM (we don't want
   *  to if already in the process of killing the VM)
   * @return offset of addr from start of instructions in bytes
   */
  @Uninterruptible
  public final Offset getInstructionOffset(Address ip, boolean dieOnFailure) {
    if (getCompilerType() == JNI || getCompilerType() == TRAP) {
      return Offset.zero();
    } else {
      Offset offset = ip.diff(Magic.objectAsAddress(instructions));
      int max = (instructions.length() + 1) << ArchitectureSpecific.ArchConstants.LG_INSTRUCTION_WIDTH;
      if (!offset.toWord().LT(Word.fromIntZeroExtend(max))) {
        Address instructionStart = Magic.objectAsAddress(instructions);
        VM.sysWriteln("\nIn thread ",RVMThread.getCurrentThreadSlot()," getInstructionOffset: ip is not within compiled code for method: ",ip);
        VM.sysWrite("\tsupposed method is ");
        VM.sysWrite(method);
        VM.sysWriteln();
        VM.sysWriteln("\tcode for this method starts at ", instructionStart);
        VM.sysWriteln("\t and has last valid return address of ", instructionStart.plus(max));
        VM.sysWriteln("The requested instruction address was ", ip);
        CompiledMethod realCM = CompiledMethods.findMethodForInstruction(ip);
        if (realCM == null) {
          VM.sysWriteln("\tUnable to find compiled method corresponding to this return address");
        } else {
          VM.sysWrite("\tFound compiled method ");
          VM.sysWrite(realCM.getMethod());
          VM.sysWriteln(" whose code contains this return address");
        }
        if (dieOnFailure) {
          VM.sysWriteln("Attempting to dump virtual machine state before exiting");
          RVMThread.dumpVirtualMachine();
          VM.sysFail("Terminating VM due to invalid request for instruction offset");
        }
      }
      // NOTE: we are absolutely positive that offset will fit in 32 bits
      // because we don't create CodeArrays that are so massive it won't.
      // Thus, we do the assertion checking above to ensure that ip is in range.
      return offset;
    }
  }

  /**
   * Return the address of the instruction at offset offset in the method's instruction stream.
   * @param offset the offset of the desired instruction (as returned by getInstructionOffset)
   * @return Address of the specified instruction
   */
  @Uninterruptible
  public final Address getInstructionAddress(Offset offset) {
    Address startAddress = Magic.objectAsAddress(instructions);
    return startAddress.plus(offset);
  }

  /**
   * Return the code array for this method that contains the given offset.
   * @param offset the offset of the desired instruction (as returned by getInstructionOffset)
   * @return CodeArray that contains the specified instruction
   */
  @Uninterruptible
  public final CodeArray codeArrayForOffset(Offset offset) {
    return instructions;
  }

  /**
   * Does the code for the compiled method contain the given return address?
   * @param ip a return address
   * @return true if it belongs to this method's code, false otherwise.
   */
  @Uninterruptible
  public final boolean containsReturnAddress(Address ip) {
    Address beg = Magic.objectAsAddress(instructions);
    Address end = beg.plus(instructions.length() << ArchitectureSpecific.ArchConstants.LG_INSTRUCTION_WIDTH);

    // note that "ip" points to a return site (not a call site)
    // so the range check here must be "ip <= beg || ip >  end"
    // and not                         "ip <  beg || ip >= end"
    //
    return !(ip.LE(beg) || ip.GT(end));
  }

  /**
   * Record that the compilation is complete.
   */
  public final void compileComplete(CodeArray code) {
    instructions = code;
    flags |= COMPILED;
  }

  /**
   * Mark the compiled method as invalid
   */
  public final void setInvalid() {
    flags |= INVALID;
  }

  /**
   * Mark the compiled method as obsolete (ie a candidate for eventual GC)
   */
  @Uninterruptible
  public final void setObsolete() {
    flags |= OBSOLETE;
  }

  @Uninterruptible
  public final void setActiveOnStack() {
    flags |= ACTIVE_ON_STACK;
  }

  @Uninterruptible
  public final void clearActiveOnStack() {
    flags &= ~ACTIVE_ON_STACK;
  }

  /**
   * Mark the compiled method as outdated (ie requires OSR),
   * the flag is set in AnalyticModel
   */
  @Uninterruptible
  public final void setOutdated() {
    if (VM.VerifyAssertions) VM._assert(this.getCompilerType() == BASELINE);
    flags |= OUTDATED;
  }

  /**
   * Check if the compiled method is marked as outdated,
   * called by Thread
   */
  @Uninterruptible
  public final boolean isOutdated() {
    return (flags & OUTDATED) != 0;
  }

  /**
   * Has compilation completed?
   */
  @Uninterruptible
  public final boolean isCompiled() {
    return (flags & COMPILED) != 0;
  }

  /**
   * Is the compiled code invalid?
   */
  @Uninterruptible
  public final boolean isInvalid() {
    return (flags & INVALID) != 0;
  }

  /**
   * Is the compiled code obsolete?
   */
  @Uninterruptible
  public final boolean isObsolete() {
    return (flags & OBSOLETE) != 0;
  }

  @Uninterruptible
  public final boolean isActiveOnStack() {
    return (flags & ACTIVE_ON_STACK) != 0;
  }

  public final double getCompilationTime() { return (double) compilationTime; }

  public final void setCompilationTime(double ct) { compilationTime = (float) ct; }

  /**
   * Identify the compiler that produced this compiled method.
   * @return one of TRAP, BASELINE, OPT, or JNI.
   * Note: use this instead of "instanceof" when gc is disabled (ie. during gc)
   */
  @Uninterruptible
  public abstract int getCompilerType();

  @Uninterruptible
  public static String compilerTypeToString(int compilerType) {
    switch (compilerType) {
      case TRAP:
        return "TRAP";
      case BASELINE:
        return "BASELINE";
      case OPT:
        return "OPT";
      case JNI:
        return "JNI";
      default:
        if (VM.VerifyAssertions) VM._assert(false);
        return null;
    }
  }

  /**
   * @return Name of the compiler that produced this compiled method.
   */
  public abstract String getCompilerName();

  /**
   * Get handler to deal with stack unwinding and exception delivery for this
   * compiled method's stackframes.
   */
  @Uninterruptible
  public abstract ExceptionDeliverer getExceptionDeliverer();

  /**
   * Find "catch" block for a machine instruction of
   * this method that might be guarded
   * against specified class of exceptions by a "try" block .
   *
   * @param instructionOffset offset of machine instruction from start of this method, in bytes
   * @param exceptionType type of exception being thrown - something like "NullPointerException"
   * @return offset of machine instruction for catch block
   * (-1 --> no catch block)
   *
   * Notes:
   * <ul>
   * <li> The "instructionOffset" must point to the instruction
   * <em> following </em> the actual
   * instruction whose catch block is sought.
   * This allows us to properly handle the case where
   * the only address we have to work with is a return address
   * (ie. from a stackframe)
   * or an exception address
   * (ie. from a null pointer dereference, array bounds check,
   * or divide by zero) on a machine architecture with variable length
   * instructions.
   * In such situations we'd have no idea how far to back up the
   * instruction pointer
   * to point to the "call site" or "exception site".
   *
   * <li> This method must not cause any allocations, because it executes with
   * gc disabled when called by RuntimeEntrypoints.deliverException().
   * </ul>
   */
  @Unpreemptible
  public abstract int findCatchBlockForInstruction(Offset instructionOffset, RVMType exceptionType);

  /**
   * Fetch symbolic reference to a method that's called by one of
   * this method's instructions.
   * @param dynamicLink place to put return information
   * @param instructionOffset offset of machine instruction from start of
   * this method, in bytes
   *
   * Notes:
   * <ul>
   * <li> The "instructionOffset" must point to the instruction i
   * <em> following </em> the call
   * instruction whose target method is sought.
   * This allows us to properly handle the case where
   * the only address we have to work with is a return address
   * (ie. from a stackframe)
   * on a machine architecture with variable length instructions.
   * In such situations we'd have no idea how far to back up the
   * instruction pointer
   * to point to the "call site".
   *
   * <li> The implementation must not cause any allocations,
   * because it executes with
   * gc disabled when called by GCMapIterator.
   * <ul>
   */
  @Uninterruptible
  public abstract void getDynamicLink(DynamicLink dynamicLink, Offset instructionOffset);

  /**
   * Find source line number corresponding to one of this method's
   * machine instructions.
   * @param instructionOffset of machine instruction from start of this method, in bytes
   * @return source line number
   * (0 == no line info available, 1 == first line of source file)
   *
   * <p> Usage note: "instructionOffset" must point to the
   * instruction <em> following </em> the actual instruction
   * whose line number is sought.
   * This allows us to properly handle the case where
   * the only address we have to work with is a return address
   * (ie. from a stackframe)
   * or an exception address
   * (ie. from a null pointer dereference, array bounds check,
   * or divide by zero) on a machine architecture with variable length
   * instructions.
   * In such situations we'd have no idea how far to back up the
   * instruction pointer
   * to point to the "call site" or "exception site".
   */
  @Uninterruptible
  public int findLineNumberForInstruction(Offset instructionOffset) {
    return 0;
  }

  /**
   * Return whether or not the given address (which is purported to be inside
   * of the compiled method's code array) corresponds to an uninterruptible context.
   *
   * @param instructionOffset of addr from start of instructions in bytes
   * @return true if the IP is within an Uninterruptible method, false otherwise.
   */
  @Interruptible
  public abstract boolean isWithinUninterruptibleCode(Offset instructionOffset);

  /**
   * Print this compiled method's portion of a stack trace
   * @param instructionOffset offset of machine instruction from start of method
   * @param out the PrintLN to print the stack trace to.
   */
  public abstract void printStackTrace(Offset instructionOffset, org.jikesrvm.PrintLN out);

  /**
   * Set the stack browser to the innermost logical stack frame of this method
   */
  public abstract void set(StackBrowser browser, Offset instr);

  /**
   * Advance the StackBrowser up one internal stack frame, if possible
   */
  public boolean up(StackBrowser browser) { return false; }

  /**
   * Return the number of bytes used to encode the compiler-specific mapping
   * information for this compiled method.
   * Used to gather stats on the space costs of mapping schemes.
   */
  public int size() { return 0; }

}
