/*
 * (C) Copyright IBM Corp. 2001
 */
/**
 * Manages a set of lockwords used by the collection threads during 
 * collection to gain exclusive access to objects or critical sections
 * of the collection process.
 * <p>
 * TestAndSet methods implemented with compare and swap logic are
 * provided for each lockword.
 */
public class VM_GCLocks {
  
  // Lockwords defined to control the synchronization of GC threads
  // during collection
  //
  private static int[] locks;
  private static int[] threadlocks;
  
  private final static int NUM_LOCKS = 10;
  
  private final static int FINISH_LOCK = 0;   // reset separately
  private final static int FINISH_MAJOR_LOCK = 1;
  private final static int INIT_LOCK = 2;
  private final static int STATICS_LOCK = 3;
  private final static int STATISTICS_LOCK = 4;
  private final static int RESET_LOCK = 5;
  
  static void init() {
    locks = new int[NUM_LOCKS];
    threadlocks = new int[VM_Scheduler.MAX_THREADS];
  }
  
  // reset all locks except the finishLock & finishMajorLock
  //
  static void reset() {
    for (int i = 2; i < NUM_LOCKS; i++) locks[i] = 0;
    VM_Memory.zero(VM_Magic.objectAsAddress(threadlocks),
		   VM_Magic.objectAsAddress(threadlocks) + VM_Scheduler.MAX_THREADS * 4);
  }
  
  static void resetFinishLock() {
    locks[FINISH_LOCK] = 0;   
    locks[FINISH_MAJOR_LOCK] = 0;   
  }
  
  //
  // TestAndSet methods use VM_Synchronization.testAndSet(Object base, int offset, int newValue).
  // If 0, sets to passed value and returns true, if not 0, returns false.
  //
  
  static boolean testAndSetThreadLock(int ndx) {
    return VM_Synchronization.testAndSet(threadlocks, ndx*4, 1);
  }
  
  static boolean testAndSetInitLock() {
    return VM_Synchronization.testAndSet(locks, INIT_LOCK*4, 1);
  }
  
  static boolean testAndSetFinishLock() {
    return VM_Synchronization.testAndSet(locks, FINISH_LOCK*4, 1);
  }
  
  static boolean testAndSetFinishMajorLock() {
    return VM_Synchronization.testAndSet(locks, FINISH_MAJOR_LOCK*4, 1);
  }
  
  static boolean testAndSetStaticsLock() {
    return VM_Synchronization.testAndSet(locks, STATICS_LOCK*4, 1);
  }
  
  static boolean testAndSetStatisticsLock() {
    return VM_Synchronization.testAndSet(locks, STATISTICS_LOCK*4, 1);
  }
  
  static boolean testAndSetResetLock() {
    return VM_Synchronization.testAndSet(locks, RESET_LOCK*4, 1);
  }
  
}
