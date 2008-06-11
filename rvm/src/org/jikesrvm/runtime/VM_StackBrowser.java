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

import org.jikesrvm.ArchitectureSpecific;
import org.jikesrvm.VM;
import org.jikesrvm.classloader.VM_Class;
import org.jikesrvm.classloader.VM_Method;
import org.jikesrvm.compilers.common.VM_CompiledMethod;
import org.jikesrvm.compilers.common.VM_CompiledMethods;
import org.vmmagic.pragma.NoInline;
import org.vmmagic.unboxed.Address;
import org.vmmagic.unboxed.Offset;

/**
 *  Use this class to explore the stack.  It is sometimes necessary to
 *  find out the current context class loader, and other things like that.
 */
public final class VM_StackBrowser implements ArchitectureSpecific.VM_StackframeLayoutConstants {

  /** Method associated with current stack location */
  private VM_Method currentMethod;
  /** Bytecode associated with current stack location */
  private int currentBytecodeIndex;

  /** The frame pointer for the current stack location */
  private Address currentFramePointer;
  /** The offset of the current instruction within its method */
  private Offset currentInstructionPointer;
  /** The current compiled method */
  private VM_CompiledMethod currentCompiledMethod;
  /** The current inline encoding index for opt compiled methods */
  private int currentInlineEncodingIndex;

  /** Initialise state of browser */
  @NoInline
  public void init() {
    currentFramePointer = VM_Magic.getFramePointer();
    upOneFrame();
  }

  /**
   * Browse up one frame
   * @param set should the state of the stack browser be effected?
   * @return do more frames exist?
   */
  private boolean upOneFrameInternal(boolean set) {
    Address fp;
    if (currentMethod != null && currentMethod.getDeclaringClass().hasBridgeFromNativeAnnotation()) {
      // Elide native frames
      fp = RuntimeEntrypoints.unwindNativeStackFrame(currentFramePointer);
    } else {
        fp = currentFramePointer;
    }

    Address prevFP = fp;
    Address newFP = VM_Magic.getCallerFramePointer(fp);
    if (newFP.EQ(STACKFRAME_SENTINEL_FP)) {
      return false;
    }
    // getReturnAddress has to be put here, consider the case
    // on ppc, when fp is the frame above SENTINEL FP
    Address newIP = VM_Magic.getReturnAddress(prevFP);

    int cmid = VM_Magic.getCompiledMethodID(newFP);

    while (cmid == INVISIBLE_METHOD_ID) {
      prevFP = newFP;
      newFP = VM_Magic.getCallerFramePointer(newFP);
      if (newFP.EQ(STACKFRAME_SENTINEL_FP)) {
        return false;
      }
      newIP = VM_Magic.getReturnAddress(prevFP);
      cmid = VM_Magic.getCompiledMethodID(newFP);
    }

    if (set) {
      VM_CompiledMethod cm = VM_CompiledMethods.getCompiledMethod(cmid);
      currentFramePointer = newFP;
      currentInstructionPointer = cm.getInstructionOffset(newIP);
      cm.set(this, currentInstructionPointer);
    }
    return true;
  }

  /** Browse up one frame failing if we fall off the stack */
  private void upOneFrame() {
    boolean ok = upOneFrameInternal(true);
    if (VM.VerifyAssertions) VM._assert(ok, "tried to browse off stack");
  }

  /** Are there more stack frames? */
  public boolean hasMoreFrames() {
    return upOneFrameInternal(false);
  }

  /** Browse up one frame eliding native frames */
  public void up() {
    if (!currentCompiledMethod.up(this)) {
      upOneFrame();
    }
  }

  /** Set the current bytecode index, called only by the appropriate compiled method code */
  public void setBytecodeIndex(int bytecodeIndex) {
    currentBytecodeIndex = bytecodeIndex;
  }

  /** Set the current method, called only by the appropriate compiled method code */
  public void setMethod(VM_Method method) {
    currentMethod = method;
  }

  /** Set the current compiled method, called only by the appropriate compiled method code */
  public void setCompiledMethod(VM_CompiledMethod cm) {
    currentCompiledMethod = cm;
  }

  /** Set the inline encoding for opt compiled methods only */
  public void setInlineEncodingIndex(int index) {
    currentInlineEncodingIndex = index;
  }

  /** The bytecode index associated with the current stack frame */
  public int getBytecodeIndex() {
    return currentBytecodeIndex;
  }

  /** The method associated with the current stack frame */
  public VM_Method getMethod() {
    return currentMethod;
  }

  /** The compiled method associated with the current stack frame */
  public VM_CompiledMethod getCompiledMethod() {
    return currentCompiledMethod;
  }

  /** The class of the method associated with the current stack frame */
  public VM_Class getCurrentClass() {
    return getMethod().getDeclaringClass();
  }

  /** The class loader of the method associated with the current stack frame */
  public ClassLoader getClassLoader() {
    return getCurrentClass().getClassLoader();
  }

  /** Get the inline encoding associated with the current stack location, called only by opt compiled methods */
  public int getInlineEncodingIndex() {
    return currentInlineEncodingIndex;
  }
}
