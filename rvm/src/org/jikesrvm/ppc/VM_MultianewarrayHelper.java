/*
 * This file is part of Jikes RVM (http://jikesrvm.sourceforge.net).
 * The Jikes RVM project is distributed under the Common Public License (CPL).
 * A copy of the license is included in the distribution, and is also
 * available at http://www.opensource.org/licenses/cpl1.0.php
 *
 * (C) Copyright IBM Corp. 2001
 */
package org.jikesrvm.ppc;

import org.jikesrvm.VM;
import org.jikesrvm.VM_Constants;
import org.jikesrvm.classloader.VM_Array;
import org.jikesrvm.classloader.VM_TypeReference;
import org.jikesrvm.runtime.VM_Magic;
import org.jikesrvm.runtime.VM_Runtime;
import org.vmmagic.unboxed.Address;

/**
 * Helper routine to pull the parameters to multianewarray off the
 * Java expression stack maintained by the baseline compiler and 
 * pass them to VM_Runtime.buildMultiDimensionalArray.
 * 
 * TODO: There is only 1 line of platform dependent code here; refactor?
 *
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
  static Object newArrayArray(int methodId, int numDimensions, int id, int argOffset)
    throws NegativeArraySizeException, 
           OutOfMemoryError {
    // fetch number of elements to be allocated for each array dimension
    //
    int[] numElements = new int[numDimensions];
    VM.disableGC();
    Address argp = VM_Magic.getFramePointer().loadAddress().plus(argOffset);
    for (int i = 0; i < numDimensions; ++i) {
      int offset = (VM_StackframeLayoutConstants.BYTES_IN_STACKSLOT * i) + BYTES_IN_INT;
      numElements[i] = argp.minus(offset).loadInt();
    }
    VM.enableGC();

    // validate arguments
    //
    for (int i = 0; i < numDimensions; ++i)
      if (numElements[i] < 0) throw new NegativeArraySizeException();

    // create array
    //
    VM_TypeReference tRef = VM_TypeReference.getTypeRef(id);
    VM_Array array = tRef.resolve().asArray();
    return VM_Runtime.buildMultiDimensionalArray(methodId, numElements, array);
  }
}

