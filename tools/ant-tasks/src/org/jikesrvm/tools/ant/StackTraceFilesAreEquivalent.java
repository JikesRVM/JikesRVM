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
import java.io.IOException;
import java.util.List;
import java.util.ArrayList;

import org.apache.tools.ant.BuildException;
import org.apache.tools.ant.types.LogLevel;
import org.apache.tools.ant.taskdefs.condition.Condition;
import org.apache.tools.ant.ProjectComponent;


/**
 * Compares two files that only contain stack traces for semantic equivalence.
 * Right now, this is done by normalizing all white space, removing empty lines
 * and removing line endings.
 */
public class StackTraceFilesAreEquivalent extends ProjectComponent implements Condition {

  private static final int VERBOSE = LogLevel.VERBOSE.getLevel();

  /** first file for comparison */
  private File firstFile;
  /** second file for comparison */
  private File secondFile;

  public void setFirstFile(File firstFile) {
    this.firstFile = firstFile;
  }

  public void setSecondFile(File secondFile) {
    this.secondFile = secondFile;
  }

  @Override
  public boolean eval() throws BuildException {
   verifyTaskAttributes();

   List<String> linesOfFirstFile = new ArrayList<String>();
   readLinesFromFileInto(firstFile, linesOfFirstFile);

   List<String> linesOfSecondFile = new ArrayList<String>();
   readLinesFromFileInto(secondFile, linesOfSecondFile);

   int firstFileLineCount = linesOfFirstFile.size();
   int secondFileLineCount = linesOfSecondFile.size();
   if (firstFileLineCount != secondFileLineCount) {
     log("First files has " + firstFileLineCount + " lines but second file has " + secondFileLineCount + " lines!", VERBOSE);
   }

   boolean success = true;
   for (int i = 0; i < linesOfFirstFile.size(); i++) {
     if (!linesOfFirstFile.get(i).equals(linesOfSecondFile.get(i))) {
       log("Lines do NOT match!", VERBOSE);
       log("Line of first file", VERBOSE);
       log(linesOfFirstFile.get(i), VERBOSE);
       log("Line of second file", VERBOSE);
       log(linesOfSecondFile.get(i), VERBOSE);
       return false;
     }
   }

   return success;
  }

  private void readLinesFromFileInto(File file, List<String> lines) throws BuildException {
    FileReader fr = null;
    BufferedReader br = null;
    try {
      fr = new FileReader(file);
      br = new BufferedReader(fr);
      String readLine = br.readLine();
      String processedLine = readLine;
      while (readLine != null) {
        processedLine = readLine.trim();
        processedLine = processedLine.replaceAll("\\s+", " ");
        if (processedLine != null) {
          lines.add(processedLine);
        }
        readLine = br.readLine();
      }
    } catch (IOException e) {
      throw new BuildException(e);
    } finally {
      try {
        if (br != null) br.close();
        if (fr != null) fr.close();
      } catch (IOException e) {
        throw new BuildException(e);
      }
    }
  }

  private void verifyTaskAttributes() {
    if (firstFile == null) {
      throw new BuildException("firstFile not set. " +
        "It must be set to the name of an existing file.");
    } else if (firstFile.isDirectory()) {
      throw new BuildException("Expected firstFile (" +
          firstFile + ") to be a file but it's a directory.");
    } else if (!firstFile.canRead()) {
      throw new BuildException("Expected firstFile (" +
          firstFile + ") to be readable but it's not.");
    }

    log("First file set to " + firstFile, VERBOSE);

    if (secondFile == null) {
      throw new BuildException("secondFile not set. " +
        "It must be set to the name of an existing file.");
    } else if (secondFile.isDirectory()) {
      throw new BuildException("Expected secondFile (" +
          secondFile + ") to be a file but it's a directory.");
    } else if (!secondFile.canRead()) {
      throw new BuildException("Expected secondFile (" +
          secondFile + ") to be readable but it's not.");
    }

    log("Second file set to " + secondFile, VERBOSE);
  }

}
