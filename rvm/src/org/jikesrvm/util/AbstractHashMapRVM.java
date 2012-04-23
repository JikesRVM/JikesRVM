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
 * Common super class for all VM hash maps
 */
abstract class AbstractHashMapRVM<K, V> {

  protected static final int DEFAULT_SIZE = 7;
  private static final float LOAD = 3;
  protected AbstractBucket<K, V>[] buckets;
  protected int numElems = 0;

  abstract static class AbstractBucket<K, V> {
    abstract AbstractBucket<K, V> getNext();

    /**
     * Change the next bucket after this bucket, possibly constructing a new
     * abstract bucket
     */
    abstract AbstractBucket<K, V> setNext(AbstractBucket<K, V> n);

    abstract K getKey();

    abstract V getValue();

    abstract void setValue(V v);
  }

  /**
   * Are two keys the same?
   */
  abstract boolean same(K key1, K key2);

  abstract int hashTheKey(K key);

  abstract AbstractBucket<K,V> createNewBucket(K k, V v, AbstractBucket<K,V> n);

  AbstractHashMapRVM(int size) {
    buckets = newBucketArray(size);
  }

  public final int size() {
    return numElems;
  }

  public final V get(K key) {
    if (key == null) {
      return null;
    }
    int bucketIdx = bucketIndex(key, buckets.length);
    AbstractBucket<K, V> cur = buckets[bucketIdx];
    while (cur != null && !same(cur.getKey(), key)) {
      cur = cur.getNext();
    }
    if (cur == null) {
      return null;
    } else {
      return cur.getValue();
    }
  }

  /**
   * Advise against growing the buckets if they are immortal, as it will lead
   * to multiple sets of buckets that will be scanned
   */
  private boolean growMapAllowed() {
    return !VM.runningVM || !MemoryManager.isImmortal(buckets);
  }

  public final V put(K key, V value) {
    if (VM.VerifyAssertions) VM._assert(key != null);
    if (growMapAllowed() && numElems > (buckets.length * LOAD)) {
      growMap();
    }

    int bucketIdx = bucketIndex(key, buckets.length);
    AbstractBucket<K, V> cur = buckets[bucketIdx];
    while (cur != null && !same(cur.getKey(), key)) {
      cur = cur.getNext();
    }
    if (cur != null) {
      // replacing existing <key,value> pair
      V tmp = cur.getValue();
      cur.setValue(value);
      return tmp;
    } else {
      buckets[bucketIdx] = createNewBucket(key, value, buckets[bucketIdx]);
      numElems++;
      return null;
    }
  }

  @SuppressWarnings("unchecked")
  private AbstractBucket<K, V>[] newBucketArray(int size) {
    return new AbstractBucket[size];
  }

  private void growMap() {
    AbstractBucket<K, V>[] newBuckets = newBucketArray(buckets.length * 2 + 1);
    // Iterate over all buckets
    for (AbstractBucket<K, V> cur : buckets) {
      // Iterate over all values in a bucket
      while (cur != null) {
        AbstractBucket<K, V> next = cur.getNext();
        int newIdx = bucketIndex(cur.getKey(), newBuckets.length);
        cur = cur.setNext(newBuckets[newIdx]);
        newBuckets[newIdx] = cur;
        cur = next;
      }
    }
    buckets = newBuckets;
  }

  public V remove(K key) {
    if (VM.VerifyAssertions) VM._assert(key != null);
    int bucketIdx = bucketIndex(key, buckets.length);
    AbstractBucket<K, V> cur = buckets[bucketIdx];
    AbstractBucket<K, V> prev = null;
    while (cur != null && !same(cur.getKey(), key)) {
      prev = cur;
      cur = cur.getNext();
    }
    if (cur != null) {
      if (prev == null) {
        // removing first bucket in chain.
        buckets[bucketIdx] = cur.getNext();
      } else {
        AbstractBucket<K, V> newPrev = prev.setNext(cur.getNext());
        if (newPrev != prev) {
          throw new UnsupportedOperationException();
        }
      }
      numElems--;
      return cur.getValue();
    } else {
      return null;
    }
  }

  public final Iterator<V> valueIterator() {
    return new ValueIterator();
  }

  public final Iterator<K> keyIterator() {
    return new KeyIterator();
  }

  private int bucketIndex(K key, int divisor) {
    if (key == null) {
      return 0;
    } else {
      return (hashTheKey(key) & 0x7fffffff) % divisor;
    }
  }

  /**
   * @return a java.lang.Iterable for the values in the hash map
   */
  public final Iterable<V> values() {
    return new Iterable<V>() {
      @Override
      public Iterator<V> iterator() {
        return AbstractHashMapRVM.this.valueIterator();
      }
    };
  }

  /**
   *
   * @return a java.lang.Iterable for the values in the hash map
   */
  public final Iterable<K> keys() {
    return new Iterable<K>() {
      @Override
      public Iterator<K> iterator() {
        return AbstractHashMapRVM.this.keyIterator();
      }
    };
  }

  /**
   * Iterator types for key and value
   */
  private class BucketIterator {
    private int bucketIndex = 0;
    private AbstractBucket<K, V> next = null;
    private int numVisited = 0;

    public AbstractBucket<K, V> nextBucket() {
      if (!hasNext()) {
        throw new NoSuchElementException();
      }

      while (next == null) {
        next = buckets[bucketIndex++];
      }
      AbstractBucket<K, V> ans = next;
      next = ans.getNext();
      numVisited++;
      return ans;
    }

    public boolean hasNext() {
      return numVisited < numElems;
    }

    public void remove() {
      throw new UnsupportedOperationException();
    }
  }

  private final class KeyIterator extends BucketIterator implements Iterator<K> {
    @Override
    public K next() {
      AbstractBucket<K, V> cur = nextBucket();
      return cur.getKey();
    }
  }

  private final class ValueIterator extends BucketIterator implements Iterator<V> {
    @Override
    public V next() {
      AbstractBucket<K, V> cur = nextBucket();
      return cur.getValue();
    }
  }
}
