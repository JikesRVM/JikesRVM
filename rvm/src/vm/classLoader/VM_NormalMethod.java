/*
 * (C) Copyright IBM Corp 2001,2002
 */
//$Id$
package com.ibm.JikesRVM.classloader;

import com.ibm.JikesRVM.*;
import java.io.DataInputStream;
import java.io.IOException;
import org.vmmagic.pragma.*;

/**
 * A method of a java class that has bytecodes.
 *
 * @author Bowen Alpern
 * @author Stephen Fink
 * @author Dave Grove
 * @author Derek Lieber
 */
public final class VM_NormalMethod 
  extends VM_Method 
  implements VM_BytecodeConstants 
{

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
  public static final int CLASS_CHECK_COST = 2*SIMPLE_OPERATION_COST;
  public static final int STORE_CHECK_COST = 4*SIMPLE_OPERATION_COST;
  // Just a call.
  public static final int THROW_COST = CALL_COST;
  // Really a bunch of operations plus a call, but undercharge because
  // we don't have worry about this causing an exponential growth of call chain
  // and we probably want to inline synchronization 
  // (to get a chance to optimize it).
  public static final int SYNCH_COST = 4*SIMPLE_OPERATION_COST;
  // The additional cost of a switch isn't that large, since if the 
  // switch has more than a few cases the method will be too big to inline 
  // anyways.
  public static final int SWITCH_COST = CALL_COST;

  /* We encode the summary in one int.  The following constants
   * are used in the encoding.
   * Summary format: ffff ffff ffff ffff ssss ssss ssss ssss
   * f = 16 bits of flags
   * s = 16 bits of size estimate
   */
  private static final int FLAG_MASK = 0xffff0000;
  private static final int SIZE_MASK = 0x0000ffff;
  // Definition of flag bits
  private static final int HAS_MAGIC      = 0x80000000;
  private static final int HAS_SYNCH      = 0x40000000;
  private static final int HAS_ALLOCATION = 0x20000000;
  private static final int HAS_THROW      = 0x10000000;
  private static final int HAS_INVOKE     = 0x08000000;
  private static final int HAS_FIELD_READ = 0x04000000;
  private static final int HAS_FIELD_WRITE= 0x02000000;
  private static final int HAS_ARRAY_READ = 0x01000000;
  private static final int HAS_ARRAY_WRITE= 0x00800000;
  private static final int HAS_JSR        = 0x00400000;
  private static final int HAS_COND_BRANCH= 0x00200000;
  private static final int HAS_SWITCH     = 0x00100000;
  private static final int HAS_BACK_BRANCH= 0x00080000;
  private static final int IS_RS_METHOD   = 0x00040000;
  
  /**
   * storage for bytecode summary
   */
  private int summary;

  /**
   * words needed for local variables (including parameters)
   */
  private final int localWords;          

  /**
   * words needed for operand stack (high water mark)
   */
  //-#if RVM_WITH_OSR
  private int operandWords;        
  //-#else
  private final int operandWords;        
  //-#endif

  /**
   * bytecodes for this method (null --> none)
   */
  private final byte[] bytecodes;           

  /**
   * try/catch/finally blocks for this method (null --> none)
   */
  private final VM_ExceptionHandlerMap exceptionHandlerMap; 

  /**
   * pc to source-line info (null --> none)
   * Each entry contains both the line number (upper 16 bits)
   * and corresponding start PC (lower 16 bits).
   */
  private final int[] lineNumberMap;       

  //-#if RVM_WITH_OSR 
  // Extra fields for on-stack replacement
  // TODO: rework the system so we don't waste space for this on the VM_Method object
  /* bytecode array constists of prologue and original bytecodes */
  private byte[] synthesizedBytecodes = null;
  /* record osr prologue */
  private byte[] osrPrologue = null;
  /* prologue may change the maximum stack height, remember the
   * original stack height */
  private int savedOperandWords;
  //-#endif

  /**
   * @param dc the VM_Class object of the class that declared this field
   * @param mr the canonical memberReference for this member.
   * @param mo modifiers associated with this member.
   * @param et exceptions thrown by this method.
   * @param lw the number of local words used by the bytecode of this method
   * @param ow the number of operand words used by the bytecode of this method
   * @param bc the bytecodes of this method
   * @param eMap the exception handler map for this method
   * @param lm the line number map for this method
   */
  VM_NormalMethod(VM_Class dc, VM_MemberReference mr,
                  int mo, VM_TypeReference[] et, int lw, int ow, byte[] bc,
                  VM_ExceptionHandlerMap eMap, int[] lm) 
  {
    super(dc, mr, mo, et);
    localWords = lw;
    operandWords = ow;
    bytecodes = bc;
    exceptionHandlerMap = eMap;
    lineNumberMap = lm;
    computeSummary();
  }

  /**
   * Generate the code for this method
   */
  protected VM_CompiledMethod genCode() throws VerifyError {
    if (VM.VerifyBytecode) {
      VM_Verifier verifier = new VM_Verifier();
      verifier.verifyMethod(this);
    }

    if (VM.writingBootImage) {
      return VM_BootImageCompiler.compile(this); 
    } else {
      return VM_RuntimeCompiler.compile(this);
    }
  }
  
  /**
   * Space required by this method for its local variables, in words.
   * Note: local variables include parameters
   */
  public final int getLocalWords() throws UninterruptiblePragma {
    return localWords;
  }

  /**
   * Space required by this method for its operand stack, in words.
   */
  public final int getOperandWords() throws UninterruptiblePragma {
    return operandWords;
  }

  /**
   * Get a representation of the bytecodes in the code attribute of this method.
   * @return object representing the bytecodes
   */
  public final VM_BytecodeStream getBytecodes() {
    return new VM_BytecodeStream(this, bytecodes);
  }  
  
  /**
   * Fill in DynamicLink object for the invoke at the given bytecode index
   * @param dynamicLink the dynamicLink object to initialize
   * @param bcIndex the bcIndex of the invoke instruction
   */
  public final void getDynamicLink(VM_DynamicLink dynamicLink, int bcIndex) throws UninterruptiblePragma {
    if (VM.VerifyAssertions) VM._assert(bytecodes != null);
    if (VM.VerifyAssertions) VM._assert(bcIndex + 2 < bytecodes.length);
    int bytecode = bytecodes[bcIndex] & 0xFF;
    if (VM.VerifyAssertions) VM._assert((VM_BytecodeConstants.JBC_invokevirtual <= bytecode)
                                        && (bytecode <= VM_BytecodeConstants.JBC_invokeinterface));
    int constantPoolIndex = ((bytecodes[bcIndex + 1] & 0xFF) << BITS_IN_BYTE) | (bytecodes[bcIndex + 2] & 0xFF);
    dynamicLink.set(declaringClass.getMethodRef(constantPoolIndex), bytecode);
  }

  /**
   * Size of bytecodes for this method
   */
  public final int getBytecodeLength() {
    return bytecodes.length;
  }

  /**
   * Exceptions caught by this method.
   * @return info (null --> method doesn't catch any exceptions)
   */
  public final VM_ExceptionHandlerMap getExceptionHandlerMap() throws UninterruptiblePragma {
    return exceptionHandlerMap;
  }

  /**
   * Return the line number information for the argument bytecode index.
   */
  public final int getLineNumberForBCIndex(int bci) throws UninterruptiblePragma {
    if (lineNumberMap == null) return 0;
    int idx;
    for (idx = 0; idx<lineNumberMap.length; idx++) {
      int pc = lineNumberMap[idx] & 0xffff; // lower 16 bits are bcIndex
      if (bci < pc) {
        if (idx == 0) idx++; // add 1, so we can subtract 1 below.
        break;
      }
    }
    return lineNumberMap[--idx] >>> 16; // upper 16 bits are line number
  }

  //-#if RVM_WITH_OSR 
  // Extra methods for on-stack replacement
  // VM_BaselineCompiler and OPT_BC2IR should check if a method is
  // for specialization by calling isForOsrSpecialization, the compiler
  // uses synthesized bytecodes (prologue + original bytecodes) for 
  // OSRing method. Other interfaces of method are not changed, therefore,
  // dynamic linking and gc referring to bytecodes are safe.
  
  /**
   * Checks if the method is in state for OSR specialization now 
   * @return true, if it is (with prologue)
   */
  public boolean isForOsrSpecialization() {
    return this.synthesizedBytecodes != null;
  }

  /**
   * Sets method in state for OSR specialization, i.e, the subsequent calls
   * of getBytecodes return the stream of specilized bytecodes.
   * NB: between flag and action, it should not allow GC or threadSwitch happen.
   * @param prologue, the bytecode of prologue
   * @param newStackHeight, the prologue may change the default height of 
   *                        stack
   */
  public void setForOsrSpecialization(byte[] prologue, int newStackHeight) {
    if (VM.VerifyAssertions) VM._assert(this.synthesizedBytecodes == null);

    byte[] newBytecodes = new byte[prologue.length + bytecodes.length];
    System.arraycopy(prologue, 0, newBytecodes, 0, prologue.length);
    System.arraycopy(bytecodes, 0, newBytecodes, prologue.length, bytecodes.length);
   
    this.osrPrologue = prologue;
    this.synthesizedBytecodes = newBytecodes;
    this.savedOperandWords = operandWords;
    if (newStackHeight > operandWords) 
      this.operandWords = newStackHeight;
  }
 
  /**
   * Restores the original state of the method.
   */
  public void finalizeOsrSpecialization() {
    if (VM.VerifyAssertions) VM._assert(this.synthesizedBytecodes != null);
    this.synthesizedBytecodes = null;
    this.osrPrologue  = null;
    this.operandWords = savedOperandWords;
  }

  /**
   * Returns the OSR prologue length for adjusting various tables and maps.
   * @return the length of prologue if the method is in state for OSR,
   *         0 otherwise.
   */
  public int getOsrPrologueLength() {
    return isForOsrSpecialization()?this.osrPrologue.length:0;
  }

  /** 
   * Returns a bytecode stream of osr prologue
   * @return osr prologue bytecode stream
   */
  public VM_BytecodeStream getOsrPrologue() {
    if (VM.VerifyAssertions) VM._assert(synthesizedBytecodes != null);
    return new VM_BytecodeStream(this, osrPrologue);
  }
  
  /**
   * Returns the synthesized bytecode stream with osr prologue
   * @return bytecode stream
   */
  public VM_BytecodeStream getOsrSynthesizedBytecodes() {
    if (VM.VerifyAssertions) VM._assert(synthesizedBytecodes != null);
    return new VM_BytecodeStream(this, synthesizedBytecodes);
  }              
  //-#endif RVM_WITH_OSR


  /*
   * Methods to access and compute method summary information
   */

  /** 
   * @return An estimate of the expected size of the machine code instructions 
   * that will be generated by the opt compiler if the method is inlined.
   */
  public final int inlinedSizeEstimate() {
    return summary & SIZE_MASK;
  }

  /**
   * @return true if the method contains a VM_Magic.xxx or Address.yyy
   */
  public final boolean hasMagic() {
    return (summary & HAS_MAGIC) != 0;
  }

  /**
   * @return true if the method contains a monitorenter/exit or is synchronized
   */
  public final boolean hasSynch() {
    return (summary & HAS_SYNCH) != 0;
  }

  /**
   * @return true if the method contains an allocation
   */
  public final boolean hasAllocation() {
    return (summary & HAS_ALLOCATION) != 0;
  }

  /**
   * @return true if the method contains an athrow
   */
  public final boolean hasThrow() {
    return (summary & HAS_THROW) != 0;
  }

  /**
   * @return true if the method contains an invoke
   */
  public final boolean hasInvoke() {
    return (summary & HAS_INVOKE) != 0;
  }

  /**
   * @return true if the method contains a getfield or getstatic
   */
  public final boolean hasFieldRead() {
    return (summary & HAS_FIELD_READ) != 0;
  }

  /**
   * @return true if the method contains a putfield or putstatic
   */
  public final boolean hasFieldWrite() {
    return (summary & HAS_FIELD_WRITE) != 0;
  }

  /**
   * @return true if the method contains an array load
   */
  public final boolean hasArrayRead() {
    return (summary & HAS_ARRAY_READ) != 0;
  }

  /**
   * @return true if the method contains an array store
   */
  public final boolean hasArrayWrite() {
    return (summary & HAS_ARRAY_WRITE) != 0;
  }

  /**
   * @return true if the method contains a jsr
   */
  public final boolean hasJSR() {
    return (summary & HAS_JSR) != 0;
  }

  /**
   * @return true if the method contains a conditional branch
   */
  public final boolean hasCondBranch() {
    return (summary & HAS_COND_BRANCH) != 0;
  }

  /**
   * @return true if the method contains a switch
   */
  public final boolean hasSwitch() {
    return (summary & HAS_SWITCH) != 0;
  }

  /**
   * @return true if the method contains a backwards branch
   */
  public final boolean hasBackwardsBranch() {
    return (summary & HAS_BACK_BRANCH) != 0;
  }

  /**
   * @return true if the method is the implementation of a runtime service
   * that is called "under the covers" from the generated code and thus is not subject to
   * inlining via the normal mechanisms.
   */
  public final boolean isRuntimeServiceMethod() {
    return (summary & IS_RS_METHOD) != 0;
  }

  /**
   * Set the value of the 'runtime service method' flag to the argument
   * value.  A method is considered to be a runtime service method if it
   * is only/primarialy invoked "under the covers" from the generated code
   * and thus is not subject to inlining via the normal mechanisms.
   * For example, the implementations of bytecodes such as new or checkcast
   * or the implementation of yieldpoints.
   * @param value true if this is a runtime service method, false it is not.
   */
  public final void setRuntimeServiceMethod(boolean value) {
    if (value) {
      summary |= IS_RS_METHOD;
    } else {
      summary &= ~IS_RS_METHOD;
    }
  }
  
  /**
   * @return true if the method may write to a given field
   */
  public final boolean mayWrite(VM_Field field) {
    if (!hasFieldWrite()) return false;
    VM_FieldReference it = field.getMemberRef().asFieldReference();
    VM_BytecodeStream bcodes = getBytecodes();
    while (bcodes.hasMoreBytecodes()) {
      int opcode = bcodes.nextInstruction();
      if (opcode == JBC_putstatic || opcode == JBC_putfield) {
        VM_FieldReference fr = bcodes.getFieldReference();
        if (!fr.definitelyDifferent(it)) return true;
      } else {
        bcodes.skipInstruction();
      }
    }
    return false;
  }

  /**
   * This method computes a summary of interesting method characteristics 
   * and stores an encoding of the summary as an int.
   */
  private void computeSummary() {
    int calleeSize = 0;
    if (isSynchronized()) {
      summary |= HAS_SYNCH;
      calleeSize += 2*SYNCH_COST; // NOTE: ignoring catch/unlock/rethrow block.  Probably the right thing to do.
    }
    
    VM_BytecodeStream bcodes = getBytecodes();
    while (bcodes.hasMoreBytecodes()) {
      switch (bcodes.nextInstruction()) {
        // Array loads: null check, bounds check, index computation, load
      case JBC_iaload:case JBC_laload:case JBC_faload:case JBC_daload:
      case JBC_aaload:case JBC_baload:case JBC_caload:case JBC_saload:
        summary |= HAS_ARRAY_READ;
        calleeSize += ARRAY_LOAD_COST;
        break;

        // Array stores: null check, bounds check, index computation, load
      case JBC_iastore:case JBC_lastore:case JBC_fastore:
      case JBC_dastore:case JBC_bastore:case JBC_castore:case JBC_sastore:
        summary |= HAS_ARRAY_WRITE;
        calleeSize += ARRAY_STORE_COST;
        break;
      case JBC_aastore:
        summary |= HAS_ARRAY_WRITE;
        calleeSize += ARRAY_STORE_COST + STORE_CHECK_COST;
        break;

        // primitive computations (likely to be very cheap)
      case JBC_iadd:case JBC_fadd:case JBC_dadd:case JBC_isub:
      case JBC_fsub:case JBC_dsub:case JBC_imul:case JBC_fmul:
      case JBC_dmul:case JBC_idiv:case JBC_fdiv:case JBC_ddiv:
      case JBC_irem:case JBC_frem:case JBC_drem:case JBC_ineg:
      case JBC_fneg:case JBC_dneg:case JBC_ishl:case JBC_ishr:
      case JBC_lshr:case JBC_iushr:case JBC_iand:case JBC_ior:
      case JBC_ixor:case JBC_iinc:
        calleeSize += SIMPLE_OPERATION_COST;
        break;

        // long computations may be different cost than primitive computations
      case JBC_ladd:case JBC_lsub:case JBC_lmul:case JBC_ldiv:
      case JBC_lrem:case JBC_lneg:case JBC_lshl:case JBC_lushr:
      case JBC_land:case JBC_lor:case JBC_lxor:
        calleeSize += LONG_OPERATION_COST;
        break;

        // Some conversion operations are very cheap
      case JBC_int2byte:case JBC_int2char:case JBC_int2short:
        calleeSize += SIMPLE_OPERATION_COST;
        break;
        // Others are a little more costly
      case JBC_i2l:case JBC_l2i:
        calleeSize += LONG_OPERATION_COST;
        break;
        // Most are roughly as expensive as a call
      case JBC_i2f:case JBC_i2d:case JBC_l2f:case JBC_l2d:
      case JBC_f2i:case JBC_f2l:case JBC_f2d:case JBC_d2i:
      case JBC_d2l:case JBC_d2f:
        calleeSize += CALL_COST;
        break;

        // approximate compares as 1 simple operation
      case JBC_lcmp:case JBC_fcmpl:case JBC_fcmpg:case JBC_dcmpl:
      case JBC_dcmpg:
        calleeSize += SIMPLE_OPERATION_COST;
        break;

        // most control flow is cheap; jsr is more expensive
      case JBC_ifeq:case JBC_ifne:case JBC_iflt:case JBC_ifge:
      case JBC_ifgt:case JBC_ifle:case JBC_if_icmpeq:case JBC_if_icmpne:
      case JBC_if_icmplt:case JBC_if_icmpge:case JBC_if_icmpgt:
      case JBC_if_icmple:case JBC_if_acmpeq:case JBC_if_acmpne:
      case JBC_ifnull:case JBC_ifnonnull:
        summary |= HAS_COND_BRANCH;
        if (bcodes.getBranchOffset() < 0) summary |= HAS_BACK_BRANCH;
        calleeSize += SIMPLE_OPERATION_COST;
        continue; // we've processed all of the bytes, so avoid the call to skipInstruction()
      case JBC_goto:
        if (bcodes.getBranchOffset() < 0) summary |= HAS_BACK_BRANCH;
        calleeSize += SIMPLE_OPERATION_COST;
        continue; // we've processed all of the bytes, so avoid the call to skipInstruction()
      case JBC_goto_w:
        if (bcodes.getWideBranchOffset() < 0) summary |= HAS_BACK_BRANCH;
        calleeSize += SIMPLE_OPERATION_COST;
        continue; // we've processed all of the bytes, so avoid the call to skipInstruction()
      case JBC_jsr:case JBC_jsr_w:
        summary |= HAS_JSR;
        calleeSize += JSR_COST;
        break;

      case JBC_tableswitch:case JBC_lookupswitch:
        summary |= HAS_SWITCH;
        calleeSize += SWITCH_COST;
        break;

      case JBC_putstatic: case JBC_putfield: 
        summary |= HAS_FIELD_WRITE;
        calleeSize += SIMPLE_OPERATION_COST;
        break;

      case JBC_getstatic: case JBC_getfield: 
        summary |= HAS_FIELD_READ;
        calleeSize += SIMPLE_OPERATION_COST;
        break;
        
        // Various flavors of calls. Assign them call cost (differentiate?)
      case JBC_invokevirtual:case JBC_invokespecial:
      case JBC_invokestatic:   
        // Special case VM_Magic's as being cheaper.
        VM_MethodReference meth = bcodes.getMethodReference();
        if (meth.getType().isMagicType()) {
          summary |= HAS_MAGIC;
          calleeSize += MAGIC_COST;
        } else {
          summary |= HAS_INVOKE;
          calleeSize += CALL_COST;
        }
        continue; // we've processed all of the bytes, so avoid the call to skipInstruction()
        
      case JBC_invokeinterface:
        summary |= HAS_INVOKE;
        calleeSize += CALL_COST;
        break;

      case JBC_xxxunusedxxx:
        if (VM.VerifyAssertions) VM._assert(VM.NOT_REACHED);
        break;

      case JBC_new: case JBC_newarray: case JBC_anewarray:
        summary |= HAS_ALLOCATION;
        calleeSize += ALLOCATION_COST;
        break;

      case JBC_arraylength:
        calleeSize += SIMPLE_OPERATION_COST;
        break;

      case JBC_athrow:
        summary |= HAS_THROW;
        calleeSize += THROW_COST;
        break;

      case JBC_checkcast:case JBC_instanceof:
        calleeSize += CLASS_CHECK_COST;
        break;

      case JBC_monitorenter:case JBC_monitorexit:
        summary |= HAS_SYNCH;
        calleeSize += SYNCH_COST;
        break;

      case JBC_multianewarray:
        summary |= HAS_ALLOCATION;
        calleeSize += CALL_COST;
        break;
      }
      bcodes.skipInstruction();
    }
    if (calleeSize > SIZE_MASK) {
      summary |= SIZE_MASK;
    } else {
      summary |= calleeSize;
    }
  }
}
