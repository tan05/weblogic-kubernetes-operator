// Copyright (c) 2020, Oracle Corporation and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.weblogic.kubernetes.utils;

import java.util.Optional;
import java.util.concurrent.Callable;

import oracle.weblogic.kubernetes.actions.impl.primitive.Kubernetes;

import static oracle.weblogic.kubernetes.extensions.LoggedTest.logger;

public class CleanupUtil {

  public static Callable<Boolean> artifactsDoesntExist(String namespace) {
    return () -> {
      boolean doesntExist = true;

      // Check if domain CRD exists
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

      // check if the job exist
      try {
        if (!Kubernetes.listJobs(namespace).getItems().isEmpty()) {
          logger.info("Jobs still exists");
          doesntExist = false;
        }
      } catch (Exception ex) {
        logger.warning(ex.getMessage());
        logger.warning("Failed to list jobs");
      }

      // check if configmaps exist
      try {
        if (!Kubernetes.listConfigMaps(namespace).getItems().isEmpty()) {
          logger.info("Config Maps still exists");
          doesntExist = false;
        }
      } catch (Exception ex) {
        logger.warning(ex.getMessage());
        logger.warning("Failed to list config maps");
      }

      // check if secrets exist
      try {
        if (!Kubernetes.listSecrets(namespace).getItems().isEmpty()) {
          logger.info("Secrets still exists");
          doesntExist = false;
        }
      } catch (Exception ex) {
        logger.warning(ex.getMessage());
        logger.warning("Failed to list secrets");
      }

      // check if pvs exist
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
        logger.warning("Failed to list namespace");
      }

      return doesntExist;
    };
  }

  public static void cleanup(String namespace) {
    logger.info("Collecting logs in namespace : {0}", namespace);

    // get all Domain objects in given namespace
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

    // get replicasets
    try {
      for (var item : Kubernetes.listReplicaSets(namespace).getItems()) {
        Kubernetes.deleteReplicaSets(namespace, item.getMetadata().getName());
      }
    } catch (Exception ex) {
      logger.warning(ex.getMessage());
      logger.warning("Failed to cleanup replica sets");
    }

    // get jobs
    try {
      for (var item : Kubernetes.listJobs(namespace).getItems()) {
        Kubernetes.deleteJob(namespace, item.getMetadata().getName());
      }
    } catch (Exception ex) {
      logger.warning(ex.getMessage());
      logger.warning("Failed to cleanup jobs");
    }

    // get configmaps
    try {
      for (var item : Kubernetes.listConfigMaps(namespace).getItems()) {
        Kubernetes.deleteConfigMap(item.getMetadata().getName(), namespace);
      }
    } catch (Exception ex) {
      logger.warning(ex.getMessage());
      logger.warning("Failed to cleanup config maps");
    }

    // get secrets
    try {
      for (var item : Kubernetes.listSecrets(namespace).getItems()) {
        Kubernetes.deleteSecret(item.getMetadata().getName(), namespace);
      }
    } catch (Exception ex) {
      logger.warning(ex.getMessage());
      logger.warning("Failed to cleanup secrets");
    }

    // get pv based on the weblogic.domainUID in pvc
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

    // get deployments
    try {
      for (var item : Kubernetes.listDeployments(namespace).getItems()) {
        Kubernetes.deleteDeployments(namespace, item.getMetadata().getName());
      }
    } catch (Exception ex) {
      logger.warning(ex.getMessage());
      logger.warning("Failed to cleanup deployments");
    }

    // get pvc
    try {
      for (var item : Kubernetes.listPersistentVolumeClaims(namespace).getItems()) {
        Kubernetes.deletePvc(item.getMetadata().getName(), namespace);
      }
    } catch (Exception ex) {
      logger.warning(ex.getMessage());
      logger.warning("Failed to cleanup persistent volume claims");
    }

    // get service accounts
    try {
      for (var item : Kubernetes.listServiceAccounts(namespace).getItems()) {
        Kubernetes.deleteServiceAccount(item.getMetadata().getName(), namespace);
      }
    } catch (Exception ex) {
      logger.warning(ex.getMessage());
      logger.warning("Failed to cleanup service accounts");
    }
    // get namespaces
    try {
      Kubernetes.deleteNamespace(namespace);
    } catch (Exception ex) {
      logger.warning(ex.getMessage());
      logger.warning("Failed to cleanup namespace");
    }
  }

}
