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
package org.mmtk.harness.lang.compiler;

import java.util.Locale;

import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;
import org.mmtk.harness.lang.runtime.ConstantPool;
import org.mmtk.harness.lang.runtime.ObjectValue;
import org.mmtk.harness.lang.runtime.Value;
import org.vmmagic.unboxed.Address;
import org.vmmagic.unboxed.harness.ArchitecturalWord;


public class RegisterTest {

  @BeforeClass
  public static void setup() {
    ArchitecturalWord.init(32);
  }

  @Test
  public void registerToStringCannotPrintValueForTemporary() throws Exception {
    int index = 0;
    Assert.assertEquals("t" + index, Register.nameOf(index));
  }

  @Test
  public void registerToStringPrintsValueOfConstant() throws Exception {
    int constantAddress = 0xDEADBEEF;
    Value constant = new ObjectValue(Address.fromIntZeroExtend(constantAddress).toObjectReference());
    Register constantReg = ConstantPool.acquire(constant);
    int regIndex = constantReg.getIndex();
    int adjustedIndex = -regIndex - 1;
    String expectedOutputForConstant = "c" + Integer.toString(adjustedIndex) + "=0x" + Integer.toHexString(constantAddress).toUpperCase(Locale.ROOT);
    String actualOutputForConstant = Register.nameOf(regIndex);
    Assert.assertEquals(expectedOutputForConstant, actualOutputForConstant);
  }

}
