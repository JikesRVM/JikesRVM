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

/** 
 * An object that implements a bijection between whole numbers and
 * objects.
 *
 * @author Stephen Fink
 */
public interface OPT_BitSetMapping {
  /**
   * Return the object numbered n.
   */
  public Object getMappedObject(int n);

  /**
   * Return the number of a given object.
   */
  public int getMappedIndex(Object o);

  /**
   * Return the size of the domain of the bijection. 
   */
  public int getMappingSize();
}
