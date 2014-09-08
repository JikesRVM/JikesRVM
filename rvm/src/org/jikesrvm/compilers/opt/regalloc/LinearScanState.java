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
package org.jikesrvm.compilers.opt.regalloc;

import java.util.ArrayList;

public final class LinearScanState {
  /**
   * The live interval information, a set of Basic Intervals
   * sorted by increasing start point
   */
  public final ArrayList<BasicInterval> intervals = new ArrayList<BasicInterval>();

  /**
   * Was any register spilled?
   */
  public boolean spilledSomething = false;

  /**
   * Analysis information used by linear scan.
   */
  public ActiveSet active;
}
