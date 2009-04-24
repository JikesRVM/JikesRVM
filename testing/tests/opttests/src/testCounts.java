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
class testCounts {
    public static void main() {
        bar(100);
    }

  public static void bar(int n) {
        int[] a = new int[n];
        int length = a.length;

        for(int i = 0 ; i < length ; i++) {
            a[i] = a[i] + 1;
        }
    }

    static int foo(int[] a, int i) {
        return a[i];
    }
}
