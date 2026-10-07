package com.zimasahealth.zcare.tenant;

/**
 * The tenant a request acts for: the {@code zc_tenant} row named by the caller's verified token.
 *
 * @param id   {@code zc_tenant.id}, the value written to {@code zcare.tenant_id}; never leaves the server
 * @param code {@code zc_tenant.code}, the token's tenant claim
 */
public record CurrentTenant(long id, String code) {
}
