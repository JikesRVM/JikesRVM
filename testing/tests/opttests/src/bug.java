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
class bug {

   static byte x[] = {-1};
   
   public static void main(String args[]) {
      int X = x[0];
      int Y;
      if (X == 0)
         Y = 0;
      else if (X > 0)
         Y = 1;
     else
         Y = -1;
     System.out.println(Y);
   }
}
