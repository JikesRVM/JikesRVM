/*
 * (C) Copyright IBM Corp. 2001
 */
//$Id$

/**
 * Abstraction for a statistic 
 *
 * @author Perry Cheng
 * 
 */
class VM_Statistic {

    protected int    count = 0;
    protected double last;
    protected double sum;
    protected double max;
    
    void addSample(double x) {
	last = x;
	if (count == 0) max = x;
	if (x > max) max = x;
	sum += x;
	count++;
    }

    int count() { return count; }
    double last() { if (VM.VerifyAssertions) VM.assert(count > 0); return last; }
    double sum()  { return sum; }
    double max()  { if (VM.VerifyAssertions) VM.assert(count > 0); return max; }
    double avg()  { if (VM.VerifyAssertions) VM.assert(count > 0); return sum / count; }

}
