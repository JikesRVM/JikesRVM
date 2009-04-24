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
package org.mmtk.harness.sanity;

import org.vmmagic.unboxed.Address;
import org.vmmagic.unboxed.ObjectReference;

/**
 * Interface to visitor objects for the sanity traversal mechanism
 */
interface HeapVisitor {
  /**
   * Called once each time a heap object is reached
   * @param object The live object
   * @param root Was the referring pointer a root ?
   * @param marked Was the object marked prior to this visit (ie is this a second or later visit)
   */
  void visitObject(ObjectReference object, boolean root, boolean marked);

  /**
   * Called once for every non-null pointer
   * @param source Source object
   * @param slot Slot in the source object
   * @param target Target object
   */
  void visitPointer(ObjectReference source, Address slot, ObjectReference target);
}
