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
import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * A wrapper round a list of threads, that gives the queue a name,
 * and counts the population across potentially many threads.
 */
class ThreadQueue implements Collection<RawThread> {

  private final String name;

  private final AtomicInteger counter;

  private final List<RawThread> queue = new LinkedList<RawThread>();

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

  public RawThread remove() {
    counter.getAndDecrement();
    return queue.remove(0);
  }

  @Override
  public boolean remove(Object o) {
    if (queue.remove(o)) {
      counter.getAndDecrement();
      return true;
    }
    return false;
  }

  @Override
  public boolean add(RawThread thread) {
    counter.incrementAndGet();
    return queue.add(thread);
  }

  @Override public int size() {
    assert queue.size() <= counter.get();
    return queue.size();
  }

  @Override
  public boolean addAll(Collection<? extends RawThread> threads) {
    counter.addAndGet(threads.size());
    return queue.addAll(threads);
  }

  @Override
  public void clear() {
    counter.addAndGet(-queue.size());
    queue.clear();
  }

  // CHECKSTYLE:OFF
  /*
   * Methods from Collection, delegated directly to the underlying list
   */
  @Override public boolean isEmpty()                    { return queue.isEmpty(); }
  @Override public Iterator<RawThread> iterator()       { return queue.iterator(); }
  @Override public Object[] toArray()                   { return queue.toArray(); }
  @Override public <T> T[] toArray(T[] a)               { return queue.toArray(a); }
  @Override public boolean contains(Object o)           { return queue.contains(o);  }
  @Override public boolean containsAll(Collection<?> c) { return queue.containsAll(c); }

  /*
   * Methods we don't implement
   */
  @Override public boolean retainAll(Collection<?> c)   { throw new UnsupportedOperationException(); }
  @Override public boolean removeAll(Collection<?> c)   { throw new UnsupportedOperationException(); }
  // CHECKSTYLE:ON
}
