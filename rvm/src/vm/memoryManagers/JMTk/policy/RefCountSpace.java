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

import org.vmmagic.unboxed.*;
import org.vmmagic.pragma.*;

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
 * @version $Revision$
 * @date $Date$
 */
public final class RefCountSpace implements Constants, Uninterruptible {
  public final static String Id = "$Id$"; 

  /****************************************************************************
   *
 * @author Ian Warrington
   * Class variables
   */
  public static final boolean INC_DEC_ROOT = false;
  public static final boolean RC_SANITY_CHECK = false;
  
  public static final int LOCAL_GC_BITS_REQUIRED = 2;
  public static final int GLOBAL_GC_BITS_REQUIRED = 0; 
  /** How many bytes are used by all GC header fields? */
  public static final int GC_HEADER_BYTES_REQUIRED = (RC_SANITY_CHECK) ? 2*BYTES_IN_ADDRESS : BYTES_IN_ADDRESS;
  protected static final Offset RC_HEADER_OFFSET = Offset.fromInt(VM_Interface.GC_HEADER_OFFSET());
  protected static final Offset RC_SANITY_HEADER_OFFSET = Offset.fromInt(VM_Interface.GC_HEADER_OFFSET() + BYTES_IN_ADDRESS);


  /* Mask bits to signify the start/finish of logging an object */
  private static final Word LOGGING_MASK = Word.one().lsh(2).sub(Word.one()); //...00011
  private static final int      LOG_BIT = 0;
  private static final Word       LOGGED = Word.zero();
  public static final Word     UNLOGGED = Word.one();
  private static final Word BEING_LOGGED = Word.one().lsh(2).sub(Word.one()); //...00011

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
  public final Address traceObject(Address object)
    throws InlinePragma {
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
  public static boolean logRequired(Address object)
    throws UninterruptiblePragma, InlinePragma {
    Word value = VM_Interface.readAvailableBitsWord(object);
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
  public static boolean attemptToLog(Address object)
    throws UninterruptiblePragma, InlinePragma {
    Word oldValue;
    do {
      oldValue = VM_Interface.prepareAvailableBits(object);
      if (oldValue.and(LOGGING_MASK).EQ(LOGGED)) return false;
    } while ((oldValue.and(LOGGING_MASK).EQ(BEING_LOGGED)) ||
             !VM_Interface.attemptAvailableBits(object, oldValue, 
                                                oldValue.or(BEING_LOGGED)));
    if (VM_Interface.VerifyAssertions) {
      Word value = VM_Interface.readAvailableBitsWord(object);
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
  public static void makeLogged(Address object)
    throws UninterruptiblePragma, InlinePragma {
    Word value = VM_Interface.readAvailableBitsWord(object);
    if (VM_Interface.VerifyAssertions)
      VM_Interface._assert(value.and(LOGGING_MASK).NE(LOGGED));
    VM_Interface.writeAvailableBitsWord(object, value.and(LOGGING_MASK.not()));
  }

  /**
   * Change <code>object</code>'s state to <code>UNLOGGED</code>.
   *
   * @param object The object whose state is to be changed.
   */
  public static void makeUnlogged(Address object)
    throws UninterruptiblePragma, InlinePragma {
    Word value = VM_Interface.readAvailableBitsWord(object);
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
   * @param typeRef the type reference for the instance being created
   * @param initialInc  do we want to initialize this header with an
   * initial increment?
   */
  public static void initializeHeader(Address object, Address typeRef,
				      boolean initialInc)
    throws InlinePragma {
    // all objects are birthed with an RC of INCREMENT
    int initialValue =  (initialInc) ? INCREMENT : 0;
    if (Plan.REF_COUNT_CYCLE_DETECTION && VM_Interface.isAcyclic(typeRef))
      initialValue |= GREEN;
    object.store(initialValue, RC_HEADER_OFFSET);
  }

  /**
   * Return true if given object is live
   *
   * @param object The object whose liveness is to be tested
   * @return True if the object is alive
   */
  public static boolean isLiveRC(Address object) 
    throws UninterruptiblePragma, InlinePragma {
    return object.loadInt(RC_HEADER_OFFSET) >= LIVE_THRESHOLD;
  }

  /**
   * Return true if given object is unreachable from roots or other
   * objects (i.e. ignoring the finalizer list).  Mark the object as a
   * finalizer object.
   *
   * @param object The object whose finalizability is to be tested
   * @return True if the object is finalizable
   */
  public static boolean isFinalizable(Address object) 
    throws UninterruptiblePragma, InlinePragma {
    setFinalizer(object);
    return object.loadInt(RC_HEADER_OFFSET) < HARD_THRESHOLD;
  }

  public static void incRCOOL(Address object) 
    throws UninterruptiblePragma, NoInlinePragma {
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
  public static void incRC(Address object)
    throws UninterruptiblePragma, InlinePragma {
    int oldValue, newValue;
    do {
      oldValue = object.prepareInt(RC_HEADER_OFFSET);
      newValue = oldValue + INCREMENT;
      if (VM_Interface.VerifyAssertions)
        VM_Interface._assert(newValue <= INCREMENT_LIMIT);
      if (Plan.REF_COUNT_CYCLE_DETECTION) newValue = (newValue & ~PURPLE);
    } while (!object.attempt(oldValue, newValue, RC_HEADER_OFFSET));
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
  public static int decRC(Address object)
    throws UninterruptiblePragma, InlinePragma {
    int oldValue, newValue;
    int rtn;
    do {
      oldValue = object.prepareInt(RC_HEADER_OFFSET);
      newValue = oldValue - INCREMENT;
      if (newValue < LIVE_THRESHOLD)
        rtn = DEC_KILL;
      else if (Plan.REF_COUNT_CYCLE_DETECTION && 
               ((newValue & COLOR_MASK) < PURPLE)) { // if not purple or green
        rtn = ((newValue & BUFFERED_MASK) == 0) ? DEC_BUFFER : DEC_PURPLE;
        newValue = (newValue & ~COLOR_MASK) | PURPLE | BUFFERED_MASK;
      } else
        rtn = DEC_PURPLE;
    } while (!object.attempt(oldValue, newValue, RC_HEADER_OFFSET));
    return rtn;
  }

  public static boolean isBuffered(Address object)
    throws UninterruptiblePragma, InlinePragma {
    return (object.loadInt(RC_HEADER_OFFSET) & BUFFERED_MASK) == BUFFERED_MASK;
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
  public static boolean incSanityRC(Address object, boolean root)
    throws UninterruptiblePragma, InlinePragma {
    int oldValue, newValue;
    do {
      oldValue = object.prepareInt(RC_SANITY_HEADER_OFFSET);
      if (root) {
        newValue = (oldValue | ROOT_REACHABLE);
      } else {
        newValue = oldValue + INCREMENT;
      }
    } while (!object.attempt(oldValue, newValue, RC_SANITY_HEADER_OFFSET));
    return (oldValue == 0);
  }

  /**
   * Check the ref count and sanity ref count for an object that
   * should have just died.
   *
   * @param object The object to be checked.
   */
  public static void checkOldObject(Address object)
    throws UninterruptiblePragma, InlinePragma {
    int rcv = object.loadInt(RC_HEADER_OFFSET)>>>(INCREMENT_SHIFT-1);
    int sanityRCV = object.loadInt(RC_SANITY_HEADER_OFFSET)>>>(INCREMENT_SHIFT-1);
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
  public static boolean checkAndClearSanityRC(Address object) 
    throws UninterruptiblePragma, InlinePragma {
    int value = object.loadInt(RC_HEADER_OFFSET);
    int sanityValue = object.loadInt(RC_SANITY_HEADER_OFFSET);
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
      object.store(0, RC_SANITY_HEADER_OFFSET);
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
  public static boolean markSanityRC(Address object)
    throws UninterruptiblePragma, InlinePragma {
    int sanityValue = object.loadInt(RC_SANITY_HEADER_OFFSET);
    if (sanityValue == 1)
      return false;
    else {
      object.store(1, RC_SANITY_HEADER_OFFSET);
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
  public static boolean unmarkSanityRC(Address object)
    throws UninterruptiblePragma, InlinePragma {
    int sanityValue = object.loadInt(RC_SANITY_HEADER_OFFSET);
    if (sanityValue == 0)
      return false;
    else {
      object.store(0, RC_SANITY_HEADER_OFFSET);
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
  public static boolean setRoot(Address object) 
    throws UninterruptiblePragma, InlinePragma {
    int oldValue, newValue;
    do {
      oldValue = object.prepareInt(RC_HEADER_OFFSET);
      if ((oldValue & ROOT_REACHABLE) == ROOT_REACHABLE)
        return false;
      newValue = oldValue | ROOT_REACHABLE;
    } while (!object.attempt(oldValue, newValue, RC_HEADER_OFFSET));
    return true;
  }

  /**
   * Clear the <code>ROOT_REACHABLE</code> bit for an object.
   *
   * @param object The object whose <code>ROOT_REACHABLE</code> bit is
   * to be cleared.
   */
  public static void unsetRoot(Address object) 
    throws UninterruptiblePragma, InlinePragma {
    int oldValue, newValue;
    do {
      oldValue = object.prepareInt(RC_HEADER_OFFSET);
      newValue = oldValue & ~ROOT_REACHABLE;
    } while (!object.attempt(oldValue, newValue, RC_HEADER_OFFSET));
  }

  /**
   * Set the <code>FINALIZABLE</code> bit for an object.
   *
   * @param object The object whose <code>FINALIZABLE</code> bit is
   * to be set.
   */
  static void setFinalizer(Address object) 
    throws UninterruptiblePragma, InlinePragma {
    int oldValue, newValue;
    do {
      oldValue = object.prepareInt(RC_HEADER_OFFSET);
      newValue = oldValue | FINALIZABLE;
    } while (!object.attempt(oldValue, newValue, RC_HEADER_OFFSET));
  }

  /**
   * Clear the <code>FINALIZABLE</code> bit for an object.
   *
   * @param object The object whose <code>FINALIZABLE</code> bit is
   * to be cleared.
   */
  public static void clearFinalizer(Address object) 
    throws UninterruptiblePragma, InlinePragma {
    int oldValue, newValue;
    do {
      oldValue = object.prepareInt(RC_HEADER_OFFSET);
      newValue = oldValue & ~FINALIZABLE;
    } while (!object.attempt(oldValue, newValue, RC_HEADER_OFFSET));
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
  public static void unsyncDecRC(Address object)
    throws UninterruptiblePragma, InlinePragma {
    int oldValue, newValue;
    int rtn;
    oldValue = object.loadInt(RC_HEADER_OFFSET);
    newValue = oldValue - INCREMENT;
    object.store(newValue, RC_HEADER_OFFSET);
  }

  /**
   * Increment the reference count of an object. This is unsychronized.
   *
   * @param object The object whose RC is to be incremented.
   */
  public static void unsyncIncRC(Address object)
    throws UninterruptiblePragma, InlinePragma {
    int oldValue, newValue;
    oldValue = object.loadInt(RC_HEADER_OFFSET);
    newValue = oldValue + INCREMENT;
    object.store(newValue, RC_HEADER_OFFSET);
  }

  public static void print(Address object)
    throws UninterruptiblePragma, InlinePragma {
    Log.write(' ');
    Log.write(object.loadInt(RC_HEADER_OFFSET)>>INCREMENT_SHIFT); 
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
  public static void clearBufferedBit(Address object) 
    throws UninterruptiblePragma, InlinePragma {
    int oldValue = object.loadInt(RC_HEADER_OFFSET);
    int newValue = oldValue & ~BUFFERED_MASK;
    object.store(newValue, RC_HEADER_OFFSET);
  }
  public static boolean isBlack(Address object) 
    throws UninterruptiblePragma, InlinePragma {
    return getLoRCColor(object) == BLACK;
  }
  public static boolean isWhite(Address object) 
    throws UninterruptiblePragma, InlinePragma {
    return getLoRCColor(object) == WHITE;
  }
  public static boolean isGreen(Address object) 
    throws UninterruptiblePragma, InlinePragma {
    return getHiRCColor(object) == GREEN;
  }
  public static boolean isPurple(Address object) 
    throws UninterruptiblePragma, InlinePragma {
    return getHiRCColor(object) == PURPLE;
  }
  public static boolean isPurpleNotGrey(Address object) 
    throws UninterruptiblePragma, InlinePragma {
    return (object.loadInt(RC_HEADER_OFFSET) & (PURPLE | GREY)) == PURPLE;
  }
  public static boolean isGrey(Address object) 
    throws UninterruptiblePragma, InlinePragma {
    return getLoRCColor(object) == GREY;
  }
  private static int getRCColor(Address object) 
    throws UninterruptiblePragma, InlinePragma {
    return COLOR_MASK & object.loadInt(RC_HEADER_OFFSET);
  }
  private static int getLoRCColor(Address object) 
    throws UninterruptiblePragma, InlinePragma {
    return LO_COLOR_MASK & object.loadInt(RC_HEADER_OFFSET);
  }
  private static int getHiRCColor(Address object) 
    throws UninterruptiblePragma, InlinePragma {
    return HI_COLOR_MASK & object.loadInt(RC_HEADER_OFFSET);
  }
  public static void makeBlack(Address object) 
    throws UninterruptiblePragma, InlinePragma {
    changeRCLoColor(object, BLACK);
  }
  public static void makeWhite(Address object) 
    throws UninterruptiblePragma, InlinePragma {
    changeRCLoColor(object, WHITE);
  }
  public static void makeGrey(Address object) 
    throws UninterruptiblePragma, InlinePragma {
    if (VM_Interface.VerifyAssertions) 
      VM_Interface._assert(getHiRCColor(object) != GREEN);
    changeRCLoColor(object, GREY);
  }
  private static void changeRCLoColor(Address object, int color)
    throws UninterruptiblePragma, InlinePragma {
    int oldValue = object.loadInt(RC_HEADER_OFFSET);
    int newValue = (oldValue & ~LO_COLOR_MASK) | color;
    object.store(newValue, RC_HEADER_OFFSET);
  }


  /****************************************************************************
   *
   * Misc
   */

  public final FreeListVMResource getVMResource() { return vmResource;}
  public final MemoryResource getMemoryResource() { return memoryResource;}
}
