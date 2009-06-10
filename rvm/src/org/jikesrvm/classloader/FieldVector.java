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
 *  Lightweight implementation of a vector of Fields.
 */
final class FieldVector {
  //-----------//
  // interface //
  //-----------//

  public FieldVector() {
    array = new RVMField[10];
  }

  // Add item.
  //
  void addElement(RVMField item) {
    if (cnt == array.length) {
      adjustLength(cnt << 1); // double size of array
    }
    array[cnt++] = item;
  }

  // Add item if it is not already in the Vector.
  //
  public void addUniqueElement(RVMField item) {
    for (int i = 0; i < cnt; i++) {
      if (array[i] == item) return;
    }
    addElement(item);
  }

  // Get item.
  //
  RVMField elementAt(int index) {
    return array[index];
  }

  // Set item.
  //
  void setElementAt(RVMField item, int index) {
    array[index] = item;
  }

  // Get number of items added so far.
  //
  public int size() {
    return cnt;
  }

  // Get array, trimmed to size.
  //
  public RVMField[] finish() {
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

  public int hashCode() {
    int val = 0;
    for (int i=cnt-1; i >= 0; i--) {
      val ^= array[i].hashCode();
    }
    return val;
  }

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
