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

import java.util.Enumeration;
import java.util.NoSuchElementException;

/**
 * Implements an empty enumeration.<p>
 *
 * The Collections class from Java 7 provides an {@code emptyEnumeration()} method
 * which will make this class unnecessary once we switch to Java 7.<p>
 *
 * TODO Remove this class when we require Java 7 for building and from
 * our class libraries.
 *
 * @param <E> type parameter
 */
public final class EmptyEnumeration<E> implements Enumeration<E> {

  @SuppressWarnings("unchecked")
  private static final EmptyEnumeration<?> INSTANCE = new EmptyEnumeration();

  /**
   * Non-instantiable. Use {@link #emptyEnumeration()} to
   * create instances.
   */
  private EmptyEnumeration() {
    // prevent instantiation
  }

  @Override
  public boolean hasMoreElements() {
    return false;
  }

  @Override
  public E nextElement() {
    throw new NoSuchElementException("The empty enumeration has no elements!");
  }

  @SuppressWarnings("unchecked")
  public static <T> Enumeration<T> emptyEnumeration() {
    return (EmptyEnumeration<T>) INSTANCE;
  }

}
