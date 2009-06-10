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
public class tak_long {

  // long tak(long x, long y, long z);

  /*
  public static void main(String argv[])
  {
        int i;
        System.out.println("Tak is running\n");
        //for (i=0; i<1000; i++){
          long result = tak(18,12,6);
        System.out.println(result + "\n");
        //}

        //System.exit(0);
  }
  */

  static boolean run() {
    long i = tak_long.tak(18L, 12L, 6L);
    System.out.println("Tak_long returned: " + i);
    return true;
  }

static long tak(long x, long y, long z) {
   if (y >= x) {
      return z;
   } else {
      return tak(tak(x-1, y, z),
                 tak(y-1, z, x),
                 tak(z-1, x, y));
   }
}

}
