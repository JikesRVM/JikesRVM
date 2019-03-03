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

import org.jikesrvm.classloader.RVMField;

public class MissingField extends AbstractFieldFailure {

  private boolean isStatic;

  public MissingField(Class<?> jdkType, RVMField rvmField, boolean isStatic) {
    super(jdkType, rvmField);
    this.isStatic = isStatic;
  }

  @Override
  public int hashCode() {
    final int prime = 31;
    int result = super.hashCode();
    result = prime * result + (isStatic ? 1231 : 1237);
    return result;
  }

  @Override
  public boolean equals(Object obj) {
    if (this == obj)
      return true;
    if (!super.equals(obj))
      return false;
    if (getClass() != obj.getClass())
      return false;
    MissingField other = (MissingField) obj;
    if (isStatic != other.isStatic)
      return false;
    return true;
  }

  @Override
  public String toString() {
    return "Missing field: " + typeName + " " + rvmFieldName;
  }

}
