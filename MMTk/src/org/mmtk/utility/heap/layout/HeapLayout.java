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

import org.mmtk.vm.VM;
import org.vmmagic.pragma.Uninterruptible;

/**
 * Encapsulate all the components that contribute to the layout of the heap
 * in virtual memory.  This gives us a pluggable choice of heap layouts.<p>
 *
 * The class consists largely of static members containing singletons that implement
 * the various design choices.
 */
@Uninterruptible
public final class HeapLayout {

  /****************************************************************************
   * Class variables
   */

  public static final Map vmMap =
      VM.HEAP_LAYOUT_32BIT ? new Map32() : new Map64();

  public static final Mmapper mmapper =
      VM.HEAP_LAYOUT_32BIT ? new ByteMapMmapper() : new FragmentedMmapper();
}
