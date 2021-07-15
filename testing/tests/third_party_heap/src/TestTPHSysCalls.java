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

import org.jikesrvm.VM;
import org.jikesrvm.runtime.SysCall;

class TestTPHSysCalls {
    public static void main(String[] args) {

        // Test the stack alignment with 0 arguments passed
        SysCall.sysCall.sysTestStackAlignment0();

        // Test the stack alignment with 5 arguments passed
        // This returns the addition of each argument multiplied by a value
        // The returned value ensures that the arguments are passed in the correct order
        for (int i = 0; i < 4; i++) {
            int a = (int) (Math.random() * 50);
            int b = (int) (Math.random() * 50);
            int c = (int) (Math.random() * 50);
            int d = (int) (Math.random() * 50);
            int e = (int) (Math.random() * 50);
            int result = SysCall.sysCall.sysTestStackAlignment5(a, b, c, d, e);
            if (result != a + 2 * b + 3 * c + 4 * d + 5 * e) {
                System.out.println("Result: " + result);
                VM.sysFail("Returned incorrect result");
            }
        }
        System.out.println("ALL TESTS PASSED");

    }
}
