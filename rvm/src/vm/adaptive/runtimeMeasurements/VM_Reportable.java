/*
 * (C) Copyright IBM Corp. 2001
 */
//$Id$

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







