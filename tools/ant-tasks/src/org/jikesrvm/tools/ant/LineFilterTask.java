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
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import org.apache.tools.ant.BuildException;
import org.apache.tools.ant.Task;
import org.apache.tools.ant.types.RegularExpression;
import org.apache.tools.ant.util.regexp.Regexp;

/**
 * Filter out any lines from input that match the supplied patterns.
 */
public class LineFilterTask extends Task {

  private final ArrayList<RegularExpression> patterns = new ArrayList<RegularExpression>();
  private File src;
  private File dest;

  public void setSrc(final File src) {
    this.src = src;
  }

  public void setDest(final File dest) {
    this.dest = dest;
  }

  public void addFilter(final RegularExpression regex) {
    patterns.add(regex);
  }

  public void execute() throws BuildException {
    if (null == src) throw new BuildException("src not set.");
    if (null == dest) throw new BuildException("dest not set.");
    if (0 == patterns.size()) throw new BuildException("No patterns specified.");

    final ArrayList<Regexp> regexpList = new ArrayList<Regexp>();
    for (final RegularExpression pattern : patterns) {
      regexpList.add(pattern.getRegexp(getProject()));
    }
    final Regexp[] regexps = regexpList.toArray(new Regexp[regexpList.size()]);
    BufferedReader reader = null;
    BufferedWriter writer = null;
    try {
      reader = new BufferedReader(new FileReader(src));
      writer = new BufferedWriter(new FileWriter(dest));

      String line = reader.readLine();
      while (null != line) {
        if (!lineMatches(line, regexps)) {
          writer.write(line);
          writer.write('\n');
        }
        line = reader.readLine();
      }
    } catch (IOException e) {
      throw new BuildException("Error truncating file " + src, e);
    } finally {
      if (null != reader) {
        try {
          reader.close();
        } catch (IOException e) {
        }
      }
      if (null != writer) {
        try {
          writer.close();
        } catch (final IOException ioe) {
          throw new BuildException(ioe);
        }
      }
    }
  }

  private boolean lineMatches(final String line, final Regexp[] regexps) {
    for (Regexp regexp : regexps) {
      if (regexp.matches(line)) {
        return true;
      }
    }
    return false;
  }
}

