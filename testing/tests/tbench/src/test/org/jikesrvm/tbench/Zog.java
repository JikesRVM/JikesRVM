/*
 *  This file is part of the Jikes RVM project (http://jikesrvm.org).
 *
 *  This file is licensed to You under the Common Public License (CPL);
 *  You may not use this file except in compliance with the License. You
 *  may obtain a copy of the License at
 *
 *      http://www.opensource.org/licenses/cpl1.0.php
 *
 *  See the COPYRIGHT.txt file distributed with this work for information
 *  regarding copyright ownership.
 */
package test.org.jikesrvm.tbench;

/**
 * This is based on the "benchmark" used to demonstrate differenced between
 * the cost of erlang processes and java threads (Originally at Joe Armstrongs
 * web page linked below). It gives us a simple basis with which to compare
 * costs of thread creation and synchronization.
 *
 * <p>Why is it called zog? Who knows but it keeps the flavour of the orginal
 * "benchmark" so lets keep it. ZugZug is "yes-yes" in the warcraft games!</p>
 *
 * @link http://www.sics.se/~joe/ericsson/du98024.html
 */
public class Zog extends Thread {
  private static final boolean DEBUG = false;

  private Zog next;
  private boolean flag;
  private int message;

  public void link(final Zog zog) {
    next = zog;
  }

  public void run() {
    try {
      do this.relay(); while (message > 0);
    } catch (InterruptedException e) {
      e.printStackTrace();
      System.exit(66);
    }
  }

  private synchronized void relay() throws InterruptedException {
    while (!flag) wait();
    flag = false;
    next.send(message - 1);
  }

  public synchronized void send(int n) throws InterruptedException {
    message = n;
    flag = true;
    notify();
  }

  static class ZugZug extends Zog {
    private long linkTime;
    private long initTime;
    private long runTime;

    public void run() {
      final long startTime = System.currentTimeMillis();
      super.run();
      final long endTime = System.currentTimeMillis();

      initTime = linkTime - startTime;
      runTime = endTime - linkTime;
    }

    public void link(final Zog zog) {
      super.link(zog);
      linkTime = System.currentTimeMillis();
    }

    public synchronized void send(int n) throws InterruptedException {
      super.send(n);
      if (DEBUG) {
        final String marker = (n == 0) ? "." : (n % 100 == 0) ? "*\n" : (n % 10 == 0) ? "+" : ".";
        System.out.print(marker);
      }
    }
  }

  public static void main(String args[]) {
    final int threadCount = Integer.parseInt(args[0]);
    final int messageCount = Integer.parseInt(args[1]);

    final Results results = performTestRun(threadCount, messageCount);
    results.displayResults();
  }

  static class Results {
    final int threadCount;
    final int messageCount;
    final long initTime;
    final long runTime;

    public Results(final int threadCount, final int messageCount, final long initTime, final long runTime) {
      this.threadCount = threadCount;
      this.messageCount = messageCount;
      this.initTime = initTime;
      this.runTime = runTime;
    }

    void displayResults() {
      final double timePerThreadSpawn = ((double) initTime) / threadCount;
      final double timePerMessage = ((double) runTime) / messageCount;

      final String message =
          "initTime = " + initTime + " ns (" + timePerThreadSpawn + " us/thread) (" + threadCount + " threads)";
      System.out.println(message);
      final String message2 =
          "runTime = " + runTime + " ns (" + timePerMessage + " us/message) (" + messageCount + " messages)";
      System.out.println(message2);
    }
  }

  private static Results performTestRun(final int threadCount, final int messageCount) {
    final ZugZug first = new ZugZug();
    first.start();

    Zog old = first;
    for (int i = 0; i < threadCount; i++) {
      if (DEBUG) {
        final String marker = (i == 0) ? "." : (i % 100 == 0) ? "*\n" : (i % 10 == 0) ? "+" : ".";
        System.out.print(marker);
      }
      final Zog current = new Zog();
      current.link(old);
      current.start();
      old = current;
    }

    first.link(old);

    try {
      first.send(messageCount);
    } catch (InterruptedException e) {
      e.printStackTrace();
      System.exit(13);
    }
    try {
      first.join();
    } catch (InterruptedException e) {
      e.printStackTrace();
      System.exit(16);
    }
    return new Results(threadCount, messageCount, first.initTime, first.runTime);
  }
}