/*
 * (C) Copyright IBM Corp. 2001
 */
//BootImageWriterConstants.java
//$Id$

/**
 * Manifest constants for bootimage writer.
 *
 * @author Derek Lieber
 * @version 03 Jan 2000
 */
public interface BootImageWriterConstants extends VM_Constants {
  /**
   * Address at which image will be loaded when it runs
   */
  public static final int IMAGE_ADDRESS = 0x30000000;

  /**
   * Maximum size image that we can write (in bytes)
   */
  public static final int IMAGE_SIZE = 40 * 1024 * 1024;

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

  /**
   * for RCGC: reference count field colored red (6) to prevent RC updates
   */
  public static final int BOOTIMAGE_REFCOUNT = (6 << 28) | 1;
}

