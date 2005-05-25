/*
 * (C) Copyright Department of Computer Science,
 * Australian National University. 2002
 */

package org.mmtk.plan;

import org.mmtk.policy.RefCountSpace;
import org.vmmagic.pragma.*;

/**
 * Common Reference Counting constants. 
 *
 * @author Daniel Frampton
 * @author Robin Garner
 * @version $Revision$
 * @date $Date$
 */
public class RefCountBaseConstants extends StopTheWorldGCConstants
  implements Uninterruptible {

  public static boolean NEEDS_WRITE_BARRIER() throws InlinePragma { 
    return true; 
  }
  public static boolean SUPPORTS_PARALLEL_GC() throws InlinePragma { 
   return false; 
  }
  public static int GC_HEADER_WORDS_REQUIRED() throws InlinePragma { 
    return RefCountSpace.GC_HEADER_WORDS_REQUIRED;
  }
}
