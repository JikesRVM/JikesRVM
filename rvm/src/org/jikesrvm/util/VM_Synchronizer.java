/*
 *  This file is part of the Jikes RVM project (http://jikesrvm.org).
 *
 *  This file is licensed to You under the Common Public License (CPL);
 *  You may not use this file except in compliance with the License. You
 *  may obtain a copy of the License at
 *
 *      http://www.opensource.org/licenses/cpl1.0.php
 *
 *  See the COPYRIGHT.txt file distributed with this work for information
 *  regarding copyright ownership.
 */
package org.jikesrvm.util;

import org.vmmagic.pragma.SynchronizedObject;

/**
 * This class defines an object which should always be allocated a
 * thin lock, since it is likely to be synchronized.
 */
@SynchronizedObject
public class VM_Synchronizer {
}
