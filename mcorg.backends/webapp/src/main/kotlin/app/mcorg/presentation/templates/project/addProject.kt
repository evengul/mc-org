package app.mcorg.presentation.templates.project

import app.mcorg.presentation.templates.baseTemplate
import kotlinx.html.*

fun addProject(isTechnical: Boolean): String = baseTemplate {
    nav {
        button {
            + "Back"
        }
        h1 {
            + "Add project"
        }
    }
    main {
        form {
            method = FormMethod.post
            encType = FormEncType.applicationXWwwFormUrlEncoded
            label {
                + "Name of project"
                input {
                    type = InputType.text
                    required = true
                    name = "projectName"
                }
            }
            label {
                + "Dimension"
                select {
                    required = true
                    name = "dimension"
                    option {
                        value = "OVERWORLD"
                        + "Overworld"
                    }
                    option {
                        value = "NETHER"
                        + "Nether"
                    }
                    option {
                        value = "THE_END"
                        + "The End"
                    }
                }
            }
            label {
                + "Priority"
                select {
                    required = true
                    name = "priority"
                    option {
                        value = "LOW"
                        + "Low"
                    }
                    option {
                        value = "MEDIUM"
                        + "Medium"
                    }
                    option {
                        value = "HIGH"
                        + "High"
                    }
                }
            }
            if (isTechnical) {
                label {
                    + "Requires perimeter"
                    input {
                        type = InputType.checkBox
                        name = "requiresPerimeter"
                    }
                }
            }
            button {
                type = ButtonType.submit
                + "Create"
            }
        }
    }
}