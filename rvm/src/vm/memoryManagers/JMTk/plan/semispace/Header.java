/*
 * (C) Copyright IBM Corp. 2001
 */
//$Id$

package com.ibm.JikesRVM.memoryManagers.JMTk;

/**
 * Chooses the appropriate collector-specific header model.
 *
 * @see VM_ObjectModel
 * 
 * @author Perry Cheng
 */
public class Header extends CopyingHeader {

  // Merges all the headers together.  In this case, we have only one.

  public final static int GC_BARRIER_BIT_MASK = -1;  // must be defined even though unused
}
