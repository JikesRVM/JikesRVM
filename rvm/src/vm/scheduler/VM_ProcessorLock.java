/*
 * (C) Copyright IBM Corp. 2001
 */
//$Id$
package com.ibm.JikesRVM;

import org.vmmagic.pragma.*;
import org.vmmagic.unboxed.*;

/**
 *
 * <p> Alternative (to Java monitors) light-weight synchronization
 * mechanism to implement thread scheduling {@link VM_Processor} and
 * Java monitors {@link VM_Lock}.  These locks should not be used
 * where Java monitors would suffice.  They are intended to be held
 * only briefly!
 *
 * <p> Normally, contending <code>VM_Processor</code>s will spin on
 * this processor lock's <code>latestContender</code> field.  If
 * <code>MCS_Locking</code> is set, the processors spin on processor
 * local data.  This is loosely based on an idea in Mellor-Crummey and
 * Scott's paper in ASPLOS-IV (1991).  MCS locking is indicated by
 * setting the preprocessor directive RVM_FOR_MCS_PROCESSOR_LOCKS to
 * 1.  Possible project: determine those conditions under which MCS
 * locking performs better than spinning on a global address.
 *
 * <p> Acquiring or releasing a lock involves atomically reading and
 * setting the lock's <code>latestContender</code> field.  If this
 * field is null, the lock is unowned.  Otherwise, the field points to
 * the virtual processor that owns the lock, or, if MCS locking is
 * being used, to the last vp on a circular queue of virtual
 * processors spinning until they get the lock, or, if MCS locking is
 * being used and the circular spin queue is being updated, to
 * <code>IN_FLUX</code>.
 *
 * <p> Contention is best handled by doing something else.  To support
 * this, <code>tryLock</code> obtains the lock (and returns true) if
 * the lock is unowned (and there is no spurious microcontention).
 * Otherwise, it returns false.
 *
 * <p> Only when "doing something else" is not an attractive option
 * (locking global thread queues, unlocking a thick lock with threads
 * waiting, avoiding starvation on a thread that has issued several
 * tryLocks, etc.) should lock() be called.  Here, any remaining
 * contention is handled by spinning on a local flag.
 *
 * <p> To add itself to the circular waiting queue, a processor must
 * succeed in setting the latestContender field to IN_FLUX.  A backoff
 * strategy is used to reduce contention for this field.  This
 * strategy has both a pseudo-random (to prevent two or more virtual
 * processors from backing off in lock step) and an exponential
 * component (to deal with really high contention).
 *
 * <p> Releasing a lock entails either atomically setting the
 * latestContender field to null (if this processor is the
 * latestContender), or releasing the first virtual processor on the
 * circular spin queue.  In the latter case, the latestContender field
 * must be set to IN_FLUX.  To give unlock() priority over lock(), the
 * backoff strategy is not used for unlocking: if a vp fails to set
 * set the field to IN_FLUX, it tries again immediately.
 *
 * <p> Usage: system locks should only be used when synchronized
 * methods cannot.  Do not do anything, (such as trigger a type cast,
 * allocate an object, or call any method of a class that does not
 * implement Uninterruptible) that might allow a thread switch or
 * trigger a garbage collection between lock and unlock.
 *
 * @author Bowen Alpern
 * @see VM_Processor
 * @see VM_Lock */
public final class VM_ProcessorLock implements VM_Constants, Uninterruptible {

//-#if RVM_FOR_MCS_PROCESSOR_LOCKS
  /**
   * Contending <code>VM_Processor</code>s spin on processor local addresses.
   */
  private static final boolean MCS_Locking = true;
//-#else
  /**
   * Contending <code>VM_Processor</code>s spin on a globally shared address.
   */
  private static final boolean MCS_Locking = false;
//-#endif
  
  /**
   * The state of the processor lock.
   * <ul>
   * <li> <code>null</code>, if the lock is not owned;
   * <li> the processor that owns the lock, if no processors are waithing;
   * <li> the last in a circular chain of processors waiting to own the lock; or
   * <li> <code>IN_FLUX</code>, if the circular chain is being edited.
   * </ul>
   * Only the first two states are possible unless MCS locking is implemented.
   */
  VM_Processor latestContender;

  /**
   * Acquire a processor lock.
   */
  public void lock () {
    if (VM.BuildForSingleVirtualProcessor) return;
    VM_Processor i = VM_Processor.getCurrentProcessor();
    if (VM.VerifyAssertions) i.lockCount += 1;
    VM_Processor p;
    int attempts = 0;
    int retries = 0;
    int latestContenderOffset = VM_Entrypoints.latestContenderField.getOffset();
    do {
      p = VM_Magic.objectAsProcessor(VM_Magic.addressAsObject(VM_Magic.prepareAddress(this, latestContenderOffset)));
      if (p == null) { // nobody owns the lock
        if (VM_Magic.attemptAddress(this, latestContenderOffset, Address.zero(), VM_Magic.objectAsAddress(i))) {
          VM_Magic.isync(); // so subsequent instructions wont see stale values
          return; 
        } else {
          continue; // don't handle contention
        }
      } else if (MCS_Locking && VM_Magic.objectAsAddress(p).NE(IN_FLUX)) { // lock is owned, but not being changed
        if (VM_Magic.attemptAddress(this, latestContenderOffset, VM_Magic.objectAsAddress(p), IN_FLUX)) {
          VM_Magic.isync(); // so subsequent instructions wont see stale values
          break; 
        }
      }
      handleMicrocontention(attempts++);
    } while (true);
    // i owns the lock
    if (VM.VerifyAssertions && !MCS_Locking) VM._assert(VM.NOT_REACHED);
    i.awaitingProcessorLock = this;
    if (p.awaitingProcessorLock != this) { // make i first (and only) waiter on the contender chain
      i.contenderLink = i;
    } else {                               // make i last waiter on the contender chain
      i.contenderLink = p.contenderLink;
      p.contenderLink = i;
    }
    VM_Magic.sync(); // so other contender will see updated contender chain
    VM_Magic.setObjectAtOffset(this, latestContenderOffset, i);  // other processors can get at the lock
    do { // spin, waiting for the lock
      VM_Magic.isync(); // to make new value visible as soon as possible
    } while (i.awaitingProcessorLock == this);
  }

  /**
   * Conditionally acquire a processor lock.
   * @return whether acquisition succeeded
   */
  boolean tryLock () {
    if (VM.BuildForSingleVirtualProcessor) return true;
    int latestContenderOffset = VM_Entrypoints.latestContenderField.getOffset();
    if (VM_Magic.prepareAddress(this, latestContenderOffset).isZero()) {
      Address cp = VM_Magic.objectAsAddress(VM_Processor.getCurrentProcessor());
      if (VM_Magic.attemptAddress(this, latestContenderOffset, Address.zero(), cp)) {
        VM_Magic.isync(); // so subsequent instructions wont see stale values
        return true; 
      }
    }
    return false;
  }

  /**
   * Release a processor lock.
   */
  public void unlock () {
    if (VM.BuildForSingleVirtualProcessor) return;
    VM_Magic.sync(); // commit changes while lock was held so they are visiable to the next processor that acquires the lock
    int latestContenderOffset = VM_Entrypoints.latestContenderField.getOffset();
    VM_Processor i = VM_Processor.getCurrentProcessor();
    if (!MCS_Locking) {
      if (VM.VerifyAssertions) i.lockCount -= 1;
      VM_Magic.setObjectAtOffset(this, latestContenderOffset, null);  // latestContender = null;
      return;
    }
    VM_Processor p;
    int retries = 0;
    do {
      p = VM_Magic.objectAsProcessor(VM_Magic.addressAsObject(VM_Magic.prepareAddress(this, latestContenderOffset)));
      if (p == i) { // nobody is waiting for the lock
        if (VM_Magic.attemptAddress(this, latestContenderOffset, VM_Magic.objectAsAddress(p), Address.zero())) {
          break;
        }
      } else if (VM_Magic.objectAsAddress(p).NE(IN_FLUX)) { // there are waiters, but the contention chain is not being chainged
        if (VM_Magic.attemptAddress(this, latestContenderOffset, VM_Magic.objectAsAddress(p), IN_FLUX)) {
          break; 
        }
      } else { // in flux
        handleMicrocontention(-1); // wait a little before trying again
      }
    } while (true);
    if (p != i) { // p is the last processor on the chain of processors contending for the lock
      VM_Processor q = p.contenderLink; // q is first processor on the chain
      if (p == q) { // only one processor waiting for the lock
        q.awaitingProcessorLock = null; // q now owns the lock
        VM_Magic.sync(); // make sure the chain of waiting processors gets updated before another processor accesses the chain
        // other contenders can get at the lock:
        VM_Magic.setObjectAtOffset(this, latestContenderOffset, q); // latestContender = q; 
      } else { // more than one processor waiting for the lock
        p.contenderLink = q.contenderLink; // remove q from the chain
        q.awaitingProcessorLock = null; // q now owns the lock
        VM_Magic.sync(); // make sure the chain of waiting processors gets updated before another processor accesses the chain
        VM_Magic.setObjectAtOffset(this, latestContenderOffset, p); // other contenders can get at the lock
      }
    }
    if (VM.VerifyAssertions) i.lockCount -= 1;
  }

  /**
   * A synonym for unlock.
   * @deprecated (was to avoid the "sync" of unlock)
   */
  public void release () { unlock(); }

  /**
   * An attempt to lock or unlock a processor lock has failed,
   * presumably due to contention with another processor.  Backoff a
   * little to increase the likelihood that a subsequent retry will
   * succeed.
   */
  private static void handleMicrocontention(int n) throws NoInlinePragma {
    if (n <= 0) return;                                  // method call overhead is delay enough
    int pid    =  VM_Processor.getCurrentProcessorId();
    if (pid < 0) pid = - pid;                            // native processors have negative ids
    delayIndex = (delayIndex + pid) % delayCount.length;
    int delay  = delayCount[delayIndex]*delayMultiplier; // pseudorandom backoff component
    delay     += delayBase << (n-1);                     // exponential backoff component
    for (int i=delay; i>0; i--) ;                        // delay a different amount of time on each processor
  }

  private static final int   delayMultiplier = 10;
  private static final int   delayBase       = 64;
  private static       int   delayIndex;
  private static       int[] delayCount = { 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13 };

  /**
   * For MCS locking, indicates that another processor is changing the
   * state of the circular waiting queue.
   */
  private static final Address  IN_FLUX = Address.max();

}
