/*
 *  This file is part of the Jikes RVM project (http://jikesrvm.org).
 *
 *  This file is licensed to You under the Common Public License (CPL);
 *  You may not use this file except in compliance with the License. You
 *  may obtain a copy of the License at
 *
 *      http://www.opensource.org/licenses/cpl1.0.php
 *
 *  See the COPYRIGHT.txt file distributed with this work for information
 *  regarding copyright ownership.
 */
package org.mmtk.plan;

import org.mmtk.vm.VM;
import org.vmmagic.pragma.*;
import org.vmmagic.unboxed.*;

/**
 * This abstract class is the fundamental mechanism for performing a
 * transitive closure over an object graph.<p>
 *
 * Some mechanisms only operate on nodes or edges, but due to limitations
 * of inheritance we have combined these two here.
 *
 * @see org.mmtk.plan.TraceLocal
 */
@Uninterruptible
public abstract class TransitiveClosure {
  /**
   * Trace an edge during GC.
   *
   * @param objLoc The location containing the object reference.
   */
  public void processEdge(Address objLoc) {
    VM.assertions.fail("processEdge not implemented.");
  }

  /**
   * Trace a node during GC.
   *
   * @param object The object to be processed.
   */
  public void processNode(ObjectReference object) {
    VM.assertions.fail("processNode not implemented.");
  }
}
