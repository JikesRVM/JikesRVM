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
import org.jikesrvm.architecture.StackFrameLayout;
import org.jikesrvm.classloader.RVMClass;
import org.jikesrvm.classloader.RVMMethod;
import org.jikesrvm.compilers.common.CompiledMethod;
import org.jikesrvm.compilers.common.CompiledMethods;
import org.vmmagic.pragma.NoInline;
import org.vmmagic.unboxed.Address;
import org.vmmagic.unboxed.Offset;

/**
 *  Use this class to explore the stack.  It is sometimes necessary to
 *  find out the current context class loader, and other things like that.
 */
public final class StackBrowser {

  /** Method associated with current stack location */
  private RVMMethod currentMethod;
  /** Bytecode associated with current stack location */
  private int currentBytecodeIndex;

  /** The frame pointer for the current stack location */
  private Address currentFramePointer;
  /** The offset of the current instruction within its method */
  private Offset currentInstructionPointer;
  /** The current compiled method */
  private CompiledMethod currentCompiledMethod;
  /** The current inline encoding index for opt compiled methods */
  private int currentInlineEncodingIndex;

  /** Initialise state of browser */
  @NoInline
  public void init() {
    currentFramePointer = Magic.getFramePointer();
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
    Address newFP = Magic.getCallerFramePointer(fp);
    if (newFP.EQ(StackFrameLayout.getStackFrameSentinelFP())) {
      return false;
    }
    // getReturnAddress has to be put here, consider the case
    // on ppc, when fp is the frame above SENTINEL FP
    Address newIP = Magic.getReturnAddress(prevFP);

    int cmid = Magic.getCompiledMethodID(newFP);

    while (cmid == StackFrameLayout.getInvisibleMethodID()) {
      prevFP = newFP;
      newFP = Magic.getCallerFramePointer(newFP);
      if (newFP.EQ(StackFrameLayout.getStackFrameSentinelFP())) {
        return false;
      }
      newIP = Magic.getReturnAddress(prevFP);
      cmid = Magic.getCompiledMethodID(newFP);
    }

    if (set) {
      CompiledMethod cm = CompiledMethods.getCompiledMethod(cmid);
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

  /** @return whether there are more stack frames */
  public boolean hasMoreFrames() {
    return upOneFrameInternal(false);
  }

  /** Browse up one frame eliding native frames */
  public void up() {
    if (!currentCompiledMethod.up(this)) {
      upOneFrame();
    }
  }

  /**
   * Example: for a stack trace of
   * <pre>
  A1 <- frame with call to countFrames()
  A2
  A3
  A4
  A5
  A6
  A7
  A8
  A9
    </pre>
   * the result would be 8 (9 frames and frame A1 isn't counted).
   *
   * @return a count of frames, excluding the frame of this method and the frame that the method is called in
   */
  public int countFrames() {
    // Save state
    RVMMethod oldMethod = currentMethod;
    int oldIndex = currentBytecodeIndex;
    Address oldFramePointer = currentFramePointer;
    Offset oldInstructionPointer = currentInstructionPointer;
    CompiledMethod oldCompiledMethod = currentCompiledMethod;
    int oldInlineEncodingfIndex = currentInlineEncodingIndex;
    // Count
    int frameCount = 0;
    while (hasMoreFrames()) {
      frameCount++;
      up();
    }
    // Reset instance variables
    currentMethod = oldMethod;
    currentBytecodeIndex = oldIndex;
    currentFramePointer = oldFramePointer;
    currentInstructionPointer = oldInstructionPointer;
    currentCompiledMethod = oldCompiledMethod;
    // Reset position in compiled method
    int compiledMethodID = Magic.getCompiledMethodID(currentFramePointer);
    CompiledMethod compiledMethod = CompiledMethods.getCompiledMethod(compiledMethodID);
    compiledMethod.set(this, currentInstructionPointer);
    currentInlineEncodingIndex = oldInlineEncodingfIndex;

    return frameCount;
  }

  public void setBytecodeIndex(int bytecodeIndex) {
    currentBytecodeIndex = bytecodeIndex;
  }

  public void setMethod(RVMMethod method) {
    currentMethod = method;
  }

  public void setCompiledMethod(CompiledMethod cm) {
    currentCompiledMethod = cm;
  }

  /**
   * Set the inline encoding. This is only necessary for
   * opt compiled methods.
   *
   * @param index the inline encoding index
   */
  public void setInlineEncodingIndex(int index) {
    currentInlineEncodingIndex = index;
  }

  /** @return the bytecode index associated with the current stack frame */
  public int getBytecodeIndex() {
    return currentBytecodeIndex;
  }

  /** @return the method associated with the current stack frame */
  public RVMMethod getMethod() {
    return currentMethod;
  }

  /** @return the compiled method associated with the current stack frame */
  public CompiledMethod getCompiledMethod() {
    return currentCompiledMethod;
  }

  /** @return the class of the method associated with the current stack frame */
  public RVMClass getCurrentClass() {
    return getMethod().getDeclaringClass();
  }

  /** @return the class loader of the method associated with the current stack frame */
  public ClassLoader getClassLoader() {
    return getCurrentClass().getClassLoader();
  }

  /**
   * Get the inline encoding associated with the current stack location.
   * This method is called only by opt compiled methods.
   *
   *  @return the inline encoding associated with the current stack location
   */
  public int getInlineEncodingIndex() {
    return currentInlineEncodingIndex;
  }

  public boolean currentMethodIs_Java_Lang_Reflect_Method_InvokeMethod() {
    return currentMethod == Entrypoints.java_lang_reflect_Method_invokeMethod;
  }

  public boolean currentMethodIs_Java_Lang_Reflect_Method_GetCallerClass() {
    if (VM.VerifyAssertions) VM._assert(VM.BuildForOpenJDK);
    return currentMethod == Entrypoints.java_lang_reflect_Method_getCallerClass;
  }

  public boolean currentMethodIs_Java_Lang_Reflect_Constructor_NewInstance() {
    return currentMethod == Entrypoints.java_lang_reflect_Constructor_newInstance;
  }

  public boolean currentMethodIsJikesRVMInternal() {
    return currentMethod.getDeclaringClass().getDescriptor().isRVMDescriptor();
  }

  public boolean currentMethodIsInClassLibrary() {
    return currentMethod.getDeclaringClass().getDescriptor().isClassLibraryDescriptor();
  }

  public boolean currentMethodIsPartOfJikesRVMJNIImplementation() {
    return currentMethod.getDeclaringClass().getDescriptor().isJNIImplementationClassDescriptor();
  }
}
