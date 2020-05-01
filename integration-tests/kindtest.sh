#!/bin/bash
if [[ -z "$WORKSPACE" ]]; then
  KINDTEST="/scratch/$USER/kindtest"
else
  KINDTEST="${WORKSPACE}/logdir/${BUILD_TAG}"
fi
mkdir -m777 -p "$KINDTEST"
export RESULT_ROOT="$KINDTEST/wl_k8s_test_results"
export PV_ROOT="$KINDTEST/k8s-pvroot"
mkdir -m777 "$RESULT_ROOT"
mkdir -m777 "$PV_ROOT"

echo 'Remove old cluster (if any)...'
kind delete cluster

echo 'Create cluster...'
cp kind-config.yaml.template "$RESULT_ROOT/kind-config.yaml"
sed -i -e "s|HOSTPATH|${PV_ROOT}|g" "$RESULT_ROOT/kind-config.yaml"
kind create cluster --config="$RESULT_ROOT/kind-config.yaml"
kubectl cluster-info --context kind-kind
kubectl get node -o wide

echo 'Checking for required ENVVARs'
[[ -z "$DOCKER_USERNAME" ]] && { echo "Error: DOCKER_USERNAME must be set"; exit 1; }
[[ -z "$DOCKER_PASSWORD" ]] && { echo "Error: DOCKER_PASSWORD must be set"; exit 1; }
[[ -z "$DOCKER_EMAIL" ]] && { echo "Error: DOCKER_EMAIL must be set"; exit 1; }
[[ -z "$REPO_USERNAME" ]] && { echo "Error: REPO_USERNAME must be set"; exit 1; }
[[ -z "$REPO_PASSWORD" ]] && { echo "Error: REPO_PASSWORD must be set"; exit 1; }
[[ -z "$REPO_REGISTRY" ]] && { echo "Error: REPO_REGISTRY must be set"; exit 1; }
[[ -z "$REPO_EMAIL" ]] && { echo "Error: REPO_EMAIL must be set"; exit 1; }
[[ -z "$OCR_USERNAME" ]] && { echo "Error: OCR_USERNAME must be set"; exit 1; }
[[ -z "$OCR_PASSWORD" ]] && { echo "Error: OCR_PASSWORD must be set"; exit 1; }

echo 'Set up test running ENVVARs...'
export KIND="true"
#export K8S_NODEPORT_HOST=`docker inspect kind-worker | jq '.[].NetworkSettings.IPAddress' | sed 's/"//g'`
export K8s_NODEPORT_HOME=`kubectl get node kind-worker -o jsonpath='{.status.addresses[?(@.type == "InternalIP")].address}'`

echo 'Clean up result root...'
rm -rf "${RESULT_ROOT:?}/*"

echo 'Run tests...'
time mvn -DPARALLEL=true \
     -P wls-integration-tests \
     clean \
     verify 2>&1 | tee "$RESULT_ROOT/kindtest.log"
