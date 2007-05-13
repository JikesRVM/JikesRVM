/*
 * This file is part of Jikes RVM (http://jikesrvm.sourceforge.net).
 * The Jikes RVM project is distributed under the Common Public License (CPL).
 * A copy of the license is included in the distribution, and is also
 * available at http://www.opensource.org/licenses/cpl1.0.php
 *
 * (C) Copyright Robin Garner, Australian National University
 */
//$Id:$

package org.jikesrvm.tools.apt;

import com.sun.mirror.apt.AnnotationProcessor;
import com.sun.mirror.apt.AnnotationProcessorEnvironment;
import com.sun.mirror.apt.AnnotationProcessorFactory;
import com.sun.mirror.apt.AnnotationProcessors;
import com.sun.mirror.declaration.AnnotationTypeDeclaration;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Set;

/**
 * Fctory class for APT - dispatches annotations to their handling classes.
 * 
 *
 */
public class SysCallProcessorFactory implements AnnotationProcessorFactory {

  /**
   * These are the annotations we know about
   */
  private static Collection<String> supportedAnnotations = 
    Collections.unmodifiableCollection(
        Arrays.asList(
            SysCallProcessor.GEN_IMPL_ANNOTATION,
            SysCallProcessor.SYSCALL_TEMPLATE_ANNOTATION));
  
  /**
   * Options that can be passed to us with "-A" - none at the moment.
   */
  private static Collection<String> supportedOptions = 
    Collections.emptySet();
  
  /**
   * The factory method
   */
  public AnnotationProcessor getProcessorFor(
      Set<AnnotationTypeDeclaration> decs, AnnotationProcessorEnvironment env) {
    if (decs == null || decs.isEmpty())
      return AnnotationProcessors.NO_OP;
    else
      return new SysCallProcessor(env);
  }

  /**
   * Required method - tells aps which annotations we support
   */
  public Collection<String> supportedAnnotationTypes() {
    return supportedAnnotations;
  }

  /**
   * Required method - tells aps which options we support
   */
  public Collection<String> supportedOptions() {
    return supportedOptions;
  }

}
