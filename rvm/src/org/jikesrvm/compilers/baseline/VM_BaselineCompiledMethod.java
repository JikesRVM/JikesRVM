/*
 * This file is part of Jikes RVM (http://jikesrvm.sourceforge.net).
 * The Jikes RVM project is distributed under the Common Public License (CPL).
 * A copy of the license is included in the distribution, and is also
 * available at http://www.opensource.org/licenses/cpl1.0.php
 *
 * (C) Copyright IBM Corp. 2001, 2003, 2005
 */
package org.jikesrvm.compilers.baseline;

import org.jikesrvm.ArchitectureSpecific.VM_BaselineConstants;
import org.jikesrvm.ArchitectureSpecific.VM_BaselineExceptionDeliverer;
import org.jikesrvm.ArchitectureSpecific.VM_Compiler;
import org.jikesrvm.classloader.*;
import org.jikesrvm.runtime.VM_DynamicLink;
import org.jikesrvm.runtime.VM_ExceptionDeliverer;
import org.jikesrvm.runtime.VM_StackBrowser;
import org.jikesrvm.compilers.common.VM_CompiledMethod;
import org.jikesrvm.compilers.baseline.VM_ReferenceMaps;
import org.jikesrvm.compilers.common.VM_ExceptionTable;
import org.jikesrvm.VM;
import org.jikesrvm.VM_PrintLN;

import org.vmmagic.pragma.*;
import org.vmmagic.unboxed.Offset;

/**
 * Compiler-specific information associated with a method's machine 
 * instructions.
 *
 * @author Bowen Alpern
 * @modified Steven Augart
 */
@SynchronizedObject
public final class VM_BaselineCompiledMethod extends VM_CompiledMethod
    implements VM_BaselineConstants {

  /** Does the baseline compiled method have a counters array? */
  private boolean hasCounters;
  /**
   * The lock acquistion offset for synchronized methods.  For
   * synchronized methods, the offset (in the method prologue) after
   * which the monitor has been obtained.  At, or before, this point,
   * the method does not own the lock.  Used by deliverException to
   * determine whether the lock needs to be released.  Note: for this
   * scheme to work, VM_Lock must not allow a yield after it has been
   * obtained.
   */
  private char lockOffset;

  /**
   * Baseline exception deliverer object
   */
  private static final VM_ExceptionDeliverer exceptionDeliverer = new VM_BaselineExceptionDeliverer();

  /**
   * Stack-slot reference maps for the compiled method.
   */
  public VM_ReferenceMaps referenceMaps;

  /*
   * Currently needed to support dynamic bridge magic; 
   * Consider integrating with GC maps
   */
  private byte[] bytecodeMap;

  /**
   * Exception table, null if not present.
   */
  private int[] eTable;

  /* To make a compiled method's stack offset independ of
   * original method, we move 'getEmptyStackOffset'
   * here.
   *
   * TODO: redesign this.  There has to be a cleaner way!
   */
  //private int startLocalOffset;
  private final int emptyStackOffset;
  private int lastFixedStackRegister;
  private int lastFloatStackRegister;

  private final int[] localFixedLocations; 
  private final int[] localFloatLocations; 
 
//  public int getStartLocalOffset() {
//    return startLocalOffset;
//  }
 
  public int getEmptyStackOffset() {
    return emptyStackOffset;
  }

  //These Locations are positioned at the top of the stackslot that contains the value
  //before accessing, substract size of value you want to access
  //e.g. to load int: load at VM_Compiler.locationToOffset(location) - BYTES_IN_INT 
  //e.g. to load double: load at VM_Compiler.locationToOffset(location) - BYTES_IN_DOUBLE
  @Uninterruptible
  public int getGeneralLocalLocation(int localIndex) {
    return VM_Compiler.getGeneralLocalLocation(localIndex, localFixedLocations, (VM_NormalMethod) method);
  }

  @Uninterruptible
  public int getFloatLocalLocation(int localIndex) {
    return VM_Compiler.getFloatLocalLocation(localIndex, localFloatLocations, (VM_NormalMethod) method);
  }

  @Uninterruptible
  public int getGeneralStackLocation(int stackIndex) {
    return VM_Compiler.offsetToLocation(emptyStackOffset - (stackIndex <<LOG_BYTES_IN_ADDRESS));
  }

  @Uninterruptible
  public int getFloatStackLocation(int stackIndex) { //for now same implementation as getGeneralStackLocation, todo
    return VM_Compiler.offsetToLocation(emptyStackOffset - (stackIndex <<LOG_BYTES_IN_ADDRESS));
  }

  @Uninterruptible
  public int getLastFixedStackRegister() {
    return lastFixedStackRegister;
  }

  @Uninterruptible
  public int getLastFloatStackRegister() {
    return lastFloatStackRegister;
  }

  public VM_BaselineCompiledMethod(int id, VM_Method m) {
    super(id, m);
    VM_NormalMethod nm = (VM_NormalMethod)m;
    //this.startLocalOffset = VM_Compiler.getStartLocalOffset(nm);
    this.emptyStackOffset = VM_Compiler.getEmptyStackOffset(nm);
    this.localFixedLocations = new int[nm.getLocalWords()]; 
    this.localFloatLocations = new int[nm.getLocalWords()]; 
    this.lastFixedStackRegister = -1;
    this.lastFloatStackRegister = -1;
  }

  public void compile() {
    VM_Compiler comp = new VM_Compiler(this, localFixedLocations, localFloatLocations);
    comp.compile();
    this.lastFixedStackRegister = comp.getLastFixedStackRegister();
    this.lastFloatStackRegister = comp.getLastFloatStackRegister();
  }
  
  @Uninterruptible
  public int getCompilerType () { 
    return BASELINE;
  }

  public String getCompilerName() {
    return "baseline compiler";
  }

  @Uninterruptible
  public VM_ExceptionDeliverer getExceptionDeliverer () { 
    return exceptionDeliverer;
  }

  public int findCatchBlockForInstruction (Offset instructionOffset, VM_Type exceptionType) {
    if (eTable == null) {
      return -1;
    } else {
      return VM_ExceptionTable.findCatchBlockForInstruction(eTable, instructionOffset, exceptionType);
    }
  }

  @Uninterruptible
  public void getDynamicLink (VM_DynamicLink dynamicLink, Offset instructionOffset) { 
    int bytecodeIndex = findBytecodeIndexForInstruction(instructionOffset);
    ((VM_NormalMethod)method).getDynamicLink(dynamicLink, bytecodeIndex);
  }

  /**
   * @return The line number, a positive integer.  Zero means unable to find.
   */
  @Uninterruptible
  public int findLineNumberForInstruction (Offset instructionOffset) { 
    int bci = findBytecodeIndexForInstruction(instructionOffset);
    if (bci == -1) return 0;
    return ((VM_NormalMethod)method).getLineNumberForBCIndex(bci);
  }

  /** 
   * Find bytecode index corresponding to one of this method's 
   * machine instructions.
   *
   * Note: This method expects the instructionIndex to refer to the machine
   *         instruction immediately FOLLOWING the bytecode in question.
   *         just like findLineNumberForInstruction. See VM_CompiledMethod
   *         for rationale
   * NOTE: instructionIndex is in units of instructions, not bytes (different from
   *       all the other methods in this interface!!)
   *
   * @return the bytecode index for the machine instruction, -1 if
   *            not available or not found.
   */
  @Uninterruptible
  public int findBytecodeIndexForInstruction (Offset instructionOffset) { 
    Offset instructionIndex = instructionOffset.toWord().rsha(LG_INSTRUCTION_WIDTH).toOffset();
    int candidateIndex = -1;
    int bcIndex = 0;
    Offset instrIndex = Offset.zero();
    for (int i = 0; i < bytecodeMap.length; ) {
      int b0 = ((int) bytecodeMap[i++]) & 255;  // unsign-extend
      int deltaBC, deltaIns;
      if (b0 != 255) {
        deltaBC = b0 >> 5;
        deltaIns = b0 & 31;
      }
      else {
        int b1 = ((int) bytecodeMap[i++]) & 255;  // unsign-extend
        int b2 = ((int) bytecodeMap[i++]) & 255;  // unsign-extend
        int b3 = ((int) bytecodeMap[i++]) & 255;  // unsign-extend
        int b4 = ((int) bytecodeMap[i++]) & 255;  // unsign-extend
        deltaBC = (b1 << 8) | b2;
        deltaIns = (b3 << 8) | b4;
      }
      bcIndex += deltaBC;
      instrIndex = instrIndex.plus(deltaIns);
      if (instrIndex.sGE(instructionIndex))
        break;
      candidateIndex = bcIndex;
    }
    return candidateIndex;
  }

  /**
   * Set the stack browser to the innermost logical stack frame of this method
   */
  public void set(VM_StackBrowser browser, Offset instr) {
    browser.setMethod(method);
    browser.setCompiledMethod(this);
    browser.setBytecodeIndex(findBytecodeIndexForInstruction(instr));

    if (VM.TraceStackTrace) {
        VM.sysWrite("setting stack to frame (base): ");
        VM.sysWrite( browser.getMethod() );
        VM.sysWrite( browser.getBytecodeIndex() );
        VM.sysWrite("\n");
    }
  }

  /**
   * Advance the VM_StackBrowser up one internal stack frame, if possible
   */
  public boolean up(VM_StackBrowser browser) {
    return false;
  }

  // Print this compiled method's portion of a stack trace 
  // Taken:   offset of machine instruction from start of method
  //          the VM_PrintLN to print the stack trace to.
  public void printStackTrace(Offset instructionOffset, VM_PrintLN out) {
    out.print("\tat ");
    out.print(method.getDeclaringClass()); // VM_Class
    out.print('.');
    out.print(method.getName()); // a VM_Atom, returned via VM_MemberReference.getName().
    out.print("(");
    out.print(method.getDeclaringClass().getSourceName()); // a VM_Atom
    int lineNumber = findLineNumberForInstruction(instructionOffset);
    if (lineNumber <= 0) {      // unknown line
      out.print("; machine code offset: ");
      out.printHex(instructionOffset.toInt());
    } else {
      out.print(':');
      out.print(lineNumber);
    }
    out.print(')');
    out.println();
  }

  /**
   * Print the eTable
   */
  public void printExceptionTable() {
    if (eTable != null) VM_ExceptionTable.printExceptionTable(eTable);
  }
  /** Set the lock acquistion offset for synchronized methods */
  public void setLockAcquisitionOffset(int off) {
    if (VM.VerifyAssertions) VM._assert((off & 0xFFFF) == off);
    lockOffset = (char)off;
  }

  /** Get the lock acquistion offset */
  public Offset getLockAcquisitionOffset() {
    return Offset.fromIntZeroExtend(lockOffset);
  }

  /** Set the method has a counters array */
  void setHasCounterArray() {
    hasCounters = true;
  }

  /** Does the method have a counters array? */
  @Uninterruptible
  public boolean hasCounterArray() { 
    return hasCounters;
  }

  // Taken: method that was compiled
  //        bytecode-index to machine-instruction-index map for method
  //        number of instructions for method
  //
  public void encodeMappingInfo(VM_ReferenceMaps referenceMaps, 
                                int[] bcMap, int numInstructions) {
    int count = 0;
    int lastBC = 0, lastIns = 0;
    for (int i=0; i<bcMap.length; i++)
      if (bcMap[i] != 0) {
        int deltaBC = i - lastBC;
        int deltaIns = bcMap[i] - lastIns;
        if (VM.VerifyAssertions) 
          VM._assert(deltaBC >= 0 && deltaIns >= 0);
        if (deltaBC <= 6 && deltaIns <= 31)
          count++;
        else {
          if (deltaBC > 65535 || deltaIns > 65535)
            VM.sysFail("VM_BaselineCompiledMethod: a fancier encoding is needed");
          count += 5;
        }
        lastBC = i;
        lastIns = bcMap[i];
      }
    bytecodeMap = new byte[count];
    count = lastBC = lastIns = 0;
    for (int i=0; i<bcMap.length; i++)
      if (bcMap[i] != 0) {
        int deltaBC = i - lastBC;
        int deltaIns = bcMap[i] - lastIns;
        if (VM.VerifyAssertions) 
          VM._assert(deltaBC >= 0 && deltaIns >= 0);
        if (deltaBC <= 6 && deltaIns <= 31) {
          bytecodeMap[count++] = (byte) ((deltaBC << 5) | deltaIns);
        }
        else { // From before, we know that deltaBC <= 65535 and deltaIns <= 65535
          bytecodeMap[count++] = (byte) 255;
          bytecodeMap[count++] = (byte) (deltaBC >> 8);
          bytecodeMap[count++] = (byte) (deltaBC & 255);
          bytecodeMap[count++] = (byte) (deltaIns >> 8);
          bytecodeMap[count++] = (byte) (deltaIns & 255);
        }
        lastBC = i;
        lastIns = bcMap[i];
      }
    referenceMaps.translateByte2Machine(bcMap);
    this.referenceMaps = referenceMaps;
    VM_ExceptionHandlerMap emap = ((VM_NormalMethod)method).getExceptionHandlerMap();
    if (emap != null) {
      eTable = VM_BaselineExceptionTable.encode(emap, bcMap);
    }
  }

  private static final VM_TypeReference TYPE = VM_TypeReference.findOrCreate(VM_BaselineCompiledMethod.class);

  public int size() {
    int size = TYPE.peekResolvedType().asClass().getInstanceSize();
    if (bytecodeMap != null) size += VM_Array.ByteArray.getInstanceSize(bytecodeMap.length);
    if (eTable != null) size += VM_Array.IntArray.getInstanceSize(eTable.length);
    if (referenceMaps != null) size += referenceMaps.size();
    return size;
  }
}
