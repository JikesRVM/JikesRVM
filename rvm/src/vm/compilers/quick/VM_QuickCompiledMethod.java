/*
 * (C) Copyright IBM Corp. 2001, 2003, 2005
 */
//$Id$
package com.ibm.JikesRVM.quick;

import com.ibm.JikesRVM.*;
import com.ibm.JikesRVM.classloader.*;
import com.ibm.JikesRVM.PrintLN; // not needed.
import org.vmmagic.pragma.*;

/**
 * Compiler-specific information associated with a method's machine 
 * instructions (if the method has been quick compiled). Based on
 * VM_CompiledMethod.
 *
 * @author Chris Hoffmann
 */
public final class VM_QuickCompiledMethod extends VM_CompiledMethod 
  implements VM_QuickConstants {

  private static final int HAS_COUNTERS = 0x08000000;
  private static final int LOCK_OFFSET  = 0x00000fff;

  /**
   * Quick exception deliverer object
   */
  private static VM_ExceptionDeliverer exceptionDeliverer = new VM_QuickExceptionDeliverer();

  /**
   * Stack-slot reference maps for the compiled method.
   */
  public VM_QuickReferenceMaps referenceMaps;

  // the bytecode map; currently needed to support dynamic bridge magic; 
  // TODO: encode this densely like the opt compiler does.
  // Think about sharing some piece of the encoding code with opt???
  private int[] _bytecodeMap;

  /**
   * Exception table, null if not present.
   */
  private int[] eTable;

  int firstGPR;
  int lastGPR;
  int firstFPR;
  int lastFPR;

  //-#if RVM_WITH_OSR
  /* To make a compiled method's local/stack offset independ of
   * original method, we move 'getFirstLocalOffset' and 'getEmptyStackOffset'
   * here.
   */
  private int firstLocalOffset;
  private int emptyStackOffset;
 
  public int getFirstLocalOffset() {
    return firstLocalOffset;
  }
 
  public int getEmptyStackOffset() {
    return emptyStackOffset;
  }

  public VM_QuickCompiledMethod(int id, VM_Method m) {
    super(id, m);
    VM_NormalMethod nm = (VM_NormalMethod)m;
    this.firstLocalOffset = VM_Compiler.getFirstLocalOffset(nm);
    this.emptyStackOffset = VM_Compiler.getEmptyStackOffset(nm);
  }
  //-#else
  public VM_QuickCompiledMethod(int id, VM_Method m) {
    super(id, m);
  }
  //-#endif

  void setRegisterRanges(int firstGPR, int lastGPR,
                         int firstFPR, int lastFPR) {
    this.firstGPR = firstGPR;
    this.lastGPR = lastGPR;
    this.firstFPR = firstFPR;
    this.lastFPR = lastFPR;
  }

  public final int getCompilerType () throws UninterruptiblePragma {
    return QUICK;
  }

  public final String getCompilerName() {
    return "quick compiler";
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
    int bytecodeIndex = -1;
    int instructionIndex = instructionOffset >>> LG_INSTRUCTION_WIDTH;
    for (int i = 0, n = _bytecodeMap.length; i < n; ++i) {
      if (_bytecodeMap[i] == 0)
        continue;               // middle of a bytecode
      if (_bytecodeMap[i] >= instructionIndex)
        break;                  // next bytecode
      bytecodeIndex = i;
    }
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
    // since "instructionIndex" points just beyond the desired instruction,
    // we scan for the line whose "instructionIndex" most-closely-preceeds
    // the desired instruction
    //
    int candidateIndex = -1;
    for (int i = 0, n = _bytecodeMap.length; i < n; i++) {
      if (_bytecodeMap[i] >= instructionIndex)
        break;
      // remember index at which each bytecode starts
      if (_bytecodeMap[i] != 0)
        candidateIndex = i;
    }
    return candidateIndex;
  }

  /** 
   * Find machine code offset in this method's machine instructions
   * given the bytecode index. 
   * @return machine code offset for the bytecode index, -1 if not available or not found.
   */
  public int findInstructionForBytecodeIndex (int bcIndex) {
    return _bytecodeMap[bcIndex];
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
  public final void printStackTrace(int instructionOffset, 
                                    PrintLN out) 
  {
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
  public void encodeMappingInfo(VM_QuickReferenceMaps referenceMaps, 
                                int[] bytecodeMap, int numInstructions) {
    _bytecodeMap = bytecodeMap;
    referenceMaps.translateByte2Machine(bytecodeMap);
    this.referenceMaps = referenceMaps;
    VM_ExceptionHandlerMap emap = ((VM_NormalMethod)method).getExceptionHandlerMap();
    if (emap != null) {
      eTable = VM_QuickExceptionTable.encode(emap, bytecodeMap);
    }
  }

  private static final VM_TypeReference TYPE = VM_TypeReference.findOrCreate(VM_BootstrapClassLoader.getBootstrapClassLoader(),
                                                                             VM_Atom.findOrCreateAsciiAtom("Lcom/ibm/JikesRVM/quick/VM_QuickCompiledMethod;"));
  public int size() {
    int size = TYPE.peekResolvedType().asClass().getInstanceSize();
    if (_bytecodeMap != null) size += VM_Array.IntArray.getInstanceSize(_bytecodeMap.length);
    if (eTable != null) size += VM_Array.IntArray.getInstanceSize(eTable.length);
    if (referenceMaps != null) size += referenceMaps.size();
    return size;
  }
}
