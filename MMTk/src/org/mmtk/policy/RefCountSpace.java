/*
 * (C) Copyright Department of Computer Science,
 * Australian National University. 2003
 */
package org.mmtk.policy;

import org.mmtk.plan.Plan;
import org.mmtk.utility.Conversions;
import org.mmtk.utility.alloc.BlockAllocator;
import org.mmtk.utility.alloc.EmbeddedMetaData;
import org.mmtk.utility.heap.MemoryResource;
import org.mmtk.utility.heap.FreeListVMResource;
import org.mmtk.utility.Log;
import org.mmtk.vm.Constants;
import org.mmtk.vm.VM_Interface;

import com.ibm.JikesRVM.VM_Address;
import com.ibm.JikesRVM.VM_Extent;
import com.ibm.JikesRVM.VM_Word;
import com.ibm.JikesRVM.VM_Magic;
import com.ibm.JikesRVM.VM_Uninterruptible;
import com.ibm.JikesRVM.VM_PragmaUninterruptible;
import com.ibm.JikesRVM.VM_PragmaInterruptible;
import com.ibm.JikesRVM.VM_PragmaLogicallyUninterruptible;
import com.ibm.JikesRVM.VM_PragmaInline;
import com.ibm.JikesRVM.VM_PragmaNoInline;

/**
 * Each instance of this class corresponds to one reference counted
 * *space*.  In other words, it maintains and performs actions with
 * respect to state that is global to a given reference counted space.
 * Each of the instance methods of this class may be called by any
 * thread (i.e. synchronization must be explicit in any instance or
 * class method).  This contrasts with the RefCountLocal, where
 * instances correspond to *plan* instances and therefore to kernel
 * threads.  Thus unlike this class, synchronization is not necessary
 * in the instance methods of RefCountLocal.
 *
 * @author <a href="http://cs.anu.edu.au/~Steve.Blackburn">Steve Blackburn</a>
 * @author Ian Warrington
 * @version $Revision$
 * @date $Date$
 */
public final class RefCountSpace implements Constants, VM_Uninterruptible {
  public final static String Id = "$Id$"; 

  /****************************************************************************
   *
   * Class variables
   */
  public static final boolean INC_DEC_ROOT = false;
  public static final boolean RC_SANITY_CHECK = false;
  
  public static final int LOCAL_GC_BITS_REQUIRED = 2;
  public static final int GLOBAL_GC_BITS_REQUIRED = 0; 
  /** How many bytes are used by all GC header fields? */
  public static final int GC_HEADER_BYTES_REQUIRED = (RC_SANITY_CHECK) ? 2*BYTES_IN_ADDRESS : BYTES_IN_ADDRESS;
  protected static final int RC_HEADER_OFFSET = VM_Interface.GC_HEADER_OFFSET();
  protected static final int RC_SANITY_HEADER_OFFSET = VM_Interface.GC_HEADER_OFFSET() + BYTES_IN_ADDRESS;


  /* Mask bits to signify the start/finish of logging an object */
  private static final VM_Word LOGGING_MASK = VM_Word.one().lsh(2).sub(VM_Word.one()); //...00011
  private static final int      LOG_BIT = 0;
  private static final VM_Word       LOGGED = VM_Word.zero();
  public static final VM_Word     UNLOGGED = VM_Word.one();
  private static final VM_Word BEING_LOGGED = VM_Word.one().lsh(2).sub(VM_Word.one()); //...00011

  public static final int DEC_KILL = 0;    // dec to zero RC --> reclaim obj
  public static final int DEC_PURPLE = 1;  // dec to non-zero RC, already buf'd
  public static final int DEC_BUFFER = -1; // dec to non-zero RC, need to bufr

  // See Bacon & Rajan ECOOP 2001 for notion of colors (purple, grey,
  // black, green).  See also Jones & Lins for a description of "Lins'
  // algorithm", on which Bacon & Rajan's is based.

  // The following are arranged to try to make the most common tests
  // fastest ("bufferd?", "green?" and "(green | purple)?") 
  private static final int     BUFFERED_MASK = 0x1;  //  .. xx0001
  private static final int        COLOR_MASK = 0x1e;  //  .. x11110 
  private static final int     LO_COLOR_MASK = 0x6;  //  .. x00110 
  private static final int     HI_COLOR_MASK = 0x18; //  .. x11000 
  private static final int             BLACK = 0x0;  //  .. xxxx0x
  private static final int              GREY = 0x2;  //  .. xxxx1x
  private static final int             WHITE = 0x4;  //  .. xx010x
  // green & purple *MUST* remain the highest colors in order to
  // preserve the (green | purple) test's precondition.
  private static final int            PURPLE = 0x8;  //  .. x01xxx
  protected static final int           GREEN = 0x10;  // .. x10xxx

  // bits used to ensure retention of objects with zero RC
  private static final int       FINALIZABLE = 0x20; //  .. 100000
  private static final int    ROOT_REACHABLE = 0x40; //  .. x10000
  private static final int    HARD_THRESHOLD = ROOT_REACHABLE;
  private static final int    LIVE_THRESHOLD = FINALIZABLE;
  private static final int         BITS_USED = 7;

  protected static final int INCREMENT_SHIFT = BITS_USED;
  protected static final int INCREMENT = 1<<INCREMENT_SHIFT;
  protected static final int AVAILABLE_BITS = BITS_IN_ADDRESS - BITS_USED;
  protected static final int INCREMENT_LIMIT = ~(1<<(BITS_IN_ADDRESS-1));

  /****************************************************************************
   *
   * Instance variables
   */
  private FreeListVMResource vmResource;
  private MemoryResource memoryResource;
  public boolean bootImageMark = false;

  /****************************************************************************
   *
   * Initialization
   */

  /**
   * Constructor
   *
   * @param vmr The virtual memory resource through which allocations
   * for this collector will go.
   * @param mr The memory resource against which allocations
   * associated with this collector will be accounted.
   */
  public RefCountSpace(FreeListVMResource vmr, MemoryResource mr) {
    vmResource = vmr;
    memoryResource = mr;
  }

  /****************************************************************************
   *
   * Allocation
   */

  /****************************************************************************
   *
   * Collection
   */

  /**
   * Prepare for a new collection increment.  Nothing to do.
   */
  public void prepare() {}

  /**
   * A new collection increment has completed.
   */
  public void release() {
  }

  /****************************************************************************
   *
   * Object processing and tracing
   */

  /**
   * An object has been encountered in a traversal of the object
   * graph.  If this reference is from a root, perform an increment
   * and add the object to the root set.
   *
   * @param object The object encountered in the trace
   * @param root True if the object is referenced directly from a root
   */
  public final VM_Address traceObject(VM_Address object)
    throws VM_PragmaInline {
    if (INC_DEC_ROOT) {
      incRC(object);
      VM_Interface.getPlan().addToRootSet(object);
    } else if (setRoot(object)) {
      VM_Interface.getPlan().addToRootSet(object);
    }
    return object;
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
  public static boolean logRequired(VM_Address object)
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
  public static boolean attemptToLog(VM_Address object)
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
  public static void makeLogged(VM_Address object)
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
  public static void makeUnlogged(VM_Address object)
    throws VM_PragmaUninterruptible, VM_PragmaInline {
    VM_Word value = VM_Interface.readAvailableBitsWord(object);
    if (VM_Interface.VerifyAssertions)
      VM_Interface._assert(value.and(LOGGING_MASK).EQ(LOGGED));
    VM_Interface.writeAvailableBitsWord(object, value.or(UNLOGGED));
  }

  /****************************************************************************
   *
   * Header manipulation
   */

  /**
   * Perform any required initialization of the GC portion of the header.
   * 
   * @param object the object ref to the storage to be initialized
   * @param tib the TIB of the instance being created
   * @param initialInc  do we want to initialize this header with an
   * initial increment?
   */
  public static void initializeHeader(VM_Address object, Object[] tib,
				      boolean initialInc)
    throws VM_PragmaInline {
    // all objects are birthed with an RC of INCREMENT
    int initialValue =  (initialInc) ? INCREMENT : 0;
    if (Plan.REF_COUNT_CYCLE_DETECTION && VM_Interface.isAcyclic(tib))
      initialValue |= GREEN;
    VM_Magic.setIntAtOffset(object, RC_HEADER_OFFSET, initialValue);
  }

  /**
   * Return true if given object is live
   *
   * @param object The object whose liveness is to be tested
   * @return True if the object is alive
   */
  public static boolean isLiveRC(VM_Address object) 
    throws VM_PragmaUninterruptible, VM_PragmaInline {
    return VM_Magic.getIntAtOffset(object, RC_HEADER_OFFSET) >= LIVE_THRESHOLD;
  }

  /**
   * Return true if given object is unreachable from roots or other
   * objects (i.e. ignoring the finalizer list).  Mark the object as a
   * finalizer object.
   *
   * @param object The object whose finalizability is to be tested
   * @return True if the object is finalizable
   */
  public static boolean isFinalizable(VM_Address object) 
    throws VM_PragmaUninterruptible, VM_PragmaInline {
    setFinalizer(object);
    return VM_Magic.getIntAtOffset(object, RC_HEADER_OFFSET) < HARD_THRESHOLD;
  }

  public static void incRCOOL(VM_Address object) 
    throws VM_PragmaUninterruptible, VM_PragmaNoInline {
    incRC(object);
  }


  /**
   * Increment the reference count of an object, clearing the "purple"
   * status of the object (if it were already purple).  An object is
   * marked purple if it is a potential root of a garbage cycle.  If
   * an object's RC is incremented, it must be live and therefore
   * should not be considered as a potential garbage cycle.  This must
   * be an atomic operation if parallel GC is supported.
   *
   * @param object The object whose RC is to be incremented.
   */
  public static void incRC(VM_Address object)
    throws VM_PragmaUninterruptible, VM_PragmaInline {
    int oldValue, newValue;
    do {
      oldValue = VM_Magic.prepareInt(object, RC_HEADER_OFFSET);
      newValue = oldValue + INCREMENT;
      if (VM_Interface.VerifyAssertions)
        VM_Interface._assert(newValue <= INCREMENT_LIMIT);
      if (Plan.REF_COUNT_CYCLE_DETECTION) newValue = (newValue & ~PURPLE);
    } while (!VM_Magic.attemptInt(object, RC_HEADER_OFFSET, oldValue, newValue));
  }

  /**
   * Decrement the reference count of an object.  Return either
   * <code>DEC_KILL</code> if the count went to zero,
   * <code>DEC_BUFFER</code> if the count did not go to zero and the
   * object was not already in the purple buffer, and
   * <code>DEC_PURPLE</code> if the count did not go to zero and the
   * object was already in the purple buffer.  This must be an atomic
   * operation if parallel GC is supported.
   *
   * @param object The object whose RC is to be decremented.
   * @return <code>DEC_KILL</code> if the count went to zero,
   * <code>DEC_BUFFER</code> if the count did not go to zero and the
   * object was not already in the purple buffer, and
   * <code>DEC_PURPLE</code> if the count did not go to zero and the
   * object was already in the purple buffer.
   */
  public static int decRC(VM_Address object)
    throws VM_PragmaUninterruptible, VM_PragmaInline {
    int oldValue, newValue;
    int rtn;
    do {
      oldValue = VM_Magic.prepareInt(object, RC_HEADER_OFFSET);
      newValue = oldValue - INCREMENT;
      if (newValue < LIVE_THRESHOLD)
        rtn = DEC_KILL;
      else if (Plan.REF_COUNT_CYCLE_DETECTION && 
               ((newValue & COLOR_MASK) < PURPLE)) { // if not purple or green
        rtn = ((newValue & BUFFERED_MASK) == 0) ? DEC_BUFFER : DEC_PURPLE;
        newValue = (newValue & ~COLOR_MASK) | PURPLE | BUFFERED_MASK;
      } else
        rtn = DEC_PURPLE;
    } while (!VM_Magic.attemptInt(object, RC_HEADER_OFFSET, oldValue, newValue));
    return rtn;
  }

  public static boolean isBuffered(VM_Address object)
    throws VM_PragmaUninterruptible, VM_PragmaInline {
    return  (VM_Magic.getIntAtOffset(object, RC_HEADER_OFFSET) & BUFFERED_MASK) == BUFFERED_MASK;
  }

  /****************************************************************************
   *
   * Sanity check support
   */
 
  /**
   * Increment the sanity reference count of an object.
   *
   * @param object The object whose sanity RC is to be incremented.
   * @return <code>true</code> if this is the first increment to this object
   */
  public static boolean incSanityRC(VM_Address object, boolean root)
    throws VM_PragmaUninterruptible, VM_PragmaInline {
    int oldValue, newValue;
    do {
      oldValue = VM_Magic.prepareInt(object, RC_SANITY_HEADER_OFFSET);
      if (root) {
        newValue = (oldValue | ROOT_REACHABLE);
      } else {
        newValue = oldValue + INCREMENT;
      }
    } while (!VM_Magic.attemptInt(object, RC_SANITY_HEADER_OFFSET, oldValue,
                                  newValue));
    return (oldValue == 0);
  }

  /**
   * Check the ref count and sanity ref count for an object that
   * should have just died.
   *
   * @param object The object to be checked.
   */
  public static void checkOldObject(VM_Address object)
    throws VM_PragmaUninterruptible, VM_PragmaInline {
    int rcv = (VM_Magic.getIntAtOffset(object, RC_HEADER_OFFSET)>>>(INCREMENT_SHIFT-1));
    int sanityRCV = (VM_Magic.getIntAtOffset(object, RC_SANITY_HEADER_OFFSET)>>>(INCREMENT_SHIFT-1));
    if (VM_Interface.VerifyAssertions)
      VM_Interface._assert(sanityRCV == 0);
    if (sanityRCV == 0 && rcv != 0) {
      VM_Interface.sysFail("");
    }
  }

  /**
   * Check the sanity reference count and actual reference count for
   * an object.
   *
   * @param object The object whose RC is to be checked.
   * @return <code>true</code> if this is the first check of this object
   */
  public static boolean checkAndClearSanityRC(VM_Address object) 
    throws VM_PragmaUninterruptible, VM_PragmaInline {
    int value = VM_Magic.getIntAtOffset(object, RC_HEADER_OFFSET);
    int sanityValue = VM_Magic.getIntAtOffset(object, RC_SANITY_HEADER_OFFSET);
    int sanityRC = sanityValue >>> INCREMENT_SHIFT;
    boolean sanityRoot = (sanityValue & ROOT_REACHABLE) == ROOT_REACHABLE;
    boolean root = (value & ROOT_REACHABLE) == ROOT_REACHABLE;
    if (sanityValue == 0) {
      return false;
    } else {
      RefCountLocal.sanityLiveObjects++;
      int rc = value >>> INCREMENT_SHIFT;
      if (sanityRC != rc) {
        Log.write("RC mismatch for object: ");
        Log.write(object);
        Log.write(" "); Log.write(rc);
        Log.write(" (rc) != "); Log.write(sanityRC);
        Log.write(" (sanityRC)");
        if (root) Log.write(" r");
        if (sanityRoot) Log.write(" sr");
        Log.writeln("");
        if (((sanityRC == 0) && !sanityRoot) || ((rc == 0) && !root)) {
          VM_Interface.sysFail("");
        }
      }
      VM_Magic.setIntAtOffset(object, RC_SANITY_HEADER_OFFSET, 0);
      return true;
    }
  }

  /**
   * Set the mark bit within the sanity RC word for an object as part
   * of a transitive closure of live objects in the sanity trace.
   * This is only used for non-reference counted objects (immortal
   * etc).
   *
   * @param object The object to be marked.
   * @return <code>true</code> if the mark bit had not already been
   * cleared.
   */
  public static boolean markSanityRC(VM_Address object)
    throws VM_PragmaUninterruptible, VM_PragmaInline {
    int sanityValue = VM_Magic.getIntAtOffset(object, RC_SANITY_HEADER_OFFSET);
    if (sanityValue == 1)
      return false;
    else {
      VM_Magic.setIntAtOffset(object, RC_SANITY_HEADER_OFFSET, 1);
      return true;
    }
  }

  /**
   * Clear the mark bit within the sanity RC word for an object as
   * part of a transitive closure of live objects in the sanity trace.
   * This is only used for non-reference counted objects (immortal
   * etc).
   *
   * @param object The object to be unmarked.
   * @return <code>true</code> if the mark bit had not already been cleared.
   */
  public static boolean unmarkSanityRC(VM_Address object)
    throws VM_PragmaUninterruptible, VM_PragmaInline {
    int sanityValue = VM_Magic.getIntAtOffset(object, RC_SANITY_HEADER_OFFSET);
    if (sanityValue == 0)
      return false;
    else {
      VM_Magic.setIntAtOffset(object, RC_SANITY_HEADER_OFFSET, 0);
      return true;
    }
  }  

  /****************************************************************************
   * 
   * Finalization and dealing with roots
   */

  /**
   * Set the <code>ROOT_REACHABLE</code> bit for an object if it is
   * not already set.  Return true if it was not already set, false
   * otherwise.
   *
   * @param object The object whose <code>ROOT_REACHABLE</code> bit is
   * to be set.
   * @return <code>true</code> if it was set by this call,
   * <code>false</code> if the bit was already set.
   */
  public static boolean setRoot(VM_Address object) 
    throws VM_PragmaUninterruptible, VM_PragmaInline {
    int oldValue, newValue;
    do {
      oldValue = VM_Magic.prepareInt(object, RC_HEADER_OFFSET);
      if ((oldValue & ROOT_REACHABLE) == ROOT_REACHABLE)
        return false;
      newValue = oldValue | ROOT_REACHABLE;
    } while (!VM_Magic.attemptInt(object, RC_HEADER_OFFSET, oldValue, newValue));
    return true;
  }

  /**
   * Clear the <code>ROOT_REACHABLE</code> bit for an object.
   *
   * @param object The object whose <code>ROOT_REACHABLE</code> bit is
   * to be cleared.
   */
  public static void unsetRoot(VM_Address object) 
    throws VM_PragmaUninterruptible, VM_PragmaInline {
    int oldValue, newValue;
    do {
      oldValue = VM_Magic.prepareInt(object, RC_HEADER_OFFSET);
      newValue = oldValue & ~ROOT_REACHABLE;
    } while (!VM_Magic.attemptInt(object, RC_HEADER_OFFSET, oldValue, newValue));
  }

  /**
   * Set the <code>FINALIZABLE</code> bit for an object.
   *
   * @param object The object whose <code>FINALIZABLE</code> bit is
   * to be set.
   */
  static void setFinalizer(VM_Address object) 
    throws VM_PragmaUninterruptible, VM_PragmaInline {
    int oldValue, newValue;
    do {
      oldValue = VM_Magic.prepareInt(object, RC_HEADER_OFFSET);
      newValue = oldValue | FINALIZABLE;
    } while (!VM_Magic.attemptInt(object, RC_HEADER_OFFSET, oldValue, newValue));
  }

  /**
   * Clear the <code>FINALIZABLE</code> bit for an object.
   *
   * @param object The object whose <code>FINALIZABLE</code> bit is
   * to be cleared.
   */
  public static void clearFinalizer(VM_Address object) 
    throws VM_PragmaUninterruptible, VM_PragmaInline {
    int oldValue, newValue;
    do {
      oldValue = VM_Magic.prepareInt(object, RC_HEADER_OFFSET);
      newValue = oldValue & ~FINALIZABLE;
    } while (!VM_Magic.attemptInt(object, RC_HEADER_OFFSET, oldValue, newValue));
  }

  /****************************************************************************
   * 
   * Trial deletion support
   */

  /**
   * Decrement the reference count of an object. This is unsychronized.
   *
   * @param object The object whose RC is to be decremented.
   */
  public static void unsyncDecRC(VM_Address object)
    throws VM_PragmaUninterruptible, VM_PragmaInline {
    int oldValue, newValue;
    int rtn;
    oldValue = VM_Magic.getIntAtOffset(object, RC_HEADER_OFFSET);
    newValue = oldValue - INCREMENT;
    VM_Magic.setIntAtOffset(object, RC_HEADER_OFFSET, newValue);
  }

  /**
   * Increment the reference count of an object. This is unsychronized.
   *
   * @param object The object whose RC is to be incremented.
   */
  public static void unsyncIncRC(VM_Address object)
    throws VM_PragmaUninterruptible, VM_PragmaInline {
    int oldValue, newValue;
    oldValue = VM_Magic.getIntAtOffset(object, RC_HEADER_OFFSET);
    newValue = oldValue + INCREMENT;
    VM_Magic.setIntAtOffset(object, RC_HEADER_OFFSET, newValue);
  }

  public static void print(VM_Address object)
    throws VM_PragmaUninterruptible, VM_PragmaInline {
    Log.write(' ');
    Log.write(VM_Magic.getIntAtOffset(object, RC_HEADER_OFFSET)>>INCREMENT_SHIFT); 
    Log.write(' ');
    switch (getHiRCColor(object)) {
    case PURPLE: Log.write('p'); break;
    case GREEN: Log.write('g'); break;
    }
    switch (getLoRCColor(object)) {
    case BLACK: Log.write('b'); break;
    case WHITE: Log.write('w'); break;
    case GREY: Log.write('g'); break;
    }
    if (isBuffered(object))
      Log.write('b');
    else
      Log.write('u');
  }
  public static void clearBufferedBit(VM_Address object) 
    throws VM_PragmaUninterruptible, VM_PragmaInline {
    int oldValue = VM_Magic.getIntAtOffset(object, RC_HEADER_OFFSET);
    int newValue = oldValue & ~BUFFERED_MASK;
    VM_Magic.setIntAtOffset(object, RC_HEADER_OFFSET, newValue);
  }
  public static boolean isBlack(VM_Address object) 
    throws VM_PragmaUninterruptible, VM_PragmaInline {
    return getLoRCColor(object) == BLACK;
  }
  public static boolean isWhite(VM_Address object) 
    throws VM_PragmaUninterruptible, VM_PragmaInline {
    return getLoRCColor(object) == WHITE;
  }
  public static boolean isGreen(VM_Address object) 
    throws VM_PragmaUninterruptible, VM_PragmaInline {
    return getHiRCColor(object) == GREEN;
  }
  public static boolean isPurple(VM_Address object) 
    throws VM_PragmaUninterruptible, VM_PragmaInline {
    return getHiRCColor(object) == PURPLE;
  }
  public static boolean isPurpleNotGrey(VM_Address object) 
    throws VM_PragmaUninterruptible, VM_PragmaInline {
    return (VM_Magic.getIntAtOffset(object, RC_HEADER_OFFSET) & (PURPLE | GREY)) == PURPLE;
  }
  public static boolean isGrey(VM_Address object) 
    throws VM_PragmaUninterruptible, VM_PragmaInline {
    return getLoRCColor(object) == GREY;
  }
  private static int getRCColor(VM_Address object) 
    throws VM_PragmaUninterruptible, VM_PragmaInline {
    return COLOR_MASK & VM_Magic.getIntAtOffset(object, RC_HEADER_OFFSET);
  }
  private static int getLoRCColor(VM_Address object) 
    throws VM_PragmaUninterruptible, VM_PragmaInline {
    return LO_COLOR_MASK & VM_Magic.getIntAtOffset(object, RC_HEADER_OFFSET);
  }
  private static int getHiRCColor(VM_Address object) 
    throws VM_PragmaUninterruptible, VM_PragmaInline {
    return HI_COLOR_MASK & VM_Magic.getIntAtOffset(object, RC_HEADER_OFFSET);
  }
  public static void makeBlack(VM_Address object) 
    throws VM_PragmaUninterruptible, VM_PragmaInline {
    changeRCLoColor(object, BLACK);
  }
  public static void makeWhite(VM_Address object) 
    throws VM_PragmaUninterruptible, VM_PragmaInline {
    changeRCLoColor(object, WHITE);
  }
  public static void makeGrey(VM_Address object) 
    throws VM_PragmaUninterruptible, VM_PragmaInline {
    if (VM_Interface.VerifyAssertions) 
      VM_Interface._assert(getHiRCColor(object) != GREEN);
    changeRCLoColor(object, GREY);
  }
  private static void changeRCLoColor(VM_Address object, int color)
    throws VM_PragmaUninterruptible, VM_PragmaInline {
    int oldValue = VM_Magic.getIntAtOffset(object, RC_HEADER_OFFSET);
    int newValue = (oldValue & ~LO_COLOR_MASK) | color;
    VM_Magic.setIntAtOffset(object, RC_HEADER_OFFSET, newValue);
  }


  /****************************************************************************
   *
   * Misc
   */

  public final FreeListVMResource getVMResource() { return vmResource;}
  public final MemoryResource getMemoryResource() { return memoryResource;}
}
