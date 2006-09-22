/*
 * This file is part of the Jikes RVM project (http://jikesrvm.sourceforge.net).
 * The Jikes RVM project is distributed under the Common Public License (CPL).
 * A copy of the license is included in the distribution, and is also
 * available at http://www.opensource.org/licenses/cpl1.0.php
 *
 * (C) Copyright IBM Corp. 2006
 */
//$Id$
package com.ibm.JikesRVM.util;

import java.util.Iterator;

/**
 * Stripped down implementation of HashSet for use
 * by core parts of the JikesRVM runtime.
 *
 * TODO: Make this more space efficient by implementing it directly
 *       instead of creating a silly HashMap to back it.
 * @author Dave Grove
 */
public final class VM_HashSet {

  private final VM_HashMap map;
  
  public VM_HashSet() {
    map = new VM_HashMap();
  }

  public VM_HashSet(int size) {
    map = new VM_HashMap(size);
  }

  public void add(Object o) {
    map.put(o, null);
  }

  public void remove(Object o) {
    map.remove(o);
  }

  public Iterator iterator() {
    return map.keyIterator();
  }

  public void addAll(VM_HashSet c) {
    for (Iterator it = c.iterator(); it.hasNext(); ) {
      add(it.next());
    }
  }
}


    
