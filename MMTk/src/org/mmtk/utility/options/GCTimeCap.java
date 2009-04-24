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
 * Try to limit reference counting collections to this time cap.
 */
public final class GCTimeCap extends org.vmutil.options.MicrosecondsOption {
  /**
   * Create the option.
   */
  public GCTimeCap() {
    super(Options.set, "GC Time Cap",
          "Try to limit reference counting collections to this time cap",
          1000000);
  }
}
