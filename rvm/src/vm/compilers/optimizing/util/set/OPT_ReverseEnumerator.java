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
import java.util.Enumeration;
import java.util.ArrayList;
import java.util.NoSuchElementException;

public final class OPT_ReverseEnumerator implements Enumeration {

  private ArrayList vec = new ArrayList();
  private int index;

  public boolean hasMoreElements () {
    return index > 0;
  }

  public Object nextElement () {
    index--;
    if (index >= 0) {
      return vec.get(index);
    } else {
      throw new NoSuchElementException();
    }
  }

  public OPT_ReverseEnumerator(Enumeration e) {
    while(e.hasMoreElements()) {
      vec.add(e.nextElement());
    }
    index = vec.size();
  }
}



