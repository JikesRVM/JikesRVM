/*
 * (C) Copyright IBM Corp. 2001
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
