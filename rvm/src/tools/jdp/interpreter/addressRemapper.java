/*
 * (C) Copyright IBM Corp. 2001
 */
//$Id$
import com.ibm.JikesRVM.*;
/**
 * This class captures the type conversion between address and object reference
 * The RVM stores all statics as integer in the JTOC;  it uses this type 
 * conversion to go between int type and object reference type.
 * The RVM runtime uses VM_Magic to do this type conversion.  This 
 * works since the compiler actually hijacks the call and generates direct 
 * machine code to get around the Java type safety.
 * However, under the JDK, this scheme is not available and the strict type
 * safety does not allow casting from int to object.
 * <p>
 * The purpose of this class is to decouple the integer and object reference
 * into two separate arrays so that we can use the JTOC under the JDK.
 * However, unlike the InterpreterStack which keeps the arrays coindexed, 
 * we cannot use the same index for our two arrays because we need to stay
 * compatible with the RVM scheme for remapping address (when the RVM picks
 * up a JTOC entry that is a reference, it will call addressAsObject() to 
 * do the type conversion).
 * Instead, the object will be inserted into the object array kept in this
 * class in sequential order and the index will be stored in the JTOC entry
 * for this object.  
 * Then the addressAsObject() and objectAsAddress() methods will provide the
 * necessary access interface.
 *
 * @author Ton Ngo, 7/31/98
 */

class addressRemapper implements VM_ObjectAddressRemapper {
  private static Object slots[];
  private int index; 

  /**
   * A fixed size array is used to hold the objects
   * Currently the JTOC does not grow either when it fills up
   * This will have to change eventually, watch to make sure that 
   * when the JTOC change, this will be changed also.
   */
  public addressRemapper() {
    slots        = new Object[16384];  
    index = 0;
  }

  /**
   * Take an object, insert into the next slot in private object array here
   * return the index as a key to retrieve this object
   * During class loading, the classloader will be filling the jtoc 
   * and will invoke this method to convert object reference to address
   * to store in the jtoc.  This implementation will intercept the
   * type conversion to store the object away in a hash table and
   * return a key that will be used later to retrieve the object
   */
  public VM_Address objectAsAddress(Object object)
  {
    index++;
    slots[index] = object;
    return VM_Address.fromInt(index);
  }

  /**
   * Take an index, retrieve and return the object saved in the
   * object array.  When a jtoc entry is accessed and it contains
   * an object reference, this method is used to convert it back 
   * a legal Java object.  The address is used as the index to
   * retrieve the object from the object array kept here.
   */
  public Object addressAsObject(VM_Address address)
  {
    return slots[address.toInt()];
  }

}
