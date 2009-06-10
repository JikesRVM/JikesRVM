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
package org.jikesrvm.compilers.common.assembler;

/**
 *  This class has been created to work around a bug whereby the system seems to have gotten
 * confused by the relationship between:
 *
 * ForwardReference -> ForwardReference.ShortBranch -> Assembler.ShortBranch, and Assembler
 *
 * This problem does not exist under IA32 since there is no need for Assembler.ShortBranch
 */
public abstract class AbstractAssembler {
  public abstract void patchShortBranch(int sourceMachinecodeIndex);

  public abstract void patchUnconditionalBranch(int sourceMachinecodeIndex);

  public abstract void patchConditionalBranch(int sourceMachinecodeIndex);

  public abstract void patchSwitchCase(int sourceMachinecodeIndex);

  public abstract void patchLoadReturnAddress(int sourceMachinecodeIndex);
}
