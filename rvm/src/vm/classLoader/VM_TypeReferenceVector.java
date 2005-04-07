/*
 * (C) Copyright IBM Corp. 2001,2005
 */
//$Id$
package com.ibm.JikesRVM.classloader;

/**
 *  Lightweight implementation of a vector of VM_Fields.
 * 
 * @author Derek Lieber
 */
final class VM_TypeReferenceVector {
  //-----------//
  // interface //
  //-----------//
   
  public VM_TypeReferenceVector() {
    array = new VM_TypeReference[10];
  }
      
  // Add item.
  //
  final void addElement(VM_TypeReference item) {
    if (cnt == array.length)
      adjustLength(cnt << 1); // double size of array
    array[cnt++] = item;
  }

  // Add item if it is not already in the Vector.
  // 
  public final void addUniqueElement(VM_TypeReference item) {
    for (int i=0; i<cnt; i++) {
      if (array[i] == item) return;
    }
    addElement(item);
  }

  // Get item.
  //
  final VM_TypeReference elementAt(int index) {
    return array[index];
  }

  // Set item.
  //
  final void setElementAt(VM_TypeReference item, int index) {
    array[index] = item;
  }

  // Get number of items added so far.
  //
  public final int size() {
    return cnt;
  }

  // Get array, trimmed to size.
  //
  public final VM_TypeReference[] finish() {
    adjustLength(cnt);
    return array;
  }

  //----------------//
  // implementation //
  //----------------//

  private VM_TypeReference[] array;
  private int   cnt;

  private static final VM_TypeReference[] empty = new VM_TypeReference[0];	
  
  private final void adjustLength(int newLength) {
    if (newLength == 0) {
      array = empty;
    } else {
      VM_TypeReference[] newElements = new VM_TypeReference[newLength];
      int n = array.length;
      if (n > newLength)
        n = newLength;
         
      for (int i = 0; i < n; ++i)
        newElements[i] = array[i];

      array = newElements;
    }
  }
}
