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
package org.mmtk.harness.lang.compiler;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;


public class CompiledMethodTable implements Iterable<CompiledMethod> {
  private Map<String,CompiledMethod> compiledMethods = new HashMap<String,CompiledMethod>();

  public void put(CompiledMethod m) {
    compiledMethods.put(m.getName(),m);
  }

  public CompiledMethod get(String name) {
    return compiledMethods.get(name);
  }

  @Override
  public Iterator<CompiledMethod> iterator() {
    return compiledMethods.values().iterator();
  }
}
