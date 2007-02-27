/*
 * This file is part of Jikes RVM (http://jikesrvm.sourceforge.net).
 * The Jikes RVM project is distributed under the Common Public License (CPL).
 * A copy of the license is included in the distribution, and is also
 * available at http://www.opensource.org/licenses/cpl1.0.php
 *
 * (C) Copyright IBM Corp. 2001
 */
package com.ibm.jikesrvm.opt;

import  java.util.Enumeration;
import  java.util.NoSuchElementException;

/**
 * Enumeration that doesn't have any elements.
 * Use the EMPTY object to access.
 * 
 * @author Igor Pechtchanski
 */
public final class OPT_EmptyEnumerator
    implements Enumeration<Object> {
  public static final OPT_EmptyEnumerator EMPTY = new OPT_EmptyEnumerator();

  public boolean hasMoreElements () {
    return  false;
  }

  public Object nextElement () {
    throw  new NoSuchElementException();
  }

  private OPT_EmptyEnumerator () {
  }
}



