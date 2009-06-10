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
package org.jikesrvm.tools.template;

import java.util.NoSuchElementException;

class QuotedStringTokenizer {
  private String str;
  private int curPos;
  private int maxPos;

  public static final String delim = " \t\n\r\f";
  public QuotedStringTokenizer(String str) {
    curPos = 0;
    this.str = str;
    maxPos = str.length();
  }

  private void skipDelimiters() {
    while (curPos < maxPos && delim.indexOf(str.charAt(curPos)) >= 0)
      curPos++;
  }

  public boolean hasMoreTokens() {
    skipDelimiters();
    return curPos < maxPos;
  }

  public String nextToken() {
    skipDelimiters();
    if (curPos >= maxPos)
      throw new NoSuchElementException();
    int start = curPos;
    if (str.charAt(curPos) == '\"') {
      start++;
      curPos++;
      boolean quoted = false;
      while (quoted || str.charAt(curPos) != '\"') {
        quoted = !quoted && str.charAt(curPos) == '\\';
        curPos++;
        if (curPos >= maxPos)
          throw new UnterminatedStringException();
      }
      StringBuffer sb = new StringBuffer();
      String s = str.substring(start, curPos++);
      int st = 0;
      for (;;) {
        int bs = s.indexOf('\\', st);
        if (bs == -1) break;
        sb.append(s.substring(st, bs));
        sb.append(s.substring(bs+1, bs+2));
        st = bs + 2;
      }
      sb.append(s.substring(st));
      return sb.toString();
    }
    while (curPos < maxPos && delim.indexOf(str.charAt(curPos)) < 0)
      curPos++;
    return str.substring(start, curPos);
  }

  public int countTokens() {
    int count = 0;
    int pos = curPos;
    while (pos < maxPos) {
      // skip delimiters
      while (pos < maxPos && delim.indexOf(str.charAt(pos)) >= 0)
        pos++;
      if (pos >= maxPos) break;
      if (str.charAt(pos) == '\"') {
        pos++;
        boolean quoted = false;
        while (quoted || str.charAt(pos) != '\"') {
          quoted = !quoted && str.charAt(pos) == '\\';
          pos++;
          if (pos >= maxPos)
            throw new UnterminatedStringException();
        }
        pos++;
      } else {
        while (pos < maxPos && delim.indexOf(str.charAt(pos)) < 0)
          pos++;
      }
      count++;
    }
    return count;
  }
}
