/*
 * (C) Copyright IBM Corp. 2001
 */
//$Id$
package com.ibm.JikesRVM.jni;

import com.ibm.JikesRVM.memoryManagers.vmInterface.VM_GCMapIterator;
import com.ibm.JikesRVM.*;

/**
 * Iterator for stack frames inserted at the transition from Java to
 * JNI Native C.  It will report JREFs associated with the executing
 * C frames which are in the "JREFs stack" attached to the executing
 * Threads JNIEnvironment.  It will update register location addresses
 * for the non-votatile registers to point to the register save area
 * in the transition frame.
 *
 * If GC happens, the saved non-volatile regs may get modified (ex. a ref
 * to a live object that gets moved), and a restore flag in the frame is
 * set to cause the returning Native code to restore those registers from
 * this save area.  If GC does not occur, the Native C code has restored
 * these regs, and the transition return code does not do the restore.
 *
 * @author Steve Smith
 */
public final class VM_JNIGCMapIterator extends VM_GCMapIterator 
  implements VM_BaselineConstants,
             VM_JNIStackframeLayoutConstants,
             VM_Uninterruptible {

  // non-volitile regs are saved at the end of the transition frame,
  // after the saved JTOC and SP, and preceeded by a GC flag.
  //
  // JNI Java to Native C transition frame...
  //
  //     <-- | saved FP       |  <- this.framePtr
  //     |   |    ...         |
  //     |   |    ...         |
  //     |   | GC flag        |
  //     |   | saved affinity |
  //     |   | proc reg       |
  //     |   | non vol 17     |
  //     |   |    ...         |
  //     |   | non vol 31     |
  //     |   | saved SP       |
  //     |   | saved JTOC     |
  //     --> |                |  <- callers FP
  //
  // The following constant is the offset from the callers FP to
  // the GC flag at the beginning of this area.  
  //

  public static int verbose = 0;
  
   // additional instance fields added by this subclass of VM_GCMapIterator
  private VM_AddressArray jniRefs;
  private int jniNextRef;
  private int jniFramePtr;
  private VM_Address jniSavedReturnAddr;
  
  public VM_JNIGCMapIterator(VM_WordArray registerLocations) {
     this.registerLocations = registerLocations;
   }

  // Override newStackWalk() in parent class VM_GCMapIterator to
  // initialize iterator for scan of JNI JREFs stack of refs
  // Taken:    thread
  // Returned: nothing
  //
  public void newStackWalk(VM_Thread thread) {
    super.newStackWalk(thread);   // sets this.thread
    VM_JNIEnvironment env = this.thread.getJNIEnv();
    // the "primordial" thread, created by JDK in the bootimage, does not have
    // a JniEnv object, all threads created by the VM will.
    if (env != null) {
      this.jniRefs = env.JNIRefs;
      this.jniNextRef = env.JNIRefsTop;
      this.jniFramePtr = env.JNIRefsSavedFP;
    }
  }

  public void setupIterator(VM_CompiledMethod compiledMethod, 
                            int instructionOffset, 
                            VM_Address framePtr) { 
    this.framePtr = framePtr;
    // processore reg (R16) was saved in reg save area at offset -72 
    // from callers frameptr, and after GC will be used to set 
    // processor reg upon return to java.  it must be reported
    // so it will be relocated, if necessary
    //
    VM_Address callers_fp = VM_Magic.getMemoryAddress(this.framePtr);
    //-#if RVM_FOR_AIX
    jniSavedReturnAddr       = callers_fp.sub(JNI_PROLOG_RETURN_ADDRESS_OFFSET);
    //-#endif
    //-#if RVM_FOR_LINUX || RVM_FOR_OSX
    // ScanThread calls getReturnAddressLocation() to get this stack frame
    // it is already processed
    jniSavedReturnAddr       = VM_Address.zero();
    //-#endif

    // set the GC flag in the Java to C frame to indicate GC occurred
    // this forces saved non volatile regs to be restored from save area
    // where those containing refs have been relocated if necessary
    //
    VM_Magic.setMemoryInt(callers_fp.sub(JNI_GC_FLAG_OFFSET), 1);
  }
  
  // return (address of) next ref in the current "frame" on the
  // threads JNIEnvironment stack of refs         
  // When at the end of the current frame, update register locations to point
  // to the non-volatile registers saved in the JNI transition frame.
  //
  public VM_Address getNextReferenceAddress() {
    if (jniNextRef > jniFramePtr) {
      VM_Address ref_address = VM_Magic.objectAsAddress(jniRefs).add(jniNextRef);
      jniNextRef = jniNextRef - BYTES_IN_ADDRESS;
      if (verbose > 0) VM.sysWriteln("JNI iterator returning JNI ref: ", ref_address);
      return ref_address;
    }

    // jniNextRef -> savedFramePtr for another "frame" of refs for another
    // sequence of Native C frames lower in the stack, or to 0 if this is the
    // last jni frame in the JNIRefs stack.  If more frames, initialize for a
    // later scan of those refs.
    //
    if (jniFramePtr > 0) {
      jniFramePtr = jniRefs.get(jniFramePtr >> LOG_BYTES_IN_ADDRESS).toInt();
      jniNextRef = jniNextRef - BYTES_IN_ADDRESS ;
    }
    
    // set register locations for non-volatiles to point to registers saved in
    // the JNI transition frame at a fixed negative offset from the callers FP.
    VM_Address registerLocation = VM_Magic.getMemoryAddress(this.framePtr).sub(JNI_RVM_NONVOLATILE_OFFSET);

    for (int i = LAST_NONVOLATILE_GPR; i >= FIRST_NONVOLATILE_GPR - 1; --i) {
      registerLocations.set(i, registerLocation);
      registerLocation = registerLocation.sub(BYTES_IN_ADDRESS);
    }

    if (verbose > 1) VM.sysWriteln("JNI iterator returning 0");
    return VM_Address.zero();  // no more refs to report
  }

  public VM_Address getNextReturnAddressAddress() {
    if ( !jniSavedReturnAddr.isZero() ) {
      VM_Address ref_address = jniSavedReturnAddr;
      jniSavedReturnAddr = VM_Address.zero();
      if (verbose > 0) {
        VM.sysWriteln("JNI getNextReturnAddressAddress returning ", ref_address);
      }
      return ref_address;
    }

    return VM_Address.zero();
  }

  public void reset() {}
  
  public void cleanupPointers() {}
  
  public int getType() {
    return VM_CompiledMethod.JNI;
  }
}
