/*
 * (C) Copyright IBM Corp. 2001
 */
//$Id$

/**
 * A method that has been compiled into machine code by one of our compilers.
 * We implement VM_SynchronizedObject because we need to synchronized 
 * on the VM_CompiledMethod object as part of the invalidation protocol.
 * 
 * @author Bowen Alpern
 * @author Derek Lieber
 */
abstract class VM_CompiledMethod implements VM_SynchronizedObject {

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
  protected INSTRUCTION[] instructions; 

  /**
   * A bit field.  The upper 4 bits are reserved for use by 
   * VM_CompiledMethod.  Subclasses may use the lower 28 bits for their own
   * purposes.
   */
  protected int bitField1;

  /**
   * Set the cmid and method fields
   */
  VM_CompiledMethod(int id, VM_Method m) {
    cmid   = id;
    method = m;
  }

  /**
   * Return the compiled method id for this compiled method
   */
  public final int getId() throws VM_PragmaUninterruptible { 
    return cmid;           
  }

  /**
   * Return the VM_Method associated with this compiled method
   */
  public final VM_Method getMethod() throws VM_PragmaUninterruptible { 
    return method;       
  }

  /**
   * Return the machine code for this compiled method
   */
  final INSTRUCTION[] getInstructions() throws VM_PragmaUninterruptible { 
    if (VM.VerifyAssertions) VM.assert((bitField1 & COMPILED) != 0);
    return instructions; 
  }

  /**
   * Record that the compilation is complete.
   */
  final void compileComplete(INSTRUCTION[] code) {
    instructions = code;
    bitField1 |= COMPILED;
  }

  /**
   * Mark the compiled method as invalid
   */
  final void setInvalid() {
    bitField1 |= INVALID;
  }

  /**
   * Mark the compiled method as obsolete (ie a candidate for eventual GC)
   */
  final void setObsolete(boolean sense) throws VM_PragmaUninterruptible {
    if (sense) {
      bitField1 |= OBSOLETE;
    } else {
      bitField1 &= ~OBSOLETE;
    }
  }

  /**
   * Has compilation completed?
   */
  final boolean isCompiled() throws VM_PragmaUninterruptible {
    return (bitField1 & COMPILED) != 0;
  }

  /**
   * Is the compiled code invalid?
   */
  final boolean isInvalid() throws VM_PragmaUninterruptible {
    return (bitField1 & INVALID) != 0;
  }

  /**
   * Is the compiled code obsolete?
   */
  final boolean	isObsolete() throws VM_PragmaUninterruptible { 
    return (bitField1 & OBSOLETE) != 0;
  }



  /**
   * Identify the compiler that produced this compiled method.
   * @return one of TRAP, BASELINE, OPT, or JNI.
   * Note: use this instead of "instanceof" when gc is disabled (ie. during gc)
   */ 
  abstract int getCompilerType() throws VM_PragmaUninterruptible;

  /**
   * Get handler to deal with stack unwinding and exception delivery for this 
   * compiled method's stackframes.
   */ 
  abstract VM_ExceptionDeliverer getExceptionDeliverer() throws VM_PragmaUninterruptible;
   
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
  abstract int findCatchBlockForInstruction(int instructionOffset, VM_Type exceptionType);

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
  abstract void getDynamicLink(VM_DynamicLink dynamicLink, 
                               int instructionOffset) throws VM_PragmaUninterruptible;

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
  int findLineNumberForInstruction(int instructionOffset) {
    return 0;
  }

  /**
   * Find (earliest) machine instruction corresponding one of this method's 
   * source line numbers.
   * @param lineNumber source line number (1 == first line of source file)
   * @return instruction offset from start of this method, 
   * in bytes (-1 --> not found)
   */
  int findInstructionForLineNumber(int lineNumber) {
    return -1;
  }

  /**
   * Find (earliest) machine instruction corresponding to the next 
   * valid source code line following this method's source line numbers.
   * @param lineNumber source line number (1 == first line of source file)
   * @return instruction offset from start of this method, in bytes 
   * (-1 --> no more valid code line)
   */
  int findInstructionForNextLineNumber(int lineNumber) {
    return -1;
  }

  /**
   * Find local variables that are in scope of specified machine instruction.
   * @param instructionOffset offset of machine instruction from start of method
   * @return local variables (null --> no local variable information available)
   */
  VM_LocalVariable[] findLocalVariablesForInstruction(int instructionOffset) {
    return null;
  }

  /**
   * Print this compiled method's portion of a stack trace 
   * @param instructionOffset offset of machine instruction from start of method
   * @param out the PrintStream to print the stack trace to.
   */
  abstract void printStackTrace(int instructionOffset, java.io.PrintStream out);
     
  /**
   * Print this compiled method's portion of a stack trace 
   * @param instructionOffset offset of machine instruction from start of method
   * @param out the PrintWriter to print the stack trace to.
   */
  abstract void printStackTrace(int instructionOffset, java.io.PrintWriter out);

  /**
   * Set the stack browser to the innermost logical stack frame of this method
   */
  abstract void set(VM_StackBrowser browser, int instr);

  /**
   * Advance the VM_StackBrowser up one internal stack frame, if possible
   */
  boolean up(VM_StackBrowser browser) { return false; }

  /**
   * Return the number of bytes of primitive arrays used to encode
   * the compiler-specific mapping information for this compiled method.
   * Used to gather stats on the space costs of mapping schemes.
   */
  int dataSize() { return 0; }

}
