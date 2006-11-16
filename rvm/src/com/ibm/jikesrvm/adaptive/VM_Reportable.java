/*
 * This file is part of Jikes RVM (http://jikesrvm.sourceforge.net).
 * The Jikes RVM project is distributed under the Common Public License (CPL).
 * A copy of the license is included in the distribution, and is also
 * available at http://www.opensource.org/licenses/cpl1.0.php
 *
 * (C) Copyright IBM Corp. 2001
 */
//$Id$
package com.ibm.jikesrvm.adaptive;

/**
 * Interface for all reportable objects that are managed by the runtime
 * measurements.
 *
 * @author Peter Sweeney
 */

interface VM_Reportable { 
  /**
   * generate a report
   */
  void report(); 
  /**
   * reset (clear) data set being gathered
   */
  void reset();  
}







