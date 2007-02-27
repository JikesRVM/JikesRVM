/*
 * This file is part of Jikes RVM (http://jikesrvm.sourceforge.net).
 * The Jikes RVM project is distributed under the Common Public License (CPL).
 * A copy of the license is included in the distribution, and is also
 * available at http://www.opensource.org/licenses/cpl1.0.php
 *
 * (C) Copyright IBM Corp. 2001
 */
/**
 * @author unascribed
 */
public class tak_fp{

  // float tak(float x, float y, float z);

  public static void main(String argv[])
  { 
        System.out.println("Tak is running\n");
          float result = tak(18,12,6);
          System.out.println(result + "\n"+test2(18));

  }

  static boolean run() {
    float f = tak(18, 12, 6);
    System.out.println("Tak_fp returned: " + f);
    return true;
  }


   public static void tests(float x, float y, float z)
   {
      test1(tak(x,y,z));
   }

   public static float test2(int x) {
      float y = 18.0F;
      test1(y);
      return y;
   } 

   public static void test1(float x) {

      System.out.println(x);
   }

static float tak(float x, float y, float z)
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
