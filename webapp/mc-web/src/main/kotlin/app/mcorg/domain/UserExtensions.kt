package app.mcorg.domain

import app.mcorg.config.AppConfig
import app.mcorg.domain.model.user.TokenProfile

fun TokenProfile.isDemoUserInProduction(): Boolean = this.isDemoUser && AppConfig.env == Production
