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
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import org.apache.tools.ant.BuildException;
import org.apache.tools.ant.Task;

/**
 * Task for copying a task from one file to another thile replacing non-ascii
 * chars with ? ensuring it is an ascii value.
 */
public class ToAsciiTask
    extends Task {

  private File src;
  private File dest;

  public void setSrc(final File src) { this.src = src; }
  public void setDest(final File dest) { this.dest = dest; }

  public void execute() {
    validate();
    processFiles();
  }

  private void processFiles() {
    FileInputStream inputStream = null;
    FileOutputStream outputStream = null;
    try {
      if (dest.exists()) dest.delete();

      inputStream = new FileInputStream(src);
      outputStream = new FileOutputStream(dest);
      while (inputStream.available() > 0) {
        final int value = inputStream.read();
        final int output = isPrintable(value) ? value : '?';
        outputStream.write(output);
      }
    } catch (IOException ioe) {
      throw new BuildException("Error loading file " + src, ioe, getLocation());
    } finally {
      if (null != inputStream) {
        try {
          inputStream.close();
        } catch (final IOException ioe) {
          //ignore
        }
      }
      if (null != outputStream) {
        try {
          outputStream.close();
        } catch (final IOException ioe) {
          //ignore
        }
      }
    }
  }

  private boolean isPrintable(final int value) {
    return !(value < 32 || value > 126) || Character.isWhitespace(value);
  }

  private void validate() {
    if (null == src) throw new BuildException("Src not set.");
    if (!src.exists()) throw new BuildException("Src does not exist. - " + src);
    if (!src.isFile()) throw new BuildException("Src is not a regular file. - " + src);
    if (null == dest) throw new BuildException("Dest not set.");
  }
}
