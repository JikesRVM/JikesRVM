/*
 * This file is part of MMTk (http://jikesrvm.sourceforge.net).
 * MMTk is distributed under the Common Public License (CPL).
 * A copy of the license is included in the distribution, and is also
 * available at http://www.opensource.org/licenses/cpl1.0.php
 *
 * (C) Copyright Richard Jones, 2006
 * Computing Laboratory, University of Kent at Canterbury
 *
 * $Id$
 */

package org.mmtk.plan;

import org.mmtk.policy.ImmortalSpace;

/**
 * This interface provides an immortal allocation space for GCspy objects.
 * @author Richard Jones
 */
public interface GCspyPlan {
  
  public static final int GCSPY_MB = 4; // 1 chunk
  
  /**
   * Any GCspy objects allocated after booting are allocated 
   * in a separate immortal space. 
   */
  public static final ImmortalSpace gcspySpace = 
      new ImmortalSpace("gcspy", Plan.DEFAULT_POLL_FREQUENCY, GCSPY_MB);
  
  /** The descriptor for the GCspy allocation space */
  public static final int GCSPY = gcspySpace.getDescriptor();
}
