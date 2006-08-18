/*
 * (C) Copyright Department of Computer Science,
 * Australian National University. 2004
 *
 * (C) Copyright IBM Corp. 2001, 2003
 */
package org.mmtk.vm;

import org.vmmagic.pragma.Uninterruptible;


/**
 * $Id: Strings.java,v 1.5 2006/06/21 07:38:13 steveb-oss Exp $ 
 * 
 * @author Steve Blackburn
 * @author Perry Cheng
 * 
 * @version $Revision: 1.5 $
 * @date $Date: 2006/06/21 07:38:13 $
 */
public abstract class Strings implements Uninterruptible {
  /**
   * Log a message.
   * 
   * @param c character array with message starting at index 0
   * @param len number of characters in message
   */
  public abstract void write(char [] c, int len);
  
  /**
   * Log a thread identifier and a message.
   * 
   * @param c character array with message starting at index 0
   * @param len number of characters in message
   */
  public abstract void writeThreadId(char [] c, int len);
  
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
  public abstract int copyStringToChars(String src, char [] dst,
      int dstBegin, int dstEnd);
}
