#! /usr/bin/env bash
# -*- coding: iso-8859-1 ; mode: shell-script ;-*-
# (C) Copyright © IBM Corp. 2004
#
# $Id$
#

## Shell script for making an optimizing Jikes RVM build.  We proceed
## in two stages.  See the directions in the user's guide, the
## appendix on bootstrapping with Kaffe.

## Alter this to meet your configuration.
## Then source it (in Bash, do ". twostage.sh".)

## This script is an outline of the procedure to follow; it's not
## guaranteed to be correct.

## @author Steven Augart
## @date 27 March 2004

export RVM_ROOT=~/JikesRVM/OSS/Trunk
images=~/JikesRVM/OSS/Images
c=$r/rvm/config/i686-pc-linux-gnu.kaffe.augart

echo "The first stage: Build a working Jikes RVM"
export RVM_HOST_CONFIG=$c
export RVM_TARGET_CONFIG=$c
export RVM_BUILD=${images}.Kaffe/prototype

jconfigure prototype
cd $RVM_BUILD
./jbuild

## Now the second stage

export DONOR_RVM_ROOT=$RVM_ROOT
export DONOR_RVM_BUILD=$RVM_BUILD

export RVM_BUILD=${images}.Kaffe/prototype-opt
c=$r/rvm/config/exp/i686-pc-linux-gnu.kaffe-with-help.augart
export RVM_HOST_CONFIG=$c
export RVM_TARGET_CONFIG=$c
jconfigure prototype-opt
cd $RVM_BUILD
./jbuild 
