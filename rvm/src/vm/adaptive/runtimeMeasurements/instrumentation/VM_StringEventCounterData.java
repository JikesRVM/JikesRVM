/*
 * (C) Copyright IBM Corp. 2001
 */
// $Id$

/**
 * VM_StringEventCounterData.java
 * 
 * A generic data object that maps strings to counters.  The key
 * method is "OPT_Instruction getCounterInstructionForEvent(String)"
 * which, given a string, returns a counter instruction that
 * increments the corresponding counter for that string.
 *
 * @author Matthew Arnold
 *
**/

import java.util.Hashtable;
import java.util.Enumeration;

class VM_StringEventCounterData extends VM_ManagedCounterData
  implements VM_Reportable 
{

  static final boolean DEBUG=false;


  /**
   *  Constructor
   *
   * @manager The manager that will provide the counter space
   **/
  VM_StringEventCounterData(OPT_InstrumentedEventCounterManager manager,
			    String name)
  {
    // Call superclass constructor
    super(manager);
    
    dataName = name;
  }

  /**
   * Given a string, find or create the counter associated and return
   * and instruction to increment that counter.  
   *
   * @param event The name of the event
   * @return An instruction to increment the count associated with the event.
   */
  OPT_Instruction getCounterInstructionForEvent(String event) {
    return getCounterInstructionForEvent(event,1.0);
  }

  /**
   * Given a string, find or create the counter associated and return
   * and instruction to increment that counter.  
   *
   * @param event The name of the event
   * @param incrementValue The value to add to counter
   * @return An instruction that will update the count associated with the event.
   *
   */
  OPT_Instruction getCounterInstructionForEvent(String event, 
						double incrementValue) {
    int counterNumber =0;

    // If this string already has a counter, return it.  Hopefully
    // String has equality defined so that two of the same string will
    // map to the same instructions.
    Integer integerCounterNum = (Integer) stringToCounterMap.get(event);
    if (integerCounterNum != null) {
      // Use existing
      counterNumber = integerCounterNum.intValue();
    }
    else {
      // Use new counter
      counterNumber = ++ eventNumber;
      // remember it, and return it
      stringToCounterMap.put(event,new Integer(eventNumber));
    }

    return createEventCounterInstruction(counterNumber,incrementValue);
  }

  /**
   * Convert a double to string with maximum precision.
   * @param num double to convert
   */
  protected static String doubleToString(double num) {
    long whole = (long)num;
    if (whole == Long.MAX_VALUE || whole == Long.MIN_VALUE)
      return Double.toString(whole);
    double fract = Math.abs(num - (double)whole);
    String res = Long.toString(whole);
    if (fract != 0.0) {
      String f2s = Double.toString(fract + 1.0);
      res += f2s.substring(1);
    }
    return res;
  }

  /**
   * Part of VM_Reportable interface
   * Print a report at the end of execution
   */
  public void report()
  {
    // Turn off future instrumentation to avoid hanging during 
    // iteration
    VM_Instrumentation.disableInstrumentation();

    VM.sysWrite("Printing " + dataName + ":\n");
    VM.sysWrite("--------------------------------------------------\n");
    double total=0;
    for (Enumeration e = stringToCounterMap.keys();
	 e.hasMoreElements();) {
      String stringName = (String) e.nextElement();

      Integer counterNum = (Integer) stringToCounterMap.get(stringName);
      double counterVal = getCounter(counterNum.intValue());
      VM.sysWrite(doubleToString(counterVal) + " " + stringName + "\n");
      total += counterVal;
    }
    VM.sysWrite("Total: " + doubleToString(total) + "\n");
  }

  /**
   *  Part of VM_Reportable interface
   **/
  public void reset() { 
    for (Enumeration e = stringToCounterMap.keys();
	 e.hasMoreElements();) {
      String stringName = (String) e.nextElement();
      Integer counterNum = (Integer) stringToCounterMap.get(stringName);
      int counterIdx = counterNum.intValue();
      setCounter(counterIdx, 0.0);
    }
  }

 /** 
  *  Map strings to a counter location
  */
  protected  Hashtable stringToCounterMap = new Hashtable();

  /**
   * A string description of this data;
   */
  String dataName= "";

  /** 
   * Used to keep track of how many counters have been used so far.
   */ 
  int eventNumber=-1;

} // end of class


