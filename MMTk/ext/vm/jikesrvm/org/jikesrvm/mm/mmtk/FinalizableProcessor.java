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
package org.jikesrvm.mm.mmtk;

import org.vmmagic.pragma.*;
import org.vmmagic.unboxed.*;

import org.jikesrvm.SizeConstants;
import org.jikesrvm.VM;
import org.jikesrvm.mm.mminterface.Selected;
import org.jikesrvm.runtime.Magic;
import org.jikesrvm.Services;
import org.jikesrvm.scheduler.RVMThread;
import org.mmtk.plan.TraceLocal;

/**
 * This class manages the processing of finalizable objects.
 */
@Uninterruptible
public final class FinalizableProcessor extends org.mmtk.vm.FinalizableProcessor implements SizeConstants {

  /********************************************************************
   * Class fields
   */

  /** The FinalizableProcessor singleton */
  private static final FinalizableProcessor finalizableProcessor = new FinalizableProcessor();

  /** Stress the system? */
  private static final boolean STRESS = VM.ForceFrequentGC;

  /** Initial size of the reference object table */
  private static final int INITIAL_SIZE = STRESS ? 1 : 256;

  /** Amount to grow the table by when it is filled */
  private static final double GROWTH_FACTOR = 2.0;

  /*************************************************************************
   * Instance fields
   */

  /** Used to ensure mutual exclusion during table manipulation */
  private final Lock lock = new Lock("AddressTable");

  /** The table of candidates */
  protected volatile AddressArray table = AddressArray.create(INITIAL_SIZE);

  /** The table of ready objects */
  protected volatile Object[] readyForFinalize = new Object[INITIAL_SIZE];

  /** Index of first entry created since last collection */
  protected int nurseryIndex = 0;

  /** Index of the first free slot in the table. */
  protected volatile int maxIndex = 0;

  /** Flag to prevent a race between threads growing the table. */
  private volatile boolean growingTables = false;

  /** Next object ready to be finalized */
  private volatile int nextReadyIndex = 0;

  /** Last object ready to be finalized */
  private volatile int lastReadyIndex = 0;

  /**
   * Create a new table.
   */
  protected FinalizableProcessor() {}

  /**
   * Grow the table.
   *
   * Can GC when it allocates, but the rest of the code can't tolerate GC.
   * This method is called without the reference processor lock held, but with
   * the flag <code>growingTable</code> set.
   */
  @UninterruptibleNoWarn
  @Unpreemptible("Non-preemptible but allocates larger table")
  private void growTables() {
    if (maxIndex >= table.length()) {
      int newLength = STRESS ? table.length() + 1 : (int)(table.length() * GROWTH_FACTOR);
      AddressArray newTable = AddressArray.create(newLength);
      for (int i=0; i < table.length(); i++) {
        newTable.set(i, table.get(i));
      }
      table = newTable;
    }

    if (maxIndex >= freeReady()) {
      /* Logically we need to be able to store all these values in the ready table */
      int readyLength = table.length() + countReady();
      if (readyLength > readyForFinalize.length) {
        /* Need to grow the ready table also */
        Object[] newReadyForFinalize = new Object[readyLength];
        int j = 0;
        for(int i=nextReadyIndex; i < lastReadyIndex && i < readyForFinalize.length; i++) {
          newReadyForFinalize[j++] = readyForFinalize[i];
        }
        if (lastReadyIndex < nextReadyIndex) {
          for(int i=0; i < lastReadyIndex; i++) {
            newReadyForFinalize[j++] = readyForFinalize[i];
          }
        }
        lastReadyIndex = j;
        nextReadyIndex = 0;
        readyForFinalize = newReadyForFinalize;
      }
    }
  }

  /**
   * Allocate an entry in the table. This should be called from an unpreemptible
   * context so that the entry can be filled. This method is responsible for growing
   * the table if necessary.
   */
  @NoInline
  @Unpreemptible("Non-preemptible but yield when table needs to be grown")
  public void add(Object object) {
    /*
     * Ensure that only one thread at a time can grow the
     * table of references.  The volatile flag <code>growingTable</code> is
     * used to allow growing the table to trigger GC, but to prevent
     * any other thread from accessing the table while it is being grown.
     *
     * If the table has space, threads will add the reference, incrementing maxIndex
     * and exit.
     *
     * If the table is full, the first thread to notice will grow the table.
     * Subsequent threads will release the lock and yield at (1) while the
     * first thread grows the table.
     */
    lock.acquire();
    while (growingTables || maxIndex >= table.length() || maxIndex >= freeReady()) {
      if (growingTables) {
        lock.release();
        RVMThread.yield(); // (1) Allow another thread to grow the table
        lock.acquire();
      } else {
        growingTables = true;  // Prevent other threads from growing table while lock is released
        lock.release();       // Can't hold the lock while allocating
        growTables();
        lock.acquire();
        growingTables = false; // Allow other threads to grow the table rather than waiting for us
      }
    }
    table.set(maxIndex++, Magic.objectAsAddress(object));
    lock.release();
  }

  /**
   * Clear the contents of the table. This is called when reference types are
   * disabled to make it easier for VMs to change this setting at runtime.
   */
  public void clear() {
    maxIndex = 0;
  }

  /**
   * Scan through all entries in the table and forward.
   *
   * Currently ignores the nursery hint.
   *
   * TODO parallelise this code?
   *
   * @param trace The trace
   * @param nursery Is this a nursery collection ?
   */
  @Override
  public void forward(TraceLocal trace, boolean nursery) {
    for (int i=0 ; i < maxIndex; i++) {
      ObjectReference ref = table.get(i).toObjectReference();
      table.set(i, trace.getForwardedFinalizable(ref).toAddress());
    }
  }

  /**
   * Scan through the list of references. Calls ReferenceProcessor's
   * processReference method for each reference and builds a new
   * list of those references still active.
   *
   * Depending on the value of <code>nursery</code>, we will either
   * scan all references, or just those created since the last scan.
   *
   * TODO parallelise this code
   *
   * @param nursery Scan only the newly created references
   */
  @Override
  @UninterruptibleNoWarn
  public void scan(TraceLocal trace, boolean nursery) {
    int toIndex = nursery ? nurseryIndex : 0;

    for (int fromIndex = toIndex; fromIndex < maxIndex; fromIndex++) {
      ObjectReference ref = table.get(fromIndex).toObjectReference();

      /* Determine liveness (and forward if necessary) */
      if (trace.isLive(ref)) {
        table.set(toIndex++, trace.getForwardedFinalizable(ref).toAddress());
        continue;
      }

      /* Make ready for finalize */
      ref = trace.retainForFinalize(ref);

      /* Add to object table */
      Offset offset = Word.fromIntZeroExtend(lastReadyIndex).lsh(LOG_BYTES_IN_ADDRESS).toOffset();
      Selected.Plan.get().storeObjectReference(Magic.objectAsAddress(readyForFinalize).plus(offset), ref);
      lastReadyIndex = (lastReadyIndex + 1) % readyForFinalize.length;
    }
    nurseryIndex = maxIndex = toIndex;
  }

  /**
   * Get an object to run finalize().
   *
   * @return The object to finalize()
   */
  @NoInline
  @Unpreemptible("Non-preemptible but may pause if another thread is growing the table")
  public Object getReady() {
    lock.acquire();
    while (growingTables) {
      lock.release();
      RVMThread.yield(); // (1) Allow another thread to grow the table
      lock.acquire();
    }
    Object result = null;
    if (nextReadyIndex != lastReadyIndex) {
      result = readyForFinalize[nextReadyIndex];
      Services.setArrayUninterruptible(readyForFinalize, nextReadyIndex, null);
      nextReadyIndex = (nextReadyIndex + 1) % readyForFinalize.length;
    }
    lock.release();
    return result;
  }

  /***********************************************************************
   * Statistics and debugging
   */

  /**
   * The number of entries in the table.
   */
  public int count() {
    return maxIndex;
  }

  /**
   * The number of entries ready to be finalized.
   */
  public int countReady() {
    return ((lastReadyIndex - nextReadyIndex) + readyForFinalize.length) % readyForFinalize.length;
  }

  /**
   * The number of entries ready to be finalized.
   */
  public int freeReady() {
    return readyForFinalize.length - countReady();
  }

  /***********************************************************************
   * Static methods.
   */

  /** Get the singleton */
  public static FinalizableProcessor getProcessor() {
    return finalizableProcessor;
  }

  /**
   * Add a finalization candidate.
   * @param object The object with a finalizer.
   */
  @Unpreemptible("Non-preemptible but may pause if table needs to be grown")
  public static void addCandidate(Object object) {
    finalizableProcessor.add(object);
  }

  /**
   * Get an object to call the finalize() method on it.
   */
  @Unpreemptible("Non-preemptible but may pause if table is being grown")
  public static Object getForFinalize() {
    return finalizableProcessor.getReady();
  }

  /**
   * The number of objects waiting for finalize() calls.
   */
  public static int countReadyForFinalize() {
    return finalizableProcessor.countReady();
  }
}
