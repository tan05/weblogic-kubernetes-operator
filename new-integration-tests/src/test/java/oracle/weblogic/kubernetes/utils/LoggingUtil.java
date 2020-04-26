// Copyright (c) 2020, Oracle Corporation and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.weblogic.kubernetes.utils;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.Date;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.Callable;

import io.kubernetes.client.openapi.ApiException;
import io.kubernetes.client.openapi.models.V1Container;
import io.kubernetes.client.openapi.models.V1ObjectMeta;
import io.kubernetes.client.openapi.models.V1PersistentVolumeClaimVolumeSource;
import io.kubernetes.client.openapi.models.V1PersistentVolumeList;
import io.kubernetes.client.openapi.models.V1Pod;
import io.kubernetes.client.openapi.models.V1PodSpec;
import io.kubernetes.client.openapi.models.V1Volume;
import io.kubernetes.client.openapi.models.V1VolumeMount;
import oracle.weblogic.kubernetes.actions.impl.primitive.Kubernetes;
import org.awaitility.core.ConditionFactory;

import static io.kubernetes.client.util.Yaml.dump;
import static java.util.concurrent.TimeUnit.MINUTES;
import static java.util.concurrent.TimeUnit.SECONDS;
import static oracle.weblogic.kubernetes.assertions.TestAssertions.podReady;
import static oracle.weblogic.kubernetes.extensions.LoggedTest.logger;
import static org.awaitility.Awaitility.with;

/**
 * A utility class to collect logs for artifacts in Kubernetes cluster.
 */
public class LoggingUtil {

  /**
   * Directory to store logs.
   */
  private static final String LOGS_DIR = System.getenv().getOrDefault("RESULT_ROOT",
        System.getProperty("java.io.tmpdir"));

  /**
   * Collect logs for artifacts in Kubernetes cluster for current running test object. This method can be called
   * anywhere in the test by passing the test instance object and list namespaces.
   *
   * <p>The collected logs are written in the LOGS_DIR/IT_TEST_CLASSNAME/CURRENT_TIMESTAMP directory.
   *
   * @param itInstance the integration test instance
   * @param namespaces list of namespaces used by the test instance
   */
  public static void collectLogs(Object itInstance, List namespaces) {
    String[] ns = {"itoperator-domainns-1", "itoperator-opns-1"}; //remove after debug
    namespaces = Arrays.asList(ns); //remove after debug
    logger.info("Collecting logs...");
    String resultDirExt = new SimpleDateFormat("yyyyMMddHHmmss").format(new Date());
    Path resultDir;
    try {
      resultDir = Files.createDirectories(
          Paths.get(LOGS_DIR, itInstance.getClass().getSimpleName(),
              resultDirExt));
      for (var namespace : namespaces) {
        LoggingUtil.generateLog((String) namespace, resultDir);
      }
    } catch (IOException ex) {
      logger.severe(ex.getMessage());
    }
  }

  /**
   * Queries the Kubernetes cluster to get the logs for various artifacts and writes it to the resultDir.
   *
   * @param namespace in which to query cluster for artifacts
   * @param resultDir existing directory to write log files
   */
  public static void generateLog(String namespace, Path resultDir) {
    logger.info("Collecting logs in namespace : {0}", namespace);

    // get service accounts
    try {
      writeToFile(Kubernetes.listServiceAccounts(namespace), resultDir.toString(), namespace + "_sa.log");
    } catch (Exception ex) {
      logger.warning(ex.getMessage());
    }

    // get namespaces
    try {
      for (var ns : Kubernetes.listNamespacesAsObjects().getItems()) {
        if (namespace.equals(ns.getMetadata().getName())) {
          writeToFile(ns, resultDir.toString(), namespace + "_ns.log");
        }
      }
    } catch (Exception ex) {
      logger.warning(ex.getMessage());
    }

    // get pvc
    try {
      writeToFile(Kubernetes.listPersistentVolumeClaims(namespace), resultDir.toString(), namespace + "_pvc.log");
    } catch (Exception ex) {
      logger.warning(ex.getMessage());
    }

    // get pv configuration and pv files based on the weblogic.domainUID label in pvc
    try {
      for (var pvc : Kubernetes.listPersistentVolumeClaims(namespace).getItems()) {
        String label = Optional.ofNullable(pvc)
            .map(metadata -> metadata.getMetadata())
            .map(labels -> labels.getLabels())
            .map(labels -> labels.get("weblogic.domainUID")).get();

        // get the persistent volumes based on label weblogic.domainUID
        V1PersistentVolumeList pvList = Kubernetes
            .listPersistentVolumes(String.format("weblogic.domainUID = %s", label));
        // write the persistent volume configurations to log
        writeToFile(pvList, resultDir.toString(), label + "_pv.log");

        // dump files stored in persistent volumes to
        // RESULT_DIR/PVC_NAME/PV_NAME location
        for (var pv : pvList.getItems()) {
          String claimName = pvc.getMetadata().getName();
          String pvName = pv.getMetadata().getName();
          copy(namespace, claimName,
              Files.createDirectories(
                  Paths.get(resultDir.toString(), claimName, pvName)));
        }
        logger.info("Done iterating through the pvs");
      }
    } catch (ApiException apex) {
      logger.warning(apex.getResponseBody());
    } catch (Exception ex) {
      logger.warning(ex.getMessage());
    }

    // get secrets
    try {
      writeToFile(Kubernetes.listSecrets(namespace), resultDir.toString(), namespace + "_secrets.log");
    } catch (Exception ex) {
      logger.warning(ex.getMessage());
    }

    // get configmaps
    try {
      writeToFile(Kubernetes.listConfigMaps(namespace), resultDir.toString(), namespace + "_cm.log");
    } catch (Exception ex) {
      logger.warning(ex.getMessage());
    }

    // get jobs
    try {
      writeToFile(Kubernetes.listJobs(namespace), resultDir.toString(), namespace + "_jobs.log");
    } catch (Exception ex) {
      logger.warning(ex.getMessage());
    }

    // get deployments
    try {
      writeToFile(Kubernetes.listDeployments(namespace), resultDir.toString(), namespace + "_deploy.log");
    } catch (Exception ex) {
      logger.warning(ex.getMessage());
    }

    // get replicasets
    try {
      writeToFile(Kubernetes.listReplicaSets(namespace), resultDir.toString(), namespace + "_rs.log");
    } catch (Exception ex) {
      logger.warning(ex.getMessage());
    }

    // get domain objects in the given namespace
    try {
      writeToFile(Kubernetes.listDomains(namespace), resultDir.toString(), namespace + "_domains.log");
    } catch (Exception ex) {
      logger.warning("Listing domain failed, not collecting any data for domain");
    }

    // get domain/operator pods
    try {
      for (var pod : Kubernetes.listPods(namespace, null).getItems()) {
        if (pod.getMetadata() != null) {
          writeToFile(Kubernetes.getPodLog(pod.getMetadata().getName(), namespace),
              resultDir.toString(),
              namespace + "-pod_" + pod.getMetadata().getName() + ".log");
        }
      }
    } catch (Exception ex) {
      logger.warning(ex.getMessage());
    }
  }

  /**
   * Write the YAML representation of object to a file in the resultDir.
   *
   * @param obj to write to the file as YAML
   * @param resultDir directory in which to write the file
   * @param fileName name of the log file to write
   * @throws IOException when write fails
   */
  private static void writeToFile(Object obj, String resultDir, String fileName) throws IOException {
    logger.info("Generating {0}", Paths.get(resultDir, fileName));
    if (obj != null) {
      Files.write(Paths.get(resultDir, fileName),
          dump(obj).getBytes(StandardCharsets.UTF_8)
      );
    } else {
      logger.info("Nothing to write in {0} list is empty", Paths.get(resultDir, fileName));
    }
  }


  // there is currently a bug in the copy API which leaves i/o stream left open
  // and copy not to exit. As a temporary fix using a Thread to do the copy
  // and discard it after a minute.
  // This won't be necessary once the bug is fixed in the api.
  // https://github.com/kubernetes-client/java/issues/861
  private static void copy(String namespace, String claimName, Path destinationPath) throws ApiException {
    V1Pod pvPod = null;
    CopyThread copypv = null;
    try {
      // create a temporary pod to mount the interested persistent volume
      pvPod = createPVPod(namespace, claimName);

      // create a thread and copy the /shared directory from persistent volume
      copypv = new CopyThread(pvPod, destinationPath);
      copypv.start();

      // wait for the copy task to complete in a minute
      ConditionFactory withStandardRetryPolicy = with().pollDelay(2, SECONDS)
          .and().with().pollInterval(15, SECONDS)
          .atMost(1, MINUTES).await();
      withStandardRetryPolicy
          .conditionEvaluationListener(
              condition -> logger.info("Waiting for copy from pod to be complete, "
                  + "(elapsed time {0} , remaining time {1}",
                  condition.getElapsedTimeInMS(),
                  condition.getRemainingTimeInMS()))
          .until(doneCopying(copypv));
    } catch (ApiException apex) {
      logger.severe(apex.getResponseBody());
    } finally {
      // interrupt the copy thead
      if (copypv.isAlive()) {
        logger.warning("Terminating the copy thread");
        copypv.interrupt();
      }
      // remove the temporary pod
      if (pvPod != null) {
        deletePVPod(namespace);
      }
    }
  }

  /**
   * Create a nginx pod named "pv-pod" with persistent volume from claimName param.
   *
   * @param namespace name of the namespace
   * @param claimName persistent volume claim name
   * @return V1Pod object
   * @throws ApiException when create pod fails
   */
  public static V1Pod createPVPod(String namespace, String claimName) throws ApiException {
    V1Pod pvPod;
    V1Pod podBody = new V1Pod()
        .spec(new V1PodSpec()
            .containers(Arrays.asList(
                new V1Container()
                    .name("pv-container")
                    .image("nginx")
                    .imagePullPolicy("IfNotPresent")
                    .volumeMounts(Arrays.asList(
                        new V1VolumeMount()
                            .name("weblogic-domain-storage-volume")
                            .mountPath("/shared")))))
            .volumes(Arrays.asList(
                new V1Volume()
                    .name("weblogic-domain-storage-volume")
                    .persistentVolumeClaim(
                        new V1PersistentVolumeClaimVolumeSource()
                            .claimName(claimName)))))
        .metadata(new V1ObjectMeta().name("pv-pod"))
        .apiVersion("v1")
        .kind("Pod");
    pvPod = Kubernetes.createPod(namespace, podBody);

    // wait for the pod to come up
    ConditionFactory withStandardRetryPolicy = with().pollDelay(2, SECONDS)
        .and().with().pollInterval(5, SECONDS)
        .atMost(1, MINUTES).await();

    withStandardRetryPolicy
        .conditionEvaluationListener(
            condition -> logger.info("Waiting for pv-pod to be ready in namespace {0}, "
                + "(elapsed time {1} , remaining time {2}",
                namespace,
                condition.getElapsedTimeInMS(),
                condition.getRemainingTimeInMS()))
        .until(podReady("pv-pod", null, namespace));
    logger.info("pv-pod ready.");
    return pvPod;
  }

  /**
   * Delete "pv-pod" pod.
   *
   * @param namespace name of the namespace
   * @throws ApiException when delete fails
   */
  public static void deletePVPod(String namespace) throws ApiException {
    Kubernetes.deletePod("pv-pod", namespace);
  }

  /**
   * Checks if the copying of the PV is done.
   * @param copyThread Thread object doing the copy
   * @return true if done copying otherwise false
   */
  private static Callable<Boolean> doneCopying(Thread copyThread) {
    return () -> {
      return !copyThread.isAlive();
    };
  }

  /**
   * A workaround utility class to copy the contents of persistent volume in a thread.
   */
  private static class CopyThread extends Thread {

    V1Pod pvPod;
    Path destinationPath;

    /**
     * Constructor.
     * @param pod V1Pod object which has access to the PV mount
     * @param destinationPath Path location to copy the /shared contents
     */
    public CopyThread(V1Pod pod, Path destinationPath) {
      this.pvPod = pod;
      this.destinationPath = destinationPath;
    }

    @Override
    public void run() {
      try {
        logger.info("Copying from PV /shared to {0}", destinationPath);
        Kubernetes.copyDirectoryFromPod(pvPod, "/shared", destinationPath);
        logger.info("Done copying.");
      } catch (ApiException ex) {
        logger.severe(ex.getResponseBody());
        logger.severe("Failed to archive persistent volume contents");
      } catch (IOException ex) {
        logger.severe(ex.getMessage());
        logger.severe("Failed to archive persistent volume contents");
      }
    }
  }

}
