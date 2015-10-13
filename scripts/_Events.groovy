import grails.util.BuildSettingsHolder

eventCreateWarStart = { warName, stagingDir ->

    // Scan all plugins src/templates for text templates.
    def dirs = BuildSettingsHolder.settings.getPluginDirectories()
    // Finally scan the application's src/templates
    dirs << new File(basedir)

    for (dir in dirs) {
        // Look for FreeMarker templates.
        def templatePath = new File(dir, 'src/templates/freemarker')
        if (templatePath.exists()) {
            println "Copying FreeMarker templates from $dir to war...\n"
            ant.copy(todir: "${stagingDir}/WEB-INF/templates/freemarker", overwrite: true) {
                fileset(dir: templatePath, includes: "**/*.*")
            }
        }
        // Look for CRM templates.
        templatePath = new File(dir, 'src/templates/crm')
        if (templatePath.exists()) {
            println "Copying CRM templates from $dir to war...\n"
            ant.copy(todir: "${stagingDir}/WEB-INF/templates/crm", overwrite: true) {
                fileset(dir: templatePath, includes: "**/*.*")
            }
        }
        // Look for text templates.
        templatePath = new File(dir, 'src/templates/text')
        if (templatePath.exists()) {
            println "Copying text templates from $dir to war...\n"
            ant.copy(todir: "${stagingDir}/WEB-INF/templates/text", overwrite: true) {
                fileset(dir: templatePath, includes: "**/*.*")
            }
        }
    }
}
