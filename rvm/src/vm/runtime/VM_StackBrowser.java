/*
 * (C) Copyright IBM Corp. 2002
 */
//$Id$

/**
 * @author Julian Dolby
 * @date May 20, 2002
 */

class VM_StackBrowser implements VM_Constants {

  private VM_Method currentMethod;
  private int currentBytecodeIndex;

  private VM_Address currentFramePointer;
  private int currentInstructionPointer;
  private VM_CompilerInfo currentCompilerInfo;

  //-#if RVM_WITH_OPT_COMPILER
  private int currentInlineEncodingIndex;
  //-#endif

  public void init() throws VM_PragmaNoInline {
    currentFramePointer = VM_Magic.getFramePointer();
    upOneFrame();
  }

  private void upOneFrame() {
    VM_Address newIP = VM_Magic.getReturnAddress(currentFramePointer);
    VM_Address newFP = VM_Magic.getCallerFramePointer(currentFramePointer);

    if (! hasMoreFrames())
      throw new Error("Error during stack browsing");

    int cmid = VM_Magic.getCompiledMethodID(newFP);
    VM_CompiledMethod cm = VM_CompiledMethods.getCompiledMethod(cmid);
    while (cmid == INVISIBLE_METHOD_ID) {
      if (cm.getMethod().getDeclaringClass().isBridgeFromNative()) 
        newFP = VM_Runtime.unwindNativeStackFrame( newFP );
      else
        newFP = VM_Magic.getCallerFramePointer( newFP );
      newIP = VM_Magic.getReturnAddress( currentFramePointer );
      cmid = VM_Magic.getCompiledMethodID(newFP);
      cm = VM_CompiledMethods.getCompiledMethod(cmid);
    }

    currentFramePointer = newFP;
    currentInstructionPointer = newIP.diff( VM_Magic.objectAsAddress(cm.getInstructions()) );

    cm.getCompilerInfo().set( this, currentInstructionPointer );
  }

  boolean hasMoreFrames() {
    VM_Address newFP =
      VM_Magic.getCallerFramePointer( currentFramePointer );
    return newFP.toInt() !=  STACKFRAME_SENTINAL_FP;
  }

  void up() {
    if (! currentCompilerInfo.up(this)) upOneFrame();
  }

  void setBytecodeIndex(int bytecodeIndex) {
    currentBytecodeIndex = bytecodeIndex;
  }

  int getBytecodeIndex() {
    return currentBytecodeIndex;
  }

  void setMethod(VM_Method method) {
    currentMethod = method;
  }

  VM_Method getMethod() {
    return currentMethod;
  }

  void setCompilerInfo(VM_CompilerInfo info) {
    currentCompilerInfo = info;
  }

  VM_CompilerInfo getCompilerInfo() {
    return currentCompilerInfo;
  }

  VM_Class getCurrentClass() {
    return getMethod().getDeclaringClass();
  }

  ClassLoader getClassLoader() {
    return getCurrentClass().getClassLoader();
  }

  //-#if RVM_WITH_OPT_COMPILER
  void setInlineEncodingIndex(int index) {
    currentInlineEncodingIndex = index;
  }

  int getInlineEncodingIndex() {
    return currentInlineEncodingIndex;
  }
  //-#endif
}
