#! /bin/sh
#
# (C) Copyright IBM Corp. 2002
#
# $Id$
#
# @author: Martin Trapp
#

basename=`basename $0`
mypath=$RVM_ROOT/rvm/regression/$basename

if [ "x$1" = x-h -o $# = 0 ] ; then
  echo "usage: $basename <seconds> <command>"
  echo "   or: $basename kill <PID> ..."
  echo "       The first form runs the given command for at most 'seconds' seconds."
  echo "       The second form kills the children of the given PID(s)"
  echo "       recursively, i.e. it also kills all their sub-processes."
  exit 1
fi

command=$1
if [ "x$command" = xkill ] ; then
  shift

  # for all argument pids
  while [ $# -gt 0 ] ; do
    pid=$1
    shift

    # ToDO:
    # Think about a portable way to stop $pid from spawning new processes
    
    # find kids and call script recursiveley
    childs=`ps -ef | awk '$3~/'$pid'/{print $2}'`
    if [ "x$childs" != x ] ; then
      $mypath kill $childs
      #echo killing $childs
      kill -9 $childs > /dev/null 2>&1
    fi
  done

else

  interval=$1
  shift
  
  #echo "$basename $interval $@"

  eval "$@" &
  worker=$!
  trap "$mypath kill $worker" INT

  (sleep $interval; $mypath kill $worker)  > /dev/null 2>&1 &
  sleeper=$!

  wait $worker  
  result=$?

  $mypath kill $sleeper

  exit $result

fi


