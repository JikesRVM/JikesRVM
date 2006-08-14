/*
 * (C) Copyright Department of Computer Science,
 * Australian National University. 2006
 */
package org.mmtk.plan.semispace.gcspy;

import org.mmtk.plan.semispace.SSMutator;
import org.mmtk.policy.LargeObjectLocal;

/**
 * This class implements <i>per-mutator thread</i> behavior and state for the
 * <i>SSGCspy</i> plan.<p>
 * 
 * GCSpy does not currently have per-muator extensions to SS.<p>
 * 
 * See {@link SSGCspy} for an overview of the GC-spy mechanisms.<p>
 * 
 * @see SSMutator
 * @see SSGCspy
 * @see SSGCspyCollector
 * @see org.mmtk.plan.StopTheWorldMutator
 * @see org.mmtk.plan.MutatorContext
 * @see org.mmtk.plan.SimplePhase#delegatePhase
 * 
 * $Id$
 * 
 * @author Steve Blackburn
 * @author <a href="http://www.cs.ukc.ac.uk/~rej">Richard Jones</a>
 * @version $Revision$
 * @date $Date$
 */
public class SSGCspyMutator extends SSMutator {
  public LargeObjectLocal getLOS() { return los; }
}
