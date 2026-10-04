/**
 * Business logic of the observation domain. Every rule, state transition and consent or permission
 * check lives here and nowhere else. Services use repositories, specifications and mappers,
 * and return DTOs, so entities never leave this layer (PKG-09).
 */
package com.zimasahealth.zcare.domains.observation.services;
