/*
 * (C) Copyright IBM Corp. 2001
 */
//$Id$
package com.ibm.JikesRVM.opt;

import  java.util.Enumeration;
import  java.util.NoSuchElementException;

/**
 * Enumeration that doesn't have any elements.
 * Use the EMPTY object to access.
 * 
 * @author Igor Pechtchanski
 */
public final class OPT_EmptyEnumerator
    implements Enumeration {
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



