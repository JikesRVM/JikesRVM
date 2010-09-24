/*
 *  This file is part of the Jikes RVM project (http://jikesrvm.org).
 *
 *  This file is licensed to You under the Eclipse Public License (EPL);
 *  You may not use this file except in compliance with the License. You
 *  may obtain a copy of the License at
 *
 *      http://www.opensource.org/licenses/eclipse-1.0.php
 *
 *  See the COPYRIGHT.txt file distributed with this work for information
 *  regarding copyright ownership.
 */
package org.jikesrvm.mm.mmtk;

import org.mmtk.plan.CollectorContext;
import org.mmtk.plan.TraceLocal;
import org.mmtk.utility.Constants;
import org.mmtk.utility.Log;
import org.jikesrvm.VM;
import org.jikesrvm.runtime.Statics;
import org.jikesrvm.runtime.Magic;
import org.jikesrvm.scheduler.RVMThread;
import org.jikesrvm.mm.mminterface.MemoryManager;

import org.vmmagic.unboxed.*;
import org.vmmagic.pragma.*;

/**
 * Class that determines all JTOC slots (statics) that hold references
 */
public final class ScanStatics implements Constants {
  /**
   * Size in 32bits words of a JTOC slot (ie 32bit addresses = 1,
   * 64bit addresses =2)
   */
  private static final int refSlotSize = Statics.getReferenceSlotSize();
  /**
   * Mask used when calculating the chunkSize to ensure chunks are
   * 64bit aligned on 64bit architectures
   */
  private static final int chunkSizeMask = 0xFFFFFFFF - (refSlotSize - 1);
  /**
   * Scan static variables (JTOC) for object references.  Executed by
   * all GC threads in parallel, with each doing a portion of the
   * JTOC.
   */
  @Inline
  @Uninterruptible
  public static void scanStatics(TraceLocal trace) {
    // The address of the statics table
    // equivalent to Statics.getSlots()
    final Address slots = Magic.getJTOC();
    // This thread as a collector
    final CollectorContext cc = RVMThread.getCurrentThread().getCollectorContext();
    // The number of collector threads
    final int numberOfCollectors = cc.parallelWorkerCount();
    // The number of static references
    final int numberOfReferences = Statics.getNumberOfReferenceSlots();
    // The size to give each thread
    final int chunkSize = (numberOfReferences / numberOfCollectors) & chunkSizeMask;
    // The number of this collector thread (1...n)
    final int threadOrdinal = cc.parallelWorkerOrdinal();

    // Start and end of statics region to be processed
    final int start = (threadOrdinal == 0) ? refSlotSize : threadOrdinal * chunkSize;
    final int end = (threadOrdinal+1 == numberOfCollectors) ? numberOfReferences : (threadOrdinal+1) * chunkSize;

    // Process region
    for (int slot=start; slot < end; slot+=refSlotSize) {
      Offset slotOffset = Offset.fromIntSignExtend(slot << LOG_BYTES_IN_INT);
      if (ScanThread.VALIDATE_REFS) checkReference(slots.plus(slotOffset), slot);
      trace.processRootEdge(slots.plus(slotOffset), true);
    }
  }

  /**
   * Check that a reference encountered during scanning is valid.  If
   * the reference is invalid, dump stack and die.
   *
   * @param refaddr The address of the reference in question.
   */
  @Uninterruptible
  private static void checkReference(Address refaddr, int slot) {
    ObjectReference ref = refaddr.loadObjectReference();
    if (!MemoryManager.validRef(ref)) {
      Log.writeln();
      Log.writeln("Invalid ref reported while scanning statics");
      Log.write("Static slot: "); Log.writeln(slot);
      Log.writeln();
      Log.write(refaddr); Log.write(":"); Log.flush(); MemoryManager.dumpRef(ref);
      Log.writeln();
      Log.writeln("Dumping stack:");
      RVMThread.dumpStack();
      VM.sysFail("\n\nScanStack: Detected bad GC map; exiting RVM with fatal error");
    }
  }
}
