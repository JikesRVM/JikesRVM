/*
 * This file is part of MMTk (http://jikesrvm.sourceforge.net).
 * MMTk is distributed under the Common Public License (CPL).
 * A copy of the license is included in the distribution, and is also
 * available at http://www.opensource.org/licenses/cpl1.0.php
 *
 * (C) Copyright Department of Computer Science,
 * Australian National University. 2002
 */
package org.mmtk.policy;

import org.mmtk.plan.TraceLocal;
import org.mmtk.utility.heap.*;
import org.mmtk.utility.Constants;

import org.vmmagic.pragma.*;
import org.vmmagic.unboxed.*;

/**
 * Each instance of this class corresponds to one *space*.
 * Each of the instance methods of this class may be called by any
 * thread (i.e. synchronization must be explicit in any instance or
 * class method).  This contrasts with the MarkSweepLocal, where
 * instances correspond to *plan* instances and therefore to kernel
 * threads.  Thus unlike this class, synchronization is not necessary
 * in the instance methods of MarkSweepLocal.
 * 
 *  $Id$
 * 
 * @author Steve Blackburn
 * @author Daniel Frampton
 * @version $Revision$
 * @date $Date$
 */
@Uninterruptible public final class ExplicitFreeListSpace extends Space
  implements Constants {

  /****************************************************************************
   * 
   * Class variables
   */
  public static final int LOCAL_GC_BITS_REQUIRED = 0;
  public static final int GLOBAL_GC_BITS_REQUIRED = 0;
  public static final int GC_HEADER_WORDS_REQUIRED = 0;

  /****************************************************************************
   * 
   * Initialization
   */

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
  public ExplicitFreeListSpace(String name, int pageBudget, Address start, 
                        Extent bytes) {
    super(name, false, false, start, bytes);
    pr = new FreeListPageResource(pageBudget, this, start, extent, ExplicitFreeListLocal.META_DATA_PAGES_PER_REGION);
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
  public ExplicitFreeListSpace(String name, int pageBudget, int mb) {
    super(name, false, false, mb);
    pr = new FreeListPageResource(pageBudget, this, start, extent, ExplicitFreeListLocal.META_DATA_PAGES_PER_REGION);
  }

  /**
   * Construct a space that consumes a given fraction of the available
   * virtual memory.<p>
   *
   * The caller specifies the amount virtual memory to be used for
   * this space <i>as a fraction of the total available</i>.  If there
   * is insufficient address space, then the constructor will fail.
   *
   * @param name The name of this space (used when printing error messages etc)
   * @param pageBudget The number of pages this space may consume
   * before consulting the plan
   * @param frac The size of the space in virtual memory, as a
   * fraction of all available virtual memory
   */
  public ExplicitFreeListSpace(String name, int pageBudget, float frac) {
    super(name, false, false, frac);
    pr = new FreeListPageResource(pageBudget, this, start, extent, ExplicitFreeListLocal.META_DATA_PAGES_PER_REGION);
  }

  /**
   * Construct a space that consumes a given number of megabytes of
   * virtual memory, at either the top or bottom of the available
   * virtual memory.
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
  public ExplicitFreeListSpace(String name, int pageBudget, int mb, boolean top) {
    super(name, false, false, mb, top);
    pr = new FreeListPageResource(pageBudget, this, start, extent, ExplicitFreeListLocal.META_DATA_PAGES_PER_REGION);
  }

  /**
   * Construct a space that consumes a given fraction of the available
   * virtual memory, at either the top or bottom of the available
   *          virtual memory.
   *
   * The caller specifies the amount virtual memory to be used for
   * this space <i>as a fraction of the total available</i>, and
   * whether it should be at the top or bottom of the available
   * virtual memory.  If the request clashes with existing virtual
   * memory allocations, then the constructor will fail.
   *
   * @param name The name of this space (used when printing error messages etc)
   * @param pageBudget The number of pages this space may consume
   * before consulting the plan
   * @param frac The size of the space in virtual memory, as a
   * fraction of all available virtual memory
   * @param top Should this space be at the top (or bottom) of the
   * available virtual memory.
   */
  public ExplicitFreeListSpace(String name, int pageBudget, float frac, boolean top) {
    super(name, false, false, frac, top);
    pr = new FreeListPageResource(pageBudget, this, start, extent, ExplicitFreeListLocal.META_DATA_PAGES_PER_REGION);
  }

  /****************************************************************************
   * 
   * Collection
   */

  /**
   * Prepare for a new collection increment.
   */
  public void prepare() {}

  /**
   * A new collection increment has completed. 
   */
  public void release() {}

  /**
   * Release an allocated page or pages
   * 
   * @param start The address of the start of the page or pages
   */
  public final void release(Address start) throws InlinePragma {
    ((FreeListPageResource) pr).releasePages(start);
  }

  /****************************************************************************
   * 
   * Object processing and tracing
   */

  /**
   * Trace a reference to an object under a mark sweep collection
   * policy.  If the object header is not already marked, mark the
   * object in either the bitmap or by moving it off the treadmill,
   * and enqueue the object for subsequent processing. The object is
   * marked as (an atomic) side-effect of checking whether already
   * marked.
   *
   * @param object The object to be traced.
   * @return The object (there is no object forwarding in this
   * collector, so we always return the same object: this could be a
   * void method but for compliance to a more general interface).
   */
  public final ObjectReference traceObject(TraceLocal trace,
                                           ObjectReference object)
    throws InlinePragma {
    return object;
  }

  /**
   * 
   * @param object The object in question
   * @return True if this object is known to be live (i.e. it is marked)
   */
  public boolean isLive(ObjectReference object)
    throws InlinePragma {
    return true;
  }
}
