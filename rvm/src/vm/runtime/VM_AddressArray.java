/*
 * (C) Copyright IBM Corp. 2001
 */
//$Id$
package com.ibm.JikesRVM;

/**
 * The VM front end is not capable of correct handling an array of VM_Address, VM_Word, ....
 * For now, we provide special types to handle these situations.
 *
 * @author Perry Cheng
 */

final public class VM_AddressArray {

  //-#if RVM_FOR_32_ADDR
  private int [] data;
  //-#elif RVM_FOR_64_ADDR
  private long [] data;
  //-#endif

  public VM_AddressArray (int size) {
    //-#if RVM_FOR_32_ADDR
    data = new int[size];
    //-#elif RVM_FOR_64_ADDR
    data = new long[size];
    //-#endif
  }

  public VM_Address get (int index) {
    //-#if RVM_FOR_32_ADDR
    return VM_Address.fromInt(data[index]);
    //-#elif RVM_FOR_64_ADDR
    return VM_Address.fromLong(data[index]);
    //-#endif
  }

  public void set (int index, VM_Address v) {
    //-#if RVM_FOR_32_ADDR
    data[index] = v.toInt();
    //-#elif RVM_FOR_64_ADDR
    data[index] = v.toLong();
    //-#endif
  }

  public int length() {
    return data.length;
  }

}
