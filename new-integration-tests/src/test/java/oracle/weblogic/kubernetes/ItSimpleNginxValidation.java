// Copyright (c) 2020, Oracle Corporation and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.weblogic.kubernetes;

import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

import com.google.gson.JsonObject;
import io.kubernetes.client.openapi.models.V1EnvVar;
import io.kubernetes.client.openapi.models.V1LocalObjectReference;
import io.kubernetes.client.openapi.models.V1ObjectMeta;
import io.kubernetes.client.openapi.models.V1Secret;
import io.kubernetes.client.openapi.models.V1SecretReference;
import io.kubernetes.client.openapi.models.V1ServiceAccount;
import oracle.weblogic.domain.AdminServer;
import oracle.weblogic.domain.Cluster;
import oracle.weblogic.domain.Configuration;
import oracle.weblogic.domain.Domain;
import oracle.weblogic.domain.DomainSpec;
import oracle.weblogic.domain.Model;
import oracle.weblogic.domain.ServerPod;
import oracle.weblogic.kubernetes.actions.impl.NginxParams;
import oracle.weblogic.kubernetes.actions.impl.OperatorParams;
import oracle.weblogic.kubernetes.actions.impl.primitive.HelmParams;
import oracle.weblogic.kubernetes.annotations.IntegrationTest;
import oracle.weblogic.kubernetes.annotations.Namespaces;
import oracle.weblogic.kubernetes.annotations.tags.MustNotRunInParallel;
import oracle.weblogic.kubernetes.annotations.tags.Slow;
import oracle.weblogic.kubernetes.extensions.LoggedTest;
import org.awaitility.core.ConditionFactory;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;

import static java.util.concurrent.TimeUnit.MINUTES;
import static java.util.concurrent.TimeUnit.SECONDS;
import static oracle.weblogic.kubernetes.TestConstants.GOOGLE_REPO_URL;
import static oracle.weblogic.kubernetes.TestConstants.K8S_NODEPORT_HOST;
import static oracle.weblogic.kubernetes.TestConstants.NGINX_CHART_NAME;
import static oracle.weblogic.kubernetes.TestConstants.NGINX_RELEASE_NAME;
import static oracle.weblogic.kubernetes.TestConstants.OPERATOR_CHART_DIR;
import static oracle.weblogic.kubernetes.TestConstants.OPERATOR_RELEASE_NAME;
import static oracle.weblogic.kubernetes.TestConstants.REPO_DUMMY_VALUE;
import static oracle.weblogic.kubernetes.TestConstants.REPO_EMAIL;
import static oracle.weblogic.kubernetes.TestConstants.REPO_NAME;
import static oracle.weblogic.kubernetes.TestConstants.REPO_PASSWORD;
import static oracle.weblogic.kubernetes.TestConstants.REPO_REGISTRY;
import static oracle.weblogic.kubernetes.TestConstants.REPO_SECRET_NAME;
import static oracle.weblogic.kubernetes.TestConstants.REPO_USERNAME;
import static oracle.weblogic.kubernetes.TestConstants.STABLE_REPO_NAME;
import static oracle.weblogic.kubernetes.actions.ActionConstants.ARCHIVE_DIR;
import static oracle.weblogic.kubernetes.actions.ActionConstants.MODEL_DIR;
import static oracle.weblogic.kubernetes.actions.ActionConstants.WDT_VERSION;
import static oracle.weblogic.kubernetes.actions.ActionConstants.WIT_BUILD_DIR;
import static oracle.weblogic.kubernetes.actions.TestActions.buildAppArchive;
import static oracle.weblogic.kubernetes.actions.TestActions.createDockerConfigJson;
import static oracle.weblogic.kubernetes.actions.TestActions.createDomainCustomResource;
import static oracle.weblogic.kubernetes.actions.TestActions.createIngress;
import static oracle.weblogic.kubernetes.actions.TestActions.createMiiImage;
import static oracle.weblogic.kubernetes.actions.TestActions.createSecret;
import static oracle.weblogic.kubernetes.actions.TestActions.createServiceAccount;
import static oracle.weblogic.kubernetes.actions.TestActions.defaultAppParams;
import static oracle.weblogic.kubernetes.actions.TestActions.defaultWitParams;
import static oracle.weblogic.kubernetes.actions.TestActions.dockerLogin;
import static oracle.weblogic.kubernetes.actions.TestActions.dockerPush;
import static oracle.weblogic.kubernetes.actions.TestActions.getIngressList;
import static oracle.weblogic.kubernetes.actions.TestActions.getOperatorImageName;
import static oracle.weblogic.kubernetes.actions.TestActions.installNginx;
import static oracle.weblogic.kubernetes.actions.TestActions.installOperator;
import static oracle.weblogic.kubernetes.actions.TestActions.scaleDomain;
import static oracle.weblogic.kubernetes.actions.TestActions.uninstallNginx;
import static oracle.weblogic.kubernetes.assertions.TestAssertions.doesImageExist;
import static oracle.weblogic.kubernetes.assertions.TestAssertions.domainExists;
import static oracle.weblogic.kubernetes.assertions.TestAssertions.isHelmReleaseDeployed;
import static oracle.weblogic.kubernetes.assertions.TestAssertions.isNginxReady;
import static oracle.weblogic.kubernetes.assertions.TestAssertions.operatorIsReady;
import static oracle.weblogic.kubernetes.assertions.TestAssertions.podDoesNotExist;
import static oracle.weblogic.kubernetes.assertions.TestAssertions.podExists;
import static oracle.weblogic.kubernetes.assertions.TestAssertions.podReady;
import static oracle.weblogic.kubernetes.assertions.TestAssertions.serviceExists;
import static oracle.weblogic.kubernetes.utils.FileUtils.checkDirectory;
import static oracle.weblogic.kubernetes.utils.TestUtils.callWebAppAndCheckForServerNameInResponse;
import static oracle.weblogic.kubernetes.utils.TestUtils.getNextFreePort;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.with;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Verify the sample application can be accessed via the ingress controller.
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@DisplayName("Verify the sample application can be accessed via the ingress controller")
@IntegrationTest
class ItSimpleNginxValidation implements LoggedTest {

  // mii constants
  private static final String WDT_MODEL_FILE = "model2-wls.yaml";
  private static final String MII_IMAGE_NAME = "mii-image";
  private static final String APP_NAME = "sample-app";

  // domain constants
  private static final String DOMAIN_VERSION = "v7";
  private static final String API_VERSION = "weblogic.oracle/" + DOMAIN_VERSION;
  private static final int NUMBER_OF_CLUSTERS = 2;
  private static final String CLUSTER_NAME_PREFIX = "cluster-";
  private static final int MANAGED_SERVER_PORT = 8001;

  private static String opNamespace = null;
  private static String domainNamespace = null;
  private static ConditionFactory withStandardRetryPolicy = null;
  private static String dockerConfigJson = "";
  private static HelmParams nginxHelmParams = null;
  private static String nginxNamespace = null;
  private static int nodeportshttp = 0;
  private static int nodeportshttps = 0;

  private final int replicaCount = 2;
  private final String managedServerNameBase = "-managed-server";
  private final String domainUid = "domain1";
  private String curlCmd = null;
  private List<String> expectedServerNamesInAppResponse = new ArrayList<>();

  /**
   * Install operator and NGINX.
   *
   * @param namespaces list of namespaces created by the IntegrationTestWatcher by the
   *                   JUnit engine parameter resolution mechanism
   */
  @BeforeAll
  public static void initAll(@Namespaces(3) List<String> namespaces) {
    // create standard, reusable retry/backoff policy
    withStandardRetryPolicy = with().pollDelay(2, SECONDS)
        .and().with().pollInterval(10, SECONDS)
        .atMost(5, MINUTES).await();

    // get a unique operator namespace
    logger.info("Get a unique namespace for operator");
    assertNotNull(namespaces.get(0), "Namespace list is null");
    opNamespace = namespaces.get(0);

    // get a unique domain namespace
    logger.info("Get a unique namespace for WebLogic domain");
    assertNotNull(namespaces.get(1), "Namespace list is null");
    domainNamespace = namespaces.get(1);

    // get a unique NGINX namespace
    logger.info("Get a unique namespace for NGINX");
    assertNotNull(namespaces.get(2), "Namespace list is null");
    nginxNamespace = namespaces.get(2);

    // install and verify operator
    installAndVerifyOperator();

    // get a free node port for NGINX
    nodeportshttp = getNextFreePort(30305, 30405);
    nodeportshttps = getNextFreePort(30443, 30543);

    // install and verify NGINX
    installAndVerifyNginx();
  }

  @Test
  @Order(1)
  @DisplayName("Create model in image domain with multiple clusters")
  @Slow
  @MustNotRunInParallel
  public void testCreateMiiDomainWithMultiClusters() {

    logger.info("DEBUG: in testCreateMiiDomainWithMultiClusters");

    // admin/managed server name here should match with model yaml in WDT_MODEL_FILE
    final String adminServerPodName = domainUid + "-admin-server";

    // create image with model files
    logger.info("creating image with model file and verify");
    String miiImage = createImageAndVerify();

    // push the image to registry to make the test work in multi node cluster
    if (!REPO_USERNAME.equals(REPO_DUMMY_VALUE)) {
      logger.info("docker login");
      assertTrue(dockerLogin(REPO_REGISTRY, REPO_USERNAME, REPO_PASSWORD), "docker login failed");

      logger.info("docker push image {0} to registry", miiImage);
      assertTrue(dockerPush(miiImage), String.format("docker push failed for image %s", miiImage));
    }

    // Create the V1Secret configuration
    V1Secret repoSecret = new V1Secret()
        .metadata(new V1ObjectMeta()
            .name(REPO_SECRET_NAME)
            .namespace(domainNamespace))
        .type("kubernetes.io/dockerconfigjson")
        .putDataItem(".dockerconfigjson", dockerConfigJson.getBytes());

    boolean secretCreated = assertDoesNotThrow(() -> createSecret(repoSecret),
        String.format("createSecret failed for %s", REPO_SECRET_NAME));
    assertTrue(secretCreated, String.format("createSecret failed while creating secret %s", REPO_SECRET_NAME));

    // create secret for admin credentials
    logger.info("Create secret for admin credentials");
    String adminSecretName = "weblogic-credentials";
    Map<String, String> adminSecretMap = new HashMap<>();
    adminSecretMap.put("username", "weblogic");
    adminSecretMap.put("password", "welcome1");
    secretCreated = assertDoesNotThrow(() -> createSecret(new V1Secret()
        .metadata(new V1ObjectMeta()
            .name(adminSecretName)
            .namespace(domainNamespace))
        .stringData(adminSecretMap)), "Create secret failed with ApiException");
    assertTrue(secretCreated, String.format("create secret failed for %s", adminSecretName));

    // create encryption secret
    logger.info("Create encryption secret");
    String encryptionSecretName = "encryptionsecret";
    Map<String, String> encryptionSecretMap = new HashMap<>();
    encryptionSecretMap.put("username", "weblogicenc");
    encryptionSecretMap.put("password", "weblogicenc");
    secretCreated = assertDoesNotThrow(() -> createSecret(new V1Secret()
        .metadata(new V1ObjectMeta()
            .name(encryptionSecretName)
            .namespace(domainNamespace))
        .stringData(encryptionSecretMap)), "Create secret failed with ApiException");
    assertTrue(secretCreated, String.format("create secret failed for %s", encryptionSecretName));

    // construct the cluster list used for domain custom resource
    List<Cluster> clusterList = new ArrayList<>();
    for (int i = NUMBER_OF_CLUSTERS; i >= 1; i--) {
      clusterList.add(new Cluster()
          .clusterName(CLUSTER_NAME_PREFIX + i)
          .replicas(replicaCount)
          .serverStartState("RUNNING"));
    }

    // create the domain CR
    Domain domain = new Domain()
        .apiVersion(API_VERSION)
        .kind("Domain")
        .metadata(new V1ObjectMeta()
            .name(domainUid)
            .namespace(domainNamespace))
        .spec(new DomainSpec()
            .domainUid(domainUid)
            .domainHomeSourceType("FromModel")
            .image(miiImage)
            .addImagePullSecretsItem(new V1LocalObjectReference()
                .name(REPO_SECRET_NAME))
            .webLogicCredentialsSecret(new V1SecretReference()
                .name(adminSecretName)
                .namespace(domainNamespace))
            .includeServerOutInPodLog(true)
            .serverStartPolicy("IF_NEEDED")
            .serverPod(new ServerPod()
                .addEnvItem(new V1EnvVar()
                    .name("JAVA_OPTIONS")
                    .value("-Dweblogic.StdoutDebugEnabled=false"))
                .addEnvItem(new V1EnvVar()
                    .name("USER_MEM_ARGS")
                    .value("-Djava.security.egd=file:/dev/./urandom ")))
            .adminServer(new AdminServer()
                .serverStartState("RUNNING"))
            .clusters(clusterList)
            .configuration(new Configuration()
                .model(new Model()
                    .domainType("WLS")
                    .runtimeEncryptionSecret(encryptionSecretName))));

    logger.info("Create domain custom resource for domainUid {0} in namespace {1}",
        domainUid, domainNamespace);
    assertTrue(assertDoesNotThrow(() -> createDomainCustomResource(domain),
        String.format("Create domain custom resource failed with ApiException for %s in namespace %s",
            domainUid, domainNamespace)),
        String.format("Create domain custom resource failed with ApiException for %s in namespace %s",
            domainUid, domainNamespace));

    // wait for the domain to exist
    logger.info("Check for domain custom resource in namespace {0}", domainNamespace);
    withStandardRetryPolicy
        .conditionEvaluationListener(
            condition -> logger.info("Waiting for domain {0} to be created in namespace {1} "
                    + "(elapsed time {2}ms, remaining time {3}ms)",
                domainUid,
                domainNamespace,
                condition.getElapsedTimeInMS(),
                condition.getRemainingTimeInMS()))
        .until(domainExists(domainUid, DOMAIN_VERSION, domainNamespace));

    // check admin server pod exist
    logger.info("Check for admin server pod {0} existence in namespace {1}",
        adminServerPodName, domainNamespace);
    checkPodCreated(adminServerPodName);

    // check admin server pod is ready
    logger.info("Wait for admin server pod {0} to be ready in namespace {1}",
        adminServerPodName, domainNamespace);
    checkPodReady(adminServerPodName);

    // check admin service is created
    logger.info("Check admin service {0} is created in namespace {1}",
        adminServerPodName, domainNamespace);
    checkServiceCreated(adminServerPodName);

    // check the readiness for the managed servers in each cluster
    for (int i = 1; i <= NUMBER_OF_CLUSTERS; i++) {
      for (int j = 1; j <= replicaCount; j++) {
        String managedServerPodName =
            domainUid + "-" + CLUSTER_NAME_PREFIX + i + managedServerNameBase + j;

        // check managed server pod is created
        logger.info("Check for managed server pod {0} is created in namespace {1}",
            managedServerPodName, domainNamespace);
        checkPodCreated(managedServerPodName);

        // check managed server pod is ready
        logger.info("Wait for managed server pod {0} to be ready in namespace {1}",
            managedServerPodName, domainNamespace);
        checkPodReady(managedServerPodName);

        // check managed server service is created
        logger.info("Check managed server service {0} is created in namespace {1}",
            managedServerPodName, domainNamespace);
        checkServiceCreated(managedServerPodName);
      }
    }
  }

  @Test
  @Order(2)
  @DisplayName("Create an ingress for each cluster of the WebLogic domain in the specified domain namespace")
  public void testCreateIngress() {

    // create an ingress for each cluster of the domain in the domain namespace
    for (int i = 1; i <= NUMBER_OF_CLUSTERS; i++) {

      String clusterName = CLUSTER_NAME_PREFIX + i;
      String ingressName = domainUid + "-" + clusterName + "-nginx";

      logger.info("Creating ingress {0} for cluster {1} of domain {2} in namespace {3}",
          ingressName, clusterName, domainUid, domainNamespace);
      assertThat(createIngress(ingressName, domainNamespace, domainUid, clusterName,
          MANAGED_SERVER_PORT, domainUid + "." + clusterName + ".test"))
          .as("Test createIngress succeeds")
          .withFailMessage(String.format("failed to create an ingress for cluster %s of domain %s in namespace %s",
              clusterName, domainUid, domainNamespace))
          .isTrue();

      // check the ingress is created
      assertThat(assertDoesNotThrow(() -> getIngressList(domainNamespace)))
          .as(String.format("found the ingress %s in namespace %s", ingressName, domainNamespace))
          .withFailMessage(String.format("can not find ingress %s in namespace %s", ingressName, domainNamespace))
          .contains(ingressName);

      logger.info("ingress {0} for cluster {1} of domain {2} is created in namespace {3}",
          ingressName, clusterName, domainUid, domainNamespace);
    }
  }

  @Test
  @Order(3)
  @DisplayName("Verify the application can be accessed through the ingress controller for each cluster")
  public void testSampleAppThroughIngressController() {

    for (int i = 1; i <= NUMBER_OF_CLUSTERS; i++) {
      String clusterName = CLUSTER_NAME_PREFIX + i;

      expectedServerNamesInAppResponse.clear();
      for (int j = 1; j <= replicaCount; j++) {
        expectedServerNamesInAppResponse.add(clusterName + managedServerNameBase + j);
      }

      // check that NGINX can access the sample apps from all managed servers in the cluster of the domain
      curlCmd = String.format("curl --silent --show-error --noproxy '*' -H 'host: %s' http://%s:%s/sample-war/index.jsp",
          domainUid + "." + clusterName + ".test", K8S_NODEPORT_HOST, nodeportshttp);
      assertThat(callWebAppAndCheckForServerNameInResponse(curlCmd, expectedServerNamesInAppResponse, 50))
          .as("verify NGINX can access the sample app from all managed servers in the domain")
          .withFailMessage("NGINX can not access the sample app from one or more of the managed servers")
          .isTrue();
    }
  }

  @Test
  @Order(4)
  @DisplayName("Verify scale domain with domainUid with multiple clusters in domainNamespace")
  public void testScaleDomainWithMultiClusters() {

    for (int i = 1; i <= NUMBER_OF_CLUSTERS; i++) {
      String clusterName = CLUSTER_NAME_PREFIX + i;

      // set the expected server name list which should return by the app response
      expectedServerNamesInAppResponse.clear();
      for (int j = 1; j <= replicaCount; j++) {
        expectedServerNamesInAppResponse.add(clusterName + managedServerNameBase + j);
      }
      logger.info("expected server name list which should be in the sample app response: {0}",
          expectedServerNamesInAppResponse);

      // get a random integer between [0 - 5]
      int numberOfServers = new Random().nextInt(6);
      logger.info("scaling the cluster {0} of domain {1} in namespace {2} to {3} servers",
          clusterName, domainUid, domainNamespace, numberOfServers);
      scaleAndVerifyDomain(domainUid, domainNamespace, clusterName, numberOfServers, expectedServerNamesInAppResponse);
    }
  }

  /**
   * TODO: remove this after Sankar's PR is merged
   * The cleanup framework does not uninstall NGINX release. Do it here for now.
   */
  @AfterAll
  public void tearDownAll() {
    // uninstall NGINX release
    if (nginxHelmParams != null) {
      assertThat(uninstallNginx(nginxHelmParams))
          .as("Test uninstallNginx returns true")
          .withFailMessage("uninstallNginx() did not return true")
          .isTrue();
    }
  }

  /**
   * Install WebLogic operator and wait until the operator pod is ready.
   */
  private static void installAndVerifyOperator() {

    // Create a service account for the unique opNamespace
    logger.info("Creating service account");
    String serviceAccountName = opNamespace + "-sa";
    assertDoesNotThrow(() -> createServiceAccount(new V1ServiceAccount()
        .metadata(new V1ObjectMeta()
                .namespace(opNamespace)
                .name(serviceAccountName))));
    logger.info("Created service account: {0}", serviceAccountName);

    // get operator image name
    String operatorImage = getOperatorImageName();
    assertFalse(operatorImage.isEmpty(), "operator image name can not be empty");
    logger.info("operator image name {0}", operatorImage);

    // Create docker registry secret in the operator namespace to pull the image from repository
    logger.info("Creating docker registry secret in namespace {0}", opNamespace);
    JsonObject dockerConfigJsonObject = createDockerConfigJson(
        REPO_USERNAME, REPO_PASSWORD, REPO_EMAIL, REPO_REGISTRY);
    dockerConfigJson = dockerConfigJsonObject.toString();

    // Create the V1Secret configuration
    V1Secret repoSecret = new V1Secret()
        .metadata(new V1ObjectMeta()
            .name(REPO_SECRET_NAME)
            .namespace(opNamespace))
        .type("kubernetes.io/dockerconfigjson")
        .putDataItem(".dockerconfigjson", dockerConfigJson.getBytes());

    boolean secretCreated = assertDoesNotThrow(() -> createSecret(repoSecret),
        String.format("createSecret failed for %s", REPO_SECRET_NAME));
    assertTrue(secretCreated, String.format("createSecret failed while creating secret %s in namespace %s",
        REPO_SECRET_NAME, opNamespace));

    // map with secret
    Map<String, Object> secretNameMap = new HashMap<>();
    secretNameMap.put("name", REPO_SECRET_NAME);

    // Helm install parameters
    HelmParams opHelmParams = new HelmParams()
        .releaseName(OPERATOR_RELEASE_NAME)
        .namespace(opNamespace)
        .chartDir(OPERATOR_CHART_DIR);

    // operator chart values to override
    OperatorParams opParams = new OperatorParams()
        .helmParams(opHelmParams)
        .image(operatorImage)
        .imagePullSecrets(secretNameMap)
        .domainNamespaces(Arrays.asList(domainNamespace))
        .serviceAccount(serviceAccountName);

    // install operator
    logger.info("Installing operator in namespace {0}", opNamespace);
    assertTrue(installOperator(opParams),
        String.format("Failed to install operator in namespace %s", opNamespace));
    logger.info("Operator installed in namespace {0}", opNamespace);

    // list Helm releases matching operator release name in operator namespace
    logger.info("Checking operator release {0} status in namespace {1}",
        OPERATOR_RELEASE_NAME, opNamespace);
    assertTrue(isHelmReleaseDeployed(OPERATOR_RELEASE_NAME, opNamespace),
        String.format("Operator release %s is not in deployed status in namespace %s",
            OPERATOR_RELEASE_NAME, opNamespace));
    logger.info("Operator release {0} status is deployed in namespace {1}",
        OPERATOR_RELEASE_NAME, opNamespace);

    // check operator is running
    logger.info("Check operator pod is running in namespace {0}", opNamespace);
    withStandardRetryPolicy
        .conditionEvaluationListener(
            condition -> logger.info("Waiting for operator to be running in namespace {0} "
                    + "(elapsed time {1}ms, remaining time {2}ms)",
                opNamespace,
                condition.getElapsedTimeInMS(),
                condition.getRemainingTimeInMS()))
        .until(operatorIsReady(opNamespace));
  }

  /**
   * Install NGINX and wait until the NGINX pod is ready.
   */
  private static void installAndVerifyNginx() {

    // Helm install parameters
    nginxHelmParams = new HelmParams()
        .releaseName(NGINX_RELEASE_NAME)
        .namespace(nginxNamespace)
        .repoUrl(GOOGLE_REPO_URL)
        .repoName(STABLE_REPO_NAME)
        .chartName(NGINX_CHART_NAME);

    // NGINX chart values to override
    NginxParams nginxParams = new NginxParams()
        .helmParams(nginxHelmParams)
        .nodePortsHttp(nodeportshttp)
        .nodePortsHttps(nodeportshttps);

    // install NGINX
    assertThat(installNginx(nginxParams))
        .as("NGINX is installed successfully")
        .withFailMessage("NGINX installation is failed")
        .isTrue();

    // verify that NGINX is installed
    logger.info("Checking NGINX release {0} status in namespace {1}",
        NGINX_RELEASE_NAME, nginxNamespace);
    assertTrue(isHelmReleaseDeployed(NGINX_RELEASE_NAME, nginxNamespace),
        String.format("NGINX release %s is not in deployed status in namespace %s",
            NGINX_RELEASE_NAME, nginxNamespace));
    logger.info("NGINX release {0} status is deployed in namespace {1}",
        NGINX_RELEASE_NAME, nginxNamespace);

    // wait until the NGINX pod is ready.
    withStandardRetryPolicy
        .conditionEvaluationListener(
            condition -> logger.info(
                "Waiting for NGINX to be ready in namespace {0} (elapsed time {1}ms, remaining time {2}ms)",
                nginxNamespace,
                condition.getElapsedTimeInMS(),
                condition.getRemainingTimeInMS()))
        .until(isNginxReady(nginxNamespace));
  }

  /**
   * Create a Docker image for model in image.
   *
   * @return image name with tag
   */
  private String createImageAndVerify() {

    // create unique image name with date
    DateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd");
    Date date = new Date();
    final String imageTag = dateFormat.format(date) + "-" + System.currentTimeMillis();
    // Add repository name in image name for Jenkins runs
    final String imageName = REPO_USERNAME.equals(REPO_DUMMY_VALUE) ? MII_IMAGE_NAME : REPO_NAME + MII_IMAGE_NAME;
    final String image = imageName + ":" + imageTag;

    // build the model file list
    final List<String> modelList = Collections.singletonList(MODEL_DIR + "/" + WDT_MODEL_FILE);

    // build an application archive using what is in resources/apps/APP_NAME
    assertTrue(buildAppArchive(defaultAppParams()
        .srcDir(APP_NAME)), String.format("Failed to create app archive for %s", APP_NAME));

    // build the archive list
    String zipFile = String.format("%s/%s.zip", ARCHIVE_DIR, APP_NAME);
    final List<String> archiveList = Collections.singletonList(zipFile);

    // Set additional environment variables for WIT
    checkDirectory(WIT_BUILD_DIR);
    Map<String, String> env = new HashMap<>();
    env.put("WLSIMG_BLDDIR", WIT_BUILD_DIR);

    // build an image using WebLogic Image Tool
    logger.info("Create image {0} using model directory {1}", image, MODEL_DIR);
    boolean result = createMiiImage(
        defaultWitParams()
            .modelImageName(imageName)
            .modelImageTag(imageTag)
            .modelFiles(modelList)
            .modelArchiveFiles(archiveList)
            .wdtVersion(WDT_VERSION)
            .env(env)
            .redirect(true));

    assertTrue(result, String.format("Failed to create the image %s using WebLogic Image Tool", image));

    // Check image exists using docker images | grep image tag.
    assertTrue(doesImageExist(imageTag),
        String.format("Image %s does not exist", image));

    return image;
  }

  /**
   * Check pod is created.
   *
   * @param podName pod name to check
   */
  private void checkPodCreated(String podName) {
    withStandardRetryPolicy
        .conditionEvaluationListener(
            condition -> logger.info("Waiting for pod {0} to be created in namespace {1} "
                    + "(elapsed time {2}ms, remaining time {3}ms)",
                podName,
                domainNamespace,
                condition.getElapsedTimeInMS(),
                condition.getRemainingTimeInMS()))
        .until(assertDoesNotThrow(() -> podExists(podName, domainUid, domainNamespace),
            String.format("podExists failed with ApiException for %s in namespace in %s",
                podName, domainNamespace)));

  }

  /**
   * Check pod is ready.
   *
   * @param podName pod name to check
   */
  private void checkPodReady(String podName) {
    withStandardRetryPolicy
        .conditionEvaluationListener(
            condition -> logger.info("Waiting for pod {0} to be ready in namespace {1} "
                    + "(elapsed time {2}ms, remaining time {3}ms)",
                podName,
                domainNamespace,
                condition.getElapsedTimeInMS(),
                condition.getRemainingTimeInMS()))
        .until(assertDoesNotThrow(() -> podReady(podName, domainUid, domainNamespace),
            String.format(
                "pod %s is not ready in namespace %s", podName, domainNamespace)));

  }

  /**
   * Check service is created.
   *
   * @param serviceName service name to check
   */
  private void checkServiceCreated(String serviceName) {
    withStandardRetryPolicy
        .conditionEvaluationListener(
            condition -> logger.info("Waiting for service {0} to be created in namespace {1} "
                    + "(elapsed time {2}ms, remaining time {3}ms)",
                serviceName,
                domainNamespace,
                condition.getElapsedTimeInMS(),
                condition.getRemainingTimeInMS()))
        .until(assertDoesNotThrow(() -> serviceExists(serviceName, null, domainNamespace),
            String.format(
                "Service %s is not ready in namespace %s", serviceName, domainNamespace)));

  }

  /**
   * Check pod does not exist.
   *
   * @param podName pod name to check
   */
  private void checkPodDoesNotExist(String podName) {
    withStandardRetryPolicy
        .conditionEvaluationListener(
            condition -> logger.info("Waiting for pod {0} to be removed in namespace {1} "
                    + "(elapsed time {2}ms, remaining time {3}ms)",
                podName,
                domainNamespace,
                condition.getElapsedTimeInMS(),
                condition.getRemainingTimeInMS()))
        .until(assertDoesNotThrow(() -> podDoesNotExist(podName, null, domainNamespace),
            String.format(
                "Pod %s still exists in namespace %s", podName, domainNamespace)));

  }

  /** Scale a cluster with clusterName to numberOfServers in the domain with domainUid in the specified
   *  domain namespace. Verify the pods are created or deleted depending on the numberOfServers. Also verify
   *  NGINX can access the sample apps to all the servers after the scale operation.
   *
   * @param domainUid the domain with domainUid to scale
   * @param domainNamespace the domain name space the domain resides
   * @param clusterName the cluster in the domain to scale
   * @param numberOfServers the number of servers to be scaled to
   * @param expectedServerNamesInAppResponse the expected server name list in the sample app curl response
   */
  private void scaleAndVerifyDomain(String domainUid,
                                    String domainNamespace,
                                    String clusterName,
                                    int numberOfServers,
                                    List<String> expectedServerNamesInAppResponse) {

    String manageServerPodNamePrefix = domainUid + "-" + clusterName + "-managed-server";

    assertThat(assertDoesNotThrow(() -> scaleDomain(domainUid, domainNamespace, clusterName, numberOfServers)))
        .as(String.format("Verify scale domain with %s in namespace %s", domainUid, domainNamespace))
        .withFailMessage(String.format("can not scale domain %s in namespace %s", domainUid, domainNamespace))
        .isTrue();

    curlCmd = String.format("curl --silent --show-error --noproxy '*' -H 'host: %s' http://%s:%s/sample-war/index.jsp",
        domainUid + "." + clusterName + ".test", K8S_NODEPORT_HOST, nodeportshttp);

    if (replicaCount <= numberOfServers) {

      // check that NGINX can access the sample apps from the original managed servers in the domain
      logger.info("Check that NGINX can access the sample app from the original managed servers in the domain "
                  + "while the domain is scaling up.");
      assertThat(callWebAppAndCheckForServerNameInResponse(curlCmd, expectedServerNamesInAppResponse, 50))
          .as("Verify NGINX can access the sample app from the original managed servers in the domain")
          .withFailMessage("NGINX can not access the sample app from one or more of the managed servers")
          .isTrue();

      // check new managed server pods are created and wait for them to be ready
      for (int i = replicaCount + 1; i <= numberOfServers; i++) {
        String manageServerPodName = manageServerPodNamePrefix + i;

        // check new managed server pods are created
        logger.info("Check for the new managed server pod {0} is created in namespace {1}",
            manageServerPodName, domainNamespace);
        checkPodCreated(manageServerPodName);

        // check new managed server pods are ready
        logger.info("Wait for the new managed server pod {0} to be ready in namespace {1}",
            manageServerPodName, domainNamespace);
        checkPodReady(manageServerPodName);

        // check new managed server services are created
        logger.info("Check for the new managed server service {0} is created in namespace {1}",
            manageServerPodName, domainNamespace);
        checkServiceCreated(manageServerPodName);

        // add the new managed server to the list
        expectedServerNamesInAppResponse.add(clusterName + "-managed-server" + i);
      }

      // check that NGINX can access the sample apps from new and original managed servers
      logger.info("Check that NGINX can access the sample app from the new and original managed servers "
          + "in the domain after the domain is scaled up.");
      assertThat(callWebAppAndCheckForServerNameInResponse(curlCmd, expectedServerNamesInAppResponse, 50))
          .as("NGINX can access the sample app from all managed servers in the domain")
          .withFailMessage("NGINX can not access the sample app from one or more of the managed servers")
          .isTrue();
    } else {
      // scale down
      // wait and check the pods are removed
      for (int i = replicaCount; i > numberOfServers; i--) {
        logger.info("Check managed server pod {0} is removed in namespace {1}",
            manageServerPodNamePrefix + i, domainNamespace);
        checkPodDoesNotExist(manageServerPodNamePrefix + i);
        expectedServerNamesInAppResponse.remove(clusterName + "-managed-server" + i);
      }

      // check that NGINX can access the remaining managed server in the domain
      logger.info("Check that NGINX can access the sample app from the remaining managed servers in the domain "
          + "after the domain is scaled down.");
      assertThat(callWebAppAndCheckForServerNameInResponse(curlCmd, expectedServerNamesInAppResponse, 50))
          .as("Verify NGINX can access the sample app from the remaining managed server in the domain")
          .withFailMessage("NGINX can not access the sample app from the remaining managed server")
          .isTrue();
    }
  }
}
