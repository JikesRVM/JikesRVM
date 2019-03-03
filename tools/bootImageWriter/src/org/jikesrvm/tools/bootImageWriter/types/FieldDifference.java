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

public enum FieldDifference {

  STATIC_IN_CLASS_LIB_BUT_NOT_STATIC_IN_HOST_JDK, STATIC_FIELD_HAS_DIFFERENT_TYPE_IN_HOST_JDK;

  @Override
  public String toString() {
    if (this == FieldDifference.STATIC_IN_CLASS_LIB_BUT_NOT_STATIC_IN_HOST_JDK) {
      return "static in class library but not static in host JDK";
    } else if (this == STATIC_FIELD_HAS_DIFFERENT_TYPE_IN_HOST_JDK) {
      return "static field in class library has different type in host JDK";
    }
    return super.toString();
  }

}
