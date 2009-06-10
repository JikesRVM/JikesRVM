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
public class threadTest1 {

    static final int  NUM_THREADS = 10;

    // simulate shared data
    static       int  a          = 0;
    static       int  b          = 0;

    static       int[]  aResult = new int[NUM_THREADS];
    static       int[]  bResult = new int[NUM_THREADS];

    public static void main(String[] args) {

        int xx;
        int yy;
        System.out.println(" Counter started.");

        TestThread1[] thread = new TestThread1[NUM_THREADS];

         for (int i = 0; i < NUM_THREADS; i ++) {
                         aResult[i] = 0;
                         bResult[i] = 0;
            thread[i] = new TestThread1(i);
                        thread[i].start();
         }

         System.out.println("a = " + a + ", b = " + b);

         int idx = 0;
         xx = 5;
         yy = 5;
         idx = xx - yy;
         while (idx < NUM_THREADS) {
                 while (aResult[idx] == 0) {};

                 idx ++;
         }

         for (int i = 0; i < NUM_THREADS; i ++) {
                 System.out.println("Final[" + i + "]: a = " + aResult[i] +
                                        ", b = " + bResult[i]);
         }
    }
}


// prototype of the user thread
class TestThread1 extends Thread {

    TestThread1(int id) { _tid = id;}

    public void run() {

       for (int i = 0; i < 10000; i++) {
            threadTest1.a++;
            threadTest1.b += 2;
       }

       if (_tid*2 > threadTest1.NUM_THREADS) {
         for (int i = 0; i < 10000; i++) {
            threadTest1.a++;
            threadTest1.b += 2;
         }

       }

       System.out.println("Final: [" + _tid + "]" + " a = " + threadTest1.a + ", b = " + threadTest1.b);
       threadTest1.aResult[_tid] =  threadTest1.a;
       threadTest1.bResult[_tid] =  threadTest1.b;
    }

    private int _tid;
}
