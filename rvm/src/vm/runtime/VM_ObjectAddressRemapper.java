/*
 * (C) Copyright IBM Corp. 2001
 */
//$Id$
package com.ibm.JikesRVM;

import org.vmmagic.unboxed.*;

/**
 * Facility for remapping object addresses across virtual machine address 
 * spaces.  Used by boot image writer to map local (jdk) objects into remote 
 * (boot image) addresses.  Used by debugger to map local (jdk) objects into 
 * remote (debugee vm) addresses.
 *
 * See also VM_Magic.setObjectAddressRemapper()
 *
 * @author Bowen Alpern
 * @author Derek Lieber
 */
public interface VM_ObjectAddressRemapper
   {
   // Map an object to an address.
   // Taken:    an object in "local" virtual machine
   // Returned: its address in a foreign virtual machine
   //
   public Address objectAsAddress(Object object);

   // Map an address to an object.
   // Taken:    value obtained from "objectAsAddress"
   // Returned: corresponding object
   //
   public Object addressAsObject(Address address);
   }
