package com.cosmetic.ecommerce.service;

import jakarta.persistence.EntityManager;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AccountSecurityService {
    private final EntityManager entities;

    public AccountSecurityService(EntityManager entities) { this.entities = entities; }

    @Transactional
    public void revokeCurrentSessions(String email) {
        requireUpdated(entities.createQuery("update User u set u.sessionVersion = u.sessionVersion + 1 "
                        + "where u.email = :email and u.enabled = true and u.sessionVersion < :maximum")
                .setParameter("email", email).setParameter("maximum", Long.MAX_VALUE).executeUpdate());
        entities.clear();
    }

    @Transactional
    public void revokeSessionsForUser(Long id) {
        requireUpdated(entities.createQuery("update User u set u.sessionVersion = u.sessionVersion + 1 "
                        + "where u.id = :id and u.sessionVersion < :maximum")
                .setParameter("id", id).setParameter("maximum", Long.MAX_VALUE).executeUpdate());
        entities.clear();
    }

    @Transactional
    public void setAccountEnabled(Long id, boolean enabled, String operatorEmail) {
        if (operatorEmail == null || operatorEmail.isBlank()) throw new IllegalArgumentException("An operator is required");
        int updated = entities.createQuery("update User u set u.enabled = :enabled, u.sessionVersion = u.sessionVersion + 1 "
                        + "where u.id = :id and u.sessionVersion < :maximum "
                        + "and (:enabled = true or u.email <> :operator)")
                .setParameter("id", id).setParameter("enabled", enabled).setParameter("operator", operatorEmail)
                .setParameter("maximum", Long.MAX_VALUE).executeUpdate();
        requireUpdated(updated);
        entities.clear();
    }

    private void requireUpdated(int updated) {
        if (updated != 1) throw new IllegalArgumentException("Account operation unavailable");
    }
}
