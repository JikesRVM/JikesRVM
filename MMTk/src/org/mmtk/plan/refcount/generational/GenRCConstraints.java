/*
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
 * @author <a href="http://cs.anu.edu.au/~Steve.Blackburn">Steve Blackburn</a>
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
