/*
 * This file is part of MMTk (http://jikesrvm.sourceforge.net).
 * MMTk is distributed under the Common Public License (CPL).
 * A copy of the license is included in the distribution, and is also
 * available at http://www.opensource.org/licenses/cpl1.0.php
 *
 * (C) Copyright Department of Computer Science,
 * Australian National University. 2002
 *
 * (C) Copyright Department of Computer Science,
 * University of Massachusetts, Amherst. 2003
 *
 * $Id$
 */
package org.mmtk.plan.semispace.gctrace;

import org.mmtk.plan.semispace.SSConstraints;

import org.vmmagic.pragma.*;

/**
 * GCTrace constants.
 * 
 * @author Steve Blackburn
 * @author Perry Cheng
 * @author Daniel Frampton
 * @author Robin Garner
 * @author <a href="http://www-ali.cs.umass.edu/~hertz">Matthew Hertz</a>
 * 
 * @version $Revision$
 * @date $Date$
 */
@Uninterruptible public class GCTraceConstraints extends SSConstraints {
  public boolean needsWriteBarrier() { return true; }

  public boolean generateGCTrace() { return true; }
}
