/*
 * Copyright 2014 Goran Ehrsson.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package grails.plugins.crm.content

import grails.util.BuildSettingsHolder
import groovy.io.FileType
import org.apache.commons.lang.StringUtils

/**
 * Import content from file system.
 */
class CrmContentImportService {

    def grailsApplication
    def crmContentService

    /**
     * Import files from file system.
     * @param folderName name of folder below 'src' or 'WEB-INF' where content will be imported from
     * @param username owner of imported content or null for current executing user
     * @return number of imported files
     */
    int importFiles(final String folderName, String username = null) {
        List<File> templates
        if (grailsApplication.warDeployed) {
            templates = grailsApplication.mainContext.getResources("**/WEB-INF/${folderName}/**".toString())?.toList().collect {
                it.file
            }
        } else {
            // Scan all plugins src/templates for text templates.
            def settings = BuildSettingsHolder.settings
            def dirs = settings.getPluginDirectories()
            // Finally scan the application's src/templates
            dirs << settings.getBaseDir()
            templates = []
            for (dir in dirs) {
                // Look for FreeMarker templates.
                def templatePath = new File(dir, "src/${folderName}")
                if (templatePath.exists()) {
                    templatePath.eachFileRecurse(FileType.FILES) { file ->
                        templates << file
                    }
                }
            }
        }

        int counter = 0

        if (templates) {
            for (file in templates.findAll { it.file && !it.hidden }) {
                def path = StringUtils.substringAfter(file.parentFile.toString(), "/${folderName}".toString())
                def folder = crmContentService.getFolder(path)
                if (!folder) {
                    folder = crmContentService.createFolders(path)
                }
                crmContentService.createResource(file, null, folder, [status: "shared", username: username, overwrite: true])
                log.debug "Loaded template $file into folder /${folder.path.join('/')}"
                counter++
            }
        }
        return counter
    }
}
