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

final public class VM_AddressArray implements VM_Uninterruptible {

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

  public VM_Address get (int index) throws VM_PragmaInline {
    //-#if RVM_FOR_32_ADDR
    if (VM.VerifyAssertions)
	return VM_Address.fromInt(data[index]);
    else
	return VM_Address.fromInt(VM_Magic.getMemoryInt(VM_Magic.objectAsAddress(data).add(index << 2)));
    //-#elif RVM_FOR_64_ADDR
    return VM_Address.fromLong(data[index]);
    //-#endif
  }

  public void set (int index, VM_Address v) throws VM_PragmaInline {
    //-#if RVM_FOR_32_ADDR
    if (VM.VerifyAssertions)
	data[index] = v.toInt();
    else
	VM_Magic.setMemoryInt(VM_Magic.objectAsAddress(data).add(index << 2), v.toInt());
    //-#elif RVM_FOR_64_ADDR
    data[index] = v.toLong();
    //-#endif
  }

  public int length() throws VM_PragmaInline {
    return data.length;
  }

}
