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
package org.jikesrvm.classloader;

/**
 *  Lightweight implementation of a vector of VM_Fields.
 */
final class VM_FieldVector {
  //-----------//
  // interface //
  //-----------//

  public VM_FieldVector() {
    array = new VM_Field[10];
  }

  // Add item.
  //
  void addElement(VM_Field item) {
    if (cnt == array.length) {
      adjustLength(cnt << 1); // double size of array
    }
    array[cnt++] = item;
  }

  // Add item if it is not already in the Vector.
  //
  public void addUniqueElement(VM_Field item) {
    for (int i = 0; i < cnt; i++) {
      if (array[i] == item) return;
    }
    addElement(item);
  }

  // Get item.
  //
  VM_Field elementAt(int index) {
    return array[index];
  }

  // Set item.
  //
  void setElementAt(VM_Field item, int index) {
    array[index] = item;
  }

  // Get number of items added so far.
  //
  public int size() {
    return cnt;
  }

  // Get array, trimmed to size.
  //
  public VM_Field[] finish() {
    adjustLength(cnt);
    return array;
  }

  //----------------//
  // implementation //
  //----------------//

  private VM_Field[] array;
  private int cnt;

  private static final VM_Field[] empty = new VM_Field[0];

  private void adjustLength(int newLength) {
    if (newLength == 0) {
      array = empty;
    } else {
      VM_Field[] newElements = new VM_Field[newLength];
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
