/*
 * (C) Copyright Department of Computer Science,
 *     Australian National University. 2002
 */
package org.mmtk.policy;

import org.mmtk.utility.heap.*;
import org.mmtk.vm.Assert;
import org.mmtk.vm.Constants;
import org.mmtk.vm.ObjectModel;
import org.mmtk.vm.Plan;

import org.vmmagic.unboxed.*;
import org.vmmagic.pragma.*;

/**
 * This class implements tracing functionality for a simple copying
 * space.  Since no state needs to be held globally or locally, all
 * methods are static.
 *
 * $Id$
 *
 * @author Perry Cheng
 * @author <a href="http://cs.anu.edu.au/~Steve.Blackburn">Steve Blackburn</a>
 * @author David Bacon
 * @author Steve Fink
 * @author Dave Grove
 *
 * @version $Revision$
 * @date $Date$
 */
public final class CopySpace extends Space
  implements Constants, Uninterruptible {

  /****************************************************************************
   *
   * Class variables
   */
  public static final int LOCAL_GC_BITS_REQUIRED = 2;
  public static final int GLOBAL_GC_BITS_REQUIRED = 0;
  public static final int GC_HEADER_BYTES_REQUIRED = 0;

  private static final Word GC_MARK_BIT_MASK = Word.one();
  private static final Word GC_FORWARDED        = Word.one().lsh(1);  // ...10
  private static final Word GC_BEING_FORWARDED  = Word.one().lsh(2).sub(Word.one());  // ...11
  private static final Word GC_FORWARDING_MASK  = GC_FORWARDED.or(GC_BEING_FORWARDED);

  /****************************************************************************
   *
   * Instance variables
   */
  private boolean fromSpace = true;
  
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
   * @param fromSpace The does this instance start life as from-space
   * (or to-space)?
   */
  public CopySpace(String name, int pageBudget, Address start, Extent bytes,
                   boolean fromSpace) {
    super(name, true, false, start, bytes);
    this.fromSpace = fromSpace;
    pr = new MonotonePageResource(pageBudget, this, start, extent);
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
   * @param fromSpace The does this instance start life as from-space
   * (or to-space)?
   */
  public CopySpace(String name, int pageBudget, int mb, boolean fromSpace) {
    super(name, true, false, mb);
    this.fromSpace = fromSpace;
    pr = new MonotonePageResource(pageBudget, this, start, extent);
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
   * @param fromSpace The does this instance start life as from-space
   * (or to-space)?
   */
  public CopySpace(String name, int pageBudget, float frac, 
                   boolean fromSpace) {
    super(name, true, false, frac);
    this.fromSpace = fromSpace;
    pr = new MonotonePageResource(pageBudget, this, start, extent);
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
   * @param fromSpace The does this instance start life as from-space
   * (or to-space)?
   */
  public CopySpace(String name, int pageBudget, int mb, boolean top, 
                   boolean fromSpace) {
    super(name, true, false, mb, top);
    this.fromSpace = fromSpace;
    pr = new MonotonePageResource(pageBudget, this, start, extent);
  }
  
  /**
   * Construct a space that consumes a given fraction of the available
   * virtual memory, at either the top or bottom of the available
   * virtual memory.
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
   * @param fromSpace The does this instance start life as from-space
   * (or to-space)?
   */
  public CopySpace(String name, int pageBudget, float frac, boolean top,
                   boolean fromSpace) {
    super(name, true, false, frac, top);
    this.fromSpace = fromSpace;
    pr = new MonotonePageResource(pageBudget, this, start, extent);
  }
  
  public void prepare(boolean fromSpace) { this.fromSpace = fromSpace; }
  public void release() { ((MonotonePageResource) pr).reset(); }

  /**
   * Release an allocated page or pages.  In this case we do nothing
   * because we only release pages enmasse.
   *
   * @param start The address of the start of the page or pages
   */
  public final void release(Address start) throws InlinePragma {
    Assert._assert(false);  // this policy only releases pages enmasse
  }

  /**
   * Trace an object under a copying collection policy.
   * If the object is already copied, the copy is returned.
   * Otherwise, a copy is created and returned.
   * In either case, the object will be marked on return.
   *
   * @param object The object to be traced.
   * @return The forwarded object.
   */
  public final ObjectReference traceObject(ObjectReference object) 
    throws InlinePragma {
    if (fromSpace)
    return forwardObject(object, true);
    else
      return object;
  }

  /**
   * Mark an object as having been traversed.
   *
   * @param object The object to be marked
   * @param markState The sense of the mark bit (flips from 0 to 1)
   */
  public static void markObject(ObjectReference object, Word markState) 
    throws InlinePragma {
    if (testAndMark(object, markState)) Plan.enqueue(object);
  }

  /**
   * Forward an object.
   *
   * @param object The object to be forwarded.
   * @return The forwarded object.
   */
  public static ObjectReference forwardObject(ObjectReference object) 
    throws InlinePragma {
    return forwardObject(object, false);
  }

  /**
   * Forward an object and enqueue it for scanning
   *
   * @param object The object to be forwarded.
   * @return The forwarded object.
   */
  public static ObjectReference forwardAndScanObject(ObjectReference object) 
    throws InlinePragma {
    return forwardObject(object, true);
  }

  /**
   * Forward an object.  If the object has not already been forwarded,
   * then conditionally enqueue it for scanning.
   *
   * @param object The object to be forwarded.
   * @param scan If <code>true</code>, then enqueue the object for
   * scanning if the object was previously unforwarded.
   * @return The forwarded object.
   */
  private static ObjectReference forwardObject(ObjectReference object,
                                               boolean scan) 
    throws InlinePragma {
    Word forwardingPtr = attemptToForward(object);

    // Somebody else got to it first.
    //
    if (stateIsForwardedOrBeingForwarded(forwardingPtr)) {
      while (stateIsBeingForwarded(forwardingPtr)) 
        forwardingPtr = getForwardingWord(object);
      ObjectReference newObject = forwardingPtr.and(GC_FORWARDING_MASK.not()).toAddress().toObjectReference();
      return newObject;
    }

    // We are the designated copier
    //
    ObjectReference newObject = ObjectModel.copy(object);
    setForwardingPointer(object, newObject);
    if (scan) {
      Plan.enqueue(newObject);       // Scan it later
    } else {
      Plan.enqueueForwardedUnscannedObject(newObject);
    }
    return newObject;
  }


  public final boolean isLive(ObjectReference object) {
    return isForwarded(object);
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
   public final void postAlloc(ObjectReference object) 
        throws InlinePragma {}
   
  /**
   * Clear the GC portion of the header for an object.
   * 
   * @param object the object ref to the storage to be initialized
   */
  public static void clearGCBits(ObjectReference object) throws InlinePragma {
    Word header = ObjectModel.readAvailableBitsWord(object);
    ObjectModel.writeAvailableBitsWord(object, header.and(GC_FORWARDING_MASK.not()));
  }
 
  /**
   * Has an object been forwarded?
   *
   * @param object The object to be checked
   * @return True if the object has been forwarded
   */
  public static boolean isForwarded(ObjectReference object)
    throws InlinePragma {
    return stateIsForwarded(getForwardingWord(object));
  }

  /**
   * Has an object been forwarded or being forwarded?
   *
   * @param object The object to be checked
   * @return True if the object has been forwarded or is being forwarded
   */
  public static boolean isForwardedOrBeingForwarded(ObjectReference object)
    throws InlinePragma {
    return stateIsForwardedOrBeingForwarded(getForwardingWord(object));
  }

  /**
   * Non-atomic read of forwarding pointer word
   *
   * @param object The object whose forwarding word is to be read
   * @return The forwarding word stored in <code>object</code>'s
   * header.
   */
  private static Word getForwardingWord(ObjectReference object)
    throws InlinePragma {
    return ObjectModel.readAvailableBitsWord(object);
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
    return getForwardingWord(object).and(GC_FORWARDING_MASK.not()).toAddress().toObjectReference();
  }

  /**
   * Used to mark boot image objects during a parallel scan of objects
   * during GC Returns true if marking was done.
   *
   * @param object The object to be marked
   * @param value The value to store in the mark bit
   */
  private static boolean testAndMark(ObjectReference object, Word value) 
    throws InlinePragma {
    Word oldValue;
    do {
      oldValue = ObjectModel.prepareAvailableBits(object);
      Word markBit = oldValue.and(GC_MARK_BIT_MASK);
      if (markBit.EQ(value)) return false;
    } while (!ObjectModel.attemptAvailableBits(object, oldValue, 
                                                oldValue.xor(GC_MARK_BIT_MASK)));
    return true;
  }

  /**
   * Either return the forwarding pointer if the object is already
   * forwarded (or being forwarded) or write the bit pattern that
   * indicates that the object is being forwarded
   *
   * @param object The object to be forwarded
   * @return The forwarding pointer for the object if it has already
   * been forwarded.
   */
  private static Word attemptToForward(ObjectReference object) 
    throws InlinePragma {
    Word oldValue;
    do {
      oldValue = ObjectModel.prepareAvailableBits(object);
      if (oldValue.and(GC_FORWARDING_MASK).EQ(GC_FORWARDED)) return oldValue;
    } while (!ObjectModel.attemptAvailableBits(object, oldValue,
                                                oldValue.or(GC_BEING_FORWARDED)));
    return oldValue;
  }

  /**
   * Is the state of the forwarding word being forwarded?
   *
   * @param fword A forwarding word.
   * @return True if the forwarding word's state is being forwarded.
   */
  private static boolean stateIsBeingForwarded(Word fword)
    throws InlinePragma {
    return  fword.and(GC_FORWARDING_MASK).EQ(GC_BEING_FORWARDED);
  }
  
  /**
   * Is the state of the forwarding word forwarded?
   *
   * @param fword A forwarding word.
   * @return True if the forwarding word's state is forwarded.
   */
  private static boolean stateIsForwarded(Word fword)
    throws InlinePragma {
    return  fword.and(GC_FORWARDING_MASK).EQ(GC_FORWARDED);
  }
  
  /**
   * Is the state of the forwarding word forwarded or being forwarded?
   *
   * @param fword A forwarding word.
   * @return True if the forwarding word's state is forwarded or being
   * forwarded.
   */
  public static boolean stateIsForwardedOrBeingForwarded(Word fword)
    throws InlinePragma {
    return !(fword.and(GC_FORWARDED).isZero());
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
  private static void setForwardingPointer(ObjectReference object, 
                                           ObjectReference ptr)
    throws InlinePragma {
    ObjectModel.writeAvailableBitsWord(object, ptr.toAddress().toWord().or(GC_FORWARDED));
  }
}
