/*
 * (C) Copyright IBM Corp. 2001
 */
//$Id$
/**
 * @author unascribed
 */
class TestConversions_toHex
   {
   public static void main(String args[])
      {
   // VM.boot();
      runTest();
      }

   public static void runTest()
      {
      SystemOut.println("TestConversions");

      SystemOut.println("\n-- i2b --"); i2b();
      SystemOut.println("\n-- i2c --"); i2c();
      SystemOut.println("\n-- i2s --"); i2s();
      
      SystemOut.println("\n-- i2l --"); i2l();
      SystemOut.println("\n-- l2i --"); l2i();

      SystemOut.println("\n-- i2f --"); i2f();
      SystemOut.println("\n-- i2d --"); i2d();
   
      SystemOut.println("\n-- l2f --"); l2f();
      SystemOut.println("\n-- l2d --"); l2d();

      SystemOut.println("\n-- f2d --"); f2d();
      SystemOut.println("\n-- f2l --"); f2l();
      SystemOut.println("\n-- f2i --"); f2i();

      SystemOut.println("\n-- d2i --"); d2i();
      SystemOut.println("\n-- d2l --"); d2l();
      SystemOut.println("\n-- d2f --"); d2f();

      testFloatLimits( Float.NEGATIVE_INFINITY,   Float.POSITIVE_INFINITY);
      testFloatLimits( Float.MIN_VALUE,           Float.MAX_VALUE);
      testFloatLimits(-Float.MAX_VALUE,           Float.MAX_VALUE);
      testFloatLimits(-99999F,                    99999F);

      testDoubleLimits( Double.NEGATIVE_INFINITY, Double.POSITIVE_INFINITY);
      testDoubleLimits( Double.MIN_VALUE,         Double.MAX_VALUE);
      testDoubleLimits(-Double.MAX_VALUE,         Double.MAX_VALUE);
      testDoubleLimits(-99999D,                   99999D);
      
//!!TODO: baseline compiler fails on this...
//    testLongDoubleLong();
      }
      
   static void
   i2b()
      {
      byte x; int  i;
      i = 0x0000007f; x = (byte)i; i = x; SystemOut.print("\nwant: 127\n got: "); SystemOut.println(i);
      i = 0x000000ff; x = (byte)i; i = x; SystemOut.print("\nwant: -1\n got: ");  SystemOut.println(i);
      i = 0xffffffff; x = (byte)i; i = x; SystemOut.print("\nwant: -1\n got: ");  SystemOut.println(i);
      }
      
   static void
   i2c()
      {
      char x; int  i;
      i = 0x0000007f; x = (char)i; i = x; SystemOut.print("\nwant: 127\n got: ");   SystemOut.println(i);
      i = 0x000000ff; x = (char)i; i = x; SystemOut.print("\nwant: 255\n got: ");   SystemOut.println(i);
      i = 0xffffffff; x = (char)i; i = x; SystemOut.print("\nwant: 65535\n got: "); SystemOut.println(i);
      }
      
   static void
   i2s()
      {
      short x; int  i;
      i = 0x00007fff; x = (short)i; i = x; SystemOut.print("\nwant: 32767\n got: "); SystemOut.println(i);
      i = 0x0000ffff; x = (short)i; i = x; SystemOut.print("\nwant: -1\n got: ");    SystemOut.println(i);
      }

   static void
   i2l()
      {
      long x; int  i;
      i = 0x7fffffff; x = (long)i; SystemOut.print("\nwant: 2147483647\n got: "); SystemOut.println(x);
      i = 0xffffffff; x = (long)i; SystemOut.print("\nwant: -1\n got: ");         SystemOut.println(x);
      }

   static void
   l2i()
      {
      long x; int  i;
      x = 0x000000007fffffffL; i = (int)x; SystemOut.print("\nwant: 2147483647\n got: "); SystemOut.println(i);
      x = 0x00000000ffffffffL; i = (int)x; SystemOut.print("\nwant: -1\n got: ");         SystemOut.println(i);
      }

   static void
   i2f()
      {
      int   i = -2;
      float f = i;
      SystemOut.print("\nwant: " + Integer.toHexString(Float.floatToIntBits(-2.0F)) + "\n got: ");
      SystemOut.println(Integer.toHexString(Float.floatToIntBits(f)));
      }

   static void
   l2f()
      {
      long  l = -2;
      float f = l;
      SystemOut.print("\nwant: " + Integer.toHexString(Float.floatToIntBits(-2.0F)) + "\n got: ");
      SystemOut.println(Integer.toHexString(Float.floatToIntBits(f)));
      }

   static void
   l2d()
      {
      long   l = -2;
      double d = l;
      SystemOut.print("\nwant: -2.0\n got: "); SystemOut.println(d);
      }

   static void
   i2d()
      {
      int    i = -2;
      double d = i;
      SystemOut.print("\nwant: -2.0\n got: "); SystemOut.println(d);
      }

   static void
   f2d()
      {
      float  f = -2.6F;
      double d = f;
      SystemOut.print("\nwant: c004ccccc0000000\n got: ");
      SystemOut.println(Long.toHexString(Double.doubleToLongBits(d)));
      }

   static void
   f2l()
      {
      float  f = -2.6F;
      long   l = (long)f;
      SystemOut.print("\nwant: -2\n got: "); SystemOut.println(l);
      }

   static void
   f2i()
      {
      float  f = -2.6F;
      int    i = (int)f;
      SystemOut.print("\nwant: -2\n got: "); SystemOut.println(i);
      }
   
   static void
   d2i()
      {
      double d = -2.6;
      int    i = (int)d;
      SystemOut.print("\nwant: -2\n got: "); SystemOut.println(i);
      }

   static void
   d2l()
      {
      double d = -2.6;
      long   l = (long)d;
      SystemOut.print("\nwant: -2\n got: "); SystemOut.println(l);
      }

   static void
   d2f()
      {
      double d = -2.6;
      float  f = (float)d;
      SystemOut.print("\nwant: " + Integer.toHexString(Float.floatToIntBits(-2.6F)) + "\n got: ");
      SystemOut.println(Integer.toHexString(Float.floatToIntBits(f)));
      }

   static void
   testFloatLimits(float lo, float hi)
      {
      SystemOut.println();
      SystemOut.println("float:  " + Integer.toHexString(Float.floatToIntBits(lo)) + " .. " +
				     Integer.toHexString(Float.floatToIntBits(hi )));
      SystemOut.println("long:   " + (long)lo + " .. " + (long)hi);
      SystemOut.println("int:    " + (int )lo + " .. " + (int )hi);
      }

   static void
   testDoubleLimits(double lo, double hi)
      {
      SystemOut.println();
      SystemOut.println("double: " + Long.toHexString(Double.doubleToLongBits(lo)) + " .. " +
				     Long.toHexString(Double.doubleToLongBits(hi)));
      SystemOut.println("long:   " + (long)lo + " .. " + (long)hi);
      SystemOut.println("int:    " + (int )lo + " .. " + (int )hi);
      }

   static long x[] = {
      0x8000000000000000L,
      0xF000000000000000L,
      0xF0000000FFFFFFFFL,
      0xF0FFFFFF0FFFFFFFL,
      0xFFFFFFFF00000000L,
      0xFFFFFFFF0FFFFFFFL,
      0xFFFFFFFFFFFFFFFFL,
      0x0000000000000000L,
      0x00000000F0000000L,
      0x00000000FFFFFFFFL,
      0x0F000000F0000000L,
      0x0FFFFFFF00000000L,
      0x0FFFFFFFFFFFFFFFL,
      0x7FFFFFFFFFFFFFFFL,
      };
      
   static void
   testLongDoubleLong()
      {
      SystemOut.print("\n-- long double long --\n");
      for (int i = 0; i < x.length; ++i)
        SystemOut.print("\nwant: " + x[i] + "\ngot:  " + ((long)(double) x[i]) + "\n");
      }
   }
