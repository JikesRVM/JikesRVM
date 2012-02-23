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

import org.mmtk.plan.TransitiveClosure;
import org.mmtk.utility.heap.FreeListPageResource;
import org.mmtk.utility.heap.VMRequest;
import org.mmtk.utility.Constants;

import org.mmtk.vm.VM;

import org.vmmagic.pragma.*;
import org.vmmagic.unboxed.*;

/**
 * Each instance of this class corresponds to one raw page space.
 *
 * This class provides access to raw memory for managing internal meta
 * data.
 */
@Uninterruptible
public final class RawPageSpace extends Space implements Constants {

  /**
   * The caller specifies the region of virtual memory to be used for
   * this space.  If this region conflicts with an existing space,
   * then the constructor will fail.
   *
   * @param name The name of this space (used when printing error messages etc)
   * @param vmRequest An object describing the virtual memory requested.
   */
  public RawPageSpace(String name, VMRequest vmRequest) {
    super(name, false, false, true, vmRequest);
    if (vmRequest.isDiscontiguous()) {
      pr = new FreeListPageResource(this, 0);
    } else {
      pr = new FreeListPageResource(this, start, extent);
    }
  }

  public void prepare() { }
  public void release() { }

  /**
   * Release a group of pages that were allocated together.
   *
   * @param first The first page in the group of pages that were
   * allocated together.
   */
  @Inline
  public void release(Address first) {
    ((FreeListPageResource) pr).releasePages(first);
  }

  /**
   * Trace an object.
   *
   * This makes no sense for a raw page space and should never be
   * called.
   *
   * @param object The object to be traced.
   * @return <code>zero</code>: calling this is an error.
   */
  @Inline
  public ObjectReference traceObject(TransitiveClosure trace, ObjectReference object) {
    if (VM.VERIFY_ASSERTIONS) VM.assertions._assert(false);
    return ObjectReference.nullReference();
  }

  public boolean isLive(ObjectReference object) {
    return true;
  }
}
