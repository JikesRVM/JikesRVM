/*
 * (C) Copyright IBM Corp. 2001
 */
//$Id$

/**
 * VM_Compiler is the baseline compiler class for powerPC architectures.
 * The compiler() method translates the bytecodes of a method to 
 * straightforward machine code.
 * 
 * @author Bowen Alpern
 * @author Derek Lieber
 */
public class VM_Compiler implements VM_BaselineConstants {

  //-----------//
  // interface //
  //-----------//
   
  static VM_CompiledMethod compile (VM_Method method) {
    int compiledMethodId = VM_CompiledMethods.createCompiledMethodId();
    if (method.isNative()) {
      VM_MachineCode machineCode = VM_JNICompiler.generateGlueCodeForNative
        (compiledMethodId, method);
      VM_CompilerInfo info = new VM_JNICompilerInfo(method);
      return new VM_CompiledMethod(compiledMethodId, method, 
                                   machineCode.getInstructions(), info); 

      //      return new VM_CompiledMethod(compiledMethodId, method, 
      //                                   machineCode.getInstructions(), 
      //                                   VM_JNIEnvironment.javaToCCompilerInfo);
    } 
    else if (!VM.BuildForInterpreter) {
      VM_Compiler     compiler     = new VM_Compiler();

      VM_MachineCode  machineCode  = compiler.genCode(compiledMethodId, method);
      INSTRUCTION[]   instructions = machineCode.getInstructions();
      int[]           bytecodeMap  = machineCode.getBytecodeMap();
      VM_CompilerInfo info;
      if (method.isSynchronized()) {
	info = new VM_BaselineCompilerInfo(method, bytecodeMap, 
                                           instructions.length, 
                                           compiler.lockOffset);
      } else {
	info = new VM_BaselineCompilerInfo(method, bytecodeMap, 
                                           instructions.length);
      }
      return new VM_CompiledMethod(compiledMethodId, method, 
                                   instructions, info);
    } else {
      return new VM_CompiledMethod(compiledMethodId, method, null, null);
    }
  }

  //----------------//
  // more interface //
  //----------------//
  
  // position of spill area within method's stackframe.
  static int getMaxSpillOffset (VM_Method m) {
    int params = m.getOperandWords()<<2; // maximum parameter area
    int spill  = params - (MIN_PARAM_REGISTERS << 2);
    if (spill < 0) spill = 0;
    return STACKFRAME_HEADER_SIZE + spill - 4;
  }
  
  // position of operand stack within method's stackframe.
  static int getEmptyStackOffset (VM_Method m) {
    int stack = m.getOperandWords()<<2; // maximum stack size
    return getMaxSpillOffset(m) + stack + 4; // last local
  }
  
  // position of locals within method's stackframe.
  static int getFirstLocalOffset (VM_Method m) {
    int locals = m.getLocalWords()<<2;       // input param words + pure locals
    return getEmptyStackOffset(m) - 4 + locals; // bottom-most local
  }
  
  // position of SP save area within method's stackframe.
  static int getSPSaveAreaOffset (VM_Method m) {
     return getFirstLocalOffset(m) + 4;
  }
  
  // size of method's stackframe.
  static int getFrameSize (VM_Method m) {
    int size;
    if (!m.isNative()) {
      size = getSPSaveAreaOffset(m) + 4;
      if (m.getDeclaringClass().isDynamicBridge()) {
	size += (LAST_NONVOLATILE_FPR - FIRST_VOLATILE_FPR + 1) * 8;
	size += (LAST_NONVOLATILE_GPR - FIRST_VOLATILE_GPR + 1) * 4;
      }
      size = (size + STACKFRAME_ALIGNMENT_MASK) & 
              ~STACKFRAME_ALIGNMENT_MASK; // round up
      return size;
    } else {
      // space for:
      //   -AIX header (6 words)
      //   -parameters and 2 new JNI parameters (jnienv + obj), minimum 8 words
      //   -JNI_SAVE_AREA_OFFSET = savedSP + savedJTOC  + Processor_Register
      //                           nonvolatile registers + GC flag + 
      //                           affinity (20 words) +
      //                           saved volatile registers
      int argSpace = 4 * (m.getParameterWords()+ 2);
      if (argSpace<32)
	argSpace = 32;
      size = AIX_FRAME_HEADER_SIZE + argSpace + JNI_SAVE_AREA_SIZE;     
    }

    size = (size + STACKFRAME_ALIGNMENT_MASK) & ~STACKFRAME_ALIGNMENT_MASK; // round up
    return size;

  }

  static VM_ExceptionDeliverer getExceptionDeliverer() {
    return exceptionDeliverer;
  }
  
  //----------------//
  // implementation //
  //----------------//

  // offset of i-th local variable with respect to FP
  private int localOffset (int i) {
    int offset = firstLocalOffset - (i << 2);
    if (VM.VerifyAssertions) VM.assert(offset < 0x8000);
    return offset;
  }
  
  // reading bytecodes //

  private int fetch1ByteSigned () {
    return bcodes[bindex++];
  }

  private int fetch1ByteUnsigned () {
    return bcodes[bindex++] & 0xFF;
  }

  private int fetch2BytesSigned () {
    int i = bcodes[bindex++] << 8;
    i |= (bcodes[bindex++] & 0xFF);
    return i;
  }

  private int fetch2BytesUnsigned () {
    int i = (bcodes[bindex++] & 0xFF) << 8;
    i |= (bcodes[bindex++] & 0xFF);
    return i;
  }

  private int fetch4BytesSigned () {
    int i = bcodes[bindex++] << 24;
    i |= (bcodes[bindex++] & 0xFF) << 16;
    i |= (bcodes[bindex++] & 0xFF) << 8;
    i |= (bcodes[bindex++] & 0xFF);
    return i;
  }

  private VM_MachineCode genCode (int compiledMethodId, VM_Method meth) {
    if (VM.TraceCompilation) VM.sysWrite("VM_Compiler: begin compiling " + meth + "\n");
    /* initialization */ { 
      if (VM.VerifyAssertions) VM.assert(T3 <= LAST_VOLATILE_GPR);           // need 4 gp temps
      if (VM.VerifyAssertions) VM.assert(F3 <= LAST_VOLATILE_FPR);           // need 4 fp temps
      if (VM.VerifyAssertions) VM.assert(S0 < SP && SP <= LAST_SCRATCH_GPR); // need 2 scratch
      this.compiledMethodId = compiledMethodId;
      this.method           = meth;
      this.klass            = meth.getDeclaringClass();
      this.bcodes           = meth.getBytecodes();
      this.bcodeLen         = bcodes.length;
      this.frameSize        = getFrameSize(meth);
      this.spSaveAreaOffset = getSPSaveAreaOffset(meth);
      this.firstLocalOffset = getFirstLocalOffset(meth);
      this.emptyStackOffset = getEmptyStackOffset(meth);
      this.asm              = new VM_Assembler(bcodeLen);
    }
    if (klass.isBridgeFromNative()) {
      VM_JNICompiler.generateGlueCodeForJNIMethod (asm, meth);
    }
   genPrologue();
   for (bindex=bIP=0; bIP<bcodeLen; bIP=bindex) {
       int code = fetch1ByteUnsigned();
       asm.resolveForwardReferences(bIP);
       switch (code) {
        case 0x00: /* --- nop --- */ {
          if (VM.TraceAssembler) asm.noteBytecode("nop");
          asm.emitNOP();
          break;
	}
        case 0x01: /* --- aconst_null --- */ {
          if (VM.TraceAssembler) asm.noteBytecode("aconst_null ");
	  asm.emitLIL(T0,  0);
          asm.emitSTU(T0, -4, SP);
          break;
	}
        case 0x02: /* --- iconst_m1 --- */ {
          if (VM.TraceAssembler) asm.noteBytecode("iconst_m1 ");
          asm.emitLIL(T0, -1);
          asm.emitSTU(T0, -4, SP);
          break;
	}
        case 0x03: /* --- iconst_0 --- */ {
          if (VM.TraceAssembler) asm.noteBytecode("iconst_0 ");
          asm.emitLIL(T0,  0);
          asm.emitSTU(T0, -4, SP);
          break;
	}
        case 0x04: /* --- iconst_1 --- */ {
          if (VM.TraceAssembler) asm.noteBytecode("iconst_1 ");
          asm.emitLIL(T0,  1);
          asm.emitSTU(T0, -4, SP);
          break;
	}
        case 0x05: /* --- iconst_2 --- */ {
          if (VM.TraceAssembler) asm.noteBytecode("iconst_2 ");
          asm.emitLIL(T0,  2);
          asm.emitSTU(T0, -4, SP);
          break;
	}
        case 0x06: /* --- iconst_3 --- */ {
          if (VM.TraceAssembler) asm.noteBytecode("iconst_3 ");
          asm.emitLIL(T0,  3);
          asm.emitSTU(T0, -4, SP);
          break;
	}
        case 0x07: /* --- iconst_4 --- */ {
          if (VM.TraceAssembler) asm.noteBytecode("iconst_4 ");
          asm.emitLIL(T0,  4);
          asm.emitSTU(T0, -4, SP);
          break;
	}
        case 0x08: /* --- iconst_5 --- */ {
          if (VM.TraceAssembler) asm.noteBytecode("iconst_5 ");
          asm.emitLIL(T0,  5);
          asm.emitSTU(T0, -4, SP);
          break;
	}
        case 0x09: /* --- lconst_0 --- */ {
          if (VM.TraceAssembler) asm.noteBytecode("lconst_0 ");  // floating-point 0 is long 0
          asm.emitLFStoc(F0, VM_Entrypoints.zeroOffset, T0);
          asm.emitSTFDU (F0, -8, SP);
          break;
	}
        case 0x0a: /* --- lconst_1 --- */ {
          if (VM.TraceAssembler) asm.noteBytecode("lconst_1 ");
          asm.emitLFDtoc(F0, VM_Entrypoints.longOneOffset, T0);
          asm.emitSTFDU (F0, -8, SP);
          break;
	}
        case 0x0b: /* --- fconst_0 --- */ {
          if (VM.TraceAssembler) asm.noteBytecode("fconst_0");
          asm.emitLFStoc(F0, VM_Entrypoints.zeroOffset, T0);
          asm.emitSTFSU (F0, -4, SP);
          break;
	}
        case 0x0c: /* --- fconst_1 --- */ {
          if (VM.TraceAssembler) asm.noteBytecode("fconst_1");
          asm.emitLFStoc(F0, VM_Entrypoints.oneOffset, T0);
          asm.emitSTFSU (F0, -4, SP);
          break;
	}
        case 0x0d: /* --- fconst_2 --- */ {
          if (VM.TraceAssembler) asm.noteBytecode("fconst_2");
          asm.emitLFStoc(F0, VM_Entrypoints.twoOffset, T0);
          asm.emitSTFSU (F0, -4, SP);
          break;
	}
        case 0x0e: /* --- dconst_0 --- */ {
          if (VM.TraceAssembler) asm.noteBytecode("dconst_0");
          asm.emitLFStoc(F0, VM_Entrypoints.zeroOffset, T0);
          asm.emitSTFDU (F0, -8, SP);
          break;
	}
        case 0x0f: /* --- dconst_1 --- */ {
          if (VM.TraceAssembler) asm.noteBytecode("dconst_1");
          asm.emitLFStoc(F0, VM_Entrypoints.oneOffset, T0);
          asm.emitSTFDU (F0, -8, SP);
          break;
	}
        case 0x10: /* --- bipush --- */ {
          int val = fetch1ByteSigned();
          if (VM.TraceAssembler) asm.noteBytecode("bipush " + val);
          asm.emitLIL(T0, val);
          asm.emitSTU(T0, -4, SP);
          break;
	}
        case 0x11: /* --- sipush --- */ {
          int val = fetch2BytesSigned();
          if (VM.TraceAssembler) asm.noteBytecode("sipush " + val);
          asm.emitLIL(T0, val);
          asm.emitSTU(T0, -4, SP);
          break;
	}
        case 0x12: /* --- ldc --- */ {
          int index = fetch1ByteUnsigned();
          int offset = klass.getLiteralOffset(index);
          if (VM.TraceAssembler) asm.noteBytecode("ldc " + index);
          asm.emitLtoc(T0,  offset);
          asm.emitSTU (T0, -4, SP);
          break;
	}
        case 0x13: /* --- ldc_w --- */ {
          int index = fetch2BytesUnsigned();
          if (VM.TraceAssembler) asm.noteBytecode("ldc_w " + index);
          int offset = klass.getLiteralOffset(index);
          asm.emitLtoc(T0,  offset);
          asm.emitSTU (T0, -4, SP);
          break;
	}
        case 0x14: /* --- ldc2_w --- */ {
          int index = fetch2BytesUnsigned();
          if (VM.TraceAssembler) asm.noteBytecode("ldc2_w " + index);
          int offset = klass.getLiteralOffset(index);
          asm.emitLFDtoc(F0,  offset, T0);
          asm.emitSTFDU (F0, -8, SP);
          break;
	}
        case 0x15: /* --- iload --- */ {
          int index = fetch1ByteUnsigned();
          if (VM.TraceAssembler) asm.noteBytecode("iload " + index);
          int offset = localOffset(index);
	  asm.emitL(T0, offset, FP);
          asm.emitSTU(T0, -4, SP);
          break;
	}
        case 0x16: /* --- lload --- */ {
          int index = fetch1ByteUnsigned();
          if (VM.TraceAssembler) asm.noteBytecode("lload " + index);
          int offset = localOffset(index) - 4;
	  asm.emitLFD  (F0, offset, FP);
          asm.emitSTFDU(F0, -8, SP);
          break;
	}
        case 0x17: /* --- fload --- */ {
          int index = fetch1ByteUnsigned();
          if (VM.TraceAssembler) asm.noteBytecode("fload " + index);
          int offset = localOffset(index);
	  asm.emitL(T0, offset, FP);
          asm.emitSTU(T0, -4, SP);
          break;
	}
        case 0x18: /* --- dload --- */ {
          int index = fetch1ByteUnsigned();
          if (VM.TraceAssembler) asm.noteBytecode("dload " + index);
          int offset = localOffset(index) - 4;
	  asm.emitLFD  (F0, offset, FP);
          asm.emitSTFDU(F0, -8, SP);
          break;
	}
        case 0x19: /* --- aload --- */ {
          int index = fetch1ByteUnsigned();
          if (VM.TraceAssembler) asm.noteBytecode("aload " + index);
          int offset = localOffset(index);
	  asm.emitL(T0, offset, FP);
          asm.emitSTU(T0, -4, SP);
          break;
	}
        case 0x1a: /* --- iload_0 --- */ {
          if (VM.TraceAssembler) asm.noteBytecode("iload_0");
          int offset = localOffset(0);
	  asm.emitL(T0, offset, FP);
          asm.emitSTU(T0, -4, SP);
          break;
	}
        case 0x1b: /* --- iload_1 --- */ {
          if (VM.TraceAssembler) asm.noteBytecode("iload_1");
          int offset = localOffset(1);
	  asm.emitL(T0, offset, FP);
          asm.emitSTU(T0, -4, SP);
          break;
	}
        case 0x1c: /* --- iload_2 --- */ {
          if (VM.TraceAssembler) asm.noteBytecode("iload_2");
          int offset = localOffset(2);
	  asm.emitL(T0, offset, FP);
          asm.emitSTU(T0, -4, SP);
          break;
	}
        case 0x1d: /* --- iload_3 --- */ {
          if (VM.TraceAssembler) asm.noteBytecode("iload_3");
          int offset = localOffset(3);
	  asm.emitL(T0, offset, FP);
          asm.emitSTU(T0, -4, SP);
          break;
	}
        case 0x1e: /* --- lload_0 --- */ {
          if (VM.TraceAssembler) asm.noteBytecode("lload_0");
          int offset = localOffset(0) - 4;
	  asm.emitLFD  (F0, offset, FP);
          asm.emitSTFDU(F0, -8, SP);
          break;
	}
        case 0x1f: /* --- lload_1 --- */ {
          if (VM.TraceAssembler) asm.noteBytecode("lload_1");
          int offset = localOffset(1) - 4;
	  asm.emitLFD  (F0, offset, FP);
          asm.emitSTFDU(F0, -8, SP);
          break;
	}
        case 0x20: /* --- lload_2 --- */ {
          if (VM.TraceAssembler) asm.noteBytecode("lload_2");
          int offset = localOffset(2) - 4;
	  asm.emitLFD  (F0, offset, FP);
          asm.emitSTFDU(F0, -8, SP);
          break;
	}
        case 0x21: /* --- lload_3 --- */ {
          if (VM.TraceAssembler) asm.noteBytecode("lload_3");
          int offset = localOffset(3) - 4;
	  asm.emitLFD  (F0, offset, FP);
          asm.emitSTFDU(F0, -8, SP);
          break;
	}
        case 0x22: /* --- fload_0 --- */ {
          if (VM.TraceAssembler) asm.noteBytecode("fload_0");
          int offset = localOffset(0);
	  asm.emitL(T0, offset, FP);
          asm.emitSTU(T0, -4, SP);
          break;
	}
        case 0x23: /* --- fload_1 --- */ {
          if (VM.TraceAssembler) asm.noteBytecode("fload_1");
          int offset = localOffset(1);
	  asm.emitL(T0, offset, FP);
          asm.emitSTU(T0, -4, SP);
          break;
	}
        case 0x24: /* --- fload_2 --- */ {
          if (VM.TraceAssembler) asm.noteBytecode("fload_2");
          int offset = localOffset(2);
	  asm.emitL(T0, offset, FP);
          asm.emitSTU(T0, -4, SP);
          break;
	}
        case 0x25: /* --- fload_3 --- */ {
          if (VM.TraceAssembler) asm.noteBytecode("fload_3");
          int offset = localOffset(3);
	  asm.emitL(T0, offset, FP);
          asm.emitSTU(T0, -4, SP);
          break;
	}
        case 0x26: /* --- dload_0 --- */ {
          if (VM.TraceAssembler) asm.noteBytecode("dload_0");
          int offset = localOffset(0) - 4;
	  asm.emitLFD  (F0, offset, FP);
          asm.emitSTFDU(F0, -8, SP);
          break;
	}
        case 0x27: /* --- dload_1 --- */ {
          if (VM.TraceAssembler) asm.noteBytecode("dload_1");
          int offset = localOffset(1) - 4;
	  asm.emitLFD  (F0, offset, FP);
          asm.emitSTFDU(F0, -8, SP);
          break;
	}
        case 0x28: /* --- dload_2 --- */ {
          if (VM.TraceAssembler) asm.noteBytecode("dload_2");
          int offset = localOffset(2) - 4;
	  asm.emitLFD  (F0, offset, FP);
          asm.emitSTFDU(F0, -8, SP);
          break;
	}
        case 0x29: /* --- dload_3 --- */ {
          if (VM.TraceAssembler) asm.noteBytecode("dload_3");
          int offset = localOffset(3) - 4;
	  asm.emitLFD  (F0, offset, FP);
          asm.emitSTFDU(F0, -8, SP);
          break;
	}
        case 0x2a: /* --- aload_0 --- */ {
          if (VM.TraceAssembler) asm.noteBytecode("aload_0");
          int offset = localOffset(0);
	  asm.emitL(T0, offset, FP);
          asm.emitSTU(T0, -4, SP);
          break;
	}
        case 0x2b: /* --- aload_1 --- */ {
          if (VM.TraceAssembler) asm.noteBytecode("aload_1");
          int offset = localOffset(1);
	  asm.emitL(T0, offset, FP);
          asm.emitSTU(T0, -4, SP);
          break;
	}           
        case 0x2c: /* --- aload_2 --- */ {
          if (VM.TraceAssembler) asm.noteBytecode("aload_2");
          int offset = localOffset(2);
	  asm.emitL(T0, offset, FP);
          asm.emitSTU(T0, -4, SP);
          break;
	}
        case 0x2d: /* --- aload_3 --- */ {
          if (VM.TraceAssembler) asm.noteBytecode("aload_3");
          int offset = localOffset(3);
	  asm.emitL(T0, offset, FP);
          asm.emitSTU(T0, -4, SP);
          break;
	}
        case 0x2e: /* --- iaload --- */ {
          if (VM.TraceAssembler) asm.noteBytecode("iaload");
          asm.emitL   (T1,  4, SP);                    // T1 is array ref
          asm.emitL   (T0,  0, SP);                    // T0 is array index
          asm.emitL   (T2,  ARRAY_LENGTH_OFFSET, T1);  // T2 is array length
          asm.emitTLLE(T2, T0);      // trap if index < 0 or index >= length
	  asm.emitSLI (T0, T0,  2);  // convert word index to byte index
	  asm.emitLX  (T2, T0, T1);  // load desired int array element
          asm.emitSTU (T2,  4, SP);  
          break;
	}
        case 0x2f: /* --- laload --- */ {
          if (VM.TraceAssembler) asm.noteBytecode("laload");
          asm.emitL   (T1,  4, SP);                    // T1 is array ref
          asm.emitL   (T0,  0, SP);                    // T0 is array index
          asm.emitL   (T2,  ARRAY_LENGTH_OFFSET, T1);  // T2 is array length
	  asm.emitTLLE(T2, T0);      // trap if index < 0 or index >= length
	  asm.emitSLI (T0, T0,  3);  // convert two word index to byte index
	  asm.emitLFDX(F0, T0, T1);  // load desired (long) array element
          asm.emitSTFD(F0,  0, SP);  
          break;
	}
        case 0x30: /* --- faload --- */ {
          if (VM.TraceAssembler) asm.noteBytecode("faload");
          asm.emitL   (T1,  4, SP);                    // T1 is array ref
          asm.emitL   (T0,  0, SP);                    // T0 is array index
          asm.emitL   (T2,  ARRAY_LENGTH_OFFSET, T1);  // T2 is array length
	  asm.emitTLLE(T2, T0);      // trap if index < 0 or index >= length
	  asm.emitSLI (T0, T0,  2);  // convert word index to byte index
	  asm.emitLX  (T2, T0, T1);  // load desired (float) array element
          asm.emitSTU (T2,  4, SP);  
          break;
	}
        case 0x31: /* --- daload --- */ {
          if (VM.TraceAssembler) asm.noteBytecode("daload");
          asm.emitL   (T1,  4, SP);                    // T1 is array ref
          asm.emitL   (T0,  0, SP);                    // T0 is array index
          asm.emitL   (T2,  ARRAY_LENGTH_OFFSET, T1);  // T2 is array length
	  asm.emitTLLE(T2, T0);      // trap if index < 0 or index >= length
	  asm.emitSLI (T0, T0,  3);  // convert two word index to byte index
	  asm.emitLFDX(F0, T0, T1);  // load desired (double) array element
          asm.emitSTFD(F0,  0, SP);  
          break;
	}
        case 0x32: /* --- aaload --- */ {
          if (VM.TraceAssembler) asm.noteBytecode("aaload");
          asm.emitL   (T1,  4, SP);                    // T1 is array ref
          asm.emitL   (T0,  0, SP);                    // T0 is array index
          asm.emitL   (T2,  ARRAY_LENGTH_OFFSET, T1);  // T2 is array length
	  asm.emitTLLE(T2, T0);      // trap if index < 0 or index >= length
	  asm.emitSLI (T0, T0,  2);  // convert word index to byte index
	  asm.emitLX  (T2, T0, T1);  // load desired (ref) array element
          asm.emitSTU (T2,  4, SP);  
          break;
	}
        case 0x33: /* --- baload --- */ {
          if (VM.TraceAssembler) asm.noteBytecode("baload");
          asm.emitL   (T1,  4, SP);                    // T1 is array ref
          asm.emitL   (T0,  0, SP);                    // T0 is array index
          asm.emitL   (T2,  ARRAY_LENGTH_OFFSET, T1);  // T2 is array length
	  asm.emitTLLE(T2, T0);      // trap if index < 0 or index >= length
	  asm.emitLBZX(T2, T0, T1);  // no load byte algebraic ...
	  asm.emitSLI (T2, T2, 24);
	  asm.emitSRAI(T2, T2, 24);  // propogate the sign bit
          asm.emitSTU (T2,  4, SP);  
          break;
	}
        case 0x34: /* --- caload --- */ {
          if (VM.TraceAssembler) asm.noteBytecode("caload");
          asm.emitL   (T1,  4, SP);                    // T1 is array ref
          asm.emitL   (T0,  0, SP);                    // T0 is array index
          asm.emitL   (T2,  ARRAY_LENGTH_OFFSET, T1);  // T2 is array length
	  asm.emitTLLE(T2, T0);      // trap if index < 0 or index >= length
	  asm.emitSLI (T0, T0,  1);  // convert halfword index to byte index
	  asm.emitLHZX(T2, T0, T1);  // load desired (char) array element
          asm.emitSTU (T2,  4, SP);  
          break;
	}
        case 0x35: /* --- saload --- */ {
          if (VM.TraceAssembler) asm.noteBytecode("saload");
          asm.emitL   (T1,  4, SP);                    // T1 is array ref
          asm.emitL   (T0,  0, SP);                    // T0 is array index
          asm.emitL   (T2,  ARRAY_LENGTH_OFFSET, T1);  // T2 is array length
	  asm.emitTLLE(T2, T0);      // trap if index < 0 or index >= length
	  asm.emitSLI (T0, T0,  1);  // convert halfword index to byte index
	  asm.emitLHAX(T2, T0, T1);  // load desired (short) array element
          asm.emitSTU (T2,  4, SP);  
          break;
	}
        case 0x36: /* --- istore --- */ {
          int index = fetch1ByteUnsigned();
          if (VM.TraceAssembler) asm.noteBytecode("istore " + index);
          asm.emitL(T0, 0, SP);
          asm.emitCAL(SP, 4, SP);
          int offset = localOffset(index);
	  asm.emitST(T0, offset, FP);
          break;
	}
        case 0x37: /* --- lstore --- */ {
          int index = fetch1ByteUnsigned();
          if (VM.TraceAssembler) asm.noteBytecode("lstore " + index);
          asm.emitLFD(F0, 0, SP);
          asm.emitCAL(SP, 8, SP);
          int offset = localOffset(index)-4;
	  asm.emitSTFD(F0, offset, FP);
          break;
	}
        case 0x38: /* --- fstore --- */ {
          int index = fetch1ByteUnsigned();
          if (VM.TraceAssembler) asm.noteBytecode("fstore " + index);
          asm.emitL  (T0, 0, SP);
          asm.emitCAL(SP, 4, SP);
          int offset = localOffset(index);
	  asm.emitST(T0, offset, FP);
          break;
	}
        case 0x39: /* --- dstore --- */ {
          int index = fetch1ByteUnsigned();
          if (VM.TraceAssembler) asm.noteBytecode("dstore " + index);
          asm.emitLFD(F0, 0, SP);
          asm.emitCAL(SP, 8, SP);
          int offset = localOffset(index)-4;
	  asm.emitSTFD(F0, offset, FP);
          break;
	}
        case 0x3a: /* --- astore --- */ {
          int index = fetch1ByteUnsigned();
          if (VM.TraceAssembler) asm.noteBytecode("astore " + index);
          asm.emitL(T0, 0, SP);
          asm.emitCAL(SP, 4, SP);
          int offset = localOffset(index);
	  asm.emitST(T0, offset, FP);
          break;
	}
        case 0x3b: /* --- istore_0 --- */ {
          if (VM.TraceAssembler) asm.noteBytecode("istore_0");
          asm.emitL(T0, 0, SP);
          asm.emitCAL(SP, 4, SP);
          int offset = localOffset(0);
	  asm.emitST(T0, offset, FP);
          break;
	}
        case 0x3c: /* --- istore_1 --- */ {
          if (VM.TraceAssembler) asm.noteBytecode("istore_1");
          asm.emitL(T0, 0, SP);
          asm.emitCAL(SP, 4, SP);
          int offset = localOffset(1);
	  asm.emitST(T0, offset, FP);
          break;
	}
        case 0x3d: /* --- istore_2 --- */ {
          if (VM.TraceAssembler) asm.noteBytecode("istore_2");
          asm.emitL(T0, 0, SP);
          asm.emitCAL(SP, 4, SP);
          int offset = localOffset(2);
	  asm.emitST(T0, offset, FP);
          break;
	}
        case 0x3e: /* --- istore_3 --- */ {
          if (VM.TraceAssembler) asm.noteBytecode("istore_3");
          asm.emitL(T0, 0, SP);
          asm.emitCAL(SP, 4, SP);
          int offset = localOffset(3);
	  asm.emitST(T0, offset, FP);
          break;
	}
        case 0x3f: /* --- lstore_0 --- */ {
          if (VM.TraceAssembler) asm.noteBytecode("lstore_0");
          asm.emitLFD(F0, 0, SP);
          asm.emitCAL(SP, 8, SP);
          int offset = localOffset(0)-4;
	  asm.emitSTFD(F0, offset, FP);
          break;
	}
        case 0x40: /* --- lstore_1 --- */ {
          if (VM.TraceAssembler) asm.noteBytecode("lstore_1");
          asm.emitLFD(F0, 0, SP);
          asm.emitCAL(SP, 8, SP);
          int offset = localOffset(1)-4;
	  asm.emitSTFD(F0, offset, FP);
          break;
	}
        case 0x41: /* --- lstore_2 --- */ {
          if (VM.TraceAssembler) asm.noteBytecode("lstore_2");
          asm.emitLFD(F0, 0, SP);
          asm.emitCAL(SP, 8, SP);
          int offset = localOffset(2)-4;
	  asm.emitSTFD(F0, offset, FP);
          break;
	} 
        case 0x42: /* --- lstore_3 --- */ {
          if (VM.TraceAssembler) asm.noteBytecode("lstore_3");
          asm.emitLFD(F0, 0, SP);
          asm.emitCAL(SP, 8, SP);
          int offset = localOffset(3)-4;
	  asm.emitSTFD(F0, offset, FP);
          break;
	}
        case 0x43: /* --- fstore_0 --- */ {
          if (VM.TraceAssembler) asm.noteBytecode("fstore_0");
          asm.emitL(T0, 0, SP);
          asm.emitCAL(SP, 4, SP);
          int offset = localOffset(0);
	  asm.emitST(T0, offset, FP);
          break;
	}
        case 0x44: /* --- fstore_1 --- */ {
          if (VM.TraceAssembler) asm.noteBytecode("fstore_1");
          asm.emitL(T0, 0, SP);
          asm.emitCAL(SP, 4, SP);
          int offset = localOffset(1);
	  asm.emitST(T0, offset, FP);
          break;
	}
        case 0x45: /* --- fstore_2 --- */ {
          if (VM.TraceAssembler) asm.noteBytecode("fstore_2");
          asm.emitL(T0, 0, SP);
          asm.emitCAL(SP, 4, SP);
          int offset = localOffset(2);
	  asm.emitST(T0, offset, FP);
          break;
	}
        case 0x46: /* --- fstore_3 --- */ {
          if (VM.TraceAssembler) asm.noteBytecode("fstore_3");
          asm.emitL(T0, 0, SP);
          asm.emitCAL(SP, 4, SP);
          int offset = localOffset(3);
	  asm.emitST(T0, offset, FP);
          break;
	}
        case 0x47: /* --- dstore_0 --- */ {
          if (VM.TraceAssembler) asm.noteBytecode("dstore_0");
          asm.emitLFD(F0, 0, SP);
          asm.emitCAL(SP, 8, SP);
          int offset = localOffset(0)-4;
	  asm.emitSTFD(F0, offset, FP);
          break;
	}
        case 0x48: /* --- dstore_1 --- */ {
          if (VM.TraceAssembler) asm.noteBytecode("dstore_1");
          asm.emitLFD(F0, 0, SP);
          asm.emitCAL(SP, 8, SP);
          int offset = localOffset(1)-4;
	  asm.emitSTFD(F0, offset, FP);
          break;
	}
        case 0x49: /* --- dstore_2 --- */ {
          if (VM.TraceAssembler) asm.noteBytecode("dstore_2");
          asm.emitLFD(F0, 0, SP);
          asm.emitCAL(SP, 8, SP);
          int offset = localOffset(2)-4;
	  asm.emitSTFD(F0, offset, FP);
          break;
	}
        case 0x4a: /* --- dstore_3 --- */ {
          if (VM.TraceAssembler) asm.noteBytecode("dstore_3");
          asm.emitLFD(F0, 0, SP);
          asm.emitCAL(SP, 8, SP);
          int offset = localOffset(3)-4;
	  asm.emitSTFD(F0, offset, FP);
          break;
	}
        case 0x4b: /* --- astore_0 --- */ {
          if (VM.TraceAssembler) asm.noteBytecode("astore_0");
          asm.emitL(T0, 0, SP);
          asm.emitCAL(SP, 4, SP);
          int offset = localOffset(0);
	  asm.emitST(T0, offset, FP);
          break;
	}
        case 0x4c: /* --- astore_1 --- */ {
          if (VM.TraceAssembler) asm.noteBytecode("astore_1");
          asm.emitL(T0, 0, SP);
          asm.emitCAL(SP, 4, SP);
          int offset = localOffset(1);
	  asm.emitST(T0, offset, FP);
          break;
	}
        case 0x4d: /* --- astore_2 --- */ {
          if (VM.TraceAssembler) asm.noteBytecode("astore_2");
          asm.emitL(T0, 0, SP);
          asm.emitCAL(SP, 4, SP);
          int offset = localOffset(2);
	  asm.emitST(T0, offset, FP);
          break;
	}
        case 0x4e: /* --- astore_3 --- */ {
          if (VM.TraceAssembler) asm.noteBytecode("astore_3");
          asm.emitL(T0, 0, SP);
          asm.emitCAL(SP, 4, SP);
          int offset = localOffset(3);
	  asm.emitST(T0, offset, FP);
          break;
	}
        case 0x4f: /* --- iastore --- */ {
          if (VM.TraceAssembler) asm.noteBytecode("iastore");
	  asm.emitL   (T1,  8, SP);                    // T1 is array ref
          asm.emitL   (T0,  4, SP);                    // T0 is array index
	  asm.emitL   (T2,  ARRAY_LENGTH_OFFSET, T1);  // T2 is array length
	  asm.emitL   (T3,  0, SP);                    // T3 is value to store
	  asm.emitTLLE(T2, T0);      // trap if index < 0 or index >= length
	  asm.emitSLI (T0, T0,  2);  // convert word index to byte index
	  asm.emitSTX (T3, T0, T1);  // store int value in array
          asm.emitCAL (SP, 12, SP);  // complete 3 pops
          break;
	}
        case 0x50: /* --- lastore --- */ { 
          if (VM.TraceAssembler) asm.noteBytecode("lastore");
          asm.emitL    (T1, 12, SP);                    // T1 is array ref
          asm.emitL    (T0,  8, SP);                    // T0 is array index
          asm.emitL    (T2,  ARRAY_LENGTH_OFFSET, T1);  // T2 is array length
	  asm.emitLFD  (F0,  0, SP);                    // F0 is value to store
	  asm.emitTLLE(T2, T0);      // trap if index < 0 or index >= length
	  asm.emitSLI  (T0, T0,  3);  // convert long index to byte index
	  asm.emitSTFDX(F0, T0, T1);  // store long value in array
          asm.emitCAL  (SP, 16, SP);  // complete 3 pops (1st is 2 words)
          break;
	}
        case 0x51: /* --- fastore --- */ {
          if (VM.TraceAssembler) asm.noteBytecode("fastore");
          asm.emitL   (T1,  8, SP);                    // T1 is array ref
          asm.emitL   (T0,  4, SP);                    // T0 is array index
          asm.emitL   (T2,  ARRAY_LENGTH_OFFSET, T1);  // T2 is array length
	  asm.emitL   (T3,  0, SP);                    // T3 is value to store
	  asm.emitTLLE(T2, T0);      // trap if index < 0 or index >= length
	  asm.emitSLI (T0, T0,  2);  // convert word index to byte index
	  asm.emitSTX (T3, T0, T1);  // store float value in array
          asm.emitCAL (SP, 12, SP);  // complete 3 pops
          break;
	}
        case 0x52: /* --- dastore --- */ {
          if (VM.TraceAssembler) asm.noteBytecode("dastore");
          asm.emitL    (T1, 12, SP);                    // T1 is array ref
          asm.emitL    (T0,  8, SP);                    // T0 is array index
          asm.emitL    (T2,  ARRAY_LENGTH_OFFSET, T1);  // T2 is array length
	  asm.emitLFD  (F0,  0, SP);                    // F0 is value to store
	  asm.emitTLLE(T2, T0);      // trap if index < 0 or index >= length
	  asm.emitSLI  (T0, T0,  3);  // convert double index to byte index
	  asm.emitSTFDX(F0, T0, T1);  // store double value in array
          asm.emitCAL  (SP, 16, SP);  // complete 3 pops (1st is 2 words)
          break;
	}
        case 0x53: /* --- aastore --- */ {
          if (VM.TraceAssembler) asm.noteBytecode("aastore");
	  asm.emitLtoc(T0,  VM_Entrypoints.checkstoreOffset);
          asm.emitMTLR(T0);
          asm.emitL   (T0,  8, SP);  //  T0 := arrayref
          asm.emitL   (T1,  0, SP);  //  T1 := value
	  asm.emitCall(spSaveAreaOffset);   // checkstore(arrayref, value)
          if (VM_Collector.NEEDS_WRITE_BARRIER) 
	    VM_Barriers.compileArrayStoreBarrier(asm, spSaveAreaOffset);
          asm.emitL   (T1,  8, SP);                    // T1 is array ref
          asm.emitL   (T0,  4, SP);                    // T0 is array index
          asm.emitL   (T2,  ARRAY_LENGTH_OFFSET, T1);  // T2 is array length
	  asm.emitL   (T3,  0, SP);                    // T3 is value to store
	  asm.emitTLLE(T2, T0);      // trap if index < 0 or index >= length
	  asm.emitSLI (T0, T0,  2);  // convert word index to byte index
	  if (VM.BuildForConcurrentGC) {
	    //-#if RVM_WITH_CONCURRENT_GC // because VM_RCBarriers not available for non concurrent GC builds
	    VM_RCBarriers.compileArrayStoreBarrier(asm, spSaveAreaOffset);
	    //-#endif
	  } else {
	    asm.emitSTX (T3, T0, T1);  // store ref value in array
	  }
          asm.emitCAL (SP, 12, SP);  // complete 3 pops
          break;
	}
        case 0x54: /* --- bastore --- */ {
          if (VM.TraceAssembler) asm.noteBytecode("bastore");
          asm.emitL   (T1,  8, SP);                    // T1 is array ref
          asm.emitL   (T0,  4, SP);                    // T0 is array index
          asm.emitL   (T2,  ARRAY_LENGTH_OFFSET, T1);  // T2 is array length
	  asm.emitL   (T3,  0, SP);                    // T3 is value to store
	  asm.emitTLLE(T2, T0);      // trap if index < 0 or index >= length
	  asm.emitSTBX(T3, T0, T1);  // store byte value in array
          asm.emitCAL (SP, 12, SP);  // complete 3 pops
          break;
	}
        case 0x55: /* --- castore --- */ {
          if (VM.TraceAssembler) asm.noteBytecode("castore");
          asm.emitL   (T1,  8, SP);                    // T1 is array ref
          asm.emitL   (T0,  4, SP);                    // T0 is array index
          asm.emitL   (T2,  ARRAY_LENGTH_OFFSET, T1);  // T2 is array length
	  asm.emitL   (T3,  0, SP);                    // T3 is value to store
	  asm.emitTLLE(T2, T0);      // trap if index < 0 or index >= length
	  asm.emitSLI (T0, T0,  1);  // convert halfword index to byte index
	  asm.emitSTHX(T3, T0, T1);  // store char value in array
          asm.emitCAL (SP, 12, SP);  // complete 3 pops
          break;
	}
        case 0x56: /* --- sastore --- */ {
          if (VM.TraceAssembler) asm.noteBytecode("sastore");
          asm.emitL   (T1,  8, SP);                    // T1 is array ref
          asm.emitL   (T0,  4, SP);                    // T0 is array index
          asm.emitL   (T2,  ARRAY_LENGTH_OFFSET, T1);  // T2 is array length
	  asm.emitL   (T3,  0, SP);                    // T3 is value to store
	  asm.emitTLLE(T2, T0);      // trap if index < 0 or index >= length
	  asm.emitSLI (T0, T0,  1);  // convert halfword index to byte index
	  asm.emitSTHX(T3, T0, T1);  // store short value in array
          asm.emitCAL (SP, 12, SP);  // complete 3 pops
          break;
	}
        case 0x57: /* --- pop --- */ {
          if (VM.TraceAssembler) asm.noteBytecode("pop");
          asm.emitCAL(SP, 4, SP);
          break;
	}
        case 0x58: /* --- pop2 --- */ {
          if (VM.TraceAssembler) asm.noteBytecode("pop2");
          asm.emitCAL(SP, 8, SP);
          break;
	}
        case 0x59: /* --- dup --- */ {
          if (VM.TraceAssembler) asm.noteBytecode("dup");
          asm.emitL  (T0,  0, SP);
          asm.emitSTU(T0, -4, SP);
          break;
	}
        case 0x5a: /* --- dup_x1 --- */ {
          if (VM.TraceAssembler) asm.noteBytecode("dup_x1");
          asm.emitL  (T0,  0, SP);
          asm.emitL  (T1,  4, SP);
          asm.emitST (T0,  4, SP);
          asm.emitST (T1,  0, SP);
          asm.emitSTU(T0, -4, SP);
          break;
	}
        case 0x5b: /* --- dup_x2 --- */ {
          if (VM.TraceAssembler) asm.noteBytecode("dup_x2");
          asm.emitL   (T0,  0, SP);
          asm.emitLFD (F0,  4, SP);
          asm.emitST  (T0,  8, SP);
          asm.emitSTFD(F0,  0, SP);
          asm.emitSTU (T0, -4, SP);
          break;
	}
        case 0x5c: /* --- dup2 --- */ {
          if (VM.TraceAssembler) asm.noteBytecode("dup2");
          asm.emitLFD  (F0,  0, SP);
          asm.emitSTFDU(F0, -8, SP);
          break;
	}
        case 0x5d: /* --- dup2_x1 --- */ {
          if (VM.TraceAssembler) asm.noteBytecode("dup2_x1");
          asm.emitLFD  (F0,  0, SP);
          asm.emitL    (T0,  8, SP);
          asm.emitSTFD (F0,  4, SP);
          asm.emitST   (T0,  0, SP);
          asm.emitSTFDU(F0, -8, SP);
          break;
	}
        case 0x5e: /* --- dup2_x2 --- */ {
          if (VM.TraceAssembler) asm.noteBytecode("dup2_x2");
          asm.emitLFD  (F0,  0, SP);
          asm.emitLFD  (F1,  8, SP);
          asm.emitSTFD (F0,  8, SP);
          asm.emitSTFD (F1,  0, SP);
          asm.emitSTFDU(F0, -8, SP);
          break;
	}
        case 0x5f: /* --- swap --- */ {
          if (VM.TraceAssembler) asm.noteBytecode("swap");
          asm.emitL  (T0,  0, SP);
          asm.emitL  (T1,  4, SP);
          asm.emitST (T0,  4, SP);
          asm.emitST (T1,  0, SP);
          break;
	}
        case 0x60: /* --- iadd --- */ {
          if (VM.TraceAssembler) asm.noteBytecode("iadd");
          asm.emitL  (T0,  0, SP);
          asm.emitL  (T1,  4, SP);
          asm.emitA  (T2, T1, T0);
          asm.emitSTU(T2,  4, SP);
          break;
	}
        case 0x61: /* --- ladd --- */ {
          if (VM.TraceAssembler) asm.noteBytecode("ladd");
          asm.emitL  (T0,  4, SP);
          asm.emitL  (T1, 12, SP);
          asm.emitL  (T2,  0, SP);
          asm.emitL  (T3,  8, SP);
          asm.emitA  (T0, T1, T0);
          asm.emitAE (T1, T2, T3);
          asm.emitST (T0, 12, SP);
          asm.emitSTU(T1,  8, SP);
          break;
	}
        case 0x62: /* --- fadd --- */ {
          if (VM.TraceAssembler) asm.noteBytecode("fadd");
          asm.emitLFS  (F0,  0, SP);
          asm.emitLFS  (F1,  4, SP);
          asm.emitFAs  (F0, F1, F0);
          asm.emitSTFSU(F0,  4, SP);
          break;
	}
        case 0x63: /* --- dadd --- */ {
          if (VM.TraceAssembler) asm.noteBytecode("dadd");
          asm.emitLFD  (F0,  0, SP);
          asm.emitLFD  (F1,  8, SP);
          asm.emitFA   (F0, F1, F0);
          asm.emitSTFDU(F0,  8, SP);
          break;
	}
        case 0x64: /* --- isub --- */ {
          if (VM.TraceAssembler) asm.noteBytecode("isub");
          asm.emitL  (T0,  0, SP);
          asm.emitL  (T1,  4, SP);
          asm.emitSF (T2, T0, T1);
          asm.emitSTU(T2,  4, SP);
          break;
	}
        case 0x65: /* --- lsub --- */ {
          if (VM.TraceAssembler) asm.noteBytecode("lsub");
          asm.emitL  (T0,  4, SP);
          asm.emitL  (T1, 12, SP);
          asm.emitL  (T2,  0, SP);
          asm.emitL  (T3,  8, SP);
          asm.emitSF (T0, T0, T1);
          asm.emitSFE(T1, T2, T3);
          asm.emitST (T0, 12, SP);
          asm.emitSTU(T1,  8, SP);
          break;
	}
        case 0x66: /* --- fsub --- */ {
          if (VM.TraceAssembler) asm.noteBytecode("fsub");
          asm.emitLFS  (F0,  0, SP);
          asm.emitLFS  (F1,  4, SP);
          asm.emitFSs  (F0, F1, F0);
          asm.emitSTFSU(F0,  4, SP);
          break;
	}
        case 0x67: /* --- dsub --- */ {
          if (VM.TraceAssembler) asm.noteBytecode("dsub");
          asm.emitLFD  (F0,  0, SP);
          asm.emitLFD  (F1,  8, SP);
          asm.emitFS   (F0, F1, F0);
          asm.emitSTFDU(F0,  8, SP);
          break;
	}
        case 0x68: /* --- imul --- */ {
          if (VM.TraceAssembler) asm.noteBytecode("imul");
          asm.emitL  (T0, 4, SP);
          asm.emitL  (T1, 0, SP);
          asm.emitMULS(T1,T0, T1);
	  asm.emitSTU(T1, 4, SP);
          break;
	}
        case 0x69: /* --- lmul --- */ {
          if (VM.TraceAssembler) asm.noteBytecode("lmul");
          asm.emitL     (T1, 12, SP);
          asm.emitL     (T3,  4, SP);
          asm.emitL     (T0,  8, SP);
          asm.emitL     (T2,  0, SP);
          asm.emitMULHWU(S0, T1, T3);
          asm.emitMULS  (T0, T0, T3);
          asm.emitA     (T0, T0, S0);
          asm.emitMULS  (S0, T1, T2);
          asm.emitMULS  (T1, T1, T3);
          asm.emitA     (T0, T0, S0);
          asm.emitST    (T1, 12, SP);
          asm.emitSTU   (T0,  8, SP);
          break;
        }
        case 0x6a: /* --- fmul --- */ {
          if (VM.TraceAssembler) asm.noteBytecode("fmul");
          asm.emitLFS  (F0,  0, SP);
          asm.emitLFS  (F1,  4, SP);
          asm.emitFMs  (F0, F1, F0); // single precision multiply
          asm.emitSTFSU(F0,  4, SP);
          break;
	}
        case 0x6b: /* --- dmul --- */ {
          if (VM.TraceAssembler) asm.noteBytecode("dmul");
          asm.emitLFD  (F0,  0, SP);
          asm.emitLFD  (F1,  8, SP);
          asm.emitFM   (F0, F1, F0);
          asm.emitSTFDU(F0,  8, SP);
          break;
	}
        case 0x6c: /* --- idiv --- */ {
          if (VM.TraceAssembler) asm.noteBytecode("idiv");
          asm.emitL   (T0, 4, SP);
          asm.emitL   (T1, 0, SP);
          asm.emitTEQ0(T1);
	  asm.emitDIV (T0, T0, T1);  // T0 := T0/T1
	  asm.emitSTU (T0, 4, SP);
          break;
	}
        case 0x6d: /* --- ldiv --- */ {
          if (VM.TraceAssembler) asm.noteBytecode("ldiv");
          asm.emitLtoc(T0, VM_Entrypoints.longDivideOffset);
          asm.emitMTLR(T0);
          asm.emitL   (T1, 12, SP);
	  asm.emitL   (T0,  8, SP);
          asm.emitL   (T3,  4, SP);
          asm.emitL   (T2,  0, SP);
          asm.emitCall(spSaveAreaOffset);
	  asm.emitST  (T1, 12, SP);
	  asm.emitSTU (T0,  8, SP);
          break;
	}
        case 0x6e: /* --- fdiv --- */ {
          if (VM.TraceAssembler) asm.noteBytecode("fdiv");
          asm.emitLFS  (F0,  0, SP);
          asm.emitLFS  (F1,  4, SP);
          asm.emitFDs  (F0, F1, F0);
          asm.emitSTFSU(F0,  4, SP);
          break;
	}
        case 0x6f: /* --- ddiv --- */ {
          if (VM.TraceAssembler) asm.noteBytecode("ddiv");
          asm.emitLFD  (F0,  0, SP);
          asm.emitLFD  (F1,  8, SP);
          asm.emitFD   (F0, F1, F0);
          asm.emitSTFDU(F0,  8, SP);
          break;
	}
        case 0x70: /* --- irem --- */ {
          if (VM.TraceAssembler) asm.noteBytecode("irem");
          asm.emitL   (T0, 4, SP);
          asm.emitL   (T1, 0, SP);
	  asm.emitTEQ0(T1);
	  asm.emitDIV (T2, T0, T1);   // T2 := T0/T1
	  asm.emitMULS(T2, T2, T1);   // T2 := [T0/T1]*T1
	  asm.emitSF  (T1, T2, T0);   // T1 := T0 - [T0/T1]*T1
	  asm.emitSTU (T1, 4, SP);
          break;
	}
        case 0x71: /* --- lrem --- */ {
          if (VM.TraceAssembler) asm.noteBytecode("lrem");
          asm.emitLtoc(T0, VM_Entrypoints.longRemainderOffset);
          asm.emitMTLR(T0);
          asm.emitL   (T1, 12, SP);
	  asm.emitL   (T0,  8, SP);
          asm.emitL   (T3,  4, SP);
          asm.emitL   (T2,  0, SP);
          asm.emitCall(spSaveAreaOffset);
	  asm.emitST  (T1, 12, SP);
	  asm.emitSTU (T0,  8, SP);
          break;
	}
        case 0x72: /* --- frem --- */ {
          if (VM.TraceAssembler) asm.noteBytecode("frem");                // compute a % b
          asm.emitLFS   (F0,  0, SP);              // F0 is b
          asm.emitLFS   (F1,  4, SP);              // F1 is a
          asm.emitFD    (F2, F1, F0);              // F2 is a/b
          asm.emitLFStoc(F3, VM_Entrypoints.halfOffset, T0);      // F3 is 0.5
          asm.emitFNEG  (F1, F3);                  // F1 is -0.5
          asm.emitFSEL  (F3, F2, F3, F1);          // F3 is (a/b<0)?-0.5:0.5
          asm.emitFS    (F2, F2, F3);              // F2 is a/b - (a/b<0)?-0.5:0.5
          asm.emitLFDtoc(F3, VM_Entrypoints.IEEEmagicOffset, T0); // F3 is MAGIC
          asm.emitFA    (F2, F2, F3);              // F2 is MAGIC + a/b - (a/b<0)?-0.5:0.5
          asm.emitFS    (F2, F2, F3);              // F2 is trunc(a/b)
          asm.emitLFS   (F1,  4, SP);              // F1 is a
          asm.emitFNMS  (F2, F2, F0, F1);          // F2 is a % b
          asm.emitSTFSU (F2,  4, SP);
          break;
	}
        case 0x73: /* --- drem --- */ {
          if (VM.TraceAssembler) asm.noteBytecode("drem");                // compute a % b
          asm.emitLFD   (F0,  0, SP);              // F0 is b
          asm.emitLFD   (F1,  8, SP);              // F1 is a
          asm.emitFD    (F2, F1, F0);              // F2 a/b
          asm.emitLFStoc(F3, VM_Entrypoints.halfOffset, T0);     // F3 is 0.5
          asm.emitFNEG  (F1, F3);                  // F1 is -0.5
          asm.emitFSEL  (F3, F2, F3, F1);          // F3 is (a/b<0)?-0.5:0.5
          asm.emitFS    (F2, F2, F3);              // F2 is a/b - (a/b<0)?-0.5:0.5
          asm.emitLFDtoc(F3, VM_Entrypoints.IEEEmagicOffset, T0); // F3 is MAGIC
          asm.emitFA    (F2, F2, F3);              // F2 is MAGIC + a/b - 0.5
          asm.emitFS    (F2, F2, F3);              // F2 is trunc(a/b)
          asm.emitLFD   (F1,  8, SP);              // F1 is a
          asm.emitFNMS  (F2, F2, F0, F1);          // F2 is a % b
          asm.emitSTFDU (F2,  8, SP);
          break;
	}
        case 0x74: /* --- ineg --- */ {
          if (VM.TraceAssembler) asm.noteBytecode("ineg");
          asm.emitL  (T0,  0, SP);
	  asm.emitNEG(T0, T0);
	  asm.emitST (T0,  0, SP);
          break;
	}
        case 0x75: /* --- lneg --- */ {
          if (VM.TraceAssembler) asm.noteBytecode("lneg");
          asm.emitL   (T0,  4, SP);
          asm.emitL   (T1,  0, SP);
          asm.emitSFI (T0, T0, 0x0);
          asm.emitSFZE(T1, T1);
          asm.emitST  (T0,  4, SP);
          asm.emitSTU (T1,  0, SP);
          break;
	}
        case 0x76: /* --- fneg --- */ {
          if (VM.TraceAssembler) asm.noteBytecode("fneg");
          asm.emitLFS (F0,  0, SP);
          asm.emitFNEG(F0, F0);
          asm.emitSTFS(F0,  0, SP);
          break;
	}
        case 0x77: /* --- dneg --- */ {
          if (VM.TraceAssembler) asm.noteBytecode("dneg");
          asm.emitLFD (F0,  0, SP);
          asm.emitFNEG(F0, F0);
          asm.emitSTFD(F0,  0, SP);
          break;
	}
        case 0x78: /* --- ishl --- */ {
          if (VM.TraceAssembler) asm.noteBytecode("ishl");
          asm.emitL   (T0,  4, SP);
          asm.emitL   (T1,  0, SP);
          asm.emitANDI(T1, T1, 0x1F);
	  asm.emitSL  (T0, T0, T1);
          asm.emitSTU (T0,  4, SP);
          break;
	}
        case 0x79: /* --- lshl --- */ {
          if (VM.TraceAssembler) asm.noteBytecode("lshl");    // l >> n
          asm.emitL   (T0,  0, SP);    // T0 is n
	  asm.emitL   (T1,  8, SP);    // T1 is low bits of l
          asm.emitANDI(T3, T0, 0x20);  // shift more than 31 bits?
          asm.emitXOR (T0, T3, T0);    // restrict shift to at most 31 bits
          asm.emitSL  (T3, T1, T0);    // low bits of l shifted n or n-32 bits
          asm.emitL   (T2,  4, SP);    // T2 is high bits of l
	  asm.emitBEQ ( 5);            // if shift less than 32, goto
          asm.emitSTU (T3,  4, SP);    // store high bits of result
	  asm.emitLIL (T0,  0);        // low bits are zero
          asm.emitST  (T0,  4, SP);    // store 'em
          asm.emitB   ( 7);            // (done)
          asm.emitSL  (T2, T2, T0);    // high bits of l shifted n bits left
	  asm.emitSFI (T0, T0, 0x20);  // T0 := 32 - T0; 
	  asm.emitSR  (T1, T1, T0);    // T1 is middle bits of result
	  asm.emitOR  (T2, T2, T1);    // T2 is high bits of result
          asm.emitSTU (T2,  4, SP);    // store high bits of result
	  asm.emitST  (T3,  4, SP);    // store low bits of result           
	  break;
	}
        case 0x7a: /* --- ishr --- */ {
          if (VM.TraceAssembler) asm.noteBytecode("ishr");
          asm.emitL   (T0,  4, SP);
          asm.emitL   (T1,  0, SP);
          asm.emitANDI(T1, T1, 0x1F);
	  asm.emitSRA (T0, T0, T1);
          asm.emitSTU (T0,  4, SP);
          break;
	}
        case 0x7b: /* --- lshr --- */ {
          if (VM.TraceAssembler) asm.noteBytecode("lshr");
          asm.emitL   (T0,  0, SP);    // T0 is n
	  asm.emitL   (T2,  4, SP);    // T2 is high bits of l
	  asm.emitANDI(T3, T0, 0x20);  // shift more than 31 bits?
          asm.emitXOR (T0, T3, T0);    // restrict shift to at most 31 bits
          asm.emitSRA (T3, T2, T0);    // high bits of l shifted n or n-32 bits
          asm.emitL   (T1,  8, SP);    // T1 is low bits of l
          asm.emitBEQ ( 5);            // if shift less than 32, goto
          asm.emitST  (T3,  8, SP);    // store low bits of result
	  asm.emitSRAI(T0, T3, 0x1F);  // propogate a full work of sign bit
          asm.emitSTU (T0,  4, SP);    // store high bits of result
          asm.emitB   ( 7);            // (done)
          asm.emitSR  (T1, T1, T0);    // low bits of l shifted n bits right
	  asm.emitSFI (T0, T0, 0x20);  // T0 := 32 - T0;
	  asm.emitSL  (T2, T2, T0);    // T2 is middle bits of result
	  asm.emitOR  (T1, T1, T2);    // T1 is low bits of result
          asm.emitST  (T1,  8, SP);    // store low bits of result 
	  asm.emitSTU (T3,  4, SP);    // store high bits of result          
	  break;
	}
        case 0x7c: /* --- iushr --- */ {
          if (VM.TraceAssembler) asm.noteBytecode("iushr");
          asm.emitL   (T0,  4, SP);
          asm.emitL   (T1,  0, SP);
          asm.emitANDI(T1, T1, 0x1F);
	  asm.emitSR  (T0, T0, T1);
          asm.emitSTU (T0,  4, SP);
          break;
	}
        case 0x7d: /* --- lushr --- */ {
          if (VM.TraceAssembler) asm.noteBytecode("lushr");
          asm.emitL   (T0,  0, SP);    // T0 is n
	  asm.emitL   (T2,  4, SP);    // T2 is high bits of l
	  asm.emitANDI(T3, T0, 0x20);  // shift more than 31 bits?
          asm.emitXOR (T0, T3, T0);    // restrict shift to at most 31 bits
          asm.emitSR  (T3, T2, T0);    // high bits of l shifted n or n-32 bits
          asm.emitL   (T1,  8, SP);    // T1 is low bits of l
          asm.emitBEQ ( 5);            // if shift less than 32, goto
          asm.emitST  (T3,  8, SP);    // store low bits of result
	  asm.emitLIL (T0,  0);        // high bits are zero
          asm.emitSTU (T0,  4, SP);    // store 'em
          asm.emitB   ( 7);            // (done)
          asm.emitSR  (T1, T1, T0);    // low bits of l shifted n bits right
	  asm.emitSFI (T0, T0, 0x20);  // T0 := 32 - T0;
	  asm.emitSL  (T2, T2, T0);    // T2 is middle bits of result
	  asm.emitOR  (T1, T1, T2);    // T1 is low bits of result
          asm.emitST  (T1,  8, SP);    // store low bits of result 
	  asm.emitSTU (T3,  4, SP);    // store high bits of result          
	  break;
	}
        case 0x7e: /* --- iand --- */ {
          if (VM.TraceAssembler) asm.noteBytecode("iand");
          asm.emitL   (T0,  0, SP);
          asm.emitL   (T1,  4, SP);
          asm.emitAND (T2, T0, T1);
          asm.emitSTU (T2,  4, SP);
          break;
	}
        case 0x7f: /* --- land --- */ {
          if (VM.TraceAssembler) asm.noteBytecode("land");
          asm.emitL  (T0,  4, SP);
          asm.emitL  (T1, 12, SP);
          asm.emitL  (T2,  0, SP);
          asm.emitL  (T3,  8, SP);
          asm.emitAND(T0, T1, T0);
          asm.emitAND(T1, T2, T3);
          asm.emitST (T0, 12, SP);
          asm.emitSTU(T1,  8, SP);
          break;
	}
        case 0x80: /* --- ior --- */ {
          if (VM.TraceAssembler) asm.noteBytecode("ior");
          asm.emitL   (T0,  0,SP);
          asm.emitL   (T1,  4,SP);
          asm.emitOR  (T2, T0,T1);
          asm.emitSTU (T2,  4,SP);
          break;
	}
        case 0x81: /* --- lor --- */ {
          if (VM.TraceAssembler) asm.noteBytecode("lor");
          asm.emitL  (T0,  4, SP);
          asm.emitL  (T1, 12, SP);
          asm.emitL  (T2,  0, SP);
          asm.emitL  (T3,  8, SP);
          asm.emitOR (T0, T1, T0);
          asm.emitOR (T1, T2, T3);
          asm.emitST (T0, 12, SP);
          asm.emitSTU(T1,  8, SP);
          break;
	}
        case 0x82: /* --- ixor --- */ {
          if (VM.TraceAssembler) asm.noteBytecode("ixor");
          asm.emitL   (T0,  0,SP);
          asm.emitL   (T1,  4,SP);
          asm.emitXOR (T2, T0,T1);
          asm.emitSTU (T2,  4,SP);
          break;
	}
        case 0x83: /* --- lxor --- */ {
          if (VM.TraceAssembler) asm.noteBytecode("lxor");
          asm.emitL  (T0,  4, SP);
          asm.emitL  (T1, 12, SP);
          asm.emitL  (T2,  0, SP);
          asm.emitL  (T3,  8, SP);
          asm.emitXOR(T0, T1, T0);
          asm.emitXOR(T1, T2, T3);
          asm.emitST (T0, 12, SP);
          asm.emitSTU(T1,  8, SP);
          break;
	}
        case 0x84: /* --- iinc --- */ {
          int index = fetch1ByteUnsigned();
          int val = fetch1ByteSigned();
          if (VM.TraceAssembler) 
	    asm.noteBytecode("iinc " + index + " " + val);
          int offset = localOffset(index);
	  asm.emitL  (T0, offset, FP);
          asm.emitCAL(T0, val, T0);
	  asm.emitST (T0, offset, FP);
          break;
	}
        case 0x85: /* --- i2l --- */ {
          if (VM.TraceAssembler) asm.noteBytecode("i2l");
          asm.emitL   (T0,  0, SP);
	  asm.emitSRAI(T1, T0, 31);
	  asm.emitSTU (T1, -4, SP);
          break;
	}
        case 0x86: /* --- i2f --- */ {
          if (VM.TraceAssembler) asm.noteBytecode("i2f");
          asm.emitL     (T0,  0, SP);               // T0 is X (an int)
	  asm.emitCMPI  (T0,  0);                   // is X < 0
          asm.emitLFDtoc(F0, VM_Entrypoints.IEEEmagicOffset, T1);  // F0 is MAGIC
          asm.emitSTFD  (F0, -4, SP);               // MAGIC on stack
          asm.emitST    (T0,  0, SP);               // if 0 <= X, MAGIC + X 
          asm.emitBGE   ( 4);                       // ow, handle X < 0
          asm.emitL     (T0, -4, SP);               // T0 is top of MAGIC
          asm.emitCAL   (T0, -1, T0);               // decrement top of MAGIC
          asm.emitST    (T0, -4, SP);               // MAGIC + X is on stack
          asm.emitLFD   (F1, -4, SP);               // F1 is MAGIC + X
          asm.emitFS    (F1, F1, F0);               // F1 is X
          asm.emitSTFS  (F1,  0, SP);               // float(X) is on stack 
          break;
	}
        case 0x87: /* --- i2d --- */ {
          if (VM.TraceAssembler) asm.noteBytecode("i2d");
          asm.emitL     (T0,  0, SP);               // T0 is X (an int)
	  asm.emitCMPI  (T0,  0);                   // is X < 0
          asm.emitLFDtoc(F0, VM_Entrypoints.IEEEmagicOffset, T1);  // F0 is MAGIC
          asm.emitSTFD  (F0, -4, SP);               // MAGIC on stack
          asm.emitST    (T0,  0, SP);               // if 0 <= X, MAGIC + X 
          asm.emitBGE   ( 4);                       // ow, handle X < 0
          asm.emitL     (T0, -4, SP);               // T0 is top of MAGIC
          asm.emitCAL   (T0, -1, T0);               // decrement top of MAGIC
          asm.emitST    (T0, -4, SP);               // MAGIC + X is on stack
          asm.emitLFD   (F1, -4, SP);               // F1 is MAGIC + X
          asm.emitFS    (F1, F1, F0);               // F1 is X
          asm.emitSTFDU (F1, -4, SP);               // float(X) is on stack 
          break;
	}
        case 0x88: /* --- l2i --- */ {
          if (VM.TraceAssembler) asm.noteBytecode("l2i");
          asm.emitCAL(SP, 4, SP); // throw away top of the long
          break;
	}
        case 0x89: /* --- l2f --- */ {
          if (VM.TraceAssembler) asm.noteBytecode("l2f");
          asm.emitLtoc (T0, VM_Entrypoints.longToDoubleOffset);
          asm.emitMTLR (T0);
          asm.emitL    (T1,  4, SP);
	  asm.emitL    (T0,  0, SP);
          asm.emitCall(spSaveAreaOffset);
	  asm.emitSTFSU(F0,  4, SP);
          break;
	}
        case 0x8a: /* --- l2d --- */ {
          if (VM.TraceAssembler) asm.noteBytecode("l2d"); 
	  asm.emitLtoc(T0, VM_Entrypoints.longToDoubleOffset);
          asm.emitMTLR(T0);
          asm.emitL   (T1,  4, SP);
	  asm.emitL   (T0,  0, SP);
          asm.emitCall(spSaveAreaOffset);
	  asm.emitSTFD(F0,  0, SP);
          break;
	}
        case 0x8b: /* --- f2i --- */ {
          if (VM.TraceAssembler) asm.noteBytecode("f2i");
          asm.emitLFS  (F0,  0, SP);
	  asm.emitFCTIZ(F0, F0);
	  asm.emitSTFD (F0, -4, SP); // TODO!! Conceivably this could cause an overflow of the stack
          break;
	}
        case 0x8c: /* --- f2l --- */ {
          if (VM.TraceAssembler) asm.noteBytecode("f2l");
          asm.emitLtoc(T0, VM_Entrypoints.doubleToLongOffset);
          asm.emitMTLR(T0);
          asm.emitLFS (F0,  0, SP);
          asm.emitCall(spSaveAreaOffset);
	  asm.emitST  (T1,  0, SP);
	  asm.emitSTU (T0, -4, SP);
          break;
	}
        case 0x8d: /* --- f2d --- */ {
          if (VM.TraceAssembler) asm.noteBytecode("f2d");
          asm.emitLFS  (F0,  0, SP);
	  asm.emitSTFDU(F0, -4, SP);
          break;
	}
        case 0x8e: /* --- d2i --- */ {
          if (VM.TraceAssembler) asm.noteBytecode("d2i");
	  asm.emitLFD  (F0,  0, SP);
	  asm.emitFCTIZ(F0, F0);
	  asm.emitSTFD (F0,  0, SP);
	  asm.emitCAL  (SP,  4, SP);
          break;
	}
        case 0x8f: /* --- d2l --- */ {
          if (VM.TraceAssembler) asm.noteBytecode("d2l");
          asm.emitLtoc(T0, VM_Entrypoints.doubleToLongOffset);
          asm.emitMTLR(T0);
          asm.emitLFD (F0, 0, SP);
          asm.emitCall(spSaveAreaOffset);
          asm.emitST  (T1, 4, SP);
	  asm.emitST  (T0, 0, SP);
          break;
	}
        case 0x90: /* --- d2f --- */ {
          if (VM.TraceAssembler) asm.noteBytecode("d2f");
          asm.emitLFD  (F0, 0, SP);
	  asm.emitSTFSU(F0, 4, SP);
          break;
	}
        case 0x91: /* --- i2b --- */ {
          if (VM.TraceAssembler) asm.noteBytecode("i2b");
          asm.emitL   (T0,  3, SP);
          asm.emitSRAI(T0, T0, 24);
          asm.emitST  (T0,  0, SP);
          break;
	}
        case 0x92: /* --- i2c --- */ {
          if (VM.TraceAssembler) asm.noteBytecode("i2c");
          asm.emitLHZ(T0, 2, SP);
	  asm.emitST (T0, 0, SP);
          break;
	}
        case 0x93: /* --- i2s --- */ {
          if (VM.TraceAssembler) asm.noteBytecode("i2s");
          asm.emitLHA(T0, 2, SP);
	  asm.emitST (T0, 0, SP);
          break;
	}
        case 0x94: /* --- lcmp --- */ {
          if (VM.TraceAssembler) asm.noteBytecode("lcmp");  // a ? b
          asm.emitL    (T1,  8, SP);  // T1 is ah
	  asm.emitL    (T3,  0, SP);  // T3 is bh
	  asm.emitCMP  (T1, T3);      // ah ? al
	  asm.emitBLT  (10);
	  asm.emitBGT  (12);
	  asm.emitL    (T0, 12, SP);  // (ah == bh), T0 is al
          asm.emitL    (T2,  4, SP);  // T2 is bl
          asm.emitCMPL (T0, T2);      // al ? bl (logical compare)
          asm.emitBLT  (5);
          asm.emitBGT  (7);
          asm.emitLIL  (T0,  0);      // a == b
          asm.emitSTU  (T0, 12, SP);  // push  0
	  asm.emitB    (6);
          asm.emitLIL  (T0, -1);      // a <  b
          asm.emitSTU  (T0, 12, SP);  // push -1
	  asm.emitB    (3);
          asm.emitLIL  (T0,  1);      // a >  b
          asm.emitSTU  (T0, 12, SP);  // push  1
          break;
	}
        case 0x95: /* --- fcmpl --- */ {
          if (VM.TraceAssembler) asm.noteBytecode("fcmpl");
	  asm.emitLFS  (F0,  4, SP);
	  asm.emitLFS  (F1,  0, SP);
	  asm.emitFCMPU(F0, F1);
          asm.emitBLE  (4);
          asm.emitLIL  (T0,  1); // the GT bit of CR0
	  asm.emitSTU  (T0,  4, SP);
	  asm.emitB    (7);
	  asm.emitBEQ  (4);
          asm.emitLIL  (T0, -1); // the LT or UO bits of CR0
	  asm.emitSTU  (T0,  4, SP);
	  asm.emitB    (3);
	  asm.emitLIL  (T0,  0);
	  asm.emitSTU  (T0,  4, SP); // the EQ bit of CR0
	  break;
	}
        case 0x96: /* --- fcmpg --- */ {
          if (VM.TraceAssembler) asm.noteBytecode("fcmpg");
          asm.emitLFS  (F0,  4, SP);
	  asm.emitLFS  (F1,  0, SP);
	  asm.emitFCMPU(F0, F1);
          asm.emitBGE  (4);         
          asm.emitLIL  (T0, -1);     // the LT bit of CR0
	  asm.emitSTU  (T0,  4, SP);
	  asm.emitB    (7);
	  asm.emitBEQ  (4);         
          asm.emitLIL  (T0,  1);     // the GT or UO bits of CR0
	  asm.emitSTU  (T0,  4, SP);
	  asm.emitB    (3);
	  asm.emitLIL  (T0,  0);     // the EQ bit of CR0
	  asm.emitSTU  (T0,  4, SP);
	  break;
	}
        case 0x97: /* --- dcmpl --- */ {
	  if (VM.TraceAssembler) asm.noteBytecode("dcmpl");
          asm.emitLFD  (F0,  8, SP);
	  asm.emitLFD  (F1,  0, SP);
	  asm.emitFCMPU(F0, F1);
          asm.emitBLE  (4);
          asm.emitLIL  (T0,  1); // the GT bit of CR0
	  asm.emitSTU  (T0, 12, SP);
	  asm.emitB    (7);
	  asm.emitBEQ  (4);
          asm.emitLIL  (T0, -1); // the LT or UO bits of CR0
	  asm.emitSTU  (T0, 12, SP);
	  asm.emitB    (3);
	  asm.emitLIL  (T0,  0);
	  asm.emitSTU  (T0, 12, SP); // the EQ bit of CR0
	  break;
	}
        case 0x98: /* --- dcmpg --- */ {
          if (VM.TraceAssembler) asm.noteBytecode("dcmpg");
          asm.emitLFD  (F0,  8, SP);
	  asm.emitLFD  (F1,  0, SP);
	  asm.emitFCMPU(F0, F1);
          asm.emitBGE  (4);         
          asm.emitLIL  (T0, -1); // the LT bit of CR0
	  asm.emitSTU  (T0, 12, SP);
	  asm.emitB    (7);
	  asm.emitBEQ  (4);         
          asm.emitLIL  (T0,  1); // the GT or UO bits of CR0
	  asm.emitSTU  (T0, 12, SP);
	  asm.emitB    (3);
	  asm.emitLIL  (T0,  0); // the EQ bit of CR0
	  asm.emitSTU  (T0, 12, SP);
	  break;
	}
        case 0x99: /* --- ifeq --- */ {
          int offset = fetch2BytesSigned();
          if (VM.TraceAssembler) asm.noteBytecode("ifeq " + offset);
          if(offset <= 0) genThreadSwitchTest(VM_Thread.BACKEDGE);
          int bTarget = bIP + offset;
          int mTarget;
          asm.emitL  (T0,  0, SP);
	  asm.emitAIr(T0, T0,  0); // compares T0 to 0 and sets CR0 
          asm.emitCAL(SP,  4, SP); // completes pop
          if (VM.TraceAssembler) asm.comment(">>> bi " + bTarget);
          if (offset <= 0) {
            mTarget = asm.relativeMachineAddress(bTarget);
	  } else {
            mTarget = 0;
            asm.reserveForwardConditionalBranch(bTarget);
	  }
          asm.emitBEQ(mTarget);
          break;
	}
        case 0x9a: /* --- ifne --- */ {
          int offset = fetch2BytesSigned();
          if (VM.TraceAssembler) asm.noteBytecode("ifne " + offset);
          if(offset <= 0) genThreadSwitchTest(VM_Thread.BACKEDGE);
          int bTarget = bIP + offset;
          int mTarget;
          asm.emitL  (T0,  0, SP);
	  asm.emitAIr(T0, T0,  0); // compares T0 to 0 and sets CR0 
          asm.emitCAL(SP,  4, SP); // completes pop
          if (VM.TraceAssembler) asm.comment(">>> bi " + bTarget);
          if (offset <= 0) {
            mTarget = asm.relativeMachineAddress(bTarget);
	  } else {
            mTarget = 0;
            asm.reserveForwardConditionalBranch(bTarget);
	  }
          asm.emitBNE(mTarget);
          break;
	}
        case 0x9b: /* --- iflt --- */ {
          int offset = fetch2BytesSigned();
          if (VM.TraceAssembler) asm.noteBytecode("iflt " + offset);
          if(offset <= 0) genThreadSwitchTest(VM_Thread.BACKEDGE);
          int bTarget = bIP + offset;
          int mTarget;
          asm.emitL  (T0,  0, SP);
	  asm.emitAIr(T0, T0,  0); // compares T0 to 0 and sets CR0 
          asm.emitCAL(SP,  4, SP); // completes pop
          if (VM.TraceAssembler) asm.comment(">>> bi " + bTarget);
          if (offset <= 0) {
            mTarget = asm.relativeMachineAddress(bTarget);
	  } else {
            mTarget = 0;
            asm.reserveForwardConditionalBranch(bTarget);
	  }
          asm.emitBLT(mTarget);
          break;
	}
        case 0x9c: /* --- ifge --- */ {
          int offset = fetch2BytesSigned();
          if (VM.TraceAssembler) asm.noteBytecode("ifge " + offset);
	  if(offset <= 0) genThreadSwitchTest(VM_Thread.BACKEDGE);
          int bTarget = bIP + offset;
          int mTarget;
          asm.emitL  (T0,  0, SP);
	  asm.emitAIr(T0, T0,  0); // compares T0 to 0 and sets CR0 
          asm.emitCAL(SP,  4, SP); // completes pop
          if (VM.TraceAssembler) asm.comment(">>> bi " + bTarget);
          if (offset <= 0) {
            mTarget = asm.relativeMachineAddress(bTarget);
	  } else {
            mTarget = 0;
            asm.reserveForwardConditionalBranch(bTarget);
	  }
          asm.emitBGE(mTarget);
          break;
	}
        case 0x9d: /* --- ifgt --- */ {
          int offset = fetch2BytesSigned();
          if (VM.TraceAssembler) asm.noteBytecode("ifgt " + offset);
	  if(offset <= 0) genThreadSwitchTest(VM_Thread.BACKEDGE);
          int bTarget = bIP + offset;
          int mTarget;
          asm.emitL  (T0,  0, SP);
	  asm.emitAIr(T0, T0,  0); // compares T0 to 0 and sets CR0 
          asm.emitCAL(SP,  4, SP); // completes pop
          if (VM.TraceAssembler) asm.comment(">>> bi " + bTarget);
          if (offset <= 0) {
            mTarget = asm.relativeMachineAddress(bTarget);
	  } else {
            mTarget = 0;
            asm.reserveForwardConditionalBranch(bTarget);
	  }
          asm.emitBGT(mTarget);
          break;
	}
        case 0x9e: /* --- ifle --- */ {
          int offset = fetch2BytesSigned();
          if (VM.TraceAssembler) asm.noteBytecode("ifle " + offset);
	  if(offset <= 0) genThreadSwitchTest(VM_Thread.BACKEDGE);
          int bTarget = bIP + offset;
          int mTarget;
          asm.emitL  (T0, 0, SP);
	  asm.emitAIr(0,  T0,  0); // T0 to 0 and sets CR0 
          asm.emitCAL(SP, 4, SP);  // completes pop
          if (VM.TraceAssembler) asm.comment(">>> bi " + bTarget);
          if (offset <= 0) {
            mTarget = asm.relativeMachineAddress(bTarget);
	  } else {
            mTarget = 0;
            asm.reserveForwardConditionalBranch(bTarget);
	  }
          asm.emitBLE(mTarget);
          break;
	}
        case 0x9f: /* --- if_icmpeq --- */ {
          int offset = fetch2BytesSigned();
          if (VM.TraceAssembler) asm.noteBytecode("if_icmpeq " + offset);
	  if(offset <= 0) genThreadSwitchTest(VM_Thread.BACKEDGE);
          int bTarget = bIP + offset;
          int mTarget;
          asm.emitL  (T0, 4, SP);
	  asm.emitL  (T1, 0, SP);
	  asm.emitCMP(T0, T1);    // sets CR0
          asm.emitCAL(SP, 8, SP); // completes 2 pops
          if (VM.TraceAssembler) asm.comment(">>> bi " + bTarget);
          if (offset <= 0) {
            mTarget = asm.relativeMachineAddress(bTarget);
	  } else {
            mTarget = 0;
            asm.reserveForwardConditionalBranch(bTarget);
          }
          asm.emitBEQ(mTarget);
          break;
	}
        case 0xa0: /* --- if_icmpne --- */ {
          int offset = fetch2BytesSigned();
          if (VM.TraceAssembler) asm.noteBytecode("if_icmpne " + offset);
          if(offset <= 0) genThreadSwitchTest(VM_Thread.BACKEDGE);
          int bTarget = bIP + offset;
          int mTarget;
          asm.emitL  (T0, 4, SP);
	  asm.emitL  (T1, 0, SP);
	  asm.emitCMP(T0, T1);    // sets CR0
          asm.emitCAL(SP, 8, SP); // completes 2 pops
          if (VM.TraceAssembler) asm.comment(">>> bi " + bTarget);
          if (offset <= 0) {
            mTarget = asm.relativeMachineAddress(bTarget);
	  } else {
            mTarget = 0;
            asm.reserveForwardConditionalBranch(bTarget);
          }
          asm.emitBNE(mTarget);
          break;
	}
        case 0xa1: /* --- if_icmplt --- */ {
          int offset = fetch2BytesSigned();
          if (VM.TraceAssembler) asm.noteBytecode("if_icmplt " + offset);
          if(offset <= 0) genThreadSwitchTest(VM_Thread.BACKEDGE);
          int bTarget = bIP + offset;
          int mTarget;
          asm.emitL  (T0, 4, SP);
	  asm.emitL  (T1, 0, SP);
	  asm.emitCMP(T0, T1);    // sets CR0
          asm.emitCAL(SP, 8, SP); // completes 2 pops
          if (VM.TraceAssembler) asm.comment(">>> bi " + bTarget);
          if (offset <= 0) {
            mTarget = asm.relativeMachineAddress(bTarget);
	  } else {
            mTarget = 0;
            asm.reserveForwardConditionalBranch(bTarget);
          }
          asm.emitBLT(mTarget);
          break;
	}
        case 0xa2: /* --- if_icmpge --- */ {
          int offset = fetch2BytesSigned();
          if (VM.TraceAssembler) asm.noteBytecode("if_icmpge " + offset);
          if(offset <= 0) genThreadSwitchTest(VM_Thread.BACKEDGE);
          int bTarget = bIP + offset;
          int mTarget;
          asm.emitL  (T0, 4, SP);
	  asm.emitL  (T1, 0, SP);
	  asm.emitCMP(T0, T1);    // sets CR0
          asm.emitCAL(SP, 8, SP); // completes 2 pops
          if (VM.TraceAssembler) asm.comment(">>> bi " + bTarget);
          if (offset <= 0) {
            mTarget = asm.relativeMachineAddress(bTarget);
	  } else {
            mTarget = 0;
            asm.reserveForwardConditionalBranch(bTarget);
          }
          asm.emitBGE(mTarget);
          break;
	}
        case 0xa3: /* --- if_icmpgt --- */ {
          int offset = fetch2BytesSigned();
          if (VM.TraceAssembler) asm.noteBytecode("if_icmpgt " + offset);
          if(offset <= 0) genThreadSwitchTest(VM_Thread.BACKEDGE);
          int bTarget = bIP + offset;
          int mTarget;
          asm.emitL  (T0, 4, SP);
	  asm.emitL  (T1, 0, SP);
	  asm.emitCMP(T0, T1);    // sets CR0
          asm.emitCAL(SP, 8, SP); // completes 2 pops
          if (VM.TraceAssembler) asm.comment(">>> bi " + bTarget);
          if (offset <= 0) {
            mTarget = asm.relativeMachineAddress(bTarget);
	  } else {
            mTarget = 0;
            asm.reserveForwardConditionalBranch(bTarget);
          }
          asm.emitBGT(mTarget);
          break;
	}
        case 0xa4: /* --- if_icmple --- */ {
          int offset = fetch2BytesSigned();
          if (VM.TraceAssembler) asm.noteBytecode("if_icmple " + offset);
          if(offset <= 0) genThreadSwitchTest(VM_Thread.BACKEDGE);
	  int bTarget = bIP + offset;
          int mTarget;
          asm.emitL  (T0, 4, SP);
	  asm.emitL  (T1, 0, SP);
	  asm.emitCMP(T0, T1);    // sets CR0
          asm.emitCAL(SP, 8, SP); // completes 2 pops
          if (VM.TraceAssembler) asm.comment(">>> bi " + bTarget);
          if (offset <= 0) {
            mTarget = asm.relativeMachineAddress(bTarget);
	  } else {
            mTarget = 0;
            asm.reserveForwardConditionalBranch(bTarget);
          }
          asm.emitBLE(mTarget);
          break;
	}
        case 0xa5: /* --- if_acmpeq --- */ {
          int offset = fetch2BytesSigned();
          if (VM.TraceAssembler) asm.noteBytecode("if_acmpeq " + offset);
          if(offset <= 0) genThreadSwitchTest(VM_Thread.BACKEDGE);
          int bTarget = bIP + offset;
          int mTarget;
          asm.emitL (T0, 4, SP);
	  asm.emitL (T1, 0, SP);
	  asm.emitCMP(T0, T1);    // sets CR0
          asm.emitCAL(SP, 8, SP); // completes 2 pops
          if (VM.TraceAssembler) asm.comment(">>> bi " + bTarget);
          if (offset <= 0) {
            mTarget = asm.relativeMachineAddress(bTarget);
	  } else {
            mTarget = 0;
            asm.reserveForwardConditionalBranch(bTarget);
	  }
          asm.emitBEQ(mTarget);
          break;
	}
        case 0xa6: /* --- if_acmpne --- */ {
          int offset = fetch2BytesSigned();
          if (VM.TraceAssembler) asm.noteBytecode("if_acmpne " + offset);
          if(offset <= 0) genThreadSwitchTest(VM_Thread.BACKEDGE);
          int bTarget = bIP + offset;
          int mTarget;
          asm.emitL (T0, 4, SP);
	  asm.emitL (T1, 0, SP);
	  asm.emitCMP(T0, T1);    // sets CR0
          asm.emitCAL(SP, 8, SP); // completes 2 pops
	  if (VM.TraceAssembler) asm.comment(">>> bi " + bTarget);
          if (offset <= 0) {
            mTarget = asm.relativeMachineAddress(bTarget);
	  } else {
            mTarget = 0;
            asm.reserveForwardConditionalBranch(bTarget);
	  }
          asm.emitBNE(mTarget);
          break;
	}
        case 0xa7: /* --- goto --- */ {
          int offset = fetch2BytesSigned();
          if (VM.TraceAssembler) asm.noteBytecode("goto " + offset);
          if(offset <= 0) genThreadSwitchTest(VM_Thread.BACKEDGE);
          int bTarget = bIP + offset;
          int mTarget;
          if (offset <= 0) {
            mTarget = asm.relativeMachineAddress(bTarget);
	  } else {
            mTarget = 0;
            asm.reserveForwardBranch(bTarget);
	  }
          if (VM.TraceAssembler) asm.comment(">>> bi " + bTarget);
          asm.emitB(mTarget);
          break;
	}
        case 0xa8: /* --- jsr --- */ {
          int offset = fetch2BytesSigned();
          if (VM.TraceAssembler) asm.noteBytecode("jsr " + offset);
	  asm.emitBL  ( 1);
          asm.emitMFLR(T1);           // LR +  0
	  asm.emitCAL (T1, 16, T1);   // LR +  4  (LR + 16 is ret address)
	  asm.emitSTU (T1, -4, SP);   // LR +  8
          int bTarget = bIP + offset;
          int mTarget;
          if (offset <= 0) {
            mTarget = asm.relativeMachineAddress(bTarget);
	  } else {
            mTarget = 0;
            asm.reserveForwardBranch(bTarget);
	  }
          if (VM.TraceAssembler) asm.comment(">-> bi " + bTarget);
          asm.emitBL(mTarget);        // LR + 12
          break;
	}
        case 0xa9: /* --- ret --- */ {
          int index = fetch1ByteUnsigned();
          if (VM.TraceAssembler) asm.noteBytecode("ret " + index);
          int offset = localOffset(index);
	  asm.emitL(T0, offset, FP);
          asm.emitMTLR(T0);
	  asm.emitBLR ();
          break;
	}
        case 0xaa: /* --- tableswitch --- */ {
          int align = bindex & 3;
          if (align != 0) bindex += 4-align; // eat padding
          int defaultval = fetch4BytesSigned();
          int low = fetch4BytesSigned();
          int high = fetch4BytesSigned();
          if (VM.TraceAssembler) asm.noteBytecode ("tableswitch [" + low + "--" + high + "] " + defaultval);
          int n = high-low+1;       // n = number of normal cases (0..n-1)
	  asm.emitL   (T0, 0, SP);  // T0 is index
	  asm.emitBL  (n+2);        // branch past table; establish base addr
          for (int i=0; i<n; i++) {
	    int offset = fetch4BytesSigned();
	    int bTarget = bIP + offset;
	    if (VM.TraceAssembler) asm.comment("--> bi " + bTarget);
	    // branch target is off table base NOT current instruction
	    // must correct relative address by i
            if (offset <= 0) {
	      asm.emitDATA((asm.relativeMachineAddress(bTarget) + i) << 2);
            } else {
              asm.reserveForwardCase(bTarget);
	      asm.emitDATA(i);
	    }
          }
          int bTarget = bIP + defaultval;
	  if (VM.TraceAssembler) asm.comment("--> bi " + bTarget + " default");
          if (defaultval <= 0) {
	    asm.emitDATA((asm.relativeMachineAddress(bTarget) + n) << 2);
	  } else {
	    asm.reserveForwardCase(bTarget);
	    asm.emitDATA(n);
	  }
	  asm.emitMFLR(T1);         // T1 is base of table
	  asm.emitLVAL(T2, low);
	  asm.emitSFr (T0, T2, T0); // CRbit0 is 1 iff index < low
	  asm.emitLVAL(T2,  n);     // T2 is default offset (high-low+2)
	  asm.emitCMP ( 1, T0, T2); // CRbit5 is 1 iff high+1 < index
	  asm.emitCROR( 0,  0,  5); // CRbit0 is 0 iff low <= index <= default
	  asm.emitBGE ( 2);         // if index in range, skip
	  asm.emitCAL (T0,  0, T2); // else use default instead
	  asm.emitSLI (T0, T0,  2); // convert to bytes
	  asm.emitLX  (T0, T0, T1); // T0 is relative offset of desired case
	  asm.emitA   (T1, T1, T0); // T1 is absolute address of desired case
	  asm.emitMTLR(T1);
          asm.emitCAL (SP,  4, SP); // pop index from stack
	  asm.emitBLR ();
          break;
	}
        case 0xab: /* --- lookupswitch --- */ {
          int align = bindex & 3;
          if (align != 0) bindex += 4-align; // eat padding
          int defaultval = fetch4BytesSigned();
          int npairs = fetch4BytesSigned();
          if (VM.TraceAssembler) asm.noteBytecode("lookupswitch [<" + npairs + ">]" + defaultval);
          asm.emitBL  ((npairs<<1)+3);// branch past table; establish base addr
          for (int i=0; i<npairs; i++) {
	    int match   = fetch4BytesSigned();
	    if (VM.TraceAssembler) asm.comment(" case " + (i+i+1));
	    asm.emitDATA(match);
	    int offset  = fetch4BytesSigned();
	    int bTarget = bIP + offset;
	    if (VM.TraceAssembler) asm.comment("--> bi " + bTarget);
	    // branch target is off table base NOT current instruction
	    // must correct relative address by 2i+1
	    int displacement = (i<<1)+1;
            if (offset <= 0) {
	      asm.emitDATA((asm.relativeMachineAddress(bTarget) + displacement) << 2);
            } else {
              asm.reserveForwardCase(bTarget);
	      asm.emitDATA(displacement);
	    }
          }
          if (VM.TraceAssembler) asm.comment(" default");
	  asm.emitDATA(0x7FFFFFFF);
	  int bTarget = bIP + defaultval;
	  if (VM.TraceAssembler) asm.comment("--> bi " + bTarget);
          int displacement = (npairs<<1)+1;
	  if (defaultval <= 0) {
	    asm.emitDATA((asm.relativeMachineAddress(bTarget) + displacement) << 2);
	  } else {
            asm.reserveForwardCase(bTarget);
	    asm.emitDATA(displacement);
	  }
	  asm.emitMFLR(T1);         // T1 is base of table
	  asm.emitL   (T0,  0, SP); // T0 is key
          asm.emitCAL (T2,  0, T1); // T2 -> first pair in the table
	  asm.emitL   (T3,  0, T2); // T3 is first match
	  // TODO replace linear search loop with binary search
	  asm.emitCMP (T0, T3);     // loop: key ? current match
	  asm.emitLU  (T3,  8, T2); // T2 -> next pair, T3 is next match
	  asm.emitBGT (-2);         // if key > current match, goto loop
	  asm.emitBEQ ( 2);         // if key = current match, skip
	  asm.emitCAL (T2, (npairs+1)<<3, T1); // T2 -> after default pair
	  asm.emitL   (T0, -4, T2); // T0 is relative offset  of desired case
	  asm.emitA   (T1, T0, T1); // T1 is absolute address of desired case
	  asm.emitMTLR(T1);
	  asm.emitCAL (SP,  4, SP); // pop key
	  asm.emitBLR ();
	  break;
	}
        case 0xac: /* --- ireturn --- */ {
          if (VM.TraceAssembler) asm.noteBytecode("ireturn");
          if (VM.UseEpilogueYieldPoints) genThreadSwitchTest(VM_Thread.EPILOGUE); 
          if (method.isSynchronized()) 
	    genSynchronizedMethodEpilogue();
          asm.emitL(T0, 0, SP);
	  genEpilogue();
          break;
	}
        case 0xad: /* --- lreturn --- */ {
          if (VM.TraceAssembler) asm.noteBytecode("lreturn");
          if (VM.UseEpilogueYieldPoints) genThreadSwitchTest(VM_Thread.EPILOGUE); 
          if (method.isSynchronized()) 
	    genSynchronizedMethodEpilogue();
          asm.emitL(T1, 4, SP); // hi register := lo word (which is at higher memory address)
	  asm.emitL(T0, 0, SP); // lo register := hi word (which is at lower  memory address)
	  genEpilogue();
          break;
	}
        case 0xae: /* --- freturn --- */ {
          if (VM.TraceAssembler) asm.noteBytecode("freturn");
          if (VM.UseEpilogueYieldPoints) genThreadSwitchTest(VM_Thread.EPILOGUE); 
          if (method.isSynchronized()) 
	    genSynchronizedMethodEpilogue();
          asm.emitLFS(F0, 0, SP);
          genEpilogue();
          break;
	}
        case 0xaf: /* --- dreturn --- */ {
          if (VM.TraceAssembler) asm.noteBytecode("dreturn");
          if (VM.UseEpilogueYieldPoints) genThreadSwitchTest(VM_Thread.EPILOGUE); 
          if (method.isSynchronized()) 
	    genSynchronizedMethodEpilogue();
          asm.emitLFD(F0, 0, SP);
          genEpilogue();
          break;
	}
        case 0xb0: /* --- areturn --- */ {
          if (VM.TraceAssembler) asm.noteBytecode("areturn");
          if (VM.UseEpilogueYieldPoints) genThreadSwitchTest(VM_Thread.EPILOGUE); 
          if (method.isSynchronized()) 
	    genSynchronizedMethodEpilogue();
          asm.emitL(T0, 0, SP);
          genEpilogue();
          break;
	}
        case 0xb1: /* --- return --- */ {
          if (VM.TraceAssembler) asm.noteBytecode("return");
          if (VM.UseEpilogueYieldPoints) genThreadSwitchTest(VM_Thread.EPILOGUE); 
          if (method.isSynchronized()) 
	    genSynchronizedMethodEpilogue();
	  genEpilogue();
          break;
        }
        case 0xb2: /* --- getstatic --- */ {
          int constantPoolIndex = fetch2BytesUnsigned();
          VM_Field fieldRef = klass.getFieldRef(constantPoolIndex);
          if (VM.TraceAssembler) asm.noteBytecode("getstatic " + constantPoolIndex  + " (" + fieldRef + ")");
	  boolean classPreresolved = false;
	  VM_Class fieldRefClass = fieldRef.getDeclaringClass();
	  if (fieldRef.needsDynamicLink(method) && VM.BuildForPrematureClassResolution) {
	    try {
	      fieldRefClass.load();
	      fieldRefClass.resolve();
	      classPreresolved = true;
	    } catch (Exception e) { // report the exception at runtime
	      VM.sysWrite("WARNING: during compilation of " + method + " premature resolution of " + fieldRefClass + " provoked the following exception: " + e); // TODO!! remove this  warning message
	    }
	  }
	  if (VM.BuildForPrematureClassResolution &&
	      !fieldRefClass.isInitialized() &&
	      !(fieldRefClass == klass) &&
	      !(fieldRefClass.isInBootImage() && VM.writingBootImage)
	      ) { // TODO!! rearrange the following code to backpatch after the first call
	    asm.emitLtoc(S0, VM_Entrypoints.initializeClassIfNecessaryOffset);
	    asm.emitMTLR(S0);
	    asm.emitLVAL(T0, fieldRefClass.getDictionaryId()); 
	    asm.emitCall(spSaveAreaOffset);
	    classPreresolved = true;
	  }
	  if (fieldRef.needsDynamicLink(method) && !classPreresolved) {
	    if (VM.VerifyAssertions && VM.BuildForStrongVolatileSemantics) // Either VM.BuildForPrematureClassResolution was not set or the class was not found (these cases are not yet handled)
	      VM.assert(VM.NOT_REACHED); // TODO!! handle this case by backpatching with code that assumes the field is volatile
            asm.emitLtoc(T0, VM_Entrypoints.getstaticOffset);
            asm.emitMTLR(T0);
            asm.emitDynamicCall(spSaveAreaOffset, klass.getFieldRefId(constantPoolIndex));
            asm.emitISYNC();
            asm.emitB(-1);
            asm.emitB(-2);
            asm.emitB(-3);
	  } else {
            fieldRef = fieldRef.resolve();
            int fieldOffset = fieldRef.getOffset();
            if (fieldRef.isVolatile() && VM.BuildForStrongVolatileSemantics) {
	      // asm.emitISYNC(); // to make getstatic of a volatile field a read barrier uncomment this line (Note this is untested and the Opt compiler must also behave.))
	      if (fieldRef.getSize() == 4) {  // see Appendix E of PowerPC Microprocessor Family: The Programming nvironments
		asm.emitLVAL  (T1, fieldOffset); // T1 = fieldOffset
		asm.emitLWARX (T0, JTOC, T1);    // T0 = value
		asm.emitSTWCXr(T0, JTOC, T1);    // atomically rewrite value
		asm.emitBNE   (-2);              // retry, if reservation lost
		asm.emitSTU   (T0, -4, SP);      // push value
	      } else { // volatile field is two words (double or long)
		if (VM.VerifyAssertions) VM.assert(fieldRef.getSize() == 8);
		asm.emitLtoc  (T0, VM_Entrypoints.doublewordVolatileMutexOffset);
		asm.emitL     (T1, OBJECT_TIB_OFFSET, T0);
		asm.emitL     (S0, VM_Entrypoints.processorLockOffset, T1);
		asm.emitMTLR  (S0);
		asm.emitCall  (spSaveAreaOffset);
		asm.emitLFDtoc(F0, fieldOffset, T0);
		asm.emitSTFDU (F0, -8, SP);
		asm.emitLtoc  (T0, VM_Entrypoints.doublewordVolatileMutexOffset);
		asm.emitL     (T1, OBJECT_TIB_OFFSET, T0);
		asm.emitL     (S0, VM_Entrypoints.processorUnlockOffset, T1);
		asm.emitMTLR  (S0);
		asm.emitCall  (spSaveAreaOffset);
	      }
	    } else if (fieldRef.getSize() == 4) { // field is one word
              asm.emitLtoc(T0, fieldOffset);
              asm.emitSTU (T0, -4, SP);
            } else { // field is two words (double or long)
	      if (VM.VerifyAssertions) VM.assert(fieldRef.getSize() == 8);
              asm.emitLFDtoc(F0, fieldOffset, T0);
              asm.emitSTFDU (F0, -8, SP);
            }
          }
          break;
        }
        case 0xb3: /* --- putstatic --- */ {
          int constantPoolIndex = fetch2BytesUnsigned();
          int fieldId = klass.getFieldRefId(constantPoolIndex);
          VM_Field fieldRef = VM_FieldDictionary.getValue(fieldId);
          if (VM.TraceAssembler) asm.noteBytecode("putstatic " + constantPoolIndex + " (" + fieldRef + ")");
          if (VM_Collector.NEEDS_WRITE_BARRIER && !fieldRef.getType().isPrimitiveType()) {
	    if (fieldRef.needsDynamicLink(method))
	      VM_Barriers.compileUnresolvedPutstaticBarrier(asm, spSaveAreaOffset, fieldId);
	    else
	      VM_Barriers.compilePutstaticBarrier(asm, spSaveAreaOffset, fieldRef.getOffset());
	  }
          boolean classPreresolved = false;
	  VM_Class fieldRefClass = fieldRef.getDeclaringClass();
	  if (fieldRef.needsDynamicLink(method) && VM.BuildForPrematureClassResolution) {
	    try {
	      fieldRefClass.load();
	      fieldRefClass.resolve();
	      classPreresolved = true;
	    } catch (Exception e) { // report the exception at runtime
	      System.err.println("WARNING: during compilation of " + method + " premature resolution of " + fieldRefClass + " provoked the following exception: " + e); // TODO!! remove this  warning message
	    }
	  }
	  if (VM.BuildForPrematureClassResolution &&
	      !fieldRefClass.isInitialized() &&
	      !(fieldRefClass == klass) &&
	      !(fieldRefClass.isInBootImage() && VM.writingBootImage)
	      ) { // TODO!! rearrange the following code to backpatch after the first call
	    asm.emitLtoc(S0, VM_Entrypoints.initializeClassIfNecessaryOffset);
	    asm.emitMTLR(S0);
	    asm.emitLVAL(T0, fieldRefClass.getDictionaryId()); 
	    asm.emitCall(spSaveAreaOffset);
	    classPreresolved = true;
VM.sysWrite("static WARNING: during compilation of " + method + " premature resolution of " + fieldRefClass + "\n"); // TODO!! remove this  warning message
	  }
	  if (fieldRef.needsDynamicLink(method) && !classPreresolved) {
            if (VM.VerifyAssertions && VM.BuildForStrongVolatileSemantics) // Either VM.BuildForPrematureClassResolution was not set or the class was not found (these cases are not yet handled)
	      VM.assert(VM.NOT_REACHED); // TODO!! handle this case by backpatching with code that assumes the field is volatile
            asm.emitL   (T0, VM_Entrypoints.putstaticOffset, JTOC);
            asm.emitMTLR(T0);
            asm.emitDynamicCall(spSaveAreaOffset, fieldId);
            asm.emitISYNC();
            asm.emitB(-1);
            asm.emitB(-2);
            asm.emitB(-3);
            asm.emitB(-4);
            if (VM.BuildForConcurrentGC && ! fieldRef.getType().isPrimitiveType()) { 
	      //-#if RVM_WITH_CONCURRENT_GC // because VM_RCBarriers not available for non concurrent GC builds
              VM_RCBarriers.compileDynamicPutstaticBarrier(asm, spSaveAreaOffset, method, fieldRef);
	      //-#endif
            } 
	  } else {
            fieldRef = fieldRef.resolve();
            int fieldOffset = fieldRef.getOffset();
            if (fieldRef.isVolatile() && VM.BuildForStrongVolatileSemantics) {
	      if (fieldRef.getSize() == 4) { // field is one word
		asm.emitL     (T0, 0, SP);
		asm.emitCAL   (SP, 4, SP);
		if (VM.BuildForConcurrentGC && ! fieldRef.getType().isPrimitiveType()) {
		  //-#if RVM_WITH_CONCURRENT_GC // because VM_RCBarriers not available for non concurrent GC builds
		  VM_RCBarriers.compilePutstaticBarrier(asm, spSaveAreaOffset, fieldOffset);
		  //-#endif
		} else { // see Appendix E of PowerPC Microprocessor Family: The Programming Environments
		  asm.emitLVAL  (T1, fieldOffset); // T1 = fieldOffset
		  asm.emitLWARX (S0, JTOC, T1);    // S0 = old value
		  asm.emitSTWCXr(T0, JTOC, T1);    // atomically replace with new value (T0)
		  asm.emitBNE   (-2);              // retry, if reservation lost
		}
		// asm.emitSYNC(); // to make putstatic of a volatile field a write barrier uncomment this line (Note this is untested and the Opt compiler must also behave.))
	      } else { // volatile field is two words (double or long)
		if (VM.VerifyAssertions) VM.assert(fieldRef.getSize() == 8);
		asm.emitLtoc   (T0, VM_Entrypoints.doublewordVolatileMutexOffset);
		asm.emitL      (T1, OBJECT_TIB_OFFSET, T0);
		asm.emitL      (S0, VM_Entrypoints.processorLockOffset, T1);
		asm.emitMTLR   (S0);
		asm.emitCall   (spSaveAreaOffset);
		asm.emitLFD    (F0, 0, SP );
		asm.emitCAL    (SP, 8, SP);
		asm.emitSTFDtoc(F0, fieldOffset, T0);
		asm.emitLtoc   (T0, VM_Entrypoints.doublewordVolatileMutexOffset);
		asm.emitL      (T1, OBJECT_TIB_OFFSET, T0);
		asm.emitL      (S0, VM_Entrypoints.processorUnlockOffset, T1);
		asm.emitMTLR   (S0);
		asm.emitCall   (spSaveAreaOffset);
	      }
	    } else if (fieldRef.getSize() == 4) { // field is one word
              asm.emitL    (T0, 0, SP);
              asm.emitCAL  (SP, 4, SP);
              if (VM.BuildForConcurrentGC && ! fieldRef.getType().isPrimitiveType()) {
		//-#if RVM_WITH_CONCURRENT_GC // because VM_RCBarriers not available for non concurrent GC builds
		VM_RCBarriers.compilePutstaticBarrier(asm, spSaveAreaOffset, fieldOffset);
		//-#endif
              } else {
                asm.emitSTtoc(T0, fieldOffset, T1);
	      }
	    } else { // field is two words (double or long)
              if (VM.VerifyAssertions) VM.assert(fieldRef.getSize() == 8);
              asm.emitLFD    (F0, 0, SP );
	      asm.emitCAL    (SP, 8, SP);
              asm.emitSTFDtoc(F0, fieldOffset, T0);
	    }
          }
          break;
	}
        case 0xb4: /* --- getfield --- */ {
          int constantPoolIndex = fetch2BytesUnsigned();
          VM_Field fieldRef = klass.getFieldRef(constantPoolIndex);
          if (VM.TraceAssembler) asm.noteBytecode("getfield " + constantPoolIndex  + " (" + fieldRef + ")");
          boolean classPreresolved = false;
	  VM_Class fieldRefClass = fieldRef.getDeclaringClass();
	  if (fieldRef.needsDynamicLink(method) && VM.BuildForPrematureClassResolution) {
	    try {
	      fieldRefClass.load();
	      fieldRefClass.resolve();
	      classPreresolved = true;
	    } catch (Exception e) { 
	      System.err.println("WARNING: during compilation of " + method + " premature resolution of " + fieldRefClass + " provoked the following exception: " + e); // TODO!! remove this  warning message
	    } // report the exception at runtime
	  }
	  if (fieldRef.needsDynamicLink(method) && !classPreresolved) {
            if (VM.VerifyAssertions && VM.BuildForStrongVolatileSemantics) // Either VM.BuildForPrematureClassResolution was not set or the class was not found (these cases are not yet handled)
	      VM.assert(VM.NOT_REACHED); // TODO!! handle this case by backpatching with code that assumes the field is volatile
            asm.emitL   (T0, VM_Entrypoints.getfieldOffset, JTOC);
            asm.emitMTLR(T0);
            asm.emitDynamicCall(spSaveAreaOffset, klass.getFieldRefId(constantPoolIndex));
            asm.emitISYNC();
            asm.emitB(-1);
            asm.emitB(-2);
            asm.emitB(-3);
	  } else {
            fieldRef = fieldRef.resolve();
	    int fieldOffset = fieldRef.getOffset();
            if (fieldRef.isVolatile() && VM.BuildForStrongVolatileSemantics) {
	      if (fieldRef.getSize() == 4) { // field is one word
		// asm.emitISYNC(); // to make getfield of a volatile field a read barrier uncomment this line (Note this is untested and the Opt compiler must also behave.))
		asm.emitL  (T1, 0, SP);                // T1 = object to read
		// see Appendix E of PowerPC Microprocessor Family: The Programming Environments
		asm.emitCAL   (T1, fieldOffset, T1); // T1 = pointer to slot
		asm.emitLWARX (T0, 0, T1);           // T0 = value
		asm.emitSTWCXr(T0, 0, T1);           // atomically replace with new value (T0)
		asm.emitBNE   (-2);	             // retry, if reservation lost
		asm.emitST    (T0, 0, SP);           // push value
	      } else { // volatile field is two words (double or long)
		if (VM.VerifyAssertions) VM.assert(fieldRef.getSize() == 8);
		asm.emitLtoc  (T0, VM_Entrypoints.doublewordVolatileMutexOffset);
		asm.emitL     (T1, OBJECT_TIB_OFFSET, T0);
		asm.emitL     (S0, VM_Entrypoints.processorLockOffset, T1);
		asm.emitMTLR  (S0);
		asm.emitCall  (spSaveAreaOffset);
		asm.emitL     (T1, 0, SP);
		asm.emitLFD   (F0, fieldOffset, T1);
		asm.emitSTFDU (F0, -4, SP);
		asm.emitLtoc  (T0, VM_Entrypoints.doublewordVolatileMutexOffset);
		asm.emitL     (T1, OBJECT_TIB_OFFSET, T0);
		asm.emitL     (S0, VM_Entrypoints.processorUnlockOffset, T1);
		asm.emitMTLR  (S0);
		asm.emitCall  (spSaveAreaOffset);
	      }
	    } else if (fieldRef.getSize() == 4) { // field is one word
	      asm.emitL (T1, 0, SP);
              asm.emitL (T0, fieldOffset, T1);
              asm.emitST(T0, 0, SP);
	    } else { // field is two words (double or long)
              if (VM.VerifyAssertions) VM.assert(fieldRef.getSize() == 8);
              asm.emitL    (T1, 0, SP);
              asm.emitLFD  (F0, fieldOffset, T1);
	      asm.emitSTFDU(F0, -4, SP);
	    }
	  }
          break;
	}
        case 0xb5: /* --- putfield --- */ {
          int constantPoolIndex = fetch2BytesUnsigned();
          int fieldId = klass.getFieldRefId(constantPoolIndex);
	  VM_Field fieldRef = VM_FieldDictionary.getValue(fieldId);
	  if (VM.TraceAssembler) asm.noteBytecode("putfield " + constantPoolIndex + " (" + fieldRef + ")");
          if (VM_Collector.NEEDS_WRITE_BARRIER &&  !fieldRef.getType().isPrimitiveType()) {
	    if (fieldRef.needsDynamicLink(method))
	      VM_Barriers.compileUnresolvedPutfieldBarrier(asm, spSaveAreaOffset, fieldId);
	    else
	      VM_Barriers.compilePutfieldBarrier(asm, spSaveAreaOffset, fieldRef.getOffset());
	  }
	  boolean classPreresolved = false;
	  VM_Class fieldRefClass = fieldRef.getDeclaringClass();
	  if (fieldRef.needsDynamicLink(method) && VM.BuildForPrematureClassResolution) {
	    try {
	      fieldRefClass.load();
	      fieldRefClass.resolve();
	      classPreresolved = true;
	    } catch (Exception e) { 
	      System.err.println("WARNING: during compilation of " + method + " premature resolution of " + fieldRefClass + " provoked the following exception: " + e); // TODO!! remove this  warning message
	    } // report the exception at runtime
	  }
	  if (fieldRef.needsDynamicLink(method) && !classPreresolved) {
	    if (VM.VerifyAssertions && VM.BuildForStrongVolatileSemantics) // Either VM.BuildForPrematureClassResolution was not set or the class was not found (these cases are not yet handled)
	      VM.assert(VM.NOT_REACHED); // TODO!! handle this case by backpatching with code that assumes the field is volatile
            asm.emitL   (T0, VM_Entrypoints.putfieldOffset, JTOC);
	    asm.emitMTLR(T0);
	    asm.emitDynamicCall(spSaveAreaOffset, fieldId);
	    asm.emitISYNC();
	    asm.emitB(-1);
	    asm.emitB(-2);
	    asm.emitB(-3);
	    asm.emitB(-4);
	    if (VM.BuildForConcurrentGC && ! fieldRef.getType().isPrimitiveType()) { 
	      //-#if RVM_WITH_CONCURRENT_GC // because VM_RCBarriers not available for non concurrent GC builds
	      VM_RCBarriers.compileDynamicPutfieldBarrier(asm, spSaveAreaOffset, method, fieldRef);
	      //-#endif
	    } 
	  } else {
            fieldRef = fieldRef.resolve();
	    int fieldOffset = fieldRef.getOffset();
            if (fieldRef.isVolatile() && VM.BuildForStrongVolatileSemantics) {
	      if (fieldRef.getSize() == 4) { // field is one word
		asm.emitL  (T1, 4, SP);                // T1 = object to update
		asm.emitL  (T0, 0, SP);                // T0 = new value
		asm.emitCAL(SP, 8, SP);                // pop stack
		if (VM.BuildForConcurrentGC && ! fieldRef.getType().isPrimitiveType()) {
		  //-#if RVM_WITH_CONCURRENT_GC // because VM_RCBarriers not available for non concurrent GC builds
		  VM_RCBarriers.compilePutfieldBarrier(asm, spSaveAreaOffset, fieldOffset, method, fieldRef);
		  //-#endif
		} else { // see Appendix E of PowerPC Microprocessor Family: The Programming Environments
		  asm.emitCAL   (T1, fieldOffset, T1); // T1 = pointer to slot
		  asm.emitLWARX (S0, 0, T1);           // S0 = old value
		  asm.emitSTWCXr(T0, 0, T1);           // atomically replace with new value (T0)
		  asm.emitBNE   (-2);	               // retry, if reservation lost
		}
		// asm.emitSYNC(); // to make putfield of a volatile field a write barrier uncomment this line (Note this is untested and the Opt compiler must also behave.))
	      } else { // volatile field is two words (double or long)
		if (VM.VerifyAssertions) VM.assert(fieldRef.getSize() == 8);
		asm.emitLtoc  (T0, VM_Entrypoints.doublewordVolatileMutexOffset);
		asm.emitL     (T1, OBJECT_TIB_OFFSET, T0);
		asm.emitL     (S0, VM_Entrypoints.processorLockOffset, T1);
		asm.emitMTLR  (S0);
		asm.emitCall  (spSaveAreaOffset);
		asm.emitLFD   (F0,  0, SP);
		asm.emitL     (T1,  8, SP);
		asm.emitCAL   (SP, 12, SP);
		asm.emitSTFD  (F0, fieldOffset, T1);
		asm.emitLtoc  (T0, VM_Entrypoints.doublewordVolatileMutexOffset);
		asm.emitL     (T1, OBJECT_TIB_OFFSET, T0);
		asm.emitL     (S0, VM_Entrypoints.processorUnlockOffset, T1);
		asm.emitMTLR  (S0);
		asm.emitCall  (spSaveAreaOffset);
	      }
	    } else if (fieldRef.getSize() == 4) { // field is one word
	      asm.emitL  (T1, 4, SP);
	      asm.emitL  (T0, 0, SP);
              asm.emitCAL(SP, 8, SP);  
	      if (VM.BuildForConcurrentGC && ! fieldRef.getType().isPrimitiveType()) {
		//-#if RVM_WITH_CONCURRENT_GC // because VM_RCBarriers not available for non concurrent GC builds
		VM_RCBarriers.compilePutfieldBarrier(asm, spSaveAreaOffset, fieldOffset, method, fieldRef);
		//-#endif
	      } else {
		asm.emitST (T0, fieldOffset, T1);
	      }
	    } else { // field is two words (double or long)
              if (VM.VerifyAssertions) VM.assert(fieldRef.getSize() == 8);
              asm.emitLFD (F0,  0, SP);
	      asm.emitL   (T1,  8, SP);
              asm.emitCAL (SP, 12, SP);
              asm.emitSTFD(F0, fieldOffset, T1);
	    }
          }
	  break;
        }  
        case 0xb6: /* --- invokevirtual --- */ {
          int constantPoolIndex = fetch2BytesUnsigned();
          VM_Method methodRef = klass.getMethodRef(constantPoolIndex);
          if (VM.TraceAssembler)  asm.noteBytecode("invokevirtual " + constantPoolIndex + " (" + methodRef + ")");
          boolean classPreresolved = false;
	  VM_Class methodRefClass = methodRef.getDeclaringClass();
	  if (methodRef.needsDynamicLink(method) && VM.BuildForPrematureClassResolution) {
	    try {
	      methodRefClass.load();
	      methodRefClass.resolve();
	      classPreresolved = true;
	    } catch (Exception e) { 
	      System.err.println("WARNING: during compilation of " + method + " premature resolution of " + methodRefClass + " provoked the following exception: " + e); // TODO!! remove this  warning message
	    } // report the exception at runtime
	  }
	  if (methodRef.needsDynamicLink(method) && !classPreresolved) {
            asm.emitL   (S0, VM_Entrypoints.invokevirtualOffset, JTOC);
            asm.emitMTLR(S0);
            asm.emitDynamicCall(spSaveAreaOffset, klass.getMethodRefId(constantPoolIndex));
            asm.emitISYNC();
            asm.emitB(-1);
            asm.emitB(-2);
            asm.emitB(-3);
            asm.emitMTLR(S0);
            genMoveParametersToRegisters(true, methodRef);
//-#if RVM_WITH_SPECIALIZATION
            asm.emitSpecializationCall(spSaveAreaOffset, method, bIP);
//-#else
            asm.emitCall(spSaveAreaOffset);
//-#endif
            genPopParametersAndPushReturnValue(true, methodRef);
          } else {
            methodRef = methodRef.resolve();
            int methodRefParameterWords = methodRef.getParameterWords() + 1; // +1 for "this" parameter
            int methodRefOffset = methodRef.getOffset();
            int objectOffset = (methodRefParameterWords << 2) - 4;
            asm.emitL   (T0, objectOffset,      SP);
            asm.emitL   (T1, OBJECT_TIB_OFFSET, T0);
            asm.emitL   (T2, methodRefOffset,   T1);
            asm.emitMTLR(T2);
            genMoveParametersToRegisters(true, methodRef);
	    //-#if RVM_WITH_SPECIALIZATION
            asm.emitSpecializationCall(spSaveAreaOffset, method, bIP);
	    //-#else
            asm.emitCall(spSaveAreaOffset);
	    //-#endif
            genPopParametersAndPushReturnValue(true, methodRef);
          }
          break;
	}
        case 0xb7: /* --- invokespecial --- */ {
          int constantPoolIndex = fetch2BytesUnsigned();
          VM_Method methodRef = klass.getMethodRef(constantPoolIndex);
          if (VM.TraceAssembler) asm.noteBytecode("invokespecial " + constantPoolIndex + " (" + methodRef + ")");
          VM_Method target;
	  VM_Class methodRefClass = methodRef.getDeclaringClass();
          if (!methodRef.getDeclaringClass().isResolved() && VM.BuildForPrematureClassResolution && false) {
	    try {
	      methodRefClass.load();
	      methodRefClass.resolve();
	    } catch (Exception e) { 
	      System.err.println("WARNING: during compilation of " + method + " premature resolution of " + methodRefClass + " provoked the following exception: " + e); // TODO!! remove this  warning message
	    } // report the exception at runtime
	  }
	  if (methodRef.getDeclaringClass().isResolved() && (target = VM_Class.findSpecialMethod(methodRef)) != null) {
	    if (target.isObjectInitializer() || target.isStatic()) { // invoke via method's jtoc slot
	      asm.emitLtoc(T0, target.getOffset());
	      asm.emitMTLR(T0);
	      genMoveParametersToRegisters(true, target);
	      //-#if RVM_WITH_SPECIALIZATION
	      asm.emitSpecializationCall(spSaveAreaOffset, method, bIP);
	      //-#else
	      asm.emitCall(spSaveAreaOffset);
	      //-#endif
	      genPopParametersAndPushReturnValue(true, target);
	    } else { // invoke via class's tib slot
	      asm.emitLtoc(T0, target.getDeclaringClass().getTibOffset());
	      asm.emitL   (T0, target.getOffset(), T0);
	      asm.emitMTLR(T0);
	      genMoveParametersToRegisters(true, target);
	      //-#if RVM_WITH_SPECIALIZATION
	      asm.emitSpecializationCall(spSaveAreaOffset, method, bIP);
	      //-#else
	      asm.emitCall(spSaveAreaOffset);
	      //-#endif
	      genPopParametersAndPushReturnValue(true, target);
	    }
          } else {
            asm.emitL   (S0, VM_Entrypoints.invokespecialOffset, JTOC);
            asm.emitMTLR(S0);
            asm.emitDynamicCall(spSaveAreaOffset, klass.getMethodRefId(constantPoolIndex));
            asm.emitISYNC();
            asm.emitB(-1);
            asm.emitB(-2);
            asm.emitMTLR(S0);
            genMoveParametersToRegisters(true, methodRef);
	    //-#if RVM_WITH_SPECIALIZATION
	    asm.emitSpecializationCall(spSaveAreaOffset, method, bIP);
	    //-#else
            asm.emitCall(spSaveAreaOffset);
	    //-#endif
            genPopParametersAndPushReturnValue(true, methodRef);
          }
          break;
	}
        case 0xb8: /* --- invokestatic --- */ {
          int constantPoolIndex = fetch2BytesUnsigned();
          VM_Method methodRef = klass.getMethodRef(constantPoolIndex);
          if (VM.TraceAssembler) asm.noteBytecode("invokestatic " + constantPoolIndex + " (" + methodRef + ")");
          if (methodRef.getDeclaringClass().isMagicType()) {
            VM_MagicCompiler.generateInlineCode(this, methodRef);
	    break;
          } 
	  boolean classPreresolved = false;
	  VM_Class methodRefClass = methodRef.getDeclaringClass();
	  if (methodRef.needsDynamicLink(method) && VM.BuildForPrematureClassResolution) {
	    try {
	      methodRefClass.load();
	      methodRefClass.resolve();
	      classPreresolved = true;
	    } catch (Exception e) { 
	      System.err.println("WARNING: during compilation of " + method + " premature resolution of " + methodRefClass + " provoked the following exception: " + e); // TODO!! remove this  warning message
	    } // report the exception at runtime
	  }
	  if (VM.BuildForPrematureClassResolution &&
	      !methodRefClass.isInitialized() &&
	      !(methodRefClass == klass) &&
	      !(methodRefClass.isInBootImage() && VM.writingBootImage)
	      ) { // TODO!! rearrange the following code to backpatch after the first call
	    asm.emitLtoc(S0, VM_Entrypoints.initializeClassIfNecessaryOffset);
	    asm.emitMTLR(S0);
	    asm.emitLVAL(T0, methodRefClass.getDictionaryId()); 
	    asm.emitCall(spSaveAreaOffset);
	    classPreresolved = true;
	}
	  if (methodRef.needsDynamicLink(method) && !classPreresolved) {
            asm.emitL   (S0, VM_Entrypoints.invokestaticOffset, JTOC);
            asm.emitMTLR(S0);
            asm.emitDynamicCall(spSaveAreaOffset, klass.getMethodRefId(constantPoolIndex));
            asm.emitISYNC();
            asm.emitB(-1);
            asm.emitB(-2);
            asm.emitMTLR(S0);
            genMoveParametersToRegisters(false, methodRef);
	    //-#if RVM_WITH_SPECIALIZATION
	    asm.emitSpecializationCall(spSaveAreaOffset, method, bIP);
	    //-#else
            asm.emitCall(spSaveAreaOffset);
	    //-#endif
            genPopParametersAndPushReturnValue(false, methodRef);
          } else {
            methodRef = methodRef.resolve();
            int methodOffset = methodRef.getOffset();
            asm.emitLtoc(T0, methodOffset);
            asm.emitMTLR(T0);
	    genMoveParametersToRegisters(false, methodRef);
	    //-#if RVM_WITH_SPECIALIZATION
	    asm.emitSpecializationCall(spSaveAreaOffset, method, bIP);
	    //-#else
            asm.emitCall(spSaveAreaOffset);
	    //-#endif
            genPopParametersAndPushReturnValue(false, methodRef);
          }
          break;
	}
        case 0xb9: /* --- invokeinterface --- */ {
          int constantPoolIndex = fetch2BytesUnsigned();
          VM_Method methodRef = klass.getMethodRef(constantPoolIndex);
          int count = fetch1ByteUnsigned();
          fetch1ByteSigned(); // eat superfluous 0
          if (VM.TraceAssembler) asm.noteBytecode("invokeinterface " + constantPoolIndex + " (" + methodRef + ") " + count + " 0");

	  // (1) Emit dynamic type checking sequence if required to 
          // do so inline.
          if (VM.BuildForIMTInterfaceInvocation || 
	      (VM.BuildForITableInterfaceInvocation && 
               VM.DirectlyIndexedITables)) {
	    VM_Method resolvedMethodRef = null;
	    try {
	      resolvedMethodRef = methodRef.resolveInterfaceMethod(false);
	    } catch (VM_ResolutionException e) {
	      // actually can't be thrown when we pass false for canLoad.
	    }
	    if (resolvedMethodRef == null) {
	      // might be a ghost ref. Call uncommon case typechecking routine 
              // to deal with this
	      asm.emitLtoc(T0, VM_Entrypoints.unresolvedInterfaceMethodOffset);
	      asm.emitMTLR(T0);
	      asm.emitLIL (T0, methodRef.getDictionaryId());  // dictionaryId of method we are trying to call
	      asm.emitL   (T1, (count-1) << 2, SP);           // the "this" object
	      asm.emitL   (T1, OBJECT_TIB_OFFSET, T1);        // its tib
	      asm.emitCall(spSaveAreaOffset);                 // throw exception, if link error
	    } else {
	      // normal case.  Not a ghost ref.
	      asm.emitLtoc(T0, VM_Entrypoints.mandatoryInstanceOfInterfaceOffset);
	      asm.emitMTLR(T0);
	      asm.emitLtoc(T0, methodRef.getDeclaringClass().getTibOffset()); // tib of the interface method
	      asm.emitL   (T0, TIB_TYPE_INDEX << 2, T0);                   // type of the interface method
	      asm.emitL   (T1, (count-1) << 2, SP);                        // the "this" object
	      asm.emitL   (T1, OBJECT_TIB_OFFSET, T1);                     // its tib
	      asm.emitCall(spSaveAreaOffset);                              // throw exception, if link error
	    }
	  }
	  // (2) Emit interface invocation sequence.
 	  if (VM.BuildForIMTInterfaceInvocation) {
	    int signatureId = VM_ClassLoader.
              findOrCreateInterfaceMethodSignatureId(methodRef.getName(), 
                                                     methodRef.getDescriptor());
            int offset      = VM_InterfaceMethodSignature.getOffset
                                                          (signatureId);
            genMoveParametersToRegisters(true, methodRef); // T0 is "this"
            asm.emitL   (S0, OBJECT_TIB_OFFSET, T0);       // its TIB
            if (VM.BuildForIndirectIMT) {
              // Load the IMT base into S0
              asm.emitL(S0, TIB_IMT_TIB_INDEX << 2, S0);
            }
            asm.emitL   (S0, offset, S0);                  // the method address
            asm.emitMTLR(S0);
	    //-#if RVM_WITH_SPECIALIZATION
	    asm.emitSpecializationCallWithHiddenParameter(spSaveAreaOffset, 
                                                          signatureId, method, 
                                                          bIP);
	    //-#else
            asm.emitCallWithHiddenParameter(spSaveAreaOffset, signatureId);
	    //-#endif
	  } else if (VM.BuildForITableInterfaceInvocation && 
		     VM.DirectlyIndexedITables && 
		     methodRef.getDeclaringClass().isResolved()) {
	    methodRef = methodRef.resolve();
	    VM_Class I = methodRef.getDeclaringClass();
	    genMoveParametersToRegisters(true, methodRef);        //T0 is "this"
	    asm.emitL   (S0, OBJECT_TIB_OFFSET, T0);  // its TIB
	    asm.emitL   (S0, TIB_ITABLES_TIB_INDEX << 2, S0); // iTables 
	    asm.emitL   (S0, I.getInterfaceId() << 2, S0);  // iTable
	    asm.emitL   (S0, I.getITableIndex(methodRef) << 2, S0); // the method to call
	    asm.emitMTLR(S0);
	    //-#if RVM_WITH_SPECIALIZATION
	    asm.emitSpecializationCall(spSaveAreaOffset, method, bIP);
	    //-#else
	    asm.emitCall(spSaveAreaOffset);
	    //-#endif
	  } else {
	    VM_Class I = methodRef.getDeclaringClass();
            int itableIndex = -1;
	    if (VM.BuildForITableInterfaceInvocation) {
	      // get the index of the method in the Itable
	      if (I.isLoaded()) {
		itableIndex = I.getITableIndex(methodRef);
	      }
	    }
            if (itableIndex == -1) {
              // itable index is not known at compile-time.
              // call "invokeInterface" to resolve object + method id into 
              // method address
              int methodRefId = klass.getMethodRefId(constantPoolIndex);
              asm.emitLtoc(T0, VM_Entrypoints.invokeInterfaceOffset);
              asm.emitMTLR(T0);
              asm.emitL   (T0, (count-1) << 2, SP); // object
              asm.emitLVAL(T1, methodRefId);        // method id
              asm.emitCall(spSaveAreaOffset);       // T0 := resolved method address
              asm.emitMTLR(T0);
              genMoveParametersToRegisters(true, methodRef);
              //-#if RVM_WITH_SPECIALIZATION
              asm.emitSpecializationCall(spSaveAreaOffset, method, bIP);
              //-#else
              asm.emitCall(spSaveAreaOffset);
              //-#endif
            } else {
              // itable index is known at compile-time.
              // call "findITable" to resolve object + interface id into 
              // itable address
              asm.emitLtoc(T0, VM_Entrypoints.findItableOffset);
              asm.emitMTLR(T0);
              asm.emitL   (T0, (count-1) << 2, SP);     // object
	      asm.emitL   (T0, OBJECT_TIB_OFFSET, T0);  // its TIB
              asm.emitLVAL(T1, I.getDictionaryId());    // interface id
              asm.emitCall(spSaveAreaOffset);   // T0 := itable reference
              asm.emitL   (T0, itableIndex << 2, T0); // T0 := the method to call
              asm.emitMTLR(T0);
              genMoveParametersToRegisters(true, methodRef);        //T0 is "this"
              //-#if RVM_WITH_SPECIALIZATION
              asm.emitSpecializationCall(spSaveAreaOffset, method, bIP);
              //-#else
              asm.emitCall(spSaveAreaOffset);
              //-#endif
            }
          }
          genPopParametersAndPushReturnValue(true, methodRef);
          break;
	}
        case 0xba: /* --- unused --- */ {
          if (VM.TraceAssembler) asm.noteBytecode("unused");
          if (VM.VerifyAssertions) VM.assert(NOT_REACHED);
          break;
	}
        case 0xbb: /* --- new --- */ {
          int constantPoolIndex = fetch2BytesUnsigned();
          VM_Type typeRef = klass.getTypeRef(constantPoolIndex);
          if (VM.TraceAssembler) asm.noteBytecode("new " + constantPoolIndex + " (" + typeRef + ")");
          if (typeRef.isInitialized()) { // call quick allocator
	    VM_Class newclass = (VM_Class)typeRef;
	    int instanceSize = newclass.getInstanceSize();
	    int tibOffset = newclass.getTibOffset();
	    asm.emitLtoc(T0, VM_Entrypoints.quickNewScalarOffset);
	    asm.emitMTLR(T0);
	    asm.emitLVAL(T0, instanceSize);
	    asm.emitLtoc(T1, tibOffset);
	    asm.emitLVAL(T2, newclass.hasFinalizer()?1:0);
	    asm.emitCall(spSaveAreaOffset);
	    asm.emitSTU (T0, -4, SP);
	  } else { // call regular allocator (someday backpatch?)
	    asm.emitLtoc(T0, VM_Entrypoints.newScalarOffset);
	    asm.emitMTLR(T0);
	    asm.emitLVAL(T0, klass.getTypeRefId(constantPoolIndex));
	    asm.emitCall(spSaveAreaOffset);
	    asm.emitSTU (T0, -4, SP);
	  }
          break;
	}
        case 0xbc: /* --- newarray --- */ {
          int atype = fetch1ByteSigned();
          VM_Array array = VM_Array.getPrimitiveArrayType(atype);
          array.resolve();
          array.instantiate();
	  if (VM.TraceAssembler)  asm.noteBytecode("newarray " + atype + "(" + array + ")");
	  int width      = array.getLogElementSize();
          int tibOffset  = array.getTibOffset();
          int headerSize = ARRAY_HEADER_SIZE;
          asm.emitLtoc (T0, VM_Entrypoints.quickNewArrayOffset);
          asm.emitMTLR (T0);
	  asm.emitL    (T0,  0, SP);                // T0 := number of elements
          asm.emitSLI  (T1, T0, width);             // T1 := number of bytes
	  asm.emitCAL  (T1, ARRAY_HEADER_SIZE, T1); //    += header bytes
          asm.emitLtoc (T2, tibOffset);             // T2 := tib
          asm.emitCall(spSaveAreaOffset);
          asm.emitST   (T0, 0, SP);
          break;
	}
        case 0xbd: /* --- anewarray --- */ {
          int constantPoolIndex = fetch2BytesUnsigned();
          VM_Type elementTypeRef = klass.getTypeRef(constantPoolIndex);
          VM_Array array = elementTypeRef.getArrayTypeForElementType();
	  // TODO!! Forcing early class loading may violate language spec.  FIX ME!!
	  array.load();
          array.resolve();
          array.instantiate();
          if (VM.TraceAssembler) asm.noteBytecode("anewarray new " + constantPoolIndex + " (" + array + ")");
	  int width      = array.getLogElementSize();
          int tibOffset = array.getTibOffset();
          int headerSize = ARRAY_HEADER_SIZE;
          asm.emitLtoc (T0, VM_Entrypoints.quickNewArrayOffset);
          asm.emitMTLR (T0);
	  asm.emitL    (T0,  0, SP);                // T0 := number of elements
          asm.emitSLI  (T1, T0, width);             // T1 := number of bytes
	  asm.emitCAL  (T1, ARRAY_HEADER_SIZE, T1); //    += header bytes
          asm.emitLtoc (T2, tibOffset);             // T2 := tib
          asm.emitCall(spSaveAreaOffset);
          asm.emitST   (T0, 0, SP);
          break;
	}
        case 0xbe: /* --- arraylength --- */ {
          if (VM.TraceAssembler) asm.noteBytecode("arraylength");
          asm.emitL (T0, 0, SP);
          asm.emitL (T1, ARRAY_LENGTH_OFFSET, T0);
	  asm.emitST(T1, 0, SP);
          break;
	}
        case 0xbf: /* --- athrow --- */ {
          if (VM.TraceAssembler) asm.noteBytecode("athrow");
          if (VM.UseEpilogueYieldPoints) genThreadSwitchTest(VM_Thread.EPILOGUE); 
          asm.emitLtoc(T0, VM_Entrypoints.athrowOffset);
          asm.emitMTLR(T0);
          asm.emitL   (T0, 0, SP);
          asm.emitCall(spSaveAreaOffset);
          break;
	}
        case 0xc0: /* --- checkcast --- */ {
          int constantPoolIndex = fetch2BytesUnsigned();
          VM_Type typeRef = klass.getTypeRef(constantPoolIndex);
          if (VM.TraceAssembler) asm.noteBytecode("checkcast " + constantPoolIndex + " (" + typeRef + ")");
          asm.emitLtoc(T0,  VM_Entrypoints.checkcastOffset);
          asm.emitMTLR(T0);
          asm.emitL   (T0,  0, SP); // checkcast(obj, klass) consumes obj
          asm.emitLVAL(T1, typeRef.getTibOffset());
          asm.emitCall(spSaveAreaOffset);               // but obj remains on stack afterwords
          break;
	}
        case 0xc1: /* --- instanceof --- */ {
          int constantPoolIndex = fetch2BytesUnsigned();
          VM_Type typeRef = klass.getTypeRef(constantPoolIndex);
          if (VM.TraceAssembler) asm.noteBytecode("instanceof " + constantPoolIndex + " (" + typeRef + ")");
          int offset = VM_Entrypoints.instanceOfOffset;
          if (typeRef.isClassType()) { 
	    VM_Class Class = typeRef.asClass();
	    if (Class.isLoaded() && Class.isFinal())
	      offset = VM_Entrypoints.instanceOfFinalOffset;
          } else if (typeRef.isArrayType() && typeRef.asArray().getElementType().isPrimitiveType())
	    offset = VM_Entrypoints.instanceOfFinalOffset;
          asm.emitLtoc(T0,  offset);            
          asm.emitMTLR(T0);
	  asm.emitL   (T0, 0, SP);
          asm.emitLVAL(T1, typeRef.getTibOffset());
          asm.emitCall(spSaveAreaOffset);
	  asm.emitST  (T0, 0, SP);
          break;
	}
        case 0xc2: /* --- monitorenter ---  */ {
          if (VM.TraceAssembler) asm.noteBytecode("monitorenter");
          asm.emitL   (T0, 0, SP);
	  // inline initial attempt to acquire the object's lock. see also: VM_Lock.lock()
	  // NB: If you change this, you must also update the version used by the opt compiler.
	  asm.emitCAL   (T1, OBJECT_STATUS_OFFSET, T0);  // T1 := &object.status
	  asm.emitLWARX (T2, 0, T1);                     // T2 := status, with reservation
	  asm.emitSRAIr ( 0, T2, OBJECT_THREAD_ID_SHIFT);// test: fatbit==0 && threadid==0
	  asm.emitBNE   (+5);                            // if false then branch to lock call
	  asm.emitOR    (T2, TI, T2);                    // T2 := (thread id | status)
	  asm.emitSTWCXr(T2, 0, T1);                     // *T1 := T2, with condition
	  asm.emitISYNC ();                              // assume success, kill contents of prefetch buffer -- see VM_Lock.lock()
	  asm.emitBEQ   (VM_Assembler.CALL_INSTRUCTIONS + 3); // if store succeeded then branch around lock call
          asm.emitL   (S0, VM_Entrypoints.lockOffset, JTOC);
          asm.emitMTLR(S0);
          asm.emitCall(spSaveAreaOffset);
	  asm.emitCAL (SP, 4, SP);
          break;
	}
        case 0xc3: /* --- monitorexit --- */ {
          if (VM.TraceAssembler) asm.noteBytecode("monitorexit");
          asm.emitL     (T0, 0, SP);
          // inline initial attempt to release the object's lock. see also: VM_Lock.unlock()
	  // NB: If you change this, you must also update the version used  by the opt compiler.
          asm.emitCAL   (T1, OBJECT_STATUS_OFFSET, T0);   // T1 := &object.status
          asm.emitLWARX (T2,  0, T1);                     // T2 := status, with reservation
          asm.emitXOR   (T2, T2, TI);                     // T2 := status ^ thread id
          asm.emitSRAIr ( 0, T2, OBJECT_LOCK_COUNT_SHIFT);// test: fatbit==0 && count==0 && lockid=me
          asm.emitBNE   (+4);                             // if not then branch to unlock call
          asm.emitSYNC  ();                               // assume success, synchronize memory updates -- see VM_Lock.unlock()
          asm.emitSTWCXr(T2, 0, T1);                      // *T1 := T2 (== unlocked state), with condition
          asm.emitBEQ   (VM_Assembler.CALL_INSTRUCTIONS + 3); // if store succeeded then branch around lock call
          asm.emitL   (S0, VM_Entrypoints.unlockOffset, JTOC);
          asm.emitMTLR(S0);
          asm.emitCall(spSaveAreaOffset);
          asm.emitCAL (SP, 4, SP);
          break;
	}
        case 0xc4: /* --- wide --- */ {
          if (VM.TraceAssembler) asm.noteBytecode("wide");
          int widecode = fetch1ByteUnsigned();
          int index = fetch2BytesUnsigned();
          switch (widecode) {
	    case 0x15: /* --- wide iload --- */ {
              if (VM.TraceAssembler) asm.noteBytecode("wide iload " + index);
              int offset = localOffset(index);
	      asm.emitL(T0, offset, FP);
	      asm.emitSTU(T0, -4, SP);
	      break;
	    }
	    case 0x16: /* --- wide lload --- */ {
              if (VM.TraceAssembler) asm.noteBytecode("wide lload " + index);
              int offset = localOffset(index) - 4;
	      asm.emitLFD  (F0, offset, FP);
	      asm.emitSTFDU(F0, -8, SP);
	      break;
	    }
	    case 0x17: /* --- wide fload --- */ {
              if (VM.TraceAssembler) asm.noteBytecode("wide fload " + index);
              int offset = localOffset(index);
	      asm.emitL(T0, offset, FP);
	      asm.emitSTU(T0, -4, SP);
	      break;
	    }
	    case 0x18: /* --- wide dload --- */ {
              if (VM.TraceAssembler) asm.noteBytecode("wide dload " + index);
              int offset = localOffset(index) - 4;
	      asm.emitLFD  (F0, offset, FP);
	      asm.emitSTFDU(F0, -8, SP);
	      break;
	    }
	    case 0x19: /* --- wide aload --- */ {
              if (VM.TraceAssembler) asm.noteBytecode("wide aload " + index);
              int offset = localOffset(index);
	      asm.emitL(T0, offset, FP);
	      asm.emitSTU(T0, -4, SP);
	      break;
	    }
	    case 0x36: /* --- wide istore --- */ {
              if (VM.TraceAssembler) asm.noteBytecode("wide istore " + index);
              asm.emitL(T0, 0, SP);
	      asm.emitCAL(SP, 4, SP);
	      int offset = localOffset(index);
	      asm.emitST(T0, offset, FP);
	      break;
	    }
	    case 0x37: /* --- wide lstore --- */ {
              if (VM.TraceAssembler) asm.noteBytecode("wide lstore " + index);
              asm.emitLFD(F0, 0, SP);
	      asm.emitCAL(SP, 8, SP);
	      int offset = localOffset(index)-4;
	      asm.emitSTFD(F0, offset, FP);
	      break;
	    }
	    case 0x38: /* --- wide fstore --- */ {
              if (VM.TraceAssembler) asm.noteBytecode("wide fstore " + index);
              asm.emitL(T0, 0, SP);
	      asm.emitCAL(SP, 4, SP);
	      int offset = localOffset(index);
	      asm.emitST(T0, offset, FP);
	      break;
	    }
	    case 0x39: /* --- wide dstore --- */ {
              if (VM.TraceAssembler) asm.noteBytecode("wide dstore " + index);
              asm.emitLFD(F0, 0, SP);
	      asm.emitCAL(SP, 8, SP);
	      int offset = localOffset(index)-4;
	      asm.emitSTFD(F0, offset, FP);
	      break;
	    }
	    case 0x3a: /* --- wide astore --- */ {
              if (VM.TraceAssembler) asm.noteBytecode("wide astore " + index);
              asm.emitL(T0, 0, SP);
	      asm.emitCAL(SP, 4, SP);
	      int offset = localOffset(index);
	      asm.emitST(T0, offset, FP);
	      break;
	    }
	    case 0x84: /* --- wide iinc --- */ {
              int val = fetch2BytesSigned();
              if (VM.TraceAssembler) asm.noteBytecode("wide inc " + index + " " + val);
              int offset = localOffset(index);
	      asm.emitL  (T0, offset, FP);
	      asm.emitCAL(T0, val, T0);
	      asm.emitST (T0, offset, FP);
	      break;
	    }
	    case 0x9a: /* --- wide ret --- */ {
              if (VM.TraceAssembler) asm.noteBytecode("wide ret " + index);
              int offset = localOffset(index);
	      asm.emitL(T0, offset, FP);
	      asm.emitMTLR(T0);
	      asm.emitBLR ();
	      break;
	    }
	    default:
	      if (VM.VerifyAssertions) VM.assert(NOT_REACHED);
          }
          break;
	}
        case 0xc5: /* --- multianewarray --- */ {
          int constantPoolIndex = fetch2BytesUnsigned();
          int dimensions = fetch1ByteUnsigned();
          VM_Type typeRef = klass.getTypeRef(constantPoolIndex);
          if (VM.TraceAssembler) asm.noteBytecode("multianewarray " + constantPoolIndex + " (" + typeRef + ") "  + dimensions);
          asm.emitLtoc(T0, VM_Entrypoints.newArrayArrayOffset);
          asm.emitMTLR(T0);
          asm.emitLVAL(T0, dimensions);
          asm.emitLVAL(T1, klass.getTypeRefId(constantPoolIndex));
          asm.emitSLI (T2, T0,  2); // number of bytes of array dimension args
          asm.emitA   (T2, SP, T2); // offset of word *above* first...
          asm.emitSF  (T2, FP, T2); // ...array dimension arg
          asm.emitCall(spSaveAreaOffset);
          asm.emitSTU (T0, (dimensions - 1)<<2, SP); // pop array dimension args, push return val
          break;
	}
        case 0xc6: /* --- ifnull --- */ {
          int offset = fetch2BytesSigned();
          if (VM.TraceAssembler) asm.noteBytecode("ifnull " + offset);
          if(offset <= 0) genThreadSwitchTest(VM_Thread.BACKEDGE);
	  asm.emitL   (T0,  0, SP);
	  asm.emitLIL (T1,  0);
	  asm.emitCMP (T0, T1);  
          asm.emitCAL (SP,  4, SP);
	  int bTarget = bIP + offset;
	  int mTarget;
	  if (offset <= 0) {
	    mTarget = asm.relativeMachineAddress(bTarget);
	  } else {
	    mTarget = 0;
	    asm.reserveForwardConditionalBranch(bTarget);
	  }
	  asm.emitBEQ(mTarget);
	  break;
	}
        case 0xc7: /* --- ifnonnull --- */ {
          int offset = fetch2BytesSigned();
          if (VM.TraceAssembler) asm.noteBytecode("ifnonnull " + offset);
          if(offset <= 0) genThreadSwitchTest(VM_Thread.BACKEDGE);
	  asm.emitL   (T0,  0, SP);
	  asm.emitLIL (T1,  0);
	  asm.emitCMP (T0, T1);  
          asm.emitCAL (SP,  4, SP);
	  int bTarget = bIP + offset;
	  int mTarget;
	  if (offset <= 0) {
	    mTarget = asm.relativeMachineAddress(bTarget);
	  } else {
	    mTarget = 0;
	    asm.reserveForwardConditionalBranch(bTarget);
	  }
	  asm.emitBNE(mTarget);
	  break;
	}
        case 0xc8: /* --- goto_w --- */ {
          int offset = fetch4BytesSigned();
          if (VM.TraceAssembler) asm.noteBytecode("goto_w " + offset);
          if(offset <= 0) genThreadSwitchTest(VM_Thread.BACKEDGE);
          int bTarget = bIP + offset;
          int mTarget;
          if (offset <= 0) {
            mTarget = asm.relativeMachineAddress(bTarget);
	  } else {
            mTarget = 0;
            asm.reserveForwardBranch(bTarget);
	  }
          if (VM.TraceAssembler) asm.comment(">>> bi " + bTarget);
          asm.emitB(mTarget);
          break;
	}
        case 0xc9: /* --- jsr_w --- */ {
          int offset = fetch4BytesSigned();
          if (VM.TraceAssembler) asm.noteBytecode("jsr_w " + offset);
          asm.emitBL  ( 1);
          asm.emitMFLR(T1);           // LR +  0
	  asm.emitCAL (T1, 16, T1);   // LR +  4  (LR + 16 is ret address)
	  asm.emitSTU (T1, -4, SP);   // LR +  8
          int bTarget = bIP + offset;
          int mTarget;
          if (offset <= 0) {
            mTarget = asm.relativeMachineAddress(bTarget);
	  } else {
            mTarget = 0;
            asm.reserveForwardBranch(bTarget);
	  }
          if (VM.TraceAssembler) asm.comment(">-> bi " + bTarget);
          asm.emitBL(mTarget);        // LR + 12
          break;
	}
        default:
          VM.sysWrite("code generator: unexpected bytecode: " + VM.intAsHexString(code) + "\n");
          if (VM.VerifyAssertions) VM.assert(NOT_REACHED);
      }
    }
    asm.emitDATA(compiledMethodId); // put method signature at end of machine code
    if (VM.TraceCompilation) VM.sysWrite("VM_Compiler: end compiling " + meth + "\n");
    return asm.makeMachineCode();
  }
  
  // Emit code to buy a stackframe, store incoming parameters, 
  // and acquire method synchronization lock.
  //
  private void genPrologue () {
    if (VM.TraceAssembler) asm.comment("prologue");

    // Generate trap if new frame would cross guard page.
    //
    if (klass.isInterruptible()) {
      asm.emitStackOverflowCheck(frameSize);                            // clobbers R0, S0
    } else {
      // TODO!! make sure stackframe of uninterruptible method doesn't overflow guard page
      // asm.emitUninterruptibleStackOverflowCheck(frameSize - STACK_SIZE_GUARD);
    }

    // Buy frame.
    //
    asm.emitSTU (FP, -frameSize, FP); // save old FP & buy new frame (trap if new frame below guard page) !!TODO: handle frames larger than 32k when addressing local variables, etc.
    
    // If this is a "dynamic bridge" method, then save all registers except GPR0, FPR0, JTOC, and FP.
    //
    if (klass.isDynamicBridge()) {
      int offset = frameSize;
      for (int i = LAST_NONVOLATILE_FPR; i >= FIRST_VOLATILE_FPR; --i)
         asm.emitSTFD (i, offset -= 8, FP);
      for (int i = LAST_NONVOLATILE_GPR; i >= FIRST_VOLATILE_GPR; --i)
         asm.emitST (i, offset -= 4, FP);
    }
    
    // Fill in frame header.
    //
    asm.emitLVAL(S0, compiledMethodId);
    asm.emitMFLR(0);
    asm.emitST  (S0, STACKFRAME_METHOD_ID_OFFSET, FP);                   // save compiled method id
    asm.emitST  (0, frameSize + STACKFRAME_NEXT_INSTRUCTION_OFFSET, FP); // save LR !!TODO: handle discontiguous stacks when saving return address
    
    // Setup expression stack and locals.
    //
    asm.emitCAL (SP, emptyStackOffset, FP);                              // setup expression stack
    genMoveParametersToLocals();                                                   // move parameters to locals
   
    // Perform a thread switch if so requested.
    //
    genThreadSwitchTest(VM_Thread.PROLOGUE); //           (VM_BaselineExceptionDeliverer WONT release the lock (for synchronized methods) during prologue code)

    // Acquire method syncronization lock.  (VM_BaselineExceptionDeliverer will release the lock (for synchronized methods) after  prologue code)
    //
    if (method.isSynchronized()) 
      genSynchronizedMethodPrologue();

    // Mark start of code for which source lines exist (for jdp debugger breakpointing).
    //
    asm.emitSENTINAL(); 
  }

  int lockOffset; // instruction that acquires the monitor for a synchronized method

  // Emit code to acquire method synchronization lock.
  //
  private void genSynchronizedMethodPrologue() {
    if (method.isStatic()) { // put java.lang.Class object for VM_Type into T0
      klass.getClassForType(); // force java.lang.Class to get loaded into "klass.classForType"
      int tibOffset = klass.getTibOffset();
      asm.emitLtoc(T0, tibOffset);
      asm.emitL   (T0, 0, T0);
      asm.emitL   (T0, VM_Entrypoints.classForTypeOffset, T0); 
    } else { // first local is "this" pointer
      asm.emitL(T0, localOffset(0), FP);
    }
    // inline initial attempt to acquire the object's lock. see also: VM_Lock.lock()
    asm.emitCAL   (T1, OBJECT_STATUS_OFFSET, T0);  // T1 := &object.status
    asm.emitLWARX (T2, 0, T1);                     // T2 := status, with reservation
    asm.emitSRAIr ( 0, T2, OBJECT_THREAD_ID_SHIFT);// test fatbit==0 && threadid==0
    asm.emitBNE   (+5);                            // if false then branch to lock call
    asm.emitOR    (T2, TI, T2);                    // T2 := (thread id | status)
    asm.emitSTWCXr(T2, 0, T1);                     // *T1 := T2, with condition
    asm.emitISYNC ();                              // assume success, kill contents of prefetch buffer -- see VM_Lock.lock()
    asm.emitBEQ   (VM_Assembler.CALL_INSTRUCTIONS + 3); // if store succeeded then branch around lock call
    asm.emitL     (S0, VM_Entrypoints.lockOffset, JTOC); // call out...
    asm.emitMTLR  (S0);                                  // ...of line lock
    asm.emitCall(spSaveAreaOffset);
    lockOffset = 4*(asm.currentInstructionOffset() - 1); // after this instruction, the method has the monitor
  }

  // Emit code to release method synchronization lock.
  //
  private void genSynchronizedMethodEpilogue () {
    if (method.isStatic()) { // put java.lang.Class for VM_Type into T0
      int tibOffset = klass.getTibOffset();
      asm.emitLtoc(T0, tibOffset);
      asm.emitL   (T0, 0, T0);
      asm.emitL   (T0, VM_Entrypoints.classForTypeOffset, T0); 
    } else { // first local is "this" pointer
      asm.emitL(T0, localOffset(0), FP); //!!TODO: think about this - can anybody store into local 0 (ie. change the value of "this")?
    }
    // inline initial attempt to release the object's lock.  see also: VM_Lock.unlock()
    asm.emitCAL   (T1, OBJECT_STATUS_OFFSET, T0);       // T1 := &object.status
    asm.emitLWARX (T2,  0, T1);                         // T2 := status, with reservation
    asm.emitXOR   (T2, T2, TI);                         // T2 := status ^ thread id
    asm.emitSRAIr ( 0, T2, OBJECT_LOCK_COUNT_SHIFT);    // test: fatbit==0 && count==0 && lockid=me
    asm.emitBNE   (+4);                                 // if not then branch to unlock call
    asm.emitSYNC  ();                                   // assume success, synchronize memory updates -- see VM_Lock.unlock()
    asm.emitSTWCXr(T2, 0, T1);                          // *T1 := T2 (== unlocked state), with condition
    asm.emitBEQ   (VM_Assembler.CALL_INSTRUCTIONS + 3); // if store succeeded then branch around lock call
    asm.emitL   (S0, VM_Entrypoints.unlockOffset, JTOC);  // call out...
    asm.emitMTLR(S0);                                     // ...of line lock
    asm.emitCall(spSaveAreaOffset);
  }
    
  // Emit code to discard stackframe and return to caller.
  //
  private void genEpilogue () {
    if (klass.isDynamicBridge()) {// Restore non-volatile registers.
      int offset = frameSize;
      for (int i = LAST_NONVOLATILE_FPR; i >= FIRST_NONVOLATILE_FPR; --i) // restore non-volatile fprs
         asm.emitLFD(i, offset -= 8, FP);
      offset -= (FIRST_NONVOLATILE_FPR - FIRST_VOLATILE_FPR) * 8;         //  skip       volatile fprs
      for (int i = LAST_NONVOLATILE_GPR; i >= FIRST_NONVOLATILE_GPR; --i) // restore non-volatile gprs
         asm.emitL  (i, offset -= 4, FP);
    }
    if (frameSize <= 0x8000) {
      asm.emitCAL(FP, frameSize, FP); // discard current frame
    } else {
      asm.emitL(FP, 0, FP);           // discard current frame
    }
    asm.emitL   (S0, STACKFRAME_NEXT_INSTRUCTION_OFFSET, FP); 
    asm.emitMTLR(S0);
    asm.emitBLR (); // branch always, through link register
  }


  /**
   * @param whereFrom is this thread switch from a PROLOGUE, BACKEDGE, or
   *       EPILOGUE?
   */
  private void genThreadSwitchTest (int whereFrom) {
    if (klass.isInterruptible()) {
      // alternate ways of setting the thread switch bit
      if (VM.BuildForDeterministicThreadSwitching) { // set THREAD_SWITCH_BIT every N method calls
	
	// Decrement counter
	asm.emitL  (T2, VM_Entrypoints.deterministicThreadSwitchCountOffset, PROCESSOR_REGISTER);
	asm.emitCAL(T2, -1, T2);  // decrement it
	asm.emitST (T2, VM_Entrypoints.deterministicThreadSwitchCountOffset, PROCESSOR_REGISTER);
        
	// If counter reaches zero, set threadswitch bit
	asm.emitCMPI(T2, 0);
	asm.emitBGT(VM_Assembler.CALL_INSTRUCTIONS + 5);
	asm.emitCRORC(THREAD_SWITCH_BIT, 0, 0); // set thread switch bit
      } else if (!VM.BuildForThreadSwitchUsingControlRegisterBit) {
	asm.emitL(S0, VM_Entrypoints.threadSwitchRequestedOffset, PROCESSOR_REGISTER);
	asm.emitCMPI(THREAD_SWITCH_REGISTER, S0, 0); // set THREAD_SWITCH_BIT in CR
      } // else rely on the timer interrupt to set the THREAD_SWITCH_BIT
      asm.emitBNTS(VM_Assembler.CALL_INSTRUCTIONS + 3); // skip, unless THREAD_SWITCH_BIT in CR is set
      if (whereFrom == VM_Thread.PROLOGUE) {
	asm.emitL   (S0, VM_Entrypoints.threadSwitchFromPrologueOffset, JTOC);
      } else if (whereFrom == VM_Thread.BACKEDGE) {
	asm.emitL   (S0, VM_Entrypoints.threadSwitchFromBackedgeOffset, JTOC);
      } else { // EPILOGUE
	asm.emitL   (S0, VM_Entrypoints.threadSwitchFromEpilogueOffset, JTOC);
      }
      asm.emitMTLR(S0);
      asm.emitCall(spSaveAreaOffset);
    }
  }

  // parameter stuff //

  // store parameters from registers into local variables of current method.
  private void genMoveParametersToLocals () {
    // AIX computation will differ
    spillOffset = getFrameSize(method) + STACKFRAME_HEADER_SIZE;
    int gp = FIRST_VOLATILE_GPR;
    int fp = FIRST_VOLATILE_FPR;
    int localIndex = 0;
    if (!method.isStatic()) {
      if (gp > LAST_VOLATILE_GPR) genUnspillWord(localIndex++);
      else asm.emitST(gp++, localOffset(localIndex++), FP);
    }
    VM_Type [] types = method.getParameterTypes();
    for (int i=0; i<types.length; i++, localIndex++) {
      VM_Type t = types[i];
      if (t.isLongType()) {
        if (gp > LAST_VOLATILE_GPR) genUnspillDoubleword(localIndex++);
	else {
	  asm.emitST(gp++, localOffset(localIndex + 1), FP); // lo mem := lo register (== hi word)
	  if (gp > LAST_VOLATILE_GPR) genUnspillWord(localIndex);
	  else asm.emitST(gp++, localOffset(localIndex), FP);// hi mem := hi register (== lo word)
	  localIndex += 1;
	}
      } else if (t.isFloatType()) {
        if (fp > LAST_VOLATILE_FPR) genUnspillWord(localIndex);
	else asm.emitSTFS(fp++, localOffset(localIndex), FP);
      } else if (t.isDoubleType()) {
        if (fp > LAST_VOLATILE_FPR) genUnspillDoubleword(localIndex++);
	else asm.emitSTFD(fp++, localOffset(localIndex++) - 4, FP);
      } else { // t is object, int, short, char, byte, or boolean
        if (gp > LAST_VOLATILE_GPR) genUnspillWord(localIndex);
	else asm.emitST(gp++, localOffset(localIndex), FP);
      }
    }
  }

  // load parameters into registers before calling method "m".
  private void genMoveParametersToRegisters (boolean hasImplicitThisArg, VM_Method m) {
    // AIX computation will differ
    spillOffset = STACKFRAME_HEADER_SIZE;
    int gp = FIRST_VOLATILE_GPR;
    int fp = FIRST_VOLATILE_FPR;
    int stackOffset = m.getParameterWords()<<2;
    if (hasImplicitThisArg) {
      if (gp > LAST_VOLATILE_GPR) genSpillWord(stackOffset);
      else asm.emitL(gp++, stackOffset, SP);
    }
    VM_Type [] types = m.getParameterTypes();
    for (int i=0; i<types.length; i++) {
      VM_Type t = types[i];
      if (t.isLongType()) {
	stackOffset -= 8;
        if (gp > LAST_VOLATILE_GPR) genSpillDoubleword(stackOffset);
	else {
	  asm.emitL(gp++, stackOffset,   SP);       // lo register := lo mem (== hi order word)
	  if (gp > LAST_VOLATILE_GPR) genSpillWord(stackOffset+4);
	  else asm.emitL(gp++, stackOffset+4, SP);  // hi register := hi mem (== lo order word)
	}
      } else if (t.isFloatType()) {
	stackOffset -= 4;
        if (fp > LAST_VOLATILE_FPR) genSpillWord(stackOffset);
	else asm.emitLFS(fp++, stackOffset, SP);
      } else if (t.isDoubleType()) {
	stackOffset -= 8;
        if (fp > LAST_VOLATILE_FPR) genSpillDoubleword(stackOffset);
	else asm.emitLFD(fp++, stackOffset, SP);
      } else { // t is object, int, short, char, byte, or boolean
	stackOffset -= 4;
        if (gp > LAST_VOLATILE_GPR) genSpillWord(stackOffset);
	else asm.emitL(gp++, stackOffset, SP);
      }
    }
    if (VM.VerifyAssertions) VM.assert(stackOffset == 0);
  }

  // push return value of method "m" from register to operand stack.
  private void genPopParametersAndPushReturnValue (boolean hasImplicitThisArg, VM_Method m) {
    VM_Type t = m.getReturnType();
    int parameterSize = 
      (m.getParameterWords() + (hasImplicitThisArg ? 1 : 0) ) << 2;
    if (t.isVoidType()) {
      if (0 < parameterSize) asm.emitCAL(SP, parameterSize, SP);
    } else if (t.isLongType()) {
      asm.emitST (FIRST_VOLATILE_GPR+1, parameterSize-4, SP); // hi mem := hi register (== lo word)
      asm.emitSTU(FIRST_VOLATILE_GPR,   parameterSize-8, SP); // lo mem := lo register (== hi word)
    } else if (t.isFloatType()) {
      asm.emitSTFSU(FIRST_VOLATILE_FPR, parameterSize-4, SP);
    } else if (t.isDoubleType()) {
      asm.emitSTFDU(FIRST_VOLATILE_FPR, parameterSize-8, SP);
    } else { // t is object, int, short, char, byte, or boolean
      asm.emitSTU(FIRST_VOLATILE_GPR, parameterSize-4, SP);
    }
  }

  private void genSpillWord (int stackOffset) {
     asm.emitL (0, stackOffset, SP);
     asm.emitST(0, spillOffset, FP);
     spillOffset += 4;
  }
     
  private void genSpillDoubleword (int stackOffset) {
     asm.emitLFD (0, stackOffset, SP);
     asm.emitSTFD(0, spillOffset, FP);
     spillOffset += 8;
  }
               
  private void genUnspillWord (int localIndex) {
     asm.emitL (0, spillOffset, FP);
     asm.emitST(0, localOffset(localIndex), FP);
     spillOffset += 4;
  }
                      
  private void genUnspillDoubleword (int localIndex) {
     asm.emitLFD (0, spillOffset, FP);
     asm.emitSTFD(0, localOffset(localIndex) - 4, FP);
     spillOffset += 8;
  }
   
  private VM_Method    method;
  /*private*/ VM_Class     klass;       //!!TODO: make private once VM_MagicCompiler is merged in
  /*private*/ VM_Assembler asm;         //!!TODO: make private once VM_MagicCompiler is merged in
  private int          compiledMethodId;
 
  // stackframe pseudo-constants //
  
  /*private*/ int frameSize;            //!!TODO: make private once VM_MagicCompiler is merged in
  /*private*/ int spSaveAreaOffset;     //!!TODO: make private once VM_MagicCompiler is merged in
  private int emptyStackOffset;
  private int firstLocalOffset;
  private int spillOffset;

  // bytecode input //
  
  private byte[] bcodes;       // indexed by bindex or bIP
  private int    bindex;       // next byte to be read
  private int    bIP;          // current bytecode instruction
  private int    bcodeLen;


  //--------------//
  // Static data. //
  //--------------//
  
  // runtime support  

  private static boolean               alreadyInitialized;
  private static VM_ExceptionDeliverer exceptionDeliverer;
  
  // Initialization, called for one or both of the following purposes:
  // - to initialize compiler's static fields as they will appear in the boot image
  // - to initialize compiler for use in building boot image
  //
  static void init() {
    if (alreadyInitialized) return;
    alreadyInitialized = true;
  
    // create handler for delivering exceptions to, or unwinding stackframes of,
    // methods generated by this compiler
    //
    exceptionDeliverer = new VM_BaselineExceptionDeliverer();

    // initialize the JNI environment
    VM_JNIEnvironment.init();
  }

}

