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
public class tak_db{

  // double tak(double x, double y, double z);

  public static void main(String[] argv) {
        System.out.println("Tak is running\n");
          double result = tak(18,12,6);
          System.out.println(result + "\n");
  }

  static boolean run() {
    double d = tak_db.tak(18, 12, 6);
    System.out.println("Tak_db returned: " + d);
    return true;
  }


static double tak(double x, double y, double z) {
   if (y >= x) {
      return z;
   } else {
      return tak(tak(x-1, y, z),
                 tak(y-1, z, x),
                 tak(z-1, x, y));
   }
}

}
