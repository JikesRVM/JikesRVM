/*
 * This file is part of MMTk (http://jikesrvm.sourceforge.net).
 * MMTk is distributed under the Common Public License (CPL).
 * A copy of the license is included in the distribution, and is also
 * available at http://www.opensource.org/licenses/cpl1.0.php
 *
 * (C) Copyright Department of Computer Science,
 * Australian National University. 2002
 */

package org.mmtk.plan.refcount;

import org.mmtk.plan.StopTheWorldCollector;
import org.vmmagic.pragma.*;

/**
 * This abstract class implements <i>per-collector thread</i> behavior
 * and state for the <i>RCBase</i> plan, which is the parent class for
 * reference counting collectors.<p>
 * 
 * See {@link RCBase} for an overview of the basic reference counting algorithm
 * 
 * FIXME Currently RCBase does not properly separate mutator and collector
 * behaviors, so most of the collection logic in RCBaseMutator should really
 * be per-collector thread, not per-mutator thread.
 * 
 * @see RCBase
 * @see RCBaseMutator
 * @see org.mmtk.plan.StopTheWorldCollector
 * @see org.mmtk.plan.CollectorContext
 * @see org.mmtk.plan.SimplePhase#delegatePhase
 * 
 * $Id$
 * 
 * @author Steve Blackburn
 * @author Daniel Frampton
 * @author Robin Garner
 * @version $Revision$
 * @date $Date$
 */
public abstract class RCBaseCollector extends StopTheWorldCollector
implements Uninterruptible {
}
