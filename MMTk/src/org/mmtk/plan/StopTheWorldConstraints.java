/*
 * (C) Copyright Department of Computer Science,
 * Australian National University. 2005
 */
package org.mmtk.plan;

import org.vmmagic.pragma.*;

/**
 * Constraints specific to Stop-the-world collectors.
 *
 * $Id$
 *
 * @author Perry Cheng
 * @author <a href="http://cs.anu.edu.au/~Steve.Blackburn">Steve Blackburn</a>
 * @author Daniel Frampton
 * @author Robin Garner
 */
public abstract class StopTheWorldConstraints extends PlanConstraints
  implements Uninterruptible {
}
