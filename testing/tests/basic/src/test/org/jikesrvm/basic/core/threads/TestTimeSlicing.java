/*
 * This file is part of Jikes RVM (http://jikesrvm.sourceforge.net).
 * The Jikes RVM project is distributed under the Common Public License (CPL).
 * A copy of the license is included in the distribution, and is also
 * available at http://www.opensource.org/licenses/cpl1.0.php
 *
 * (C) Copyright IBM Corp. 2001
 */
//$Id: /jikesrvm/local/testing/tests/threads/src/TestBackEdgeGC.java 10522 2006-11-14T22:42:56.816831Z dgrove-oss  $
package test.org.jikesrvm.basic.core.threads;

import java.util.Arrays;
import java.util.ArrayList;

/**
 * @author unascribed
 */
class TestTimeSlicing {

  private static final int LAST_COUNT = 10; // threads count to ten in step

  public static void main(String[] args) {

    final Task[] tasks = new Task[3];
    final int[] countArray = new int[tasks.length];

    for (int i = 0; i < tasks.length; ++i) {
      tasks[i] = new Task(i, countArray);
    }

    //start up tasks
    for (Task task : tasks) { task.start(); }

    // spin and wait for values to fill
    while (countArray[tasks.length - 1] != LAST_COUNT) {
      spinner();
    }

    XThread.say("bye");
    XThread.outputMessages();
  }

  // static routine to spin a bit
  //
  static int spinner() {
    // dummy calculation
    int sum = 0;
    for (int i = 1; i < 100; i++) {
      sum += i;
    }
    Thread.yield();
    return sum;
  }

  static class Task extends XThread {
    private final int id;
    private final int[] values;
    private final int previous;


    //---------------------------------

    Task(int id, int[] values) {
      super("Task " + id);
      this.id = id;
      this.values = values;
      this.previous = (id + values.length - 1) % values.length;
    }

    void performTask() {
      while (values[id] < LAST_COUNT) {
        boolean increment = false;
        if( 0 == id && values[previous] == values[id] ) {
          increment = true;
        } else if( 0 != id && values[previous] - 1 == values[id] ) {
          increment = true;
        }

        if( increment ) {
          values[id]++;
          tsay("incremented to count: " + values[id]);
        }
        //  spin for a while
        spinner();  // virtual spin code
      }
    }
  }
}
