/*
 * (C) Copyright IBM Corp. 2001
 */
//$Id$


package com.ibm.JikesRVM.memoryManagers;

import com.ibm.JikesRVM.VM_Magic;
import com.ibm.JikesRVM.VM_Scheduler;
import com.ibm.JikesRVM.VM_Constants;
import com.ibm.JikesRVM.VM_Synchronization;
import com.ibm.JikesRVM.VM_Memory;
import com.ibm.JikesRVM.VM_PragmaUninterruptible;

/**
 * Manages a set of lockwords used by the collection threads during 
 * collection to gain exclusive access to objects or critical sections
 * of the collection process.
 * <p>
 * TestAndSet methods implemented with compare and swap logic are
 * provided for each lockword.
 *
 * @author Dick Attanasio
 * @author Stephen Smith
 */
public class VM_GCLocks {
  
  private final static int NUM_LOCKS = 10;
  
  private final static int FINISH_LOCK = 0;   // reset separately
  private final static int FINISH_MAJOR_LOCK = 1;
  private final static int INIT_LOCK = 2;
  private final static int STATICS_LOCK = 3;
  private final static int STATISTICS_LOCK = 4;
  private final static int RESET_LOCK = 5;
  
  // Lockwords defined to control the synchronization of GC threads
  // during collection
  //
  private static final int[] locks = new int[NUM_LOCKS];

  private static final int[] threadlocks = new int[VM_Scheduler.MAX_THREADS];
  
  // reset all locks except the finishLock & finishMajorLock
  //
  static void reset() throws VM_PragmaUninterruptible {
    for (int i = 2; i < NUM_LOCKS; i++) locks[i] = 0;
    VM_Memory.zero(VM_Magic.objectAsAddress(threadlocks),
		   VM_Magic.objectAsAddress(threadlocks).add(VM_Scheduler.MAX_THREADS * 4));
  }
  
  static void resetFinishLock() throws VM_PragmaUninterruptible {
    locks[FINISH_LOCK] = 0;   
    locks[FINISH_MAJOR_LOCK] = 0;   
  }
  
  //
  // TestAndSet methods use VM_Synchronization.testAndSet(Object base, int offset, int newValue).
  // If 0, sets to passed value and returns true, if not 0, returns false.
  //
  
  static boolean testAndSetThreadLock(int ndx) throws VM_PragmaUninterruptible {
    return VM_Synchronization.testAndSet(threadlocks, ndx*4, 1);
  }
  
  static boolean testAndSetInitLock() throws VM_PragmaUninterruptible {
    return VM_Synchronization.testAndSet(locks, INIT_LOCK*4, 1);
  }
  
  static boolean testAndSetFinishLock() throws VM_PragmaUninterruptible {
    return VM_Synchronization.testAndSet(locks, FINISH_LOCK*4, 1);
  }
  
  static boolean testAndSetFinishMajorLock() throws VM_PragmaUninterruptible {
    return VM_Synchronization.testAndSet(locks, FINISH_MAJOR_LOCK*4, 1);
  }
  
  static boolean testAndSetStaticsLock() throws VM_PragmaUninterruptible {
    return VM_Synchronization.testAndSet(locks, STATICS_LOCK*4, 1);
  }
  
  static boolean testAndSetStatisticsLock() throws VM_PragmaUninterruptible {
    return VM_Synchronization.testAndSet(locks, STATISTICS_LOCK*4, 1);
  }
  
  static boolean testAndSetResetLock() throws VM_PragmaUninterruptible {
    return VM_Synchronization.testAndSet(locks, RESET_LOCK*4, 1);
  }
  
}
