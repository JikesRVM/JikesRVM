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
package org.jikesrvm.tools.annotation_processing;

import com.sun.mirror.apt.AnnotationProcessor;
import com.sun.mirror.apt.AnnotationProcessorEnvironment;
import com.sun.mirror.apt.AnnotationProcessorFactory;
import com.sun.mirror.apt.AnnotationProcessors;
import com.sun.mirror.declaration.AnnotationTypeDeclaration;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Set;

/** Factory class for APT - dispatches annotations to their handling classes. */
public class SysCallProcessorFactory implements AnnotationProcessorFactory {
  /** The factory method */
  public AnnotationProcessor getProcessorFor(final Set<AnnotationTypeDeclaration> decs,
                                             final AnnotationProcessorEnvironment env) {
    if (null == decs || decs.isEmpty()) {
      return AnnotationProcessors.NO_OP;
    } else {
      return new SysCallProcessor(env);
    }
  }

  /** @return the set of annotation names we process. */
  public Collection<String> supportedAnnotationTypes() {
    final List<String> list =
        Arrays.asList(SysCallProcessor.GEN_IMPL_ANNOTATION, SysCallProcessor.SYSCALL_TEMPLATE_ANNOTATION);
    return Collections.unmodifiableCollection(list);
  }

  /** @return  the set of options we support. */
  @SuppressWarnings({"unchecked"})
  public Collection<String> supportedOptions() {
    return Collections.EMPTY_SET;
  }
}
