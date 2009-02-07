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

import static org.jikesrvm.ArchitectureSpecific.StackframeLayoutConstants.INVISIBLE_METHOD_ID;
import static org.jikesrvm.ArchitectureSpecific.StackframeLayoutConstants.STACKFRAME_SENTINEL_FP;

import org.jikesrvm.VM;
import org.jikesrvm.Options;
import org.jikesrvm.classloader.Atom;
import org.jikesrvm.classloader.MemberReference;
import org.jikesrvm.classloader.RVMMethod;
import org.jikesrvm.classloader.NormalMethod;
import org.jikesrvm.compilers.common.CompiledMethod;
import org.jikesrvm.compilers.common.CompiledMethods;
import org.jikesrvm.compilers.opt.runtimesupport.OptCompiledMethod;
import org.jikesrvm.compilers.opt.runtimesupport.OptEncodedCallSiteTree;
import org.jikesrvm.compilers.opt.runtimesupport.OptMachineCodeMap;
import org.jikesrvm.scheduler.RVMThread;
import org.vmmagic.pragma.Uninterruptible;
import org.vmmagic.pragma.NoInline;
import org.vmmagic.unboxed.Address;
import org.vmmagic.unboxed.Offset;

/**
 * A list of compiled method and instructionOffset pairs that describe the state
 * of the call stack at a particular instant.
 */
public class StackTrace {
  /**
   * The compiled method ids of the stack trace. Ordered with the top of the stack at
   * 0 and the bottom of the stack at the end of the array
   */
  private final int[] compiledMethods;

  /** The offset of the instruction within the compiled method */
  private final int[] instructionOffsets;

  /** Index of the last stack trace; only used to support VM.VerboseStackTracePeriod */
  private static int lastTraceIndex = 0;

  /**
   * Create a trace for the call stack of RVMThread.getThreadForStackTrace
   * (normally the current thread unless we're in GC)
   */
  @NoInline
  public StackTrace() {
    boolean isVerbose = false;
    int traceIndex = 0;
    if (VM.VerifyAssertions && VM.VerboseStackTracePeriod > 0) {
      // Poor man's atomic integer, to get through bootstrap
      synchronized(StackTrace.class) {
         traceIndex = lastTraceIndex++;
      }
      isVerbose = (traceIndex % VM.VerboseStackTracePeriod == 0);
    }
    RVMThread stackTraceThread = RVMThread.getCurrentThread().getThreadForStackTrace();
    if (stackTraceThread != RVMThread.getCurrentThread()) {
      // (1) Count the number of frames comprising the stack.
      int numFrames = countFramesNoGC(stackTraceThread);
      // (2) Construct arrays to hold raw data
      compiledMethods = new int[numFrames];
      instructionOffsets = new int[numFrames];
      // (3) Fill in arrays
      recordFramesNoGC(stackTraceThread);
    } else {
      // (1) Count the number of frames comprising the stack.
      int numFrames = countFramesUninterruptible(stackTraceThread);
      // (2) Construct arrays to hold raw data
      compiledMethods = new int[numFrames];
      instructionOffsets = new int[numFrames];
      // (3) Fill in arrays
      recordFramesUninterruptible(stackTraceThread);
    }
    // Debugging trick: print every nth stack trace created
    if (isVerbose) {
      VM.disableGC();
      VM.sysWriteln("[ BEGIN Verbosely dumping stack at time of creating StackTrace # ", traceIndex);
      RVMThread.dumpStack();
      VM.sysWriteln("END Verbosely dumping stack at time of creating StackTrace # ", traceIndex, " ]");
      VM.enableGC();
    }
  }

  /**
   * Walk the stack counting the number of stack frames encountered.
   * The stack being walked isn't our stack so GC must be disabled.
   * @return number of stack frames encountered
   */
  private int countFramesNoGC(RVMThread stackTraceThread) {
    int stackFrameCount = 0;
    VM.disableGC(); // so fp & ip don't change under our feet
    Address fp;
    Address ip;
    /* Stack trace for a sleeping thread */
    fp = stackTraceThread.contextRegisters.getInnermostFramePointer();
    ip = stackTraceThread.contextRegisters.getInnermostInstructionAddress();
    while (Magic.getCallerFramePointer(fp).NE(STACKFRAME_SENTINEL_FP)) {
      int compiledMethodId = Magic.getCompiledMethodID(fp);
      if (compiledMethodId != INVISIBLE_METHOD_ID) {
        CompiledMethod compiledMethod =
          CompiledMethods.getCompiledMethod(compiledMethodId);
        if ((compiledMethod.getCompilerType() != CompiledMethod.TRAP) &&
            compiledMethod.hasBridgeFromNativeAnnotation()) {
          // skip native frames, stopping at last native frame preceeding the
          // Java To C transition frame
          fp = RuntimeEntrypoints.unwindNativeStackFrame(fp);
        }
      }
      stackFrameCount++;
      ip = Magic.getReturnAddress(fp);
      fp = Magic.getCallerFramePointer(fp);
    }
    VM.enableGC();
    return stackFrameCount;
  }

  /**
   * Walk the stack recording the stack frames encountered.
   * The stack being walked isn't our stack so GC must be disabled.
   */
  private void recordFramesNoGC(RVMThread stackTraceThread) {
    int stackFrameCount = 0;
    VM.disableGC(); // so fp & ip don't change under our feet
    Address fp;
    Address ip;
    /* Stack trace for a sleeping thread */
    fp = stackTraceThread.contextRegisters.getInnermostFramePointer();
    ip = stackTraceThread.contextRegisters.getInnermostInstructionAddress();
    while (Magic.getCallerFramePointer(fp).NE(STACKFRAME_SENTINEL_FP)) {
      int compiledMethodId = Magic.getCompiledMethodID(fp);
      compiledMethods[stackFrameCount] = compiledMethodId;
      if (compiledMethodId != INVISIBLE_METHOD_ID) {
        CompiledMethod compiledMethod =
          CompiledMethods.getCompiledMethod(compiledMethodId);
        if (compiledMethod.getCompilerType() != CompiledMethod.TRAP) {
          instructionOffsets[stackFrameCount] =
            compiledMethod.getInstructionOffset(ip).toInt();
          if (compiledMethod.hasBridgeFromNativeAnnotation()) {
            // skip native frames, stopping at last native frame preceeding the
            // Java To C transition frame
            fp = RuntimeEntrypoints.unwindNativeStackFrame(fp);
          }
        }
      }
      stackFrameCount++;
      ip = Magic.getReturnAddress(fp);
      fp = Magic.getCallerFramePointer(fp);
    }
    VM.enableGC();
  }

  /**
   * Walk the stack counting the number of stack frames encountered.
   * The stack being walked is our stack, so code is Uninterrupible to stop the
   * stack moving.
   * @return number of stack frames encountered
   */
  @Uninterruptible
  @NoInline
  private int countFramesUninterruptible(RVMThread stackTraceThread) {
    int stackFrameCount = 0;
    Address fp;
    Address ip;
    /* Stack trace for the current thread */
    fp = Magic.getFramePointer();
    ip = Magic.getReturnAddress(fp);
    fp = Magic.getCallerFramePointer(fp);
    while (Magic.getCallerFramePointer(fp).NE(STACKFRAME_SENTINEL_FP)) {
      int compiledMethodId = Magic.getCompiledMethodID(fp);
      if (compiledMethodId != INVISIBLE_METHOD_ID) {
        CompiledMethod compiledMethod =
          CompiledMethods.getCompiledMethod(compiledMethodId);
        if ((compiledMethod.getCompilerType() != CompiledMethod.TRAP) &&
            compiledMethod.hasBridgeFromNativeAnnotation()) {
          // skip native frames, stopping at last native frame preceeding the
          // Java To C transition frame
          fp = RuntimeEntrypoints.unwindNativeStackFrame(fp);
        }
      }
      stackFrameCount++;
      ip = Magic.getReturnAddress(fp);
      fp = Magic.getCallerFramePointer(fp);
    }
    //VM.sysWriteln("stack frame count = ",stackFrameCount);
    return stackFrameCount;
  }

  /**
   * Walk the stack recording the stack frames encountered
   * The stack being walked is our stack, so code is Uninterrupible to stop the
   * stack moving.
   */
  @Uninterruptible
  @NoInline
  private void recordFramesUninterruptible(RVMThread stackTraceThread) {
    int stackFrameCount = 0;
    Address fp;
    Address ip;
    /* Stack trace for the current thread */
    fp = Magic.getFramePointer();
    ip = Magic.getReturnAddress(fp);
    fp = Magic.getCallerFramePointer(fp);
    while (Magic.getCallerFramePointer(fp).NE(STACKFRAME_SENTINEL_FP)) {
      //VM.sysWriteln("at stackFrameCount = ",stackFrameCount);
      int compiledMethodId = Magic.getCompiledMethodID(fp);
      compiledMethods[stackFrameCount] = compiledMethodId;
      if (compiledMethodId != INVISIBLE_METHOD_ID) {
        CompiledMethod compiledMethod =
          CompiledMethods.getCompiledMethod(compiledMethodId);
        if (compiledMethod.getCompilerType() != CompiledMethod.TRAP) {
          instructionOffsets[stackFrameCount] =
            compiledMethod.getInstructionOffset(ip).toInt();
          if (compiledMethod.hasBridgeFromNativeAnnotation()) {
            //VM.sysWriteln("native!");
            // skip native frames, stopping at last native frame preceeding the
            // Java To C transition frame
            fp = RuntimeEntrypoints.unwindNativeStackFrame(fp);
          }
        } else {
          //VM.sysWriteln("trap!");
        }
      } else {
        //VM.sysWriteln("invisible method!");
      }
      stackFrameCount++;
      ip = Magic.getReturnAddress(fp);
      fp = Magic.getCallerFramePointer(fp);
    }
  }

  /** Class to wrap up a stack frame element */
  public static class Element {
    /** Stack trace's method, null => invisible or trap */
    private final RVMMethod method;
    /** Line number of element */
    private final int lineNumber;
    /** Is this an invisible method? */
    private final boolean isInvisible;
    /** Is this a hardware trap method? */
    private final boolean isTrap;
    /** Constructor for non-opt compiled methods */
    Element(CompiledMethod cm, int off) {
      isInvisible = (cm == null);
      if (!isInvisible) {
        isTrap = cm.getCompilerType() == CompiledMethod.TRAP;
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
    Element(RVMMethod method, int ln) {
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
        Atom fn = method.getDeclaringClass().getSourceName();
        return (fn != null)  ? fn.toString() : null;
      }
    }
    /** Get class name */
    public String getClassName() {
      if (isInvisible || isTrap) {
        return "";
      } else {
        return method.getDeclaringClass().toString();
      }
    }
    /** Get class */
    public Class<?> getElementClass() {
      if (isInvisible || isTrap) {
        return null;
      }
      return method.getDeclaringClass().getClassForType();
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

  /**
   * Get the compiled method at element
   */
  private CompiledMethod getCompiledMethod(int element) {
    if ((element >= 0) && (element < compiledMethods.length)) {
      int mid = compiledMethods[element];
      if (mid != INVISIBLE_METHOD_ID) {
        return CompiledMethods.getCompiledMethod(mid);
      }
    }
    return null;
  }

  /** Return the stack trace for use by the Throwable API */
  public Element[] getStackTrace(Throwable cause) {
    int first = firstRealMethod(cause);
    int last = lastRealMethod(first);
    Element[] elements = new Element[countFrames(first, last)];
    if (!VM.BuildForOptCompiler) {
      int element = 0;
      for (int i=first; i <= last; i++) {
        elements[element] = new Element(getCompiledMethod(i), instructionOffsets[i]);
        element++;
      }
    } else {
      int element = 0;
      for (int i=first; i <= last; i++) {
        CompiledMethod compiledMethod = getCompiledMethod(i);
        if ((compiledMethod == null) ||
            (compiledMethod.getCompilerType() != CompiledMethod.OPT)) {
          // Invisible or non-opt compiled method
          elements[element] = new Element(compiledMethod, instructionOffsets[i]);
          element++;
        } else {
          Offset instructionOffset = Offset.fromIntSignExtend(instructionOffsets[i]);
          OptCompiledMethod optInfo = (OptCompiledMethod)compiledMethod;
          OptMachineCodeMap map = optInfo.getMCMap();
          int iei = map.getInlineEncodingForMCOffset(instructionOffset);
          if (iei < 0) {
            elements[element] = new Element(compiledMethod, instructionOffsets[i]);
            element++;
          } else {
            int[] inlineEncoding = map.inlineEncoding;
            int bci = map.getBytecodeIndexForMCOffset(instructionOffset);
            for (; iei >= 0; iei = OptEncodedCallSiteTree.getParent(iei, inlineEncoding)) {
              int mid = OptEncodedCallSiteTree.getMethodID(iei, inlineEncoding);
              RVMMethod method = MemberReference.getMemberRef(mid).asMethodReference().getResolvedMember();
              int lineNumber = ((NormalMethod)method).getLineNumberForBCIndex(bci);
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
        CompiledMethod compiledMethod = getCompiledMethod(i);
        if ((compiledMethod == null) ||
            (compiledMethod.getCompilerType() != CompiledMethod.OPT)) {
          // Invisible or non-opt compiled method
          numElements++;
        } else {
          Offset instructionOffset = Offset.fromIntSignExtend(instructionOffsets[i]);
          OptCompiledMethod optInfo = (OptCompiledMethod)compiledMethod;
          OptMachineCodeMap map = optInfo.getMCMap();
          int iei = map.getInlineEncodingForMCOffset(instructionOffset);
          if (iei < 0) {
            numElements++;
          } else {
            int[] inlineEncoding = map.inlineEncoding;
            for (; iei >= 0; iei = OptEncodedCallSiteTree.getParent(iei, inlineEncoding)) {
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
     * at org.jikesrvm.runtime.StackTrace.<init>(StackTrace.java:78)
     * at java.lang.VMThrowable.fillInStackTrace(VMThrowable.java:67)
     * at java.lang.Throwable.fillInStackTrace(Throwable.java:498)
     * at java.lang.Throwable.<init>(Throwable.java:159)
     * at java.lang.Throwable.<init>(Throwable.java:147)
     * at java.lang.Exception.<init>(Exception.java:66)
     * at java.lang.RuntimeException.<init>(RuntimeException.java:64)
     * at java.lang.NullPointerException.<init>(NullPointerException.java:69)
     * at org.jikesrvm.runtime.RuntimeEntrypoints.deliverHardwareException(RuntimeEntrypoints.java:682)
     * at <hardware trap>(Unknown Source:0)
     *
     * and a software trap to look like:
     * at org.jikesrvm.runtime.StackTrace.<init>(StackTrace.java:78)
     * at java.lang.VMThrowable.fillInStackTrace(VMThrowable.java:67)
     * at java.lang.Throwable.fillInStackTrace(Throwable.java:498)
     * at java.lang.Throwable.<init>(Throwable.java:159)
     * at java.lang.Error.<init>(Error.java:81)
     * at java.lang.LinkageError.<init>(LinkageError.java:72)
     * at java.lang.ExceptionInInitializerError.<init>(ExceptionInInitializerError.java:85)
     * at java.lang.ExceptionInInitializerError.<init>(ExceptionInInitializerError.java:75)
     *
     * and an OutOfMemoryError to look like:
     * ???
     * ...
     * at org.jikesrvm.mm.mminterface.MemoryManager.allocateSpace(MemoryManager.java:613)
     * ...
     * at org.jikesrvm.runtime.RuntimeEntrypoints.unresolvedNewArray(RuntimeEntrypoints.java:401)
     */
    if (Options.stackTraceFull) {
      return 0;
    } else {
      int element = 0;
      CompiledMethod compiledMethod = getCompiledMethod(element);

      // Deal with OutOfMemoryError
      if (cause instanceof OutOfMemoryError) {
        // (1) search until RuntimeEntrypoints
        while((element < compiledMethods.length) &&
            (compiledMethod != null) &&
             compiledMethod.getMethod().getDeclaringClass().getClassForType() != RuntimeEntrypoints.class) {
          element++;
          compiledMethod = getCompiledMethod(element);
        }
        // (2) continue until not RuntimeEntrypoints
        while((element < compiledMethods.length) &&
              (compiledMethod != null) &&
              compiledMethod.getMethod().getDeclaringClass().getClassForType() == RuntimeEntrypoints.class) {
          element++;
          compiledMethod = getCompiledMethod(element);
        }
        return element;
      }

      // (1) remove any StackTrace frames
      while((element < compiledMethods.length) &&
            (compiledMethod != null) &&
            compiledMethod.getMethod().getDeclaringClass().getClassForType() == StackTrace.class) {
        element++;
        compiledMethod = getCompiledMethod(element);
      }
      // (2) remove any VMThrowable frames
      if (VM.BuildForGnuClasspath) {
        while((element < compiledMethods.length) &&
              (compiledMethod != null) &&
              compiledMethod.getMethod().getDeclaringClass().getClassForType().getName().equals("java.lang.VMThrowable")) {
          element++;
          compiledMethod = getCompiledMethod(element);
        }
      }
      // (3) remove any Throwable frames
      while((element < compiledMethods.length) &&
            (compiledMethod != null) &&
            compiledMethod.getMethod().getDeclaringClass().getClassForType() == java.lang.Throwable.class) {
        element++;
        compiledMethod = getCompiledMethod(element);
      }
      // (4) remove frames belonging to exception constructors upto the causes constructor
      while((element < compiledMethods.length) &&
            (compiledMethod != null) &&
            (compiledMethod.getMethod().getDeclaringClass().getClassForType() != cause.getClass()) &&
            compiledMethod.getMethod().isObjectInitializer() &&
            compiledMethod.getMethod().getDeclaringClass().isThrowable()) {
        element++;
        compiledMethod = getCompiledMethod(element);
      }
      // (5) remove frames belonging to the causes constructor
      // NB This can be made to incorrectly elide frames if the cause
      // exception is thrown from a constructor of the cause exception, however,
      // Sun's VM has the same problem
      while((element < compiledMethods.length) &&
            (compiledMethod != null) &&
            (compiledMethod.getMethod().getDeclaringClass().getClassForType() == cause.getClass()) &&
            compiledMethod.getMethod().isObjectInitializer()) {
        element++;
        compiledMethod = getCompiledMethod(element);
      }
      // (6) remove possible hardware exception deliverer frames
      if (element < compiledMethods.length - 2) {
        compiledMethod = getCompiledMethod(element+1);
        if ((compiledMethod != null) &&
            compiledMethod.getCompilerType() == CompiledMethod.TRAP) {
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
     * at org.jikesrvm.runtime.Reflection.invoke(Reflection.java:132)
     * at org.jikesrvm.scheduler.MainThread.run(MainThread.java:195)
     * at org.jikesrvm.scheduler.RVMThread.run(RVMThread.java:534)
     * at org.jikesrvm.scheduler.RVMThread.startoff(RVMThread.java:1113
     *
     * and on another thread to look like:
     * at org.jikesrvm.scheduler.RVMThread.run(RVMThread.java:534)
     * at org.jikesrvm.scheduler.RVMThread.startoff(RVMThread.java:1113)
     */
    int max = compiledMethods.length-1;
    if (Options.stackTraceFull) {
      return max;
    } else {
      // Start at end of array and elide a frame unless we find a place to stop
      for (int i=max; i >= first; i--) {
        if (compiledMethods[i] == INVISIBLE_METHOD_ID) {
          // we found an invisible method, assume next method if this is sane
          if (i-1 >= 0) {
            return i-1;
          } else {
            return max; // not sane => return max
          }
        }
        CompiledMethod compiledMethod = getCompiledMethod(i);
        if (compiledMethod.getCompilerType() == CompiledMethod.TRAP) {
          // looks like we've gone too low
          return max;
        }
        Class<?> frameClass = compiledMethod.getMethod().getDeclaringClass().getClassForType();
        if ((frameClass != org.jikesrvm.scheduler.MainThread.class) &&
            (frameClass != org.jikesrvm.scheduler.RVMThread.class) &&
            (frameClass != org.jikesrvm.runtime.Reflection.class)){
          // Found a non-VM method
          return i;
        }
      }
      // No frame found
      return max;
    }
  }
}

