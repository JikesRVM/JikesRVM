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

  //-#ifdef BYTE_SIZED_INSTRUCTION
  private byte [] data;
  //-#endif

  //-#ifdef INT_SIZED_INSTRUCTION
  private int [] data;
  //-#endif

  static public VM_CodeArray create (int size) throws VM_PragmaInterruptible {
    if (VM.runningVM) VM._assert(false);  // should be hijacked
    return new VM_CodeArray(size);
  }

  private VM_CodeArray (int size) throws VM_PragmaInterruptible {

    //-#ifdef BYTE_SIZED_INSTRUCTION
    data = new byte[size];
    //-#endif
    //-#ifdef INT_SIZED_INSTRUCTION
    data = new int[size];
    //-#endif

    for (int i=0; i<size; i++) 
      data[i] = 0;
  }

  //-#ifdef BYTE_SIZED_INSTRUCTION
  public byte get (int index) throws VM_PragmaInline {
  //-#endif
  //-#ifdef INT_SIZED_INSTRUCTION
  public int get (int index) throws VM_PragmaInline {
  //-#endif
    if (VM.runningVM || VM.writingImage) VM._assert(false);  // should be hijacked
    return data[index];
  }

  //-#ifdef BYTE_SIZED_INSTRUCTION
  public void set (int index, byte v) throws VM_PragmaInline {
  //-#endif
  //-#ifdef INT_SIZED_INSTRUCTION
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
