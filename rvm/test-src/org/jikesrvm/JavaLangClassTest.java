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
package org.jikesrvm;

import static org.junit.Assert.assertNull;

import org.jikesrvm.junit.runners.RequiresBuiltJikesRVM;
import org.jikesrvm.junit.runners.VMRequirements;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.junit.runner.RunWith;

/**
 * Jikes RVM replaces {@link java.lang.Class} from the class library with
 * its own version. This test is supposed to make sure that that version is
 * consistent with the official API.
 */
@RunWith(VMRequirements.class)
@Category(RequiresBuiltJikesRVM.class)
public class JavaLangClassTest {

  private static final Class<?>[] EMPTY_ARGS = new Class[0];

  @Test(expected = ClassNotFoundException.class)
  public void forName_String_nullNameLeadsToClassNotFoundException() throws ClassNotFoundException {
    Class.forName(null);
  }

  @Test(expected = ClassNotFoundException.class)
  public void forName_MultipleArgs_nullNameLeadsToClassNotFoundException() throws ClassNotFoundException {
    Class.forName(null, false, this.getClass().getClassLoader());
  }

  @Test(expected = NullPointerException.class)
  public void getMethod_nullNameLeadsToNPE() throws NoSuchMethodException {
    getClass().getMethod(null, EMPTY_ARGS);
  }

  @Test(expected = NoSuchMethodException.class)
  public void getMethod_initMethodCannotBeFound() throws NoSuchMethodException {
    getClass().getMethod("<init>", EMPTY_ARGS);
  }

  @Test(expected = NoSuchMethodException.class)
  public void getMethod_clinitMethodCannotBeFound() throws NoSuchMethodException {
    getClass().getMethod("<clinit>", EMPTY_ARGS);
  }

  @Test(expected = NullPointerException.class)
  public void getField_nullNameLeadsToNPE() throws NoSuchFieldException {
    getClass().getField(null);
  }

  @Test(expected = NullPointerException.class)
  public void getDeclaredField_nullNameLeadsToNPE() throws NoSuchFieldException {
    getClass().getDeclaredField(null);
  }

  @Test(expected = NullPointerException.class)
  public void getDeclaredMethod_nullNameLeadsToNPE() throws NoSuchMethodException {
    getClass().getDeclaredMethod(null);
  }

  @Test(expected = NullPointerException.class)
  public void getResourceAsStream_nullNameLeadsToNPE() throws NoSuchMethodException {
    getClass().getResourceAsStream(null);
  }

  @Test
  public void getResource_returnsNullForNullArgument() throws NoSuchMethodException {
    assertNull(getClass().getResource(null));
  }

  @Test(expected = NullPointerException.class)
  public void getAnnotation_nullAnnotationLeadsToNPE() throws NoSuchMethodException {
    getClass().getAnnotation(null);
  }

  @Test(expected = NullPointerException.class)
  public void isAnnotationPresent_nullAnnotationLeadsToNPE() throws NoSuchMethodException {
    getClass().isAnnotationPresent(null);
  }

  @Test(expected = NullPointerException.class)
  public void isAssignalbleFrom_nullClassLeadsToNPE() throws NoSuchMethodException {
    getClass().isAssignableFrom(null);
  }

}
