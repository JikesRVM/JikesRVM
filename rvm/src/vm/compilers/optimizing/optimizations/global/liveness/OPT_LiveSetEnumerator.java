/*
 * (C) Copyright IBM Corp. 2001, 2004
 */
//$Id$
package com.ibm.JikesRVM.opt;

import  java.util.Enumeration;
import  java.util.NoSuchElementException;

/**
 * An enumeration over live set lists
 *
 * @author Michael Hind
 */
public class OPT_LiveSetEnumerator implements Enumeration {

  /**
   *  the current element on this list
   */
  private OPT_LiveSetElement current;

  /**
   * The constructor
   * @param   list  The {@link OPT_LiveSetElement} at the head of the list.
   */
  public OPT_LiveSetEnumerator(OPT_LiveSetElement list) {
    current = list;
  }

  /**
   * Are there any more elements?
   * @return whether there are any more elements?
   */
  public boolean hasMoreElements() {
    return  current != null;
  }

  /**
   * Returns the next element, if one exists, otherwise throws an exception
   * @return the next element, if one exists, otherwise throws an exception
   */
  public Object nextElement() {
    if (current != null) {
      OPT_LiveSetElement ret = current;
      current = current.getNext();
      return  ret.getRegisterOperand();
    } 
    else {
      throw  new NoSuchElementException("OPT_LiveSetEnumerator");
    }
  }
}



