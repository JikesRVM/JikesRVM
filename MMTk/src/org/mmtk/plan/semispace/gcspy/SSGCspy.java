/*
 * (C) Copyright Department of Computer Science,
 * Australian National University. 2002
 */
package org.mmtk.plan.semispace.gcspy;

import org.mmtk.plan.semispace.SS;
import org.mmtk.utility.gcspy.drivers.ContiguousSpaceDriver;
import org.mmtk.utility.gcspy.drivers.TreadmillDriver;
import org.mmtk.utility.gcspy.GCspy;
import org.mmtk.utility.Log;
import org.mmtk.utility.options.Options;

import org.mmtk.vm.gcspy.ServerInterpreter;

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
 * @author Daniel Frampton
 * @author Robin Garner
 * @author <a href="http://www.cs.ukc.ac.uk/~rej">Richard Jones</a>
 *
 * @version $Revision$
 * @date $Date$
 */
public class SSGCspy extends SS implements Uninterruptible {

  /****************************************************************************
   *
   * Class variables
   */


  // The event, BEFORE_COLLECTION or AFTER_COLLECTION
  static final int BEFORE_COLLECTION = 0;
  static final int SEMISPACE_COPIED = BEFORE_COLLECTION + 1;
  static final int AFTER_COLLECTION = SEMISPACE_COPIED + 1;
  static int gcspyEvent_ = BEFORE_COLLECTION;

  // The specific drivers for this collector
  static ContiguousSpaceDriver ss0Driver;
  static ContiguousSpaceDriver ss1Driver;
  static ContiguousSpaceDriver immortalDriver;
  static TreadmillDriver losDriver;

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
    int tilesize = Options.gcspyTileSize.getValue();
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
