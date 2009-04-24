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
package org.mmtk.harness.lang;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;

/**
 * An ArrayList-based stack, to avoid the unnecessary synchronization that
 * the Vector based java.util.Stack class inherits from.
 *
 * @param <E> Element type of the stack
 */
public class UnsyncStack<E> implements Iterable<E> {
  /** The elements of the stack. */
  private final ArrayList<E> elements = new ArrayList<E>();

  /**
   * Push to the top of the stack
   * @param value
   * @return
   */
  public E push(E value) {
    elements.add(value);
    return value;
  }

  /**
   * Pop off the stack
   * @return
   */
  public E pop() {
    return elements.remove(elements.size()-1);
  }

  /**
   * Look at the top element
   * @return
   */
  public E peek() {
    return elements.get(elements.size()-1);
  }

  /**
   * # elements in the stack.
   * @return
   */
  public int size() {
    return elements.size();
  }

  /**
   * Are there any elements in the stack ?
   * @return
   */
  public boolean isEmpty() {
    return size() == 0;
  }

  /**
   * Iterate over the stack contents - top of stack to bottom.
   */
  @Override
  public Iterator<E> iterator() {
    ArrayList<E> tmp = new ArrayList<E>(elements);
    Collections.reverse(tmp);
    return tmp.iterator();
  }
}
