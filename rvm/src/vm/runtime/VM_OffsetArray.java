/*
 * (C) Copyright IBM Corp. 2001
 */
//$Id$
package com.ibm.JikesRVM;

/**
 * The VM front end is not capable of correct handling an array of VM_Address, VM_Word, ....
 * For now, we provide special types to handle these situations.
 *
 * @author Perry Cheng
 */
final public class VM_OffsetArray implements VM_Uninterruptible {

  private VM_Offset[] data;

  static public VM_OffsetArray create (int size) throws VM_PragmaInterruptible {
    if (VM.runningVM) VM._assert(false);  // should be hijacked
    return new VM_OffsetArray(size);
  }

  private VM_OffsetArray (int size) throws VM_PragmaInterruptible {
    data = new VM_Offset[size];
  }

  public VM_Offset get (int index) throws VM_PragmaInline {
    if (VM.runningVM || VM.writingImage) VM._assert(false);  // should be hijacked
    return data[index];
  }

  public void set (int index, VM_Offset v) throws VM_PragmaInline {
    if (VM.runningVM || VM.writingImage) VM._assert(false);  // should be hijacked
    data[index] = v;
  }

  public int length() throws VM_PragmaInline {
    if (VM.runningVM || VM.writingImage) VM._assert(false);  // should be hijacked
    return data.length;
  }

  public Object getBacking() throws VM_PragmaInline {
    if (!VM.writingImage)
      VM.sysFail("VM_OffsetArray.getBacking called when not writing boot image");
    return data;
  }

}
