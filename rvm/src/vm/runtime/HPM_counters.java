/*
 * (C) Copyright IBM Corp 2001,2002
 */
//$Id:&
package com.ibm.JikesRVM;

/**
 * HPM counter values
 *
 * @author Peter F. Sweeney 
 */
public final class HPM_counters
{
  /*
   * Possible HPM counters
   * 0 counter is real time
   * 1-MAX_VALUES are HPM counter values 
   */
  public long []counters;
  /**
   * constructor
   */
  HPM_counters() {
    counters = new long[HPM_info.MAX_VALUES];
    for (int i=0; i<HPM_info.MAX_VALUES; i++) {
      counters[i]=0;
    }
  }
 
  /**
   * Dump out counter values.
   * @param info    HPM information
   * @return        return true if at least one counter not zero.
   */
  public boolean dump_counters(HPM_info info) {
    // System.out.println("HPM_counters.dump() # of counters "+info.numberOfCounters);
    boolean notZero = false;
    for (int i=0; i<=info.numberOfCounters; i++) {
      if (counters[i] > 0) {
        notZero = true;
        System.out.println(i+": "+info.short_name(i)+":"+format_long(counters[i]));
      }
    }
    return notZero;
  }
  /*
   * Reset counters to zero
   */
  public void reset_counters() {
    for (int i=0; i<HPM_info.MAX_VALUES; i++) {
      counters[i]=0;
    }
  }
  /*
   * Accumulate this object's counters with sum's and store in sum.
   * Method is uninterruptible because called from VM_Processor.dispatch() at
   * thread switch time.
   * @param sum        where accumulated values go
   * @param n_counters number of counters
   */
  public void accumulate(HPM_counters sum, int n_counters) throws VM_PragmaUninterruptible
  {
    for (int i=0; i<=n_counters; i++) {
      sum.counters[i] += counters[i];
    }
  }
  /*
   * Pretty print a long with commas.
   * @param value  long to be formatted
   */
  public String format_long(long value) {
    String value_ = new Long(value).toString();
    int length = value_.length();
    int segments  = length/3;
    int remainder = length - (segments*3);
    String constructed = "";
    if (remainder > 0) {
      constructed += value_.substring(0,remainder)+",";
    }
    int start = remainder;
    for (int i=0; i<segments; i++) {
      constructed += value_.substring(start, start+3);
      start += 3;
      if (i<(segments-1)) {
        constructed += ",";
      }
    }
    return constructed;
  }

}
