#!/bin/bash
# Copyright (c) 2019, 2020, Oracle Corporation and/or its affiliates.
# Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

# Usage: run-update.sh
#
# This script demonstrates the steps for updating a running model
# in image domain by deploying a model configmap that defines
# a new datasource, deploying a new secret that's referenced by
# the configmap, and finally by patching the domain resource
# 'domain restart version'. If the domain is shutdown, then the
# domain should restart, or if the domain is already running, then
# it should roll.
#
# Prerequisites:
#
#    - The domain has already been staged to WORKDIR (such as via the 
#      'run-main.sh' script). The domain may already be up and running,
#      or it can be shutdown...
#
#    - Namespace DOMAIN_NAMESPACE exists (default 'sample-domain1-ns').
#
#    - The WebLogic operator is deployed and monitoring DOMAIN_NAMESPACE.
#
#    - If domain type is JRF, a database is deployed using
#      sample 'kubernetes/samples/scripts/create-oracle-db-service' with
#      access as per the urls and credentials in 'create-secrets.sh'.
#
#    - Optional deployment of traefik, where traefik is
#      monitoring DOMAIN_NAMESPACE.
#
# Optionally set the following env vars:
#
#    WORKDIR
#      Working directory for the sample with at least 10g of space.
#      Defaults to '/tmp/$USER/model-in-image-sample-work-dir'.
#
#    WDT_DOMAIN_TYPE
#      WLS (default), RestrictedJRF, JRF
#
#    Others
#      See 'custom-env.sh'.
#

set -eu
set -o pipefail

SCRIPTDIR="$( cd "$(dirname "$0")" > /dev/null 2>&1 ; pwd -P )"


export INCLUDE_CONFIGMAP=true #tells stage-domain-resource.sh and
                              #create-secrets.sh to account for configmap

#######################################################################
# Stage model configmap from 'SCRIPTDIR/sample-configmap' to 
# 'WORKDIR/configmap'. Then deploy it.

$SCRIPTDIR/stage-model-configmap.sh
$SCRIPTDIR/create-model-configmap.sh

#######################################################################
# Deploy secrets again (will include extra secret needed by configmap).

$SCRIPTDIR/create-secrets.sh

#######################################################################
# Stage domain resource again (will uncomment references to the
# config map and its secret). Then redeploy it. If the domain
# isn't already running this will cause the domain's introspector job
# to run, but the subsequent call to 'patch' below will
# interrupt this action and cause a (redundant) rerun of the job.

$SCRIPTDIR/stage-domain-resource.sh
$SCRIPTDIR/create-domain-resource.sh

#######################################################################
# Patch domain resource restart version. 
#     This will force introspector job to rerun and regenerate WebLogic
#     config with the model files from the configmap. This will also
#     force a subsequent rolling restart.

$SCRIPTDIR/util-patch-restart-version.sh

#######################################################################
# Wait for pods to roll and reach the new restart version.

$SCRIPTDIR/util-wl-pod-wait.sh -p 3

echo "@@"
echo "@@ Info: Voila! Script '$(basename $0)' completed successfully! All pods ready."
echo "@@"
