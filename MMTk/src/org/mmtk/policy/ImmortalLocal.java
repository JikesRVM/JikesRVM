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
package org.mmtk.policy;

import org.mmtk.utility.alloc.BumpPointer;

import org.vmmagic.pragma.*;

/**
 * This class implements unsynchronized (local) elements of an
 * immortal space. Allocation is via the bump pointer
 * (@see BumpPointer).
 *
 * @see BumpPointer
 * @see ImmortalSpace
 */
@Uninterruptible public final class ImmortalLocal extends BumpPointer {

  /**
   * Constructor
   *
   * @param space The space to bump point into.
   */
  public ImmortalLocal(ImmortalSpace space) {
    super(space, true);
  }
}
