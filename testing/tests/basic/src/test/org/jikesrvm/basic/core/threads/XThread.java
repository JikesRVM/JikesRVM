/*
 * This file is part of Jikes RVM (http://jikesrvm.sourceforge.net).
 * The Jikes RVM project is distributed under the Common Public License (CPL).
 * A copy of the license is included in the distribution, and is also
 * available at http://www.opensource.org/licenses/cpl1.0.php
 *
 * (C) Copyright Peter Donald. 2007
 */
package test.org.jikesrvm.basic.core.threads;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.Map;
import java.util.Set;

/**
 * Utility class to make it easier to generate deterministic output by caching output per thread and outputting it at end.
 *
 * @author Peter Donald
 */
abstract class XThread extends Thread {

  static class ThreadRecord {
    final String name;
    final LinkedList<String> messages = new LinkedList<String>();
    final Thread thread;

    ThreadRecord(final String name, final Thread thread) {
      this.name = name;
      this.thread = thread;
    }
  }

  private static final Map<Integer, ThreadRecord> records = new HashMap<Integer, ThreadRecord>();
  private static final ThreadRecord mainRecord = new ThreadRecord("Main", null);

  static {
    records.put(0, mainRecord);
  }

  private static int maxId = 1;
  static boolean holdMessages = !"false".equals(System.getProperty("tests.DeterministicOutput"));

  private final ThreadRecord record;
  private final int id;
  boolean completed;
  boolean running;

  public XThread(String name) {
    super(name);
    synchronized (XThread.class) {
      id = maxId++;
    }
    record = new ThreadRecord(name, this);
    records.put(id, record);
    tsay("creating");
  }

  public synchronized void start() {
    tsay("starting");
    super.start();
  }

  public void run() {
    tsay("run starting");
    running = true;
    try {
      performTask();
    } finally {
      tsay("run finishing");
    }
    completed = true;
  }

  abstract void performTask();

  void tsay(final String message) {
    if (holdMessages) {
      record.messages.add(message);
    } else {
      output(id, record.name, message);
    }
  }

  public static void say(final String message) {
    final Thread thread = Thread.currentThread();
    final int id = (thread instanceof XThread) ? ((XThread) thread).id : 0;

    final ThreadRecord record = records.get(id);
    if (holdMessages) {
      record.messages.add(message);
    } else {
      output(id, record.name, message);
    }
  }

  public static synchronized void joinOnAll() {
    for (ThreadRecord record : records.values()) {
      if (null != record.thread && record.thread.isAlive()) {
        try {
          record.thread.join();
        } catch (final InterruptedException ie) {
        }
      }
    }
  }

  public static synchronized void outputMessages() {
    joinOnAll();
    final Set<Integer> keySet = records.keySet();
    final ArrayList<Integer> ids = new ArrayList<Integer>(keySet.size());
    ids.addAll(keySet);
    Collections.sort(ids);

    for (final Integer id : ids) {
      final ThreadRecord record = records.get(id);
      for (final String message : record.messages) {
        final String name = record.name;
        output(id, name, message);
      }
    }
  }

  private static void output(final int id,
                             final String name,
                             final String message) {
    System.out.println("Thread " + id + " " + name + ": " + message);
  }
}
