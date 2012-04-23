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
package org.jikesrvm.util;

import java.util.Iterator;
import java.util.NoSuchElementException;

import org.jikesrvm.VM;
import org.jikesrvm.mm.mminterface.MemoryManager;

/**
 * Common super class for all VM hash sets
 */
abstract class AbstractHashSetRVM<T>  implements Iterable<T> {

  protected static final int DEFAULT_SIZE = 7;
  private static final float LOAD = 3;
  protected AbstractBucket<T>[] buckets;
  protected int numElems = 0;

  abstract static class AbstractBucket<T> {
    /**
     * Change the next bucket after this bucket, possibly constructing a new
     * abstract bucket
     */
    abstract AbstractBucket<T> setNext(AbstractBucket<T> next);

    abstract AbstractBucket<T> getNext();

    abstract T getKey();
  }

  abstract AbstractBucket<T> createNewBucket(T key, AbstractBucket<T> next);

  @SuppressWarnings("unchecked")
  protected AbstractBucket<T>[] newBucketArray(int size) {
    return new AbstractBucket[size];
  }

  AbstractHashSetRVM(int size) {
    buckets = newBucketArray(size);
  }

  public int size() {
    return numElems;
  }

  /**
   * Advise against growing the buckets if they are immortal, as it will lead
   * to multiple sets of buckets that will be scanned
   */
  private boolean growMapAllowed() {
    return !VM.runningVM || !MemoryManager.isImmortal(buckets);
  }

  public void add(T key) {
    if (VM.VerifyAssertions) VM._assert(key != null);
    if (growMapAllowed() && numElems > (buckets.length * LOAD)) {
      growMap();
    }

    int bucketIdx = bucketIndex(key, buckets.length);
    AbstractBucket<T> cur = buckets[bucketIdx];
    while (cur != null && !cur.getKey().equals(key)) {
      cur = cur.getNext();
    }
    if (cur == null) {
      buckets[bucketIdx] = createNewBucket(key, buckets[bucketIdx]);
      numElems++;
    }
  }

  public T get(T key) {
    if (key == null) {
      return null;
    }
    int bucketIdx = bucketIndex(key, buckets.length);
    AbstractBucket<T> cur = buckets[bucketIdx];
    while (cur != null && !cur.getKey().equals(key)) {
      cur = cur.getNext();
    }
    if (cur == null) {
      return null;
    } else {
      return cur.getKey();
    }
  }

  public boolean contains(T key) {
    return get(key) != null;
  }

  public void addAll(AbstractHashSetRVM<T> c) {
    for (T t : c) {
      add(t);
    }
  }

  private void growMap() {
    AbstractBucket<T>[] newBuckets = newBucketArray(buckets.length * 2 + 1);
    for (AbstractBucket<T> cur : buckets) {
      while (cur != null) {
        AbstractBucket<T> next = cur.getNext();
        int newIdx = bucketIndex(cur.getKey(), newBuckets.length);
        cur = cur.setNext(newBuckets[newIdx]);
        newBuckets[newIdx] = cur;
        cur = next;
      }
    }
    buckets = newBuckets;
  }

  public void remove(T key) {
    if (VM.VerifyAssertions) VM._assert(key != null);
    int bucketIdx = bucketIndex(key, buckets.length);
    AbstractBucket<T> cur = buckets[bucketIdx];
    AbstractBucket<T> prev = null;
    while (cur != null && !cur.getKey().equals(key)) {
      prev = cur;
      cur = cur.getNext();
    }
    if (cur != null) {
      if (prev == null) {
        // removing first bucket in chain.
        buckets[bucketIdx] = cur.getNext();
      } else {
        AbstractBucket<T> newPrev = prev.setNext(cur.getNext());
        if (newPrev != prev) {
          throw new UnsupportedOperationException();
        }
      }
      numElems--;
    }
  }

  public void removeAll() {
    for (int i=0; i<buckets.length; i++) {
      buckets[i] = null;
    }
    numElems = 0;
  }

  @Override
  public Iterator<T> iterator() {
    return new SetIterator();
  }

  private int bucketIndex(T key, int divisor) {
    if (key == null) {
      return 0;
    } else {
      return (key.hashCode() & 0x7fffffff) % divisor;
    }
  }

  /**
   * Iterator
   */
  class SetIterator implements Iterator<T> {
    private int bucketIndex = 0;
    private AbstractBucket<T> next = null;
    private int numVisited = 0;

    @Override
    public T next() {
      if (!hasNext()) {
        throw new NoSuchElementException();
      }

      while (next == null) {
        next = buckets[bucketIndex++];
      }
      AbstractBucket<T> ans = next;
      next = ans.getNext();
      numVisited++;
      return ans.getKey();
    }

    @Override
    public boolean hasNext() {
      return numVisited < numElems;
    }

    @Override
    public void remove() {
      throw new UnsupportedOperationException();
    }
  }
}
