/*
 * (C) Copyright IBM Corp. 2001
 */
//$Id$
package org.vmmagic.unboxed;

import org.vmmagic.pragma.*;
import com.ibm.JikesRVM.VM;

/**
 * The VM front end is not capable of correct handling an array of Address, Word, ....
 * For now, we provide special types to handle these situations.
 *
 * @author Perry Cheng
 */
final public class OffsetArray implements Uninterruptible {

  private Offset[] data;

  static public OffsetArray create (int size) throws InterruptiblePragma {
    if (VM.runningVM) VM._assert(false);  // should be hijacked
    return new OffsetArray(size);
  }

  private OffsetArray (int size) throws InterruptiblePragma {
    data = new Offset[size];
    Offset zero = Offset.zero();
    for (int i=0; i<size; i++) {
      data[i] = zero;
    }
  }

  public Offset get (int index) throws InlinePragma { 
    if (VM.runningVM || VM.writingImage) VM._assert(false);  // should be hijacked
    return data[index];
  }

  public void set (int index, Offset v) throws InlinePragma {
    if (VM.runningVM || VM.writingImage) VM._assert(false);  // should be hijacked
    data[index] = v;
  }

  public int length() throws InlinePragma {
    if (VM.runningVM || VM.writingImage) VM._assert(false);  // should be hijacked
    return data.length;
  }

  public Object getBacking() throws InlinePragma {
    if (!VM.writingImage)
      VM.sysFail("VM_OffsetArray.getBacking called when not writing boot image");
    return data;
  }

}
