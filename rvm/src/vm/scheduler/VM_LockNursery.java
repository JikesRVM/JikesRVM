/*
 * (C) Copyright IBM Corp 2001,2002
 */
//$Id$
package com.ibm.JikesRVM;

import com.ibm.JikesRVM.memoryManagers.vmInterface.MM_Interface;

/**
 *   VM_LockNursery provides RVM support for synchronization on objects that
 *   do not have their own thin lock inlined in the object.  This code is only
 *   used to support monitorenter/monitorexit bytecodes; synchronized methods
 *   always cause their classes to have thin locks allocated inline in the object.
 *   <p>
 *
 *  @see VM_Lock
 *  @author David F. Bacon 
 *  @author Stephen Fink
 *  @author Dave Grove
 */
public final class VM_LockNursery implements VM_Constants, VM_Uninterruptible {

  private static final class VM_LockBucket {
    public VM_LockBucket next;
    public Object object; // NOTE: should be a weak reference if the table is not purged via other means!!
    public VM_Lock lock;
  }

  private static final int SIZE = 317;
  private static final int ALLOC = 1024;

  private static final boolean DEBUG = false;

  private static final boolean STATS = VM_Lock.STATS;
  private static int lockNumber;
  private static int lockOperations;

  private static final VM_LockNursery nursery = new VM_LockNursery();

  private final VM_LockBucket buckets[];

  private VM_LockBucket freeBuckets;

  private final VM_ProcessorLock myLock = new VM_ProcessorLock();

  public VM_LockNursery () {
    buckets = new VM_LockBucket[SIZE];

    for (int i = 0; i < ALLOC; i++) {
      VM_LockBucket b = new VM_LockBucket();
      b.lock = new VM_Lock();
      b.next = freeBuckets;
      freeBuckets = b;
    }
  }


  /**
   * Obtains a lock on the indicated object.  
   *
   * @param o the object to be locked 
   */
  public static void lock(Object o) {
    VM_Lock lock = nursery.findOrInsert(o, true);
    while (!lock.lockHeavy(o)) {
      if (VM_Processor.getCurrentProcessor().threadSwitchingEnabled()) {
        VM_Thread.yield(); // wait, hope o gets unlocked
      }
    }
    if (STATS) lockOperations++;
  }

  /**
   * Releases a lock on the indicated object.  
   *
   * @param o the object to be unlocked 
   */
  public static void unlock (Object o) {
    VM_Lock lock = nursery.findOrInsert(o, false);
    if (lock == null) {
      VM_Lock.raiseIllegalMonitorStateException("lock nursery: unlock", o);
    } else {
      lock.unlockHeavy(o);
    }
  }

  public static VM_Lock findOrCreate (Object o, boolean create) { 
    return nursery.findOrInsert(o, create); 
  }

  /**
   * Finds a lock bucket for object o, or creates one if insert is true.
   *
   * @param o the object to be found
   * @param insert if true, create bucket if none found
   * @return the lock bucket or null
   */
  private VM_Lock findOrInsert (Object o, boolean insert) {
    int h = VM_ObjectModel.getObjectHashCode(o) % SIZE;
    if (h < 0) h = -h;

    myLock.lock();
    for (VM_LockBucket b = buckets[h]; b != null; b = b.next) {
      if (b.object == o) {
        if (DEBUG) { VM.sysWrite(VM_Magic.objectAsAddress(o));  VM.sysWrite(": Found nursery lock\n"); }
        myLock.unlock();
        return b.lock;
      }
    }

    if (insert) {
      if (DEBUG) { VM.sysWrite(VM_Magic.objectAsAddress(o));  VM.sysWrite(": Created nursery lock\n"); }
      VM_LockBucket b = allocate();
      b.next = buckets[h];
      b.object = o;
      b.lock.lockedObject = o;
      // Want to say: buckets[h] = b but the array store check is a potential thread switch point!
      // WARNING: Because we are using magic here, we are going to miss a write barrier update in
      //          a generational GC so._assert that the collector isn't using one!!!
      if (VM.VerifyAssertions) VM._assert(!MM_Interface.NEEDS_WRITE_BARRIER);
      VM_Magic.setObjectAtOffset(buckets, h<<LOG_BYTES_IN_ADDRESS, b); 
      myLock.unlock();
      return b.lock;
    } else {
      myLock.unlock();
      return null;
    }
  }

  /**
   * Allocate a bucket from the free list, or throw a death exception.
   *
   * @return an empty bucket
   */
  private VM_LockBucket allocate () {
    VM_LockBucket b = freeBuckets;
    if (b == null) {
      VM.sysFail("Out of space for VM_LockNursery; increase ALLOC");
    }
    freeBuckets = freeBuckets.next;
    if (STATS) lockNumber++;
    return b;
  }

  static void notifyAppRunStart(String app, int value) {
    if (!STATS) return;
    lockNumber = 0;
    lockOperations = 0;
  }
  static void notifyExit(int value) {
    if (!STATS) return;
    VM.sysWrite("LockNursery: "); VM.sysWrite(lockNumber); VM.sysWrite(" nursery locks created\n");
    VM.sysWrite("LockNursery: "); VM.sysWrite(lockOperations); VM.sysWrite(" nursery lock operations");
    VM_Stats.percentage(lockOperations, value, "all lock operations");
  }
}
