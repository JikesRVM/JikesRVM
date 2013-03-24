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
import static org.junit.Assert.assertThat;

import java.lang.reflect.Field;
import java.util.Arrays;
import java.util.List;

import org.jikesrvm.junit.runners.RequiresJikesRVM;
import org.jikesrvm.junit.runners.VMRequirements;
import org.jikesrvm.tests.util.TestingTools;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.junit.runner.RunWith;

@RunWith(VMRequirements.class)
@Category(RequiresJikesRVM.class)
public class FieldVectorTest {

  public int firstFieldForTesting;
  public Object secondFieldForTesting;
  public long thirdFieldForTesting;

  private FieldVector fv;

  @Before
  public void setUp() {
    fv = new FieldVector();
  }

  @Test
  public void oneFieldIsSavedCorrectly() throws Exception {
    Field field = this.getClass().getField("firstFieldForTesting");
    RVMField rvmField = TestingTools.getRVMFieldForField(field);
    fv.addElement(rvmField);
    RVMField[] fields = fv.finish();
    assertThat(fields.length, is(1));
    assertThat(Arrays.asList(fields).contains(rvmField),is(true));
  }

  @Test
  public void finalArrayIsTrimmedToSize() throws Exception {
    Field firstField = this.getClass().getField("firstFieldForTesting");
    RVMField firstRVMField = TestingTools.getRVMFieldForField(firstField);

    Field secondField = this.getClass().getField("secondFieldForTesting");
    RVMField secondRVMField = TestingTools.getRVMFieldForField(secondField);

    Field thirdField = this.getClass().getField("thirdFieldForTesting");
    RVMField thirdRVMField = TestingTools.getRVMFieldForField(thirdField);

    fv.addElement(firstRVMField);
    fv.addElement(secondRVMField);
    fv.addElement(thirdRVMField);

    RVMField[] fields = fv.finish();
    assertThat(fields.length, is(3));
    List<RVMField> fieldList = Arrays.asList(fields);
    assertThat(fieldList.contains(firstRVMField),is(true));
    assertThat(fieldList.contains(secondRVMField),is(true));
    assertThat(fieldList.contains(thirdRVMField),is(true));
  }

  @Test
  public void cachingWorks() throws Exception {
    Field secondField = this.getClass().getField("secondFieldForTesting");
    RVMField secondRVMField = TestingTools.getRVMFieldForField(secondField);
    fv.addElement(secondRVMField);
    RVMField[] canonicalFieldArray = fv.finish();

    FieldVector anotherVectorWithSameFields = new FieldVector();
    anotherVectorWithSameFields.addElement(secondRVMField);
    RVMField[] resultFieldArray = anotherVectorWithSameFields.finish();
    assertSame(canonicalFieldArray, resultFieldArray);
  }

}
