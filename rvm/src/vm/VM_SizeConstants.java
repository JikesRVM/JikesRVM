/*
 * (C) Copyright IBM Corp. 2003
 */
//$Id$

package com.ibm.JikesRVM;

/**
 * Constants defining the basic sizes of primitive quantities
 *
 * @author David F. Bacon
 * @author Perry Cheng
 * @author Kris Venstermans
 */
public interface VM_SizeConstants {

  public static final int LOG_BITS_IN_BYTE = 3;
  public static final int BITS_IN_BYTE = 1<<LOG_BITS_IN_BYTE;

  public static final int LOG_BYTES_IN_INT = 2;
  public static final int BYTES_IN_INT = 1<<LOG_BYTES_IN_INT;
  public static final int LOG_BITS_IN_INT = LOG_BITS_IN_BYTE + LOG_BYTES_IN_INT;
  public static final int BITS_IN_INT = 1<<LOG_BITS_IN_INT;

  public static final int LOG_BYTES_IN_ADDRESS = VM.BuildFor64Bit ? 3 : 2;
  public static final int BYTES_IN_ADDRESS = 1<<LOG_BYTES_IN_ADDRESS;
  public static final int LOG_BITS_IN_ADDRESS = LOG_BITS_IN_BYTE + LOG_BYTES_IN_ADDRESS;
  public static final int BITS_IN_ADDRESS = 1<<LOG_BITS_IN_ADDRESS;
}
