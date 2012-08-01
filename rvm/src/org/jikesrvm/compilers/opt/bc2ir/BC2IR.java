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
package org.jikesrvm.compilers.opt.bc2ir;

import java.util.ArrayList;
import java.util.Enumeration;
import java.util.HashSet;
import java.util.NoSuchElementException;

import org.jikesrvm.VM;
import org.jikesrvm.ArchitectureSpecificOpt.RegisterPool;
import org.jikesrvm.adaptive.controller.Controller;
import org.jikesrvm.classloader.BytecodeConstants;
import org.jikesrvm.classloader.BytecodeStream;
import org.jikesrvm.classloader.ClassLoaderConstants;
import org.jikesrvm.classloader.RVMClass;
import org.jikesrvm.classloader.ExceptionHandlerMap;
import org.jikesrvm.classloader.RVMField;
import org.jikesrvm.classloader.FieldReference;
import org.jikesrvm.classloader.RVMMethod;
import org.jikesrvm.classloader.MethodReference;
import org.jikesrvm.classloader.RVMType;
import org.jikesrvm.classloader.TypeReference;
import org.jikesrvm.compilers.baseline.SwitchBranchProfile;
import org.jikesrvm.compilers.common.CompiledMethod;
import org.jikesrvm.compilers.common.CompiledMethods;
import org.jikesrvm.compilers.opt.ClassLoaderProxy;
import org.jikesrvm.compilers.opt.FieldAnalysis;
import org.jikesrvm.compilers.opt.OperationNotImplementedException;
import org.jikesrvm.compilers.opt.OptimizingCompilerException;
import org.jikesrvm.compilers.opt.Simplifier;
import org.jikesrvm.compilers.opt.StaticFieldReader;
import org.jikesrvm.compilers.opt.driver.OptConstants;
import org.jikesrvm.compilers.opt.driver.OptimizingCompiler;
import org.jikesrvm.compilers.opt.inlining.CompilationState;
import org.jikesrvm.compilers.opt.inlining.InlineDecision;
import org.jikesrvm.compilers.opt.inlining.InlineSequence;
import org.jikesrvm.compilers.opt.inlining.Inliner;
import org.jikesrvm.compilers.opt.ir.ALoad;
import org.jikesrvm.compilers.opt.ir.AStore;
import org.jikesrvm.compilers.opt.ir.Athrow;
import org.jikesrvm.compilers.opt.ir.BasicBlock;
import org.jikesrvm.compilers.opt.ir.Binary;
import org.jikesrvm.compilers.opt.ir.BoundsCheck;
import org.jikesrvm.compilers.opt.ir.CacheOp;
import org.jikesrvm.compilers.opt.ir.Call;
import org.jikesrvm.compilers.opt.ir.ControlFlowGraph;
import org.jikesrvm.compilers.opt.ir.Empty;
import org.jikesrvm.compilers.opt.ir.ExceptionHandlerBasicBlock;
import org.jikesrvm.compilers.opt.ir.ExceptionHandlerBasicBlockBag;
import org.jikesrvm.compilers.opt.ir.GetField;
import org.jikesrvm.compilers.opt.ir.GetStatic;
import org.jikesrvm.compilers.opt.ir.Goto;
import org.jikesrvm.compilers.opt.ir.GuardedBinary;
import org.jikesrvm.compilers.opt.ir.GuardedUnary;
import org.jikesrvm.compilers.opt.ir.IRTools;
import org.jikesrvm.compilers.opt.ir.IfCmp;
import org.jikesrvm.compilers.opt.ir.InstanceOf;
import org.jikesrvm.compilers.opt.ir.Instruction;
import org.jikesrvm.compilers.opt.ir.LookupSwitch;
import org.jikesrvm.compilers.opt.ir.MonitorOp;
import org.jikesrvm.compilers.opt.ir.Multianewarray;
import org.jikesrvm.compilers.opt.ir.Move;
import org.jikesrvm.compilers.opt.ir.New;
import org.jikesrvm.compilers.opt.ir.NewArray;
import org.jikesrvm.compilers.opt.ir.NullCheck;
import org.jikesrvm.compilers.opt.ir.Nullary;
import org.jikesrvm.compilers.opt.ir.Operator;
import org.jikesrvm.compilers.opt.ir.Operators;
import org.jikesrvm.compilers.opt.ir.OsrBarrier;
import org.jikesrvm.compilers.opt.ir.OsrPoint;
import org.jikesrvm.compilers.opt.ir.PutField;
import org.jikesrvm.compilers.opt.ir.PutStatic;
import org.jikesrvm.compilers.opt.ir.Register;
import org.jikesrvm.compilers.opt.ir.ResultCarrier;
import org.jikesrvm.compilers.opt.ir.StoreCheck;
import org.jikesrvm.compilers.opt.ir.TableSwitch;
import org.jikesrvm.compilers.opt.ir.Trap;
import org.jikesrvm.compilers.opt.ir.TypeCheck;
import org.jikesrvm.compilers.opt.ir.Unary;
import org.jikesrvm.compilers.opt.ir.ZeroCheck;
import org.jikesrvm.compilers.opt.ir.operand.AddressConstantOperand;
import org.jikesrvm.compilers.opt.ir.operand.BranchOperand;
import org.jikesrvm.compilers.opt.ir.operand.BranchProfileOperand;
import org.jikesrvm.compilers.opt.ir.operand.ConditionOperand;
import org.jikesrvm.compilers.opt.ir.operand.ConstantOperand;
import org.jikesrvm.compilers.opt.ir.operand.DoubleConstantOperand;
import org.jikesrvm.compilers.opt.ir.operand.FloatConstantOperand;
import org.jikesrvm.compilers.opt.ir.operand.IntConstantOperand;
import org.jikesrvm.compilers.opt.ir.operand.LocationOperand;
import org.jikesrvm.compilers.opt.ir.operand.LongConstantOperand;
import org.jikesrvm.compilers.opt.ir.operand.MethodOperand;
import org.jikesrvm.compilers.opt.ir.operand.NullConstantOperand;
import org.jikesrvm.compilers.opt.ir.operand.Operand;
import org.jikesrvm.compilers.opt.ir.operand.OsrTypeInfoOperand;
import org.jikesrvm.compilers.opt.ir.operand.RegisterOperand;
import org.jikesrvm.compilers.opt.ir.operand.TrapCodeOperand;
import org.jikesrvm.compilers.opt.ir.operand.TrueGuardOperand;
import org.jikesrvm.compilers.opt.ir.operand.TypeOperand;
import org.jikesrvm.osr.OSRConstants;
import org.jikesrvm.osr.ObjectHolder;
import org.jikesrvm.osr.bytecodes.InvokeStatic;
import org.jikesrvm.runtime.Entrypoints;
import org.jikesrvm.runtime.Magic;
import org.vmmagic.pragma.NoInline;
import org.vmmagic.unboxed.Address;
import org.vmmagic.unboxed.Offset;

/**
 * This class translates from bytecode to HIR.
 * <p>
 * The only public entry point is BC2IR.generateHIR.
 * generateHIR is passed an argument GenerationContext.
 * The context is assumed to be "empty" but "initialized." Invoking
 * generateHIR on a context results in it being "filled in" with the HIR
 * for the method (and for any inlined methods) as specified by the
 * state of the context.
 * <p>
 * The basic idea is to abstractly interpret the bytecode stream,
 * translating it into a register-based IR along the way.  At each program
 * point BC2IR has an abstract stack and an abstract local variable array.
 * Based on this, and on the bytecode, it can generate instructions.
 * It also does a number of forward flow-sensitive dataflow analyses and
 * optimistic optimizations in the process. There's lots of details in
 * John Whaley's master thesis from MIT.  However, one needs to be careful
 * because this code has substantial diverged from the system described in
 * his thesis.
 * Some optimizations/features described in Johns's thesis are not implemented
 * here. Some optimizations/features implemented here are not described
 * in John's thesis.
 * In particular this code takes a different approach to JSRs (inlining them),
 * and has more advanced and effective implementation of the inlining
 * transformation. <p>
 *
 *
 * @see IRGenOptions
 * @see GenerationContext
 * @see ConvertBCtoHIR
 */
public final class BC2IR
    implements IRGenOptions, Operators, BytecodeConstants, ClassLoaderConstants, OptConstants, OSRConstants {
  /**
   * Dummy slot.
   * Used to deal with the fact the longs/doubles take
   * two words of stack space/local space to represent.
   * This field needs to be accessed by several of the IR classes,
   * but is not intended to be referenced by general client code.
   */
  public static final DummyStackSlot DUMMY = new DummyStackSlot();

  /**
   * Generate HIR as specified by the argument GenerationContext.
   * As a result of calling this method, the cfg field of the generation
   * context is populated with basic blocks and instructions.
   * Additionally, other fields of the generation context will be modified
   * to summarize what happened during IR generation.
   * <p>
   * This is the only external entry point to BC2IR.
   * <p>
   * Note: most clients should be calling methods in
   * ConvertBCtoHIR or in Inliner rather than invoking
   * BC2IR.generateHIR directly.
   *
   * @param context the generation context
   */
  public static void generateHIR(GenerationContext context) {
    new BC2IR(context).generateHIR();
  }

  //////////////////////////////////////////
  // vvv Implementation details below vvv //
  //////////////////////////////////////////
  /**
   * The generation context.
   */
  private GenerationContext gc;

  /**
   * Bytecodes for the method being generated.
   */
  private BytecodeStream bcodes;

  // Fields to support generation of instructions/blocks
  /**
   * The set of BasicBlockLEs we are generating
   */
  private BBSet blocks;

  /**
   * Bytecode index of current instruction.
   */
  private int instrIndex;

  // OSR field
  private boolean osrGuardedInline = false;

  /**
   * OSR field: TODO rework this mechanism!
   * adjustment of bcIndex of instructions because of
   * specialized bytecode.
   */
  private int bciAdjustment;

  /**
   * Last instruction generated (for ELIM_COPY_LOCALS)
   */
  private Instruction lastInstr;

  /**
   * Does basic block end here?
   */
  private boolean endOfBasicBlock;

  /**
   * Do we fall through to the next basic block?
   */
  private boolean fallThrough;

  /**
   * Current BBLE.
   */
  private BasicBlockLE currentBBLE;

  /**
   * Current simulated stack state.
   */
  private OperandStack stack;

  /**
   * Current state of local variables.
   */
  private Operand[] _localState;

  /**
   * Index of next basic block.
   */
  private int runoff;

  private Operand currentGuard;

  /**
   * Was something inlined?
   */
  private boolean inlinedSomething;

  /**
   * OSR: used for PSEUDO_InvokeStatic to recover the type info
   */
  private int param1, param2;

  /**
   * osr barrier needs type information of locals and stacks,
   * it has to be created before a _callHelper.
   * only when the call site is going to be inlined, the instruction
   * is inserted before the call site.
   */
  private Instruction lastOsrBarrier = null;

  /**
   *  Debugging with method_to_print. Switch following 2
   *  to both be non-final. Set DBG_SELECTIVE to true
   *  DBG_SELECTED will then be true when the method matches.
   *  You must also uncomment the assignment to DBG_SELECTIVE in start
   */
  private static final boolean DBG_SELECTIVE = false;
  private static final boolean DBG_SELECTED = false;

  //////////
  // End of field declarations
  //////////

  // Prevent external instantiation

  private BC2IR() {}

  /**
   * Construct the BC2IR object for the generation context.
   * After the constructor completes, we're ready to start generating
   * HIR from bytecode 0 of context.method.
   *
   * @param context the context to generate HIR into
   */
  private BC2IR(GenerationContext context) {
    start(context);
    for (int argIdx = 0, localIdx = 0; argIdx < context.arguments.length;) {
      TypeReference argType = context.arguments[argIdx].getType();
      _localState[localIdx++] = context.arguments[argIdx++];
      if (argType.isLongType() || argType.isDoubleType()) {
        _localState[localIdx++] = DUMMY;
      }
    }
    finish(context);
  }

  @NoInline
  private void start(GenerationContext context) {
    gc = context;
    // To use the following you need to change the declarations
    // in IRGenOption.java
    if (DBG_SELECTIVE) {
      if (gc.options.hasMETHOD_TO_PRINT() && gc.options.fuzzyMatchMETHOD_TO_PRINT(gc.method.toString())) {
        VM.sysWrite("Whoops! you need to uncomment the assignment to DBG_SELECTED");
        // DBG_SELECTED = true;
      } else {
        // DBG_SELECTED = false;
      }

    }

    if (context.method.isForOsrSpecialization()) {
      bcodes = context.method.getOsrSynthesizedBytecodes();
    } else {
      bcodes = context.method.getBytecodes();
    }

    // initialize the local state from context.arguments
    _localState = new Operand[context.method.getLocalWords()];

    if (context.method.isForOsrSpecialization()) {
      this.bciAdjustment = context.method.getOsrPrologueLength();
    } else {
      this.bciAdjustment = 0;
    }

    this.osrGuardedInline = VM.runningVM &&
       context.options.OSR_GUARDED_INLINING &&
       !context.method.isForOsrSpecialization() &&
       OptimizingCompiler.getAppStarted() &&
       (Controller.options != null) &&
       Controller.options.ENABLE_RECOMPILATION;
  }

  private void finish(GenerationContext context) {
    // Initialize simulated stack.
    stack = new OperandStack(context.method.getOperandWords());
    // Initialize BBSet.
    blocks = new BBSet(context, bcodes, _localState);
    // Finish preparing to generate from bytecode 0
    currentBBLE = blocks.getEntry();
    gc.prologue.insertOut(currentBBLE.block);
    if (DBG_CFG || DBG_SELECTED) {
      db("Added CFG edge from " + gc.prologue + " to " + currentBBLE.block);
    }
    runoff = currentBBLE.max;
  }

  /**
   * Main generation loop.
   */
  private void generateHIR() {
    // Constructor initialized generation state to start
    // generating from bytecode 0, so get the ball rolling.
    if (DBG_BB || DBG_SELECTED) db("bbl: " + printBlocks());
    generateFrom(0);
    // While there are more blocks that need it, pick one and generate it.
    for (currentBBLE = blocks.getNextEmptyBlock(currentBBLE); currentBBLE != null; currentBBLE =
        blocks.getNextEmptyBlock(currentBBLE)) {
      // Found a block. Set the generation state appropriately.
      currentBBLE.clearSelfRegen();
      runoff = Math.min(blocks.getNextBlockBytecodeIndex(currentBBLE), currentBBLE.max);
      if (currentBBLE.stackState == null) {
        stack.clear();
      } else {
        stack = currentBBLE.stackState.copy();
      }
      _localState = currentBBLE.copyLocalState();
      if (DBG_BB || DBG_SELECTED) db("bbl: " + printBlocks());
      // Generate it!
      generateFrom(currentBBLE.low);
    }
    // Construct initial code order, commit to recursive inlines,
    // insert any synthetic blocks.
    if (DBG_BB || DBG_SELECTED) db("doing final pass over basic blocks: " + printBlocks());
    blocks.finalPass(inlinedSomething);
  }

  // pops the length off the stack
  //
  public Instruction generateAnewarray(TypeReference arrayTypeRef, TypeReference elementTypeRef) {
    if (arrayTypeRef == null) {
      if (VM.VerifyAssertions) VM._assert(elementTypeRef != null);
      arrayTypeRef = elementTypeRef.getArrayTypeForElementType();
    }
    if (elementTypeRef == null) {
      elementTypeRef = arrayTypeRef.getArrayElementType();
    }

    RegisterOperand t = gc.temps.makeTemp(arrayTypeRef);
    t.setPreciseType();
    markGuardlessNonNull(t);
    // We can do early resolution of the array type if the element type
    // is already initialized.
    RVMType arrayType = arrayTypeRef.peekType();
    Operator op;
    TypeOperand arrayOp;

    if ((arrayType != null) && (arrayType.isInitialized() || arrayType.isInBootImage())) {
      op = NEWARRAY;
      arrayOp = makeTypeOperand(arrayType);
      t.setExtant();
    } else {
      RVMType elementType = elementTypeRef.peekType();
      if ((elementType != null) && (elementType.isInitialized() || elementType.isInBootImage())) {
        arrayType = arrayTypeRef.resolve();
        arrayType.resolve();
        arrayType.instantiate();
        op = NEWARRAY;
        arrayOp = makeTypeOperand(arrayType);
        t.setExtant();
      } else {
        op = NEWARRAY_UNRESOLVED;
        arrayOp = makeTypeOperand(arrayTypeRef);
      }
    }
    Instruction s = NewArray.create(op, t, arrayOp, popInt());
    push(t.copyD2U());
    rectifyStateWithErrorHandler();
    rectifyStateWithExceptionHandler(TypeReference.JavaLangNegativeArraySizeException);
    return s;
  }

  /**
   * Generate instructions for a basic block.
   * May discover other basic blocks that need to be generated along the way.
   *
   * @param fromIndex bytecode index to start from
   */
  private void generateFrom(int fromIndex) {
    if (DBG_BB || DBG_SELECTED) {
      db("generating code into " + currentBBLE + " with runoff " + runoff);
    }
    currentBBLE.setGenerated();
    endOfBasicBlock = fallThrough = false;
    lastInstr = null;
    bcodes.reset(fromIndex);
    while (true) {
      // Must keep currentBBLE.high up-to-date in case we try to jump into
      // the middle of the block we're currently generating.  Simply updating
      // high once endsBasicBlock is true doesn't enable us to catch this case.
      currentBBLE.high = instrIndex = bcodes.index();
      int code = bcodes.nextInstruction();
      if (DBG_BCPARSE) {
        db("parsing " +
           instrIndex +
           " " +
           code +
           " : 0x" +
           Integer.toHexString(code) +
           " " +
           ((code < JBC_name.length) ? JBC_name[code] : "unknown bytecode"));
      }
      Instruction s = null;

      lastOsrBarrier = null;

      switch (code) {
        case JBC_nop:
          break;

        case JBC_aconst_null:
          push(new NullConstantOperand());
          break;

        case JBC_iconst_m1:
        case JBC_iconst_0:
        case JBC_iconst_1:
        case JBC_iconst_2:
        case JBC_iconst_3:
        case JBC_iconst_4:
        case JBC_iconst_5:
          push(new IntConstantOperand(code - JBC_iconst_0));
          break;

        case JBC_lconst_0:
        case JBC_lconst_1:
          pushDual(new LongConstantOperand(code - JBC_lconst_0));
          break;

        case JBC_fconst_0:
          push(new FloatConstantOperand(0.f));
          break;

        case JBC_fconst_1:
          push(new FloatConstantOperand(1.f));
          break;

        case JBC_fconst_2:
          push(new FloatConstantOperand(2.f));
          break;

        case JBC_dconst_0:
          pushDual(new DoubleConstantOperand(0.));
          break;

        case JBC_dconst_1:
          pushDual(new DoubleConstantOperand(1.));
          break;

        case JBC_bipush:
          push(new IntConstantOperand(bcodes.getByteValue()));
          break;

        case JBC_sipush:
          push(new IntConstantOperand(bcodes.getShortValue()));
          break;

        case JBC_ldc:
          push(getConstantOperand(bcodes.getConstantIndex()));
          break;

        case JBC_ldc_w:
          push(getConstantOperand(bcodes.getWideConstantIndex()));
          break;

        case JBC_ldc2_w:
          pushDual(getConstantOperand(bcodes.getWideConstantIndex()));
          break;

        case JBC_iload:
          s = do_iload(bcodes.getLocalNumber());
          break;

        case JBC_lload:
          s = do_lload(bcodes.getLocalNumber());
          break;

        case JBC_fload:
          s = do_fload(bcodes.getLocalNumber());
          break;

        case JBC_dload:
          s = do_dload(bcodes.getLocalNumber());
          break;

        case JBC_aload:
          s = do_aload(bcodes.getLocalNumber());
          break;

        case JBC_iload_0:
        case JBC_iload_1:
        case JBC_iload_2:
        case JBC_iload_3:
          s = do_iload(code - JBC_iload_0);
          break;

        case JBC_lload_0:
        case JBC_lload_1:
        case JBC_lload_2:
        case JBC_lload_3:
          s = do_lload(code - JBC_lload_0);
          break;

        case JBC_fload_0:
        case JBC_fload_1:
        case JBC_fload_2:
        case JBC_fload_3:
          s = do_fload(code - JBC_fload_0);
          break;

        case JBC_dload_0:
        case JBC_dload_1:
        case JBC_dload_2:
        case JBC_dload_3:
          s = do_dload(code - JBC_dload_0);
          break;

        case JBC_aload_0:
        case JBC_aload_1:
        case JBC_aload_2:
        case JBC_aload_3:
          s = do_aload(code - JBC_aload_0);
          break;

        case JBC_iaload: {
          Operand index = popInt();
          Operand ref = pop();
          clearCurrentGuard();
          if (do_NullCheck(ref) || do_BoundsCheck(ref, index)) {
            break;
          }
          if (VM.VerifyAssertions) {
            assertIsType(ref, TypeReference.IntArray);
          }
          s = _aloadHelper(INT_ALOAD, ref, index, TypeReference.Int);
        }
        break;

        case JBC_laload: {
          Operand index = popInt();
          Operand ref = pop();
          clearCurrentGuard();
          if (do_NullCheck(ref) || do_BoundsCheck(ref, index)) {
            break;
          }
          if (VM.VerifyAssertions) {
            assertIsType(ref, TypeReference.LongArray);
          }
          s = _aloadHelper(LONG_ALOAD, ref, index, TypeReference.Long);
        }
        break;

        case JBC_faload: {
          Operand index = popInt();
          Operand ref = pop();
          clearCurrentGuard();
          if (do_NullCheck(ref) || do_BoundsCheck(ref, index)) {
            break;
          }
          if (VM.VerifyAssertions) {
            assertIsType(ref, TypeReference.FloatArray);
          }
          s = _aloadHelper(FLOAT_ALOAD, ref, index, TypeReference.Float);
        }
        break;

        case JBC_daload: {
          Operand index = popInt();
          Operand ref = pop();
          clearCurrentGuard();
          if (do_NullCheck(ref) || do_BoundsCheck(ref, index)) {
            break;
          }
          if (VM.VerifyAssertions) {
            assertIsType(ref, TypeReference.DoubleArray);
          }
          s = _aloadHelper(DOUBLE_ALOAD, ref, index, TypeReference.Double);
        }
        break;

        case JBC_aaload: {
          Operand index = popInt();
          Operand ref = pop();
          clearCurrentGuard();
          if (do_NullCheck(ref) || do_BoundsCheck(ref, index)) {
            break;
          }
          TypeReference type = getRefTypeOf(ref).getArrayElementType();
          if (VM.VerifyAssertions) VM._assert(type.isReferenceType());
          s = _aloadHelper(REF_ALOAD, ref, index, type);
        }
        break;

        case JBC_baload: {
          Operand index = popInt();
          Operand ref = pop();
          clearCurrentGuard();
          if (do_NullCheck(ref) || do_BoundsCheck(ref, index)) {
            break;
          }
          TypeReference type = getArrayTypeOf(ref);
          if (VM.VerifyAssertions) {
            VM._assert(type == TypeReference.ByteArray || type == TypeReference.BooleanArray);
          }
          if (type == TypeReference.ByteArray) {
            s = _aloadHelper(BYTE_ALOAD, ref, index, TypeReference.Byte);
          } else {
            s = _aloadHelper(UBYTE_ALOAD, ref, index, TypeReference.Boolean);
          }
        }
        break;

        case JBC_caload: {
          Operand index = popInt();
          Operand ref = pop();
          clearCurrentGuard();
          if (do_NullCheck(ref) || do_BoundsCheck(ref, index)) {
            break;
          }
          if (VM.VerifyAssertions) {
            assertIsType(ref, TypeReference.CharArray);
          }
          s = _aloadHelper(USHORT_ALOAD, ref, index, TypeReference.Char);
        }
        break;

        case JBC_saload: {
          Operand index = popInt();
          Operand ref = pop();
          clearCurrentGuard();
          if (do_NullCheck(ref) || do_BoundsCheck(ref, index)) {
            break;
          }
          if (VM.VerifyAssertions) {
            assertIsType(ref, TypeReference.ShortArray);
          }
          s = _aloadHelper(SHORT_ALOAD, ref, index, TypeReference.Short);
        }
        break;

        case JBC_istore:
          s = do_store(bcodes.getLocalNumber(), popInt());
          break;

        case JBC_lstore:
          s = do_store(bcodes.getLocalNumber(), popLong());
          break;

        case JBC_fstore:
          s = do_store(bcodes.getLocalNumber(), popFloat());
          break;

        case JBC_dstore:
          s = do_store(bcodes.getLocalNumber(), popDouble());
          break;

        case JBC_astore:
          s = do_astore(bcodes.getLocalNumber());
          break;

        case JBC_istore_0:
        case JBC_istore_1:
        case JBC_istore_2:
        case JBC_istore_3:
          s = do_store(code - JBC_istore_0, popInt());
          break;

        case JBC_lstore_0:
        case JBC_lstore_1:
        case JBC_lstore_2:
        case JBC_lstore_3:
          s = do_store(code - JBC_lstore_0, popLong());
          break;

        case JBC_fstore_0:
        case JBC_fstore_1:
        case JBC_fstore_2:
        case JBC_fstore_3:
          s = do_store(code - JBC_fstore_0, popFloat());
          break;

        case JBC_dstore_0:
        case JBC_dstore_1:
        case JBC_dstore_2:
        case JBC_dstore_3:
          s = do_store(code - JBC_dstore_0, popDouble());
          break;

        case JBC_astore_0:
        case JBC_astore_1:
        case JBC_astore_2:
        case JBC_astore_3:
          s = do_astore(code - JBC_astore_0);
          break;

        case JBC_iastore: {
          Operand val = popInt();
          Operand index = popInt();
          Operand ref = pop();
          clearCurrentGuard();
          if (do_NullCheck(ref) || do_BoundsCheck(ref, index)) {
            break;
          }
          if (VM.VerifyAssertions) {
            assertIsType(ref, TypeReference.IntArray);
          }
          s =
              AStore.create(INT_ASTORE,
                            val,
                            ref,
                            index,
                            new LocationOperand(TypeReference.Int),
                            getCurrentGuard());
        }
        break;

        case JBC_lastore: {
          Operand val = popLong();
          Operand index = popInt();
          Operand ref = pop();
          clearCurrentGuard();
          if (do_NullCheck(ref) || do_BoundsCheck(ref, index)) {
            break;
          }
          if (VM.VerifyAssertions) {
            assertIsType(ref, TypeReference.LongArray);
          }
          s =
              AStore.create(LONG_ASTORE,
                            val,
                            ref,
                            index,
                            new LocationOperand(TypeReference.Long),
                            getCurrentGuard());
        }
        break;

        case JBC_fastore: {
          Operand val = popFloat();
          Operand index = popInt();
          Operand ref = pop();
          clearCurrentGuard();
          if (do_NullCheck(ref) || do_BoundsCheck(ref, index)) {
            break;
          }
          if (VM.VerifyAssertions) {
            assertIsType(ref, TypeReference.FloatArray);
          }
          s =
              AStore.create(FLOAT_ASTORE,
                            val,
                            ref,
                            index,
                            new LocationOperand(TypeReference.Float),
                            getCurrentGuard());
        }
        break;

        case JBC_dastore: {
          Operand val = popDouble();
          Operand index = popInt();
          Operand ref = pop();
          clearCurrentGuard();
          if (do_NullCheck(ref) || do_BoundsCheck(ref, index)) {
            break;
          }
          if (VM.VerifyAssertions) {
            assertIsType(ref, TypeReference.DoubleArray);
          }
          s =
              AStore.create(DOUBLE_ASTORE,
                            val,
                            ref,
                            index,
                            new LocationOperand(TypeReference.Double),
                            getCurrentGuard());
        }
        break;

        case JBC_aastore: {
          Operand val = pop();
          Operand index = popInt();
          Operand ref = pop();
          clearCurrentGuard();
          if (do_NullCheck(ref) || do_BoundsCheck(ref, index)) {
            break;
          }
          TypeReference type = getRefTypeOf(ref).getArrayElementType();
          if (VM.VerifyAssertions) VM._assert(type.isReferenceType());
          if (do_CheckStore(ref, val, type)) {
            break;
          }
          s = AStore.create(REF_ASTORE, val, ref, index, new LocationOperand(type), getCurrentGuard());
        }
        break;

        case JBC_bastore: {
          Operand val = popInt();
          Operand index = popInt();
          Operand ref = pop();
          clearCurrentGuard();
          if (do_NullCheck(ref) || do_BoundsCheck(ref, index)) {
            break;
          }
          TypeReference type = getArrayTypeOf(ref);
          if (VM.VerifyAssertions) {
            VM._assert(type == TypeReference.ByteArray || type == TypeReference.BooleanArray);
          }
          if (type == TypeReference.ByteArray) {
            type = TypeReference.Byte;
          } else {
            type = TypeReference.Boolean;
          }
          s = AStore.create(BYTE_ASTORE, val, ref, index, new LocationOperand(type), getCurrentGuard());
        }
        break;

        case JBC_castore: {
          Operand val = popInt();
          Operand index = popInt();
          Operand ref = pop();
          clearCurrentGuard();
          if (do_NullCheck(ref) || do_BoundsCheck(ref, index)) {
            break;
          }
          if (VM.VerifyAssertions) {
            assertIsType(ref, TypeReference.CharArray);
          }
          s =
              AStore.create(SHORT_ASTORE,
                            val,
                            ref,
                            index,
                            new LocationOperand(TypeReference.Char),
                            getCurrentGuard());
        }
        break;

        case JBC_sastore: {
          Operand val = popInt();
          Operand index = popInt();
          Operand ref = pop();
          clearCurrentGuard();
          if (do_NullCheck(ref) || do_BoundsCheck(ref, index)) {
            break;
          }
          if (VM.VerifyAssertions) {
            assertIsType(ref, TypeReference.ShortArray);
          }
          s =
              AStore.create(SHORT_ASTORE,
                            val,
                            ref,
                            index,
                            new LocationOperand(TypeReference.Short),
                            getCurrentGuard());
        }
        break;

        case JBC_pop:
          stack.pop();
          break;

        case JBC_pop2:
          stack.pop2();
          break;

        case JBC_dup: {
          Operand op1 = stack.pop();
          stack.push(op1);
          s = pushCopy(op1);
        }
        break;

        case JBC_dup_x1: {
          Operand op1 = stack.pop();
          Operand op2 = stack.pop();
          stack.push(op1);
          stack.push(op2);
          s = pushCopy(op1);
        }
        break;

        case JBC_dup_x2: {
          Operand op1 = stack.pop();
          Operand op2 = stack.pop();
          Operand op3 = stack.pop();
          stack.push(op1);
          stack.push(op3);
          stack.push(op2);
          s = pushCopy(op1);
        }
        break;

        case JBC_dup2: {
          Operand op1 = stack.pop();
          Operand op2 = stack.pop();
          stack.push(op2);
          stack.push(op1);
          s = pushCopy(op2);
          if (s != null) {
            appendInstruction(s);
            s = null;
          }
          s = pushCopy(op1);
        }
        break;

        case JBC_dup2_x1: {
          Operand op1 = stack.pop();
          Operand op2 = stack.pop();
          Operand op3 = stack.pop();
          stack.push(op2);
          stack.push(op1);
          stack.push(op3);
          s = pushCopy(op2);
          if (s != null) {
            appendInstruction(s);
            s = null;
          }
          s = pushCopy(op1);
        }
        break;

        case JBC_dup2_x2: {
          Operand op1 = stack.pop();
          Operand op2 = stack.pop();
          Operand op3 = stack.pop();
          Operand op4 = stack.pop();
          stack.push(op2);
          stack.push(op1);
          stack.push(op4);
          stack.push(op3);
          s = pushCopy(op2);
          if (s != null) {
            appendInstruction(s);
            s = null;
          }
          s = pushCopy(op1);
        }
        break;

        case JBC_swap: {
          Operand op1 = stack.pop();
          Operand op2 = stack.pop();
          stack.push(op1);
          stack.push(op2);
        }
        break;

        case JBC_iadd: {
          Operand op2 = popInt();
          Operand op1 = popInt();
          s = _binaryHelper(INT_ADD, op1, op2, TypeReference.Int);
        }
        break;

        case JBC_ladd: {
          Operand op2 = popLong();
          Operand op1 = popLong();
          s = _binaryDualHelper(LONG_ADD, op1, op2, TypeReference.Long);
        }
        break;

        case JBC_fadd: {
          Operand op2 = popFloat();
          Operand op1 = popFloat();
          s = _binaryHelper(FLOAT_ADD, op1, op2, TypeReference.Float);
        }
        break;

        case JBC_dadd: {
          Operand op2 = popDouble();
          Operand op1 = popDouble();
          s = _binaryDualHelper(DOUBLE_ADD, op1, op2, TypeReference.Double);
        }
        break;

        case JBC_isub: {
          Operand op2 = popInt();
          Operand op1 = popInt();
          s = _binaryHelper(INT_SUB, op1, op2, TypeReference.Int);
        }
        break;

        case JBC_lsub: {
          Operand op2 = popLong();
          Operand op1 = popLong();
          s = _binaryDualHelper(LONG_SUB, op1, op2, TypeReference.Long);
        }
        break;

        case JBC_fsub: {
          Operand op2 = popFloat();
          Operand op1 = popFloat();
          s = _binaryHelper(FLOAT_SUB, op1, op2, TypeReference.Float);
        }
        break;

        case JBC_dsub: {
          Operand op2 = popDouble();
          Operand op1 = popDouble();
          s = _binaryDualHelper(DOUBLE_SUB, op1, op2, TypeReference.Double);
        }
        break;

        case JBC_imul: {
          Operand op2 = popInt();
          Operand op1 = popInt();
          s = _binaryHelper(INT_MUL, op1, op2, TypeReference.Int);
        }
        break;

        case JBC_lmul: {
          Operand op2 = popLong();
          Operand op1 = popLong();
          s = _binaryDualHelper(LONG_MUL, op1, op2, TypeReference.Long);
        }
        break;

        case JBC_fmul: {
          Operand op2 = popFloat();
          Operand op1 = popFloat();
          s = _binaryHelper(FLOAT_MUL, op1, op2, TypeReference.Float);
        }
        break;

        case JBC_dmul: {
          Operand op2 = popDouble();
          Operand op1 = popDouble();
          s = _binaryDualHelper(DOUBLE_MUL, op1, op2, TypeReference.Double);
        }
        break;

        case JBC_idiv: {
          clearCurrentGuard();
          Operand op2 = popInt();
          Operand op1 = popInt();
          if (do_IntZeroCheck(op2)) {
            break;
          }
          s = _guardedBinaryHelper(INT_DIV, op1, op2, getCurrentGuard(), TypeReference.Int);
        }
        break;

        case JBC_ldiv: {
          clearCurrentGuard();
          Operand op2 = popLong();
          Operand op1 = popLong();
          if (do_LongZeroCheck(op2)) {
            break;
          }
          s = _guardedBinaryDualHelper(LONG_DIV, op1, op2, getCurrentGuard(), TypeReference.Long);
        }
        break;

        case JBC_fdiv: {
          Operand op2 = popFloat();
          Operand op1 = popFloat();
          s = _binaryHelper(FLOAT_DIV, op1, op2, TypeReference.Float);
        }
        break;

        case JBC_ddiv: {
          Operand op2 = popDouble();
          Operand op1 = popDouble();
          s = _binaryDualHelper(DOUBLE_DIV, op1, op2, TypeReference.Double);
        }
        break;

        case JBC_irem: {
          clearCurrentGuard();
          Operand op2 = popInt();
          Operand op1 = popInt();
          if (do_IntZeroCheck(op2)) {
            break;
          }
          s = _guardedBinaryHelper(INT_REM, op1, op2, getCurrentGuard(), TypeReference.Int);
        }
        break;

        case JBC_lrem: {
          clearCurrentGuard();
          Operand op2 = popLong();
          Operand op1 = popLong();
          if (do_LongZeroCheck(op2)) {
            break;
          }
          s = _guardedBinaryDualHelper(LONG_REM, op1, op2, getCurrentGuard(), TypeReference.Long);
        }
        break;

        case JBC_frem: {
          Operand op2 = popFloat();
          Operand op1 = popFloat();
          s = _binaryHelper(FLOAT_REM, op1, op2, TypeReference.Float);
        }
        break;

        case JBC_drem: {
          Operand op2 = popDouble();
          Operand op1 = popDouble();
          s = _binaryDualHelper(DOUBLE_REM, op1, op2, TypeReference.Double);
        }
        break;

        case JBC_ineg:
          s = _unaryHelper(INT_NEG, popInt(), TypeReference.Int);
          break;

        case JBC_lneg:
          s = _unaryDualHelper(LONG_NEG, popLong(), TypeReference.Long);
          break;

        case JBC_fneg:
          s = _unaryHelper(FLOAT_NEG, popFloat(), TypeReference.Float);
          break;

        case JBC_dneg:
          s = _unaryDualHelper(DOUBLE_NEG, popDouble(), TypeReference.Double);
          break;

        case JBC_ishl: {
          Operand op2 = popShiftInt(false);
          Operand op1 = popInt();
          s = _binaryHelper(INT_SHL, op1, op2, TypeReference.Int);
        }
        break;

        case JBC_lshl: {
          Operand op2 = popShiftInt(true);
          Operand op1 = popLong();
          s = _binaryDualHelper(LONG_SHL, op1, op2, TypeReference.Long);
        }
        break;

        case JBC_ishr: {
          Operand op2 = popShiftInt(false);
          Operand op1 = popInt();
          s = _binaryHelper(INT_SHR, op1, op2, TypeReference.Int);
        }
        break;

        case JBC_lshr: {
          Operand op2 = popShiftInt(true);
          Operand op1 = popLong();
          s = _binaryDualHelper(LONG_SHR, op1, op2, TypeReference.Long);
        }
        break;

        case JBC_iushr: {
          Operand op2 = popShiftInt(false);
          Operand op1 = popInt();
          s = _binaryHelper(INT_USHR, op1, op2, TypeReference.Int);
        }
        break;

        case JBC_lushr: {
          Operand op2 = popShiftInt(true);
          Operand op1 = popLong();
          s = _binaryDualHelper(LONG_USHR, op1, op2, TypeReference.Long);
        }
        break;

        case JBC_iand: {
          Operand op2 = popInt();
          Operand op1 = popInt();
          s = _binaryHelper(INT_AND, op1, op2, TypeReference.Int);
        }
        break;

        case JBC_land: {
          Operand op2 = popLong();
          Operand op1 = popLong();
          s = _binaryDualHelper(LONG_AND, op1, op2, TypeReference.Long);
        }
        break;

        case JBC_ior: {
          Operand op2 = popInt();
          Operand op1 = popInt();
          s = _binaryHelper(INT_OR, op1, op2, TypeReference.Int);
        }
        break;

        case JBC_lor: {
          Operand op2 = popLong();
          Operand op1 = popLong();
          s = _binaryDualHelper(LONG_OR, op1, op2, TypeReference.Long);
        }
        break;

        case JBC_ixor: {
          Operand op2 = popInt();
          Operand op1 = popInt();
          s = _binaryHelper(INT_XOR, op1, op2, TypeReference.Int);
        }
        break;

        case JBC_lxor: {
          Operand op2 = popLong();
          Operand op1 = popLong();
          s = _binaryDualHelper(LONG_XOR, op1, op2, TypeReference.Long);
        }
        break;

        case JBC_iinc: {
          int index = bcodes.getLocalNumber();
          s = do_iinc(index, bcodes.getIncrement());
        }
        break;

        case JBC_i2l:
          s = _unaryDualHelper(INT_2LONG, popInt(), TypeReference.Long);
          break;

        case JBC_i2f:
          s = _unaryHelper(INT_2FLOAT, popInt(), TypeReference.Float);
          break;

        case JBC_i2d:
          s = _unaryDualHelper(INT_2DOUBLE, popInt(), TypeReference.Double);
          break;

        case JBC_l2i:
          s = _unaryHelper(LONG_2INT, popLong(), TypeReference.Int);
          break;

        case JBC_l2f:
          s = _unaryHelper(LONG_2FLOAT, popLong(), TypeReference.Float);
          break;

        case JBC_l2d:
          s = _unaryDualHelper(LONG_2DOUBLE, popLong(), TypeReference.Double);
          break;

        case JBC_f2i:
          s = _unaryHelper(FLOAT_2INT, popFloat(), TypeReference.Int);
          break;

        case JBC_f2l:
          s = _unaryDualHelper(FLOAT_2LONG, popFloat(), TypeReference.Long);
          break;

        case JBC_f2d:
          s = _unaryDualHelper(FLOAT_2DOUBLE, popFloat(), TypeReference.Double);
          break;

        case JBC_d2i:
          s = _unaryHelper(DOUBLE_2INT, popDouble(), TypeReference.Int);
          break;

        case JBC_d2l:
          s = _unaryDualHelper(DOUBLE_2LONG, popDouble(), TypeReference.Long);
          break;

        case JBC_d2f:
          s = _unaryHelper(DOUBLE_2FLOAT, popDouble(), TypeReference.Float);
          break;

        case JBC_int2byte:
          s = _unaryHelper(INT_2BYTE, popInt(), TypeReference.Byte);
          break;

        case JBC_int2char:
          s = _unaryHelper(INT_2USHORT, popInt(), TypeReference.Char);
          break;

        case JBC_int2short:
          s = _unaryHelper(INT_2SHORT, popInt(), TypeReference.Short);
          break;

        case JBC_lcmp: {
          Operand op2 = popLong();
          Operand op1 = popLong();
          s = _binaryHelper(LONG_CMP, op1, op2, TypeReference.Int);
        }
        break;

        case JBC_fcmpl: {
          Operand op2 = popFloat();
          Operand op1 = popFloat();
          s = _binaryHelper(FLOAT_CMPL, op1, op2, TypeReference.Int);
        }
        break;

        case JBC_fcmpg: {
          Operand op2 = popFloat();
          Operand op1 = popFloat();
          s = _binaryHelper(FLOAT_CMPG, op1, op2, TypeReference.Int);
        }
        break;

        case JBC_dcmpl: {
          Operand op2 = popDouble();
          Operand op1 = popDouble();
          s = _binaryHelper(DOUBLE_CMPL, op1, op2, TypeReference.Int);
        }
        break;

        case JBC_dcmpg: {
          Operand op2 = popDouble();
          Operand op1 = popDouble();
          s = _binaryHelper(DOUBLE_CMPG, op1, op2, TypeReference.Int);
        }
        break;

        case JBC_ifeq:
          s = _intIfHelper(ConditionOperand.EQUAL());
          break;

        case JBC_ifne:
          s = _intIfHelper(ConditionOperand.NOT_EQUAL());
          break;

        case JBC_iflt:
          s = _intIfHelper(ConditionOperand.LESS());
          break;

        case JBC_ifge:
          s = _intIfHelper(ConditionOperand.GREATER_EQUAL());
          break;

        case JBC_ifgt:
          s = _intIfHelper(ConditionOperand.GREATER());
          break;

        case JBC_ifle:
          s = _intIfHelper(ConditionOperand.LESS_EQUAL());
          break;

        case JBC_if_icmpeq:
          s = _intIfCmpHelper(ConditionOperand.EQUAL());
          break;

        case JBC_if_icmpne:
          s = _intIfCmpHelper(ConditionOperand.NOT_EQUAL());
          break;

        case JBC_if_icmplt:
          s = _intIfCmpHelper(ConditionOperand.LESS());
          break;

        case JBC_if_icmpge:
          s = _intIfCmpHelper(ConditionOperand.GREATER_EQUAL());
          break;

        case JBC_if_icmpgt:
          s = _intIfCmpHelper(ConditionOperand.GREATER());
          break;

        case JBC_if_icmple:
          s = _intIfCmpHelper(ConditionOperand.LESS_EQUAL());
          break;

        case JBC_if_acmpeq:
          s = _refIfCmpHelper(ConditionOperand.EQUAL());
          break;

        case JBC_if_acmpne:
          s = _refIfCmpHelper(ConditionOperand.NOT_EQUAL());
          break;

        case JBC_goto: {
          int offset = bcodes.getBranchOffset();
          if (offset != 3) {
            // skip generating frivolous goto's
            s = _gotoHelper(offset);
          }
        }
        break;

        case JBC_jsr:
          s = _jsrHelper(bcodes.getBranchOffset());
          break;

        case JBC_ret:
          s = _retHelper(bcodes.getLocalNumber());
          break;

        case JBC_tableswitch: {
          bcodes.alignSwitch();
          Operand op0 = popInt();
          int defaultoff = bcodes.getDefaultSwitchOffset();
          int low = bcodes.getLowSwitchValue();
          int high = bcodes.getHighSwitchValue();
          int number = high - low + 1;
          if (CF_TABLESWITCH && op0 instanceof IntConstantOperand) {
            int v1 = ((IntConstantOperand) op0).value;
            int match = bcodes.computeTableSwitchOffset(v1, low, high);
            int offset = match == 0 ? defaultoff : match;
            bcodes.skipTableSwitchOffsets(number);
            if (DBG_CF) {
              db("changed tableswitch to goto because index (" + v1 + ") is constant");
            }
            s = _gotoHelper(offset);
            break;
          }
          s =
              TableSwitch.create(TABLESWITCH,
                                 op0,
                                 null,
                                 null,
                                 new IntConstantOperand(low),
                                 new IntConstantOperand(high),
                                 generateTarget(defaultoff),
                                 null,
                                 number * 2);
          for (int i = 0; i < number; ++i) {
            TableSwitch.setTarget(s, i, generateTarget(bcodes.getTableSwitchOffset(i)));
          }
          bcodes.skipTableSwitchOffsets(number);

          // Set branch probabilities
          SwitchBranchProfile sp = gc.getSwitchProfile(instrIndex - bciAdjustment);
          if (sp == null) {
            float approxProb = 1.0f / (number + 1); // number targets + default
            TableSwitch.setDefaultBranchProfile(s, new BranchProfileOperand(approxProb));
            for (int i = 0; i < number; ++i) {
              TableSwitch.setBranchProfile(s, i, new BranchProfileOperand(approxProb));
            }
          } else {
            TableSwitch.setDefaultBranchProfile(s, new BranchProfileOperand(sp.getDefaultProbability()));
            for (int i = 0; i < number; ++i) {
              TableSwitch.setBranchProfile(s, i, new BranchProfileOperand(sp.getCaseProbability(i)));
            }
          }
        }
        break;

        case JBC_lookupswitch: {
          bcodes.alignSwitch();
          Operand op0 = popInt();
          int defaultoff = bcodes.getDefaultSwitchOffset();
          int numpairs = bcodes.getSwitchLength();
          if (numpairs == 0) {
            s = _gotoHelper(defaultoff);
            break;
          }
          if (CF_LOOKUPSWITCH && op0 instanceof IntConstantOperand) {
            int v1 = ((IntConstantOperand) op0).value;
            int match = bcodes.computeLookupSwitchOffset(v1, numpairs);
            int offset = match == 0 ? defaultoff : match;
            bcodes.skipLookupSwitchPairs(numpairs);
            if (DBG_CF) {
              db("changed lookupswitch to goto because index (" + v1 + ") is constant");
            }
            s = _gotoHelper(offset);
            break;
          }

          // Construct switch
          s = LookupSwitch.create(LOOKUPSWITCH, op0, null, null, generateTarget(defaultoff), null, numpairs * 3);
          for (int i = 0; i < numpairs; ++i) {
            LookupSwitch.setMatch(s, i, new IntConstantOperand(bcodes.getLookupSwitchValue(i)));
            LookupSwitch.setTarget(s, i, generateTarget(bcodes.getLookupSwitchOffset(i)));
          }
          bcodes.skipLookupSwitchPairs(numpairs);

          // Set branch probabilities
          SwitchBranchProfile sp = gc.getSwitchProfile(instrIndex - bciAdjustment);
          if (sp == null) {
            float approxProb = 1.0f / (float) (numpairs + 1); // num targets + default
            LookupSwitch.setDefaultBranchProfile(s, new BranchProfileOperand(approxProb));
            for (int i = 0; i < numpairs; ++i) {
              LookupSwitch.setBranchProfile(s, i, new BranchProfileOperand(approxProb));
            }
          } else {
            LookupSwitch.setDefaultBranchProfile(s, new BranchProfileOperand(sp.getDefaultProbability()));
            for (int i = 0; i < numpairs; ++i) {
              LookupSwitch.setBranchProfile(s, i, new BranchProfileOperand(sp.getCaseProbability(i)));
            }
          }
        }
        break;

        case JBC_ireturn:
          _returnHelper(INT_MOVE, popInt());
          break;

        case JBC_lreturn:
          _returnHelper(LONG_MOVE, popLong());
          break;

        case JBC_freturn:
          _returnHelper(FLOAT_MOVE, popFloat());
          break;

        case JBC_dreturn:
          _returnHelper(DOUBLE_MOVE, popDouble());
          break;

        case JBC_areturn: {
          Operand op0 = popRef();
          if (VM.VerifyAssertions && !op0.isDefinitelyNull()) {
            TypeReference retType = op0.getType();
            assertIsAssignable(gc.method.getReturnType(), retType);
          }
          _returnHelper(REF_MOVE, op0);
        }
        break;

        case JBC_return:
          _returnHelper(null, null);
          break;

        case JBC_getstatic: {
          // field resolution
          FieldReference ref = bcodes.getFieldReference();
          boolean unresolved = ref.needsDynamicLink(bcodes.getMethod());
          LocationOperand fieldOp = makeStaticFieldRef(ref);
          Operand offsetOp;
          TypeReference fieldType = ref.getFieldContentsType();
          RegisterOperand t = gc.temps.makeTemp(fieldType);
          if (unresolved) {
            RegisterOperand offsetrop = gc.temps.makeTempOffset();
            appendInstruction(Unary.create(RESOLVE_MEMBER, offsetrop.copyRO(), fieldOp.copy()));
            offsetOp = offsetrop;
            rectifyStateWithErrorHandler();
          } else {
            RVMField field = ref.peekResolvedField();
            offsetOp = new AddressConstantOperand(field.getOffset());

            // use results of field analysis to refine type of result
            RVMType ft = fieldType.peekType();
            if (ft != null && ft.isClassType()) {
              TypeReference concreteType = FieldAnalysis.getConcreteType(field);
              if (concreteType != null) {
                if (concreteType == fieldType) {
                  t.setDeclaredType();
                  t.setPreciseType();
                } else {
                  fieldType = concreteType;
                  t.setPreciseType(concreteType);
                }
              }
            }

            // optimization: if the field is final and either
            // initialized or we're writing the bootimage and in an
            // RVM bootimage class, then get the value at compile
            // time.
            if (gc.options.SIMPLIFY_CHASE_FINAL_FIELDS && field.isFinal()) {
              RVMClass declaringClass = field.getDeclaringClass();
              if (declaringClass.isInitialized() || declaringClass.isInBootImage()) {
                try {
                  ConstantOperand rhs = StaticFieldReader.getStaticFieldValue(field);
                  // VM.sysWrite("Replaced getstatic of "+field+" with "+rhs+"\n");
                  push(rhs, fieldType);
                  break;
                } catch (NoSuchFieldException e) {
                  if (VM.runningVM) { // this is unexpected
                    throw new Error("Unexpected exception", e);
                  } else {
                    // Field not found during bootstrap due to chasing a field
                    // only valid in the bootstrap JVM
                  }
                }
              }
            } else if (field.isRuntimeFinal()) {
              if (VM.VerifyAssertions) VM._assert(fieldType.isBooleanType());
              boolean rhsBool = field.getRuntimeFinalValue();
              push(new IntConstantOperand(rhsBool? 1 : 0));
              break;
            }
          }

          s = GetStatic.create(GETSTATIC, t, offsetOp, fieldOp);
          if (fieldOp.mayBeVolatile()) {
              appendInstruction(s);
              s = Empty.create(READ_CEILING);
          }

          push(t.copyD2U(), fieldType);
        }
        break;

        case JBC_putstatic: {
          // field resolution
          FieldReference ref = bcodes.getFieldReference();
          boolean unresolved = ref.needsDynamicLink(bcodes.getMethod());
          LocationOperand fieldOp = makeStaticFieldRef(ref);
          Operand offsetOp;
          if (unresolved) {
            RegisterOperand offsetrop = gc.temps.makeTempOffset();
            appendInstruction(Unary.create(RESOLVE_MEMBER, offsetrop.copyRO(), fieldOp.copy()));
            offsetOp = offsetrop;
            rectifyStateWithErrorHandler();
          } else {
            RVMField field = ref.peekResolvedField();
            offsetOp = new AddressConstantOperand(field.getOffset());
          }

          TypeReference fieldType = ref.getFieldContentsType();
          Operand r = pop(fieldType);
          if (fieldOp.mayBeVolatile()) {
              appendInstruction(Empty.create(WRITE_FLOOR));
          }
          s = PutStatic.create(PUTSTATIC, r, offsetOp, fieldOp);
          if (fieldOp.mayBeVolatile()) {
            appendInstruction(s);
            s = Empty.create(FENCE);
          }
        }
        break;

        case JBC_getfield: {
          // field resolution
          FieldReference ref = bcodes.getFieldReference();
          boolean unresolved = ref.needsDynamicLink(bcodes.getMethod());
          LocationOperand fieldOp = makeInstanceFieldRef(ref);
          Operand offsetOp;
          TypeReference fieldType = ref.getFieldContentsType();
          RVMField field = null;
          RegisterOperand t = gc.temps.makeTemp(fieldType);
          if (unresolved) {
            RegisterOperand offsetrop = gc.temps.makeTempOffset();
            appendInstruction(Unary.create(RESOLVE_MEMBER, offsetrop.copyRO(), fieldOp.copy()));
            offsetOp = offsetrop;
            rectifyStateWithErrorHandler();
          } else {
            field = ref.peekResolvedField();
            offsetOp = new AddressConstantOperand(field.getOffset());

            // use results of field analysis to refine type.
            RVMType ft = fieldType.peekType();
            if (ft != null && ft.isClassType()) {
              TypeReference concreteType = FieldAnalysis.getConcreteType(field);
              if (concreteType != null) {
                if (concreteType == fieldType) {
                  t.setDeclaredType();
                  t.setPreciseType();
                } else {
                  fieldType = concreteType;
                  t.setType(concreteType);
                  t.setPreciseType();
                }
              }
            }
          }

          Operand op1 = pop();
          clearCurrentGuard();
          if (do_NullCheck(op1)) {
            break;
          }

          // optimization: if the field is final and referenced by a
          // constant reference then get the value at compile time.
          // NB avoid String fields
          if (op1.isConstant() && field.isFinal()) {
            try {
              ConstantOperand rhs =
                  StaticFieldReader.getFieldValueAsConstant(field, op1.asObjectConstant().value);
              push(rhs, fieldType);
              break;
            } catch (NoSuchFieldException e) {
              if (VM.runningVM) { // this is unexpected
                throw new Error("Unexpected exception", e);
              } else {
                // Field not found during bootstrap due to chasing a field
                // only valid in the bootstrap JVM
              }
            }
          }

          s = GetField.create(GETFIELD, t, op1, offsetOp, fieldOp, getCurrentGuard());
          if (fieldOp.mayBeVolatile()) {
              appendInstruction(s);
              s = Empty.create(READ_CEILING);
          }

          push(t.copyD2U(), fieldType);
        }
        break;

        case JBC_putfield: {
          // field resolution
          FieldReference ref = bcodes.getFieldReference();
          boolean unresolved = ref.needsDynamicLink(bcodes.getMethod());
          LocationOperand fieldOp = makeInstanceFieldRef(ref);
          TypeReference fieldType = ref.getFieldContentsType();
          Operand offsetOp;
          if (unresolved) {
            RegisterOperand offsetrop = gc.temps.makeTempOffset();
            appendInstruction(Unary.create(RESOLVE_MEMBER, offsetrop.copyRO(), fieldOp.copy()));
            offsetOp = offsetrop;
            rectifyStateWithErrorHandler();
          } else {
            RVMField field = ref.peekResolvedField();
            offsetOp = new AddressConstantOperand(field.getOffset());
          }

          Operand val = pop(fieldType);
          Operand obj = popRef();
          clearCurrentGuard();
          if (do_NullCheck(obj)) {
            break;
          }

          if (fieldOp.mayBeVolatile()) {
              appendInstruction(Empty.create(WRITE_FLOOR));
          }
          s = PutField.create(PUTFIELD, val, obj, offsetOp, fieldOp, getCurrentGuard());
          if (fieldOp.mayBeVolatile()) {
            appendInstruction(s);
            s = Empty.create(FENCE);
          }
        }
        break;

        case JBC_invokevirtual: {
          MethodReference ref = bcodes.getMethodReference();

          // See if this is a magic method (Address, Word, etc.)
          // If it is, generate the inline code and we are done.
          if (ref.isMagic()) {
            boolean generated = GenerateMagic.generateMagic(this, gc, ref);
            if (generated) break; // all done.
          }

          /* just create an osr barrier right before _callHelper
           * changes the states of locals and stacks.
           */
          if (this.osrGuardedInline) {
            lastOsrBarrier = _createOsrBarrier();
          }

          if (ref.isMiranda()) {
            // An invokevirtual that is really an invokeinterface.
            s = _callHelper(ref, MethodOperand.INTERFACE(ref, null));
            if (s == null)
              break;
            Operand receiver = Call.getParam(s, 0);
            RVMClass receiverType = (RVMClass) receiver.getType().peekType();
            // null check on this parameter of call
            clearCurrentGuard();
            if (do_NullCheck(receiver)) {
              // call will always raise null pointer exception
              s = null;
              break;
            }
            Call.setGuard(s, getCurrentGuard());

            // Attempt to resolve the interface call to a particular virtual method.
            // This is independent of whether or not the static type of the receiver is
            // known to implement the interface and it is not that case that being able
            // to prove one implies the other.
            RVMMethod vmeth = null;
            if (receiverType != null && receiverType.isInitialized() && !receiverType.isInterface()) {
              vmeth = ClassLoaderProxy.lookupMethod(receiverType, ref);
            }
            if (vmeth != null) {
              MethodReference vmethRef = vmeth.getMemberRef().asMethodReference();
              MethodOperand mop = MethodOperand.VIRTUAL(vmethRef, vmeth);
              if (receiver.isConstant() || (receiver.isRegister() && receiver.asRegister().isPreciseType())) {
                mop.refine(vmeth, true);
              }
              Call.setMethod(s, mop);
              boolean unresolved = vmethRef.needsDynamicLink(bcodes.getMethod());
              if (unresolved) {
                RegisterOperand offsetrop = gc.temps.makeTempOffset();
                appendInstruction(Unary.create(RESOLVE_MEMBER, offsetrop.copyRO(), Call.getMethod(s).copy()));
                Call.setAddress(s, offsetrop);
                rectifyStateWithErrorHandler();
              } else {
                Call.setAddress(s, new AddressConstantOperand(vmeth.getOffset()));
              }

              // Attempt to inline virtualized call.
              if (maybeInlineMethod(shouldInline(s,
                                                 receiver.isConstant() ||
                                                 (receiver.isRegister() && receiver.asRegister().isExtant()),
                                                 instrIndex - bciAdjustment), s)) {
                return;
              }
            }

          } else {
            // A normal invokevirtual.  Create call instruction.
            boolean unresolved = ref.needsDynamicLink(bcodes.getMethod());
            RVMMethod target = ref.peekResolvedMethod();
            MethodOperand methOp = MethodOperand.VIRTUAL(ref, target);

            s = _callHelper(ref, methOp);
            if (s == null)
              break;

            // Handle possibility of dynamic linking.
            // Must be done before null_check!
            if (unresolved) {
              RegisterOperand offsetrop = gc.temps.makeTempOffset();
              appendInstruction(Unary.create(RESOLVE_MEMBER, offsetrop.copyRO(), Call.getMethod(s).copy()));
              Call.setAddress(s, offsetrop);
              rectifyStateWithErrorHandler();
            } else {
              if (VM.VerifyAssertions) VM._assert(target != null);
              Call.setAddress(s, new AddressConstantOperand(target.getOffset()));
            }

            // null check receiver
            Operand receiver = Call.getParam(s, 0);
            clearCurrentGuard();
            if (do_NullCheck(receiver)) {
              // call will always raise null pointer exception
              s = null;
              break;
            }
            Call.setGuard(s, getCurrentGuard());

            // Use compile time type of receiver to try reduce the number
            // of targets.
            // If we succeed, we'll update meth and s's method operand.
            boolean isExtant = false;
            boolean isPreciseType = false;
            TypeReference tr = null;
            if (receiver.isRegister()) {
              RegisterOperand rop = receiver.asRegister();
              isExtant = rop.isExtant();
              isPreciseType = rop.isPreciseType();
              tr = rop.getType();
            } else {
              isExtant = true;
              isPreciseType = true;
              tr = receiver.getType();
            }
            RVMType type = tr.peekType();
            if (type != null && type.isResolved()) {
              if (type.isClassType()) {
                RVMMethod vmeth = target;
                if (target == null || type != target.getDeclaringClass()) {
                  vmeth = ClassLoaderProxy.lookupMethod(type.asClass(), ref);
                }
                if (vmeth != null) {
                  methOp.refine(vmeth, isPreciseType || type.asClass().isFinal());
                }
              } else {
                // Array: will always be calling the method defined in java.lang.Object
                if (VM.VerifyAssertions) VM._assert(target != null, "Huh?  Target method must already be resolved if receiver is array");
                methOp.refine(target, true);
              }
            }

            // Consider inlining it.
            if (maybeInlineMethod(shouldInline(s, isExtant, instrIndex - bciAdjustment), s)) {
              return;
            }
          }

          // noninlined CALL must be treated as potential throw of anything
          rectifyStateWithExceptionHandlers();
        }
        break;

        case JBC_invokespecial: {
          MethodReference ref = bcodes.getMethodReference();
          RVMMethod target = ref.resolveInvokeSpecial();

          /* just create an osr barrier right before _callHelper
           * changes the states of locals and stacks.
           */
          if (this.osrGuardedInline) {
            lastOsrBarrier = _createOsrBarrier();
          }

          s = _callHelper(ref, MethodOperand.SPECIAL(ref, target));
          if (s == null)
            break;

          // Handle possibility of dynamic linking. Must be done before null_check!
          // NOTE: different definition of unresolved due to semantics of invokespecial.
          if (target == null) {
            RegisterOperand offsetrop = gc.temps.makeTempOffset();
            appendInstruction(Unary.create(RESOLVE_MEMBER, offsetrop.copyRO(), Call.getMethod(s).copy()));
            Call.setAddress(s, offsetrop);
            rectifyStateWithErrorHandler();
          } else {
            Call.setAddress(s, new AddressConstantOperand(target.getOffset()));
          }

          // null check receiver
          Operand receiver = Call.getParam(s, 0);
          clearCurrentGuard();
          if (do_NullCheck(receiver)) {
            // call will always raise null pointer exception
            s = null;
            break;
          }
          Call.setGuard(s, getCurrentGuard());

          // Consider inlining it.
          if (maybeInlineMethod(shouldInline(s, false, instrIndex - bciAdjustment), s)) {
            return;
          }

          // noninlined CALL must be treated as potential throw of anything
          rectifyStateWithExceptionHandlers();
        }
        break;

        case JBC_invokestatic: {
          MethodReference ref = bcodes.getMethodReference();

          // See if this is a magic method (Magic, Address, Word, etc.)
          // If it is, generate the inline code and we are done.
          if (ref.isMagic()) {
            boolean generated = GenerateMagic.generateMagic(this, gc, ref);
            if (generated) break;
          }

          // A non-magical invokestatic.  Create call instruction.
          boolean unresolved = ref.needsDynamicLink(bcodes.getMethod());
          RVMMethod target = ref.peekResolvedMethod();

          /* just create an osr barrier right before _callHelper
          * changes the states of locals and stacks.
          */
          if (this.osrGuardedInline) {
            lastOsrBarrier = _createOsrBarrier();
          }

          s = _callHelper(ref, MethodOperand.STATIC(ref, target));
          if (s == null)
            break;

          if (Call.conforms(s)) {
            MethodOperand methOp = Call.getMethod(s);
            if (methOp.getTarget() == target) {
              // Handle possibility of dynamic linking.
              if (unresolved) {
                RegisterOperand offsetrop = gc.temps.makeTempOffset();
                appendInstruction(Unary.create(RESOLVE_MEMBER, offsetrop.copyRO(), Call.getMethod(s).copy()));
                Call.setAddress(s, offsetrop);
                rectifyStateWithErrorHandler();
              } else {
                Call.setAddress(s, new AddressConstantOperand(target.getOffset()));
              }

              // Consider inlining it.
              if (maybeInlineMethod(shouldInline(s, false, instrIndex - bciAdjustment), s)) {
                return;
              }
            }
          }
          // noninlined CALL must be treated as potential throw of anything
          rectifyStateWithExceptionHandlers();
        }
        break;

        case JBC_invokeinterface: {
          MethodReference ref = bcodes.getMethodReference();
          bcodes.alignInvokeInterface();
          RVMMethod resolvedMethod = null;
          resolvedMethod = ref.peekInterfaceMethod();

          /* just create an osr barrier right before _callHelper
           * changes the states of locals and stacks.
           */
          if (this.osrGuardedInline) {
            lastOsrBarrier = _createOsrBarrier();
          }

          s = _callHelper(ref, MethodOperand.INTERFACE(ref, resolvedMethod));
          if (s == null)
            break;

          Operand receiver = Call.getParam(s, 0);
          RVMClass receiverType = (RVMClass) receiver.getType().peekType();
          boolean requiresImplementsTest = VM.BuildForIMTInterfaceInvocation;

          // Invokeinterface requires a dynamic type check
          // to ensure that the receiver object actually
          // implements the interface.  This is necessary
          // because the verifier does not detect incompatible class changes.
          // Depending on the implementation of interface dispatching
          // we are using, we may have to make this test explicit
          // in the calling sequence if we can't prove at compile time
          // that it is not needed.
          if (requiresImplementsTest && resolvedMethod == null) {
            // Sigh.  Can't even resolve the reference to figure out what interface
            // method we are trying to call. Therefore we must make generate a call
            // to an out-of-line typechecking routine to handle it at runtime.
            RVMMethod target = Entrypoints.unresolvedInvokeinterfaceImplementsTestMethod;
            Instruction callCheck =
              Call.create2(CALL,
                           null,
                           new AddressConstantOperand(target.getOffset()),
                           MethodOperand.STATIC(target),
                           new IntConstantOperand(ref.getId()),
                           receiver.copy());
            if (gc.options.H2L_NO_CALLEE_EXCEPTIONS) {
              callCheck.markAsNonPEI();
            }

            appendInstruction(callCheck);
            callCheck.bcIndex = RUNTIME_SERVICES_BCI;

            requiresImplementsTest = false; // the above call subsumes the test
            rectifyStateWithErrorHandler(); // Can raise incompatible class change error.
          }

          // null check on this parameter of call. Must be done after dynamic linking!
          clearCurrentGuard();
          if (do_NullCheck(receiver)) {
            // call will always raise null pointer exception
            s = null;
            break;
          }
          Call.setGuard(s, getCurrentGuard());

          if (requiresImplementsTest) {
            // We know what interface method the program wants to invoke.
            // Attempt to avoid inserting the type check by seeing if the
            // known static type of the receiver implements the desired interface.
            RVMType interfaceType = resolvedMethod.getDeclaringClass();
            if (receiverType != null && receiverType.isResolved() && !receiverType.isInterface()) {
              byte doesImplement =
                ClassLoaderProxy.includesType(interfaceType.getTypeRef(), receiverType.getTypeRef());
              requiresImplementsTest = doesImplement != YES;
            }
          }

          // Attempt to resolve the interface call to a particular virtual method.
          // This is independent of whether or not the static type of the receiver is
          // known to implement the interface and it is not that case that being able
          // to prove one implies the other.
          RVMMethod vmeth = null;
          if (receiverType != null && receiverType.isInitialized() && !receiverType.isInterface()) {
            vmeth = ClassLoaderProxy.lookupMethod(receiverType, ref);
          }
          if (vmeth != null) {
            MethodReference vmethRef = vmeth.getMemberRef().asMethodReference();
            // We're going to virtualize the call.  Must inject the
            // DTC to ensure the receiver implements the interface if
            // requiresImplementsTest is still true.
            // Note that at this point requiresImplementsTest => resolvedMethod != null
            if (requiresImplementsTest) {
              RegisterOperand checkedReceiver = gc.temps.makeTemp(receiver);
              appendInstruction(TypeCheck.create(MUST_IMPLEMENT_INTERFACE,
                  checkedReceiver,
                  receiver.copy(),
                  makeTypeOperand(resolvedMethod.getDeclaringClass()),
                  getCurrentGuard()));
              checkedReceiver.refine(resolvedMethod.getDeclaringClass().getTypeRef());
              Call.setParam(s, 0, checkedReceiver.copyRO());
              receiver = checkedReceiver;
              rectifyStateWithErrorHandler(); // Can raise incompatible class change error.
            }
            MethodOperand mop = MethodOperand.VIRTUAL(vmethRef, vmeth);
            if (receiver.isConstant() || receiver.asRegister().isPreciseType()) {
              mop.refine(vmeth, true);
            }
            Call.setMethod(s, mop);
            boolean unresolved = vmethRef.needsDynamicLink(bcodes.getMethod());
            if (unresolved) {
              RegisterOperand offsetrop = gc.temps.makeTempOffset();
              appendInstruction(Unary.create(RESOLVE_MEMBER, offsetrop.copyRO(), Call.getMethod(s).copy()));
              Call.setAddress(s, offsetrop);
              rectifyStateWithErrorHandler();
            } else {
              Call.setAddress(s, new AddressConstantOperand(vmeth.getOffset()));
            }

            // Attempt to inline virtualized call.
            if (maybeInlineMethod(shouldInline(s,
                receiver.isConstant() || receiver.asRegister().isExtant(),
                instrIndex - bciAdjustment), s)) {
              return;
            }
          } else {
            // We can't virtualize the call;
            // try to inline a predicted target for the interface invocation
            // inline code will include DTC to ensure receiver implements the interface.
            if (resolvedMethod != null &&
                maybeInlineMethod(shouldInline(s, false, instrIndex - bciAdjustment), s)) {
              return;
            } else {
              if (requiresImplementsTest) {
                RegisterOperand checkedReceiver = gc.temps.makeTemp(receiver);
                appendInstruction(TypeCheck.create(MUST_IMPLEMENT_INTERFACE,
                    checkedReceiver,
                    receiver.copy(),
                    makeTypeOperand(resolvedMethod.getDeclaringClass()),
                    getCurrentGuard()));
                checkedReceiver.refine(resolvedMethod.getDeclaringClass().getTypeRef());
                Call.setParam(s, 0, checkedReceiver.copyRO());
                // don't have to rectify with error handlers; rectify call below subsumes.
              }
            }
          }

          // CALL must be treated as potential throw of anything
          rectifyStateWithExceptionHandlers();
        }
        break;

        case JBC_xxxunusedxxx:
          OptimizingCompilerException.UNREACHABLE();
          break;

        case JBC_new: {
          TypeReference klass = bcodes.getTypeReference();
          RegisterOperand t = gc.temps.makeTemp(klass);
          t.setPreciseType();
          markGuardlessNonNull(t);
          Operator operator;
          TypeOperand klassOp;
          RVMClass klassType = (RVMClass) klass.peekType();
          if (klassType != null && (klassType.isInitialized() || klassType.isInBootImage())) {
            klassOp = makeTypeOperand(klassType);
            operator = NEW;
            t.setExtant();
          } else {
            operator = NEW_UNRESOLVED;
            klassOp = makeTypeOperand(klass);
          }
          s = New.create(operator, t, klassOp);
          push(t.copyD2U());
          rectifyStateWithErrorHandler();
        }
        break;

        case JBC_newarray: {
          RVMType array = bcodes.getPrimitiveArrayType();
          TypeOperand arrayOp = makeTypeOperand(array);
          RegisterOperand t = gc.temps.makeTemp(array.getTypeRef());
          t.setPreciseType();
          t.setExtant();
          markGuardlessNonNull(t);
          s = NewArray.create(NEWARRAY, t, arrayOp, popInt());
          push(t.copyD2U());
          rectifyStateWithExceptionHandler(TypeReference.JavaLangNegativeArraySizeException);
        }
        break;

        case JBC_anewarray: {
          TypeReference elementTypeRef = bcodes.getTypeReference();
          s = generateAnewarray(null, elementTypeRef);
        }
        break;

        case JBC_arraylength: {
          Operand op1 = pop();
          clearCurrentGuard();
          if (do_NullCheck(op1)) {
            break;
          }
          if (VM.VerifyAssertions) {
            VM._assert(getArrayTypeOf(op1).isArrayType());
          }
          RegisterOperand t = gc.temps.makeTempInt();
          s = GuardedUnary.create(ARRAYLENGTH, t, op1, getCurrentGuard());
          push(t.copyD2U());
        }
        break;

        case JBC_athrow: {
          Operand op0 = pop();
          clearCurrentGuard();
          if (do_NullCheck(op0)) {
            break;
          }
          TypeReference type = getRefTypeOf(op0);
          if (VM.VerifyAssertions) assertIsAssignable(TypeReference.JavaLangThrowable, type);
          if (!gc.method.isInterruptible()) {
            // prevent code motion in or out of uninterruptible code sequence
            appendInstruction(Empty.create(UNINT_END));
          }
          endOfBasicBlock = true;
          BasicBlock definiteTarget = rectifyStateWithExceptionHandler(type, true);
          if (definiteTarget != null) {
            appendInstruction(CacheOp.create(SET_CAUGHT_EXCEPTION, op0));
            s = Goto.create(GOTO, definiteTarget.makeJumpTarget());
            definiteTarget.setExceptionHandlerWithNormalIn();
          } else {
            s = Athrow.create(ATHROW, op0);
          }
        }
        break;

        case JBC_checkcast: {
          TypeReference typeRef = bcodes.getTypeReference();
          boolean classLoading = couldCauseClassLoading(typeRef);
          Operand op2 = pop();
          if (typeRef.isWordLikeType()) {
            op2 = op2.copy();
            if (op2 instanceof RegisterOperand) {
              ((RegisterOperand) op2).setType(typeRef);
            }
            push(op2);
            if (DBG_CF) db("skipped gen of checkcast to word type " + typeRef);
            break;
          }
          if (VM.VerifyAssertions) VM._assert(op2.isRef());
          if (CF_CHECKCAST && !classLoading) {
            if (op2.isDefinitelyNull()) {
              push(op2);
              if (DBG_CF) db("skipped gen of null checkcast");
              break;
            }
            TypeReference type = getRefTypeOf(op2);  // non-null, null case above
            byte typeTestResult = ClassLoaderProxy.includesType(typeRef, type);
            if (typeTestResult == YES) {
              push(op2);
              if (DBG_CF) {
                db("skipped gen of checkcast of " + op2 + " from " + typeRef + " to " + type);
              }
              break;
            }
            if (typeTestResult == NO) {
              if (isNonNull(op2)) {
                // Definite class cast exception
                endOfBasicBlock = true;
                appendInstruction(Trap.create(TRAP, gc.temps.makeTempValidation(), TrapCodeOperand.CheckCast()));
                rectifyStateWithExceptionHandler(TypeReference.JavaLangClassCastException);
                if (DBG_CF) db("Converted checkcast into unconditional trap");
                break;
              } else {
                // At runtime either it is null and the checkcast succeeds or it is non-null
                // and a class cast exception is raised
                RegisterOperand refinedOp2 = gc.temps.makeTemp(op2);
                s = TypeCheck.create(CHECKCAST, refinedOp2, op2.copy(), makeTypeOperand(typeRef.peekType()));
                refinedOp2.refine(TypeReference.NULL_TYPE);
                push(refinedOp2.copyRO());
                rectifyStateWithExceptionHandler(TypeReference.JavaLangClassCastException);
                if (DBG_CF) db("Narrowed type downstream of checkcast to NULL");
                break;
              }
            }
          }

          RegisterOperand refinedOp2 = gc.temps.makeTemp(op2);
          if (classLoading) {
            s = TypeCheck.create(CHECKCAST_UNRESOLVED, refinedOp2, op2.copy(), makeTypeOperand(typeRef));
          } else {
            TypeOperand typeOp = makeTypeOperand(typeRef.peekType());
            if (isNonNull(op2)) {
              s = TypeCheck.create(CHECKCAST_NOTNULL, refinedOp2, op2.copy(), typeOp, getGuard(op2));
            } else {
              s = TypeCheck.create(CHECKCAST, refinedOp2, op2.copy(), typeOp);
            }
          }
          refinedOp2.refine(typeRef);
          push(refinedOp2.copyRO());
          rectifyStateWithExceptionHandler(TypeReference.JavaLangClassCastException);
          if (classLoading) rectifyStateWithErrorHandler();
        }
        break;

        case JBC_instanceof: {
          TypeReference typeRef = bcodes.getTypeReference();
          boolean classLoading = couldCauseClassLoading(typeRef);
          Operand op2 = pop();
          if (VM.VerifyAssertions) VM._assert(op2.isRef());
          if (CF_INSTANCEOF && !classLoading) {
            if (op2.isDefinitelyNull()) {
              push(new IntConstantOperand(0));
              if (DBG_CF) db("skipped gen of null instanceof");
              break;
            }
            TypeReference type = getRefTypeOf(op2);                 // non-null
            int answer = ClassLoaderProxy.includesType(typeRef, type);
            if (answer == YES && isNonNull(op2)) {
              push(new IntConstantOperand(1));
              if (DBG_CF) {
                db(op2 + " instanceof " + typeRef + " is always true ");
              }
              break;
            } else if (answer == NO) {
              if (DBG_CF) {
                db(op2 + " instanceof " + typeRef + " is always false ");
              }
              push(new IntConstantOperand(0));
              break;
            }
          }

          RegisterOperand t = gc.temps.makeTempInt();
          if (classLoading) {
            s = InstanceOf.create(INSTANCEOF_UNRESOLVED, t, makeTypeOperand(typeRef), op2);
          } else {
            TypeOperand typeOp = makeTypeOperand(typeRef.peekType());
            if (isNonNull(op2)) {
              s = InstanceOf.create(INSTANCEOF_NOTNULL, t, typeOp, op2, getGuard(op2));
            } else {
              s = InstanceOf.create(INSTANCEOF, t, typeOp, op2);
            }
          }

          push(t.copyD2U());
          if (classLoading) rectifyStateWithErrorHandler();
        }
        break;

        case JBC_monitorenter: {
          Operand op0 = pop();
          clearCurrentGuard();
          if (do_NullCheck(op0)) {
            break;
          }
          if (VM.VerifyAssertions) VM._assert(op0.isRef());
          s = MonitorOp.create(MONITORENTER, op0, getCurrentGuard());
        }
        break;

        case JBC_monitorexit: {
          Operand op0 = pop();
          clearCurrentGuard();
          if (do_NullCheck(op0)) {
            break;
          }
          s = MonitorOp.create(MONITOREXIT, op0, getCurrentGuard());
          rectifyStateWithExceptionHandler(TypeReference.JavaLangIllegalMonitorStateException);
        }
        break;

        case JBC_wide: {
          int widecode = bcodes.getWideOpcode();
          int index = bcodes.getWideLocalNumber();
          switch (widecode) {
            case JBC_iload:
              s = do_iload(index);
              break;

            case JBC_lload:
              s = do_lload(index);
              break;

            case JBC_fload:
              s = do_fload(index);
              break;

            case JBC_dload:
              s = do_dload(index);
              break;

            case JBC_aload:
              s = do_aload(index);
              break;

            case JBC_istore:
              s = do_store(index, popInt());
              break;

            case JBC_lstore:
              s = do_store(index, popLong());
              break;

            case JBC_fstore:
              s = do_store(index, popFloat());
              break;

            case JBC_dstore:
              s = do_store(index, popDouble());
              break;

            case JBC_astore:
              s = do_astore(index);
              break;

            case JBC_iinc:
              s = do_iinc(index, bcodes.getWideIncrement());
              break;

            case JBC_ret:
              s = _retHelper(index);
              break;

            default:
              OptimizingCompilerException.UNREACHABLE();
              break;
          }
        }
        break;

        case JBC_multianewarray: {
          TypeReference arrayType = bcodes.getTypeReference();
          int dimensions = bcodes.getArrayDimension();

          if (dimensions == 1) {
            s = generateAnewarray(arrayType, null);
          } else {
            TypeOperand typeOp = makeTypeOperand(arrayType);
            RegisterOperand result = gc.temps.makeTemp(arrayType);
            markGuardlessNonNull(result);
            result.setPreciseType();
            TypeReference innermostElementTypeRef = arrayType.getInnermostElementType();
            RVMType innermostElementType = innermostElementTypeRef.peekType();
            if (innermostElementType != null && (innermostElementType.isInitialized() || innermostElementType.isInBootImage())) {
              result.setExtant();
            }
            s = Multianewarray.create(NEWOBJMULTIARRAY, result, typeOp, dimensions);
            for (int i = 0; i < dimensions; i++) {
              Multianewarray.setDimension(s, dimensions - i - 1, popInt());
            }
            push(result.copyD2U());
            rectifyStateWithErrorHandler();
            rectifyStateWithExceptionHandler(TypeReference.JavaLangNegativeArraySizeException);
          }
        }
        break;

        case JBC_ifnull:
          s = _refIfNullHelper(ConditionOperand.EQUAL());
          break;

        case JBC_ifnonnull:
          s = _refIfNullHelper(ConditionOperand.NOT_EQUAL());
          break;

        case JBC_goto_w: {
          int offset = bcodes.getWideBranchOffset();
          if (offset != 5) {
            // skip generating frivolous goto's
            s = _gotoHelper(offset);
          }
        }
        break;

        case JBC_jsr_w:
          s = _jsrHelper(bcodes.getWideBranchOffset());
          break;

        case JBC_impdep1: {
          if (VM.BuildForAdaptiveSystem) {
          int pseudo_opcode = bcodes.nextPseudoInstruction();
          switch (pseudo_opcode) {
            case PSEUDO_LoadIntConst: {
              int value = bcodes.readIntConst();

              if (VM.TraceOnStackReplacement) {
                VM.sysWriteln("PSEUDO_LoadIntConst " + value);
              }

              push(new IntConstantOperand(value));

              // used for PSEUDO_InvokeStatic to recover the type info
              param1 = param2;
              param2 = value;

              break;
            }
            case PSEUDO_LoadLongConst: {
              long value = bcodes.readLongConst();

              if (VM.TraceOnStackReplacement) {
                VM.sysWriteln("PSEUDO_LoadLongConst " + value);
              }

              pushDual(new LongConstantOperand(value, Offset.zero()));
              break;
            }
            case PSEUDO_LoadWordConst: {
              Address a =
                  (VM.BuildFor32Addr) ? Address.fromIntSignExtend(bcodes.readIntConst()) : Address.fromLong(bcodes.readLongConst());

              push(new AddressConstantOperand(a));

              if (VM.TraceOnStackReplacement) {
                VM.sysWrite("PSEUDO_LoadWordConst 0x");
              }
              VM.sysWrite(a);
              VM.sysWriteln();

              break;
            }
            case PSEUDO_LoadFloatConst: {
              int ibits = bcodes.readIntConst();
              float value = Float.intBitsToFloat(ibits);

              if (VM.TraceOnStackReplacement) {
                VM.sysWriteln("PSEUDO_LoadFloatConst " + value);
              }

              push(new FloatConstantOperand(value, Offset.zero()));
              break;
            }

            case PSEUDO_LoadDoubleConst: {
              long lbits = bcodes.readLongConst();

              double value = Magic.longBitsAsDouble(lbits);

              if (VM.TraceOnStackReplacement) {
                VM.sysWriteln("PSEUDO_LoadDoubleConst " + lbits);
              }

              pushDual(new DoubleConstantOperand(value, Offset.zero()));
              break;
            }

            case PSEUDO_LoadRetAddrConst: {
              int value = bcodes.readIntConst();

              if (VM.TraceOnStackReplacement) {
                VM.sysWriteln("PSEUDO_LoadRetAddrConst " + value);
              }

              push(new ReturnAddressOperand(value));
              break;
            }
            case PSEUDO_InvokeStatic: {
              /* pseudo invoke static for getRefAt and cleanRefAt, both must be resolved already */
              int targetidx = bcodes.readIntConst();
              RVMMethod meth = InvokeStatic.targetMethod(targetidx);

              if (VM.TraceOnStackReplacement) {
                VM.sysWriteln("PSEUDO_Invoke " + meth + "\n");
              }

              s = _callHelper(meth.getMemberRef().asMethodReference(), MethodOperand.STATIC(meth));
              if (s == null)
                break;
              Call.setAddress(s, new AddressConstantOperand(meth.getOffset()));

              /* try to set the type of return register */
              if (targetidx == GETREFAT) {
                Object realObj = ObjectHolder.getRefAt(param1, param2);

                if (VM.VerifyAssertions) VM._assert(realObj != null);

                TypeReference klass = Magic.getObjectType(realObj).getTypeRef();

                RegisterOperand op0 = gc.temps.makeTemp(klass);
                Call.setResult(s, op0);
                pop();    // pop the old one and push the new return type.
                push(op0.copyD2U(), klass);
              }

              // CALL must be treated as potential throw of anything
              rectifyStateWithExceptionHandlers();
              break;
            }
            case PSEUDO_InvokeCompiledMethod: {
              int cmid = bcodes.readIntConst();
              int origBCIdx = bcodes.readIntConst(); // skip it
              CompiledMethod cm = CompiledMethods.getCompiledMethod(cmid);
              RVMMethod meth = cm.getMethod();

              if (VM.TraceOnStackReplacement) {
                VM.sysWriteln("PSEUDO_InvokeCompiledMethod " + meth + "\n");
              }

              /* the bcIndex should be adjusted to the original */
              s = _callHelper(meth.getMemberRef().asMethodReference(),
                              MethodOperand.COMPILED(meth, cm.getOsrJTOCoffset()));
              if (s == null)
                break;

              // adjust the bcindex of s to the original bytecode's index
              // it should be able to give the correct exception handling
              s.bcIndex = origBCIdx + bciAdjustment;

              rectifyStateWithExceptionHandlers();
              break;
            }
            case PSEUDO_ParamInitEnd: {
              // indicates the place to insert method prologue and stack
              // overflow checks.
              // opt compiler should consider this too

              break;
            }
            default:
              if (VM.TraceOnStackReplacement) {
                VM.sysWriteln("OSR Error, no such pseudo opcode : " + pseudo_opcode);
              }

              OptimizingCompilerException.UNREACHABLE();
              break;
          }
            break;
          } else {
            OptimizingCompilerException.UNREACHABLE();
          }
        }
        default:
          OptimizingCompilerException.UNREACHABLE();
          break;
      }

      if (s != null && !currentBBLE.isSelfRegen()) {
        appendInstruction(s);
      }

      // check runoff
      if (VM.VerifyAssertions) VM._assert(bcodes.index() <= runoff);
      if (!endOfBasicBlock && bcodes.index() == runoff) {
        if (DBG_BB || DBG_SELECTED) {
          db("runoff occurred! current basic block: " + currentBBLE + ", runoff = " + runoff);
        }
        endOfBasicBlock = fallThrough = true;
      }
      if (endOfBasicBlock) {
        if (currentBBLE.isSelfRegen()) {
          // This block ended in a goto that jumped into the middle of it.
          // Through away all out edges from this block, they're out of date
          // because we're going to have to regenerate this block.
          currentBBLE.block.deleteOut();
          if (DBG_CFG || DBG_SELECTED) {
            db("Deleted all out edges of " + currentBBLE.block);
          }
          return;
        }
        if (fallThrough) {
          if (VM.VerifyAssertions) VM._assert(bcodes.index() < bcodes.length());
          // Get/Create fallthrough BBLE and record it as
          // currentBBLE's fallThrough.
          currentBBLE.fallThrough = getOrCreateBlock(bcodes.index());
          currentBBLE.block.insertOut(currentBBLE.fallThrough.block);
        }
        return;
      }
    }
  }

  private Instruction _unaryHelper(Operator operator, Operand val, TypeReference type) {
    RegisterOperand t = gc.temps.makeTemp(type);
    Instruction s = Unary.create(operator, t, val);
    Simplifier.DefUseEffect simp = Simplifier.simplify(true, gc.temps, gc.options, s);
    if ((simp == Simplifier.DefUseEffect.MOVE_FOLDED) || (simp == Simplifier.DefUseEffect.MOVE_REDUCED)) {
      gc.temps.release(t);
      push(Move.getClearVal(s));
      return null;
    } else {
      push(t.copyD2U());
      return s;
    }
  }

  private Instruction _unaryDualHelper(Operator operator, Operand val, TypeReference type) {
    RegisterOperand t = gc.temps.makeTemp(type);
    Instruction s = Unary.create(operator, t, val);
    Simplifier.DefUseEffect simp = Simplifier.simplify(true, gc.temps, gc.options, s);
    if ((simp == Simplifier.DefUseEffect.MOVE_FOLDED) || (simp == Simplifier.DefUseEffect.MOVE_REDUCED)) {
      gc.temps.release(t);
      pushDual(Move.getClearVal(s));
      return null;
    } else {
      pushDual(t.copyD2U());
      return s;
    }
  }

  private Instruction _binaryHelper(Operator operator, Operand op1, Operand op2,
                                        TypeReference type) {
    RegisterOperand t = gc.temps.makeTemp(type);
    Instruction s = Binary.create(operator, t, op1, op2);
    Simplifier.DefUseEffect simp = Simplifier.simplify(true, gc.temps, gc.options, s);
    if ((simp == Simplifier.DefUseEffect.MOVE_FOLDED) || (simp == Simplifier.DefUseEffect.MOVE_REDUCED)) {
      gc.temps.release(t);
      push(Move.getClearVal(s));
      return null;
    } else {
      push(t.copyD2U());
      return s;
    }
  }

  private Instruction _guardedBinaryHelper(Operator operator, Operand op1, Operand op2,
                                               Operand guard, TypeReference type) {
    RegisterOperand t = gc.temps.makeTemp(type);
    Instruction s = GuardedBinary.create(operator, t, op1, op2, guard);
    Simplifier.DefUseEffect simp = Simplifier.simplify(true, gc.temps, gc.options, s);
    if ((simp == Simplifier.DefUseEffect.MOVE_FOLDED) || (simp == Simplifier.DefUseEffect.MOVE_REDUCED)) {
      gc.temps.release(t);
      push(Move.getClearVal(s));
      return null;
    } else {
      push(t.copyD2U());
      return s;
    }
  }

  private Instruction _binaryDualHelper(Operator operator, Operand op1, Operand op2,
                                            TypeReference type) {
    RegisterOperand t = gc.temps.makeTemp(type);
    Instruction s = Binary.create(operator, t, op1, op2);
    Simplifier.DefUseEffect simp = Simplifier.simplify(true, gc.temps, gc.options, s);
    if ((simp == Simplifier.DefUseEffect.MOVE_FOLDED) || (simp == Simplifier.DefUseEffect.MOVE_REDUCED)) {
      gc.temps.release(t);
      pushDual(Move.getClearVal(s));
      return null;
    } else {
      pushDual(t.copyD2U());
      return s;
    }
  }

  private Instruction _guardedBinaryDualHelper(Operator operator, Operand op1, Operand op2,
                                                   Operand guard, TypeReference type) {
    RegisterOperand t = gc.temps.makeTemp(type);
    Instruction s = GuardedBinary.create(operator, t, op1, op2, guard);
    Simplifier.DefUseEffect simp = Simplifier.simplify(true, gc.temps, gc.options, s);
    if ((simp == Simplifier.DefUseEffect.MOVE_FOLDED) || (simp == Simplifier.DefUseEffect.MOVE_REDUCED)) {
      gc.temps.release(t);
      pushDual(Move.getClearVal(s));
      return null;
    } else {
      pushDual(t.copyD2U());
      return s;
    }
  }

  private Instruction _moveHelper(Operator operator, Operand val, TypeReference type) {
    RegisterOperand t = gc.temps.makeTemp(type);
    push(t.copyD2U());
    Instruction s = Move.create(operator, t, val);
    s.position = gc.inlineSequence;
    s.bcIndex = instrIndex;
    return s;
  }

  private Instruction _moveDualHelper(Operator operator, Operand val, TypeReference type) {
    RegisterOperand t = gc.temps.makeTemp(type);
    pushDual(t.copyD2U());
    Instruction s = Move.create(operator, t, val);
    s.position = gc.inlineSequence;
    s.bcIndex = instrIndex;
    return s;
  }

  public Instruction _aloadHelper(Operator operator, Operand ref, Operand index,
                                      TypeReference type) {
    RegisterOperand t = gc.temps.makeTemp(type);
    t.setDeclaredType();
    LocationOperand loc = new LocationOperand(type);
    Instruction s = ALoad.create(operator, t, ref, index, loc, getCurrentGuard());
    t = t.copyD2U();
    if (type.isLongType() || type.isDoubleType()) {
      pushDual(t);
    } else {
      push(t);
    }
    return s;
  }

  /**
   * Pop method parameters off the expression stack.
   * If a non-void return, then create a result operand and push it
   * on the stack.
   * Create the call instruction and initialize all its operands.
   */
  private Instruction _callHelper(MethodReference meth, MethodOperand methOp) {
    int numHiddenParams = methOp.isStatic() ? 0 : 1;
    TypeReference[] params = meth.getParameterTypes();
    Instruction s = Call.create(CALL, null, null, null, null, params.length + numHiddenParams);
    if (gc.options.H2L_NO_CALLEE_EXCEPTIONS) {
      s.markAsNonPEI();
    }
    for (int i = params.length - 1; i >= 0; i--) {
      try {
        Call.setParam(s, i + numHiddenParams, pop(params[i]));
      } catch (OptimizingCompilerException.IllegalUpcast e) {
        throw new Error("Illegal upcast creating call to " + meth + " from " + gc.method + " argument " + i, e);
      }
    }
    if (numHiddenParams != 0) {
      Operand ref = pop();
      Call.setParam(s, 0, ref);
    }
    Call.setMethod(s, methOp);

    // need to set it up early because the inlining oracle use it
    s.position = gc.inlineSequence;
    // no longer used by the inline oracle as it is incorrectly adjusted by OSR,
    // can't adjust it here as it will effect the exception handler maps
    s.bcIndex = instrIndex;

    TypeReference rtype = meth.getReturnType();
    if (rtype.isVoidType()) {
      return s;
    } else {
      RegisterOperand t = gc.temps.makeTemp(rtype);
      Call.setResult(s, t);
      Simplifier.DefUseEffect simp = Simplifier.simplify(true, gc.temps, gc.options, s);
      if ((simp == Simplifier.DefUseEffect.MOVE_FOLDED) || (simp == Simplifier.DefUseEffect.MOVE_REDUCED)) {
        gc.temps.release(t);
        push(Move.getClearVal(s), rtype);
        return null;
      } else {
        push(t.copyD2U(), rtype);
        return s;
      }
    }
  }

  private void _returnHelper(Operator operator, Operand val) {
    if (gc.resultReg != null) {
      TypeReference returnType = val.getType();
      RegisterOperand ret = new RegisterOperand(gc.resultReg, returnType);
      boolean returningRegister = false;
      if (val.isRegister()) {
        returningRegister = true;
        ret.setInheritableFlags(val.asRegister());
        setGuard(ret, getGuard(val));
      }
      appendInstruction(Move.create(operator, ret, val));
      // pass analysis facts about val back to our caller
      if (gc.result == null) {
        if (returningRegister) {
          gc.result = ret.copyD2U();
        } else {
          gc.result = val.copy();
        }
      } else {
        Operand meet = Operand.meet(gc.result, val, gc.resultReg);
        // Return value can't be forced to bottom...violation of Java spec.
        if (VM.VerifyAssertions) VM._assert(meet != null);
        gc.result = meet;
      }
    }
    if (gc.method.isObjectInitializer() && gc.method.getDeclaringClass().declaresFinalInstanceField()) {
      /* JMM Compliance.  Must insert StoreStore barrier before returning from constructor of class with final instance fields */
      appendInstruction(Empty.create(WRITE_FLOOR));
    }
    appendInstruction(gc.epilogue.makeGOTO());
    currentBBLE.block.insertOut(gc.epilogue);
    if (DBG_CFG || DBG_SELECTED) {
      db("Added CFG edge from " + currentBBLE.block + " to " + gc.epilogue);
    }
    endOfBasicBlock = true;
  }

  //// APPEND INSTRUCTION.
  /**
   * Append an instruction to the current basic block.
   *
   * @param s instruction to append
   */
  public void appendInstruction(Instruction s) {
    currentBBLE.block.appendInstruction(s);
    s.position = gc.inlineSequence;
    s.bcIndex = instrIndex;
    lastInstr = s;
    if (DBG_INSTR || DBG_SELECTED) db("-> " + s.bcIndex + ":\t" + s);
  }

  /**
   * HACK: Mark current basic block unsafe for scheduling.
   * TODO: remove when we've got UNINT_BEGIN/END working correctly.
   */
  void markBBUnsafeForScheduling() {
    currentBBLE.block.setUnsafeToSchedule();
  }

  //// MAKE A FIELD REFERENCE.
  /**
   * Make a field reference operand referring to the given field with the
   * given type.
   *
   * @param f desired field
   */
  private LocationOperand makeStaticFieldRef(FieldReference f) {
    return new LocationOperand(f);
  }

  private LocationOperand makeInstanceFieldRef(FieldReference f) {
    return new LocationOperand(f);
  }

  //// MAKE A TYPE REFERENCE.
  /**
   * Make a type operand that refers to the given type.
   *
   * @param type desired type
   */
  private TypeOperand makeTypeOperand(TypeReference type) {
    if (VM.VerifyAssertions) VM._assert(type != null);
    return new TypeOperand(type);
  }

  /**
   * Make a type operand that refers to the given type.
   *
   * @param type desired type
   */
  private TypeOperand makeTypeOperand(RVMType type) {
    if (VM.VerifyAssertions) VM._assert(type != null);
    return new TypeOperand(type);
  }

  private boolean couldCauseClassLoading(TypeReference typeRef) {
    RVMType type = typeRef.peekType();
    if (type == null) return true;
    if (type.isInitialized()) return false;
    if (type.isArrayType()) return !type.isResolved();
    if (type.isClassType() && type.asClass().isInBootImage()) return false;
    return true;
  }

  /**
   * Fetch the value of the next operand, a constant, from the bytecode
   * stream.
   * @return the value of a literal constant from the bytecode stream,
   * encoding as a constant IR operand
   */
  public Operand getConstantOperand(int index) {
    byte desc = bcodes.getConstantType(index);
    RVMClass declaringClass = bcodes.getDeclaringClass();
    switch (desc) {
      case CP_INT:
        return ClassLoaderProxy.getIntFromConstantPool(declaringClass, index);
      case CP_FLOAT:
        return ClassLoaderProxy.getFloatFromConstantPool(declaringClass, index);
      case CP_STRING:
        return ClassLoaderProxy.getStringFromConstantPool(declaringClass, index);
      case CP_LONG:
        return ClassLoaderProxy.getLongFromConstantPool(declaringClass, index);
      case CP_DOUBLE:
        return ClassLoaderProxy.getDoubleFromConstantPool(declaringClass, index);
      case CP_CLASS:
        return ClassLoaderProxy.getClassFromConstantPool(declaringClass, index);
      default:
        if (VM.VerifyAssertions) VM._assert(VM.NOT_REACHED, "invalid literal type: 0x" + Integer.toHexString(desc));
        return null;
    }
  }

  //// LOAD LOCAL VARIABLE ONTO STACK.
  /**
   * Simulate a load from a given local variable of an int.
   * Returns generated instruction (or null if no instruction generated.)
   *
   * @param index local variable number
   */
  private Instruction do_iload(int index) {
    Operand r = getLocal(index);
    if (VM.VerifyAssertions) VM._assert(r.isIntLike());
    if (LOCALS_ON_STACK) {
      push(r);
      return null;
    } else {
      return _moveHelper(INT_MOVE, r, TypeReference.Int);
    }
  }

  /**
   * Simulate a load from a given local variable of a float.
   * Returns generated instruction (or null if no instruction generated.)
   *
   * @param index local variable number
   */
  private Instruction do_fload(int index) {
    Operand r = getLocal(index);
    if (VM.VerifyAssertions) VM._assert(r.isFloat());
    if (LOCALS_ON_STACK) {
      push(r);
      return null;
    } else {
      return _moveHelper(FLOAT_MOVE, r, TypeReference.Float);
    }
  }

  /**
   * Simulate a load from a given local variable of a reference.
   * Returns generated instruction (or null if no instruction generated.)
   *
   * @param index local variable number
   */
  private Instruction do_aload(int index) {
    Operand r = getLocal(index);
    if (VM.VerifyAssertions && !(r.isRef() || r.isAddress())) {
      VM._assert(VM.NOT_REACHED, r + " not ref, but a " + r.getType());
    }
    if (LOCALS_ON_STACK) {
      push(r);
      return null;
    } else {
      return _moveHelper(REF_MOVE, r, r.getType());
    }
  }

  /**
   * Simulate a load from a given local variable of a long.
   * Returns generated instruction (or null if no instruction generated.)
   *
   * @param index local variable number
   */
  private Instruction do_lload(int index) {
    Operand r = getLocalDual(index);
    if (VM.VerifyAssertions) VM._assert(r.isLong());
    if (LOCALS_ON_STACK) {
      pushDual(r);
      return null;
    } else {
      return _moveDualHelper(LONG_MOVE, r, TypeReference.Long);
    }
  }

  /**
   * Simulate a load from a given local variable of a double.
   * Returns generated instruction (or null if no instruction generated.)
   *
   * @param index local variable number
   */
  private Instruction do_dload(int index) {
    Operand r = getLocalDual(index);
    if (VM.VerifyAssertions) VM._assert(r.isDouble());
    if (LOCALS_ON_STACK) {
      pushDual(r);
      return null;
    } else {
      return _moveDualHelper(DOUBLE_MOVE, r, TypeReference.Double);
    }
  }

  //// INCREMENT A LOCAL VARIABLE.
  /**
   * Simulate the incrementing of a given int local variable.
   * Returns generated instruction (or null if no instruction generated.)
   *
   * @param index local variable number
   * @param amount amount to increment by
   */
  private Instruction do_iinc(int index, int amount) {
    Operand r = getLocal(index);
    if (VM.VerifyAssertions) VM._assert(r.isIntLike());
    if (LOCALS_ON_STACK) {
      replaceLocalsOnStack(index, TypeReference.Int);
    }
    RegisterOperand op0 = gc.makeLocal(index, TypeReference.Int);
    if (r instanceof IntConstantOperand) {
      // do constant folding.
      int res = amount + ((IntConstantOperand) r).value;
      IntConstantOperand val = new IntConstantOperand(res);
      if (CP_IN_LOCALS) {
        setLocal(index, val);
      } else {
        setLocal(index, op0);
      }
      Instruction s = Move.create(INT_MOVE, op0, val);
      s.position = gc.inlineSequence;
      s.bcIndex = instrIndex;
      return s;
    }
    setLocal(index, op0);
    return Binary.create(INT_ADD, op0, r, new IntConstantOperand(amount));
  }

  //// POP FROM STACK AND STORE INTO LOCAL VARIABLE.
  /**
   * Simulate a store into a given local variable of an int/long/double/float
   * Returns generated instruction (or null if no instruction generated.)
   *
   * @param index local variable number
   */
  private Instruction do_store(int index, Operand op1) {
    TypeReference type = op1.getType();
    boolean Dual = (type.isLongType() || type.isDoubleType());
    if (LOCALS_ON_STACK) {
      replaceLocalsOnStack(index, type);
    }
    if (ELIM_COPY_LOCALS) {
      if (op1 instanceof RegisterOperand) {
        RegisterOperand rop1 = (RegisterOperand) op1;
        Register r1 = rop1.getRegister();
        if (lastInstr != null &&
            ResultCarrier.conforms(lastInstr) &&
            ResultCarrier.hasResult(lastInstr) &&
            !r1.isLocal() &&
            r1 == ResultCarrier.getResult(lastInstr).getRegister()) {
          if (DBG_ELIMCOPY) db("eliminated copy " + op1 + " to" + index);
          RegisterOperand newop0 = gc.makeLocal(index, rop1);
          ResultCarrier.setResult(lastInstr, newop0);
          if (Dual) {
            setLocalDual(index, newop0);
          } else {
            setLocal(index, newop0);
          }
          gc.temps.release(rop1);
          return null;
        }
      }
    }
    RegisterOperand op0 =
        (op1 instanceof RegisterOperand) ? gc.makeLocal(index, (RegisterOperand) op1) : gc.makeLocal(index,
                                                                                                             type);
    Operand set = op0;
    if (CP_IN_LOCALS) {
      set = (op1 instanceof RegisterOperand) ? op0 : op1;
    }
    if (Dual) {
      setLocalDual(index, set);
    } else {
      setLocal(index, set);
    }
    Instruction s = Move.create(IRTools.getMoveOp(type), op0, op1);
    s.position = gc.inlineSequence;
    s.bcIndex = instrIndex;
    return s;
  }

  /**
   * Simulate a store into a given local variable of an object ref.
   * Returns generated instruction (or null if no instruction generated.)
   *
   * @param index local variable number
   */
  private Instruction do_astore(int index) {
    Operand op1 = pop();
    if (op1 instanceof ReturnAddressOperand) {
      setLocal(index, op1);
      return null;
    }
    boolean doConstantProp = false;
    if ((op1 instanceof NullConstantOperand) || (op1 instanceof AddressConstantOperand)) {
      doConstantProp = true;
    }
    TypeReference type = op1.getType();
    if (LOCALS_ON_STACK) {
      replaceLocalsOnStack(index, type);
    }
    if (ELIM_COPY_LOCALS) {
      if (op1 instanceof RegisterOperand) {
        RegisterOperand rop1 = (RegisterOperand) op1;
        Register r1 = rop1.getRegister();
        if (lastInstr != null &&
            ResultCarrier.conforms(lastInstr) &&
            ResultCarrier.hasResult(lastInstr) &&
            !r1.isLocal() &&
            r1 == ResultCarrier.getResult(lastInstr).getRegister()) {
          if (DBG_ELIMCOPY) {
            db("eliminated copy " + op1 + " to " + index);
          }
          RegisterOperand newop0 = gc.makeLocal(index, rop1);
          ResultCarrier.setResult(lastInstr, newop0);
          setLocal(index, newop0);
          gc.temps.release(rop1);
          return null;
        }
      }
    }
    RegisterOperand op0;
    if (op1 instanceof RegisterOperand) {
      RegisterOperand rop1 = (RegisterOperand) op1;
      op0 = gc.makeLocal(index, rop1);
      if (hasGuard(rop1)) {
        RegisterOperand g0 = gc.makeNullCheckGuard(op0.getRegister());
        appendInstruction(Move.create(GUARD_MOVE, g0.copyRO(), getGuard(rop1)));
        setGuard(op0, g0);
      }
    } else {
      op0 = gc.makeLocal(index, type);
    }
    if (CP_IN_LOCALS) {
      setLocal(index, doConstantProp ? op1 : op0);
    } else {
      setLocal(index, op0);
    }
    Instruction s = Move.create(REF_MOVE, op0, op1);
    s.position = gc.inlineSequence;
    s.bcIndex = instrIndex;
    return s;
  }

  //// PUSH OPERAND ONTO THE STACK.
  /**
   * Push a single width operand (int, float, ref, ...) on the simulated stack.
   *
   * @param r operand to push
   */
  public void push(Operand r) {
    if (VM.VerifyAssertions) VM._assert(r.instruction == null);
    stack.push(r);
  }

  /**
   * Push a double width operand (long, double) on the simulated stack.
   *
   * @param r operand to push
   */
  void pushDual(Operand r) {
    if (VM.VerifyAssertions) VM._assert(r.instruction == null);
    stack.push(DUMMY);
    stack.push(r);
  }

  /**
   * Push an operand of the specified type on the simulated stack.
   *
   * @param r operand to push
   * @param type data type of operand
   */
  void push(Operand r, TypeReference type) {
    if (VM.VerifyAssertions) VM._assert(r.instruction == null);
    if (type.isVoidType()) {
      return;
    }
    if (type.isLongType() || type.isDoubleType()) {
      pushDual(r);
    } else {
      push(r);
    }
  }

  /**
   * Push a copy of the given operand onto simulated stack.
   *
   * @param op1 operand to push
   * @param b1 bytecode index to associate with the pushed operand
   */
  @SuppressWarnings("unused")
  private Instruction pushCopy(Operand op1, int b1) {
    if (VM.VerifyAssertions) VM._assert(op1.instruction == null);
    if (op1 instanceof RegisterOperand) {
      RegisterOperand reg = (RegisterOperand) op1;
      if (!reg.getRegister().isLocal()) {
        lastInstr = null;       // to prevent eliminating this temporary.
      }
      stack.push(reg.copy());
    } else {
      stack.push(op1.copy());
    }
    return null;
  }

  /**
   * Push a copy of the given operand onto simulated stack.
   *
   * @param op1 operand to push
   */
  private Instruction pushCopy(Operand op1) {
    if (VM.VerifyAssertions) VM._assert(op1.instruction == null);
    if (op1 instanceof RegisterOperand) {
      RegisterOperand reg = (RegisterOperand) op1;
      if (!reg.getRegister().isLocal()) {
        lastInstr = null;       // to prevent eliminating this temporary.
      }
      stack.push(reg.copy());
    } else {
      stack.push(op1.copy());
    }
    return null;
  }

  //// POP OPERAND FROM THE STACK.
  /**
   * Pop an operand from the stack. No type checking is performed.
   */
  Operand pop() {
    return stack.pop();
  }

  /**
   * Pop an int operand from the stack.
   */
  public Operand popInt() {
    Operand r = pop();
    if (VM.VerifyAssertions) VM._assert(r.isIntLike());
    return r;
  }

  /**
   * Pop a float operand from the stack.
   */
  Operand popFloat() {
    Operand r = pop();
    if (VM.VerifyAssertions) VM._assert(r.isFloat());
    return r;
  }

  /**
   * Pop a ref operand from the stack.
   */
  public Operand popRef() {
    Operand r = pop();
    if (VM.VerifyAssertions) VM._assert(r.isRef() || r.isAddress());
    return r;
  }

  /**
   * Pop a ref operand from the stack.
   */
  public Operand popAddress() {
    Operand r = pop();
    if (VM.VerifyAssertions) VM._assert(r.isAddress());
    return r;
  }

  /**
   * Pop a long operand from the stack.
   */
  Operand popLong() {
    Operand r = pop();
    if (VM.VerifyAssertions) VM._assert(r.isLong());
    popDummy();
    return r;
  }

  /**
   * Pop a double operand from the stack.
   */
  Operand popDouble() {
    Operand r = pop();
    if (VM.VerifyAssertions) VM._assert(r.isDouble());
    popDummy();
    return r;
  }

  /**
   * Pop a dummy operand from the stack.
   */
  void popDummy() {
    Operand r = pop();
    if (VM.VerifyAssertions) VM._assert(r == DUMMY);
  }

  /**
   * Pop an operand of the given type from the stack.
   */
  Operand pop(TypeReference type) {
    Operand r = pop();
    // Can't assert the following due to approximations by
    // ClassLoaderProxy.findCommonSuperclass
    // if (VM.VerifyAssertions) assertIsType(r, type);
    // Avoid upcasts of magic types to regular j.l.Objects
//    if (VM.VerifyAssertions && (type == TypeReference.JavaLangObject))
//      VM._assert(!r.getType().isMagicType());
    if (VM.VerifyAssertions) {
      if ((type == TypeReference.JavaLangObject) &&
          (r.getType().isMagicType()) &&
          !gc.method.getDeclaringClass().getTypeRef().isMagicType()) {
        throw new OptimizingCompilerException.IllegalUpcast(r.getType());
      }
    }
    if (type.isLongType() || type.isDoubleType()) {
      popDummy();
    }
    return r;
  }

  /**
   * Pop an int from the stack to be used in a shift. A shift only uses the
   * bottom 5 or 6 bits of an int so the upper bits must be masked to conform
   * with the semantics of xx_SHx. NB the opt compiler shift operators allow that
   * (x << 16) << 16 == x << 32, which isn't true in the bytecode
   * @param longShift is this a shift of a long
   * @return the operand containing the amount to shift by
   */
  private Operand popShiftInt(boolean longShift) {
    Operand op = popInt();
    if (op instanceof IntConstantOperand) {
      int val = op.asIntConstant().value;
      if (!longShift) {
        if ((val > 0) && (val <= 31)) {
          return op;
        } else {
          return new IntConstantOperand(val & 0x1F);
        }
      } else {
        if ((val > 0) && (val <= 63)) {
          return op;
        } else {
          return new IntConstantOperand(val & 0x3F);
        }
      }
    } else {
      Instruction s =
          _binaryHelper(INT_AND, op, new IntConstantOperand(longShift ? 0x3F : 0x1f), TypeReference.Int);
      if (s != null && !currentBBLE.isSelfRegen()) {
        appendInstruction(s);
      }
      return popInt();
    }
  }

  //// SUBROUTINES.
  private Instruction _jsrHelper(int offset) {
    // (1) notify the BBSet that we have reached a JSR bytecode.
    //     This enables the more complex JSR-aware implementation of
    //     BBSet.getOrCreateBlock.
    blocks.seenJSR();

    // (2) push return address on expression stack
    push(new ReturnAddressOperand(bcodes.index()));

    // (3) generate GOTO to subroutine body.
    BranchOperand branch = generateTarget(offset);
    return Goto.create(GOTO, branch);
  }

  private Instruction _retHelper(int var) {
    // (1) consume the return address from the specified local variable
    Operand local = getLocal(var);
    ReturnAddressOperand ra = (ReturnAddressOperand) local;
    setLocal(var, null); // must set local null before calling getOrCreateBlock!!
    BasicBlockLE rb = getOrCreateBlock(ra.retIndex);

    // (2) generate a GOTO to the return site.
    currentBBLE.block.insertOut(rb.block);
    endOfBasicBlock = true;
    if (DBG_CFG || DBG_SELECTED) db("Added CFG edge from " + currentBBLE.block + " to " + rb.block);
    return Goto.create(GOTO, rb.block.makeJumpTarget());
  }

  //// GET TYPE OF AN OPERAND.
  /**
   * Return the data type of the given operand, assuming that the operand is
   * an array reference. (and not a null constant.)
   *
   * @param op operand to get type of
   */
  public TypeReference getArrayTypeOf(Operand op) {
    if (VM.VerifyAssertions) VM._assert(!op.isDefinitelyNull());
    return op.getType();
  }

  /**
   * Return the data type of the given operand, assuming that the operand is
   * a reference. (and not a null constant.)
   *
   * @param op operand to get type of
   */
  private TypeReference getRefTypeOf(Operand op) {
    if (VM.VerifyAssertions) VM._assert(!op.isDefinitelyNull());
    return op.getType();
  }

  //// HELPER FUNCTIONS FOR ASSERTION VERIFICATION
  /**
   * Assert that the given operand is of the given type, or of
   * a subclass of the given type.
   *
   * @param op operand to check
   * @param type expected type of operand
   */
  public void assertIsType(Operand op, TypeReference type) {
    if (VM.VerifyAssertions) {
      if (op.isDefinitelyNull()) {
        VM._assert(type.isReferenceType());
      } else if (op.isIntLike()) {
        VM._assert(type.isIntLikeType());
      } else {
        TypeReference type1 = op.getType();
        if (ClassLoaderProxy.includesType(type, type1) == NO) {
          VM._assert(VM.NOT_REACHED, op + ": " + type + " is not assignable with " + type1);
        }
      }
    }
  }

  /**
   * Assert that the given child type is a subclass of the given parent type.
   *
   * @param parentType parent type
   * @param childType child type
   */
  private void assertIsAssignable(TypeReference parentType, TypeReference childType) {
    if (VM.VerifyAssertions) {
      if (childType.isUnboxedType()) {
        //TODO: This should be VM._assert(gc.method.getReturnType() == retType.isUnboxedType());
        // but all word types are converted into addresses and thus the assertion fails. This should be fixed.
        VM._assert(parentType.isUnboxedType());
      } else {
        // fudge to deal with conservative approximation
        // in ClassLoaderProxy.findCommonSuperclass
        if (childType != TypeReference.JavaLangObject) {
          if (ClassLoaderProxy.includesType(parentType, childType) == NO) {
            VM.sysWriteln("type reference equality " + (parentType == childType));
            Enumeration<InlineSequence> callHierarchy = gc.inlineSequence.enumerateFromRoot();
            while(callHierarchy.hasMoreElements()) {
              VM.sysWriteln(callHierarchy.nextElement().toString());
            }
            VM._assert(VM.NOT_REACHED, parentType + " not assignable with " + childType);
          }
        }
      }
    }
  }

  //// DEBUGGING.
  /**
   * Print a debug string to the sysWrite stream
   *
   * @param val string to print
   */
  private void db(String val) {
    VM.sysWrite("IRGEN " + bcodes.getDeclaringClass() + "." + gc.method.getName() + ":" + val + "\n");
  }

  /**
   * Return a string representation of the current basic block set.
   */
  private String printBlocks() {
    StringBuilder res = new StringBuilder();
    for (Enumeration<BasicBlockLE> e = blocks.contents(); e.hasMoreElements();) {
      BasicBlockLE b = e.nextElement();
      if (b == currentBBLE) {
        res.append("*");
      }
      res.append(b.toString());
      res.append(" ");
    }
    return res.toString();
  }

  //// GENERATE CHECK INSTRUCTIONS.
  public static boolean isNonNull(Operand op) {
    if (op instanceof RegisterOperand) {
      RegisterOperand rop = (RegisterOperand) op;
      if (VM.VerifyAssertions) {
        VM._assert((rop.scratchObject == null) ||
                   (rop.scratchObject instanceof RegisterOperand) ||
                   (rop.scratchObject instanceof TrueGuardOperand));
      }
      return rop.scratchObject != null;
    } else {
      return op.isConstant();
    }
  }

  public static boolean hasGuard(RegisterOperand rop) {
    return rop.scratchObject != null;
  }

  public static boolean hasLessConservativeGuard(RegisterOperand rop1, RegisterOperand rop2) {
    if (rop1.scratchObject == rop2.scratchObject) {
      return false;
    }
    if (rop1.scratchObject instanceof Operand) {
      if (rop2.scratchObject instanceof Operand) {
        Operand op1 = (Operand) rop1.scratchObject;
        Operand op2 = (Operand) rop2.scratchObject;
        if (op2 instanceof TrueGuardOperand) {
          // rop2 is top therefore rop1 can't be less conservative!
          return false;
        } else {
          return !(op1.similar(op2));
        }
      } else {
        return true;
      }
    } else {
      // rop1 is bottom, therefore is most conservative guard possible
      return false;
    }
  }

  public void markGuardlessNonNull(RegisterOperand rop) {
    RegisterOperand g = gc.makeNullCheckGuard(rop.getRegister());
    appendInstruction(Move.create(GUARD_MOVE, g, new TrueGuardOperand()));
    rop.scratchObject = g.copy();
  }

  public static Operand getGuard(Operand op) {
    if (op instanceof RegisterOperand) {
      RegisterOperand rop = (RegisterOperand) op;
      if (VM.VerifyAssertions) {
        VM._assert((rop.scratchObject == null) ||
                   (rop.scratchObject instanceof RegisterOperand) ||
                   (rop.scratchObject instanceof TrueGuardOperand));
      }
      if (rop.scratchObject == null) {
        return null;
      } else {
        return ((Operand) rop.scratchObject).copy();
      }
    }
    if (VM.VerifyAssertions) {
      VM._assert(op.isConstant());
    }
    return new TrueGuardOperand();
  }

  public static void setGuard(RegisterOperand rop, Operand guard) {
    rop.scratchObject = guard;
  }

  private void setCurrentGuard(Operand guard) {
    if (currentGuard instanceof RegisterOperand) {
      if (VM.VerifyAssertions) {
        VM._assert(!(guard instanceof TrueGuardOperand));
      }
      // shouldn't happen given current generation --dave.
      RegisterOperand combined = gc.temps.makeTempValidation();
      appendInstruction(Binary.create(GUARD_COMBINE, combined, getCurrentGuard(), guard.copy()));
      currentGuard = combined;
    } else {
      currentGuard = guard;
    }
  }

  public void clearCurrentGuard() {
    currentGuard = null;
  }

  public Operand getCurrentGuard() {
    // This check is needed for when guards are (unsafely) turned off
    if (currentGuard != null) {
      return currentGuard.copy();
    }
    return null;
  }

  /**
   * Generate a null-check instruction for the given operand.
   * @return {@code true} if an unconditional throw is generated, {@code false} otherwise
   */
  public boolean do_NullCheck(Operand ref) {
    if (gc.noNullChecks()) {
      setCurrentGuard(new TrueGuardOperand());
      return false;
    }
    if (ref.isDefinitelyNull()) {
      if (DBG_CF) db("generating definite exception: null_check of definitely null");
      endOfBasicBlock = true;
      rectifyStateWithNullPtrExceptionHandler();
      appendInstruction(Trap.create(TRAP, gc.temps.makeTempValidation(), TrapCodeOperand.NullPtr()));
      return true;
    }
    if (ref instanceof RegisterOperand) {
      RegisterOperand rop = (RegisterOperand) ref;
      if (hasGuard(rop)) {
        Operand guard = getGuard(rop);
        setCurrentGuard(guard);
        if (DBG_ELIMNULL) {
          db("null check of " + ref + " is not necessary; guarded by " + guard);
        }
        return false;
      }
      // rop is possibly null, insert the null check,
      // rectify with exception handler, update the guard state.
      RegisterOperand guard = gc.makeNullCheckGuard(rop.getRegister());
      appendInstruction(NullCheck.create(NULL_CHECK, guard, ref.copy()));
      rectifyStateWithNullPtrExceptionHandler();
      setCurrentGuard(guard);
      setGuard(rop, guard);
      if (DBG_ELIMNULL) db(rop + " is guarded by " + guard);
      // Now, try to leverage this null check by updating
      // other unguarded (and thus potentially null)
      // RegisterOperands representing the same Register.
      if (rop.getRegister().isLocal()) {
        // We want to learn that downstream of this nullcheck, other
        // uses of this local variable will also be non-null.
        // BUT, we MUST NOT just directly set the guard of the appropriate
        // element of our locals array (operands in the local array
        // may appear in previously generated instructions).
        // Therefore we call getLocal (which internally makes a copy),
        // mark the copy with the new guard
        // and finally store the copy back into the local state.
        int number = gc.getLocalNumberFor(rop.getRegister(), rop.getType());
        if (number != -1) {
          Operand loc = getLocal(number);
          if (loc instanceof RegisterOperand) {
            if (DBG_ELIMNULL) {
              db("setting local #" + number + "(" + loc + ") to non-null");
            }
            setGuard((RegisterOperand) loc, guard);
          }
          setLocal(number, loc);
        }
      }
      // At least within this basic block we know that all subsequent uses
      // of ref will be non null, since they are guarded by the null check
      // instruction we just inserted.  Update all RegisterOperands with
      // this register currently on the expression stack appropriately.
      // Stack rectification will ensure that we don't propagate this
      // non-nullness to a use that is not dominated by the null check in
      // the current basic block.
      for (int i = stack.getSize() - 1; i >= 0; --i) {
        Operand sop = stack.getFromTop(i);
        if (sop instanceof RegisterOperand) {
          RegisterOperand sreg = (RegisterOperand) sop;
          if (sreg.getRegister() == rop.getRegister()) {
            if (hasGuard(sreg)) {
              if (DBG_ELIMNULL) {
                db(sreg + " on stack already with guard " + getGuard(sreg));
              }
            } else {
              if (DBG_ELIMNULL) {
                db("setting " + sreg + " on stack to be guarded by " + guard);
              }
              setGuard(sreg, guard);
            }
          }
        }
      }
      return false;
    } else {
      // cannot be null becuase it's not in a register.
      if (DBG_ELIMNULL) {
        db("skipped generation of a null-check instruction for non-register " + ref);
      }
      setCurrentGuard(new TrueGuardOperand());
      return false;
    }
  }

  /**
   * Generate a boundscheck instruction for the given operand and index.
   * @return {@code true} if an unconditional throw is generated, {@code false} otherwise
   */
  public boolean do_BoundsCheck(Operand ref, Operand index) {
    // Unsafely eliminate all bounds checks
    if (gc.noBoundsChecks()) {
      return false;
    }
    RegisterOperand guard = gc.temps.makeTempValidation();
    appendInstruction(BoundsCheck.create(BOUNDS_CHECK, guard, ref.copy(), index.copy(), getCurrentGuard()));
    setCurrentGuard(guard);
    rectifyStateWithArrayBoundsExceptionHandler();
    return false;
  }

  /**
   * Generate a check for 0 for the given operand
   * @return {@code true} if an unconditional trap is generated, {@code false} otherwise
   */
  private boolean do_IntZeroCheck(Operand div) {
    if (div instanceof IntConstantOperand) {
      if (((IntConstantOperand) div).value == 0) {
        endOfBasicBlock = true;
        rectifyStateWithArithmeticExceptionHandler();
        appendInstruction(Trap.create(TRAP, gc.temps.makeTempValidation(), TrapCodeOperand.DivByZero()));
        return true;
      } else {
        if (DBG_CF) {
          db("skipped gen of int_zero_check of " + div.asIntConstant().value);
        }
        setCurrentGuard(new TrueGuardOperand());
        return false;
      }
    }
    RegisterOperand guard = gc.temps.makeTempValidation();
    appendInstruction(ZeroCheck.create(INT_ZERO_CHECK, guard, div.copy()));
    setCurrentGuard(guard);
    rectifyStateWithArithmeticExceptionHandler();
    return false;
  }

  /**
   * Generate a check for 0 for the given operand
   * @return {@code true} if an unconditional trap is generated, {@code false} otherwise
   */
  private boolean do_LongZeroCheck(Operand div) {
    if (div instanceof LongConstantOperand) {
      if (((LongConstantOperand) div).value == 0) {
        endOfBasicBlock = true;
        rectifyStateWithArithmeticExceptionHandler();
        appendInstruction(Trap.create(TRAP, gc.temps.makeTempValidation(), TrapCodeOperand.DivByZero()));
        return true;
      } else {
        if (DBG_CF) {
          db("skipped gen of long_zero_check of " + div.asLongConstant().value);
        }
        setCurrentGuard(new TrueGuardOperand());
        return false;
      }
    }
    RegisterOperand guard = gc.temps.makeTempValidation();
    appendInstruction(ZeroCheck.create(LONG_ZERO_CHECK, guard, div.copy()));
    setCurrentGuard(guard);
    rectifyStateWithArithmeticExceptionHandler();
    return false;
  }

  /**
   * Generate a storecheck for the given array and elem
   * @param ref the array reference
   * @param elem the element to be written to the array
   * @param elemType the type of the array references elements
   * @return {@code true} if an unconditional throw is generated, {@code false} otherwise
   */
  private boolean do_CheckStore(Operand ref, Operand elem, TypeReference elemType) {
    if (!gc.doesCheckStore) return false;

    if (CF_CHECKSTORE) {
      // NOTE: BE WARY OF ADDITIONAL OPTIMZATIONS.
      // ARRAY SUBTYPING IS SUBTLE (see JLS 10.10) --dave
      if (elem.isDefinitelyNull()) {
        if (DBG_TYPE) db("skipping checkstore of null constant");
        return false;
      }
      if (elemType.isArrayType()) {
        TypeReference elemType2 = elemType;
        do {
          elemType2 = elemType2.getArrayElementType();
        } while (elemType2.isArrayType());
        RVMType et2 = elemType2.peekType();
        if (et2 != null) {
          if (et2.isPrimitiveType() || et2.isUnboxedType() || ((RVMClass) et2).isFinal()) {
            TypeReference myElemType = getRefTypeOf(elem);
            if (myElemType == elemType) {
              if (DBG_TYPE) {
                db("eliminating checkstore to an array with a final element type " + elemType);
              }
              return false;
            } else {
              // run time check is still necessary
            }
          }
        }
      } else {
        // elemType is class
        RVMType et = elemType.peekType();
        if (et != null && ((RVMClass) et).isFinal()) {
          if (getRefTypeOf(elem) == elemType) {
            if (DBG_TYPE) {
              db("eliminating checkstore to an array with a final element type " + elemType);
            }
            return false;
          } else {
            // run time check is still necessary
          }
        }
      }
    }

    RegisterOperand guard = gc.temps.makeTempValidation();
    if (isNonNull(elem)) {
      RegisterOperand newGuard = gc.temps.makeTempValidation();
      appendInstruction(Binary.create(GUARD_COMBINE, newGuard, getGuard(elem), getCurrentGuard()));
      appendInstruction(StoreCheck.create(OBJARRAY_STORE_CHECK_NOTNULL,
                                          guard,
                                          ref.copy(),
                                          elem.copy(),
                                          newGuard.copy()));
    } else {
      appendInstruction(StoreCheck.create(OBJARRAY_STORE_CHECK, guard, ref.copy(), elem.copy(), getCurrentGuard()));
    }
    setCurrentGuard(guard);
    rectifyStateWithArrayStoreExceptionHandler();
    return false;
  }

  //// GENERATE BRANCHING INSTRUCTIONS.
  /**
   * Get or create a block at the specified target.
   * Rectifies current state with target state.
   * Instructions to rectify state are appended to currentBBLE.
   * If the target is between bcodes.index() and runoff, runoff is
   * updated to be target.
   *
   * @param target target index
   */
  private BasicBlockLE getOrCreateBlock(int target) {
    return getOrCreateBlock(target, currentBBLE, stack, _localState);
  }

  /**
   * Get or create a block at the specified target.
   * If simStack is non-{@code null}, rectifies stack state with target stack state.
   * If simLocals is non-{@code null}, rectifies local state with target local state.
   * Any instructions needed to rectify stack/local state are appended to from.
   * If the target is between bcodes.index() and runoff, runoff is
   * updated to be target.
   *
   * @param target target index
   * @param from the block from which control is being transfered
   *                  and to which stack rectification instructions are added.
   * @param simStack stack state to rectify, or {@code null}
   * @param simLocals local state to rectify, or {@code null}
   */
  private BasicBlockLE getOrCreateBlock(int target, BasicBlockLE from, OperandStack simStack, Operand[] simLocals) {
    if ((target > bcodes.index()) && (target < runoff)) {
      if (DBG_BB || DBG_SELECTED) db("updating runoff from " + runoff + " to " + target);
      runoff = target;
    }
    return blocks.getOrCreateBlock(target, from, simStack, simLocals);
  }

  private BranchOperand generateTarget(int offset) {
    BasicBlockLE targetbble = getOrCreateBlock(offset + instrIndex);
    currentBBLE.block.insertOut(targetbble.block);
    endOfBasicBlock = true;
    if (DBG_CFG || DBG_SELECTED) {
      db("Added CFG edge from " + currentBBLE.block + " to " + targetbble.block);
    }
    return targetbble.block.makeJumpTarget();
  }

  // GOTO
  private Instruction _gotoHelper(int offset) {
    return Goto.create(GOTO, generateTarget(offset));
  }

  // helper function for if?? bytecodes
  private Instruction _intIfHelper(ConditionOperand cond) {
    int offset = bcodes.getBranchOffset();
    Operand op0 = popInt();
    if (offset == 3) {
      return null;             // remove frivolous IFs
    }
    if (CF_INTIF && op0 instanceof IntConstantOperand) {
      int c = cond.evaluate(((IntConstantOperand) op0).value, 0);
      if (c == ConditionOperand.TRUE) {
        if (DBG_CF) {
          db(cond + ": changed branch to goto because predicate (" + op0 + ") is constant true");
        }
        return _gotoHelper(offset);
      } else if (c == ConditionOperand.FALSE) {
        if (DBG_CF) {
          db(cond + ": eliminated branch because predicate (" + op0 + ") is constant false");
        }
        return null;
      }
    }
    fallThrough = true;
    if (!(op0 instanceof RegisterOperand)) {
      if (DBG_CF) db("generated int_ifcmp of " + op0 + " with 0");
      RegisterOperand guard = gc.temps.makeTempValidation();
      return IfCmp.create(INT_IFCMP,
                          guard,
                          op0,
                          new IntConstantOperand(0),
                          cond,
                          generateTarget(offset),
                          gc.getConditionalBranchProfileOperand(instrIndex - bciAdjustment, offset < 0));
    }
    RegisterOperand val = (RegisterOperand) op0;
    BranchOperand branch = null;
    if (lastInstr != null) {
      switch (lastInstr.getOpcode()) {
        case INSTANCEOF_opcode:
        case INSTANCEOF_UNRESOLVED_opcode: {
          if (DBG_TYPE) db("last instruction was instanceof");
          RegisterOperand res = InstanceOf.getResult(lastInstr);
          if (DBG_TYPE) db("result was in " + res + ", we are checking " + val);
          if (val.getRegister() != res.getRegister()) {
            break;            // not our value
          }
          Operand ref = InstanceOf.getRef(lastInstr);
          // should've been constant folded anyway
          if (!(ref instanceof RegisterOperand)) {
            break;
          }
          RegisterOperand guard = null;
          // Propagate types and non-nullness along the CFG edge where we
          // know that refReg is an instanceof type2
          RegisterOperand refReg = (RegisterOperand) ref;
          TypeReference type2 = InstanceOf.getType(lastInstr).getTypeRef();
          if (cond.isNOT_EQUAL()) {
            // IS an instance of on the branch-taken edge
            boolean generated = false;
            if (refReg.getRegister().isLocal()) {
              int locNum = gc.getLocalNumberFor(refReg.getRegister(), refReg.getType());
              if (locNum != -1) {
                Operand loc = getLocal(locNum);
                if (loc instanceof RegisterOperand) {
                  if (DBG_TYPE) {
                    db(val +
                       " is from instanceof test, propagating new type of " +
                       refReg +
                       " (" +
                       type2 +
                       ") to basic block at " +
                       offset);
                  }
                  RegisterOperand locr = (RegisterOperand) loc;
                  RegisterOperand tlocr = locr.copyU2U();
                  guard = gc.makeNullCheckGuard(tlocr.getRegister());
                  setGuard(tlocr, guard.copyD2U());
                  tlocr.clearDeclaredType();
                  tlocr.clearPreciseType();
                  tlocr.setType(type2);
                  setLocal(locNum, tlocr);
                  branch = generateTarget(offset);
                  generated = true;
                  setLocal(locNum, locr);
                }
              }
            }
            if (!generated) {
              branch = generateTarget(offset);
            }
          } else if (cond.isEQUAL()) {
            // IS an instance of on the fallthrough edge.
            branch = generateTarget(offset);
            if (refReg.getRegister().isLocal()) {
              int locNum = gc.getLocalNumberFor(refReg.getRegister(), refReg.getType());
              if (locNum != -1) {
                Operand loc = getLocal(locNum);
                if (loc instanceof RegisterOperand) {
                  if (DBG_TYPE) {
                    db(val +
                       " is from instanceof test, propagating new type of " +
                       refReg +
                       " (" +
                       type2 +
                       ") along fallthrough edge");
                  }
                  RegisterOperand locr = (RegisterOperand) loc;
                  guard = gc.makeNullCheckGuard(locr.getRegister());
                  setGuard(locr, guard.copyD2U());
                  locr.clearDeclaredType();
                  locr.clearPreciseType();
                  locr.setType(type2);
                  setLocal(locNum, loc);
                }
              }
            }
          }
          if (guard == null) {
            guard = gc.temps.makeTempValidation();
          }
          return IfCmp.create(INT_IFCMP,
                              guard,
                              val,
                              new IntConstantOperand(0),
                              cond,
                              branch,
                              gc.getConditionalBranchProfileOperand(instrIndex - bciAdjustment, offset < 0));
        }
        case INSTANCEOF_NOTNULL_opcode: {
          if (DBG_TYPE) db("last instruction was instanceof");
          RegisterOperand res = InstanceOf.getResult(lastInstr);
          if (DBG_TYPE) {
            db("result was in " + res + ", we are checking " + val);
          }
          if (val.getRegister() != res.getRegister()) {
            break;            // not our value
          }
          Operand ref = InstanceOf.getRef(lastInstr);
          // should've been constant folded anyway
          if (!(ref instanceof RegisterOperand)) {
            break;
          }
          // Propagate types along the CFG edge where we know that
          // refReg is an instanceof type2
          RegisterOperand refReg = (RegisterOperand) ref;
          TypeReference type2 = InstanceOf.getType(lastInstr).getTypeRef();
          if (cond.isNOT_EQUAL()) {
            // IS an instance of on the branch-taken edge
            boolean generated = false;
            if (refReg.getRegister().isLocal()) {
              int locNum = gc.getLocalNumberFor(refReg.getRegister(), refReg.getType());
              if (locNum != -1) {
                Operand loc = getLocal(locNum);
                if (loc instanceof RegisterOperand) {
                  if (DBG_TYPE) {
                    db(val +
                       " is from instanceof test, propagating new type of " +
                       refReg +
                       " (" +
                       type2 +
                       ") to basic block at " +
                       offset);
                  }
                  RegisterOperand locr = (RegisterOperand) loc;
                  RegisterOperand tlocr = locr.copyU2U();
                  tlocr.clearDeclaredType();
                  tlocr.clearPreciseType();
                  tlocr.setType(type2);
                  setLocal(locNum, tlocr);
                  branch = generateTarget(offset);
                  generated = true;
                  setLocal(locNum, locr);
                }
              }
            }
            if (!generated) {
              branch = generateTarget(offset);
            }
          } else if (cond.isEQUAL()) {
            // IS an instance of on the fallthrough edge.
            branch = generateTarget(offset);
            if (refReg.getRegister().isLocal()) {
              int locNum = gc.getLocalNumberFor(refReg.getRegister(), refReg.getType());
              if (locNum != -1) {
                Operand loc = getLocal(locNum);
                if (loc instanceof RegisterOperand) {
                  if (DBG_TYPE) {
                    db(val +
                       " is from instanceof test, propagating new type of " +
                       refReg +
                       " (" +
                       type2 +
                       ") along fallthrough edge");
                  }
                  RegisterOperand locr = (RegisterOperand) loc;
                  locr.setType(type2);
                  locr.clearDeclaredType();
                  setLocal(locNum, loc);
                }
              }
            }
          }
          RegisterOperand guard = gc.temps.makeTempValidation();
          return IfCmp.create(INT_IFCMP,
                              guard,
                              val,
                              new IntConstantOperand(0),
                              cond,
                              branch,
                              gc.getConditionalBranchProfileOperand(instrIndex - bciAdjustment, offset < 0));
        }
        case DOUBLE_CMPG_opcode:
        case DOUBLE_CMPL_opcode:
        case FLOAT_CMPG_opcode:
        case FLOAT_CMPL_opcode:
        case LONG_CMP_opcode: {
          RegisterOperand res = Binary.getResult(lastInstr);
          if (val.getRegister() != res.getRegister()) {
            break;            // not our value
          }
          Operator operator = null;
          switch (lastInstr.getOpcode()) {
            case DOUBLE_CMPG_opcode:
              cond.translateCMPG();
              operator = DOUBLE_IFCMP;
              break;
            case DOUBLE_CMPL_opcode:
              cond.translateCMPL();
              operator = DOUBLE_IFCMP;
              break;
            case FLOAT_CMPG_opcode:
              cond.translateCMPG();
              operator = FLOAT_IFCMP;
              break;
            case FLOAT_CMPL_opcode:
              cond.translateCMPL();
              operator = FLOAT_IFCMP;
              break;
            case LONG_CMP_opcode:
              operator = LONG_IFCMP;
              break;
            default:
              OptimizingCompilerException.UNREACHABLE();
              break;
          }
          Operand val1 = Binary.getClearVal1(lastInstr);
          Operand val2 = Binary.getClearVal2(lastInstr);
          if (!(val1 instanceof RegisterOperand)) {
            // swap operands
            Operand temp = val1;
            val1 = val2;
            val2 = temp;
            cond = cond.flipOperands();
          }
          lastInstr.remove();
          lastInstr = null;
          branch = generateTarget(offset);
          RegisterOperand guard = gc.temps.makeTempValidation();
          return IfCmp.create(operator,
                              guard,
                              val1,
                              val2,
                              cond,
                              branch,
                              gc.getConditionalBranchProfileOperand(instrIndex - bciAdjustment, offset < 0));
        }
        default:
          // Fall through and Insert INT_IFCMP
          break;
      }
    }
    branch = generateTarget(offset);
    RegisterOperand guard = gc.temps.makeTempValidation();
    return IfCmp.create(INT_IFCMP,
                        guard,
                        val,
                        new IntConstantOperand(0),
                        cond,
                        branch,
                        gc.getConditionalBranchProfileOperand(instrIndex - bciAdjustment, offset < 0));
  }

  // helper function for if_icmp?? bytecodes
  private Instruction _intIfCmpHelper(ConditionOperand cond) {
    int offset = bcodes.getBranchOffset();
    Operand op1 = popInt();
    Operand op0 = popInt();
    if (offset == 3) {
      return null;             // remove frivolous INF_IFCMPs
    }
    if (!(op0 instanceof RegisterOperand)) {
      // swap operands
      Operand temp = op0;
      op0 = op1;
      op1 = temp;
      cond = cond.flipOperands();
    }
    if (CF_INTIFCMP && (op0 instanceof IntConstantOperand) && (op1 instanceof IntConstantOperand)) {
      int c = cond.evaluate(((IntConstantOperand) op0).value, ((IntConstantOperand) op1).value);
      if (c == ConditionOperand.TRUE) {
        if (DBG_CF) {
          db(cond + ": changed branch to goto because predicate (" + op0 + ", " + op1 + ") is constant true");
        }
        return _gotoHelper(offset);
      } else if (c == ConditionOperand.FALSE) {
        if (DBG_CF) {
          db(cond + ": eliminated branch because predicate (" + op0 + "," + op1 + ") is constant false");
        }
        return null;
      }
    }
    fallThrough = true;
    RegisterOperand guard = gc.temps.makeTempValidation();
    return IfCmp.create(INT_IFCMP,
                        guard,
                        op0,
                        op1,
                        cond,
                        generateTarget(offset),
                        gc.getConditionalBranchProfileOperand(instrIndex - bciAdjustment, offset < 0));
  }

  // helper function for ifnull/ifnonnull bytecodes
  private Instruction _refIfNullHelper(ConditionOperand cond) {
    if (VM.VerifyAssertions) VM._assert(cond.isEQUAL() || cond.isNOT_EQUAL());
    int offset = bcodes.getBranchOffset();
    Operand op0 = popRef();
    if (offset == 3) {
      return null;             // remove frivolous REF_IFs
    }
    if (CF_REFIF) {
      if (op0.isDefinitelyNull()) {
        if (cond.isEQUAL()) {
          if (DBG_CF) {
            db(cond + ": changed branch to goto because predicate is true");
          }
          return _gotoHelper(offset);
        } else {
          if (DBG_CF) {
            db(cond + ": eliminated branch because predicate is false");
          }
          return null;
        }
      }
      if (isNonNull(op0)) {
        if (cond.isNOT_EQUAL()) {
          if (DBG_CF) {
            db(cond + ": changed branch to goto because predicate is true");
          }
          return _gotoHelper(offset);
        } else {
          if (DBG_CF) {
            db(cond + ": eliminated branch because predicate is false");
          }
          return null;
        }
      }
    }
    RegisterOperand ref = (RegisterOperand) op0;
    BranchOperand branch = null;
    RegisterOperand guard = null;
    if (cond.isEQUAL()) {
      branch = generateTarget(offset);
      if (ref.getRegister().isLocal()) {
        int locNum = gc.getLocalNumberFor(ref.getRegister(), ref.getType());
        if (locNum != -1) {
          Operand loc = getLocal(locNum);
          if (loc instanceof RegisterOperand) {
            RegisterOperand locr = (RegisterOperand) loc;
            guard = gc.makeNullCheckGuard(locr.getRegister());
            setGuard(locr, guard.copyD2U());
            setLocal(locNum, loc);
          }
        }
      }
    } else {
      boolean generated = false;
      if (ref.getRegister().isLocal()) {
        int locNum = gc.getLocalNumberFor(ref.getRegister(), ref.getType());
        if (locNum != -1) {
          Operand loc = getLocal(locNum);
          if (loc instanceof RegisterOperand) {
            RegisterOperand locr = (RegisterOperand) loc;
            RegisterOperand tlocr = locr.copyU2U();
            guard = gc.makeNullCheckGuard(locr.getRegister());
            setGuard(tlocr, guard.copyD2U());
            setLocal(locNum, tlocr);
            branch = generateTarget(offset);
            generated = true;
            setLocal(locNum, locr);
          }
        }
      }
      if (!generated) {
        branch = generateTarget(offset);
      }
    }
    fallThrough = true;
    if (guard == null) {
      guard = gc.temps.makeTempValidation();
    }
    return IfCmp.create(REF_IFCMP,
                        guard,
                        ref,
                        new NullConstantOperand(),
                        cond,
                        branch,
                        gc.getConditionalBranchProfileOperand(instrIndex - bciAdjustment, offset < 0));
  }

  // helper function for if_acmp?? bytecodes
  private Instruction _refIfCmpHelper(ConditionOperand cond) {
    if (VM.VerifyAssertions) VM._assert(cond.isEQUAL() || cond.isNOT_EQUAL());
    int offset = bcodes.getBranchOffset();
    Operand op1 = popRef();
    Operand op0 = popRef();
    if (offset == 3) {
      return null;             // remove frivolous REF_IFCMPs
    }
    if (!(op0 instanceof RegisterOperand)) {
      // swap operands
      Operand temp = op0;
      op0 = op1;
      op1 = temp;
      cond = cond.flipOperands();
    }
    if (CF_REFIFCMP && op0.isDefinitelyNull() && op1.isDefinitelyNull()) {
      if (cond.isEQUAL()) {
        if (DBG_CF) {
          db(cond + ": changed branch to goto because predicate is true");
        }
        return _gotoHelper(offset);
      } else {
        if (DBG_CF) {
          db(cond + ": eliminated branch because predicate is false");
        }
        return null;
      }
    }
    fallThrough = true;
    RegisterOperand guard = gc.temps.makeTempValidation();
    return IfCmp.create(REF_IFCMP,
                        guard,
                        op0,
                        op1,
                        cond,
                        generateTarget(offset),
                        gc.getConditionalBranchProfileOperand(instrIndex - bciAdjustment, offset < 0));
  }

////REPLACE LOCALS ON STACK.
  //



  /**
   * Replaces copies of local {@code <#index,type>} with
   * newly-generated temporaries, and generates the necessary move instructions.
   * @param index the local's index
   * @param type the local's type
   */
  private void replaceLocalsOnStack(int index, TypeReference type) {
    int i;
    int size = stack.getSize();
    for (i = 0; i < size; ++i) {
      Operand op = stack.getFromTop(i);
      if (gc.isLocal(op, index, type)) {
        RegisterOperand lop = (RegisterOperand) op;
        RegisterOperand t = gc.temps.makeTemp(lop);
        Instruction s = Move.create(IRTools.getMoveOp(t.getType()), t, op);
        stack.replaceFromTop(i, t.copyD2U());
        s.position = gc.inlineSequence;
        s.bcIndex = instrIndex;
        if (DBG_LOCAL || DBG_SELECTED) {
          db("replacing local " + index + " at " + i + " from tos with " + t);
        }
        appendInstruction(s);
      }
    }
  }

  //////////
  // EXCEPTION HANDLERS.

  //////////
  // Some common cases to make the code more readable...

  private BasicBlock rectifyStateWithNullPtrExceptionHandler() {
    return rectifyStateWithNullPtrExceptionHandler(false);
  }

  private BasicBlock rectifyStateWithArrayBoundsExceptionHandler() {
    return rectifyStateWithArrayBoundsExceptionHandler(false);
  }

  private BasicBlock rectifyStateWithArithmeticExceptionHandler() {
    return rectifyStateWithArithmeticExceptionHandler(false);
  }

  private BasicBlock rectifyStateWithArrayStoreExceptionHandler() {
    return rectifyStateWithArrayStoreExceptionHandler(false);
  }

  private BasicBlock rectifyStateWithErrorHandler() {
    return rectifyStateWithErrorHandler(false);
  }

  public void rectifyStateWithExceptionHandlers() {
    rectifyStateWithExceptionHandlers(false);
  }

  public BasicBlock rectifyStateWithExceptionHandler(TypeReference exceptionType) {
    return rectifyStateWithExceptionHandler(exceptionType, false);
  }

  private BasicBlock rectifyStateWithNullPtrExceptionHandler(boolean linkToExitIfUncaught) {
    TypeReference et = TypeReference.JavaLangNullPointerException;
    return rectifyStateWithExceptionHandler(et, linkToExitIfUncaught);
  }

  private BasicBlock rectifyStateWithArrayBoundsExceptionHandler(boolean linkToExitIfUncaught) {
    TypeReference et = TypeReference.JavaLangArrayIndexOutOfBoundsException;
    return rectifyStateWithExceptionHandler(et, linkToExitIfUncaught);
  }

  private BasicBlock rectifyStateWithArithmeticExceptionHandler(boolean linkToExitIfUncaught) {
    TypeReference et = TypeReference.JavaLangArithmeticException;
    return rectifyStateWithExceptionHandler(et, linkToExitIfUncaught);
  }

  private BasicBlock rectifyStateWithArrayStoreExceptionHandler(boolean linkToExitIfUncaught) {
    TypeReference et = TypeReference.JavaLangArrayStoreException;
    return rectifyStateWithExceptionHandler(et, linkToExitIfUncaught);
  }

  private BasicBlock rectifyStateWithErrorHandler(boolean linkToExitIfUncaught) {
    TypeReference et = TypeReference.JavaLangError;
    return rectifyStateWithExceptionHandler(et, linkToExitIfUncaught);
  }

  // If exactly 1 catch block is guarenteed to catch the exception,
  // then we return it.
  // Returning null means that no such block was found.
  private BasicBlock rectifyStateWithExceptionHandler(TypeReference exceptionType,
                                                          boolean linkToExitIfUncaught) {
    currentBBLE.block.setCanThrowExceptions();
    int catchTargets = 0;
    if (DBG_EX) db("\tchecking exceptions of " + currentBBLE.block);
    if (currentBBLE.handlers != null) {
      for (HandlerBlockLE xbble : currentBBLE.handlers) {
        if (DBG_EX) db("\texception block " + xbble.entryBlock);
        byte mustCatch = xbble.mustCatchException(exceptionType);
        if (mustCatch != NO || xbble.mayCatchException(exceptionType) != NO) {
          if (DBG_EX) {
            db("PEI of type " + exceptionType + " could be caught by " + xbble + " rectifying locals");
          }
          catchTargets++;
          blocks.rectifyLocals(_localState, xbble);
          currentBBLE.block.insertOut(xbble.entryBlock);
          if (DBG_CFG || DBG_SELECTED) {
            db("Added CFG edge from " + currentBBLE.block + " to " + xbble.entryBlock);
          }
        }
        if (mustCatch == YES) {
          if (DBG_EX) {
            db("\t" + xbble + " will defintely catch exceptions of type " + exceptionType);
          }
          if (DBG_EX && catchTargets == 1) {
            db("\t  and it is the only target");
          }
          return (catchTargets == 1) ? xbble.entryBlock : null;
        }
      }
    }
    // Now, consider the enclosing exception context.
    // NOTE: Because the locals of the current method can't
    // possibly matter to the locals of the enclosing method, it is
    // sufficient to add a CFG edge (no need to rectify locals).
    // It is the responsibility of the BC2IR object generating the
    // caller method to ensure that the exposed handler blocks are
    // generated if they are reachable from a callee.
    // See maybeInlineMethod.
    if (gc.enclosingHandlers != null) {
      for (Enumeration<BasicBlock> e = gc.enclosingHandlers.enumerator(); e.hasMoreElements();) {
        ExceptionHandlerBasicBlock xbb = (ExceptionHandlerBasicBlock) e.nextElement();
        byte mustCatch = xbb.mustCatchException(exceptionType);
        if (mustCatch != NO || xbb.mayCatchException(exceptionType) != NO) {
          if (DBG_EX) {
            db("PEI of type " + exceptionType + " could be caught by enclosing handler " + xbb);
          }
          catchTargets++;
          currentBBLE.block.insertOut(xbb);
          if (DBG_CFG || DBG_SELECTED) {
            db("Added CFG edge from " + currentBBLE.block + " to " + xbb);
          }
        }
        if (mustCatch == YES) {
          if (DBG_EX) {
            db("\t" + xbb + " will defintely catch exceptions of type " + exceptionType);
          }
          if (DBG_EX && catchTargets == 1) {
            db("\t  and it is the only target");
          }
          return (catchTargets == 1) ? xbb : null;
        }
      }
    }
    // If we get to here, then we didn't find a handler block that
    // is guarenteed to catch the exception. Therefore deal with the
    // possibly uncaught exception.
    currentBBLE.block.setMayThrowUncaughtException();
    if (linkToExitIfUncaught) {
      if (DBG_EX) {
        db("added explicit edge from " + currentBBLE + " to outermost exit");
      }
      currentBBLE.block.insertOut(gc.exit);
      if (DBG_CFG || DBG_SELECTED) {
        db("Added CFG edge from " + currentBBLE.block + " to exit");
      }
    }
    return null;
  }

  /*
   * Very similar to the above, but since we aren't told what might be thrown,
   * we are forced to connect to every in scope handler and can't
   * identify a definite target.
   */
  private void rectifyStateWithExceptionHandlers(boolean linkToExitIfUncaught) {
    currentBBLE.block.setCanThrowExceptions();
    currentBBLE.block.setMayThrowUncaughtException();
    if (linkToExitIfUncaught) {
      if (DBG_EX) {
        db("PEI of unknown type caused edge from " + currentBBLE + " to outermost exit");
      }
      currentBBLE.block.insertOut(gc.exit);
      if (DBG_CFG || DBG_SELECTED) {
        db("Added CFG edge from " + currentBBLE.block + " to exit");
      }
    }
    if (currentBBLE.handlers != null) {
      for (HandlerBlockLE xbble : currentBBLE.handlers) {
        if (DBG_EX) {
          db("PEI of unknown type could be caught by " + xbble + " rectifying locals");
        }
        blocks.rectifyLocals(_localState, xbble);
        currentBBLE.block.insertOut(xbble.entryBlock);
        if (DBG_CFG || DBG_SELECTED) {
          db("Added CFG edge from " + currentBBLE.block + " to " + xbble.entryBlock);
        }
      }
    }
    // Now, consider the enclosing exception context; ditto NOTE above.
    if (gc.enclosingHandlers != null) {
      for (Enumeration<BasicBlock> e = gc.enclosingHandlers.enumerator(); e.hasMoreElements();) {
        ExceptionHandlerBasicBlock xbb = (ExceptionHandlerBasicBlock) e.nextElement();
        if (DBG_EX) {
          db("PEI of unknown type could be caught by enclosing handler " + xbb);
        }
        currentBBLE.block.insertOut(xbb);
        if (DBG_CFG || DBG_SELECTED) {
          db("Added CFG edge from " + currentBBLE.block + " to " + xbb);
        }
      }
    }
  }

  //////////
  // INLINING support
  //////////
  /**
   * Should we inline a call site?
   *
   * @param call the call instruction being considered for inlining
   * @param isExtant is the receiver of a virtual method an extant object?
   * @param realBCI the real bytecode index of the call instruction, not adjusted because of OSR
   */
  private InlineDecision shouldInline(Instruction call, boolean isExtant, int realBCI) {
    if (Call.getMethod(call).getTarget() == null) {
      return InlineDecision.NO("Target method is null");
    }
    CompilationState state = new CompilationState(call, isExtant, gc.options, gc.original_cm, realBCI);
    InlineDecision d = gc.inlinePlan.shouldInline(state);
    return d;
  }

  /**
   * Attempt to inline a method. This may fail.
   *
   * @param inlDec the inline decision for this call site
   * @param callSite the call instruction we are attempting to inline
   * @return {@code true} if inlining succeeded, {@code false} otherwise
   */
  private boolean maybeInlineMethod(InlineDecision inlDec, Instruction callSite) {
    if (inlDec.isNO()) {
      return false;
    }

    // Insert OsrBarrier point before the callsite which is going to be
    // inlined, attach the OsrBarrier instruction to callsite's scratch
    // object, then the callee can find this barrier

    // verify it
    if (this.osrGuardedInline) {
      if (VM.VerifyAssertions) VM._assert(lastOsrBarrier != null);
      callSite.scratchObject = lastOsrBarrier;
    }

    // Execute the inline decision.
    // NOTE: It is tempting to wrap the call to Inliner.execute in
    // a try/catch block that suppresses MagicNotImplemented failures
    // by "backing out" the attempted inlining of a method that contained
    // an unimplemented magic.  Unfortunately, this is somewhat hard to do
    // cleanly, since exceptional control flow can inject control flow graph
    // edges from inlinedContext to blocks in the enclosing caller CFG.
    // These are not easy to find and remove because inlinedContext is not
    // well-formed (the exception was thrown while generating the IR, in
    // particular before calling finalPass, therefore the inlined CFG
    // is not formed and finding all of its member blocks is somewhat awkward).
    // We could write code to deal with this, but since in practice the
    // opt compiler implements all but a few fringe magics, it is just fine
    // to completely give up rather than take heroic measures here.
    // In a few cases we do care about, we use NoInlinePragma to
    // prevent the opt compiler from inlining a method that contains an
    // unimplemented magic.
    GenerationContext inlinedContext =
        Inliner.execute(inlDec, gc, currentBBLE.block.exceptionHandlers, callSite);

    inlinedSomething = true;
    // TODO: We're currently not keeping track if any of the
    // enclosing exception handlers are actually reachable from
    // this inlined callee.
    // Therefore we simply force all of them to be generated wrt
    // the state of the local variables in currentBBLE.
    // This can result in generating unreachable handlers
    // (if no PEI can reach them) and in generating suboptimal
    // catch blocks (by merging in currentBBLE's local state
    // into catch blocks that can't actually be reached from the inlined CFG).
    // I strongly suspect it's not worth worrying about this.....
    // dead code elimination should zap the unreachable handlers,
    // and we shouldn't care too  much about the
    // optimization possibilities lost by the extra local rectification.
    // Especially since the odds of currentBBLE actually having
    // unreachable handler blocks is darn close to zero. --dave 9/21/99.
    // NOTE: No need to add CFG edges (they were added as needed
    // during generation of the callee)
    if (currentBBLE.handlers != null) {
      for (HandlerBlockLE handler : currentBBLE.handlers) {
        blocks.rectifyLocals(_localState, handler);
      }
    }
    if (inlinedContext.epilogue != null) {
      // Wrap a synthetic BBLE around GenerationContext.epilogue and
      // pass it as from to getOrCreateBlock.
      // This causes any compensation code inserted by getOrCreateBlock
      // into the epilogue of the inlined method (see inlineTest7)
      BasicBlockLE epilogueBBLE = new BasicBlockLE(0);
      epilogueBBLE.block = inlinedContext.epilogue;
      if (inlinedContext.result != null) {
        // If the call has a result, _callHelper allocated a new
        // temp for it and pushed it onto the expression stack.
        // But, since we successfully inlined the call and
        // inlinedContext.epilogue != null,
        // we can use inlinedContext.result to obtain better
        // downstream information about the inlined callee's return value.
        // Therefore we'll pop the old callSite.result off the stack
        // and push result instead.
        // NOTE: It's critical that we pop callSite.result
        // _before_ we copy the stack state into epilogueBBLE!
        // Otherwise we'll end up with bogus code in the inlined
        // method's prologue due to stack saving!!!!
        TypeReference resultType = Call.getResult(callSite).getType();
        pop(resultType);        // throw away callSite.result
      }
      blocks.rectifyStacks(currentBBLE.block, stack, epilogueBBLE);
      if (inlinedContext.result != null) {
        TypeReference resultType = Call.getResult(callSite).getType();
        push(inlinedContext.result, resultType);
      }
      epilogueBBLE.copyIntoLocalState(_localState);
      BasicBlockLE afterBBLE = blocks.getOrCreateBlock(bcodes.index(), epilogueBBLE, stack, _localState);
      // Create the InliningBlockLE and initialize fallThrough links.
      InliningBlockLE inlinedCallee = new InliningBlockLE(inlinedContext, epilogueBBLE);
      currentBBLE.fallThrough = inlinedCallee;
      currentBBLE.block.insertOut(inlinedCallee.gc.cfg.firstInCodeOrder());
      epilogueBBLE.fallThrough = afterBBLE;
      epilogueBBLE.block.insertOut(epilogueBBLE.fallThrough.block);
    } else {
      // All exits from the callee were via throws.
      // Therefore the next basic block is unreachable (unless
      // there is a branch to it from somewhere else in the current method,
      // which will naturally be handled when we generate the branch).
      InliningBlockLE inlinedCallee = new InliningBlockLE(inlinedContext, null);
      currentBBLE.fallThrough = inlinedCallee;
      currentBBLE.block.insertOut(inlinedCallee.gc.cfg.firstInCodeOrder());
    }
    endOfBasicBlock = true;
    return true;
  }

  /* create an OSR Barrier instruction at the current position.
   */
  private Instruction _createOsrBarrier() {
    ArrayList<Operand> livevars = new ArrayList<Operand>();

    /* for local variables, we have to use helper to make a register. */
    /* ltypes and stypes should be the full length
     * WARNING: what's the order of DUMMY and LONG?
     */
    int localnum = _localState.length;
    byte[] ltypes = new byte[localnum];

    int num_llocals = 0;
    for (int i = 0, n = _localState.length; i < n; i++) {
      Operand op = _localState[i];

      if ((op != null) && (op != DUMMY)) {
        livevars.add(_loadLocalForOSR(op));
        num_llocals++;

        if (op instanceof ReturnAddressOperand) {
          ltypes[i] = ReturnAddressTypeCode;
        } else {
          TypeReference typ = op.getType();
          if (typ.isWordLikeType() || (typ == TypeReference.NULL_TYPE)) {
            ltypes[i] = WordTypeCode;
          } else {
            ltypes[i] = typ.getName().parseForTypeCode();
          }
        }

      } else {
        ltypes[i] = VoidTypeCode;
      }
    }
    int stacknum = stack.getSize();
    byte[] stypes = new byte[stacknum];

    /* the variable on stack can be used directly ? */
    int num_lstacks = 0;
    for (int i = 0, n = stack.getSize(); i < n; i++) {
      Operand op = stack.peekAt(i);

      if ((op != null) && (op != DUMMY)) {

        if (op.isRegister()) {
          livevars.add(op.asRegister().copyU2U());
        } else {
          livevars.add(op.copy());
        }

        num_lstacks++;

        if (op instanceof ReturnAddressOperand) {
          stypes[i] = ReturnAddressTypeCode;
        } else {
          TypeReference typ = op.getType();
          if (typ.isWordLikeType() || (typ == TypeReference.NULL_TYPE)) {
            stypes[i] = WordTypeCode;
          } else {
            /* for stack operand, reverse the order for long and double */
            byte tcode = typ.getName().parseForTypeCode();
            if ((tcode == LongTypeCode) || (tcode == DoubleTypeCode)) {
              stypes[i - 1] = tcode;
              stypes[i] = VoidTypeCode;
            } else {
              stypes[i] = tcode;
            }
          }
        }

      } else {
        stypes[i] = VoidTypeCode;
      }
    }

    Instruction barrier = OsrBarrier.create(OSR_BARRIER, null, // temporarily
                                                num_llocals + num_lstacks);

    for (int i = 0, n = livevars.size(); i < n; i++) {
      Operand op = livevars.get(i);
      if (op instanceof ReturnAddressOperand) {
        int tgtpc = ((ReturnAddressOperand) op).retIndex - gc.method.getOsrPrologueLength();
        op = new IntConstantOperand(tgtpc);
      } else if (op instanceof LongConstantOperand) {
        op = _prepareLongConstant(op);
      } else if (op instanceof DoubleConstantOperand) {
        op = _prepareDoubleConstant(op);
      }

      if (VM.VerifyAssertions) VM._assert(op != null);

      OsrBarrier.setElement(barrier, i, op);
    }

    // patch type info operand
    OsrTypeInfoOperand typeinfo = new OsrTypeInfoOperand(ltypes, stypes);

    OsrBarrier.setTypeInfo(barrier, typeinfo);

    /* if the current method is for specialization, the bcIndex
     * has to be adjusted at "OsrPointConstructor".
     */
    barrier.position = gc.inlineSequence;
    barrier.bcIndex = instrIndex;

    return barrier;
  }

  /** special process for long/double constants */
  private Operand _prepareLongConstant(Operand op) {
    /* for long and double constants, always move them to a register,
     * therefor, BURS will split it in two registers.
     */
    RegisterOperand t = gc.temps.makeTemp(op.getType());
    appendInstruction(Move.create(LONG_MOVE, t, op));

    return t.copyD2U();
  }

  /** special process for long/double constants */
  private Operand _prepareDoubleConstant(Operand op) {
    /* for long and double constants, always move them to a register,
     * therefor, BURS will split it in two registers.
     */
    RegisterOperand t = gc.temps.makeTemp(op.getType());
    appendInstruction(Move.create(DOUBLE_MOVE, t, op));

    return t.copyD2U();
  }

  /**
   * make a temporary register, and create a move instruction
   * @param op the local variable.
   * @return operand marked as use.
   */
  private Operand _loadLocalForOSR(Operand op) {

    /* return address is processed specially */
    if (op instanceof ReturnAddressOperand) {
      return op;
    }

    RegisterOperand t = gc.temps.makeTemp(op.getType());

    byte tcode = op.getType().getName().parseForTypeCode();

    Operator operator = null;

    switch (tcode) {
      case ClassTypeCode:
      case ArrayTypeCode:
        operator = REF_MOVE;
        break;
      case BooleanTypeCode:
      case ByteTypeCode:
      case ShortTypeCode:
      case CharTypeCode:
      case IntTypeCode:
        operator = INT_MOVE;
        break;
      case LongTypeCode:
        operator = LONG_MOVE;
        break;
      case FloatTypeCode:
        operator = FLOAT_MOVE;
        break;
      case DoubleTypeCode:
        operator = DOUBLE_MOVE;
        break;
      case VoidTypeCode:
        return null;
    }

    appendInstruction(Move.create(operator, t, op.copy()));
    return t.copyD2U();
  }

  /**
   * Creates an OSR point instruction with its dependent OsrBarrier
   * which provides type and variable information.
   * The OsrPoint instruction is going to be refilled immediately
   * after BC2IR, before any other optimizations.
   */
  public static Instruction _osrHelper(Instruction barrier) {
    Instruction inst = OsrPoint.create(YIELDPOINT_OSR, null,  // currently unknown
                                           0);    // currently unknown
    inst.scratchObject = barrier;
    return inst;
  }

  //// LOCAL STATE.
  /**
   * Gets the specified local variable. This can return an RegisterOperand
   * which refers to the given local, or some other kind of operand (if the
   * local variable is assumed to contain a particular value.)
   *
   * @param i local variable number
   */
  private Operand getLocal(int i) {
    Operand local = _localState[i];
    if (DBG_LOCAL || DBG_SELECTED) db("getting local " + i + " for use: " + local);
    return local.copy();
  }

  /**
   * Gets the specified local variable (long, double). This can return an
   * RegisterOperand which refers to the given local, or some other kind
   * of operand (if the local variable is assumed to contain a given value.)
   *
   * @param i local variable number
   */
  private Operand getLocalDual(int i) {
    if (VM.VerifyAssertions) VM._assert(_localState[i + 1] == DUMMY);
    Operand local = _localState[i];
    if (DBG_LOCAL || DBG_SELECTED) db("getting local " + i + " for use: " + local);
    return local.copy();
  }

  /**
   * Set the specified local variable
   *
   * @param i local variable number
   * @param op Operand to store in the local
   */
  private void setLocal(int i, Operand op) {
    if (DBG_LOCAL || DBG_SELECTED) db("setting local " + i + " with " + op);
    _localState[i] = op;
  }

  /**
   * Set the specified local variable
   *
   * @param i local variable number
   * @param op Operand to store in the local
   */
  private void setLocalDual(int i, Operand op) {
    if (DBG_LOCAL || DBG_SELECTED) db("setting dual local " + i + " with " + op);
    _localState[i] = op;
    _localState[i + 1] = DUMMY;
  }

  //////////////////////////////////////////////
  // vvv Various classes for internal use vvv //
  //////////////////////////////////////////////

  /**
   * A somewhat complex subtask of IR generation is to discover and maintain
   * the set of basic blocks that are being generated.
   * This class encapsulates that functionality.
   * The backing data store is a red/black tree, but there are a number of
   * very specialized operations that are performed during search/insertion
   * so we roll our own instead of using one from the standard library.
   */
  private static final class BBSet implements IRGenOptions {
    /** root of the backing red/black tree*/
    private BasicBlockLE root;

    /**
     * is it the case that we can ignore JSR processing because
     * BC2IR has not yet generated a JSR bytecode in this method?
     */
    private boolean noJSR = true;

    /** entry block of the CFG */
    private final BasicBlockLE entry;

    /** associated generation context */
    private final GenerationContext gc;

    /** associated bytecodes */
    private final BytecodeStream bcodes;

    // Fields to support generation/identification of catch blocks
    /** Start bytecode index for each exception handler ranges */
    private int[] startPCs;

    /** End bytecode index for each exception handler range */
    private int[] endPCs;

    /** Start bytecode index of each the exception handler */
    private int[] handlerPCs;

    /** Type of exception handled by each exception handler range. */
    private TypeOperand[] exceptionTypes;

    /**
     * Initialize the BBSet to handle basic block generation for the argument
     * generation context and bytecode info.
     * @param gc the generation context to generate blocks for
     * @param bcodes the bytecodes of said generation context
     * @param localState the state of the local variables for the block
     *                   beginning at bytecode 0.
     */
    BBSet(GenerationContext gc, BytecodeStream bcodes, Operand[] localState) {
      this.gc = gc;
      this.bcodes = bcodes;

      // Set up internal data structures to deal with exception handlers
      parseExceptionTables();

      // Create the entry block, setting root as a sideffect.
      entry = _createBBLE(0, null, null, false);
      entry.setStackKnown();
      entry.copyIntoLocalState(localState);
    }

    /** return the entry BBLE */
    BasicBlockLE getEntry() { return entry; }

    /**
     * Notify the BBSet that BC2IR has encountered a JSR bytecode.
     * This enables more complex logic in getOrCreateBlock to drive
     * the basic block specialization that is the key to JSR inlining.
     */
    void seenJSR() { noJSR = false; }

    /**
     * Return a enumeration of the BasicBlockLE's currently in the BBSet.
     */
    Enumeration<BasicBlockLE> contents() {
      return TreeEnumerator.enumFromRoot(root);
    }

    /**
     * Gets the bytecode index of the block in the set which has the
     * next-higher bytecode index.
     * Returns bcodes.length() if x is currently the block with the highest
     * starting bytecode index.
     * @param x basic block to start at.
     */
    int getNextBlockBytecodeIndex(BasicBlockLE x) {
      BasicBlockLE nextBBLE = getSuccessor(x, x.low);
      return nextBBLE == null ? bcodes.length() : nextBBLE.low;
    }

    /**
     * Finds the next ungenerated block, starting at the argument
     * block and searching forward, wrapping around to the beginning.
     * If all blocks are generated, it returns null.
     * @param start the basic block at which to start looking.
     */
    BasicBlockLE getNextEmptyBlock(BasicBlockLE start) {
      if (DBG_BBSET) db("getting the next empty block after " + start);

      // Look for an ungenerated block after start.
      TreeEnumerator e = TreeEnumerator.enumFromNode(start);
      while (e.hasMoreElements()) {
        BasicBlockLE block = e.next();
        if (DBG_BBSET) {
          db("Considering block " + block + " " + block.genState());
        }
        if (block.isReadyToGenerate()) {
          if (DBG_BBSET) db("block " + block + " is not yet generated");
          return block;
        }
      }

      // There were none. Start looking from the beginning.
      if (DBG_BBSET) db("at end of bytecodes, restarting at beginning");
      e = TreeEnumerator.enumFromRoot(root);
      while (true) {
        BasicBlockLE block = e.next();
        if (block == start) {
          if (DBG_BBSET) db("wrapped around, no more empty blocks");
          return null;
        }
        if (DBG_BBSET) {
          db("Considering block " + block + " " + block.genState());
        }
        if (block.isReadyToGenerate()) {
          if (DBG_BBSET) db("block " + block + " is not yet generated");
          return block;
        }
      }
    }

    /**
     * Get or create a block at the specified target.
     * If simStack is non-null, rectifies stack state with target stack state.
     * If simLocals is non-null, rectifies local state with target local state.
     * Any instructions needed to rectify stack/local state are appended to
     * from.
     *
     * @param target target index
     * @param from the block from which control is being transfered
     *                  and to which rectification instructions are added.
     * @param simStack stack state to rectify, or null
     * @param simLocals local state to rectify, or null
     */
    BasicBlockLE getOrCreateBlock(int target, BasicBlockLE from, OperandStack simStack, Operand[] simLocals) {
      if (DBG_BB || DBG_SELECTED) {
        db("getting block " +
           target +
           ", match stack: " +
           (simStack != null) +
           " match locals: " +
           (simLocals != null));
      }
      return getOrCreateBlock(root, true, target, from, simStack, simLocals);
    }

    /**
     * Mark a previously generated block for regeneration.
     * We define this method here so that in the future
     * we can implement a more efficient getNextEmptyBlock that
     * (1) avoids generating lots of blocks when a CFG predecessor has a
     * pending regeneration and (2) avoids the scan through all blocks when
     * there are no more blocks left to generate.
     */
    private void markBlockForRegeneration(BasicBlockLE p) {
      if (DBG_REGEN) db("marking " + p + " for regeneration");
      if (p.fallThrough != null && p.fallThrough instanceof InliningBlockLE) {
        // if the fallthrough out edge of this block is an
        // InlineMethodBasicBlock, then the inlined method must also be
        // regenerated.  In preparation for this, we must delete all out
        // edges from the inlined method to the caller.
        // (These arise from thrown/caught exceptions.)
        InliningBlockLE imbb = (InliningBlockLE) p.fallThrough;
        imbb.deleteAllOutEdges();
      }
      // discard any "real" instructions in the block
      if (!p.block.isEmpty()) {
        p.block.discardInstructions();
      }
      p.setSelfRegen();
      p.clearGenerated();
      p.fallThrough = null;
      // If p had a non-empty stack on entry, we need to go through it
      // and copy all of its operands (they may point to instructions
      // we just blew away, but then again they may not (may not be in p),
      // so we can't simply null out the instruction field);
      if (p.stackState != null) {
        int i = p.stackState.getSize();
        while (i-- > 0) {
          Operand op = p.stackState.getFromTop(i);
          p.stackState.replaceFromTop(i, op.copy());
        }
      }
    }

    /**
     * Rectify the given stack state with the state contained in the given
     * BBLE, adding the necessary move instructions to the end of the given
     * basic block to make register numbers agree and rectify mis-matched constants.
     * <p>
     * @param block basic block to append move instructions to
     * @param stack stack to copy
     * @param p BBLE to copy stack state into
     */
    void rectifyStacks(BasicBlock block, OperandStack stack, BasicBlockLE p) {
      if (stack == null || stack.isEmpty()) {
        if (VM.VerifyAssertions) VM._assert(p.stackState == null);
        if (!p.isStackKnown()) {
          p.setStackKnown();
        }
        if (DBG_STACK || DBG_SELECTED) {
          db("Rectified empty expression stack into " + p + "(" + p.block + ")");
        }
        return;
      }
      boolean generated = p.isGenerated();
      // (1) Rectify the stacks.
      if (!p.isStackKnown()) {
        // First time we reached p. Thus, its expression stack
        // is implicitly top and the meet degenerates to a copy operation
        // with possibly some register renaming.
        // (We need to ensure that non-local registers appear at
        // most once on each expression stack).
        if (DBG_STACK || DBG_SELECTED) {
          db("First stack rectifiction for " + p + "(" + p.block + ") simply saving");
        }
        if (VM.VerifyAssertions) VM._assert(p.stackState == null);
        p.stackState = new OperandStack(stack.getCapacity());
        for (int i = stack.getSize() - 1; i >= 0; i--) {
          Operand op = stack.getFromTop(i);
          if (op == DUMMY) {
            p.stackState.push(DUMMY);
          } else if (op instanceof RegisterOperand) {
            RegisterOperand rop = op.asRegister();
            if (rop.getRegister().isLocal()) {
              RegisterOperand temp = gc.temps.makeTemp(rop);
              temp.setInheritableFlags(rop);
              setGuard(temp, getGuard(rop));
              Instruction move = Move.create(IRTools.getMoveOp(rop.getType()), temp, rop.copyRO());
              move.bcIndex = RECTIFY_BCI;
              move.position = gc.inlineSequence;
              block.appendInstructionRespectingTerminalBranch(move);
              p.stackState.push(temp.copy());
              if (DBG_STACK || DBG_SELECTED) {
                db("Inserted " + move + " into " + block + " to rename local");
              }
            } else {
              p.stackState.push(rop.copy());
            }
          } else {
            p.stackState.push(op.copy());
          }
        }
        p.setStackKnown();
      } else {
        // A real rectification.
        // We need to update mergedStack such that
        // mergedStack[i] = meet(mergedStack[i], stack[i]).
        if (DBG_STACK || DBG_SELECTED) db("rectifying stacks");
        try {
          if (VM.VerifyAssertions) {
            VM._assert(stack.getSize() == p.stackState.getSize());
          }
        } catch (NullPointerException e) {
          System.err.println("stack size " + stack.getSize());
          System.err.println(stack);
          System.err.println(p.stackState);
          System.err.println(gc.method.toString());
          block.printExtended();
          p.block.printExtended();
          throw e;
        }
        for (int i = 0; i < stack.getSize(); ++i) {
          Operand sop = stack.getFromTop(i);
          Operand mop = p.stackState.getFromTop(i);
          if ((sop == DUMMY) || (sop instanceof ReturnAddressOperand)) {
            if (VM.VerifyAssertions) VM._assert(mop.similar(sop));
            continue;
          } else if (sop.isConstant() || mop.isConstant()) {
            if (mop.similar(sop)) {
              continue; // constants are similar; so we don't have to do anything.
            }
            // sigh. Non-similar constants.
            if (mop.isConstant()) {
              // Insert move instructions in all predecessor
              // blocks except 'block' to move mop into a register.
              RegisterOperand mopTmp = gc.temps.makeTemp(mop);
              if (DBG_STACK || DBG_SELECTED) db("Merged stack has constant operand " + mop);
              for (Enumeration<BasicBlock> preds = p.block.getIn(); preds.hasMoreElements();) {
                BasicBlock pred = preds.nextElement();
                if (pred == block) continue;
                injectMove(pred, mopTmp.copyRO(), mop.copy());
              }
              p.stackState.replaceFromTop(i, mopTmp.copy());
              if (generated) {
                if (DBG_STACK || DBG_SELECTED) {
                  db("\t...forced to regenerate " + p + " (" + p.block + ") because of this");
                }
                markBlockForRegeneration(p);
                generated = false;
                p.block.deleteOut();
                if (DBG_CFG || DBG_SELECTED) db("Deleted all out edges of " + p.block);
              }
              mop = mopTmp;
            }
            if (sop.isConstant()) {
              // Insert move instruction into block.
              RegisterOperand sopTmp = gc.temps.makeTemp(sop);
              if (DBG_STACK || DBG_SELECTED) db("incoming stack has constant operand " + sop);
              injectMove(block, sopTmp, sop);
              sop = sopTmp.copyRO();
            }
          }

          // sop and mop are RegisterOperands (either originally or because
          // we forced them to be above due to incompatible constants.
          RegisterOperand rsop = sop.asRegister();
          RegisterOperand rmop = mop.asRegister();
          if (rmop.getRegister() != rsop.getRegister()) {
            // must insert move at end of block to get register #s to match
            RegisterOperand temp = rsop.copyRO();
            temp.setRegister(rmop.getRegister());
            injectMove(block, temp, rsop.copyRO());
          }
          Operand meet = Operand.meet(rmop, rsop, rmop.getRegister());
          if (DBG_STACK || DBG_SELECTED) db("Meet of " + rmop + " and " + rsop + " is " + meet);
          if (meet != rmop) {
            if (generated) {
              if (DBG_STACK || DBG_SELECTED) {
                db("\t...forced to regenerate " + p + " (" + p.block + ") because of this");
              }
              markBlockForRegeneration(p);
              generated = false;
              p.block.deleteOut();
              if (DBG_CFG || DBG_SELECTED) db("Deleted all out edges of " + p.block);
            }
            p.stackState.replaceFromTop(i, meet);
          }
        }
      }
    }

    private void injectMove(BasicBlock block, RegisterOperand res, Operand val) {
      Instruction move = Move.create(IRTools.getMoveOp(res.getType()), res, val);
      move.bcIndex = RECTIFY_BCI;
      move.position = gc.inlineSequence;
      block.appendInstructionRespectingTerminalBranch(move);
      if (DBG_STACK || DBG_SELECTED) {
        db("Inserted " + move + " into " + block);
      }
    }

    /**
     * Rectify the given local variable state with the local variable state
     * stored in the given BBLE.
     *
     * @param localState local variable state to rectify
     * @param p target BBLE to rectify state to
     */
    void rectifyLocals(Operand[] localState, BasicBlockLE p) {
      if (!p.isLocalKnown()) {
        if (DBG_LOCAL || DBG_SELECTED) {
          db("rectifying with heretofore unknown locals, changing to save");
        }
        p.copyIntoLocalState(localState);
        return;
      }
      if (DBG_LOCAL || DBG_SELECTED) db("rectifying current local state with " + p);
      boolean generated = p.isGenerated();
      Operand[] incomingState = localState;
      Operand[] presentState = p.localState;
      if (VM.VerifyAssertions) {
        VM._assert(incomingState.length == presentState.length);
      }
      for (int i = 0, n = incomingState.length; i < n; ++i) {
        Operand pOP = presentState[i];
        Operand iOP = incomingState[i];
        if (pOP == iOP) {
          if (DBG_LOCAL || DBG_SELECTED) {
            db("local states have the exact same operand " + pOP + " for local " + i);
          }
        } else {
          boolean untyped = (pOP == null || pOP == DUMMY || pOP instanceof ReturnAddressOperand);
          Operand mOP = Operand.meet(pOP, iOP, untyped ? null : gc.localReg(i, pOP.getType()));
          if (DBG_LOCAL || DBG_SELECTED) db("Meet of " + pOP + " and " + iOP + " is " + mOP);
          if (mOP != pOP) {
            if (generated) {
              if (DBG_LOCAL || DBG_SELECTED) {
                db("\t...forced to regenerate " + p + " (" + p.block + ") because of this");
              }
              markBlockForRegeneration(p);
              generated = false;
              p.block.deleteOut();
              if (DBG_CFG || DBG_SELECTED) db("Deleted all out edges of " + p.block);
            }
            presentState[i] = mOP;
          }
        }
      }
    }

    /**
     * Do a final pass over the generated basic blocks to create
     * the initial code ordering. All blocks generated for the method
     * will be inserted after gc.prologue.<p>
     *
     * NOTE: Only some CFG edges are created here.....
     * we're mainly just patching together a code linearization.
     */
    void finalPass(boolean inlinedSomething) {
      TreeEnumerator e = TreeEnumerator.enumFromRoot(root);
      BasicBlock cop = gc.prologue;
      BasicBlockLE curr = getEntry();
      BasicBlockLE next = null;
      top:
      while (true) {
        // Step 0: If curr is the first block in a catch block,
        // inject synthetic entry block too.
        if (curr instanceof HandlerBlockLE) {
          // tell our caller that we actually put a handler in the final CFG.
          gc.generatedExceptionHandlers = true;
          HandlerBlockLE hcurr = (HandlerBlockLE) curr;
          if (DBG_FLATTEN) {
            db("injecting handler entry block " + hcurr.entryBlock + " before " + hcurr);
          }
          gc.cfg.insertAfterInCodeOrder(cop, hcurr.entryBlock);
          cop = hcurr.entryBlock;
        }
        // Step 1: Insert curr in the code order (after cop, updating cop).
        if (DBG_FLATTEN) db("flattening: " + curr + " (" + curr.block + ")");
        curr.setInCodeOrder();
        gc.cfg.insertAfterInCodeOrder(cop, curr.block);
        cop = curr.block;
        if (DBG_FLATTEN) {
          db("Current Code order for " + gc.method + "\n");
          for (BasicBlock bb = gc.prologue; bb != null; bb = (BasicBlock) bb.getNext()) {
            VM.sysWrite(bb + "\n");
          }
        }
        // Step 1.1 Sometimes (rarely) there will be an inscope
        // exception handler that wasn't actually generated.  If this happens,
        // make a new, filtered EHBBB to avoid later confusion.
        if (curr.handlers != null) {
          int notGenerated = 0;
          for (HandlerBlockLE handler : curr.handlers) {
            if (!handler.isGenerated()) {
              if (DBG_EX || DBG_FLATTEN) {
                db("Will remove unreachable handler " + handler + " from " + curr);
              }
              notGenerated++;
            }
          }
          if (notGenerated > 0) {
            if (notGenerated == curr.handlers.length) {
              if (DBG_EX || DBG_FLATTEN) {
                db("No (local) handlers were actually reachable for " + curr + "; setting to caller");
              }
              curr.block.exceptionHandlers = curr.block.exceptionHandlers.getCaller();
            } else {
              ExceptionHandlerBasicBlock[] nlh =
                  new ExceptionHandlerBasicBlock[curr.handlers.length - notGenerated];
              for (int i = 0, j = 0; i < curr.handlers.length; i++) {
                if (curr.handlers[i].isGenerated()) {
                  nlh[j++] = curr.handlers[i].entryBlock;
                } else {
                  if (VM.VerifyAssertions) {
                    VM._assert(curr.handlers[i].entryBlock.hasZeroIn(), "Non-generated handler with CFG edges");
                  }
                }
              }
              curr.block.exceptionHandlers =
                  new ExceptionHandlerBasicBlockBag(nlh, curr.block.exceptionHandlers.getCaller());
            }
          }
        }
        // Step 2: Identify the next basic block to add to the code order.
        // curr wants to fallthrough to an inlined method.
        // Inject the entire inlined CFG in the code order.
        // There's some fairly complicated coordination between this code,
        // GenerationContext, and maybeInlineMethod.  Sorry, but you'll
        // have to take a close look at all of these to see how it
        // all fits together....--dave
        if (curr.fallThrough != null && curr.fallThrough instanceof InliningBlockLE) {
          InliningBlockLE icurr = (InliningBlockLE) curr.fallThrough;
          BasicBlock forw = cop.nextBasicBlockInCodeOrder();
          BasicBlock calleeEntry = icurr.gc.cfg.firstInCodeOrder();
          BasicBlock calleeExit = icurr.gc.cfg.lastInCodeOrder();
          gc.cfg.breakCodeOrder(cop, forw);
          gc.cfg.linkInCodeOrder(cop, icurr.gc.cfg.firstInCodeOrder());
          gc.cfg.linkInCodeOrder(icurr.gc.cfg.lastInCodeOrder(), forw);
          if (DBG_CFG || DBG_SELECTED) {
            db("Added CFG edge from " + cop + " to " + calleeEntry);
          }
          if (icurr.epilogueBBLE != null) {
            if (DBG_FLATTEN) {
              db("injected " + icurr + " between " + curr + " and " + icurr.epilogueBBLE.fallThrough);
            }
            if (VM.VerifyAssertions) {
              VM._assert(icurr.epilogueBBLE.block == icurr.gc.cfg.lastInCodeOrder());
            }
            curr = icurr.epilogueBBLE;
            cop = curr.block;
          } else {
            if (DBG_FLATTEN) db("injected " + icurr + " after " + curr);
            curr = icurr;
            cop = calleeExit;
          }
        }
        next = curr.fallThrough;
        if (DBG_FLATTEN && next == null) {
          db(curr + " has no fallthrough case, getting next block");
        }
        if (next != null) {
          if (DBG_CFG || DBG_SELECTED) {
            db("Added CFG edge from " + curr.block + " to " + next.block);
          }
          if (next.isInCodeOrder()) {
            if (DBG_FLATTEN) {
              db("fallthrough " + next + " is already flattened, adding goto");
            }
            curr.block.appendInstruction(next.block.makeGOTO());
            // set next to null to indicate no "real" fall through
            next = null;
          }
        }
        if (next == null) {
          // Can't process fallthroughblock, so get next BBLE from enumeration
          while (true) {
            if (!e.hasMoreElements()) {
              // all done.
              if (DBG_FLATTEN) db("no more blocks! all done");
              break top;
            }
            next = e.next();
            if (DBG_FLATTEN) db("looking at " + next);
            if (!next.isGenerated()) {
              if (DBG_FLATTEN) db("block " + next + " was not generated");
              continue;
            }
            if (!next.isInCodeOrder()) {
              break;
            }
          }
          if (DBG_FLATTEN) db("found unflattened block: " + next);
        }
        curr = next;
      }
      // If the epilogue was unreachable, remove it from the code order and cfg
      // and set gc.epilogue to null.
      boolean removedSomethingFromCodeOrdering = inlinedSomething;
      if (gc.epilogue.hasZeroIn()) {
        if (DBG_FLATTEN || DBG_CFG) {
          db("Deleting unreachable epilogue " + gc.epilogue);
        }
        gc.cfg.removeFromCodeOrder(gc.epilogue);
        removedSomethingFromCodeOrdering = true;

        // remove the node from the graph AND adjust its edge info
        gc.epilogue.remove();
        gc.epilogue.deleteIn();
        gc.epilogue.deleteOut();
        if (VM.VerifyAssertions) VM._assert(gc.epilogue.hasZeroOut());
        gc.epilogue = null;
      }
      // if gc has an unlockAndRethrow block that was not used, then remove it
      if (gc.unlockAndRethrow != null && gc.unlockAndRethrow.hasZeroIn()) {
        gc.cfg.removeFromCFGAndCodeOrder(gc.unlockAndRethrow);
        removedSomethingFromCodeOrdering = true;
        gc.enclosingHandlers.remove(gc.unlockAndRethrow);
      }
      // if we removed a basic block then we should compact the node numbering
      if (removedSomethingFromCodeOrdering) {
        gc.cfg.compactNodeNumbering();
      }

      if (DBG_FLATTEN) {
        db("Current Code order for " + gc.method + "\n");
        for (BasicBlock bb = gc.prologue; bb != null; bb = (BasicBlock) bb.getNext()) {
          bb.printExtended();
        }
      }
      if (DBG_FLATTEN) {
        db("Final CFG for " + gc.method + "\n");
        gc.cfg.printDepthFirst();
      }
    }

    //////////////////////////////////////////
    // Gory implementation details of BBSet //
    //////////////////////////////////////////

    /**
     * Print a debug string to the sysWrite stream.
     * @param val string to print
     */
    private void db(String val) {
      VM.sysWrite("IRGEN " + bcodes.getDeclaringClass() + "." + gc.method.getName() + ":" + val + "\n");
    }

    /**
     * Initialize the global exception handler arrays for the method.<p>
     */
    private void parseExceptionTables() {
      ExceptionHandlerMap eMap = gc.method.getExceptionHandlerMap();
      if (DBG_EX) db("\texception handlers for " + gc.method + ": " + eMap);
      if (eMap == null) return;  // method has no exception handling ranges.
      startPCs = eMap.getStartPC();
      endPCs = eMap.getEndPC();
      handlerPCs = eMap.getHandlerPC();
      int numExceptionHandlers = startPCs.length;
      exceptionTypes = new TypeOperand[numExceptionHandlers];
      for (int i = 0; i < numExceptionHandlers; i++) {
        exceptionTypes[i] = new TypeOperand(eMap.getExceptionType(i));
        if (DBG_EX) db("\t\t[" + startPCs[i] + "," + endPCs[i] + "] " + eMap.getExceptionType(i));
      }
    }

    /**
     * Initialize bble's handlers array based on startPCs/endPCs.
     * In the process, new HandlerBlockLE's may be created
     * (by the call to getOrCreateBlock). <p>
     * PRECONDITION: bble.low and bble.max have already been correctly
     * set to reflect the invariant that a basic block is in exactly one
     * "handler range."<p>
     * Also initializes bble.block.exceptionHandlers.
     */
    private void initializeExceptionHandlers(BasicBlockLE bble, Operand[] simLocals) {
      if (startPCs != null) {
        HashSet<TypeReference> caughtTypes = new HashSet<TypeReference>();
        for (int i = 0; i < startPCs.length; i++) {
          TypeReference caughtType = exceptionTypes[i].getTypeRef();
          if (bble.low >= startPCs[i] && bble.max <= endPCs[i] && !caughtTypes.contains(caughtType)) {
            // bble's basic block is contained within this handler's range.
            HandlerBlockLE eh = (HandlerBlockLE) getOrCreateBlock(handlerPCs[i], bble, null, simLocals);
            if (DBG_EX) db("Adding handler " + eh + " to " + bble);
            caughtTypes.add(caughtType);
            bble.addHandler(eh);
          }
        }
      }
      if (bble.handlers != null) {
        ExceptionHandlerBasicBlock[] ehbbs = new ExceptionHandlerBasicBlock[bble.handlers.length];
        for (int i = 0; i < bble.handlers.length; i++) {
          ehbbs[i] = bble.handlers[i].entryBlock;
        }
        bble.block.exceptionHandlers = new ExceptionHandlerBasicBlockBag(ehbbs, gc.enclosingHandlers);
      } else {
        bble.block.exceptionHandlers = gc.enclosingHandlers;
      }
    }

    /**
     * Given a starting bytecode index, find the greatest bcIndex that
     * is still has the same inscope exception handlers.
     * @param bcIndex the start bytecode index
     */
    private int exceptionEndRange(int bcIndex) {
      int max = bcodes.length();
      if (startPCs != null) {
        for (int spc : startPCs) {
          if (bcIndex < spc && max > spc) {
            max = spc;
          }
        }
        for (int epc : endPCs) {
          if (bcIndex < epc && max > epc) {
            max = epc;
          }
        }
      }
      return max;
    }

    /**
     * We specialize basic blocks with respect to the return addresses
     * they have on their expression stack and/or in their local variables
     * on entry to the block. This has the effect of inlining the
     * subroutine body at all of the JSR sites that invoke it.
     * This is the key routine: it determines whether or not the
     * argument simState (stack and locals) contains compatible
     * return addresses as the candidate BasicBlockLE.
     * <p>
     * The main motivation for inlining away all JSR's is that it eliminates
     * the "JSR problem" for type accurate GC.  It is also simpler to
     * implement and arguably results in more efficient generated code
     * (assuming that we don't get horrific code bloat).
     * To deal with the code bloat, we detect excessive code duplication and
     * stop IR generation (bail out to the baseline compiler).
     *
     * @param simStack  The expression stack to match
     * @param simLocals The local variables to match
     * @param candBBLE  The candidate BaseicBlockLE
     */
    private boolean matchingJSRcontext(OperandStack simStack, Operand[] simLocals, BasicBlockLE candBBLE) {
      if (DBG_INLINE_JSR) {
        db("Matching JSR context of argument stack/locals against " + candBBLE);
      }

      int numRA = 0;
      if (simStack != null && candBBLE.isStackKnown()) {
        for (int i = simStack.getSize() - 1; i >= 0; i--) {
          Operand op = simStack.getFromTop(i);
          if (op instanceof ReturnAddressOperand) {
            if (numRA++ > MAX_RETURN_ADDRESSES) {
              throw new OperationNotImplementedException("Too many subroutines");
            }
            if (DBG_INLINE_JSR) db("simStack operand " + i + " is " + op);
            Operand cop = candBBLE.stackState.getFromTop(i);
            if (!Operand.conservativelyApproximates(cop, op)) {
              if (DBG_INLINE_JSR) db("Not Matching: " + cop + " and " + op);
              return false;
            } else {
              if (DBG_INLINE_JSR) db("operand " + cop + " is compatible with " + op);
            }
          }
        }
      }

      if (simLocals != null && candBBLE.isLocalKnown()) {
        for (int i = 0; i < simLocals.length; i++) {
          Operand op = simLocals[i];
          if (op instanceof ReturnAddressOperand) {
            if (numRA++ > MAX_RETURN_ADDRESSES) {
              throw new OperationNotImplementedException("Too many subroutines");
            }
            if (DBG_INLINE_JSR) db("simLocal " + i + " is " + op);
            Operand cop = candBBLE.localState[i];
            if (!Operand.conservativelyApproximates(cop, op)) {
              if (DBG_INLINE_JSR) db("Not Matching: " + cop + " and " + op);
              return false;
            } else {
              if (DBG_INLINE_JSR) db("operand " + cop + " is compatible with " + op);
            }
          }
        }
      }

      if (DBG_INLINE_JSR) db("Found " + candBBLE + " to be compatible");
      return true;
    }

    /**
     * Get or create a block at the specified target.
     * If simStack is non-null, rectifies stack state with target stack state.
     * If simLocals is non-null, rectifies local state with target local state.
     * Any instructions needed to rectify stack/local state are appended to
     * from.
     * As blocks are created, they are added to the red/black tree below x.
     *
     * @param x starting node for search.
     * @param shouldCreate should we create the block if we run off the tree?
     * @param target target index
     * @param from the block from which control is being transfered
     *                  and to which rectification instructions are added.
     * @param simStack stack state to rectify, or {@code null}
     * @param simLocals local state to rectify, or {@code null}
     */
    private BasicBlockLE getOrCreateBlock(BasicBlockLE x, boolean shouldCreate, int target, BasicBlockLE from,
                                          OperandStack simStack, Operand[] simLocals) {
      if (target < x.low) {
        if (x.left == null) {
          return condCreateAndInit(x, shouldCreate, target, from, simStack, simLocals, true);
        } else {
          if (DBG_BBSET) db("following left branch from " + x + " to " + x.left);
          return getOrCreateBlock(x.left, shouldCreate, target, from, simStack, simLocals);
        }
      } else if (target > x.low) {
        if ((x.low < target) && (target <= x.high)) {
          // the target points to the middle of x; mark x for regen
          if (DBG_BBSET) db("target points to middle of " + x);
          markBlockForRegeneration(x);
          x.high = x.low;
          x.block.deleteOut();
          if (DBG_CFG || DBG_SELECTED) db("Deleted all out edges of " + x.block);
        }
        if (x.right == null) {
          return condCreateAndInit(x, shouldCreate, target, from, simStack, simLocals, false);
        } else {
          if (DBG_BBSET) db("following right branch from " + x + " to " + x.right);
          return getOrCreateBlock(x.right, shouldCreate, target, from, simStack, simLocals);
        }
      } else {
        // found a basic block at the target bytecode index.
        if (noJSR || matchingJSRcontext(simStack, simLocals, x)) {
          if (DBG_BBSET) db("found block " + x + " (" + x.block + ")");
          if (simStack != null) rectifyStacks(from.block, simStack, x);
          if (simLocals != null) rectifyLocals(simLocals, x);
          return x;
        }
        if (DBG_BBSET) db("found block " + x + ", but JSR context didn't match");
        if (x.left == null) {
          if (x.right == null) {
            return condCreateAndInit(x, shouldCreate, target, from, simStack, simLocals, true);
          } else {
            if (DBG_BBSET) {
              db(x + " has only right child, continuing down that branch");
            }
            return getOrCreateBlock(x.right, shouldCreate, target, from, simStack, simLocals);
          }
        } else {
          if (x.right == null) {
            if (DBG_BBSET) {
              db(x + " has only left child, continuing down that branch");
            }
            return getOrCreateBlock(x.left, shouldCreate, target, from, simStack, simLocals);
          } else {
            if (DBG_BBSET) {
              db(x + " has two children, searching left branch first");
            }
            BasicBlockLE bble = getOrCreateBlock(x.left, false, target, from, simStack, simLocals);
            if (bble != null) {
              return bble;
            } else {
              if (DBG_BBSET) {
                db("didn't find " + target + " on left branch, continuing down right branch");
              }
              return getOrCreateBlock(x.right, shouldCreate, target, from, simStack, simLocals);
            }
          }
        }
      }
    }

    /**
     * Conditionally create a block at the specified target as a child of x.
     * If simStack is non-{@code null}, rectifies stack state with target stack state.
     * If simLocals is non-{@code null}, rectifies local state with target local state.
     * Any instructions needed to rectify stack/local state are appended to
     * from.
     *
     * @param x starting node for search.
     * @param shouldCreate should we create the block if we run off the tree?
     * @param target target index
     * @param from the block from which control is being transfered
     *                  and to which rectification instructions are added.
     * @param simStack stack state to rectify, or {@code null}
     * @param simLocals local state to rectify, or {@code null}
     * @param left are we creating a left child of parent?
     * @return the newly created block, or {@code null} if !shouldCreate
     */
    private BasicBlockLE condCreateAndInit(BasicBlockLE x, boolean shouldCreate, int target, BasicBlockLE from,
                                           OperandStack simStack, Operand[] simLocals, boolean left) {
      BasicBlockLE bble = null;
      if (shouldCreate) {
        bble = _createBBLE(target, simLocals, x, left);
        if (simStack != null) {
          rectifyStacks(from.block, simStack, bble);
        }
        if (simLocals != null) {
          bble.copyIntoLocalState(simLocals);
        }
      }
      return bble;
    }

    /**
     * Allocate a new BBLE at the given bcIndex.
     * If bcIndex is the start of an handler block,
     * then a HandlerBlockLE is created.
     * After the BBLE is created, its handlers data structure is initialized
     * (which may cause other blocks to be created).
     * @param bcIndex the bytecode index at which the block should be created.
     * @param simLocals the localState to pass (via initializeExceptionHandler)to
     *                  to getOrCreateBlock if we need to create BBLEs for
     *                  exception handlers.  This is only actually used if
     *                  !noJSR.  We don't need the expression stack, since
     *                  the only thing on the expression stack on entry to
     *                  a handler block is the exception object (and thus
     *                  we can skip scanning the expression stack for
     *                  return addresses when creating a handler block).
     * @param parent parent in Red/Black tree
     * @param left are we creating a left child of parent?
     */
    private BasicBlockLE _createBBLE(int bcIndex, Operand[] simLocals, BasicBlockLE parent, boolean left) {
      BasicBlockLE newBBLE = null;
      if (handlerPCs != null) {
        for (int i = 0; i < handlerPCs.length; i++) {
          if (handlerPCs[i] == bcIndex) {
            if (newBBLE == null) {
              newBBLE =
                  new HandlerBlockLE(bcIndex,
                                     gc.inlineSequence,
                                     exceptionTypes[i],
                                     gc.temps,
                                     gc.method.getOperandWords(),
                                     gc.cfg);
              ((HandlerBlockLE) newBBLE).entryBlock.firstRealInstruction().
                  position = gc.inlineSequence;
            } else {
              ((HandlerBlockLE) newBBLE).addCaughtException(exceptionTypes[i]);
            }
          }
        }
      }
      if (newBBLE == null) {
        newBBLE = new BasicBlockLE(bcIndex, gc.inlineSequence, gc.cfg);
      }

      // Set newBBLE.max to encode exception ranges
      newBBLE.max = exceptionEndRange(bcIndex);

      if (DBG_BBSET) db("Created " + newBBLE);

      // Now, insert newBBLE into our backing Red/Black tree before we call
      // initializeExceptionHandlers.
      // We must do it in this order because initExHand may in turn call
      // _createBBLE to create new handler blocks, and our tree must contain
      // newBBLE before we can correctly insert another block.
      treeInsert(parent, newBBLE, left);

      initializeExceptionHandlers(newBBLE, simLocals);
      return newBBLE;
    }

    /**
     * Returns the basic block which has the next-higher bytecode index.
     * Returns {@code null} if x is the highest block.
     * @param x basic block at which to start the search for a higher block
     * @param value the contents of x.low (makes tail call elim work better
     *              if we avoid the obvious 1 argument wrapper function)
     */
    private BasicBlockLE getSuccessor(BasicBlockLE x, int value) {
      if (x.right != null) return minimumBB(x.right, value);
      BasicBlockLE y = x.parent;
      while ((y != null) && (x == y.right)) {
        x = y;
        y = x.parent;
      }
      // at this point either x is the root, or x is the left child of y
      if ((y == null) || (y.low != value)) return y;
      return getSuccessor(y, value);
    }

    private BasicBlockLE minimumBB(BasicBlockLE x, int value) {
      if (x.left != null) return minimumBB(x.left, value);
      if (value == x.low) return getSuccessor(x, value);
      return x;
    }

    /**
     * Insert <code>newBBLE</code> as a child of parent in our Red/Black tree.
     * @param parent the parent node
     * @param newBBLE  the new child node
     * @param left   is the child the left or right child of parent?
     */
    private void treeInsert(BasicBlockLE parent, BasicBlockLE newBBLE, boolean left) {
      if (parent == null) {
        if (VM.VerifyAssertions) VM._assert(root == null);
        root = newBBLE;
        root.setBlack();
        if (DBG_BBSET) db("inserted " + newBBLE + " as root of tree");
      } else {
        if (left) {
          parent.left = newBBLE;
        } else {
          parent.right = newBBLE;
        }
        newBBLE.parent = parent;
        if (DBG_BBSET) {
          db("inserted new block " + newBBLE + " as " + (left ? "left" : "right") + " child of " + parent);
        }
        fixupBBSet(newBBLE);
      }
    }

    /**
     * Performs tree fixup (restore Red/Black invariants) after adding a
     * new node to the tree.
     * @param x node that was added.
     */
    private void fixupBBSet(BasicBlockLE x) {
      if (DBG_BBSET) db("fixing up tree after inserting " + x);
      x.setRed();
      while (x != root) {
        BasicBlockLE xp = x.parent;
        if (xp.isBlack()) {
          break;
        }
        if (DBG_BBSET) db(x + " and its parent " + xp + " are both red");
        BasicBlockLE xpp = xp.parent;
        if (DBG_BBSET) db(xp + "'s parent is " + xpp);
        if (xp == xpp.left) {
          BasicBlockLE y = xpp.right;
          if ((y != null) && y.isRed()) {
            xp.setBlack();
            y.setBlack();
            xpp.setRed();
            x = xpp;
          } else {
            if (x == xp.right) {
              x = xp;
              leftRotateBBSet(xp);
              xp = x.parent;
              xpp = xp.parent;
            }
            xp.setBlack();
            xpp.setRed();
            rightRotateBBSet(xpp);
          }
        } else {
          BasicBlockLE y = xpp.left;
          if ((y != null) && y.isRed()) {
            xp.setBlack();
            y.setBlack();
            xpp.setRed();
            x = xpp;
          } else {
            if (x == xp.left) {
              x = xp;
              rightRotateBBSet(xp);
              xp = x.parent;
              xpp = xp.parent;
            }
            xp.setBlack();
            xpp.setRed();
            leftRotateBBSet(xpp);
          }
        }
      }
      root.setBlack();
      // verifyTree();
    }

    private void leftRotateBBSet(BasicBlockLE x) {
      if (DBG_BBSET) db("performing left tree rotation");
      BasicBlockLE y = x.right;
      BasicBlockLE yl = y.left;
      x.right = yl;
      if (yl != null) {
        yl.parent = x;
      }
      BasicBlockLE xp = x.parent;
      y.parent = xp;
      if (xp == null) {
        root = y;
      } else if (x == xp.left) {
        xp.left = y;
      } else {
        xp.right = y;
      }
      y.left = x;
      x.parent = y;
    }

    private void rightRotateBBSet(BasicBlockLE x) {
      if (DBG_BBSET) db("performing right tree rotation");
      BasicBlockLE y = x.left;
      BasicBlockLE yr = y.right;
      x.left = yr;
      if (yr != null) {
        yr.parent = x;
      }
      BasicBlockLE xp = x.parent;
      y.parent = xp;
      if (xp == null) {
        root = y;
      } else if (x == xp.right) {
        xp.right = y;
      } else {
        xp.left = y;
      }
      y.right = x;
      x.parent = y;
    }

    @SuppressWarnings("unused")
    // here for debugging
    private void verifyTree() {
      if (VM.VerifyAssertions) {
        VM._assert(root.isBlack());
        verifyTree(root, -1, bcodes.length());
        countBlack(root);
      }
    }

    private void verifyTree(BasicBlockLE node, int min, int max) {
      if (VM.VerifyAssertions) {
        VM._assert(node.low >= min);
        VM._assert(node.low <= max);
        if (node.left != null) {
          VM._assert(node.isBlack() || node.left.isBlack());
          VM._assert(node.left.parent == node);
          verifyTree(node.left, min, node.low);
        }
        if (node.right != null) {
          VM._assert(node.isBlack() || node.right.isBlack());
          VM._assert(node.right.parent == node);
          verifyTree(node.right, node.low, max);
        }
      }
    }

    private int countBlack(BasicBlockLE node) {
      if (node == null) return 1;
      int left = countBlack(node.left);
      int right = countBlack(node.right);
      if (VM.VerifyAssertions) VM._assert(left == right);
      if (node.isBlack()) {
        left++;
      }
      return left;
    }

    private static final class TreeEnumerator implements Enumeration<BasicBlockLE> {
      BasicBlockLE node;

      static TreeEnumerator enumFromRoot(BasicBlockLE root) {
        if (root.left != null) {
          do {
            root = root.left;
          } while (root.left != null);
        }
        return new TreeEnumerator(root);
      }

      static TreeEnumerator enumFromNode(BasicBlockLE node) {
        return new TreeEnumerator(node);
      }

      private TreeEnumerator(BasicBlockLE node) {
        this.node = node;
      }

      @Override
      public boolean hasMoreElements() {
        return (node != null);
      }

      public BasicBlockLE next() {
        BasicBlockLE retVal = node;
        if (retVal == null) {
          throw new NoSuchElementException();
        }
        if (retVal.right != null) {
          node = retVal.right;
          while (node.left != null) {
            node = node.left;
          }
        } else {
          BasicBlockLE x = retVal;
          node = x.parent;
          while ((node != null) && (node.right == x)) {
            x = node;
            node = x.parent;
          }
        }
        return retVal;
      }

      @Override
      public BasicBlockLE nextElement() {
        return next();
      }
    }
  }

  /**
   * Simulated Operand Stack
   */
  private static final class OperandStack {

    private final Operand[] stack;
    private int top;

    OperandStack(int size) {
      stack = new Operand[size];
      top = 0;
    }

    OperandStack copy() {
      OperandStack newss = new OperandStack(stack.length);
      newss.top = top;
      for (int i = 0; i < top; i++) {
        // deep copy of stack
        newss.stack[i] = stack[i].copy();
      }
      return newss;
    }

    void clear() {
      top = 0;
    }

    void push(Operand val) {
//      if (VM.VerifyAssertions) VM._assert(val.instruction == null);
      stack[top++] = val;
    }

    Operand pop() {
      return stack[--top];
    }

    Operand peek(int depth) {
      return stack[top - depth - 1];
    }

    Operand peekAt(int pos) {
      return stack[pos];
    }

    void pop2() {
      pop();
      pop();
    }

    void swap() {
      Operand v1 = pop();
      Operand v2 = pop();
      push(v1);
      push(v2);
    }

    boolean isEmpty() {
      return (top == 0);
    }

    int getSize() {
      return top;
    }

    int getCapacity() {
      return stack.length;
    }

    Operand getFromTop(int n) {
      return stack[top - n - 1];
    }

    void replaceFromTop(int n, Operand op) {
      if (VM.VerifyAssertions) VM._assert(op.instruction == null);
      stack[top - n - 1] = op;
    }
  }

  /**
   * This class is used as a 'wrapper' to a basic block to hold
   * information that is necessary only for IR generation.
   */
  private static class BasicBlockLE {
    // Used by BC2IR to maintain red/black tree of BBLE's during generation
    BasicBlockLE parent, left, right;

    /** Start bytecode of this BBLE */
    final int low;

    /** Current end bytecode of this BBLE */
    int high;

    /** Maximum possible bytecode of this BBLE (wrt exception ranges) */
    int max;

    /** Basic block that this BBLE refers to. */
    BasicBlock block;

    /** State of the stack at the start of this basic block. */
    OperandStack stackState;

    /** State of the local variables at the start of this basic block. */
    Operand[] localState;

    /**
     * The desired fallthrough (next in code order) BBLE (may be {@code null}).
     * NOTE: we may not always end up actually falling through
     * (see BBSet.finalPass).
     */
    BasicBlockLE fallThrough;

    /**
     * The exception handler BBLE's for this block (null if none)
     */
    HandlerBlockLE[] handlers;

    /**
     * Encoding of random boolean state
     */
    private byte flags;
    private static final byte STACK_KNOWN = 0x01;
    private static final byte LOCAL_KNOWN = 0x02;
    private static final byte SELF_REGEN = 0x04;
    private static final byte GENERATED = 0x08;
    private static final byte COLOR = 0x10;           //(Red = 0, Black = 1)
    private static final byte IN_CODE_ORDER = 0x20;

    final void setStackKnown() { flags |= STACK_KNOWN; }

    final void clearStackKnown() { flags &= ~STACK_KNOWN; }

    final boolean isStackKnown() { return (flags & STACK_KNOWN) != 0; }

    final void setLocalKnown() { flags |= LOCAL_KNOWN; }

    final void clearLocalKnown() { flags &= ~LOCAL_KNOWN; }

    final boolean isLocalKnown() { return (flags & LOCAL_KNOWN) != 0; }

    final void setSelfRegen() { flags |= SELF_REGEN; }

    final void clearSelfRegen() { flags &= ~SELF_REGEN; }

    final boolean isSelfRegen() { return (flags & SELF_REGEN) != 0; }

    final void setGenerated() { flags |= GENERATED; }

    final void clearGenerated() { flags &= ~GENERATED; }

    final boolean isGenerated() { return (flags & GENERATED) != 0; }

    final void setBlack() { flags |= COLOR; }

    final boolean isBlack() { return (flags & COLOR) != 0; }

    final void setRed() { flags &= ~COLOR; }

    final boolean isRed() { return (flags & COLOR) == 0; }

    final void setInCodeOrder() { flags |= IN_CODE_ORDER; }

    final void clearInCodeOrder() { flags &= ~IN_CODE_ORDER; }

    final boolean isInCodeOrder() { return (flags & IN_CODE_ORDER) != 0; }

    /**
     * Is the BBLE ready to generate?
     */
    final boolean isReadyToGenerate() {
      // (isStackKnown() && isLocalKnown && !isGenerated)
      byte READY_MASK = STACK_KNOWN | LOCAL_KNOWN | GENERATED;
      byte READY_VAL = STACK_KNOWN | LOCAL_KNOWN;
      return (flags & READY_MASK) == READY_VAL;
    }

    /**
     * Save a shallow copy of the given local variable state into this.
     * @param _localState local variable state to save
     */
    final void copyIntoLocalState(Operand[] _localState) {
      localState = new Operand[_localState.length];
      System.arraycopy(_localState, 0, localState, 0, _localState.length);
      setLocalKnown();
    }

    /**
     * Return a shallow copy of my local state.
     */
    final Operand[] copyLocalState() {
      Operand[] ls = new Operand[localState.length];
      System.arraycopy(localState, 0, ls, 0, localState.length);
      return ls;
    }

    /**
     * Add an exception handler BBLE to the handlers array.
     * NOTE: this isn't incredibly efficient, but empirically the expected
     * number of handlers per basic block is 0, with an observed
     * maximum across 10,000+ methods of 3.
     * Until this changes, we just don't care.
     */
    final void addHandler(HandlerBlockLE handler) {
      if (handlers == null) {
        handlers = new HandlerBlockLE[1];
        handlers[0] = handler;
      } else {
        for (HandlerBlockLE handler1 : handlers) {
          if (handler1 == handler) {
            return;             //already there (was in emap more than once)
          }
        }
        int n = handlers.length;
        HandlerBlockLE[] tmp = new HandlerBlockLE[n + 1];
        for (int i = 0; i < n; i++) {
          tmp[i] = handlers[i];
        }
        tmp[n] = handler;
        handlers = tmp;
      }
    }

    /**
     * Create a new BBLE (and basic block) for the specified bytecode index.
     *
     * @param loc bytecode index
     * @param position the inline sequence
     * @param cfg ControlFlowGraph into which the block
     *            will eventually be inserted
     */
    BasicBlockLE(int loc, InlineSequence position, ControlFlowGraph cfg) {
      block = new BasicBlock(loc, position, cfg);
      low = loc;
      high = loc;
    }

    // Only for use by subclasses to avoid above constructor.
    protected BasicBlockLE(int loc) { low = loc;  }

    /**
     * Returns a string representation of this BBLE.
     */
    @Override
    public String toString() {
      if (isGenerated()) {
        return "(" + low + "," + high + "," + max + ")";
      }
      if (isReadyToGenerate()) {
        return "{" + low + "," + max + "}";
      }
      return "[" + low + "," + max + "]";
    }

    /**
     * Returns a string representation of state that determines if the BBLE
     * is ready to be generated */
    public String genState() {
      return "(sk=" + isStackKnown() + ", lk=" + isLocalKnown() + ", gen=" + isGenerated() + ")";
    }
  }

  /**
   * Extend BasicBlockLE for handler blocks
   */
  private static final class HandlerBlockLE extends BasicBlockLE {
    /**
     * The RegisterOperand that code should use to access
     * the caught exception object
     */
    final RegisterOperand exceptionObject;

    /**
     * The synthetic entry basic block for this handler.
     * It contains the instruction sequence to get the caught exception object
     * into a "normal" register operand (exceptionObject);
     */
    final ExceptionHandlerBasicBlock entryBlock;

    /**
     * Create a new exception handler BBLE (and exception handler basic block)
     * for the specified bytecode index and exception type.
     *
     * @param loc bytecode index
     * @param position inline sequence
     * @param eType   exception type
     * @param temps the register pool to allocate exceptionObject from
     * @param exprStackSize max size of expression stack
     * @param cfg ControlFlowGraph into which the block
     *            will eventually be inserted
     */
    HandlerBlockLE(int loc, InlineSequence position, TypeOperand eType, RegisterPool temps,
                   int exprStackSize, ControlFlowGraph cfg) {
      super(loc);
      entryBlock = new ExceptionHandlerBasicBlock(SYNTH_CATCH_BCI, position, eType, cfg);
      block = new BasicBlock(loc, position, cfg);
      // NOTE: We intentionally use throwable rather than eType to avoid
      // having the complexity of having to regenerate the handler when a
      // new type of caught exception is added. Since we shouldn't care about
      // the performance of code in exception handling blocks, this
      // should be the right tradeoff.
      exceptionObject = temps.makeTemp(TypeReference.JavaLangThrowable);
      setGuard(exceptionObject, new TrueGuardOperand());    // know not null
      high = loc;
      // Set up expression stack on entry to have the caught exception operand.
      stackState = new OperandStack(exprStackSize);
      stackState.push(exceptionObject);
      setStackKnown();
      // entry block contains instructions to transfer the caught
      // exception object to exceptionObject.
      Instruction s = Nullary.create(GET_CAUGHT_EXCEPTION, exceptionObject.copyD2D());
      entryBlock.appendInstruction(s);
      s.bcIndex = SYNTH_CATCH_BCI;
      entryBlock.insertOut(block);
    }

    void addCaughtException(TypeOperand et) {
      entryBlock.addCaughtException(et);
    }

    byte mayCatchException(TypeReference et) {
      return entryBlock.mayCatchException(et);
    }

    byte mustCatchException(TypeReference et) {
      return entryBlock.mustCatchException(et);
    }
  }

  /**
   * Extend BasicBlockLE to support inlining during IR generation.
   */
  private static final class InliningBlockLE extends BasicBlockLE {
    final GenerationContext gc;
    final BasicBlockLE epilogueBBLE;

    InliningBlockLE(GenerationContext c, BasicBlockLE bble) {
      super(0);
      gc = c;
      epilogueBBLE = bble;
    }

    @Override
    public String toString() {
      return "(Inline method " + gc.method + ")";
    }

    /**
     * delete the outgoing CFG edges from all
     * basic blocks in the callee (gc.cfg).
     * This is used when the BBLE preceeding the inlined
     * method block needs to be regenerated, thus forcing
     * us to discard the callee IR (which may contains
     * control flow links to the caller IR because of exception handlers).
     * <p>
     * TODO: One might be able to do this more efficiently by
     * keeping track of the exposed edges in the generation context
     * and commiting them once the top level generation
     * completes.  Probably not worth it, since we expect this
     * method to be called very infrequently.
     */
    void deleteAllOutEdges() {
      for (BasicBlock bb = gc.cfg.firstInCodeOrder(); bb != null; bb = bb.nextBasicBlockInCodeOrder()) {
        bb.deleteOut();
      }
    }
  }

  /**
   * Dummy stack slot
   * @see BC2IR#DUMMY
   */
  private static final class DummyStackSlot extends Operand {
    @Override
    public Operand copy() { return this; }

    @Override
    public boolean similar(Operand op) { return (op instanceof DummyStackSlot); }

    @Override
    public String toString() { return "<DUMMY>"; }
  }

  /**
   * ReturnAddress operand. Used to represent the address pushed on
   * the expression stack by a JSR instruction.
   */
  public static final class ReturnAddressOperand extends Operand {
    final int retIndex;

    ReturnAddressOperand(int ri) { retIndex = ri; }

    @Override
    public Operand copy() { return this; }

    @Override
    public boolean similar(Operand op) {
      return (op instanceof ReturnAddressOperand) && (retIndex == ((ReturnAddressOperand) op).retIndex);
    }

    @Override
    public String toString() {
      return "<return address " + retIndex + ">";
    }
  }
}
