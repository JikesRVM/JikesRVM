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

import static org.mmtk.policy.immix.ImmixConstants.DEFAULT_DEFRAG_FREE_HEADROOM;

public class DefragFreeHeadroom extends org.vmutil.options.PagesOption {
  /**
   * Create the option.
   */
  public DefragFreeHeadroom() {
    super(Options.set, "Defrag Free Headroom",
          "Allow the defragmenter this amount of free headroom during defrag. For analysis purposes only!",
          DEFAULT_DEFRAG_FREE_HEADROOM);
  }
}
