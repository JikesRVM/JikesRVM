/*
 * (C) Copyright IBM Corp. 2001
 */
//$Id$

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
final class VM_JNIGCMapIterator extends VM_GCMapIterator implements VM_BaselineConstants 
   {

   // non-volitile regs are saved at the end of the transition frame,
   // after the saved JTOC and SP, and preceeded by a GC flag.
   //
   // JNI Java to Native C transition frame...
   //
   //	 <-- | saved FP       |  <- this.framePtr
   //	 |   |    ...         |
   //	 |   |    ...         |
   //	 |   | GC flag        |
   //	 |   | saved affinity |
   //	 |   | proc reg       |
   //	 |   | non vol 17     |
   //	 |   |    ...         |
   //	 |   | non vol 31     |
   //	 |   | saved SP       |
   //	 |   | saved JTOC     |
   //	 --> |                |  <- callers FP
   //
   // The following constant is the offset from the callers FP to
   // the GC flag at the beginning of this area.  
   //

   // additional instance fields added by this subclass of VM_GCMapIterator
   int[]  jniRefs;
   int jniNextRef;
   int jniFramePtr;
   VM_Address jniSavedProcessorRegAddr;
   VM_Address jniSavedReturnAddr;

   VM_JNIGCMapIterator(int[] registerLocations) {
       this.registerLocations = registerLocations;
   }

  // Override newStackWalk() in parent class VM_GCMapIterator to
  // initialize iterator for scan of JNI JREFs stack of refs
  // Taken:    thread
  // Returned: nothing
  //
  void newStackWalk(VM_Thread thread) {
     super.newStackWalk(thread);   // sets this.thread
     VM_JNIEnvironment env = this.thread.getJNIEnv();
     // the "primordial" thread, created by JDK in the bootimage, does not have
     // a JniEnv object, all threads created by the VM will.
     if (env != null) {
	 this.jniRefs = env.JNIRefs;
	 this.jniNextRef = env.JNIRefsTop;
	 this.jniFramePtr = env.JNIRefsSavedFP;
	 this.jniSavedProcessorRegAddr = VM_Address.zero();     // necessary so getNextRefAddr() can be used to report
	                                                      // jniRefs in a "frame", without calling setup.  
     }
     }

   void setupIterator(VM_CompiledMethod compiledMethod, int instructionOffset, 
		      VM_Address framePtr) { //- implements VM_GCMapIterator
      this.framePtr = framePtr;
      // processore reg (R16) was saved in reg save area at offset -72 
      // from callers frameptr, and after GC will be used to set 
      // processor reg upon return to java.  it must be reported
      // so it will be relocated, if necessary
      //
      VM_Address callers_fp = VM_Address.fromInt(VM_Magic.getMemoryWord(this.framePtr));
      jniSavedProcessorRegAddr = callers_fp.sub(JNI_PR_OFFSET);
      jniSavedReturnAddr       = callers_fp.sub(JNI_PROLOG_RETURN_ADDRESS_OFFSET);

      // set the GC flag in the Java to C frame to indicate GC occurred
      // this forces saved non volatile regs to be restored from save area
      // where those containing refs have been relocated if necessary
      //
      VM_Magic.setMemoryWord(callers_fp.sub(JNI_GC_FLAG_OFFSET), 1);
      }

   // return (address of) next ref in the current "frame" on the
   // threads JNIEnvironment stack of refs	  
   // When at the end of the current frame, update register locations to point
   // to the non-volatile registers saved in the JNI transition frame.
   //
   VM_Address getNextReferenceAddress() { //- implements VM_GCMapIterator
      int nextFP;
      VM_Address ref_address;

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

      // jniNextRef -> savedFramePtr for another "frame" of refs for another
      // sequence of Native C frames lower in the stack, or to 0 if this is the
      // last jni frame in the JNIRefs stack.  If more frames, initialize for a
      // later scan of those refs.
      //
      if ( jniFramePtr > 0) {
	  jniFramePtr = jniRefs[jniFramePtr >> 2];
	  jniNextRef = jniNextRef - 4 ;
      }

      // set register locations for non-volatiles to point to registers saved in
      // the JNI transition frame at a fixed negative offset from the callers FP.

      int registerLocation = VM_Magic.getMemoryWord(this.framePtr) - JNI_RVM_NONVOLATILE_OFFSET;

      for (int i = LAST_NONVOLATILE_GPR; i >= FIRST_NONVOLATILE_GPR - 1; --i) {
         registerLocations[i] = registerLocation;
         registerLocation -= 4;
      }


      return VM_Address.zero();  // no more refs to report
      }

   VM_Address getNextReturnAddressAddress() { //- implements VM_GCMapIterator

       VM_Address  ref_address;
       if ( !jniSavedReturnAddr.isZero() ) {
          ref_address = jniSavedReturnAddr;
          jniSavedReturnAddr = VM_Address.zero();
          return ref_address;
      }

      return VM_Address.zero();
      }

   void reset() {}

   void cleanupPointers() {}

   int getType() {
       return VM_GCMapIterator.JNI;
   }
}
