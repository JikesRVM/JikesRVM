/*
 * This file is part of Jikes RVM (http://jikesrvm.sourceforge.net).
 * The Jikes RVM project is distributed under the Common Public License (CPL).
 * A copy of the license is included in the distribution, and is also
 * available at http://www.opensource.org/licenses/cpl1.0.php
 *
 * (C) Copyright IBM Corp. 2006
 */
package com.ibm.jikesrvm.util;

import java.util.Iterator;
import java.util.NoSuchElementException;
  
/**
 * Stripped down implementation of HashMap data structure for use
 * by core parts of the JikesRVM runtime.
 *
 * TODO: This should be a final class; rewrite subclasses to let us do that.
 *
 * @author Dave Grove
 * @author Robin Garner
 */
public class VM_HashMap<K,V> {
  private static final int DEFAULT_SIZE = 7;
  private static final float LOAD = 3; /* bias to save space by default */

  private Bucket<K,V>[] buckets;
  private int numElems = 0;
  
  public VM_HashMap() {
    this(DEFAULT_SIZE);
  }
  
  @SuppressWarnings("unchecked") // the java generic array problem
  private Bucket<K,V>[] newBucketArray(int size) {
    return new Bucket[size];
  }
  
  public VM_HashMap(int size) {
    buckets = newBucketArray(size);
  }

  public final int size() {
    return numElems;
  }
  
  public final V get(K key) {
    int bucketIdx = bucketIndex(key, buckets.length);
    Bucket<K,V> cur = buckets[bucketIdx];
    while (cur != null && !cur.key.equals(key)) {
      cur = cur.next;
    }
    if (cur == null) {
      return null;
    } else {
      return cur.value;
    }
  }

  public final V put(K key, V value) {
    if (numElems > (buckets.length * LOAD)) {
      growMap();
    }

    int bucketIdx = bucketIndex(key, buckets.length);
    Bucket<K,V> cur = buckets[bucketIdx];
    while (cur != null && !cur.key.equals(key)) {
      cur = cur.next;
    }
    if (cur != null) {
      // replacing existing <key,value> pair
      V tmp = cur.value;
      cur.value = value;
      return tmp;
    } else {
      Bucket<K,V> newBucket = new Bucket<K,V>(key, value);
      newBucket.next = buckets[bucketIdx];
      buckets[bucketIdx] = newBucket;
      numElems++;
      return null;
    }
  }

  private void growMap() {
    Bucket<K,V>[] newBuckets = newBucketArray(buckets.length*2+1);
    for (int i=0; i<buckets.length; i++) {
      Bucket<K,V> cur = buckets[i];
      while (cur != null) {
        Bucket<K,V> next = cur.next;
        int newIdx = bucketIndex(cur.key, newBuckets.length);
        cur.next = newBuckets[newIdx];
        newBuckets[newIdx] = cur;
        cur = next;
      }
    }
    buckets = newBuckets;
  }
  
  public final V remove(K key) {
    int bucketIdx = bucketIndex(key, buckets.length);
    Bucket<K,V> cur = buckets[bucketIdx];
    Bucket<K,V> prev = null;
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
      return cur.value;
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
      return (key.hashCode() & 0x7fffffff) % divisor;
    }
  }
  
  private static final class Bucket<K,V> {
    final K key;
    V value;
    Bucket<K,V> next;

    Bucket(K k, V v) {
      key = k;
      value = v;
    }
  }

  /**
   * Iterator types for key and value
   */
  private class BucketIterator {
    private int bucketIndex = 0;
    private Bucket<K,V> next = null;
    private Bucket<K,V> last = null;
    private int numVisited = 0;

    public Bucket<K,V> nextBucket() {
      if (!hasNext()) {
        throw new NoSuchElementException();
      }
        
      while (next == null) {
        next = buckets[bucketIndex++];
      }
      Bucket<K,V> ans = next;
      next = ans.next;
      numVisited++;
      return ans;
    }

    public boolean hasNext() {
      return numVisited < numElems;
    }

    public void remove() {
      if (last == null) {
        throw new IllegalStateException();
      }
      VM_HashMap.this.remove(last.key);
      last = null;
    }
  }
  
  private final class KeyIterator extends BucketIterator implements Iterator<K> {
    public K next() {
      Bucket<K,V> cur = nextBucket(); 
      return cur.key;
    }
  }
  
  private final class ValueIterator extends BucketIterator implements Iterator<V> {
    public V next() {
      Bucket<K,V> cur = nextBucket(); 
      return cur.value;
    }
  }
  
  /**
   * These two methods allow VM_HashMaps to be used in the Java 5 for loop.
   */
  
  /**
   * @return a java.lang.Iterable for the values in the hash map
   */
  public final Iterable<V> values() {
    return new Iterable<V>() {
      public Iterator<V> iterator() {
        return VM_HashMap.this.valueIterator();
      }
    };
  }
  
  /**
   * 
   * @return a java.lang.Iterable for the values in the hash map
   */
  public final Iterable<K> keys() {
    return new Iterable<K>() {
      public Iterator<K> iterator() {
        return VM_HashMap.this.keyIterator();
      }
    };
  }
}
