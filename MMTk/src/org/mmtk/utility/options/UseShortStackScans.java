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
 * Should a major GC be performed when a system GC is triggered?
 */
public final class UseShortStackScans extends org.vmutil.options.BooleanOption {
  /**
   * Create the option.
   */
  public UseShortStackScans() {
    super(Options.set, "Use Short Stack Scans",
          "Use optimization that only scans the part of the stack that has changed since last GC?",
          false);
  }
}
