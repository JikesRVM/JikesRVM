/*
 * (C) Copyright IBM Corp. 2002, 2005
 */
//$Id$
package com.ibm.JikesRVM;

import com.ibm.JikesRVM.classloader.*;

import org.vmmagic.unboxed.*;
import org.vmmagic.pragma.*;

/**
 *  Use this class to explore the stack.  It is sometimes necessary to
 *  find out the current context class loader, and other things like that.
 *  
 * @author Julian Dolby
 * @date May 20, 2002
 */
public final class VM_StackBrowser implements VM_Constants {

  private VM_Method currentMethod;
  private int currentBytecodeIndex;

  private Address currentFramePointer;
  private int currentInstructionPointer;
  private VM_CompiledMethod currentCompiledMethod;
    
  //-#if RVM_WITH_OPT_COMPILER
  private int currentInlineEncodingIndex;
  //-#endif

  public void init() throws NoInlinePragma {
    currentFramePointer = VM_Magic.getFramePointer();
    upOneFrame();
  }

  private boolean upOneFrameInternal(boolean set) {
    Address fp;
    if (currentMethod != null && currentMethod.getDeclaringClass().isBridgeFromNative()) 
      fp = VM_Runtime.unwindNativeStackFrame(currentFramePointer);
    else 
      fp = currentFramePointer;

    Address prevFP = fp;
    Address newFP = VM_Magic.getCallerFramePointer(fp);
    if (newFP.EQ(STACKFRAME_SENTINEL_FP) )
      return false;
    // getReturnAddress has to be put here, consider the case
    // on ppc, when fp is the frame above SENTINEL FP
    Address newIP = VM_Magic.getReturnAddress(prevFP);

    int cmid = VM_Magic.getCompiledMethodID(newFP);
        
    while (cmid == INVISIBLE_METHOD_ID) {
      prevFP = newFP;
      newFP = VM_Magic.getCallerFramePointer(newFP);
      if (newFP.EQ(STACKFRAME_SENTINEL_FP))
        return false;
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

  private void upOneFrame() {
    boolean ok = upOneFrameInternal(true);
    if (VM.VerifyAssertions) VM._assert(ok, "tried to browse off stack");
  }

  public boolean hasMoreFrames() {
    return upOneFrameInternal(false);
  }
    
  public void up() {
    if (!currentCompiledMethod.up(this)) {
      upOneFrame();
    }
  }

  public void setBytecodeIndex(int bytecodeIndex) {
    currentBytecodeIndex = bytecodeIndex;
  }

  public int getBytecodeIndex() {
    return currentBytecodeIndex;
  }

  public void setMethod(VM_Method method) {
    currentMethod = method;
  }

  public VM_Method getMethod() {
    return currentMethod;
  }

  public VM_CompiledMethod getCompiledMethod() {
    return currentCompiledMethod;
  }

  public void setCompiledMethod(VM_CompiledMethod cm) {
    currentCompiledMethod = cm;
  }

  public VM_Class getCurrentClass() {
    return getMethod().getDeclaringClass();
  }

  public ClassLoader getClassLoader() {
    return getCurrentClass().getClassLoader();
  }

  //-#if RVM_WITH_OPT_COMPILER
  public void setInlineEncodingIndex(int index) {
    currentInlineEncodingIndex = index;
  }

  public int getInlineEncodingIndex() {
    return currentInlineEncodingIndex;
  }
  //-#endif
}
