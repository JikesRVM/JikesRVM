/*
 * (C) Copyright IBM Corp. 2001
 */
//$Id$


/**
 * Compiler-specific information associated with a method's 
 * machine instructions.
 *
 * @author Bowen Alpern
 * @author Derek Lieber
 */
abstract class VM_CompilerInfo  {
  final static int TRAP      = 0; // no code: special trap handling stackframe
  final static int BASELINE  = 1; // baseline code
  final static int OPT       = 3; // opt code
  final static int JNI       = 4; // java to Native C transition frame

  /**
   * Get compiler that produced this method's machine code.
   * @return one of the constants, above
   * Note: use this instead of "instanceof" when gc is disabled (ie. during gc)
   */ 
  abstract int getCompilerType() throws VM_PragmaUninterruptible ;

  /**
   * Get handler to deal with stack unwinding and exception delivery for this 
   * method's stackframes.
   */ 
  abstract VM_ExceptionDeliverer getExceptionDeliverer() throws VM_PragmaUninterruptible;
   
  /**
   * Find "catch" block for a machine instruction of 
   * this method that might be guarded 
   * against specified class of exceptions by a "try" block .
   *
   * @param instructionOffset offset of machine instruction from start 
   * of this method, in bytes
   * @param exceptionType type of exception being thrown - 
   * something like "NullPointerException"
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
  abstract int findCatchBlockForInstruction(int instructionOffset, 
                                            VM_Type exceptionType);

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
    * @param instructionOffset of machine instruction from start of 
    * this method, in bytes
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
  abstract int findLineNumberForInstruction(int instructionOffset);

  /**
   * Find (earliest) machine instruction corresponding one of this method's 
   * source line numbers.
   * @param lineNumber source line number (1 == first line of source file)
   * @return instruction offset from start of this method, 
   * in bytes (-1 --> not found)
   */
  abstract int findInstructionForLineNumber(int lineNumber);

  /**
   * Find (earliest) machine instruction corresponding to the next 
   * valid source code line following this method's source line numbers.
   * @param lineNumber source line number (1 == first line of source file)
   * @return instruction offset from start of this method, in bytes 
   * (-1 --> no more valid code line)
   */
  abstract int findInstructionForNextLineNumber(int lineNumber);

  /**
   * Find local variables that are in scope of specified machine instruction.
   * @param instructionOffset offset of machine instruction from start of method
   * @return local variables (null --> no local variable information available)
   */
  abstract VM_LocalVariable[] findLocalVariablesForInstruction(int instructionOffset);

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
  abstract boolean up(VM_StackBrowser browser);

}
