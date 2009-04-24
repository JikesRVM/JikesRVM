/*
 *  This file is part of the Jikes RVM project (http://jikesrvm.org).
 *
 *  This file is licensed to You under the Eclipse Public License (EPL);
 *  You may not use this file except in compliance with the License. You
 *  may obtain a copy of the License at
 *
 *      http://www.opensource.org/licenses/eclipse-1.0.php
 *
 *  See the COPYRIGHT.txt file distributed with this work for information
 *  regarding copyright ownership.
 */
package org.mmtk.utility.options;

import org.vmmagic.pragma.*;

/**
 * The granularity of the trace being produced.
 */
public final class TraceRate extends org.vmutil.options.IntOption
  implements org.mmtk.utility.Constants {
  /**
   * Create the option.
   */
  public TraceRate() {
    super(Options.set, "Trace Rate",
        "The granularity of the trace being produced.  By default, the trace has the maximum possible granularity.",
        Integer.MAX_VALUE);
  }

  /**
   * Return the appropriate value.
   *
   * @return the trace rate.
   */
  @Uninterruptible
  public int getValue() {
    return (this.value < BYTES_IN_ADDRESS) ? 1 : (this.value >> LOG_BYTES_IN_ADDRESS);
  }

  /**
   * Trace rate must be positive.
   */
  protected void validate() {
    failIf(value <= 0, "Can not have a negative trace rate");
  }
}
