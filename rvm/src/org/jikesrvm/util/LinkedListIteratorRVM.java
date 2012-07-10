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

import java.util.ListIterator;
import java.util.NoSuchElementException;
import org.jikesrvm.VM;

public final class LinkedListIteratorRVM<T> implements ListIterator<T> {
  boolean canRemove = false;

  /** The list we are iterating over */
  final LinkedListRVM<T> l;

  /** Pointer to the current (most recently returned) element. */
  private LinkedListRVM.Element<T> cursor = null;

  /**
   * Constructor
   *
   * @param l The list to iterate over.
   */
  LinkedListIteratorRVM(LinkedListRVM<T> l) {
    this.l = l;
  }

  @Override
  public void add(T arg0) {
    l.insertAfter(cursor, arg0);
    cursor = cursor.next;
  }

  @Override
  public boolean hasNext() {
    return cursor != l.tail;
  }

  @Override
  public boolean hasPrevious() {
    return cursor != l.head;
  }

  @Override
  public T next() {
    if (cursor == null) {
      cursor = l.head;
    } else {
      if (cursor.next == null) {
        throw new NoSuchElementException();
      }
      cursor = cursor.next;
    }
    canRemove = true;
    return cursor.entry;
  }

  @Override
  public void remove() {
    if (canRemove) {
      l.removeInternal(cursor);
      canRemove = false;
    } else {
      throw new IllegalStateException();
    }
  }

  /* ---------------------------------------------------------------------- */
  /*                      Methods below unimplemented                       */
  /* ---------------------------------------------------------------------- */

  @Override
  public int nextIndex() {
    if (VM.VerifyAssertions) VM._assert(VM.NOT_REACHED);
    return 0;
  }

  @Override
  public T previous() {
    if (VM.VerifyAssertions) VM._assert(VM.NOT_REACHED);
    return null;
  }

  @Override
  public int previousIndex() {
    if (VM.VerifyAssertions) VM._assert(VM.NOT_REACHED);
    return 0;
  }

  @Override
  public void set(Object arg0) {
    if (VM.VerifyAssertions) VM._assert(VM.NOT_REACHED);
  }

}
