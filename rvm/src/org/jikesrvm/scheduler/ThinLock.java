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
import org.jikesrvm.Services;
import org.jikesrvm.objectmodel.ThinLockConstants;
import org.jikesrvm.runtime.Magic;
import org.vmmagic.pragma.Inline;
import org.vmmagic.pragma.NoInline;
import org.vmmagic.pragma.NoNullCheck;
import org.vmmagic.pragma.Uninterruptible;
import org.vmmagic.pragma.Unpreemptible;
import org.vmmagic.unboxed.Offset;
import org.vmmagic.unboxed.Word;

/**
 * Implementation of thin locks.
 */
@Uninterruptible
public final class ThinLock implements ThinLockConstants {

  private static final boolean ENABLE_BIASED_LOCKING = true;

  @Inline
  @NoNullCheck
  @Unpreemptible
  public static void inlineLock(Object o, Offset lockOffset) {
    Word old = Magic.prepareWord(o, lockOffset); // FIXME: bad for PPC?
    Word id = old.and(TL_THREAD_ID_MASK.or(TL_STAT_MASK));
    Word tid = Word.fromIntSignExtend(RVMThread.getCurrentThread().getLockingId());
    if (id.EQ(tid)) {
      Word changed = old.plus(TL_LOCK_COUNT_UNIT);
      if (!changed.and(TL_LOCK_COUNT_MASK).isZero()) {
        setDedicatedU16(o, lockOffset, changed);
        return;
      }
    } else if (id.EQ(TL_STAT_THIN)) {
      // lock is thin and not held by anyone
      if (Magic.attemptWord(o, lockOffset, old, old.or(tid))) {
        Magic.isync();
        return;
      }
    }
    lock(o, lockOffset);
  }

  @Inline
  @NoNullCheck
  @Unpreemptible
  public static void inlineUnlock(Object o, Offset lockOffset) {
    Word old = Magic.prepareWord(o, lockOffset); // FIXME: bad for PPC?
    Word id = old.and(TL_THREAD_ID_MASK.or(TL_STAT_MASK));
    Word tid = Word.fromIntSignExtend(RVMThread.getCurrentThread().getLockingId());
    if (id.EQ(tid)) {
      if (!old.and(TL_LOCK_COUNT_MASK).isZero()) {
        setDedicatedU16(o, lockOffset, old.minus(TL_LOCK_COUNT_UNIT));
        return;
      }
    } else if (old.xor(tid).rshl(TL_LOCK_COUNT_SHIFT).EQ(TL_STAT_THIN.rshl(TL_LOCK_COUNT_SHIFT))) {
      Magic.sync();
      if (Magic.attemptWord(o, lockOffset, old, old.and(TL_UNLOCK_MASK).or(TL_STAT_THIN))) {
        return;
      }
    }
    unlock(o, lockOffset);
  }

  @NoInline
  @NoNullCheck
  @Unpreemptible
  public static void lock(Object o, Offset lockOffset) {
    if (STATS) fastLocks++;

    Word threadId = Word.fromIntZeroExtend(RVMThread.getCurrentThread().getLockingId());

    for (int cnt=0;;cnt++) {
      Word old = Magic.getWordAtOffset(o, lockOffset);
      Word stat = old.and(TL_STAT_MASK);
      boolean tryToInflate=false;
      if (stat.EQ(TL_STAT_BIASABLE)) {
        Word id = old.and(TL_THREAD_ID_MASK);
        if (id.isZero()) {
          if (ENABLE_BIASED_LOCKING) {
            // lock is unbiased, bias it in our favor and grab it
            if (Synchronization.tryCompareAndSwap(
                  o, lockOffset,
                  old,
                  old.or(threadId).plus(TL_LOCK_COUNT_UNIT))) {
              Magic.isync();
              return;
            }
          } else {
            // lock is unbiased but biasing is NOT allowed, so turn it into
            // a thin lock
            if (Synchronization.tryCompareAndSwap(
                  o, lockOffset,
                  old,
                  old.or(threadId).or(TL_STAT_THIN))) {
              Magic.isync();
              return;
            }
          }
        } else if (id.EQ(threadId)) {
          // lock is biased in our favor
          Word changed = old.plus(TL_LOCK_COUNT_UNIT);
          if (!changed.and(TL_LOCK_COUNT_MASK).isZero()) {
            setDedicatedU16(o, lockOffset, changed);
            return;
          } else {
            tryToInflate=true;
          }
        } else {
          if (casFromBiased(o, lockOffset, old, biasBitsToThinBits(old), cnt)) {
            continue; // don't spin, since it's thin now
          }
        }
      } else if (stat.EQ(TL_STAT_THIN)) {
        Word id = old.and(TL_THREAD_ID_MASK);
        if (id.isZero()) {
          if (Synchronization.tryCompareAndSwap(
                o, lockOffset, old, old.or(threadId))) {
            Magic.isync();
            return;
          }
        } else if (id.EQ(threadId)) {
          Word changed = old.plus(TL_LOCK_COUNT_UNIT);
          if (changed.and(TL_LOCK_COUNT_MASK).isZero()) {
            tryToInflate=true;
          } else if (Synchronization.tryCompareAndSwap(
                       o, lockOffset, old, changed)) {
            Magic.isync();
            return;
          }
        } else if (cnt>retryLimit) {
          tryToInflate=true;
        }
      } else {
        if (VM.VerifyAssertions) VM._assert(stat.EQ(TL_STAT_FAT));
        // lock is fat.  contend on it.
        if (Lock.getLock(getLockIndex(old)).lockHeavy(o)) {
          return;
        }
      }

      if (tryToInflate) {
        if (STATS) slowLocks++;
        // the lock is not fat, is owned by someone else, or else the count wrapped.
        // attempt to inflate it (this may fail, in which case we'll just harmlessly
        // loop around) and lock it (may also fail, if we get the wrong lock).  if it
        // succeeds, we're done.
        // NB: this calls into our attemptToMarkInflated() method, which will do the
        // Right Thing if the lock is biased to someone else.
        if (inflateAndLock(o, lockOffset)) {
          return;
        }
      } else {
        RVMThread.yieldNoHandshake();
      }
    }
  }

  @NoInline
  @NoNullCheck
  @Unpreemptible
  public static void unlock(Object o, Offset lockOffset) {
    Word threadId = Word.fromIntZeroExtend(RVMThread.getCurrentThread().getLockingId());
    for (int cnt=0;;cnt++) {
      Word old = Magic.getWordAtOffset(o, lockOffset);
      Word stat = old.and(TL_STAT_MASK);
      if (stat.EQ(TL_STAT_BIASABLE)) {
        Word id = old.and(TL_THREAD_ID_MASK);
        if (id.EQ(threadId)) {
          if (old.and(TL_LOCK_COUNT_MASK).isZero()) {
            RVMThread.raiseIllegalMonitorStateException("biased unlocking: we own this object but the count is already zero", o);
          }
          setDedicatedU16(o, lockOffset, old.minus(TL_LOCK_COUNT_UNIT));
          return;
        } else {
          RVMThread.raiseIllegalMonitorStateException("biased unlocking: we don't own this object", o);
        }
      } else if (stat.EQ(TL_STAT_THIN)) {
        Magic.sync();
        Word id = old.and(TL_THREAD_ID_MASK);
        if (id.EQ(threadId)) {
          Word changed;
          if (old.and(TL_LOCK_COUNT_MASK).isZero()) {
            changed = old.and(TL_UNLOCK_MASK).or(TL_STAT_THIN);
          } else {
            changed = old.minus(TL_LOCK_COUNT_UNIT);
          }
          if (Synchronization.tryCompareAndSwap(
                o, lockOffset, old, changed)) {
            return;
          }
        } else {
          if (false) {
            VM.sysWriteln("threadId = ",threadId);
            VM.sysWriteln("id = ",id);
          }
          RVMThread.raiseIllegalMonitorStateException("thin unlocking: we don't own this object", o);
        }
      } else {
        if (VM.VerifyAssertions) VM._assert(stat.EQ(TL_STAT_FAT));
        // fat unlock
        Lock.getLock(getLockIndex(old)).unlockHeavy(o);
        return;
      }
    }
  }

  @Uninterruptible
  @NoNullCheck
  public static boolean holdsLock(Object o, Offset lockOffset, RVMThread thread) {
    for (int cnt=0;;++cnt) {
      int tid = thread.getLockingId();
      Word bits = Magic.getWordAtOffset(o, lockOffset);
      if (bits.and(TL_STAT_MASK).EQ(TL_STAT_BIASABLE)) {
        // if locked, then it is locked with a thin lock
        return
          bits.and(TL_THREAD_ID_MASK).toInt() == tid &&
          !bits.and(TL_LOCK_COUNT_MASK).isZero();
      } else if (bits.and(TL_STAT_MASK).EQ(TL_STAT_THIN)) {
        return bits.and(TL_THREAD_ID_MASK).toInt()==tid;
      } else {
        if (VM.VerifyAssertions) VM._assert(bits.and(TL_STAT_MASK).EQ(TL_STAT_FAT));
        // if locked, then it is locked with a fat lock
        Lock l=Lock.getLock(getLockIndex(bits));
        if (l!=null) {
          l.mutex.lock();
          boolean result = (l.getOwnerId()==tid && l.getLockedObject()==o);
          l.mutex.unlock();
          return result;
        }
      }
      RVMThread.yieldNoHandshake();
    }
  }

  @Inline
  @Uninterruptible
  public static boolean isFat(Word lockWord) {
    return lockWord.and(TL_STAT_MASK).EQ(TL_STAT_FAT);
  }

  /**
   * Return the lock index for a given lock word.  Assert valid index
   * ranges, that the fat lock bit is set, and that the lock entry
   * exists.
   *
   * @param lockWord The lock word whose lock index is being established
   * @return the lock index corresponding to the lock workd.
   */
  @Inline
  @Uninterruptible
  public static int getLockIndex(Word lockWord) {
    int index = lockWord.and(TL_LOCK_ID_MASK).rshl(TL_LOCK_ID_SHIFT).toInt();
    if (VM.VerifyAssertions) {
      if (!(index > 0 && index < Lock.numLocks())) {
        VM.sysWrite("Lock index out of range! Word: "); VM.sysWrite(lockWord);
        VM.sysWrite(" index: "); VM.sysWrite(index);
        VM.sysWrite(" locks: "); VM.sysWrite(Lock.numLocks());
        VM.sysWriteln();
      }
      VM._assert(index > 0 && index < Lock.numLocks());  // index is in range
      VM._assert(lockWord.and(TL_STAT_MASK).EQ(TL_STAT_FAT));        // fat lock bit is set
    }
    return index;
  }

  @Inline
  @Uninterruptible
  public static int getLockOwner(Word lockWord) {
    if (VM.VerifyAssertions) VM._assert(!isFat(lockWord));
    if (lockWord.and(TL_STAT_MASK).EQ(TL_STAT_BIASABLE)) {
      if (lockWord.and(TL_LOCK_COUNT_MASK).isZero()) {
        return 0;
      } else {
        return lockWord.and(TL_THREAD_ID_MASK).toInt();
      }
    } else {
      return lockWord.and(TL_THREAD_ID_MASK).toInt();
    }
  }

  @Inline
  @Uninterruptible
  public static int getRecCount(Word lockWord) {
    if (VM.VerifyAssertions) VM._assert(getLockOwner(lockWord)!=0);
    if (lockWord.and(TL_STAT_MASK).EQ(TL_STAT_BIASABLE)) {
      return lockWord.and(TL_LOCK_COUNT_MASK).rshl(TL_LOCK_COUNT_SHIFT).toInt();
    } else {
      return lockWord.and(TL_LOCK_COUNT_MASK).rshl(TL_LOCK_COUNT_SHIFT).toInt()+1;
    }
  }

  /**
   * Set only the dedicated locking 16-bit part of the given value. This is the only part
   * that is allowed to be written without a CAS. This takes care of the shifting and
   * storing of the value.
   *
   * @param o The object whose header is to be changed
   * @param lockOffset The lock offset
   * @param value The value which contains the 16-bit portion to be written.
   */
  @Inline
  @Unpreemptible
  private static void setDedicatedU16(Object o, Offset lockOffset, Word value) {
    Magic.setCharAtOffset(o, lockOffset.plus(TL_DEDICATED_U16_OFFSET), (char)(value.toInt() >>> TL_DEDICATED_U16_SHIFT));
  }

  @NoInline
  @Unpreemptible
  public static boolean casFromBiased(Object o, Offset lockOffset,
                                      Word oldLockWord, Word changed,
                                      int cnt) {
    RVMThread me=RVMThread.getCurrentThread();
    Word id=oldLockWord.and(TL_THREAD_ID_MASK);
    if (id.isZero()) {
      if (false) VM.sysWriteln("id is zero - easy case.");
      return Synchronization.tryCompareAndSwap(o, lockOffset, oldLockWord, changed);
    } else {
      if (false) VM.sysWriteln("id = ",id);
      int slot=id.toInt()>>TL_THREAD_ID_SHIFT;
      if (false) VM.sysWriteln("slot = ",slot);
      RVMThread owner=RVMThread.threadBySlot[slot];
      if (owner==me /* I own it, so I can unbias it trivially.  This occurs
                       when we are inflating due to, for example, wait() */ ||
          owner==null /* the thread that owned it is dead, so it's safe to
                         unbias. */) {
        // note that we use a CAS here, but it's only needed in the case
        // that owner==null, since in that case some other thread may also
        // be unbiasing.
        return Synchronization.tryCompareAndSwap(
          o, lockOffset, oldLockWord, changed);
      } else {
        boolean result=false;

        // NB. this may stop a thread other than the one that had the bias,
        // if that thread died and some other thread took its slot.  that's
        // why we do a CAS below.  it's only needed if some other thread
        // had seen the owner be null (which may happen if we came here after
        // a new thread took the slot while someone else came here when the
        // slot was still null).  if it was the case that everyone else had
        // seen a non-null owner, then the pair handshake would serve as
        // sufficient synchronization (the id would identify the set of threads
        // that shared that id's communicationLock).  oddly, that means that
        // this whole thing could be "simplified" to acquire the
        // communicationLock even if the owner was null.  but that would be
        // goofy.
        if (false) VM.sysWriteln("entering pair handshake");
        owner.beginPairHandshake();
        if (false) VM.sysWriteln("done with that");

        Word newLockWord=Magic.getWordAtOffset(o, lockOffset);
        result=Synchronization.tryCompareAndSwap(
          o, lockOffset, oldLockWord, changed);
        owner.endPairHandshake();
        if (false) VM.sysWriteln("that worked.");

        return result;
      }
    }
  }

  @Inline
  @Unpreemptible
  public static boolean attemptToMarkInflated(Object o, Offset lockOffset,
                                              Word oldLockWord,
                                              int lockId,
                                              int cnt) {
    if (VM.VerifyAssertions) VM._assert(oldLockWord.and(TL_STAT_MASK).NE(TL_STAT_FAT));
    if (false) VM.sysWriteln("attemptToMarkInflated with oldLockWord = ",oldLockWord);
    // what this needs to do:
    // 1) if the lock is thin, it's just a CAS
    // 2) if the lock is unbiased, CAS in the inflation
    // 3) if the lock is biased in our favor, store the lock without CAS
    // 4) if the lock is biased but to someone else, enter the pair handshake
    //    to unbias it and install the inflated lock
    Word changed=
      TL_STAT_FAT.or(Word.fromIntZeroExtend(lockId).lsh(TL_LOCK_ID_SHIFT))
      .or(oldLockWord.and(TL_UNLOCK_MASK));
    if (false && oldLockWord.and(TL_STAT_MASK).EQ(TL_STAT_THIN))
      VM.sysWriteln("obj = ",Magic.objectAsAddress(o),
                    ", old = ",oldLockWord,
                    ", owner = ",getLockOwner(oldLockWord),
                    ", rec = ",getLockOwner(oldLockWord)==0?0:getRecCount(oldLockWord),
                    ", changed = ",changed,
                    ", lockId = ",lockId);
    if (false) VM.sysWriteln("changed = ",changed);
    if (oldLockWord.and(TL_STAT_MASK).EQ(TL_STAT_THIN)) {
      if (false) VM.sysWriteln("it's thin, inflating the easy way.");
      return Synchronization.tryCompareAndSwap(
        o, lockOffset, oldLockWord, changed);
    } else {
      return casFromBiased(o, lockOffset, oldLockWord, changed, cnt);
    }
  }

  /**
   * Promotes a light-weight lock to a heavy-weight lock.  If this returns the lock
   * that you gave it, its mutex will be locked; otherwise, its mutex will be unlocked.
   * Hence, calls to this method should always be followed by a condition lock() or
   * unlock() call.
   *
   * @param o the object to get a heavy-weight lock
   * @param lockOffset the offset of the thin lock word in the object.
   * @return the inflated lock; either the one you gave, or another one, if the lock
   *         was inflated by some other thread.
   */
  @NoNullCheck
  @Unpreemptible
  protected static Lock attemptToInflate(Object o,
                                         Offset lockOffset,
                                         Lock l) {
    if (false) VM.sysWriteln("l = ",Magic.objectAsAddress(l));
    l.mutex.lock();
    for (int cnt=0;;++cnt) {
      Word bits = Magic.getWordAtOffset(o, lockOffset);
      // check to see if another thread has already created a fat lock
      if (isFat(bits)) {
        if (trace) {
          VM.sysWriteln("Thread #",RVMThread.getCurrentThreadSlot(),
                        ": freeing lock ",Magic.objectAsAddress(l),
                        " because we had a double-inflate");
        }
        Lock result = Lock.getLock(getLockIndex(bits));
        if (result==null ||
            result.lockedObject!=o) {
          continue; /* this is nasty.  this will happen when a lock
                       is deflated. */
        }
        Lock.free(l);
        l.mutex.unlock();
        return result;
      }
      if (VM.VerifyAssertions) VM._assert(l!=null);
      if (attemptToMarkInflated(
            o, lockOffset, bits, l.index, cnt)) {
        l.setLockedObject(o);
        l.setOwnerId(getLockOwner(bits));
        if (l.getOwnerId() != 0) {
          l.setRecursionCount(getRecCount(bits));
        } else {
          if (VM.VerifyAssertions) VM._assert(l.getRecursionCount()==0);
        }
        return l;
      }
      // contention detected, try again
    }
  }

  @Inline
  @Uninterruptible
  private static Word biasBitsToThinBits(Word bits) {
    int lockOwner=getLockOwner(bits);

    Word changed=bits.and(TL_UNLOCK_MASK).or(TL_STAT_THIN);

    if (lockOwner!=0) {
      int recCount=getRecCount(bits);
      changed=changed
        .or(Word.fromIntZeroExtend(lockOwner))
        .or(Word.fromIntZeroExtend(recCount-1).lsh(TL_LOCK_COUNT_SHIFT));
    }

    return changed;
  }

  @Inline
  @Uninterruptible
  public static boolean attemptToMarkDeflated(Object o, Offset lockOffset,
                                              Word oldLockWord) {
    // we allow concurrent modification of the lock word when it's thin or fat.
    Word changed=oldLockWord.and(TL_UNLOCK_MASK).or(TL_STAT_THIN);
    if (VM.VerifyAssertions) VM._assert(getLockOwner(changed)==0);
    return Synchronization.tryCompareAndSwap(
      o, lockOffset, oldLockWord, changed);
  }

  @Uninterruptible
  public static void markDeflated(Object o, Offset lockOffset, int id) {
    for (;;) {
      Word bits=Magic.getWordAtOffset(o, lockOffset);
      if (VM.VerifyAssertions) VM._assert(isFat(bits));
      if (VM.VerifyAssertions) VM._assert(getLockIndex(bits)==id);
      if (attemptToMarkDeflated(o, lockOffset, bits)) {
        return;
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
    if (isFat(old)) { // already a fat lock in place
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
   * portBOB benchmark on a 12-way SMP (AIX) in the Fall of '99.  FP
   * confirmed that it's still optimal for JBB and DaCapo on 4-, 8-,
   * and 16-way SMPs (Linux/ia32) in Spring '09.
   */
  private static final int retryLimit = 40;

  static final boolean STATS = Lock.STATS;

  static final boolean trace = false;

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

