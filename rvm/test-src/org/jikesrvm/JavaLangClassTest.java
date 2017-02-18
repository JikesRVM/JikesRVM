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

import static org.junit.Assert.*;

import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.HashSet;

import org.jikesrvm.junit.runners.VMRequirements;
import org.junit.Test;
import org.junit.runner.RunWith;

/**
 * Jikes RVM replaces {@link java.lang.Class} from the class library with
 * its own version. This test is supposed to make sure that that version is
 * consistent with the official API.
 */
@RunWith(VMRequirements.class)
public class JavaLangClassTest {

  private static final Class<?>[] EMPTY_ARGS = new Class[0];

  @Test(expected = NullPointerException.class)
  public void forName_String_nullNameLeadsToNPE() throws ClassNotFoundException {
    Class.forName(null);
  }

  @Test(expected = NullPointerException.class)
  public void forName_MultipleArgs_nullNameLeadsToNPE() throws ClassNotFoundException {
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

  @Test(expected = NullPointerException.class)
  public void getResource_nullArgumentLeadsToNPE() throws NoSuchMethodException {
    getClass().getResource(null);
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

  @Test
  public void getMethodsForAbstractClassesDoesntReturnDuplicateImplementationmethods() throws NoSuchMethodException {
    Method[] methods = AbstractSubClass.class.getMethods();
    Method m = AbstractSubClass.class.getMethod("m");
    String methodName = m.getName();
    Class<?>[] methodParameters = m.getParameterTypes();
    HashSet<Method> foundMethods = new HashSet<Method>();
    for (Method method : methods) {
      if (methodName.equals(method.getName()) &&
      Arrays.equals(methodParameters, method.getParameterTypes())) {
        foundMethods.add(method);
      }
    }
    assertTrue(foundMethods.toString(), foundMethods.size() == 1);
  }

  private interface M1 {
    void m();
  }

  private interface M2 {
    void m();
  }

  private abstract static class AbstractSuperclass implements M2, M1 {
    @Override
    public void m() {
    }
  }

  private abstract static class AbstractSubClass extends AbstractSuperclass implements M2, M1 {
  }

  @Test
  public void getEnclosingMethodReturnsNullForLocalClassInType() throws NoSuchMethodException {
    class OuterLocalClass {
      class InnerLocalClass {}

      void getEnclosingMethodReturnsNullForLocalClassInType() {
        assertNull(InnerLocalClass.class.getEnclosingMethod());
      }
    }
    OuterLocalClass olc = new OuterLocalClass();
    olc.getEnclosingMethodReturnsNullForLocalClassInType();
  }

  private static class OuterClass {

    static {
      class InnerLocalClass {}
      assertNull(InnerLocalClass.class.getEnclosingMethod());
    }

    public void forceInitialization() { }
  }

  @Test
  public void getEnclosingMethodReturnsNullForLocalClassInStaticInitializer() throws NoSuchMethodException {
    OuterClass oc = new OuterClass();
    oc.forceInitialization();
  }

  @Test
  public void getEnclosingMethodReturnsNullForLocalClassInConstructor() throws NoSuchMethodException {
    class OuterLocalClass {
      OuterLocalClass() throws NoSuchMethodException {
        class LocalClassInConstructor {}
        assertNull(LocalClassInConstructor.class.getEnclosingMethod());
      }
    }
    @SuppressWarnings("unused")
    OuterLocalClass olc = new OuterLocalClass();
  }

  @Test
  public void getEnclosingMethodReturnsEnclosingMethodForLocalClassInMethod() throws NoSuchMethodException {
    class OuterLocalClass {
      public void methodWithLocalClass() throws NoSuchMethodException {
        class LocalClassInMethod {};
        assertEquals(LocalClassInMethod.class.getEnclosingMethod(),
            OuterLocalClass.class.getMethod("methodWithLocalClass"));
      }
    }
    OuterLocalClass olc = new OuterLocalClass();
    olc.methodWithLocalClass();
  }

  @Test
  public void getEnclosingConstructorReturnsNullForLocalClassInType() throws NoSuchMethodException {
    class OuterLocalClass {
      class InnerLocalClass {}

      void getEnclosingConstructorReturnsNullForLocalClassInType() {
        assertNull(InnerLocalClass.class.getEnclosingConstructor());
      }
    }
    OuterLocalClass olc = new OuterLocalClass();
    olc.getEnclosingConstructorReturnsNullForLocalClassInType();
  }

  private static class OuterClassForConstructor {

    static {
      class InnerLocalClass {}
      assertNull(InnerLocalClass.class.getEnclosingConstructor());
    }

    public void forceInitialization() {

    }
  }

  @Test
  public void getEnclosingConstructorReturnsNullForLocalClassInStaticInitializer() throws NoSuchMethodException {
    OuterClassForConstructor oc = new OuterClassForConstructor();
    oc.forceInitialization();
  }

  @Test
  public void getEnclosingConstructorReturnsNullForLocalClassInMethod() {
    class OuterLocalClass {
      void methodWithLocalClass() {
        class LocalClassInMethod {}
        assertNull(LocalClassInMethod.class.getEnclosingConstructor());
      }
    }
    OuterLocalClass olc = new OuterLocalClass();
    olc.methodWithLocalClass();
  }

  @Test
  public void getEnclosingMethodReturnsConstructorForLocalClassInConstructor() throws NoSuchMethodException {
    class OuterLocalClass {
      OuterLocalClass() throws NoSuchMethodException {
        class LocalClassInconstructor {}
        assertEquals(LocalClassInconstructor.class.getEnclosingConstructor(),
            OuterLocalClass.class.getDeclaredConstructor(org.jikesrvm.JavaLangClassTest.class));
      }
    }
    @SuppressWarnings("unused")
    OuterLocalClass olc = new OuterLocalClass();
  }

}
