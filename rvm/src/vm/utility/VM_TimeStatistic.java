/*
 * (C) Copyright IBM Corp. 2001
 */
//$Id$

/**
 * Abstraction for a statistic that measures time spent in a phase.
 *
 * @author Perry Cheng
 * 
 */
class VM_TimeStatistic extends VM_Statistic implements VM_Uninterruptible {

    private double curStart = -1.0;
    double lastStart = -1.0;
    double lastStop = -1.0;

    void start(double x) {
	if (VM.VerifyAssertions) VM.assert(curStart == -1.0);
	lastStart = curStart = x;
    }

    void stop(double x) {
	if (VM.VerifyAssertions) VM.assert(curStart != -1.0);
	lastStop = x;
	addSample(x - curStart);
	curStart = -1.0;
    }

    void start() {
	start(VM_Time.now());
    }

    void stop() {
	stop(VM_Time.now());
    }

    void start(VM_TimeStatistic other) {
	double now = VM_Time.now();
	other.stop(now);
	start(now);
    }

    int lastMs() {
	return (int)(last()*1000.0);
    }

    int avgMs() {
	return (int)(avg()*1000.0);
    }

    int maxMs() {
	return (int)(max()*1000.0);
    }

    int sumS() {
	return (int)(sum());
    }

    int avgUs() {
	return (int)(avg()*1000.0);
    }

}
