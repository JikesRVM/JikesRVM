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
class TestCall {
   public static void main(String[] args) {
      run();
   }

   public static boolean run() {
      System.out.println("TestCall");
      System.out.print("want: 5\n");
      System.out.print(" got: "+fooI(1,2,3,4,5,6,7,8,9,10,11,12,1)+"\n");
      System.out.print("want: -4\n");
      System.out.print(" got: "+fooIR(1,2,3,4,5,6,7,8,9,10,11,12,1)+"\n");
      System.out.print("want: 3\n");
      System.out.print(" got: "+fooL(1L,2L,3L,4L,0,5L,6L,7L,8L,1L)+"\n");
      System.out.print("want: 7.0\n");
      System.out.print(" got: "+fooF(1.F,2.F,3.F,4.F,5.F,6.F,7.F,8.F,
                                     9.F,10.F,11.F,12.F,13.F,14.F,15.F,16.F,1.F)+"\n");
      System.out.print("want: 211.0 \n");
      System.out.print(" got: "+fooID(1,1.,2,2.,3,3.,4,4.,5,5.,6,6.,7,7.,
                                      8,8.,9,9.,10,10.,11,11.,12,12.,13,13.,14.,15.)+"\n");
      System.out.print("want: 7.0\n");
      System.out.print(" got: "+fooD(1.,2.,3.,4.,5.,6.,7.,8.,
                                     9.,10.,11.,12.,13.,14.,15.,16.,1.)+"\n");
      return true;
   }

   static int fooI(int a1, int a2, int a3, int a4, int a5, int a6, int a7, int a8,
              int a9, int a10, int a11, int a12, int a13) {
      return fooI1(a1,a2,a3,a4,a5,a6,a7,a8,a9,a10,a11,a12,a13);
   }

   static int fooIR(int a1, int a2, int a3, int a4, int a5, int a6, int a7, int a8,
              int a9, int a10, int a11, int a12, int a13) {
      return fooI1(a10,a9,a8,a7,a6,a5,a4,a3,a2,a1,a11,a12,a13)+a1;
   }


   static int fooI1(int a1, int a2, int a3, int a4, int a5, int a6, int a7, int a8,
              int a9, int a10, int a11, int a12, int a13) {
       return -a1+a2-a3+a4-a5+a6-a7+a8-a9+a10-a11+a12-a13;
   }

   static int fooI2(int a1, int a2, int a3, int a4, int a5, int a6, int a7, int a8,
              int a9, int a10, int a11, int a12, int a13) {
       return a12+a13+a12;
   }



   static long fooL(long a1, long a2, long a3, long a4, int i, long a5,
                    long a6, long a7, long a8, long a9) {
       return fooL1(a1,a2,a3,a4,i,a5,a6,a7,a8,a9);
   }

   static long fooL1(long a1, long a2, long a3, long a4, int i, long a5,
                    long a6, long a7, long a8, long a9) {
       return -a1+a2-a3+a4-a5+a6-a7+a8-a9;
   }


   static float fooF(float a1, float a2, float a3, float a4, float a5, float a6,
              float a7, float a8,
              float a9, float a10, float a11, float a12, float a13,
              float a14, float a15, float a16, float a17) {
      return fooF1(a1,a2,a3,a4,a5,a6,a7,a8,a9,a10,a11,a12,a13,a14,a15,a16,a17);
   }

   static float fooF1(float a1, float a2, float a3, float a4, float a5, float a6,
              float a7, float a8,
              float a9, float a10, float a11, float a12, float a13,
              float a14, float a15, float a16, float a17) {
       return -a1+a2-a3+a4-a5+a6-a7+a8-a9+a10-a11+a12-a13+a14-a15+a16-a17;
   }

   static double fooD(double a1, double a2, double a3, double a4, double a5, double a6,
              double a7, double a8,
              double a9, double a10, double a11, double a12, double a13,
              double a14, double a15, double a16, double a17) {
      return fooD1(a1,a2,a3,a4,a5,a6,a7,a8,a9,a10,a11,a12,a13,a14,a15,a16,a17);
   }

   static double fooD1(double a1, double a2, double a3, double a4, double a5, double a6,
              double a7, double a8,
              double a9, double a10, double a11, double a12, double a13,
              double a14, double a15, double a16, double a17) {
       return -a1+a2-a3+a4-a5+a6-a7+a8-a9+a10-a11+a12-a13+a14-a15+a16-a17;
   }

   static double fooID(int a1, double f1, int a2, double f2, int a3,  double f3,
                       int a4, double f4, int a5, double f5, int a6,  double f6,
                       int a7, double f7, int a8, double f8, int a9,  double f9,
                       int a10,double f10,int a11,double f11,int a12, double f12,
                       int a13,double f13,
                       double f14, double f15) {
      int i    = a1+a2+a3+a4+a5+a6+a7+a8+a9+a10+a11+a12+a13;
      double d = f1+f2+f3+f4+f5+f6+f7+f8+f9+f10+f11+f12+f13+f14+f15;
      return i+d;
   }


}

