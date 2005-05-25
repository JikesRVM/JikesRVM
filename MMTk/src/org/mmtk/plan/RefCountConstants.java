/*
 * (C) Copyright Department of Computer Science,
 * Australian National University. 2002
 */

package org.mmtk.plan;

import org.mmtk.policy.RefCountSpace;
import org.vmmagic.pragma.*;

/**
 * Reference Counting constants. 
 *
 * @author Daniel Frampton
 * @author Robin Garner
 * @version $Revision$
 * @date $Date$
 */
public class RefCountConstants extends RefCountBaseConstants implements Uninterruptible {
  public static int GC_HEADER_BITS_REQUIRED() throws InlinePragma {
    return RefCountSpace.LOCAL_GC_BITS_REQUIRED;
  }
}
