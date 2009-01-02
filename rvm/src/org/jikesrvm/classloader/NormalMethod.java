/*
 *  This file is part of the Jikes RVM project (http://jikesrvm.org).
 *
 *  This file is licensed to You under the Common Public License (CPL);
 *  You may not use this file except in compliance with the License. You
 *  may obtain a copy of the License at
 *
 *      http://www.opensource.org/licenses/cpl1.0.php
 *
 *  See the COPYRIGHT.txt file distributed with this work for information
 *  regarding copyright ownership.
 */
package org.jikesrvm.classloader;

import org.jikesrvm.VM;
import org.jikesrvm.compilers.common.BootImageCompiler;
import org.jikesrvm.compilers.common.CompiledMethod;
import org.jikesrvm.compilers.common.RuntimeCompiler;
import org.jikesrvm.runtime.DynamicLink;
import org.jikesrvm.util.HashMapRVM;
import org.vmmagic.pragma.Uninterruptible;

/**
 * A method of a java class that has bytecodes.
 */
public final class NormalMethod extends RVMMethod implements BytecodeConstants {

  /* As we read the bytecodes for the method, we compute
   * a simple summary of some interesting properties of the method.
   * Because we do this for every method, we require the summarization to
   * be fast and the computed summary to be very space efficient.
   *
   * The following constants encode the estimated relative cost in
   * machine instructions when a particular class of bytecode is compiled
   * by the optimizing compiler. The estimates approximate the typical
   * optimization the compiler is able to perform.
   * This information is used to estimate how big a method will be when
   * it is inlined.
   */
  public static final int SIMPLE_OPERATION_COST = 1;
  public static final int LONG_OPERATION_COST = 2;
  public static final int ARRAY_LOAD_COST = 2;
  public static final int ARRAY_STORE_COST = 2;
  public static final int JSR_COST = 5;
  public static final int CALL_COST = 6;
  // Bias to inlining methods with magic
  // most magics are quite cheap (0-1 instructions)
  public static final int MAGIC_COST = 0;
  // News are actually more expensive than calls
  // but bias to inline methods that allocate
  // objects becuase we expect better downstream optimization of
  // the caller due to class analysis
  // and propagation of nonNullness
  public static final int ALLOCATION_COST = 4;
  // Approximations, assuming some CSE/PRE of object model computations
  public static final int CLASS_CHECK_COST = 2 * SIMPLE_OPERATION_COST;
  public static final int STORE_CHECK_COST = 4 * SIMPLE_OPERATION_COST;
  // Just a call.
  public static final int THROW_COST = CALL_COST;
  // Really a bunch of operations plus a call, but undercharge because
  // we don't have worry about this causing an exponential growth of call chain
  // and we probably want to inline synchronization
  // (to get a chance to optimize it).
  public static final int SYNCH_COST = 4 * SIMPLE_OPERATION_COST;
  // The additional cost of a switch isn't that large, since if the
  // switch has more than a few cases the method will be too big to inline
  // anyways.
  public static final int SWITCH_COST = CALL_COST;

  // Definition of flag bits
  private static final char HAS_MAGIC = 0x8000;
  private static final char HAS_SYNCH = 0x4000;
  private static final char HAS_ALLOCATION = 0x2000;
  private static final char HAS_THROW = 0x1000;
  private static final char HAS_INVOKE = 0x0800;
  private static final char HAS_FIELD_READ = 0x0400;
  private static final char HAS_FIELD_WRITE = 0x0200;
  private static final char HAS_ARRAY_READ = 0x0100;
  private static final char HAS_ARRAY_WRITE = 0x0080;
  private static final char HAS_JSR = 0x0040;
  private static final char HAS_COND_BRANCH = 0x0020;
  private static final char HAS_SWITCH = 0x0010;
  private static final char HAS_BACK_BRANCH = 0x0008;
  private static final char IS_RS_METHOD = 0x0004;

  /**
   * storage for bytecode summary flags
   */
  private char summaryFlags;
  /**
   * storage for bytecode summary size
   */
  private char summarySize;

  /**
   * words needed for local variables (including parameters)
   */
  private final short localWords;

  /**
   * words needed for operand stack (high water mark)
   * TODO: OSR redesign;  add subclass of NormalMethod for OSR method
   *       and then make this field final in NormalMethod.
   */
  private short operandWords;

  /**
   * bytecodes for this method (null --> none)
   */
  private final byte[] bytecodes;

  /**
   * try/catch/finally blocks for this method (null --> none)
   */
  private final ExceptionHandlerMap exceptionHandlerMap;

  /**
   * pc to source-line info (null --> none)
   * Each entry contains both the line number (upper 16 bits)
   * and corresponding start PC (lower 16 bits).
   */
  private final int[] lineNumberMap;

  /**
   * the local variable table
   */
  private static final HashMapRVM<NormalMethod, LocalVariableTable> localVariableTables = new HashMapRVM<NormalMethod, LocalVariableTable>();

  // Extra fields for on-stack replacement
  /** Possible OSR bytecode array consisting of prologue and original bytecodes */
  private static final HashMapRVM<NormalMethod, byte[]> synthesizedBytecodes =
    new HashMapRVM<NormalMethod, byte[]>();
  /** Possible OSR record of osr prologue */
  private static final HashMapRVM<NormalMethod, byte[]> osrPrologues =
    new HashMapRVM<NormalMethod, byte[]>();
  /**
   * Possibly OSR prologue may change the maximum stack height, remember the
   * original stack height
   */
  private static final HashMapRVM<NormalMethod, Integer> savedOperandWords =
    new HashMapRVM<NormalMethod, Integer>();

  /**
   * Construct a normal Java bytecode method's information
   *
   * @param dc the TypeReference object of the class that declared this field
   * @param mr the canonical memberReference for this member.
   * @param mo modifiers associated with this member.
   * @param et exceptions thrown by this method.
   * @param lw the number of local words used by the bytecode of this method
   * @param ow the number of operand words used by the bytecode of this method
   * @param bc the bytecodes of this method
   * @param eMap the exception handler map for this method
   * @param lm the line number map for this method
   * @param lvt the local variable table for this method
   * @param constantPool the constantPool for this method
   * @param sig generic type of this method.
   * @param annotations array of runtime visible annotations
   * @param parameterAnnotations array of runtime visible paramter annotations
   * @param ad annotation default value for that appears in annotation classes
   */
  NormalMethod(TypeReference dc, MemberReference mr, short mo, TypeReference[] et, short lw, short ow,
                  byte[] bc, ExceptionHandlerMap eMap, int[] lm, LocalVariableTable lvt, int[] constantPool, Atom sig,
                  RVMAnnotation[] annotations, RVMAnnotation[][] parameterAnnotations, Object ad) {
    super(dc, mr, mo, et, sig, annotations, parameterAnnotations, ad);
    localWords = lw;
    operandWords = ow;
    bytecodes = bc;
    exceptionHandlerMap = eMap;
    lineNumberMap = lm;
    localVariableTables.put(this, lvt);
    computeSummary(constantPool);
  }

  /**
   * Generate the code for this method
   */
  protected CompiledMethod genCode() throws VerifyError {
    if (VM.writingBootImage) {
      return BootImageCompiler.compile(this);
    } else {
      return RuntimeCompiler.compile(this);
    }
  }

  /**
   * Space required by this method for its local variables, in words.
   * Note: local variables include parameters
   */
  @Uninterruptible
  public int getLocalWords() {
    return localWords;
  }

  /**
   * Space required by this method for its operand stack, in words.
   */
  @Uninterruptible
  public int getOperandWords() {
    return operandWords;
  }

  /**
   * Get a representation of the bytecodes in the code attribute of this method.
   * @return object representing the bytecodes
   */
  public BytecodeStream getBytecodes() {
    return new BytecodeStream(this, bytecodes);
  }

  /**
   * Fill in DynamicLink object for the invoke at the given bytecode index
   * @param dynamicLink the dynamicLink object to initialize
   * @param bcIndex the bcIndex of the invoke instruction
   */
  @Uninterruptible
  public void getDynamicLink(DynamicLink dynamicLink, int bcIndex) {
    if (VM.VerifyAssertions) VM._assert(bytecodes != null);
    if (VM.VerifyAssertions) VM._assert(bcIndex + 2 < bytecodes.length);
    int bytecode = bytecodes[bcIndex] & 0xFF;
    if (VM.VerifyAssertions) {
      VM._assert((BytecodeConstants.JBC_invokevirtual <= bytecode) &&
                 (bytecode <= BytecodeConstants.JBC_invokeinterface));
    }
    int constantPoolIndex = ((bytecodes[bcIndex + 1] & 0xFF) << BITS_IN_BYTE) | (bytecodes[bcIndex + 2] & 0xFF);
    dynamicLink.set(getDeclaringClass().getMethodRef(constantPoolIndex), bytecode);
  }

  /**
   * Size of bytecodes for this method
   */
  public int getBytecodeLength() {
    return bytecodes.length;
  }

  /**
   * Exceptions caught by this method.
   * @return info (null --> method doesn't catch any exceptions)
   */
  @Uninterruptible
  public ExceptionHandlerMap getExceptionHandlerMap() {
    return exceptionHandlerMap;
  }

  /**
   * Return the line number information for the argument bytecode index.
   * @return The line number, a positive integer.  Zero means unable to find.
   */
  @Uninterruptible
  public int getLineNumberForBCIndex(int bci) {
    if (lineNumberMap == null) return 0;
    int idx;
    for (idx = 0; idx < lineNumberMap.length; idx++) {
      int pc = lineNumberMap[idx] & 0xffff; // lower 16 bits are bcIndex
      if (bci < pc) {
        if (idx == 0) idx++; // add 1, so we can subtract 1 below.
        break;
      }
    }
    return lineNumberMap[--idx] >>> 16; // upper 16 bits are line number
  }

  // Extra methods for on-stack replacement
  // BaselineCompiler and BC2IR should check if a method is
  // for specialization by calling isForOsrSpecialization, the compiler
  // uses synthesized bytecodes (prologue + original bytecodes) for
  // OSRing method. Other interfaces of method are not changed, therefore,
  // dynamic linking and gc referring to bytecodes are safe.

  /**
   * Checks if the method is in state for OSR specialization now
   * @return true, if it is (with prologue)
   */
  public boolean isForOsrSpecialization() {
    synchronized(synthesizedBytecodes) {
      return synthesizedBytecodes.get(this) != null;
    }
  }

  /**
   * Sets method in state for OSR specialization, i.e, the subsequent calls
   * of {@link #getBytecodes} return the stream of specialized bytecodes.
   *
   * NB: between flag and action, it should not allow GC or threadSwitch happen.
   * @param prologue   The bytecode of prologue
   * @param newStackHeight  The prologue may change the default height of
   *                        stack
   */
  public void setForOsrSpecialization(byte[] prologue, short newStackHeight) {
    if (VM.VerifyAssertions) {
      synchronized (synthesizedBytecodes) {
        VM._assert(synthesizedBytecodes.get(this) == null);
      }
    }

    byte[] newBytecodes = new byte[prologue.length + bytecodes.length];
    System.arraycopy(prologue, 0, newBytecodes, 0, prologue.length);
    System.arraycopy(bytecodes, 0, newBytecodes, prologue.length, bytecodes.length);

    synchronized(osrPrologues) {
      osrPrologues.put(this, prologue);
    }
    synchronized(synthesizedBytecodes) {
      synthesizedBytecodes.put(this, newBytecodes);
    }
    synchronized(savedOperandWords) {
      savedOperandWords.put(this, Integer.valueOf(operandWords));
    }
    if (newStackHeight > operandWords) {
      this.operandWords = newStackHeight;
    }
  }

  /**
   * Restores the original state of the method.
   */
  public void finalizeOsrSpecialization() {
    if (VM.VerifyAssertions) {
      synchronized (synthesizedBytecodes) {
        VM._assert(synthesizedBytecodes.get(this) != null);
      }
    }
    synchronized(osrPrologues) {
      osrPrologues.remove(this);
    }
    synchronized(synthesizedBytecodes) {
      synthesizedBytecodes.remove(this);
    }
    synchronized(savedOperandWords) {
      this.operandWords = (short)(savedOperandWords.get(this).intValue());
      savedOperandWords.remove(this);
    }
  }

  /**
   * Returns the OSR prologue length for adjusting various tables and maps.
   * @return the length of prologue if the method is in state for OSR,
   *         0 otherwise.
   */
  public int getOsrPrologueLength() {
    if(isForOsrSpecialization()) {
      synchronized(osrPrologues) {
        return osrPrologues.get(this).length;
      }
    } else {
      return 0;
    }
  }

  /**
   * Returns a bytecode stream of osr prologue
   * @return osr prologue bytecode stream
   */
  public BytecodeStream getOsrPrologue() {
    if (VM.VerifyAssertions) {
      synchronized (synthesizedBytecodes) {
        VM._assert(synthesizedBytecodes.get(this) != null);
      }
    }
    byte[] osrPrologue;
    synchronized(osrPrologues) {
      osrPrologue = osrPrologues.get(this);
    }
    return new BytecodeStream(this, osrPrologue);
  }

  /**
   * Returns the synthesized bytecode stream with osr prologue
   * @return bytecode stream
   */
  public BytecodeStream getOsrSynthesizedBytecodes() {
    byte[] bytecodes;
    synchronized(synthesizedBytecodes) {
      bytecodes = synthesizedBytecodes.get(this);
      if (VM.VerifyAssertions) VM._assert(bytecodes != null);
    }
    return new BytecodeStream(this, bytecodes);
  }

  /*
  * Methods to access and compute method summary information
  */

  /**
   * @return An estimate of the expected size of the machine code instructions
   * that will be generated by the opt compiler if the method is inlined.
   */
  public int inlinedSizeEstimate() {
    return summarySize & 0xFFFF;
  }

  /**
   * @return true if the method contains a Magic.xxx or Address.yyy
   */
  public boolean hasMagic() {
    return (summaryFlags & HAS_MAGIC) != 0;
  }

  /**
   * @return true if the method contains a monitorenter/exit or is synchronized
   */
  public boolean hasSynch() {
    return (summaryFlags & HAS_SYNCH) != 0;
  }

  /**
   * @return true if the method contains an allocation
   */
  public boolean hasAllocation() {
    return (summaryFlags & HAS_ALLOCATION) != 0;
  }

  /**
   * @return true if the method contains an athrow
   */
  public boolean hasThrow() {
    return (summaryFlags & HAS_THROW) != 0;
  }

  /**
   * @return true if the method contains an invoke
   */
  public boolean hasInvoke() {
    return (summaryFlags & HAS_INVOKE) != 0;
  }

  /**
   * @return true if the method contains a getfield or getstatic
   */
  public boolean hasFieldRead() {
    return (summaryFlags & HAS_FIELD_READ) != 0;
  }

  /**
   * @return true if the method contains a putfield or putstatic
   */
  public boolean hasFieldWrite() {
    return (summaryFlags & HAS_FIELD_WRITE) != 0;
  }

  /**
   * @return true if the method contains an array load
   */
  public boolean hasArrayRead() {
    return (summaryFlags & HAS_ARRAY_READ) != 0;
  }

  /**
   * @return true if the method contains an array store
   */
  public boolean hasArrayWrite() {
    return (summaryFlags & HAS_ARRAY_WRITE) != 0;
  }

  /**
   * @return true if the method contains a jsr
   */
  public boolean hasJSR() {
    return (summaryFlags & HAS_JSR) != 0;
  }

  /**
   * @return true if the method contains a conditional branch
   */
  public boolean hasCondBranch() {
    return (summaryFlags & HAS_COND_BRANCH) != 0;
  }

  /**
   * @return true if the method contains a switch
   */
  public boolean hasSwitch() {
    return (summaryFlags & HAS_SWITCH) != 0;
  }

  /**
   * @return true if the method contains a backwards branch
   */
  public boolean hasBackwardsBranch() {
    return (summaryFlags & HAS_BACK_BRANCH) != 0;
  }

  /**
   * @return true if the method is the implementation of a runtime service
   * that is called "under the covers" from the generated code and thus is not subject to
   * inlining via the normal mechanisms.
   */
  public boolean isRuntimeServiceMethod() {
    return (summaryFlags & IS_RS_METHOD) != 0;
  }

  /**
   * Set the value of the 'runtime service method' flag to the argument
   * value.  A method is considered to be a runtime service method if it
   * is only/primarily invoked "under the covers" from the generated code
   * and thus is not subject to inlining via the normal mechanisms.
   * For example, the implementations of bytecodes such as new or checkcast
   * or the implementation of yieldpoints.
   * @param value true if this is a runtime service method, false it is not.
   */
  public void setRuntimeServiceMethod(boolean value) {
    if (value) {
      summaryFlags |= IS_RS_METHOD;
    } else {
      summaryFlags &= ~IS_RS_METHOD;
    }
  }

  /**
   * @return true if the method may write to a given field
   */
  public boolean mayWrite(RVMField field) {
    if (!hasFieldWrite()) return false;
    FieldReference it = field.getMemberRef().asFieldReference();
    BytecodeStream bcodes = getBytecodes();
    while (bcodes.hasMoreBytecodes()) {
      int opcode = bcodes.nextInstruction();
      if (opcode == JBC_putstatic || opcode == JBC_putfield) {
        FieldReference fr = bcodes.getFieldReference();
        if (!fr.definitelyDifferent(it)) return true;
      } else {
        bcodes.skipInstruction();
      }
    }
    return false;
  }

  /**
   * For use by {@link RVMClass#allBootImageTypesResolved()} only.
   */
  void recomputeSummary(int[] constantPool) {
    if (hasFieldRead()) {
      // Now that all bootimage classes are resolved, we may be able to lower the
      // estimated machine code size of some getstatics, so recompute summary.
      computeSummary(constantPool);
    }

  }

  /**
   * This method computes a summary of interesting method characteristics
   * and stores an encoding of the summary as an int.
   */
  private void computeSummary(int[] constantPool) {
    int calleeSize = 0;
    if (isSynchronized()) {
      summaryFlags |= HAS_SYNCH;
      calleeSize += 2 * SYNCH_COST; // NOTE: ignoring catch/unlock/rethrow block.  Probably the right thing to do.
    }

    BytecodeStream bcodes = getBytecodes();
    while (bcodes.hasMoreBytecodes()) {
      switch (bcodes.nextInstruction()) {
        // Array loads: null check, bounds check, index computation, load
        case JBC_iaload:
        case JBC_laload:
        case JBC_faload:
        case JBC_daload:
        case JBC_aaload:
        case JBC_baload:
        case JBC_caload:
        case JBC_saload:
          summaryFlags |= HAS_ARRAY_READ;
          calleeSize += ARRAY_LOAD_COST;
          break;

          // Array stores: null check, bounds check, index computation, load
        case JBC_iastore:
        case JBC_lastore:
        case JBC_fastore:
        case JBC_dastore:
        case JBC_bastore:
        case JBC_castore:
        case JBC_sastore:
          summaryFlags |= HAS_ARRAY_WRITE;
          calleeSize += ARRAY_STORE_COST;
          break;
        case JBC_aastore:
          summaryFlags |= HAS_ARRAY_WRITE;
          calleeSize += ARRAY_STORE_COST + STORE_CHECK_COST;
          break;

          // primitive computations (likely to be very cheap)
        case JBC_iadd:
        case JBC_fadd:
        case JBC_dadd:
        case JBC_isub:
        case JBC_fsub:
        case JBC_dsub:
        case JBC_imul:
        case JBC_fmul:
        case JBC_dmul:
        case JBC_idiv:
        case JBC_fdiv:
        case JBC_ddiv:
        case JBC_irem:
        case JBC_frem:
        case JBC_drem:
        case JBC_ineg:
        case JBC_fneg:
        case JBC_dneg:
        case JBC_ishl:
        case JBC_ishr:
        case JBC_lshr:
        case JBC_iushr:
        case JBC_iand:
        case JBC_ior:
        case JBC_ixor:
        case JBC_iinc:
          calleeSize += SIMPLE_OPERATION_COST;
          break;

          // long computations may be different cost than primitive computations
        case JBC_ladd:
        case JBC_lsub:
        case JBC_lmul:
        case JBC_ldiv:
        case JBC_lrem:
        case JBC_lneg:
        case JBC_lshl:
        case JBC_lushr:
        case JBC_land:
        case JBC_lor:
        case JBC_lxor:
          calleeSize += LONG_OPERATION_COST;
          break;

          // Some conversion operations are very cheap
        case JBC_int2byte:
        case JBC_int2char:
        case JBC_int2short:
          calleeSize += SIMPLE_OPERATION_COST;
          break;
          // Others are a little more costly
        case JBC_i2l:
        case JBC_l2i:
          calleeSize += LONG_OPERATION_COST;
          break;
          // Most are roughly as expensive as a call
        case JBC_i2f:
        case JBC_i2d:
        case JBC_l2f:
        case JBC_l2d:
        case JBC_f2i:
        case JBC_f2l:
        case JBC_f2d:
        case JBC_d2i:
        case JBC_d2l:
        case JBC_d2f:
          calleeSize += CALL_COST;
          break;

          // approximate compares as 1 simple operation
        case JBC_lcmp:
        case JBC_fcmpl:
        case JBC_fcmpg:
        case JBC_dcmpl:
        case JBC_dcmpg:
          calleeSize += SIMPLE_OPERATION_COST;
          break;

          // most control flow is cheap; jsr is more expensive
        case JBC_ifeq:
        case JBC_ifne:
        case JBC_iflt:
        case JBC_ifge:
        case JBC_ifgt:
        case JBC_ifle:
        case JBC_if_icmpeq:
        case JBC_if_icmpne:
        case JBC_if_icmplt:
        case JBC_if_icmpge:
        case JBC_if_icmpgt:
        case JBC_if_icmple:
        case JBC_if_acmpeq:
        case JBC_if_acmpne:
        case JBC_ifnull:
        case JBC_ifnonnull:
          summaryFlags |= HAS_COND_BRANCH;
          if (bcodes.getBranchOffset() < 0) summaryFlags |= HAS_BACK_BRANCH;
          calleeSize += SIMPLE_OPERATION_COST;
          continue; // we've processed all of the bytes, so avoid the call to skipInstruction()
        case JBC_goto:
          if (bcodes.getBranchOffset() < 0) summaryFlags |= HAS_BACK_BRANCH;
          calleeSize += SIMPLE_OPERATION_COST;
          continue; // we've processed all of the bytes, so avoid the call to skipInstruction()
        case JBC_goto_w:
          if (bcodes.getWideBranchOffset() < 0) summaryFlags |= HAS_BACK_BRANCH;
          calleeSize += SIMPLE_OPERATION_COST;
          continue; // we've processed all of the bytes, so avoid the call to skipInstruction()
        case JBC_jsr:
        case JBC_jsr_w:
          summaryFlags |= HAS_JSR;
          calleeSize += JSR_COST;
          break;

        case JBC_tableswitch:
        case JBC_lookupswitch:
          summaryFlags |= HAS_SWITCH;
          calleeSize += SWITCH_COST;
          break;

        case JBC_putstatic:
        case JBC_putfield:
          summaryFlags |= HAS_FIELD_WRITE;
          calleeSize += SIMPLE_OPERATION_COST;
          break;

        case JBC_getstatic:
          summaryFlags |= HAS_FIELD_READ;

          // Treat getstatic of primitive values from final static fields
          // as "free" since we expect it be a compile time constant by the
          // time the opt compiler compiles the method.
          FieldReference fldRef = bcodes.getFieldReference(constantPool);
          if (fldRef.getFieldContentsType().isPrimitiveType()) {
            RVMField fld = fldRef.peekResolvedField();
            if (fld == null || !fld.isFinal()){
              calleeSize += SIMPLE_OPERATION_COST;
            }
          } else {
            calleeSize += SIMPLE_OPERATION_COST;
          }
          continue; // we've processed all of the bytes, so avoid the call to skipInstruction()

        case JBC_getfield:
          summaryFlags |= HAS_FIELD_READ;
          calleeSize += SIMPLE_OPERATION_COST;
          break;

          // Various flavors of calls. Assign them call cost (differentiate?)
        case JBC_invokevirtual:
        case JBC_invokespecial:
        case JBC_invokestatic:
          // Special case Magic's as being cheaper.
          MethodReference meth = bcodes.getMethodReference(constantPool);
          if (meth.getType().isMagicType()) {
            summaryFlags |= HAS_MAGIC;
            calleeSize += MAGIC_COST;
          } else {
            summaryFlags |= HAS_INVOKE;
            calleeSize += CALL_COST;
          }
          continue; // we've processed all of the bytes, so avoid the call to skipInstruction()

        case JBC_invokeinterface:
          summaryFlags |= HAS_INVOKE;
          calleeSize += CALL_COST;
          break;

        case JBC_xxxunusedxxx:
          if (VM.VerifyAssertions) VM._assert(VM.NOT_REACHED);
          break;

        case JBC_new:
        case JBC_newarray:
        case JBC_anewarray:
          summaryFlags |= HAS_ALLOCATION;
          calleeSize += ALLOCATION_COST;
          break;

        case JBC_arraylength:
          calleeSize += SIMPLE_OPERATION_COST;
          break;

        case JBC_athrow:
          summaryFlags |= HAS_THROW;
          calleeSize += THROW_COST;
          break;

        case JBC_checkcast:
        case JBC_instanceof:
          calleeSize += CLASS_CHECK_COST;
          break;

        case JBC_monitorenter:
        case JBC_monitorexit:
          summaryFlags |= HAS_SYNCH;
          calleeSize += SYNCH_COST;
          break;

        case JBC_multianewarray:
          summaryFlags |= HAS_ALLOCATION;
          calleeSize += CALL_COST;
          break;
      }
      bcodes.skipInstruction();
    }
    if (calleeSize > Character.MAX_VALUE) {
      summarySize = Character.MAX_VALUE;
    } else {
      summarySize = (char) calleeSize;
    }
  }

  /**
   * @return LocalVariableTable associated with this method
   */
  public LocalVariableTable getLocalVariableTable() {
    return localVariableTables.get(this);
  }
}
