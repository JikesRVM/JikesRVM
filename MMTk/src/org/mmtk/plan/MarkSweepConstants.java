/*
 * (C) Copyright Department of Computer Science,
 * Australian National University. 2005
 */
package org.mmtk.plan;

import org.mmtk.policy.MarkSweepSpace;
import org.vmmagic.pragma.*;

/**
 * This class contains all mark sweep constants.
 *
 * @author Daniel Frampton 
 * @author Robin Garner
 *
 * @version $Revision$
 * @date $Date$
 */
public class MarkSweepConstants extends StopTheWorldGCConstants implements Uninterruptible {
  public static int GC_HEADER_BITS_REQUIRED() throws InlinePragma {
    return MarkSweepSpace.LOCAL_GC_BITS_REQUIRED;
  }

  public static int GC_HEADER_WORDS_REQUIRED() throws InlinePragma { 
    return MarkSweepSpace.GC_HEADER_WORDS_REQUIRED;
  }
}
