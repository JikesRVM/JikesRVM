/*
 * (C) Copyright IBM Corp. 2001
 */
//$Id$
package com.ibm.JikesRVM;

import org.vmmagic.pragma.*;

/**
 * Abstraction for a statistic that measures time spent in a phase.
 *
 * @author Perry Cheng
 */
public class VM_TimeStatistic extends VM_Statistic implements Uninterruptible {

  // Invariant: Time spent in timer equals accumulated + ((begin == -1) ? 0 : cycles() - begin)
  //
  private long begin;
  private long accumulated;   
  private boolean paused;

  public VM_TimeStatistic () { 
    reset(); 
  }

  private void reset () {
    begin = -1;
    accumulated = 0;
    paused = false;
  }

  public void start (long x) {
    if (VM.VerifyAssertions) VM._assert(begin == -1);
    begin = x;
  }

  public void stop (long x) {
    if (begin != -1) accumulated += (x - begin);
    addSample(VM_Time.cyclesToSecs(accumulated));
    reset();
  }

  public void pause () {
    if (VM.VerifyAssertions) VM._assert(begin != -1);
    accumulated += (VM_Time.cycles() - begin);
    begin = -1;
    paused = true;
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
