#
# (C) Copyright IBM Corp. 2001
#
# $Id$

# @author Peter Sweeney
# @date 11/1/2001

#
# Files that don't have a CVS Ids tag
#
/\/bin\/ids\/rvmrt.stamp/ { next }
/\/bin\/ids\/set\/R-/ { next }

/\/doc\/userguide\// { next }

/\/regression\/tests\/javalex\/qb1.lex.ref/ {next}
/\/regression\/tests\/SPECjbb2000\/SPECjbb./ { next }

/\/src\/tools\/preprocessor\/testFancy.preprocessor/ { next }
/\/src\/tools\/preprocessor\/testSimple.preprocessor/ { next }

/\/tools\/bootImageRunner\/VM_0005fInetAddress.h/ { next }
/\/tools\/bootImageWriter\/rvm.security/ { next }
/\/tools\/jburg\/COPYRIGHT/ { next }
/\/tools\/jdp\/sanityTest.jdp/ { next }

/\/ids\/rvmrt.stamp/ { next }

/\/ReleaseNotes-/ { next }

/\/TimeLimit.sanity/ { next }
/\/TimeLimit.performance/ { next }

#
# print everything else
#
/.*/
