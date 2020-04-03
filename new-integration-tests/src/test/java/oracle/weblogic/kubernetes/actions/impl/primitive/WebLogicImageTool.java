// Copyright (c) 2020, Oracle Corporation and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.weblogic.kubernetes.actions.impl.primitive;

import java.io.FileNotFoundException;
import java.util.List;

import static oracle.weblogic.kubernetes.actions.ActionConstants.IMAGE_TOOL;
import static oracle.weblogic.kubernetes.actions.ActionConstants.WDT;
import static oracle.weblogic.kubernetes.actions.ActionConstants.WDT_ZIP_PATH;
import static oracle.weblogic.kubernetes.actions.ActionConstants.WIT;
import static oracle.weblogic.kubernetes.extensions.LoggedTest.logger;

/**
 * Implementation of actions that use WebLogic Image Tool to create/update a WebLogic Docker image.
 */

public class WebLogicImageTool extends InstallWITCommon {

  private WITParams params;

  /**
   * Set up the WITParams with the default values
   * @return the instance of WIT 
   */
  public static WITParams withDefaults() {
    return new WITParams().defaults();
  }

  /**
   * Set up the WIT with customized parameters
   * @return the instance of WIT 
   */
  public static WebLogicImageTool withParams(WITParams params) {
    return new WebLogicImageTool().with(params);
  }
  
  private WebLogicImageTool with(WITParams params) {
    this.params = params;
    return this;
  }

  /**
   * Create an image using the params using WIT update command
   * @return true if the command succeeds 
   */
  public boolean updateImage() {
    // download WIT if it is not in the expected location 
    if (!downloadWIT()) {
      logger.warning("Failed to download or unzip WebLogic Image Tool");
      return false;
    } 
   
    // download WDT if it is not in the expected location 
    if (!downloadWDT()) {
      logger.warning("Failed to download WebLogic Deploy Tool");
      return false;
    } 

    try {
      // check if the WIT binary exists, throws FileNotFoundException if the file does not exist
      checkFile(IMAGE_TOOL);

      // delete the old cache entry for the WDT installer
      if (!deleteEntry()) {
        logger.warning("Failed to delete cache entry in WebLogic Image Tool");
        return false;
      }
 
      // add the cache entry for the WDT installer
      if (!addInstaller()) {
        logger.warning("Failed to add installer to WebLogic Image Tool");
        return false;
      }
  
    } catch (FileNotFoundException fnfe) {
      logger.warning("Failed to create an image due to Exception: " + fnfe.getMessage());
      return false;
    }
  
    return executeAndVerify(buildCommand(), params.redirect());
  }
  
  private boolean downloadWIT() {
    // install WIT if needed
    return new Installer()
        .with(new InstallParams()
            .type(WIT)
            .verify(true)
            .unzip(true))
        .download();
  }
  
  private boolean downloadWDT() {
    // install WDT if needed
    return new Installer()
        .with(new InstallParams()
            .type(WDT)
            .verify(true)
            .unzip(false))
        .download();
  } 
  
  private String buildCommand() {
    String command = 
        IMAGE_TOOL 
        + " update "
        + " --tag " + params.modelImageName() + ":" + params.modelImageTag()
        + " --fromImage " + params.baseImageName() + ":" + params.baseImageTag()
        + " --wdtDomainType " + params.domainType()
        + " --wdtModelOnly ";
  
    if (params.modelFiles() != null && params.modelFiles().size() != 0) {
      command += " --wdtModel " + buildList(params.modelFiles());
    }
    if (params.modelVariableFiles() != null && params.modelVariableFiles().size() != 0) {
      command += " --wdtVariables " + buildList(params.modelVariableFiles());
    }
    if (params.modelArchiveFiles() != null && params.modelArchiveFiles().size() != 0) {
      command += " --wdtArchive " + buildList(params.modelArchiveFiles());
    }
  
    return command;
  }

  private String buildList(List<String> list) {
    StringBuilder sbString = new StringBuilder("");
        
    //iterate through ArrayList
    for (String item : list) {
      //append ArrayList element followed by comma
      sbString.append(item).append(",");
    }
        
    //convert StringBuffer to String
    String strList = sbString.toString();
        
    //remove last comma from String if you want
    if (strList.length() > 0) {
      strList = strList.substring(0, strList.length() - 1);
    }
    return strList;
  }
  
  /**
   * Add WDT installer to the WebLogic Image Tool cache
   * @return true if the command succeeds 
   */
  public boolean addInstaller() throws FileNotFoundException {
    try {
      checkFile(WDT_ZIP_PATH);
    } catch (FileNotFoundException fnfe) {
      logger.warning("Failed to create an image due to Exception: " + fnfe.getMessage());
      throw fnfe;
    }
    return executeAndVerify(
        IMAGE_TOOL 
        + " cache addInstaller "
        + " --type wdt"
        + " --version " + params.wdtVersion()
        + " --path " + WDT_ZIP_PATH);
  }
  
  /**
   * Delete the WDT installer cache entry from the WebLogic Image Tool
   * @return true if the command succeeds
   */
  public boolean deleteEntry() {
    return executeAndVerify(
        IMAGE_TOOL 
        + " cache deleteEntry "
        + "--key " + "wdt_" + params.wdtVersion());
  }

}