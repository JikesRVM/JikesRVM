/*
 * (C) Copyright IBM Corp. 2006
 */
//$Id$
package com.ibm.JikesRVM.util;

import java.util.Iterator;
import java.util.NoSuchElementException;
  
/**
 * Stripped down implementation of HashMap data structure for use
 * by core parts of the JikesRVM runtime.
 *
 * While developing; have a bogus impl by forwarding to java.util.HashMap
 * This won't actually fix anything, but enables me to see how widely used this
 * data structure is going to need to be and what API I have to support on it.
 *
 * TODO: This should be a final class; rewrite subclasses to let us do that.
 *
 * @author Dave Grove
 */
public class VM_HashMap {
  private static final boolean STAGING = false;
  private static final int DEFAULT_SIZE = 7;
  private static final float LOAD = 3; /* bias to save space by default */

    /* if STAGING */
  private final java.util.HashMap map;
  /* if !STAGING */
  private Bucket[] buckets;
  private int numElems = 0;
  
  public VM_HashMap() {
    this(DEFAULT_SIZE);
  }
      
  
  public VM_HashMap(int size) {
    if (STAGING) {
      map = new java.util.HashMap(size);
    } else {
      map = null;
      buckets = new Bucket[size];
    }
  }

  public final int size() {
    return numElems;
  }
  
  public final Object get(Object key) {
    if (STAGING) {
      return map.get(key);
    } else {
      int bucketIdx = bucketIndex(key, buckets.length);
      Bucket cur = buckets[bucketIdx];
      while (cur != null && !cur.key.equals(key)) {
        cur = cur.next;
      }
      if (cur == null) {
        return null;
      } else {
        return cur.value;
      }
    }
  }

  public final Object put(Object key, Object value) {
    if (STAGING) {
      return map.put(key, value);
    } else {
      if (numElems > (buckets.length * LOAD)) {
        growMap();
      }

      int bucketIdx = bucketIndex(key, buckets.length);
      Bucket cur = buckets[bucketIdx];
      while (cur != null && !cur.key.equals(key)) {
        cur = cur.next;
      }
      if (cur != null) {
        // replacing existing <key,value> pair
        Object tmp = cur.value;
        cur.value = value;
        return tmp;
      } else {
        Bucket newBucket = new Bucket(key, value);
        newBucket.next = buckets[bucketIdx];
        buckets[bucketIdx] = newBucket;
        numElems++;
        return null;
      }
    }
  }

  private final void growMap() {
    Bucket[] newBuckets = new Bucket[buckets.length*2+1];
    for (int i=0; i<buckets.length; i++) {
      Bucket cur = buckets[i];
      while (cur != null) {
        Bucket next = cur.next;
        int newIdx = bucketIndex(cur.key, newBuckets.length);
        cur.next = newBuckets[newIdx];
        newBuckets[newIdx] = cur;
        cur = next;
      }
    }
    buckets = newBuckets;
  }
  
  public final Object remove(Object key) {
    if (STAGING) {
      return map.remove(key);
    } else {
      int bucketIdx = bucketIndex(key, buckets.length);
      Bucket cur = buckets[bucketIdx];
      Bucket prev = null;
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
  }

  public final Iterator valueIterator() {
    if (STAGING) {
      return map.values().iterator();
    } else {
      return new MapIterator(false);
    }
  }
    
  public final Iterator keyIterator() {
    if (STAGING) {
      return map.keySet().iterator();
    } else {
      return new MapIterator(true);
    }
  }

  private final int bucketIndex(Object key, int divisor) {
    if (key == null) {
      return 0;
    } else {
      return (key.hashCode() & 0x7fffffff) % divisor;
    }
  }
  
  private static final class Bucket {
    final Object key;
    Object value;
    Bucket next;

    Bucket(Object k, Object v) {
      key = k;
      value = v;
    }
  }

  private final class MapIterator implements Iterator {
    private final boolean key;
    private int bucketIndex = 0;
    private Bucket next = null;
    private Bucket last = null;
    private int numVisited = 0;

    MapIterator(boolean k) {
      key = k;
    }
    
    public Object next() {
      if (!hasNext()) {
        throw new NoSuchElementException();
      }
        
      while (next == null) {
        next = buckets[bucketIndex++];
      }
      Bucket ans = next;
      next = ans.next;
      numVisited++;
      return key ? ans.key : ans.value;
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
}
