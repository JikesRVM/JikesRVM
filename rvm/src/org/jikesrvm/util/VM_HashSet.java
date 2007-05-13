/*
 * This file is part of Jikes RVM (http://jikesrvm.sourceforge.net).
 * The Jikes RVM project is distributed under the Common Public License (CPL).
 * A copy of the license is included in the distribution, and is also
 * available at http://www.opensource.org/licenses/cpl1.0.php
 *
 * (C) Copyright IBM Corp. 2006
 */
package org.jikesrvm.util;

import java.util.Iterator;
import java.util.NoSuchElementException;

/**
 * Stripped down implementation of HashSet for use
 * by core parts of the JikesRVM runtime.
 */
public final class VM_HashSet<T> implements Iterable<T> {
  private static final int DEFAULT_SIZE = 7;
  private static final float LOAD = 3; /* bias to save space by default */

  private Bucket<T>[] buckets;
  private int numElems = 0;
  
  public VM_HashSet() {
    this(DEFAULT_SIZE);
  }
  
  @SuppressWarnings("unchecked") // the java generic array problem
  private Bucket<T>[] newBucketArray(int size) {
    return new Bucket[size];
  }
  
  public VM_HashSet(int size) {
      buckets = newBucketArray(size);
  }

  public int size() {
    return numElems;
  }

  public void add(T key) {
    if (numElems > (buckets.length * LOAD)) {
      growMap();
    }

    int bucketIdx = bucketIndex(key, buckets.length);
    Bucket<T> cur = buckets[bucketIdx];
    while (cur != null && !cur.key.equals(key)) {
      cur = cur.next;
    }
    if (cur == null) {
      Bucket<T> newBucket = new Bucket<T>(key);
      newBucket.next = buckets[bucketIdx];
      buckets[bucketIdx] = newBucket;
      numElems++;
    }
  }
  
  public T get(T key) {
    int bucketIdx = bucketIndex(key, buckets.length);
    Bucket<T> cur = buckets[bucketIdx];
    while (cur != null && !cur.key.equals(key)) {
      cur = cur.next;
    }
    if (cur == null)
      return null;
    else
      return cur.key;
  }
  
  public boolean contains(T key) {
    return get(key) != null;
  }

  public void addAll(VM_HashSet<T> c) {
    for (T t : c) {
      add(t);
    }
  }


  private void growMap() {
    Bucket<T>[] newBuckets = newBucketArray(buckets.length*2+1);
    for (Bucket<T> cur : buckets) {
      while (cur != null) {
        Bucket<T> next = cur.next;
        int newIdx = bucketIndex(cur.key, newBuckets.length);
        cur.next = newBuckets[newIdx];
        newBuckets[newIdx] = cur;
        cur = next;
      }
    }
    buckets = newBuckets;
  }

  public void remove(T key) {
    int bucketIdx = bucketIndex(key, buckets.length);
    Bucket<T> cur = buckets[bucketIdx];
    Bucket<T> prev = null;
    while (cur != null && !cur.key.equals(key)) {
      prev = cur;
      cur = cur.next;
    }
    if (cur != null) {
      if (prev == null) {
        // removing first bucket in chain.
        buckets[bucketIdx] = cur.next;
      } else {
        prev.next = cur.next;
      }
      numElems--;
    } 
  }

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
  
  private static final class Bucket<T> {
    final T key;
    Bucket<T> next;

    Bucket(T key) {
      this.key = key;
    }
  }

  /**
   * Iterator 
   */
  private class SetIterator implements Iterator<T> {
    private int bucketIndex = 0;
    private Bucket<T> next = null;
    private Bucket<T> last = null;
    private int numVisited = 0;

    public T next() {
      if (!hasNext()) {
        throw new NoSuchElementException();
      }
        
      while (next == null) {
        next = buckets[bucketIndex++];
      }
      Bucket<T> ans = next;
      next = ans.next;
      numVisited++;
      return ans.key;
    }

    public boolean hasNext() {
      return numVisited < numElems;
    }

    public void remove() {
      if (last == null) {
        throw new IllegalStateException();
      }
      VM_HashSet.this.remove(last.key);
      last = null;
    }
  }
}


    
