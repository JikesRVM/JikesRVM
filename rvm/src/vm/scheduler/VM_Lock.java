/*
 * (C) Copyright IBM Corp 2001,2002
 */
//$Id$
package com.ibm.JikesRVM;
/**
   VM_Lock provides RVM support for monitors and Java level 
   synchronization.

   <p>
   This class may be decomposed into four sections:
   <OL>
   <LI> support for synchronization methods of java.lang.Oblect,
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
   VM_BaselineExceptionDeliverer#unwindStackFrame exception handler}
   of the baseline compiler assumes that until lock() returns the lock
   has not been obtained.)
   </p>

   <p><STRONG>Section 1:</STRONG> 
   support for {@link java.lang.Object#notify}, {@link
   java.lang.Object#notifyAll}, and {@link java.lang.Object#wait}.
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
   @author Bowen Alpern 
*/

public final class VM_Lock implements VM_Constants, VM_Uninterruptible {

  ////////////////////////////////////////////////////////////////////////
  /// Section 1: Support for synchronizing methods of java.lang.Object ///
  ////////////////////////////////////////////////////////////////////////

  /**
   * Support for Java synchronization primitive.
   *
   * @param o the object synchronized on
   * @see java.lang.Object#wait()
   */
  public static void wait (Object o) throws VM_PragmaLogicallyUninterruptible /* only loses control at expected points -- I think -dave */{
    if (STATS) waitOperations++;
    VM_Thread t = VM_Thread.getCurrentThread();
    t.proxy = new VM_Proxy(t); // cache the proxy before obtaining lock
    VM_Lock l = VM_ObjectModel.getHeavyLock(o, true);
    // this thread is supposed to own the lock on o
    if (l.ownerId != t.getLockingId())
      raiseIllegalMonitorStateException("waiting on", o);
    // allow an entering thread a chance to get the lock
    l.mutex.lock(); // until unlock(), thread-switching fatal
    VM_Thread n = l.entering.dequeue();
    if (n != null) n.scheduleHighPriority();
    // squirrel away lock state in current thread
    t.waitObject = l.lockedObject;
    t.waitCount  = l.recursionCount;
    // release l and simultaneously put t on l's waiting queue
    l.ownerId = 0;
    Throwable rethrow = null;
    try {
      t.yield(l.waiting, l.mutex); // thread-switching benign
    } catch (Throwable thr) {
      rethrow = thr; // An InterruptedException. We'll rethrow it after regaining the lock on o.
    }
    // regain lock
    VM_ObjectModel.genericLock(o);
    t.waitObject = null;          
    if (t.waitCount != 1) { // reset recursion count
      l = VM_ObjectModel.getHeavyLock(o, true);
      l.recursionCount = t.waitCount;
    }
    if (rethrow != null) {
      VM_Runtime.athrow(rethrow); // doesn't return
    }
  }

  /**
   * Support for Java synchronization primitive.
   *
   * @param o the object synchronized on
   * @param millis the number of milliseconds to wait for notification
   * @see java.lang.Object#wait(long time)
   */
  public static void wait (Object o, long millis) throws VM_PragmaLogicallyUninterruptible /* only loses control at expected points -- I think -dave */{
    double time;
    VM_Thread t = VM_Thread.getCurrentThread();
    if (STATS) timedWaitOperations++;
    // Get proxy and set wakeup time
    t.wakeupCycle = VM_Time.cycles() + VM_Time.millisToCycles(millis);
    t.proxy = new VM_Proxy(t, t.wakeupCycle); // cache the proxy before obtaining locks
    // Get monitor lock
    VM_Lock l = VM_ObjectModel.getHeavyLock(o, true);
    // this thread is supposed to own the lock on o
    if (l.ownerId != t.getLockingId())
      raiseIllegalMonitorStateException("waiting on", o);
    // allow an entering thread a chance to get the lock
    l.mutex.lock(); // until unlock(), thread-switching fatal
    VM_Thread n = l.entering.dequeue();
    if (n != null) n.scheduleHighPriority();
    VM_Scheduler.wakeupMutex.lock();
    // squirrel away lock state in current thread
    t.waitObject = l.lockedObject;
    t.waitCount  = l.recursionCount;
    // release locks and simultaneously put t on their waiting queues
    l.ownerId = 0;
    Throwable rethrow = null;
    try {
      t.yield(l.waiting, l.mutex, VM_Scheduler.wakeupQueue, VM_Scheduler.wakeupMutex); // thread-switching benign
    } catch (Throwable thr) {
        rethrow = thr;
    }
    // regain lock
    VM_ObjectModel.genericLock(o);
    t.waitObject = null;          
    if (t.waitCount != 1) { // reset recursion count
      l = VM_ObjectModel.getHeavyLock(o, true);
      l.recursionCount = t.waitCount;
    }
    if (rethrow != null)
        VM_Runtime.athrow(rethrow);
  }

  /**
   * Support for Java synchronization primitive.
   *
   * @param o the object synchronized on
   * @see java.lang.Object#notify
   */
  public static void notify (Object o) {
    if (STATS) notifyOperations++;
    VM_Lock l = VM_ObjectModel.getHeavyLock(o, false);
    if (l == null) return;
    if (l.ownerId != VM_Processor.getCurrentProcessor().threadId)
      raiseIllegalMonitorStateException("notifying ", o);
    l.mutex.lock(); // until unlock(), thread-switching fatal
    VM_Thread t = l.waiting.dequeue();
    if (false) { // this "optimization" seems tempting, but actually makes things worse (on Volano, at least) [--DL]
       if (t != null) { // global queue: check to see if thread's stack in use by some other dispatcher
          if (t.beingDispatched) l.entering.enqueue(t); // normal scheduling
          else VM_Processor.getCurrentProcessor().readyQueue.enqueueHighPriority(t); // optimized scheduling
       }
    } else {
      if (t != null) l.entering.enqueue(t);
    }
    l.mutex.unlock(); // thread-switching benign
  }

  /**
   * Support for Java synchronization primitive.
   *
   * @param o the object synchronized on
   * @see java.lang.Object#notifyAll
   */
  public static void notifyAll (Object o) {
    if (STATS) notifyAllOperations++;
    VM_Lock l = VM_ObjectModel.getHeavyLock(o, false);
    if (l == null) return;
    if (l.ownerId != VM_Processor.getCurrentProcessor().threadId)
      raiseIllegalMonitorStateException("notifying ", o);
    l.mutex.lock(); // until unlock(), thread-switching fatal
    VM_Thread t = l.waiting.dequeue();
    while (t != null) {
      l.entering.enqueue(t); 
      t = l.waiting.dequeue();
    }
    l.mutex.unlock(); // thread-switching benign
  }

  ///////////////////////////////////////////////////
  /// Section 2: Support for heavy-weight locking ///
  ///////////////////////////////////////////////////

  /** The object being locked (if any). */
  Object               lockedObject;
  /** The id of the thread that owns this lock (if any). */
  int                  ownerId;
  /** The number of times the owning thread (if any) has acquired this lock. */
  int                  recursionCount;
  /** A queue of threads contending for this lock (guarded by <code>mutex</code>). */
  VM_ThreadQueue       entering;   
  /** A queue of (proxies for) threads awaiting notification on this object (guarded by <code>mutex</code>). */
  VM_ProxyWaitingQueue waiting; 
  /** A spin lock to handle contention for the data structures of this lock. */
  VM_ProcessorLock     mutex;

  /**
   * A heavy weight lock to handle extreme contention and wait/notify
   * synchronization.
   */
  VM_Lock () {
    entering = new VM_ThreadQueue();
    waiting  = new VM_ProxyWaitingQueue();
    mutex    = new VM_ProcessorLock();
  }

  //////////////////////////////////////////////////////////////////////////
  /// Section 2A: Support for locking (and unlocking) heavy-weight locks ///
  //////////////////////////////////////////////////////////////////////////

  /**
   * Acquires this heavy-weight lock on the indicated object.
   *
   * @param o the object to be locked 
   * @return true, if the lock succeeds; false, otherwise
   */
  boolean lockHeavy (Object o) {
    if (tentativeMicrolocking) {
      if (!mutex.tryLock()) 
        return false;
    } else mutex.lock();  // Note: thread switching is not allowed while mutex is held.
    if (lockedObject != o) { // lock disappeared before we got here
      mutex.unlock(); // thread switching benign
      return false;
    }
    if (STATS) lockOperations++;
    int threadId = VM_Processor.getCurrentProcessor().threadId;
    if (ownerId == threadId) {
      recursionCount ++;
    } else if (ownerId == 0) {
      ownerId = threadId;
      recursionCount = 1;
    } else if (VM_Processor.getCurrentProcessor().threadSwitchingEnabled()) {
      VM_Thread.yield(entering, mutex); // thread-switching benign
      // when this thread next gets scheduled, it will be entitled to the lock,
      // but another thread might grab it first.
      return false; // caller will try again
    } else { // can't yield - must spin and let caller retry
      // potential deadlock if user thread is contending for a lock with thread switching disabled
      if (VM.VerifyAssertions) VM._assert(VM_Thread.getCurrentThread().isGCThread);
      mutex.unlock(); // thread-switching benign
      return false; // caller will try again
    }
    mutex.unlock(); // thread-switching benign
    return true;
  }
  
  /**
   * Releases this heavy-weight lock on the indicated object.
   *
   * @param o the object to be unlocked 
   */
  void unlockHeavy (Object o) {
    boolean deflated = false;
    mutex.lock(); // Note: thread switching is not allowed while mutex is held.
    if (ownerId != VM_Processor.getCurrentProcessor().threadId) {
      mutex.unlock(); // thread-switching benign
      raiseIllegalMonitorStateException("heavy unlocking", o);
    }
    if (0 < --recursionCount) {
      mutex.unlock(); // thread-switching benign
      return;
    }
    if (STATS) unlockOperations++;
    ownerId = 0;
    VM_Thread t = entering.dequeue();
    if (t != null) t.scheduleHighPriority();
    else if (entering.isEmpty() && waiting.isEmpty()) { // heavy lock can be deflated
      // Possible project: decide on a heuristic to control when lock should be deflated
      int lockOffset = VM_Magic.getObjectType(o).getThinLockOffset();
      if (lockOffset != -1) { // deflate heavy lock
        deflate(o, lockOffset);
        deflated = true;
      }
    }
    mutex.unlock(); // does a VM_Magic.sync();  (thread-switching benign)
    if (deflated && ((LOCK_ALLOCATION_UNIT_SIZE<<1) <= VM_Processor.getCurrentProcessor().freeLocks) && balanceFreeLocks)
      globalizeFreeLocks();
  }
  

  /**
   * Disassociates this heavy-weight lock from the indicated object.
   * This lock is not heald, nor are any threads on its queues.  Note:
   * the mutex for this lock is held when deflate is called.
   *
   * @param o the object from which this lock is to be disassociated
   */
  private void deflate (Object o, int lockOffset) {
    if (VM.VerifyAssertions) {
      VM._assert(lockedObject == o);
      VM._assert(recursionCount == 0);
      VM._assert(entering.isEmpty());
      VM._assert(waiting.isEmpty());
    }
    if (STATS) deflations++;
    VM_ThinLock.deflate(o, lockOffset, this);
    lockedObject = null;
    free(this);
  }


  ////////////////////////////////////////////////////////////////////////////
  /// Section 3: Support for allocating (and recycling) heavy-weight locks ///
  ////////////////////////////////////////////////////////////////////////////

  // lock table implementation
  // 
                 boolean       active;
  private        VM_Lock       nextFreeLock;
                 int           index;
  private static int           nextLockIndex;

  // Maximum number of VM_Lock's that we can support
  //
  private static final int LOCK_ALLOCATION_UNIT_SIZE  =  100;
  private static final int LOCK_ALLOCATION_UNIT_COUNT =  2500;  // TEMP SUSAN
          static final int MAX_LOCKS = LOCK_ALLOCATION_UNIT_SIZE * LOCK_ALLOCATION_UNIT_COUNT ;
          static final int INIT_LOCKS = 4096;

  private static VM_ProcessorLock lockAllocationMutex;
  private static int              lockUnitsAllocated;
  private static VM_Lock          globalFreeLock;
  private static int              globalFreeLocks;

  /**
   * Sets up the data structures for holding heavy-weight locks.
   */
  static void init() throws VM_PragmaInterruptible {
    lockAllocationMutex = new VM_ProcessorLock();
    VM_Scheduler.locks  = new VM_Lock[INIT_LOCKS+1]; // don't use slot 0
    if (VM.VerifyAssertions) // check that each potential lock is addressable
      VM._assert((VM_Scheduler.locks.length-1<=(VM_ThinLockConstants.TL_LOCK_ID_MASK>>>VM_ThinLockConstants.TL_LOCK_ID_SHIFT))
                || (VM_ThinLockConstants.TL_LOCK_ID_MASK==-1));
  }
  
  static void growLocks() throws VM_PragmaLogicallyUninterruptible /* ok because the caller is prepared to lose control when it allocates a lock -- dave */ {
    VM_Lock [] oldLocks = VM_Scheduler.locks;
    int newSize = 2 * oldLocks.length;
    if (newSize > MAX_LOCKS + 1)
      VM.sysFail("Cannot grow lock array greater than maximum possible index");
    VM_Lock [] newLocks = new VM_Lock[newSize];
    for (int i=0; i<oldLocks.length; i++)
      newLocks[i] = oldLocks[i];
    VM_Scheduler.locks = newLocks;
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
  static VM_Lock allocate () throws VM_PragmaLogicallyUninterruptible /* ok because the caller is prepared to lose control when it allocates a lock -- dave */ {
    VM_Processor mine = VM_Processor.getCurrentProcessor();
    if (mine.isInitialized && !mine.threadSwitchingEnabled()) return null; // Collector threads can't use heavy locks because they don't fix up their stacks after moving objects
    if ((mine.freeLocks == 0) && (0 < globalFreeLocks) && balanceFreeLocks) {
      localizeFreeLocks();
    }
    VM_Lock l = mine.freeLock;
    if (l != null) {
      mine.freeLock = l.nextFreeLock;
      l.nextFreeLock = null;
      mine.freeLocks--;
      l.active = true;
    } else {
      l = new VM_Lock(); // may cause thread switch (and processor loss)
      mine = VM_Processor.getCurrentProcessor();
      if (mine.lastLockIndex < mine.nextLockIndex) {
        lockAllocationMutex.lock();
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
      while (l.index >= VM_Scheduler.locks.length)
        growLocks();
      VM_Scheduler.locks[l.index] = l;
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
   *
   * @return a free VM_Lock; or <code>null</code>, if garbage collection is not enabled
   */
  static void free (VM_Lock l) {
    l.active = false;
    VM_Processor mine = VM_Processor.getCurrentProcessor();
    l.nextFreeLock = mine.freeLock;
    mine.freeLock  = l;
    mine.freeLocks ++;
    mine.locksFreed++;
  }

  /**
   * Transfers free heavy-weight locks from a processor local
   * structure to a global one.
   *
   * Only used if RVM_WITH_FREE_LOCK_BALANCING preprocessor
   * directive is set for the current build.
   */
  private static void globalizeFreeLocks () {
    VM_Processor mine = VM_Processor.getCurrentProcessor();
    if (mine.freeLocks <= LOCK_ALLOCATION_UNIT_SIZE) {
      if (VM.VerifyAssertions) VM._assert(mine.freeLock != null);
      VM_Lock q = mine.freeLock;
      while (q.nextFreeLock != null) {
        q = q.nextFreeLock;
      }
      lockAllocationMutex.lock();
      q.nextFreeLock  = globalFreeLock;
      globalFreeLock  = mine.freeLock;
      globalFreeLocks += mine.freeLocks;
      lockAllocationMutex.unlock();
      mine.freeLock   = null;
      mine.freeLocks  = 0;
    } else {
      VM_Lock p = null;
      VM_Lock q = mine.freeLock;
      for (int i=0; i<LOCK_ALLOCATION_UNIT_SIZE; i++) {
        p = q;
        q = q.nextFreeLock;
      }
      lockAllocationMutex.lock();
      p.nextFreeLock   = globalFreeLock;
      globalFreeLock   = mine.freeLock;
      globalFreeLocks += LOCK_ALLOCATION_UNIT_SIZE;
      lockAllocationMutex.unlock();
      mine.freeLock    = q;
      mine.freeLocks  -= LOCK_ALLOCATION_UNIT_SIZE;
    }
  }

  /**
   * Transfers free heavy-weight locks from a global structure to a
   * processor local one.
   *
   * Only used if RVM_WITH_FREE_LOCK_BALANCING preprocessor
   * directive is set for the current build.  
   */
  private static void localizeFreeLocks () {
    if (true) return; // TEMP
    VM_Processor mine = VM_Processor.getCurrentProcessor();
    if (VM.VerifyAssertions) VM._assert(mine.freeLock == null);
    lockAllocationMutex.lock();
    if (globalFreeLocks <= LOCK_ALLOCATION_UNIT_SIZE){
      mine.freeLock   = globalFreeLock;
      mine.freeLocks  = globalFreeLocks;
      globalFreeLock  = null;
      globalFreeLocks = 0;
    } else {
      VM_Lock p = null;
      VM_Lock q = globalFreeLock;
      for (int i=0; i<LOCK_ALLOCATION_UNIT_SIZE; i++) {
        p = q;
        q = q.nextFreeLock;
      }
      p.nextFreeLock   = null;
      mine.freeLock    = globalFreeLock;
      mine.freeLocks   = LOCK_ALLOCATION_UNIT_SIZE;
      globalFreeLock   = q;
      globalFreeLocks -= LOCK_ALLOCATION_UNIT_SIZE;
    }
    lockAllocationMutex.unlock();
  }

  static void raiseIllegalMonitorStateException(String msg, Object o) throws VM_PragmaLogicallyUninterruptible {
    throw new IllegalMonitorStateException(msg + o);
  }


  ///////////////////////////////////////////////////////////////
  /// Section 4: Support for debugging and performance tuning ///
  ///////////////////////////////////////////////////////////////

//-#if RVM_WITH_FREE_LOCK_BALANCING
  /**
   * Attempt to keep the roughly equal sized pools for free
   * heavy-weight locks on each processor.
   *
   * Preprocessor directive RVM_WITH_FREE_LOCK_BALANCING=1.  
   */
  private static final boolean balanceFreeLocks = true;
//-#else
  /**
   * Don't attempt to keep the roughly equal sized pools for free
   * heavy-weight locks on each processor.  Each virual processor
   * keeps its own free pool of the locks that it has freed 
   * (deflated).
   *
   * Preprocessor directive RVM_WITH_FREE_LOCK_BALANCING=0.
   */
  private static final boolean balanceFreeLocks = false;
//-#endif

//-#if RVM_WITH_TENTATIVE_MICROLOCKING
  /**
   * Give up the attempt to get a heavy-weight lock, if its
   * <code>mutex</code> microlock is held by another procesor.
   *
   * Preprocessor directive RVM_WITH_TENTATIVE_MICROLOCKING=1.
   */
  private static final boolean tentativeMicrolocking = true;
//-#else
  /**
   * Persist in attempting to get a heavy-weight lock, if its
   * <code>mutex</code> microlock is held by another procesor.
   *
   * Preprocessor directive RVM_WITH_TENTATIVE_MICROLOCKING=0.
   */
  private static final boolean tentativeMicrolocking = false;
//-#endif

  /**
   * Reports the state of a heavy-weight lock.
   */
  void dump() {
    if (!active) 
      return;
    VM.sysWrite("Lock "); VM.sysWriteInt(index); VM.sysWrite(":\n");
    VM.sysWrite(" lockedObject: 0x"); VM.sysWriteHex(VM_Magic.objectAsAddress(lockedObject)); 
    VM.sysWrite("   thin lock = "); 
    VM.sysWriteHex(VM_Magic.getMemoryInt(VM_Magic.objectAsAddress(lockedObject).add(VM_ObjectModel.defaultThinLockOffset())));
    VM.sysWrite("\n");

    VM.sysWrite(" ownerId: "); VM.sysWriteInt(ownerId); VM.sysWrite(" recursionCount: "); VM.sysWriteInt(recursionCount); VM.sysWrite("\n");
    VM.sysWrite(" entering: "); entering.dump();
    VM.sysWrite(" waiting: ");  waiting.dump();

    VM.sysWrite(" mutexLatestContender: ");
    if (mutex.latestContender == null) VM.sysWrite("<null>");
    else                     VM.sysWriteHex(mutex.latestContender.id);
    VM.sysWrite("\n");
  }


  /**
   * Does the currently executing thread own the lock on obj?
   * @param obj the object to check
   * @return true if the currently executing thread owns obj, false otherwise
   */
  public static boolean owns(Object o) {
    return owns(o, VM_Processor.getCurrentProcessor().threadId);
  }

  /**
   * Does the given thread own the lock on obj?
   * @param obj the object to check
   * @param tid thread locking id
   * @return true if the currently executing thread owns obj, false otherwise
   */
  static boolean owns(Object o, int tid) {
    com.ibm.JikesRVM.classloader.VM_Type t = VM_Magic.getObjectType(o);
    int thinLockOffset = t.getThinLockOffset();
    if (thinLockOffset == -1) {
      VM_Lock l = VM_LockNursery.findOrCreate(o, false);
      return l != null && l.ownerId == tid;
    } else {
      int bits = VM_Magic.getIntAtOffset(o, thinLockOffset);
      if ((bits & VM_ThinLockConstants.TL_FAT_LOCK_MASK) == 0) {
        // if locked, then locked with a thin lock
        return (bits & VM_ThinLockConstants.TL_THREAD_ID_MASK) == tid;
      } else {
        // if locked, then locked with a fat lock
        int index = (bits & TL_LOCK_ID_MASK) >>> TL_LOCK_ID_SHIFT;
        VM_Lock l = VM_Scheduler.locks[index];
        return l != null && l.ownerId == tid;
      }
    }
  }

    //////////////////////////////////////////////
    //             Statistics                   //
    //////////////////////////////////////////////

  public static void boot () throws VM_PragmaInterruptible {
    VM_Callbacks.addExitMonitor(new VM_Lock.ExitMonitor());
    VM_Callbacks.addAppRunStartMonitor(new VM_Lock.AppRunStartMonitor());
  }

  static final class AppRunStartMonitor implements VM_Callbacks.AppRunStartMonitor {
    public void notifyAppRunStart (String app, int value) {
      if (! STATS) return;
      waitOperations = 0;
      timedWaitOperations = 0;
      notifyOperations = 0;
      notifyAllOperations = 0;
      lockOperations = 0;
      unlockOperations = 0;
      deflations = 0;

      VM_ThinLock.notifyAppRunStart("", 0);
      VM_LockNursery.notifyAppRunStart("", 0);
    }
  }

  static final class ExitMonitor implements VM_Callbacks.ExitMonitor {
    public void notifyExit (int value) {
      if (! STATS) return;

      int totalLocks = lockOperations + VM_ThinLock.fastLocks + VM_ThinLock.slowLocks;

      VM.sysWrite("FatLocks: "); VM.sysWrite(waitOperations);      VM.sysWrite(" wait operations\n");
      VM.sysWrite("FatLocks: "); VM.sysWrite(timedWaitOperations); VM.sysWrite(" timed wait operations\n");
      VM.sysWrite("FatLocks: "); VM.sysWrite(notifyOperations);    VM.sysWrite(" notify operations\n");
      VM.sysWrite("FatLocks: "); VM.sysWrite(notifyAllOperations); VM.sysWrite(" notifyAll operations\n");
      VM.sysWrite("FatLocks: "); VM.sysWrite(lockOperations);      VM.sysWrite(" locks");
      VM_Stats.percentage(lockOperations, totalLocks, "all lock operations");
      VM.sysWrite("FatLocks: "); VM.sysWrite(unlockOperations);    VM.sysWrite(" unlock operations\n");
      VM.sysWrite("FatLocks: "); VM.sysWrite(deflations);          VM.sysWrite(" deflations\n");

      VM_ThinLock.notifyExit(totalLocks);
      VM_LockNursery.notifyExit(totalLocks);
    }
  }

  static final boolean STATS = false;

  static int waitOperations;
  static int timedWaitOperations;
  static int notifyOperations;
  static int notifyAllOperations;
  static int lockOperations;
  static int unlockOperations;
  static int deflations;

}
