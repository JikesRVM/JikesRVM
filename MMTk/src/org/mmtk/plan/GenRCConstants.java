/*
 * (C) Copyright Department of Computer Science,
 * Australian National University. 2002
 */

package org.mmtk.plan;

import org.mmtk.policy.CopySpace;
import org.vmmagic.pragma.*;

/**
 * Generational Reference Counting constants
 *
 * @author Daniel Frampton
 * @author Robin Garner
 * @version $Revision$
 * @date $Date$
 */
public class GenRCConstants extends RefCountBaseConstants implements Uninterruptible {
  public static boolean GENERATIONAL_HEAP_GROWTH() throws InlinePragma { 
    return true; 
  }

  public static boolean MOVES_OBJECTS() throws InlinePragma { 
    return true; 
  }

  public static int GC_HEADER_BITS_REQUIRED() throws InlinePragma {
    return CopySpace.LOCAL_GC_BITS_REQUIRED;
  }

  public static boolean STEAL_NURSERY_GC_HEADER() throws InlinePragma { 
    return false; 
  }
}
