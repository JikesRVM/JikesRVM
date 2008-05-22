/*
 *  This file is part of the Jikes RVM project (http://jikesrvm.org).
 *
 *  This file is licensed to You under the Common Public License (CPL);
 *  You may not use this file except in compliance with the License. You
 *  may obtain a copy of the License at
 *
 *      http://www.opensource.org/licenses/cpl1.0.php
 *
 *  See the COPYRIGHT.txt file distributed with this work for information
 *  regarding copyright ownership.
 */
package org.jikesrvm.util;

/**
 * A hash map with entirely immutable buckets. It doesn't correctly support
 * remove, and its values cannot be mutated by a put with the same key - use
 * with care.
 */
public final class VM_ImmutableEntryHashMap<K, V> extends VM_AbstractHashMap<K,V> {

  static final class Bucket<K, V> extends AbstractBucket<K,V> {
    private final AbstractBucket<K, V> next;
    private final K key;
    private final V value;

    Bucket(K k, V v, AbstractBucket<K, V> n) {
      key = k;
      value = v;
      next = n;
    }

    AbstractBucket<K, V> getNext() {
      return next;
    }

    AbstractBucket<K, V> setNext(AbstractBucket<K, V> n) {
      if (next == n) {
        return this;
      } else {
        return new Bucket<K, V>(key, value, n);
      }
    }

    K getKey() {
      return key;
    }

    V getValue() {
      return value;
    }

    void setValue(V v) {
      throw new UnsupportedOperationException();
    }
  }

  @Override
  AbstractBucket<K,V> createNewBucket(K key, V value, AbstractBucket<K, V> next) {
    return new Bucket<K,V>(key, value, next);
  }

  public VM_ImmutableEntryHashMap() {
    super(DEFAULT_SIZE);
  }

  public VM_ImmutableEntryHashMap(int size) {
    super(size);
  }

  @Override
  protected boolean same(K k1, K k2) {
    return k1.equals(k2);
  }
}
