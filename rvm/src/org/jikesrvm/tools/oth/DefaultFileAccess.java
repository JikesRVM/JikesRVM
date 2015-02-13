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
package org.jikesrvm.tools.oth;

import java.io.BufferedReader;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.util.StringTokenizer;

public class DefaultFileAccess implements FileAccess {

  @Override
  public final String[] readOptionStringFromFile(String fileName) throws IOException {
    BufferedReader in = getBufferedReaderForFile(fileName);
    StringBuilder s = getStringBuilderToHoldFileContents();
    readContentIntoStringBuilder(in, s);
    String[] options = tokenizeInput(s.toString());
    return options;
  }

  protected StringBuilder getStringBuilderToHoldFileContents() {
    return new StringBuilder();
  }

  protected BufferedReader getBufferedReaderForFile(String fileName) throws FileNotFoundException {
    return new BufferedReader(new FileReader(fileName));
  }

  protected final void readContentIntoStringBuilder(BufferedReader in, StringBuilder s) throws IOException {
    String line = "";
    while (in.ready() && line != null) {
      line = in.readLine();
      if (line != null) {
        line = line.trim();
        if (isNonCommentLine(line)) {
          s.append(line);
          s.append(" ");
        }
      }
    }
    in.close();
  }

  protected final String[] tokenizeInput(String s) {
    StringTokenizer t = new StringTokenizer(s);
    String[] av = new String[t.countTokens()];
    for (int j = 0; j < av.length; j++) {
      av[j] = t.nextToken();
    }
    return av;
  }

  protected final boolean isNonCommentLine(String line) {
    return !line.startsWith("#");
  }

}
