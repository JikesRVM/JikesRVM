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

import java.util.*;

/** 
 * @author Mauricio J. Serrano
 * @author John Whaley
 */
public class OPT_CompoundEnumerator<T> implements Enumeration<T> {
  private final Enumeration<T> first;    
  private final Enumeration<T> second;

  public OPT_CompoundEnumerator(Enumeration<T> first, Enumeration<T> second) {
    this.first = first;
    this.second = second;
  }

  public boolean hasMoreElements() {
    return first.hasMoreElements()||second.hasMoreElements();
  }

  public T nextElement() {
    if (first.hasMoreElements())
      return first.nextElement();
    else
      return second.nextElement();
  }
}
