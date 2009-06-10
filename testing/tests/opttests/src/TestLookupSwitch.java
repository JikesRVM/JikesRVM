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
class TestLookupSwitch {
  static boolean run() {
    int i = foo(1000);
    System.out.println("TestLookupSwitch returned: " + i);
    return true;
  }

  static int foo(int bar) {
    int retVal = 0;

    switch (bar) {
    case -1000:
    case -100:
      retVal = (bar+1);
      break;
    case -10:
      retVal = (bar+2);
      break;
    case -1:
      retVal = (bar+3);
    case 1:
    case 10:
      retVal = (bar+4);
      break;
    case 100:
      retVal = (bar+5);
      break;
    case 1000:
    case 10000:
    case 100000:
    case 1000000:
      retVal = (bar+6);
      break;
    case 10000000:
    case 100000000:
      retVal = (bar+7);
    default:
      retVal = (bar+8);
      break;
    }

    return retVal;
  }

}
