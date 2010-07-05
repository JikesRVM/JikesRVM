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
package org.mmtk.harness.options;

import org.mmtk.harness.Harness;
import org.vmutil.options.BooleanOption;

/**
 * By default, the sanity checker loads pointers from the heap using
 * the read barrier.  It's possible that this isn't appropriate
 * for some collectors (eg Baker), so this option makes it optional.
 */
public class SanityUsesReadBarrier extends BooleanOption {

  /**
   * Constructor
   */
  public SanityUsesReadBarrier() {
    super(Harness.options, "Sanity Uses Read Barrier",
        "Disable use of the read barrier in the harness sanity checker",
        Boolean.valueOf(System.getProperty("mmtk.harness.sanityusesreadbarrier", "true")));
  }

}
