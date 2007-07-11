package org.vmmagic.pragma;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.lang.annotation.ElementType;
import org.vmmagic.Pragma;

/**
 * The entrypoint annotation indicates that the method or field is
 * directly accessed by the compiler. We cache resolved values for
 * these elements in VM_Entrypoints.
 */
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.METHOD, ElementType.FIELD})
@Pragma
public @interface Entrypoint { }
