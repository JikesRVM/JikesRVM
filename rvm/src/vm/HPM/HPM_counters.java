/*
 * (C) Copyright IBM Corp 2001,2002
 */
//$Id$
package com.ibm.JikesRVM;

import org.vmmagic.pragma.*;

/**
 * HPM counter values
 * A HPM_counter may be constructed only after VM_HPM is booted.
 * The number of events that are counters may be more or less than the number
 * of physical HPM counters: less because fewer events are specified, more because
 * of multiplexing.
 *
 * @author Peter F. Sweeney 
 */
public final class HPM_counters
{
  /*
   * Possible HPM counter values
   * 0 counter is real time
   * 1-HPM_info.numberOfEvents are HPM counter values 
   */
  public long [] counters;
  // local buffer
  private char[] l_buffer;
  // format buffer
  private char[] f_buffer;
  // maximum number of characters in a long
  static private int MAX_LONG_LENGTH            = 20;
  // maximum number of characters in a long when formatted with commas
  static private int MAX_LONG_FORMAT_LENGTH = 26;
  /**
   * constructor
   */
  HPM_counters() {
    if (!VM_HardwarePerformanceMonitors.booted()) {
      VM.sysWrite("***HPM_counters() called before VM_HPM.booted()!***\n");
      new Exception().printStackTrace();
      VM.sysExit(VM.EXIT_STATUS_HPM_TROUBLE);
    }
    // TEMPORARY
    counters = new long[HPM_info.getNumberOfValues()];
    for (int i=0; i<HPM_info.getNumberOfValues(); i++) {
      counters[i]=0;
    }
    l_buffer = new char[MAX_LONG_LENGTH];
    f_buffer = new char[MAX_LONG_FORMAT_LENGTH];

  }
 
  /**
   * Dump out counter values.
   * @return        return true if at least one counter not zero.
   */
  public boolean dump() throws UninterruptiblePragma 
  {
    boolean notZero = false;
    for (int i=0; i<HPM_info.getNumberOfValues(); i++) {
      if (counters[i] > 0) {
        notZero = true;
        //      System.out.println(i+": "+HPM_info.short_name(i)+":"+format_long(counters[i]));
        VM.sysWrite  (i,": ");
        VM.sysWrite  (HPM_info.short_name(i));
        VM.sysWrite  (":");
        VM.sysWrite  (format_long(counters[i]),MAX_LONG_FORMAT_LENGTH);
        VM.sysWriteln();
      }
    }
    return notZero;
  }
  /*
   * Reset counters to zero
   */
  public void reset() throws UninterruptiblePragma {
    for (int i=0; i<HPM_info.getNumberOfValues(); i++) {
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
  public void accumulate(HPM_counters sum, int n_counters) throws UninterruptiblePragma
  {
    for (int i=0; i<n_counters; i++) {
      sum.counters[i] += counters[i];
    }
  }
  /*
   * Pretty print a long with commas.
   * CONSTRAINT: must be uninterruptible: can't use a String, because its methods are interruptible.
   *
   * @param value  long to be formatted
   * @return char[] of long formatted with commas
   */
  public char[] format_long(long value) throws UninterruptiblePragma 
  {
    if(VM_HardwarePerformanceMonitors.verbose>=10) {
      VM.sysWrite("HPM_counters.format_length(");VM.sysWriteLong(value);VM.sysWrite(")");
    }

    int i;
    // clear buffers
    for (i=0; i<MAX_LONG_LENGTH; i++) {
      l_buffer[i] = ' ';
      f_buffer[i] = ' ';
    }
    for (; i < MAX_LONG_FORMAT_LENGTH; i++){
      f_buffer[i] = ' ';
    }

    // copy value over to char array l_buffer
    long l_value = value;
    int l_index = MAX_LONG_LENGTH-1;
    while (l_value > 0) {
      int remainder = (int)(l_value - ((int)(l_value/10))*10);
      l_buffer[l_index] = (char)((int)'0' + remainder);

      l_value = (int)(l_value/10);
      l_index--;
    }

    // format char array commas in f_buffer
    int length  = (MAX_LONG_LENGTH-1) - l_index;
    if(VM_HardwarePerformanceMonitors.verbose>=10) {
      VM.sysWrite(" l_buffer '");VM.sysWrite(l_buffer,MAX_LONG_LENGTH); 
      VM.sysWrite("', l_index ");VM.sysWrite(l_index);
      VM.sysWrite(", length ");VM.sysWrite(length);VM.sysWrite("\n");
    }
    l_index     =  MAX_LONG_LENGTH-1;
    int f_index =  MAX_LONG_FORMAT_LENGTH-1;
    // copy over three digits at a time
    while (length > 3 && l_index >= 2 && f_index >= 3) {
      f_buffer[f_index  ] = l_buffer[l_index  ];
      f_buffer[f_index-1] = l_buffer[l_index-1];
      f_buffer[f_index-2] = l_buffer[l_index-2];
      f_buffer[f_index-3] = ',';
      f_index -=4; l_index -=3;
      length -=3;
    }
    if (l_index < 2) {
      VM.sysWrite("***HPM_counters.format_length(");
      VM.sysWriteLong(value);VM.sysWrite(") l_index ");VM.sysWrite(l_index,MAX_LONG_LENGTH);
      VM.sysWrite("< 2 and length ");VM.sysWrite(length);VM.sysWrite(" > 3!***\n");
      VM.sysExit(VM.EXIT_STATUS_HPM_TROUBLE);
    }
    if (f_index < 3) {
      VM.sysWrite("***HPM_counters.format_length(");
      VM.sysWriteLong(value);VM.sysWrite(") f_index ");VM.sysWrite(f_index,MAX_LONG_FORMAT_LENGTH);
      VM.sysWrite("< 3 and length ");VM.sysWrite(length);VM.sysWrite(" > 3!***\n");
      VM.sysExit(VM.EXIT_STATUS_HPM_TROUBLE);
    }
    // copy over remainders digits
    if (length > 0) {
      while (length > 0) {
        f_buffer[f_index] = l_buffer[l_index];
        f_index--; l_index--;
        length--;
      }
    }
    if(VM_HardwarePerformanceMonitors.verbose>=10) {
      VM.sysWrite("\tinitialized f_buffer '");VM.sysWrite(f_buffer,MAX_LONG_FORMAT_LENGTH); 
      VM.sysWrite("'\n");
    }
    return f_buffer;
  }

}
