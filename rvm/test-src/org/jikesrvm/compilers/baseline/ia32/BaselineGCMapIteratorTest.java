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

import static org.jikesrvm.ia32.StackframeLayoutConstants.BYTES_IN_STACKSLOT;
import static org.jikesrvm.runtime.UnboxedSizeConstants.BYTES_IN_WORD;
import static org.jikesrvm.tests.util.AssertUnboxed.assertEquals;
import static org.jikesrvm.tests.util.AssertUnboxed.assertZero;
import static org.jikesrvm.tests.util.TestingTools.createNonMovableWordArray;
import org.jikesrvm.architecture.ArchConstants;
import org.jikesrvm.classloader.NormalMethod;
import org.jikesrvm.compilers.common.CompiledMethod;
import org.jikesrvm.junit.runners.RequiresBuiltJikesRVM;
import org.jikesrvm.junit.runners.RequiresIA32;
import org.jikesrvm.junit.runners.VMRequirements;
import org.jikesrvm.runtime.Magic;
import org.jikesrvm.tests.util.MethodsForTests;
import org.jikesrvm.tests.util.TestingTools;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Ignore;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.junit.runner.RunWith;
import org.vmmagic.unboxed.Address;
import org.vmmagic.unboxed.AddressArray;
import org.vmmagic.unboxed.Offset;
import org.vmmagic.unboxed.Word;
import org.vmmagic.unboxed.WordArray;

@RunWith(VMRequirements.class)
@Category({RequiresIA32.class, RequiresBuiltJikesRVM.class})
public class BaselineGCMapIteratorTest {

  private static final Offset NO_OFFSET = Offset.zero();
  private static final Address NO_FP = Address.zero();

  private AddressArray registerLocations;
  private BaselineGCMapIterator gcMapIter;

  @Before
  public void setUp() {
    registerLocations = AddressArray.create(ArchConstants.getNumberOfGPRs());
    gcMapIter = new BaselineGCMapIterator(registerLocations);
  }

  @BeforeClass
  public static void ensureUsedMethodsAreCompiled() {
    MethodsForTests.emptyStaticMethodWithoutAnnotations();
    MethodsForTests.emptyStaticMethodWithObjectParam(null);
  }

  // This test case is only documentation. It musn't be run
  // because it would cause a NPE in uninterruptible code which
  // would lead to a crash in Jikes RVM.
  @Ignore
  @Test(expected = NullPointerException.class)
  public void usingIteratorBeforeCallToSetupIsNotPossible() {
    gcMapIter.reset();
  }

  // The following test cases use JikesRVMSupport to access the actual
  // compiled methods. This is obviously not possible on VMs other than
  // Jikes RVM.

  @Test
  public void setupOfIteratorRequiresOnlyCompiledMethod() throws Exception {
    NormalMethod nm = TestingTools.getNormalMethod(MethodsForTests.class, "emptyStaticMethodWithoutAnnotations");
    CompiledMethod cm = nm.getCurrentCompiledMethod();
    gcMapIter.setupIterator(cm, NO_OFFSET, NO_FP);
  }

  @Test
  public void getNextReturnAddressAddressReturnsZeroIfNoJSRsArePresent() throws Exception {
    NormalMethod nm = TestingTools.getNormalMethod(MethodsForTests.class, "emptyStaticMethodWithoutAnnotations");
    CompiledMethod cm = nm.getCurrentCompiledMethod();
    gcMapIter.setupIterator(cm, NO_OFFSET, NO_FP);
    Address returnAddressAddress = gcMapIter.getNextReturnAddressAddress();
    assertZero(returnAddressAddress);
  }

  @Test
  public void getNextReferenceAddressReturnsZeroIfNoReferencesArePresent() throws Exception {
    NormalMethod nm = TestingTools.getNormalMethod(MethodsForTests.class, "emptyStaticMethodWithoutAnnotations");
    CompiledMethod cm = nm.getCurrentCompiledMethod();
    gcMapIter.setupIterator(cm, NO_OFFSET, NO_FP);
    Address referenceAddr = gcMapIter.getNextReferenceAddress();
    assertZero(referenceAddr);
  }

  @Test
  public void getNextReferenceAddressReturnsCorrectReferenceIfReferencesArePresent() throws Exception {
    NormalMethod nm = TestingTools.getNormalMethod(MethodsForTests.class, "emptyStaticMethodWithObjectParam", Object.class);
    CompiledMethod cm = nm.getCurrentCompiledMethod();
    // Fake a stack frame
    int stackWords = 5;
    WordArray wa = createNonMovableWordArray(stackWords);
    for (int i = 0; i < wa.length(); i++) {
      wa.set(i, Word.fromIntSignExtend(5 * i));
    }
    int targetSlot = wa.length() - 1;
    Address fp = Magic.objectAsAddress(wa).plus(targetSlot * BYTES_IN_WORD);
    // local 0 is reference.
    // +/- 0 words: FP
    //   - 1 words: CMID
    //   - 2 words: saved GPRs (EDI)
    //   - 3 words: saved GPRs (EBX)
    //   - 4 words: local0 == reference
    Address targetAddress = fp.minus(4 * BYTES_IN_STACKSLOT);
    Word targetContents = targetAddress.loadWord();

    gcMapIter.setupIterator(cm, Offset.fromIntZeroExtend(cm.getEntryCodeArray().length()), fp);

    Address referenceAddr = gcMapIter.getNextReferenceAddress();
    assertEquals(targetAddress, referenceAddr);
    assertEquals(targetContents, referenceAddr.loadWord());
  }

}
