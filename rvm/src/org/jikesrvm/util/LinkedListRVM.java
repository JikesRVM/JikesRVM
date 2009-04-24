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

import java.util.Collection;
import java.util.Iterator;
import java.util.List;
import java.util.ListIterator;
import org.jikesrvm.VM;

/**
 * Implementation of java.util.LinkedList for use in classes that
 * end up in the boot image.
 */
public final class LinkedListRVM<T> implements List<T> {

  /** Element count */
  private int count = 0;

  /** pointer to first element in the list */
  Element<T> head = null;

  /** pointer to last element in the list */
  Element<T> tail = null;

  /**
   * Insert an element at a given position in the list.
   * <p>
   * UNIMPLEMENTED
   *
   * @param pos Position in the list (0..size()-1)
   * @param entry Element to insert
   */
  public void add(int pos, T entry) {
    if (VM.VerifyAssertions) VM._assert(false);
  }

  /**
   * Insert at the tail of the list
   *
   * @param entry The entry to add.
   * @return true (as per java collections framework standard)
   */
  public boolean add(final T entry) {
    final Element<T> element = new Element<T>(entry);
    element.next = null;
    if (head == null) {
      if (VM.VerifyAssertions) VM._assert(tail == null);
      head = element;
      element.prev = null;
    } else {
      tail.next = element;
      element.prev = tail;
    }
    tail = element;
    count++;
    return true;
  }

  /**
   * Insert an entry after the given element.  Used via the iterator.
   *
   * @param e List element
   * @param t New list entry
   */
  void insertAfter(Element<T> e, T t) {
    Element<T> newElement = new Element<T>(t);
    if (e == null) {
      newElement.next = head;
      newElement.prev = null;
      head = newElement;
    } else {
      newElement.next = e.next;
      newElement.prev = e;
      if (e.next != null) {
        e.next.prev = newElement;
      }
      e.next = newElement;
    }
    if (tail == null || tail == e) {
      tail = newElement;
    }
    count++;
  }

  /**
   * Add all members of the given collection.
   * <p>
   * UNIMPLEMENTED
   */
  public boolean addAll(Collection<? extends T> arg0) {
    if (VM.VerifyAssertions) VM._assert(false);
    return false;
  }

  /**
   * Add all members of the given collection after the given element.
   * <p>
   * UNIMPLEMENTED
   */
  public boolean addAll(int arg0, Collection<? extends T> arg1) {
    if (VM.VerifyAssertions) VM._assert(false);
    return false;
  }

  /**
   * Discard all entries in the list
   */
  public void clear() {
    head = tail = null;
    count = 0;
  }

  /**
   * Membership test
   *
   * @param arg0 Object to check
   * @return true if the list contains arg0, false otherwise
   */
  public boolean contains(Object arg0) {
    return indexOf(arg0) != -1;
  }

  /**
   * Set inclusion test
   *
   * @param arg0 Objects to check
   * @return true if the list contains all objects in arg0, false otherwise
   */
  public boolean containsAll(Collection<?> arg0) {
    for (Object o : arg0) {
      if (!contains(o)) {
        return false;
      }
    }
    return true;
  }

  /**
   * Return the nth element of the list
   * <p>
   * UNIMPLEMENTED
   * @param index
   */
  public T get(int index) {
    /* Special-case getting the head of the list for speed */
    if (index == 0 && head != null) {
      return head.entry;
    }

    /* bounds check */
    if (index < 0 || index >= size()) {
      throw new IndexOutOfBoundsException();
    }

    Element<T> cursor = head;
    for (int i = 0; i < index; i++) {
      cursor = cursor.next;
    }
    return cursor.entry;
  }

  /**
   * Return the position of the given element.
   *
   * @param arg0 Member to test for.
   * @return Zero-based index of the element, or -1 if not found.
   */
  public int indexOf(Object arg0) {
    int i = 0;
    for (T t : this) {
      if (t == arg0) {
        return i;
      }
      i++;
    }
    return -1;
  }

  public boolean isEmpty() {
    return count == 0;
  }

  public Iterator<T> iterator() {
    return new LinkedListIteratorRVM<T>(this);
  }

  /** UNIMPLEMENTED */
  public int lastIndexOf(Object arg0) {
    if (VM.VerifyAssertions) VM._assert(false);
    return 0;
  }

  public ListIterator<T> listIterator() {
    return new LinkedListIteratorRVM<T>(this);
  }

  /** UNIMPLEMENTED */
  public ListIterator<T> listIterator(int arg0) {
    if (VM.VerifyAssertions) VM._assert(false);
    return null;
  }

  /**
   * Remove the nth element of the list.
   *
   * @param index n
   * @return The nth element
   */
  public T remove(int index) {
    /* bounds check */
    if (index < 0 || index >= size()) {
      throw new IndexOutOfBoundsException();
    }

    Element<T> cursor = head;
    for (int i = 0; i < index; i++) {
      cursor = cursor.next;
    }
    removeInternal(cursor);
    return cursor.entry;
  }

  /**
   * Remove the given element from the list
   */
  public boolean remove(Object arg0) {
    Element<T> cursor = head;
    while (cursor != null && !(arg0 == null ? cursor.entry == null : cursor.entry.equals(arg0))) {
      cursor = cursor.next;
    }
    if (cursor == null) {
      return false;
    } else {
      removeInternal(cursor);
      return true;
    }
  }

  void removeInternal(Element<T> e) {
    if (e.prev == null) {
      if (VM.VerifyAssertions) VM._assert(e == head);
      head = e.next;
    } else {
      e.prev.next = e.next;
    }

    if (e.next == null) {
      if (VM.VerifyAssertions) VM._assert(e == tail);
      tail = e.prev;
    } else {
      e.next.prev = e.prev;
    }

    count--;
  }

  /** UNIMPLEMENTED */
  public boolean removeAll(Collection<?> arg0) {
    if (VM.VerifyAssertions) VM._assert(false);
    return false;
  }

  /** UNIMPLEMENTED */
  public boolean retainAll(Collection<?> arg0) {
    if (VM.VerifyAssertions) VM._assert(false);
    return false;
  }

  /** UNIMPLEMENTED */
  public T set(int arg0, T arg1) {
    if (VM.VerifyAssertions) VM._assert(false);
    return null;
  }

  public int size() {
    return count;
  }

  /** UNIMPLEMENTED */
  public List<T> subList(int arg0, int arg1) {
    if (VM.VerifyAssertions) VM._assert(false);
    return null;
  }

  /** UNIMPLEMENTED */
  public Object[] toArray() {
    if (VM.VerifyAssertions) VM._assert(false);
    return null;
  }

  /** UNIMPLEMENTED */
  public <U> U[] toArray(U[] arg0) {
    if (VM.VerifyAssertions) VM._assert(false);
    return null;
  }

  /**
   * Class for the actual elements of the list.
   *
   *
   * @param <T> Type of the entry
   */
  static class Element<T> {
    Element<T> next;
    Element<T> prev;
    T entry;

    Element(T entry) {
      this.entry = entry;
    }
  }
}
