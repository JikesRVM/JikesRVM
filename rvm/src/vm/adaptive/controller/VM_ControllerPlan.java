/*
 * (C) Copyright IBM Corp. 2001
 */
//$Id$

import java.util.LinkedList;
import java.util.ListIterator;

/**
 * An instance of this class describes a compilation decision made by
 * the controller
 *
 * @author Michael Hind
 */
final class VM_ControllerPlan {

  // The plan was created, but the setStatus method was never called
  static final byte UNINITIALIZED = 0;

  // The plan was successfully completed, i.e., the method was recompiled
  static final byte COMPLETED = 1;

  // Compilation began the method, but failed in an error
  static final byte ABORTED_COMPILATION_ERROR = 2;

  // The plan was aborted because the compilation queue was full
  static final byte ABORTED_QUEUE_FULL = 3;

  // The compilation is still in progress
  static final byte IN_PROGRESS = 4;

  // The compilation completed, but a new plan for the same method also completed,
  // so this is not the most recent completed plan
  static final byte OUTDATED = 5;

  // This is used by clients to initialize local variables for Java semantics
  static final byte UNKNOWN = 99;   
  
  /**
   *  The associate compilation plan 
   */
  private OPT_CompilationPlan compPlan; 

  /**
   *  The time we created this plan
   */
  private int timeCreated;

  /**
   *  The time compilation began
   */
  private int timeInitiated = -1;

  /**
   *  The time compilation end
   */
  private int timeCompleted = -1;

  /**
   *  The CPU time it took for the compilation
   */
  private double compilationCPUTime;

  /**
   *  The speedup we were expecting
   */
  private double expectedSpeedup;

  /**
   *  The priority associated with this plan
   */
  private double priority;

  /**
   *  The compiled method ID for this plan
   */
  private int CMID;

  /**
   *  The compiled method ID for the previous plan for this method
   */
  private int prevCMID;

  /**
   *  The status of this plan
   */
  private byte status;

  /**
   *  The list that we are onstatus of this plan
   */
  private LinkedList planList; 


  /**
   * Construct a controller plan
   *
   * @param compPlan     The compilation plan
   * @param timeCreated  The "time" this plan was created
   * @param prevCMID     The previous compiled method ID
   * @param expectedSpeedup	Expected recompilation benefit
   * @param priority     How important is executing this plan?
   */
  public VM_ControllerPlan(OPT_CompilationPlan compPlan, 
			   int timeCreated, 
			   int prevCMID, 
			   double expectedSpeedup,
			   double priority) {
    this.compPlan = compPlan;
    this.timeCreated = timeCreated;
    this.prevCMID = prevCMID;
    this.status = VM_ControllerPlan.UNINITIALIZED;
    this.expectedSpeedup = expectedSpeedup;
    this.priority = priority;
  }
  

  /**
   * Execute the plan.
   * 
   * @return true on success, false on failure
   */
  boolean execute() {
    // mark plan as in progress and insert it into controller memory
    setStatus(VM_ControllerPlan.IN_PROGRESS);
    VM_ControllerMemory.insert(this);
      
    // Attempt to add plan to compilation queue.
    boolean succeeded = 
      VM_Controller.compilationQueue.insert(getPriority(), this);

    // Logging, and record failure in plan if necessary.
    if (succeeded) {
      if (VM.LogAOSEvents) 
	VM_AOSLogging.recompilationScheduled(getCompPlan(), getPriority()); 
    } else {
      if (VM.LogAOSEvents) 
	VM_AOSLogging.recompilationQueueFull(getCompPlan()); 
      setStatus(VM_ControllerPlan.ABORTED_QUEUE_FULL); 
    }

    return succeeded;
  }



  /**
   * The compilation plan
   */
  public OPT_CompilationPlan getCompPlan() { return compPlan; }

  /**
   * The expected speedup <em>for this method </em> due to this recompilation
   */
  public double getExpectedSpeedup() { return expectedSpeedup; }

  /**
   * The priority (how important is it that this plan be executed)
   */
  public double getPriority() { return priority; }

  /**
   * The time this plan was created
   */
  public int getTimeCreated() { return timeCreated;  }
   
  /**
   * The time (according to the controller clock) compilation of this plan 
   * began.
   */
  public int getTimeInitiated() { return timeInitiated; }
  public void setTimeInitiated(int t) { timeInitiated = t; }
  

  /**
   * The time (according to the controller clock) compilation of this plan 
   * completed.
   */
  public int getTimeCompleted() { return timeCompleted; }
  public void setTimeCompleted(int t) { timeCompleted = t; }


  /**
   * The CPU time in milliseconds actually consumed by the compilation
   * thread to execute this plan. 
   */
  public double getCompilationCPUTime() { return compilationCPUTime; }
  public void setCompilationCPUTime(double t) { compilationCPUTime = t; }


  /**
   * CMID (compiled method id) associated with the code produced 
   * by executing this plan
   */
  public int getCMID() { return CMID; }
  public void setCMID(int x) { CMID = x; }


  /**
   * CMID (compiled method id) associated with the *PREVIOUS* compiled 
   * version of this method
   */
  public int getPrevCMID() { return prevCMID; }


  /**
   * Status of this compilation plan, choose from the values above
   */
  public byte getStatus() { return status; }

  public void setStatus(byte newStatus) { 
    status = newStatus; 

    // if we are marking this plan as completed, all previous completed plans
    // for this method should be marked as OUTDATED
    if (newStatus == COMPLETED) {
      // iterate over the planList until we get to this item
      ListIterator iter = planList.listIterator();
      while (iter.hasNext()) {
	VM_ControllerPlan curPlan = (VM_ControllerPlan) iter.next();

	// exit when we find ourselves
	if (curPlan == this) break;

	if (curPlan.getStatus() == COMPLETED) {
	  curPlan.status = OUTDATED;
	}
      } // more to process
    }
  }

  /**
   * List of plans for a source method
   */
  public LinkedList getPlanList() { return planList; }
  public void setPlanList(LinkedList list) { planList = list; }

  public String getStatusString() {
    switch (status) {
    case UNINITIALIZED:             return "UNINITIALIZED";
    case COMPLETED:                 return "COMPLETED";
    case ABORTED_COMPILATION_ERROR: return "ABORTED_COMPILATION_ERROR";
    case ABORTED_QUEUE_FULL:        return "ABORTED_QUEUE_FULL";
    case IN_PROGRESS:               return "IN_PROGRESS";
    case OUTDATED:                  return "OUTDATED";
    case UNKNOWN:                   return "UNKNOWN (not error)";
    default:                        return "**** ERROR, UNKNOWN STATUS ****";
    }
  }

  public String toString() {
    StringBuffer buf = new StringBuffer();

    buf.append("Method: "+ getCompPlan().method
	       +"\n\tCompiled Method ID: " + CMID
	       +"\n\tPrevious Compiled Method ID: " + prevCMID
	       +"\n\tCreated at "+ timeCreated
	       +"\n\tInitiated at "+ timeInitiated
	       +"\n\tCompleted at "+ timeCompleted
	       +"\n\tCPU Time Consumed: "+ compilationCPUTime
	       +"\n\tExpected Speedup: "+ expectedSpeedup
	       +"\n\tPriority: "+ priority
	       +"\n\tStatus: "+ getStatusString()
	       +"\n\tComp. Plan Level: "+compPlan.options.getOptLevel() +"\n");
    return buf.toString();
  }

}
