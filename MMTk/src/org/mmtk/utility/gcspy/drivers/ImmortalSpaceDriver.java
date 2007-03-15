/*
 * This file is part of MMTk (http://jikesrvm.sourceforge.net).
 * MMTk is distributed under the Common Public License (CPL).
 * A copy of the license is included in the distribution, and is also
 * available at http://www.opensource.org/licenses/cpl1.0.php
 *
 * (C) Copyright Richard Jones, 2005-6
 * Computing Laboratory, University of Kent at Canterbury
 */
package org.mmtk.utility.gcspy.drivers;

import org.mmtk.policy.Space;
import org.mmtk.utility.scan.MMType;
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
 *
 *
 * @author <a href="http://www.ukc.ac.uk/people/staff/rej">Richard Jones</a>
 * @author Hanspeter Johner
 */
@Uninterruptible public class ImmortalSpaceDriver extends LinearSpaceDriver {

  private static final boolean DEBUG = false;

  // Instance variables
  private AbstractDriver[] registeredDrivers;

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
  }

  /**
   * Get the name of this driver type.
   * @return The name, "MMTk ImmortalSpaceDriver" for this driver.
   */
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
  public void scan(ObjectReference object, boolean total) {
    // get type of object
    MMType type = VM.objectModel.getObjectType(object);
    Address addr = object.toAddress();
    
    if (subspace.addressInRange(addr)) {
      // Address in Range, locate references
      int references = type.getReferences(object);
      for (int i = 0; i < references; i++) {
        Address target = type.getSlot(object, i).loadAddress();
        // notify registered drivers
        for (int j = 0; j < this.registeredDrivers.length; j++) 
          registeredDrivers[j].handleReferenceFromImmortalSpace(target);
      }
      // Work done, call scan() in superclass for default handling
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

}
