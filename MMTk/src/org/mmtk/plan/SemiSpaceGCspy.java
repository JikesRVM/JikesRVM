/*
 * (C) Copyright Department of Computer Science,
 * Australian National University. 2002
 */
package org.mmtk.plan;

import org.mmtk.policy.Space;
import org.mmtk.policy.CopySpace;
import org.mmtk.utility.alloc.BumpPointer;
import org.mmtk.utility.alloc.LinearScan;
import org.mmtk.utility.gcspy.drivers.ContiguousSpaceDriver;
import org.mmtk.utility.gcspy.drivers.TreadmillDriver;
import org.mmtk.utility.gcspy.GCspy;
import org.mmtk.utility.heap.*;
import org.mmtk.utility.Log;
import org.mmtk.utility.scan.*;
import org.mmtk.utility.options.*;
import org.mmtk.vm.Assert;
import org.mmtk.vm.gcspy.ServerInterpreter;

import org.vmmagic.unboxed.*;
import org.vmmagic.pragma.*;

/**
 * This class extends a simple semi-space collector to instrument it for
 * GCspy. It makes use of Daniel Frampton's LinearScan patches to allow
 * CopySpaces to be scanned linearly (rather than recording allocations
 * with an ObjectMap as in prior versions.<p>
 *
 * See the Jones & Lins GC book, section 2.2 for an overview of the basic
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
public class SemiSpaceGCspy extends SemiSpaceBase implements Uninterruptible {

  /****************************************************************************
   *
   * Class variables
   */

  // use a slightly more expensive hashing operation (no other effect) 
  public static final boolean NEEDS_LINEAR_SCAN = true;

  // The event, BEFORE_COLLECTION or AFTER_COLLECTION
  private static final int BEFORE_COLLECTION = 0;
  private static final int SEMISPACE_COPIED = BEFORE_COLLECTION + 1;
  private static final int AFTER_COLLECTION = SEMISPACE_COPIED + 1;
  private static int gcspyEvent_ = BEFORE_COLLECTION;

  // The specific drivers for this collector
  private static ContiguousSpaceDriver ss0Driver;
  private static ContiguousSpaceDriver ss1Driver;
  private static ContiguousSpaceDriver immortalDriver; 
  private static TreadmillDriver losDriver;

  private static int nextServerSpaceId = 0;	// ServerSpace IDs must be unique



  /****************************************************************************
   *
   * GCSPY
   */
  static {
    GCspy.createOptions();
  }

  /**
   * Start the server and wait if necessary
   * 
   * WARNING: allocates memory
   * @param wait Whether to wait
   * @param port The port to talk to the GCspy client (e.g. visualiser)
   */
  public static final void startGCspyServer(int port, boolean wait)
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
    int tilesize = GCspy.gcspyTilesize.getValue(); 
    // TODO What if this is too small (i.e. too many tiles for GCspy buffer)
    // TODO stop the GCspy spaces in the visualiser from fluctuating in size
    // so much as we resize them.
    ss0Driver = new ContiguousSpaceDriver
                        ("Semispace 0 Space", 
			 copySpace0,
			 tilesize,
			 true);
    ss1Driver = new ContiguousSpaceDriver
                        ("Semispace 1 Space", 
			 copySpace1,
			 tilesize,
			 false);
    immortalDriver = new ContiguousSpaceDriver
                        ("Immortal Space", 
			 immortalSpace,
			 tilesize,
			 false);
    losDriver = new TreadmillDriver
                        ("Large Object Space", 
			 loSpace,
			 tilesize,
			 LOS_SIZE_THRESHOLD,
			 false);

    //Log.write("SemiServerInterpreter initialised\n");
    
    gcspyEvent_ = BEFORE_COLLECTION;

    // Start the server
    ServerInterpreter.startServer(wait);
  }



  /****************************************************************************
   *
   * Data gathering
   */

  /**
   * Perform operations with <i>global</i> scope in preparation for a
   * collection.  This is called by <code>StopTheWorld</code>, which will
   * ensure that <i>only one thread</i> executes this.<p>
   */
  protected void globalPrepare() {
    gcspyGatherData(BEFORE_COLLECTION);
    super.globalPrepare();
  }

  /**
   * Perform operations with <i>global</i> scope to clean up at the
   * end of a collection.  This is called by <code>StopTheWorld</code>,
   * which will ensure that <i>only one</i> thread executes this.<p>
   */
  protected void globalRelease() {
    super.globalRelease();
    gcspyGatherData(AFTER_COLLECTION);    
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
   * Gather data for GCspy
   * This method sweeps the semispace under consideration to gather data.
   * Used space can obviously be discovered in constant time simply by comparing
   * the start and the end addresses of the semispace, but per-object 
   * information needs to be gathered by sweeping through the space.
   * 
   * @param event The event, either BEFORE_COLLECTION, SEMISPACE_COPIED or AFTER_COLLECTION
   */
  private void gcspyGatherData(int event) {
    // Port = 0 means no gcspy
    if (GCspy.getGCspyPort() == 0)
      return;
    
    if (ServerInterpreter.shouldTransmit(event)) {		
      ServerInterpreter.startCompensationTimer();
      
      // -- Handle the semispace collector --
      // BEFORE_COLLECTION: hi has not yet been flipped by globalPrepare()
      // SEMISPACE_COPIED:  hi has been flipped
      // AFTER_COLLECTION:  hi has been flipped
      CopySpace scannedSpace = null;
      CopySpace otherSpace = null;
      ContiguousSpaceDriver driver = null;
      ContiguousSpaceDriver otherDriver = null;

      /*
      if (hi) Log.write("\nExamining Highspace (", event);
      else    Log.write("\nExamining Lowspace (", event);
      Log.write(")");
      reportSpaces();
      */

      if (hi) {
        scannedSpace = copySpace1;
        otherSpace = copySpace0;
	driver = ss1Driver;
	otherDriver = ss0Driver;
      } else { 
	scannedSpace = copySpace0;
        otherSpace = copySpace1;
        driver = ss0Driver;
	otherDriver = ss1Driver;
      }	
      gcspyGatherData(driver, scannedSpace, ss);
      
      if (event == AFTER_COLLECTION) {
        otherDriver.zero();
	// FIXME As far as I can see, release() only resets a CopySpace's
	// cursor (and optionally zeroes or mprotects the pages); it doesn't
	// make them available to other spaces.
	// If it does release pages then need to change 
	// ContiguousSpaceDriver.setRange
	Address start = otherSpace.getStart();
	otherDriver.setRange(start, start);
      }
      
      // -- Handle the LargeObjectSpace --
      losDriver.zero();
      los.gcspyGatherData(event, losDriver, false); // read fromspace
      if (event == SEMISPACE_COPIED)
        los.gcspyGatherData(event, losDriver, true);  // read tospace
      
      // -- Handle the immortal space --
      immortalDriver.zero(); 
      gcspyGatherData(immortalDriver, immortalSpace, immortal);

      // Transmit the data
      ServerInterpreter.stopCompensationTimer();	
      driver.finish(event);
      if (event == AFTER_COLLECTION) otherDriver.finish(event);
      immortalDriver.finish(event);
      losDriver.finish(event);

    } else {
      //Log.write("not transmitting...");
    }
    ServerInterpreter.serverSafepoint(event);		
  }

  /**
   * Gather data for GCspy
   * @param the semispace number
   * @param space the ContiguousSpace
   * @param bp the BumpPointer for this space
   */
  private void gcspyGatherData(ContiguousSpaceDriver driver, 
                               Space space, BumpPointer bp) {
    if (Assert.VERIFY_ASSERTIONS) 
      Assert._assert(bp.getSpace() == space, "Space / BumpPointer mismatch");
    Address start = space.getStart();
    driver.zero();
    driver.setRange(start, bp.getCursor());
    LinearScan scanner = driver.getScanner();
    bp.linearScan(scanner);
  }


  /**
   * Called by GCspy drivers 
   * @return a unique space ID
   */
  public static int getNextServerSpaceId() { return nextServerSpaceId++; }


  /**
   * Report information on the semispaces
   */
  private static void reportSpaces() {
    Log.write("  Low semispace:  "); Log.write(copySpace0.getStart());
    //Log.writeln("-", copySpace0.getCursor());
    Log.write("  High semispace: "); Log.write(copySpace1.getStart());
    //Log.writeln("-", copySpace1.getCursor());
  }

}
