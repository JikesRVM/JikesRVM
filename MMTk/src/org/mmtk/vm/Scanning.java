/*
 * This file is part of MMTk (http://jikesrvm.sourceforge.net).
 * MMTk is distributed under the Common Public License (CPL).
 * A copy of the license is included in the distribution, and is also
 * available at http://www.opensource.org/licenses/cpl1.0.php
 *
 * (C) Copyright Department of Computer Science,
 * Australian National University. 2004
 *
 * (C) Copyright IBM Corp. 2001, 2003
 */
package org.mmtk.vm;

import org.mmtk.plan.TraceLocal;
import org.mmtk.plan.TraceStep;
import org.mmtk.utility.Constants;

import org.vmmagic.pragma.Uninterruptible;
import org.vmmagic.unboxed.*;

/**
 * $Id: Scanning.java,v 1.6 2006/06/21 07:38:13 steveb-oss Exp $
 * 
 * @author Steve Blackburn
 * @author Perry Cheng
 * 
 * @version $Revision: 1.6 $
 * @date $Date: 2006/06/21 07:38:13 $
 */
public abstract class Scanning implements Constants, Uninterruptible {
  /**
   * Delegated scanning of a object, processing each pointer field
   * encountered. 
   * 
   * @param object The object to be scanned.
   */
  public abstract void scanObject(TraceStep trace, ObjectReference object);

  /**
   * Delegated precopying of a object's children, processing each pointer field
   * encountered.
   * 
   * @param trace The trace object to use for precopying.
   * @param object The object to be scanned.
   */
  public abstract void precopyChildren(TraceLocal trace, ObjectReference object);

  /**
   * Prepares for using the <code>computeAllRoots</code> method.  The
   * thread counter allows multiple GC threads to co-operatively
   * iterate through the thread data structure (if load balancing
   * parallel GC threads were not important, the thread counter could
   * simply be replaced by a for loop).
   */
  public abstract void resetThreadCounter();

  /**
   * Pre-copy all potentially movable instances used in the course of
   * GC.  This includes the thread objects representing the GC threads
   * themselves.  It is crucial that these instances are forwarded
   * <i>prior</i> to the GC proper.  Since these instances <i>are
   * not</i> enqueued for scanning, it is important that when roots
   * are computed the same instances are explicitly scanned and
   * included in the set of roots.  The existence of this method
   * allows the actions of calculating roots and forwarding GC
   * instances to be decoupled. 
   */
  public abstract void preCopyGCInstances(TraceLocal trace);

  /**
   * Computes all roots.  This method establishes all roots for
   * collection and places them in the root values, root locations and
   * interior root locations queues.  This method should not have side
   * effects (such as copying or forwarding of objects).  There are a
   * number of important preconditions:
   * 
   * <ul>
   * <li> All objects used in the course of GC (such as the GC thread
   * objects) need to be "pre-copied" prior to calling this method.
   * <li> The <code>threadCounter</code> must be reset so that load
   * balancing parallel GC can share the work of scanning threads.
   * </ul>
   * 
   * @param trace The trace to use for computing roots.
   */
  public abstract void computeAllRoots(TraceLocal trace);

  
  /**
   * Compute all roots out of the VM's boot image (if any).  This method is a no-op
   * in the case where the VM does not maintain an MMTk-visible Java space.   However,
   * when the VM does maintain a space (such as a boot image) which is visible to MMTk,
   * that space could either be scanned by MMTk as part of its transitive closure over
   * the whole heap, or as a (considerable) performance optimization, MMTk could avoid
   * scanning the space if it is aware of all pointers out of that space.  This method
   * is used to establish the root set out of the scannable space in the case where
   * such a space exists.
   *  
   * @param trace The trace object to use to report root locations.
   */
  public abstract void computeBootImageRoots(TraceLocal trace);
}
