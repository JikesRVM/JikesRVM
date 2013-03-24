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

import java.util.WeakHashMap;

/**
 *  Lightweight implementation of a vector of Fields. This class is intended
 *  to be used by a single thread and is therefore not thread-safe.
 */
final class MethodVector {
  //-----------//
  // interface //
  //-----------//

  public MethodVector() {
    array = new RVMMethod[10];
  }

  void addElement(RVMMethod item) {
    if (cnt == array.length) {
      adjustLength(cnt << 1); // double size of array
    }
    array[cnt++] = item;
  }

  /**
   * Adds an item if it is not already in the vector. The test
   * for the item uses object identity.
   *
   * @param item method to be added
   */
  public void addUniqueElement(RVMMethod item) {
    for (int i = 0; i < cnt; i++) {
      if (array[i] == item) return;
    }
    addElement(item);
  }

  RVMMethod elementAt(int index) {
    return array[index];
  }

  void setElementAt(RVMMethod item, int index) {
    array[index] = item;
  }

  public int size() {
    return cnt;
  }

  /**
   *
   * @return an array of methods, trimmed to size. The returned array
   *  is canonical: Adding the same set of methods in the same order
   *  to different newly-created vectors {@code v1} and {@code v2}
   *  will lead to the same array being returned for both {@code v1}
   *  and {@code v2} when this method is called.
   */
  public RVMMethod[] finish() {
    synchronized(MethodVector.class) {
      RVMMethod[] result = popularMVs.get(this);
      if (result != null) {
        array = result;
        return result;
      } else {
        adjustLength(cnt);
        popularMVs.put(this, array);
        return array;
      }
    }
  }

  @Override
  public int hashCode() {
    int val = 0;
    for (int i=cnt-1; i >= 0; i--) {
      val ^= array[i].hashCode();
    }
    return val;
  }

  @Override
  public boolean equals(Object obj) {
    if (obj instanceof MethodVector) {
      MethodVector that = (MethodVector)obj;
      if (cnt != that.cnt) return false;
      for(int i=cnt-1; i>=0; i--) {
        if (array[i] != that.array[i]) return false;
      }
      return true;
    } else {
      return false;
    }
  }

  //----------------//
  // implementation //
  //----------------//

  private RVMMethod[] array;
  private int cnt;

  private static final RVMMethod[] empty = new RVMMethod[0];
  private static final WeakHashMap<MethodVector, RVMMethod[]>
    popularMVs = new WeakHashMap<MethodVector, RVMMethod[]>();

  private void adjustLength(int newLength) {
    if (newLength == 0) {
      array = empty;
    } else {
      RVMMethod[] newElements = new RVMMethod[newLength];
      int n = array.length;
      if (n > newLength) {
        n = newLength;
      }

      for (int i = 0; i < n; ++i) {
        newElements[i] = array[i];
      }

      array = newElements;
    }
  }
}
