/*
 * (C) Copyright IBM Corp 2001,2002
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

  static final int LOG_BYTES_IN_BYTE = 0;
  static final int BYTES_IN_BYTE = 1;
  static final int LOG_BITS_IN_BYTE = 3;
  static final int BITS_IN_BYTE = 1<<LOG_BITS_IN_BYTE;
    
  static final int LOG_BYTES_IN_BOOLEAN = 0;
  static final int BYTES_IN_BOOLEAN = 1<<LOG_BYTES_IN_BOOLEAN;
  static final int LOG_BITS_IN_BOOLEAN = LOG_BITS_IN_BYTE + LOG_BYTES_IN_BOOLEAN;
  static final int BITS_IN_BOOLEAN = 1<<LOG_BITS_IN_BOOLEAN;
    
  static final int LOG_BYTES_IN_CHAR = 1;
  static final int BYTES_IN_CHAR = 1<<LOG_BYTES_IN_CHAR;
  static final int LOG_BITS_IN_CHAR = LOG_BITS_IN_BYTE + LOG_BYTES_IN_CHAR;
  static final int BITS_IN_CHAR = 1<<LOG_BITS_IN_CHAR;
    
  static final int LOG_BYTES_IN_SHORT = 1;
  static final int BYTES_IN_SHORT = 1<<LOG_BYTES_IN_SHORT;
  static final int LOG_BITS_IN_SHORT = LOG_BITS_IN_BYTE + LOG_BYTES_IN_SHORT;
  static final int BITS_IN_SHORT = 1<<LOG_BITS_IN_SHORT;

  static final int LOG_BYTES_IN_INT = 2;
  static final int BYTES_IN_INT = 1<<LOG_BYTES_IN_INT;
  static final int LOG_BITS_IN_INT = LOG_BITS_IN_BYTE + LOG_BYTES_IN_INT;
  static final int BITS_IN_INT = 1<<LOG_BITS_IN_INT;
    
  static final int LOG_BYTES_IN_FLOAT = 2;
  static final int BYTES_IN_FLOAT = 1<<LOG_BYTES_IN_FLOAT;
  static final int LOG_BITS_IN_FLOAT = LOG_BITS_IN_BYTE + LOG_BYTES_IN_FLOAT;
  static final int BITS_IN_FLOAT = 1<<LOG_BITS_IN_FLOAT;
    
  static final int LOG_BYTES_IN_LONG = 3;
  static final int BYTES_IN_LONG = 1<<LOG_BYTES_IN_LONG;
  static final int LOG_BITS_IN_LONG = LOG_BITS_IN_BYTE + LOG_BYTES_IN_LONG;
  static final int BITS_IN_LONG = 1<<LOG_BITS_IN_LONG;
    
  static final int LOG_BYTES_IN_DOUBLE = 3;
  static final int BYTES_IN_DOUBLE = 1<<LOG_BYTES_IN_DOUBLE;
  static final int LOG_BITS_IN_DOUBLE = LOG_BITS_IN_BYTE + LOG_BYTES_IN_DOUBLE;
  static final int BITS_IN_DOUBLE = 1<<LOG_BITS_IN_DOUBLE;

  static final int LOG_BYTES_IN_ADDRESS = VM.BuildFor64Addr ? 3 : 2;
  static final int BYTES_IN_ADDRESS = 1<<LOG_BYTES_IN_ADDRESS;
  static final int LOG_BITS_IN_ADDRESS = LOG_BITS_IN_BYTE + LOG_BYTES_IN_ADDRESS;
  static final int BITS_IN_ADDRESS = 1<<LOG_BITS_IN_ADDRESS;

  static final int LOG_BYTES_IN_WORD = VM.BuildFor64Addr ? 3 : 2;
  static final int BYTES_IN_WORD = 1<<LOG_BYTES_IN_WORD;
  static final int LOG_BITS_IN_WORD = LOG_BITS_IN_BYTE + LOG_BYTES_IN_WORD;
  static final int BITS_IN_WORD = 1<<LOG_BITS_IN_WORD;
}
