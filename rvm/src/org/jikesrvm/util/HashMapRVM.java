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

/**
 * Stripped down implementation of HashMap data structure for use
 * by core parts of the JikesRVM runtime. Consider the use of
 * {@link ImmutableEntryHashMapRVM} when the use of the HashMap
 * methods is limited.
 */
public final class HashMapRVM<K, V> extends AbstractHashMapRVM<K, V> {

  static final class Bucket<K, V> extends AbstractBucket<K,V> {
    private AbstractBucket<K, V> next;
    private final K key;
    private V value;

    Bucket(K k, V v, AbstractBucket<K, V> n) {
      key = k;
      value = v;
      next = n;
    }

    AbstractBucket<K, V> getNext() {
      return next;
    }

    AbstractBucket<K, V> setNext(AbstractBucket<K, V> n) {
      next = n;
      return this;
    }

    K getKey() {
      return key;
    }

    V getValue() {
      return value;
    }

    void setValue(V v) {
      value = v;
    }
  }

  @Override
  AbstractBucket<K,V> createNewBucket(K key, V value, AbstractBucket<K, V> next) {
    return new Bucket<K,V>(key, value, next);
  }

  public HashMapRVM() {
    super(DEFAULT_SIZE);
  }

  public HashMapRVM(int size) {
    super(size);
  }

  @Override
  protected boolean same(K k1, K k2) {
    return k1.equals(k2);
  }

  @Override
  protected int hashTheKey(K key) {
    return key.hashCode();
  }
}
