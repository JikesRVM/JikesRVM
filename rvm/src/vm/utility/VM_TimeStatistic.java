/*
 * (C) Copyright IBM Corp. 2001
 */
//$Id$
package com.ibm.JikesRVM;

/**
 * Abstraction for a statistic that measures time spent in a phase.
 *
 * @author Perry Cheng
 */
public class VM_TimeStatistic extends VM_Statistic implements VM_Uninterruptible {

  private long curStart = -1;
  private long lastStart = -1;
  private long lastStop = -1;

  public void start(long x) {
    if (VM.VerifyAssertions) VM._assert(curStart == -1);
    lastStart = curStart = x;
  }

  public void stop(long x) {
    if (VM.VerifyAssertions) VM._assert(curStart != -1);
    lastStop = x;
    addSample(VM_Time.cyclesToMillis(x - curStart)/1000);
    curStart = -1;
  }
  
  public void start() {
    start(VM_Time.cycles());
  }

  public void stop() {
    stop(VM_Time.cycles());
  }

  public void start(VM_TimeStatistic other) {
    long now = VM_Time.cycles();
    other.stop(now);
    start(now);
  }

  public int lastMs() {
    return (int)(last()*1000);
  }

  public int avgMs() {
    return (int)(avg()*1000);
  }

  public int maxMs() {
    return (int)(max()*1000);
  }

  public int sumS() {
    return (int)(sum());
  }

  public int avgUs() {
    return (int)(avg()*1000*1000);
  }
}
