#
# (C) Copyright IBM Corp. 2001
#
# $Id$

# @author Peter Sweeney
# @date 11/1/2001

#
# Files that don't have a javadoc author tag
#
/\/src\/tools\/bootImageRunner\/VM_0005fInetAddress.h/ { next }
/\/src\/tools\/bootImageWriter\/rvm.security/ { next }
/\/src\/tools\/jburg\/COPYRIGHT/ { next }
/\/src\/tools\/jdp\/sanityTest.jdp/ { next }
/\/src\/tools\/jburg/ { next }
/\/src\/tools\/preprocessor\/testFancy.preprocessor/ { next }
/\/src\/tools\/preprocessor\/testSimple.preprocessor/ { next }

/\/src\/examples\/opttests\/Linpack.java/ { next }

/\/rmvrt.stamp/ { next }

/\/userguide\// { next }

/\/ids\/rvmrt.stamp/ { next }

#
# print everything else
#
/.*/
