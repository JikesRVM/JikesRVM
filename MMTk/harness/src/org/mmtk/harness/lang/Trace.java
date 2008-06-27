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

public class Trace {
  public enum Item { ALLOC, CALL, OBJECT }

  private static EnumSet<Item> enabled = EnumSet.noneOf(Item.class);

  static {
    //enable(Item.ALLOC);
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

  public static void trace(Item item, String pattern, Object...args) {
    if (isEnabled(item)) {
      System.out.printf("["+item+"] "+pattern+"%n",args);
    }
  }
}
