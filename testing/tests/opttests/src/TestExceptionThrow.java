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
import java.io.FileInputStream;
import java.io.IOException;

class MyErrorBase extends Throwable {
     private static final long serialVersionUID = 0L;
}

class MyError extends MyErrorBase {
     private static final long serialVersionUID = 0L;
}

class NotMyError extends Throwable {
     private static final long serialVersionUID = 0L;
}

class TestExceptionThrow {
   public static void main(String[] args) throws Throwable {
      run();
   }

   public static boolean run() throws Throwable {
      boolean correct = true;
      System.out.println("run1");
      if (!run1()) correct = false;
      System.out.println("run2");
      if (!run2()) correct = false;
      System.out.println("run3");
      if (!run3()) correct = false;
      System.out.println("run4");
      if (!run4()) correct = false;
      System.out.println("run5");
      if (!run5()) correct = false;
      System.out.println("run6");
      if (!run6()) correct = false;
      System.out.println("run7");
      if (!run7()) correct = false;
      System.out.println("run8");
      if (!run8()) correct = false;
      System.out.println("run9");
      if (!run9()) correct = false;
      System.out.println("run10");
      if (!run10()) correct = false;
      //if (!run11()) correct = false;
      System.out.println("run12");
      if (!run12()) correct = false;
      System.out.println("run13");
      if (!run13()) correct = false;
      System.out.println("run14");
      if (!run14()) correct = false;
      System.out.println("run15");
      if (!run15()) correct = false;
      System.out.println("run16");
      if (!run16()) correct = false;
      System.out.println("run17");
      if (!run17()) correct = false;
      System.out.println("run18");
      if (!run18()) correct = false;
      return correct;
   }


   public static boolean run1() throws Throwable {
      System.out.println("TestExceptionThrow");
      int a = 1;
      int b = 2;
      // test "user" exceptions
      try {
         int c = a + b * foo1();
         System.out.println(c);
      } catch (MyErrorBase  e) {
         System.out.println("caught: " + e);
      }

      // test "vm" exceptions
      try {
         FileInputStream s = new FileInputStream("xyzzy");
         System.out.println(s);
      } catch (IOException e) {
         System.out.println("caught: " + e.getClass());
      }
      return true;
   }

   static int foo1() throws MyError,NotMyError {
      if (true) {
        throw new    MyError();
      } else {
        throw new NotMyError();
      }
   }

    static int[] testa = new int[3];

     public static boolean run2() {
       try {
         return run2a();
       } catch (IndexOutOfBoundsException e5) {
         System.out.println(" IndexOutOfBoundsException: '" + e5 + "', but caught in run()!!!");
       }
       System.out.println(" At End");
       return true;
     }

     public static boolean run2a() throws IndexOutOfBoundsException {
       return run2b();
     }

     public static boolean run2b() throws IndexOutOfBoundsException {
       return run2c();
     }

     public static boolean run2c() throws IndexOutOfBoundsException {
       return run2d();
     }

     public static boolean run2d() throws IndexOutOfBoundsException {
       return run2e();
     }

     public static boolean run2e() throws IndexOutOfBoundsException {
       return run2f();
     }

     public static boolean run2f() throws IndexOutOfBoundsException {
       throw new IndexOutOfBoundsException("I was thrwon in run7()!!!");
     }


   static int[] test3 = null; //new int[3];

   public static boolean run3() {
      try {
        test3[4] = 0;
      } catch (IndexOutOfBoundsException e5) {
        System.out.println(" IndexOutOfBoundsException caught");
      } catch (NullPointerException e) {
        System.out.println(" NullPointerException");
      }
      System.out.println(" At End");
      return true;
   }



   public static boolean run4() {
       System.out.println(divide(1,0));
       return true;
   }

   static int divide(int a, int b) {
     try {
         return a / b;
     } catch (ArithmeticException e) {
         return a + 1;
     }
   }



   static int[] test5 = null; //new int[3];

   public static boolean run5() {
    try {
      foo5a();
    } catch (IndexOutOfBoundsException e5) {
      System.out.println(" IndexOutOfBoundsException caught");
    } catch (NullPointerException e) {
      System.out.println(" NullPointerException");
    }
    System.out.println(" At End");

    return true;
  }

  public static void foo5a() {
      foo5b();
  }

  public static void foo5b() {
      testa[4] = 0;
  }



   public static boolean run6() {
       System.out.println(access(4));
       return true;
   }

   static int access(int i) {
     try {
         return testa[i];
     } catch (ArrayIndexOutOfBoundsException e) {
         return i + 1;
     }
   }


   public static boolean run7() throws Throwable {
      System.out.println("TestThrow");

      // test "user" exceptions
      try {
         int a = 1;
         int b = 2;
         int c = a + b * foo7();
         System.out.println(c);
      } catch (MyErrorBase  e) {
         System.out.println("caught: " + e);
         // e.printStackTrace(System.out);     // !!TODO: fix backtrace so it omits <init> functions for throwables
      }

      // test "vm" exceptions
      try {
         FileInputStream s = new FileInputStream("xyzzy");
         System.out.println(s);
      } catch (IOException e) {
         System.out.println("caught: " + e.getClass());
      }
      return true;
   }

   static int foo7() throws MyError,NotMyError {
      if (true) {
        throw new MyError();
      } else {
        throw new NotMyError();
      }
   }


     // very similar to run5(), but throw is inline instead of in callee method
   public static boolean run8() throws Throwable {
     try {
       if (testa.length <= 3)
         throw new IndexOutOfBoundsException("I am IndexOBE");
       testa[3] = 0;
     } catch (NullPointerException n) {
       System.out.println(n + ", but caught by NullPointCheckException");
     } catch (ArithmeticException a) {
       System.out.println(a + ", but caught by ArithMeticException");
     } catch (IndexOutOfBoundsException e5) {
       System.out.println(" IndexOutOfBoundsException caught");
     }
     System.out.println(" At End");
     return true;
   }

  public static boolean run9() {
    try {
      foo9(1);
      try {
        foo9(2);
        try {
          foo9(3);
        } catch (IndexOutOfBoundsException e1) {
          System.out.println(" so [0].");
          try {
            foo9(4);
            try {
              foo9(5);
            } catch (IndexOutOfBoundsException e2) {
              System.out.println(" so [1].");
            }
          } catch (IndexOutOfBoundsException e3) {
            System.out.println(" so [2].");
          }
        }
      } catch (IndexOutOfBoundsException e4) {
        System.out.println(" so [4].");
      }
    } catch (IndexOutOfBoundsException e5) {
      System.out.println(" so.");
    }
    System.out.println(" At End");

    return true;
  }

  public static void foo9(int i) {
    try {
      System.out.println("does it work? " + i + "   ");
      if ((i < 0) || (i > 2)) {
        System.out.println(" IndexOutOfBoundsException with index = " + i);
        throw new IndexOutOfBoundsException();
      }
    } catch (IndexOutOfBoundsException e) {
      System.out.println(" NullPointerException caught.");
      System.out.println(" Will throw again");
      throw e;
    }
  }

  static int a10 = 0;
  static int b10 = 1;

  public static boolean run10() throws NullPointerException {
     try {
        throw new NullPointerException();
     } catch (Exception e) {

     }
     try {
        int x = b10 / a10;
     } catch (Exception e) {
     }
     return true;
  }

  public static boolean run11() {
     run11aux(null);
     return true;
  }

  static String run11s1 = "";
  static StringBuffer run11s2;

  public static void run11a(String a) { }
  public static String run11b() {
    return "test";
  };

  public static void run11aux(Object a) {
     if (run11s1.equals(""))
        run11a("Global");
     try {
        run11s2.append(run11b());
     } catch (Exception e) {
     }
     run11s2.append(run11b());
     if (run11s2 != null)
        run11s2.append("test");
  }


   public static boolean run12() {
       try {
         System.out.println(divide1(1,0));
       } catch (ArithmeticException e) {
         System.out.println("caught run12");
       }
       return true;
   }

   static int divide1(int a, int b) {
         return a / b;
   }

   public static boolean run13() {
     try {
       System.out.println(longDivide1(1L,0L));
     } catch (ArithmeticException e) {
       System.out.println("caught run13");
     }
     return true;
   }

   static long longDivide1(long a, long b) {
         return a / b;
   }

   public static boolean run14() {
     System.out.println(longDivideCatch(5L,0L));
     return true;
   }

   static long longDivideCatch(long a, long b) {
     try {
       return a / b;
     } catch (ArithmeticException ae) {
       return a / (b + 1);
     }
   }

   public static boolean run15() {
     try {
       System.out.println(longRem(Long.MAX_VALUE,0L));
     } catch (ArithmeticException e) {
       System.out.println("caught run15");
     }
     return true;
   }

   static long longRem(long a, long b) {
     return a % b;
   }

   public static boolean run16() {
     System.out.println(longRemCatch(Long.MAX_VALUE,0L));
     return true;
   }

   static long longRemCatch(long a, long b) {
     try {
       return a % b;
     } catch (ArithmeticException ae) {
       return a % (b + 4);
     }
   }

   public static boolean run17() {
     try {
       System.out.println(intRem(Integer.MAX_VALUE,0));
     } catch (ArithmeticException ae) {
       System.out.println("caught run17");
     }
     return true;
   }

   static int intRem(int a, int b) {
     return a % b;
   }

   public static boolean run18() {
     System.out.println(intRemCatch(Integer.MAX_VALUE,0));
     return true;
   }

   static int intRemCatch(int a, int b) {
     try {
       return a % b;
     } catch (ArithmeticException ae) {
       return a % (b + 13);
     }
   }

}
