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
public class TestUnresolved {

   public static void main(String[] args) {
      run();
   }

   static boolean run() {
      System.out.println(foo(temp1.x));
      System.out.println(foo1(temp1.x));
      System.out.println(foo1(temp1.x));
      System.out.println(foo2());
      System.out.println(foo2());
      System.out.println(foo3());
      System.out.println(foo3());
      foo4();
      new temp4(10).bar();

      temp5[] test = new temp5[2];
      test[0] = new temp5();
      test[1] = new temp6();
      for (int i=0; i< 2; i++) {
        System.out.println(test[i].foo());
      }
      return true;
   }

   static int foo(temp a) {
       a.a++;
       return a.a;
   }

   static long foo1(temp a) {
       long t = a.c;
       a.c = t +1;
       return t;
   }

   static long foo2() {
       long t = temp1.y;
       temp1.y = t+1;
       return t;
   }

   static double foo3() {
       double t = temp1.z;
       temp1.z = t+1;
       return t;
   }


   static void foo4() {
      temp3 x = new temp3();
      x.key = 2L;
      System.out.println(x.key);
      long t = temp3.temp;
      t = t +1;
      temp3.temp = t;
      System.out.println(t);
      System.out.println(temp3.temp);
   }

}

// NOTE: These classes should not be compiled with the opt-compiler
//  so that unresolved works

class temp {
    int a = 1, b;
    long c = 1, d;
}

class temp1 {
    static temp x = new temp();
    static long y = 1;
    static double z = 1.0;
}


class temp3 {

   static long temp = 1L;
   Object node;
   long   key;
   Object val;
   }

class temp4 {
  int y;

  temp4(int a) {
    y = a;
  }
  private void foo(int x) {
    System.out.println(x * 2);
  }
  public void bar() {
    foo(y);
  }
}
class temp5 {
  int foo() { return 1; }
}

class temp6 extends temp5 {
  int foo() { return 2; }
}
