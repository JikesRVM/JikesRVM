/*
 * (C) Copyright IBM Corp. 2001, 2003
 */
//$Id$
package com.ibm.JikesRVM;

import com.ibm.JikesRVM.classloader.*;
import com.ibm.JikesRVM.PrintLN; // not needed.

import org.vmmagic.pragma.*;

/**
 * Compiler-specific information associated with a method's machine 
 * instructions.
 *
 * @author Bowen Alpern
 * @modified Steven Augart
 */
public final class VM_BaselineCompiledMethod extends VM_CompiledMethod 
  implements VM_BaselineConstants {

  private static final int HAS_COUNTERS = 0x08000000;
  private static final int LOCK_OFFSET  = 0x00000fff;

  /**
   * Baseline exception deliverer object
   */
  private static VM_ExceptionDeliverer exceptionDeliverer = new VM_BaselineExceptionDeliverer();

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

  //-#if RVM_WITH_OSR
  /* To make a compiled method's local/stack offset independ of
   * original method, we move 'getStartLocalOffset' and 'getEmptyStackOffset'
   * here.
   */
  private int startLocalOffset;
  private int emptyStackOffset;
 
  public int getStartLocalOffset() {
    return startLocalOffset;
  }
 
  public int getEmptyStackOffset() {
    return emptyStackOffset;
  }

  public VM_BaselineCompiledMethod(int id, VM_Method m) {
    super(id, m);
    VM_NormalMethod nm = (VM_NormalMethod)m;
    this.startLocalOffset = VM_Compiler.getStartLocalOffset(nm);
    this.emptyStackOffset = VM_Compiler.getEmptyStackOffset(nm);
  }
  //-#else
  public VM_BaselineCompiledMethod(int id, VM_Method m) {
    super(id, m);
  }
  //-#endif

  public final int getCompilerType () throws UninterruptiblePragma {
    return BASELINE;
  }

  public final String getCompilerName() {
    return "baseline compiler";
  }

  public final VM_ExceptionDeliverer getExceptionDeliverer () throws UninterruptiblePragma {
    return exceptionDeliverer;
  }

  public final int findCatchBlockForInstruction (int instructionOffset, VM_Type exceptionType) {
    if (eTable == null) {
      return -1;
    } else {
      return VM_ExceptionTable.findCatchBlockForInstruction(eTable, instructionOffset, exceptionType);
    }
  }

  public final void getDynamicLink (VM_DynamicLink dynamicLink, int instructionOffset) throws UninterruptiblePragma {
    int instructionIndex = instructionOffset >>> LG_INSTRUCTION_WIDTH;
    int bytecodeIndex = findBytecodeIndexForInstruction(instructionIndex);
    ((VM_NormalMethod)method).getDynamicLink(dynamicLink, bytecodeIndex);
  }

  public final int findLineNumberForInstruction (int instructionOffset) throws UninterruptiblePragma {
    int instructionIndex = instructionOffset >>> LG_INSTRUCTION_WIDTH; 
    int bci = findBytecodeIndexForInstruction(instructionIndex);
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
  public final int findBytecodeIndexForInstruction (int instructionIndex) throws UninterruptiblePragma {
    int candidateIndex = -1;
    int bcIndex = 0, instrIndex = 0;
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
      instrIndex += deltaIns;
      if (instrIndex >= instructionIndex)
        break;
      candidateIndex = bcIndex;
    }
    return candidateIndex;
  }

  /**
   * Set the stack browser to the innermost logical stack frame of this method
   */
  public final void set(VM_StackBrowser browser, int instr) {
    browser.setMethod(method);
    browser.setCompiledMethod(this);
    browser.setBytecodeIndex(findBytecodeIndexForInstruction(instr>>>LG_INSTRUCTION_WIDTH));

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
  public final boolean up(VM_StackBrowser browser) {
    return false;
  }

  // Print this compiled method's portion of a stack trace 
  // Taken:   offset of machine instruction from start of method
  //          the PrintLN to print the stack trace to.
  public final void printStackTrace(int instructionOffset, PrintLN out) {
    out.print("\tat ");
    out.print(method.getDeclaringClass()); // VM_Class
    out.print('.');
    out.print(method.getName()); // a VM_Atom, returned via VM_MemberReference.getName().
    out.print("(");
    out.print(method.getDeclaringClass().getSourceName()); // a VM_Atom
    int lineNumber = findLineNumberForInstruction(instructionOffset);
    if (lineNumber <= 0) {      // unknown line
      out.print("; machine code offset: ");
      out.printHex(instructionOffset);
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
  public final void printExceptionTable() {
    if (eTable != null) VM_ExceptionTable.printExceptionTable(eTable);
  }

  // We use the available bits in bitField1 to encode the lock acquistion offset
  // for synchronized methods
  // For synchronized methods, the offset (in the method prologue) after which
  // the monitor has been obtained.  At, or before, this point, the method does
  // not own the lock.  Used by deliverException to determine whether the lock
  // needs to be released.  Note: for this scheme to work, VM_Lock must not
  // allow a yield after it has been obtained.
  public void setLockAcquisitionOffset(int off) {
    if (VM.VerifyAssertions) VM._assert((off & LOCK_OFFSET) == off);
    bitField1 |= (off & LOCK_OFFSET);
  }

  public int getLockAcquisitionOffset() {
    return bitField1 & LOCK_OFFSET;
  }

  void setHasCounterArray() {
    bitField1 |= HAS_COUNTERS;
  }

  boolean hasCounterArray() throws UninterruptiblePragma {
    return (bitField1 & HAS_COUNTERS) != 0;
  }

  // Taken: method that was compiled
  //        bytecode-index to machine-instruction-index map for method
  //        number of instructions for method
  //
    static int counter = 0;
    static int goodCount = 0;
    static int badBCCount = 0;
        static int badInsCount = 0;
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

  private static final VM_TypeReference TYPE = VM_TypeReference.findOrCreate(VM_SystemClassLoader.getVMClassLoader(),
                                                                             VM_Atom.findOrCreateAsciiAtom("Lcom/ibm/JikesRVM/VM_BaselineCompiledMethod;"));
  public int size() {
    int size = TYPE.peekResolvedType().asClass().getInstanceSize();
    if (bytecodeMap != null) size += VM_Array.ByteArray.getInstanceSize(bytecodeMap.length);
    if (eTable != null) size += VM_Array.IntArray.getInstanceSize(eTable.length);
    if (referenceMaps != null) size += referenceMaps.size();
    return size;
  }
}
