/*
 * (C) Copyright IBM Corp. 2001
 */
//$Id$
package com.ibm.JikesRVM;

/**
 * A VM_MethodListener that relies on its organizer alone to process
 * the sample data.
 *
 * @author Matthew Arnold
 * @author Stephen Fink
 * @author Dave Grove
 * @author Michael Hind
 * @author Peter Sweeney
 */
final class VM_BasicMethodListener extends VM_MethodListener 
  implements VM_Uninterruptible {

  /**
   * @param sampleSize the initial sampleSize for the listener
   */
  public VM_BasicMethodListener(int sampleSize) {
    super(sampleSize, true);
  }

  /** 
   * Nothing to report.
   */
  public void report() {
    VM.sysWrite("BasicMethodListener has nothing to report!");
  }


  /**
   * We rely on the organizer to process the samples, therefore
   * nothing to do.
   */
  public void processSamples() { }

} 
