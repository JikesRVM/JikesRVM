/*
 * (C) Copyright Department of Computer Science,
 * Australian National University. 2002
 */
package org.mmtk.plan.generational.copying;

import org.mmtk.policy.CopySpace;
import org.mmtk.policy.Space;
import org.mmtk.plan.generational.*;
import org.mmtk.plan.Trace;

import org.vmmagic.pragma.*;

/**
 * This class implements the functionality of a standard
 * two-generation copying collector.  Nursery collections occur when
 * either the heap is full or the nursery is full.  The nursery size
 * is determined by an optional command line argument.  If undefined,
 * the nursery size is "infinite", so nursery collections only occur
 * when the heap is full (this is known as a flexible-sized nursery
 * collector).  Thus both fixed and flexible nursery sizes are
 * supported.  Full heap collections occur when the nursery size has
 * dropped to a statically defined threshold,
 * <code>NURSERY_THRESHOLD</code><p>
 *
 * See the Jones & Lins GC book, chapter 7 for a detailed discussion
 * of generational collection and section 7.3 for an overview of the
 * flexible nursery behavior ("The Standard ML of New Jersey
 * collector"), or go to Appel's paper "Simple generational garbage
 * collection and fast allocation." SP&E 19(2):171--183, 1989.<p>
 *
 * All plans make a clear distinction between <i>global</i> and
 * <i>thread-local</i> activities.  Global activities must be
 * synchronized, whereas no synchronization is required for
 * thread-local activities.  Instances of Plan map 1:1 to "kernel
 * threads" (aka CPUs or in Jikes RVM, VM_Processors).  Thus instance
 * methods allow fast, unsychronized access to Plan utilities such as
 * allocation and collection.  Each instance rests on static resources
 * (such as memory and virtual memory resources) which are "global"
 * and therefore "static" members of Plan.  This mapping of threads to
 * instances is crucial to understanding the correctness and
 * performance proprties of this plan.
 *
 * $Id$
 *
 * @author <a href="http://cs.anu.edu.au/~Steve.Blackburn">Steve Blackburn</a>
 * @author Daniel Frampton
 * @author Robin Garner
 * @version $Revision$
 * @date $Date$
 */
public class GenCopy extends Gen implements Uninterruptible {

  /****************************************************************************
   *
   * Class variables
   */

  // GC state
  static boolean hi = false; // True if copying to "higher" semispace 

  /**
   * The low half of the copying mature space.  We allocate into this space
   * when <code>hi</code> is <code>false</code>.
   */
  static CopySpace matureSpace0 = new CopySpace("ss0", DEFAULT_POLL_FREQUENCY, (float) 0.25, false);
  static final int MS0 = matureSpace0.getDescriptor();
  
  /**
   * The high half of the copying mature space. We allocate into this space
   * when <code>hi</code> is <code>true</code>.
   */
  static CopySpace matureSpace1 = new CopySpace("ss1", DEFAULT_POLL_FREQUENCY, (float) 0.25, true);
  static final int MS1 = matureSpace1.getDescriptor();


  /****************************************************************************
   * 
   * Instance fields
   */
  final Trace matureTrace;
  
  /**
   * Constructor
   */
  public GenCopy() {
    super();
    matureTrace = new Trace(metaDataSpace);
  }
  
  /**
   * @return Does the mature space do copying ?
   */
  protected boolean copyMature() {
    return true;
  }

  /** 
   * @return The semispace we are currently allocating into 
   */
  static final CopySpace toSpace() { 
    return hi ? matureSpace1 : matureSpace0; 
  }
  
  /**
   * @return Space descriptor for to-space.
   */
  static final int toSpaceDesc() { return hi ? MS1 : MS0; }
  
  /** 
   * @return The semispace we are currently copying from 
   * (or copied from at last major GC) 
   */
  static final CopySpace fromSpace() { 
    return hi ? matureSpace0 : matureSpace1; 
  }
  
  /**
   * @return Space descriptor for from-space
   */
  static final int fromSpaceDesc() { return hi ? MS0 : MS1; }
  
  /****************************************************************************
   *
   * Collection
   */

  /**
   * Perform a phase of the currently active collection.
   * 
   * @param phaseId Collection phase to process
   */
  public void collectionPhase(int phaseId) throws InlinePragma {
    if (collectMatureSpace()) {
      if (phaseId == PREPARE) {
        super.collectionPhase(phaseId);
        hi = !hi;         // flip the semi-spaces
        matureSpace0.prepare(hi);
        matureSpace1.prepare(!hi);
        matureTrace.prepare();
        return;
      }
      if (phaseId == RELEASE) {
        matureTrace.release();
        fromSpace().release();
        super.collectionPhase(phaseId);
        return;
      }
    }
    super.collectionPhase(phaseId);
  }
  
  /*****************************************************************************
   * 
   * Accounting
   */
 
  /**
   * Return the number of pages reserved for use given the pending
   * allocation.
   *
   * @return The number of pages reserved given the pending
   * allocation, excluding space reserved for copying.
   */
  public int getPagesUsed() throws InlinePragma {
    return toSpace().reservedPages() + super.getPagesUsed();
  }

  /**
   * Return the number of pages reserved for copying.  
   *
   * @return the number of pages reserved for copying.  
   */
  public final int getCopyReserve() {
    // we must account for the number of pages required for copying,
    // which equals the number of semi-space pages reserved
    return toSpace().reservedPages() + super.getCopyReserve();
  }

  /**************************************************************************
   * Miscellaneous methods
   */
  
  /**
   * @return The mature space we are currently allocating into
   */
  public Space activeMatureSpace() throws InlinePragma { 
    return toSpace(); 
  }
}
