/*
 * (C) Copyright Department of Computer Science,
 *     Australian National University. 2002
 */
package org.mmtk.policy;

import org.mmtk.plan.TraceLocal;
import org.mmtk.utility.heap.*;
import org.mmtk.utility.Constants;

import org.mmtk.vm.VM;

import org.vmmagic.unboxed.*;
import org.vmmagic.pragma.*;

/**
 * This class implements functionality for a simple sliding mark-compact
 * space.
 * 
 * $Id$
 * 
 * @author Daniel Frampton
 * @version $Revision$
 * @date $Date$
 */
public final class MarkCompactSpace extends Space
  implements Constants, Uninterruptible {

  /****************************************************************************
   * 
   * Class variables
   */
  public static final int LOCAL_GC_BITS_REQUIRED = 1;
  public static final int GLOBAL_GC_BITS_REQUIRED = 0;
  public static final int GC_HEADER_WORDS_REQUIRED = 1;

  private static final Word GC_MARK_BIT_MASK = Word.one();
  private static final Offset FORWARDING_POINTER_OFFSET = VM.objectModel.GC_HEADER_OFFSET();

  /****************************************************************************
   * 
   * Instance variables
   */

  /****************************************************************************
   * 
   * Initialization
   */

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
  public MarkCompactSpace(String name, int pageBudget, float frac) {
    super(name, true, false, frac);
    pr = new MonotonePageResource(pageBudget, this, start, extent);
  }

  /**
   * Prepare for a collection
   */
  public void prepare() {
    // nothing to do
  }

  /**
   * Release after a collection
   */
  public void release() {
    // nothing to do
  }

  
  /**
   * Notify that several pages are no longer in use.
   * 
   * @param pages The number of pages
   */
  public void unusePages(int pages) {
    ((MonotonePageResource) pr).unusePages(pages);
  }

  /**
   * Notify that several pages are no longer in use.
   * 
   * @param pages The number of pages
   */
  public void reusePages(int pages) {
    ((MonotonePageResource) pr).reusePages(pages);
  }

  /**
   * Release an allocated page or pages.  In this case we do nothing
   * because we only release pages enmasse.
   * 
   * @param start The address of the start of the page or pages
   */
  public final void release(Address start) throws InlinePragma {
    if (VM.VERIFY_ASSERTIONS)
      VM.assertions._assert(false); // this policy only releases pages enmasse
  }

  /**
   * Trace an object under a copying collection policy.
   * If the object is already copied, the copy is returned.
   * Otherwise, a copy is created and returned.
   * In either case, the object will be marked on return.
   *
   * @param trace The trace being conducted.
   * @param object The object to be forwarded.
   * @return The forwarded object.
   */
  public ObjectReference traceObject(TraceLocal trace, ObjectReference object)
      throws InlinePragma {
    if (VM.VERIFY_ASSERTIONS)
      VM.assertions._assert(false);
    return null;
  }

  /**
   * Trace an object under a copying collection policy.
   * If the object is already copied, the copy is returned.
   * Otherwise, a copy is created and returned.
   * In either case, the object will be marked on return.
   *
   * @param trace The trace being conducted.
   * @param object The object to be forwarded.
   * @return The forwarded object.
   */
  public ObjectReference traceMarkObject(TraceLocal trace, ObjectReference object)
    throws InlinePragma {
    if (testAndMark(object)) {
      trace.enqueue(object);
    } else if (!getForwardingPointer(object).isNull()) {
      return getForwardingPointer(object);
    }
    return object;
  }

  /**
   * Trace an object under a copying collection policy.
   * If the object is already copied, the copy is returned.
   * Otherwise, a copy is created and returned.
   * In either case, the object will be marked on return.
   *
   * @param trace The trace being conducted.
   * @param object The object to be forwarded.
   * @return The forwarded object.
   */
  public ObjectReference traceForwardObject(TraceLocal trace, ObjectReference object)
    throws InlinePragma {
    if (testAndClearMark(object)) {
      trace.enqueue(object);
    }
    ObjectReference newObject = getForwardingPointer(object);
    if (VM.VERIFY_ASSERTIONS) VM.assertions._assert(!newObject.isNull());
    return getForwardingPointer(object);
  }

  /**
   * Is this object live?
   * 
   * @param object The object
   * @return True if the object is live
   */
  public final boolean isLive(ObjectReference object) {
    return isMarked(object);
  }

  /**
   * Has the object in this space been reached during the current collection.
   * This is used for GC Tracing.
   * 
   * @param object The object reference.
   * @return True if the object is reachable.
   */
  public final boolean isReachable(ObjectReference object) {
    return isMarked(object);
  }


  /****************************************************************************
   * 
   * Header manipulation
   */

  /**
   * Perform any required post-allocation initialization
   * 
   * <i>Nothing to be done in this case</i>
   * 
   * @param object the object ref to the storage to be initialized
   */
  public final void postAlloc(ObjectReference object) throws InlinePragma {
  }

  /**
   * Non-atomic read of forwarding pointer
   * 
   * @param object The object whose forwarding pointer is to be read
   * @return The forwarding pointer stored in <code>object</code>'s
   * header.
   */
  public static ObjectReference getForwardingPointer(ObjectReference object)
      throws InlinePragma {
    return object.toAddress().loadObjectReference(FORWARDING_POINTER_OFFSET);
  }

  /**
   * Initialise the header of the object.
   * 
   * @param object The object to initialise
   */
  public void initializeHeader(ObjectReference object)
    throws InlinePragma {
    // nothing to do
  }

  /**
   * Used to mark boot image objects during a parallel scan of objects
   * during GC Returns true if marking was done.
   * 
   * @param object The object to be marked
   */
  public static boolean testAndMark(ObjectReference object) 
    throws InlinePragma {
    Word oldValue;
    do {
      oldValue = VM.objectModel.prepareAvailableBits(object);
      Word markBit = oldValue.and(GC_MARK_BIT_MASK);
      if (!markBit.isZero()) return false;
    } while (!VM.objectModel.attemptAvailableBits(object, oldValue, 
                                                oldValue.or(GC_MARK_BIT_MASK)));
    return true;
  }

  /**
   * Used to mark boot image objects during a parallel scan of objects
   * during GC Returns true if marking was done.
   * 
   * @param object The object to be marked
   */
  public static boolean isMarked(ObjectReference object) 
    throws InlinePragma {
    Word oldValue = VM.objectModel.readAvailableBitsWord(object);
    Word markBit = oldValue.and(GC_MARK_BIT_MASK);
    return (!markBit.isZero());
  }

  /**
   * Used to mark boot image objects during a parallel scan of objects
   * during GC Returns true if marking was done.
   * 
   * @param object The object to be marked
   */
  private static boolean testAndClearMark(ObjectReference object)
      throws InlinePragma {
    Word oldValue;
    do {
      oldValue = VM.objectModel.prepareAvailableBits(object);
      Word markBit = oldValue.and(GC_MARK_BIT_MASK);
      if (markBit.isZero()) return false;
    } while (!VM.objectModel.attemptAvailableBits(object, oldValue, 
                                                oldValue.and(GC_MARK_BIT_MASK.not())));
    return true;
  }

  
  /**
   * Used to mark boot image objects during a parallel scan of objects
   * during GC Returns true if marking was done.
   * 
   * @param object The object to be marked
   */
  public static boolean toBeCompacted(ObjectReference object)
      throws InlinePragma {
    Word oldValue = VM.objectModel.readAvailableBitsWord(object);
    Word markBit = oldValue.and(GC_MARK_BIT_MASK);
    return !markBit.isZero() && getForwardingPointer(object).isNull();
  }

  /**
   * Used to mark boot image objects during a parallel scan of objects
   * during GC Returns true if marking was done.
   * 
   * @param object The object to be marked
   */
  public static void clearMark(ObjectReference object) 
    throws InlinePragma {
    Word oldValue = VM.objectModel.readAvailableBitsWord(object);
    VM.objectModel.writeAvailableBitsWord(object, oldValue.and(GC_MARK_BIT_MASK.not()));
  }

  /**
   * Non-atomic write of forwarding pointer word (assumption, thread
   * doing the set has done attempt to forward and owns the right to
   * copy the object)
   * 
   * @param object The object whose forwarding pointer is to be set
   * @param ptr The forwarding pointer to be stored in the object's
   * forwarding word
   */
  public static void setForwardingPointer(ObjectReference object,
                                           ObjectReference ptr)
    throws InlinePragma {
    object.toAddress().store(ptr.toAddress(), FORWARDING_POINTER_OFFSET);
  }

  /**
   * Non-atomic clear of forwarding pointer word (assumption, thread
   * doing the set has done attempt to forward and owns the right to
   * copy the object)
   * 
   * @param object The object whose forwarding pointer is to be set
   */
  public static void clearForwardingPointer(ObjectReference object)
      throws InlinePragma {
    object.toAddress().store(Address.zero(), FORWARDING_POINTER_OFFSET);
  }
}
