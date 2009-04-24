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
import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.util.Vector;
import org.apache.tools.ant.BuildException;
import org.apache.tools.ant.Task;
import org.apache.tools.ant.taskdefs.Property;
import org.apache.tools.ant.types.RegularExpression;
import org.apache.tools.ant.util.regexp.Regexp;

public class SelectRegexTask
    extends Task {

  private RegularExpression pattern;
  private int patternLines;
  private String select;

  private String property;
  private File file;

  public void setProperty(final String property) { this.property = property; }
  public void setSelect(final String select) { this.select = select; }
  public void setFile(final File file) { this.file = file; }

  public void setPattern(final String pattern) {
    this.pattern = new RegularExpression();
    this.pattern.setPattern(pattern);
    this.patternLines = pattern.split(System.getProperty("line.separator")).length;
  }

  public void execute() {
    validate();

    final String output = matchFile();
    if (output != null) {
      Property p = (Property) getProject().createTask("property");
      p.setName(property);
      p.setValue(output);
      p.execute();
    }
  }

  private String matchFile() {
    BufferedReader input = null;
    try {
      final Regexp regexp = this.pattern.getRegexp(getProject());
      input = new BufferedReader(new FileReader(file));
      String[] lines = new String[patternLines];
      String sep = System.getProperty("line.separator");
      for(int i=0; i < lines.length; i++) {
        lines[i] = "";
      }
      int nextLine = 0;
      while((lines[nextLine] = input.readLine()) != null) {
        StringBuilder sb = new StringBuilder();
        for(int i=nextLine+1; i <= nextLine + lines.length ; i++) {
          String line = lines[i % lines.length];
          sb.append(line);
          sb.append(sep);
        }
        String result = performMatching(sb.toString());
        if (result != null) {
          return result;
        }
        nextLine = (nextLine + 1) % lines.length;
      }
      return null;
    } catch (IOException ioe) {
      throw new BuildException("Error loading file " + file, ioe, getLocation());
    } finally {
      if (input != null) {
        try {
          input.close();
        } catch (final IOException ioe) {
          //ignore
        }
      }
    }
  }

  private String performMatching(final String input) {
    final Regexp regexp = this.pattern.getRegexp(getProject());
    final Vector groups = regexp.getGroups(input, 0);
    if (groups != null && !groups.isEmpty()) {
      String output = select;
      final int count = groups.size();
      for (int i = 0; i < count; i++) {
        final String group = (String) groups.get(i);
        output = output.replace("\\" + i, group);
      }
      return output;
    }
    return null;
  }

  private void validate() {
    if (null == property) throw new BuildException("Property not set.");
    if (null == pattern) throw new BuildException("No regular expression specified.");
    if (null == select) throw new BuildException("Select not set.");
    if (null == file) throw new BuildException("File not set.");
    if (!file.exists()) throw new BuildException("File does not exist. - " + file);
    if (!file.isFile()) throw new BuildException("File is not a regular file. - " + file);
  }
}
