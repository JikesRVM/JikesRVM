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
import java.io.Reader;
import java.io.StringReader;
import java.util.HashMap;
import java.util.Map;

public class TestFileAccess extends DefaultFileAccess implements FileAccess {

  private final Map<String, String> fileNameToContent;
  private final StringBuilder fileContent;

  public TestFileAccess() {
    fileNameToContent = new HashMap<String, String>(1);
    fileContent = new StringBuilder();
  }

  @Override
  protected StringBuilder getStringBuilderToHoldFileContents() {
    return fileContent;
  }

  @Override
  protected BufferedReader getBufferedReaderForFile(String fileName) throws FileNotFoundException {
    String fileContent = fileNameToContent.get(fileName);
    if (fileContent == null) {
      throw new FileNotFoundException(fileName + System.getProperty("line.separator"));
    }
    Reader r = new StringReader(fileContent);
    BufferedReader br = new BufferedReader(r);
    return br;
  }

  public void putContentForFile(String fileName, String content) {
    fileNameToContent.put(fileName, content);
  }

}
