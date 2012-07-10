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
package org.jikesrvm.adaptive.measurements.instrumentation;

import java.util.Enumeration;
import java.util.Vector;
import org.jikesrvm.VM;
import org.jikesrvm.compilers.opt.InstrumentedEventCounterManager;
import org.jikesrvm.compilers.opt.ir.Instruction;

/**
 * This class provides the basic functionality for instrumented data
 * that use counters allocated from a InstrumentedEventCounterManager.
 * It provides the basic interface to access counters,  forwarding
 * those requests to the counter manager.
 */
public class ManagedCounterData {

  /**
   * @param counterManager The counterManager that will provide the counter space
   */
  ManagedCounterData(InstrumentedEventCounterManager counterManager) {
    // Basic block instrumentation is performed using a common counter
    // allocation for the whole method.  It requests that space here.
    this.counterManager = counterManager;
  }

  /**
   * This method must be called before creating any counters for this
   * data.  It registers this data with the counter manager and gets a
   * "handle" that is coded into the counter instruction.  If you need
   * to change the number of counters in this data AFTER you have
   * created counters, use void
   * ManagerdCounterData.resizeCounters(int) instead.
   *
   * @param countersNeeded How many counters are needed by this data
   */
  public void initializeCounters(int countersNeeded) {
    // Confirm that this method is called only once.  Once a handle is
    // assigned, it should not be changed.  Use resizeCounters(int) to
    // change the size of the data.
    if (VM.VerifyAssertions) {
      VM._assert(handle == -1);
    }

    this.numCounters = countersNeeded;
    // Register  this many counters with the counter manager
    this.handle = counterManager.registerCounterSpace(countersNeeded);
  }

  /**
   * Tell the data to automatically expand the counters if there is a
   * request to count an event that is greater than the current size.
   *
   * @param autoGrow Whether the counters should grow automatically.
   */
  public void automaticallyGrowCounters(boolean autoGrow) {

    final int INITIAL_COUNTER_SIZE = 20;

    automaticallyGrowCounters = autoGrow;
    if (automaticallyGrowCounters) {
      initializeCounters(INITIAL_COUNTER_SIZE);
    }
  }

  /**
   * Used to reset the number of counters for this data
   *
   * @param countersNeeded The number of counters needed
   */
  public void resizeCounters(int countersNeeded) {
    // Confirm that counters have been initialized (using initializeCounters(int))
    if (VM.VerifyAssertions) {
      VM._assert(handle != -1);
    }

    counterManager.resizeCounterSpace(this.getHandle(), countersNeeded);
    numCounters = countersNeeded;
  }

  /**
   * Return the count for the given (relative) index
   *
   * @param counterNumber The event number within the data
   * @return The count associated with this counter
   */
  public double getCounter(int counterNumber) {
    // Confirm that counters have been initialized
    //  (using initializeCounters(int))
    if (VM.VerifyAssertions) {
      VM._assert(handle != -1);
    }
    return counterManager.getCounter(this.getHandle(), counterNumber);
  }

  /**
   * Set the count for the given index
   *
   * @param counterNumber The event number within the data
   * @param value The new value of the counter
   */
  public void setCounter(int counterNumber, double value) {
    // Confirm that counters have been initialized (using initializeCounters(int))
    if (VM.VerifyAssertions) {
      VM._assert(handle != -1);
    }
    if (counterNumber >= getNumCounters()) {
      if (automaticallyGrowCounters) {
        while (counterNumber >= getNumCounters()) {
          resizeCounters(getNumCounters() * 2);
        }
      } else {
        if (VM.VerifyAssertions) { VM._assert(VM.NOT_REACHED); }
      }
    }

    counterManager.setCounter(this.getHandle(), counterNumber, value);
  }

  /**
   * Return the number of counters currently allocated for this data
   *
   *  @return the number of counters
   */
  public int getNumCounters() {
    // Confirm that counters have been initialized (using initializeCounters(int))
    if (VM.VerifyAssertions) {
      VM._assert(handle != -1);
    }
    return numCounters;
  }

  /**
   * Counter Managers give id's that identify the counter space they
   * have given to each data. This method returns that ID.
   *
   * @return The handle given to this data object by the counter manager.
   **/
  public int getHandle() {
    return handle;
  }

  /**
   * Return the counter manager for this data.
   *
   * @return the counter manager object
   */
  public InstrumentedEventCounterManager getCounterManager() {
    return counterManager;
  }

  /**
   * Create a place holder instruction to represent an increment of a
   * particular counted event.  Simply forwards the request to the
   * counter manager.
   *
   * @param counterNumber The number of the counter to increment
   * @return The instruction that will update the given counter
   */
  public Instruction createEventCounterInstruction(int counterNumber) {
    return createEventCounterInstruction(counterNumber, 1.0);
  }

  /**
   * Create a place holder instruction to represent the counted event.
   * Simply forwards the request to the counter manager.
   *
   * @param counterNumber The number of the counter to increment
   * @param incrementValue The value to add to the given counter
   * @return The instruction that will update the given counter
   */
  Instruction createEventCounterInstruction(int counterNumber, double incrementValue) {
    // Confirm that counters have been initialized
    if (VM.VerifyAssertions) {
      VM._assert(handle != -1);
    }

    // If we automatically growing counters, see if we need to.
    if (counterNumber >= numCounters) {
      if (automaticallyGrowCounters) {
        while (counterNumber >= numCounters) {
          resizeCounters(getNumCounters() * 2);
        }
      } else {
        // Should we put a warning here?? Not sure.
      }
    }
    return getCounterManager().createEventCounterInstruction(getHandle(), counterNumber, incrementValue);
  }

  /**
   *  This method prints the (sorted) nonzero elements a counter
   *  array.
   *
   * @param f a function that gets the "name" for each counter
   */
  final void report(CounterNameFunction f) {
    double sum = 0;
    Vector<Counter> vec = new Vector<Counter>();

    // set up a vector of non-zero counts
    for (int i = 0; i < getNumCounters(); i++) {
      double count = getCounter(i);
      if (count > 0.0) {
        sum += count;
        String s = f.getName(i);
        vec.addElement(new Counter(s, count));
      }
    }

    // sort the vector in decreasing order
    sort(vec);

    // print
    for (Enumeration<Counter> e = vec.elements(); e.hasMoreElements();) {
      Counter c = e.nextElement();
      String s = c.name;
      double count = c.count;
      double percent = (100 * count) / sum;
      VM.sysWrite(count + "/" + sum + " = " + percent + "% " + s + "\n");
    }
  }

  /**
   * Sort a Vector<Counter> by decreasing count.
   * (code borrowed from InstructionSampler.java)
   * <p>
   * Shell sort
   * <p>
   * Reference: "The C Programming Language", Kernighan & Ritchie, p. 116
   */
  private void sort(Vector<?> v) {
    int n = v.size();
    for (int gap = n / 2; gap > 0; gap /= 2) {
      for (int i = gap; i < n; ++i) {
        for (int j = i - gap; j >= 0; j -= gap) {
          double a = ((Counter) v.elementAt(j)).count;
          double b = ((Counter) v.elementAt(j + gap)).count;
          if (a >= b) break;
          swap(v, j, j + gap);
        }
      }
    }
  }

  // Interchange vec[i] with vec[j]
  private <T> void swap(Vector<T> vec, int i, int j) {
    T t = vec.elementAt(i);
    vec.setElementAt(vec.elementAt(j), i);
    vec.setElementAt(t, j);
  }

  /* -----   Implementation   ---- */

  /**
   * How many counters are needed by this data?
   **/
  protected int numCounters = 0;

  /**
   *  When a data object is registered with a counter manager, it is
   *  given an id, which is stored here.
   **/
  protected int handle = -1;

  /**
   * Basic block instrumentation stores its counters using an
   * abstracted counter allocation technique (a counterManager)
   **/
  protected InstrumentedEventCounterManager counterManager = null;

  protected boolean automaticallyGrowCounters = false;

  /**
   * Auxiliary class
   */
  static final class Counter {
    final String name;
    final double count;

    Counter(String s, double c) {
      name = s;
      count = c;
    }
  }

}

