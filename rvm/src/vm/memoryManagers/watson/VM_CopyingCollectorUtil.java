/*
 * (C) Copyright IBM Corp. 2001
 */
//$Id$

/**
 * Shared utility code for copying collectors.
 * Code originally written by Steve Smith;
 * refactored and moved here by Dave Grove so
 * it could be shared by all copying collectors.
 * 
 * @author Steve Smith
 */
class VM_CopyingCollectorUtil implements VM_Constants, 
					 VM_GCConstants, 
					 VM_Uninterruptible {

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
  static VM_Address copyAndScanObject(VM_Address fromRef, boolean scan) {
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
	VM.assert(false);  
      }
      return toRef;
    }

    // We are the GC thread that must copy the object, so do it.
    Object[] tib = VM_ObjectModel.getTIB(fromObj);
    VM_Type type = VM_Magic.objectAsType(tib[TIB_TYPE_INDEX]);
    if (VM_Allocator.writeBarrier) {
      forwardingPtr |= VM_AllocatorHeader.GC_BARRIER_BIT_MASK;
    }
    if (VM.VerifyAssertions) VM.assert(VM_GCUtil.validObject(type));
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
      if (arrayType == VM_Type.CodeType) {
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







}

