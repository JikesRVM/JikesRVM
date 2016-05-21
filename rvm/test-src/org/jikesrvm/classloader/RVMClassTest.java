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
import static org.junit.Assert.*;
import static org.jikesrvm.classloader.ClassLoaderConstants.*;

import java.util.Arrays;
import java.util.List;

import org.jikesrvm.junit.runners.RequiresBuiltJikesRVM;
import org.jikesrvm.junit.runners.VMRequirements;
import org.jikesrvm.runtime.RuntimeEntrypoints;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.junit.runner.RunWith;

@RunWith(VMRequirements.class)
@Category(RequiresBuiltJikesRVM.class)
public class RVMClassTest {

  @Test
  public void testAdditionOfSubclasses() throws ClassNotFoundException {
    String topClassName = "ClassForRVMClassTest";
    RVMClass javaLangObject = JikesRVMSupport.getTypeForClass(Class.forName("java.lang.Object")).asClass();
    RVMClass topClass = createRVMClass(topClassName, javaLangObject);
    assertThat(topClass.getSubClasses().length, is(0));

    String firstSubClassName = topClassName + "SubclassOne";
    RVMClass firstSubClass = createRVMClass(firstSubClassName, topClass);
    assertThat(firstSubClass.getSuperClass(), is(topClass));

    String secondSubClassName = topClassName + "SubclassTwo";
    RVMClass secondSubClass = createRVMClass(secondSubClassName, topClass);
    assertThat(secondSubClass.getSuperClass(), is(topClass));

    RVMClass[] subClasses = topClass.getSubClasses();
    List<RVMClass> subClassesList = Arrays.asList(subClasses);
    assertTrue(subClassesList.contains(firstSubClass));
    assertTrue(subClassesList.contains(secondSubClass));
    assertThat(subClassesList.size(), is(2));
  }

  private RVMClass createRVMClass(String className, RVMClass superClass)
      throws ClassNotFoundException {
    TypeReference tRef = TypeReference.findOrCreate(className);
    int[] constantPool = new int[0];
    short modifiers = ACC_SYNTHETIC | ACC_PUBLIC;
    RVMClass[] declaredInterfaces = new RVMClass[0];
    RVMField[] declaredFields = new RVMField[0];
    RVMMethod[] declaredMethods = new RVMMethod[0];
    RVMClass klass =
        new RVMClass(tRef, constantPool, modifiers, modifiers,
            superClass,
            declaredInterfaces,
            declaredFields, declaredMethods,
            null, null, null, null, null, null, null, null);
    tRef.setType(klass);
    RuntimeEntrypoints.initializeClassForDynamicLink(klass);
    return klass;
  }

  @Test
  public void interfaceVirtualMethodsDontContainMethodsWithTheSameSignatureAndName() throws ClassNotFoundException {
    RVMMethod[] testInterfaceAMethods = JikesRVMSupport.getTypeForClass(
        TestInterface_A.class).getVirtualMethods();
    RVMMethod[] testInterfaceBMethods = JikesRVMSupport.getTypeForClass(
        TestInterface_B.class).getVirtualMethods();
    assertThat(testInterfaceBMethods.length, is(testInterfaceAMethods.length));
  }

  private interface TestInterface_B extends TestInterface_A {
    @Override
    void test();
  }

  private interface TestInterface_A {
    void test();
  }


}
