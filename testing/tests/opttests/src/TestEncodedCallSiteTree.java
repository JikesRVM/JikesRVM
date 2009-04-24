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
class TestEncodedCallSiteTree {
  static final int A = 100;
  static final int B = 200;
  static final int C = 300;
  static final int D = 400;
  static final int E = 500;
  static final int F = 600;
  static final int G = 700;
  static final int H = 800;
  static final int I = 900;
  static final int J = 1000;

  // <A, 12, B>, <A,14,C>, <A,16,D>, <B,3,E>, <B,5,F>, <C,10,G>, <G,20,H>, <H,30,I>
  static int[] data1 =  { -1, A, -2, 12, B, 14, C, 16, D, -6, 3, E, 5, F, -9, 10, G, -2, 20, H, -2, 30, I };

  static int[] data2 = {-1,9954,-2,7,9537,10,9528,16,9515,36,9518,60,3432,-8,14,9538,24,291,40,23,43,291,56,137,59,23,
                        -12,3,291,11,3432,-4,3,287,-5,-6,398,1,7533,-5,399,-7,399,-27,3,287,-28,5,291,-2,3,287,-32,3,287,
                        -33,2,111,-2,1,69,-2,3,7523,-40,5,291,-2,3,287,-63,5,3432,-2,-6,398,1,91,37,85,-5,399,-7,399,
                        -6,36,45,-78,5,3432,-2,-6,398,1,91,37,85,-5,399,-7,399,-90,-6,398,1,91,37,85,-5,399,-7,399};

  public static void main(String[] args) {
    test(A, 10, H, data1);
    test(A, 14, C, data1);
    test(B, 3, F, data1);
    test(B, 3, E, data1);
    test(B, 10, A, data1);
    test(J, 7, A, data1);
    test(H, 30, I, data1);
    test(I, 30, H, data1);

    test(9535, 32, 9514, data2);
  }

  static void test(int callerMID, int bcIndex, int calleeMID, int[] data) {
    System.out.println("Result is "+VM_OptEncodedCallSiteTree.callEdgeMissing(callerMID, bcIndex, calleeMID, data)+"\n");
  }

}
