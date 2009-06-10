/*
 *  This file is part of the Jikes RVM project (http://jikesrvm.org).
 *
 *  This file is licensed to You under the Eclipse Public License (EPL);
 *  You may not use this file except in compliance with the License. You
 *  may obtain a copy of the License at
 *
 *      http://www.opensource.org/licenses/eclipse-1.0.php
 *
 *  See the COPYRIGHT.txt file distributed with this work for information
 *  regarding copyright ownership.
 */
package org.mmtk.plan;

import org.vmmagic.pragma.*;

/**
 * This class (and its sub-classes) implement <i>per-mutator thread</i>
 * behavior and state.
 *
 * MMTk assumes that the VM instantiates instances of MutatorContext
 * in thread local storage (TLS) for each application thread. Accesses
 * to this state are therefore assumed to be low-cost during mutator
 * time.<p>
 *
 * @see MutatorContext
 */
@Uninterruptible
public abstract class StopTheWorldMutator extends SimpleMutator {
}
