/*
 * This file is part of Jikes RVM (http://jikesrvm.sourceforge.net).
 * The Jikes RVM project is distributed under the Common Public License (CPL).
 * A copy of the license is included in the distribution, and is also
 * available at http://www.opensource.org/licenses/cpl1.0.php
 *
 * (C) Copyright IBM Corp. 2007
 *
 * $Id: VM_Assembler.java 11260 2007-01-02 23:25:20Z steveb-oss $
 *
 * This class has been created to work around a bug whereby the system seems to have gotten
 * confused by the relationship between:
 * 
 * VM_ForwardReference -> VM_ForwardReference.ShortBranch -> VM_Assembler.ShortBranch, and VM_Assembler
 * 
 * This problem does not exist under IA32 since there is no need for VM_Assembler.ShortBranch
 */
package com.ibm.jikesrvm;

public abstract class VM_AbstractAssembler {
  abstract void patchShortBranch(int sourceMachinecodeIndex);
  abstract void patchUnconditionalBranch(int sourceMachinecodeIndex);
  abstract void patchConditionalBranch(int sourceMachinecodeIndex);
  abstract void patchSwitchCase(int sourceMachinecodeIndex);
}
