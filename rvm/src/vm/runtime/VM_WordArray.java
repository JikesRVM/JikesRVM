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
final public class VM_WordArray implements VM_Uninterruptible {

  private VM_Word[] data;

  static public VM_WordArray create (int size) throws VM_PragmaInterruptible {
    if (VM.runningVM) VM._assert(false);  // should be hijacked
    return new VM_WordArray(size);
  }

  private VM_WordArray (int size) throws VM_PragmaInterruptible {
    data = new VM_Word[size];
  }

  public VM_Word get (int index) throws VM_PragmaInline {
    if (VM.runningVM || VM.writingImage) VM._assert(false);  // should be hijacked
    return data[index];
  }

  public void set (int index, VM_Word v) throws VM_PragmaInline {
    if (VM.runningVM || VM.writingImage) VM._assert(false);  // should be hijacked
    data[index] = v;
  }

  public int length() throws VM_PragmaInline {
    if (VM.runningVM || VM.writingImage) VM._assert(false);  // should be hijacked
    return data.length;
  }

  public Object getBacking() throws VM_PragmaInline {
    if (!VM.writingImage)
	VM.sysFail("VM_WordArray.getBacking called when not writing boot image");
    return data;
  }
}
