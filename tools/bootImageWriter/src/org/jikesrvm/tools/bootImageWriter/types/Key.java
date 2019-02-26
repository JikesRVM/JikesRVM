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

/**
 * Key for looking up fieldInfo
 */
public class Key {
  /**
   * JDK type
   */
  final Class<?> jdkType;

  /**
   * Constructor.
   * @param jdkType the type to associate with the key
   */
  public Key(Class<?> jdkType) {
    this.jdkType = jdkType;
  }

  /**
   * Returns a hash code value for the key.
   * @return a hash code value for this key
   */
  @Override
  public int hashCode() {
    return jdkType.hashCode();
  }

  /**
   * Indicates whether some other key is "equal to" this one.
   * @param that the object with which to compare
   * @return true if this key is the same as the that argument;
   *         false otherwise
   */
  @Override
  public boolean equals(Object that) {
    return (that instanceof Key) && jdkType == ((Key)that).jdkType;
  }
}
