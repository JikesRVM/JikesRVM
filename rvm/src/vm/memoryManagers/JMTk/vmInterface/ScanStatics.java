/*
 * (C) Copyright IBM Corp. 2001
 */
//$Id$

package org.mmtk.vm;

import org.mmtk.utility.deque.AddressDeque;
import com.ibm.JikesRVM.memoryManagers.mmInterface.VM_CollectorThread;
import org.vmmagic.unboxed.*;
import org.vmmagic.pragma.*;

import com.ibm.JikesRVM.VM_Statics;
import com.ibm.JikesRVM.VM;
import com.ibm.JikesRVM.VM_Magic;
import com.ibm.JikesRVM.VM_Constants;
import com.ibm.JikesRVM.VM_Thread;

/**
 * Class that determines all JTOC slots (statics) that hold references
 *
 * @author Perry Cheng
 */  
public class ScanStatics
  implements Constants, VM_Constants {

  /**
   * Scan static variables (JTOC) for object references.
   * Executed by all GC threads in parallel, with each doing a portion of the JTOC.
   */
  public static void scanStatics (AddressDeque rootLocations) throws UninterruptiblePragma {

    int numSlots = VM_Statics.getNumberOfSlots();
    Address slots = VM_Statics.getSlots();
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
        rootLocations.push(slots.add(slot << LOG_BYTES_IN_INT));

      }  // end of for loop

      start = start + stride;

    }  // end of while loop

  }  // scanStatics

  /*
  static boolean validateRefs () throws UninterruptiblePragma {
    int numSlots = VM_Statics.getNumberOfSlots();
    Address slots = VM_Statics.getSlots();
    boolean result = true;
    for ( int slot=0; slot<numSlots; slot++ ) {
      if ( ! VM_Statics.isReference(slot) ) continue;
      Address ref = slots.add(slot << LOG_BYTES_IN_ADDRESS).loadAddress();
      if ( (!ref.isZero()) && !VM_GCUtil.validRef(ref) ) {
        VM.sysWrite("\nScanStatics.validateRefs:bad ref in slot "); VM.sysWrite(slot,false); VM.sysWrite("\n");
        VM.sysWriteHex(slot); VM.sysWrite(" ");
        VM_GCUtil.dumpRef(ref);
        result = false;
      }
    }  // end of for loop
    return result;
  }  // validateRefs


  static boolean validateRefs ( int depth ) throws UninterruptiblePragma {
    int numSlots = VM_Statics.getNumberOfSlots();
    Address slots = VM_Statics.getSlots();
    boolean result = true;
    for ( int slot=0; slot<numSlots; slot++ ) {
      if ( ! VM_Statics.isReference(slot) ) continue;
      Address ref = slots.loadAddress(slot << LOG_BYTES_IN_ADDRESS);
      if ( ! VM_ScanObject.validateRefs( ref, depth ) ) {
        VM.sysWrite("ScanStatics.validateRefs: Bad Ref reached from JTOC slot ");
        VM.sysWrite(slot,false);
        VM.sysWrite("\n");
        result = false;
      }
    }
    return result;
  }

  static void dumpRefs ( int start, int count ) throws UninterruptiblePragma {
    int numSlots = VM_Statics.getNumberOfSlots();
    Address slots = VM_Statics.getSlots();
    int last     = start + count;
    if (last > numSlots) last = numSlots;
    VM.sysWrite("Dumping Static References...\n");
      for ( int slot=start; slot<last; slot++ ) {
        if ( ! VM_Statics.isReference(slot) ) continue;
        Address ref = slots.loadAddress(slot << LOG_BYTES_IN_ADDRESS);
        if (!ref.isZero()) {
          VM.sysWrite(slot,false); VM.sysWrite(" "); VM_GCUtil.dumpRef(ref);
        }
      }  // end of for loop
    VM.sysWrite("Done\n");
  }  // dumpRefs
*/

}   // VM_ScanStatics
