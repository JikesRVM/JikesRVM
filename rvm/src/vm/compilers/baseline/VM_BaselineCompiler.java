/*
 * (C) Copyright IBM Corp. 2001
 */
//$Id$

/**
 * Baseline compiler - platform independent code.
 * Platform dependent versions extend this class and define
 * the host of abstract methods defined by this class to complete
 * the implementation of a baseline compiler for a particular target,
 *
 * @author Bowen Alpern
 * @author Maria Butrico
 * @author Anthony Cocchi
 * @author Dave Grove
 * @author Derek Lieber
 * @author Janice Shepherd
 */
abstract class VM_BaselineCompiler {

  /** 
   * Options used during base compiler execution 
   */
  protected static VM_BASEOptions options;

  /** 
   * Holds the options as the command line is being processed. 
   */
  protected static VM_BASEOptions setUpOptions;


  /**
   * The method being compiled
   */
  protected final VM_Method method;

  /** 
   * The declaring class of the method being compiled
   */
  protected final VM_Class klass;

  /**
   * The VM_Assembler being used for this compilation
   */
  protected final VM_Assembler asm; 

  /**
   * The bytecodes of the method being compiled
   */
  protected final byte[] bytecodes;

  /**
   * Mapping from bytecodes to machine code offsets
   */
  protected final int[] bytecodeMap;

  /**
   * index into bytecodes and bytecodeMap
   */
  protected int bi;      

  /**
   * bi at the start of a bytecode
   */
  protected int biStart; 

  /**
   * The compiledMethod assigned to this compilation of method
   */
  protected VM_CompiledMethod compiledMethod;

  /** 
   * The height of the expression stack at the start of each bytecode.
   * Only saved for some architecutres, on others this field will be null.
   * See the VM_Compiler constructor.
   */
  protected int[] stackHeights;

  /**
   * machine code offset at which the lock is acquired in the prologue of a synchronized method.
   */
  protected int lockOffset;

  /**
   * Should we print the machine code we generate?
   */
  protected boolean shouldPrint = false;

  /**
   * Is the method currently being compiled interruptible?
   */
  protected final boolean isInterruptible;

  /**
   * Construct a VM_Compiler
   */
  protected VM_BaselineCompiler(VM_CompiledMethod cm) {
    compiledMethod = cm;
    method = cm.getMethod();
    shouldPrint  = ((options.PRINT_MACHINECODE) &&
		    (!options.hasMETHOD_TO_PRINT() ||
		     options.fuzzyMatchMETHOD_TO_PRINT(method.toString())));

    klass = method.getDeclaringClass();
    bytecodes = method.getBytecodes();
    bytecodeMap = new int [bytecodes.length];
    asm = new VM_Assembler(bytecodes.length, shouldPrint);
    isInterruptible = method.isInterruptible();
  }


  /**
   * Clear out crud from bootimage writing
   */
  static void initOptions() {
    options = new VM_BASEOptions();
    setUpOptions = new VM_BASEOptions();
  }

  /**
   * After all the command line options have been processed, set up the official version of the options
   * that will be used during execution. Do any error checks that are needed.
   */
  static void postBootOptions() {
    // If the user has requested machine code dumps, then force a test of method to print option so
    // extra classes needed to process matching will be loaded and compiled upfront. Thus avoiding getting
    // stuck looping by just asking if we have a match in the middle of compilation. Pick an obsure string
    // for the check.
    if ((setUpOptions.hasMETHOD_TO_PRINT() && setUpOptions.fuzzyMatchMETHOD_TO_PRINT("???")) || 
	(setUpOptions.hasMETHOD_TO_BREAK() && setUpOptions.fuzzyMatchMETHOD_TO_BREAK("???"))) {
      VM.sysWrite("??? is not a sensible string to specify for method name");
    }
    //-#if !RVM_WITH_ADAPTIVE_SYSTEM
    if (setUpOptions.PRELOAD_CLASS != null) {
      VM.sysWrite("Option preload_class should only be used when the optimizing compiler is the runtime");
      VM.sysWrite(" compiler or in an adaptive system\n");
      VM.sysExit(1);
    }
    //-#endif

    options = setUpOptions;   // Switch to the version with the user command line processed
  }

  /**
   * Process a command line argument
   * @param prefix
   * @param arg     Command line argument with prefix stripped off
   */
  static void processCommandLineArg(String prefix, String arg) {
    if (setUpOptions != null) {
      if (setUpOptions.processAsOption(prefix, arg)) {
	return;
      } else {
	VM.sysWrite("VM_BaselineCompiler: Unrecognized argument \""+ arg + "\"\n");
	VM.sysExit(1);
      }
    } else {
      VM.sysWrite("VM_BaselineCompiler: Compiler setUpOptions not enabled; Ignoring argument \""+ arg + "\"\n");
    }
  }


  /**
   * Compile the given method with the baseline compiler.
   * 
   * @param method the VM_Method to compile.
   * @return the generated VM_CompiledMethod for said VM_Method.
   */
  public static synchronized VM_CompiledMethod compile (VM_Method method) {
    if (method.isNative()) {
      VM_CompiledMethod cm = VM_CompiledMethods.createCompiledMethod(method, VM_CompilerInfo.JNI);
      VM_MachineCode machineCode = VM_JNICompiler.generateGlueCodeForNative(cm);
      VM_CompilerInfo info = new VM_JNICompilerInfo(method);
      cm.compileComplete(info, machineCode.getInstructions());
      return cm;
    } else {
      VM_CompiledMethod cm = VM_CompiledMethods.createCompiledMethod(method, VM_CompilerInfo.BASELINE);
      if (VM.runningAsJDPRemoteInterpreter) {
	// "fake" compilation
	cm.compileComplete(null, null);
      } else {
	new VM_Compiler(cm).compile();
      }
      return cm;
    }
  }


  /**
   * Top level driver for baseline compilation of a method.
   */
  protected void compile() {
    if (options.PRINT_METHOD) printMethodMessage();
    if (shouldPrint) printStartHeader(method);
    VM_ReferenceMaps refMaps     = new VM_ReferenceMaps(method, stackHeights);
    VM_MachineCode  machineCode  = genCode();
    if (shouldPrint) printEndHeader(method);

    INSTRUCTION[]   instructions = machineCode.getInstructions();
    int[]           bytecodeMap  = machineCode.getBytecodeMap();
    VM_CompilerInfo info;
    if (method.isSynchronized()) {
      info = new VM_BaselineCompilerInfo(method, refMaps, bytecodeMap, instructions.length, lockOffset);
    } else {
      info = new VM_BaselineCompilerInfo(method, refMaps, bytecodeMap, instructions.length);
    }
    compiledMethod.compileComplete(info, instructions);
  }


  /*
   * Reading bytecodes from the array of bytes
   */
  protected final int fetch1ByteSigned () {
    return bytecodes[bi++];
  }
  
  protected final int fetch1ByteUnsigned () {
    return bytecodes[bi++] & 0xFF;
  }
  
  protected final int fetch2BytesSigned () {
    int i = bytecodes[bi++] << 8;
    i |= (bytecodes[bi++] & 0xFF);
    return i;
  }
  
  protected final int fetch2BytesUnsigned () {
    int i = (bytecodes[bi++] & 0xFF) << 8;
    i |= (bytecodes[bi++] & 0xFF);
    return i;
  }
  
  protected final int fetch4BytesSigned () {
    int i = bytecodes[bi++] << 24;
    i |= (bytecodes[bi++] & 0xFF) << 16;
    i |= (bytecodes[bi++] & 0xFF) << 8;
    i |= (bytecodes[bi++] & 0xFF);
    return i;
  }

  final int getBytecodeIndex () {
    return biStart;
  }
  
  final int[] getBytecodeMap () {
    return bytecodeMap;
  }


  /**
   * Print a message to mark the start of machine code printing for a method
   * @param method
   */
  protected final void printStartHeader (VM_Method method) {
    VM.sysWrite("baseline Start: Final machine code for method ");
    VM.sysWrite(method.getDeclaringClass().toString());
    VM.sysWrite(" "); 
    VM.sysWrite(method.getName());
    VM.sysWrite(" ");
    VM.sysWrite(method.getDescriptor());
    VM.sysWrite("\n");
  }

  /**
   * Print a message to mark the end of machine code printing for a method
   * @param method
   */
  protected final void printEndHeader (VM_Method method) {
    VM.sysWrite("baseline End: Final machine code for method ");
    VM.sysWrite(method.getDeclaringClass().toString());
    VM.sysWrite(" "); 
    VM.sysWrite(method.getName());
    VM.sysWrite(" ");
    VM.sysWrite(method.getDescriptor());
    VM.sysWrite("\n");
  }

  /**
   * Print a message of a method name
   */
  protected final void printMethodMessage () {
    VM.sysWrite("-methodBase ");
    VM.sysWrite(method.getDeclaringClass().toString());
    VM.sysWrite(" "); 
    VM.sysWrite(method.getName());
    VM.sysWrite(" ");
    VM.sysWrite(method.getDescriptor());
    VM.sysWrite(" \n");
  }


  /**
   * Main code generation loop.
   */
  protected final VM_MachineCode genCode () {
    emit_prologue();
    for (bi=0; bi<bytecodes.length;) {
      bytecodeMap[bi] = asm.getMachineCodeIndex();
      asm.resolveForwardReferences(bi);
      biStart = bi;
      int code = fetch1ByteUnsigned();
      switch (code) {
      case 0x00: /* nop */ {
	if (shouldPrint) asm.noteBytecode(biStart, "nop");
	break;
      }

      case 0x01: /* aconst_null */ {
	if (shouldPrint) asm.noteBytecode(biStart, "aconst_null ");
	emit_aconst_null();
	break;
      }

      case 0x02: /* iconst_m1 */ {
	if (shouldPrint) asm.noteBytecode(biStart, "iconst_m1 ");
	emit_iconst(-1);
	break;
      }

      case 0x03: /* iconst_0 */ {
	if (shouldPrint) asm.noteBytecode(biStart, "iconst_0 ");
	emit_iconst(0);
	break;
      }

      case 0x04: /* iconst_1 */ {
	if (shouldPrint) asm.noteBytecode(biStart, "iconst_1 ");
	emit_iconst(1);
	break;
      }

      case 0x05: /* iconst_2 */ {
	if (shouldPrint) asm.noteBytecode(biStart, "iconst_2 ");
	emit_iconst(2);
	break;
      }

      case 0x06: /* iconst_3 */ {
	if (shouldPrint) asm.noteBytecode(biStart, "iconst_3 ");
	emit_iconst(3);
	break;
      }

      case 0x07: /* iconst_4 */ {
	if (shouldPrint) asm.noteBytecode(biStart, "iconst_4 ");
	emit_iconst(4);
	break;
      }

      case 0x08: /* iconst_5 */ {
	if (shouldPrint) asm.noteBytecode(biStart, "iconst_5 ");
	emit_iconst(5);
	break;
      }

      case 0x09: /* lconst_0 */ {
	if (shouldPrint) asm.noteBytecode(biStart, "lconst_0 ");  // floating-point 0 is long 0
	emit_lconst(0);
	break;
      }

      case 0x0a: /* lconst_1 */ {
	if (shouldPrint) asm.noteBytecode(biStart, "lconst_1 ");
	emit_lconst(1);
	break;
      }

      case 0x0b: /* fconst_0 */ {
	if (shouldPrint) asm.noteBytecode(biStart, "fconst_0");
	emit_fconst_0();
	break;
      }

      case 0x0c: /* fconst_1 */ {
	if (shouldPrint) asm.noteBytecode(biStart, "fconst_1");
	emit_fconst_1();
	break;
      }

      case 0x0d: /* fconst_2 */ {
	if (shouldPrint) asm.noteBytecode(biStart, "fconst_2");
	emit_fconst_2();
	break;
      }

      case 0x0e: /* dconst_0 */ {
	if (shouldPrint) asm.noteBytecode(biStart, "dconst_0");
	emit_dconst_0();
	break;
      }

      case 0x0f: /* dconst_1 */ {
	if (shouldPrint) asm.noteBytecode(biStart, "dconst_1");
	emit_dconst_1();
	break;
      }

      case 0x10: /* bipush */ {
	int val = fetch1ByteSigned();
	if (shouldPrint) asm.noteBytecode(biStart, "bipush " + val);
	emit_iconst(val);
	break;
      }

      case 0x11: /* sipush */ {
	int val = fetch2BytesSigned();
	if (shouldPrint) asm.noteBytecode(biStart, "sipush " + val);
	emit_iconst(val);
	break;
      }

      case 0x12: /* ldc */ {
	int index = fetch1ByteUnsigned();
	int offset = klass.getLiteralOffset(index);
	if (shouldPrint) asm.noteBytecode(biStart, "ldc " + index);
	emit_ldc(offset);
	break;
      }

      case 0x13: /* ldc_w */ {
	int index = fetch2BytesUnsigned();
	if (shouldPrint) asm.noteBytecode(biStart, "ldc_w " + index);
	int offset = klass.getLiteralOffset(index);
	emit_ldc(offset);
	break;
      }

      case 0x14: /* ldc2_w */ {
	int index = fetch2BytesUnsigned();
	if (shouldPrint) asm.noteBytecode(biStart, "ldc2_w " + index);
	int offset = klass.getLiteralOffset(index);
	emit_ldc2(offset);
	break;
      }

      case 0x15: /* iload */ {
	int index = fetch1ByteUnsigned();
	if (shouldPrint) asm.noteBytecode(biStart, "iload " + index);
	emit_iload(index);
	break;
      }

      case 0x16: /* lload */ {
	int index = fetch1ByteUnsigned();
	if (shouldPrint) asm.noteBytecode(biStart, "lload " + index);
	emit_lload(index);
	break;
      }

      case 0x17: /* fload */ {
	int index = fetch1ByteUnsigned();
	if (shouldPrint) asm.noteBytecode(biStart, "fload " + index);
	emit_fload(index);
	break;
      }

      case 0x18: /* dload */ {
	int index = fetch1ByteUnsigned();
	if (shouldPrint) asm.noteBytecode(biStart, "dload " + index);
	emit_dload(index);
	break;
      }

      case 0x19: /* aload */ {
	int index = fetch1ByteUnsigned();
	if (shouldPrint) asm.noteBytecode(biStart, "aload " + index);
	emit_aload(index);
	break;
      }

      case 0x1a: /* iload_0 */ {
	if (shouldPrint) asm.noteBytecode(biStart, "iload_0");
	emit_iload(0);
	break;
      }

      case 0x1b: /* iload_1 */ {
	if (shouldPrint) asm.noteBytecode(biStart, "iload_1");
	emit_iload(1);
	break;
      }

      case 0x1c: /* iload_2 */ {
	if (shouldPrint) asm.noteBytecode(biStart, "iload_2");
	emit_iload(2);
	break;
      }

      case 0x1d: /* iload_3 */ {
	if (shouldPrint) asm.noteBytecode(biStart, "iload_3");
	emit_iload(3);
	break;
      }

      case 0x1e: /* lload_0 */ {
	if (shouldPrint) asm.noteBytecode(biStart, "lload_0");
	emit_lload(0);
	break;
      }

      case 0x1f: /* lload_1 */ {
	if (shouldPrint) asm.noteBytecode(biStart, "lload_1");
	emit_lload(1);
	break;
      }

      case 0x20: /* lload_2 */ {
	if (shouldPrint) asm.noteBytecode(biStart, "lload_2");
	emit_lload(2);
	break;
      }

      case 0x21: /* lload_3 */ {
	if (shouldPrint) asm.noteBytecode(biStart, "lload_3");
	emit_lload(3);
	break;
      }

      case 0x22: /* fload_0 */ {
	if (shouldPrint) asm.noteBytecode(biStart, "fload_0");
	emit_fload(0);
	break;
      }

      case 0x23: /* fload_1 */ {
	if (shouldPrint) asm.noteBytecode(biStart, "fload_1");
	emit_fload(1);
	break;
      }

      case 0x24: /* fload_2 */ {
	if (shouldPrint) asm.noteBytecode(biStart, "fload_2");
	emit_fload(2);
	break;
      }

      case 0x25: /* fload_3 */ {
	if (shouldPrint) asm.noteBytecode(biStart, "fload_3");
	emit_fload(3);
	break;
      }

      case 0x26: /* dload_0 */ {
	if (shouldPrint) asm.noteBytecode(biStart, "dload_0");
	emit_dload(0);
	break;
      }

      case 0x27: /* dload_1 */ {
	if (shouldPrint) asm.noteBytecode(biStart, "dload_1");
	emit_dload(1);
	break;
      }

      case 0x28: /* dload_2 */ {
	if (shouldPrint) asm.noteBytecode(biStart, "dload_2");
	emit_dload(2);
	break;
      }

      case 0x29: /* dload_3 */ {
	if (shouldPrint) asm.noteBytecode(biStart, "dload_3");
	emit_dload(3);
	break;
      }

      case 0x2a: /* aload_0 */ {
	if (shouldPrint) asm.noteBytecode(biStart, "aload_0");
	emit_aload(0);
	break;
      }

      case 0x2b: /* aload_1 */ {
	if (shouldPrint) asm.noteBytecode(biStart, "aload_1");
	emit_aload(1);
	break;
      }           

      case 0x2c: /* aload_2 */ {
	if (shouldPrint) asm.noteBytecode(biStart, "aload_2");
	emit_aload(2);
	break;
      }

      case 0x2d: /* aload_3 */ {
	if (shouldPrint) asm.noteBytecode(biStart, "aload_3");
	emit_aload(3);
	break;
      } 

      case 0x2e: /* iaload */ {
	if (shouldPrint) asm.noteBytecode(biStart, "iaload");
	emit_iaload();
	break;
      }

      case 0x2f: /* laload */ {
	if (shouldPrint) asm.noteBytecode(biStart, "laload");
	emit_laload();
	break;
      }

      case 0x30: /* faload */ {
	if (shouldPrint) asm.noteBytecode(biStart, "faload");
	emit_faload();
	break;
      }

      case 0x31: /* daload */ {
	if (shouldPrint) asm.noteBytecode(biStart, "daload");
	emit_daload();
	break;
      }

      case 0x32: /* aaload */ {
	if (shouldPrint) asm.noteBytecode(biStart, "aaload");
	emit_aaload();
	break;
      }

      case 0x33: /* baload */ {
	if (shouldPrint) asm.noteBytecode(biStart, "baload");
	emit_baload();
	break;
      }

      case 0x34: /* caload */ {
	if (shouldPrint) asm.noteBytecode(biStart, "caload");
	emit_caload();
	break;
      }

      case 0x35: /* saload */ {
	if (shouldPrint) asm.noteBytecode(biStart, "saload");
	emit_saload();
	break;
      }

      case 0x36: /* istore */ {
	int index = fetch1ByteUnsigned();
	if (shouldPrint) asm.noteBytecode(biStart, "istore " + index);
	emit_istore(index);
	break;
      }

      case 0x37: /* lstore */ {
	int index = fetch1ByteUnsigned();
	if (shouldPrint) asm.noteBytecode(biStart, "lstore " + index);
	emit_lstore(index);
	break;
      }

      case 0x38: /* fstore */ {
	int index = fetch1ByteUnsigned();
	if (shouldPrint) asm.noteBytecode(biStart, "fstore " + index);
	emit_fstore(index);
	break;
      }

      case 0x39: /* dstore */ {
	int index = fetch1ByteUnsigned();
	if (shouldPrint) asm.noteBytecode(biStart, "dstore " + index);
	emit_dstore(index);
	break;
      }

      case 0x3a: /* astore */ {
	int index = fetch1ByteUnsigned();
	if (shouldPrint) asm.noteBytecode(biStart, "astore " + index);
	emit_astore(index);
	break;
      }

      case 0x3b: /* istore_0 */ {
	if (shouldPrint) asm.noteBytecode(biStart, "istore_0");
	emit_istore(0);
	break;
      }

      case 0x3c: /* istore_1 */ {
	if (shouldPrint) asm.noteBytecode(biStart, "istore_1");
	emit_istore(1);
	break;
      }

      case 0x3d: /* istore_2 */ {
	if (shouldPrint) asm.noteBytecode(biStart, "istore_2");
	emit_istore(2);
	break;
      }

      case 0x3e: /* istore_3 */ {
	if (shouldPrint) asm.noteBytecode(biStart, "istore_3");
	emit_istore(3);
	break;
      }

      case 0x3f: /* lstore_0 */ {
	if (shouldPrint) asm.noteBytecode(biStart, "lstore_0");
	emit_lstore(0);
	break;
      }

      case 0x40: /* lstore_1 */ {
	if (shouldPrint) asm.noteBytecode(biStart, "lstore_1");
	emit_lstore(1);
	break;
      }

      case 0x41: /* lstore_2 */ {
	if (shouldPrint) asm.noteBytecode(biStart, "lstore_2");
	emit_lstore(2);
	break;
      } 

      case 0x42: /* lstore_3 */ {
	if (shouldPrint) asm.noteBytecode(biStart, "lstore_3");
	emit_lstore(3);
	break;
      }

      case 0x43: /* fstore_0 */ {
	if (shouldPrint) asm.noteBytecode(biStart, "fstore_0");
	emit_fstore(0);
	break;
      }

      case 0x44: /* fstore_1 */ {
	if (shouldPrint) asm.noteBytecode(biStart, "fstore_1");
	emit_fstore(1);
	break;
      }

      case 0x45: /* fstore_2 */ {
	if (shouldPrint) asm.noteBytecode(biStart, "fstore_2");
	emit_fstore(2);
	break;
      }

      case 0x46: /* fstore_3 */ {
	if (shouldPrint) asm.noteBytecode(biStart, "fstore_3");
	emit_fstore(3);
	break;
      }

      case 0x47: /* dstore_0 */ {
	if (shouldPrint) asm.noteBytecode(biStart, "dstore_0");
	emit_dstore(0);
	break;
      }

      case 0x48: /* dstore_1 */ {
	if (shouldPrint) asm.noteBytecode(biStart, "dstore_1");
	emit_dstore(1);
	break;
      }

      case 0x49: /* dstore_2 */ {
	if (shouldPrint) asm.noteBytecode(biStart, "dstore_2");
	emit_dstore(2);
	break;
      }

      case 0x4a: /* dstore_3 */ {
	if (shouldPrint) asm.noteBytecode(biStart, "dstore_3");
	emit_dstore(3);
	break;
      }

      case 0x4b: /* astore_0 */ {
	if (shouldPrint) asm.noteBytecode(biStart, "astore_0");
	emit_astore(0);
	break;
      }

      case 0x4c: /* astore_1 */ {
	if (shouldPrint) asm.noteBytecode(biStart, "astore_1");
	emit_astore(1);
	break;
      }

      case 0x4d: /* astore_2 */ {
	if (shouldPrint) asm.noteBytecode(biStart, "astore_2");
	emit_astore(2);
	break;
      }

      case 0x4e: /* astore_3 */ {
	if (shouldPrint) asm.noteBytecode(biStart, "astore_3");
	emit_astore(3);
	break;
      }

      case 0x4f: /* iastore */ {
	if (shouldPrint) asm.noteBytecode(biStart, "iastore");
	emit_iastore();
	break;
      }

      case 0x50: /* lastore */ { 
	if (shouldPrint) asm.noteBytecode(biStart, "lastore"); 
	emit_lastore();
	break;
      }

      case 0x51: /* fastore */ {
	if (shouldPrint) asm.noteBytecode(biStart, "fastore");
	emit_fastore();
	break;
      }

      case 0x52: /* dastore */ {
	if (shouldPrint) asm.noteBytecode(biStart, "dastore");
	emit_dastore();
	break;
      }

      case 0x53: /* aastore */ {
	if (shouldPrint) asm.noteBytecode(biStart, "aastore");
	if (VM.VerifyUnint && !isInterruptible) forbiddenBytecode("aastore");
	emit_aastore();
	break;
      }

      case 0x54: /* bastore */ {
	if (shouldPrint) asm.noteBytecode(biStart, "bastore");
	emit_bastore();
	break;
      }

      case 0x55: /* castore */ {
	if (shouldPrint) asm.noteBytecode(biStart, "castore");
	emit_castore();
	break;
      }

      case 0x56: /* sastore */ {
	if (shouldPrint) asm.noteBytecode(biStart, "sastore");
	emit_sastore();
	break;
      }

      case 0x57: /* pop */ {
	if (shouldPrint) asm.noteBytecode(biStart, "pop");
	emit_pop();
	break;
      }

      case 0x58: /* pop2 */ {
	if (shouldPrint) asm.noteBytecode(biStart, "pop2");
	emit_pop2();
	break;
      }

      case 0x59: /* dup */ {
	if (shouldPrint) asm.noteBytecode(biStart, "dup");
	emit_dup();
	break;
      } 

      case 0x5a: /* dup_x1 */ {
	if (shouldPrint) asm.noteBytecode(biStart, "dup_x1");
	emit_dup_x1();
	break;
      }

      case 0x5b: /* dup_x2 */ {
	if (shouldPrint) asm.noteBytecode(biStart, "dup_x2");
	emit_dup_x2();
	break;
      }

      case 0x5c: /* dup2 */ {
	if (shouldPrint) asm.noteBytecode(biStart, "dup2");
	emit_dup2();
	break;
      }

      case 0x5d: /* dup2_x1 */ {
	if (shouldPrint) asm.noteBytecode(biStart, "dup2_x1");
	emit_dup2_x1();
	break;
      }

      case 0x5e: /* dup2_x2 */ {
	if (shouldPrint) asm.noteBytecode(biStart, "dup2_x2");
	emit_dup2_x2();
	break;
      }

      case 0x5f: /* swap */ {
	if (shouldPrint) asm.noteBytecode(biStart, "swap");
	emit_swap();
	break;
      }

      case 0x60: /* iadd */ {
	if (shouldPrint) asm.noteBytecode(biStart, "iadd");
	emit_iadd();
	break;
      }

      case 0x61: /* ladd */ {
	if (shouldPrint) asm.noteBytecode(biStart, "ladd");
	emit_ladd();
	break;
      }

      case 0x62: /* fadd */ {
	if (shouldPrint) asm.noteBytecode(biStart, "fadd");
	emit_fadd();
	break;
      }

      case 0x63: /* dadd */ {
	if (shouldPrint) asm.noteBytecode(biStart, "dadd");
	emit_dadd();
	break;
      }

      case 0x64: /* isub */ {
	if (shouldPrint) asm.noteBytecode(biStart, "isub");
	emit_isub();
	break;
      }

      case 0x65: /* lsub */ {
	if (shouldPrint) asm.noteBytecode(biStart, "lsub");
	emit_lsub();
	break;
      }

      case 0x66: /* fsub */ {
	if (shouldPrint) asm.noteBytecode(biStart, "fsub");
	emit_fsub();
	break;
      }

      case 0x67: /* dsub */ {
	if (shouldPrint) asm.noteBytecode(biStart, "dsub");
	emit_dsub();
	break;
      }

      case 0x68: /* imul */ {
	if (shouldPrint) asm.noteBytecode(biStart, "imul");
	emit_imul();
	break;
      }

      case 0x69: /* lmul */ {
	if (shouldPrint) asm.noteBytecode(biStart, "lmul");
	emit_lmul();
	break;
      }

      case 0x6a: /* fmul */ {
	if (shouldPrint) asm.noteBytecode(biStart, "fmul");
	emit_fmul();
	break;
      }

      case 0x6b: /* dmul */ {
	if (shouldPrint) asm.noteBytecode(biStart, "dmul");
	emit_dmul();
	break;
      }

      case 0x6c: /* idiv */ {
	if (shouldPrint) asm.noteBytecode(biStart, "idiv");
	emit_idiv();
	break;
      }

      case 0x6d: /* ldiv */ {
	if (shouldPrint) asm.noteBytecode(biStart, "ldiv");
	emit_ldiv();
	break;
      }

      case 0x6e: /* fdiv */ {
	if (shouldPrint) asm.noteBytecode(biStart, "fdiv");
	emit_fdiv();
	break;
      }

      case 0x6f: /* ddiv */ {
	if (shouldPrint) asm.noteBytecode(biStart, "ddiv");
	emit_ddiv();
	break;
      }

      case 0x70: /* irem */ {
	if (shouldPrint) asm.noteBytecode(biStart, "irem");
	emit_irem();
	break;
      }

      case 0x71: /* lrem */ {
	if (shouldPrint) asm.noteBytecode(biStart, "lrem");
	emit_lrem();
	break;
      }

      case 0x72: /* frem */ {
	if (shouldPrint) asm.noteBytecode(biStart, "frem"); 
	emit_frem();
	break;
      }

      case 0x73: /* drem */ {
	if (shouldPrint) asm.noteBytecode(biStart, "drem");
	emit_drem();
	break;
      }

      case 0x74: /* ineg */ {
	if (shouldPrint) asm.noteBytecode(biStart, "ineg");
	emit_ineg();
	break;
      }

      case 0x75: /* lneg */ {
	if (shouldPrint) asm.noteBytecode(biStart, "lneg");
	emit_lneg();
	break;
      }

      case 0x76: /* fneg */ {
	if (shouldPrint) asm.noteBytecode(biStart, "fneg");
	emit_fneg();
	break;
      }

      case 0x77: /* dneg */ {
	if (shouldPrint) asm.noteBytecode(biStart, "dneg");
	emit_dneg();
	break;
      }

      case 0x78: /* ishl */ {
	if (shouldPrint) asm.noteBytecode(biStart, "ishl");
	emit_ishl();
	break;
      }

      case 0x79: /* lshl */ {
	if (shouldPrint) asm.noteBytecode(biStart, "lshl");    // l >> n
	emit_lshl();
	break;
      }

      case 0x7a: /* ishr */ {
	if (shouldPrint) asm.noteBytecode(biStart, "ishr");
	emit_ishr();
	break;
      }

      case 0x7b: /* lshr */ {
	if (shouldPrint) asm.noteBytecode(biStart, "lshr");
	emit_lshr();
	break;
      }

      case 0x7c: /* iushr */ {
	if (shouldPrint) asm.noteBytecode(biStart, "iushr");
	emit_iushr();
	break;
      }

      case 0x7d: /* lushr */ {
	if (shouldPrint) asm.noteBytecode(biStart, "lushr");
	emit_lushr();
	break;
      }

      case 0x7e: /* iand */ {
	if (shouldPrint) asm.noteBytecode(biStart, "iand");
	emit_iand();
	break;
      }

      case 0x7f: /* land */ {
	if (shouldPrint) asm.noteBytecode(biStart, "land");
	emit_land();
	break;
      }

      case 0x80: /* ior */ {
	if (shouldPrint) asm.noteBytecode(biStart, "ior");
	emit_ior();
	break;
      }

      case 0x81: /* lor */ {
	if (shouldPrint) asm.noteBytecode(biStart, "lor");
	emit_lor();
	break;
      }

      case 0x82: /* ixor */ {
	if (shouldPrint) asm.noteBytecode(biStart, "ixor");
	emit_ixor();
	break;
      }

      case 0x83: /* lxor */ {
	if (shouldPrint) asm.noteBytecode(biStart, "lxor");
	emit_lxor();
	break;
      }

      case 0x84: /* iinc */ {
	int index = fetch1ByteUnsigned();
	int val = fetch1ByteSigned();
	if (shouldPrint) asm.noteBytecode(biStart, "iinc " + index + " " + val);
	emit_iinc(index, val);
	break;
      }

      case 0x85: /* i2l */ {
	if (shouldPrint) asm.noteBytecode(biStart, "i2l");
	emit_i2l();
	break;
      }

      case 0x86: /* i2f */ {
	if (shouldPrint) asm.noteBytecode(biStart, "i2f");
	emit_i2f();
	break;
      }

      case 0x87: /* i2d */ {
	if (shouldPrint) asm.noteBytecode(biStart, "i2d");
	emit_i2d();
	break;
      }

      case 0x88: /* l2i */ {
	if (shouldPrint) asm.noteBytecode(biStart, "l2i");
	emit_l2i();
	break;
      }

      case 0x89: /* l2f */ {
	if (shouldPrint) asm.noteBytecode(biStart, "l2f");
	emit_l2f();
	break;
      }

      case 0x8a: /* l2d */ {
	if (shouldPrint) asm.noteBytecode(biStart, "l2d");
	emit_l2d();
	break;
      }

      case 0x8b: /* f2i */ {
	if (shouldPrint) asm.noteBytecode(biStart, "f2i");
	emit_f2i();
	break;
      }

      case 0x8c: /* f2l */ {
	if (shouldPrint) asm.noteBytecode(biStart, "f2l");
	emit_f2l();
	break;
      }

      case 0x8d: /* f2d */ {
	if (shouldPrint) asm.noteBytecode(biStart, "f2d");
	emit_f2d();
	break;
      }

      case 0x8e: /* d2i */ {
	if (shouldPrint) asm.noteBytecode(biStart, "d2i");
	emit_d2i();
	break;
      }

      case 0x8f: /* d2l */ {
	if (shouldPrint) asm.noteBytecode(biStart, "d2l");
	emit_d2l();
	break;
      }

      case 0x90: /* d2f */ {
	if (shouldPrint) asm.noteBytecode(biStart, "d2f");
	emit_d2f();
	break;
      }

      case 0x91: /* i2b */ {
	if (shouldPrint) asm.noteBytecode(biStart, "i2b");
	emit_i2b();
	break;
      }

      case 0x92: /* i2c */ {
	if (shouldPrint) asm.noteBytecode(biStart, "i2c");
	emit_i2c();
	break;
      }

      case 0x93: /* i2s */ {
	if (shouldPrint) asm.noteBytecode(biStart, "i2s");
	emit_i2s();
	break;
      }

      case 0x94: /* lcmp */ {
	if (shouldPrint) asm.noteBytecode(biStart, "lcmp");  // a ? b
	emit_lcmp();
	break;
      }

      case 0x95: /* fcmpl */ {
	if (shouldPrint) asm.noteBytecode(biStart, "fcmpl");
	emit_fcmpl();
	break;
      }

      case 0x96: /* fcmpg */ {
	if (shouldPrint) asm.noteBytecode(biStart, "fcmpg");
	emit_fcmpg();
	break;
      }

      case 0x97: /* dcmpl */ {
	if (shouldPrint) asm.noteBytecode(biStart, "dcmpl");
	emit_dcmpl();
	break;
      }

      case 0x98: /* dcmpg */ {
	if (shouldPrint) asm.noteBytecode(biStart, "dcmpg");
	emit_dcmpg();
	break;
      }

      case 0x99: /* ifeq */ {
	int offset = fetch2BytesSigned();
	int bTarget = biStart + offset;
	if (shouldPrint) asm.noteBytecode(biStart, "ifeq " + offset + " [" + bTarget + "] ");
	if (offset < 0) emit_threadSwitchTest(VM_Thread.BACKEDGE);
	emit_ifeq(bTarget);
	break;
      }

      case 0x9a: /* ifne */ {
	int offset = fetch2BytesSigned();
	int bTarget = biStart + offset;
	if (shouldPrint) asm.noteBytecode(biStart, "ifne " + offset + " [" + bTarget + "] ");
	if (offset < 0) emit_threadSwitchTest(VM_Thread.BACKEDGE);
	emit_ifne(bTarget);
	break;
      }

      case 0x9b: /* iflt */ {
	int offset = fetch2BytesSigned();
	int bTarget = biStart + offset;
	if (shouldPrint) asm.noteBytecode(biStart, "iflt " + offset + " [" + bTarget + "] ");
	if (offset < 0) emit_threadSwitchTest(VM_Thread.BACKEDGE);
	emit_iflt(bTarget);
	break;
      }

      case 0x9c: /* ifge */ {
	int offset = fetch2BytesSigned();
	int bTarget = biStart + offset;
	if (shouldPrint) asm.noteBytecode(biStart, "ifge " + offset + " [" + bTarget + "] ");
	if (offset < 0) emit_threadSwitchTest(VM_Thread.BACKEDGE);
	emit_ifge(bTarget);
	break;
      }

      case 0x9d: /* ifgt */ {
	int offset = fetch2BytesSigned();
	int bTarget = biStart + offset;
	if (shouldPrint) asm.noteBytecode(biStart, "ifgt " + offset + " [" + bTarget + "] ");
	if (offset < 0) emit_threadSwitchTest(VM_Thread.BACKEDGE);
	emit_ifgt(bTarget);
	break;
      }

      case 0x9e: /* ifle */ {
	int offset = fetch2BytesSigned();
	int bTarget = biStart + offset;
	if (shouldPrint) asm.noteBytecode(biStart, "ifle " + offset + " [" + bTarget + "] ");
	if (offset < 0) emit_threadSwitchTest(VM_Thread.BACKEDGE);
	emit_ifle(bTarget);
	break;
      }

      case 0x9f: /* if_icmpeq */ {
	int offset = fetch2BytesSigned();
	int bTarget = biStart + offset;
	if (shouldPrint) asm.noteBytecode(biStart, "if_icmpeq " + offset + " [" + bTarget + "] ");
	if (offset < 0) emit_threadSwitchTest(VM_Thread.BACKEDGE);
	emit_if_icmpeq(bTarget);
	break;
      }

      case 0xa0: /* if_icmpne */ {
	int offset = fetch2BytesSigned();
	int bTarget = biStart + offset;
	if (shouldPrint) asm.noteBytecode(biStart, "if_icmpne " + offset + " [" + bTarget + "] ");
	if (offset < 0) emit_threadSwitchTest(VM_Thread.BACKEDGE);
	emit_if_icmpne(bTarget);
	break;
      }

      case 0xa1: /* if_icmplt */ {
	int offset = fetch2BytesSigned();
	int bTarget = biStart + offset;
	if (shouldPrint) asm.noteBytecode(biStart, "if_icmplt " + offset + " [" + bTarget + "] ");
	if (offset < 0) emit_threadSwitchTest(VM_Thread.BACKEDGE);
	emit_if_icmplt(bTarget);
	break;
      }

      case 0xa2: /* if_icmpge */ {
	int offset = fetch2BytesSigned();
	int bTarget = biStart + offset;
	if (shouldPrint) asm.noteBytecode(biStart, "if_icmpge " + offset + " [" + bTarget + "] ");
	if (offset < 0) emit_threadSwitchTest(VM_Thread.BACKEDGE);
	emit_if_icmpge(bTarget);
	break;
      }

      case 0xa3: /* if_icmpgt */ {
	int offset = fetch2BytesSigned();
	int bTarget = biStart + offset;
	if (shouldPrint) asm.noteBytecode(biStart, "if_icmpgt " + offset + " [" + bTarget + "] ");
	if (offset < 0) emit_threadSwitchTest(VM_Thread.BACKEDGE);
	emit_if_icmpgt(bTarget);
	break;
      }

      case 0xa4: /* if_icmple */ {
	int offset = fetch2BytesSigned();
	int bTarget = biStart + offset;
	if (shouldPrint) asm.noteBytecode(biStart, "if_icmple " + offset + " [" + bTarget + "] ");
	if (offset < 0) emit_threadSwitchTest(VM_Thread.BACKEDGE);
	emit_if_icmple(bTarget);
	break;
      }

      case 0xa5: /* if_acmpeq */ {
	int offset = fetch2BytesSigned();
	int bTarget = biStart + offset;
	if (shouldPrint) asm.noteBytecode(biStart, "if_acmpeq " + offset + " [" + bTarget + "] ");
	if (offset < 0) emit_threadSwitchTest(VM_Thread.BACKEDGE);
	emit_if_acmpeq(bTarget);
	break;
      }

      case 0xa6: /* if_acmpne */ {
	int offset = fetch2BytesSigned();
	int bTarget = biStart + offset;
	if (shouldPrint) asm.noteBytecode(biStart, "if_acmpne " + offset + " [" + bTarget + "] ");
	if (offset < 0) emit_threadSwitchTest(VM_Thread.BACKEDGE);
	emit_if_acmpne(bTarget);
	break;
      }

      case 0xa7: /* goto */ {
	int offset = fetch2BytesSigned();
	int bTarget = biStart + offset; // bi has been bumped by 3 already
	if (shouldPrint) asm.noteBytecode(biStart, "goto " + offset + " [" + bTarget + "] ");
	if (offset < 0) emit_threadSwitchTest(VM_Thread.BACKEDGE);
	emit_goto(bTarget);
	break;
      }

      case 0xa8: /* jsr */ {
	int offset = fetch2BytesSigned();
	int bTarget = biStart + offset;
	if (shouldPrint) asm.noteBytecode(biStart, "jsr " + offset + " [" + bTarget + "] ");
	emit_jsr(bTarget);
	break;
      }

      case 0xa9: /* ret */ {
	int index = fetch1ByteUnsigned();
	if (shouldPrint) asm.noteBytecode(biStart, "ret " +index);
	emit_ret(index);
	break;
      }

      case 0xaa: /* tableswitch */ {
	bi = (bi+3) & -4; // eat padding
	int defaultval = fetch4BytesSigned();
	int low = fetch4BytesSigned();
	int high = fetch4BytesSigned();
	if (shouldPrint) asm.noteBytecode(biStart, "tableswitch [" + low + "--" + high + "] " + defaultval);
	emit_tableswitch(defaultval, low, high);
	break;
      }

      case 0xab: /* lookupswitch */ {
	bi = (bi+3) & -4; // eat padding
	int defaultval = fetch4BytesSigned();
	int npairs = fetch4BytesSigned();
	if (shouldPrint) asm.noteBytecode(biStart, "lookupswitch [<" + npairs + ">]" + defaultval);
	emit_lookupswitch(defaultval, npairs);
	break;
      }

      case 0xac: /* ireturn */ {
	if (shouldPrint) asm.noteBytecode(biStart, "ireturn");
 	if (VM.UseEpilogueYieldPoints) emit_threadSwitchTest(VM_Thread.EPILOGUE);
	emit_ireturn();
	break;
      }

      case 0xad: /* lreturn */ {
	if (shouldPrint) asm.noteBytecode(biStart, "lreturn");
 	if (VM.UseEpilogueYieldPoints) emit_threadSwitchTest(VM_Thread.EPILOGUE);
	emit_lreturn();
	break;
      }

      case 0xae: /* freturn */ {
	if (shouldPrint) asm.noteBytecode(biStart, "freturn");
 	if (VM.UseEpilogueYieldPoints) emit_threadSwitchTest(VM_Thread.EPILOGUE);
	emit_freturn();
	break;
      }

      case 0xaf: /* dreturn */ {
	if (shouldPrint) asm.noteBytecode(biStart, "dreturn");
 	if (VM.UseEpilogueYieldPoints) emit_threadSwitchTest(VM_Thread.EPILOGUE);
	emit_dreturn();
	break;
      }

      case 0xb0: /* areturn */ {
	if (shouldPrint) asm.noteBytecode(biStart, "areturn");
 	if (VM.UseEpilogueYieldPoints) emit_threadSwitchTest(VM_Thread.EPILOGUE);
	emit_areturn();
	break;
      }

      case 0xb1: /* return */ {
	if (shouldPrint) asm.noteBytecode(biStart, "return");
 	if (VM.UseEpilogueYieldPoints) emit_threadSwitchTest(VM_Thread.EPILOGUE);
	emit_return();
	break;
      }

      case 0xb2: /* getstatic */ {
	int constantPoolIndex = fetch2BytesUnsigned();
	VM_Field fieldRef = klass.getFieldRef(constantPoolIndex);
	if (shouldPrint) asm.noteBytecode(biStart, "getstatic " + constantPoolIndex  + " (" + fieldRef + ")");
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
	    !(fieldRefClass.isInBootImage() && VM.writingBootImage)) { 
	  if (VM.VerifyUnint && !isInterruptible) forbiddenBytecode("unresolved getstatic "+fieldRef);
	  emit_initializeClassIfNeccessary(fieldRefClass.getDictionaryId());
	  classPreresolved = true;
	}
	if (fieldRef.needsDynamicLink(method) && !classPreresolved) {
	  if (VM.VerifyAssertions) VM.assert(!VM.BuildForStrongVolatileSemantics); // Either VM.BuildForPrematureClassResolution was not set or the class was not found (these cases are not yet handled)
	  if (VM.VerifyUnint && !isInterruptible) forbiddenBytecode("unresolved getstatic "+fieldRef);
	  emit_unresolved_getstatic(fieldRef);
	} else {
	  emit_resolved_getstatic(fieldRef.resolve());
	}
	break;
      }
      
      case 0xb3: /* putstatic */ {
	int constantPoolIndex = fetch2BytesUnsigned();
	int fieldId = klass.getFieldRefId(constantPoolIndex);
	VM_Field fieldRef = VM_FieldDictionary.getValue(fieldId);
	if (shouldPrint) asm.noteBytecode(biStart, "putstatic " + constantPoolIndex + " (" + fieldRef + ")");
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
	    !(fieldRefClass.isInBootImage() && VM.writingBootImage)) { 
	  if (VM.VerifyUnint && !isInterruptible) forbiddenBytecode("unresolved putstatic "+fieldRef);
	  emit_initializeClassIfNeccessary(fieldRefClass.getDictionaryId());
	  classPreresolved = true;
	}
	if (fieldRef.needsDynamicLink(method) && !classPreresolved) {
	  if (VM.VerifyAssertions) VM.assert(!VM.BuildForStrongVolatileSemantics); // Either VM.BuildForPrematureClassResolution was not set or the class was not found (these cases are not yet handled)
	  if (VM.VerifyUnint && !isInterruptible) forbiddenBytecode("unresolved putstatic "+fieldRef);
	  emit_unresolved_putstatic(fieldRef);
	} else {
	  emit_resolved_putstatic(fieldRef.resolve());
	}
	break;
      }

      case 0xb4: /* getfield */ {
	int constantPoolIndex = fetch2BytesUnsigned();
	VM_Field fieldRef = klass.getFieldRef(constantPoolIndex);
	if (shouldPrint) asm.noteBytecode(biStart, "getfield " + constantPoolIndex  + " (" + fieldRef + ")");
	boolean classPreresolved = false;
	VM_Class fieldRefClass = fieldRef.getDeclaringClass();
	if (fieldRef.needsDynamicLink(method) && VM.BuildForPrematureClassResolution) {
	  try {
	    fieldRefClass.load();
	    fieldRefClass.resolve();
	    if (VM.VerifyUnint && !isInterruptible) forbiddenBytecode("unresolved getfield "+fieldRef);
	    classPreresolved = true;
	  } catch (Exception e) { 
	    System.err.println("WARNING: during compilation of " + method + " premature resolution of " + fieldRefClass + " provoked the following exception: " + e); // TODO!! remove this  warning message
	  } // report the exception at runtime
	}
	if (fieldRef.needsDynamicLink(method) && !classPreresolved) {
	  if (VM.VerifyAssertions) VM.assert(!VM.BuildForStrongVolatileSemantics); // Either VM.BuildForPrematureClassResolution was not set or the class was not found (these cases are not yet handled)
	  if (VM.VerifyUnint && !isInterruptible) forbiddenBytecode("unresolved getfield "+fieldRef);
	  emit_unresolved_getfield(fieldRef);
	} else {
	  emit_resolved_getfield(fieldRef.resolve());
	}
	break;
      }

      case 0xb5: /* putfield */ {
	int constantPoolIndex = fetch2BytesUnsigned();
	int fieldId = klass.getFieldRefId(constantPoolIndex);
	VM_Field fieldRef = VM_FieldDictionary.getValue(fieldId);
	if (shouldPrint) asm.noteBytecode(biStart, "putfield " + constantPoolIndex + " (" + fieldRef + ")");
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
	  if (VM.VerifyAssertions) VM.assert(!VM.BuildForStrongVolatileSemantics); // Either VM.BuildForPrematureClassResolution was not set or the class was not found (these cases are not yet handled)
	  if (VM.VerifyUnint && !isInterruptible) forbiddenBytecode("unresolved putfield "+fieldRef);
	  emit_unresolved_putfield(fieldRef);
	} else {
	  emit_resolved_putfield(fieldRef.resolve());
	}
	break;
      }  

      case 0xb6: /* invokevirtual */ {
	int constantPoolIndex = fetch2BytesUnsigned();
	VM_Method methodRef = klass.getMethodRef(constantPoolIndex);
	if (shouldPrint) asm.noteBytecode(biStart, "invokevirtual " + constantPoolIndex + " (" + methodRef + ")");
	if (methodRef.getDeclaringClass().isAddressType()) {
	  emit_Magic(methodRef);
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
	if (methodRef.needsDynamicLink(method) && !classPreresolved) {
	  if (VM.VerifyUnint && !isInterruptible) forbiddenBytecode("unresolved invokevirtual "+methodRef);
	  emit_unresolved_invokevirtual(methodRef);
	} else {
	  methodRef = methodRef.resolve();
	  if (VM.VerifyUnint && !isInterruptible) checkTarget(methodRef);
	  emit_resolved_invokevirtual(methodRef);
	}
	break;
      }

      case 0xb7: /* invokespecial */ {
	int constantPoolIndex = fetch2BytesUnsigned();
	VM_Method methodRef = klass.getMethodRef(constantPoolIndex);
	if (shouldPrint) asm.noteBytecode(biStart, "invokespecial " + constantPoolIndex + " (" + methodRef + ")");
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
	  if (VM.VerifyUnint && !isInterruptible) checkTarget(target);
	  emit_resolved_invokespecial(methodRef, target);
	} else {
	  emit_unresolved_invokespecial(methodRef);
	}     
	break;
      }

      case 0xb8: /* invokestatic */ {
	int constantPoolIndex = fetch2BytesUnsigned();
	VM_Method methodRef = klass.getMethodRef(constantPoolIndex);
	if (shouldPrint) asm.noteBytecode(biStart, "invokestatic " + constantPoolIndex + " (" + methodRef + ")");
	if (methodRef.getDeclaringClass().isMagicType() ||
	    methodRef.getDeclaringClass().isAddressType()) {
	  emit_Magic(methodRef);
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
	    !(methodRefClass.isInBootImage() && VM.writingBootImage)) {
	  if (VM.VerifyUnint && !isInterruptible) forbiddenBytecode("unresolved invokestatic "+methodRef);
	  emit_initializeClassIfNeccessary(methodRefClass.getDictionaryId());
	  classPreresolved = true;
	}
	if (methodRef.needsDynamicLink(method) && !classPreresolved) {
	  if (VM.VerifyUnint && !isInterruptible) forbiddenBytecode("unresolved invokestatic "+methodRef);
	  emit_unresolved_invokestatic(methodRef);
	} else {
	  methodRef = methodRef.resolve();
	  if (VM.VerifyUnint && !isInterruptible) checkTarget(methodRef);
	  emit_resolved_invokestatic(methodRef);
	}
	break;
      }

      case 0xb9: /* invokeinterface --- */ {
	int constantPoolIndex = fetch2BytesUnsigned();
	VM_Method methodRef = klass.getMethodRef(constantPoolIndex);
	int count = fetch1ByteUnsigned();
	fetch1ByteSigned(); // eat superfluous 0
	if (shouldPrint) asm.noteBytecode(biStart, "invokeinterface " + constantPoolIndex + " (" + methodRef + ") " + count + " 0");
	if (VM.VerifyUnint && !isInterruptible) forbiddenBytecode("invokeinterface "+methodRef);
	emit_invokeinterface(methodRef, count); 
	break;
      }

      case 0xba: /* unused */ {
	if (shouldPrint) asm.noteBytecode(biStart, "unused");
	if (VM.VerifyAssertions) VM.assert(VM.NOT_REACHED);
	break;
      }

      case 0xbb: /* new */ {
	int constantPoolIndex = fetch2BytesUnsigned();
	VM_Class typeRef = klass.getTypeRef(constantPoolIndex).asClass();
	if (shouldPrint) asm.noteBytecode(biStart, "new " + constantPoolIndex + " (" + typeRef + ")");
	if (VM.VerifyUnint && !isInterruptible) forbiddenBytecode("new "+typeRef);
	if (typeRef.isInitialized() || typeRef.isInBootImage()) { 
	  emit_resolved_new(typeRef);
	} else { 
	  emit_unresolved_new(klass.getTypeRefId(constantPoolIndex));
	}
	break;
      }

      case 0xbc: /* newarray */ {
	int atype = fetch1ByteSigned();
	VM_Array array = VM_Array.getPrimitiveArrayType(atype);
	array.resolve();
	array.instantiate();
	if (shouldPrint) asm.noteBytecode(biStart, "newarray " + atype + "(" + array + ")");
	if (VM.VerifyUnint && !isInterruptible) forbiddenBytecode("new "+array);
	emit_newarray(array);
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
	if (shouldPrint) asm.noteBytecode(biStart, "anewarray new " + constantPoolIndex + " (" + array + ")");
	if (VM.VerifyUnint && !isInterruptible) forbiddenBytecode("new "+array);
	emit_newarray(array);
	break;
      }

      case 0xbe: /* arraylength */ { 
	if (shouldPrint) asm.noteBytecode(biStart, "arraylength");
	emit_arraylength();
	break;
      }

      case 0xbf: /* athrow */ {
	if (shouldPrint) asm.noteBytecode(biStart, "athrow");  
 	if (VM.UseEpilogueYieldPoints) emit_threadSwitchTest(VM_Thread.EPILOGUE);
	if (VM.VerifyUnint && !isInterruptible) forbiddenBytecode("athrow");
	emit_athrow();
	break;
      }

      case 0xc0: /* checkcast */ {
	int constantPoolIndex = fetch2BytesUnsigned();
	VM_Type typeRef = klass.getTypeRef(constantPoolIndex);
	if (shouldPrint) asm.noteBytecode(biStart, "checkcast " + constantPoolIndex + " (" + typeRef + ")");
	VM_Method target = VM_Entrypoints.checkcastMethod;
	if (typeRef.isClassType() && typeRef.isLoaded() && typeRef.asClass().isFinal()) {
	  target = VM_Entrypoints.checkcastFinalMethod;
	} else if (typeRef.isArrayType()) {
	  VM_Type elemType = typeRef.asArray().getElementType();
	  if (elemType.isPrimitiveType() || 
	      (elemType.isClassType() && elemType.isLoaded() && elemType.asClass().isFinal())) {
	    target = VM_Entrypoints.checkcastFinalMethod;
	  }
	}
	if (VM.VerifyUnint && !isInterruptible && target != VM_Entrypoints.checkcastFinalMethod) forbiddenBytecode("checkcast "+typeRef);
	emit_checkcast(typeRef, target);
	break;
      }

      case 0xc1: /* instanceof */ {
	int constantPoolIndex = fetch2BytesUnsigned();
	VM_Type typeRef = klass.getTypeRef(constantPoolIndex);
	if (shouldPrint) asm.noteBytecode(biStart, "instanceof " + constantPoolIndex  + " (" + typeRef + ")");
	VM_Method target = VM_Entrypoints.instanceOfMethod;
	if (typeRef.isClassType() && typeRef.isLoaded() && typeRef.asClass().isFinal()) {
	  target = VM_Entrypoints.instanceOfFinalMethod;
	} else if (typeRef.isArrayType()) {
	  VM_Type elemType = typeRef.asArray().getElementType();
	  if (elemType.isPrimitiveType() || 
	      (elemType.isClassType() && elemType.isLoaded() && elemType.asClass().isFinal())) {
	    target = VM_Entrypoints.instanceOfFinalMethod;
	  }
	}
	if (VM.VerifyUnint && !isInterruptible && target != VM_Entrypoints.instanceOfFinalMethod) forbiddenBytecode("checkcast "+typeRef);
	emit_instanceof(typeRef, target);
	break;
      }

      case 0xc2: /* monitorenter  */ {
	if (shouldPrint) asm.noteBytecode(biStart, "monitorenter");  
	if (VM.VerifyUnint && !isInterruptible) forbiddenBytecode("monitorenter");
	emit_monitorenter();
	break;
      }

      case 0xc3: /* monitorexit */ {
	if (shouldPrint) asm.noteBytecode(biStart, "monitorexit"); 
	if (VM.VerifyUnint && !isInterruptible) forbiddenBytecode("monitorexit");
	emit_monitorexit();
	break;
      }

      case 0xc4: /* wide */ {
	int widecode = fetch1ByteUnsigned();
	int index = fetch2BytesUnsigned();
	switch (widecode) {
	case 0x15: /* --- wide iload --- */ {
	  if (shouldPrint) asm.noteBytecode(biStart, "wide iload " + index);
	  emit_iload(index);
	  break;
	}
	case 0x16: /* --- wide lload --- */ {
	  if (shouldPrint) asm.noteBytecode(biStart, "wide lload " + index);
	  emit_lload(index);
	  break;
	}
	case 0x17: /* --- wide fload --- */ {
	  if (shouldPrint) asm.noteBytecode(biStart, "wide fload " + index);
	  emit_fload(index);
	  break;
	}
	case 0x18: /* --- wide dload --- */ {
	  if (shouldPrint) asm.noteBytecode(biStart, "wide dload " + index);
	  emit_dload(index);
	  break;
	}
	case 0x19: /* --- wide aload --- */ {
	  if (shouldPrint) asm.noteBytecode(biStart, "wide aload " + index);
	  emit_aload(index);
	  break;
	}
	case 0x36: /* --- wide istore --- */ {
	  if (shouldPrint) asm.noteBytecode(biStart, "wide istore " + index);
	  emit_istore(index);
	  break;
	}
	case 0x37: /* --- wide lstore --- */ {
	  if (shouldPrint) asm.noteBytecode(biStart, "wide lstore " + index);
	  emit_lstore(index);
	  break;
	}
	case 0x38: /* --- wide fstore --- */ {
	  if (shouldPrint) asm.noteBytecode(biStart, "wide fstore " + index);
	  emit_fstore(index);
	  break;
	}
	case 0x39: /* --- wide dstore --- */ {
	  if (shouldPrint) asm.noteBytecode(biStart, "wide dstore " + index);
	  emit_dstore(index);
	  break;
	}
	case 0x3a: /* --- wide astore --- */ {
	  if (shouldPrint) asm.noteBytecode(biStart, "wide astore " + index);
	  emit_astore(index);
	  break;
	}
	case 0x84: /* --- wide iinc --- */ {
	  int val = fetch2BytesSigned();
	  if (shouldPrint) asm.noteBytecode(biStart, "wide inc " + index + " by " + val);
	  emit_iinc(index, val);
	  break;
	}
	case 0x9a: /* --- wide ret --- */ {
	  if (shouldPrint) asm.noteBytecode(biStart, "wide ret " + index);
	  emit_ret(index);
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
	VM_Array typeRef      = klass.getTypeRef(constantPoolIndex).asArray();
	int dictionaryId      = klass.getTypeRefId(constantPoolIndex);
	if (shouldPrint) asm.noteBytecode(biStart, "multianewarray " + constantPoolIndex + " (" + typeRef + ") " + dimensions);
	if (VM.VerifyUnint && !isInterruptible) forbiddenBytecode("multianewarray");
	emit_multianewarray(typeRef, dimensions, dictionaryId);
	break;
      }

      case 0xc6: /* ifnull */ {
	int offset = fetch2BytesSigned();
	int bTarget = biStart + offset;
	if (shouldPrint) asm.noteBytecode(biStart, "ifnull " + offset + " [" + bTarget + "] ");
	if (offset < 0) emit_threadSwitchTest(VM_Thread.BACKEDGE);
	emit_ifnull(bTarget);
	break;
      }

      case 0xc7: /* ifnonnull */ {
	int offset = fetch2BytesSigned();
	int bTarget = biStart + offset;
	if (shouldPrint) asm.noteBytecode(biStart, "ifnonnull " + offset + " [" + bTarget + "] ");
	if (offset < 0) emit_threadSwitchTest(VM_Thread.BACKEDGE);
	emit_ifnonnull(bTarget);
	break;
      }

      case 0xc8: /* goto_w */ {
	int offset = fetch4BytesSigned();
	int bTarget = biStart + offset; // bi has been bumped by 5 already
	if (shouldPrint) asm.noteBytecode(biStart, "goto_w " + offset + " [" + bTarget + "] ");
	if(offset < 0) emit_threadSwitchTest(VM_Thread.BACKEDGE);
	emit_goto(bTarget);
	break;
      }

      case 0xc9: /* jsr_w */ {
	int offset = fetch4BytesSigned();
	int bTarget = biStart + offset;
	if (shouldPrint) asm.noteBytecode(biStart, "jsr_w " + offset + " [" + bTarget + "] ");
	emit_jsr(bTarget);
	break;
      }

      default:
	VM.sysWrite("VM_Compiler: unexpected bytecode: " + VM_Services.getHexString((int)code, false) + "\n");
	if (VM.VerifyAssertions) VM.assert(VM.NOT_REACHED);
      }
    }
    return asm.finalizeMachineCode(bytecodeMap);
  }


  /**
   * Print a warning message whan we compile a bytecode that is forbidden in
   * Uninterruptible code.
   * 
   * @param msg description of bytecode that is violating the invariant
   */
  protected final void forbiddenBytecode(String msg) {
    if (!VM.ParanoidVerifyUnint && VM_PragmaLogicallyUninterruptible.declaredBy(method)) return; //Programmer has asserted that we don't have to do checking for this method.
    VM.sysWriteln("WARNING "+ method + ": contains forbidden bytecode "+msg);
  }

  /**
   * Ensure that the callee method is safe to invoke from uninterruptible code
   * 
   * @param target the target methodRef
   */
  protected final void checkTarget(VM_Method target) {
    if (!VM.ParanoidVerifyUnint && VM_PragmaLogicallyUninterruptible.declaredBy(method)) return; //Programmer has asserted that we don't have to do checking for this method.
    if (target.isInterruptible()) {
      VM.sysWriteln("WARNING "+ method + ": contains call to interruptible method "+target);
    }
  }


  /*
   * The target-specific VM_Compiler class must implement the 
   * following (lengthy) list of abstract methods.
   * Porting the baseline compiler to a new platform
   * mainly entails implementing all of these methods.
   */


  /*
   * Misc routines not directly tied to a particular bytecode
   */

  /**
   * Emit the prologue for the method
   */
  protected abstract void emit_prologue();

  /**
   * Emit code to complete the dynamic linking of a
   * prematurely resolved VM_Type.
   * @param dictionaryId of type to link (if necessary)
   */
  protected abstract void emit_initializeClassIfNeccessary(int dictionaryId);

  /**
   * Emit the code for a threadswitch tests (aka a yieldpoint).
   * @param whereFrom is this thread switch from a PROLOGUE, BACKEDGE, or EPILOGUE?
   */
  protected abstract void emit_threadSwitchTest(int whereFrom);

  /**
   * Emit the code to implement the spcified magic.
   * @param magicMethod desired magic
   */
  protected abstract void emit_Magic(VM_Method magicMethod);


  /*
   * Loading constants
   */


  /**
   * Emit code to load the null constant.
   */
  protected abstract void emit_aconst_null();

  /**
   * Emit code to load an int constant.
   * @param val the int constant to load
   */
  protected abstract void emit_iconst(int val);

  /**
   * Emit code to load a long constant
   * @param val the lower 32 bits of long constant (upper32 are 0).
   */
  protected abstract void emit_lconst(int val);

  /**
   * Emit code to load 0.0f
   */
  protected abstract void emit_fconst_0();

  /**
   * Emit code to load 1.0f
   */
  protected abstract void emit_fconst_1();

  /**
   * Emit code to load 2.0f
   */
  protected abstract void emit_fconst_2();

  /**
   * Emit code to load 0.0d
   */
  protected abstract void emit_dconst_0();

  /**
   * Emit code to load 1.0d
   */
  protected abstract void emit_dconst_1();

  /**
   * Emit code to load a 32 bit constant
   * @param offset JTOC offset of the constant 
   */
  protected abstract void emit_ldc(int offset);

  /**
   * Emit code to load a 64 bit constant
   * @param offset JTOC offset of the constant 
   */
  protected abstract void emit_ldc2(int offset);


  /*
   * loading local variables
   */


  /**
   * Emit code to load an int local variable
   * @param index the local index to load
   */
  protected abstract void emit_iload(int index);

  /**
   * Emit code to load a long local variable
   * @param index the local index to load
   */
  protected abstract void emit_lload(int index);

  /**
   * Emit code to local a float local variable
   * @param index the local index to load
   */
  protected abstract void emit_fload(int index);

  /**
   * Emit code to load a double local variable
   * @param index the local index to load
   */
  protected abstract void emit_dload(int index);

  /**
   * Emit code to load a reference local variable
   * @param index the local index to load
   */
  protected abstract void emit_aload(int index);


  /*
   * storing local variables
   */


  /**
   * Emit code to store an int to a local variable
   * @param index the local index to load
   */
  protected abstract void emit_istore(int index);

  /**
   * Emit code to store a long to a local variable
   * @param index the local index to load
   */
  protected abstract void emit_lstore(int index);

  /**
   * Emit code to store a float to a local variable
   * @param index the local index to load
   */
  protected abstract void emit_fstore(int index);

  /**
   * Emit code to store an double  to a local variable
   * @param index the local index to load
   */
  protected abstract void emit_dstore(int index);

  /**
   * Emit code to store a reference to a local variable
   * @param index the local index to load
   */
  protected abstract void emit_astore(int index);


  /*
   * array loads
   */


  /**
   * Emit code to load from an int array
   */
  protected abstract void emit_iaload();

  /**
   * Emit code to load from a long array
   */
  protected abstract void emit_laload();

  /**
   * Emit code to load from a float array
   */
  protected abstract void emit_faload();

  /**
   * Emit code to load from a double array
   */
  protected abstract void emit_daload();

  /**
   * Emit code to load from a reference array
   */
  protected abstract void emit_aaload();

  /**
   * Emit code to load from a byte/boolean array
   */
  protected abstract void emit_baload();

  /**
   * Emit code to load from a char array
   */
  protected abstract void emit_caload();

  /**
   * Emit code to load from a short array
   */
  protected abstract void emit_saload();


  /*
   * array stores
   */


  /**
   * Emit code to store to an int array
   */
  protected abstract void emit_iastore();

  /**
   * Emit code to store to a long array
   */
  protected abstract void emit_lastore();

  /**
   * Emit code to store to a float array
   */
  protected abstract void emit_fastore();

  /**
   * Emit code to store to a double array
   */
  protected abstract void emit_dastore();

  /**
   * Emit code to store to a reference array
   */
  protected abstract void emit_aastore();

  /**
   * Emit code to store to a byte/boolean array
   */
  protected abstract void emit_bastore();

  /**
   * Emit code to store to a char array
   */
  protected abstract void emit_castore();

  /**
   * Emit code to store to a short array
   */
  protected abstract void emit_sastore();


  /*
   * expression stack manipulation
   */


  /**
   * Emit code to implement the pop bytecode
   */
  protected abstract void emit_pop();

  /**
   * Emit code to implement the pop2 bytecode
   */
  protected abstract void emit_pop2();

  /**
   * Emit code to implement the dup bytecode
   */
  protected abstract void emit_dup();

  /**
   * Emit code to implement the dup_x1 bytecode
   */
  protected abstract void emit_dup_x1();

  /**
   * Emit code to implement the dup_x2 bytecode
   */
  protected abstract void emit_dup_x2();

  /**
   * Emit code to implement the dup2 bytecode
   */
  protected abstract void emit_dup2();

  /**
   * Emit code to implement the dup2_x1 bytecode
   */
  protected abstract void emit_dup2_x1();

  /**
   * Emit code to implement the dup2_x2 bytecode
   */
  protected abstract void emit_dup2_x2();

  /**
   * Emit code to implement the swap bytecode
   */
  protected abstract void emit_swap();


  /*
   * int ALU
   */


  /**
   * Emit code to implement the iadd bytecode
   */
  protected abstract void emit_iadd();

  /**
   * Emit code to implement the isub bytecode
   */
  protected abstract void emit_isub();

  /**
   * Emit code to implement the imul bytecode
   */
  protected abstract void emit_imul();

  /**
   * Emit code to implement the idiv bytecode
   */
  protected abstract void emit_idiv();

  /**
   * Emit code to implement the irem bytecode
   */
  protected abstract void emit_irem();

  /**
   * Emit code to implement the ineg bytecode
   */
  protected abstract void emit_ineg();

  /**
   * Emit code to implement the ishl bytecode
   */
  protected abstract void emit_ishl();

  /**
   * Emit code to implement the ishr bytecode
   */
  protected abstract void emit_ishr();

  /**
   * Emit code to implement the iushr bytecode
   */
  protected abstract void emit_iushr();

  /**
   * Emit code to implement the iand bytecode
   */
  protected abstract void emit_iand();

  /**
   * Emit code to implement the ior bytecode
   */
  protected abstract void emit_ior();

  /**
   * Emit code to implement the ixor bytecode
   */
  protected abstract void emit_ixor();

  /**
   * Emit code to implement the iinc bytecode
   * @param index index of local
   * @param val value to increment it by
   */
  protected abstract void emit_iinc(int index, int val);


  /*
   * long ALU
   */


  /**
   * Emit code to implement the ladd bytecode
   */
  protected abstract void emit_ladd();

  /**
   * Emit code to implement the lsub bytecode
   */
  protected abstract void emit_lsub();

  /**
   * Emit code to implement the lmul bytecode
   */
  protected abstract void emit_lmul();

  /**
   * Emit code to implement the ldiv bytecode
   */
  protected abstract void emit_ldiv();

  /**
   * Emit code to implement the lrem bytecode
   */
  protected abstract void emit_lrem();

  /**
   * Emit code to implement the lneg bytecode
   */
  protected abstract void emit_lneg();

  /**
   * Emit code to implement the lshsl bytecode
   */
  protected abstract void emit_lshl();

  /**
   * Emit code to implement the lshr bytecode
   */
  protected abstract void emit_lshr();

  /**
   * Emit code to implement the lushr bytecode
   */
  protected abstract void emit_lushr();

  /**
   * Emit code to implement the land bytecode
   */
  protected abstract void emit_land();

  /**
   * Emit code to implement the lor bytecode
   */
  protected abstract void emit_lor();

  /**
   * Emit code to implement the lxor bytecode
   */
  protected abstract void emit_lxor();


  /*
   * float ALU
   */


  /**
   * Emit code to implement the fadd bytecode
   */
  protected abstract void emit_fadd();

  /**
   * Emit code to implement the fsub bytecode
   */
  protected abstract void emit_fsub();

  /**
   * Emit code to implement the fmul bytecode
   */
  protected abstract void emit_fmul();

  /**
   * Emit code to implement the fdiv bytecode
   */
  protected abstract void emit_fdiv();

  /**
   * Emit code to implement the frem bytecode
   */
  protected abstract void emit_frem();

  /**
   * Emit code to implement the fneg bytecode
   */
  protected abstract void emit_fneg();


  /*
   * double ALU
   */


  /**
   * Emit code to implement the dadd bytecode
   */
  protected abstract void emit_dadd();

  /**
   * Emit code to implement the dsub bytecode
   */
  protected abstract void emit_dsub();

  /**
   * Emit code to implement the dmul bytecode
   */
  protected abstract void emit_dmul();

  /**
   * Emit code to implement the ddiv bytecode
   */
  protected abstract void emit_ddiv();

  /**
   * Emit code to implement the drem bytecode
   */
  protected abstract void emit_drem();

  /**
   * Emit code to implement the dneg bytecode
   */
  protected abstract void emit_dneg();


  /*
   * conversion ops
   */


  /**
   * Emit code to implement the i2l bytecode
   */
  protected abstract void emit_i2l();

  /**
   * Emit code to implement the i2f bytecode
   */
  protected abstract void emit_i2f();

  /**
   * Emit code to implement the i2d bytecode
   */
  protected abstract void emit_i2d();

  /**
   * Emit code to implement the l2i bytecode
   */
  protected abstract void emit_l2i();

  /**
   * Emit code to implement the l2f bytecode
   */
  protected abstract void emit_l2f();

  /**
   * Emit code to implement the l2d bytecode
   */
  protected abstract void emit_l2d();

  /**
   * Emit code to implement the f2i bytecode
   */
  protected abstract void emit_f2i();

  /**
   * Emit code to implement the f2l bytecode
   */
  protected abstract void emit_f2l();

  /**
   * Emit code to implement the f2d bytecode
   */
  protected abstract void emit_f2d();

  /**
   * Emit code to implement the d2i bytecode
   */
  protected abstract void emit_d2i();

  /**
   * Emit code to implement the d2l bytecode
   */
  protected abstract void emit_d2l();

  /**
   * Emit code to implement the d2f bytecode
   */
  protected abstract void emit_d2f();

  /**
   * Emit code to implement the i2b bytecode
   */
  protected abstract void emit_i2b();

  /**
   * Emit code to implement the i2c bytecode
   */
  protected abstract void emit_i2c();

  /**
   * Emit code to implement the i2s bytecode
   */
  protected abstract void emit_i2s();


  /*
   * comparision ops
   */


  /**
   * Emit code to implement the lcmp bytecode
   */
  protected abstract void emit_lcmp();

  /**
   * Emit code to implement the fcmpl bytecode
   */
  protected abstract void emit_fcmpl();

  /**
   * Emit code to implement the fcmpg bytecode
   */
  protected abstract void emit_fcmpg();

  /**
   * Emit code to implement the dcmpl bytecode
   */
  protected abstract void emit_dcmpl();

  /**
   * Emit code to implement the dcmpg bytecode
   */
  protected abstract void emit_dcmpg();


  /*
   * branching
   */


  /**
   * Emit code to implement the ifeg bytecode
   * @param bTarget target bytecode of the branch
   */
  protected abstract void emit_ifeq(int bTarget);

  /**
   * Emit code to implement the ifne bytecode
   * @param bTarget target bytecode of the branch
   */
  protected abstract void emit_ifne(int bTarget);

  /**
   * Emit code to implement the iflt bytecode
   * @param bTarget target bytecode of the branch
   */
  protected abstract void emit_iflt(int bTarget);

  /**
   * Emit code to implement the ifge bytecode
   * @param bTarget target bytecode of the branch
   */
  protected abstract void emit_ifge(int bTarget);

  /**
   * Emit code to implement the ifgt bytecode
   * @param bTarget target bytecode of the branch
   */
  protected abstract void emit_ifgt(int bTarget);

  /**
   * Emit code to implement the ifle bytecode
   * @param bTarget target bytecode of the branch
   */
  protected abstract void emit_ifle(int bTarget);

  /**
   * Emit code to implement the if_icmpeq bytecode
   * @param bTarget target bytecode of the branch
   */
  protected abstract void emit_if_icmpeq(int bTarget);

  /**
   * Emit code to implement the if_icmpne bytecode
   * @param bTarget target bytecode of the branch
   */
  protected abstract void emit_if_icmpne(int bTarget);

  /**
   * Emit code to implement the if_icmplt bytecode
   * @param bTarget target bytecode of the branch
   */
  protected abstract void emit_if_icmplt(int bTarget);

  /**
   * Emit code to implement the if_icmpge bytecode
   * @param bTarget target bytecode of the branch
   */
  protected abstract void emit_if_icmpge(int bTarget);

  /**
   * Emit code to implement the if_icmpgt bytecode
   * @param bTarget target bytecode of the branch
   */
  protected abstract void emit_if_icmpgt(int bTarget);

  /**
   * Emit code to implement the if_icmple bytecode
   * @param bTarget target bytecode of the branch
   */
  protected abstract void emit_if_icmple(int bTarget);

  /**
   * Emit code to implement the if_acmpeq bytecode
   * @param bTarget target bytecode of the branch
   */
  protected abstract void emit_if_acmpeq(int bTarget);

  /**
   * Emit code to implement the if_acmpne bytecode
   * @param bTarget target bytecode of the branch
   */
  protected abstract void emit_if_acmpne(int bTarget);

  /**
   * Emit code to implement the ifnull bytecode
   * @param bTarget target bytecode of the branch
   */
  protected abstract void emit_ifnull(int bTarget);

  /**
   * Emit code to implement the ifnonnull bytecode
   * @param bTarget target bytecode of the branch
   */
  protected abstract void emit_ifnonnull(int bTarget);

  /**
   * Emit code to implement the goto and gotow bytecodes
   * @param bTarget target bytecode of the branch
   */
  protected abstract void emit_goto(int bTarget);

  /**
   * Emit code to implement the jsr and jsrw bytecode
   * @param bTarget target bytecode of the jsr
   */
  protected abstract void emit_jsr(int bTarget);

  /**
   * Emit code to implement the ret bytecode
   * @param index local variable containing the return address
   */
  protected abstract void emit_ret(int index);

  /**
   * Emit code to implement the tableswitch bytecode
   * @param defaultval bcIndex of the default target
   * @param low low value of switch
   * @param high high value of switch
   */
  protected abstract void emit_tableswitch(int defaultval, int low, int high);

  /**
   * Emit code to implement the lookupswitch bytecode
   * @param defaultval bcIndex of the default target
   * @param npairs number of pairs in the lookup switch
   */
  protected abstract void emit_lookupswitch(int defaultval, int npairs);


  /*
   * returns (from function; NOT ret)
   */


  /**
   * Emit code to implement the ireturn bytecode
   */
  protected abstract void emit_ireturn();

  /**
   * Emit code to implement the lreturn bytecode
   */
  protected abstract void emit_lreturn();

  /**
   * Emit code to implement the freturn bytecode
   */
  protected abstract void emit_freturn();

  /**
   * Emit code to implement the dreturn bytecode
   */
  protected abstract void emit_dreturn();

  /**
   * Emit code to implement the areturn bytecode
   */
  protected abstract void emit_areturn();

  /**
   * Emit code to implement the return bytecode
   */
  protected abstract void emit_return();


  /*
   * field access
   */


  /**
   * Emit code to implement a dynamically linked getstatic
   * @param fieldRef the referenced field
   */
  protected abstract void emit_unresolved_getstatic(VM_Field fieldRef);

  /**
   * Emit code to implement a getstatic
   * @param fieldRef the referenced field
   */
  protected abstract void emit_resolved_getstatic(VM_Field fieldRef);


  /**
   * Emit code to implement a dynamically linked putstatic
   * @param fieldRef the referenced field
   */
  protected abstract void emit_unresolved_putstatic(VM_Field fieldRef);

  /**
   * Emit code to implement a putstatic
   * @param fieldRef the referenced field
   */
  protected abstract void emit_resolved_putstatic(VM_Field fieldRef);


  /**
   * Emit code to implement a dynamically linked getfield
   * @param fieldRef the referenced field
   */
  protected abstract void emit_unresolved_getfield(VM_Field fieldRef);

  /**
   * Emit code to implement a getfield
   * @param fieldRef the referenced field
   */
  protected abstract void emit_resolved_getfield(VM_Field fieldRef);


  /**
   * Emit code to implement a dynamically linked putfield
   * @param fieldRef the referenced field
   */
  protected abstract void emit_unresolved_putfield(VM_Field fieldRef);

  /**
   * Emit code to implement a putfield
   * @param fieldRef the referenced field
   */
  protected abstract void emit_resolved_putfield(VM_Field fieldRef);


  /*
   * method invocation
   */

  /**
   * Emit code to implement a dynamically linked invokevirtual
   * @param methodRef the referenced method
   */
  protected abstract void emit_unresolved_invokevirtual(VM_Method methodRef);

  /**
   * Emit code to implement invokevirtual
   * @param methodRef the referenced method
   */
  protected abstract void emit_resolved_invokevirtual(VM_Method methodRef);


  /**
   * Emit code to implement a dynamically linked invokespecial
   * @param methodRef the referenced method
   * @param target the method to invoke
   */
  protected abstract void emit_resolved_invokespecial(VM_Method methodRef, VM_Method target);

  /**
   * Emit code to implement invokespecial
   * @param methodRef the referenced method
   */
  protected abstract void emit_unresolved_invokespecial(VM_Method methodRef);


  /**
   * Emit code to implement a dynamically linked invokestatic
   * @param methodRef the referenced method
   */
  protected abstract void emit_unresolved_invokestatic(VM_Method methodRef);

  /**
   * Emit code to implement invokestatic
   * @param methodRef the referenced method
   */
  protected abstract void emit_resolved_invokestatic(VM_Method methodRef);


  /**
   * Emit code to implement the invokeinterface bytecode
   * @param methodRef the referenced method
   * @param count number of parameter words (see invokeinterface bytecode)
   */
  protected abstract void emit_invokeinterface(VM_Method methodRef, int count);
 

  /*
   * other object model functions
   */ 


  /**
   * Emit code to allocate a scalar object
   * @param typeRef the VM_Class to instantiate
   */
  protected abstract void emit_resolved_new(VM_Class typeRef);

  /**
   * Emit code to dynamically link and allocate a scalar object
   * @param the dictionaryId of the VM_Class to dynamically link & instantiate
   */
  protected abstract void emit_unresolved_new(int dictionaryId);

  /**
   * Emit code to allocate an array
   * @param array the VM_Array to instantiate
   */
  protected abstract void emit_newarray(VM_Array array);

  /**
   * Emit code to allocate a multi-dimensional array
   * @param typeRef the VM_Array to instantiate
   * @param dimensions the number of dimensions
   * @param dictionaryId, the dictionaryId of typeRef
   */
  protected abstract void emit_multianewarray(VM_Array typeRef, int dimensions, int dictionaryId);

  /**
   * Emit code to implement the arraylength bytecode
   */
  protected abstract void emit_arraylength();

  /**
   * Emit code to implement the athrow bytecode
   */
  protected abstract void emit_athrow();

  /**
   * Emit code to implement the checkcast bytecode
   * @param typeRef the LHS type
   * @param target the method to invoke to implement this checkcast
   */
  protected abstract void emit_checkcast(VM_Type typeRef, VM_Method target);

  /**
   * Emit code to implement the instanceof bytecode
   * @param typeRef the LHS type
   * @param target the method to invoke to implement this instanceof
   */
  protected abstract void emit_instanceof(VM_Type typeRef, VM_Method target);

  /**
   * Emit code to implement the monitorenter bytecode
   */
  protected abstract void emit_monitorenter();

  /**
   * Emit code to implement the monitorexit bytecode
   */
  protected abstract void emit_monitorexit();
}
