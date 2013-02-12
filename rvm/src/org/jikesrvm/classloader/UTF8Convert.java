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
package org.jikesrvm.classloader;

import java.io.UTFDataFormatException;
import java.nio.ByteBuffer;
import org.vmmagic.pragma.Pure;
import org.jikesrvm.VM;
import org.vmmagic.pragma.Inline;
import org.vmmagic.pragma.NoInline;

/**
 * Abstract class that contains conversion routines to/from utf8
 * and/or pseudo-utf8.  It does not support utf8 encodings of
 * more than 3 bytes.
 * <p>
 * The difference between utf8 and pseudo-utf8 is the special
 * treatment of null.  In utf8, null is encoded as a single byte
 * directly, whereas in pseudo-utf8, it is encoded as a two-byte
 * sequence.  See the JVM specification for more information.
 */
public abstract class UTF8Convert {

  /**
   * Strictly check the format of the utf8/pseudo-utf8 byte array in
   * fromUTF8.
   */
  static final boolean STRICTLY_CHECK_FORMAT = false;
  /**
   * Set fromUTF8 to not throw an exception when given a normal utf8
   * byte array.
   */
  static final boolean ALLOW_NORMAL_UTF8 = false;
  /**
   * Set fromUTF8 to not throw an exception when given a pseudo utf8
   * byte array.
   */
  static final boolean ALLOW_PSEUDO_UTF8 = true;
  /**
   * Set toUTF8 to write in pseudo-utf8 (rather than normal utf8).
   */
  static final boolean WRITE_PSEUDO_UTF8 = true;

  /**
   * UTF8 character visitor abstraction
   */
  private abstract static class UTF8CharacterVisitor {
    abstract void visit_char(char c);
  }

  /**
   * Visitor that builds up a char[] as characters are decoded
   */
  private static final class ByteArrayStringEncoderVisitor extends UTF8CharacterVisitor {
    final char[] result;
    int index;
    ByteArrayStringEncoderVisitor(int length) {
      result = new char[length];
      index = 0;
    }
    @Override
    void visit_char(char c) {
      result[index] = c;
      index++;
    }
    @Override
    public String toString() {
      if (VM.runningVM) {
        return java.lang.JikesRVMSupport.newStringWithoutCopy(result, 0, index);
      } else {
        return new String(result, 0, index);
      }
    }
  }

  /**
   * Visitor that builds up a char[] as characters are decoded
   */
  private static final class ByteBufferStringEncoderVisitor extends UTF8CharacterVisitor {
    final char[] result;
    int index;
    ByteBufferStringEncoderVisitor(int length) {
      result = new char[length];
      index = 0;
    }
    @Override
    void visit_char(char c) {
      result[index] = c;
      index++;
    }
    @Override
    public String toString() {
      if (VM.runningVM) {
        return java.lang.JikesRVMSupport.newStringWithoutCopy(result, 0, index);
      } else {
        return new String(result, 0, index);
      }
    }
  }

  /**
   * Visitor that builds up a String.hashCode form hashCode as characters are decoded
   */
  private static final class StringHashCodeVisitor extends UTF8CharacterVisitor {
    int result = 0;
    @Override
    void visit_char(char c) {
      result = result * 31 + c;
    }
    int getResult() {
      return result;
    }
  }

  /**
   * Convert the given sequence of (pseudo-)utf8 formatted bytes
   * into a String.<p>
   *
   * The acceptable input formats are controlled by the
   * STRICTLY_CHECK_FORMAT, ALLOW_NORMAL_UTF8, and ALLOW_PSEUDO_UTF8
   * flags.
   *
   * @param utf8 (pseudo-)utf8 byte array
   * @throws UTFDataFormatException if the (pseudo-)utf8 byte array is not valid (pseudo-)utf8
   * @return unicode string
   */
  public static String fromUTF8(byte[] utf8) throws UTFDataFormatException {
    UTF8CharacterVisitor visitor = new ByteArrayStringEncoderVisitor(utf8.length);
    visitUTF8(utf8, visitor);
    return visitor.toString();
  }

  /**
   * Convert the given sequence of (pseudo-)utf8 formatted bytes
   * into a String.
   *
   * The acceptable input formats are controlled by the
   * STRICTLY_CHECK_FORMAT, ALLOW_NORMAL_UTF8, and ALLOW_PSEUDO_UTF8
   * flags.<p>
   *
   * @param utf8 (pseudo-)utf8 byte array
   * @throws UTFDataFormatException if the (pseudo-)utf8 byte array is not valid (pseudo-)utf8
   * @return unicode string
   */
  public static String fromUTF8(ByteBuffer utf8) throws UTFDataFormatException {
    UTF8CharacterVisitor visitor = new ByteBufferStringEncoderVisitor(utf8.remaining());
    visitUTF8(utf8, visitor);
    return visitor.toString();
  }

  /**
   * Convert the given sequence of (pseudo-)utf8 formatted bytes
   * into a String hashCode.<p>
   *
   * The acceptable input formats are controlled by the
   * STRICTLY_CHECK_FORMAT, ALLOW_NORMAL_UTF8, and ALLOW_PSEUDO_UTF8
   * flags.
   *
   * @param utf8 (pseudo-)utf8 byte array
   * @throws UTFDataFormatException if the (pseudo-)utf8 byte array is not valid (pseudo-)utf8
   * @return hashCode corresponding to if this were a String.hashCode
   */
  public static int computeStringHashCode(byte[] utf8) throws UTFDataFormatException {
    StringHashCodeVisitor visitor = new StringHashCodeVisitor();
    visitUTF8(utf8, visitor);
    return visitor.getResult();
  }

  /**
   * Generate exception messages without bloating code
   */
  @NoInline
  private static void throwDataFormatException(String message, int location) throws UTFDataFormatException {
    throw new UTFDataFormatException(message + " at location " + location);
  }

  /**
   * Visit all bytes of the given utf8 string calling the visitor when a
   * character is decoded.<p>
   *
   * The acceptable input formats are controlled by the
   * STRICTLY_CHECK_FORMAT, ALLOW_NORMAL_UTF8, and ALLOW_PSEUDO_UTF8
   * flags.
   *
   * @param utf8 (pseudo-)utf8 byte array
   * @param visitor called when characters are decoded
   * @throws UTFDataFormatException if the (pseudo-)utf8 byte array is not valid (pseudo-)utf8
   */
  @Inline
  private static void visitUTF8(byte[] utf8, UTF8CharacterVisitor visitor) throws UTFDataFormatException {
    for (int i = 0, n = utf8.length; i < n;) {
      byte b = utf8[i++];
      if (STRICTLY_CHECK_FORMAT && !ALLOW_NORMAL_UTF8) {
        if (b == 0) {
          throwDataFormatException("0 byte encountered", i-1);
        }
      }
      if (b >= 0) {  // < 0x80 unsigned
        // in the range '\001' to '\177'
        visitor.visit_char((char) b);
        continue;
      }
      try {
        byte nb = utf8[i++];
        if (b < -32) {  // < 0xe0 unsigned
          // '\000' or in the range '\200' to '\u07FF'
          char c = (char) (((b & 0x1f) << 6) | (nb & 0x3f));
          visitor.visit_char(c);
          if (STRICTLY_CHECK_FORMAT) {
            if (((b & 0xe0) != 0xc0) || ((nb & 0xc0) != 0x80)) {
              throwDataFormatException("invalid marker bits for double byte char" , i-2);
            }
            if (c < '\200') {
              if (!ALLOW_PSEUDO_UTF8 || (c != '\000')) {
                throwDataFormatException("encountered double byte char that should have been single byte", i-2);
              }
            } else if (c > '\u07FF') {
              throwDataFormatException("encountered double byte char that should have been single byte", i-2);
            }
          }
        } else {
          byte nnb = utf8[i++];
          // in the range '\u0800' to '\uFFFF'
          char c = (char) (((b & 0x0f) << 12) | ((nb & 0x3f) << 6) | (nnb & 0x3f));
          visitor.visit_char(c);
          if (STRICTLY_CHECK_FORMAT) {
            if (((b & 0xf0) != 0xe0) || ((nb & 0xc0) != 0x80) || ((nnb & 0xc0) != 0x80)) {
              throwDataFormatException("invalid marker bits for triple byte char", i - 3);
            }
            if (c < '\u0800') {
              throwDataFormatException("encountered triple byte char that should have been fewer bytes", i - 3);
            }
          }
        }
      } catch (ArrayIndexOutOfBoundsException e) {
        throwDataFormatException("unexpected end", i);
      }
    }
  }

  /**
   * Visit all bytes of the given utf8 string calling the visitor when a
   * character is decoded.<p>
   *
   * The acceptable input formats are controlled by the
   * STRICTLY_CHECK_FORMAT, ALLOW_NORMAL_UTF8, and ALLOW_PSEUDO_UTF8
   * flags.
   *
   * @param utf8 (pseudo-)utf8 byte array
   * @param visitor called when characters are decoded
   * @throws UTFDataFormatException if the (pseudo-)utf8 byte array is not valid (pseudo-)utf8
   */
  @Inline
  private static void visitUTF8(ByteBuffer utf8, UTF8CharacterVisitor visitor) throws UTFDataFormatException {
    while (utf8.hasRemaining()) {
      byte b = utf8.get();
      if (STRICTLY_CHECK_FORMAT && !ALLOW_NORMAL_UTF8) {
        if (b == 0) {
          throwDataFormatException("0 byte encountered", utf8.position() - 1);
        }
      }
      if (b >= 0) {  // < 0x80 unsigned
        // in the range '\001' to '\177'
        visitor.visit_char((char) b);
        continue;
      }
      try {
        byte nb = utf8.get();
        if (b < -32) {  // < 0xe0 unsigned
          // '\000' or in the range '\200' to '\u07FF'
          char c = (char) (((b & 0x1f) << 6) | (nb & 0x3f));
          visitor.visit_char(c);
          if (STRICTLY_CHECK_FORMAT) {
            if (((b & 0xe0) != 0xc0) || ((nb & 0xc0) != 0x80)) {
              throwDataFormatException("invalid marker bits for double byte char", utf8.position() - 2);
            }
            if (c < '\200') {
              if (!ALLOW_PSEUDO_UTF8 || (c != '\000')) {
                throwDataFormatException("encountered double byte char that should have been single byte", utf8.position() - 2);
              }
            } else if (c > '\u07FF') {
              throwDataFormatException("encountered double byte char that should have been single byte", utf8.position() - 2);
            }
          }
        } else {
          byte nnb = utf8.get();
          // in the range '\u0800' to '\uFFFF'
          char c = (char) (((b & 0x0f) << 12) | ((nb & 0x3f) << 6) | (nnb & 0x3f));
          visitor.visit_char(c);
          if (STRICTLY_CHECK_FORMAT) {
            if (((b & 0xf0) != 0xe0) || ((nb & 0xc0) != 0x80) || ((nnb & 0xc0) != 0x80)) {
              throwDataFormatException("invalid marker bits for triple byte char", utf8.position() - 3);
            }
            if (c < '\u0800') {
              throwDataFormatException("encountered triple byte char that should have been fewer bytes", utf8.position() - 3);
            }
          }
        }
      } catch (ArrayIndexOutOfBoundsException e) {
        throwDataFormatException("unexpected end", utf8.position());
      }
    }
  }

  /**
   * Convert the given String into a sequence of (pseudo-)utf8
   * formatted bytes.<p>
   *
   * The output format is controlled by the WRITE_PSEUDO_UTF8 flag.
   *
   * @param s String to convert
   * @return array containing sequence of (pseudo-)utf8 formatted bytes
   */
  public static byte[] toUTF8(String s) {
    byte[] result = new byte[utfLength(s)];
    int result_index = 0;
    for (int i = 0, n = s.length(); i < n; ++i) {
      char c = s.charAt(i);
      // in all shifts below, c is an (unsigned) char,
      // so either >>> or >> is ok
      if (((!WRITE_PSEUDO_UTF8) || (c >= 0x0001)) && (c <= 0x007F)) {
        result[result_index++] = (byte) c;
      } else if (c > 0x07FF) {
        result[result_index++] = (byte) (0xe0 | (byte) (c >> 12));
        result[result_index++] = (byte) (0x80 | ((c & 0xfc0) >> 6));
        result[result_index++] = (byte) (0x80 | (c & 0x3f));
      } else {
        result[result_index++] = (byte) (0xc0 | (byte) (c >> 6));
        result[result_index++] = (byte) (0x80 | (c & 0x3f));
      }
    }
    return result;
  }

  /**
   * Convert the given String into a sequence of (pseudo-)utf8
   * formatted bytes.<p>
   *
   * The output format is controlled by the WRITE_PSEUDO_UTF8 flag.
   *
   * @param s String to convert
   * @param b Byte buffer to hold result
   */
  @Inline
  public static void toUTF8(String s, ByteBuffer b) {
    int result_index = 0;
    for (int i = 0, n = s.length(); i < n; ++i) {
      char c = s.charAt(i);
      // in all shifts below, c is an (unsigned) char,
      // so either >>> or >> is ok
      if (((!WRITE_PSEUDO_UTF8) || (c >= 0x0001)) && (c <= 0x007F)) {
        b.put((byte) c);
      } else if (c > 0x07FF) {
        b.put((byte) (0xe0 | (byte) (c >> 12)));
        b.put((byte) (0x80 | ((c & 0xfc0) >> 6)));
        b.put((byte) (0x80 | (c & 0x3f)));
      } else {
        b.put((byte) (0xc0 | (byte) (c >> 6)));
        b.put((byte) (0x80 | (c & 0x3f)));
      }
    }
  }

  /**
   * Returns the length of a string's UTF encoded form.
   */
  @Pure
  public static int utfLength(String s) {
    int utflen = 0;
    for (int i = 0, n = s.length(); i < n; ++i) {
      int c = s.charAt(i);
      if (((!WRITE_PSEUDO_UTF8) || (c >= 0x0001)) && (c <= 0x007F)) {
        ++utflen;
      } else if (c > 0x07FF) {
        utflen += 3;
      } else {
        utflen += 2;
      }
    }
    return utflen;
  }

  /**
   * Check whether the given sequence of bytes is valid (pseudo-)utf8.
   *
   * @param bytes byte array to check
   * @return {@code true} iff the given sequence is valid (pseudo-)utf8.
   */
  public static boolean check(byte[] bytes) {
    for (int i = 0, n = bytes.length; i < n;) {
      byte b = bytes[i++];
      if (!ALLOW_NORMAL_UTF8) {
        if (b == 0) return false;
      }
      if (b >= 0) {  // < 0x80 unsigned
        // in the range '\001' to '\177'
        continue;
      }
      try {
        byte nb = bytes[i++];
        if (b < -32) {  // < 0xe0 unsigned
          // '\000' or in the range '\200' to '\u07FF'
          char c = (char) (((b & 0x1f) << 6) | (nb & 0x3f));
          if (((b & 0xe0) != 0xc0) || ((nb & 0xc0) != 0x80)) {
            return false;
          }
          if (c < '\200') {
            if (!ALLOW_PSEUDO_UTF8 || (c != '\000')) {
              return false;
            }
          } else if (c > '\u07FF') {
            return false;
          }
        } else {
          byte nnb = bytes[i++];
          // in the range '\u0800' to '\uFFFF'
          char c = (char) (((b & 0x0f) << 12) | ((nb & 0x3f) << 6) | (nnb & 0x3f));
          if (((b & 0xf0) != 0xe0) || ((nb & 0xc0) != 0x80) || ((nnb & 0xc0) != 0x80)) {
            return false;
          }
          if (c < '\u0800') {
            return false;
          }
        }
      } catch (ArrayIndexOutOfBoundsException e) {
        return false;
      }
    }
    return true;
  }
}
