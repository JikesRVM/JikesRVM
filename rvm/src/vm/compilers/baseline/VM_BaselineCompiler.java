/*
 * (C) Copyright IBM Corp. 2001
 */
//$Id$
package com.ibm.JikesRVM;

//-#if RVM_WITH_OSR
import com.ibm.JikesRVM.OSR.*;
//-#endif
import com.ibm.JikesRVM.classloader.*;

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
public abstract class VM_BaselineCompiler implements VM_BytecodeConstants, 
                                                     VM_SizeConstants
                                                     //-#if RVM_WITH_OSR
                                                     , OSR_Constants
                                                     //-#endif
{

  private static long gcMapCycles;
  private static long osrSetupCycles;
  private static long codeGenCycles;
  private static long encodingCycles;

  /** 
   * Options used during base compiler execution 
   */
  public static VM_BaselineOptions options;

  /**
   * has fullyBootedVM been called by VM.boot?
   */
  protected static boolean fullyBootedVM = false;

  /**
   * The method being compiled
   */
  protected final VM_NormalMethod method;

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
  protected final VM_BytecodeStream bcodes;

  /**
   * Mapping from bytecodes to machine code offsets
   */
  protected final int[] bytecodeMap;

  /**
   * bi at the start of a bytecode
   */
  protected int biStart; 

  /**
   * Next edge counter entry to allocate
   */
  protected int edgeCounterIdx;

  /**
   * The compiledMethod assigned to this compilation of method
   */
  protected VM_BaselineCompiledMethod compiledMethod;

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
  protected VM_BaselineCompiler(VM_BaselineCompiledMethod cm) {
    compiledMethod = cm;
    method = (VM_NormalMethod)cm.getMethod();
    shouldPrint  = (!VM.runningTool &&
                    (options.PRINT_MACHINECODE) &&
                    (!options.hasMETHOD_TO_PRINT() ||
                     options.fuzzyMatchMETHOD_TO_PRINT(method.toString())));

    if (!VM.runningTool && options.PRINT_METHOD) printMethodMessage();
    if (shouldPrint && VM.runningVM && !fullyBootedVM) {
      shouldPrint = false;
      if (options.PRINT_METHOD) {
        VM.sysWriteln("\ttoo early in VM.boot() to print machine code");
      }
    }

    klass = method.getDeclaringClass();
    //-#if RVM_WITH_OSR
    // new synthesized bytecodes for osr
    if (method.isForOsrSpecialization()) 
      bcodes = method.getOsrSynthesizedBytecodes();
    else
      //-#endif
    bcodes = method.getBytecodes();
    bytecodeMap = new int [bcodes.length()+1];
    asm = new VM_Assembler(bcodes.length(), shouldPrint, (VM_Compiler)this);
    isInterruptible = method.isInterruptible();
  }

  /**
   * Clear out crud from bootimage writing
   */
  static void initOptions() {
    options = new VM_BaselineOptions();
  }

  /**
   * Now that VM is fully booted, enable options 
   * such as PRINT_MACHINE_CODE that require a fully booted VM.
   */
  static void fullyBootedVM() {
    // If the user has requested machine code dumps, then force a test 
    // of method to print option so extra classes needed to process 
    // matching will be loaded and compiled upfront. Thus avoiding getting
    // stuck looping by just asking if we have a match in the middle of 
    // compilation. Pick an obsure string for the check.
    if (options.hasMETHOD_TO_PRINT() && options.fuzzyMatchMETHOD_TO_PRINT("???")) {
      VM.sysWrite("??? is not a sensible string to specify for method name");
    }
    //-#if !RVM_WITH_ADAPTIVE_SYSTEM
    if (options.PRELOAD_CLASS != null) {
      VM.sysWrite("Option preload_class should only be used when the optimizing compiler is the runtime");
      VM.sysWrite(" compiler or in an adaptive system\n");
      VM.sysExit(VM.exitStatusBogusCommandLineArg);
    }
    //-#endif
    fullyBootedVM = true;
  }

  /**
   * Process a command line argument
   * @param prefix
   * @param arg     Command line argument with prefix stripped off
   */
  public static void processCommandLineArg(String prefix, String arg) {
    if (options.processAsOption(prefix, arg)) {
      return;
    } else {
      VM.sysWrite("VM_BaselineCompiler: Unrecognized argument \""+ arg + "\"\n");
      VM.sysExit(VM.exitStatusBogusCommandLineArg);
    }
  }

  /**
   * Generate a report of time spent in various phases of the baseline compiler.
   * <p> NB: This method may be called in a context where classloading and/or 
   * GC cannot be allowed.
   * Therefore we must use primitive sysWrites for output and avoid string 
   * appends and other allocations.
   *
   * @param explain Should an explanation of the metrics be generated?
   */
  public static void generateBaselineCompilerSubsystemReport(boolean explain) {
    VM.sysWriteln("\n\t\tBaseline Compiler SubSystem");
    VM.sysWriteln("\tPhase\t\t\t    Time");
    VM.sysWriteln("\t\t\t\t(ms)    (%ofTotal)");

    double gcMapTime = VM_Time.cyclesToMillis(gcMapCycles);
    double osrSetupTime = VM_Time.cyclesToMillis(osrSetupCycles);
    double codeGenTime = VM_Time.cyclesToMillis(codeGenCycles);
    double encodingTime = VM_Time.cyclesToMillis(encodingCycles);
    double total = gcMapTime + osrSetupTime + codeGenTime + encodingTime;
    
    VM.sysWrite("\tCompute GC Maps\t\t", gcMapTime);
    VM.sysWriteln("\t",100*gcMapTime/total);

    if (osrSetupTime > 0) {
      VM.sysWrite("\tOSR setup \t\t", osrSetupTime);
      VM.sysWriteln("\t",100*osrSetupTime/total);
    }

    VM.sysWrite("\tCode generation\t\t", codeGenTime);
    VM.sysWriteln("\t",100*codeGenTime/total);

    VM.sysWrite("\tEncode GC/MC maps\t", encodingTime);
    VM.sysWriteln("\t",100*encodingTime/total);

    VM.sysWriteln("\tTOTAL\t\t\t", total);
  }

  /**
   * Compile the given method with the baseline compiler.
   * 
   * @param method the VM_NormalMethod to compile.
   * @return the generated VM_CompiledMethod for said VM_NormalMethod.
   */
  public static VM_CompiledMethod compile (VM_NormalMethod method) {
    VM_BaselineCompiledMethod cm = (VM_BaselineCompiledMethod)VM_CompiledMethods.createCompiledMethod(method, VM_CompiledMethod.BASELINE);
    new VM_Compiler(cm).compile();
    return cm;
  }


  /**
   * Top level driver for baseline compilation of a method.
   */
  protected void compile() {
    if (shouldPrint) printStartHeader(method);

    // Phase 1: GC map computation
    long start = 0;
    if (VM.MeasureCompilation) start = VM_Thread.getCurrentThread().accumulateCycles();
    VM_ReferenceMaps refMaps = new VM_ReferenceMaps(compiledMethod, stackHeights);
    if (VM.MeasureCompilation) {
      long end = VM_Thread.getCurrentThread().accumulateCycles();
      gcMapCycles += end - start;
    }

    //-#if RVM_WITH_OSR
    /* reference map and stackheights were computed using original bytecodes
     * and possibly new operand words
     * recompute the stack height, but keep the operand words of the code 
     * generation consistant with reference map 
     */
    // Phase 2: OSR setup
    if (VM.MeasureCompilation) start = VM_Thread.getCurrentThread().accumulateCycles();
    boolean edge_counters = options.EDGE_COUNTERS;
    if (method.isForOsrSpecialization()) {
      options.EDGE_COUNTERS = false;
      // we already allocated enough space for stackHeights, shift it back first
      System.arraycopy(stackHeights, 0, stackHeights, 
                       method.getOsrPrologueLength(), 
                       method.getBytecodeLength());   // NB: getBytecodeLength returns back the length of original bytecodes
      
      // compute stack height for prologue
      new OSR_BytecodeTraverser().prologueStackHeights(method, method.getOsrPrologue(), stackHeights);
    } 
    if (VM.MeasureCompilation) {
      long end = VM_Thread.getCurrentThread().accumulateCycles();
      osrSetupCycles += end - start;
    }
    //-#endif
    
    // Phase 3: Code gen
    if (VM.MeasureCompilation) start = VM_Thread.getCurrentThread().accumulateCycles();
    VM_MachineCode  machineCode  = genCode();
    VM_CodeArray    instructions = machineCode.getInstructions();
    int[]           bcMap        = machineCode.getBytecodeMap();
    if (VM.MeasureCompilation) {
      long end = VM_Thread.getCurrentThread().accumulateCycles();
      codeGenCycles += end - start;
    }

    //-#if RVM_WITH_OSR
    /* adjust machine code map, and restore original bytecode
     * for building reference map later.
     */
    // Phase 4: OSR part 2
    if (VM.MeasureCompilation) start = VM_Thread.getCurrentThread().accumulateCycles();
    if (method.isForOsrSpecialization()) {
      int[] newmap = new int[bcMap.length - method.getOsrPrologueLength()];
      System.arraycopy(bcMap,
                       method.getOsrPrologueLength(),
                       newmap,
                       0,
                       newmap.length);
      machineCode.setBytecodeMap(newmap);
      bcMap = newmap;
      // switch back to original state
      method.finalizeOsrSpecialization();
      // restore options
      options.EDGE_COUNTERS = edge_counters;     
    }
    if (VM.MeasureCompilation) {
      long end = VM_Thread.getCurrentThread().accumulateCycles();
      osrSetupCycles += end - start;
    }
    //-#endif
        
    // Phase 5: Encode machine code maps
    if (VM.MeasureCompilation) start = VM_Thread.getCurrentThread().accumulateCycles();
    if (method.isSynchronized()) {
      compiledMethod.setLockAcquisitionOffset(lockOffset);
    }
    compiledMethod.encodeMappingInfo(refMaps, bcMap, instructions.length());
    compiledMethod.compileComplete(instructions);
    if (edgeCounterIdx > 0) {
      VM_EdgeCounts.allocateCounters(method, edgeCounterIdx);
    }
    if (shouldPrint) {
      compiledMethod.printExceptionTable();
      printEndHeader(method);
    }
    if (VM.MeasureCompilation) {
      long end = VM_Thread.getCurrentThread().accumulateCycles();
      encodingCycles += end - start;
    }
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

  protected final int getEdgeCounterOffset() {
    return method.getId() << 2;
  }


  /**
   * Main code generation loop.
   */
  protected final VM_MachineCode genCode () {
    // determine if we are going to insert edge counters for this method
    if (options.EDGE_COUNTERS && 
        !method.getDeclaringClass().isBridgeFromNative() &&
        (method.hasCondBranch() || method.hasSwitch())) {
      compiledMethod.setHasCounterArray(); // yes, we will inject counters for this method.
    }

    emit_prologue();
    while (bcodes.hasMoreBytecodes()) {
      biStart = bcodes.index();
      bytecodeMap[biStart] = asm.getMachineCodeIndex();
      asm.resolveForwardReferences(biStart);
      //-#if RVM_WITH_OSR
      asm.patchLoadAddrConst(biStart);
      //-#endif
      starting_bytecode();
      int code = bcodes.nextInstruction();
      switch (code) {
      case JBC_nop: {
        if (shouldPrint) asm.noteBytecode(biStart, "nop");
        break;
      }

      case JBC_aconst_null: {
        if (shouldPrint) asm.noteBytecode(biStart, "aconst_null");
        emit_aconst_null();
        break;
      }

      case JBC_iconst_m1: {
        if (shouldPrint) asm.noteBytecode(biStart, "iconst_m1");
        emit_iconst(-1);
        break;
      }

      case JBC_iconst_0: {
        if (shouldPrint) asm.noteBytecode(biStart, "iconst_0");
        emit_iconst(0);
        break;
      }

      case JBC_iconst_1: {
        if (shouldPrint) asm.noteBytecode(biStart, "iconst_1");
        emit_iconst(1);
        break;
      }

      case JBC_iconst_2: {
        if (shouldPrint) asm.noteBytecode(biStart, "iconst_2");
        emit_iconst(2);
        break;
      }

      case JBC_iconst_3: {
        if (shouldPrint) asm.noteBytecode(biStart, "iconst_3");
        emit_iconst(3);
        break;
      }

      case JBC_iconst_4: {
        if (shouldPrint) asm.noteBytecode(biStart, "iconst_4");
        emit_iconst(4);
        break;
      }

      case JBC_iconst_5: {
        if (shouldPrint) asm.noteBytecode(biStart, "iconst_5");
        emit_iconst(5);
        break;
      }

      case JBC_lconst_0: {
        if (shouldPrint) asm.noteBytecode(biStart, "lconst_0");  // floating-point 0 is long 0
        emit_lconst(0);
        break;
      }

      case JBC_lconst_1: {
        if (shouldPrint) asm.noteBytecode(biStart, "lconst_1");
        emit_lconst(1);
        break;
      }

      case JBC_fconst_0: {
        if (shouldPrint) asm.noteBytecode(biStart, "fconst_0");
        emit_fconst_0();
        break;
      }

      case JBC_fconst_1: {
        if (shouldPrint) asm.noteBytecode(biStart, "fconst_1");
        emit_fconst_1();
        break;
      }

      case JBC_fconst_2: {
        if (shouldPrint) asm.noteBytecode(biStart, "fconst_2");
        emit_fconst_2();
        break;
      }

      case JBC_dconst_0: {
        if (shouldPrint) asm.noteBytecode(biStart, "dconst_0");
        emit_dconst_0();
        break;
      }

      case JBC_dconst_1: {
        if (shouldPrint) asm.noteBytecode(biStart, "dconst_1");
        emit_dconst_1();
        break;
      }

      case JBC_bipush: {
        int val = bcodes.getByteValue();
        if (shouldPrint) asm.noteBytecode(biStart, "bipush", val);
        emit_iconst(val);
        break;
      }

      case JBC_sipush: {
        int val = bcodes.getShortValue();
        if (shouldPrint) asm.noteBytecode(biStart, "sipush", val);
        emit_iconst(val);
        break;
      }

      case JBC_ldc: {
        int index = bcodes.getConstantIndex();
        if (shouldPrint) asm.noteBytecode(biStart, "ldc", index);
        int offset = klass.getLiteralOffset(index);
        emit_ldc(offset);
        break;
      }

      case JBC_ldc_w: {
        int index = bcodes.getWideConstantIndex();
        if (shouldPrint) asm.noteBytecode(biStart, "ldc_w", index);
        int offset = klass.getLiteralOffset(index);
        emit_ldc(offset);
        break;
      }

      case JBC_ldc2_w: {
        int index = bcodes.getWideConstantIndex();
        if (shouldPrint) asm.noteBytecode(biStart, "ldc2_w", index);
        int offset = klass.getLiteralOffset(index);
        emit_ldc2(offset);
        break;
      }

      case JBC_iload: {
        int index = bcodes.getLocalNumber();
        if (shouldPrint) asm.noteBytecode(biStart, "iload" , index);
        emit_iload(index);
        break;
      }

      case JBC_lload: {
        int index = bcodes.getLocalNumber();
        if (shouldPrint) asm.noteBytecode(biStart, "lload", index);
        emit_lload(index);
        break;
      }

      case JBC_fload: {
        int index = bcodes.getLocalNumber();
        if (shouldPrint) asm.noteBytecode(biStart, "fload", index);
        emit_fload(index);
        break;
      }

      case JBC_dload: {
        int index = bcodes.getLocalNumber();
        if (shouldPrint) asm.noteBytecode(biStart, "dload", index);
        emit_dload(index);
        break;
      }

      case JBC_aload: {
        int index = bcodes.getLocalNumber();
        if (shouldPrint) asm.noteBytecode(biStart, "aload", index);
        emit_aload(index);
        break;
      }

      case JBC_iload_0: {
        if (shouldPrint) asm.noteBytecode(biStart, "iload_0");
        emit_iload(0);
        break;
      }

      case JBC_iload_1: {
        if (shouldPrint) asm.noteBytecode(biStart, "iload_1");
        emit_iload(1);
        break;
      }

      case JBC_iload_2: {
        if (shouldPrint) asm.noteBytecode(biStart, "iload_2");
        emit_iload(2);
        break;
      }

      case JBC_iload_3: {
        if (shouldPrint) asm.noteBytecode(biStart, "iload_3");
        emit_iload(3);
        break;
      }

      case JBC_lload_0: {
        if (shouldPrint) asm.noteBytecode(biStart, "lload_0");
        emit_lload(0);
        break;
      }

      case JBC_lload_1: {
        if (shouldPrint) asm.noteBytecode(biStart, "lload_1");
        emit_lload(1);
        break;
      }

      case JBC_lload_2: {
        if (shouldPrint) asm.noteBytecode(biStart, "lload_2");
        emit_lload(2);
        break;
      }

      case JBC_lload_3: {
        if (shouldPrint) asm.noteBytecode(biStart, "lload_3");
        emit_lload(3);
        break;
      }

      case JBC_fload_0: {
        if (shouldPrint) asm.noteBytecode(biStart, "fload_0");
        emit_fload(0);
        break;
      }

      case JBC_fload_1: {
        if (shouldPrint) asm.noteBytecode(biStart, "fload_1");
        emit_fload(1);
        break;
      }

      case JBC_fload_2: {
        if (shouldPrint) asm.noteBytecode(biStart, "fload_2");
        emit_fload(2);
        break;
      }

      case JBC_fload_3: {
        if (shouldPrint) asm.noteBytecode(biStart, "fload_3");
        emit_fload(3);
        break;
      }

      case JBC_dload_0: {
        if (shouldPrint) asm.noteBytecode(biStart, "dload_0");
        emit_dload(0);
        break;
      }

      case JBC_dload_1: {
        if (shouldPrint) asm.noteBytecode(biStart, "dload_1");
        emit_dload(1);
        break;
      }

      case JBC_dload_2: {
        if (shouldPrint) asm.noteBytecode(biStart, "dload_2");
        emit_dload(2);
        break;
      }

      case JBC_dload_3: {
        if (shouldPrint) asm.noteBytecode(biStart, "dload_3");
        emit_dload(3);
        break;
      }

      case JBC_aload_0: {
        if (shouldPrint) asm.noteBytecode(biStart, "aload_0");
        emit_aload(0);
        break;
      }

      case JBC_aload_1: {
        if (shouldPrint) asm.noteBytecode(biStart, "aload_1");
        emit_aload(1);
        break;
      }           

      case JBC_aload_2: {
        if (shouldPrint) asm.noteBytecode(biStart, "aload_2");
        emit_aload(2);
        break;
      }

      case JBC_aload_3: {
        if (shouldPrint) asm.noteBytecode(biStart, "aload_3");
        emit_aload(3);
        break;
      } 

      case JBC_iaload: {
        if (shouldPrint) asm.noteBytecode(biStart, "iaload");
        emit_iaload();
        break;
      }

      case JBC_laload: {
        if (shouldPrint) asm.noteBytecode(biStart, "laload");
        emit_laload();
        break;
      }

      case JBC_faload: {
        if (shouldPrint) asm.noteBytecode(biStart, "faload");
        emit_faload();
        break;
      }

      case JBC_daload: {
        if (shouldPrint) asm.noteBytecode(biStart, "daload");
        emit_daload();
        break;
      }

      case JBC_aaload: {
        if (shouldPrint) asm.noteBytecode(biStart, "aaload");
        emit_aaload();
        break;
      }

      case JBC_baload: {
        if (shouldPrint) asm.noteBytecode(biStart, "baload");
        emit_baload();
        break;
      }

      case JBC_caload: {
        if (shouldPrint) asm.noteBytecode(biStart, "caload");
        emit_caload();
        break;
      }

      case JBC_saload: {
        if (shouldPrint) asm.noteBytecode(biStart, "saload");
        emit_saload();
        break;
      }

      case JBC_istore: {
        int index = bcodes.getLocalNumber();
        if (shouldPrint) asm.noteBytecode(biStart, "istore", index);
        emit_istore(index);
        break;
      }

      case JBC_lstore: {
        int index = bcodes.getLocalNumber();
        if (shouldPrint) asm.noteBytecode(biStart, "lstore", index);
        emit_lstore(index);
        break;
      }

      case JBC_fstore: {
        int index = bcodes.getLocalNumber();
        if (shouldPrint) asm.noteBytecode(biStart, "fstore", index);
        emit_fstore(index);
        break;
      }

      case JBC_dstore: {
        int index = bcodes.getLocalNumber();
        if (shouldPrint) asm.noteBytecode(biStart, "dstore", index);
        emit_dstore(index);
        break;
      }

      case JBC_astore: {
        int index = bcodes.getLocalNumber();
        if (shouldPrint) asm.noteBytecode(biStart, "astore", index);
        emit_astore(index);
        break;
      }

      case JBC_istore_0: {
        if (shouldPrint) asm.noteBytecode(biStart, "istore_0");
        emit_istore(0);
        break;
      }

      case JBC_istore_1: {
        if (shouldPrint) asm.noteBytecode(biStart, "istore_1");
        emit_istore(1);
        break;
      }

      case JBC_istore_2: {
        if (shouldPrint) asm.noteBytecode(biStart, "istore_2");
        emit_istore(2);
        break;
      }

      case JBC_istore_3: {
        if (shouldPrint) asm.noteBytecode(biStart, "istore_3");
        emit_istore(3);
        break;
      }

      case JBC_lstore_0: {
        if (shouldPrint) asm.noteBytecode(biStart, "lstore_0");
        emit_lstore(0);
        break;
      }

      case JBC_lstore_1: {
        if (shouldPrint) asm.noteBytecode(biStart, "lstore_1");
        emit_lstore(1);
        break;
      }

      case JBC_lstore_2: {
        if (shouldPrint) asm.noteBytecode(biStart, "lstore_2");
        emit_lstore(2);
        break;
      } 

      case JBC_lstore_3: {
        if (shouldPrint) asm.noteBytecode(biStart, "lstore_3");
        emit_lstore(3);
        break;
      }

      case JBC_fstore_0: {
        if (shouldPrint) asm.noteBytecode(biStart, "fstore_0");
        emit_fstore(0);
        break;
      }

      case JBC_fstore_1: {
        if (shouldPrint) asm.noteBytecode(biStart, "fstore_1");
        emit_fstore(1);
        break;
      }

      case JBC_fstore_2: {
        if (shouldPrint) asm.noteBytecode(biStart, "fstore_2");
        emit_fstore(2);
        break;
      }

      case JBC_fstore_3: {
        if (shouldPrint) asm.noteBytecode(biStart, "fstore_3");
        emit_fstore(3);
        break;
      }

      case JBC_dstore_0: {
        if (shouldPrint) asm.noteBytecode(biStart, "dstore_0");
        emit_dstore(0);
        break;
      }

      case JBC_dstore_1: {
        if (shouldPrint) asm.noteBytecode(biStart, "dstore_1");
        emit_dstore(1);
        break;
      }

      case JBC_dstore_2: {
        if (shouldPrint) asm.noteBytecode(biStart, "dstore_2");
        emit_dstore(2);
        break;
      }

      case JBC_dstore_3: {
        if (shouldPrint) asm.noteBytecode(biStart, "dstore_3");
        emit_dstore(3);
        break;
      }

      case JBC_astore_0: {
        if (shouldPrint) asm.noteBytecode(biStart, "astore_0");
        emit_astore(0);
        break;
      }

      case JBC_astore_1: {
        if (shouldPrint) asm.noteBytecode(biStart, "astore_1");
        emit_astore(1);
        break;
      }

      case JBC_astore_2: {
        if (shouldPrint) asm.noteBytecode(biStart, "astore_2");
        emit_astore(2);
        break;
      }

      case JBC_astore_3: {
        if (shouldPrint) asm.noteBytecode(biStart, "astore_3");
        emit_astore(3);
        break;
      }

      case JBC_iastore: {
        if (shouldPrint) asm.noteBytecode(biStart, "iastore");
        emit_iastore();
        break;
      }

      case JBC_lastore: { 
        if (shouldPrint) asm.noteBytecode(biStart, "lastore"); 
        emit_lastore();
        break;
      }

      case JBC_fastore: {
        if (shouldPrint) asm.noteBytecode(biStart, "fastore");
        emit_fastore();
        break;
      }

      case JBC_dastore: {
        if (shouldPrint) asm.noteBytecode(biStart, "dastore");
        emit_dastore();
        break;
      }

      case JBC_aastore: {
        if (shouldPrint) asm.noteBytecode(biStart, "aastore");
        if (VM.VerifyUnint && !isInterruptible) forbiddenBytecode("aastore");
        emit_aastore();
        break;
      }

      case JBC_bastore: {
        if (shouldPrint) asm.noteBytecode(biStart, "bastore");
        emit_bastore();
        break;
      }

      case JBC_castore: {
        if (shouldPrint) asm.noteBytecode(biStart, "castore");
        emit_castore();
        break;
      }

      case JBC_sastore: {
        if (shouldPrint) asm.noteBytecode(biStart, "sastore");
        emit_sastore();
        break;
      }

      case JBC_pop: {
        if (shouldPrint) asm.noteBytecode(biStart, "pop");
        emit_pop();
        break;
      }

      case JBC_pop2: {
        if (shouldPrint) asm.noteBytecode(biStart, "pop2");
        emit_pop2();
        break;
      }

      case JBC_dup: {
        if (shouldPrint) asm.noteBytecode(biStart, "dup");
        emit_dup();
        break;
      } 

      case JBC_dup_x1: {
        if (shouldPrint) asm.noteBytecode(biStart, "dup_x1");
        emit_dup_x1();
        break;
      }

      case JBC_dup_x2: {
        if (shouldPrint) asm.noteBytecode(biStart, "dup_x2");
        emit_dup_x2();
        break;
      }

      case JBC_dup2: {
        if (shouldPrint) asm.noteBytecode(biStart, "dup2");
        emit_dup2();
        break;
      }

      case JBC_dup2_x1: {
        if (shouldPrint) asm.noteBytecode(biStart, "dup2_x1");
        emit_dup2_x1();
        break;
      }

      case JBC_dup2_x2: {
        if (shouldPrint) asm.noteBytecode(biStart, "dup2_x2");
        emit_dup2_x2();
        break;
      }

      case JBC_swap: {
        if (shouldPrint) asm.noteBytecode(biStart, "swap");
        emit_swap();
        break;
      }

      case JBC_iadd: {
        if (shouldPrint) asm.noteBytecode(biStart, "iadd");
        emit_iadd();
        break;
      }

      case JBC_ladd: {
        if (shouldPrint) asm.noteBytecode(biStart, "ladd");
        emit_ladd();
        break;
      }

      case JBC_fadd: {
        if (shouldPrint) asm.noteBytecode(biStart, "fadd");
        emit_fadd();
        break;
      }

      case JBC_dadd: {
        if (shouldPrint) asm.noteBytecode(biStart, "dadd");
        emit_dadd();
        break;
      }

      case JBC_isub: {
        if (shouldPrint) asm.noteBytecode(biStart, "isub");
        emit_isub();
        break;
      }

      case JBC_lsub: {
        if (shouldPrint) asm.noteBytecode(biStart, "lsub");
        emit_lsub();
        break;
      }

      case JBC_fsub: {
        if (shouldPrint) asm.noteBytecode(biStart, "fsub");
        emit_fsub();
        break;
      }

      case JBC_dsub: {
        if (shouldPrint) asm.noteBytecode(biStart, "dsub");
        emit_dsub();
        break;
      }

      case JBC_imul: {
        if (shouldPrint) asm.noteBytecode(biStart, "imul");
        emit_imul();
        break;
      }

      case JBC_lmul: {
        if (shouldPrint) asm.noteBytecode(biStart, "lmul");
        emit_lmul();
        break;
      }

      case JBC_fmul: {
        if (shouldPrint) asm.noteBytecode(biStart, "fmul");
        emit_fmul();
        break;
      }

      case JBC_dmul: {
        if (shouldPrint) asm.noteBytecode(biStart, "dmul");
        emit_dmul();
        break;
      }

      case JBC_idiv: {
        if (shouldPrint) asm.noteBytecode(biStart, "idiv");
        emit_idiv();
        break;
      }

      case JBC_ldiv: {
        if (shouldPrint) asm.noteBytecode(biStart, "ldiv");
        emit_ldiv();
        break;
      }

      case JBC_fdiv: {
        if (shouldPrint) asm.noteBytecode(biStart, "fdiv");
        emit_fdiv();
        break;
      }

      case JBC_ddiv: {
        if (shouldPrint) asm.noteBytecode(biStart, "ddiv");
        emit_ddiv();
        break;
      }

      case JBC_irem: {
        if (shouldPrint) asm.noteBytecode(biStart, "irem");
        emit_irem();
        break;
      }

      case JBC_lrem: {
        if (shouldPrint) asm.noteBytecode(biStart, "lrem");
        emit_lrem();
        break;
      }

      case JBC_frem: {
        if (shouldPrint) asm.noteBytecode(biStart, "frem"); 
        emit_frem();
        break;
      }

      case JBC_drem: {
        if (shouldPrint) asm.noteBytecode(biStart, "drem");
        emit_drem();
        break;
      }

      case JBC_ineg: {
        if (shouldPrint) asm.noteBytecode(biStart, "ineg");
        emit_ineg();
        break;
      }

      case JBC_lneg: {
        if (shouldPrint) asm.noteBytecode(biStart, "lneg");
        emit_lneg();
        break;
      }

      case JBC_fneg: {
        if (shouldPrint) asm.noteBytecode(biStart, "fneg");
        emit_fneg();
        break;
      }

      case JBC_dneg: {
        if (shouldPrint) asm.noteBytecode(biStart, "dneg");
        emit_dneg();
        break;
      }

      case JBC_ishl: {
        if (shouldPrint) asm.noteBytecode(biStart, "ishl");
        emit_ishl();
        break;
      }

      case JBC_lshl: {
        if (shouldPrint) asm.noteBytecode(biStart, "lshl");    // l >> n
        emit_lshl();
        break;
      }

      case JBC_ishr: {
        if (shouldPrint) asm.noteBytecode(biStart, "ishr");
        emit_ishr();
        break;
      }

      case JBC_lshr: {
        if (shouldPrint) asm.noteBytecode(biStart, "lshr");
        emit_lshr();
        break;
      }

      case JBC_iushr: {
        if (shouldPrint) asm.noteBytecode(biStart, "iushr");
        emit_iushr();
        break;
      }

      case JBC_lushr: {
        if (shouldPrint) asm.noteBytecode(biStart, "lushr");
        emit_lushr();
        break;
      }

      case JBC_iand: {
        if (shouldPrint) asm.noteBytecode(biStart, "iand");
        emit_iand();
        break;
      }

      case JBC_land: {
        if (shouldPrint) asm.noteBytecode(biStart, "land");
        emit_land();
        break;
      }

      case JBC_ior: {
        if (shouldPrint) asm.noteBytecode(biStart, "ior");
        emit_ior();
        break;
      }

      case JBC_lor: {
        if (shouldPrint) asm.noteBytecode(biStart, "lor");
        emit_lor();
        break;
      }

      case JBC_ixor: {
        if (shouldPrint) asm.noteBytecode(biStart, "ixor");
        emit_ixor();
        break;
      }

      case JBC_lxor: {
        if (shouldPrint) asm.noteBytecode(biStart, "lxor");
        emit_lxor();
        break;
      }

      case JBC_iinc: {
        int index = bcodes.getLocalNumber();
        int val = bcodes.getIncrement();
        if (shouldPrint) asm.noteBytecode(biStart, "iinc",index, val);
        emit_iinc(index, val);
        break;
      }

      case JBC_i2l: {
        if (shouldPrint) asm.noteBytecode(biStart, "i2l");
        emit_i2l();
        break;
      }

      case JBC_i2f: {
        if (shouldPrint) asm.noteBytecode(biStart, "i2f");
        emit_i2f();
        break;
      }

      case JBC_i2d: {
        if (shouldPrint) asm.noteBytecode(biStart, "i2d");
        emit_i2d();
        break;
      }

      case JBC_l2i: {
        if (shouldPrint) asm.noteBytecode(biStart, "l2i");
        emit_l2i();
        break;
      }

      case JBC_l2f: {
        if (shouldPrint) asm.noteBytecode(biStart, "l2f");
        emit_l2f();
        break;
      }

      case JBC_l2d: {
        if (shouldPrint) asm.noteBytecode(biStart, "l2d");
        emit_l2d();
        break;
      }

      case JBC_f2i: {
        if (shouldPrint) asm.noteBytecode(biStart, "f2i");
        emit_f2i();
        break;
      }

      case JBC_f2l: {
        if (shouldPrint) asm.noteBytecode(biStart, "f2l");
        emit_f2l();
        break;
      }

      case JBC_f2d: {
        if (shouldPrint) asm.noteBytecode(biStart, "f2d");
        emit_f2d();
        break;
      }

      case JBC_d2i: {
        if (shouldPrint) asm.noteBytecode(biStart, "d2i");
        emit_d2i();
        break;
      }

      case JBC_d2l: {
        if (shouldPrint) asm.noteBytecode(biStart, "d2l");
        emit_d2l();
        break;
      }

      case JBC_d2f: {
        if (shouldPrint) asm.noteBytecode(biStart, "d2f");
        emit_d2f();
        break;
      }

      case JBC_int2byte: {
        if (shouldPrint) asm.noteBytecode(biStart, "i2b");
        emit_i2b();
        break;
      }

      case JBC_int2char: {
        if (shouldPrint) asm.noteBytecode(biStart, "i2c");
        emit_i2c();
        break;
      }

      case JBC_int2short: {
        if (shouldPrint) asm.noteBytecode(biStart, "i2s");
        emit_i2s();
        break;
      }

      case JBC_lcmp: {
        if (shouldPrint) asm.noteBytecode(biStart, "lcmp");  // a ? b
        emit_lcmp();
        break;
      }

      case JBC_fcmpl: {
        if (shouldPrint) asm.noteBytecode(biStart, "fcmpl");
        emit_fcmpl();
        break;
      }

      case JBC_fcmpg: {
        if (shouldPrint) asm.noteBytecode(biStart, "fcmpg");
        emit_fcmpg();
        break;
      }

      case JBC_dcmpl: {
        if (shouldPrint) asm.noteBytecode(biStart, "dcmpl");
        emit_dcmpl();
        break;
      }

      case JBC_dcmpg: {
        if (shouldPrint) asm.noteBytecode(biStart, "dcmpg");
        emit_dcmpg();
        break;
      }

      case JBC_ifeq: {
        int offset = bcodes.getBranchOffset();
        int bTarget = biStart + offset;
        if (shouldPrint) asm.noteBranchBytecode(biStart, "ifeq", offset, bTarget);
        if (offset < 0) emit_threadSwitchTest(VM_Thread.BACKEDGE);
        emit_ifeq(bTarget);
        break;
      }

      case JBC_ifne: {
        int offset = bcodes.getBranchOffset();
        int bTarget = biStart + offset;
        if (shouldPrint) asm.noteBranchBytecode(biStart, "ifne", offset, bTarget);
        if (offset < 0) emit_threadSwitchTest(VM_Thread.BACKEDGE);
        emit_ifne(bTarget);
        break;
      }

      case JBC_iflt: {
        int offset = bcodes.getBranchOffset();
        int bTarget = biStart + offset;
        if (shouldPrint) asm.noteBranchBytecode(biStart, "iflt", offset, bTarget);
        if (offset < 0) emit_threadSwitchTest(VM_Thread.BACKEDGE);
        emit_iflt(bTarget);
        break;
      }

      case JBC_ifge: {
        int offset = bcodes.getBranchOffset();
        int bTarget = biStart + offset;
        if (shouldPrint) asm.noteBranchBytecode(biStart, "ifge", offset, bTarget);
        if (offset < 0) emit_threadSwitchTest(VM_Thread.BACKEDGE);
        emit_ifge(bTarget);
        break;
      }

      case JBC_ifgt: {
        int offset = bcodes.getBranchOffset();
        int bTarget = biStart + offset;
        if (shouldPrint) asm.noteBranchBytecode(biStart, "ifgt", offset, bTarget);
        if (offset < 0) emit_threadSwitchTest(VM_Thread.BACKEDGE);
        emit_ifgt(bTarget);
        break;
      }

      case JBC_ifle: {
        int offset = bcodes.getBranchOffset();
        int bTarget = biStart + offset;
        if (shouldPrint) asm.noteBranchBytecode(biStart, "ifle", offset, bTarget);
        if (offset < 0) emit_threadSwitchTest(VM_Thread.BACKEDGE);
        emit_ifle(bTarget);
        break;
      }

      case JBC_if_icmpeq: {
        int offset = bcodes.getBranchOffset();
        int bTarget = biStart + offset;
        if (shouldPrint) asm.noteBranchBytecode(biStart, "if_icmpeq", offset, bTarget);
        if (offset < 0) emit_threadSwitchTest(VM_Thread.BACKEDGE);
        emit_if_icmpeq(bTarget);
        break;
      }

      case JBC_if_icmpne: {
        int offset = bcodes.getBranchOffset();
        int bTarget = biStart + offset;
        if (shouldPrint) asm.noteBranchBytecode(biStart, "if_icmpne", offset, bTarget);
        if (offset < 0) emit_threadSwitchTest(VM_Thread.BACKEDGE);
        emit_if_icmpne(bTarget);
        break;
      }

      case JBC_if_icmplt: {
        int offset = bcodes.getBranchOffset();
        int bTarget = biStart + offset;
        if (shouldPrint) asm.noteBranchBytecode(biStart, "if_icmplt", offset, bTarget);
        if (offset < 0) emit_threadSwitchTest(VM_Thread.BACKEDGE);
        emit_if_icmplt(bTarget);
        break;
      }

      case JBC_if_icmpge: {
        int offset = bcodes.getBranchOffset();
        int bTarget = biStart + offset;
        if (shouldPrint) asm.noteBranchBytecode(biStart, "if_icmpge", offset, bTarget);
        if (offset < 0) emit_threadSwitchTest(VM_Thread.BACKEDGE);
        emit_if_icmpge(bTarget);
        break;
      }

      case JBC_if_icmpgt: {
        int offset = bcodes.getBranchOffset();
        int bTarget = biStart + offset;
        if (shouldPrint) asm.noteBranchBytecode(biStart, "if_icmpgt", offset, bTarget);
        if (offset < 0) emit_threadSwitchTest(VM_Thread.BACKEDGE);
        emit_if_icmpgt(bTarget);
        break;
      }

      case JBC_if_icmple: {
        int offset = bcodes.getBranchOffset();
        int bTarget = biStart + offset;
        if (shouldPrint) asm.noteBranchBytecode(biStart, "if_icmple", offset, bTarget);
        if (offset < 0) emit_threadSwitchTest(VM_Thread.BACKEDGE);
        emit_if_icmple(bTarget);
        break;
      }

      case JBC_if_acmpeq: {
        int offset = bcodes.getBranchOffset();
        int bTarget = biStart + offset;
        if (shouldPrint) asm.noteBranchBytecode(biStart, "if_acmpeq", offset, bTarget);
        if (offset < 0) emit_threadSwitchTest(VM_Thread.BACKEDGE);
        emit_if_acmpeq(bTarget);
        break;
      }

      case JBC_if_acmpne: {
        int offset = bcodes.getBranchOffset();
        int bTarget = biStart + offset;
        if (shouldPrint) asm.noteBranchBytecode(biStart, "if_acmpne", offset, bTarget);
        if (offset < 0) emit_threadSwitchTest(VM_Thread.BACKEDGE);
        emit_if_acmpne(bTarget);
        break;
      }

      case JBC_goto: {
        int offset = bcodes.getBranchOffset();
        int bTarget = biStart + offset; // bi has been bumped by 3 already
        if (shouldPrint) asm.noteBranchBytecode(biStart, "goto", offset, bTarget);
        if (offset < 0) emit_threadSwitchTest(VM_Thread.BACKEDGE);
        emit_goto(bTarget);
        break;
      }

      case JBC_jsr: {
        int offset = bcodes.getBranchOffset();
        int bTarget = biStart + offset;
        if (shouldPrint) asm.noteBranchBytecode(biStart, "jsr", offset, bTarget);
        emit_jsr(bTarget);
        break;
      }

      case JBC_ret: {
        int index = bcodes.getLocalNumber();
        if (shouldPrint) asm.noteBytecode(biStart, "ret ", index);
        emit_ret(index);
        break;
      }

      case JBC_tableswitch: {
        bcodes.alignSwitch();
        int defaultval = bcodes.getDefaultSwitchOffset();
        int low = bcodes.getLowSwitchValue();
        int high = bcodes.getHighSwitchValue();
        if (shouldPrint) asm.noteTableswitchBytecode(biStart, low, high, defaultval);
        emit_tableswitch(defaultval, low, high);
        break;
      }

      case JBC_lookupswitch: {
        bcodes.alignSwitch();
        int defaultval = bcodes.getDefaultSwitchOffset();
        int npairs = bcodes.getSwitchLength();
        if (shouldPrint) asm.noteLookupswitchBytecode(biStart, npairs, defaultval);
        emit_lookupswitch(defaultval, npairs);
        break;
      }

      case JBC_ireturn: {
        if (shouldPrint) asm.noteBytecode(biStart, "ireturn");
        if (VM.UseEpilogueYieldPoints) emit_threadSwitchTest(VM_Thread.EPILOGUE);
        emit_ireturn();
        break;
      }

      case JBC_lreturn: {
        if (shouldPrint) asm.noteBytecode(biStart, "lreturn");
        if (VM.UseEpilogueYieldPoints) emit_threadSwitchTest(VM_Thread.EPILOGUE);
        emit_lreturn();
        break;
      }

      case JBC_freturn: {
        if (shouldPrint) asm.noteBytecode(biStart, "freturn");
        if (VM.UseEpilogueYieldPoints) emit_threadSwitchTest(VM_Thread.EPILOGUE);
        emit_freturn();
        break;
      }

      case JBC_dreturn: {
        if (shouldPrint) asm.noteBytecode(biStart, "dreturn");
        if (VM.UseEpilogueYieldPoints) emit_threadSwitchTest(VM_Thread.EPILOGUE);
        emit_dreturn();
        break;
      }

      case JBC_areturn: {
        if (shouldPrint) asm.noteBytecode(biStart, "areturn");
        if (VM.UseEpilogueYieldPoints) emit_threadSwitchTest(VM_Thread.EPILOGUE);
        emit_areturn();
        break;
      }

      case JBC_return: {
        if (shouldPrint) asm.noteBytecode(biStart, "return");
        if (VM.UseEpilogueYieldPoints) emit_threadSwitchTest(VM_Thread.EPILOGUE);
        emit_return();
        break;
      }

      case JBC_getstatic: {
        VM_FieldReference fieldRef = bcodes.getFieldReference();
        if (shouldPrint) asm.noteBytecode(biStart, "getstatic", fieldRef);
        if (fieldRef.needsDynamicLink(method)) {
          if (VM.VerifyUnint && !isInterruptible) forbiddenBytecode("unresolved getstatic ", fieldRef);
          emit_unresolved_getstatic(fieldRef);
        } else {
          emit_resolved_getstatic(fieldRef);
        }
        break;
      }
      
      case JBC_putstatic: {
        VM_FieldReference fieldRef = bcodes.getFieldReference();
        if (shouldPrint) asm.noteBytecode(biStart, "putstatic", fieldRef);
        if (fieldRef.needsDynamicLink(method)) {
          if (VM.VerifyUnint && !isInterruptible) forbiddenBytecode("unresolved putstatic ", fieldRef);
          emit_unresolved_putstatic(fieldRef);
        } else {
          emit_resolved_putstatic(fieldRef);
        }
        break;
      }

      case JBC_getfield: {
        VM_FieldReference fieldRef = bcodes.getFieldReference();
        if (shouldPrint) asm.noteBytecode(biStart, "getfield", fieldRef);
        if (fieldRef.needsDynamicLink(method)) {
          if (VM.VerifyUnint && !isInterruptible) forbiddenBytecode("unresolved getfield ", fieldRef);
          emit_unresolved_getfield(fieldRef);
        } else {
          emit_resolved_getfield(fieldRef);
        }
        break;
      }

      case JBC_putfield: {
        VM_FieldReference fieldRef = bcodes.getFieldReference();
        if (shouldPrint) asm.noteBytecode(biStart, "putfield", fieldRef);
        if (fieldRef.needsDynamicLink(method)) {
          if (VM.VerifyUnint && !isInterruptible) forbiddenBytecode("unresolved putfield ", fieldRef);
          emit_unresolved_putfield(fieldRef);
        } else {
          emit_resolved_putfield(fieldRef);
        }
        break;
      }  

      case JBC_invokevirtual: {
        //-#if RVM_WITH_OSR
        VM_ForwardReference xx = null;
        if (biStart == this.pendingIdx) {
          VM_ForwardReference x = emit_pending_goto(0);  // goto X
          this.pendingRef.resolve(asm);          // pendingIdx:     (target of pending goto in prologue)
          VM_CompiledMethod cm = VM_CompiledMethods.getCompiledMethod(this.pendingCMID);
          if (VM.VerifyAssertions) VM._assert(cm.isSpecialForOSR());      
          emit_invoke_compiledmethod(cm);  //  invoke_cmid
          xx = emit_pending_goto(0);                     // goto XX
          x.resolve(asm);                       //  X:
        }
        //-#endif

        VM_MethodReference methodRef = bcodes.getMethodReference();
        if (shouldPrint) asm.noteBytecode(biStart, "invokevirtual", methodRef);
        if (methodRef.getType().isMagicType()) {
          if (emit_Magic(methodRef)) {
            break;
          }
        } 

        if (methodRef.needsDynamicLink(method)) {
          if (VM.VerifyUnint && !isInterruptible) forbiddenBytecode("unresolved invokevirtual ", methodRef);
          emit_unresolved_invokevirtual(methodRef);
        } else {
          if (VM.VerifyUnint && !isInterruptible) checkTarget(methodRef.peekResolvedMethod());
          emit_resolved_invokevirtual(methodRef);
        }

        //-#if RVM_WITH_OSR
        if (xx != null) {
          xx.resolve(asm);                     // XX:
        }
        //-#endif
        break;
      }

      case JBC_invokespecial: {
        //-#if RVM_WITH_OSR
        VM_ForwardReference xx = null;
        if (biStart == this.pendingIdx) {
          VM_ForwardReference x = emit_pending_goto(0);  // goto X
          this.pendingRef.resolve(asm);          // pendingIdx:     (target of pending goto in prologue)
          VM_CompiledMethod cm = VM_CompiledMethods.getCompiledMethod(this.pendingCMID);
          if (VM.VerifyAssertions) VM._assert(cm.isSpecialForOSR());      
          emit_invoke_compiledmethod(cm);  //  invoke_cmid
          xx = emit_pending_goto(0);                     // goto XX
          x.resolve(asm);                       //  X:
        }
        //-#endif
        VM_MethodReference methodRef = bcodes.getMethodReference();
        if (shouldPrint) asm.noteBytecode(biStart, "invokespecial", methodRef);
        VM_Method target = methodRef.resolveInvokeSpecial();
        if (target != null) {
          if (VM.VerifyUnint && !isInterruptible) checkTarget(target);
          emit_resolved_invokespecial(methodRef, target);
        } else {
          emit_unresolved_invokespecial(methodRef);
        }     

        //-#if RVM_WITH_OSR
        if (xx != null) {
          xx.resolve(asm);                     // XX:
        }
        //-#endif

        break;
      }

      case JBC_invokestatic: {
        //-#if RVM_WITH_OSR
        VM_ForwardReference xx = null;
        if (biStart == this.pendingIdx) {
          VM_ForwardReference x = emit_pending_goto(0);  // goto X
          this.pendingRef.resolve(asm);          // pendingIdx:     (target of pending goto in prologue)
          VM_CompiledMethod cm = VM_CompiledMethods.getCompiledMethod(this.pendingCMID);
          if (VM.VerifyAssertions) VM._assert(cm.isSpecialForOSR());      
          emit_invoke_compiledmethod(cm);  //  invoke_cmid
          xx = emit_pending_goto(0);                     // goto XX
          x.resolve(asm);                       //  X:
        }
        //-#endif

        VM_MethodReference methodRef = bcodes.getMethodReference();
        if (shouldPrint) asm.noteBytecode(biStart, "invokestatic", methodRef);
        if (methodRef.getType().isMagicType()) {
          if (emit_Magic(methodRef))
            break;
        }
        if (methodRef.needsDynamicLink(method)) {
          if (VM.VerifyUnint && !isInterruptible) forbiddenBytecode("unresolved invokestatic ", methodRef);
          emit_unresolved_invokestatic(methodRef);
        } else {
          if (VM.VerifyUnint && !isInterruptible) checkTarget(methodRef.peekResolvedMethod());
          emit_resolved_invokestatic(methodRef);
        }

        //-#if RVM_WITH_OSR
        if (xx != null) {
          xx.resolve(asm);                     // XX:
        }
        //-#endif

        break;
      }

      case JBC_invokeinterface: {
        //-#if RVM_WITH_OSR
        VM_ForwardReference xx = null;
        if (biStart == this.pendingIdx) {
          VM_ForwardReference x = emit_pending_goto(0);  // goto X
          this.pendingRef.resolve(asm);          // pendingIdx:     (target of pending goto in prologue)
          VM_CompiledMethod cm = VM_CompiledMethods.getCompiledMethod(this.pendingCMID);
          if (VM.VerifyAssertions) VM._assert(cm.isSpecialForOSR());      
          emit_invoke_compiledmethod(cm);  //  invoke_cmid
          xx = emit_pending_goto(0);                     // goto XX
          x.resolve(asm);                       //  X:
        }
        //-#endif

        VM_MethodReference methodRef = bcodes.getMethodReference();
        bcodes.alignInvokeInterface();
        if (shouldPrint) asm.noteBytecode(biStart, "invokeinterface", methodRef);
        if (VM.VerifyUnint && !isInterruptible) forbiddenBytecode("invokeinterface ", methodRef);
        emit_invokeinterface(methodRef); 

        //-#if RVM_WITH_OSR
        if (xx != null) {
          xx.resolve(asm);                     // XX:
        }
        //-#endif

        break;
      }

      case JBC_xxxunusedxxx: {
        if (shouldPrint) asm.noteBytecode(biStart, "unused");
        if (VM.VerifyAssertions) VM._assert(VM.NOT_REACHED);
        break;
      }

      case JBC_new: {
        VM_TypeReference typeRef = bcodes.getTypeReference();
        if (shouldPrint) asm.noteBytecode(biStart, "new", typeRef);
        if (VM.VerifyUnint && !isInterruptible) forbiddenBytecode("new ", typeRef);
        VM_Type type = typeRef.peekResolvedType();
        if (type != null && (type.isInitialized() || type.isInBootImage())) { 
          emit_resolved_new(type.asClass());
        } else { 
          emit_unresolved_new(typeRef);
        }
        break;
      }

      case JBC_newarray: {
        int atype = bcodes.getArrayElementType();
        VM_Array array = VM_Array.getPrimitiveArrayType(atype);
        if (VM.VerifyAssertions) VM._assert(array.isResolved());
        if (shouldPrint) asm.noteBytecode(biStart, "newarray", array.getTypeRef());
        if (VM.VerifyUnint && !isInterruptible) forbiddenBytecode("new ", array);
        emit_resolved_newarray(array);
        break;
      }

      case JBC_anewarray: {
        VM_TypeReference elementTypeRef = bcodes.getTypeReference();
        VM_TypeReference arrayRef = elementTypeRef.getArrayTypeForElementType();

        if (shouldPrint) asm.noteBytecode(biStart, "anewarray new", arrayRef);
        if (VM.VerifyUnint && !isInterruptible) forbiddenBytecode("new ", arrayRef);
        
        if (VM.VerifyAssertions && elementTypeRef.isWordType()) {
          VM._assert(false, "During compilation of " + method 
                    + " found an anewarray of "+ elementTypeRef + "\n"
                    + "You must use the 'create' function to create an array of this type");
        }

        // We can do early resolution of the array type if the element type 
        // is already initialized.
        VM_Array array = (VM_Array)arrayRef.peekResolvedType();
        if (array != null && !(array.isInitialized() || array.isInBootImage()) &&
            VM_Type.JavaLangObjectType.isInstantiated()) {
          VM_Type elementType = elementTypeRef.peekResolvedType();
          if (elementType != null && (elementType.isInitialized() || elementType.isInBootImage())) {
            array.resolve();
            array.instantiate();
          }
          if (array.isInitialized() || array.isInBootImage()) {
            emit_resolved_newarray(array);
            break;
          }
        }
        emit_unresolved_newarray(arrayRef);
        break;
      }

      case JBC_arraylength: { 
        if (shouldPrint) asm.noteBytecode(biStart, "arraylength");
        emit_arraylength();
        break;
      }

      case JBC_athrow: {
        if (shouldPrint) asm.noteBytecode(biStart, "athrow");  
        if (VM.UseEpilogueYieldPoints) emit_threadSwitchTest(VM_Thread.EPILOGUE);
        if (VM.VerifyUnint && !isInterruptible) forbiddenBytecode("athrow");
        emit_athrow();
        break;
      }

      case JBC_checkcast: {
        VM_TypeReference typeRef = bcodes.getTypeReference();
        if (shouldPrint) asm.noteBytecode(biStart, "checkcast", typeRef);
        VM_Type type = typeRef.peekResolvedType();
        if (type != null) {
          if (type.isClassType()) {
            if (type.asClass().isFinal()) {
              emit_checkcast_final(type);
              break;
            } else if (type.isResolved() && !type.asClass().isInterface()) {
              emit_checkcast_resolvedClass(type);
              break;
            } // else fall through to emit_checkcast
          } else if (type.isArrayType()) {
            VM_Type elemType = type.asArray().getElementType();
            if (elemType.isPrimitiveType() || 
                (elemType.isClassType() && elemType.asClass().isFinal())) {
              emit_checkcast_final(type);
              break;
            } // else fall through to emit_checkcast
          } else {
            // checkcast to a primitive. Must be a word type.
            if (VM.VerifyAssertions) VM._assert(type.isWordType());
            break;
          }
        }
        if (VM.VerifyUnint && !isInterruptible) forbiddenBytecode("checkcast ", typeRef);
        emit_checkcast(typeRef);
        break;
      }

      case JBC_instanceof: {
        VM_TypeReference typeRef = bcodes.getTypeReference();
        if (shouldPrint) asm.noteBytecode(biStart, "instanceof", typeRef);
        VM_Type type = typeRef.peekResolvedType();
        if (type != null) {
          if (type.isClassType()) {
            if (type.asClass().isFinal()) {
              emit_instanceof_final(type);
              break;
            } else if (type.isResolved() && !type.asClass().isInterface()) {
              emit_instanceof_resolvedClass(type);
              break;
            }
          } else if (type.isArrayType()) {
            VM_Type elemType = type.asArray().getElementType();
            if (elemType.isPrimitiveType() || 
                (elemType.isClassType() && elemType.asClass().isFinal())) {
              emit_instanceof_final(type);
              break;
            }
          }
        }
        if (VM.VerifyUnint && !isInterruptible) forbiddenBytecode("instanceof ", typeRef);
        emit_instanceof(typeRef);
        break;
      }

      case JBC_monitorenter: {
        if (shouldPrint) asm.noteBytecode(biStart, "monitorenter");  
        if (VM.VerifyUnint && !isInterruptible) forbiddenBytecode("monitorenter");
        emit_monitorenter();
        break;
      }

      case JBC_monitorexit: {
        if (shouldPrint) asm.noteBytecode(biStart, "monitorexit"); 
        if (VM.VerifyUnint && !isInterruptible) forbiddenBytecode("monitorexit");
        emit_monitorexit();
        break;
      }

      case JBC_wide: {
        int widecode = bcodes.getWideOpcode();
        int index = bcodes.getWideLocalNumber();
        switch (widecode) {
        case JBC_iload: {
          if (shouldPrint) asm.noteBytecode(biStart, "wide iload", index);
          emit_iload(index);
          break;
        }
        case JBC_lload: {
          if (shouldPrint) asm.noteBytecode(biStart, "wide lload", index);
          emit_lload(index);
          break;
        }
        case JBC_fload: {
          if (shouldPrint) asm.noteBytecode(biStart, "wide fload", index);
          emit_fload(index);
          break;
        }
        case JBC_dload: {
          if (shouldPrint) asm.noteBytecode(biStart, "wide dload", index);
          emit_dload(index);
          break;
        }
        case JBC_aload: {
          if (shouldPrint) asm.noteBytecode(biStart, "wide aload", index);
          emit_aload(index);
          break;
        }
        case JBC_istore: {
          if (shouldPrint) asm.noteBytecode(biStart, "wide istore", index);
          emit_istore(index);
          break;
        }
        case JBC_lstore: {
          if (shouldPrint) asm.noteBytecode(biStart, "wide lstore", index);
          emit_lstore(index);
          break;
        }
        case JBC_fstore: {
          if (shouldPrint) asm.noteBytecode(biStart, "wide fstore", index);
          emit_fstore(index);
          break;
        }
        case JBC_dstore: {
          if (shouldPrint) asm.noteBytecode(biStart, "wide dstore", index);
          emit_dstore(index);
          break;
        }
        case JBC_astore: {
          if (shouldPrint) asm.noteBytecode(biStart, "wide astore", index);
          emit_astore(index);
          break;
        }
        case JBC_iinc: {
          int val = bcodes.getWideIncrement();
          if (shouldPrint) asm.noteBytecode(biStart, "wide inc", index, val);
          emit_iinc(index, val);
          break;
        }
        case JBC_ret: {
          if (shouldPrint) asm.noteBytecode(biStart, "wide ret", index);
          emit_ret(index);
          break;
        }
        default:
          if (VM.VerifyAssertions) VM._assert(VM.NOT_REACHED);
        }
        break;
      }

      case JBC_multianewarray: {
        VM_TypeReference typeRef = bcodes.getTypeReference();
        int dimensions        = bcodes.getArrayDimension();
        if (shouldPrint) asm.noteBytecode(biStart, "multianewarray", typeRef);
        if (VM.VerifyUnint && !isInterruptible) forbiddenBytecode("multianewarray");
        emit_multianewarray(typeRef, dimensions);
        break;
      }

      case JBC_ifnull: {
        int offset = bcodes.getBranchOffset();
        int bTarget = biStart + offset;
        if (shouldPrint) asm.noteBranchBytecode(biStart, "ifnull", offset, bTarget);
        if (offset < 0) emit_threadSwitchTest(VM_Thread.BACKEDGE);
        emit_ifnull(bTarget);
        break;
      }

      case JBC_ifnonnull: {
        int offset = bcodes.getBranchOffset();
        int bTarget = biStart + offset;
        if (shouldPrint) asm.noteBranchBytecode(biStart, "ifnonnull", offset, bTarget);
        if (offset < 0) emit_threadSwitchTest(VM_Thread.BACKEDGE);
        emit_ifnonnull(bTarget);
        break;
      }

      case JBC_goto_w: {
        int offset = bcodes.getWideBranchOffset();
        int bTarget = biStart + offset;
        if (shouldPrint) asm.noteBranchBytecode(biStart, "goto_w", offset, bTarget);
        if(offset < 0) emit_threadSwitchTest(VM_Thread.BACKEDGE);
        emit_goto(bTarget);
        break;
      }

      case JBC_jsr_w: {
        int offset = bcodes.getWideBranchOffset();
        int bTarget = biStart + offset;
        if (shouldPrint) asm.noteBranchBytecode(biStart, "jsr_w", offset, bTarget);
        emit_jsr(bTarget);
        break;
      }

      //-#if RVM_WITH_OSR
      /* CAUTION: can not use JBC_impdep1, which is 0xfffffffe ( signed ),
       * this is not consistant with OPT compiler.
       */
      case JBC_impdep1: /* --- pseudo bytecode --- */ {
        int pseudo_opcode = bcodes.nextPseudoInstruction(); 
        // pseudo instruction
        switch (pseudo_opcode) {
        case PSEUDO_LoadIntConst: {
          int value = bcodes.readIntConst();

          if (shouldPrint) asm.noteBytecode(biStart, "pseudo_load_int", value);
          
          int slot = VM_Statics.findOrCreateIntLiteral(value);
          int offset = slot << LOG_BYTES_IN_INT;

          emit_ldc(offset);
            
          break;
        }
        case PSEUDO_LoadLongConst: {
          long value = bcodes.readLongConst();  // fetch8BytesUnsigned();
 
          if (shouldPrint) asm.noteBytecode(biStart, "pseudo_load_long", value);
          
          int slot = VM_Statics.findOrCreateLongLiteral(value);
          int offset = slot << LOG_BYTES_IN_INT;

          emit_ldc2(offset);

          break;
        }
        case PSEUDO_LoadFloatConst: {
          int ibits = bcodes.readIntConst(); // fetch4BytesSigned();
          
          if (shouldPrint) asm.noteBytecode(biStart, "pseudo_load_float", ibits);
          
          int slot = VM_Statics.findOrCreateFloatLiteral(ibits);
          int offset = slot << LOG_BYTES_IN_INT;

          emit_ldc(offset);

          break;
        }
        case PSEUDO_LoadDoubleConst: {
          long lbits = bcodes.readLongConst(); // fetch8BytesUnsigned();

          if (shouldPrint) asm.noteBytecode(biStart, "pseudo_load_double", lbits);
          
          int slot = VM_Statics.findOrCreateDoubleLiteral(lbits);
          int offset = slot << LOG_BYTES_IN_INT;

          emit_ldc2(offset);

          break;
        }
        case PSEUDO_LoadAddrConst: {
          int bcIndex = bcodes.readIntConst(); // fetch4BytesSigned();

          if (shouldPrint) asm.noteBytecode(biStart, "pseudo_load_addr", bcIndex);
          // for bytecode to get future bytecode's address
          // we register it and patch it later.
          emit_loadaddrconst(bcIndex);

          break;
        }
        case PSEUDO_InvokeStatic: {
          VM_Method methodRef = null;
          int targetidx = bcodes.readIntConst(); // fetch4BytesSigned();
          switch (targetidx) {
          case GETREFAT:
            methodRef = VM_Entrypoints.osrGetRefAtMethod;
            break;
          case CLEANREFS:
            methodRef = VM_Entrypoints.osrCleanRefsMethod;
            break;
          default:
            if (VM.TraceOnStackReplacement) VM.sysWriteln("pseudo_invokstatic with unknown target index "+targetidx);
            if (VM.VerifyAssertions) VM._assert(VM.NOT_REACHED);
            break;
          }

          if (shouldPrint) asm.noteBytecode(biStart, "pseudo_invokestatic", methodRef);
          emit_resolved_invokestatic(methodRef.getMemberRef().asMethodReference());
          break;
        }
          /*
        case PSEUDO_CheckCast: {

          if (shouldPrint) asm.noteBytecode(biStart, "pseudo_checkcast");
          
          // fetch 4 byte type id
          int tid = bcodes.readIntConst(); // fetch4BytesSigned();
          // do nothing now
          break;
        }
          */
        case PSEUDO_InvokeCompiledMethod: {
          int cmid = bcodes.readIntConst(); // fetch4BytesSigned();    // callee's cmid
          int origIdx = bcodes.readIntConst(); // fetch4BytesSigned(); // orginal bytecode index of this call (for build gc map)

          if (shouldPrint) asm.noteBytecode(biStart, "pseudo_invoke_cmid", cmid);
          
          this.pendingCMID = cmid;
          this.pendingIdx = origIdx+this.method.getOsrPrologueLength();
          this.pendingRef = emit_pending_goto(this.pendingIdx);
          /*
          VM_CompiledMethod cm = VM_CompiledMethods.getCompiledMethod(cmid);
          if (VM.VerifyAssertions) VM._assert(cm.isSpecialForOSR());      
          emit_invoke_compiledmethod(cm);
          */
          break;
        }
        case PSEUDO_ParamInitEnd: {
          if (shouldPrint) asm.noteBytecode(biStart, "pseudo_paraminitend");
          // now we can inserted stack overflow check, 
          emit_deferred_prologue();
          break;
        }
        default:
          if (VM.TraceOnStackReplacement) VM.sysWrite("Unexpected PSEUDO code "
                                                      +VM.intAsHexString(pseudo_opcode)+"\n");
          if (VM.VerifyAssertions) VM._assert(VM.NOT_REACHED);
          break;
        }

        break;
      }
      //-#endif

      default:
        VM.sysWrite("VM_Compiler: unexpected bytecode: " + VM_Services.getHexString((int)code, false) + "\n");
        if (VM.VerifyAssertions) VM._assert(VM.NOT_REACHED);
      }
    }
    bytecodeMap[bcodes.length()] = asm.getMachineCodeIndex();
    return asm.finalizeMachineCode(bytecodeMap);
  }

  //-#if RVM_WITH_OSR
  /* for invoke compiled method, we have to fool GC map, 
   * InvokeCompiledMethod has two parameters compiledMethodID 
   * and originalBytecodeIndex of that call site
   * 
   * we make the InvokeCompiledMethod pending until generating code
   * for the original call.
   * it looks like following instruction sequence:
   *    invokeCompiledMethod cmid, bc
   *
   *  ==>  forward (x)
   *
   *          bc:  forward(x')
   *             resolve (x):
   *               invoke cmid
   *               forward(x")
   *             resolve (x'):
   *               invoke xxxx
   *             resolve (x");
   * in this way, the instruction for invokeCompiledMethod is right before
   * the original call, and it uses the original call's GC map
   */
  private int pendingCMID = -1;
  private int pendingIdx  = -1;
  private VM_ForwardReference pendingRef = null;
  //-#endif

  /**
   * Print a warning message whan we compile a bytecode that is forbidden in
   * Uninterruptible code.
   * 
   * @param msg description of bytecode that is violating the invariant
   * @param obj object that provides further information
   */
  protected final void forbiddenBytecode(String msg, Object obj) throws VM_PragmaNoInline {
    forbiddenBytecode(msg+obj);
  }

  /**
   * Print a warning message whan we compile a bytecode that is forbidden in
   * Uninterruptible code.
   * 
   * @param msg description of bytecode that is violating the invariant
   */
  protected final void forbiddenBytecode(String msg) throws VM_PragmaNoInline {
    if (!VM.ParanoidVerifyUnint && VM_PragmaLogicallyUninterruptible.declaredBy(method)) return; //Programmer has asserted that we don't have to do checking for this method.
    VM.sysWriteln("WARNING " + method + ": contains forbidden bytecode " + msg);
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
   * Notify VM_Compiler that we are starting code gen for the bytecode biStart
   */
  protected abstract void starting_bytecode();

  /**
   * Emit the prologue for the method
   */
  protected abstract void emit_prologue();

  /**
   * Emit the code for a threadswitch tests (aka a yieldpoint).
   * @param whereFrom is this thread switch from a PROLOGUE, BACKEDGE, or EPILOGUE?
   */
  protected abstract void emit_threadSwitchTest(int whereFrom);

  //-#if RVM_WITH_OSR
  protected abstract void emit_threadSwitch(int whereFrom);
  protected abstract void emit_deferred_prologue();
  //-#endif

  /**
   * Emit the code to implement the spcified magic.
   * @param magicMethod desired magic
   */
  protected abstract boolean emit_Magic(VM_MethodReference magicMethod);


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
  protected abstract void emit_unresolved_getstatic(VM_FieldReference fieldRef);

  /**
   * Emit code to implement a getstatic
   * @param fieldRef the referenced field
   */
  protected abstract void emit_resolved_getstatic(VM_FieldReference fieldRef);


  /**
   * Emit code to implement a dynamically linked putstatic
   * @param fieldRef the referenced field
   */
  protected abstract void emit_unresolved_putstatic(VM_FieldReference fieldRef);

  /**
   * Emit code to implement a putstatic
   * @param fieldRef the referenced field
   */
  protected abstract void emit_resolved_putstatic(VM_FieldReference fieldRef);


  /**
   * Emit code to implement a dynamically linked getfield
   * @param fieldRef the referenced field
   */
  protected abstract void emit_unresolved_getfield(VM_FieldReference fieldRef);

  /**
   * Emit code to implement a getfield
   * @param fieldRef the referenced field
   */
  protected abstract void emit_resolved_getfield(VM_FieldReference fieldRef);


  /**
   * Emit code to implement a dynamically linked putfield
   * @param fieldRef the referenced field
   */
  protected abstract void emit_unresolved_putfield(VM_FieldReference fieldRef);

  /**
   * Emit code to implement a putfield
   * @param fieldRef the referenced field
   */
  protected abstract void emit_resolved_putfield(VM_FieldReference fieldRef);


  /*
   * method invocation
   */

  /**
   * Emit code to implement a dynamically linked invokevirtual
   * @param methodRef the referenced method
   */
  protected abstract void emit_unresolved_invokevirtual(VM_MethodReference methodRef);

  /**
   * Emit code to implement invokevirtual
   * @param methodRef the referenced method
   */
  protected abstract void emit_resolved_invokevirtual(VM_MethodReference methodRef);


  /**
   * Emit code to implement a dynamically linked invokespecial
   * @param methodRef the referenced method
   * @param target the method to invoke
   */
  protected abstract void emit_resolved_invokespecial(VM_MethodReference methodRef, VM_Method target);

  /**
   * Emit code to implement invokespecial
   * @param methodRef the referenced method
   */
  protected abstract void emit_unresolved_invokespecial(VM_MethodReference methodRef);


  /**
   * Emit code to implement a dynamically linked invokestatic
   * @param methodRef the referenced method
   */
  protected abstract void emit_unresolved_invokestatic(VM_MethodReference methodRef);

  /**
   * Emit code to implement invokestatic
   * @param methodRef the referenced method
   */
  protected abstract void emit_resolved_invokestatic(VM_MethodReference methodRef);

  //-#if RVM_WITH_OSR
  protected abstract void emit_invoke_compiledmethod(VM_CompiledMethod cm);
  protected abstract VM_ForwardReference emit_pending_goto(int origidx);
  //-#endif

  /**
   * Emit code to implement the invokeinterface bytecode
   * @param methodRef the referenced method
   * @param count number of parameter words (see invokeinterface bytecode)
   */
  protected abstract void emit_invokeinterface(VM_MethodReference methodRef);
 

  /*
   * other object model functions
   */ 


  /**
   * Emit code to allocate a scalar object
   * @param type the VM_Class to instantiate
   */
  protected abstract void emit_resolved_new(VM_Class typeRef);

  /**
   * Emit code to dynamically link and allocate a scalar object
   * @param typeRef typeReference to dynamically link & instantiate
   */
  protected abstract void emit_unresolved_new(VM_TypeReference typeRef);

  /**
   * Emit code to allocate an array
   * @param array the VM_Array to instantiate
   */
  protected abstract void emit_resolved_newarray(VM_Array array);

  /**
   * Emit code to dynamically link the element class and allocate an array
   * @param typeRef typeReference to dynamically link & instantiate
   */
  protected abstract void emit_unresolved_newarray(VM_TypeReference typeRef);

  /**
   * Emit code to allocate a multi-dimensional array
   * @param typeRef typeReference to dynamically link & instantiate
   * @param dimensions the number of dimensions
   */
  protected abstract void emit_multianewarray(VM_TypeReference typeRef, int dimensions);

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
   */
  protected abstract void emit_checkcast(VM_TypeReference typeRef);

  /**
   * Emit code to implement the checkcast bytecode
   * @param type the LHS type
   */
  protected abstract void emit_checkcast_resolvedClass(VM_Type type);

  /**
   * Emit code to implement the checkcast bytecode
   * @param type the LHS type
   */
  protected abstract void emit_checkcast_final(VM_Type type);

  /**
   * Emit code to implement the instanceof bytecode
   * @param typeRef the LHS type
   */
  protected abstract void emit_instanceof(VM_TypeReference typeRef);

  /**
   * Emit code to implement the instanceof bytecode
   * @param type the LHS type
   */
  protected abstract void emit_instanceof_resolvedClass(VM_Type type);

  /**
   * Emit code to implement the instanceof bytecode
   * @param type the LHS type
   */
  protected abstract void emit_instanceof_final(VM_Type type);

  /**
   * Emit code to implement the monitorenter bytecode
   */
  protected abstract void emit_monitorenter();

  /**
   * Emit code to implement the monitorexit bytecode
   */
  protected abstract void emit_monitorexit();

  //-#if RVM_WITH_OSR
  protected abstract void emit_loadaddrconst(int bcIndex);
  //-#endif

}
