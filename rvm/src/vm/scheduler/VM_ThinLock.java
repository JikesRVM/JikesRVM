/*
 * (C) Copyright IBM Corp. 2001
 */
//$Id$
package com.ibm.JikesRVM;

import com.ibm.JikesRVM.classloader.*;
import org.vmmagic.pragma.*;
import org.vmmagic.unboxed.*;

/**
 * Implementation of thin locks.
 * 
 * @author Bowen Alpern
 * @author David Bacon
 * @author Dave Grove
 * @author Derek Lieber
 */
final class VM_ThinLock implements VM_ThinLockConstants, Uninterruptible {

  ////////////////////////////////////////
  /// Support for light-weight locking ///
  ////////////////////////////////////////

  /**
   * Obtains a lock on the indicated object.  Abreviated light-weight
   * locking sequence inlined by the optimizing compiler for the
   * prologue of synchronized methods and for the
   * <code>monitorenter</code> bytecode.
   *
   * @param o the object to be locked 
   * @param lockOffset the offset of the thin lock word in the object.
   * @see com.ibm.JikesRVM.opt.OPT_ExpandRuntimeServices
   */
  static void inlineLock(Object o, int lockOffset) throws InlinePragma {
    Word old = VM_Magic.prepareWord(o, lockOffset);
    if (old.rshl(TL_THREAD_ID_SHIFT).isZero()) { 
      // implies that fatbit == 0 & threadid == 0
      int threadId = VM_Processor.getCurrentProcessor().threadId;
      if (VM_Magic.attemptWord(o, lockOffset, old, old.or(Word.fromIntZeroExtend(threadId)))) {
        VM_Magic.isync(); // don't use stale prefetched data in monitor
        if (STATS) fastLocks++;
        return;           // common case: o is now locked
      }
    }
    lock(o, lockOffset); // uncommon case: default to out-of-line lock()
  }

  /**
   * Releases the lock on the indicated object.  Abreviated
   * light-weight unlocking sequence inlined by the optimizing
   * compiler for the epilogue of synchronized methods and for the
   * <code>monitorexit</code> bytecode.
   *
   * @param o the object to be unlocked 
   * @param lockOffset the offset of the thin lock word in the object.
   * @see com.ibm.JikesRVM.opt.OPT_ExpandRuntimeServices
   */
  static void inlineUnlock(Object o, int lockOffset) throws InlinePragma {
    Word old = VM_Magic.prepareWord(o, lockOffset);
    Word threadId = Word.fromIntZeroExtend(VM_Processor.getCurrentProcessor().threadId);
    if (old.xor(threadId).rshl(TL_LOCK_COUNT_SHIFT).isZero()) { // implies that fatbit == 0 && count == 0 && lockid == me
      VM_Magic.sync(); // memory barrier: subsequent locker will see previous writes
      if (VM_Magic.attemptWord(o, lockOffset, old, old.and(TL_UNLOCK_MASK))) {
        return; // common case: o is now unlocked
      }
    }
    unlock(o, lockOffset);  // uncommon case: default to non inlined unlock()
  }

  /**
   * Obtains a lock on the indicated object.  Light-weight locking
   * sequence for the prologue of synchronized methods and for the
   * <code>monitorenter</code> bytecode.
   *
   * @param o the object to be locked 
   * @param lockOffset the offset of the thin lock word in the object.
   */
  static void lock(Object o, int lockOffset) throws NoInlinePragma {
major: while (true) { // repeat only if attempt to lock a promoted lock fails
         int retries = retryLimit;
         Word threadId = Word.fromIntZeroExtend(VM_Processor.getCurrentProcessor().threadId);
minor:  while (0 != retries--) { // repeat if there is contention for thin lock
          Word old = VM_Magic.prepareWord(o, lockOffset);
          Word id = old.and(TL_THREAD_ID_MASK.or(TL_FAT_LOCK_MASK));
          if (id.isZero()) { // o isn't locked
            if (VM_Magic.attemptWord(o, lockOffset, old, old.or(threadId))) {
              VM_Magic.isync(); // don't use stale prefetched data in monitor
              if (STATS) slowLocks++;
              break major;  // lock succeeds
            }
            continue minor; // contention, possibly spurious, try again
          }
          if (id.EQ(threadId)) { // this thread has o locked already
            Word changed = old.toAddress().add(TL_LOCK_COUNT_UNIT).toWord(); // update count
            if (changed.and(TL_LOCK_COUNT_MASK).isZero()) { // count wrapped around (most unlikely), make heavy lock
              while (!inflateAndLock(o, lockOffset)) { // wait for a lock to become available
                if (VM_Processor.getCurrentProcessor().threadSwitchingEnabled())
                  VM_Thread.yield();;
              }
              break major;  // lock succeeds (note that lockHeavy has issued an isync)
            }
            if (VM_Magic.attemptWord(o, lockOffset, old, changed)) {
              VM_Magic.isync(); // don't use stale prefetched data in monitor !!TODO: is this isync required?
              if (STATS) slowLocks++;
              break major;  // lock succeeds
            }
            continue minor; // contention, probably spurious, try again (TODO!! worry about this)
          }

          if (!(old.and(TL_FAT_LOCK_MASK).isZero())) { // o has a heavy lock
            int index = old.and(TL_LOCK_ID_MASK).rshl(TL_LOCK_ID_SHIFT).toInt();
            while (index >= VM_Scheduler.locks.length) 
              VM_Lock.growLocks();
            if (VM_Scheduler.locks[index].lockHeavy(o)) {
              break major; // lock succeeds (note that lockHeavy has issued an isync)
            }
            // heavy lock failed (deflated or contention for system lock)
            if (VM_Processor.getCurrentProcessor().threadSwitchingEnabled()) {
              VM_Thread.yield(); // wait, hope o gets unlocked
            }
            continue major;    // try again
          }
          // real contention: wait (hope other thread unlocks o), try again
          if (traceContention) { // for performance tuning only (see section 5)
            Address fp = VM_Magic.getFramePointer();
            fp = VM_Magic.getCallerFramePointer(fp);
            int mid = VM_Magic.getCompiledMethodID(fp);
            VM_Method m1 = VM_CompiledMethods.getCompiledMethod(mid).getMethod();
            fp = VM_Magic.getCallerFramePointer(fp);
            mid = VM_Magic.getCompiledMethodID(fp);
            VM_Method m2 = VM_CompiledMethods.getCompiledMethod(mid).getMethod();
            String s = m1.getDeclaringClass() + "." + m1.getName() + " " + m2.getDeclaringClass() + "." + m2.getName();
            VM_Scheduler.trace(VM_Magic.getObjectType(o).toString(), s, -2-retries);
          }
          if (0 != retries && VM_Processor.getCurrentProcessor().threadSwitchingEnabled()) {
            VM_Thread.yield(); // wait, hope o gets unlocked
          }
        }
        // create a heavy lock for o and lock it
        if (inflateAndLock(o, lockOffset)) break major;
       }
       // o has been locked, must return before an exception can be thrown
  }

  /**
   * Releases the lock on the indicated object.   Light-weight unlocking
   * sequence for the epilogue of synchronized methods and for the
   * <code>monitorexit</code> bytecode.
   *
   * @param o the object to be locked 
   * @param lockOffset the offset of the thin lock word in the object.
   */
  static void unlock(Object o, int lockOffset) throws NoInlinePragma {
    VM_Magic.sync(); // prevents stale data from being seen by next owner of the lock
    while (true) { // spurious contention detected
      Word old = VM_Magic.prepareWord(o, lockOffset);
      Word id  = old.and(TL_THREAD_ID_MASK.or(TL_FAT_LOCK_MASK));
      Word threadId = Word.fromIntZeroExtend(VM_Processor.getCurrentProcessor().threadId);
      if (id.NE(threadId)) { // not normal case
        if (!(old.and(TL_FAT_LOCK_MASK).isZero())) { // o has a heavy lock
          int index = old.and(TL_LOCK_ID_MASK).rshl(TL_LOCK_ID_SHIFT).toInt();
          VM_Scheduler.locks[index].unlockHeavy(o); 
          // note that unlockHeavy has issued a sync
          return;
        } 
        VM_Scheduler.trace("VM_Lock", "unlock error: thin lock word = ", old.toAddress());
        VM_Scheduler.trace("VM_Lock", "unlock error: thin lock word = ", VM_Magic.objectAsAddress(o));
        // VM_Scheduler.trace("VM_Lock", VM_Thread.getCurrentThread().toString(), 0);
        VM_Lock.raiseIllegalMonitorStateException("thin unlocking", o);
      }
      if (old.and(TL_LOCK_COUNT_MASK).isZero()) { // get count, 0 is the last lock
        Word changed = old.and(TL_UNLOCK_MASK);
        if (VM_Magic.attemptWord(o, lockOffset, old, changed)) {
          return; // unlock succeeds
        } 
        continue;
      }
      // more than one lock
      // decrement recursion count
      Word changed = old.toAddress().sub(TL_LOCK_COUNT_UNIT).toWord(); 
      if (VM_Magic.attemptWord(o, lockOffset, old, changed)) {
        return; // unlock succeeds
      }
    }
  }

  ////////////////////////////////////////////////////////////////
  /// Support for inflating (and deflating) heavy-weight locks ///
  ////////////////////////////////////////////////////////////////

  /**
   * Promotes a light-weight lock to a heavy-weight lock.  Note: the
   * object is question will normally be locked by another thread,
   * or it may be unlocked.  If there is already a heavy-weight lock
   * on this object, that lock is returned.
   *
   * @param o the object to get a heavy-weight lock 
   * @param lockOffset the offset of the thin lock word in the object.
   * @return the heavy-weight lock on this object
   */
  private static VM_Lock inflate (Object o, int lockOffset) {
    Word old;
    Word changed;
    VM_Lock l = VM_Lock.allocate();
    if (VM.VerifyAssertions) VM._assert(l != null); // inflate called by wait (or notify) which shouldn't be called during GC
    Word locked = TL_FAT_LOCK_MASK.or(Word.fromIntZeroExtend(l.index).lsh(TL_LOCK_ID_SHIFT));
    l.mutex.lock();
    do {
      old = VM_Magic.prepareWord(o, lockOffset);
      // check to see if another thread has already created a fat lock
      if (!(old.and(TL_FAT_LOCK_MASK).isZero())) { // already a fat lock in place
        int index = old.and(TL_LOCK_ID_MASK).rshl(TL_LOCK_ID_SHIFT).toInt();
        VM_Lock.free(l);
        l.mutex.unlock();
        l = VM_Scheduler.locks[index];
        return l;
      }
      changed = locked.or(old.and(TL_UNLOCK_MASK));
      if (VM_Magic.attemptWord(o, lockOffset, old, changed)) {
        l.lockedObject = o;
        l.ownerId      = old.and(TL_THREAD_ID_MASK).toInt();
        if (l.ownerId != 0) 
          l.recursionCount = old.and(TL_LOCK_COUNT_MASK).rshl(TL_LOCK_COUNT_SHIFT).toInt() +1;
        l.mutex.unlock();
        return l;      // VM_Lock in place
      }
      // contention detected, try again
    } while (true);
  }

  static void deflate (Object o, int lockOffset, VM_Lock l) {
    if (VM.VerifyAssertions) {
      Word old = VM_Magic.getWordAtOffset(o, lockOffset);
      VM._assert(!(old.and(TL_FAT_LOCK_MASK).isZero()));
      VM._assert(l == VM_Scheduler.locks[old.and(TL_LOCK_ID_MASK).rshl(TL_LOCK_ID_SHIFT).toInt()]);
    }
    Word old;
    Word changed;
    do {
      old = VM_Magic.prepareWord(o, lockOffset);
    } while (!VM_Magic.attemptWord(o, lockOffset, old, old.and(TL_UNLOCK_MASK)));
  }


  /**
   * Promotes a light-weight lock to a heavy-weight lock and locks it.
   * Note: the object is question will normally be locked by another
   * thread, or it may be unlocked.  If there is already a
   * heavy-weight lock on this object, that lock is returned.
   *
   * @param o the object to get a heavy-weight lock 
   * @param lockOffset the offset of the thin lock word in the object.
   * @return whether the object was successfully locked
   */
  private static boolean inflateAndLock (Object o, int lockOffset) {
    Word old;
    Word changed;
    VM_Lock l = VM_Lock.allocate();
    if (l == null) return false; // can't allocate locks during GC
    Word locked = TL_FAT_LOCK_MASK.or(Word.fromIntZeroExtend(l.index).lsh(TL_LOCK_ID_SHIFT));
    l.mutex.lock();
    do {
      old = VM_Magic.prepareWord(o, lockOffset);
      // check to see if another thread has already created a fat lock
      if (!(old.and(TL_FAT_LOCK_MASK).isZero())) { // already a fat lock in place
        VM_Lock.free(l);
        l.mutex.unlock();
        int index = old.and(TL_LOCK_ID_MASK).rshl(TL_LOCK_ID_SHIFT).toInt();
        l = VM_Scheduler.locks[index];
        l.mutex.lock();
        if (l.lockedObject == o) break;  // l is heavy lock for o
        l.mutex.unlock();
        return false;
      }
      changed = locked.or(old.and(TL_UNLOCK_MASK));
      if (VM_Magic.attemptWord(o, lockOffset, old, changed)) {
        l.lockedObject = o;
        l.ownerId = old.and(TL_THREAD_ID_MASK).toInt();
        if (l.ownerId != 0) 
          l.recursionCount = old.and(TL_LOCK_COUNT_MASK).rshl(TL_LOCK_COUNT_SHIFT).toInt() +1;
        break;  // l is heavy lock for o
      } 
      // contention detected, try again
    } while (true);
    int threadId = VM_Processor.getCurrentProcessor().threadId; 
    if (l.ownerId == 0) {
      l.ownerId = threadId;
      l.recursionCount = 1;
    } else if (l.ownerId == threadId) {
      l.recursionCount++;
    } else if (VM_Processor.getCurrentProcessor().threadSwitchingEnabled()) {
      VM_Thread.yield(l.entering, l.mutex); // thread-switching benign
      // when this thread next gets scheduled, it will be entitled to the lock,
      // but another thread might grab it first.
      return false; // caller will try again
    } else { // can't yield - must spin and let caller retry
      // potential deadlock if user thread is contending for a lock with thread switching disabled
      if (VM.VerifyAssertions) VM._assert(VM_Thread.getCurrentThread().isGCThread);
      l.mutex.unlock(); // thread-switching benign
      return false; // caller will try again
    }
    l.mutex.unlock(); // thread-switching benign
    return true;
  }


  ////////////////////////////////////////////////////////////////////////////
  /// Get heavy-weight lock for an object; if thin, inflate it.
  ////////////////////////////////////////////////////////////////////////////

  /**
   * Obtains the heavy-weight lock, if there is one, associated with the
   * indicated object.  Returns <code>null</code>, if there is no
   * heavy-weight lock associated with the object.
   *
   * @param o the object from which a lock is desired
   * @param lockOffset the offset of the thin lock word in the object.
   * @param create if true, create heavy lock if none found
   * @return the heavy-weight lock on the object (if any)
   */
  static VM_Lock getHeavyLock (Object o, int lockOffset, boolean create) {
    Word old = VM_Magic.getWordAtOffset(o, lockOffset);
    if (!(old.and(TL_FAT_LOCK_MASK).isZero())) { // already a fat lock in place
      int index = old.and(TL_LOCK_ID_MASK).rshl(TL_LOCK_ID_SHIFT).toInt();
      return VM_Scheduler.locks[index];
    } else if (create) {
      return inflate(o, lockOffset);
    } else {
      return null;
    }

  }

  ///////////////////////////////////////////////////////////////
  /// Support for debugging and performance tuning ///
  ///////////////////////////////////////////////////////////////

  //-#if RVM_WITH_VARIABLE_LOCK_RETRY_LIMIT
  /**
   * Number of times a thread yields before inflating the lock on a
   * object to a heavy-weight lock.  The current value was for the
   * portBOB benchmark on a 12-way SMP (AIX) in the Fall of '99.  This
   * is almost certainly not the optimal value.
   *
   * Preprocessor directive RVM_WITH_VARIABLE_LOCK_RETRY_LIMIT=1.  
   */
  private static int retryLimit = 40; // (-1 is effectively infinity)
  //-#else
  /**
   * Number of times a thread yields before inflating the lock on a
   * object to a heavy-weight lock.  The current value was for the
   * portBOB benchmark on a 12-way SMP (AIX) in the Fall of '99.  This
   * is almost certainly not the optimal value.
   *
   * Preprocessor directive RVM_WITH_VARIABLE_LOCK_RETRY_LIMIT=0.
   */
  private static final int retryLimit = 40; // (-1 is effectively infinity)
  //-#endif

  //-#if RVM_WITH_LOCK_CONTENTION_TRACING
  /**
   * Report lock contention (for debugging).
   *
   * Preprocessor directive RVM_WITH_LOCK_CONTENTION_TRACING=1.
   */
  private static final boolean traceContention = true;
  //-#else
  /**
   * Don't report lock contention. 
   *
   * Preprocessor directive RVM_WITH_LOCK_CONTENTION_TRACING=0.
   */
  private static final boolean traceContention = false;
  //-#endif

  //////////////////////////////////////////////
  //             Statistics                   //
  //////////////////////////////////////////////

  static final boolean STATS = VM_Lock.STATS;

  static int fastLocks;
  static int slowLocks;

  static void notifyAppRunStart (String app, int value) {
    if (!STATS) return;
    fastLocks = 0;
    slowLocks = 0;
  }

  static void notifyExit (int value) {
    if (!STATS) return;
    VM.sysWrite("ThinLocks: "); VM.sysWrite(fastLocks);      VM.sysWrite(" fast locks");
    VM_Stats.percentage(fastLocks, value, "all lock operations");
    VM.sysWrite("ThinLocks: "); VM.sysWrite(slowLocks);      VM.sysWrite(" slow locks");
    VM_Stats.percentage(slowLocks, value, "all lock operations");
  }

}
