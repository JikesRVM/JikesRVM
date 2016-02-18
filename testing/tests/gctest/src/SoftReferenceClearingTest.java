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

import java.lang.ref.SoftReference;

import org.jikesrvm.VM;
import org.vmmagic.pragma.Uninterruptible;

/**
 * A test case for RVM-1067 (Overly eager clearing of soft references).
 * <p>
 * Description of the test case based on the mail that reported the bug:
 * <pre>
 * When a soft reference A is reachable only though another soft
 * reference B, the garbage collector failed to preserve the correct
 * semantics before the bug from this test case was fixed.
 *
 * In this test case, there are 3 objects. A "normal" object is directly
 * reachable from a local variable, o.
 * A SoftReference object is also directly reachable from a local
 * variable, rr. Its referent, call it r, is another SoftReference object,
 * which in turn has as its referent, o. Immediately prior to GC,
 * r is soft reachable from rr (and only from rr). Thus, using the
 * notation -s-> to indicate a soft reference, and naming objects
 * by the variables that point to them, immediately before GC we
 * have:
 * root  ---> rr -s-> r -s-> o <--- root
 *
 * The specification for soft references permits two actions on a
 * softly reachable (and by definition, not strongly reachable) object:
 * it preserves the object or it may choose to reclaim it. The only softly
 * reachable object in this scenario is r.
 *
 * In the former case, the object graph above is unchanged. Specifically,
 * r == rr.get() && r.get() == o.
 * In the latter case, after GC, rr.get() == null.
 * However, r.get() returned null on Jikes RVM before the bug fix,
 * which should be impossible
 * since either r == rr.get() == null and we should get an NPE when we call r.get(),
 * or r == rr.get() != null and r.get() should return o.
 * </pre>
 */
public class SoftReferenceClearingTest {

  public static void main(String[] args) throws Exception {
    Object o = new Object();
    SoftReference<Object> r = new SoftReference<Object>(o);
    SoftReference<SoftReference<Object>> rr = new SoftReference<SoftReference<Object>>(r);

    r = null;
    System.gc();
    r = rr.get();
    boolean success = determineTestSuccess(o, r);
    if (success) {
      System.out.println("ALL TESTS PASSED");
    } else {
      System.out.println("SOME TESTS FAILED");
    }
  }

  @Uninterruptible
  public static boolean determineTestSuccess(Object o, SoftReference<Object> r) {
    boolean testSuccess = false;
    VM.sysWriteln("o is null: ");
    VM.sysWriteln(o == null);
    VM.sysWriteln("r is null: ");
    VM.sysWriteln(r == null);
    if (r != null) {
      VM.sysWriteln("r.get() is null: ");
      Object referentOfR = java.lang.ref.JikesRVMSupport.uninterruptibleReferenceGet(r);
      VM.sysWriteln(referentOfR == null);
      if (referentOfR != null && referentOfR == o) {
        testSuccess = true;
      } else {
        testSuccess = false;
      }
    } else {
      testSuccess = true;
    }
    return testSuccess;
  }

}
