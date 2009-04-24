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
package org.mmtk.harness.lang.parser;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;

/**
 * Provide access to script source for error messages etc.
 */
public class Source {

  private final String filename;
  private final ArrayList<String> lines = new ArrayList<String>();

  private boolean isInitialized = false;

  /**
   * Create a source object for the named file.
   * @param filename
   */
  public Source(String filename) {
    this.filename = filename;
  }

  private void readSource() {
    try {
      BufferedReader source = new BufferedReader(new FileReader(filename));
      for (String line = source.readLine(); line != null; line = source.readLine()) {
        lines.add(line);
      }
      source.close();
    } catch (IOException e) {
      throw new RuntimeException(e);
    }
  }

  private void initialize() {
    if (!isInitialized) {
      isInitialized = true;
      readSource();
    }
  }

  /**
   * Return the given source line (numbered from 1).
   * @param line
   * @return
   */
  public String getLine(int line) {
    initialize();
    return lines.get(line-1);
  }
}
