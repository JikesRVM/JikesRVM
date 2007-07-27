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
package org.jikesrvm.runtime;

import static org.jikesrvm.ArchitectureSpecific.VM_StackframeLayoutConstants.INVISIBLE_METHOD_ID;
import static org.jikesrvm.ArchitectureSpecific.VM_StackframeLayoutConstants.STACKFRAME_SENTINEL_FP;
import org.jikesrvm.VM;
import org.jikesrvm.VM_Options;
import org.jikesrvm.classloader.VM_Atom;
import org.jikesrvm.classloader.VM_MemberReference;
import org.jikesrvm.classloader.VM_Method;
import org.jikesrvm.classloader.VM_NormalMethod;
import org.jikesrvm.compilers.common.VM_CompiledMethod;
import org.jikesrvm.compilers.common.VM_CompiledMethods;
import org.jikesrvm.compilers.opt.VM_OptCompiledMethod;
import org.jikesrvm.compilers.opt.VM_OptEncodedCallSiteTree;
import org.jikesrvm.compilers.opt.VM_OptMachineCodeMap;
import org.jikesrvm.scheduler.VM_Scheduler;
import org.jikesrvm.scheduler.VM_Thread;
import org.vmmagic.unboxed.Address;
import org.vmmagic.unboxed.Offset;

/**
 * A list of compiled method and instructionOffset pairs that describe the state
 * of the call stack at a particular instant.
 */
public class VM_StackTrace {
  /**
   * The compiled methods of the stack trace. Ordered with the top of the stack at
   * 0 and the bottom of the stack at the end of the array
   */
  private final VM_CompiledMethod[] compiledMethods;

  /** The offset of the instruction within the compiled method */
  private final int[] instructionOffsets;

  /** Index of the last stack trace */
  private static int lastTraceIndex = 0;

  /** Index of this stack trace */
  private final int traceIndex;

  /** Should this be (or is this) a verbose stack trace? */
  private boolean isVerbose() {
    // If we're printing verbose stack traces...
    // AND this particular trace meets the periodicity requirements
    return (VM.VerboseStackTracePeriod > 0) &&
    (((traceIndex - 1) % VM.VerboseStackTracePeriod) == 0);

  }

  /**
   * Create a trace of the current call stack
   */
  public VM_StackTrace() {
    // Poor man's atomic integer, to get through bootstrap
    synchronized(VM_StackTrace.class) {
      lastTraceIndex++;
      traceIndex = lastTraceIndex;
    }
    // (1) Count the number of frames comprising the stack.
    int numFrames = walkFrames(false);
    // (2) Construct arrays to hold raw data
    compiledMethods = new VM_CompiledMethod[numFrames];
    instructionOffsets = new int[numFrames];
    // (3) Fill in arrays
    walkFrames(true);
    // Debugging trick: print every nth stack trace created
    if (isVerbose()) {
      VM.disableGC();
      VM.sysWriteln("[ BEGIN Verbosely dumping stack at time of creating VM_StackTrace # ", traceIndex);
      VM_Scheduler.dumpStack();
      VM.sysWriteln("END Verbosely dumping stack at time of creating VM_StackTrace # ", traceIndex, " ]");
      VM.enableGC();
    }
  }

  /**
   * Walk the stack counting the number of stack frames encountered
   * @param record fill in the compiledMethods and instructionOffsets arrays?
   * @return number of stack frames encountered
   */
  private int walkFrames(boolean record) {
    int stackFrameCount = 0;
    VM.disableGC(); // so fp & ip don't change under our feet
    VM_Thread stackTraceThread = VM_Scheduler.getCurrentThread().getThreadForStackTrace();
    Address fp;
    Address ip;
    if (stackTraceThread != VM_Scheduler.getCurrentThread()) {
      /* Stack trace for a sleeping thread */
      fp = stackTraceThread.contextRegisters.getInnermostFramePointer();
      ip = stackTraceThread.contextRegisters.getInnermostInstructionAddress();
    } else {
      /* Stack trace for the current thread */
      fp = VM_Magic.getFramePointer();
      ip = VM_Magic.getReturnAddress(fp);
      fp = VM_Magic.getCallerFramePointer(fp);
    }
    while (VM_Magic.getCallerFramePointer(fp).NE(STACKFRAME_SENTINEL_FP)) {
      int compiledMethodId = VM_Magic.getCompiledMethodID(fp);
      if (compiledMethodId != INVISIBLE_METHOD_ID) {
        VM_CompiledMethod compiledMethod =
          VM_CompiledMethods.getCompiledMethod(compiledMethodId);
        if (record) {
          compiledMethods[stackFrameCount] = compiledMethod;
        }
        if (compiledMethod.getCompilerType() != VM_CompiledMethod.TRAP) {
          if (record) {
            instructionOffsets[stackFrameCount] =
              compiledMethod.getInstructionOffset(ip).toInt();
          }
          if (compiledMethod.getMethod().getDeclaringClass()
              .hasBridgeFromNativeAnnotation()) {
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

  /** Class to wrap up a stack frame element */
  public static class Element {
    /** Stack trace's method, null => invisible or trap */
    private final VM_Method method;
    /** Line number of element */
    private final int lineNumber;
    /** Is this an invisible method? */
    private final boolean isInvisible;
    /** Is this a hardware trap method? */
    private final boolean isTrap;
    /** Constructor for non-opt compiled methods */
    Element(VM_CompiledMethod cm, int off) {
      isInvisible = (cm == null);
      if (!isInvisible) {
        isTrap = cm.getCompilerType() == VM_CompiledMethod.TRAP;
        if (!isTrap) {
          method = cm.getMethod();
          lineNumber = cm.findLineNumberForInstruction(Offset.fromIntSignExtend(off));
        } else {
          method = null;
          lineNumber = 0;
        }
      } else {
        isTrap = false;
        method = null;
        lineNumber = 0;
      }
    }
    /** Constructor for opt compiled methods */
    Element(VM_Method method, int ln) {
      this.method = method;
      lineNumber = ln;
      isTrap = false;
      isInvisible = false;
    }
    /** Get source file name */
    public String getFileName() {
      if (isInvisible || isTrap) {
        return null;
      } else {
        VM_Atom fn = method.getDeclaringClass().getSourceName();
        return (fn != null)  ? fn.toString() : null;
      }
    }
    /** Get class name */
    public String getClassName() {
      if (isInvisible || isTrap) {
        return null;
      } else {
        return method.getDeclaringClass().toString();
      }
    }
    /** Get method name */
    public String getMethodName() {
      if (isInvisible) {
        return "<invisible method>";
      } else if (isTrap) {
        return "<hardware trap>";
      } else {
        return method.getName().toString();
      }
    }
    /** Get line number */
    public int getLineNumber() {
      return lineNumber;
    }
    public boolean isNative() {
      if (isInvisible || isTrap) {
        return false;
      } else {
        return method.isNative();
      }
    }
  }

  /** Return the stack trace for use by the Throwable API */
  public Element[] getStackTrace(Throwable cause) {
    int first = firstRealMethod(cause);
    int last = lastRealMethod(first);
    Element[] elements = new Element[countFrames(first, last)];
    if (!VM.BuildForOptCompiler) {
      int element = 0;
      for (int i=first; i <= last; i++) {
        elements[element] = new Element(compiledMethods[i], instructionOffsets[i]);
        element++;
      }
    } else {
      int element = 0;
      for (int i=first; i <= last; i++) {
        VM_CompiledMethod compiledMethod = compiledMethods[i];
        if ((compiledMethod == null) ||
            (compiledMethod.getCompilerType() != VM_CompiledMethod.OPT)) {
          // Invisible or non-opt compiled method
          elements[element] = new Element(compiledMethod, instructionOffsets[i]);
          element++;
        } else {
          Offset instructionOffset = Offset.fromIntSignExtend(instructionOffsets[i]);
          VM_OptCompiledMethod optInfo = (VM_OptCompiledMethod)compiledMethod;
          VM_OptMachineCodeMap map = optInfo.getMCMap();
          int iei = map.getInlineEncodingForMCOffset(instructionOffset);
          if (iei < 0) {
            elements[element] = new Element(compiledMethod, instructionOffsets[i]);
            element++;
          } else {
            int[] inlineEncoding = map.inlineEncoding;
            int bci = map.getBytecodeIndexForMCOffset(instructionOffset);
            for (; iei >= 0; iei = VM_OptEncodedCallSiteTree.getParent(iei, inlineEncoding)) {
              int mid = VM_OptEncodedCallSiteTree.getMethodID(iei, inlineEncoding);
              VM_Method method = VM_MemberReference.getMemberRef(mid).asMethodReference().getResolvedMember();
              int lineNumber = ((VM_NormalMethod)method).getLineNumberForBCIndex(bci);
              elements[element] = new Element(method, lineNumber);
              element++;
            }
          }
        }
      }
    }
    return elements;
  }

  /**
   * Count number of stack frames including those inlined by the opt compiler
   * @param first the first compiled method to look from
   * @param last the last compiled method to look to
   */
  private int countFrames(int first, int last) {
    int numElements=0;
    if (!VM.BuildForOptCompiler) {
      numElements = last - first + 1;
    } else {
      for (int i=first; i <= last; i++) {
        VM_CompiledMethod compiledMethod = compiledMethods[i];
        if ((compiledMethod == null) ||
            (compiledMethod.getCompilerType() != VM_CompiledMethod.OPT)) {
          // Invisible or non-opt compiled method
          numElements++;
        } else {
          Offset instructionOffset = Offset.fromIntSignExtend(instructionOffsets[i]);
          VM_OptCompiledMethod optInfo = (VM_OptCompiledMethod)compiledMethod;
          VM_OptMachineCodeMap map = optInfo.getMCMap();
          int iei = map.getInlineEncodingForMCOffset(instructionOffset);
          if (iei < 0) {
            numElements++;
          } else {
            int[] inlineEncoding = map.inlineEncoding;
            for (; iei >= 0; iei = VM_OptEncodedCallSiteTree.getParent(iei, inlineEncoding)) {
              numElements++;
            }
          }
        }
      }
    }
    return numElements;
  }
  /**
   * Find the first non-VM method/exception initializer method in the stack
   * trace. As we're working with the compiled methods we're assumig the
   * constructor of the exception won't have been inlined into the throwing
   * method.
   *
   * @param cause the cause of generating the stack trace marking the end of the
   *          frames to elide
   * @return the index of the method throwing the exception or else 0
   */
  private int firstRealMethod(Throwable cause) {
    /* We expect a hardware trap to look like:
     * at org.jikesrvm.runtime.VM_StackTrace.<init>(VM_StackTrace.java:78)
     * at java.lang.VMThrowable.fillInStackTrace(VMThrowable.java:67)
     * at java.lang.Throwable.fillInStackTrace(Throwable.java:498)
     * at java.lang.Throwable.<init>(Throwable.java:159)
     * at java.lang.Throwable.<init>(Throwable.java:147)
     * at java.lang.Exception.<init>(Exception.java:66)
     * at java.lang.RuntimeException.<init>(RuntimeException.java:64)
     * at java.lang.NullPointerException.<init>(NullPointerException.java:69)
     * at org.jikesrvm.runtime.VM_Runtime.deliverHardwareException(VM_Runtime.java:682)
     * at <hardware trap>(Unknown Source:0)
     *
     * and a software trap to look like:
     * at org.jikesrvm.runtime.VM_StackTrace.<init>(VM_StackTrace.java:78)
     * at java.lang.VMThrowable.fillInStackTrace(VMThrowable.java:67)
     * at java.lang.Throwable.fillInStackTrace(Throwable.java:498)
     * at java.lang.Throwable.<init>(Throwable.java:159)
     * at java.lang.Error.<init>(Error.java:81)
     * at java.lang.LinkageError.<init>(LinkageError.java:72)
     * at java.lang.ExceptionInInitializerError.<init>(ExceptionInInitializerError.java:85)
     * at java.lang.ExceptionInInitializerError.<init>(ExceptionInInitializerError.java:75)
     *
     * and an OutOfMemoryError to look like:
     * at org.jikesrvm.scheduler.VM_Processor.dispatch(VM_Processor.java:211)
     * at org.jikesrvm.scheduler.VM_Thread.morph(VM_Thread.java:1125)
     * ...
     * at org.jikesrvm.memorymanagers.mminterface.MM_Interface.allocateSpace(MM_Interface.java:613)
     * ...
     * at org.jikesrvm.runtime.VM_Runtime.unresolvedNewArray(VM_Runtime.java:401)
     */
    if (VM_Options.stackTraceFull) {
      return 0;
    } else {
      int element = 0;
      // Deal with OutOfMemoryError
      if (cause instanceof OutOfMemoryError) {
        // (1) search until VM_Runtime
        while((element < compiledMethods.length) &&
            (compiledMethods[element] != null) &&
            compiledMethods[element].method.getDeclaringClass().getClassForType() != VM_Runtime.class) {
          element++;
        }
        // (2) continue until not VM_Runtime
        while((element < compiledMethods.length) &&
            (compiledMethods[element] != null) &&
            compiledMethods[element].method.getDeclaringClass().getClassForType() == VM_Runtime.class) {
          element++;
        }
        return element;
      }

      // (1) remove any VM_StackTrace frames
      while((element < compiledMethods.length) &&
          (compiledMethods[element] != null) &&
          compiledMethods[element].method.getDeclaringClass().getClassForType() == VM_StackTrace.class) {
        element++;
      }
      // (2) remove any VMThrowable frames
      while((element < compiledMethods.length) &&
          (compiledMethods[element] != null) &&
          compiledMethods[element].method.getDeclaringClass().getClassForType() == java.lang.VMThrowable.class) {
        element++;
      }
      // (3) remove any Throwable frames
      while((element < compiledMethods.length) &&
          (compiledMethods[element] != null) &&
          compiledMethods[element].method.getDeclaringClass().getClassForType() == java.lang.Throwable.class) {
        element++;
      }
      // (4) remove frames belonging to exception constructors upto the causes constructor
      while((element < compiledMethods.length) &&
          (compiledMethods[element] != null) &&
          (compiledMethods[element].method.getDeclaringClass().getClassForType() != cause.getClass()) &&
          compiledMethods[element].method.isObjectInitializer() &&
          compiledMethods[element].method.getDeclaringClass().isThrowable()) {
        element++;
      }
      // (5) remove frames belonging to the causes constructor
      // NB This can be made to incorrectly elide frames if the cause
      // exception is thrown from a constructor of the cause exception, however,
      // Sun's VM has the same problem
      while((element < compiledMethods.length) &&
          (compiledMethods[element] != null) &&
          (compiledMethods[element].method.getDeclaringClass().getClassForType() == cause.getClass()) &&
          compiledMethods[element].method.isObjectInitializer()) {
        element++;
      }
      // (6) remove possible hardware exception deliverer frames
      if (element < compiledMethods.length - 2) {
        if ((compiledMethods[element+1] != null) &&
            compiledMethods[element+1].getCompilerType() == VM_CompiledMethod.TRAP) {
          element+=2;
        }
      }
      return element;
    }
  }
  /**
   * Find the first non-VM method at the end of the stack trace
   * @param first the first real method of the stack trace
   * @return compiledMethods.length-1 if no non-VM methods found else the index of
   *         the method
   */
  private int lastRealMethod(int first) {
    /* We expect an exception on the main thread to look like:
     * at <invisible method>(Unknown Source:0)
     * at org.jikesrvm.runtime.VM_Reflection.invoke(VM_Reflection.java:132)
     * at org.jikesrvm.scheduler.VM_MainThread.run(VM_MainThread.java:195)
     * at org.jikesrvm.scheduler.VM_Thread.run(VM_Thread.java:534)
     * at org.jikesrvm.scheduler.VM_Thread.startoff(VM_Thread.java:1113
     *
     * and on another thread to look like:
     * at org.jikesrvm.scheduler.VM_Thread.run(VM_Thread.java:534)
     * at org.jikesrvm.scheduler.VM_Thread.startoff(VM_Thread.java:1113)
     */
    int max = compiledMethods.length-1;
    if (VM_Options.stackTraceFull) {
      return max;
    } else {
      // Start at end of array and elide a frame unless we find a place to stop
      for (int i=max; i >= first; i--) {
        if (compiledMethods[i] == null) {
          // we found an invisible method, assume next method if this is sane
          if (i-1 >= 0) {
            return i-1;
          } else {
            return max; // not sane => return max
          }
        }
        if (compiledMethods[i].getCompilerType() == VM_CompiledMethod.TRAP) {
          // looks like we've gone too low
          return max;
        }
        Class<?> frameClass = compiledMethods[i].method.getDeclaringClass().getClassForType();
        if ((frameClass != org.jikesrvm.scheduler.VM_MainThread.class) &&
            (frameClass != org.jikesrvm.scheduler.VM_Thread.class) &&
            (frameClass != org.jikesrvm.runtime.VM_Reflection.class)){
          // Found a non-VM method
          return i;
        }
      }
      // No frame found
      return max;
    }
  }
}
