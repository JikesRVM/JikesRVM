/*
 * (C) Copyright IBM Corp. 2001
 */
//$Id$

package org.mmtk.vm;

import org.mmtk.utility.deque.AddressDeque;


/**
 * Class that determines all JTOC slots (statics) that hold references
 *
 * @author Perry Cheng
 */  
public class ScanStatics
  {

  /**
   * Scan static variables (JTOC) for object references.
   * Executed by all GC threads in parallel, with each doing a portion of the JTOC.
   */
  public static void scanStatics (AddressDeque rootLocations) {

  }  // scanStatics


}   // VM_ScanStatics
