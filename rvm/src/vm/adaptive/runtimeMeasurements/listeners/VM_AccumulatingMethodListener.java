/*
 * (C) Copyright IBM Corp. 2001
 */
//$Id$

/**
 * A VM_MethodListener that accumulates samples into a VM_MethodCountData.
 *
 * @author Matthew Arnold
 * @author Stephen Fink
 * @author Dave Grove
 * @author Michael Hind
 * @author Peter Sweeney
 */
final class VM_AccumulatingMethodListener extends VM_MethodListener 
  implements VM_Uninterruptible {

  /**
   * @param sampleSize the initial sampleSize for the listener
   * @param notifyOrganizer should the listener notify an organizer
   *                    when its threshold is reached?
   * @param data the VM_MethodCountData object to use to accumulate the samples
   */
  public VM_AccumulatingMethodListener(int sampleSize, 
				       boolean notifyOrganizer,
				       VM_MethodCountData data) {
    super(sampleSize, notifyOrganizer);
    this.data = data;
  }

  /** 
   * Update data with the current samples and generate a cumulative report.
   * Reset ourselves, since the sample buffer has been drained into data.
   */
  public void report() {
    processSamples();
    reset();
    VM.sysWrite("\nMethod sampler report");
    data.report();
  }

  /**
   * Process the samples.
   */
  public void processSamples() {
    data.update(samples, getNumSamples());
  }

  /**
   * @return the method data being updated by the listener.
   */
  public final VM_MethodCountData getData() { return data; }

  // The cummulative method sample data associated with this listener
  protected VM_MethodCountData data;
} 
