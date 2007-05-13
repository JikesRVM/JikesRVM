/*
 * This file is part of Jikes RVM (http://jikesrvm.sourceforge.net).
 * The Jikes RVM project is distributed under the Common Public License (CPL).
 * A copy of the license is included in the distribution, and is also
 * available at http://www.opensource.org/licenses/cpl1.0.php
 *
 * (C) Copyright IBM Corp. 2007
 *
 *
 * This class was formerly a private static inner class of VM_Assembler, but has
 * been pulled out to work around a bug whereby the system seems to have gotten
 * confused by the relationship between:
 * 
 * VM_ForwardReference -> VM_ForwardReference.ShortBranch -> VM_Assembler.ShortBranch, and VM_Assembler
 * 
 * This problem does not exist under IA32 since there is no need for VM_Assembler.ShortBranch
 */
package org.jikesrvm.compilers.common.assembler.ppc;

import org.jikesrvm.compilers.common.assembler.VM_AbstractAssembler;
import org.jikesrvm.compilers.common.assembler.VM_ForwardReference;

class VM_AssemblerShortBranch extends VM_ForwardReference.ShortBranch {
  final int spTopOffset;

  VM_AssemblerShortBranch(int source, int sp) {
    super(source);
    spTopOffset = sp;
  }

  public void resolve(VM_AbstractAssembler asm) {
    super.resolve(asm);
    if (((VM_Assembler) asm).compiler != null) {
      ((VM_Assembler) asm).compiler.spTopOffset = spTopOffset;
    }
  }
}