#!/bin/ksh
#
# (C) Copyright IBM Corp. 2001
#
# $Id$
#
# @author Julian Dolby

ulimit -t $1
shift

exec "$@"
