/*
 *  This file is part of the Jikes RVM project (http://jikesrvm.org).
 *
 *  This file is licensed to You under the Common Public License (CPL);
 *  You may not use this file except in compliance with the License. You
 *  may obtain a copy of the License at
 *
 *      http://www.opensource.org/licenses/cpl1.0.php
 *
 *  See the COPYRIGHT.txt file distributed with this work for information
 *  regarding copyright ownership.
 */
package org.mmtk.plan.refcount.fullheap;

import org.mmtk.plan.refcount.RCBase;
import org.mmtk.policy.Space;
import org.mmtk.utility.options.Options;
import org.mmtk.vm.VM;
import org.mmtk.vm.Collection;

import org.vmmagic.pragma.*;

/**
 * This class implements the global state of a simple reference counting
 * collector.
 * 
 * All plans make a clear distinction between <i>global</i> and
 * <i>thread-local</i> activities, and divides global and local state
 * into separate class hierarchies.  Global activities must be
 * synchronized, whereas no synchronization is required for
 * thread-local activities.  There is a single instance of Plan (or the
 * appropriate sub-class), and a 1:1 mapping of PlanLocal to "kernel
 * threads" (aka CPUs or in Jikes RVM, VM_Processors).  Thus instance
 * methods of PlanLocal allow fast, unsychronized access to functions such as
 * allocation and collection.
 *
 * The global instance defines and manages static resources
 * (such as memory and virtual memory resources).  This mapping of threads to
 * instances is crucial to understanding the correctness and
 * performance properties of MMTk plans.
 */
@Uninterruptible public class RC extends RCBase { 
  /*****************************************************************************
   * 
   * Collection
   */

  /**
   * Poll for a collection
   * 
   * @param mustCollect Force a collection.
   * @param space The space that caused the poll.
   * @return True if a collection is required.
   */
  @LogicallyUninterruptible
  public boolean poll(boolean mustCollect, Space space) { 
    if (getCollectionsInitiated() > 0 || !isInitialized()) return false;
    mustCollect |= stressTestGCRequired();
    boolean heapFull = getPagesReserved() > getTotalPages();
    boolean metaDataFull = metaDataSpace.reservedPages() >
                           META_DATA_FULL_THRESHOLD;
    int newMetaDataPages = metaDataSpace.committedPages() - 
                           previousMetaDataPages;
    if (mustCollect || heapFull || metaDataFull ||
        (progress && (newMetaDataPages > Options.metaDataLimit.getPages()))) {
      if (space == metaDataSpace) {
        setAwaitingCollection();
        return false;
      }
      required = space.reservedPages() - space.committedPages();
      VM.collection.triggerCollection(Collection.RESOURCE_GC_TRIGGER);
      return true;
    }
    return false;
  }
}
