/*
 * (C) Copyright IBM Corp. 2001
 */
//$Id$
package com.ibm.JikesRVM.adaptive;

import com.ibm.JikesRVM.VM;
import com.ibm.JikesRVM.VM_Thread;
import com.ibm.JikesRVM.VM_Method;
import com.ibm.JikesRVM.VM_CompiledMethod;
import com.ibm.JikesRVM.VM_CompiledMethods;
import com.ibm.JikesRVM.opt.*;

/**
 * An organizer for method listener information. 
 * <p>
 * This organizer is designed to work well with non-decayed 
 * cumulative method samples.  The basic idea is that each time 
 * the sampling threshold is reached we update the accumulated method 
 * sample data with the new data and then notify the controller of all 
 * methods that were sampled in the current window.
 * <p>
 * It augments this basic mechanism by tracking estimates of how 
 * many samples are expected for the method in the next sampling window.
 * It then uses the error between the estimated and actual samples 
 * to detect methods that appear to be "ramping up" or "ramping down."  
 * When such methods are detected, the organizer adjusts the number of 
 * samples it reports to the  controller for that method, within bounds 
 * specified by the controller.
 * <p>
 * TODO: Rather than adjusting the num samples reported (ie lying) to
 * the controller, we should add a ramp up/down factor to
 * the hot method event and explictly include it in the model calculation.
 * I'm kludging it for now because it isn't clear if (1) 
 * we can get estimators that will detect the ramp up/down effects accurately
 * enough to be useful and (2) even if we can detect ramp up/down 
 * that adjusting the number of samples will make enough of a difference to
 * overcome the overhead of keeping the history.
 * 
 * @author Dave Grove
 */
final class VM_SlopeDetectingMethodSampleOrganizer extends VM_Organizer {

  private static final boolean DEBUG = false;

  /**
   * Filter out all opt-compiled methods that were compiled 
   * at this level or higher.
   */
  private int filterOptLevel;

  /**
   *  The listener
   */
  private VM_BasicMethodListener listener;

  /**
   * Bounds on adjustment to num samples made by delta from recent history.
   */
  private double adjustmentBounds;
  
  /**
   * mapping from cmid to history array
   */
  private int[] historyMap;

  /**
   * The history arrays.
   * We do not use history[0].
   * For each entry = history[i]:
   *    entry[CMID_IDX]    is the cmid.
   *    entry[EPOCH_IDX]   is the last epoch in which this cmid was sampled.
   *    entry[ENTRY_IDX+k] is the number of samples taken of cmid
   *                       in epoch (k mod numEpochs)
   */
  private int[][] history;
  private static final int FIRST_ENTRY = 1;
  private static final int NOT_MAPPED = 0;
  private static final int CMID_IDX  = 0;
  private static final int EPOCH_IDX = 1;
  private static final int ENTRY_IDX = 2;

  /** idx of next available history entry */
  private int nextEntry = FIRST_ENTRY;

  /**
   * The current epoch number.
   */
  private int curEpoch;

  /**
   * How many epochs should we maintain?
   */
  private int numEpochs;

  /**
   * @param listener         the associated listener
   * @param filterOptLevel   filter out all opt-compiled methods that 
   *                         were compiled at this level or higher
   * @param adjustmentBounds the organizer is allowed to adjust sample
   *                         data by a factor of anywhere from 
   *                         (1-adjustmentBounds) to (1+adjustmentBounds)
   * @param numEpochs        history window size
   */
  VM_SlopeDetectingMethodSampleOrganizer(VM_BasicMethodListener listener, 
					 int filterOptLevel,
					     double adjustmentBounds,
					     int numEpochs) {
    this.listener         = listener;
    this.filterOptLevel   = filterOptLevel;
    this.adjustmentBounds = adjustmentBounds;
    this.numEpochs        = numEpochs;
    this.historyMap       = new int[(int)(VM_CompiledMethods.numCompiledMethods() * 1.25)];
    if (VM.VerifyAssertions) VM._assert(NOT_MAPPED == 0);
    if (VM.VerifyAssertions) VM._assert(NOT_MAPPED != FIRST_ENTRY);
    this.history          = new int[128][];
    listener.setOrganizer(this);
    makeDaemon(true);
  }


  /**
   * Initialization: set up data structures and sampling objects.
   */
  public void initialize() {
    if (VM.LogAOSEvents) 
      VM_AOSLogging.methodSampleOrganizerThreadStarted(filterOptLevel);

    // Install and activate my listener
    VM_RuntimeMeasurements.installMethodListener(listener);
    listener.activate();
  }


  /**
   * Method that is called when the sampling threshold is reached
   */
  void thresholdReached() {
    if (VM.LogAOSEvents) VM_AOSLogging.organizerThresholdReached();
    prepareForNewEpoch();
    processRawData();
    listener.reset();
    listener.activate();
  }


  // Prepare history data structures to process a new epoch of samples
  private void prepareForNewEpoch() {
    // advance epoch
    if (++curEpoch == numEpochs) {
      curEpoch = 0;
    }
    
    // remove completely defunct entries or clear the data in
    // entry[ENTRY_IDX+curEpoch] to prepare to put new data there.
    for (int i=FIRST_ENTRY; i<nextEntry; i++) {
      int[] entry = history[i];
      if (entry[EPOCH_IDX] == curEpoch) {
	clearHistory(entry[CMID_IDX]);
      } else {
	entry[ENTRY_IDX+curEpoch] = 0;
      }
    }
  }
  
  
  // Process the raw data from the listener by accumulating samples into
  // the entries for curEpoch, updating the global method sample data,
  // and notifying the controller of interesting methods (reporting their
  // adjusted number of samples -- see TODO in class header comment).
  private void processRawData() {
    int numSamples = listener.getNumSamples();
    int[] samples = listener.getSamples();
    
    for (int i=1; i<numSamples; i++) {
      int cmid = samples[i];
      int[] entry = getEntry(cmid);
      entry[ENTRY_IDX+curEpoch]++;
      entry[EPOCH_IDX] = curEpoch;
    }

    if (DEBUG) dumpHistory();

    for (int i= FIRST_ENTRY; i<nextEntry; i++) {
      int[] entry = history[i];
      if (entry[EPOCH_IDX] == curEpoch) {
	int cmid = entry[CMID_IDX];
	int samplesThisTime = entry[ENTRY_IDX+curEpoch];
	double samplesThisTimeDouble = (double)samplesThisTime;

	// (1) update global sampling data
	VM_Controller.methodSamples.update(cmid, samplesThisTimeDouble);
	double totalSamples = VM_Controller.methodSamples.getData(cmid);

	// (2) See if the controller cares about this method. 
	//     If it does, then make a prediction and notify the controller.
	//     The Controller cares unless it's either a trap method or 
	//     already opt compiled at filterOptLevel or higher.
	//     But, we require that a method be sampled at least 3.0 times
	//     before we report it to the controller to avoid reporting truly
	//     cold but randomly sampled once or twice methods.
	if (totalSamples > 3.0) {
	  VM_CompiledMethod cm = VM_CompiledMethods.getCompiledMethod(cmid);
	  if (cm != null) {
	    int compilerType = cm.getCompilerType();
	    if (!(compilerType == VM_CompiledMethod.TRAP ||
		  (compilerType == VM_CompiledMethod.OPT && 
		   (((VM_OptCompiledMethod)cm).getOptLevel() >= filterOptLevel)))) {

	      // (2a) compute prediction
	      int histSamples = -samplesThisTime;
	      for (int j=0; j<numEpochs; j++) {
		histSamples += entry[ENTRY_IDX+j];
	      }
	      double prediction = 
		((double)histSamples) / ((double)(numEpochs-1));
	      double adjFactor;
	      if (prediction > 0.00001) {
		double slope = samplesThisTimeDouble/prediction;
		if (DEBUG) {
		  VM_Method meth = cm.getMethod();
		  VM.sysWrite("For method "+meth+"("+cmid+") with thisTime="+samplesThisTime+
			      " and total="+totalSamples+ " and prediction ="+prediction+
			      " and history ");
		  for (int k=0; k<numEpochs; k++) {
		    VM.sysWrite(entry[ENTRY_IDX+k],false);
		    VM.sysWrite(" ");
		  }
		  VM.sysWrite("\n\tWe compute a slope of "+slope);
		}

		// Bound adjustment factor as previously instructed by controller
		adjFactor = slope;
		if (adjFactor < 1.0) {
		  adjFactor = 1.0 - (0.5 * (1.0 - slope)); // dampen adjustment
		  if (adjFactor < adjustmentBounds) {
		    adjFactor = adjustmentBounds;
		  } 
		} else {
		  adjFactor = 1.0 + (0.5 * (slope - 1.0 )); // dampen adjustment
		  if (adjFactor > adjustmentBounds + 1.0) {
		    adjFactor = 1.0 + adjustmentBounds;
		  }
		}
	      } else {
		adjFactor = 1.0 + (adjustmentBounds / 2.0);
		if (DEBUG) {
		  VM_Method meth = cm.getMethod();
		  VM.sysWrite("No sample history for method "+meth+"("+cmid+")");
		}
	      }

	      // (2b) adjust totalSamples based on how different it is from the prediction
	      totalSamples = adjFactor * totalSamples;
	      if (DEBUG) VM.sysWrite(" leading to an adjustment factor of "+adjFactor+
				     " and adjusted totalSamples="+
				     totalSamples+"\n");

	      // (2c) tell the controller about this method
	      VM_HotMethodRecompilationEvent event = 
		new VM_HotMethodRecompilationEvent(cm, totalSamples);
	      if (VM_Controller.controllerInputQueue.prioritizedInsert(totalSamples, event)){
		if (VM.LogAOSEvents) {
		  VM_AOSLogging.controllerNotifiedForHotness(cm, totalSamples);
		}
	      } else {
		if (VM.LogAOSEvents) VM_AOSLogging.controllerInputQueueFull(event);
	      }
	    }
	  }
	}
      }
    }
  }

  private void dumpHistory() {
    VM.sysWrite("\n######\ncurEpoch = "+curEpoch+
		", numEpochs = "+numEpochs+"\n");
    for (int i=FIRST_ENTRY; i<nextEntry; i++) {
      int[] entry = history[i];
      VM.sysWrite("cmid = "+entry[CMID_IDX]+", lastEpoch ="+entry[EPOCH_IDX]+
		  "\thistory = ");
      for (int j=0; j<numEpochs; j++) {
	VM.sysWrite(entry[ENTRY_IDX+j],false);
	VM.sysWrite(" ");
      }
      VM.sysWrite("\n");
    }

    VM.sysWrite("Checking history map...");
    for (int i=FIRST_ENTRY; i<nextEntry; i++) {
      if (historyMap[history[i][CMID_IDX]] != i) {
	VM.sysWrite("Mismatch between historyMap and entry (1) "+i);
      }
    }
    for (int i=0; i<historyMap.length; i++) {
      if (historyMap[i] != 0) {
	if (history[historyMap[i]][CMID_IDX] != i) {
	  VM.sysWrite("Mismatch between historyMap and entry (2) "+i);
	}
      }
    }
    VM.sysWrite("...done\n");
  }

  // get the entry[] for a cmid, allocating new entries and
  // growing the backing store as needed.
  private int[] getEntry(int cmid) {
    if (cmid >= historyMap.length) {       // grow historyMap
      int[] tmp = new int[(int)(VM_CompiledMethods.numCompiledMethods() * 1.25)];
      for (int i=0; i< historyMap.length; i++) {
	tmp[i] = historyMap[i];
      }
      historyMap = tmp;
    }
    if (historyMap[cmid] == NOT_MAPPED) {
      int idx = nextEntry++;
      if (idx >= history.length) {         // grow history
	int[][] tmp = new int[history.length*2][];
	for (int i=0; i<history.length; i++) {
	  tmp[i] = history[i];
	}
	history = tmp;
      }
      if (VM.VerifyAssertions) VM._assert(history[idx] == null);
      history[idx] = new int[ENTRY_IDX+numEpochs];
      history[idx][CMID_IDX] = cmid;
      historyMap[cmid] = idx;
      return history[idx];
    } else {
      return history[historyMap[cmid]];
    }
  }

  // remove an entry
  private void clearHistory(int cmid) {
    if (DEBUG) VM.sysWrite("Clearing history for "+cmid+"\n");
    int entryIdx = historyMap[cmid];
    if (entryIdx != NOT_MAPPED) {
      historyMap[cmid] = NOT_MAPPED;
      nextEntry--;
      if (entryIdx<nextEntry) {
	history[entryIdx] = history[nextEntry];
	historyMap[history[entryIdx][CMID_IDX]] = entryIdx;
      }
      history[nextEntry] = null;
    }
  }
}
