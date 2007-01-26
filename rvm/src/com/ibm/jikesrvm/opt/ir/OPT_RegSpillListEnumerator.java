/*
 * This file is part of Jikes RVM (http://jikesrvm.sourceforge.net).
 * The Jikes RVM project is distributed under the Common Public License (CPL).
 * A copy of the license is included in the distribution, and is also
 * available at http://www.opensource.org/licenses/cpl1.0.php
 *
 * (C) Copyright IBM Corp. 2001
 */
// $Id$
package com.ibm.jikesrvm.opt.ir;

import java.util.Enumeration;
import java.util.NoSuchElementException;

/**
 * This class provides an enumerator for a list of OPT_RegSpillElements
 * @author Michael Hind
 */
public class OPT_RegSpillListEnumerator implements Enumeration<OPT_RegSpillListElement> {

  /**
   *  The next element to return when called
   */
  private OPT_RegSpillListElement nextElementToReturn;

  /**
   * constructor
   * @param list the list to enumerate over
   */
//  OPT_RegSpillListEnumerator(OPT_LinkedList list) {
//    nextElementToReturn = (OPT_RegSpillListElement)list.first();
//  }

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
  public final OPT_RegSpillListElement nextElement() {
    if (nextElementToReturn != null) {
      return next();
    } 
    else {
      throw new NoSuchElementException("OPT_RegSpillListElementEnumerator");
    }
  }

  /**
   * Returns the next element or throws an exception if none exist
   * @return the next element
   */
  public final OPT_RegSpillListElement next() {
    OPT_RegSpillListElement ret = nextElementToReturn;
//    nextElementToReturn = (OPT_RegSpillListElement)ret.getNext();
    return  ret;
  }
}



