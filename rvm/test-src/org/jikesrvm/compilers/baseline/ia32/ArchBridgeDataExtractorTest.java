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
package org.jikesrvm.compilers.baseline.ia32;

import static org.jikesrvm.runtime.UnboxedSizeConstants.BYTES_IN_WORD;
import static org.jikesrvm.tests.util.AssertUnboxed.assertEquals;
import static org.jikesrvm.tests.util.AssertUnboxed.assertZero;
import static org.jikesrvm.tests.util.TestingTools.createNonMovableWordArray;

import org.jikesrvm.classloader.TypeReference;
import org.jikesrvm.junit.runners.Requires32BitAddressing;
import org.jikesrvm.junit.runners.Requires64BitAddressing;
import org.jikesrvm.junit.runners.RequiresBuiltJikesRVM;
import org.jikesrvm.junit.runners.RequiresIA32;
import org.jikesrvm.junit.runners.VMRequirements;
import org.jikesrvm.runtime.Magic;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.junit.runner.RunWith;
import org.vmmagic.unboxed.Address;
import org.vmmagic.unboxed.Word;
import org.vmmagic.unboxed.WordArray;

@RunWith(VMRequirements.class)
@Category({RequiresIA32.class, RequiresBuiltJikesRVM.class})
public class ArchBridgeDataExtractorTest {


  private MockBridgeDataExtractor bridge;

  @Before
  public void createBridgeExtractor() {
    bridge = new MockBridgeDataExtractor();
  }

  @Test
  @Category(Requires32BitAddressing.class)
  public void longParametersInBridgeMethodsAreProcessedCorrectlyFor32BitAddressing() {
    TypeReference[] types = {TypeReference.Long, TypeReference.JavaLangObject};
    bridge.setBridgeParameterTypes(types);
    Address bridgeParameterAddr = bridge.getNextBridgeParameterAddress();
    assertZero(bridgeParameterAddr);
  }

  @Test
  @Category(Requires64BitAddressing.class)
  public void longParametersInBridgeMethodsAreProcessedCorrectlyFor64BitAddressing() {
    TypeReference[] types = {TypeReference.Long, TypeReference.JavaLangObject};
    bridge.setBridgeParameterTypes(types);
    Word expected = Word.fromIntSignExtend(11);
    int length = 4;
    WordArray stackFrame = createNonMovableWordArray(length);
    int bridgeRegLocSlotIndex = stackFrame.length() - 1;
    stackFrame.set(bridgeRegLocSlotIndex - 1, expected);
    Address brideRegLoc = Magic.objectAsAddress(stackFrame).plus(bridgeRegLocSlotIndex * BYTES_IN_WORD);
    bridge.setBridgeRegisterLocation(brideRegLoc);
    Address bridgeParameterAddr = bridge.getNextBridgeParameterAddress();
    Word bridgeParameter = bridgeParameterAddr.loadWord();
    assertEquals(expected, bridgeParameter);
  }

  private static class MockBridgeDataExtractor extends ArchBridgeDataExtractor {

    public void setBridgeParameterTypes(TypeReference[] types) {
      bridgeParameterTypes = types;
    }

  }

}
