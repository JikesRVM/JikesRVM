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
 * @author Mauricio J. Serrano
 * @author John Whaley
 */
class OPT_EmptySet extends java.util.AbstractSet {
  public static OPT_EmptySet INSTANCE = new OPT_EmptySet();

  public java.util.Iterator iterator () {
    return  OPT_EmptyIterator.INSTANCE;
  }

  public int size () {
    return  0;
  }
}



