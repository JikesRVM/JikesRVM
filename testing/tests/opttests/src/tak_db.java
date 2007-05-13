/*
 * This file is part of Jikes RVM (http://jikesrvm.sourceforge.net).
 * The Jikes RVM project is distributed under the Common Public License (CPL).
 * A copy of the license is included in the distribution, and is also
 * available at http://www.opensource.org/licenses/cpl1.0.php
 *
 * (C) Copyright IBM Corp. 2001
 */
/**
 */
public class tak_db{

  // double tak(double x, double y, double z);

  public static void main(String argv[])
  { 
        System.out.println("Tak is running\n");
          double result = tak(18,12,6);
          System.out.println(result + "\n");
  }

  static boolean run() {
    double d = tak_db.tak(18, 12, 6);
    System.out.println("Tak_db returned: " + d);
    return true;
  }


static double tak(double x, double y, double z)
{
   if (y >= x)
   {
      return z;
   }
   else
   {
      return tak(tak (x-1, y, z),
                 tak (y-1, z, x),
                 tak (z-1, x, y));
   }
}

}
