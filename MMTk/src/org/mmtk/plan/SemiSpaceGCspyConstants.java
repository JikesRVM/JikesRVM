/*
 * (C) Copyright Department of Computer Science,
 * Australian National University. 2002
 */
package org.mmtk.plan;

import org.vmmagic.pragma.*;

/**
 * Semi space GCspy constants.
 *
 * @author Daniel Frampton 
 * @author Robin Garner
 *
 * @version $Revision$
 * @date $Date$
 */
public class SemiSpaceGCspyConstants extends SemiSpaceConstants implements Uninterruptible {

  public static boolean NEEDS_LINEAR_SCAN() throws InlinePragma { 
    return true; 
  }
  public static boolean WITH_GCSPY() throws InlinePragma { 
    return true; 
  }
}
