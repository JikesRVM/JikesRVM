/*
 * (C) Copyright Department of Computer Science,
 *     Australian National University. 2002
 */

package org.mmtk.policy;

import org.mmtk.plan.Plan;
import org.mmtk.utility.heap.*;
import org.mmtk.vm.VM_Interface;
import org.mmtk.vm.Constants;

import com.ibm.JikesRVM.VM_Uninterruptible;
import com.ibm.JikesRVM.VM_PragmaInline;
import com.ibm.JikesRVM.VM_Address;
import com.ibm.JikesRVM.VM_Word;
import com.ibm.JikesRVM.VM_Magic;


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
public final class CopySpace extends BasePolicy 
  implements Constants, VM_Uninterruptible {

  /****************************************************************************
   *
   * Class variables
   */
  public static final int LOCAL_GC_BITS_REQUIRED = 2;
  public static final int GLOBAL_GC_BITS_REQUIRED = 0;
  public static final int GC_HEADER_BYTES_REQUIRED = 0;

  private static final VM_Word GC_MARK_BIT_MASK = VM_Word.one();
  private static final VM_Word GC_FORWARDED        = VM_Word.one().lsh(1);  // ...10
  private static final VM_Word GC_BEING_FORWARDED  = VM_Word.one().lsh(2).sub(VM_Word.one());  // ...11
  private static final VM_Word GC_FORWARDING_MASK  = GC_FORWARDED.or(GC_BEING_FORWARDED);

  public static void prepare(VMResource vm, MemoryResource mr) { }
  public static void release(VMResource vm, MemoryResource mr) { }

  /**
   * Trace an object under a copying collection policy.
   * If the object is already copied, the copy is returned.
   * Otherwise, a copy is created and returned.
   * In either case, the object will be marked on return.
   *
   * @param object The object to be traced.
   * @return The forwarded object.
   */
  public static VM_Address traceObject(VM_Address object) 
    throws VM_PragmaInline {
    return forwardObject(object, true);
  }

  /**
   * Mark an object as having been traversed.
   *
   * @param object The object to be marked
   * @param markState The sense of the mark bit (flips from 0 to 1)
   */
  public static void markObject(VM_Address object, VM_Word markState) 
    throws VM_PragmaInline {
    if (testAndMark(object, markState)) 
      VM_Interface.getPlan().enqueue(object);
  }

  /**
   * Forward an object.
   *
   * @param object The object to be forwarded.
   * @return The forwarded object.
   */
  public static VM_Address forwardObject(VM_Address object) 
    throws VM_PragmaInline {
    return forwardObject(object, false);
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
  private static VM_Address forwardObject(VM_Address object, boolean scan) 
    throws VM_PragmaInline {
    VM_Word forwardingPtr = attemptToForward(object);

    // Somebody else got to it first.
    //
    if (stateIsForwardedOrBeingForwarded(forwardingPtr)) {
      while (stateIsBeingForwarded(forwardingPtr)) 
        forwardingPtr = getForwardingWord(object);
      VM_Address newObject = forwardingPtr.and(GC_FORWARDING_MASK.not()).toAddress();
      return newObject;
    }

    // We are the designated copier
    //
    VM_Address newObject = VM_Interface.copy(object);
    setForwardingPointer(object, newObject);
    if (scan) {
      Plan.enqueue(newObject);       // Scan it later
    } else {
      Plan.enqueueForwardedUnscannedObject(newObject);
    }

    return newObject;
  }


  public static boolean isLive(VM_Address obj) {
    return isForwarded(obj);
  }


  /****************************************************************************
   *
   * Header manipulation
   */

  /**
   * Clear the GC portion of the header for an object.
   * 
   * @param object the object ref to the storage to be initialized
   */
  public static void clearGCBits(VM_Address object) throws VM_PragmaInline {
    VM_Word header = VM_Interface.readAvailableBitsWord(object);
    VM_Interface.writeAvailableBitsWord(object, header.and(GC_FORWARDING_MASK.not()));
  }
 
  /**
   * Has an object been forwarded?
   *
   * @param object The object to be checked
   * @return True if the object has been forwarded
   */
  public static boolean isForwarded(VM_Address object) throws VM_PragmaInline {
    return stateIsForwarded(getForwardingWord(object));
  }

  /**
   * Has an object been forwarded or being forwarded?
   *
   * @param object The object to be checked
   * @return True if the object has been forwarded or is being forwarded
   */
  public static boolean isForwardedOrBeingForwarded(VM_Address object)
    throws VM_PragmaInline {
    return stateIsForwardedOrBeingForwarded(getForwardingWord(object));
  }

  /**
   * Non-atomic read of forwarding pointer word
   *
   * @param object The object whose forwarding word is to be read
   * @return The forwarding word stored in <code>object</code>'s
   * header.
   */
  private static VM_Word getForwardingWord(VM_Address object)
    throws VM_PragmaInline {
    return VM_Interface.readAvailableBitsWord(object);
  }

 /**
   * Non-atomic read of forwarding pointer
   *
   * @param object The object whose forwarding pointer is to be read
   * @return The forwarding pointer stored in <code>object</code>'s
   * header.
   */
  public static VM_Address getForwardingPointer(VM_Address object) 
    throws VM_PragmaInline {
    return getForwardingWord(object).and(GC_FORWARDING_MASK.not()).toAddress();
  }

  /**
   * Used to mark boot image objects during a parallel scan of objects
   * during GC Returns true if marking was done.
   *
   * @param object The object to be marked
   * @param value The value to store in the mark bit
   */
  private static boolean testAndMark(VM_Address object, VM_Word value) 
    throws VM_PragmaInline {
    VM_Word oldValue;
    do {
      oldValue = VM_Interface.prepareAvailableBits(object);
      VM_Word markBit = oldValue.and(GC_MARK_BIT_MASK);
      if (markBit.EQ(value)) return false;
    } while (!VM_Interface.attemptAvailableBits(object, oldValue, 
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
  private static VM_Word attemptToForward(VM_Address object) 
    throws VM_PragmaInline {
    VM_Word oldValue;
    do {
      oldValue = VM_Interface.prepareAvailableBits(object);
      if (oldValue.and(GC_FORWARDING_MASK).EQ(GC_FORWARDED)) return oldValue;
    } while (!VM_Interface.attemptAvailableBits(object, oldValue,
                                                oldValue.or(GC_BEING_FORWARDED)));
    return oldValue;
  }

  /**
   * Is the state of the forwarding word being forwarded?
   *
   * @param fword A forwarding word.
   * @return True if the forwarding word's state is being forwarded.
   */
  private static boolean stateIsBeingForwarded(VM_Word fword)
    throws VM_PragmaInline {
    return  fword.and(GC_FORWARDING_MASK).EQ(GC_BEING_FORWARDED);
  }
  
  /**
   * Is the state of the forwarding word forwarded?
   *
   * @param fword A forwarding word.
   * @return True if the forwarding word's state is forwarded.
   */
  private static boolean stateIsForwarded(VM_Word fword)
    throws VM_PragmaInline {
    return  fword.and(GC_FORWARDING_MASK).EQ(GC_FORWARDED);
  }
  
  /**
   * Is the state of the forwarding word forwarded or being forwarded?
   *
   * @param fword A forwarding word.
   * @return True if the forwarding word's state is forwarded or being
   * forwarded.
   */
  public static boolean stateIsForwardedOrBeingForwarded(VM_Word fword)
    throws VM_PragmaInline {
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
  private static void setForwardingPointer(VM_Address object, VM_Address ptr)
    throws VM_PragmaInline {
    VM_Interface.writeAvailableBitsWord(object, ptr.toWord().or(GC_FORWARDED));
  }
}
