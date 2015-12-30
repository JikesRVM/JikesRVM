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
package org.jikesrvm.compilers.opt.lir2mir;

public interface BURS_StateCoder {

  /* Action modifiers */
  byte NOFLAGS           = 0x00;
  byte EMIT_INSTRUCTION  = 0x01;
  byte LEFT_CHILD_FIRST  = 0x02;
  byte RIGHT_CHILD_FIRST = 0x04;

  /* Generate code */
  void code(AbstractBURS_TreeNode p, int  n, int ruleno);
}
