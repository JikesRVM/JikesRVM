/*
 * (C) Copyright IBM Corp. 2001
 */
// $Id$
package com.ibm.JikesRVM;

import com.ibm.JikesRVM.classloader.*;

/**
 * Helper routine to pull the parameters to multianewarray off the
 * Java expression stack maintained by the baseline compiler and 
 * pass them to VM_Runtime.buildMultiDimensionalArray.
 * 
 * TODO: There is only 1 line of platform dependent code here; refactor?
 *
 * @author Bowen Alpern
 * @author Tony Cocchi 
 * @author Derek Lieber
 */
public class VM_MultianewarrayHelper implements VM_Constants {

  /**
   * Allocate something like "new Foo[cnt0][cnt1]...[cntN-1]",
   *                      or "new int[cnt0][cnt1]...[cntN-1]".
   * @param methodId      id of caller
   * @param numDimensions number of array dimensions
   * @param typeId        type referencd id of type of array
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
    VM_Address argp = VM_Magic.getMemoryAddress(VM_Magic.getFramePointer()).add(argOffset);
    for (int i = 0; i < numDimensions; ++i) {
      int offset = (BYTES_IN_STACKSLOT * i) + BYTES_IN_INT;
      numElements[i] = VM_Magic.getMemoryInt(argp.sub(offset));
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

