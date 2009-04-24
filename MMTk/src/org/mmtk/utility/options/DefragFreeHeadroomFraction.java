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

import static org.mmtk.policy.immix.ImmixConstants.DEFAULT_DEFRAG_FREE_HEADROOM_FRACTION;

public class DefragFreeHeadroomFraction extends org.vmutil.options.FloatOption {
  /**
   * Create the option.
   */
  public DefragFreeHeadroomFraction() {
    super(Options.set, "Defrag Free Headroom Fraction",
          "Allow the defragmenter this fraction of the heap size as free headroom during defrag. For analysis purposes only!",
          DEFAULT_DEFRAG_FREE_HEADROOM_FRACTION);
  }

  /**
   * Ensure the value is valid.
   */
  protected void validate() {
    failIf((this.value < 0 || this.value > 1.0), "Ratio must be a float between 0 and 1");
  }
}
