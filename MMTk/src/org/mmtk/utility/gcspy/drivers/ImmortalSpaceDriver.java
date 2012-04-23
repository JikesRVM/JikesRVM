/*
 *  This file is part of the Jikes RVM project (http://jikesrvm.org).
 *
 *  This file is licensed to You under the Eclipse Public License (EPL);
 *  You may not use this file except in compliance with the License. You
 *  may obtain a copy of the License at
 *
 *      http://www.opensource.org/licenses/eclipse-1.0.php
 *
 *  See the COPYRIGHT.txt file distributed with this work for information
 *  regarding copyright ownership.
 */
package org.mmtk.utility.gcspy.drivers;

import org.mmtk.plan.TransitiveClosure;
import org.mmtk.policy.Space;
import org.mmtk.utility.Log;
import org.mmtk.vm.gcspy.ServerInterpreter;
import org.mmtk.vm.VM;

import org.vmmagic.unboxed.*;
import org.vmmagic.pragma.*;

/**
 * GCspy driver for the contiguous MMTk ImmortalSpace.
 * Adds features for the Immortal space.
 * <p>
 *
 * This class extends LinearSpaceDriver, a simple driver for contiguous MMTk spaces
 * such as CopySpace and ImmortalSpace.
 */
@Uninterruptible public class ImmortalSpaceDriver extends LinearSpaceDriver {

  private static final boolean DEBUG = false;

  // Instance variables
  private AbstractDriver[] registeredDrivers;
  private ImmortalSpaceDriver.Closure closure;

  /**
   * Create a new driver for an immortal Contiguous MMTk space.
   *
   * @param server The GCspy ServerInterpreter
   * @param spaceName The name of this GCspy space
   * @param mmtkSpace The MMTk space
   * @param blockSize The tile size
   * @param mainSpace Is this the main space?
   */
  public ImmortalSpaceDriver(
                     ServerInterpreter server,
                     String spaceName,
                     Space mmtkSpace,
                     int blockSize,
                     boolean mainSpace) {

    super(server, spaceName, mmtkSpace, blockSize, mainSpace);

    if (DEBUG) {
      Log.write("ImmortalSpaceDriver for "); Log.write(spaceName);
      Log.write(", blocksize="); Log.write(blockSize);
      Log.write(", start="); Log.write(mmtkSpace.getStart());
      Log.write(", extent="); Log.write(mmtkSpace.getExtent());
      Log.write(", maxTileNum="); Log.writeln(maxTileNum);
    }

    // initially no registered drivers for reference notification
    registeredDrivers = new AbstractDriver[0];

    closure = new ImmortalSpaceDriver.Closure();
  }

  /**
   * Get the name of this driver type.
   * @return The name, "MMTk ImmortalSpaceDriver" for this driver.
   */
  @Override
  protected String getDriverName() {
    return "MMTk ImmortalSpaceDriver";
  }

  /**
   * Update the tile statistics. <br>
   * This method overrides <code> scan </code> iin its superclass to
   * add immortal space features.
   *
   * @param object The current object
   * @param total Whether to accumulate the values
   */
  @Override
  public void scan(ObjectReference object, boolean total) {
    Address addr = object.toAddress();

    if (subspace.addressInRange(addr)) {
      VM.scanning.scanObject(closure, object);
      super.scan(object, total);
    }
  }

  /**
   * Register a set of AbstractDriver instances to be notified about direct references.
   *
   * @param drivers The array of driver objects.
   */
  public void registerDriversForReferenceNotification(AbstractDriver[] drivers) {
    this.registeredDrivers = drivers;
  }

  /**
   * Used to visit the edges in the immortal object.
   */
  @Uninterruptible
  private class Closure extends TransitiveClosure {
    /**
     * Process an edge.
     */
    @Override
    public void processEdge(ObjectReference source, Address slot) {
      // Address in Range, locate references
      Address target = slot.loadAddress();
      // notify registered drivers
      for (int j = 0; j < registeredDrivers.length; j++) {
        registeredDrivers[j].handleReferenceFromImmortalSpace(target);
      }
    }
  }
}
