/*
 * (C) Copyright IBM Corp. 2001
 */
//$Id$

import java.util.*;

/**
 * An organizer of call graph edge information that is used for 
 * adaptive inlining.
 *
 * VM_AIByEdgeOrganizer communicates with an edge listener through a 
 * integer array, denoted buffer.  When this organizer is woken up
 * via threshold reached, it processes the sequence of triples 
 * that are contained in buffer.
 * After the buffer is processed, the organizer checks to see
 * if any methods compiled at VM_Controller.options.MAX_OPT_LEVEL
 * should be recompiled due to inlining opportunities.
 * 
 * EXPECTATION: buffer is filled all the way up with triples.
 * 
 * Potential problems: The system does not retain new edges between
 * organizer runs.  There could be a race condition where a
 * ??? where what?? --dave
 * 
 * @author Peter Sweeney 
 * @author Dave Grove
 * @modified Stephen Fink
 * @modified Michael Hind
 * @modified Matthew Arnold
 */
class VM_AIByEdgeOrganizer extends VM_Organizer implements VM_Decayable {

  private final static boolean DEBUG = false;

  /*
   * buffer provides the communication channel between the edge listener
   * and the organizer.
   * The buffer contains an array of triples <callee, caller, address> where
   * the caller and callee are VM_CompiledMethodID's, and address identifies
   * the call site.
   * bufferSize is the number of triples contained in buffer.
   * The edge listener adds triples.  
   * At some point the listener deregisters itself and notifies the organizer 
   * by calling thresholdReached().
   */
  private int[] buffer;
  private int   bufferSize;
  private int   numberOfBufferTriples;

  /**
   *	Representation of call graph.
   */
  private VM_PartialCallGraph callGraph;
  /*
   *  The edge listener
   */
  VM_EdgeListener edgeListener;

  /**
   * Constructor
   */
  VM_AIByEdgeOrganizer(VM_EdgeListener edgeListener) {
     if (DEBUG) VM.sysWrite("VM_AIByEdgeOrganizer.<init>(): enter\n");     
     this.edgeListener = edgeListener;
     edgeListener.setOrganizer(this);
  }

  /**
   */
  public void decay() {
     VM_AdaptiveInlining.decay();
  }

  /**
   * Initialization: set up data structures and sampling objects.
   */
  public void initialize() {
    if (DEBUG) VM.sysWrite("VM_AIByEdgeOrganizer.initialize(): enter\n");

    if (VM.LogAOSEvents) VM_AOSLogging.AIByEdgeOrganizerThreadStarted();

    numberOfBufferTriples = VM_Controller.options.AI_SAMPLE_SIZE;

    bufferSize = numberOfBufferTriples * 3;
    buffer     = new int[bufferSize];

    edgeListener.setBuffer(buffer); 

    // allocate internal data structures.
    callGraph   = VM_AdaptiveInlining.getPartialCallGraph();

    // Install and activate the edge listener
    VM_RuntimeMeasurements.installContextListener(edgeListener);
    edgeListener.activate();

    // register as decayable
    VM_RuntimeMeasurements.registerDecayableObject(this);

    if (DEBUG) VM.sysWrite("VM_AIByEdgeOrganizer.initialize(): exit\n");
  }

  /**
   * Method that is called when the sampling threshold is reached.
   * Process contents of buffer: 
   *    add call graph edges and increment their weights.
   */
  void thresholdReached() {
    if(DEBUG)
      VM.sysWrite("VM_AIByEdgeOrganizer.thresholdReached(): enter and reregister.\n");

    VM_AdaptiveInlining.incrementNumYieldPoints(edgeListener.
                getTimesUpdateCalled());
    for (int i=0; i<bufferSize; i=i+3) {

       VM_CompiledMethod compiledMethod = null;

       int calleeCMID = buffer[i+0];
       compiledMethod   = VM_CompiledMethods.getCompiledMethod(calleeCMID);
       if (compiledMethod == null) continue;
       VM_Method callee = compiledMethod.getMethod();
       if (OPT_InliningUtilities.methodShouldNotBeInlined(callee)) {
	  if(DEBUG)VM.sysWrite("  "+callee+" should not be inlined!\n");
	  continue;
       }

       int callerCMID = buffer[i+1];
       compiledMethod   = VM_CompiledMethods.getCompiledMethod(callerCMID);
       if (compiledMethod == null) continue;
       VM_Method stackFrameCaller = compiledMethod.getMethod();
       
       int MCOffset = buffer[i+2];
       VM_CompilerInfo compilerInfo = compiledMethod.getCompilerInfo();
       
       int bytecodeIndex = -1;
       VM_Method caller = null;

       int compilerType = compilerInfo.getCompilerType();
       switch (compilerType) {
       case VM_CompilerInfo.TRAP:
       case VM_CompilerInfo.JNI:
	 if (DEBUG) VM.sysWrite("Skipping sample with TRAP/JNI caller");
	 continue;
       case VM_CompilerInfo.BASELINE:
	 {
	   VM_BaselineCompilerInfo baseCompilerInfo = 
	     (VM_BaselineCompilerInfo)compilerInfo;
	   // note: the following call expects the offset in INSTRUCTIONS!
	   bytecodeIndex = baseCompilerInfo.findBytecodeIndexForInstruction
	     (MCOffset>>VM.LG_INSTRUCTION_WIDTH);
	   caller = stackFrameCaller;
	 }
	 break;
       case VM_CompilerInfo.OPT:
	 {
	   VM_OptCompilerInfo optCompilerInfo = (VM_OptCompilerInfo)compilerInfo;
	   VM_OptMachineCodeMap mc_map = optCompilerInfo.getMCMap();
	   try {
	     bytecodeIndex = mc_map.getBytecodeIndexForMCOffset(MCOffset);
	     if (bytecodeIndex == -1) {
	       // this can happen we we sample a call 
               // to a runtimeSerivce routine. 
	       // We aren't setup to inline such methods anyways, 
               // so skip the sample.
	       if (DEBUG) VM.sysWrite("  *** SKIP SAMPLE "+
				      stackFrameCaller+"@"+compiledMethod+
				      " at MC offset "+MCOffset+
				      " calling "+callee+
				      " due to invalid bytecodeIndex\n");
	       continue; // skip sample.
	     }
	   } catch (java.lang.ArrayIndexOutOfBoundsException e) {
	     VM.sysWrite("  ***ERROR: getBytecodeIndexForMCOffset("+MCOffset
			 +") ArrayIndexOutOfBounds!\n");
	     e.printStackTrace(); caller = stackFrameCaller;
	     continue;  // skip sample
	   } catch (OPT_OptimizingCompilerException e) {
	     VM.sysWrite("***Error: SKIP SAMPLE: can't find bytecode index in OPT compiled "+
			 stackFrameCaller+"@"+compiledMethod+" at MC offset "+MCOffset+"!\n");
	     continue;  // skip sample
	   }
	   
	   try {
	     caller = mc_map.getMethodForMCOffset(MCOffset);
	   } catch (java.lang.ArrayIndexOutOfBoundsException e) {
	     VM.sysWrite("  ***ERROR: getMethodForMCOffset("
			 +MCOffset+") ArrayIndexOutOfBounds!\n");
	     e.printStackTrace(); caller = stackFrameCaller;
	     continue;
	   } catch (OPT_OptimizingCompilerException e) {
	     VM.sysWrite("***Error: SKIP SAMPLE: can't find caller in OPT compiled "+
			 stackFrameCaller+"@"+compiledMethod+" at MC offset "
			 +MCOffset+"!\n");
	     continue;  // skip sample
	   }

	   if (caller == null) {
	     VM.sysWrite("  ***ERROR: getMethodForMCOffset("+
			 MCOffset+") returned null!\n");
	     caller = stackFrameCaller;
	     continue;  // skip sample
	   }
	 }
	 break;
       }

       // increment the call graph edge, adding it if needed
       callGraph.incrementEdge(caller, bytecodeIndex, callee);
    }

    // If using an offline inline plan, don't recompute anything, and don't 
    // notify the controller.
    if (!VM_Controller.options.USE_OFFLINE_INLINE_PLAN) {
      // force a recomputation of the current state of hot edges
      Vector vectorOfTriples = VM_AdaptiveInlining.recomputeHotEdges();
      
      if(DEBUG) {
	VM.sysWrite("\nNew edges found:\n");
	for (int i=0; i<vectorOfTriples.size(); i++) {
	  VM_CallSiteTriple triple = (VM_CallSiteTriple)
                vectorOfTriples.elementAt(i);
	  VM.sysWrite((i+1)+": "+triple.toString()+"\n");
	}
      }
      
      VM_MethodCountSet HM_data = 
	VM_Controller.methodSamples.collectHotMethods(VM_Controller.options.MAX_OPT_LEVEL,
						      VM_Controller.options.AI_METHOD_HOTNESS_THRESHOLD);
      if (VM.LogAOSEvents) 
	VM_AOSLogging.AIorganizerFoundHotMethods(HM_data.cmids.length);
      
      findMethodsToRecompile(vectorOfTriples, HM_data);
    }
    if(DEBUG) callGraph.dump();

    // Clear listener and activate it again.
    edgeListener.reset();
    edgeListener.activate();

    if(DEBUG)VM.sysWrite("VM_AIByEdgeOrganizer.thresholdReached(): exit\n");
  }  

   /*
    * Given a vector of new edges and a set of methods, determine if 
    * a method in the set contains a call site corresponding to an edge 
    * in the vector and that call site has not been inlined.
    *
    * @param vectorOfTriples	new edges that are hot in call graph
    * @param hotMethodSet	methods that are hot and compiled at max opt level.
    *
    */
   private void findMethodsToRecompile(Vector vectorOfTriples,
				       VM_MethodCountSet hotMethodSet) {
     if (DEBUG) VM.sysWrite("\nVM_AIByEdgeOrganizer.findMethodsToRecompile() "
                + hotMethodSet.cmids.length+"\n");

     if (vectorOfTriples.isEmpty() || hotMethodSet.cmids.length == 0) {
       if (DEBUG) VM.sysWrite("  return early\n");
       return;
     }

     // Consider each hot max opt level method
     for (int i=0; i<hotMethodSet.cmids.length; i++) {
       int cmid	                   = hotMethodSet.cmids[i];
       VM_CompiledMethod hotMethod = VM_CompiledMethods.getCompiledMethod(cmid);
       double numSamples           = hotMethodSet.counters[i];
       VM_OptMachineCodeMap mcMap  = 
	 ((VM_OptCompilerInfo)hotMethod.getCompilerInfo()).getMCMap();
       double edgeHotness          = 0.0;

       if (DEBUG) VM.sysWrite(" Process hot method: "+
			      hotMethod.getMethod()+" with "+numSamples+"\n");
       // For each edge, see if the callsite is present, 
       // but the callee is absent in hotMethod.
       for (Enumeration triples = vectorOfTriples.elements(); 
                triples.hasMoreElements(); ) {
	 VM_CallSiteTriple triple = (VM_CallSiteTriple)triples.nextElement();
	 if (!VM_AdaptiveInlining.knownNonInlinedEdge(cmid, triple)) {
	   VM_Method caller = triple.getCaller();
	   int bytecodeIndex= triple.getBytecodeIndex();
	   VM_Method callee = triple.getCallee();
	   if (DEBUG) VM.sysWrite("   Edge candidate "+triple+"\n");

	   try {
	     if (mcMap.callsitePresent(caller, bytecodeIndex)) {
	       if (DEBUG) VM.sysWrite(" FOUND EDGE: "+triple+
				      " that can be inlined into "
				      +hotMethod.getMethod()+"\n");
	       edgeHotness += triple.getWeight();
	       if (VM.LogAOSEvents) 
		 VM_AOSLogging.inliningOpportunityDetected(hotMethod, 
							   numSamples, 
							   triple);
	     }
	   }
	   catch (Throwable e){
	     VM.sysWrite("ERROR in adaptive system! Exception caught!\n");
	     VM.sysWrite(" AI Organizer considering edge "+triple+
			 " to be inlined into "
			   +hotMethod.getMethod()+"\n");
	     e.printStackTrace();
	   }
	 }
       }

       // Notify the controller if we found a candidate edge in hotMethod
       if (edgeHotness > 0.0001) {
	 edgeHotness   /= VM_AdaptiveInlining.getNumYieldPoints();
	 double boost   = 
	   1.0 + (VM_Controller.options.MAX_EXPECTED_AI_BOOST * edgeHotness);
	 VM_AINewHotEdgeEvent event = 
	   new VM_AINewHotEdgeEvent(cmid, numSamples, boost);
	 if (!VM_Controller.controllerInputQueue.prioritizedInsert(numSamples, 
								   event)) {
	   if (VM.LogAOSEvents) VM_AOSLogging.controllerInputQueueFull(event);
	 } else {
	   if (VM.LogAOSEvents) {
	     VM_CompiledMethod cm = VM_CompiledMethods.getCompiledMethod(cmid);
	     VM_AOSLogging.controllerNotifiedForInlining(cm, 
							 numSamples, 
							 boost);
	   }
	 }
       }
     }
     
     if (DEBUG) 
       VM.sysWrite("\nVM_AIByEdgeOrganizer.findMethodsToRecompile() exit\n\n");
   }

  /**
   * Last opportunity to say something.
   * Dump call graph and edge weights.
   */
  public void report() {
     if (VM_Controller.options.FINAL_REPORT_LEVEL >= 2) {
       VM.sysWrite("\n\nVM_AIByEdgeOrganizer.report()\n");
       VM.sysWrite(" callGraph dump\n");
       callGraph.dump();
     }
  }
}
