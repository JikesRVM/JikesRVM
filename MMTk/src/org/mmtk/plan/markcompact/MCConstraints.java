/*
 * This file is part of MMTk (http://jikesrvm.sourceforge.net).
 * MMTk is distributed under the Common Public License (CPL).
 * A copy of the license is included in the distribution, and is also
 * available at http://www.opensource.org/licenses/cpl1.0.php
 *
 * (C) Copyright Department of Computer Science,
 * Australian National University. 2005
 */
package org.mmtk.plan.markcompact;

import org.mmtk.plan.StopTheWorldConstraints;

import org.mmtk.policy.MarkCompactSpace;

import org.vmmagic.pragma.*;

/**
 * This class and its subclasses communicate to the host VM/Runtime
 * any features of the selected plan that it needs to know.  This is
 * separate from the main Plan/PlanLocal class in order to bypass any
 * issues with ordering of static initialization.
 * 
 * $Id$
 * 
 * @author Daniel Frampton
 * @version $Revision$
 * @date $Date$
 */
public class MCConstraints extends StopTheWorldConstraints
  implements Uninterruptible {

  public boolean movesObjects() { return true; }

  public boolean needsForwardAfterLiveness() { return true; }

  public boolean needsImmortalTypeInfo() { return true; }

  public boolean needsLinearScan() { return true; }

  public int gcHeaderBits() { return MarkCompactSpace.LOCAL_GC_BITS_REQUIRED; }

  public int gcHeaderWords() { return MarkCompactSpace.GC_HEADER_WORDS_REQUIRED; }
}
