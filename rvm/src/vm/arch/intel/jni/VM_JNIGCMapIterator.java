/*
 * (C) Copyright IBM Corp. 2001
 */
//$Id$
package com.ibm.JikesRVM;

import com.ibm.JikesRVM.memoryManagers.vmInterface.VM_GCMapIterator;

/**
 * Iterator for stack frames inserted at the transition from Java to
 * JNI Native C.  It will report JREFs associated with the executing
 * C frames which are in the "JREFs stack" attached to the executing
 * Threads JNIEnvironment.  It will update register location addresses
 * for the non-volatile registers to point to the registers saved
 * in the transition frame.
 *
 * @see VM_JNICompiler
 * @author Steve Smith
 */
public final class VM_JNIGCMapIterator extends VM_GCMapIterator
    implements VM_BaselineConstants, VM_Uninterruptible {

  // Java to Native C transition frame...(see VM_JNICompiler)
  //
  //  0   	+ saved FP   + <---- FP for Jave to Native C glue frame
  // -4   	| methodID   |
  // -8   	| saved EDI  |  non-volatile GPR (JTOC for baseline callers or ? for opt callers)
  // -C  	| saved EBX  |  non-volatile GPR  
  // -10  	| saved EBP  |  non-volatile GPR  
  // -14        | returnAddr |  (for return from OutOfLineMachineCode)
  // -18        | saved PR   |  
  // -1C	| arg n-1    |  reordered arguments to native method
  // -20	|  ...       |  ...
  // -24	| arg 1      |  ...
  // -28  	| arg 0      |  ...
  // -2C  	| class/obj  |  required 2nd argument to all native methods
  // -30  	| jniEnv     |  required 1st argument to all native methods
  // -34   	| returnAddr |  return address pushed by call to native method  
  //    	+ saved FP   +  <---- FP for called native method  

  // additional instance fields added by this subclass of VM_GCMapIterator
  int[]         jniRefs;
  int           jniNextRef;
  int           jniFramePtr;
  VM_Address    jniSavedProcessorRegAddr;     // -> saved PR reg
  VM_Address    jniSavedReturnAddr;           // -> return addr in generated transition prolog
  
  public VM_JNIGCMapIterator(int[] registerLocations) {
    this.registerLocations = registerLocations;
  }
  
  // Override newStackWalk() in parent class VM_GCMapIterator to
  // initialize iterator for scan of JNI JREFs stack of refs
  // Taken:    thread
  // Returned: nothing
  //
  public void newStackWalk(VM_Thread thread) {
    super.newStackWalk(thread);   // sets this.thread, inits registerLocations[]
    VM_JNIEnvironment env = this.thread.getJNIEnv();
    // the "primordial" thread, created by JDK in the bootimage, does not have
    // a JniEnv object, all threads created by the VM will.
    if (env != null) {
      this.jniRefs = env.refsArray();
      this.jniNextRef = env.refsTop();
      this.jniFramePtr = env.savedRefsFP();  
      this.jniSavedProcessorRegAddr = VM_Address.zero();  
                                    // necessary so getNextRefAddr() can be used to report
                                    // jniRefs in a "frame", without calling setup. 
    } 
  }
     
  public void setupIterator(VM_CompiledMethod compiledMethod, int instructionOffset, VM_Address framePtr) {
    this.framePtr = framePtr;

    // processor reg (PR) was saved at JNI_PR_OFFSET, and will be used to
    // set processor reg upon return to java.  it must be reported during
    // GC so it will be relocated, if necessary.
    //
    jniSavedProcessorRegAddr = framePtr.add(VM_JNICompiler.JNI_PR_OFFSET);

    // return address into generated prolog must be relocated if the code object
    // for that prolog/epilog is moved by GC
    jniSavedReturnAddr       = framePtr.add(VM_JNICompiler.JNI_RETURN_ADDRESS_OFFSET);

  } //- implements VM_GCMapIterator
   
  // return (address of) next ref in the current "frame" on the
  // threads JNIEnvironment stack of refs	  
  // When at the end of the current frame, update register locations to point
  // to the non-volatile registers saved in the JNI transition frame.
  //
  public VM_Address getNextReferenceAddress() {
    int nextFP;
    VM_Address ref_address;

    // first report jni refs in the current frame in the jniRef side stack
    // until all in the frame are reported
    //
    if ( jniNextRef > jniFramePtr ) {
      ref_address = VM_Magic.objectAsAddress(jniRefs).add(jniNextRef);
      jniNextRef = jniNextRef - 4;
      return ref_address;
    }

    // report location of saved processor reg in the Java to C frame
    if ( !jniSavedProcessorRegAddr.isZero() ) {
      ref_address = jniSavedProcessorRegAddr;
      jniSavedProcessorRegAddr = VM_Address.zero();
      return ref_address;
    }

    // no more refs to report, before returning 0, setup for processing
    // the next jni frame, if any

    // jniNextRef -> savedFramePtr for another "frame" of refs for another
    // sequence of Native C frames lower in the stack, or to 0 if this is the
    // last jni frame in the JNIRefs stack.  If more frames, initialize for a
    // later scan of those refs.
    //
    if ( jniFramePtr > 0 ) {
      jniFramePtr = jniRefs[jniFramePtr >> 2];
      jniNextRef = jniNextRef - 4;
    }

    // set register locations for non-volatiles to point to registers saved in
    // the JNI transition frame at a fixed negative offset from the callers FP.
    // the save non-volatiles are EBX, EBP,  and EDI (JTOC)
    //
    registerLocations[JTOC] = framePtr.add(VM_JNICompiler.EDI_SAVE_OFFSET).toInt();
    registerLocations[EBX]  = framePtr.add(VM_JNICompiler.EBX_SAVE_OFFSET).toInt();
    registerLocations[EBP]  = framePtr.add(VM_JNICompiler.EBP_SAVE_OFFSET).toInt();

    return VM_Address.zero();  // no more refs to report
  } //- implements VM_GCMapIterator
  
  public VM_Address getNextReturnAddressAddress() {
    VM_Address ref_address;
    if ( !jniSavedReturnAddr.isZero() ) {
      ref_address = jniSavedReturnAddr;
      jniSavedReturnAddr = VM_Address.zero();
      return ref_address;
    }
    return VM_Address.zero();
  } //- implements VM_GCMapIterator
  
  public void reset() {
  } //- implements VM_GCMapIterator
  
  public void cleanupPointers() {
  } //- implements VM_GCMapIterator
  
  public int getType() {
    return VM_CompiledMethod.JNI;
  } //- implements VM_GCMapIterator
   
}
