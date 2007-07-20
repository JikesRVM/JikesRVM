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
package org.jikesrvm.scheduler;

import org.jikesrvm.VM;
import org.jikesrvm.VM_Callbacks;
import org.jikesrvm.VM_Constants;
import org.jikesrvm.VM_Services;
import org.jikesrvm.objectmodel.VM_ObjectModel;
import org.jikesrvm.objectmodel.VM_ThinLockConstants;
import org.jikesrvm.runtime.VM_Magic;
import org.vmmagic.pragma.Interruptible;
import org.vmmagic.pragma.LogicallyUninterruptible;
import org.vmmagic.pragma.Uninterruptible;
import org.vmmagic.unboxed.Offset;
import org.vmmagic.unboxed.Word;

/**
 VM_Lock provides RVM support for monitors and Java level
 synchronization.

 <p>
 This class may be decomposed into four sections:
 <OL>
 <LI> support for synchronization methods of java.lang.Object,
 <LI> heavy weight locking mechanism,
 <LI> management of heavy weight locks, and
 <LI> debugging and performance tuning support.
 </OL>
 </p>

 <p><STRONG>Requirement 1:</STRONG>
 It must be possible to lock an object when allocations are not
 allowed.
 </p>

 <p><STRONG>Requirement 2:</STRONG>
 After a lock has been obtained, the code of this class must return
 without allowing a thread switch.  (The {@link
org.jikesrvm.ArchitectureSpecific.VM_BaselineExceptionDeliverer#unwindStackFrame(org.jikesrvm.compilers.common.VM_CompiledMethod, org.jikesrvm.ArchitectureSpecific.VM_Registers)
 exception handler}
 of the baseline compiler assumes that until lock() returns the lock
 has not been obtained.)
 </p>

 <p><STRONG>Section 1:</STRONG>
 support for {@link java.lang.Object#notify}, {@link
java.lang.Object#notifyAll}, and {@link java.lang.Object#wait()}.
 When these methods are called, the indicated object must be locked
 by the current thread.  <p>

 <p><STRONG>Section 2:</STRONG>
 has two sections.  <EM>Section 2a:</EM> locks (and unlocking)
 objects with heavy-weight locks associated with them.  <EM>Section
 2b:</EM> associates (and disassociates) heavy-weight locks with
 objects.
 </p>

 <p><STRONG>Section 3:</STRONG>
 Allocates (and frees) heavy weight locks consistent with Requirement
 1.  Also, distributes them among the virtual processors.
 </p>

 <p><STRONG>Section 4:</STRONG>
 debugging and performance tuning stuff.
 </p>

 <p>
 The following performance tuning issues have not yet been addressed
 adaquately:
 <OL>
 <LI> <EM>What to do if the attempt to lock an object fails?</EM>  There
 are three choices: try again (busy-wait), yield and then try again,
 inflate the lock and yield to the heavy-weight lock's entering
 queue.  Currently, yield n times, then inflate.
 (This seemed to be best for the portBOB benchmark on a 12-way AIX
 SMP in the Fall of '99.)
 <LI> <EM>When should a heavy-weight lock be deflated?</EM>  Currently,
 deflation happens when the lock is unlocked with nothing on either
 of its queues.  Probably better, would be to periodically (what
 period?) examine heavy-weight locks and deflate any that havn't
 been held for a while (how long?).
 <LI> <EM>How many heavy-weight locks are needed? and how should they be
 managed?</EM>  Currently, each processor maintains a pool of free
 locks.  When a lock is inflated by a processor it is taken from
 this pool and when a lock is deflated by a processor it gets added
 to the processors pool.  Since inflation can happen on one processor
 and deflation on another, this can create an imbalance.  It might
 be worth investigating a scheme for balancing these local pools.
 <LI> <EM>Is there any advantage to using the {@link VM_ProcessorLock#tryLock}
 method?</EM>
 </OL>
 Once these questions, and the issue of using MCS locking in {@link
VM_ProcessorLock}, have been investigate, then a larger performance issue
 comes into view.  A number of different light-weight locking schemes have
 been proposed over the years (see last several OOPSLA's).  It should be
 possible to implement each of them in RVM and compare their performance.
 </p>

 @see java.lang.Object
 @see VM_ThinLock
 @see VM_ProcessorLock
 */

@Uninterruptible
public abstract class VM_Lock implements VM_Constants {
  /** The object being locked (if any). */
  protected Object lockedObject;
  /** The id of the thread that owns this lock (if any). */
  protected int ownerId;
  /** The number of times the owning thread (if any) has acquired this lock. */
  protected int recursionCount;
  /** A spin lock to handle contention for the data structures of this lock. */
  public final VM_ProcessorLock mutex;

  /**
   * Should we attempt to keep the roughly equal sized pools for free
   * heavy-weight locks on each processor?
   */
  protected static final boolean balanceFreeLocks = false;

  /** Control the gathering of statistics */
  public static final boolean STATS = false;

  /** Number of lock operations */
  public static int lockOperations;
  /** Number of unlock operations */
  public static int unlockOperations;
  /** Number of deflations */
  public static int deflations;
  
  /**
   * Acquires this heavy-weight lock on the indicated object.
   *
   * @param o the object to be locked
   * @return true, if the lock succeeds; false, otherwise
   */
  public abstract boolean lockHeavy(Object o);

  /**
   * Releases this heavy-weight lock on the indicated object.
   *
   * @param o the object to be unlocked
   */
  public abstract void unlockHeavy(Object o);

  /** Thick locks. */
  public static VM_Lock[] locks;

  // lock table implementation
  //
  protected boolean active;
  private VM_Lock nextFreeLock;
  protected int index;

  // Maximum number of VM_Lock's that we can support
  //
  protected static final int LOCK_ALLOCATION_UNIT_SIZE = 100;
  private static final int LOCK_ALLOCATION_UNIT_COUNT = 2500;  // TEMP SUSAN
  static final int MAX_LOCKS = LOCK_ALLOCATION_UNIT_SIZE * LOCK_ALLOCATION_UNIT_COUNT;
  static final int INIT_LOCKS = 4096;

  private static final VM_ProcessorLock lockAllocationMutex = new VM_ProcessorLock();
  private static int lockUnitsAllocated;
  private static VM_Lock globalFreeLock;
  private static int globalFreeLocks;

  /**
   * A heavy weight lock to handle extreme contention and wait/notify
   * synchronization.
   */
  public VM_Lock() {
    mutex = new VM_ProcessorLock();
  }

  public void setOwnerId(int id) {
    ownerId = id;
  }
  public int getOwnerId() {
    return ownerId;
  }

  public void setRecursionCount(int c) {
    recursionCount = c;
  }
  public int getRecursionCount() {
    return recursionCount;
  }

  public void setLockedObject(Object o) {
    lockedObject = o;
  }
  public Object getLockedObject() {
    return lockedObject;
  }

  
  /**
   * Sets up the data structures for holding heavy-weight locks.
   */
  @Interruptible
  public static void init() {
    locks = new VM_Lock[INIT_LOCKS + 1]; // don't use slot 0
    if (VM.VerifyAssertions) {
      // check that each potential lock is addressable
      VM._assert((locks.length - 1 <=
                  VM_ThinLockConstants.TL_LOCK_ID_MASK.rshl(VM_ThinLockConstants.TL_LOCK_ID_SHIFT).toInt())||
                  VM_ThinLockConstants.TL_LOCK_ID_MASK.EQ(Word.fromIntSignExtend(-1)));
    }
  }

  @LogicallyUninterruptible
  /* ok because the caller is prepared to lose control when it allocates a lock -- dave */
  static void growLocks() {
    VM_Lock[] oldLocks = locks;
    int newSize = 2 * oldLocks.length;
    if (newSize > MAX_LOCKS + 1) {
      VM.sysFail("Cannot grow lock array greater than maximum possible index");
    }
    VM_Lock[] newLocks = new VM_Lock[newSize];
    for (int i = 0; i < oldLocks.length; i++) {
      newLocks[i] = oldLocks[i];
    }
    locks = newLocks;
  }

  /**
   * Delivers up an unassigned heavy-weight lock.  Locks are allocated
   * from processor specific regions or lists, so normally no synchronization
   * is required to obtain a lock.
   *
   * Collector threads cannot use heavy-weight locks.
   *
   * @return a free VM_Lock; or <code>null</code>, if garbage collection is not enabled
   */
  @LogicallyUninterruptible
  /* ok because the caller is prepared to lose control when it allocates a lock -- dave */
  static VM_Lock allocate() {
    VM_Processor mine = VM_Processor.getCurrentProcessor();
    if (mine.isInitialized && !mine.threadSwitchingEnabled()) {
      return null; // Collector threads can't use heavy locks because they don't fix up their stacks after moving objects
    }
    if ((mine.freeLocks == 0) && (0 < globalFreeLocks) && balanceFreeLocks) {
      localizeFreeLocks(mine);
    }
    VM_Lock l = mine.freeLock;
    if (l != null) {
      mine.freeLock = l.nextFreeLock;
      l.nextFreeLock = null;
      mine.freeLocks--;
      l.active = true;
    } else {
      l = new VM_Scheduler.LockModel(); // may cause thread switch (and processor loss)
      mine = VM_Processor.getCurrentProcessor();
      if (mine.lastLockIndex < mine.nextLockIndex) {
        lockAllocationMutex.lock("lock allocation mutex");
        mine.nextLockIndex = 1 + (LOCK_ALLOCATION_UNIT_SIZE * lockUnitsAllocated++);
        lockAllocationMutex.unlock();
        mine.lastLockIndex = mine.nextLockIndex + LOCK_ALLOCATION_UNIT_SIZE - 1;
        if (MAX_LOCKS <= mine.lastLockIndex) {
          VM.sysWriteln("Too many fat locks on processor ", mine.id); // make MAX_LOCKS bigger? we can keep going??
          VM.sysFail("Exiting VM with fatal error");
          return null;
        }
      }
      l.index = mine.nextLockIndex++;
      while (l.index >= locks.length) {
        growLocks();
      }
      locks[l.index] = l;
      l.active = true;
      VM_Magic.sync(); // make sure other processors see lock initialization.  Note: Derek and I BELIEVE that an isync is not required in the other processor because the lock is newly allocated - Bowen
    }
    mine.locksAllocated++;
    return l;
  }

  /**
   * Recycles a unused heavy-weight lock.  Locks are deallocated
   * to processor specific lists, so normally no synchronization
   * is required to obtain or release a lock.
   */
  protected static void free(VM_Lock l) {
    l.active = false;
    VM_Processor mine = VM_Processor.getCurrentProcessor();
    l.nextFreeLock = mine.freeLock;
    mine.freeLock = l;
    mine.freeLocks++;
    mine.locksFreed++;
  }

  /**
   * Transfers free heavy-weight locks from a processor local
   * structure to a global one.
   *
   * Only used if RVM_WITH_FREE_LOCK_BALANCING preprocessor
   * directive is set for the current build.
   */
  protected static void globalizeFreeLocks(VM_Processor mine) {
    if (mine.freeLocks <= LOCK_ALLOCATION_UNIT_SIZE) {
      if (VM.VerifyAssertions) VM._assert(mine.freeLock != null);
      VM_Lock q = mine.freeLock;
      while (q.nextFreeLock != null) {
        q = q.nextFreeLock;
      }
      lockAllocationMutex.lock("lock allocation mutex for globalizing locks");
      q.nextFreeLock = globalFreeLock;
      globalFreeLock = mine.freeLock;
      globalFreeLocks += mine.freeLocks;
      lockAllocationMutex.unlock();
      mine.freeLock = null;
      mine.freeLocks = 0;
    } else {
      VM_Lock p = null;
      VM_Lock q = mine.freeLock;
      for (int i = 0; i < LOCK_ALLOCATION_UNIT_SIZE; i++) {
        p = q;
        q = q.nextFreeLock;
      }
      lockAllocationMutex.lock("lock allocation mutex for globalizing locks");
      p.nextFreeLock = globalFreeLock;
      globalFreeLock = mine.freeLock;
      globalFreeLocks += LOCK_ALLOCATION_UNIT_SIZE;
      lockAllocationMutex.unlock();
      mine.freeLock = q;
      mine.freeLocks -= LOCK_ALLOCATION_UNIT_SIZE;
    }
  }

  /**
   * Transfers free heavy-weight locks from a global structure to a
   * processor local one.
   *
   * Only used if RVM_WITH_FREE_LOCK_BALANCING preprocessor
   * directive is set for the current build.
   */
  private static void localizeFreeLocks(VM_Processor mine) {
    if (true) return; // TEMP
    if (VM.VerifyAssertions) VM._assert(mine.freeLock == null);
    lockAllocationMutex.lock("lock allocation mutex for localize");
    if (globalFreeLocks <= LOCK_ALLOCATION_UNIT_SIZE) {
      mine.freeLock = globalFreeLock;
      mine.freeLocks = globalFreeLocks;
      globalFreeLock = null;
      globalFreeLocks = 0;
    } else {
      VM_Lock p = null;
      VM_Lock q = globalFreeLock;
      for (int i = 0; i < LOCK_ALLOCATION_UNIT_SIZE; i++) {
        p = q;
        q = q.nextFreeLock;
      }
      p.nextFreeLock = null;
      mine.freeLock = globalFreeLock;
      mine.freeLocks = LOCK_ALLOCATION_UNIT_SIZE;
      globalFreeLock = q;
      globalFreeLocks -= LOCK_ALLOCATION_UNIT_SIZE;
    }
    lockAllocationMutex.unlock();
  }

  public static void dumpLocks() {
    for (VM_Lock lock : locks) {
      if (lock != null) {
        lock.dump();
      }
    }
    VM.sysWrite("\n");
  }

  /**
   * Dump threads blocked trying to get this lock
   */
  protected abstract void dumpBlockedThreads();
  /**
   * Dump threads waiting to be notified on this lock
   */
  protected abstract void dumpWaitingThreads();

  /**
   * Reports the state of a heavy-weight lock, via {@link VM#sysWrite}.
   */
  private void dump() {
    if (!active) {
      return;
    }
    VM.sysWrite("Lock ");
    VM.sysWriteInt(index);
    VM.sysWrite(":\n");
    VM.sysWrite(" lockedObject: ");
    VM.sysWriteHex(VM_Magic.objectAsAddress(lockedObject));
    VM.sysWrite("   thin lock = ");
    VM.sysWriteHex(VM_Magic.objectAsAddress(lockedObject).loadAddress(VM_ObjectModel.defaultThinLockOffset()));
    VM.sysWrite(" object type = ");
    VM.sysWrite(VM_Magic.getObjectType(lockedObject).getDescriptor());
    VM.sysWriteln();

    VM.sysWrite(" ownerId: ");
    VM.sysWriteInt(ownerId);
    VM.sysWrite(" (");
    VM.sysWriteInt(ownerId >>> VM_ThinLockConstants.TL_THREAD_ID_SHIFT);
    VM.sysWrite(") recursionCount: ");
    VM.sysWriteInt(recursionCount);
    VM.sysWriteln();
    dumpBlockedThreads();
    dumpWaitingThreads();

    VM.sysWrite(" mutexLatestContender: ");
    if (mutex.latestContender == null) {
      VM.sysWrite("<null>");
    } else {
      VM.sysWriteHex(mutex.latestContender.id);
    }
    VM.sysWrite("\n");
  }

  /**
   * Is this lock blocking thread t?
   */
  protected abstract boolean isBlocked(VM_Thread t);

  /**
   * Is this thread t waiting on this lock?
   */
  protected abstract boolean isWaiting(VM_Thread t);
  
  /**
   * scan lock queues for thread and report its state
   */
  @Interruptible
  public static String getThreadState(VM_Thread t) {
    for (int i = 0; i < locks.length; ++i) {
      VM_Lock l = locks[i];
      if (l == null || !l.active) continue;
      if (l.isBlocked(t)) return ("waitingForLock(blocked)" + i);
      if (l.isWaiting(t)) return "waitingForNotification(waiting)";
    }
    return null;
  }
  
  //////////////////////////////////////////////
  //             Statistics                   //
  //////////////////////////////////////////////

  @Interruptible
  public static void boot() {
    VM_Callbacks.addExitMonitor(new VM_Lock.ExitMonitor());
    VM_Callbacks.addAppRunStartMonitor(new VM_Lock.AppRunStartMonitor());
  }

  static final class AppRunStartMonitor implements VM_Callbacks.AppRunStartMonitor {
    public void notifyAppRunStart(String app, int value) {
      if (!STATS) return;
      lockOperations = 0;
      unlockOperations = 0;
      deflations = 0;

      VM_ThinLock.notifyAppRunStart("", 0);
    }
  }

  static final class ExitMonitor implements VM_Callbacks.ExitMonitor {
    public void notifyExit(int value) {
      if (!STATS) return;

      int totalLocks = lockOperations + VM_ThinLock.fastLocks + VM_ThinLock.slowLocks;

      VM_Thread.dumpStats();
      VM.sysWrite(" notifyAll operations\n");
      VM.sysWrite("FatLocks: ");
      VM.sysWrite(lockOperations);
      VM.sysWrite(" locks");
      VM_Services.percentage(lockOperations, totalLocks, "all lock operations");
      VM.sysWrite("FatLocks: ");
      VM.sysWrite(unlockOperations);
      VM.sysWrite(" unlock operations\n");
      VM.sysWrite("FatLocks: ");
      VM.sysWrite(deflations);
      VM.sysWrite(" deflations\n");

      VM_ThinLock.notifyExit(totalLocks);
    }
  }
}
