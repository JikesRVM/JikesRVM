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
class inlineTest5 {

  static int run() {
    int i = l2i0(0x000000000fffffffL);
    int j = l2i0(0x0000000000ffffffL);

    return i+j;
  }

  static int l2i0(long i) {
    return l2i1(i) + l2i2(i) + 1;
  }

  static int l2i1(long i) {
    return l2i2(i) + l2i5(i) + 2;
  }

  static int l2i2(long i) {
    return l2i3(i) +l2i5(i) + 3;
  }

  static int l2i3(long i) {
    return l2i5(i) + l2i5(i) + 4;
  }
/*
  static int l2i4(long i) {
    return l2i5(i) + 5;
  }
*/
  static int l2i5(long i) {

    int j = (int)i;

    int k = ((int)i)*2+j*5;
    int l = ((int)i)+j+k;
    int m = k-l*6;

    return m;

  }
}

