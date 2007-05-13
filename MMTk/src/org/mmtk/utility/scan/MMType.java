/*
 * This file is part of MMTk (http://jikesrvm.sourceforge.net).
 * MMTk is distributed under the Common Public License (CPL).
 * A copy of the license is included in the distribution, and is also
 * available at http://www.opensource.org/licenses/cpl1.0.php
 *
 * (C) Copyright Department of Computer Science,
 *     Australian National University. 2003
 */
package org.mmtk.utility.scan;

import org.mmtk.utility.Constants;
import org.mmtk.vm.VM;

import org.vmmagic.unboxed.*;
import org.vmmagic.pragma.*;

/**
 * This class encapsulates type-specific memory management information.
 * 
 */
@Uninterruptible public final class MMType implements Constants {
  // AJG: Maybe should make this immutable. See Item 13 of Effective Java.
  private final boolean isReferenceArray;
  private final boolean isDelegated;
  private final boolean isAcyclic;
  private final int[] offsets;
  private final byte allocator;

  // per-type statistics
  private int allocCount;
  private int allocBytes;
  private int copyCount;
  private int copyBytes;
  private int scanCount;
  private int scanBytes;

  private static final boolean PROFILING_STATISTICS = false;

  /** Used by mmtypes for arrays */
  private static final int [] zeroLengthIntArray = new int [0];
  
  /**
   * Factory methods
   */
  
  /**
   * Create an MMType for a reference array
   */
  @Interruptible
  public static MMType createRefArray(boolean isAcyclic, int allocator) {
    return new MMType(false,true,isAcyclic,allocator,zeroLengthIntArray);
  }
  
  /**
   * Create an MMType for a primitive array
   */
  @Interruptible
  public static MMType createPrimArray(int allocator) {
    return new MMType(false,false,true,allocator,zeroLengthIntArray);
  }
  
  /**
   * Create an MMType for a scalar.
   * 
   * @param isAcyclic
   * @param allocator
   * @param offsets
   * @return
   */
  @Interruptible
  public static MMType createScalar(boolean isAcyclic, int allocator, int[] offsets) {
    return new MMType(false,false,isAcyclic,allocator,offsets);
  }
  
  /**
   * Create an MMType for a delegated type.
   * 
   * @param allocator
   * @return
   */
  @Interruptible
  public static MMType createDelegated(boolean isAcyclic, int allocator) {
    return new MMType(true,false,isAcyclic,allocator,null);
  }
  
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
      boolean isAcyclic, int allocator, int[] offsets) { 
    this.isDelegated = isDelegated;
    this.isReferenceArray = isReferenceArray;
    this.isAcyclic = isAcyclic;
    this.allocator = (byte)allocator;
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
  @Inline
  public Address getSlot(ObjectReference object, int reference) { 
    Address addr = object.toAddress();
    if (isReferenceArray)
      return addr.plus(VM.ARRAY_BASE_OFFSET).plus(reference << LOG_BYTES_IN_ADDRESS);
    else
      return addr.plus(offsets[reference]);
  }

  /**
   * Return the number of references in an object.  In the case of a
   * scalar this is the number of fields, in the case of an array, the
   * number of elements in the array.
   * 
   * @param object The object in question
   * @return The number of references in the object
   */
  @Inline
  public int getReferences(ObjectReference object) { 
    if (isReferenceArray)
      return VM.objectModel.getArrayLength(object);
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
  @Inline
  void profileAlloc(int size) { 
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
  @Inline
  public void profileCopy(int size) { 
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
  @Inline
  void profileScan(int size) { 
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
  public boolean isReferenceArray() { return isReferenceArray; }

  /** @return True if this type is known to be inherently acyclic */
  public boolean isAcyclic() { return isAcyclic; }

  /** @return The allocator to be used by default for this type */
  public int getAllocator() { return allocator; }

}
