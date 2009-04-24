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

/**
 * Test native method with threads
 */
class tNativeThreads {

  static final int NUMBER_OF_WORKERS = 5;

  public static native int nativeFoo(int count);

  public static        int javaFoo(int count) {
    NativeThreadsWorker.say("tNativeThreads.javaFoo"," - entered and about to return");
    return count +1;
  }

  public static void main(String[] args) {

    // RVMThread.dumpVirtualMachine();

    System.out.println("Attempting to load dynamic library ...");
    System.out.println("(the LIBPATH env variable must be set for this directory)");

    System.loadLibrary("tNativeThreads");



      System.out.println("starting TestDispatch stuff");

      NativeThreadsWorker[] a = new NativeThreadsWorker[NUMBER_OF_WORKERS];
      for (int wrk = 0; wrk < NUMBER_OF_WORKERS; wrk++) {
           a[wrk] = new NativeThreadsWorker("ping");
           a[wrk].start();
         }

      NativeThreadsWorker b = new NativeThreadsWorker("pong");
      b.start();

      while (!b.isFinished)
          Thread.currentThread().yield();

      //count number of workers that completed
      //
      int cntr = 0;
      for (int i = 0; i < NUMBER_OF_WORKERS; i++) {
          if (a[i].isFinished)
             cntr++;
      }
      if (cntr < NUMBER_OF_WORKERS) {

        //     RVMThread.dumpVirtualMachine();
      }


      for (int wrk = 0; wrk < NUMBER_OF_WORKERS; wrk ++)
        while (!a[wrk].isFinished) {
          try {
            //say(name, "sleeping");
            Thread.currentThread().sleep(300);
          } catch (InterruptedException e) {}
          Thread.currentThread().yield();
        }

      //      RVMThread.dumpVirtualMachine();
  }
}






