/*
 * (C) Copyright IBM Corp. 2001
 */
// $Id$
package com.ibm.JikesRVM.opt;

import  java.util.Enumeration;
import  java.util.NoSuchElementException;

/**
 * This class provides an enumerator for a OPT_GCIRMap 
 * @author Michael Hind
 */
class OPT_GCIRMapEnumerator implements Enumeration {

  /**
   *  The next element to return when called
   */
  private OPT_GCIRMapElement nextElementToReturn;

  /**
   * constructor
   * @param list the list underlying the OPT_GCIRMap object
   */
  OPT_GCIRMapEnumerator(OPT_LinkedList list) {
    nextElementToReturn = (OPT_GCIRMapElement)list.first();
  }

  /**
   * Any elements left?
   * @return if any elements left
   */
  public final boolean hasMoreElements() {
    return nextElementToReturn != null;
  }

  /**
   * Returns the next element or throws an exception if none exist
   * @return the next element
   */
  public final Object nextElement() {
    if (nextElementToReturn != null) {
      return next();
    } 
    else {
      throw new NoSuchElementException("OPT_GCIRMapEnumerator");
    }
  }

  /**
   * Returns the next elmenent or NULL
   * @return the next elmenent or NULL
   */
  public final OPT_GCIRMapElement next() {
    OPT_GCIRMapElement ret = nextElementToReturn;
    nextElementToReturn = (OPT_GCIRMapElement)ret.getNext();
    return  ret;
  }
}



