/*
 * (C) Copyright IBM Corp. 2001
 */
//$Id$
package com.ibm.JikesRVM;

/**
 * VM_CodeArray represents a code object (contiguous memory region containing code).
 * The types of the access methods are platform-dependent.
 *
 * @author Perry Cheng
 */

final public class VM_CodeArray implements VM_Uninterruptible {

  //-#if RVM_FOR_IA32
  private byte [] data;
  //-#endif

  //-#if RVM_FOR_POWERPC
  private int [] data;
  //-#endif

  static public VM_CodeArray create (int size) throws VM_PragmaInterruptible {
    if (VM.runningVM) VM._assert(false);  // should be hijacked
    return new VM_CodeArray(size);
  }

  private VM_CodeArray (int size) throws VM_PragmaInterruptible {

    //-#if RVM_FOR_IA32
    data = new byte[size];
    //-#endif
    //-#if RVM_FOR_POWERPC
    data = new int[size];
    //-#endif

    for (int i=0; i<size; i++) 
      data[i] = 0;
  }

  //-#if RVM_FOR_IA32
  public byte get (int index) throws VM_PragmaInline {
  //-#endif
  //-#if RVM_FOR_POWERPC
  public int get (int index) throws VM_PragmaInline {
  //-#endif
    if (VM.runningVM || VM.writingImage) VM._assert(false);  // should be hijacked
    return data[index];
  }

  //-#if RVM_FOR_IA32
  public void set (int index, byte v) throws VM_PragmaInline {
  //-#endif
  //-#if RVM_FOR_POWERPC
  public void set (int index, int v) throws VM_PragmaInline {
  //-#endif
    if (VM.runningVM || VM.writingImage) VM._assert(false);  // should be hijacked
    data[index] = v;
  }

  public int length() throws VM_PragmaInline {
    if (VM.runningVM || VM.writingImage) VM._assert(false);  // should be hijacked
    return data.length;
  }

  public Object getBacking() throws VM_PragmaInline {
    if (!VM.writingImage)
      VM.sysFail("VM_CodeArray.getBacking called when not writing boot image");
    return data;
  }
}
