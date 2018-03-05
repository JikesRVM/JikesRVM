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
import static org.jikesrvm.classloader.BytecodeConstants.JBC_arraylength;
import static org.jikesrvm.classloader.BytecodeConstants.JBC_getfield;
import static org.jikesrvm.classloader.BytecodeConstants.JBC_getstatic;
import static org.jikesrvm.classloader.BytecodeConstants.JBC_impdep1;
import static org.jikesrvm.classloader.BytecodeConstants.JBC_impdep2;
import static org.jikesrvm.classloader.BytecodeConstants.JBC_invokedynamic;
import static org.jikesrvm.classloader.BytecodeConstants.JBC_invokeinterface;
import static org.jikesrvm.classloader.BytecodeConstants.JBC_invokespecial;
import static org.jikesrvm.classloader.BytecodeConstants.JBC_invokestatic;
import static org.jikesrvm.classloader.BytecodeConstants.JBC_invokevirtual;
import static org.jikesrvm.classloader.BytecodeConstants.JBC_nop;
import static org.jikesrvm.classloader.BytecodeConstants.JBC_putfield;
import static org.jikesrvm.classloader.BytecodeConstants.JBC_putstatic;
import static org.junit.Assert.assertThat;

import org.jikesrvm.junit.runners.VMRequirements;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(VMRequirements.class)
public class BytecodeConstantsTest {

  @Test
  public void invokeinterfaceIsAJava6Call() throws Exception {
    assertThat(BytecodeConstants.JBC_isJava6Call(JBC_invokeinterface), is(true));
  }


  @Test
  public void invokevirtualIsAJava6Call() throws Exception {
    assertThat(BytecodeConstants.JBC_isJava6Call(JBC_invokevirtual), is(true));
  }

  @Test
  public void invokestaticIsAJava6Call() throws Exception {
    assertThat(BytecodeConstants.JBC_isJava6Call(JBC_invokestatic), is(true));
  }

  @Test
  public void invokespecialIsAJava6Call() throws Exception {
    assertThat(BytecodeConstants.JBC_isJava6Call(JBC_invokespecial), is(true));
  }

  @Test
  public void invokedynamicIsNotAJava6Call() throws Exception {
    assertThat(BytecodeConstants.JBC_isJava6Call(JBC_invokedynamic), is(false));
  }

  @Test
  public void impdep1IsNotAJava6Call() throws Exception {
    assertThat(BytecodeConstants.JBC_isJava6Call(JBC_impdep1), is(false));
  }

  @Test
  public void impdep12sNotAJava6Call() throws Exception {
    assertThat(BytecodeConstants.JBC_isJava6Call(JBC_impdep2), is(false));
  }

  @Test
  public void getstaticIsAFieldAccess() throws Exception {
    assertThat(BytecodeConstants.JBC_isFieldAccess(JBC_getstatic), is(true));
  }

  @Test
  public void putstaticIsAFieldAccess() throws Exception {
    assertThat(BytecodeConstants.JBC_isFieldAccess(JBC_putstatic), is(true));
  }

  @Test
  public void getfieldIsAFieldAccess() throws Exception {
    assertThat(BytecodeConstants.JBC_isFieldAccess(JBC_getfield), is(true));
  }

  @Test
  public void putfieldIsAFieldAccess() throws Exception {
    assertThat(BytecodeConstants.JBC_isFieldAccess(JBC_putfield), is(true));
  }

  @Test
  public void arraylengthIsNotAFieldAccess() throws Exception {
    assertThat(BytecodeConstants.JBC_isFieldAccess(JBC_arraylength), is(false));
  }

  @Test
  public void nopIsNotAFieldAccess() throws Exception {
    assertThat(BytecodeConstants.JBC_isFieldAccess(JBC_nop), is(false));
  }

}
