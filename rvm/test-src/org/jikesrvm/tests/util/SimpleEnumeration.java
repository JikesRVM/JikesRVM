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
package org.jikesrvm.tests.util;

import java.util.Collection;
import java.util.Deque;
import java.util.Enumeration;
import java.util.LinkedList;
import java.util.NoSuchElementException;

public class SimpleEnumeration<T> implements Enumeration<T> {

  private final Deque<T> deque;

  public SimpleEnumeration(Collection<T> collection) {
    deque = new LinkedList<T>();
    deque.addAll(collection);
  }

  @Override
  public T nextElement() {
    if (!deque.isEmpty()) {
      return deque.removeFirst();
    }
    throw new NoSuchElementException();
  }

  @Override
  public boolean hasMoreElements() {
    return !deque.isEmpty();
  }

}
