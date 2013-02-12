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
package org.jikesrvm.compilers.opt.liveness;

import java.util.Enumeration;

import org.jikesrvm.compilers.opt.regalloc.LiveIntervalElement;

/**
 * Enumerator for a list of live intervals stored on a basic block.
 * <p>
 * Note: This is fragile.  Use with care iff you know what you're doing.
 * <p>
 * TODO: redesign the way live info is stored on the IR to be a bit more
 * robust.  e.g., don't use scratch fields.
 */
public class LiveIntervalEnumeration implements Enumeration<LiveIntervalElement> {
  private LiveIntervalElement currentElement;

  /**
   * @param first  The first live interval in a list to be enumerated
   */
  public LiveIntervalEnumeration(LiveIntervalElement first) {
    this.currentElement = first;
  }

  @Override
  public boolean hasMoreElements() {
    return currentElement != null;
  }

  @Override
  public LiveIntervalElement nextElement() {
    LiveIntervalElement result = currentElement;
    currentElement = currentElement.getNext();
    return result;
  }
}
