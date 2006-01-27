/*
 * (C) Copyright IBM Corp. 2001
 */
//BootImageWriterConstants.java
//$Id$

import com.ibm.JikesRVM.*;
import org.vmmagic.unboxed.Address;

/**
 * Manifest constants for bootimage writer.
 *
 * @author Derek Lieber
 * @version 03 Jan 2000
 */
public interface BootImageWriterConstants extends VM_Constants {

  /**
   * Address to associate with objects that haven't yet been placed into image.
   * Any Address that's unaligned will do.
   */
  public static final Address OBJECT_NOT_ALLOCATED = Address.fromIntSignExtend(0xeeeeeee1);

  /**
   * Address to associate with objects that are not to be placed into image.
   * Any Address that's unaligned will do.
   */
  public static final Address OBJECT_NOT_PRESENT = Address.fromIntSignExtend(0xeeeeeee2);

  /**
   * Starting index for objects in VM_TypeDictionary.
   * = 1, since slot 0 is reserved for null
   */
  public static final int FIRST_TYPE_DICTIONARY_INDEX = 1;
}

