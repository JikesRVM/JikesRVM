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
 * A hash set with entirely immutable buckets. It doesn't correctly support
 * remove, so use with care.
 */
public final class ImmutableEntryHashSetRVM<T> extends AbstractHashSetRVM<T> {

  static final class Bucket<T> extends AbstractBucket<T> {
    private final T key;
    private final AbstractBucket<T> next;

    Bucket(T key, AbstractBucket<T> next) {
      this.key = key;
      this.next = next;
    }

    @Override
    AbstractBucket<T> setNext(AbstractBucket<T> next) {
      if (this.next == next) {
        return this;
      } else {
        return new Bucket<T>(key, next);
      }
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

  public ImmutableEntryHashSetRVM() {
    super(DEFAULT_SIZE);
  }

  public ImmutableEntryHashSetRVM(int size) {
    super(size);
  }

  @Override
  public void remove(T key) {
    throw new UnsupportedOperationException();
  }
}
