/*
 * (C) Copyright IBM Corp. 2001
 */
//$Id$

package com.ibm.JikesRVM.memoryManagers.vmInterface;

import com.ibm.JikesRVM.memoryManagers.watson.*;

import com.ibm.JikesRVM.VM;
import com.ibm.JikesRVM.VM_Constants;
import com.ibm.JikesRVM.VM_Address;
import com.ibm.JikesRVM.VM_Processor;
import com.ibm.JikesRVM.VM_Method;
import com.ibm.JikesRVM.VM_CompiledMethod;
import com.ibm.JikesRVM.VM_CompiledMethods;
import com.ibm.JikesRVM.VM_Scheduler;
import com.ibm.JikesRVM.VM_Runtime;
import com.ibm.JikesRVM.VM_Magic;
import com.ibm.JikesRVM.VM_Thread;
import com.ibm.JikesRVM.VM_PragmaUninterruptible;

/**
 * Class that supports scanning thread stacks for references during
 * collections. References are located using GCMapIterators and are
 * inserted into a set of root locations.  Optionally, a set of 
 * interior pointer locations paired with the object is created.
 *
 * @author Stephen Smith
 * @author Perry Cheng
 */  
public class ScanStack implements VM_Constants, VM_GCConstants {

  // quietly validates each ref reported by map iterators
  static final boolean VALIDATE_STACK_REFS = true;

  // debugging options to produce printout during scanStack
  // MULTIPLE GC THREADS WILL PRODUCE SCRAMBLED OUTPUT so only
  // use these when running with PROCESSORS=1

  // includes in output a dump of the contents of each frame
  // forces DUMP_STACK_REFS & TRACE_STACKS on (ie. everything!!)
   static final boolean DUMP_STACK_FRAMES = false;

  // includes in output the refs reported by map iterators
  // forces TRACE_STACKS on 
  static final boolean DUMP_STACK_REFS = DUMP_STACK_FRAMES || false;

  // outputs names of methods as their frames are scanned
  static final boolean TRACE_STACKS = DUMP_STACK_REFS || false;

  static int stackDumpCount = 0;



  /**
   * Scans a threads stack during collection to find object references.
   * Locate and save locations containing roots and/or return addresses.
   * Include JNI ative frames.
   * <p>
   *
   * @param t              VM_Thread for the thread whose stack is being scanned
   * @param top_frame      address of stack frame at which to begin the scan
   * @param rootLocations  set to store addresses containing roots
   * @param relocate_code  set to store addresses containing return addresses (if null, skip)
   */
  public static void scanThreadStack (VM_Thread t, VM_Address top_frame, boolean relocate_code) throws VM_PragmaUninterruptible {

    Plan plan = VM_Processor.getCurrentProcessor().mmPlan;
    AddressSet rootLocations = plan.locations;
    AddressPairSet codeLocations = relocate_code ? collector.interiorLocations : null;

    VM_Address             ip, fp, prevFp;
    VM_CompiledMethod      compiledMethod;

    // Don't forget ip in hardwareExceptionRegisters, if it is in use.
    //
    if (codeLocations != null && t.hardwareExceptionRegisters.inuse) {
      ip = t.hardwareExceptionRegisters.ip;
      compiledMethod = VM_CompiledMethods.findMethodForInstruction(ip);
      if (VM.VerifyAssertions) VM._assert(compiledMethod != null);
      compiledMethod.setObsolete( false );
      VM_Address code = VM_Magic.objectAsAddress( compiledMethod.getInstructions() );
      codeLocations.push(code, t.hardwareExceptionRegisters.getIPLocation());
    }

    // get gc thread local iterator group from our VM_CollectorThread object
    VM_GCMapIteratorGroup iteratorGroup = collector.iteratorGroup;
    iteratorGroup.newStackWalk(t);
    
    if (TRACE_STACKS) VM_Scheduler.trace("VM_ScanStack", "Thread id", t.getIndex());

    if (!top_frame.isZero()) {
      prevFp = top_frame;
      // start scan at caller of passed in fp
      ip = VM_Magic.getReturnAddress(top_frame);
      fp = VM_Magic.getCallerFramePointer(top_frame);
    }
    else {
      prevFp = VM_Address.zero();
      // start scan using fp & ip in threads saved context registers
      ip = t.contextRegisters.getInnermostInstructionAddress();
      fp = t.contextRegisters.getInnermostFramePointer();
    }

    if (TRACE_STACKS) {
      VM.sysWrite("  top_frame = "); VM.sysWrite(top_frame); VM.sysWrite("\n");
      VM.sysWrite("         ip = "); VM.sysWrite(ip); VM.sysWrite("\n");
      VM.sysWrite("         fp = "); VM.sysWrite(fp); VM.sysWrite("\n");
      VM.sysWrite("  registers.ip = "); VM.sysWrite(t.contextRegisters.ip); VM.sysWrite("\n");
    }
    
    if (DUMP_STACK_REFS && t.jniEnv != null) t.jniEnv.dumpJniRefsStack();

    // It is possible to have a stack with only Native C frames, for a thread
    // that started in C, "attached" to the VM, made JNIFunction calls, and
    // has now returned back to C. In this case, scanStack should be called
    // with a "topJavaFrame" = 0. There may be references in the threads
    // JNIrefs side stack that need to be processed, below after the loop.

    if ( fp.NE(VM_Address.fromInt(STACKFRAME_SENTINAL_FP)) ) {

      if ( DUMP_STACK_REFS) {
	VM_Scheduler.dumpStack( ip, fp ); VM.sysWrite("\n");
      }

      // At start of loop:
      //   fp -> frame for method invocation being processed
      //   ip -> instruction pointer in the method (normally a call site)
      
      while (VM_Magic.getCallerFramePointer(fp).NE(VM_Address.fromInt(STACKFRAME_SENTINAL_FP))) {
	
	int compiledMethodId = VM_Magic.getCompiledMethodID(fp);
	
	// reflection and jni generate "invisible" transition frames with
	// method_id == 0.  reflections frames are skipped here. For PowerPC
	// JNI C to java transitions frames exist, but should be skipped at the
	// end of this loop during incrementing tothe "next" frame.
	//
	if (compiledMethodId == VM_Constants.INVISIBLE_METHOD_ID) {
	  
	  if (TRACE_STACKS) VM.sysWrite("\n--- METHOD --- <invisible method>\n");
	  
	  // skip "invisible" frame
	  prevFp = fp;
	  ip = VM_Magic.getReturnAddress(fp);
	  fp = VM_Magic.getCallerFramePointer(fp);
	  continue;
	}
	
	// following is for normal Java (and JNI Java to C transition) frames
	
	compiledMethod = VM_CompiledMethods.getCompiledMethod(compiledMethodId);
	compiledMethod.setObsolete( false );
	VM_Method method = compiledMethod.getMethod();
	
	// initialize MapIterator for this frame
	int offset = ip.diff(VM_Magic.objectAsAddress(compiledMethod.getInstructions()));
	VM_GCMapIterator iterator = iteratorGroup.selectIterator(compiledMethod);
	iterator.setupIterator(compiledMethod, offset, fp);
	
	if (TRACE_STACKS) {
	  VM_Scheduler.outputMutex.lock();
	  VM.sysWrite("\n--- METHOD --- ");
	  VM.sysWrite(method);
	  VM.sysWrite(" at offset ", offset);
	  VM.sysWrite(".\n");
	  VM_Scheduler.outputMutex.unlock();
	}
	if (DUMP_STACK_FRAMES) dumpStackFrame( fp, prevFp );
	
	if (DUMP_STACK_REFS) 
	  VM.sysWrite("--- Refs Reported By GCMap Iterator ---\n");
	
	if (false) {
	  VM.sysWrite("--- FRAME DUMP of METHOD ");
	  VM.sysWrite(method);
	  VM.sysWrite(" at offset ");
	  VM.sysWrite(offset,false);
	  VM.sysWrite(".--- \n");
	  VM.sysWrite(" fp = "); VM.sysWrite(fp);
	  VM.sysWrite(" ip = "); VM.sysWrite(ip); VM.sysWrite("\n");
	  dumpStackFrame( fp, prevFp );
	}
	
	// scan the map for this frame and process each reference
	//
	for (VM_Address refaddr = iterator.getNextReferenceAddress();  !refaddr.isZero();
	     refaddr = iterator.getNextReferenceAddress()) {
	  
	  if (VM.VerifyAssertions && VALIDATE_STACK_REFS) {
	    VM_Address ref = VM_Address.fromInt(VM_Magic.getMemoryWord(refaddr));
	    if (!VM_GCUtil.validRef(ref)) {
	      VM.sysWrite("\nInvalid ref reported while scanning stack\n");
	      VM.sysWrite("--- METHOD --- ");
	      VM.sysWrite(method);
	      VM.sysWrite(" at offset ");
	      VM.sysWrite(offset,false);
	      VM.sysWrite(".\n");
	      VM.sysWrite(" fp = "); VM.sysWrite(fp);
	      VM.sysWrite(" ip = "); VM.sysWrite(ip); VM.sysWrite("\n");
	      // dump out bad ref
	      VM.sysWrite(refaddr); VM.sysWrite(":"); VM_GCUtil.dumpRef(ref);
	      // dump out contents of frame
	      dumpStackFrame( fp, prevFp );
	      // dump stack starting at current frame
	      VM.sysWrite("\nDumping stack starting at frame with bad ref:\n");
	      VM_Scheduler.dumpStack( ip, fp );
	      // start stact starting at top
	      VM_Address top_ip = t.contextRegisters.getInnermostInstructionAddress();
	      VM_Address top_fp = t.contextRegisters.getInnermostFramePointer();
	      VM_Scheduler.dumpStack( top_ip, top_fp );
	      VM.sysFail("\n\nVM_ScanStack: Detected bad GC map; exiting RVM with fatal error");
	    }
	  }
	  if (DUMP_STACK_REFS) {
	    VM_Address ref = VM_Magic.getMemoryAddress(refaddr);
	    VM.sysWrite(refaddr); VM.sysWrite(":"); VM_GCUtil.dumpRef(ref);
	  }
	  
	  rootLocations.push(refaddr);
      }

      if (codeLocations != null) {
	// process the code object for the method, and if it is moved, relocate
	// the saved ip and any internal code pointers (JSR subroutine return
	// addresses. Note, the instructions field of the VM_Method object is
	// NOT updated at this time, so that other invocations of the same
	// method can use the original address to compute the relocation delta.
	//
	VM_Address code = VM_Magic.objectAsAddress( compiledMethod.getInstructions() );

	if (codeLocations != null) {
	  
	  code = VM_Magic.objectAsAddress( compiledMethod.getInstructions() );
	  if (prevFp.isZero()) {
	    // top-most stack frame, ip saved in threads context regs
	    codeLocations.push(code, t.contextRegisters.getIPLocation());
	  }
	  else {
	    codeLocations.push(code, VM_Magic.getReturnAddressLocation(prevFp));
	  }
	
	  // scan for internal code pointers in the stack frame and relocate
	  iterator.reset();
	  for (VM_Address retaddr = iterator.getNextReturnAddressAddress();  !retaddr.isZero();
	       retaddr = iterator.getNextReturnAddressAddress()) {
	    codeLocations.push(code, retaddr);
	  }
	}
      } 
      
      iterator.cleanupPointers();
      
      // if at a JNIFunction method, it is preceeded by native frames that must be skipped
      //
      if (compiledMethod.getMethod().getDeclaringClass().isBridgeFromNative()) {
	// skip native frames, stopping at last native frame PRECEEDING the
	// Java To C transition frame
	fp = VM_Runtime.unwindNativeStackFrame(fp);
	
	if ( TRACE_STACKS ) VM.sysWrite("scanStack skipping native C frames\n");
      }       
      
      // set fp & ip for next frame
      prevFp = fp;
      ip = VM_Magic.getReturnAddress(fp);
      fp = VM_Magic.getCallerFramePointer(fp);
      
      } // end of while != sentinel
      
    } // end of if (fp != STACKFRAME_SENTINAL_FP)
    
    // if we are scanning the stack of a thread that entered the VM via a createJVM
    // or attachJVM then the "bottom" of the stack had native C frames instead of the 
    // usual java frames.  The JNIEnv for the thread may still contain jniRefs that
    // have been returned to the native C code, but have not been reported for GC.
    // calling getNextReferenceAddress without first calling setup... will report the
    // remaining jniRefs in the current "frame" of the jniRefs stack.  (this should
    // be the bottom frame)
    //
    //-#if RVM_FOR_AIX
    VM_GCMapIterator iterator = iteratorGroup.getJniIterator();
    VM_Address refaddr =  iterator.getNextReferenceAddress();
    while( !refaddr.isZero() ) {
      rootLocations.push(refaddr);
      refaddr =  iterator.getNextReferenceAddress();
    }
    //-#else
    // exclude PPC FOR LINUX
    //-#endif
    
    if (TRACE_STACKS) VM.sysWrite("--- End Of Stack Scan ---\n");
    
  } // scanStack
  

  // dump contents of a stack frame. attempts to interpret each
  // word a an object reference
  //
  static void dumpStackFrame(VM_Address fp, VM_Address prevFp ) throws VM_PragmaUninterruptible {
    VM_Address start,end;
//-#if RVM_FOR_IA32
    if (prevFp.isZero()) {
      start = fp.sub(20*WORD_SIZE);
      VM.sysWrite("--- 20 words of stack frame with fp = ");
    }
    else {
      start = prevFp;    // start at callee fp
      VM.sysWrite("--- stack frame with fp = ");
    }
    VM.sysWrite(fp);
    VM.sysWrite(" ----\n");
    end = fp;            // end at fp
//-#endif
//-#if RVM_FOR_POWERPC
    VM.sysWrite("--- stack frame with fp = ");
    VM.sysWrite(fp);
    VM.sysWrite(" ----\n");
    start = fp;                         // start at fp
    end = VM_Magic.getMemoryAddress(fp);   // stop at callers fp
//-#endif

    for (VM_Address loc = start; loc.LE(end); loc = loc.add(WORD_SIZE)) {
      VM.sysWrite(loc.diff(start),false);
      VM.sysWrite(" ");
      VM.sysWrite(loc);
      VM.sysWrite(" ");
      VM_Address value = VM_Address.fromInt(VM_Magic.getMemoryWord(loc));
      VM.sysWrite(value);
      VM.sysWrite(" ");
      if ( VM_GCUtil.refInVM(value) && loc.NE(start) && loc.NE(end) )
	VM_GCUtil.dumpRef(value);
      else
	VM.sysWrite("\n");
    }
    VM.sysWrite("\n");
  }

}
