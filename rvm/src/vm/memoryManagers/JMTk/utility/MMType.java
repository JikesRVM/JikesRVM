/*
 * (C) Copyright Department of Computer Science,
 *     Australian National University. 2003
 */
//$Id$
package com.ibm.JikesRVM.memoryManagers.JMTk;

import com.ibm.JikesRVM.memoryManagers.vmInterface.Constants;
import com.ibm.JikesRVM.memoryManagers.vmInterface.VM_Interface;

import com.ibm.JikesRVM.VM_Address;
import com.ibm.JikesRVM.VM_Offset;

import com.ibm.JikesRVM.VM_Uninterruptible;
import com.ibm.JikesRVM.VM_PragmaInterruptible;
import com.ibm.JikesRVM.VM_PragmaInline;

/**
 * This class encapsulates type-specific memory management information. 
 *
 * @author Andrew Gray
 * @author <a href="http://cs.anu.edu.au/~Steve.Blackburn">Steve Blackburn</a>
 * @version $Revision$
 * @date $Date$
 */ 
public final class MMType implements Constants, VM_Uninterruptible {
  // AJG: Maybe should make this immutable.  See Item 13 of Effective Java.
  private boolean isReferenceArray;
  private boolean isDelegated;
  private boolean isAcyclic;
  private VM_Offset arrayOffset;
  private int [] offsets;
  private int allocator;
  
  // per-type statistics
  private int allocCount;
  private int allocBytes;
  private int copyCount;                 
  private int copyBytes;                 
  private int scanCount;                 
  private int scanBytes;
  private int bootCount;
  private int bootBytes; 
  
  private static final boolean PROFILING_STATISTICS = false;

  /****************************************************************************
   *
   * Initialization
   */

  /**
   * Constructor
   *
   * @param isDelegated True if scanning of this type is delegated to the VM
   * @param isReferenceArray True if the type is array of reference
   * @param isAcyclic True if the type is inherently acyclic
   * @param allocator The allocator through which instances of this
   * type should be allocated by defaul.
   * @param offsets An array of integer offsets for the fields of this
   * type (if any).
   */
  public MMType(boolean isDelegated, boolean isReferenceArray, 
                boolean isAcyclic, int allocator, int [] offsets)
    throws VM_PragmaInterruptible {
    this.isDelegated = isDelegated;
    this.isReferenceArray = isReferenceArray;
    this.isAcyclic = isAcyclic;
    this.allocator = allocator;
    this.offsets = offsets;
  }

  /****************************************************************************
   *
   * Scanning and tracing
   */

  /**
   * Return a slot (location of an address) given an object address
   * and a reference number.
   *
   * @param object The address of an object
   * @param reference The number of a field in a scalar or the index
   * into an array
   * @return The address of the relevant slot within the object
   */
  VM_Address getSlot(VM_Address object, int reference) throws VM_PragmaInline {
    if (isReferenceArray)
      return object.add(arrayOffset).add(reference << LOG_BYTES_IN_ADDRESS);
    else
      return object.add(offsets[reference]);
  }

  /**
   * Return the number of references in an object.  In the case of a
   * scalar this is the number of fields, in the case of an array, the
   * number of elements in the array.
   *
   * @param object The object in question
   * @return The number of references in the object
   */
  int getReferences(VM_Address object) throws VM_PragmaInline {
    if (isReferenceArray)
      return VM_Interface.getArrayLength(object);
    else
      return offsets.length;
  }

  /****************************************************************************
   *
   * Statistics
   */

  /**
   * Account for an alloc of this type if profiling is turned on.
   *
   * @param size The number of bytes allocated
   */
  void profileAlloc(int size) throws VM_PragmaInline {
    if (PROFILING_STATISTICS) {
      allocCount++;
      allocBytes += size;
    }
  }

  /**
   * Account for a copy of this type if profiling is turned on.
   *
   * @param size The number of bytes copied. 
   */
  public void profileCopy(int size) throws VM_PragmaInline {
    if (PROFILING_STATISTICS) {
      copyCount++;
      copyBytes += size;
    }
  }

  /**
   * Account for a scan of this type if profiling is turned on.
   *
   * @param size The number of bytes scanned. 
   */
  void profileScan(int size) throws VM_PragmaInline {
    if (PROFILING_STATISTICS) {
      scanCount++;
      scanBytes += size;
    }
  }

  /****************************************************************************
   *
   * Convenience Methods
   */

  /** @return True if scanning is delegated to the VM for this type */
  boolean isDelegated() { return isDelegated; }

  /** @return True if this type is an array of references */
  boolean isReferenceArray() { return isReferenceArray; }

  /** @return True if this type is known to be inherently acyclic */
  boolean isAcyclic() { return isAcyclic; }

  /** @return The allocator to be used by default for this type */
  public int getAllocator() { return allocator; }

}
