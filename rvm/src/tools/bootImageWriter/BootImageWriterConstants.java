/*
 * (C) Copyright IBM Corp. 2001
 */
//BootImageWriterConstants.java
//$Id$

import com.ibm.JikesRVM.*;

/**
 * Manifest constants for bootimage writer.
 *
 * @author Derek Lieber
 * @version 03 Jan 2000
 */
public interface BootImageWriterConstants extends VM_Constants {

  /**
   * Maximum size image that we can write (in bytes)
   */
  public static final int IMAGE_SIZE = 60 * 1024 * 1024;

  /**
   * Offset to associate with objects that haven't yet been placed into image.
   * Any offset that's outside image will do.
   */
  public static final int OBJECT_NOT_ALLOCATED = 0xeeeeeee1;

  /**
   * Offset to associate with objects that are not to be placed into image.
   * Any offset that's outside image will do.
   */
  public static final int OBJECT_NOT_PRESENT = 0xeeeeeee2;

  /**
   * Starting index for objects in VM_TypeDictionary.
   * = 1, since slot 0 is reserved for null
   */
  public static final int FIRST_TYPE_DICTIONARY_INDEX = 1;
}

