/*
 * (C) Copyright IBM Corp. 2001
 */
//$Id$

/**
 * Primitive accumulating timer that detects if a gc
 * occurs during the timing interval and doesn't count it.
 * May not be appropriate for all circumstances, but useful in some.
 * In particular, it is the responsibility of the user to ensure
 * non-concurrent access. 
 *
 * @author Dave Grove
 */
class VM_Stopwatch {
  int count;
  double elapsedTime;
  private double startTime;
  private int gcEpoch;

  public final void start() {
    startTime = VM_Time.now();
    gcEpoch = VM_CollectorThread.collectionCount;
  }
  
  public final void stop() {
    double endTime = VM_Time.now();
    if (gcEpoch ==  VM_CollectorThread.collectionCount) {
      count++;
      elapsedTime += (endTime - startTime);
    }
  }

  public final void report(double totalTime, String name) {
    if (count > 0) {
      VM.sysWrite(name);
      VM.sysWrite(count, false);
      VM.sysWrite("\t");
      VM.sysWrite(VM_Time.toMilliSecs(elapsedTime),false);
      VM.sysWrite("\t");
      VM_RuntimeCompilerInfrastructure.printPercentage(elapsedTime, totalTime);
      VM.sysWrite("%\n");
    }
  }
}
