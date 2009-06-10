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
class TestDeadVPQueueWorker extends Thread {

   static final int starting      = 1;
   static final int inConstructor = 2;
   static final int running       = 3;
   static final int ending        = 4;

   int    state = starting;
   String myname;

   static int count = 0;

   TestDeadVPQueueWorker(String name) {
      myname = name;
      state = inConstructor;
   }

 public static native int nativeFoo(int count);

   public void run() {
        state = running;
        count++;

        System.out.println(myname + ": running count = " + count);

        int returnValue = nativeFoo(17);
        System.out.println("First nativeFoo return " + returnValue);

        returnValue = nativeFoo(30);
        System.out.println("Second nativeFoo return " + returnValue);


        state = ending;
      }
   }
