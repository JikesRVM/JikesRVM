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

public class PretenureThresholdFraction extends org.vmutil.options.FloatOption {
  /**
   *
   */
  private static final float DEFAULT_PRETENURE_THRESHOLD_FRACTION = 0.5f; // if object is bigger than this fraction of nursery, pretenure to LOS

  /**
   * Create the option.
   */
  public PretenureThresholdFraction() {
    super(Options.set, "Pretenure Threshold Fraction",
          "Objects larger than this fraction of the remaining nursery will be allocated directly into the LOS.",
          DEFAULT_PRETENURE_THRESHOLD_FRACTION);
  }

  /**
   * Ensure the value is valid.
   */
  @Override
  protected void validate() {
    failIf((this.value < 0 || this.value > 1.0), "Ratio must be a float between 0 and 1");
  }
}
