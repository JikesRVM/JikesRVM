/*
 * (C) Copyright Department of Computer Science,
 * Australian National University. 2005
 */
package org.mmtk.plan.marksweep;

import org.mmtk.plan.StopTheWorldConstraints;

import org.mmtk.policy.MarkSweepSpace;

import org.vmmagic.pragma.*;

/**
 * This class and its subclasses communicate to the host VM/Runtime
 * any features of the selected plan that it needs to know.  This is
 * separate from the main Plan/PlanLocal class in order to bypass any
 * issues with ordering of static initialization.
 *
 * $Id$
 *
 * @author <a href="http://cs.anu.edu.au/~Steve.Blackburn">Steve Blackburn</a>
 * @author Daniel Frampton
 * @author Robin Garner
 */
public class MSConstraints extends StopTheWorldConstraints
  implements Uninterruptible {

  public int gcHeaderBits() { return MarkSweepSpace.LOCAL_GC_BITS_REQUIRED; }

  public int gcHeaderWords() { return MarkSweepSpace.GC_HEADER_WORDS_REQUIRED; }
}
