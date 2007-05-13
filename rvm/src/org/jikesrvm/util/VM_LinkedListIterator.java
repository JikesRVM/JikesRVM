package org.jikesrvm.util;

import java.util.ListIterator;
import java.util.NoSuchElementException;
import org.jikesrvm.VM;

public final class VM_LinkedListIterator<T> implements ListIterator<T> {
  boolean canRemove = false;

  /** The list we are iterating over */
  final VM_LinkedList<T> l;

  /** Pointer to the current (most recently returned) element. */
  private VM_LinkedList.Element<T> cursor = null;

  /**
   * Constructor
   *
   * @param l The list to iterate over.
   */
  VM_LinkedListIterator(VM_LinkedList<T> l) {
    this.l = l;
  }

  public void add(T arg0) {
    l.insertAfter(cursor, arg0);
    cursor = cursor.next;
  }

  public boolean hasNext() {
    return cursor != l.tail;
  }

  public boolean hasPrevious() {
    return cursor != l.head;
  }

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

  public int nextIndex() {
    if (VM.VerifyAssertions) VM._assert(false);
    return 0;
  }

  public T previous() {
    if (VM.VerifyAssertions) VM._assert(false);
    return null;
  }

  public int previousIndex() {
    if (VM.VerifyAssertions) VM._assert(false);
    return 0;
  }

  public void set(Object arg0) {
    if (VM.VerifyAssertions) VM._assert(false);
  }

}
