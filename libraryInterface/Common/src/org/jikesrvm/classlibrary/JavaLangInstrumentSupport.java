/*
 *  This file is part of the Jikes RVM project (http://jikesrvm.org).
 *
 *  This file is licensed to You under the Eclipse Public License (EPL);
 *  You may not use this file except in compliance with the License. You
 *  may obtain a copy of the License at
 *
 *      http://www.opensource.org/licenses/eclipse-1.0.php
 *
 *  See the COPYRIGHT.txt file distributed with this work for information
 *  regarding copyright ownership.
 */
package org.jikesrvm.classlibrary;

import org.jikesrvm.classloader.RVMArray;
import org.jikesrvm.classloader.RVMClass;
import org.jikesrvm.classloader.RVMType;

/**
 * Common utilities for Jikes RVM implementations of the java.lang.instrument API
 */
public final class JavaLangInstrumentSupport {

  public static long getObjectSize(Object objectToSize) {
    Class<?> cl = objectToSize.getClass();
    RVMType vmType = java.lang.JikesRVMSupport.getTypeForClass(cl);
    if (cl.isArray()) {
      RVMArray vmArray = (RVMArray)vmType;
      int nelements = java.lang.reflect.Array.getLength(objectToSize);
      return vmArray.getInstanceSize(nelements);
    } else {
      RVMClass vmClass = (RVMClass)vmType;
      return vmClass.getInstanceSize();
    }
  }

}
