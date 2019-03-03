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

public class AbstractFieldFailure {

  protected String typeName;
  protected String rvmFieldName;

  protected AbstractFieldFailure(Class<?> jdkType, RVMField rvmField) {
    if (jdkType == null) {
      this.typeName = rvmField.getDeclaringClass().toString();
    } else {
      this.typeName = jdkType.getName();
    }
    this.rvmFieldName = rvmField.getName().toString();
  }

  @Override
  public int hashCode() {
    final int prime = 31;
    int result = 1;
    result = prime * result + ((typeName == null) ? 0 :
      typeName.hashCode());
    result = prime * result + ((rvmFieldName == null) ? 0 :
      rvmFieldName.hashCode());
    return result;
  }

  @Override
  public boolean equals(Object obj) {
    if (this == obj)
      return true;
    if (obj == null)
      return false;
    if (getClass() != obj.getClass())
      return false;
    AbstractFieldFailure other = (AbstractFieldFailure) obj;
    if (typeName == null) {
      if (other.typeName != null)
        return false;
    } else if (!typeName.equals(other.typeName))
      return false;
    if (rvmFieldName == null) {
      if (other.rvmFieldName != null)
        return false;
    } else if (!rvmFieldName.equals(other.rvmFieldName))
      return false;
    return true;
  }

}
