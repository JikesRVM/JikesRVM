/*
 * (C) Copyright IBM Corp. 2001
 */
//$Id$
package com.ibm.JikesRVM;

import com.ibm.JikesRVM.classloader.*;
import org.vmmagic.pragma.*;
import org.vmmagic.unboxed.*;

/**
 * A method that has been compiled into machine code by one of our compilers.
 * We implement VM_SynchronizedObject because we need to synchronized 
 * on the VM_CompiledMethod object as part of the invalidation protocol.
 * 
 * @author Bowen Alpern
 * @author Derek Lieber
 * @modified Steven Augart
 */
public abstract class VM_CompiledMethod implements VM_SynchronizedObject, VM_SizeConstants {

  /*
   * constants for compiler types
   */
  public final static int TRAP      = 0; // no code: special trap handling stackframe
  public final static int BASELINE  = 1; // baseline code
  public final static int OPT       = 3; // opt code
  public final static int JNI       = 4; // java to Native C transition frame

  /*
   * constants for bitField1
   */
  private final static int COMPILED     = 0x80000000;
  private final static int INVALID      = 0x40000000;
  private final static int OBSOLETE     = 0x20000000;

  //-#if RVM_WITH_OSR
  // flags the baseline compiled method is outdated, needs OSR
  private final static int OUTDATED         = 0x10000000;
  //-#endif
  protected final static int AVAIL_BITS = 0x0fffffff;

  /**
   * The compiled method id of this compiled method (index into VM_CompiledMethods)
   */
  protected final int cmid; 
  
  /**
   * The VM_Method that was compiled
   */
  protected final VM_Method method;

  /**
   * The compiled machine code for said method.
   */
  protected VM_CodeArray instructions; 

  //-#if RVM_WITH_OSR
  /**
   * Has the method sample data for this compiled method been reset?
   */
  private boolean samplesReset = false;
 
  public void setSamplesReset() { samplesReset = true; }
  public boolean getSamplesReset() { return samplesReset; }

  
  /* the offset of instructions in JTOC, for osr-special compiled method
   * only. all osr-ed method is treated like static.
   */
  protected boolean isSpecialForOSR = false;
  protected int osrJTOCoffset = 0;
  public void setSpecialForOSR() {
    this.isSpecialForOSR = true;

    // set jtoc
    this.osrJTOCoffset = VM_Statics.allocateSlot(VM_Statics.METHOD);
    VM_Statics.setSlotContents(this.osrJTOCoffset, this.instructions);
    this.osrJTOCoffset <<= LOG_BYTES_IN_INT;
  }

  public boolean isSpecialForOSR() {
    return this.isSpecialForOSR;
  }

  public int getOsrJTOCoffset() {
    if (VM.VerifyAssertions) VM._assert(this.isSpecialForOSR);
    return this.osrJTOCoffset;
  }
  //-#endif

  /**
   * The time in milliseconds taken to compile the method.
   */
  protected float compilationTime;

  /**
   * A bit field.  The upper 4 bits are reserved for use by 
   * VM_CompiledMethod.  Subclasses may use the lower 28 bits for their own
   * purposes.
   */
  protected int bitField1;

  /**
   * Set the cmid and method fields
   */
  public VM_CompiledMethod(int id, VM_Method m) {
    cmid   = id;
    method = m;
  }

  /**
   * Return the compiled method id for this compiled method
   */
  public final int getId() throws UninterruptiblePragma { 
    return cmid;           
  }

  /**
   * Return the VM_Method associated with this compiled method
   */
  public final VM_Method getMethod() throws UninterruptiblePragma { 
    return method;       
  }

  /**
   * Return the machine code for this compiled method
   */
  public final VM_CodeArray getInstructions() throws UninterruptiblePragma { 
    if (VM.VerifyAssertions) VM._assert((bitField1 & COMPILED) != 0);
    return instructions; 
  }

  /**
   * Return the offset in bytes of the given Address from the start
   * of the machine code array.
   * @param ip a Address (should be an interior pointer to instructions)
   * @return offset of addr from start of instructions in bytes
   */
  public final int getInstructionOffset(Address ip) throws UninterruptiblePragma {
    if (getCompilerType() == JNI || getCompilerType() == TRAP) {
      return 0;
    } else {
      VM_CodeArray code = getInstructions();
      Offset offset = ip.diff(VM_Magic.objectAsAddress(code));
      if (VM.VerifyAssertions) {
        int max = (code.length()+1)<< VM_Constants.LG_INSTRUCTION_WIDTH;
        if (!offset.toWord().LT(Word.fromIntZeroExtend(max))) {
          VM.sysWrite(method);
          VM.sysWriteln();
          VM.sysWriteln(offset);
          VM.sysWriteln(Offset.fromInt(max));
          VM.sysWriteln(ip);
          VM.sysWriteln(VM_Magic.objectAsAddress(code));
          VM._assert(false);
        }
      }
      // NOTE: we are absolutely positive that offset will fit in 32 bits
      // because we don't create VM_CodeArrays that are so massive it won't.
      // Thus, we do the assertion checking above to ensure that ip is in range.
      return offset.toInt();
    }
  }

  /**
   * Record that the compilation is complete.
   */
  public final void compileComplete(VM_CodeArray code) {
    instructions = code;
    bitField1 |= COMPILED;
  }

  /**
   * Mark the compiled method as invalid
   */
  public final void setInvalid() {
    bitField1 |= INVALID;
  }

  /**
   * Mark the compiled method as obsolete (ie a candidate for eventual GC)
   */
  public final void setObsolete(boolean sense) throws UninterruptiblePragma {
    if (sense) {
      bitField1 |= OBSOLETE;
    } else {
      bitField1 &= ~OBSOLETE;
    }
  }

  //-#if RVM_WITH_OSR
  /**
   * Mark the compiled method as outdated (ie requires OSR),
   * the flag is set in VM_AnalyticModel
   */
  public final void setOutdated() throws UninterruptiblePragma {
    if (VM.VerifyAssertions) VM._assert(this.getCompilerType() == BASELINE);
    bitField1 |= OUTDATED;
  }
  
  /**
   * Check if the compiled method is marked as outdated,
   * called by VM_Thread
   */
  public final boolean isOutdated() throws UninterruptiblePragma {
    return (bitField1 & OUTDATED) != 0;
  }
  //-#endif
  
  /**
   * Has compilation completed?
   */
  public final boolean isCompiled() throws UninterruptiblePragma {
    return (bitField1 & COMPILED) != 0;
  }

  /**
   * Is the compiled code invalid?
   */
  public final boolean isInvalid() throws UninterruptiblePragma {
    return (bitField1 & INVALID) != 0;
  }

  /**
   * Is the compiled code obsolete?
   */
  public final boolean isObsolete() throws UninterruptiblePragma { 
    return (bitField1 & OBSOLETE) != 0;
  }

  public final double getCompilationTime() { return (double)compilationTime; }
  public final void setCompilationTime(double ct) { compilationTime = (float)ct; }

  /**
   * Identify the compiler that produced this compiled method.
   * @return one of TRAP, BASELINE, OPT, or JNI.
   * Note: use this instead of "instanceof" when gc is disabled (ie. during gc)
   */ 
  public abstract int getCompilerType() throws UninterruptiblePragma;

  public static final String compilerTypeToString (int compilerType) throws UninterruptiblePragma {
    switch (compilerType) {
      case TRAP: return "TRAP";
      case BASELINE: return "BASELINE";
      case OPT: return "OPT";
      case JNI: return "JNI";
      default: if (VM.VerifyAssertions) VM._assert(false); return null;
    }
  }

  /**
   * @return Name of the compiler that produced this compiled method.
   */ 
  public abstract String getCompilerName();

  /**
   * Get handler to deal with stack unwinding and exception delivery for this 
   * compiled method's stackframes.
   */ 
  public abstract VM_ExceptionDeliverer getExceptionDeliverer() throws UninterruptiblePragma;
   
  /**
   * Find "catch" block for a machine instruction of 
   * this method that might be guarded 
   * against specified class of exceptions by a "try" block .
   *
   * @param instructionOffset offset of machine instruction from start of this method, in bytes
   * @param exceptionType type of exception being thrown - something like "NullPointerException"
   * @return offset of machine instruction for catch block 
   * (-1 --> no catch block)
   * 
   * Notes: 
   * <ul>
   * <li> The "instructionOffset" must point to the instruction 
   * <em> following </em> the actual
   * instruction whose catch block is sought. 
   * This allows us to properly handle the case where
   * the only address we have to work with is a return address 
   * (ie. from a stackframe)
   * or an exception address 
   * (ie. from a null pointer dereference, array bounds check,
   * or divide by zero) on a machine architecture with variable length 
   * instructions.
   * In such situations we'd have no idea how far to back up the 
   * instruction pointer
   * to point to the "call site" or "exception site".
   * 
   * <li> This method must not cause any allocations, because it executes with
   * gc disabled when called by VM_Runtime.deliverException().
   * </ul>
   */
  public abstract int findCatchBlockForInstruction(int instructionOffset, VM_Type exceptionType);

  /**
   * Fetch symbolic reference to a method that's called by one of 
   * this method's instructions.
   * @param dynamicLink place to put return information
   * @param instructionOffset offset of machine instruction from start of 
   * this method, in bytes
   * @return nothing (return information is filled in)
   *
   * Notes: 
   * <ul>
   * <li> The "instructionOffset" must point to the instruction i
   * <em> following </em> the call
   * instruction whose target method is sought. 
   * This allows us to properly handle the case where
   * the only address we have to work with is a return address 
   * (ie. from a stackframe)
   * on a machine architecture with variable length instructions.
   * In such situations we'd have no idea how far to back up the 
   * instruction pointer
   * to point to the "call site".
   *
   * <li> The implementation must not cause any allocations, 
   * because it executes with
   * gc disabled when called by VM_GCMapIterator.
   * <ul>
   */
  public abstract void getDynamicLink(VM_DynamicLink dynamicLink, 
                                      int instructionOffset) throws UninterruptiblePragma;

   /**
    * Find source line number corresponding to one of this method's 
    * machine instructions.
    * @param instructionOffset of machine instruction from start of this method, in bytes
    * @return source line number 
    * (0 == no line info available, 1 == first line of source file)
    *
    * <p> Usage note: "instructionOffset" must point to the 
    * instruction <em> following </em> the actual instruction
    * whose line number is sought. 
    * This allows us to properly handle the case where
    * the only address we have to work with is a return address 
    * (ie. from a stackframe)
    * or an exception address 
    * (ie. from a null pointer dereference, array bounds check,
    * or divide by zero) on a machine architecture with variable length 
    * instructions.
    * In such situations we'd have no idea how far to back up the 
    * instruction pointer
    * to point to the "call site" or "exception site".
    */
  public int findLineNumberForInstruction(int instructionOffset) throws UninterruptiblePragma {
    return 0;
  }

  /**
   * Print this compiled method's portion of a stack trace 
   * @param instructionOffset offset of machine instruction from start of method
   * @param out the PrintLN to print the stack trace to.
   */
  public abstract void printStackTrace(int instructionOffset, com.ibm.JikesRVM.PrintLN out);

  /**
   * Set the stack browser to the innermost logical stack frame of this method
   */
  public abstract void set(VM_StackBrowser browser, int instr);

  /**
   * Advance the VM_StackBrowser up one internal stack frame, if possible
   */
  public boolean up(VM_StackBrowser browser) { return false; }

  /**
   * Return the number of bytes used to encode the compiler-specific mapping 
   * information for this compiled method.
   * Used to gather stats on the space costs of mapping schemes.
   */
  public int size() { return 0; }

}
