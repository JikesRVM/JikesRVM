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
  
  public boolean dump_counters(HPM_info info) {
    // System.out.println("HPM_counters.dump() # of counters "+info.numberOfCounters);
    boolean notZero = false;
    for (int i=0; i<info.numberOfCounters; i++) {
      if (counters[i] > 0) {
	notZero = true;
	System.out.println(i+": "+info.short_name(i)+":"+format_number(counters[i]));
      }
    }
    return notZero;
  }
  public void dump_counter(HPM_info info, int i) {
    if (i>info.numberOfCounters) {
      System.err.println("***HPM_counters.dump_counter("+i+") "+i+" > number of counters "+
			 info.numberOfCounters+"!***");
      System.exit(-1);
    }
    System.out.println(info.short_name(i)+": "+counters[i]);
  }
  public void reset_counters() {
    for (int i=0; i<HPM_info.MAX_VALUES; i++) {
      counters[i]=0;
    }
  }
  public void accumulate(HPM_counters sum, HPM_info info) {
    // System.out.println("HPM_counters.dump() # of counters "+info.numberOfCounters);
    for (int i=0; i<=info.numberOfCounters; i++) {
      sum.counters[i] += counters[i];
    }
  }

  public String format_number(long counter) {
    String value = new Long(counter).toString();
    int length = value.length();
    int segments  = length/3;
    int remainder = length - (segments*3);
    String constructed = "";
    if (remainder > 0) {
      constructed += value.substring(0,remainder)+",";
    }
    int start = remainder;
    for (int i=0; i<segments; i++) {
      constructed += value.substring(start, start+3);
      start += 3;
      if (i<(segments-1)) {
	constructed += ",";
      }
    }
    return constructed;
  }

}
