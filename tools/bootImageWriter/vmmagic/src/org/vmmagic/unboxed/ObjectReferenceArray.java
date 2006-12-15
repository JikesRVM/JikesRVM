/*
 * This file is part of Jikes RVM (http://jikesrvm.sourceforge.net).
 * The Jikes RVM project is distributed under the Common Public License (CPL).
 * A copy of the license is included in the distribution, and is also
 * available at http://www.opensource.org/licenses/cpl1.0.php
 *
 * (C) Copyright ANU, 2004
 */
//$Id$
package org.vmmagic.unboxed;

import org.vmmagic.pragma.*;
import com.ibm.jikesrvm.VM;

/**
 * The VM front end is not capable of correct handling an array 
 * of ObjectReferences ...
 *
 * @author Daniel Frampton
 */
@Uninterruptible final public class ObjectReferenceArray {

  private ObjectReference[] data;

  @Interruptible
  static public ObjectReferenceArray create (int size) { 
    if (VM.runningVM) VM._assert(false);  // should be hijacked
    return new ObjectReferenceArray(size);
  }

  @Interruptible
  private ObjectReferenceArray(int size) { 
    data = new ObjectReference[size];
    for (int i=0; i<size; i++) {
      data[i] = ObjectReference.nullReference();
    }
  }

  @Inline
  public ObjectReference get(int index) { 
    if (VM.runningVM || VM.writingImage) VM._assert(false);  // should be hijacked
    return data[index];
  }

  @Inline
  public void set(int index, ObjectReference v) { 
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
        VM.sysFail("ObjectReferenceArray.getBacking called when not writing boot image");
    return data;
  }
}
