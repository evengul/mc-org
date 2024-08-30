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
            label {
                + "Name of project"
                input {
                    type = InputType.text
                    required
                }
            }
            select {
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
            label {
                + "Priority"
                select {
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