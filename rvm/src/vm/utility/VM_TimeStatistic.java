/*
 * (C) Copyright IBM Corp. 2001
 */
//$Id$
package com.ibm.JikesRVM;

/**
 * Abstraction for a statistic that measures time spent in a phase.
 *
 * @author Perry Cheng
 * 
 */
public class VM_TimeStatistic extends VM_Statistic implements VM_Uninterruptible {

    private double curStart = -1.0;
    public double lastStart = -1.0;
    public double lastStop = -1.0;

    public void start(double x) {
	if (VM.VerifyAssertions) VM._assert(curStart == -1.0);
	lastStart = curStart = x;
    }

    public void stop(double x) {
	if (VM.VerifyAssertions) VM._assert(curStart != -1.0);
	lastStop = x;
	addSample(x - curStart);
	curStart = -1.0;
    }

    public void start() {
	start(VM_Time.now());
    }

    public void stop() {
	stop(VM_Time.now());
    }

    public void start(VM_TimeStatistic other) {
	double now = VM_Time.now();
	other.stop(now);
	start(now);
    }

    public int lastMs() {
	return (int)(last()*1000.0);
    }

    public int avgMs() {
	return (int)(avg()*1000.0);
    }

    public int maxMs() {
	return (int)(max()*1000.0);
    }

    public int sumS() {
	return (int)(sum());
    }

    public int avgUs() {
	return (int)(avg()*1000.0);
    }

}
