/*
 * (C) Copyright IBM Corp. 2001
 */
//$Id$

package com.ibm.JikesRVM.memoryManagers.vmInterface;

import com.ibm.JikesRVM.VM;
import com.ibm.JikesRVM.VM_Address;
import com.ibm.JikesRVM.VM_PragmaUninterruptible;
import com.ibm.JikesRVM.VM_Uninterruptible;

/*
 * @author Perry Cheng  
 */  

final public class AddressSet implements VM_Uninterruptible {

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
    if (VM.VerifyAssertions) {
      VM._assert(!addr.isZero());
      if (cursor >= address.length)  
	VM.sysFail("AddressSet overflowed"); 
    }
    address[cursor++] = addr.toInt(); 
  }

  public VM_Address pop() {
    if (cursor == 0)
      return VM_Address.zero();
    return VM_Address.fromInt(address[--cursor]);
  }

  public int size() {
    return cursor;
  }

}
