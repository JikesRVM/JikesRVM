/*
 * (C) Copyright IBM Corp. 2001
 */
//$Id$
package com.ibm.JikesRVM;

import com.ibm.JikesRVM.classloader.*;

/**
 * Compiler-specific information associated with a method's machine 
 * instructions.
 *
 * @author Bowen Alpern
 */
final public class VM_BaselineCompiledMethod extends VM_CompiledMethod 
  implements VM_BaselineConstants {

  // !!TODO: needed for dynamic bridge, eventually we should extract a condensed version of called-method-map!!!
  // This is wasting a lot of space!
  private static final boolean saveBytecodeMap = true;
  
  private static final int HAS_COUNTERS = 0x08000000;
  private static final int LOCK_OFFSET  = 0x00000fff;

  /**
   * Baseline exception deliverer object
   */
  private static VM_ExceptionDeliverer exceptionDeliverer = new VM_BaselineExceptionDeliverer();

  /**
   * Stack-slot reference maps for the compiled method.
   */
  VM_ReferenceMaps referenceMaps;

  // the bytecode map; currently needed to support dynamic bridge magic; 
  private int[] _bytecodeMap;

  /**
   * Exception table, null if not present.
   */
  private int[] eTable;

  /**
   * Line tables for use by "findLineNumberForInstruction()".
   * Note that line mappings for a method appear in order of increasing instruction offset.
   * The same line number can appear more than once (each with a different instruction offset).
   * Null tables mean "method has no line information".
   * The instruction offset at which each instruction sequence begins.
   */
  private int[] lineInstructionOffsets;    
  
  // Local variable tables for use by "findLocalVariablesForInstruction()"
  // Null tables mean "method has no local variable information".
  private int[] localStartInstructionOffsets;           // range of instructions for which...
  private int[] localEndInstructionOffsets;             // ...i-th table entry is in scope (inclusive)


  public VM_BaselineCompiledMethod(int id, VM_Method m) {
    super(id, m);
  }

  public final int getCompilerType () throws VM_PragmaUninterruptible {
    return BASELINE;
  }

  public final VM_ExceptionDeliverer getExceptionDeliverer () throws VM_PragmaUninterruptible {
    return exceptionDeliverer;
  }

  public final int findCatchBlockForInstruction (int instructionOffset, VM_Type exceptionType) {
    if (eTable == null) {
      return -1;
    } else {
      return VM_ExceptionTable.findCatchBlockForInstruction(eTable, instructionOffset, exceptionType);
    }
  }

  public final void getDynamicLink (VM_DynamicLink dynamicLink, int instructionOffset) throws VM_PragmaUninterruptible {
    int bytecodeIndex = -1;
    int instructionIndex = instructionOffset >>> LG_INSTRUCTION_WIDTH;
    for (int i = 0, n = _bytecodeMap.length; i < n; ++i) {
      if (_bytecodeMap[i] == 0)
        continue;               // middle of a bytecode
      if (_bytecodeMap[i] >= instructionIndex)
        break;                  // next bytecode
      bytecodeIndex = i;
    }
    byte[] bytecodes = method.getRawBytecodes();
    int bytecode = bytecodes[bytecodeIndex] & 0xFF;
    int constantPoolIndex = ((bytecodes[bytecodeIndex + 1] & 0xFF) << 8) | (bytecodes[bytecodeIndex + 2] & 0xFF);
    dynamicLink.set(method.getDeclaringClass().getMethodRef(constantPoolIndex), bytecode);
  }

  public final int findLineNumberForInstruction (int instructionOffset) throws VM_PragmaUninterruptible {
    if (lineInstructionOffsets == null)
      return 0;                // method has no line information
    // since "instructionOffset" points just beyond the desired instruction,
    // we scan for the line whose "instructionOffset" most-closely-preceeds 
    // the desired instruction
    //
    int[] instructionOffsets = lineInstructionOffsets;
    int candidateIndex = -1;
    for (int i = 0, n = instructionOffsets.length; i < n; ++i) {
      if (instructionOffsets[i] >= instructionOffset)
        break;
      candidateIndex = i;
    }
    if (candidateIndex == -1)
      return 0;                // not found
    return method.getLineNumberMap().lineNumbers[candidateIndex];
  }

  /** Find bytecode index corresponding to one of this method's 
   * machine instructions.
   *
   * Note: compile with saveBytecodeMap = true !
   * Note: This method expects the instructionIndex to refer to the machine
   * 	     instruction immediately FOLLOWING the bytecode in question.
   *	     just like getLineNumberForInstruction. See VM_CompiledMethod
   * 	     for rationale
   * NOTE: instructionIndex is in units of words, not bytes (different from
   *       all the other methods in this interface!!)
   *
   * @returns the bytecode index for the machine instruction, -1 if
   *		not available or not found.
   */
  public final int findBytecodeIndexForInstruction (int instructionIndex) {
    if (_bytecodeMap == null)
      return -1;               // method has no bytecode information
    // since "instructionIndex" points just beyond the desired instruction,
    // we scan for the line whose "instructionIndex" most-closely-preceeds
    // the desired instruction
    //
    int candidateIndex = -1;
    for (int i = 0, n = _bytecodeMap.length; i < n; ++i) {
      if (_bytecodeMap[i] >= instructionIndex)
        break;
      // remember index at which each bytecode starts
      if (_bytecodeMap[i] != 0)
        candidateIndex = i;
    }
    if (candidateIndex == -1)
      return -1;               // not found
    return candidateIndex;
  }

  /** Find machine code offset in this method's machine instructions
   * given the bytecode index. 
   * Note: compile with saveBytecodeMap = true.
   * @returns machine code offset for the bytecode index, -1 if not
   * available or not found.
   */
  public int findInstructionForBytecodeIndex (int bcIndex) {
    if (saveBytecodeMap == false)
      return -1; 
    else 
      return _bytecodeMap[bcIndex];
  }

  // Find (earliest) machine instruction corresponding one of this method's source line numbers.
  //
  public final int findInstructionForLineNumber (int lineNumber) {
    if (lineInstructionOffsets == null)
      return -1;               // method has no line information
    int[] lineNumbers = method.getLineNumberMap().lineNumbers;
    for (int i = 0, n = lineNumbers.length; i < n; ++i) {
      if (lineNumbers[i] == lineNumber)
        return lineInstructionOffsets[i];
    }
    return -1;
  }

  // Find (earliest) machine instruction corresponding to the next valid source code line
  // following this method's source line numbers.
  // Return -1 if there is no more valid source code in this method
  public final int findInstructionForNextLineNumber (int lineNumber) {
    if (lineInstructionOffsets == null)
      return -1;               // method has no line information
    int[] lineNumbers = method.getLineNumberMap().lineNumbers;
    for (int i = 0, n = lineNumbers.length; i < n; ++i) {
      if (lineNumbers[i] == lineNumber)
        if (i == n - 1)         // last valid source code line, no next line
          return -1; 
        else 
          return lineInstructionOffsets[i + 1];
    }
    return -1;
  }

  // Find local variables that are in scope of specified machine instruction.
  //
  public final VM_LocalVariable[] findLocalVariablesForInstruction (int instructionOffset) {
    VM_LocalVariable[] localVariables = method.getLocalVariables();
    if (localVariables == null)
      return null;
    // pass 1
    int count = 0;
    for (int i = 0, n = localVariables.length; i < n; ++i)
      if (instructionOffset >= localStartInstructionOffsets[i] && instructionOffset <= localEndInstructionOffsets[i])
        count++;
      // pass 2
    VM_LocalVariable[] results = new VM_LocalVariable[count];
    count = 0;
    for (int i = 0, n = localVariables.length; i < n; ++i)
      if (instructionOffset >= localStartInstructionOffsets[i] && instructionOffset <= localEndInstructionOffsets[i])
        results[count++] = localVariables[i];
    return results;
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
  //          the PrintStream to print the stack trace to.
  public final void printStackTrace (int instructionOffset, java.io.PrintStream out) {
    int lineNumber = findLineNumberForInstruction(instructionOffset);
    if (lineNumber <= 0) {      // unknown line
      out.println("\tat " + method + " (offset: " + VM.intAsHexString(instructionOffset)
          + ")");
    } else {      // print class name + method name + file name + line number
      out.println("\tat " + method.getDeclaringClass().getDescriptor().classNameFromDescriptor()
          + "." + method.getName() + " (" + method.getDeclaringClass().getSourceName()
          + ":" + lineNumber + ")");
    }
  }

  // Print this compiled method's portion of a stack trace 
  // Taken:   offset of machine instruction from start of method
  //          the PrintWriter to print the stack trace to.
  public final void printStackTrace (int instructionOffset, java.io.PrintWriter out) {
    int lineNumber = findLineNumberForInstruction(instructionOffset);
    if (lineNumber <= 0) {      // unknown line
      out.println("\tat " + method + " (offset: " + VM.intAsHexString(instructionOffset)
          + ")");
    } else {      // print class name + method name + file name + line number
      out.println("\tat " + method.getDeclaringClass().getDescriptor().classNameFromDescriptor()
          + "." + method.getName() + " (" + method.getDeclaringClass().getSourceName()
          + ":" + lineNumber + ")");
    }
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

  boolean hasCounterArray() {
    return (bitField1 & HAS_COUNTERS) != 0;
  }

  // Taken: method that was compiled
  //        bytecode-index to machine-instruction-index map for method
  //        number of instructions for method
  //
  public void encodeMappingInfo(VM_ReferenceMaps referenceMaps, 
			 int[] bytecodeMap, int numInstructions) {
    if (saveBytecodeMap)
      _bytecodeMap = bytecodeMap;
    // create stack-slot reference map
    //
    referenceMaps.translateByte2Machine(bytecodeMap);
    this.referenceMaps = referenceMaps;
    

    // create exception tables
    //
    VM_ExceptionHandlerMap emap = method.getExceptionHandlerMap();
    if (emap != null) {
      eTable = VM_BaselineExceptionTable.encode(emap, bytecodeMap);
    }

    // create line tables
    //
    VM_LineNumberMap lmap = method.getLineNumberMap();
    if (lmap != null) {
      int[] startPCs = lmap.startPCs;
      lineInstructionOffsets = new int[startPCs.length];
      for (int i = 0, n = startPCs.length; i < n; ++i)
        lineInstructionOffsets[i] = (bytecodeMap[startPCs[i]] << VM.LG_INSTRUCTION_WIDTH);
    }

    // create local variable tables
    //
    VM_LocalVariable[] vmap = method.getLocalVariables();
    if (vmap != null) {
      localStartInstructionOffsets = new int[vmap.length];
      localEndInstructionOffsets = new int[vmap.length];
      for (int i = 0, n = vmap.length; i < n; ++i) {
        VM_LocalVariable v = vmap[i];
        localStartInstructionOffsets[i] = bytecodeMap[v.startPC] << VM.LG_INSTRUCTION_WIDTH;
	if (v.endPC < bytecodeMap.length) {
	  localEndInstructionOffsets[i] = bytecodeMap[v.endPC] << VM.LG_INSTRUCTION_WIDTH;
	} else { 
	  localEndInstructionOffsets[i] = numInstructions << VM.LG_INSTRUCTION_WIDTH;
	}
      }
    }
  }

  private static final VM_Class TYPE = VM_ClassLoader.findOrCreateType(VM_Atom.findOrCreateAsciiAtom("Lcom/ibm/JikesRVM/VM_BaselineCompiledMethod;"), VM_SystemClassLoader.getVMClassLoader()).asClass();
  public int size() {
    VM_Array intArray = VM_Array.arrayOfIntType;
    int size = TYPE.getInstanceSize();
    if (_bytecodeMap != null) size += intArray.getInstanceSize(_bytecodeMap.length);
    if (eTable != null) size += intArray.getInstanceSize(eTable.length);
    if (lineInstructionOffsets != null) size += intArray.getInstanceSize(lineInstructionOffsets.length);
    if (referenceMaps != null) size += referenceMaps.size();
    if (localStartInstructionOffsets != null) size += intArray.getInstanceSize(localStartInstructionOffsets.length);
    if (localEndInstructionOffsets != null) size += intArray.getInstanceSize(localEndInstructionOffsets.length);
    return size;
  }
}
