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
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.assertTrue;

import java.util.Arrays;
import java.util.List;

import org.jikesrvm.VM;
import org.jikesrvm.junit.runners.RequiresJikesRVM;
import org.jikesrvm.junit.runners.VMRequirements;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.junit.runner.RunWith;

@RunWith(VMRequirements.class)
@Category(RequiresJikesRVM.class)
public class MethodVectorTest {

  private static TypeReference tRef;
  private MethodVector mv;

  @BeforeClass
  public static void createTypeReference() {
    if (VM.runningVM) {
      tRef = TypeReference.findOrCreate("LFoo;");
    }
  }

  @Before
  public void setUp() {
    mv = new MethodVector();
  }

  @Test
  public void oneMethodIsSavedCorrectly() {
    RVMMethod oneMethod = createMockMethod("foo");

    mv.addElement(oneMethod);

    RVMMethod[] methods = mv.finish();
    assertThat(methods.length, is(1));
    assertSame(methods[0], oneMethod);
  }

  RVMMethod createMockMethod(String methodName) {
    Atom memberName = Atom.findOrCreateAsciiAtom(methodName);
    Atom memberDescriptor = Atom.findOrCreateAsciiAtom("()V");
    MemberReference mr = MemberReference.findOrCreate(tRef, memberName, memberDescriptor);
    short mo = 0;
    short lw = 0;
    short ow = 0;
    byte[] bc = new byte[0];
    int[] constantPool = new int[0];
    NormalMethod mockMethod = new NormalMethod(tRef, mr, mo, null, lw, ow, bc, null, null, null, constantPool, null, null, null, null);
    return mockMethod;
  }

  @Test
  public void finalArrayIsTrimmedToSize() {
    RVMMethod fooMethod = createMockMethod("foo");
    RVMMethod barMethod = createMockMethod("bar");
    RVMMethod bazMethod = createMockMethod("baz");

    mv.addElement(fooMethod);
    mv.addElement(barMethod);
    mv.addElement(bazMethod);

    RVMMethod[] methods = mv.finish();
    assertThat(methods.length, is(3));
    List<RVMMethod> methodList = Arrays.asList(methods);
    assertTrue(methodList.contains(fooMethod));
    assertTrue(methodList.contains(barMethod));
    assertTrue(methodList.contains(bazMethod));
  }

  @Test
  public void addUniqueElementOnlyAddsElementsIfRequired() {
    RVMMethod testMethod = createMockMethod("test");

    mv.addUniqueElement(testMethod);
    mv.addUniqueElement(testMethod);

    RVMMethod[] methods = mv.finish();
    assertThat(methods.length, is(1));
    assertSame(methods[0], testMethod);
  }

  @Test
  public void sizeWorksCorrectly() {
    assertThat(mv.size(), is(0));

    RVMMethod testMethod = createMockMethod("sizeTest");
    mv.addElement(testMethod);
    assertThat(mv.size(), is(1));

    mv.addElement(testMethod);
    assertThat(mv.size(), is(2));
  }

  @Test
  public void elementAtWorksForValidIndexes() {
    RVMMethod fooMethod = createMockMethod("foo");
    mv.addElement(fooMethod);
    RVMMethod barMethod = createMockMethod("bar");
    mv.addElement(barMethod);
    RVMMethod bazMethod = createMockMethod("baz");
    mv.addElement(bazMethod);
    assertSame(fooMethod, mv.elementAt(0));
    assertSame(barMethod, mv.elementAt(1));
    assertSame(bazMethod, mv.elementAt(2));
  }

  @Test(expected = ArrayIndexOutOfBoundsException.class)
  public void elementAtDoesNotCheckForTooSmallIndexes() {
    mv.elementAt(-1);
  }

  @Test(expected = ArrayIndexOutOfBoundsException.class)
  public void elementAtDoesNotCheckForTooBigIndexes() {
    mv.elementAt(1000);
  }

  @Test
  public void elementAtReturnsUnintializedValuesForInvalidIndexes() {
    RVMMethod method = mv.elementAt(5);
    assertNull(method);
  }

  @Test
  public void setElementWorksCorrectly() throws Exception {
    RVMMethod fooMethod = createMockMethod("foo");
    RVMMethod barMethod = createMockMethod("bar");
    RVMMethod bazMethod = createMockMethod("baz");

    RVMMethod replaceMethod = createMockMethod("replace");

    mv.addElement(fooMethod);
    mv.addElement(barMethod);
    mv.addElement(bazMethod);

    mv.setElementAt(replaceMethod, 1);

    RVMMethod[] methods = mv.finish();
    assertThat(methods.length, is(3));
    assertSame(fooMethod, methods[0]);
    assertSame(replaceMethod, methods[1]);
    assertSame(bazMethod, methods[2]);
  }

  @Test(expected = ArrayIndexOutOfBoundsException.class)
  public void setElementAtDoesNotCheckForTooSmallIndexes() {
    RVMMethod indexTooSmallMethod = createMockMethod("indexTooSmall");
    mv.setElementAt(indexTooSmallMethod, -1);
  }

  @Test(expected = ArrayIndexOutOfBoundsException.class)
  public void setElementAtDoesNotCheckForTooBigIndexes() {
    RVMMethod indexTooBigMethod = createMockMethod("indexTooBig");
    mv.setElementAt(indexTooBigMethod, 1000);
  }

  @Test
  public void setElementAtAllowsSettingOfValuesForInvalidIndexes() {
    RVMMethod invalidIndexMethod = createMockMethod("invalidIndex");
    mv.setElementAt(invalidIndexMethod, 5);
  }


  @Test(expected = NullPointerException.class)
  public void setElementAtAllowsNullValuesWhichWillCauseTroubleLater() {
    RVMMethod okMethod = createMockMethod("ok");
    mv.addElement(okMethod);
    mv.setElementAt(null, 0);
    RVMMethod[] methods = mv.finish();
    assertNull(methods[0]);
  }

  @Test
  public void cachingWorks() throws Exception {
    RVMMethod methodOne = createMockMethod("methodOne");
    RVMMethod methodTwo = createMockMethod("methodTwo");

    mv.addElement(methodOne);
    mv.addElement(methodTwo);
    RVMMethod[] canonicalMethodArray = mv.finish();

    MethodVector anotherVectorWithSameFields = new MethodVector();
    anotherVectorWithSameFields.addElement(methodOne);
    anotherVectorWithSameFields.addElement(methodTwo);
    RVMMethod[] resultMethodArray = anotherVectorWithSameFields.finish();
    assertSame(canonicalMethodArray, resultMethodArray);
  }

}
