/*
 * (C) Copyright IBM Corp. 2001
 */
//$Id$

import java.io.PrintStream;
import java.io.PrintWriter;

/**
 * A list of method/instructionOffset pairs that describe the
 * state of the call stack at a particular instant.
 */
public class VM_StackTrace implements VM_Constants {
   //-----------//
   // interface //
   //-----------//

  /** 
   * Create a trace (walkback) of our own call stack.
   * @return list of stackframes that called us
   */
   public static VM_StackTrace[] create() {
     if (VM.TraceTimes) VM_Timer.start(VM_Timer.EXCEPTION_HANDLING);

     int vmStart = VM_BootRecord.the_boot_record.startAddress;
     int vmEnd = VM_BootRecord.the_boot_record.largeStart + VM_BootRecord.the_boot_record.largeSize;

     // count number of frames comprising stack
     //
     int stackFrameCount = 0;
     VM.disableGC(); // so fp & ip don't change under our feet
     int fp = VM_Magic.getFramePointer();
     int ip = VM_Magic.getReturnAddress(fp);
     fp = VM_Magic.getCallerFramePointer(fp);
     while (VM_Magic.getCallerFramePointer(fp) != STACKFRAME_SENTINAL_FP) {
       stackFrameCount++;
       int compiledMethodId = VM_Magic.getCompiledMethodID(fp);
       if (compiledMethodId!=INVISIBLE_METHOD_ID) {
	 VM_CompiledMethod compiledMethod = VM_ClassLoader.getCompiledMethod(compiledMethodId);
	 if (compiledMethod.getMethod().getDeclaringClass().isBridgeFromNative()) {
	   // skip native frames, stopping at last native frame preceeding the
	   // Java To C transition frame
	   fp = VM_Runtime.unwindNativeStackFrame(fp);	 
	 }
       } 
       ip = VM_Magic.getReturnAddress(fp);
       fp = VM_Magic.getCallerFramePointer(fp);
     }
     VM.enableGC();

     // allocate space in which to record stacktrace
     //
     VM_StackTrace[] stackTrace = new VM_StackTrace[stackFrameCount];
     for (int i = 0; i < stackFrameCount; ++i) {
       stackTrace[i] = new VM_StackTrace();
     }

     // rewalk stack and record stacktrace
     //
     VM.disableGC(); // so fp & ip don't change under our feet
     fp = VM_Magic.getFramePointer();
     ip = VM_Magic.getReturnAddress(fp);
     fp = VM_Magic.getCallerFramePointer(fp);
     for (int i = 0; i < stackFrameCount; ++i) {
       int compiledMethodId = VM_Magic.getCompiledMethodID(fp);
       if (compiledMethodId!=INVISIBLE_METHOD_ID) {
	 VM_CompiledMethod compiledMethod = VM_ClassLoader.getCompiledMethod(compiledMethodId);
	 stackTrace[i].compiledMethod = compiledMethod;
	 stackTrace[i].instructionOffset = ip - VM_Magic.objectAsAddress(compiledMethod.getInstructions());
	 if (compiledMethod.getMethod().getDeclaringClass().isBridgeFromNative()) {
	   // skip native frames, stopping at last native frame preceeding the
	   // Java To C transition frame
	   fp = VM_Runtime.unwindNativeStackFrame(fp);
	 }       
       }
       ip = VM_Magic.getReturnAddress(fp);
       fp = VM_Magic.getCallerFramePointer(fp);
     }
     VM.enableGC();
      
     if (VM.TraceTimes) VM_Timer.stop(VM_Timer.EXCEPTION_HANDLING);
     return stackTrace;
   }


  /**
   * Print stack trace.
   * Delegate the actual printing of the stack trace to the VM_CompilerInfo's to
   * deal with inlining by the opt compiler in a sensible fashion.
   * 
   * @param stackTrace stack trace to be printed
   * @param out        stream to print on
   */
  public static void print(VM_StackTrace[] stackTrace, PrintStream out) {
    for (int i = 0, n = stackTrace.length; i < n; ++i) {
      if (i == 50) { 
	// large stack - suppress excessive output
	int oldIndex = i;
	int newIndex = n - 50;
	if (newIndex > oldIndex) {
	  i = newIndex;
	  out.println("\t..." + (newIndex - oldIndex) + " stackframes omitted...");
	}
      }
         
      VM_CompiledMethod compiledMethod = stackTrace[i].compiledMethod;
      if (compiledMethod == null) {
	out.println("\tat <invisible method>");
      } else {
	compiledMethod.getCompilerInfo().printStackTrace(stackTrace[i].instructionOffset, out);
      }
    }
  }
   
  /**
   * Print stack trace.
   * Delegate the actual printing of the stack trace to the VM_CompilerInfo's to
   * deal with inlining by the opt compiler in a sensible fashion.
   * 
   * @param stackTrace stack trace to be printed
   * @param out        printwriter to print on
   */
   public static void print(VM_StackTrace[] stackTrace, PrintWriter out) {
     for (int i = 0, n = stackTrace.length; i < n; ++i) {
       if (i == 50) { // large stack - suppress excessive output
	 int oldIndex = i;
	 int newIndex = n - 10;
	 if (newIndex > oldIndex) {
	   i = newIndex;
	   out.println("\t..." + (newIndex - oldIndex) + " stackframes omitted...");
	 }
       }
         
       VM_CompiledMethod compiledMethod = stackTrace[i].compiledMethod;
       if (compiledMethod == null) {
	 out.println("\tat <invisible method>");
       }

       VM_Method       method       = compiledMethod.getMethod();
       VM_CompilerInfo compilerInfo = compiledMethod.getCompilerInfo();
       if (compilerInfo.getCompilerType() == VM_CompilerInfo.TRAP) {
	 out.println("\tat <hardware trap>");
       } else {
	 compiledMethod.getCompilerInfo().printStackTrace(stackTrace[i].instructionOffset, out);
       }
     }
   }

  //----------------//
  // implementation //
  //----------------//

  VM_CompiledMethod compiledMethod;
  int               instructionOffset;
}
