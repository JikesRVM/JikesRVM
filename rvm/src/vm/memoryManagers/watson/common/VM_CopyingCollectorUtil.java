/*
 * (C) Copyright IBM Corp. 2001
 */
//$Id$

package com.ibm.JikesRVM.memoryManagers.watson;

import  com.ibm.JikesRVM.memoryManagers.vmInterface.*;
import com.ibm.JikesRVM.classloader.*;

import com.ibm.JikesRVM.VM_Thread;
import com.ibm.JikesRVM.VM_Magic;
import com.ibm.JikesRVM.VM_ObjectModel;
import com.ibm.JikesRVM.VM;
import com.ibm.JikesRVM.VM_Address;
import com.ibm.JikesRVM.VM_Scheduler;
import com.ibm.JikesRVM.VM_Memory;
import com.ibm.JikesRVM.VM_Constants;
import com.ibm.JikesRVM.VM_PragmaUninterruptible;

/**
 * Shared utility code for copying collectors.
 * Code originally written by Steve Smith;
 * refactored and moved here by Dave Grove so
 * it could be shared by all copying collectors.
 * 
 * @author Steve Smith
 */
class VM_CopyingCollectorUtil implements VM_Constants, 
					 VM_GCConstants {


//-#if RVM_WITH_NONCOPYING_GC
//-#else
  /**
   * Processes live objects that need to be marked, copied and
   * forwarded during collection.  Returns the new address of the object
   * in the mature space.  If the object was not previously marked, then the
   * invoking collector thread will do the copying and optionally enqueue the
   * copied object on the work queue of objects to be scanned.
   *
   * @param fromObj object to be processed
   * @param scan should the object be scanned?
   * @return the address of the Object in mature space
   */
  static VM_Address copyAndScanObject(VM_Address fromRef, boolean scan) throws VM_PragmaUninterruptible {
    Object fromObj = VM_Magic.addressAsObject(fromRef);
    VM_Address toRef;
    Object toObj;
    int forwardingPtr = VM_AllocatorHeader.attemptToForward(fromObj);
    VM_Magic.isync();   // prevent instructions moving infront of attemptToForward

    if (VM_AllocatorHeader.stateIsForwardedOrBeingForwarded(forwardingPtr)) {
      // if isBeingForwarded, object is being copied by another GC thread; 
      // wait (should be very short) for valid ptr to be set
      if (VM_GCStatistics.COUNT_COLLISIONS && VM_AllocatorHeader.stateIsBeingForwarded(forwardingPtr)) {
	VM_GCStatistics.collisionCount++;
      }
      while (VM_AllocatorHeader.stateIsBeingForwarded(forwardingPtr)) {
	forwardingPtr = VM_AllocatorHeader.getForwardingWord(fromObj);
      }
      VM_Magic.isync();  // prevent following instructions from being moved in front of waitloop
      toRef = VM_Address.fromInt(forwardingPtr & ~VM_AllocatorHeader.GC_FORWARDING_MASK);
      toObj = VM_Magic.addressAsObject(toRef);
      if (VM.VerifyAssertions && !(VM_AllocatorHeader.stateIsForwarded(forwardingPtr) && VM_GCUtil.validRef(toRef))) {
	VM_Scheduler.traceHex("copyAndScanObject", "invalid forwarding ptr =",forwardingPtr);
	VM._assert(false);  
      }
      return toRef;
    }

    // We are the GC thread that must copy the object, so do it.
    Object[] tib = VM_ObjectModel.getTIB(fromObj);
    VM_Type type = VM_Magic.objectAsType(tib[TIB_TYPE_INDEX]);
    if (VM_Allocator.writeBarrier) {
      forwardingPtr |= VM_AllocatorHeader.GC_BARRIER_BIT_MASK;
    }
    if (VM.VerifyAssertions) VM._assert(VM_GCUtil.validObject(type));
    int numBytes;
    if (type.isClassType()) {
      VM_Class classType = type.asClass();
      numBytes = VM_ObjectModel.bytesRequiredWhenCopied(fromObj, classType);
      VM_Address region = VM_Allocator.gc_getMatureSpace(numBytes);
      toObj = VM_ObjectModel.moveObject(region, fromObj, numBytes, classType, forwardingPtr);
      toRef = VM_Magic.objectAsAddress(toObj);
    } else {
      VM_Array arrayType = type.asArray();
      int numElements = VM_Magic.getArrayLength(fromObj);
      numBytes = VM_ObjectModel.bytesRequiredWhenCopied(fromObj, arrayType, numElements);
      VM_Address region = VM_Allocator.gc_getMatureSpace(numBytes);
      toObj = VM_ObjectModel.moveObject(region, fromObj, numBytes, arrayType, forwardingPtr);
      toRef = VM_Magic.objectAsAddress(toObj);
      if (arrayType == VM_Type.InstructionArrayType) {
	// sync all moved code arrays to get icache and dcache in sync immediately.
	int dataSize = numBytes - VM_ObjectModel.computeHeaderSize(VM_Magic.getObjectType(toObj));
	VM_Memory.sync(toRef, dataSize);
      }
    }

    VM_GCStatistics.profileCopy(fromObj, numBytes, tib);    
    
    if (VM_Allocator.writeBarrier) {
      // make it safe for write barrier to access barrier bit non-atmoically
      VM_ObjectModel.initializeAvailableByte(toObj); 
    }

    VM_Magic.sync(); // make changes viewable to other processors 
    
    VM_AllocatorHeader.setForwardingPointer(fromObj, toObj);

    if (scan) VM_GCWorkQueue.putToWorkBuffer(toRef);
    return toRef;
  }
//-#endif

  /**
   * Scans all threads in the VM_Scheduler threads array.  A threads stack
   * will be copied if necessary and any interior addresses relocated.
   * Each threads stack is scanned for object references, which will
   * becomes Roots for a collection. <p>
   * 
   * All collector threads execute here in parallel, and compete for
   * individual threads to process.  Each collector thread processes
   * its own thread object and stack.
   * 
   * @param fromHeap the heap that we are copying objects out of.
   */
  static void scanThreads (VM_Heap fromHeap)  throws VM_PragmaUninterruptible {
    // get ID of running GC thread
    int myThreadId = VM_Thread.getCurrentThread().getIndex();
    int[] oldstack;
    
    for (int i=0; i<VM_Scheduler.threads.length; i++ ) {
      VM_Thread t = VM_Scheduler.threads[i];
      VM_Address ta = VM_Magic.objectAsAddress(t);
      
      if (t == null) {
	// Nothing to do (no thread object...)
      } else if (i == myThreadId) {  
	// let each GC thread scan its own thread object

	// GC threads are assumed not to have native processors.  if this proves
	// false, then we will have to deal with its write buffers
	if (VM.VerifyAssertions) VM._assert(t.nativeAffinity == null);
	
	// all threads should have been copied out of fromspace earlier
	if (VM.VerifyAssertions) VM._assert(fromHeap == null || !fromHeap.refInHeap(ta));
	
	if (VM.VerifyAssertions) oldstack = t.stack;    // for verifying gc stacks not moved
	VM_ScanObject.scanObjectOrArray(t);
	if (VM.VerifyAssertions) VM._assert(oldstack == t.stack);
	
	if (t.jniEnv != null) VM_ScanObject.scanObjectOrArray(t.jniEnv);

	VM_ScanObject.scanObjectOrArray(t.contextRegisters);

	VM_ScanObject.scanObjectOrArray(t.hardwareExceptionRegisters);

	ScanStack.scanThreadStack(t, VM_Address.zero(), true);
	ScanStack.processRoots();

      } else if (t.isGCThread && (VM_Magic.threadAsCollectorThread(t).gcOrdinal > 0)) {
	// skip other collector threads participating (have ordinal number) in this GC
      } else if (VM_GCLocks.testAndSetThreadLock(i)) {
	// have thread to be processed, compete for it with other GC threads
	
	if (VM_Allocator.verbose >= 3) VM.sysWriteln("    Processing mutator thread ",i);
	
	// all threads should have been copied out of fromspace earlier
	if (VM.VerifyAssertions) VM._assert(fromHeap == null || !fromHeap.refInHeap(ta));
	
	// scan thread object to force "interior" objects to be copied, marked, and
	// queued for later scanning.
	oldstack = t.stack;    // remember old stack address before scanThread
	VM_ScanObject.scanObjectOrArray(t);
	
	// if stack moved, adjust interior stack pointers
	if (oldstack != t.stack) {
	  if (VM_Allocator.verbose >= 3) VM.sysWriteln("    Adjusting mutator stack ",i);
	  t.fixupMovedStack(VM_Magic.objectAsAddress(t.stack).diff(VM_Magic.objectAsAddress(oldstack)).toInt());
	}
	
	// the above scanThread(t) will have marked and copied the threads JNIEnvironment object,
	// but not have scanned it (likely queued for later scanning).  We force a scan of it now,
	// to force copying of the JNI Refs array, which the following scanStack call will update,
	// and we want to ensure that the updates go into the "new" copy of the array.
	//
	if (t.jniEnv != null) VM_ScanObject.scanObjectOrArray(t.jniEnv);
	
	// Likewise we force scanning of the threads contextRegisters, to copy 
	// contextRegisters.gprs where the threads registers were saved when it yielded.
	// Any saved object references in the gprs will be updated during the scan
	// of its stack.
	//
	VM_ScanObject.scanObjectOrArray(t.contextRegisters);

	VM_ScanObject.scanObjectOrArray(t.hardwareExceptionRegisters);
	
	// all threads in "unusual" states, such as running threads in
	// SIGWAIT (nativeIdleThreads, nativeDaemonThreads, passiveCollectorThreads),
	// set their ContextRegisters before calling SIGWAIT so that scans of
	// their stacks will start at the caller of SIGWAIT
	//
	// fp = -1 case, which we need to add support for again
	// this is for "attached" threads that have returned to C, but
	// have been given references which now reside in the JNIEnv sidestack

	if (VM_Allocator.verbose >= 3) VM.sysWriteln("    Scanning stack for thread ",i);
	ScanStack.scanThreadStack(t, VM_Address.zero(), true);
	ScanStack.processRoots();
      } 
    } 
  }
  

}

