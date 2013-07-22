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
package org.mmtk.harness.lang.compiler;

import java.util.ArrayList;
import java.util.List;

import org.mmtk.harness.lang.Trace;
import org.mmtk.harness.lang.Trace.Item;
import org.mmtk.harness.lang.type.Type;

public class Temporary {

  private List<Register> freePool = new ArrayList<Register>();

  private int nextIndex = 0;

  /**
   * Get a free temporary from the pool, or create a new one.
   * @return
   */
  public Register acquire(Type type) {
    if (freePool.isEmpty()) {
      Register tmp = Register.createTemporary(nextIndex++, type);
      Trace.trace(Item.COMPILER,"Acquire new temporary, %s", tmp);
      return tmp;
    }
    Register result = freePool.remove(freePool.size()-1);
    Trace.trace(Item.COMPILER,"Acquire temporary, %s", result);
    result.setUsed();
    result.setType(type);
    return result;
  }

  public void release(Register...temp) {
    for (Register t : temp) {
      if (t.isTemporary()) {
        t.setFree();
        t.setType(Type.NULL);
        freePool.add(t);
      }
    }
  }

  /**
   * Allocate a new temporary register pool
   *
   * @param firstIndex
   */
  public Temporary(int firstIndex) {
    nextIndex = firstIndex;
  }

  public int size() {
    return nextIndex;
  }
}
