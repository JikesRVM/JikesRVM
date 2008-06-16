/*
 *  This file is part of the Jikes RVM project (http://jikesrvm.org).
 *
 *  This file is licensed to You under the Common Public License (CPL);
 *  You may not use this file except in compliance with the License. You
 *  may obtain a copy of the License at
 *
 *      http://www.opensource.org/licenses/cpl1.0.php
 *
 *  See the COPYRIGHT.txt file distributed with this work for information
 *  regarding copyright ownership.
 */
package org.jikesrvm.ppc;

import org.jikesrvm.VM;
import org.jikesrvm.VM_Constants;
import org.jikesrvm.classloader.RVMArray;
import org.jikesrvm.classloader.VM_TypeReference;
import org.jikesrvm.runtime.VM_Magic;
import org.jikesrvm.runtime.RuntimeEntrypoints;
import org.vmmagic.unboxed.Address;

/**
 * Helper routine to pull the parameters to multianewarray off the
 * Java expression stack maintained by the baseline compiler and
 * pass them to RuntimeEntrypoints.buildMultiDimensionalArray.
 *
 * TODO: There is only 1 line of platform dependent code here; refactor?
 */
public abstract class VM_MultianewarrayHelper implements VM_Constants {

  /**
   * Allocate something like "new Foo[cnt0][cnt1]...[cntN-1]",
   *                      or "new int[cnt0][cnt1]...[cntN-1]".
   * @param methodId      id of caller
   * @param numDimensions number of array dimensions
   * @param id            {@link VM_TypeReference} id of type of array
   * @param argOffset     position of word *above* `cnt0' argument within caller's frame
   *                      This is used to access the number of elements to
   *                      be allocated for each dimension.
   * See also: bytecode 0xc5 ("multianewarray") in VM_Compiler
   */
  static Object newArrayArray(int methodId, int numDimensions, int typeId, int argOffset)
      throws NoClassDefFoundError, NegativeArraySizeException, OutOfMemoryError {
    if (numDimensions == 2) {
      int dim0, dim1;
      // fetch number of elements to be allocated for each array dimension
      VM.disableGC();
      Address argp = VM_Magic.getFramePointer().loadAddress().plus(argOffset);
      int offset = (VM_StackframeLayoutConstants.BYTES_IN_STACKSLOT * 0) + BYTES_IN_INT;
      dim0 = argp.minus(offset).loadInt();
      offset = (VM_StackframeLayoutConstants.BYTES_IN_STACKSLOT * 1) + BYTES_IN_INT;
      dim1 = argp.minus(offset).loadInt();
      VM.enableGC();
      // validate arguments
      if ((dim0 < 0) || (dim1 < 0)) throw new NegativeArraySizeException();
      // create array
      VM_TypeReference tRef = VM_TypeReference.getTypeRef(typeId);
      RVMArray array = tRef.resolve().asArray();
      return RuntimeEntrypoints.buildTwoDimensionalArray(methodId, dim0, dim1, array);
    } else {
      // fetch number of elements to be allocated for each array dimension
      int[] numElements = new int[numDimensions];
      VM.disableGC();
      Address argp = VM_Magic.getFramePointer().loadAddress().plus(argOffset);
      for (int i = 0; i < numDimensions; ++i) {
        int offset = (VM_StackframeLayoutConstants.BYTES_IN_STACKSLOT * i) + BYTES_IN_INT;
        numElements[i] = argp.minus(offset).loadInt();
      }
      VM.enableGC();
      // validate arguments
      for (int elements : numElements) {
        if (elements < 0) throw new NegativeArraySizeException();
      }
      // create array
      VM_TypeReference tRef = VM_TypeReference.getTypeRef(typeId);
      RVMArray array = tRef.resolve().asArray();
      return RuntimeEntrypoints.buildMultiDimensionalArray(methodId, numElements, array);
    }
  }
}

