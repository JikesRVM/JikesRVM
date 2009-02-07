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
import org.jikesrvm.Services;
import org.jikesrvm.classloader.RVMMethod;
import org.jikesrvm.compilers.common.CompiledMethods;
import org.jikesrvm.objectmodel.ThinLockConstants;
import org.jikesrvm.runtime.Magic;
import org.vmmagic.pragma.Entrypoint;
import org.vmmagic.pragma.Inline;
import org.vmmagic.pragma.NoInline;
import org.vmmagic.pragma.Uninterruptible;
import org.vmmagic.pragma.Unpreemptible;
import org.vmmagic.unboxed.Address;
import org.vmmagic.unboxed.Offset;
import org.vmmagic.unboxed.Word;

/**
 * Implementation of thin locks.
 */
@Uninterruptible
public final class ThinLock implements ThinLockConstants {

  ////////////////////////////////////////
  /// Support for light-weight locking ///
  ////////////////////////////////////////

  /**
   * Return the lock index for a given lock word.  Assert valid index
   * ranges, that the fat lock bit is set, and that the lock entry
   * exists.
   *
   * @param lockWord The lock word whose lock index is being established
   * @return the lock index corresponding to the lock workd.
   */
  @Inline
  private static int getLockIndex(Word lockWord) {
    int index = lockWord.and(TL_LOCK_ID_MASK).rshl(TL_LOCK_ID_SHIFT).toInt();
    if (VM.VerifyAssertions) {
      if (!(index > 0 && index < Lock.numLocks())) {
        VM.sysWrite("Lock index out of range! Word: "); VM.sysWrite(lockWord);
        VM.sysWrite(" index: "); VM.sysWrite(index);
        VM.sysWrite(" locks: "); VM.sysWrite(Lock.numLocks());
        VM.sysWriteln();
      }
      VM._assert(index > 0 && index < Lock.numLocks());  // index is in range
      VM._assert(!lockWord.and(TL_FAT_LOCK_MASK).isZero());        // fat lock bit is set
      VM._assert(Lock.getLock(index) != null);               // the lock is actually there
    }
    return index;
  }

  /**
   * Obtains a lock on the indicated object.  Abbreviated light-weight
   * locking sequence inlined by the optimizing compiler for the
   * prologue of synchronized methods and for the
   * <code>monitorenter</code> bytecode.
   *
   * @param o the object to be locked
   * @param lockOffset the offset of the thin lock word in the object.
   * @see org.jikesrvm.compilers.opt.hir2lir.ExpandRuntimeServices
   */
  @Inline
  @Entrypoint
  @Unpreemptible("Become another thread when lock is contended, don't preempt in other cases")
  static void inlineLock(Object o, Offset lockOffset) {
    Word old = Magic.prepareWord(o, lockOffset);
    if (old.rshl(TL_THREAD_ID_SHIFT).isZero()) {
      // implies that fatbit == 0 & threadid == 0
      int threadId = RVMThread.getCurrentThread().getLockingId();
      if (Magic.attemptWord(o, lockOffset, old, old.or(Word.fromIntZeroExtend(threadId)))) {
        Magic.isync(); // don't use stale prefetched data in monitor
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
   * @see org.jikesrvm.compilers.opt.hir2lir.ExpandRuntimeServices
   */
  @Inline
  @Entrypoint
  @Unpreemptible("No preemption normally, but may raise exceptions")
  static void inlineUnlock(Object o, Offset lockOffset) {
    Word old = Magic.prepareWord(o, lockOffset);
    Word threadId = Word.fromIntZeroExtend(RVMThread.getCurrentThread().getLockingId());
    if (old.xor(threadId).rshl(TL_LOCK_COUNT_SHIFT).isZero()) { // implies that fatbit == 0 && count == 0 && lockid == me
      Magic.sync(); // memory barrier: subsequent locker will see previous writes
      if (Magic.attemptWord(o, lockOffset, old, old.and(TL_UNLOCK_MASK))) {
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
  @NoInline
  @Unpreemptible("Become another thread when lock is contended, don't preempt in other cases")
  public static void lock(Object o, Offset lockOffset) {
    major:
    while (true) { // repeat only if attempt to lock a promoted lock fails
      int retries = retryLimit;
      Word threadId = Word.fromIntZeroExtend(RVMThread.getCurrentThread().getLockingId());
      while (0 != retries--) { // repeat if there is contention for thin lock
        Word old = Magic.prepareWord(o, lockOffset);
        Word id = old.and(TL_THREAD_ID_MASK.or(TL_FAT_LOCK_MASK));
        if (id.isZero()) { // o isn't locked
          if (Magic.attemptWord(o, lockOffset, old, old.or(threadId))) {
            Magic.isync(); // don't use stale prefetched data in monitor
            if (STATS) slowLocks++;
            break major;  // lock succeeds
          }
          continue; // contention, possibly spurious, try again
        }
        if (id.EQ(threadId)) { // this thread has o locked already
          Word changed = old.toAddress().plus(TL_LOCK_COUNT_UNIT).toWord(); // update count
          if (changed.and(TL_LOCK_COUNT_MASK).isZero()) { // count wrapped around (most unlikely), make heavy lock
            while (!inflateAndLock(o, lockOffset)) { // wait for a lock to become available
              RVMThread.yield();
            }
            break major;  // lock succeeds (note that lockHeavy has issued an isync)
          }
          if (Magic.attemptWord(o, lockOffset, old, changed)) {
            Magic.isync(); // don't use stale prefetched data in monitor !!TODO: is this isync required?
            if (STATS) slowLocks++;
            break major;  // lock succeeds
          }
          continue; // contention, probably spurious, try again (TODO!! worry about this)
        }

        if (!(old.and(TL_FAT_LOCK_MASK).isZero())) { // o has a heavy lock
          int index = getLockIndex(old);
          if (Lock.getLock(index).lockHeavy(o)) {
            break major; // lock succeeds (note that lockHeavy has issued an isync)
          }
          // heavy lock failed (deflated or contention for system lock)
          RVMThread.yield();
          continue major;    // try again
        }
        // real contention: wait (hope other thread unlocks o), try again
        if (traceContention) { // for performance tuning only (see section 5)
          Address fp = Magic.getFramePointer();
          fp = Magic.getCallerFramePointer(fp);
          int mid = Magic.getCompiledMethodID(fp);
          RVMMethod m1 = CompiledMethods.getCompiledMethod(mid).getMethod();
          fp = Magic.getCallerFramePointer(fp);
          mid = Magic.getCompiledMethodID(fp);
          RVMMethod m2 = CompiledMethods.getCompiledMethod(mid).getMethod();
          String s = m1.getDeclaringClass() + "." + m1.getName() + " " + m2.getDeclaringClass() + "." + m2.getName();
          RVMThread.trace(Magic.getObjectType(o).toString(), s, -2 - retries);
        }
        if (0 != retries) {
          RVMThread.yield(); // wait, hope o gets unlocked
        }
      }
      // create a heavy lock for o and lock it
      if (inflateAndLock(o, lockOffset)) break;
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
  @NoInline
  @Unpreemptible("No preemption normally, but may raise exceptions")
  public static void unlock(Object o, Offset lockOffset) {
    Magic.sync(); // prevents stale data from being seen by next owner of the lock
    while (true) { // spurious contention detected
      Word old = Magic.prepareWord(o, lockOffset);
      Word id = old.and(TL_THREAD_ID_MASK.or(TL_FAT_LOCK_MASK));
      Word threadId = Word.fromIntZeroExtend(RVMThread.getCurrentThread().getLockingId());
      if (id.NE(threadId)) { // not normal case
        if (!(old.and(TL_FAT_LOCK_MASK).isZero())) { // o has a heavy lock
          Lock.getLock(getLockIndex(old)).unlockHeavy(o);
          // note that unlockHeavy has issued a sync
          return;
        }
        RVMThread.trace("Lock", "unlock error: thin lock word = ", old.toAddress());
        RVMThread.trace("Lock", "unlock error: thin lock word = ", Magic.objectAsAddress(o));
        // RVMThread.trace("Lock", RVMThread.getCurrentThread().toString(), 0);
        RVMThread.raiseIllegalMonitorStateException("thin unlocking", o);
      }
      if (old.and(TL_LOCK_COUNT_MASK).isZero()) { // get count, 0 is the last lock
        Word changed = old.and(TL_UNLOCK_MASK);
        if (Magic.attemptWord(o, lockOffset, old, changed)) {
          return; // unlock succeeds
        }
        continue;
      }
      // more than one lock
      // decrement recursion count
      Word changed = old.toAddress().minus(TL_LOCK_COUNT_UNIT).toWord();
      if (Magic.attemptWord(o, lockOffset, old, changed)) {
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
  @Unpreemptible
  private static Lock inflate(Object o, Offset lockOffset) {
    if (VM.VerifyAssertions) {
      VM._assert(holdsLock(o, lockOffset, RVMThread.getCurrentThread()));
      // this assertions is just plain wrong.
      //VM._assert((Magic.getWordAtOffset(o, lockOffset).and(TL_FAT_LOCK_MASK).isZero()));
    }
    Lock l = Lock.allocate();
    if (VM.VerifyAssertions) {
      VM._assert(l != null); // inflate called by wait (or notify) which shouldn't be called during GC
    }
    Lock rtn = attemptToInflate(o, lockOffset, l);
    if (rtn == l)
      l.mutex.unlock();
    return rtn;
  }

  /**
   * Promotes a light-weight lock to a heavy-weight lock and locks it.
   * Note: the object in question will normally be locked by another
   * thread, or it may be unlocked.  If there is already a
   * heavy-weight lock on this object, that lock is returned.
   *
   * @param o the object to get a heavy-weight lock
   * @param lockOffset the offset of the thin lock word in the object.
   * @return whether the object was successfully locked
   */
  @Unpreemptible
  private static boolean inflateAndLock(Object o, Offset lockOffset) {
    Lock l = Lock.allocate();
    if (l == null) return false; // can't allocate locks during GC
    Lock rtn = attemptToInflate(o, lockOffset, l);
    if (l != rtn) {
      l = rtn;
      l.mutex.lock();
    }
    return l.lockHeavyLocked(o);
  }

  /**
   * Promotes a light-weight lock to a heavy-weight lock.
   *
   * @param o the object to get a heavy-weight lock
   * @param lockOffset the offset of the thin lock word in the object.
   * @return whether the object was successfully locked
   */
  private static Lock attemptToInflate(Object o, Offset lockOffset, Lock l) {
    Word old;
    l.mutex.lock();
    do {
      old = Magic.prepareWord(o, lockOffset);
      // check to see if another thread has already created a fat lock
      if (!(old.and(TL_FAT_LOCK_MASK).isZero())) { // already a fat lock in place
        if (Lock.trace) {
          VM.sysWriteln("Thread #",RVMThread.getCurrentThreadSlot(),
                        ": freeing lock ",Magic.objectAsAddress(l),
                        " because we had a double-inflate");
        }
        Lock.free(l);
        l.mutex.unlock();
        l = Lock.getLock(getLockIndex(old));
        return l;
      }
      Word locked = TL_FAT_LOCK_MASK.or(Word.fromIntZeroExtend(l.index).lsh(TL_LOCK_ID_SHIFT));
      Word changed = locked.or(old.and(TL_UNLOCK_MASK));
      if (VM.VerifyAssertions) VM._assert(getLockIndex(changed) == l.index);
      if (Magic.attemptWord(o, lockOffset, old, changed)) {
        l.setLockedObject(o);
        l.setOwnerId(old.and(TL_THREAD_ID_MASK).toInt());
        if (l.getOwnerId() != 0) {
          l.setRecursionCount(old.and(TL_LOCK_COUNT_MASK).rshl(TL_LOCK_COUNT_SHIFT).toInt() + 1);
        }
        return l;
      }
      // contention detected, try again
    } while (true);
  }

  public static void deflate(Object o, Offset lockOffset, Lock l) {
    if (VM.VerifyAssertions) {
      Word old = Magic.getWordAtOffset(o, lockOffset);
      VM._assert(!(old.and(TL_FAT_LOCK_MASK).isZero()));
      VM._assert(l == Lock.getLock(getLockIndex(old)));
    }
    Word old;
    do {
      old = Magic.prepareWord(o, lockOffset);
    } while (!Magic.attemptWord(o, lockOffset, old, old.and(TL_UNLOCK_MASK)));
  }

  /**
   * @param obj an object
   * @param lockOffset the offset of the thin lock word in the object.
   * @param thread a thread
   * @return <code>true</code> if the lock on obj at offset lockOffset is currently owned
   *         by thread <code>false</code> if it is not.
   */
  public static boolean holdsLock(Object obj, Offset lockOffset, RVMThread thread) {
    int tid = thread.getLockingId();
    Word bits = Magic.getWordAtOffset(obj, lockOffset);
    if (bits.and(TL_FAT_LOCK_MASK).isZero()) {
      // if locked, then it is locked with a thin lock
      return (bits.and(ThinLockConstants.TL_THREAD_ID_MASK).toInt() == tid);
    } else {
      // if locked, then it is locked with a fat lock
      int index = getLockIndex(bits);
      Lock l = Lock.getLock(index);
      return l != null && l.getOwnerId() == tid;
    }
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
  @Unpreemptible
  public static Lock getHeavyLock(Object o, Offset lockOffset, boolean create) {
    Word old = Magic.getWordAtOffset(o, lockOffset);
    if (!(old.and(TL_FAT_LOCK_MASK).isZero())) { // already a fat lock in place
      return Lock.getLock(getLockIndex(old));
    } else if (create) {
      return inflate(o, lockOffset);
    } else {
      return null;
    }

  }

  ///////////////////////////////////////////////////////////////
  /// Support for debugging and performance tuning ///
  ///////////////////////////////////////////////////////////////

  /**
   * Number of times a thread yields before inflating the lock on a
   * object to a heavy-weight lock.  The current value was for the
   * portBOB benchmark on a 12-way SMP (AIX) in the Fall of '99.  This
   * is almost certainly not the optimal value.
   */
  private static final int retryLimit = 40; // (-1 is effectively infinity)

  /**
   * Should we trace lockContention to enable debugging?
   */
  private static final boolean traceContention = false;

  //////////////////////////////////////////////
  //             Statistics                   //
  //////////////////////////////////////////////

  static final boolean STATS = Lock.STATS;

  static int fastLocks;
  static int slowLocks;

  static void notifyAppRunStart(String app, int value) {
    if (!STATS) return;
    fastLocks = 0;
    slowLocks = 0;
  }

  static void notifyExit(int value) {
    if (!STATS) return;
    VM.sysWrite("ThinLocks: ");
    VM.sysWrite(fastLocks);
    VM.sysWrite(" fast locks");
    Services.percentage(fastLocks, value, "all lock operations");
    VM.sysWrite("ThinLocks: ");
    VM.sysWrite(slowLocks);
    VM.sysWrite(" slow locks");
    Services.percentage(slowLocks, value, "all lock operations");
  }

}

