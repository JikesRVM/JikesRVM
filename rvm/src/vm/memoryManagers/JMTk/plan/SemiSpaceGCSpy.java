/*
 * (C) Copyright Department of Computer Science,
 * Australian National University. 2002
 */
package org.mmtk.plan;

import org.mmtk.policy.CopySpace;
import org.mmtk.policy.ImmortalSpace;
import org.mmtk.policy.TreadmillSpace;
import org.mmtk.policy.TreadmillLocal;
import org.mmtk.utility.alloc.AllocAdvice;
import org.mmtk.utility.alloc.Allocator;
import org.mmtk.utility.alloc.BumpPointer;
import org.mmtk.utility.CallSite;
import org.mmtk.utility.Conversions;
import org.mmtk.utility.heap.*;
import org.mmtk.utility.Log;
import org.mmtk.utility.scan.*;
import org.mmtk.utility.Options;
import org.mmtk.vm.Assert;
import org.mmtk.vm.Plan;

import org.vmmagic.unboxed.*;
import org.vmmagic.pragma.*;

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
 * $Id$
 *
 * @author <a href="http://cs.anu.edu.au/~Steve.Blackburn">Steve Blackburn</a>
 * @author Perry Cheng
 * @author <a href="http://www.cs.ukc.ac.uk/~rej">Richard Jones</a>
 *
 * @version $Revision$
 * @date $Date$
 */
public class SemiSpaceGCSpy extends SemiSpaceBase implements Uninterruptible {

  /****************************************************************************
   *
   * Class variables
   */
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
  }

  /**
   * Constructor
   */
  public SemiSpaceGCSpy() {
    objectMap = new ObjectMap();
  }

  /**
   * The boot method is called early in the boot process before any
   * allocation.
   */
  public static final void boot() throws InterruptiblePragma {
    objectMap.boot();
    
    SemiSpaceBase.boot();
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
    throws InterruptiblePragma {
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
  public static final void releaseVMResource(Address start, Extent bytes) {
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
  public static final void acquireVMResource(Address start, Address end, Extent bytes) {
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
    
    Address addr = Address.zero();
    while (objectMap.hasNext()) {
      addr = objectMap.next();
      if (Assert.VERIFY_ASSERTIONS) Assert._assert(VMResource.refInVM(addr));
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
   * Perform post-allocation actions.  For many allocators none are
   * required.
   *
   * @param object The newly allocated object
   * @param typeRef The type reference for the instance being created
   * @param bytes The size of the space to be allocated (in bytes)
   * @param allocator The allocator number to be used for this allocation
   */
  public final void postAlloc(Address object, Address typeRef, int bytes,
                              int allocator)
    throws InlinePragma {
    switch (allocator) {
    case  DEFAULT_SPACE:
      // In principle, taxing the allocator is undesirable and only
      // necessary because it is not possible to sweep through the
      // heap.
      if (Plan.WITH_GCSPY) 
	if (GCSpy.getGCSpyPort() != 0)
	  objectMap.alloc(object);
      return;
    case IMMORTAL_SPACE: ImmortalSpace.postAlloc(object); return;
    case LOS_SPACE: losSpace.initializeHeader(object); return;
    default:
      if (Assert.VERIFY_ASSERTIONS) Assert.fail("No such allocator"); 
    }
  }

  /**
   * Allocate space for copying an object (this method <i>does not</i>
   * copy the object, it only allocates space)
   *
   * @param original A reference to the original object
   * @param bytes The size of the space to be allocated (in bytes)
   * @param align The requested alignment.
   * @param offset The alignment offset.
   * @return The address of the first byte of the allocated region
   */
  public final Address allocCopy(Address original, int bytes, 
                                    int align, int offset) 
    throws InlinePragma {
    if (Assert.VERIFY_ASSERTIONS) Assert._assert(bytes <= LOS_SIZE_THRESHOLD);

    // Knock copied objects out of the object map so we can see what's left
    // (i.e. garbage) in fromspace after the collection.
    if (Plan.WITH_GCSPY) 
      if (GCSpy.getGCSpyPort() != 0) {
        objectMap.dealloc(original);
      }
    Address result = ss.alloc(bytes, align, offset);
    return result;
  }

  /**  
   * Perform any post-copy actions.
   *
   * @param object The newly allocated object
   * @param typeRef the type reference for the instance being created
   * @param bytes The size of the space to be allocated (in bytes)
   */
  public final void postCopy(Address object, Address typeRef, int bytes)
  throws InlinePragma {
    CopySpace.clearGCBits(object);

    if (Plan.WITH_GCSPY) 
      if (GCSpy.getGCSpyPort() != 0)
	objectMap.alloc(object);
  }
}
