/*
 * This file is part of Jikes RVM (http://jikesrvm.sourceforge.net).
 * The Jikes RVM project is distributed under the Common Public License (CPL).
 * A copy of the license is included in the distribution, and is also
 * available at http://www.opensource.org/licenses/cpl1.0.php
 *
 * (C) Copyright Peter Donald. 2007
 */
package com.ibm.jikesrvm;

/**
 * Utility class to get at implementation of SysCall interface.
 */
public class VM_SysCallUtil {
  @SuppressWarnings({"unchecked"})
  public static <T> T getImplementation(Class<T> type) {
    try {
      return (T) Class.forName(type.getName() + "Impl").newInstance();
    } catch (Exception e) {
      e.printStackTrace();
      VM.sysFail("Error creating generated implementation of " + type.getName());
      return null;
    }
  }

}
