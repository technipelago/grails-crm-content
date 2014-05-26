<%@ page contentType="text/html;charset=UTF-8" %>
<html>
<head>
    <meta name="layout" content="${grailsApplication.config.crm.content.cms.layout ?: 'main'}"/>
</head>

<body>

<div class="row-fluid">

    <div class="span8 offset1">
        <div class="row-fluid">

            <crm:render template="${(grailsApplication.config.crm.content.cms.path ?: 'pages') + (uri ?: 'index.html')}"
                        extensions="${grailsApplication.config.crm.content.cms.extensions}">
                <h1><g:message code="crmContent.page.not.found.title" default="Page not found!"/></h1>

                <h2><g:message code="crmContent.page.not.found.message" default=""/></h2>
                <% response.setStatus(404) %>
            </crm:render>
        </div>
    </div>

    <div class="span3">
    </div>

</div>

</body>
</html>