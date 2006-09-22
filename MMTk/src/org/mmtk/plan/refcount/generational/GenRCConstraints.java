/*
 * This file is part of MMTk (http://jikesrvm.sourceforge.net).
 * MMTk is distributed under the Common Public License (CPL).
 * A copy of the license is included in the distribution, and is also
 * available at http://www.opensource.org/licenses/cpl1.0.php
 *
 * (C) Copyright Department of Computer Science,
 * Australian National University. 2002
 */

package org.mmtk.plan.refcount.generational;

import org.mmtk.policy.CopySpace;
import org.mmtk.plan.refcount.*;
import org.vmmagic.pragma.*;

/**
 * Generational Reference Counting constants
 * 
 * $Id$
 * 
 * @author Steve Blackburn
 * @author Daniel Frampton
 * @author Robin Garner
 * @version $Revision$
 * @date $Date$
 */
public class GenRCConstraints extends RCBaseConstraints
  implements Uninterruptible {

  public boolean generational() { return true; }

  public boolean movesObjects() { return true; }

  public int gcHeaderBits() { return CopySpace.LOCAL_GC_BITS_REQUIRED; }

  public boolean stealNurseryGcHeader() { return false; }
}
