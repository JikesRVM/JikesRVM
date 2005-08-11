/*
 * (C) Copyright Department of Computer Science,
 * Australian National University. 2004
 *
 * (C) Copyright IBM Corp. 2001, 2003
 */
package org.mmtk.vm;

import com.ibm.JikesRVM.VM;
import com.ibm.JikesRVM.VM_CommandLineArgs;
import com.ibm.JikesRVM.VM_Processor;

import org.vmmagic.unboxed.*;
import org.vmmagic.pragma.*;

/**
 * $Id$ 
 *
 * @author <a href="http://cs.anu.edu.au/~Steve.Blackburn">Steve Blackburn</a>
 * @author Perry Cheng
 *
 * @version $Revision$
 * @date $Date$
 */
public class Strings implements Uninterruptible {
  /**
   * Primitive parsing facilities for strings
   */
  public static int parseInt(String value) throws InterruptiblePragma {
    return VM_CommandLineArgs.primitiveParseInt(value);
  }

  public static float parseFloat(String value) throws InterruptiblePragma {
    return VM_CommandLineArgs.primitiveParseFloat(value);
  }

  /**
   * Log a message.
   *
   * @param c character array with message starting at index 0
   * @param len number of characters in message
   */
  public static void write(char [] c, int len) {
    VM.sysWrite(c, len);
  }

  /**
   * Log a thread identifier and a message.
   *
   * @param c character array with message starting at index 0
   * @param len number of characters in message
   */
  public static void writeThreadId(char [] c, int len) {
    VM.psysWrite(c, len);
  }


  /**
   * Copies characters from the string into the character array.
   * Thread switching is disabled during this method's execution.
   * <p>
   * <b>TODO:</b> There are special memory management semantics here that
   * someone should document.
   *
   * @param src the source string
   * @param dst the destination array
   * @param dstBegin the start offset in the desination array
   * @param dstEnd the index after the last character in the
   * destination to copy to
   * @return the number of characters copied.
   */
  public static int copyStringToChars(String src, char [] dst,
                                      int dstBegin, int dstEnd)
    throws LogicallyUninterruptiblePragma {
    if (Assert.runningVM())
      VM_Processor.getCurrentProcessor().disableThreadSwitching();
    int len = src.length();
    int n = (dstBegin + len <= dstEnd) ? len : (dstEnd - dstBegin);
    for (int i = 0; i < n; i++) 
      Barriers.setArrayNoBarrier(dst, dstBegin + i, src.charAt(i));
    if (Assert.runningVM())
      VM_Processor.getCurrentProcessor().enableThreadSwitching();
    return n;
  }
}
