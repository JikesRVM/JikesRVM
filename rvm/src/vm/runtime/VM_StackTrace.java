/*
 * (C) Copyright IBM Corp. 2001
 */
//$Id$
package com.ibm.JikesRVM;

import java.io.PrintStream;
import java.io.PrintWriter;

/**
 * A list of method/instructionOffset pairs that describe the
 * state of the call stack at a particular instant.
 *
 * @author Bowen Alpern
 * @author Derek Lieber
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
     // count number of frames comprising stack
     //
     int stackFrameCount = 0;
     VM.disableGC(); // so fp & ip don't change under our feet
     VM_Address fp = VM_Magic.getFramePointer();
     VM_Address ip = VM_Magic.getReturnAddress(fp);
     fp = VM_Magic.getCallerFramePointer(fp);
     while (VM_Magic.getCallerFramePointer(fp).toInt() != STACKFRAME_SENTINAL_FP) {
       stackFrameCount++;
       int compiledMethodId = VM_Magic.getCompiledMethodID(fp);
       if (compiledMethodId!=INVISIBLE_METHOD_ID) {
	 VM_CompiledMethod compiledMethod = VM_CompiledMethods.getCompiledMethod(compiledMethodId);
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
	 VM_CompiledMethod compiledMethod = VM_CompiledMethods.getCompiledMethod(compiledMethodId);
	 stackTrace[i].compiledMethod = compiledMethod;
	 stackTrace[i].instructionOffset = ip.diff(VM_Magic.objectAsAddress(compiledMethod.getInstructions())).toInt();
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
      
     if (verboseTracePeriod > 0) {
	 if ((verboseTraceIndex++ % verboseTracePeriod) == 0) {
	     VM.disableGC();
	     VM_Scheduler.dumpStack();
	     VM.enableGC();
	 }
     }

     return stackTrace;
   }


  /**
   * Print stack trace.
   * Delegate the actual printing of the stack trace to the VM_CompiledMethod's to
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
	compiledMethod.printStackTrace(stackTrace[i].instructionOffset, out);
      }
    }
  }
   
  /**
   * Print stack trace.
   * Delegate the actual printing of the stack trace to the VM_CompiledMethod's to
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
       if (compiledMethod == null) 
	 out.println("\tat <invisible method>");
       else 
	 compiledMethod.printStackTrace(stackTrace[i].instructionOffset, out);
     }
   }

  //----------------//
  // implementation //
  //----------------//

  static int verboseTracePeriod = 0;
  static int verboseTraceIndex = 0;

  VM_CompiledMethod compiledMethod;
  public int               instructionOffset;
}
