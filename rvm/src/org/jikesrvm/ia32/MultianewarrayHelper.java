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
package org.jikesrvm.ia32;

import org.jikesrvm.VM;
import org.jikesrvm.classloader.RVMArray;
import org.jikesrvm.classloader.TypeReference;
import org.jikesrvm.runtime.Magic;
import org.jikesrvm.runtime.RuntimeEntrypoints;
import org.vmmagic.unboxed.Address;

import static org.jikesrvm.SizeConstants.BYTES_IN_WORD;

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
   * @param methodId method id of caller
   * @param numDimensions number of array dimensions
   * @param typeId type id of type reference for array
   * @param argOffset position of word *above* `cnt0' argument within
   * caller's frame This is used to access the number of elements to
   * be allocated for each dimension.
   *
   * See also: bytecode 0xc5 ("multianewarray") in BaselineCompilerImpl
   */
  static Object newArrayArray(int methodId, int numDimensions, int typeId, int argOffset)
      throws NoClassDefFoundError, NegativeArraySizeException, OutOfMemoryError {
    if (numDimensions == 2) {
      int dim0, dim1;
      // fetch number of elements to be allocated for each array dimension
      VM.disableGC();
      Address argp = Magic.getFramePointer().plus(argOffset);
      argp = argp.minus(BYTES_IN_WORD);
      dim0 = argp.loadInt();
      argp = argp.minus(BYTES_IN_WORD);
      dim1 = argp.loadInt();
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
      Address argp = Magic.getFramePointer().plus(argOffset);
      for (int i = 0; i < numDimensions; ++i) {
        argp = argp.minus(BYTES_IN_WORD);
        numElements[i] = argp.loadInt();
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
