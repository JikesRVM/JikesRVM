/*
 * (C) Copyright Department of Computer Science,
 * Australian National University. 2004
 */
//$Id$
package org.mmtk.utility.alloc;

import org.vmmagic.unboxed.*;
import org.vmmagic.pragma.*;

/**
 * Callbacks from BumpPointer during a linear scan are dispatched through
 * a subclass of this object.
 *
 * @author Daniel Frampton 
 * @version $Revision$
 * @date    $Date$
 */
abstract public class LinearScan implements Uninterruptible {
  /**
   * Scan an object. 
   *
   * @param object The object to scan
   */
  abstract public void scan(ObjectReference object);
}
