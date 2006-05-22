/*
 * (C) Copyright IBM Corp. 2006
 */
//$Id$
package com.ibm.JikesRVM.util;


/**
 * Stripped down implementation of HashMap data structure for use
 * by core parts of the JikesRVM runtime.
 *
 * While developing; have a bogus impl by simply subclassing java.util.HashSet
 * This won't actually fix anything, but enables me to see how widely used this
 * data structure is going to need to be and what API I have to support on it.
 *
 * TODO: This should be a final class; rewrite subclasses to let us do that.
 * 
 * @author Dave Grove
 */
public class VM_HashSet extends java.util.HashSet {

  public VM_HashSet() {
    super();
  }

  public VM_HashSet(int size) {
    super(size);
  }
  
}


    
