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

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.Writer;
import org.apache.tools.ant.BuildException;
import org.apache.tools.ant.Task;


/**
 * Splits a file into multiple files
 */
public class SplitInstructionFormats extends Task {

  /** File being split */
  private File src;
  /** Directory to hold results */
  private File dest;

  /** Set the source file */
  public void setSrc(File src) {
    this.src = src;
  }

  /** Set the destination directory */
  public void setDest(File dest) {
    this.dest = dest;
  }

  /** Perform the task */
  public void execute() throws BuildException {
    if (src == null) {
      throw new BuildException("src not set.");
    } else if (!src.isFile()) {
      throw new BuildException("Expected src (" + src + ") is a file");
    }
    if (dest == null) {
      throw new BuildException("dest not set.");
    } else if (!dest.isDirectory()) {
      throw new BuildException("Expected dest (" + dest + ") is a directory");
    }

    File curFile = null;
    try {
      BufferedReader reader = new BufferedReader(new FileReader(src));
      Writer writer = null;
      String line = reader.readLine();
      while (line != null) {
        String matchStart = "##NEW_FILE_STARTS_HERE ";
        String matchEnd = "##";
        if (line.startsWith(matchStart) && line.endsWith(matchEnd)) {
          String curFileName = line.substring(matchStart.length(),
                                              line.length() - matchEnd.length());
          curFile = new File(dest, curFileName);
          if (writer != null) {
            writer.close();
          }
          writer = new FileWriter(curFile);
        } else {
          if (writer != null) {
            writer.write(line);
            writer.write('\n');
          }
        }
        line = reader.readLine();
      }
      if (writer != null) {
        writer.close();
      }
    } catch (IOException e) {
      throw new BuildException("Error splitting files " + src + " " + curFile, e);
    }
  }
}
