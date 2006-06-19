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
 * @author Steve Blackburn
 * @author Daniel Frampton
 * @author Robin Garner
 */
public abstract class StopTheWorldConstraints extends PlanConstraints
  implements Uninterruptible {
}
