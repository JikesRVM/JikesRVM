/*
 * (C) Copyright IBM Corp. 2001
 */
//$Id$

package com.ibm.JikesRVM.memoryManagers.vmInterface;

import com.ibm.JikesRVM.VM;
import com.ibm.JikesRVM.VM_Constants;
import com.ibm.JikesRVM.VM_SizeConstants;
import com.ibm.JikesRVM.VM_Address;
import com.ibm.JikesRVM.VM_JavaHeader;

/**
 * JMTk follows the pattern set by Jikes RVM for defining sizes of
 * primitive types thus:
 *
 *  static final int LOG_BYTES_IN_INT = 2;
 *  static final int BYTES_IN_INT = 1<<LOG_BYTES_IN_INT;
 *  static final int LOG_BITS_IN_INT = LOG_BITS_IN_BYTE + LOG_BYTES_IN_INT;
 *  static final int BITS_IN_INT = 1<<LOG_BITS_IN_INT;
 *
 * In this case, we simply extend VM_SizeConstants, which has already
 * defined all such constants.  This is in fact a necessity becuase of
 * the wierdness of VM_Processor *extending* Plan (and therefore
 * implementing both Constants and VM_SizeConstants, and thus being
 * exposed to potential duplication of constants).
 *
 * @author Perry Cheng
 */
public interface Constants extends VM_SizeConstants {

  static final int MAX_INT = 0x7fffffff;

  static final int LOG_BYTES_IN_MBYTE = 20;
  static final int BYTES_IN_MBYTE = 1 << LOG_BYTES_IN_MBYTE;

  static final int LOG_BYTES_IN_PAGE = 12;
  static final int BYTES_IN_PAGE = 1 << LOG_BYTES_IN_PAGE;

  /* Assume an address refers to a byte */
  static final int LOG_BYTES_IN_ADDRESS_SPACE = BITS_IN_ADDRESS;
}

