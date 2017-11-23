/*
 *  This file is part of the Jikes RVM project (http://jikesrvm.org).
 *
 *  This file is licensed to You under the Eclipse Public License (EPL);
 *  You may not use this file except in compliance with the License. You
 *  may obtain a copy of the License at
 *
 *      http://www.opensource.org/licenses/eclipse-1.0.php
 *
 *  See the COPYRIGHT.txt file distributed with this work for information
 *  regarding copyright ownership.
 */
package org.jikesrvm.compilers.baseline;

import static org.jikesrvm.classloader.BytecodeConstants.JBC_caload;
import static org.jikesrvm.classloader.BytecodeConstants.JBC_getfield;
import static org.jikesrvm.classloader.BytecodeConstants.JBC_ifeq;
import static org.jikesrvm.classloader.BytecodeConstants.JBC_ifge;
import static org.jikesrvm.classloader.BytecodeConstants.JBC_ifgt;
import static org.jikesrvm.classloader.BytecodeConstants.JBC_ifle;
import static org.jikesrvm.classloader.BytecodeConstants.JBC_iflt;
import static org.jikesrvm.classloader.BytecodeConstants.JBC_ifne;
import static org.jikesrvm.runtime.ExitStatus.EXIT_STATUS_BOGUS_COMMAND_LINE_ARG;
import static org.jikesrvm.runtime.UnboxedSizeConstants.LOG_BYTES_IN_ADDRESS;

import org.jikesrvm.VM;
import org.jikesrvm.classloader.Atom;
import org.jikesrvm.classloader.FieldReference;
import org.jikesrvm.classloader.MethodReference;
import org.jikesrvm.classloader.NormalMethod;
import org.jikesrvm.compilers.common.CodeArray;
import org.jikesrvm.compilers.common.CompiledMethod;
import org.jikesrvm.compilers.common.CompiledMethods;
import org.jikesrvm.osr.BytecodeTraverser;
import org.jikesrvm.runtime.MagicNames;
import org.jikesrvm.runtime.Time;
import org.jikesrvm.scheduler.RVMThread;
import org.vmmagic.unboxed.Offset;

/**
 * Baseline compiler - platform independent code.
 * <p>
 * Platform dependent versions extend this class and define
 * the host of abstract methods defined by TemplateCompilerFramework to complete
 * the implementation of a baseline compiler for a particular target.
 * <p>
 * In addition to the framework provided by TemplateCompilerFramework, this compiler
 * also provides hooks for bytecode merging for some common bytecode combinations.
 * By default, bytecode merging is active but has no effect. Subclasses that want to
 * implement the merging need to override the hook methods.
 */
public abstract class BaselineCompiler extends TemplateCompilerFramework {

  /**
   * Merge commonly adjacent bytecodes?
   */
  private static final boolean mergeBytecodes = true;

  private static long gcMapNanos;
  private static long osrSetupNanos;
  private static long codeGenNanos;
  private static long encodingNanos;

  /**
   * Options used during base compiler execution
   */
  public static BaselineOptions options;

  /**
   * Next edge counter entry to allocate
   */
  protected int edgeCounterIdx;

  /**
   * Reference maps for method being compiled
   */
  ReferenceMaps refMaps;

  protected final Offset getEdgeCounterOffset() {
    return Offset.fromIntZeroExtend(method.getId() << LOG_BYTES_IN_ADDRESS);
  }

  protected final int getEdgeCounterIndex() {
    return method.getId();
  }

  /**
   * The types that locals can take.
   * There are two types of locals:
   * <ul>
   *  <li> the parameters of the method. They only have one type.</li>
   *  <li> the other locals. Numbers get reused when stack shrinks and grows
   *   again. Therefore, these can have more than one type assigned.
   * </ul>
   * The compiler can use this information to assign registers to locals.
   * See the BaselineCompilerImpl constructor.
   */
  protected final byte[] localTypes;

  protected BaselineCompiler(BaselineCompiledMethod cm) {
    super(cm);
    shouldPrint =
        (!VM.runningTool &&
         (options.PRINT_MACHINECODE) &&
         (!options.hasMETHOD_TO_PRINT() || options.fuzzyMatchMETHOD_TO_PRINT(method.toString())));
    if (!VM.runningTool && options.PRINT_METHOD) printMethodMessage();
    if (shouldPrint && VM.runningVM && !VM.fullyBooted) {
      shouldPrint = false;
      if (options.PRINT_METHOD) {
        VM.sysWriteln("\ttoo early in VM.boot() to print machine code");
      }
    }
    localTypes = new byte[method.getLocalWords()];
  }

  /**
   * Indicate if specified Magic method causes a frame to be created on the runtime stack.
   * @param methodToBeCalled RVMMethod of the magic method being called
   * @return true if method causes a stackframe to be created
   */
  public static boolean checkForActualCall(MethodReference methodToBeCalled) {
    Atom methodName = methodToBeCalled.getName();
    return methodName == MagicNames.invokeClassInitializer ||
      methodName == MagicNames.invokeMethodReturningVoid ||
      methodName == MagicNames.invokeMethodReturningInt ||
      methodName == MagicNames.invokeMethodReturningLong ||
      methodName == MagicNames.invokeMethodReturningFloat ||
      methodName == MagicNames.invokeMethodReturningDouble ||
      methodName == MagicNames.invokeMethodReturningObject ||
      methodName == MagicNames.addressArrayCreate;
  }

  /**
   * Clear out crud from bootimage writing
   */
  public static void initOptions() {
    options = new BaselineOptions();
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
    // compilation. Pick an obscure string for the check.
    if (options.hasMETHOD_TO_PRINT() && options.fuzzyMatchMETHOD_TO_PRINT("???")) {
      VM.sysWrite("??? is not a sensible string to specify for method name");
    }
  }

  /**
   * Process a command line argument
   * @param prefix the argument's prefix
   * @param arg     Command line argument with prefix stripped off
   */
  public static void processCommandLineArg(String prefix, String arg) {
    if (!options.processAsOption(prefix, arg)) {
      VM.sysWriteln("BaselineCompiler: Unrecognized argument \"" + arg + "\"");
      VM.sysExit(EXIT_STATUS_BOGUS_COMMAND_LINE_ARG);
    }
  }

  /**
   * Generate a report of time spent in various phases of the baseline compiler.
   * <p> NB: This method may be called in a context where class loading and/or
   * GC cannot be allowed. Therefore we must use primitive sysWrites for output and avoid string
   * appends and other allocations.
   * <p>
   * FIXME should this method be uninterruptible?
   *
   * @param explain Should an explanation of the metrics be generated?
   */
  public static void generateBaselineCompilerSubsystemReport(boolean explain) {
    if (!VM.MeasureCompilationPhases) return;

    VM.sysWriteln();
    VM.sysWriteln("\t\tBaseline Compiler SubSystem");
    VM.sysWriteln("\tPhase\t\t\t    Time");
    VM.sysWriteln("\t\t\t\t(ms)    (%ofTotal)");

    double gcMapTime = Time.nanosToMillis(gcMapNanos);
    double osrSetupTime = Time.nanosToMillis(osrSetupNanos);
    double codeGenTime = Time.nanosToMillis(codeGenNanos);
    double encodingTime = Time.nanosToMillis(encodingNanos);
    double total = gcMapTime + osrSetupTime + codeGenTime + encodingTime;

    VM.sysWrite("\tCompute GC Maps\t\t", gcMapTime);
    VM.sysWriteln("\t", 100 * gcMapTime / total);

    if (osrSetupTime > 0) {
      VM.sysWrite("\tOSR setup \t\t", osrSetupTime);
      VM.sysWriteln("\t", 100 * osrSetupTime / total);
    }

    VM.sysWrite("\tCode generation\t\t", codeGenTime);
    VM.sysWriteln("\t", 100 * codeGenTime / total);

    VM.sysWrite("\tEncode GC/MC maps\t", encodingTime);
    VM.sysWriteln("\t", 100 * encodingTime / total);

    VM.sysWriteln("\tTOTAL\t\t\t", total);
  }

  /**
   * Compile the given method with the baseline compiler.
   *
   * @param method the NormalMethod to compile.
   * @return the generated CompiledMethod for said NormalMethod.
   */
  public static CompiledMethod compile(NormalMethod method) {
    if (VM.VerifyAssertions) VM._assert(!method.getDeclaringClass().hasSaveVolatileAnnotation(), "Baseline compiler doesn't implement SaveVolatile");

    BaselineCompiledMethod cm =
        (BaselineCompiledMethod) CompiledMethods.createCompiledMethod(method, CompiledMethod.BASELINE);
    cm.compile();
    return cm;
  }

  protected abstract void initializeCompiler();

  /**
   * Top level driver for baseline compilation of a method.
   */
  protected void compile() {
    if (shouldPrint) printStartHeader(method);

    // Phase 1: GC map computation
    long start = 0;
    try {
      if (VM.MeasureCompilationPhases) {
        start = Time.nanoTime();
      }
      refMaps = new ReferenceMaps((BaselineCompiledMethod) compiledMethod, stackHeights, localTypes);
    } finally {
      if (VM.MeasureCompilationPhases) {
        long end = Time.nanoTime();
        gcMapNanos += end - start;
      }
    }

    /* reference map and stackheights were computed using original bytecodes
     * and possibly new operand words
     * recompute the stack height, but keep the operand words of the code
     * generation consistent with reference map
     * TODO: revisit this code as part of OSR redesign
     */
    // Phase 2: OSR setup\
    boolean edge_counters = options.PROFILE_EDGE_COUNTERS;
    try {
      if (VM.MeasureCompilationPhases) {
        start = Time.nanoTime();
      }
      if (VM.BuildForAdaptiveSystem && method.isForOsrSpecialization()) {
        options.PROFILE_EDGE_COUNTERS = false;
        // we already allocated enough space for stackHeights, shift it back first
        System.arraycopy(stackHeights,
                         0,
                         stackHeights,
                         method.getOsrPrologueLength(),
                         method.getBytecodeLength());   // NB: getBytecodeLength returns back the length of original bytecodes

        // compute stack height for prologue
        new BytecodeTraverser().prologueStackHeights(method, method.getOsrPrologue(), stackHeights);
      }
    } finally {
      if (VM.MeasureCompilationPhases) {
        long end = Time.nanoTime();
        osrSetupNanos += end - start;
      }
    }

    // Phase 3: Code generation
    int[] bcMap;
    MachineCode machineCode;
    CodeArray instructions;
    try {
      if (VM.MeasureCompilationPhases) {
        start = Time.nanoTime();
      }

      // determine if we are going to insert edge counters for this method
      if (options.PROFILE_EDGE_COUNTERS &&
          !method.getDeclaringClass().hasBridgeFromNativeAnnotation() &&
          (method.hasCondBranch() || method.hasSwitch())) {
        ((BaselineCompiledMethod) compiledMethod).setHasCounterArray(); // yes, we will inject counters for this method.
      }

      //do platform specific tasks before generating code;
      initializeCompiler();

      machineCode = genCode();
      instructions = machineCode.getInstructions();
      bcMap = machineCode.getBytecodeMap();
    } finally {
      if (VM.MeasureCompilationPhases) {
        long end = Time.nanoTime();
        codeGenNanos += end - start;
      }
    }

    /* adjust machine code map, and restore original bytecode
     * for building reference map later.
     * TODO: revisit this code as part of OSR redesign
     */
    // Phase 4: OSR part 2
    try {
      if (VM.MeasureCompilationPhases) {
        start = Time.nanoTime();
      }
      if (VM.BuildForAdaptiveSystem && method.isForOsrSpecialization()) {
        int[] newmap = new int[bcMap.length - method.getOsrPrologueLength()];
        System.arraycopy(bcMap, method.getOsrPrologueLength(), newmap, 0, newmap.length);
        machineCode.setBytecodeMap(newmap);
        bcMap = newmap;
        // switch back to original state
        method.finalizeOsrSpecialization();
        // restore options
        options.PROFILE_EDGE_COUNTERS = edge_counters;
      }
    } finally {
      if (VM.MeasureCompilationPhases) {
        long end = Time.nanoTime();
        osrSetupNanos += end - start;
      }
    }

    // Phase 5: Encode machine code maps
    try {
      if (VM.MeasureCompilationPhases) {
        start = Time.nanoTime();
      }
      if (method.isSynchronized()) {
        ((BaselineCompiledMethod) compiledMethod).setLockAcquisitionOffset(lockOffset);
      }
      ((BaselineCompiledMethod) compiledMethod).encodeMappingInfo(refMaps, bcMap);
      compiledMethod.compileComplete(instructions);
      if (edgeCounterIdx > 0) {
        EdgeCounts.allocateCounters(method, edgeCounterIdx);
      }
      if (shouldPrint) {
        ((BaselineCompiledMethod) compiledMethod).printExceptionTable();
        printEndHeader(method);
      }
    } finally {
      if (VM.MeasureCompilationPhases) {
        long end = Time.nanoTime();
        encodingNanos += end - start;
      }
    }
  }

  @Override
  protected String getCompilerName() {
    return "baseline";
  }

  /**
   * @return whether the current bytecode is on the boundary of a basic block
   */
  private boolean basicBlockBoundary() {
    int index = biStart;
    short currentBlock = refMaps.byteToBlockMap[index];
    index--;
    while (index >= 0) {
      short prevBlock = refMaps.byteToBlockMap[index];
      if (prevBlock == currentBlock) {
        return false;
      } else if (prevBlock != BasicBlock.NOTBLOCK) {
        return true;
      }
      index--;
    }
    return true;
  }

  /**
   * Emits code to load an int local variable
   * @param index the local index to load
   */
  @Override
  protected final void emit_iload(int index) {
    if (!mergeBytecodes || basicBlockBoundary()) {
      emit_regular_iload(index);
    } else {
      int nextBC = bcodes.peekNextOpcode();
      switch (nextBC) {
      case JBC_caload:
        if (shouldPrint) getLister().noteBytecode(biStart, "caload");
        bytecodeMap[bcodes.index()] = getAssembler().getMachineCodeIndex();
        bcodes.nextInstruction(); // skip opcode
        emit_iload_caload(index);
        break;
      default:
        emit_regular_iload(index);
        break;
      }
    }
  }

  /**
   * Emits code to load an int local variable
   * @param index the local index to load
   */
  protected abstract void emit_regular_iload(int index);

  /**
   * Emits code to load an int local variable and then load from a character array.
   * <p>
   * By default, this method emits code for iload and then for caload.
   * Subclasses that want to implement bytecode merging for this pattern
   * must override this method.
   *
   * @param index the local index to load
   */
  protected void emit_iload_caload(int index) {
    emit_regular_iload(index);
    emit_caload();
  }

  /**
   * Emits code to load a reference local variable
   * @param index the local index to load
   */
  @Override
  protected final void emit_aload(int index) {
    if (!mergeBytecodes || basicBlockBoundary()) {
      emit_regular_aload(index);
    } else {
      int nextBC = bcodes.peekNextOpcode();
      switch (nextBC) {
      case JBC_getfield: {
        int gfIndex = bcodes.index();
        bcodes.nextInstruction(); // skip opcode
        FieldReference fieldRef = bcodes.getFieldReference();
        if (fieldRef.needsDynamicLink(method)) {
          bcodes.reset(gfIndex);
          emit_regular_aload(index);
        } else {
          bytecodeMap[gfIndex] = getAssembler().getMachineCodeIndex();
          if (shouldPrint) getLister().noteBytecode(biStart, "getfield", fieldRef);
          emit_aload_resolved_getfield(index, fieldRef);
        }
        break;
      }
      default:
        emit_regular_aload(index);
        break;
      }
    }
  }

  /**
   * Emits code to load a reference local variable
   * @param index the local index to load
   */
  protected abstract void emit_regular_aload(int index);

  /**
   * Emits code to load a reference local variable and then perform a field load
   * <p>
   * By default, this method emits code for aload and then for resolved_getfield.
   * Subclasses that want to implement bytecode merging for this pattern
   * must override this method.
   *
   * @param index the local index to load
   * @param fieldRef the referenced field
   */
  protected void emit_aload_resolved_getfield(int index, FieldReference fieldRef) {
    emit_regular_aload(index);
    emit_resolved_getfield(fieldRef);
  }

  @Override
  protected final void emit_lcmp() {
    if (!mergeBytecodes || basicBlockBoundary()) {
      emit_regular_lcmp();
    } else {
      int nextBC = bcodes.peekNextOpcode();
      switch (nextBC) {
        case JBC_ifeq:
          do_lcmp_if(BranchCondition.EQ);
          break;
        case JBC_ifne:
          do_lcmp_if(BranchCondition.NE);
          break;
        case JBC_iflt:
          do_lcmp_if(BranchCondition.LT);
          break;
        case JBC_ifge:
          do_lcmp_if(BranchCondition.GE);
          break;
        case JBC_ifgt:
          do_lcmp_if(BranchCondition.GT);
          break;
        case JBC_ifle:
          do_lcmp_if(BranchCondition.LE);
          break;
        default:
          emit_regular_lcmp();
          break;
      }
    }
  }

  /**
   * Handles the bytecode pattern {@code lcmp; if..}
   * @param bc branch condition
   */
  private void do_lcmp_if(BranchCondition bc) {
    final boolean shouldPrint = this.shouldPrint;
    int biStart = bcodes.index();  // start of if bytecode
    bytecodeMap[biStart] = getAssembler().getMachineCodeIndex();
    bcodes.nextInstruction(); // skip opcode
    int offset = bcodes.getBranchOffset();
    int bTarget = biStart + offset;
    if (shouldPrint) getLister().noteBranchBytecode(biStart, "if" + bc, offset, bTarget);
    if (offset <= 0) emit_threadSwitchTest(RVMThread.BACKEDGE);
    emit_lcmp_if(bTarget, bc);
  }

  /**
   * Emits code to implement the lcmp bytecode
   */
  protected abstract void emit_regular_lcmp();

  /**
   * Emits code to perform an lcmp followed by ifeq.
   * <p>
   * By default, this method emits code for lcmp and then for ifeq.
   * Subclasses that want to implement bytecode merging for this pattern
   * must override this method.
   * @param bTarget target bytecode of the branch
   * @param bc branch condition
   */
  protected void emit_lcmp_if(int bTarget, BranchCondition bc) {
    emit_regular_lcmp();
    emit_if(bTarget, bc);
  }

  @Override
  protected final void emit_DFcmpGL(boolean single, boolean unorderedGT) {
    if (!mergeBytecodes || basicBlockBoundary()) {
      emit_regular_DFcmpGL(single, unorderedGT);
    } else {
      int nextBC = bcodes.peekNextOpcode();
      switch (nextBC) {
      case JBC_ifeq:
        do_DFcmpGL_if(single, unorderedGT, BranchCondition.EQ);
        break;
      case JBC_ifne:
        do_DFcmpGL_if(single, unorderedGT, BranchCondition.NE);
        break;
      case JBC_iflt:
        do_DFcmpGL_if(single, unorderedGT, BranchCondition.LT);
        break;
      case JBC_ifge:
        do_DFcmpGL_if(single, unorderedGT, BranchCondition.GE);
        break;
      case JBC_ifgt:
        do_DFcmpGL_if(single, unorderedGT, BranchCondition.GT);
        break;
      case JBC_ifle:
        do_DFcmpGL_if(single, unorderedGT, BranchCondition.LE);
        break;
      default:
        emit_regular_DFcmpGL(single, unorderedGT);
        break;
      }
    }
  }

  /**
   * Handles the bytecode pattern {@code DFcmpGL; if..}
   * @param single {@code true} for float [f], {@code false} for double [d]
   * @param unorderedGT {@code true} for [g], {@code false} for [l]
   * @param bc branch condition
   */
  private void do_DFcmpGL_if(boolean single, boolean unorderedGT, BranchCondition bc) {
    final boolean shouldPrint = this.shouldPrint;
    int biStart = bcodes.index();  // start of if bytecode
    bytecodeMap[biStart] = getAssembler().getMachineCodeIndex();
    bcodes.nextInstruction(); // skip opcode
    int offset = bcodes.getBranchOffset();
    int bTarget = biStart + offset;
    if (shouldPrint) getLister().noteBranchBytecode(biStart, "if" + bc, offset, bTarget);
    if (offset <= 0) emit_threadSwitchTest(RVMThread.BACKEDGE);
    emit_DFcmpGL_if(single, unorderedGT, bTarget, bc);
  }

  /**
   * Emits code to implement the [df]cmp[gl] bytecodes
   * @param single {@code true} for float [f], {@code false} for double [d]
   * @param unorderedGT {@code true} for [g], {@code false} for [l]
   */
  protected abstract void emit_regular_DFcmpGL(boolean single, boolean unorderedGT);

  /**
   * Emits code to perform an [df]cmp[gl] followed by ifeq
   * <p>
   * By default, this method emits code for [df]cmp[gl] and then for ifeq.
   * Subclasses that want to implement bytecode merging for this pattern
   * must override this method.

   * @param single {@code true} for float [f], {@code false} for double [d]
   * @param unorderedGT {@code true} for [g], {@code false} for [l]
   * @param bTarget target bytecode of the branch
   * @param bc branch condition
   */
  protected void emit_DFcmpGL_if(boolean single, boolean unorderedGT, int bTarget, BranchCondition bc) {
    emit_regular_DFcmpGL(single, unorderedGT);
    emit_if(bTarget, bc);
  }

}
