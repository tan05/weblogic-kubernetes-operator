// Copyright (c) 2020, Oracle Corporation and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.weblogic.kubernetes.utils;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;

import io.kubernetes.client.openapi.ApiException;
import oracle.weblogic.kubernetes.actions.impl.primitive.Kubernetes;

import static io.kubernetes.client.util.Yaml.dump;
import static oracle.weblogic.kubernetes.extensions.LoggedTest.logger;

/**
 * A utility class to collect logs for artifacts in Kubernetes cluster.
 */
public class LoggingUtil {

  /**
   * Directory to store logs.
   */
  private static final String LOGS_DIR = System.getProperty("RESULT_ROOT",
      System.getProperty("java.io.tmpdir"));

  /**
   * Collect logs for artifacts in Kubernetes cluster for current running test object. This method can be called
   * anywhere in the test by passing the test instance object and list namespaces.
   *
   * <p>
   * The collected logs are written in the LOGS_DIR/IT_TEST_CLASSNAME/CURRENT_TIMESTAMP directory.
   *
   * @param itInstance the integration test instance
   * @param namespaces list of namespaces used by the test instance
   */
  public static void collectLogs(Object itInstance, List namespaces) throws IOException {
    logger.info("Collecting logs...");
    String resultDirExt = new SimpleDateFormat("yyyyMMddHHmmss").format(new Date());

    Path resultDir = Files.createDirectories(
        Paths.get(LOGS_DIR, itInstance.getClass().getSimpleName(),
            resultDirExt));
    for (var namespace : namespaces) {
      LoggingUtil.generateLog((String) namespace, resultDir);
    }
  }

  /**
   * Queries the Kubernetes cluster to get the logs for various artifacts and writes it to the resultDir.
   *
   * @param namespace in which to query cluster for artifacts
   * @param resultDir existing directory to write log files
   * @throws IOException when writing to log files fail
   * @throws ApiException when Kubernetes cluster query fails
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

    // get pv based on the weblogic.domainUID in pvc
    try {
      for (var pvc : Kubernetes.listPersistentVolumeClaims(namespace).getItems()) {
        if (pvc.getMetadata() != null
            && pvc.getMetadata().getLabels() != null
            && pvc.getMetadata().getLabels().get("weblogic.domainUID") != null) {
          String label = pvc.getMetadata().getLabels().get("weblogic.domainUID");
          writeToFile(Kubernetes.listPersistentVolumes(
              String.format("weblogic.domainUID in (%s)", label)), resultDir.toString(), label + "_pv.log");
        }
      }
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
              namespace + "_" + pod.getMetadata().getName() + ".log");
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

}
