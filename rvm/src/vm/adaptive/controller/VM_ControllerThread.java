/*
 * (C) Copyright IBM Corp. 2001
 */
//$Id$

import java.util.Vector;
import java.util.Enumeration;

/**
 * This class implements the controller thread.  This entity is the brains of 
 * the adaptive optimization system.  It communicates with the runtime 
 * measurements subsystem to instruct and gather profiling information.  
 * It also talks to the compilation threads to generate 
 *     a) instrumented executables;
 *     b) optimized executables; 
 *     c) static information about a method; or 
 *     d) all of the above.
 *
 *  @author Michael Hind
 *  @author David Grove
 *  @author Stephen Fink
 *  @author Peter Sweeney
 */
class VM_ControllerThread extends VM_Thread {

  /**
   * constructor
   * @param sentinel: an object to signal when up and running
   */
  VM_ControllerThread(Object sentinel)  { this.sentinel = sentinel; }
  private Object sentinel;


  /**
   * This method is the entry point to the controller, it is called when
   * the controllerThread is created.
   */
  public void run() {
    // save this object so others can access it, if needed
    VM_Controller.controllerThread = this;

    // Bring up the logging system
    if (VM.LogAOSEvents) VM_AOSLogging.boot();
    if (VM.LogAOSEvents) VM_AOSLogging.controllerStarted();

    // Create measurement entities that are NOT related to 
    // adaptive recompilation
    createProfilers();

    if (!VM_Controller.options.adaptive()) {
      // We're running an AOS bootimage with a non-adaptive primary strategy. 
      // We already set up any requested profiling infrastructure, so nothing
      // left to do but exit.
      controllerInitDone();
      VM.sysWrite("\nAOS: In non-adaptive mode; controller thread exiting.\n");
      return; // controller thread exits.
    }

    // Create the organizerThreads and schedule them
    createOrganizerThreads();

    // Create the compilationThread and schedule it
    createCompilationThread();

    // Initialize the controller "memory"
    VM_ControllerMemory.init();

    // Initialize the CompilerDNA class
    VM_CompilerDNA.init();

    // Create our set of standard optimization plans.
    VM_Controller.recompilationStrategy.init();

    controllerInitDone();

    // Enter main controller loop.
    // Pull an event to process off of 
    // VM_Controller.controllerInputQueue and handle it.  
    // If no events are on the queue, then the deleteMin call will 
    // block until an event is available.
    // Repeat forever.
    while (true) {
      Object event = VM_Controller.controllerInputQueue.deleteMin();
      ((VM_ControllerInputEvent)event).process();
    }
  }

  // Now that we're done initializing, signal the sentinel object.
  private void controllerInitDone() {
    try {
      synchronized(sentinel) {
	sentinel.notify();
      }
    } catch (Exception e) {
      e.printStackTrace();
      VM.sysFail("Failed to start up controller subsystem");
    }
  }

  
  /**
   * Called when the controller thread is about to wait on 
   * VM_Controller.controllerInputQueue
   */
  public void aboutToWait() {
  }


  /**
   * Called when the controller thread is woken after waiting on 
   * VM_Controller.controllerInputQueue
   */
  public void doneWaiting() {
    if (VM.LogAOSEvents) VM_ControllerMemory.incrementNumAwoken();
  }


  ///////////////////////
  // Initialization.
  //  Create AOS threads.
  //  Initialize AOS data structures that depend on command line arguments.
  ///////////////////////

  /**
   *  Create the compilationThread and schedule it
   */
  private void createCompilationThread() {
    VM_CompilationThread ct = new VM_CompilationThread();
    VM_Controller.compilationThread = ct;
    ct.makeDaemon(true);
    VM_Processor.getCurrentProcessor().scheduleThread(ct);
  }

  /**
   * Create profiling entities that are NOT used for adaptive recompilation
   */
  private void createProfilers() {
    VM_AOSOptions opts = VM_Controller.options;

    if (opts.GATHER_PROFILE_DATA) {
      VM_MethodCountData tmp = new VM_MethodCountData();
      VM_RuntimeMeasurements.registerReportableObject(tmp);
      VM_MethodListener methodListener = 
        new VM_AccumulatingMethodListener(opts.INITIAL_SAMPLE_SIZE, 
					  false, tmp);
      VM_RuntimeMeasurements.installMethodListener(methodListener);
      methodListener.activate();
    }
  }


  /**
   *  Create the organizerThreads and schedule them
   */
  private void createOrganizerThreads() {
    VM_AOSOptions opts = VM_Controller.options;

    // Primary backing store for method sample data
    VM_Controller.methodSamples = new VM_MethodCountData();

    // Select organizers to drive method recompilation 
    int filterOptLevel = opts.ADAPTIVE_RECOMPILATION ? 
      opts.FILTER_OPT_LEVEL : opts.DEFAULT_OPT_LEVEL;
    VM_Organizer methodOrganizer = null;
    if (opts.windowing()) {
      VM_BasicMethodListener methodListener = 
	new VM_BasicMethodListener(opts.INITIAL_SAMPLE_SIZE);
      methodOrganizer = 
	new VM_MethodSampleOrganizer(methodListener, filterOptLevel);
    } else if (opts.windowingWithHistory()) {
      VM_BasicMethodListener methodListener = 
	new VM_BasicMethodListener(opts.INITIAL_SAMPLE_SIZE);
      methodOrganizer = 
	new VM_SlopeDetectingMethodSampleOrganizer(methodListener,
						   filterOptLevel,
						   opts.MSO_ADJUST_BOUNDS,
						   opts.MSO_NUM_EPOCHS);
    } else {
      VM.sysFail("Unimplemented selection of method organizer");
    }
    VM_Controller.organizers.addElement(methodOrganizer);


    // Decay runtime measurement data 
    if (opts.ADAPTIVE_INLINING) {
      VM_Organizer decayOrganizer = 
	new VM_DecayOrganizer(new VM_YieldCounterListener(opts.DECAY_FREQUENCY));
      VM_Controller.organizers.addElement(decayOrganizer);
    }
    
    if (opts.ADAPTIVE_INLINING) {
      VM_Organizer AIOrganizer = 
	new VM_AIByEdgeOrganizer(new VM_EdgeListener());
      VM_Controller.organizers.addElement(AIOrganizer);
    }

    for (Enumeration e = VM_Controller.organizers.elements(); 
	 e.hasMoreElements(); ) {
      VM_Organizer o = (VM_Organizer)e.nextElement();
      o.makeDaemon(true);
      VM_Processor.getCurrentProcessor().scheduleThread(o);
    }
  }


  /**
   * Final report
   */
  public static void report() {
    if (VM.LogAOSEvents) VM_AOSLogging.controllerCompleted();
  }

} 
