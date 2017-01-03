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
package org.jikesrvm.compilers.baseline.arm;

import static org.jikesrvm.runtime.JavaSizeConstants.BYTES_IN_DOUBLE;
import static org.jikesrvm.runtime.JavaSizeConstants.BYTES_IN_FLOAT;
import static org.jikesrvm.runtime.JavaSizeConstants.BYTES_IN_INT;
import static org.jikesrvm.runtime.JavaSizeConstants.BYTES_IN_LONG;
import static org.jikesrvm.runtime.JavaSizeConstants.LOG_BYTES_IN_CHAR;
import static org.jikesrvm.runtime.JavaSizeConstants.LOG_BYTES_IN_FLOAT;
import static org.jikesrvm.runtime.JavaSizeConstants.LOG_BYTES_IN_INT;
import static org.jikesrvm.runtime.JavaSizeConstants.LOG_BYTES_IN_LONG;
import static org.jikesrvm.runtime.UnboxedSizeConstants.BYTES_IN_ADDRESS;
import static org.jikesrvm.runtime.UnboxedSizeConstants.LOG_BYTES_IN_ADDRESS;
import static org.jikesrvm.compilers.baseline.BBConstants.ADDRESS_TYPE;
import static org.jikesrvm.compilers.baseline.BBConstants.DOUBLE_TYPE;
import static org.jikesrvm.compilers.baseline.BBConstants.FLOAT_TYPE;
import static org.jikesrvm.compilers.baseline.BBConstants.LONG_TYPE;
import static org.jikesrvm.compilers.baseline.BBConstants.VOID_TYPE;
import static org.jikesrvm.mm.mminterface.Barriers.NEEDS_ADDRESS_PUTFIELD_BARRIER;
import static org.jikesrvm.mm.mminterface.Barriers.NEEDS_BOOLEAN_PUTFIELD_BARRIER;
import static org.jikesrvm.mm.mminterface.Barriers.NEEDS_BYTE_ASTORE_BARRIER;
import static org.jikesrvm.mm.mminterface.Barriers.NEEDS_BYTE_PUTFIELD_BARRIER;
import static org.jikesrvm.mm.mminterface.Barriers.NEEDS_CHAR_ASTORE_BARRIER;
import static org.jikesrvm.mm.mminterface.Barriers.NEEDS_CHAR_PUTFIELD_BARRIER;
import static org.jikesrvm.mm.mminterface.Barriers.NEEDS_DOUBLE_ASTORE_BARRIER;
import static org.jikesrvm.mm.mminterface.Barriers.NEEDS_DOUBLE_PUTFIELD_BARRIER;
import static org.jikesrvm.mm.mminterface.Barriers.NEEDS_EXTENT_PUTFIELD_BARRIER;
import static org.jikesrvm.mm.mminterface.Barriers.NEEDS_FLOAT_ASTORE_BARRIER;
import static org.jikesrvm.mm.mminterface.Barriers.NEEDS_FLOAT_PUTFIELD_BARRIER;
import static org.jikesrvm.mm.mminterface.Barriers.NEEDS_INT_ASTORE_BARRIER;
import static org.jikesrvm.mm.mminterface.Barriers.NEEDS_INT_PUTFIELD_BARRIER;
import static org.jikesrvm.mm.mminterface.Barriers.NEEDS_LONG_ASTORE_BARRIER;
import static org.jikesrvm.mm.mminterface.Barriers.NEEDS_LONG_PUTFIELD_BARRIER;
import static org.jikesrvm.mm.mminterface.Barriers.NEEDS_OBJECT_ALOAD_BARRIER;
import static org.jikesrvm.mm.mminterface.Barriers.NEEDS_OBJECT_GETFIELD_BARRIER;
import static org.jikesrvm.mm.mminterface.Barriers.NEEDS_OBJECT_GETSTATIC_BARRIER;
import static org.jikesrvm.mm.mminterface.Barriers.NEEDS_OBJECT_PUTFIELD_BARRIER;
import static org.jikesrvm.mm.mminterface.Barriers.NEEDS_OBJECT_PUTSTATIC_BARRIER;
import static org.jikesrvm.mm.mminterface.Barriers.NEEDS_OFFSET_PUTFIELD_BARRIER;
import static org.jikesrvm.mm.mminterface.Barriers.NEEDS_SHORT_ASTORE_BARRIER;
import static org.jikesrvm.mm.mminterface.Barriers.NEEDS_SHORT_PUTFIELD_BARRIER;
import static org.jikesrvm.mm.mminterface.Barriers.NEEDS_WORD_PUTFIELD_BARRIER;
import static org.jikesrvm.compilers.common.assembler.arm.AssemblerConstants.COND.EQ;
import static org.jikesrvm.compilers.common.assembler.arm.AssemblerConstants.COND.GE;
import static org.jikesrvm.compilers.common.assembler.arm.AssemblerConstants.COND.GT;
import static org.jikesrvm.compilers.common.assembler.arm.AssemblerConstants.COND.LE;
import static org.jikesrvm.compilers.common.assembler.arm.AssemblerConstants.COND.LT;
import static org.jikesrvm.compilers.common.assembler.arm.AssemblerConstants.COND.NE;
import static org.jikesrvm.compilers.common.assembler.arm.AssemblerConstants.COND.HI;
import static org.jikesrvm.compilers.common.assembler.arm.AssemblerConstants.COND.HS;
import static org.jikesrvm.compilers.common.assembler.arm.AssemblerConstants.COND.LO;
import static org.jikesrvm.compilers.common.assembler.arm.AssemblerConstants.COND.LS;
import static org.jikesrvm.compilers.common.assembler.arm.AssemblerConstants.COND.ALWAYS;
import static org.jikesrvm.compilers.common.assembler.arm.AssemblerConstants.COND.NOCOND;
import static org.jikesrvm.compilers.common.assembler.arm.AssemblerConstants.COND.UNORDERED;
import static org.jikesrvm.arm.BaselineConstants.T0;
import static org.jikesrvm.arm.BaselineConstants.T1;
import static org.jikesrvm.arm.BaselineConstants.T2;
import static org.jikesrvm.arm.BaselineConstants.T3;
import static org.jikesrvm.arm.BaselineConstants.F0;
import static org.jikesrvm.arm.BaselineConstants.F1;
import static org.jikesrvm.arm.BaselineConstants.F2;
import static org.jikesrvm.arm.BaselineConstants.F4;
import static org.jikesrvm.arm.BaselineConstants.F6;
import static org.jikesrvm.arm.BaselineConstants.F0and1;
import static org.jikesrvm.arm.BaselineConstants.F2and3;
import static org.jikesrvm.arm.BaselineConstants.F4and5;
import static org.jikesrvm.arm.BaselineConstants.F6and7;
import static org.jikesrvm.arm.BaselineConstants.F0and1and2and3;
import static org.jikesrvm.arm.RegisterConstants.TR;
import static org.jikesrvm.arm.RegisterConstants.JTOC;
import static org.jikesrvm.arm.RegisterConstants.FP;
import static org.jikesrvm.arm.RegisterConstants.R12;
import static org.jikesrvm.arm.RegisterConstants.SP;
import static org.jikesrvm.arm.RegisterConstants.LR;
import static org.jikesrvm.arm.RegisterConstants.PC;
import static org.jikesrvm.arm.RegisterConstants.FIRST_VOLATILE_GPR;
import static org.jikesrvm.arm.RegisterConstants.LAST_VOLATILE_GPR;
import static org.jikesrvm.arm.RegisterConstants.FIRST_VOLATILE_FPR;
import static org.jikesrvm.arm.RegisterConstants.LAST_VOLATILE_FPR;
import static org.jikesrvm.arm.RegisterConstants.FIRST_VOLATILE_DPR;
import static org.jikesrvm.arm.RegisterConstants.LAST_VOLATILE_DPR;
import static org.jikesrvm.arm.RegisterConstants.FIRST_LOCAL_GPR;
import static org.jikesrvm.arm.RegisterConstants.LAST_LOCAL_GPR;
import static org.jikesrvm.arm.RegisterConstants.FIRST_LOCAL_FPR;
import static org.jikesrvm.arm.RegisterConstants.LAST_LOCAL_FPR;
import static org.jikesrvm.arm.RegisterConstants.FIRST_LOCAL_DPR;
import static org.jikesrvm.arm.RegisterConstants.LAST_LOCAL_DPR;
import static org.jikesrvm.arm.RegisterConstants.FIRST_OS_PARAMETER_GPR;
import static org.jikesrvm.arm.RegisterConstants.LAST_OS_PARAMETER_GPR;
import static org.jikesrvm.arm.RegisterConstants.FIRST_OS_PARAMETER_FPR;
import static org.jikesrvm.arm.RegisterConstants.LAST_OS_PARAMETER_FPR;
import static org.jikesrvm.arm.RegisterConstants.FIRST_OS_PARAMETER_DPR;
import static org.jikesrvm.arm.RegisterConstants.LAST_OS_PARAMETER_DPR;
import static org.jikesrvm.arm.RegisterConstants.INSTRUCTION_WIDTH;
import static org.jikesrvm.arm.StackframeLayoutConstants.STACKFRAME_SAVED_REGISTER_OFFSET;
import static org.jikesrvm.arm.StackframeLayoutConstants.STACKFRAME_PARAMETER_OFFSET;
import static org.jikesrvm.arm.StackframeLayoutConstants.STACKFRAME_METHOD_ID_OFFSET;
import static org.jikesrvm.arm.StackframeLayoutConstants.STACKFRAME_RETURN_ADDRESS_OFFSET;
import static org.jikesrvm.arm.StackframeLayoutConstants.STACKFRAME_FRAME_POINTER_OFFSET;
import static org.jikesrvm.objectmodel.TIBLayoutConstants.NEEDS_DYNAMIC_LINK;
import static org.jikesrvm.objectmodel.TIBLayoutConstants.TIB_DOES_IMPLEMENT_INDEX;
import static org.jikesrvm.objectmodel.TIBLayoutConstants.TIB_INTERFACE_DISPATCH_TABLE_INDEX;
import static org.jikesrvm.objectmodel.TIBLayoutConstants.TIB_SUPERCLASS_IDS_INDEX;
import static org.jikesrvm.objectmodel.TIBLayoutConstants.TIB_TYPE_INDEX;

import org.jikesrvm.VM;
import org.jikesrvm.classloader.DynamicTypeCheck;
import org.jikesrvm.classloader.RVMArray;
import org.jikesrvm.classloader.Atom;
import org.jikesrvm.classloader.RVMClass;
import org.jikesrvm.classloader.RVMField;
import org.jikesrvm.classloader.FieldReference;
import org.jikesrvm.classloader.InterfaceInvocation;
import org.jikesrvm.classloader.InterfaceMethodSignature;
import org.jikesrvm.classloader.MemberReference;
import org.jikesrvm.classloader.RVMMethod;
import org.jikesrvm.classloader.MethodReference;
import org.jikesrvm.classloader.RVMType;
import org.jikesrvm.classloader.TypeReference;
import org.jikesrvm.arm.RegisterConstants.GPR;
import org.jikesrvm.arm.RegisterConstants.FPR;
import org.jikesrvm.arm.RegisterConstants.DPR;
import org.jikesrvm.compilers.common.assembler.arm.AssemblerConstants.COND;
import org.jikesrvm.compilers.baseline.BaselineCompiledMethod;
import org.jikesrvm.compilers.baseline.BaselineCompiler;
import org.jikesrvm.compilers.baseline.EdgeCounts;
import org.jikesrvm.compilers.common.CompiledMethod;
import org.jikesrvm.compilers.common.assembler.ForwardReference;
import org.jikesrvm.compilers.common.assembler.AbstractAssembler;
import org.jikesrvm.compilers.common.assembler.arm.Assembler;
import org.jikesrvm.jni.arm.JNICompiler;
import org.jikesrvm.mm.mminterface.MemoryManager;
import org.jikesrvm.objectmodel.ObjectModel;
import org.jikesrvm.runtime.ArchEntrypoints;
import org.jikesrvm.runtime.Entrypoints;
import org.jikesrvm.runtime.MagicNames;
import org.jikesrvm.runtime.Statics;
import org.jikesrvm.scheduler.RVMThread;
import org.vmmagic.pragma.Inline;
import org.vmmagic.pragma.Pure;
import org.vmmagic.pragma.Uninterruptible;
import org.vmmagic.unboxed.Offset;

/**
 * Compiler is the baseline compiler class for ARM architectures.
 *
 * NAMING CONVENTIONS:
 * Methods of the form asm.emit...      produce individual machine instructions (defined in Assembler.java)
 * Methods of the form asm.generate... may produce several machine instructions (defined in Assembler.java)
 * Methods of the form emit_... refer to java bytecodes and implement the behaviour of that bytecode (defined in BaselineCompilerImpl.java)
 * Methods of the form gen... are helper methods with reusable code                                  (defined in BaselineCompilerImpl.java)
 * Methods of the form Barriers.compile... produce code to call read/write barriers (defined in Barriers.java)
 *
 *
 * CALLING CONVENTION:
 * As this class emits the code for a method it also defines the JikesRVM internal calling convention
 * Since opt and baseline methods may coexist, the opt compiler has to obey this calling convention as well
 * The JNI classes (and the genSysCall() method here) interact with external code and
 *   are responsible for implementing the operating system's calling convention
 *
 * The convention for (32-bit) ARM is based off of the standard ARM calling convention with the modification that
 * Long values are passed and returned in the Floating Point / Advanced SIMD registers
 *
 * 32-bit and smaller values are passed in the General Purpose Registers R0-3 (here referred to as T0-3)
 *   They are returned in T0
 * Float values are passed in the 32-bit view of the FP registers F0 etc.
 *   They are returned in F0
 * Double and Long values are passed in the 64-bit view of the FP registers F0and1 etc.
 *   They are returned in F0and1
 * Parameters are allocated to registers strictly in ascending order. If an odd-numbered float register is skipped
 *   because the parameter is a double, it is NOT reserved for a future float to fill (This differs from the ARM convention, because the ARM convention is a pain).
 * Any values that don't fit in the volatile registers are passed on the stack
 *
 *
 * REGISTER USE CONVENTION:
 * Register usage is mostly defined in the files RegisterConstants.java and BaselineConstants.java
 * The 32-bit ARM implementation follows the standard ARM register conventions.
 * R0-3 (here defined as T0-3) are volatile/parameter passing registers, not preserved across a function call
 * R4-8 are nonvolatile, used for local variables and saved by the called function
 * R9 is the Thread Register, R10 is the Java Table of Contents pointer (JTOC). These are never modified and do not need to be saved
 * R11 is the Frame Pointer (FP). It is saved by the called function
 * R12 is (per the ARM standard) the Intra-Procedure Call scratch register. It is also used by JikesRVM as a general scratch register
 *     Compilers should not assume it to be preserved by any code they did not personally generate
 *     Specifically, assume it is clobbered by any function call and any code emitted by the Assembler.generate... and BaselineCompilerImpl.gen... functions.
 *     However, Assembler.generate... functions must support using R12 as the output register without clobbering any others
 *     This is an important use case as some trampolines need to calculate an address to branch to while preserving all other registers
 *
 * ARM floating point/SIMD registers can be accessed as either 32-bit or 64-bit registers
 * The 32-bit F0 and F1 overlap with the 64-bit D0 which is defined here as F0and1 as a reminder of this relationship
 *
 *
 * FUNCTION PROLOGUE/EPILOGUE
 *
 * JikesRVM ARM function calls proceed as follows:
 *
 * 1. The caller writes parameters to registers, and pushes the ones that do not fit onto the stack in reverse order
      (according to the ARM convention, which is the reverse of the Java VM stack).
 * 2. The caller uses a BL or BLX to branch to the callee and save a return address in LR.
 * 3. The callee loads the compiledMethodId into R12.
 * 4. The callee pushes any of the registers R4-7 it intends to use, R11, R12, and LR onto the stack. This can be done in one instruction on ARM.
 * 5. The callee pushes any nonvolatile floating point registers it intends to use onto the stack. This can be done in one instruction on ARM.
 * 6. The callee saves the address of the saved R11 into R11. This is the frame pointer. The address of on-stack parameters,
                 saved registers, and on-stack locals relative to the FP can be calculated at compile time.
 * 7. The callee copies parameters (from registers or stack) into nonvolatile local variable registers.
 * 8. All parameters have now been allocated a spot either in registers or on the stack. Other local variables are allocated registers.
 * 9. Any local variables that do not fit in registers are allocated on the stack.
 * 10. The stack pointer is set to point to the last element of this local area.
 * .......
 * Computation proceeds, freely using R0-3, the volatile floating point registers, and the scratch register R12
 * .......
 * 1. The callee saves the return value in T0 or F0 or F0and1.
 * 2. The callee sets the SP to its value just after the registers were saved.
 * 3. The callee pops the nonvolatile floating point registers. This can be done in one instruction on ARM.
 * 4. The callee pops R4-7, R11, R12, LR. This can be done in one instruction on ARM.
 * 5. The callee pops its on-register parameters, thus restoring the stack to its state from just before step 1) above.
 * 6. The caller returns to LR. This can be merged with the previous step by popping the value directly into the PC.
 *
 * STACK FRAME
 * The ARM stack grows downwards from hi-mem to low-mem.
 * The ARM stack pointer points to the lowest 32-bit word currently on the stack
 *
 *
 *
 * BASELINE JAVA STACK AT THE START OF A CALL INSTRUCTION
 *
 *
 *                     hi-mem
 *            |                         |
 *            |                         |
 *            |                         |
 *            |                         |
 *            |                         |
 *            | Caller's operand stack  |
 *            |                         |
 *            |                         | <--- Caller's SP before parameters pushed
 *            +-------------------------+
 *            |                         | <--- First parameter
 *            |                         |
 *            |       Parameters        |
 *            |                         |
 *            |                         |
 *            |                         |
 *            |                         |
 *            |                         |
 *            |                         | <-- Last parameter, caller's SP
 *            +-------------------------+
 *                     low-mem
 *
 *
 *
 * STACK IMMEDIATELY BEFORE CONTROL PASSES TO CALLEE
 *
 *
 *                     hi-mem
 *            |                         |
 *            |                         |
 *            |                         |
 *            |                         |
 *            |                         |
 *            | Caller's operand stack  |
 *            |                         |
 *            |                         | <--- Caller's SP before parameters pushed
 *            +-------------------------+
 *            |                         | <--- Last parameter
 *            |                         |
 *            |       Parameters        |
 *            |                         |
 *            |                         | <--- First non-register parameter, caller's SP
 *            +-------------------------+
 *                     low-mem                 (earlier parameters moved to registers)
 *
 *
 *
 * BASELINE STACK INSIDE CALLEE
 *
 *
 *                     hi-mem
 *            |                         |
 *            |                         |
 *            |                         |
 *            |                         |
 *            |                         |
 *            | Caller's operand stack  |
 *            |                         |
 *            |                         | <--- Caller's SP before parameters pushed
 *            +-------------------------+
 *            |                         | <--- Last parameter
 *            |                         |
 *            |       Parameters        |
 *            |                         |
 *            |                         | <--- First non-register parameter, caller's SP, callee's FP + 12 (STACKFRAME_PARAMETER_OFFSET = 12)
 *            +-------------------------+
 *            |     Saved LR            | <--- Callee's FP + 8     (STACKFRAME_RETURN_ADDRESS_OFFSET = 8)
 *            +-------------------------+
 *            |   Compiled method ID    | <--- Callee's FP + 4     (STACKFRAME_METHOD_ID_OFFSET = 4)
 *            +-------------------------+
 *            |     Saved R11 (FP)      | <--- Callee's FP         (STACKFRAME_FRAME_POINTER_OFFSET = 0)
 *            +-------------------------+
 *            |                         | <--- Highest saved core register (e.g. R8)   (STACKFRAME_SAVED_REGISTER_OFFSET = 0, meaning the top of the slot)
 *            |                         |
 *            |     Saved Registers     |
 *            |                         |
 *            |                         | <--- Lowest saved core register (R4)
 *            +-------------------------+
 *            |                         | <--- Highest saved floating point register (high word) (e.g. D9)
 *            |     Saved Registers     |
 *            |                         | <--- Lowest saved floating point register (high word)
 *            |                         | <--- Lowest saved floating point register (low word) (D8=S16), callee's FP - (number of saved register bytes)
 *            +-------------------------+
 *            |                         | <--- First non-register (and non-parameter) local, callee's FP - (number of saved register bytes) - (size of local)
 *            |                         | <--- Address (and low word) of first non-register local if it is a double
 *            |                         |
 *            |        Locals           |
 *            |                         |
 *            |                         |
 *            |                         |
 *            |                         |
 *            |                         |
 *            |                         |
 *            |                         | <--- Last local, FP + ArchBaselineCompiledMethod.getEmptyStackOffset()
 *            +-------------------------+
 *            |                         | <--- First 32-bit value on operand stack, callee's FP - (number of saved register bytes) - (number of locals bytes) - 4
 *            |                         |
 *            |                         |
 *            |                         |
 *            | Callee's operand stack  |
 *            |                         |
 *            |                         |
 *            |                         |
 *            |                         |
 *            |                         | <--- Callee's SP
 *            +-------------------------+
 *                     low-mem
 *
 *
 * STACK AFTER CALLEE RETURNS
 *
 *
 *                     hi-mem
 *            |                         |
 *            |                         |
 *            |                         |
 *            |                         |
 *            |                         |
 *            | Caller's operand stack  |
 *            |                         |
 *            |                         | <--- Caller's SP
 *            +-------------------------+
 *                     low-mem
 *
 */
public final class BaselineCompilerImpl extends BaselineCompiler {

  final Assembler asm;

  private static final boolean USE_NONVOLATILE_REGISTERS = true;

  // Maps from local variable indices to registers or stack locations
  private final short[] localGeneralLocations; // We need both because a double and an int could share a java "local" index, but be allocated to different registers
  private final short[] localFloatLocations;   // e.g. local number 6 can't fit in a core register but will fit into an FPR.

  private int SAVED_REGISTER_BYTES; // Set after all variables are assigned and we know which registers are being used
  private int LOCALS_BYTES = 0;     // Incremented when local variables are assigned

  static final short MAX_REGISTER_LOC = 100; // Locations < 0 refer to the locals, >= 100 refer to the parameter area, in-between refer to registers
  // Thus:
  // The first non-register parameter is located at FP + STACKFRAME_PARAMETER_OFFSET and has location "100", so can be found at "FP + STACKFRAME_PARAMETER_OFFSET + (loc - MAX_REGISTER_LOC)"
  // The first register parameter is located in R0 and has location "0", so can be found at the register loc
  // The first local is located at FP + STACKFRAME_SAVED_REGISTER_OFFSET - SAVED_REGISTER_BYTES - 4 or -8 and has location "-4" or "-8", so can be found at "FP + STACKFRAME_SAVED_REGISTER_OFFSET - SAVED_REGISTER_BYTES + loc"

  /** Can non-volatile registers be used? */
  private final boolean use_nonvolatile_registers;
  private final boolean SAVE_ALL_REGS;

  private boolean isInitialised = false;

  /**
   * Create a Compiler object for the compilation of method.
   */
  public BaselineCompilerImpl(BaselineCompiledMethod cm) {
    super(cm);
    localGeneralLocations = new short[method.getLocalWords()];
    localFloatLocations   = new short[method.getLocalWords()];

    use_nonvolatile_registers = USE_NONVOLATILE_REGISTERS && !method.hasBaselineNoRegistersAnnotation();
    SAVE_ALL_REGS = use_nonvolatile_registers && method.hasBaselineSaveLSRegistersAnnotation();

    if (VM.VerifyAssertions) VM._assert(T3.value() <= LAST_VOLATILE_GPR.value());
    if (VM.VerifyAssertions) VM._assert(F6.value() <= LAST_VOLATILE_FPR.value());
    if (VM.VerifyAssertions) VM._assert(F6and7.value() + 1 <= LAST_VOLATILE_FPR.value());
    if (VM.VerifyAssertions) VM._assert(F0and1and2and3.value() + 3 <= LAST_VOLATILE_FPR.value());
    // Unfortunately I don't remember why these two conditions were necessary - there's a good chance they aren't anymore:
    if (VM.VerifyAssertions) VM._assert((LAST_LOCAL_FPR.value() - FIRST_LOCAL_FPR.value()) >= (LAST_VOLATILE_FPR.value() - FIRST_VOLATILE_FPR.value()));
    if (VM.VerifyAssertions) VM._assert((LAST_LOCAL_GPR.value() - FIRST_LOCAL_GPR.value()) >= (LAST_VOLATILE_GPR.value() - FIRST_VOLATILE_GPR.value()));

    asm = new Assembler(bcodes.length(), shouldPrint, this);
  }

  @Override
  protected AbstractAssembler getAssembler() {
    return asm;
  }

  @Override
  protected void initializeCompiler() {
    defineLocalLocations(); // Can only be performed after localTypes are filled in by buildReferenceMaps
    isInitialised = true;
  }

  private short nextGPR = FIRST_LOCAL_GPR.value();
  private short nextFPR = FIRST_LOCAL_FPR.value();

  private short paramStackBytes = 0;

  private short assignParamStackPos(int size) {
    if (VM.VerifyAssertions) VM._assert(paramStackBytes <= Short.MAX_VALUE - size);
    short pos = (short)(MAX_REGISTER_LOC + paramStackBytes);
    paramStackBytes += size;
    return pos;
  }

  private short assignLocalPos(int size) {
    if (VM.VerifyAssertions) VM._assert(LOCALS_BYTES <= Short.MAX_VALUE - size);
    LOCALS_BYTES += size;
    return (short)(-LOCALS_BYTES);
  }

  // Rules:
  //  Parameters on the stack stay on the stack
  //  Parameters in registers (r0-r3) get moved to (r4-r7); likewise for floats
  //  Other locals get allocated to remaining registers, then to the stack
  //  If !use_nonvolatile_registers, then all register parameters go to the locals area too

  // The main difficulty here is that the same localIndex can refer to multiple types throughout a function's lifetime
  // However, parameters' type is fixed, so it is safe to allocate a 32-bit chunk of a float register for a float parameter


  private short nextParamSourceGPR = FIRST_VOLATILE_GPR.value();
  private short nextParamSourceFPR = FIRST_VOLATILE_FPR.value();

  @Inline
  private void assignGeneralParameter(final int localIndex) {
    if (nextParamSourceGPR > LAST_VOLATILE_GPR.value()) {
      localGeneralLocations[localIndex] = assignParamStackPos(BYTES_IN_ADDRESS);
    } else {
      nextParamSourceGPR++;
      if (use_nonvolatile_registers) {
        localGeneralLocations[localIndex] = nextGPR++;
      } else {
        localGeneralLocations[localIndex] = assignLocalPos(BYTES_IN_ADDRESS);
      }
    }
  }

  @Inline
  private void assignFloatParameter(final int localIndex) {
    if (nextParamSourceFPR > LAST_VOLATILE_FPR.value()) {
      localFloatLocations[localIndex] = assignParamStackPos(BYTES_IN_FLOAT);
    } else {
      nextParamSourceFPR++;
      if (use_nonvolatile_registers) {
        localFloatLocations[localIndex] = nextFPR++;
      } else {
        localFloatLocations[localIndex] = assignLocalPos(BYTES_IN_FLOAT);
      }
    }
  }

  @Inline
  private void assignDoubleParameter(final int localIndex) {
    if ((nextParamSourceFPR & 1) == 1) nextParamSourceFPR++;  // 64-bit align
    if (nextParamSourceFPR > LAST_VOLATILE_FPR.value()) {
      localFloatLocations[localIndex] = assignParamStackPos(BYTES_IN_DOUBLE);
    } else {
      nextParamSourceFPR += 2;
      if (use_nonvolatile_registers) {
        if ((nextFPR & 1) == 1) nextFPR++; // 64-bit align
        localFloatLocations[localIndex] = nextFPR;
        nextFPR += 2;
      } else {
        localFloatLocations[localIndex] = assignLocalPos(BYTES_IN_DOUBLE);
      }
    }
  }

  @Inline
  private void assignLocal(final int localIndex, final boolean canBeGeneral, final boolean canBeFloat, final boolean canBeDouble) {
    if ((nextFPR & 1) == 1) nextFPR++; // 64-bit align

    // Parameters have a fixed type for life, so it is safe to allocate only 32-bits
    final boolean hasFPR = (canBeFloat || canBeDouble) && use_nonvolatile_registers && (nextFPR + 1 <= LAST_LOCAL_FPR.value());
    final boolean hasGPR = canBeGeneral && use_nonvolatile_registers && (nextGPR <= LAST_LOCAL_GPR.value());

    final boolean needsGeneralStackSlot = canBeGeneral && !hasGPR;
    final boolean needsFloatStackSlot = (canBeFloat || canBeDouble) && !hasFPR;

    final boolean hasStackSlot = needsGeneralStackSlot || needsFloatStackSlot;
    final int stackSize = (canBeDouble ? BYTES_IN_DOUBLE : BYTES_IN_ADDRESS);

    if (hasFPR) {
      localFloatLocations[localIndex] = nextFPR;
      nextFPR += 2;
    }

    if (hasGPR) {
      localGeneralLocations[localIndex] = nextGPR++;
    }

    if (hasStackSlot) {
      final short stackPos = assignLocalPos(stackSize);

      if (needsGeneralStackSlot)
        localGeneralLocations[localIndex] = stackPos;

      if (needsFloatStackSlot)
        localFloatLocations[localIndex] = stackPos;
    }
  }

  private void defineLocalLocations() {
    int nparam = method.getParameterWords(); // For sanity check
    TypeReference[] types = method.getParameterTypes();
    int localIndex = 0;

    if (!method.isStatic()) { // "this" pointer
      if (VM.VerifyAssertions) VM._assert(localTypes[0] == ADDRESS_TYPE);
      assignGeneralParameter(localIndex);
      localIndex++;
      nparam++;
    }

    for (TypeReference t : types) {
      if (t.isLongType() || t.isDoubleType()) {
        assignDoubleParameter(localIndex);
        localIndex += 2;
      } else if (t.isFloatType()) {
        assignFloatParameter(localIndex);
        localIndex++;
      } else { // t is int-like or object
        assignGeneralParameter(localIndex);
        localIndex++;
      }
    }

    if (VM.VerifyAssertions) VM._assert(localIndex == nparam);

    //rest of locals, non parameters, could be reused for different types
    int nLocalWords = method.getLocalWords();
    for (; localIndex < nLocalWords; localIndex++) {
      final byte currentLocal = localTypes[localIndex];
      final boolean CAN_BE_DOUBLE  = (currentLocal & (DOUBLE_TYPE | LONG_TYPE)) != 0;
      final boolean CAN_BE_FLOAT   = (currentLocal & FLOAT_TYPE) != 0;
      final boolean CAN_BE_GENERAL = (currentLocal & (~DOUBLE_TYPE) & (~LONG_TYPE) & (~FLOAT_TYPE)) != VOID_TYPE;
      if (currentLocal != VOID_TYPE) // VOID_TYPE refers to blank local slots - typically the high word of a long or double
        assignLocal(localIndex, CAN_BE_GENERAL, CAN_BE_FLOAT, CAN_BE_DOUBLE);
    }

    if ((nextFPR & 1) == 1) nextFPR++;  // 64-bit align
    if (klass.hasDynamicBridgeAnnotation()) // Saves all registers, even non-volatiles
        SAVED_REGISTER_BYTES = ((1 + LAST_LOCAL_GPR.value() - FIRST_LOCAL_GPR.value() + 1 + LAST_VOLATILE_GPR.value() - FIRST_VOLATILE_GPR.value()) << LOG_BYTES_IN_ADDRESS) +
                               ((1 + LAST_LOCAL_FPR.value() - FIRST_LOCAL_FPR.value() + 1 + LAST_VOLATILE_FPR.value() - FIRST_VOLATILE_FPR.value()) << LOG_BYTES_IN_FLOAT);
    else if (SAVE_ALL_REGS)
        SAVED_REGISTER_BYTES = ((LAST_LOCAL_GPR.value() - FIRST_LOCAL_GPR.value() + 1) << LOG_BYTES_IN_ADDRESS) + ((LAST_LOCAL_FPR.value() - FIRST_LOCAL_FPR.value() + 1) << LOG_BYTES_IN_FLOAT);
    else
        SAVED_REGISTER_BYTES = ((nextGPR - FIRST_LOCAL_GPR.value()) << LOG_BYTES_IN_ADDRESS) + ((nextFPR - FIRST_LOCAL_FPR.value()) << LOG_BYTES_IN_FLOAT);
  }

  @Uninterruptible
  static boolean isRegister(short location) {
    return location >= 0 && location < MAX_REGISTER_LOC;
  }

  @Uninterruptible
  short getNextGPR() {
    if (VM.VerifyAssertions) VM._assert(isInitialised);
    return (short)(SAVE_ALL_REGS ? LAST_LOCAL_GPR.value() + 1 : nextGPR);
  }

  @Uninterruptible
  short getNextFPR() {
    if (VM.VerifyAssertions) VM._assert(isInitialised);
    return (short)(SAVE_ALL_REGS ? LAST_LOCAL_FPR.value() + 1 : nextFPR);
  }

  @Uninterruptible
  int getSavedRegisterBytes() {
    if (VM.VerifyAssertions) VM._assert(isInitialised);
    return SAVED_REGISTER_BYTES;
  }

  @Uninterruptible
  int getLocalsBytes() {
    if (VM.VerifyAssertions) VM._assert(isInitialised);
    return LOCALS_BYTES;
  }

  @Uninterruptible
  short [] getlocalGeneralLocations() {
    if (VM.VerifyAssertions) VM._assert(isInitialised);
    return localGeneralLocations;
  }

  /**
   *
   * Loads a local variable into the specified register
   */
  @Inline
  private void genLoadGeneralLocal(GPR reg, short src) {
    if (VM.VerifyAssertions) VM._assert(!isRegister(src));
    if (src >= MAX_REGISTER_LOC)
      asm.emitLDRimm(ALWAYS, reg, FP, STACKFRAME_PARAMETER_OFFSET.toInt() + src - MAX_REGISTER_LOC);
    else
      asm.emitLDRimm(ALWAYS, reg, FP, STACKFRAME_SAVED_REGISTER_OFFSET.toInt() - SAVED_REGISTER_BYTES + src);
  }

  @Inline
  private void genLoadFloatLocal(FPR reg, short src) {
    if (VM.VerifyAssertions) VM._assert(!isRegister(src));
    if (src >= MAX_REGISTER_LOC)
      asm.emitVLDR32(ALWAYS, reg, FP, STACKFRAME_PARAMETER_OFFSET.toInt() + src - MAX_REGISTER_LOC);
    else
      asm.emitVLDR32(ALWAYS, reg, FP, STACKFRAME_SAVED_REGISTER_OFFSET.toInt() - SAVED_REGISTER_BYTES + src);
  }

  @Inline
  private void genLoadDoubleLocal(DPR reg, short src) {
    if (VM.VerifyAssertions) VM._assert(!isRegister(src));
    if (src >= MAX_REGISTER_LOC)
      asm.emitVLDR64(ALWAYS, reg, FP, STACKFRAME_PARAMETER_OFFSET.toInt() + src - MAX_REGISTER_LOC);
    else
      asm.emitVLDR64(ALWAYS, reg, FP, STACKFRAME_SAVED_REGISTER_OFFSET.toInt() - SAVED_REGISTER_BYTES + src);
  }


  @Inline
  private void genStoreGeneralLocal(short dest, GPR reg) {
    if (VM.VerifyAssertions) VM._assert(!isRegister(dest));
    if (dest >= MAX_REGISTER_LOC)
      asm.emitSTRimm(ALWAYS, reg, FP, STACKFRAME_PARAMETER_OFFSET.toInt() + dest - MAX_REGISTER_LOC);
    else
      asm.emitSTRimm(ALWAYS, reg, FP, STACKFRAME_SAVED_REGISTER_OFFSET.toInt() - SAVED_REGISTER_BYTES + dest);
  }

  @Inline
  private void genStoreFloatLocal(short dest, FPR reg) {
    if (VM.VerifyAssertions) VM._assert(!isRegister(dest));
    if (dest >= MAX_REGISTER_LOC)
      asm.emitVSTR32(ALWAYS, reg, FP, STACKFRAME_PARAMETER_OFFSET.toInt() + dest - MAX_REGISTER_LOC);
    else
      asm.emitVSTR32(ALWAYS, reg, FP, STACKFRAME_SAVED_REGISTER_OFFSET.toInt() - SAVED_REGISTER_BYTES + dest);
  }

  @Inline
  private void genStoreDoubleLocal(short dest, DPR reg) {
    if (VM.VerifyAssertions) VM._assert(!isRegister(dest));
    if (dest >= MAX_REGISTER_LOC)
      asm.emitVSTR64(ALWAYS, reg, FP, STACKFRAME_PARAMETER_OFFSET.toInt() + dest - MAX_REGISTER_LOC);
    else
      asm.emitVSTR64(ALWAYS, reg, FP, STACKFRAME_SAVED_REGISTER_OFFSET.toInt() - SAVED_REGISTER_BYTES + dest);
  }

  /*
   * implementation of abstract methods of BaselineCompiler
   */

  /**
   * Nothing to do on ARM
   */
  @Override
  protected void starting_bytecode() {}

  @Override
  protected void emit_prologue() {
    genPrologue();
  }

  @Override
  protected void emit_threadSwitchTest(int whereFrom) {
    genThreadSwitchTest(whereFrom);
  }

  @Override
  protected boolean emit_Magic(MethodReference magicMethod) {
    return genMagic(magicMethod);
  }

  /*
   * Loading constants
   */

  @Override
  protected void emit_aconst_null() {
    asm.emitMOVimm(ALWAYS, T0, 0);
    asm.emitPUSH(ALWAYS, T0);
  }

  @Override // Must be able to handle constants up to 16 bits
  protected void emit_iconst(int val) {
    asm.generateImmediateLoad(ALWAYS, T0, val);
    asm.emitPUSH(ALWAYS, T0);
  }

  @Override
  protected void emit_lconst(int val) {
    asm.emitMOVimm(ALWAYS, T0, 0);      // high part
    asm.emitMOVimm(ALWAYS, T1, val);    // low part (either 1 or 0)
    asm.emitPUSH(ALWAYS, T0);
    asm.emitPUSH(ALWAYS, T1);
  }

  @Override
  protected void emit_fconst_0() {
    asm.emitMOVimm(ALWAYS, T0, 0);
    asm.emitPUSH(ALWAYS, T0);
  }

  @Override
  protected void emit_fconst_1() {
    asm.emitVMOV32imm(ALWAYS, F0, 1.0F);
    asm.emitVPUSH32(ALWAYS, F0);
  }

  @Override
  protected void emit_fconst_2() {
    asm.emitVMOV32imm(ALWAYS, F0, 2.0F);
    asm.emitVPUSH32(ALWAYS, F0);
  }

  @Override
  protected void emit_dconst_0() {
    asm.emitMOVimm(ALWAYS, T0, 0);
    asm.emitPUSH(ALWAYS, T0);
    asm.emitPUSH(ALWAYS, T0);
  }

  @Override
  protected void emit_dconst_1() {
    asm.emitVMOV64imm(ALWAYS, F0and1, 1.0);
    asm.emitVPUSH64(ALWAYS, F0and1);
  }

  @Override
  protected void emit_ldc(Offset offset, byte type) {
    asm.generateOffsetLoad(ALWAYS, T0, JTOC, offset);
    asm.emitPUSH(ALWAYS, T0);
  }

  @Override
  protected void emit_ldc2(Offset offset, byte type) {
    asm.generateOffsetLoad64(ALWAYS, F0and1, JTOC, offset);
    asm.emitVPUSH64(ALWAYS, F0and1);
  }

  /*
   * loading local variables
   */

  @Override
  protected void emit_regular_iload(int index) {
    short loc = localGeneralLocations[index];
    if (isRegister(loc)) {
      asm.emitPUSH(ALWAYS, GPR.lookup(loc));
    } else {
      genLoadGeneralLocal(T0, loc);
      asm.emitPUSH(ALWAYS, T0);
    }
  }

  @Override
  protected void emit_lload(int index) {
    short loc = localFloatLocations[index];
    if (isRegister(loc)) {
      asm.emitVPUSH64(ALWAYS, DPR.lookup(loc));
    } else {
      genLoadDoubleLocal(F0and1, loc);
      asm.emitVPUSH64(ALWAYS, F0and1);
    }
  }

  @Override
  protected void emit_fload(int index) {
    short loc = localFloatLocations[index];
    if (isRegister(loc)) {
      asm.emitVPUSH32(ALWAYS, FPR.lookup(loc));
    } else {
      genLoadFloatLocal(F0, loc);
      asm.emitVPUSH32(ALWAYS, F0);
    }
  }

  @Override
  protected void emit_dload(int index) {
    short loc = localFloatLocations[index];
    if (isRegister(loc)) {
      asm.emitVPUSH64(ALWAYS, DPR.lookup(loc));
    } else {
      genLoadDoubleLocal(F0and1, loc);
      asm.emitVPUSH64(ALWAYS, F0and1);
    }
  }

  @Override
  protected void emit_regular_aload(int index) {
    short loc = localGeneralLocations[index];
    if (isRegister(loc)) {
      asm.emitPUSH(ALWAYS, GPR.lookup(loc));
    } else {
      genLoadGeneralLocal(T0, loc);
      asm.emitPUSH(ALWAYS, T0);
    }
  }

  /*
   * storing local variables
   */

  @Override
  protected void emit_istore(int index) {
    short loc = localGeneralLocations[index];
    if (isRegister(loc)) {
      asm.emitPOP(ALWAYS, GPR.lookup(loc));
    } else {
      asm.emitPOP(ALWAYS, T0);
      genStoreGeneralLocal(loc, T0);
    }
  }

  @Override
  protected void emit_lstore(int index) {
    short loc = localFloatLocations[index];
    if (isRegister(loc)) {
      asm.emitVPOP64(ALWAYS, DPR.lookup(loc));
    } else {
      asm.emitVPOP64(ALWAYS, F0and1);
      genStoreDoubleLocal(loc, F0and1);
    }
  }

  @Override
  protected void emit_fstore(int index) {
    short loc = localFloatLocations[index];
    if (isRegister(loc)) {
      asm.emitVPOP32(ALWAYS, FPR.lookup(loc));
    } else {
      asm.emitVPOP32(ALWAYS, F0);
      genStoreFloatLocal(loc, F0);
    }
  }

  @Override
  protected void emit_dstore(int index) {
    short loc = localFloatLocations[index];
    if (isRegister(loc)) {
      asm.emitVPOP64(ALWAYS, DPR.lookup(loc));
    } else {
      asm.emitVPOP64(ALWAYS, F0and1);
      genStoreDoubleLocal(loc, F0and1);
    }
  }

  @Override
  protected void emit_astore(int index) {
    short loc = localGeneralLocations[index];
    if (isRegister(loc)) {
      asm.emitPOP(ALWAYS, GPR.lookup(loc));
    } else {
      asm.emitPOP(ALWAYS, T0);
      genStoreGeneralLocal(loc, T0);
    }
  }

  /*
   * array loads
   */

  @Override
  protected void emit_iaload() {
    asm.emitPOP(ALWAYS, T1); // T1 is array index
    asm.emitPOP(ALWAYS, T0); // T0 is array ref, T2 is array length
    asm.emitLDRimm(ALWAYS, T2, T0, ObjectModel.getArrayLengthOffset().toInt());  // Implicit null check
    asm.generateBoundsCheck(T1, T2);
    asm.emitLDRshift(ALWAYS, T2, T0, T1, LOG_BYTES_IN_INT);
    asm.emitPUSH(ALWAYS, T2);
  }

  @Override
  protected void emit_laload() {
    asm.emitPOP(ALWAYS, T1); // T1 is array index
    asm.emitPOP(ALWAYS, T0); // T0 is array ref, T2 is array length
    asm.emitLDRimm(ALWAYS, T2, T0, ObjectModel.getArrayLengthOffset().toInt());  // Implicit null check
    asm.generateBoundsCheck(T1, T2);
    asm.emitADDshift(ALWAYS, T0, T0, T1, LOG_BYTES_IN_LONG); // T0 is now address
    asm.emitVLDR64(ALWAYS, F0and1, T0, 0);
    asm.emitVPUSH64(ALWAYS, F0and1);
  }

  @Override
  protected void emit_faload() {
    emit_iaload();
  }

  @Override
  protected void emit_daload() {
    emit_laload();
  }

  @Override
  protected void emit_aaload() {
    asm.emitPOP(ALWAYS, T1); // T1 is array index
    asm.emitPOP(ALWAYS, T0); // T0 is array ref, T2 is array length
    asm.emitLDRimm(ALWAYS, T2, T0, ObjectModel.getArrayLengthOffset().toInt());  // Implicit null check
    asm.generateBoundsCheck(T1, T2);

    if (NEEDS_OBJECT_ALOAD_BARRIER) {
      Barriers.compileArrayLoadBarrier(this); // Assumes T0 = array ref, T1 = index; On return T0 = value
      asm.emitPUSH(ALWAYS, T0);
    } else {
      asm.emitLDRshift(ALWAYS, T2, T0, T1, LOG_BYTES_IN_ADDRESS);
      asm.emitPUSH(ALWAYS, T2);
    }
  }

  @Override
  protected void emit_baload() {
    asm.emitPOP(ALWAYS, T1); // T1 is array index
    asm.emitPOP(ALWAYS, T0); // T0 is array ref, T2 is array length
    asm.emitLDRimm(ALWAYS, T2, T0, ObjectModel.getArrayLengthOffset().toInt());  // Implicit null check
    asm.generateBoundsCheck(T1, T2);
    asm.emitLDRSB(ALWAYS, T2, T0, T1); // Sign-extend
    asm.emitPUSH(ALWAYS, T2);
  }

  @Override
  protected void emit_caload() {
    asm.emitPOP(ALWAYS, T1); // T1 is array index
    asm.emitPOP(ALWAYS, T0); // T0 is array ref, T2 is array length
    asm.emitLDRimm(ALWAYS, T2, T0, ObjectModel.getArrayLengthOffset().toInt());  // Implicit null check
    asm.generateBoundsCheck(T1, T2);
    asm.emitADD(ALWAYS, T1, T1, T1); // T1 *= 2 (two bytes per short)
    asm.emitLDRH(ALWAYS, T2, T0, T1);  // Zero-extend
    asm.emitPUSH(ALWAYS, T2);
  }

  @Override
  protected void emit_saload() {
    asm.emitPOP(ALWAYS, T1); // T1 is array index
    asm.emitPOP(ALWAYS, T0); // T0 is array ref, T2 is array length
    asm.emitLDRimm(ALWAYS, T2, T0, ObjectModel.getArrayLengthOffset().toInt());  // Implicit null check
    asm.generateBoundsCheck(T1, T2);
    asm.emitADD(ALWAYS, T1, T1, T1); // T1 *= 2 (two bytes per short)
    asm.emitLDRSH(ALWAYS, T2, T0, T1); // Sign-extend
    asm.emitPUSH(ALWAYS, T2);
  }

  /*
   * array stores
   */

  @Override
  protected void emit_iastore() {
    asm.emitPOP(ALWAYS, T2); // T2 is value to store
    asm.emitPOP(ALWAYS, T1); // T1 is array index
    asm.emitPOP(ALWAYS, T0); // T0 is array ref, T3 is array length
    asm.emitLDRimm(ALWAYS, T3, T0, ObjectModel.getArrayLengthOffset().toInt());  // Implicit null check
    asm.generateBoundsCheck(T1, T3);

    if (NEEDS_INT_ASTORE_BARRIER) {
      Barriers.compileArrayStoreBarrierInt(this); // Assumes T0 = ref, T1 = index, T2 = value
    } else {
      asm.emitSTRshift(ALWAYS, T2, T0, T1, LOG_BYTES_IN_INT);
    }
  }

  @Override
  protected void emit_lastore() {
    asm.emitVPOP64(ALWAYS, F0and1); // F0and1 is value to store
    asm.emitPOP(ALWAYS, T1); // T1 is array index
    asm.emitPOP(ALWAYS, T0); // T0 is array ref, T3 is array length
    asm.emitLDRimm(ALWAYS, T3, T0, ObjectModel.getArrayLengthOffset().toInt());  // Implicit null check
    asm.generateBoundsCheck(T1, T3);

    if (NEEDS_LONG_ASTORE_BARRIER) {
      Barriers.compileArrayStoreBarrierLong(this); // Assumes T0 = ref, T1 = index, F0and1 = value
    } else {
      asm.emitADDshift(ALWAYS, T0, T0, T1, LOG_BYTES_IN_LONG); // T0 is now address
      asm.emitVSTR64(ALWAYS, F0and1, T0, 0);
    }
  }

  @Override
  protected void emit_fastore() {
    asm.emitPOP(ALWAYS, T2); // T2 is value to store
    asm.emitPOP(ALWAYS, T1); // T1 is array index
    asm.emitPOP(ALWAYS, T0); // T0 is array ref, T3 is array length
    asm.emitLDRimm(ALWAYS, T3, T0, ObjectModel.getArrayLengthOffset().toInt());  // Implicit null check
    asm.generateBoundsCheck(T1, T3);

    if (NEEDS_FLOAT_ASTORE_BARRIER) {
      asm.emitVMOV_core_to_extension32(ALWAYS, F0, T2);
      Barriers.compileArrayStoreBarrierFloat(this); // Assumes T0 = ref, T1 = index, F0 = value
    } else {
      asm.emitSTRshift(ALWAYS, T2, T0, T1, LOG_BYTES_IN_FLOAT);
    }
  }

  @Override
  protected void emit_dastore() {
    asm.emitVPOP64(ALWAYS, F0and1); // F0and1 is value to store
    asm.emitPOP(ALWAYS, T1); // T1 is array index
    asm.emitPOP(ALWAYS, T0); // T0 is array ref, T3 is array length
    asm.emitLDRimm(ALWAYS, T3, T0, ObjectModel.getArrayLengthOffset().toInt());  // Implicit null check
    asm.generateBoundsCheck(T1, T3);

    if (NEEDS_DOUBLE_ASTORE_BARRIER) {
      Barriers.compileArrayStoreBarrierDouble(this); // Assumes T0 = ref, T1 = index, F0and1 = value
    } else {
      asm.emitADDshift(ALWAYS, T0, T0, T1, LOG_BYTES_IN_LONG); // T0 is now address
      asm.emitVSTR64(ALWAYS, F0and1, T0, 0);
    }
  }

  @Override
  protected void emit_aastore() {
    if (doesCheckStore) {
      emit_resolved_invokestatic((MethodReference)Entrypoints.aastoreMethod.getMemberRef());
    } else {
      emit_resolved_invokestatic((MethodReference)Entrypoints.aastoreUninterruptibleMethod.getMemberRef());
    }
  }

  @Override
  protected void emit_bastore() {
    asm.emitPOP(ALWAYS, T2); // T2 is value to store
    asm.emitPOP(ALWAYS, T1); // T1 is array index
    asm.emitPOP(ALWAYS, T0); // T0 is array ref, T3 is array length
    asm.emitLDRimm(ALWAYS, T3, T0, ObjectModel.getArrayLengthOffset().toInt());  // Implicit null check
    asm.generateBoundsCheck(T1, T3);

    if (NEEDS_BYTE_ASTORE_BARRIER) {
      Barriers.compileArrayStoreBarrierByte(this); // Assumes T0 = ref, T1 = index, T2 = value
    } else {
      asm.emitSTRB(ALWAYS, T2, T0, T1);
    }
  }

  @Override
  protected void emit_castore() {
    asm.emitPOP(ALWAYS, T2); // T2 is value to store
    asm.emitPOP(ALWAYS, T1); // T1 is array index
    asm.emitPOP(ALWAYS, T0); // T0 is array ref, T3 is array length
    asm.emitLDRimm(ALWAYS, T3, T0, ObjectModel.getArrayLengthOffset().toInt());  // Implicit null check
    asm.generateBoundsCheck(T1, T3);

    if (NEEDS_CHAR_ASTORE_BARRIER) {
      Barriers.compileArrayStoreBarrierChar(this); // Assumes T0 = ref, T1 = index, T2 = value
    } else {
      asm.emitADD(ALWAYS, T1, T1, T1); // T1 *= 2 (2 bytes per short)
      asm.emitSTRH(ALWAYS, T2, T0, T1);
    }
  }

  @Override
  protected void emit_sastore() {
    asm.emitPOP(ALWAYS, T2); // T2 is value to store
    asm.emitPOP(ALWAYS, T1); // T1 is array index
    asm.emitPOP(ALWAYS, T0); // T0 is array ref, T3 is array length
    asm.emitLDRimm(ALWAYS, T3, T0, ObjectModel.getArrayLengthOffset().toInt());  // Implicit null check
    asm.generateBoundsCheck(T1, T3);

    if (NEEDS_SHORT_ASTORE_BARRIER) {
      Barriers.compileArrayStoreBarrierShort(this); // Assumes T0 = ref, T1 = index, T2 = value
    } else {
      asm.emitADD(ALWAYS, T1, T1, T1); // T1 *= 2 (2 bytes per short)
      asm.emitSTRH(ALWAYS, T2, T0, T1);
    }
  }

  /*
   * expression stack manipulation
   */

  @Override
  protected void emit_pop() {
    asm.emitPOP(ALWAYS, T0);
  }

  @Override
  protected void emit_pop2() {
    asm.emitVPOP64(ALWAYS, F0and1);
  }

  @Override
  protected void emit_dup() {
    asm.emitPOP(ALWAYS, T0);
    asm.emitPUSH(ALWAYS, T0);
    asm.emitPUSH(ALWAYS, T0);
  }

  @Override
  protected void emit_dup_x1() {
    asm.emitPOP(ALWAYS, T0);
    asm.emitPOP(ALWAYS, T1);
    asm.emitPUSH(ALWAYS, T0);
    asm.emitPUSH(ALWAYS, T1);
    asm.emitPUSH(ALWAYS, T0);
  }

  @Override
  protected void emit_dup_x2() {
    asm.emitPOP(ALWAYS, T0);
    asm.emitVPOP64(ALWAYS, F0and1);
    asm.emitPUSH(ALWAYS, T0);
    asm.emitVPUSH64(ALWAYS, F0and1);
    asm.emitPUSH(ALWAYS, T0);
  }

  @Override
  protected void emit_dup2() {
    asm.emitVPOP64(ALWAYS, F0and1);
    asm.emitVPUSH64(ALWAYS, F0and1);
    asm.emitVPUSH64(ALWAYS, F0and1);
  }

  @Override
  protected void emit_dup2_x1() {
    asm.emitVPOP64(ALWAYS, F0and1);
    asm.emitPOP(ALWAYS, T2);
    asm.emitVPUSH64(ALWAYS, F0and1);
    asm.emitPUSH(ALWAYS, T2);
    asm.emitVPUSH64(ALWAYS, F0and1);
  }

  @Override
  protected void emit_dup2_x2() {
    asm.emitVPOP64(ALWAYS, F0and1);
    asm.emitVPOP64(ALWAYS, F2and3);
    asm.emitVPUSH64(ALWAYS, F0and1);
    asm.emitVPUSH64(ALWAYS, F2and3);
    asm.emitVPUSH64(ALWAYS, F0and1);
  }

  @Override
  protected void emit_swap() {
    asm.emitPOP(ALWAYS, T0);
    asm.emitPOP(ALWAYS, T1);
    asm.emitPUSH(ALWAYS, T0);
    asm.emitPUSH(ALWAYS, T1);
  }

  /*
   * int ALU
   */

  @Override
  protected void emit_iadd() {
    asm.emitPOP(ALWAYS, T0);
    asm.emitPOP(ALWAYS, T1);
    asm.emitADD(ALWAYS, T0, T1, T0);
    asm.emitPUSH(ALWAYS, T0);
  }

  @Override
  protected void emit_isub() {
    asm.emitPOP(ALWAYS, T0);
    asm.emitPOP(ALWAYS, T1);
    asm.emitSUB(ALWAYS, T0, T1, T0);
    asm.emitPUSH(ALWAYS, T0);
  }

  @Override
  protected void emit_imul() {
    asm.emitPOP(ALWAYS, T0);
    asm.emitPOP(ALWAYS, T1);
    asm.emitMUL(ALWAYS, T0, T1, T0);
    asm.emitPUSH(ALWAYS, T0);
  }

  @Override
  protected void emit_idiv() {
    asm.emitPOP(ALWAYS, T1);
    asm.emitPOP(ALWAYS, T0);
    asm.generateDivideByZeroCheck(T1); // TODO: maybe unnecessary if the division itself can trap
    asm.emitSDIV(ALWAYS, T0, T0, T1);  // T0 := T0/T1
    asm.emitPUSH(ALWAYS, T0);
  }

  @Override
  protected void emit_irem() {
    asm.emitPOP(ALWAYS, T1);
    asm.emitPOP(ALWAYS, T0);
    asm.generateDivideByZeroCheck(T1); // TODO: maybe unnecessary if the division itself can trap
    asm.emitSDIV(ALWAYS, T2, T0, T1);  // T2 := T0/T1
    asm.emitMUL(ALWAYS, T2, T2, T1);   // T2 := [T0/T1]*T1
    asm.emitSUB(ALWAYS, T1, T0, T2);   // T1 := T0 - [T0/T1]*T1
    asm.emitPUSH(ALWAYS, T1);
  }

  @Override
  protected void emit_ineg() {
    asm.emitPOP(ALWAYS, T0);
    asm.emitRSBimm(ALWAYS, T0, T0, 0);
    asm.emitPUSH(ALWAYS, T0);
  }

  @Override
  protected void emit_ishl() {
    asm.emitPOP(ALWAYS, T1);
    asm.emitPOP(ALWAYS, T0);
    asm.emitANDimm(ALWAYS, T1, T1, 0x1F);
    asm.emitLSL(ALWAYS, T0, T0, T1);      // T0 = T0 << T1
    asm.emitPUSH(ALWAYS, T0);
  }

  @Override
  protected void emit_ishr() {
    asm.emitPOP(ALWAYS, T1);
    asm.emitPOP(ALWAYS, T0);
    asm.emitANDimm(ALWAYS, T1, T1, 0x1F);
    asm.emitASR(ALWAYS, T0, T0, T1);    // T0 = T0 >> T1
    asm.emitPUSH(ALWAYS, T0);
  }

  @Override
  protected void emit_iushr() {
    asm.emitPOP(ALWAYS, T1);
    asm.emitPOP(ALWAYS, T0);
    asm.emitANDimm(ALWAYS, T1, T1, 0x1F);
    asm.emitLSR(ALWAYS, T0, T0, T1);    // T0 = T0 >>> T1
    asm.emitPUSH(ALWAYS, T0);
  }

  @Override
  protected void emit_iand() {
    asm.emitPOP(ALWAYS, T1);
    asm.emitPOP(ALWAYS, T0);
    asm.emitAND(ALWAYS, T0, T0, T1);
    asm.emitPUSH(ALWAYS, T0);
  }

  @Override
  protected void emit_ior() {
    asm.emitPOP(ALWAYS, T1);
    asm.emitPOP(ALWAYS, T0);
    asm.emitORR(ALWAYS, T0, T0, T1);
    asm.emitPUSH(ALWAYS, T0);
  }

  @Override
  protected void emit_ixor() {
    asm.emitPOP(ALWAYS, T1);
    asm.emitPOP(ALWAYS, T0);
    asm.emitEOR(ALWAYS, T0, T0, T1);
    asm.emitPUSH(ALWAYS, T0);
  }

  @Override
  protected void emit_iinc(int index, int val) {
    short loc = localGeneralLocations[index];
    if (isRegister(loc)) {
      asm.generateImmediateAdd(ALWAYS, GPR.lookup(loc), GPR.lookup(loc), val);
    } else {
      genLoadGeneralLocal(T0, loc);
      asm.generateImmediateAdd(ALWAYS, T0, T0, val);
      genStoreGeneralLocal(loc, T0);
    }
  }

  /*
   * long ALU
   */

  @Override
  protected void emit_ladd() {
    asm.emitVPOP64(ALWAYS, F0and1);
    asm.emitVPOP64(ALWAYS, F2and3);
    asm.emitVADDint64(F0and1, F2and3, F0and1);
    asm.emitVPUSH64(ALWAYS, F0and1);
  }

  @Override
  protected void emit_lsub() {
    asm.emitVPOP64(ALWAYS, F0and1);
    asm.emitVPOP64(ALWAYS, F2and3);
    asm.emitVSUBint64(F0and1, F2and3, F0and1);
    asm.emitVPUSH64(ALWAYS, F0and1);
  }

  @Override
  protected void emit_lmul() {
    asm.emitVPOP64(ALWAYS, F4and5); // L1 + H1 << 32
    asm.emitVPOP64(ALWAYS, F6and7); // L2 + H2 << 32
                                    // Desired result = L1 * L2 + (L1 * H2) << 32 + (L2 * H1) << 32
    asm.emitVMULLint32_scalar(F0and1and2and3, F4and5, F6and7); // Widening multiply each 32-bit piece of F4and5 by the lower-order 32-bit piece of F6and7
    // F0and1 = all 64 bits of L1 * L2
    // F2and3 = all 64 bits of H1 * L2 (we only need to keep the lower 32)

    asm.emitVSHL64imm(F2and3, F2and3, 32);
    // F2and3 = H1 * L2 << 32 (upper 64 bits are cut off)

    asm.emitVMULint32_scalar(F4and5, F6and7, F4and5); // Multiply each 32-bit piece of F6and7 by lower order 32-bit piece of F4and5
    // F4 = lower 32 bits of L1 * L2 (junk)
    // F5 = lower 32 L1 * H2

    asm.emitVSUBint64(F6and7, F6and7, F6and7); // Set F6and7 to 0
    asm.emitVMOV32(ALWAYS, F4, F6); // remove junk in F4 so F4and5 = L1 * H2 << 32

    asm.emitVADDint64(F0and1, F0and1, F2and3);
    asm.emitVADDint64(F0and1, F0and1, F4and5);

    asm.emitVPUSH64(ALWAYS, F0and1);
  }

  @Override
  protected void emit_ldiv() {
    asm.emitPOP(ALWAYS, T2);         // low word of denominator
    asm.emitPOP(ALWAYS, T3);         // high word
    asm.emitORR(ALWAYS, T0, T3, T2); // OR them together
    asm.generateDivideByZeroCheck(T0);
    asm.emitPOP(ALWAYS, T0);         // low word of numerator
    asm.emitPOP(ALWAYS, T1);         // high word

    // Pass params in T0,T1 and T2,T3; return value in T0,T1
    genSysCall(ALWAYS, Entrypoints.sysLongDivideIPField);

    asm.emitPUSH(ALWAYS, T1);
    asm.emitPUSH(ALWAYS, T0);
  }

  @Override
  protected void emit_lrem() {
    asm.emitPOP(ALWAYS, T2);         // low word of denominator
    asm.emitPOP(ALWAYS, T3);         // high word
    asm.emitORR(ALWAYS, T0, T3, T2); // OR them together
    asm.generateDivideByZeroCheck(T0);
    asm.emitPOP(ALWAYS, T0);         // low word of numerator
    asm.emitPOP(ALWAYS, T1);         // high word

    // Pass params in T0,T1 and T2,T3; return value in T0,T1
    genSysCall(ALWAYS, Entrypoints.sysLongRemainderIPField);

    asm.emitPUSH(ALWAYS, T1);
    asm.emitPUSH(ALWAYS, T0);
  }

  @Override
  protected void emit_lneg() {
    asm.emitPOP(ALWAYS, T0);  // low word
    asm.emitPOP(ALWAYS, T1);  // high word
    asm.emitRSBSimm(ALWAYS, T0, T0, 0); // Reverse subtract, set carry flag
    asm.emitRSCimm(ALWAYS, T1, T1, 0);  // Reverse subtract with carry
    asm.emitPUSH(ALWAYS, T1);
    asm.emitPUSH(ALWAYS, T0);
  }

  @Override
  protected void emit_lshl() {
    asm.emitPOP(ALWAYS, T0);
    asm.emitVPOP64(ALWAYS, F0and1);
    asm.emitANDimm(ALWAYS, T0, T0, 0x3F);
    asm.emitASRimm(ALWAYS, T1, T0, 31);    // T1 is the sign-extended high word (see emit_i2l)
    asm.emitVMOV_core_to_extension64(ALWAYS, F2and3, T0, T1);
    asm.emitVSHL64(F0and1, F0and1, F2and3);    // F0and1 = F0and1 << F2and3
    asm.emitVPUSH64(ALWAYS, F0and1);
  }

  @Override
  protected void emit_lshr() {
    asm.emitPOP(ALWAYS, T0);
    asm.emitVPOP64(ALWAYS, F0and1);
    asm.emitANDimm(ALWAYS, T0, T0, 0x3F);
    asm.emitRSBimm(ALWAYS, T0, T0, 0);     // Right shift is a negative left shift
    asm.emitASRimm(ALWAYS, T1, T0, 31);    // T1 is the sign-extended high word
    asm.emitVMOV_core_to_extension64(ALWAYS, F2and3, T0, T1);
    asm.emitVSHL64signed(F0and1, F0and1, F2and3);    // F0and1 = F0and1 >> F2and3
    asm.emitVPUSH64(ALWAYS, F0and1);
  }

  @Override
  protected void emit_lushr() {
    asm.emitPOP(ALWAYS, T0);
    asm.emitVPOP64(ALWAYS, F0and1);
    asm.emitANDimm(ALWAYS, T0, T0, 0x3F);
    asm.emitRSBimm(ALWAYS, T0, T0, 0);     // Right shift is a negative left shift
    asm.emitASRimm(ALWAYS, T1, T0, 31);    // T1 is the sign-extended high word
    asm.emitVMOV_core_to_extension64(ALWAYS, F2and3, T0, T1);
    asm.emitVSHL64(F0and1, F0and1, F2and3);    // F0and1 = F0and1 >>> F2and3
    asm.emitVPUSH64(ALWAYS, F0and1);
  }

  @Override
  protected void emit_land() {
    asm.emitVPOP64(ALWAYS, F0and1);
    asm.emitVPOP64(ALWAYS, F2and3);
    asm.emitVAND64(F0and1, F2and3, F0and1);
    asm.emitVPUSH64(ALWAYS, F0and1);
  }

  @Override
  protected void emit_lor() {
    asm.emitVPOP64(ALWAYS, F0and1);
    asm.emitVPOP64(ALWAYS, F2and3);
    asm.emitVORR64(F0and1, F2and3, F0and1);
    asm.emitVPUSH64(ALWAYS, F0and1);
  }

  @Override
  protected void emit_lxor() {
    asm.emitVPOP64(ALWAYS, F0and1);
    asm.emitVPOP64(ALWAYS, F2and3);
    asm.emitVEOR64(F0and1, F2and3, F0and1);
    asm.emitVPUSH64(ALWAYS, F0and1);
  }

  /*
   * float ALU
   */

  @Override
  protected void emit_fadd() {
    asm.emitVPOP32(ALWAYS, F0);
    asm.emitVPOP32(ALWAYS, F1);
    asm.emitVADD32(ALWAYS, F0, F1, F0);
    asm.emitVPUSH32(ALWAYS, F0);
  }

  @Override
  protected void emit_fsub() {
    asm.emitVPOP32(ALWAYS, F0);
    asm.emitVPOP32(ALWAYS, F1);
    asm.emitVSUB32(ALWAYS, F0, F1, F0);
    asm.emitVPUSH32(ALWAYS, F0);
  }

  @Override
  protected void emit_fmul() {
    asm.emitVPOP32(ALWAYS, F0);
    asm.emitVPOP32(ALWAYS, F1);
    asm.emitVMUL32(ALWAYS, F0, F1, F0);
    asm.emitVPUSH32(ALWAYS, F0);
  }

  @Override
  protected void emit_fdiv() {
    asm.emitVPOP32(ALWAYS, F0);
    asm.emitVPOP32(ALWAYS, F1);
    asm.emitVDIV32(ALWAYS, F0, F1, F0);
    asm.emitVPUSH32(ALWAYS, F0);
  }

  @Override
  protected void emit_frem() {
    asm.emitVPOP32(ALWAYS, F2);
    asm.emitVCVT_float32_to_float64(ALWAYS, F2and3, F2);
    asm.emitVPOP32(ALWAYS, F0);
    asm.emitVCVT_float32_to_float64(ALWAYS, F0and1, F0);

    // Pass params in F0and1 and F2and3; return value in F0and1
    genSysCall(ALWAYS, Entrypoints.sysDoubleRemainderIPField);

    asm.emitVCVT_float64_to_float32(ALWAYS, F0, F0and1);
    asm.emitVPUSH32(ALWAYS, F0);
  }

  @Override
  protected void emit_fneg() {
    asm.emitVPOP32(ALWAYS, F0);
    asm.emitVNEG32(ALWAYS, F0, F0);
    asm.emitVPUSH32(ALWAYS, F0);
  }

 /*
  * double ALU
  */

  @Override
  protected void emit_dadd() {
    asm.emitVPOP64(ALWAYS, F0and1);
    asm.emitVPOP64(ALWAYS, F2and3);
    asm.emitVADD64(ALWAYS, F0and1, F2and3, F0and1);
    asm.emitVPUSH64(ALWAYS, F0and1);
  }

  @Override
  protected void emit_dsub() {
    asm.emitVPOP64(ALWAYS, F0and1);
    asm.emitVPOP64(ALWAYS, F2and3);
    asm.emitVSUB64(ALWAYS, F0and1, F2and3, F0and1);
    asm.emitVPUSH64(ALWAYS, F0and1);
  }

  @Override
  protected void emit_dmul() {
    asm.emitVPOP64(ALWAYS, F0and1);
    asm.emitVPOP64(ALWAYS, F2and3);
    asm.emitVMUL64(ALWAYS, F0and1, F2and3, F0and1);
    asm.emitVPUSH64(ALWAYS, F0and1);
  }

  @Override
  protected void emit_ddiv() {
    asm.emitVPOP64(ALWAYS, F0and1);
    asm.emitVPOP64(ALWAYS, F2and3);
    asm.emitVDIV64(ALWAYS, F0and1, F2and3, F0and1);
    asm.emitVPUSH64(ALWAYS, F0and1);
  }

  @Override
  protected void emit_drem() {
    asm.emitVPOP64(ALWAYS, F2and3);
    asm.emitVPOP64(ALWAYS, F0and1);

    // Pass params in F0and1 and F2and3; return value in F0and1
    genSysCall(ALWAYS, Entrypoints.sysDoubleRemainderIPField);

    asm.emitVPUSH64(ALWAYS, F0and1);
  }

  @Override
  protected void emit_dneg() {
    asm.emitVPOP64(ALWAYS, F0and1);
    asm.emitVNEG64(ALWAYS, F0and1, F0and1);
    asm.emitVPUSH64(ALWAYS, F0and1);
  }

 /*
  * conversion ops
  */

  @Override
  protected void emit_i2l() {
    asm.emitPOP(ALWAYS, T0);
    asm.emitASRimm(ALWAYS, T1, T0, 31);    // T1 = T0 >> 31, so T1 is the sign extension
    asm.emitPUSH(ALWAYS, T1);
    asm.emitPUSH(ALWAYS, T0);
  }

  @Override
  protected void emit_i2f() {
    asm.emitVPOP32(ALWAYS, F0);
    asm.emitVCVT_int32_to_float32(ALWAYS, F0, F0);
    asm.emitVPUSH32(ALWAYS, F0);
  }

  @Override
  protected void emit_i2d() {
    asm.emitVPOP32(ALWAYS, F0);
    asm.emitVCVT_int32_to_float64(ALWAYS, F0and1, F0);
    asm.emitVPUSH64(ALWAYS, F0and1);
  }

  @Override
  protected void emit_l2i() {
    asm.emitPOP(ALWAYS, T0);
    asm.emitPOP(ALWAYS, T1);
    asm.emitPUSH(ALWAYS, T0);
  }

  @Override
  protected void emit_l2f() {
    asm.emitPOP(ALWAYS, T0); // low word
    asm.emitPOP(ALWAYS, T1); // high word

    // Pass param in T0,T1; return value in F0
    genSysCall(ALWAYS, Entrypoints.sysLongToFloatIPField);

    asm.emitVPUSH32(ALWAYS, F0);
  }

  @Override
  protected void emit_l2d() {
    asm.emitPOP(ALWAYS, T0); // low word
    asm.emitPOP(ALWAYS, T1); // high word

    // Pass param in T0,T1; return value in F0and1
    genSysCall(ALWAYS, Entrypoints.sysLongToDoubleIPField);

    asm.emitVPUSH64(ALWAYS, F0and1);
  }

  @Override
  protected void emit_f2i() {
    asm.emitVPOP32(ALWAYS, F0);
    asm.emitVCVT_float32_to_int32(ALWAYS, F0, F0);
    asm.emitVPUSH32(ALWAYS, F0);
  }

  @Override
  protected void emit_f2l() {
    asm.emitVPOP32(ALWAYS, F0);

    // Pass param in F0; return value in T0,T1
    genSysCall(ALWAYS, Entrypoints.sysFloatToLongIPField);

    asm.emitPUSH(ALWAYS, T1); // high word
    asm.emitPUSH(ALWAYS, T0); // low word
  }

  @Override
  protected void emit_f2d() {
    asm.emitVPOP32(ALWAYS, F0);
    asm.emitVCVT_float32_to_float64(ALWAYS, F0and1, F0);
    asm.emitVPUSH64(ALWAYS, F0and1);
  }

  @Override
  protected void emit_d2i() {
    asm.emitVPOP64(ALWAYS, F0and1);
    asm.emitVCVT_float64_to_int32(ALWAYS, F0, F0and1);
    asm.emitVPUSH32(ALWAYS, F0);
  }

  @Override
  protected void emit_d2l() {
    asm.emitVPOP64(ALWAYS, F0and1);

    // Pass param in F0and1; return value in T0,T1
    genSysCall(ALWAYS, Entrypoints.sysDoubleToLongIPField);

    asm.emitPUSH(ALWAYS, T1); // high word
    asm.emitPUSH(ALWAYS, T0); // low word
  }

  @Override
  protected void emit_d2f() {
    asm.emitVPOP64(ALWAYS, F0and1);
    asm.emitVCVT_float64_to_float32(ALWAYS, F0, F0and1);
    asm.emitVPUSH32(ALWAYS, F0);
  }

  @Override
  protected void emit_i2b() {
    asm.emitLDRSBimm(ALWAYS, T0, SP, 0); // sign-extend
    asm.emitPOP(ALWAYS, T1);             // Could avoid the POP using pre-indexed load instruction?
    asm.emitPUSH(ALWAYS, T0);
  }

  @Override
  protected void emit_i2c() {
    asm.emitLDRHimm(ALWAYS, T0, SP, 0);  // zero-extend
    asm.emitPOP(ALWAYS, T1);             // Could avoid the POP using pre-indexed load instruction?
    asm.emitPUSH(ALWAYS, T0);
  }

  @Override
  protected void emit_i2s() {
    asm.emitLDRSHimm(ALWAYS, T0, SP, 0); // sign-extend
    asm.emitPOP(ALWAYS, T1);             // Could avoid the POP using pre-indexed load instruction?
    asm.emitPUSH(ALWAYS, T0);
  }

  /*
   * comparison ops
   */

  @Override
  protected void emit_regular_lcmp() {
    asm.emitPOP(ALWAYS, T2);  // low word
    asm.emitPOP(ALWAYS, T3);  // high word
    asm.emitPOP(ALWAYS, T0);  // low word
    asm.emitPOP(ALWAYS, T1);  // high word

    asm.emitCMP(ALWAYS, T1, T3); // Check high words

    // Now check low words (only if high words eq) - shift down to do an unsigned compare
    asm.emitLSRimm(EQ, T3, T2, 1);
    asm.emitLSRimm(EQ, T1, T0, 1);
    asm.emitCMP(EQ, T1, T3);

    // Now the last bit of the low words that was shifted off before:
    asm.emitANDimm(EQ, T2, T2, 1);
    asm.emitANDimm(EQ, T0, T0, 1);
    asm.emitCMP(EQ, T0, T2);

    asm.emitMVNimm(LT, T0,-1);
    asm.emitMOVimm(EQ, T0, 0);
    asm.emitMOVimm(GT, T0, 1);
    asm.emitPUSH(ALWAYS, T0);
  }

  @Override
  protected void emit_regular_DFcmpGL(boolean single, boolean unorderedGT) {
    if (single) {
      asm.emitVPOP32(ALWAYS, F0);
      asm.emitVPOP32(ALWAYS, F1);
      asm.emitVCMP32(ALWAYS, F1, F0);
    } else {
      asm.emitVPOP64(ALWAYS, F0and1);
      asm.emitVPOP64(ALWAYS, F2and3);
      asm.emitVCMP64(ALWAYS, F2and3, F0and1);
    }
    asm.emitVMRS();

    asm.emitMVNimm(LT, T0, -1);
    asm.emitMOVimm(EQ, T0, 0);
    asm.emitMOVimm(GT, T0, 1);
    if (unorderedGT)
      asm.emitMOVimm(UNORDERED, T0, 1);
    else
      asm.emitMVNimm(UNORDERED, T0, -1);
    // Could do this in 3 instructions instead of 4 using the more obscure condition options
    asm.emitPUSH(ALWAYS, T0);
  }

  /*
   * branching
   */

  /**
   * @param bc the branch condition
   * @return assembler constant equivalent for the branch condition
   */
  @Pure
  private COND mapCondition(BranchCondition bc) {
    switch (bc) {
      case EQ: return EQ;
      case NE: return NE;
      case LT: return LT;
      case GE: return GE;
      case GT: return GT;
      case LE: return LE;
      default: if (VM.VerifyAssertions) VM._assert(VM.NOT_REACHED); return NOCOND;
    }
  }

  @Override
  @Inline(value = Inline.When.ArgumentsAreConstant, arguments = {2})
  protected void emit_if(int bTarget, BranchCondition bc) {
    asm.emitPOP(ALWAYS, T0);
    asm.emitCMPimm(ALWAYS, T0, 0);
    genCondBranch(mapCondition(bc), bTarget);
  }

  @Override
  @Inline(value = Inline.When.ArgumentsAreConstant, arguments = {2})
  protected void emit_if_icmp(int bTarget, BranchCondition bc) {
    asm.emitPOP(ALWAYS, T1);
    asm.emitPOP(ALWAYS, T0);
    asm.emitCMP(ALWAYS, T0, T1);
    genCondBranch(mapCondition(bc), bTarget);
  }

  @Override
  protected void emit_if_acmpeq(int bTarget) {
    asm.emitPOP(ALWAYS, T1);
    asm.emitPOP(ALWAYS, T0);
    asm.emitCMP(ALWAYS, T0, T1);
    genCondBranch(EQ, bTarget);
  }

  @Override
  protected void emit_if_acmpne(int bTarget) {
    asm.emitPOP(ALWAYS, T1);
    asm.emitPOP(ALWAYS, T0);
    asm.emitCMP(ALWAYS, T0, T1);
    genCondBranch(NE, bTarget);
  }

  @Override
  protected void emit_ifnull(int bTarget) {
    asm.emitPOP(ALWAYS, T0);
    asm.emitCMPimm(ALWAYS, T0, 0);
    genCondBranch(EQ, bTarget);
  }

  @Override
  protected void emit_ifnonnull(int bTarget) {
    asm.emitPOP(ALWAYS, T0);
    asm.emitCMPimm(ALWAYS, T0, 0);
    genCondBranch(NE, bTarget);
  }

  @Override
  protected void emit_goto(int bTarget) {
    int mTarget = bytecodeMap[bTarget];                  // mTarget will be the machine code address, if known; otherwise, zero
    asm.generateUnknownBranch(ALWAYS, mTarget, bTarget); // Need this macro because it might be a forward or backward branch
  }

  @Override
  protected void emit_jsr(int bTarget) {
    asm.emitADDimm(ALWAYS, T0, PC, INSTRUCTION_WIDTH); // ARM PC points 2 instructions ahead, so adding 1 will give the address after the goto
    asm.emitPUSH(ALWAYS, T0);
    emit_goto(bTarget);
  }

  @Override
  protected void emit_ret(int index) {
    short location = localGeneralLocations[index];

    if (!isRegister(location)) {
      genLoadGeneralLocal(T0, location);
      location = T0.value();
    }

    asm.emitBX(ALWAYS, GPR.lookup(location));
  }

  @Override
  protected void emit_tableswitch(int defaultval, int low, int high) {
    int n = high - low + 1;             // n = number of normal cases (0..n-1)
    int firstCounter = edgeCounterIdx;  // only used if options.PROFILE_EDGE_COUNTERS;

    if (options.PROFILE_EDGE_COUNTERS) {
      edgeCounterIdx += n + 1; // allocate n+1 counters
      genLoadCounterArray(T2); // Load counter array for this method (clobbers T0, T1)
    }                          // T2 now holds array address

    asm.emitPOP(ALWAYS, T0);  // T0 is index

    asm.generateImmediateSubtract(ALWAYS, T0, T0, low); // T0 = T0 - low;

    // T0 is now index - low; compare T0 against n = high - low + 1
    // If T0 < 0 or T0 >= n, pick default branch

    asm.generateImmediateCompare(ALWAYS, T0, n); // Compare T0 against n

    // The HS condition (unsigned greater than or equal) will trip if T0 is negative (highest bit set) or if T0 is actually >= n

    if (options.PROFILE_EDGE_COUNTERS)
      genIncEdgeCounter(HS, T2, T1, firstCounter + n); // Increment default counter (clobbers T1, but not T2)

    int bTarget = biStart + defaultval; // biStart is set to the current bytecode by TemplateCompilerFramework
    int mTarget = bytecodeMap[bTarget];              // mTarget will be the machine code address, if known; otherwise, zero
    asm.generateUnknownBranch(HS, mTarget, bTarget); // Need this macro because it might be a forward or backward branch

    // HS not tripped, lookup table - can now use T0 as the index

    if (options.PROFILE_EDGE_COUNTERS)
      genIncEdgeCounterReg(ALWAYS, T2, T1, firstCounter, T0); // Pick a counter to increment using T0 (clobbers T1, but not T2 or T0)


    // T0 is in words but we need it to be in bytes (so shift it) when calculating offset of destination
    // Also, the ARM PC points 2 instructions ahead, so this is the correct place to do the load:

    asm.emitLDRshift(ALWAYS, T0, PC, T0, LOG_BYTES_IN_INT); // Load the destination (relative offset) from the table below
    int baseM = asm.getMachineCodeIndex();                  // All offsets need to be relative to this point
    asm.emitADD(ALWAYS, PC, PC, T0);                        // Branch to the destination (calculate absolute address by adding the PC)

    // Now print the table: n relative offsets

    for (int i = 0; i < n; i++) {
      bTarget = biStart + bcodes.getTableSwitchOffset(i);
      mTarget = bytecodeMap[bTarget];      // mTarget will be the machine code address, if known; otherwise, zero
      asm.generateSwitchCase(baseM, mTarget, bTarget);
    }
    bcodes.skipTableSwitchOffsets(n);
  }

  @Override
  protected void emit_lookupswitch(int defaultval, int npairs) {
    if (options.PROFILE_EDGE_COUNTERS) {
      // Load counter array for this method
      genLoadCounterArray(T2);
    }

    asm.emitPOP(ALWAYS, T0); // T0 is key

    // Check key against all the values
    for (int i = 0; i < npairs; i++) {
      int match = bcodes.getLookupSwitchValue(i);
      asm.generateImmediateCompare(ALWAYS, T0, match); // Compare T0 against match

      if (options.PROFILE_EDGE_COUNTERS)
        genIncEdgeCounter(EQ, T2, T1, edgeCounterIdx++); // Increment counter (clobbers T1)

      int bTarget = biStart + bcodes.getLookupSwitchOffset(i);
      int mTarget = bytecodeMap[bTarget];              // mTarget will be the machine code address, if known; otherwise, zero
      asm.generateUnknownBranch(EQ, mTarget, bTarget); // Need this macro because it might be a forward or backward branch

    }

    // Key doesn't match any of the values, take default branch

    bcodes.skipLookupSwitchPairs(npairs);

    if (options.PROFILE_EDGE_COUNTERS)
      genIncEdgeCounter(ALWAYS, T2, T1, edgeCounterIdx++);

    int bTarget = biStart + defaultval;
    int mTarget = bytecodeMap[bTarget];
    asm.generateUnknownBranch(ALWAYS, mTarget, bTarget);
  }

  /*
   * returns (from function; NOT ret)
   */

  @Override
  protected void emit_ireturn() {
    if (method.isSynchronized()) genSynchronizedMethodEpilogue();
    asm.emitPOP(ALWAYS, T0); // Return value in T0
    genEpilogue();
  }

  @Override
  protected void emit_lreturn() {
    if (method.isSynchronized()) genSynchronizedMethodEpilogue();
    asm.emitVPOP64(ALWAYS, F0and1); // Return value in F0and1
    genEpilogue();
  }

  @Override
  protected void emit_freturn() {
    if (method.isSynchronized()) genSynchronizedMethodEpilogue();
    asm.emitVPOP32(ALWAYS, F0); // Return value in F0
    genEpilogue();
  }

  @Override
  protected void emit_dreturn() {
    if (method.isSynchronized()) genSynchronizedMethodEpilogue();
    asm.emitVPOP64(ALWAYS, F0and1); // Return value in F0and1
    genEpilogue();
  }

  @Override
  protected void emit_areturn() {
    if (method.isSynchronized()) genSynchronizedMethodEpilogue();
    asm.emitPOP(ALWAYS, T0); // Return value in T0
    genEpilogue();
  }

  @Override
  protected void emit_return() {
    if (method.isSynchronized()) genSynchronizedMethodEpilogue();
    if (method.isObjectInitializer() && method.getDeclaringClass().declaresFinalInstanceField()) {
      // JMM compliance. Emit StoreStore barrier
      asm.emitDMBst();
    }
    genEpilogue();
  }

  /*
   * field access
   */

  @Override
  protected void emit_unresolved_getstatic(FieldReference fieldRef) {
    genDynamicLinkingSequence(T0, fieldRef); // T0 = offset from JTOC  (clobbers T0-T3, LR)
    if (NEEDS_OBJECT_GETSTATIC_BARRIER && fieldRef.getFieldContentsType().isReferenceType()) {
      Barriers.compileGetstaticBarrier(this, fieldRef.getId()); // Assumes offset in T0, returns value in T0
      asm.emitPUSH(ALWAYS, T0);
    } else if (fieldRef.getSize() <= BYTES_IN_INT) { // field is one word
      asm.emitLDR(ALWAYS, T0, JTOC, T0);
      asm.emitPUSH(ALWAYS, T0);
    } else { // field is two words (double or long)
      if (VM.VerifyAssertions) VM._assert(fieldRef.getSize() == BYTES_IN_LONG);
      asm.emitADD(ALWAYS, T0, JTOC, T0);
      asm.emitVLDR64(ALWAYS, F0and1, T0, 0);
      asm.emitVPUSH64(ALWAYS, F0and1);
    }

    // JMM: could be volatile (post-barrier when first operation)
    // LoadLoad and LoadStore barriers.
    asm.emitDMB();
  }

  @Override
  protected void emit_resolved_getstatic(FieldReference fieldRef) {
    RVMField field = fieldRef.peekResolvedField();
    Offset fieldOffset = field.getOffset();

    if (NEEDS_OBJECT_GETSTATIC_BARRIER && fieldRef.getFieldContentsType().isReferenceType() && !field.isUntraced()) {
      asm.generateImmediateLoad(ALWAYS, T0, fieldOffset.toInt());
      Barriers.compileGetstaticBarrier(this, fieldRef.getId()); // Assumes offset in T0, returns value in T0
      asm.emitPUSH(ALWAYS, T0);
    } else if (fieldRef.getSize() <= BYTES_IN_INT) { // field is one word
      asm.generateOffsetLoad(ALWAYS, T0, JTOC, fieldOffset);
      asm.emitPUSH(ALWAYS, T0);
    } else { // field is two words (double or long)
      if (VM.VerifyAssertions) VM._assert(fieldRef.getSize() == BYTES_IN_LONG);
      asm.generateOffsetLoad64(ALWAYS, F0and1, JTOC, fieldOffset);
      asm.emitVPUSH64(ALWAYS, F0and1);
    }

    if (field.isVolatile()) {
      // JMM: post-barrier when first operation
      // LoadLoad and LoadStore barriers.
      asm.emitDMB();
    }
  }

  @Override
  protected void emit_unresolved_putstatic(FieldReference fieldRef) {
    // JMM: could be volatile (pre-barrier when second operation)
    // StoreStore barrier.
    asm.emitDMBst();

    genDynamicLinkingSequence(T0, fieldRef); // T1 = offset from JTOC  (clobbers T0-T3, LR)
    if (NEEDS_OBJECT_PUTSTATIC_BARRIER && !fieldRef.getFieldContentsType().isPrimitiveType()) {
      Barriers.compilePutstaticBarrier(this, fieldRef.getId()); // Expects offset in T1, value on stack
    } else if (fieldRef.getSize() <= BYTES_IN_INT) { // field is one word
      asm.emitPOP(ALWAYS, T1);
      asm.emitSTR(ALWAYS, T1, JTOC, T0);
    } else { // field is two words (double or long)
      if (VM.VerifyAssertions) VM._assert(fieldRef.getSize() == BYTES_IN_LONG);
      asm.emitVPOP64(ALWAYS, F0and1);
      asm.emitADD(ALWAYS, T0, JTOC, T0);
      asm.emitVSTR64(ALWAYS, F0and1, T0, 0);
    }

    // JMM: Could be volatile, post-barrier when first operation
    // StoreLoad barrier.
    asm.emitDMB();
  }

  @Override
  protected void emit_resolved_putstatic(FieldReference fieldRef) {
    RVMField field = fieldRef.peekResolvedField();
    Offset fieldOffset = field.getOffset();

    if (field.isVolatile()) {
      // JMM: (pre-barrier when second operation)
      // StoreStore barrier.
      asm.emitDMBst();
    }

    if (NEEDS_OBJECT_PUTSTATIC_BARRIER && !fieldRef.getFieldContentsType().isPrimitiveType() && !field.isUntraced()) {
      asm.generateImmediateLoad(ALWAYS, T1, fieldOffset.toInt());
      Barriers.compilePutstaticBarrier(this, fieldRef.getId()); // Expects offset in T1, value on stack
    } else if (fieldRef.getSize() <= BYTES_IN_INT) { // field is one word
      asm.emitPOP(ALWAYS, T0);
      asm.generateOffsetStore(ALWAYS, T0, JTOC, fieldOffset);
    } else { // field is two words (double or long)
      if (VM.VerifyAssertions) VM._assert(fieldRef.getSize() == BYTES_IN_LONG);
      asm.emitVPOP64(ALWAYS, F0and1);
      asm.generateOffsetStore64(ALWAYS, F0and1, JTOC, fieldOffset);
    }

    if (field.isVolatile()) {
      // JMM: post-barrier when first operation
      // StoreLoad barrier.
      asm.emitDMB();
    }
  }

  @Override
  protected void emit_unresolved_getfield(FieldReference fieldRef) {
    TypeReference fieldType = fieldRef.getFieldContentsType();
    genDynamicLinkingSequence(T1, fieldRef); // T1 = offset from object reference  (clobbers T0-T3, LR)
    if (NEEDS_OBJECT_GETFIELD_BARRIER && fieldType.isReferenceType()) {
      Barriers.compileGetfieldBarrier(this, fieldRef.getId()); // Expects offset in T1, object reference on stack, returns value in T0
      asm.emitPUSH(ALWAYS, T0);
    } else {
      // T2 = object reference
      asm.emitPOP(ALWAYS, T2);
      if (fieldType.isReferenceType() || fieldType.isWordLikeType() || fieldType.isIntType() || fieldType.isFloatType()) {
        // 32bit load
        asm.emitLDR(ALWAYS, T0, T2, T1);
        asm.emitPUSH(ALWAYS, T0);
      } else if (fieldType.isBooleanType()) {
        // 8bit unsigned load
        asm.emitLDRB(ALWAYS, T0, T2, T1);
        asm.emitPUSH(ALWAYS, T0);
      } else if (fieldType.isByteType()) {
        // 8bit signed load
        asm.emitLDRSB(ALWAYS, T0, T2, T1);
        asm.emitPUSH(ALWAYS, T0);
      } else if (fieldType.isShortType()) {
        // 16bit signed load
        asm.emitLDRSH(ALWAYS, T0, T2, T1);
        asm.emitPUSH(ALWAYS, T0);
      } else if (fieldType.isCharType()) {
        // 16bit unsigned load
        asm.emitLDRH(ALWAYS, T0, T2, T1);
        asm.emitPUSH(ALWAYS, T0);
      } else {
        // 64bit load
        if (VM.VerifyAssertions) VM._assert(fieldType.isLongType() || fieldType.isDoubleType());
        asm.emitADD(ALWAYS, T2, T2, T1);
        asm.emitVLDR64(ALWAYS, F0and1, T2, 0);
        asm.emitVPUSH64(ALWAYS, F0and1);
      }
    }

    // JMM: Could be volatile; post-barrier when first operation
    // LoadLoad and LoadStore barriers.
    asm.emitDMB();
  }

  @Override
  protected void emit_resolved_getfield(FieldReference fieldRef) {
    RVMField field = fieldRef.peekResolvedField();
    TypeReference fieldType = fieldRef.getFieldContentsType();
    Offset fieldOffset = field.getOffset();
    if (NEEDS_OBJECT_GETFIELD_BARRIER && fieldType.isReferenceType() && !field.isUntraced()) {
      asm.generateImmediateLoad(ALWAYS, T1, fieldOffset.toInt());
      Barriers.compileGetfieldBarrier(this, fieldRef.getId()); // Expects offset in T1, object reference on stack, returns value in T0
      asm.emitPUSH(ALWAYS, T0);
    } else {
      asm.emitPOP(ALWAYS, T2); // T2 = object reference
      if (fieldType.isReferenceType() || fieldType.isWordLikeType() || fieldType.isIntType() || fieldType.isFloatType()) {
        // 32bit load
        asm.emitLDRimm(ALWAYS, T0, T2, fieldOffset.toInt());
        asm.emitPUSH(ALWAYS, T0);
      } else if (fieldType.isBooleanType()) {
        // 8bit unsigned load
        asm.emitLDRBimm(ALWAYS, T0, T2, fieldOffset.toInt());
        asm.emitPUSH(ALWAYS, T0);
      } else if (fieldType.isByteType()) {
        // 8bit signed load
        asm.emitLDRSBimm(ALWAYS, T0, T2, fieldOffset.toInt());
        asm.emitPUSH(ALWAYS, T0);
      } else if (fieldType.isShortType()) {
        // 16bit signed load
        asm.emitLDRSHimm(ALWAYS, T0, T2, fieldOffset.toInt());
        asm.emitPUSH(ALWAYS, T0);
      } else if (fieldType.isCharType()) {
        // 16bit unsigned load
        asm.emitLDRHimm(ALWAYS, T0, T2, fieldOffset.toInt());
        asm.emitPUSH(ALWAYS, T0);
      } else {
        // 64bit load
        if (VM.VerifyAssertions) VM._assert(fieldType.isLongType() || fieldType.isDoubleType());
        asm.emitVLDR64(ALWAYS, F0and1, T2, fieldOffset.toInt());
        asm.emitVPUSH64(ALWAYS, F0and1);
      }
    }

    if (field.isVolatile()) {
      // JMM: post-barrier when first operation
      // LoadLoad and LoadStore barriers.
      asm.emitDMB();
    }
  }

  @Override
  protected void emit_unresolved_putfield(FieldReference fieldRef) {
    // JMM: could be volatile (pre-barrier when second operation)
    // StoreStore barrier.
    asm.emitDMBst();

    TypeReference fieldType = fieldRef.getFieldContentsType();
    genDynamicLinkingSequence(T2, fieldRef); // T2 = offset from object reference  (clobbers T0-T3, LR)
    if (NEEDS_OBJECT_PUTFIELD_BARRIER && fieldType.isReferenceType()) {
      Barriers.compilePutfieldBarrier(this, fieldRef.getId()); // Expects offset in T2, value and object reference on stack
    } else if (NEEDS_BOOLEAN_PUTFIELD_BARRIER && fieldType.isBooleanType()) {
      Barriers.compilePutfieldBarrierBoolean(this, fieldRef.getId()); // Expects offset in T2, value and object reference on stack
    } else if (NEEDS_BYTE_PUTFIELD_BARRIER && fieldType.isByteType()) {
      Barriers.compilePutfieldBarrierByte(this, fieldRef.getId()); // Expects offset in T2, value and object reference on stack
    } else if (NEEDS_CHAR_PUTFIELD_BARRIER && fieldType.isCharType()) {
      Barriers.compilePutfieldBarrierChar(this, fieldRef.getId()); // Expects offset in T2, value and object reference on stack
    } else if (NEEDS_SHORT_PUTFIELD_BARRIER && fieldType.isShortType()) {
      Barriers.compilePutfieldBarrierShort(this, fieldRef.getId()); // Expects offset in T2, value and object reference on stack
    } else if (NEEDS_INT_PUTFIELD_BARRIER && fieldType.isIntType()) {
      Barriers.compilePutfieldBarrierInt(this, fieldRef.getId()); // Expects offset in T2, value and object reference on stack
    } else if (NEEDS_WORD_PUTFIELD_BARRIER && fieldType.isWordType()) {
      Barriers.compilePutfieldBarrierWord(this, fieldRef.getId()); // Expects offset in T2, value and object reference on stack
    } else if (NEEDS_ADDRESS_PUTFIELD_BARRIER && fieldType.isAddressType()) {
      Barriers.compilePutfieldBarrierAddress(this, fieldRef.getId()); // Expects offset in T2, value and object reference on stack
    } else if (NEEDS_OFFSET_PUTFIELD_BARRIER && fieldType.isOffsetType()) {
      Barriers.compilePutfieldBarrierOffset(this, fieldRef.getId()); // Expects offset in T2, value and object reference on stack
    } else if (NEEDS_EXTENT_PUTFIELD_BARRIER && fieldType.isExtentType()) {
      Barriers.compilePutfieldBarrierExtent(this, fieldRef.getId()); // Expects offset in T2, value and object reference on stack
    } else if (NEEDS_FLOAT_PUTFIELD_BARRIER && fieldType.isFloatType()) {
      asm.emitMOV(ALWAYS, T1, T2);
      Barriers.compilePutfieldBarrierFloat(this, fieldRef.getId()); // Expects offset in T1, value and object reference on stack
    } else if (NEEDS_DOUBLE_PUTFIELD_BARRIER && fieldType.isDoubleType()) {
      asm.emitMOV(ALWAYS, T1, T2);
      Barriers.compilePutfieldBarrierDouble(this, fieldRef.getId()); // Expects offset in T1, value and object reference on stack
    } else if (NEEDS_LONG_PUTFIELD_BARRIER && fieldType.isLongType()) {
      asm.emitMOV(ALWAYS, T1, T2);
      Barriers.compilePutfieldBarrierLong(this, fieldRef.getId()); // Expects offset in T1, value and object reference on stack
    } else if (fieldType.isWordLikeType() || fieldType.isReferenceType() || fieldType.isIntType() || fieldType.isFloatType()) {
      // 32bit store
      asm.emitPOP(ALWAYS, T0);    // T0 = value
      asm.emitPOP(ALWAYS, T1);    // T1 = object reference
      asm.emitSTR(ALWAYS, T0, T1, T2);
    } else if (fieldType.isBooleanType() || fieldType.isByteType()) {
      // 8bit store
      asm.emitPOP(ALWAYS, T0);    // T0 = value
      asm.emitPOP(ALWAYS, T1);    // T1 = object reference
      asm.emitSTRB(ALWAYS, T0, T1, T2);
    } else if (fieldType.isShortType() || fieldType.isCharType()) {
      // 16bit store
      asm.emitPOP(ALWAYS, T0);    // T0 = value
      asm.emitPOP(ALWAYS, T1);    // T1 = object reference
      asm.emitSTRH(ALWAYS, T0, T1, T2);
    } else {
      // 64bit store
      if (VM.VerifyAssertions) VM._assert(fieldType.isLongType() || fieldType.isDoubleType());
      asm.emitVPOP64(ALWAYS, F0and1); // F0and1 = value
      asm.emitPOP(ALWAYS, T1);        // T1 = object reference
      asm.emitADD(ALWAYS, T2, T1, T2);
      asm.emitVSTR64(ALWAYS, F0and1, T2, 0);
    }

    // JMM: Could be volatile; post-barrier when first operation
    // StoreLoad barrier.
    asm.emitDMB();
  }

  @Override
  protected void emit_resolved_putfield(FieldReference fieldRef) {
    RVMField field = fieldRef.peekResolvedField();
    Offset fieldOffset = field.getOffset();
    TypeReference fieldType = fieldRef.getFieldContentsType();

    if (field.isVolatile()) {
      // JMM: pre-barrier when second operation
      // StoreStore barrier.
      asm.emitDMBst();
    }

    if (NEEDS_OBJECT_PUTFIELD_BARRIER && fieldType.isReferenceType() && !field.isUntraced()) {
      asm.generateImmediateLoad(ALWAYS, T2, fieldOffset.toInt());
      Barriers.compilePutfieldBarrier(this, fieldRef.getId()); // Expects offset in T2, value and object reference on stack
    } else if (NEEDS_BOOLEAN_PUTFIELD_BARRIER && fieldType.isBooleanType()) {
      asm.generateImmediateLoad(ALWAYS, T2, fieldOffset.toInt());
      Barriers.compilePutfieldBarrierBoolean(this, fieldRef.getId()); // Expects offset in T2, value and object reference on stack
    } else if (NEEDS_BYTE_PUTFIELD_BARRIER && fieldType.isByteType()) {
      asm.generateImmediateLoad(ALWAYS, T2, fieldOffset.toInt());
      Barriers.compilePutfieldBarrierByte(this, fieldRef.getId()); // Expects offset in T2, value and object reference on stack
    } else if (NEEDS_CHAR_PUTFIELD_BARRIER && fieldType.isCharType()) {
      asm.generateImmediateLoad(ALWAYS, T2, fieldOffset.toInt());
      Barriers.compilePutfieldBarrierChar(this, fieldRef.getId()); // Expects offset in T2, value and object reference on stack
    } else if (NEEDS_SHORT_PUTFIELD_BARRIER && fieldType.isShortType()) {
      asm.generateImmediateLoad(ALWAYS, T2, fieldOffset.toInt());
      Barriers.compilePutfieldBarrierShort(this, fieldRef.getId()); // Expects offset in T2, value and object reference on stack
    } else if (NEEDS_INT_PUTFIELD_BARRIER && fieldType.isIntType()) {
      asm.generateImmediateLoad(ALWAYS, T2, fieldOffset.toInt());
      Barriers.compilePutfieldBarrierInt(this, fieldRef.getId()); // Expects offset in T2, value and object reference on stack
    } else if (NEEDS_WORD_PUTFIELD_BARRIER && fieldType.isWordType()) {
      asm.generateImmediateLoad(ALWAYS, T2, fieldOffset.toInt());
      Barriers.compilePutfieldBarrierWord(this, fieldRef.getId()); // Expects offset in T2, value and object reference on stack
    } else if (NEEDS_ADDRESS_PUTFIELD_BARRIER && fieldType.isAddressType()) {
      asm.generateImmediateLoad(ALWAYS, T2, fieldOffset.toInt());
      Barriers.compilePutfieldBarrierAddress(this, fieldRef.getId()); // Expects offset in T2, value and object reference on stack
    } else if (NEEDS_OFFSET_PUTFIELD_BARRIER && fieldType.isOffsetType()) {
      asm.generateImmediateLoad(ALWAYS, T2, fieldOffset.toInt());
      Barriers.compilePutfieldBarrierOffset(this, fieldRef.getId()); // Expects offset in T2, value and object reference on stack
    } else if (NEEDS_EXTENT_PUTFIELD_BARRIER && fieldType.isExtentType()) {
      asm.generateImmediateLoad(ALWAYS, T2, fieldOffset.toInt());
      Barriers.compilePutfieldBarrierExtent(this, fieldRef.getId()); // Expects offset in T2, value and object reference on stack
    } else if (NEEDS_FLOAT_PUTFIELD_BARRIER && fieldType.isFloatType()) {
      asm.generateImmediateLoad(ALWAYS, T1, fieldOffset.toInt());
      Barriers.compilePutfieldBarrierFloat(this, fieldRef.getId()); // Expects offset in T1, value and object reference on stack
    } else if (NEEDS_DOUBLE_PUTFIELD_BARRIER && fieldType.isDoubleType()) {
      asm.generateImmediateLoad(ALWAYS, T1, fieldOffset.toInt());
      Barriers.compilePutfieldBarrierDouble(this, fieldRef.getId()); // Expects offset in T1, value and object reference on stack
    } else if (NEEDS_LONG_PUTFIELD_BARRIER && fieldType.isLongType()) {
      asm.generateImmediateLoad(ALWAYS, T1, fieldOffset.toInt());
      Barriers.compilePutfieldBarrierLong(this, fieldRef.getId()); // Expects offset in T1, value and object reference on stack
    } else if (fieldType.isWordLikeType() || fieldType.isReferenceType() || fieldType.isIntType() || fieldType.isFloatType()) {
      // 32bit store
      asm.emitPOP(ALWAYS, T0);    // T0 = value
      asm.emitPOP(ALWAYS, T1);    // T1 = object reference
      asm.emitSTRimm(ALWAYS, T0, T1, fieldOffset.toInt());
    } else if (fieldType.isBooleanType() || fieldType.isByteType()) {
      // 8bit store
      asm.emitPOP(ALWAYS, T0);    // T0 = value
      asm.emitPOP(ALWAYS, T1);    // T1 = object reference
      asm.emitSTRBimm(ALWAYS, T0, T1, fieldOffset.toInt());
    } else if (fieldType.isShortType() || fieldType.isCharType()) {
      // 16bit store
      asm.emitPOP(ALWAYS, T0);    // T0 = value
      asm.emitPOP(ALWAYS, T1);    // T1 = object reference
      asm.emitSTRHimm(ALWAYS, T0, T1, fieldOffset.toInt());
    } else {
      // 64bit store
      if (VM.VerifyAssertions) VM._assert(fieldType.isLongType() || fieldType.isDoubleType());
      asm.emitVPOP64(ALWAYS, F0and1); // F0and1 = value
      asm.emitPOP(ALWAYS, T1);        // T1 = object reference
      asm.emitVSTR64(ALWAYS, F0and1, T1, fieldOffset.toInt());
    }

    if (field.isVolatile()) {
      // JMM: post-barrier when first operation
      // StoreLoad barrier.
      asm.emitDMB();
    }
  }

  /*
   * method invocation
   */

  @Override
  protected void emit_unresolved_invokevirtual(MethodReference methodRef) {
    int thisPointerOffset = methodRef.getParameterWords() << LOG_BYTES_IN_INT; // Return value doesn't include "this" pointer
    genDynamicLinkingSequence(T0, methodRef);          // T0 = method offset  (clobbers T0-T3, LR)
    asm.emitLDRimm(ALWAYS, T2, SP, thisPointerOffset); // T2 = object reference (peek at "this" pointer on stack)
    asm.baselineEmitLoadTIB(T1, T2);                   // T1 = TIB
    asm.emitLDR(ALWAYS, LR, T1, T0);                   // Load method address
    genMoveParametersToRegisters(true, methodRef);
    asm.emitBLX(ALWAYS, LR);
    genPopParametersAndPushReturnValue(true, methodRef);
  }

  @Override
  protected void emit_resolved_invokevirtual(MethodReference methodRef) {
    int thisPointerOffset = methodRef.getParameterWords() << LOG_BYTES_IN_INT; // Return value doesn't include "this" pointer
    Offset methodOffset = methodRef.peekResolvedMethod().getOffset();
    asm.emitLDRimm(ALWAYS, T0, SP, thisPointerOffset);   // T0 = object reference (peek at "this" pointer on stack)
    asm.baselineEmitLoadTIB(T1, T0);                     // T1 = TIB
    asm.generateOffsetLoad(ALWAYS, LR, T1, methodOffset);// Load method address
    genMoveParametersToRegisters(true, methodRef);
    asm.emitBLX(ALWAYS, LR);
    genPopParametersAndPushReturnValue(true, methodRef);
  }

  @Override
  protected void emit_resolved_invokespecial(MethodReference methodRef, RVMMethod target) {
    if (target.isObjectInitializer()) { // invoke via method's jtoc slot
      asm.generateOffsetLoad(ALWAYS, LR, JTOC, target.getOffset());
    } else { // invoke via class's tib slot
      if (VM.VerifyAssertions) VM._assert(!target.isStatic());
      asm.generateOffsetLoad(ALWAYS, T0, JTOC, target.getDeclaringClass().getTibOffset());
      asm.generateOffsetLoad(ALWAYS, LR, T0, target.getOffset());
    }
    genMoveParametersToRegisters(true, methodRef);
    asm.emitBLX(ALWAYS, LR);
    genPopParametersAndPushReturnValue(true, methodRef);
  }

  @Override
  protected void emit_unresolved_invokespecial(MethodReference methodRef) {
    // must be a static method; if it was a super then declaring class _must_ be resolved
    genDynamicLinkingSequence(T0, methodRef); // T0 = method offset  (clobbers T0-T3, LR)
    asm.emitLDR(ALWAYS, LR, JTOC, T0);
    genMoveParametersToRegisters(true, methodRef);
    asm.emitBLX(ALWAYS, LR);
    genPopParametersAndPushReturnValue(true, methodRef);
  }

  @Override
  protected void emit_unresolved_invokestatic(MethodReference methodRef) {
    genDynamicLinkingSequence(T0, methodRef); // T0 = method offset  (clobbers T0-T3, LR)
    asm.emitLDR(ALWAYS, LR, JTOC, T0);
    genMoveParametersToRegisters(false, methodRef);
    asm.emitBLX(ALWAYS, LR);
    genPopParametersAndPushReturnValue(false, methodRef);
  }

  @Override
  protected void emit_resolved_invokestatic(MethodReference methodRef) {
    Offset methodOffset = methodRef.peekResolvedMethod().getOffset();
    asm.generateOffsetLoad(ALWAYS, LR, JTOC, methodOffset);
    genMoveParametersToRegisters(false, methodRef);
    asm.emitBLX(ALWAYS, LR);
    genPopParametersAndPushReturnValue(false, methodRef);
  }

  @Override
  protected void emit_invokeinterface(MethodReference methodRef) {
    int thisPointerOffset = methodRef.getParameterWords() << LOG_BYTES_IN_INT; // Return value doesn't include "this" pointer
    RVMMethod resolvedMethod = methodRef.peekInterfaceMethod(); // Null if can't resolve at compile time

    // (1) Emit dynamic type checking sequence if required to
    // do so inline.
    if (VM.BuildForIMTInterfaceInvocation) {
      if (methodRef.isMiranda()) {
        // TODO: It's not entirely clear that we can just assume that
        //       the class actually implements the interface.
        //       However, we don't know what interface we need to be checking
        //       so there doesn't appear to be much else we can do here.
      } else {
        if (resolvedMethod == null) {
          // Can't successfully resolve it at compile time.
          // Call uncommon case typechecking routine to do the right thing when this code actually executes.
          asm.generateOffsetLoad(ALWAYS, LR, JTOC, Entrypoints.unresolvedInvokeinterfaceImplementsTestMethod.getOffset());
          asm.generateImmediateLoad(ALWAYS, T0, methodRef.getId());  // T0 = id of method reference we are trying to call
          asm.emitLDRimm(ALWAYS, T1, SP, thisPointerOffset); // T1 = object reference (peek at "this" pointer on stack)
          asm.emitBLX(ALWAYS, LR);      // Call method with parameters T0, T1 (throws exception, if link error)
        } else {
          RVMClass interfaceClass = resolvedMethod.getDeclaringClass();
          int interfaceIndex = interfaceClass.getDoesImplementIndex();
          int interfaceMask = interfaceClass.getDoesImplementBitMask();

          asm.emitLDRimm(ALWAYS, T0, SP, thisPointerOffset); // T0 = object reference (peek at "this" pointer on stack)
          asm.baselineEmitLoadTIB(T0, T0);                   // T0 = TIB of "this" object
          asm.emitLDRimm(ALWAYS, T0, T0, TIB_DOES_IMPLEMENT_INDEX << LOG_BYTES_IN_ADDRESS); // T0 = "implements" bit vector

          if (DynamicTypeCheck.MIN_DOES_IMPLEMENT_SIZE <= interfaceIndex) {
            // must do arraybounds check of implements bit vector
            asm.emitLDRimm(ALWAYS, T1, T0, ObjectModel.getArrayLengthOffset().toInt()); // T1 = array length
            asm.emitMOVimm(ALWAYS, T2, interfaceIndex);                         // T2 = index of array element that contains this interface's bit
            asm.emitCMP(ALWAYS, T2, T1);
            asm.generateInterfaceImplementCheck(GE); // Will throw MUST_IMPLEMENT_INTERFACE trap if T2 >= T1 (out of bounds)
          }

          // Test the appropriate bit to see if the interface is implemented
          asm.emitLDRimm(ALWAYS, T1, T0, interfaceIndex << LOG_BYTES_IN_INT); // T1 = array element that contains the bit
          asm.generateCheckBitsSet(ALWAYS, T1, interfaceMask); // ANDs T1 with interfaceMask and sets condition flags
          asm.generateInterfaceImplementCheck(EQ); // Will throw MUST_IMPLEMENT_INTERFACE trap if (T1 & interfaceMask) == 0
        }
      }
    }
    // (2) Emit interface invocation sequence.
    if (VM.BuildForIMTInterfaceInvocation) {
      InterfaceMethodSignature sig = InterfaceMethodSignature.findOrCreate(methodRef);
      asm.emitLDRimm(ALWAYS, T0, SP, thisPointerOffset);          // T0 = object reference (peek at "this" pointer on stack)
      asm.baselineEmitLoadTIB(LR, T0);                            // LR = TIB
      asm.emitLDRimm(ALWAYS, LR, LR, TIB_INTERFACE_DISPATCH_TABLE_INDEX << LOG_BYTES_IN_ADDRESS); // LR = IMT base
      asm.generateOffsetLoad(ALWAYS, LR, LR, sig.getIMTOffset()); // LR = method address
      genMoveParametersToRegisters(true, methodRef);
      asm.generateImmediateLoad(ALWAYS, R12, sig.getId()); // Pass a "hidden" parameter to the interface resolver (see InterfaceMethodConflictResolver.java)
      asm.emitBLX(ALWAYS, LR);                             // Use R12 because all other registers are potentially taken :(
    } else {
      int itableIndex = -1;
      if (VM.BuildForITableInterfaceInvocation && resolvedMethod != null) {
        // get the index of the method in the Itable
        itableIndex =
            InterfaceInvocation.getITableIndex(resolvedMethod.getDeclaringClass(),
                                                  methodRef.getName(),
                                                  methodRef.getDescriptor());
      }
      if (itableIndex == -1) {
        // itable index is not known at compile-time.
        // call "invokeInterface" to resolve object + method id into method address
        asm.generateOffsetLoad(ALWAYS, LR, JTOC, Entrypoints.invokeInterfaceMethod.getOffset());
        asm.emitLDRimm(ALWAYS, T0, SP, thisPointerOffset); // T0 = object reference (peek at "this" pointer on stack)
        asm.generateImmediateLoad(ALWAYS, T1, methodRef.getId());  // T1 = method id
        asm.emitBLX(ALWAYS, LR);      // Takes parameters in T0, T1; Returns T0 = resolved method address
        asm.emitMOV(ALWAYS, LR, T0);
        genMoveParametersToRegisters(true, methodRef);
        asm.emitBLX(ALWAYS, LR);
      } else {
        // itable index is known at compile-time.
        // call "findITable" to resolve object + interface id into itable address
        asm.generateOffsetLoad(ALWAYS, LR, JTOC, Entrypoints.findItableMethod.getOffset());
        asm.emitLDRimm(ALWAYS, T0, SP, thisPointerOffset); // T0 = object reference (peek at "this" pointer on stack)
        asm.baselineEmitLoadTIB(T0, T0);                   // T0 = TIB
        asm.generateImmediateLoad(ALWAYS, T1, resolvedMethod.getDeclaringClass().getInterfaceId()); // T1 = interface id
        asm.emitBLX(ALWAYS, LR);      // Takes parameters in T0, T1; Returns T0 = itable reference
        asm.generateOffsetLoad(ALWAYS, LR, T0, Offset.fromIntSignExtend(itableIndex << LOG_BYTES_IN_ADDRESS)); // LR = the method to call
        genMoveParametersToRegisters(true, methodRef);
        asm.emitBLX(ALWAYS, LR);
      }
    }
    genPopParametersAndPushReturnValue(true, methodRef);
  }

  /*
   * other object model functions
   */

  @Override
  protected void emit_resolved_new(RVMClass typeRef) {
    int instanceSize = typeRef.getInstanceSize();
    Offset tibOffset = typeRef.getTibOffset();
    int whichAllocator = MemoryManager.pickAllocator(typeRef, method);
    int align = ObjectModel.getAlignment(typeRef);
    int offset = ObjectModel.getOffsetForAlignment(typeRef, false);
    int site = MemoryManager.getAllocationSite(true);
    asm.generateOffsetLoad(ALWAYS, LR, JTOC, Entrypoints.resolvedNewScalarMethod.getOffset());

    // On-stack arguments
    asm.generateImmediateLoad(ALWAYS, T0, align);
    asm.generateImmediateLoad(ALWAYS, T1, offset);
    asm.generateImmediateLoad(ALWAYS, T2, site);
    asm.emitPUSH(ALWAYS, T2); // TODO: could use PUSH multiple instruction
    asm.emitPUSH(ALWAYS, T1);
    asm.emitPUSH(ALWAYS, T0);

    // Register arguments
    asm.generateImmediateLoad(ALWAYS, T0, instanceSize);
    asm.generateOffsetLoad(ALWAYS, T1, JTOC, tibOffset);
    asm.emitMOVimm(ALWAYS, T2, typeRef.hasFinalizer() ? 1 : 0);
    asm.generateImmediateLoad(ALWAYS, T3, whichAllocator);

    asm.emitBLX(ALWAYS, LR);  // Expects 4 arguments in registers and 3 on the stack, returns value in T0
    asm.emitPUSH(ALWAYS, T0);
  }

  @Override
  protected void emit_unresolved_new(TypeReference typeRef) {
    int site = MemoryManager.getAllocationSite(true);
    asm.generateOffsetLoad(ALWAYS, LR, JTOC, Entrypoints.unresolvedNewScalarMethod.getOffset());
    asm.generateImmediateLoad(ALWAYS, T0, typeRef.getId());
    asm.generateImmediateLoad(ALWAYS, T1, site);
    asm.emitBLX(ALWAYS, LR);      // Takes parameters in T0, T1; returns address in T0
    asm.emitPUSH(ALWAYS, T0);
  }

  @Override
  protected void emit_resolved_newarray(RVMArray array) {
    int width = array.getLogElementSize();
    Offset tibOffset = array.getTibOffset();
    int headerSize = ObjectModel.computeArrayHeaderSize(array);
    int whichAllocator = MemoryManager.pickAllocator(array, method);
    int site = MemoryManager.getAllocationSite(true);
    int align = ObjectModel.getAlignment(array);
    int offset = ObjectModel.getOffsetForAlignment(array, false);
    asm.generateOffsetLoad(ALWAYS, LR, JTOC, Entrypoints.resolvedNewArrayMethod.getOffset());

    asm.emitPOP(ALWAYS, T0); // Grab the parameter (which needs to be passed on)

    // On-stack arguments
    asm.generateImmediateLoad(ALWAYS, T1, whichAllocator);
    asm.generateImmediateLoad(ALWAYS, T2, align);
    asm.generateImmediateLoad(ALWAYS, T3, offset);
    asm.generateImmediateLoad(ALWAYS, R12, site);
    asm.emitPUSH(ALWAYS, R12); // TODO: could use PUSH multiple instruction
    asm.emitPUSH(ALWAYS, T3);
    asm.emitPUSH(ALWAYS, T2);
    asm.emitPUSH(ALWAYS, T1);

    // Register arguments
    asm.emitMOVimm(ALWAYS, T1, width);
    asm.emitMOVimm(ALWAYS, T2, headerSize);
    asm.generateOffsetLoad(ALWAYS, T3, JTOC, tibOffset);

    asm.emitBLX(ALWAYS, LR);  // Expects 4 arguments in registers and 4 on the stack, returns value in T0
    asm.emitPUSH(ALWAYS, T0);
  }

  @Override
  protected void emit_unresolved_newarray(TypeReference typeRef) {
    int site = MemoryManager.getAllocationSite(true);
    asm.generateOffsetLoad(ALWAYS, LR, JTOC, Entrypoints.unresolvedNewArrayMethod.getOffset());
    asm.emitPOP(ALWAYS, T0); // T0 = number of elements
    asm.generateImmediateLoad(ALWAYS, T1, typeRef.getId());
    asm.generateImmediateLoad(ALWAYS, T2, site);
    asm.emitBLX(ALWAYS, LR);      // Takes parameters in T0, T1, T2; returns address in T0
    asm.emitPUSH(ALWAYS, T0);
  }

  @Override
  protected void emit_multianewarray(TypeReference typeRef, int dimensions) {
    asm.generateOffsetLoad(ALWAYS, LR, JTOC, ArchEntrypoints.newArrayArrayMethod.getOffset());
    asm.generateImmediateLoad(ALWAYS, T0, method.getId());
    asm.generateImmediateLoad(ALWAYS, T1, dimensions);
    asm.generateImmediateLoad(ALWAYS, T2, typeRef.getId());
    asm.emitMOV(ALWAYS, T3, SP);  // Function needs stack pointer so it can read the array sizes
    asm.emitBLX(ALWAYS, LR);      // Takes parameters in T0, T1, T2, T3; Reads (but does not pop) values on the stack; returns address in T0
    asm.emitADDimm(ALWAYS, SP, SP, dimensions << LOG_BYTES_IN_INT); // Pop array sizes off the stack
    asm.emitPUSH(ALWAYS, T0);
  }

  @Override
  protected void emit_arraylength() {
    asm.emitPOP(ALWAYS, T0);
    asm.emitLDRimm(ALWAYS, T0, T0, ObjectModel.getArrayLengthOffset().toInt());
    asm.emitPUSH(ALWAYS, T0);
  }

  @Override
  protected void emit_athrow() {
    asm.generateOffsetLoad(ALWAYS, LR, JTOC, Entrypoints.athrowMethod.getOffset());
    asm.emitPOP(ALWAYS, T0);      // athrow() expects argument in T0
    asm.emitBLX(ALWAYS, LR);
  }

  @Override
  protected void emit_checkcast(TypeReference typeRef) {
    asm.generateOffsetLoad(ALWAYS, LR, JTOC, Entrypoints.checkcastMethod.getOffset());
    asm.emitLDRimm(ALWAYS, T0, SP, 0); // load the object being checked (keeping it on the stack)
    asm.generateImmediateLoad(ALWAYS, T1, typeRef.getId()); // checkcast() expects argument in T1
    asm.emitBLX(ALWAYS, LR);
  }

  @Override
  protected void emit_checkcast_resolvedInterface(RVMClass type) {
    int interfaceIndex = type.getDoesImplementIndex();
    int interfaceMask = type.getDoesImplementBitMask();

    asm.emitLDRimm(ALWAYS, T0, SP, 0); // load the object being checked (keeping it on the stack)
    asm.emitCMPimm(ALWAYS, T0, 0);     // check for null
    ForwardReference isNull = asm.generateForwardBranch(EQ); // Skip everything else if null

    asm.baselineEmitLoadTIB(T0, T0);                         // T0 = TIB
    asm.emitLDRimm(ALWAYS, T0, T0, TIB_DOES_IMPLEMENT_INDEX << LOG_BYTES_IN_ADDRESS); // T0 = "implements" bit vector

    if (DynamicTypeCheck.MIN_DOES_IMPLEMENT_SIZE <= interfaceIndex) {
      // must do arraybounds check of implements bit vector
      asm.emitLDRimm(ALWAYS, T1, T0, ObjectModel.getArrayLengthOffset().toInt()); // T1 = array length
      asm.emitMOVimm(ALWAYS, T2, interfaceIndex);                                 // T2 = index of array element that contains this interface's bit
      asm.emitCMP(ALWAYS, T2, T1);
      asm.generateCheckCast(GE); // Will throw CHECKAST trap if T2 >= T1 (out of bounds)
    }

    // Test the appropriate bit to see if the interface is implemented
    asm.emitLDRimm(ALWAYS, T1, T0, interfaceIndex << LOG_BYTES_IN_INT); // T1 = array element that contains the bit
    asm.generateCheckBitsSet(ALWAYS, T1, interfaceMask); // ANDs T1 with interfaceMask and sets condition flags
    asm.generateCheckCast(EQ);                           // Will throw CHECKCAST trap if (T1 & interfaceMask) == 0

    isNull.resolve(asm);
  }

  @Override
  protected void emit_checkcast_resolvedClass(RVMClass type) {
    int LHSDepth = type.getTypeDepth();
    int LHSId = type.getId();

    asm.emitLDRimm(ALWAYS, T0, SP, 0); // load the object being checked (keeping it on the stack)
    asm.emitCMPimm(ALWAYS, T0, 0);     // check for null
    ForwardReference isNull = asm.generateForwardBranch(EQ); // Skip everything else if null

    asm.baselineEmitLoadTIB(T0, T0);                                                  // T0 = TIB
    asm.emitLDRimm(ALWAYS, T0, T0, TIB_SUPERCLASS_IDS_INDEX << LOG_BYTES_IN_ADDRESS); // T0 = superclass display

    if (DynamicTypeCheck.MIN_SUPERCLASS_IDS_SIZE <= LHSDepth) {
      // must do arraybounds check of superclass display
      asm.emitLDRimm(ALWAYS, T1, T0, ObjectModel.getArrayLengthOffset().toInt()); // T1 = array length
      asm.emitMOVimm(ALWAYS, T2, LHSDepth);                                       // T2 = index of array element that contains the class's id
      asm.emitCMP(ALWAYS, T2, T1);
      asm.generateCheckCast(GE); // Will throw CHECKAST trap if T2 >= T1 (out of bounds)
    }

    // Compare the id's to see if the cast succeeds
    asm.emitLDRHimm(ALWAYS, T0, T0, LHSDepth << LOG_BYTES_IN_CHAR); // T0 = the id
    asm.generateImmediateCompare(ALWAYS, T0, LHSId); // Compare T0 against LHSId
    asm.generateCheckCast(NE);                       // Will throw CHECKCAST trap if the id's don't match

    isNull.resolve(asm);
  }

  @Override
  protected void emit_checkcast_final(RVMType type) {
    asm.emitLDRimm(ALWAYS, T0, SP, 0); // load the object being checked (keeping it on the stack)
    asm.emitCMPimm(ALWAYS, T0, 0);     // check for null
    ForwardReference isNull = asm.generateForwardBranch(EQ); // Skip everything else if null

    asm.baselineEmitLoadTIB(T0, T0);                               // TIB of the object
    asm.generateOffsetLoad(ALWAYS, T1, JTOC, type.getTibOffset()); // TIB of LHS type
    asm.emitCMP(ALWAYS, T0, T1); // TIBs equal?
    asm.generateCheckCast(NE);   // Will throw CHECKCAST trap if (T1 =! T0)
    isNull.resolve(asm);
  }

  @Override
  protected void emit_instanceof(TypeReference typeRef) {
    asm.generateOffsetLoad(ALWAYS, LR, JTOC, Entrypoints.instanceOfMethod.getOffset());
    asm.emitPOP(ALWAYS, T0);                                // instanceOf() expects argument in T0
    asm.generateImmediateLoad(ALWAYS, T1, typeRef.getId()); // instanceOf() expects argument in T1
    asm.emitBLX(ALWAYS, LR);
    asm.emitPUSH(ALWAYS, T0);                               // Return value in T0
  }

  @Override
  protected void emit_instanceof_resolvedInterface(RVMClass type) {
    int interfaceIndex = type.getDoesImplementIndex();
    int interfaceMask = type.getDoesImplementBitMask();

    asm.emitPOP(ALWAYS, T0);          // load the object being checked
    asm.emitCMPimm(ALWAYS, T0, 0);    // check for null
    ForwardReference isNull = asm.generateForwardBranch(EQ); // Skip everything else if null (T0 = 0 so can push it as the result)

    asm.baselineEmitLoadTIB(T0, T0);                                                  // T0 = TIB
    asm.emitLDRimm(ALWAYS, T0, T0, TIB_DOES_IMPLEMENT_INDEX << LOG_BYTES_IN_ADDRESS); // T0 = "implements" bit vector

    ForwardReference outOfBounds = null;
    if (DynamicTypeCheck.MIN_DOES_IMPLEMENT_SIZE <= interfaceIndex) {
      // must do arraybounds check of implements bit vector
      asm.emitLDRimm(ALWAYS, T1, T0, ObjectModel.getArrayLengthOffset().toInt()); // T1 = array length
      asm.emitMOVimm(ALWAYS, T2, interfaceIndex);                         // T2 = index of array element that contains this interface's bit
      asm.emitCMP(ALWAYS, T2, T1);
      asm.emitMOVimm(GE, T0, 0); // Result zero if T2 >= T1 (out of bounds)
      outOfBounds = asm.generateForwardBranch(GE); // Skip everything else if out of bounds
    }

    // Test the appropriate bit to see if the interface is implemented
    asm.emitLDRimm(ALWAYS, T1, T0, interfaceIndex << LOG_BYTES_IN_INT); // T1 = array element that contains the bit
    asm.generateCheckBitsSet(ALWAYS, T1, interfaceMask); // ANDs T1 with interfaceMask and sets condition flags
    asm.emitMOVimm(EQ, T0, 0); // Write zero as the result if (T1 & interfaceMask) == 0
    asm.emitMOVimm(NE, T0, 1); // Otherwise, the instanceof succeeds and writes 1

    isNull.resolve(asm);
    if (outOfBounds != null) outOfBounds.resolve(asm);

    asm.emitPUSH(ALWAYS, T0);
  }

  @Override
  protected void emit_instanceof_resolvedClass(RVMClass type) {
    int LHSDepth = type.getTypeDepth();
    int LHSId = type.getId();

    asm.emitPOP(ALWAYS, T0);          // load the object being checked
    asm.emitCMPimm(ALWAYS, T0, 0);    // check for null
    ForwardReference isNull = asm.generateForwardBranch(EQ); // Skip everything else if null (T0 = 0 so can push it as the result)

    asm.baselineEmitLoadTIB(T0, T0);                                                  // T0 = TIB
    asm.emitLDRimm(ALWAYS, T0, T0, TIB_SUPERCLASS_IDS_INDEX << LOG_BYTES_IN_ADDRESS); // T0 = superclass display

    ForwardReference outOfBounds = null;
    if (DynamicTypeCheck.MIN_SUPERCLASS_IDS_SIZE <= LHSDepth) {
      // must do arraybounds check of superclass display
      asm.emitLDRimm(ALWAYS, T1, T0, ObjectModel.getArrayLengthOffset().toInt()); // T1 = array length
      asm.emitMOVimm(ALWAYS, T2, LHSDepth);                         // T2 = index of array element that contains the class's id
      asm.emitCMP(ALWAYS, T2, T1);
      asm.emitMOVimm(GE, T0, 0); // Result zero if T2 >= T1 (out of bounds)
      outOfBounds = asm.generateForwardBranch(GE); // Skip everything else if out of bounds
    }

    // Compare the id's to see if instanceof succeeds
    asm.emitLDRHimm(ALWAYS, T0, T0, LHSDepth << LOG_BYTES_IN_CHAR); // T0 = the id
    asm.generateImmediateCompare(ALWAYS, T0, LHSId); // Compare T0 against LHSId
    asm.emitMOVimm(NE, T0, 0); // Write zero as the result if the id's don't match
    asm.emitMOVimm(EQ, T0, 1); // Otherwise, the instanceof succeeds and writes 1

    isNull.resolve(asm);
    if (outOfBounds != null) outOfBounds.resolve(asm);

    asm.emitPUSH(ALWAYS, T0);
  }

  @Override
  protected void emit_instanceof_final(RVMType type) {
    asm.emitPOP(ALWAYS, T0);          // load the object being checked
    asm.emitCMPimm(ALWAYS, T0, 0);    // check for null
    ForwardReference isNull = asm.generateForwardBranch(EQ); // Skip everything else if null (T0 = 0 so can push it as the result)

    asm.baselineEmitLoadTIB(T0, T0);                               // TIB of the object
    asm.generateOffsetLoad(ALWAYS, T1, JTOC, type.getTibOffset()); // TIB of LHS type
    asm.emitCMP(ALWAYS, T0, T1);                           // TIBs equal?
    asm.emitMOVimm(NE, T0, 0); // Write zero as the result if (T0 != T1)
    asm.emitMOVimm(EQ, T0, 1); // Otherwise, the instanceof succeeds and writes 1

    isNull.resolve(asm);
    asm.emitPUSH(ALWAYS, T0);
  }

  @Override
  protected void emit_monitorenter() {
    asm.generateOffsetLoad(ALWAYS, LR, JTOC, Entrypoints.lockMethod.getOffset());
    asm.emitPOP(ALWAYS, T0);   // genericLock() expects argument in T0
    asm.generateNullCheck(T0);
    asm.emitBLX(ALWAYS, LR);
  }

  @Override
  protected void emit_monitorexit() {
    asm.generateOffsetLoad(ALWAYS, LR, JTOC, Entrypoints.unlockMethod.getOffset());
    asm.emitPOP(ALWAYS, T0);   // genericUnlock() expects argument in T0
    asm.generateNullCheck(T0);
    asm.emitBLX(ALWAYS, LR);
  }

  // Places resolved member address in reg
  // Clobbers T0-T3, LR (but reg can be any of these)
  private void genDynamicLinkingSequence(GPR reg, MemberReference ref) {
    if (VM.VerifyAssertions) VM._assert(reg != R12);
    int memberId = ref.getId();
    Offset memberOffset = Offset.fromIntZeroExtend(memberId << LOG_BYTES_IN_INT);
    Offset tableOffset = Entrypoints.memberOffsetsField.getOffset();
    Offset resolverOffset = Entrypoints.resolveMemberMethod.getOffset();

    // load offset table
    asm.generateOffsetLoad(ALWAYS, T0, JTOC, tableOffset);
    asm.generateOffsetLoad(ALWAYS, T0, T0, memberOffset);

    asm.generateImmediateCompare(ALWAYS, T0, NEEDS_DYNAMIC_LINK); // If reg == NEEDS_DYNAMIC_LINK, call resolver
    asm.generateOffsetLoad(EQ, LR, JTOC, resolverOffset);
    asm.generateImmediateLoad(EQ, T0, memberId);   // id of member we are resolving
    asm.emitBLX(EQ, LR);          // Takes parameter in T0; will throw exception if link error; Stores the result in the table and also returns it in T0
    if (reg != T0)
      asm.emitMOV(ALWAYS, reg, T0);
  }

  // Emit code to buy a stackframe, store incoming parameters,
  // and acquire method synchronization lock.
  // TODO: push-multiple instruction
  private void genPrologue() {
    if (klass.hasBridgeFromNativeAnnotation()) {
      // Methods defined in JNIFunctions.java which can be called from C code:
      // Generate code to convert from C convention to Jikes convention
      JNICompiler.generatePrologueForJNIMethod(asm, method);
    }

    // Generate trap if new frame would cross guard page.
    if (isInterruptible) {
      asm.generateStackOverflowCheck(SAVED_REGISTER_BYTES + LOCALS_BYTES); // clobbers R12 only
    }

    // If this is a "dynamic bridge" method, then save all registers except the constants (TR, JTOC) and the scratch (R12).
    if (klass.hasDynamicBridgeAnnotation()) {
      asm.generateImmediateLoad(ALWAYS, R12, compiledMethod.getId()); // compiled method id

      asm.emitPUSH(ALWAYS, LR);
      asm.emitPUSH(ALWAYS, R12);
      asm.emitPUSH(ALWAYS, FP);

      for (int i = LAST_LOCAL_GPR.value(); i >= FIRST_LOCAL_GPR.value(); i--) {
        asm.emitPUSH(ALWAYS, GPR.lookup(i));
      }

      if (VM.VerifyAssertions) VM._assert(LAST_VOLATILE_GPR.value() < FIRST_LOCAL_GPR.value());

      for (int i = LAST_VOLATILE_GPR.value(); i >= FIRST_VOLATILE_GPR.value(); i--) {
        asm.emitPUSH(ALWAYS, GPR.lookup(i));
      }

      if (VM.VerifyAssertions) VM._assert((LAST_VOLATILE_DPR.value() & 1) == 0 && (LAST_LOCAL_DPR.value() & 1) == 0);   // Should be even
      if (VM.VerifyAssertions) VM._assert((FIRST_VOLATILE_DPR.value() & 1) == 0 && (FIRST_LOCAL_DPR.value() & 1) == 0);

      for (int i = LAST_LOCAL_DPR.value(); i >= FIRST_LOCAL_DPR.value(); i -= 2) {
        asm.emitVPUSH64(ALWAYS, DPR.lookup(i));
      }

      if (VM.VerifyAssertions) VM._assert(LAST_VOLATILE_DPR.value() < FIRST_LOCAL_DPR.value());

      for (int i = LAST_VOLATILE_DPR.value(); i >= FIRST_VOLATILE_DPR.value(); i -= 2) {
        asm.emitVPUSH64(ALWAYS, DPR.lookup(i));
      }

      asm.generateImmediateSubtract(ALWAYS, FP, SP, STACKFRAME_SAVED_REGISTER_OFFSET.toInt() - SAVED_REGISTER_BYTES); // Set frame pointer

    } else {
      asm.generateImmediateLoad(ALWAYS, R12, compiledMethod.getId()); // compiled method id

      asm.emitPUSH(ALWAYS, LR);
      asm.emitPUSH(ALWAYS, R12);
      asm.emitPUSH(ALWAYS, FP);
      if (VM.VerifyAssertions) VM._assert(STACKFRAME_FRAME_POINTER_OFFSET.toInt() == 0);

      // save non-volatile registers.

      for (int i = (SAVE_ALL_REGS ? LAST_LOCAL_GPR.value() : nextGPR - 1); i >= FIRST_LOCAL_GPR.value(); i--) {
        asm.emitPUSH(ALWAYS, GPR.lookup(i));
      }

      if (VM.VerifyAssertions) VM._assert((nextFPR & 1) == 0 && (LAST_LOCAL_DPR.value() & 1) == 0);

      for (int i = (SAVE_ALL_REGS ? LAST_LOCAL_DPR.value() : nextFPR - 2); i >= FIRST_LOCAL_DPR.value(); i -= 2) {
        asm.emitVPUSH64(ALWAYS, DPR.lookup(i));
      }

      asm.generateImmediateSubtract(ALWAYS, FP, SP, STACKFRAME_SAVED_REGISTER_OFFSET.toInt() - SAVED_REGISTER_BYTES); // Set frame pointer
    }

    if (VM.VerifyAssertions) VM._assert((paramStackBytes & 0x3) == 0); // paramStackBytes % 4 == 0; i.e. word-aligned
    if (VM.VerifyAssertions) VM._assert(LOCALS_BYTES >= 0);

    if (LOCALS_BYTES > 0)
      asm.emitSUBimm(ALWAYS, SP, SP, LOCALS_BYTES); // Reserve space for local variables

    // Setup locals.
    genMoveParametersToLocals();

    // Perform a thread switch if so requested.
    // defer generating prologues which may trigger GC, see emit_deferred_prologue
    if (method.isForOsrSpecialization()) {
      return;
    }

    genThreadSwitchTest(RVMThread.PROLOGUE); // (BaselineExceptionDeliverer WONT release the lock (for synchronized methods) during prologue code)

    // Acquire method syncronization lock.  (BaselineExceptionDeliverer will release the lock (for synchronized methods) after prologue code)
    if (method.isSynchronized()) {
      genSynchronizedMethodPrologue();
    }
  }

  @Override
  protected void emit_deferred_prologue() {
    if (VM.VerifyAssertions) VM._assert(method.isForOsrSpecialization());
    genThreadSwitchTest(RVMThread.PROLOGUE);

    // No sync prologue for OSR method
  }

  // Emit code to acquire method synchronization lock.
  private void genSynchronizedMethodPrologue() {
    if (method.isStatic()) { // Lock using java.lang.Class object
      asm.generateOffsetLoad(ALWAYS, T0, JTOC, Offset.fromIntSignExtend(Statics.findOrCreateObjectLiteral(klass.getClassForType())));
    } else {                 // Lock using "this" pointer
      short loc = localGeneralLocations[0];
      if (VM.VerifyAssertions) VM._assert(isRegister(loc)); // TODO: what if using noregisters?
      asm.emitMOV(ALWAYS, T0, GPR.lookup(loc));
    }
    asm.generateOffsetLoad(ALWAYS, LR, JTOC, Entrypoints.lockMethod.getOffset()); // call method with parameter in T0
    asm.emitBLX(ALWAYS, LR);

    // Calculate index of instruction after which method has the monitor - used by exception deliverer to decide whether the lock needs to be released
    lockOffset = BYTES_IN_INT * (asm.getMachineCodeIndex() - 1); // TODO: PPC uses -1 and times 4, intel uses plain value ...
  }

  // Emit code to release method synchronization lock.
  private void genSynchronizedMethodEpilogue() {
    if (method.isStatic()) { // Lock using java.lang.Class object
      asm.generateOffsetLoad(ALWAYS, T0, JTOC, Offset.fromIntSignExtend(Statics.findOrCreateObjectLiteral(klass.getClassForType())));
    } else {                 // Lock using "this" pointer
      short loc = localGeneralLocations[0];
      if (VM.VerifyAssertions) VM._assert(isRegister(loc)); // TODO: what if using noregisters?
      asm.emitMOV(ALWAYS, T0, GPR.lookup(loc));
    }
    asm.generateOffsetLoad(ALWAYS, LR, JTOC, Entrypoints.unlockMethod.getOffset()); // call method with parameter in T0
    asm.emitBLX(ALWAYS, LR);
  }

  // Emit code to discard stackframe and return to caller.
  // TODO: pop-multiple instruction, return by popping PC
  private void genEpilogue() {
    if (klass.hasDynamicBridgeAnnotation()) {
      // we never return from a DynamicBridge frame
      // see implementation of Magic.dynamicBridgeTo in genMagic() below
      asm.generateUndefined();
    } else {
      asm.generateImmediateAdd(ALWAYS, SP, FP, STACKFRAME_SAVED_REGISTER_OFFSET.toInt() - SAVED_REGISTER_BYTES); // Return to the start of our stackframe

      // restore non-volatile registers.

      if (VM.VerifyAssertions) VM._assert((nextFPR & 1) == 0 && (LAST_LOCAL_DPR.value() & 1) == 0);

      for (int i = FIRST_LOCAL_DPR.value(); i <= (SAVE_ALL_REGS ? LAST_LOCAL_DPR.value() : nextFPR - 2); i += 2) {
        asm.emitVPOP64(ALWAYS, DPR.lookup(i));
      }

      for (int i = FIRST_LOCAL_GPR.value(); i <= (SAVE_ALL_REGS ? LAST_LOCAL_GPR.value() : nextGPR - 1); i++) {
        asm.emitPOP(ALWAYS, GPR.lookup(i));
      }

      asm.emitPOP(ALWAYS, FP);
      asm.emitPOP(ALWAYS, R12); // Discard compiled method id
      asm.emitPOP(ALWAYS, LR);

      if (VM.VerifyAssertions) VM._assert((paramStackBytes & 0x3) == 0); // paramStackBytes % 4 == 0; i.e. word-aligned
      if (VM.VerifyAssertions) VM._assert(paramStackBytes >= 0);

      if (paramStackBytes > 0)  // Pop on-stack parameters
        asm.emitADDimm(ALWAYS, SP, SP, paramStackBytes);

      if (klass.hasBridgeFromNativeAnnotation()) {
        // Generate code to return to C function
        JNICompiler.generateEpilogueForJNIMethod(asm, method);
      } else {
        asm.emitBX(ALWAYS, LR); // return
      }
    }
  }

  /**
   * Emit the code to load the counter array into the given register.
   * May call a read barrier so will kill all temporaries.
   *
   * @param reg The register to hold the counter array.
   */
  private void genLoadCounterArray(GPR reg) {
    if (NEEDS_OBJECT_ALOAD_BARRIER) {
      asm.generateOffsetLoad(ALWAYS, T0, JTOC, Entrypoints.edgeCountersField.getOffset());
      asm.generateImmediateLoad(ALWAYS, T1, getEdgeCounterIndex());
      Barriers.compileArrayLoadBarrier(this); // Expects T0 = array ref, T1 = array index; returns value in T0
      if (reg != T0) {
        asm.emitMOV(ALWAYS, reg, T0);
      }
    } else {
      asm.generateOffsetLoad(ALWAYS, reg, JTOC, Entrypoints.edgeCountersField.getOffset());
      asm.generateOffsetLoad(ALWAYS, reg, reg, getEdgeCounterOffset());
    }
  }

  /**
   * Emit the code for a bytecode level conditional branch
   * Assumes the condition flags have been set
   * @param cc the condition code to branch on
   * @param bTarget the target bytecode index
   */
  private void genCondBranch(COND cond, int bTarget) {
    if (options.PROFILE_EDGE_COUNTERS) {
      int entry = edgeCounterIdx;
      edgeCounterIdx += 2;     // Allocate 2 counters, taken and not taken
      genLoadCounterArray(T0); // Load counter array for this method

      // Increment appropriate counter
      genIncEdgeCounter(cond,        T0, T1, entry + EdgeCounts.TAKEN);
      genIncEdgeCounter(cond.flip(), T0, T1, entry + EdgeCounts.NOT_TAKEN);
    }

    int mTarget = bytecodeMap[bTarget];                // mTarget will be the machine code address, if known; otherwise, zero
    asm.generateUnknownBranch(cond, mTarget, bTarget); // Need this macro because it might be a forward or backward branch
  }

  /**
   * increment an edge counter.
   * @param counters register containing base of counter array
   * @param counterIdx index of counter to increment
   */
  private void genIncEdgeCounter(COND cond, GPR counters, GPR scratch, int counterIdx) {
    asm.emitLDRimm(cond, scratch, counters, counterIdx << LOG_BYTES_IN_INT);
    asm.emitMOVimm(cond, R12, 1);
    asm.emitQADD(cond, scratch, scratch, R12); // Saturating add
    asm.emitSTRimm(cond, scratch, counters, counterIdx << LOG_BYTES_IN_INT);
  }

  /**
   * increment an edge counter using a register to choose the counter
   * @param counters register containing base of counter array
   * @param (immediate) base index of counter treated as "zero"
   * @param counterIdx register containining index to add to base (must not be modified)
   */
  private void genIncEdgeCounterReg(COND cond, GPR counters, GPR scratch, int base, GPR counterIdx) {
    asm.emitADDimm(cond, counterIdx, counterIdx, base);
    asm.emitLDRshift(cond, scratch, counters, counterIdx, LOG_BYTES_IN_INT); // Shift to get offset in bytes
    asm.emitMOVimm(cond, R12, 1);
    asm.emitQADD(cond, scratch, scratch, R12);         // Saturating add
    asm.emitSTRshift(cond, scratch, counters, counterIdx, LOG_BYTES_IN_INT);
    asm.emitSUBimm(cond, counterIdx, counterIdx, base);  // Restore counterIdx
  }

  /**
   * @param whereFrom is this thread switch from a PROLOGUE, BACKEDGE, or EPILOGUE?
   */
  private void genThreadSwitchTest(int whereFrom) {
    if (isInterruptible) {
      // yield if takeYieldpoint is non-zero.
      asm.generateOffsetLoad(ALWAYS, R12, TR, Entrypoints.takeYieldpointField.getOffset());
      asm.emitCMPimm(ALWAYS, R12, 0);
      if (whereFrom == RVMThread.PROLOGUE) {
        // Take yieldpoint if yieldpoint flag is non-zero (either 1 or -1)
        asm.generateOffsetLoad(NE, LR, JTOC, Entrypoints.yieldpointFromPrologueMethod.getOffset());
        asm.emitBLX(NE, LR);
      } else if (whereFrom == RVMThread.BACKEDGE) {
        // Take yieldpoint if yieldpoint flag is >0
        asm.generateOffsetLoad(GT, LR, JTOC, Entrypoints.yieldpointFromBackedgeMethod.getOffset());
        asm.emitBLX(GT, LR);
      } else { // EPILOGUE
        // Take yieldpoint if yieldpoint flag is non-zero (either 1 or -1)
        asm.generateOffsetLoad(NE, LR, JTOC, Entrypoints.yieldpointFromEpilogueMethod.getOffset());
        asm.emitBLX(NE, LR);
      }

      if (VM.BuildForAdaptiveSystem && options.INVOCATION_COUNTERS) { // TODO: rename
        if (VM.VerifyAssertions) VM._assert(VM.NOT_REACHED); // NOT IMPLEMENTED
      }
    }
  }

  // Store parameters from registers into local variables of current method.
  // (On-stack parameters will stay where they are)
  private void genMoveParametersToLocals() {
    TypeReference[] types = method.getParameterTypes();
    int localIndex = 0;
    int gp = FIRST_VOLATILE_GPR.value();
    int fp = FIRST_VOLATILE_FPR.value();
    short loc;

    if (!method.isStatic()) { // "this" pointer
      loc = localGeneralLocations[localIndex++];
      if (isRegister(loc))
        asm.emitMOV(ALWAYS, GPR.lookup(loc), GPR.lookup(gp++));
      else
        genStoreGeneralLocal(loc, GPR.lookup(gp++));
    }

    for (TypeReference t : types) {
      if (t.isLongType() || t.isDoubleType()) {
        if ((fp & 1) == 1) fp++; // 64-bit align
        if (fp <= LAST_VOLATILE_FPR.value() - 1) { // Leave non-register parameters where they are
          loc = localFloatLocations[localIndex];
          if (isRegister(loc))
            asm.emitVMOV64(ALWAYS, DPR.lookup(loc), DPR.lookup(fp));
          else
            genStoreDoubleLocal(loc, DPR.lookup(fp));
          fp += 2;
          localIndex += 2;
        } else {
           if (VM.VerifyAssertions) VM._assert(!isRegister(localFloatLocations[localIndex]));
           localIndex += 2;
        }
      } else if (t.isFloatType()) {
        if (fp <= LAST_VOLATILE_FPR.value()) { // Leave non-register parameters where they are
          loc = localFloatLocations[localIndex++];
          if (isRegister(loc))
            asm.emitVMOV32(ALWAYS, FPR.lookup(loc), FPR.lookup(fp++));
          else
            genStoreFloatLocal(loc, FPR.lookup(fp++));
        } else {
           if (VM.VerifyAssertions) VM._assert(!isRegister(localFloatLocations[localIndex]));
           localIndex++;
        }
      } else { // int-like or object
        if (gp <= LAST_VOLATILE_GPR.value()) { // Leave non-register parameters where they are
          loc = localGeneralLocations[localIndex++];
          if (isRegister(loc))
            asm.emitMOV(ALWAYS, GPR.lookup(loc), GPR.lookup(gp++));
          else
            genStoreGeneralLocal(loc, GPR.lookup(gp++));
        } else {
           if (VM.VerifyAssertions) VM._assert(!isRegister(localGeneralLocations[localIndex]));
           localIndex++;
        }
      }
    }

    if (VM.VerifyAssertions) VM._assert(gp <= LAST_VOLATILE_GPR.value() + 1);
    if (VM.VerifyAssertions) VM._assert(fp <= LAST_VOLATILE_FPR.value() + 1);
  }

  // Move parameters from the java convention to the ARM/jikesrvm convention
  private void genMoveParametersToRegisters(boolean hasImplicitThisArg, MethodReference m) {
    // At the start of the call, the SP points to the last parameter, and the first parameter is above it somewhere
    // At the end of it, we want the first few parameters to be in registers, and the rest to be in reverse order starting at the SP.
    // Do this in two passes. First, read all the register parameters in, and keep track of the rest.
    // Then, go through the rest in reverse order and move them to their position on the stack

    TypeReference[] types = m.getParameterTypes();

    int readOffset = m.getParameterWords() + (hasImplicitThisArg ? 1 : 0);
    int writeOffset = 0;
    int gp = FIRST_VOLATILE_GPR.value();
    int fp = FIRST_VOLATILE_FPR.value();

    int dpr_stack = 0;
    int fpr_stack = 0;
    int gpr_stack = 0;

    if (hasImplicitThisArg)
      asm.emitLDRimm(ALWAYS, GPR.lookup(gp++), SP, --readOffset << LOG_BYTES_IN_INT);

    for (TypeReference t : types) {
      if (t.isLongType() || t.isDoubleType()) {
        if ((fp & 1) == 1) fp++; // 64-bit align
        if (fp <= LAST_VOLATILE_FPR.value() - 1) {
          readOffset -= 2;
          asm.emitVLDR64(ALWAYS, DPR.lookup(fp), SP, readOffset << LOG_BYTES_IN_INT);
          fp += 2;
        } else {
          // This one goes on the stack
          dpr_stack++; readOffset -= 2;
        }
      } else if (t.isFloatType()) {
        if (fp <= LAST_VOLATILE_FPR.value()) {
          asm.emitVLDR32(ALWAYS, FPR.lookup(fp++), SP, --readOffset << LOG_BYTES_IN_INT);
        } else {
          // This one goes on the stack
          fpr_stack++; readOffset--;
        }
      } else { // int-like or object
        if (gp <= LAST_VOLATILE_GPR.value()) {
          asm.emitLDRimm(ALWAYS, GPR.lookup(gp++), SP, --readOffset << LOG_BYTES_IN_INT);
        } else {
          // This one goes on the stack
          gpr_stack++; readOffset--;
        }
      }
    }

    if (VM.VerifyAssertions) VM._assert(readOffset == 0);
    if (VM.VerifyAssertions) VM._assert(gp <= LAST_VOLATILE_GPR.value() + 1);
    if (VM.VerifyAssertions) VM._assert(fp <= LAST_VOLATILE_FPR.value() + 1);

    // Registers have been allocated, now go in reverse order
    for (int i = types.length - 1; i >= 0; i--) {
      TypeReference t = types[i];
      if (t.isLongType() || t.isDoubleType()) {
        if (dpr_stack > 0) {
          asm.emitLDRimm(ALWAYS, R12, SP, readOffset++ << LOG_BYTES_IN_INT);
          asm.emitSTRimm(ALWAYS, R12, SP, writeOffset - 2 << LOG_BYTES_IN_INT);

          asm.emitLDRimm(ALWAYS, R12, SP, readOffset++ << LOG_BYTES_IN_INT);
          asm.emitSTRimm(ALWAYS, R12, SP, writeOffset - 1 << LOG_BYTES_IN_INT);

          writeOffset -= 2; dpr_stack--;
        } else {
          readOffset += 2;
        }
      } else if (t.isFloatType()) {
        if (fpr_stack > 0) {
          asm.emitLDRimm(ALWAYS, R12, SP, readOffset++ << LOG_BYTES_IN_INT);
          asm.emitSTRimm(ALWAYS, R12, SP, --writeOffset << LOG_BYTES_IN_INT);
          fpr_stack--;
        } else {
          readOffset++;
        }
      } else { // int-like or object
        if (gpr_stack > 0) {
          asm.emitLDRimm(ALWAYS, R12, SP, readOffset++ << LOG_BYTES_IN_INT);
          asm.emitSTRimm(ALWAYS, R12, SP, --writeOffset << LOG_BYTES_IN_INT);
          gpr_stack--;
        } else {
          readOffset++;
        }
      }
    }

    if (VM.VerifyAssertions) VM._assert(writeOffset <= 0);

    if (writeOffset < 0)
      asm.generateImmediateAdd(ALWAYS, SP, SP, writeOffset << LOG_BYTES_IN_INT);

    if (VM.VerifyAssertions) VM._assert(dpr_stack == 0);
    if (VM.VerifyAssertions) VM._assert(fpr_stack == 0);
    if (VM.VerifyAssertions) VM._assert(gpr_stack == 0);
    if (VM.VerifyAssertions) VM._assert(readOffset == m.getParameterWords());
    if (VM.VerifyAssertions) VM._assert(writeOffset <= 0);
  }

  // push return value of method "m" from register to operand stack.
  private void genPopParametersAndPushReturnValue(boolean hasImplicitThisArg, MethodReference m) {

    int readOffset = m.getParameterWords() + (hasImplicitThisArg ? 1 : 0);

    if (VM.VerifyAssertions) VM._assert(readOffset >= 0);

    if (readOffset > 0)
      asm.emitADDimm(ALWAYS, SP, SP, readOffset << LOG_BYTES_IN_INT);

    TypeReference t = m.getReturnType();
    if (!t.isVoidType()) {
      if (t.isLongType() || t.isDoubleType()) {
        asm.emitVPUSH64(ALWAYS, F0and1);
      } else if (t.isFloatType()) {
        asm.emitVPUSH32(ALWAYS, F0);
      } else { // t is int-like or object
        asm.emitPUSH(ALWAYS, T0);
      }
    }
  }

  @Override
  protected void emit_loadretaddrconst(int bcIndex) {
    asm.generateLoadReturnAddress(bcIndex);
  }

  /**
   * Emit code to invoke a compiled method (with known jtoc offset).
   * Treat it like a resolved invoke static, but possibly pass a "this" pointer
   *
   * I have not thought about GCMaps for invoke_compiledmethod
   * TODO: Figure out what the above GCMaps comment means and fix it!
   */
  @Override
  protected void emit_invoke_compiledmethod(CompiledMethod cm) {
    Offset methodOffset = cm.getOsrJTOCoffset();
    asm.generateOffsetLoad(ALWAYS, LR, JTOC, methodOffset);
    boolean hasThisPointer = !cm.method.isStatic();
    MethodReference methodRef = cm.method.getMemberRef().asMethodReference();
    genMoveParametersToRegisters(hasThisPointer, methodRef);
    asm.emitBLX(ALWAYS, LR);
    genPopParametersAndPushReturnValue(hasThisPointer, methodRef);
  }

  @Override
  protected ForwardReference emit_pending_goto(int bTarget) {
    return asm.generateForwardBranch(ALWAYS);
  }

  //*************************************************************************
  //                             MAGIC
  //*************************************************************************

  /*
   *  Generate inline machine instructions for special methods that cannot be
   *  implemented in java bytecodes. These instructions are generated whenever
   *  we encounter an "invokestatic" bytecode that calls a method with a
   *  signature of the form "static native Magic.xxx(...)".
   *
   * NOTE: when adding a new "methodName" to "generate()", be sure to also
   * consider how it affects the values on the stack and update
   * "checkForActualCall()" accordingly.
   * If no call is actually generated, the map will reflect the status of the
   * locals (including parameters) at the time of the call but nothing on the
   * operand stack for the call site will be mapped.
   */

  /** Generate inline code sequence for specified method.
   * @param methodToBeCalled method whose name indicates semantics of code to be generated
   * @return true if there was magic defined for the method
   */
  private boolean genMagic(MethodReference methodToBeCalled) {
    Atom methodName = methodToBeCalled.getName();

    if (methodToBeCalled.isSysCall()) {
      genSysCall(methodToBeCalled);
      return true;
    }

    if (methodToBeCalled.getType() == TypeReference.Address) {
      // Address.xyz magic

      TypeReference[] types = methodToBeCalled.getParameterTypes();

      // Loads all take the form:
      // ..., Address, [Offset] -> ..., Value
      if (methodName == MagicNames.loadAddress ||
          methodName == MagicNames.loadObjectReference ||
          methodName == MagicNames.loadWord ||
          methodName == MagicNames.loadInt ||
          methodName == MagicNames.loadFloat) {
        if (types.length == 0) {
          asm.emitPOP(ALWAYS, T0); // Pop base
          asm.emitLDRimm(ALWAYS, T0, T0, 0);
          asm.emitPUSH(ALWAYS, T0);
        } else {
          asm.emitPOP(ALWAYS, T1); // Pop offset
          asm.emitPOP(ALWAYS, T0); // Pop base
          asm.emitLDR(ALWAYS, T0, T0, T1);
          asm.emitPUSH(ALWAYS, T0);
        }
        return true;
      } else if (methodName == MagicNames.loadChar) {
        if (types.length == 0) {
          asm.emitPOP(ALWAYS, T0);            // Pop base
          asm.emitLDRHimm(ALWAYS, T0, T0, 0); // unsigned 16-bit load
          asm.emitPUSH(ALWAYS, T0);
        } else {
          asm.emitPOP(ALWAYS, T1);          // Pop offset
          asm.emitPOP(ALWAYS, T0);          // Pop base
          asm.emitLDRH(ALWAYS, T0, T0, T1); // unsigned 16-bit load
          asm.emitPUSH(ALWAYS, T0);
        }
        return true;
      } else if (methodName == MagicNames.loadShort) {
        if (types.length == 0) {
          asm.emitPOP(ALWAYS, T0);             // Pop base
          asm.emitLDRSHimm(ALWAYS, T0, T0, 0); // signed 16-bit load
          asm.emitPUSH(ALWAYS, T0);
        } else {
          asm.emitPOP(ALWAYS, T1);           // Pop offset
          asm.emitPOP(ALWAYS, T0);           // Pop base
          asm.emitLDRSH(ALWAYS, T0, T0, T1); // signed 16-bit load
          asm.emitPUSH(ALWAYS, T0);
        }
        return true;
      } else if (methodName == MagicNames.loadByte) {
        if (types.length == 0) {
          asm.emitPOP(ALWAYS, T0);             // Pop base
          asm.emitLDRSBimm(ALWAYS, T0, T0, 0); // signed 8-bit load
          asm.emitPUSH(ALWAYS, T0);
        } else {
          asm.emitPOP(ALWAYS, T1);           // Pop offset
          asm.emitPOP(ALWAYS, T0);           // Pop base
          asm.emitLDRSB(ALWAYS, T0, T0, T1); // signed 8-bit load
          asm.emitPUSH(ALWAYS, T0);
        }
        return true;
      } else if (methodName == MagicNames.loadDouble || methodName == MagicNames.loadLong) {
        if (types.length == 0) {
          asm.emitPOP(ALWAYS, T0); // Pop base
          asm.emitVLDR64(ALWAYS, F0and1, T0, 0);
          asm.emitVPUSH64(ALWAYS, F0and1);
        } else {
          asm.emitPOP(ALWAYS, T1); // Pop offset
          asm.emitPOP(ALWAYS, T0); // Pop base
          asm.emitADD(ALWAYS, T0, T0, T1);
          asm.emitVLDR64(ALWAYS, F0and1, T0, 0);
          asm.emitVPUSH64(ALWAYS, F0and1);
        }
        return true;

      // Prepares all take the form:
      // ..., Address, [Offset] -> ..., Value
      } else if (methodName == MagicNames.prepareInt ||
               methodName == MagicNames.prepareWord ||
               methodName == MagicNames.prepareObjectReference ||
               methodName == MagicNames.prepareAddress) {
        if (types.length == 0) {
          asm.emitPOP(ALWAYS, T1);        // Pop base
          asm.emitLDREX(ALWAYS, T0, T1);  // thread has now reserved this address
          asm.emitPUSH(ALWAYS, T0);       // push read value
        } else {
          asm.emitPOP(ALWAYS, T1);        // Pop offset
          asm.emitPOP(ALWAYS, T0);        // Pop base
          asm.emitADD(ALWAYS, T1, T1, T0);
          asm.emitLDREX(ALWAYS, T0, T1);  // thread has now reserved this address
          asm.emitPUSH(ALWAYS, T0);       // push read value
        }
        return true;
      } else if ((methodName == MagicNames.prepareLong) ||
          (VM.BuildFor64Addr && (methodName == MagicNames.prepareWord)) ||
          (VM.BuildFor64Addr && (methodName == MagicNames.prepareObjectReference)) ||
          (VM.BuildFor64Addr && (methodName == MagicNames.prepareAddress))) {
        if (types.length == 0) {
          asm.emitPOP(ALWAYS, T1);        // Pop base
          asm.emitLDREXD(ALWAYS, T0, T1, T1);  // thread has now reserved this address
          asm.emitPUSH(ALWAYS, T1);       // T1 = high word
          asm.emitPUSH(ALWAYS, T0);       // T0 = low word
        } else {
          asm.emitPOP(ALWAYS, T1);        // Pop offset
          asm.emitPOP(ALWAYS, T0);        // Pop base
          asm.emitADD(ALWAYS, T1, T1, T0);
          asm.emitLDREXD(ALWAYS, T0, T1, T1);  // thread has now reserved this address
          asm.emitPUSH(ALWAYS, T1);       // T1 = high word
          asm.emitPUSH(ALWAYS, T0);       // T0 = low word
        }
        return true;

      // Attempts all take the form:
      // ..., Address, OldVal, NewVal, [Offset] -> ..., Success?
      } else if (methodName == MagicNames.attempt &&
          (types[0] == TypeReference.Int ||
           types[0] == TypeReference.Address ||
           types[0] == TypeReference.Word)) {
        if (types.length == 2) {
          asm.emitPOP(ALWAYS, T2);  // Pop new value
          asm.emitPOP(ALWAYS, T1);  // Ignore old value
          asm.emitPOP(ALWAYS, T1);  // Pop base
          asm.emitSTREX(ALWAYS, T0, T2, T1); // store new value
          asm.emitEORimm(ALWAYS, T0, T0, 1); // STREX returns 0 on success and 1 on failure, but we want the opposite
          asm.emitPUSH(ALWAYS, T0); // push success of conditional store
        } else {
          asm.emitPOP(ALWAYS, T2);  // Pop new value
          asm.emitPOP(ALWAYS, T1);  // Ignore old value
          asm.emitPOP(ALWAYS, T1);  // Pop offset
          asm.emitPOP(ALWAYS, T0);  // Pop base
          asm.emitADD(ALWAYS, T1, T0, T1);
          asm.emitSTREX(ALWAYS, T0, T2, T1); // store new value
          asm.emitEORimm(ALWAYS, T0, T0, 1); // STREX returns 0 on success and 1 on failure, but we want the opposite
          asm.emitPUSH(ALWAYS, T0); // push success of conditional store
        }
        return true;
      } else if (methodName == MagicNames.attempt &&
          (types[0] == TypeReference.Long)) {
        if (types.length == 2) {
          asm.emitPOP(ALWAYS, T2);  // Pop low word of new value
          asm.emitPOP(ALWAYS, T3);  // Pop high word
          asm.emitPOP(ALWAYS, T1);  // Ignore low word of old value
          asm.emitPOP(ALWAYS, T1);  // Ignore high word
          asm.emitPOP(ALWAYS, T1);  // Pop base
          asm.emitSTREXD(ALWAYS, T0, T2, T3, T1); // store new value
          asm.emitEORimm(ALWAYS, T0, T0, 1);      // STREXD returns 0 on success and 1 on failure, but we want the opposite
          asm.emitPUSH(ALWAYS, T0); // push success of conditional store
        } else {
          asm.emitPOP(ALWAYS, T2);  // Pop low word of new value
          asm.emitPOP(ALWAYS, T3);  // Pop high word
          asm.emitPOP(ALWAYS, T1);  // Ignore low word of old value
          asm.emitPOP(ALWAYS, T1);  // Ignore high word
          asm.emitPOP(ALWAYS, T1);  // Pop offset
          asm.emitPOP(ALWAYS, T0);  // Pop base
          asm.emitADD(ALWAYS, T1, T0, T1);
          asm.emitSTREXD(ALWAYS, T0, T2, T3, T1); // store new value
          asm.emitEORimm(ALWAYS, T0, T0, 1);      // STREXD returns 0 on success and 1 on failure, but we want the opposite
          asm.emitPUSH(ALWAYS, T0); // push success of conditional store
        }
        return true;

      // Stores all take the form:
      // ..., Address, Value, [Offset] -> ...
      } else if (methodName == MagicNames.store) {

        if (types[0] == TypeReference.Word ||
            types[0] == TypeReference.ObjectReference ||
            types[0] == TypeReference.Address ||
            types[0] == TypeReference.Int ||
            types[0] == TypeReference.Float) {
          if (types.length == 1) {
            asm.emitPOP(ALWAYS, T2); // Pop new value
            asm.emitPOP(ALWAYS, T0); // Pop base
            asm.emitSTRimm(ALWAYS, T2, T0, 0);
          } else {
            asm.emitPOP(ALWAYS, T1); // Pop offset
            asm.emitPOP(ALWAYS, T2); // Pop new value
            asm.emitPOP(ALWAYS, T0); // Pop base
            asm.emitSTR(ALWAYS, T2, T0, T1);
          }
          return true;
        } else if (types[0] == TypeReference.Byte || types[0] == TypeReference.Boolean) {
          if (types.length == 1) {
            asm.emitPOP(ALWAYS, T2); // Pop new value
            asm.emitPOP(ALWAYS, T0); // Pop base
            asm.emitSTRBimm(ALWAYS, T2, T0, 0);
          } else {
            asm.emitPOP(ALWAYS, T1); // Pop offset
            asm.emitPOP(ALWAYS, T2); // Pop new value
            asm.emitPOP(ALWAYS, T0); // Pop base
            asm.emitSTRB(ALWAYS, T2, T0, T1);
          }
          return true;
        } else if (types[0] == TypeReference.Short || types[0] == TypeReference.Char) {
          if (types.length == 1) {
            asm.emitPOP(ALWAYS, T2); // Pop new value
            asm.emitPOP(ALWAYS, T0); // Pop base
            asm.emitSTRHimm(ALWAYS, T2, T0, 0);
          } else {
            asm.emitPOP(ALWAYS, T1); // Pop offset
            asm.emitPOP(ALWAYS, T2); // Pop new value
            asm.emitPOP(ALWAYS, T0); // Pop base
            asm.emitSTRH(ALWAYS, T2, T0, T1);
          }
          return true;
        } else if (types[0] == TypeReference.Double || types[0] == TypeReference.Long) {
          if (types.length == 1) {
            asm.emitVPOP64(ALWAYS, F0and1); // Pop new value
            asm.emitPOP(ALWAYS, T0);        // Pop base
            asm.emitVSTR64(ALWAYS, F0and1, T0, 0);
          } else {
            asm.emitPOP(ALWAYS, T1);        // Pop offset
            asm.emitVPOP64(ALWAYS, F0and1); // Pop new value
            asm.emitPOP(ALWAYS, T0);        // Pop base
            asm.emitADD(ALWAYS, T0, T0, T1);
            asm.emitVSTR64(ALWAYS, F0and1, T0, 0);
          }
          return true;
        }
      }
    }

    if (methodName == MagicNames.getFramePointer) {
      asm.emitPUSH(ALWAYS, FP);
    } else if (methodName == MagicNames.getCallerFramePointer) {
      asm.emitPOP(ALWAYS, T0);                                                 // Read argument: callee FP
      asm.emitLDRimm(ALWAYS, T0, T0, STACKFRAME_FRAME_POINTER_OFFSET.toInt()); // Read the saved caller FP
      asm.emitPUSH(ALWAYS, T0);                                                // Push it as return value
    } else if (methodName == MagicNames.setCallerFramePointer) {
      asm.emitPOP(ALWAYS, T1);                                                 // Read argument: new FP
      asm.emitPOP(ALWAYS, T0);                                                 // Read argument: callee FP
      asm.emitSTRimm(ALWAYS, T1, T0, STACKFRAME_FRAME_POINTER_OFFSET.toInt()); // Overwrite the saved caller FP with T1
    } else if (methodName == MagicNames.getCompiledMethodID) {
      asm.emitPOP(ALWAYS, T0);                                                 // Read argument: callee FP
      asm.emitLDRimm(ALWAYS, T0, T0, STACKFRAME_METHOD_ID_OFFSET.toInt());     // Read the saved methodID
      asm.emitPUSH(ALWAYS, T0);                                                // Push it as return value
    } else if (methodName == MagicNames.setCompiledMethodID) {
      asm.emitPOP(ALWAYS, T1);                                                 // Read argument: new ID
      asm.emitPOP(ALWAYS, T0);                                                 // Read argument: callee FP
      asm.emitSTRimm(ALWAYS, T1, T0, STACKFRAME_METHOD_ID_OFFSET.toInt());     // Overwrite the saved ID with T1
    } else if (methodName == MagicNames.getNextInstructionAddress) {
      asm.emitPOP(ALWAYS, T0);                                                 // Read argument: callee FP
      asm.emitLDRimm(ALWAYS, T0, T0, STACKFRAME_RETURN_ADDRESS_OFFSET.toInt());// Read the saved callee LR
      asm.emitPUSH(ALWAYS, T0);                                                // Push it as return value
    } else if (methodName == MagicNames.getReturnAddressLocation) {
      asm.emitPOP(ALWAYS, T0);                                                 // Read argument: callee FP
      asm.emitADDimm(ALWAYS, T0, T0, STACKFRAME_RETURN_ADDRESS_OFFSET.toInt());// The *address* of the saved caller LR
      asm.emitPUSH(ALWAYS, T0);                                                // Push it as return value
    } else if (methodName == MagicNames.getTocPointer || methodName == MagicNames.getJTOC) {
      asm.emitPUSH(ALWAYS, JTOC);
    } else if (methodName == MagicNames.getThreadRegister) {
      asm.emitPUSH(ALWAYS, TR);
    } else if (methodName == MagicNames.setThreadRegister) {
      asm.emitPOP(ALWAYS, TR);
    } else if (methodName == MagicNames.getTimeBase) {
      asm.generateGetTimer(ALWAYS, T0, T1); // T0 = low word, T1 = high word
      asm.emitPUSH(ALWAYS, T1);
      asm.emitPUSH(ALWAYS, T0);
    } else if (methodName == MagicNames.invokeClassInitializer) {
      asm.emitPOP(ALWAYS, T0); // Address to be called
      asm.emitBLX(ALWAYS, T0); // Call it
    } else if (methodName == MagicNames.invokeMethodReturningVoid) {
      genMethodInvocation();
    } else if (methodName == MagicNames.invokeMethodReturningInt) {
      genMethodInvocation();
      asm.emitPUSH(ALWAYS, T0);        // push result
    } else if (methodName == MagicNames.invokeMethodReturningLong) {
      genMethodInvocation();
      asm.emitVPUSH64(ALWAYS, F0and1); // push result
    } else if (methodName == MagicNames.invokeMethodReturningFloat) {
      genMethodInvocation();
      asm.emitVPUSH32(ALWAYS, F0);     // push result
    } else if (methodName == MagicNames.invokeMethodReturningDouble) {
      genMethodInvocation();
      asm.emitVPUSH64(ALWAYS, F0and1); // push result
    } else if (methodName == MagicNames.invokeMethodReturningObject) {
      genMethodInvocation();
      asm.emitPUSH(ALWAYS, T0);        // push result
    } else if (methodName == MagicNames.addressArrayCreate) {
      RVMArray type = methodToBeCalled.getType().resolve().asArray();
      emit_resolved_newarray(type);
    } else if (methodName == MagicNames.addressArrayLength) {
      emit_arraylength();
    } else if (methodName == MagicNames.addressArrayGet) {
      emit_iaload();
    } else if (methodName == MagicNames.addressArraySet) {
      emit_iastore(); // TODO: should we avoid this one because it might compile a write barrier?
    } else if (methodName == MagicNames.getIntAtOffset || methodName == MagicNames.getFloatAtOffset) {
      asm.emitPOP(ALWAYS, T1); // Pop offset
      asm.emitPOP(ALWAYS, T0); // Pop object
      asm.emitLDR(ALWAYS, T0, T0, T1);
      asm.emitPUSH(ALWAYS, T0);
    } else if (methodName == MagicNames.getObjectAtOffset ||
               methodName == MagicNames.getWordAtOffset ||
               methodName == MagicNames.getAddressAtOffset ||
               methodName == MagicNames.getOffsetAtOffset ||
               methodName == MagicNames.getExtentAtOffset ||
               methodName == MagicNames.getTIBAtOffset) {

      if (methodToBeCalled.getParameterTypes().length == 3)
        asm.emitPOP(ALWAYS, T1); // discard locationMetadata parameter

      asm.emitPOP(ALWAYS, T1); // Pop offset
      asm.emitPOP(ALWAYS, T0); // Pop object
      asm.emitLDR(ALWAYS, T0, T0, T1);
      asm.emitPUSH(ALWAYS, T0);
    } else if (methodName == MagicNames.getUnsignedByteAtOffset) {
      asm.emitPOP(ALWAYS, T1);          // Pop offset
      asm.emitPOP(ALWAYS, T0);          // Pop object
      asm.emitLDRB(ALWAYS, T0, T0, T1); // unsigned 8-bit load
      asm.emitPUSH(ALWAYS, T0);
    } else if (methodName == MagicNames.getByteAtOffset) {
      asm.emitPOP(ALWAYS, T1);           // Pop offset
      asm.emitPOP(ALWAYS, T0);           // Pop object
      asm.emitLDRSB(ALWAYS, T0, T0, T1); // signed 8-bit load
      asm.emitPUSH(ALWAYS, T0);
    } else if (methodName == MagicNames.getCharAtOffset) {
      asm.emitPOP(ALWAYS, T1);          // Pop offset
      asm.emitPOP(ALWAYS, T0);          // Pop object
      asm.emitLDRH(ALWAYS, T0, T0, T1); // unsigned 16-bit load
      asm.emitPUSH(ALWAYS, T0);
    } else if (methodName == MagicNames.getShortAtOffset) {
      asm.emitPOP(ALWAYS, T1);           // Pop offset
      asm.emitPOP(ALWAYS, T0);           // Pop object
      asm.emitLDRSH(ALWAYS, T0, T0, T1); // signed 16-bit load
      asm.emitPUSH(ALWAYS, T0);
    } else if (methodName == MagicNames.setIntAtOffset ||
               methodName == MagicNames.setFloatAtOffset ||
               methodName == MagicNames.setObjectAtOffset ||
               methodName == MagicNames.setWordAtOffset ||
               methodName == MagicNames.setAddressAtOffset ||
               methodName == MagicNames.setOffsetAtOffset ||
               methodName == MagicNames.setExtentAtOffset) {

      if (methodToBeCalled.getParameterTypes().length == 4)
        asm.emitPOP(ALWAYS, T2); // discard locationMetadata parameter

      asm.emitPOP(ALWAYS, T2); // Pop new value
      asm.emitPOP(ALWAYS, T1); // Pop offset
      asm.emitPOP(ALWAYS, T0); // Pop object
      asm.emitSTR(ALWAYS, T2, T0, T1);
    } else if (methodName == MagicNames.setByteAtOffset || methodName == MagicNames.setBooleanAtOffset) {
      if (methodToBeCalled.getParameterTypes().length == 4)
        asm.emitPOP(ALWAYS, T2); // discard locationMetadata parameter

      asm.emitPOP(ALWAYS, T2); // Pop new value
      asm.emitPOP(ALWAYS, T1); // Pop offset
      asm.emitPOP(ALWAYS, T0); // Pop object
      asm.emitSTRB(ALWAYS, T2, T0, T1);
    } else if (methodName == MagicNames.setCharAtOffset || methodName == MagicNames.setShortAtOffset) {
      if (methodToBeCalled.getParameterTypes().length == 4)
        asm.emitPOP(ALWAYS, T2); // discard locationMetadata parameter

      asm.emitPOP(ALWAYS, T2); // Pop new value
      asm.emitPOP(ALWAYS, T1); // Pop offset
      asm.emitPOP(ALWAYS, T0); // Pop object
      asm.emitSTRH(ALWAYS, T2, T0, T1);
    } else if (methodName == MagicNames.getLongAtOffset || methodName == MagicNames.getDoubleAtOffset) {
      asm.emitPOP(ALWAYS, T1); // Pop offset
      asm.emitPOP(ALWAYS, T0); // Pop object
      asm.emitADD(ALWAYS, T0, T0, T1);
      asm.emitVLDR64(ALWAYS, F0and1, T0, 0);
      asm.emitVPUSH64(ALWAYS, F0and1);
    } else if ((methodName == MagicNames.setLongAtOffset) || (methodName == MagicNames.setDoubleAtOffset)) {
      if (methodToBeCalled.getParameterTypes().length == 4)
        asm.emitPOP(ALWAYS, T2); // discard locationMetadata parameter

      asm.emitVPOP64(ALWAYS, F0and1); // Pop new value
      asm.emitPOP(ALWAYS, T1);        // Pop offset
      asm.emitPOP(ALWAYS, T0);        // Pop object
      asm.emitADD(ALWAYS, T0, T0, T1);
      asm.emitVSTR64(ALWAYS, F0and1, T0, 0);
    } else if (methodName == MagicNames.getMemoryInt ||
               methodName == MagicNames.getMemoryWord ||
               methodName == MagicNames.getMemoryAddress) {
      asm.emitPOP(ALWAYS, T0); // Pop address
      asm.emitLDRimm(ALWAYS, T0, T0, 0);
      asm.emitPUSH(ALWAYS, T0);
    } else if (methodName == MagicNames.setMemoryInt || methodName == MagicNames.setMemoryWord) {
      asm.emitPOP(ALWAYS, T1); // Pop new value
      asm.emitPOP(ALWAYS, T0); // Pop address
      asm.emitSTRimm(ALWAYS, T1, T0, 0);
    } else if (methodName == MagicNames.prepareInt ||
               methodName == MagicNames.prepareObject ||
               methodName == MagicNames.prepareAddress ||
               methodName == MagicNames.prepareWord) {
      asm.emitPOP(ALWAYS, T1);        // Pop offset
      asm.emitPOP(ALWAYS, T0);        // Pop object
      asm.emitADD(ALWAYS, T1, T1, T0);
      asm.emitLDREX(ALWAYS, T0, T1);  // thread has now reserved this address
      asm.emitPUSH(ALWAYS, T0);       // push read value
    } else if (methodName == MagicNames.prepareLong) {
      asm.emitPOP(ALWAYS, T1);        // Pop offset
      asm.emitPOP(ALWAYS, T0);        // Pop object
      asm.emitADD(ALWAYS, T1, T1, T0);
      asm.emitLDREXD(ALWAYS, T0, T1, T1);  // thread has now reserved this address
      asm.emitPUSH(ALWAYS, T1);       // T1 = high word
      asm.emitPUSH(ALWAYS, T0);       // T0 = low word // TODO: check if this is the right endianness
    } else if (methodName == MagicNames.attemptInt ||
               methodName == MagicNames.attemptObject ||
               methodName == MagicNames.attemptObjectReference ||
               methodName == MagicNames.attemptAddress ||
               methodName == MagicNames.attemptWord) {
      asm.emitPOP(ALWAYS, T2);  // Pop new value
      asm.emitPOP(ALWAYS, T1);  // Ignore old value
      asm.emitPOP(ALWAYS, T1);  // Pop offset
      asm.emitPOP(ALWAYS, T0);  // Pop object
      asm.emitADD(ALWAYS, T1, T0, T1);
      asm.emitSTREX(ALWAYS, T0, T2, T1); // store new value
      asm.emitEORimm(ALWAYS, T0, T0, 1); // STREX returns 0 on success and 1 on failure, but we want the opposite
      asm.emitPUSH(ALWAYS, T0); // push success of conditional store
    } else if (methodName == MagicNames.attemptLong) {
      asm.emitPOP(ALWAYS, T2);  // Pop low word of new value
      asm.emitPOP(ALWAYS, T3);  // Pop high word
      asm.emitPOP(ALWAYS, T1);  // Ignore low word of old value
      asm.emitPOP(ALWAYS, T1);  // Ignore high word
      asm.emitPOP(ALWAYS, T1);  // Pop offset
      asm.emitPOP(ALWAYS, T0);  // Pop object
      asm.emitADD(ALWAYS, T1, T0, T1);
      asm.emitSTREXD(ALWAYS, T0, T2, T3, T1); // store new value
      asm.emitEORimm(ALWAYS, T0, T0, 1);      // STREXD returns 0 on success and 1 on failure, but we want the opposite
      asm.emitPUSH(ALWAYS, T0); // push success of conditional store
    } else if (methodName == MagicNames.saveThreadState) {
      asm.emitPOP(ALWAYS, T0); // T0 = address of Registers object
      asm.generateOffsetLoad(ALWAYS, LR, JTOC, ArchEntrypoints.saveThreadStateInstructionsField.getOffset());
      asm.emitBLX(ALWAYS, LR); // call out of line machine code
    } else if (methodName == MagicNames.threadSwitch) {
      if (VM.VerifyAssertions) VM._assert(VM.NOT_REACHED, "threadSwitch not implemented");
    } else if (methodName == MagicNames.restoreHardwareExceptionState) {
      asm.generateOffsetLoad(ALWAYS, LR, JTOC, ArchEntrypoints.restoreHardwareExceptionStateInstructionsField.getOffset());
      asm.emitPOP(ALWAYS, R12);// R12 = address of Registers object
      asm.emitBX(ALWAYS, LR);  // branch to out of line machine code (does not return)
    } else if (methodName == MagicNames.returnToNewStack) {
      if (VM.VerifyAssertions) VM._assert(method.hasBaselineNoRegistersAnnotation());
      if (VM.VerifyAssertions) VM._assert(nextGPR == FIRST_LOCAL_GPR.value());
      if (VM.VerifyAssertions) VM._assert(nextFPR == FIRST_LOCAL_FPR.value());
      if (VM.VerifyAssertions) VM._assert(SAVED_REGISTER_BYTES == 0);
      // TODO: use pop multiple instruction, return by popping to PC
      asm.emitPOP(ALWAYS, FP);
      asm.emitADDimm(ALWAYS, SP, FP, STACKFRAME_SAVED_REGISTER_OFFSET.toInt());
      asm.emitPOP(ALWAYS, FP);
      asm.emitPOP(ALWAYS, R12); // discard compiled method id
      asm.emitPOP(ALWAYS, LR);
      if (VM.VerifyAssertions) VM._assert((paramStackBytes & 0x3) == 0); // paramStackBytes % 4 == 0; i.e. word-aligned
      if (VM.VerifyAssertions) VM._assert(paramStackBytes >= 0);
      if (paramStackBytes > 0)  // Pop on-stack parameters
        asm.emitADDimm(ALWAYS, SP, SP, paramStackBytes);
      asm.emitBX(ALWAYS, LR);   // return
    } else if (methodName == MagicNames.dynamicBridgeTo) {
      // TODO: use pop multiple instruction, return by popping to PC
      if (VM.VerifyAssertions) VM._assert(klass.hasDynamicBridgeAnnotation());

      // fetch parameter (address to branch to)
      asm.emitPOP(ALWAYS, R12);

      asm.generateImmediateAdd(ALWAYS, SP, FP, STACKFRAME_SAVED_REGISTER_OFFSET.toInt() - SAVED_REGISTER_BYTES); // Return to the start of our stackframe

      // restore all registers, including the volatiles (these are only saved for "dynamic bridge" methods).
      if (VM.VerifyAssertions) VM._assert((LAST_VOLATILE_DPR.value() & 1) == 0 && (LAST_LOCAL_DPR.value() & 1) == 0);   // Should be even
      if (VM.VerifyAssertions) VM._assert((FIRST_VOLATILE_DPR.value() & 1) == 0 && (FIRST_LOCAL_DPR.value() & 1) == 0);

      for (int i = FIRST_VOLATILE_DPR.value(); i <= LAST_VOLATILE_DPR.value(); i += 2) {
        asm.emitVPOP64(ALWAYS, DPR.lookup(i));
      }

      if (VM.VerifyAssertions) VM._assert(LAST_VOLATILE_DPR.value() < FIRST_LOCAL_DPR.value());

      for (int i = FIRST_LOCAL_DPR.value(); i <= LAST_LOCAL_DPR.value(); i += 2) {
        asm.emitVPOP64(ALWAYS, DPR.lookup(i));
      }

      for (int i = FIRST_VOLATILE_GPR.value(); i <= LAST_VOLATILE_GPR.value(); i++) {
        asm.emitPOP(ALWAYS, GPR.lookup(i));
      }

      if (VM.VerifyAssertions) VM._assert(LAST_VOLATILE_GPR.value() < FIRST_LOCAL_GPR.value());

      for (int i = FIRST_LOCAL_GPR.value(); i <= LAST_LOCAL_GPR.value(); i++) {
        asm.emitPOP(ALWAYS, GPR.lookup(i));
      }

      asm.emitPOP(ALWAYS, FP);
      asm.emitPOP(ALWAYS, LR); // Discard compiled method id
      asm.emitPOP(ALWAYS, LR);

      if (VM.VerifyAssertions) VM._assert((paramStackBytes & 0x3) == 0); // paramStackBytes % 4 == 0; i.e. word-aligned
      if (VM.VerifyAssertions) VM._assert(paramStackBytes >= 0);

      if (paramStackBytes > 0) // Pop on-stack parameters
        asm.emitADDimm(ALWAYS, SP, SP, paramStackBytes);

      asm.emitBX(ALWAYS, R12); // Branch to method

    } else if (methodName == MagicNames.objectAsAddress ||
               methodName == MagicNames.addressAsByteArray ||
               methodName == MagicNames.addressAsObject ||
               methodName == MagicNames.addressAsTIB ||
               methodName == MagicNames.objectAsType ||
               methodName == MagicNames.objectAsShortArray ||
               methodName == MagicNames.objectAsIntArray ||
               methodName == MagicNames.objectAsThread ||
               methodName == MagicNames.floatAsIntBits ||
               methodName == MagicNames.intBitsAsFloat ||
               methodName == MagicNames.doubleAsLongBits ||
               methodName == MagicNames.longBitsAsDouble) {
      // no-op (a type change, not a representation change)
    } else if (methodName == MagicNames.getObjectType) {
      asm.emitPOP(ALWAYS, T0);                      // get object pointer
      asm.baselineEmitLoadTIB(T1, T0);              // get Type Information Block
      asm.emitLDRimm(ALWAYS, T0, T1, TIB_TYPE_INDEX << LOG_BYTES_IN_ADDRESS); // get "type" field from TIB
      asm.emitPUSH(ALWAYS, T0);                     // Store type
    } else if (methodName == MagicNames.getArrayLength) {
      emit_arraylength();
    } else if (methodName == MagicNames.pause) {
      asm.emitYIELD(ALWAYS); // PAUSE on x86 corresponds to YIELD on ARM
    } else if (methodName == MagicNames.combinedLoadBarrier) {
      asm.emitDMB();
    } else if (methodName == MagicNames.storeStoreBarrier) {
      asm.emitDMBst();
    } else if (methodName == MagicNames.fence) {
      asm.emitDMB();
    } else if (methodName == MagicNames.sync) {
      asm.emitDMB(); // Technically SYNC is PPC-only but it is used by some generic code
    } else if (methodName == MagicNames.isync) {
      asm.emitISB(); // Technically ISYNC is PPC-only but it is used by some generic code
    } else if (methodName == MagicNames.dcbst ||
               methodName == MagicNames.dcbt ||
               methodName == MagicNames.dcbtst ||
               methodName == MagicNames.dcbz ||
               methodName == MagicNames.dcbzl ||
               methodName == MagicNames.icbi) {
      if (VM.VerifyAssertions) VM._assert(VM.NOT_REACHED); // PPC only
    } else if (methodName == MagicNames.prefetch) {
      asm.emitPOP(ALWAYS, T0);
      asm.emitPLDimm(T0, 0); // Preload Data into cache
    } else if (methodName == MagicNames.sqrt) {
      TypeReference argType = method.getParameterTypes()[0];
      if (argType == TypeReference.Float) {
        asm.emitVPOP32(ALWAYS, F0);
        asm.emitVSQRT32(ALWAYS, F0, F0);
        asm.emitVPUSH32(ALWAYS, F0);
      } else {
        if (VM.VerifyAssertions) VM._assert(argType == TypeReference.Double);
        asm.emitVPOP64(ALWAYS, F0and1);
        asm.emitVSQRT64(ALWAYS, F0and1, F0and1);
        asm.emitVPUSH64(ALWAYS, F0and1);
      }
    } else if (methodName == MagicNames.getInlineDepth ||
               methodName == MagicNames.isConstantParameter) {
      emit_iconst(0); // These always return 0 for the baseline compiler
    } else if (methodName == MagicNames.wordToInt ||
               methodName == MagicNames.wordToAddress ||
               methodName == MagicNames.wordToOffset ||
               methodName == MagicNames.wordToObject ||
               methodName == MagicNames.wordFromObject ||
               methodName == MagicNames.wordToObjectReference ||
               methodName == MagicNames.wordToExtent ||
               methodName == MagicNames.wordToWord ||
               methodName == MagicNames.codeArrayAsObject ||
               methodName == MagicNames.tibAsObject) {
      // no-op
    } else if (methodName == MagicNames.wordToLong) {
      asm.emitPOP(ALWAYS, T0);       // Low word
      asm.emitMOVimm(ALWAYS, T1, 0); // High word
      asm.emitPUSH(ALWAYS, T1);
      asm.emitPUSH(ALWAYS, T0);
    } else if (methodName == MagicNames.wordFromInt || methodName == MagicNames.wordFromIntSignExtend || methodName == MagicNames.wordFromIntZeroExtend) {
      // no-op
    } else if (methodName == MagicNames.wordFromLong) {
      asm.emitPOP(ALWAYS, T0); // Low word
      asm.emitPOP(ALWAYS, T1); // High word
      asm.emitPUSH(ALWAYS, T0);
    } else if (methodName == MagicNames.wordPlus) {
      emit_iadd();
    } else if (methodName == MagicNames.wordMinus || methodName == MagicNames.wordDiff) {
      emit_isub();
    } else if (methodName == MagicNames.wordEQ) {
      genComparison(EQ);
    } else if (methodName == MagicNames.wordNE) {
      genComparison(NE);
    } else if (methodName == MagicNames.wordLT) { // unsigned
      genComparison(LO);
    } else if (methodName == MagicNames.wordLE) { // unsigned
      genComparison(LS);
    } else if (methodName == MagicNames.wordGT) { // unsigned
      genComparison(HI);
    } else if (methodName == MagicNames.wordGE) { // unsigned
      genComparison(HS);
    } else if (methodName == MagicNames.wordsLT) { // signed
      genComparison(LT);
    } else if (methodName == MagicNames.wordsLE) { // signed
      genComparison(LE);
    } else if (methodName == MagicNames.wordsGT) { // signed
      genComparison(GT);
    } else if (methodName == MagicNames.wordsGE) { // signed
      genComparison(GE);
    } else if (methodName == MagicNames.wordIsZero || methodName == MagicNames.wordIsNull) {
      asm.emitPOP(ALWAYS, T0);
      asm.emitMOVimm(ALWAYS, T1, 0);
      asm.emitCMP(ALWAYS, T0, T1);
      asm.emitMOVimm(NE, T2, 0);
      asm.emitMOVimm(EQ, T2, 1);
      asm.emitPUSH(ALWAYS, T2);
    } else if (methodName == MagicNames.wordIsMax) {
      asm.emitPOP(ALWAYS, T0);
      asm.emitMVNimm(ALWAYS, T1, -1); // -1 = max word
      asm.emitCMP(ALWAYS, T0, T1);
      asm.emitMOVimm(NE, T2, 0);
      asm.emitMOVimm(EQ, T2, 1);
      asm.emitPUSH(ALWAYS, T2);
    } else if (methodName == MagicNames.wordZero || methodName == MagicNames.wordNull) {
      emit_iconst(0);
    } else if (methodName == MagicNames.wordOne) {
      emit_iconst(1);
    } else if (methodName == MagicNames.wordMax) {
      emit_iconst(-1);
    } else if (methodName == MagicNames.wordAnd) {
      asm.emitPOP(ALWAYS, T1);
      asm.emitPOP(ALWAYS, T0);
      asm.emitAND(ALWAYS, T0, T0, T1);
      asm.emitPUSH(ALWAYS, T0);
    } else if (methodName == MagicNames.wordOr) {
      asm.emitPOP(ALWAYS, T1);
      asm.emitPOP(ALWAYS, T0);
      asm.emitORR(ALWAYS, T0, T0, T1);
      asm.emitPUSH(ALWAYS, T0);
    } else if (methodName == MagicNames.wordNot) {
      asm.emitPOP(ALWAYS, T0);
      asm.emitNOT(ALWAYS, T0, T0);
      asm.emitPUSH(ALWAYS, T0);
    } else if (methodName == MagicNames.wordXor) {
      asm.emitPOP(ALWAYS, T1);
      asm.emitPOP(ALWAYS, T0);
      asm.emitEOR(ALWAYS, T0, T0, T1);
      asm.emitPUSH(ALWAYS, T0);
    } else if (methodName == MagicNames.wordLsh) {
      asm.emitPOP(ALWAYS, T1);
      asm.emitPOP(ALWAYS, T0);
      asm.emitLSL(ALWAYS, T0, T0, T1);    // T0 = T0 << T1
      asm.emitPUSH(ALWAYS, T0);
    } else if (methodName == MagicNames.wordRshl) {
      asm.emitPOP(ALWAYS, T1);
      asm.emitPOP(ALWAYS, T0);
      asm.emitLSR(ALWAYS, T0, T0, T1);    // T0 = T0 >>> T1
      asm.emitPUSH(ALWAYS, T0);
    } else if (methodName == MagicNames.wordRsha) {
      asm.emitPOP(ALWAYS, T1);
      asm.emitPOP(ALWAYS, T0);
      asm.emitASR(ALWAYS, T0, T0, T1);    // T0 = T0 >> T1
      asm.emitPUSH(ALWAYS, T0);
    } else {
      return false; // Not a magic method: proceed with normal method call
    }
    return true;
  }

  /** Emit code to perform a comparison on 2 address values
   * @param cc condition to test
   */
  private void genComparison(COND cc) {
    asm.emitPOP(ALWAYS, T1);
    asm.emitPOP(ALWAYS, T0);
    asm.emitCMP(ALWAYS, T0, T1);
    asm.emitMOVimm(cc.flip(), T2, 0);
    asm.emitMOVimm(cc,        T2, 1);
    asm.emitPUSH(ALWAYS, T2);
  }

  /**
   * Indicate if the specified {@link Magic} method causes a frame to be created on the runtime stack.
   * @param methodToBeCalled   {@link RVMMethod} of the magic method being called
   * @return <code>true</code> if <code>methodToBeCalled</code> causes a stackframe to be created
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

  //----------------//
  // implementation //
  //----------------//

  /**
   * Generate code to invoke arbitrary method with arbitrary parameters/return value.
   * We generate inline code that calls "OutOfLineMachineCode.reflectiveMethodInvokerInstructions"
   * which, at runtime, will create a new stackframe with an appropriately sized spill area
   * (but no register save area, locals, or operand stack), load up the specified
   * fpr's and gpr's, call the specified method, pop the stackframe, and return a value.
   *
   * Takes the same arguments on the stack as "invokeMethodReturningX()":
   * 0. pointer to method code
   * 1. pointer to array of values to set the volatile (parameter) GPRs to
   * 2. pointer to array of values to set the volatile (parameter) FPRs to
   * 3. fprMeta, whatever that is ...
   * 4. pointer to array of values to set the spills (on-stack parameters) to
   *
   * See MachineReflection.packageParameters() to observe how these values are initialised
   */
  private void genMethodInvocation() {
    asm.generateOffsetLoad(ALWAYS, LR, JTOC, ArchEntrypoints.reflectiveMethodInvokerInstructionsField.getOffset());

    asm.emitPOP(ALWAYS, T3); // spills
    asm.emitPOP(ALWAYS, T2); // fprMeta (ignored)
    asm.emitPOP(ALWAYS, T2); // fprs
    asm.emitPOP(ALWAYS, T0); // gprs // Do these in reverse order so we can overwrite all the GPRs in order (and T0 is last)
    asm.emitPOP(ALWAYS, T1); // code

    asm.emitBLX(ALWAYS, LR);
  }

  /**
   * Generate call and return sequence to invoke a C function (defined in the bootloader) through the
   * boot record field specified by target.
   * Caller handles parameter passing and expression stack according to the ARM procedure call standard
   * (setting up args, pushing return, adjusting stack height).
   *
   * When this function is called, we assume that parameters have all been allocated on registers and the stack
   * For many functions this will be purely in registers
   * However, the general case for arbitrary parameters is handled in a different overload of this function below
   */
  private void genSysCall(COND cond, RVMField target) {
    // Use LR as scratch since we will clobber it anyway
    asm.generateOffsetLoad(cond, LR, JTOC, Entrypoints.the_boot_recordField.getOffset());
    asm.generateOffsetLoad(cond, R12, LR, target.getOffset());
    asm.emitBLX(cond, R12);
  }

  /**
   * Invoke a C function whose parameters have not been set up yet
   *
   * First argument on the stack is the address of the function to call
   * The rest are arguments to the function, which need to be passed according to OS calling conventions
   * Note that, after registers are assigned, ARM conventions expect the FIRST spill argument to be the LAST on the stack
   * As opposed to Java, where the FIRST spill is also the FIRST on the stack
   *
   */
  private void genSysCall(MethodReference methodToBeCalled) {
    if (VM.VerifyAssertions) VM._assert(methodToBeCalled.isSysCall());

    int paramWords = methodToBeCalled.getParameterWords(); // paramWords includes the first param, which is the address

    // At the start of the call, the SP points to the last parameter, and the first parameter is above it somewhere
    // At the end of it, we want the first few parameters to be in registers, and the rest to be in reverse order starting at the SP.
    // Do this in two passes. First, read all the register parameters in, and keep track of the rest to find the extent of the spills.
    // Then, go through the rest and move them to their position on the stack.
    // Because of alignment requirements in the ARM calling convention, the resulting spills might take up more stack space than the original java parameters
    // We thus have to write the spills below the java params on the stack, to avoid overwriting them

    // TODO: test this thoroughly
    TypeReference[] args = methodToBeCalled.getParameterTypes();

    int readOffset = paramWords - 1;
    int gp = FIRST_OS_PARAMETER_GPR.value();
    int fp = FIRST_OS_PARAMETER_FPR.value(); // In ARM calling convention, single-precision floats can backfill.
    int dp = FIRST_OS_PARAMETER_DPR.value(); // So if the arguments are float, double, float, they go in S0, D1, S1 (note that D1 is the double view of S2 and S3).

    int totalSpillWords = 0;

    for (int i = 1; i < args.length; i++) { // Start with 1 because argument 0 is the address
      TypeReference t = args[i];
      if (t.isDoubleType()) {
        if (dp < fp) dp = fp;
        if ((dp & 1) == 1) dp++; // 64-bit align
        if (dp <= LAST_OS_PARAMETER_DPR.value()) {
          readOffset -= 2;
          asm.emitVLDR64(ALWAYS, DPR.lookup(dp), SP, readOffset << LOG_BYTES_IN_INT);
          dp += 2;
        } else {
          // This one goes on the stack
          if ((totalSpillWords & 1) == 1) totalSpillWords++; // 64-bit align
          readOffset -= 2; totalSpillWords += 2;
        }
      } else if (t.isFloatType()) {
        if (fp < dp && (fp & 1) == 0) fp = dp; // No more to backfill
        if (fp <= LAST_OS_PARAMETER_FPR.value()) {
          asm.emitVLDR32(ALWAYS, FPR.lookup(fp++), SP, --readOffset << LOG_BYTES_IN_INT);
        } else {
          // This one goes on the stack
          readOffset--; totalSpillWords++;
        }
      } else if (t.isLongType()) {
        if ((gp & 1) == 1) gp++; // 64-bit align
        if (gp <= LAST_OS_PARAMETER_GPR.value() - 1) {
          asm.emitLDRimm(ALWAYS, GPR.lookup(gp + 1), SP, --readOffset << LOG_BYTES_IN_INT);
          asm.emitLDRimm(ALWAYS, GPR.lookup(gp),     SP, --readOffset << LOG_BYTES_IN_INT);
          gp += 2;
        } else {
          // This one goes on the stack
          if ((totalSpillWords & 1) == 1) totalSpillWords++; // 64-bit align
          readOffset -= 2; totalSpillWords += 2;
        }
      } else { // int-like or object
        if (gp <= LAST_OS_PARAMETER_GPR.value()) {
          asm.emitLDRimm(ALWAYS, GPR.lookup(gp++), SP, --readOffset << LOG_BYTES_IN_INT);
        } else {
          // This one goes on the stack
          readOffset--; totalSpillWords++;
        }
      }
    }

    if (VM.VerifyAssertions) VM._assert(readOffset == 0);
    if (VM.VerifyAssertions) VM._assert(gp <= LAST_OS_PARAMETER_GPR.value() + 1);
    if (VM.VerifyAssertions) VM._assert(fp <= LAST_OS_PARAMETER_FPR.value() + 1);
    if (VM.VerifyAssertions) VM._assert(dp <= LAST_OS_PARAMETER_DPR.value() + 2);
    if (VM.VerifyAssertions) VM._assert((dp & 1) == 0);

    // Registers have been allocated, now we are interested in the stack
    // ARM calling convention requires the stack to be doubleword aligned
    // We will thus use the first two stackslots to record whether we needed to adjust alignment or not
    // So the stack will look like this:
    // ------------------
    //  address to call
    //
    //
    //  java params
    //
    // ------------------
    //  optional padding
    //  alignment flag
    // ------------------
    //
    //    spills
    //
    // ------------------ <- 64-bit aligned boundary
    //
    // The alignment flag will be 1 if there is one word of garbage above it, and 0 otherwise
    // The need for the flag depends on the current alignment of the SP and the number of spills
    // We use the flag to invert this operation later

    // Use LR as scratch to save the old SP
    asm.emitMOV(ALWAYS, LR, SP);

    asm.emitANDSimm(ALWAYS, R12, SP, 4); // Test alignment (sets condition flags)

    if ((totalSpillWords & 1) == 0) { // Even number, so doubleword-align SP -> Will decrement by 4 if (SP & 4 != 0), otherwise by 8 to preserve alignment
       asm.emitMOVimm(NE, R12, 0);    // Requires a word adjustment
       asm.emitMOVimm(EQ, R12, 1);    // No adjustment needed (but need to skip 8 bytes to store this flag)
       asm.emitPUSH(EQ, R12);         // Write padding
       asm.emitPUSH(ALWAYS, R12);     // Write flag
    } else {                        // odd number, so do the opposite
       asm.emitMOVimm(EQ, R12, 0);    // Requires a word adjustment
       asm.emitMOVimm(NE, R12, 1);    // No adjustment needed (but need to skip 8 bytes to store this flag)
       asm.emitPUSH(NE, R12);         // Write padding
       asm.emitPUSH(ALWAYS, R12);     // Write flag
    }

    if (totalSpillWords != 0)
      asm.generateImmediateSubtract(ALWAYS, SP, SP, totalSpillWords << LOG_BYTES_IN_INT);

    // And now, repeat the parameter allocation process to actually write the spills

    // TODO: could use new variables here to assert that the procedure gives the same result both times
    readOffset = paramWords - 1;
    int writeOffset = 0;
    gp = FIRST_OS_PARAMETER_GPR.value();
    fp = FIRST_OS_PARAMETER_FPR.value();
    dp = FIRST_OS_PARAMETER_DPR.value();

    for (int i = 1; i < args.length; i++) { // Start with 1 because argument 0 is the address
      TypeReference t = args[i];
      if (t.isDoubleType()) {
        if (dp < fp) dp = fp;
        if ((dp & 1) == 1) dp++; // 64-bit align
        if (dp <= LAST_OS_PARAMETER_DPR.value()) {
          readOffset -= 2; dp += 2; // Registers
        } else {
          // This one goes on the stack
          if ((writeOffset & 1) == 1) writeOffset++; // 64-bit align

          asm.emitLDRimm(ALWAYS, R12, LR, --readOffset << LOG_BYTES_IN_INT);
          asm.emitSTRimm(ALWAYS, R12, SP, writeOffset + 1 << LOG_BYTES_IN_INT);

          asm.emitLDRimm(ALWAYS, R12, LR, --readOffset << LOG_BYTES_IN_INT);
          asm.emitSTRimm(ALWAYS, R12, SP, writeOffset << LOG_BYTES_IN_INT);

          writeOffset += 2;
        }
      } else if (t.isFloatType()) {
        if (fp < dp && (fp & 1) == 0) fp = dp; // No more to backfill
        if (fp <= LAST_OS_PARAMETER_FPR.value()) {
          readOffset--; fp++; // Registers
        } else {
          // This one goes on the stack
          asm.emitLDRimm(ALWAYS, R12, LR, --readOffset << LOG_BYTES_IN_INT);
          asm.emitSTRimm(ALWAYS, R12, SP, writeOffset++ << LOG_BYTES_IN_INT);
        }
      } else if (t.isLongType()) {
        if ((gp & 1) == 1) gp++; // 64-bit align
        if (gp <= LAST_OS_PARAMETER_GPR.value() - 1) {
          readOffset -= 2; gp += 2; // Registers
        } else {
          // This one goes on the stack
          if ((writeOffset & 1) == 1) writeOffset++; // 64-bit align

          asm.emitLDRimm(ALWAYS, R12, LR, --readOffset << LOG_BYTES_IN_INT);
          asm.emitSTRimm(ALWAYS, R12, SP, writeOffset + 1 << LOG_BYTES_IN_INT);

          asm.emitLDRimm(ALWAYS, R12, LR, --readOffset << LOG_BYTES_IN_INT);
          asm.emitSTRimm(ALWAYS, R12, SP, writeOffset << LOG_BYTES_IN_INT);

          writeOffset += 2;
        }
      } else { // int-like or object
        if (gp <= LAST_OS_PARAMETER_GPR.value()) {
          readOffset--; gp++; // Registers
        } else {
          // This one goes on the stack
          asm.emitLDRimm(ALWAYS, R12, LR, --readOffset << LOG_BYTES_IN_INT);
          asm.emitSTRimm(ALWAYS, R12, SP, writeOffset++ << LOG_BYTES_IN_INT);
        }
      }
    }

    if (VM.VerifyAssertions) VM._assert(readOffset == 0);
    if (VM.VerifyAssertions) VM._assert(gp <= LAST_OS_PARAMETER_GPR.value() + 1);
    if (VM.VerifyAssertions) VM._assert(fp <= LAST_OS_PARAMETER_FPR.value() + 1);
    if (VM.VerifyAssertions) VM._assert(dp <= LAST_OS_PARAMETER_DPR.value() + 2);
    if (VM.VerifyAssertions) VM._assert((dp & 1) == 0);
    if (VM.VerifyAssertions) VM._assert(writeOffset == totalSpillWords);

    // All spills and registers now written. LR holds old SP so we can use it to find the address of the function to call (the first java parameter)
    asm.emitLDRimm(ALWAYS, LR, LR, paramWords - 1 << LOG_BYTES_IN_INT);
    asm.emitBLX(ALWAYS, LR);            // Make the call


    // Pop spills
    if (totalSpillWords != 0)
      asm.generateImmediateAdd(ALWAYS, SP, SP, totalSpillWords << LOG_BYTES_IN_INT);

    // Undo alignment
    asm.emitPOP(ALWAYS, R12);
    asm.emitCMPimm(ALWAYS, R12, 1); // Check flag
    asm.emitPOP(EQ, R12);           // Pop padding if flag == 1

    // Pop java parameters
    if (paramWords != 0)
      asm.generateImmediateAdd(ALWAYS, SP, SP, paramWords << LOG_BYTES_IN_INT);

    // Push return value (if any)
    TypeReference rtype = methodToBeCalled.getReturnType();
    if (rtype.isIntLikeType() || rtype.isWordLikeType() || rtype.isReferenceType()) {
      asm.emitPUSH(ALWAYS, FIRST_OS_PARAMETER_GPR);
    } else if (rtype.isDoubleType()) {
      asm.emitVPUSH64(ALWAYS, FIRST_OS_PARAMETER_DPR);
    } else if (rtype.isFloatType()) {
      asm.emitVPUSH32(ALWAYS, FIRST_OS_PARAMETER_FPR);
    } else if (rtype.isLongType()) {
      asm.emitPUSH(ALWAYS, FIRST_OS_PARAMETER_GPR.nextGPR()); // High word
      asm.emitPUSH(ALWAYS, FIRST_OS_PARAMETER_GPR);           // Low word
    }
  }
}

