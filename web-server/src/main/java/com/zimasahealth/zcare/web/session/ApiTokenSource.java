package com.zimasahealth.zcare.web.session;

/**
 * Supplies the bearer token sent to the API for a signed-in user. The token exists only on this
 * server: it is added to the forwarded call and never written to a browser response.
 */
public interface ApiTokenSource {

    String tokenFor(SessionUser user);
}
