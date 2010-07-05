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
package org.mmtk.plan.semispace.usePrimitiveWriteBarriers;

import org.mmtk.plan.semispace.SSCollector;
import org.vmmagic.pragma.*;

/**
 * This class extends the {@link SSCollector} class as part of the
 * {@link UsePrimitiveWriteBarriers} collector. All implementation details
 * concerning GC are handled by {@link SSCollector}
 */
@Uninterruptible
public class UsePrimitiveWriteBarriersCollector extends SSCollector {
}
