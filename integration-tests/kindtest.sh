#!/bin/bash
KINDTEST="/scratch/$USER/kindtest"
export PV_ROOT="$KINDTEST/k8s-pvroot"
mkdir -m777 -p "$PV_ROOT"
export RESULT_ROOT="$KINDTEST/wl_k8s_test_results"
mkdir -m777 -p "$RESULT_ROOT"

echo 'Remove old cluster (if any)...'
kind delete cluster

echo 'Create cluster...'
cp kind-config.yaml.template "$KINDTEST/kind-config.yaml"
sed -i -e "s|HOSTPATH|${KINDTEST}|g" "$KINDTEST/kind-config.yaml"
kind create cluster --config="$KINDTEST/kind-config.yaml"

echo 'Set up test running ENVVARs...'
export KIND="true"

#FIXME by externalizing
export DOCKER_USERNAME="markxnelson"
export DOCKER_PASSWORD="think1up@"
export DOCKER_EMAIL="mark.x.nelson@oracle.com"
export REPO_USERNAME="weblogick8s/cloudlogic"
export REPO_PASSWORD="5GFBY+k;ru.<A9WWu3bg"
export REPO_REGISTRY="phx.ocir.io"
export REPO_EMAIL="mark.x.nelson@oracle.com"
export OCR_USERNAME="mark.x.nelson@oracle.com"
export OCR_PASSWORD="DerekDoesntKnowMyPassword"

export K8S_NODEPORT_HOST=`docker inspect kind-worker | jq '.[].NetworkSettings.IPAddress' | sed 's/"//g'`

# echo 'Copy images into kind...'
# docker login -u $DOCKER_USERNAME -p $DOCKER_PASSWORD
# docker login phx.ocir.io -u $REPO_USERNAME -p $REPO_PASSWORD
# docker login container-registry.oracle.com -u $OCR_USERNAME -p $OCR_PASSWORD

echo 'Clean up result root...'
rm -rf "${RESULT_ROOT:?}/*"

echo 'Run tests...'
time mvn -DPARALLEL=true \
     -P wls-integration-tests \
     clean \
     verify