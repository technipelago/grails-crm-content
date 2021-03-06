/*
 * Copyright (c) 2014 Goran Ehrsson.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
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
        importFiles(folderName, null, username)
    }

    /**
     * Import files from file system.
     * @param folderName name of folder below 'src' or 'WEB-INF' where content will be imported from
     * @param root target folder
     * @param username owner of imported content or null for current executing user
     * @return number of imported files
     */
    int importFiles(final String folderName, final String root, final String username) {
        final String folderNameUnix = folderName ? StringUtils.replaceChars(folderName, '\\', '/') : null
        List<File> templates
        if (grailsApplication.warDeployed) {
            templates = grailsApplication.mainContext.getResources("**/WEB-INF/${folderNameUnix}/**".toString())?.toList().collect {
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
                // Look for files in src/<folderName>/...
                def templatePath = new File(dir, "src/${folderNameUnix}")
                if (templatePath.exists()) {
                    templatePath.eachFileRecurse(FileType.FILES) { file ->
                        templates << file
                    }
                }
            }
        }

        String rootPath = null
        if (root) {
            rootPath = root.tr('\\/', File.separator + File.separator)
            if (rootPath.endsWith(File.separator)) {
                rootPath = rootPath.substring(0, rootPath.length() - 1)
            }
        }

        int counter = 0
        if (templates) {
            final String folderNameNative = StringUtils.replaceChars(folderName, '\\/', File.separator + File.separator)
            for (file in templates.findAll { it.file && !it.hidden }) {
                String path = StringUtils.substringAfter(file.parentFile.toString(), File.separator + folderNameNative) ?: null
                if(!path) {
                    throw new IllegalArgumentException("Invalid folder [$folderNameNative]")
                }
                if(rootPath) {
                    path = rootPath + path
                }
                def folder = crmContentService.getFolder(path)
                if (!folder) {
                    folder = crmContentService.createFolders(path)
                }
                crmContentService.createResource(file, null, folder, [status: "shared", username: username, overwrite: true])
                log.debug "Loaded template $file into folder /${folder ? folder.path.join('/') : ''}"
                counter++
            }
        }
        return counter
    }
}
