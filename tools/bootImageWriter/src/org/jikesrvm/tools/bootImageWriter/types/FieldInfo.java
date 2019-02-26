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

import java.lang.reflect.Field;

import org.jikesrvm.classloader.RVMType;

/**
 * Class to collecting together field information
 */
public class FieldInfo {
  /**
   *  Field table from JDK verion of class
   */
  public final Field[]  jdkFields;

  /**
   *  Fields that are the one-to-one match of rvm instanceFields
   *  includes superclasses
   */
  public Field[]  jdkInstanceFields;

  /**
   *  Fields that are the one-to-one match of RVM staticFields
   */
  public Field[]  jdkStaticFields;

  /**
   *  RVM type associated with this Field info
   */
  public RVMType rvmType;

  /**
   *  JDK type associated with this Field info
   */
  public final Class<?> jdkType;

  /**
   * Constructor.
   * @param jdkType the type to associate with the key
   */
  public FieldInfo(Class<?> jdkType, RVMType rvmType) {
    this.jdkFields = jdkType.getDeclaredFields();
    this.jdkType = jdkType;
    this.rvmType = rvmType;
  }
}
