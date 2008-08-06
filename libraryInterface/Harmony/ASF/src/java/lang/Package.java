/* 
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 * 
 *     http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package java.lang;

import java.lang.ref.SoftReference;
import java.lang.annotation.Annotation;
import java.lang.reflect.AnnotatedElement;
import java.net.URL;
import java.net.JarURLConnection;
import java.security.AccessController;
import java.security.PrivilegedAction;
import java.util.jar.Attributes;
import java.util.jar.Manifest;
import java.util.Collection;
import java.util.Hashtable;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.StringTokenizer;

import org.jikesrvm.classloader.BootstrapClassLoader;
import org.jikesrvm.classloader.RVMClass;

/**
 * This class must be implemented by the VM vendor.
 * 
 * An instance of class Package contains information about a Java package. This
 * includes implementation and specification versions. Typically this
 * information is retrieved from the manifest.
 * <p>
 * Packages are managed by class loaders. All classes loaded by the same loader
 * from the same package share a Package instance.
 * 
 * 
 * @see ClassLoader
 * @since 1.0
 */
public class Package implements AnnotatedElement {
    /**
     * The defining loader.
     */
    private final ClassLoader loader;

    /**
     * A map of {url<String>, attrs<Manifest>} pairs for caching
     * attributes of bootsrap jars.
     */
    private static SoftReference<Map<String, Manifest>> jarCache;

    /**
     * An url of a source jar, for deffered attributes initialization.
     * After the initialization, if any, is reset to null.
     */
    private String jar;

    private String implTitle;

    private String implVendor;

    private String implVersion;

    private final String name;

    private URL sealBase;

    private String specTitle;

    private String specVendor;

    private String specVersion;

    /**
     * Name must not be null.
     */
    Package(ClassLoader ld, String packageName, String sTitle, String sVersion, String sVendor,
            String iTitle, String iVersion, String iVendor, URL base) {
        loader = ld;
        name = packageName.toString();
        specTitle = sTitle;
        specVersion = sVersion;
        specVendor = sVendor;
        implTitle = iTitle;
        implVersion = iVersion;
        implVendor = iVendor;
        sealBase = base;
    }

    /**
     * Prevent this class from being instantiated
     */
    private Package(){
	loader = null;
	name = null;
    }
    
    /**
     * Gets the annotation associated with the given annotation type and this
     * package.
     * 
     * @return An instance of {@link Annotation} or <code>null</code>.
     * @since 1.5
     * @see java.lang.reflect.AnnotatedElement#getAnnotation(java.lang.Class)
     */
    public <T extends Annotation> T getAnnotation(Class<T> annotationType) {
	if(annotationType == null) {
	    throw new NullPointerException();
	}
	Annotation aa[] = getAnnotations();
	for (int i = 0; i < aa.length; i++) {
	    if(aa[i].annotationType().equals(annotationType)) {
		return (T) aa[i];
	    }
	}
	return null;
    }

    /**
     * Gets all of the annotations associated with this package.
     * 
     * @return An array of {@link Annotation} instances, which may be empty.
     * @since 1.5
     * @see java.lang.reflect.AnnotatedElement#getAnnotations()
     */
    public Annotation[] getAnnotations() {
        return getDeclaredAnnotations();
    }

    /**
     * Gets all of the annotations directly declared on this element.
     * 
     * @return An array of {@link Annotation} instances, which may be empty.
     * @since 1.5
     * @see java.lang.reflect.AnnotatedElement#getDeclaredAnnotations()
     */
    public Annotation[] getDeclaredAnnotations() {
	Class pkgInfo;
	try
	{
	    pkgInfo = Class.forName(name + ".package-info", false, loader);
	}
	catch (ClassNotFoundException _)
	{
	    return new Annotation[0];
	}
	return pkgInfo.getDeclaredAnnotations();
    }

    /**
     * Indicates whether or not the given annotation is present.
     * 
     * @return A value of <code>true</code> if the annotation is present,
     *         otherwise <code>false</code>.
     * @since 1.5
     * @see java.lang.reflect.AnnotatedElement#isAnnotationPresent(java.lang.Class)
     */
    public boolean isAnnotationPresent(Class<? extends Annotation> annotationType) {
	return getAnnotation(annotationType) != null;
    }

    /**
     * Return the title of the implementation of this package, or null if this
     * is unknown. The format of this string is unspecified.
     * 
     * @return The implementation title, or null
     */
    public String getImplementationTitle() {
        if (jar != null) {
            init();
        }
        return implTitle;
    }

    /**
     * Return the name of the vendor or organization that provided this
     * implementation of the package, or null if this is unknown. The format of
     * this string is unspecified.
     * 
     * @return The implementation vendor name, or null
     */
    public String getImplementationVendor() {
        if (jar != null) {
            init();
        }
        return implVendor;
    }

    /**
     * Return the version of the implementation of this package, or null if this
     * is unknown. The format of this string is unspecified.
     * 
     * @return The implementation version, or null
     */
    public String getImplementationVersion() {
        if (jar != null) {
            init();
        }
        return implVersion;
    }

    /**
     * Return the name of this package in the standard dot notation; for
     * example: "java.lang".
     * 
     * @return The name of this package
     */
    public String getName() {
        return name;
    }

    /**
     * Attempt to locate the requested package in the caller's class loader. If
     * no package information can be located, null is returned.
     * 
     * @param packageName The name of the package to find
     * @return The package requested, or null
     * 
     * @see ClassLoader#getPackage
     */
    public static Package getPackage(String packageName) {
	ClassLoader callerLoader = RVMClass.getClassLoaderFromStackFrame(1);
        return callerLoader == null ?
	    BootstrapClassLoader.getBootstrapClassLoader().getPackage(packageName) :
	    callerLoader.getPackage(packageName);
    }

    /**
     * Return all the packages known to the caller's class loader.
     * 
     * @return All the packages known to the caller's class loader
     * 
     * @see ClassLoader#getPackages
     */
    public static Package[] getPackages() {
        ClassLoader callerLoader = RVMClass.getClassLoaderFromStackFrame(1);
        if (callerLoader == null) {
	    return BootstrapClassLoader.getBootstrapClassLoader().getPackages();
        }
        return callerLoader.getPackages();
    }

    /**
     * Return the title of the specification this package implements, or null if
     * this is unknown.
     * 
     * @return The specification title, or null
     */
    public String getSpecificationTitle() {
        if (jar != null) {
            init();
        }
        return specTitle;
    }

    /**
     * Return the name of the vendor or organization that owns and maintains the
     * specification this package implements, or null if this is unknown.
     * 
     * @return The specification vendor name, or null
     */
    public String getSpecificationVendor() {
        if (jar != null) {
            init();
        }
        return specVendor;
    }

    /**
     * Return the version of the specification this package implements, or null
     * if this is unknown. The version string is a sequence of non-negative
     * integers separated by dots; for example: "1.2.3".
     * 
     * @return The specification version string, or null
     */
    public String getSpecificationVersion() {
        if (jar != null) {
            init();
        }
        return specVersion;
    }

    /**
     * Answers an integer hash code for the receiver. Any two objects which
     * answer <code>true</code> when passed to <code>equals</code> must
     * answer the same value for this method.
     * 
     * @return the receiver's hash
     */
    @Override
    public int hashCode() {
        return name.hashCode();
    }

    /**
     * Return true if this package's specification version is compatible with
     * the specified version string. Version strings are compared by comparing
     * each dot separated part of the version as an integer.
     * 
     * @param desiredVersion The version string to compare against
     * @return true if the package versions are compatible, false otherwise
     * 
     * @throws NumberFormatException if the package's version string or the one
     *         provided is not in the correct format
     */
    public boolean isCompatibleWith(String desiredVersion) throws NumberFormatException {

        if (jar != null) {
            init();
        }

        if (specVersion == null || specVersion.length() == 0) {
            throw new NumberFormatException(
		"No specification version defined for the package");
        }

        if (!specVersion.matches("[\\p{javaDigit}]+(.[\\p{javaDigit}]+)*")) {
            throw new NumberFormatException(
                    "Package specification version is not of the correct dotted form : "
                    + specVersion);
        }

        if (desiredVersion == null || desiredVersion.length() == 0) {
            throw new NumberFormatException("Empty version to check");
        }

        if (!desiredVersion.matches("[\\p{javaDigit}]+(.[\\p{javaDigit}]+)*")) {
            throw new NumberFormatException(
                    "Desired version is not of the correct dotted form : "
                    + desiredVersion);
        }

        StringTokenizer specVersionTokens = new StringTokenizer(specVersion,
								".");

        StringTokenizer desiredVersionTokens = new StringTokenizer(
	    desiredVersion, ".");

        try {
            while (specVersionTokens.hasMoreElements()) {
                int desiredVer = Integer.parseInt(desiredVersionTokens
						  .nextToken());
                int specVer = Integer.parseInt(specVersionTokens.nextToken());
                if (specVer != desiredVer) {
                    return specVer > desiredVer;
                }
            }
        } catch (NoSuchElementException e) {
	    /*
	     * run out of tokens for desiredVersion
	     */
        }

        /*
         *   now, if desired is longer than spec, and they have been
         *   equal so far (ex.  1.4  <->  1.4.0.0) then the remainder
         *   better be zeros
         */

        while (desiredVersionTokens.hasMoreTokens()) {
	    if (0 != Integer.parseInt(desiredVersionTokens.nextToken())) {
		return false;
	    }
        }

        return true;
    }

    /**
     * Return true if this package is sealed, false otherwise.
     * 
     * @return true if this package is sealed, false otherwise
     */
    public boolean isSealed() {
        if (jar != null) {
            init();
        }
        return sealBase != null;
    }

    /**
     * Return true if this package is sealed with respect to the specified URL,
     * false otherwise.
     * 
     * @param url the URL to test
     * @return true if this package is sealed, false otherwise
     */
    public boolean isSealed(URL url) {
        if (jar != null) {
            init();
        }
        return url.equals(sealBase);
    }

    /**
     * Answers a string containing a concise, human-readable description of the
     * receiver.
     * 
     * @return a printable representation for the receiver.
     */
    @Override
    public String toString() {
        if (jar != null) {
            init();
        }
        return "package " + name + (specTitle != null ? " " + specTitle : "")
	    + (specVersion != null ? " " + specVersion : "");
    }

    /**
     * Performs initialization of optional attributes, if the source jar location
     * was specified in the lazy constructor.
     */
    private void init() {
        try {
            Map<String, Manifest> map = null;
            Manifest manifest = null;
            URL sealURL = null;
            if (jarCache != null && (map = jarCache.get()) != null) {
                manifest = map.get(jar);
            }

            if (manifest == null) {
                final URL url = sealURL = new URL(jar);

                manifest = AccessController.doPrivileged(
                    new PrivilegedAction<Manifest>() {
                        public Manifest run()
                        {
                            try {
                                return ((JarURLConnection)url
                                        .openConnection()).getManifest();
                            } catch (Exception e) {
                                return new Manifest();
                            }
                        }
                    });
                if (map == null) {
                    map = new Hashtable<String, Manifest>();
                    if (jarCache == null) {
                        jarCache = new SoftReference<Map<String, Manifest>>(map);
                    }
                }
                map.put(jar, manifest);
            }

            Attributes mainAttrs = manifest.getMainAttributes();
            Attributes pkgAttrs = manifest.getAttributes(name.replace('.','/')+"/");

            specTitle = pkgAttrs == null || (specTitle = pkgAttrs
					     .getValue(Attributes.Name.SPECIFICATION_TITLE)) == null
		? mainAttrs.getValue(Attributes.Name.SPECIFICATION_TITLE)
		: specTitle;
            specVersion = pkgAttrs == null || (specVersion = pkgAttrs
					       .getValue(Attributes.Name.SPECIFICATION_VERSION)) == null
		? mainAttrs.getValue(Attributes.Name.SPECIFICATION_VERSION)
		: specVersion;
            specVendor = pkgAttrs == null || (specVendor = pkgAttrs
					      .getValue(Attributes.Name.SPECIFICATION_VENDOR)) == null
		? mainAttrs.getValue(Attributes.Name.SPECIFICATION_VENDOR)
		: specVendor;
            implTitle = pkgAttrs == null || (implTitle = pkgAttrs
					     .getValue(Attributes.Name.IMPLEMENTATION_TITLE)) == null
		? mainAttrs.getValue(Attributes.Name.IMPLEMENTATION_TITLE)
		: implTitle;
            implVersion = pkgAttrs == null || (implVersion = pkgAttrs
					       .getValue(Attributes.Name.IMPLEMENTATION_VERSION)) == null
                    ? mainAttrs
		.getValue(Attributes.Name.IMPLEMENTATION_VERSION)
		: implVersion;
            implVendor = pkgAttrs == null || (implVendor = pkgAttrs
					      .getValue(Attributes.Name.IMPLEMENTATION_VENDOR)) == null
		? mainAttrs.getValue(Attributes.Name.IMPLEMENTATION_VENDOR)
		: implVendor;
            String sealed = pkgAttrs == null || (sealed = pkgAttrs
						 .getValue(Attributes.Name.SEALED)) == null ? mainAttrs
		.getValue(Attributes.Name.SEALED) : sealed;
            if (Boolean.valueOf(sealed).booleanValue()) {
                sealBase = sealURL != null ? sealURL : new URL(jar);
            }
        } catch (Exception e) {}
        jar = null;
    }

}
