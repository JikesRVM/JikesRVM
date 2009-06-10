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
package test.org.jikesrvm.basic.core.threads;

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
        if(0 == id && values[previous] == values[id]) {
          increment = true;
        } else if(0 != id && values[previous] - 1 == values[id]) {
          increment = true;
        }

        if(increment) {
          values[id]++;
          tsay("incremented to count: " + values[id]);
        }
        //  spin for a while
        spinner();  // virtual spin code
      }
    }
  }
}
