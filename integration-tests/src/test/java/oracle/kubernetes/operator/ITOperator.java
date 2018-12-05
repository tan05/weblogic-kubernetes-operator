// Copyright 2018, Oracle Corporation and/or its affiliates.  All rights reserved.
// Licensed under the Universal Permissive License v 1.0 as shown at
// http://oss.oracle.com/licenses/upl.

package oracle.kubernetes.operator;

import oracle.kubernetes.operator.utils.Domain;
import oracle.kubernetes.operator.utils.ExecCommand;
import oracle.kubernetes.operator.utils.ExecResult;
import oracle.kubernetes.operator.utils.Operator;
import oracle.kubernetes.operator.utils.TestUtils;
import org.junit.AfterClass;
import org.junit.Assume;
import org.junit.BeforeClass;
import org.junit.FixMethodOrder;
import org.junit.Test;
import org.junit.runners.MethodSorters;

/**
 * Simple JUnit test file used for testing Operator.
 *
 * <p>This test is used for creating Operator(s) and multiple domains which are managed by the
 * Operator(s).
 */
@FixMethodOrder(MethodSorters.NAME_ASCENDING)
public class ITOperator extends BaseTest {

  // property file used to customize operator properties for operator inputs yaml
  private static String op1YamlFile = "operator1.yaml";
  private static String op2YamlFile = "operator2.yaml";
  private static final String opForDelYamlFile1 = "operator_del1.yaml";
  private static final String opForDelYamlFile2 = "operator_del2.yaml";

  // property file used to customize domain properties for domain inputs yaml
  private static String domain1YamlFile = "domain1.yaml";
  private static String domain2YamlFile = "domain2.yaml";
  private static String domain3YamlFile = "domain3.yaml";
  private static String domain4YamlFile = "domain4.yaml";
  private static String domain5YamlFile = "domain5.yaml";
  private static String domain6YamlFile = "domain6.yaml";
  private static String domain7YamlFile = "domain7.yaml";
  private static String domain8YamlFile = "domain8.yaml";
  private static final String domain1ForDelValueYamlFile = "domain_del_1.yaml";
  private static final String domain2ForDelValueYamlFile = "domain_del_2.yaml";
  private static final String domain3ForDelValueYamlFile = "domain_del_3.yaml";
  private static String domain9YamlFile = "domain9.yaml";
  private static String domain10YamlFile = "domain10.yaml";
  private static String domain11YamlFile = "domain11.yaml";

  // property file used to configure constants for integration tests
  private static String appPropsFile = "OperatorIT.properties";

  private static Operator operator1, operator2;

  private static Operator operatorForDel1;
  private static Operator operatorForDel2;

  private static boolean QUICKTEST;
  private static boolean SMOKETEST;
  private static boolean JENKINS;

  // Set QUICKTEST env var to true to run a small subset of tests.
  // Set SMOKETEST env var to true to run an even smaller subset
  // of tests, plus leave domain1 up and running when the test completes.
  static {
    QUICKTEST =
        System.getenv("QUICKTEST") != null && System.getenv("QUICKTEST").equalsIgnoreCase("true");
    SMOKETEST =
        System.getenv("SMOKETEST") != null && System.getenv("SMOKETEST").equalsIgnoreCase("true");
    if (SMOKETEST) QUICKTEST = true;
    if (System.getenv("JENKINS") != null) {
      JENKINS = new Boolean(System.getenv("JENKINS")).booleanValue();
    }
  }

  /**
   * This method gets called only once before any of the test methods are executed. It does the
   * initialization of the integration test properties defined in OperatorIT.properties and setting
   * the resultRoot, pvRoot and projectRoot attributes.
   *
   * @throws Exception
   */
  @BeforeClass
  public static void staticPrepare() throws Exception {
    // initialize test properties and create the directories
    initialize(appPropsFile);
  }

  /**
   * Releases k8s cluster lease
   *
   * @throws Exception
   */
  @AfterClass
  public static void staticUnPrepare() throws Exception {
    logger.info("+++++++++++++++++++++++++++++++++---------------------------------+");
    logger.info("BEGIN");
    logger.info("Run once, release cluster lease");

    StringBuffer cmd =
        new StringBuffer("export RESULT_ROOT=$RESULT_ROOT && export PV_ROOT=$PV_ROOT && ");
    cmd.append(BaseTest.getProjectRoot())
        .append("/integration-tests/src/test/resources/statedump.sh");
    logger.info("Running " + cmd);

    ExecResult result = ExecCommand.exec(cmd.toString());
    if (result.exitValue() == 0) logger.info("Executed statedump.sh " + result.stdout());
    else
      logger.info("Execution of statedump.sh failed, " + result.stderr() + "\n" + result.stdout());

    if (JENKINS) {
      cleanup();
    }

    if (getLeaseId() != "") {
      logger.info("Release the k8s cluster lease");
      TestUtils.releaseLease(getProjectRoot(), getLeaseId());
    }

    logger.info("SUCCESS");
  }

  @Test
  public void test1CreateFirstOperatorAndDomain() throws Exception {

    logTestBegin("test1CreateFirstOperatorAndDomain");
    logger.info("Creating Operator & waiting for the script to complete execution");
    Domain domain1 = null;
    // create operator1
    operator1 = TestUtils.createOperator(op1YamlFile);
    try {
      domain1 = testDomainCreation(domain1YamlFile);
      domain1.verifyDomainCreated();
      testBasicUseCases(domain1);
      testAdvancedUseCasesForADomain(operator1, domain1);

      if (!SMOKETEST) domain1.testWlsLivenessProbe();
    } finally {

      if (domain1 != null && JENKINS) domain1.shutdownUsingServerStartPolicy();
    }

    logger.info("SUCCESS - test1CreateFirstOperatorAndDomain");
  }

  @Test
  public void test2CreateAnotherDomainInDefaultNS() throws Exception {
    Assume.assumeFalse(QUICKTEST);

    logTestBegin("test2CreateAnotherDomainInDefaultNS");
    logger.info("Creating Domain domain2 & verifing the domain creation");
    Domain domain2 = null;

    if (operator1 == null) {
      operator1 = TestUtils.createOperator(op1YamlFile);
    }
    try {
      // create domain2
      domain2 = testDomainCreation(domain2YamlFile);
      domain2.verifyDomainCreated();
      testBasicUseCases(domain2);
    } finally {
      if (domain2 != null && JENKINS) {
        logger.info("Destroy domain2");
        domain2.destroy();
      }
    }
    logger.info("SUCCESS - test2CreateAnotherDomainInDefaultNS");
  }

  @Test
  public void test3CreateDomainInTest1NS() throws Exception {
    Assume.assumeFalse(QUICKTEST);
    logTestBegin("test3CreateDomainInTest1NS");
    logger.info("Creating Domain domain3 & verifing the domain creation");

    if (operator1 == null) {
      operator1 = TestUtils.createOperator(op1YamlFile);
    }
    Domain domain3 = null;
    try {
      // create domain3
      domain3 = testDomainCreation(domain3YamlFile);
      domain3.verifyDomainCreated();
      testBasicUseCases(domain3);
      testWLDFScaling(operator1, domain3);
    } finally {
      if (domain3 != null && JENKINS) {
        domain3.destroy();
      }
    }

    logger.info("SUCCESS - test3CreateDomainInTest1NS");
  }

  @Test
  public void test4CreateAnotherOperatorManagingTest2NS() throws Exception {
    Assume.assumeFalse(QUICKTEST);

    logTestBegin("test4CreateAnotherOperatorManagingTest2NS");
    logger.info("Creating Operator & waiting for the script to complete execution");
    // create operator2
    operator2 = TestUtils.createOperator(op2YamlFile);
    logger.info("SUCCESS - test4CreateAnotherOperatorManagingTest2NS");
  }

  @Test
  public void test5CreateConfiguredDomainInTest2NS() throws Exception {
    Assume.assumeFalse(QUICKTEST);

    logTestBegin("test5CreateConfiguredDomainInTest2NS");
    logger.info("Creating Domain domain4 & verifing the domain creation");

    logger.info("Checking if operator1 and domain1 are running, if not creating");
    if (operator1 == null) {
      operator1 = TestUtils.createOperator(op1YamlFile);
    }

    Domain domain4 = null, domain5 = null;

    try {
      domain4 = testDomainCreation(domain4YamlFile);
      domain4.verifyDomainCreated();
      testBasicUseCases(domain4);
      logger.info("Checking if operator2 is running, if not creating");
      if (operator2 == null) {
        operator2 = TestUtils.createOperator(op2YamlFile);
      }
      // create domain5 with configured cluster
      domain5 = testDomainCreation(domain5YamlFile);
      domain5.verifyDomainCreated();
      testBasicUseCases(domain5);
      logger.info("Verify the only remaining running domain domain4 is unaffected");
      domain4.verifyDomainCreated();

      testClusterScaling(operator2, domain5);

      logger.info("Verify the only remaining running domain domain4 is unaffected");
      domain4.verifyDomainCreated();

      logger.info("Destroy and create domain4 and verify no impact on domain5");
      domain4.destroy();
      domain4.create();

      logger.info("Verify no impact on domain5");
      domain5.verifyDomainCreated();
      logger.info("SUCCESS - test5CreateConfiguredDomainInTest2NS");
    } finally {
      if (domain4 != null && JENKINS) {
        domain4.destroy();
      }
      if (domain5 != null && JENKINS) {
        domain5.destroy();
      }
    }
  }

  @Test
  public void test6CreateDomainWithStartPolicyAdminOnly() throws Exception {
    Assume.assumeFalse(QUICKTEST);

    logTestBegin("test6CreateDomainWithStartPolicyAdminOnly");
    logger.info("Checking if operator1 is running, if not creating");
    if (operator1 == null) {
      operator1 = TestUtils.createOperator(op1YamlFile);
    }
    logger.info("Creating Domain domain6 & verifing the domain creation");
    // create domain6
    Domain domain6 = null;

    try {
      domain6 = testDomainCreation(domain6YamlFile);
      domain6.verifyDomainCreated();
    } finally {
      if (domain6 != null && JENKINS) domain6.destroy();
    }

    logger.info("SUCCESS - test6CreateDomainWithStartPolicyAdminOnly");
  }

  @Test
  public void test7CreateDomainPVReclaimPolicyRecycle() throws Exception {
    Assume.assumeFalse(QUICKTEST);

    logTestBegin("test7CreateDomainPVReclaimPolicyRecycle");
    logger.info("Checking if operator1 is running, if not creating");
    if (operator1 == null) {
      operator1 = TestUtils.createOperator(op1YamlFile);
    }
    logger.info("Creating Domain domain7 & verifing the domain creation");
    // create domain7
    Domain domain7 = null;
    try {
      domain7 = testDomainCreation(domain7YamlFile);
      domain7.verifyDomainCreated();
    } finally {
      if (domain7 != null) domain7.shutdown();
    }
    domain7.deletePVCAndCheckPVReleased();
    logger.info("SUCCESS - test7CreateDomainPVReclaimPolicyRecycle");
  }

  @Test
  public void test8CreateDomainOnExistingDir() throws Exception {
    Assume.assumeFalse(QUICKTEST);

    logTestBegin("test8CreateDomainOnExistingDir");
    if (operator1 == null) {
      operator1 = TestUtils.createOperator(op1YamlFile);
    }

    Domain domain8 = null;

    try {
      domain8 = testDomainCreation(domain8YamlFile);
      domain8.verifyDomainCreated();
    } finally {
      if (domain8 != null && JENKINS) {
        // create domain on existing dir
        domain8.destroy();
      }
    }

    domain8.createDomainOnExistingDirectory();
    logger.info("SUCCESS - test8CreateDomainOnExistingDir");
  }

  // @Test
  public void testACreateDomainApacheLB() throws Exception {
    Assume.assumeFalse(QUICKTEST);

    logTestBegin("testACreateDomainApacheLB");
    logger.info("Creating Domain domain9 & verifing the domain creation");
    if (operator1 == null) {
      operator1 = TestUtils.createOperator(op1YamlFile);
    }

    // create domain9
    Domain domain9 = null;
    try {
      domain9 = testDomainCreation(domain9YamlFile);
      domain9.verifyDomainCreated();
      domain9.verifyAdminConsoleViaLB();
    } finally {
      if (domain9 != null && JENKINS) domain9.destroy();
    }
    logger.info("SUCCESS - testACreateDomainApacheLB");
  }

  @Test
  public void testBCreateDomainWithDefaultValuesInSampleInputs() throws Exception {
    Assume.assumeFalse(QUICKTEST);

    logTestBegin("testBCreateDomainWithDefaultValuesInSampleInputs");
    logger.info("Creating Domain domain10 & verifing the domain creation");
    if (operator1 == null) {
      operator1 = TestUtils.createOperator(op1YamlFile);
    }

    // create domain10
    Domain domain10 = null;
    try {
      domain10 = testDomainCreation(domain10YamlFile);
      domain10.verifyDomainCreated();
      testBasicUseCases(domain10);
      testAdvancedUseCasesForADomain(operator1, domain10);
    } finally {
      if (domain10 != null && JENKINS) domain10.destroy();
    }

    logger.info("SUCCESS - testBCreateDomainWithDefaultValuesInSampleInputs");
  }

  @Test
  public void testDeleteOneDomain() throws Exception {
    Assume.assumeFalse(QUICKTEST);
    logTestBegin("Deleting one domain.");

    if (operatorForDel1 == null) {
      logger.info("About to create operator");
      operatorForDel1 = TestUtils.createOperator(opForDelYamlFile1);
    }
    Domain domain = null;
    try {
      domain = testDomainCreation(domain1ForDelValueYamlFile);
      domain.verifyDomainCreated();
      TestUtils.verifyBeforeDeletion(domain);

      logger.info("About to delete domain: " + domain.getDomainUid());
      TestUtils.deleteWeblogicDomainResources(domain.getDomainUid());

      TestUtils.verifyAfterDeletion(domain);
    } finally {
      if (domain != null && JENKINS) domain.destroy();
    }
    logger.info("SUCCESS - testDeleteOneDomain");
  }

  @Test
  public void testDeleteTwoDomains() throws Exception {
    Assume.assumeFalse(QUICKTEST);
    logTestBegin("Deleting two domains.");

    if (operatorForDel2 == null) {
      logger.info("About to create operator");
      operatorForDel2 = TestUtils.createOperator(opForDelYamlFile2);
    }
    Domain domainDel1 = null, domainDel2 = null;
    try {
      domainDel1 = testDomainCreation(domain2ForDelValueYamlFile);
      domainDel1.verifyDomainCreated();
      domainDel2 = testDomainCreation(domain3ForDelValueYamlFile);
      domainDel2.verifyDomainCreated();

      TestUtils.verifyBeforeDeletion(domainDel1);
      TestUtils.verifyBeforeDeletion(domainDel2);

      final String domainUidsToBeDeleted =
          domainDel1.getDomainUid() + "," + domainDel2.getDomainUid();
      logger.info("About to delete domains: " + domainUidsToBeDeleted);
      TestUtils.deleteWeblogicDomainResources(domainUidsToBeDeleted);

      TestUtils.verifyAfterDeletion(domainDel1);
      TestUtils.verifyAfterDeletion(domainDel2);
    } finally {
      if (domainDel1 != null && JENKINS) domainDel1.destroy();
      if (domainDel2 != null && JENKINS) domainDel2.destroy();
    }
    logger.info("SUCCESS - testDeleteTwoDomains");
  }

  // @Test
  public void testAutoSitConfigOverrides() throws Exception {
    Assume.assumeFalse(QUICKTEST);
    logTestBegin("testAutoSitConfigOverrides");

    if (operator1 == null) {
      operator1 = TestUtils.createOperator(op1YamlFile);
    }
    Domain domain11 = null;
    try {
      // cp py
      copyCreateDomainScript();
      domain11 = testDomainCreation(domain11YamlFile);
      domain11.verifyDomainCreated();
      testBasicUseCases(domain11);
      logger.info("SUCCESS - testAutoSitConfigOverrides");
    } finally {

      StringBuffer cmd = new StringBuffer("cd ");
      cmd.append(BaseTest.getProjectRoot())
          .append(
              "/integration-tests/src/test/resources/domain-home-on-pv && cp create-domain.py.bak create-domain.py");
      logger.info("Running " + cmd);
      ExecResult result = ExecCommand.exec(cmd.toString());
      if (result.exitValue() != 0) {
        throw new RuntimeException(cmd + " failed");
      }

      if (domain11 != null && JENKINS) {
        domain11.destroy();
      }
    }
  }

  private Domain testAdvancedUseCasesForADomain(Operator operator, Domain domain) throws Exception {
    if (!SMOKETEST) {
      testClusterScaling(operator, domain);
      testDomainLifecyle(operator, domain);
      testOperatorLifecycle(operator, domain);
    }
    return domain;
  }

  private Domain testDomainCreation(String domainYamlFile) throws Exception {
    return TestUtils.createDomain(domainYamlFile);
  }

  private void testBasicUseCases(Domain domain) throws Exception {
    testAdminT3Channel(domain);
    testAdminServerExternalService(domain);
  }

  private void copyCreateDomainScript() throws Exception {
    String createDomainScriptPath =
        BaseTest.getProjectRoot() + "/integration-tests/src/test/resources/domain-home-on-pv";

    StringBuffer cmd = new StringBuffer("cd ");
    cmd.append(createDomainScriptPath).append(" && cp create-domain.py create-domain.py.bak");
    logger.info("Running " + cmd);

    ExecResult result = ExecCommand.exec(cmd.toString());
    if (result.exitValue() != 0) {
      throw new RuntimeException(cmd + " failed");
    }

    cmd = new StringBuffer("cd ");
    cmd.append(createDomainScriptPath)
        .append(" && cp create-domain-auto-sit-config.py create-domain.py");
    logger.info("Running " + cmd);
    result = ExecCommand.exec(cmd.toString());
    if (result.exitValue() != 0) {
      throw new RuntimeException(cmd + " failed");
    }
  }
}
