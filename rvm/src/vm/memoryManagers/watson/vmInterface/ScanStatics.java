/*
 * (C) Copyright IBM Corp. 2001
 */
//$Id$

package com.ibm.JikesRVM.memoryManagers.watson;

import com.ibm.JikesRVM.memoryManagers.vmInterface.*;

import com.ibm.JikesRVM.VM_Magic;
import com.ibm.JikesRVM.VM_Statics;
import com.ibm.JikesRVM.VM_Address;
import com.ibm.JikesRVM.VM;
import com.ibm.JikesRVM.VM_Constants;
import com.ibm.JikesRVM.VM_Thread;
import com.ibm.JikesRVM.VM_PragmaUninterruptible;

/**
 * Class that supports scanning statics (the JTOC) for references
 * and processing those references
 *
 * @author Stephen Smith
 * @author Perry Cheng
 */  
public class ScanStatics
  implements VM_Constants, VM_GCConstants {

  /**
   * Scan static variables (JTOC) for object references.
   * Executed by all GC threads in parallel, with each doing a portion of the JTOC.
   */
  static void scanStatics () throws VM_PragmaUninterruptible {
    int numSlots = VM_Statics.getNumberOfSlots();
    VM_Address slots    = VM_Magic.objectAsAddress(VM_Statics.getSlots());
    int chunkSize = 512;
    int slot, start, end, stride, slotAddress;
    VM_CollectorThread ct;

    stride = chunkSize * VM_CollectorThread.numCollectors();
    ct = VM_Magic.threadAsCollectorThread(VM_Thread.getCurrentThread());
    start = (ct.getGCOrdinal() - 1) * chunkSize;

    while ( start < numSlots ) {
      end = start + chunkSize;
      if (end > numSlots)
	end = numSlots;  // doing last segment of JTOC

      for ( slot=start; slot<end; slot++ ) {

	if ( ! VM_Statics.isReference(slot) ) continue;

	// slot contains a ref of some kind.  call collector specific
	// processPointerField, passing address of reference
	//
	VM_Allocator.processPtrField(slots.add(slot << LG_WORDSIZE));

      }  // end of for loop

      start = start + stride;

    }  // end of while loop

  }  // scanStatics


  static boolean validateRefs () throws VM_PragmaUninterruptible {
    int numSlots = VM_Statics.getNumberOfSlots();
    VM_Address slots    = VM_Magic.objectAsAddress(VM_Statics.getSlots());
    boolean result = true;
    for ( int slot=0; slot<numSlots; slot++ ) {
      if ( ! VM_Statics.isReference(slot) ) continue;
      VM_Address ref = VM_Magic.getMemoryAddress(slots.add(slot << LG_WORDSIZE));
      if ( (!ref.isZero()) && !VM_GCUtil.validRef(ref) ) {
	VM.sysWriteln("\nScanStatics.validateRefs:bad ref in slot ", slot); 
	VM.sysWriteHex(slot); VM.sysWrite(" ");
	VM_GCUtil.dumpRef(ref);
	result = false;
      }
    }  // end of for loop
    return result;
  }  // validateRefs


  static boolean validateRefs ( int depth ) throws VM_PragmaUninterruptible {
    int numSlots = VM_Statics.getNumberOfSlots();
    VM_Address slots    = VM_Magic.objectAsAddress(VM_Statics.getSlots());
    boolean result = true;
    for ( int slot=0; slot<numSlots; slot++ ) {
      if ( ! VM_Statics.isReference(slot) ) continue;
      VM_Address ref = VM_Address.fromInt(VM_Magic.getMemoryWord(slots.add(slot << LG_WORDSIZE)));
      if ( ! VM_ScanObject.validateRefs( ref, depth ) ) {
	VM.sysWriteln("ScanStatics.validateRefs: Bad Ref reached from JTOC slot ", slot);
	result = false;
      }
    }
    return result;
  }

  static void dumpRefs ( int start, int count ) throws VM_PragmaUninterruptible {
    int numSlots = VM_Statics.getNumberOfSlots();
    VM_Address slots    = VM_Magic.objectAsAddress(VM_Statics.getSlots());
    int last     = start + count;
    if (last > numSlots) last = numSlots;
    VM.sysWrite("Dumping Static References...\n");
      for ( int slot=start; slot<last; slot++ ) {
	if ( ! VM_Statics.isReference(slot) ) continue;
	VM_Address ref = VM_Address.fromInt(VM_Magic.getMemoryWord(slots.add(slot << LG_WORDSIZE)));
	if (!ref.isZero()) {
	  VM.sysWrite(slot); VM.sysWrite(" "); VM_GCUtil.dumpRef(ref);
	}
      }  // end of for loop
    VM.sysWrite("Done\n");
  }  // dumpRefs



}   // VM_ScanStatics
