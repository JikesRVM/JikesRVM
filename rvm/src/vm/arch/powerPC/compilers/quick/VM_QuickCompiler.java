/*
 * (C) Copyright IBM Corp. 2001
 */
//$Id$
package com.ibm.JikesRVM.quick;

import com.ibm.JikesRVM.*;
import com.ibm.JikesRVM.memoryManagers.mmInterface.MM_Interface;
import com.ibm.JikesRVM.jni.VM_JNICompiler;
import com.ibm.JikesRVM.classloader.*;
import org.vmmagic.pragma.*;

/**
 * VM_QuickCompiler is a quicker compiler class for powerPC
 * architectures. The quick compiler is intended to be a way to
 * explore simple compiler optimizations without requiring an
 * understanding of the complex optimizing compiler
 * infrastructure. The quick compiler itself is an experiment to
 * determine how much efficiency can be gained through simple
 * optimizations rather than through a lengthy set of complex
 * optimizations that may provide only marginal improvement in
 * exchange for significant increases in the compilation time.
 *
 * Although it is very similar to the base compiler in that it
 * compiles directly from bytecodes into machine language without
 * transformations into intermediate representations, it tries to be
 * much smarter about register allocation. In particular, where the base
 * compiler stores its stack in memory, the quick compiler assigns a
 * certain number of registers to hold stack values and a certain number
 * to hold local variable values.
 *
 * It also tries, as much as possible, to do all its work in a single
 * pass through the bytecodes. In fact, it needs at least one pass to
 * generate the basic blocks and stack snapshots at entry to each block
 * and then one pass to generate code based on those basic blocks. But,
 * for instance, if the retargeting option is enabled the compiler
 * actually generates a machine code instruction that targets a stack
 * register. If it later determines that the instruction could have gone
 * directly into a local variable register or parameter register, it
 * rewrites the instruction it previously wrote. In other words, the
 * quick compiler doesn't do an analysis pass and then a code generation
 * pass.
 * 
 * One potentional point for future experimentation is in the assignment
 * of registers to the stack and to local variables if there are not
 * enough registers available. For instance, while generating the stack
 * snapshots it could determine which local variables are alive in only
 * a single block,. This would allow more than one variable to be
 * assigned to a register. Or it could determine which variables are
 * used a lot and ensure those get first choice of registers. In
 * practice, at least over the SPECjvm98 benchmark, most methods do not
 * seem to use more than a handful of stack and local variables.
 *
 * 
 *
 * @author Chris Hoffmann
 * @version 1.0
 */
public class VM_QuickCompiler extends VM_CompilerFramework
  implements VM_QuickConstants,
             VM_BaselineConstants, VM_BytecodeConstants,
             VM_BBConstants, VM_AssemblerConstants {



  // Begin platform-independent methods and fields, could be moved to
  // a separate parent class if this compiler is ever implemented on
  // anything but the PowerPC platform.
  
  private static long gcMapCycles;
  //private static long osrSetupCycles;
  private static long codeGenCycles;
  private static long encodingCycles;
  /** 
   * Options used during compiler execution 
   */
  public static VM_QuickOptions options;

  protected int edgeCounterIdx;

  private VM_QuickReferenceMaps refMaps;

  // If we're doing a short forward jump of less than 
  // this number of bytecodes, then we can always use a short-form
  // conditional branch (don't have to emit a nop & bc).
  private static final int SHORT_FORWARD_LIMIT = 500;

  // stackframe pseudo-constants //
  
  /*private*/ int frameSize;            //!!TODO: make private once VM_MagicCompiler is merged in
  private int stackBottomOffset;
  private int firstLocalOffset;
  private int spillOffset;
  private int callerSaveOffset;
  private int savedThisPtrOffset;
  private int firstScratchOffset;

  private int maxStackHeight;

  private int firstFixedStackRegister;
  private int lastFixedStackRegister;
  private int firstFloatStackRegister;
  private int lastFloatStackRegister;
  private int firstFixedLocalRegister;
  private int lastFixedLocalRegister;
  private int firstFloatLocalRegister;
  private int lastFloatLocalRegister;

  private int nextFixedStackRegister;
  private int nextFloatStackRegister;

  private int nLocalWords;
  private int nStackWords;

  /**
   * This field is set to true if the parameter (volatile) registers
  are in use and therefore are not available for use as scratch registers.
   *
   */
  private static boolean paramRegsInUse = false;
  private static int[] tempGPRs;
  private static int numTempGPRs;
  private static int tempGPRIndex=0;
  private static int[] tempFPRs;
  private static int numTempFPRs;
  private static int tempFPRIndex=0;

  private short currentBlock;
  private int currentStackHeight;
  private int pendingStackHeight;
  private byte[][] stackSnapshots;
  
  private short[] byteToBlockMap;
  private VM_BasicBlock[] basicBlocks;
  private static byte[] stack;
  private static int[]  stackLocations;
  
  private static byte[] stackAfterOpcode;
  private static int[]  stackLocationsAfterOpcode;

  private static byte[] gcMapLocalRegs;
  private static byte[] gcMapStackRegs;

  private boolean GCLocationsSet=false;

  private static int lastGCIndex;

  private boolean unreachableBytecode;

  private static int nesting = 0;

  private VM_ExceptionHandlerMap exceptions;
  // bytecode input //
  
  private int sri0;
  private int sri1;
  private int sri2;

  private int sro0;
  private boolean resultIsStackRegister = false;

  int resultStackIndex;         
  int  returnLocation;
  byte returnType;

  //-#if RVM_WITH_QUICK_COMPILER_MOVE_ELIMINATION
  int returnValue;
  public static boolean allowRetargeting = true;
  private static class RegisterDescription {
    int register=0;
    int slotIndex=0;
  };
  
  private static int[] slotContents;
  private static byte[] slotStatus;

  private static int[] valueContents;
  private static byte[] valueTypes;
  private static byte[] valueJavaTypes;
  private static long[] valuePendingToSlots;
  private static long[] valueCompletedToSlots;

  private static int[]  valueSetAtBCI;
  private static int[] valueSetAtMCI;
  private static int[]  valueSetInRegister;

  private static RegisterDescription registerDescription =
    new RegisterDescription();
  private final static byte STATUS_WRITTEN = 0;
  private final static byte STATUS_PENDING = 1;
  private final static byte STATUS_DEAD    = 3;

  private final static byte VALUE_TYPE_UNKNOWN = 0;
  private final static byte VALUE_TYPE_CONSTANT = 1;
  private final static byte VALUE_TYPE_CALCULATION = 2;
  private final static byte VALUE_TYPE_LOCAL = 3;

  private final static byte VALUE_NOT_SET = 0;
  private final static byte FIRST_VALUE = 1;


  private int currentValue;

  private boolean isGCPoint=false;

  //-#endif
  


   //--------------//
  // Static data. //
  //--------------//

  // The following constants describe the various stack manipulation
  // bytecodes. The format is pairs of stack positions: before and
  // after, where the position is relative to the current stack top.
  // For example, 0, +1 means the entry currently at the top of the
  // stack moves to the slot one position higher in the stack.
  
  // 1 0 | _ --> 0 1 | 0
  private final static int[] copyList_dup_x1 = {
     0, +1,
    -1,  0,
    +1, -1};
  
  // 2 1 0 | _ --> 0 2 1 | 0
  private final static int[] copyList_dup_x2 = {
     0, +1,
    -1,  0,
    -2, -1,
    +1, -2};

  // 1 0 | _ _ --> 1 0 | 1 0
  private final static int[] copyList_dup2 = {
     0, +2,
    -1, +1};

  // 2 1 0 | _ _ --> 1 0 2 | 1 0
  private final static int[] copyList_dup2_x1 = {
     0, +2,
    -1, +1,
    -2,  0,
    +2, -1,
    +1, -2};

  // 3 2 1 0 | _ _ --> 1 0 3 2 | 1 0
  private final static int[] copyList_dup2_x2 = {
     0, +2,
    -1, +1,
    -2,  0,
    -3, -1,
    +2, -2,
    +1, -3};

  private static int[] localWordLocation = new int[20];
  private static int[] localLongLocation = new int[20];
  private static int[] localFloatLocation = new int[20];
  private static int[] localDoubleLocation = new int[20];
  private static int[] stackWordLocation = new int[20];
  private static int[] stackLongLocation = new int[20];
  private static int[] stackFloatLocation = new int[20];
  private static int[] stackDoubleLocation = new int[20];

  
  // runtime support  

  private static boolean               alreadyInitialized;
  private static VM_ExceptionDeliverer exceptionDeliverer;


  /**
   * Construct a VM_QuickCompiler
   */
  protected VM_QuickCompiler(VM_QuickCompiledMethod cm) {
    super(cm);



    //-#if RVM_WITH_QUICK_COMPILER_MOVE_ELIMINATION
    allowRetargeting = options.INSTRUCTION_RETARGET;
    //-#endif

    shouldPrint  = (!VM.runningTool &&
                    (options.PRINT_MACHINECODE) &&
                    (!options.hasMETHOD_TO_PRINT() ||
                     options.fuzzyMatchMETHOD_TO_PRINT(method.toString())));


    if (!VM.runningTool && options.PRINT_METHOD) printMethodMessage();


    if (shouldPrint && !fullyBootedVM) {
      shouldPrint = false;
      if (options.PRINT_METHOD) {
        VM.sysWriteln("\ttoo early in VM.boot() to print machine code");
      }
    }
    asm = new VM_Assembler(bcodes.length(), shouldPrint);


  }

  ////////////////////////////////////////////
  // Initialization
  ////////////////////////////////////////////
  /**
   * Prepare compiler for use.
   * @param options options to use for compilations during initialization
   */
  public static void init (VM_QuickOptions o) {
    try {
      if (!(VM.writingBootImage || VM.runningTool || VM.runningVM)) {
        // Caller failed to ensure that the VM was initialized.
        throw new VM_QuickCompilerException("VM not initialized", true);
      }
      // Make a local copy so that some options can be forced off just for the
      // duration of this initialization step.
      options = (VM_QuickOptions)o.clone();

    } catch (VM_QuickCompilerException e) {
      // failures during initialization can't be ignored
      e.isFatal = true;
      throw e;
    } catch (Throwable e) {
      VM.sysWriteln( e.toString() );
      throw new VM_QuickCompilerException("Quick compiler", 
                                                "untrapped failure during init, "
                                                + " Converting to VM_QuickCompilerException");
    }
  }

  /**
   * Modify the options used while compiling the boot image
   * @param options the options to modify
   */
  public static void setBootOptions(VM_QuickOptions o) {
    // Currently, we do nothing special to the options. The opt
    // compiler, for instance, modifies the options instance to set
    // the optimization level.
  }

  /**
   * Clear out crud from bootimage writing
   */
  public static void initOptions() {
    if (options == null)
      options = new VM_QuickOptions();
  }
  
  /**
   * Now that VM is fully booted, enable options 
   * such as PRINT_MACHINE_CODE that require a fully booted VM.
   */
  public static void fullyBootedVM() {
    // If the user has requested machine code dumps, then force a test 
    // of method to print option so extra classes needed to process 
    // matching will be loaded and compiled upfront. Thus avoiding getting
    // stuck looping by just asking if we have a match in the middle of 
    // compilation. Pick an obsure string for the check.
    if (options != null && options.hasMETHOD_TO_PRINT() && options.fuzzyMatchMETHOD_TO_PRINT("???")) {
      VM.sysWrite("??? is not a sensible string to specify for method name");
    }
    //-#if !RVM_WITH_ADAPTIVE_SYSTEM
    if (options != null && options.PRELOAD_CLASS != null) {
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
      VM.sysWrite("VM_QuickCompiler: Unrecognized argument \""+ arg + "\"\n");
      VM.sysExit(VM.EXIT_STATUS_BOGUS_COMMAND_LINE_ARG);
    }
  }


  /**
   * Compile the given method with the baseline compiler.
   * 
   * @param method the VM_NormalMethod  to compile.
   * @return the generated VM_CompiledMethod for said VM_NormalMethod .
   */
  public static
    VM_CompiledMethod compile (VM_NormalMethod  method) {
    if (nesting > 0) {
      throw new VM_QuickCompilerException("Reentrant call to quick compiler", false);
    }
    if (method.getDeclaringClass().isDynamicBridge())
      throw new VM_QuickCompilerException("Can't quick compile dynamic bridge", false);
    
    // synchronized 
    VM_QuickCompiledMethod cm = (VM_QuickCompiledMethod)VM_CompiledMethods.createCompiledMethod(method, VM_CompiledMethod.QUICK);
    try {
      nesting += 1;
      new VM_QuickCompiler(cm).compile();
      return cm;
    }

    catch (Exception e) {
      throw new VM_QuickCompilerException(e.getMessage(), false);
    }

    finally {
      nesting -= 1;
    }
  }

  /**
   * Generate a report of time spent in various phases of the quick compiler.
   * <p> NB: This method may be called in a context where classloading and/or 
   * GC cannot be allowed.
   * Therefore we must use primitive sysWrites for output and avoid string 
   * appends and other allocations.
   *
   * @param explain Should an explanation of the metrics be generated?
   */
  public static void generateQuickCompilerSubsystemReport(boolean explain) {
    VM.sysWriteln("\n\t\tQuick Compiler SubSystem");
    VM.sysWriteln("\tPhase\t\t\t    Time");
    VM.sysWriteln("\t\t\t\t(ms)    (%ofTotal)");

    double gcMapTime = VM_Time.cyclesToMillis(gcMapCycles);
    double osrSetupTime = 0.0;
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

    //-#if RVM_WITH_QUICK_COMPILER_BLOCK_COUNT
    printBlockCounts();
    //-#endif

  }

  /**
   * Top level driver for baseline compilation of a method.
   */
  protected void compile() {
    if (shouldPrint) printStartHeader(method);

    exceptions = method.getExceptionHandlerMap();
    long start = 0;
    //-#if RVM_WITH_OSR
    /* reference map and stackheights were computed using original bytecodes
     * and possibly new operand words
     * recompute the stack height, but keep the operand words of the code 
     * generation consistant with reference map 
     */
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
    //-#endif

    // determine if we are going to insert edge counters for this method
    if (options.EDGE_COUNTERS && 
        !method.getDeclaringClass().isBridgeFromNative() &&
        (method.hasCondBranch() || method.hasSwitch())) {
      ((VM_QuickCompiledMethod)compiledMethod).setHasCounterArray(); // yes, we will inject counters for this method.
    }

    /* initialization */
    { 
      if (VM.VerifyAssertions) VM._assert(T3 <= LAST_VOLATILE_GPR);           // need 4 gp temps
      if (VM.VerifyAssertions) VM._assert(F3 <= LAST_VOLATILE_FPR);           // need 4 fp temps
      if (VM.VerifyAssertions) VM._assert(NUM_FIXED_STACK_REGISTERS > 1);  // space for two words in memory
      if (VM.VerifyAssertions) VM._assert(NUM_FIXED_STACK_REGISTERS + FIRST_FIXED_STACK_REGISTER - 1 <= LAST_NONVOLATILE_GPR);

      // Set these two first as some of the following *Offset methods
      // need them.
      this.nLocalWords = method.getLocalWords();
      this.nStackWords = method.getOperandWords();
      this.maxStackHeight =  this.nStackWords;
      this.frameSize          = getFrameSize(method);
      this.firstLocalOffset   = getFirstLocalOffset(method);
      this.stackBottomOffset  = getStackBottomOffset(method);
      this.callerSaveOffset   = getCallerSaveOffset(method);
      this.savedThisPtrOffset = getThisPtrSaveAreaOffset(method);
      this.firstScratchOffset = getScratchOffset(method);
      this.currentBlock = VM_BasicBlock.NOTBLOCK;
      
    if (VM.MeasureCompilation) start = VM_Thread.getCurrentThread().accumulateCycles();
    this.refMaps = new VM_QuickReferenceMaps((VM_QuickCompiledMethod) compiledMethod,
                                             nLocalWords, nStackWords,
                                             firstLocalOffset, stackBottomOffset,
                                             savedThisPtrOffset,
                                             stackHeights);
    if (VM.MeasureCompilation) {
      long end = VM_Thread.getCurrentThread().accumulateCycles();
      gcMapCycles += end - start;
    }
      this.stackSnapshots = refMaps.getStackSnapshots();
      this.byteToBlockMap = refMaps.getByteToBlockMap();
      this.basicBlocks    = refMaps.getBasicBlocks();


      defineStackAndLocalRegisters(nLocalWords, nStackWords);
      defineTempRegisters(nLocalWords, nStackWords);
      refMaps.setRegisterRanges(firstFixedLocalRegister,
                                lastFixedLocalRegister,
                                firstFixedStackRegister,
                                lastFixedStackRegister);
      ((VM_QuickCompiledMethod)compiledMethod).setRegisterRanges(min(firstFixedLocalRegister,firstFixedStackRegister),
                                                                 max(lastFixedLocalRegister, lastFixedStackRegister),
                                                                 min(firstFloatLocalRegister, firstFloatStackRegister),
                                                                 max(lastFloatLocalRegister, lastFloatStackRegister));
      
      
      initializeLocations(nLocalWords, nStackWords);

      
      if (gcMapLocalRegs == null ||
          gcMapLocalRegs.length < nLocalWords)
        gcMapLocalRegs = new byte[nLocalWords];
      if (gcMapStackRegs == null ||
          gcMapStackRegs.length < nStackWords)
        gcMapStackRegs = new byte[nStackWords];


      //-#if RVM_WITH_QUICK_COMPILER_MOVE_ELIMINATION
      moveElimInit();
      //-#endif

      // ... add a little extra to stack to make manipulations
      // easer
      int stackSize = nStackWords + 5;
      if (stack == null || stack.length < stackSize) {
        stack = new byte[stackSize];
        stackAfterOpcode = new byte[stackSize];
        stackLocations = new int[stackSize];
        stackLocationsAfterOpcode = new int[stackSize];
      }
    }
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
    //-#endif
        
    if (VM.MeasureCompilation) start = VM_Thread.getCurrentThread().accumulateCycles();
    if (method.isSynchronized()) {
      ((VM_QuickCompiledMethod)compiledMethod).setLockAcquisitionOffset(lockOffset);
    }
    ((VM_QuickCompiledMethod)compiledMethod).encodeMappingInfo(refMaps, bcMap, instructions.length());
    compiledMethod.compileComplete(instructions);
    refMaps.compilationComplete();
    if (edgeCounterIdx > 0) {
      VM_EdgeCounts.allocateCounters(method, edgeCounterIdx);
    }
    if (shouldPrint) {
      ((VM_QuickCompiledMethod)compiledMethod).printExceptionTable();
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

  /**
   * Returns the compiler type as a string
   *
   * @return the string "quick"
   */
  protected String getCompilerName() {
    return "quick";
  }



  /**
   * Determines the source and target registers needed for an
  opcode. Source registers are stored in the variables
  <code>sri0</code> to <code>sri2</code> (moving down the stack) and
  the target register is in <code>sro0</code>. If the stack item is
  not already in a stack slot stored in a register, generate code to
  move it into a scratch register.

  This routine is intended to be used in conjunction with
  <code>cleanupRegisters</code>, which will generate the code to move
  <code>sro0</code> to memory if need be.
   *
   * @param numPoppedFromStack number of items popped from the stack
   * @param typePushedOnStack the type pushed on the stack
   * @param moveToRegisters true if code should be generated to
   *actually move items into the registers.
   @see cleanupRegisters()
   */
  private final void assignRegisters(int numPoppedFromStack,
                                     byte typePushedOnStack) {
    assignRegisters(numPoppedFromStack, typePushedOnStack, true);
  }
  
  /**
   * Determines the source and target registers needed for an
  opcode. Source registers are stored in the variables
  <code>sri0</code> to <code>sri2</code> (moving down the stack) and
  the target register is in <code>sro0</code>. If the stack item is
  not already in a stack slot stored in a register, optionally
  generate code to move it into a scratch register.

  This routine is intended to be used in conjunction with
  <code>cleanupRegisters</code>, which will generate the code to move
  <code>sro0</code> to memory if need be.
   *
   * @param numPoppedFromStack number of items popped from the stack
   * @param typePushedOnStack the type pushed on the stack
   * @param moveToRegisters true if code should be generated to
   *actually move items into the registers.
   @see cleanupRegisters()
   */
  private final void assignRegisters(int numPoppedFromStack,
                                     byte typePushedOnStack,
                                     boolean moveToRegisters) {
    int r=-1;
    int location=0;


    //-#if RVM_WITH_QUICK_COMPILER_MOVE_ELIMINATION
    int liveStackHeight = currentStackHeight;
    for (int n=0; n < numPoppedFromStack; n++) {
      liveStackHeight -= 1;
      byte type = stack[liveStackHeight];
      if (type == LONG_TYPE || type == DOUBLE_TYPE)
        liveStackHeight -= 1;
    }
    //-#else
      moveToRegisters = true;
    //-#endif
    
    pendingStackHeight = currentStackHeight;
    for (int n=0; n < numPoppedFromStack; n++) {
      pendingStackHeight -= 1;
      byte type = stack[pendingStackHeight];
      if (type == LONG_TYPE || type == DOUBLE_TYPE)
        pendingStackHeight -= 1;
      if (moveToRegisters) {
        //-#if RVM_WITH_QUICK_COMPILER_MOVE_ELIMINATION
        r = useStackEntry(pendingStackHeight, liveStackHeight);
        location = registerToLocation(r);
        //-#endif
        if (isRegister(location))
          r = locationToRegister(location);
        else {
          if (needsFloatRegister(type))
            r = getTempRegister(FLOAT_TYPE);
          else
            r = getTempRegister(WORD_TYPE);
          copyByLocation(type, location, type, r);
        }
      
        switch(n) {
        case 0:
          sri0 = r;
          break;
        case 1:
          sri1 = r;
          break;
        case 2:
          sri2 = r;
          break;
        }
      } // end moving to registers
    }
    
    //-#if RVM_WITH_QUICK_COMPILER_MOVE_ELIMINATION
//     markStackDead(currentStackHeight-numPoppedFromStack,
//                         numPoppedFromStack);
    //-#endif

    // Get information on the stack state after this opcode is
    // finished:
    
    int finalHeight = modifyStack(stack, currentStackHeight, 
                                  numPoppedFromStack, typePushedOnStack,
                                  stackAfterOpcode);
    assignStackLocations(stackAfterOpcode, finalHeight,
                         stackLocations, stackLocationsAfterOpcode);
    
    returnType = typePushedOnStack;
    //-#if RVM_WITH_QUICK_COMPILER_MOVE_ELIMINATION
    returnValue = VALUE_NOT_SET;
    //-#endif
    
    if (returnType != VOID_TYPE) {
      //-#if RVM_WITH_QUICK_COMPILER_MOVE_ELIMINATION
      // We must make sure that the value currently in the stack slot
      // that will hold the result of this opcode is saved somewhere
      // safe before we clobber it with the callee's return value.
      saveSlotInSafeLocation(stackIndexToSlotIndex(pendingStackHeight),
                      pendingStackHeight);
      //-#endif
      location = getStackLocationAfterOpcode(typePushedOnStack,
                                             pendingStackHeight);
      if (isRegister(location)) {
        sro0 = locationToRegister(location);
        resultIsStackRegister = true;
      }
      else {
        if (needsFloatRegister(typePushedOnStack))
          sro0 = LAST_VOLATILE_FPR;
        else
          sro0 = LAST_VOLATILE_GPR;
        resultIsStackRegister = false;
      }
      returnLocation = location;
      //-#if RVM_WITH_QUICK_COMPILER_MOVE_ELIMINATION
      if (moveToRegisters)
        returnValue =
          recordCalculation(resultIsStackRegister?sro0:-1,
                                  pendingStackHeight,
                                  returnType, biStart);
      //-#endif
    }

    if (!moveToRegisters) 
      sro0 = -1;   // suppress copyByLocation in cleanup routine
      
    pendingStackHeight = finalHeight;

    //-#if RVM_WITH_QUICK_COMPILER_MOVE_ELIMINATION
    
    // All items have been popped off the stack, and any values that
    //should have been stored in a local or live stack entry but had
    //had that write postponed have now been flushed to one of those
    //"safe" slots. So, now we can tell the reference map what
    //locations truly contain Objects.
    
    if (isGCPoint && !GCLocationsSet) {
      addLocationsToRefMap(biStart);
    }
    //-#endif
  }
  
  
  void cleanupRegisters() {
    
    
    currentStackHeight = pendingStackHeight;
    if (returnType != VOID_TYPE) {
      if (sro0 >= 0)
        copyByLocation(returnType, sro0, returnType, returnLocation);
      //-#if RVM_WITH_QUICK_COMPILER_MOVE_ELIMINATION
      int resultIndex =
        currentStackHeight - VM_BuildBB.getJavaStackSize(returnType);
      if (returnValue >= FIRST_VALUE) {
        byte status = STATUS_WRITTEN;
        //-#if RVM_WITH_QUICK_COMPILER_CONSTANT_FOLDING
        if (valueTypes[returnValue] == VALUE_TYPE_CONSTANT)
          status = STATUS_PENDING;
        else
          status = STATUS_WRITTEN;
        //-#endif
          
        updateValue(returnValue, returnType, returnLocation);
        setSlotContents(stackIndexToSlotIndex(resultIndex),
                              returnValue, resultIndex, status);
      }
      //-#endif
    }
    returnType = VOID_TYPE;
    
    byte[] ts = stack;
    stack = stackAfterOpcode;
    stackAfterOpcode = ts;
    
    int[] tl = stackLocations;
    stackLocations = stackLocationsAfterOpcode;
    stackLocationsAfterOpcode = tl;

    releaseTempRegisters();
    
    if (VM.VerifyAssertions) VM._assert(currentStackHeight >= 0 &&
                                        currentStackHeight <= maxStackHeight);
    
  }

  void pop(byte type) {
    currentStackHeight -= VM_BuildBB.getJavaStackSize(type);
    if (VM.VerifyAssertions) VM._assert(currentStackHeight >= 0);
    assignStackLocationsAfterPop();
  }

  void push(byte type) {
    assignStackLocationsBeforePush(type);
    stack[currentStackHeight++] = type;
    if (VM_BuildBB.getJavaStackSize(type) > 1)
      stack[currentStackHeight++] = type;
    if (VM.VerifyAssertions) VM._assert(currentStackHeight <= maxStackHeight);
  }

  void popToRegister(byte type, int register) {
    popToRegister(type, type, register);
  }
  void popToRegister(byte type, byte registerType, int register) {
    currentStackHeight -= VM_BuildBB.getJavaStackSize(type);
    //-#if RVM_WITH_QUICK_COMPILER_MOVE_ELIMINATION
    useStackEntry(currentStackHeight, currentStackHeight, register);
    assignStackLocationsAfterPop();
    //-#else
    int location = getStackLocation(type, currentStackHeight);
    copyByLocation(type, location,
                   registerType, registerToLocation(register));
    assignStackLocationsAfterPop();
    //-#endif 
  }
  int popToAnyRegister(byte type) {
    int r;
    //-#if RVM_WITH_QUICK_COMPILER_MOVE_ELIMINATION
    currentStackHeight -= VM_BuildBB.getJavaStackSize(type);
    r=useStackEntry(currentStackHeight, currentStackHeight);
    assignStackLocationsAfterPop();
    //-#else
    r = getTempRegister(type);
    popToRegister(type, r);
    //-#endif
    
    return r;
  }
  
  void popToMemory(byte type, int offset) {
    currentStackHeight -= VM_BuildBB.getJavaStackSize(type);
    //-#if RVM_WITH_QUICK_COMPILER_MOVE_ELIMINATION
    int register = useStackEntry(currentStackHeight,
                                 currentStackHeight-1);
    //-#else
    copyByLocation(type, registerToLocation(register),
                   type, offsetToLocation(offset));
    assignStackLocationsAfterPop();
    //-#endif
  }
  
  void peekToRegister(byte type, int register) {
    peekToRegister(type, currentStackHeight-1, register);
  }
  
  void peekToRegister(byte type, int peekedHeight, int register) {
    //-#if RVM_WITH_QUICK_COMPILER_MOVE_ELIMINATION
    moveStackEntryToSpecificRegister(peekedHeight,
                                     currentStackHeight,
                                     register);
    //-#else
    int location = getStackLocation(type, peekedHeight);
    copyByLocation(type, location,
                   type, register);
    //-#endif 
  }

  int peekToAnyRegister(byte type, int peekedHeight) {
    //-#if RVM_WITH_QUICK_COMPILER_MOVE_ELIMINATION
    int r = moveStackEntryToRegister(peekedHeight,
                                     currentStackHeight);
    //-#else
    int location = getStackLocation(type, peekedHeight);
    copyByLocation(type, location,
                   type, r);
    //-#endif
    return r;
  }
  
  void peekToMemory(byte type, int peekedHeight, int offset) {
    //-#if RVM_WITH_QUICK_COMPILER_MOVE_ELIMINATION
    int r = moveStackEntryToRegister(peekedHeight, currentStackHeight);
    copyByLocation(type, registerToLocation(r),
                   type, offsetToLocation(offset));
    //-#else
    int location = getStackLocation(type, peekedHeight);
    copyByLocation(type, location,
                   type, offsetToLocation(offset));
    //-#endif
  }
  
  void pushFromRegister(byte type, int register) {
    pushFromRegister(type, type, register);
  }
  void pushFromRegister(byte type, byte registerType, int register) {
    //-#if RVM_WITH_QUICK_COMPILER_MOVE_ELIMINATION
    // XXX TODO: might want a version where the value is passed in!
    recordCalculation(register, currentStackHeight, type, biStart);
    //-#endif
    assignStackLocationsBeforePush(type);
    int location = getStackLocation(type, currentStackHeight);
    copyByLocation(registerType, register,
                   type, location);
    stack[currentStackHeight++] = type;
    if (VM_BuildBB.getJavaStackSize(type) > 1)
      stack[currentStackHeight++] = type;
    if (VM.VerifyAssertions) VM._assert(currentStackHeight >= 0 &&
                                        currentStackHeight <= maxStackHeight);
  }


  /**
   *
   The workhorse routine that is responsible for copying values from
   one slot to another. Every value is in a <i>location</i> that
   represents either a numbered register or an offset from the frame
   pointer (registers are positive numbers and offsets are
   negative). This method will generate register moves, memory stores,
   or memory loads as needed to get the value from its source location
   to its target. This method also understands how to do a few conversions
   from one type of value to another (for instance float to word).
   *
   * @param srcType the type of the source (e.g. <code>WORD_TYPE</code>)
   * @param src the source location
   * @param destType the type of the destination
   * @param dest the destination location
   */
  void copyByLocation(byte srcType, int src,
                      byte destType, int dest) {
    if (src == dest && srcType == destType)
      return;
    
    boolean srcIsRegister = isRegister(src);
    boolean destIsRegister = isRegister(dest);

    if (!srcIsRegister)
      src = locationToOffset(src);
    if (!destIsRegister)
      dest = locationToOffset(dest);
    
    if (srcType == destType) {
      if (srcType == FLOAT_TYPE) {
        if (srcIsRegister)
          if (destIsRegister)
            // register to register move
            asm.emitFMR(dest, src);
          else {
            // register to memory move
            asm.emitSTFS(src, dest, FP);
          }
        else
          if (destIsRegister) {
            // memory to register move
            asm.emitLFS(dest, src, FP);
          }
          else {
            // memory to memory move
            asm.emitLFS(SF0, src, FP);
            asm.emitSTFS(SF0, dest, FP);
          }
      }
      else if (srcType == DOUBLE_TYPE) {
        if (srcIsRegister)
          if (destIsRegister)
            // register to register move
            asm.emitFMR(dest, src);
          else {
            // register to memory move
            asm.emitSTFD(src, dest, FP);
          }
        else
          if (destIsRegister) {
            // memory to register move
            asm.emitLFD(dest, src, FP);
          }
          else {
            // memory to memory move
            asm.emitLFD(SF0, src, FP);
            asm.emitSTFD(SF0, dest, FP);
          }
      }
      else if (needsTwoRegisters(srcType)) {
        if (srcIsRegister)
          if (destIsRegister) {
            // register to register move
            asm.emitMR(dest,   src);
            asm.emitMR(dest+1, src+1);
          }
          else {
            // register to memory move
            asm.emitSTW(src+1  , dest+4, FP);
            asm.emitSTW(src    , dest,   FP);
          }
        else
          if (destIsRegister) {
            
            // memory to register move
            asm.emitLWZ(dest+1,    src+4, FP);
            asm.emitLWZ(dest,      src,   FP);
          }
          else {
            // memory to memory move
            // Trick: use a single, 64-bit wide, floating point
            // register so we do the transfer in half the number of
            // steps we would require if we used a 32-bit wide fixed
            // point register
            
            asm.emitLFD(SF0, src, FP);
            asm.emitSTFD(SF0, dest, FP);
          }
      }
      else {
        // Default, two single-word types. Use the fixed point
        // registers and a single word in memory.
        if (srcIsRegister)
          if (destIsRegister)
            // register to register move
            asm.emitMR(dest, src);
          else {
            // register to memory move
            asm.emitSTW(src, dest, FP);
          }
        else
          if (destIsRegister) {
            // memory to register move
            asm.emitLWZ(dest, src, FP);
          }
          else {
            // memory to memory move
            asm.emitLWZ(S0, src, FP);
            asm.emitSTW(S0, dest, FP);
          }
      }
    } // end if matching types
    else if (needsFloatRegister(srcType) &&
             srcIsRegister &&
             destType == LONG_TYPE) {
      // Trick: doubles are sometimes stored in a single 64-bit wide
      // floating-point register.
      // DOUBLE --> LONG
      if (destIsRegister) {
        asm.emitSTFD (src,    firstScratchOffset, FP); 
        asm.emitLWZ    (dest,   firstScratchOffset, FP);
        asm.emitLWZ    (dest+1, firstScratchOffset+4, FP);
      }
      else {
        asm.emitSTFD(src, dest, FP);
      }
    }
    else if (needsFloatRegister(destType) &&
             srcIsRegister &&
             srcType == LONG_TYPE) {
      // Trick: doubles are sometimes stored in a single 64-bit wide
      // floating-point register.
      // LONG --> DOUBLE
      if (destIsRegister) {
        asm.emitSTW   (src,   firstScratchOffset, FP);
        asm.emitSTW   (src+1, firstScratchOffset+4, FP);
        asm.emitLFD   (dest,  firstScratchOffset, FP); 
      }
      else {
        asm.emitSTW   (src,   dest, FP);
        asm.emitSTW   (src+1, dest+4, FP);
      }
    }
    else if (srcType == DOUBLE_TYPE &&
             srcIsRegister &&
             destType == WORD_TYPE) {
      // Float to word always requires using memory. There is no
      // direct way to move part of a float register to a fixed
      // register.
    // This is a bit-to-bit copy, not a conversion!
      // DOUBLE --> INT
      asm.emitSTFD (src,  firstScratchOffset, FP); 

      if (!destIsRegister) {
        asm.emitLWZ   (S0, firstScratchOffset+4, FP);
        asm.emitSTW (S0,  dest, FP);
      } else {
        asm.emitLWZ(dest, firstScratchOffset+4, FP);
      }
        
    }
    else if (srcType == FLOAT_TYPE &&
             srcIsRegister &&
             destType == WORD_TYPE) {
      // Float to word always requires using memory. There is no
      // direct way to move part of a float register to a fixed
      // register.
    // This is a bit-to-bit copy, not a conversion!
      // DOUBLE --> INT
      asm.emitSTFS (src,  firstScratchOffset, FP); 

      if (!destIsRegister) {
        asm.emitLWZ   (S0, firstScratchOffset, FP);
        asm.emitSTW (S0,  dest, FP);
      } else {
        asm.emitLWZ(dest, firstScratchOffset, FP);
      }
        
    }
    else if (destType == FLOAT_TYPE &&
             srcIsRegister &&
             srcType == WORD_TYPE) {
      // Float to word always requires using memory. There is no
      // direct way to move part of a float register to a fixed
      // register.
      // INT --> FLOAT
      if (destIsRegister) {
        asm.emitSTW   (src,   firstScratchOffset, FP);
        asm.emitLFS   (dest,  firstScratchOffset, FP); 
      }
      else {
        asm.emitSTW   (src,   dest, FP);
      }
    }
    else {
      // CJH what other combinations are allowable??
      
      if (VM.VerifyAssertions) {
        VM.sysWrite("copyByLocation error. src=");
        VM.sysWrite(src);
        VM.sysWrite(", srcType=");
        VM.sysWrite(srcType);
        VM.sysWrite(", dest=");
        VM.sysWrite(dest);
        VM.sysWrite(", destType=");
        VM.sysWrite(destType);
        VM.sysWriteln();
        VM._assert(NOT_REACHED);
      }
    }
      
  }

  /**
   * Utility routine to load a long value from memory.
   *
   * @param destLocation the location of the destination
   * @param offsetRegister the register contain the offset
   * @param baseRegister the register containing the base address
   */
  private void loadLongFromMemory(int destLocation, int offsetRegister,
                                  int baseRegister) {
    if (isRegister(destLocation)) {
      int dest = locationToRegister(destLocation);
      // memory to register move
      // for LUX, RT and RA must be different
      if (dest == offsetRegister) {
        asm.emitMR(S1, offsetRegister);
        offsetRegister = S1;
      }
      asm.emitLWZUX (dest,      offsetRegister,   baseRegister);
      asm.emitLWZ   (dest+1,    4, offsetRegister);
    }
    else {
      // memory to memory move
      // Trick: use a single, 64-bit wide, floating point
      // register so we do the transfer in half the number of
      // steps we would require if we used a 32-bit wide fixed
      // point register
      int dest = locationToOffset(destLocation);
      asm.emitLFDX(SF0, offsetRegister, baseRegister);
      asm.emitSTFD(SF0, dest, FP);
    }
  }
  
  /**
   * Utility routine to write a long value to memory
   *
   * @param srcLocation the location of the source
   * @param offsetRegister the register contain the offset
   * @param baseRegister the register containing the base address
   */
  private void storeLongToMemory(int srcLocation, int offsetRegister,
                                 int baseRegister) {
    if (isRegister(srcLocation)) {
      int src = locationToRegister(srcLocation);
      // register to memory move
      asm.emitSTWUX(src,      offsetRegister,   baseRegister);
      asm.emitSTW  (src+1,    4, offsetRegister);
    }
    else {
      // memory to memory move
      // Trick: use a single, 64-bit wide, floating point
      // register so we do the transfer in half the number of
      // steps we would require if we used a 32-bit wide fixed
      // point register
      int src = locationToOffset(srcLocation);
      asm.emitLFD  (SF0, src, FP);
      asm.emitSTFDX(SF0, offsetRegister, baseRegister);
    }
  }
  
  /** Returns offset of i-th local variable with respect to FP
   */
  private int localOffset (int i)  throws UninterruptiblePragma {
    int offset = firstLocalOffset - (i << 2);
    if (VM.VerifyAssertions) VM._assert(offset < 0x8000);
    return offset;
  }
  
  final static int localOffset (int i, int firstLocalOffset) throws UninterruptiblePragma  {
    int offset = firstLocalOffset - (i << 2);
    if (VM.VerifyAssertions) VM._assert(offset < 0x8000);
    return offset;
  }
  

  final static boolean isRegister(int location)  throws UninterruptiblePragma {
    return location > 0;
  }

  final static int locationToRegister(int location)  throws UninterruptiblePragma {
    return location;
  }

  final static int locationToOffset(int location)  throws UninterruptiblePragma {
    return -location;
  }

  final static int offsetToLocation(int offset)  throws UninterruptiblePragma {
    return -offset;
  }

  final static int registerToLocation(int register)  throws UninterruptiblePragma {
    return register;
  }

  
  final int getStackLocation(byte type, int stackPosition) {
    return stackLocations[stackPosition];
  }

  final int getStackLocationAfterOpcode(byte type, int stackPosition) {
    return stackLocationsAfterOpcode[stackPosition];
  }

  final int getLocalLocation(byte type, int localIndex) {
    int pos;
    switch (type) {
    case FLOAT_TYPE:
      pos = localFloatLocation[localIndex];
      break;
    case DOUBLE_TYPE:
      pos = localDoubleLocation[localIndex];
      break;
    case LONG_TYPE:
      pos = localLongLocation[localIndex];
      break;
    default:
      pos = localWordLocation[localIndex];
      break;
    }
    return pos;
  }

  private final int getConstantLocalLocation(byte type, int localIndex, int local0Offset)  throws UninterruptiblePragma {
    int pos;
    int numFixedLocalRegisters =
      1 + lastFixedLocalRegister - firstFixedLocalRegister;
    int numFloatLocalRegisters =
      1 + lastFloatLocalRegister - firstFloatLocalRegister;
    switch (type) {
    case LONG_TYPE:
      if (localIndex+1 < numFixedLocalRegisters)
        pos = firstFixedLocalRegister+localIndex;
      else
        pos = -localOffset(localIndex+1, local0Offset);
      break;
    case FLOAT_TYPE:
      if (localIndex < numFloatLocalRegisters)
        pos = firstFloatLocalRegister+localIndex;
      else
        pos = -localOffset(localIndex, local0Offset);
      break;
    case DOUBLE_TYPE:
      if (localIndex < numFloatLocalRegisters)
        pos = firstFloatLocalRegister+localIndex;
      else
        pos = -localOffset(localIndex+1, local0Offset);
      break;
    default:
      if (localIndex < numFixedLocalRegisters)
        pos = firstFixedLocalRegister+localIndex;
      else
        pos = -localOffset(localIndex, local0Offset);
      break;
    }
    return pos;
  }

  /**
   * Assigns registers to locals and stack entries. TODO: This should
  be much smarter about assigning registers to locals! For instance,
  doing some analysis of how often a local is used and possibly
  per-block liveness analysis so registers could be assigned to many locals.
   *
   * @param nLocalWords number of words used by locals
   * @param nStackWords number of words used by stack
   */
  void defineStackAndLocalRegisters(int nLocalWords, int nStackWords) {
      if (false) {
        firstFixedStackRegister = FIRST_FIXED_STACK_REGISTER;
        lastFixedStackRegister =  FIRST_FIXED_STACK_REGISTER +
          min(NUM_FIXED_STACK_REGISTERS, nStackWords) - 1;
        firstFloatStackRegister = FIRST_FLOAT_STACK_REGISTER;
        lastFloatStackRegister =  FIRST_FLOAT_STACK_REGISTER +
          min(NUM_FLOAT_STACK_REGISTERS, nStackWords) - 1;

        firstFixedLocalRegister = FIRST_FIXED_LOCAL_REGISTER;
        lastFixedLocalRegister =  FIRST_FIXED_LOCAL_REGISTER +
          min(NUM_FIXED_LOCAL_REGISTERS, nLocalWords) - 1;
        firstFloatLocalRegister = FIRST_FLOAT_LOCAL_REGISTER;
        lastFloatLocalRegister =  FIRST_FLOAT_LOCAL_REGISTER +
          min(NUM_FLOAT_LOCAL_REGISTERS, nLocalWords) - 1;
      } else {
        firstFixedLocalRegister = FIRST_NONVOLATILE_GPR;
        if (nLocalWords == 0 ) {
            // Make sure the first value is greater than last so no
            // for loops will run:
          lastFixedLocalRegister =  firstFixedLocalRegister-1;
        } else if ((nStackWords+nLocalWords) <
                   (1+LAST_NONVOLATILE_GPR-FIRST_NONVOLATILE_GPR)) {
          lastFixedLocalRegister =
            firstFixedLocalRegister + nLocalWords-1;
        } else {
          lastFixedLocalRegister =
            LAST_NONVOLATILE_GPR - min(DEFAULT_FIXED_STACK_REGISTERS,
                                       nStackWords)-1;
        }
        firstFixedStackRegister = lastFixedLocalRegister + 1;
        if (nStackWords == 0 ) {
            // Make sure the first value is greater than last so no
            // for loops will run:
          lastFixedStackRegister =  firstFixedStackRegister-1;
        } else if ((nStackWords+nLocalWords) <
                   (1+LAST_NONVOLATILE_GPR-FIRST_NONVOLATILE_GPR)) {
          lastFixedStackRegister =
            firstFixedStackRegister + nStackWords-1;
        } else {
          lastFixedStackRegister = LAST_NONVOLATILE_GPR;
        }
        
        firstFloatLocalRegister = FIRST_NONVOLATILE_FPR;
        if (nLocalWords == 0 ) {
            // Make sure the first value is greater than last so no
            // for loops will run:
          lastFloatLocalRegister =  firstFloatLocalRegister-1;
        } else if ((nStackWords+nLocalWords) <
                   (1+LAST_NONVOLATILE_FPR-FIRST_NONVOLATILE_FPR)) {
          lastFloatLocalRegister =
            firstFloatLocalRegister + nLocalWords-1;
        } else {
          lastFloatLocalRegister =
            LAST_NONVOLATILE_FPR - min(DEFAULT_FLOAT_STACK_REGISTERS,
                                       nStackWords)-1;
        }
        firstFloatStackRegister = lastFloatLocalRegister + 1;
        if (nStackWords == 0 ) {
            // Make sure the first value is greater than last so no
            // for loops will run:
          lastFloatStackRegister =  firstFloatStackRegister-1;
        } else if ((nStackWords+nLocalWords) <
                   (1+LAST_NONVOLATILE_FPR-FIRST_NONVOLATILE_FPR)) {
          lastFloatStackRegister =
            firstFloatStackRegister + nStackWords-1;
        } else {
          lastFloatStackRegister = LAST_NONVOLATILE_FPR;
        }
        
      }
      
    }
  
  /**
   * Assigns registers that can be used to hold temporary
  values. Unfortunately, currently this is a fixed set regardless of
  the method.
   *
   * @param nLocalWords number of words used by locals
   * @param nStackWords number of words used by stack
   */
  private static void defineTempRegisters(int nStackWords, int nLocalWords) {
    if (tempGPRs != null)
      return;
    
    tempGPRs = new int[32];
    tempFPRs = new int[32];
    
    // I would love to use any NON_VOLATILES left over after assigning
    // to stack and locals as temporaries, but then we'd have to save
    // and restore them in our prologue/epilogue, and at that point we
    // don't know how many temporaries are actually needed by this
    // method.

    // We allow the upper OS parameter GPRs to be used for a pool of
    // temporaries, but reserve the lower four (r3-r6) for use of the
    // little internal function calls some bytecodes use:
    numTempGPRs = 0;
    
    for (int i=0; i < 5; i++)
      tempGPRs[numTempGPRs++]= LAST_OS_PARAMETER_GPR - i;
      
    numTempFPRs = 0;

    for (int i=0; i < 5; i++)
      tempFPRs[numTempFPRs++]= LAST_OS_PARAMETER_FPR - i;

  }

  /**
   * Gets a register that can be used as a scratch register. This
  method works in one of two modes. A bytecode may need to grab
  several different scratch registers that all need to be live at
  once, or it may need only a single temporary at a time. There are
  only a few scratch registers available, so if a routine continually
  asks for a temporary and gets a different one back each time we may
  run out of temporaries.  Unfortunately, the method requesting a
  temporary may not know whether the temporary will be needed once
  it's done. Therefore it is a caller's responsibility to set the
  instance variable <code>paramRegsInUse</code> to determine this.
   *
   * @param type The type of the registers
   * @return The register's number
   */
  private static final int getTempRegister(byte type) {
    // In general, we want to use a pool of temporary registers we can
    // dip into while compiling a bytecode. The OS parameter registers
    // make a convenient pool. The problem is that we may need a
    // temporary in the routine that moves stack entries to parameter
    // registers, hence that pool is unavailable. But during that
    // situation we know we only need a single temporary, and only
    // long enough to move a stack entry out of memory and into the
    // spill area for the parameters. So we can use one of the
    // standard scratch registers.
    if (paramRegsInUse){
      if (needsFloatRegister(type))
        return SF0;
      else
        return S0;      
    }
      
    if (needsFloatRegister(type)) {
      if (VM.VerifyAssertions) VM._assert(tempFPRIndex < numTempFPRs);
      return tempFPRs[tempFPRIndex++];
    } else if (type != LONG_TYPE) {
      if (VM.VerifyAssertions) VM._assert(tempGPRIndex < numTempGPRs);
      return tempGPRs[tempGPRIndex++];
    }
    else {
      // Find two consecutive GPR's and return the lower-valued one
      while (tempGPRs[tempGPRIndex] != 1+tempGPRs[tempGPRIndex+1] &&
             tempGPRIndex <= numTempGPRs-2) {
        tempGPRIndex += 1;
      }
      if (VM.VerifyAssertions) VM._assert(tempGPRIndex < numTempGPRs-1);
      tempGPRIndex += 1;
      return tempGPRs[tempGPRIndex++];
    }
  }

  /**
   * Resets temporary register pool so all scratch registers are
  available for use again.
   *
   */
  private static final void releaseTempRegisters() {
    tempGPRIndex = 0;
    tempFPRIndex = 0;
  }
    

  /**
   * Calculate the initial locations (register or memory offset) for each type
  of slot (local or stack) and type. For efficiency, these are stored
  in arrays rather than calculated every bytecode. Note that the
  locations depend on the type as, for example, there are more float
  registers available for our use than integer registers, and a long
  cannot straddle a register and memory location.
   *
   * @param numLocal number of local words used by the method
   * @param numStack number of stack words used by the method
   */
  private void initializeLocations(int numLocal, int numStack) {
    int i;
    if (localWordLocation == null ||
        localWordLocation.length < numLocal) {
      localWordLocation = new int[numLocal];
      localLongLocation = new int[numLocal];
      localFloatLocation = new int[numLocal];
      localDoubleLocation = new int[numLocal];
    }
      
    if (stackWordLocation == null ||
        stackWordLocation.length < numStack) {
      stackWordLocation = new int[numStack];
      stackLongLocation = new int[numStack];
      stackFloatLocation = new int[numStack];
      stackDoubleLocation = new int[numStack];
    }
    
    for (i=0; i<numLocal; i++) {
      localWordLocation[i] = getConstantLocalLocation(WORD_TYPE, i,
                                                    firstLocalOffset);
      localFloatLocation[i] = getConstantLocalLocation(FLOAT_TYPE, i,
                                                     firstLocalOffset);
      localLongLocation[i] = getConstantLocalLocation(LONG_TYPE, i,
                                                    firstLocalOffset);
      localDoubleLocation[i] = getConstantLocalLocation(DOUBLE_TYPE, i,
                                                     firstLocalOffset);
    }
  }

  /**
   * Update the locations (register or memory offset) for stack entry.
   Only the part of the stack that has changed should need to have its
  locations updated, so we reuse the lower part of the stack.
   *
   */
  private void assignStackLocations() {
    assignStackLocations(stack, currentStackHeight,
                         stackLocations, stackLocations);
  }
  
  /**
   * Update the locations (register or memory offset) for stack entry.
   Only the part of the stack that has changed should need to have its
   locations updated, so we reuse the lower part of the stack.
   *
   * @param theStack the new stack array
   * @param stackHeight the height of the stack
   * @param oldStackLocations an array of the old new locations
   * @param newStackLocations an array to fill in with new locations
   */
  private void assignStackLocations(byte[] theStack, int stackHeight,
                                    int[] oldStackLocations,
                                    int[] newStackLocations) {

    if (oldStackLocations != null && oldStackLocations != newStackLocations)
      System.arraycopy(oldStackLocations, stackHeight,
                       newStackLocations, stackHeight,
                       nStackWords-stackHeight);

    nextFixedStackRegister=firstFixedStackRegister;
    nextFloatStackRegister=firstFloatStackRegister;
    for (int stackPosition=0;
         stackPosition < stackHeight;
         stackPosition++) {
      byte type = theStack[stackPosition];
      switch (type) {
      case LONG_TYPE:
        if (nextFixedStackRegister+1 <= lastFixedStackRegister)
          newStackLocations[stackPosition] = nextFixedStackRegister;
        else
          newStackLocations[stackPosition] = -(stackBottomOffset-((stackPosition+1)<<2));
        stackPosition += 1;
        nextFixedStackRegister += 2;
        break;
      case FLOAT_TYPE:
        if (nextFloatStackRegister <= lastFloatStackRegister)
          newStackLocations[stackPosition] = nextFloatStackRegister;
        else
          newStackLocations[stackPosition] = -(stackBottomOffset-(stackPosition<<2));
        nextFloatStackRegister += 1;
        break;
      case DOUBLE_TYPE:
        if (nextFloatStackRegister <= lastFloatStackRegister)
          newStackLocations[stackPosition] = nextFloatStackRegister;
        else
          newStackLocations[stackPosition] = -(stackBottomOffset-((stackPosition+1)<<2));
        stackPosition += 1;
        nextFloatStackRegister += 1;
        break;
      default:
        if (nextFixedStackRegister <= lastFixedStackRegister)
          newStackLocations[stackPosition] = nextFixedStackRegister;
        else
          newStackLocations[stackPosition] = -(stackBottomOffset-(stackPosition<<2));
        nextFixedStackRegister += 1;
        break;
      }
    }
  }
  
  /**
   * Update the locations (register or memory offset) for stack entry.
   This is a customized version of <code>assignStackLocations</code>
   for use before a push.
   *
   * @param pushedType the type pushed on the stack
   */
  private void assignStackLocationsBeforePush(byte pushedType) {
    switch (pushedType) {
    case LONG_TYPE:
      if (nextFixedStackRegister+1 <= lastFixedStackRegister)
        stackLocations[currentStackHeight] = nextFixedStackRegister;
      else
        stackLocations[currentStackHeight] = -(stackBottomOffset-((currentStackHeight+1)<<2));
      nextFixedStackRegister += 2;
      break;
    case FLOAT_TYPE:
      if (nextFloatStackRegister <= lastFloatStackRegister)
        stackLocations[currentStackHeight] = nextFloatStackRegister;
      else
        stackLocations[currentStackHeight] = -(stackBottomOffset-(currentStackHeight<<2));
      nextFloatStackRegister += 1;
      break;
    case DOUBLE_TYPE:
      if (nextFloatStackRegister <= lastFloatStackRegister)
        stackLocations[currentStackHeight] = nextFloatStackRegister;
      else
        stackLocations[currentStackHeight] = -(stackBottomOffset-((currentStackHeight+1)<<2));
      nextFloatStackRegister += 1;
      break;
    default:
      if (nextFixedStackRegister <= lastFixedStackRegister)
        stackLocations[currentStackHeight] = nextFixedStackRegister;
      else
        stackLocations[currentStackHeight] = -(stackBottomOffset-(currentStackHeight<<2));
      nextFixedStackRegister += 1;
      break;
    }
  }
  
  /**
   * Update the locations (register or memory offset) for stack entry.
   This is a customized version of <code>assignStackLocations</code>
   for use after a push.
   *
   * @param pushedType the type pushed on the stack
   */
  private void assignStackLocationsAfterPop() {
    byte poppedType = stack[currentStackHeight];
    switch (poppedType) {
    case LONG_TYPE:
      nextFixedStackRegister -= 2;
      break;
    case FLOAT_TYPE:
    case DOUBLE_TYPE:
      nextFloatStackRegister -= 1;
      break;
    default:
      nextFixedStackRegister -= 1;
      break;
    }
  }

  
  /**
   * Modifies the stack based on what is popped off and pushed
  on. Does not actually generate code.
   *
   * @param oldStack original stack array
   * @param oldStackHeight height of original stack array
   * @param numPoppedFromStack number of entries popped off stack
   * @param pushedType the type pushed on the stack (or VOID_TYPE)
   * @param newStack array to hold the new stack entries
   * @return the new stack height
   */
  private int  modifyStack(byte[] oldStack, int oldStackHeight,
                           int numPoppedFromStack, byte pushedType,
                           byte[] newStack) {
    System.arraycopy(oldStack, 0, newStack, 0, oldStackHeight);
    int newStackHeight=oldStackHeight;
    for (int i=0; i < numPoppedFromStack; i++)
      newStackHeight -=
        VM_BuildBB.getJavaStackSize(oldStack[newStackHeight-1]);
    
    if (pushedType != VOID_TYPE) {
      newStack[newStackHeight++] = pushedType;
      if (VM_BuildBB.getJavaStackSize(pushedType) > 1)
        newStack[newStackHeight++] = pushedType;
    }
    return newStackHeight;
  }
  
  /**
   * Modifies the stack based on a description of how the current
  bytecode affects it.
  on. Does not actually generate code.
  * @see handleStackRearrangement
   *
   * @param oldStack original stack array
   * @param oldStackHeight height of original stack array
   * @param copyInstructions an array describing the copies
   * @param newStack array to hold the new stack entries
   * @return the new stack height
   */
  private int  modifyStack(byte[] oldStack, int oldStackHeight,
                           int[] copyInstructions,
                           byte[] newStack) {
    System.arraycopy(oldStack, 0, newStack, 0, oldStackHeight);
    int maxOffset = 0;
    int lastStackSlot = oldStackHeight - 1;
    int len = copyInstructions.length;
    
    for (int i=0; i < len; i += 2) {
      int offset =  copyInstructions[i+1];
      byte type = newStack[lastStackSlot + copyInstructions[i]];
      newStack[lastStackSlot + offset] = type;
      if (offset > maxOffset)
        maxOffset = offset;
    }
    return oldStackHeight+maxOffset;
  }
  
  
  /**
   * Returns the minumum of two ints. We don't want to use Math.min as
  it causes problems when put in the primordials
   *
   * @param x the first value
   * @param y the second value
   * @return the minumum of <code>x</code> and <code>y</code>
   * @exception UninterruptiblePragma if an error occurs
   */
  public final static int min(int x, int y)  throws UninterruptiblePragma {
    if (x < y)
      return x;
    else
      return y;
  }

  /**
   * Returns the maximum of two ints. We don't want to use Math.max as
  it causes problems when put in the primordials
   *
   * @param x the first value
   * @param y the second value
   * @return the maximum of <code>x</code> and <code>y</code>
   * @exception UninterruptiblePragma if an error occurs
   */
  public final static int max(int x, int y)  throws UninterruptiblePragma {
    if (x > y)
      return x;
    else
      return y;
  }

  /**
   * Returns a count of the number of ones in the binary representation
  of a long value.
   *
   * @param l a <code>long</code> value
   * @return the count
   */
  private final static int digitCount(long l) {
    l = ((l & 0x5555555555555555L) + ((l >> (1 << 0)) & 0x5555555555555555L));
    l = ((l & 0x3333333333333333L) + ((l >> (1 << 1)) & 0x3333333333333333L));
    l = ((l & 0x0F0F0F0F0F0F0F0FL) + ((l >> (1 << 2)) & 0x0F0F0F0F0F0F0F0FL));
    l = ((l & 0x00FF00FF00FF00FFL) + ((l >> (1 << 3)) & 0x00FF00FF00FF00FFL));
    l = ((l & 0x0000FFFF0000FFFFL) + ((l >> (1 << 4)) & 0x0000FFFF0000FFFFL));
    l = ((l & 0x00000000FFFFFFFFL) + ((l >> (1 << 5)) & 0x00000000FFFFFFFFL));
    return (int)l;
  }
  
  /**
   * Finds the lowest order bit in the binary representation of a long
   * that contains a one.
   *
   * @param l a <code>long</code> value
   * @param start_index where to start looking
   * @param end_index where to stop looking
   * @return index of lowest order 1, or -1 if all bits are 0.
   */
  private final static int indexOfLowestBit(long l, int start_index, int end_index) {

    if (start_index > 0)
      l =  l & (-1L << start_index);

    // We deal mostly with sparely populated bit masks, so it's very
    // likely the argument will be zero.
    if (l == 0L)
      return -1;

    do {
      int pivot = (start_index+end_index)/2;
      long mask = (1L << (pivot+1))-1L;
      if ((l & mask) == 0)
        start_index = pivot+1;  // bit must be left of 'pivot'
      else
        end_index = pivot;      // bit is btw pivot and end_index (inclusive)
    } while (end_index > start_index);
    return start_index;
  }
  

  private final static boolean needsFloatRegister(byte type) {
    return 0 != (type & FLOATING_POINT_);
  }
  
  private final static boolean needsTwoRegisters(byte type) {
    return 0 != (type & TWO_REGISTERS_);
  }
  
  private final static boolean isReferenceType(byte type) {
    return 0 != (type & OBJECT_);
  }

  //-#if RVM_WITH_QUICK_COMPILER_MOVE_ELIMINATION

  // One of the goals was to reduce the amount of memory allocation
  // during code generation. Thus, the information about the values,
  // their status, and where they are stored, is kept in static arrays
  // and often depends on bit masks and such. That makes these
  // routines even more ugly than they are by nature. A very
  // interesting experiment would be to rewrite these routines in an
  // object-oriented way so that there are actual value objects and
  // see if there is truly any significant impact on performance
  // during code generation.

  // ------------------------------------------------------------
  // Initialization
  //

  /**
   * Initialize move elimination subsystem.
   *
   */
  private void moveElimInit() {
    int length = nStackWords+nLocalWords;
    if (slotStatus == null ||
        slotStatus.length < length) {
      slotContents = new int[length+10];
      slotStatus = new byte[length];
    }
    
    // This is a very conservative estimate of total number of values
    // that could be generated in this method: allow for two values
    // per bytecode plus a little extra!
    int maxValues = 10 + 2 *byteToBlockMap.length;
    if (valueTypes == null || valueTypes.length < maxValues) {
      valueJavaTypes = new byte[maxValues];
      valueTypes = new byte[maxValues];
      valuePendingToSlots = new long[maxValues];
      valueCompletedToSlots = new long[maxValues];
      valueContents = new int[maxValues];
      valueSetAtBCI = new int[maxValues];
      valueSetInRegister = new int[maxValues];
      //valueSetType = new byte[maxValues];
      valueSetAtMCI = new int[maxValues];
    }

    for (int i=0; i < nStackWords+nLocalWords; i++){
      slotContents[i] = VALUE_NOT_SET;
      slotStatus[i] = STATUS_WRITTEN;
    }
    for (int i=0; i < valuePendingToSlots.length; i++){
      valuePendingToSlots[i] = 0L;
      valueCompletedToSlots[i] = 0L;
    }
    
  }

  /**
   * Record move elimination information at the start of a basic block.
   *
   */
  private void moveElimStartBlock() {
    // Load the stack and locals with dummy values since we can't
    // assume anything about their contents (other than the types of
    // the Java objects currently on the stack).

    currentValue = FIRST_VALUE;

    int theValue;

    for (int i=0; i < nStackWords+nLocalWords; i++){
      int stackIndex = slotIndexToStackIndex(i);
      if (stackIndex >=0 && stackIndex < currentStackHeight) {
        theValue = createValue(i,
                                     VALUE_TYPE_UNKNOWN,
                                     stack[stackIndex],
                                     getStackLocation(stack[stackIndex],
                                                      stackIndex));
      }
      else {
        theValue = createValue(i,
                                     VALUE_TYPE_UNKNOWN,
                                     VOID_TYPE,
                                     -1);
      }

      slotContents[i] = VALUE_NOT_SET; // old value is irrelevant
      setSlotContents(i, theValue,
                            currentStackHeight, STATUS_WRITTEN);
    }
  }

  /**
   * Record move elimination information at the end of a basic block.
   *
   * @param stackHeight the amount of the stack currently live.
   */
  private void moveElimFinishBlock(int stackHeight) {
    int i;
    paramRegsInUse = true;
    for (i=0; i < nStackWords+nLocalWords; i++) {
      if (slotStatus[i] == STATUS_PENDING) {
        flushSlot(i, stackHeight);
      }
    }
    paramRegsInUse = false;
  }


  /**
   * Create a new value.
   *
   * @param location the register or address containing value
   * (currently unused).
   * @param type the kind of value (e.g. constant, calculation)
   * @param javaType what Java type the value is (e.g. long, int)
   * @param contents what the value represents (e.g. value of a
   * constant value).
   * @return ID of value.
   */
  private int createValue(int location, byte type, byte javaType,
                                int contents) {
    // location currently unused
    valueSetAtBCI[currentValue] = -1;
    valueTypes[currentValue] = type;
    valueContents[currentValue] = contents;
    valueJavaTypes[currentValue] = javaType;
    valuePendingToSlots[currentValue] = 0L;
    valueCompletedToSlots[currentValue] = 0L;
    return currentValue++;
  }

  /**
   * Create a new value.
   *
   * @param type the kind of value (e.g. constant, calculation)
   * @param contents what the value represents (e.g. value of a
   * constant value).
   * @return ID of value.
   */
  private int createValue(byte type, int contents) {
    valueSetAtBCI[currentValue] = -1;
    valueTypes[currentValue] = type;
    valueContents[currentValue] = contents;
    valueJavaTypes[currentValue] = VOID_TYPE;
    valuePendingToSlots[currentValue] = 0L;
    valueCompletedToSlots[currentValue] = 0L;
    return currentValue++;
  }

  /**
   * Change the javaType of a value
   *
   * @param theValue the value's ID
   * @param javaType what Java type the value is (e.g. long, int)
   * @param location the register or address containing value
   * (currently unused).
   * @return ID of value.
   */
  private void updateValue(int theValue, byte javaType,
                                int location) {
    //valueSetAtBCI[theValue] = location;
    valueJavaTypes[theValue] = javaType;
  }

   // ------------------------------------------------------------
  // Recording stack activities
  //

  /**
   * Record a java load opcode.
   *
   * @param srcLocal the number of the local
   * @param destStackIndex the stack index it will be loaded to
   * @param javaType the java type of the local 
   * @param bcIndex the bytecode index where the load occurred.
   * @return the ID of the value created.
   */
  private int recordLoad(int srcLocal, int destStackIndex,
                               byte javaType, int bcIndex) {
    srcLocal = localIndexToSlotIndex(srcLocal);
    int destStack = stackIndexToSlotIndex(destStackIndex);
    
    int theValue = slotContents[srcLocal];
    int oldValue = slotContents[destStack];
    
    // A frequent Java idiom is a store and immediate reload, e.g.:
    //   istore 1
    //   iload 1
    // In this case we do not need to reassign the current
    // stack slot. If it already had the value written to it, it still
    // has that value written to it after this sequenece of bytecodes.
    if (false && theValue == oldValue) {
      
      setSlotStatus(destStack, STATUS_PENDING);
    } else {

      setSlotContents(destStack, theValue,
                            destStackIndex, STATUS_PENDING);
    }
    
    valueJavaTypes[theValue] = javaType;
    return theValue;
  }

  /**
   * Record a java store opcode.
   *
   * @param srcStackIndex the stack index containing the value
   * @param destLocal the number of the local to store to
   * @param bcIndex the bytecode index where thestore occurred.
   * @return the ID of the value created.
   */
  private int recordStore(int srcStackIndex, int destLocal,
                                byte javaType) {
    destLocal = localIndexToSlotIndex(destLocal);
    int srcStack = stackIndexToSlotIndex(srcStackIndex);
    
    int theValue = slotContents[srcStack];
    int oldValue = slotContents[destLocal];
    
    valueJavaTypes[theValue] = javaType;

    if (allowRetargeting &&
        valueTypes[theValue] == VALUE_TYPE_CALCULATION &&
        valueSetAtMCI[theValue] >= 0 &&
        isRegister(getLocalLocation(javaType, destLocal)) &&
        numberOfCompletedWrites(theValue) == 1 &&
        lastGCIndex <= valueSetAtBCI[theValue]) {
      if (asm.retargetInstruction(valueSetAtMCI[theValue],
                                  locationToRegister(getLocalLocation(javaType, destLocal)))) {
        valueRetargeted(theValue);
        setSlotContents(destLocal, theValue, srcStackIndex,
                              STATUS_WRITTEN);
        return theValue;
      }
    }
    
    if (theValue != oldValue)
      setSlotContents(destLocal, theValue, srcStackIndex,
                            STATUS_PENDING);
    return theValue;
  }

  /**
   * Record the creation of an integer constant value (e.g. by an sipush opcode).
   *
   * @param c the value of the constant
   * @param destStackIndex the stack index to get the value
   * @param bcIndex the bytecode index where thestore occurred.
   * @return the ID of the value created.
   */
 private int recordConstant(int c, int destStackIndex, int bcIndex) {
    int theValue = createValue(getStackLocation(WORD_TYPE, destStackIndex),
                                     VALUE_TYPE_CONSTANT,
                                     WORD_TYPE,
                                     c);
    
    int destStack = stackIndexToSlotIndex(destStackIndex);
    int oldValue = slotContents[destStack];
    
    setSlotContents(destStack, theValue,
                          destStackIndex, STATUS_PENDING);

    return theValue;
  }
  

  /**
   * Record the creation of a calculated value (e.g. by an imul
  opcode).
  * The machine code that generates a calculated value might be
  rewritten later to target the result to a register other than a
  stack register.
   *
   * @param srcReg the register containing the value
   * @param destStackIndex the stack index to of the value
   * @param javaType the java type of the value
   * @param bcIndex the bytecode index where thestore occurred.
   * @return the ID of the value created.
   */
  private int recordCalculation(int srcReg, int destStackIndex,
                                      byte javaType,
                                      int bcIndex) {
    int theValue = createValue(getStackLocation(javaType, destStackIndex),
                                     VALUE_TYPE_CALCULATION,
                                     javaType,
                                     0);

    int destStack = stackIndexToSlotIndex(destStackIndex);
    int oldValue = slotContents[destStack];

    setSlotContents(destStack, theValue,
                          destStackIndex, STATUS_WRITTEN);
    
    valueSetInRegister[theValue] = srcReg;
    valueSetAtMCI[theValue] = -1;
    valueSetAtBCI[theValue] = bcIndex;
    
    return theValue;
  }

  /**
   * Mark that a value came from a machine code instruction that could
  be retargeted to avoid the extra move from a stack register to a
  parameter or local variable register.
   *
   * @param theValue the ID of the value
   * @param mcIndex the index of the machine code that set this value.
   */
  private void setRetargetable(int theValue, int mcIndex) {
    valueSetAtMCI[theValue] = mcIndex;
  }

  /**
   * Mark the part of the stack that is no longer alive. Values that
  are stored only in the dead part of the stack are not yet written to
  safe locations since the register will not be modified.
   *
   */
  private void markStackDead() {
    
    int last = nStackWords + nLocalWords;
    for (int slot = stackIndexToSlotIndex(currentStackHeight);
         slot < last;
         slot++) {
      if (slotStatus[slot] == STATUS_DEAD)
        break;                  // have met previously killed area
      setSlotStatus(slot, STATUS_DEAD);
    }
  }
  
  /**
   * Clear out the dead part of the stack and force any postponed
  moves of values in that part.
   *
   */
  private void invalidateDeadStackValues() {
    paramRegsInUse = true;
    int slot = stackIndexToSlotIndex(currentStackHeight);
    for (int i=currentStackHeight; i < nStackWords; i++, slot++) {
      setSlotContents(slot, VALUE_NOT_SET, currentStackHeight,
                            STATUS_WRITTEN);
    }

    paramRegsInUse = false;
  }

  /**
   * Mark that a value in a stack slot has been read and get it into a
  register. This stack entry is now dead, and its value might need to
  be written to other locations.
   *
   * @param entry the stack index being used
   * @param liveStackHeight how much of the stack will be live after
   * all entries are popped from the stack
   * @return the register containing the value.
   */
  private final int useStackEntry(int entry, int liveStackHeight) {
    entry = stackIndexToSlotIndex(entry);
    int theValue = slotContents[entry];
    moveValueToAnyRegister(registerDescription, theValue, liveStackHeight);
    if (isGCPoint) {
      saveSlotInSafeLocation(entry, liveStackHeight);
    }
    return registerDescription.register;
  }

  /**
   * Mark that a value in a stack slot has been read and get it into a
  specific register. This stack entry is now dead, and its value might need to
  be written to other locations.
   *
   * @param entry the stack index being used
   * @param liveStackHeight how much of the stack will be live after
   * all entries are popped from the stack
   * @param register the register that should contain the value.
   */
  private final void useStackEntry(int entry, int liveStackHeight, int register) {
    moveStackEntryToSpecificRegister(entry, liveStackHeight, register);
    if (isGCPoint) {
      saveSlotInSafeLocation( stackIndexToSlotIndex(entry), liveStackHeight);
    }
  }

  
  // ------------------------------------------------------------
  // Implementation
  // ------------------------------------------------------------



  // We consider all the stack entries and slot entries to elements in
  // a single array of slots. First are all the locals used by the
  // method, then the stack (from bottom of stack to highest).
  
  /**
   * Convert a Java stack index to a slot index. 
   * We consider all the stack entries and slot entries to elements in
   * a single array of slots. First are all the locals used by the
   * method, then the stack (from bottom of stack to highest).
   *
   * @param index stack index
   * @return slot index
   */
  private final int stackIndexToSlotIndex(int index) {
    return index + nLocalWords;
  }
  
  /**
   * Convert a Java local variable  index to a slot index. 
   * We consider all the stack entries and slot entries to elements in
   * a single array of slots. First are all the locals used by the
   * method, then the stack (from bottom of stack to highest).
   *
   * @param index local variable index
   * @return slot index
   */
  private final int localIndexToSlotIndex(int index) {
    return index;
  }

  /**
   * Convert a slot index to a Java stack index
   * We consider all the stack entries and slot entries to elements in
   * a single array of slots. First are all the locals used by the
   * method, then the stack (from bottom of stack to highest).
   *
   * @param index slot index
   * @return stack index
   */
  private final  int slotIndexToStackIndex(int index) {
    return index - nLocalWords;
  }
  
  /**
   * Convert a slot index to a Java local variable index.
   * We consider all the stack entries and slot entries to elements in
   * a single array of slots. First are all the locals used by the
   * method, then the stack (from bottom of stack to highest).
   *
   * @param index slot index
   * @return local variable index
   */
  private final int slotIndexToLocalIndex(int index) {
    if (index < nLocalWords)
      return index;
    else
      return -1;
  }


  /* These must be called when the stack array and location array
   * contain the stack state at the start of the opcode. Therefore
  these routines cannot record any state about the condition after the
  opcode (no pushing result values to their final locations, and so on).
   */
  /**
   * Set the contents of a slot to a value.
   *
   * @param slot the slot
   * @param value the value
   * @param liveStackHeight amount of stack still live
   * @param status status of slot (e.g. pending, written)
   */
  private final void setSlotContents(int slot, int value,
                                           int liveStackHeight, 
                                           byte status) {
    setSlotContents(slot, value, liveStackHeight, status, true);
  }
  
  /**
   * Set the contents of a slot to a value.
   *
   * @param slot the slot
   * @param value the value
   * @param liveStackHeight amount of stack still live
   * @param status status of slot (e.g. pending, written)
   * @param doSpill if true, store the previous value of the slot in a
   *safe location if it's still needed.
   */
  private final void setSlotContents(int slot, int value,
                                           int liveStackHeight, 
                                           byte status,
                                           boolean doSpill) {
    int oldValue = slotContents[slot];
    long setmask = (0x1L<<slot);
    long clearmask;
    if (VM_BuildBB.getJavaStackSize(valueJavaTypes[oldValue]) > 1)
      clearmask = ~(0x3L << slot);
    else
      clearmask = ~(0x1L << slot);

  
    
    valuePendingToSlots[oldValue] &= clearmask;


    if (doSpill) {
      // If there are slots that need this value and the current slot is
      // the only slot containing the value, we must flush that value
      // out now before we overwrite this slot.
      
      if (onlyPendingWrites(oldValue, slot, liveStackHeight)) {
        saveValueInSafeLocation(oldValue, slot, liveStackHeight);
      }
    }

    valueCompletedToSlots[oldValue] &= clearmask;

    if (VM_BuildBB.getJavaStackSize(valueJavaTypes[value]) > 1)
      clearmask = ~(0x3L << slot);
    else
      clearmask = ~(0x1L << slot);

    if (status == STATUS_PENDING) {
      valuePendingToSlots[value] |= setmask;
      valueCompletedToSlots[value] &= clearmask;
    } else if (status == STATUS_WRITTEN) {
      valueCompletedToSlots[value] |= setmask;
      valuePendingToSlots[value] &= clearmask;
    } else {
      if (VM.VerifyAssertions && doSpill) {
        VM._assert(NOT_REACHED);
      }
    }

    
    slotStatus[slot] = status;
    slotContents[slot] = value;
  }

  /**
   * A specialized version of <code>setSlotContents</code> that swaps
  the contents of two slots.
   *
   * @param slot1 first slot
   * @param slot2 second slot
   */
  private final void swapSlotContents(int slot1, int slot2) {
    int v1 = slotContents[slot1];
    int v2 = slotContents[slot2];
    byte s1 = slotStatus[slot1];
    byte s2 = slotStatus[slot2];

    long setmask;
    long clearmask;

    setmask = 1L << slot1;
    if (VM_BuildBB.getJavaStackSize(valueJavaTypes[v1]) > 1)
      clearmask = ~(0x3L << slot1);
    else
      clearmask = ~(0x1L << slot1);

    // remove slot 1 from v1's locations
    valuePendingToSlots[v1] &= clearmask;
    valueCompletedToSlots[v1] &= clearmask;
    // and add it to v2's locations
    valuePendingToSlots[v2] |= setmask;
    valueCompletedToSlots[v2] |= setmask;
    
    setmask = 1 << slot2;
    if (VM_BuildBB.getJavaStackSize(valueJavaTypes[v2]) > 1)
      clearmask = ~(0x3L << slot2);
    else
      clearmask = ~(0x1L << slot2);

    // remove slot 2 from v2's locations
    valuePendingToSlots[v2] &= clearmask;
    valueCompletedToSlots[v2] &= clearmask;
    // and add it to v1's locations
    valuePendingToSlots[v1] |= setmask;
    valueCompletedToSlots[v1] |= setmask;
    
    slotStatus[slot1] = s2;
    slotContents[slot1] = v2;
    slotStatus[slot2] = s1;
    slotContents[slot2] = v1;

  }

  /**
   * Set status of a slot
   *
   * @param slot the slot
   * @param status the status (e.g. STATUS_PENDING)
   */
  private void setSlotStatus(int slot, byte status)
  {
    slotStatus[slot] = status;
    int value = slotContents[slot];
    long setmask = (0x1L<<slot);
    long clearmask;
    if (VM_BuildBB.getJavaStackSize(valueJavaTypes[value]) > 1)
      clearmask = ~(0x3L << slot);
    else
      clearmask = ~(0x1L << slot);
    
    if (status == STATUS_PENDING) {
      valuePendingToSlots[value] |= setmask;
      valueCompletedToSlots[value] &= clearmask;
    } else if (status == STATUS_WRITTEN) {
      valueCompletedToSlots[value] |= setmask;
      valuePendingToSlots[value] &= clearmask;
    } else {
      // STATUS_DEAD. We no longer need to force this slot to be
      // written, but if it *had* a value written to it already, it
      // will keep that value available for use until the slot has a
      // new value set.
      valuePendingToSlots[value] &= clearmask;
    }
  }

  /**
   * Called only after a machine code has been rewritten to
   * change the target register from a stack register to a local or
   * parameter register.
   * @param value the value
   */
  private final void valueRetargeted(int value) {

    int currentSlot = getNextCompletedWrite(value, -1);
    slotContents[currentSlot] = VALUE_NOT_SET;
    slotStatus[currentSlot] = STATUS_DEAD;
    valueCompletedToSlots[value] = 0L;
    valueSetAtMCI[value] = -1;
    
  }

  private final boolean anyCompletedWrites(int value) {
    return valueCompletedToSlots[value] != 0L;
  }

  private final int numberOfCompletedWrites(int value) {
    int count = 0;
    long mask = valueCompletedToSlots[value];
    int limit = nLocalWords+nStackWords;
    return digitCount(mask & ((1L << limit)-1));
  }

  private final boolean anyPendingWrites(int value) {
    return valuePendingToSlots[value] != 0L;
  }

  private final boolean anyPendingWrites(int value,
                                               int liveStackHeight) {
    long mask = (1L << (nLocalWords+liveStackHeight)) - 1;
    return (mask & valuePendingToSlots[value]) != 0L;
  }

  private final boolean onlyPendingWrites(int value,
                                                int slotBeingWritten,
                                                int liveStackHeight) {
    long mask = ((1L << (nLocalWords+liveStackHeight)) - 1 &
                 ~(1L << slotBeingWritten));
    return ((mask & valuePendingToSlots[value]) != 0L &&
            (mask & valueCompletedToSlots[value]) == 0L);
    
  }

  /**
   * When called repeatedly, <code>getNextPendingWrite</code>
  will iterate through the slots containing <code>value</code> and
  return the indices of all slots in a STATUS_PENDING state.

  An <code>index</code> less than 0 will return the first (lowest
  numbered) slot. A value of 0 or greater will return the next higher
  numbered slot, or -1 if none.
   *
   * @param value the value to examine
   * @param index the index at which to start looking
   * @return next index, or -1 if none
   */
  private final int getNextPendingWrite(int value, int index) {
    long field = valuePendingToSlots[value];

    if (index < 0)
      index = 0;
    else
      index += 1;
    return indexOfLowestBit(field, index, nStackWords+nLocalWords);
  }

  /**
   * When called repeatedly, <code>getNextCompletedWrite</code>
  will iterate through the slots containing <code>value</code> and
  return the indices of all slots in a STATUS_COMPLETED state.

  An <code>index</code> less than 0 will return the first (lowest
  numbered) slot. A value of 0 or greater will return the next higher
  numbered slot, or -1 if none.
   *
   * @param value the value to examine
   * @param index the index at which to start looking
   * @return next index, or -1 if none
   */
  private final int getNextCompletedWrite(int value, int index) {
    long field = valueCompletedToSlots[value];

    if (index < 0)
      index = 0;
    else
      index += 1;
    
    return indexOfLowestBit(field, index, nStackWords+nLocalWords);
  }

  /**
     Puts the value into any register.
  * Here are our choices, in order of preference:
  * 1) if already in a register, use that
  * 2) if there is a pending write to a register-local, do that write
  * 3) if there is a pending write to a live register-stack, do that write
  * 4) load the value into a temporary.
  @param rd A <code>RegisterDescription</code> object to hold the
  results
  @param theValue the value to get into a register
  @param liveStackHeight how much of the stack is live
  */
  
  private void moveValueToAnyRegister(RegisterDescription rd,
                                       int theValue, int liveStackHeight) {
    byte javaType = valueJavaTypes[theValue];
    int index = -1;
    int stackIndex;
    int localIndex;
    int completedWriteStackLocation=0;
    int completedWriteLocalLocation=0;
    int completedWriteLocation=0;
    
    rd.register = -1;

    // First, we see if the value is already written to a register and
    // if so use that register. 
    if (anyCompletedWrites(theValue)) {
      for (index = getNextCompletedWrite(theValue, -1);
           index >= 0;
           index = getNextCompletedWrite(theValue, index)) {
        stackIndex = slotIndexToStackIndex(index);
        if (stackIndex >=0) {
          completedWriteStackLocation = getStackLocation(javaType, stackIndex);
          if (isRegister(completedWriteStackLocation)) {
            rd.slotIndex = index;
            rd.register = locationToRegister(completedWriteStackLocation);
            break;
          }
        } else {
          localIndex = slotIndexToLocalIndex(index);
          if (VM.VerifyAssertions) VM._assert(localIndex >= 0);
          completedWriteLocalLocation = getLocalLocation(javaType, localIndex);
          if (isRegister(completedWriteLocalLocation)) {
            rd.slotIndex = index;
            rd.register = locationToRegister(completedWriteLocalLocation);
            break;
          }
        }
      }
    }
    
    if (rd.register >= 0) {
      return;
    }

    // Next, are there any pending writes to a register? Find first
    int pendingWriteStackLocation = 0;
    int pendingWriteLocalLocation = 0;
    if (anyPendingWrites(theValue, liveStackHeight)) {
      for (index = getNextPendingWrite(theValue, -1);
           index >= 0;
           index = getNextPendingWrite(theValue, index)) {
        // We take advantage of the fact that the *Write iterator
        // checks local slots before checking stack slots to abort
        // this loop early if there is a pending write to a local.
        localIndex = slotIndexToLocalIndex(index);
        if (localIndex >= 0) {
          pendingWriteLocalLocation = getLocalLocation(javaType, localIndex);
          if (isRegister(pendingWriteLocalLocation)) {
            rd.slotIndex = index;
            rd.register = locationToRegister(pendingWriteLocalLocation);
            setSlotStatus(index, STATUS_WRITTEN);
            break;
          }
        } else {
          stackIndex = slotIndexToStackIndex(index);
          if (VM.VerifyAssertions) VM._assert(stackIndex >= 0);
          pendingWriteStackLocation = getStackLocation(javaType, stackIndex);
          if (isRegister(pendingWriteStackLocation)) {
            rd.slotIndex = index;
            rd.register = locationToRegister(pendingWriteStackLocation);
            setSlotStatus(index, STATUS_WRITTEN);
            break;
          }
        }
      }
    }
    
    if (rd.register < 0) 
      {
      // OK, the value is only stored in memory right now, and there is
      // no request to write it to a register, so we must move it from
      // memory into a temporary register.

      rd.slotIndex = -1;
    
      if (VM.VerifyAssertions) {
        VM._assert((valueTypes[theValue] == VALUE_TYPE_CONSTANT) ||
                   completedWriteLocalLocation != 0 ||
                   completedWriteStackLocation != 0);
      }

      rd.register = getTempRegister(javaType);

    }

    
    
    if (valueTypes[theValue] == VALUE_TYPE_CONSTANT) {
      asm.emitLVAL(rd.register, valueContents[theValue]);
    } else {
      // The priority here really doesn't matter....
      if (completedWriteStackLocation != 0)
        completedWriteLocation = completedWriteStackLocation;
      else
        completedWriteLocation = completedWriteLocalLocation;
      // TODO: If dest is a stack slot and not a register
      copyByLocation(javaType, completedWriteLocation,
                     javaType, registerToLocation(rd.register));
    }

  }
  
  /**
   * Puts a stack entry into a register if necessary.
   *
   * @param stackIndex the index of the entry
   * @param liveStackHeight how much of the stack is alive
   * @return an <code>int</code> value
   */
  private int moveStackEntryToRegister(int stackIndex,
                                             int liveStackHeight) {
    int theValue = slotContents[stackIndexToSlotIndex(stackIndex)];
    moveValueToAnyRegister(registerDescription,
                             theValue, liveStackHeight);
    return registerDescription.register;
  }
  
  /**
   * Puts a stack entry into a specific register.
   *
   * @param stackIndex the index of the entry
   * @param liveStackHeight how much of the stack is alive
   * @param requiredRegister the register
   * @return an <code>int</code> value
   */
  private void moveStackEntryToSpecificRegister(int stackIndex,
                                           int liveStackHeight,
                                           int requiredRegister) {
    int r=-1;
    int theValue = slotContents[stackIndexToSlotIndex(stackIndex)];
    byte javaType = valueJavaTypes[theValue];
    int index = -1;
    int localIndex;
    int completedWriteStackLocation=0;
    int completedWriteLocalLocation=0;
    int completedWriteLocation=0;
    
    
    if (valueTypes[theValue] == VALUE_TYPE_CONSTANT) {
      asm.emitLVAL   (requiredRegister,  valueContents[theValue]);   
      return;
    }

    if (allowRetargeting &&
        valueTypes[theValue] == VALUE_TYPE_CALCULATION &&
        valueSetAtMCI[theValue] >= 0 &&
        !anyPendingWrites(theValue) &&
        numberOfCompletedWrites(theValue) == 1 &&
        //CJH XXX lastGCIndex <= valueSetAtBCI[theValue] &&
        ((requiredRegister > FIRST_VOLATILE_GPR + 2 &&
          requiredRegister < LAST_VOLATILE_GPR) ||
         requiredRegister >= FIRST_NONVOLATILE_GPR)) {

      if (asm.retargetInstruction(valueSetAtMCI[theValue],
                                  requiredRegister)) {
        valueRetargeted(theValue);
        // The value is no longer reachable...
        return;
      }
    }

      // First, we see if the value is already written to a register and
    // if so use that register. 
    if (anyCompletedWrites(theValue)) {
      for (index = getNextCompletedWrite(theValue, -1);
           index >= 0;
           index = getNextCompletedWrite(theValue, index)) {
        stackIndex = slotIndexToStackIndex(index);
        if (stackIndex >=0) {
          completedWriteStackLocation = getStackLocation(javaType, stackIndex);
          if (isRegister(completedWriteStackLocation)) {
            r = locationToRegister(completedWriteStackLocation);
            break;
          }
        } else {
          localIndex = slotIndexToLocalIndex(index);
          if (VM.VerifyAssertions) VM._assert(localIndex >= 0);
          completedWriteLocalLocation = getLocalLocation(javaType, localIndex);
          if (isRegister(completedWriteLocalLocation)) {
            r = locationToRegister(completedWriteLocalLocation);
            break;
          }
        }
      }
    }
    
    if (r >= 0) {
      copyByLocation(javaType, registerToLocation(r),
                     javaType, registerToLocation(requiredRegister));
      return;
    }

    // The priority here really doesn't matter...
    if (completedWriteStackLocation != 0)
      completedWriteLocation = completedWriteStackLocation;
    else
      completedWriteLocation = completedWriteLocalLocation;
    
    copyByLocation(javaType, completedWriteLocation,
                   javaType, registerToLocation(requiredRegister));
  }


  /**
   * Ensures the value in the slot will still be available if the slot
  is overwritten. If there are any pending writes to any local
  variables or live stack slots, regardless of whether the slot is
  stored in memory or a register, it will be moved into one of them.
   *
   * @param slotIndex the slot index
   * @param liveStackHeight how much of the stack is still alive
   */
  private void saveSlotInSafeLocation(int slotIndex, int liveStackHeight) {
    saveValueInSafeLocation(slotContents[slotIndex], slotIndex, liveStackHeight);
  }
  
  /**
   * Ensures the value in a slot will still be available if the slot
  is overwritten. If there are any pending writes to any local
  variables or live stack slots, regardless of whether the slot is
  stored in memory or a register, it will be moved into one of them.
   *
   * @param theValue the value
   * @param slotBeingWritten the slot that will receive the value
   * @param liveStackHeight how much of the stack is still alive
   */
  private void saveValueInSafeLocation(int theValue, int slotBeingWritten,
                                int liveStackHeight) {
    if (!onlyPendingWrites(theValue, slotBeingWritten,
                                 liveStackHeight)) {
      return;
    }
    
    
    moveValueToAnyRegister(registerDescription, theValue, liveStackHeight);
    int r = registerDescription.register;


    // Check to see if the value is in a temporary register, or in
    // the dead part of the stack, or only in the slot about to be
    // overwritten. If not, it's in a safe location.
    
    if (registerDescription.slotIndex != slotBeingWritten) {
      if (registerDescription.slotIndex >= 0 &&
          (slotIndexToLocalIndex(registerDescription.slotIndex) >= 0 ||
          (slotIndexToStackIndex(registerDescription.slotIndex) <
           liveStackHeight))) {
        return;
      }
    }

    for (int index = getNextPendingWrite(theValue, -1);
         index >= 0;
         index = getNextPendingWrite(theValue, index)) {

      int stackIndex = slotIndexToStackIndex(index);
          
      if (stackIndex >= liveStackHeight)
        continue;             // to dead part of stack, so ignore it

      byte javaType = valueJavaTypes[theValue];
      int dstLocation;
      if (stackIndex >= 0)
        dstLocation = getStackLocation(javaType, stackIndex);
      else
        dstLocation = getLocalLocation(javaType,
                                       slotIndexToLocalIndex(index));

      setSlotStatus(index, STATUS_WRITTEN);

      copyByLocation(javaType, registerToLocation(r),
                     javaType, dstLocation);
      break;
    }
  }

  /**
     Forces the contents of a slot to be written to that slot if it
     hasn't already been done.
     @param slot the slot to flush
     @param liveStackHeight how much of the stack is alive
   */
  private void flushSlot(int slot, int liveStackHeight) {

    if (slotStatus[slot] != STATUS_PENDING)
      return;

    int theValue = slotContents[slot];

    moveValueToAnyRegister(registerDescription, theValue, liveStackHeight);
    int r = registerDescription.register;
    setSlotStatus(slot, STATUS_WRITTEN);

    int stackIndex = slotIndexToStackIndex(slot);

    if (stackIndex >= 0) {
      int stackLocation = getStackLocation(valueJavaTypes[theValue],
                                           stackIndex);

      if (registerToLocation(r) == stackLocation)
        return;                 // Got it in the desired place
      
      copyByLocation(valueJavaTypes[theValue], registerToLocation(r),
                     valueJavaTypes[theValue], stackLocation);
    } else {
      int localIndex = slotIndexToLocalIndex(slot);
      int localLocation = getLocalLocation(valueJavaTypes[theValue],
                                           localIndex);
      if (registerToLocation(r) == localLocation)
        return;                 // Got it in the desired place
      
        copyByLocation(valueJavaTypes[theValue], registerToLocation(r),
                     valueJavaTypes[theValue], localLocation);
    }
  }

  
  //-#endif RVM_WITH_QUICK_COMPILER_MOVE_ELIMINATION
  
  // Load/Store assist
  private int aloadSetup (int logSize, int indexRegister, int refRegister,
                           int offsetRegister) {
    asm.emitLWZ   (offsetRegister,  VM_ObjectModel.getArrayLengthOffset(), refRegister);  
    asm.emitTWLLE(offsetRegister, indexRegister); // trap if index < 0
                                                  // or index >=
                                                  // length
    if (logSize > 0) {
      asm.emitSLWI  (offsetRegister, indexRegister,  logSize); // convert index to offset
      return offsetRegister;
    } else
      return indexRegister;
  }

  private int astoreSetup (int logSize, 
                            int indexRegister, int refRegister,
                            int offsetRegister) {
    asm.emitLWZ   (offsetRegister,  VM_ObjectModel.getArrayLengthOffset(), refRegister);
    asm.emitTWLLE(offsetRegister, indexRegister); // trap if index < 0 or index >= length
    if (logSize > 0) {
      asm.emitSLWI  (offsetRegister, indexRegister,  logSize); // convert index to offset
      return offsetRegister;
    } else
      return indexRegister;
  }

  
  // Emit code to buy a stackframe, store incoming parameters, 
  // and acquire method synchronization lock.
  //
  private void genPrologue () {
    if (klass.isBridgeFromNative()) {
      VM_JNICompiler.generateGlueCodeForJNIMethod (asm, method);
    }

    // Generate trap if new frame would cross guard page.
    //
    if (isInterruptible) {
      asm.emitStackOverflowCheck(frameSize);                            // clobbers R0, S0
    }

    // Buy frame.
    //
    asm.emitSTAddrU (FP, -frameSize, FP); // save old FP & buy new frame (trap if new frame below guard page) !!TODO: handle frames larger than 32k when addressing local variables, etc.
    
    // If this is a "dynamic bridge" method, then save all registers except GPR0, FPR0, JTOC, and FP.
    //
    if (klass.isDynamicBridge()) {
      int offset = frameSize;
      for (int i = LAST_NONVOLATILE_FPR; i >= FIRST_VOLATILE_FPR; --i)
         asm.emitSTFD (i, offset -= BYTES_IN_DOUBLE, FP);
      for (int i = LAST_NONVOLATILE_GPR; i >= FIRST_VOLATILE_GPR; --i)
         asm.emitSTAddr(i, offset -= BYTES_IN_ADDRESS, FP);

      //-#if RVM_WITH_OSR
      // round up first, save scratch FPRs
      offset = VM_Memory.alignDown(offset - STACKFRAME_ALIGNMENT + 1, STACKFRAME_ALIGNMENT);

      for (int i = LAST_SCRATCH_FPR; i >= FIRST_SCRATCH_FPR; --i)
        asm.emitSTFD(i, offset -= BYTES_IN_DOUBLE, FP);
      for (int i = LAST_SCRATCH_GPR; i >= FIRST_SCRATCH_GPR; --i)
        asm.emitSTAddr(i, offset -= BYTES_IN_ADDRESS, FP);
      //-#endif
    }
    
    // TODO: this is unnecessary? if klass is dynamic bridge, as all
    // the registers are saved.
    int limit;
    int offset = callerSaveOffset;
    for (int i = min(firstFixedLocalRegister, firstFixedStackRegister);
         i <= max(lastFixedLocalRegister, lastFixedStackRegister);
         i++, offset -= 4) {
      asm.emitSTW(i, offset, FP);
    }

    // and save our scratch registers
    
    asm.emitSTW(S1, offset, FP);
    offset -= 4;
    asm.emitSTW(S0, offset, FP);
    offset -= 4;
    
    for (int i = min(firstFloatLocalRegister, firstFloatStackRegister);
         i <= max(lastFloatLocalRegister, lastFloatStackRegister);
         i++, offset -= 8) {
      asm.emitSTFD (i, offset, FP);
    }

    // and save our float scratch register
    // Caution : we know there is is only one!
    asm.emitSTFD (SF0, offset, FP);
    offset -= 8;
    
    
    // Fill in frame header.
    //
    asm.emitLVAL(S0, compiledMethod.getId());
    
    asm.emitMFLR(0);
    asm.emitSTW (S0, STACKFRAME_METHOD_ID_OFFSET, FP);                   // save compiled method id
    asm.emitSTAddr (0, frameSize + STACKFRAME_NEXT_INSTRUCTION_OFFSET, FP); // save LR !!TODO: handle discontiguous stacks when saving return address
    
    // Setup expression stack and locals.
    //
    genMoveParametersToLocals();                          

    // if the method is not static, the first argument to the method
    // is the 'this' pointer. Save it so we're sure to have a safe
    // reference to that ptr.
    if (!method.isStatic())
      asm.emitSTW(FIRST_VOLATILE_GPR, savedThisPtrOffset, FP);
   
    // Perform a thread switch if so requested.
    //
    //-#if RVM_WITH_OSR
    /* defer generating prologues which may trigger GC, see emit_deferred_prologue*/
    if (method.isForOsrSpecialization()) {
      return;
    }
    //-#endif
    genThreadSwitchTest(VM_Thread.PROLOGUE); //           (VM_BaselineExceptionDeliverer WONT release the lock (for synchronized methods) during prologue code)

    // Acquire method syncronization lock.  (VM_BaselineExceptionDeliverer will release the lock (for synchronized methods) after  prologue code)
    //
    if (method.isSynchronized()) 
      genSynchronizedMethodPrologue();

  }

  int lockOffset; // instruction that acquires the monitor for a synchronized method

  // Emit code to acquire method synchronization lock.
  //
  private void genSynchronizedMethodPrologue() {
    if (method.isStatic()) { // put java.lang.Class object for VM_Type into T0
      if (VM.writingBootImage) {
        VM.deferClassObjectCreation(klass);
      } else {
        klass.getClassForType();
      }
      int tibOffset = klass.getTibOffset();
      asm.emitLAddrToc(T0, tibOffset);
      asm.emitLAddr(T0, 0, T0);
      asm.emitLAddr(T0, VM_Entrypoints.classForTypeField.getOffset(), T0); 
    } else { 
      asm.emitLAddr(T0, savedThisPtrOffset, FP);
    }
    asm.emitLAddr(S0, VM_Entrypoints.lockMethod.getOffset(), JTOC); // call out...
    asm.emitMTCTR  (S0);                                  // ...of line lock
    asm.emitBCCTRL();
    lockOffset = BYTES_IN_INT*(asm.getMachineCodeIndex() - 1); // after this instruction, the method has the monitor
    
  }

  // Emit code to release method synchronization lock.
  //
  private void genSynchronizedMethodEpilogue () {
    if (method.isStatic()) { // put java.lang.Class for VM_Type into T0
      int tibOffset = klass.getTibOffset();
      asm.emitLAddrToc(T0, tibOffset);
      asm.emitLAddr(T0, 0, T0);
      asm.emitLAddr(T0, VM_Entrypoints.classForTypeField.getOffset(), T0); 
    } else { 
      asm.emitLAddr(T0, savedThisPtrOffset, FP);
    }
    asm.emitLAddr(S0, VM_Entrypoints.unlockMethod.getOffset(), JTOC);  // call out...
    asm.emitMTCTR(S0);                                     // ...of line lock
    asm.emitBCCTRL();
  }
    
  // Emit code to discard stackframe and return to caller.
  //
  private void genEpilogue () {
    if (klass.isDynamicBridge()) {// Restore non-volatile registers.
      // we never return from a DynamicBridge frame
      asm.emitTWWI(-1);
    } else {


      int offset = callerSaveOffset;

      
      for (int i = min(firstFixedLocalRegister, firstFixedStackRegister);
           i <= max(lastFixedLocalRegister, lastFixedStackRegister);
           i++, offset -= 4) {
        asm.emitLWZ (i, offset, FP);
      }
      // and restore our scratch registers
      asm.emitLWZ(S1, offset, FP);
      offset -= 4;
      asm.emitLWZ(S0, offset, FP);
      offset -= 4;
      
      for (int i = min(firstFloatLocalRegister, firstFloatStackRegister);
           i <= max(lastFloatLocalRegister, lastFloatStackRegister);
           i++, offset -= 8) {
        asm.emitLFD (i, offset, FP);
      }

      // and restore our float scratch register
      asm.emitLFD (SF0, offset, FP);
      offset -= 8;
    

      if (frameSize <= 0x8000) {
        asm.emitADDI(FP, frameSize, FP); // discard current frame
      } else {
        asm.emitLWZ(FP, 0, FP);           // discard current frame
      }
      asm.emitLWZ   (S0, STACKFRAME_NEXT_INSTRUCTION_OFFSET, FP); 
      asm.emitMTLR(S0);
      asm.emitBCLR (); // branch always, through link register
    }
  }

  /**
   * Emit the code for a bytecode level conditional branch
   * @param cc the condition code to branch on
   * @param bTarget the target bytecode index
   */
  private void genCondBranch(int cc, int bTarget) {
    if (options.EDGE_COUNTERS) {
      // Allocate 2 counters, taken and not taken
      int entry = edgeCounterIdx;
      edgeCounterIdx += 2;

      // Load counter array for this method
      asm.emitLAddrToc (S0, VM_Entrypoints.edgeCountersField.getOffset());
      asm.emitLAddrOffset(S0, S0, getEdgeCounterOffset());

      // Flip conditions so we can jump over the increment of the taken counter.
      VM_ForwardReference fr = asm.emitForwardBC(asm.flipCode(cc));

      // Increment taken counter & jump to target
      incEdgeCounter(S0, S1, entry+VM_EdgeCounts.TAKEN);
      asm.emitB(bytecodeMap[bTarget], bTarget);

      // Not taken
      fr.resolve(asm);
      incEdgeCounter(S0, S1, entry+VM_EdgeCounts.NOT_TAKEN);
    } else {
      if (bTarget - SHORT_FORWARD_LIMIT < biStart) {
        asm.emitShortBC(cc, bytecodeMap[bTarget], bTarget);
      } else {
        asm.emitBC(cc, bytecodeMap[bTarget], bTarget);
      }
    }
  }


  
  //*************************************************************************
  //                             MAGIC
  //*************************************************************************

  /*
   *  Generate inline machine instructions for special methods that cannot be 
   *  implemented in java bytecodes. These instructions are generated whenever  
   *  we encounter an "invokestatic" bytecode that calls a method with a 
   *  signature of the form "static native VM_Magic.xxx(...)".
   *  23 Jan 1998 Derek Lieber
   * 
   * NOTE: when adding a new "methodName" to "generate()", be sure to also 
   * consider how it affects the values on the stack and update 
   * "checkForActualCall()" accordingly.
   * If no call is actually generated, the map will reflect the status of the 
   * locals (including parameters) at the time of the call but nothing on the 
   * operand stack for the call site will be mapped.
   *  7 Jul 1998 Janice Shepherd
   */

  /** Generate inline code sequence for specified method.
   * @param methodToBeCalled: method whose name indicates semantics of code to be generated
   * @return true if there was magic defined for the method
   */
  private boolean  generateInlineCode(VM_MethodReference methodToBeCalled) {
    VM_Atom methodName = methodToBeCalled.getName();

    

    if (methodToBeCalled.getType() == VM_TypeReference.SysCall) {
      VM_TypeReference[] args = methodToBeCalled.getParameterTypes();

      // (1) Set up arguments according to OS calling convention
      int paramWords = methodToBeCalled.getParameterWords();
      int gp = FIRST_OS_PARAMETER_GPR;
      int fp = FIRST_OS_PARAMETER_FPR;
      int stackHeight = currentStackHeight-paramWords;
      for (int i=0; i<args.length; i++) {
        VM_TypeReference t = args[i];
        if (t.isLongType()) {
          if (VM.BuildFor64Addr) {
            peekToRegister(LONG_TYPE, stackHeight, gp);
            gp++;  
          } else {
            peekToRegister(LONG_TYPE, stackHeight, gp);
            gp += 2;
          }
          stackHeight += 2;
        } else if (t.isFloatType()) {
          peekToRegister(FLOAT_TYPE, stackHeight, fp++);
          stackHeight += 1;
        } else if (t.isDoubleType()) {
          peekToRegister(DOUBLE_TYPE, stackHeight, fp++);
          stackHeight += 2;
        } else if (t.isIntLikeType()) {
          peekToRegister(WORD_TYPE, stackHeight, gp++);
          stackHeight += 1;
        } else { // t is object
          peekToRegister(OBJECT_TYPE, stackHeight, gp++);
          stackHeight += 1;
        }
      }

      currentStackHeight -= paramWords;
      
      if (VM.VerifyAssertions) {
        VM._assert(currentStackHeight >= 0);
        // Laziness.  We don't support sysCalls with so 
        // many arguments that we would have to spill some to the stack.
        VM._assert(gp - 1 <= LAST_OS_PARAMETER_GPR);
        VM._assert(fp - 1 <= LAST_OS_PARAMETER_FPR);
      }

      // (2) Call it
      int paramBytes = paramWords * BYTES_IN_STACKSLOT;
      VM_Field ip = VM_Entrypoints.getSysCallField(methodName.toString());
      generateSysCall(paramBytes, ip);

      // (3) Push return value (if any)
      VM_TypeReference rtype = methodToBeCalled.getReturnType();
      if (rtype.isIntLikeType()) {
        pushFromRegister(WORD_TYPE, T0);
      } else if (rtype.isWordType() || rtype.isReferenceType()) {
        pushFromRegister(OBJECT_TYPE, T0); 
      } else if (rtype.isDoubleType()) {
        pushFromRegister(DOUBLE_TYPE, FIRST_OS_PARAMETER_FPR);
      } else if (rtype.isFloatType()) {
        pushFromRegister(FLOAT_TYPE, FIRST_OS_PARAMETER_FPR);
      } else if (rtype.isLongType()) {
        pushFromRegister(LONG_TYPE, T0);
      }
      return true;
    }

    if (methodToBeCalled.getType() == VM_TypeReference.Address) {
      // Address.xyz magic

      VM_TypeReference[] types = methodToBeCalled.getParameterTypes();

      // Loads all take the form:
      // ..., Address, [Offset] -> ..., Value

      if (methodName == VM_MagicNames.loadAddress ||
          methodName == VM_MagicNames.loadObjectReference ||
          methodName == VM_MagicNames.loadWord) {

        if (types.length == 0) {
          popToRegister(OBJECT_TYPE, T0);                  // pop base 
          asm.emitLAddr(T0,  0, T0);    // *(base)
          pushFromRegister(OBJECT_TYPE, T0);                 // push *(base)
        } else {
          popToRegister(WORD_TYPE, T1);                   // pop offset
          popToRegister(OBJECT_TYPE, T0);                  // pop base 
          asm.emitLAddrX(T0, T1, T0);   // *(base+offset)
          pushFromRegister(OBJECT_TYPE, T0);                 // push *(base+offset)
        }
        return true;
      }

      if (methodName == VM_MagicNames.loadChar ||
          methodName == VM_MagicNames.loadShort) {

        if (types.length == 0) {
          popToRegister(OBJECT_TYPE, T0);                  // pop base
          asm.emitLHZ(T0, 0, T0);       // load with zero extension.
          pushFromRegister(WORD_TYPE, T0);                  // push *(base) 
        } else {
          popToRegister(WORD_TYPE, T1);                   // pop offset
          popToRegister(OBJECT_TYPE, T0);                  // pop base
          asm.emitLHZX(T0, T1, T0);     // load with zero extension.
          pushFromRegister(WORD_TYPE, T0);                  // push *(base+offset) 
        }
        return true;
      }

      if (methodName == VM_MagicNames.loadByte) {
        if (types.length == 0) {
          popToRegister(OBJECT_TYPE, T0);                  // pop base
          asm.emitLBZ(T0, 0, T0);       // load with zero extension.
          pushFromRegister(WORD_TYPE, T0);                  // push *(base) 
        } else {
          popToRegister(WORD_TYPE, T1);                   // pop offset
          popToRegister(OBJECT_TYPE, T0);                  // pop base
          asm.emitLBZX(T0, T1, T0);     // load with zero extension.
          pushFromRegister(WORD_TYPE, T0);                  // push *(base+offset) 
        }
        return true;
      }


      if (methodName == VM_MagicNames.loadInt ||
          methodName == VM_MagicNames.loadFloat) {

        if (types.length == 0) {
          popToRegister(OBJECT_TYPE, T0);                  // pop base 
          asm.emitLInt(T0,  0, T0);     // *(base)
          pushFromRegister(WORD_TYPE, T0);                  // push *(base)
        } else {
          popToRegister(WORD_TYPE, T1);                   // pop offset
          popToRegister(OBJECT_TYPE, T0);                  // pop base 
          asm.emitLIntX(T0, T1, T0);    // *(base+offset)
          pushFromRegister(WORD_TYPE, T0);                  // push *(base+offset)
        }
        return true;
      }

      if (methodName == VM_MagicNames.loadDouble ||
          methodName == VM_MagicNames.loadLong) {

        if (types.length == 0) {
          popToRegister(OBJECT_TYPE, T1);                  // pop base 
          asm.emitLFD (F0, 0, T1);      // *(base)
          pushFromRegister(DOUBLE_TYPE, F0);               // push double
        } else {       
          popToRegister(WORD_TYPE, T2);                   // pop offset 
          popToRegister(OBJECT_TYPE, T1);                  // pop base 
          asm.emitLFDX (F0, T1, T2);    // *(base+offset)
          pushFromRegister(DOUBLE_TYPE, F0);               // push *(base+offset)
        }
        return true;
      }
      
      // Prepares all take the form:
      // ..., Address, [Offset] -> ..., Value

      if (methodName == VM_MagicNames.prepareInt) {
        if (types.length == 0) {
          popToRegister(OBJECT_TYPE, T0);                             // pop base 
          asm.emitLWARX(T0, 0, T0);                // *(base), setting reservation address
          // this Integer is not sign extended !!
          pushFromRegister(WORD_TYPE, T0);                             // push *(base+offset)
        } else {
          popToRegister(WORD_TYPE, T1);                              // pop offset
          popToRegister(OBJECT_TYPE, T0);                             // pop base 
          asm.emitLWARX(T0,  T1, T0);              // *(base+offset), setting reservation address
          // this Integer is not sign extended !!
          pushFromRegister(WORD_TYPE, T0);                             // push *(base+offset)
        }
        return true;
      }

      if (methodName == VM_MagicNames.prepareWord || 
          methodName == VM_MagicNames.prepareObjectReference ||
          methodName == VM_MagicNames.prepareAddress) {
        if (types.length == 0) {
          popToRegister(OBJECT_TYPE, T0);                             // pop base
          if (VM.BuildFor32Addr) {
            asm.emitLWARX(T0, 0, T0);            // *(base+offset), setting 
          } else {                               // reservation address
            asm.emitLDARX(T0, 0, T0);
          }
          pushFromRegister(OBJECT_TYPE, T0);                            // push *(base+offset)
        } else {
          popToRegister(WORD_TYPE, T1);                              // pop offset
          popToRegister(OBJECT_TYPE, T0);                             // pop base
          if (VM.BuildFor32Addr) {
            asm.emitLWARX(T0,  T1, T0);          // *(base+offset), setting 
          } else {                               // reservation address
            asm.emitLDARX(T0, T1, T0);
          }
          pushFromRegister(OBJECT_TYPE, T0);                            // push *(base+offset)
        }
        return true;  
      } 

      // Attempts all take the form:
      // ..., Address, OldVal, NewVal, [Offset] -> ..., Success?

      if (methodName == VM_MagicNames.attempt && 
          types[0] == VM_TypeReference.Int) {
        if (types.length == 2) { 
          popToRegister(WORD_TYPE, T2);                            // pop newValue                 
          pop(WORD_TYPE);                         // ignore oldValue            
          popToRegister(OBJECT_TYPE, T0);                           // pop base 
          asm.emitSTWCXr(T2,  0, T0);            // store new value and set CR0
          asm.emitLVAL(T0,  0);                  // T0 := false
          VM_ForwardReference fr = asm.emitForwardBC(NE);             // skip, if store failed
          asm.emitLVAL(T0,  1);                  // T0 := true
          fr.resolve(asm);
          pushFromRegister(WORD_TYPE, T0);                           // push success of store
        } else {
          popToRegister(WORD_TYPE, T1);                            // pop offset
          popToRegister(WORD_TYPE, T2);                            // pop newValue                 
          pop(WORD_TYPE);                         // ignore oldValue            
          popToRegister(OBJECT_TYPE, T0);                           // pop base 
          asm.emitSTWCXr(T2,  T1, T0);           // store new value and set CR0
          asm.emitLVAL(T0,  0);                  // T0 := false
          VM_ForwardReference fr = asm.emitForwardBC(NE);             // skip, if store failed
          asm.emitLVAL(T0,  1);                  // T0 := true
          fr.resolve(asm);
          pushFromRegister(WORD_TYPE, T0);                           // push success of store
        }
        return true;
      }

      if (methodName == VM_MagicNames.attempt &&
          (types[0] == VM_TypeReference.Address ||
           types[0] == VM_TypeReference.Word)) {

        if (types.length == 2) {
          popToRegister(OBJECT_TYPE, T2);                             // pop newValue
          pop(WORD_TYPE);                           // ignore oldValue
          popToRegister(OBJECT_TYPE, T0);                             // pop base 
          if (VM.BuildFor32Addr) {
            asm.emitSTWCXr(T2,  0, T0);          // store new value and set CR0
          } else { 
            asm.emitSTDCXr(T2,  0, T0);          // store new value and set CR0
          }
          asm.emitLVAL(T0, 0);                   // T0 := false
          VM_ForwardReference fr = asm.emitForwardBC(NE);  // skip, if store failed
          asm.emitLVAL(T0, 1);                   // T0 := true
          fr.resolve(asm);  
          pushFromRegister(WORD_TYPE, T0);                           // push success of store
        } else {
          popToRegister(WORD_TYPE, T1);                              // pop offset
          popToRegister(OBJECT_TYPE, T2);                             // pop newValue
          pop(WORD_TYPE);                           // ignore oldValue
          popToRegister(OBJECT_TYPE, T0);                             // pop base 
          if (VM.BuildFor32Addr) {
            asm.emitSTWCXr(T2,  T1, T0);         // store new value and set CR0
          } else {
            asm.emitSTDCXr(T2,  T1, T0);         // store new value and set CR0
          }
          asm.emitLVAL(T0, 0);                   // T0 := false
          VM_ForwardReference fr = asm.emitForwardBC(NE);             // skip, if store failed
          asm.emitLVAL(T0, 1);                   // T0 := true
          fr.resolve(asm);  
          pushFromRegister(WORD_TYPE, T0);                           // push success of store
        }
        return true;
      }

      // Stores all take the form:
      // ..., Address, Value, [Offset] -> ...
      if (methodName == VM_MagicNames.store) {

        if(types[0] == VM_TypeReference.Word ||
           types[0] == VM_TypeReference.ObjectReference ||
           types[0] == VM_TypeReference.Address) {
          if (types.length == 1) {
            popToRegister(OBJECT_TYPE, T1);                 // pop newvalue
            popToRegister(OBJECT_TYPE, T0);                 // pop base 
            asm.emitSTAddrX(T1, 0, T0);   // *(base) = newvalue
          } else {
            popToRegister(WORD_TYPE, T1);                  // pop offset
            popToRegister(OBJECT_TYPE, T2);                 // pop newvalue
            popToRegister(OBJECT_TYPE, T0);                 // pop base 
            asm.emitSTAddrX(T2, T1, T0); // *(base+offset) = newvalue
          }
          return true;
        }

        if(types[0] == VM_TypeReference.Byte) {
          if (types.length == 1) {
            popToRegister(WORD_TYPE, T1);                  // pop newvalue
            popToRegister(OBJECT_TYPE, T0);                 // pop base 
            asm.emitSTBX(T1, 0, T0);      // *(base) = newvalue
          } else {
            popToRegister(WORD_TYPE, T1);                  // pop offset
            popToRegister(WORD_TYPE, T2);                  // pop newvalue
            popToRegister(OBJECT_TYPE, T0);                 // pop base 
            asm.emitSTBX(T2, T1, T0);    // *(base+offset) = newvalue
          }
          return true;
        }

        if(types[0] == VM_TypeReference.Int ||
           types[0] == VM_TypeReference.Float) {
          if (types.length == 1) {
            popToRegister(WORD_TYPE, T1);                  // pop newvalue
            popToRegister(OBJECT_TYPE, T0);                 // pop base 
            asm.emitSTWX(T1, 0, T0);      // *(base+offset) = newvalue
          } else {
            popToRegister(WORD_TYPE, T1);                  // pop offset
            popToRegister(WORD_TYPE, T2);                  // pop newvalue
            popToRegister(OBJECT_TYPE, T0);                 // pop base 
            asm.emitSTWX(T2, T1, T0);    // *(base+offset) = newvalue
          }
          return true;
        }

        if(types[0] == VM_TypeReference.Short ||
           types[0] == VM_TypeReference.Char) {
          if (types.length == 1) {
            popToRegister(WORD_TYPE, T1);                  // pop newvalue
            popToRegister(OBJECT_TYPE, T0);                 // pop base 
            asm.emitSTHX(T1, 0, T0);      // *(base) = newvalue
          } else {
            popToRegister(WORD_TYPE, T1);                  // pop offset
            popToRegister(WORD_TYPE, T2);                  // pop newvalue
            popToRegister(OBJECT_TYPE, T0);                 // pop base 
            asm.emitSTHX(T2, T1, T0);    // *(base+offset) = newvalue
          }
          return true;
        }

        if(types[0] == VM_TypeReference.Double ||
          types[0] == VM_TypeReference.Long) {
          if (types.length == 1) {
            popToRegister(LONG_TYPE, T1);                      // pop newvalue low and high
            popToRegister(OBJECT_TYPE, T0);                          // pop base 
            if (VM.BuildFor32Addr) {
              asm.emitSTWX(T2, 0, T0);             // *(base) = newvalue low
              asm.emitSTWX(T1, BYTES_IN_INT, T0);  // *(base+4) = newvalue high
            } else {
              asm.emitSTDX(T1, 0, T0);           // *(base) = newvalue 
            }
          } else {
            popToRegister(WORD_TYPE, T1);                           // pop offset
            popToRegister(LONG_TYPE, T2);                      // pop newvalue low and high
            popToRegister(OBJECT_TYPE, T0);                          // pop base 
            if (VM.BuildFor32Addr) {
              asm.emitSTWX(T3, T1, T0);           // *(base+offset) = newvalue low
              asm.emitADDI(T1, BYTES_IN_INT, T1); // offset += 4
              asm.emitSTWX(T2, T1, T0);           // *(base+offset) = newvalue high
            } else {
              asm.emitSTDX(T2, T1, T0);           // *(base+offset) = newvalue 
            }
          }
          return true;
        }
      }
    }

    if (methodName == VM_MagicNames.getFramePointer) {
      pushFromRegister(OBJECT_TYPE, FP); 
    } else if (methodName == VM_MagicNames.getCallerFramePointer) {
      popToRegister(OBJECT_TYPE, T0);                               // pop  frame pointer of callee frame
      asm.emitLAddr(T1, STACKFRAME_FRAME_POINTER_OFFSET, T0); // load frame pointer of caller frame
      pushFromRegister(OBJECT_TYPE, T1);                               // push frame pointer of caller frame
    } else if (methodName == VM_MagicNames.setCallerFramePointer) {
      popToRegister(OBJECT_TYPE, T1); // value
      popToRegister(OBJECT_TYPE, T0); // fp
      asm.emitSTAddr(T1,  STACKFRAME_FRAME_POINTER_OFFSET, T0); // *(address+SFPO) := value
    } else if (methodName == VM_MagicNames.getCompiledMethodID) {
      popToRegister(OBJECT_TYPE, T0);                           // pop  frame pointer of callee frame
      asm.emitLWZ (T1, STACKFRAME_METHOD_ID_OFFSET, T0); // load compiled method id
      pushFromRegister(WORD_TYPE, T1);                           // push method ID 
    } else if (methodName == VM_MagicNames.setCompiledMethodID) {
      popToRegister(WORD_TYPE, T1); // value
      popToRegister(OBJECT_TYPE, T0); // fp
      asm.emitSTW(T1,  STACKFRAME_METHOD_ID_OFFSET, T0); // *(address+SNIO) := value
    } else if (methodName == VM_MagicNames.getNextInstructionAddress) {
      popToRegister(OBJECT_TYPE, T0);                                  // pop  frame pointer of callee frame
      asm.emitLAddr(T1, STACKFRAME_NEXT_INSTRUCTION_OFFSET, T0); // load frame pointer of caller frame
      pushFromRegister(OBJECT_TYPE, T1);                                  // push frame pointer of caller frame
    } else if (methodName == VM_MagicNames.setNextInstructionAddress) {
      popToRegister(OBJECT_TYPE, T1); // value
      popToRegister(OBJECT_TYPE, T0); // fp
      asm.emitSTAddr(T1,  STACKFRAME_NEXT_INSTRUCTION_OFFSET, T0); // *(address+SNIO) := value
    } else if (methodName == VM_MagicNames.getReturnAddressLocation) {
      popToRegister(OBJECT_TYPE, T0);                                  // pop  frame pointer of callee frame
      asm.emitLAddr(T1, STACKFRAME_FRAME_POINTER_OFFSET, T0);    // load frame pointer of caller frame
      asm.emitADDI (T2, STACKFRAME_NEXT_INSTRUCTION_OFFSET, T1); // get location containing ret addr
      pushFromRegister(OBJECT_TYPE, T2);                                  // push frame pointer of caller frame
    } else if (methodName == VM_MagicNames.getTocPointer ||
               methodName == VM_MagicNames.getJTOC) {
      pushFromRegister(OBJECT_TYPE, JTOC); 
    } else if (methodName == VM_MagicNames.getProcessorRegister) {
      pushFromRegister(OBJECT_TYPE, PROCESSOR_REGISTER);
    } else if (methodName == VM_MagicNames.setProcessorRegister) {
      popToRegister(OBJECT_TYPE, PROCESSOR_REGISTER);
    } else if (methodName == VM_MagicNames.getTimeBase) {
      if (VM.BuildFor64Addr) {
        asm.emitMFTB (T0);      // T0 := time base
      } else {
        int label = asm.getMachineCodeIndex();
        asm.emitMFTBU(T0);                      // T0 := time base, upper
        asm.emitMFTB (T1);                      // T1 := time base, lower
        asm.emitMFTBU(T2);                      // T2 := time base, upper
        asm.emitCMP  (T0, T2);                  // T0 == T2?
        asm.emitBC   (NE, label);               // lower rolled over, try again
      }
      pushFromRegister(LONG_TYPE, T0);              
    }  else if (methodName == VM_MagicNames.invokeMain) {
      popToRegister(OBJECT_TYPE, T0); // t0 := ip
      asm.emitMTCTR(T0);
      popToRegister(OBJECT_TYPE, T0); // t0 := parameter
      asm.emitBCCTRL();          // call
    } else if (methodName == VM_MagicNames.invokeClassInitializer) {
      popToRegister(OBJECT_TYPE, T0); // t0 := address to be called
      asm.emitMTCTR(T0);
      asm.emitBCCTRL();          // call
    } else if (methodName == VM_MagicNames.invokeMethodReturningVoid) {
      generateMethodInvocation(); // call method
    } else if (methodName == VM_MagicNames.invokeMethodReturningInt) {
      generateMethodInvocation(); // call method
      pushFromRegister(WORD_TYPE, T0);       // push result
    } else if (methodName == VM_MagicNames.invokeMethodReturningLong) {
      generateMethodInvocation(); // call method
      pushFromRegister(LONG_TYPE, T0);       // push result
    } else if (methodName == VM_MagicNames.invokeMethodReturningFloat) {
      generateMethodInvocation(); // call method
      pushFromRegister(FLOAT_TYPE, F0);     // push result
    } else if (methodName == VM_MagicNames.invokeMethodReturningDouble) {
      generateMethodInvocation(); // call method
      pushFromRegister(DOUBLE_TYPE, F0);     // push result
    } else if (methodName == VM_MagicNames.invokeMethodReturningObject) {
      generateMethodInvocation(); // call method
      pushFromRegister(OBJECT_TYPE, T0);       // push result
    } else if (methodName == VM_MagicNames.addressArrayCreate) {
      VM_Array type = methodToBeCalled.getType().resolve().asArray();
      emit_resolved_newarray(type);
    } else if (methodName == VM_MagicNames.addressArrayLength) {
      emit_arraylength();
    } else if (methodName == VM_MagicNames.addressArrayGet) {
      if (VM.BuildFor32Addr || methodToBeCalled.getType() == VM_TypeReference.CodeArray) {
        emit_iaload();
      } else {
        if (VM.VerifyAssertions) VM._assert(NOT_REACHED);
        emit_laload();
      }
    } else if (methodName == VM_MagicNames.addressArraySet) {
      if (VM.BuildFor32Addr || methodToBeCalled.getType() == VM_TypeReference.CodeArray) {
        emit_iastore();  
      } else {
        if (VM.VerifyAssertions) VM._assert(NOT_REACHED);
        emit_lastore();
      }
    } else if (methodName == VM_MagicNames.getIntAtOffset) { 
      popToRegister(WORD_TYPE, T1); // pop offset
      popToRegister(OBJECT_TYPE, T0); // pop object
      asm.emitLIntX (T0, T1, T0); // *(object+offset)
      pushFromRegister(WORD_TYPE, T0); // push *(object+offset)
    } else if (methodName == VM_MagicNames.getObjectAtOffset ||
               methodName == VM_MagicNames.getObjectArrayAtOffset) {
      popToRegister(WORD_TYPE, T1); // pop offset
      popToRegister(OBJECT_TYPE, T0); // pop object
      asm.emitLAddrX(T0, T1, T0); // *(object+offset)
      pushFromRegister(OBJECT_TYPE, T0); // push *(object+offset)
    } else if (methodName == VM_MagicNames.getWordAtOffset) {
      popToRegister(WORD_TYPE, T1); // pop offset
      popToRegister(OBJECT_TYPE, T0); // pop object
      asm.emitLAddrX(T0, T1, T0); // *(object+offset)
      pushFromRegister(WORD_TYPE, T0); // push *(object+offset)
    } else if (methodName == VM_MagicNames.getByteAtOffset) {
      popToRegister(WORD_TYPE, T1);   // pop offset
      popToRegister(OBJECT_TYPE, T0);   // pop object
      asm.emitLBZX(T0, T1, T0);   // load byte with zero extension.
      pushFromRegister(WORD_TYPE, T0);    // push *(object+offset) 
    } else if (methodName == VM_MagicNames.getCharAtOffset) {
      popToRegister(WORD_TYPE, T1);   // pop offset
      popToRegister(OBJECT_TYPE, T0);   // pop object
      asm.emitLHZX(T0, T1, T0);   // load byte with zero extension.
      pushFromRegister(WORD_TYPE, T0);    // push *(object+offset) 
    } else if (methodName == VM_MagicNames.setIntAtOffset){
      popToRegister(WORD_TYPE, T2); // pop newvalue
      popToRegister(WORD_TYPE, T1); // pop offset
      popToRegister(OBJECT_TYPE, T0); // pop object
      asm.emitSTWX(T2, T1, T0); // *(object+offset) = newvalue
    } else if (methodName == VM_MagicNames.setObjectAtOffset ||
               methodName == VM_MagicNames.setWordAtOffset) {
      if (methodToBeCalled.getParameterTypes().length == 4) {
        pop(WORD_TYPE); // discard locationMetadata parameter
      }
      popToRegister(OBJECT_TYPE, T2); // pop newvalue
      popToRegister(WORD_TYPE, T1); // pop offset
      popToRegister(OBJECT_TYPE, T0); // pop object
      asm.emitSTAddrX(T2, T1, T0); // *(object+offset) = newvalue
    } else if (methodName == VM_MagicNames.setWordAtOffset) {
      popToRegister(WORD_TYPE, T2); // pop newvalue
      popToRegister(WORD_TYPE, T1); // pop offset
      popToRegister(OBJECT_TYPE, T0); // pop object
      asm.emitSTAddrX(T2, T1, T0); // *(object+offset) = newvalue
    } else if (methodName == VM_MagicNames.setByteAtOffset) {
      popToRegister(WORD_TYPE, T2); // pop newvalue
      popToRegister(WORD_TYPE, T1); // pop offset
      popToRegister(OBJECT_TYPE, T0); // pop object
      asm.emitSTBX(T2, T1, T0); // *(object+offset) = newvalue
    } else if (methodName == VM_MagicNames.setCharAtOffset) {
      popToRegister(WORD_TYPE, T2); // pop newvalue
      popToRegister(WORD_TYPE, T1); // pop offset
      popToRegister(OBJECT_TYPE, T0); // pop object
      asm.emitSTHX(T2, T1, T0); // *(object+offset) = newvalue
    } else if (methodName == VM_MagicNames.getLongAtOffset) {
      popToRegister(WORD_TYPE, T2); // pop offset
      popToRegister(OBJECT_TYPE, T1); // pop object
      if (VM.BuildFor32Addr) {
        // The long will be stored in T0 and T1
        asm.emitLWZX (T0, T1, T2); // *(object+offset)
        asm.emitADDI(T2, BYTES_IN_INT, T2); // offset += 4
        asm.emitLWZX (T1, T1, T2); // *(object+offset+4)
      } else {
        asm.emitLDX(T1, T1, T2);
      }
      pushFromRegister(LONG_TYPE, T0);
    } else if (methodName == VM_MagicNames.getDoubleAtOffset) {
      popToRegister(WORD_TYPE, T2); // pop offset
      popToRegister(OBJECT_TYPE, T1); // pop object
      asm.emitLFDX (F0, T1, T2); 
      pushFromRegister(DOUBLE_TYPE, F0);
    } else if (methodName == VM_MagicNames.setLongAtOffset){
      popToRegister(LONG_TYPE, T2); // Long goes to T2 and T3
      popToRegister(WORD_TYPE, T1); // pop offset
      popToRegister(OBJECT_TYPE, T0); // pop object
      if (VM.BuildFor32Addr) {
        asm.emitSTWX(T2, T1, T0); // *(object+offset) = newvalue high
        asm.emitADDI(T1, BYTES_IN_INT, T1); // offset += 4
        asm.emitSTWX(T3, T1, T0); // *(object+offset) = newvalue low
      } else {
        asm.emitSTDX(T2, T1, T0); // *(object+offset) = newvalue 
      } 
    } else if (methodName == VM_MagicNames.setDoubleAtOffset) {
      popToRegister(DOUBLE_TYPE, F0);
      popToRegister(WORD_TYPE, T1); // pop offset
      popToRegister(OBJECT_TYPE, T0); // pop object
      asm.emitSTFDX (F0, T1, T0);  // store double value in object
    } else if (methodName == VM_MagicNames.getMemoryInt){
      popToRegister(OBJECT_TYPE, T0); // address
      asm.emitLInt (T0,  0, T0); // *address
      pushFromRegister(WORD_TYPE, T0); // *sp := *address
    } else if (methodName == VM_MagicNames.getMemoryWord ||
               methodName == VM_MagicNames.getMemoryAddress) {
      popToRegister(OBJECT_TYPE, T0); // address
      asm.emitLAddr(T0,  0, T0); // *address
      pushFromRegister(OBJECT_TYPE, T0); // *sp := *address
    } else if (methodName == VM_MagicNames.setMemoryInt ){
      popToRegister(WORD_TYPE, T1); // value
      popToRegister(OBJECT_TYPE, T0); // address
      asm.emitSTW(T1,  0, T0); // *address := value
    } else if (methodName == VM_MagicNames.setMemoryWord ||
               methodName == VM_MagicNames.setMemoryAddress) {
      popToRegister(OBJECT_TYPE, T1); // value
      popToRegister(OBJECT_TYPE, T0); // address
      asm.emitSTAddr(T1,  0, T0); // *address := value
    } else if (methodName == VM_MagicNames.prepareInt){
      popToRegister(WORD_TYPE, T1); // pop offset
      popToRegister(OBJECT_TYPE, T0); // pop object
      if (VM.BuildForSingleVirtualProcessor) {
        asm.emitLWZX (T0, T1, T0); // *(object+offset)
      } else {
        asm.emitLWARX(T0,  T1, T0); // *(object+offset), setting processor's reservation address
      } //this Integer is not sign extended !!
      pushFromRegister(WORD_TYPE, T0); // push *(object+offset)
    } else if (methodName == VM_MagicNames.prepareObject ||
               methodName == VM_MagicNames.prepareAddress ||
               methodName == VM_MagicNames.prepareWord) {
      popToRegister(WORD_TYPE, T1); // pop offset
      popToRegister(OBJECT_TYPE, T0); // pop object
      if (VM.BuildForSingleVirtualProcessor) {
        asm.emitLAddrX(T0, T1, T0); // *(object+offset)
      } else {
        if (VM.BuildFor32Addr) {
          asm.emitLWARX(T0,  T1, T0); // *(object+offset), setting processor's reservation address
        } else {
          asm.emitLDARX(T0, T1, T0);
        } 
      }
      pushFromRegister((methodName == VM_MagicNames.prepareWord)?WORD_TYPE:OBJECT_TYPE, T0); // push *(object+offset)
    } else if (methodName == VM_MagicNames.attemptInt){
      popToRegister(WORD_TYPE, T2);  // pop newValue
      pop(WORD_TYPE); // ignore oldValue
      popToRegister(WORD_TYPE, T1);  // pop offset
      popToRegister(OBJECT_TYPE, T0);  // pop object
      if (VM.BuildForSingleVirtualProcessor) {
        asm.emitSTWX(T2, T1, T0); // store new value (on one VP this succeeds by definition)
        asm.emitLVAL   (T0,  1);   // T0 := true
        pushFromRegister(WORD_TYPE, T0);  // push success of conditional store
      } else {
        asm.emitSTWCXr(T2,  T1, T0); // store new value and set CR0
        asm.emitLVAL   (T0,  0);  // T0 := false
        VM_ForwardReference fr = asm.emitForwardBC(NE); // skip, if store failed
        asm.emitLVAL   (T0,  1);   // T0 := true
        fr.resolve(asm);
        pushFromRegister(WORD_TYPE, T0);  // push success of conditional store
      }
    } else if (methodName == VM_MagicNames.attemptObject ||
               methodName == VM_MagicNames.attemptAddress ||
               methodName == VM_MagicNames.attemptWord) {
      popToRegister(OBJECT_TYPE, T2);  // pop newValue
      pop(OBJECT_TYPE); // ignore oldValue
      popToRegister(WORD_TYPE, T1);  // pop offset
      popToRegister(OBJECT_TYPE, T0);  // pop object
      if (VM.BuildForSingleVirtualProcessor) {
        asm.emitSTAddrX(T2,  T1, T0); // store new value (on one VP this succeeds by definition)
        asm.emitLVAL   (T0,  1);   // T0 := true
        pushFromRegister(OBJECT_TYPE, T0);  // push success of conditional store
      } else {
        if (VM.BuildFor32Addr) {
          asm.emitSTWCXr(T2,  T1, T0); // store new value and set CR0
        } else {
          asm.emitSTDCXr(T2,  T1, T0); // store new value and set CR0
        }
        asm.emitLVAL   (T0,  0);  // T0 := false
        VM_ForwardReference fr = asm.emitForwardBC(NE); // skip, if store failed
        asm.emitLVAL   (T0,  1);   // T0 := true
        fr.resolve(asm);
        pushFromRegister(WORD_TYPE, T0);  // push success of conditional store
      }
    } else if (methodName == VM_MagicNames.saveThreadState) {
      peekToRegister(OBJECT_TYPE, T0); // T0 := address of VM_Registers object
      asm.emitLAddrToc(S0, VM_Entrypoints.saveThreadStateInstructionsField.getOffset());
      asm.emitMTCTR(S0);
      asm.emitBCCTRL(); // call out of line machine code
      currentStackHeight -= 1;
    } else if (methodName == VM_MagicNames.threadSwitch) {
      peekToRegister(OBJECT_TYPE, currentStackHeight-1, T1); // T1 := address of VM_Registers of new thread
      peekToRegister(OBJECT_TYPE, currentStackHeight-2, T0); // T0 := address of previous VM_Thread object
      asm.emitLAddrToc(S0, VM_Entrypoints.threadSwitchInstructionsField.getOffset());
      asm.emitMTCTR(S0);
      asm.emitBCCTRL();
      currentStackHeight -= 2;
    } else if (methodName == VM_MagicNames.restoreHardwareExceptionState) {
      peekToRegister(OBJECT_TYPE, T0); // T0 := address of VM_Registers object
      asm.emitLAddrToc(S0, VM_Entrypoints.restoreHardwareExceptionStateInstructionsField.getOffset());
      asm.emitMTLR(S0);
      asm.emitBCLR(); // branch to out of line machine code (does not return)
    } else if (methodName == VM_MagicNames.returnToNewStack) {
      peekToRegister(OBJECT_TYPE, FP);                                  // FP := new stackframe
      asm.emitLAddr(S0, STACKFRAME_NEXT_INSTRUCTION_OFFSET, FP); // fetch...
      asm.emitMTLR(S0);                                         // ...return address
      asm.emitBCLR ();                                           // return to caller
    } else if (methodName == VM_MagicNames.dynamicBridgeTo) {
      if (VM.VerifyAssertions) VM._assert(klass.isDynamicBridge());
         
      // fetch parameter (address to branch to) into CT register
      //
      popToRegister(OBJECT_TYPE, T0); 
      asm.emitMTCTR(T0);

      // restore volatile and non-volatile registers
      // (note that these are only saved for "dynamic bridge" methods)
      //
      int offset = frameSize;

      // restore non-volatile and volatile fprs
      for (int i = LAST_NONVOLATILE_FPR; i >= FIRST_VOLATILE_FPR; --i)
        asm.emitLFD(i, offset -= BYTES_IN_DOUBLE, FP);
      
      // restore non-volatile gprs
      for (int i = LAST_NONVOLATILE_GPR; i >= FIRST_NONVOLATILE_GPR; --i)
        asm.emitLAddr(i, offset -= BYTES_IN_ADDRESS, FP);
            
      // skip saved thread-id, processor, and scratch registers
      offset -= (FIRST_NONVOLATILE_GPR - LAST_VOLATILE_GPR - 1) * BYTES_IN_ADDRESS;
         
      // restore volatile gprs
      for (int i = LAST_VOLATILE_GPR; i >= FIRST_VOLATILE_GPR; --i)
        asm.emitLAddr(i, offset -= BYTES_IN_ADDRESS, FP);
          
      // pop stackframe
      asm.emitLAddr(FP, 0, FP);
         
      // restore link register
      asm.emitLAddr(S0, STACKFRAME_NEXT_INSTRUCTION_OFFSET, FP);
      asm.emitMTLR(S0);

      asm.emitBCCTR(); // branch always, through count register
    } else if (methodName == VM_MagicNames.objectAsAddress         ||
               methodName == VM_MagicNames.addressAsByteArray      ||
               methodName == VM_MagicNames.addressAsIntArray       ||
               methodName == VM_MagicNames.addressAsObject         ||
               methodName == VM_MagicNames.addressAsObjectArray    ||
               methodName == VM_MagicNames.addressAsType           ||
               methodName == VM_MagicNames.objectAsType            ||
               methodName == VM_MagicNames.objectAsByteArray       ||
               methodName == VM_MagicNames.objectAsShortArray      ||
               methodName == VM_MagicNames.objectAsIntArray        ||
               methodName == VM_MagicNames.addressAsThread         ||
               methodName == VM_MagicNames.objectAsThread          ||
               methodName == VM_MagicNames.objectAsProcessor       ||
               methodName == VM_MagicNames.threadAsCollectorThread ||
               methodName == VM_MagicNames.addressAsRegisters      ||
               methodName == VM_MagicNames.addressAsStack          ) {
      // no-op (a type change, not a representation change)
    } else if (methodName == VM_MagicNames.floatAsIntBits) {
      assignRegisters(1, WORD_TYPE);
      copyByLocation(FLOAT_TYPE,
                              registerToLocation(sri0),
                              WORD_TYPE,
                              registerToLocation(sro0));
      cleanupRegisters();       
    } else if (methodName == VM_MagicNames.intBitsAsFloat) {
      assignRegisters(1, FLOAT_TYPE);
      copyByLocation(WORD_TYPE,
                              registerToLocation(sri0),
                              FLOAT_TYPE,
                              registerToLocation(sro0));
                                   
      cleanupRegisters();
    } else if  (methodName == VM_MagicNames.doubleAsLongBits) {
      assignRegisters(1, LONG_TYPE);
      copyByLocation(DOUBLE_TYPE,
                              registerToLocation(sri0),
                              LONG_TYPE,
                              registerToLocation(sro0));
                                   
      cleanupRegisters();
    } else if (methodName == VM_MagicNames.longBitsAsDouble) {
      assignRegisters(1, DOUBLE_TYPE);
      copyByLocation(LONG_TYPE,
                              registerToLocation(sri0),
                              DOUBLE_TYPE,
                              registerToLocation(sro0));
      cleanupRegisters();
    } else if (methodName == VM_MagicNames.getObjectType) {
      popToRegister(OBJECT_TYPE, T0);                   // get object pointer
      VM_ObjectModel.baselineEmitLoadTIB(asm,T0,T0);
      asm.emitLAddr(T0,  TIB_TYPE_INDEX << LOG_BYTES_IN_ADDRESS, T0); // get "type" field from type information block
      pushFromRegister(OBJECT_TYPE, T0);                   // *sp := type
    } else if (methodName == VM_MagicNames.getArrayLength) {
      popToRegister(OBJECT_TYPE, T0);                   // get object pointer
      asm.emitLInt(T0,  VM_ObjectModel.getArrayLengthOffset(), T0); // get array length field
      pushFromRegister(WORD_TYPE, T0);                   // *sp := length
    } else if (methodName == VM_MagicNames.sync) {
      asm.emitSYNC();
    } else if (methodName == VM_MagicNames.isync) {
      asm.emitISYNC();
    } else if (methodName == VM_MagicNames.dcbst) {
      popToRegister(OBJECT_TYPE, T0);    // address
      asm.emitDCBST(0, T0);
    } else if (methodName == VM_MagicNames.icbi) {
      popToRegister(OBJECT_TYPE, T0);    // address
      asm.emitICBI(0, T0);
    } else if (methodName == VM_MagicNames.wordToInt) {
      // no-op
      pop(OBJECT_TYPE);
      push(WORD_TYPE);
    } else if (methodName == VM_MagicNames.wordToAddress ||
               methodName == VM_MagicNames.wordToOffset ||
               methodName == VM_MagicNames.wordToObject ||
               methodName == VM_MagicNames.wordFromObject ||
               methodName == VM_MagicNames.wordToObjectReference ||
               methodName == VM_MagicNames.wordToExtent ||
               methodName == VM_MagicNames.wordToWord) {
      // no-op [pop object then push object]
    } else if (methodName == VM_MagicNames.wordToLong){
      popToRegister(OBJECT_TYPE, T1);
      asm.emitLVAL(T0,0);
      pushFromRegister(LONG_TYPE, T0);
    } else if (methodName == VM_MagicNames.wordFromInt ||
               methodName == VM_MagicNames.wordFromIntSignExtend) {
      if (VM.BuildFor64Addr) {
        popToRegister(WORD_TYPE, T0);
        popToRegister(OBJECT_TYPE, T0);
      } // else no-op
      pop(WORD_TYPE);
      push(OBJECT_TYPE);
    } else if (methodName == VM_MagicNames.wordFromIntZeroExtend) {
      if (VM.BuildFor64Addr) {
        if (VM.VerifyAssertions) VM._assert(NOT_REACHED);
      } // else no-op
      pop(WORD_TYPE);
      push(OBJECT_TYPE);
    } else if (methodName == VM_MagicNames.wordFromLong) {
      pop(LONG_TYPE);
      push(OBJECT_TYPE);
    } else if (methodName == VM_MagicNames.wordAdd) {
      if (VM.BuildFor64Addr && (methodToBeCalled.getParameterTypes()[0] == VM_TypeReference.Int)){
        popToRegister(WORD_TYPE, T0);
      } else {
        popToRegister(OBJECT_TYPE, T0);
      }
      popToRegister(OBJECT_TYPE, T1);
      asm.emitADD (T2, T1, T0);
      pushFromRegister(OBJECT_TYPE, T2);
    } else if (methodName == VM_MagicNames.wordSub ||
               methodName == VM_MagicNames.wordDiff) {
      if (VM.BuildFor64Addr && (methodToBeCalled.getParameterTypes()[0] == VM_TypeReference.Int)){
        popToRegister(WORD_TYPE, T0);
      } else {
        popToRegister(OBJECT_TYPE, T0);
      }
      popToRegister(OBJECT_TYPE, T1);
      asm.emitSUBFC (T2, T0, T1);
      pushFromRegister(OBJECT_TYPE, T2);
    } else if (methodName == VM_MagicNames.wordLT) {
      // unsigned comparison generating a boolean
      generateAddrComparison(false, LT);
    } else if (methodName == VM_MagicNames.wordLE) {
      // unsigned comparison generating a boolean
      generateAddrComparison(false, LE);
    } else if (methodName == VM_MagicNames.wordEQ) {
      // unsigned comparison generating a boolean
      generateAddrComparison(false, EQ);
    } else if (methodName == VM_MagicNames.wordNE) {
      // unsigned comparison generating a boolean
      generateAddrComparison(false, NE);
    } else if (methodName == VM_MagicNames.wordGT) {
      // unsigned comparison generating a boolean
      generateAddrComparison(false, GT);
    } else if (methodName == VM_MagicNames.wordGE) {
      // unsigned comparison generating a boolean
      generateAddrComparison(false, GE);
    } else if (methodName == VM_MagicNames.wordsLT) {
      generateAddrComparison(true, LT);
    } else if (methodName == VM_MagicNames.wordsLE) {
      generateAddrComparison(true, LE);
     } else if (methodName == VM_MagicNames.wordsGT) {
      generateAddrComparison(true, GT);
    } else if (methodName == VM_MagicNames.wordsGE) {
      generateAddrComparison(true, GE);
    } else if (methodName == VM_MagicNames.wordIsZero ||
               methodName == VM_MagicNames.wordIsNull) {
      // unsigned comparison generating a boolean
      generateAddrComparison(EQ, 0);
    } else if (methodName == VM_MagicNames.wordIsMax) {
      // unsigned comparison generating a boolean
      generateAddrComparison(EQ, -1);
    } else if (methodName == VM_MagicNames.wordZero ||
               methodName == VM_MagicNames.wordNull) {
      asm.emitLVAL (T0,  0);
      pushFromRegister(OBJECT_TYPE, T0);
    } else if (methodName == VM_MagicNames.wordOne) {
      asm.emitLVAL (T0,  1);
      pushFromRegister(OBJECT_TYPE, T0);
    } else if (methodName == VM_MagicNames.wordMax) {
      asm.emitLVAL (T0, -1);
      pushFromRegister(OBJECT_TYPE, T0);
    } else if (methodName == VM_MagicNames.wordAnd) {
      popToRegister(OBJECT_TYPE, T0);
      popToRegister(OBJECT_TYPE, T1);
      asm.emitAND(T2, T1, T0);
      pushFromRegister(OBJECT_TYPE, T2);
    } else if (methodName == VM_MagicNames.wordOr) {
      popToRegister(OBJECT_TYPE, T0);
      popToRegister(OBJECT_TYPE, T1);
      asm.emitOR (T2, T1, T0);
      pushFromRegister(OBJECT_TYPE, T2);
    } else if (methodName == VM_MagicNames.wordNot) {
      popToRegister(OBJECT_TYPE, T0);
      asm.emitLVAL(T1, -1);
      asm.emitXOR(T2, T1, T0);
      pushFromRegister(OBJECT_TYPE, T2);
    } else if (methodName == VM_MagicNames.wordXor) {
      popToRegister(OBJECT_TYPE, T0);
      popToRegister(OBJECT_TYPE, T1);
      asm.emitXOR(T2, T1, T0);
      pushFromRegister(OBJECT_TYPE, T2);
    } else if (methodName == VM_MagicNames.wordLsh) {
      popToRegister(WORD_TYPE, T0);
      popToRegister(OBJECT_TYPE, T1);
      if (VM.BuildFor32Addr)
        asm.emitSLW (T2, T1, T0);
      else
        asm.emitSLD (T2, T1, T0);
      pushFromRegister(OBJECT_TYPE, T2);
    } else if (methodName == VM_MagicNames.wordRshl) {
      popToRegister(WORD_TYPE, T0);
      popToRegister(OBJECT_TYPE, T1);
      if (VM.BuildFor32Addr)
        asm.emitSRW (T2, T1, T0);
      else
        asm.emitSRD (T2, T1, T0);
      pushFromRegister(OBJECT_TYPE, T2);
    } else if (methodName == VM_MagicNames.wordRsha) {
      popToRegister(WORD_TYPE, T0);
      popToRegister(OBJECT_TYPE, T1);
      if (VM.BuildFor32Addr)
        asm.emitSRAW (T2, T1, T0);
      else
        asm.emitSRAD (T2, T1, T0);
      pushFromRegister(OBJECT_TYPE, T2);
    } else {
      return false;
    }
    return true;
  }

  /** 
   * Generate code to invoke arbitrary method with arbitrary parameters/return value.
   * We generate inline code that calls "VM_OutOfLineMachineCode.reflectiveMethodInvokerInstructions"
   * which, at runtime, will create a new stackframe with an appropriately sized spill area
   * (but no register save area, locals, or operand stack), load up the specified
   * fpr's and gpr's, call the specified method, pop the stackframe, and return a value.
   */
  private void generateMethodInvocation () {
    // On entry the stack looks like this:
    //
    //                       hi-mem
    //            +-------------------------+    \
    //            |         code[]          |     |
    //            +-------------------------+     |
    //            |         gprs[]          |     |
    //            +-------------------------+     |- java operand stack
    //            |         fprs[]          |     |
    //            +-------------------------+     |
    //            |         spills[]        |     |
    //            +-------------------------+    /

    // fetch parameters and generate call to method invoker
    //
    asm.emitLAddrToc (S0, VM_Entrypoints.reflectiveMethodInvokerInstructionsField.getOffset());
    asm.emitMTCTR (S0);
    popToRegister(OBJECT_TYPE, T3);        // t3 := spills
    popToRegister(OBJECT_TYPE, T2);        // t2 := fprs
    popToRegister(OBJECT_TYPE, T1);        // t1 := gprs
    popToRegister(OBJECT_TYPE, T0);        // t0 := code
    asm.emitBCCTRL();
  }

  /** Emit code to perform an unsigned comparison on 2 address values
    * @param cc: condition to test
    */ 
  private void generateAddrComparison(boolean signed, int cc) {
    int r1 = popToAnyRegister(OBJECT_TYPE);
    int r0 = popToAnyRegister(OBJECT_TYPE);
    int r2 = getTempRegister(WORD_TYPE);
    asm.emitLVAL(r2,  1);
    if (VM.BuildFor32Addr) {
      if (signed)
        asm.emitCMP(r0, r1);
      else
        asm.emitCMPL(r0, r1);
    } else {
      if (signed)
        asm.emitCMPD(r0, r1);
      else
        asm.emitCMPLD(r0, r1);
    } 
    VM_ForwardReference fr = asm.emitForwardBC(cc);
    asm.emitLVAL(r2,  0);
    fr.resolve(asm);
    pushFromRegister(WORD_TYPE, r2);
  }


  /** Emit code to perform an unsigned comparison on 2 address values
    * @param cc: condition to test
    * @param constant: constant to test against
    */ 
  private void generateAddrComparison(int cc, int constant) {
    int r1 = getTempRegister(OBJECT_TYPE);
    asm.emitLVAL (r1,  constant);
    int r0 = popToAnyRegister(OBJECT_TYPE);
    int r2 = getTempRegister(WORD_TYPE);
    asm.emitLVAL(r2,  1);
    if (VM.BuildFor32Addr) {
      asm.emitCMPL(r0, r1);    // unsigned comparison
    } else {
      asm.emitCMPLD(r0, r1);   // unsigned comparison
    } 
    VM_ForwardReference fr = asm.emitForwardBC(cc);
    asm.emitLVAL(r2,  0);
    fr.resolve(asm);
    pushFromRegister(WORD_TYPE, r2);
  }


  /**
   * Indicate if specified VM_Magic method causes a frame to be created
   * on the runtime stack.
   * @param methodToBeCalled a <code>VM_MethodReference</code> value
   * @return true if method causes a stackframe to be created
   */
  public static boolean checkForActualCall(VM_MethodReference methodToBeCalled) {
    VM_Atom methodName = methodToBeCalled.getName();
    return methodName == VM_MagicNames.invokeMain                  ||
      methodName == VM_MagicNames.invokeClassInitializer      ||
      methodName == VM_MagicNames.invokeMethodReturningVoid   ||
      methodName == VM_MagicNames.invokeMethodReturningInt    ||
      methodName == VM_MagicNames.invokeMethodReturningLong   ||
      methodName == VM_MagicNames.invokeMethodReturningFloat  ||
      methodName == VM_MagicNames.invokeMethodReturningDouble ||
      methodName == VM_MagicNames.invokeMethodReturningObject ||
      methodName == VM_MagicNames.addressArrayCreate;
  }

  /** 
   * Generate code for "int VM_Magic.sysCallSigWait(int ip, int val0, int val1)
   * TODO: This code is almost dead....
   * 
   * @param rawParameterSize: number of bytes in parameters (not including IP)
   */
  private void generateSysCall1(int rawParametersSize) {
    int ipIndex = rawParametersSize >> LOG_BYTES_IN_STACKSLOT; // where to access IP parameter
    int linkageAreaSize   = rawParametersSize +         // values
      BYTES_IN_STACKSLOT +                              // saveJTOC
      (6 * BYTES_IN_STACKSLOT);                         // backlink + cr + lr + res + res + TOC

    peekToRegister(RETURN_ADDRESS_TYPE, 0, ipIndex);              // load desired IP. MUST do before we change FP value
    asm.emitMTCTR(0);                  // send to CTR so we can call it in a few instructions.

    if (VM.BuildFor32Addr) {
      asm.emitSTWU (FP,  -linkageAreaSize, FP);        // create linkage area
    } else {
      asm.emitSTDU (FP,  -linkageAreaSize, FP);        // create linkage area
    }
    asm.emitSTAddr(JTOC, linkageAreaSize-BYTES_IN_STACKSLOT, FP);      // save JTOC

    asm.emitLAddrToc(S0, VM_Entrypoints.the_boot_recordField.getOffset()); // load sysTOC into JTOC
    asm.emitLAddr(JTOC, VM_Entrypoints.sysTOCField.getOffset(), S0);

    asm.emitBCCTRL();                             // call the desired function

    asm.emitLAddr(JTOC, linkageAreaSize - BYTES_IN_STACKSLOT, FP);    // restore JTOC
    asm.emitADDI (FP, linkageAreaSize, FP);        // remove linkage area
  }

  /** 
   * Generate call and return sequence to invoke a C function through the
   * boot record field specificed by target. 
   * Caller handles parameter passing and expression stack 
   * (setting up args, pushing return, adjusting stack height).
   *
   * <pre>
   *  Create a linkage area that's compatible with RS6000 "C" calling conventions.
   * Just before the call, the stack looks like this:
   *
   *                     hi-mem
   *            +-------------------------+  . . . . . . . .
   *            |          ...            |                  \
   *            +-------------------------+                   |
   *            |          ...            |    \              |
   *            +-------------------------+     |             |
   *            |       (int val0)        |     |  java       |- java
   *            +-------------------------+     |-  operand   |   stack
   *            |       (int val1)        |     |    stack    |    frame
   *            +-------------------------+     |             |
   *            |          ...            |     |             |
   *            +-------------------------+     |             |
   *            |      (int valN-1)       |     |             |
   *            +-------------------------+    /              |
   *            |          ...            |                   |
   *            +-------------------------+                   |
   *            |                         | <-- spot for this frame's callee's return address
   *            +-------------------------+                   |
   *            |          MI             | <-- this frame's method id
   *            +-------------------------+                   |
   *            |       saved FP          | <-- this frame's caller's frame
   *            +-------------------------+  . . . . . . . . /
   *            |      saved JTOC         |
   *            +-------------------------+  . . . . . . . . . . . . . .
   *            | parameterN-1 save area  | +  \                         \
   *            +-------------------------+     |                         |
   *            |          ...            | +   |                         |
   *            +-------------------------+     |- register save area for |
   *            |  parameter1 save area   | +   |    use by callee        |
   *            +-------------------------+     |                         |
   *            |  parameter0 save area   | +  /                          |  rs6000
   *            +-------------------------+                               |-  linkage
   *        +20 |       TOC save area     | +                             |    area
   *            +-------------------------+                               |
   *        +16 |       (reserved)        | -    + == used by callee      |
   *            +-------------------------+      - == ignored by callee   |
   *        +12 |       (reserved)        | -                             |
   *            +-------------------------+                               |
   *         +8 |       LR save area      | +                             |
   *            +-------------------------+                               |
   *         +4 |       CR save area      | +                             |
   *            +-------------------------+                               |
   *  FP ->  +0 |       (backlink)        | -                             |
   *            +-------------------------+  . . . . . . . . . . . . . . /
   *
   * Notes:
   * 1. parameters are according to host OS calling convention.
   * 2. space is also reserved on the stack for use by callee
   *    as parameter save area
   * 3. parameters are pushed on the java operand stack left to right
   *    java conventions) but if callee saves them, they will
   *    appear in the parameter save area right to left (C conventions)
   */
  private void generateSysCall(int parametersSize, VM_Field target) {
    int linkageAreaSize   = parametersSize + BYTES_IN_STACKSLOT + (6 * BYTES_IN_STACKSLOT);

    if (VM.BuildFor32Addr) {
      asm.emitSTWU (FP,  -linkageAreaSize, FP);        // create linkage area
    } else {
      asm.emitSTDU (FP,  -linkageAreaSize, FP);        // create linkage area
    }
    asm.emitSTAddr(JTOC, linkageAreaSize-BYTES_IN_STACKSLOT, FP);      // save JTOC

    // acquire toc and ip from bootrecord
    asm.emitLAddrToc(S0, VM_Entrypoints.the_boot_recordField.getOffset());
    asm.emitLAddr(JTOC, VM_Entrypoints.sysTOCField.getOffset(), S0);
    asm.emitLAddr(0, target.getOffset(), S0);

    // call it
    asm.emitMTCTR(0);
    asm.emitBCCTRL(); 

    // cleanup
    asm.emitLAddr(JTOC, linkageAreaSize - BYTES_IN_STACKSLOT, FP);    // restore JTOC
    asm.emitADDI (FP, linkageAreaSize, FP);        // remove linkage area
  }


  private void emitDynamicLinkingSequence(int reg, VM_MemberReference ref, boolean couldBeZero) {
    int memberId = ref.getId();
    int memberOffset = memberId << LOG_BYTES_IN_INT;
    int tableOffset = VM_Entrypoints.memberOffsetsField.getOffset();
    if (couldBeZero) {
      int resolverOffset = VM_Entrypoints.resolveMemberMethod.getOffset();
      int label = asm.getMachineCodeIndex();
      
      // load offset table
      asm.emitLAddrToc (reg, tableOffset);
      asm.emitLIntOffset(reg, reg, memberOffset);

      // test for non-zero offset and branch around call to resolver
      asm.emitCMPI (reg, NEEDS_DYNAMIC_LINK);         // reg ?= NEEDS_DYNAMIC_LINK, is field's class loaded?
      VM_ForwardReference fr1 = asm.emitForwardBC(NE);
      asm.emitLAddrToc (T0, resolverOffset);
      asm.emitMTCTR (T0);
      asm.emitLVAL(T0, memberId);            // id of member we are resolving
      asm.emitBCCTRL ();                              // link; will throw exception if link error
      asm.emitB    (label);                   // go back and try again
      fr1.resolve(asm);
    } else {
      // load offset table
      asm.emitLAddrToc (reg, tableOffset);
      asm.emitLIntOffset(reg, reg, memberOffset);
    }
  }

  /**
   * increment an edge counter.  
   * @param counters register containing base of counter array
   * @param scratch scratch register
   * @param counterIdx index of counter to increment
   */
  private final void incEdgeCounter(int counters, int scratch, int counterIdx) {
    asm.emitLInt     (scratch, counterIdx<<2, counters);
    asm.emitADDI    (scratch, 1, scratch);
    asm.emitRLWINM (scratch, scratch, 0, 1, 31);
    asm.emitSTW    (scratch, counterIdx<<2, counters);
  }

  private final void incEdgeCounterIdx(int counters, int scratch, int base, int counterIdx) {
    asm.emitADDI     (counters, base<<2, counters);
    asm.emitLIntX      (scratch, counterIdx, counters);
    asm.emitADDI     (scratch, 1, scratch);
    asm.emitRLWINM  (scratch, scratch, 0, 1, 31);
    asm.emitSTWX     (scratch, counterIdx, counters);
  }    
  /**
   * @param whereFrom is this thread switch from a PROLOGUE, BACKEDGE, or EPILOGUE?
   */
  private void genThreadSwitchTest (int whereFrom) {
    if (isInterruptible) {
      VM_ForwardReference fr;
      // yield if takeYieldpoint is non-zero.
      asm.emitLInt(S0, VM_Entrypoints.takeYieldpointField.getOffset(), PROCESSOR_REGISTER);
      asm.emitCMPI(S0, 0); 
      if (whereFrom == VM_Thread.PROLOGUE) {
        // Take yieldpoint if yieldpoint flag is non-zero (either 1 or -1)
        fr = asm.emitForwardBC(EQ);
        asm.emitLAddr(S0, VM_Entrypoints.yieldpointFromPrologueMethod.getOffset(), JTOC);
      } else if (whereFrom == VM_Thread.BACKEDGE) {
        // Take yieldpoint if yieldpoint flag is >0
        fr = asm.emitForwardBC(LE);
        asm.emitLAddr(S0, VM_Entrypoints.yieldpointFromBackedgeMethod.getOffset(), JTOC);
      } else { // EPILOGUE
        // Take yieldpoint if yieldpoint flag is non-zero (either 1 or -1)
        fr = asm.emitForwardBC(EQ);
        asm.emitLAddr(S0, VM_Entrypoints.yieldpointFromEpilogueMethod.getOffset(), JTOC);
      }
      asm.emitMTCTR(S0);
      asm.emitBCCTRL();
      fr.resolve(asm);

      //-#if RVM_WITH_ADAPTIVE_SYSTEM
      if (options.INVOCATION_COUNTERS) {
        int id = compiledMethod.getId();
        com.ibm.JikesRVM.adaptive.VM_InvocationCounts.allocateCounter(id);
        asm.emitLAddrToc (T0, VM_Entrypoints.invocationCountsField.getOffset());
        asm.emitLVAL(T1, compiledMethod.getId() << LOG_BYTES_IN_INT);
        asm.emitLIntX   (T2, T0, T1);                       
        asm.emitADDICr  (T2, T2, -1);
        asm.emitSTWX  (T2, T0, T1);
        VM_ForwardReference fr2 = asm.emitForwardBC(asm.GT);
        asm.emitLAddrToc (T0, VM_Entrypoints.invocationCounterTrippedMethod.getOffset());
        asm.emitMTCTR(T0);
        asm.emitLVAL(T0, id);
        asm.emitBCCTRL();
        fr2.resolve(asm);
      }
      //-#endif
    }
  }

  private void genLoadLocalToRegister(byte type, int localIndex,
                                      int register) {
    int l = getLocalLocation(type, localIndex);
    copyByLocation(type, l, type, register);
  }

  private void genStoreLocalFromRegister(byte type, int localIndex,
                                         int register) {
    int l = getLocalLocation(type, localIndex);
    copyByLocation(type, register, type, l);
  }

  private void genStoreLocalFromMemory(byte type, int localIndex,
                                       int fpOffset) {
    int l = getLocalLocation(type, localIndex);
    copyByLocation(type, offsetToLocation(fpOffset), type, l);
  }

  // parameter stuff //

  /** Stores parameters from registers into local variables of current
   * method.
   */
  private void genMoveParametersToLocals () {
    // AIX computation will differ
    spillOffset = getFrameSize(method) + STACKFRAME_HEADER_SIZE;
    int gp = FIRST_VOLATILE_GPR;
    int fp = FIRST_VOLATILE_FPR;
    int localIndex = 0;

    // Don't let parameter registers be used as temporary
    // registers. This means that S0, S1, and SF0  could be stomped on
    // in the subsequent loop
    paramRegsInUse = true;
    
    if (!method.isStatic()) {
      if (gp > LAST_VOLATILE_GPR)
        genUnspillWord(OBJECT_TYPE, localIndex++);
      else
        genStoreLocalFromRegister(OBJECT_TYPE, localIndex++, gp++);
    }
    VM_TypeReference [] types = method.getParameterTypes();
    for (int i=0; i<types.length; i++, localIndex++) {
      VM_TypeReference t = types[i];
      if (t.isLongType()) {
        if (gp == LAST_VOLATILE_GPR) {
          // Striding
          asm.emitMR(S0, gp);
          asm.emitLWZ(S1, spillOffset, FP);
          genStoreLocalFromRegister(LONG_TYPE, localIndex, S0);
          spillOffset += 4;
        } else if (gp > LAST_VOLATILE_GPR) {
          genUnspillDoubleword(LONG_TYPE, localIndex);
        } else {
          genStoreLocalFromRegister(LONG_TYPE, localIndex, gp);
        }
        gp += 2;
        localIndex +=1;
      } else if (t.isFloatType()) {
        if (fp > LAST_VOLATILE_FPR)
          genUnspillWord(FLOAT_TYPE, localIndex);
        else
          genStoreLocalFromRegister(FLOAT_TYPE, localIndex, fp++);
      } else if (t.isDoubleType()) {
        if (fp > LAST_VOLATILE_FPR)
          genUnspillDoubleword(DOUBLE_TYPE, localIndex);
        else
          genStoreLocalFromRegister(DOUBLE_TYPE, localIndex, fp++);
        localIndex += 1;
      } else { // t is object, int, short, char, byte, or boolean
        if (gp > LAST_VOLATILE_GPR)
          genUnspillWord(WORD_TYPE, localIndex);
        else
          genStoreLocalFromRegister(WORD_TYPE, localIndex, gp++);
      }
    }

  }

  // 
  /**
   * Loads parameters into registers before calling method.
   *
   * @param hasImplicitThisArg true if method is not static
   * @param thisArgAlreadyMoved true if 'this' is already in T0
   * @param m <code>VM_MethodReference</code>of method to call
   */
  void genMoveParametersToRegisters (boolean hasImplicitThisArg,
                                     boolean thisArgAlreadyMoved,
                                     VM_MethodReference m) {
    spillOffset = STACKFRAME_HEADER_SIZE;
    int stackOffset = m.getParameterWords();
    int stackIndex = currentStackHeight - stackOffset;

    // Remember where the bottom of the argument list is so we know
    // where to push the return value to after the invocation.
    resultStackIndex = stackIndex;

    int gp = FIRST_VOLATILE_GPR;
    int fp = FIRST_VOLATILE_FPR;
    
    VM_TypeReference [] types = m.getParameterTypes();

    // Don't let parameter registers be used as temporary
    // registers. This means that utility routines might use S0, S1,
    // or SF0, so this method should *not* rely on them remaining
    // valid after any method call.
    
    paramRegsInUse = true;
    
    // Arguments are on the stack in the reverse order from what would
    // be most convenient for us: things further down the stack should
    // be in lower ordered registers.

    
    if (hasImplicitThisArg) {
      if (thisArgAlreadyMoved)
        gp += 1;
      else
        peekToRegister(OBJECT_TYPE, stackIndex-1, gp++);

      resultStackIndex -= 1;
    }

    //-#if RVM_WITH_QUICK_COMPILER_MOVE_ELIMINATION
//     int wordsKilled = m.getParameterWords() + (hasImplicitThisArg?1:0);
//      markStackDead(currentStackHeight-wordsKilled, wordsKilled);
    //-#endif
    
    for (int i=0; i < types.length; i++) {
      VM_TypeReference t = types[i];
    //-#if RVM_WITH_QUICK_COMPILER_MOVE_ELIMINATION
      if (isGCPoint) {
        saveSlotInSafeLocation(stackIndexToSlotIndex(stackIndex),
                               resultStackIndex);
      }
    //-#endif
      if (t.isLongType()) {
        if (gp == LAST_VOLATILE_GPR) {
          // Striding
          peekToRegister(LONG_TYPE, stackIndex, S0);
          asm.emitMR(gp, S0);
          asm.emitSTW(S1, spillOffset, FP);
          spillOffset += 4;
          gp += 2;
          stackIndex += 2;
        }
        else if (gp > LAST_VOLATILE_GPR) {
          genSpillDoubleword(LONG_TYPE, stackIndex);
          stackIndex += 2;
        }
        else {
          peekToRegister(LONG_TYPE, stackIndex, gp);
          gp += 2;
          stackIndex += 2;
        }
      } else if (t.isFloatType()) {
        if (fp > LAST_VOLATILE_FPR)
          genSpillWord(FLOAT_TYPE, stackIndex);
        else
          peekToRegister(FLOAT_TYPE, stackIndex, fp++);
        stackIndex += 1;
      } else if (t.isDoubleType()) {
        if (fp > LAST_VOLATILE_FPR)
          genSpillDoubleword(DOUBLE_TYPE, stackIndex);
        else
          peekToRegister(DOUBLE_TYPE, stackIndex, fp++);
        stackIndex += 2;
      } else { // t is object, int, short, char, byte, or boolean
        if (gp > LAST_VOLATILE_GPR)
          genSpillWord(WORD_TYPE, stackIndex);
        else
          peekToRegister(WORD_TYPE, stackIndex, gp++);
        stackIndex += 1;
      }
    }

    currentStackHeight = resultStackIndex;
    
    // XXX CJH TODO: what if no parameters but push result on stack?
    // Must ensure anything currently in those stack slots are saved
    // in a safe slot as well.

    if (isGCPoint && !GCLocationsSet) {
      addLocationsToRefMap(biStart);
    }

  }


  /**
   * Pushes return value of method  from register to operand stack.
   * @param hasImplicitThisArg true if method is not static
   * @param m the <code>VM_MethodReference</code> to the method
   */
  void genPopParametersAndPushReturnValue (boolean hasImplicitThisArg,
                                           VM_MethodReference m) {

    if (VM.VerifyAssertions) VM._assert(currentStackHeight >= 0 &&
                                        currentStackHeight <= maxStackHeight);
    VM_TypeReference t = m.getReturnType();
    if (t.isVoidType()) {       // do nothing
      assignStackLocations();
      return;
    }
    

    byte type = VM_BuildBB.getJavaStackType(t);

    //-#if RVM_WITH_QUICK_COMPILER_MOVE_ELIMINATION
    recordCalculation(0, currentStackHeight, type, biStart);
    //-#endif

    stack[currentStackHeight++] = type;
    if (VM_BuildBB.getJavaStackSize(type) > 1)
      stack[currentStackHeight++] = type;

    assignStackLocations();

    int stackLocation;
    int register;
    if (t.isLongType()) {
      stackLocation=getStackLocation(LONG_TYPE, resultStackIndex);
      register = FIRST_VOLATILE_GPR;
      copyByLocation(LONG_TYPE, registerToLocation(register),
                     LONG_TYPE, stackLocation);
    } else if (t.isFloatType()) {
      stackLocation=getStackLocation(FLOAT_TYPE, resultStackIndex);
      register = FIRST_VOLATILE_FPR;
      copyByLocation(FLOAT_TYPE, registerToLocation(register),
                     FLOAT_TYPE, stackLocation);
    } else if (t.isDoubleType()) {
      stackLocation=getStackLocation(DOUBLE_TYPE, resultStackIndex);
      register = FIRST_VOLATILE_FPR;
      copyByLocation(DOUBLE_TYPE, registerToLocation(register),
                     DOUBLE_TYPE, stackLocation);
    } else { // t is object, int, short, char, byte, or boolean
      stackLocation=getStackLocation(WORD_TYPE, resultStackIndex);
      register = FIRST_VOLATILE_GPR;
      copyByLocation(WORD_TYPE, registerToLocation(register),
                     WORD_TYPE, stackLocation);
    }


    if (VM.VerifyAssertions) VM._assert(currentStackHeight >= 0 &&
                                        currentStackHeight <= maxStackHeight);

  }

  private void genSpillWord (byte type) {
    popToMemory(type, spillOffset);
    spillOffset += 4;
  }
     
  private void genSpillDoubleword (byte type) {
    popToMemory(type, spillOffset);
    spillOffset += 8;
  }
               
  private void genSpillWord (byte type, int peekHeight) {
    peekToMemory(type, peekHeight, spillOffset);
    spillOffset += 4;
  }
     
  private void genSpillDoubleword (byte type, int peekHeight) {
    peekToMemory(type, peekHeight, spillOffset);
    spillOffset += 8;
  }
               
  private void genUnspillWord (byte type, int localIndex) {
    genStoreLocalFromMemory(type, localIndex, spillOffset);
    spillOffset += 4;
  }
                      
  private void genUnspillDoubleword (byte type, int localIndex) {
    genStoreLocalFromMemory(type, localIndex, spillOffset);
    spillOffset += 8;
  }

  // Begin platform-dependent code

  //----------------//
  // more interface //
  //----------------//
  
  
  /////////////////////////////////////////////////////////////////////////
 //    position of spill area within method's stackframe.
  /* this is the original with bug fixes */
  static int getMaxSpillOffset (VM_NormalMethod m)  throws UninterruptiblePragma {
    int params = m.getOperandWords()<<2; // maximum parameter area
    int spill  = params - (MIN_PARAM_REGISTERS << 2);
    if (spill < 0) spill = 0;
    return STACKFRAME_HEADER_SIZE + spill - 4;
  }

  // position of a word of memory that can be used for various
  // temporary manipulations. (TODO: not needed anymore? Does Jikes
  // already provide this in the JTOC?)
  static int getScratchOffset(VM_NormalMethod m)  throws UninterruptiblePragma {
    return  getMaxSpillOffset(m)+4;
  }

// position of operand stack within method's stackframe.
  static int getStackBottomOffset (VM_NormalMethod m)  throws UninterruptiblePragma {
    int stack = m.getOperandWords()<<2; // maximum stack size
    return (getScratchOffset(m)+4) + stack; 
  }

  // position of locals within method's stackframe.
  static int getFirstLocalOffset (VM_NormalMethod m)  throws UninterruptiblePragma {
    int locals = m.getLocalWords()<<2;       // input param words + pure locals
    return getStackBottomOffset(m) + 8 + locals; // bottom-most local
  }
  
  // position of caller's saved non-volatiles within method's
  // stackframe.
  // We allocate enough space to save all of the registers even though
  // we may not actually use them all -- some day this could be optimized
  static int getCallerSaveOffset (VM_NormalMethod m)  throws UninterruptiblePragma {
     int offset = getFirstLocalOffset(m) + 4 +
       ((1+NUM_GPRS - (LAST_VOLATILE_GPR-FIRST_VOLATILE_GPR)) << LOG_BYTES_IN_ADDRESS) +
       ((1+NUM_FPRS - (LAST_VOLATILE_FPR-FIRST_VOLATILE_FPR)) << LOG_BYTES_IN_DOUBLE);
      return (offset + 7) & ~7;  // align pointer for doubles
  }
  
  // position of area to save THIS ptr for non-static methods.
  static int getThisPtrSaveAreaOffset (VM_NormalMethod m) throws UninterruptiblePragma {
     return getCallerSaveOffset(m) + 4;
  }
  
  
  // position of current exception object within method's stackframe.
  static int getExceptionObjectOffset (VM_NormalMethod m)  throws UninterruptiblePragma {
     return getThisPtrSaveAreaOffset(m) + 4;
  }
  
  // size of method's stackframe.
  static int getFrameSize (VM_NormalMethod m)  throws UninterruptiblePragma {
    int size;
    size = getExceptionObjectOffset(m) + 4;
    if (m.getDeclaringClass().isDynamicBridge()) {
      size += (LAST_NONVOLATILE_FPR - FIRST_VOLATILE_FPR + 1) * 8;
      size += (LAST_NONVOLATILE_GPR - FIRST_VOLATILE_GPR + 1) * 4;
    }
    if (VM.BuildFor32Addr) {
      size = VM_Memory.alignUp(size , STACKFRAME_ALIGNMENT);
    }
    return size;
  }



  /**
   * Records in the GC map the locations of any reference objects in
  the locals or stack. The GC map already knows which slots are
  references, but it doesn't know if the slot will be stored in a
  register or memory. Also, if move elimination is being performed,
  it's possible a stack entry never actually had a value written to
  it. This routine will pass this information to the map.
   *
   * @param biStart the bytecode index of this GC point
   */
  void addLocationsToRefMap(int biStart) {


    for (int i=0; i < nLocalWords; i++) {
      //-#if RVM_WITH_QUICK_COMPILER_MOVE_ELIMINATION
      if (slotStatus[localIndexToSlotIndex(i)] == STATUS_PENDING) {
        gcMapLocalRegs[i] = EMPTY_SLOT;
        continue;
      }
      //-#endif
      int l = localWordLocation[i];
      if (isRegister(l))
        gcMapLocalRegs[i] = (byte)locationToRegister(l);
      else
        gcMapLocalRegs[i] = 0;
    }
    
    for (int i=0; i < nStackWords; i++) {
      //-#if RVM_WITH_QUICK_COMPILER_MOVE_ELIMINATION
      if ((slotStatus[stackIndexToSlotIndex(i)] == STATUS_PENDING ||
           i >= currentStackHeight)) {
        gcMapStackRegs[i] = EMPTY_SLOT;
        continue;
      }
      //-#endif
      if (i < currentStackHeight &&
          isReferenceType(stack[i]) && isRegister(stackLocations[i]))
        gcMapStackRegs[i] = (byte)locationToRegister(stackLocations[i]);
      else
        gcMapStackRegs[i] = 0;
    }

    refMaps.setLocationMap(biStart, gcMapStackRegs, gcMapLocalRegs);
    GCLocationsSet=true;
    }

  /*
   * The target-specific VM_Compiler class must implement the 
   * following (lengthy) list of abstract methods.
   * Porting the baseline compiler to a new platform
   * mainly entails implementing all of these methods.
   */

  /**
   * Notify compiler that we are starting code gen for a bytecode.
   */
  
  protected void starting_bytecode() {

    // The compiler framework simply steps through the byte array in
    // order. But, we have come across class files that contain blocks
    // of completely unreachable instructions. Since there is no valid
    // stack information for those blocks the quick compiler can't
    // generate code for them (unlike the baseline compiler).
    
    if (currentBlock != byteToBlockMap[biStart] &&
        stackSnapshots[biStart] == null) {
      unreachableBytecode = true;
      return;
    }
    unreachableBytecode = false;
    if (byteToBlockMap[biStart] <= 0) {
      throw new VM_QuickCompilerException("Unreachable bytecode");
    }

    
    if (currentBlock != byteToBlockMap[biStart]) {
      starting_block();         
    }

    isGCPoint = refMaps.isGCPoint(biStart);
    if (isGCPoint) {
      //-#if RVM_WITH_QUICK_COMPILER_MOVE_ELIMINATION
      invalidateDeadStackValues();
      //-#endif

      lastGCIndex = biStart;
      GCLocationsSet=false;
    }

    paramRegsInUse = false;
  }
  

  /**
   * Notify compiler that we are ending code gen for a bytecode
   */
  protected void ending_bytecode() {
    if (unreachableBytecode) return;

    releaseTempRegisters();

    markStackDead();
    
    //-#if RVM_WITH_QUICK_COMPILER_MOVE_ELIMINATION
    if (isGCPoint && !GCLocationsSet) {
      addLocationsToRefMap(biStart);
    }
    //-#endif
    
    if (basicBlocks[currentBlock].getEnd() == biStart)
      ending_block();
  }
    
  /**
   * Notify compiler that we are starting code gen for a new block
   */
  private void starting_block() {
    //-#if RVM_WITH_QUICK_COMPILER_COUNT_MR
    VM_Assembler.resetMRCount();
    //-#endif 
    //-#if RVM_WITH_QUICK_COMPILER_DYNAMIC_BLOCK_COUNT
    genBlockTally(asm);
    //-#endif
    currentBlock = byteToBlockMap[biStart];
    currentStackHeight =  stackSnapshots[biStart].length;
    System.arraycopy(stackSnapshots[biStart], 0, stack, 0,
                     currentStackHeight);
    assignStackLocations();

    lastGCIndex = -1;
    
    if (exceptions != null) {
      int[] tryHandlerPC = exceptions.getHandlerPC();
      int tryLength = tryHandlerPC.length;
      for (int i=0; i<tryLength; i++) {
          
        if (biStart == tryHandlerPC[i]) {
          // VM_QuickExceptionDeliverer has left the exception object at
          // an offset of firstScratchOffset from the FP. Move it into the
          // register assigned to stack #0.
          int r = locationToRegister(getStackLocation(OBJECT_TYPE, 0));
          asm.emitLAddr(r, getExceptionObjectOffset(method), FP);
          break;
        }
      }
    }
    
    //-#if RVM_WITH_QUICK_COMPILER_MOVE_ELIMINATION
    moveElimStartBlock();
    //-#endif 
  }
  
  /**
   * Notify compiler that we are ending code gen for a new block
   */
  private void ending_block() {
    //-#if RVM_WITH_QUICK_COMPILER_MOVE_ELIMINATION
    moveElimFinishBlock(currentStackHeight);
    //-#endif
    //-#if RVM_WITH_QUICK_COMPILER_COUNT_MR
    VM.sysWrite(iBlock);
    VM.sysWrite(": ");
    VM.sysWriteln( VM_Assembler.getMRCount());
    iBlock += 1;
    //-#endif 
  }
  
  /**
   * Emit the prologue for the method
   */
  protected void emit_prologue() {
    genPrologue();
  }

  /**
   * Emit the code for a threadswitch tests (aka a yieldpoint).
   * @param whereFrom is this thread switch from a PROLOGUE, BACKEDGE, or EPILOGUE?
   */
  protected void emit_threadSwitchTest(int whereFrom) {
    genThreadSwitchTest(whereFrom);
  }

//   //-#if RVM_WITH_OSR
//   protected void emit_threadSwitch(int whereFrom)
//   protected void emit_deferred_prologue()
//   //-#endif

  /**
   * Emit the code to implement the spcified magic.
   * @param magicMethod desired magic
   */
  protected boolean emit_Magic(VM_MethodReference magicMethod) {
    if (unreachableBytecode) return true;
    return generateInlineCode(magicMethod);
  }


  /*
   * Loading constants
   */


  /**
   * Emit code to load the null constant.
   */
  protected void emit_aconst_null() {
    if (unreachableBytecode) return;
    assignRegisters(0,  OBJECT_TYPE);
    //-#if RVM_WITH_QUICK_COMPILER_MOVE_ELIMINATION
    //-#if RVM_WITH_QUICK_COMPILER_CONSTANT_FOLDING
    //-#else
    asm.emitLVAL(sro0,  0);     
    //-#endif
    returnValue = createValue(VALUE_TYPE_CONSTANT, 0);
    cleanupRegisters();
    //-#else
    asm.emitLVAL(sro0,  0);
    cleanupRegisters();
    //-#endif
  }
  
  /**
   * Emit code to load an int constant.
   * @param val the int constant to load
   */
  protected void emit_iconst(int val) {
    if (unreachableBytecode) return;
    assignRegisters(0,  WORD_TYPE);
    //-#if RVM_WITH_QUICK_COMPILER_MOVE_ELIMINATION
    //-#if RVM_WITH_QUICK_COMPILER_CONSTANT_FOLDING
    //-#else
    //asm.emitLVAL(sro0,  val);   // XXX CJH remove?
    //-#endif
    returnValue = createValue(VALUE_TYPE_CONSTANT, val);
    cleanupRegisters();
    //-#else
    asm.emitLVAL(sro0,  val);
    cleanupRegisters();
    //-#endif
  }

  /**
   * Emit code to load a long constant
   * @param val the lower 32 bits of long constant (upper32 are 0).
   */
  protected void emit_lconst(int val) {
    if (unreachableBytecode) return;
    assignRegisters(0,  LONG_TYPE);
    int flw0 = getTempRegister(DOUBLE_TYPE);
    if (val == 0) {
      asm.emitLFStoc(flw0, VM_Entrypoints.zeroFloatField.getOffset(), S0);
    } else {
      if (VM.VerifyAssertions) VM._assert(val == 1);
      asm.emitLFDtoc(flw0, VM_Entrypoints.longOneField.getOffset(), S0);
    }
    copyByLocation(DOUBLE_TYPE, registerToLocation(flw0),
                   LONG_TYPE, registerToLocation(sro0));
    cleanupRegisters();
  }

  /**
   * Emit code to load 0.0f
   */
  protected void emit_fconst_0() {
    if (unreachableBytecode) return;
    assignRegisters(0,  FLOAT_TYPE);
    asm.emitLFStoc(sro0, VM_Entrypoints.zeroFloatField.getOffset(), S0);
    cleanupRegisters();
  }

  /**
   * Emit code to load 1.0f
   */
  protected void emit_fconst_1() {
    if (unreachableBytecode) return;
    assignRegisters(0,  FLOAT_TYPE);
    asm.emitLFStoc(sro0, VM_Entrypoints.oneFloatField.getOffset(), S0);
    cleanupRegisters();
  }

  /**
   * Emit code to load 2.0f
   */
  protected void emit_fconst_2() {
    if (unreachableBytecode) return;
    assignRegisters(0,  FLOAT_TYPE);
    asm.emitLFStoc(sro0, VM_Entrypoints.twoFloatField.getOffset(), S0);
    cleanupRegisters();
  }

  /**
   * Emit code to load 0.0d
   */
  protected void emit_dconst_0() {
    if (unreachableBytecode) return;
    assignRegisters(0,  DOUBLE_TYPE);
    asm.emitLFStoc(sro0, VM_Entrypoints.zeroFloatField.getOffset(), S0);
    cleanupRegisters();
  }

  /**
   * Emit code to load 1.0d
   */
  protected void emit_dconst_1() {
    if (unreachableBytecode) return;
    assignRegisters(0,  DOUBLE_TYPE);
    asm.emitLFStoc(sro0, VM_Entrypoints.oneFloatField.getOffset(), S0);
    cleanupRegisters();
  }

  /**
   * Emit code to load a 32 bit constant
   * @param offset JTOC offset of the constant 
   */
  protected void emit_ldc(int offset) {
    if (unreachableBytecode) return;
    if (VM.BuildFor64Addr) {
      if (VM.VerifyAssertions) VM._assert(NOT_REACHED);
    }
    byte slotDescription = VM_Statics.getSlotDescription(offset>>LOG_BYTES_IN_INT);
    switch (slotDescription) {
    case VM_Statics.FLOAT_LITERAL:
      assignRegisters(0,  FLOAT_TYPE);
      asm.emitLFStoc(sro0,  offset, S0);
      break;
    case VM_Statics.STRING_LITERAL:
      assignRegisters(0,  OBJECT_TYPE);
      asm.emitLAddrToc(sro0,  offset);
      break;
    default:
      assignRegisters(0,  WORD_TYPE);
      asm.emitLIntToc(sro0,  offset);
      break;
    }
    cleanupRegisters();
  }

  /**
   * Emit code to load a 64 bit constant
   * @param offset JTOC offset of the constant 
   */
  protected void emit_ldc2(int offset) {
    if (unreachableBytecode) return;
    // XXX CJH TODO!
     if (VM.BuildFor64Addr) {
       if (VM.VerifyAssertions) VM._assert(NOT_REACHED);

     }
    byte pushType;
    if (VM_Statics.getSlotDescription(offset>>LOG_BYTES_IN_INT) == VM_Statics.DOUBLE_LITERAL)
      pushType = DOUBLE_TYPE;
    else
      pushType = LONG_TYPE;
    
    assignRegisters(0,  pushType);
    int flw0 = getTempRegister(DOUBLE_TYPE);
    asm.emitLFDtoc(flw0,  offset, S0);
    copyByLocation(DOUBLE_TYPE, registerToLocation(flw0),
                   pushType, registerToLocation(sro0));
    cleanupRegisters();
  }

//   /*
//    * loading local variables
//    */


  /**
   * Emit code to load an int local variable
   * @param index the local index to load
   */
  protected void emit_iload(int index) {
    if (unreachableBytecode) return;
    //-#if RVM_WITH_QUICK_COMPILER_MOVE_ELIMINATION
    recordLoad(index, currentStackHeight, WORD_TYPE, biStart);
    //-#endif
    assignRegisters(0, WORD_TYPE, false);
    cleanupRegisters();
  }

  /**
   * Emit code to load a long local variable
   * @param index the local index to load
   */
  protected void emit_lload(int index) {
    if (unreachableBytecode) return;
    //-#if RVM_WITH_QUICK_COMPILER_MOVE_ELIMINATION
    recordLoad(index, currentStackHeight, LONG_TYPE, biStart);
    //-#endif
    assignRegisters(0, LONG_TYPE, false);
    cleanupRegisters();
  }

  /**
   * Emit code to local a float local variable
   * @param index the local index to load
   */
  protected void emit_fload(int index) {
    if (unreachableBytecode) return;
    //-#if RVM_WITH_QUICK_COMPILER_MOVE_ELIMINATION
    recordLoad(index, currentStackHeight, FLOAT_TYPE, biStart);
    //-#endif
    assignRegisters(0, FLOAT_TYPE, false);
    cleanupRegisters();
  }

  /**
   * Emit code to load a double local variable
   * @param index the local index to load
   */
  protected void emit_dload(int index){
    if (unreachableBytecode) return;
    //-#if RVM_WITH_QUICK_COMPILER_MOVE_ELIMINATION
    recordLoad(index, currentStackHeight, DOUBLE_TYPE, biStart);
    //-#endif
    assignRegisters(0, DOUBLE_TYPE, false);
    cleanupRegisters();
  }

  /**
   * Emit code to load a reference local variable
   * @param index the local index to load
   */
  protected void emit_aload(int index){
    if (unreachableBytecode) return;
    //-#if RVM_WITH_QUICK_COMPILER_MOVE_ELIMINATION
    recordLoad(index, currentStackHeight, OBJECT_TYPE, biStart);
    //-#endif
    assignRegisters(0, OBJECT_TYPE, false);
    cleanupRegisters();
  }


//   /*
//    * storing local variables
//    */


  /**
   * Emit code to store an int to a local variable
   * @param index the local index to store
   */
  protected void emit_istore(int index) {
    if (unreachableBytecode) return;
    //-#if RVM_WITH_QUICK_COMPILER_MOVE_ELIMINATION
    recordStore(currentStackHeight-1, index, WORD_TYPE);
    //-#endif
    assignRegisters(1, VOID_TYPE, false);
    cleanupRegisters();
  }

  /**
   * Emit code to store a long to a local variable
   * @param index the local index to store
   */
  protected void emit_lstore(int index){
    if (unreachableBytecode) return;
    //-#if RVM_WITH_QUICK_COMPILER_MOVE_ELIMINATION
    recordStore(currentStackHeight-2, index, LONG_TYPE);
    //-#endif
    assignRegisters(1, VOID_TYPE, false);
    cleanupRegisters();
  }

  /**
   * Emit code to store a float to a local variable
   * @param index the local index to store
   */
  protected void emit_fstore(int index) {
    if (unreachableBytecode) return;
    //-#if RVM_WITH_QUICK_COMPILER_MOVE_ELIMINATION
    recordStore(currentStackHeight-1, index, FLOAT_TYPE);
    //-#endif
    assignRegisters(1, VOID_TYPE, false);
    cleanupRegisters();
  }

  /**
   * Emit code to store an double  to a local variable
   * @param index the local index to store
   */
  protected void emit_dstore(int index) {
    if (unreachableBytecode) return;
    //-#if RVM_WITH_QUICK_COMPILER_MOVE_ELIMINATION
    recordStore(currentStackHeight-2, index, DOUBLE_TYPE);
    //-#endif
    assignRegisters(1, VOID_TYPE, false);
    cleanupRegisters();
  }

  /**
   * Emit code to store a reference to a local variable
   * @param index the local index to store
   */
  protected void emit_astore(int index) {
    if (unreachableBytecode) return;
    //-#if RVM_WITH_QUICK_COMPILER_MOVE_ELIMINATION
    recordStore(currentStackHeight-1, index, OBJECT_TYPE);
    //-#endif
    assignRegisters(1, VOID_TYPE, false);
    cleanupRegisters();
  }


//   /*
//    * array loads
//    */

    // sri0 --> array index
    // sri1 --> array references
    // fxw0 --> offset (in a temp register)

  /**
   * Emit code to load from an int array
   */
  protected void emit_iaload() {
    if (unreachableBytecode) return;
    assignRegisters(2, WORD_TYPE);
    int offsetRegister = aloadSetup(LOG_BYTES_IN_INT, sri0, sri1, S0);
    asm.emitLWZX  (sro0, offsetRegister, sri1);  // load desired int array element
    cleanupRegisters();
  }

  /**
   * Emit code to load from a long array
   */
  protected void emit_laload() {
    if (unreachableBytecode) return;
    assignRegisters(2, LONG_TYPE);
    int offsetRegister = aloadSetup(LOG_BYTES_IN_LONG, sri0, sri1, S0);
    loadLongFromMemory(sro0, offsetRegister, sri1);
    cleanupRegisters();
  }

  /**
   * Emit code to load from a float array
   */
  protected void emit_faload(){
    if (unreachableBytecode) return;
    assignRegisters(2, FLOAT_TYPE);
    int offsetRegister = aloadSetup(LOG_BYTES_IN_FLOAT, sri0, sri1, S0);
    asm.emitLFSX(sro0, offsetRegister, sri1);  // load desired (float) array element
    cleanupRegisters();
  }

  /**
   * Emit code to load from a double array
   */
  protected void emit_daload(){
    if (unreachableBytecode) return;
    assignRegisters(2, DOUBLE_TYPE);
    int offsetRegister = aloadSetup(LOG_BYTES_IN_DOUBLE, sri0, sri1, S0);
    asm.emitLFDX(sro0, offsetRegister, sri1);  // load desired (double) array element
    cleanupRegisters();
  }

  /**
   * Emit code to load from a reference array
   */
  protected void emit_aaload(){
    if (unreachableBytecode) return;
    assignRegisters(2, OBJECT_TYPE);
    int offsetRegister = aloadSetup(LOG_BYTES_IN_ADDRESS, sri0, sri1, S0);
    asm.emitLWZX  (sro0, offsetRegister, sri1);  // load desired (ref) array element
    cleanupRegisters();
  }

  /**
   * Emit code to load from a byte/boolean array
   */
  protected void emit_baload(){
    if (unreachableBytecode) return;
    assignRegisters(2, WORD_TYPE);
    int offsetRegister = aloadSetup(0, sri0, sri1, S0);
    asm.emitLBZX(sro0, offsetRegister, sri1);  // no load byte algebraic ...
    asm.emitEXTSB(sro0, sro0);
    cleanupRegisters();
  }

  /**
   * Emit code to load from a char array
   */
  protected void emit_caload() {
    if (unreachableBytecode) return;
    assignRegisters(2, WORD_TYPE);
    int offsetRegister = aloadSetup(LOG_BYTES_IN_CHAR, sri0, sri1, S0);
    asm.emitLHZX(sro0, offsetRegister, sri1);  // load desired (char) array element
    cleanupRegisters();
  }

  /**
   * Emit code to load from a short array
   */
  protected void emit_saload() {
    if (unreachableBytecode) return;
    assignRegisters(2, WORD_TYPE);
    int offsetRegister = aloadSetup(LOG_BYTES_IN_SHORT, sri0, sri1, S0);
    asm.emitLHAX(sro0, offsetRegister, sri1);  // load desired (short) array element
    cleanupRegisters();
  }


  /*
   * array stores
   */

    // sri0 --> value to store
    // sri1 --> array index
    // sri2 --> array references
    // S0   --> length in temp register.

  /**
   * Emit code to store to an int array
   */
  protected void emit_iastore() {
    if (unreachableBytecode) return;
    assignRegisters(3, VOID_TYPE);
    int offsetRegister = astoreSetup(LOG_BYTES_IN_INT, sri1, sri2, S0);
    asm.emitSTWX (sri0, offsetRegister, sri2);  // store int value in array
    cleanupRegisters();
  }

  /**
   * Emit code to store to a long array
   */
  protected void emit_lastore() {
    if (unreachableBytecode) return;
    assignRegisters(3, VOID_TYPE);
    int offsetRegister = astoreSetup(LOG_BYTES_IN_LONG, sri1, sri2, S0);
    storeLongToMemory(sri0, offsetRegister, sri2);
    cleanupRegisters();
  }

  /**
   * Emit code to store to a float array
   */
  protected void emit_fastore() {
    if (unreachableBytecode) return;
    assignRegisters(3, VOID_TYPE);
    int offsetRegister = astoreSetup(LOG_BYTES_IN_FLOAT, sri1, sri2, S0);
    asm.emitSTFSX (sri0, offsetRegister, sri2);  // store float value in array
    cleanupRegisters();
  }

  /**
   * Emit code to store to a double array
   */
  protected void emit_dastore() {
    if (unreachableBytecode) return;
    assignRegisters(3, VOID_TYPE);
    int offsetRegister = astoreSetup(LOG_BYTES_IN_DOUBLE, sri1, sri2, S0);
    asm.emitSTFDX (sri0, offsetRegister, sri2);  // store double value in array
    cleanupRegisters();
  }

  /**
   * Emit code to store to a reference array
   */
  protected void emit_aastore() {
    if (unreachableBytecode) return;
    asm.emitLAddrToc(T0,  VM_Entrypoints.checkstoreMethod.getOffset());
    asm.emitMTCTR(T0);
    // Use the peek routine here as assignRegisters could put the
    // value in a volatile register that gets clobbered by the
    // function call:
    peekToRegister(OBJECT_TYPE, currentStackHeight-1, T1);     // T1 := value
    peekToRegister(OBJECT_TYPE, currentStackHeight-3, T0);     // T0 := arrayref
    asm.emitBCCTRL();   // checkstore(arrayref, value)
    // Now it's safe to get the values again
    assignRegisters(3, VOID_TYPE);
    // sri0 -- value
    // sri1 -- index
    // sri2 -- array
    if (MM_Interface.NEEDS_WRITE_BARRIER) {
      VM_QuickBarriers.compileArrayStoreBarrier(asm, sri2, sri1, sri0,S1);
    } else {
      int offsetRegister = astoreSetup(LOG_BYTES_IN_ADDRESS, sri1, sri2, S0);
      asm.emitSTAddrX (sri0, offsetRegister, sri2);  // store ref value in array
    }
    
    cleanupRegisters();
  }

  /**
   * Emit code to store to a byte/boolean array
   */
  protected void emit_bastore() {
    if (unreachableBytecode) return;
    assignRegisters(3, VOID_TYPE);
    int offsetRegister = astoreSetup(0, sri1, sri2, S0);
    asm.emitSTBX(sri0, offsetRegister, sri2);  // store byte value in array
    cleanupRegisters();
  }

  /**
   * Emit code to store to a char array
   */
  protected void emit_castore() {
    if (unreachableBytecode) return;
    assignRegisters(3, VOID_TYPE);
    int offsetRegister = astoreSetup(LOG_BYTES_IN_CHAR, sri1, sri2, S0);
    asm.emitSTHX(sri0, offsetRegister, sri2);  // store char value in array
    cleanupRegisters();
  }

  /**
   * Emit code to store to a short array
   */
  protected void emit_sastore() {
    if (unreachableBytecode) return;
    assignRegisters(3, VOID_TYPE);
    int offsetRegister = astoreSetup(LOG_BYTES_IN_SHORT, sri1, sri2, S0);
    asm.emitSTHX(sri0, offsetRegister, sri2);  // store short value in array
    cleanupRegisters();
  }


  /**
   * Generates the code that handles stack manipulation bytecodes. The
  argument is an array of ints describing how the stack entries should
  move. The array is logically divided into pairs, so the i'th entry
  in the array is the index of an entry and the i+1'th entry is the
  location it moves to. All indicese are numbered downward from the
  topmost stack entry, which is position zero.  For example, dup2 is
  [0, +2, -1, +1], meaning the top is copied two slots above the
  current stack top and the entry just below the top is copied one
  slot above the old stack top. The entries in the array must be
  ordered so no values are clobbered before they can be moved!
   *
   * @param copyList a description of how to copy stack entries
   */
  private void handleStackRearrangement(int[] copyList) {
    int newStackHeight = modifyStack(stack, currentStackHeight,
                                     copyList, stackAfterOpcode);
    assignStackLocations(stackAfterOpcode, newStackHeight,
                         stackLocations, stackLocationsAfterOpcode);

    int count = copyList.length;
    int lastStackSlot = currentStackHeight - 1;
    byte type;
    boolean shiftingOld = true;
    for (int i=0; i < count; i += 2) {
      int oldIndex = lastStackSlot + copyList[i];
      int newIndex = lastStackSlot + copyList[i+1];

      // Rearranging happens in two phases:
      // 1) shift existing stack entries up to their new locations
      // in the stack
      // 2) filling in the resulting gap(s) by copying values from slots
      // written in phase 1
      
      if (shiftingOld && oldIndex > lastStackSlot) {
        shiftingOld = false;
        // copying from newly written slots
        type = stackAfterOpcode[newIndex];
      } else {
        // copying from existing slots
        type = stack[oldIndex];
      }

      // The java stack manipulation opcodes have no type information,
      // only the number of stack slots duplicated. But in this
      // compiler implementation, both slots used a long or double
      // type will be copied with a single copyByLocation
      // call. Therefore we must skip the copy of the second slot in
      // that case. We require the copy list be sorted properly so we
      // can detect these cases!
      if (VM_BuildBB.getJavaStackSize(type) > 1) {
        if (VM.VerifyAssertions) VM._assert(copyList[i] == 1+copyList[i+2] &&
                                            copyList[i+1] == 1+copyList[i+3]);
        i += 2;
        oldIndex -= 1;
        newIndex -= 1;
      }
      
      int srcLocation;
      if (shiftingOld)   {
        // copying from existing slots
        srcLocation = stackLocations[oldIndex];
      } else {
        // copying from newly written slots
        srcLocation = stackLocationsAfterOpcode[oldIndex];
      }

      copyByLocation(type, srcLocation,
                     type, stackLocationsAfterOpcode[newIndex]);

      //-#if RVM_WITH_QUICK_COMPILER_MOVE_ELIMINATION
      int oldSlotIndex = stackIndexToSlotIndex(oldIndex);
      int newSlotIndex = stackIndexToSlotIndex(newIndex);
      setSlotContents(newSlotIndex, slotContents[oldSlotIndex],
                            currentStackHeight, slotStatus[oldSlotIndex],
                            false);
      //-#endif
    }

    currentStackHeight = newStackHeight;
    byte[] ts = stack;
    stack = stackAfterOpcode;
    stackAfterOpcode = ts;
    int[] tl = stackLocations;
    stackLocations = stackLocationsAfterOpcode;
    stackLocationsAfterOpcode = tl;
    
  }

  
  /**
   * Emit code to implement the pop bytecode
   */
  protected final void emit_pop() {
    if (unreachableBytecode) return;
    currentStackHeight -= 1;
    assignStackLocationsAfterPop();
  }

  /**
   * Emit code to implement the pop2 bytecode
   */
  protected final void emit_pop2() {
    if (unreachableBytecode) return;
    currentStackHeight -= 2;
    assignStackLocations();
  }

  /**
   * Emit code to implement the dup bytecode
   */
  protected final void emit_dup() {
    if (unreachableBytecode) return;
    // "dup" happens often enough that we hand-code its operation
    // rather than use the more generic handleStackRearrangement
    // method.
    
    int topSlot = currentStackHeight-1;
    byte topType = stack[topSlot];
    int topLocation = getStackLocation(topType, topSlot);
    
    assignStackLocationsBeforePush(topType);

    copyByLocation(topType, topLocation,
                   topType, getStackLocation(topType, currentStackHeight));

    //-#if RVM_WITH_QUICK_COMPILER_MOVE_ELIMINATION
    int oldSlotIndex = stackIndexToSlotIndex(topSlot);
    int newSlotIndex = stackIndexToSlotIndex(currentStackHeight);
    setSlotContents(newSlotIndex, slotContents[oldSlotIndex],
                          currentStackHeight, slotStatus[oldSlotIndex],
                          false);
    //-#endif

    stack[currentStackHeight++] = topType;
  }

  /**
   * Emit code to implement the dup_x1 bytecode
   */
  protected final void emit_dup_x1() {
    if (unreachableBytecode) return;
    handleStackRearrangement(copyList_dup_x1);
  }

  /**
   * Emit code to implement the dup_x2 bytecode
   */
  protected final void emit_dup_x2() {
    if (unreachableBytecode) return;
    handleStackRearrangement(copyList_dup_x2);
  }

  /**
   * Emit code to implement the dup2 bytecode
   */
  protected final void emit_dup2() {
    if (unreachableBytecode) return;
    handleStackRearrangement(copyList_dup2);
  }

  /**
   * Emit code to implement the dup2_x1 bytecode
   */
  protected final void emit_dup2_x1() {
    if (unreachableBytecode) return;
    handleStackRearrangement(copyList_dup2_x1);
  }

  /**
   * Emit code to implement the dup2_x2 bytecode
   */
  protected final void emit_dup2_x2() {
    if (unreachableBytecode) return;
    handleStackRearrangement(copyList_dup2_x2);
  }

  /**
   * Emit code to implement the swap bytecode
   */
  protected final void emit_swap() {
    if (unreachableBytecode) return;
    int topIndex = currentStackHeight - 1;
    int nextIndex = topIndex - 1;
    byte initialTopType = stack[topIndex];
    byte initialNextType = stack[nextIndex];
    int initialTopLocation = getStackLocation(initialTopType, topIndex);
    int initialNextLocation = getStackLocation(initialNextType, nextIndex);

    //-#if RVM_WITH_QUICK_COMPILER_MOVE_ELIMINATION
    swapSlotContents(stackIndexToSlotIndex(topIndex),
                           stackIndexToSlotIndex(nextIndex));
    //-#endif

    stack[topIndex] = stack[nextIndex] ;
    stack[nextIndex] = initialTopType;


    if (needsFloatRegister(initialTopType) ==
        needsFloatRegister(initialNextType)) {
      int tempRegister;

      if (needsFloatRegister(initialTopType))
        tempRegister = F0;          
      else
        tempRegister = S0;
    
      copyByLocation(initialTopType, initialNextLocation,
                     initialTopType, registerToLocation(tempRegister));
      copyByLocation(initialTopType, initialTopLocation,
                     initialTopType, initialNextLocation);
      copyByLocation(initialTopType, registerToLocation(tempRegister),
                     initialTopType, initialTopLocation);
    } else {

      // If the types of the two slots that were swapped were different,
      // it's possible the location assignments for those stack slots
      // need changing.

      assignStackLocations();
      
      int finalTopLocation = getStackLocation(initialNextType, topIndex);
      int finalNextLocation = getStackLocation(initialTopType, nextIndex);

      // Do each individually
      if (initialTopLocation != finalNextLocation) {
    
        copyByLocation(initialTopType, initialTopLocation,
                       initialTopType, finalNextLocation);
      }
      if (initialNextLocation != finalTopLocation) {
    
        copyByLocation(initialNextType, initialNextLocation,
                       initialNextType, finalTopLocation);
      }
    }
  }


//   /*
//    * int ALU
//    */


  /**
   * Emit code to implement the iadd bytecode
   */
  protected final void emit_iadd() {
    if (unreachableBytecode) return;
    assignRegisters(2, WORD_TYPE); // no work registers
    //-#if RVM_WITH_QUICK_COMPILER_MOVE_ELIMINATION
    if (resultIsStackRegister)
      setRetargetable(returnValue, asm.getMachineCodeIndex());
    //-#endif
    asm.emitADD  (sro0, sri0, sri1);
    cleanupRegisters();
  }

  /**
   * Emit code to implement the isub bytecode
   */
  protected final void emit_isub() {
    if (unreachableBytecode) return;
    assignRegisters(2, WORD_TYPE); // no work registers
    //-#if RVM_WITH_QUICK_COMPILER_MOVE_ELIMINATION
    if (resultIsStackRegister)
      setRetargetable(returnValue, asm.getMachineCodeIndex());
    //-#endif
    asm.emitSUBFC  (sro0, sri0, sri1);
    cleanupRegisters();
  }

  /**
   * Emit code to implement the imul bytecode
   */
  protected final void emit_imul() {
    if (unreachableBytecode) return;
    assignRegisters(2, WORD_TYPE); // no work registers
    //-#if RVM_WITH_QUICK_COMPILER_MOVE_ELIMINATION
    if (resultIsStackRegister)
      setRetargetable(returnValue, asm.getMachineCodeIndex());
    //-#endif
    asm.emitMULLW  (sro0, sri0, sri1);
    cleanupRegisters();
  }

  /**
   * Emit code to implement the idiv bytecode
   */
  protected final void emit_idiv() {
    if (unreachableBytecode) return;
    assignRegisters(2, WORD_TYPE); // no work registers
    asm.emitTWEQ0(sri0);
    //-#if RVM_WITH_QUICK_COMPILER_MOVE_ELIMINATION
    if (resultIsStackRegister)
      setRetargetable(returnValue, asm.getMachineCodeIndex());
    //-#endif
    asm.emitDIVW (sro0, sri1, sri0);  // sro0 := sri1/sri0
    cleanupRegisters();
  }

  /**
   * Emit code to implement the irem bytecode
   */
  protected final void emit_irem() {
    if (unreachableBytecode) return;
    assignRegisters(2, WORD_TYPE);
    asm.emitTWEQ0(sri0);
    asm.emitDIVW (S0, sri1, sri0); // S0 := sri1/sri0
    asm.emitMULLW(S0, S0, sri0); // S0 := [sri1/sri0]*sri0
    //-#if RVM_WITH_QUICK_COMPILER_MOVE_ELIMINATION
    if (resultIsStackRegister)
      setRetargetable(returnValue, asm.getMachineCodeIndex());
    //-#endif
    asm.emitSUBFC  (sro0, S0, sri1);   // sro0 := sri1 - [sri1/sri0]*sri0
    cleanupRegisters();
  }

  /**
   * Emit code to implement the ineg bytecode
   */
  protected final void emit_ineg() {
    if (unreachableBytecode) return;
    assignRegisters(1, WORD_TYPE);
    //-#if RVM_WITH_QUICK_COMPILER_MOVE_ELIMINATION
    if (resultIsStackRegister)
      setRetargetable(returnValue, asm.getMachineCodeIndex());
    //-#endif
    asm.emitNEG (sro0, sri0);  
    cleanupRegisters();
  }

  /**
   * Emit code to implement the ishl bytecode
   */
  protected final void emit_ishl() {
    if (unreachableBytecode) return;
    assignRegisters(2, WORD_TYPE); // no work registers
    asm.emitANDI(S0, sri0, 0x1F);
    //-#if RVM_WITH_QUICK_COMPILER_MOVE_ELIMINATION
    if (resultIsStackRegister)
      setRetargetable(returnValue, asm.getMachineCodeIndex());
    //-#endif
    asm.emitSLW (sro0, sri1, S0);
    cleanupRegisters();
  }

  /**
   * Emit code to implement the ishr bytecode
   */
  protected final void emit_ishr() {
    if (unreachableBytecode) return;
    assignRegisters(2, WORD_TYPE); // no work registers
    asm.emitANDI(S0, sri0, 0x1F);
    //-#if RVM_WITH_QUICK_COMPILER_MOVE_ELIMINATION
    if (resultIsStackRegister)
      setRetargetable(returnValue, asm.getMachineCodeIndex());
    //-#endif
    asm.emitSRAW(sro0, sri1, S0);
    cleanupRegisters();
  }

  /**
   * Emit code to implement the iushr bytecode
   */
  protected final void emit_iushr() {
    if (unreachableBytecode) return;
    assignRegisters(2, WORD_TYPE); // no work registers
    asm.emitANDI(S0, sri0, 0x1F);
    //-#if RVM_WITH_QUICK_COMPILER_MOVE_ELIMINATION
    if (resultIsStackRegister)
      setRetargetable(returnValue, asm.getMachineCodeIndex());
    //-#endif
    asm.emitSRW (sro0, sri1, S0);
    cleanupRegisters();
  }

  /**
   * Emit code to implement the iand bytecode
   */
  protected final void emit_iand() {
    if (unreachableBytecode) return;
    assignRegisters(2, WORD_TYPE); // no work registers
    //-#if RVM_WITH_QUICK_COMPILER_MOVE_ELIMINATION
    if (resultIsStackRegister)
      setRetargetable(returnValue, asm.getMachineCodeIndex());
    //-#endif
    asm.emitAND (sro0, sri0, sri1);
    cleanupRegisters();
  }

  /**
   * Emit code to implement the ior bytecode
   */
  protected final void emit_ior() {
    if (unreachableBytecode) return;
    assignRegisters(2, WORD_TYPE); // no work registers
    //-#if RVM_WITH_QUICK_COMPILER_MOVE_ELIMINATION
    if (resultIsStackRegister)
      setRetargetable(returnValue, asm.getMachineCodeIndex());
    //-#endif
    asm.emitOR (sro0, sri0, sri1);
    cleanupRegisters();
  }

  /**
   * Emit code to implement the ixor bytecode
   */
  protected final void emit_ixor() {
    if (unreachableBytecode) return;
    assignRegisters(2, WORD_TYPE); // no work registers
    //-#if RVM_WITH_QUICK_COMPILER_MOVE_ELIMINATION
    if (resultIsStackRegister)
      setRetargetable(returnValue, asm.getMachineCodeIndex());
    //-#endif
    asm.emitXOR (sro0, sri0, sri1);
    cleanupRegisters();
  }

  /**
   * Emit code to implement the iinc bytecode
   * @param index index of local
   * @param val value to increment it by
   */
  protected final void emit_iinc(int index, int val) {
    if (unreachableBytecode) return;

    //-#if RVM_WITH_QUICK_COMPILER_MOVE_ELIMINATION
    int slotIndex = localIndexToSlotIndex(index);
    flushSlot(slotIndex, currentStackHeight);
    int theValue = createValue(0,
                                     VALUE_TYPE_CALCULATION,
                                     WORD_TYPE,
                                     -1);
    setSlotContents(slotIndex, theValue,
                          currentStackHeight, STATUS_WRITTEN);

    //-#endif
      
    int location = getLocalLocation(WORD_TYPE, index);
    
    if (isRegister(location)) {
      int r = locationToRegister(location);
      asm.emitADDI(r, val, r);
    }
    else {
      genLoadLocalToRegister(WORD_TYPE, index, S0);
      asm.emitADDI(S0, val, S0);
      genStoreLocalFromRegister(WORD_TYPE, index, S0);
    }
  }


//   /*
//    * long ALU
//    */

  /**
   * Emit code to implement the ladd bytecode
   */
  protected final void emit_ladd() {
    if (unreachableBytecode) return;
    assignRegisters(2, LONG_TYPE); // no work registers
    if (VM.BuildFor32Addr) {
      asm.emitADD   (sro0+1, sri0+1, sri1+1);
      asm.emitADDE  (sro0, sri0, sri1);
    } else {
      asm.emitADD   (sro0, sri0, sri1);
    }
    cleanupRegisters();
  }

  /**
   * Emit code to implement the lsub bytecode
   */
  protected final void emit_lsub() {
    if (unreachableBytecode) return;
    assignRegisters(2, LONG_TYPE); // no work registers
    if (VM.BuildFor32Addr) {
      asm.emitSUBFC  (sro0+1, sri0+1, sri1+1); 
      asm.emitSUBFE  (sro0, sri0, sri1);
    } else {
      asm.emitSUBFC  (sro0, sri0, sri1); 
    }
    cleanupRegisters();
  }

  /**
   * Emit code to implement the lmul bytecode
   */
  protected final void emit_lmul() {
    if (unreachableBytecode) return;
    assignRegisters(2, LONG_TYPE);
    if (VM.BuildFor64Addr) {
      asm.emitMULLD (sro0, sri1, sri0);
    } else {
      // sri0   := hi order word of arg0 (h0)
      // sri0+1 := lo order word of arg0 (l0)
      // sri1   := hi order word of arg1 (h1)
      // sri1+1 := lo order word of arg1 (l1)
      asm.emitMULHWU(S0, sri1+1, sri0+1); // upper word of l0 * l1
      asm.emitMULLW  (S1, sri1, sri0+1);   // S1 := h1 * l0
      asm.emitADD     (S1, S1, S0);     // S1 := h1*l0 + upper(l0*l1)
      asm.emitMULLW  (S0, sri1+1, sri0);   // S0 := l1 * h0
      asm.emitADD     (sro0, S1, S0);     // hr := l1*h0+h1*l0+upper(l0*l1)
      asm.emitMULLW  (sro0+1, sri1+1, sri0+1); // lr := lower word of
                                               // l0 * l1
    }
    cleanupRegisters();
  }

  /**
   * Emit code to implement the ldiv bytecode
   */
  protected final void emit_ldiv() {
    if (unreachableBytecode) return;
    if (VM.BuildFor64Addr) {
      assignRegisters(2, LONG_TYPE);
      asm.emitTDEQ0(sri1);
      asm.emitDIVD(sro0, sri0, sri1);
      cleanupRegisters();
    } else {
      popToRegister(LONG_TYPE, T2);
      asm.emitOR  (S0, T3, T2); // or two halfs of denominator together
      asm.emitTWEQ0(S0);         // trap if 0.
      popToRegister(LONG_TYPE, T0);
      generateSysCall(16, VM_Entrypoints.sysLongDivideIPField);
      pushFromRegister(LONG_TYPE, T0);
    }
  }

  /**
   * Emit code to implement the lrem bytecode
   */
  protected final void emit_lrem() {
    if (unreachableBytecode) return;
    if (VM.BuildFor64Addr) {
      // XXX CJH TODO:
      if (VM.VerifyAssertions) VM._assert(NOT_REACHED);
      assignRegisters(2, LONG_TYPE);
      asm.emitTDEQ0(sri1);
      asm.emitDIVD(T0,T1,T3);      // T0 := T1/T3
      asm.emitMULLD(T0, T0, T3);   // T0 := [T1/T3]*T3
      asm.emitSUBFC (T1, T0, T1);   // T1 := T1 - [T1/T3]*T3
      cleanupRegisters();
    } else {
      popToRegister(LONG_TYPE, T2);
      asm.emitOR  (S0, T3, T2); // or two halfs of denominator together
      asm.emitTWEQ0(S0);         // trap if 0.
      popToRegister(LONG_TYPE, T0);
      generateSysCall(16, VM_Entrypoints.sysLongRemainderIPField);
      pushFromRegister(LONG_TYPE, T0);
    }
  } 

  /**
   * Emit code to implement the lneg bytecode
   */
  protected final void emit_lneg() {
    if (unreachableBytecode) return;
    assignRegisters(1, LONG_TYPE);
    if (VM.BuildFor64Addr) {
      asm.emitNEG(sro0, sri0);
    } else {
      // sri0   is low bits of l
      // sri0+1 is high bits of l
      // Note nonstandard arg order required by VM_Assembler!!!
      asm.emitSUBFIC (sri0+1, sro0+1, 0x0); 
      asm.emitSUBFZE (sro0, sri0);
    }
    cleanupRegisters();
  }

  /**
   * Emit code to implement the lshsl bytecode
   */
  protected final void emit_lshl() {
    if (unreachableBytecode) return;
    if (VM.BuildFor64Addr) {
      assignRegisters(2, LONG_TYPE);
      asm.emitANDI(S0, sri0, 0x3F);
      asm.emitSLD  (sro0, sri1, S0);
      cleanupRegisters();
    } else {
      assignRegisters(2, LONG_TYPE); // two fixed work regs
      // sri0 is n
      // sri1   := hi order word of long (h1)
      // sri1+1 := lo order word of long (l1)

      asm.emitANDI(S0, sri0, 0x20);   // shift more than 31 bits?
      asm.emitXOR (S1, S0, sri0);   // restrict shift to at most 31 bits
      VM_ForwardReference fr1 = asm.emitForwardBC(EQ); // if shift less than 32, goto
      asm.emitSLW (sro0, sri1+1, S1); // hi bits are l1 shifted n or n-32
      asm.emitLVAL (sro0+1,  0);         // and low bits are zero
      VM_ForwardReference fr2 = asm.emitForwardB();
      fr1.resolve(asm);
      asm.emitMR  (S0, sri1+1);         // (n<32) --> remember low bits
      asm.emitSLW (sro0+1, sri1+1, S1); // lo is l1 shifted n or n-32 
      asm.emitSLW (sro0, sri1, S1);   // high bits of l shifted n left
      asm.emitSUBFIC (S1, S1, 0x20);   // shift := 32 - shift; 
      asm.emitSRW (S0, S0, S1);   // S0 is middle bits of result
      asm.emitOR  (sro0, sro0, S0);   // sro0 is high bits of result
      fr2.resolve(asm);
      cleanupRegisters();
    }
  }

  /**
   * Emit code to implement the lshr bytecode
   */
  protected final void emit_lshr() {
    if (unreachableBytecode) return;
    if (VM.BuildFor64Addr) {
      assignRegisters(2, LONG_TYPE);
      asm.emitANDI(S0, sri0, 0x3F);
      asm.emitSRAD (sro0, sri1, S0);
      cleanupRegisters();
    } else {
      assignRegisters(2, LONG_TYPE); // two fixed work regs
      // sri0 is n
      // sri1 is hi order word of l (h1)
      // sri1+1 is low order word of l (l1)

      asm.emitANDI(S0, sri0, 0x20);  // shift more than 31 bits?
      asm.emitXOR (S1, S0, sri0);  // restrict shift to at most 31 bits
      VM_ForwardReference fr1 = asm.emitForwardBC(EQ); // shift < 32
      asm.emitSRAW(sro0+1, sri1,   S1);  // lo bits are hl shifted n or n-32
      asm.emitSRAWI(sro0,   sri1, 0x1F);  // propagate sign bit into hi bits
      VM_ForwardReference fr2 = asm.emitForwardB();
      fr1.resolve(asm);
      asm.emitMR  (S0, sri1);           // (n<32) --> remember high bits
      asm.emitSRW (sro0+1, sri1+1, S1); // lo bits of l shifted n bits right
      asm.emitSRAW(sro0,   sri1,   S1); // hi bits of l shifted n bits right
      asm.emitSUBFIC (S1, S1, 0x20);  // shift := 32 - shift;
      asm.emitSLW (S0, S0, S1);  // S0 is middle bits of result
      asm.emitOR  (sro0+1, sro0+1, S0); // sro0+1 is low bits of result
      fr2.resolve(asm);
      cleanupRegisters();
    }
  }

  /**
   * Emit code to implement the lushr bytecode
   */
  protected final void emit_lushr() {
    if (unreachableBytecode) return;
    if (VM.BuildFor64Addr) {
      assignRegisters(2, LONG_TYPE);
      asm.emitANDI(S0, sri0, 0x3F);
      asm.emitSRD (sro0, sri1, S0);
      cleanupRegisters();
    } else {
      assignRegisters(2, LONG_TYPE); // two fixed work regs
      // sri0 is n
      // sri1 is hi order word of l (h1)
      // sri1+1 is low order word of l (l1)

      asm.emitANDI(S0, sri0, 0x20);  // shift more than 31 bits?
      asm.emitXOR (S1, S0, sri0);  // restrict shift to at most 31 bits
      VM_ForwardReference fr1 = asm.emitForwardBC(EQ); // shift < 32
      asm.emitSRW (sro0+1, sri1,   S1);  // lo bits are hl shifted n or n-32
      asm.emitLVAL (sro0, 0);           // high bits are zero
      VM_ForwardReference fr2 = asm.emitForwardB(); // (done)
      fr1.resolve(asm);
      asm.emitMR  (S0, sri1);           // (n<32) --> remember high bits
      asm.emitSRW (sro0+1, sri1+1, S1); // low bits of l shifted n bits right
      asm.emitSRW (sro0,   sri1, S1);  // high bits of l shifted n bits right
      asm.emitSUBFIC (S1, S1, 0x20);  // shift := 32 - shift;
      asm.emitSLW (S0, S0, S1);  // S0 is middle bits of result
      asm.emitOR  (sro0+1, sro0+1, S0); // sro0+1 is low bits of result
      fr2.resolve(asm);
      cleanupRegisters();
    }
  }
  /**
   * Emit code to implement the land bytecode
   */
  protected final void emit_land() {
    if (unreachableBytecode) return;
    assignRegisters(2, LONG_TYPE); // no work registers
    if (VM.BuildFor32Addr) {
      asm.emitAND(sro0, sri1, sri0);
      asm.emitAND(sro0+1, sri0+1, sri1+1);
    } else {
      asm.emitAND(sro0, sri1, sri0);
    }
    cleanupRegisters();
  }

  /**
   * Emit code to implement the lor bytecode
   */
  protected final void emit_lor() {
    if (unreachableBytecode) return;
    assignRegisters(2, LONG_TYPE); // no work registers
    if (VM.BuildFor32Addr) {
      asm.emitOR(sro0, sri1, sri0);
      asm.emitOR(sro0+1, sri0+1, sri1+1);
    } else {
      asm.emitOR(sro0, sri1, sri0);
    }
    cleanupRegisters();
  }

  /**
   * Emit code to implement the lxor bytecode
   */
  protected final void emit_lxor() {
    if (unreachableBytecode) return;
    assignRegisters(2, LONG_TYPE); // no work registers
    if (VM.BuildFor32Addr) {
      asm.emitXOR(sro0, sri1, sri0);
      asm.emitXOR(sro0+1, sri0+1, sri1+1);
    } else {
      asm.emitXOR(sro0, sri1, sri0);
    }
    cleanupRegisters();
  }


//   /*
//    * float ALU
//    */


  /**
   * Emit code to implement the fadd bytecode
   */
  protected final void emit_fadd() {
    if (unreachableBytecode) return;
    assignRegisters(2, FLOAT_TYPE); // three float work regs
    //-#if RVM_WITH_QUICK_COMPILER_MOVE_ELIMINATION
    if (resultIsStackRegister)
      setRetargetable(returnValue, asm.getMachineCodeIndex());
    //-#endif
    asm.emitFADDS  (sro0, sri0, sri1);
    cleanupRegisters();
  }

  /**
   * Emit code to implement the fsub bytecode
   */
  protected final void emit_fsub() {
    if (unreachableBytecode) return;
    assignRegisters(2, FLOAT_TYPE); // three float work regs
    //-#if RVM_WITH_QUICK_COMPILER_MOVE_ELIMINATION
    if (resultIsStackRegister)
      setRetargetable(returnValue, asm.getMachineCodeIndex());
    //-#endif
    asm.emitFSUBS  (sro0, sri1, sri0);
    cleanupRegisters();
  }

  /**
   * Emit code to implement the fmul bytecode
   */
  protected final void emit_fmul() {
    if (unreachableBytecode) return;
    assignRegisters(2, FLOAT_TYPE); // three float work regs
    //-#if RVM_WITH_QUICK_COMPILER_MOVE_ELIMINATION
    if (resultIsStackRegister)
      setRetargetable(returnValue, asm.getMachineCodeIndex());
    //-#endif
    asm.emitFMULS  (sro0, sri1, sri0); // single precision multiply
    cleanupRegisters();
  }

  /**
   * Emit code to implement the fdiv bytecode
   */
  protected final void emit_fdiv() {
    if (unreachableBytecode) return;
    assignRegisters(2, FLOAT_TYPE); // three float work regs
    //-#if RVM_WITH_QUICK_COMPILER_MOVE_ELIMINATION
    if (resultIsStackRegister)
      setRetargetable(returnValue, asm.getMachineCodeIndex());
    //-#endif
    asm.emitFDIVS (sro0, sri1, sri0);  
    cleanupRegisters();
  }

  /**
   * Emit code to implement the frem bytecode
   */
  protected final void emit_frem() {
    if (unreachableBytecode) return; 

    popToRegister(FLOAT_TYPE, F1);
    popToRegister(FLOAT_TYPE, F0);
    generateSysCall(16, VM_Entrypoints.sysDoubleRemainderIPField);
    pushFromRegister(FLOAT_TYPE, F0);
  }

  /**
   * Emit code to implement the fneg bytecode
   */
  protected final void emit_fneg() {
    if (unreachableBytecode) return;
    assignRegisters(1, FLOAT_TYPE);
    //-#if RVM_WITH_QUICK_COMPILER_MOVE_ELIMINATION
    if (resultIsStackRegister)
      setRetargetable(returnValue, asm.getMachineCodeIndex());
    //-#endif
    asm.emitFNEG (sro0, sri0);  
    cleanupRegisters();
  }


//   /*
//    * double ALU
//    */


  /**
   * Emit code to implement the dadd bytecode
   */
  protected final void emit_dadd() {
    if (unreachableBytecode) return;
    assignRegisters(2, DOUBLE_TYPE); // no work registers
    //-#if RVM_WITH_QUICK_COMPILER_MOVE_ELIMINATION
    if (resultIsStackRegister)
      setRetargetable(returnValue, asm.getMachineCodeIndex());
    //-#endif
    asm.emitFADD  (sro0, sri0, sri1);
    cleanupRegisters();
  }

  /**
   * Emit code to implement the dsub bytecode
   */
  protected final void emit_dsub() {
    if (unreachableBytecode) return;

    assignRegisters(2, DOUBLE_TYPE); // no work registers
    //-#if RVM_WITH_QUICK_COMPILER_MOVE_ELIMINATION
    if (resultIsStackRegister)
      setRetargetable(returnValue, asm.getMachineCodeIndex());
    //-#endif
    asm.emitFSUB   (sro0, sri1, sri0);
    cleanupRegisters();
  }

  /**
   * Emit code to implement the dmul bytecode
   */
  protected final void emit_dmul() {
    if (unreachableBytecode) return;
    assignRegisters(2, DOUBLE_TYPE); // no work registers
    //-#if RVM_WITH_QUICK_COMPILER_MOVE_ELIMINATION
    if (resultIsStackRegister)
      setRetargetable(returnValue, asm.getMachineCodeIndex());
    //-#endif
    asm.emitFMUL  (sro0, sri1, sri0); 
    cleanupRegisters();
  }

  /**
   * Emit code to implement the ddiv bytecode
   */
  protected final void emit_ddiv() {
    if (unreachableBytecode) return;
    assignRegisters(2, DOUBLE_TYPE); // no work registers
    //-#if RVM_WITH_QUICK_COMPILER_MOVE_ELIMINATION
    if (resultIsStackRegister)
      setRetargetable(returnValue, asm.getMachineCodeIndex());
    //-#endif
    asm.emitFDIV  (sro0, sri1, sri0);  
    cleanupRegisters();
  }

  /**
   * Emit code to implement the drem bytecode
   */
  protected final void emit_drem() {
    if (unreachableBytecode) return;
    popToRegister(DOUBLE_TYPE, F1);                 //F1 is b
    popToRegister(DOUBLE_TYPE, F0);                 //F0 is a
    generateSysCall(16, VM_Entrypoints.sysDoubleRemainderIPField);
    pushFromRegister(DOUBLE_TYPE, F0);
  }

  /**
   * Emit code to implement the dneg bytecode
   */
  protected final void emit_dneg() {
    if (unreachableBytecode) return;
    assignRegisters(1, DOUBLE_TYPE);
    //-#if RVM_WITH_QUICK_COMPILER_MOVE_ELIMINATION
    if (resultIsStackRegister)
      setRetargetable(returnValue, asm.getMachineCodeIndex());
    //-#endif
    asm.emitFNEG (sro0, sri0);  
    cleanupRegisters();
  }


//   /*
//    * conversion ops
//    */


  /**
   * Emit code to implement the i2l bytecode
   */
  protected final void emit_i2l() {
    if (unreachableBytecode) return;
    assignRegisters(1, LONG_TYPE);
    if (VM.BuildFor64Addr) {
      if (sro0 != sri0)
        asm.emitMR(sro0, sri0);
    } else {
      // original word goes in lo-order word of result
      asm.emitMR(sro0+1, sri0);
      // fill hi-order word of result with  0's or 1's depending on sign
      asm.emitSRAWI(sro0, sri0, 31);
    }
    cleanupRegisters();
  }

  /**
   * Emit code to implement the i2f bytecode
   */
  protected final void emit_i2f() {
    if (unreachableBytecode) return;
    if (VM.BuildFor64Addr) {
      // XXX CJH TODO:
      if (VM.VerifyAssertions) VM._assert(NOT_REACHED);
    } else {
      assignRegisters(1, FLOAT_TYPE);

      asm.emitLFDtoc(SF0, VM_Entrypoints.IEEEmagicField.getOffset(), S0);// F0 is MAGIC
      asm.emitSTFD  (SF0, VM_Entrypoints.scratchStorageField.getOffset(), PROCESSOR_REGISTER); // MAGIC in memory
      asm.emitSTW   (sri0,  VM_Entrypoints.scratchStorageField.getOffset()+4, PROCESSOR_REGISTER); // if 0 <= X, MAGIC + X 
      asm.emitCMPI  (sri0,  0);                   // is X < 0
      VM_ForwardReference fr1 = asm.emitForwardBC(GE);      // Now, handle X < 0
      asm.emitLInt   (S0, VM_Entrypoints.scratchStorageField.getOffset(), PROCESSOR_REGISTER);// S0 is top of MAGIC
      asm.emitADDI   (S0, -1, S0);               // decrement top of MAGIC
      asm.emitSTW   (S0, VM_Entrypoints.scratchStorageField.getOffset(), PROCESSOR_REGISTER);  // MAGIC + X is in memory
      fr1.resolve(asm);
      asm.emitLFD   (sro0, VM_Entrypoints.scratchStorageField.getOffset(), PROCESSOR_REGISTER);  // output is MAGIC + X
    //-#if RVM_WITH_QUICK_COMPILER_MOVE_ELIMINATION
    if (resultIsStackRegister)
      setRetargetable(returnValue, asm.getMachineCodeIndex());
    //-#endif
      asm.emitFSUB     (sro0, sro0, SF0);           // output is X
      cleanupRegisters();
    }
  }

  /**
   * Emit code to implement the i2d bytecode
   */
  protected final void emit_i2d() {
    if (unreachableBytecode) return;
    if (VM.BuildFor64Addr) {
      // XXX CJH TODO:
      if (VM.VerifyAssertions) VM._assert(NOT_REACHED);
    } else {
      assignRegisters(1, DOUBLE_TYPE);

      asm.emitLFDtoc(SF0, VM_Entrypoints.IEEEmagicField.getOffset(), S0);// F0 is MAGIC
      asm.emitSTFD  (SF0, VM_Entrypoints.scratchStorageField.getOffset(), PROCESSOR_REGISTER); // MAGIC in memory
      asm.emitSTW   (sri0,  VM_Entrypoints.scratchStorageField.getOffset()+4, PROCESSOR_REGISTER); // if 0 <= X, MAGIC + X 
      asm.emitCMPI  (sri0,  0);                   // is X < 0
      VM_ForwardReference fr1 = asm.emitForwardBC(GE);      // Now, handle X < 0
      asm.emitLInt   (S0, VM_Entrypoints.scratchStorageField.getOffset(), PROCESSOR_REGISTER);// S0 is top of MAGIC
      asm.emitADDI   (S0, -1, S0);               // decrement top of MAGIC
      asm.emitSTW   (S0, VM_Entrypoints.scratchStorageField.getOffset(), PROCESSOR_REGISTER);  // MAGIC + X is in memory
      fr1.resolve(asm);
      asm.emitLFD   (sro0, VM_Entrypoints.scratchStorageField.getOffset(), PROCESSOR_REGISTER);  // output is MAGIC + X
    //-#if RVM_WITH_QUICK_COMPILER_MOVE_ELIMINATION
    if (resultIsStackRegister)
      setRetargetable(returnValue, asm.getMachineCodeIndex());
    //-#endif
      asm.emitFSUB     (sro0, sro0, SF0);           // output is X
      cleanupRegisters();
    }
  }

  /**
   * Emit code to implement the l2i bytecode
   */
  protected final void emit_l2i() {
    if (unreachableBytecode) return;
    assignRegisters(1, WORD_TYPE);
    // all we need to do is move the lo-order word of the input
    // (which is in the higher register) to the formerly hi-order
    // word of the result (which is in the lower register)
    asm.emitMR(sro0, sri0+1);
    cleanupRegisters();
  }

  /**
   * Emit code to implement the l2f bytecode
   */
  protected final void emit_l2f() {
    if (unreachableBytecode) return;
    popToRegister(LONG_TYPE, T0);
    generateSysCall(8, VM_Entrypoints.sysLongToFloatIPField);
    pushFromRegister(FLOAT_TYPE, F0);
  }

  /**
   * Emit code to implement the l2d bytecode
   */
  protected final void emit_l2d() {
    if (unreachableBytecode) return;
    popToRegister(LONG_TYPE, T0);
    generateSysCall(8, VM_Entrypoints.sysLongToDoubleIPField);
    pushFromRegister(DOUBLE_TYPE, F0);
  }

  /**
   * Emit code to implement the f2i bytecode
   */
  protected final void emit_f2i() {
    if (unreachableBytecode) return;
    popToRegister(FLOAT_TYPE, F0);
    asm.emitFCMPU(F0, F0);
    VM_ForwardReference fr1 = asm.emitForwardBC(NE);
    // Normal case: F0 == F0 therefore not a NaN
    asm.emitFCTIWZ(F0, F0);
    if (VM.BuildFor64Addr) { 
      // XXX CJH TODO: pushLowDoubleAsInt(F0);
      if (VM.VerifyAssertions) VM._assert(NOT_REACHED);
    } else {
      asm.emitSTFD  (F0, VM_Entrypoints.scratchStorageField.getOffset(), PROCESSOR_REGISTER);
      asm.emitLWZ   (S0, VM_Entrypoints.scratchStorageField.getOffset() + 4, PROCESSOR_REGISTER);
    }
    VM_ForwardReference fr2 = asm.emitForwardB();
    fr1.resolve(asm);
    // A NaN => 0
    asm.emitLVAL  (S0, 0);
    fr2.resolve(asm);
    pushFromRegister(WORD_TYPE, S0);

  }

  /**
   * Emit code to implement the f2l bytecode
   */
  protected final void emit_f2l() {
    if (unreachableBytecode) return;
    popToRegister(FLOAT_TYPE, F0);
    generateSysCall(4, VM_Entrypoints.sysFloatToLongIPField);
    pushFromRegister(LONG_TYPE, T0);
  }

  /**
   * Emit code to implement the f2d bytecode
   */
  protected final void emit_f2d() {
    if (unreachableBytecode) return;
    // floats are already doubles!
    pop(FLOAT_TYPE);
    push(DOUBLE_TYPE);
  }

  /**
   * Emit code to implement the d2i bytecode
   */
  protected final void emit_d2i() {
    if (unreachableBytecode) return;
    popToRegister(DOUBLE_TYPE, F0);
    asm.emitFCTIWZ(F0, F0);
    pushFromRegister(WORD_TYPE, DOUBLE_TYPE, F0);
  }

  /**
   * Emit code to implement the d2l bytecode
   */
  protected final void emit_d2l() {
    if (unreachableBytecode) return;
    popToRegister(DOUBLE_TYPE, F0);
    generateSysCall(8, VM_Entrypoints.sysDoubleToLongIPField);
    pushFromRegister(LONG_TYPE, T0);
  }

  /**
   * Emit code to implement the d2f bytecode
   */
  protected final void emit_d2f() {
    if (unreachableBytecode) return;
    assignRegisters(1, FLOAT_TYPE);
    //-#if RVM_WITH_QUICK_COMPILER_MOVE_ELIMINATION
    if (resultIsStackRegister)
      setRetargetable(returnValue, asm.getMachineCodeIndex());
    //-#endif
    asm.emitFRSP(sro0, sri0);
    cleanupRegisters();
  }

  /**
   * Emit code to implement the i2b bytecode
   */
  protected final void emit_i2b() {
    if (unreachableBytecode) return;
    assignRegisters(1, WORD_TYPE);
    asm.emitSLWI (sro0, sri0, 24);
    //-#if RVM_WITH_QUICK_COMPILER_MOVE_ELIMINATION
    if (resultIsStackRegister)
      setRetargetable(returnValue, asm.getMachineCodeIndex());
    //-#endif
    asm.emitSRAWI(sro0, sro0, 24);
    cleanupRegisters();
  }

  /**
   * Emit code to implement the i2c bytecode
   */
  protected final void emit_i2c() {
    if (unreachableBytecode) return;
    assignRegisters(1, WORD_TYPE);
    //-#if RVM_WITH_QUICK_COMPILER_MOVE_ELIMINATION
    if (resultIsStackRegister)
      setRetargetable(returnValue, asm.getMachineCodeIndex());
    //-#endif
    asm.emitANDI(sro0, sri0, 0xFFFF);
    cleanupRegisters();
  }

  /**
   * Emit code to implement the i2s bytecode
   */
  protected final void emit_i2s() {
    if (unreachableBytecode) return;
    assignRegisters(1, WORD_TYPE);
    asm.emitSLWI (sro0, sri0, 16);
    //-#if RVM_WITH_QUICK_COMPILER_MOVE_ELIMINATION
    if (resultIsStackRegister)
      setRetargetable(returnValue, asm.getMachineCodeIndex());
    //-#endif
    asm.emitSRAWI(sro0, sro0, 16);
    cleanupRegisters();
  }


  /*
   * comparision ops
   */


  /**
   * Emit code to implement the lcmp bytecode
   */
  protected final void emit_lcmp() {
    if (unreachableBytecode) return;
    //-#if RVM_FOR_64_ADDR
    if (VM.VerifyAssertions) 
      VM._assert(NOT_REACHED);
    //-#else
    assignRegisters(2, WORD_TYPE);
    asm.emitCMP  (sri1, sri0);      // ah ? bh
    VM_ForwardReference fr_h_lt = asm.emitForwardBC(LT);
    VM_ForwardReference fr_h_gt = asm.emitForwardBC(GT);
    asm.emitCMPL (sri1+1, sri0+1);      // al ? bl (logical compare)
    VM_ForwardReference fr_l_lt = asm.emitForwardBC(LT);
    VM_ForwardReference fr_l_gt = asm.emitForwardBC(GT);
    asm.emitLVAL  (sro0,  0);      // a == b
    VM_ForwardReference fr_eq_done = asm.emitForwardB();
    fr_l_lt.resolve(asm);
    fr_h_lt.resolve(asm);
    asm.emitLVAL  (sro0, -1);      // a <  b
    VM_ForwardReference fr_lt_done = asm.emitForwardB();
    fr_h_gt.resolve(asm);
    fr_l_gt.resolve(asm);
    asm.emitLVAL  (sro0,  1);      // a >  b
    fr_eq_done.resolve(asm);
    fr_lt_done.resolve(asm);
    cleanupRegisters();
    //-#endif
  }

  /**
   * Emit code to implement the fcmpl bytecode
   */
  protected final void emit_fcmpl() {
    if (unreachableBytecode) return;
    assignRegisters(2, WORD_TYPE);
    asm.emitFCMPU(sri1, sri0);
    VM_ForwardReference fr_le = asm.emitForwardBC(LE);
    asm.emitLVAL  (sro0,  1); // the GT bit of CR0
    VM_ForwardReference fr_gt_done = asm.emitForwardB();
    fr_le.resolve(asm);
    VM_ForwardReference fr_eq = asm.emitForwardBC(EQ);
    asm.emitLVAL  (sro0, -1); // the LT or UO bits of CR0
    VM_ForwardReference fr_lt_done = asm.emitForwardB();
    fr_eq.resolve(asm);
    asm.emitLVAL  (sro0,  0);
    fr_gt_done.resolve(asm);
    fr_lt_done.resolve(asm);
    cleanupRegisters();
  }

  /**
   * Emit code to implement the fcmpg bytecode
   */
  protected final void emit_fcmpg() {
    if (unreachableBytecode) return;
    assignRegisters(2, WORD_TYPE);
      asm.emitFCMPU(sri1, sri0);
    VM_ForwardReference fr_ge = asm.emitForwardBC(GE);
    asm.emitLVAL  (sro0, -1);     // the LT bit of CR0
    VM_ForwardReference fr_lt_done = asm.emitForwardB();
    fr_ge.resolve(asm);
    VM_ForwardReference fr_eq = asm.emitForwardBC(EQ);
    asm.emitLVAL  (sro0,  1);     // the GT or UO bits of CR0
    VM_ForwardReference fr_gt_done = asm.emitForwardB();
    fr_eq.resolve(asm);
    asm.emitLVAL  (sro0,  0);     // the EQ bit of CR0
    fr_lt_done.resolve(asm);
    fr_gt_done.resolve(asm);
    cleanupRegisters();
  }
  /**
   * Emit code to implement the dcmpl bytecode
   */
  protected final void emit_dcmpl() {
    if (unreachableBytecode) return;
    assignRegisters(2, WORD_TYPE);
      asm.emitFCMPU(sri1, sri0);
      VM_ForwardReference fr_le = asm.emitForwardBC(LE);
      asm.emitLVAL  (sro0,  1); // the GT bit of CR0
      VM_ForwardReference fr_gt_done = asm.emitForwardB();
      fr_le.resolve(asm);
      VM_ForwardReference fr_eq = asm.emitForwardBC(EQ);
      asm.emitLVAL  (sro0, -1); // the LT or UO bits of CR0
      VM_ForwardReference fr_lt_done = asm.emitForwardB();
      fr_eq.resolve(asm);
      asm.emitLVAL  (sro0,  0);
      fr_gt_done.resolve(asm);
      fr_lt_done.resolve(asm);
    cleanupRegisters();
  }

  /**
   * Emit code to implement the dcmpg bytecode
   */
  protected final void emit_dcmpg() {
    if (unreachableBytecode) return;
    assignRegisters(2, WORD_TYPE);
      asm.emitFCMPU(sri1, sri0);
      VM_ForwardReference fr_ge = asm.emitForwardBC(GE);
      asm.emitLVAL  (sro0, -1); // the LT bit of CR0
      VM_ForwardReference fr_lt_done = asm.emitForwardB();
      fr_ge.resolve(asm);
      VM_ForwardReference fr_eq = asm.emitForwardBC(EQ);
      asm.emitLVAL  (sro0,  1); // the GT or UO bits of CR0
      VM_ForwardReference fr_gt_done = asm.emitForwardB();
      fr_eq.resolve(asm);
      asm.emitLVAL  (sro0,  0); // the EQ bit of CR0
      fr_lt_done.resolve(asm);
      fr_gt_done.resolve(asm);
    cleanupRegisters();
  }




//   /*
//    * branching
//    */


  /**
   * Emit code to implement the ifeg bytecode
   * @param bTarget target bytecode of the branch
   */
  protected final void emit_ifeq(int bTarget) {
    if (unreachableBytecode) {
      if (options.EDGE_COUNTERS)
        edgeCounterIdx += 2;
      return;
    }
    int r0 = popToAnyRegister(WORD_TYPE);
    //-#if RVM_WITH_QUICK_COMPILER_MOVE_ELIMINATION
    moveElimFinishBlock(currentStackHeight);
    //-#endif
    asm.emitADDICr(S0, r0,  0); // compares r0 to 0 and sets CR0 
    genCondBranch(EQ,  bTarget);
  }

  /**
   * Emit code to implement the ifne bytecode
   * @param bTarget target bytecode of the branch
   */
  protected final void emit_ifne(int bTarget) {
    if (unreachableBytecode) {
      if (options.EDGE_COUNTERS)
        edgeCounterIdx += 2;
      return;
    }
    int r0 = popToAnyRegister(WORD_TYPE);
    //-#if RVM_WITH_QUICK_COMPILER_MOVE_ELIMINATION
    moveElimFinishBlock(currentStackHeight);
    //-#endif
    asm.emitADDICr(S0, r0,  0); // compares r0 to 0 and sets CR0 
    genCondBranch(NE,  bTarget);
  }

  /**
   * Emit code to implement the iflt bytecode
   * @param bTarget target bytecode of the branch
   */
  protected final void emit_iflt(int bTarget)  {
    if (unreachableBytecode) {
      if (options.EDGE_COUNTERS)
        edgeCounterIdx += 2;
      return;
    }
    int r0 = popToAnyRegister(WORD_TYPE);
    //-#if RVM_WITH_QUICK_COMPILER_MOVE_ELIMINATION
    moveElimFinishBlock(currentStackHeight);
    //-#endif
    asm.emitADDICr(S0, r0,  0); // compares r0 to 0 and sets CR0 
    genCondBranch(LT,  bTarget);
  }


  /**
   * Emit code to implement the ifge bytecode
   * @param bTarget target bytecode of the branch
   */
  protected final void emit_ifge(int bTarget)  {
    if (unreachableBytecode) {
      if (options.EDGE_COUNTERS)
        edgeCounterIdx += 2;
      return;
    }
    int r0 = popToAnyRegister(WORD_TYPE);
    //-#if RVM_WITH_QUICK_COMPILER_MOVE_ELIMINATION
    moveElimFinishBlock(currentStackHeight);
    //-#endif
    asm.emitADDICr(S0, r0,  0); // compares r0 to 0 and sets CR0 
    genCondBranch(GE,  bTarget);
  }


  /**
   * Emit code to implement the ifgt bytecode
   * @param bTarget target bytecode of the branch
   */
  protected final void emit_ifgt(int bTarget)  {
    if (unreachableBytecode) {
      if (options.EDGE_COUNTERS)
        edgeCounterIdx += 2;
      return;
    }
    int r0 = popToAnyRegister(WORD_TYPE);
    //-#if RVM_WITH_QUICK_COMPILER_MOVE_ELIMINATION
    moveElimFinishBlock(currentStackHeight);
    //-#endif
    asm.emitADDICr(S0, r0,  0); // compares r0 to 0 and sets CR0 
    genCondBranch(GT,  bTarget);
  }


  /**
   * Emit code to implement the ifle bytecode
   * @param bTarget target bytecode of the branch
   */
  protected final void emit_ifle(int bTarget)  {
    if (unreachableBytecode) {
      if (options.EDGE_COUNTERS)
        edgeCounterIdx += 2;
      return;
    }
    int r0 = popToAnyRegister(WORD_TYPE);
    //-#if RVM_WITH_QUICK_COMPILER_MOVE_ELIMINATION
    moveElimFinishBlock(currentStackHeight);
    //-#endif
    asm.emitADDICr(S0, r0,  0); // compares r0 to 0 and sets CR0 
    genCondBranch(LE,  bTarget);
  }


  /**
   * Emit code to implement the if_icmpeq bytecode
   * @param bTarget target bytecode of the branch
   */
  protected final void emit_if_icmpeq(int bTarget) {
    if (unreachableBytecode) {
      if (options.EDGE_COUNTERS)
        edgeCounterIdx += 2;
      return;
    }
    int r1 = popToAnyRegister(WORD_TYPE);
    int r0 = popToAnyRegister(WORD_TYPE);
    //-#if RVM_WITH_QUICK_COMPILER_MOVE_ELIMINATION
    moveElimFinishBlock(currentStackHeight);
    //-#endif
    asm.emitCMP(r0, r1);    // sets CR0
    genCondBranch(EQ, bTarget);
  }

  /**
   * Emit code to implement the if_icmpne bytecode
   * @param bTarget target bytecode of the branch
   */
  protected final void emit_if_icmpne(int bTarget) {
    if (unreachableBytecode) {
      if (options.EDGE_COUNTERS)
        edgeCounterIdx += 2;
      return;
    }
    int r1 = popToAnyRegister(WORD_TYPE);
    int r0 = popToAnyRegister(WORD_TYPE);
    //-#if RVM_WITH_QUICK_COMPILER_MOVE_ELIMINATION
    moveElimFinishBlock(currentStackHeight);
    //-#endif
    asm.emitCMP(r0, r1);    // sets CR0
    genCondBranch(NE, bTarget);
  } 

  /**
   * Emit code to implement the if_icmplt bytecode
   * @param bTarget target bytecode of the branch
   */
  protected final void emit_if_icmplt(int bTarget) {
    if (unreachableBytecode) {
      if (options.EDGE_COUNTERS)
        edgeCounterIdx += 2;
      return;
    }
    int r1 = popToAnyRegister(WORD_TYPE);
    int r0 = popToAnyRegister(WORD_TYPE);
    //-#if RVM_WITH_QUICK_COMPILER_MOVE_ELIMINATION
    moveElimFinishBlock(currentStackHeight);
    //-#endif
    asm.emitCMP(r0, r1);    // sets CR0
    genCondBranch(LT, bTarget);
  }

  /**
   * Emit code to implement the if_icmpge bytecode
   * @param bTarget target bytecode of the branch
   */
  protected final void emit_if_icmpge(int bTarget) {
    if (unreachableBytecode) {
      if (options.EDGE_COUNTERS)
        edgeCounterIdx += 2;
      return;
    }
    int r1 = popToAnyRegister(WORD_TYPE);
    int r0 = popToAnyRegister(WORD_TYPE);
    //-#if RVM_WITH_QUICK_COMPILER_MOVE_ELIMINATION
    moveElimFinishBlock(currentStackHeight);
    //-#endif
    asm.emitCMP(r0, r1);    // sets CR0
    genCondBranch(GE, bTarget);
  }

  /**
   * Emit code to implement the if_icmpgt bytecode
   * @param bTarget target bytecode of the branch
   */
  protected final void emit_if_icmpgt(int bTarget) {
    if (unreachableBytecode) {
      if (options.EDGE_COUNTERS)
        edgeCounterIdx += 2;
      return;
    }
    int r1 = popToAnyRegister(WORD_TYPE);
    int r0 = popToAnyRegister(WORD_TYPE);
    //-#if RVM_WITH_QUICK_COMPILER_MOVE_ELIMINATION
    moveElimFinishBlock(currentStackHeight);
    //-#endif
    asm.emitCMP(r0, r1);    // sets CR0
    genCondBranch(GT, bTarget);
  }

  /**
   * Emit code to implement the if_icmple bytecode
   * @param bTarget target bytecode of the branch
   */
  protected final void emit_if_icmple(int bTarget) {
    if (unreachableBytecode) {
      if (options.EDGE_COUNTERS)
        edgeCounterIdx += 2;
      return;
    }
    int r1 = popToAnyRegister(WORD_TYPE);
    int r0 = popToAnyRegister(WORD_TYPE);
    //-#if RVM_WITH_QUICK_COMPILER_MOVE_ELIMINATION
    moveElimFinishBlock(currentStackHeight);
    //-#endif
    asm.emitCMP(r0, r1);    // sets CR0
    genCondBranch(LE, bTarget);
  }

  /**
   * Emit code to implement the if_acmpeq bytecode
   * @param bTarget target bytecode of the branch
   */
  protected final void emit_if_acmpeq(int bTarget) {
    if (unreachableBytecode) {
      if (options.EDGE_COUNTERS)
        edgeCounterIdx += 2;
      return;
    }
    int r1 = popToAnyRegister(OBJECT_TYPE);
    int r0 = popToAnyRegister(OBJECT_TYPE);
    //-#if RVM_WITH_QUICK_COMPILER_MOVE_ELIMINATION
    moveElimFinishBlock(currentStackHeight);
    //-#endif
    asm.emitCMP(r0, r1);    // sets CR0
    genCondBranch(EQ, bTarget);
  }

  /**
   * Emit code to implement the if_acmpne bytecode
   * @param bTarget target bytecode of the branch
   */
  protected final void emit_if_acmpne(int bTarget)  {
    if (unreachableBytecode) {
      if (options.EDGE_COUNTERS)
        edgeCounterIdx += 2;
      return;
    }
    int r1 = popToAnyRegister(OBJECT_TYPE);
    int r0 = popToAnyRegister(OBJECT_TYPE);
    //-#if RVM_WITH_QUICK_COMPILER_MOVE_ELIMINATION
    moveElimFinishBlock(currentStackHeight);
    //-#endif
    asm.emitCMP(r0, r1);    // sets CR0
    genCondBranch(NE, bTarget);
  }


  /**
   * Emit code to implement the ifnull bytecode
   * @param bTarget target bytecode of the branch
   */
  protected final void emit_ifnull(int bTarget) {
    if (unreachableBytecode) {
      if (options.EDGE_COUNTERS)
        edgeCounterIdx += 2;
      return;
    }
    int r0 = popToAnyRegister(OBJECT_TYPE);
    //-#if RVM_WITH_QUICK_COMPILER_MOVE_ELIMINATION
    moveElimFinishBlock(currentStackHeight);
    //-#endif
    asm.emitLVAL (S1,  0);
    asm.emitCMP(r0, S1);    // sets CR0
    genCondBranch(EQ, bTarget);
  }
  
  /**
   * Emit code to implement the ifnonnull bytecode
   * @param bTarget target bytecode of the branch
   */
  protected final void emit_ifnonnull(int bTarget) {
    if (unreachableBytecode) {
      if (options.EDGE_COUNTERS)
        edgeCounterIdx += 2;
      return;
    }
    int r0 = popToAnyRegister(OBJECT_TYPE);
    //-#if RVM_WITH_QUICK_COMPILER_MOVE_ELIMINATION
    moveElimFinishBlock(currentStackHeight);
    //-#endif
    asm.emitLVAL (S1,  0);
    asm.emitCMP(r0, S1);    // sets CR0
    genCondBranch(NE, bTarget);
  }

  /**
   * Emit code to implement the goto and gotow bytecodes
   * @param bTarget target bytecode of the branch
   */
  protected final void emit_goto(int bTarget)  {
    if (unreachableBytecode) return;
    //-#if RVM_WITH_QUICK_COMPILER_MOVE_ELIMINATION
    moveElimFinishBlock(currentStackHeight);
    //-#endif
    int mTarget = bytecodeMap[bTarget];
    asm.emitB(mTarget, bTarget);
  }


  /**
   * Emit code to implement the jsr and jsrw bytecode
   * @param bTarget target bytecode of the jsr
   */
  protected final void emit_jsr(int bTarget) {
    if (unreachableBytecode) return;
    //-#if RVM_WITH_QUICK_COMPILER_MOVE_ELIMINATION
    moveElimFinishBlock(currentStackHeight);
    //-#endif
    VM_ForwardReference fr = asm.emitForwardBL();
    fr.resolve(asm); // get PC into LR...
    int start = asm.getMachineCodeIndex();
    int delta = 4;
    asm.emitMFLR(S0);           // LR +  0
    asm.emitADDI (S0, delta*INSTRUCTION_WIDTH, S0);   // LR +  4  
    pushFromRegister(RETURN_ADDRESS_TYPE, S0);
    asm.emitBL(bytecodeMap[bTarget], bTarget); // LR + 12
    int done = asm.getMachineCodeIndex();
    if (VM.VerifyAssertions) VM._assert((done - start) == delta);
  }

  /**
   * Emit code to implement the ret bytecode
   * @param index local variable containing the return address
   */
  protected final void emit_ret(int index) {
    if (unreachableBytecode) return;
    //-#if RVM_WITH_QUICK_COMPILER_MOVE_ELIMINATION
    // This is a bit of overkill.
    flushSlot(localIndexToSlotIndex(index), currentStackHeight);
    //-#endif
    // TODO: the local could already be in a register, so we don't
    // need to move it into S0...
    genLoadLocalToRegister(RETURN_ADDRESS_TYPE, index, S0);
    //-#if RVM_WITH_QUICK_COMPILER_MOVE_ELIMINATION
    moveElimFinishBlock(currentStackHeight);
    //-#endif
    asm.emitMTLR(S0);
    asm.emitBCLR ();
  }

  /**
   * Emit code to implement the tableswitch bytecode
   * @param defaultval bcIndex of the default target
   * @param low low value of switch
   * @param high high value of switch
   */
  protected final void emit_tableswitch(int defaultval, int low, int high) {
    if (unreachableBytecode) {
      edgeCounterIdx += high-low+2; // allocate n+1 counters
      return;
    }
    assignRegisters(1, VOID_TYPE); // three fixed scratch regs
    // S0 -- holds purely temporary values
    // S1 -- edge counter address
    // fxw0 -- index/offset
    int fxw0 = getTempRegister(WORD_TYPE);

      
    //-#if RVM_WITH_QUICK_COMPILER_MOVE_ELIMINATION
    moveElimFinishBlock(currentStackHeight-1);
    //-#endif

    int bTarget = biStart + defaultval;
    int mTarget = bytecodeMap[bTarget];
    int n = high-low+1;       // n = number of normal cases (0..n-1)
    int firstCounter = edgeCounterIdx; // only used if options.EDGE_COUNTERS;

    // sri0 is index
    if (asm.fits(16, -low)) {
      asm.emitADDI(fxw0, -low, sri0);
    } else {
      asm.emitLVAL(S0, low);
      asm.emitSUBFC (fxw0, S0, sri0); 
    }
    asm.emitLVAL(S1, n);
    asm.emitCMPL(fxw0, S1);
    if (options.EDGE_COUNTERS) {
      edgeCounterIdx += n+1; // allocate n+1 counters
      // Load counter array for this method
      asm.emitLAddrToc (S1, VM_Entrypoints.edgeCountersField.getOffset());
      asm.emitLAddrOffset(S1, S1, getEdgeCounterOffset());
      
      VM_ForwardReference fr = asm.emitForwardBC(LT); // jump around jump to default target
      incEdgeCounter(S1, S0, firstCounter + n);
      asm.emitB (mTarget, bTarget);
      fr.resolve(asm);
    } else {
      // conditionally jump to default target
      if (bTarget - SHORT_FORWARD_LIMIT < biStart) {
        asm.emitShortBC(GE, mTarget, bTarget);
      } else {
        asm.emitBC  (GE, mTarget, bTarget); 
      }
    }
    VM_ForwardReference fr1 = asm.emitForwardBL();
    for (int i=0; i<n; i++) {
      int offset = bcodes.getTableSwitchOffset(i);
      bTarget = biStart + offset;
      mTarget = bytecodeMap[bTarget];
      asm.emitSwitchCase(i, mTarget, bTarget);
    }
    bcodes.skipTableSwitchOffsets(n);
    fr1.resolve(asm);
    asm.emitMFLR(S0);         // S0 is base of table
    asm.emitSLWI (fxw0, fxw0,  LOG_BYTES_IN_INT); // convert to bytes
    if (options.EDGE_COUNTERS) {
      incEdgeCounterIdx(S1, S0, firstCounter, fxw0);
      asm.emitMFLR(S0);         // restore base of table
    }
    asm.emitLIntX  (fxw0, fxw0, S0); // fxw0 is relative offset of desired case
    asm.emitADD  (S0, S0, fxw0); // S0 is absolute address of desired case
    asm.emitMTCTR(S0);
    asm.emitBCCTR ();
    releaseTempRegisters();
  } 
  
  /**
   * Emit code to implement the lookupswitch bytecode
   * @param defaultval bcIndex of the default target
   * @param npairs number of pairs in the lookup switch
   */
  protected final void emit_lookupswitch(int defaultval, int npairs) {
    if (unreachableBytecode) {
      edgeCounterIdx += npairs + 1;
      return;
    }
    assignRegisters(1, VOID_TYPE); // three scratch registers
    // S0 -- holds purely temporary values
    // S1 -- edge counter address
    
    //-#if RVM_WITH_QUICK_COMPILER_MOVE_ELIMINATION
    moveElimFinishBlock(currentStackHeight-1);
    //-#endif
    if (options.EDGE_COUNTERS) {
      // Load counter array for this method
      asm.emitLAddrToc (S1, VM_Entrypoints.edgeCountersField.getOffset());
      asm.emitLAddrOffset(S1, S1, getEdgeCounterOffset());
    }

    // sri0 is key
    for (int i=0; i<npairs; i++) {
      int match   = bcodes.getLookupSwitchValue(i);
      if (asm.fits(match, 16)) {
        asm.emitCMPI(sri0, match);
      } else {
        asm.emitLVAL(S0, match);
        asm.emitCMP(sri0, S0);
      }
      int offset  = bcodes.getLookupSwitchOffset(i);
      int bTarget = biStart + offset;
      int mTarget = bytecodeMap[bTarget];
      if (options.EDGE_COUNTERS) {
        // Flip conditions so we can jump over the increment of the taken counter.
        VM_ForwardReference fr = asm.emitForwardBC(NE);
        // Increment counter & jump to target
        incEdgeCounter(S1, S0, edgeCounterIdx++);
        asm.emitB(mTarget, bTarget);
        fr.resolve(asm);
      } else {
        if (bTarget - SHORT_FORWARD_LIMIT < biStart) {
          asm.emitShortBC(EQ, mTarget, bTarget);
        } else {
          asm.emitBC(EQ, mTarget, bTarget);
        }
      }
    }
    bcodes.skipLookupSwitchPairs(npairs);
    int bTarget = biStart + defaultval;
    int mTarget = bytecodeMap[bTarget];
    if (options.EDGE_COUNTERS) {
      incEdgeCounter(S1, S0, edgeCounterIdx++);
    }
    asm.emitB(mTarget, bTarget);
  }


//   /*
//    * returns (from function NOT ret)
//    */


  /**
   * Emit code to implement the ireturn bytecode
   */
  protected final void emit_ireturn()  {
    if (unreachableBytecode) return;
    if (method.isSynchronized()) genSynchronizedMethodEpilogue();
    popToRegister(WORD_TYPE, T0);
    genEpilogue();
  }

  /**
   * Emit code to implement the lreturn bytecode
   */
  protected final void emit_lreturn()  {
    if (unreachableBytecode) return;

    if (method.isSynchronized()) genSynchronizedMethodEpilogue();
    popToRegister(LONG_TYPE, T0);
    genEpilogue();
  }

  /**
   * Emit code to implement the freturn bytecode
   */
  protected final void emit_freturn()  {
    if (unreachableBytecode) return;

    if (method.isSynchronized()) genSynchronizedMethodEpilogue();
    popToRegister(FLOAT_TYPE, F0);
    genEpilogue();
  }

  /**
   * Emit code to implement the dreturn bytecode
   */
  protected final void emit_dreturn()  {
    if (unreachableBytecode) return;

    if (method.isSynchronized()) genSynchronizedMethodEpilogue();
    popToRegister(DOUBLE_TYPE, F0);
    genEpilogue();
  }

  /**
   * Emit code to implement the areturn bytecode
   */
  protected final void emit_areturn()  {
    if (unreachableBytecode) return;

    if (method.isSynchronized()) genSynchronizedMethodEpilogue();
    popToRegister(OBJECT_TYPE, T0);
    genEpilogue();
  }

  /**
   * Emit code to implement the return bytecode
   */
  protected final void emit_return()  {
    if (unreachableBytecode) return;
    if (method.isSynchronized()) genSynchronizedMethodEpilogue();
    genEpilogue();
  }


//   /*
//    * field access
//    */


  /**
   * Emit code to implement a dynamically linked getstatic
   * @param fieldRef the referenced field
   */
  protected final void emit_unresolved_getstatic(VM_FieldReference fieldRef) {
    if (unreachableBytecode) return;
    byte javaStackType = VM_BuildBB.getJavaStackType(fieldRef.getFieldContentsType());

    int offsetRegister = getTempRegister(WORD_TYPE);
    
    emitDynamicLinkingSequence(offsetRegister, fieldRef, true);
        
    assignRegisters(0, javaStackType);

    switch (javaStackType) {
    case FLOAT_TYPE:
      asm.emitLFSX(sro0, offsetRegister, JTOC); 
      break;
    case DOUBLE_TYPE:
      asm.emitLFDX(sro0, offsetRegister, JTOC); 
      break;
    case LONG_TYPE:
      asm.emitLWZX (sro0,      offsetRegister,  JTOC);
      asm.emitADDI(offsetRegister, 4, offsetRegister);
      asm.emitLWZX (sro0+1,    offsetRegister,  JTOC);
      break;
    default:
      asm.emitLWZX(sro0, offsetRegister, JTOC); 
      break;
    }
    cleanupRegisters();
  }

  /**
   * Emit code to implement a getstatic
   * @param fieldRef the referenced field
   */
  protected final void emit_resolved_getstatic(VM_FieldReference fieldRef) {
    if (unreachableBytecode) return;
    byte javaStackType =  VM_BuildBB.getJavaStackType(fieldRef.getFieldContentsType());
    assignRegisters(0, javaStackType);

    int fieldOffset = fieldRef.peekResolvedField().getOffset();
        
    switch (javaStackType) {
    case FLOAT_TYPE:
      asm.emitLFStoc (sro0, fieldOffset, S0);
      break;
    case DOUBLE_TYPE:
      asm.emitLFDtoc (sro0, fieldOffset, S0);
      break;
    case LONG_TYPE:
      asm.emitLWZtoc(sro0,      fieldOffset);
      asm.emitLWZtoc(sro0+1,    fieldOffset+4);
      break;
    default:
      asm.emitLWZtoc (sro0, fieldOffset);
      break;
    }
    cleanupRegisters();
  }


  /**
   * Emit code to implement a dynamically linked putstatic
   * @param fieldRef the referenced field
   */
  protected final void emit_unresolved_putstatic(VM_FieldReference fieldRef) {
    if (unreachableBytecode) return;
    byte javaStackType =  stack[currentStackHeight-1];

    // putstatic barrier currently unsupported
    //     if (MM_Interface.NEEDS_WRITE_BARRIER && !fieldRef.getFieldContentsType().isPrimitiveType()) {
    //       VM_Barriers.compilePutstaticBarrier(this); // NOTE: offset is in T1 from emitDynamicLinkingSequence
    //       emitDynamicLinkingSequence(T1, fieldRef, false);
    //     }
    int offsetRegister = getTempRegister(WORD_TYPE);
    
    emitDynamicLinkingSequence(offsetRegister, fieldRef, true);
    assignRegisters(1, VOID_TYPE);
    switch (javaStackType) {
    case FLOAT_TYPE:
      asm.emitSTFSX(sri0, offsetRegister, JTOC); 
      break;
    case DOUBLE_TYPE:
      asm.emitSTFDX(sri0, offsetRegister, JTOC); 
      break;
    case LONG_TYPE:
      asm.emitSTWX (sri0,      offsetRegister,  JTOC);
      asm.emitADDI(offsetRegister, 4, offsetRegister);
      asm.emitSTWX (sri0+1,    offsetRegister,  JTOC);
      break;
    default:
      asm.emitSTWX(sri0, offsetRegister, JTOC);
      break;
    }
    cleanupRegisters();
  }

  /**
   * Emit code to implement a putstatic
   * @param fieldRef the referenced field
   */
  protected final void emit_resolved_putstatic(VM_FieldReference fieldRef) {
    if (unreachableBytecode) return;
    byte javaStackType =  VM_BuildBB.getJavaStackType(fieldRef.getFieldContentsType());
    assignRegisters(1, VOID_TYPE);

    int fieldOffset = fieldRef.peekResolvedField().getOffset();
    // putstatic barrier currently unsupported
    //     if (MM_Interface.NEEDS_WRITE_BARRIER && !fieldRef.getFieldContentsType().isPrimitiveType()) {
    //       VM_Barriers.compilePutstaticBarrierImm(this, fieldOffset);
    //     }
    switch (javaStackType) {
    case FLOAT_TYPE:
      asm.emitSTFStoc (sri0, fieldOffset, S0);
      break;
    case DOUBLE_TYPE:
      asm.emitSTFDtoc(sri0, fieldOffset, S0); 
      break;
    case LONG_TYPE:
      asm.emitSTWtoc(sri0,      fieldOffset,   S0);
      asm.emitSTWtoc(sri0+1,    fieldOffset+4, S0);
      break;
    default:
      asm.emitSTWtoc(sri0, fieldOffset, S0);
      break;
    }
    cleanupRegisters();
  }

  
  /**
   * Emit code to implement a dynamically linked getfield
   * @param fieldRef the referenced field
   */
  protected final void emit_unresolved_getfield(VM_FieldReference fieldRef) {
    if (unreachableBytecode) return;
    byte javaStackType =  VM_BuildBB.getJavaStackType(fieldRef.getFieldContentsType());
    // offsetRegister -- field offset in object
    // S1 -- scratch register
    
    int offsetRegister = getTempRegister(WORD_TYPE);
    
    emitDynamicLinkingSequence(offsetRegister, fieldRef, true);
        
    assignRegisters(1, javaStackType);

    switch (javaStackType) {
    case FLOAT_TYPE:
      asm.emitLFSX(sro0, offsetRegister, sri0); 
      break;
    case DOUBLE_TYPE:
      asm.emitLFDX(sro0, offsetRegister, sri0); 
      break;
    case LONG_TYPE:
      int r;
      if (sro0 == sri0) {   // might be reused for efficiency
        r = S1;
        asm.emitMR(r, sri0);
      } else
        r = sri0;
      asm.emitLWZX (sro0,      offsetRegister,  r);
      asm.emitADDI(offsetRegister, 4, offsetRegister);
      asm.emitLWZX (sro0+1,    offsetRegister,  r);
      break;
    default:
      asm.emitLWZX(sro0, offsetRegister, sri0); 
      break;
    }
    cleanupRegisters();
  }
  
  /**
   * Emit code to implement a getfield
   * @param fieldRef the referenced field
   */
  protected final void emit_resolved_getfield(VM_FieldReference fieldRef) {
    if (unreachableBytecode) return;
    byte javaStackType =  VM_BuildBB.getJavaStackType(fieldRef.getFieldContentsType());
    int fieldOffset = fieldRef.peekResolvedField().getOffset();
    assignRegisters(1, javaStackType);
    switch (javaStackType) {
    case FLOAT_TYPE:
      asm.emitLFS (sro0, fieldOffset, sri0);
      break;
    case DOUBLE_TYPE:
      asm.emitLFD (sro0, fieldOffset, sri0);
      break;
    case LONG_TYPE:
      asm.emitLWZ(sro0+1,    fieldOffset+4, sri0);
      asm.emitLWZ(sro0,      fieldOffset,   sri0);
      break;
    default:
      asm.emitLWZ (sro0, fieldOffset, sri0);
      break;
    }
    cleanupRegisters();
  }


  /**
   * Emit code to implement a dynamically linked putfield
   * @param fieldRef the referenced field
   */
  protected final void emit_unresolved_putfield(VM_FieldReference fieldRef) {
    if (unreachableBytecode) return;
    byte javaStackType =  VM_BuildBB.getJavaStackType(fieldRef.getFieldContentsType());
    
    int offsetReg = getTempRegister(WORD_TYPE);

    emitDynamicLinkingSequence(offsetReg, fieldRef, true);
        
    assignRegisters(2, VOID_TYPE);

    if (MM_Interface.NEEDS_WRITE_BARRIER &&
        !fieldRef.getFieldContentsType().isPrimitiveType()) {
      VM_QuickBarriers.compilePutfieldBarrier(asm, offsetReg, sri1, sri0,
                                              fieldRef.getId(), S1); 
      emitDynamicLinkingSequence(offsetReg, fieldRef, false);
    } else {
      switch (javaStackType) {
      case FLOAT_TYPE:
        asm.emitSTFSX(sri0, offsetReg, sri1); 
        break;
      case DOUBLE_TYPE:
        asm.emitSTFDX(sri0, offsetReg, sri1); 
        break;
      case LONG_TYPE:
        asm.emitSTWX (sri0,      offsetReg,  sri1);
        asm.emitADDI(offsetReg, 4, offsetReg);
        asm.emitSTWX (sri0+1,    offsetReg,  sri1);

        break;
      default:
        asm.emitSTWX(sri0, offsetReg, sri1); 
        break;
      }
          
    }
    cleanupRegisters();
  }

  /**
   * Emit code to implement a putfield
   * @param fieldRef the referenced field
   */
  protected final void emit_resolved_putfield(VM_FieldReference fieldRef) {
    if (unreachableBytecode) return;

    byte javaStackType =  VM_BuildBB.getJavaStackType(fieldRef.getFieldContentsType());
    assignRegisters(2, VOID_TYPE);
    // sri0 -- value
    // sri1 -- object

    int fieldOffset = fieldRef.peekResolvedField().getOffset();
    if (MM_Interface.NEEDS_WRITE_BARRIER &&
        !fieldRef.getFieldContentsType().isPrimitiveType()) {
      VM_QuickBarriers.compilePutfieldBarrierImm(asm, fieldOffset,
                                                 sri1, sri0,
                                                 fieldRef.getId(),
                                                 S0);
    } else {
      switch (javaStackType) {
      case FLOAT_TYPE:
        asm.emitSTFS (sri0, fieldOffset, sri1);
        break;
      case DOUBLE_TYPE:
        asm.emitSTFD (sri0, fieldOffset, sri1);
        break;
      case LONG_TYPE:
        asm.emitSTW(sri0,      fieldOffset,   sri1);
        asm.emitSTW(sri0+1,    fieldOffset+4, sri1);
        break;
      default:
        asm.emitSTW(sri0, fieldOffset, sri1);
        break;
      }
    }
    cleanupRegisters();
  }
    

  /*
   * method invocation
   */

  /**
   * Emit code to implement a dynamically linked invokevirtual
   * @param methodRef the referenced method
   */
  protected final void emit_unresolved_invokevirtual(VM_MethodReference methodRef) {
    if (unreachableBytecode) return;
    int methodRefParameterWords = methodRef.getParameterWords() + 1; // +1 for "this" parameter
    int thisOffset = currentStackHeight -
      methodRefParameterWords;
    
    int offsetRegister = getTempRegister(WORD_TYPE);
    
    emitDynamicLinkingSequence(offsetRegister, methodRef, true); // leaves method offset in offsetRegister
    peekToRegister(OBJECT_TYPE, thisOffset, T0); // load this
    VM_ObjectModel.baselineEmitLoadTIB(asm, S1, T0); // load TIB
    asm.emitLAddrX(offsetRegister, offsetRegister, S1);
    asm.emitMTCTR(offsetRegister);
    genMoveParametersToRegisters(true, true, methodRef);
    asm.emitBCCTRL();
    genPopParametersAndPushReturnValue(true, methodRef);


  }

  /**
   * Emit code to implement invokevirtual
   * @param methodRef the referenced method
   */
  protected final void emit_resolved_invokevirtual(VM_MethodReference methodRef) {
    if (unreachableBytecode) return;
    int methodRefParameterWords = methodRef.getParameterWords() + 1; // +1 for "this" parameter
    int thisOffset = currentStackHeight -
      methodRefParameterWords;
    peekToRegister(OBJECT_TYPE, thisOffset, T0); // load this
    VM_ObjectModel.baselineEmitLoadTIB(asm, S1, T0); // load TIB
    int methodOffset = methodRef.peekResolvedMethod().getOffset();
    asm.emitLAddr(S1, methodOffset, S1);
    asm.emitMTCTR(S1);
    genMoveParametersToRegisters(true, true, methodRef);
    asm.emitBCCTRL();
    genPopParametersAndPushReturnValue(true, methodRef);
  }


  /**
   * Emit code to implement a dynamically linked invokespecial
   * @param methodRef the referenced method
   * @param target the method to invoke
   */
  protected final void emit_resolved_invokespecial(VM_MethodReference methodRef, VM_Method target) {
    if (unreachableBytecode) return;
    if (target.isObjectInitializer()) { // invoke via method's jtoc slot
      asm.emitLAddrToc(S0, target.getOffset());
    } else { // invoke via class's tib slot
      if (VM.VerifyAssertions) VM._assert(!target.isStatic());
      asm.emitLAddrToc(S0, target.getDeclaringClass().getTibOffset());
      asm.emitLAddr(S0, target.getOffset(), S0);
    }
    asm.emitMTCTR(S0);
    genMoveParametersToRegisters(true, false, methodRef);
    asm.emitBCCTRL();
    genPopParametersAndPushReturnValue(true, methodRef);
  }

  /**
   * Emit code to implement invokespecial
   * @param methodRef the referenced method
   */
  protected final void emit_unresolved_invokespecial(VM_MethodReference methodRef) {
    if (unreachableBytecode) return;
    // must be a static method; if it was a super then declaring class _must_ be resolved
    int offsetRegister = getTempRegister(WORD_TYPE);
    
    emitDynamicLinkingSequence(offsetRegister, methodRef, true); // leaves method offset in offsetRegister
    asm.emitLAddrX(offsetRegister, offsetRegister, JTOC); 
    asm.emitMTCTR(offsetRegister);
    genMoveParametersToRegisters(true, false, methodRef);
    asm.emitBCCTRL();
    genPopParametersAndPushReturnValue(true, methodRef);
  }


  /**
   * Emit code to implement a dynamically linked invokestatic
   * @param methodRef the referenced method
   */
  protected final void emit_unresolved_invokestatic(VM_MethodReference methodRef) {
    if (unreachableBytecode) return;
    int offsetRegister = getTempRegister(WORD_TYPE);
    
    emitDynamicLinkingSequence(offsetRegister, methodRef, true);                      // leaves method offset in offsetRegister
    asm.emitLAddrX(offsetRegister, offsetRegister, JTOC); // method offset left in offsetRegister by emitDynamicLinkingSequence
    asm.emitMTCTR(offsetRegister);
    genMoveParametersToRegisters(false, false, methodRef);
    asm.emitBCCTRL();
    genPopParametersAndPushReturnValue(false, methodRef);
  }

  /**
   * Emit code to implement invokestatic
   * @param methodRef the referenced method
   */
  protected final void emit_resolved_invokestatic(VM_MethodReference methodRef) {
    if (unreachableBytecode) return;
    int methodOffset = methodRef.peekResolvedMethod().getOffset();
    asm.emitLAddrToc(S0, methodOffset);
    asm.emitMTCTR(S0);
    genMoveParametersToRegisters(false, false, methodRef);
    asm.emitBCCTRL();
    genPopParametersAndPushReturnValue(false, methodRef);
  }

  //-#if RVM_WITH_OSR
  protected final void emit_invoke_compiledmethod(VM_CompiledMethod cm)
  protected VM_ForwardReference emit_pending_goto(int origidx)
  //-#endif

  /**
   * Emit code to implement the invokeinterface bytecode
   * @param methodRef the referenced method
   * @param count number of parameter words (see invokeinterface bytecode)
   */
    protected final void emit_invokeinterface(VM_MethodReference methodRef) {
    if (unreachableBytecode) return;
    int methodRefParameterWords = methodRef.getParameterWords() + 1; // +1 for "this" parameter
    int thisOffset = currentStackHeight -
      methodRefParameterWords;
    VM_Method resolvedMethod = null;
    resolvedMethod = methodRef.peekInterfaceMethod();

    // (1) Emit dynamic type checking sequence if required to 
    // do so inline.
    if (VM.BuildForIMTInterfaceInvocation || 
        (VM.BuildForITableInterfaceInvocation && 
         VM.DirectlyIndexedITables)) {
      if (resolvedMethod == null) {
        // Can't successfully resolve it at compile time.
        // Call uncommon case typechecking routine to do the right thing when this code actually executes.
        asm.emitLAddrToc(T0, VM_Entrypoints.unresolvedInvokeinterfaceImplementsTestMethod.getOffset());
        asm.emitMTCTR(T0);
        asm.emitLVAL (T0, methodRef.getId());            // id of method reference we are trying to call
        peekToRegister(OBJECT_TYPE, thisOffset, T1); // the "this" object
        VM_ObjectModel.baselineEmitLoadTIB(asm,T1,T1);
        asm.emitBCCTRL();                 // throw exception, if link error
      } else {
        // normal case.  Not a ghost ref.
        asm.emitLAddrToc(T0, VM_Entrypoints.invokeinterfaceImplementsTestMethod.getOffset());
        asm.emitMTCTR(T0);
        asm.emitLAddrToc(T0, resolvedMethod.getDeclaringClass().getTibOffset()); // tib of the interface method
        asm.emitLAddr(T0, TIB_TYPE_INDEX << LOG_BYTES_IN_ADDRESS, T0);                   // type of the interface method
        peekToRegister(OBJECT_TYPE, thisOffset, T1); // the "this" object
        VM_ObjectModel.baselineEmitLoadTIB(asm,T1,T1);
        asm.emitBCCTRL();                              // throw exception, if link error
      }
    }
    // (2) Emit interface invocation sequence.
    if (VM.BuildForIMTInterfaceInvocation) {
      VM_InterfaceMethodSignature sig = VM_InterfaceMethodSignature.findOrCreate(methodRef);
      int offset = sig.getIMTOffset();
      genMoveParametersToRegisters(true, false, methodRef); // T0 is "this"
      VM_ObjectModel.baselineEmitLoadTIB(asm,S0,T0);
      if (VM.BuildForIndirectIMT) {
        // Load the IMT base into S0
        asm.emitLAddr(S0, TIB_IMT_TIB_INDEX << LOG_BYTES_IN_ADDRESS, S0);
      }
      asm.emitLAddr(S0, offset, S0);                  // the method address
      asm.emitMTCTR(S0);
      asm.emitLVAL(S1, sig.getId());      // pass "hidden" parameter in S1 scratch  register
      asm.emitBCCTRL();
    } else if (VM.BuildForITableInterfaceInvocation && 
               VM.DirectlyIndexedITables && 
               resolvedMethod != null) {
      VM_Class I = resolvedMethod.getDeclaringClass();
      genMoveParametersToRegisters(true, false, methodRef);        //T0 is "this"
      VM_ObjectModel.baselineEmitLoadTIB(asm,S0,T0);
      asm.emitLAddr (S0, TIB_ITABLES_TIB_INDEX << LOG_BYTES_IN_ADDRESS, S0); // iTables 
      asm.emitLAddr (S0, I.getInterfaceId() << LOG_BYTES_IN_ADDRESS, S0);  // iTable
      int idx = VM_InterfaceInvocation.getITableIndex(I, methodRef.getName(), methodRef.getDescriptor());
      asm.emitLAddr(S0, idx << LOG_BYTES_IN_ADDRESS, S0); // the method to call
      asm.emitMTCTR(S0);
      asm.emitBCCTRL();
    } else {
      int itableIndex = -1;
      if (VM.BuildForITableInterfaceInvocation && resolvedMethod != null) {
        // get the index of the method in the Itable
        itableIndex = VM_InterfaceInvocation.getITableIndex(resolvedMethod.getDeclaringClass(), 
                                                            methodRef.getName(), methodRef.getDescriptor());
      }

      if (itableIndex == -1) {
        // itable index is not known at compile-time.
        // call "invokeInterface" to resolve object + method id into method address
        int methodRefId = methodRef.getId();
        asm.emitLAddrToc(T0, VM_Entrypoints.invokeInterfaceMethod.getOffset());
        asm.emitMTCTR(T0);
        peekToRegister(OBJECT_TYPE, thisOffset, T0); // the "this" object
        asm.emitLVAL(T1, methodRefId);        // method id
        asm.emitBCCTRL();       // T0 := resolved method address
        asm.emitMTCTR(T0);
        genMoveParametersToRegisters(true, false, methodRef);
        asm.emitBCCTRL();
      } else {
        // itable index is known at compile-time.
        // call "findITable" to resolve object + interface id into 
        // itable address
        asm.emitLAddrToc(T0, VM_Entrypoints.findItableMethod.getOffset());
        asm.emitMTCTR(T0);
        peekToRegister(OBJECT_TYPE, thisOffset, T0); // the "this" object
        VM_ObjectModel.baselineEmitLoadTIB(asm,T0,T0);
        asm.emitLVAL(T1, resolvedMethod.getDeclaringClass().getInterfaceId());    // interface id
        asm.emitBCCTRL();   // T0 := itable reference
        asm.emitLAddr(T0, itableIndex << LOG_BYTES_IN_ADDRESS, T0); // T0 := the method to call
        asm.emitMTCTR(T0);
        genMoveParametersToRegisters(true, false, methodRef);        //T0 is "this"
        asm.emitBCCTRL();
      }
    }
    genPopParametersAndPushReturnValue(true, methodRef);
  }
 

  /*
   * other object model functions
   */ 


  /**
   * Emit code to allocate a scalar object
   * @param type the VM_Class to instantiate
   */
  protected final void emit_resolved_new(VM_Class typeRef) {
    if (unreachableBytecode) return;
    int instanceSize = typeRef.getInstanceSize();
    int tibOffset = typeRef.getTibOffset();
    int whichAllocator = MM_Interface.pickAllocator(typeRef, method);
    int align = VM_ObjectModel.getAlignment(typeRef);
    int offset = VM_ObjectModel.getOffsetForAlignment(typeRef);
    asm.emitLAddrToc(S0, VM_Entrypoints.resolvedNewScalarMethod.getOffset());
    asm.emitMTCTR(S0);
    asm.emitLVAL(T0, instanceSize);
    asm.emitLAddrToc(T1, tibOffset);
    asm.emitLVAL(T2, typeRef.hasFinalizer()?1:0);
    asm.emitLVAL(T3, whichAllocator);
    asm.emitLVAL(T4, align);
    asm.emitLVAL(T5, offset);
    asm.emitBCCTRL();
    pushFromRegister(OBJECT_TYPE, T0);
  }

  /**
   * Emit code to dynamically link and allocate a scalar object
   * @param typeRef typeReference to dynamically link & instantiate
   */
  protected final void emit_unresolved_new(VM_TypeReference typeRef) {
    if (unreachableBytecode) return;
    asm.emitLAddrToc(S0, VM_Entrypoints.unresolvedNewScalarMethod.getOffset());
    asm.emitMTCTR(S0);
    asm.emitLVAL(T0, typeRef.getId());
    asm.emitBCCTRL();
    pushFromRegister(OBJECT_TYPE, T0);
  }

  /**
   * Emit code to allocate an array
   * @param array the VM_Array to instantiate
   */
  protected final void emit_resolved_newarray(VM_Array array) {
    if (unreachableBytecode) return;
    int width      = array.getLogElementSize();
    int tibOffset  = array.getTibOffset();
    int headerSize = VM_ObjectModel.computeArrayHeaderSize(array);
    int whichAllocator = MM_Interface.pickAllocator(array, method);
    int align = VM_ObjectModel.getAlignment(array);
    int offset = VM_ObjectModel.getOffsetForAlignment(array);
    asm.emitLAddrToc (S0, VM_Entrypoints.resolvedNewArrayMethod.getOffset());
    asm.emitMTCTR(S0);
    popToRegister(WORD_TYPE, T0);     // T0 := number of elements
    asm.emitLVAL (T1, width);         // T1 := log element size
    asm.emitLVAL (T2, headerSize);    // T2 := header bytes
    asm.emitLAddrToc(T3, tibOffset);  // T3 := tib
    asm.emitLVAL (T4, whichAllocator);// T4 := allocator
    asm.emitLVAL(T5, align);
    asm.emitLVAL(T6, offset);
    asm.emitBCCTRL();
    pushFromRegister(OBJECT_TYPE, T0);
  }

  /**
   * Emit code to dynamically link the element class and allocate an array
   * @param typeRef typeReference to dynamically link & instantiate
   */
  protected final void emit_unresolved_newarray(VM_TypeReference typeRef) {
    if (unreachableBytecode) return;
    asm.emitLAddrToc (S0, VM_Entrypoints.unresolvedNewArrayMethod.getOffset());
    asm.emitMTCTR(S0);
    popToRegister(WORD_TYPE, T0);            // T0 := number of elements
    asm.emitLVAL (T1, typeRef.getId());      // T1 := id of type ref
    asm.emitBCCTRL();
    pushFromRegister(OBJECT_TYPE, T0);
  }

  /**
   * Emit code to allocate a multi-dimensional array
   * @param typeRef typeReference to dynamically link & instantiate
   * @param dimensions the number of dimensions
   */
  protected final void emit_multianewarray(VM_TypeReference typeRef, int dimensions) {
    if (unreachableBytecode) return;
    // Dimensions must be in a continuous block of memory. Since even
    // stack slots that are ordinarily kept in registers still have a
    // reserved spot in the stack frame, we can simply flush those
    // register values to the (usually unused) memory location that
    // corresponds to them.
    int firstEntry = currentStackHeight-dimensions;
    for (int i= firstEntry, d = dimensions; d > 0; d--, i++) {
      int l = getStackLocation(WORD_TYPE, i);
      if (isRegister(l)) {
        //-#if RVM_WITH_QUICK_COMPILER_MOVE_ELIMINATION
        moveValueToAnyRegister(registerDescription,
                                 slotContents[stackIndexToSlotIndex(i)],
                                 firstEntry);
        asm.emitSTW(registerDescription.register, stackBottomOffset-(i<<2), FP);
        //-#else
        asm.emitSTW(locationToRegister(l), stackBottomOffset-(i<<2), FP);
        //-#endif
      }
      currentStackHeight -= 1;
    }
    assignStackLocations();
    
    asm.emitLAddrToc(S0, VM_Entrypoints.newArrayArrayMethod.getOffset());
    asm.emitMTCTR(S0);
    asm.emitLVAL(T0, method.getId());
    asm.emitLVAL(T1, dimensions);
    asm.emitLVAL(T2, typeRef.getId());
    // offset of word *above* first array dimension arg
    asm.emitLVAL(T3,  stackBottomOffset-((firstEntry-1)<<2));
    asm.emitBCCTRL();
    pushFromRegister(OBJECT_TYPE, T0);
    }
  /**
   * Emit code to implement the arraylength bytecode
   */
  protected final void emit_arraylength() {
    if (unreachableBytecode) return;
    assignRegisters(1, ARRAY_TYPE);
    asm.emitLInt(sro0, VM_ObjectModel.getArrayLengthOffset(), sri0);
    cleanupRegisters();
  }

  /**
   * Emit code to implement the athrow bytecode
   */
  protected final void emit_athrow() {
    if (unreachableBytecode) return;
    asm.emitLAddrToc(S0, VM_Entrypoints.athrowMethod.getOffset());
    asm.emitMTCTR(S0);
    popToRegister(OBJECT_TYPE, T0);
    asm.emitBCCTRL();
  }

  /**
   * Emit code to implement the checkcast bytecode
   * @param typeRef the LHS type
   */
  protected final void emit_checkcast(VM_TypeReference typeRef) {
    if (unreachableBytecode) return;
    asm.emitLAddrToc(S0,  VM_Entrypoints.checkcastMethod.getOffset());
    asm.emitMTCTR(S0);
      // checkcast(obj, typeRef) consumes obj ...but if it
      // doesn't throw an exception then the obj remains on stack
      // afterwords for vm, so ensure object is both on top of stack
      // AND is in the proper register for the subroutine call.
    peekToRegister(OBJECT_TYPE, currentStackHeight-1, T0);
    asm.emitLVAL(T1, typeRef.getId());
    asm.emitBCCTRL();               // but obj remains on stack afterwords
    
  }

  /**
   * Emit code to implement the checkcast bytecode
   * @param type the LHS type
   */
  protected final void emit_checkcast_resolvedClass(VM_Type type) {
    if (unreachableBytecode) return;
    asm.emitLAddrToc(S0,  VM_Entrypoints.checkcastResolvedClassMethod.getOffset());
    asm.emitMTCTR(S0);
      // checkcast(obj, typeRef) consumes obj ...but if it
      // doesn't throw an exception then the obj remains on stack
      // afterwords for vm, so ensure object is both on top of stack
      // AND is in the proper register for the subroutine call.
    peekToRegister(OBJECT_TYPE, currentStackHeight-1, T0);
    asm.emitLVAL(T1, type.getId());
    asm.emitBCCTRL();               // but obj remains on stack
                                    // afterwords
  }

  /**
   * Emit code to implement the checkcast bytecode
   * @param type the LHS type
   */
  protected final void emit_checkcast_final(VM_Type type) {
    if (unreachableBytecode) return;
    asm.emitLAddrToc(S0,  VM_Entrypoints.checkcastFinalMethod.getOffset());
    asm.emitMTCTR(S0);
      // checkcast(obj, typeRef) consumes obj ...but if it
      // doesn't throw an exception then the obj remains on stack
      // afterwords for vm, so ensure object is both on top of stack
      // AND is in the proper register for the subroutine call.
    peekToRegister(OBJECT_TYPE, currentStackHeight-1, T0);
    asm.emitLVAL(T1, type.getTibOffset());
    asm.emitBCCTRL();               // but obj remains on stack
                                    // afterwords
  }

  /**
   * Emit code to implement the instanceof bytecode
   * @param typeRef the LHS type
   */
  protected final void emit_instanceof(VM_TypeReference typeRef) {
    if (unreachableBytecode) return;
    asm.emitLAddrToc(S0,  VM_Entrypoints.instanceOfMethod.getOffset());
    asm.emitMTCTR(S0);
    popToRegister(OBJECT_TYPE, T0);
    asm.emitLVAL(T1, typeRef.getId());
    asm.emitBCCTRL();
    pushFromRegister(OBJECT_TYPE, T0);
  }

  /**
   * Emit code to implement the instanceof bytecode
   * @param type the LHS type
   */
  protected final void emit_instanceof_resolvedClass(VM_Type type) {
    if (unreachableBytecode) return;
    asm.emitLAddrToc(S0,  VM_Entrypoints.instanceOfResolvedClassMethod.getOffset());
    asm.emitMTCTR(S0);
    popToRegister(OBJECT_TYPE, T0);
    asm.emitLVAL(T1, type.getId());
    asm.emitBCCTRL();
    pushFromRegister(OBJECT_TYPE, T0);
  }

  /**
   * Emit code to implement the instanceof bytecode
   * @param type the LHS type
   */
  protected final void emit_instanceof_final(VM_Type type) {
    if (unreachableBytecode) return;
    asm.emitLAddrToc(S0,  VM_Entrypoints.instanceOfFinalMethod.getOffset());
    asm.emitMTCTR(S0);
    popToRegister(OBJECT_TYPE, T0);
    asm.emitLVAL(T1, type.getTibOffset());
    asm.emitBCCTRL();
    pushFromRegister(OBJECT_TYPE, T0);
  }

  /**
   * Emit code to implement the monitorenter bytecode
   */
  protected final void emit_monitorenter() {
    if (unreachableBytecode) return;
    popToRegister(OBJECT_TYPE, T0); 
    asm.emitLAddr(S0, VM_Entrypoints.lockMethod.getOffset(), JTOC);
    asm.emitMTCTR(S0);
    asm.emitBCCTRL();
  }

  /**
   * Emit code to implement the monitorexit bytecode
   */
  protected final void emit_monitorexit() {
    if (unreachableBytecode) return;
    popToRegister(OBJECT_TYPE, T0); 
    asm.emitLAddr(S0, VM_Entrypoints.unlockMethod.getOffset(), JTOC);
    asm.emitMTCTR(S0);
    asm.emitBCCTRL();
  }

//   //-#if RVM_WITH_OSR
//   protected final void emit_loadaddrconst(int bcIndex)
//   //-#endif
  
  protected final int getEdgeCounterOffset() {
    return method.getId() << LOG_BYTES_IN_ADDRESS;
  }


  /**
   * Get description of virtual machine component (field or method).
   * Note: This is method is intended for use only by VM classes that need 
   * to address their own fields and methods in the runtime virtual machine 
   * image.  It should not be used for general purpose class loading.
   * @param classDescriptor  class  descriptor - something like "Lcom/ibm/JikesRVM/VM_Runtime;"
   * @param memberName       member name       - something like "invokestatic"
   * @param memberDescriptor member descriptor - something like "()V"
   * @return corresponding VM_Member object
   */
  private static VM_Member getMember(String classDescriptor, String memberName, 
                                     String memberDescriptor) {
    VM_Atom clsDescriptor = VM_Atom.findOrCreateAsciiAtom(classDescriptor);
    VM_Atom memName       = VM_Atom.findOrCreateAsciiAtom(memberName);
    VM_Atom memDescriptor = VM_Atom.findOrCreateAsciiAtom(memberDescriptor);
    try {
      VM_TypeReference tRef = VM_TypeReference.findOrCreate(VM_SystemClassLoader.getVMClassLoader(), clsDescriptor);
      VM_Class cls = (VM_Class)tRef.resolve();
      cls.resolve();

      VM_Member member;
      if ((member = cls.findDeclaredField(memName, memDescriptor)) != null)
        return member;
      if ((member = cls.findDeclaredMethod(memName, memDescriptor)) != null)
        return member;

      // The usual causes for getMember() to fail are:
      //  1. you mispelled the class name, member name, or member signature
      //  2. the class containing the specified member didn't get compiled
      //
      VM.sysWrite("VM_QuickCompiler.getMember: can't find class="+classDescriptor+" member="+memberName+" desc="+memberDescriptor+"\n");
      VM._assert(NOT_REACHED);
    } catch (Exception e) {
      e.printStackTrace();
      VM.sysWrite("VM_QuickCompiler.getMember: can't resolve class=" + classDescriptor+
                  " member=" + memberName + " desc=" + memberDescriptor + "\n");
      VM._assert(NOT_REACHED);
    }
    return null; // placate jikes
  }
  private static VM_Method getMethod(String klass, String member, String descriptor) {
    return (VM_Method)getMember(klass, member, descriptor);
  }

}

