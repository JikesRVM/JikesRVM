/*
 *  This file is part of the Jikes RVM project (http://jikesrvm.org).
 *
 *  This file is licensed to You under the Eclipse Public License (EPL);
 *  You may not use this file except in compliance with the License. You
 *  may obtain a copy of the License at
 *
 *      http://www.opensource.org/licenses/eclipse-1.0.php
 *
 *  See the COPYRIGHT.txt file distributed with this work for information
 *  regarding copyright ownership.
 */
package org.jikesrvm.arm;

import static org.jikesrvm.runtime.JavaSizeConstants.BYTES_IN_INT;

import org.jikesrvm.VM;
import org.jikesrvm.classloader.RVMArray;
import org.jikesrvm.classloader.TypeReference;
import org.jikesrvm.runtime.RuntimeEntrypoints;
import org.vmmagic.pragma.Entrypoint;
import org.vmmagic.unboxed.Address;

/**
 * Helper routine to pull the parameters to multianewarray off the
 * Java expression stack maintained by the baseline compiler and
 * pass them to RuntimeEntrypoints.buildMultiDimensionalArray.<p>
 *
 * TODO: There is only 1 line of platform dependent code here; refactor?
 */
public abstract class MultianewarrayHelper {

  /**
   * Allocate something like {@code new Foo[cnt0][cnt1]...[cntN-1]},
   *                      or {@code new int[cnt0][cnt1]...[cntN-1]}.
   * @param methodId      id of caller
   * @param numDimensions number of array dimensions
   * @param typeId        {@link TypeReference} id of type of array
   * @param sp            Pointer to the "count n" (last) argument to the multianewarray bytecode
   *                      This is used to access the number of elements to
   *                      be allocated for each dimension.
   * See also: bytecode 0xc5 ("multianewarray") in Compiler
   * @return the new array
   */
  @Entrypoint
  static Object newArrayArray(int methodId, int numDimensions, int typeId, int sp)
      throws NoClassDefFoundError, NegativeArraySizeException, OutOfMemoryError {
    Address stackPointer = Address.fromIntZeroExtend(sp);
    if (numDimensions == 2) {
      int dim0, dim1;
      // fetch number of elements to be allocated for each array dimension
      VM.disableGC();
      dim1 = stackPointer.loadInt();
      dim0 = stackPointer.plus(BYTES_IN_INT).loadInt();
      VM.enableGC();
      // validate arguments
      if ((dim0 < 0) || (dim1 < 0)) throw new NegativeArraySizeException();
      // create array
      TypeReference tRef = TypeReference.getTypeRef(typeId);
      RVMArray array = tRef.resolve().asArray();
      return RuntimeEntrypoints.buildTwoDimensionalArray(methodId, dim0, dim1, array);
    } else {
      // fetch number of elements to be allocated for each array dimension
      int[] numElements = new int[numDimensions];
      VM.disableGC();
      for (int i = 0; i < numDimensions; i++) {
        int offset = (numDimensions - i - 1) * BYTES_IN_INT;
        numElements[i] = stackPointer.plus(offset).loadInt();
      }
      VM.enableGC();
      // validate arguments
      for (int elements : numElements) {
        if (elements < 0) throw new NegativeArraySizeException();
      }
      // create array
      TypeReference tRef = TypeReference.getTypeRef(typeId);
      RVMArray array = tRef.resolve().asArray();
      return RuntimeEntrypoints.buildMultiDimensionalArray(methodId, numElements, array);
    }
  }
}

