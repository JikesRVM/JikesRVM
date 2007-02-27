/*
 * This file is part of Jikes RVM (http://jikesrvm.sourceforge.net).
 * The Jikes RVM project is distributed under the Common Public License (CPL).
 * A copy of the license is included in the distribution, and is also
 * available at http://www.opensource.org/licenses/cpl1.0.php
 *
 * (C) Copyright IBM Corp. 2001
 */
package com.ibm.jikesrvm.opt;

import java.util.Iterator;
import com.ibm.jikesrvm.util.VM_LinkedList;

/**
 * @author Mauricio J. Serrano
 * @author John Whaley
 * @author Robin Garner
 */
final class OPT_Queue<T> implements Iterable<T> {
  private final VM_LinkedList<T> elements = new VM_LinkedList<T>();

  OPT_Queue() { }

  OPT_Queue(T e) {
    elements.add(e);
  }

  final T insert(T e) {
    elements.add(e);            // Insert at tail
    return e;
  }

  final T remove() {
    return elements.remove(0);  // Remove from head
  }

  final boolean isEmpty() {
    return elements.size() == 0;
  }

  public final Iterator<T> iterator() {
    return elements.iterator();
  }
}
