/*
 * (C) Copyright IBM Corp. 2001
 */
//$Id$
package com.ibm.JikesRVM;

import org.vmmagic.pragma.*;

//-#if RVM_WITH_OSR
import com.ibm.JikesRVM.OSR.*;
//-#endif
import com.ibm.JikesRVM.classloader.*;

/**
 * Baseline compiler - platform independent code.
 * Platform dependent versions extend this class and define
 * the host of abstract methods defined by VM_CompilerFramework to complete
 * the implementation of a baseline compiler for a particular target,
 *
 * @author Bowen Alpern
 * @author Maria Butrico
 * @author Anthony Cocchi
 * @author Dave Grove
 * @author Derek Lieber
 * @author Janice Shepherd
 */
public abstract class VM_BaselineCompiler extends VM_CompilerFramework
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
   * Next edge counter entry to allocate
   */
  protected int edgeCounterIdx;

  protected final int getEdgeCounterOffset() {
    return method.getId() << LOG_BYTES_IN_ADDRESS;
  }


  /**
   * Construct a VM_Compiler
   */
  protected VM_BaselineCompiler(VM_BaselineCompiledMethod cm) {
    super(cm);
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
    asm = new VM_Assembler(bcodes.length(), shouldPrint, (VM_Compiler)this);
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
      VM.sysExit(VM.EXIT_STATUS_BOGUS_COMMAND_LINE_ARG);
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
      VM.sysExit(VM.EXIT_STATUS_BOGUS_COMMAND_LINE_ARG);
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
    VM_ReferenceMaps refMaps = new VM_ReferenceMaps((VM_BaselineCompiledMethod)compiledMethod, stackHeights);
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

    // determine if we are going to insert edge counters for this method
    if (options.EDGE_COUNTERS && 
        !method.getDeclaringClass().isBridgeFromNative() &&
        (method.hasCondBranch() || method.hasSwitch())) {
      ((VM_BaselineCompiledMethod)compiledMethod).setHasCounterArray(); // yes, we will inject counters for this method.
    }

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
      ((VM_BaselineCompiledMethod)compiledMethod).setLockAcquisitionOffset(lockOffset);
    }
    ((VM_BaselineCompiledMethod)compiledMethod).encodeMappingInfo(refMaps, bcMap, instructions.length());
    compiledMethod.compileComplete(instructions);
    if (edgeCounterIdx > 0) {
      VM_EdgeCounts.allocateCounters(method, edgeCounterIdx);
    }
    if (shouldPrint) {
      ((VM_BaselineCompiledMethod)compiledMethod).printExceptionTable();
      printEndHeader(method);
    }
    if (VM.MeasureCompilation) {
      long end = VM_Thread.getCurrentThread().accumulateCycles();
      encodingCycles += end - start;
    }
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

  protected String getCompilerName() {
    return "baseline";
  }



}
