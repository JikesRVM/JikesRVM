#
# (C) Copyright IBM Corp. 2001
#
# $Id$

# @author Peter Sweeney
# @date 11/1/2001

#
# Files that don't have a javadoc author tag
#
/\/bin\/ids\/rvmrt.stamp/ { next }
/\/bin\/ids\/set\/R-/ { next }

/\/doc\/userguide\// { next }

/\/regression\/tests\/javalex\/qb1.lex.ref/ {next}
/\/regression\/tests\/jBYTEmark\/jBYTEmark.java/ {next}
/\/regression\/tests\/SPECjbb2000\/SPECjbb./ {next}

/\/src\/examples\/opttests\/Linpack.java/ { next }

/\/src\/tools\/bootImageRunner\/VM_0005fInetAddress.h/ { next }
/\/src\/tools\/bootImageWriter\/rvm.security/ { next }
/\/src\/tools\/jburg\/COPYRIGHT/ { next }
/\/src\/tools\/jdp\/sanityTest.jdp/ { next }
/\/src\/tools\/jburg/ { next }
/\/src\/tools\/preprocessor\/testFancy.preprocessor/ { next }
/\/src\/tools\/preprocessor\/testSimple.preprocessor/ { next }

/\/ReleaseNotes-/ { next }

/\/TimeLimit.sanity/ { next }
/\/TimeLimit.performance/ { next }

#
# print everything else
#
/.*/
