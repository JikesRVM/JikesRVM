/*
 * (C) Copyright IBM Corp. 2001
 */
//$Id$
package com.ibm.JikesRVM.opt;

/**
 * Reverse the order of an enumeration.
 * 
 * @author Stephen Fink
 */
import  java.util.Enumeration;
import  java.util.Vector;
import  java.util.NoSuchElementException;

final class OPT_ReverseEnumerator implements Enumeration {

  private Vector vec = new Vector();
  private int index;

  public boolean hasMoreElements () {
    return index > 0;
  }

  public Object nextElement () {
    index--;
    if (index >= 0) {
      return vec.elementAt(index);
    } else {
      throw  new NoSuchElementException();
    }
  }

  public OPT_ReverseEnumerator(Enumeration e) {
    for ( ; e.hasMoreElements(); ) {
      vec.addElement(e.nextElement());
    }
    index = vec.size();
  }
}



