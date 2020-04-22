// Copyright (c) 2020, Oracle Corporation and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.weblogic.kubernetes.utils;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.Callable;

import oracle.weblogic.kubernetes.actions.impl.primitive.Kubernetes;
import org.awaitility.core.ConditionFactory;

import static java.util.concurrent.TimeUnit.MINUTES;
import static java.util.concurrent.TimeUnit.SECONDS;
import static oracle.weblogic.kubernetes.extensions.LoggedTest.logger;
import static org.awaitility.Awaitility.with;

/**
 * The CleanupUtil class is used for cleaning up all the Kubernetes artifacts left behind by the integration tests.
 *
 */
public class CleanupUtil {

  private static ConditionFactory withStandardRetryPolicy = null;

  /**
   * Cleanup all artifacts in the Kubernetes cluster. Waits for the deletion to be completed until up to 3 minutes.
   *
   * @param namespaces list of namespaces
   */
  public static void cleanup(List<String> namespaces) {

    // Delete all the artifacts in the list of namespaces
    deleteArtifacts(namespaces);

    // wait for the artifacts to be deleted waiting for a maximum of 3 minutes
    withStandardRetryPolicy = with().pollDelay(2, SECONDS)
        .and().with().pollInterval(10, SECONDS)
        .atMost(3, MINUTES).await();

    namespaces.stream().map((namespace) -> {
      logger.info("Check for artifacts in namespace {0}", namespace);
      return namespace;
    }).forEachOrdered((namespace) -> {
      withStandardRetryPolicy
          .conditionEvaluationListener(
              condition -> logger.info("Waiting for artifacts to be deleted in namespace {0}, "
                  + "(elapsed time {1} , remaining time {2}",
                  namespace,
                  condition.getElapsedTimeInMS(),
                  condition.getRemainingTimeInMS()))
          .until(CleanupUtil.artifactsDoesntExist(namespace));
    });
  }

  /**
   * Checks if the artifacts in given namespace still exists.
   *
   * @param namespace name of the namespace
   * @return true if none of the artifacts exists, false otherwise
   */
  public static Callable<Boolean> artifactsDoesntExist(String namespace) {
    return () -> {
      boolean doesntExist = true;

      // Check if domain exists
      try {
        if (!Kubernetes.listDomains(namespace).getItems().isEmpty()) {
          logger.info("Domain still exists");
          doesntExist = false;
        }
      } catch (Exception ex) {
        logger.warning(ex.getMessage());
        logger.warning("Failed to list domains");
      }

      // Check if the replica sets exist
      try {
        if (!Kubernetes.listReplicaSets(namespace).getItems().isEmpty()) {
          logger.info("ReplicaSets still exists");
          doesntExist = false;
        }
      } catch (Exception ex) {
        logger.warning(ex.getMessage());
        logger.warning("Failed to list replica sets");
      }

      // check if the jobs exist
      try {
        if (!Kubernetes.listJobs(namespace).getItems().isEmpty()) {
          logger.info("Jobs still exists");
          doesntExist = false;
        }
      } catch (Exception ex) {
        logger.warning(ex.getMessage());
        logger.warning("Failed to list jobs");
      }

      // check if the configmaps exist
      try {
        if (!Kubernetes.listConfigMaps(namespace).getItems().isEmpty()) {
          logger.info("Config Maps still exists");
          doesntExist = false;
        }
      } catch (Exception ex) {
        logger.warning(ex.getMessage());
        logger.warning("Failed to list config maps");
      }

      // check if the secrets exist
      try {
        if (!Kubernetes.listSecrets(namespace).getItems().isEmpty()) {
          logger.info("Secrets still exists");
          doesntExist = false;
        }
      } catch (Exception ex) {
        logger.warning(ex.getMessage());
        logger.warning("Failed to list secrets");
      }

      // check if persistent volume exist
      try {
        for (var item : Kubernetes.listPersistentVolumeClaims(namespace).getItems()) {
          String label = Optional.ofNullable(item)
              .map(pvc -> pvc.getMetadata())
              .map(metadata -> metadata.getLabels())
              .map(labels -> labels.get("weblogic.domainUID")).get();
          if (label != null) {
            if (!Kubernetes.listPersistentVolumes(
                String.format("weblogic.domainUID in (%s)", label))
                .getItems().isEmpty()) {
              logger.info("Persistent Volumes still exists");
              doesntExist = false;
            }
          }
        }
      } catch (Exception ex) {
        logger.warning(ex.getMessage());
        logger.warning("Failed to list persistent volumes");
      }

      // check if deployments exist
      try {
        if (!Kubernetes.listDeployments(namespace).getItems().isEmpty()) {
          logger.info("Deployments still exists");
          doesntExist = false;
        }
      } catch (Exception ex) {
        logger.warning(ex.getMessage());
        logger.warning("Failed to list deployments");
      }

      // get pvc
      try {
        if (!Kubernetes.listPersistentVolumeClaims(namespace).getItems().isEmpty()) {
          logger.info("Persistent Volumes Claims still exists");
          doesntExist = false;
        }
      } catch (Exception ex) {
        logger.warning(ex.getMessage());
        logger.warning("Failed to list persistent volume claims");
      }

      // check if service accounts exist
      try {
        if (!Kubernetes.listServiceAccounts(namespace).getItems().isEmpty()) {
          logger.info("Service Accounts still exists");
          doesntExist = false;
        }
      } catch (Exception ex) {
        logger.warning(ex.getMessage());
        logger.warning("Failed to cleanup service accounts");
      }
      // get namespaces
      try {
        if (Kubernetes.listNamespaces().contains(namespace)) {
          logger.info("Namespace still exists");
          doesntExist = false;
        }
      } catch (Exception ex) {
        logger.warning(ex.getMessage());
        logger.warning("Failed to list namespaces");
      }

      return doesntExist;
    };
  }

  /**
   * Deletes the artifacts in the Kubernetes cluster in the namespaces list.
   *
   * @param namespaces list of namespaces
   */
  public static void deleteArtifacts(List<String> namespaces) {
    for (String namespace : namespaces) {
      logger.info("Cleaning up artifacts in namespace {0}", namespace);

      // Delete all Domain objects in given namespace
      try {
        for (var item : Kubernetes.listDomains(namespace).getItems()) {
          Kubernetes.deleteDomainCustomResource(
              item.getMetadata().getLabels().get("weblogic.domainUID"),
              namespace
          );
        }
      } catch (Exception ex) {
        logger.warning(ex.getMessage());
        logger.warning("Failed to cleanup domains");
      }

      // Delete replicasets
      try {
        for (var item : Kubernetes.listReplicaSets(namespace).getItems()) {
          Kubernetes.deleteReplicaSet(namespace, item.getMetadata().getName());
        }
      } catch (Exception ex) {
        logger.warning(ex.getMessage());
        logger.warning("Failed to cleanup replica sets");
      }

      // Delete jobs
      try {
        for (var item : Kubernetes.listJobs(namespace).getItems()) {
          Kubernetes.deleteJob(namespace, item.getMetadata().getName());
        }
      } catch (Exception ex) {
        logger.warning(ex.getMessage());
        logger.warning("Failed to cleanup jobs");
      }

      // Delete configmaps
      try {
        for (var item : Kubernetes.listConfigMaps(namespace).getItems()) {
          Kubernetes.deleteConfigMap(item.getMetadata().getName(), namespace);
        }
      } catch (Exception ex) {
        logger.warning(ex.getMessage());
        logger.warning("Failed to cleanup config maps");
      }

      // Delete secrets
      try {
        for (var item : Kubernetes.listSecrets(namespace).getItems()) {
          Kubernetes.deleteSecret(item.getMetadata().getName(), namespace);
        }
      } catch (Exception ex) {
        logger.warning(ex.getMessage());
        logger.warning("Failed to cleanup secrets");
      }

      // Delete pv
      try {
        for (var item : Kubernetes.listPersistentVolumeClaims(namespace).getItems()) {
          String label = Optional.ofNullable(item)
              .map(pvc -> pvc.getMetadata())
              .map(metadata -> metadata.getLabels())
              .map(labels -> labels.get("weblogic.domainUID")).get();
          if (label != null) {
            for (var listPersistentVolume : Kubernetes
                .listPersistentVolumes(String.format("weblogic.domainUID in (%s)", label))
                .getItems()) {
              Kubernetes.deletePv(listPersistentVolume.getMetadata().getName());
            }
          }
        }
      } catch (Exception ex) {
        logger.warning(ex.getMessage());
        logger.warning("Failed to cleanup persistent volumes");
      }

      // Delete deployments
      try {
        for (var item : Kubernetes.listDeployments(namespace).getItems()) {
          Kubernetes.deleteDeployments(namespace, item.getMetadata().getName());
        }
      } catch (Exception ex) {
        logger.warning(ex.getMessage());
        logger.warning("Failed to cleanup deployments");
      }

      // Delete pvc
      try {
        for (var item : Kubernetes.listPersistentVolumeClaims(namespace).getItems()) {
          Kubernetes.deletePvc(item.getMetadata().getName(), namespace);
        }
      } catch (Exception ex) {
        logger.warning(ex.getMessage());
        logger.warning("Failed to cleanup persistent volume claims");
      }

      // Delete service accounts
      try {
        for (var item : Kubernetes.listServiceAccounts(namespace).getItems()) {
          Kubernetes.deleteServiceAccount(item.getMetadata().getName(), namespace);
        }
      } catch (Exception ex) {
        logger.warning(ex.getMessage());
        logger.warning("Failed to cleanup service accounts");
      }
      // Delete namespace
      try {
        Kubernetes.deleteNamespace(namespace);
      } catch (Exception ex) {
        logger.warning(ex.getMessage());
        logger.warning("Failed to cleanup namespace");
      }
    }
  }

}
