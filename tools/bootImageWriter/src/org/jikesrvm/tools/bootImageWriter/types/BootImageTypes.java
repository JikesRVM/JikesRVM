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
package org.jikesrvm.tools.bootImageWriter.types;

import java.util.Collection;
import java.util.Hashtable;

import org.jikesrvm.classloader.RVMType;

/**
 * Manages information about the mapping between host JDK and boot image types.
 */
public abstract class BootImageTypes {

  /**
   * Types to be placed into bootimage, stored as key/value pairs
   * where key is a String like "java.lang.Object" or "[Ljava.lang.Object;"
   * and value is the corresponding RVMType.
   */
  public static final Hashtable<String,RVMType> bootImageTypes =
    new Hashtable<String,RVMType>(5000);

  public static void record(String typeName, RVMType type) {
    bootImageTypes.put(typeName, type);
  }

  public static int typeCount() {
    return bootImageTypes.size();
  }

  public static Collection<RVMType> allTypes() {
    return bootImageTypes.values();
  }

  /**
   * Obtains RVM type corresponding to host JDK type.
   *
   * @param jdkType JDK type
   * @return RVM type ({@code null} --> type does not appear in list of classes
   *         comprising bootimage)
   */
  public static RVMType getRvmTypeForHostType(Class<?> jdkType) {
    return bootImageTypes.get(jdkType.getName());
  }

}
