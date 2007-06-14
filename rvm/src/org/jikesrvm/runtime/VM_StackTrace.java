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

import static org.jikesrvm.ArchitectureSpecific.VM_StackframeLayoutConstants.STACKFRAME_SENTINEL_FP;
import static org.jikesrvm.ArchitectureSpecific.VM_StackframeLayoutConstants.INVISIBLE_METHOD_ID;
import org.jikesrvm.VM;
import org.jikesrvm.classloader.VM_Atom;
import org.jikesrvm.classloader.VM_Class;
import org.jikesrvm.classloader.VM_MemberReference;
import org.jikesrvm.classloader.VM_Method;
import org.jikesrvm.classloader.VM_NormalMethod;
import org.jikesrvm.compilers.common.VM_CompiledMethod;
import org.jikesrvm.compilers.common.VM_CompiledMethods;
import org.jikesrvm.compilers.opt.VM_OptCompiledMethod;
import org.jikesrvm.compilers.opt.VM_OptEncodedCallSiteTree;
import org.jikesrvm.compilers.opt.VM_OptMachineCodeMap;
import org.jikesrvm.scheduler.VM_Scheduler;
import org.vmmagic.unboxed.Address;
import org.vmmagic.unboxed.Offset;

/**
 * A list of compiled method and instructionOffset pairs that describe the state
 * of the call stack at a particular instant.
 */
public class VM_StackTrace {
  /** The compiled methods of the stack trace */
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
   * @param skip number of stack trace elements to skip prior to creating
   */
  public VM_StackTrace(int skip) {
    // Poor man's atomic integer, to get through bootstrap
    synchronized(VM_StackTrace.class) {
      lastTraceIndex++;
      traceIndex = lastTraceIndex;
    }
    // (1) Count the number of frames comprising the stack.
    int numFrames = walkFrames(false, skip + 1);
    // (2) Construct arrays to hold raw data
    compiledMethods = new VM_CompiledMethod[numFrames];
    instructionOffsets = new int[numFrames];
    // (3) Fill in arrays
    walkFrames(true, skip + 1);
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
   * @param skip number of stack frames to skip prior to counting
   * @return number of stack frames encountered
   */
  private int walkFrames(boolean record, int skip) {
    int stackFrameCount = 0;
    VM.disableGC(); // so fp & ip don't change under our feet
    Address fp = VM_Magic.getFramePointer();
    Address ip = VM_Magic.getReturnAddress(fp);
    for (int i = 0; i < skip; i++) {
      fp = VM_Magic.getCallerFramePointer(fp);
      ip = VM_Magic.getReturnAddress(fp);
    }
    fp = VM_Magic.getCallerFramePointer(fp);
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
  public Element[] getStackTrace() {
    Element[] elements = new Element[countFrames()];
    if (!VM.BuildForOptCompiler) {
      for (int i=0; i < compiledMethods.length; i++) {
        elements[i] = new Element(compiledMethods[i], instructionOffsets[i]);
      }
    } else {
      int element = 0;
      for (int i=0; i < compiledMethods.length; i++) {
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

  /** Count number of stack frames including those inlined */
  private int countFrames() {
    int numElements=0;    
    if (!VM.BuildForOptCompiler) {
      numElements = compiledMethods.length;
    } else {
      for (int i=0; i < compiledMethods.length; i++) {
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
}
