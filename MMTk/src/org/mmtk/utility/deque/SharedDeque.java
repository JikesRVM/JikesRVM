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
package org.mmtk.utility.deque;

import org.mmtk.policy.RawPageSpace;
import org.mmtk.policy.Space;
import org.mmtk.utility.Constants;
import org.mmtk.utility.Log;
import org.mmtk.vm.Lock;
import org.mmtk.vm.VM;
import org.vmmagic.pragma.Entrypoint;
import org.vmmagic.pragma.Inline;
import org.vmmagic.pragma.Uninterruptible;
import org.vmmagic.unboxed.Address;
import org.vmmagic.unboxed.Offset;

/**
 * This supports <i>unsynchronized</i> enqueuing and dequeuing of buffers
 * for shared use.  The data can be added to and removed from either end
 * of the deque.
 */
@Uninterruptible
public class SharedDeque extends Deque implements Constants {
  private static final boolean DISABLE_WAITING = true;
  private static final Offset NEXT_OFFSET = Offset.zero();
  private static final Offset PREV_OFFSET = Offset.fromIntSignExtend(BYTES_IN_ADDRESS);

  private static final boolean TRACE = false;
  private static final boolean TRACE_DETAIL = false;
  private static final boolean TRACE_BLOCKERS = false;

  /****************************************************************************
   *
   * Public instance methods
   */

  /**
   * Constructor
   */
  public SharedDeque(String name, RawPageSpace rps, int arity) {
    this.rps = rps;
    this.arity = arity;
    this.name = name;
    lock = VM.newLock("SharedDeque");
    clearCompletionFlag();
    head = HEAD_INITIAL_VALUE;
    tail = TAIL_INITIAL_VALUE;
  }

  /** Get the arity (words per entry) of this queue */
  @Inline
  final int getArity() { return arity; }

  /**
   * Enqueue a block on the head or tail of the shared queue
   *
   * @param buf
   * @param arity
   * @param toTail
   */
  final void enqueue(Address buf, int arity, boolean toTail) {
    if (VM.VERIFY_ASSERTIONS) VM.assertions._assert(arity == this.arity);
    lock();
    if (toTail) {
      // Add to the tail of the queue
      setNext(buf, Address.zero());
      if (tail.EQ(TAIL_INITIAL_VALUE))
        head = buf;
      else
        setNext(tail, buf);
      setPrev(buf, tail);
      tail = buf;
    } else {
      // Add to the head of the queue
      setPrev(buf, Address.zero());
      if (head.EQ(HEAD_INITIAL_VALUE))
        tail = buf;
      else
        setPrev(head, buf);
      setNext(buf, head);
      head = buf;
    }
    bufsenqueued++;
    if (VM.VERIFY_ASSERTIONS) VM.assertions._assert(checkDequeLength(bufsenqueued));
    unlock();
  }

  public final void clearDeque(int arity) {
    Address buf = dequeue(arity);
    while (!buf.isZero()) {
      free(bufferStart(buf));
      buf = dequeue(arity);
    }
    setCompletionFlag();
  }

  @Inline
  final Address dequeue(int arity) {
    return dequeue(arity, false);
  }

  final Address dequeue(int arity, boolean fromTail) {
    if (VM.VERIFY_ASSERTIONS) VM.assertions._assert(arity == this.arity);
    return dequeue(false, fromTail);
  }

  @Inline
  final Address dequeueAndWait(int arity) {
    return dequeueAndWait(arity, false);
  }

  final Address dequeueAndWait(int arity, boolean fromTail) {
    if (VM.VERIFY_ASSERTIONS) VM.assertions._assert(arity == this.arity);
    Address buf = dequeue(false, fromTail);
    if (buf.isZero() && (!complete())) {
      buf = dequeue(true, fromTail);  // Wait inside dequeue
    }
    return buf;
  }

  /**
   * Prepare for parallel processing. All active GC threads will
   * participate, and pop operations will block until all work
   * is complete.
   */
  public final void prepare() {
    if (DISABLE_WAITING) {
      prepareNonBlocking();
    } else {
      /* This should be the normal mode of operation once performance is fixed */
      prepare(VM.collection.activeGCThreads());
    }
  }

  /**
   * Prepare for processing where pop operations on the deques
   * will never block.
   */
  public final void prepareNonBlocking() {
    prepare(1);
  }

  /**
   * Prepare for parallel processing where a specific number
   * of threads take part.
   *
   * @param consumers # threads taking part.
   */
  private void prepare(int consumers) {
    if (VM.VERIFY_ASSERTIONS) VM.assertions._assert(numConsumersWaiting == 0);
    setNumConsumers(consumers);
    clearCompletionFlag();
  }

  public final void reset() {
    if (VM.VERIFY_ASSERTIONS) VM.assertions._assert(numConsumersWaiting == 0);
    clearCompletionFlag();
    setNumConsumersWaiting(0);
    assertExhausted();
  }

  public final void assertExhausted() {
    if (VM.VERIFY_ASSERTIONS) VM.assertions._assert(head.isZero() && tail.isZero());
  }

  @Inline
  final Address alloc() {
    Address rtn = rps.acquire(PAGES_PER_BUFFER);
    if (rtn.isZero()) {
      Space.printUsageMB();
      VM.assertions.fail("Failed to allocate space for queue.  Is metadata virtual memory exhausted?");
    }
    if (VM.VERIFY_ASSERTIONS) VM.assertions._assert(rtn.EQ(bufferStart(rtn)));
    return rtn;
  }

  @Inline
  final void free(Address buf) {
    if (VM.VERIFY_ASSERTIONS) VM.assertions._assert(buf.EQ(bufferStart(buf)) && !buf.isZero());
    rps.release(buf);
  }

  @Inline
  public final int enqueuedPages() {
    return (int) (bufsenqueued * PAGES_PER_BUFFER);
  }

  /****************************************************************************
   *
   * Private instance methods and fields
   */

  /** The name of this shared deque - for diagnostics */
  private final String name;

  /** Raw page space from which to allocate */
  private RawPageSpace rps;

  /** Number of words per entry */
  private final int arity;

  /** Completion flag - set when all consumers have arrived at the barrier */
  @Entrypoint
  private volatile int completionFlag;

  /** # active threads - processing is complete when # waiting == this */
  @Entrypoint
  private volatile int numConsumers;

  /** # threads waiting */
  @Entrypoint
  private volatile int numConsumersWaiting;

  /** Head of the shared deque */
  @Entrypoint
  protected volatile Address head;

  /** Tail of the shared deque */
  @Entrypoint
  protected volatile Address tail;
  @Entrypoint
  private volatile int bufsenqueued;
  private Lock lock;

  private static final long WARN_PERIOD = (long)(2*1E9);
  private static final long TIMEOUT_PERIOD = 10 * WARN_PERIOD;

  /**
   * Dequeue a block from the shared pool.  If 'waiting' is true, and the
   * queue is empty, wait for either a new block to show up or all the
   * other consumers to join us.
   *
   * @param waiting
   * @param fromTail
   * @return the Address of the block
   */
  private Address dequeue(boolean waiting, boolean fromTail) {
    lock();
    Address rtn = ((fromTail) ? tail : head);
    if (rtn.isZero()) {
      if (VM.VERIFY_ASSERTIONS) VM.assertions._assert(tail.isZero() && head.isZero());
      // no buffers available
      if (waiting) {
        int ordinal = TRACE ? 0 : VM.activePlan.collector().getId();
        setNumConsumersWaiting(numConsumersWaiting + 1);
        while (rtn.isZero()) {
          if (numConsumersWaiting == numConsumers)
            setCompletionFlag();
          if (TRACE) {
            Log.write("-- ("); Log.write(ordinal);
            Log.write(") joining wait queue of SharedDeque(");
            Log.write(name); Log.write(") ");
            Log.write(numConsumersWaiting); Log.write("/");
            Log.write(numConsumers);
            Log.write(" consumers waiting");
            if (complete()) Log.write(" WAIT COMPLETE");
            Log.writeln();
            if (TRACE_BLOCKERS)
              VM.assertions.dumpStack();
          }
          unlock();
          // Spin and wait
          spinWait(fromTail);

          if (complete()) {
            if (TRACE) {
              Log.write("-- ("); Log.write(ordinal); Log.writeln(") EXITING");
            }
            lock();
            setNumConsumersWaiting(numConsumersWaiting - 1);
            unlock();
            return Address.zero();
          }
          lock();
          // Re-get the list head/tail while holding the lock
          rtn = ((fromTail) ? tail : head);
        }
        setNumConsumersWaiting(numConsumersWaiting - 1);
        if (TRACE) {
          Log.write("-- ("); Log.write(ordinal); Log.write(") resuming work ");
          Log.write(" n="); Log.writeln(numConsumersWaiting);
        }
      } else {
        unlock();
        return Address.zero();
      }
    }
    if (fromTail) {
      // dequeue the tail buffer
      setTail(getPrev(tail));
      if (head.EQ(rtn)) {
        setHead(Address.zero());
        if (VM.VERIFY_ASSERTIONS) VM.assertions._assert(tail.isZero());
      } else {
        setNext(tail, Address.zero());
      }
    } else {
      // dequeue the head buffer
      setHead(getNext(head));
      if (tail.EQ(rtn)) {
        setTail(Address.zero());
        if (VM.VERIFY_ASSERTIONS) VM.assertions._assert(head.isZero());
      } else {
        setPrev(head, Address.zero());
      }
    }
    bufsenqueued--;
    unlock();
    return rtn;
  }

  /**
   * Spinwait for GC work to arrive
   *
   * @param fromTail Check the head or the tail ?
   */
  private void spinWait(boolean fromTail) {
    long startNano = 0;
    long lastElapsedNano = 0;
    while (true) {
      long startCycles = VM.statistics.cycles();
      long endCycles = startCycles + ((long) 1e9); // a few hundred milliseconds more or less.
      long nowCycles;
      do {
        VM.memory.isync();
        Address rtn = ((fromTail) ? tail : head);
        if (!rtn.isZero() || complete()) return;
        nowCycles = VM.statistics.cycles();
      } while (startCycles < nowCycles && nowCycles < endCycles); /* check against both ends to guard against CPU migration */

      /*
       * According to the cycle counter, we've been spinning for a while.
       * Time to check nanoTime and see if we should print a warning and/or fail.
       * We lock the deque while doing this to avoid interleaved messages from multiple threads.
       */
      lock();
      if (startNano == 0) {
        startNano = VM.statistics.nanoTime();
      } else {
        long nowNano = VM.statistics.nanoTime();
        long elapsedNano = nowNano - startNano;
        if (elapsedNano - lastElapsedNano > WARN_PERIOD) {
          Log.write("GC Warning: SharedDeque("); Log.write(name);
          Log.write(") wait has reached "); Log.write(VM.statistics.nanosToSecs(elapsedNano));
          Log.write(", "); Log.write(numConsumersWaiting); Log.write("/");
          Log.write(numConsumers); Log.writeln(" threads waiting");
          lastElapsedNano = elapsedNano;
        }
        if (elapsedNano > TIMEOUT_PERIOD) {
          unlock();   // To allow other GC threads to die in turn
          VM.assertions.fail("GC Error: SharedDeque Timeout");
        }
      }
      unlock();
    }
  }

  /**
   * Set the "next" pointer in a buffer forming the linked buffer chain.
   *
   * @param buf The buffer whose next field is to be set.
   * @param next The reference to which next should point.
   */
  private static void setNext(Address buf, Address next) {
    buf.store(next, NEXT_OFFSET);
  }

  /**
   * Get the "next" pointer in a buffer forming the linked buffer chain.
   *
   * @param buf The buffer whose next field is to be returned.
   * @return The next field for this buffer.
   */
  protected final Address getNext(Address buf) {
    return buf.loadAddress(NEXT_OFFSET);
  }

  /**
   * Set the "prev" pointer in a buffer forming the linked buffer chain.
   *
   * @param buf The buffer whose next field is to be set.
   * @param prev The reference to which prev should point.
   */
  private void setPrev(Address buf, Address prev) {
    buf.store(prev, PREV_OFFSET);
  }

  /**
   * Get the "next" pointer in a buffer forming the linked buffer chain.
   *
   * @param buf The buffer whose next field is to be returned.
   * @return The next field for this buffer.
   */
  protected final Address getPrev(Address buf) {
    return buf.loadAddress(PREV_OFFSET);
  }

  /**
   * Check the number of buffers in the work queue (for debugging
   * purposes).
   *
   * @param length The number of buffers believed to be in the queue.
   * @return True if the length of the queue matches length.
   */
  private boolean checkDequeLength(int length) {
    Address top = head;
    int l = 0;
    while (!top.isZero() && l <= length) {
      top = getNext(top);
      l++;
    }
    return l == length;
  }

  /**
   * Lock this shared queue.  We use one simple low-level lock to
   * synchronize access to the shared queue of buffers.
   */
  private void lock() {
    lock.acquire();
  }

  /**
   * Release the lock.  We use one simple low-level lock to synchronize
   * access to the shared queue of buffers.
   */
  private void unlock() {
    lock.release();
  }

  /**
   * Is the current round of processing complete ?
   */
  private boolean complete() {
    return completionFlag == 1;
  }

  /**
   * Set the completion flag.
   */
  @Inline
  private void setCompletionFlag() {
    if (TRACE_DETAIL) {
      Log.writeln("# setCompletionFlag: ");
    }
    completionFlag = 1;
  }

  /**
   * Clear the completion flag.
   */
  @Inline
  private void clearCompletionFlag() {
    if (TRACE_DETAIL) {
      Log.writeln("# clearCompletionFlag: ");
    }
    completionFlag = 0;
  }

  @Inline
  private void setNumConsumers(int newNumConsumers) {
    if (TRACE_DETAIL) {
      Log.write("# Num consumers "); Log.writeln(newNumConsumers);
    }
    numConsumers = newNumConsumers;
  }

  @Inline
  private void setNumConsumersWaiting(int newNCW) {
    if (TRACE_DETAIL) {
      Log.write("# Num consumers waiting "); Log.writeln(newNCW);
    }
    numConsumersWaiting = newNCW;
  }

  @Inline
  private void setHead(Address newHead) {
    head = newHead;
    VM.memory.sync();
  }

  @Inline
  private void setTail(Address newTail) {
    tail = newTail;
    VM.memory.sync();
  }
}
