/*
 * (C) Copyright ANU, 2004
 */
//$Id$
package org.vmmagic.unboxed;

import org.vmmagic.pragma.*;
import com.ibm.JikesRVM.VM;

/**
 * The VM front end is not capable of correct handling an array 
 * of ObjectReferences ...
 *
 * @author Daniel Frampton
 */
final public class ObjectReferenceArray implements Uninterruptible {

  private ObjectReference[] data;

  static public ObjectReferenceArray create (int size) 
    throws InterruptiblePragma {
    if (VM.runningVM) VM._assert(false);  // should be hijacked
    return new ObjectReferenceArray(size);
  }

  private ObjectReferenceArray(int size) throws InterruptiblePragma {
    data = new ObjectReference[size];
    for (int i=0; i<size; i++) {
      data[i] = ObjectReference.nullReference();
    }
  }

  public ObjectReference get(int index) throws InlinePragma {
    if (VM.runningVM || VM.writingImage) VM._assert(false);  // should be hijacked
    return data[index];
  }

  public void set(int index, ObjectReference v) throws InlinePragma {
    if (VM.runningVM || VM.writingImage) VM._assert(false);  // should be hijacked
    data[index] = v;
  }

  public int length() throws InlinePragma {
    if (VM.runningVM || VM.writingImage) VM._assert(false);  // should be hijacked
    return data.length;
  }

  public Object getBacking() throws InlinePragma {
    if (!VM.writingImage)
        VM.sysFail("ObjectReferenceArray.getBacking called when not writing boot image");
    return data;
  }
}
