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
import org.jikesrvm.VM_SizeConstants;

/**
 * The object reference type is used by the runtime system and collector to 
 * represent a type that holds a reference to a single object. 
 * We use a separate type instead of the Java Object type for coding clarity,
 * to make a clear distinction between objects the VM is written in, and 
 * objects that the VM is managing. No operations that can not be completed in
 * pure Java should be allowed on Object.
 * 
 * @author Daniel Frampton
 */
@Uninterruptible public final class ObjectReference extends ArchitecturalWord implements VM_SizeConstants {
  ObjectReference(int value) {
    super(value, true);
  }
  ObjectReference(long value) {
    super(value);
  }
  
  /**
   * Convert from an object to a reference.
   * @param obj The object 
   * @return The corresponding reference
   */
  public static ObjectReference fromObject(Object obj) {
    return null;
  }

  /**
   * Return a null reference
   */
  @Inline
  public static final ObjectReference nullReference() { 
    return null;
  }

  /**
   * Get a heap address for the object. 
   */
  public Address toAddress() {
    return null;
  }

  /**
   * Is this a null reference?
   */
  public boolean isNull() {
    return false;
  }
}
