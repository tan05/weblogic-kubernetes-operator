// Copyright (c) 2020, Oracle Corporation and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.weblogic.kubernetes;

import java.util.List;
import java.util.Map;
import java.util.Properties;

import oracle.weblogic.kubernetes.annotations.Namespaces;
import oracle.weblogic.kubernetes.extensions.IntegrationTestWatcher;
import oracle.weblogic.kubernetes.extensions.LoggedTest;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

@ExtendWith(IntegrationTestWatcher.class)
public class ItATest1 implements LoggedTest {

  @BeforeAll
  public void beforeAll(@Namespaces(5) List namespaces) {
    logger.info(namespaces.toString());
    logger.info("BeforeAll");
  }

  @AfterAll
  public void afterAll() {
    logger.info("AfterAll");

  }

  @BeforeEach
  public void beforeEach() {
    logger.info("BeforeEach");
  }

  @AfterEach
  public void afterEach() {
    logger.info("AfterEach");
  }

  @Test
  public void test1(List namespaces) {
    logger.info(namespaces.toString());
  }

  private static final String LOGS_DIR = System.getProperty("user.dirvalue",
      System.getProperty("java.io.tmpdir"));

  public static void main(String[] args) {
    System.out.println(LOGS_DIR);
    System.out.println("SYSTEM PROPERTIES");
    Properties properties = System.getProperties();
    for (Map.Entry<Object, Object> entry : properties.entrySet()) {
      Object key = entry.getKey();
      Object val = entry.getValue();
      System.out.println("key : " + key + "value : " + val);
    }
    System.out.println("ENV VARIABLES");
    Map<String, String> env = System.getenv();
    for (Map.Entry<String, String> entry : env.entrySet()) {
      Object key = entry.getKey();
      Object val = entry.getValue();
      System.out.println("key : " + key + "value : " + val);
    }
  }

}
