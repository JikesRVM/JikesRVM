/*
 * (C) Copyright Department of Computer Science,
 * Australian National University. 2005
 *
 * (C) Copyright IBM Corp. 2001
 */

package org.mmtk.utility;

import org.mmtk.vm.VMConstants;
import org.mmtk.vm.Assert;

/**
 * MMTk follows the pattern set by Jikes RVM for defining sizes of
 * primitive types thus:
 *
 *  static final int LOG_BYTES_IN_INT = 2;
 *  static final int BYTES_IN_INT = 1<<LOG_BYTES_IN_INT;
 *  static final int LOG_BITS_IN_INT = LOG_BITS_IN_BYTE + LOG_BYTES_IN_INT;
 *  static final int BITS_IN_INT = 1<<LOG_BITS_IN_INT;
 *
 * $Id$
 *
 * @author Perry Cheng
 * @author <a href="http://cs.anu.edu.au/~Steve.Blackburn">Steve Blackburn</a>
 * @version $Revision$
 * @date $Date$
 */
public interface Constants {

  /****************************************************************************
   *
   * MMTk constants
   */
  static final int PUTFIELD_WRITE_BARRIER = 0;
  static final int GETFIELD_READ_BARRIER = 0;
  static final int PUTSTATIC_WRITE_BARRIER = 1;
  static final int GETSTATIC_READ_BARRIER = 1;
  static final int AASTORE_WRITE_BARRIER = 2;
  static final int AALOAD_READ_BARRIER = 2;


  /****************************************************************************
   *
   * Generic sizes
   */
  static final byte LOG_BYTES_IN_BYTE = 0;
  static final int BYTES_IN_BYTE = 1;
  static final byte LOG_BITS_IN_BYTE = 3;
  static final int BITS_IN_BYTE = 1<<LOG_BITS_IN_BYTE;

  static final byte LOG_BYTES_IN_MBYTE = 20;
  static final int BYTES_IN_MBYTE = 1<< LOG_BYTES_IN_MBYTE;

  static final byte LOG_BYTES_IN_KBYTE = 10;
  static final int BYTES_IN_KBYTE = 1<< LOG_BYTES_IN_KBYTE;

  /****************************************************************************
   *
   * Java-specific sizes currently required by MMTk
   *
   * TODO MMTk should really become independant of these Java types
   */
  static final byte LOG_BYTES_IN_SHORT = 1;
  static final int BYTES_IN_SHORT = 1<<LOG_BYTES_IN_SHORT;
  static final byte LOG_BITS_IN_SHORT = LOG_BITS_IN_BYTE + LOG_BYTES_IN_SHORT;
  static final int BITS_IN_SHORT = 1<<LOG_BITS_IN_SHORT;
  
  static final byte LOG_BYTES_IN_INT = 2;
  static final int BYTES_IN_INT = 1<<LOG_BYTES_IN_INT;
  static final byte LOG_BITS_IN_INT = LOG_BITS_IN_BYTE + LOG_BYTES_IN_INT;
  static final int BITS_IN_INT = 1<<LOG_BITS_IN_INT;

  static final int MAX_INT = 0x7fffffff;
  static final int MIN_INT = 0x80000000;

  /****************************************************************************
   *
   * VM-Specific sizes
   */
  static final byte LOG_BYTES_IN_ADDRESS = VMConstants.LOG_BYTES_IN_ADDRESS();
  static final int BYTES_IN_ADDRESS = 1<<LOG_BYTES_IN_ADDRESS;
  static final int LOG_BITS_IN_ADDRESS = LOG_BITS_IN_BYTE + LOG_BYTES_IN_ADDRESS;
  static final int BITS_IN_ADDRESS = 1<<LOG_BITS_IN_ADDRESS;

  // Note that in MMTk we currently define WORD & ADDRESS to be the same size
  static final byte LOG_BYTES_IN_WORD = LOG_BYTES_IN_ADDRESS;
  static final int BYTES_IN_WORD = 1<<LOG_BYTES_IN_WORD;
  static final int LOG_BITS_IN_WORD = LOG_BITS_IN_BYTE + LOG_BYTES_IN_WORD;
  static final int BITS_IN_WORD = 1<<LOG_BITS_IN_WORD;

  static final byte LOG_BYTES_IN_PAGE = VMConstants.LOG_BYTES_IN_PAGE();
  static final int BYTES_IN_PAGE = 1<< LOG_BYTES_IN_PAGE;
  static final int LOG_BITS_IN_PAGE = LOG_BITS_IN_BYTE + LOG_BYTES_IN_PAGE;
  static final int BITS_IN_PAGE = 1<<LOG_BITS_IN_PAGE;

  /* Assume byte-addressability */
  static final byte LOG_BYTES_IN_ADDRESS_SPACE = (byte) BITS_IN_ADDRESS;

  /**
   * This value specifies the <i>minimum</i> allocation alignment
   * requirement of the VM.  When making allocation requests, both
   * <code>align</code> and <code>offset</code> must be multiples of
   * <code>MIN_ALIGNMENT</code>.
   *
   * This value is required to be a power of 2.
   */
  static final byte LOG_MIN_ALIGNMENT = VMConstants.LOG_MIN_ALIGNMENT();
  static final int MIN_ALIGNMENT = 1<<LOG_MIN_ALIGNMENT;

  /**
   * The maximum alignment request the vm will make. This must be a 
   * power of two multiple of the minimum alignment.
   */
  static final int MAX_ALIGNMENT = MIN_ALIGNMENT<<VMConstants.MAX_ALIGNMENT_SHIFT(); 
  
  /**
   * The VM will add at most this value minus BYTES_IN_INT bytes of
   * padding to the front of an object that it places in a region of
   * memory. This value must be a power of 2.
   */
  static final int MAX_BYTES_PADDING = VMConstants.MAX_BYTES_PADDING();
}
