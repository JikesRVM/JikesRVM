/*
 * (C) Copyright Department of Computer Science,
 * Australian National University. 2002
 *
 * (C) Copyright Department of Computer Science,
 * University of Massachusetts, Amherst. 2003
 */
package org.mmtk.plan.semispace.gctrace;

import org.mmtk.plan.semispace.SSConstraints;

import org.vmmagic.pragma.*;

/**
 * GCTrace constants.
 *
 * @author <a href="http://cs.anu.edu.au/~Steve.Blackburn">Steve Blackburn</a>
 * @author Perry Cheng
 * @author Daniel Frampton
 * @author Robin Garner
 * @author <a href="http://www-ali.cs.umass.edu/~hertz">Matthew Hertz</a>
 *
 * @version $Revision$
 * @date $Date$
 */
public class GCTraceConstraints extends SSConstraints implements Uninterruptible {
  public boolean needsWriteBarrier() { return true; }

  public boolean generateGCTrace() { return true; }
}
