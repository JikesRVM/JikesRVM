/*
 * This file is part of Jikes RVM (http://jikesrvm.sourceforge.net).
 * The Jikes RVM project is distributed under the Common Public License (CPL).
 * A copy of the license is included in the distribution, and is also
 * available at http://www.opensource.org/licenses/cpl1.0.php
 *
 * (C) Copyright IBM Corp. 2001, 2004
 */
package org.jikesrvm.opt;

import  java.util.Enumeration;
import  java.util.NoSuchElementException;

import org.jikesrvm.opt.ir.OPT_RegisterOperand;

/**
 * An enumeration over live set lists
 *
 * @author Michael Hind
 */
public class OPT_LiveSetEnumerator implements Enumeration<OPT_RegisterOperand> {

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
  public OPT_RegisterOperand nextElement() {
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



