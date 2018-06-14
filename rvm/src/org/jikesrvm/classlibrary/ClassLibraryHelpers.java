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

import org.jikesrvm.VM;
import org.jikesrvm.classloader.RVMClass;
import org.jikesrvm.classloader.RVMMethod;
import org.jikesrvm.runtime.Reflection;
import org.jikesrvm.runtime.RuntimeEntrypoints;

// TODO decide where to put this code before merging the OpenJDK branch
public class ClassLibraryHelpers {

  /**
   * Allocates an object of the given class and runs the no-arg constructor
   * (even if that constructor is private).
   *
   * @param clazz
   *          clazz to be instantiated
   * @return an object of the given class
   */
  @SuppressWarnings("unchecked")
  public static <T> T allocateObjectForClassAndRunNoArgConstructor(
      Class<T> clazz) {
    RVMClass rvmClass = JikesRVMSupport.getTypeForClass(clazz).asClass();
    RVMMethod[] constructors = rvmClass.getConstructorMethods();
    RVMMethod noArgConst = null;
    for (RVMMethod constructor : constructors) {
      if (constructor.getParameterTypes().length == 0) {
        noArgConst = constructor;
        break;
      }
    }
    if (VM.VerifyAssertions)
      VM._assert(noArgConst != null, "didn't find any no-arg constructor");
    T systemThreadGroup = (T) RuntimeEntrypoints.resolvedNewScalar(rvmClass);
    Reflection.invoke(noArgConst, null, systemThreadGroup, null, true);
    return systemThreadGroup;
  }

}
