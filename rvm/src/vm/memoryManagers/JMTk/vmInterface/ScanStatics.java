/*
 * (C) Copyright IBM Corp. 2001
 */


package org.mmtk.vm;

import org.mmtk.utility.deque.AddressDeque;
import org.mmtk.utility.Constants;
import com.ibm.JikesRVM.VM_Statics;
import com.ibm.JikesRVM.VM;
import com.ibm.JikesRVM.VM_Magic;
import com.ibm.JikesRVM.VM_Constants;
import com.ibm.JikesRVM.VM_Thread;
import com.ibm.JikesRVM.memoryManagers.mmInterface.VM_CollectorThread;

import org.vmmagic.unboxed.*;
import org.vmmagic.pragma.*;

/**
 * Class that determines all JTOC slots (statics) that hold references
 *
 * $Id$
 *
 * @author Perry Cheng
 */  
public class ScanStatics implements Constants {

  /****************************************************************************
   *
   * Class variables
   */

  /** slots are integer sized, not word sized */ // FIXME: Document why.
  private static final int LOG_BYTES_IN_SLOT = LOG_BYTES_IN_INT;

  /**
   * Scan static variables (JTOC) for object references.  Executed by
   * all GC threads in parallel, with each doing a portion of the
   * JTOC.
   */
  public static void scanStatics (AddressDeque rootLocations) 
    throws UninterruptiblePragma {

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
        rootLocations.push(slots.add(slot << LOG_BYTES_IN_SLOT));
      }  // end of for loop
      start = start + stride;
    }  // end of while loop
  }  // scanStatics
}
