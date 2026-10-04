/**
 * Tenant and Access (roadmap milestone M3): tenants, organisations, role grants, time-bound
 * break-glass access, tenant configuration history, local user accounts (a placeholder until
 * Keycloak owns sign-in) and each tenant's WhatsApp number.
 *
 * <p>Owns {@code zc_tenant}, {@code zc_organisation}, {@code zc_role_assignment},
 * {@code zc_break_glass_grant}, {@code zc_config_history}, {@code zc_user} and
 * {@code zc_tenant_whatsapp}, created by
 * {@code db/changelog/migrations/20260929_03_access.xml}. Non-Java files of this domain live
 * in {@code src/main/resources/domains/access/}.
 */
package com.zimasahealth.zcare.domains.access;
