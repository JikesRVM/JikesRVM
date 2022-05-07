package org.jikesrvm.energy;
class LogEntry {
	public int threadId;
	public int methodId;
	public int invocationCounter;
	public double[] counters;
	public int[] ratio;
	public long time;
	public double hotMethodStartTime;

	public LogEntry(int threadId, int methodId, int invocationCounter, double[] counters, long time) {
		this.threadId = threadId;
		this.methodId = methodId;
		this.invocationCounter = invocationCounter;
		this.counters = counters;	
		this.time = time;
	}

	public LogEntry(int threadId, int methodId, int invocationCounter, double[] counters) {
		this.threadId = threadId;
		this.methodId = methodId;
		this.invocationCounter = invocationCounter;
		this.counters = counters;	
		this.time = time;
	}
	public LogEntry(int threadId, int methodId, double[] counters, long time, double hotMethodStartTime) {
		this.threadId = threadId;
		this.methodId = methodId;
		this.counters = counters;	
		this.time = time;
		this.hotMethodStartTime = hotMethodStartTime;
	}
}
