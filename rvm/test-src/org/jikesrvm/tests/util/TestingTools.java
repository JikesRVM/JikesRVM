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
package org.jikesrvm.tests.util;

import java.lang.reflect.Field;
import java.lang.reflect.JikesRVMSupport;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Enumeration;
import java.util.Iterator;
import java.util.Vector;

import org.jikesrvm.classloader.NormalMethod;
import org.jikesrvm.classloader.RVMField;
import org.jikesrvm.classloader.RVMMethod;
import org.jikesrvm.compilers.opt.inlining.InlineSequence;
import org.jikesrvm.compilers.opt.ir.Call;
import org.jikesrvm.compilers.opt.ir.Instruction;

public class TestingTools {

  public static <T> Iterable<T> asIterable(final Iterator<T> it) {
    return new Iterable<T>() {
       @Override
      public Iterator<T> iterator() {
         return it;
       }
    };
  }

  public static <T> Vector<T> asVector(T... elems) {
    Vector<T> v = new Vector<T>(elems.length);

    for (int i = 0; i < elems.length; i++)
      v.add(i, elems[i]);

    return v;
  }

  public static Byte[] boxed(byte [] bytes) {
    Byte[] boxedBytes = new Byte[bytes.length];

    for (int i=0; i<boxedBytes.length; i++) {
        boxedBytes[i] = bytes[i];
    }

    return boxedBytes;
  }

  public static <T> ArrayList<T> toList(Enumeration<T> en) {
    return Collections.list(en);
  }

  public static NormalMethod getNormalMethod(Class<?> declaringClass, String name, Class<?>... argumentTypes) throws Exception {
    Method m = declaringClass.getMethod(name, argumentTypes);
    RVMMethod rvmm = JikesRVMSupport.getMethodOf(m);
    return (NormalMethod) rvmm;
  }

  private static Instruction setByteCodeIndex(int byteCodeIndex) {
    Instruction instruction = Call.create(org.jikesrvm.compilers.opt.ir.Operators.CALL, null, null, null, null, 0);
    instruction.setBytecodeIndex(byteCodeIndex);
    return instruction;
  }

  public static InlineSequence createInlineSequence(Class<?> declaringClass, String methodName, Class<?>... argumentTypes) throws Exception {
    return new InlineSequence(getNormalMethod(declaringClass, methodName));
  }

  public static InlineSequence createInlineSequence(InlineSequence node ,int ByteCodeIndex, Class<?> declaringClass, String methodName, Class<?>... argumentTypes) throws Exception {
    return new InlineSequence(getNormalMethod(declaringClass, methodName), node, setByteCodeIndex(ByteCodeIndex));
  }

  public static RVMField getRVMFieldForField(Field field) {
    return JikesRVMSupport.getFieldOf(field);
  }

}
