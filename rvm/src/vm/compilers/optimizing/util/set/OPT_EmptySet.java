/*
 * (C) Copyright IBM Corp. 2001
 */
//$Id$
package com.ibm.JikesRVM.opt;

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



