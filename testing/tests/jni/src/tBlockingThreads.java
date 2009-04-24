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
import org.jikesrvm.scheduler.RVMThread;

/**
 * Test native methods that block in native code
 */

class tBlockingThreads {

  static final boolean FORCE_GC = true;
  static final int NUMBER_OF_WORKERS = 2;

  public static native int nativeBlocking(int time);

  public static void main(String[] args) {
    int time;

    System.out.println("Testing threads that block in native code with WORKERS =" + NUMBER_OF_WORKERS);

    System.out.println("Attempting to load dynamic library ...");
    System.out.println("(the LIBPATH env variable must be set for this directory)");

    System.loadLibrary("tBlockingThreads");

    if (NUMBER_OF_WORKERS == 0) {
      // have main thread make the native blocking call
      for (int i=1; i < 5; i++) {
        time = 1 * i;
        RVMThread.trace("main","calling nativeBlocking for time = ",time);
        nativeBlocking(time);
        RVMThread.trace("main","returned from nativeBlocking for time = ",time);
      }
    } else {
      // create worker threads which each make repeated native blocking calls
      BlockingThreadsWorker[] a = new BlockingThreadsWorker[NUMBER_OF_WORKERS];
      for (int wrk = 0; wrk < NUMBER_OF_WORKERS; wrk++) {
          if (wrk%2 == 0)
            a[wrk] = new BlockingThreadsWorker(1);
          else
            a[wrk] = new BlockingThreadsWorker(2);

          a[wrk].start();
        }

      for (int i = 0; i < NUMBER_OF_WORKERS; i ++) {
        int cntr = 1;
        while(!a[i].isFinished) {

          try {
            Thread.currentThread().sleep(100);
          } catch (InterruptedException e) {}

          cntr++;
          if (cntr%1000 == 0)
            RVMThread.trace("main","waiting for worker",i);

          if (FORCE_GC) {
            System.out.println("\nMain calling System.gc:\n");
            System.gc();
          }

        }
      }

      System.out.println("Finished");
    } // use Worker Threads

  } // main

}
