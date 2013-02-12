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

/**
 * Concurrent trigger percentage
 */
public class ConcurrentTrigger extends org.vmutil.options.IntOption {
  /**
   * Create the option.
   */
  public ConcurrentTrigger() {
    super(Options.set, "Concurrent Trigger",
          "Concurrent trigger percentage",
          30);
  }

  /**
   * Only accept values between 1 and 100 (inclusive)
   */
  @Override
  protected void validate() {
    failIf(this.value <= 0, "Trigger must be between 1 and 100");
    failIf(this.value > 100, "Trigger must be between 1 and 100");
  }
}
