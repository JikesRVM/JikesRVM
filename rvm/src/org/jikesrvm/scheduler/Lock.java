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
package org.jikesrvm.scheduler;

import org.jikesrvm.VM;
import org.jikesrvm.Callbacks;
import org.jikesrvm.Constants;
import org.jikesrvm.Services;
import org.jikesrvm.objectmodel.ObjectModel;
import org.jikesrvm.objectmodel.ThinLockConstants;
import org.jikesrvm.runtime.Magic;
import org.vmmagic.pragma.Inline;
import org.vmmagic.pragma.Interruptible;
import org.vmmagic.pragma.Uninterruptible;
import org.vmmagic.pragma.UnpreemptibleNoWarn;
import org.vmmagic.pragma.Unpreemptible;
import org.vmmagic.unboxed.Word;
import org.vmmagic.unboxed.Offset;

/**
 Lock provides RVM support for monitors and Java level
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
org.jikesrvm.ArchitectureSpecific.BaselineExceptionDeliverer#unwindStackFrame(org.jikesrvm.compilers.common.CompiledMethod, org.jikesrvm.ArchitectureSpecific.Registers)
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
 1.
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
 <LI> <EM>Is there any advantage to using the {@link SpinLock#tryLock}
 method?</EM>
 </OL>
 Once these questions, and the issue of using MCS locking in {@link
SpinLock}, have been investigate, then a larger performance issue
 comes into view.  A number of different light-weight locking schemes have
 been proposed over the years (see last several OOPSLA's).  It should be
 possible to implement each of them in RVM and compare their performance.
 </p>

 @see java.lang.Object
 @see ThinLock
 @see SpinLock
 */

@Uninterruptible
public class Lock implements Constants {
  /****************************************************************************
   * Constants
   */

  /** do debug tracing? */
  protected static final boolean trace = false;
  /** Control the gathering of statistics */
  public static final boolean STATS = false;

  /** The (fixed) number of entries in the lock table spine */
  protected static final int LOCK_SPINE_SIZE = 128;
  /** The log size of each chunk in the spine */
  protected static final int LOG_LOCK_CHUNK_SIZE = 11;
  /** The size of each chunk in the spine */
  protected static final int LOCK_CHUNK_SIZE = 1 << LOG_LOCK_CHUNK_SIZE;
  /** The mask used to get the chunk-level index */
  protected static final int LOCK_CHUNK_MASK = LOCK_CHUNK_SIZE - 1;
  /** The maximum possible number of locks */
  protected static final int MAX_LOCKS = LOCK_SPINE_SIZE * LOCK_CHUNK_SIZE;
  /** The number of chunks to allocate on startup */
  protected static final int INITIAL_CHUNKS = 1;

  /**
   * Should we give up or persist in the attempt to get a heavy-weight lock,
   * if its <code>mutex</code> microlock is held by another procesor.
   */
  private static final boolean tentativeMicrolocking = false;

  // Heavy lock table.

  /** The table of locks. */
  private static Lock[][] locks;
  /** Used during allocation of locks within the table. */
  private static final SpinLock lockAllocationMutex = new SpinLock();
  /** The number of chunks in the spine that have been physically allocated */
  private static int chunksAllocated;
  /** The number of locks allocated (these may either be in use, on a global
   * freelist, or on a thread's freelist. */
  private static int nextLockIndex;

  // Global free list.

  /** A global lock free list head */
  private static Lock globalFreeLock;
  /** the number of locks held on the global free list. */
  private static int globalFreeLocks;
  /** the total number of allocation operations. */
  private static int globalLocksAllocated;
  /** the total number of free operations. */
  private static int globalLocksFreed;

  // Statistics

  /** Number of lock operations */
  public static int lockOperations;
  /** Number of unlock operations */
  public static int unlockOperations;
  /** Number of deflations */
  public static int deflations;

  /****************************************************************************
   * Instance
   */

  /** The object being locked (if any). */
  protected Object lockedObject;
  /** The id of the thread that owns this lock (if any). */
  protected int ownerId;
  /** The number of times the owning thread (if any) has acquired this lock. */
  protected int recursionCount;
  /** A spin lock to handle contention for the data structures of this lock. */
  public final SpinLock mutex;
  /** Is this lock currently being used? */
  protected boolean active;
  /** The next free lock on the free lock list */
  private Lock nextFreeLock;
  /** This lock's index in the lock table*/
  protected int index;
  /** Queue for entering the lock, guarded by mutex. */
  ThreadQueue entering;
  /** Queue for waiting on a notify, guarded by mutex as well. */
  ThreadQueue waiting;

  /**
   * A heavy weight lock to handle extreme contention and wait/notify
   * synchronization.
   */
  public Lock() {
    mutex = new SpinLock();
    entering = new ThreadQueue();
    waiting = new ThreadQueue();
  }

  /**
   * Acquires this heavy-weight lock on the indicated object.
   *
   * @param o the object to be locked
   * @return true, if the lock succeeds; false, otherwise
   */
  @Unpreemptible
  public boolean lockHeavy(Object o) {
    if (tentativeMicrolocking) {
      if (!mutex.tryLock()) {
        return false;
      }
    } else {
      mutex.lock();  // Note: thread switching is not allowed while mutex is held.
    }
    return lockHeavyLocked(o);
  }
  /** Complete the task of acquiring the heavy lock, assuming that the mutex
      is already acquired (locked). */
  @Unpreemptible
  public boolean lockHeavyLocked(Object o) {
    if (lockedObject != o) { // lock disappeared before we got here
      mutex.unlock(); // thread switching benign
      return false;
    }
    if (STATS) lockOperations++;
    RVMThread me = RVMThread.getCurrentThread();
    int threadId = me.getLockingId();
    if (ownerId == threadId) {
      recursionCount++;
    } else if (ownerId == 0) {
      ownerId = threadId;
      recursionCount = 1;
    } else {
      entering.enqueue(me);
      mutex.unlock();
      me.monitor().lockNoHandshake();
      while (entering.isQueued(me)) {
        me.monitor().waitWithHandshake(); // this may spuriously return
      }
      me.monitor().unlock();
      return false;
    }
    mutex.unlock(); // thread-switching benign
    return true;
  }

  @UnpreemptibleNoWarn
  private static void raiseIllegalMonitorStateException(String msg, Object o) {
    throw new IllegalMonitorStateException(msg + o);
  }

  /**
   * Releases this heavy-weight lock on the indicated object.
   *
   * @param o the object to be unlocked
   */
  public void unlockHeavy(Object o) {
    boolean deflated = false;
    mutex.lock(); // Note: thread switching is not allowed while mutex is held.
    RVMThread me = RVMThread.getCurrentThread();
    if (ownerId != me.getLockingId()) {
      mutex.unlock(); // thread-switching benign
      raiseIllegalMonitorStateException("heavy unlocking", o);
    }
    recursionCount--;
    if (0 < recursionCount) {
      mutex.unlock(); // thread-switching benign
      return;
    }
    if (STATS) unlockOperations++;
    ownerId = 0;
    RVMThread toAwaken = entering.dequeue();
    if (toAwaken == null && entering.isEmpty() && waiting.isEmpty()) { // heavy lock can be deflated
      // Possible project: decide on a heuristic to control when lock should be deflated
      Offset lockOffset = Magic.getObjectType(o).getThinLockOffset();
      if (!lockOffset.isMax()) { // deflate heavy lock
        deflate(o, lockOffset);
        deflated = true;
      }
    }
    mutex.unlock(); // does a Magic.sync();  (thread-switching benign)
    if (toAwaken != null) {
      toAwaken.monitor().lockedBroadcastNoHandshake();
    }
  }

  /**
   * Disassociates this heavy-weight lock from the indicated object.
   * This lock is not held, nor are any threads on its queues.  Note:
   * the mutex for this lock is held when deflate is called.
   *
   * @param o the object from which this lock is to be disassociated
   */
  private void deflate(Object o, Offset lockOffset) {
    if (VM.VerifyAssertions) {
      VM._assert(lockedObject == o);
      VM._assert(recursionCount == 0);
      VM._assert(entering.isEmpty());
      VM._assert(waiting.isEmpty());
    }
    if (STATS) deflations++;
    ThinLock.deflate(o, lockOffset, this);
    lockedObject = null;
    free(this);
  }

  /**
   * Set the owner of a lock
   * @param id The thread id of the owner.
   */
  public void setOwnerId(int id) {
    ownerId = id;
  }

  /**
   * Get the thread id of the current owner of the lock.
   */
  public int getOwnerId() {
    return ownerId;
  }

  /**
   * Update the lock's recursion count.
   */
  public void setRecursionCount(int c) {
    recursionCount = c;
  }

  /**
   * Get the lock's recursion count.
   */
  public int getRecursionCount() {
    return recursionCount;
  }

  /**
   * Set the object that this lock is referring to.
   */
  public void setLockedObject(Object o) {
    lockedObject = o;
  }

  /**
   * Get the object that this lock is referring to.
   */
  public Object getLockedObject() {
    return lockedObject;
  }

  /**
   * Dump threads blocked trying to get this lock
   */
  protected void dumpBlockedThreads() {
    VM.sysWrite(" entering: ");
    entering.dump();
  }
  /**
   * Dump threads waiting to be notified on this lock
   */
  protected void dumpWaitingThreads() {
    VM.sysWrite(" waiting: ");
    waiting.dump();
  }

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
    VM.sysWriteHex(Magic.objectAsAddress(lockedObject));
    VM.sysWrite("   thin lock = ");
    VM.sysWriteHex(Magic.objectAsAddress(lockedObject).loadAddress(ObjectModel.defaultThinLockOffset()));
    VM.sysWrite(" object type = ");
    VM.sysWrite(Magic.getObjectType(lockedObject).getDescriptor());
    VM.sysWriteln();

    VM.sysWrite(" ownerId: ");
    VM.sysWriteInt(ownerId);
    VM.sysWrite(" (");
    VM.sysWriteInt(ownerId >>> ThinLockConstants.TL_THREAD_ID_SHIFT);
    VM.sysWrite(") recursionCount: ");
    VM.sysWriteInt(recursionCount);
    VM.sysWriteln();
    dumpBlockedThreads();
    dumpWaitingThreads();

    VM.sysWrite(" mutexLatestContender: ");
    if (mutex.latestContender == null) {
      VM.sysWrite("<null>");
    } else {
      VM.sysWriteInt(mutex.latestContender.getThreadSlot());
    }
    VM.sysWrite("\n");
  }

  /**
   * Is this lock blocking thread t?
   */
  protected boolean isBlocked(RVMThread t) {
    return entering.isQueued(t);
  }

  /**
   * Is this thread t waiting on this lock?
   */
  protected boolean isWaiting(RVMThread t) {
    return waiting.isQueued(t);
  }

  /****************************************************************************
   * Static Lock Table
   */

  /**
   * Sets up the data structures for holding heavy-weight locks.
   */
  @Interruptible
  public static void init() {
    nextLockIndex = 1;
    locks = new Lock[LOCK_SPINE_SIZE][];
    for (int i=0; i < INITIAL_CHUNKS; i++) {
      chunksAllocated++;
      locks[i] = new Lock[LOCK_CHUNK_SIZE];
    }
    if (VM.VerifyAssertions) {
      // check that each potential lock is addressable
      VM._assert(((MAX_LOCKS - 1) <=
                  ThinLockConstants.TL_LOCK_ID_MASK.rshl(ThinLockConstants.TL_LOCK_ID_SHIFT).toInt()) ||
                  ThinLockConstants.TL_LOCK_ID_MASK.EQ(Word.fromIntSignExtend(-1)));
    }
  }

  /**
   * Delivers up an unassigned heavy-weight lock.  Locks are allocated
   * from processor specific regions or lists, so normally no synchronization
   * is required to obtain a lock.
   *
   * Collector threads cannot use heavy-weight locks.
   *
   * @return a free Lock; or <code>null</code>, if garbage collection is not enabled
   */
  @UnpreemptibleNoWarn("The caller is prepared to lose control when it allocates a lock")
  static Lock allocate() {
    RVMThread me=RVMThread.getCurrentThread();
    if (me.cachedFreeLock != null) {
      Lock l = me.cachedFreeLock;
      me.cachedFreeLock = null;
      if (trace) {
        VM.sysWriteln("Lock.allocate: returning ",Magic.objectAsAddress(l),
                      ", a cached free lock from Thread #",me.getThreadSlot());
      }
      return l;
    }

    Lock l = null;
    while (l == null) {
      if (globalFreeLock != null) {
        lockAllocationMutex.lock();
        l = globalFreeLock;
        if (l != null) {
          globalFreeLock = l.nextFreeLock;
          l.nextFreeLock = null;
          l.active = true;
          globalFreeLocks--;
        }
        lockAllocationMutex.unlock();
        if (trace && l!=null) {
          VM.sysWriteln("Lock.allocate: returning ",Magic.objectAsAddress(l),
                        " from the global freelist for Thread #",me.getThreadSlot());
        }
      } else {
        l = new Lock(); // may cause thread switch (and processor loss)
        lockAllocationMutex.lock();
        if (globalFreeLock == null) {
          // ok, it's still correct for us to be adding a new lock
          if (nextLockIndex >= MAX_LOCKS) {
            VM.sysWriteln("Too many fat locks"); // make MAX_LOCKS bigger? we can keep going??
            VM.sysFail("Exiting VM with fatal error");
          }
          l.index = nextLockIndex++;
          globalLocksAllocated++;
        } else {
          l = null; // someone added to the freelist, try again
        }
        lockAllocationMutex.unlock();
        if (l != null) {
          if (l.index >= numLocks()) {
            /* We need to grow the table */
            growLocks(l.index);
          }
          addLock(l);
          l.active = true;
          /* make sure other processors see lock initialization.
           * Note: Derek and I BELIEVE that an isync is not required in the other processor because the lock is newly allocated - Bowen */
          Magic.sync();
        }
        if (trace && l!=null) {
          VM.sysWriteln("Lock.allocate: returning ",Magic.objectAsAddress(l),
                        ", a freshly allocated lock for Thread #",
                        me.getThreadSlot());
        }
      }
    }
    return l;
  }

  /**
   * Recycles an unused heavy-weight lock.  Locks are deallocated
   * to processor specific lists, so normally no synchronization
   * is required to obtain or release a lock.
   */
  protected static void free(Lock l) {
    l.active = false;
    RVMThread me = RVMThread.getCurrentThread();
    if (me.cachedFreeLock == null) {
      if (trace) {
        VM.sysWriteln("Lock.free: setting ",Magic.objectAsAddress(l),
                      " as the cached free lock for Thread #",
                      me.getThreadSlot());
      }
      me.cachedFreeLock = l;
    } else {
      if (trace) {
        VM.sysWriteln("Lock.free: returning ",Magic.objectAsAddress(l),
                      " to the global freelist for Thread #",
                      me.getThreadSlot());
      }
      returnLock(l);
    }
  }
  static void returnLock(Lock l) {
    if (trace) {
      VM.sysWriteln("Lock.returnLock: returning ",Magic.objectAsAddress(l),
                    " to the global freelist for Thread #",
                    RVMThread.getCurrentThreadSlot());
    }
    lockAllocationMutex.lock();
    l.nextFreeLock = globalFreeLock;
    globalFreeLock = l;
    globalFreeLocks++;
    globalLocksFreed++;
    lockAllocationMutex.unlock();
  }

  /**
   * Grow the locks table by allocating a new spine chunk.
   */
  @UnpreemptibleNoWarn("The caller is prepared to lose control when it allocates a lock")
  static void growLocks(int id) {
    int spineId = id >> LOG_LOCK_CHUNK_SIZE;
    if (spineId >= LOCK_SPINE_SIZE) {
      VM.sysFail("Cannot grow lock array greater than maximum possible index");
    }
    for(int i=chunksAllocated; i <= spineId; i++) {
      if (locks[i] != null) {
        /* We were beaten to it */
        continue;
      }

      /* Allocate the chunk */
      Lock[] newChunk = new Lock[LOCK_CHUNK_SIZE];

      lockAllocationMutex.lock();
      if (locks[i] == null) {
        /* We got here first */
        locks[i] = newChunk;
        chunksAllocated++;
      }
      lockAllocationMutex.unlock();
    }
  }

  /**
   * Return the number of lock slots that have been allocated. This provides
   * the range of valid lock ids.
   */
  public static int numLocks() {
    return chunksAllocated * LOCK_CHUNK_SIZE;
  }

  /**
   * Read a lock from the lock table by id.
   *
   * @param id The lock id
   * @return The lock object.
   */
  @Inline
  public static Lock getLock(int id) {
    return locks[id >> LOG_LOCK_CHUNK_SIZE][id & LOCK_CHUNK_MASK];
  }

  /**
   * Add a lock to the lock table
   *
   * @param l The lock object
   */
  @Uninterruptible
  public static void addLock(Lock l) {
    Lock[] chunk = locks[l.index >> LOG_LOCK_CHUNK_SIZE];
    int index = l.index & LOCK_CHUNK_MASK;
    Services.setArrayUninterruptible(chunk, index, l);
  }

  /**
   * Dump the lock table.
   */
  public static void dumpLocks() {
    for (int i = 0; i < numLocks(); i++) {
      Lock l = getLock(i);
      if (l != null) {
        l.dump();
      }
    }
    VM.sysWrite("\n");
    VM.sysWrite("lock availability stats: ");
    VM.sysWriteInt(globalLocksAllocated);
    VM.sysWrite(" locks allocated, ");
    VM.sysWriteInt(globalLocksFreed);
    VM.sysWrite(" locks freed, ");
    VM.sysWriteInt(globalFreeLocks);
    VM.sysWrite(" free locks\n");
  }

  /**
   * Count number of locks held by thread
   * @param id the thread locking ID we're counting for
   * @return number of locks held
   */
  public static int countLocksHeldByThread(int id) {
    int count=0;
    for (int i = 0; i < numLocks(); i++) {
      Lock l = getLock(i);
      if (l != null && l.active && l.ownerId == id && l.recursionCount > 0) {
        count++;
      }
    }
    return count;
  }

  /**
   * scan lock queues for thread and report its state
   */
  @Interruptible
  public static String getThreadState(RVMThread t) {
    for (int i = 0; i < numLocks(); i++) {
      Lock l = getLock(i);
      if (l == null || !l.active) continue;
      if (l.isBlocked(t)) return ("waitingForLock(blocked)" + i);
      if (l.isWaiting(t)) return "waitingForNotification(waiting)";
    }
    return null;
  }

  /****************************************************************************
   * Statistics
   */

  /**
   * Set up callbacks to report statistics.
   */
  @Interruptible
  public static void boot() {
    if (STATS) {
      Callbacks.addExitMonitor(new Lock.ExitMonitor());
      Callbacks.addAppRunStartMonitor(new Lock.AppRunStartMonitor());
    }
  }

  /**
   * Initialize counts in preparation for gathering statistics
   */
  private static final class AppRunStartMonitor implements Callbacks.AppRunStartMonitor {
    public void notifyAppRunStart(String app, int value) {
      lockOperations = 0;
      unlockOperations = 0;
      deflations = 0;

      ThinLock.notifyAppRunStart("", 0);
    }
  }

  /**
   * Report statistics at the end of execution.
   */
  private static final class ExitMonitor implements Callbacks.ExitMonitor {
    public void notifyExit(int value) {
      int totalLocks = lockOperations + ThinLock.fastLocks + ThinLock.slowLocks;

      RVMThread.dumpStats();
      VM.sysWrite(" notifyAll operations\n");
      VM.sysWrite("FatLocks: ");
      VM.sysWrite(lockOperations);
      VM.sysWrite(" locks");
      Services.percentage(lockOperations, totalLocks, "all lock operations");
      VM.sysWrite("FatLocks: ");
      VM.sysWrite(unlockOperations);
      VM.sysWrite(" unlock operations\n");
      VM.sysWrite("FatLocks: ");
      VM.sysWrite(deflations);
      VM.sysWrite(" deflations\n");

      ThinLock.notifyExit(totalLocks);
      VM.sysWriteln();

      VM.sysWrite("lock availability stats: ");
      VM.sysWriteInt(globalLocksAllocated);
      VM.sysWrite(" locks allocated, ");
      VM.sysWriteInt(globalLocksFreed);
      VM.sysWrite(" locks freed, ");
      VM.sysWriteInt(globalFreeLocks);
      VM.sysWrite(" free locks\n");
    }
  }
}

