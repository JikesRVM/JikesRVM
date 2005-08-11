/*
 * (C) Copyright Department of Computer Science,
 * Australian National University. 2002
 */
package org.mmtk.plan.semispace.gcspy;

import org.mmtk.plan.semispace.SSConstraints;
import org.vmmagic.pragma.*;

/**
 * Semi space GCspy constants.
 *
 * $Id$
 *
 * @author <a href="http://cs.anu.edu.au/~Steve.Blackburn">Steve Blackburn</a>
 * @author Perry Cheng
 * @author Daniel Frampton
 * @author Robin Garner
 * @author <a href="http://www.cs.ukc.ac.uk/~rej">Richard Jones</a>
 *
 * @version $Revision$
 * @date $Date$
 */
public class SSGCspyConstraints extends SSConstraints
  implements Uninterruptible {

  public boolean needsLinearScan() { return true; }
  
  public boolean withGCspy() { return true; }
}
