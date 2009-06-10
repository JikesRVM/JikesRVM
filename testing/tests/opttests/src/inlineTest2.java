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
class inlineTest2 {

  static int run() {
    int i = l2i(0x000000000fffffffL);
    int j = l2i(0x0000000000ffffffL);

    return i+j;
  }

  static int l2i(long i) {
    return (int)i;
  }
}

