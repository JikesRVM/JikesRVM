/*
 * (C) Copyright Department of Computer Science,
 *     Australian National University. 2004
 */

package org.mmtk.policy;

import org.mmtk.utility.Log;
import org.mmtk.utility.heap.FreeListPageResource;
import org.mmtk.vm.Assert;
import org.mmtk.vm.Constants;

import org.vmmagic.pragma.*;
import org.vmmagic.unboxed.*;

/**
 * Each instance of this class corresponds to one raw page space.
 *
 * This class provides access to raw memory for managing internal meta
 * data.
 *
 *  $Id$
 *
 * @author <a href="http://cs.anu.edu.au/~Steve.Blackburn">Steve Blackburn</a>
 * @version $Revision$
 * @date $Date$
 */
public final class RawPageSpace extends Space 
  implements Constants, Uninterruptible {

  public RawPageSpace(String name, int pageBudget, Address start,
		      Extent bytes) {
    super(name, false, false, start, bytes);
    pr = new FreeListPageResource(pageBudget, this, start, extent);
  }
 
  public RawPageSpace(String name, int pageBudget, int mb) {
    super(name, false, false, mb);
    pr = new FreeListPageResource(pageBudget, this, start, extent);
  }
  
  public RawPageSpace(String name, int pageBudget, int mb, boolean top) {
    super(name, false, false, mb, top);
    pr = new FreeListPageResource(pageBudget, this, start, extent);
  }

  public final void prepare() { }
  public final void release() { }

  /**
   * Release a group of pages that were allocated together.
   *
   * @param first The first page in the group of pages that were
   * allocated together.
   */
  public final void release(Address first) throws InlinePragma {
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
  public final Address traceObject(Address object) throws InlinePragma {
    if (Assert.VERIFY_ASSERTIONS) Assert._assert(false);
    return Address.zero();
  }

  public final boolean isLive(Address obj) {
    return true;
  }
}
