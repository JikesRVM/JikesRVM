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
package org.mmtk.policy;

import org.mmtk.plan.TraceLocal;
import org.mmtk.utility.heap.FreeListPageResource;
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
@Uninterruptible public final class RawPageSpace extends Space 
  implements Constants {

  /**
   * The caller specifies the region of virtual memory to be used for
   * this space.  If this region conflicts with an existing space,
   * then the constructor will fail.
   * 
   * @param name The name of this space (used when printing error messages etc)
   * @param pageBudget The number of pages this space may consume
   * before consulting the plan
   * @param start The start address of the space in virtual memory
   * @param bytes The size of the space in virtual memory, in bytes
   */
  public RawPageSpace(String name, int pageBudget, Address start,
                      Extent bytes) {
    super(name, false, false, start, bytes);
    pr = new FreeListPageResource(pageBudget, this, start, extent);
  }

  /**
   * Construct a space of a given number of megabytes in size.<p>
   * 
   * The caller specifies the amount virtual memory to be used for
   * this space <i>in megabytes</i>.  If there is insufficient address
   * space, then the constructor will fail.
   *
   * @param name The name of this space (used when printing error messages etc)
   * @param pageBudget The number of pages this space may consume
   * before consulting the plan
   * @param mb The size of the space in virtual memory, in megabytes (MB)
   */
  public RawPageSpace(String name, int pageBudget, int mb) {
    super(name, false, false, mb);
    pr = new FreeListPageResource(pageBudget, this, start, extent);
  }

  /**
   * Construct a space that consumes a given number of megabytes of
   * virtual memory, at either the top or bottom of the available
   *          virtual memory.
   *
   * The caller specifies the amount virtual memory to be used for
   * this space <i>in megabytes</i>, and whether it should be at the
   * top or bottom of the available virtual memory.  If the request
   * clashes with existing virtual memory allocations, then the
   * constructor will fail.
   *
   * @param name The name of this space (used when printing error messages etc)
   * @param pageBudget The number of pages this space may consume
   * before consulting the plan
   * @param mb The size of the space in virtual memory, in megabytes (MB)
   * @param top Should this space be at the top (or bottom) of the
   * available virtual memory.
   */
  public RawPageSpace(String name, int pageBudget, int mb, boolean top) {
    super(name, false, false, mb, top);
    pr = new FreeListPageResource(pageBudget, this, start, extent);
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
  public ObjectReference traceObject(TraceLocal trace,
                                           ObjectReference object) { 
    if (VM.VERIFY_ASSERTIONS) VM.assertions._assert(false);
    return ObjectReference.nullReference();
  }

  public boolean isLive(ObjectReference object) {
    return true;
  }
}
