#
# (C) Copyright IBM Corp. 2001
#
# $Id$

# @author Peter Sweeney
# @date 11/1/2001

#
# Files that don't have a copyright notice
#
/\/bin\/ids\/rvmrt.stamp/ { next }
/\/bin\/ids\/set\/R-/ { next }

/\/doc\/userguide\// { next }

/\/regression\/tests\/SPECjbb2000\/SPECjbb./ { next }

/\/tests\/javalex\/qb1.lex.ref/ { next }

/\/tools\/bootImageRunner\/VM_0005fInetAddress.h/ { next }
/\/tools\/bootImageWriter\/rvm.security/ { next }
/\/tools\/jburg\/COPYRIGHT/ { next }
/\/tools\/jdp\/sanityTest.jdp/ { next }

/\/ReleaseNotes-/ { next }

/\/TimeLimit.sanity/ { next }
/\/TimeLimit.performance/ { next }

/\.properties$/ { next }
/\.properties\.sample$/ { next }
/README$/ { next }

#
# print everything else
#
/.*/
