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
package org.jikesrvm.tools.ant;

import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import org.apache.tools.ant.BuildException;
import org.apache.tools.ant.Task;

/**
 * If the file is greater than the specified number of bytes then truncate.
 */
public class TruncateTask extends Task {
  private File file;
  private int size;

  public void setFile(final File file) {
    this.file = file;
  }

  public void setSize(final int size) {
    this.size = size;
  }

  public void execute() throws BuildException {
    if (null == file) throw new BuildException("file not set.");
    if (0 >= size) throw new BuildException("size not set to a value greater than 0.");
    if (file.length() < size) {
      return;
    }
    try {
      final RandomAccessFile random = new RandomAccessFile(file, "rw");
      random.setLength(size);
      random.close();
    } catch (IOException e) {
      throw new BuildException("Error truncating file " + file, e);
    }
  }
}
