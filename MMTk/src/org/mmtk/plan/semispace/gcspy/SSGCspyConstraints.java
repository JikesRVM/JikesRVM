/*
 * This file is part of MMTk (http://jikesrvm.sourceforge.net).
 * MMTk is distributed under the Common Public License (CPL).
 * A copy of the license is included in the distribution, and is also
 * available at http://www.opensource.org/licenses/cpl1.0.php
 *
 * (C) Copyright Department of Computer Science,
 * Australian National University. 2002
 */
package org.mmtk.plan.semispace.gcspy;

import org.mmtk.plan.semispace.SSConstraints;
import org.vmmagic.pragma.*;

/**
 * Semi space GCspy constants.
 * 
 *
 * @author Steve Blackburn
 * @author Perry Cheng
 * @author Daniel Frampton
 * @author Robin Garner
 * @author <a href="http://www.cs.ukc.ac.uk/~rej">Richard Jones</a>
 * 
 */
@Uninterruptible public class SSGCspyConstraints extends SSConstraints {

  public boolean needsLinearScan() { return true; }

  public boolean withGCspy() { return true; }
}
