/*
 * (C) Copyright Department of Computer Science,
 * Australian National University. 2002
 *
 * (C) Copyright Department of Computer Science,
 * University of Massachusetts, Amherst. 2003
 */
package org.mmtk.plan;

import org.vmmagic.pragma.*;

/**
 * GCTrace constants.
 *
 * @author Daniel Frampton 
 * @author Robin Garner 
 *
 * @version $Revision$
 * @date $Date$
 */
public class GCTraceConstants extends SemiSpaceBaseConstants implements Uninterruptible {
  public static boolean NEEDS_WRITE_BARRIER() throws InlinePragma { 
    return true; 
  }

  public static boolean GENERATE_GC_TRACE() throws InlinePragma { 
    return true; 
  }
}
