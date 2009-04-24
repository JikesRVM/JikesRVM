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
 * Trigger cycle buffer filtering if the space available falls below this threshold.
 */
public final class CycleFilterThreshold extends org.vmutil.options.PagesOption {
  /**
   * Create the option.
   */
  public CycleFilterThreshold() {
    super(Options.set, "Cycle Filter Threshold",
        "Trigger cycle buffer filtering if the space available falls below this threshold",
        512);
  }
}
