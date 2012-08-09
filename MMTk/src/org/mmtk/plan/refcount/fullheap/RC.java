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
package org.mmtk.plan.refcount.fullheap;

import org.mmtk.plan.refcount.RCBase;
import org.vmmagic.pragma.*;

/**
 * This class implements the global state of a reference counting collector.
 * See Shahriyar et al for details of and rationale for the optimizations used
 * here (http://dx.doi.org/10.1145/2258996.2259008).  See Chapter 4 of
 * Daniel Frampton's PhD thesis for details of and rationale for the cycle
 * collection strategy used by this collector.
 */
@Uninterruptible
public class RC extends RCBase {
}
