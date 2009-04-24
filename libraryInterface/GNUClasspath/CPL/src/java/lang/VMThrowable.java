/*
 *  This file is part of the Jikes RVM project (http://jikesrvm.org).
 *
 *  This file is licensed to You under the Eclipse Public License (EPL);
 *  You may not use this file except in compliance with the License. You
 *  may obtain a copy of the License at
 *
 *      http://www.opensource.org/licenses/eclipse-1.0.php
 *
 *  See the COPYRIGHT.txt file distributed with this work for information
 *  regarding copyright ownership.
 */
package java.lang;

import org.jikesrvm.VM;
import org.jikesrvm.runtime.StackTrace;
import org.jikesrvm.scheduler.RVMThread;
/**
 * Implementation of throwables for JikesRVM.
 */
public final class VMThrowable {
  /** The stack trace for this throwable */
  private StackTrace stackTrace;

  /**
   * Zero length array of stack trace elements, returned when handling an OOM or
   * an unexpected error
   */
  private static final StackTraceElement[] zeroLengthStackTrace = new StackTraceElement[0];

  /** Constructor called by fillInStackTrace*/
  private VMThrowable(StackTrace stackTrace) {
    this.stackTrace = stackTrace;
  }

  /**
   * Create the VMThrowable
   * @return constructed VMThrowable
   */
  static VMThrowable fillInStackTrace(Throwable parent){
    if (!VM.fullyBooted) {
      return null;
    } else if (RVMThread.getCurrentThread().getThreadForStackTrace().isGCThread()) {
      VM.sysWriteln("Exception in GC thread");
      RVMThread.dumpVirtualMachine();
      return null;
    }
    try {
      StackTrace stackTrace = new StackTrace();
      return new VMThrowable(stackTrace);
    } catch (OutOfMemoryError oome) {
      return null;
    } catch (Throwable t) {
      VM.sysFail("VMThrowable.fillInStackTrace(): Cannot fill in a stack trace; got a weird Throwable when I tried to");
      return null;
    }
  }

  /** Return the stack trace */
  StackTraceElement[] getStackTrace(Throwable parent) {
    if (stackTrace == null) {
      return zeroLengthStackTrace;
    } else if (RVMThread.getCurrentThread().getThreadForStackTrace().isGCThread()) {
      VM.sysWriteln("VMThrowable.getStackTrace called from GC thread: dumping stack using scheduler");
      RVMThread.dumpStack();
      return zeroLengthStackTrace;
    }

    StackTrace.Element[] vmElements;
    try {
      vmElements = stackTrace.getStackTrace(parent);
    } catch (Throwable t) {
      VM.sysWriteln("Error calling StackTrace.getStackTrace: dumping stack using scheduler");
      RVMThread.dumpStack();
      return zeroLengthStackTrace;
    }
    if (vmElements == null) {
      VM.sysWriteln("Error calling StackTrace.getStackTrace returned null");
      RVMThread.dumpStack();
      return zeroLengthStackTrace;
    }
    if (VM.fullyBooted) {
      try {
        StackTraceElement[] elements = new StackTraceElement[vmElements.length];
        for (int i=0; i < vmElements.length; i++) {
          StackTrace.Element vmElement = vmElements[i];
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
    for (StackTrace.Element vmElement : vmElements) {
      if (vmElement == null) {
        VM.sysWriteln("Error stack trace with null entry");
        RVMThread.dumpStack();
        return zeroLengthStackTrace;
      }
      String fileName = vmElement.getFileName();
      int lineNumber = vmElement.getLineNumber();
      String className = vmElement.getClassName();
      String methodName = vmElement.getMethodName();
      VM.sysWrite("   at ");
      if (className != "") {
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
