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

/**
 * A generic iterator containing no items.<p>
 *
 * NOTE: This class is only necessary until Java 7. Java 7's Collections
 * class provides an {@code emptyIterator()} method.<p>
 *
 * TODO Remove this class when we require Java 7 to build and all supported
 * class libraries support Java 7.
 */
public final class EmptyIterator<T> implements Iterator<T> {

  @SuppressWarnings("unchecked")
  private static final EmptyIterator<?> INSTANCE = new EmptyIterator();

  /**
   * Clients must use {@link #getInstance()} to obtain an instance.
   */
  private EmptyIterator() {
  }

  @Override
  public boolean hasNext() {
    return false;
  }

  @Override
  public T next() {
    throw new NoSuchElementException();
  }

  @Override
  public void remove() {
    throw new IllegalStateException();
  }

  @SuppressWarnings("unchecked")
  public static <U> Iterator<U> getInstance() {
    return (Iterator<U>) INSTANCE;
  }
}
