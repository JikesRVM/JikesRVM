/*
 * (C) Copyright IBM Corp. 2001
 */
// TestArithmetic
//
class TestArithmetic
   {
   public static void main(String args[])
      {
   // VM.boot();
      runTest();
      }

   public static void runTest()
      {
      SystemOut.println("TestArithmetic");

      SystemOut.println("\n-- itest --"); itest();
      SystemOut.println("\n-- ltest --"); ltest();
      SystemOut.println("\n-- ftest --"); ftest();
      SystemOut.println("\n-- dtest --"); dtest();

      SystemOut.println("\n-- nanTestFloat --"); nanTestFloat();
      SystemOut.println("\n-- nanTestDouble --"); nanTestDouble();
      
//!!TODO: baseline compiler fails on this...
//    SystemOut.println("\n-- remTest --"); remTest();
      }

   static void
   itest()
      {
      int a = 3;
      SystemOut.print("\nwant: 4\n got: ");          SystemOut.println(a  +   1);  // iadd
      SystemOut.print("\nwant: 2\n got: ");          SystemOut.println(a  -   1);  // isub
      SystemOut.print("\nwant: 9\n got: ");          SystemOut.println(a  *   3);  // imul
      SystemOut.print("\nwant: 1\n got: ");          SystemOut.println(a  /   2);  // idiv
      SystemOut.print("\nwant: 1\n got: ");          SystemOut.println(a  %   2);  // irem
      SystemOut.print("\nwant: -3\n got: ");         SystemOut.println(   -   a);  // ineg
      SystemOut.print("\nwant: 4\n got: ");          SystemOut.println(   ++  a);  // iinc
      
      a = 0x00000011;
      int b = 0x00000101;

      SystemOut.print("\nwant: 1\n got: ");          SystemOut.println(a  &   b);  // iand
      SystemOut.print("\nwant: 273\n got: ");        SystemOut.println(a  |   b);  // ior
      SystemOut.print("\nwant: 272\n got: ");        SystemOut.println(a  ^   b);  // ixor
      
      a = 0xfffffffd; // -3
      
      SystemOut.print("\nwant: -6\n got: ");         SystemOut.println(a  <<  1);  // ishl
      SystemOut.print("\nwant: -2\n got: ");         SystemOut.println(a  >>  1);  // ishr
      SystemOut.print("\nwant: 2147483646\n got: "); SystemOut.println(a >>>  1);  // iushr
      }
      
   static void
   ltest()
      {
      long a = 10000000000L;
      long b = 2;
                                                              
      SystemOut.print("\nwant: 10000000002\n got: ");         SystemOut.println(a +  b);  // ladd
      SystemOut.print("\nwant: 9999999998\n got: ");          SystemOut.println(a -  b);  // lsub
      SystemOut.print("\nwant: 20000000000\n got: ");         SystemOut.println(a *  b);  // lmul
      SystemOut.print("\nwant: 5000000000\n got: ");          SystemOut.println(a /  b);  // ldiv
      SystemOut.print("\nwant: 0\n got: ");                   SystemOut.println(a %  b);  // lrem
      SystemOut.print("\nwant: -2\n got: ");                  SystemOut.println(  -  b);  // lneg
      SystemOut.print("\nwant: -10000000000\n got: ");        SystemOut.println(  -  a);  // lneg

      a = 0x0110000000000011L;
      b = 0x1010000000000101L;

      SystemOut.print("\nwant: 4503599627370497\n got: ");    SystemOut.println(a &   b);  // land
      SystemOut.print("\nwant: 1229482698272145681\n got: "); SystemOut.println(a |   b);  // lor
      SystemOut.print("\nwant: 1224979098644775184\n got: "); SystemOut.println(a ^   b);  // lxor

      a = 0xfffffffffffffffdL; // -3

      SystemOut.print("\nwant: -6\n got: ");                  SystemOut.println(a <<  1);  // lshl
      SystemOut.print("\nwant: -2\n got: ");                  SystemOut.println(a >>  1);  // lshr
      SystemOut.print("\nwant: -1\n got: ");                  SystemOut.println(a >> 33);  // lshr, count > 32
      SystemOut.print("\nwant: 9223372036854775806\n got: "); SystemOut.println(a >>> 1);  // lushr
      }

   static void
   ftest()
      {
      float a = 1;
      float b = 2;
                                                              
      SystemOut.print("\nwant: 3.0\n got: ");   SystemOut.println(a + b);  // fadd
      SystemOut.print("\nwant: -1.0\n got: ");  SystemOut.println(a - b);  // fsub
      SystemOut.print("\nwant: 2.0\n got: ");   SystemOut.println(a * b);  // fmul
      SystemOut.print("\nwant: 0.5\n got: ");   SystemOut.println(a / b);  // fdiv
      SystemOut.print("\nwant: -1.0\n got: ");  SystemOut.println(  - a);  // fneg

      a = 1.5F; 
      b = 0.9F;
      SystemOut.print("\nwant: 0.6\n got: ");   SystemOut.println(a % b);  // frem
      }

   static void
   dtest()
      {
      double a = 1;
      double b = 2;
                                                              
      SystemOut.print("\nwant: 3.0\n got: ");   SystemOut.println(a + b);  // dadd
      SystemOut.print("\nwant: -1.0\n got: ");  SystemOut.println(a - b);  // dsub
      SystemOut.print("\nwant: 2.0\n got: ");   SystemOut.println(a * b);  // dmul
      SystemOut.print("\nwant: 0.5\n got: ");   SystemOut.println(a / b);  // ddiv
      SystemOut.print("\nwant: -1.0\n got: ");  SystemOut.println(  - a);  // dneg
   
      a = 1.5;
      b = 0.9;
      SystemOut.print("\nwant: 0.6\n got: ");   SystemOut.println(a % b);  // drem
      }

   static void nanTestFloat()
      {
      float zero = 0;
      float NaN = zero / zero;
      
      SystemOut.print("  expr     expected    got    \n");
      SystemOut.print("---------- -------- ----------\n");
      SystemOut.print("NaN <  NaN  false     " + (NaN <  NaN) + "\n");
      SystemOut.print("NaN <= NaN  false     " + (NaN <= NaN) + "\n");
      SystemOut.print("NaN == NaN  false     " + (NaN == NaN) + "\n");
      SystemOut.print("NaN != NaN  true      " + (NaN != NaN) + "\n");
      SystemOut.print("NaN >= NaN  false     " + (NaN >= NaN) + "\n");
      SystemOut.print("NaN >  NaN  false     " + (NaN >  NaN) + "\n");
      }

   static void nanTestDouble()
      {
      double zero = 0;
      double NaN = zero / zero;
      
      SystemOut.print("  expr     expected    got    \n");
      SystemOut.print("---------- -------- ----------\n");
      SystemOut.print("NaN <  NaN  false     " + (NaN <  NaN) + "\n");
      SystemOut.print("NaN <= NaN  false     " + (NaN <= NaN) + "\n");
      SystemOut.print("NaN == NaN  false     " + (NaN == NaN) + "\n");
      SystemOut.print("NaN != NaN  true      " + (NaN != NaN) + "\n");
      SystemOut.print("NaN >= NaN  false     " + (NaN >= NaN) + "\n");
      SystemOut.print("NaN >  NaN  false     " + (NaN >  NaN) + "\n");
      }

   static void remTest()
      {
      rem(+2,+3);
      rem(+2,-3);
      rem(-2,+3);
      rem(-2,-3);
      }
      
   static void rem(double a, double b)
      {
      SystemOut.println( a + "  /  " + b + "=" + (a/b) );
      SystemOut.println( a + "  %  " + b + "=" + (a%b) );
      SystemOut.println( a + " rem " + b + "=" + Math.IEEEremainder(a,b));
      SystemOut.println();
      }
   }
