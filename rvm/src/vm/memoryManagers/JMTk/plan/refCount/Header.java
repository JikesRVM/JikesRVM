/*
 * (C) Copyright IBM Corp. 2001
 */
//$Id$

package org.mmtk.plan;


import com.ibm.JikesRVM.VM_Address;
import com.ibm.JikesRVM.VM_Word;
import com.ibm.JikesRVM.VM_Magic;
import com.ibm.JikesRVM.VM_PragmaInline;
import com.ibm.JikesRVM.VM_PragmaNoInline;
import com.ibm.JikesRVM.VM_PragmaUninterruptible;
import com.ibm.JikesRVM.VM_PragmaLogicallyUninterruptible;

/**
 * Chooses the appropriate collector-specific header model.
 *
 * @see VM_ObjectModel
 * 
 * @author <a href="http://cs.anu.edu.au/~Steve.Blackburn">Steve Blackburn</a>
 * @author Ian Warrington
 * @version $Revision$
 * @date $Date$
 */
import org.mmtk.vm.VM_Interface;
public class Header extends RCHeader {
  public final static String Id = "$Id$"; 

  // Merges all the headers together.  In this case, we have only one.
  public final static VM_Word GC_BARRIER_BIT_MASK = VM_Word.fromIntSignExtend(-1); // must be defined even though unused

  /* Mask bits to signify the start/finish of logging an object */
  public static final VM_Word LOGGING_MASK = VM_Word.one().lsh(2).sub(VM_Word.one()); //...00011
  public static final int      LOG_BIT = 0;
  public static final VM_Word       LOGGED = VM_Word.zero();
  public static final VM_Word     UNLOGGED = VM_Word.one();
  public static final VM_Word BEING_LOGGED = VM_Word.one().lsh(2).sub(VM_Word.one()); //...00011

  /****************************************************************************
   *
   * Empty public methods existing only for compliance
   */
  public static boolean isBeingForwarded(VM_Address base) 
    throws VM_PragmaUninterruptible, VM_PragmaInline {
    if (VM_Interface.VerifyAssertions)
      VM_Interface._assert(false);
    return false;
  }

  public static boolean isForwarded(VM_Address base) 
    throws VM_PragmaUninterruptible, VM_PragmaInline {
    if (VM_Interface.VerifyAssertions)
      VM_Interface._assert(false);
    return false;
  }

  static void setBarrierBit(VM_Address ref)
    throws VM_PragmaUninterruptible, VM_PragmaInline {
    if (VM_Interface.VerifyAssertions)
      VM_Interface._assert(false);
  }

  /****************************************************************************
   *
   * Object Logging Methods
   */

  /**
   * Return true if <code>object</code> is yet to be logged (for
   * coalescing RC).
   *
   * @param object The object in question
   * @return <code>true</code> if <code>object</code> needs to be logged.
   */
  static boolean logRequired(VM_Address object)
    throws VM_PragmaUninterruptible, VM_PragmaInline {
    VM_Word value = VM_Interface.readAvailableBitsWord(object);
    return value.and(LOGGING_MASK).EQ(UNLOGGED);
  }

  /**
   * Attempt to log <code>object</code> for coalescing RC. This is
   * used to handle a race to log the object, and returns
   * <code>true</code> if we are to log the object and
   * <code>false</code> if we lost the race to log the object.
   *
   * <p>If this method returns <code>true</code>, it leaves the object
   * in the <code>BEING_LOGGED</code> state.  It is the responsibility
   * of the caller to change the object to <code>LOGGED</code> once
   * the logging is complete.
   *
   * @see makeLogged
   * @param object The object in question
   * @return <code>true</code> if the race to log
   * <code>object</code>was won.
   */
  static boolean attemptToLog(VM_Address object)
    throws VM_PragmaUninterruptible, VM_PragmaInline {
    VM_Word oldValue;
    do {
      oldValue = VM_Interface.prepareAvailableBits(object);
      if (oldValue.and(LOGGING_MASK).EQ(LOGGED)) return false;
    } while ((oldValue.and(LOGGING_MASK).EQ(BEING_LOGGED)) ||
             !VM_Interface.attemptAvailableBits(object, oldValue, 
                                                oldValue.or(BEING_LOGGED)));
    if (VM_Interface.VerifyAssertions) {
      VM_Word value = VM_Interface.readAvailableBitsWord(object);
      VM_Interface._assert(value.and(LOGGING_MASK).EQ(BEING_LOGGED));
    }
    return true;
  }

  /**
   * Signify completion of logging <code>object</code>.
   *
   * <code>object</code> is left in the <code>LOGGED</code> state.
   *
   * @see attemptToLog
   * @param object The object whose state is to be changed.
   */
  static void makeLogged(VM_Address object)
    throws VM_PragmaUninterruptible, VM_PragmaInline {
    VM_Word value = VM_Interface.readAvailableBitsWord(object);
    if (VM_Interface.VerifyAssertions)
      VM_Interface._assert(value.and(LOGGING_MASK).NE(LOGGED));
    VM_Interface.writeAvailableBitsWord(object, value.and(LOGGING_MASK.not()));
  }

  /**
   * Change <code>object</code>'s state to <code>UNLOGGED</code>.
   *
   * @param object The object whose state is to be changed.
   */
  static void makeUnlogged(VM_Address object)
    throws VM_PragmaUninterruptible, VM_PragmaInline {
    VM_Word value = VM_Interface.readAvailableBitsWord(object);
    if (VM_Interface.VerifyAssertions)
      VM_Interface._assert(value.and(LOGGING_MASK).EQ(LOGGED));
    VM_Interface.writeAvailableBitsWord(object, value.or(UNLOGGED));
  }

  /**
   * Perform any required initialization of the GC portion of the header.
   * Called for objects created at boot time.
   * 
   * @param ref the object ref to the storage to be initialized
   * @param tib the TIB of the instance being created
   * @param size the number of bytes allocated by the GC system for
   * this object.
   * @param isScalar are we initializing a scalar (true) or array
   * (false) object?
   */
  public static VM_Word getBootTimeAvailableBits(int ref, Object[] tib,
                                                 int size, boolean isScalar,
                                                 VM_Word status)
    throws VM_PragmaUninterruptible, VM_PragmaInline {
    if (Plan.WITH_COALESCING_RC) status = status.or(UNLOGGED);
    return status;
  }  

}
