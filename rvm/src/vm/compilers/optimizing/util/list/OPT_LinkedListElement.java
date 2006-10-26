/*
 * This file is part of Jikes RVM (http://jikesrvm.sourceforge.net).
 * The Jikes RVM project is distributed under the Common Public License (CPL).
 * A copy of the license is included in the distribution, and is also
 * available at http://www.opensource.org/licenses/cpl1.0.php
 *
 * (C) Copyright IBM Corp. 2001
 */
//$Id$
package com.ibm.jikesrvm.opt;

/**
 * @author Mauricio J. Serrano
 * @author John Whaley
 */
public abstract class OPT_LinkedListElement {
  protected OPT_LinkedListElement next;

  /**
   * Returns a pointer to the next element in the list, or null if this is the
   * last element.
   *
   * @return pointer to next element
   */
  public final OPT_LinkedListElement getNext() {
    return  next;
  }

  public final void setNext(OPT_LinkedListElement next) {
    if (this != next)
      this.next = next;
  }

  public final void insertAfter(OPT_LinkedListElement e) {
    if (this != e) {
      e.next = next;
      next = e;
    }
  }

  public final void insertBefore(OPT_LinkedListElement e) {
    if (this != e)
      e.next = this;
  }

  public final void removeNext() {
    next = next.next;
  }

  /**
   * Append given linked list to self.
   *
   * @param l list to append
   */
  public final OPT_LinkedListElement append(OPT_LinkedListElement l) {
    if (this == l)
      return  this;
    if (next != null)
      next.append(l); 
    else 
      next = l;
    return  this;
  }
}
