/*
 * (C) Copyright IBM Corp. 2001
 */
//$Id$
package org.vmmagic.unboxed;

import org.vmmagic.pragma.*;
import com.ibm.JikesRVM.VM;

/**
 * The VM front end is not capable of correct handling an array of Address, VM_Word, ....
 * For now, we provide special types to handle these situations.
 *
 * @author Perry Cheng
 */
final public class ExtentArray implements Uninterruptible {
  
  private Extent[] data;

  static public ExtentArray create (int size) throws InterruptiblePragma {
    if (VM.runningVM) VM._assert(false);  // should be hijacked
    return new ExtentArray(size);
  }

  private ExtentArray (int size) throws InterruptiblePragma {
    data = new Extent[size];
    Extent zero = Extent.zero();
    for (int i=0; i<size; i++) {
      data[i] = zero;
    }
  }

  public Extent get (int index) throws InlinePragma {
    if (VM.runningVM || VM.writingImage) VM._assert(false);  // should be hijacked
    return data[index];
  }

  public void set (int index, Extent v) throws InlinePragma {
    if (VM.runningVM || VM.writingImage) VM._assert(false);  // should be hijacked
    data[index] = v;
  }

  public int length() throws InlinePragma {
    if (VM.runningVM || VM.writingImage) VM._assert(false);  // should be hijacked
    return data.length;
  }

  public Object getBacking() throws InlinePragma {
    if (!VM.writingImage)
      VM.sysFail("ExtentArray.getBacking called when not writing boot image");
    return data;
  }
}
