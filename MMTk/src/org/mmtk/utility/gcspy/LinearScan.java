/*
 * This file is part of MMTk (http://jikesrvm.sourceforge.net).
 * MMTk is distributed under the Common Public License (CPL).
 * A copy of the license is included in the distribution, and is also
 * available at http://www.opensource.org/licenses/cpl1.0.php
 *
 * (C) Copyright Richard Jones, 2004
 * Computing Laboratory, University of Kent at Canterbury
 * All rights reserved.
 */
package org.mmtk.utility.gcspy;

import org.mmtk.utility.gcspy.drivers.AbstractDriver;

import org.vmmagic.unboxed.*;
import org.vmmagic.pragma.*;

/**
 * This class is only necessary because we cannot implement
 * org.mmtk.utility.alloc.LinearScan as an interface since the invokeinterface
 * bytecode is forbidden in uninterruptible code.
 * 
 * $Id$
 * 
 * @author <a href="http://www.ukc.ac.uk/people/staff/rej">Richard Jones</a>
 * @version $Revision$
 * @date $Date$
 */
public class LinearScan extends org.mmtk.utility.alloc.LinearScan
  implements Uninterruptible {

  private final AbstractDriver driver;

  /**
   * Create a new scanner.
   * @param d The GCspy driver that provides the callback.
   */
  public LinearScan (AbstractDriver d) { driver = d; }

  /**
   * Scan an object. The object reference is passed to the scan method of the 
   * GCspy driver registered with this scanner.
   * @param obj The object to scan.
   */
  public void scan(ObjectReference obj) { driver.scan(obj);  }
}

