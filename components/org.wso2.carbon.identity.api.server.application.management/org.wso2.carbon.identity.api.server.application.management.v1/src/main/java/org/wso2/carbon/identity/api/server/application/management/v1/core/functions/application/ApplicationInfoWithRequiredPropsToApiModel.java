/*
 * Copyright (c) 2022, WSO2 Inc. (http://www.wso2.org) All Rights Reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.wso2.carbon.identity.api.server.application.management.v1.core.functions.application;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.wso2.carbon.identity.api.server.application.management.common.ApplicationManagementServiceHolder;
import org.wso2.carbon.identity.api.server.application.management.v1.AdditionalSpProperty;
import org.wso2.carbon.identity.api.server.application.management.v1.AdvancedApplicationConfiguration;
import org.wso2.carbon.identity.api.server.application.management.v1.ApplicationListItem;
import org.wso2.carbon.identity.api.server.application.management.v1.ApplicationResponseModel;
import org.wso2.carbon.identity.api.server.application.management.v1.core.ServerApplicationManagementService;
import org.wso2.carbon.identity.api.server.common.Constants;
import org.wso2.carbon.identity.api.server.common.ContextLoader;
import org.wso2.carbon.identity.application.common.IdentityApplicationManagementException;
import org.wso2.carbon.identity.application.mgt.ApplicationConstants;
import org.wso2.carbon.identity.application.mgt.ApplicationMgtUtil;
import org.wso2.carbon.utils.multitenancy.MultitenantConstants;

import java.util.List;
import java.util.Set;
import java.util.function.Function;

import static org.wso2.carbon.identity.api.server.application.management.common.ApplicationManagementConstants.APPLICATION_MANAGEMENT_PATH_COMPONENT;
import static org.wso2.carbon.identity.application.common.util.IdentityApplicationConstants.IS_MANAGEMENT_APP_SP_PROPERTY_NAME;
import static org.wso2.carbon.identity.application.common.util.IdentityApplicationConstants.TEMPLATE_ID_SP_PROPERTY_NAME;
import static org.wso2.carbon.identity.application.common.util.IdentityApplicationConstants.USE_USER_ID_FOR_DEFAULT_SUBJECT;
import static org.wso2.carbon.identity.application.mgt.dao.impl.ApplicationDAOImpl.USE_DOMAIN_IN_ROLES;
import static org.wso2.carbon.identity.base.IdentityConstants.SKIP_CONSENT;
import static org.wso2.carbon.identity.base.IdentityConstants.SKIP_LOGOUT_CONSENT;

/**
 * Converts the backend model ApplicationBasicInfo into the corresponding API model object.
 */
public class ApplicationInfoWithRequiredPropsToApiModel implements Function<ApplicationResponseModel,
        ApplicationListItem> {

    private static final Log log = LogFactory.getLog(ServerApplicationManagementService.class);

    private static final Set<String> systemApplications =
            ApplicationManagementServiceHolder.getApplicationManagementService().getSystemApplications();

    @Override
    public ApplicationListItem apply(ApplicationResponseModel applicationResponseModel) {

        return new ApplicationListItem()
                .id(applicationResponseModel.getId())
                .name(applicationResponseModel.getName())
                .description(applicationResponseModel.getDescription())
                .image(applicationResponseModel.getImageUrl())
                .accessUrl(applicationResponseModel.getAccessUrl())
                .access(getAccess(applicationResponseModel.getName()))
                .advancedConfigurations(getAdvancedConfigurations(applicationResponseModel))
                .templateId(applicationResponseModel.getTemplateId())
                .self(getApplicationLocation(applicationResponseModel.getId()));
    }

    private AdvancedApplicationConfiguration getAdvancedConfigurations(
            ApplicationResponseModel applicationResponseModel) {

        AdvancedApplicationConfiguration advancedApplicationConfiguration =
                applicationResponseModel.getAdvancedConfigurations();

        if (advancedApplicationConfiguration != null) {
            List<AdditionalSpProperty> additionalSpPropertiesList =
                    advancedApplicationConfiguration.getAdditionalSpProperties();
        /* These properties are part of advanced configurations and hence removing
        them as they can't be packed as a part of additional sp properties again. */
            advancedApplicationConfiguration.
                    setAdditionalSpProperties(removeAndSetSpProperties(additionalSpPropertiesList));
        }
        return advancedApplicationConfiguration;
    }

    private List<AdditionalSpProperty> removeAndSetSpProperties(List<AdditionalSpProperty> propertyList) {

        /* These properties are either first class or part of advanced configurations and hence removing
        them as they can't be packed as a part of additional sp properties again.*/
        propertyList.removeIf(property -> SKIP_CONSENT.equals(property.getName()));
        propertyList.removeIf(property -> SKIP_LOGOUT_CONSENT.equals(property.getName()));
        propertyList.removeIf(property -> USE_DOMAIN_IN_ROLES.equals(property.getName()));
        propertyList.removeIf(property -> USE_USER_ID_FOR_DEFAULT_SUBJECT.equals(property.getName()));
        propertyList.removeIf(property -> TEMPLATE_ID_SP_PROPERTY_NAME.equals(property.getName()));
        propertyList.removeIf(property -> IS_MANAGEMENT_APP_SP_PROPERTY_NAME.equals(property.getName()));
        return propertyList;
    }

    private String getApplicationLocation(String resourceId) {

        return ContextLoader.buildURIForBody(
                Constants.V1_API_PATH_COMPONENT + APPLICATION_MANAGEMENT_PATH_COMPONENT + "/" + resourceId).toString();
    }

    private ApplicationListItem.AccessEnum getAccess(String applicationName) {

        String username = ContextLoader.getUsernameFromContext();
        String tenantDomain = ContextLoader.getTenantDomainFromContext();

        try {
            if (ApplicationConstants.LOCAL_SP.equals(applicationName) ||
                    (MultitenantConstants.SUPER_TENANT_DOMAIN_NAME.equals(tenantDomain) && systemApplications != null
                            && systemApplications.stream().anyMatch(applicationName::equalsIgnoreCase)) ||
                    !ApplicationMgtUtil.isUserAuthorized(applicationName, username)) {
                return ApplicationListItem.AccessEnum.READ;
            }
        } catch (IdentityApplicationManagementException e) {
            log.error("Failed to check user authorization for the application: " + applicationName, e);
            return ApplicationListItem.AccessEnum.READ;
        }

        return ApplicationListItem.AccessEnum.WRITE;
    }

}
