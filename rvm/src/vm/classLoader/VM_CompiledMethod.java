/*
 * (C) Copyright IBM Corp. 2001
 */
//$Id$

/**
 * A method that has been compiled into machine code by one of our compilers.
 * We implement VM_SynchronizedObject because we need to synchronized 
 * on the VM_CompiledMethod object as part of the invalidation protocol.
 * 
 * @author Bowen Alpern
 * @author Derek Lieber
 */
class VM_CompiledMethod implements VM_Uninterruptible, VM_SynchronizedObject {

  //-----------//
  // interface //
  //-----------//
   
  final int getId() { 
    return id;           
  }
  
  final VM_Method getMethod() { 
    return method;       
  }

  final INSTRUCTION[] getInstructions() { 
    if (VM.VerifyAssertions) VM.assert((state & COMPILED) != 0);
    return instructions; 
  }

  final VM_CompilerInfo getCompilerInfo() { 
    if (VM.VerifyAssertions) VM.assert((state & COMPILED) != 0);
    return compilerInfo; 
  }

  final void compileComplete(VM_CompilerInfo info, INSTRUCTION[] code) {
    compilerInfo = info;
    instructions = code;
    state |= COMPILED;
  }

  final void setInvalid() {
    state |= INVALID;
  }

  final void setObsolete(boolean sense) {
    if (sense) {
      state |= OBSOLETE;
    } else {
      state &= ~OBSOLETE;
    }
  }

  final boolean isCompiled() {
    return (state & COMPILED) != 0;
  }

  final boolean isInvalid() {
    return (state & INVALID) != 0;
  }

  final boolean	isObsolete() { 
    return (state & OBSOLETE) != 0;
  }
   
  //----------------//
  // implementation //
  //----------------//

  private static final int VACANT     = 0x00;
  private static final int COMPILED   = 0x01;
  private static final int INVALID    = 0x02;
  private static final int OBSOLETE   = 0x80;

  private int             id;           // index of this compiled method in VM_ClassLoader.compiledMethods[]
  private VM_Method       method;       // method
  private INSTRUCTION[]   instructions; // machine code for that method
  private VM_CompilerInfo compilerInfo; // tables and maps for handling exceptions, gc, etc.
  private int             state;        // state of the compiled method.
   
  VM_CompiledMethod(int id, VM_Method method) {
    this.id     = id;
    this.method = method;
    this.state  = VACANT;
  }
}
