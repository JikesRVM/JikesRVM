/*
 * (C) Copyright IBM Corp. 2001
 */
//$Id$

/**
 * Compiler-specific information associated with a method's machine 
 * instructions.
 *
 * @author Bowen Alpern
 */
class VM_BaselineCompilerInfo extends VM_CompilerInfo implements VM_BaselineConstants {

  //-----------//
  // interface //
  //-----------//
  // Get compiler that generated this method's machine code.
  //
  final int getCompilerType () {
    return  BASELINE;
  }

  // Get handler to deal with stack unwinding and exception delivery for this method's stackframes.
  //
  final VM_ExceptionDeliverer getExceptionDeliverer () {
    return  VM_Compiler.getExceptionDeliverer();
  }

  // Find "catch" block for a machine instruction of this method.
  //
  final int findCatchBlockForInstruction (int instructionOffset, VM_Type exceptionType) {
    if (tryStartInstructionOffsets == null)
      return  -1;               // method has no catch blocks
    for (int i = 0, n = tryStartInstructionOffsets.length; i < n; ++i) {
      // note that "instructionOffset" points to a return site (not a call site)
      // so the range check here must be "instructionOffset <= beg || instructionOffset >  end"
      // and not                         "instructionOffset <  beg || instructionOffset >= end"
      //
      if (instructionOffset <= tryStartInstructionOffsets[i] || instructionOffset > tryEndInstructionOffsets[i])
        continue;
      if (exceptionTypes[i] == null) {
        // catch block handles any exception
        return  catchInstructionOffsets[i];
      }
      VM_Type lhs = exceptionTypes[i];
      if (lhs.isInitialized()) {
        if (VM.BuildForFastDynamicTypeCheck) {
          if (VM_DynamicTypeCheck.instanceOfClass(lhs.asClass(), exceptionType.getTypeInformationBlock())) {
            return  catchInstructionOffsets[i];
          }
        } else {
	  try {
	    if (VM_Runtime.isAssignableWith(lhs, exceptionType)) {
	      return  catchInstructionOffsets[i];
	    }
	  } catch (VM_ResolutionException e) {
	    // cannot be thrown since lhs and rhs are initialized 
	    // thus no classloading will be performed
	  }
        }
      }
    }
    return  -1;
  }

  // Fetch symbolic reference to a method that's called by one of this method's instructions.
  // Taken:    place to put return information
  //           offset of machine instruction that issued the call
  // Returned: nothing
  //
  final void getDynamicLink (VM_DynamicLink dynamicLink, int instructionOffset) {
    int bytecodeIndex = -1;
    int instructionIndex = instructionOffset >>> LG_INSTRUCTION_WIDTH;
    for (int i = 0, n = _bytecodeMap.length; i < n; ++i) {
      if (_bytecodeMap[i] == 0)
        continue;               // middle of a bytecode
      if (_bytecodeMap[i] >= instructionIndex)
        break;                  // next bytecode
      bytecodeIndex = i;
    }
    byte[] bytecodes = method.getBytecodes();
    int bytecode = bytecodes[bytecodeIndex] & 0xFF;
    int constantPoolIndex = ((bytecodes[bytecodeIndex + 1] & 0xFF) << 8) | (bytecodes[
        bytecodeIndex + 2] & 0xFF);
    dynamicLink.set(method.getDeclaringClass().getMethodRef(constantPoolIndex), 
        bytecode);
  }

  // Find source line number corresponding to one of this method's machine instructions.
  //
  final int findLineNumberForInstruction (int instructionOffset) {
    if (lineInstructionOffsets == null)
      return  0;                // method has no line information
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
      return  0;                // not found
    return  lineNumbers[candidateIndex];
  }

  /** Find bytecode index corresponding to one of this method's 
   * machine instructions.
   *
   * Note: compile with saveBytecodeMap = true !
   * Note: This method expects the instructionIndex to refer to the machine
   * 	     instruction immediately FOLLOWING the bytecode in question.
   *	     just like getLineNumberForInstruction. See VM_CompilerInfo
   * 	     for rationale
   * NOTE: instructionIndex is in units of words, not bytes (different from
   *       all the other methods in this interface!!)
   *
   * @returns the bytecode index for the machine instruction, -1 if
   *		not available or not found.
   */
  final int findBytecodeIndexForInstruction (int instructionIndex) {
    if (_bytecodeMap == null)
      return  -1;               // method has no bytecode information
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
      return  -1;               // not found
    return  candidateIndex;
  }

  /** Find machine code offset in this method's machine instructions
   * given the bytecode index. 
   * Note: compile with saveBytecodeMap = true.
   * @returns machine code offset for the bytecode index, -1 if not
   * available or not found.
   */
  public int findInstructionForBytecodeIndex (int bcIndex) {
    if (saveBytecodeMap == false)
      return  -1; 
    else 
      return  _bytecodeMap[bcIndex];
  }

  // Find (earliest) machine instruction corresponding one of this method's source line numbers.
  //
  final int findInstructionForLineNumber (int lineNumber) {
    if (lineInstructionOffsets == null)
      return  -1;               // method has no line information
    for (int i = 0, n = lineNumbers.length; i < n; ++i) {
      if (lineNumbers[i] == lineNumber)
        return  lineInstructionOffsets[i];
    }
    return  -1;
  }

  // Find (earliest) machine instruction corresponding to the next valid source code line
  // following this method's source line numbers.
  // Return -1 if there is no more valid source code in this method
  final int findInstructionForNextLineNumber (int lineNumber) {
    if (lineInstructionOffsets == null)
      return  -1;               // method has no line information
    for (int i = 0, n = lineNumbers.length; i < n; ++i) {
      if (lineNumbers[i] == lineNumber)
        if (i == n - 1)         // last valid source code line, no next line
          return  -1; 
        else 
          return  lineInstructionOffsets[i + 1];
    }
    return  -1;
  }

  // Find local variables that are in scope of specified machine instruction.
  //
  public final VM_LocalVariable[] findLocalVariablesForInstruction (int instructionOffset) {
    if (localVariables == null)
      return  null;
    int count;
    // pass 1
    count = 0;
    for (int i = 0, n = localVariables.length; i < n; ++i)
      if (instructionOffset >= localStartInstructionOffsets[i] && instructionOffset <= localEndInstructionOffsets[i])
        count++;
      // pass 2
    VM_LocalVariable[] results = new VM_LocalVariable[count];
    count = 0;
    for (int i = 0, n = localVariables.length; i < n; ++i)
      if (instructionOffset >= localStartInstructionOffsets[i] && instructionOffset <= localEndInstructionOffsets[i])
        results[count++] = localVariables[i];
    return  results;
  }

  /**
   * Set the stack browser to the innermost logical stack frame of this method
   */
  final void set(VM_StackBrowser browser, int instr) {
    browser.setMethod( method );
    browser.setCompilerInfo( this );
    browser.setBytecodeIndex( findBytecodeIndexForInstruction(instr>>>LG_INSTRUCTION_WIDTH) );

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
  final boolean up(VM_StackBrowser browser) {
      return false;
  }

  // Print this compiled method's portion of a stack trace 
  // Taken:   offset of machine instruction from start of method
  //          the PrintStream to print the stack trace to.
  final void printStackTrace (int instructionOffset, java.io.PrintStream out) {
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
  final void printStackTrace (int instructionOffset, java.io.PrintWriter out) {
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
  //----------------//
  // implementation //
  //----------------//
  // The method that was compiled.
  //
  VM_Method method;
  // Stack-slot reference maps for that method.
  //
  VM_ReferenceMaps referenceMaps;
  // cache the bytecode map
  private final boolean saveBytecodeMap = true;                 // !!TODO: needed for dynamic bridge, eventually we should extract a condensed version of called-method-map
  private int[] _bytecodeMap;
  // Exception tables for use by "findCatchBlockForInstruction()".
  // Null tables mean "method has no try/catch/finally blocks".
  //
  private int[] tryStartInstructionOffsets;                     // instruction offset at which i-th try block begins
  // 0-indexed from start of method's instructions[]
  private int[] tryEndInstructionOffsets;       // instruction offset at which i-th try block ends (exclusive)
  // 0-indexed from end of method's instructions[]
  private int[] catchInstructionOffsets;        // instruction offset at which  exception handler for i-th try block begins
  // 0-indexed from handler of method's instructions[]
  private VM_Type[] exceptionTypes;             // exception type for which i-th handler is to be invoked
  // - something like "java/lang/IOException".
  // a null entry indicates a "finally block" (handler accepts all exceptions).
  // Line tables for use by "findLineNumberForInstruction()".
  // Note that line mappings for a method appear in order of increasing instruction offset.
  // The same line number can appear more than once (each with a different instruction offset).
  // Null tables mean "method has no line information".
  //
  int[] lineNumbers;            // line number at which each instruction sequence begins
  // 1-indexed from start of method's source file
  int[] lineInstructionOffsets;                 // instruction offset at which each instruction sequence begins
  // 0-indexed from start of method's instructions[]
  // Local variable tables for use by "findLocalVariablesForInstruction()"
  // Null tables mean "method has no local variable information".
  //
  VM_LocalVariable[] localVariables;            // local variables table provided by javac compiler
  int[] localStartInstructionOffsets;           // range of instructions for which...
  int[] localEndInstructionOffsets;             // ...i-th table entry is in scope (inclusive)
  // For synchronized methods, the offset (in the method prologue) after which
  // the monitor has been obtained.  At, or before, this point, the method does
  // not own the lock.  Used by deliverException to determine whether the lock
  // needs to be released.  Note: for this scheme to work, VM_Lock must not
  // allow a yield after it has been obtained.
  //
  int lockAcquisitionOffset = 0;                // used only for synchronized methods

  // Create compiled method description, for synchronized methods only.
  // Taken: method that was compiled
  //        bytecode-index to machine-instruction-index map for method
  //        number of instructions for method
  //        offset of the monitor acquisition instruction (in prologue)
  //
  VM_BaselineCompilerInfo (VM_Method method, VM_ReferenceMaps referenceMaps, 
			   int[] bytecodeMap, int numInstructions, 
			   int lockOffset) {
    this(method, referenceMaps, bytecodeMap, numInstructions);
    this.lockAcquisitionOffset = lockOffset;
  }

  // Create compiled method description.
  // Taken: method that was compiled
  //        bytecode-index to machine-instruction-index map for method
  //        number of instructions for method
  //
  VM_BaselineCompilerInfo (VM_Method method, VM_ReferenceMaps referenceMaps, 
			   int[] bytecodeMap, int numInstructions) {
    this.method = method;
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
      int[] startPCs = emap.startPCs;
      int[] endPCs = emap.endPCs;
      int[] handlerPCs = emap.handlerPCs;
      tryStartInstructionOffsets = new int[startPCs.length];
      tryEndInstructionOffsets = new int[endPCs.length];
      catchInstructionOffsets = new int[handlerPCs.length];
      for (int i = 0, n = startPCs.length; i < n; ++i) {
        tryStartInstructionOffsets[i] = (bytecodeMap[startPCs[i]] << VM.LG_INSTRUCTION_WIDTH);
        tryEndInstructionOffsets[i] = (bytecodeMap[endPCs[i]] << VM.LG_INSTRUCTION_WIDTH);
        catchInstructionOffsets[i] = (bytecodeMap[handlerPCs[i]] << VM.LG_INSTRUCTION_WIDTH);
      }
      exceptionTypes = emap.exceptionTypes;
    }
    // create line tables
    //
    VM_LineNumberMap lmap = method.getLineNumberMap();
    if (lmap != null) {
      int[] startPCs = lmap.startPCs;
      lineInstructionOffsets = new int[startPCs.length];
      for (int i = 0, n = startPCs.length; i < n; ++i)
        lineInstructionOffsets[i] = (bytecodeMap[startPCs[i]] << VM.LG_INSTRUCTION_WIDTH);
      lineNumbers = lmap.lineNumbers;
    }
    // create local variable tables
    //
    VM_LocalVariable vmap[] = method.getLocalVariables();
    if (vmap != null) {
      localVariables = vmap;
      localStartInstructionOffsets = new int[vmap.length];
      localEndInstructionOffsets = new int[vmap.length];
      for (int i = 0, n = vmap.length; i < n; ++i) {
        VM_LocalVariable v = vmap[i];
        localStartInstructionOffsets[i] = (bytecodeMap[v.startPC] << VM.LG_INSTRUCTION_WIDTH);
        localEndInstructionOffsets[i] = v.endPC < bytecodeMap.length ?
            (bytecodeMap[v.endPC] << VM.LG_INSTRUCTION_WIDTH) 
	  : (numInstructions << VM.LG_INSTRUCTION_WIDTH);
      }
    }
  }
}
