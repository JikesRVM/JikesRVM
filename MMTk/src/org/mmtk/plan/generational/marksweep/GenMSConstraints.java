/*
 * (C) Copyright Department of Computer Science,
 * Australian National University. 2005
 */
package org.mmtk.plan.generational.marksweep;

import org.mmtk.plan.generational.GenConstraints;

import org.vmmagic.pragma.*;

/**
 * This class and its subclasses communicate to the host VM/Runtime
 * any features of the selected plan that it needs to know.  This is
 * separate from the main Plan/PlanLocal class in order to bypass any
 * issues with ordering of static initialization.
 * 
 * $Id$
 * 
 * @author Steve Blackburn
 * @author Daniel Frampton
 * @author Robin Garner
 */
public class GenMSConstraints extends GenConstraints
  implements Uninterruptible {

  public boolean needsLinearScan() { return true; } // FIXME: This is a hack to work around bug 1549822
}
