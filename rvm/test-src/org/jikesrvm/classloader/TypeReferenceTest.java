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
package org.jikesrvm.classloader;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.*;
import static org.junit.Assert.*;

import org.jikesrvm.junit.runners.RequiresBuiltJikesRVM;
import org.jikesrvm.junit.runners.VMRequirements;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.junit.runner.RunWith;

@RunWith(VMRequirements.class)
@Category(RequiresBuiltJikesRVM.class)
public class TypeReferenceTest {

  @Test
  public void mappingOfPrimitiveClassNameToTypeReferenceIsNullForNonPrimitives() {
    TypeReference result = TypeReference.mapPrimitiveClassNameToTypeReference("Class");
    assertThat(result, is(nullValue()));
  }

  @Test
  public void mappingOfPrimitiveClassNameToTypeReferenceIsNullForNull() {
    TypeReference result = TypeReference.mapPrimitiveClassNameToTypeReference(null);
    assertThat(result, is(nullValue()));
  }

  @Test
  public void mappingOfPrimitiveClassNameIsCorrectForVoid() {
    TypeReference result = TypeReference.mapPrimitiveClassNameToTypeReference("void");
    assertThat(result, is(TypeReference.Void));
  }

  @Test
  public void mappingIsCaseSensitive() {
    TypeReference result = TypeReference.mapPrimitiveClassNameToTypeReference("Void");
    assertThat(result, is(nullValue()));
  }

  @Test
  public void mappingOfPrimitiveClassNameIsCorrectForBoolean() {
    TypeReference result = TypeReference.mapPrimitiveClassNameToTypeReference("boolean");
    assertThat(result, is(TypeReference.Boolean));
  }

  @Test
  public void mappingOfPrimitiveClassNameIsCorrectForByte() {
    TypeReference result = TypeReference.mapPrimitiveClassNameToTypeReference("byte");
    assertThat(result, is(TypeReference.Byte));
  }

  @Test
  public void mappingOfPrimitiveClassNameIsCorrectForChar() {
    TypeReference result = TypeReference.mapPrimitiveClassNameToTypeReference("char");
    assertThat(result, is(TypeReference.Char));
  }

  @Test
  public void mappingOfPrimitiveClassNameIsCorrectForDouble() {
    TypeReference result = TypeReference.mapPrimitiveClassNameToTypeReference("double");
    assertThat(result, is(TypeReference.Double));
  }

  @Test
  public void mappingOfPrimitiveClassNameIsCorrectForFloat() {
    TypeReference result = TypeReference.mapPrimitiveClassNameToTypeReference("float");
    assertThat(result, is(TypeReference.Float));
  }

  @Test
  public void mappingOfPrimitiveClassNameIsCorrectForInt() {
    TypeReference result = TypeReference.mapPrimitiveClassNameToTypeReference("int");
    assertThat(result, is(TypeReference.Int));
  }

  @Test
  public void mappingOfPrimitiveClassNameIsCorrectForLong() {
    TypeReference result = TypeReference.mapPrimitiveClassNameToTypeReference("long");
    assertThat(result, is(TypeReference.Long));
  }

  @Test
  public void mappingOfPrimitiveClassNameIsCorrectForShort() {
    TypeReference result = TypeReference.mapPrimitiveClassNameToTypeReference("short");
    assertThat(result, is(TypeReference.Short));
  }

}
