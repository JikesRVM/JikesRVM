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
package org.jikesrvm.compilers.opt.util;

import java.util.Enumeration;
import java.util.NoSuchElementException;

/**
 * Enumeration that doesn't have any elements.
 * Use the EMPTY object to access.
 */
public final class EmptyEnumerator implements Enumeration<Object> {
  private static final EmptyEnumerator EMPTY = new EmptyEnumerator();

  @SuppressWarnings({"unchecked", "RedundantCast"})
  public static <T> Enumeration<T> emptyEnumeration() {
    return (Enumeration<T>) (Enumeration) EMPTY;
  }

  public boolean hasMoreElements() {
    return false;
  }

  public Object nextElement() {
    throw new NoSuchElementException();
  }

  private EmptyEnumerator() {
  }
}



