/*
 * (C) Copyright IBM Corp. 2001
 */
// Information associated with artifical stackframe inserted by hardware trap handler.
// 02 Jun 1999 Derek Lieber
//
class VM_HardwareTrapCompilerInfo extends VM_CompilerInfo
   {
   final int 
   getCompilerType() 
      { 
      return TRAP; 
      }

   final VM_ExceptionDeliverer 
   getExceptionDeliverer()
      {
      // this method should never get called, because exception delivery begins
      // at site of exception, which is one frame above artificial "trap" frame
      // corresponding to this compiler-info object
      //
      if (VM.VerifyAssertions) VM.assert(VM.NOT_REACHED);
      return null;
      }
      
   final void
   getDynamicLink(VM_DynamicLink dynamicLink, int instructionOffset) //- implements VM_CompilerInfo
      {
      // this method should never get called, because exception delivery begins
      // at site of exception, which is one frame above artificial "trap" frame
      // corresponding to this compiler-info object
      //
      if (VM.VerifyAssertions) VM.assert(VM.NOT_REACHED);
      }

   final int
   findCatchBlockForInstruction(int instructionOffset, VM_Type exceptionType)
      {
      return -1;
      }
   
   final int
   findLineNumberForInstruction(int instructionOffset)
      {
      return 0;
      }

   final int
   findBytecodeIndexForInstruction(int instructionOffset)
      {
      return -1;
      }
      
   final int
   findInstructionForLineNumber(int lineNumber)
      {
      return -1;
      }
      
   final int
   findInstructionForNextLineNumber(int lineNumber) 
      {
      return -1;
      }

   public final VM_LocalVariable[]
   findLocalVariablesForInstruction(int instructionOffset)
      {
      return null;
      }

   public final void 
   printStackTrace(int instructionOffset, java.io.PrintStream out) 
      {
      out.println("\tat <hardware trap>");
      }

   public final void 
   printStackTrace(int instructionOffset, java.io.PrintWriter out)
      {
      out.println("\tat <hardware trap>");
      }
  }
