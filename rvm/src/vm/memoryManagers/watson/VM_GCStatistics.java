/*
 * (C) Copyright IBM Corp. 2001
 */
//$Id$

class VM_GCStatistics implements VM_GCConstants, VM_Uninterruptible {

  // Number and types of GC
  static int gcExternalCount = 0;   // number of calls from System.gc
  static int gcCount = 0;           // number of minor collections
  static int gcMajorCount = 0;      // number of major collections

  // accumulated times & counts for sysExit callback printout
  static VM_Statistic bytesCopied = new VM_Statistic();
  static VM_Statistic minorBytesCopied = new VM_Statistic();     
  static VM_Statistic majorBytesCopied = bytesCopied;

  // time spend in various phases
  static VM_TimeStatistic startTime = new VM_TimeStatistic();
  static VM_TimeStatistic initTime = new VM_TimeStatistic();
  static VM_TimeStatistic rootTime = new VM_TimeStatistic();
  static VM_TimeStatistic scanTime = new VM_TimeStatistic();
  static VM_TimeStatistic finalizeTime = new VM_TimeStatistic();
  static VM_TimeStatistic finishTime = new VM_TimeStatistic();
  static VM_TimeStatistic GCTime = new VM_TimeStatistic();
  static VM_TimeStatistic minorGCTime = new VM_TimeStatistic();
  static VM_TimeStatistic majorGCTime = GCTime;

  // collisions in obtaining object ownership to copy
  static final boolean COUNT_COLLISIONS = false;
  static int collisionCount = 0;

  // more statistics
  static final boolean COUNT_BY_TYPES    = false;
  static final boolean COUNT_ALLOCATIONS = false;

  // verify that all allocations are word size aligned
  static final boolean VERIFY_ALIGNMENT = false;

  // verify that all allocations return zero-filled storage.
  static final boolean VERIFY_ZEROED_ALLOCATIONS = false;

  static final int DEFAULT = 0;  // non-generational
  static final int MINOR = 1;
  static final int MAJOR = 2;

  static void updateGCStats(int GCType, int copied) {
      if (VM.VerifyAssertions) 
	  VM.assert(copied >= 0);
      if (GCType == DEFAULT || GCType == MAJOR)
	  bytesCopied.addSample(copied);
      else if (GCType == MINOR)
	  minorBytesCopied.addSample(copied);
      else
	  VM.assert(false);
  }

  static void printGCStats(int GCType) {

      if (VM_Allocator.verbose >= 2)
	  printGCPhaseTimes();  	
      
      if (VM_Allocator.verbose >= 1 ) {
	  printVerboseOutputLine(GCType);
	  if (VM_CollectorThread.MEASURE_WAIT_TIMES)
	      VM_CollectorThread.printThreadWaitTimes();
	  else {
	      if (VM_GCWorkQueue.MEASURE_WAIT_TIMES) {
		  VM.sysWrite("*** Wait Times for Scanning \n");
		  VM_GCWorkQueue.printAllWaitTimes();
		  VM_GCWorkQueue.saveAllWaitTimes();
		  VM.sysWrite("*** Wait Times for Finalization \n");
		  VM_GCWorkQueue.printAllWaitTimes();
		  VM_GCWorkQueue.resetAllWaitTimes();
		}
	  }
	  
	  if (VM_GCWorkQueue.WORKQUEUE_COUNTS) {
	      VM.sysWrite("*** Work Queue Counts for Scanning \n");
	      VM_GCWorkQueue.printAllCounters();
	      VM_GCWorkQueue.saveAllCounters();
		VM.sysWrite("*** WorkQueue Counts for Finalization \n");
		VM_GCWorkQueue.printAllCounters();
		VM_GCWorkQueue.resetAllCounters();
	  }
	  
	  if (VM_Allocator.RENDEZVOUS_TIMES) 
	      VM_CollectorThread.gcBarrier.printRendezvousTimes();
      }
  }

  private static void printGCPhaseTimes () {

    // if invoked with -verbose:gc print output line for this last GC
      VM.sysWrite("<GC ", gcCount, "> ");
      VM.sysWrite("startTime ", (int)(startTime.last*1000000.0), "(us) ");
      VM.sysWrite("init ", (int)(initTime.last*1000000.0), "(us) ");
      VM.sysWrite("stacks & statics ", (int)(rootTime.last*1000000.0), "(us) ");
      VM.sysWrite("scanning ", (int)(scanTime.last*1000.0), "(ms) ");
      VM.sysWrite("finalize ", (int)(finalizeTime.last*1000000.0), "(us) ");
      VM.sysWriteln("finish ",  (int)(finishTime.last*1000000.0), "(us) ");
  }
       

  private static void printVerboseOutputLine (int GCType) {

      int gcTimeMs = (GCType == MINOR) ? minorGCTime.lastMs() : GCTime.lastMs();
      int free = (int) VM_Allocator.allSmallFreeMemory();
      int total = (int) VM_Allocator.allSmallUsableMemory();
      double freeFraction = free / (double) total;
      int copiedKb = (int) (((GCType == MINOR) ? minorBytesCopied.last() : bytesCopied.last()) / 1024);

      VM.sysWrite("<GC ", gcCount, ">  ");
      VM.sysWrite(gcTimeMs, " ms ");
      VM.sysWrite("   small: ", copiedKb, " Kb copied     ");
      VM.sysWrite(free / 1024, " Kb free (");
      VM.sysWrite(freeFraction * 100.0); VM.sysWrite("%)   ");
      VM.sysWrite("rate = "); VM.sysWrite(((double) copiedKb) / gcTimeMs); VM.sysWriteln("(Mb/s)      ");
  }

  static void clearSummaryStatistics () {
    VM_ObjectModel.hashRequests = 0;
    VM_ObjectModel.hashTransition1 = 0;
    VM_ObjectModel.hashTransition2 = 0;

    VM_Processor st;
    for (int i = 1; i <= VM_Scheduler.numProcessors; i++) {
      st = VM_Scheduler.processors[i];
      st.totalBytesAllocated = 0;
      st.totalObjectsAllocated = 0;
      st.synchronizedObjectsAllocated = 0;
    }
  }

  static void printSummaryStatistics () {

    if (VM_ObjectModel.HASH_STATS) {
      VM.sysWriteln("Hash operations:    ", VM_ObjectModel.hashRequests);
      VM.sysWriteln("Unhashed -> Hashed: ", VM_ObjectModel.hashTransition1);
      VM.sysWriteln("Hashed   -> Moved:  ", VM_ObjectModel.hashTransition2);
    }

    int np = VM_Scheduler.numProcessors;
    
    // showParameter();
    VM.sysWriteln("\nGC Summary:  ", gcCount, " Collections");
    if (gcCount != 0) {
	VM.sysWrite("GC Summary:  Minor Times   ");
	VM.sysWrite("total ", minorGCTime.sumS(), " (s)    ");
	VM.sysWrite("avg ", minorGCTime.avgMs(), " (ms)    ");
	VM.sysWriteln("max ", minorGCTime.maxMs(), " (ms)    ");
	if (majorGCTime.count() > 0) {
	    VM.sysWrite("GC Summary:  Major Times   ");
	    VM.sysWrite("total ", majorGCTime.sumS(), " (s)    ");
	    VM.sysWrite("avg ", majorGCTime.avgMs(), " (ms)    ");
	    VM.sysWriteln("max ", majorGCTime.maxMs(), " (ms)    ");
	}
	VM.sysWrite("GC Summary:  Minor copied  ");
	VM.sysWrite("avg ", (int) minorBytesCopied.avg() / 1024, " (Kb)    ");
	VM.sysWriteln("max ", (int) minorBytesCopied.max() / 1024, " (Kb)");
	if (majorBytesCopied.count() > 0) {
	    VM.sysWrite("GC Summary:  Major copied  ");
	    VM.sysWrite("avg ", (int) majorBytesCopied.avg() / 1024, " (Kb)    ");
	    VM.sysWriteln("max ", (int) majorBytesCopied.max() / 1024, " (Kb)");
	}
    }
    
    if (COUNT_COLLISIONS && (gcCount>0) && (np>1)) {
	VM.sysWriteln("GC Summary:  avg number of collisions per collection = ",
		      collisionCount/gcCount);
    }
    
    if (VM_Allocator.verbose >= 1 && gcCount>0) {
	VM.sysWrite("Average Time in Phases of Collection:\n");
	VM.sysWrite("startTime ", startTime.avgUs(), "(us) init ");
	VM.sysWrite( initTime.avgUs(), "(us) stacks & statics ");
	VM.sysWrite( rootTime.avgUs(), "(us) scanning ");
	VM.sysWrite( scanTime.avgMs(), "(ms) finalize ");
	VM.sysWrite( finalizeTime.avgUs(), "(us) finish ");
	VM.sysWrite( finishTime.avgUs(), "(us)>\n\n");
    }
    
    if (VM_CollectorThread.MEASURE_WAIT_TIMES && (gcCount>0)) {
	double totalBufferWait = 0.0;
	double totalFinishWait = 0.0;
	double totalRendezvousWait = 0.0;
	int avgBufferWait=0, avgFinishWait=0, avgRendezvousWait=0;
	
	VM_CollectorThread ct;
	for (int i=1; i <= np; i++ ) {
	    ct = VM_CollectorThread.collectorThreads[VM_Scheduler.processors[i].id];
	    totalBufferWait += ct.totalBufferWait;
	    totalFinishWait += ct.totalFinishWait;
	    totalRendezvousWait += ct.totalRendezvousWait;
	}
	avgBufferWait = ((int)((totalBufferWait/(double)gcCount)*1000000.0))/np;
	avgFinishWait = ((int)((totalFinishWait/(double)gcCount)*1000000.0))/np;
	avgRendezvousWait = ((int)((totalRendezvousWait/(double)gcCount)*1000000.0))/np;
	
	VM.sysWrite("Average Wait Times For Each Collector Thread In A Collection:\n");
	VM.sysWrite("Buffer Wait ", avgBufferWait, " (us) Finish Wait ");
	VM.sysWrite( avgFinishWait, " (us) Rendezvous Wait ");
	VM.sysWrite( avgRendezvousWait, " (us)\n\n");
    }

    if (COUNT_ALLOCATIONS) {
      long bytes = 0, objects = 0, syncObjects = 0;
      VM_Processor st;
      for (int i = 1; i <= VM_Scheduler.numProcessors; i++) {
        st = VM_Scheduler.processors[i];
        bytes += st.totalBytesAllocated;
        objects += st.totalObjectsAllocated;
        syncObjects += st.synchronizedObjectsAllocated;
      }
      VM.sysWrite(" Total No. of Objects Allocated in this run ");
      VM.sysWrite(Long.toString(objects));
      VM.sysWrite("\n Total No. of Synchronized Objects Allocated in this run ");
      VM.sysWrite(Long.toString(syncObjects));
      VM.sysWrite("\n Total No. of bytes Allocated in this run ");
      VM.sysWrite(Long.toString(bytes));
      VM.sysWrite("\n");
    }
  } // printSummaryStatistics


  static void profileCopy (Object obj, Object[] tib) {
      if (COUNT_BY_TYPE) {
          VM_Type t = VM_Magic.objectAsType(tib[0]);
	  t.copiedCount++;
      }
  }

  static void profileAlloc (VM_Address addr, int size, Object[] tib) {
      VM_Magic.pragmaInline();
      if (COUNT_BY_TYPE) {
          VM_Type t = VM_Magic.objectAsType(tib[0]);
	  t.allocatedCount++;
      }
      if (COUNT_ALLOCATIONS) {
	  VM_Processor st = VM_Processor.getCurrentProcessor();
	  st.totalBytesAllocated += size;
	  st.totalObjectsAllocated++;
          VM_Type t = VM_Magic.objectAsType(tib[0]);
          if (t.thinLockOffset != -1) {
	      st.synchronizedObjectsAllocated++;
          }
      }

      if (VERIFY_ALIGNMENT) {
	if ((size & ~(WORDSIZE - 1)) != size ||
	    VM_Memory.align(addr, WORDSIZE).NE(addr)) {
	  VM.sysWrite("Non word size aligned region allocated ");
	  VM.sysWrite("size is ", size);
	  VM.sysWriteln(" address is ", addr.toInt());
	  VM.sysFail("...exiting VM");
	}
      }

      if (VERIFY_ZEROED_ALLOCATIONS) {
	for (int i=0; i<size; i+= 4) {
	  int val = VM_Magic.getMemoryWord(addr.add(i));
	  if (val != 0) {
	    VM.sysWrite("Non-zeroed memory allocated ");
	    VM.sysWriteln("\taddress is ",addr.toInt());
	    VM.sysWriteln("\tnon-zero address is ", addr.add(i).toInt());
	    VM.sysWriteln("\tvalue is ", val);
	    VM.sysFail("...exiting VM");
	  }
	}
      }
  }  // profileAlloc

}
