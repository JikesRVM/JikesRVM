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
class logic {

   static boolean not(boolean a) {
       return !a;
   }

   static boolean and(boolean a, boolean b) {
      return a && b;
   }

   static boolean or(boolean a, boolean b) {
      return a || b;
   }

   static boolean notA(boolean a) {
     return !a;
   }

   static boolean notB(boolean a) {
      if (a)
        return false;
      return true;
   }
   static boolean aja(int a) {
      return (a & 0x1f) < 0;
   }

   static boolean ajaja(int a, int b) {
      return a != b;
   }

   static int  eje(int a) {
    if (aja(a))
       return 0;
    else
       return 1;
   }

   static int  iji(int a) {
    if (aja(a))
       return 3;
    else
       return 5;
   }

   static int  ojo(int a, int b) {
    if (ajaja(a,b))
       return 3;
    else
       return 5;
   }

   static boolean uju(Object a) {
      return a == null;
   }

   static int[] array;

   static int uju1() {
      if (array != null)
         return array[1];
      return 0;
   }

   static int test(int a) {
      if ((a & 0xf) == 0)
         return 3;
      else
         return 5;
   }

   static int insert(int a) {
      return (a & 0xFC07FFFF) | 0x580000;
   }

   static int insert1(int a) {
      return (a & 0xFFFFFF0F) | 0x50;
   }

   static int insert2(int a) {
      return (a & 0xFFF00FFF) | 0x15000;
   }

   static int insert3(int a, int b) {
      return (a & ~0x03F80000) | (b & 0x03F80000);
   }

   static int extract(int a) {
      return (a >>> 8) & 0xF;
   }

static int extract1(int a) {
    return (a >>> 8) & 0xE;
}


static int extract2(int a) {
    return (a >>> 28) & 0xFFFF;
}

static int extract3(int a) {
    return (a >>> 28) & 0xFF00;
}

static int extract4(int a) {
    return (a << 12) >>> 4;
}

static int extract5(int a) {
    return (a <<  4) >>> 12;
}

static int extract6(int a) {
    return (a >>> 4) << 12;
}

static int extract7(int a) {
    return (a >>>12) <<  4;
}


static int extract8(int a) {
    return (a >>12) >>  4;
}

static int extract9(int a) {
    return (a <<12) <<  4;
}

static int extract10(int a) {
    return (a & 0x7ffff) >>> 28;
}

static int extract11(int a) {
    return (a & 0x7ffff000) >>> 8;
}

static int extract12(int a) {
    return (a & 0x7ffff000) >>> 28;
}



static int andI(int a) {
    return a & 0x7ffff0;
}

static char charS;
static short shortS;
static byte byteS;
static boolean booleanS;

static void store(int a, boolean b) {
   charS = (char)a;
   shortS= (short)a;
   byteS = (byte)a;
   booleanS = b;
}


   public static void main(String[] args) {
      run();
   }

   public static boolean run() {
     System.out.println(extract(-1));
     System.out.println(extract1(-1));
     System.out.println(extract2(-1));
     System.out.println(extract3(-1));
     System.out.println(extract4(-1));
     System.out.println(extract5(-1));
     System.out.println(extract6(-1));
     System.out.println(extract7(-1));
     System.out.println(extract8(-1));
     System.out.println(extract9(-1));
     System.out.println(extract10(-1));
     System.out.println(extract11(-1));
     System.out.println(extract12(-1));
     System.out.println(insert(0));
     System.out.println(insert1(0));
     System.out.println(insert2(0));
     return true;
   }
}
