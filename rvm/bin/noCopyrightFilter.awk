#
# (C) Copyright IBM Corp. 2001
#
# $Id$

# @author Peter Sweeney
# @date 11/1/2001

#
# Files that don't have a copyright notice
#
/\/tools\/bootImageRunner\/VM_0005fInetAddress.h/ { next }
/\/tools\/bootImageWriter\/rvm.security/ { next }
/\/tools\/jburg\/COPYRIGHT/ { next }
/\/tools\/jdp\/sanityTest.jdp/ { next }

/\/tests\/javalex\/qb1.lex.ref/ { next }

/\/rmvrt.stamp/ { next }

/\/userguide\// { next }

/\/ids\/rvmrt.stamp/ { next }

#
# print everything else
#
/.*/
