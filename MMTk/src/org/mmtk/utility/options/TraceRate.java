/*
 * (C) Copyright Department of Computer Science,
 * Australian National University. 2004
 */
package org.mmtk.utility.options;

import org.vmmagic.pragma.UninterruptiblePragma;

/**
 * The granularity of the trace being produced.
 * 
 * @author Daniel Frampton
 * @version $Revision$
 * @date $Date$
 */
public class TraceRate extends IntOption implements org.mmtk.vm.Constants {
  /**
   * Create the option.
   */
  public TraceRate() {
    super("Trace Rate",
          "The granularity of the trace being produced.  By default, the trace has the maximum possible granularity.  (Log 2 of the num of bytes)",
          Integer.MAX_VALUE);
  }

  /**
   * Return the appropriate value. 
   *
   * @return the trace rate.
   */
  public int getValue() throws UninterruptiblePragma {
    return (this.value < LOG_BYTES_IN_ADDRESS) 
      ? 0 
      : this.value - LOG_BYTES_IN_ADDRESS;
  }
}
