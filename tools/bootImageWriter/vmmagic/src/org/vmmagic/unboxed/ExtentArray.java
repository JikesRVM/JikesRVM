/*
 *  This file is part of the Jikes RVM project (http://jikesrvm.org).
 *
 *  This file is licensed to You under the Common Public License (CPL);
 *  You may not use this file except in compliance with the License. You
 *  may obtain a copy of the License at
 *
 *      http://www.opensource.org/licenses/cpl1.0.php
 *
 *  See the COPYRIGHT.txt file distributed with this work for information
 *  regarding copyright ownership.
 */
package org.vmmagic.unboxed;

import org.vmmagic.pragma.*;
import org.jikesrvm.VM;

/**
 * The VM front end is not capable of correct handling an array of Address, VM_Word, ....
 * For now, we provide special types to handle these situations.
 *
 * @author Perry Cheng
 */
@Uninterruptible final public class ExtentArray {
  
  private Extent[] data;

  @Interruptible
  static public ExtentArray create (int size) { 
    if (VM.runningVM) VM._assert(false);  // should be hijacked
    return new ExtentArray(size);
  }

  private ExtentArray (int size) { 
    data = new Extent[size];
    Extent zero = Extent.zero();
    for (int i=0; i<size; i++) {
      data[i] = zero;
    }
  }

  @Inline
  public Extent get (int index) { 
    if (VM.runningVM || VM.writingImage) VM._assert(false);  // should be hijacked
    return data[index];
  }

  @Inline
  public void set (int index, Extent v) { 
    if (VM.runningVM || VM.writingImage) VM._assert(false);  // should be hijacked
    data[index] = v;
  }

  @Inline
  public int length() { 
    if (VM.runningVM || VM.writingImage) VM._assert(false);  // should be hijacked
    return data.length;
  }

  @Inline
  public Object getBacking() { 
    if (!VM.writingImage)
      VM.sysFail("ExtentArray.getBacking called when not writing boot image");
    return data;
  }
}
