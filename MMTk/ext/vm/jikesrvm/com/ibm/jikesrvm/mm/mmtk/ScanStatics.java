/*
 * This file is part of MMTk (http://jikesrvm.sourceforge.net).
 * MMTk is distributed under the Common Public License (CPL).
 * A copy of the license is included in the distribution, and is also
 * available at http://www.opensource.org/licenses/cpl1.0.php
 *
 * (C) Copyright IBM Corp. 2001
 */
package com.ibm.jikesrvm.mm.mmtk;

import org.mmtk.plan.TraceLocal;
import org.mmtk.utility.deque.AddressDeque;
import org.mmtk.utility.Constants;
import com.ibm.jikesrvm.VM_Statics;
import com.ibm.jikesrvm.VM;
import com.ibm.jikesrvm.VM_Magic;
import com.ibm.jikesrvm.VM_Constants;
import com.ibm.jikesrvm.VM_Thread;
import com.ibm.jikesrvm.memorymanagers.mminterface.VM_CollectorThread;

import org.vmmagic.unboxed.*;
import org.vmmagic.pragma.*;

/**
 * Class that determines all JTOC slots (statics) that hold references
 *
 * $Id: ScanStatics.java,v 1.3 2006/06/05 04:30:57 steveb-oss Exp $
 *
 * @author Perry Cheng
 */  
public final class ScanStatics implements Constants {

  /****************************************************************************
   *
   * Class variables
   */

  /**
   * Scan static variables (JTOC) for object references.  Executed by
   * all GC threads in parallel, with each doing a portion of the
   * JTOC.
   */
  public static void scanStatics(TraceLocal trace) 
    throws UninterruptiblePragma, InlinePragma {

    int numSlots = VM_Statics.getNumberOfSlots();
    Address slots = VM_Statics.getSlots();
    int chunkSize = 512;
    int slot, start, end, stride;
    int refSlotSize = VM_Statics.getReferenceSlotSize(); //reference slots are aligned
    VM_CollectorThread ct;

    stride = chunkSize * VM_CollectorThread.numCollectors();
    ct = VM_Magic.threadAsCollectorThread(VM_Thread.getCurrentThread());
    start = (ct.getGCOrdinal() - 1) * chunkSize;

    while (start < numSlots) {
      end = start + chunkSize;
      if (end > numSlots)
        end = numSlots;  // doing last segment of JTOC
      for (slot=start; slot<end; slot+=refSlotSize) {
        if (VM_Statics.isReference(slot)) {
          // slot contains a ref of some kind.  call collector specific
          // processPointerField, passing address of reference
          //
          trace.addRootLocation(slots.plus(VM_Statics.slotAsOffset(slot)));
        }
      }  // end of for loop
      start = start + stride;
    }  // end of while loop
  }  // scanStatics
}
