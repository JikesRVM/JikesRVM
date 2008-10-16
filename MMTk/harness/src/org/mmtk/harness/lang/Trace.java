/*
 *  This file is part of the Jikes RVM project (http://jikesrvm.org).
 *
 *  This file is licensed to You under the Common Public License (CPL);
 *  You may not use this file except in compliance with the License. You
 *  may obtain a copy of the License at
 *
 *      http://www.opensource.org/licenses/cpl1.0.php
 *
 *  See the COPYRIGHT.txt file distributed with this work for information
 *  regarding copyright ownership.
 */
package org.mmtk.harness.lang;

import java.util.EnumSet;

public final class Trace {
  public enum Item { ALLOC, CALL, OBJECT, INTRINSIC, LOAD, STORE, HASH, ENV,
    ROOTS, COLLECT, AVBYTE, EVAL, COMPILER, CHECKER, SCHEDULER }

  private static EnumSet<Item> enabled = EnumSet.noneOf(Item.class);

  static {
    //enable(Item.ALLOC);
  }

  public static String[] itemNames() {
    String[] result = new String[Item.values().length+1];
    result[0] = "NONE";
    for (int i=0; i < Item.values().length; i++) {
      result[i+1] = Item.values()[i].toString();
    }
    return result;
  }

  public static void enable(String item) {
    enable(Item.valueOf(item));
  }

  public static void enable(Item item) {
    enabled.add(item);
  }

  public static boolean isEnabled(Item item) {
    return enabled.contains(item);
  }

  public static synchronized void trace(Item item, String pattern, Object...args) {
    if (isEnabled(item)) {
      printf(prefix(item) + pattern + "%n",args);
    }
  }

  public static String prefix(Item item) {
    return "["+item+"] ";
  }

  public static void printf(String pattern, Object...args) {
    System.err.printf(pattern,args);
  }
}
