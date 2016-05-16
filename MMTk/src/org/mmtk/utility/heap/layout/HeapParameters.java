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
package org.mmtk.utility.heap.layout;

/**
 * VM-Independent parameters of the Virtual Memory Layout.
 * <p>
 * MUST NOT have a dependency on the VM class(es), because the constants
 * defined in this class are intended to be accessible to the VM, and we
 * mustn't create a circular dependence.
 */
public class HeapParameters {

  /**
   * log_2 of the maximum number of spaces a Plan can support.
   */
  public static final int LOG_MAX_SPACES = 4;

  /**
   * Maximum number of spaces a Plan can support.
   */
  public static final int MAX_SPACES = 1 << LOG_MAX_SPACES;

  /**
   * In a 64-bit addressing model, each space is the same size, given
   * by this constant.  At the moment, we require that the number of
   * pages in a space fit into a 32-bit signed int, so the maximum
   * size of this constant is 41 (assuming 4k pages).
   */
  public static final int LOG_SPACE_SIZE_64 = 41;

}
