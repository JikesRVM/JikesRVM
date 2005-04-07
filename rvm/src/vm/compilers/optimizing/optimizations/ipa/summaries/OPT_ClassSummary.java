/*
 * (C) Copyright IBM Corp. 2001,2005
 */
//$Id$
package com.ibm.JikesRVM.opt;

import com.ibm.JikesRVM.classloader.VM_Class;

/**
 * Hold semantic information about a class that is not defined in
 * {@link VM_Class}.  Note: This is currently unused but is kept around
 * in case we should have a need for it.
 * 
 * @author Stephen Fink
 */
public class OPT_ClassSummary {

  /**
   * @param v The {@link VM_Class} we want to store additional information
   * about. 
   */
  OPT_ClassSummary (VM_Class v) {
    vmClass = v;
  }
  /**
   * class this object tracks
   */
  VM_Class vmClass; 
}

