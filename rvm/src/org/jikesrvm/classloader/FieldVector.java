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
final class FieldVector {
  //-----------//
  // interface //
  //-----------//

  public FieldVector() {
    array = new RVMField[10];
  }

  void addElement(RVMField item) {
    if (cnt == array.length) {
      adjustLength(cnt << 1); // double size of array
    }
    array[cnt++] = item;
  }

  /**
   * @return an array of fields, trimmed to size. The returned array
   *  is canonical: Adding the same set of fields in the same order
   *  to different newly-created vectors {@code v1} and {@code v2}
   *  will lead to the same array being returned for both {@code v1}
   *  and {@code v2} when this method is called.
   */
  public RVMField[] finish() {
    synchronized(RVMField.class) {
      RVMField[] result = popularFVs.get(this);
      if (result != null) {
        array = result;
        return result;
      } else {
        adjustLength(cnt);
        popularFVs.put(this, array);
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
    if (obj instanceof FieldVector) {
      FieldVector that = (FieldVector)obj;
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

  private RVMField[] array;
  private int cnt;

  private static final RVMField[] empty = new RVMField[0];
  private static final WeakHashMap<FieldVector, RVMField[]>
    popularFVs = new WeakHashMap<FieldVector, RVMField[]>();

  private void adjustLength(int newLength) {
    if (newLength == 0) {
      array = empty;
    } else {
      RVMField[] newElements = new RVMField[newLength];
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
