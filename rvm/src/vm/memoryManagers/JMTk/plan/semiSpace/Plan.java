/*
 * (C) Copyright Department of Computer Science,
 * Australian National University. 2002
 */
package org.mmtk.plan;

import org.mmtk.policy.CopySpace;
import org.mmtk.policy.ImmortalSpace;
import org.mmtk.policy.TreadmillSpace;
import org.mmtk.policy.TreadmillLocal;
import org.mmtk.utility.AllocAdvice;
import org.mmtk.utility.Allocator;
import org.mmtk.utility.BumpPointer;
import org.mmtk.utility.CallSite;
import org.mmtk.utility.Conversions;
import org.mmtk.utility.FreeListVMResource;
import org.mmtk.utility.HeapGrowthManager;
import org.mmtk.utility.Log;
import org.mmtk.utility.MemoryResource;
import org.mmtk.utility.MonotoneVMResource;
import org.mmtk.utility.MMType;
import org.mmtk.utility.Options;
import org.mmtk.utility.Scan;
import org.mmtk.utility.VMResource;
import org.mmtk.vm.VM_Interface;

import com.ibm.JikesRVM.VM_Address;
import com.ibm.JikesRVM.VM_Word;
import com.ibm.JikesRVM.VM_Extent;
import com.ibm.JikesRVM.VM_Magic;
import com.ibm.JikesRVM.VM_Uninterruptible;
import com.ibm.JikesRVM.VM_PragmaUninterruptible;
import com.ibm.JikesRVM.VM_PragmaInterruptible;
import com.ibm.JikesRVM.VM_PragmaLogicallyUninterruptible;
import com.ibm.JikesRVM.VM_PragmaInline;
import com.ibm.JikesRVM.VM_PragmaNoInline;

import org.mmtk.utility.gcspy.ObjectMap;
import org.mmtk.utility.gcspy.SemiSpaceDriver;
import org.mmtk.utility.gcspy.ImmortalSpaceDriver;
import org.mmtk.utility.gcspy.TreadmillDriver;
import org.mmtk.vm.gcspy.GCSpy;
import org.mmtk.vm.gcspy.ServerInterpreter;

/**
 * This class implements a simple semi-space collector. See the Jones
 * & Lins GC book, section 2.2 for an overview of the basic
 * algorithm. This implementation also includes a large object space
 * (LOS), and an uncollected "immortal" space.<p>
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
 * @author <a href="http://cs.anu.edu.au/~Steve.Blackburn">Steve Blackburn</a>
 * @author Perry Cheng
 * @author <a href="http://www.cs.ukc.ac.uk/~rej">Richard Jones</a>
 *
 * @version $Revision$
 * @date $Date$
 */
public class Plan extends StopTheWorldGC implements VM_Uninterruptible {
  public static final String Id = "$Id$"; 

  /****************************************************************************
   *
   * Class variables
   */
  public static final boolean MOVES_OBJECTS = true;

  // virtual memory resources
  private static FreeListVMResource losVM;
  private static MonotoneVMResource ss0VM;
  private static MonotoneVMResource ss1VM;

  // memory resources
  private static MemoryResource ssMR;
  private static MemoryResource losMR;

  // large object space (LOS) collector
  private static TreadmillSpace losSpace;

  // GC state
  private static boolean hi = false; // True if allocing to "higher" semispace

  // Allocators
  private static final byte LOW_SS_SPACE = 0;
  private static final byte HIGH_SS_SPACE = 1;
  private static final byte LOS_SPACE = 2;
  public static final byte DEFAULT_SPACE = 3; // logical space that maps to either LOW_SS_SPACE or HIGH_SS_SPACE

  // Miscellaneous constants
  private static final int POLL_FREQUENCY = DEFAULT_POLL_FREQUENCY;
  private static final int LOS_SIZE_THRESHOLD = DEFAULT_LOS_SIZE_THRESHOLD;
  
  // Memory layout constants
  public  static final long            AVAILABLE = VM_Interface.MAXIMUM_MAPPABLE.diff(PLAN_START).toLong();
  private static final VM_Extent         SS_SIZE = Conversions.roundDownMB(VM_Extent.fromIntZeroExtend((int)(AVAILABLE / 2.3)));
  private static final VM_Extent        LOS_SIZE = Conversions.roundDownMB(VM_Extent.fromIntZeroExtend((int)(AVAILABLE / 2.3 * 0.3)));
  public  static final VM_Extent        MAX_SIZE = SS_SIZE.add(SS_SIZE);

  private static final VM_Address      LOS_START = PLAN_START;
  private static final VM_Address        LOS_END = LOS_START.add(LOS_SIZE);
  private static final VM_Address       SS_START = LOS_END;
  private static final VM_Address   LOW_SS_START = SS_START;
  private static final VM_Address  HIGH_SS_START = SS_START.add(SS_SIZE);
  private static final VM_Address         SS_END = HIGH_SS_START.add(SS_SIZE);
  private static final VM_Address       HEAP_END = SS_END;


  // The event, BEFORE_COLLECTION or AFTER_COLLECTION
  private static final int BEFORE_COLLECTION = 0;
  private static final int SEMISPACE_COPIED = BEFORE_COLLECTION + 1;
  private static final int AFTER_COLLECTION = SEMISPACE_COPIED + 1;
  private static int gcspyEvent_ = BEFORE_COLLECTION;

  // The specific drivers for this collector
  private static SemiSpaceDriver gcspyDriver;
  private static ImmortalSpaceDriver immortalDriver;
  private static TreadmillDriver tmDriver;

  private static int nextServerSpaceId = 0;	// ServerSpace IDs must be unique


  /****************************************************************************
   *
   * Instance variables
   */

  // allocators
  public BumpPointer ss;
  private TreadmillLocal los;

  // Largely for didactic reasons, we keep track of object allocation
  // with an ObjectMap and then use it to sweep through the heap.
  // However, for a semispace collector it would be better to measure
  // space used by measuring the size of the VMResource used (in constant
  // time).
  private static ObjectMap objectMap;
  
  /****************************************************************************
   *
   * Initialization
   */

  /**
   * Class initializer.  This is executed <i>prior</i> to bootstrap
   * (i.e. at "build" time).  This is where key <i>global</i>
   * instances are allocated.  These instances will be incorporated
   * into the boot image by the build process.
   */
  static {
    ssMR = new MemoryResource("ss", POLL_FREQUENCY);
    losMR = new MemoryResource("los", POLL_FREQUENCY);
    ss0VM = new MonotoneVMResource(LOW_SS_SPACE, "Lower SS", ssMR, LOW_SS_START, SS_SIZE, VMResource.MOVABLE);
    ss1VM = new MonotoneVMResource(HIGH_SS_SPACE, "Upper SS", ssMR, HIGH_SS_START, SS_SIZE, VMResource.MOVABLE);
    losVM = new FreeListVMResource(LOS_SPACE, "LOS", LOS_START, LOS_SIZE, VMResource.IN_VM);
    losSpace = new TreadmillSpace(losVM, losMR);

    addSpace(LOW_SS_SPACE, "Lower Semi-Space");
    addSpace(HIGH_SS_SPACE, "Upper Semi-Space");
    addSpace(LOS_SPACE, "LOS Space");
    // DEFAULT_SPACE is logical and does not actually exist
  }

  /**
   * Constructor
   */
  public Plan() {
    if (VM_Interface.GCSPY) 
      objectMap = new ObjectMap();
    
    ss = new BumpPointer(ss0VM);
    los = new TreadmillLocal(losSpace);
  }

  /**
   * The boot method is called early in the boot process before any
   * allocation.
   */
  public static final void boot()
    throws VM_PragmaInterruptible {
    if (VM_Interface.GCSPY) 
      objectMap.boot();
    
    StopTheWorldGC.boot();
  }

  /****************************************************************************
   *
   * GCSPY
   */

  /**
   * Start the server and wait if necessary
   * 
   * WARNING: allocates memory
   * @param wait Whether to wait
   * @param port The port to talk to the GCSpy client (e.g. visualiser)
   */
  public static final void startGCSpyServer(int port, boolean wait)
    throws VM_PragmaInterruptible {
    String eventNames[] = {"Before collection", 
			   "Semispace copied",
                           "After collection"};
    String generalInfo = "SemiSpace Server Interpreter\n\nGeneral Info";
    
    // The Server
    ServerInterpreter.init("SemiSpaceServerInterpreter",
			    port,
			    eventNames,
			    true /* verbose*/,
			    generalInfo);

    // Initialise each driver
    int tilesize = Options.gcspyTilesize; 
    int maxTiles = HeapGrowthManager.getMaxHeapSize() / tilesize;
    int tmMaxTiles = (int) (maxTiles * 0.3);
    int immortalMaxTiles = IMMORTAL_SIZE.toInt() / tilesize;
    gcspyDriver = new SemiSpaceDriver
                        ("Semispace Space", 
			 tilesize, LOW_SS_START, HIGH_SS_START, HIGH_SS_START, SS_END,
			 maxTiles,
			 true);
    immortalDriver = new ImmortalSpaceDriver
                        ("Immortal Space", 
			 immortalVM,
			 tilesize, IMMORTAL_START, IMMORTAL_END, 
			 immortalMaxTiles,
			 false);
    tmDriver = new TreadmillDriver
                        ("Treadmill Space", 
			 losVM,
			 tilesize, LOS_START, LOS_END,
			 tmMaxTiles,
			 LOS_SIZE_THRESHOLD,
			 false);
    

    //Log.write("SemiServerInterpreter initialised\n");
    
    gcspyEvent_ = BEFORE_COLLECTION;

    // Start the server
    ServerInterpreter.startServer(wait);
  }


  /**
   * Prepare GCSpy for a collection
   * Order of operations is guaranteed by StopTheWorld plan
   *	1. globalPrepare()
   *	2. threadLocalPrepare()
   *	3. gcspyPrepare()
   *	4. gcspyPreRelease()
   *	5. threadLocalRelease()
   *	6. gcspyRelease()
   *	7. globalRelease()
   */
  protected final void gcspyPrepare() {
    //reportSpaces();
    gcspyGatherData(BEFORE_COLLECTION);
  }

  /**
   * This is performed before thread local releases
   */
  protected final void gcspyPreRelease() { 
    gcspyGatherData(SEMISPACE_COPIED);
  }

  /**
   * This is performed after thread local releases
   */
  protected final void gcspyPostRelease() {
    gcspyGatherData(AFTER_COLLECTION);
  }

  /**
   * After VMResource release
   * We use this to release fromspace from the object map
   *
   * @param start the start of the resource
   * @param end the end of the resource
   * @param bytes the number of bytes released
   */
  public static final void releaseVMResource(VM_Address start, VM_Extent bytes) {
    //Log.write("Plan.releaseVMResource:", start);
    //Log.write("-", start.add(bytes));
    //Log.writeln(", bytes released: ", bytes);
    //reportSpaces();
    objectMap.release(start, bytes.toInt());  
  }
  
  /**
   * After VMResource acquisition
   * @param start the start of the resource
   * @param end the end of the resource
   * @param bytes the number of byted acquired
   */
  public static final void acquireVMResource(VM_Address start, VM_Address end, VM_Extent bytes) {
  }
  
  /**
   * Gather data for GCSpy
   * This method sweeps the semispace under consideration to gather data.
   * This can obviously be discovered in constant time simply by comparing
   * the start and the end addresses of the semispace. However, we demonstrate
   * here how data can be gathered for the more general case (when objects are
   * not compacted).
   * The LOS is handled better.
   * 
   * @param event The event, either BEFORE_COLLECTION, SEMISPACE_COPIED or AFTER_COLLECTION
   */
  private void gcspyGatherData(int event) {
    // Port = 0 means no gcspy
    if (GCSpy.getGCSpyPort() == 0)
      return;
    
    // We should not allocate anything while transmitting
    //if (VM_Interface.VerifyAssertions)
    //  objectMap.trapAllocation(true);

    if (ServerInterpreter.shouldTransmit(event)) {		
      ServerInterpreter.startCompensationTimer();
      
      // -- Handle the semispace collector --
      // iterate through toSpace
      // at this point, hi has been flipped by globalPrepare()
      gcspyDriver.zero();
      boolean useLowSpace = hi ^ (event != BEFORE_COLLECTION);
      switch (event) {
      case BEFORE_COLLECTION:
      case AFTER_COLLECTION:
	/*
	if (useLowSpace) 
	  Log.write("\nExamining Lowspace (", event);
	else
	  Log.write("\nExamining Highspace (", event);
	Log.write(")";
	reportSpaces();
        */
	if (useLowSpace) 
          gcspyGatherSemispacedata(0, ss0VM, true);
        else 
          gcspyGatherSemispacedata(1, ss1VM, true);
	break;
      case SEMISPACE_COPIED:
	/*
	if (hi) 
	  Log.write("\nExamining Highspace (", event);
	else
	  Log.write("\nExamining Lowspace (", event);
	Log.write(")";
	reportSpaces();
        */
	gcspyGatherSemispacedata(0, ss0VM, !hi);
	gcspyGatherSemispacedata(1, ss1VM,  hi);
        break;
      }
      
      // -- Handle the Treadmill space --
      tmDriver.zero();
      los.gcspyGatherData(event, tmDriver, false);
      los.gcspyGatherData(event, tmDriver, true);
      
      // -- Handle the immortal space --
      immortalDriver.zero(); 
      immortal.gcspyGatherData(event, immortalDriver);

      ServerInterpreter.stopCompensationTimer();	
      gcspyDriver.finish(event, useLowSpace ? 0 : 1);
      immortalDriver.finish(event);
      tmDriver.finish(event);

    } else {
      //Log.write("not transmitting...");
    }
    ServerInterpreter.serverSafepoint(event);		
    //if (VM_Interface.VerifyAssertions)
    //  objectMap.trapAllocation(false); 
  }

  private void gcspyGatherSemispacedata(int semi, MonotoneVMResource ssVM, boolean total) {
    objectMap.iterator(ssVM.getStart(), ssVM.getCursor());	
    gcspyDriver.setRange(semi, ssVM.getStart(), ssVM.getCursor());
    
    VM_Address addr = VM_Address.zero();
    while (objectMap.hasNext()) {
      addr = objectMap.next();
      if (VM_Interface.VerifyAssertions) 
	VM_Interface._assert(VMResource.refInVM(addr));
      gcspyDriver.traceObject(addr, total);
    }
  }

  private static void reportSpaces() {
    Log.write("  Low semispace:  ", ss0VM.getStart());
    Log.writeln("-", ss0VM.getCursor());
    Log.write("  High semispace: ", ss1VM.getStart());
    Log.writeln("-", ss1VM.getCursor());
  }

  public static int getNextServerSpaceId() { return nextServerSpaceId++; }


  /****************************************************************************
   *
   * Allocation
   */

  /**
   * Allocate space (for an object)
   *
   * @param bytes The size of the space to be allocated (in bytes)
   * @param isScalar True if the object occupying this space will be a scalar
   * @param allocator The allocator number to be used for this allocation
   * @param advice Statically-generated allocation advice for this allocation
   * @return The address of the first byte of the allocated region
   */
  public final VM_Address alloc(int bytes, boolean isScalar, int allocator,
                                AllocAdvice advice)
    throws VM_PragmaInline {
    if (GATHER_MARK_CONS_STATS) cons.inc(bytes);
    if (VM_Interface.VerifyAssertions) VM_Interface._assert(bytes == (bytes & (~(BYTES_IN_ADDRESS-1))));
    if (allocator == DEFAULT_SPACE && bytes > LOS_SIZE_THRESHOLD) {
      return los.alloc(isScalar, bytes);
    } else {
      switch (allocator) {
      case  DEFAULT_SPACE: return ss.alloc(isScalar, bytes);
      case IMMORTAL_SPACE: return immortal.alloc(isScalar, bytes);
      case      LOS_SPACE: return los.alloc(isScalar, bytes);
      default: 
        if (VM_Interface.VerifyAssertions)
          VM_Interface.sysFail("No such allocator");
        return VM_Address.zero();
      }
    }
  }

  /**
   * Perform post-allocation actions.  For many allocators none are
   * required.
   *
   * @param ref The newly allocated object
   * @param tib The TIB of the newly allocated object
   * @param bytes The size of the space to be allocated (in bytes)
   * @param isScalar True if the object occupying this space will be a scalar
   * @param allocator The allocator number to be used for this allocation
   */
  public final void postAlloc(VM_Address ref, Object[] tib, int bytes,
                              boolean isScalar, int allocator)
    throws VM_PragmaInline {
    if (allocator == DEFAULT_SPACE && bytes > LOS_SIZE_THRESHOLD) {
      Header.initializeLOSHeader(ref, tib, bytes, isScalar);
    } else {
      switch (allocator) {
      case  DEFAULT_SPACE:
			   // In principle, taxing the allocator is undesirable
			   // and only necessary because it is not possible to
			   // sweep through the heap.
                           if (VM_Interface.GCSPY) 
                             if (GCSpy.getGCSpyPort() != 0)
			       objectMap.alloc(VM_Magic.objectAsAddress(ref));
                           return;
      case IMMORTAL_SPACE: ImmortalSpace.postAlloc(ref); return;
      case      LOS_SPACE: Header.initializeLOSHeader(ref, tib, bytes, isScalar); return;
      default:
        if (VM_Interface.VerifyAssertions) 
          VM_Interface.sysFail("No such allocator");
      }
    }
  }

  /**
   * Allocate space for copying an object (this method <i>does not</i>
   * copy the object, it only allocates space)
   *
   * @param original A reference to the original object
   * @param bytes The size of the space to be allocated (in bytes)
   * @param isScalar True if the object occupying this space will be a scalar
   * @return The address of the first byte of the allocated region
   */
  public final VM_Address allocCopy(VM_Address original, int bytes, 
                                    boolean isScalar) 
    throws VM_PragmaInline {
    if (VM_Interface.VerifyAssertions) VM_Interface._assert(bytes <= LOS_SIZE_THRESHOLD);
    if (GATHER_MARK_CONS_STATS) {
      cons.inc(bytes);
      mark.inc(bytes);
    }
    // Knock copied objects out of the object map so we can see what's left
    // (i.e. garbage) in fromspace after the collection.
    if (VM_Interface.GCSPY) 
      if (GCSpy.getGCSpyPort() != 0) {
        objectMap.dealloc(VM_Magic.objectAsAddress(original));
      }
    VM_Address result = ss.alloc(isScalar, bytes);
    return result;
  }

  /**  
   * Perform any post-copy actions.  In this case nothing is required.
   *
   * @param ref The newly allocated object
   * @param tib The TIB of the newly allocated object
   * @param bytes The size of the space to be allocated (in bytes)
   * @param isScalar True if the object occupying this space will be a scalar
   */
  public final void postCopy(VM_Address ref, Object[] tib, int bytes,
                             boolean isScalar)
         throws VM_PragmaInline {
    if (VM_Interface.GCSPY) 
      if (GCSpy.getGCSpyPort() != 0)
	objectMap.alloc(VM_Magic.objectAsAddress(ref));
  }

  protected final byte getSpaceFromAllocator (Allocator a) {
    if (a == ss) return DEFAULT_SPACE;
    if (a == los) return LOS_SPACE;
    return super.getSpaceFromAllocator(a);
  }

  protected final Allocator getAllocatorFromSpace (byte s) {
    if (s == DEFAULT_SPACE) return ss;
    if (s == LOS_SPACE) return los;
    return super.getAllocatorFromSpace(s);
  }


  /**
   * Give the compiler/runtime statically generated alloction advice
   * which will be passed to the allocation routine at runtime.
   *
   * @param type The type id of the type being allocated
   * @param bytes The size (in bytes) required for this object
   * @param callsite Information identifying the point in the code
   * where this allocation is taking place.
   * @param hint A hint from the compiler as to which allocator this
   * site should use.
   * @return Allocation advice to be passed to the allocation routine
   * at runtime
   */
  public final AllocAdvice getAllocAdvice(MMType type, int bytes,
                                          CallSite callsite,
                                          AllocAdvice hint) {
    return null;
  }

  /**
   * Return the initial header value for a newly allocated LOS
   * instance.
   *
   * @param bytes The size of the newly created instance in bytes.
   * @return The inital header value for the new instance.
   */
  public static final VM_Word getInitialHeaderValue(int bytes)
    throws VM_PragmaInline {
    return losSpace.getInitialHeaderValue(bytes);
  }

  /**
   * This method is called periodically by the allocation subsystem
   * (by default, each time a page is consumed), and provides the
   * collector with an opportunity to collect.<p>
   *
   * We trigger a collection whenever an allocation request is made
   * that would take the number of pages in use (committed for use)
   * beyond the number of pages available.  Collections are triggered
   * through the runtime, and ultimately call the
   * <code>collect()</code> method of this class or its superclass.<p>
   *
   * This method is clearly interruptible since it can lead to a GC.
   * However, the caller is typically uninterruptible and this fiat allows 
   * the interruptibility check to work.  The caveat is that the caller 
   * of this method must code as though the method is interruptible. 
   * In practice, this means that, after this call, processor-specific
   * values must be reloaded.
   *
   * @param mustCollect True if a this collection is forced.
   * @param mr The memory resource that triggered this collection.
   * @return True if a collection is triggered
   */
  public final boolean poll(boolean mustCollect, MemoryResource mr) 
    throws VM_PragmaLogicallyUninterruptible {
    if (collectionsInitiated > 0 || !initialized || mr == metaDataMR)
      return false;
    mustCollect |= stressTestGCRequired();
    if (mustCollect || getPagesReserved() > getTotalPages()) {
      required = mr.reservedPages() - mr.committedPages();
      if (mr == ssMR) required = required<<1; // must account for copy reserve
      VM_Interface.triggerCollection(VM_Interface.RESOURCE_GC_TRIGGER);
      return true;
    }
    return false;
  }

  /****************************************************************************
   *
   * Collection
   */
  // Important notes:
  //   . Global actions are executed by only one thread
  //   . Thread-local actions are executed by all threads
  //   . The following order is guaranteed by BasePlan, with each
  //     separated by a synchronization barrier.:
  //      1. globalPrepare()
  //      2. threadLocalPrepare()
  //      3. threadLocalRelease()
  //      4. globalRelease()
  //

  /**
   * Perform operations with <i>global</i> scope in preparation for a
   * collection.  This is called by <code>StopTheWorld</code>, which will
   * ensure that <i>only one thread</i> executes this.<p>
   *
   * In this case, it means flipping semi-spaces, resetting the
   * semi-space memory resource, and preparing each of the collectors.
   */
  protected final void globalPrepare() {
    hi = !hi;        // flip the semi-spaces
    ssMR.reset();    // reset the semispace memory resource, and
    // prepare each of the collected regions
    CopySpace.prepare(((hi) ? ss0VM : ss1VM), ssMR);
    ImmortalSpace.prepare(immortalVM, null);
    losSpace.prepare(losVM, losMR);
  }

  /**
   * Perform operations with <i>thread-local</i> scope in preparation
   * for a collection.  This is called by <code>StopTheWorld</code>, which
   * will ensure that <i>all threads</i> execute this.<p>
   *
   * In this case, it means resetting the semi-space and large object
   * space allocators.
   */
  protected final void threadLocalPrepare(int count) {
    // rebind the semispace bump pointer to the appropriate semispace.
    ss.rebind(((hi) ? ss1VM : ss0VM)); 
    los.prepare();
  }

  /**
   * Perform operations with <i>thread-local</i> scope to clean up at
   * the end of a collection.  This is called by
   * <code>StopTheWorld</code>, which will ensure that <i>all threads</i>
   * execute this.<p>
   *
   * In this case, it means releasing the large object space (which
   * triggers the sweep phase of the treadmill collector used by the
   * LOS).
   */
  protected final void threadLocalRelease(int count) {
    los.release();
  }

  /**
   * Perform operations with <i>global</i> scope to clean up at the
   * end of a collection.  This is called by <code>StopTheWorld</code>,
   * which will ensure that <i>only one</i> thread executes this.<p>
   *
   * In this case, it means releasing each of the spaces and checking
   * whether the GC made progress.
   */
  protected final void globalRelease() {
    // release each of the collected regions
    losSpace.release();
    ((hi) ? ss0VM : ss1VM).release();
    CopySpace.release(((hi) ? ss0VM : ss1VM), ssMR);
    ImmortalSpace.release(immortalVM, null);
    progress = (getPagesReserved() + required < getTotalPages());
  }


  /****************************************************************************
   *
   * Object processing and tracing
   */


  /**
   * Trace a reference during GC.  This involves determining which
   * collection policy applies and calling the appropriate
   * <code>trace</code> method.
   *
   * @param obj The object reference to be traced.  This is <i>NOT</i> an
   * interior pointer.
   * @return The possibly moved reference.
   */
  public static final VM_Address traceObject(VM_Address obj)
    throws VM_PragmaInline {
    if (obj.isZero()) return obj;
    VM_Address addr = VM_Interface.refToAddress(obj);
    byte space = VMResource.getSpace(addr);
    switch (space) {
    case LOW_SS_SPACE:   return   hi  ? CopySpace.traceObject(obj) : obj;
    case HIGH_SS_SPACE:  return (!hi) ? CopySpace.traceObject(obj) : obj;
    case LOS_SPACE:      return losSpace.traceObject(obj);
    case IMMORTAL_SPACE: return ImmortalSpace.traceObject(obj);
    case BOOT_SPACE:     return ImmortalSpace.traceObject(obj);
    case META_SPACE:     return obj;
    default:  
      if (VM_Interface.VerifyAssertions) 
        spaceFailure(obj, space, "Plan.traceObject()");
      return obj;
    }
  }

  /**
   * Trace a reference during GC.  This involves determining which
   * collection policy applies and calling the appropriate
   * <code>trace</code> method.
   *
   * @param obj The object reference to be traced.  This is <i>NOT</i>
   * an interior pointer.
   * @param root True if this reference to <code>obj</code> was held
   * in a root.
   * @return The possibly moved reference.
   */
  public static final VM_Address traceObject(VM_Address obj, boolean root) {
    return traceObject(obj);  // root or non-root is of no consequence here
  }

  /**
   * Scan an object that was previously forwarded but not scanned.
   * The separation between forwarding and scanning is necessary for
   * the "pre-copying" mechanism to function properly.
   *
   * @param object The object to be scanned.
   */
  protected final void scanForwardedObject(VM_Address object) {
    Scan.scanObject(object);
  }

  /**
   * Forward the object referred to by a given address and update the
   * address if necessary.  This <i>does not</i> enqueue the referent
   * for processing; the referent must be explicitly enqueued if it is
   * to be processed.
   *
   * @param location The location whose referent is to be forwarded if
   * necessary.  The location will be updated if the referent is
   * forwarded.
   */
  public static void forwardObjectLocation(VM_Address location) 
    throws VM_PragmaInline {
    VM_Address obj = VM_Magic.getMemoryAddress(location);
    if (!obj.isZero()) {
      VM_Address addr = VM_Interface.refToAddress(obj);
      byte space = VMResource.getSpace(addr);
      if ((hi && space == LOW_SS_SPACE) || (!hi && space == HIGH_SS_SPACE))
        VM_Magic.setMemoryAddress(location, CopySpace.forwardObject(obj));
    }
  }

  /**
   * If the object in question has been forwarded, return its
   * forwarded value.<p>
   *
   * @param object The object which may have been forwarded.
   * @return The forwarded value for <code>object</code>.
   */
  public static final VM_Address getForwardedReference(VM_Address object) {
    if (!object.isZero()) {
      VM_Address addr = VM_Interface.refToAddress(object);
      byte space = VMResource.getSpace(addr);
      if ((hi && space == LOW_SS_SPACE) || (!hi && space == HIGH_SS_SPACE)) {
        if (VM_Interface.VerifyAssertions) 
          VM_Interface._assert(CopyingHeader.isForwarded(object));
        return CopyingHeader.getForwardingPointer(object);
      }
    }
    return object;
  }

  /**
   * Return true if the given reference is to an object that is within
   * one of the semi-spaces.
   *
   * @param ref The object in question
   * @return True if the given reference is to an object that is within
   * one of the semi-spaces.
   */
  public static final boolean isSemiSpaceObject(VM_Address ref) {
    VM_Address addr = VM_Interface.refToAddress(VM_Magic.objectAsAddress(ref));
    return (addr.GE(SS_START) && addr.LE(SS_END));
  }

  /**
   * Return true if <code>obj</code> is a live object.
   *
   * @param obj The object in question
   * @return True if <code>obj</code> is a live object.
   */
  public static final boolean isLive(VM_Address obj) {
    if (obj.isZero()) return false;
    VM_Address addr = VM_Interface.refToAddress(obj);
    byte space = VMResource.getSpace(addr);
    switch (space) {
    case LOW_SS_SPACE:    return CopySpace.isLive(obj);
    case HIGH_SS_SPACE:   return CopySpace.isLive(obj);
    case LOS_SPACE:       return losSpace.isLive(obj);
    case IMMORTAL_SPACE:  return true;
    case BOOT_SPACE:      return true;
    case META_SPACE:      return true;
    default:
      if (VM_Interface.VerifyAssertions) 
        spaceFailure(obj, space, "Plan.isLive()");
      return false;
    }
  }


  /**
   * Reset the GC bits in the header word of an object that has just
   * been copied.  This may, for example, involve clearing a write
   * barrier bit.  In this case nothing is required, so the header
   * word is returned unmodified.
   *
   * @param fromObj The original (uncopied) object
   * @param forwardingWord The integer containing the GC bits, which is the GC word
   * of the original object, and typically encodes some GC state as
   * well as pointing to the copied object.
   * @param bytes The size of the copied object in bytes.
   * @return The updated GC word (in this case unchanged).
   */
  public static final VM_Word resetGCBitsForCopy(VM_Address fromObj,
					     VM_Word forwardingWord, int bytes) {
    return forwardingWord; // a no-op for this collector
  }

  // XXX Missing Javadoc comment.
  public static boolean willNotMove (VM_Address obj) {
   boolean movable = VMResource.refIsMovable(obj);
   if (!movable) return true;
   VM_Address addr = VM_Interface.refToAddress(obj);
   return (hi ? ss1VM : ss0VM).inRange(addr);
  }

  /****************************************************************************
   *
   * Space management
   */

  /**
   * Return the number of pages reserved for use given the pending
   * allocation.  This <i>includes</i> space reserved for copying.
   *
   * @return The number of pages reserved given the pending
   * allocation, including space reserved for copying.
   */
  protected static final int getPagesReserved() {
    // we must account for the number of pages required for copying,
    // which equals the number of semi-space pages reserved
    return ssMR.reservedPages() + getPagesUsed();
  }

  /**
   * Return the number of pages reserved for use given the pending
   * allocation.  This is <i>exclusive of</i> space reserved for
   * copying.
   *
   * @return The number of pages reserved given the pending
   * allocation, excluding space reserved for copying.
   */
  protected static final int getPagesUsed() {
    int pages = ssMR.reservedPages();
    pages += losMR.reservedPages();
    pages += immortalMR.reservedPages();
    pages += metaDataMR.reservedPages();
    return pages;
  }

  /**
   * Return the number of pages available for allocation, <i>assuming
   * all future allocation is to the semi-space</i>.
   *
   * @return The number of pages available for allocation, <i>assuming
   * all future allocation is to the semi-space</i>.
   */
  protected static final int getPagesAvail() {
    int semispaceTotal = getTotalPages() - losMR.reservedPages() 
      - immortalMR.reservedPages();
    return (semispaceTotal>>1) - ssMR.reservedPages();
  }


  /****************************************************************************
   *
   * Miscellaneous
   */

  /**
   * Show the status of each of the allocators.
   */
  public final void show() {
    ss.show();
    los.show();
    immortal.show();
  }


}
