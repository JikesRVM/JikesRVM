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

/**
 * Provides a memory management implementation that can allocate but not collect.<p>
 *
 * A VM built with this collector will crash when all available memory has been exhausted.
 * It will also crash when a collection is triggered via {@link java.lang.System#gc()} (or
 * equivalent).
 */
package org.mmtk.plan.nogc;
