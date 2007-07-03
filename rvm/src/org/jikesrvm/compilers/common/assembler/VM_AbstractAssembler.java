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
package org.jikesrvm.compilers.common.assembler;

/**
 *  This class has been created to work around a bug whereby the system seems to have gotten
 * confused by the relationship between:
 *
 * VM_ForwardReference -> VM_ForwardReference.ShortBranch -> VM_Assembler.ShortBranch, and VM_Assembler
 *
 * This problem does not exist under IA32 since there is no need for VM_Assembler.ShortBranch
 */
public abstract class VM_AbstractAssembler {
  public abstract void patchShortBranch(int sourceMachinecodeIndex);

  public abstract void patchUnconditionalBranch(int sourceMachinecodeIndex);

  public abstract void patchConditionalBranch(int sourceMachinecodeIndex);

  public abstract void patchSwitchCase(int sourceMachinecodeIndex);
}
