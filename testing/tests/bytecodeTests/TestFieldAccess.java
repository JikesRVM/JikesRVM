/*
 * (C) Copyright IBM Corp. 2001
 */
//$Id$
/**
 * @author unascribed
 */
class TestFieldAccess
   {
   static boolean s0 = true;
   static byte    s1 = -1;
   static char    s2 = 0x41;     // 'A'
   static short   s3 = -3;
   static int     s4 = -4;
   static long    s5 = -5;
   static float   s6 = -6;
   static double  s7 = -7;
   static Object  s8 = new TestFieldAccess();

          boolean x0 = true;
          byte    x1 = -1;
          char    x2 = 0x41;     // 'A'
          short   x3 = -3;
          int     x4 = -4;
          long    x5 = -5;
          float   x6 = -6;
          double  x7 = -7;
          Object  x8 = this;

   public String toString() { return "Instance of " + getClass().getName(); }

   public static void main(String args[])
      {
   // VM.boot();
      runTest();
      }

   public static void runTest()
      {
      SystemOut.println("TestFieldAccess");

      TestFieldAccess b = new TestFieldAccess();

      SystemOut.print("\nwant: true\n got: ");                        SystemOut.println(b.s0);
      SystemOut.print("\nwant: -1\n got: ");                          SystemOut.println(b.s1);
      SystemOut.print("\nwant: A\n got: ");                           SystemOut.println(b.s2);
      SystemOut.print("\nwant: -3\n got: ");                          SystemOut.println(b.s3);
      SystemOut.print("\nwant: -4\n got: ");                          SystemOut.println(b.s4);
      SystemOut.print("\nwant: -5\n got: ");                          SystemOut.println(b.s5);
      SystemOut.print("\nwant: -6.0\n got: ");                        SystemOut.println(b.s6);
      SystemOut.print("\nwant: -7.0\n got: ");                        SystemOut.println(b.s7);
      SystemOut.print("\nwant: Instance of TestFieldAccess\n got: "); SystemOut.println(b.s8);

      SystemOut.print("\nwant: true\n got: ");                        SystemOut.println(b.x0);
      SystemOut.print("\nwant: -1\n got: ");                          SystemOut.println(b.x1);
      SystemOut.print("\nwant: A\n got: ");                           SystemOut.println(b.x2);
      SystemOut.print("\nwant: -3\n got: ");                          SystemOut.println(b.x3);
      SystemOut.print("\nwant: -4\n got: ");                          SystemOut.println(b.x4);
      SystemOut.print("\nwant: -5\n got: ");                          SystemOut.println(b.x5);
      SystemOut.print("\nwant: -6.0\n got: ");                        SystemOut.println(b.x6);
      SystemOut.print("\nwant: -7.0\n got: ");                        SystemOut.println(b.x7);
      SystemOut.print("\nwant: Instance of TestFieldAccess\n got: "); SystemOut.println(b.x8);
      }
   }
