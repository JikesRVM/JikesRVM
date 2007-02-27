/*
 * This file is part of Jikes RVM (http://jikesrvm.sourceforge.net).
 * The Jikes RVM project is distributed under the Common Public License (CPL).
 * A copy of the license is included in the distribution, and is also
 * available at http://www.opensource.org/licenses/cpl1.0.php
 *
 * (C) Copyright IBM Corp. 2001,2005
 */
package com.ibm.jikesrvm.opt;

import com.ibm.jikesrvm.classloader.VM_Class;

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

