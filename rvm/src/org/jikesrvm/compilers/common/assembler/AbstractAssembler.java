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

import org.jikesrvm.architecture.MachineRegister;
import org.jikesrvm.compilers.common.CodeArray;

/**
 * <p>This class has been created to work around a bug whereby the system seems to have gotten
 * confused by the relationship between:</p>
 *
 * <p>ForwardReference -&gt; ForwardReference.ShortBranch -&gt; Assembler.ShortBranch, and Assembler</p>
 *
 * This problem does not exist under IA32 since there is no need for Assembler.ShortBranch.
 */
public abstract class AbstractAssembler {
  public abstract void patchShortBranch(int sourceMachinecodeIndex);

  public abstract void patchUnconditionalBranch(int sourceMachinecodeIndex);

  public abstract void patchConditionalBranch(int sourceMachinecodeIndex);

  public abstract void patchSwitchCase(int sourceMachinecodeIndex);

  public abstract void patchLoadReturnAddress(int sourceMachinecodeIndex);

  public abstract int getMachineCodeIndex();

  public abstract CodeArray getMachineCodes();

  public abstract void resolveForwardReferences(int biStart);


  /**
   * The following method will emit code that moves a reference to an
   * object's TIB into a destination register.
   *
   * @param dest the number of the destination register
   * @param object the number of the register holding the object reference
   */
  public abstract void baselineEmitLoadTIB(MachineRegister dest, MachineRegister object);
}
