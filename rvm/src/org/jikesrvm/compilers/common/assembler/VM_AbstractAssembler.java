/*
 * This file is part of Jikes RVM (http://jikesrvm.sourceforge.net).
 * The Jikes RVM project is distributed under the Common Public License (CPL).
 * A copy of the license is included in the distribution, and is also
 * available at http://www.opensource.org/licenses/cpl1.0.php
 *
 * (C) Copyright IBM Corp. 2007
 *
 *
 * This class has been created to work around a bug whereby the system seems to have gotten
 * confused by the relationship between:
 * 
 * VM_ForwardReference -> VM_ForwardReference.ShortBranch -> VM_Assembler.ShortBranch, and VM_Assembler
 * 
 * This problem does not exist under IA32 since there is no need for VM_Assembler.ShortBranch
 */
package org.jikesrvm.compilers.common.assembler;

public abstract class VM_AbstractAssembler {
  public abstract void patchShortBranch(int sourceMachinecodeIndex);

  public abstract void patchUnconditionalBranch(int sourceMachinecodeIndex);

  public abstract void patchConditionalBranch(int sourceMachinecodeIndex);

  public abstract void patchSwitchCase(int sourceMachinecodeIndex);
}
