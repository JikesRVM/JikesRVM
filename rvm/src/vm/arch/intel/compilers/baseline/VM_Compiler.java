/*
 * (C) Copyright IBM Corp. 2001
 */
//$Id$

/**
 * 
 * @author Bowen Alpern
 * @author Maria Butrico
 * @author Anthony Cocchi
 */
public class VM_Compiler implements VM_BaselineConstants {

  //-----------//
  // interface //
  //-----------//
  
  static VM_CompiledMethod compile (VM_Method method) {
    int compiledMethodId = VM_ClassLoader.createCompiledMethodId();
    if (method.isNative()) {
      VM_MachineCode machineCode = VM_JNICompiler.generateGlueCodeForNative(compiledMethodId, method);
      VM_CompilerInfo info = new VM_JNICompilerInfo(method);
      return new VM_CompiledMethod(compiledMethodId, method, machineCode.getInstructions(), info);
    }
    if (!VM.BuildForInterpreter) {
      VM_Compiler     compiler     = new VM_Compiler();
      VM_MachineCode  machineCode  = compiler.genCode(compiledMethodId, method);
      INSTRUCTION[]   instructions = machineCode.getInstructions();
      int[]           bytecodeMap  = machineCode.getBytecodeMap();
      VM_CompilerInfo info;
      if (method.isSynchronized()) {
        info = new VM_BaselineCompilerInfo(method, bytecodeMap, instructions.length, compiler.lockOffset);
      } else {
        info = new VM_BaselineCompilerInfo(method, bytecodeMap, instructions.length);
      }
      VM_Assembler.TRACE = false;
      return new VM_CompiledMethod(compiledMethodId, method, instructions, info);
    } else {
      return new VM_CompiledMethod(compiledMethodId, method, null, null);
    }
  }

  // The last true local
  //
  static int getEmptyStackOffset (VM_Method m) {
    return getFirstLocalOffset(m) - (m.getLocalWords()<<LG_WORDSIZE) + WORDSIZE;
  }

  // This is misnamed.  It should be getFirstParameterOffset.
  // It will not work as a base to access true locals.
  // TODO!! make sure it is not being used incorrectly
  //
  static int getFirstLocalOffset (VM_Method method) {
    if (method.getDeclaringClass().isBridgeFromNative())
      return STACKFRAME_BODY_OFFSET - (VM_JNICompiler.SAVED_GPRS_FOR_JNI << LG_WORDSIZE);
    else
      return STACKFRAME_BODY_OFFSET - (SAVED_GPRS << LG_WORDSIZE);
  }
  
  final int getBytecodeIndex () {
    return biStart;
  }
  
  final int[] getBytecodeMap () {
    return bytecodeMap;
  }

  static VM_ExceptionDeliverer getExceptionDeliverer () {
     return exceptionDeliverer;
  }

  //----------------//
  // implementation //
  //----------------//
  
  private final VM_MachineCode genCode (int compiledMethodId, VM_Method method) {
    /* initialization */ {
      // TODO!! check register ranges TODO!!
      this.method          = method;
      klass                = method.getDeclaringClass();
      bytecodes            = method.getBytecodes();
      bytecodeLength       = bytecodes.length;
      bytecodeMap          = new int [bytecodeLength];
      if (klass.isBridgeFromNative())
	// JNIFunctions need space for bigger prolog & epilog
	asm                  = new VM_Assembler(bytecodeLength+10);
      else
	asm                  = new VM_Assembler(bytecodeLength);
      profilerClass        = null; // TODO!! set this correctly
      parameterWords       = method.getParameterWords();
      parameterWords      += (method.isStatic() ? 0 : 1); // add 1 for this pointer
      //      if (VM.VerifyAssertions) VM.assert(parameters == 0); // TODO!!
    }
    VM_Assembler asm = this.asm; // premature optimization
    if (klass.isBridgeFromNative()) {
      // replace the normal prologue with a special prolog
      VM_JNICompiler.generateGlueCodeForJNIMethod (asm, method, compiledMethodId);
      // set some constants for the code generation of the rest of the method
      // firstLocalOffset is shifted down because more registers are saved
      firstLocalOffset = STACKFRAME_BODY_OFFSET - (VM_JNICompiler.SAVED_GPRS_FOR_JNI<<LG_WORDSIZE) ;

    } else {
      genPrologue(compiledMethodId);
    }
    for (bi=0; bi<bytecodeLength;) { 
      bytecodeMap[bi] = asm.getMachineCodeIndex();
      asm.resolveForwardReferences(bi);
      biStart = bi;
      int code = fetch1ByteUnsigned();
      // if (VM.runningVM) VM.sysWrite("\n at: " + VM_Lister.decimal(biStart) + " = " + VM_Lister.hex((byte) code) + "\n");
      switch (code) {
      case 0x00: /* nop */ {
	if (VM_Assembler.TRACE) asm.noteBytecode(biStart, "nop");
	break;
      }
      case 0x01: /* aconst_null */ {
	if (VM_Assembler.TRACE) asm.noteBytecode(biStart, "aconst_null ");
	asm.emitPUSH_Imm(0);
	break;
      }
      case 0x02: /* iconst_m1 */ {
	if (VM_Assembler.TRACE) asm.noteBytecode(biStart, "iconst_m1 ");
	asm.emitPUSH_Imm(-1);
	break;
      }
      case 0x03: /* iconst_0 */ {
	if (VM_Assembler.TRACE) asm.noteBytecode(biStart, "iconst_0 ");
	asm.emitPUSH_Imm(0);
	break;
      }
      case 0x04: /* iconst_1 */ {
	if (VM_Assembler.TRACE) asm.noteBytecode(biStart, "iconst_1 ");
	asm.emitPUSH_Imm(1);
	break;
      }
      case 0x05: /* iconst_2 */ {
	if (VM_Assembler.TRACE) asm.noteBytecode(biStart, "iconst_2 ");
	asm.emitPUSH_Imm(2);
	break;
      }
      case 0x06: /* iconst_3 */ {
	if (VM_Assembler.TRACE) asm.noteBytecode(biStart, "iconst_3 ");
	asm.emitPUSH_Imm(3);
	break;
      }
      case 0x07: /* iconst_4 */ {
	if (VM_Assembler.TRACE) asm.noteBytecode(biStart, "iconst_4 ");
	asm.emitPUSH_Imm(4);
	break;
      }
      case 0x08: /* iconst_5 */ {
	if (VM_Assembler.TRACE) asm.noteBytecode(biStart, "iconst_5 ");
	asm.emitPUSH_Imm(5);
	break;
      }
      case 0x09: /* lconst_0 */ {
	if (VM_Assembler.TRACE) asm.noteBytecode(biStart, "lconst_0 ");  // floating-point 0 is long 0
	asm.emitPUSH_Imm(0);
	asm.emitPUSH_Imm(0);
	break;
      }
      case 0x0a: /* lconst_1 */ {
	if (VM_Assembler.TRACE) asm.noteBytecode(biStart, "lconst_1 ");
	asm.emitPUSH_Imm(0);  // high part
	asm.emitPUSH_Imm(1);  //  low part
	break;
      }
      case 0x0b: /* fconst_0 */ {
	if (VM_Assembler.TRACE) asm.noteBytecode(biStart, "fconst_0");
	asm.emitPUSH_Imm(0);
	break;
      }
      case 0x0c: /* fconst_1 */ {
	if (VM_Assembler.TRACE) asm.noteBytecode(biStart, "fconst_1");
	asm.emitPUSH_Imm(0x3f800000);
	break;
      }
      case 0x0d: /* fconst_2 */ {
	if (VM_Assembler.TRACE) asm.noteBytecode(biStart, "fconst_2");
	asm.emitPUSH_Imm(0x40000000);
	break;
      }
      case 0x0e: /* dconst_0 */ {
	if (VM_Assembler.TRACE) asm.noteBytecode(biStart, "dconst_0");
	asm.emitPUSH_Imm(0x00000000);
	asm.emitPUSH_Imm(0x00000000);
	break;
      }
      case 0x0f: /* dconst_1 */ {
	if (VM_Assembler.TRACE) asm.noteBytecode(biStart, "dconst_1");
	asm.emitPUSH_Imm(0x3ff00000);
	asm.emitPUSH_Imm(0x00000000);
	break;
      }
      case 0x10: /* bipush */ {
	int val = fetch1ByteSigned();
	if (VM_Assembler.TRACE) asm.noteBytecode(biStart, "bipush " + VM_Lister.decimal(val));
	asm.emitPUSH_Imm(val);
	break;
      }
      case 0x11: /* sipush */ {
	int val = fetch2BytesSigned();
	if (VM_Assembler.TRACE) asm.noteBytecode(biStart, "sipush " + VM_Lister.decimal(val));
	asm.emitPUSH_Imm(val);
	break;
      }
      case 0x12: /* ldc */ {
	int index = fetch1ByteUnsigned();
	int offset = klass.getLiteralOffset(index);
	if (VM_Assembler.TRACE) asm.noteBytecode(biStart, "ldc " + VM_Lister.decimal(index));
	asm.emitPUSH_RegDisp(JTOC, offset);   
	break;
      }
      case 0x13: /* ldc_w */ {
	int index = fetch2BytesUnsigned();
	if (VM_Assembler.TRACE) asm.noteBytecode(biStart, "ldc_w " + VM_Lister.decimal(index));
	int offset = klass.getLiteralOffset(index);
	asm.emitPUSH_RegDisp(JTOC, offset);   
	break;
      }
      case 0x14: /* ldc2_w */ {
	int index = fetch2BytesUnsigned();
	if (VM_Assembler.TRACE) asm.noteBytecode(biStart, "ldc2_w " + VM_Lister.decimal(index));
	int offset = klass.getLiteralOffset(index);
	asm.emitPUSH_RegDisp(JTOC, offset+4); // high part of the long
	asm.emitPUSH_RegDisp(JTOC, offset);   // low part of the long
	break;
      }
      case 0x15: /* iload */ {
	int index = fetch1ByteUnsigned();
	if (VM_Assembler.TRACE) asm.noteBytecode(biStart, "iload " + VM_Lister.decimal(index));
	int offset = localOffset(index);
	asm.emitPUSH_RegDisp(FP,offset);
	break;
      }
      case 0x16: /* lload */ {
	int index = fetch1ByteUnsigned();
	if (VM_Assembler.TRACE) asm.noteBytecode(biStart, "lload " + VM_Lister.decimal(index));
	int offset = localOffset(index);
	asm.emitPUSH_RegDisp(FP, offset); // high part
	offset = localOffset(index+1);
	asm.emitPUSH_RegDisp(FP, offset); //  low part
	break;
      }
      case 0x17: /* fload */ {
	int index = fetch1ByteUnsigned();
	if (VM_Assembler.TRACE) asm.noteBytecode(biStart, "fload " + VM_Lister.decimal(index));
	int offset = localOffset(index);
	asm.emitPUSH_RegDisp (FP, offset);
	break;
      }
      case 0x18: /* dload */ {
	int index = fetch1ByteUnsigned();
	if (VM_Assembler.TRACE) asm.noteBytecode(biStart, "dload " + VM_Lister.decimal(index));
	int offset = localOffset(index);
	asm.emitPUSH_RegDisp(FP, offset); // high part
	offset = localOffset(index+1);
	asm.emitPUSH_RegDisp(FP, offset); //  low part
	break;
      }
      case 0x19: /* aload */ {
	int index = fetch1ByteUnsigned();
	if (VM_Assembler.TRACE) asm.noteBytecode(biStart, "aload " + VM_Lister.decimal(index));
	int offset = localOffset(index);
	asm.emitPUSH_RegDisp(FP, offset);
	break;
      }
      case 0x1a: /* iload_0 */ {
	if (VM_Assembler.TRACE) asm.noteBytecode(biStart, "iload_0");
	int offset = localOffset(0);
	asm.emitPUSH_RegDisp ( FP, offset);
	break;
      }
      case 0x1b: /* iload_1 */ {
	if (VM_Assembler.TRACE) asm.noteBytecode(biStart, "iload_1");
	int offset = localOffset(1);
	asm.emitPUSH_RegDisp ( FP, offset);
	break;
      }
      case 0x1c: /* iload_2 */ {
	if (VM_Assembler.TRACE) asm.noteBytecode(biStart, "iload_2");
	int offset = localOffset(2);
	asm.emitPUSH_RegDisp (FP, offset);
	break;
      }
      case 0x1d: /* iload_3 */ {
	if (VM_Assembler.TRACE) asm.noteBytecode(biStart, "iload_3");
	int offset = localOffset(3);
	asm.emitPUSH_RegDisp ( FP, offset);
	break;
      }
      case 0x1e: /* lload_0 */ {
	if (VM_Assembler.TRACE) asm.noteBytecode(biStart, "lload_0");
	int offset = localOffset(0);
	asm.emitPUSH_RegDisp(FP, offset); // high part
	offset = localOffset(1);
	asm.emitPUSH_RegDisp(FP, offset); //  low part
	break;
      }
      case 0x1f: /* lload_1 */ {
	if (VM_Assembler.TRACE) asm.noteBytecode(biStart, "lload_1");
	int offset = localOffset(1);
	asm.emitPUSH_RegDisp(FP, offset); // high part
	offset = localOffset(2);
	asm.emitPUSH_RegDisp(FP, offset); //  low part
	break;
      }
      case 0x20: /* lload_2 */ {
	if (VM_Assembler.TRACE) asm.noteBytecode(biStart, "lload_2");
	int offset = localOffset(2);
	asm.emitPUSH_RegDisp(FP, offset); // high part
	offset = localOffset(3);
	asm.emitPUSH_RegDisp(FP, offset); //  low part
	break;
      }
      case 0x21: /* lload_3 */ {
	if (VM_Assembler.TRACE) asm.noteBytecode(biStart, "lload_3");
	int offset = localOffset(3);
	asm.emitPUSH_RegDisp(FP, offset); // high part
	offset = localOffset(4);
	asm.emitPUSH_RegDisp(FP, offset); //  low part
	break;
      }
      case 0x22: /* fload_0 */ {
	if (VM_Assembler.TRACE) asm.noteBytecode(biStart, "fload_0");
	int offset = localOffset(0);
	asm.emitPUSH_RegDisp ( FP, offset);
	break;
      }
      case 0x23: /* fload_1 */ {
	if (VM_Assembler.TRACE) asm.noteBytecode(biStart, "fload_1");
	int offset = localOffset(1);
	asm.emitPUSH_RegDisp ( FP, offset);
	break;
      }
      case 0x24: /* fload_2 */ {
	if (VM_Assembler.TRACE) asm.noteBytecode(biStart, "fload_2");
	int offset = localOffset(2);
	asm.emitPUSH_RegDisp ( FP, offset);
	break;
      }
      case 0x25: /* fload_3 */ {
	if (VM_Assembler.TRACE) asm.noteBytecode(biStart, "fload_3");
	int offset = localOffset(3);
	asm.emitPUSH_RegDisp ( FP, offset);
	break;
      }
      case 0x26: /* dload_0 */ {
	if (VM_Assembler.TRACE) asm.noteBytecode(biStart, "dload_0");
	int offset = localOffset(0);
	asm.emitPUSH_RegDisp(FP, offset); // high part
	offset = localOffset(1);
	asm.emitPUSH_RegDisp(FP, offset); //  low part
	break;
      }
      case 0x27: /* dload_1 */ {
	if (VM_Assembler.TRACE) asm.noteBytecode(biStart, "dload_1");
	int offset = localOffset(1);
	asm.emitPUSH_RegDisp(FP, offset); // high part
	offset = localOffset(2);
	asm.emitPUSH_RegDisp(FP, offset); //  low part
	break;
      }
      case 0x28: /* dload_2 */ {
	if (VM_Assembler.TRACE) asm.noteBytecode(biStart, "dload_2");
	int offset = localOffset(2);
	asm.emitPUSH_RegDisp(FP, offset); // high part
	offset = localOffset(3);
	asm.emitPUSH_RegDisp(FP, offset); //  low part
	break;
      }
      case 0x29: /* dload_3 */ {
	if (VM_Assembler.TRACE) asm.noteBytecode(biStart, "dload_3");
	int offset = localOffset(3);
	asm.emitPUSH_RegDisp(FP, offset); // high part
	offset = localOffset(4);
	asm.emitPUSH_RegDisp(FP, offset); //  low part
	break;
      }
      case 0x2a: /* aload_0 */ {
	if (VM_Assembler.TRACE) asm.noteBytecode(biStart, "aload_0");
	int offset = localOffset(0);
	asm.emitPUSH_RegDisp(FP, offset);
	break;
      }
      case 0x2b: /* aload_1 */ {
	if (VM_Assembler.TRACE) asm.noteBytecode(biStart, "aload_1");
	int offset = localOffset(1);
	asm.emitPUSH_RegDisp(FP, offset);
	break;
      }           
      case 0x2c: /* aload_2 */ {
	if (VM_Assembler.TRACE) asm.noteBytecode(biStart, "aload_2");
	int offset = localOffset(2);
	asm.emitPUSH_RegDisp(FP, offset);
	break;
      }
      case 0x2d: /* aload_3 */ {
	if (VM_Assembler.TRACE) asm.noteBytecode(biStart, "aload_3");
	int offset = localOffset(3);
	asm.emitPUSH_RegDisp(FP, offset);
	break;
      } 
      case 0x2e: /* iaload */ {
	if (VM_Assembler.TRACE) asm.noteBytecode(biStart, "iaload");
	asm.emitMOV_Reg_RegDisp(T0, SP, 0);       // T0 is array index
	asm.emitMOV_Reg_RegDisp(S0, SP, 4);       // S0 is the array ref
	genBoundsCheck(asm, T0, S0);              // T0 is index, S0 is address of array
	asm.emitADD_Reg_Imm(SP, WORDSIZE*2);      // complete popping the 2 args
	asm.emitPUSH_RegIdx(S0, T0, asm.WORD, 0); // push desired int array element
	break;
      }
      case 0x2f: /* laload */ {
	if (VM_Assembler.TRACE) asm.noteBytecode(biStart, "laload");
	asm.emitMOV_Reg_RegDisp(T0, SP, 0);              // T0 is array index
	asm.emitMOV_Reg_RegDisp(S0, SP, 4);              // S0 is the array ref
	genBoundsCheck(asm, T0, S0);                     // T0 is index, S0 is address of array
	asm.emitADD_Reg_Imm(SP, WORDSIZE*2);             // complete popping the 2 args
	asm.emitPUSH_RegIdx(S0, T0, asm.LONG, WORDSIZE); // load high part of desired long array element
	asm.emitPUSH_RegIdx(S0, T0, asm.LONG, 0);        // load low part of desired long array element
	break;
      }
      case 0x30: /* faload */ {
	if (VM_Assembler.TRACE) asm.noteBytecode(biStart, "faload");
	asm.emitMOV_Reg_RegDisp(T0, SP, 0);       // T0 is array index
	asm.emitMOV_Reg_RegDisp(S0, SP, 4);       // S0 is the array ref
	genBoundsCheck(asm, T0, S0);              // T0 is index, S0 is address of array
	asm.emitADD_Reg_Imm(SP, WORDSIZE*2);      // complete popping the 2 args
	asm.emitPUSH_RegIdx(S0, T0, asm.WORD, 0); // push desired float array element
	break;
      }
      case 0x31: /* daload */ {
	if (VM_Assembler.TRACE) asm.noteBytecode(biStart, "daload");
	asm.emitMOV_Reg_RegDisp(T0, SP, 0);              // T0 is array index
	asm.emitMOV_Reg_RegDisp(S0, SP, 4);              // S0 is the array ref
	genBoundsCheck(asm, T0, S0);                     // T0 is index, S0 is address of array
	asm.emitADD_Reg_Imm(SP, WORDSIZE*2);             // complete popping the 2 args
	asm.emitPUSH_RegIdx(S0, T0, asm.LONG, WORDSIZE); // load high part of double
	asm.emitPUSH_RegIdx(S0, T0, asm.LONG, 0);        // load low part of double
	break;
      }
      case 0x32: /* aaload */ {
	if (VM_Assembler.TRACE) asm.noteBytecode(biStart, "aaload");
	asm.emitMOV_Reg_RegDisp(T0, SP, 0);       // T0 is array index
	asm.emitMOV_Reg_RegDisp(S0, SP, 4);       // S0 is the array ref
	genBoundsCheck(asm, T0, S0);              // T0 is index, S0 is address of array
	asm.emitADD_Reg_Imm(SP, WORDSIZE*2);      // complete popping the 2 args
	asm.emitPUSH_RegIdx(S0, T0, asm.WORD, 0); // push desired object array element
	break;
      }
      case 0x33: /* baload */ {
	if (VM_Assembler.TRACE) asm.noteBytecode(biStart, "baload");
	asm.emitMOV_Reg_RegDisp(T0, SP, 0);                     // T0 is array index
	asm.emitMOV_Reg_RegDisp(S0, SP, 4);                     // S0 is the array ref
	genBoundsCheck(asm, T0, S0);                            // T0 is index, S0 is address of array
	asm.emitADD_Reg_Imm(SP, WORDSIZE*2);                    // complete popping the 2 args
	asm.emitMOVSX_Reg_RegIdx_Byte(T1, S0, T0, asm.BYTE, 0); // load byte and sign extend to a 32 bit word
	asm.emitPUSH_Reg(T1);                                   // push sign extended byte onto stack
	break;
      }
      case 0x34: /* caload */ {
	if (VM_Assembler.TRACE) asm.noteBytecode(biStart, "caload");
	asm.emitMOV_Reg_RegDisp(T0, SP, 0);                      // T0 is array index
	asm.emitMOV_Reg_RegDisp(S0, SP, 4);                      // S0 is the array ref
	genBoundsCheck(asm, T0, S0);                             // T0 is index, S0 is address of array
	asm.emitADD_Reg_Imm(SP, WORDSIZE*2);                     // complete popping the 2 args
	asm.emitMOVZX_Reg_RegIdx_Word(T1, S0, T0, asm.SHORT, 0); // load halfword without sign extend to a 32 bit word
	asm.emitPUSH_Reg(T1);                                    // push char onto stack
	break;
      }
      case 0x35: /* saload */ {
	if (VM_Assembler.TRACE) asm.noteBytecode(biStart, "saload");
	asm.emitMOV_Reg_RegDisp(T0, SP, 0);                      // T0 is array index
	asm.emitMOV_Reg_RegDisp(S0, SP, 4);                      // S0 is the array ref
	genBoundsCheck(asm, T0, S0);                             // T0 is index, S0 is address of array
	asm.emitADD_Reg_Imm(SP, WORDSIZE*2);                     // complete popping the 2 args
	asm.emitMOVSX_Reg_RegIdx_Word(T1, S0, T0, asm.SHORT, 0); // load halfword sign extend to a 32 bit word
	asm.emitPUSH_Reg(T1);                                    // push sign extended short onto stack
	break;
      }
      case 0x36: /* istore */ {
	int index = fetch1ByteUnsigned();
	if (VM_Assembler.TRACE) asm.noteBytecode(biStart, "istore " + VM_Lister.decimal(index));
	int offset = localOffset(index);
	asm.emitPOP_RegDisp (FP, offset);
	break;
      }
      case 0x37: /* lstore */ {
	int index = fetch1ByteUnsigned();
	if (VM_Assembler.TRACE) asm.noteBytecode(biStart, "lstore " + VM_Lister.decimal(index));
	int offset = localOffset(index+1);
	asm.emitPOP_RegDisp(FP, offset); // high part
	offset = localOffset(index);
	asm.emitPOP_RegDisp(FP, offset); //  low part
	break;
      }
      case 0x38: /* fstore */ {
	int index = fetch1ByteUnsigned();
	if (VM_Assembler.TRACE) asm.noteBytecode(biStart, "fstore " + VM_Lister.decimal(index));
	int offset = localOffset(index);
	asm.emitPOP_RegDisp (FP, offset);
	break;
      }
      case 0x39: /* dstore */ {
	int index = fetch1ByteUnsigned();
	if (VM_Assembler.TRACE) asm.noteBytecode(biStart, "dstore " + VM_Lister.decimal(index));
	int offset = localOffset(index+1);
	asm.emitPOP_RegDisp(FP, offset); // high part
	offset = localOffset(index);
	asm.emitPOP_RegDisp(FP, offset); //  low part
	break;
      }
      case 0x3a: /* astore */ {
	int index = fetch1ByteUnsigned();
	if (VM_Assembler.TRACE) asm.noteBytecode(biStart, "astore " + VM_Lister.decimal(index));
	int offset = localOffset(index);
	asm.emitPOP_RegDisp (FP, offset);
	break;
      }
      case 0x3b: /* istore_0 */ {
	if (VM_Assembler.TRACE) asm.noteBytecode(biStart, "istore_0");
	int offset = localOffset(0);
	asm.emitPOP_RegDisp (FP, offset);
	break;
      }
      case 0x3c: /* istore_1 */ {
	if (VM_Assembler.TRACE) asm.noteBytecode(biStart, "istore_1");
	int offset = localOffset(1);
	asm.emitPOP_RegDisp (FP, offset);
	break;
      }
      case 0x3d: /* istore_2 */ {
	if (VM_Assembler.TRACE) asm.noteBytecode(biStart, "istore_2");
	int offset = localOffset(2);
	asm.emitPOP_RegDisp (FP, offset);
	break;
      }
      case 0x3e: /* istore_3 */ {
	if (VM_Assembler.TRACE) asm.noteBytecode(biStart, "istore_3");
	int offset = localOffset(3);
	asm.emitPOP_RegDisp (FP, offset);
	break;
      }
      case 0x3f: /* lstore_0 */ {
	if (VM_Assembler.TRACE) asm.noteBytecode(biStart, "lstore_0");
	int offset = localOffset(1);
	asm.emitPOP_RegDisp(FP, offset); // high part
	offset = localOffset(0);
	asm.emitPOP_RegDisp(FP, offset); //  low part
	break;
      }
      case 0x40: /* lstore_1 */ {
	if (VM_Assembler.TRACE) asm.noteBytecode(biStart, "lstore_1");
	int offset = localOffset(2);
	asm.emitPOP_RegDisp(FP, offset); // high part
	offset = localOffset(1);
	asm.emitPOP_RegDisp(FP, offset); //  low part
	break;
      }
      case 0x41: /* lstore_2 */ {
	if (VM_Assembler.TRACE) asm.noteBytecode(biStart, "lstore_2");
	int offset = localOffset(3);
	asm.emitPOP_RegDisp(FP, offset); // high part
	offset = localOffset(2);
	asm.emitPOP_RegDisp(FP, offset); //  low part
	break;
      } 
      case 0x42: /* lstore_3 */ {
	if (VM_Assembler.TRACE) asm.noteBytecode(biStart, "lstore_3");
	int offset = localOffset(4);
	asm.emitPOP_RegDisp(FP, offset); // high part
	offset = localOffset(3);
	asm.emitPOP_RegDisp(FP, offset); //  low part
	break;
      }
      case 0x43: /* fstore_0 */ {
	if (VM_Assembler.TRACE) asm.noteBytecode(biStart, "fstore_0");
	int offset = localOffset(0);
	asm.emitPOP_RegDisp (FP, offset);
	break;
      }
      case 0x44: /* fstore_1 */ {
	if (VM_Assembler.TRACE) asm.noteBytecode(biStart, "fstore_1");
	int offset = localOffset(1);
	asm.emitPOP_RegDisp (FP, offset);
	break;
      }
      case 0x45: /* fstore_2 */ {
	if (VM_Assembler.TRACE) asm.noteBytecode(biStart, "fstore_2");
	int offset = localOffset(2);
	asm.emitPOP_RegDisp (FP, offset);
	break;
      }
      case 0x46: /* fstore_3 */ {
	if (VM_Assembler.TRACE) asm.noteBytecode(biStart, "fstore_3");
	int offset = localOffset(3);
	asm.emitPOP_RegDisp (FP, offset);
	break;
      }
      case 0x47: /* dstore_0 */ {
	if (VM_Assembler.TRACE) asm.noteBytecode(biStart, "dstore_0");
	int offset = localOffset(1);
	asm.emitPOP_RegDisp(FP, offset); // high part
	offset = localOffset(0);
	asm.emitPOP_RegDisp(FP, offset); //  low part
	break;
      }
      case 0x48: /* dstore_1 */ {
	if (VM_Assembler.TRACE) asm.noteBytecode(biStart, "dstore_1");
	int offset = localOffset(2);
	asm.emitPOP_RegDisp(FP, offset); // high part
	offset = localOffset(1);
	asm.emitPOP_RegDisp(FP, offset); //  low part
	break;
      }
      case 0x49: /* dstore_2 */ {
	if (VM_Assembler.TRACE) asm.noteBytecode(biStart, "dstore_2");
	int offset = localOffset(3);
	asm.emitPOP_RegDisp(FP, offset); // high part
	offset = localOffset(2);
	asm.emitPOP_RegDisp(FP, offset); //  low part
	break;
      }
      case 0x4a: /* dstore_3 */ {
	if (VM_Assembler.TRACE) asm.noteBytecode(biStart, "dstore_3");
	int offset = localOffset(4);
	asm.emitPOP_RegDisp(FP, offset); // high part
	offset = localOffset(3);
	asm.emitPOP_RegDisp(FP, offset); //  low part
	break;
      }
      case 0x4b: /* astore_0 */ {
	if (VM_Assembler.TRACE) asm.noteBytecode(biStart, "astore_0");
	int offset = localOffset(0);
	asm.emitPOP_RegDisp (FP, offset);
	break;
      }
      case 0x4c: /* astore_1 */ {
	if (VM_Assembler.TRACE) asm.noteBytecode(biStart, "astore_1");
	int offset = localOffset(1);
	asm.emitPOP_RegDisp (FP, offset);
	break;
      }
      case 0x4d: /* astore_2 */ {
	if (VM_Assembler.TRACE) asm.noteBytecode(biStart, "astore_2");
	int offset = localOffset(2);
	asm.emitPOP_RegDisp (FP, offset);
	break;
      }
      case 0x4e: /* astore_3 */ {
	if (VM_Assembler.TRACE) asm.noteBytecode(biStart, "astore_3");
	int offset = localOffset(3);
	asm.emitPOP_RegDisp (FP, offset);
	break;
      }
      case 0x4f: /* iastore */ {
	if (VM_Assembler.TRACE) asm.noteBytecode(biStart, "iastore");
	asm.emitMOV_Reg_RegDisp(T0, SP, 4);              // T0 is array index
	asm.emitMOV_Reg_RegDisp(S0, SP, 8);              // S0 is the array ref
	genBoundsCheck(asm, T0, S0);                     // T0 is index, S0 is address of array
	asm.emitMOV_Reg_RegDisp(T1, SP, 0);              // T1 is the int value
	asm.emitMOV_RegIdx_Reg(S0, T0, asm.WORD, 0, T1); // [S0 + T0<<2] <- T1
        asm.emitADD_Reg_Imm(SP, WORDSIZE*3);             // complete popping the 3 args
	break;
      }
      case 0x50: /* lastore */ { 
	if (VM_Assembler.TRACE) asm.noteBytecode(biStart, "lastore"); 
	asm.emitMOV_Reg_RegDisp(T0, SP, 8);                     // T0 is the array index
	asm.emitMOV_Reg_RegDisp(S0, SP, 12);                    // S0 is the array ref
	genBoundsCheck(asm, T0, S0);                            // T0 is index, S0 is address of array
	asm.emitPOP_Reg(T1);                                    // low part of long value
	asm.emitMOV_RegIdx_Reg(S0, T0, asm.LONG, 0, T1);        // [S0 + T0<<3 + 0] <- T1 store low part into array i.e.  
	asm.emitPOP_Reg(T1);                                    // high part of long value
	asm.emitMOV_RegIdx_Reg(S0, T0, asm.LONG, WORDSIZE, T1); // [S0 + T0<<3 + 4] <- T1 store high part into array i.e. 
	asm.emitADD_Reg_Imm(SP, WORDSIZE*2);                    // remove index and ref from the stack
	break;
      }
      case 0x51: /* fastore */ {
	if (VM_Assembler.TRACE) asm.noteBytecode(biStart, "fastore");
	asm.emitMOV_Reg_RegDisp(T0, SP, 4);              // T0 is array index
	asm.emitMOV_Reg_RegDisp(S0, SP, 8);              // S0 is the array ref
	genBoundsCheck(asm, T0, S0);                     // T0 is index, S0 is address of array
	asm.emitMOV_Reg_RegDisp(T1, SP, 0);              // T1 is the float value
	asm.emitMOV_RegIdx_Reg(S0, T0, asm.WORD, 0, T1); // [S0 + T0<<2] <- T1
        asm.emitADD_Reg_Imm(SP, WORDSIZE*3);             // complete popping the 3 args
	break;
      }
      case 0x52: /* dastore */ {
	if (VM_Assembler.TRACE) asm.noteBytecode(biStart, "dastore");
	asm.emitMOV_Reg_RegDisp(T0, SP, 8);                     // T0 is the array index
	asm.emitMOV_Reg_RegDisp(S0, SP, 12);                    // S0 is the array ref
	genBoundsCheck(asm, T0, S0);                            // T0 is index, S0 is address of array
	asm.emitPOP_Reg(T1);                                    // low part of double value
	asm.emitMOV_RegIdx_Reg(S0, T0, asm.LONG, 0, T1);        // [S0 + T0<<3 + 0] <- T1 store low part into array i.e.  
	asm.emitPOP_Reg(T1);                                    // high part of double value
	asm.emitMOV_RegIdx_Reg(S0, T0, asm.LONG, WORDSIZE, T1); // [S0 + T0<<3 + 4] <- T1 store high part into array i.e. 
	asm.emitADD_Reg_Imm(SP, WORDSIZE*2);                    // remove index and ref from the stack
	break;
      }
      case 0x53: /* aastore */ {
	if (VM_Assembler.TRACE) asm.noteBytecode(biStart, "aastore");
	asm.emitPUSH_RegDisp(SP, 2<<LG_WORDSIZE);        // duplicate array ref
	asm.emitPUSH_RegDisp(SP, 1<<LG_WORDSIZE);        // duplicate object value
	genParameterRegisterLoad(2);                     // pass 2 parameter
	asm.emitCALL_RegDisp(JTOC, VM_Entrypoints.checkstoreOffset); // checkstore(array ref, value)
	asm.emitMOV_Reg_RegDisp(T0, SP, 4);              // T0 is array index
	asm.emitMOV_Reg_RegDisp(S0, SP, 8);              // S0 is the array ref
	genBoundsCheck(asm, T0, S0);                     // T0 is index, S0 is address of array
	asm.emitMOV_Reg_RegDisp(T1, SP, 0);              // T1 is the object value
	asm.emitMOV_RegIdx_Reg(S0, T0, asm.WORD, 0, T1); // [S0 + T0<<2] <- T1
        asm.emitADD_Reg_Imm(SP, WORDSIZE*3);             // complete popping the 3 args
	break;
      }
      case 0x54: /* bastore */ {
	if (VM_Assembler.TRACE) asm.noteBytecode(biStart, "bastore");
	asm.emitMOV_Reg_RegDisp(T0, SP, 4);                   // T0 is array index
	asm.emitMOV_Reg_RegDisp(S0, SP, 8);                   // S0 is the array ref
	genBoundsCheck(asm, T0, S0);                          // T0 is index, S0 is address of array
	asm.emitMOV_Reg_RegDisp(T1, SP, 0);                   // T1 is the byte value
	asm.emitMOV_RegIdx_Reg_Byte(S0, T0, asm.BYTE, 0, T1); // [S0 + T0<<2] <- T1
        asm.emitADD_Reg_Imm(SP, WORDSIZE*3);                  // complete popping the 3 args
	break;
      }
      case 0x55: /* castore */ {
	asm.emitMOV_Reg_RegDisp(T0, SP, 4);                   // T0 is array index
	asm.emitMOV_Reg_RegDisp(S0, SP, 8);                   // S0 is the array ref
	genBoundsCheck(asm, T0, S0);                          // T0 is index, S0 is address of array
	asm.emitMOV_Reg_RegDisp(T1, SP, 0);                   // T1 is the char value
	asm.emitMOV_RegIdx_Reg_Word(S0, T0, asm.SHORT, 0, T1);// store halfword element into array i.e. [S0 +T0] <- T1 (halfword)
        asm.emitADD_Reg_Imm(SP, WORDSIZE*3);                  // complete popping the 3 args
	break;
      }
      case 0x56: /* sastore */ {
	if (VM_Assembler.TRACE) asm.noteBytecode(biStart, "sastore");
	asm.emitMOV_Reg_RegDisp(T0, SP, 4);                   // T0 is array index
	asm.emitMOV_Reg_RegDisp(S0, SP, 8);                   // S0 is the array ref
	genBoundsCheck(asm, T0, S0);                          // T0 is index, S0 is address of array
	asm.emitMOV_Reg_RegDisp(T1, SP, 0);                   // T1 is the short value
	asm.emitMOV_RegIdx_Reg_Word(S0, T0, asm.SHORT, 0, T1);// store halfword element into array i.e. [S0 +T0] <- T1 (halfword)
        asm.emitADD_Reg_Imm(SP, WORDSIZE*3);                  // complete popping the 3 args
	break;
      }
      case 0x57: /* pop */ {
	if (VM_Assembler.TRACE) asm.noteBytecode(biStart, "pop");
	asm.emitPOP_Reg(T0);
	break;
      }
      case 0x58: /* pop2 */ {
	if (VM_Assembler.TRACE) asm.noteBytecode(biStart, "pop2");
	asm.emitPOP_Reg(T0);
	asm.emitPOP_Reg(T0);
	break;
      }
      case 0x59: /* dup */ {
	if (VM_Assembler.TRACE) asm.noteBytecode(biStart, "dup");
	asm.emitMOV_Reg_RegInd (T0, SP);
	asm.emitPUSH_Reg(T0);
	break;
      } 
      case 0x5a: /* dup_x1 */ {
	if (VM_Assembler.TRACE) asm.noteBytecode(biStart, "dup_x1");
	asm.emitPOP_Reg(T0);
	asm.emitPOP_Reg(S0);
	asm.emitPUSH_Reg(T0);
	asm.emitPUSH_Reg(S0);
	asm.emitPUSH_Reg(T0);
	break;
      }
      case 0x5b: /* dup_x2 */ {
	if (VM_Assembler.TRACE) asm.noteBytecode(biStart, "dup_x2");
	asm.emitPOP_Reg(T0);
	asm.emitPOP_Reg(S0);
	asm.emitPOP_Reg(T1);
	asm.emitPUSH_Reg(T0);
	asm.emitPUSH_Reg(T1);
	asm.emitPUSH_Reg(S0);
	asm.emitPUSH_Reg(T0);
	break;
      }
      case 0x5c: /* dup2 */ {
	if (VM_Assembler.TRACE) asm.noteBytecode(biStart, "dup2");
	asm.emitMOV_Reg_RegDisp (T0, SP, 4);
	asm.emitMOV_Reg_RegInd (S0, SP);
	asm.emitPUSH_Reg(T0);
	asm.emitPUSH_Reg(S0);
	break;
      }
      case 0x5d: /* dup2_x1 */ {
	if (VM_Assembler.TRACE) asm.noteBytecode(biStart, "dup2_x1");
	asm.emitPOP_Reg(T0);
	asm.emitPOP_Reg(S0);
	asm.emitPOP_Reg(T1);
	asm.emitPUSH_Reg(S0);
	asm.emitPUSH_Reg(T0);
	asm.emitPUSH_Reg(T1);
	asm.emitPUSH_Reg(S0);
	asm.emitPUSH_Reg(T0);
	break;
      }
      case 0x5e: /* dup2_x2 */ {
	if (VM_Assembler.TRACE) asm.noteBytecode(biStart, "dup2_x2");
	asm.emitPOP_Reg(T0);
	asm.emitPOP_Reg(S0);
	asm.emitPOP_Reg(T1);
	asm.emitPOP_Reg(JTOC);                  // JTOC is scratch register
	asm.emitPUSH_Reg(S0);
	asm.emitPUSH_Reg(T0);
	asm.emitPUSH_Reg(JTOC);
	asm.emitPUSH_Reg(T1);
	asm.emitPUSH_Reg(S0);
	asm.emitPUSH_Reg(T0);
	asm.emitMOV_Reg_RegDisp (JTOC, PR, VM_Entrypoints.jtocOffset); // restore JTOC register
	break;
      }
      case 0x5f: /* swap */ {
	if (VM_Assembler.TRACE) asm.noteBytecode(biStart, "swap");
	asm.emitPOP_Reg(T0);
	asm.emitPOP_Reg(S0);
	asm.emitPUSH_Reg(T0);
	asm.emitPUSH_Reg(S0);
	break;
      }
      case 0x60: /* iadd */ {
	if (VM_Assembler.TRACE) asm.noteBytecode(biStart, "iadd");
	asm.emitPOP_Reg(T0);
	asm.emitADD_RegInd_Reg(SP, T0);
	break;
      }
      case 0x61: /* ladd */ {
	if (VM_Assembler.TRACE) asm.noteBytecode(biStart, "ladd");
	asm.emitPOP_Reg(T0);                 // the low half of one long
	asm.emitPOP_Reg(S0);                 // the high half
	asm.emitADD_RegInd_Reg(SP, T0);          // add low halves
	asm.emitADC_RegDisp_Reg(SP, WORDSIZE, S0);   // add high halves with carry
	break;
      }
      case 0x62: /* fadd */ {
	if (VM_Assembler.TRACE) asm.noteBytecode(biStart, "fadd");
	asm.emitFLD_Reg_RegInd (FP0, SP);        // FPU reg. stack <- value2
	asm.emitFADD_Reg_RegDisp(FP0, SP, WORDSIZE); // FPU reg. stack += value1
	asm.emitPOP_Reg   (T0);           // discard 
	asm.emitFSTP_RegInd_Reg(SP, FP0);        // POP FPU reg. stack onto stack
	break;
      }
      case 0x63: /* dadd */ {
	if (VM_Assembler.TRACE) asm.noteBytecode(biStart, "dadd");
	asm.emitFLD_Reg_RegInd_Quad (FP0, SP);        // FPU reg. stack <- value2
	asm.emitFADD_Reg_RegDisp_Quad(FP0, SP, 8);        // FPU reg. stack += value1
	asm.emitADD_Reg_Imm(SP, 2*WORDSIZE);  // shrink the stack
	asm.emitFSTP_RegInd_Reg_Quad(SP, FP0);        // POP FPU reg. stack onto stack
	break;
      }
      case 0x64: /* isub */ {
	if (VM_Assembler.TRACE) asm.noteBytecode(biStart, "isub");
	asm.emitPOP_Reg(T0);
	asm.emitSUB_RegInd_Reg(SP, T0);
	break;
      }
      case 0x65: /* lsub */ {
	if (VM_Assembler.TRACE) asm.noteBytecode(biStart, "lsub");
	asm.emitPOP_Reg(T0);                 // the low half of one long
	asm.emitPOP_Reg(S0);                 // the high half
	asm.emitSUB_RegInd_Reg(SP, T0);          // subtract low halves
	asm.emitSBB_RegDisp_Reg(SP, WORDSIZE, S0);   // subtract high halves with borrow
	break;
      }
      case 0x66: /* fsub */ {
	if (VM_Assembler.TRACE) asm.noteBytecode(biStart, "fsub");
	asm.emitFLD_Reg_RegDisp (FP0, SP, WORDSIZE); // FPU reg. stack <- value1
	asm.emitFSUB_Reg_RegDisp(FP0, SP, 0);        // FPU reg. stack -= value2
	asm.emitPOP_Reg   (T0);           // discard 
	asm.emitFSTP_RegInd_Reg(SP, FP0);        // POP FPU reg. stack onto stack
	break;
      }
      case 0x67: /* dsub */ {
	if (VM_Assembler.TRACE) asm.noteBytecode(biStart, "dsub");
	asm.emitFLD_Reg_RegDisp_Quad (FP0, SP, 8);          // FPU reg. stack <- value1
	asm.emitFSUB_Reg_RegDisp_Quad(FP0, SP, 0);          // FPU reg. stack -= value2
	asm.emitADD_Reg_Imm   (SP, 2*WORDSIZE); // shrink the stack
	asm.emitFSTP_RegInd_Reg_Quad(SP, FP0);          // POP FPU reg. stack onto stack
	break;
      }
      case 0x68: /* imul */ {
	if (VM_Assembler.TRACE) asm.noteBytecode(biStart, "imul");
	asm.emitPOP_Reg (T0);
	asm.emitIMUL2_Reg_RegInd(T0, SP);
	asm.emitMOV_RegInd_Reg (SP, T0);
	break;
      }
      case 0x69: /* lmul */ {
	if (VM_Assembler.TRACE) asm.noteBytecode(biStart, "lmul");
	// 0: JTOC is used as scratch registers (see 14)
	// 1: load value1.low temp0, i.e., save value1.low
	// 2: eax <- temp0 eax is value1.low
	// 3: edx:eax <- eax * value2.low (product of the two low halves)
	// 4: store eax which is  result.low into place --> value1.low is destroyed
	// 5: temp1 <- edx which is the carry of the product of the low halves
	// aex and edx now free of results
	// 6: aex <- temp0 which is still value1.low
	// 7: pop into aex aex <- value2.low  --> value2.low is sort of destroyed
	// 8: edx:eax <- eax * value1.hi  (value2.low * value1.hi)
	// 9: temp1 += aex
	// 10: pop into eax; eax <- value2.hi -> value2.hi is sort of destroyed
	// 11: edx:eax <- eax * temp0 (value2.hi * value1.low)
	// 12: temp1 += eax  temp1 is now result.hi
	// 13: store result.hi
	// 14: restore JTOC
	if (VM.VerifyAssertions) VM.assert(S0 != EAX);
	if (VM.VerifyAssertions) VM.assert(S0 != EDX);
	asm.emitMOV_Reg_RegDisp (JTOC, SP, 8);          // step 1: JTOC is temp0
	asm.emitMOV_Reg_Reg (EAX, JTOC);            // step 2
	asm.emitMUL_Reg_RegInd(EAX, SP);    // step 3
	asm.emitMOV_RegDisp_Reg (SP, 8, EAX);           // step 4
	asm.emitMOV_Reg_Reg (S0, EDX);              // step 5: S0 is temp1
	asm.emitMOV_Reg_Reg (EAX, JTOC);            // step 6
	asm.emitPOP_Reg (EAX);                  // step 7: SP changed!
	asm.emitIMUL1_Reg_RegDisp(EAX, SP, 8);// step 8
	asm.emitADD_Reg_Reg (S0, EAX);      // step 9
	asm.emitPOP_Reg (EAX);                  // step 10: SP changed!
	asm.emitIMUL1_Reg_Reg(EAX, JTOC);    // step 11
	asm.emitADD_Reg_Reg (S0, EAX);      // step 12
	asm.emitMOV_RegDisp_Reg (SP, 4, S0);            // step 13
	asm.emitMOV_Reg_RegDisp (JTOC, PR, VM_Entrypoints.jtocOffset); // restore JTOC register
	break;
      }
      case 0x6a: /* fmul */ {
	if (VM_Assembler.TRACE) asm.noteBytecode(biStart, "fmul");
	asm.emitFLD_Reg_RegInd (FP0, SP);        // FPU reg. stack <- value2
	asm.emitFMUL_Reg_RegDisp(FP0, SP, WORDSIZE); // FPU reg. stack *= value1
	asm.emitPOP_Reg   (T0);           // discard 
	asm.emitFSTP_RegInd_Reg(SP, FP0);        // POP FPU reg. stack onto stack
	break;
      }
      case 0x6b: /* dmul */ {
	if (VM_Assembler.TRACE) asm.noteBytecode(biStart, "dmul");
	asm.emitFLD_Reg_RegInd_Quad (FP0, SP);          // FPU reg. stack <- value2
	asm.emitFMUL_Reg_RegDisp_Quad(FP0, SP, 8);          // FPU reg. stack *= value1
	asm.emitADD_Reg_Imm   (SP, 2*WORDSIZE); // shrink the stack
	asm.emitFSTP_RegInd_Reg_Quad(SP, FP0);          // POP FPU reg. stack onto stack
	break;
      }
      case 0x6c: /* idiv */ {
	if (VM_Assembler.TRACE) asm.noteBytecode(biStart, "idiv");
	asm.emitPOP_Reg (ECX); // NOTE: can't use symbolic registers because of intel hardware requirements
	asm.emitPOP_Reg (EAX);
	asm.emitCDQ ();    // sign extend EAX into EDX
	asm.emitIDIV_Reg_Reg(EAX, ECX); // compute EAX/ECX - Quotient in EAX, remainder in EDX
	asm.emitPUSH_Reg(EAX);
	break;
      }
      case 0x6d: /* ldiv */ {
	if (VM_Assembler.TRACE) asm.noteBytecode(biStart, "ldiv");
	int offset = VM_Entrypoints.longDivideOffset;
	genParameterRegisterLoad(4); // pass 4 parameter words (2 longs)
	asm.emitCALL_RegDisp(JTOC, offset);
	asm.emitPUSH_Reg(T0);  // high half
	asm.emitPUSH_Reg(T1);  // low half
	break;
      }
      case 0x6e: /* fdiv */ {
	if (VM_Assembler.TRACE) asm.noteBytecode(biStart, "fdiv");
	asm.emitFLD_Reg_RegDisp (FP0, SP, WORDSIZE); // FPU reg. stack <- value1
	asm.emitFDIV_Reg_RegDisp(FP0, SP, 0);        // FPU reg. stack /= value2
	asm.emitPOP_Reg   (T0);           // discard 
	asm.emitFSTP_RegInd_Reg(SP, FP0);        // POP FPU reg. stack onto stack
	break;
      }
      case 0x6f: /* ddiv */ {
	if (VM_Assembler.TRACE) asm.noteBytecode(biStart, "ddiv");
	asm.emitFLD_Reg_RegDisp_Quad (FP0, SP, 8);          // FPU reg. stack <- value1
	asm.emitFDIV_Reg_RegInd_Quad(FP0, SP);          // FPU reg. stack /= value2
	asm.emitADD_Reg_Imm   (SP, 2*WORDSIZE); // shrink the stack
	asm.emitFSTP_RegInd_Reg_Quad(SP, FP0);          // POP FPU reg. stack onto stack
	break;
      }
      case 0x70: /* irem */ {
	if (VM_Assembler.TRACE) asm.noteBytecode(biStart, "irem");
	asm.emitPOP_Reg (ECX); // NOTE: can't use symbolic registers because of intel hardware requirements
	asm.emitPOP_Reg (EAX);
	asm.emitCDQ ();    // sign extend EAX into EDX
	asm.emitIDIV_Reg_Reg(EAX, ECX); // compute EAX/ECX - Quotient in EAX, remainder in EDX
	asm.emitPUSH_Reg(EDX);
	break;
      }
      case 0x71: /* lrem */ {
	if (VM_Assembler.TRACE) asm.noteBytecode(biStart, "lrem");
	int offset = VM_Entrypoints.longRemainderOffset;
	genParameterRegisterLoad(4); // pass 4 parameter words (2 longs)
	asm.emitCALL_RegDisp(JTOC, offset);
	asm.emitPUSH_Reg(T0);  // high half
	asm.emitPUSH_Reg(T1);  // low half
	break;
      }
      case 0x72: /* frem */ {
	if (VM_Assembler.TRACE) asm.noteBytecode(biStart, "frem"); 
	asm.emitFLD_Reg_RegInd (FP0, SP);        // FPU reg. stack <- value2, or a
	asm.emitFLD_Reg_RegDisp (FP0, SP, WORDSIZE); // FPU reg. stack <- value1, or b
	asm.emitFPREM ();             // FPU reg. stack <- a%b
	asm.emitFSTP_RegDisp_Reg(SP, WORDSIZE, FP0); // POP FPU reg. stack (results) onto java stack
	asm.emitFSTP_RegInd_Reg(SP, FP0);        // POP FPU reg. stack onto java stack
	asm.emitPOP_Reg   (T0);           // shrink the stack (T0 discarded)
	break;
      }
      case 0x73: /* drem */ {
	if (VM_Assembler.TRACE) asm.noteBytecode(biStart, "drem");
	asm.emitFLD_Reg_RegInd_Quad (FP0, SP);          // FPU reg. stack <- value2, or a
	asm.emitFLD_Reg_RegDisp_Quad (FP0, SP, 2*WORDSIZE); // FPU reg. stack <- value1, or b
	asm.emitFPREM ();               // FPU reg. stack <- a%b
	asm.emitFSTP_RegDisp_Reg_Quad(SP, 2*WORDSIZE, FP0); // POP FPU reg. stack (result) onto java stack
	asm.emitFSTP_RegInd_Reg_Quad(SP, FP0);         // POP FPU reg. stack onto java stack
	asm.emitADD_Reg_Imm   (SP, 2*WORDSIZE); // shrink the stack
	break;
      }
      case 0x74: /* ineg */ {
	if (VM_Assembler.TRACE) asm.noteBytecode(biStart, "ineg");
	asm.emitNEG_RegInd(SP); // [SP] <- -[SP]
	break;
      }
      case 0x75: /* lneg */ {
	if (VM_Assembler.TRACE) asm.noteBytecode(biStart, "lneg");
	asm.emitNEG_RegDisp(SP, 4);    // [SP+4] <- -[SP+4] or high <- -high
	asm.emitNEG_RegInd(SP);    // [SP] <- -[SP] or low <- -low
	asm.emitSBB_RegDisp_Imm(SP, 4, 0); // [SP+4] += borrow or high += borrow
	break;
      }
      case 0x76: /* fneg */ {
	if (VM_Assembler.TRACE) asm.noteBytecode(biStart, "fneg");
	asm.emitFLD_Reg_RegInd (FP0, SP); // FPU reg. stack <- value1
	asm.emitFCHS  ();      // change sign to stop of FPU stack
	asm.emitFSTP_RegInd_Reg(SP, FP0); // POP FPU reg. stack onto stack
	break;
      }
      case 0x77: /* dneg */ {
	if (VM_Assembler.TRACE) asm.noteBytecode(biStart, "dneg");
	asm.emitFLD_Reg_RegInd_Quad (FP0, SP); // FPU reg. stack <- value1
	asm.emitFCHS  ();      // change sign to stop of FPU stack
	asm.emitFSTP_RegInd_Reg_Quad(SP, FP0); // POP FPU reg. stack onto stack
	break;
      }
      case 0x78: /* ishl */ {
	if (VM_Assembler.TRACE) asm.noteBytecode(biStart, "ishl");
	if (VM.VerifyAssertions) VM.assert(ECX != T0);
	asm.emitPOP_Reg(ECX);
	asm.emitSHL_RegInd_Reg(SP, ECX);   // shift T0 left ECX times;  ECX low order 5 bits
	break;
      }
      case 0x79: /* lshl */ {
	if (VM_Assembler.TRACE) asm.noteBytecode(biStart, "lshl");    // l >> n
	if (VM.VerifyAssertions) VM.assert (ECX != T0); // ECX is constrained to be the shift count
	if (VM.VerifyAssertions) VM.assert (ECX != T1);
	if (VM.VerifyAssertions) VM.assert (ECX != JTOC);
	// 1: pop shift amount into JTOC (JTOC must be restored at the end)
	// 2: pop low half into T0
	// 3: pop high half into T1
	// 4: ECX <- JTOC, copy the shift count
	// 5: JTOC <- JTOC & 32 --> if 0 then shift amount is less than 32
	// 6: branch to step 12 if results is zero
	// the result is not zero --> the shift amount is greater than 32
	// 7: ECX <- ECX XOR JTOC   --> ECX is orginal shift amount minus 32
	// 8: T1 <- T0, or replace the high half with the low half.  This accounts for the 32 bit shift
	// 9: shift T1 left by ECX bits
	// 10: T0 <- 0
	// 11: branch to step 14
	// 12: shift left double from T0 into T1 by ECX bits.  T0 is unaltered
	// 13: shift left T0, the low half, also by ECX bits
	// 14: push high half from T1
	// 15: push the low half from T0
	// 16: restore the JTOC
	asm.emitPOP_Reg (JTOC);                 // original shift amount 6 bits
	asm.emitPOP_Reg (T0);                   // pop low half 
	asm.emitPOP_Reg (T1);                   // pop high half
	asm.emitMOV_Reg_Reg (ECX, JTOC);
	asm.emitAND_Reg_Imm (JTOC, 32);
	VM_ForwardReference fr1 = asm.forwardJcc(asm.EQ);
	asm.emitXOR_Reg_Reg (ECX, JTOC);
	asm.emitMOV_Reg_Reg (T1, T0);               // low replaces high
	asm.emitSHL_Reg_Reg (T1, ECX);
	asm.emitXOR_Reg_Reg (T0, T0);
	VM_ForwardReference fr2 = asm.forwardJMP();
	fr1.resolve(asm);
	asm.emitSHLD_Reg_Reg_Reg(T1, T0, ECX);          // shift high half (step 12)
	asm.emitSHL_Reg_Reg (T0, ECX);                   // shift low half
	fr2.resolve(asm);
	asm.emitPUSH_Reg(T1);                   // push high half (step 14)
	asm.emitPUSH_Reg(T0);                   // push low half
	asm.emitMOV_Reg_RegDisp (JTOC, PR, VM_Entrypoints.jtocOffset); // restore JTOC
	break;
      }
      case 0x7a: /* ishr */ {
	// unit test by IArith
	if (VM_Assembler.TRACE) asm.noteBytecode(biStart, "ishr");
	if (VM.VerifyAssertions) VM.assert (ECX != T0);
	asm.emitPOP_Reg (ECX);
	asm.emitSAR_RegInd_Reg (SP, ECX);  // shift T0 right ECX times;  ECX low order 5 bits
	break;
      }
      case 0x7b: /* lshr */ {
	if (VM_Assembler.TRACE) asm.noteBytecode(biStart, "lshr");
	if (VM.VerifyAssertions) VM.assert (ECX != T0); // ECX is constrained to be the shift count
	if (VM.VerifyAssertions) VM.assert (ECX != T1);
	if (VM.VerifyAssertions) VM.assert (ECX != JTOC);
	// 1: pop shift amount into JTOC (JTOC must be restored at the end)
	// 2: pop low half into T0
	// 3: pop high half into T1
	// 4: ECX <- JTOC, copy the shift count
	// 5: JTOC <- JTOC & 32 --> if 0 then shift amount is less than 32
	// 6: branch to step 13 if results is zero
	// the result is not zero --> the shift amount is greater than 32
	// 7: ECX <- ECX XOR JTOC   --> ECX is orginal shift amount minus 32
	// 8: T0 <- T1, or replace the low half with the high half.  This accounts for the 32 bit shift
	// 9: shift T0 right arithmetic by ECX bits
	// 10: ECX <- 31
	// 11: shift T1 right arithmetic by ECX=31 bits, thus exending the sigh
	// 12: branch to step 15
	// 13: shift right double from T1 into T0 by ECX bits.  T1 is unaltered
	// 14: shift right arithmetic T1, the high half, also by ECX bits
	// 15: push high half from T1
	// 16: push the low half from T0
	// 17: restore JTOC
	asm.emitPOP_Reg (JTOC);                 // original shift amount 6 bits
	asm.emitPOP_Reg (T0);                   // pop low half 
	asm.emitPOP_Reg (T1);                   // pop high
	asm.emitMOV_Reg_Reg (ECX, JTOC);
	asm.emitAND_Reg_Imm (JTOC, 32);
	VM_ForwardReference fr1 = asm.forwardJcc(asm.EQ);
	asm.emitXOR_Reg_Reg (ECX, JTOC);
	asm.emitMOV_Reg_Reg (T0, T1);               // replace low with high
	asm.emitSAR_Reg_Reg (T0, ECX);                   // and shift it
	asm.emitMOV_Reg_Imm (ECX, 31);
	asm.emitSAR_Reg_Reg (T1, ECX);                   // set high half
	VM_ForwardReference fr2 = asm.forwardJMP();
	fr1.resolve(asm);
	asm.emitSHRD_Reg_Reg_Reg(T0, T1, ECX);          // shift low half (step 13)
	asm.emitSAR_Reg_Reg (T1, ECX);                   // shift high half
	fr2.resolve(asm);
	asm.emitPUSH_Reg(T1);                   // push high half (step 15)
	asm.emitPUSH_Reg(T0);                   // push low half
	asm.emitMOV_Reg_RegDisp (JTOC, PR, VM_Entrypoints.jtocOffset); // restore JTOC
	break;
      }
      case 0x7c: /* iushr */ {
	// unit test by IArith
	if (VM_Assembler.TRACE) asm.noteBytecode(biStart, "iushr");
	if (VM.VerifyAssertions) VM.assert (ECX != T0);
	asm.emitPOP_Reg (ECX);
	asm.emitSHR_RegInd_Reg(SP, ECX);  // shift T0 right ECX times;  ECX low order 5 bits
	break;
      }
      case 0x7d: /* lushr */ {
	if (VM_Assembler.TRACE) asm.noteBytecode(biStart, "lushr");
	if (VM.VerifyAssertions) VM.assert (ECX != T0); // ECX is constrained to be the shift count
	if (VM.VerifyAssertions) VM.assert (ECX != T1);
	if (VM.VerifyAssertions) VM.assert (ECX != JTOC);
	// 1: pop shift amount into JTOC (JTOC must be restored at the end)
	// 2: pop low half into T0
	// 3: ECX <- JTOC, copy the shift count
	// 4: JTOC <- JTOC & 32 --> if 0 then shift amount is less than 32
	// 5: branch to step 11 if results is zero
	// the result is not zero --> the shift amount is greater than 32
	// 6: ECX <- ECX XOR JTOC   --> ECX is orginal shift amount minus 32
	// 7: pop high half into T0 replace the low half with the high 
	//        half.  This accounts for the 32 bit shift
	// 8: shift T0 right logical by ECX bits
	// 9: T1 <- 0                        T1 is the high half
	// 10: branch to step 14
	// 11: pop high half into T1
	// 12: shift right double from T1 into T0 by ECX bits.  T1 is unaltered
	// 13: shift right logical T1, the high half, also by ECX bits
	// 14: push high half from T1
	// 15: push the low half from T0
	// 16: restore JTOC
	asm.emitPOP_Reg(JTOC);                // original shift amount 6 bits
	asm.emitPOP_Reg(T0);                  // pop low half 
	asm.emitMOV_Reg_Reg(ECX, JTOC);
	asm.emitAND_Reg_Imm(JTOC, 32);
	VM_ForwardReference fr1 = asm.forwardJcc(asm.EQ);
	asm.emitXOR_Reg_Reg (ECX, JTOC);
	asm.emitPOP_Reg (T0);                   // replace low with high
	asm.emitSHR_Reg_Reg (T0, ECX);      // and shift it (count - 32)
	asm.emitXOR_Reg_Reg (T1, T1);               // high <- 0
	VM_ForwardReference fr2 = asm.forwardJMP();
	fr1.resolve(asm);
	asm.emitPOP_Reg (T1);                   // high half (step 11)
	asm.emitSHRD_Reg_Reg_Reg(T0, T1, ECX);          // shift low half
	asm.emitSHR_Reg_Reg (T1, ECX);                   // shift high half
	fr2.resolve(asm);
	asm.emitPUSH_Reg(T1);                   // push high half (step 14)
	asm.emitPUSH_Reg(T0);                   // push low half
	asm.emitMOV_Reg_RegDisp (JTOC, PR, VM_Entrypoints.jtocOffset); // restore JTOC
	break;
      }
      case 0x7e: /* iand */ {
	// unit test by IArith
	if (VM_Assembler.TRACE) asm.noteBytecode(biStart, "iand");
	asm.emitPOP_Reg(T0);
	asm.emitAND_RegInd_Reg(SP, T0);
	break;
      }
      case 0x7f: /* land */ {
	if (VM_Assembler.TRACE) asm.noteBytecode(biStart, "land");
	asm.emitPOP_Reg(T0);        // low
	asm.emitPOP_Reg(S0);        // high
	asm.emitAND_RegInd_Reg(SP, T0);
	asm.emitAND_RegDisp_Reg(SP, 4, S0);
	break;
      }
      case 0x80: /* ior */ {
	// unit test by IArith
	if (VM_Assembler.TRACE) asm.noteBytecode(biStart, "ior");
	asm.emitPOP_Reg(T0);
	asm.emitOR_RegInd_Reg (SP, T0);
	break;
      }
      case 0x81: /* lor */ {
	if (VM_Assembler.TRACE) asm.noteBytecode(biStart, "lor");
	asm.emitPOP_Reg(T0);        // low
	asm.emitPOP_Reg(S0);        // high
	asm.emitOR_RegInd_Reg(SP, T0);
	asm.emitOR_RegDisp_Reg(SP, 4, S0);
	break;
      }
      case 0x82: /* ixor */ {
	// unit test by IArith
	if (VM_Assembler.TRACE) asm.noteBytecode(biStart, "ixor");
	asm.emitPOP_Reg(T0);
	asm.emitXOR_RegInd_Reg(SP, T0);
	break;
      }
      case 0x83: /* lxor */ {
	if (VM_Assembler.TRACE) asm.noteBytecode(biStart, "lxor");
	asm.emitPOP_Reg(T0);        // low
	asm.emitPOP_Reg(S0);        // high
	asm.emitXOR_RegInd_Reg(SP, T0);
	asm.emitXOR_RegDisp_Reg(SP, 4, S0);
	break;
      }
      case 0x84: /* iinc */ {
	int index = fetch1ByteUnsigned();
	int val = fetch1ByteSigned();
	if (VM_Assembler.TRACE) asm.noteBytecode(biStart, "iinc " + VM_Lister.decimal(index) + " " + VM_Lister.decimal(val));
	int offset = localOffset(index);
	asm.emitADD_RegDisp_Imm(FP, offset, val);
	break;
      }
      case 0x85: /* i2l */ {
	if (VM_Assembler.TRACE) asm.noteBytecode(biStart, "i2l");
	asm.emitPOP_Reg (EAX);
	asm.emitCDQ ();
	asm.emitPUSH_Reg(EDX);
	asm.emitPUSH_Reg(EAX);
	break;
      }
      case 0x86: /* i2f */ {
	// unit test by FArith
	if (VM_Assembler.TRACE) asm.noteBytecode(biStart, "i2f");
	asm.emitFILD_Reg_RegInd(FP0, SP);
	asm.emitFSTP_RegInd_Reg(SP, FP0);
	break;
      }
      case 0x87: /* i2d */ {
	// unit test by DArith
	if (VM_Assembler.TRACE) asm.noteBytecode(biStart, "i2d");
	asm.emitFILD_Reg_RegInd(FP0, SP);
	asm.emitPUSH_Reg(T0);             // grow the stack
	asm.emitFSTP_RegInd_Reg_Quad(SP, FP0);
	break;
      }
      case 0x88: /* l2i */ {
	if (VM_Assembler.TRACE) asm.noteBytecode(biStart, "l2i");
	asm.emitPOP_Reg (T0); // low half of the long
	asm.emitPOP_Reg (S0); // high half of the long
	asm.emitPUSH_Reg(T0);
	break;
      }
      case 0x89: /* l2f */ {
	if (VM_Assembler.TRACE) asm.noteBytecode(biStart, "l2f");
	asm.emitFILD_Reg_RegInd_Quad(FP0, SP);
	asm.emitADD_Reg_Imm(SP, WORDSIZE);                // shrink the stack
	asm.emitFSTP_RegInd_Reg(SP, FP0);
	break;
      }
      case 0x8a: /* l2d */ {
	if (VM_Assembler.TRACE) asm.noteBytecode(biStart, "l2d");
	asm.emitFILD_Reg_RegInd_Quad(FP0, SP);
	asm.emitFSTP_RegInd_Reg_Quad(SP, FP0);
	break;
      }
      case 0x8b: /* f2i */ {
	if (VM_Assembler.TRACE) asm.noteBytecode(biStart, "f2i");
	// convert to double
	asm.emitFLD_Reg_RegInd(FP0, SP);
	asm.emitSUB_Reg_Imm(SP, WORDSIZE);                // grow the stack
	asm.emitFSTP_RegInd_Reg_Quad(SP, FP0);
	// and convert to int
	int offset = VM_Entrypoints.doubleToIntOffset;
	genParameterRegisterLoad(); // pass 1 parameter
	asm.emitCALL_RegDisp(JTOC, offset);
	asm.emitPUSH_Reg(T0);
	break;
      }
      case 0x8c: /* f2l */ {
	if (VM_Assembler.TRACE) asm.noteBytecode(biStart, "f2l");
	// convert to double
	asm.emitFLD_Reg_RegInd(FP0, SP);
	asm.emitSUB_Reg_Imm(SP, WORDSIZE);                // grow the stack
	asm.emitFSTP_RegInd_Reg_Quad(SP, FP0);
	// and convert to long
	int offset = VM_Entrypoints.doubleToLongOffset;
	genParameterRegisterLoad(); // pass 1 parameter
	asm.emitCALL_RegDisp(JTOC, offset);
	asm.emitPUSH_Reg(T0);        // high half
	asm.emitPUSH_Reg(T1);        // low half
	break;
      }
      case 0x8d: /* f2d */ {
	if (VM_Assembler.TRACE) asm.noteBytecode(biStart, "f2d");
	asm.emitFLD_Reg_RegInd(FP0, SP);
	asm.emitSUB_Reg_Imm(SP, WORDSIZE);                // grow the stack
	asm.emitFSTP_RegInd_Reg_Quad(SP, FP0);
	break;
      }
      case 0x8e: /* d2i */ {
	if (VM_Assembler.TRACE) asm.noteBytecode(biStart, "d2i");
	int offset = VM_Entrypoints.doubleToIntOffset;
	genParameterRegisterLoad(); // pass 1 parameter
	asm.emitCALL_RegDisp(JTOC, offset);
	asm.emitPUSH_Reg(T0);
	break;
      }
      case 0x8f: /* d2l */ {
	if (VM_Assembler.TRACE) asm.noteBytecode(biStart, "d2l");
	int offset = VM_Entrypoints.doubleToLongOffset;
	genParameterRegisterLoad(); // pass 1 parameter
	asm.emitCALL_RegDisp(JTOC, offset);
	asm.emitPUSH_Reg(T0);        // high half
	asm.emitPUSH_Reg(T1);        // low half
	break;
      }
      case 0x90: /* d2f */ {
	if (VM_Assembler.TRACE) asm.noteBytecode(biStart, "d2f");
	asm.emitFLD_Reg_RegInd_Quad(FP0, SP);
	asm.emitADD_Reg_Imm(SP, WORDSIZE);                // shrink the stack
	asm.emitFSTP_RegInd_Reg(SP, FP0);
	break;
      }
      case 0x91: /* i2b */ {
	if (VM_Assembler.TRACE) asm.noteBytecode(biStart, "i2b");
	//          asm.emitMOVSXb(T0, SP, 0); // load byte off top of the stack sign extended
	//          asm.emitMOV   (SP, 0, T0); // store result back on the top of the stack
	asm.emitPOP_Reg   (T0);
	asm.emitMOVSX_Reg_Reg_Byte(T0, T0);
	asm.emitPUSH_Reg  (T0);
	break;
      }
      case 0x92: /* i2c */ {
	if (VM_Assembler.TRACE) asm.noteBytecode(biStart, "i2c");
	//          asm.emitMOVZXh(T0, SP, 0); // load char off top of the stack sign extended
	//          asm.emitMOV   (SP, 0, T0); // store result back on the top of the stack
	asm.emitPOP_Reg   (T0);
	asm.emitMOVZX_Reg_Reg_Word(T0, T0);
	asm.emitPUSH_Reg  (T0);
	break;
      }
      case 0x93: /* i2s */ {
	if (VM_Assembler.TRACE) asm.noteBytecode(biStart, "i2s");
	//          asm.emitMOVSXh(T0, SP, 0); // load short off top of the stack sign extended
	//          asm.emitMOV   (SP, 0, T0); // store result back on the top of the stack
	asm.emitPOP_Reg   (T0);
	asm.emitMOVSX_Reg_Reg_Word(T0, T0);
	asm.emitPUSH_Reg  (T0);
	break;
      }
      case 0x94: /* lcmp */ {
	if (VM_Assembler.TRACE) asm.noteBytecode(biStart, "lcmp");  // a ? b
	asm.emitPOP_Reg(T0);        // the low half of value2
	asm.emitPOP_Reg(S0);        // the high half of value2
	asm.emitPOP_Reg(T1);        // the low half of value1
	asm.emitSUB_Reg_Reg(T1, T0);        // subtract the low half of value2 from
                                // low half of value1, result into T1
	asm.emitPOP_Reg(T0);        // the high half of value 1
	//  pop does not alter the carry register
	asm.emitSBB_Reg_Reg(T0, S0);        // subtract the high half of value2 plus
                                // borrow from the high half of value 1,
                                // result in T0
	asm.emitMOV_Reg_Imm(S0, -1);        // load -1 into S0
	VM_ForwardReference fr1 = asm.forwardJcc(asm.LT); // result negative --> branch to end
	asm.emitMOV_Reg_Imm(S0, 0);        // load 0 into S0
	asm.emitOR_Reg_Reg(T0, T1);        // result 0 
	VM_ForwardReference fr2 = asm.forwardJcc(asm.EQ); // result 0 --> branch to end
	asm.emitMOV_Reg_Imm(S0, 1);        // load 1 into S0
	fr1.resolve(asm);
	fr2.resolve(asm);
	asm.emitPUSH_Reg(S0);        // push result on stack
	break;
      }
      case 0x95: /* fcmpl !!- */ {
	if (VM_Assembler.TRACE) asm.noteBytecode(biStart, "fcmpl");
	VM_ForwardReference fr1,fr2,fr3;
	asm.emitFLD_Reg_RegDisp(FP0, SP, WORDSIZE);          // copy value1 into FPU
	asm.emitFLD_Reg_RegInd(FP0, SP);                        // copy value2 into FPU
	asm.emitADD_Reg_Imm(SP, 2*WORDSIZE);                // popping the stack
	if (VM.VerifyAssertions) VM.assert(S0 != EAX);                        // eax is used by FNSTSW
	asm.emitXOR_Reg_Reg(S0, S0);                        // S0 <- 0
	asm.emitFUCOMPP();                        // compare and pop FPU *2
	asm.emitFNSTSW();                     // move FPU flags into (E)AX
	asm.emitSAHF();                       // store AH into flags
	fr1 = asm.forwardJcc(asm.EQ);        // branch if ZF set (eq. or unord.)
	// ZF not set ->  neither equal nor unordered
	asm.emitMOV_Reg_Imm(S0, 1);                        // load 1 into S0
	fr2 = asm.forwardJcc(asm.LLT);        // branch if CF set (val2 < val1)
	asm.emitMOV_Reg_Imm(S0, -1);                        // load -1 into S0
	fr1.resolve(asm);                        // ZF set (equal or unordered)
	fr3 = asm.forwardJcc(asm.LGE);        // branch if CF not set (not unordered)
	asm.emitMOV_Reg_Imm(S0, -1);                        // load -1 into S0
	fr3.resolve(asm);
	fr2.resolve(asm);
	asm.emitPUSH_Reg(S0);                        // push result on stack
	break;
      }
      case 0x96: /* fcmpg !!- */ {
	if (VM_Assembler.TRACE) asm.noteBytecode(biStart, "fcmpg");
	VM_ForwardReference fr1,fr2,fr3;
	asm.emitFLD_Reg_RegDisp(FP0, SP, WORDSIZE);          // copy value1 into FPU
	asm.emitFLD_Reg_RegInd(FP0, SP);                        // copy value2 into FPU
	asm.emitADD_Reg_Imm(SP, 2*WORDSIZE);                // popping the stack
	if (VM.VerifyAssertions) VM.assert(S0 != EAX);                        // eax is used by FNSTSW
	asm.emitXOR_Reg_Reg(S0, S0);                        // S0 <- 0
	asm.emitFUCOMPP();                        // compare and pop FPU *2
	asm.emitFNSTSW();                     // move FPU flags into (E)AX
	asm.emitSAHF();                       // store AH into flags
	fr1 = asm.forwardJcc(asm.EQ);        // branch if ZF set (eq. or unord.)
	// ZF not set ->  neither equal nor unordered
	asm.emitMOV_Reg_Imm(S0, 1);                        // load 1 into S0
	fr2 = asm.forwardJcc(asm.LLT);        // branch if CF set (val2 < val1)
	asm.emitMOV_Reg_Imm(S0, -1);                        // load -1 into S0
	fr1.resolve(asm);                        // ZF set (equal or unordered)
	fr3 = asm.forwardJcc(asm.LGE);        // branch if CF not set (not unordered)
	asm.emitMOV_Reg_Imm(S0, 1);                        // load 1 into S0
	fr3.resolve(asm);
	fr2.resolve(asm);
	asm.emitPUSH_Reg(S0);                        // push result on stack
	break;
      }
      case 0x97: /* dcmpl !!- */ {
	if (VM_Assembler.TRACE) asm.noteBytecode(biStart, "dcmpl");
	VM_ForwardReference fr1,fr2,fr3;
	asm.emitFLD_Reg_RegDisp_Quad(FP0, SP, WORDSIZE*2);        // copy value1 into FPU
	asm.emitFLD_Reg_RegInd_Quad(FP0, SP);                        // copy value2 into FPU
	asm.emitADD_Reg_Imm(SP, 4*WORDSIZE);                // popping the stack
	if (VM.VerifyAssertions) VM.assert(S0 != EAX);                        // eax is used by FNSTSW
	asm.emitXOR_Reg_Reg(S0, S0);                        // S0 <- 0
	asm.emitFUCOMPP();                        // compare and pop FPU *2
	asm.emitFNSTSW();                     // move FPU flags into (E)AX
	asm.emitSAHF();                       // store AH into flags
	fr1 = asm.forwardJcc(asm.EQ);        // branch if ZF set (eq. or unord.)
	// ZF not set ->  neither equal nor unordered
	asm.emitMOV_Reg_Imm(S0, 1);                        // load 1 into S0
	fr2 = asm.forwardJcc(asm.LLT);        // branch if CF set (val2 < val1)
	asm.emitMOV_Reg_Imm(S0, -1);                        // load -1 into S0
	fr1.resolve(asm);                        // ZF set (equal or unordered)
	fr3 = asm.forwardJcc(asm.LGE);        // branch if CF not set (not unordered)
	asm.emitMOV_Reg_Imm(S0, -1);                        // load -1 into S0
	fr3.resolve(asm);
	fr2.resolve(asm);
	asm.emitPUSH_Reg(S0);                        // push result on stack
	break;
      }
      case 0x98: /* dcmpg !!- */ {
	if (VM_Assembler.TRACE) asm.noteBytecode(biStart, "dcmpg");
	VM_ForwardReference fr1,fr2,fr3;
	asm.emitFLD_Reg_RegDisp_Quad(FP0, SP, WORDSIZE*2);        // copy value1 into FPU
	asm.emitFLD_Reg_RegInd_Quad(FP0, SP);                        // copy value2 into FPU
	asm.emitADD_Reg_Imm(SP, 4*WORDSIZE);                // popping the stack
	if (VM.VerifyAssertions) VM.assert(S0 != EAX);                        // eax is used by FNSTSW
	asm.emitXOR_Reg_Reg(S0, S0);                        // S0 <- 0
	asm.emitFUCOMPP();                        // compare and pop FPU *2
	asm.emitFNSTSW();                     // move FPU flags into (E)AX
	asm.emitSAHF();                       // store AH into flags
	fr1 = asm.forwardJcc(asm.EQ);        // branch if ZF set (eq. or unord.)
	// ZF not set ->  neither equal nor unordered
	asm.emitMOV_Reg_Imm(S0, 1);                        // load 1 into S0
	fr2 = asm.forwardJcc(asm.LLT);        // branch if CF set (val2 < val1)
	asm.emitMOV_Reg_Imm(S0, -1);                        // load -1 into S0
	fr1.resolve(asm);                        // ZF set (equal or unordered)
	fr3 = asm.forwardJcc(asm.LGE);        // branch if CF not set (not unordered)
	asm.emitMOV_Reg_Imm(S0, 1);                        // load 1 into S0
	fr3.resolve(asm);
	fr2.resolve(asm);
	asm.emitPUSH_Reg(S0);                        // push result on stack
	break;
      }
      case 0x99: /* ifeq */ {
	int offset = fetch2BytesSigned();
	int bTarget = biStart + offset;
	int mTarget = bytecodeMap[bTarget];
	if (VM_Assembler.TRACE) asm.noteBytecode(biStart, "ifeq " + VM_Lister.decimal(offset) + " [" + VM_Lister.decimal(bTarget) + "] ");
	if (offset < 0) genThreadSwitchTest(VM_Thread.BACKEDGE);
	asm.emitPOP_Reg(T0);
	asm.emitCMP_Reg_Imm(T0, 0);
	asm.emitJCC_Cond_ImmOrLabel(asm.EQ, mTarget, bTarget);
	break;
      }
      case 0x9a: /* ifne */ {
	int offset = fetch2BytesSigned();
	int bTarget = biStart + offset;
	int mTarget = bytecodeMap[bTarget];
	if (VM_Assembler.TRACE) asm.noteBytecode(biStart, "ifne " + VM_Lister.decimal(offset) + " [" + VM_Lister.decimal(bTarget) + "] ");
	if (offset < 0) genThreadSwitchTest(VM_Thread.BACKEDGE);
	asm.emitPOP_Reg(T0);
	asm.emitCMP_Reg_Imm(T0, 0);
	asm.emitJCC_Cond_ImmOrLabel(asm.NE, mTarget, bTarget);
	break;
      }
      case 0x9b: /* iflt */ {
	int offset = fetch2BytesSigned();
	int bTarget = biStart + offset;
	int mTarget = bytecodeMap[bTarget];
	if (VM_Assembler.TRACE) asm.noteBytecode(biStart, "iflt " + VM_Lister.decimal(offset) + " [" + VM_Lister.decimal(bTarget) + "] ");
	if (offset < 0) genThreadSwitchTest(VM_Thread.BACKEDGE);
	asm.emitPOP_Reg(T0);
	asm.emitCMP_Reg_Imm(T0, 0);
	asm.emitJCC_Cond_ImmOrLabel(asm.LT, mTarget, bTarget);
	break;
      }
      case 0x9c: /* ifge */ {
	int offset = fetch2BytesSigned();
	int bTarget = biStart + offset;
	int mTarget = bytecodeMap[bTarget];
	if (VM_Assembler.TRACE) asm.noteBytecode(biStart, "ifge " + VM_Lister.decimal(offset) + " [" + VM_Lister.decimal(bTarget) + "] ");
	if (offset < 0) genThreadSwitchTest(VM_Thread.BACKEDGE);
	asm.emitPOP_Reg(T0);
	asm.emitCMP_Reg_Imm(T0, 0);
	asm.emitJCC_Cond_ImmOrLabel(asm.GE, mTarget, bTarget);
	break;
      }
      case 0x9d: /* ifgt */ {
	int offset = fetch2BytesSigned();
	int bTarget = biStart + offset;
	int mTarget = bytecodeMap[bTarget];
	if (VM_Assembler.TRACE) asm.noteBytecode(biStart, "ifgt " + VM_Lister.decimal(offset) + " [" + VM_Lister.decimal(bTarget) + "] ");
	if (offset < 0) genThreadSwitchTest(VM_Thread.BACKEDGE);
	asm.emitPOP_Reg(T0);
	asm.emitCMP_Reg_Imm(T0, 0);
	asm.emitJCC_Cond_ImmOrLabel(asm.GT, mTarget, bTarget);
	break;
      }
      case 0x9e: /* ifle */ {
	int offset = fetch2BytesSigned();
	int bTarget = biStart + offset;
	int mTarget = bytecodeMap[bTarget];
	if (VM_Assembler.TRACE) asm.noteBytecode(biStart, "ifle " + VM_Lister.decimal(offset) + " [" + VM_Lister.decimal(bTarget) + "] ");
	if (offset < 0) genThreadSwitchTest(VM_Thread.BACKEDGE);
	asm.emitPOP_Reg(T0);
	asm.emitCMP_Reg_Imm(T0, 0);
	asm.emitJCC_Cond_ImmOrLabel(asm.LE, mTarget, bTarget);
	break;
      }
      case 0x9f: /* if_icmpeq */ {
	int offset = fetch2BytesSigned();
	int bTarget = biStart + offset;
	int mTarget = bytecodeMap[bTarget];
	if (VM_Assembler.TRACE) asm.noteBytecode(biStart, "if_icmpeq " + VM_Lister.decimal(offset) + " [" + VM_Lister.decimal(bTarget) + "] ");
	if (offset < 0) genThreadSwitchTest(VM_Thread.BACKEDGE);
	asm.emitPOP_Reg(S0);
	asm.emitPOP_Reg(T0);
	asm.emitCMP_Reg_Reg(T0, S0);
	asm.emitJCC_Cond_ImmOrLabel(asm.EQ, mTarget, bTarget);
	break;
      }
      case 0xa0: /* if_icmpne */ {
	int offset = fetch2BytesSigned();
	int bTarget = biStart + offset;
	int mTarget = bytecodeMap[bTarget];
	if (VM_Assembler.TRACE) asm.noteBytecode(biStart, "if_icmpne " + VM_Lister.decimal(offset) + " [" + VM_Lister.decimal(bTarget) + "] ");
	if (offset < 0) genThreadSwitchTest(VM_Thread.BACKEDGE);
	asm.emitPOP_Reg(S0);
	asm.emitPOP_Reg(T0);
	asm.emitCMP_Reg_Reg(T0, S0);
	asm.emitJCC_Cond_ImmOrLabel(asm.NE, mTarget, bTarget);
	break;
      }
      case 0xa1: /* if_icmplt */ {
	// maria  backward brach test is TESTED
	int offset = fetch2BytesSigned();
	int bTarget = biStart + offset;
	int mTarget = bytecodeMap[bTarget];
	if (VM_Assembler.TRACE) asm.noteBytecode(biStart, "if_icmplt " + VM_Lister.decimal(offset) + " [" + VM_Lister.decimal(bTarget) + "] ");
	if (offset < 0) genThreadSwitchTest(VM_Thread.BACKEDGE);
	asm.emitPOP_Reg(S0);
	asm.emitPOP_Reg(T0);
	asm.emitCMP_Reg_Reg(T0, S0);
	asm.emitJCC_Cond_ImmOrLabel(asm.LT, mTarget, bTarget);
	break;
      }
      case 0xa2: /* if_icmpge */ {
	int offset = fetch2BytesSigned();
	int bTarget = biStart + offset;
	int mTarget = bytecodeMap[bTarget];
	if (VM_Assembler.TRACE) asm.noteBytecode(biStart, "if_icmpge " + VM_Lister.decimal(offset) + " [" + VM_Lister.decimal(bTarget) + "] ");
	if (offset < 0) genThreadSwitchTest(VM_Thread.BACKEDGE);
	asm.emitPOP_Reg(S0);
	asm.emitPOP_Reg(T0);
	asm.emitCMP_Reg_Reg(T0, S0);
	asm.emitJCC_Cond_ImmOrLabel(asm.GE, mTarget, bTarget);
	break;
      }
      case 0xa3: /* if_icmpgt */ {
	int offset = fetch2BytesSigned();
	int bTarget = biStart + offset;
	int mTarget = bytecodeMap[bTarget];
	if (VM_Assembler.TRACE) asm.noteBytecode(biStart, "if_icmpgt " + VM_Lister.decimal(offset) + " [" + VM_Lister.decimal(bTarget) + "] ");
	if (offset < 0) genThreadSwitchTest(VM_Thread.BACKEDGE);
	asm.emitPOP_Reg(S0);
	asm.emitPOP_Reg(T0);
	asm.emitCMP_Reg_Reg(T0, S0);
	asm.emitJCC_Cond_ImmOrLabel(asm.GT, mTarget, bTarget);
	break;
      }
      case 0xa4: /* if_icmple */ {
	int offset = fetch2BytesSigned();
	int bTarget = biStart + offset;
	int mTarget = bytecodeMap[bTarget];
	if (VM_Assembler.TRACE) asm.noteBytecode(biStart, "if_icmple " + VM_Lister.decimal(offset) + " [" + VM_Lister.decimal(bTarget) + "] ");
	if (offset < 0) genThreadSwitchTest(VM_Thread.BACKEDGE);
	asm.emitPOP_Reg(S0);
	asm.emitPOP_Reg(T0);
	asm.emitCMP_Reg_Reg(T0, S0);
	asm.emitJCC_Cond_ImmOrLabel(asm.LE, mTarget, bTarget);
	break;
      }
      case 0xa5: /* if_acmpeq */ {
	int offset = fetch2BytesSigned();
	int bTarget = biStart + offset;
	int mTarget = bytecodeMap[bTarget];
	if (VM_Assembler.TRACE) asm.noteBytecode(biStart, "if_acmpeq " + VM_Lister.decimal(offset) + " [" + VM_Lister.decimal(bTarget) + "] ");
	if (offset < 0) genThreadSwitchTest(VM_Thread.BACKEDGE);
	asm.emitPOP_Reg(S0);
	asm.emitPOP_Reg(T0);
	asm.emitCMP_Reg_Reg(T0, S0);
	asm.emitJCC_Cond_ImmOrLabel(asm.EQ, mTarget, bTarget);
	break;
      }
      case 0xa6: /* if_acmpne */ {
	int offset = fetch2BytesSigned();
	int bTarget = biStart + offset;
	int mTarget = bytecodeMap[bTarget];
	if (VM_Assembler.TRACE) asm.noteBytecode(biStart, "if_acmpne " + VM_Lister.decimal(offset) + " [" + VM_Lister.decimal(bTarget) + "] ");
	if (offset < 0) genThreadSwitchTest(VM_Thread.BACKEDGE);
	asm.emitPOP_Reg(S0);
	asm.emitPOP_Reg(T0);
	asm.emitCMP_Reg_Reg(T0, S0);
	asm.emitJCC_Cond_ImmOrLabel(asm.NE, mTarget, bTarget);
	break;
      }
      case 0xa7: /* goto */ {
	// unit test by IBack
	int offset = fetch2BytesSigned();
	int bTarget = biStart + offset; // bi has been bumped by 3 already
	int mTarget = bytecodeMap[bTarget];
	if (VM_Assembler.TRACE) asm.noteBytecode(biStart, "goto " + VM_Lister.decimal(offset) + " [" + VM_Lister.decimal(bTarget) + "] ");
	if (offset < 0) genThreadSwitchTest(VM_Thread.BACKEDGE);
	asm.emitJMP_ImmOrLabel(mTarget, bTarget);
	break;
      }
      case 0xa8: /* jsr */ {
	int offset = fetch2BytesSigned();
	int bTarget = biStart + offset;
	int mTarget = bytecodeMap[bTarget];
	if (VM_Assembler.TRACE) asm.noteBytecode(biStart, "jsr " + VM_Lister.decimal(offset) + " [" + VM_Lister.decimal(bTarget) + "] (" + VM_Lister.decimal(mTarget));
	asm.emitCALL_ImmOrLabel(mTarget, bTarget);
	break;
      }
      case 0xa9: /* ret */ {
	int index = fetch1ByteUnsigned();
	if (VM_Assembler.TRACE) asm.noteBytecode(biStart, "ret " + VM_Lister.decimal(index));
	int offset = localOffset(index);
	asm.emitJMP_RegDisp(FP, offset); 
	break;
      }
      case 0xaa: /* tableswitch */ {
	// unit test by Table
	bi = (bi+3) & -4; // eat padding
	int defaultval = fetch4BytesSigned();
	int bTarget = biStart + defaultval;
	int mTarget = bytecodeMap[bTarget];
	int low = fetch4BytesSigned();
	int high = fetch4BytesSigned();
	if (VM_Assembler.TRACE) asm.noteBytecode(biStart, "tableswitch [" + VM_Lister.decimal(low) + "--" + VM_Lister.decimal(high) + "] " + VM_Lister.decimal(defaultval));
	int n = high-low+1;                        // n = number of normal cases (0..n-1)
	asm.emitPOP_Reg (T0);                          // T0 is index of desired case
	asm.emitSUB_Reg_Imm(T0, low);                     // relativize T0
	asm.emitCMP_Reg_Imm(T0, n);                       // 0 <= relative index < n
	asm.emitJCC_Cond_ImmOrLabel (asm.LGE, mTarget, bTarget);   // if not, goto default case
	asm.emitCALL_Imm(asm.getMachineCodeIndex() + 5 + (n<<LG_WORDSIZE) ); 
	// jump around table, pushing address of 0th delta
	for (int i=0; i<n; i++) {                  // create table of deltas
	  int offset = fetch4BytesSigned();
	  bTarget = biStart + offset;
	  mTarget = bytecodeMap[bTarget];
	  // delta i: difference between address of case i and of delta 0
	  asm.emitOFFSET_Imm_ImmOrLabel(i, mTarget, bTarget );
	}
	asm.emitPOP_Reg (S0);                          // S0 = address of 0th delta 
	asm.emitADD_Reg_RegIdx (S0, S0, T0, asm.WORD, 0);     // S0 += [S0 + T0<<2]
	asm.emitPUSH_Reg(S0);                          // push computed case address
	asm.emitRET ();                            // goto case
	break;
      }
      case 0xab: /* lookupswitch */ {
	// unit test by Lookup
	bi = (bi+3) & -4; // eat padding
	int defaultval = fetch4BytesSigned();
	int npairs = fetch4BytesSigned();
	if (VM_Assembler.TRACE) asm.noteBytecode(biStart, "lookupswitch [<" + VM_Lister.decimal(npairs) + ">]" + VM_Lister.decimal(defaultval));
	asm.emitPOP_Reg(T0);
	for (int i=0; i<npairs; i++) {
	  int match   = fetch4BytesSigned();
	  asm.emitCMP_Reg_Imm(T0, match);
	  int offset  = fetch4BytesSigned();
	  int bTarget = biStart + offset;
	  int mTarget = bytecodeMap[bTarget];
	  asm.emitJCC_Cond_ImmOrLabel(asm.EQ, mTarget, bTarget);
	}
	int bTarget = biStart + defaultval;
	int mTarget = bytecodeMap[bTarget];
	asm.emitJMP_ImmOrLabel(mTarget, bTarget);
	// TODO replace linear search loop with binary search
	break;
      }
      case 0xac: /* ireturn */ {
	if (VM_Assembler.TRACE) asm.noteBytecode(biStart, "ireturn");
 	if (VM.UseEpilogueYieldPoints) genThreadSwitchTest(VM_Thread.EPILOGUE);
	if (method.isSynchronized()) genMonitorExit();
	asm.emitPOP_Reg(T0);
	genEpilogue(); 
	break;
      }
      case 0xad: /* lreturn */ {
	if (VM_Assembler.TRACE) asm.noteBytecode(biStart, "lreturn");
 	if (VM.UseEpilogueYieldPoints) genThreadSwitchTest(VM_Thread.EPILOGUE);
	if (method.isSynchronized()) genMonitorExit();
	asm.emitPOP_Reg(T1); // low half
	asm.emitPOP_Reg(T0); // high half
	genEpilogue();
	break;
      }
      case 0xae: /* freturn */ {
	if (VM_Assembler.TRACE) asm.noteBytecode(biStart, "freturn");
 	if (VM.UseEpilogueYieldPoints) genThreadSwitchTest(VM_Thread.EPILOGUE);
	if (method.isSynchronized()) genMonitorExit();
	asm.emitFLD_Reg_RegInd(FP0, SP);
	asm.emitADD_Reg_Imm(SP, WORDSIZE); // pop the stack
	genEpilogue();
	break;
      }
      case 0xaf: /* dreturn */ {
	if (VM_Assembler.TRACE) asm.noteBytecode(biStart, "dreturn");
 	if (VM.UseEpilogueYieldPoints) genThreadSwitchTest(VM_Thread.EPILOGUE);
	if (method.isSynchronized()) genMonitorExit();
	asm.emitFLD_Reg_RegInd_Quad(FP0, SP);
	asm.emitADD_Reg_Imm(SP, WORDSIZE<<1); // pop the stack
	genEpilogue();
	break;
      }
      case 0xb0: /* areturn */ {
	if (VM_Assembler.TRACE) asm.noteBytecode(biStart, "areturn");
 	if (VM.UseEpilogueYieldPoints) genThreadSwitchTest(VM_Thread.EPILOGUE);
	if (method.isSynchronized()) genMonitorExit();
	asm.emitPOP_Reg(T0);
	genEpilogue(); 
	break;
      }
      case 0xb1: /* return */ {
	if (VM_Assembler.TRACE) asm.noteBytecode(biStart, "return");
 	if (VM.UseEpilogueYieldPoints) genThreadSwitchTest(VM_Thread.EPILOGUE);
	if (method.isSynchronized()) genMonitorExit();
	genEpilogue(); 
	break;
      }
      case 0xb2: /* getstatic */ {
	int constantPoolIndex = fetch2BytesUnsigned();
	VM_Field fieldRef = klass.getFieldRef(constantPoolIndex);
	if (VM_Assembler.TRACE) asm.noteBytecode(biStart, "getstatic " + VM_Lister.decimal(constantPoolIndex)  + " (" + fieldRef + ")");
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
	  asm.emitMOV_Reg_Imm (T0, fieldRefClass.getDictionaryId());
	  asm.emitPUSH_Reg    (T0);
	  asm.emitCALL_RegDisp(JTOC, VM_Entrypoints.initializeClassIfNecessaryOffset);
	  classPreresolved = true;
	}
	if (fieldRef.needsDynamicLink(method) && !classPreresolved) {
	  if (VM.VerifyAssertions && VM.BuildForStrongVolatileSemantics) // Either VM.BuildForPrematureClassResolution was not set or the class was not found (these cases are not yet handled)
	    VM.assert(VM.NOT_REACHED); // TODO!! handle this case by emitting code that assumes the field is volatile
	  int offset = fieldRef.getDictionaryId()<<LG_WORDSIZE;  // offset of field in dictionary and in fieldOffsets
	  int retryLabel = asm.getMachineCodeIndex();            // branch here after dynamic class loading
	  asm.emitMOV_Reg_RegDisp (T0, JTOC, VM_Entrypoints.fieldOffsetsOffset);            // T0 is fieldOffsets table
	  asm.emitMOV_Reg_RegDisp (T0, T0, offset);                          // T0 is offset in JTOC of static field, or 0 if field's class isn't loaded
	  asm.emitCMP_Reg_Imm (T0, 0);                                   // T0 ?= 0, is field's class loaded?
	  VM_ForwardReference fr = asm.forwardJcc(asm.NE);       // if so, skip 3 instructions
	  int classId = fieldRef.getDeclaringClass().getDictionaryId();
	  asm.emitPUSH_Imm(classId);                                 // pass an indirect pointer to field's class
	  genParameterRegisterLoad(1);                           // pass 1 parameter word
	  asm.emitCALL_RegDisp(JTOC, VM_Entrypoints.loadClassOnDemandOffset);           // load field's class
	  asm.emitJMP_Imm (retryLabel);                           // reload T0
	  fr.resolve(asm);                                       // comefrom
	  if (fieldRef.getSize() == 4) { // field is one word
	    asm.emitPUSH_RegIdx (JTOC, T0, asm.BYTE, 0);        // get static field
	  } else { // field is two words (double or long)
	    if (VM.VerifyAssertions) VM.assert(fieldRef.getSize() == 8);
	    // TODO!! use 8-byte move if possible
	    asm.emitPUSH_RegIdx (JTOC, T0, asm.BYTE, WORDSIZE); // get high part
	    asm.emitPUSH_RegIdx (JTOC, T0, asm.BYTE, 0);        // get low part
	  }
	} else {
          fieldRef = fieldRef.resolve();
	  int fieldOffset = fieldRef.getOffset();
	  if (fieldRef.getSize() == 4) { // field is one word
	    asm.emitPUSH_RegDisp(JTOC, fieldOffset);
	  } else { // field is two words (double or long)
	    if (VM.VerifyAssertions) VM.assert(fieldRef.getSize() == 8);
            if (fieldRef.isVolatile() && VM.BuildForStrongVolatileSemantics) {
	      asm.emitMOV_Reg_RegDisp (T0, JTOC, VM_Entrypoints.doublewordVolatileMutexOffset);
	      asm.emitPUSH_Reg        (T0);
	      asm.emitMOV_Reg_RegDisp (S0, T0, OBJECT_TIB_OFFSET);
	      asm.emitCALL_RegDisp    (S0, VM_Entrypoints.processorLockOffset);
	    }
	    // TODO!! use 8-byte move if possible
	    asm.emitPUSH_RegDisp(JTOC, fieldOffset+WORDSIZE); // get high part
	    asm.emitPUSH_RegDisp(JTOC, fieldOffset);          // get low part
            if (fieldRef.isVolatile() && VM.BuildForStrongVolatileSemantics) {
	      asm.emitMOV_Reg_RegDisp (T0, JTOC, VM_Entrypoints.doublewordVolatileMutexOffset);
	      asm.emitPUSH_Reg        (T0);
	      asm.emitMOV_Reg_RegDisp (S0, T0, OBJECT_TIB_OFFSET);
	      asm.emitCALL_RegDisp    (S0, VM_Entrypoints.processorUnlockOffset);
	    }
	  }
	}
	break;
      }
      case 0xb3: /* putstatic */ {
	int constantPoolIndex = fetch2BytesUnsigned();
	int fieldId = klass.getFieldRefId(constantPoolIndex);
	VM_Field fieldRef = VM_FieldDictionary.getValue(fieldId);
	if (VM_Assembler.TRACE) asm.noteBytecode(biStart, "putstatic " + VM_Lister.decimal(constantPoolIndex) + " (" + fieldRef + ")");
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
	  asm.emitMOV_Reg_Imm (T0, fieldRefClass.getDictionaryId());
	  asm.emitPUSH_Reg    (T0);
	  asm.emitCALL_RegDisp(JTOC, VM_Entrypoints.initializeClassIfNecessaryOffset);
	  classPreresolved = true;
	}
	if (fieldRef.needsDynamicLink(method) && !classPreresolved) {
	  if (VM.VerifyAssertions && VM.BuildForStrongVolatileSemantics) // Either VM.BuildForPrematureClassResolution was not set or the class was not found (these cases are not yet handled)
	    VM.assert(VM.NOT_REACHED); // TODO!! handle this case by emitting code that assumes the field is volatile
	  int offset = fieldRef.getDictionaryId()<<LG_WORDSIZE;  // offset of field in dictionary and in fieldOffsets
	  int retryLabel = asm.getMachineCodeIndex();            // branch here, after dynamic class loading
	  asm.emitMOV_Reg_RegDisp (T0, JTOC, VM_Entrypoints.fieldOffsetsOffset);            // T0 is fieldOffsets table
	  asm.emitMOV_Reg_RegDisp (T0, T0, offset);                          // T0 is offset in JTOC of static field, or 0 if field's class isn't loaded
	  asm.emitCMP_Reg_Imm (T0, 0);                                   // T0 ?= 0, is field's class loaded?
	  VM_ForwardReference fr = asm.forwardJcc(asm.NE);       // if so, skip 3 instructions
	  int classId = fieldRef.getDeclaringClass().getDictionaryId();
	  asm.emitPUSH_Imm(classId);                                 // pass an indirect pointer to field's class
	  genParameterRegisterLoad(1);                           // pass 1 parameter word
	  asm.emitCALL_RegDisp(JTOC, VM_Entrypoints.loadClassOnDemandOffset);           // load field's class
	  asm.emitJMP_Imm (retryLabel);                           // reload T0
	  fr.resolve(asm);                                       // comefrom
	  if (fieldRef.getSize() == 4) { // field is one word
	    asm.emitPOP_RegIdx(JTOC, T0, asm.BYTE, 0);
	  } else { // field is two words (double or long)
	    if (VM.VerifyAssertions) VM.assert(fieldRef.getSize() == 8);
	    // TODO!! use 8-byte move if possible
	    asm.emitPOP_RegIdx(JTOC, T0, asm.BYTE, 0);        // store low part
	    asm.emitPOP_RegIdx(JTOC, T0, asm.BYTE, WORDSIZE); // store high part
	  }
	} else {
          fieldRef = fieldRef.resolve();
	  int fieldOffset = fieldRef.getOffset();
	  if (fieldRef.getSize() == 4) { // field is one word
	    asm.emitPOP_RegDisp(JTOC, fieldOffset);
	  } else { // field is two words (double or long)
	    if (VM.VerifyAssertions) VM.assert(fieldRef.getSize() == 8);
	    if (fieldRef.isVolatile() && VM.BuildForStrongVolatileSemantics) {
	      asm.emitMOV_Reg_RegDisp (T0, JTOC, VM_Entrypoints.doublewordVolatileMutexOffset);
	      asm.emitPUSH_Reg        (T0);
	      asm.emitMOV_Reg_RegDisp (S0, T0, OBJECT_TIB_OFFSET);
	      asm.emitCALL_RegDisp    (S0, VM_Entrypoints.processorLockOffset);
	    }
	    // TODO!! use 8-byte move if possible
	    asm.emitPOP_RegDisp(JTOC, fieldOffset);          // store low part
	    asm.emitPOP_RegDisp(JTOC, fieldOffset+WORDSIZE); // store high part
            if (fieldRef.isVolatile() && VM.BuildForStrongVolatileSemantics) {
	      asm.emitMOV_Reg_RegDisp (T0, JTOC, VM_Entrypoints.doublewordVolatileMutexOffset);
	      asm.emitPUSH_Reg        (T0);
	      asm.emitMOV_Reg_RegDisp (S0, T0, OBJECT_TIB_OFFSET);
	      asm.emitCALL_RegDisp    (S0, VM_Entrypoints.processorUnlockOffset);
	    }
	  }
	}
	break;
      }
      case 0xb4: /* getfield */ {
	int constantPoolIndex = fetch2BytesUnsigned();
	VM_Field fieldRef = klass.getFieldRef(constantPoolIndex);
	if (VM_Assembler.TRACE) asm.noteBytecode(biStart, "getfield " + VM_Lister.decimal(constantPoolIndex)  + " (" + fieldRef + ")");
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
	    VM.assert(VM.NOT_REACHED); // TODO!! handle this case by emitting code that assumes the field is volatile
	  int offset = fieldRef.getDictionaryId()<<LG_WORDSIZE;  // offset of field in dictionary and in fieldOffsets
	  int retryLabel = asm.getMachineCodeIndex();            // branch here after dynamic class loading
	  asm.emitMOV_Reg_RegDisp (T0, JTOC, VM_Entrypoints.fieldOffsetsOffset);            // T0 is fieldOffsets table
	  asm.emitMOV_Reg_RegDisp (T0, T0, offset);                          // T0 is offset in JTOC of static field, or 0 if field's class isn't loaded
	  asm.emitCMP_Reg_Imm (T0, 0);                                   // T0 ?= 0, is field's class loaded?
	  VM_ForwardReference fr = asm.forwardJcc(asm.NE);       // if so, skip 3 instructions
	  int classId = fieldRef.getDeclaringClass().getDictionaryId();
	  asm.emitPUSH_Imm(classId);                                 // pass an indirect pointer to field's class
	  genParameterRegisterLoad(1);                           // pass 1 parameter word
	  asm.emitCALL_RegDisp(JTOC, VM_Entrypoints.loadClassOnDemandOffset);           // load field's class
	  asm.emitJMP_Imm (retryLabel);                           // reload T0
	  fr.resolve(asm);                                       // comefrom
	  if (fieldRef.getSize() == 4) { // field is one word
	    asm.emitPOP_Reg (S0);
	    asm.emitPUSH_RegIdx(S0, T0, asm.BYTE, 0);
	  } else { // field is two words (double or long)
	    if (VM.VerifyAssertions) VM.assert(fieldRef.getSize() == 8);
	    // TODO!! use 8-byte move if possible 
	    asm.emitPOP_Reg (S0);
	    asm.emitPUSH_RegIdx(S0, T0, asm.BYTE, WORDSIZE);                // get high part
	    asm.emitPUSH_RegIdx(S0, T0, asm.BYTE, 0);          // get low part
	  }
	} else {
          fieldRef = fieldRef.resolve();
	  int fieldOffset = fieldRef.getOffset();
	  if (fieldRef.getSize() == 4) { // field is one word
	    asm.emitPOP_Reg (T0);
	    asm.emitPUSH_RegDisp(T0, fieldOffset);
	  } else { // field is two words (double or long)
	    if (VM.VerifyAssertions) VM.assert(fieldRef.getSize() == 8);
	    if (fieldRef.isVolatile() && VM.BuildForStrongVolatileSemantics) {
	      asm.emitMOV_Reg_RegDisp (T0, JTOC, VM_Entrypoints.doublewordVolatileMutexOffset);
	      asm.emitPUSH_Reg        (T0);
	      asm.emitMOV_Reg_RegDisp (S0, T0, OBJECT_TIB_OFFSET);
	      asm.emitCALL_RegDisp    (S0, VM_Entrypoints.processorLockOffset);
	    }
	    // TODO!! use 8-byte move if possible
	    asm.emitPOP_Reg (T0);
	    asm.emitPUSH_RegDisp(T0, fieldOffset+WORDSIZE); // get high part
	    asm.emitPUSH_RegDisp(T0, fieldOffset);          // get low part
            if (fieldRef.isVolatile() && VM.BuildForStrongVolatileSemantics) {
	      asm.emitMOV_Reg_RegDisp (T0, JTOC, VM_Entrypoints.doublewordVolatileMutexOffset);
	      asm.emitPUSH_Reg        (T0);
	      asm.emitMOV_Reg_RegDisp (S0, T0, OBJECT_TIB_OFFSET);
	      asm.emitCALL_RegDisp    (S0, VM_Entrypoints.processorUnlockOffset);
	    }
	  }
	}
	break;
      }
      case 0xb5: /* putfield */ {
	int constantPoolIndex = fetch2BytesUnsigned();
	int fieldId = klass.getFieldRefId(constantPoolIndex);
	VM_Field fieldRef = VM_FieldDictionary.getValue(fieldId);
	if (VM_Assembler.TRACE) asm.noteBytecode(biStart, "putfield " + VM_Lister.decimal(constantPoolIndex) + " (" + fieldRef + ")");
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
	    VM.assert(VM.NOT_REACHED); // TODO!! handle this case by emitting code that assumes the field is volatile
	  int offset = fieldRef.getDictionaryId()<<LG_WORDSIZE;                  // offset of field in dictionary and in fieldOffsets
	  int retryLabel = asm.getMachineCodeIndex();                            // branch here, after dynamic class loading
	  asm.emitMOV_Reg_RegDisp (T0, JTOC, VM_Entrypoints.fieldOffsetsOffset); // T0 is fieldOffsets table
	  asm.emitMOV_Reg_RegDisp (T0, T0, offset);                              // T0 is offset in JTOC of static field, or 0 if field's class isn't loaded
	  asm.emitCMP_Reg_Imm (T0, 0);                                           // T0 ?= 0, is field's class loaded?
	  VM_ForwardReference fr = asm.forwardJcc(asm.NE);                       // if so, skip 3 instructions
	  int classId = fieldRef.getDeclaringClass().getDictionaryId();
	  asm.emitPUSH_Imm(classId);                                             // pass an indirect pointer to field's class
	  genParameterRegisterLoad(1);                                           // pass 1 parameter word
	  asm.emitCALL_RegDisp(JTOC, VM_Entrypoints.loadClassOnDemandOffset);                   // load field's class
	  asm.emitJMP_Imm (retryLabel);                                          // reload T0
	  fr.resolve(asm);                                                       // comefrom
	  if (fieldRef.getSize() == 4) {// field is one word
	    asm.emitPOP_Reg (T1);                                                // value
	    asm.emitPOP_Reg (S0);                                                // obj's ref
	    asm.emitMOV_RegIdx_Reg (S0, T0, asm.BYTE, 0, T1);                    // [S0+T0] <- T1
	  } else { // field is two words (double or long)
	    if (VM.VerifyAssertions) VM.assert(fieldRef.getSize() == 8);
	    // TODO!! use 8-byte move if possible
	    asm.emitPOP_Reg (JTOC);                                              // low part of value- use JTOC as scratch register
	    asm.emitPOP_Reg (T1);                                                // high part of value
	    asm.emitPOP_Reg (S0);                                                // obj's ref
	    asm.emitMOV_RegIdx_Reg (S0, T0, asm.BYTE, 0, JTOC);                  // store low part
	    asm.emitMOV_RegIdx_Reg (S0, T0, asm.BYTE, WORDSIZE, T1);             // store high part
	    asm.emitMOV_Reg_RegDisp (JTOC, PR, VM_Entrypoints.jtocOffset);                      // restore JTOC register
	  }
	} else {
          fieldRef = fieldRef.resolve();
	  int fieldOffset = fieldRef.getOffset();
	  if (fieldRef.getSize() == 4) { // field is one word
	    asm.emitPOP_Reg (T0);                                                // value
	    asm.emitPOP_Reg (S0);                                                // obj's ref
	    asm.emitMOV_RegDisp_Reg (S0, fieldOffset, T0);                       // [S0+fieldOffset] <- T0
	  } else { // field is two words (double or long)
	    if (VM.VerifyAssertions) VM.assert(fieldRef.getSize() == 8);
	    if (fieldRef.isVolatile() && VM.BuildForStrongVolatileSemantics) {
	      asm.emitMOV_Reg_RegDisp (T0, JTOC, VM_Entrypoints.doublewordVolatileMutexOffset);
	      asm.emitPUSH_Reg        (T0);
	      asm.emitMOV_Reg_RegDisp (S0, T0, OBJECT_TIB_OFFSET);
	      asm.emitCALL_RegDisp    (S0, VM_Entrypoints.processorLockOffset);
	    }
	    // TODO!! use 8-byte move if possible
	    asm.emitPOP_Reg (T0);                                                // low part of value
	    asm.emitPOP_Reg (T1);                                                // high part of value
	    asm.emitPOP_Reg (S0);                                                // obj's ref
	    asm.emitMOV_RegDisp_Reg (S0, fieldOffset, T0);                       // store low part
	    asm.emitMOV_RegDisp_Reg (S0, fieldOffset+WORDSIZE, T1);              // store high part
            if (fieldRef.isVolatile() && VM.BuildForStrongVolatileSemantics) {
	      asm.emitMOV_Reg_RegDisp (T0, JTOC, VM_Entrypoints.doublewordVolatileMutexOffset);
	      asm.emitPUSH_Reg        (T0);
	      asm.emitMOV_Reg_RegDisp (S0, T0, OBJECT_TIB_OFFSET);
	      asm.emitCALL_RegDisp    (S0, VM_Entrypoints.processorUnlockOffset);
	    }
	  }
	}
	break;
      }  
      case 0xb6: /* invokevirtual */ {
	int constantPoolIndex = fetch2BytesUnsigned();
	VM_Method methodRef = klass.getMethodRef(constantPoolIndex);
	if (VM_Assembler.TRACE) asm.noteBytecode(biStart, "invokevirtual " + VM_Lister.decimal(constantPoolIndex) + " (" + methodRef + ")");
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
	  int offset = methodRef.getDictionaryId()<<LG_WORDSIZE; // offset of method in dictionary and in methodOffsets
	  int retryLabel = asm.getMachineCodeIndex();            // branch here, after dynamic class loading
	  asm.emitMOV_Reg_RegDisp (T0, JTOC, VM_Entrypoints.methodOffsetsOffset);           // T0 is methodOffsets table
	  asm.emitMOV_Reg_RegDisp (T0, T0, offset);                          // T0 is offset in TIB of virtual method, or 0
	  asm.emitCMP_Reg_Imm (T0, 0);                                   // T0 ?= 0, is method's class loaded?
	  VM_ForwardReference fr = asm.forwardJcc(asm.NE);       // if so, skip
	  int classId = methodRef.getDeclaringClass().getDictionaryId();
	  asm.emitPUSH_Imm(classId);                                 // pass an indirect pointer to field's class
	  genParameterRegisterLoad(1);                           // pass 1 parameter word
	  asm.emitCALL_RegDisp(JTOC, VM_Entrypoints.loadClassOnDemandOffset);           // load field's class
	  asm.emitJMP_Imm (retryLabel);                           // reload T0
	  fr.resolve(asm);                                       // comefrom
	  int methodRefparameterWords = methodRef.getParameterWords() + 1; // +1 for "this" parameter
	  int objectOffset = (methodRefparameterWords << 2) - 4;           // object offset into stack
	  asm.emitMOV_Reg_RegDisp (S0, SP, objectOffset);                    // S0 has "this" parameter
	  asm.emitMOV_Reg_RegDisp (S0, S0, OBJECT_TIB_OFFSET);               // S0 has TIB
	  asm.emitMOV_Reg_RegIdx (S0, S0, T0, asm.BYTE, 0);                 // S0 has address of virtual method
	  genParameterRegisterLoad(methodRef, true);
	  asm.emitCALL_Reg(S0);                                      // call virtual method
	  genResultRegisterUnload(methodRef);                    // push return value, if any
	} else {
          methodRef = methodRef.resolve();
	  int methodRefparameterWords = methodRef.getParameterWords() + 1; // +1 for "this" parameter
	  int methodRefOffset = methodRef.getOffset();
	  int objectOffset = (methodRefparameterWords << 2) - WORDSIZE; // object offset into stack
	  asm.emitMOV_Reg_RegDisp (S0, SP, objectOffset);
	  asm.emitMOV_Reg_RegDisp (S0, S0, OBJECT_TIB_OFFSET);
	  genParameterRegisterLoad(methodRef, true);
	  asm.emitCALL_RegDisp(S0, methodRefOffset);
	  genResultRegisterUnload(methodRef);
	}
	break;
      }
      case 0xb7: /* invokespecial */ {
	int constantPoolIndex = fetch2BytesUnsigned();
	VM_Method methodRef = klass.getMethodRef(constantPoolIndex);
	if (VM_Assembler.TRACE) asm.noteBytecode(biStart, "invokespecial " + VM_Lister.decimal(constantPoolIndex) + " (" + methodRef + ")");
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
	  if (VM.VerifyAssertions) VM.assert(target.isObjectInitializer() || !target.isStatic());
	  if (target.isObjectInitializer()) {
	    genParameterRegisterLoad(methodRef, true);
	    asm.emitCALL_RegDisp(JTOC, target.getOffset());
	    genResultRegisterUnload(target);
	  } else {
	    if (VM.VerifyAssertions) VM.assert(!target.isStatic());
	    // invoke via class's tib slot
	    int methodRefOffset = target.getOffset();
	    asm.emitMOV_Reg_RegDisp (S0, JTOC, target.getDeclaringClass().getTibOffset());
	    genParameterRegisterLoad(methodRef, true);
	    asm.emitCALL_RegDisp(S0, methodRefOffset);
	    genResultRegisterUnload(methodRef);
	  }
	} else {
	  // VM.sysWrite("dynamic link from " + method + " to " + methodRef + " [special]\n");
	  int offset = methodRef.getDictionaryId()<<LG_WORDSIZE;                  // offset of method in dictionary and in methodOffsets
	  int retryLabel = asm.getMachineCodeIndex();                             // branch here, after dynamic class loading
	  asm.emitMOV_Reg_RegDisp (S0, JTOC, VM_Entrypoints.methodOffsetsOffset); // S0 is methodOffsets table
	  asm.emitMOV_Reg_RegDisp (S0, S0, offset);                               // S0 is offset in JTOC of static method, or 0
	  asm.emitCMP_Reg_Imm (S0, 0);                                            // S0 ?= 0, is method's class loaded?
	  VM_ForwardReference fr = asm.forwardJcc(asm.NE);                        // if so, skip
	  int classId = methodRef.getDeclaringClass().getDictionaryId();
	  asm.emitPUSH_Imm(classId);                                              // pass an indirect pointer to field's class
	  genParameterRegisterLoad(1);                                            // pass 1 parameter word
	  asm.emitCALL_RegDisp(JTOC, VM_Entrypoints.loadClassOnDemandOffset);                    // load field's class
	  asm.emitJMP_Imm (retryLabel);                                           // reload S0
	  fr.resolve(asm);                                                        // comefrom
	  genParameterRegisterLoad(methodRef, true);
	  asm.emitCALL_RegIdx(JTOC, S0, asm.BYTE, 0);                             // call static method
	  genResultRegisterUnload(methodRef);                                     // push return value, if any
	}     
	break;
      }
      case 0xb8: /* invokestatic */ {
	int constantPoolIndex = fetch2BytesUnsigned();
	VM_Method methodRef = klass.getMethodRef(constantPoolIndex);
	if (VM_Assembler.TRACE) asm.noteBytecode(biStart, "invokestatic " + VM_Lister.decimal(constantPoolIndex) + " (" + methodRef + ")");
	if (methodRef.getDeclaringClass().isMagicType()) {
	  genMagic(methodRef);
	  break;
	}
	boolean classPreresolved = false;
	VM_Class methodRefClass = methodRef.getDeclaringClass();
	if (methodRef.needsDynamicLink(method) && VM.BuildForPrematureClassResolution) {
	  try {
	    methodRefClass.load();
	    methodRefClass.resolve();
	    classPreresolved = true;
	  } catch (Exception e) { // report the exception at runtime
	    VM.sysWrite("WARNING: during compilation of " + method + " premature resolution of " + methodRefClass + " provoked the following exception: " + e); // TODO!! remove this  warning message
	  }
	}
	if (VM.BuildForPrematureClassResolution &&
	    !methodRefClass.isInitialized() &&
	    !(methodRefClass == klass) &&
	    !(methodRefClass.isInBootImage() && VM.writingBootImage)
	    ) { // TODO!! rearrange the following code to backpatch after the first call
	  asm.emitMOV_Reg_Imm (T0, methodRefClass.getDictionaryId());
	  asm.emitPUSH_Reg    (T0);
	  asm.emitCALL_RegDisp(JTOC, VM_Entrypoints.initializeClassIfNecessaryOffset);
	  classPreresolved = true;
	}
	if (methodRef.needsDynamicLink(method) && !classPreresolved) {
	  int offset = methodRef.getDictionaryId()<<LG_WORDSIZE;                  // offset of method in dictionary and in methodOffsets
	  int retryLabel = asm.getMachineCodeIndex();                             // branch here, after dynamic class loading
	  asm.emitMOV_Reg_RegDisp (S0, JTOC, VM_Entrypoints.methodOffsetsOffset); // S0 is methodOffsets table
	  asm.emitMOV_Reg_RegDisp (S0, S0, offset);                               // S0 is offset in JTOC of static method, or 0
	  asm.emitCMP_Reg_Imm (S0, 0);                                            // S0 ?= 0, is method's class loaded?
	  VM_ForwardReference fr = asm.forwardJcc(asm.NE);                        // if so, skip
	  int classId = methodRef.getDeclaringClass().getDictionaryId();
	  asm.emitPUSH_Imm(classId);                                              // pass an indirect pointer to field's class
	  genParameterRegisterLoad(1);                                            // pass 1 parameter word
	  asm.emitCALL_RegDisp(JTOC, VM_Entrypoints.loadClassOnDemandOffset);     // load field's class
	  asm.emitJMP_Imm (retryLabel);                                           // reload S0
	  fr.resolve(asm);                                                        // comefrom
	  genParameterRegisterLoad(methodRef, false);          
	  asm.emitCALL_RegIdx(JTOC, S0, asm.BYTE, 0);                             // call static method
	  genResultRegisterUnload(methodRef);                                     // push return value, if any
	} else {
          methodRef = methodRef.resolve();
	  int methodOffset = methodRef.getOffset();
	  genParameterRegisterLoad(methodRef, false);
	  asm.emitCALL_RegDisp(JTOC, methodOffset);
	  genResultRegisterUnload(methodRef);
	}
	break;
      }
      case 0xb9: /* invokeinterface --- */ {
	int constantPoolIndex = fetch2BytesUnsigned();
	VM_Method methodRef = klass.getMethodRef(constantPoolIndex);
	int count = fetch1ByteUnsigned();
	fetch1ByteSigned(); // eat superfluous 0
	if (VM_Assembler.TRACE) asm.noteBytecode(biStart, "invokeinterface " + VM_Lister.decimal(constantPoolIndex) + " (" + methodRef + ") " + VM_Lister.decimal(count) + " 0");

	// (1) Emit dynamic type checking sequence if required to do so inline.
	if (VM.BuildForIMTInterfaceInvocation || 
	    (VM.BuildForITableInterfaceInvocation && VM.DirectlyIndexedITables)) {
	  VM_Method resolvedMethodRef = null;
	  try {
	    resolvedMethodRef = methodRef.resolveInterfaceMethod(false);
	  } catch (VM_ResolutionException e) {
	    // actually can't be thrown when we pass false for canLoad.
	  }
	  if (resolvedMethodRef == null) {
	    // might be a ghost ref. Call uncommon case typechecking routine to deal with this
	    asm.emitMOV_Reg_RegDisp (S0, SP, (count-1) << 2);                       // "this" object
	    asm.emitPUSH_Imm(methodRef.getDictionaryId());                          // dict id of target
	    asm.emitPUSH_RegDisp(S0, OBJECT_TIB_OFFSET);                            // tib of "this" object
	    genParameterRegisterLoad(2);                                            // pass 2 parameter word
	    asm.emitCALL_RegDisp(JTOC, VM_Entrypoints.unresolvedInterfaceMethodOffset);// check that "this" class implements the interface
	  } else {
	    asm.emitMOV_Reg_RegDisp (T0, JTOC, methodRef.getDeclaringClass().getTibOffset()); // tib of the interface method
	    asm.emitMOV_Reg_RegDisp (S0, SP, (count-1) << 2);                                 // "this" object
	    asm.emitPUSH_RegDisp(T0, TIB_TYPE_INDEX << 2);                                // type of the interface method
	    asm.emitPUSH_RegDisp(S0, OBJECT_TIB_OFFSET);                                  // tib of "this" object
	    genParameterRegisterLoad(2);                                          // pass 2 parameter word
	    asm.emitCALL_RegDisp(JTOC, VM_Entrypoints.mandatoryInstanceOfInterfaceOffset);// check that "this" class implements the interface
	  }
	}

	// (2) Emit interface invocation sequence.
	if (VM.BuildForIMTInterfaceInvocation) {
	  int signatureId = VM_ClassLoader.findOrCreateInterfaceMethodSignatureId(methodRef.getName(), methodRef.getDescriptor());
	  int offset      = VM_InterfaceMethodSignature.getOffset(signatureId);
	  asm.emitMOV_RegDisp_Imm (PR, VM_Entrypoints.hiddenSignatureIdOffset, signatureId); // squirrel away signature ID
	  asm.emitMOV_Reg_RegDisp (S0, SP, (count-1) << 2);                                  // "this" object
	  asm.emitMOV_Reg_RegDisp (S0, S0, OBJECT_TIB_OFFSET);                               // tib of "this" object
          if (VM.BuildForIndirectIMT) {
            // Load the IMT Base into S0
            asm.emitMOV_Reg_RegDisp(S0, S0, TIB_IMT_TIB_INDEX << 2);
          }
	  genParameterRegisterLoad(methodRef, true);
	  asm.emitCALL_RegDisp(S0, offset);                                             // the interface call
	} else if (VM.BuildForITableInterfaceInvocation && 
		   VM.DirectlyIndexedITables && 
		   methodRef.getDeclaringClass().isResolved()) {
	  methodRef = methodRef.resolve();
	  VM_Class I = methodRef.getDeclaringClass();
	  asm.emitMOV_Reg_RegDisp (S0, SP, (count-1) << 2);                                 // "this" object
	  asm.emitMOV_Reg_RegDisp (S0, S0, OBJECT_TIB_OFFSET);                              // tib of "this" object
	  asm.emitMOV_Reg_RegDisp (S0, S0, TIB_ITABLES_TIB_INDEX << 2);                     // iTables
	  asm.emitMOV_Reg_RegDisp (S0, S0, I.getInterfaceId() << 2);                        // iTable
	  genParameterRegisterLoad(methodRef, true);
	  asm.emitCALL_RegDisp(S0, I.getITableIndex(methodRef) << 2);                       // the interface call
	} else {
	  // call "invokeInterface" to resolve object + method id into method address
	  int methodRefId = klass.getMethodRefId(constantPoolIndex);
	  asm.emitPUSH_RegDisp(SP, (count-1)<<LG_WORDSIZE);  // "this" parameter is obj
	  asm.emitPUSH_Imm(methodRefId);                 // id of method to call
	  genParameterRegisterLoad(2);               // pass 2 parameter words
	  asm.emitCALL_RegDisp(JTOC,  VM_Entrypoints.invokeInterfaceOffset); // invokeinterface(obj, id) returns address to call
	  asm.emitMOV_Reg_Reg (S0, T0);                      // S0 has address of method
	  genParameterRegisterLoad(methodRef, true);
	  asm.emitCALL_Reg(S0);                          // the interface method (its parameters are on stack)
	}
	genResultRegisterUnload(methodRef);
	break;
      }
      case 0xba: /* unused */ {
	if (VM_Assembler.TRACE) asm.noteBytecode(biStart, "unused");
	if (VM.VerifyAssertions) VM.assert(VM.NOT_REACHED);
	break;
      }
      case 0xbb: /* new */ {
	// unit test by NullCompare
	int constantPoolIndex = fetch2BytesUnsigned();
	VM_Type typeRef = klass.getTypeRef(constantPoolIndex);
	if (VM_Assembler.TRACE) asm.noteBytecode(biStart, "new " + VM_Lister.decimal(constantPoolIndex) + " (" + typeRef + ")");
	if (typeRef.isInitialized() || ((VM_Class) typeRef).isInBootImage()) { // call quick allocator
	  VM_Class newclass = (VM_Class) typeRef;
	  int instanceSize = newclass.getInstanceSize();
	  int tibOffset = newclass.getOffset();
	  asm.emitPUSH_Imm(instanceSize);            
	  asm.emitPUSH_RegDisp (JTOC, tibOffset);        // put tib on stack    
	  asm.emitPUSH_Imm(newclass.hasFinalizer()?1:0); // does the class have a finalizer?
	  genParameterRegisterLoad(3);                   // pass 3 parameter words
	  asm.emitCALL_RegDisp (JTOC, VM_Entrypoints.quickNewScalarOffset);
	  asm.emitPUSH_Reg (T0);
	} else { // call regular allocator (someday backpatch?)
	  asm.emitPUSH_Imm(klass.getTypeRefId(constantPoolIndex));            
	  genParameterRegisterLoad(1);           // pass 1 parameter word
	  asm.emitCALL_RegDisp (JTOC, VM_Entrypoints.newScalarOffset);
	  asm.emitPUSH_Reg (T0);
	}
	break;
      }
      case 0xbc: /* newarray */ {
	// unit test by PArray
	int atype = fetch1ByteSigned();
	VM_Array array = VM_Array.getPrimitiveArrayType(atype);
	array.resolve();
	array.instantiate();
	if (VM_Assembler.TRACE) asm.noteBytecode(biStart, "newarray " + VM_Lister.decimal(atype) + "(" + array + ")");
	int width      = array.getLogElementSize();
	int tibOffset  = array.getOffset();
	int headerSize = VM.ARRAY_HEADER_SIZE;
	// count is already on stack- nothing required
	asm.emitMOV_Reg_RegInd (T0, SP);               // get number of elements
	asm.emitSHL_Reg_Imm (T0, width);              // compute array size
	asm.emitADD_Reg_Imm(T0, headerSize);
	asm.emitPUSH_Reg(T0);      
	asm.emitPUSH_RegDisp(JTOC, tibOffset);        // put tib on stack    
	genParameterRegisterLoad(3);          // pass 3 parameter words
	asm.emitCALL_RegDisp(JTOC, VM_Entrypoints.quickNewArrayOffset);
	asm.emitPUSH_Reg(T0);
	break;
      }
      case 0xbd: /* anewarray */ {
	int constantPoolIndex = fetch2BytesUnsigned();
	VM_Type elementTypeRef = klass.getTypeRef(constantPoolIndex);
	VM_Array array = elementTypeRef.getArrayTypeForElementType();
	// TODO!! Forcing early class loading may violate language spec.  FIX ME!!
	array.load();
	array.resolve();
	array.instantiate();
	if (VM_Assembler.TRACE) asm.noteBytecode(biStart, "anewarray new " + VM_Lister.decimal(constantPoolIndex) + " (" + array + ")");
	int width      = array.getLogElementSize();
	int tibOffset  = array.getOffset();
	int headerSize = VM.ARRAY_HEADER_SIZE;
	// count is already on stack- nothing required
	asm.emitMOV_Reg_RegInd (T0, SP);               // get number of elements
	asm.emitSHL_Reg_Imm (T0, width);              // compute array size
	asm.emitADD_Reg_Imm(T0, headerSize);
	asm.emitPUSH_Reg(T0);            
	asm.emitPUSH_RegDisp(JTOC, tibOffset);        // put tib on stack    
	genParameterRegisterLoad(3);          // pass 3 parameter words  
	asm.emitCALL_RegDisp(JTOC, VM_Entrypoints.quickNewArrayOffset);
	asm.emitPUSH_Reg(T0);
	break;
      }
      case 0xbe: /* arraylength */ { 
	// unit test by PArray
	if (VM_Assembler.TRACE) asm.noteBytecode(biStart, "arraylength");
	asm.emitPOP_Reg (T0);                        // array reference
	asm.emitPUSH_RegDisp(T0, ARRAY_LENGTH_OFFSET);
	break;
      }
      case 0xbf: /* athrow */ {
	if (VM_Assembler.TRACE) asm.noteBytecode(biStart, "athrow");  
 	if (VM.UseEpilogueYieldPoints) genThreadSwitchTest(VM_Thread.EPILOGUE);
	genParameterRegisterLoad(1);          // pass 1 parameter word
	asm.emitCALL_RegDisp(JTOC, VM_Entrypoints.athrowOffset);
	break;
      }
      case 0xc0: /* checkcast */ {
	int constantPoolIndex = fetch2BytesUnsigned();
	VM_Type typeRef = klass.getTypeRef(constantPoolIndex);
	if (VM_Assembler.TRACE) asm.noteBytecode(biStart, "checkcast " + VM_Lister.decimal(constantPoolIndex) + " (" + typeRef + ")");
	asm.emitPUSH_RegInd (SP);                                // duplicate the object ref on the stack
	asm.emitPUSH_Imm(typeRef.getTibOffset());                // JTOC index that identifies klass  
	genParameterRegisterLoad(2);                         // pass 2 parameter words
	asm.emitCALL_RegDisp (JTOC, VM_Entrypoints.checkcastOffset); // checkcast(obj, klass-identifier)
	break;
      }
      case 0xc1: /* instanceof */ {
	int constantPoolIndex = fetch2BytesUnsigned();
	VM_Type typeRef = klass.getTypeRef(constantPoolIndex);
	if (VM_Assembler.TRACE) asm.noteBytecode(biStart, "instanceof " + VM_Lister.decimal(constantPoolIndex)  + " (" + typeRef + ")");
	int offset = VM_Entrypoints.instanceOfOffset;
	if (typeRef.isClassType() && typeRef.asClass().isLoaded() && typeRef.asClass().isFinal()) {
	  offset = VM_Entrypoints.instanceOfFinalOffset;
	} else if (typeRef.isArrayType() && typeRef.asArray().getElementType().isPrimitiveType()) {
	  offset = VM_Entrypoints.instanceOfFinalOffset;
	}
	asm.emitPUSH_Imm(typeRef.getTibOffset());  
	genParameterRegisterLoad(2);          // pass 2 parameter words
	asm.emitCALL_RegDisp(JTOC, offset);
	asm.emitPUSH_Reg(T0);
	break;
      }
      case 0xc2: /* monitorenter  */ {
	if (VM_Assembler.TRACE) asm.noteBytecode(biStart, "monitorenter");  
	genParameterRegisterLoad(1);          // pass 1 parameter word
	asm.emitCALL_RegDisp(JTOC, VM_Entrypoints.lockOffset);
	break;
      }
      case 0xc3: /* monitorexit */ {
	if (VM_Assembler.TRACE) asm.noteBytecode(biStart, "monitorexit"); 
	genParameterRegisterLoad(1);          // pass 1 parameter word
	asm.emitCALL_RegDisp(JTOC, VM_Entrypoints.unlockOffset);  
	break;
      }
      case 0xc4: /* wide */ {
	int widecode = fetch1ByteUnsigned();
	int index = fetch2BytesUnsigned();
	switch (widecode) {
	case 0x15: /* --- wide iload --- */ {
	  if (VM_Assembler.TRACE) asm.noteBytecode(biStart, "wide iload " + VM_Lister.decimal(index));
	  int offset = localOffset(index);
	  asm.emitPUSH_RegDisp(FP,offset);
	  break;
	}
	case 0x16: /* --- wide lload --- */ {
	  if (VM_Assembler.TRACE) asm.noteBytecode(biStart, "wide lload " + VM_Lister.decimal(index));
	  int offset = localOffset(index) - 4;
	  asm.emitPUSH_RegDisp(FP, offset+4);  // high part
	  asm.emitPUSH_RegDisp(FP, offset);    //  low part
	  break;
	}
	case 0x17: /* --- wide fload --- */ {
	  if (VM_Assembler.TRACE) asm.noteBytecode(biStart, "wide fload " + VM_Lister.decimal(index));
	  int offset = localOffset(index);
	  asm.emitPUSH_RegDisp (FP, offset);
	  break;
	}
	case 0x18: /* --- wide dload --- */ {
	  if (VM_Assembler.TRACE) asm.noteBytecode(biStart, "wide dload " + VM_Lister.decimal(index));
	  int offset = localOffset(index) - 4;
	  asm.emitPUSH_RegDisp(FP, offset+4);  // high part
	  asm.emitPUSH_RegDisp(FP, offset);    //  low part
	  break;
	}
	case 0x19: /* --- wide aload --- */ {
	  if (VM_Assembler.TRACE) asm.noteBytecode(biStart, "wide aload " + VM_Lister.decimal(index));
	  int offset = localOffset(index);
	  asm.emitPUSH_RegDisp(FP, offset);
	  break;
	}
	case 0x36: /* --- wide istore --- */ {
	  if (VM_Assembler.TRACE) asm.noteBytecode(biStart, "wide istore " + VM_Lister.decimal(index));
	  int offset = localOffset(index);
	  asm.emitPOP_RegDisp (FP, offset);
	  break;
	}
	case 0x37: /* --- wide lstore --- */ {
	  if (VM_Assembler.TRACE) asm.noteBytecode(biStart, "wide lstore " + VM_Lister.decimal(index));
	  int offset = localOffset(index)-4;
	  asm.emitPOP_RegDisp (FP, offset);   // store low half of long
	  asm.emitPOP_RegDisp (FP, offset+4); // store high half
	  break;
	}
	case 0x38: /* --- wide fstore --- */ {
	  if (VM_Assembler.TRACE) asm.noteBytecode(biStart, "wide fstore " + VM_Lister.decimal(index));
	  int offset = localOffset(index);
	  asm.emitPOP_RegDisp (FP, offset);
	  break;
	}
	case 0x39: /* --- wide dstore --- */ {
	  if (VM_Assembler.TRACE) asm.noteBytecode(biStart, "wide dstore " + VM_Lister.decimal(index));
	  int offset = localOffset(index)-4;
	  asm.emitPOP_RegDisp (FP, offset);   // store low half of double
	  asm.emitPOP_RegDisp (FP, offset+WORDSIZE); // store high half
	  break;
	}
	case 0x3a: /* --- wide astore --- */ {
	  if (VM_Assembler.TRACE) asm.noteBytecode(biStart, "wide astore " + VM_Lister.decimal(index));
	  int offset = localOffset(index);
	  asm.emitPOP_RegDisp (FP, offset);
	  break;
	}
	case 0x84: /* --- wide iinc --- */ {
	  int val = fetch2BytesSigned();
	  if (VM_Assembler.TRACE) asm.noteBytecode(biStart, "wide inc " + VM_Lister.decimal(index) + " by " + VM_Lister.decimal(val));
	  int offset = localOffset(index);
	  asm.emitADD_RegDisp_Imm(FP, offset, val);
	  break;
	}
	case 0x9a: /* --- wide ret --- */ {
	  if (VM_Assembler.TRACE) asm.noteBytecode(biStart, "wide ret " + VM_Lister.decimal(index));
	  int offset = localOffset(index);
	  asm.emitJMP_RegDisp(FP, offset); 
	  break;
	}
	default:
	  if (VM.VerifyAssertions) VM.assert(VM.NOT_REACHED);
	}
	break;
      }
      case 0xc5: /* multianewarray */ {
	int constantPoolIndex = fetch2BytesUnsigned();
	int dimensions        = fetch1ByteUnsigned();
	VM_Type typeRef       = klass.getTypeRef(constantPoolIndex);
	int dictionaryId      = klass.getTypeRefId(constantPoolIndex);
	if (VM_Assembler.TRACE) asm.noteBytecode(biStart, "multianewarray " + VM_Lister.decimal(constantPoolIndex) + " (" + typeRef + ") " + VM_Lister.decimal(dimensions));
	// setup parameters for newarrayarray routine
	asm.emitPUSH_Imm (dimensions);                     // dimension of arays
	asm.emitPUSH_Imm (dictionaryId);                   // type of array elements               
	asm.emitPUSH_Imm ((dimensions + 5)<<LG_WORDSIZE);  // offset to dimensions from FP on entry to newarray 
	// NOTE: 5 extra words- 3 for parameters, 1 for return address on stack, 1 for code technique in VM_Linker
	genParameterRegisterLoad(3);                   // pass 3 parameter words
	asm.emitCALL_RegDisp(JTOC, VM_Entrypoints.newArrayArrayOffset); 
	for (int i = 0; i < dimensions ; i++) asm.emitPOP_Reg(S0); // clear stack of dimensions
	asm.emitPUSH_Reg(T0);                              // push array ref on stack
	break;
      }
      case 0xc6: /* ifnull */ {
	// unit test by NullCompare
	int offset = fetch2BytesSigned();
	int bTarget = biStart + offset;
	int mTarget = bytecodeMap[bTarget];
	if (VM_Assembler.TRACE) asm.noteBytecode(biStart, "ifnull " + VM_Lister.decimal(offset) + " [" + VM_Lister.decimal(bTarget) + "] ");
	if (offset < 0) genThreadSwitchTest(VM_Thread.BACKEDGE);
	asm.emitPOP_Reg(T0);
	asm.emitCMP_Reg_Imm(T0, 0);
	asm.emitJCC_Cond_ImmOrLabel(asm.EQ, mTarget, bTarget);
	break;
      }
      case 0xc7: /* ifnonnull */ {
	// unit test by NullCompare
	int offset = fetch2BytesSigned();
	int bTarget = biStart + offset;
	int mTarget = bytecodeMap[bTarget];
	if (VM_Assembler.TRACE) asm.noteBytecode(biStart, "ifnonnull " + VM_Lister.decimal(offset) + " [" + VM_Lister.decimal(bTarget) + "] ");
	if (offset < 0) genThreadSwitchTest(VM_Thread.BACKEDGE);
	asm.emitPOP_Reg(T0);
	asm.emitCMP_Reg_Imm(T0, 0);
	asm.emitJCC_Cond_ImmOrLabel(asm.NE, mTarget, bTarget);
	break;
      }
      case 0xc8: /* goto_w */ {
	int offset = fetch4BytesSigned();
	int bTarget = biStart + offset; // bi has been bumped by 5 already
	int mTarget = bytecodeMap[bTarget];
	if (VM_Assembler.TRACE) asm.noteBytecode(biStart, "goto_w " + VM_Lister.decimal(offset) + " [" + VM_Lister.decimal(bTarget) + "] ");
	if(offset < 0) genThreadSwitchTest(VM_Thread.BACKEDGE);
	asm.emitJMP_ImmOrLabel(mTarget, bTarget);
	break;
      }
      case 0xc9: /* jsr_w */ {
	int offset = fetch4BytesSigned();
	int bTarget = biStart + offset;
	int mTarget = bytecodeMap[bTarget];
	if (VM_Assembler.TRACE) asm.noteBytecode(biStart, "jsr_w " + VM_Lister.decimal(offset) + " [" + VM_Lister.decimal(bTarget) + "] ");
	asm.emitCALL_ImmOrLabel(mTarget, bTarget);
	break;
      }
      default:
	VM.sysWrite("VM_Compiler: unexpected bytecode: " + VM_Lister.hex(code) + "\n");
	if (VM.VerifyAssertions) VM.assert(VM.NOT_REACHED);
      }
    }
    return new VM_MachineCode(asm.getMachineCodes(), bytecodeMap);
  }
  
  private final void genPrologue (int cmid) {
    if (asm.TRACE) asm.comment("prologue for " + method);
    /* paramaters are on the stack and/or in registers;  There is space
     * on the stack for all the paramaters;  Parameter slots in the
     * stack are such that the first paramater has the higher address,
     * i.e., it pushed below all the other paramaters;  The return
     * address is the topmost entry on the stack.  The frame pointer
     * still addresses the previous frame.
     * The first word of the header, currently addressed by the stack
     * pointer, contains the return address.
     */

    /* establish a new frame:
     * push the caller's frame pointer in the stack, and
     * reset the frame pointer to the current stack top,
     * ie, the frame pointer addresses directly the word
     * that contains the previous frame pointer.
     * The second word of the header contains the frame
     * point of the caller.
     * The third word of the header contains the compiled method id of the called method.
     */
    asm.emitPUSH_Reg       (FP);			 // store caller's frame pointer
    asm.emitMOV_Reg_Reg    (FP, SP);			 // establish new frame
    asm.emitMOV_RegDisp_Imm(FP, STACKFRAME_METHOD_ID_OFFSET, cmid);	// 3rd word of header
    asm.emitMOV_RegDisp_Reg(PR, VM_Entrypoints.framePointerOffset, FP); // so hardware trap handler can always find it (opt compiler will reuse FP register)

    // save registers
    asm.emitMOV_RegDisp_Reg (FP, JTOC_SAVE_OFFSET, JTOC);          // save nonvolatile JTOC register
    asm.emitMOV_Reg_RegDisp (JTOC, PR, VM_Entrypoints.jtocOffset); // establish JTOC register
    int savedRegistersSize   = SAVED_GPRS<<LG_WORDSIZE;	// default
    /* handle "dynamic brige" methods:
     * save all registers except FP, SP, PR, S0 (scratch), and
     * JTOC saved above.
     */
    if (klass.isDynamicBridge()) {
      savedRegistersSize += 3 << LG_WORDSIZE;
      asm.emitMOV_RegDisp_Reg (FP, T0_SAVE_OFFSET,  T0); 
      asm.emitMOV_RegDisp_Reg (FP, T1_SAVE_OFFSET,  T1); 
      asm.emitMOV_RegDisp_Reg (FP, EBX_SAVE_OFFSET, EBX); 
      asm.emitFNSAVE_RegDisp(FP, FPU_SAVE_OFFSET);
      savedRegistersSize += FPU_STATE_SIZE;
    } else if (klass.isBridgeFromNative()) {
	savedRegistersSize = VM_JNICompiler.SAVED_GPRS_FOR_JNI<<LG_WORDSIZE;
    }          

    // copy registers to callee's stackframe
    firstLocalOffset         = STACKFRAME_BODY_OFFSET - savedRegistersSize;
    int firstParameterOffset = (parameterWords << LG_WORDSIZE) + WORDSIZE;
    genParameterCopy(firstParameterOffset, firstLocalOffset);

    int emptyStackOffset = firstLocalOffset - (method.getLocalWords() << LG_WORDSIZE) + WORDSIZE;
    asm.emitADD_Reg_Imm (SP, emptyStackOffset);		// set aside room for non parameter locals
    /*
     * generate stacklimit check
     */
    if (klass.isInterruptible()) {
      asm.emitMOV_Reg_RegDisp (S0, PR, VM_Entrypoints.activeThreadOffset);	// S0<-thd. @
      asm.emitMOV_Reg_RegDisp (S0, S0, VM_Entrypoints.stackLimitOffset);	// S0<-limit
      asm.emitSUB_Reg_Reg (S0, SP);                                   	// space left
      asm.emitADD_Reg_Imm (S0, method.getOperandWords() << LG_WORDSIZE); 	// space left after this expression stack
      VM_ForwardReference fr = asm.forwardJcc(asm.LT);	// Jmp around trap if OK
      asm.emitINT_Imm ( VM_Runtime.TRAP_STACK_OVERFLOW + RVM_TRAP_BASE );	// trap
      fr.resolve(asm);
    } else {
      // TODO!! make sure stackframe of uninterruptible method doesn't overflow guard page
    }

    if (method.isSynchronized()) genMonitorEnter();

    genThreadSwitchTest(VM_Thread.PROLOGUE);

    asm.emitNOP();                                      // mark end of prologue for JDP
  }
  
  private final void genEpilogue () {
    if (klass.isBridgeFromNative()) {
      // pop locals and parameters, get to saved GPR's
      asm.emitADD_Reg_Imm(SP, (this.method.getLocalWords() << LG_WORDSIZE));
      VM_JNICompiler.generateEpilogForJNIMethod(asm, this.method);
      return;
    }

    if (klass.isDynamicBridge()) {
      // Restore non-volatile registers. 
      asm.emitMOV_Reg_RegDisp (EBX, FP, EBX_SAVE_OFFSET); 

      // don't restore the return paramater :)
      // and don't restore the volatiles

      // restore FPU state
      asm.emitFRSTOR_RegDisp(FP, FPU_SAVE_OFFSET);
    }

    asm.emitMOV_Reg_RegDisp (JTOC, FP, JTOC_SAVE_OFFSET);// restore nonvolatile JTOC register
    asm.emitLEAVE();				// discard current stack frame
    asm.emitMOV_RegDisp_Reg(PR, VM_Entrypoints.framePointerOffset, FP); // so hardware trap handler can always find it (opt compiler will reuse FP register)
    asm.emitRET_Imm(parameterWords << LG_WORDSIZE);	 // return to caller- pop parameters from stack
  }
   
  private final void genMonitorEnter () {
    if (method.isStatic()) {
      klass.getClassForType(); 			                   // force java.lang.Class to get loaded int klass.classForType
      int tibOffset = klass.getTibOffset();
      asm.emitMOV_Reg_RegDisp (T0, JTOC, tibOffset);	           // T0 = tib for klass
      asm.emitMOV_Reg_RegInd (T0, T0);		                   // T0 = VM_Class for klass
      asm.emitPUSH_RegDisp(T0, VM_Entrypoints.classForTypeOffset); // push java.lang.Class object for klass
    } else {
      asm.emitPUSH_RegDisp(FP, localOffset(0));	                   // push "this" object
    }
    genParameterRegisterLoad(1);			           // pass 1 parameter
    asm.emitCALL_RegDisp(JTOC, VM_Entrypoints.lockOffset);  
    lockOffset = asm.getMachineCodeIndex() - 1;                    // after this instruction, the method has the monitor
  }
  
  private final void genMonitorExit () {
    if (method.isStatic()) {
      int tibOffset = klass.getTibOffset();
      asm.emitMOV_Reg_RegDisp (T0, JTOC, tibOffset);                   // T0 = tib for klass
      asm.emitMOV_Reg_RegInd (T0, T0);                             // T0 = VM_Class for klass
      asm.emitPUSH_RegDisp(T0, VM_Entrypoints.classForTypeOffset); // push java.lang.Class object for klass
    } else {
      asm.emitPUSH_RegDisp(FP, localOffset(0));                    // push "this" object
    }
    genParameterRegisterLoad(1); // pass 1 parameter
    asm.emitCALL_RegDisp(JTOC, VM_Entrypoints.unlockOffset);  
  }
  
  private final void genBoundsCheck (VM_Assembler asm, byte indexReg, byte arrayRefReg ) { 
    asm.emitCMP_RegDisp_Reg(arrayRefReg, ARRAY_LENGTH_OFFSET, indexReg);  // compare index to array length
    VM_ForwardReference fr = asm.forwardJcc(asm.LGT);                     // Jmp around trap if index is OK
    asm.emitMOV_RegDisp_Reg(PR, VM_Entrypoints.arrayIndexTrapParamOffset, indexReg); // "pass" index param to C trap handler
    asm.emitINT_Imm(VM_Runtime.TRAP_ARRAY_BOUNDS + RVM_TRAP_BASE );	  // trap
    fr.resolve(asm);
  }

  /** 
   * Copy a single floating-point double parameter from operand stack into fp register stack.
   * Assumption: method to be called has exactly one parameter.
   * Assumption: parameter type is double.
   * Assumption: parameter is on top of stack.
   * Also, this method is only called before generation of a call
   * to doubleToInt() or doubleToLong()
   */
  private final void genParameterRegisterLoad () {
    if (0 < NUM_PARAMETER_FPRS) {
      asm.emitFLD_Reg_RegInd_Quad(FP0, SP);
    }
  }
   
  /** 
   * Copy parameters from operand stack into registers.
   * Assumption: parameters are layed out on the stack in order
   * with SP pointing to the last parameter.
   * Also, this method is called before the generation of a "helper" method call.
   * Assumption: no floating-point parameters.
   * @param params number of parameter words (including "this" if any).
   */
  private final void genParameterRegisterLoad (int params) {
    if (VM.VerifyAssertions) VM.assert(0 < params);
    if (0 < NUM_PARAMETER_GPRS) {
      asm.emitMOV_Reg_RegDisp(T0, SP, (params-1) << LG_WORDSIZE);
    }
    if (1 < params && 1 < NUM_PARAMETER_GPRS) {
      asm.emitMOV_Reg_RegDisp(T1, SP, (params-2) << LG_WORDSIZE);
    }
  }
   
  /** 
   * Copy parameters from operand stack into registers.
   * Assumption: parameters are layed out on the stack in order
   * with SP pointing to the last parameter.
   * Also, this method is called before the generation of an explicit method call.
   * @param method is the method to be called.
   * @param hasThisParameter is the method virtual?
   */
  private final void genParameterRegisterLoad (VM_Method method, boolean hasThisParam) {
    int max = NUM_PARAMETER_GPRS + NUM_PARAMETER_FPRS;
    if (max == 0) return; // quit looking when all registers are full
    int gpr = 0;  // number of general purpose registers filled
    int fpr = 0;  // number of floating point  registers filled
    byte  T = T0; // next GPR to get a parameter
    int params = method.getParameterWords() + (hasThisParam ? 1 : 0);
    int offset = (params-1) << LG_WORDSIZE; // stack offset of first parameter word
    if (hasThisParam) {
      if (gpr < NUM_PARAMETER_GPRS) {
	asm.emitMOV_Reg_RegDisp(T, SP, offset);
	T = T1; // at most 2 parameters can be passed in general purpose registers
	gpr++;
	max--;
      }
      offset -= WORDSIZE;
    }
    VM_Type [] types = method.getParameterTypes();
    for (int i=0; i<types.length; i++) {
      if (max == 0) return; // quit looking when all registers are full
      VM_Type t = types[i];
      if (t.isLongType()) {
        if (gpr < NUM_PARAMETER_GPRS) {
	  asm.emitMOV_Reg_RegDisp(T, SP, offset); // lo register := hi mem (== hi order word)
	  T = T1; // at most 2 parameters can be passed in general purpose registers
	  gpr++;
	  max--;
	  if (gpr < NUM_PARAMETER_GPRS) {
	    asm.emitMOV_Reg_RegDisp(T, SP, offset - WORDSIZE);  // hi register := lo mem (== lo order word)
	    gpr++;
	    max--;
	  }
	}
	offset -= 2*WORDSIZE;
      } else if (t.isFloatType()) {
        if (fpr < NUM_PARAMETER_FPRS) {
	  asm.emitFLD_Reg_RegDisp(FP0, SP, offset);
	  fpr++;
	  max--;
	}
	offset -= WORDSIZE;
      } else if (t.isDoubleType()) {
        if (fpr < NUM_PARAMETER_FPRS) {
	  asm.emitFLD_Reg_RegDisp_Quad(FP0, SP, offset - WORDSIZE);
	  fpr++;
	  max--;
	}
	offset -= 2*WORDSIZE;
      } else { // t is object, int, short, char, byte, or boolean
        if (gpr < NUM_PARAMETER_GPRS) {
	  asm.emitMOV_Reg_RegDisp(T, SP, offset);
	  T = T1; // at most 2 parameters can be passed in general purpose registers
	  gpr++;
	  max--;
	}
	offset -= WORDSIZE;
      }
    }
    if (VM.VerifyAssertions) VM.assert(offset == - WORDSIZE);
  }
   
  /** 
   * Store parameters into local space of the callee's stackframe.
   * Taken: srcOffset - offset from frame pointer of first parameter in caller's stackframe.
   *        dstOffset - offset from frame pointer of first local in callee's stackframe
   * Assumption: although some parameters may be passed in registers,
   * space for all parameters is layed out in order on the caller's stackframe.
   */
  private final void genParameterCopy (int srcOffset, int dstOffset) {
    int gpr = 0;  // number of general purpose registers unloaded
    int fpr = 0;  // number of floating point registers unloaded
    byte  T = T0; // next GPR to get a parameter
    if (!method.isStatic()) { // handle "this" parameter
      if (gpr < NUM_PARAMETER_GPRS) {
	asm.emitMOV_RegDisp_Reg(FP, dstOffset, T);
	T = T1; // at most 2 parameters can be passed in general purpose registers
	gpr++;
      } else { // no parameters passed in registers
	asm.emitMOV_Reg_RegDisp(S0, FP, srcOffset);
	asm.emitMOV_RegDisp_Reg(FP, dstOffset, S0);
      }
      srcOffset -= WORDSIZE;
      dstOffset -= WORDSIZE;
    }
    VM_Type [] types     = method.getParameterTypes();
    int     [] fprOffset = new     int [NUM_PARAMETER_FPRS]; // to handle floating point parameters in registers
    boolean [] is32bit   = new boolean [NUM_PARAMETER_FPRS]; // to handle floating point parameters in registers
    for (int i=0; i<types.length; i++) {
      VM_Type t = types[i];
      if (t.isLongType()) {
        if (gpr < NUM_PARAMETER_GPRS) {
	  asm.emitMOV_RegDisp_Reg(FP, dstOffset, T);    // hi mem := lo register (== hi order word)
	  T = T1;                                       // at most 2 parameters can be passed in general purpose registers
	  gpr++;
	  srcOffset -= WORDSIZE;
	  dstOffset -= WORDSIZE;
	  if (gpr < NUM_PARAMETER_GPRS) {
	    asm.emitMOV_RegDisp_Reg(SP, dstOffset, T);  // lo mem := hi register (== lo order word)
	    gpr++;
	  } else {
	    asm.emitMOV_Reg_RegDisp(S0, FP, srcOffset); // lo mem from caller's stackframe
	    asm.emitMOV_RegDisp_Reg(FP, dstOffset, S0);
	  }
	} else {
	  asm.emitMOV_Reg_RegDisp(S0, FP, srcOffset);   // hi mem from caller's stackframe
	  asm.emitMOV_RegDisp_Reg(FP, dstOffset, S0);
	  srcOffset -= WORDSIZE;
	  dstOffset -= WORDSIZE;
	  asm.emitMOV_Reg_RegDisp(S0, FP, srcOffset);   // lo mem from caller's stackframe
	  asm.emitMOV_RegDisp_Reg(FP, dstOffset, S0);
	}
	srcOffset -= WORDSIZE;
	dstOffset -= WORDSIZE;
      } else if (t.isFloatType()) {
        if (fpr < NUM_PARAMETER_FPRS) {
	  fprOffset[fpr] = dstOffset;
	  is32bit[fpr]   = true;
	  fpr++;
	} else {
	  asm.emitMOV_Reg_RegDisp(S0, FP, srcOffset);
	  asm.emitMOV_RegDisp_Reg(FP, dstOffset, S0);
	}
	srcOffset -= WORDSIZE;
	dstOffset -= WORDSIZE;
      } else if (t.isDoubleType()) {
        if (fpr < NUM_PARAMETER_FPRS) {
	  srcOffset -= WORDSIZE;
	  dstOffset -= WORDSIZE;
	  fprOffset[fpr] = dstOffset;
	  is32bit[fpr]   = false;
	  fpr++;
	} else {
	  asm.emitMOV_Reg_RegDisp(S0, FP, srcOffset);   // hi mem from caller's stackframe
	  asm.emitMOV_RegDisp_Reg(FP, dstOffset, S0);
	  srcOffset -= WORDSIZE;
	  dstOffset -= WORDSIZE;
	  asm.emitMOV_Reg_RegDisp(S0, FP, srcOffset);   // lo mem from caller's stackframe
	  asm.emitMOV_RegDisp_Reg(FP, dstOffset, S0);
	}
	srcOffset -= WORDSIZE;
	dstOffset -= WORDSIZE;
      } else { // t is object, int, short, char, byte, or boolean
        if (gpr < NUM_PARAMETER_GPRS) {
	  asm.emitMOV_RegDisp_Reg(SP, dstOffset, T);
	  T = T1; // at most 2 parameters can be passed in general purpose registers
	  gpr++;
	} else {
	  asm.emitMOV_Reg_RegDisp(S0, FP, srcOffset);
	  asm.emitMOV_RegDisp_Reg(FP, dstOffset, S0);
	}
	srcOffset -= WORDSIZE;
	dstOffset -= WORDSIZE;
      }
    }
    for (int i=fpr-1; 0<=i; i--) { // unload the floating point register stack (backwards)
      if (is32bit[i]) {
	asm.emitFSTP_RegDisp_Reg(FP, fprOffset[i], FP0);
      } else {
	asm.emitFSTP_RegDisp_Reg_Quad(FP, fprOffset[i], FP0);
      }
    }
  }
   
  /** 
   * Push return value of method from register to operand stack.
   */
  private final void genResultRegisterUnload (VM_Method method) {
    VM_Type t = method.getReturnType();
    if (t.isVoidType()) return;
    if (t.isLongType()) {
      asm.emitPUSH_Reg(T0); // high half
      asm.emitPUSH_Reg(T1); // low half
    } else if (t.isFloatType()) {
      asm.emitSUB_Reg_Imm  (SP, 4);
      asm.emitFSTP_RegInd_Reg(SP, FP0);
    } else if (t.isDoubleType()) {
      asm.emitSUB_Reg_Imm  (SP, 8);
      asm.emitFSTP_RegInd_Reg_Quad(SP, FP0);
    } else { // t is object, int, short, char, byte, or boolean
      asm.emitPUSH_Reg(T0);
    }
  }
  
  /**
   * @param whereFrom is this thread switch from a PROLOGUE, BACKEDGE, or
   *       EPILOGUE?
   */
  private final void genThreadSwitchTest (int whereFrom) {
    if (!klass.isInterruptible()) {
      return;
    } else if (VM.BuildForDeterministicThreadSwitching) {
      asm.emitDEC_RegDisp(PR, VM_Entrypoints.deterministicThreadSwitchCountOffset);                          // 0 == count-- ??
      VM_ForwardReference fr1 = asm.forwardJcc(asm.EQ);                  // if not, skip
      asm.emitMOV_RegDisp_Imm(PR, VM_Entrypoints.deterministicThreadSwitchCountOffset, THREAD_SWITCH_LIMIT);     // reset count
      if (whereFrom == VM_Thread.PROLOGUE) {
        asm.emitCALL_RegDisp(JTOC, VM_Entrypoints.threadSwitchFromPrologueOffset); 
      } else if (whereFrom == VM_Thread.BACKEDGE) {
        asm.emitCALL_RegDisp(JTOC, VM_Entrypoints.threadSwitchFromBackedgeOffset); 
      } else { // EPILOGUE
        asm.emitCALL_RegDisp(JTOC, VM_Entrypoints.threadSwitchFromEpilogueOffset); 
      }
      fr1.resolve(asm);
    } else {
      asm.emitCMP_RegDisp_Imm(PR, VM_Entrypoints.threadSwitchRequestedOffset, 0);    // thread switch requested ??
      VM_ForwardReference fr1 = asm.forwardJcc(asm.EQ);                    // if not, skip
      if (whereFrom == VM_Thread.PROLOGUE) {
        asm.emitCALL_RegDisp(JTOC, VM_Entrypoints.threadSwitchFromPrologueOffset); 
      } else if (whereFrom == VM_Thread.BACKEDGE) {
        asm.emitCALL_RegDisp(JTOC, VM_Entrypoints.threadSwitchFromBackedgeOffset); 
      } else { // EPILOGUE
        asm.emitCALL_RegDisp(JTOC, VM_Entrypoints.threadSwitchFromEpilogueOffset); 
      }
      fr1.resolve(asm);
    }
  }

  private final void genMagic (VM_Method m) {
    VM_Atom methodName = m.getName();

    if (methodName == VM_MagicNames.attempt) {
      // attempt gets called with four arguments
      //   base
      //   offset
      //   oldVal
      //   newVal
      // returns ([base+offset] == oldVal)
      // if ([base+offset] == oldVal) [base+offset] := newVal
      // (operation on memory is atomic)
      asm.emitPOP_Reg (T1);            // newVal
      asm.emitPOP_Reg (EAX);           // oldVal (EAX is implicit arg to LCMPXCNG
      asm.emitPOP_Reg (S0);            // S0 = offset
      asm.emitADD_Reg_RegInd(S0, SP);  // S0 += base
      if (VM.BuildForSingleVirtualProcessor) {
	asm.emitMOV_RegInd_Reg (S0, T1);       // simply a store on uniprocessor (need not be atomic or cmp/xchg)
	asm.emitMOV_RegInd_Imm (SP, 1);        // 'push' true (overwriting base)
      } else {
	asm.emitLockNextInstruction();
	asm.emitCMPXCHG_RegInd_Reg (S0, T1);   // atomic compare-and-exchange
	asm.emitMOV_RegInd_Imm (SP, 0);        // 'push' false (overwriting base)
	VM_ForwardReference fr = asm.forwardJcc(asm.NE); // skip if compare fails
	asm.emitMOV_RegInd_Imm (SP, 1);        // 'push' true (overwriting base)
	fr.resolve(asm);
      }
      return;
    }
    
    if (methodName == VM_MagicNames.invokeMain) {
      // invokeMain gets "called" with two arguments:
      //   String[] mainArgs       // the arguments to the main method
      //   INSTRUCTION[] mainCode  // the code for the main method
      asm.emitPOP_Reg (S0);            // 
      genParameterRegisterLoad(1); // pass 1 parameter word	
      asm.emitCALL_Reg(S0);            // branches to mainCode with mainArgs on the stack
      return;
    }
    
    if (methodName == VM_MagicNames.saveThreadState) {
      int offset = VM_Entrypoints.saveThreadStateInstructionsOffset;
      genParameterRegisterLoad(1); // pass 1 parameter word
      asm.emitCALL_RegDisp(JTOC, offset);
      return;
    }

    if (methodName == VM_MagicNames.resumeThreadExecution) {
      int offset = VM_Entrypoints.resumeThreadExecutionInstructionsOffset;
      genParameterRegisterLoad(2); // pass 2 parameter words
      asm.emitCALL_RegDisp(JTOC, offset);
      return;
    }         
         
    if (methodName == VM_MagicNames.restoreHardwareExceptionState) {
      int offset = VM_Entrypoints.restoreHardwareExceptionStateInstructionsOffset;
      genParameterRegisterLoad(1); // pass 1 parameter word
      asm.emitCALL_RegDisp(JTOC, offset);
      return;
    }

    if (methodName == VM_MagicNames.invokeClassInitializer) {
      asm.emitPOP_Reg (S0);
      asm.emitCALL_Reg(S0); // call address just popped
      return;
    }
    
    /*
     * sysCall0, sysCall1, sysCall2, sysCall3 and sysCall4 return
     * an integer (32 bits).
     *
     *	hi mem
     *	  branch address	<- SP
     *
     * before call to C
     *  hi mem
     *	  branch address
     *	  saved ebx
     *	  saved pr
     *	  saved jtoc		<- SP
     */
    if (methodName == VM_MagicNames.sysCall0) {
      asm.emitMOV_Reg_Reg(T0, SP);	// T0 <- SP
      asm.emitPUSH_Reg(EBX);	// save three nonvolatiles: EBX
      asm.emitPUSH_Reg(PR);	// PR aka ESI
      asm.emitPUSH_Reg(JTOC);	// JTOC aka EDI
      asm.emitCALL_RegInd(T0);	// branch to C code
      asm.emitPOP_Reg(JTOC);	// restore the three nonvolatiles
      asm.emitPOP_Reg(PR);
      asm.emitPOP_Reg(EBX);
      asm.emitMOV_RegInd_Reg(SP, T0);	// store return value
      return;
    }
    
    if (methodName == VM_MagicNames.sysCall1) {
      asm.emitPOP_Reg(S0);	// first and only argument
      asm.emitMOV_Reg_Reg(T0, SP);	// T0 <- SP
      asm.emitPUSH_Reg(EBX);	// save three nonvolatiles: EBX
      asm.emitPUSH_Reg(PR);	// PR aka ESI
      asm.emitPUSH_Reg(JTOC);	// JTOC aka EDI
      asm.emitPUSH_Reg(S0);	// push arg on stack
      asm.emitCALL_RegInd(T0);	// branch to C code
      asm.emitPOP_Reg(S0);	// pop the argument 
      asm.emitPOP_Reg(JTOC);	// restore the three nonvolatiles
      asm.emitPOP_Reg(PR);
      asm.emitPOP_Reg(EBX);
      asm.emitMOV_RegInd_Reg(SP, T0);	// store return value
      return;
    }
    
    if (methodName == VM_MagicNames.sysCall2) {
      // C require its arguments reversed
      asm.emitPOP_Reg(T1);	// second arg
      asm.emitPOP_Reg(S0);	// first arg
      asm.emitMOV_Reg_Reg(T0, SP);	// T0 <- SP
      asm.emitPUSH_Reg(EBX);	// save three nonvolatiles: EBX
      asm.emitPUSH_Reg(PR);	// PR aka ESI
      asm.emitPUSH_Reg(JTOC);	// JTOC aka EDI
      asm.emitPUSH_Reg(T1);	// reorder arguments for C 
      asm.emitPUSH_Reg(S0);	// reorder arguments for C
      asm.emitCALL_RegInd(T0);	// branch to C code
      asm.emitADD_Reg_Imm(SP, WORDSIZE*2);	// pop the arguments 
      asm.emitPOP_Reg(JTOC);	// restore the three nonvolatiles
      asm.emitPOP_Reg(PR);
      asm.emitPOP_Reg(EBX);
      asm.emitMOV_RegInd_Reg(SP, T0);	// store return value
      return;
    }
    
    if (methodName == VM_MagicNames.sysCall3) {
      // C require its arguments reversed
      asm.emitMOV_Reg_RegInd(T0, SP);			// load 3rd arg
      asm.emitMOV_RegDisp_Reg(SP, -1*WORDSIZE, T0);	// store 3rd arg
      asm.emitMOV_Reg_RegDisp(T0, SP, WORDSIZE);	// load 2nd arg
      asm.emitMOV_RegDisp_Reg(SP, -2*WORDSIZE, T0);	// store 2nd arg
      asm.emitMOV_Reg_RegDisp(T0, SP, 2*WORDSIZE);	// load 1st arg
      asm.emitMOV_RegDisp_Reg(SP, -3*WORDSIZE, T0);	// store 1st arg
      asm.emitMOV_Reg_Reg(T0, SP);			// T0 <- SP
      asm.emitMOV_RegDisp_Reg(SP, 2*WORDSIZE, EBX);	// save three nonvolatiles: EBX
      asm.emitMOV_RegDisp_Reg(SP, 1*WORDSIZE, PR);	// PR aka ESI
      asm.emitMOV_RegInd_Reg(SP, JTOC);			// JTOC aka EDI
      asm.emitADD_Reg_Imm(SP, -3*WORDSIZE);		// grow the stack
      asm.emitCALL_RegDisp(T0, 3*WORDSIZE); // fourth arg on stack is address to call
      asm.emitADD_Reg_Imm(SP, WORDSIZE*3);		// pop the arguments 
      asm.emitPOP_Reg(JTOC);	// restore the three nonvolatiles
      asm.emitPOP_Reg(PR);
      asm.emitPOP_Reg(EBX);
      asm.emitMOV_RegInd_Reg(SP, T0);			// store return value
      return;
    }
    
    if (methodName == VM_MagicNames.sysCall4) {
      // C require its arguments reversed
      asm.emitMOV_Reg_RegDisp(T0, SP, WORDSIZE);	// load 3rd arg
      asm.emitMOV_RegDisp_Reg(SP, -1*WORDSIZE, T0);	// store 3th arg
      asm.emitMOV_Reg_RegDisp(T0, SP, 2*WORDSIZE);	// load 2nd arg
      asm.emitMOV_RegDisp_Reg(SP, -2*WORDSIZE, T0);	// store 2nd arg
      asm.emitMOV_Reg_RegDisp(T0, SP, 3*WORDSIZE);	// load 1st arg
      asm.emitMOV_RegDisp_Reg(SP, -3*WORDSIZE, T0);	// store 1st arg
      asm.emitMOV_Reg_Reg(T0, SP);			// T0 <- SP
      asm.emitMOV_RegDisp_Reg(SP, 3*WORDSIZE, EBX);	// save three nonvolatiles: EBX
      asm.emitMOV_RegDisp_Reg(SP, 2*WORDSIZE, PR);	// PR aka ESI
      asm.emitMOV_RegDisp_Reg(SP, 1*WORDSIZE, JTOC);	// JTOC aka EDI
      asm.emitADD_Reg_Imm(SP, -3*WORDSIZE);		// grow the stack
      asm.emitCALL_RegDisp(T0, 4*WORDSIZE); // fifth arg on stack is address to call
      asm.emitADD_Reg_Imm(SP, WORDSIZE*4);		// pop the arguments 
      asm.emitPOP_Reg(JTOC);	// restore the three nonvolatiles
      asm.emitPOP_Reg(PR);
      asm.emitPOP_Reg(EBX);
      asm.emitMOV_RegInd_Reg(SP, T0);			// store return value
      return;
    }
    
    /*
     * sysCall_L_0  returns a long and takes no arguments
     */
    if (methodName == VM_MagicNames.sysCall_L_0) {
      asm.emitMOV_Reg_Reg(T0, SP);
      asm.emitPUSH_Reg(EBX);	// save three nonvolatiles: EBX
      asm.emitPUSH_Reg(PR);	// PR aka ESI
      asm.emitPUSH_Reg(JTOC);	// JTOC aka EDI
      asm.emitCALL_RegInd(T0);	// first arg on stack is address to call
      asm.emitPOP_Reg(JTOC);	// restore the three nonvolatiles
      asm.emitPOP_Reg(PR);
      asm.emitPOP_Reg(EBX);
      asm.emitMOV_RegInd_Reg(SP, T1);	// store return value: hi half
      asm.emitPUSH_Reg(T0);	// low half
      return;
    }
    
    /*
     * sysCall_L_I  returns a long and takes an integer argument
     */
    if (methodName == VM_MagicNames.sysCall_L_I) {
      asm.emitPOP_Reg(S0);	// the one integer argument
      asm.emitMOV_Reg_Reg(T0, SP);	// T0 <- SP
      asm.emitPUSH_Reg(EBX);	// save three nonvolatiles: EBX
      asm.emitPUSH_Reg(PR);	// PR aka ESI
      asm.emitPUSH_Reg(JTOC);	// JTOC aka EDI
      asm.emitPUSH_Reg(S0);	// push arg on stack
      asm.emitCALL_RegInd(T0);	// branch to C code
      asm.emitPOP_Reg(S0);	// pop the argument 
      asm.emitPOP_Reg(JTOC);	// restore the three nonvolatiles
      asm.emitPOP_Reg(PR);
      asm.emitPOP_Reg(EBX);
      asm.emitMOV_RegInd_Reg(SP, T1);	// store return value: hi half
      asm.emitPUSH_Reg(T0);	// low half
      return;
    }
    
    if (methodName == VM_MagicNames.sysCallAD) {  // address, double
      // C require its arguments reversed
      asm.emitMOV_Reg_RegInd(T0, SP);			// load 2nd arg
      asm.emitMOV_RegDisp_Reg(SP, -2*WORDSIZE, T0);	// store 2nd arg
      asm.emitMOV_Reg_RegDisp(T0, SP, WORDSIZE);	// load 2nd arg
      asm.emitMOV_RegDisp_Reg(SP, -1*WORDSIZE, T0);	// store 2nd arg
      asm.emitMOV_Reg_RegDisp(T0, SP, 2*WORDSIZE);	// load 1st arg
      asm.emitMOV_RegDisp_Reg(SP, -3*WORDSIZE, T0);	// store 1st arg
      asm.emitMOV_Reg_Reg(T0, SP);			// T0 <- SP
      asm.emitMOV_RegDisp_Reg(SP, 2*WORDSIZE, EBX);	// save three nonvolatiles: EBX
      asm.emitMOV_RegDisp_Reg(SP, 1*WORDSIZE, PR);	// PR aka ESI
      asm.emitMOV_RegInd_Reg(SP, JTOC);			// JTOC aka EDI
      asm.emitADD_Reg_Imm(SP, -3*WORDSIZE);		// grow the stack
      asm.emitCALL_RegDisp(T0, 3*WORDSIZE); // 4th word on orig. stack is address to call
      asm.emitADD_Reg_Imm(SP, WORDSIZE*3);		// pop the arguments 
      asm.emitPOP_Reg(JTOC);	// restore the three nonvolatiles
      asm.emitPOP_Reg(PR);
      asm.emitPOP_Reg(EBX);
      asm.emitMOV_RegInd_Reg(SP, T0);			// store return value
      return;
    }

    /*
     * A special version of sysCall2, for invoking sigWait and allowing
     * collection of the frame making the call.  The signature of the
     * magic is shared between powerPC and intel to simplify the caller.
     * The signature is
     * address to "call"
     * toc or undefined for intel
     * address of a lock/barrier which will be passed to sigWait
     * value to store into the lock/barrier, also passed to sigWait.  
     * the VM_Register object of the executing thread.
     *
     * The magic stores the current ip/fp into the VM_Register, to
     * allow collection of this thread from the current frame and below.
     * It then reverses the order of the two parameters on the stack to
     * conform to C calling convention, and finally invokes sigwait.
     *
     * stack:
     *	low memory
     *		ip -- address of sysPthreadSigWait in sys.C
     *		toc --
     *          p1 -- address of lockword
     *		p2 -- value to store in lockword
     *          address of VM_Register object for this thread
     *	high mem
     * This to be invoked from baseline code only.
     */
    if (methodName == VM_MagicNames.sysCallSigWait) {
      int ipOffset   = VM.getMember("LVM_Registers;", "ip", "I").getOffset();
      int fpOffset   = VM.getMember("LVM_Registers;", "fp",  "I").getOffset();
      int gprsOffset = VM.getMember("LVM_Registers;", "gprs", "[I").getOffset();

      asm.emitMOV_Reg_RegInd(T0, SP);	// T0 <- context register obj @
      asm.emitMOV_RegDisp_Reg(T0, fpOffset, FP);	// store fp in context
      asm.emitCALL_Imm (asm.getMachineCodeIndex() + 5);
      asm.emitPOP_Reg(T1);				// T1 <- IP
      asm.emitMOV_RegDisp_Reg(T0, ipOffset, T1);	// store ip in context
      asm.emitMOV_Reg_RegDisp(T0, T0, gprsOffset);	// T0 <- grps array @
      asm.emitMOV_Reg_Imm(T1, (int) FP );		// T1 <- the index for fp
      asm.emitMOV_RegIdx_Reg(T0, T1, asm.WORD, 0, FP);	// store fp in the gpr array too
      asm.emitMOV_Reg_RegDisp(T1, SP, WORDSIZE);	// second arg
      asm.emitMOV_Reg_RegDisp(S0, SP, 2*WORDSIZE);	// first arg
      asm.emitMOV_Reg_Reg(T0, SP);	// T0 <- [sysPthreadSigWait @]
      asm.emitADD_Reg_Imm(T0, 4*WORDSIZE);
      asm.emitPUSH_Reg(JTOC);	// save JTOC aka EDI
      asm.emitPUSH_Reg(T1);	// reorder arguments for C 
      asm.emitPUSH_Reg(S0);	// reorder arguments for C
      asm.emitCALL_RegInd(T0);	// branch to C code
      asm.emitADD_Reg_Imm(SP, WORDSIZE*2);	// pop the arguments 
      asm.emitPOP_Reg(JTOC);	// restore JTOC
      asm.emitADD_Reg_Imm(SP, WORDSIZE*4);	// pop all but last
      asm.emitMOV_RegInd_Reg(SP, T0);	// overwrite last with return value

      return;
    }
    
    if (methodName == VM_MagicNames.getFramePointer) {
      asm.emitPUSH_Reg(FP);
      return;
    }
    
    if (methodName == VM_MagicNames.setFramePointer) {
      asm.emitPOP_Reg(FP);
      return;
    }

    if (methodName == VM_MagicNames.getCallerFramePointer) {
      asm.emitPOP_Reg(T0);                                       // Callee FP
      asm.emitPUSH_RegDisp(T0, STACKFRAME_FRAME_POINTER_OFFSET); // Caller FP
      return;
    }

    if (methodName == VM_MagicNames.setCallerFramePointer) {
      asm.emitPOP_Reg(T0);  // value
      asm.emitPOP_Reg(S0);  // fp
      asm.emitMOV_RegDisp_Reg(S0, STACKFRAME_FRAME_POINTER_OFFSET, T0); // [S0+SFPO] <- T0
      return;
    }

    if (methodName == VM_MagicNames.getCompiledMethodID) {
      asm.emitPOP_Reg(T0);                                   // Callee FP
      asm.emitPUSH_RegDisp(T0, STACKFRAME_METHOD_ID_OFFSET); // Callee CMID
      return;
    }

    if (methodName == VM_MagicNames.setCompiledMethodID) {
      asm.emitPOP_Reg(T0);  // value
      asm.emitPOP_Reg(S0);  // fp
      asm.emitMOV_RegDisp_Reg(S0, STACKFRAME_METHOD_ID_OFFSET, T0); // [S0+SMIO] <- T0
      return;
    }

    if (methodName == VM_MagicNames.getReturnAddress) {
      asm.emitPOP_Reg(T0);                                        // Callee FP
      asm.emitPUSH_RegDisp(T0, STACKFRAME_RETURN_ADDRESS_OFFSET); // Callee return address
      return;
    }
    
    if (methodName == VM_MagicNames.setReturnAddress) {
      asm.emitPOP_Reg(T0);  // value
      asm.emitPOP_Reg(S0);  // fp
      asm.emitMOV_RegDisp_Reg(S0, STACKFRAME_RETURN_ADDRESS_OFFSET, T0); // [S0+SRAO] <- T0
      return;
    }

    if (methodName == VM_MagicNames.getTocPointer ||
	methodName == VM_MagicNames.getJTOC ) {
      asm.emitPUSH_Reg(JTOC);
      return;
    }
    
    if (methodName == VM_MagicNames.getThreadId) {
      asm.emitPUSH_RegDisp(PR, VM_Entrypoints.threadIdOffset);                                   
      return;
    }
       
    // set the Thread id register (not really a register)
    if (methodName == VM_MagicNames.setThreadId) {
      asm.emitPOP_RegDisp(PR, VM_Entrypoints.threadIdOffset);                                   
      return;
    }
    
    // get the processor register (PR)
    if (methodName == VM_MagicNames.getProcessorRegister) {
      asm.emitPUSH_Reg(PR);
      return;
    }  

    // set the processor register (PR)
    if (methodName == VM_MagicNames.setProcessorRegister) {
      asm.emitPOP_Reg(PR);
      return;
    }
  

    if (methodName == VM_MagicNames.getIntAtOffset ||
	methodName == VM_MagicNames.getObjectAtOffset ||
	methodName == VM_MagicNames.prepare) {
      asm.emitPOP_Reg (T0);                  // object ref
      asm.emitPOP_Reg (S0);                  // offset
      asm.emitPUSH_RegIdx(T0, S0, asm.BYTE, 0); // pushes [T0+S0]
      return;
    }
    
    if (methodName == VM_MagicNames.setIntAtOffset ||
	methodName == VM_MagicNames.setObjectAtOffset ) {
      asm.emitPOP_Reg(T0);                   // value
      asm.emitPOP_Reg(S0);                   // offset
      asm.emitPOP_Reg(T1);                   // obj ref
      asm.emitMOV_RegIdx_Reg(T1, S0, asm.BYTE, 0, T0); // [T1+S0] <- T0
      return;
    }
    
    if (methodName == VM_MagicNames.getLongAtOffset) {
      asm.emitPOP_Reg (T0);                  // object ref
      asm.emitPOP_Reg (S0);                  // offset
      asm.emitPUSH_RegIdx(T0, S0, asm.BYTE, 4); // pushes [T0+S0+4]
      asm.emitPUSH_RegIdx(T0, S0, asm.BYTE, 0); // pushes [T0+S0]
      return;
    }
    
    if (methodName == VM_MagicNames.setLongAtOffset) {
      asm.emitMOV_Reg_RegInd (T0, SP);		// value high
      asm.emitMOV_Reg_RegDisp(S0, SP, +8 );	// offset
      asm.emitMOV_Reg_RegDisp(T1, SP, +12);	// obj ref
      asm.emitMOV_RegIdx_Reg (T1, S0, asm.BYTE, 0, T0); // [T1+S0] <- T0
      asm.emitMOV_Reg_RegDisp(T0, SP, +4 );	// value low
      asm.emitMOV_RegIdx_Reg (T1, S0, asm.BYTE, 4, T0); // [T1+S0+4] <- T0
      return;
    }
    
    if (methodName == VM_MagicNames.getMemoryWord) {
      asm.emitPOP_Reg(T0);	// address
      asm.emitPUSH_RegInd(T0); // pushes [T0+0]
      return;
    }
    
    if (methodName == VM_MagicNames.setMemoryWord) {
      asm.emitPOP_Reg(T0);  // value
      asm.emitPOP_Reg(S0);  // address
      asm.emitMOV_RegInd_Reg(S0,T0); // [S0+0] <- T0
      return;
    }
    
    if (methodName == VM_MagicNames.objectAsAddress         ||
	methodName == VM_MagicNames.addressAsByteArray      ||
	methodName == VM_MagicNames.addressAsIntArray       ||
	methodName == VM_MagicNames.addressAsObject         ||
	methodName == VM_MagicNames.addressAsType           ||
	methodName == VM_MagicNames.objectAsType            ||
	methodName == VM_MagicNames.objectAsShortArray      ||
	methodName == VM_MagicNames.objectAsByteArray       ||
	methodName == VM_MagicNames.pragmaNoOptCompile      ||
	methodName == VM_MagicNames.addressAsThread         ||
	methodName == VM_MagicNames.objectAsThread          ||
	methodName == VM_MagicNames.objectAsProcessor       ||
//-#if RVM_WITH_JIKESRVM_MEMORY_MANAGERS
	methodName == VM_MagicNames.addressAsBlockControl   ||
	methodName == VM_MagicNames.addressAsSizeControl    ||
	methodName == VM_MagicNames.addressAsSizeControlArray   ||
//-#if RVM_WITH_CONCURRENT_GC
	methodName == VM_MagicNames.threadAsRCCollectorThread ||
//-#endif
//-#endif
	methodName == VM_MagicNames.threadAsCollectorThread ||
	methodName == VM_MagicNames.addressAsRegisters      ||
	methodName == VM_MagicNames.addressAsStack          ||
	methodName == VM_MagicNames.floatAsIntBits          ||
	methodName == VM_MagicNames.intBitsAsFloat          ||
	methodName == VM_MagicNames.doubleAsLongBits        ||
	methodName == VM_MagicNames.pragmaInline            ||
	methodName == VM_MagicNames.pragmaNoInline          ||
	methodName == VM_MagicNames.longBitsAsDouble)
      {
	// no-op (a type change, not a representation change)
	return;
      }
    
    // code for      VM_Type VM_Magic.getObjectType(Object object)
    if (methodName == VM_MagicNames.getObjectType) {
      asm.emitPOP_Reg (T0);			          // object ref
      asm.emitMOV_Reg_RegDisp (T0, T0, OBJECT_TIB_OFFSET);       // T0 <- object's TIB
      asm.emitPUSH_RegDisp(T0, TIB_TYPE_INDEX<<LG_WORDSIZE); // push VM_Type slot of TIB
      return;
    }
    
    // code for      int VM_Magic.getObjectStatus(Object object)
    if (methodName == VM_MagicNames.getObjectStatus) {
      asm.emitPOP_Reg(T0);			// object ref
      asm.emitPUSH_RegDisp(T0, OBJECT_STATUS_OFFSET); 
      return;
    }
    
    if (methodName == VM_MagicNames.getArrayLength) {
      asm.emitPOP_Reg(T0);			// object ref
      asm.emitPUSH_RegDisp(T0, ARRAY_LENGTH_OFFSET); 
      return;
    }
    
    if (methodName == VM_MagicNames.pragmaInline) {
      VM.sysWrite("  ignoring 'VM_Magic.pragmaInline'\n");
      return;
    }
    
    if (methodName == VM_MagicNames.pragmaNoInline) {
      VM.sysWrite("  ignoring 'VM_Magic.pragmaNoInline'\n");
      return;
    }
    
    if (methodName == VM_MagicNames.sync) {  // nothing required on IA32
      return;
    }
    
    if (methodName == VM_MagicNames.isync) { // nothing required on IA32
      return;
    }
    
    // baseline compiled invocation only: all paramaters on the stack
    // hi mem
    //      Code
    //      GPRs
    //      FPRs
    //      Spills
    // low-mem
    if (methodName == VM_MagicNames.invokeMethodReturningVoid) {
      int offset = VM_Entrypoints.reflectiveMethodInvokerInstructionsOffset;
      genParameterRegisterLoad(4); // pass 4 parameter words
      asm.emitCALL_RegDisp(JTOC, offset);
      return;
    }                 

    if (methodName == VM_MagicNames.invokeMethodReturningInt) {
      int offset = VM_Entrypoints.reflectiveMethodInvokerInstructionsOffset;
      genParameterRegisterLoad(4); // pass 4 parameter words
      asm.emitCALL_RegDisp(JTOC, offset);
      asm.emitPUSH_Reg(T0);
      return;
    }                 

    if (methodName == VM_MagicNames.invokeMethodReturningLong) {
      int offset = VM_Entrypoints.reflectiveMethodInvokerInstructionsOffset;
      genParameterRegisterLoad(4); // pass 4 parameter words
      asm.emitCALL_RegDisp(JTOC, offset);
      asm.emitPUSH_Reg(T0); // high half
      asm.emitPUSH_Reg(T1); // low half
      return;
    }                 

    if (methodName == VM_MagicNames.invokeMethodReturningFloat) {
      int offset = VM_Entrypoints.reflectiveMethodInvokerInstructionsOffset;
      genParameterRegisterLoad(4); // pass 4 parameter words
      asm.emitCALL_RegDisp(JTOC, offset);
      asm.emitSUB_Reg_Imm  (SP, 4);
      asm.emitFSTP_RegInd_Reg(SP, FP0);
      return;
    }                 

    if (methodName == VM_MagicNames.invokeMethodReturningDouble) {
      int offset = VM_Entrypoints.reflectiveMethodInvokerInstructionsOffset;
      genParameterRegisterLoad(4); // pass 4 parameter words
      asm.emitCALL_RegDisp(JTOC, offset);
      asm.emitSUB_Reg_Imm  (SP, 8);
      asm.emitFSTP_RegInd_Reg_Quad(SP, FP0);
      return;
    }                 

    if (methodName == VM_MagicNames.invokeMethodReturningObject) {
      int offset = VM_Entrypoints.reflectiveMethodInvokerInstructionsOffset;
      genParameterRegisterLoad(4); // pass 4 parameter words
      asm.emitCALL_RegDisp(JTOC, offset);
      asm.emitPUSH_Reg(T0);
      return;
    }                 

    // baseline invocation
    // one paramater, on the stack  -- actual code
    if (methodName == VM_MagicNames.dynamicBridgeTo) {
      if (VM.VerifyAssertions) VM.assert(klass.isDynamicBridge());

      // save the branch address for later
      asm.emitPOP_Reg (S0);		// S0<-code address

      // restore FPU state
      asm.emitFRSTOR_RegDisp(FP, FPU_SAVE_OFFSET);

      // restore GPRs
      asm.emitMOV_Reg_RegDisp (T0,  FP, T0_SAVE_OFFSET); 
      asm.emitMOV_Reg_RegDisp (T1,  FP, T1_SAVE_OFFSET); 
      asm.emitMOV_Reg_RegDisp (EBX, FP, EBX_SAVE_OFFSET); 
      asm.emitMOV_Reg_RegDisp (JTOC,  FP, JTOC_SAVE_OFFSET); 

      // pop frame
      asm.emitMOV_Reg_Reg(SP, FP);	// SP<-FP
      asm.emitPOP_Reg (FP);		// FP<-previous FP 

      // branch
      asm.emitJMP_Reg (S0);

      return;
    }
                                                  
    if (methodName == VM_MagicNames.returnToNewStack) {
      // load frame pointer with new stack address
      asm.emitPOP_Reg (FP);	
      // restore nonvolatile JTOC register
      asm.emitMOV_Reg_RegDisp (JTOC, FP, JTOC_SAVE_OFFSET);
      // discard current stack frame
      asm.emitLEAVE();				
      // so hardware trap handler can always find it 
      // (opt compiler will reuse FP register)
      asm.emitMOV_RegDisp_Reg(PR, VM_Entrypoints.framePointerOffset, FP); 
      // return to caller- pop parameters from stack
      asm.emitRET_Imm(parameterWords << LG_WORDSIZE);	 
      return;
    }

    if (methodName == VM_MagicNames.roundToZero) {
      // Store the FPU Control Word to a JTOC slot
      asm.emitFNSTCW_RegDisp(JTOC, VM_Entrypoints.FPUControlWordOffset);
      // Set the bits in the status word that control round to zero.
      // Note that we use a 32-bit and, even though we only care about the
      // high-order 16 bits
      asm.emitOR_RegDisp_Imm(JTOC,VM_Entrypoints.FPUControlWordOffset, 0x0c00);
      // Now store the result back into the FPU Control Word
      asm.emitFLDCW_RegDisp(JTOC,VM_Entrypoints.FPUControlWordOffset);
      return;
    }
    
    if (methodName == VM_MagicNames.clearThreadSwitchBit) { // nothing to do
      // ignore obsolete magic
      return;
    }

    if (methodName == VM_MagicNames.getTime) {
    VM.sysWrite("WARNING: VM_Compiler compiling unimplemented magic: getTime in " + method + "\n");
      asm.emitMOV_RegInd_Imm(SP, 0);  // TEMP!! for now, return 0
      return;
    }

    if (methodName == VM_MagicNames.getTimeBase) {
    VM.sysWrite("WARNING: VM_Compiler compiling unimplemented magic: getTimeBase in " + method + "\n");
      asm.emitMOV_RegInd_Imm(SP, 0);  // TEMP!! for now, return 0
      return;
    }
    
    VM.sysWrite("WARNING: VM_Compiler compiling unimplemented magic: " + methodName + " in " + method + "\n");
    asm.emitINT_Imm(0xFF); // trap
    
  }

  // Offset of Java local variable (off frame pointer)
  //
  private final int localOffset  (int local) {
    return firstLocalOffset - (local<<LG_WORDSIZE);
  }
  
  /* reading bytecodes */
  
  private final int fetch1ByteSigned () {
    return bytecodes[bi++];
  }
  
  private final int fetch1ByteUnsigned () {
    return bytecodes[bi++] & 0xFF;
  }
  
  private final int fetch2BytesSigned () {
    int i = bytecodes[bi++] << 8;
    i |= (bytecodes[bi++] & 0xFF);
    return i;
  }
  
  private final int fetch2BytesUnsigned () {
    int i = (bytecodes[bi++] & 0xFF) << 8;
    i |= (bytecodes[bi++] & 0xFF);
    return i;
  }
  
  private final int fetch4BytesSigned () {
    int i = bytecodes[bi++] << 24;
    i |= (bytecodes[bi++] & 0xFF) << 16;
    i |= (bytecodes[bi++] & 0xFF) << 8;
    i |= (bytecodes[bi++] & 0xFF);
    return i;
  }
  
  static void init() {
    exceptionDeliverer = new VM_BaselineExceptionDeliverer();
  }

  static void boot() {
  }
 
  private static final int THREAD_SWITCH_LIMIT = 100;
  private static final int MAX_CODE_EXPANSION = 20;
  private static VM_ExceptionDeliverer exceptionDeliverer;

  private VM_Method    method;
  private VM_Class     klass;
  private VM_Assembler asm; 
  private byte[]       bytecodes;
  private int[]        bytecodeMap;
  private int          bytecodeLength;
  private int          bi;      // index into bytecodes and bytecodeMap
  private int          biStart; // bi at the start of a bytecode
  private VM_Class     profilerClass;
  private int          parameterWords;
  private int          firstLocalOffset;
  
          int          lockOffset;

}
