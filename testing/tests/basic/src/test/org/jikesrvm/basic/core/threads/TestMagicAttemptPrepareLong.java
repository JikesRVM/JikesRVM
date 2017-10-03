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
package test.org.jikesrvm.basic.core.threads;

import org.jikesrvm.classloader.RVMField;
import org.jikesrvm.runtime.Magic;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

import org.vmmagic.unboxed.Offset;

class TestMagicAttemptPrepareLong extends XThread {

  public static void main(String[] args) {
    LongField lf = new LongField();
    for (int i = 0; i < 5; i++) {
      TestMagicAttemptPrepareLong tmapl = new TestMagicAttemptPrepareLong(i,lf);
      tmapl.start();
    }
    XThread.say("bye");
    XThread.outputMessages();
  }

  static volatile int vi = 0;

  int n;
  long l;
  LongField lf;

  TestMagicAttemptPrepareLong(int i, LongField lf) {
    super("MAPL" + i);
    n = i;
    l = (((long) n) << 32) + n;
    this.lf = lf;
  }

  void performTask() {
    int errors = 0;

    RVMField internalLongField = null;
    try {
      Field longField = LongField.class.getField("longAccessedViaMagic");
      Class<?> jikesRVMSupportClass = Class.forName("java.lang.reflect.JikesRVMSupport");
      Method m = jikesRVMSupportClass.getMethod("getFieldOf", Class.forName("java.lang.reflect.Field"));
      internalLongField = (RVMField) m.invoke(null, longField);
    } catch (Exception ex) {
      ex.printStackTrace();
      return;
    }

    Offset fieldOffset = internalLongField.getOffset();

    for (int i = 0; i < 10000000; i++) {
      long tl = Magic.prepareLong(lf, fieldOffset);
      Magic.attemptLong(lf, fieldOffset, tl, l);
      int n0 = (int) tl;
      int n1 = (int) (tl >> 32);
      if (n0 != n1) errors++;
      vi = n;
    }
    tsay(errors + " errors found");
  }

  private static class LongField {
    public long longAccessedViaMagic = 0;
  }

}
