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

import org.mmtk.harness.vm.ObjectModel;
import org.mmtk.policy.CopySpace;
import org.mmtk.policy.Space;
import org.vmmagic.unboxed.Address;
import org.vmmagic.unboxed.ObjectReference;

/**
 * Enforce the CopySpace invariant that all live objects are in to-space
 * <p>
 * This should be true at the start of the RELEASE collection phase
 */
public class FromSpaceInvariant implements HeapVisitor {

  /**
   * Creation of a FromSpaceInvariant object enforces the invariant.
   */
  public FromSpaceInvariant() {
    Traversal.traverse(this);
  }

  /**
   * Unused - the heap invariant is enforced on pointers (edges)
   */
  @Override
  public void visitObject(ObjectReference object, boolean root, boolean marked) {
  }

  /**
   * Enforce the invariant.  We use this visitor method so we can
   */
  @Override
  public void visitPointer(ObjectReference source, Address slot, ObjectReference target) {
    Space space = Space.getSpaceForObject(target);
    if (space instanceof CopySpace && ((CopySpace)space).isFromSpace()) {
      assert false : String.format("### Object %s is in from-space, pointed to by %s slot %s%n",
          ObjectModel.getString(target), ObjectModel.getString(source), slot);
    }
  }
}
