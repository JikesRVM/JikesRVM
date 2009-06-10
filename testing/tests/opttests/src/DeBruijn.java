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
class DeBruijn {
  static boolean run() {
    String str = calc(5);
    System.out.println("DeBruijn returned: " + str);
    return true;
  }

  public static String calc(int length) {

    if (length > 32) {
      //      System.out.println("Number is too LARGE!");
      return "Number is too LARGE!";
    }

    length = 1 << length;

    boolean[] table = new boolean [length];

    int mask = length - 1;

    String str = "";

    for (int i=0, val=mask; i<length; ++i) {
      val <<= 1;
      val &= mask;
      if (table[val]) {
        ++val;
        if (table[val]) {
          //      System.out.println("John is wrong!");
          return "John is wrong!";
        } else {
          table[val] = true;
          //      System.out.print("1");
          str = str +"1";
        }
      } else {
        table[val] = true;
        //      System.out.print("0");
        str = str + "0";
      }
    }
    //    System.out.println();
    return str;
  }

}
