/*
 * (C) Copyright IBM Corp. 2001
 */
//$Id$

/**
 * Trivial heap for temp use by RCGC
 */
final class VM_RCHeap extends VM_Heap
  implements VM_Uninterruptible {

  VM_RCHeap(String s) {
    super(s);
  }

  protected VM_Address allocateZeroedMemory(int size) {
    VM.sysFail("allocateZeroedMemory on VM_RCHeap not implemented");
    return VM_Address.zero();
  }

  protected void postAllocationProcessing(Object newObj) { 
  }


}
