/*
 * (C) Copyright IBM Corp. 2001
 */
//$Id$

package com.ibm.JikesRVM.memoryManagers.watson;

import com.ibm.JikesRVM.VM;
import com.ibm.JikesRVM.VM_Address;
import com.ibm.JikesRVM.VM_EventLogger;
import com.ibm.JikesRVM.VM_BootRecord;
import com.ibm.JikesRVM.VM_PragmaUninterruptible;

/**
 * @author Perry Cheng  
 */  
public class AddressSet {

  // Deficiency in compiler prevents use of VM_Address []
  private int [] address;
  private int cursor;

  public AddressSet(int size) { 
    address = new int[size];
  }

  public void clear() { 
    cursor = 0; 
  }

  public boolean isEmpty() {
    return cursor == 0;
  }

  public void push(VM_Address addr) { 
    if (VM.VerifyAssertions) VM._assert(!addr.isZero());
    if (VM.VerifyAssertions) VM._assert(cursor < address.length);
    address[cursor++] = addr.toInt(); 
  }

  public VM_Address pop() {
    if (cursor == 0)
      return VM_Address.zero();
    return VM_Address.fromInt(address[--cursor]);
  }

}
