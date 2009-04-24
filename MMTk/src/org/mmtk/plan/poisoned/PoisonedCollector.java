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
package org.mmtk.plan.poisoned;

import org.mmtk.plan.marksweep.MSCollector;

import org.vmmagic.pragma.Uninterruptible;

/**
 * This class implements a poisoned collector, that is essentially a test
 * case for read and write barriers in the VM.
 */
@Uninterruptible
public class PoisonedCollector extends MSCollector {
}
