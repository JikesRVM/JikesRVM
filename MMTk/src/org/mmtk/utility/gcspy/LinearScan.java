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
package org.mmtk.utility.gcspy;

import org.mmtk.utility.gcspy.drivers.AbstractDriver;

import org.vmmagic.unboxed.*;
import org.vmmagic.pragma.*;

/**
 * This class is only necessary because we cannot implement
 * org.mmtk.utility.alloc.LinearScan as an interface since the invokeinterface
 * bytecode is forbidden in uninterruptible code.
 */
@Uninterruptible public class LinearScan extends org.mmtk.utility.alloc.LinearScan {

  private final AbstractDriver driver;

  /**
   * Create a new scanner.
   * @param d The GCspy driver that provides the callback.
   */
  public LinearScan(AbstractDriver d) { driver = d; }

  /**
   * Scan an object. The object reference is passed to the scan method of the
   * GCspy driver registered with this scanner.
   * @param obj The object to scan.
   */
  public void scan(ObjectReference obj) { driver.scan(obj);  }
}

