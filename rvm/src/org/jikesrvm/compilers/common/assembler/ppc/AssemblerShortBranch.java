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
package org.jikesrvm.compilers.common.assembler.ppc;

import org.jikesrvm.compilers.common.assembler.AbstractAssembler;
import org.jikesrvm.compilers.common.assembler.ForwardReference;

/**
 * This class was formerly a private static inner class of Assembler, but has
 * been pulled out to work around a bug whereby the system seems to have gotten
 * confused by the relationship between:
 *
 * ForwardReference -> ForwardReference.ShortBranch -> Assembler.ShortBranch, and Assembler
 *
 * This problem does not exist under IA32 since there is no need for Assembler.ShortBranch
 */
class AssemblerShortBranch extends ForwardReference.ShortBranch {
  final int spTopOffset;

  AssemblerShortBranch(int source, int sp) {
    super(source);
    spTopOffset = sp;
  }

  @Override
  public void resolve(AbstractAssembler asm) {
    super.resolve(asm);
    if (((Assembler) asm).compiler != null) {
      ((Assembler) asm).compiler.spTopOffset = spTopOffset;
    }
  }
}
