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
package org.mmtk.harness.scheduler.rawthreads;

import java.util.Collection;
import java.util.Iterator;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * A wrapper round a list of threads, that gives the queue a name,
 * and counts the population across potentially many queues.
 *
 * This allows the scheduler to count the number of blocked threads when threads may
 * be blocked on any number of different queues.
 */
class ThreadQueue implements Collection<RawThread> {

  private final String name;

  private final AtomicInteger counter;

  private final Queue<RawThread> cqueue = new ConcurrentLinkedQueue<RawThread>();

  /**
   * Create a ThreadQueue with an explicit counter, potentially shared with
   * other ThreadQueues.
   * @param name
   * @param counter
   */
  ThreadQueue(String name, AtomicInteger counter) {
    this.name = name;
    this.counter = counter;
  }

  ThreadQueue(String name) {
    this(name,new AtomicInteger());
  }

  public String getName() {
    return name;
  }

  public synchronized RawThread remove() {
    RawThread thread = cqueue.remove();
    if (thread != null) counter.getAndDecrement();
    return thread;
  }

  @Override
  public synchronized boolean remove(Object o) {
    if (cqueue.remove(o)) {
      counter.getAndDecrement();
      return true;
    }
    return false;
  }

  @Override
  public synchronized boolean add(RawThread thread) {
    counter.incrementAndGet();
    return cqueue.add(thread);
  }

  @Override
  public synchronized int size() {
    assert cqueue.size() <= counter.get() : "queue size = "+cqueue.size()+", counter="+counter.get();
    return cqueue.size();
  }

  @Override
  public synchronized boolean addAll(Collection<? extends RawThread> threads) {
    counter.addAndGet(threads.size());
    return cqueue.addAll(threads);
  }

  @Override
  public synchronized void clear() {
    counter.addAndGet(-cqueue.size());
    cqueue.clear();
  }

  // CHECKSTYLE:OFF
  /*
   * Methods from Collection, delegated directly to the underlying list
   */
  @Override public boolean isEmpty()                    { return cqueue.isEmpty(); }
  @Override public Iterator<RawThread> iterator()       { return cqueue.iterator(); }
  @Override public Object[] toArray()                   { return cqueue.toArray(); }
  @Override public <T> T[] toArray(T[] a)               { return cqueue.toArray(a); }
  @Override public boolean contains(Object o)           { return cqueue.contains(o);  }
  @Override public boolean containsAll(Collection<?> c) { return cqueue.containsAll(c); }

  /*
   * Methods we don't implement
   */
  @Override public boolean retainAll(Collection<?> c)   { throw new UnsupportedOperationException(); }
  @Override public boolean removeAll(Collection<?> c)   { throw new UnsupportedOperationException(); }
  // CHECKSTYLE:ON
}
