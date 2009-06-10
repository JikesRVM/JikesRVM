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
 * Stripped down implementation of HashSet for use
 * by core parts of the JikesRVM runtime. Consider the use of
 * {@link ImmutableEntryHashSetRVM} when the use of the HashSet
 * methods is limited.
 */
public final class HashSetRVM<T> extends AbstractHashSetRVM<T> {
  static final class Bucket<T> extends AbstractBucket<T> {
    private final T key;
    private AbstractBucket<T> next;

    Bucket(T key, AbstractBucket<T> next) {
      this.key = key;
      this.next = next;
    }

    @Override
    AbstractBucket<T> setNext(AbstractBucket<T> next) {
      this.next = next;
      return this;
    }

    @Override
    AbstractBucket<T> getNext() {
      return next;
    }

    @Override
    T getKey() {
      return key;
    }
  }

  @Override
  AbstractBucket<T> createNewBucket(T key, AbstractBucket<T> next) {
    return new Bucket<T>(key, next);
  }

  public HashSetRVM() {
    super(DEFAULT_SIZE);
  }

  public HashSetRVM(int size) {
    super(size);
  }
}
