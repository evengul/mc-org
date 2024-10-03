package app.mcorg.presentation.templates

data class NavBarRightIcon(val icon: String,
                           val title: String,
                           val link: String? = null,
                           val id: String? = null,
                           val onClick: String? = null,
                           val data: Map<String, String> = emptyMap()
)