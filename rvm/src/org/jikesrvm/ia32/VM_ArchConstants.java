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
package org.jikesrvm.ia32;

import org.jikesrvm.VM_Configuration;

/**
 * Architecture specific constants.
 */
public interface VM_ArchConstants extends VM_StackframeLayoutConstants, VM_RegisterConstants, VM_TrapConstants {
  static final boolean SSE2_OPS = VM_Configuration.BuildForSSE2;
}
