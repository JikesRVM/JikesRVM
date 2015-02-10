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
package org.jikesrvm.tests.util;

import java.io.IOException;
import java.io.OutputStream;

/**
 * Provides access to the received output in the form of a {@link StringBuilder}.
 */
public class StringBuilderOutputStream extends OutputStream {

  private final StringBuilder output = new StringBuilder();

  @Override
  public void write(int byteAsInt) throws IOException {
    byte b = (byte) (byteAsInt & 0x000000FF);
    byte[] bytes = new byte[1];
    bytes[0] = b;
    output.append(new String(bytes));
  }

  public StringBuilder getOutput() {
    return output;
  }

}
