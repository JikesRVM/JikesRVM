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
class inlineTest6 {

  static int run() {
    int i = sum(100);
    int j = sum(200);

    return i+j;
  }

  static int sum(int i) {
    int j;
    if (i == 0)
        j = i;
    else
        j = sum(i-1) + i;
    return j;
  }
}
