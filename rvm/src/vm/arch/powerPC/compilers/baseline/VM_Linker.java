/*
 * (C) Copyright IBM Corp. 2001
 */
// $Id$

/**
 * Perform dynamic linking as call sites and load/store sites are encountered 
 * at execution time.
 *
 * @author Bowen Alpern
 * @author Tony Cocchi 
 * @author Derek Lieber
 * @date 13 Apr 1999 
 */
class VM_Linker implements VM_BaselineConstants {

  // Handle dynamic linking for a VM_Method 
  // given: the id of the method
  //
  static void resolveMethod (int methodId) throws VM_ResolutionException {
    VM_Method target = VM_MethodDictionary.getValue(methodId);
    VM_TableBasedDynamicLinker.resolveMember(target);
  }
      
  // Handle dynamic linking for a VM_Field 
  // given: the id of the field
  //
  static void resolveField (int fieldId) throws VM_ResolutionException {
    VM_Field target = VM_FieldDictionary.getValue(fieldId);
    VM_TableBasedDynamicLinker.resolveMember(target);
  }
      
  // Allocate something like "new Foo[cnt0][cnt1]...[cntN-1]",
  //                      or "new int[cnt0][cnt1]...[cntN-1]".
  // Taken:    number of array dimensions
  //           type of array (VM_TypeDictionary id)
  //           position of word *above* `cnt0' argument within caller's frame
  //           number of elements to allocate for each dimension (undeclared, passed on stack following "dictionaryId" argument)
  // See also: bytecode 0xc5 ("multianewarray")
  //
  // TODO: is this really architecture specific? --dave
  static Object newArrayArray(int numDimensions, int dictionaryId, int argOffset /*, cntN-1, ..., cnt1, cnt0 */)
    throws VM_ResolutionException, NegativeArraySizeException, OutOfMemoryError {
    VM_Magic.pragmaNoInline();

    // fetch number of elements to be allocated for each array dimension
    //
    int[] numElements = new int[numDimensions];
    VM.disableGC();
    int argp = VM_Magic.getMemoryWord(VM_Magic.getFramePointer()) + argOffset;
    for (int i = 0; i < numDimensions; ++i)
      numElements[i] = VM_Magic.getMemoryWord(argp -= 4);
    VM.enableGC();

    // validate arguments
    //
    for (int i = 0; i < numDimensions; ++i)
      if (numElements[i] < 0)
	throw new NegativeArraySizeException();

    // create array
    //
    return VM_Runtime.buildMultiDimensionalArray(numElements, 0, VM_TypeDictionary.getValue(dictionaryId).asArray());
  }
}

