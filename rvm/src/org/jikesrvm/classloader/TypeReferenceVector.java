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
final class TypeReferenceVector {
  //-----------//
  // interface //
  //-----------//

  public TypeReferenceVector() {
    array = new TypeReference[10];
  }

  // Add item.
  //
  void addElement(TypeReference item) {
    if (cnt == array.length) {
      adjustLength(cnt << 1); // double size of array
    }
    array[cnt++] = item;
  }

  // Add item if it is not already in the Vector.
  //
  public void addUniqueElement(TypeReference item) {
    for (int i = 0; i < cnt; i++) {
      if (array[i] == item) return;
    }
    addElement(item);
  }

  // Get item.
  //
  TypeReference elementAt(int index) {
    return array[index];
  }

  // Set item.
  //
  void setElementAt(TypeReference item, int index) {
    array[index] = item;
  }

  // Get number of items added so far.
  //
  public int size() {
    return cnt;
  }

  // Get array, trimmed to size.
  //
  public TypeReference[] finish() {
    TypeReference[] result = popularTRVs.get(this);
    if (result != null) {
      array = result;
      return result;
    } else {
      adjustLength(cnt);
      popularTRVs.put(this, array);
      return array;
    }
  }

  @Override
  public int hashCode() {
    int val=0;
    for(int i=0; i<cnt; i++) {
      val ^= array[i].hashCode();
    }
    return val;
  }

  @Override
  public boolean equals(Object obj) {
    if (obj instanceof TypeReferenceVector) {
      TypeReferenceVector that = (TypeReferenceVector)obj;
      if (cnt != that.cnt) return false;
      for(int i=0; i<cnt; i++) {
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

  private TypeReference[] array;
  private int cnt;

  private static final TypeReference[] empty = new TypeReference[0];
  private static final WeakHashMap<TypeReferenceVector,TypeReference[]>
    popularTRVs = new WeakHashMap<TypeReferenceVector,TypeReference[]>();

  private void adjustLength(int newLength) {
    if (newLength == 0) {
      array = empty;
    } else {
      TypeReference[] newElements = new TypeReference[newLength];
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
