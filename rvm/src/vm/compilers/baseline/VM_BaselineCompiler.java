/*
 * (C) Copyright IBM Corp. 2001
 */
//$Id$

/**
 * Baseline compiler - platform independent code.
 * This is the super class of the platform dependent versions
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
  protected VM_Method method;

  /** 
   * The declaring class of the method being compiled
   */
  protected VM_Class klass;


  /**
   * The bytecodes of the method being compiled
   */
  protected byte[] bytecodes;

  /**
   * Mapping from bytecodes to machine code offsets
   */
  protected int[]        bytecodeMap;

  /**
   * index into bytecodes and bytecodeMap
   */
  protected int bi;      

  /**
   * bi at the start of a bytecode
   */
  protected int biStart; 

  /**
   * The compiledMethodId assigned to this compilation of method
   */
  protected int compiledMethodId;

  /** 
   * The height of the expression stack at the start of each bytecode.
   * Only saved for some architecutres, on others this field will be null.
   * See the VM_Compiler constructor.
   */
  protected int[] stackHeights;

  /**
   * Should we print the machine code we generate?
   */
  protected boolean shouldPrint = false;

  /**
   * machine code offset at which the lock is acquired in the prologue of a synchronized method.
   */
  protected int lockOffset;


  /**
   * Construct a VM_Compiler
   */
  protected VM_BaselineCompiler(VM_Method m, int cmid) {
    method = m;
    compiledMethodId = cmid;
    shouldPrint  = ((options.PRINT_MACHINECODE) &&
		    (!options.hasMETHOD_TO_PRINT() ||
		     options.fuzzyMatchMETHOD_TO_PRINT(method.toString())));

    klass                = method.getDeclaringClass();
    bytecodes            = method.getBytecodes();
    bytecodeMap          = new int [bytecodes.length];
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
    int cmid = VM_CompiledMethods.createCompiledMethodId();
    if (method.isNative()) {
      VM_MachineCode machineCode = VM_JNICompiler.generateGlueCodeForNative(cmid, method);
      VM_CompilerInfo info = new VM_JNICompilerInfo(method);
      return new VM_CompiledMethod(cmid, method, machineCode.getInstructions(), info);
    } else if (VM.runningAsJDPRemoteInterpreter) {
      return new VM_CompiledMethod(cmid, method, null, null);
    } else {
      return new VM_Compiler(method, cmid).compile();
    }
  }


  /**
   * Top level driver for baseline compilation of a method.
   */
  protected VM_CompiledMethod compile() {
    if (options.PRINT_METHOD) printMethodMessage();
    if (shouldPrint) printStartHeader(method);
    VM_ReferenceMaps refMaps     = new VM_ReferenceMaps(method, stackHeights);
    VM_MachineCode  machineCode  = genCode(compiledMethodId, method);
    if (shouldPrint) printEndHeader(method);

    INSTRUCTION[]   instructions = machineCode.getInstructions();
    int[]           bytecodeMap  = machineCode.getBytecodeMap();
    VM_CompilerInfo info;
    if (method.isSynchronized()) {
      info = new VM_BaselineCompilerInfo(method, refMaps, bytecodeMap, instructions.length, lockOffset);
    } else {
      info = new VM_BaselineCompilerInfo(method, refMaps, bytecodeMap, instructions.length);
    }
    return new VM_CompiledMethod(compiledMethodId, method, instructions, info);
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


  protected abstract VM_MachineCode genCode (int compiledMethodId, VM_Method method);



}
