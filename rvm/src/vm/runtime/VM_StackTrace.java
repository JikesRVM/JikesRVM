/*
 * (C) Copyright IBM Corp. 2001
 */
//$Id$
package com.ibm.JikesRVM;

import java.io.PrintStream;
import java.io.PrintWriter;

/**
 * A list of compiled method and instructionOffset pairs that describe 
 * the state of the call stack at a particular instant.
 *
 * @author Bowen Alpern
 * @author Dave Grove
 * @author Derek Lieber
 */
public class VM_StackTrace implements VM_Constants {

  static int verboseTracePeriod = 0;
  static int verboseTraceIndex = 0;

  /**
   * The compiled methods that comprise the trace
   */
  private VM_CompiledMethod[] compiledMethods;

  /**
   * The instruction offsets within those methods.
   */
  private VM_OffsetArray offsets;
  
  /**
   * Create a trace of the current call stack
   */
  public VM_StackTrace(int skip) {
    // (1) Count the number of frames compirsing the stack.
    int numFrames = walkFrames(false, skip+1);
    compiledMethods = new VM_CompiledMethod[numFrames];
    offsets = VM_OffsetArray.create(numFrames);
    walkFrames(true, skip+1);
    
    if (verboseTracePeriod > 0) {
      if ((verboseTraceIndex++ % verboseTracePeriod) == 0) {
	VM.disableGC();
	VM_Scheduler.dumpStack();
	VM.enableGC();
      }
    }
  }

  private int walkFrames(boolean record, int skip) {
    int stackFrameCount = 0;
    VM.disableGC(); // so fp & ip don't change under our feet
    VM_Address fp = VM_Magic.getFramePointer();
    VM_Address ip = VM_Magic.getReturnAddress(fp);
    for (int i=0; i<skip; i++) {
      fp = VM_Magic.getCallerFramePointer(fp);
      ip = VM_Magic.getReturnAddress(fp);
    }
    fp = VM_Magic.getCallerFramePointer(fp);
    while (VM_Magic.getCallerFramePointer(fp).NE(STACKFRAME_SENTINAL_FP)) {
      int compiledMethodId = VM_Magic.getCompiledMethodID(fp);
      if (compiledMethodId != INVISIBLE_METHOD_ID) {
	VM_CompiledMethod compiledMethod = VM_CompiledMethods.getCompiledMethod(compiledMethodId);
	if (record) compiledMethods[stackFrameCount] = compiledMethod;
	if (compiledMethod.getCompilerType() != VM_CompiledMethod.TRAP) {
	  if (record) {
	    VM_Address start = VM_Magic.objectAsAddress(compiledMethod.getInstructions());
	    offsets.set(stackFrameCount, ip.diff(start));
	  }
	  if (compiledMethod.getMethod().getDeclaringClass().isBridgeFromNative()) {
	    // skip native frames, stopping at last native frame preceeding the
	    // Java To C transition frame
	    fp = VM_Runtime.unwindNativeStackFrame(fp);	 
	  }
	} 
      }
      stackFrameCount++;
      ip = VM_Magic.getReturnAddress(fp);
      fp = VM_Magic.getCallerFramePointer(fp);
    }
    VM.enableGC();
    return stackFrameCount;
  }
  

  /**
   * Print stack trace.
   * Delegate the actual printing of the stack trace to the VM_CompiledMethod's to
   * deal with inlining by the opt compiler in a sensible fashion.
   * 
   * @param stackTrace stack trace to be printed
   * @param out        stream to print on
   */
  public void print(PrintStream out) {
    for (int i = 0, n = compiledMethods.length; i < n; i++) {
      if (i == 50) { 
	// large stack - suppress excessive output
	int oldIndex = i;
	int newIndex = n - 10;
	if (newIndex > oldIndex) {
	  i = newIndex;
	  out.println("\t..." + (newIndex - oldIndex) + " stackframes omitted...");
	}
      }
      VM_CompiledMethod cm = compiledMethods[i];
      if (cm == null) {
	out.println("\tat <invisible method>");
      } else {
	cm.printStackTrace(offsets.get(i), out);
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
  public void print(PrintWriter out) {
    for (int i = 0, n = compiledMethods.length; i < n; ++i) {
      if (i == 50) { // large stack - suppress excessive output
	int oldIndex = i;
	int newIndex = n - 10;
	if (newIndex > oldIndex) {
	  i = newIndex;
	  out.println("\t..." + (newIndex - oldIndex) + " stackframes omitted...");
	}
      }

      VM_CompiledMethod cm = compiledMethods[i];
      if (cm == null) {
	out.println("\tat <invisible method>");
      } else {
	cm.printStackTrace(offsets.get(i), out);
      }
    }
  }
}
