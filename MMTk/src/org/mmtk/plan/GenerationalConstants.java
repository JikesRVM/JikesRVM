/*
 * (C) Copyright Department of Computer Science,
 * Australian National University. 2002
 */
package org.mmtk.plan;

import org.mmtk.policy.CopySpace;
import org.vmmagic.pragma.*;

/**
 * Generational constants.
 *
 * @author Daniel Frampton
 * @author Robin Garner
 * @version $Revision$
 * @date $Date$
 */
public class GenerationalConstants extends StopTheWorldGCConstants 
  implements Uninterruptible {
  public static boolean GENERATIONAL_HEAP_GROWTH() throws InlinePragma { 
    return true; 
  }
  public static boolean MOVES_OBJECTS() throws InlinePragma { 
    return true; 
  }
  public static int GC_HEADER_BITS_REQUIRED() throws InlinePragma { 
    return CopySpace.LOCAL_GC_BITS_REQUIRED;
  }

  public static int GC_HEADER_WORDS_REQUIRED() throws InlinePragma { 
    return CopySpace.GC_HEADER_WORDS_REQUIRED;
  }
  public static boolean NEEDS_WRITE_BARRIER() throws InlinePragma { 
    return true; 
  }
}
