/*
 * (C) Copyright IBM Corp. 2001, 2004
 */
// $Id$
package com.ibm.JikesRVM.opt.ir;

import com.ibm.JikesRVM.opt.OPT_LinkedList;
import java.util.Enumeration;
import java.util.NoSuchElementException;

/**
 * This class provides an enumerator for a OPT_GCIRMap 
 * @author Michael Hind
 */
public class OPT_GCIRMapEnumerator implements Enumeration {

  /**
   *  The next element to return when called.
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
   * Are there any elements left?
   * @return true if any elements left, false otherwise
   */
  public final boolean hasMoreElements() {
    return nextElementToReturn != null;
  }

  /**
   * Returns the next element, and advances the read pointer past this
   * element.
   * @return the next element
   * @throws NoSuchElementException if there is no next element.
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
   * Returns the next element, and advances the read pointer to the
   * element after that.
   * @return the next element
   * @throws NullPointerException if there is no next element to return.
   */
  public final OPT_GCIRMapElement next() {
    OPT_GCIRMapElement ret = nextElementToReturn;
    /* if ret is null, we'll automatically throw a NullPointerException. */
    nextElementToReturn = (OPT_GCIRMapElement)ret.getNext(); 
    return ret;
  }
}



