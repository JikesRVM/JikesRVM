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
package test.org.jikesrvm.basic.core.intrinsics;

import org.jikesrvm.VM;
import org.jikesrvm.runtime.SysCall;

public class TestSysCalls {

  public static void main(String[] args) {
    testArgumentPassingForSysCalls();
  }

  public static void testArgumentPassingForSysCalls() {
    VM.sysWriteln("Test argument passing: longs and doubles");

    SysCall.sysCall.sysArgumentPassingSeveralLongsAndSeveralDoubles(Long.MIN_VALUE, Long.MAX_VALUE, -1L, 1L, 0xFFFF0000FFFF0000L, 0x0000FFFF0000FFFFL, 0, -1L, Double.MIN_VALUE, Double.MAX_VALUE, -1D, 0.1D, 1.2345678910E300D, Double.NaN, Double.MAX_VALUE, -1D);

    VM.sysWriteln("Test argument passing: floats and ints");

    SysCall.sysCall.sysArgumentPassingSeveralFloatsAndSeveralInts(Float.MAX_VALUE, Float.MIN_VALUE, -1f, 1f, 0.1f, 1.23456789E34F, Float.NaN, Float.POSITIVE_INFINITY, Integer.MAX_VALUE, Integer.MIN_VALUE, -1, 1, 0, 0xFFFF0000, 0x0000FFFF, 0xF0E0A1B1);
  }

}
