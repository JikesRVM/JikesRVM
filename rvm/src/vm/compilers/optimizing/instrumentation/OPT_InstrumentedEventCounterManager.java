/*
 * (C) Copyright IBM Corp. 2001
 */
//$Id$

/**
 * This interface defines the functionality necessary to be a
 * OPT_InstrumentedEventCounterManager.  The goal of this interface is
 * to provide a mechanism for instrumentation phases to performing
 * counting of events, but to keep the implemntation of the counters
 * completely hidden.  
 * 
 * @author Matthew Arnold
 *
 **/
abstract class OPT_InstrumentedEventCounterManager {
  static final boolean DEBUG = false;

  /**
   *  This method is called to called to tell the counter manager to
   *  reserve the needed space.  A handle is returned telling where
   *  the counter space begins.
   *
   * @param countersNeeded The number of counters being requested 
   * @return A "handle", or name  for the counter space reserved.
   */
  abstract int registerCounterSpace (int countersNeeded);

  /**
   *  This method is called to change the number of counters needed.
   *
   * @param handle  The handle describing which the data to be resized
   * @param countersNeeded The number of counters needed
   */
  abstract void resizeCounterSpace (int handle, int countersNeeded);

  /** 
   * Get the value of a counter.
   *
   * @param handle The counter space to look in
   * @param location The counter whose value to return
   */
  abstract double getCounter (int handle, int location);

  /** 
   * Set the value of a counter.
   *
   * @param handle The counter space to look in
   * @param location The counter whose value to return
   * @param value The new value of the counter
   */
  abstract void setCounter (int handle, int location, double value);

  /**
   * Create a place holder instruction to represent the counted event.
   *
   * @param handle The counter space to look in
   * @param location The counter whose value to return
   * @param incrementValue The value to add to the counter
   * @return The instruction to increment the given counter
   */
  abstract OPT_Instruction createEventCounterInstruction (int handle, 
      int location, double incrementValue);

  /**
   *  Take an event counter instruction and mutate it into IR instructions that 
   *  will do the actual counting. 
   */
  abstract void mutateOptEventCounterInstruction (OPT_Instruction i, OPT_IR ir);

  /**
   * Allow a counter to be inserted into a baseline compiled method.
   * Still  under construction. 
   */
  abstract void insertBaselineCounter ();
}               // end of class



