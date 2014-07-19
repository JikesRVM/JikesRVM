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
package org.jikesrvm.tools.checkstyle;

import java.util.Random;
import org.jikesrvm.VM;
import org.jikesrvm.compilers.opt.ir.IR;

public class TestFile {

  private static final Random r = new Random();

  // Cases that are forbidden

  public void forbiddenAssert() {
    assert true;
  }

  public void unguardedVMAssert() {
    boolean bool = new Random().nextBoolean();
    VM._assert(bool);
  }

  public void assertGuardedWithWrongCondition() {
    boolean bool = new Random().nextBoolean();
    if (bool) {
      VM._assert(!bool);
    }
  }

  public void falseMustNotBeUsedInVMAssert() {
    if (VM.VerifyAssertions) {
      VM._assert(false); // VM.NOT_REACHED must be used instead
    }
    if (VM.VerifyAssertions) {
      VM._assert(false, "msg");
    }
  }

  public void incorrectlyGuardedAssert() {
    boolean bool = r.nextBoolean();
    if (VM.VerifyAssertions || bool) {
      VM._assert(!bool);
    }
    if (VM.ExtremeAssertions || bool) {
      VM._assert(!bool);
    }
    if (IR.PARANOID || bool) {
      VM._assert(!bool);
    }
    if (IR.SANITY_CHECK || bool) {
      VM._assert(!bool);
    }
  }

  public void forbiddenStringConcatenationInMessage() {
    boolean bool = r.nextBoolean();
    if (VM.VerifyAssertions) {
      VM._assert(bool, "a" + "b" + bool + "c");
    }
    if (VM.VerifyAssertions) {
      VM._assert(bool, "msg", bool + "c");
    }
  }

  // Cases that are allowed

  public void guardedVMAssert() {
    boolean bool = r.nextBoolean();
    if (VM.VerifyAssertions) {
      VM._assert(bool);
    }
  }

  public void guardedAssertNoBraces() {
    boolean bool = r.nextBoolean();
    if (VM.VerifyAssertions)
      VM._assert(bool);
  }

  public void guardedVMAssertWithExtremeAssertions() {
    boolean bool = r.nextBoolean();
    if (VM.ExtremeAssertions) {
      VM._assert(bool);
    }
  }

  public void guardedAssertWithIRSanityCheck() {
    boolean bool = r.nextBoolean();
    if (IR.SANITY_CHECK) {
      VM._assert(bool);
    }
  }

  public void guardedAssertWithIRParanoid() {
    boolean bool = r.nextBoolean();
    if (IR.PARANOID) {
      VM._assert(bool);
    }
  }

  public void nestedGuardedVMAssert() {
    if (VM.VerifyAssertions) {
      boolean bool1 = r.nextBoolean();
      boolean bool2 = r.nextBoolean();
      if (bool1 && bool2) {
        bool2 = r.nextBoolean();
      }
      if (bool1) {
        VM._assert(bool2);
      }
    }
  }

  public void nestedGuardedVMAssertWithExtremeAssertions() {
    if (VM.ExtremeAssertions) {
      boolean bool1 = r.nextBoolean();
      boolean bool2 = r.nextBoolean();
      if (bool1 && bool2) {
        bool2 = r.nextBoolean();
      }
      if (bool1) {
        VM._assert(bool2);
      }
    }
  }

  public void nestedGuardedVMAssertWithIRParanoid() {
    if (IR.PARANOID) {
      boolean bool1 = r.nextBoolean();
      boolean bool2 = r.nextBoolean();
      if (bool1 && bool2) {
        bool2 = r.nextBoolean();
      }
      if (bool1) {
        VM._assert(bool2);
      }
    }
  }

  public void nestedGuardedVMAssertWithIRParanoid() {
    if (IR.SANITY_CHECK) {
      boolean bool1 = r.nextBoolean();
      boolean bool2 = r.nextBoolean();
      if (bool1 && bool2) {
        bool2 = r.nextBoolean();
      }
      if (bool1) {
        VM._assert(bool2);
      }
    }
  }

  public void guardedAssertWithAnd() {
    boolean bool = r.nextBoolean();
    if (VM.VerifyAssertions && bool) {
      VM._assert(!bool);
    }
    if (VM.ExtremeAssertions && bool) {
      VM._assert(!bool);
    }
    if (IR.PARANOID && bool) {
      VM._assert(!bool);
    }
    if (IR.SANITY_CHECK && bool) {
      VM._assert(!bool);
    }
  }

  public void falseUsedInANormalCall() {
    Boolean.toString(false);
  }

  public void aMethod() {
    aMethod();
  }

}
