/*
 * (C) Copyright IBM Corp. 2001
 */
//$Id$

/**
 * Base class for iterators that identify object references and JSR return addresses
 * held in stackframes produced by each of our compilers (baseline, opt, etc.).
 * All compiler specific GCMapIterators extend this abstract class.
 *
 * @see VM_BaselineGCMapIterator
 * @see VM_OptGCMapIterator
 * @see VM_GCMapIteratorGroup
 *
 * @author Janice Shepherd
 */
abstract class VM_GCMapIterator implements VM_Uninterruptible {
  
  /** thread whose stack is currently being scanned */
  VM_Thread thread; 
  
  /** address of stackframe currently being scanned */
  VM_Address  framePtr;
  
  /** address where each gpr register was saved by previously scanned stackframe(s) */
  int[]     registerLocations;
  
  /**
   * Prepare to scan a thread's stack and saved registers for object references.
   *
   * @param thread VM_Thread whose stack is being scanned
   */
  void newStackWalk(VM_Thread thread) {
    this.thread = thread;
  }
  
  /**
   * Prepare to iterate over object references and JSR return addresses held by a stackframe.
   * 
   * @param compiledMethod     method running in the stackframe
   * @param instructionOffset  offset of current instruction within that method's code
   * @param framePtr           address of stackframe to be visited
   */
  abstract void setupIterator(VM_CompiledMethod compiledMethod, int instructionOffset, VM_Address framePtr);
  
  /**
   * Get address of next object reference held by current stackframe.
   * Returns zero when there are no more references to report.
   * <p>
   * Side effect: registerLocations[] updated at end of iteration.
   * TODO: registerLocations[] update should be done via separately called
   * method instead of as side effect.
   * <p>
   *
   * @return address of word containing an object reference
   *         zero if no more references to report
   */
  abstract VM_Address getNextReferenceAddress();
  
  /**
   * Get address of next JSR return address held by current stackframe.
   *
   * @return address of word containing a JSR return address
   *         zero if no more return addresses to report
   */
  abstract VM_Address getNextReturnAddressAddress();
  
  /**
   * Prepare to re-iterate on same stackframe, and to switch between
   * "reference" iteration and "JSR return address" iteration.
   */
  abstract void reset();
  
  /**
   * Iteration is complete, release any internal data structures including 
   * locks acquired during setupIterator for jsr maps.
   * 
   */
  abstract void cleanupPointers();
  
  /**
   * Get the type of this iterator (BASELINE, OPT, etc.).
   * Called from VM_GCMapIteratorGroup to select which iterator
   * to use for a stackframe.  The possible types are specified 
   * in VM_CompiledMethod.
   *
   * @return type code for this iterator
   */
  abstract int getType();
}
