/*
 *  This file is part of the Jikes RVM project (http://jikesrvm.org).
 *
 *  This file is licensed to You under the Eclipse Public License (EPL);
 *  You may not use this file except in compliance with the License. You
 *  may obtain a copy of the License at
 *
 *      http://www.opensource.org/licenses/eclipse-1.0.php
 *
 *  See the COPYRIGHT.txt file distributed with this work for information
 *  regarding copyright ownership.
 */
package org.mmtk.utility;

import org.mmtk.vm.VM;

import org.vmmagic.unboxed.*;
import org.vmmagic.pragma.*;

/**
 * Error and trace logging.
 */
@Uninterruptible
public class Log implements Constants {

  /****************************************************************************
   *
   * Class variables
   */

  /**
   * characters in the write buffer for the caller's message.  This
   * does not include characters reserved for the overflow message.
   *
   * This needs to be large because Jikes RVM's implementation of Lock.java
   * logs a lot of information when there is potential GC deadlock.
   */
  private static final int MESSAGE_BUFFER_SIZE = 3000;

  /** message added when the write buffer has overflown */
  private static final String OVERFLOW_MESSAGE = "... WARNING: Text truncated.\n";

  private static final char OVERFLOW_MESSAGE_FIRST_CHAR = OVERFLOW_MESSAGE.charAt(0);

  /** characters in the overflow message, including the (optional) final
   * newline  */
  private static final int OVERFLOW_SIZE = OVERFLOW_MESSAGE.length();

  /**
   * characters in buffer for building string representations of
   * longs.  A long is a signed 64-bit integer in the range -2^63 to
   * 2^63+1.  The number of digits in the decimal representation of
   * 2^63 is ceiling(log10(2^63)) == ceiling(63 * log10(2)) == 19.  An
   * extra character may be required for a minus sign (-).  So the
   * maximum number of characters is 20.
   */
  private static final int TEMP_BUFFER_SIZE = 20;

  /** string that prefixes numbers logged in hexadecimal */
  private static final String HEX_PREFIX = "0x";

  /**
   * log2 of number of bits represented by a single hexidemimal digit
   */
  private static final int LOG_BITS_IN_HEX_DIGIT = 2;

  /**
   * log2 of number of digits in the unsigned hexadecimal
   * representation of a byte
   */
  private static final int LOG_HEX_DIGITS_IN_BYTE = LOG_BITS_IN_BYTE - LOG_BITS_IN_HEX_DIGIT;

  /**
   * map of hexadecimal digit values to their character representations
   */
  private static final char [] hexDigitCharacter =
  { '0', '1', '2', '3', '4', '5', '6', '7', '8', '9', 'a', 'b', 'c', 'd', 'e', 'f' };

  /** new line character. Emitted by writeln methods. */
  private static final char NEW_LINE_CHAR = '\n';

  /** log instance used at build time. */
  private static Log log = new Log();

  /****************************************************************************
   *
   * Instance variables
   */

  /** buffer to store written message until flushing */
  private char [] buffer = new char[MESSAGE_BUFFER_SIZE + OVERFLOW_SIZE];

  /** location of next character to be written */
  private int bufferIndex = 0;

  /** <code>true</code> if the buffer has overflown */
  private boolean overflow = false;

  /** The last character that was written by #addToBuffer(char).  This is
      used to check whether we want to newline-terminate the text. */
  private char overflowLastChar = '\0';

  /** <code>true</code> if a thread id will be prepended */
  private boolean threadIdFlag = false;

  /** buffer for building string representations of longs */
  private char[] tempBuffer = new char[TEMP_BUFFER_SIZE];

  /** constructor */
  public Log() {
    for (int i = 0; i < OVERFLOW_SIZE; i++) {
      buffer[MESSAGE_BUFFER_SIZE + i] = OVERFLOW_MESSAGE.charAt(i);
    }
  }

  /**
   * writes a boolean. Either "true" or "false" is logged.
   *
   * @param b boolean value to be logged.
   */
  public static void write(boolean b) {
    write(b ? "true" : "false");
  }

  /**
   * writes a character
   *
   * @param c character to be logged
   */
  public static void write(char c) {
    add(c);
  }

  /**
   * writes a long, in decimal.  The value is not padded and no
   * thousands seperator is logged.  If the value is negative a
   * leading minus sign (-) is logged.
   *
   *
   * @param l long value to be logged
   */
  public static void write(long l) {
    boolean negative = l < 0;
    int nextDigit;
    char nextChar;
    int index = TEMP_BUFFER_SIZE - 1;
    char[] intBuffer = getIntBuffer();

    nextDigit = (int) (l % 10);
    nextChar = hexDigitCharacter[negative ? - nextDigit : nextDigit];
    intBuffer[index--] = nextChar;
    l = l / 10;

    while (l != 0) {
      nextDigit = (int) (l % 10);
      nextChar = hexDigitCharacter[negative ? - nextDigit : nextDigit];
      intBuffer[index--] = nextChar;
      l = l / 10;
    }

    if (negative) {
      intBuffer[index--] = '-';
    }

    for (index++; index < TEMP_BUFFER_SIZE; index++) {
      add(intBuffer[index]);
    }
  }

  /**
   * writes a <code>double</code>.  Two digits after the decimal point
   * are always logged.  The value is not padded and no thousands
   * seperator is used.  If the value is negative a leading
   * hyphen-minus (-) is logged.  The decimal point is a full stop
   * (.).
   *
   * @param d the double to be logged
   */
  public static void write(double d) { write(d, 2); }

  /**
   * writes a <code>double</code>.  The number of digits after the
   * decimal point is determined by <code>postDecimalDigits</code>.
   * The value is not padded and not thousands seperator is used. If
   * the value is negative a leading hyphen-minus (-) is logged.  The
   * decimal point is a full stop (.) and is logged even if
   * <postDecimcalDigits</code> is zero. If <code>d</code> is greater
   * than the largest representable value of type <code>int</code>, it
   * is logged as "TooBig".  Similarly, if it is less than
   * the negative of the largest representable value, it is logged as
   * "TooSmall".  If <code>d</code> is NaN is is logged as "NaN".
   *
   * @param d the double to be logged
   * @param postDecimalDigits the number of digits to be logged after
   * the decimal point.  If less than or equal to zero no digits are
   * logged, but the decimal point is.
   */
  public static void write(double d, int postDecimalDigits) {
    if (d != d) {
      write("NaN");
      return;
    }
    if (d > Integer.MAX_VALUE) {
      write("TooBig");
      return;
    }
    if (d < -Integer.MAX_VALUE) {
      write("TooSmall");
      return;
    }

    boolean negative = (d < 0.0);
    d = negative ? (-d) : d;       // Take absolute value
    int ones = (int) d;
    int multiplier = 1;
    while (postDecimalDigits-- > 0)
      multiplier *= 10;
    int remainder = (int) (multiplier * (d - ones));
    if (remainder < 0) remainder = 0;
    if (negative) write('-');
    write(ones);
    write('.');
    while (multiplier > 1) {
      multiplier /= 10;
      write(remainder / multiplier);
      remainder %= multiplier;
    }
  }

  /**
   * writes an array of characters
   *
   * @param c the array of characters to be logged
   */
  public static void write(char[] c) {
    write(c, c.length);
  }

  /**
   * writes the start of an array of characters
   *
   * @param c the array of characters
   * @param len the number of characters to be logged, starting with
   * the first character
   */
  public static void write(char[] c, int len) {
    if (VM.VERIFY_ASSERTIONS) VM.assertions._assert(len <= c.length);
    for (int i = 0; i < len; i++) {
      add(c[i]);
    }
  }

  /**
   * writes an array of bytes.  The bytes are interpretted
   * as characters.
   *
   * @param b the array of bytes to be logged
   */
  public static void write(byte[] b) {
    for (int i = 0; i < b.length; i++) {
      add((char)b[i]);
    }
  }

  /**
   * writes a string
   *
   * @param s the string to be logged
   */
  public static void write(String s) {
    add(s);
  }

  /**
   * writes a word, in hexadecimal.  It is zero-padded to the size of
   * an address.
   *
   * @param w the word to be logged
   */
  public static void write(Word w) {
    writeHex(w, BYTES_IN_ADDRESS);
  }

  /**
   * writes a word, in decimal.
   *
   * @param w the word to be logged
   */
  public static void writeDec(Word w) {
    if (BYTES_IN_ADDRESS == 4) {
      write(w.toInt());
    } else {
      write(w.toLong());
    }
  }

  /**
   * writes an address, in hexademical. It is zero-padded.
   *
   * @param a the address to be logged
   */
  public static void write(Address a) {
    writeHex(a.toWord(), BYTES_IN_ADDRESS);
  }

  /**
   * writes a string followed by an address, in hexademical.
   * @see #write(String)
   * @see #write(Address)
   *
   * @param s the string to be logged
   * @param a the address to be logged
   */
  public static void write(String s, Address a) {
    write(s);
    write(a);
  }

  /**
   * Write a string followed by a long
   * @see #write(String)
   * @see #write(long)
   *
   * @param s the string to be logged
   * @param l the long to be logged
   */
  public static void write(String s, long l) {
    write(s);
    write(l);
  }

  /**
   * writes an object reference, in hexademical. It is zero-padded.
   *
   * @param o the object reference to be logged
   */
  public static void write(ObjectReference o) {
    writeHex(o.toAddress().toWord(), BYTES_IN_ADDRESS);
  }

  /**
   * writes an offset, in hexademical. It is zero-padded.
   *
   * @param o the offset to be logged
   */
  public static void write(Offset o) {
    writeHex(o.toWord(), BYTES_IN_ADDRESS);
  }

  /**
   * writes an extent, in hexademical. It is zero-padded.
   *
   * @param e the extent to be logged
   */
  public static void write(Extent e) {
    writeHex(e.toWord(), BYTES_IN_ADDRESS);
  }

  /**
   * write a new-line and flushes the buffer
   */
  public static void writeln() {
    writelnWithFlush(true);
  }

  /**
   * writes a boolean and a new-line, then flushes the buffer.
   * @see #write(boolean)
   *
   * @param b boolean value to be logged.
   */
  public static void writeln(boolean b) { writeln(b, true); }

  /**
   * writes a character and a new-line, then flushes the buffer.
   * @see #write(char)
   *
   * @param c character to be logged
   */
  public static void writeln(char c)    { writeln(c, true); }

  /**
   * writes a long, in decimal, and a new-line, then flushes the buffer.
   * @see #write(long)
   *
   * @param l long value to be logged
   */
  public static void writeln(long l)    { writeln(l, true); }

  /**
   * writes a <code>double</code> and a new-line, then flushes the buffer.
   * @see #write(double)
   *
   * @param d the double to be logged
   */
  public static void writeln(double d)  { writeln(d, true); }

  /**
   * writes a <code>double</code> and a new-line, then flushes the buffer.
   * @see #write(double, int)
   *
   * @param d the double to be logged
   */
  public static void writeln(double d, int postDecimalDigits) {
    writeln(d, postDecimalDigits, true); }

  /**
   * writes an array of characters and a new-line, then flushes the buffer.
   * @see #write(char [])
   *
   * @param ca the array of characters to be logged
   */
  public static void writeln(char [] ca) { writeln(ca, true); }

  /**
   * writes the start of an array of characters and a new-line, then
   * flushes the buffer.
   * @see #write(char[], int)
   *
   * @param ca the array of characters
   * @param len the number of characters to be logged, starting with
   * the first character
   */
  public static void writeln(char [] ca, int len) { writeln(ca, len, true); }

  /**
   * writes an array of bytes and a new-line, then
   * flushes the buffer.
   * @see #write(byte[])
   *
   * @param b the array of bytes to be logged
   */
  public static void writeln(byte [] b) { writeln(b, true); }

  /**
   * writes a string and a new-line, then flushes the buffer.
   *
   * @param s the string to be logged
   */
  public static void writeln(String s)  { writeln(s, true); }

  /**
   * writes a word, in hexadecimal, and a new-line, then flushes the buffer.
   * @see #write(Word)
   *
   * @param w the word to be logged
   */
  public static void writeln(Word w) { writeln(w, true); }

  /**
   * writes an address, in hexademical, and a new-line, then flushes
   * the buffer.
   * @see #write(Address)
   *
   * @param a the address to be logged
   */
  public static void writeln(Address a) { writeln(a, true); }

  /**
   * writes an object reference, in hexademical, and a new-line, then
   * flushes the buffer.
   * @see #write(ObjectReference)
   *
   * @param o the object reference to be logged
   */
  public static void writeln(ObjectReference o) { writeln(o, true); }

  /**
   * writes an offset, in hexademical, and a new-line, then flushes the buffer.
   * @see #write(Offset)
   *
   * @param o the offset to be logged
   */
  public static void writeln(Offset o) { writeln(o, true); }

  /**
   * writes an extent, in hexademical, and a new-line, then flushes the buffer.
   * @see #write(Extent)
   *
   * @param e the extent to be logged
   */
  public static void writeln(Extent e) { writeln(e, true); }

  /**
   * writes a new-line without flushing the buffer
   */
  public static void writelnNoFlush() {
    writelnWithFlush(false);
  }

  /**
   * writes a boolean and a new-line, then optionally flushes the buffer.
   * @see #write(boolean)
   *
   * @param b boolean value to be logged.
   * @param flush if <code>true</code> then flushes the buffer
   */
  public static void writeln(boolean b, boolean flush) {
    write(b);
    writelnWithFlush(flush);
  }

  /**
   * writes a character and a new-line, then optionally flushes the
   * buffer.
   * @see #write(char)
   *
   * @param c character to be logged
   * @param flush if <code>true</code> then flushes the buffer
   */
  public static void writeln(char c, boolean flush) {
    write(c);
    writelnWithFlush(flush);
  }

  /**
   * writes a long, in decimal, and a new-line, then optionally flushes
   * the buffer.
   * @see #write(long)
   *
   * @param l long value to be logged
   * @param flush if <code>true</code> then flushes the buffer
   */
  public static void writeln(long l, boolean flush) {
    write(l);
    writelnWithFlush(flush);
  }

  /**
   * writes a <code>double</code> and a new-line, then optionally flushes
   * the buffer.
   * @see #write(double)
   *
   * @param d the double to be logged
   * @param flush if <code>true</code> then flush the buffer
   */
  public static void writeln(double d, boolean flush) {
    write(d);
    writelnWithFlush(flush);
  }

  /**
   * writes a <code>double</code> and a new-line, then optionally flushes
   * the buffer.
   * @see #write(double, int)
   *
   * @param d the double to be logged
   * @param flush if <code>true</code> then flushes the buffer
   */
  public static void writeln(double d, int postDecimalDigits, boolean flush) {
    write(d, postDecimalDigits);
    writelnWithFlush(flush);
  }


  /**
   * writes an array of characters and a new-line, then optionally
   * flushes the buffer.
   * @see #write(char [])
   *
   * @param ca the array of characters to be logged
   * @param flush if <code>true</code> then flushes the buffer
   */
  public static void writeln(char[] ca, boolean flush) {
    write(ca);
    writelnWithFlush(flush);
  }

  /**
   * writes the start of an array of characters and a new-line, then
   * optionally flushes the buffer.
   * @see #write(char[], int)
   *
   * @param ca the array of characters
   * @param len the number of characters to be logged, starting with
   * the first character
   * @param flush if <code>true</code> then flushes the buffer
   */
  public static void writeln(char[] ca, int len, boolean flush) {
    write(ca, len);
    writelnWithFlush(flush);
  }

  /**
   * writes an array of bytes and a new-line, then optionally flushes the
   * buffer.
   * @see #write(byte[])
   *
   * @param b the array of bytes to be logged
   * @param flush if <code>true</code> then flushes the buffer
   */
  public static void writeln(byte[] b, boolean flush) {
    write(b);
    writelnWithFlush(flush);
  }

  /**
   * writes a string and a new-line, then optionally flushes the buffer.
   *
   * @param s the string to be logged
   * @param flush if <code>true</code> then flushes the buffer
   */
  public static void writeln(String s, boolean flush) {
    write(s);
    writelnWithFlush(flush);
  }

  public static void writeln(String s, long l) {
    write(s);
    writeln(l);
  }


  /**
   * writes a word, in hexadecimal, and a new-line, then optionally
   * flushes the buffer.
   * @see #write(Word)
   *
   * @param w the word to be logged
   * @param flush if <code>true</code> then flushes the buffer
   */
  public static void writeln(Word w, boolean flush) {
    write(w);
    writelnWithFlush(flush);
  }


  /**
   * writes an address, in hexademical, and a new-line, then optionally
   * flushes the buffer.
   * @see #write(Address)
   *
   * @param a the address to be logged
   * @param flush if <code>true</code> then flushes the buffer
   */
  public static void writeln(Address a, boolean flush) {
    write(a);
    writelnWithFlush(flush);
  }

  /**
   * writes an object reference, in hexademical, and a new-line, then
   * optionally flushes the buffer.
   * @see #write(ObjectReference)
   *
   * @param o the object reference to be logged
   * @param flush if <code>true</code> then flushes the buffer
   */
  public static void writeln(ObjectReference o, boolean flush) {
    write(o);
    writelnWithFlush(flush);
  }

  /**
   * writes an offset, in hexademical, and a new-line, then optionally
   * flushes the buffer.
   * @see #write(Offset)
   *
   * @param o the offset to be logged
   * @param flush if <code>true</code> then flushes the buffer
   */
  public static void writeln(Offset o, boolean flush) {
    write(o);
    writelnWithFlush(flush);
  }

  /**
   * writes an extent, in hexademical, and a new-line, then optionally
   * flushes the buffer.
   * @see #write(Extent)
   *
   * @param e the extent to be logged
   * @param flush if <code>true</code> then flushes the buffer
   */
  public static void writeln(Extent e, boolean flush) {
    write(e);
    writelnWithFlush(flush);
  }

  /**
   * writes a string followed by a Address
   * @see #write(String)
   * @see #write(Address)
   *
   * @param s the string to be logged
   * @param a the Address to be logged
   */
  public static void writeln(String s, Address a) {
    write(s);
    writeln(a);
  }

  /**
   * Log a thread identifier at the start of the next message flushed.
   */
  public static void prependThreadId() {
    getLog().setThreadIdFlag();
  }

  /**
   * flushes the buffer.  The buffered effected of writes since the last
   * flush will be logged in one block without output from other
   * thread's logging interleaving.
   */
  public static void flush() {
    getLog().flushBuffer();
  }

  /**
   * writes a new-line and optionally flushes the buffer
   *
   * @param flush if <code>true</code> the buffer is flushed
   */
  private static void writelnWithFlush(boolean flush) {
    add(NEW_LINE_CHAR);
    if (flush) {
      flush();
    }
  }

  /**
   * writes a <code>long</code> in hexadecimal
   *
   * @param w the Word to be logged
   * @param bytes the number of bytes from the long to be logged.  If
   * less than 8 then the least significant bytes are logged and some
   * of the most significant bytes are ignored.
   */
  private static void writeHex(Word w, int bytes) {
    int hexDigits = bytes * (1 << LOG_HEX_DIGITS_IN_BYTE);
    int nextDigit;

    write(HEX_PREFIX);

    for (int digitNumber = hexDigits - 1; digitNumber >= 0; digitNumber--) {
      nextDigit = w.rshl(digitNumber << LOG_BITS_IN_HEX_DIGIT).toInt() & 0xf;
      char nextChar = hexDigitCharacter[nextDigit];
      add(nextChar);
    }
  }

  /**
   * adds a character to the buffer
   *
   * @param c the character to add
   */
  private static void add(char c) {
    getLog().addToBuffer(c);
  }

  /**
   * adds a string to the buffer
   *
   * @param s the string to add
   */
  private static void add(String s) {
    getLog().addToBuffer(s);
  }

  private static Log getLog() {
    if (VM.assertions.runningVM()) {
      return VM.activePlan.log();
    } else {
      return log;
    }
  }

  /**
   * adds a character to the buffer
   *
   * @param c the character to add
   */
  private void addToBuffer(char c) {
    if (bufferIndex < MESSAGE_BUFFER_SIZE) {
      buffer[bufferIndex++] = c;
    } else {
      overflow = true;
      overflowLastChar = c;
    }
  }

  /**
   * adds a string to the buffer
   *
   * @param s the string to add
   */
  private void addToBuffer(String s) {
    if (bufferIndex < MESSAGE_BUFFER_SIZE) {
      bufferIndex += VM.strings.copyStringToChars(s, buffer, bufferIndex, MESSAGE_BUFFER_SIZE + 1);
      if (bufferIndex == MESSAGE_BUFFER_SIZE + 1) {
        overflow = true;
        // We don't bother setting OVERFLOW_LAST_CHAR, since we don't have an
        // MMTk method that lets us peek into a string. Anyway, it's just a
        // convenience to get the newline right.
        buffer[MESSAGE_BUFFER_SIZE] = OVERFLOW_MESSAGE_FIRST_CHAR;
        bufferIndex--;
      }
    } else {
      overflow = true;
    }
  }

  /**
   * flushes the buffer
   */
  private void flushBuffer() {
    int newlineAdjust = overflowLastChar == NEW_LINE_CHAR ? 0 : -1;
    int totalMessageSize = overflow ? (MESSAGE_BUFFER_SIZE + OVERFLOW_SIZE + newlineAdjust) : bufferIndex;
    if (threadIdFlag) {
      VM.strings.writeThreadId(buffer, totalMessageSize);
    } else {
      VM.strings.write(buffer, totalMessageSize);
    }
    threadIdFlag = false;
    overflow = false;
    overflowLastChar = '\0';
    bufferIndex = 0;
  }

  /**
   * sets the flag so that a thread identifier will be included before
   * the logged message
   */
  private void setThreadIdFlag() {
    threadIdFlag = true;
  }

  /**
   * gets the buffer for building string representations of integers.
   * There is one of these buffers for each Log instance.
   */
  private static char[] getIntBuffer() {
    return getLog().getTempBuffer();
  }

  /**
   * gets the buffer for building string representations of integers.
   */
  private char[] getTempBuffer() {
    return tempBuffer;
  }
}
