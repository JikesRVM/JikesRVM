/*
 * (C) Copyright IBM Corp. 2001
 */
//$Id$

import instructionFormats.*;

/** 
 * An implementation of VM_CompiledMethod for the OPT compiler.
 *
 * <p> NOTE: VM_OptCompilerMethod live as long as their corresponding
 * compiled machine code.  Therefore, they should only contain
 * state that is really required to be persistent.  Anything
 * transitory should be stored on the OPT_IR object. 
 * 
 * @author Dave Grove
 * @author Mauricio Serrano
 */
final class VM_OptCompiledMethod extends VM_CompiledMethod
  implements OPT_Operators, VM_Uninterruptible  {

  VM_OptCompiledMethod(int id, VM_Method m) {
    super(id,m);    
  }

  /**
   * Get compiler that generated this method's machine code.
   */ 
  final int getCompilerType() {
    return VM_CompiledMethod.OPT;
  }

  /**
   * Get handler to deal with stack unwinding and exception delivery 
   * for this method's stackframes.
   */
  final VM_ExceptionDeliverer getExceptionDeliverer() {
    return exceptionDeliverer;
  }

  /**
   * Find "catch" block for a machine instruction of this method.
   */ 
  final int findCatchBlockForInstruction(int instructionOffset, 
					 VM_Type exceptionType) throws VM_PragmaInterruptible {
    if (_eMap == null) {
      return -1;
    } else {
      return _eMap.findCatchBlockForInstruction(instructionOffset, 
						exceptionType);
    }
  }

  /**
   * Fetch symbolic reference to a method that's called 
   * by one of this method's instructions.
   * @param dynamicLink place to put return information
   * @param instructionOffset offset of machine instruction that issued 
   *                          the call
   */ 
  final void getDynamicLink(VM_DynamicLink dynamicLink, int instructionOffset) {
    int bci = _mcMap.getBytecodeIndexForMCOffset(instructionOffset);
    VM_Method realMethod = _mcMap.getMethodForMCOffset(instructionOffset);
    if (bci == -1 || realMethod == null)
      VM.sysFail( "Mapping to source code location not available at Dynamic Linking point\n");
    byte[] bytecodes = realMethod.getBytecodes();
    int bc = bytecodes[bci] & 0xFF;
    int cpi = ((bytecodes[bci + 1] & 0xFF) << 8) | (bytecodes[bci + 2] & 0xFF);
    dynamicLink.set(realMethod.getDeclaringClass().getMethodRef(cpi), bc);
  }

  /**
   * Find source line number corresponding to one of this method's 
   * machine instructions.
   */
  final int findLineNumberForInstruction(int instructionOffset) {
    int bci = _mcMap.getBytecodeIndexForMCOffset(instructionOffset);
    if (bci < 0)
      return 0;
    VM_LineNumberMap lmap = method.getLineNumberMap();
    if (lmap == null)
      return 0;
    return lmap.getLineNumberForBCIndex(bci);
  }

  /**
   * Set the stack browser to the innermost logical stack frame of this method
   */
  final void set(VM_StackBrowser browser, int instr) throws VM_PragmaInterruptible {
    VM_OptMachineCodeMap map = getMCMap();
    int iei = map.getInlineEncodingForMCOffset(instr);
    if (iei >= 0) {
      int[] inlineEncoding = map.inlineEncoding;
      int mid = VM_OptEncodedCallSiteTree.getMethodID(iei, inlineEncoding);

      browser.setInlineEncodingIndex(iei);
      browser.setBytecodeIndex(map.getBytecodeIndexForMCOffset(instr));
      browser.setCompiledMethod(this);
      browser.setMethod(VM_MethodDictionary.getValue(mid));

      if (VM.TraceStackTrace) {
	  VM.sysWrite("setting stack to frame (opt): ");
	  VM.sysWrite( browser.getMethod() );
	  VM.sysWrite( browser.getBytecodeIndex() );
	  VM.sysWrite("\n");
      }
    } else {
      if (VM.VerifyAssertions) VM.assert(VM.NOT_REACHED);
    }
  }

  /**
   * Advance the VM_StackBrowser up one internal stack frame, if possible
   */
  final boolean up(VM_StackBrowser browser) throws VM_PragmaInterruptible {
    VM_OptMachineCodeMap map = getMCMap();
    int iei = browser.getInlineEncodingIndex();
    int[] ie = map.inlineEncoding;
    int next = VM_OptEncodedCallSiteTree.getParent(iei, ie);
    if (next >= 0) {
      int mid = VM_OptEncodedCallSiteTree.getMethodID(next, ie);
      int bci = VM_OptEncodedCallSiteTree.getByteCodeOffset(iei, ie);

      browser.setInlineEncodingIndex( next );
      browser.setBytecodeIndex( bci );
      browser.setMethod( VM_MethodDictionary.getValue(mid) );

      if (VM.TraceStackTrace) {
	  VM.sysWrite("up within frame stack (opt): ");
	  VM.sysWrite( browser.getMethod() );
	  VM.sysWrite( browser.getBytecodeIndex() );
	  VM.sysWrite("\n");
      }

      return true;
    }

    else
      return false;
  }

  /**
   * Print this compiled method's portion of a stack trace.
   * @param offset the offset of machine instruction from start of method
   * @param out    the PrintStream to print the stack trace to.
   */
  final void printStackTrace(int instructionOffset, java.io.PrintStream out) throws VM_PragmaInterruptible {
    VM_OptMachineCodeMap map = getMCMap();
    int iei = map.getInlineEncodingForMCOffset(instructionOffset);
    if (iei >= 0) {
      int[] inlineEncoding = map.inlineEncoding;
      int bci = map.getBytecodeIndexForMCOffset(instructionOffset);
      for (int j = iei; 
	   j >= 0; 
	   j = VM_OptEncodedCallSiteTree.getParent(j, inlineEncoding)) {
        int mid = VM_OptEncodedCallSiteTree.getMethodID(j, inlineEncoding);
        VM_Method m = VM_MethodDictionary.getValue(mid);
        VM_LineNumberMap lmap = m.getLineNumberMap();
        int lineNumber = 0;
        if (lmap != null) {
          lineNumber = lmap.getLineNumberForBCIndex(bci);
        }
        out.println("\tat " 
		    + m.getDeclaringClass().getDescriptor().classNameFromDescriptor()
		    + "." + m.getName() + " (" + 
		    m.getDeclaringClass().getSourceName() + ":" + 
		    lineNumber + ")");
        if (j > 0) {
          bci = VM_OptEncodedCallSiteTree.getByteCodeOffset(j, inlineEncoding);
        }
      }
    } else {
      out.println("\tat " 
		  + method.getDeclaringClass().getDescriptor().
		  classNameFromDescriptor()
		  + "." + method.getName() + " (" 
		  + method.getDeclaringClass().getSourceName()
		  + ": machine code offset " + 
		  VM.intAsHexString(instructionOffset)
		  + ")");
    }
  }

  /**
   * Print this compiled method's portion of a stack trace.
   * @param instructionOffset offset of machine instruction from start of method
   * @param out               the PrintWriter to print the stack trace to.
   */
  final void printStackTrace(int instructionOffset, java.io.PrintWriter out) throws VM_PragmaInterruptible {
    VM_OptMachineCodeMap map = getMCMap();
    int iei = map.getInlineEncodingForMCOffset(instructionOffset);
    if (iei >= 0) {
      int[] inlineEncoding = map.inlineEncoding;
      int bci = map.getBytecodeIndexForMCOffset(instructionOffset);
      for (int j = iei; 
	   j >= 0; 
	   j = VM_OptEncodedCallSiteTree.getParent(j, inlineEncoding)) {
        int mid = VM_OptEncodedCallSiteTree.getMethodID(j, inlineEncoding);
        VM_Method m = VM_MethodDictionary.getValue(mid);
        VM_LineNumberMap lmap = m.getLineNumberMap();
        int lineNumber = 0;
        if (lmap != null) {
          lineNumber = lmap.getLineNumberForBCIndex(bci);
        }
        out.println("\tat " 
		    + m.getDeclaringClass().getDescriptor().classNameFromDescriptor()
		    + "." + m.getName() + " (" + 
		    m.getDeclaringClass().getSourceName() + ":" + 
		    lineNumber + ")");
        if (j > 0) {
          bci = VM_OptEncodedCallSiteTree.getByteCodeOffset(j, inlineEncoding);
        }
      }
    } else {
      out.println("\tat " 
		  + method.getDeclaringClass().getDescriptor().
		  classNameFromDescriptor()
		  + "." + method.getName() + " (" 
		  + method.getDeclaringClass().getSourceName()
		  + ": machine code offset " + 
		  VM.intAsHexString(instructionOffset) + ")");
    }
  }

  private static final VM_Class TYPE = VM_ClassLoader.findOrCreateType(VM_Atom.findOrCreateAsciiAtom("LVM_ExceptionTable;"), VM_SystemClassLoader.getVMClassLoader()).asClass();
  final int size() throws VM_PragmaInterruptible {
    int size = TYPE.getInstanceSize();
    size += _mcMap.size();
    if (_eMap != null) size += _eMap.size();
    //-#if RVM_FOR_IA32
    if (patchMap != null) size += VM_Array.arrayOfIntType.getInstanceSize(patchMap.length);
    //-#endif
    return size;
  }

  /**
   * Get the offset for the end of the prologue
   */
  final int getEndPrologueOffset() {
    return bitField1 & AVAIL_BITS;
  }

  /**
   * Get the offset for the end of the prologue
   * @param endPrologue
   */
  final void setEndPrologueOffset(int endPrologue) {
    bitField1 |= endPrologue & AVAIL_BITS;
  }

  //----------------//
  // implementation //
  //----------------//
  private static final VM_OptExceptionDeliverer exceptionDeliverer = 
    new VM_OptExceptionDeliverer();

  //////////////////////////////////////
  // Information the opt compiler needs to persistently associate 
  // with a particular compiled method.
  
  /** The primary machine code maps */
  private VM_OptMachineCodeMap _mcMap;
  /** The encoded exception tables (null if there are none) */
  private VM_OptExceptionTable _eMap;
  //-#if RVM_FOR_IA32
  private int[] patchMap;
  //-#endif

  // 64 bits to encode other tidbits about the method. Current usage is:
  // SSSS SSSS SSSS SSSU VOOO FFFF FFII IIII EEEE EEEE EEEE EEEE NNNN NNNN NNNN NNNN
  // N = unsigned offset (off the framepointer) of nonvolatile save area in bytes
  // E = unsigned offset (off the framepointer) of caught exception object in bytes
  // I = first saved nonvolatile integer register (assume 64 or fewer int registers).
  // F = first saved nonvolatile floating point register (assume 64 or  fewer fp registers)
  // O = opt level at which the method was compiled (assume max of 8 opt levels)
  // V = were the volatile registers saved? (1 = true, 0 = false)
  // U = Is the current method executing with instrumentation (1 = yes, 0 = no)
  // S = size of the fixed portion of the stackframe.

  private long _bits;
  private static final long NONVOLATILE_MASK     = 0x000000000000ffffL;
  private static final int  NONVOLATILE_SHIFT    = 0;
  private static final long EXCEPTION_OBJ_MASK   = 0x00000000ffff0000L;
  private static final int  EXCEPTION_OBJ_SHIFT  = 16;
  private static final long INTEGER_MASK         = 0x0000003f00000000L;
  private static final int  INTEGER_SHIFT        = 32;   
  private static final long FLOAT_MASK           = 0x00000fc000000000L;
  private static final int  FLOAT_SHIFT          = 38;
  private static final long OPT_LEVEL_MASK       = 0x0000700000000000L;
  private static final int  OPT_LEVEL_SHIFT      = 44;
  private static final long SAVE_VOLATILE_MASK   = 0x0000800000000000L;
  private static final long INSTRU_METHOD_MASK   = 0x0001000000000000L;
  private static final long FIXED_SIZE_MASK      = 0xfffe000000000000L;
  private static final int  FIXED_SIZE_SHIFT     = 49;
  
  private static final int NO_INTEGER_ENTRY = (int)(INTEGER_MASK >>> INTEGER_SHIFT);
  private static final int NO_FLOAT_ENTRY   = (int)(FLOAT_MASK >>> FLOAT_SHIFT);

  final int getUnsignedNonVolatileOffset() {
    return (int)((_bits & NONVOLATILE_MASK) >>> NONVOLATILE_SHIFT);
  }
  final int getUnsignedExceptionOffset() {
    return (int)((_bits & EXCEPTION_OBJ_MASK) >>> EXCEPTION_OBJ_SHIFT);
  }
  final int getFirstNonVolatileGPR() {
    int t = (int)((_bits & INTEGER_MASK) >>> INTEGER_SHIFT);
    return (t == NO_INTEGER_ENTRY) ? -1 : t;
  }
  final int getFirstNonVolatileFPR() {
    int t = (int)((_bits & FLOAT_MASK) >>> FLOAT_SHIFT);
    return (t == NO_FLOAT_ENTRY) ? -1 : t;
  }
  final int getOptLevel() {
    return (int)((_bits & OPT_LEVEL_MASK) >>> OPT_LEVEL_SHIFT);
  }
  final boolean isSaveVolatile() {
    return (_bits & SAVE_VOLATILE_MASK) != 0L;
  }
  final boolean isInstrumentedMethod() {
    return (_bits & INSTRU_METHOD_MASK) != 0L;
  }
  final int getFrameFixedSize() {
    return (int)((_bits & FIXED_SIZE_MASK) >>> FIXED_SIZE_SHIFT);
  }


  final void setUnsignedNonVolatileOffset(int x) {
    if (VM.VerifyAssertions) VM.assert(x >= 0 && x < (NONVOLATILE_MASK >>> NONVOLATILE_SHIFT));
    _bits = (_bits & ~NONVOLATILE_MASK) | (((long)x) << NONVOLATILE_SHIFT);
  }
  final void setUnsignedExceptionOffset(int x) {
    if (VM.VerifyAssertions) VM.assert(x >= 0 && x < (EXCEPTION_OBJ_MASK >>> EXCEPTION_OBJ_SHIFT));
    _bits = (_bits & ~EXCEPTION_OBJ_MASK) | (((long)x) << EXCEPTION_OBJ_SHIFT);
  }
  final void setFirstNonVolatileGPR(int x) {
    if (x == -1) {
      _bits |= INTEGER_MASK;
    } else {
      if (VM.VerifyAssertions) VM.assert(x >= 0 && x < NO_INTEGER_ENTRY);
      _bits = (_bits & ~INTEGER_MASK) | (((long)x) << INTEGER_SHIFT);
    }
  }
  final void setFirstNonVolatileFPR(int x) {
    if (x == -1) {
      _bits |= FLOAT_MASK;
    } else {
      if (VM.VerifyAssertions) VM.assert(x >= 0 && x < NO_FLOAT_ENTRY);
      _bits = (_bits & ~FLOAT_MASK) | (((long)x) << FLOAT_SHIFT);
    }
  }
  final void setOptLevel(int x) {
    if (VM.VerifyAssertions) VM.assert(x >= 0 && x < (OPT_LEVEL_MASK >>> OPT_LEVEL_SHIFT));
    _bits = (_bits & ~OPT_LEVEL_MASK) | (((long)x) << OPT_LEVEL_SHIFT);
  }
  final void setSaveVolatile(boolean sv) {
    if (sv) 
      _bits |= SAVE_VOLATILE_MASK;
    else 
      _bits &= ~SAVE_VOLATILE_MASK;
  }
  final void setInstrumentedMethod(boolean sv) {
    if (sv) 
      _bits |= INSTRU_METHOD_MASK;
    else 
      _bits &= ~INSTRU_METHOD_MASK;
  }
  final void setFrameFixedSize(int x) {
    if (VM.VerifyAssertions) VM.assert(x >= 0 && x < (FIXED_SIZE_MASK >>> FIXED_SIZE_SHIFT));
    _bits = (_bits & ~FIXED_SIZE_MASK) | (((long)x) << FIXED_SIZE_SHIFT);
  }
  
  /**
   * Return the number of non-volatile GPRs used by this method.
   */
  int getNumberOfNonvolatileGPRs() {
    //-#if RVM_FOR_POWERPC
    return VM_RegisterConstants.NUM_GPRS - getFirstNonVolatileGPR();
    //-#elif RVM_FOR_IA32
    return VM_RegisterConstants.NUM_NONVOLATILE_GPRS - getFirstNonVolatileGPR();
    //-#endif
  }
  /**
   * Return the number of non-volatile FPRs used by this method.
   */
  int getNumberOfNonvolatileFPRs() {
    //-#if RVM_FOR_POWERPC
    return VM_RegisterConstants.NUM_FPRS - getFirstNonVolatileFPR();
    //-#elif RVM_FOR_IA32
    return VM_RegisterConstants.NUM_NONVOLATILE_FPRS - getFirstNonVolatileFPR();
    //-#endif
  }
  /**
   * Set the number of non-volatile GPRs used by this method.
   */
  void setNumberOfNonvolatileGPRs(short n) {
    //-#if RVM_FOR_POWERPC
    setFirstNonVolatileGPR(VM_RegisterConstants.NUM_GPRS - n);
    //-#elif RVM_FOR_IA32
    setFirstNonVolatileGPR(VM_RegisterConstants.NUM_NONVOLATILE_GPRS - n);
    //-#endif
  }
  /**
   * Set the number of non-volatile FPRs used by this method.
   */
  void setNumberOfNonvolatileFPRs(short n) {
    //-#if RVM_FOR_POWERPC
    setFirstNonVolatileFPR(VM_RegisterConstants.NUM_FPRS - n);
    //-#elif RVM_FOR_IA32
    setFirstNonVolatileFPR(VM_RegisterConstants.NUM_NONVOLATILE_FPRS - n);
    //-#endif
  }


  /**
   * @return the machine code map for the compiled method.
   */
  public final VM_OptMachineCodeMap getMCMap () {
    return _mcMap;
  }

  /**
   * @return the encoded exeption table for the compiled method.
   */
  public VM_OptExceptionTable getExceptionTable () {
    return _eMap;
  }

  /**
   * Create the final machine code map for the compiled method.
   * Remember the offset for the end of prologue too for jdp
   * @param ir the ir 
   * @param machineCodeLength the number of machine code instructions.
   */
  public final void createFinalMCMap (OPT_IR ir, int machineCodeLength) throws VM_PragmaInterruptible {
    _mcMap = new VM_OptMachineCodeMap(ir, machineCodeLength);
    setEndPrologueOffset(ir.MIRInfo.instAfterPrologue.getmcOffset());
  }

  /**
   * Create the final exception table from the IR for the method.
   * @param ir the ir 
   */
  public final void createFinalExceptionTable (OPT_IR ir) throws VM_PragmaInterruptible {
    if (ir.hasReachableExceptionHandlers()) {
      _eMap = new VM_OptExceptionTable(ir);
    }
  }

  //-#if RVM_FOR_IA32
  /**
   * Create the code patching maps from the IR for the method
   * @param ir the ir 
   */
  public final void createCodePatchMaps(OPT_IR ir) throws VM_PragmaInterruptible {
    // (1) count the patch points
    int patchPoints = 0;
    for (OPT_Instruction s = ir.firstInstructionInCodeOrder();
	 s != null;
	 s = s.nextInstructionInCodeOrder()) {
      if (s.operator() == IG_PATCH_POINT) {
	patchPoints++;
      }
    }
    // (2) if we have patch points, create the map.
    if (patchPoints != 0) {
      patchMap = new int[patchPoints*2];
      int idx = 0;
      for (OPT_Instruction s = ir.firstInstructionInCodeOrder();
	   s != null;
	   s = s.nextInstructionInCodeOrder()) {
	if (s.operator() == IG_PATCH_POINT) {
	  int patchPoint = s.getmcOffset();
	  int newTarget = InlineGuard.getTarget(s).target.getmcOffset();
	  // A patch map is the offset of the last byte of the patch point
	  // and the new branch immediate to lay down if the code is ever patched.
	  patchMap[idx++] = patchPoint-1;
	  patchMap[idx++] = newTarget - patchPoint;
	}
      }
    }
  }

  /**
   * Apply the code patches to the INSTRUCTION array of cm
   */
  public final void applyCodePatches(VM_CompiledMethod cm) throws VM_PragmaInterruptible {
    if (patchMap != null) {
      INSTRUCTION[] code = cm.getInstructions();
      for (int idx=0; idx<patchMap.length; idx += 2) {
	VM_Assembler.patchCode(code, patchMap[idx], patchMap[idx+1]);
      }
    }
  }
  //-#endif

}
