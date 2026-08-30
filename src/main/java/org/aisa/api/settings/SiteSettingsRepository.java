package org.aisa.api.settings;

import org.springframework.data.jpa.repository.JpaRepository;

/** Repository for the single settings row. Keyed by Short — see SiteSettings.SINGLETON_ID. */
public interface SiteSettingsRepository extends JpaRepository<SiteSettings, Short> {
}
