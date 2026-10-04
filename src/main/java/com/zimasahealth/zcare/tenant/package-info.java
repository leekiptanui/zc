/**
 * Tenant context (roadmap milestone M2): resolves the tenant from the caller's token and sets
 * {@code zcare.tenant_id} for each transaction so that row-level security applies.
 *
 * <p>Request-scoped state is cleared in a guaranteed hook (CODE-07). Domain packages may depend
 * on this package; it never depends on a domain package (PKG-04).
 */
package com.zimasahealth.zcare.tenant;
