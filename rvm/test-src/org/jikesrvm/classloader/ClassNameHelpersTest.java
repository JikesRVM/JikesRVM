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
import static org.hamcrest.CoreMatchers.nullValue;
import static org.junit.Assert.*;

import org.jikesrvm.junit.runners.VMRequirements;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(VMRequirements.class)
public class ClassNameHelpersTest {

  private static final String JAVA_LANG_STRING_CLASSNAME = "java.lang.String";
  private static final String JAVA_LANG_STRING_INTERNAL_NAME = "java/lang/String";

  @Test
  public void convertClassnameToInternalNameWorksForReferenceTypes() throws Exception {
    assertThat(ClassNameHelpers.convertClassnameToInternalName(JAVA_LANG_STRING_CLASSNAME), is(JAVA_LANG_STRING_INTERNAL_NAME));
  }

  @Test
  public void convertClassnameToInternalNameReturnsNullForNull() throws Exception {
    assertThat(ClassNameHelpers.convertClassnameToInternalName(null), nullValue());
  }

  @Test
  public void convertClassnameToInternalNameReturnsDoesNotChangeInternalNames() throws Exception {
    assertThat(ClassNameHelpers.convertClassnameToInternalName(JAVA_LANG_STRING_INTERNAL_NAME), is(JAVA_LANG_STRING_INTERNAL_NAME));
  }

}
