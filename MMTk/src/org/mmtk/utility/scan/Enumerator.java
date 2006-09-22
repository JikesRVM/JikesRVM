/*
 * This file is part of MMTk (http://jikesrvm.sourceforge.net).
 * MMTk is distributed under the Common Public License (CPL).
 * A copy of the license is included in the distribution, and is also
 * available at http://www.opensource.org/licenses/cpl1.0.php
 *
 * (C) Copyright Department of Computer Science,
 * Australian National University. 2003
 */
//$Id$
package org.mmtk.utility.scan;

import org.vmmagic.unboxed.*;
import org.vmmagic.pragma.*;

/**
 * Callbacks from ScanObject to Plan.enumeratePointerLocation are
 * dispatched through an object of this class, so that we have the
 * opportunity to change the behaviour through sub-classing. <p>
 * 
 * @author Robin Garner
 * @version $Revision$
 * @date    $Date$
 */

abstract public class Enumerator implements Uninterruptible {
  abstract public void enumeratePointerLocation(Address location);
}
