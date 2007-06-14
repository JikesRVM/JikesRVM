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
package java.lang;

import org.jikesrvm.VM;
import org.jikesrvm.runtime.VM_StackTrace;
import org.jikesrvm.scheduler.VM_Thread;
import org.jikesrvm.scheduler.VM_Scheduler;
/**
 * Implementation of string interning for JikesRVM.
 */
public final class VMThrowable {
  /** The stack trace for this throwable */
  private VM_StackTrace stackTrace;
  /** Count of how many out of memory errors have occured */
  private int numOutOfMemoryErrors = 0;
  /** Number of out of memory errors for this  */
  private static final int maxOutOfMemoryErrors = 5;
  /** Proxy VMThrowable used when its not safe to create a proper VMThrowable */
  private static final VMThrowable proxyVMThrowable = new VMThrowable();
  /**
   * Zero length array of stack trace elements, returned when handling an OOM or
   * an unexpected error
   */
  private static final StackTraceElement[] zeroLengthStackTrace = new StackTraceElement[0];

  /**
   * Constructor - used to construct the proxy stack trace that stands in place
   * of stack traces
   */
  private VMThrowable() {
    stackTrace = null;
  }
  /** Constructor called by fillInStackTrace*/
  private VMThrowable(VM_StackTrace stackTrace) {
    this.stackTrace = stackTrace;
  }

  /**
   * Create the VMThrowable
   * @return constructed VMThrowable
   */
  static VMThrowable fillInStackTrace(Throwable parent){
    if (!VM.fullyBooted) {
      VM.sysWriteln("Exception in non-fully booted VM");
      VM_Scheduler.dumpStack();
      return null;
    }
    else if(VM_Thread.getCurrentThread().isGCThread()) {
      VM.sysWriteln("Exception in GC thread");
      VM_Scheduler.dumpStack();
      return null;
    } else if (parent instanceof OutOfMemoryError) {
      return proxyVMThrowable;
    } else {
      try {
        VM_StackTrace stackTrace = new VM_StackTrace(1);
        return new VMThrowable(stackTrace);
      } catch (OutOfMemoryError t) {
        VM.sysWriteln("VMThrowable.fillInStackTrace(): Cannot fill in a stack trace; out of memory!");
        proxyVMThrowable.tallyOutOfMemoryError();
        return null;
      } catch (Throwable t) {
        VM.sysFail("VMThrowable.fillInStackTrace(): Cannot fill in a stack trace; got a weird Throwable when I tried to");
        return null;
      }
    }
  }

  /** Return the stack trace */
  StackTraceElement[] getStackTrace(Throwable parent) {
    if (this == proxyVMThrowable || stackTrace == null) {
      return zeroLengthStackTrace;
    } else if (VM_Thread.getCurrentThread().isGCThread()) {
      VM.sysWriteln("VMThrowable.getStackTrace called from GC thread: dumping stack using scheduler");
      VM_Scheduler.dumpStack();
      return zeroLengthStackTrace;
    } else {
      VM_StackTrace.Element[] vmElements;
      try {
        vmElements = stackTrace.getStackTrace();
      } catch (Throwable t) {
        VM.sysWriteln("Error calling VM_StackTrace.getStackTrace: dumping stack using scheduler");
        VM_Scheduler.dumpStack();
        return zeroLengthStackTrace;
      }
      if (vmElements == null) {
        VM.sysWriteln("Error calling VM_StackTrace.getStackTrace returned null");
        VM_Scheduler.dumpStack();
        return zeroLengthStackTrace;
      }
      if (VM.fullyBooted) {
        try {
          StackTraceElement[] elements = new StackTraceElement[vmElements.length];
          for (int i=0; i < vmElements.length; i++) {
            VM_StackTrace.Element vmElement = vmElements[i];
            String fileName = vmElement.getFileName();
            int lineNumber = vmElement.getLineNumber();
            String className = vmElement.getClassName();
            String methodName = vmElement.getMethodName();
            boolean isNative = vmElement.isNative();
            elements[i] = new StackTraceElement(fileName, lineNumber, className, methodName, isNative);
          }
          return elements;
        } catch (Throwable t) {
          VM.sysWriteln("Error constructing StackTraceElements: dumping stack");
        }
      } else {
        VM.sysWriteln("Dumping stack using sysWrite in not fullyBooted VM");
      }
      for (VM_StackTrace.Element vmElement : vmElements) {
        if (vmElement == null) {
          VM.sysWriteln("Error stack trace with null entry");
          VM_Scheduler.dumpStack();
          return zeroLengthStackTrace;
        }
        String fileName = vmElement.getFileName();
        int lineNumber = vmElement.getLineNumber();
        String className = vmElement.getClassName();
        String methodName = vmElement.getMethodName();
        VM.sysWrite("   at ");
        if (className != null) {
          VM.sysWrite(className);
          VM.sysWrite(".");
        }
        VM.sysWrite(methodName);
        if (fileName != null) {
          VM.sysWrite("(");
          VM.sysWrite(fileName);
          if (lineNumber > 0) {
            VM.sysWrite(":");
            VM.sysWrite(vmElement.getLineNumber());
          }
          VM.sysWrite(")");
        }
        VM.sysWriteln();
      }
      return zeroLengthStackTrace;
    }
  }
  /**
   * Did an out of memory error occur during the creation of the throwable? Only
   * allow so many before terminating the VM, to avoid infinite recursion
   */
  public void tallyOutOfMemoryError() {
    if (++numOutOfMemoryErrors >= maxOutOfMemoryErrors) {
      // We exit before printing, in case we're in some weird hell where
      // everything is broken, even VM.sysWriteln()..
      VM.sysExit(VM.EXIT_STATUS_TOO_MANY_OUT_OF_MEMORY_ERRORS);
    }
    if (numOutOfMemoryErrors > 1 ) {
      VM.sysWriteln("GC Warning: I'm now ", numOutOfMemoryErrors, " levels deep in OutOfMemoryErrors while handling this Throwable");
    }
  }
}
