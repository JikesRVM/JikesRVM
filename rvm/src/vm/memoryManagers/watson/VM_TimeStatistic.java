/*
 * (C) Copyright IBM Corp. 2001
 */

/**
 * Abstraction for a statistic that measures time spent in a phase.
 *
 * @author Perry Cheng
 * 
 */
class VM_TimeStatistic {

    int count;
    double last;
    double sum;
    double max;
    private double curStart = -1.0;
    double lastStart = -1.0;
    double lastStop = -1.0;

    private void addSample(double x) {
	last = x;
	if (count == 0) max = x;
	if (x > max) max = x;
	sum += x;
	count++;
    }

    void start(double x) {
	if (VM.VerifyAssertions) VM.assert(curStart == -1.0);
	lastStart = curStart = x;
    }

    private void stop(double x) {
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

    int avgMs() {
	return (int)(sum/count*1000.0);
    }

    int avgUs() {
	return (int)(sum/count*1000.0);
    }

}
