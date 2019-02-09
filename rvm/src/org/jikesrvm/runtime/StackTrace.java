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
package org.jikesrvm.runtime;

import org.jikesrvm.VM;
import org.jikesrvm.architecture.AbstractRegisters;
import org.jikesrvm.architecture.StackFrameLayout;
import org.jikesrvm.Options;
import org.jikesrvm.classloader.Atom;
import org.jikesrvm.classloader.MemberReference;
import org.jikesrvm.classloader.RVMMethod;
import org.jikesrvm.classloader.NormalMethod;
import org.jikesrvm.compilers.baseline.BaselineCompiledMethod;
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
   * Prints an internal stack trace for every stack trace obtained via
   * {@link #getStackTrace(Throwable)}. The internal stack trace
   * has machine code offsets and bytecode index information for methods.
   */
  private static final boolean PRINT_INTERNAL_STACK_TRACE = false;

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
   * Create a trace for the call stack of the current thread
   */
  @NoInline
  public StackTrace() {
    this(RVMThread.getCurrentThread());
  }

  /**
   * Constructs a stack trace.
   * <p>
   * Note: no inlining directives here because they aren't necessary for correctness.
   * This class removes all frames belonging to its methods.
   *
   * @param rvmThread the thread whose stack is examined. It is the caller's
   *  responsibility to block that thread if required.
   */
  public StackTrace(RVMThread rvmThread) {
    assertThreadBlockedOrCurrent(rvmThread);
    boolean isVerbose = false;
    int traceIndex = 0;
    if (VM.VerifyAssertions && VM.VerboseStackTracePeriod > 0) {
      // Poor man's atomic integer, to get through bootstrap
      synchronized (StackTrace.class) {
         traceIndex = lastTraceIndex++;
      }
      isVerbose = (traceIndex % VM.VerboseStackTracePeriod == 0);
    }
    // (1) Count the number of frames comprising the stack.
    int numFrames = countFramesUninterruptible(rvmThread);
    // (2) Construct arrays to hold raw data
    compiledMethods = new int[numFrames];
    instructionOffsets = new int[numFrames];
    // (3) Fill in arrays
    recordFramesUninterruptible(rvmThread);
    // Debugging trick: print every nth stack trace created
    if (isVerbose) {
      VM.disableGC();
      VM.sysWriteln("[ BEGIN Verbosely dumping stack at time of creating StackTrace # ", traceIndex);
      RVMThread.dumpStack();
      VM.sysWriteln("END Verbosely dumping stack at time of creating StackTrace # ", traceIndex, " ]");
      VM.enableGC();
    }
  }

  private void assertThreadBlockedOrCurrent(RVMThread rvmThread) {
    if (VM.VerifyAssertions) {
      if (rvmThread != RVMThread.getCurrentThread()) {
        rvmThread.monitor().lockNoHandshake();
        boolean blocked = rvmThread.isBlocked();
        rvmThread.monitor().unlock();
        if (!blocked) {
          String msg = "Can only dump stack of blocked threads if not dumping" +
              " own stack but thread " +  rvmThread + " was in state " +
              rvmThread.getExecStatus() + " and wasn't blocked!";
          VM._assert(VM.NOT_REACHED, msg);
        }
      }
    }
  }

  /**
   * Walk the stack counting the number of stack frames encountered.
   * The stack being walked is our stack, so code is Uninterruptible to stop the
   * stack moving.
   *
   * @param stackTraceThread the thread whose stack is walked
   * @return number of stack frames encountered
   */
  @Uninterruptible
  @NoInline
  private int countFramesUninterruptible(RVMThread stackTraceThread) {
    int stackFrameCount = 0;
    Address fp;
    /* Stack trace for the thread */
    if (stackTraceThread == RVMThread.getCurrentThread()) {
      fp = Magic.getFramePointer();
    } else {
      AbstractRegisters contextRegisters = stackTraceThread.getContextRegisters();
      fp =  contextRegisters.getInnermostFramePointer();
    }
    fp = Magic.getCallerFramePointer(fp);
    while (Magic.getCallerFramePointer(fp).NE(StackFrameLayout.getStackFrameSentinelFP())) {
      int compiledMethodId = Magic.getCompiledMethodID(fp);
      if (compiledMethodId != StackFrameLayout.getInvisibleMethodID()) {
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
      fp = Magic.getCallerFramePointer(fp);
    }
    //VM.sysWriteln("stack frame count = ",stackFrameCount);
    return stackFrameCount;
  }

  /**
   * Walk the stack recording the stack frames encountered
   * The stack being walked is our stack, so code is Uninterrupible to stop the
   * stack moving.
   *
   * @param stackTraceThread the thread whose stack is walked
   */
  @Uninterruptible
  @NoInline
  private void recordFramesUninterruptible(RVMThread stackTraceThread) {
    int stackFrameCount = 0;
    Address fp;
    Address ip;
    /* Stack trace for the thread */
    if (stackTraceThread == RVMThread.getCurrentThread()) {
      fp = Magic.getFramePointer();
    } else {
      AbstractRegisters contextRegisters = stackTraceThread.getContextRegisters();
      fp =  contextRegisters.getInnermostFramePointer();
    }
    ip = Magic.getReturnAddress(fp);
    fp = Magic.getCallerFramePointer(fp);
    while (Magic.getCallerFramePointer(fp).NE(StackFrameLayout.getStackFrameSentinelFP())) {
      //VM.sysWriteln("at stackFrameCount = ",stackFrameCount);
      int compiledMethodId = Magic.getCompiledMethodID(fp);
      compiledMethods[stackFrameCount] = compiledMethodId;
      if (compiledMethodId != StackFrameLayout.getInvisibleMethodID()) {
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
      ip = Magic.getReturnAddress(fp, stackTraceThread);
      fp = Magic.getCallerFramePointer(fp);
    }
  }

  /** Class to wrap up a stack frame element */
  public static class Element {
    /** Stack trace's method, null =&gt; invisible or trap */
    protected final RVMMethod method;
    /** Line number of element */
    protected final int lineNumber;
    /** Is this an invisible method? */
    protected final boolean isInvisible;
    /** Is this a hardware trap method? */
    protected final boolean isTrap;

    /**
     * Constructor for non-opt compiled methods
     * @param cm the compiled method
     * @param off offset of the instruction from start of machine code,
     *  in bytes
     */
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

    /**
     * Constructor for opt compiled methods.
     * @param method the method that was called
     * @param ln the line number
     */
    Element(RVMMethod method, int ln) {
      this.method = method;
      lineNumber = ln;
      isTrap = false;
      isInvisible = false;
    }

    /** @return source file name */
    public String getFileName() {
      if (isInvisible || isTrap) {
        return null;
      } else {
        Atom fn = method.getDeclaringClass().getSourceName();
        return (fn != null)  ? fn.toString() : null;
      }
    }

    public String getClassName() {
      if (isInvisible || isTrap) {
        return "";
      } else {
        return method.getDeclaringClass().toString();
      }
    }

    public Class<?> getElementClass() {
      if (isInvisible || isTrap) {
        return null;
      }
      return method.getDeclaringClass().getClassForType();
    }

    public String getMethodName() {
      if (isInvisible) {
        return "<invisible method>";
      } else if (isTrap) {
        return "<hardware trap>";
      } else {
        if (method != null) {
          return method.getName().toString();
        } else {
          return "<unknown method: method was null>";
        }
      }
    }

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
   * A stack trace element that contains additional debugging information,
   * namely machine code offsets and byte code indexes.
   */
  static class InternalStackTraceElement extends Element {
    /** machine code offset */
    private Offset mcOffset;
    /** byte code index */
    private int bci;

    /**
     * Constructor for non-opt compiled methods
     * @param cm the compiled method
     * @param off offset of the instruction from start of machine code,
     *  in bytes
     */
    InternalStackTraceElement(CompiledMethod cm, int off) {
      super(cm, off);
      if (!isInvisible) {
        if (!isTrap) {
          Offset machineCodeOffset = Offset.fromIntSignExtend(off);
          mcOffset = machineCodeOffset;
          if (cm instanceof BaselineCompiledMethod) {
            bci = ((BaselineCompiledMethod) cm).findBytecodeIndexForInstruction(machineCodeOffset);
          } else if (cm instanceof OptCompiledMethod) {
            bci = ((OptCompiledMethod) cm).getMCMap().getBytecodeIndexForMCOffset(machineCodeOffset);
          } else {
            bci = 0;
          }
        } else {
          mcOffset = Offset.zero();
          bci = 0;
        }
      } else {
        mcOffset = Offset.zero();
        bci = 0;
      }
    }

    /**
     * Constructor for opt compiled methods.
     * @param method the method that was called
     * @param ln the line number
     * @param mcOffset the machine code offset for the line
     * @param bci the bytecode index for the line
     *
     */
    InternalStackTraceElement(RVMMethod method, int ln, Offset mcOffset, int bci) {
      super(method, ln);
      this.mcOffset = mcOffset;
      this.bci = bci;
    }

    void printForDebugging() {
      VM.sysWrite("{IST: ");
      VM.sysWrite(method.getDeclaringClass().toString());
      VM.sysWrite(".");
      VM.sysWrite(method.getName());
      VM.sysWrite(" --- ");
      VM.sysWrite("line_number: ");
      VM.sysWrite(lineNumber);
      VM.sysWrite(" byte_code_index: ");
      VM.sysWrite(bci);
      VM.sysWrite(" machine_code_offset: ");
      VM.sysWrite(mcOffset);
      VM.sysWriteln();
    }
  }

  private CompiledMethod getCompiledMethod(int element) {
    if ((element >= 0) && (element < compiledMethods.length)) {
      int mid = compiledMethods[element];
      if (mid != StackFrameLayout.getInvisibleMethodID()) {
        return CompiledMethods.getCompiledMethod(mid);
      }
    }
    return null;
  }

  /**
   * @param cause the throwable that caused the stack trace
   * @return the stack trace for use by the Throwable API
   */
  public Element[] getStackTrace(Throwable cause) {
    int first = firstRealMethod(cause);
    int last = lastRealMethod(first);
    Element[] elements = buildStackTrace(first, last);
    if (PRINT_INTERNAL_STACK_TRACE) {
      VM.sysWriteln();
      for (Element e : elements) {
        InternalStackTraceElement internalEle = (InternalStackTraceElement) e;
        internalEle.printForDebugging();
      }
    }
    return elements;
  }

  private Element createStandardStackTraceElement(CompiledMethod cm, int off) {
    if (!PRINT_INTERNAL_STACK_TRACE) {
      return new Element(cm, off);
    } else {
      return new InternalStackTraceElement(cm, off);
    }
  }

  private Element createOptStackTraceElement(RVMMethod m, int ln, Offset mcOffset, int bci) {
    if (!PRINT_INTERNAL_STACK_TRACE) {
      return new Element(m, ln);
    } else {
      return new InternalStackTraceElement(m, ln, mcOffset, bci);
    }
  }

  private Element[] buildStackTrace(int first, int last) {
    Element[] elements = new Element[countFrames(first, last)];
    if (!VM.BuildForOptCompiler) {
      int element = 0;
      for (int i = first; i <= last; i++) {
        elements[element] = createStandardStackTraceElement(getCompiledMethod(i), instructionOffsets[i]);
        element++;
      }
    } else {
      int element = 0;
      for (int i = first; i <= last; i++) {
        CompiledMethod compiledMethod = getCompiledMethod(i);
        if ((compiledMethod == null) ||
            (compiledMethod.getCompilerType() != CompiledMethod.OPT)) {
          // Invisible or non-opt compiled method
          elements[element] = createStandardStackTraceElement(compiledMethod, instructionOffsets[i]);
          element++;
        } else {
          Offset instructionOffset = Offset.fromIntSignExtend(instructionOffsets[i]);
          OptCompiledMethod optInfo = (OptCompiledMethod)compiledMethod;
          OptMachineCodeMap map = optInfo.getMCMap();
          int iei = map.getInlineEncodingForMCOffset(instructionOffset);
          if (iei < 0) {
            elements[element] = createStandardStackTraceElement(compiledMethod, instructionOffsets[i]);
            element++;
          } else {
            int[] inlineEncoding = map.inlineEncoding;
            int bci = map.getBytecodeIndexForMCOffset(instructionOffset);
            for (; iei >= 0; iei = OptEncodedCallSiteTree.getParent(iei, inlineEncoding)) {
              int mid = OptEncodedCallSiteTree.getMethodID(iei, inlineEncoding);
              RVMMethod method = MemberReference.getMethodRef(mid).getResolvedMember();
              int lineNumber = ((NormalMethod)method).getLineNumberForBCIndex(bci);
              elements[element] = createOptStackTraceElement(method, lineNumber, instructionOffset, bci);
              element++;
              if (iei > 0) {
                bci = OptEncodedCallSiteTree.getByteCodeOffset(iei, inlineEncoding);
              }
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
   * @return the number of stack frames
   */
  private int countFrames(int first, int last) {
    int numElements = 0;
    if (!VM.BuildForOptCompiler) {
      numElements = last - first + 1;
    } else {
      for (int i = first; i <= last; i++) {
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
   * trace. As we're working with the compiled methods we're assuming the
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
        while ((element < compiledMethods.length) &&
            (compiledMethod != null) &&
             compiledMethod.getMethod().getDeclaringClass().getClassForType() != RuntimeEntrypoints.class) {
          element++;
          compiledMethod = getCompiledMethod(element);
        }
        // (2) continue until not RuntimeEntrypoints
        while ((element < compiledMethods.length) &&
              (compiledMethod != null) &&
              compiledMethod.getMethod().getDeclaringClass().getClassForType() == RuntimeEntrypoints.class) {
          element++;
          compiledMethod = getCompiledMethod(element);
        }
        return element;
      }

      // (1) remove any StackTrace frames
      element = removeStackTraceFrames(element);
      compiledMethod = getCompiledMethod(element);

      // (2a) remove any VMThrowable frames
      if (VM.BuildForGnuClasspath) {
        while ((element < compiledMethods.length) &&
              (compiledMethod != null) &&
              compiledMethod.getMethod().getDeclaringClass().getClassForType().getName().equals("java.lang.VMThrowable")) {
          element++;
          compiledMethod = getCompiledMethod(element);
        }
      }
      // (2b) remove any java_lang_Throwable frames.
      if (VM.BuildForOpenJDK) {
        while ((element < compiledMethods.length) &&
              (compiledMethod != null) &&
              compiledMethod.getMethod().getDeclaringClass().getClassForType().getName().equals("org.jikesrvm.classlibrary.openjdk.replacements.java_lang_Throwable")) {
          element++;
          compiledMethod = getCompiledMethod(element);
        }
      }
      // (3) remove any Throwable frames
      while ((element < compiledMethods.length) &&
            (compiledMethod != null) &&
            compiledMethod.getMethod().getDeclaringClass().getClassForType() == java.lang.Throwable.class) {
        element++;
        compiledMethod = getCompiledMethod(element);
      }
      // (4) remove frames belonging to exception constructors upto the causes constructor
      while ((element < compiledMethods.length) &&
            (compiledMethod != null) &&
            (compiledMethod.getMethod().getDeclaringClass().getClassForType() != cause.getClass()) &&
            compiledMethod.getMethod().isObjectInitializer() &&
            compiledMethod.getMethod().getDeclaringClass().isAssignableToThrowable()) {
        element++;
        compiledMethod = getCompiledMethod(element);
      }
      // (5) remove frames belonging to the causes constructor
      // NB This can be made to incorrectly elide frames if the cause
      // exception is thrown from a constructor of the cause exception, however,
      // Sun's VM has the same problem
      while ((element < compiledMethods.length) &&
            (compiledMethod != null) &&
            (compiledMethod.getMethod().getDeclaringClass().getClassForType() == cause.getClass()) &&
            compiledMethod.getMethod().isObjectInitializer()) {
        element++;
        compiledMethod = getCompiledMethod(element);
      }
      // (6) remove possible RuntimeEntrypoints.raise* methods used by
      // PPC opt compiler. Note: only one of the methods can be present at
      // a time!
      if ((element < compiledMethods.length) &&
        (compiledMethod != null) &&
        Entrypoints.isInvisibleRaiseMethod(compiledMethod.getMethod())) {
        element++;
        compiledMethod = getCompiledMethod(element);
      }
      // (7) remove possible hardware exception deliverer frames
      if (element < compiledMethods.length - 2) {
        compiledMethod = getCompiledMethod(element + 1);
        if ((compiledMethod != null) &&
            compiledMethod.getCompilerType() == CompiledMethod.TRAP) {
          element += 2;
        }
      }
      return element;
    }
  }

  /**
   * Finds the first non-VM method in the stack trace. In this case, the assumption
   * is that no exception occurred which makes the job of this method much easier
   * than of {@link #firstRealMethod(Throwable)}: it is only necessary to skip
   * frames from this class.
   *
   * @return the index of the method or else 0
   */
  private int firstRealMethod() {
    return removeStackTraceFrames(0);
  }

  /**
   * Removes all frames from the StackTrace class (i.e. this class) from
   * the stack trace by skipping them.
   * <p>
   * Note: Callers must update all data relating to the element index themselves.
   *
   * @param element the element index
   * @return an updated element index
   */
  private int removeStackTraceFrames(int element) {
    CompiledMethod compiledMethod = getCompiledMethod(element);
    while ((element < compiledMethods.length) &&
          (compiledMethod != null) &&
          compiledMethod.getMethod().getDeclaringClass().getClassForType() == StackTrace.class) {
      element++;
      compiledMethod = getCompiledMethod(element);
    }
    return element;
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
    int max = compiledMethods.length - 1;
    if (Options.stackTraceFull) {
      return max;
    } else {
      // Start at end of array and elide a frame unless we find a place to stop
      for (int i = max; i >= first; i--) {
        if (compiledMethods[i] == StackFrameLayout.getInvisibleMethodID()) {
          // we found an invisible method, assume next method if this is sane
          if (i - 1 >= 0) {
            return i - 1;
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
            (frameClass != org.jikesrvm.runtime.Reflection.class)) {
          // Found a non-VM method
          return i;
        }
      }
      // No frame found
      return max;
    }
  }

  /**
   * Gets a stack trace at the current point in time, assuming the thread didn't
   * throw any exception.
   *
   * @param framesToSkip count of frames to skip. Note: frames from this class are always
   * skipped and thus not included in the count. For example, if the caller were
   * {@code foo()} and you wanted to skip {@code foo}'s frame, you would pass
   * {@code 1}.
   *
   * @return a stack trace
   */
  public Element[] stackTraceNoException(int framesToSkip) {
    if (VM.VerifyAssertions) VM._assert(framesToSkip >= 0, "Cannot skip negative amount of frames");
    int first = firstRealMethod();
    first += framesToSkip;
    int last = lastRealMethod(first);
    return buildStackTrace(first, last);
  }

  /**
   * Converts a series of Jikes RVM internal stack trace elements to a series of stack
   * trace elements of the Java API.
   *
   * @param vmElements a non-{@code null} array of stack elemetns
   * @return a possibly empty array of stack trace elements
   */
  public static StackTraceElement[] convertToJavaClassLibraryStackTrace(
      Element[] vmElements) {
    StackTraceElement[] elements = new StackTraceElement[vmElements.length];
    for (int i = 0; i < vmElements.length; i++) {
      Element vmElement = vmElements[i];
      String fileName = vmElement.getFileName();
      int lineNumber = vmElement.getLineNumber();
      String className = vmElement.getClassName();
      String methodName = vmElement.getMethodName();
      elements[i] = new StackTraceElement(className, methodName, fileName, lineNumber);
    }
    return elements;
  }

}

