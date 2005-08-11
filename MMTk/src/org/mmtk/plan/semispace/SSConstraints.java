/*
 * (C) Copyright Department of Computer Science,
 * Australian National University. 2002
 */
package org.mmtk.plan.semispace;

import org.mmtk.plan.*;
import org.mmtk.policy.CopySpace;
import org.vmmagic.pragma.*;

/**
 * SemiSpace common constants.
 *
 * $Id$
 *
 * @author <a href="http://cs.anu.edu.au/~Steve.Blackburn">Steve Blackburn</a>
 * @author Perry Cheng
 * @author Robin Garner
 * @author Daniel Frampton
 *
 * @version $Revision$
 * @date $Date$
 */
public class SSConstraints extends StopTheWorldConstraints
  implements Uninterruptible {

  public boolean movesObjects() { return true; }

  public int gcHeaderBits() { return CopySpace.LOCAL_GC_BITS_REQUIRED; }

  public int gcHeaderWords() { return CopySpace.GC_HEADER_WORDS_REQUIRED; }

}
