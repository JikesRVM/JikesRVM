/*
 * This file is part of Jikes RVM (http://jikesrvm.sourceforge.net).
 * The Jikes RVM project is distributed under the Common Public License (CPL).
 * A copy of the license is included in the distribution, and is also
 * available at http://www.opensource.org/licenses/cpl1.0.php
 *
 * (C) Copyright IBM Corp. 2001
 */

class table {
  static boolean run() {
    int i = table.foo(3);
    System.out.println("Table returned: " + i);
    return true;
  }

  static int foo(int a) {
    if (a<1)
      return 0;
    else {
      switch(a) {
        case 0: a = 1; break; 
        case 1: a = 2; break; 
        case 2: a = 3; break; 
        case 3: a = 4; break; 
        case 4: a = 5; break; 
      }
      return a;
    }
  }
}
