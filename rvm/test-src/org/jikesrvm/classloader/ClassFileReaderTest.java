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
import static org.jikesrvm.classloader.ClassFileReader.TAG_TYPEREF;
import static org.jikesrvm.classloader.ClassFileReader.TAG_UTF;
import static org.junit.Assert.assertThat;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;

import org.jikesrvm.junit.runners.RequiresBuiltJikesRVM;
import org.jikesrvm.junit.runners.VMRequirements;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.junit.runner.RunWith;

@RunWith(VMRequirements.class)
@Category(RequiresBuiltJikesRVM.class)
public class ClassFileReaderTest {

  private static final int CLASS_FILE_MAGIC_NUMBER = 0xCAFEBABE;
  private DataOutputStream dos;
  private byte[] bytes;
  private DataInputStream dis;
  private ByteArrayInputStream byteArrayInputStream;
  private ByteArrayOutputStream bos;
  private ClassFileReader classFileReader;

  @Before
  public void setup() {
    bos = new ByteArrayOutputStream();
    dos = new DataOutputStream(bos);
  }

  @After
  public void teardown() throws IOException {
    dos.close();
    if (dis != null) {
      dis.close();
    }
  }

  private void transferDataToDataInputStream() {
    bytes = bos.toByteArray();
    byteArrayInputStream = new ByteArrayInputStream(bytes);
    dis = new DataInputStream(byteArrayInputStream);
    classFileReader = new ClassFileReader(byteArrayInputStream);
  }

  @Test(expected = ClassFormatError.class)
  public void classFormatErrorIsThrownWhenMagicNumberIsNotPresent() throws Exception {
    dos.writeInt(0xDEADDEAD);
    transferDataToDataInputStream();
    String className = null;
    classFileReader.readClass(className, null);
  }

  @Test(expected = ClassFormatError.class)
  public void classFileVersionsLowerThan45AreNotSupported() throws Exception {
    dos.writeInt(CLASS_FILE_MAGIC_NUMBER);
    dos.writeShort(0);
    dos.writeShort(44);
    transferDataToDataInputStream();
    String className = null;
    classFileReader.readClass(className, null);
  }

  @Test(expected = ClassFormatError.class)
  public void classFileVersionsHigherThan50Minor0AreNotSupported() throws Exception {
    dos.writeInt(CLASS_FILE_MAGIC_NUMBER);
    dos.writeShort(1);
    dos.writeShort(50);
    transferDataToDataInputStream();
    String className = null;
    classFileReader.readClass(className, null);
  }

  @Test(expected = ClassFormatError.class)
  public void classFileVersionsHigherThan50AreNotSupported() throws Exception {
    dos.writeInt(CLASS_FILE_MAGIC_NUMBER);
    dos.writeShort(0);
    dos.writeShort(51);
    transferDataToDataInputStream();
    String className = null;
    classFileReader.readClass(className, null);
  }

  @Test
  public void smokeTestForReading() throws Exception {
    dos.writeInt(CLASS_FILE_MAGIC_NUMBER);
    dos.writeShort(0);
    dos.writeShort(45);
    // 4 entries, starting at 1, so length of 5
    dos.writeShort(5);
    // index 1 (0 is unused!)
    dos.writeByte(TAG_UTF);
    String superClassName = "java/lang/Object";
    dos.writeShort(superClassName.getBytes().length);
    dos.writeBytes(superClassName);
    // index 2
    dos.writeByte(TAG_TYPEREF);
    dos.writeShort(1);
    // index 3
    dos.writeByte(TAG_UTF);
    String ownClassName = "org/jikesrvm/classloading/SmokeTest";
    dos.writeShort(ownClassName.getBytes().length);
    dos.writeBytes(ownClassName);
    // index 4
    dos.writeByte(TAG_TYPEREF);
    dos.writeShort(3);
    // undefined modifiers
    dos.writeShort(0);
    // write type index
    dos.writeShort(4);
    // write superclass index
    dos.writeShort(2);
    // write number of interfaces: none
    dos.writeShort(0);
    // write number of fields: none
    dos.writeShort(0);
    // write number of methods: none
    dos.writeShort(0);
    // write number of attributes: none
    dos.writeShort(0);
    transferDataToDataInputStream();
    String className = "org.jikesrvm.classloading.SmokeTest";
    RVMType readClass = classFileReader.readClass(className, null);
    assertThat(readClass.asClass().getDescriptor().toString(), is("Lorg/jikesrvm/classloading/SmokeTest;"));
  }

}
